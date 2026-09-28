package riptide.util;

public final class QuantizedRotationSmoother {
   private static final double EPSILON = 1.0E-8;
   private static final double MIN_ACCELERATION = 0.65;
   private double yawVelocity;
   private double pitchVelocity;
   private double yawNoise;
   private double pitchNoise;
   private double curvePhase;
   private long previousYawCounts;
   private long previousPitchCounts;
   private long randomState = 7640891576956012809L;
   private boolean hasPreviousStep;
   private boolean repeatPolarity;

   public void reset() {
      this.reset(7640891576956012809L);
   }

   public void reset(long seed) {
      this.randomState = mixSeed(seed);
      this.halt();
      this.curvePhase = this.unitRandom() * Math.PI * 2.0;
   }

   public void halt() {
      this.yawVelocity = 0.0;
      this.pitchVelocity = 0.0;
      this.yawNoise = 0.0;
      this.pitchNoise = 0.0;
      this.previousYawCounts = 0L;
      this.previousPitchCounts = 0L;
      this.hasPreviousStep = false;
      this.repeatPolarity = false;
   }

   public QuantizedRotationSmoother.Step step(
      float yawErrorDegrees,
      float pitchErrorDegrees,
      double degreesPerCount,
      float horizontalSpeed,
      float verticalSpeed,
      float directionChange,
      float midpoint,
      boolean allowYaw,
      boolean allowPitch
   ) {
      return this.stepWithLimits(
         yawErrorDegrees,
         pitchErrorDegrees,
         degreesPerCount,
         speedLimit(horizontalSpeed),
         speedLimit(verticalSpeed),
         1.0,
         directionChange,
         midpoint,
         allowYaw,
         allowPitch
      );
   }

   public QuantizedRotationSmoother.Step stepFast(
      float yawErrorDegrees,
      float pitchErrorDegrees,
      double degreesPerCount,
      float horizontalDegreesPerTick,
      float verticalDegreesPerTick,
      float directionChange,
      float midpoint,
      boolean allowYaw,
      boolean allowPitch
   ) {
      return Double.isFinite(degreesPerCount) && !(degreesPerCount <= 1.0E-8)
         ? this.stepWithLimits(
            yawErrorDegrees,
            pitchErrorDegrees,
            degreesPerCount,
            degreeSpeedLimit(horizontalDegreesPerTick, degreesPerCount),
            degreeSpeedLimit(verticalDegreesPerTick, degreesPerCount),
            1.3,
            directionChange,
            midpoint,
            allowYaw,
            allowPitch
         )
         : this.zeroStep();
   }

   public QuantizedRotationSmoother.Step stepCapped(
      float yawErrorDegrees,
      float pitchErrorDegrees,
      double degreesPerCount,
      float horizontalDegreesPerTick,
      float verticalDegreesPerTick,
      float accelerationDegreesPerTick,
      float directionChange,
      float midpoint,
      boolean allowYaw,
      boolean allowPitch
   ) {
      if (Double.isFinite(degreesPerCount) && !(degreesPerCount <= 1.0E-8)) {
         double cap = Math.max(Math.max(0.01, (double)horizontalDegreesPerTick), Math.max(0.01, (double)verticalDegreesPerTick));
         double acceleration = Double.isFinite(accelerationDegreesPerTick) ? Math.max(cap, (double)accelerationDegreesPerTick) : cap;
         return this.stepWithLimits(
            yawErrorDegrees,
            pitchErrorDegrees,
            degreesPerCount,
            degreeSpeedLimit(horizontalDegreesPerTick, degreesPerCount),
            degreeSpeedLimit(verticalDegreesPerTick, degreesPerCount),
            Math.max(1.3, acceleration / cap),
            directionChange,
            midpoint,
            allowYaw,
            allowPitch
         );
      } else {
         return this.zeroStep();
      }
   }

   private QuantizedRotationSmoother.Step stepWithLimits(
      float yawErrorDegrees,
      float pitchErrorDegrees,
      double degreesPerCount,
      double horizontalLimit,
      double verticalLimit,
      double responseScale,
      float directionChange,
      float midpoint,
      boolean allowYaw,
      boolean allowPitch
   ) {
      if (Double.isFinite(degreesPerCount) && !(degreesPerCount <= 1.0E-8)) {
         long yawError = allowYaw ? Math.round(yawErrorDegrees / degreesPerCount) : 0L;
         long pitchError = allowPitch ? Math.round(pitchErrorDegrees / degreesPerCount) : 0L;
         if (!allowYaw) {
            this.yawVelocity = 0.0;
         }

         if (!allowPitch) {
            this.pitchVelocity = 0.0;
         }

         double direction = clamp(directionChange, 0.0, 1.0);
         double curvePoint = clamp(midpoint, 0.0, 1.0);
         this.yawVelocity = this.advanceVelocity(this.yawVelocity, yawError, horizontalLimit, direction, curvePoint, responseScale);
         this.pitchVelocity = this.advanceVelocity(this.pitchVelocity, pitchError, verticalLimit, direction, curvePoint, responseScale);
         this.yawNoise = this.yawNoise * 0.72 + this.centeredRandom() * 0.28;
         this.pitchNoise = this.pitchNoise * 0.67 + this.centeredRandom() * 0.33;
         this.curvePhase = this.curvePhase + (0.51 + this.unitRandom() * 0.23);
         double distance = Math.hypot(yawError, pitchError);
         double yawCandidate = this.yawVelocity;
         double pitchCandidate = this.pitchVelocity;
         if (allowYaw && allowPitch && distance >= 7.0) {
            double curvature = Math.sin(this.curvePhase) * Math.min(1.15, 0.32 + Math.sqrt(distance) * 0.045) * (0.55 + direction * 0.45);
            yawCandidate += -pitchError / distance * curvature;
            pitchCandidate += yawError / distance * curvature;
         }

         double noiseScale = distance >= 5.0 ? 0.72 + direction * 0.28 : 0.0;
         yawCandidate += this.yawNoise * noiseScale;
         pitchCandidate += this.pitchNoise * noiseScale * 0.83;
         long yawCounts = boundedCounts(yawCandidate, yawError);
         long pitchCounts = boundedCounts(pitchCandidate, pitchError);
         QuantizedRotationSmoother.Step varied = this.varyRepeatedStep(yawCounts, pitchCounts, yawError, pitchError);
         this.previousYawCounts = varied.yawCounts;
         this.previousPitchCounts = varied.pitchCounts;
         this.hasPreviousStep = true;
         return varied;
      } else {
         return this.zeroStep();
      }
   }

   private QuantizedRotationSmoother.Step zeroStep() {
      this.yawVelocity = 0.0;
      this.pitchVelocity = 0.0;
      this.previousYawCounts = 0L;
      this.previousPitchCounts = 0L;
      this.hasPreviousStep = false;
      return new QuantizedRotationSmoother.Step(0L, 0L);
   }

   private double advanceVelocity(double velocity, long error, double speedLimit, double directionChange, double midpoint, double responseScale) {
      if (error == 0L) {
         return approach(velocity, 0.0, Math.max(0.65, speedLimit * 0.28));
      } else {
         double acceleration = Math.max(0.65, speedLimit * (0.14 + (1.0 - midpoint) * 0.1 + directionChange * 0.06)) * Math.max(1.0, responseScale);
         double brakingAcceleration = acceleration * (0.72 + midpoint * 0.75);
         double brakingSpeed = Math.sqrt(2.0 * brakingAcceleration * Math.abs((double)error));
         double desired = Math.copySign(Math.min(speedLimit, brakingSpeed), (double)error);
         return approach(velocity, desired, acceleration);
      }
   }

   private QuantizedRotationSmoother.Step varyRepeatedStep(long yaw, long pitch, long yawError, long pitchError) {
      if (this.hasPreviousStep && (yaw != 0L || pitch != 0L) && yaw == this.previousYawCounts && pitch == this.previousPitchCounts) {
         boolean preferYaw = Math.abs(yawError) - Math.abs(yaw) >= Math.abs(pitchError) - Math.abs(pitch);
         long variedYaw = yaw;
         long variedPitch = pitch;
         if (preferYaw && this.canVary(yaw, yawError)) {
            variedYaw = this.varyCount(yaw, yawError);
         } else if (this.canVary(pitch, pitchError)) {
            variedPitch = this.varyCount(pitch, pitchError);
         } else if (this.canVary(yaw, yawError)) {
            variedYaw = this.varyCount(yaw, yawError);
         }

         this.repeatPolarity = !this.repeatPolarity;
         return new QuantizedRotationSmoother.Step(variedYaw, variedPitch);
      } else {
         return new QuantizedRotationSmoother.Step(yaw, pitch);
      }
   }

   private boolean canVary(long count, long error) {
      return count != 0L && Math.abs(error) > Math.abs(count) + 1L;
   }

   private long varyCount(long count, long error) {
      long direction = Long.signum(error);
      long candidate = count + (this.repeatPolarity ? direction : -direction);
      if (candidate == count || Long.signum(candidate) != direction || Math.abs(candidate) > Math.abs(error)) {
         candidate = count + direction;
      }

      return candidate;
   }

   private static long boundedCounts(double candidate, long error) {
      if (error != 0L && Double.isFinite(candidate)) {
         long rounded = Math.round(candidate);
         long direction = Long.signum(error);
         if (rounded != 0L && Long.signum(rounded) != direction) {
            return 0L;
         } else {
            long magnitude = Math.min(Math.abs(rounded), Math.abs(error));
            if (magnitude == 0L && Math.abs(candidate) >= 0.35) {
               magnitude = 1L;
            }

            return direction * magnitude;
         }
      } else {
         return 0L;
      }
   }

   private static double speedLimit(float configuredSpeed) {
      return 2.0 + 68.0 * clamp(configuredSpeed, 0.01, 1.0);
   }

   private static double degreeSpeedLimit(float degreesPerTick, double degreesPerCount) {
      double degrees = Double.isFinite(degreesPerTick) ? Math.max(0.01, (double)degreesPerTick) : 0.01;
      return Math.max(2.0, degrees / degreesPerCount);
   }

   private static double approach(double current, double target, double amount) {
      return current < target ? Math.min(target, current + amount) : Math.max(target, current - amount);
   }

   private double centeredRandom() {
      return this.unitRandom() * 2.0 - 1.0;
   }

   private double unitRandom() {
      long x = this.randomState;
      x ^= x << 13;
      x ^= x >>> 7;
      x ^= x << 17;
      this.randomState = x;
      return (x >>> 11) * 1.110223E-16F;
   }

   private static long mixSeed(long seed) {
      long value = seed == 0L ? -7046029254386353131L : seed;
      value ^= value >>> 30;
      value *= -4658895280553007687L;
      value ^= value >>> 27;
      value *= -7723592293110705685L;
      value ^= value >>> 31;
      return value == 0L ? -3335678366873096957L : value;
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   public record Step(long yawCounts, long pitchCounts) {
      public RiptideRotationUtil.Rotation asDelta(double degreesPerCount) {
         return Double.isFinite(degreesPerCount) && !(degreesPerCount <= 0.0)
            ? new RiptideRotationUtil.Rotation((float)(this.yawCounts * degreesPerCount), (float)(this.pitchCounts * degreesPerCount))
            : new RiptideRotationUtil.Rotation(0.0F, 0.0F);
      }
   }
}
