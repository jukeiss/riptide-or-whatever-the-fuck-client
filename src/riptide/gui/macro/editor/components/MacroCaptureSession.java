package riptide.gui.macro.editor.components;

public final class MacroCaptureSession {
   private String itemSlotKey;

   public String itemSlotKey() {
      return this.itemSlotKey;
   }

   public boolean hasItemSlotCapture() {
      return this.itemSlotKey != null;
   }

   public boolean isItemSlotCapture(String key) {
      return key != null && key.equals(this.itemSlotKey);
   }

   public void startItemSlotCapture(String key, Runnable beginCapture) {
      if (key != null && !key.isBlank()) {
         this.itemSlotKey = key;
         if (beginCapture != null) {
            beginCapture.run();
         }
      }
   }

   public boolean stopItemSlotCapture(Runnable endCapture) {
      if (this.itemSlotKey == null) {
         return false;
      } else {
         this.itemSlotKey = null;
         if (endCapture != null) {
            endCapture.run();
         }

         return true;
      }
   }

   public void toggleItemSlotCapture(String key, Runnable beginCapture, Runnable endCapture) {
      if (this.isItemSlotCapture(key)) {
         this.stopItemSlotCapture(endCapture);
      } else {
         this.startItemSlotCapture(key, beginCapture);
      }
   }

   public void clearItemSlotCapture() {
      this.itemSlotKey = null;
   }
}
