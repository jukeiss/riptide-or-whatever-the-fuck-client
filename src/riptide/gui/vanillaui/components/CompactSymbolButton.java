package riptide.gui.vanillaui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.HoverFades;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiColors;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiIcons;

public final class CompactSymbolButton {
   public static final String CLOSE = "X";
   public static final String MOVE_UP = "^";
   public static final String MOVE_DOWN = "v";
   public static final String EXPAND = "+";
   public static final String COLLAPSE = "-";

   private CompactSymbolButton() {
   }

   public static void render(GuiGraphicsExtractor graphics, Font font, UiBounds bounds, String symbol, boolean hovered, boolean active, boolean danger) {
      if (graphics != null && font != null && bounds != null && bounds.width() > 0 && bounds.height() > 0) {
         UiContext context = UiContexts.overlay(graphics, font, -10000, -10000);
         UiColors colors = context.theme().colors();
         int fill = danger ? RiptideTheme.recolor(-868936937, RiptideTheme.Channel.DANGER) : -1156179423;
         UiRenderer.frame(graphics, bounds, fill, danger ? colors.bad : colors.borderSoft);
         float hoverT = HoverFades.get(HoverFades.key(bounds), hovered && active);
         if (hoverT > 0.001F) {
            UiRenderer.rect(graphics, bounds.inset(1), (int)(32.0F * hoverT) << 24 | 16777215);
         }

         if (!active) {
            UiRenderer.rect(graphics, bounds.inset(1), 1711276032);
         }

         int color = active ? colors.text : colors.disabled;
         if ("X".equals(symbol)) {
            UiRenderer.cross(graphics, bounds.inset(3), color);
         } else if ("^".equals(symbol) || "v".equals(symbol)) {
            UiBounds glyph = centeredSquare(bounds, 3);
            if ("^".equals(symbol)) {
               UiRenderer.chevronUp(graphics, glyph, color);
            } else {
               UiRenderer.chevron(graphics, glyph, true, color);
            }
         } else if ("+".equals(symbol)) {
            UiBounds glyph = centeredSquare(bounds, 3);
            RiptideUiIcons.blit(graphics, RiptideUiIcons.PLUS, glyph.x(), glyph.y(), glyph.width(), glyph.height(), color);
         } else {
            String glyph = symbol == null ? "" : symbol;
            context.text().drawCentered(graphics, glyph, bounds, color);
         }
      }
   }

   private static UiBounds centeredSquare(UiBounds bounds, int inset) {
      int gs = Math.max(4, Math.min(bounds.width(), bounds.height()) - inset * 2);
      int box = Math.min(bounds.width(), bounds.height());
      if ((box - gs & 1) != 0) {
         gs--;
      }

      return UiBounds.of(bounds.x() + (bounds.width() - gs) / 2, bounds.y() + (bounds.height() - gs) / 2, Math.max(1, gs), Math.max(1, gs));
   }

   public static void renderIcon(GuiGraphicsExtractor graphics, Font font, UiBounds bounds, Identifier icon, boolean hovered, boolean active, boolean danger) {
      if (graphics != null && font != null && bounds != null && bounds.width() > 0 && bounds.height() > 0) {
         UiContext context = UiContexts.overlay(graphics, font, -10000, -10000);
         UiColors colors = context.theme().colors();
         int fill = danger ? -799925730 : -1156179423;
         int borderColor = danger ? -1947062 : colors.borderSoft;
         UiRenderer.frame(graphics, bounds, fill, borderColor);
         float hoverT = HoverFades.get(HoverFades.key(bounds), hovered && active);
         if (hoverT > 0.001F) {
            UiRenderer.rect(graphics, bounds.inset(1), (int)(32.0F * hoverT) << 24 | 16777215);
         }

         if (!active) {
            UiRenderer.rect(graphics, bounds.inset(1), 1711276032);
         }

         int color = active ? -1 : -2130706433;
         int size = Math.max(4, Math.min(bounds.width(), bounds.height()) - 5);
         int box = Math.min(bounds.width(), bounds.height());
         if ((box - size & 1) != 0) {
            size--;
         }

         RiptideUiIcons.blit(graphics, icon, bounds.x() + (bounds.width() - size) / 2, bounds.y() + (bounds.height() - size) / 2, size, color);
      }
   }

   public static void renderGlyph(GuiGraphicsExtractor graphics, Font font, UiBounds bounds, String symbol, int color, float alpha) {
      if (graphics != null && font != null && bounds != null && !(alpha <= 0.001F)) {
         int resolved = UiRenderer.applyAlpha(color, alpha);
         String glyph = symbol == null ? "" : symbol;
         UiContexts.overlay(graphics, font, -10000, -10000).text().drawCentered(graphics, glyph, bounds, resolved);
      }
   }

   public static Font minecraftFont() {
      Minecraft minecraft = Minecraft.getInstance();
      return minecraft == null ? null : minecraft.font;
   }
}
