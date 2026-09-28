package riptide.util.macro;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class WaitForHealthAction implements MacroAction, MacroCaptureOutput {
   public static final String COMPARISON_DROPS_BELOW = "Drops Below";
   public static final String COMPARISON_RISES_ABOVE = "Rises Above";
   public float healthThreshold = 20.0F;
   public boolean below = true;
   public boolean listenDuringPreviousAction = false;
   public String saveAs = "";

   public WaitForHealthAction() {
   }

   public WaitForHealthAction(float healthThreshold, boolean below) {
      this.healthThreshold = healthThreshold;
      this.below = below;
   }

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putFloat("healthThreshold", this.healthThreshold);
      tag.putBoolean("below", this.below);
      tag.putString("comparison", this.comparisonLabel());
      MacroWaitOptions.write(tag, this);
      tag.putString("saveAs", this.saveAs);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("healthThreshold")) {
         this.healthThreshold = tag.getFloatOr("healthThreshold", 20.0F);
      }

      if (tag.contains("comparison")) {
         this.below = "Drops Below".equals(tag.getStringOr("comparison", "Drops Below"));
      } else if (tag.contains("below")) {
         this.below = tag.getBooleanOr("below", true);
      }

      MacroWaitOptions.read(tag, this);
      this.saveAs = tag.getStringOr("saveAs", "");
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.WAIT_HEALTH;
   }

   @Override
   public String getDisplayName() {
      return "Wait HP: " + this.comparisonLabel() + " " + this.thresholdText();
   }

   @Override
   public String getIcon() {
      return "HP";
   }

   public String comparisonLabel() {
      return this.below ? "Drops Below" : "Rises Above";
   }

   public String thresholdText() {
      return String.format(Locale.ROOT, "%.1f", this.healthThreshold);
   }

   public String waitingStatusText() {
      return this.below ? "Waiting for health to drop below " + this.thresholdText() : "Waiting for health to rise above " + this.thresholdText();
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
