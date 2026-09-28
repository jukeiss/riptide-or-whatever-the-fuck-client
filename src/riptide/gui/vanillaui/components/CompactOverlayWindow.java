package riptide.gui.vanillaui.components;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.util.RiptideUiScale;

public final class CompactOverlayWindow {
   private static final int SAFE_MARGIN = 4;

   private CompactOverlayWindow() {
   }

   public static UiBounds clamp(UiBounds wanted, int minWidth, int minHeight) {
      int screenWidth = Math.max(1, RiptideUiScale.getVirtualScreenWidth());
      int screenHeight = Math.max(1, RiptideUiScale.getVirtualScreenHeight());
      int availableWidth = Math.max(1, screenWidth - 8);
      int availableHeight = Math.max(1, screenHeight - 8);
      int width = Math.max(Math.min(minWidth, availableWidth), Math.min(wanted.width(), availableWidth));
      int height = Math.max(Math.min(minHeight, availableHeight), Math.min(wanted.height(), availableHeight));
      int x = Math.max(4, Math.min(wanted.x(), Math.max(4, screenWidth - 4 - width)));
      int y = Math.max(4, Math.min(wanted.y(), Math.max(4, screenHeight - 4 - height)));
      return UiBounds.of(x, y, width, height);
   }

   public static void render(UiContext context, UiBounds bounds, int headerHeight, String title, boolean collapsed, boolean active, boolean headerHovered) {
      CompactWindow.renderFrame(context, bounds, title, collapsed, true, true, headerHovered, active, 4, 4, headerHeight);
   }

   public static boolean beginBodyClip(GuiGraphicsExtractor graphics, UiBounds bounds, int headerHeight, boolean collapsed) {
      if (!collapsed && bounds.height() > headerHeight + 1) {
         UiScissorStack.global()
            .push(
               graphics,
               UiBounds.of(bounds.x() + 1, bounds.y() + headerHeight, Math.max(0, bounds.width() - 2), Math.max(0, bounds.height() - headerHeight - 1))
            );
         return true;
      } else {
         return false;
      }
   }

   public static void endBodyClip(GuiGraphicsExtractor graphics, boolean clipped) {
      if (clipped) {
         UiScissorStack.global().pop(graphics);
      }
   }
}
