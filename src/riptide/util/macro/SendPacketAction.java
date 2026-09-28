package riptide.util.macro;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import riptide.modules.PackHideState;
import riptide.util.PacketRegenerator;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideClipboardHelper;
import riptide.util.RiptidePacketNamer;
import riptide.util.RiptidePacketSender;
import riptide.util.RiptideSharedState;

public class SendPacketAction implements MacroAction, WaitsForGui {
   public List<RiptideSharedState.QueuedPacket> packets = new ArrayList<>();
   public String customName = "";
   public boolean waitForGuiBefore = false;
   public boolean waitForGuiAfter = false;
   public String guiName = "";

   public List<RiptideSharedState.QueuedPacket> getPackets() {
      return this.packets;
   }

   public void regeneratePackets() {
      List<RiptideSharedState.QueuedPacket> newPackets = new ArrayList<>(this.packets.size());

      for (RiptideSharedState.QueuedPacket qp : this.packets) {
         if (qp.packet != null) {
            if (qp.isExactReplay()) {
               newPackets.add(new RiptideSharedState.QueuedPacket(qp.packet, qp.getDelay(), qp.getId(), qp.getReplayMode()));
            } else {
               Packet<?> regenerated = PacketRegenerator.regenerate(qp.packet);
               if (regenerated != null) {
                  newPackets.add(new RiptideSharedState.QueuedPacket(regenerated, qp.getDelay(), qp.getId(), qp.getReplayMode()));
               }
            }
         }
      }

      this.packets.clear();
      this.packets.addAll(newPackets);
   }

   @Override
   public void execute(Minecraft mc) {
      if (!PackHideState.isHardLocked()) {
         if (mc.player != null && mc.getConnection() != null) {
            int successCount = 0;
            int failCount = 0;

            for (RiptideSharedState.QueuedPacket qp : this.packets) {
               if (qp.packet == null) {
                  failCount++;
               } else {
                  try {
                     RiptidePacketSender.send(qp.packet);
                     successCount++;
                  } catch (Exception var7) {
                     RiptideClientMessaging.sendPrefixed("§cSend failed: " + var7.getMessage());
                     failCount++;
                  }
               }
            }

            if (failCount > 0) {
               RiptideClientMessaging.sendPrefixed(String.format("§eSent %d/%d packets (%d failed)", successCount, this.packets.size(), failCount));
            }
         } else {
            RiptideClientMessaging.sendPrefixed("§cNo network connection!");
         }
      }
   }

   @Override
   public boolean isWaitForGuiBefore() {
      return this.waitForGuiBefore;
   }

   @Override
   public void setWaitForGuiBefore(boolean v) {
      this.waitForGuiBefore = v;
   }

   @Override
   public boolean isWaitForGuiAfter() {
      return this.waitForGuiAfter;
   }

   @Override
   public void setWaitForGuiAfter(boolean v) {
      this.waitForGuiAfter = v;
   }

   @Override
   public String getWaitGuiName() {
      return this.guiName;
   }

   @Override
   public void setWaitGuiName(String name) {
      this.guiName = name != null ? name : "";
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.SEND_PACKET;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "SEND_PACKET");
      tag.putString("customName", this.customName);
      tag.putBoolean("waitForGuiBefore", this.waitForGuiBefore);
      tag.putBoolean("waitForGuiAfter", this.waitForGuiAfter);
      tag.putString("guiName", this.guiName);
      ListTag packetList = new ListTag();

      for (RiptideSharedState.QueuedPacket qp : this.packets) {
         CompoundTag packetTag = RiptideClipboardHelper.serializeQueuedPacket(qp);
         if (packetTag != null) {
            packetList.add(packetTag);
         }
      }

      tag.put("packets", packetList);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.packets.clear();
      this.customName = tag.getStringOr("customName", "");
      this.waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
      this.waitForGuiAfter = WaitsForGui.loadAfter(tag, false);
      if (tag.contains("guiName")) {
         this.guiName = tag.getStringOr("guiName", "");
      }

      if (tag.contains("packets")) {
         ListTag packetList = (ListTag)tag.get("packets");
         if (packetList != null) {
            for (int i = 0; i < packetList.size(); i++) {
               Tag element = packetList.get(i);
               if (element instanceof CompoundTag) {
                  RiptideSharedState.QueuedPacket qp = RiptideClipboardHelper.deserializeQueuedPacket((CompoundTag)element);
                  if (qp != null) {
                     this.packets.add(qp);
                  }
               }
            }
         }
      }
   }

   @Override
   public String getDisplayName() {
      if (this.customName != null && !this.customName.trim().isEmpty()) {
         return "Send " + this.customName.trim() + WaitsForGui.timingLabel(this);
      } else if (this.packets.isEmpty()) {
         return "Send (empty)" + WaitsForGui.timingLabel(this);
      } else {
         Map<String, Integer> nameCounts = new LinkedHashMap<>();

         for (RiptideSharedState.QueuedPacket qp : this.packets) {
            if (qp.packet != null) {
               String name = RiptidePacketNamer.getFriendlyName(qp.packet);
               nameCounts.merge(name, 1, Integer::sum);
            }
         }

         if (nameCounts.isEmpty()) {
            return "Send (empty)" + WaitsForGui.timingLabel(this);
         } else if (nameCounts.size() == 1) {
            Entry<String, Integer> entry = nameCounts.entrySet().iterator().next();
            String pktName = entry.getKey();
            int count = entry.getValue();
            return count == 1 ? "Send " + pktName + WaitsForGui.timingLabel(this) : "Send " + pktName + " x" + count + WaitsForGui.timingLabel(this);
         } else {
            return "Send Group (" + this.packets.size() + ")" + WaitsForGui.timingLabel(this);
         }
      }
   }

   @Override
   public String getIcon() {
      return "P";
   }
}
