package riptide.gui.vanillaui.components;

import riptide.gui.vanillaui.UiBounds;

public final class ScrollContainer {
   private ScrollContainer() {
   }

   public static int clampScroll(int scroll, int contentHeight, int viewHeight) {
      return Math.max(0, Math.min(scroll, Math.max(0, contentHeight - Math.max(0, viewHeight))));
   }

   public static int firstVisibleRow(int scroll, int rowHeight, int count) {
      return count > 0 && rowHeight > 0 ? Math.max(0, Math.min(count - 1, scroll / rowHeight)) : 0;
   }

   public static int lastVisibleRow(int scroll, int viewHeight, int rowHeight, int count) {
      return count > 0 && rowHeight > 0 ? Math.max(0, Math.min(count - 1, (scroll + Math.max(0, viewHeight) + rowHeight - 1) / rowHeight)) : -1;
   }

   public static boolean contains(UiBounds viewport, int mouseX, int mouseY) {
      return viewport != null && viewport.contains(mouseX, mouseY);
   }
}
