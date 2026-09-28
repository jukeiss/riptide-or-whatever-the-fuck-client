package riptide.util;

public final class RiptideCpsTracker {
   private static final long WINDOW_NS = 1000000000L;
   private static final int CAP = 48;
   private static final long[] LEFT = new long[48];
   private static final long[] RIGHT = new long[48];
   private static int leftIdx;
   private static int rightIdx;

   private RiptideCpsTracker() {
   }

   public static void recordLeft() {
      LEFT[leftIdx] = System.nanoTime();
      leftIdx = (leftIdx + 1) % 48;
   }

   public static void recordRight() {
      RIGHT[rightIdx] = System.nanoTime();
      rightIdx = (rightIdx + 1) % 48;
   }

   public static int leftCps() {
      return count(LEFT);
   }

   public static int rightCps() {
      return count(RIGHT);
   }

   public static int totalCps() {
      return leftCps() + rightCps();
   }

   public static boolean leftActiveRecently(long ms) {
      return activeRecently(LEFT, leftIdx, ms);
   }

   public static boolean rightActiveRecently(long ms) {
      return activeRecently(RIGHT, rightIdx, ms);
   }

   private static boolean activeRecently(long[] ring, int idx, long ms) {
      long newest = ring[(idx + 48 - 1) % 48];
      return newest != 0L && newest > System.nanoTime() - ms * 1000000L;
   }

   private static int count(long[] ring) {
      long cutoff = System.nanoTime() - 1000000000L;
      int c = 0;

      for (int i = 0; i < 48; i++) {
         if (ring[i] > cutoff) {
            c++;
         }
      }

      return c;
   }
}
