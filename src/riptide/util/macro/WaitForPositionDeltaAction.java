package riptide.util.macro;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class WaitForPositionDeltaAction implements MacroAction, MacroCaptureOutput {
   public double distance = 5.0;
   public boolean horizontalOnly = false;
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
      tag.putDouble("distance", this.distance);
      tag.putBoolean("horizontalOnly", this.horizontalOnly);
      tag.putBoolean("enabled", this.enabled);
      MacroWaitOptions.write(tag, this);
      tag.putString("saveAs", this.saveAs);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.distance = Math.max(0.0, tag.getDoubleOr("distance", 5.0));
      this.horizontalOnly = tag.getBooleanOr("horizontalOnly", false);
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }

      MacroWaitOptions.read(tag, this);
      this.saveAs = tag.getStringOr("saveAs", "");
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.WAIT_POSITION_DELTA;
   }

   @Override
   public String getDisplayName() {
      return "Wait Position Delta: " + String.format(Locale.ROOT, "%.1f", this.distance);
   }

   @Override
   public String getIcon() {
      return "DPos";
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
