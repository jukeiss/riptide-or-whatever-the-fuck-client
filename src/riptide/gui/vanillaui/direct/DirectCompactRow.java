package riptide.gui.vanillaui.direct;

import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.components.ConnectedButton;
import riptide.util.RiptideTheme;

public class DirectCompactRow extends DirectUiContainer {
   private DirectUiInsets padding = DirectUiInsets.NONE;
   private int gap = 0;
   private boolean underlineOnHover = false;
   private int underlineColor = -50373;
   private float occupiedWidth = 0.0F;

   public DirectCompactRow setPadding(DirectUiInsets padding) {
      DirectUiInsets next = padding == null ? DirectUiInsets.NONE : padding;
      if (!this.padding.equals(next)) {
         this.padding = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectCompactRow setGap(int gap) {
      int next = Math.max(0, gap);
      if (this.gap != next) {
         this.gap = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectCompactRow setUnderlineOnHover(boolean underlineOnHover) {
      this.underlineOnHover = underlineOnHover;
      return this;
   }

   public DirectCompactRow setUnderlineColor(int underlineColor) {
      this.underlineColor = underlineColor;
      return this;
   }

   @Override
   public float preferredWidth(DirectRenderContext context) {
      DirectUiInsets scaledPadding = context.theme().scale(this.padding);
      int scaledGap = context.theme().scale(this.gap);
      float total = scaledPadding.horizontal();
      int visibleCount = 0;

      for (DirectUiNode child : this.children) {
         if (child != null && child.isVisible()) {
            total += child.preferredWidth(context);
            visibleCount++;
         }
      }

      if (visibleCount > 1) {
         total += (visibleCount - 1) * scaledGap;
      }

      return total;
   }

   @Override
   public float preferredHeight(DirectRenderContext context, float availableWidth) {
      DirectUiInsets scaledPadding = context.theme().scale(this.padding);
      float tallest = 0.0F;

      for (DirectUiNode child : this.children) {
         if (child != null && child.isVisible()) {
            tallest = Math.max(tallest, child.preferredHeight(context, availableWidth));
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
      this.occupiedWidth = 0.0F;

      for (DirectUiNode childx : this.children) {
         if (childx != null && childx.isVisible()) {
            float childWidth = childx.growX() ? growWidth : childx.preferredWidth(context);
            float childHeight = Math.min(innerHeight, childx.preferredHeight(context, childWidth));
            float childY = innerY + Math.max(0.0F, (innerHeight - childHeight) / 2.0F);
            childx.setBounds(cursorX, childY, childWidth, childHeight);
            cursorX += childWidth + scaledGap;
         }
      }

      this.occupiedWidth = Math.max(0.0F, cursorX - innerX - (visibleCount > 0 ? scaledGap : 0));
   }

   @Override
   public void render(DirectRenderContext context) {
      if (this.visible) {
         super.render(context);
         this.drawConnectedButtonSeams(context);
         if (this.underlineOnHover) {
            float hover = this.updateHover(this.contains(context.mouseX(), context.mouseY()), context.delta());
            if (hover <= 0.0F || context.drawContext() == null) {
               return;
            }

            DirectUiInsets scaledPadding = context.theme().scale(this.padding);
            int drawX = Math.round(this.x + scaledPadding.left());
            int drawY = Math.round(this.y + this.height - context.theme().scale(1));
            float underlineAreaWidth = Math.max(this.occupiedWidth, this.width - scaledPadding.horizontal());
            int underlineWidth = Math.max(1, Math.round(underlineAreaWidth * hover));
            int underlineX = drawX + Math.max(0, Math.round((underlineAreaWidth - underlineWidth) / 2.0F));
            context.drawContext()
               .fill(
                  underlineX,
                  drawY,
                  underlineX + underlineWidth,
                  drawY + context.theme().scale(1),
                  RiptideTheme.recolor(this.underlineColor, RiptideTheme.Channel.ACCENT)
               );
         }
      }
   }

   private void drawConnectedButtonSeams(DirectRenderContext context) {
      if (context.drawContext() != null) {
         UiContext ui = null;
         DirectUiNode previous = null;

         for (DirectUiNode child : this.children) {
            if (child != null && child.isVisible()) {
               if (previous instanceof DirectUiButton left
                  && child instanceof DirectUiButton right
                  && left.isConnectedCell()
                  && right.isConnectedCell()
                  && Math.abs(left.x() + left.width() - right.x()) <= 0.5F) {
                  if (ui == null) {
                     ui = UiContexts.overlay(context.drawContext(), context.textRenderer(), Math.round(context.mouseX()), Math.round(context.mouseY()));
                  }

                  int leftColor = left.connectedSeamColor(ui);
                  int rightColor = right.connectedSeamColor(ui);
                  float leftWeight = left.connectedSeamWeight();
                  float rightWeight = right.connectedSeamWeight();
                  int seamColor = ConnectedButton.chooseSeamColor(leftColor, leftWeight, rightColor, rightWeight);
                  int seamX = Math.round(right.x());
                  int seamY = Math.round(Math.max(left.y(), right.y()));
                  int seamBottom = Math.round(Math.min(left.y() + left.height(), right.y() + right.height()));
                  ConnectedButton.drawVerticalSeam(ui, seamX, seamY, Math.max(0, seamBottom - seamY), seamColor);
               }

               previous = child;
            }
         }
      }
   }
}
