package riptide.modules;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideWorldGeometry;

public final class AmethystGeodeFinderModule extends Module {
   private static final long RESCAN_NANOS = 60000000000L;
   private static volatile boolean renderHookInstalled;
   private static AmethystGeodeFinderModule cachedInstance;
   private final Map<Long, AmethystGeodeFinderModule.Box> found = new ConcurrentHashMap<>();
   private final Map<Long, Long> scannedAt = new ConcurrentHashMap<>();
   private int radius = 12;

   public AmethystGeodeFinderModule() {
      super("amethyst-geode-finder", "Geode Finder", ModuleCategory.RENDER, "Highlights amethyst geodes.");
      this.add(new IntSetting("radius", "Radius", 12, 2, 32, 1).description("Chunk radius scanned and drawn around you.").build());
      this.add(
         new IntSetting("min-budding", "Budding Needed", 1, 1, 30, 1).description("Budding amethyst blocks in a chunk before it counts as a geode.").build()
      );
      this.add(new ChoiceSetting("mode", "Render", "Both", "Both", "Box", "Fill").description("Outline, translucent fill, or both.").build());
      this.add(new IntSetting("fill-alpha", "Fill Opacity", 40, 0, 255, 5).description("How solid the box fill is.").build());
      this.add(new BoolSetting("notify", "Notify", true).description("Chat message when a new geode is found.").build());
      this.add(new ColorSetting("color", "Color", -5092353).group("Colors").description("Geode highlight color.").build());
   }

   public static void initialize() {
      installRenderHook();
   }

   @Override
   public String info() {
      return Integer.toString(this.found.size());
   }

   @Override
   public void onEnable() {
      installRenderHook();
      this.found.clear();
      this.scannedAt.clear();
   }

   @Override
   public void onGameLeft() {
      this.found.clear();
      this.scannedAt.clear();
   }

   @Override
   protected void onOptionValueChanged(String var1) {
      if ("min-budding".equals(var1)) {
         this.found.clear();
         this.scannedAt.clear();
      }
   }

   @Override
   public void tick() {
      if (MC.level != null && MC.player != null) {
         this.radius = this.integer("radius");
         int var1 = MC.player.blockPosition().getX() >> 4;
         int var2 = MC.player.blockPosition().getZ() >> 4;
         long var3 = System.nanoTime();
         int var5 = 4;

         for (int var6 = 0; var6 <= this.radius && var5 > 0; var6++) {
            for (int var7 = var1 - var6; var7 <= var1 + var6 && var5 > 0; var7++) {
               for (int var8 = var2 - var6; var8 <= var2 + var6 && var5 > 0; var8++) {
                  if (Math.max(Math.abs(var7 - var1), Math.abs(var8 - var2)) == var6) {
                     long var9 = key(var7, var8);
                     Long var11 = this.scannedAt.get(var9);
                     if (var11 == null || var3 - var11 >= 60000000000L) {
                        LevelChunk var12 = MC.level.getChunkSource().getChunk(var7, var8, false);
                        if (var12 != null) {
                           this.scannedAt.put(var9, var3);
                           this.scan(var12, var7, var8);
                           var5--;
                        }
                     }
                  }
               }
            }
         }

         int var13 = this.radius + 4;
         this.found.keySet().removeIf(var3x -> Math.abs(keyX(var3x) - var1) > var13 || Math.abs(keyZ(var3x) - var2) > var13);
         this.scannedAt.keySet().removeIf(var3x -> Math.abs(keyX(var3x) - var1) > var13 || Math.abs(keyZ(var3x) - var2) > var13);
      }
   }

   private void scan(LevelChunk var1, int var2, int var3) {
      int var4 = this.integer("min-budding");
      int var5 = Integer.MAX_VALUE;
      int var6 = Integer.MAX_VALUE;
      int var7 = Integer.MAX_VALUE;
      int var8 = Integer.MIN_VALUE;
      int var9 = Integer.MIN_VALUE;
      int var10 = Integer.MIN_VALUE;
      int var11 = 0;
      LevelChunkSection[] var12 = var1.getSections();
      int var13 = MC.level.getMinY();

      for (int var14 = 0; var14 < var12.length; var14++) {
         LevelChunkSection var15 = var12[var14];
         if (var15 != null && !var15.hasOnlyAir() && var15.maybeHas(var0 -> var0.is(Blocks.BUDDING_AMETHYST))) {
            int var16 = var13 + (var14 << 4);

            for (int var17 = 0; var17 < 16; var17++) {
               for (int var18 = 0; var18 < 16; var18++) {
                  for (int var19 = 0; var19 < 16; var19++) {
                     BlockState var20 = var15.getBlockState(var18, var17, var19);
                     if (var20.is(Blocks.BUDDING_AMETHYST)) {
                        int var21 = (var2 << 4) + var18;
                        int var22 = var16 + var17;
                        int var23 = (var3 << 4) + var19;
                        var5 = Math.min(var5, var21);
                        var6 = Math.min(var6, var22);
                        var7 = Math.min(var7, var23);
                        var8 = Math.max(var8, var21);
                        var9 = Math.max(var9, var22);
                        var10 = Math.max(var10, var23);
                        var11++;
                     }
                  }
               }
            }
         }
      }

      long var24 = key(var2, var3);
      if (var11 < var4) {
         this.found.remove(var24);
      } else {
         boolean var25 = !this.found.containsKey(var24);
         this.found.put(var24, new AmethystGeodeFinderModule.Box(var5, var6, var7, var8 + 1, var9 + 1, var10 + 1, var11));
         if (var25 && this.bool("notify")) {
            RiptideClientMessaging.sendPrefixed(
               "§dGeode §f" + (var5 + var8) / 2 + ", " + (var6 + var9) / 2 + ", " + (var7 + var10) / 2 + " §7(" + var11 + " budding)"
            );
         }
      }
   }

   private static long key(int var0, int var1) {
      return var0 & 4294967295L | (long)var1 << 32;
   }

   private static int keyX(long var0) {
      return (int)var0;
   }

   private static int keyZ(long var0) {
      return (int)(var0 >>> 32);
   }

   private static AmethystGeodeFinderModule instance() {
      AmethystGeodeFinderModule var0 = cachedInstance;
      if (var0 == null && ModuleRegistry.get("amethyst-geode-finder") instanceof AmethystGeodeFinderModule var1) {
         var0 = var1;
         cachedInstance = var1;
      }

      return var0;
   }

   private static synchronized void installRenderHook() {
      if (!renderHookInstalled) {
         renderHookInstalled = true;
         LevelRenderEvents.COLLECT_SUBMITS
            .register(
               (CollectSubmits)var0 -> {
                  try {
                     AmethystGeodeFinderModule var1 = instance();
                     if (var1 == null || !var1.isEnabled() || PackHideState.isActive() || var1.found.isEmpty()) {
                        return;
                     }

                     if (MC == null || MC.level == null || MC.player == null || MC.gui.hud.isHidden() || ModuleRenderUtil.shouldSuppressEspForUi()) {
                        return;
                     }

                     String var2 = var1.choice("mode");
                     boolean var3 = !"Box".equals(var2);
                     boolean var4 = !"Fill".equals(var2);
                     Vec3 var5 = var0.levelState().cameraRenderState.pos;
                     if (var3) {
                        var0.submitNodeCollector()
                           .submitCustomGeometry(
                              var0.poseStack(), RiptideRenderTypes.storageEspFillSeeThrough(), (var2x, var3x) -> var1.emit(var2x, var3x, var5, true, false)
                           );
                     }

                     if (var4) {
                        var0.submitNodeCollector()
                           .submitCustomGeometry(
                              var0.poseStack(), RiptideRenderTypes.storageEspLinesSeeThrough(), (var2x, var3x) -> var1.emit(var2x, var3x, var5, false, true)
                           );
                     }
                  } catch (Throwable var6) {
                  }
               }
            );
      }
   }

   private void emit(Pose var1, VertexConsumer var2, Vec3 var3, boolean var4, boolean var5) {
      int var6 = ModuleRenderUtil.color(this, "color", -5092353) & 16777215;
      int var7 = this.integer("fill-alpha");

      for (AmethystGeodeFinderModule.Box var9 : this.found.values()) {
         double var10 = var9.minX() - 0.5 - var3.x;
         double var12 = var9.maxX() + 0.5 - var3.x;
         double var14 = var9.minY() - 0.5 - var3.y;
         double var16 = var9.maxY() + 0.5 - var3.y;
         double var18 = var9.minZ() - 0.5 - var3.z;
         double var20 = var9.maxZ() + 0.5 - var3.z;
         if (var4 && var7 > 0) {
            int var22 = var7 << 24 | var6;
            quad(var1, var2, var10, var14, var18, var10, var16, var18, var12, var16, var18, var12, var14, var18, var22);
            quad(var1, var2, var12, var14, var20, var12, var16, var20, var10, var16, var20, var10, var14, var20, var22);
            quad(var1, var2, var10, var14, var20, var10, var16, var20, var10, var16, var18, var10, var14, var18, var22);
            quad(var1, var2, var12, var14, var18, var12, var16, var18, var12, var16, var20, var12, var14, var20, var22);
            quad(var1, var2, var10, var14, var18, var12, var14, var18, var12, var14, var20, var10, var14, var20, var22);
            quad(var1, var2, var10, var16, var20, var12, var16, var20, var12, var16, var18, var10, var16, var18, var22);
         }

         if (var5) {
            int var23 = 0xFF000000 | var6;
            edge(var1, var2, var10, var14, var18, var12, var14, var18, var23);
            edge(var1, var2, var12, var14, var18, var12, var14, var20, var23);
            edge(var1, var2, var12, var14, var20, var10, var14, var20, var23);
            edge(var1, var2, var10, var14, var20, var10, var14, var18, var23);
            edge(var1, var2, var10, var16, var18, var12, var16, var18, var23);
            edge(var1, var2, var12, var16, var18, var12, var16, var20, var23);
            edge(var1, var2, var12, var16, var20, var10, var16, var20, var23);
            edge(var1, var2, var10, var16, var20, var10, var16, var18, var23);
            edge(var1, var2, var10, var14, var18, var10, var16, var18, var23);
            edge(var1, var2, var12, var14, var18, var12, var16, var18, var23);
            edge(var1, var2, var12, var14, var20, var12, var16, var20, var23);
            edge(var1, var2, var10, var14, var20, var10, var16, var20, var23);
         }
      }
   }

   private static void edge(Pose var0, VertexConsumer var1, double var2, double var4, double var6, double var8, double var10, double var12, int var14) {
      RiptideWorldGeometry.line(var0, var1, var2, var4, var6, var8, var10, var12, var14, 2.0F);
   }

   private static void quad(
      Pose var0,
      VertexConsumer var1,
      double var2,
      double var4,
      double var6,
      double var8,
      double var10,
      double var12,
      double var14,
      double var16,
      double var18,
      double var20,
      double var22,
      double var24,
      int var26
   ) {
      var1.addVertex(var0, (float)var2, (float)var4, (float)var6).setColor(var26);
      var1.addVertex(var0, (float)var8, (float)var10, (float)var12).setColor(var26);
      var1.addVertex(var0, (float)var14, (float)var16, (float)var18).setColor(var26);
      var1.addVertex(var0, (float)var20, (float)var22, (float)var24).setColor(var26);
   }

   private record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int count) {
   }
}
