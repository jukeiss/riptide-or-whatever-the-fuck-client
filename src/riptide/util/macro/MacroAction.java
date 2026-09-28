package riptide.util.macro;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public interface MacroAction {
   String LISTEN_DURING_PREVIOUS_KEY = "listenDuringPreviousAction";

   void execute(Minecraft var1);

   CompoundTag toTag();

   void fromTag(CompoundTag var1);

   default MacroActionType getType() {
      return null;
   }

   default String getTypeId() {
      MacroActionType type = this.getType();
      return type != null ? type.name() : this.getClass().getName();
   }

   String getDisplayName();

   String getIcon();

   default boolean isEnabled() {
      return true;
   }

   default void setEnabled(boolean enabled) {
   }

   default void sanitizeForSharing() {
   }

   default boolean listensDuringPreviousAction() {
      try {
         Field field = this.getClass().getField("listenDuringPreviousAction");
         return field.getBoolean(this);
      } catch (Throwable var2) {
         return false;
      }
   }

   default void setListenDuringPreviousAction(boolean enabled) {
      try {
         Field field = this.getClass().getField("listenDuringPreviousAction");
         field.setBoolean(this, enabled);
      } catch (Throwable var3) {
      }
   }
}
