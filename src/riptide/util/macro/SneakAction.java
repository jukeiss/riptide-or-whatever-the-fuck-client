package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class SneakAction implements MacroAction, PacketOrdered {
   public boolean sneak = true;
   private boolean enabled = true;
   public boolean persistent = false;
   public volatile PacketOrder packetOrder = PacketOrder.INSTANT;

   public SneakAction() {
   }

   public SneakAction(boolean sneak) {
      this.sneak = sneak;
   }

   @Override
   public void execute(Minecraft mc) {
      if (mc.options != null && mc.options.keyShift != null) {
         mc.options.keyShift.setDown(this.sneak);
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putBoolean("sneak", this.sneak);
      tag.putBoolean("persistent", this.persistent);
      tag.putBoolean("enabled", this.enabled);
      PacketOrdered.save(tag, this.packetOrder);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("sneak")) {
         this.sneak = tag.getBooleanOr("sneak", true);
      }

      if (tag.contains("persistent")) {
         this.persistent = tag.getBooleanOr("persistent", false);
      }

      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }

      this.packetOrder = PacketOrdered.load(tag);
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.SNEAK;
   }

   @Override
   public PacketOrder getPacketOrder() {
      return this.packetOrder;
   }

   @Override
   public String getDisplayName() {
      return "Sneak: " + (this.sneak ? "ON" : "OFF") + (this.persistent ? " [P]" : "");
   }

   @Override
   public String getIcon() {
      return "SNK";
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
