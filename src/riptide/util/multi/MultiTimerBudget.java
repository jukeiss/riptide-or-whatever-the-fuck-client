package riptide.util.multi;

final class MultiTimerBudget {
   static final long TICK_NANOS = 50000000L;
   static final long DRIFT_NANOS = 120000000L;
   private long balanceNanos;

   MultiTimerBudget() {
      this.reset(System.nanoTime());
   }

   synchronized void reset(long nowNanos) {
      this.balanceNanos = nowNanos - 120000000L;
   }

   synchronized boolean reserve(long nowNanos) {
      if (this.balanceNanos < nowNanos - 120000000L) {
         this.balanceNanos = nowNanos - 120000000L;
      }

      if (this.balanceNanos > nowNanos) {
         return false;
      } else {
         this.balanceNanos += 50000000L;
         return true;
      }
   }

   synchronized int available(long nowNanos) {
      long floored = Math.max(this.balanceNanos, nowNanos - 120000000L);
      return floored > nowNanos ? 0 : (int)((nowNanos - floored) / 50000000L) + 1;
   }

   synchronized long drainMillis(long nowNanos, int packets) {
      if (packets <= 0) {
         return 0L;
      } else {
         long floored = Math.max(this.balanceNanos, nowNanos - 120000000L);
         long finishAt = floored + (packets - 1) * 50000000L;
         return Math.max(0L, (finishAt - nowNanos) / 1000000L);
      }
   }
}
