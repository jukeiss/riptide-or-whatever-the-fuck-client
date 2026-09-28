package riptide.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.components.ToastStack;
import riptide.modules.PackHideState;

public final class RiptideNotifications {
   private static final int SUCCESS = -13248397;
   private static final int WARNING = -14249;
   private static final int ERROR = -42149;
   private static final ToastStack TOASTS = new ToastStack(2100L, 140.0F, 220.0F, 4, 4, 18);

   private RiptideNotifications() {
   }

   public static void copied(String message) {
      show(message != null && !message.isBlank() ? message : "Copied.", -13248397);
   }

   public static void success(String message) {
      show(message, -13248397);
   }

   public static void warning(String message) {
      show(message, -14249);
   }

   public static void error(String message) {
      show(message, -42149);
   }

   public static void show(String message, int accentColor) {
      if (!PackHideState.shouldSuppressClientOutput()) {
         TOASTS.show(message, accentColor);
      }
   }

   public static void render(GuiGraphicsExtractor graphics) {
      if (PackHideState.shouldSuppressClientOutput()) {
         clear();
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (graphics != null && mc != null && mc.font != null) {
            RiptideNameScrape.render(graphics);
            if (TOASTS.hasVisibleToasts()) {
               int width = Math.min(320, Math.max(120, RiptideUiScale.getVirtualScreenWidth() - 16));
               int x = Math.max(8, (RiptideUiScale.getVirtualScreenWidth() - width) / 2);
               TOASTS.render(UiContexts.overlay(graphics, mc.font, -1, -1), x, 8, width);
            }
         }
      }
   }

   public static boolean hasVisible() {
      return PackHideState.shouldSuppressClientOutput() ? false : TOASTS.hasVisibleToasts() || RiptideNameScrape.isActive();
   }

   public static int pendingCount() {
      return TOASTS.size();
   }

   public static void clear() {
      TOASTS.clear();
   }
}
