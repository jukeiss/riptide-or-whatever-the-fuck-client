package riptide.gui.vanillaui.direct;

import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.components.CompactWindow;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;

public class DirectAccordionSection extends DirectUiContainer {
   private final DirectUiColumn content = new DirectUiColumn();
   private String title;
   private boolean expanded = true;
   private int headerHeight = 18;
   private int contentTopGap = 2;

   public DirectAccordionSection(String title) {
      this.title = title == null ? "" : title;
      this.content.setPadding(DirectUiInsets.NONE).setGap(2);
      this.add(this.content);
   }

   public DirectUiColumn content() {
      return this.content;
   }

   public DirectAccordionSection setTitle(String title) {
      String next = title == null ? "" : title;
      if (!this.title.equals(next)) {
         this.title = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectAccordionSection setExpanded(boolean expanded) {
      if (this.expanded != expanded) {
         this.expanded = expanded;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectAccordionSection syncExpanded(boolean expanded) {
      if (this.expanded != expanded) {
         this.expanded = expanded;
         this.markLayoutDirty();
      }

      return this;
   }

   public boolean isExpanded() {
      return this.expanded;
   }

   public DirectAccordionSection setHeaderHeight(int headerHeight) {
      int next = Math.max(12, headerHeight);
      if (this.headerHeight != next) {
         this.headerHeight = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectAccordionSection setContentTopGap(int contentTopGap) {
      int next = Math.max(0, contentTopGap);
      if (this.contentTopGap != next) {
         this.contentTopGap = next;
         this.markLayoutDirty();
      }

      return this;
   }

   @Override
   public float preferredWidth(DirectRenderContext context) {
      return Math.max(
         (float)(
            UiText.width(context.textRenderer(), this.title, context.theme().fontFor(UiTone.LABEL), context.theme().color(UiTone.LABEL))
               + context.theme().scale(18)
         ),
         this.content.preferredWidth(context)
      );
   }

   @Override
   public float preferredHeight(DirectRenderContext context, float availableWidth) {
      int scaledHeaderHeight = Math.max(context.theme().scale(this.headerHeight), context.theme().lineHeight(UiTone.LABEL, 4));
      if (!this.expanded) {
         return scaledHeaderHeight;
      } else {
         int scaledContentTopGap = context.theme().scale(this.contentTopGap);
         float bodyHeight = scaledContentTopGap + this.content.preferredHeight(context, availableWidth);
         return scaledHeaderHeight + bodyHeight;
      }
   }

   @Override
   protected void layoutChildren(DirectRenderContext context) {
      int scaledHeaderHeight = Math.max(context.theme().scale(this.headerHeight), context.theme().lineHeight(UiTone.LABEL, 4));
      int scaledContentTopGap = context.theme().scale(this.contentTopGap);
      float contentY = this.y + scaledHeaderHeight + (this.expanded ? scaledContentTopGap : 0.0F);
      float contentH = this.expanded ? Math.max(0.0F, this.height - scaledHeaderHeight - scaledContentTopGap) : 0.0F;
      this.content.setVisible(this.expanded);
      this.content.setBounds(this.x, contentY, this.width, contentH);
   }

   @Override
   public void render(DirectRenderContext context) {
      if (this.visible) {
         int drawX = Math.round(this.x);
         int drawY = Math.round(this.y);
         int drawW = Math.round(this.width);
         int drawHeaderH = Math.max(context.theme().scale(this.headerHeight), context.theme().lineHeight(UiTone.LABEL, 4));
         boolean hovered = context.mouseX() >= this.x
            && context.mouseX() <= this.x + this.width
            && context.mouseY() >= this.y
            && context.mouseY() <= this.y + drawHeaderH;
         CompactWindow.renderFrame(
            UiContexts.overlay(context.drawContext(), context.textRenderer(), Math.round(context.mouseX()), Math.round(context.mouseY())),
            UiBounds.of(drawX, drawY, drawW, Math.round(this.height)),
            this.title,
            !this.expanded,
            true,
            false,
            hovered,
            true,
            4,
            4,
            drawHeaderH
         );
         this.renderBody(context, drawHeaderH);
      }
   }

   private void renderBody(DirectRenderContext context, int drawHeaderH) {
      if (this.expanded) {
         int clipTop = Math.round(this.y + drawHeaderH);
         int clipBottom = Math.round(this.y + this.height);
         if (clipBottom > clipTop) {
            context.viewport().enableScissor(context.drawContext(), this.x, this.y + drawHeaderH, this.x + this.width, this.y + this.height);

            try {
               super.render(context);
            } finally {
               context.viewport().disableScissor(context.drawContext());
            }
         }
      }
   }

   @Override
   public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
      int scaledHeaderHeight = Math.max(context.theme().scale(this.headerHeight), context.theme().lineHeight(UiTone.LABEL, 4));
      if (button == 0 && mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY <= this.y + scaledHeaderHeight) {
         this.expanded = !this.expanded;
         this.markLayoutDirty();
         return true;
      } else {
         return this.expanded && super.mouseClicked(context, mouseX, mouseY, button);
      }
   }
}
