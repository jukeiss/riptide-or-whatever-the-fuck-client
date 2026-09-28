package riptide.gui.vanillaui.direct;

public record DirectUiInsets(int left, int top, int right, int bottom) {
   public static final DirectUiInsets NONE = new DirectUiInsets(0, 0, 0, 0);

   public static DirectUiInsets all(int value) {
      return new DirectUiInsets(value, value, value, value);
   }

   public static DirectUiInsets symmetric(int horizontal, int vertical) {
      return new DirectUiInsets(horizontal, vertical, horizontal, vertical);
   }

   public int horizontal() {
      return this.left + this.right;
   }

   public int vertical() {
      return this.top + this.bottom;
   }
}
