package riptide.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;

public final class SpawnerNametagsModule extends Module {
   private static volatile boolean hook;
   private static SpawnerNametagsModule cached;
   private final Map<BlockPos, String> labels = new LinkedHashMap<>();
   private int ticks;

   public SpawnerNametagsModule() {
      super("spawner-nametags", "Spawner Nametags", ModuleCategory.RENDER, "Shows which mob each nearby spawner spawns.");
      // Riptide restores "enabled" from config without calling onEnable, so a hook
      // installed only in onEnable never registers after a relaunch. Install it here.
      installHook();
      this.add(new IntSetting("range", "Range", 128, 16, 256, 8).description("How far away a spawner still gets a label, in blocks.").build());
      this.add(new IntSetting("refresh", "Refresh", 20, 5, 200, 5).description("Ticks between label rebuilds.").build());
      this.add(new BoolSetting("distance", "Show Distance", true).description("Add how many blocks away the spawner is.").build());
   }

   @Override
   public String info() {
      return Integer.toString(this.labels.size());
   }

   @Override
   public void onEnable() {
      installHook();
      this.labels.clear();
      this.ticks = 0;
   }

   @Override
   public void onDisable() {
      this.labels.clear();
   }

   @Override
   public void onGameLeft() {
      this.labels.clear();
   }

   @Override
   public void tick() {
      if (MC.level != null && MC.player != null) {
         if (++this.ticks >= this.integer("refresh")) {
            this.ticks = 0;
            this.rebuild();
         }
      } else {
         this.labels.clear();
      }
   }

   private void rebuild() {
      this.labels.clear();
      int var1 = this.integer("range");
      int var2 = Math.max(1, (var1 >> 4) + 1);
      int var3 = MC.player.blockPosition().getX() >> 4;
      int var4 = MC.player.blockPosition().getZ() >> 4;
      double var5 = (double)var1 * var1;

      for (int var7 = var3 - var2; var7 <= var3 + var2; var7++) {
         for (int var8 = var4 - var2; var8 <= var4 + var2; var8++) {
            LevelChunk var9 = MC.level.getChunkSource().getChunk(var7, var8, false);
            if (var9 != null) {
               for (BlockEntity var11 : var9.getBlockEntities().values()) {
                  if (var11 instanceof SpawnerBlockEntity var12) {
                     BlockPos var13 = var11.getBlockPos();
                     if (!(MC.player.blockPosition().distSqr(var13) > var5)) {
                        this.labels.put(var13.immutable(), mobName(var12, var13));
                     }
                  }
               }
            }
         }
      }
   }

   private static String mobName(SpawnerBlockEntity var0, BlockPos var1) {
      try {
         Entity var2 = var0.getSpawner().getOrCreateDisplayEntity(MC.level, var1);
         if (var2 != null) {
            return var2.getType().getDescription().getString();
         }
      } catch (Throwable var3) {
      }

      return "Spawner";
   }

   private static SpawnerNametagsModule instance() {
      SpawnerNametagsModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("spawner-nametags") instanceof SpawnerNametagsModule var1) {
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
                     SpawnerNametagsModule var1 = instance();
                     if (var1 == null || !var1.isEnabled() || PackHideState.isActive() || var1.labels.isEmpty()) {
                        return;
                     }

                     if (MC == null || MC.level == null || MC.player == null || MC.gui.hud.isHidden() || ModuleRenderUtil.shouldSuppressEspForUi()) {
                        return;
                     }

                     Vec3 var2 = var0.levelState().cameraRenderState.pos;
                     boolean var3 = var1.bool("distance");
                     PoseStack var4 = var0.poseStack();

                     for (Entry var6 : var1.labels.entrySet()) {
                        BlockPos var7 = (BlockPos)var6.getKey();
                        String var8 = (String)var6.getValue();
                        if (var3) {
                           int var9 = (int)Math.sqrt(MC.player.blockPosition().distSqr(var7));
                           var8 = var8 + " §7" + var9 + "m";
                        }

                        var4.pushPose();
                        var4.translate(var7.getX() + 0.5 - var2.x, var7.getY() + 1.4 - var2.y, var7.getZ() + 0.5 - var2.z);
                        var0.submitNodeCollector()
                           .submitNameTag(var4, Vec3.ZERO, 1610612736, Component.literal(var8), false, 15728880, var0.levelState().cameraRenderState);
                        var4.popPose();
                     }
                  } catch (Throwable var10) {
                  }
               }
            );
      }
   }
}
