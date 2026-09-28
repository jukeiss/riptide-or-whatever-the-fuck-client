package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import riptide.modules.PackHideState;
import riptide.util.PacketRegenerator;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideClipboardHelper;
import riptide.util.RiptidePacketSender;

public class PacketAction implements MacroAction {
   public String packetData = "";
   public boolean regenerate = true;
   public String description = "Packet";

   public PacketAction() {
   }

   public PacketAction(String packetData, boolean regenerate, String description) {
      this.packetData = packetData;
      this.regenerate = regenerate;
      this.description = description;
   }

   @Override
   public void execute(Minecraft mc) {
      if (!PackHideState.isHardLocked()) {
         if (mc.getConnection() == null) {
            RiptideClientMessaging.sendPrefixed("§cNo network connection!");
         } else {
            MacroTemplate.Resolution packetDataResolution = MacroVariables.resolve(this.packetData, mc);
            if (packetDataResolution.success()) {
               String resolvedPacketData = packetDataResolution.value();
               if (resolvedPacketData.isEmpty()) {
                  RiptideClientMessaging.sendPrefixed("§cPacket data is empty!");
               } else {
                  try {
                     Packet<?> packet = RiptideClipboardHelper.deserializePacketFromBase64(resolvedPacketData);
                     if (packet == null) {
                        RiptideClientMessaging.sendPrefixed("§cFailed to deserialize packet! Invalid data.");
                        return;
                     }

                     if (this.regenerate) {
                        Packet<?> regenerated = PacketRegenerator.regenerate(packet);
                        if (regenerated == null) {
                           RiptideClientMessaging.sendPrefixed("§cFailed to regenerate packet: " + packet.getClass().getSimpleName());
                           return;
                        }

                        packet = regenerated;
                     }

                     RiptidePacketSender.send(packet);
                  } catch (Exception var6) {
                     RiptideClientMessaging.sendPrefixed("§cPacket send error: " + var6.getMessage());
                     riptide.RiptideClientAddon.LOG.error("[MacroExecutor] Packet action failed", var6);
                  }
               }
            }
         }
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("packetData", this.packetData);
      tag.putBoolean("regenerate", this.regenerate);
      tag.putString("description", this.description);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("packetData")) {
         this.packetData = tag.getStringOr("packetData", "");
      }

      if (tag.contains("regenerate")) {
         this.regenerate = tag.getBooleanOr("regenerate", true);
      }

      if (tag.contains("description")) {
         this.description = tag.getStringOr("description", "");
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.PACKET;
   }

   @Override
   public String getDisplayName() {
      return this.description.isEmpty() ? "Unknown Packet" : this.description;
   }

   @Override
   public String getIcon() {
      return "Pkt";
   }
}
