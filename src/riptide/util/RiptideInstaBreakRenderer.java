package riptide.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import riptide.modules.PackHideState;

public final class RiptideInstaBreakRenderer {
   private static volatile BlockPos targetPos;
   private static volatile String targetShape = "Lines";
   private static volatile int lineColor = -50373;
   private static volatile int sideColor = 0;
   private static final float LINE_WIDTH = 2.0F;

   private RiptideInstaBreakRenderer() {
   }

   public static void initialize() {
      LevelRenderEvents.COLLECT_SUBMITS
         .register(
            (CollectSubmits)context -> {
               if (!PackHideState.isHardLocked()) {
                  BlockPos pos = targetPos;
                  String shape = targetShape;
                  int renderColor = lineColor;
                  int renderSideColor = sideColor;
                  RiptideSharedState state = RiptideSharedState.get();
                  if (state.isPlaceCaptureActive()) {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc != null && mc.level != null && mc.player != null && mc.hitResult instanceof BlockHitResult bhr) {
                        BlockPos support = bhr.getBlockPos();
                        Direction face = bhr.getDirection() == null ? Direction.UP : bhr.getDirection();
                        BlockPos preview = support.relative(face);
                        if (!mc.level.isOutsideBuildHeight(preview)) {
                           pos = preview;
                           shape = "Lines";
                           renderColor = -50373;
                           renderSideColor = 0;
                        }
                     }
                  }

                  if (pos != null) {
                     Vec3 camera = context.levelState().cameraRenderState.pos;
                     AABB box = new AABB(pos).move(-camera.x, -camera.y, -camera.z);
                     PoseStack poseStack = context.poseStack();
                     int finalColor = renderColor;
                     int finalSideColor = renderSideColor;
                     if (drawSides(shape) && (finalSideColor >>> 24 & 0xFF) > 0) {
                        context.submitNodeCollector()
                           .submitCustomGeometry(
                              poseStack, RiptideRenderTypes.storageEspFillSeeThrough(), (pose, buffer) -> fillBox(pose, buffer, box, finalSideColor)
                           );
                     }

                     if (drawLines(shape)) {
                        context.submitNodeCollector()
                           .submitCustomGeometry(
                              poseStack, RiptideRenderTypes.storageEspLinesSeeThrough(), (pose, buffer) -> renderBox(pose, buffer, box, finalColor)
                           );
                     }
                  }
               }
            }
         );
   }

   public static void setTarget(BlockPos pos) {
      targetPos = pos == null ? null : pos.immutable();
      targetShape = "Lines";
      lineColor = -50373;
      sideColor = 0;
   }

   public static void setTarget(BlockPos pos, String shape, int line, int side) {
      targetPos = pos == null ? null : pos.immutable();
      targetShape = shape == null ? "Lines" : shape;
      lineColor = line;
      sideColor = side;
   }

   public static void setTarget(BlockPos pos, int color) {
      targetPos = pos == null ? null : pos.immutable();
      targetShape = "Lines";
      lineColor = color;
      sideColor = 0;
   }

   public static void clearTarget(BlockPos pos) {
      BlockPos current = targetPos;
      if (current == null || pos == null || current.equals(pos)) {
         targetPos = null;
      }
   }

   public static void clear() {
      targetPos = null;
   }

   public static void tickPlacePreview() {
   }

   private static void renderBox(Pose pose, VertexConsumer buffer, AABB box, int color) {
      double x1 = box.minX;
      double y1 = box.minY;
      double z1 = box.minZ;
      double x2 = box.maxX;
      double y2 = box.maxY;
      double z2 = box.maxZ;
      line(pose, buffer, x1, y1, z1, x2, y1, z1, color);
      line(pose, buffer, x2, y1, z1, x2, y1, z2, color);
      line(pose, buffer, x2, y1, z2, x1, y1, z2, color);
      line(pose, buffer, x1, y1, z2, x1, y1, z1, color);
      line(pose, buffer, x1, y2, z1, x2, y2, z1, color);
      line(pose, buffer, x2, y2, z1, x2, y2, z2, color);
      line(pose, buffer, x2, y2, z2, x1, y2, z2, color);
      line(pose, buffer, x1, y2, z2, x1, y2, z1, color);
      line(pose, buffer, x1, y1, z1, x1, y2, z1, color);
      line(pose, buffer, x2, y1, z1, x2, y2, z1, color);
      line(pose, buffer, x2, y1, z2, x2, y2, z2, color);
      line(pose, buffer, x1, y1, z2, x1, y2, z2, color);
   }

   private static void line(Pose pose, VertexConsumer buffer, double x1, double y1, double z1, double x2, double y2, double z2, int color) {
      RiptideWorldGeometry.line(pose, buffer, x1, y1, z1, x2, y2, z2, color, 2.0F);
   }

   private static void fillBox(Pose pose, VertexConsumer buffer, AABB box, int color) {
      quad(pose, buffer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, color);
      quad(pose, buffer, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, box.minX, box.maxY, box.minZ, color);
      quad(pose, buffer, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color);
      quad(pose, buffer, box.maxX, box.minY, box.minZ, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, color);
      quad(pose, buffer, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, color);
      quad(pose, buffer, box.maxX, box.minY, box.maxZ, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color);
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

   private static boolean drawLines(String shape) {
      return !"Sides".equalsIgnoreCase(shape);
   }

   private static boolean drawSides(String shape) {
      return "Both".equalsIgnoreCase(shape) || "Sides".equalsIgnoreCase(shape);
   }
}
