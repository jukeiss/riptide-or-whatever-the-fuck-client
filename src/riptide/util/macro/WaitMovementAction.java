package riptide.util.macro;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class WaitMovementAction implements MacroAction {
   public WaitMovementAction.Mode mode = WaitMovementAction.Mode.POSITION;
   public double x = 0.0;
   public double y = 0.0;
   public double z = 0.0;
   public double leeway = 1.0;
   public boolean checkRotation = false;
   public double yaw = 0.0;
   public double pitch = 0.0;
   public double rotLeeway = 5.0;
   public double distance = 5.0;
   public double minDistance = 5.0;
   public boolean horizontalOnly = false;
   public String targetDimension = "";
   public boolean listenDuringPreviousAction = false;
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
   }

   public MacroAction resolveSubAction() {
      return (MacroAction)(switch (this.mode) {
         case POSITION -> this.toPosition();
         case POSITION_DELTA -> this.toPositionDelta();
         case WORLD_CHANGE -> this.toWorldChange();
         case TELEPORT -> this.toTeleport();
      });
   }

   public WaitPosAction toPosition() {
      WaitPosAction a = new WaitPosAction();
      a.x = this.x;
      a.y = this.y;
      a.z = this.z;
      a.leeway = this.leeway;
      a.checkRotation = this.checkRotation;
      a.yaw = (float)this.yaw;
      a.pitch = (float)this.pitch;
      a.rotLeeway = (float)this.rotLeeway;
      a.listenDuringPreviousAction = this.listenDuringPreviousAction;
      return a;
   }

   public WaitForPositionDeltaAction toPositionDelta() {
      WaitForPositionDeltaAction a = new WaitForPositionDeltaAction();
      a.distance = this.distance;
      a.horizontalOnly = this.horizontalOnly;
      a.listenDuringPreviousAction = this.listenDuringPreviousAction;
      return a;
   }

   public WaitForWorldChangeAction toWorldChange() {
      WaitForWorldChangeAction a = new WaitForWorldChangeAction();
      a.targetDimension = this.targetDimension;
      a.listenDuringPreviousAction = this.listenDuringPreviousAction;
      return a;
   }

   public WaitForTeleportAction toTeleport() {
      WaitForTeleportAction a = new WaitForTeleportAction();
      a.minDistance = this.minDistance;
      a.horizontalOnly = this.horizontalOnly;
      a.listenDuringPreviousAction = this.listenDuringPreviousAction;
      return a;
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.WAIT_MOVEMENT;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "WAIT_MOVEMENT");
      tag.putString("mode", this.mode.name());
      tag.putDouble("x", this.x);
      tag.putDouble("y", this.y);
      tag.putDouble("z", this.z);
      tag.putDouble("leeway", this.leeway);
      tag.putBoolean("checkRotation", this.checkRotation);
      tag.putDouble("yaw", this.yaw);
      tag.putDouble("pitch", this.pitch);
      tag.putDouble("rotLeeway", this.rotLeeway);
      tag.putDouble("distance", this.distance);
      tag.putDouble("minDistance", this.minDistance);
      tag.putBoolean("horizontalOnly", this.horizontalOnly);
      tag.putString("targetDimension", this.targetDimension == null ? "" : this.targetDimension);
      tag.putBoolean("enabled", this.enabled);
      MacroWaitOptions.write(tag, this);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.mode = MacroStringList.enumValue(WaitMovementAction.Mode.class, tag.getStringOr("mode", "POSITION"), WaitMovementAction.Mode.POSITION);
      this.x = tag.getDoubleOr("x", 0.0);
      this.y = tag.getDoubleOr("y", 0.0);
      this.z = tag.getDoubleOr("z", 0.0);
      this.leeway = tag.getDoubleOr("leeway", 1.0);
      this.checkRotation = tag.getBooleanOr("checkRotation", false);
      this.yaw = tag.getDoubleOr("yaw", 0.0);
      this.pitch = tag.getDoubleOr("pitch", 0.0);
      this.rotLeeway = tag.getDoubleOr("rotLeeway", 5.0);
      this.distance = tag.getDoubleOr("distance", 5.0);
      this.minDistance = Math.max(0.0, tag.getDoubleOr("minDistance", 5.0));
      this.horizontalOnly = tag.getBooleanOr("horizontalOnly", false);
      this.targetDimension = tag.getStringOr("targetDimension", "");
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }

      MacroWaitOptions.read(tag, this);
   }

   @Override
   public String getDisplayName() {
      return switch (this.mode) {
         case POSITION -> "Wait Pos " + String.format(Locale.ROOT, "%.0f,%.0f,%.0f", this.x, this.y, this.z);
         case POSITION_DELTA -> "Wait Moved " + String.format(Locale.ROOT, "%.1f", this.distance);
         case WORLD_CHANGE -> this.targetDimension != null && !this.targetDimension.isBlank() ? "Wait World: " + this.targetDimension : "Wait World Change";
         case TELEPORT -> this.minDistance <= 0.0 ? "Wait Teleport" : "Wait Teleport " + String.format(Locale.ROOT, "%.1f", this.minDistance);
      };
   }

   @Override
   public String getIcon() {
      return "MV";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   public static enum Mode {
      POSITION,
      POSITION_DELTA,
      WORLD_CHANGE,
      TELEPORT;
   }
}
