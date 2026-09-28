package riptide.util;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import net.minecraft.client.input.KeyEvent;
import riptide.modules.AutoTotemModule;
import riptide.modules.ModuleRegistry;
import riptide.modules.RiptideModule;
import riptide.util.macro.MacroExecutor;

public final class RiptideInventoryMoveHelper {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final Set<KeyMapping> OWNED_KEYS = Collections.newSetFromMap(new IdentityHashMap<>());

   private RiptideInventoryMoveHelper() {
   }

   public static boolean handleKeyEvent(KeyEvent input, boolean pressed) {
      if (input == null) {
         return false;
      } else if (AutoTotemModule.operationActive()) {
         releaseMovementKeysIfSafe();
         return false;
      } else {
         RiptideModule module = RiptideModule.get();
         if (module == null || !module.isActive() || !module.isInventoryMoveEnabled()) {
            releaseMovementKeysIfSafe();
            return false;
         } else if (MacroExecutor.isControllingInput()) {
            releaseMovementKeysIfSafe();
            return false;
         } else if (!shouldHandleCurrentScreen()) {
            releaseMovementKeysIfSafe();
            return false;
         } else if (RiptideOverlayManager.get().isAnyTextFieldFocused()) {
            releaseMovementKeysIfSafe();
            return false;
         } else if (MC != null && MC.options != null) {
            boolean handled = false;
            handled |= pass(MC.options.keyUp, input, pressed);
            handled |= pass(MC.options.keyDown, input, pressed);
            handled |= pass(MC.options.keyLeft, input, pressed);
            handled |= pass(MC.options.keyRight, input, pressed);
            handled |= pass(MC.options.keyJump, input, pressed);
            handled |= pass(MC.options.keyShift, input, pressed);
            return handled | pass(MC.options.keySprint, input, pressed);
         } else {
            releaseMovementKeysIfSafe();
            return false;
         }
      }
   }

   public static void syncHeldMovementKeysIfSafe() {
      if (canHandleMovementKeys()) {
         sync(MC.options.keyUp);
         sync(MC.options.keyDown);
         sync(MC.options.keyLeft);
         sync(MC.options.keyRight);
         sync(MC.options.keyJump);
         sync(MC.options.keyShift);
         sync(MC.options.keySprint);
      }
   }

   public static void releaseMovementKeysIfSafe() {
      if (MC != null && MC.options != null) {
         releaseOwned(MC.options.keyUp);
         releaseOwned(MC.options.keyDown);
         releaseOwned(MC.options.keyLeft);
         releaseOwned(MC.options.keyRight);
         releaseOwned(MC.options.keyJump);
         releaseOwned(MC.options.keyShift);
         releaseOwned(MC.options.keySprint);
      }
   }

   public static void resyncMovementKeysAfterPov() {
      if (MC != null && MC.options != null) {
         if (AutoTotemModule.operationActive()) {
            releaseMovementKeysIfSafe();
         } else if (!RiptideOverlayManager.get().isAnyTextFieldFocused()
            && !(MC.gui.screen() instanceof ChatScreen)
            && !(MC.gui.screen() instanceof AbstractSignEditScreen)
            && !(MC.gui.screen() instanceof BookEditScreen)
            && (MC.gui.screen() == null || shouldHandleCurrentScreen())) {
            sync(MC.options.keyUp);
            sync(MC.options.keyDown);
            sync(MC.options.keyLeft);
            sync(MC.options.keyRight);
            sync(MC.options.keyJump);
            sync(MC.options.keyShift);
            sync(MC.options.keySprint);
         } else {
            releaseMovementKeysIfSafe();
         }
      }
   }

   private static boolean pass(KeyMapping binding, KeyEvent input, boolean pressed) {
      if (binding != null && binding.matches(input)) {
         binding.setDown(pressed);
         if (pressed) {
            OWNED_KEYS.add(binding);
         } else {
            OWNED_KEYS.remove(binding);
         }

         return true;
      } else {
         return false;
      }
   }

   private static void sync(KeyMapping binding) {
      if (binding != null) {
         boolean physical = RiptideKeyMappingBridge.of(binding).riptide$isActuallyDown();
         boolean pressed = physical || binding == MC.options.keyShift && ModuleRegistry.sneakHoldsShift();
         binding.setDown(pressed);
         if (physical) {
            OWNED_KEYS.add(binding);
         } else {
            OWNED_KEYS.remove(binding);
         }
      }
   }

   private static void releaseOwned(KeyMapping binding) {
      if (binding != null) {
         if (OWNED_KEYS.remove(binding)) {
            binding.setDown(false);
         }
      }
   }

   private static boolean shouldHandleCurrentScreen() {
      if (MC != null && MC.gui.screen() != null) {
         Screen screen = MC.gui.screen();
         if (!(screen instanceof AbstractContainerScreen)) {
            return false;
         } else if (screen instanceof AnvilScreen) {
            return false;
         } else if (screen instanceof AbstractCommandBlockEditScreen) {
            return false;
         } else {
            return screen instanceof StructureBlockEditScreen ? false : !(screen.getFocused() instanceof EditBox);
         }
      } else {
         return false;
      }
   }

   private static boolean canHandleMovementKeys() {
      if (AutoTotemModule.operationActive()) {
         releaseMovementKeysIfSafe();
         return false;
      } else {
         RiptideModule module = RiptideModule.get();
         if (module == null || !module.isActive() || !module.isInventoryMoveEnabled()) {
            releaseMovementKeysIfSafe();
            return false;
         } else if (MacroExecutor.isControllingInput()) {
            releaseMovementKeysIfSafe();
            return false;
         } else if (!shouldHandleCurrentScreen()) {
            releaseMovementKeysIfSafe();
            return false;
         } else if (RiptideOverlayManager.get().isAnyTextFieldFocused()) {
            releaseMovementKeysIfSafe();
            return false;
         } else if (MC != null && MC.options != null) {
            return true;
         } else {
            releaseMovementKeysIfSafe();
            return false;
         }
      }
   }
}
