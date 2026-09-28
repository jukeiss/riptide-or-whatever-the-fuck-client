package riptide.util;

public class RiptideWindowLayout {
   public int x;
   public int y;
   public int width;
   public int height;
   public boolean visible;
   public boolean collapsed;

   public RiptideWindowLayout() {
   }

   public RiptideWindowLayout(int x, int y, int width, int height, boolean visible, boolean collapsed) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.visible = visible;
      this.collapsed = collapsed;
   }
}
