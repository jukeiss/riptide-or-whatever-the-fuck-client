package riptide.gui.vanillaui.direct;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiSizing;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;

public final class DirectHudPanelRenderer {
   private static final CompactTheme THEME = new CompactTheme();

   private DirectHudPanelRenderer() {
   }

   public static int render(
      GuiGraphicsExtractor context, Font textRenderer, int x, int y, int width, String title, List<DirectHudPanelRenderer.Row> rows, int accentColor
   ) {
      String titleTrimmed = UiText.trimToWidth(textRenderer, title, width - 12, THEME.fontFor(UiTone.LABEL), THEME.color(UiTone.BODY));
      ArrayList<DirectHudPanelRenderer.Row> trimmedRows = new ArrayList<>(rows.size());

      for (DirectHudPanelRenderer.Row row : rows) {
         trimmedRows.add(
            new DirectHudPanelRenderer.Row(UiText.trimToWidth(textRenderer, row.text(), width - 12, row.font(), row.color()), row.font(), row.color())
         );
      }

      return renderPreTrimmed(context, textRenderer, x, y, width, titleTrimmed, trimmedRows, accentColor);
   }

   public static int panelHeight(int rowCount) {
      int headerHeight = Math.max(THEME.headerHeight(), THEME.lineHeight(UiTone.LABEL, 2));
      int rowHeight = THEME.lineHeight(UiTone.BODY, 2);
      int contentHeight = Math.max(rowHeight, rowCount * rowHeight);
      return headerHeight + 6 + contentHeight + 6;
   }

   public static int renderPreTrimmed(
      GuiGraphicsExtractor context, Font textRenderer, int x, int y, int width, String title, List<DirectHudPanelRenderer.Row> rows, int accentColor
   ) {
      return renderPreTrimmed(context, textRenderer, x, y, width, title, rows, accentColor, 0, true, true);
   }

   public static int renderPreTrimmed(
      GuiGraphicsExtractor context,
      Font textRenderer,
      int x,
      int y,
      int width,
      String title,
      List<DirectHudPanelRenderer.Row> rows,
      int accentColor,
      int minHeight,
      boolean leftBorder,
      boolean rightBorder
   ) {
      return renderPreTrimmed(context, textRenderer, x, y, width, title, rows, accentColor, minHeight, leftBorder, rightBorder, true, true);
   }

   public static int renderPreTrimmed(
      GuiGraphicsExtractor context,
      Font textRenderer,
      int x,
      int y,
      int width,
      String title,
      List<DirectHudPanelRenderer.Row> rows,
      int accentColor,
      int minHeight,
      boolean leftBorder,
      boolean rightBorder,
      boolean topBorder,
      boolean bottomBorder
   ) {
      int headerHeight = Math.max(THEME.headerHeight(), THEME.lineHeight(UiTone.LABEL, 2));
      int rowHeight = THEME.lineHeight(UiTone.BODY, 2);
      int bodyPadding = 6;
      int contentHeight = Math.max(rowHeight, rows.size() * rowHeight);
      int height = Math.max(minHeight, headerHeight + bodyPadding + contentHeight + 6);
      UiRenderer.rect(context, UiBounds.of(x, y, width, height), THEME.windowFill());
      UiRenderer.rect(context, UiBounds.of(x + 1, y + 1, width - 2, headerHeight - 1), THEME.headerFill());
      if (topBorder) {
         UiRenderer.horizontalEdge(context, x, y, width, accentColor);
      }

      if (bottomBorder) {
         UiRenderer.horizontalEdge(context, x, y + height - 1, width, THEME.borderColor());
      }

      if (leftBorder) {
         UiRenderer.verticalEdge(context, x, y, height, THEME.borderColor());
      }

      if (rightBorder) {
         UiRenderer.verticalEdge(context, x + width - 1, y, height, THEME.borderColor());
      }

      UiRenderer.horizontalEdge(context, x + 1, y + 1, width - 2, 620756991);
      int padX = 6;
      int titleY = UiSizing.alignTextY(y, headerHeight, THEME.fontHeight(UiTone.LABEL), THEME.bodyTextNudge());
      UiText.draw(context, textRenderer, title, THEME.fontFor(UiTone.LABEL), THEME.color(UiTone.BODY), x + padX, titleY, false);
      int rowY = y + headerHeight + 4;

      for (DirectHudPanelRenderer.Row row : rows) {
         UiText.draw(
            context,
            textRenderer,
            row.text(),
            row.font(),
            row.color(),
            x + padX,
            UiSizing.alignTextY(rowY, rowHeight, THEME.fontHeight(UiTone.BODY), THEME.bodyTextNudge()),
            false
         );
         rowY += rowHeight;
      }

      return height;
   }

   public record Row(String text, Identifier font, int color) {
      public static DirectHudPanelRenderer.Row body(String text, int color) {
         return new DirectHudPanelRenderer.Row(text, DirectHudPanelRenderer.THEME.fontFor(UiTone.BODY), color);
      }

      public static DirectHudPanelRenderer.Row muted(String text) {
         return new DirectHudPanelRenderer.Row(text, DirectHudPanelRenderer.THEME.fontFor(UiTone.MUTED), DirectHudPanelRenderer.THEME.color(UiTone.MUTED));
      }
   }
}
