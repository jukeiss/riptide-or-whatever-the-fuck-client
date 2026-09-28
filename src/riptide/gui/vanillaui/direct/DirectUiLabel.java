package riptide.gui.vanillaui.direct;

import riptide.gui.vanillaui.components.UiSizing;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;

public class DirectUiLabel extends DirectUiNode {
   private String text;
   private UiTone tone;
   private boolean shadow;
   private boolean trimToBounds;

   public DirectUiLabel(String text, UiTone tone) {
      this.text = text == null ? "" : text;
      this.tone = tone == null ? UiTone.BODY : tone;
      this.height = 10.0F;
   }

   public DirectUiLabel setText(String text) {
      String next = text == null ? "" : text;
      if (!this.text.equals(next)) {
         this.text = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectUiLabel setTone(UiTone tone) {
      UiTone next = tone == null ? UiTone.BODY : tone;
      if (this.tone != next) {
         this.tone = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectUiLabel setShadow(boolean shadow) {
      this.shadow = shadow;
      return this;
   }

   public DirectUiLabel setTrimToBounds(boolean trimToBounds) {
      this.trimToBounds = trimToBounds;
      return this;
   }

   public DirectUiLabel setGrowX(boolean growX) {
      super.setGrowX(growX);
      return this;
   }

   @Override
   public float preferredWidth(DirectRenderContext context) {
      return UiText.width(context.textRenderer(), this.text, context.theme().fontFor(this.tone), context.theme().color(this.tone));
   }

   @Override
   public float preferredHeight(DirectRenderContext context, float availableWidth) {
      return context.theme().lineHeight(this.tone, 3);
   }

   @Override
   public void render(DirectRenderContext context) {
      if (this.visible) {
         String displayComponent = this.text;
         if (this.trimToBounds && this.width > 0.0F) {
            displayComponent = UiText.trimToWidth(
               context.textRenderer(), this.text, Math.round(this.width), context.theme().fontFor(this.tone), context.theme().color(this.tone)
            );
         }

         UiText.draw(
            context.drawContext(),
            context.textRenderer(),
            displayComponent,
            context.theme().fontFor(this.tone),
            context.applyAlpha(context.theme().color(this.tone)),
            Math.round(this.x),
            UiSizing.alignTextY(Math.round(this.y), Math.round(this.height), context.theme().fontHeight(this.tone), context.theme().bodyTextNudge()),
            this.shadow
         );
      }
   }
}
