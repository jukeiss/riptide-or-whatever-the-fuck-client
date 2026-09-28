package riptide.gui.vanillaui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;

public final class ScreenButton {
   private final UiBounds bounds;
   private final Component label;
   private final Runnable action;
   private final Button.Tone tone;
   private boolean active = true;

   public ScreenButton(int x, int y, int width, int height, Component label, Button.Tone tone, Runnable action) {
      this.bounds = UiBounds.of(x, y, width, height);
      this.label = (Component)(label == null ? Component.empty() : label);
      this.tone = tone == null ? Button.Tone.NORMAL : tone;
      this.action = action;
   }

   public ScreenButton setActive(boolean active) {
      this.active = active;
      return this;
   }

   public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
      Button.render(
         UiContexts.overlay(graphics, font, mouseX, mouseY),
         this.bounds,
         this.label.getString(),
         this.tone,
         this.active && this.bounds.contains(mouseX, mouseY),
         false
      );
   }

   public boolean click(double mouseX, double mouseY, int mouseButton) {
      if (this.active && mouseButton == 0 && this.bounds.contains((int)mouseX, (int)mouseY)) {
         if (this.action != null) {
            this.action.run();
         }

         return true;
      } else {
         return false;
      }
   }
}
