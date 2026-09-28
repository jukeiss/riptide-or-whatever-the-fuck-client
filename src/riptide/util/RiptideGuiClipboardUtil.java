package riptide.util;

import net.minecraft.client.Minecraft;

public final class RiptideGuiClipboardUtil {
   private static final Minecraft MC = Minecraft.getInstance();

   private RiptideGuiClipboardUtil() {
   }

   public static void copyGuiTitleJson() {
      if (MC.gui.screen() != null && MC.keyboardHandler != null) {
         String title = MC.gui.screen().getTitle() == null ? "" : MC.gui.screen().getTitle().getString();
         MC.keyboardHandler.setClipboard(title);
         RiptideNotifications.copied("GUI title copied.");
      } else {
         RiptideNotifications.error("Copy failed: no screen.");
      }
   }
}
