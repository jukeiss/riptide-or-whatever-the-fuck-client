package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class WaitForWorldChangeAction implements MacroAction, MacroCaptureOutput {
   public String targetDimension = "";
   public boolean listenDuringPreviousAction = false;
   public String saveAs = "";
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("targetDimension", this.targetDimension == null ? "" : this.targetDimension);
      tag.putBoolean("enabled", this.enabled);
      MacroWaitOptions.write(tag, this);
      tag.putString("saveAs", this.saveAs);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.targetDimension = tag.getStringOr("targetDimension", "");
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }

      MacroWaitOptions.read(tag, this);
      this.saveAs = tag.getStringOr("saveAs", "");
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.WAIT_WORLD_CHANGE;
   }

   @Override
   public String getDisplayName() {
      return this.targetDimension != null && !this.targetDimension.isBlank() ? "Wait World: " + this.targetDimension : "Wait World Change";
   }

   @Override
   public String getIcon() {
      return "WRL";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   @Override
   public String getSaveAs() {
      return this.saveAs;
   }

   @Override
   public void setSaveAs(String name) {
      this.saveAs = name == null ? "" : name;
   }
}
