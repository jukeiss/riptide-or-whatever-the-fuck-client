package riptide.util;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import riptide.modules.PackHideState;

public final class RiptideInputClicker {
   private static final Minecraft MC = Minecraft.getInstance();
   private static boolean attackQueued;
   private static boolean useQueued;
   private static RiptideInputClicker.PacedUseOwner pacedUseQueued = RiptideInputClicker.PacedUseOwner.NONE;
   private static long scaffoldGenerationQueued;
   private static int hotbarSlotQueued = -1;
   private static boolean attackPressed;
   private static boolean usePressed;
   private static RiptideInputClicker.PacedUseOwner pacedUseActive = RiptideInputClicker.PacedUseOwner.NONE;
   private static RiptideInputClicker.PacedUseOwner pacedUseInProgress = RiptideInputClicker.PacedUseOwner.NONE;
   private static long scaffoldGenerationActive;
   private static long scaffoldGenerationInProgress;
   private static KeyMapping hotbarPressed;
   private static boolean attackHeld;
   private static boolean useHeld;

   private RiptideInputClicker() {
   }

   public static void queueAttackClick() {
      if (!PackHideState.isHardLocked()) {
         attackQueued = true;
      }
   }

   public static void queueUseClick() {
      if (!PackHideState.isHardLocked()) {
         useQueued = true;
      }
   }

   public static void queueScaffoldUseClick(long generation) {
      if (generation > 0L && !PackHideState.isHardLocked()) {
         scaffoldGenerationQueued = generation;
         queuePacedUseClick(RiptideInputClicker.PacedUseOwner.SCAFFOLD);
      }
   }

   public static void queueFastExpUseClick() {
      queuePacedUseClick(RiptideInputClicker.PacedUseOwner.FAST_EXP);
   }

   public static void queueFastBlockUseClick() {
      queuePacedUseClick(RiptideInputClicker.PacedUseOwner.FAST_BLOCK);
   }

   public static boolean beginFastExpUseClick() {
      return beginPacedUseClick(RiptideInputClicker.PacedUseOwner.FAST_EXP);
   }

   public static boolean beginFastBlockUseClick() {
      return beginPacedUseClick(RiptideInputClicker.PacedUseOwner.FAST_BLOCK);
   }

   public static boolean beginScaffoldUseClick(long generation) {
      if (generation <= 0L || scaffoldGenerationActive != generation) {
         return false;
      } else if (!beginPacedUseClick(RiptideInputClicker.PacedUseOwner.SCAFFOLD)) {
         return false;
      } else {
         scaffoldGenerationInProgress = generation;
         scaffoldGenerationActive = 0L;
         return true;
      }
   }

   public static boolean isScaffoldUseInProgress() {
      return pacedUseInProgress == RiptideInputClicker.PacedUseOwner.SCAFFOLD;
   }

   public static long scaffoldUseGenerationInProgress() {
      return isScaffoldUseInProgress() ? scaffoldGenerationInProgress : 0L;
   }

   public static void cancelScaffoldUseClick() {
      if (pacedUseQueued == RiptideInputClicker.PacedUseOwner.SCAFFOLD
         || pacedUseActive == RiptideInputClicker.PacedUseOwner.SCAFFOLD
         || pacedUseInProgress == RiptideInputClicker.PacedUseOwner.SCAFFOLD
         || scaffoldGenerationQueued != 0L
         || scaffoldGenerationActive != 0L
         || scaffoldGenerationInProgress != 0L) {
         cancelPacedUseClick(RiptideInputClicker.PacedUseOwner.SCAFFOLD);
         scaffoldGenerationQueued = 0L;
         scaffoldGenerationActive = 0L;
         scaffoldGenerationInProgress = 0L;
      }
   }

   public static boolean isFastExpUseInProgress() {
      return pacedUseInProgress == RiptideInputClicker.PacedUseOwner.FAST_EXP;
   }

   public static void cancelFastExpUseClick() {
      cancelPacedUseClick(RiptideInputClicker.PacedUseOwner.FAST_EXP);
   }

   public static void cancelFastBlockUseClick() {
      cancelPacedUseClick(RiptideInputClicker.PacedUseOwner.FAST_BLOCK);
   }

   private static void queuePacedUseClick(RiptideInputClicker.PacedUseOwner owner) {
      if (!PackHideState.isHardLocked() && owner != null && owner != RiptideInputClicker.PacedUseOwner.NONE) {
         pacedUseQueued = owner;
      }
   }

   private static boolean beginPacedUseClick(RiptideInputClicker.PacedUseOwner owner) {
      if (pacedUseActive != owner) {
         return false;
      } else {
         pacedUseActive = RiptideInputClicker.PacedUseOwner.NONE;
         pacedUseInProgress = owner;
         return true;
      }
   }

   private static void cancelPacedUseClick(RiptideInputClicker.PacedUseOwner owner) {
      if (pacedUseQueued == owner) {
         pacedUseQueued = RiptideInputClicker.PacedUseOwner.NONE;
      }

      if (pacedUseActive == owner && MC != null && MC.options != null) {
         simulate(MC.options.keyUse, false);
         usePressed = false;
      }

      if (pacedUseActive == owner) {
         pacedUseActive = RiptideInputClicker.PacedUseOwner.NONE;
      }

      if (pacedUseInProgress == owner) {
         pacedUseInProgress = RiptideInputClicker.PacedUseOwner.NONE;
      }

      if (owner == RiptideInputClicker.PacedUseOwner.SCAFFOLD) {
         scaffoldGenerationQueued = 0L;
         scaffoldGenerationActive = 0L;
         scaffoldGenerationInProgress = 0L;
      }

      if (MC != null && MC.options != null && MC.options.keyUse != null) {
         RiptideKeyMappingBridge.of(MC.options.keyUse).riptide$resetPressedState();
      }
   }

   public static void setAttackHeld(boolean held) {
      attackHeld = applyHold(MC != null && MC.options != null ? MC.options.keyAttack : null, attackHeld, held);
   }

   public static void setUseHeld(boolean held) {
      useHeld = applyHold(MC != null && MC.options != null ? MC.options.keyUse : null, useHeld, held);
   }

   private static boolean applyHold(KeyMapping mapping, boolean current, boolean held) {
      if (mapping == null) {
         return false;
      } else if (held && !PackHideState.isHardLocked() && canProcessInput()) {
         if (!mapping.isDown()) {
            simulate(mapping, true);
         }

         return true;
      } else {
         if (current) {
            simulate(mapping, false);
            RiptideKeyMappingBridge.of(mapping).riptide$resetPressedState();
         }

         return false;
      }
   }

   private static void releaseHolds() {
      setAttackHeld(false);
      setUseHeld(false);
   }

   public static void queueHotbarSlot(int slot) {
      if (!PackHideState.isHardLocked()) {
         hotbarSlotQueued = Math.max(0, Math.min(8, slot));
      }
   }

   public static void beforeHandleKeybinds() {
      if (!canProcessInput()) {
         standDown();
      } else {
         boolean combatOwnsUse = RiptideCombatClicker.ownsKeyUseThisTick();
         boolean combatOwnsAttack = RiptideCombatClicker.ownsKeyAttackThisTick();
         if (attackQueued && !combatOwnsAttack) {
            simulate(MC.options.keyAttack, true);
            attackPressed = true;
         }

         if (!combatOwnsAttack) {
            attackQueued = false;
         }

         if (!combatOwnsUse && (useQueued || pacedUseQueued != RiptideInputClicker.PacedUseOwner.NONE)) {
            boolean plainWinsTick = useQueued && pacedUseQueued != RiptideInputClicker.PacedUseOwner.NONE;
            pacedUseActive = plainWinsTick ? RiptideInputClicker.PacedUseOwner.NONE : pacedUseQueued;
            scaffoldGenerationActive = pacedUseActive == RiptideInputClicker.PacedUseOwner.SCAFFOLD ? scaffoldGenerationQueued : 0L;
            if (pacedUseActive != RiptideInputClicker.PacedUseOwner.NONE) {
               while (MC.options.keyUse.consumeClick()) {
               }
            }

            simulate(MC.options.keyUse, true);
            usePressed = true;
         }

         if (hotbarSlotQueued >= 0 && MC.options.keyHotbarSlots != null && hotbarSlotQueued < MC.options.keyHotbarSlots.length) {
            hotbarPressed = MC.options.keyHotbarSlots[hotbarSlotQueued];
            simulate(hotbarPressed, true);
         }

         if (!combatOwnsUse) {
            useQueued = false;
            if (pacedUseActive != RiptideInputClicker.PacedUseOwner.NONE || pacedUseQueued == RiptideInputClicker.PacedUseOwner.NONE) {
               pacedUseQueued = RiptideInputClicker.PacedUseOwner.NONE;
               scaffoldGenerationQueued = 0L;
            }
         }

         hotbarSlotQueued = -1;
      }
   }

   public static void onClientTickStart() {
      if (!canProcessInput()) {
         standDown();
      }
   }

   public static void afterHandleKeybinds() {
      if (MC != null && MC.options != null) {
         if (attackPressed) {
            simulate(MC.options.keyAttack, false);
            attackPressed = false;
         }

         if (usePressed) {
            simulate(MC.options.keyUse, false);
            usePressed = false;
         }

         pacedUseActive = RiptideInputClicker.PacedUseOwner.NONE;
         pacedUseInProgress = RiptideInputClicker.PacedUseOwner.NONE;
         scaffoldGenerationActive = 0L;
         scaffoldGenerationInProgress = 0L;
         if (hotbarPressed != null) {
            simulate(hotbarPressed, false);
            hotbarPressed = null;
         }
      } else {
         attackPressed = false;
         usePressed = false;
         pacedUseActive = RiptideInputClicker.PacedUseOwner.NONE;
         pacedUseInProgress = RiptideInputClicker.PacedUseOwner.NONE;
         scaffoldGenerationActive = 0L;
         scaffoldGenerationInProgress = 0L;
      }
   }

   private static void standDown() {
      if (physicalClicksAreStale()) {
         clear();
      } else {
         releaseOwnedInput();
      }
   }

   private static boolean physicalClicksAreStale() {
      return MC == null
         || MC.player == null
         || MC.level == null
         || MC.options == null
         || MC.getWindow() == null
         || MC.gui.screen() != null
         || MC.gui.overlay() != null;
   }

   public static void releaseOwnedInput() {
      attackQueued = false;
      useQueued = false;
      pacedUseQueued = RiptideInputClicker.PacedUseOwner.NONE;
      scaffoldGenerationQueued = 0L;
      scaffoldGenerationActive = 0L;
      scaffoldGenerationInProgress = 0L;
      hotbarSlotQueued = -1;
      afterHandleKeybinds();
      releaseHolds();
   }

   public static void clear() {
      attackQueued = false;
      useQueued = false;
      pacedUseQueued = RiptideInputClicker.PacedUseOwner.NONE;
      scaffoldGenerationQueued = 0L;
      scaffoldGenerationActive = 0L;
      scaffoldGenerationInProgress = 0L;
      hotbarSlotQueued = -1;
      afterHandleKeybinds();
      releaseHolds();
      drainStalePhysicalClicks();
   }

   private static void drainStalePhysicalClicks() {
      if (MC != null && MC.options != null) {
         drainClick(MC.options.keyUse);
         drainClick(MC.options.keyAttack);
      }
   }

   private static void drainClick(KeyMapping mapping) {
      if (mapping != null) {
         while (mapping.consumeClick()) {
         }

         RiptideKeyMappingBridge.of(mapping).riptide$resetPressedState();
      }
   }

   private static boolean canProcessInput() {
      return MC != null
         && MC.player != null
         && MC.level != null
         && MC.options != null
         && MC.getWindow() != null
         && MC.gui.screen() == null
         && MC.gui.overlay() == null
         && !PackHideState.isActive();
   }

   private static void simulate(KeyMapping mapping, boolean pressed) {
      if (mapping != null) {
         RiptideKeyMappingBridge.of(mapping).riptide$simulatePress(pressed);
      }
   }

   private static enum PacedUseOwner {
      NONE,
      FAST_BLOCK,
      FAST_EXP,
      SCAFFOLD;
   }
}
