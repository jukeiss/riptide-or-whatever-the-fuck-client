package riptide.modules;

import riptide.util.RiptideConfig;

public final class RiptideHideReset {
   private RiptideHideReset() {
   }

   public static void onStartup() {
      RiptideConfig var0 = RiptideConfig.getGlobal();
      RiptideConfig.ModuleState var1 = var0.modules == null ? null : var0.modules.get("hide");
      if (var1 != null && var1.enabled) {
         var1.enabled = false;
         PackHideState.publishRuntimeState(var0);
         PackHideState.disableAndRestore(ModuleRegistry.get("hide"));
         PackHideState.refresh();
      } else {
         PackHideState.refresh();
      }
   }
}
