package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class EndPacketGateAction implements MacroAction {
   public String gateId = "auto";
   public boolean flushOnDisable = true;

   public EndPacketGateAction() {
   }

   public EndPacketGateAction(String gateId) {
      this.gateId = gateId != null && !gateId.isBlank() ? gateId : "auto";
   }

   @Override
   public void execute(Minecraft mc) {
      PacketGateManager.disableAndFlushConfigured(this.gateId, mc == null ? null : mc.getConnection(), this.flushOnDisable);
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.END_PACKET_GATE;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "END_PACKET_GATE");
      tag.putString("gateId", this.gateId == null ? "auto" : this.gateId);
      tag.putBoolean("flushOnDisable", this.flushOnDisable);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.gateId = tag.getStringOr("gateId", "auto");
      this.flushOnDisable = tag.getBooleanOr("flushOnDisable", true);
   }

   @Override
   public String getDisplayName() {
      return "End Packet Gate";
   }

   @Override
   public String getIcon() {
      return "G";
   }
}
