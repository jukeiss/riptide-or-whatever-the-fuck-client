package riptide.gui.vanillaui.components;

import java.util.List;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiColors;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiTextRenderer;

public final class Banner {
   private static final int PAD_X = 6;
   private static final int BODY_GAP = 4;
   private static final int BOTTOM_PAD = 6;

   private Banner() {
   }

   public static int height(UiContext context, boolean hasDetail) {
      int headerHeight = 15;
      int lineHeight = 9;
      return headerHeight + 4 + lineHeight + (hasDetail ? 4 + lineHeight : 0) + 6;
   }

   public static int height(UiContext context, int width, String message, String detail) {
      int maxTextWidth = Math.max(1, width - 12);
      int lineHeight = 9;
      List<String> messageLines = context.text().wrapFully(message, maxTextWidth);
      List<String> detailLines = detail != null && !detail.isEmpty() ? context.text().wrapFully(detail, maxTextWidth) : List.of();
      return 15 + 4 + Math.max(1, messageLines.size()) * lineHeight + (detailLines.isEmpty() ? 0 : 4 + detailLines.size() * lineHeight) + 6;
   }

   public static void render(UiContext context, UiBounds requestedBounds, String title, String message, String detail) {
      UiColors colors = context.theme().colors();
      UiTextRenderer text = context.text();
      boolean hasDetail = detail != null && !detail.isEmpty();
      boolean dockedToTop = requestedBounds.y() <= 0;
      int margin = 4;
      int width = Math.max(1, Math.min(requestedBounds.width(), Math.max(1, context.screenWidth() - margin * 2)));
      int height = height(context, width, message, detail);
      int x = Math.max(margin, Math.min(requestedBounds.x(), Math.max(margin, context.screenWidth() - margin - width)));
      int y = dockedToTop ? 0 : Math.max(margin, Math.min(requestedBounds.y(), Math.max(margin, context.screenHeight() - margin - height)));
      UiBounds bounds = UiBounds.of(x, y, width, height);
      int headerHeight = 15;
      UiRenderer.rect(context.graphics(), bounds, colors.window);
      UiRenderer.rect(context.graphics(), UiBounds.of(x + 1, y + 1, Math.max(0, width - 2), Math.max(0, headerHeight - 2)), colors.header);
      if (!dockedToTop) {
         UiRenderer.horizontalEdge(context.graphics(), x, y, width, colors.accent);
      }

      UiRenderer.horizontalEdge(context.graphics(), x, y + height - 1, width, colors.borderSoft);
      UiRenderer.verticalEdge(context.graphics(), x, y + (dockedToTop ? 1 : 0), height - (dockedToTop ? 1 : 0), colors.borderSoft);
      UiRenderer.verticalEdge(context.graphics(), x + width - 1, y + (dockedToTop ? 1 : 0), height - (dockedToTop ? 1 : 0), colors.borderSoft);
      int maxTextWidth = Math.max(0, width - 12);
      int titleY = text.centeredY(UiBounds.of(x, y, width, headerHeight));
      int messageY = y + headerHeight + 4;
      text.drawFitted(context.graphics(), title, x + 6, titleY, maxTextWidth, colors.text);
      List<String> messageLines = text.wrapFully(message, maxTextWidth);

      for (int i = 0; i < messageLines.size(); i++) {
         text.draw(context.graphics(), messageLines.get(i), x + 6, messageY + i * 9, colors.text);
      }

      if (hasDetail) {
         int detailY = messageY + messageLines.size() * 9 + 4;
         List<String> detailLines = text.wrapFully(detail, maxTextWidth);

         for (int i = 0; i < detailLines.size(); i++) {
            text.draw(context.graphics(), detailLines.get(i), x + 6, detailY + i * 9, colors.muted);
         }
      }
   }
}
