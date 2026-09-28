package riptide.gui.vanillaui.direct;

public class DirectRow extends DirectUiContainer {
   private DirectUiInsets padding = DirectUiInsets.NONE;
   private int gap = 0;

   public DirectRow setPadding(DirectUiInsets padding) {
      DirectUiInsets next = padding == null ? DirectUiInsets.NONE : padding;
      if (!this.padding.equals(next)) {
         this.padding = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectRow setGap(int gap) {
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
      float tallest = 0.0F;
      float innerWidth = Math.max(0.0F, availableWidth - scaledPadding.horizontal());

      for (DirectUiNode child : this.children) {
         if (child != null && child.isVisible()) {
            tallest = Math.max(tallest, child.preferredHeight(context, innerWidth));
         }
      }

      return scaledPadding.top() + tallest + scaledPadding.bottom();
   }

   @Override
   protected void layoutChildren(DirectRenderContext context) {
      DirectUiInsets scaledPadding = context.theme().scale(this.padding);
      int scaledGap = context.theme().scale(this.gap);
      float innerX = this.x + scaledPadding.left();
      float innerY = this.y + scaledPadding.top();
      float innerWidth = Math.max(0.0F, this.width - scaledPadding.horizontal());
      float innerHeight = Math.max(0.0F, this.height - scaledPadding.vertical());
      float fixedWidth = 0.0F;
      int growCount = 0;
      int visibleCount = 0;

      for (DirectUiNode child : this.children) {
         if (child != null && child.isVisible()) {
            visibleCount++;
            if (child.growX()) {
               growCount++;
            } else {
               fixedWidth += child.preferredWidth(context);
            }
         }
      }

      float totalGap = Math.max(0, visibleCount - 1) * scaledGap;
      float freeWidth = Math.max(0.0F, innerWidth - fixedWidth - totalGap);
      float growWidth = growCount > 0 ? freeWidth / growCount : 0.0F;
      float cursorX = innerX;

      for (DirectUiNode childx : this.children) {
         if (childx != null && childx.isVisible()) {
            float childWidth = childx.growX() ? growWidth : childx.preferredWidth(context);
            float childHeight = Math.max(innerHeight, childx.preferredHeight(context, childWidth));
            childx.setBounds(cursorX, innerY, childWidth, childHeight);
            cursorX += childWidth + scaledGap;
         }
      }
   }
}
