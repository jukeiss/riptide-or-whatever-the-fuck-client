package riptide.gui.vanillaui;

public interface UiComponent {
   UiBounds bounds();

   void setBounds(UiBounds var1);

   default UiBounds hitBounds() {
      return this.bounds();
   }

   default void render(UiContext context) {
   }

   default UiInputResult mouseClicked(int mouseX, int mouseY, int button) {
      return UiInputResult.IGNORED;
   }

   default UiInputResult mouseReleased(int mouseX, int mouseY, int button) {
      return UiInputResult.IGNORED;
   }

   default UiInputResult mouseDragged(int mouseX, int mouseY, int button, double deltaX, double deltaY) {
      return UiInputResult.IGNORED;
   }

   default UiInputResult mouseScrolled(int mouseX, int mouseY, double amount) {
      return UiInputResult.IGNORED;
   }

   default UiInputResult keyPressed(int key, int scanCode, int modifiers) {
      return UiInputResult.IGNORED;
   }

   default UiInputResult charTyped(char chr) {
      return UiInputResult.IGNORED;
   }
}
