package riptide.security;

import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

public final class RiptideNumericSanity {
   public static final double SANE_LIMIT = 1.0E9;
   public static final double MAX_MOTION_PER_AXIS = 10000.0;

   private RiptideNumericSanity() {
   }

   public static boolean outOfRange(double value) {
      return Double.isNaN(value) || Double.isInfinite(value) || Math.abs(value) > 1.0E9;
   }

   public static boolean outOfRange(Vec3 vec) {
      return vec == null || outOfRange(vec.x) || outOfRange(vec.y) || outOfRange(vec.z);
   }

   public static boolean motionOutOfRange(double value) {
      return Double.isNaN(value) || Double.isInfinite(value) || Math.abs(value) > 10000.0;
   }

   public static boolean motionOutOfRange(Vec3 vec) {
      return vec == null || motionOutOfRange(vec.x) || motionOutOfRange(vec.y) || motionOutOfRange(vec.z);
   }

   public static boolean positionMoveOutOfRange(PositionMoveRotation change) {
      return change == null
         || outOfRange(change.position())
         || motionOutOfRange(change.deltaMovement())
         || outOfRange(change.yRot())
         || outOfRange(change.xRot());
   }
}
