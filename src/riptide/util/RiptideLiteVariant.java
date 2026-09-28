package riptide.util;

import java.io.InputStream;

public final class RiptideLiteVariant {
   private static final boolean ENABLED = compute();

   private RiptideLiteVariant() {
   }

   private static boolean compute() {
      try {
         boolean var1;
         try (InputStream in = RiptideLiteVariant.class.getResourceAsStream("/riptide-lite.marker")) {
            var1 = in != null;
         }

         return var1;
      } catch (Throwable var5) {
         return false;
      }
   }

   public static boolean enabled() {
      return ENABLED;
   }
}
