package riptide.gui.vanillaui.components;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;

public final class CompactControlGlyphs {
   private CompactControlGlyphs() {
   }

   public static void drawClose(GuiGraphicsExtractor context, int x, int y, int size, int color, int shadowColor, float alpha) {
      if (!(alpha <= 0.001F)) {
         UiRenderer.cross(context, UiBounds.of(x, y, size, size), UiRenderer.applyAlpha(color, alpha));
      }
   }

   public static void drawChevron(
      GuiGraphicsExtractor context, int x, int y, int size, CompactControlGlyphs.ChevronDirection direction, int color, int shadowColor, float alpha
   ) {
      if (!(alpha <= 0.001F)) {
         int resolved = UiRenderer.applyAlpha(color, alpha);
         UiBounds bounds = UiBounds.of(x, y, size, size);
         switch (direction) {
            case RIGHT:
               UiRenderer.chevron(context, bounds, false, resolved);
               break;
            case DOWN:
               UiRenderer.chevron(context, bounds, true, resolved);
               break;
            case UP:
               UiRenderer.chevronUp(context, bounds, resolved);
         }
      }
   }

   public static void drawChevronProgress(GuiGraphicsExtractor context, int x, int y, int size, float progress, int color, int shadowColor, float alpha) {
      if (!(alpha <= 0.001F)) {
         float clamped = Math.max(0.0F, Math.min(1.0F, progress));
         int resolved = UiRenderer.applyAlpha(color, alpha);
         UiBounds bounds = UiBounds.of(x, y, size, size);
         if (clamped >= 0.5F) {
            UiRenderer.chevron(context, bounds, true, resolved);
         } else {
            UiRenderer.chevron(context, bounds, false, resolved);
         }
      }
   }

   public static enum ChevronDirection {
      RIGHT,
      DOWN,
      UP;
   }
}
