package riptide.util;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.ProtocolInfo.Details;
import net.minecraft.network.ProtocolInfo.DetailsProvider;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.GameProtocols;

public final class RiptidePacketCapture {
   private static final Map<Packet<?>, RiptidePacketCapture.PacketSnapshot> PACKET_SNAPSHOTS = Collections.synchronizedMap(new WeakHashMap<>());
   private static final Map<Channel, RiptidePacketCapture.EncryptionSnapshot> ENCRYPTION_SNAPSHOTS = Collections.synchronizedMap(new WeakHashMap<>());
   private static final Map<String, Map<String, Integer>> PACKET_ID_TABLE_CACHE = new ConcurrentHashMap<>();

   private RiptidePacketCapture() {
   }

   public static void capturePlaintext(Packet<?> packet, String direction, String protocolPhase, PacketType<?> packetType, ByteBuf source) {
      if (packet != null && source != null) {
         capturePlaintextBytes(packet, direction, protocolPhase, packetType, copyReadableBytes(source));
      }
   }

   public static void capturePlaintextBytes(Packet<?> packet, String direction, String protocolPhase, PacketType<?> packetType, byte[] bytes) {
      if (packet != null && bytes != null) {
         int numericPacketId = readLeadingVarInt(bytes);
         if (numericPacketId < 0) {
            numericPacketId = resolvePacketId(protocolPhase, direction, packetType);
         }

         PACKET_SNAPSHOTS.put(
            packet,
            new RiptidePacketCapture.PacketSnapshot(
               direction,
               protocolPhase,
               packet.getClass().getName(),
               packetType == null ? "" : packetType.toString(),
               numericPacketId,
               bytes,
               System.currentTimeMillis()
            )
         );
      }
   }

   public static RiptidePacketCapture.PacketSnapshot snapshot(Packet<?> packet) {
      return packet == null ? null : PACKET_SNAPSHOTS.get(packet);
   }

   public static void markEncryptionEnabled(Channel channel) {
      if (channel != null) {
         RiptidePacketCapture.EncryptionSnapshot previous = ENCRYPTION_SNAPSHOTS.get(channel);
         ENCRYPTION_SNAPSHOTS.put(channel, previous == null ? RiptidePacketCapture.EncryptionSnapshot.newEnabled() : previous.withEnabled(true));
      }
   }

   public static void captureCiphertext(Channel channel, String direction, ByteBuf source) {
      if (channel != null && source != null) {
         RiptidePacketCapture.EncryptionSnapshot previous = ENCRYPTION_SNAPSHOTS.get(channel);
         if (previous == null) {
            previous = RiptidePacketCapture.EncryptionSnapshot.newEnabled();
         }

         ENCRYPTION_SNAPSHOTS.put(channel, previous.withCiphertext(direction, copyReadableBytes(source)));
      }
   }

   public static RiptidePacketCapture.EncryptionSnapshot encryptionSnapshot(Channel channel) {
      return channel == null ? null : ENCRYPTION_SNAPSHOTS.get(channel);
   }

   public static byte[] copyReadableBytes(ByteBuf source) {
      if (source == null) {
         return RiptideNetworkCaptureState.EMPTY_BYTES;
      } else {
         int length = Math.max(0, source.readableBytes());
         if (length == 0) {
            return RiptideNetworkCaptureState.EMPTY_BYTES;
         } else {
            byte[] bytes = new byte[length];
            source.getBytes(source.readerIndex(), bytes);
            return bytes;
         }
      }
   }

   public static String compactHex(byte[] bytes, int maxBytes) {
      return RiptidePayloadSupport.toCompactHex(bytes, maxBytes);
   }

   private static int readLeadingVarInt(byte[] bytes) {
      if (bytes != null && bytes.length != 0) {
         int value = 0;
         int shift = 0;

         for (int i = 0; i < Math.min(5, bytes.length); i++) {
            int b = bytes[i] & 255;
            value |= (b & 127) << shift;
            if ((b & 128) == 0) {
               return value;
            }

            shift += 7;
         }

         return -1;
      } else {
         return -1;
      }
   }

   public static int resolvePacketId(String protocolPhase, String direction, PacketType<?> packetType) {
      if (packetType == null) {
         return -1;
      } else {
         String phase = normalizePhase(protocolPhase);
         String flow = normalizeDirection(direction);
         if (!phase.isBlank() && !flow.isBlank()) {
            Map<String, Integer> ids = PACKET_ID_TABLE_CACHE.computeIfAbsent(phase + "|" + flow, key -> buildPacketIdTable(phase, flow));
            Integer id = ids.get(packetType.toString());
            return id == null ? -1 : id;
         } else {
            return -1;
         }
      }
   }

   private static Map<String, Integer> buildPacketIdTable(String phase, String flow) {
      DetailsProvider provider = detailsProvider(phase, flow);
      if (provider == null) {
         return Map.of();
      } else {
         Map<String, Integer> ids = new HashMap<>();

         try {
            Details details = provider.details();
            details.listPackets((type, networkId) -> {
               if (type != null && networkId >= 0) {
                  ids.put(type.toString(), networkId);
               }
            });
         } catch (Throwable var5) {
         }

         return ids.isEmpty() ? Map.of() : Map.copyOf(ids);
      }
   }

   private static DetailsProvider detailsProvider(String phase, String flow) {
      boolean c2s = "C2S".equals(flow);
      if ("CONFIGURATION".equals(phase)) {
         return c2s ? ConfigurationProtocols.SERVERBOUND_TEMPLATE : ConfigurationProtocols.CLIENTBOUND_TEMPLATE;
      } else if ("PLAY".equals(phase)) {
         return (DetailsProvider)(c2s ? GameProtocols.SERVERBOUND_TEMPLATE : GameProtocols.CLIENTBOUND_TEMPLATE);
      } else {
         return null;
      }
   }

   private static String normalizePhase(String protocolPhase) {
      if (protocolPhase != null && !protocolPhase.isBlank()) {
         String value = protocolPhase.strip().toUpperCase(Locale.ROOT);
         if (value.contains("CONFIG")) {
            return "CONFIGURATION";
         } else {
            return !value.contains("PLAY") && !value.contains("GAME") ? value : "PLAY";
         }
      } else {
         return "";
      }
   }

   private static String normalizeDirection(String direction) {
      if (direction != null && !direction.isBlank()) {
         String value = direction.strip().toUpperCase(Locale.ROOT);
         if (value.contains("SERVERBOUND") || value.contains("OUT") || value.contains("C2S")) {
            return "C2S";
         } else {
            return !value.contains("CLIENTBOUND") && !value.contains("IN") && !value.contains("S2C") ? value : "S2C";
         }
      } else {
         return "";
      }
   }

   public record EncryptionSnapshot(
      boolean enabled, long enabledAtMs, byte[] lastInboundCiphertext, byte[] lastOutboundCiphertext, long lastInboundAtMs, long lastOutboundAtMs
   ) {
      static RiptidePacketCapture.EncryptionSnapshot newEnabled() {
         return new RiptidePacketCapture.EncryptionSnapshot(true, System.currentTimeMillis(), new byte[0], new byte[0], 0L, 0L);
      }

      RiptidePacketCapture.EncryptionSnapshot withEnabled(boolean value) {
         return new RiptidePacketCapture.EncryptionSnapshot(
            value,
            value && this.enabledAtMs == 0L ? System.currentTimeMillis() : this.enabledAtMs,
            this.lastInboundCiphertext,
            this.lastOutboundCiphertext,
            this.lastInboundAtMs,
            this.lastOutboundAtMs
         );
      }

      RiptidePacketCapture.EncryptionSnapshot withCiphertext(String direction, byte[] bytes) {
         long now = System.currentTimeMillis();
         return !"C2S".equalsIgnoreCase(direction) && !"OUT".equalsIgnoreCase(direction)
            ? new RiptidePacketCapture.EncryptionSnapshot(
               this.enabled, this.enabledAtMs, bytes == null ? new byte[0] : (byte[])bytes.clone(), this.lastOutboundCiphertext, now, this.lastOutboundAtMs
            )
            : new RiptidePacketCapture.EncryptionSnapshot(
               this.enabled, this.enabledAtMs, this.lastInboundCiphertext, bytes == null ? new byte[0] : (byte[])bytes.clone(), this.lastInboundAtMs, now
            );
      }

      public byte[] lastInboundCiphertext() {
         return this.lastInboundCiphertext == null ? new byte[0] : (byte[])this.lastInboundCiphertext.clone();
      }

      public byte[] lastOutboundCiphertext() {
         return this.lastOutboundCiphertext == null ? new byte[0] : (byte[])this.lastOutboundCiphertext.clone();
      }
   }

   public record PacketSnapshot(
      String direction, String protocolPhase, String packetClassName, String packetType, int numericPacketId, byte[] plaintextBytes, long capturedAtMs
   ) {
      public byte[] plaintextBytes() {
         return this.plaintextBytes == null ? new byte[0] : (byte[])this.plaintextBytes.clone();
      }
   }
}
