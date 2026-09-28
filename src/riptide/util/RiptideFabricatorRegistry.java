package riptide.util;

import org.jetbrains.annotations.Nullable;

public class RiptideFabricatorRegistry {
   private static volatile RiptideFabricatorOverlay activeOverlay = null;

   public static void setActiveOverlay(@Nullable RiptideFabricatorOverlay overlay) {
      activeOverlay = overlay;
   }

   @Nullable
   public static RiptideFabricatorOverlay getActiveOverlay() {
      return activeOverlay;
   }
}
