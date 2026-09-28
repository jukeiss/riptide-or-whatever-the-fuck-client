package riptide.gui.vanillaui.direct;

public class DirectSpacer extends DirectUiNode {
   public DirectSpacer(float width, float height) {
      this.width = Math.max(0.0F, width);
      this.height = Math.max(0.0F, height);
   }

   @Override
   public float preferredWidth(DirectRenderContext context) {
      return context.theme().scale(this.width);
   }

   @Override
   public float preferredHeight(DirectRenderContext context, float availableWidth) {
      return context.theme().scale(this.height);
   }
}
