package riptide.util;

import riptide.modules.PackHideState;

public final class RiptideMenuPrefs {
   private RiptideMenuPrefs() {
   }

   public static boolean customMainMenuEnabled() {
      if (RiptideLiteVariant.enabled()) {
         return false;
      } else {
         RiptideConfig config = RiptideConfig.getGlobal();
         return config == null || config.customMainMenu;
      }
   }

   public static boolean vanillaMenuVisuals() {
      return PackHideState.isActive() || !customMainMenuEnabled();
   }
}
