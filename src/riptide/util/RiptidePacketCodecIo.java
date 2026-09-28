package riptide.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;

public final class RiptidePacketCodecIo {
   private static final Minecraft MC = Minecraft.getInstance();

   private RiptidePacketCodecIo() {
   }

   public static RiptidePacketCodecIo.DecodedPacket decodeFullPacket(Class<? extends Packet<?>> packetClass, byte[] plaintextBytes, String protocolPhase) {
      ByteBuf buf = Unpooled.wrappedBuffer(plaintextBytes == null ? new byte[0] : plaintextBytes);

      RiptidePacketCodecIo.DecodedPacket var7;
      try {
         int packetId = readVarInt(buf);
         byte[] body = new byte[buf.readableBytes()];
         if (body.length > 0) {
            buf.readBytes(body);
         }

         Packet<?> packet = decodeBody(packetClass, body, protocolPhase);
         var7 = new RiptidePacketCodecIo.DecodedPacket(packet, packetId, body, "minecraft full packet STREAM_CODEC");
      } finally {
         buf.release();
      }

      return var7;
   }

   public static Packet<?> decodeBody(Class<? extends Packet<?>> packetClass, byte[] bodyBytes, String protocolPhase) {
      if (packetClass == null) {
         throw new IllegalArgumentException("Packet class is missing.");
      } else {
         byte[] body = bodyBytes == null ? new byte[0] : (byte[])bodyBytes.clone();
         if (packetClass != ServerboundCustomPayloadPacket.class && packetClass != ClientboundCustomPayloadPacket.class) {
            List<RiptidePacketCodecIo.CodecCandidate> codecs = findStreamCodecs(packetClass, protocolPhase);
            if (codecs.isEmpty()) {
               throw new IllegalArgumentException("No STREAM_CODEC found for " + packetClass.getName() + ".");
            } else {
               Throwable firstFailure = null;

               for (RiptidePacketCodecIo.CodecCandidate candidate : codecs) {
                  for (RiptidePacketCodecIo.BufferFactory factory : codecBufferFactories(body)) {
                     ByteBuf buffer = factory.create();

                     Packet var13;
                     try {
                        Object decoded = candidate.decode(buffer);
                        if (!(decoded instanceof Packet<?> packet)) {
                           throw new IllegalArgumentException(
                              "Codec " + candidate.name + " returned " + (decoded == null ? "null" : decoded.getClass().getName()) + ", not a packet."
                           );
                        }

                        if (!packetClass.isInstance(packet)) {
                           throw new IllegalArgumentException(
                              "Codec " + candidate.name + " returned " + packet.getClass().getName() + ", expected " + packetClass.getName() + "."
                           );
                        }

                        if (buffer.readableBytes() > 0) {
                           throw new IllegalArgumentException("Codec " + candidate.name + " left " + buffer.readableBytes() + " trailing byte(s).");
                        }

                        var13 = packet;
                     } catch (Throwable var17) {
                        if (firstFailure == null) {
                           firstFailure = var17;
                        }
                        continue;
                     } finally {
                        buffer.release();
                     }

                     return var13;
                  }
               }

               throw new IllegalArgumentException(safeMessage(firstFailure));
            }
         } else {
            return decodeRawCustomPayload(packetClass, body);
         }
      }
   }

   public static byte[] stripPacketId(byte[] plaintextBytes) {
      ByteBuf buf = Unpooled.wrappedBuffer(plaintextBytes == null ? new byte[0] : plaintextBytes);

      byte[] var3;
      try {
         readVarInt(buf);
         byte[] body = new byte[buf.readableBytes()];
         if (body.length > 0) {
            buf.readBytes(body);
         }

         var3 = body;
      } finally {
         buf.release();
      }

      return var3;
   }

   private static List<RiptidePacketCodecIo.CodecCandidate> findStreamCodecs(Class<?> packetClass, String protocolPhase) {
      List<RiptidePacketCodecIo.CodecCandidate> candidates = new ArrayList<>();

      for (Class<?> cursor = packetClass; cursor != null && cursor != Object.class; cursor = cursor.getSuperclass()) {
         for (Field field : cursor.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && StreamCodec.class.isAssignableFrom(field.getType())) {
               try {
                  field.setAccessible(true);
                  if (field.get(null) instanceof StreamCodec codec) {
                     candidates.add(new RiptidePacketCodecIo.CodecCandidate(field.getName(), codec));
                  }
               } catch (Throwable var10) {
               }
            }
         }
      }

      String phase = protocolPhase == null ? "" : protocolPhase.toLowerCase(Locale.ROOT);
      candidates.sort(Comparator.comparingInt(candidate -> codecPriority(candidate.name, phase)));
      return candidates;
   }

   private static int codecPriority(String fieldName, String phase) {
      String name = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT);
      if (phase.contains("config") && name.contains("config")) {
         return 0;
      } else if (!phase.contains("play") && !phase.contains("game") || !name.contains("game") && !name.equals("stream_codec")) {
         if (name.equals("stream_codec")) {
            return 1;
         } else if (name.contains("game")) {
            return 2;
         } else {
            return name.contains("config") ? 3 : 4;
         }
      } else {
         return 0;
      }
   }

   private static List<RiptidePacketCodecIo.BufferFactory> codecBufferFactories(byte[] bodyBytes) {
      byte[] bytes = bodyBytes == null ? new byte[0] : (byte[])bodyBytes.clone();
      List<RiptidePacketCodecIo.BufferFactory> out = new ArrayList<>();
      RegistryAccess access = registryAccess();
      if (access != null) {
         out.add(() -> new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer((byte[])bytes.clone()), access));
      }

      out.add(() -> new FriendlyByteBuf(Unpooled.wrappedBuffer((byte[])bytes.clone())));
      out.add(() -> Unpooled.wrappedBuffer((byte[])bytes.clone()));
      return out;
   }

   private static RegistryAccess registryAccess() {
      try {
         if (MC != null && MC.level != null) {
            return MC.level.registryAccess();
         }

         if (MC != null && MC.getConnection() != null) {
            return MC.getConnection().registryAccess();
         }
      } catch (Throwable var1) {
      }

      return null;
   }

   private static Packet<?> decodeRawCustomPayload(Class<? extends Packet<?>> packetClass, byte[] body) {
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(body == null ? new byte[0] : body));

      ServerboundCustomPayloadPacket var6;
      try {
         Identifier channel = buf.readIdentifier();
         byte[] payload = new byte[buf.readableBytes()];
         if (payload.length > 0) {
            buf.readBytes(payload);
         }

         RiptidePayloadSupport.RawCustomPacketPayload rawPayload = new RiptidePayloadSupport.RawCustomPacketPayload(channel, payload);
         RiptidePayloadSupport.rememberUnknownPayloadBytes(rawPayload, payload);
         if (packetClass != ServerboundCustomPayloadPacket.class) {
            return new ClientboundCustomPayloadPacket(rawPayload);
         }

         RiptidePayloadChannelSubscriptionManager.rememberRequestedChannel(channel.toString());
         var6 = new ServerboundCustomPayloadPacket(rawPayload);
      } catch (Throwable var10) {
         throw new IllegalArgumentException("Custom payload body must be <channel identifier><payload bytes>: " + safeMessage(var10));
      } finally {
         buf.release();
      }

      return var6;
   }

   private static int readVarInt(ByteBuf buf) {
      int value = 0;
      int position = 0;

      while (buf.isReadable()) {
         byte currentByte = buf.readByte();
         value |= (currentByte & 127) << position;
         if ((currentByte & 128) == 0) {
            return value;
         }

         position += 7;
         if (position >= 32) {
            throw new IllegalArgumentException("Packet id VarInt is too long.");
         }
      }

      throw new IllegalArgumentException("Full packet bytes are missing the packet id VarInt.");
   }

   private static String safeMessage(Throwable t) {
      if (t == null) {
         return "unknown";
      } else {
         String message = t.getMessage();
         return message != null && !message.isBlank() ? message : t.getClass().getSimpleName();
      }
   }

   private interface BufferFactory {
      ByteBuf create();
   }

   private record CodecCandidate(String name, StreamCodec<ByteBuf, ?> codec) {
      Object decode(ByteBuf buffer) {
         return this.codec.decode(buffer);
      }
   }

   public record DecodedPacket(Packet<?> packet, int packetId, byte[] bodyBytes, String source) {
      public DecodedPacket(Packet<?> packet, int packetId, byte[] bodyBytes, String source) {
         bodyBytes = bodyBytes == null ? new byte[0] : (byte[])bodyBytes.clone();
         source = source != null && !source.isBlank() ? source : "minecraft STREAM_CODEC";
         this.packet = packet;
         this.packetId = packetId;
         this.bodyBytes = bodyBytes;
         this.source = source;
      }

      public byte[] bodyBytes() {
         return (byte[])this.bodyBytes.clone();
      }
   }
}
