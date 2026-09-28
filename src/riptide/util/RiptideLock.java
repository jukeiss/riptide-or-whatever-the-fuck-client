package riptide.util;

import net.minecraft.client.Minecraft;

public final class RiptideLock {
   private static String ALLOWED = "__RIPTIDE_LICENSE__";

   private RiptideLock() {
   }

   public static boolean allowed() {
      try {
         String var0 = ALLOWED;
         if (var0 != null && !var0.isEmpty() && var0.charAt(0) != '_') {
            Minecraft var1 = Minecraft.getInstance();
            if (var1 != null && var1.player != null) {
               String var2 = var1.player.getStringUUID().replace("-", "").toLowerCase();
               return var0.replace("-", "").toLowerCase().contains(var2);
            } else {
               return true;
            }
         } else {
            return true;
         }
      } catch (Throwable var3) {
         return true;
      }
   }
}
