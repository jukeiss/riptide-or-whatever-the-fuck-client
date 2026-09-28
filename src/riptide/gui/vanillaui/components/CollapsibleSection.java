package riptide.gui.vanillaui.components;

import java.util.Locale;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiColors;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiRenderer;

public final class CollapsibleSection {
   private CollapsibleSection() {
   }

   public static void renderHeader(UiContext context, UiBounds bounds, String title, boolean collapsed, boolean hovered) {
      UiColors colors = context.theme().colors();
      UiRenderer.frame(context.graphics(), bounds, hovered ? colors.rowHover : colors.row, colors.borderSoft);
      UiRenderer.rect(context.graphics(), UiBounds.of(bounds.x(), bounds.y(), 2, bounds.height()), colors.accent);
      UiRenderer.chevron(context.graphics(), UiBounds.of(bounds.x() + 5, bounds.y() + Math.max(0, (bounds.height() - 8) / 2), 8, 8), !collapsed, colors.text);
      context.text()
         .drawFitted(
            context.graphics(),
            safe(title).toUpperCase(Locale.ROOT),
            bounds.x() + 17,
            context.text().centeredY(bounds),
            Math.max(1, bounds.width() - 22),
            colors.muted
         );
   }

   private static String safe(String value) {
      return value == null ? "" : value;
   }
}
