package riptide.gui.vanillaui.direct;

import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;

public class DirectPanel extends DirectUiContainer {
   private final DirectUiColumn content = new DirectUiColumn();
   private boolean active = true;
   private DirectUiInsets padding = DirectUiInsets.all(6);
   private boolean drawBorder = true;
   private boolean drawFill = true;

   public DirectPanel() {
      this.add(this.content);
   }

   public DirectUiColumn content() {
      return this.content;
   }

   public DirectPanel setActive(boolean active) {
      this.active = active;
      return this;
   }

   public DirectPanel setPadding(DirectUiInsets padding) {
      DirectUiInsets next = padding == null ? DirectUiInsets.NONE : padding;
      if (!this.padding.equals(next)) {
         this.padding = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectPanel setDrawBorder(boolean drawBorder) {
      this.drawBorder = drawBorder;
      return this;
   }

   public DirectPanel setDrawFill(boolean drawFill) {
      this.drawFill = drawFill;
      return this;
   }

   @Override
   public float preferredWidth(DirectRenderContext context) {
      DirectUiInsets scaledPadding = context.theme().scale(this.padding);
      return scaledPadding.horizontal() + this.content.preferredWidth(context);
   }

   @Override
   public float preferredHeight(DirectRenderContext context, float availableWidth) {
      DirectUiInsets scaledPadding = context.theme().scale(this.padding);
      return scaledPadding.vertical() + this.content.preferredHeight(context, Math.max(0.0F, availableWidth - scaledPadding.horizontal()));
   }

   @Override
   protected void layoutChildren(DirectRenderContext context) {
      DirectUiInsets scaledPadding = context.theme().scale(this.padding);
      this.content
         .setBounds(
            this.x + scaledPadding.left(),
            this.y + scaledPadding.top(),
            Math.max(0.0F, this.width - scaledPadding.horizontal()),
            Math.max(0.0F, this.height - scaledPadding.vertical())
         );
   }

   @Override
   public void render(DirectRenderContext context) {
      if (this.visible) {
         DirectRenderContext drawContext = context.withAlpha(this.active ? 1.0F : 0.56F);
         int drawX = Math.round(this.x);
         int drawY = Math.round(this.y);
         int drawW = Math.round(this.width);
         int drawH = Math.round(this.height);
         int fill = this.active ? drawContext.theme().windowFill() : drawContext.theme().windowFillInactive();
         int border = this.active ? drawContext.theme().borderColor() : drawContext.theme().borderSoft();
         UiBounds bounds = UiBounds.of(drawX, drawY, drawW, drawH);
         if (this.drawFill && this.drawBorder) {
            UiRenderer.frame(drawContext.drawContext(), bounds, drawContext.applyAlpha(fill), drawContext.applyAlpha(border));
         } else if (this.drawFill) {
            UiRenderer.rect(drawContext.drawContext(), bounds, drawContext.applyAlpha(fill));
         } else if (this.drawBorder) {
            UiRenderer.outline(drawContext.drawContext(), bounds, drawContext.applyAlpha(border));
         }

         super.render(drawContext);
      }
   }
}
