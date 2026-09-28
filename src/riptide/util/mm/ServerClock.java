package riptide.util.mm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class ServerClock {
   private static volatile ServerClock.Anchor anchor;

   private ServerClock() {
   }

   public static void adopt(String jwt) {
      long iatMs = claimMs(jwt, "iat");
      if (iatMs > 0L) {
         anchor = new ServerClock.Anchor(iatMs, System.nanoTime());
      }
   }

   public static void adoptServerTime(long serverMs) {
      if (serverMs > 0L) {
         anchor = new ServerClock.Anchor(serverMs, System.nanoTime());
      }
   }

   public static long nowMs() {
      ServerClock.Anchor a = anchor;
      return a == null ? System.currentTimeMillis() : a.serverMs + (System.nanoTime() - a.nanos) / 1000000L;
   }

   private static long claimMs(String jwt, String name) {
      if (jwt != null && !jwt.isEmpty()) {
         try {
            int d1 = jwt.indexOf(46);
            int d2 = jwt.indexOf(46, d1 + 1);
            if (d1 > 0 && d2 > d1) {
               byte[] payload = Base64.getUrlDecoder().decode(jwt.substring(d1 + 1, d2));
               JsonObject o = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
               return o.has(name) ? o.get(name).getAsLong() * 1000L : 0L;
            } else {
               return 0L;
            }
         } catch (Throwable var6) {
            return 0L;
         }
      } else {
         return 0L;
      }
   }

   private record Anchor(long serverMs, long nanos) {
   }
}
