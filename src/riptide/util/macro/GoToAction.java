package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideCompatManager;

public class GoToAction implements MacroAction {
   public double x = 0.0;
   public double y = 0.0;
   public double z = 0.0;
   private boolean enabled = true;
   public boolean waitForArrival = true;
   public double arrivalRadius = 2.0;
   public int timeoutMs = 60000;

   public GoToAction() {
   }

   public GoToAction(double x, double y, double z) {
      this.x = x;
      this.y = y;
      this.z = z;
   }

   @Override
   public void execute(Minecraft mc) {
      if (mc.player != null) {
         String command = String.format("#goto %.1f %.1f %.1f", this.x, this.y, this.z);
         if (!RiptideCompatManager.sendBaritoneCommand(mc, command)) {
            RiptideClientMessaging.sendPrefixed("§cBaritone is not available, cannot send goto command.");
         }
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putDouble("x", this.x);
      tag.putDouble("y", this.y);
      tag.putDouble("z", this.z);
      tag.putBoolean("waitForArrival", this.waitForArrival);
      tag.putDouble("arrivalRadius", this.arrivalRadius);
      tag.putInt("timeoutMs", this.timeoutMs);
      tag.putBoolean("enabled", this.enabled);
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

      if (tag.contains("waitForArrival")) {
         this.waitForArrival = tag.getBooleanOr("waitForArrival", true);
      }

      if (tag.contains("arrivalRadius")) {
         this.arrivalRadius = tag.getDoubleOr("arrivalRadius", 2.0);
      }

      if (tag.contains("timeoutMs")) {
         this.timeoutMs = tag.getIntOr("timeoutMs", 60000);
      }

      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.GO_TO;
   }

   @Override
   public String getDisplayName() {
      return String.format("Go To (%.0f, %.0f, %.0f)%s", this.x, this.y, this.z, this.waitForArrival ? " [W]" : "");
   }

   @Override
   public String getIcon() {
      return "GO";
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
