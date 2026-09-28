package riptide.gui.vanillaui.components;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiIcons;

public final class CompactListRenderer {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int HEADER_ACTION_RIGHT_INSET = 2;

   private CompactListRenderer() {
   }

   public static void drawFrame(GuiGraphicsExtractor context, int x, int y, int width, int height, boolean focused) {
      int bg = focused ? THEME.listFillFocused() : THEME.listFill();
      int border = focused ? THEME.headerAccent() : THEME.borderColor();
      UiBounds bounds = UiBounds.of(x, y, width, height);
      UiRenderer.frame(context, bounds, bg, border);
      UiRenderer.horizontalEdge(context, x + 1, y + 1, Math.max(0, width - 2), 385875967);
   }

   public static void drawHeader(GuiGraphicsExtractor context, Font textRenderer, String title, int x, int y) {
      UiText.draw(context, textRenderer, title, THEME.fontFor(UiTone.LABEL), THEME.color(UiTone.MUTED), x, y, false);
   }

   public static void drawHeaderAction(
      GuiGraphicsExtractor context, Font textRenderer, String label, boolean enabled, int listX, int listWidth, int headerY, int mouseX, int mouseY
   ) {
      int buttonHeight = 13;
      int buttonWidth = Math.max(54, UiText.width(textRenderer, label, THEME.fontFor(UiTone.BODY), THEME.color(UiTone.BODY)) + 12);
      int buttonX = listX + listWidth - buttonWidth - 2;
      int buttonY = headerY + Math.max(0, (THEME.lineHeight(UiTone.LABEL, 1) - buttonHeight) / 2) - 1;
      CompactOverlayButton button = CompactOverlayButton.create(buttonX, buttonY, buttonWidth, buttonHeight, Component.literal(label), b -> {});
      button.active = enabled;
      button.setVariant(enabled ? CompactOverlayButton.Variant.SECONDARY : CompactOverlayButton.Variant.GHOST);
      CompactOverlayButton.renderStyled(context, textRenderer, button, mouseX, mouseY);
   }

   public static void drawEmptyState(GuiGraphicsExtractor context, Font textRenderer, String text, int x, int y, int width) {
      String trimmed = UiText.trimToWidth(textRenderer, text, Math.max(1, width - 10), THEME.fontFor(UiTone.MUTED), THEME.color(UiTone.MUTED));
      int textY = UiSizing.alignTextY(y, 14, THEME.fontHeight(UiTone.MUTED), THEME.bodyTextNudge());
      UiText.draw(context, textRenderer, trimmed, THEME.fontFor(UiTone.MUTED), THEME.color(UiTone.MUTED), x + 6, textY, false);
   }

   public static void drawRow(
      GuiGraphicsExtractor context,
      Font textRenderer,
      String label,
      int x,
      int y,
      int width,
      int height,
      boolean hovered,
      boolean selected,
      CompactListRenderer.RowTone tone
   ) {
      drawRow(context, textRenderer, Component.literal(label == null ? "" : label), x, y, width, height, hovered, selected, tone, false);
   }

   public static void drawRow(
      GuiGraphicsExtractor context,
      Font textRenderer,
      Component label,
      int x,
      int y,
      int width,
      int height,
      boolean hovered,
      boolean selected,
      CompactListRenderer.RowTone tone,
      boolean useMinecraftText
   ) {
      if (selected) {
         THEME.rowFillSelected();
      } else if (hovered) {
         THEME.rowFillHovered();
      } else {
         THEME.rowFillNormal();
      }
      int fg = switch (tone) {
         case NORMAL -> selected ? RiptideTheme.recolor(-2828, RiptideTheme.Channel.TEXT) : THEME.color(UiTone.BODY);
         case READY -> selected ? -1507344 : -6426438;
         case WARNING -> selected ? -3104 : -14726;
         case MISSING -> RiptideTheme.recolor(selected ? -4884 : -20561, RiptideTheme.Channel.DANGER);
         case DANGER -> RiptideTheme.recolor(selected ? -5398 : -29556, RiptideTheme.Channel.DANGER);
      };
      drawRowWithColor(context, textRenderer, label, x, y, width, height, hovered, selected, fg, useMinecraftText);
   }

   public static void drawRowWithColor(
      GuiGraphicsExtractor context,
      Font textRenderer,
      Component label,
      int x,
      int y,
      int width,
      int height,
      boolean hovered,
      boolean selected,
      int textColor,
      boolean useMinecraftText
   ) {
      drawRowWithColorAndIcon(context, textRenderer, label, ItemStack.EMPTY, x, y, width, height, hovered, selected, textColor, useMinecraftText);
   }

   public static void drawRowWithColorAndIcon(
      GuiGraphicsExtractor context,
      Font textRenderer,
      Component label,
      ItemStack icon,
      int x,
      int y,
      int width,
      int height,
      boolean hovered,
      boolean selected,
      int textColor,
      boolean useMinecraftText
   ) {
      int bg = selected ? THEME.rowFillSelected() : (hovered ? THEME.rowFillHovered() : THEME.rowFillNormal());
      int rightInset = 6;
      boolean hasIcon = icon != null && !icon.isEmpty();
      int textX = x + 6 + (hasIcon ? 15 : 0);
      UiRenderer.rect(context, UiBounds.of(x, y, width, height), bg);
      if (hasIcon) {
         drawSmallItemIcon(context, icon, x + 3, y + Math.max(0, (height - 12) / 2));
      }

      int textWidth = Math.max(1, x + width - rightInset - textX);
      int textY = UiSizing.alignTextY(y, height, THEME.fontHeight(UiTone.BODY), THEME.bodyTextNudge());
      if (useMinecraftText) {
         List<FormattedCharSequence> wrapped = textRenderer.split((FormattedText)(label == null ? Component.empty() : label), textWidth);
         FormattedCharSequence line = wrapped.isEmpty() ? Component.empty().getVisualOrderText() : wrapped.get(0);
         context.text(textRenderer, line, textX, textY, textColor, false);
      } else {
         String trimmed = UiText.trimToWidth(textRenderer, label == null ? "" : label.getString(), textWidth, THEME.fontFor(UiTone.BODY), textColor);
         UiText.draw(context, textRenderer, trimmed, THEME.fontFor(UiTone.BODY), textColor, textX, textY, false);
      }
   }

   private static void drawSmallItemIcon(GuiGraphicsExtractor context, ItemStack stack, int x, int y) {
      context.pose().pushMatrix();
      context.pose().scale(0.75F, 0.75F);
      context.item(stack, Math.round(x / 0.75F), Math.round(y / 0.75F));
      context.pose().popMatrix();
   }

   public static void drawDivider(GuiGraphicsExtractor context, int x, int y, int width) {
      UiRenderer.horizontalEdge(context, x + 5, y, Math.max(0, width - 10), 721420287);
   }

   public static void drawDeleteButton(GuiGraphicsExtractor context, int x, int y, int size, boolean hovered) {
      drawDeleteButton(context, x, y, size, size, hovered);
   }

   public static void drawDeleteButton(GuiGraphicsExtractor context, int x, int y, int width, int height, boolean hovered) {
      CompactSymbolButton.renderIcon(context, CompactSymbolButton.minecraftFont(), UiBounds.of(x, y, width, height), RiptideUiIcons.TRASH, hovered, true, true);
   }

   public static void drawStructuralButton(GuiGraphicsExtractor context, int x, int y, int size, String symbol, boolean hovered, boolean danger) {
      drawStructuralButton(context, x, y, size, size, symbol, hovered, danger);
   }

   public static void drawStructuralButton(GuiGraphicsExtractor context, int x, int y, int width, int height, String symbol, boolean hovered, boolean danger) {
      CompactSymbolButton.render(context, CompactSymbolButton.minecraftFont(), UiBounds.of(x, y, width, height), symbol, hovered, true, danger);
   }

   public static enum RowTone {
      NORMAL,
      READY,
      WARNING,
      MISSING,
      DANGER;
   }
}
