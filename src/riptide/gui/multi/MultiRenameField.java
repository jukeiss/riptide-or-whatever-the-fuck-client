package riptide.gui.multi;

public final class MultiRenameField {
   private static final int MAX = 50;
   private final StringBuilder text = new StringBuilder();
   private boolean focused;

   public boolean focused() {
      return this.focused;
   }

   public void focus() {
      this.focused = true;
   }

   public void blur() {
      this.focused = false;
   }

   public String text() {
      return this.text.toString();
   }

   public void set(String value) {
      this.text.setLength(0);
      if (value != null) {
         this.text.append(value.length() > 50 ? value.substring(0, 50) : value);
      }
   }

   public boolean charTyped(char c) {
      if (!this.focused) {
         return false;
      } else if (c >= ' ' && c != 127 && this.text.length() < 50) {
         this.text.append(c);
         return true;
      } else {
         return false;
      }
   }

   public boolean keyPressed(int keyCode) {
      if (!this.focused) {
         return false;
      } else {
         switch (keyCode) {
            case 256:
            case 257:
            case 335:
               this.focused = false;
               return true;
            case 259:
               if (this.text.length() > 0) {
                  this.text.deleteCharAt(this.text.length() - 1);
               }

               return true;
            default:
               return false;
         }
      }
   }
}
