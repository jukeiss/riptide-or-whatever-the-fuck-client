package riptide.util.macro;

public final class MacroStepPalette {
   public static final int DONE = -11184811;
   public static final int WAIT = -3750202;

   private MacroStepPalette() {
   }

   public static int colorFor(int index, int current, int lastCompletedStep, int nowColor) {
      if (index == current) {
         return nowColor;
      } else {
         return index < lastCompletedStep ? -11184811 : -3750202;
      }
   }
}
