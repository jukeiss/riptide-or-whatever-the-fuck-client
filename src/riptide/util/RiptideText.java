package riptide.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FontDescription.Resource;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.assets.UiAssets;
import riptide.gui.vanillaui.components.UiText;

public final class RiptideText {
   private static final Map<Identifier, FontDescription> FONT_CACHE = new ConcurrentHashMap<>();

   private RiptideText() {
   }

   public static int colorFor(RiptideText.Tone tone) {
      return switch (tone) {
         case TITLE, LABEL, BODY -> RiptideColors.textPrimary();
         case MUTED -> RiptideColors.textMuted();
         case ACCENT -> RiptideColors.accent();
      };
   }

   public static MutableComponent literal(String value, RiptideText.Tone tone) {
      return Component.literal(value == null ? "" : value).setStyle(styleFor(tone));
   }

   public static MutableComponent literal(String value, int color) {
      Identifier fontId = fontIdFor(RiptideText.Tone.BODY);
      return Component.literal(value == null ? "" : value).setStyle(Style.EMPTY.withFont(fontSource(fontId)).withColor(color));
   }

   public static String sanitizeUiLabel(String value) {
      return value != null && !value.isEmpty()
         ? value.replace("✓", "X")
            .replace("✔", "X")
            .replace("✕", "X")
            .replace("✖", "X")
            .replace("→", "->")
            .replace("←", "<-")
            .replace("—", "-")
            .replace("–", "-")
            .replace("·", "-")
            .replace("▼", "v")
            .replace("▾", "v")
            .replace("▲", "^")
            .replace("▴", "^")
            .replace("●", "X")
            .replace("○", "O")
            .replace("≡", "=")
            .replace("★", "*")
            .replace("∞", "INF")
            .replace("⚠", "WARN")
            .replace("⚡", "BURST")
            .trim()
         : "";
   }

   public static Style styleFor(RiptideText.Tone tone) {
      int color = colorFor(tone);
      return Style.EMPTY.withFont(fontSource(fontIdFor(tone))).withColor(color);
   }

   public static int width(Font renderer, String value, RiptideText.Tone tone) {
      return UiText.width(renderer, value, fontIdFor(tone), colorFor(tone));
   }

   public static String trimToWidth(Font renderer, String value, int maxWidth, RiptideText.Tone tone) {
      return UiText.trimToWidth(renderer, value, maxWidth, fontIdFor(tone), colorFor(tone));
   }

   public static void draw(GuiGraphicsExtractor context, Font renderer, String value, RiptideText.Tone tone, int x, int y, boolean shadow) {
      UiText.draw(context, renderer, value, fontIdFor(tone), colorFor(tone), x, y, shadow);
   }

   public static void draw(GuiGraphicsExtractor context, Font renderer, String value, int color, int x, int y, boolean shadow) {
      UiText.draw(context, renderer, value, UiAssets.FONT_BODY, color, x, y, shadow);
   }

   private static Identifier fontIdFor(RiptideText.Tone tone) {
      return switch (tone) {
         case TITLE -> UiAssets.FONT_TITLE;
         case LABEL -> UiAssets.FONT_LABEL;
         case BODY, MUTED, ACCENT -> UiAssets.FONT_BODY;
      };
   }

   private static FontDescription fontSource(Identifier fontId) {
      return FONT_CACHE.computeIfAbsent(fontId, Resource::new);
   }

   public static enum Tone {
      TITLE,
      LABEL,
      BODY,
      MUTED,
      ACCENT;
   }
}
