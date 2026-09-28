package riptide.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.CompactOverlayWindow;
import riptide.gui.vanillaui.components.OverlayTopBar;

public abstract class RiptideWindow {
   protected static final int HEADER_HEIGHT = 16;
   protected static final int RESIZE_HANDLE = 10;

   public static int sharedHeaderHeight() {
      return 16;
   }

   protected RiptideWindowLayout clampToScreen(IRiptideOverlay overlay) {
      return this.clampToScreen(overlay, overlay.getBounds());
   }

   protected RiptideWindowLayout clampToScreen(IRiptideOverlay overlay, RiptideWindowLayout bounds) {
      Minecraft mc = Minecraft.getInstance();
      return mc != null && mc.getWindow() != null && bounds != null
         ? clampToScreenSize(
            bounds, overlay.getMinWidth(), overlay.getMinHeight(), RiptideUiScale.getVirtualScreenWidth(), RiptideUiScale.getVirtualScreenHeight()
         )
         : bounds;
   }

   static RiptideWindowLayout clampToScreenSize(RiptideWindowLayout bounds, int overlayMinWidth, int overlayMinHeight, int screenWidth, int screenHeight) {
      if (bounds == null) {
         return null;
      } else if (screenWidth > 0 && screenHeight > 0) {
         int safeMargin = 4;
         int availableWidth = Math.max(1, screenWidth - safeMargin * 2);
         int availableHeight = Math.max(16, screenHeight - safeMargin * 2);
         int minWidth = Math.min(overlayMinWidth, availableWidth);
         int minHeight = Math.min(overlayMinHeight, availableHeight);
         int width = Math.max(minWidth, Math.min(bounds.width, availableWidth));
         int height = Math.max(minHeight, Math.min(bounds.height, availableHeight));
         int renderedHeight = bounds.collapsed ? 16 : height;
         int x = Math.max(safeMargin, Math.min(bounds.x, Math.max(safeMargin, screenWidth - safeMargin - width)));
         int y = Math.max(safeMargin, Math.min(bounds.y, Math.max(safeMargin, screenHeight - safeMargin - renderedHeight)));
         return new RiptideWindowLayout(x, y, width, height, bounds.visible, bounds.collapsed);
      } else {
         return bounds;
      }
   }

   protected void renderWindowFrame(
      GuiGraphicsExtractor context, int mouseX, int mouseY, RiptideWindowLayout bounds, String title, boolean collapsed, boolean activeDrag
   ) {
      boolean active = activeDrag || this.isWindowActive();
      int frameHeight = this.getRenderedFrameHeight(bounds, collapsed);
      UiContext ui = UiContexts.overlay(context, Minecraft.getInstance().font, mouseX, mouseY);
      CompactOverlayWindow.render(
         ui, UiBounds.of(bounds.x, bounds.y, bounds.width, frameHeight), 16, title, collapsed, active, mouseY >= bounds.y && mouseY < bounds.y + 16
      );
   }

   protected boolean beginWindowBodyClip(GuiGraphicsExtractor context, RiptideWindowLayout bounds, boolean collapsed) {
      int frameHeight = this.getRenderedFrameHeight(bounds, collapsed);
      return !collapsed && frameHeight > 17
         ? CompactOverlayWindow.beginBodyClip(context, UiBounds.of(bounds.x, bounds.y, bounds.width, frameHeight), 16, collapsed)
         : false;
   }

   protected void endWindowBodyClip(GuiGraphicsExtractor context, boolean clipped) {
      CompactOverlayWindow.endBodyClip(context, clipped);
   }

   protected void renderWindowInactiveOverlay(GuiGraphicsExtractor context, RiptideWindowLayout bounds, boolean collapsed, boolean activeDrag) {
      int frameHeight = this.getRenderedFrameHeight(bounds, collapsed);
      if (!activeDrag && !this.isWindowActive()) {
         UiRenderer.rect(context, UiBounds.of(bounds.x + 1, bounds.y + 1, Math.max(0, bounds.width - 2), Math.max(0, frameHeight - 2)), 603979776);
      }
   }

   protected int alignViewportHeight(int innerHeight, int rowStep) {
      int safeInnerHeight = Math.max(0, innerHeight);
      int safeRowStep = Math.max(1, rowStep);
      if (safeInnerHeight != 0 && safeRowStep > 1) {
         int aligned = safeInnerHeight / safeRowStep * safeRowStep;
         return aligned > 0 ? aligned : Math.min(safeInnerHeight, safeRowStep);
      } else {
         return safeInnerHeight;
      }
   }

   protected int quantizeScrollOffset(int offset, int stepSize, int maxScroll) {
      int clampedMax = Math.max(0, maxScroll);
      int clampedOffset = Math.max(0, Math.min(offset, clampedMax));
      int safeStepSize = Math.max(1, stepSize);
      if (safeStepSize <= 1) {
         return clampedOffset;
      } else {
         int quantized = clampedOffset / safeStepSize * safeStepSize;
         if (quantized > clampedMax) {
            quantized = clampedMax / safeStepSize * safeStepSize;
         }

         return Math.max(0, Math.min(quantized, clampedMax));
      }
   }

   protected int getRenderedFrameHeight(RiptideWindowLayout bounds, boolean collapsed) {
      if (bounds == null) {
         return 16;
      } else {
         return collapsed ? 16 : Math.max(16, bounds.height);
      }
   }

   protected boolean isOverCloseButton(double mouseX, double mouseY, RiptideWindowLayout bounds) {
      return this.topBarCloseBounds(bounds).contains((int)mouseX, (int)mouseY);
   }

   protected boolean isOverCollapseButton(double mouseX, double mouseY, RiptideWindowLayout bounds) {
      return this.shouldUseSharedHeaderClickCollapse() ? false : this.topBarCollapseBounds(bounds).contains((int)mouseX, (int)mouseY);
   }

   protected boolean isOverWindowControl(double mouseX, double mouseY, RiptideWindowLayout bounds) {
      return this.isOverCloseButton(mouseX, mouseY, bounds) || this.isOverCollapseButton(mouseX, mouseY, bounds);
   }

   private boolean isWindowActive() {
      return !(this instanceof IRiptideOverlay overlay)
         ? true
         : RiptideOverlayManager.get().isFocusedOverlay(overlay) || RiptideOverlayManager.get().isTopOverlay(overlay);
   }

   private UiBounds topBarBounds(RiptideWindowLayout bounds) {
      return UiBounds.of(bounds.x, bounds.y, bounds.width, 16);
   }

   private UiBounds topBarCloseBounds(RiptideWindowLayout bounds) {
      return OverlayTopBar.closeButton(this.topBarBounds(bounds), 16);
   }

   private UiBounds topBarCollapseBounds(RiptideWindowLayout bounds) {
      return OverlayTopBar.collapseButton(this.topBarBounds(bounds), 16);
   }

   private boolean shouldUseSharedHeaderClickCollapse() {
      return this instanceof IRiptideOverlay overlay && overlay.usesSharedHeaderClickCollapse();
   }
}
