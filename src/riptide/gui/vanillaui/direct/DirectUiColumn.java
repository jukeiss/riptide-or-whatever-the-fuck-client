package riptide.gui.vanillaui.direct;

public class DirectUiColumn extends DirectUiContainer {
   private DirectUiInsets padding = DirectUiInsets.NONE;
   private int gap = 0;

   public DirectUiColumn setPadding(DirectUiInsets padding) {
      DirectUiInsets next = padding == null ? DirectUiInsets.NONE : padding;
      if (!this.padding.equals(next)) {
         this.padding = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectUiColumn setGap(int gap) {
      int next = Math.max(0, gap);
      if (this.gap != next) {
         this.gap = next;
         this.markLayoutDirty();
      }

      return this;
   }

   @Override
   public float preferredHeight(DirectRenderContext context, float availableWidth) {
      DirectUiInsets scaledPadding = context.theme().scale(this.padding);
      int scaledGap = context.theme().scale(this.gap);
      float innerWidth = Math.max(0.0F, availableWidth - scaledPadding.horizontal());
      float total = scaledPadding.top() + scaledPadding.bottom();
      boolean first = true;

      for (DirectUiNode child : this.children) {
         if (child != null && child.isVisible()) {
            if (!first) {
               total += scaledGap;
            }

            total += child.preferredHeight(context, innerWidth);
            first = false;
         }
      }

      return total;
   }

   @Override
   protected void layoutChildren(DirectRenderContext context) {
      DirectUiInsets scaledPadding = context.theme().scale(this.padding);
      int scaledGap = context.theme().scale(this.gap);
      float innerX = this.x + scaledPadding.left();
      float cursorY = this.y + scaledPadding.top();
      float innerWidth = Math.max(0.0F, this.width - scaledPadding.horizontal());

      for (DirectUiNode child : this.children) {
         if (child != null && child.isVisible()) {
            float childHeight = child.preferredHeight(context, innerWidth);
            child.setBounds(innerX, cursorY, innerWidth, childHeight);
            cursorY += childHeight + scaledGap;
         }
      }
   }
}
