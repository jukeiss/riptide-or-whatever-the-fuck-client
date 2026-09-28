package riptide.util;

import java.util.Locale;
import net.minecraft.util.Util;

public final class RiptideLinks {
   public static final String DISCORD = "";
   public static final String RIPTIDE_INC_DISCORD = "";
   public static final String WEBSITE = "                   ";

   private RiptideLinks() {
   }

   public static void open(String var0) {
      if (!isOpenableUrl(var0)) {
         riptide.RiptideClientAddon.LOG.warn("[Riptide] Refused to open non-http(s) URL: {}", var0);
      } else {
         try {
            Util.getPlatform().openUri(var0);
         } catch (Throwable var2) {
         }
      }
   }

   public static boolean isOpenableUrl(String var0) {
      if (var0 == null) {
         return false;
      } else {
         String var1 = var0.trim().toLowerCase(Locale.ROOT);
         return var1.startsWith("https://") || var1.startsWith("http://") || var1.startsWith("mailto:");
      }
   }
}
