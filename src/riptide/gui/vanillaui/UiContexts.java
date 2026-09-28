package riptide.gui.vanillaui;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.util.RiptideUiScale;

public final class UiContexts {
   private static final UiTheme THEME = new UiTheme();
   private static final Map<Font, UiTextRenderer> TEXT_RENDERERS = new IdentityHashMap<>();
   private static volatile UiContexts.FontEntry lastTextRenderer;

   private UiContexts() {
   }

   public static UiContext overlay(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
      return new UiContext(
         graphics,
         THEME,
         textRenderer(font),
         Math.max(1, RiptideUiScale.getVirtualScreenWidth()),
         Math.max(1, RiptideUiScale.getVirtualScreenHeight()),
         mouseX,
         mouseY,
         0.0F
      );
   }

   public static void refreshTheme() {
      THEME.refresh();
   }

   public static UiTextRenderer textRenderer(Font font) {
      UiContexts.FontEntry last = lastTextRenderer;
      if (last != null && last.font() == font) {
         return last.renderer();
      } else {
         UiTextRenderer renderer;
         synchronized (TEXT_RENDERERS) {
            renderer = TEXT_RENDERERS.computeIfAbsent(font, UiTextRenderer::new);
         }

         lastTextRenderer = new UiContexts.FontEntry(font, renderer);
         return renderer;
      }
   }

   private record FontEntry(Font font, UiTextRenderer renderer) {
   }
}
