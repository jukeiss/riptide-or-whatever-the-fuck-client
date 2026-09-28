package riptide.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface IRiptideOverlay {
   void render(GuiGraphicsExtractor var1, int var2, int var3, float var4);

   boolean mouseClicked(double var1, double var3, int var5);

   boolean mouseReleased(double var1, double var3, int var5);

   boolean mouseDragged(double var1, double var3, int var5, double var6, double var8);

   boolean mouseScrolled(double var1, double var3, double var5);

   boolean keyPressed(int var1, int var2, int var3);

   boolean charTyped(char var1, int var2);

   boolean isVisible();

   void setVisible(boolean var1);

   boolean isMouseOver(double var1, double var3);

   boolean isOverDragBar(double var1, double var3);

   default int getZLevel() {
      return 0;
   }

   default boolean isCollapsed() {
      return false;
   }

   default void setCollapsed(boolean collapsed) {
   }

   default void toggleCollapsed() {
      this.setCollapsed(!this.isCollapsed());
   }

   default boolean hasTextFieldFocused() {
      return false;
   }

   default void clearTextFieldFocus() {
   }

   default boolean wantsKeyboardCapture() {
      return false;
   }

   default String getOverlayId() {
      return this.getClass().getSimpleName();
   }

   default IRiptideOverlay.OverlayScope getDefaultOverlayScope() {
      return IRiptideOverlay.OverlayScope.HOST_SCREEN;
   }

   default boolean persistsAcrossScreenClose() {
      return false;
   }

   default RiptideWindowLayout getBounds() {
      return new RiptideWindowLayout(0, 0, this.getMinWidth(), this.getMinHeight(), this.isVisible(), this.isCollapsed());
   }

   default void setBounds(RiptideWindowLayout bounds) {
   }

   default int getMinWidth() {
      return 240;
   }

   default int getMinHeight() {
      return 140;
   }

   default boolean isOverResizeHandle(double mouseX, double mouseY) {
      return false;
   }

   default boolean usesSharedHeaderClickCollapse() {
      return false;
   }

   default void saveLayout() {
      RiptideSharedState.get().setWindowLayout(this.getOverlayId(), this.getBounds());
   }

   default void restoreLayout() {
      RiptideWindowLayout layout = RiptideSharedState.get().getWindowLayout(this.getOverlayId());
      if (layout != null) {
         this.setBounds(layout);
      }
   }

   public static enum OverlayScope {
      HOST_SCREEN,
      MODULE_MENU,
      CONTAINER_GUI,
      BACKGROUND_STATUS;
   }
}
