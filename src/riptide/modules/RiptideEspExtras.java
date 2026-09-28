package riptide.modules;

import riptide.api.module.IntSetting;

public final class RiptideEspExtras {
   public static final String RANGE_ID = "max-distance";

   private RiptideEspExtras() {
   }

   public static void addRange(Module var0) {
      try {
         var0.add(
            new IntSetting("max-distance", "Max Distance", 128, 16, 512, 8)
               .group("General")
               .description("Furthest container drawn, in blocks. Lower = less clutter when base finding.")
               .build()
         );
      } catch (Throwable var2) {
      }
   }

   public static double rangeSq(Module var0, double var1) {
      try {
         String var3 = var0.value("max-distance");
         if (var3 != null && !var3.isEmpty()) {
            int var4 = (int)Double.parseDouble(var3.trim());
            if (var4 >= 16 && var4 <= 512) {
               double var5 = (double)var4 * var4;
               return Math.min(var5, var1);
            }
         }
      } catch (Throwable var7) {
      }

      return var1;
   }
}
