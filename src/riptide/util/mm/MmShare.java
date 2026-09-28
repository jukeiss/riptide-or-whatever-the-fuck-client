package riptide.util.mm;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import riptide.util.RiptideClipboardHelper;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroManager;
import riptide.util.RiptideNotifications;
import riptide.util.RiptidePacketLoggerOverlay;
import riptide.util.RiptideSharedState;
import riptide.util.macro.MacroAction;
import riptide.util.mm.msg.MmMessages;

public final class MmShare {
   private MmShare() {
   }

   public static MmMessages.MacroOffer buildMacroOffer(RiptideMacro macro) {
      if (macro == null) {
         return null;
      } else {
         RiptideMacro stripped = macro.deepCopy();
         stripped.name = "";
         stripped.keyCode = -1;
         String hash = RiptideClipboardHelper.serializeMacroToBase64(stripped);
         if (hash == null) {
            return null;
         } else {
            MmMessages.MacroOffer offer = new MmMessages.MacroOffer();
            offer.macroName = "";
            List<MacroAction> actions = macro.actions;
            offer.actionCount = actions == null ? 0 : actions.size();
            if (offer.actionCount == 1) {
               MacroAction only = actions.get(0);
               offer.singleActionLabel = only == null ? "" : safe(only.getDisplayName());
            }

            offer.hash = hash;
            return offer;
         }
      }
   }

   public static MmMessages.PacketOffer buildPacketOffer(List<RiptideSharedState.QueuedPacket> queue, String friendlyName) {
      if (queue != null && !queue.isEmpty()) {
         String data = RiptideClipboardHelper.serializeQueueToBase64(queue);
         if (data == null) {
            return null;
         } else {
            MmMessages.PacketOffer offer = new MmMessages.PacketOffer();
            int n = queue.size();
            offer.friendlyName = friendlyName != null && !friendlyName.isBlank() ? friendlyName : n + (n == 1 ? " packet" : " packets");
            offer.direction = "C2S";
            offer.data = data;
            return offer;
         }
      } else {
         return null;
      }
   }

   public static List<RiptideSharedState.QueuedPacket> queueFromOffer(MmMessages.PacketOffer offer) {
      return offer != null && offer.data != null ? RiptideClipboardHelper.deserializeQueueFromBase64(offer.data) : null;
   }

   public static int addToQueue(MmMessages.PacketOffer offer) {
      List<RiptideSharedState.QueuedPacket> add = queueFromOffer(offer);
      if (add != null && !add.isEmpty()) {
         List<RiptideSharedState.QueuedPacket> cur = new ArrayList<>(RiptideSharedState.get().getDelayedPackets());
         cur.addAll(add);
         RiptideSharedState.get().setDelayedPackets(cur);
         return add.size();
      } else {
         return -1;
      }
   }

   public static RiptideMacro importMacro(MmMessages.MacroOffer offer) {
      if (offer == null) {
         return null;
      } else {
         RiptideMacro macro = RiptideClipboardHelper.deserializeMacroFromBase64(offer.hash);
         if (macro == null) {
            RiptideNotifications.show("Could not import macro (corrupt or unsupported).", -2075846);
            return null;
         } else {
            macro.keyCode = -1;
            String name = offer.macroName != null && !offer.macroName.isBlank() ? offer.macroName : macro.name;
            RiptideMacro imported = RiptideMacroManager.get().addImportedCopy(macro, name);
            RiptideNotifications.show("Imported macro: " + (imported != null ? imported.name : name), -13248397);
            return imported;
         }
      }
   }

   public static Packet<?> packetFromOffer(MmMessages.PacketOffer offer) {
      return offer != null && offer.data != null ? RiptideClipboardHelper.deserializePacketFromBase64(offer.data) : null;
   }

   public static RiptidePacketLoggerOverlay.LogEntry inspectableEntry(MmMessages.PacketOffer offer) {
      Packet<?> packet = packetFromOffer(offer);
      if (packet == null) {
         return null;
      } else {
         String dir = offer.direction != null && !offer.direction.isBlank() ? offer.direction : "C2S";
         return new RiptidePacketLoggerOverlay.LogEntry(
            System.currentTimeMillis(),
            0,
            dir,
            offer.friendlyName == null ? packet.getClass().getSimpleName() : offer.friendlyName,
            packet.getClass(),
            packet,
            false,
            false,
            false,
            null,
            null,
            null,
            null
         );
      }
   }

   private static String safe(String s) {
      return s == null ? "" : s;
   }
}
