package riptide.util.macro;

import riptide.util.RiptideMacro;

public final class MacroConditionUtil {
   private MacroConditionUtil() {
   }

   public static boolean isWaitConditionAction(MacroAction action) {
      return action != null && !(action instanceof DelayAction) && RaceAction.isConditionAction(action);
   }

   public static boolean startsWithWaitCondition(RiptideMacro macro) {
      if (macro != null && macro.actions != null) {
         for (MacroAction action : macro.actions) {
            if (action != null && action.isEnabled()) {
               return isWaitConditionAction(action);
            }
         }

         return false;
      } else {
         return false;
      }
   }
}
