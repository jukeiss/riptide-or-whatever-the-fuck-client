package riptide.util;

public final class RawMouseAccumulator {
   private double queuedX;
   private double queuedY;
   private double residualX;
   private double residualY;

   public void queue(double x, double y) {
      if (Double.isFinite(x)) {
         this.queuedX += x;
      }

      if (Double.isFinite(y)) {
         this.queuedY += y;
      }
   }

   public void replaceQueued(double x, double y) {
      this.queuedX = Double.isFinite(x) ? x : 0.0;
      this.queuedY = Double.isFinite(y) ? y : 0.0;
   }

   public RawMouseAccumulator.Counts consume() {
      double totalX = this.queuedX + this.residualX;
      double totalY = this.queuedY + this.residualY;
      long wholeX = wholeCounts(totalX);
      long wholeY = wholeCounts(totalY);
      this.residualX = totalX - wholeX;
      this.residualY = totalY - wholeY;
      this.queuedX = 0.0;
      this.queuedY = 0.0;
      return new RawMouseAccumulator.Counts(wholeX, wholeY);
   }

   public void clear() {
      this.queuedX = 0.0;
      this.queuedY = 0.0;
      this.residualX = 0.0;
      this.residualY = 0.0;
   }

   private static long wholeCounts(double value) {
      if (Double.isFinite(value) && !(Math.abs(value) < 1.0)) {
         return value > 0.0 ? (long)Math.floor(value) : (long)Math.ceil(value);
      } else {
         return 0L;
      }
   }

   public record Counts(long x, long y) {
   }
}
