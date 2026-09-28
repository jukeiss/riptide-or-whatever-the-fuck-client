package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroManager;

public class StartMacroAction implements MacroAction {
   public String macroName = "";
   public boolean restartIfRunning = false;
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
      if (this.macroName != null && !this.macroName.isBlank()) {
         if (this.restartIfRunning && MacroExecutor.isMacroRunning(this.macroName)) {
            MacroExecutor.stopMacro(this.macroName);
         } else if (MacroExecutor.isMacroRunning(this.macroName)) {
            return;
         }

         RiptideMacro macro = RiptideMacroManager.get().get(this.macroName);
         if (macro != null) {
            macro.execute();
         }
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("macroName", this.macroName == null ? "" : this.macroName);
      tag.putBoolean("restartIfRunning", this.restartIfRunning);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.macroName = tag.getStringOr("macroName", "");
      this.restartIfRunning = tag.getBooleanOr("restartIfRunning", false);
      this.enabled = tag.getBooleanOr("enabled", true);
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.START_MACRO;
   }

   @Override
   public String getDisplayName() {
      return this.macroName != null && !this.macroName.isBlank() ? "Start: " + this.macroName : "Start Macro";
   }

   @Override
   public String getIcon() {
      return "RUN";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }
}
