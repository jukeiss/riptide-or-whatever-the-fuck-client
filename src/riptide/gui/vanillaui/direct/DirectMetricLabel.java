package riptide.gui.vanillaui.direct;

import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiTextRenderer;

public final class DirectMetricLabel extends DirectUiNode {
   private String key;
   private String value;
   private int keyColor = -4743522;
   private int valueColor = -791321;

   public DirectMetricLabel(String key, String value) {
      this.key = key == null ? "" : key;
      this.value = value == null ? "" : value;
      this.height = 11.0F;
   }

   public DirectMetricLabel setValue(String value) {
      String next = value == null ? "" : value;
      if (!this.value.equals(next)) {
         this.value = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectMetricLabel setKeyColor(int keyColor) {
      this.keyColor = keyColor;
      return this;
   }

   public DirectMetricLabel setValueColor(int valueColor) {
      this.valueColor = valueColor;
      return this;
   }

   public DirectMetricLabel setGrowX(boolean growX) {
      super.setGrowX(growX);
      return this;
   }

   @Override
   public float preferredWidth(DirectRenderContext context) {
      return UiContexts.textRenderer(context.textRenderer()).width(this.key + this.value);
   }

   @Override
   public float preferredHeight(DirectRenderContext context, float availableWidth) {
      return 11.0F;
   }

   @Override
   public void render(DirectRenderContext context) {
      if (this.visible) {
         UiTextRenderer text = UiContexts.textRenderer(context.textRenderer());
         int drawX = Math.round(this.x);
         int drawY = Math.round(this.y) + Math.max(1, (Math.round(this.height) - 9) / 2);
         String safeKey = this.key == null ? "" : this.key;
         String safeValue = this.value == null ? "" : this.value;
         text.draw(context.drawContext(), safeKey, drawX, drawY, context.applyAlpha(this.keyColor));
         int keyWidth = text.width(safeKey);
         text.drawFitted(
            context.drawContext(), safeValue, drawX + keyWidth, drawY, Math.max(1, Math.round(this.width) - keyWidth), context.applyAlpha(this.valueColor)
         );
      }
   }
}
