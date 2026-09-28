package riptide.gui.screen;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.network.chat.Component;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.util.RiptideTheme;

public class RiptideStyledButton extends Button {
   private static final int MASK = -15594737;
   private final riptide.gui.vanillaui.components.Button.Tone tone;
   private final Supplier<String> dynamicLabel;
   private boolean toggled;

   public RiptideStyledButton(int x, int y, int width, int height, Component label, riptide.gui.vanillaui.components.Button.Tone tone, OnPress onPress) {
      this(x, y, width, height, label, tone, null, onPress);
   }

   public RiptideStyledButton(
      int x, int y, int width, int height, Component label, riptide.gui.vanillaui.components.Button.Tone tone, Supplier<String> dynamicLabel, OnPress onPress
   ) {
      super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
      this.tone = tone == null ? riptide.gui.vanillaui.components.Button.Tone.NORMAL : tone;
      this.dynamicLabel = dynamicLabel;
   }

   public RiptideStyledButton setToggled(boolean toggled) {
      this.toggled = toggled;
      return this;
   }

   protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      Font font = Minecraft.getInstance().font;
      UiBounds bounds = UiBounds.of(this.getX(), this.getY(), this.getWidth(), this.getHeight());
      UiRenderer.rect(graphics, bounds, RiptideTheme.recolor(-15594737, RiptideTheme.Channel.BUTTON));
      boolean hovered = this.active
         && mouseX >= this.getX()
         && mouseX < this.getX() + this.getWidth()
         && mouseY >= this.getY()
         && mouseY < this.getY() + this.getHeight();
      String text = this.dynamicLabel != null ? this.dynamicLabel.get() : this.getMessage().getString();
      riptide.gui.vanillaui.components.Button.render(
         UiContexts.overlay(graphics, font, mouseX, mouseY),
         bounds,
         text,
         this.toggled ? this.tone : riptide.gui.vanillaui.components.Button.Tone.NORMAL,
         hovered,
         this.toggled
      );
   }
}
