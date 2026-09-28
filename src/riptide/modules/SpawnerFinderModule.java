package riptide.modules;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideWorldGeometry;

public final class SpawnerFinderModule extends Module {
   private static volatile boolean hook;
   private static SpawnerFinderModule cached;
   private final Map<BlockPos, Boolean> spawners = new ConcurrentHashMap<>();
   private int timer;

   public SpawnerFinderModule() {
      super("spawner-finder", "Spawner Finder", ModuleCategory.RENDER, "Boxes monster spawners in loaded chunks and alerts you when a new one appears.");
      // Riptide restores "enabled" from config without calling onEnable, so a hook
      // installed only in onEnable never registers after a relaunch. Install it here.
      installHook();
      this.add(new IntSetting("radius", "Chunk Radius", 8, 1, 16, 1).description("How many loaded chunks around you to search.").build());
      this.add(new IntSetting("delay", "Scan Delay", 20, 1, 200, 1).description("Ticks between scans (20 ticks = 1 second).").build());
      this.add(new BoolSetting("chat", "Chat Alert", true).group("Alerts").description("Print the coordinates when a new spawner is found.").build());
      this.add(new BoolSetting("sound", "Sound Alert", true).group("Alerts").description("Play a ping when a new spawner is found.").build());
      this.add(new BoolSetting("keep", "Keep Found", true).description("Keep markers even after the server stops sending the spawner.").build());
      this.add(
         new BoolSetting("column", "Chunk Column", false)
            .group("Render")
            .description("Also draw a tall outline up the spawner's chunk so you can see it far away.")
            .build()
      );
      this.add(new ColorSetting("color", "Color", -12124306).group("Render").description("Box color.").build());
   }

   @Override
   public String info() {
      return Integer.toString(this.spawners.size());
   }

   @Override
   public void onEnable() {
      installHook();
      this.spawners.clear();
      this.timer = 0;
   }

   @Override
   public void onDisable() {
      this.spawners.clear();
   }

   @Override
   public void onGameLeft() {
      this.spawners.clear();
   }

   @Override
   public void tick() {
      if (MC.level != null && MC.player != null) {
         if (++this.timer >= this.integer("delay")) {
            this.timer = 0;
            if (!this.bool("keep")) {
               this.spawners.clear();
            }

            int var1 = this.integer("radius");
            int var2 = MC.player.blockPosition().getX() >> 4;
            int var3 = MC.player.blockPosition().getZ() >> 4;

            for (int var4 = var2 - var1; var4 <= var2 + var1; var4++) {
               for (int var5 = var3 - var1; var5 <= var3 + var1; var5++) {
                  LevelChunk var6 = MC.level.getChunkSource().getChunk(var4, var5, false);
                  if (var6 != null) {
                     for (BlockEntity var8 : var6.getBlockEntities().values()) {
                        if (var8 instanceof SpawnerBlockEntity) {
                           this.found(var8.getBlockPos().immutable());
                        }
                     }
                  }
               }
            }

            int var9 = var1 + 6;
            this.spawners.keySet().removeIf(var3x -> Math.abs((var3x.getX() >> 4) - var2) > var9 || Math.abs((var3x.getZ() >> 4) - var3) > var9);
         }
      } else {
         this.spawners.clear();
      }
   }

   private void found(BlockPos var1) {
      if (this.spawners.putIfAbsent(var1, Boolean.TRUE) == null) {
         if (this.bool("chat")) {
            RiptideClientMessaging.sendPrefixed("§aSpawner §7at §f" + var1.getX() + ", " + var1.getY() + ", " + var1.getZ());
         }

         if (this.bool("sound") && MC.player != null) {
            try {
               MC.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7F, 1.4F);
            } catch (Throwable var3) {
            }
         }
      }
   }

   private static SpawnerFinderModule instance() {
      SpawnerFinderModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("spawner-finder") instanceof SpawnerFinderModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0;
   }

   private static synchronized void installHook() {
      if (!hook) {
         hook = true;
         LevelRenderEvents.COLLECT_SUBMITS.register((CollectSubmits)var0 -> {
            try {
               SpawnerFinderModule var1 = instance();
               if (var1 == null || !var1.isEnabled() || PackHideState.isActive() || var1.spawners.isEmpty()) {
                  return;
               }

               if (MC == null || MC.level == null || MC.player == null || MC.gui.hud.isHidden() || ModuleRenderUtil.shouldSuppressEspForUi()) {
                  return;
               }

               Vec3 var2 = var0.levelState().cameraRenderState.pos;
               int var3 = ModuleRenderUtil.color(var1, "color", -12124306) | 0xFF000000;
               boolean var4 = var1.bool("column");
               int var5 = MC.level.getMaxY() + 1;
               int var6 = MC.level.getMinY();
               var0.submitNodeCollector().submitCustomGeometry(var0.poseStack(), RiptideRenderTypes.storageEspLinesSeeThrough(), (var6x, var7x) -> {
                  for (BlockPos var9 : var1.spawners.keySet()) {
                     box(var6x, var7x, var9.getX(), var9.getY(), var9.getZ(), var9.getX() + 1.0, var9.getY() + 1.0, var9.getZ() + 1.0, var2, var3);
                     if (var4) {
                        int var10 = var9.getX() & -16;
                        int var11 = var9.getZ() & -16;
                        box(var6x, var7x, var10, var6, var11, var10 + 16.0, var5, var11 + 16.0, var2, var3);
                     }
                  }
               });
            } catch (Throwable var7) {
            }
         });
      }
   }

   static void box(Pose var0, VertexConsumer var1, double var2, double var4, double var6, double var8, double var10, double var12, Vec3 var14, int var15) {
      double var16 = var2 - var14.x;
      double var18 = var4 - var14.y;
      double var20 = var6 - var14.z;
      double var22 = var8 - var14.x;
      double var24 = var10 - var14.y;
      double var26 = var12 - var14.z;
      edge(var0, var1, var16, var18, var20, var22, var18, var20, var15);
      edge(var0, var1, var22, var18, var20, var22, var18, var26, var15);
      edge(var0, var1, var22, var18, var26, var16, var18, var26, var15);
      edge(var0, var1, var16, var18, var26, var16, var18, var20, var15);
      edge(var0, var1, var16, var24, var20, var22, var24, var20, var15);
      edge(var0, var1, var22, var24, var20, var22, var24, var26, var15);
      edge(var0, var1, var22, var24, var26, var16, var24, var26, var15);
      edge(var0, var1, var16, var24, var26, var16, var24, var20, var15);
      edge(var0, var1, var16, var18, var20, var16, var24, var20, var15);
      edge(var0, var1, var22, var18, var20, var22, var24, var20, var15);
      edge(var0, var1, var22, var18, var26, var22, var24, var26, var15);
      edge(var0, var1, var16, var18, var26, var16, var24, var26, var15);
   }

   private static void edge(Pose var0, VertexConsumer var1, double var2, double var4, double var6, double var8, double var10, double var12, int var14) {
      RiptideWorldGeometry.line(var0, var1, var2, var4, var6, var8, var10, var12, var14, 1.6F);
   }
}
