package riptide.gui.vanillaui.components;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public final class UiText {
   private static final int TRIM_CACHE_LIMIT = 2048;
   private static final Map<UiText.TrimKey, String> TRIM_CACHE = Collections.synchronizedMap(new LinkedHashMap<UiText.TrimKey, String>(256, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<UiText.TrimKey, String> eldest) {
         return this.size() > 2048;
      }
   });
   private static volatile int reloadGeneration;

   public static int reloadGeneration() {
      return reloadGeneration;
   }

   private UiText() {
   }

   public static MutableComponent literal(String value, Identifier fontId, int color) {
      return Component.literal(value == null ? "" : value).setStyle(Style.EMPTY.withColor(color));
   }

   public static int width(Font renderer, String value, Identifier fontId, int color) {
      return renderer.width(value == null ? "" : value);
   }

   public static int fontHeight(Identifier fontId) {
      return 9;
   }

   public static String trimToWidth(Font renderer, String value, int maxWidth, Identifier fontId, int color) {
      String safeValue = value == null ? "" : value;
      UiText.TrimKey key = new UiText.TrimKey(safeValue, maxWidth, fontId);
      String cached = TRIM_CACHE.get(key);
      if (cached != null) {
         return cached;
      } else if (safeValue.isEmpty()) {
         return "";
      } else {
         int allowedWidth = maxWidth + 4;
         if (width(renderer, safeValue, fontId, color) <= allowedWidth) {
            TRIM_CACHE.put(key, safeValue);
            return safeValue;
         } else {
            String trimmed = renderer.plainSubstrByWidth(safeValue, Math.max(0, allowedWidth));
            TRIM_CACHE.put(key, trimmed);
            return trimmed;
         }
      }
   }

   public static String trimToWidthEllipsis(Font renderer, String value, int maxWidth, Identifier fontId, int color) {
      String safeValue = value == null ? "" : value;
      if (!safeValue.isEmpty() && maxWidth > 0) {
         if (width(renderer, safeValue, fontId, color) <= maxWidth) {
            return safeValue;
         } else {
            String ellipsis = "...";
            int ellipsisWidth = width(renderer, ellipsis, fontId, color);
            if (maxWidth <= ellipsisWidth) {
               return trimToWidth(renderer, safeValue, maxWidth, fontId, color);
            } else {
               String base = renderer.plainSubstrByWidth(safeValue, Math.max(0, maxWidth - ellipsisWidth));

               while (!base.isEmpty() && width(renderer, base + ellipsis, fontId, color) > maxWidth) {
                  base = base.substring(0, base.length() - 1);
               }

               return base.isEmpty() ? ellipsis : base + ellipsis;
            }
         }
      } else {
         return "";
      }
   }

   public static void draw(GuiGraphicsExtractor context, Font renderer, String value, Identifier fontId, int color, int x, int y, boolean shadow) {
      context.text(renderer, value == null ? "" : value, x, y, color, shadow);
   }

   public static void drawFitted(
      GuiGraphicsExtractor context, Font renderer, String value, Identifier fontId, int color, int x, int y, int maxWidth, boolean shadow
   ) {
      String safe = value == null ? "" : value;
      if (!safe.isEmpty() && maxWidth > 0) {
         int measured = width(renderer, safe, fontId, color);
         if (measured <= maxWidth) {
            draw(context, renderer, safe, fontId, color, x, y, shadow);
         } else {
            float scale = Math.min(1.0F, (float)maxWidth / Math.max(1, measured));
            context.pose().pushMatrix();

            try {
               context.pose().scale(scale, scale);
               draw(context, renderer, safe, fontId, color, Math.round(x / scale), Math.round(y / scale), shadow);
            } finally {
               context.pose().popMatrix();
            }
         }
      }
   }

   public static void drawEllipsized(
      GuiGraphicsExtractor context, Font renderer, String value, Identifier fontId, int color, int x, int y, int maxWidth, boolean shadow
   ) {
      String safe = trimToWidthEllipsis(renderer, value, maxWidth, fontId, color);
      if (!safe.isEmpty()) {
         draw(context, renderer, safe, fontId, color, x, y, shadow);
      }
   }

   public static void fill(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1, int color) {
      context.fill(x0, y0, x1, y1, color);
   }

   public static void onClientResourceReload() {
      TRIM_CACHE.clear();
      reloadGeneration++;
   }

   private record TrimKey(String value, int maxWidth, Identifier fontId) {
   }
}
