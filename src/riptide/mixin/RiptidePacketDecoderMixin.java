package riptide.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import riptide.modules.RiptideModule;
import riptide.util.RiptideNetworkCaptureState;
import riptide.util.RiptidePacketCapture;
import riptide.util.multi.MultiConnectionContext;

@Mixin({PacketDecoder.class})
public abstract class RiptidePacketDecoderMixin<T extends PacketListener> {
   @Shadow
   @Final
   private ProtocolInfo<T> protocolInfo;

   @WrapMethod(
      method = {"decode"}
   )
   private void riptide$captureIncomingPlaintext(ChannelHandlerContext ctx, ByteBuf input, List<Object> out, Operation<Void> original) {
      long captureState = RiptideNetworkCaptureState.state();
      if (RiptideNetworkCaptureState.mode(captureState) == 0) {
         original.call(new Object[]{ctx, input, out});
      } else {
         int outSizeBeforeDecode = out == null ? 0 : out.size();
         boolean multi = ctx != null && MultiConnectionContext.isMulti(ctx.channel());
         boolean suppressPayloadCodec = multi && RiptideNetworkCaptureState.capturesPayloads(captureState);
         if (suppressPayloadCodec) {
            RiptideNetworkCaptureState.beginMultiCodecSuppression();
         }

         byte[] incomingPlaintext = !multi && RiptideNetworkCaptureState.capturesPlaintext(captureState)
            ? RiptidePacketCapture.copyReadableBytes(input)
            : RiptideNetworkCaptureState.EMPTY_BYTES;

         try {
            original.call(new Object[]{ctx, input, out});
         } finally {
            if (suppressPayloadCodec) {
               RiptideNetworkCaptureState.endMultiCodecSuppression();
            }
         }

         if (!multi) {
            if (out != null && !out.isEmpty()) {
               int start = Math.max(0, Math.min(outSizeBeforeDecode, out.size()));
               boolean capturePlaintext = RiptideNetworkCaptureState.capturesPlaintext(captureState) && incomingPlaintext.length > 0;
               boolean capturePayloads = RiptideNetworkCaptureState.capturesPayloads(captureState);
               RiptideModule module = RiptideModule.get();
               if (module != null) {
                  String protocol = this.protocolInfo.id().id();

                  for (int i = start; i < out.size(); i++) {
                     if (out.get(i) instanceof Packet<?> packet) {
                        if (capturePlaintext) {
                           RiptidePacketCapture.capturePlaintextBytes(packet, "S2C", protocol, packet.type(), incomingPlaintext);
                        }

                        if (capturePayloads && riptide$isPayloadCarrier(packet)) {
                           module.captureDecodedPayloadPacket(packet, "S2C", protocol, "decoder");
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Unique
   private static boolean riptide$isPayloadCarrier(Packet<?> packet) {
      return packet instanceof ClientboundCustomPayloadPacket || packet instanceof BundlePacket;
   }
}
