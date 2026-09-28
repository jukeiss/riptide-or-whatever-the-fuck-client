package riptide.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class RiptideRotationUtil {
   private RiptideRotationUtil() {
   }

   public static RiptideRotationUtil.Rotation playerRotation(LocalPlayer player) {
      return new RiptideRotationUtil.Rotation(player.getYRot(), player.getXRot());
   }

   public static RiptideRotationUtil.Rotation lookingAt(Vec3 point, Vec3 from) {
      Vec3 diff = point.subtract(from);
      double horiz = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
      float yaw = (float)(Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
      float pitch = (float)(-Math.toDegrees(Math.atan2(diff.y, horiz)));
      return new RiptideRotationUtil.Rotation(Mth.wrapDegrees(yaw), Mth.clamp(Mth.wrapDegrees(pitch), -90.0F, 90.0F));
   }

   public static RiptideRotationUtil.Rotation towardsLinear(
      RiptideRotationUtil.Rotation current, RiptideRotationUtil.Rotation target, float horizontalFactor, float verticalFactor
   ) {
      float yawDiff = angleDifference(target.yaw, current.yaw);
      float pitchDiff = angleDifference(target.pitch, current.pitch);
      double length = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
      if (length < 1.0E-9) {
         return target;
      } else {
         float yawStep = Math.abs((float)(yawDiff / length)) * horizontalFactor;
         float pitchStep = Math.abs((float)(pitchDiff / length)) * verticalFactor;
         return new RiptideRotationUtil.Rotation(
            current.yaw + Mth.clamp(yawDiff, -yawStep, yawStep), current.pitch + Mth.clamp(pitchDiff, -pitchStep, pitchStep)
         );
      }
   }

   public static RiptideRotationUtil.Rotation interpolate(RiptideRotationUtil.Rotation current, RiptideRotationUtil.Rotation target, float factor) {
      float t = Mth.clamp(factor, 0.0F, 1.0F);
      return new RiptideRotationUtil.Rotation(
         current.yaw + angleDifference(target.yaw, current.yaw) * t, Mth.clamp(current.pitch + angleDifference(target.pitch, current.pitch) * t, -90.0F, 90.0F)
      );
   }

   public static RiptideRotationUtil.Rotation normalizeToSensitivity(RiptideRotationUtil.Rotation rotation, RiptideRotationUtil.Rotation current) {
      double gcd = mouseDegreesPerRawInput();
      if (gcd <= 0.0) {
         return rotation;
      } else {
         float yawDiff = angleDifference(rotation.yaw, current.yaw);
         float pitchDiff = angleDifference(rotation.pitch, current.pitch);
         float yaw = current.yaw + (float)(Math.round(yawDiff / gcd) * gcd);
         float pitch = current.pitch + (float)(Math.round(pitchDiff / gcd) * gcd);
         return new RiptideRotationUtil.Rotation(yaw, Mth.clamp(pitch, -90.0F, 90.0F));
      }
   }

   public static double sensitivityGcd() {
      return mouseDegreesPerRawInput();
   }

   public static double mouseDegreesPerRawInput() {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.options != null) {
         double f = (Double)mc.options.sensitivity().get() * 0.6 + 0.2;
         float sensitivityFactor = (float)(f * f * f * 8.0);
         return sensitivityFactor * 0.15F;
      } else {
         return 0.0;
      }
   }

   public static float angleDifference(float target, float current) {
      return Mth.wrapDegrees(target - current);
   }

   public static float angleTo(RiptideRotationUtil.Rotation current, RiptideRotationUtil.Rotation target) {
      float yaw = angleDifference(target.yaw, current.yaw);
      float pitch = angleDifference(target.pitch, current.pitch);
      return (float)Math.min(180.0, Math.sqrt(yaw * yaw + pitch * pitch));
   }

   public static float rotationAngleTo(RiptideRotationUtil.Rotation a, RiptideRotationUtil.Rotation b) {
      Vec3 first = Vec3.directionFromRotation(a.pitch(), a.yaw());
      Vec3 second = Vec3.directionFromRotation(b.pitch(), b.yaw());
      double cosine = Mth.clamp(first.dot(second), -1.0, 1.0);
      return (float)Math.toDegrees(Math.acos(cosine));
   }

   public static void apply(LocalPlayer player, RiptideRotationUtil.Rotation rotation, boolean syncPrevious) {
      if (player != null && rotation != null) {
         float yaw = Mth.wrapDegrees(rotation.yaw);
         float pitch = Mth.clamp(rotation.pitch, -90.0F, 90.0F);
         if (syncPrevious) {
            player.yRotO = yaw;
            player.xRotO = pitch;
            player.setYHeadRot(yaw);
            player.yHeadRotO = yaw;
            player.setYBodyRot(yaw);
            player.yBodyRotO = yaw;
         } else {
            player.yRotO = player.getYRot();
            player.xRotO = player.getXRot();
         }

         player.setYRot(yaw);
         player.setXRot(pitch);
      }
   }

   public record Rotation(float yaw, float pitch) {
   }
}
