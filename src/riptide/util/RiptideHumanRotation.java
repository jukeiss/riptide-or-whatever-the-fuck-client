package riptide.util;

import java.util.Objects;
import java.util.Random;

public final class RiptideHumanRotation {
   private static final double FALLBACK_GCD = 0.15;
   private static final double REROLL_GOAL_DEGREES = 4.0;
   private static final double PEAK_MIN = 0.7;
   private static final double PEAK_SPAN = 0.3;
   private static final double EASE_MIN = 0.55;
   private static final double EASE_SPAN = 0.35;
   private static final double ACCEL_MIN = 8.0;
   private static final double ACCEL_SPAN = 10.0;
   private static final double TAIL_CUT_DEGREES = 3.0;
   private static final double OFFSET_MIN = 0.75;
   private static final double OFFSET_SPAN = 1.25;
   private static final double ARRIVE_DEGREES = 0.5;
   private static final double IDENTICAL_DELTA_MIN = 1.25;
   private static final double JITTER_PROBABILITY = 0.2;
   private static final double DITHER_OTHER_MIN = 2.75;
   private static final double DITHER_PROBABILITY = 0.5;
   private static final double PITCH_LIMIT = 89.9;

   private RiptideHumanRotation() {
   }

   public static double settleBandDegrees(double gcd) {
      return 0.5 + (gcd > 0.0 ? gcd : 0.15);
   }

   public static boolean isInitialized(RiptideHumanRotation.Stream s) {
      return s != null && s.initialized;
   }

   public static RiptideRotationUtil.Rotation current(RiptideHumanRotation.Stream s) {
      return isInitialized(s) ? new RiptideRotationUtil.Rotation((float)s.yawAcc, (float)s.pitchAcc) : null;
   }

   public static void seed(RiptideHumanRotation.Stream s, RiptideRotationUtil.Rotation from) {
      Objects.requireNonNull(s, "stream");
      Objects.requireNonNull(from, "from");
      s.yawAcc = from.yaw();
      s.pitchAcc = from.pitch();
      s.lastStepYaw = 0.0;
      s.lastStepPitch = 0.0;
      s.lastKYaw = 0;
      s.lastKPitch = 0;
      s.active = null;
      s.pending = null;
      s.lastGoalYaw = from.yaw();
      s.lastGoalPitch = from.pitch();
      s.initialized = true;
   }

   public static void clear(RiptideHumanRotation.Stream s) {
      Objects.requireNonNull(s, "stream");
      s.initialized = false;
      s.yawAcc = 0.0;
      s.pitchAcc = 0.0;
      s.lastStepYaw = 0.0;
      s.lastStepPitch = 0.0;
      s.lastKYaw = 0;
      s.lastKPitch = 0;
      s.active = null;
      s.pending = null;
      s.lastGoalYaw = 0.0;
      s.lastGoalPitch = 0.0;
   }

   public static RiptideHumanRotation.Step compute(
      RiptideHumanRotation.Stream s, RiptideRotationUtil.Rotation goal, float maxYawStep, float maxPitchStep, double gcd
   ) {
      return compute(s, goal, maxYawStep, maxPitchStep, gcd, true);
   }

   public static RiptideHumanRotation.Step compute(
      RiptideHumanRotation.Stream s, RiptideRotationUtil.Rotation goal, float maxYawStep, float maxPitchStep, double gcd, boolean applyGoalOffset
   ) {
      return compute(s, goal, maxYawStep, maxPitchStep, gcd, applyGoalOffset, RiptideHumanRotation.MotionProfile.STANDARD);
   }

   public static RiptideHumanRotation.Step compute(
      RiptideHumanRotation.Stream s,
      RiptideRotationUtil.Rotation goal,
      float maxYawStep,
      float maxPitchStep,
      double gcd,
      boolean applyGoalOffset,
      RiptideHumanRotation.MotionProfile profile
   ) {
      Objects.requireNonNull(s, "stream");
      Objects.requireNonNull(goal, "goal");
      Objects.requireNonNull(profile, "profile");
      if (!s.initialized) {
         throw new IllegalStateException("stream is not seeded; seed() from the current server rotation first");
      } else {
         double g = gcd > 0.0 ? gcd : 0.15;
         RiptideHumanRotation.Rolls rolls;
         if (rollsStale(s, goal, profile)) {
            s.pending = roll(s.random, goal, profile);
            rolls = s.pending;
         } else {
            s.pending = null;
            rolls = s.active;
         }

         s.lastGoalYaw = goal.yaw();
         s.lastGoalPitch = goal.pitch();
         double effectiveYaw = goal.yaw() + (applyGoalOffset ? rolls.offYaw : 0.0);
         double effectivePitch = clampPitch(goal.pitch() + (applyGoalOffset ? rolls.offPitch : 0.0));
         RiptideHumanRotation.Axis yawAxis = new RiptideHumanRotation.Axis(
            wrapDegrees(effectiveYaw - s.yawAcc), maxYawStep, g, s.lastStepYaw, rolls.accel, Double.NaN
         );
         RiptideHumanRotation.Axis pitchAxis = new RiptideHumanRotation.Axis(
            effectivePitch - s.pitchAcc, maxPitchStep, g, s.lastStepPitch, rolls.accel, s.pitchAcc
         );
         int kYaw = quantize(yawAxis, s.lastKYaw, rolls.peakYaw, rolls.ease, s.random);
         int kPitch = quantize(pitchAxis, s.lastKPitch, rolls.peakPitch, rolls.ease, s.random);
         if (kYaw == 0 && Math.abs(kPitch * g) > 2.75 && yawAxis.converged() && s.random.nextDouble() < 0.5) {
            kYaw = dither(yawAxis, s.random);
         }

         if (kPitch == 0 && Math.abs(kYaw * g) > 2.75 && pitchAxis.converged() && s.random.nextDouble() < 0.5) {
            kPitch = dither(pitchAxis, s.random);
         }

         return new RiptideHumanRotation.Step(s, kYaw, kPitch, g);
      }
   }

   public static RiptideRotationUtil.Rotation apply(RiptideHumanRotation.Stream s, RiptideHumanRotation.Step step) {
      Objects.requireNonNull(s, "stream");
      Objects.requireNonNull(step, "step");
      if (!s.initialized) {
         throw new IllegalStateException("stream is not seeded; seed() first");
      } else if (step.owner != s) {
         throw new IllegalArgumentException("step was computed on a different stream");
      } else {
         if (s.pending != null) {
            s.active = s.pending;
            s.pending = null;
         }

         s.yawAcc = s.yawAcc + step.kYaw * step.gcd;
         s.pitchAcc = clampPitch(s.pitchAcc + step.kPitch * step.gcd);
         s.lastStepYaw = Math.abs(step.kYaw * step.gcd);
         s.lastStepPitch = Math.abs(step.kPitch * step.gcd);
         s.lastKYaw = step.kYaw;
         s.lastKPitch = step.kPitch;
         return current(s);
      }
   }

   public static RiptideRotationUtil.Rotation step(
      RiptideHumanRotation.Stream s, RiptideRotationUtil.Rotation goal, float maxYawStep, float maxPitchStep, double gcd
   ) {
      return step(s, goal, maxYawStep, maxPitchStep, gcd, true);
   }

   public static RiptideRotationUtil.Rotation step(
      RiptideHumanRotation.Stream s, RiptideRotationUtil.Rotation goal, float maxYawStep, float maxPitchStep, double gcd, boolean applyGoalOffset
   ) {
      return step(s, goal, maxYawStep, maxPitchStep, gcd, applyGoalOffset, RiptideHumanRotation.MotionProfile.STANDARD);
   }

   public static RiptideRotationUtil.Rotation step(
      RiptideHumanRotation.Stream s,
      RiptideRotationUtil.Rotation goal,
      float maxYawStep,
      float maxPitchStep,
      double gcd,
      boolean applyGoalOffset,
      RiptideHumanRotation.MotionProfile profile
   ) {
      Objects.requireNonNull(s, "stream");
      Objects.requireNonNull(goal, "goal");
      Objects.requireNonNull(profile, "profile");
      if (!s.initialized) {
         seed(s, goal);
         return current(s);
      } else {
         return apply(s, compute(s, goal, maxYawStep, maxPitchStep, gcd, applyGoalOffset, profile));
      }
   }

   static double[] effectiveGoalForTesting(RiptideHumanRotation.Stream s) {
      return isInitialized(s) && s.active != null ? new double[]{s.lastGoalYaw + s.active.offYaw, clampPitch(s.lastGoalPitch + s.active.offPitch)} : null;
   }

   private static RiptideHumanRotation.Rolls roll(Random random, RiptideRotationUtil.Rotation goal, RiptideHumanRotation.MotionProfile profile) {
      RiptideHumanRotation.Rolls rolls = new RiptideHumanRotation.Rolls();
      rolls.profile = profile;
      rolls.peakYaw = profile.peakMin + profile.peakSpan * random.nextDouble();
      rolls.peakPitch = profile.peakMin + profile.peakSpan * random.nextDouble();
      rolls.ease = profile.easeMin + profile.easeSpan * random.nextDouble();
      rolls.accel = profile.accelMin + profile.accelSpan * random.nextDouble();
      rolls.offYaw = rollOffset(random);
      rolls.offPitch = rollOffset(random);
      rolls.goalYaw = goal.yaw();
      rolls.goalPitch = goal.pitch();
      return rolls;
   }

   private static double rollOffset(Random random) {
      double magnitude = 0.75 + 1.25 * random.nextDouble();
      return random.nextBoolean() ? magnitude : -magnitude;
   }

   private static boolean rollsStale(RiptideHumanRotation.Stream s, RiptideRotationUtil.Rotation goal, RiptideHumanRotation.MotionProfile profile) {
      if (s.active == null) {
         return true;
      } else if (s.active.profile != profile) {
         return true;
      } else {
         double dYaw = wrapDegrees(goal.yaw() - s.active.goalYaw);
         double dPitch = goal.pitch() - s.active.goalPitch;
         return Math.max(Math.abs(dYaw), Math.abs(dPitch)) > 4.0;
      }
   }

   private static int quantize(RiptideHumanRotation.Axis axis, int lastK, double peak, double ease, Random random) {
      double desired = axis.absRemaining <= 3.0 ? axis.absRemaining : Math.min(Math.min(axis.absRemaining * ease, axis.cap * peak), axis.rampLimit);
      int k = axis.direction * (int)Math.round(desired / axis.gcd);
      if (!axis.converged() && (k == 0 || Integer.signum(k) != axis.direction)) {
         k = axis.direction;
      }

      while (k != 0 && !axis.valid(k)) {
         k -= Integer.signum(k);
      }

      if (k == lastK && Math.abs(k * axis.gcd) > 1.25) {
         int first = random.nextBoolean() ? 1 : -1;
         if (axis.valid(k + first)) {
            k += first;
         } else if (axis.valid(k - first)) {
            k -= first;
         }
      }

      if (random.nextDouble() < 0.2) {
         int first = random.nextBoolean() ? 1 : -1;
         if (axis.valid(k + first) && keepsAimC(k + first, lastK, axis.gcd)) {
            k += first;
         } else if (axis.valid(k - first) && keepsAimC(k - first, lastK, axis.gcd)) {
            k -= first;
         }
      }

      if (k != 0 && onAimBMultiple(Math.abs(k * axis.gcd))) {
         int first = random.nextBoolean() ? 1 : -1;
         if (axis.valid(k + first) && keepsAimC(k + first, lastK, axis.gcd) && !onAimBMultiple(Math.abs((k + first) * axis.gcd))) {
            k += first;
         } else if (axis.valid(k - first) && keepsAimC(k - first, lastK, axis.gcd) && !onAimBMultiple(Math.abs((k - first) * axis.gcd))) {
            k -= first;
         }
      }

      return k;
   }

   private static boolean keepsAimC(int k, int lastK, double gcd) {
      return k != lastK || Math.abs(k * gcd) <= 1.25;
   }

   private static int dither(RiptideHumanRotation.Axis axis, Random random) {
      int sign = random.nextBoolean() ? 1 : -1;
      if (axis.valid(sign)) {
         return sign;
      } else {
         return axis.valid(-sign) ? -sign : 0;
      }
   }

   private static boolean onAimBMultiple(double absDelta) {
      return Math.abs(absDelta - 0.1 * Math.round(absDelta / 0.1)) <= 1.0E-6 || Math.abs(absDelta - 0.25 * Math.round(absDelta / 0.25)) <= 1.0E-6;
   }

   private static double wrapDegrees(double degrees) {
      double wrapped = degrees % 360.0;
      if (wrapped >= 180.0) {
         wrapped -= 360.0;
      }

      if (wrapped < -180.0) {
         wrapped += 360.0;
      }

      return wrapped;
   }

   private static double clampPitch(double pitch) {
      return Math.max(-89.9, Math.min(89.9, pitch));
   }

   private static final class Axis {
      final double absRemaining;
      final int direction;
      final double cap;
      final double gcd;
      final double rampLimit;
      final double pitchAcc;
      final boolean pitchPinned;

      Axis(double remaining, float cap, double gcd, double lastStep, double accel, double pitchAcc) {
         this.absRemaining = Math.abs(remaining);
         this.direction = remaining > 0.0 ? 1 : (remaining < 0.0 ? -1 : 0);
         this.cap = cap;
         this.gcd = gcd;
         this.rampLimit = lastStep + accel;
         this.pitchAcc = pitchAcc;
         this.pitchPinned = !Double.isNaN(pitchAcc)
            && this.direction != 0
            && Math.abs(pitchAcc) <= 89.90000000100001
            && Math.abs(pitchAcc + this.direction * gcd) > 89.90000000100001;
      }

      boolean converged() {
         return this.absRemaining <= 0.5 || this.pitchPinned;
      }

      boolean valid(int k) {
         double delta = Math.abs(k * this.gcd);
         if (delta > this.cap) {
            return false;
         } else if (delta > this.absRemaining + this.gcd + 1.0E-9) {
            return false;
         } else if (delta > this.rampLimit + 1.0E-9) {
            return false;
         } else {
            return this.converged() || k != 0 && Integer.signum(k) == this.direction
               ? Double.isNaN(this.pitchAcc) || !(Math.abs(this.pitchAcc + k * this.gcd) > 89.90000000100001)
               : false;
         }
      }
   }

   public static enum MotionProfile {
      STANDARD(0.7, 0.3, 0.55, 0.35, 8.0, 10.0),
      TELLY_FLICK(0.97, 0.03, 0.97, 0.03, 60.0, 8.0),
      TELLY_AIR_FLICK(0.95, 0.05, 0.985, 0.015, 68.0, 8.0),
      SURROUND_FAST_2(0.8, 0.2, 0.65, 0.3, 11.0, 7.0),
      SURROUND_FAST_3(0.9, 0.1, 0.75, 0.24, 14.0, 4.0),
      SURROUND_FAST_4(0.95, 0.05, 0.91, 0.08, 31.5, 9.0),
      SURROUND_FAST_5(0.97, 0.03, 0.97, 0.02, 62.0, 18.0),
      BED_SHELL(0.88, 0.12, 0.982, 0.016, 46.0, 12.0);

      private final double peakMin;
      private final double peakSpan;
      private final double easeMin;
      private final double easeSpan;
      private final double accelMin;
      private final double accelSpan;

      private MotionProfile(double peakMin, double peakSpan, double easeMin, double easeSpan, double accelMin, double accelSpan) {
         this.peakMin = peakMin;
         this.peakSpan = peakSpan;
         this.easeMin = easeMin;
         this.easeSpan = easeSpan;
         this.accelMin = accelMin;
         this.accelSpan = accelSpan;
      }
   }

   private static final class Rolls {
      RiptideHumanRotation.MotionProfile profile;
      double peakYaw;
      double peakPitch;
      double ease;
      double accel;
      double offYaw;
      double offPitch;
      double goalYaw;
      double goalPitch;
   }

   public static final class Step {
      private final RiptideHumanRotation.Stream owner;
      private final int kYaw;
      private final int kPitch;
      private final double gcd;

      private Step(RiptideHumanRotation.Stream owner, int kYaw, int kPitch, double gcd) {
         this.owner = owner;
         this.kYaw = kYaw;
         this.kPitch = kPitch;
         this.gcd = gcd;
      }

      public RiptideRotationUtil.Rotation preview() {
         return new RiptideRotationUtil.Rotation(
            (float)(this.owner.yawAcc + this.kYaw * this.gcd), (float)RiptideHumanRotation.clampPitch(this.owner.pitchAcc + this.kPitch * this.gcd)
         );
      }
   }

   public static final class Stream {
      private final Random random;
      private boolean initialized;
      private double yawAcc;
      private double pitchAcc;
      private double lastStepYaw;
      private double lastStepPitch;
      private int lastKYaw;
      private int lastKPitch;
      private RiptideHumanRotation.Rolls active;
      private RiptideHumanRotation.Rolls pending;
      private double lastGoalYaw;
      private double lastGoalPitch;

      public Stream() {
         this(new Random());
      }

      public Stream(Random random) {
         this.random = Objects.requireNonNull(random, "random");
      }
   }
}
