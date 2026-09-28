package riptide.util;

public interface RiptideSpecialGuiActions {
   void riptide$closeWithPacket();

   void riptide$closeWithoutPacket();

   void riptide$desync();

   default void riptide$closeWithPacket(boolean notify) {
      this.riptide$closeWithPacket();
   }

   default void riptide$closeWithoutPacket(boolean notify) {
      this.riptide$closeWithoutPacket();
   }

   default void riptide$desync(boolean notify) {
      this.riptide$desync();
   }
}
