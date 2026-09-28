package riptide.util.macro;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import riptide.modules.PackHideState;
import riptide.util.RiptidePacketNamer;
import riptide.util.RiptidePacketRegistry;
import riptide.util.RiptideSharedState;

public final class PacketGateManager {
   private static final ConcurrentHashMap<String, PacketGateManager.Gate> GATES = new ConcurrentHashMap<>();
   private static volatile String lastOpenedGateKey = null;

   private PacketGateManager() {
   }

   public static void install(PacketGateAction action) {
      install(action, 0L);
   }

   public static void install(PacketGateAction action, long ownerRunId) {
      if (!PackHideState.isHardLocked()) {
         installOwned(action, ownerRunId);
      }
   }

   static void installOwned(PacketGateAction action, long ownerRunId) {
      if (action != null) {
         ownerRunId = Math.max(0L, ownerRunId);
         String id = action.gateId != null && !action.gateId.isBlank() ? action.gateId.trim() : "auto";
         if (action.mode == PacketGateAction.GateMode.DISABLE_GATE) {
            disable(id, ownerRunId);
         } else {
            String key = gateKey(ownerRunId, id);
            GATES.put(key, new PacketGateManager.Gate(ownerRunId, id, action.mode, action.effectivePackets(), action.flushOnDisable));
            lastOpenedGateKey = key;
         }
      }
   }

   public static void disable(String id) {
      if (id != null && !id.isBlank() && !id.equalsIgnoreCase("all")) {
         String target = id.trim();
         GATES.entrySet().removeIf(entry -> entry.getValue().id.equalsIgnoreCase(target));
         refreshLastOpenedGate();
      } else {
         clearAll();
      }
   }

   public static void disable(String id, long ownerRunId) {
      if (ownerRunId <= 0L) {
         disable(id);
      } else {
         if (id != null && !id.isBlank() && !id.equalsIgnoreCase("all")) {
            GATES.remove(gateKey(ownerRunId, id));
         } else {
            GATES.entrySet().removeIf(entry -> entry.getValue().ownerRunId == ownerRunId);
         }

         refreshLastOpenedGate();
      }
   }

   public static void disableAndFlushConfigured(String id, ClientPacketListener connection, boolean flushRequested) {
      disableAndFlushConfigured(id, 0L, connection, flushRequested);
   }

   public static void disableAndFlushConfigured(String id, long ownerRunId, ClientPacketListener connection, boolean flushRequested) {
      if (PackHideState.isHardLocked()) {
         disable(id, ownerRunId);
      } else if (ownerRunId > 0L || id != null && !id.isBlank() && !id.equalsIgnoreCase("all")) {
         ArrayList<PacketGateManager.Gate> removed = new ArrayList<>();
         if (id == null || id.isBlank() || id.equalsIgnoreCase("all")) {
            GATES.entrySet().removeIf(entry -> {
               if (entry.getValue().ownerRunId != ownerRunId) {
                  return false;
               } else {
                  removed.add(entry.getValue());
                  return true;
               }
            });
         } else if (ownerRunId > 0L) {
            PacketGateManager.Gate gate = GATES.remove(gateKey(ownerRunId, id));
            if (gate != null) {
               removed.add(gate);
            }
         } else {
            String target = id.trim();
            GATES.entrySet().removeIf(entry -> {
               if (!entry.getValue().id.equalsIgnoreCase(target)) {
                  return false;
               } else {
                  removed.add(entry.getValue());
                  return true;
               }
            });
         }

         refreshLastOpenedGate();
         boolean shouldFlush = flushRequested || removed.stream().anyMatch(gate -> gate.flushOnDisable);
         if (shouldFlush && !hasActiveDelayGate()) {
            RiptideSharedState.get().flushDelayedPackets(connection);
         }
      } else {
         clearAllAndFlushConfigured(connection);
      }
   }

   public static void disableCurrentGate() {
      if (PackHideState.isHardLocked()) {
         clearAll();
      } else {
         String key = lastOpenedGateKey;
         if (key != null) {
            PacketGateManager.Gate gate = GATES.get(key);
            if (gate != null) {
               disableAndFlushConfigured(gate.id, gate.ownerRunId, Minecraft.getInstance().getConnection(), true);
            }
         }
      }
   }

   public static void clearAll() {
      GATES.clear();
      lastOpenedGateKey = null;
   }

   public static void clearAllAndFlushConfigured(ClientPacketListener connection) {
      if (PackHideState.isHardLocked()) {
         clearAll();
      } else {
         boolean flush = GATES.values().stream().anyMatch(g -> g.mode == PacketGateAction.GateMode.DELAY && g.flushOnDisable);
         clearAll();
         if (flush) {
            RiptideSharedState.get().flushDelayedPackets(connection);
         }
      }
   }

   public static void clearOwnerAndFlushConfigured(long ownerRunId, ClientPacketListener connection) {
      if (ownerRunId > 0L) {
         disableAndFlushConfigured("all", ownerRunId, connection, false);
      }
   }

   private static boolean hasActiveDelayGate() {
      return GATES.values().stream().anyMatch(g -> g.mode == PacketGateAction.GateMode.DELAY);
   }

   private static String newestGateKeyOrNull() {
      String newest = null;

      for (String key : GATES.keySet()) {
         newest = key;
      }

      return newest;
   }

   private static void refreshLastOpenedGate() {
      String current = lastOpenedGateKey;
      if (current == null || !GATES.containsKey(current)) {
         lastOpenedGateKey = newestGateKeyOrNull();
      }
   }

   private static String gateKey(long ownerRunId, String id) {
      String normalized = id != null && !id.isBlank() ? id.trim().toLowerCase(Locale.ROOT) : "auto";
      return ownerRunId + "\u0000" + normalized;
   }

   static int activeGateCountForOwner(long ownerRunId) {
      int count = 0;

      for (PacketGateManager.Gate gate : GATES.values()) {
         if (gate.ownerRunId == ownerRunId) {
            count++;
         }
      }

      return count;
   }

   public static PacketGateManager.Result handle(Packet<?> packet, String direction) {
      if (PackHideState.isHardLocked()) {
         return PacketGateManager.Result.PASS;
      } else if (packet != null && !GATES.isEmpty()) {
         if (isTransactionSync(packet)) {
            return PacketGateManager.Result.PASS;
         } else {
            PacketGateManager.Result result = PacketGateManager.Result.PASS;

            for (PacketGateManager.Gate gate : GATES.values()) {
               boolean packetMatches = gate.matchesAll || anyNormalizedMatch(gate.normalizedPackets, packet, direction);
               switch (gate.mode) {
                  case CANCEL:
                     if (packetMatches) {
                        return PacketGateManager.Result.CANCEL;
                     }
                     break;
                  case DELAY:
                     if (packetMatches) {
                        result = PacketGateManager.Result.DELAY;
                     }
                     break;
                  case ALLOW_ONLY:
                     if (!packetMatches) {
                        return PacketGateManager.Result.CANCEL;
                     }
                  case DISABLE_GATE:
               }
            }

            return result;
         }
      } else {
         return PacketGateManager.Result.PASS;
      }
   }

   private static boolean isTransactionSync(Packet<?> packet) {
      return packet instanceof ServerboundPongPacket
         || packet instanceof ClientboundPingPacket
         || packet instanceof ServerboundKeepAlivePacket
         || packet instanceof ClientboundKeepAlivePacket;
   }

   private static boolean anyNormalizedMatch(String[] normalizedPatterns, Packet<?> packet, String direction) {
      for (int i = 0; i < normalizedPatterns.length; i++) {
         if (matchesNormalized(normalizedPatterns[i], packet, direction)) {
            return true;
         }
      }

      return false;
   }

   public static boolean hasActiveGates() {
      return PackHideState.isHardLocked() ? false : !GATES.isEmpty();
   }

   public static boolean matchesPacket(String expected, Packet<?> packet, String direction) {
      return expected != null && !expected.isBlank() ? matchesNormalized(normalize(expected), packet, direction) : true;
   }

   private static boolean matchesNormalized(String want, Packet<?> packet, String direction) {
      if (want.isEmpty()) {
         return false;
      } else if (contains(want, normalize(RiptidePacketNamer.getFriendlyName(packet, direction)))) {
         return true;
      } else if (contains(want, normalize(RiptidePacketNamer.getFriendlyName(packet)))) {
         return true;
      } else if (contains(want, normalize(packet.getClass().getSimpleName()))) {
         return true;
      } else {
         String registry = normalize(RiptidePacketRegistry.getName(packet.getClass()));
         return contains(want, registry);
      }
   }

   private static boolean contains(String a, String b) {
      return !a.isEmpty() && !b.isEmpty() && (a.equals(b) || a.endsWith(b) || b.endsWith(a));
   }

   private static String normalize(String value) {
      return RiptidePacketNamer.normalizePacketKey(value);
   }

   public static final class Gate {
      public final long ownerRunId;
      public final String id;
      public final PacketGateAction.GateMode mode;
      public final ArrayList<String> packets;
      public final boolean flushOnDisable;
      final String[] normalizedPackets;
      final boolean matchesAll;

      Gate(long ownerRunId, String id, PacketGateAction.GateMode mode, ArrayList<String> packets, boolean flushOnDisable) {
         this.ownerRunId = ownerRunId;
         this.id = id;
         this.mode = mode;
         this.packets = packets;
         this.flushOnDisable = flushOnDisable;
         boolean all = packets.isEmpty();
         String[] normalized = new String[packets.size()];

         for (int i = 0; i < packets.size(); i++) {
            String pattern = packets.get(i);
            if (pattern != null && !pattern.isBlank()) {
               normalized[i] = PacketGateManager.normalize(pattern);
            } else {
               all = true;
               normalized[i] = "";
            }
         }

         this.matchesAll = all;
         this.normalizedPackets = normalized;
      }
   }

   public static enum Result {
      PASS,
      CANCEL,
      DELAY;
   }
}
