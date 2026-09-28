package riptide.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class RiptideIconButton extends Button {
   private final Identifier icon;
   private final int textureWidth;
   private final int textureHeight;
   private final int iconSize;

   public RiptideIconButton(int x, int y, int width, int height, Component label, Identifier icon, int textureSize, int iconSize, OnPress onPress) {
      this(x, y, width, height, label, icon, textureSize, textureSize, iconSize, onPress);
   }

   public RiptideIconButton(
      int x, int y, int width, int height, Component label, Identifier icon, int textureWidth, int textureHeight, int iconSize, OnPress onPress
   ) {
      super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
      this.icon = icon;
      this.textureWidth = Math.max(1, textureWidth);
      this.textureHeight = Math.max(1, textureHeight);
      this.iconSize = Math.max(1, iconSize);
   }

   protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      Font font = Minecraft.getInstance().font;
      int color = this.active ? -1 : -7703436;
      if (this.icon == null) {
         int textW = font.width(this.getMessage());
         graphics.text(font, this.getMessage(), this.getX() + (this.getWidth() - textW) / 2, this.textY(font), color);
      } else {
         int drawSize = Math.min(this.iconSize, Math.max(1, Math.min(this.getWidth() - 4, this.getHeight() - 4)));
         boolean hasRoomForText = this.getWidth() >= drawSize + font.width(this.getMessage()) + 18;
         int iconX = hasRoomForText ? this.getX() + 3 : this.getX() + (this.getWidth() - drawSize) / 2;
         int iconY = this.getY() + (this.getHeight() - drawSize) / 2;
         graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            this.icon,
            iconX,
            iconY,
            0.0F,
            0.0F,
            drawSize,
            drawSize,
            this.textureWidth,
            this.textureHeight,
            this.textureWidth,
            this.textureHeight,
            color
         );
         if (hasRoomForText) {
            int textX = iconX + drawSize + 4;
            graphics.text(font, this.getMessage(), textX, this.textY(font), color);
         }
      }
   }

   private int textY(Font font) {
      int spareHeight = Math.max(0, this.getHeight() - 9);
      return this.getY() + (spareHeight + 1) / 2 + 1;
   }
}
