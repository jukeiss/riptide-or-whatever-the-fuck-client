package riptide.util;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import riptide.modules.PackHideState;

public final class RiptideScaffoldPlaceRenderer {
   private static final int CAP = 32;
   private static final long DURATION_NS = 320000000L;
   private static final double FADE_START = 0.62;
   private static final double INFLATE = 0.0035;
   private static final float BASE_ALPHA = 0.55F;
   private static final int[] PX = new int[32];
   private static final int[] PY = new int[32];
   private static final int[] PZ = new int[32];
   private static final long[] AT = new long[32];
   private static int idx;
   private static volatile boolean on;
   private static volatile boolean customColor;
   private static volatile int color = -50373;

   private RiptideScaffoldPlaceRenderer() {
   }

   public static void push(boolean enabled, boolean custom, int customArgb) {
      on = enabled;
      customColor = custom;
      color = customArgb;
   }

   public static void disable() {
      on = false;
   }

   public static void recordPlacement(BlockPos pos) {
      if (on && pos != null) {
         PX[idx] = pos.getX();
         PY[idx] = pos.getY();
         PZ[idx] = pos.getZ();
         AT[idx] = System.nanoTime();
         idx = (idx + 1) % 32;
      }
   }

   public static boolean isActive() {
      return on && !PackHideState.isHardLocked();
   }

   public static void initialize() {
      LevelRenderEvents.COLLECT_SUBMITS.register((CollectSubmits)context -> {
         if (isActive()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.level != null) {
               long now = System.nanoTime();
               int argb = baseColor();
               Vec3 origin = context.levelState().cameraRenderState.pos;
               context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RiptideRenderTypes.storageEspFillSeeThrough(), (pose, buffer) -> {
                  for (int i = 0; i < 32; i++) {
                     long t = AT[i];
                     if (t != 0L) {
                        long age = now - t;
                        if (age >= 0L && age < 320000000L) {
                           ripple(pose, buffer, PX[i] - origin.x, PY[i] - origin.y, PZ[i] - origin.z, age / 3.2E8, argb);
                        }
                     }
                  }
               });
            }
         }
      });
   }

   private static int baseColor() {
      return customColor ? color | 0xFF000000 : RiptideTheme.recolor(-50373, RiptideTheme.Channel.ACCENT);
   }

   private static void ripple(Pose pose, VertexConsumer buffer, double bx, double by, double bz, double t, int argb) {
      double inv = 1.0 - t;
      double scale = 1.0 - inv * inv * inv;
      double fade = t < 0.62 ? 1.0 : 1.0 - (t - 0.62) / 0.38;
      int alpha = (int)(0.55F * fade * 255.0);
      if (alpha > 0) {
         int c = argb & 16777215 | alpha << 24;
         double half = 0.5 * scale + 0.0035;
         double x1 = bx + 0.5 - half;
         double y1 = by + 0.5 - half;
         double z1 = bz + 0.5 - half;
         double x2 = bx + 0.5 + half;
         double y2 = by + 0.5 + half;
         double z2 = bz + 0.5 + half;
         quad(pose, buffer, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, c);
         quad(pose, buffer, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, c);
         quad(pose, buffer, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, c);
         quad(pose, buffer, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, c);
         quad(pose, buffer, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, c);
         quad(pose, buffer, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, c);
      }
   }

   private static void quad(
      Pose pose,
      VertexConsumer buffer,
      double x1,
      double y1,
      double z1,
      double x2,
      double y2,
      double z2,
      double x3,
      double y3,
      double z3,
      double x4,
      double y4,
      double z4,
      int color
   ) {
      buffer.addVertex(pose, (float)x1, (float)y1, (float)z1).setColor(color);
      buffer.addVertex(pose, (float)x2, (float)y2, (float)z2).setColor(color);
      buffer.addVertex(pose, (float)x3, (float)y3, (float)z3).setColor(color);
      buffer.addVertex(pose, (float)x4, (float)y4, (float)z4).setColor(color);
   }
}
