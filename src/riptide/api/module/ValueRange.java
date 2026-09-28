package riptide.api.module;

import java.util.Random;

public record ValueRange(double min, double max) {
   public ValueRange(double min, double max) {
      if (max < min) {
         double swap = min;
         min = max;
         max = swap;
      }

      this.min = min;
      this.max = max;
   }

   public ValueRange(int min, int max) {
      this((double)min, (double)max);
   }

   public ValueRange clamp(double lower, double upper) {
      double high = Math.max(lower, upper);
      return new ValueRange(Math.max(lower, Math.min(high, this.min)), Math.max(lower, Math.min(high, this.max)));
   }

   public ValueRange withMinSeparation(double separation, double lower, double upper, boolean anchorMin) {
      double high = Math.max(lower, upper);
      if (!(separation <= 0.0) && !(high - lower < separation)) {
         ValueRange bounded = this.clamp(lower, high);
         if (bounded.max - bounded.min >= separation) {
            return bounded;
         } else if (anchorMin) {
            double pushed = bounded.min + separation;
            return pushed <= high ? new ValueRange(bounded.min, pushed) : new ValueRange(high - separation, high);
         } else {
            double pushed = bounded.max - separation;
            return pushed >= lower ? new ValueRange(pushed, bounded.max) : new ValueRange(lower, lower + separation);
         }
      } else {
         return this.clamp(lower, high);
      }
   }

   public double random(Random random) {
      return random != null && !(this.max <= this.min) ? this.min + random.nextDouble() * (this.max - this.min) : this.min;
   }

   @Override
   public String toString() {
      return number(this.min) + "," + number(this.max);
   }

   public String format(double step) {
      boolean whole = step >= 1.0 && step == Math.rint(step);
      return whole ? Math.round(this.min) + " - " + Math.round(this.max) : number(this.min) + " - " + number(this.max);
   }

   private static String number(double value) {
      return value == Math.rint(value) && !Double.isInfinite(value) ? Long.toString((long)value) : String.valueOf(Math.round(value * 100.0) / 100.0);
   }

   public static ValueRange parse(String raw, ValueRange fallback) {
      if (raw == null) {
         return fallback;
      } else {
         int comma = raw.indexOf(44);
         if (comma < 0) {
            return fallback;
         } else {
            try {
               return new ValueRange(Double.parseDouble(raw.substring(0, comma).trim()), Double.parseDouble(raw.substring(comma + 1).trim()));
            } catch (RuntimeException var4) {
               return fallback;
            }
         }
      }
   }
}
