package riptide.gui.vanillaui.direct;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.components.CompactTheme;

public final class DirectRenderContext {
   private final GuiGraphicsExtractor drawContext;
   private final Font textRenderer;
   private final DirectViewport viewport;
   private final CompactTheme theme;
   private final float mouseX;
   private final float mouseY;
   private final float delta;
   private final float alpha;

   public DirectRenderContext(
      GuiGraphicsExtractor drawContext, Font textRenderer, DirectViewport viewport, CompactTheme theme, float mouseX, float mouseY, float delta
   ) {
      this(drawContext, textRenderer, viewport, theme, mouseX, mouseY, delta, 1.0F);
   }

   public DirectRenderContext(
      GuiGraphicsExtractor drawContext, Font textRenderer, DirectViewport viewport, CompactTheme theme, float mouseX, float mouseY, float delta, float alpha
   ) {
      this.drawContext = drawContext;
      this.textRenderer = textRenderer;
      this.viewport = viewport;
      this.theme = theme;
      this.mouseX = mouseX;
      this.mouseY = mouseY;
      this.delta = delta;
      this.alpha = Math.max(0.0F, Math.min(1.0F, alpha));
   }

   public GuiGraphicsExtractor drawContext() {
      return this.drawContext;
   }

   public Font textRenderer() {
      return this.textRenderer;
   }

   public DirectViewport viewport() {
      return this.viewport;
   }

   public CompactTheme theme() {
      return this.theme;
   }

   public float mouseX() {
      return this.mouseX;
   }

   public float mouseY() {
      return this.mouseY;
   }

   public float delta() {
      return this.delta;
   }

   public float alpha() {
      return this.alpha;
   }

   public DirectRenderContext withAlpha(float alpha) {
      return new DirectRenderContext(this.drawContext, this.textRenderer, this.viewport, this.theme, this.mouseX, this.mouseY, this.delta, alpha);
   }

   public int applyAlpha(int color) {
      return applyAlpha(color, this.alpha);
   }

   public void drawTexturedQuad(Identifier textureId, int x1, int y1, int x2, int y2) {
      if (this.drawContext != null && textureId != null) {
         this.drawContext.blit(textureId, x1, y1, x2, y2, 0.0F, 1.0F, 0.0F, 1.0F);
         if (this.alpha < 0.999F) {
            int overlayAlpha = Math.max(0, Math.min(255, Math.round((1.0F - this.alpha) * 255.0F)));
            this.drawContext.fill(x1, y1, x2, y2, overlayAlpha << 24 | 460553);
         }
      }
   }

   public static int applyAlpha(int color, float alpha) {
      int a = color >>> 24 & 0xFF;
      int scaled = Math.max(0, Math.min(255, Math.round(a * Math.max(0.0F, Math.min(1.0F, alpha)))));
      return scaled << 24 | color & 16777215;
   }
}
