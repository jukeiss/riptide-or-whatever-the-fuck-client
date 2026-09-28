package riptide.security;

public final class RiptideProtectorServerPackFailureGuard {
   private static volatile long suppressServerPacksUntilMs;

   private RiptideProtectorServerPackFailureGuard() {
   }

   public static void suppressServerPacksTemporarily() {
      suppressServerPacksUntilMs = Math.max(suppressServerPacksUntilMs, System.currentTimeMillis() + 15000L);
   }

   public static boolean shouldSuppressServerPacks() {
      return System.currentTimeMillis() < suppressServerPacksUntilMs;
   }

   public static void clear() {
      suppressServerPacksUntilMs = 0L;
   }
}
