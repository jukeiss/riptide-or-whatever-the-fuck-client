package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class SprintAction implements MacroAction, PacketOrdered {
   public boolean sprint = true;
   private boolean enabled = true;
   public boolean persistent = false;
   public volatile PacketOrder packetOrder = PacketOrder.INSTANT;

   public SprintAction() {
   }

   public SprintAction(boolean sprint) {
      this.sprint = sprint;
   }

   @Override
   public void execute(Minecraft mc) {
      if (mc.options != null && mc.options.keySprint != null) {
         mc.options.keySprint.setDown(this.sprint);
      }

      if (mc.player != null) {
         mc.player.setSprinting(this.sprint);
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putBoolean("sprint", this.sprint);
      tag.putBoolean("persistent", this.persistent);
      tag.putBoolean("enabled", this.enabled);
      PacketOrdered.save(tag, this.packetOrder);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("sprint")) {
         this.sprint = tag.getBooleanOr("sprint", true);
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
      return MacroActionType.SPRINT;
   }

   @Override
   public PacketOrder getPacketOrder() {
      return this.packetOrder;
   }

   @Override
   public String getDisplayName() {
      return "Sprint: " + (this.sprint ? "ON" : "OFF") + (this.persistent ? " [P]" : "");
   }

   @Override
   public String getIcon() {
      return "SPR";
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
