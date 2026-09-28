package riptide.util;

import java.util.Random;

public final class RiptideHumanRng {
   private static final Random RNG = new Random();

   private RiptideHumanRng() {
   }

   public static long nextIntervalNs(double targetCps, RiptideHumanRng.Profile profile, long debtNanos) {
      return nextIntervalNs(targetCps, profile, debtNanos, new RiptideHumanRng.ClickState());
   }

   public static long nextIntervalNs(double targetCps, RiptideHumanRng.Profile profile, long debtNanos, RiptideHumanRng.ClickState state) {
      double cps = Math.max(0.1, targetCps);
      long mean = Math.max(1L, Math.round(1.0E9 / cps));
      RiptideHumanRng.ClickState profileState = state == null ? new RiptideHumanRng.ClickState() : state;
      double interval;
      if (mean <= 50000000L) {
         interval = mean;
      } else {
         interval = switch (profile == null ? RiptideHumanRng.Profile.NONE : profile) {
            case NONE -> mean;
            case RANDOM -> randomInterval(mean, profileState);
            case RANDOM_PLUS -> randomPlusInterval(mean, profileState);
         };
      }
      double floorMultiplier = switch (profile == null ? RiptideHumanRng.Profile.NONE : profile) {
         case NONE -> 0.1;
         case RANDOM -> 0.6;
         case RANDOM_PLUS -> 0.45;
      };
      long floor = Math.max(1L, Math.round(mean * floorMultiplier));
      long ceiling = Math.max(floor, Math.round(mean * 3.0));
      profileState.lastRawIntervalNanos = Math.max(1L, Math.round(interval));
      long adjusted = Math.round(interval - debtNanos);
      return Math.max(floor, Math.min(ceiling, adjusted));
   }

   public static long triggerJitterMs() {
      double v = 50.0 + RNG.nextGaussian() * 30.0;
      return (long)Math.max(0.0, Math.min(150.0, v));
   }

   private static double randomInterval(long mean, RiptideHumanRng.ClickState state) {
      updateTempoDrift(state, 0.055, 0.11, -0.14, 0.16);
      double micro = clamp(0.11 * RNG.nextGaussian(), -0.25, 0.28);
      double raw = mean * (1.0 + state.tempoDrift + micro);
      return balanceProfileMean(mean, state, raw, 0.6, 1.5, 0.28);
   }

   private static double randomPlusInterval(long mean, RiptideHumanRng.ClickState state) {
      updateTempoDrift(state, 0.035, 0.13, -0.18, 0.2);
      if (state.hesitationCooldownClicks > 0) {
         state.hesitationCooldownClicks--;
      }

      double micro = clamp(0.12 * RNG.nextGaussian(), -0.3, 0.32);
      double raw = mean * (1.0 + state.tempoDrift + micro);
      if (state.hesitationCooldownClicks <= 0 && RNG.nextDouble() < hesitationChance(mean)) {
         double extra = mean * (0.75 + RNG.nextDouble() * 1.15) + (2.5E7 + RNG.nextDouble() * 8.5E7);
         raw += extra;
         double cps = 1.0E9 / mean;
         double cooldownSeconds = 4.0 + RNG.nextDouble() * 8.0;
         state.hesitationCooldownClicks = Math.max(8, (int)Math.round(cooldownSeconds * cps));
      } else if (state.profileDebtNanos > mean * 0.75 && RNG.nextDouble() < 0.28) {
         raw *= 0.72 + RNG.nextDouble() * 0.16;
      } else if (RNG.nextDouble() < 0.035) {
         raw *= 0.78 + RNG.nextDouble() * 0.18;
      }

      return balanceProfileMean(mean, state, raw, 0.45, 3.0, 0.34);
   }

   private static double hesitationChance(long mean) {
      double secondsPerClick = mean / 1.0E9;
      return clamp(secondsPerClick / 9.0, 0.0045, 0.024);
   }

   private static void updateTempoDrift(RiptideHumanRng.ClickState state, double rate, double noise, double min, double max) {
      double target = RNG.nextGaussian() * noise;
      state.tempoDrift = state.tempoDrift + (target - state.tempoDrift) * rate;
      state.tempoDrift = clamp(state.tempoDrift, min, max);
   }

   private static double balanceProfileMean(
      long mean, RiptideHumanRng.ClickState state, double raw, double minFactor, double maxFactor, double maxCorrectionFactor
   ) {
      double min = mean * minFactor;
      double max = mean * maxFactor;
      double maxCorrection = mean * maxCorrectionFactor;
      double correction = clamp(state.profileDebtNanos, -maxCorrection, maxCorrection);
      double balanced = clamp(raw - correction, min, max);
      state.profileDebtNanos = Math.round(clamp(state.profileDebtNanos + balanced - mean, -mean * 8.0, mean * 8.0));
      return balanced;
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   public static final class ClickState {
      private double tempoDrift;
      private long profileDebtNanos;
      private int hesitationCooldownClicks;
      private long lastRawIntervalNanos;

      public void reset() {
         this.tempoDrift = 0.0;
         this.profileDebtNanos = 0L;
         this.hesitationCooldownClicks = 0;
         this.lastRawIntervalNanos = 0L;
      }

      public long lastRawIntervalNanos() {
         return this.lastRawIntervalNanos;
      }
   }

   public static enum Profile {
      NONE,
      RANDOM,
      RANDOM_PLUS;
   }
}
