package riptide.gui.vanillaui.direct;

public class DirectFormRow extends DirectUiContainer {
   private final DirectUiNode labelNode;
   private final DirectUiNode controlNode;
   private DirectUiInsets padding = DirectUiInsets.NONE;
   private int gap = 0;
   private float labelWidth = -1.0F;
   private boolean alignControlEnd = false;

   public DirectFormRow(DirectUiNode labelNode, DirectUiNode controlNode) {
      this.labelNode = this.add(labelNode);
      this.controlNode = this.add(controlNode);
   }

   public DirectFormRow setPadding(DirectUiInsets padding) {
      DirectUiInsets next = padding == null ? DirectUiInsets.NONE : padding;
      if (!this.padding.equals(next)) {
         this.padding = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectFormRow setGap(int gap) {
      int next = Math.max(0, gap);
      if (this.gap != next) {
         this.gap = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectFormRow setLabelWidth(float labelWidth) {
      float next = Math.max(0.0F, labelWidth);
      if (Float.compare(this.labelWidth, next) != 0) {
         this.labelWidth = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectFormRow setAlignControlEnd(boolean alignControlEnd) {
      if (this.alignControlEnd != alignControlEnd) {
         this.alignControlEnd = alignControlEnd;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectUiNode labelNode() {
      return this.labelNode;
   }

   public DirectUiNode controlNode() {
      return this.controlNode;
   }

   private float resolvedLabelWidth(DirectRenderContext context) {
      if (this.labelWidth > 0.0F) {
         return this.labelWidth;
      } else {
         return this.labelNode == null ? 0.0F : this.labelNode.preferredWidth(context);
      }
   }

   @Override
   public float preferredWidth(DirectRenderContext context) {
      DirectUiInsets scaledPadding = context.theme().scale(this.padding);
      int scaledGap = context.theme().scale(this.gap);
      float total = scaledPadding.horizontal();
      total += this.resolvedLabelWidth(context);
      if (this.labelNode != null && this.labelNode.isVisible() && this.controlNode != null && this.controlNode.isVisible()) {
         total += scaledGap;
      }

      if (this.controlNode != null && this.controlNode.isVisible()) {
         total += this.controlNode.preferredWidth(context);
      }

      return total;
   }

   @Override
   public float preferredHeight(DirectRenderContext context, float availableWidth) {
      DirectUiInsets scaledPadding = context.theme().scale(this.padding);
      float labelHeight = this.labelNode != null && this.labelNode.isVisible() ? this.labelNode.preferredHeight(context, availableWidth) : 0.0F;
      float controlHeight = this.controlNode != null && this.controlNode.isVisible() ? this.controlNode.preferredHeight(context, availableWidth) : 0.0F;
      return scaledPadding.top() + Math.max(labelHeight, controlHeight) + scaledPadding.bottom();
   }

   @Override
   protected void layoutChildren(DirectRenderContext context) {
      DirectUiInsets scaledPadding = context.theme().scale(this.padding);
      int scaledGap = context.theme().scale(this.gap);
      float innerX = this.x + scaledPadding.left();
      float innerY = this.y + scaledPadding.top();
      float innerWidth = Math.max(0.0F, this.width - scaledPadding.horizontal());
      float innerHeight = Math.max(0.0F, this.height - scaledPadding.vertical());
      float resolvedLabelWidth = Math.min(this.resolvedLabelWidth(context), innerWidth);
      float controlX = innerX;
      if (this.labelNode != null && this.labelNode.isVisible()) {
         float labelHeight = Math.min(innerHeight, this.labelNode.preferredHeight(context, resolvedLabelWidth));
         float labelY = innerY + Math.max(0.0F, (innerHeight - labelHeight) / 2.0F);
         this.labelNode.setBounds(innerX, labelY, resolvedLabelWidth, labelHeight);
         controlX = innerX + resolvedLabelWidth + (this.controlNode != null && this.controlNode.isVisible() ? scaledGap : 0);
      }

      if (this.controlNode != null && this.controlNode.isVisible()) {
         float controlWidth = Math.max(0.0F, innerX + innerWidth - controlX);
         if (!this.controlNode.growX()) {
            controlWidth = Math.min(controlWidth, this.controlNode.preferredWidth(context));
         }

         if (this.alignControlEnd && !this.controlNode.growX()) {
            controlX = Math.max(controlX, innerX + innerWidth - controlWidth);
         }

         float controlHeight = Math.min(innerHeight, this.controlNode.preferredHeight(context, controlWidth));
         float controlY = innerY + Math.max(0.0F, (innerHeight - controlHeight) / 2.0F);
         this.controlNode.setBounds(controlX, controlY, controlWidth, controlHeight);
      }
   }
}
