package riptide.util;

public final class SmoothedMouseAccumulator {
   private static final double EPSILON = 1.0E-9;
   private double remainingX;
   private double remainingY;
   private double carryX;
   private double carryY;
   private double remainingSeconds;
   private long previousX;
   private long previousY;
   private boolean varyX;
   private boolean varyY = true;

   public void replace(double x, double y, double durationSeconds) {
      if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(durationSeconds)) {
         this.carryX = compatibleCarry(this.carryX, this.remainingX, x);
         this.carryY = compatibleCarry(this.carryY, this.remainingY, y);
         this.remainingX = x;
         this.remainingY = y;
         this.remainingSeconds = Math.max(0.001, durationSeconds);
      }
   }

   public RawMouseAccumulator.Counts consume(double elapsedSeconds) {
      if (Double.isFinite(elapsedSeconds) && !(elapsedSeconds <= 0.0) && !(this.remainingSeconds <= 0.0)) {
         double fraction = elapsedSeconds + 1.0E-9 >= this.remainingSeconds ? 1.0 : Math.min(1.0, elapsedSeconds / this.remainingSeconds);
         double sliceX = this.remainingX * fraction;
         double sliceY = this.remainingY * fraction;
         this.remainingX -= sliceX;
         this.remainingY -= sliceY;
         this.remainingSeconds = Math.max(0.0, this.remainingSeconds - elapsedSeconds);
         if (this.remainingSeconds == 0.0) {
            this.remainingX = 0.0;
            this.remainingY = 0.0;
         }

         double totalX = this.carryX + sliceX;
         double totalY = this.carryY + sliceY;
         long wholeX = wholeCounts(totalX);
         long wholeY = wholeCounts(totalY);
         if (this.remainingSeconds > 1.0E-9) {
            wholeX = varyRepeated(wholeX, this.previousX, this.remainingX, this.varyX);
            wholeY = varyRepeated(wholeY, this.previousY, this.remainingY, this.varyY);
            if (wholeX == this.previousX && wholeX != 0L) {
               this.varyX = !this.varyX;
            }

            if (wholeY == this.previousY && wholeY != 0L) {
               this.varyY = !this.varyY;
            }
         }

         this.carryX = totalX - wholeX;
         this.carryY = totalY - wholeY;
         this.previousX = wholeX;
         this.previousY = wholeY;
         return new RawMouseAccumulator.Counts(wholeX, wholeY);
      } else {
         return new RawMouseAccumulator.Counts(0L, 0L);
      }
   }

   public boolean hasPending() {
      return this.remainingSeconds > 0.0;
   }

   public void clear() {
      this.remainingX = 0.0;
      this.remainingY = 0.0;
      this.carryX = 0.0;
      this.carryY = 0.0;
      this.remainingSeconds = 0.0;
      this.previousX = 0L;
      this.previousY = 0L;
      this.varyX = false;
      this.varyY = true;
   }

   private static double compatibleCarry(double carry, double previous, double next) {
      if (Math.abs(next) < 1.0E-9) {
         return 0.0;
      } else {
         double direction = Math.abs(previous) >= 1.0E-9 ? previous : carry;
         return Math.abs(direction) >= 1.0E-9 && Math.signum(direction) != Math.signum(next) ? 0.0 : carry;
      }
   }

   private static long wholeCounts(double value) {
      if (Double.isFinite(value) && !(Math.abs(value) < 1.0)) {
         double nearest = Math.rint(value);
         if (Math.abs(value - nearest) < 1.0E-7) {
            value = nearest;
         }

         return value > 0.0 ? (long)Math.floor(value) : (long)Math.ceil(value);
      } else {
         return 0L;
      }
   }

   private static long varyRepeated(long value, long previous, double future, boolean increase) {
      if (value != 0L && value == previous && !(Math.abs(future) < 1.0)) {
         long direction = Long.signum(value);
         long varied = value + (increase ? direction : -direction);
         return Long.signum(varied) == direction ? varied : value;
      } else {
         return value;
      }
   }
}
