package riptide.util;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RiptidePlayerStats {
   private static final long TTL_MS = 600000L;
   public static final String BALANCE = "balance";
   public static final String SHARDS = "shards";
   public static final String KILLS = "kills";
   public static final String DEATHS = "deaths";
   public static final String PLAYTIME = "playtime";
   private static final Map<String, RiptidePlayerStats.Entry> STATS = new ConcurrentHashMap<>();

   private RiptidePlayerStats() {
   }

   private static String key(String var0, String var1) {
      return var0.toLowerCase(Locale.ROOT) + "\u0000" + var1;
   }

   public static void put(String var0, String var1, String var2) {
      if (var0 != null && !var0.isEmpty() && var1 != null && var2 != null) {
         STATS.put(key(var0, var1), new RiptidePlayerStats.Entry(var2, System.currentTimeMillis()));
      }
   }

   public static String get(String var0, String var1) {
      if (var0 != null && var1 != null) {
         RiptidePlayerStats.Entry var2 = STATS.get(key(var0, var1));
         if (var2 == null) {
            return null;
         } else if (System.currentTimeMillis() - var2.at() > 600000L) {
            STATS.remove(key(var0, var1));
            return null;
         } else {
            return var2.value();
         }
      } else {
         return null;
      }
   }

   public static boolean has(String var0, String var1) {
      return get(var0, var1) != null;
   }

   public static void clear() {
      STATS.clear();
   }

   public static int size() {
      return STATS.size();
   }

   public static String shorten(double var0) {
      double var2 = Math.abs(var0);
      if (var2 >= 1.0E12) {
         return trim(var0 / 1.0E12) + "T";
      } else if (var2 >= 1.0E9) {
         return trim(var0 / 1.0E9) + "B";
      } else if (var2 >= 1000000.0) {
         return trim(var0 / 1000000.0) + "M";
      } else {
         return var2 >= 1000.0 ? trim(var0 / 1000.0) + "K" : trim(var0);
      }
   }

   private static String trim(double var0) {
      return var0 == Math.floor(var0) && !Double.isInfinite(var0) ? Long.toString((long)var0) : String.format(Locale.ROOT, "%.1f", var0);
   }

   private record Entry(String value, long at) {
   }
}
