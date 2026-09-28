package riptide.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.VoxelShape;
import riptide.modules.PackFreecamState;
import riptide.modules.PackHideState;

public final class RiptideFreecamHighlightRenderer {
   private static final int REACHABLE_LINE = -13248397;
   private static final int REACHABLE_FILL = 775280755;
   private static final int UNREACHABLE_LINE = -1946548;
   private static final int UNREACHABLE_FILL = 786582604;
   private static final float LINE_WIDTH = 2.0F;
   private static final double INFLATE = 0.0025;
   private static final double ENTITY_INFLATE = 0.04;
   private static final double REACH_PADDING = 1.0;
   private static final double ENTITY_REACH_PADDING = 3.0;

   private RiptideFreecamHighlightRenderer() {
   }

   public static boolean isReplacingVanillaOutline() {
      return PackFreecamState.isActive() && PackFreecamState.interactEnabled() && !PackHideState.isHardLocked();
   }

   public static void initialize() {
      LevelRenderEvents.COLLECT_SUBMITS
         .register(
            (CollectSubmits)context -> {
               if (isReplacingVanillaOutline()) {
                  Minecraft mc = Minecraft.getInstance();
                  if (mc != null && mc.level != null && mc.player != null) {
                     CameraRenderState cameraState = context.levelState().cameraRenderState;
                     Vec3 origin = cameraState.pos;
                     Vec3 direction = Vec3.directionFromRotation(cameraState.xRot, cameraState.yRot);
                     double blockRange = Math.max(4.5, mc.player.blockInteractionRange());
                     BlockHitResult blockHit = mc.level
                        .clip(new ClipContext(origin, origin.add(direction.scale(blockRange)), Block.OUTLINE, Fluid.NONE, mc.player));
                     boolean hasBlock = blockHit != null && blockHit.getType() == Type.BLOCK;
                     double blockDistSq = hasBlock ? origin.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;
                     double entityRange = Math.max(3.0, mc.player.entityInteractionRange());
                     Vec3 entityEnd = origin.add(direction.scale(entityRange));
                     AABB entitySearch = new AABB(origin, entityEnd).inflate(1.0);
                     EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                        mc.player, origin, entityEnd, entitySearch, EntitySelector.CAN_BE_PICKED, entityRange * entityRange
                     );
                     double entityDistSq = entityHit != null ? origin.distanceToSqr(entityHit.getLocation()) : Double.MAX_VALUE;
                     PoseStack poseStack = context.poseStack();
                     if (entityHit != null && entityDistSq <= blockDistSq) {
                        Entity target = entityHit.getEntity();
                        boolean reachable = mc.player.isWithinEntityInteractionRange(target, 3.0);
                        int lineColor = reachable ? -13248397 : -1946548;
                        int fillColor = reachable ? 775280755 : 786582604;
                        AABB box = target.getBoundingBox().inflate(0.04).move(-origin.x, -origin.y, -origin.z);
                        context.submitNodeCollector()
                           .submitCustomGeometry(
                              poseStack, RiptideRenderTypes.storageEspFillSeeThrough(), (pose, buffer) -> fillBox(pose, buffer, box, fillColor)
                           );
                        context.submitNodeCollector()
                           .submitCustomGeometry(
                              poseStack, RiptideRenderTypes.storageEspLinesSeeThrough(), (pose, buffer) -> renderBox(pose, buffer, box, lineColor)
                           );
                     } else if (hasBlock) {
                        BlockPos pos = blockHit.getBlockPos();
                        boolean reachable = mc.player.isWithinBlockInteractionRange(pos, 1.0);
                        int lineColor = reachable ? -13248397 : -1946548;
                        int fillColor = reachable ? 775280755 : 786582604;
                        AABB box = targetBox(mc, pos).inflate(0.0025).move(-origin.x, -origin.y, -origin.z);
                        context.submitNodeCollector()
                           .submitCustomGeometry(
                              poseStack, RiptideRenderTypes.storageEspFillSeeThrough(), (pose, buffer) -> fillBox(pose, buffer, box, fillColor)
                           );
                        context.submitNodeCollector()
                           .submitCustomGeometry(
                              poseStack, RiptideRenderTypes.storageEspLinesSeeThrough(), (pose, buffer) -> renderBox(pose, buffer, box, lineColor)
                           );
                     }
                  }
               }
            }
         );
   }

   private static AABB targetBox(Minecraft mc, BlockPos pos) {
      try {
         VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
         if (!shape.isEmpty()) {
            return shape.bounds().move(pos.getX(), pos.getY(), pos.getZ());
         }
      } catch (Throwable var3) {
      }

      return new AABB(pos);
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
}
