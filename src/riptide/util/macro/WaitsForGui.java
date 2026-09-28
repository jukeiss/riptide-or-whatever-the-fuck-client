package riptide.util.macro;

import net.minecraft.nbt.CompoundTag;

public interface WaitsForGui {
   boolean isWaitForGuiBefore();

   void setWaitForGuiBefore(boolean var1);

   boolean isWaitForGuiAfter();

   void setWaitForGuiAfter(boolean var1);

   String getWaitGuiName();

   void setWaitGuiName(String var1);

   default boolean isWaitForGui() {
      return this.isWaitForGuiBefore() || this.isWaitForGuiAfter();
   }

   default void setWaitForGui(boolean v) {
      this.setWaitForGuiBefore(false);
      this.setWaitForGuiAfter(v);
   }

   default boolean isWaitForGuiChange() {
      return false;
   }

   static boolean loadBefore(CompoundTag tag, boolean defaultBefore) {
      if (tag == null) {
         return defaultBefore;
      } else {
         return tag.contains("waitForGuiBefore") ? tag.getBooleanOr("waitForGuiBefore", defaultBefore) : false;
      }
   }

   static boolean loadAfter(CompoundTag tag, boolean defaultAfter) {
      if (tag == null) {
         return defaultAfter;
      } else {
         return !tag.contains("waitForGuiAfter") && !tag.contains("waitForGuiBefore")
            ? tag.getBooleanOr("waitForGui", defaultAfter)
            : tag.getBooleanOr("waitForGuiAfter", defaultAfter);
      }
   }

   static String timingLabel(WaitsForGui wait) {
      if (wait == null) {
         return "";
      } else {
         boolean before = wait.isWaitForGuiBefore();
         boolean after = wait.isWaitForGuiAfter();
         if (before && after) {
            return " [before+after]";
         } else if (before) {
            return " [before]";
         } else {
            return after ? " [after]" : "";
         }
      }
   }
}
