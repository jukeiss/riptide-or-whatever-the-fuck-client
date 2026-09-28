package riptide.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

public class RiptideGhostInventoryScreen extends InventoryScreen {
   public RiptideGhostInventoryScreen(Player player) {
      super(player);
   }

   public static boolean isOpen() {
      return Minecraft.getInstance().gui.screen() instanceof RiptideGhostInventoryScreen;
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
   }

   public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
   }

   public void extractCarriedItem(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
   }

   protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
   }

   protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
   }

   protected void extractSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
   }

   protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
   }

   public boolean keyPressed(KeyEvent event) {
      return true;
   }

   public boolean keyReleased(KeyEvent event) {
      return true;
   }

   public boolean charTyped(CharacterEvent event) {
      return true;
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      return true;
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      return true;
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      return true;
   }

   public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      return true;
   }

   public boolean shouldCloseOnEsc() {
      return false;
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void containerTick() {
   }

   public void triggerImmediateNarration(boolean onlyNarrateNew) {
   }
}
