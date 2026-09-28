package riptide.modules;

import net.minecraft.world.item.ItemStack;
import riptide.util.RiptideAdminToolsOverlay;
import riptide.util.RiptideOverlayManager;

public final class RiptideAdminToolsBridge {
   private RiptideAdminToolsBridge() {
   }

   public static boolean fillNbtEditorSilently(ItemStack stack) {
      return ModuleRegistry.get("admin-tools") instanceof BuiltinModules.AdminToolsModule adminTools ? adminTools.fillItemEditorFromStack(stack, false) : false;
   }

   public static boolean openFilledAdminEditor(ItemStack stack) {
      if (!fillNbtEditorSilently(stack)) {
         return false;
      } else {
         try {
            RiptideAdminToolsOverlay overlay = RiptideAdminToolsOverlay.getSharedOverlay();
            RiptideOverlayManager manager = RiptideOverlayManager.get();
            manager.register(overlay);
            overlay.setVisible(true);
            overlay.showRawItemEditor();
            manager.bringToFront(overlay);
         } catch (Throwable var3) {
         }

         return true;
      }
   }
}
