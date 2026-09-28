package riptide.gui.multi;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;

public final class MultiTooltip {
   private static final int MAX_WIDTH = 190;
   private static final int BG = -267386864;
   private static final int BORDER = 1347420320;
   private static final int TEXT = -1;

   private MultiTooltip() {
   }

   public static void render(GuiGraphicsExtractor graphics, Font font, String text, int mouseX, int mouseY) {
      if (graphics != null && font != null && text != null && !text.isBlank()) {
         List<String> lines = wrap(font, text, 190);
         if (!lines.isEmpty()) {
            int width = 0;

            for (String line : lines) {
               width = Math.max(width, font.width(line));
            }

            int height = lines.size() * 10 - 2;
            int screenWidth = Math.max(1, RiptideUiScale.getVirtualScreenWidth());
            int screenHeight = Math.max(1, RiptideUiScale.getVirtualScreenHeight());
            int x = mouseX + 10;
            int y = mouseY - 12;
            if (x + width + 4 > screenWidth) {
               x = Math.max(4, mouseX - width - 12);
            }

            if (y + height + 4 > screenHeight) {
               y = Math.max(4, screenHeight - height - 4);
            }

            if (y < 4) {
               y = 4;
            }

            graphics.nextStratum();
            UiRenderer.rect(graphics, UiBounds.of(x - 3, y - 3, width + 6, height + 6), -267386864);
            UiRenderer.rect(graphics, UiBounds.of(x - 3, y - 3, 2, height + 6), RiptideTheme.recolor(-50373, RiptideTheme.Channel.ACCENT));
            int lineY = y;

            for (String line : lines) {
               graphics.text(font, Component.literal(line).getVisualOrderText(), x, lineY, -1, true);
               lineY += 10;
            }
         }
      }
   }

   public static boolean hovered(int x, int y, int w, int h, int mouseX, int mouseY) {
      return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
   }

   private static List<String> wrap(Font font, String text, int maxWidth) {
      List<String> out = new ArrayList<>();
      StringBuilder line = new StringBuilder();

      for (String word : text.split(" ")) {
         String candidate = line.isEmpty() ? word : line + " " + word;
         if (font.width(candidate) > maxWidth && !line.isEmpty()) {
            out.add(line.toString());
            line = new StringBuilder(word);
         } else {
            line = new StringBuilder(candidate);
         }
      }

      if (!line.isEmpty()) {
         out.add(line.toString());
      }

      return out;
   }
}
