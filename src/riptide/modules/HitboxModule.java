package riptide.modules;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideWorldGeometry;

public final class HitboxModule extends Module {
   private static volatile boolean hook;
   private static HitboxModule cached;

   public HitboxModule() {
      super("hitbox", "Hitbox", ModuleCategory.RENDER, "Draws the collision box outline of nearby entities.");
      // Riptide restores "enabled" from config without calling onEnable, so a hook
      // installed only in onEnable never registers after a relaunch. Install it here.
      installHook();
      this.add(new BoolSetting("players", "Players", true).description("Outline players.").build());
      this.add(new BoolSetting("mobs", "Mobs", true).description("Outline mobs and other living entities.").build());
      this.add(
         new IntSetting("expand", "Expand", 0, 0, 40, 1).description("Puff the box out by this many hundredths of a block (0 = the true hitbox).").build()
      );
      this.add(new IntSetting("range", "Range", 32, 4, 128, 4).description("How far away hitboxes still draw, in blocks.").build());
      this.add(new ColorSetting("color", "Color", -1).group("Colors").description("Outline color.").build());
   }

   public static void initialize() {
      installHook();
   }

   @Override
   public void onEnable() {
      installHook();
   }

   private static HitboxModule instance() {
      HitboxModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("hitbox") instanceof HitboxModule var1) {
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
               HitboxModule var1 = instance();
               if (var1 == null || !var1.isEnabled() || PackHideState.isActive()) {
                  return;
               }

               if (MC == null || MC.level == null || MC.player == null || MC.gui.hud.isHidden() || ModuleRenderUtil.shouldSuppressEspForUi()) {
                  return;
               }

               CameraRenderState var2 = var0.levelState().cameraRenderState;
               Vec3 var3 = var2.pos;
               int var4 = ModuleRenderUtil.color(var1, "color", -1) | 0xFF000000;
               double var5 = var1.integer("range");
               double var7 = var5 * var5;
               double var9 = var1.integer("expand") / 100.0;
               boolean var11 = var1.bool("players");
               boolean var12 = var1.bool("mobs");
               var0.submitNodeCollector().submitCustomGeometry(var0.poseStack(), RiptideRenderTypes.storageEspLinesSeeThrough(), (var8, var9x) -> {
                  for (Entity var11x : MC.level.entitiesForRendering()) {
                     if (var11x != MC.player && var11x instanceof LivingEntity var12x && var12x.isAlive()) {
                        boolean var13x = var11x instanceof Player;
                        if ((var13x ? var11 : var12) && !(var11x.position().distanceToSqr(var3) > var7)) {
                           AABB var14 = var11x.getBoundingBox().inflate(var9);
                           drawBox(var8, var9x, var14, var3, var4);
                        }
                     }
                  }
               });
            } catch (Throwable var13) {
            }
         });
      }
   }

   private static void drawBox(Pose var0, VertexConsumer var1, AABB var2, Vec3 var3, int var4) {
      double var5 = var2.minX - var3.x;
      double var7 = var2.minY - var3.y;
      double var9 = var2.minZ - var3.z;
      double var11 = var2.maxX - var3.x;
      double var13 = var2.maxY - var3.y;
      double var15 = var2.maxZ - var3.z;
      edge(var0, var1, var5, var7, var9, var11, var7, var9, var4);
      edge(var0, var1, var11, var7, var9, var11, var7, var15, var4);
      edge(var0, var1, var11, var7, var15, var5, var7, var15, var4);
      edge(var0, var1, var5, var7, var15, var5, var7, var9, var4);
      edge(var0, var1, var5, var13, var9, var11, var13, var9, var4);
      edge(var0, var1, var11, var13, var9, var11, var13, var15, var4);
      edge(var0, var1, var11, var13, var15, var5, var13, var15, var4);
      edge(var0, var1, var5, var13, var15, var5, var13, var9, var4);
      edge(var0, var1, var5, var7, var9, var5, var13, var9, var4);
      edge(var0, var1, var11, var7, var9, var11, var13, var9, var4);
      edge(var0, var1, var11, var7, var15, var11, var13, var15, var4);
      edge(var0, var1, var5, var7, var15, var5, var13, var15, var4);
   }

   private static void edge(Pose var0, VertexConsumer var1, double var2, double var4, double var6, double var8, double var10, double var12, int var14) {
      RiptideWorldGeometry.line(var0, var1, var2, var4, var6, var8, var10, var12, var14, 1.5F);
   }
}
