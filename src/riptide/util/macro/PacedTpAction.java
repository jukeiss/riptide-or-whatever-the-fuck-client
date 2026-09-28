package riptide.util.macro;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import riptide.util.multi.PacketTeleportController;

public final class PacedTpAction implements MacroAction {
   public double x;
   public double y;
   public double z;
   public boolean relativeX;
   public boolean relativeY;
   public boolean relativeZ;
   public int maxPackets = 20;
   public int pauseMs = 500;
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
      PacketTeleportController.startMacro(this);
   }

   public String commandArguments() {
      return coordinate(this.x, this.relativeX)
         + " "
         + coordinate(this.y, this.relativeY)
         + " "
         + coordinate(this.z, this.relativeZ)
         + " "
         + clamp(this.maxPackets, 1, 100)
         + " "
         + clamp(this.pauseMs, 50, 10000);
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putDouble("x", finite(this.x));
      tag.putDouble("y", finite(this.y));
      tag.putDouble("z", finite(this.z));
      tag.putBoolean("relativeX", this.relativeX);
      tag.putBoolean("relativeY", this.relativeY);
      tag.putBoolean("relativeZ", this.relativeZ);
      tag.putInt("maxPackets", clamp(this.maxPackets, 1, 100));
      tag.putInt("pauseMs", clamp(this.pauseMs, 50, 10000));
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.x = finite(tag.getDoubleOr("x", 0.0));
      this.y = finite(tag.getDoubleOr("y", 0.0));
      this.z = finite(tag.getDoubleOr("z", 0.0));
      this.relativeX = tag.getBooleanOr("relativeX", false);
      this.relativeY = tag.getBooleanOr("relativeY", false);
      this.relativeZ = tag.getBooleanOr("relativeZ", false);
      this.maxPackets = clamp(tag.getIntOr("maxPackets", 20), 1, 100);
      this.pauseMs = clamp(tag.getIntOr("pauseMs", 500), 50, 10000);
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.TP;
   }

   @Override
   public String getDisplayName() {
      return "TP " + coordinate(this.x, this.relativeX) + " " + coordinate(this.y, this.relativeY) + " " + coordinate(this.z, this.relativeZ);
   }

   @Override
   public String getIcon() {
      return "TP";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   private static String coordinate(double value, boolean relative) {
      return (relative ? "~" : "") + String.format(Locale.ROOT, "%.2f", finite(value));
   }

   private static double finite(double value) {
      return Double.isFinite(value) ? value : 0.0;
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }
}
