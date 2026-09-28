package riptide.gui.vanillaui;

public record UiBounds(int x, int y, int width, int height) {
   public static UiBounds of(int x, int y, int width, int height) {
      return new UiBounds(x, y, Math.max(0, width), Math.max(0, height));
   }

   public int right() {
      return this.x + this.width;
   }

   public int bottom() {
      return this.y + this.height;
   }

   public boolean contains(int px, int py) {
      return px >= this.x && px < this.right() && py >= this.y && py < this.bottom();
   }

   public UiBounds inset(int amount) {
      return this.inset(amount, amount, amount, amount);
   }

   public UiBounds inset(int left, int top, int right, int bottom) {
      int nx = this.x + left;
      int ny = this.y + top;
      return of(nx, ny, this.width - left - right, this.height - top - bottom);
   }

   public UiBounds clampInside(int screenWidth, int screenHeight) {
      int w = Math.min(this.width, Math.max(0, screenWidth));
      int h = Math.min(this.height, Math.max(0, screenHeight));
      int nx = clamp(this.x, 0, Math.max(0, screenWidth - w));
      int ny = clamp(this.y, 0, Math.max(0, screenHeight - h));
      return of(nx, ny, w, h);
   }

   public UiBounds union(UiBounds other) {
      if (other == null) {
         return this;
      } else {
         int nx = Math.min(this.x, other.x);
         int ny = Math.min(this.y, other.y);
         return of(nx, ny, Math.max(this.right(), other.right()) - nx, Math.max(this.bottom(), other.bottom()) - ny);
      }
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }
}
