package riptide.util;

import java.util.Random;
import java.util.function.LongSupplier;

public final class RiptideClickPacer {
   private static final double UNARMED_CPS = 12.0;
   private static final long PHASE_MIN_NANOS = 2500000000L;
   private static final long PHASE_SPAN_NANOS = 3000000000L;
   private static final double MAX_TICK_CPS = 20.0;
   private static final double TICK_SECONDS = 0.05;
   private static final int MIN_HESITATION_TICKS = 1;
   private static final int HESITATION_TICK_SPAN = 3;
   private static final double MEAN_HESITATION_TICKS = 2.0;
   private static final double MAX_BANKED_CLICKS = 2.0;
   private static final int WINDOW_GAPS = 49;
   private static final long WINDOW_NANOS = 4000000000L;
   private final Random rng;
   private final LongSupplier clock;
   private double budget;
   private double scheduledCps;
   private long lastNanos;
   private long phaseEndNanos;
   private double phaseCps;
   private double phaseRoughness;
   private double phaseHesitationChance;
   private int hesitationTicks;
   private final long[] recentClicks = new long[49];
   private int recentIndex;
   private int recentCount;

   public RiptideClickPacer() {
      this(new Random(), System::nanoTime);
   }

   RiptideClickPacer(Random rng, LongSupplier clock) {
      this.rng = rng;
      this.clock = clock;
   }

   public void reset() {
      this.budget = 0.0;
      this.scheduledCps = 0.0;
      this.lastNanos = 0L;
      this.phaseEndNanos = 0L;
      this.phaseCps = 0.0;
      this.phaseRoughness = 0.0;
      this.phaseHesitationChance = 0.0;
      this.hesitationTicks = 0;
   }

   public boolean shouldClick(double minCps, double maxCps) {
      double high = Math.max(0.5, Math.max(minCps, maxCps));
      double low = Math.max(0.5, Math.min(minCps, maxCps));
      long now = this.clock.getAsLong();
      long elapsed;
      if (this.lastNanos == 0L) {
         this.hesitationTicks = 0;
         this.rollPhase(now, low, high);
         this.scheduledCps = this.rollClickCps();
         this.budget = 1.0;
         elapsed = 0L;
      } else {
         elapsed = Math.max(0L, Math.min(200000000L, now - this.lastNanos));
      }

      this.lastNanos = now;
      if (now >= this.phaseEndNanos) {
         this.rollPhase(now, low, high);
      }

      if (this.hesitationTicks > 0) {
         this.hesitationTicks--;
         return false;
      } else {
         this.budget = Math.min(2.0, this.budget + elapsed * this.scheduledCps / 1.0E9);
         if (this.budget < 1.0) {
            return false;
         } else if (high <= 12.0 && this.wouldFillWindow(now)) {
            return false;
         } else {
            this.recordClick(now);
            this.budget--;
            this.scheduledCps = this.rollClickCps();
            if (this.phaseHesitationChance > 0.0 && this.rng.nextDouble() < this.phaseHesitationChance) {
               this.hesitationTicks = 1 + this.rng.nextInt(3);
            }

            return true;
         }
      }
   }

   private void rollPhase(long now, double low, double high) {
      this.phaseEndNanos = now + 2500000000L + (long)(this.rng.nextDouble() * 3.0E9);
      boolean ragged = this.phaseRoughness < 0.3 ? this.rng.nextDouble() < 0.8 : this.rng.nextDouble() < 0.2;
      this.phaseRoughness = ragged ? 0.4 + this.rng.nextDouble() * 0.6 : this.rng.nextDouble() * 0.15;
      double placeInBand = 0.05 + 0.9 * this.phaseRoughness + this.rng.nextGaussian() * 0.18;
      double target = low + (high - low) * Math.max(0.0, Math.min(1.0, placeInBand));
      double wanted = ragged ? 0.02 + this.rng.nextDouble() * 0.12 : 0.0;
      double affordablePause = 1.0 / target - 0.05;
      double affordableChance = affordablePause / 0.1;
      this.phaseHesitationChance = Math.max(0.0, Math.min(wanted, affordableChance));
      double spentOnPauses = this.phaseHesitationChance * 2.0 * 0.05;
      double perClick = 1.0 / target - spentOnPauses;
      this.phaseCps = perClick > 0.0 ? Math.min(20.0, 1.0 / perClick) : 20.0;
   }

   private boolean wouldFillWindow(long now) {
      return this.recentCount < 49 ? false : now - this.recentClicks[this.recentIndex] < 4000000000L;
   }

   private void recordClick(long now) {
      this.recentClicks[this.recentIndex] = now;
      this.recentIndex = (this.recentIndex + 1) % 49;
      if (this.recentCount < 49) {
         this.recentCount++;
      }
   }

   private double rollClickCps() {
      double room = Math.min(0.3, Math.max(0.0, 1.0 - this.phaseCps / 20.0));
      double wobble = this.rng.nextGaussian() * (0.01 + 0.12 * this.phaseRoughness);
      double period = 1.0 / this.phaseCps * (1.0 + Math.max(-room, Math.min(room, wobble)));
      return period <= 0.0 ? Math.min(20.0, this.phaseCps) : Math.max(0.5, Math.min(20.0, 1.0 / period));
   }
}
