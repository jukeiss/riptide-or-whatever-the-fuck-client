package riptide.util;

public abstract class RiptideOverlayBase extends RiptideWindow implements IRiptideOverlay {
   private final String overlayId;
   protected int panelX;
   protected int panelY;
   protected int panelWidth;
   protected int panelHeight;
   protected boolean visible;
   protected boolean collapsed;
   private RiptideContextMenu<?> contextMenu;
   private int lastSeenMx;
   private int lastSeenMy;

   protected RiptideOverlayBase() {
      this(null, 0, 0);
   }

   protected RiptideOverlayBase(String overlayId, int initialWidth, int initialHeight) {
      this.overlayId = overlayId;
      this.panelWidth = initialWidth;
      this.panelHeight = initialHeight;
   }

   @Override
   public String getOverlayId() {
      return this.overlayId != null ? this.overlayId : this.getClass().getSimpleName();
   }

   @Override
   public boolean isVisible() {
      return this.visible;
   }

   @Override
   public void setVisible(boolean v) {
      this.visible = v;
      if (!v) {
         this.clearHiddenInteractionState();
      }

      this.saveLayout();
   }

   @Override
   public boolean isCollapsed() {
      return this.collapsed;
   }

   @Override
   public void setCollapsed(boolean c) {
      if (this.collapsed != c) {
         this.collapsed = c;
         if (c) {
            this.clearHiddenInteractionState();
         }

         this.saveLayout();
      }
   }

   @Override
   public RiptideWindowLayout getBounds() {
      return new RiptideWindowLayout(this.panelX, this.panelY, this.panelWidth, this.panelHeight, this.visible, this.collapsed);
   }

   @Override
   public void setBounds(RiptideWindowLayout bounds) {
      if (bounds != null) {
         RiptideWindowLayout c = this.clampToScreen(this, bounds);
         this.panelX = c.x;
         this.panelY = c.y;
         this.panelWidth = c.width;
         this.panelHeight = c.height;
         this.visible = c.visible;
         this.collapsed = c.collapsed;
      }
   }

   @Override
   public boolean isMouseOver(double mx, double my) {
      if (!this.visible) {
         return false;
      } else {
         int frameH = this.currentFrameHeight();
         boolean overPanel = mx >= this.panelX && mx <= this.panelX + this.panelWidth && my >= this.panelY && my <= this.panelY + frameH;
         return this.contextMenu != null && this.contextMenu.isMouseOver(mx, my) ? true : overPanel;
      }
   }

   @Override
   public boolean isOverDragBar(double mx, double my) {
      if (!this.visible) {
         return false;
      } else if (mx < this.panelX || mx > this.panelX + this.panelWidth) {
         return false;
      } else {
         return !(my < this.panelY) && !(my > this.panelY + 16) ? !this.isOverWindowControl(mx, my, this.getBounds()) : false;
      }
   }

   protected int currentFrameHeight() {
      return this.collapsed ? 16 : this.panelHeight;
   }

   protected void setContextMenu(RiptideContextMenu<?> menu) {
      this.contextMenu = menu;
   }

   protected RiptideContextMenu<?> contextMenu() {
      return this.contextMenu;
   }

   protected final void clearHiddenInteractionState() {
      this.clearTextFieldFocus();
      if (this.contextMenu != null) {
         this.contextMenu.close();
      }
   }

   protected int hoverBlocked(int mouse, int axis) {
      if (this.contextMenu != null && this.contextMenu.isOpen()) {
         int otherAxis = axis == 0 ? this.lastSeenMy : this.lastSeenMx;
         boolean covered = axis == 0 ? this.contextMenu.isMouseOver(mouse, otherAxis) : this.contextMenu.isMouseOver(otherAxis, mouse);
         return covered ? -10000 : mouse;
      } else {
         return mouse;
      }
   }

   protected final void recordMouse(int mx, int my) {
      this.lastSeenMx = mx;
      this.lastSeenMy = my;
   }

   protected int bodyMouseX(int mx, int my) {
      return this.contextMenu != null && this.contextMenu.isMouseOver(mx, my) ? -10000 : mx;
   }

   protected int bodyMouseY(int mx, int my) {
      return this.contextMenu != null && this.contextMenu.isMouseOver(mx, my) ? -10000 : my;
   }
}
