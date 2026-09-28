package riptide.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import riptide.modules.Module;
import riptide.modules.ModuleRegistry;
import riptide.modules.ModuleRenderUtil;
import riptide.modules.PackHideState;

public final class RiptideSkeletonRenderer {
   private static final double HIP_Y = 0.72;
   private static final double NECK_Y = 1.35;
   private static final double HEAD_LEN = 0.3;
   private static final double HIP_HALF = 0.11;
   private static final double SHOULDER_HALF = 0.32;
   private static final double LEG_LEN = 0.72;
   private static final double ARM_LEN = 0.66;
   private static final double CROUCH_LEAN = 0.3;
   private static final double CROUCH_ARM = 0.3;
   private static final double DISTANCE_SPAN = 48.0;
   private static final RiptideBufferSource.Holder BUFFERS = new RiptideBufferSource.Holder(786432);
   private static volatile List<RiptideSkeletonRenderer.Bone> pending = List.of();

   private RiptideSkeletonRenderer() {
   }

   public static void initialize() {
      LevelRenderEvents.COLLECT_SUBMITS.register((CollectSubmits)context -> {
         pending = List.of();
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.level != null && mc.player != null) {
            if (!PackHideState.isActive() && !mc.gui.hud.isHidden()) {
               Module esp = ModuleRegistry.get("esp");
               if (esp != null && esp.isEnabled() && Boolean.parseBoolean(esp.value("skeleton"))) {
                  if (!ModuleRenderUtil.shouldSuppressEspForUi()) {
                     boolean distanceColor = "Distance".equals(esp.value("skeleton-color-mode"));
                     int staticColor = ModuleRenderUtil.color(esp, "skeleton-color", -1);
                     float width = (float)Math.max(0.5, Math.min(6.0, parseDouble(esp.value("skeleton-width"), 2.0)));
                     Vec3 camera = context.levelState().cameraRenderState.pos;
                     float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                     List<RiptideSkeletonRenderer.Bone> bones = new ArrayList<>();

                     for (Entity entity : mc.level.entitiesForRendering()) {
                        if (entity instanceof LivingEntity living && !living.isRemoved() && living != mc.player && ModuleRenderUtil.shouldEsp(entity)) {
                           int color = distanceColor ? distanceColor(camera, living, partial) : staticColor;
                           addSkeleton(bones, living, partial, camera, color, width);
                        }
                     }

                     if (!bones.isEmpty()) {
                        pending = bones;
                     }
                  }
               }
            }
         }
      });
   }

   public static boolean hasPending() {
      return !pending.isEmpty();
   }

   public static void flush(PoseStack matrices) {
      List<RiptideSkeletonRenderer.Bone> bones = pending;
      if (!bones.isEmpty()) {
         pending = List.of();
         RiptideBufferSource bufferSource = BUFFERS.get();
         VertexConsumer buffer = bufferSource.getBuffer(RiptideRenderTypes.tracerEspLines());
         Pose pose = matrices.last();

         for (RiptideSkeletonRenderer.Bone bone : bones) {
            drawLine(pose, buffer, bone.a, bone.b, bone.color, bone.width);
         }

         bufferSource.uploadAndDraw();
      }
   }

   private static void addSkeleton(List<RiptideSkeletonRenderer.Bone> bones, LivingEntity e, float partial, Vec3 cam, int color, float width) {
      double px = Mth.lerp(partial, e.xOld, e.getX());
      double py = Mth.lerp(partial, e.yOld, e.getY());
      double pz = Mth.lerp(partial, e.zOld, e.getZ());
      float bodyYaw = e.yBodyRotO + Mth.wrapDegrees(e.yBodyRot - e.yBodyRotO) * partial;
      float headYaw = e.yHeadRotO + Mth.wrapDegrees(e.yHeadRot - e.yHeadRotO) * partial;
      float netHeadYaw = Mth.wrapDegrees(headYaw - bodyYaw);
      float headPitch = e.getViewXRot(partial);
      float limbPos = e.walkAnimation.position(partial);
      float limbSpeed = Math.min(1.0F, e.walkAnimation.speed(partial));
      boolean crouch = e.isCrouching();
      double s = Math.max(0.1, e.getBbHeight() / 1.8);
      double hipY = 0.72 * s;
      double neckY = 1.35 * s;
      double hipHalf = 0.11 * s;
      double shoulderHalf = 0.32 * s;
      double legLen = 0.72 * s;
      double armLen = 0.66 * s;
      double headLen = 0.3 * s;
      float t = limbPos * 0.6662F;
      float legR = Mth.cos(t) * 1.4F * limbSpeed;
      float legL = Mth.cos(t + (float) Math.PI) * 1.4F * limbSpeed;
      double swingR = Math.cos(t) * limbSpeed;
      double swingL = -Math.cos(t) * limbSpeed;
      double age = e.tickCount + partial;
      double bobPitch = Math.sin(age * 0.067) * 0.05;
      double bobRoll = Math.cos(age * 0.09) * 0.03 + 0.03;
      double crouchArm = crouch ? 0.3 : 0.0;
      boolean rightDominant = e.getMainArm() == HumanoidArm.RIGHT;
      boolean rightHeld = !(rightDominant ? e.getMainHandItem() : e.getOffhandItem()).isEmpty();
      boolean leftHeld = !(rightDominant ? e.getOffhandItem() : e.getMainHandItem()).isEmpty();
      boolean using = e.isUsingItem();
      boolean rightUsing = using && e.getUsedItemHand() == InteractionHand.MAIN_HAND == rightDominant;
      boolean leftUsing = using && !rightUsing;
      double pitchR = armPitch(swingR, rightHeld, rightUsing) - bobPitch + crouchArm;
      double pitchL = armPitch(swingL, leftHeld, leftUsing) + bobPitch + crouchArm;
      double[] pelvis = new double[]{0.0, hipY, 0.0};
      double[] neck = new double[]{0.0, neckY, 0.0};
      double[] hipL = new double[]{-hipHalf, hipY, 0.0};
      double[] hipR = new double[]{hipHalf, hipY, 0.0};
      double[] shL = new double[]{-shoulderHalf, neckY, 0.0};
      double[] shR = new double[]{shoulderHalf, neckY, 0.0};
      double[] footL = new double[]{-hipHalf, hipY - legLen * Math.cos(legL), -legLen * Math.sin(legL)};
      double[] footR = new double[]{hipHalf, hipY - legLen * Math.cos(legR), -legLen * Math.sin(legR)};
      double[] handR = armLocal(1.0, shoulderHalf, neckY, armLen, pitchR, bobRoll);
      double[] handL = armLocal(-1.0, shoulderHalf, neckY, armLen, pitchL, bobRoll);
      if (crouch) {
         leanUpper(neck, hipY);
         leanUpper(shL, hipY);
         leanUpper(shR, hipY);
         leanUpper(handL, hipY);
         leanUpper(handR, hipY);
      }

      double[] head = headFrom(neck, headLen, headPitch, netHeadYaw);
      double rad = Math.toRadians(bodyYaw);
      double sin = Math.sin(rad);
      double cos = Math.cos(rad);
      Vec3 wPelvis = world(px, py, pz, pelvis, sin, cos, cam);
      Vec3 wNeck = world(px, py, pz, neck, sin, cos, cam);
      Vec3 wHipL = world(px, py, pz, hipL, sin, cos, cam);
      Vec3 wHipR = world(px, py, pz, hipR, sin, cos, cam);
      Vec3 wShL = world(px, py, pz, shL, sin, cos, cam);
      Vec3 wShR = world(px, py, pz, shR, sin, cos, cam);
      Vec3 wFootL = world(px, py, pz, footL, sin, cos, cam);
      Vec3 wFootR = world(px, py, pz, footR, sin, cos, cam);
      Vec3 wHandL = world(px, py, pz, handL, sin, cos, cam);
      Vec3 wHandR = world(px, py, pz, handR, sin, cos, cam);
      Vec3 wHead = world(px, py, pz, head, sin, cos, cam);
      add(bones, wPelvis, wNeck, color, width);
      add(bones, wHipL, wHipR, color, width);
      add(bones, wShL, wShR, color, width);
      add(bones, wNeck, wHead, color, width);
      add(bones, wShL, wHandL, color, width);
      add(bones, wShR, wHandR, color, width);
      add(bones, wHipL, wFootL, color, width);
      add(bones, wHipR, wFootR, color, width);
   }

   private static double armPitch(double swing, boolean holdingItem, boolean using) {
      if (using) {
         return swing * 0.5 + 0.9;
      } else {
         return holdingItem ? swing * 0.5 + 0.31415927 : swing;
      }
   }

   private static double[] armLocal(double side, double shoulderHalf, double neckY, double armLen, double pitch, double roll) {
      double cp = Math.cos(pitch);
      double sp = Math.sin(pitch);
      double cr = Math.cos(roll);
      double sr = Math.sin(roll);
      return new double[]{side * (shoulderHalf + armLen * cp * sr), neckY - armLen * cp * cr, armLen * sp};
   }

   private static double[] headFrom(double[] neck, double headLen, float headPitch, float netHeadYaw) {
      double hpr = Math.toRadians(headPitch);
      double upY = headLen * Math.cos(hpr);
      double upZ = headLen * Math.sin(hpr);
      double hyr = Math.toRadians(netHeadYaw);
      return new double[]{neck[0] + upZ * Math.sin(hyr), neck[1] + upY, neck[2] + upZ * Math.cos(hyr)};
   }

   private static void leanUpper(double[] p, double pivotY) {
      double ry = p[1] - pivotY;
      double rz = p[2];
      double c = Math.cos(0.3);
      double s = Math.sin(0.3);
      p[1] = pivotY + ry * c - rz * s;
      p[2] = ry * s + rz * c;
   }

   private static Vec3 world(double px, double py, double pz, double[] local, double sin, double cos, Vec3 cam) {
      double lx = local[0];
      double ly = local[1];
      double lz = local[2];
      double wx = px + lx * -cos + lz * -sin;
      double wz = pz + lx * -sin + lz * cos;
      double wy = py + ly;
      return new Vec3(wx - cam.x, wy - cam.y, wz - cam.z);
   }

   private static void drawLine(Pose entry, VertexConsumer buffer, Vec3 a, Vec3 b, int color, float width) {
      Vector3f normal = new Vector3f((float)(b.x - a.x), (float)(b.y - a.y), (float)(b.z - a.z));
      if (!(normal.lengthSquared() <= 1.0E-8F)) {
         normal.normalize();
         float x1 = (float)a.x;
         float y1 = (float)a.y;
         float z1 = (float)a.z;
         float x2 = (float)b.x;
         float y2 = (float)b.y;
         float z2 = (float)b.z;
         buffer.addVertex(entry, x1, y1, z1).setColor(color).setNormal(entry, normal).setLineWidth(width);
         float distToCam = new Vector3f(x1, y1, z1).negate().dot(normal);
         float length = new Vector3f(x2, y2, z2).sub(x1, y1, z1).length();
         if (distToCam > 0.0F && distToCam < length) {
            Vector3f mid = new Vector3f(normal).mul(distToCam).add(x1, y1, z1);
            buffer.addVertex(entry, mid.x, mid.y, mid.z).setColor(color).setNormal(entry, normal).setLineWidth(width);
            buffer.addVertex(entry, mid.x, mid.y, mid.z).setColor(color).setNormal(entry, normal).setLineWidth(width);
         }

         buffer.addVertex(entry, x2, y2, z2).setColor(color).setNormal(entry, normal).setLineWidth(width);
      }
   }

   private static void add(List<RiptideSkeletonRenderer.Bone> bones, Vec3 a, Vec3 b, int color, float width) {
      bones.add(new RiptideSkeletonRenderer.Bone(a, b, color, width));
   }

   private static double parseDouble(String value, double fallback) {
      if (value == null) {
         return fallback;
      } else {
         try {
            return Double.parseDouble(value.trim());
         } catch (NumberFormatException var4) {
            return fallback;
         }
      }
   }

   private static int distanceColor(Vec3 cam, LivingEntity e, float partial) {
      double dx = Mth.lerp(partial, e.xOld, e.getX()) - cam.x;
      double dy = Mth.lerp(partial, e.yOld, e.getY()) - cam.y;
      double dz = Mth.lerp(partial, e.zOld, e.getZ()) - cam.z;
      return ModuleRenderUtil.distanceHueColor(Math.sqrt(dx * dx + dy * dy + dz * dz), 48.0);
   }

   private record Bone(Vec3 a, Vec3 b, int color, float width) {
   }
}
