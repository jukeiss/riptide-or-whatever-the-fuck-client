package riptide.util.multi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class MultiPacketPolicy {
   private boolean gravity = true;
   private boolean autoPosition = true;
   private boolean autoLook = false;
   private boolean autoSwing = false;
   private final List<MultiPacketPolicy.Slot> slots = new ArrayList<>(
      List.of(
         new MultiPacketPolicy.Slot("", true), new MultiPacketPolicy.Slot("", true), new MultiPacketPolicy.Slot("", true), new MultiPacketPolicy.Slot("", true)
      )
   );
   private final List<MultiPacketPolicy.Rule> blocklist = new ArrayList<>();

   public MultiPacketPolicy() {
   }

   public MultiPacketPolicy(MultiPacketPolicy source) {
      if (source != null) {
         this.gravity = source.gravity;
         this.autoPosition = source.autoPosition;
         this.autoLook = source.autoLook;
         this.autoSwing = source.autoSwing;
         this.slots.clear();
         this.slots.addAll(source.slots);
         this.blocklist.addAll(source.blocklist);
         this.normalize();
      }
   }

   public boolean gravity() {
      return this.gravity;
   }

   public void setGravity(boolean gravity) {
      this.gravity = gravity;
   }

   public boolean autoPosition() {
      return this.autoPosition;
   }

   public void setAutoPosition(boolean autoPosition) {
      this.autoPosition = autoPosition;
   }

   public boolean autoLook() {
      return this.autoLook;
   }

   public void setAutoLook(boolean autoLook) {
      this.autoLook = autoLook;
   }

   public boolean autoSwing() {
      return this.autoSwing;
   }

   public void setAutoSwing(boolean autoSwing) {
      this.autoSwing = autoSwing;
   }

   public List<MultiPacketPolicy.Slot> slots() {
      return List.copyOf(this.slots);
   }

   public void setSlot(int index, MultiPacketPolicy.Slot slot) {
      if (index >= 0 && index < 4) {
         this.slots.set(index, slot == null ? new MultiPacketPolicy.Slot("", true) : slot);
      } else {
         throw new IndexOutOfBoundsException(index);
      }
   }

   public List<MultiPacketPolicy.Rule> blocklist() {
      return List.copyOf(this.blocklist);
   }

   public void setBlocklist(List<MultiPacketPolicy.Rule> rules) {
      this.blocklist.clear();
      if (rules != null) {
         this.blocklist.addAll(rules);
      }

      this.normalize();
   }

   public boolean allows(MultiPacketPolicy.Direction direction, String packetClass, boolean protocolCritical, boolean movementPacket) {
      if (protocolCritical) {
         return true;
      } else {
         String packet = packetClass == null ? "" : packetClass;
         if (this.isBlocked(direction, packet)) {
            return false;
         } else {
            if (direction == MultiPacketPolicy.Direction.C2S) {
               for (MultiPacketPolicy.Slot slot : this.slots) {
                  if (!slot.packetClass().isBlank() && slot.packetClass().equals(packet)) {
                     return slot.enabled();
                  }
               }
            }

            return true;
         }
      }
   }

   public static boolean isProtected(MultiPacketPolicy.Direction direction, String packetClass) {
      String name = packetClass == null ? "" : packetClass;
      return direction == MultiPacketPolicy.Direction.C2S
         ? ends(
            name,
            "ServerboundKeepAlivePacket",
            "ServerboundPongPacket",
            "ServerboundAcceptTeleportationPacket",
            "ServerboundCookieResponsePacket",
            "ServerboundResourcePackPacket",
            "ServerboundLoginAcknowledgedPacket",
            "ServerboundFinishConfigurationPacket",
            "ServerboundSelectKnownPacks",
            "ServerboundAcceptCodeOfConductPacket",
            "ServerboundCustomQueryAnswerPacket",
            "ServerboundConfigurationAcknowledgedPacket",
            "ServerboundChatAckPacket",
            "ServerboundChatSessionUpdatePacket",
            "ServerboundPlayerLoadedPacket",
            "ServerboundClientInformationPacket",
            "ServerboundCustomPayloadPacket",
            "ServerboundCustomClickActionPacket"
         )
         : ends(
            name,
            "ClientboundKeepAlivePacket",
            "ClientboundPingPacket",
            "ClientboundDisconnectPacket",
            "ClientboundLoginDisconnectPacket",
            "ClientboundHelloPacket",
            "ClientboundLoginFinishedPacket",
            "ClientboundLoginCompressionPacket",
            "ClientboundCustomQueryPacket",
            "ClientboundFinishConfigurationPacket",
            "ClientboundRegistryDataPacket",
            "ClientboundUpdateEnabledFeaturesPacket",
            "ClientboundSelectKnownPacks",
            "ClientboundResetChatPacket",
            "ClientboundCodeOfConductPacket",
            "ClientboundUpdateTagsPacket",
            "ClientboundCookieRequestPacket",
            "ClientboundStoreCookiePacket",
            "ClientboundResourcePackPushPacket",
            "ClientboundTransferPacket",
            "ClientboundLoginPacket",
            "ClientboundStartConfigurationPacket",
            "ClientboundPlayerPositionPacket",
            "ClientboundPlayerRotationPacket",
            "ClientboundAddEntityPacket",
            "ClientboundMoveEntityPacket$Pos",
            "ClientboundMoveEntityPacket$PosRot",
            "ClientboundMoveEntityPacket$Rot",
            "ClientboundEntityPositionSyncPacket",
            "ClientboundTeleportEntityPacket",
            "ClientboundRotateHeadPacket",
            "ClientboundSetEntityDataPacket",
            "ClientboundSetEntityMotionPacket",
            "ClientboundRemoveEntitiesPacket",
            "ClientboundSetPassengersPacket",
            "ClientboundMoveMinecartPacket",
            "ClientboundShowDialogPacket",
            "ClientboundClearDialogPacket"
         );
   }

   private static boolean ends(String value, String... names) {
      for (String name : names) {
         if (value.endsWith(name)) {
            return true;
         }
      }

      return false;
   }

   private boolean isBlocked(MultiPacketPolicy.Direction direction, String packetClass) {
      for (MultiPacketPolicy.Rule rule : this.blocklist) {
         if (rule.direction() == direction && rule.packetClass().equals(packetClass)) {
            return true;
         }
      }

      return false;
   }

   public CompoundTag toTag() {
      this.normalize();
      CompoundTag tag = new CompoundTag();
      tag.putBoolean("gravity", this.gravity);
      tag.putBoolean("autoPosition", this.autoPosition);
      tag.putBoolean("autoLook", this.autoLook);
      tag.putBoolean("autoSwing", this.autoSwing);
      ListTag slotTags = new ListTag();

      for (MultiPacketPolicy.Slot slot : this.slots) {
         slotTags.add(slot.toTag());
      }

      tag.put("slots", slotTags);
      ListTag blockTags = new ListTag();

      for (MultiPacketPolicy.Rule rule : this.blocklist) {
         blockTags.add(rule.toTag());
      }

      tag.put("blocklist", blockTags);
      return tag;
   }

   public static MultiPacketPolicy fromTag(CompoundTag tag) {
      MultiPacketPolicy policy = new MultiPacketPolicy();
      policy.gravity = tag.getBooleanOr("gravity", true);
      policy.autoPosition = tag.getBooleanOr("autoPosition", true);
      policy.autoLook = tag.getBooleanOr("autoLook", false);
      policy.autoSwing = tag.getBooleanOr("autoSwing", false);
      policy.slots.clear();

      for (Tag value : tag.getListOrEmpty("slots")) {
         if (value instanceof CompoundTag compound) {
            policy.slots.add(MultiPacketPolicy.Slot.fromTag(compound));
         }
      }

      for (Tag valuex : tag.getListOrEmpty("blocklist")) {
         if (valuex instanceof CompoundTag compound) {
            policy.blocklist.add(MultiPacketPolicy.Rule.fromTag(compound));
         }
      }

      policy.normalize();
      return policy;
   }

   private void normalize() {
      while (this.slots.size() < 4) {
         this.slots.add(new MultiPacketPolicy.Slot("", true));
      }

      while (this.slots.size() > 4) {
         this.slots.remove(this.slots.size() - 1);
      }

      Set<String> seen = new HashSet<>();
      this.blocklist
         .removeIf(
            rule -> rule == null
               || rule.packetClass().isBlank()
               || isProtected(rule.direction(), rule.packetClass())
               || !seen.add(rule.direction().name() + "\u0000" + rule.packetClass())
         );
   }

   public static enum Direction {
      C2S,
      S2C;
   }

   public record Rule(MultiPacketPolicy.Direction direction, String packetClass) {
      public Rule(MultiPacketPolicy.Direction direction, String packetClass) {
         direction = direction == null ? MultiPacketPolicy.Direction.C2S : direction;
         packetClass = packetClass == null ? "" : packetClass.trim();
         this.direction = direction;
         this.packetClass = packetClass;
      }

      CompoundTag toTag() {
         CompoundTag tag = new CompoundTag();
         tag.putString("direction", this.direction.name());
         tag.putString("packet", this.packetClass);
         return tag;
      }

      static MultiPacketPolicy.Rule fromTag(CompoundTag tag) {
         MultiPacketPolicy.Direction direction;
         try {
            direction = MultiPacketPolicy.Direction.valueOf(tag.getStringOr("direction", MultiPacketPolicy.Direction.C2S.name()).toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException var3) {
            direction = MultiPacketPolicy.Direction.C2S;
         }

         return new MultiPacketPolicy.Rule(direction, tag.getStringOr("packet", ""));
      }
   }

   public record Slot(String packetClass, boolean enabled) {
      public Slot(String packetClass, boolean enabled) {
         packetClass = packetClass == null ? "" : packetClass.trim();
         this.packetClass = packetClass;
         this.enabled = enabled;
      }

      CompoundTag toTag() {
         CompoundTag tag = new CompoundTag();
         tag.putString("packet", this.packetClass);
         tag.putBoolean("enabled", this.enabled);
         return tag;
      }

      static MultiPacketPolicy.Slot fromTag(CompoundTag tag) {
         return new MultiPacketPolicy.Slot(tag.getStringOr("packet", ""), tag.getBooleanOr("enabled", true));
      }
   }
}
