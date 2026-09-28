package riptide.mixin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.PackHideState;
import riptide.util.RiptideNetworkCaptureState;
import riptide.util.RiptidePayloadSupport;

@Mixin(
   targets = {"net.minecraft.network.protocol.common.custom.CustomPacketPayload$1"}
)
public abstract class RiptideCustomPayloadCodecMixin {
   @Unique
   private static final ThreadLocal<RiptideCustomPayloadCodecMixin.CaptureCursor> RIPTIDE_CAPTURE_CURSOR = ThreadLocal.withInitial(
      RiptideCustomPayloadCodecMixin.CaptureCursor::new
   );

   @Inject(
      method = {"encode(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$encodeRawCustomPacketPayload(FriendlyByteBuf buf, CustomPacketPayload payload, CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         long captureState = RiptideNetworkCaptureState.codecState();
         boolean capturePayload = RiptideNetworkCaptureState.capturesPayloads(captureState);
         if (capturePayload && buf != null) {
            RiptideCustomPayloadCodecMixin.CaptureCursor cursor = RIPTIDE_CAPTURE_CURSOR.get();
            cursor.encodeStartIndex = buf.writerIndex();
            cursor.encodeState = captureState;
         }

         boolean isRaw = payload instanceof RiptidePayloadSupport.RawCustomPacketPayload;
         if (isRaw || capturePayload) {
            byte[] rememberedBytes = RiptidePayloadSupport.getRememberedUnknownPayloadBytes(payload);
            if (rememberedBytes != null || isRaw) {
               Type<?> type = payload.type();
               if (type != null && type.id() != null) {
                  byte[] bytes = rememberedBytes != null ? rememberedBytes : ((RiptidePayloadSupport.RawCustomPacketPayload)payload).bytes();
                  buf.writeIdentifier(type.id());
                  if (bytes.length > 0) {
                     buf.writeBytes(bytes);
                  }

                  ci.cancel();
               }
            }
         }
      }
   }

   @Inject(
      method = {"encode(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"},
      at = {@At("TAIL")}
   )
   private void riptide$captureEncodedCustomPacketPayload(FriendlyByteBuf buf, CustomPacketPayload payload, CallbackInfo ci) {
      long captureState = RiptideNetworkCaptureState.codecState();
      if (RiptideNetworkCaptureState.capturesPayloads(captureState)) {
         RiptideCustomPayloadCodecMixin.CaptureCursor cursor = RIPTIDE_CAPTURE_CURSOR.get();
         int startIndex = cursor.encodeState == captureState ? cursor.encodeStartIndex : -1;
         cursor.encodeStartIndex = -1;
         cursor.encodeState = 0L;
         if (payload != null && buf != null && startIndex >= 0) {
            int end = buf.writerIndex();
            if (end > startIndex) {
               byte[] encodedBytes = new byte[end - startIndex];
               buf.getBytes(startIndex, encodedBytes);
               RiptidePayloadSupport.rememberDecodedPayloadBytes(payload, riptide$payloadChannel(payload), encodedBytes);
            }
         }
      }
   }

   @Inject(
      method = {"decode(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;"},
      at = {@At("HEAD")}
   )
   private void riptide$captureDecodeStart(FriendlyByteBuf buf, CallbackInfoReturnable<CustomPacketPayload> cir) {
      long captureState = RiptideNetworkCaptureState.codecState();
      if (RiptideNetworkCaptureState.capturesPayloads(captureState) && buf != null) {
         RiptideCustomPayloadCodecMixin.CaptureCursor cursor = RIPTIDE_CAPTURE_CURSOR.get();
         cursor.decodeStartIndex = buf.readerIndex();
         cursor.decodeState = captureState;
      }
   }

   @Inject(
      method = {"decode(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;"},
      at = {@At("RETURN")}
   )
   private void riptide$captureDecodedCustomPacketPayload(FriendlyByteBuf buf, CallbackInfoReturnable<CustomPacketPayload> cir) {
      long captureState = RiptideNetworkCaptureState.codecState();
      if (RiptideNetworkCaptureState.capturesPayloads(captureState)) {
         RiptideCustomPayloadCodecMixin.CaptureCursor cursor = RIPTIDE_CAPTURE_CURSOR.get();
         int startIndex = cursor.decodeState == captureState ? cursor.decodeStartIndex : -1;
         cursor.decodeStartIndex = -1;
         cursor.decodeState = 0L;
         CustomPacketPayload payload = (CustomPacketPayload)cir.getReturnValue();
         if (payload != null && buf != null && startIndex >= 0) {
            int end = buf.readerIndex();
            if (end > startIndex) {
               byte[] encodedBytes = new byte[end - startIndex];
               buf.getBytes(startIndex, encodedBytes);
               RiptidePayloadSupport.rememberDecodedPayloadBytes(payload, riptide$payloadChannel(payload), encodedBytes);
            }
         }
      }
   }

   @Unique
   private static String riptide$payloadChannel(CustomPacketPayload payload) {
      try {
         Type<?> type = payload == null ? null : payload.type();
         if (type != null && type.id() != null) {
            return type.id().toString();
         }
      } catch (Throwable var2) {
      }

      return "";
   }

   @Unique
   private static final class CaptureCursor {
      private int encodeStartIndex = -1;
      private int decodeStartIndex = -1;
      private long encodeState;
      private long decodeState;
   }
}
