package riptide.util.macro;

import net.minecraft.nbt.CompoundTag;

public interface PacketOrdered {
   String PACKET_ORDER_KEY = "packetOrder";

   PacketOrder getPacketOrder();

   default boolean isTickAligned() {
      return this.getPacketOrder() == PacketOrder.GRIM;
   }

   static void save(CompoundTag tag, PacketOrder order) {
      tag.putString("packetOrder", (order == null ? PacketOrder.INSTANT : order).name());
   }

   static PacketOrder load(CompoundTag tag) {
      return tag != null && tag.contains("packetOrder")
         ? PacketOrder.parse(tag.getStringOr("packetOrder", "INSTANT"), PacketOrder.INSTANT)
         : PacketOrder.INSTANT;
   }
}
