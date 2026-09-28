package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class RepeatAction implements MacroAction {
   public int stepCount = 1;
   public int repeatCount = 2;
   private boolean enabled = true;

   public RepeatAction() {
   }

   public RepeatAction(int stepCount, int repeatCount) {
      this.stepCount = stepCount;
      this.repeatCount = repeatCount;
   }

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putInt("stepCount", this.stepCount);
      tag.putInt("repeatCount", this.repeatCount);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("stepCount")) {
         this.stepCount = tag.getIntOr("stepCount", 1);
      }

      if (tag.contains("repeatCount")) {
         this.repeatCount = tag.getIntOr("repeatCount", 2);
      }

      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.REPEAT;
   }

   @Override
   public String getDisplayName() {
      return "Repeat next " + this.stepCount + " step" + (this.stepCount != 1 ? "s" : "") + " x" + this.repeatCount;
   }

   @Override
   public String getIcon() {
      return "RPT";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }
}
