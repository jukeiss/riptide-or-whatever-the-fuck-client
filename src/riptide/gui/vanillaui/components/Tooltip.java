package riptide.gui.vanillaui.components;

import java.util.List;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiRenderer;

public final class Tooltip {
   private static final int SCREEN_MARGIN = 4;
   private static final int PADDING = 4;
   private static final int ANCHOR_GAP = 8;
   private static final int LINE_HEIGHT = 10;

   private Tooltip() {
   }

   public static void render(UiContext context, String text, int mouseX, int mouseY) {
      render(context, text, mouseX, mouseY, 220);
   }

   public static void render(UiContext context, String text, int mouseX, int mouseY, int preferredMaxWidth) {
      if (text != null && !text.isBlank()) {
         int availableW = Math.max(1, context.screenWidth() - 8);
         int availableH = Math.max(1, context.screenHeight() - 8);
         int maxTextWidth = Math.max(1, Math.min(Math.max(32, preferredMaxWidth), Math.max(1, availableW - 8)));
         List<String> lines = context.text().wrapFully(text, maxTextWidth);
         int maxLineWidth = 0;

         for (String line : lines) {
            maxLineWidth = Math.max(maxLineWidth, context.text().width(line));
         }

         int w = Math.min(availableW, Math.max(1, maxLineWidth + 8));
         int maxLines = Math.max(1, Math.min(lines.size(), Math.max(1, (availableH - 8) / 10)));
         int h = Math.min(availableH, Math.max(1, maxLines * 10 + 8));
         int x = mouseX + 8;
         int y = mouseY + 8;
         if (x + w > context.screenWidth() - 4) {
            x = mouseX - w - 8;
         }

         if (y + h > context.screenHeight() - 4) {
            y = mouseY - h - 8;
         }

         UiBounds bounds = UiBounds.of(clamp(x, 4, Math.max(4, context.screenWidth() - 4 - w)), clamp(y, 4, Math.max(4, context.screenHeight() - 4 - h)), w, h);
         UiRenderer.rect(context.graphics(), bounds, context.theme().colors().windowStrong);
         UiRenderer.rect(context.graphics(), UiBounds.of(bounds.x(), bounds.y(), 2, bounds.height()), context.theme().colors().accent);

         for (int i = 0; i < maxLines; i++) {
            context.text().draw(context.graphics(), lines.get(i), bounds.x() + 4 + 2, bounds.y() + 4 + i * 10, context.theme().colors().text);
         }
      }
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(value, max));
   }
}
