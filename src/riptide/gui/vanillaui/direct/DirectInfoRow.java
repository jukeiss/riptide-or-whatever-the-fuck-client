package riptide.gui.vanillaui.direct;

import riptide.gui.vanillaui.components.UiSizing;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.util.RiptideTheme;

public class DirectInfoRow extends DirectUiNode {
   private String label;
   private String value;
   private float labelWidth = 76.0F;
   private UiTone labelTone = UiTone.MUTED;
   private UiTone valueTone = UiTone.BODY;
   private Integer valueColorOverride;
   private int textYOffset = 0;
   private Runnable onPress;

   public DirectInfoRow(String label, String value) {
      this.label = label == null ? "" : label;
      this.value = value == null ? "--" : value;
      this.height = 12.0F;
   }

   public DirectInfoRow setLabel(String label) {
      this.label = label == null ? "" : label;
      return this;
   }

   public DirectInfoRow setValue(String value) {
      this.value = value == null ? "--" : value;
      return this;
   }

   public DirectInfoRow setLabelWidth(float labelWidth) {
      this.labelWidth = Math.max(24.0F, labelWidth);
      return this;
   }

   public DirectInfoRow setLabelTone(UiTone labelTone) {
      this.labelTone = labelTone == null ? UiTone.MUTED : labelTone;
      return this;
   }

   public DirectInfoRow setValueTone(UiTone valueTone) {
      this.valueTone = valueTone == null ? UiTone.BODY : valueTone;
      return this;
   }

   public DirectInfoRow setValueColorOverride(Integer valueColorOverride) {
      this.valueColorOverride = valueColorOverride;
      return this;
   }

   public DirectInfoRow setTextYOffset(int textYOffset) {
      this.textYOffset = textYOffset;
      return this;
   }

   public DirectInfoRow setOnPress(Runnable onPress) {
      this.onPress = onPress;
      return this;
   }

   private boolean isClickable() {
      return this.onPress != null && this.enabled;
   }

   @Override
   public float preferredHeight(DirectRenderContext context, float availableWidth) {
      int baselineHeight = 12;
      int labelHeight = context.theme().fontHeight(this.labelTone) + context.theme().scale(3);
      int valueHeight = context.theme().fontHeight(this.valueTone) + context.theme().scale(3);
      return Math.max(baselineHeight, Math.max(labelHeight, valueHeight));
   }

   @Override
   public void render(DirectRenderContext context) {
      if (this.visible) {
         int drawX = Math.round(this.x);
         int drawY = Math.round(this.y);
         int drawW = Math.round(this.width);
         int drawH = Math.round(this.height);
         int minSegment = context.theme().scale(24);
         int labelW = Math.round(Math.min(this.labelWidth, (float)Math.max(minSegment, drawW - minSegment)));
         int valueX = drawX + labelW;
         int valueW = Math.max(1, drawW - labelW);
         boolean clickable = this.isClickable();
         boolean hovered = clickable && this.contains(context.mouseX(), context.mouseY());
         if (hovered) {
            context.drawContext()
               .fill(drawX, drawY, drawX + drawW, drawY + drawH, context.applyAlpha(RiptideTheme.recolor(339024671, RiptideTheme.Channel.ACCENT)));
         }

         int labelColor = clickable && hovered
            ? UiSizing.lerpColor(context.theme().color(this.labelTone), RiptideTheme.recolor(-791321, RiptideTheme.Channel.TEXT), 0.35F)
            : context.theme().color(this.labelTone);
         int valueColor = this.valueColorOverride != null ? this.valueColorOverride : context.theme().color(this.valueTone);
         if (clickable && hovered) {
            valueColor = UiSizing.lerpColor(valueColor, RiptideTheme.recolor(-2314, RiptideTheme.Channel.TEXT), 0.28F);
         }

         int labelTextY = UiSizing.alignTextY(drawY, drawH, context.theme().fontHeight(this.labelTone), context.theme().bodyTextNudge() + this.textYOffset);
         int valueTextY = UiSizing.alignTextY(drawY, drawH, context.theme().fontHeight(this.valueTone), context.theme().bodyTextNudge() + this.textYOffset);
         String displayLabel = UiText.trimToWidth(
            context.textRenderer(), this.label, Math.max(1, labelW - context.theme().scale(4)), context.theme().fontFor(this.labelTone), labelColor
         );
         String displayValue = UiText.trimToWidth(
            context.textRenderer(), this.value, Math.max(1, valueW - context.theme().scale(2)), context.theme().fontFor(this.valueTone), valueColor
         );
         UiText.draw(
            context.drawContext(),
            context.textRenderer(),
            displayLabel,
            context.theme().fontFor(this.labelTone),
            context.applyAlpha(labelColor),
            drawX,
            labelTextY,
            false
         );
         UiText.draw(
            context.drawContext(),
            context.textRenderer(),
            displayValue,
            context.theme().fontFor(this.valueTone),
            context.applyAlpha(valueColor),
            valueX,
            valueTextY,
            false
         );
      }
   }

   @Override
   public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
      if (button == 0 && this.isClickable() && this.contains(mouseX, mouseY)) {
         this.onPress.run();
         return true;
      } else {
         return false;
      }
   }
}
