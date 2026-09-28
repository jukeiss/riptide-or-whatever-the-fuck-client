package riptide.util.macro;

import net.minecraft.nbt.CompoundTag;

final class MacroWaitOptions {
   private MacroWaitOptions() {
   }

   static void write(CompoundTag tag, MacroAction action) {
      if (tag != null && action != null) {
         tag.putBoolean("listenDuringPreviousAction", action.listensDuringPreviousAction());
      }
   }

   static void read(CompoundTag tag, MacroAction action) {
      if (tag != null && action != null) {
         action.setListenDuringPreviousAction(tag.getBooleanOr("listenDuringPreviousAction", false));
      }
   }
}
