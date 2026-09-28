package riptide.util;

public final class RiptideEssentialBridge {
   private RiptideEssentialBridge() {
   }

   public static void disable(RiptideConfig config) {
   }

   public static void restoreIfOrphaned(RiptideConfig config) {
      if (config != null && config.essentialHiddenByPanic) {
         config.essentialHiddenByPanic = false;
         config.save();
      }
   }

   public static void restore(RiptideConfig config) {
      if (config != null && config.essentialHiddenByPanic) {
         config.essentialHiddenByPanic = false;
      }
   }
}
