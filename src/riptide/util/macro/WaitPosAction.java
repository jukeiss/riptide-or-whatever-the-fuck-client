package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class WaitPosAction implements MacroAction, MacroCaptureOutput {
   public double x = 0.0;
   public double y = 0.0;
   public double z = 0.0;
   public double leeway = 1.0;
   public boolean checkRotation = false;
   public float yaw = 0.0F;
   public float pitch = 0.0F;
   public float rotLeeway = 5.0F;
   private boolean enabled = true;
   public boolean listenDuringPreviousAction = false;
   public String saveAs = "";

   public WaitPosAction() {
   }

   public WaitPosAction(double x, double y, double z, double leeway) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.leeway = leeway;
   }

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putDouble("x", this.x);
      tag.putDouble("y", this.y);
      tag.putDouble("z", this.z);
      tag.putDouble("leeway", this.leeway);
      tag.putBoolean("checkRotation", this.checkRotation);
      tag.putFloat("yaw", this.yaw);
      tag.putFloat("pitch", this.pitch);
      tag.putFloat("rotLeeway", this.rotLeeway);
      tag.putBoolean("enabled", this.enabled);
      MacroWaitOptions.write(tag, this);
      tag.putString("saveAs", this.saveAs);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("x")) {
         this.x = tag.getDoubleOr("x", 0.0);
      }

      if (tag.contains("y")) {
         this.y = tag.getDoubleOr("y", 0.0);
      }

      if (tag.contains("z")) {
         this.z = tag.getDoubleOr("z", 0.0);
      }

      if (tag.contains("leeway")) {
         this.leeway = tag.getDoubleOr("leeway", 1.0);
      }

      if (tag.contains("checkRotation")) {
         this.checkRotation = tag.getBooleanOr("checkRotation", false);
      }

      if (tag.contains("yaw")) {
         this.yaw = tag.getFloatOr("yaw", 0.0F);
      }

      if (tag.contains("pitch")) {
         this.pitch = tag.getFloatOr("pitch", 0.0F);
      }

      if (tag.contains("rotLeeway")) {
         this.rotLeeway = tag.getFloatOr("rotLeeway", 5.0F);
      }

      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }

      MacroWaitOptions.read(tag, this);
      this.saveAs = tag.getStringOr("saveAs", "");
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.WAIT_POS;
   }

   @Override
   public String getDisplayName() {
      return this.checkRotation
         ? String.format("Wait Pos & Rot (%.0f, %.0f, %.0f)", this.x, this.y, this.z)
         : String.format("Wait Pos (%.0f, %.0f, %.0f) +/-%.1f", this.x, this.y, this.z, this.leeway);
   }

   @Override
   public String getIcon() {
      return "LOC";
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
