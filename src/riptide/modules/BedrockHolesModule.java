package riptide.modules;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;

public final class BedrockHolesModule extends Module {
   private static volatile boolean hook;
   private static BedrockHolesModule cached;
   private final Set<BlockPos> holes = ConcurrentHashMap.newKeySet();
   private final Queue<long[]> queue = new ArrayDeque<>();
   private int timer;

   public BedrockHolesModule() {
      super("bedrock-holes", "Bedrock Holes", ModuleCategory.RENDER, "Marks deepslate tucked under the bedrock layer, where people hide bases.");
      // Riptide restores "enabled" from config without calling onEnable, so a hook
      // installed only in onEnable never registers after a relaunch. Install it here.
      installHook();
      this.add(new IntSetting("radius", "Chunk Radius", 6, 1, 16, 1).description("How many loaded chunks around you to scan.").build());
      this.add(new IntSetting("delay", "Scan Delay", 40, 5, 300, 5).description("Ticks between scan passes.").build());
      this.add(
         new IntSetting("per-tick", "Chunks Per Tick", 1, 1, 8, 1).description("How many chunks to scan each tick. Higher is faster but heavier.").build()
      );
      this.add(new IntSetting("max-y", "Max Y", 0, -64, 64, 1).description("Highest Y to consider. The bedrock floor sits around -60.").build());
      this.add(new IntSetting("search-up", "Bedrock Search", 8, 1, 24, 1).description("How many blocks above the deepslate to look for bedrock.").build());
      this.add(new BoolSetting("require-bedrock", "Require Bedrock", true).description("Only mark deepslate that actually has bedrock above it.").build());
      this.add(new BoolSetting("cobbled", "Include Cobbled", false).description("Also count cobbled deepslate.").build());
      this.add(new IntSetting("max", "Max Marks", 800, 50, 4000, 50).description("Stop after this many marked blocks.").build());
      this.add(new ColorSetting("color", "Color", -44976).group("Render").description("Marker color.").build());
   }

   @Override
   public String info() {
      return Integer.toString(this.holes.size());
   }

   @Override
   public void onEnable() {
      installHook();
      this.reset();
   }

   @Override
   public void onDisable() {
      this.reset();
   }

   @Override
   public void onGameLeft() {
      this.reset();
   }

   private void reset() {
      this.holes.clear();
      this.queue.clear();
      this.timer = 0;
   }

   @Override
   public void tick() {
      if (MC.level != null && MC.player != null) {
         if (this.queue.isEmpty() && ++this.timer >= this.integer("delay")) {
            this.timer = 0;
            this.fillQueue();
         }

         int var1 = this.integer("per-tick");

         while (var1-- > 0 && !this.queue.isEmpty()) {
            long[] var2 = this.queue.poll();
            LevelChunk var3 = MC.level.getChunkSource().getChunk((int)var2[0], (int)var2[1], false);
            if (var3 != null) {
               this.scan(var3, (int)var2[0], (int)var2[1]);
            }
         }
      } else {
         this.reset();
      }
   }

   private void fillQueue() {
      int var1 = this.integer("radius");
      int var2 = MC.player.blockPosition().getX() >> 4;
      int var3 = MC.player.blockPosition().getZ() >> 4;
      int var4 = var1 + 4;
      this.holes.removeIf(var3x -> Math.abs((var3x.getX() >> 4) - var2) > var4 || Math.abs((var3x.getZ() >> 4) - var3) > var4);

      for (int var5 = var2 - var1; var5 <= var2 + var1; var5++) {
         for (int var6 = var3 - var1; var6 <= var3 + var1; var6++) {
            this.queue.add(new long[]{var5, var6});
         }
      }
   }

   private void scan(LevelChunk var1, int var2, int var3) {
      int var4 = this.integer("max");
      if (this.holes.size() < var4) {
         boolean var5 = this.bool("require-bedrock");
         boolean var6 = this.bool("cobbled");
         int var7 = this.integer("search-up");
         int var8 = MC.level.getMinY();
         int var9 = Math.min(this.integer("max-y"), MC.level.getMaxY());
         MutableBlockPos var10 = new MutableBlockPos();
         int var11 = var2 << 4;
         int var12 = var3 << 4;

         for (int var13 = var8; var13 <= var9; var13++) {
            for (int var14 = 0; var14 < 16; var14++) {
               for (int var15 = 0; var15 < 16; var15++) {
                  if (this.holes.size() >= var4) {
                     return;
                  }

                  var10.set(var11 + var14, var13, var12 + var15);
                  if (isDeepslate(var1.getBlockState(var10).getBlock(), var6) && (!var5 || this.bedrockAbove(var10, var7, var9))) {
                     this.holes.add(var10.immutable());
                  }
               }
            }
         }
      }
   }

   private boolean bedrockAbove(BlockPos var1, int var2, int var3) {
      MutableBlockPos var4 = new MutableBlockPos();
      int var5 = Math.min(var1.getY() + var2, MC.level.getMaxY());

      for (int var6 = var1.getY() + 1; var6 <= var5; var6++) {
         var4.set(var1.getX(), var6, var1.getZ());
         if (MC.level.getBlockState(var4).is(Blocks.BEDROCK)) {
            return true;
         }
      }

      return false;
   }

   private static boolean isDeepslate(Block var0, boolean var1) {
      return var0 == Blocks.DEEPSLATE || var1 && var0 == Blocks.COBBLED_DEEPSLATE;
   }

   private static BedrockHolesModule instance() {
      BedrockHolesModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("bedrock-holes") instanceof BedrockHolesModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0;
   }

   private static synchronized void installHook() {
      if (!hook) {
         hook = true;
         LevelRenderEvents.COLLECT_SUBMITS
            .register(
               (CollectSubmits)var0 -> {
                  try {
                     BedrockHolesModule var1 = instance();
                     if (var1 == null || !var1.isEnabled() || PackHideState.isActive() || var1.holes.isEmpty()) {
                        return;
                     }

                     if (MC == null || MC.level == null || MC.player == null || MC.gui.hud.isHidden() || ModuleRenderUtil.shouldSuppressEspForUi()) {
                        return;
                     }

                     Vec3 var2 = var0.levelState().cameraRenderState.pos;
                     int var3 = ModuleRenderUtil.color(var1, "color", -44976) | 0xFF000000;
                     var0.submitNodeCollector()
                        .submitCustomGeometry(
                           var0.poseStack(),
                           RiptideRenderTypes.storageEspLinesSeeThrough(),
                           (var3x, var4x) -> {
                              for (BlockPos var6 : var1.holes) {
                                 SpawnerFinderModule.box(
                                    var3x, var4x, var6.getX(), var6.getY(), var6.getZ(), var6.getX() + 1.0, var6.getY() + 1.0, var6.getZ() + 1.0, var2, var3
                                 );
                              }
                           }
                        );
                  } catch (Throwable var4) {
                  }
               }
            );
      }
   }
}
