package riptide.gui.vanillaui.components;

import riptide.gui.vanillaui.HoverFades;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiColors;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiRenderer;

public final class Chip {
   private Chip() {
   }

   public static void render(UiContext context, UiBounds bounds, String label, boolean selected, boolean hovered) {
      render(context, bounds, label, context.theme().colors().accent, selected, hovered);
   }

   public static void render(UiContext context, UiBounds bounds, String label, int accentColor, boolean selected, boolean hovered) {
      UiColors colors = context.theme().colors();
      float hoverT = HoverFades.get(HoverFades.key(bounds), hovered);
      int border = selected ? accentColor : colors.buttonBorder;
      int fill = selected ? tint(accentColor, 0.12F) : lerpRow(colors.row, colors.rowHover, hoverT);
      UiRenderer.frame(context.graphics(), bounds, fill, border);
      int text = !selected && !hovered ? colors.muted : colors.text;
      context.text().drawCentered(context.graphics(), label == null ? "" : label, bounds, text);
   }

   public static void renderDisabled(UiContext context, UiBounds bounds, String label) {
      render(context, bounds, label, false, false);
      UiRenderer.rect(context.graphics(), bounds, 1711276032);
   }

   private static int tint(int color, float alpha) {
      int a = Math.round(255.0F * alpha);
      return a << 24 | color & 16777215;
   }

   private static int lerpRow(int from, int to, float t) {
      int ar = from >> 16 & 0xFF;
      int ag = from >> 8 & 0xFF;
      int ab = from & 0xFF;
      int br = to >> 16 & 0xFF;
      int bg = to >> 8 & 0xFF;
      int bb = to & 0xFF;
      return 0xFF000000 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
   }
}
