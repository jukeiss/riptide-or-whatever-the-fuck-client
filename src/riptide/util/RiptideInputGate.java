package riptide.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import riptide.gui.screen.RiptideModuleScreen;
import riptide.gui.vanillaui.components.CompactTextInput;

public final class RiptideInputGate {
   private static final Minecraft MC = Minecraft.getInstance();

   private RiptideInputGate() {
   }

   public static boolean canRunRiptideKeybinds() {
      RiptideConfig config = RiptideConfig.getGlobal();
      if (MC == null) {
         return false;
      } else if (CompactTextInput.anyFocused()) {
         return false;
      } else if (MC.gui.screen() == null) {
         return true;
      } else if (config == null || !config.keybindInsideGui) {
         return false;
      } else if (MC.gui.screen() instanceof ChatScreen || MC.gui.screen() instanceof InBedChatScreen) {
         return false;
      } else if (MC.gui.screen() instanceof AbstractSignEditScreen) {
         return false;
      } else if (MC.gui.screen() instanceof BookEditScreen || MC.gui.screen() instanceof BookSignScreen) {
         return false;
      } else if (isTypingTarget(MC.gui.screen().getFocused())) {
         return false;
      } else if (hasFocusedTextInput(MC.gui.screen(), 0)) {
         return false;
      } else if (RiptideOverlayManager.get().isAnyTextFieldFocused()) {
         return false;
      } else {
         return !RiptideLiteVariant.enabled() && MC.gui.screen() instanceof RiptideModuleScreen moduleScreen ? !moduleScreen.blocksGlobalKeybinds() : true;
      }
   }

   private static boolean hasFocusedTextInput(GuiEventListener listener, int depth) {
      if (listener == null || depth > 4) {
         return false;
      } else if (!(listener instanceof EditBox) && !(listener instanceof MultiLineEditBox)) {
         if (listener instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
               if (hasFocusedTextInput(child, depth + 1)) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return isTypingTarget(listener);
      }
   }

   private static boolean isTypingTarget(GuiEventListener listener) {
      if (!(listener instanceof EditBox editBox)) {
         return listener instanceof MultiLineEditBox multiLine ? multiLine.isFocused() : false;
      } else {
         return editBox.isFocused() && editBox.isVisible();
      }
   }
}
