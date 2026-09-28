package riptide.gui.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.components.SectionPanel;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideUiScale;

public abstract class RiptideScreen extends Screen {
   protected RiptideScreen(Component title) {
      super(title);
   }

   protected static String safeTrim(String value) {
      return value == null ? "" : value.trim();
   }

   protected static MouseButtonEvent virtualEvent(MouseButtonEvent event) {
      return new MouseButtonEvent(
         RiptideUiScale.toVirtual(event.x()), RiptideUiScale.toVirtual(event.y()), new MouseButtonInfo(event.button(), event.modifiers())
      );
   }

   protected int screenWidth() {
      int width = RiptideUiScale.getVirtualScreenWidth();
      return width <= 0 ? this.width : width;
   }

   protected int screenHeight() {
      int height = RiptideUiScale.getVirtualScreenHeight();
      return height <= 0 ? this.height : height;
   }

   protected void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill) {
      SectionPanel.renderBody(UiContexts.overlay(graphics, this.font, -10000, -10000), UiBounds.of(x, y, w, h), fill);
   }

   protected void toast(String message, int accentColor) {
      if (message != null && !message.isBlank() && this.minecraft != null) {
         this.minecraft.execute(() -> RiptideNotifications.show(message, accentColor));
      }
   }
}
