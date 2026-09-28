package riptide.gui.vanillaui.direct;

public class DirectViewportSlot extends DirectUiNode {
   private float preferredHeight = 64.0F;

   public DirectViewportSlot setPreferredHeight(float preferredHeight) {
      float next = Math.max(0.0F, preferredHeight);
      if (Float.compare(this.preferredHeight, next) != 0) {
         this.preferredHeight = next;
         this.markLayoutDirty();
      }

      return this;
   }

   @Override
   public float preferredHeight(DirectRenderContext context, float availableWidth) {
      return context.theme().scale(this.preferredHeight);
   }
}
