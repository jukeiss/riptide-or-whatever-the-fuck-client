package riptide.gui.vanillaui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class UiTextRenderer {
   private static final int MAX_CACHE = 2048;
   private static final int VANILLA_FONT_HEIGHT = 9;
   private static final int OPTICAL_TEXT_NUDGE = 1;
   private final Font font;
   private final Map<String, Integer> widthCache = new LinkedHashMap<String, Integer>(256, 0.75F, true) {
      {
         Objects.requireNonNull(UiTextRenderer.this);
      }

      @Override
      protected boolean removeEldestEntry(Entry<String, Integer> eldest) {
         return this.size() > 2048;
      }
   };
   private final Map<UiTextRenderer.TrimKey, String> trimCache = new LinkedHashMap<UiTextRenderer.TrimKey, String>(256, 0.75F, true) {
      {
         Objects.requireNonNull(UiTextRenderer.this);
      }

      @Override
      protected boolean removeEldestEntry(Entry<UiTextRenderer.TrimKey, String> eldest) {
         return this.size() > 2048;
      }
   };
   private final Map<UiTextRenderer.WrapKey, List<String>> wrapCache = new LinkedHashMap<UiTextRenderer.WrapKey, List<String>>(256, 0.75F, true) {
      {
         Objects.requireNonNull(UiTextRenderer.this);
      }

      @Override
      protected boolean removeEldestEntry(Entry<UiTextRenderer.WrapKey, List<String>> eldest) {
         return this.size() > 2048;
      }
   };

   public UiTextRenderer(Font font) {
      this.font = font;
   }

   public Font font() {
      return this.font;
   }

   public int width(String text) {
      String safe = text == null ? "" : text;
      Integer cached = this.widthCache.get(safe);
      if (cached != null) {
         return cached;
      } else {
         int width = this.font.width(safe);
         this.widthCache.put(safe, width);
         return width;
      }
   }

   public String trim(String text, int maxWidth) {
      String safe = text == null ? "" : text;
      if (maxWidth <= 0) {
         return "";
      } else if (this.width(safe) <= maxWidth) {
         return safe;
      } else {
         UiTextRenderer.TrimKey key = new UiTextRenderer.TrimKey(safe, maxWidth);
         String cached = this.trimCache.get(key);
         if (cached != null) {
            return cached;
         } else {
            String trimmed = this.font.plainSubstrByWidth(safe, maxWidth);
            this.trimCache.put(key, trimmed);
            return trimmed;
         }
      }
   }

   public String trimEllipsis(String text, int maxWidth) {
      String safe = text == null ? "" : text;
      if (maxWidth > 0 && !safe.isEmpty()) {
         if (this.width(safe) <= maxWidth) {
            return safe;
         } else {
            String ellipsis = "...";
            int ellipsisWidth = this.width(ellipsis);
            if (maxWidth <= ellipsisWidth) {
               return this.trim(safe, maxWidth);
            } else {
               String base = this.font.plainSubstrByWidth(safe, Math.max(0, maxWidth - ellipsisWidth));

               while (!base.isEmpty() && this.width(base + ellipsis) > maxWidth) {
                  base = base.substring(0, base.length() - 1);
               }

               return base.isEmpty() ? ellipsis : base + ellipsis;
            }
         }
      } else {
         return "";
      }
   }

   public void draw(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
      graphics.text(this.font, text == null ? "" : text, x, y, color, false);
   }

   public void drawTrimmed(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color) {
      this.draw(graphics, this.trim(text, maxWidth), x, y, color);
   }

   public void drawFitted(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color) {
      this.drawFitted(graphics, text, x, y, maxWidth, color, 0.75F);
   }

   public void drawFitted(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color, float minimumScale) {
      String safe = text == null ? "" : text;
      if (!safe.isEmpty() && maxWidth > 0) {
         int textWidth = this.width(safe);
         if (textWidth <= maxWidth) {
            this.draw(graphics, safe, x, y, color);
         } else {
            float requiredScale = Math.min(1.0F, (float)maxWidth / Math.max(1, textWidth));
            float scale = requiredScale;
            graphics.pose().pushMatrix();

            try {
               graphics.pose().scale(scale, scale);
               this.draw(graphics, safe, Math.round(x / scale), Math.round(y / scale), color);
            } finally {
               graphics.pose().popMatrix();
            }
         }
      }
   }

   public void drawEllipsized(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color) {
      String display = this.trimEllipsis(text, maxWidth);
      if (!display.isEmpty()) {
         this.draw(graphics, display, x, y, color);
      }
   }

   public List<String> wrapFully(String text, int maxWidth) {
      String safe = text == null ? "" : text;
      int width = Math.max(1, maxWidth);
      UiTextRenderer.WrapKey key = new UiTextRenderer.WrapKey(safe, width);
      List<String> cached = this.wrapCache.get(key);
      if (cached != null) {
         return cached;
      } else {
         List<String> lines = new ArrayList<>();

         for (TextWrapLayout.Line line : TextWrapLayout.layout(safe, width, (start, end) -> this.width(safe.substring(start, end)))) {
            lines.add(safe.substring(line.start(), line.renderEnd()));
         }

         if (lines.isEmpty()) {
            lines.add("");
         }

         List<String> result = List.copyOf(lines);
         this.wrapCache.put(key, result);
         return result;
      }
   }

   public void drawCentered(GuiGraphicsExtractor graphics, String text, UiBounds bounds, int color) {
      this.drawCentered(graphics, text, bounds, color, 0);
   }

   public void drawCentered(GuiGraphicsExtractor graphics, String text, UiBounds bounds, int color, int horizontalNudge) {
      String display = text == null ? "" : text;
      int maxWidth = Math.max(0, bounds.width() - 4);
      float scale = this.width(display) <= maxWidth ? 1.0F : Math.min(1.0F, (float)maxWidth / Math.max(1, this.width(display)));
      int displayWidth = Math.round(this.width(display) * scale);
      int centeredX = bounds.x() + Math.max(2, (bounds.width() - displayWidth + 1) / 2);
      int minX = bounds.x() + 1;
      int maxX = Math.max(minX, bounds.right() - displayWidth - 1);
      int x = Math.max(minX, Math.min(maxX, centeredX + horizontalNudge));
      int y = this.centeredY(bounds);
      this.drawFitted(graphics, display, x, y, maxWidth, color);
   }

   public int centeredY(UiBounds bounds) {
      int spareHeight = Math.max(0, bounds.height() - 9);
      return bounds.y() + Math.max(1, (spareHeight + 1) / 2 + 1);
   }

   private record TrimKey(String text, int maxWidth) {
      private TrimKey(String text, int maxWidth) {
         Objects.requireNonNull(text);
         this.text = text;
         this.maxWidth = maxWidth;
      }
   }

   private record WrapKey(String text, int maxWidth) {
      private WrapKey(String text, int maxWidth) {
         Objects.requireNonNull(text);
         this.text = text;
         this.maxWidth = maxWidth;
      }
   }
}
