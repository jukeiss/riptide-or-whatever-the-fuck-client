package riptide.mixin.security;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import riptide.security.RiptideProtector;
import riptide.security.RiptideProtectorPacketContext;

@Mixin({PacketDecoder.class})
public class RiptideProtectorPacketDecoderMixin {
   @WrapOperation(
      method = {"decode"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/codec/StreamCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;"
      )}
   )
   private Object riptide$wrapDecode(StreamCodec instance, Object buffer, Operation<Object> original) {
      if (!RiptideProtector.shouldTagPacketComponents()) {
         return riptide$decodeCompatibly(instance, buffer, original);
      } else {
         RiptideProtectorPacketContext.setProcessingPacket(true);

         Object var4;
         try {
            var4 = riptide$decodeCompatibly(instance, buffer, original);
         } finally {
            RiptideProtectorPacketContext.setProcessingPacket(false);
         }

         return var4;
      }
   }

   @Unique
   private static Object riptide$decodeCompatibly(StreamCodec instance, Object buffer, Operation<Object> original) {
      if (buffer instanceof ByteBuf byteBuf) {
         int startIndex = byteBuf.readerIndex();

         try {
            return original.call(new Object[]{instance, buffer});
         } catch (DecoderException var9) {
            DecoderException decodeFailure = var9;
            if (riptide$isMissingLoginSessionId(var9)) {
               byteBuf.readerIndex(startIndex);

               try {
                  riptide$readVarInt(byteBuf);
                  GameProfile profile = (GameProfile)ByteBufCodecs.GAME_PROFILE.decode(byteBuf);
                  if (byteBuf.isReadable()) {
                     byteBuf.readerIndex(startIndex);
                     throw decodeFailure;
                  } else {
                     return new ClientboundLoginFinishedPacket(profile, UUID.randomUUID());
                  }
               } catch (RuntimeException var7) {
                  byteBuf.readerIndex(startIndex);
                  throw var9;
               }
            } else if (riptide$isTruncatedEntityData(var9)) {
               byteBuf.readerIndex(startIndex);

               try {
                  int entityId = riptide$readVarInt(byteBuf);
                  return new ClientboundSetEntityDataPacket(entityId, List.of());
               } catch (RuntimeException var8) {
                  byteBuf.readerIndex(startIndex);
                  throw var9;
               }
            } else {
               throw var9;
            }
         }
      } else {
         return original.call(new Object[]{instance, buffer});
      }
   }

   @Unique
   private static boolean riptide$isTruncatedEntityData(DecoderException failure) {
      String message = failure.getMessage();
      if (message != null && message.contains("clientbound/minecraft:set_entity_data")) {
         for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof IndexOutOfBoundsException) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   @Unique
   private static boolean riptide$isMissingLoginSessionId(DecoderException failure) {
      String message = failure.getMessage();
      if (message != null && message.contains("clientbound/minecraft:login_finished")) {
         for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof IndexOutOfBoundsException) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   @Unique
   private static int riptide$readVarInt(ByteBuf buffer) {
      int value = 0;

      for (int byteIndex = 0; byteIndex < 5; byteIndex++) {
         int current = buffer.readByte();
         value |= (current & 127) << byteIndex * 7;
         if ((current & 128) == 0) {
            return value;
         }
      }

      throw new DecoderException("VarInt is too big");
   }
}
