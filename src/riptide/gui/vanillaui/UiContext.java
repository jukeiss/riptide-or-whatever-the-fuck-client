package riptide.gui.vanillaui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class UiContext {
   private final GuiGraphicsExtractor graphics;
   private final UiTheme theme;
   private final UiTextRenderer text;
   private final int screenWidth;
   private final int screenHeight;
   private final int mouseX;
   private final int mouseY;
   private final float delta;

   public UiContext(GuiGraphicsExtractor graphics, UiTheme theme, UiTextRenderer text, int screenWidth, int screenHeight, int mouseX, int mouseY, float delta) {
      this.graphics = graphics;
      this.theme = theme;
      this.text = text;
      this.screenWidth = screenWidth;
      this.screenHeight = screenHeight;
      this.mouseX = mouseX;
      this.mouseY = mouseY;
      this.delta = delta;
   }

   public GuiGraphicsExtractor graphics() {
      return this.graphics;
   }

   public UiTheme theme() {
      return this.theme;
   }

   public UiTextRenderer text() {
      return this.text;
   }

   public int screenWidth() {
      return this.screenWidth;
   }

   public int screenHeight() {
      return this.screenHeight;
   }

   public int mouseX() {
      return this.mouseX;
   }

   public int mouseY() {
      return this.mouseY;
   }

   public float delta() {
      return this.delta;
   }
}
