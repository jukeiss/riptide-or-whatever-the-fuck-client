package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class StopMacroAction implements MacroAction {
   public String macroName = "";
   public StopMacroAction.StopTarget target = StopMacroAction.StopTarget.SELF;
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
      if (this.target == StopMacroAction.StopTarget.ALL) {
         MacroExecutor.stop();
      } else if (this.target == StopMacroAction.StopTarget.SELECTED && this.macroName != null && !this.macroName.isBlank()) {
         MacroExecutor.stopMacro(this.macroName);
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("target", this.target.name());
      tag.putString("macroName", this.macroName == null ? "" : this.macroName);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      try {
         this.target = StopMacroAction.StopTarget.valueOf(tag.getStringOr("target", StopMacroAction.StopTarget.SELF.name()));
      } catch (Exception var3) {
         this.target = StopMacroAction.StopTarget.SELF;
      }

      this.macroName = tag.getStringOr("macroName", "");
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.STOP_MACRO;
   }

   @Override
   public String getDisplayName() {
      if (this.target == StopMacroAction.StopTarget.SELECTED && this.macroName != null && !this.macroName.isBlank()) {
         return "Stop: " + this.macroName;
      } else {
         return this.target == StopMacroAction.StopTarget.ALL ? "Stop All Macros" : "Stop This Macro";
      }
   }

   @Override
   public String getIcon() {
      return "STP";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   public static enum StopTarget {
      SELF,
      SELECTED,
      ALL;
   }
}
