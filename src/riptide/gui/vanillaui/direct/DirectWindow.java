package riptide.gui.vanillaui.direct;

import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.CompactWindow;
import riptide.gui.vanillaui.components.UiTone;

public class DirectWindow extends DirectUiContainer {
   private final DirectUiColumn content = new DirectUiColumn();
   private String title;
   private Identifier titleIcon;
   private boolean showBody = true;
   private boolean active = true;
   private boolean headerHovered = false;
   private boolean centerTitle = true;
   private boolean showCollapseControl = false;
   private boolean showCloseControl = false;
   private UiTone titleTone = UiTone.TITLE;
   private int titleLeftInset = 10;
   private int titleRightInset = 10;

   public DirectWindow(String title) {
      this.title = title == null ? "" : title;
      this.content.setGap(4).setPadding(DirectUiInsets.all(6));
      this.add(this.content);
   }

   public DirectUiColumn content() {
      return this.content;
   }

   public DirectWindow setTitle(String title) {
      String next = title == null ? "" : title;
      if (!this.title.equals(next)) {
         this.title = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectWindow setTitleIcon(Identifier titleIcon) {
      this.titleIcon = titleIcon;
      return this;
   }

   public DirectWindow setShowBody(boolean showBody) {
      if (this.showBody != showBody) {
         this.showBody = showBody;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectWindow restoreShowBody(boolean showBody) {
      if (this.showBody != showBody) {
         this.showBody = showBody;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectWindow syncShowBody(boolean showBody) {
      return this.restoreShowBody(showBody);
   }

   public DirectWindow setActive(boolean active) {
      this.active = active;
      return this;
   }

   public DirectWindow setHeaderHovered(boolean headerHovered) {
      this.headerHovered = headerHovered;
      return this;
   }

   public DirectWindow setCenterTitle(boolean centerTitle) {
      this.centerTitle = centerTitle;
      return this;
   }

   public DirectWindow setHeaderControls(boolean collapse, boolean close) {
      if (this.showCollapseControl != collapse || this.showCloseControl != close) {
         this.showCollapseControl = collapse;
         this.showCloseControl = close;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectWindow setTitleTone(UiTone titleTone) {
      UiTone next = titleTone == null ? UiTone.TITLE : titleTone;
      if (this.titleTone != next) {
         this.titleTone = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectWindow setTitleAreaInsets(int titleLeftInset, int titleRightInset) {
      int nextLeft = Math.max(0, titleLeftInset);
      int nextRight = Math.max(0, titleRightInset);
      if (this.titleLeftInset != nextLeft || this.titleRightInset != nextRight) {
         this.titleLeftInset = nextLeft;
         this.titleRightInset = nextRight;
         this.markLayoutDirty();
      }

      return this;
   }

   @Override
   public float preferredHeight(DirectRenderContext context, float availableWidth) {
      int headerH = Math.max(context.theme().headerHeight(), context.theme().lineHeight(this.titleTone, 4));
      float bodyHeight = this.showBody ? this.content.preferredHeight(context, availableWidth) : 0.0F;
      return headerH + bodyHeight;
   }

   @Override
   protected void layoutChildren(DirectRenderContext context) {
      int headerH = Math.max(context.theme().headerHeight(), context.theme().lineHeight(this.titleTone, 4));
      this.content.setVisible(this.showBody);
      this.content.setBounds(this.x, this.y + headerH, this.width, this.showBody ? Math.max(0.0F, this.height - headerH) : 0.0F);
   }

   @Override
   public void render(DirectRenderContext context) {
      if (this.visible) {
         DirectRenderContext drawContext = context;
         int drawX = Math.round(this.x);
         int drawY = Math.round(this.y);
         int drawW = Math.round(this.width);
         int drawH = Math.round(this.height);
         int headerH = Math.max(context.theme().headerHeight(), context.theme().lineHeight(this.titleTone, 4));
         CompactWindow.renderFrame(
            UiContexts.overlay(context.drawContext(), context.textRenderer(), Math.round(context.mouseX()), Math.round(context.mouseY())),
            UiBounds.of(drawX, drawY, drawW, drawH),
            this.title,
            !this.showBody,
            this.showCollapseControl,
            this.showCloseControl,
            this.headerHovered,
            this.active,
            Math.max(4, context.theme().scale(this.titleLeftInset)),
            Math.max(34, context.theme().scale(this.titleRightInset)),
            headerH
         );
         if (this.showBody) {
            int clipTop = Math.round(this.y + headerH);
            int clipBottom = Math.round(this.y + this.height);
            if (clipBottom > clipTop) {
               context.viewport().enableScissor(context.drawContext(), this.x, this.y + headerH, this.x + this.width, this.y + this.height);

               try {
                  super.render(drawContext);
               } finally {
                  context.viewport().disableScissor(context.drawContext());
               }
            }
         }

         if (!this.active) {
            UiRenderer.rect(context.drawContext(), UiBounds.of(drawX + 1, drawY + 1, Math.max(0, drawW - 2), Math.max(0, drawH - 2)), 603979776);
         }
      }
   }
}
