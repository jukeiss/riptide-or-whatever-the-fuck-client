package riptide.util;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import riptide.gui.macro.editor.ActionEditorOverlay;
import riptide.util.macro.PayloadAction;
import riptide.util.macro.SendPacketAction;
import riptide.util.macro.WaitForPacketAction;

public final class RiptidePacketEntryActions {
   private RiptidePacketEntryActions() {
   }

   public static boolean canQueue(RiptidePacketLoggerOverlay.LogEntry entry) {
      return entry != null && "C2S".equalsIgnoreCase(entry.direction) && entry.packetRef != null;
   }

   public static boolean canDirectSend(RiptidePacketLoggerOverlay.LogEntry entry) {
      return canQueue(entry);
   }

   public static boolean directSend(RiptidePacketLoggerOverlay.LogEntry entry) {
      if (!canDirectSend(entry)) {
         RiptideClientMessaging.sendPrefixed("§cOnly C2S packets can be sent.");
         return false;
      } else if (entry.packetRef instanceof ServerboundCustomPayloadPacket) {
         return directSendPayload(entry);
      } else {
         Packet<?> regenerated = PacketRegenerator.regenerate(entry.packetRef);
         if (regenerated == null) {
            RiptideClientMessaging.sendPrefixed("§cCannot regenerate: " + entry.shortName);
            return false;
         } else {
            try {
               RiptidePacketSender.send(regenerated);
               RiptideClientMessaging.sendPrefixed("Sent: " + entry.shortName);
               return true;
            } catch (Exception var3) {
               RiptideClientMessaging.sendPrefixed("§cSend failed: " + var3.getMessage());
               return false;
            }
         }
      }
   }

   private static boolean directSendPayload(RiptidePacketLoggerOverlay.LogEntry entry) {
      RiptidePayloadSupport.PayloadSnapshot snapshot = RiptidePayloadSupport.snapshotFromEntry(entry);
      if (snapshot != null && snapshot.channel() != null && !snapshot.channel().isBlank()) {
         byte[] rawBytes = snapshot.rawBytes();
         if (!RiptidePayloadSupport.sendPayload(snapshot.channel(), rawBytes, snapshot.protocolPhase())) {
            RiptideClientMessaging.sendPrefixed("§cFailed to send payload: " + entry.shortName);
            return false;
         } else {
            RiptideClientMessaging.sendPrefixed("Sent payload: " + snapshot.channel());
            return true;
         }
      } else {
         Packet<?> regenerated = PacketRegenerator.regenerate(entry.packetRef);
         if (regenerated != null) {
            try {
               RiptidePacketSender.send(regenerated);
               RiptideClientMessaging.sendPrefixed("Sent: " + entry.shortName);
               return true;
            } catch (Exception var4) {
               RiptideClientMessaging.sendPrefixed("§cSend failed: " + var4.getMessage());
               return false;
            }
         } else {
            RiptideClientMessaging.sendPrefixed("§cCannot send: no payload data for " + entry.shortName);
            return false;
         }
      }
   }

   public static boolean canAddSendAction(RiptidePacketLoggerOverlay.LogEntry entry) {
      return canQueue(entry);
   }

   public static boolean canAddWaitAction(RiptidePacketLoggerOverlay.LogEntry entry) {
      return entry != null && entry.shortName != null && !entry.shortName.isBlank() && entry.direction != null && !entry.direction.isBlank();
   }

   public static boolean canEditPayload(RiptidePacketLoggerOverlay.LogEntry entry) {
      return entry != null && entry.isPayload && "C2S".equalsIgnoreCase(entry.direction);
   }

   public static boolean canAddPayloadAction(RiptidePacketLoggerOverlay.LogEntry entry) {
      return canEditPayload(entry);
   }

   public static boolean queue(RiptidePacketLoggerOverlay.LogEntry entry) {
      if (!canQueue(entry)) {
         RiptideClientMessaging.sendPrefixed("§cOnly C2S packets can be queued.");
         return false;
      } else {
         RiptideSharedState.get().enqueuePacket(entry.packetRef);
         RiptideClientMessaging.sendPrefixed("Queued: " + entry.shortName);
         return true;
      }
   }

   public static boolean addSendActionToVisibleMacro(RiptidePacketLoggerOverlay.LogEntry entry) {
      if (!canAddSendAction(entry)) {
         RiptideClientMessaging.sendPrefixed("§cOnly C2S packets can be added as send actions.");
         return false;
      } else {
         RiptideMacroEditorOverlay macroEditor = getOrOpenMacroEditor();
         if (macroEditor == null) {
            RiptideClientMessaging.sendPrefixed("§cCannot open the macro editor.");
            return false;
         } else {
            Packet<?> regenerated = PacketRegenerator.regenerate(entry.packetRef);
            if (regenerated == null) {
               RiptideClientMessaging.sendPrefixed("§cCannot regenerate: " + entry.shortName);
               return false;
            } else {
               SendPacketAction action = new SendPacketAction();
               action.waitForGuiBefore = false;
               action.waitForGuiAfter = false;
               action.guiName = "";
               action.packets.add(new RiptideSharedState.QueuedPacket(regenerated, 0));
               macroEditor.addAction(action);
               RiptideOverlayManager.get().bringToFront(macroEditor);
               RiptideClientMessaging.sendPrefixed("Added send action: " + entry.shortName);
               return true;
            }
         }
      }
   }

   public static boolean addWaitActionToVisibleMacro(RiptidePacketLoggerOverlay.LogEntry entry) {
      if (!canAddWaitAction(entry)) {
         RiptideClientMessaging.sendPrefixed("§cCannot add this packet as a wait condition.");
         return false;
      } else {
         RiptideMacroEditorOverlay macroEditor = getOrOpenMacroEditor();
         if (macroEditor == null) {
            RiptideClientMessaging.sendPrefixed("§cCannot open the macro editor.");
            return false;
         } else {
            String target = WaitForPacketAction.withDirection(entry.direction, entry.shortName);
            if (target.isEmpty()) {
               RiptideClientMessaging.sendPrefixed("§cCannot add this packet as a wait condition.");
               return false;
            } else {
               WaitForPacketAction action = new WaitForPacketAction(target);
               action.packetNames.add(target);
               macroEditor.addAction(action);
               RiptideOverlayManager.get().bringToFront(macroEditor);
               RiptideClientMessaging.sendPrefixed("Added wait condition: " + WaitForPacketAction.getDisplayLabel(target));
               return true;
            }
         }
      }
   }

   public static boolean openPayloadEditor(RiptidePacketLoggerOverlay.LogEntry entry) {
      if (!canEditPayload(entry)) {
         RiptideClientMessaging.sendPrefixed("§cOnly captured C2S custom payload packets can be edited.");
         return false;
      } else {
         PayloadAction action = RiptidePayloadSupport.seedActionFromEntry(entry);
         if (action == null) {
            RiptideClientMessaging.sendPrefixed("§cFailed to seed payload editor from packet.");
            return false;
         } else {
            ActionEditorOverlay.getSharedOverlay().openStandalonePayloadEditor(action);
            RiptideOverlayManager.get().bringToFront(ActionEditorOverlay.getSharedOverlay());
            return true;
         }
      }
   }

   public static boolean addPayloadActionToVisibleMacro(RiptidePacketLoggerOverlay.LogEntry entry) {
      if (!canAddPayloadAction(entry)) {
         RiptideClientMessaging.sendPrefixed("§cOnly captured C2S custom payload packets can be added as payload actions.");
         return false;
      } else {
         RiptideMacroEditorOverlay macroEditor = getOrOpenMacroEditor();
         if (macroEditor == null) {
            RiptideClientMessaging.sendPrefixed("§cCannot open the macro editor.");
            return false;
         } else {
            PayloadAction action = RiptidePayloadSupport.seedActionFromEntry(entry);
            if (action == null) {
               RiptideClientMessaging.sendPrefixed("§cFailed to create payload action from packet.");
               return false;
            } else {
               macroEditor.addAction(action);
               RiptideOverlayManager.get().bringToFront(macroEditor);
               RiptideClientMessaging.sendPrefixed("Added payload action: " + entry.shortName);
               return true;
            }
         }
      }
   }

   public static boolean hasVisibleMacroEditor() {
      return findVisibleMacroEditor() != null;
   }

   private static RiptideMacroEditorOverlay getOrOpenMacroEditor() {
      RiptideMacroEditorOverlay macroEditor = findVisibleMacroEditor();
      if (macroEditor != null) {
         RiptideOverlayManager.get().bringToFront(macroEditor);
         return macroEditor;
      } else {
         macroEditor = RiptideMacroEditorOverlay.getSharedOverlay();
         if (macroEditor == null) {
            return null;
         } else {
            RiptideMacro existingMacro = RiptideSharedState.get().getEditingMacro();
            macroEditor.open(existingMacro);
            RiptideOverlayManager.get().bringToFront(macroEditor);
            return macroEditor;
         }
      }
   }

   private static RiptideMacroEditorOverlay findVisibleMacroEditor() {
      for (IRiptideOverlay overlay : RiptideOverlayManager.get().getOverlays()) {
         if (overlay instanceof RiptideMacroEditorOverlay macroEditor && macroEditor.isVisible()) {
            return macroEditor;
         }
      }

      return null;
   }
}
