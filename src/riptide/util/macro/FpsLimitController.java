package riptide.util.macro;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public final class FpsLimitController {
   private static final long LEGACY_OWNER = 0L;
   private static final long MAX_INDEFINITE_FREEZE_NANOS = 600000000000L;
   private static final ConcurrentHashMap<Long, FpsLimitController.Override> OVERRIDES = new ConcurrentHashMap<>();
   private static volatile int publishedLimit = -1;
   private static volatile long publishedNextExpiry = Long.MAX_VALUE;

   private FpsLimitController() {
   }

   public static void apply(int fpsLimit, long durationNanos) {
      apply(0L, fpsLimit, durationNanos);
   }

   public static synchronized void apply(long ownerRunId, int fpsLimit, long durationNanos) {
      if (durationNanos <= 0L) {
         clear(ownerRunId);
      } else {
         OVERRIDES.put(ownerRunId, new FpsLimitController.Override(Math.max(0, fpsLimit), System.nanoTime() + durationNanos, false));
         republish(System.nanoTime());
      }
   }

   public static void applyUntilCleared(int fpsLimit) {
      applyUntilCleared(0L, fpsLimit);
   }

   public static synchronized void applyUntilCleared(long ownerRunId, int fpsLimit) {
      int limit = Math.max(0, fpsLimit);
      long expiry = limit == 0 ? System.nanoTime() + 600000000000L : 0L;
      OVERRIDES.put(ownerRunId, new FpsLimitController.Override(limit, expiry, true));
      republish(System.nanoTime());
   }

   public static void clear() {
      clear(0L);
   }

   public static synchronized void clear(long ownerRunId) {
      OVERRIDES.remove(ownerRunId);
      republish(System.nanoTime());
   }

   public static synchronized void clearAll() {
      OVERRIDES.clear();
      publishedLimit = -1;
      publishedNextExpiry = Long.MAX_VALUE;
   }

   public static boolean isActive() {
      return activeLimit() >= 0;
   }

   public static int limit() {
      int active = activeLimit();
      return Math.max(0, active);
   }

   public static int activeLimit() {
      int limit = publishedLimit;
      if (limit < 0) {
         return -1;
      } else {
         long expiry = publishedNextExpiry;
         return expiry != Long.MAX_VALUE && System.nanoTime() - expiry >= 0L ? refreshExpired() : limit;
      }
   }

   public static boolean shouldFreeze() {
      return activeLimit() == 0;
   }

   public static long remainingMillis() {
      if (activeLimit() < 0) {
         return 0L;
      } else {
         long now = System.nanoTime();
         long longest = 0L;

         for (FpsLimitController.Override override : OVERRIDES.values()) {
            if (override.indefinite && override.limit != 0) {
               return Long.MAX_VALUE;
            }

            longest = Math.max(longest, override.expiryNanos - now);
         }

         return longest > 0L ? longest / 1000000L : 0L;
      }
   }

   private static synchronized int refreshExpired() {
      long now = System.nanoTime();
      if (publishedLimit >= 0 && publishedNextExpiry != Long.MAX_VALUE && now - publishedNextExpiry >= 0L) {
         republish(now);
         return publishedLimit;
      } else {
         return publishedLimit;
      }
   }

   private static void republish(long now) {
      int effective = Integer.MAX_VALUE;
      long nextExpiry = Long.MAX_VALUE;

      for (Entry<Long, FpsLimitController.Override> entry : OVERRIDES.entrySet()) {
         FpsLimitController.Override override = entry.getValue();
         if (!override.active(now)) {
            OVERRIDES.remove(entry.getKey(), override);
         } else {
            effective = Math.min(effective, override.limit);
            if (!override.indefinite || override.limit == 0) {
               nextExpiry = Math.min(nextExpiry, override.expiryNanos);
            }
         }
      }

      publishedLimit = effective == Integer.MAX_VALUE ? -1 : effective;
      publishedNextExpiry = publishedLimit < 0 ? Long.MAX_VALUE : nextExpiry;
   }

   private record Override(int limit, long expiryNanos, boolean indefinite) {
      boolean active(long now) {
         return this.indefinite && this.limit != 0 ? true : now - this.expiryNanos < 0L;
      }
   }
}
