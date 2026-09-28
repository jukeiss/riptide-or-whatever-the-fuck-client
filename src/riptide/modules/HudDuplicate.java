package riptide.modules;

import riptide.util.RiptideHudManager;

/**
 * Riptide draws HUD text through two independent systems: the draggable elements in
 * RiptideHudManager (several of which are on by default) and the fixed-corner panels of
 * the HUD suite. Several of them show the same thing - keystrokes, the module list, CPS,
 * armour, potions, FPS, coordinates - so turning a suite panel on can put a second copy
 * on screen that the HUD editor cannot move.
 *
 * This reports whether the draggable element of a given id is already drawing, so a suite
 * panel can stand down instead of doubling up.
 */
public final class HudDuplicate {
   private HudDuplicate() {
   }

   /** True when the draggable HUD element with this id is enabled and drawing. */
   public static boolean shows(String elementId) {
      try {
         return RiptideHudManager.state(elementId).enabled;
      } catch (Throwable var2) {
         return false;
      }
   }

   /** True when {@code module} should skip drawing because the draggable element covers it. */
   public static boolean suppresses(Module module, String elementId) {
      return module != null && module.bool("hide-dup") && shows(elementId);
   }
}
