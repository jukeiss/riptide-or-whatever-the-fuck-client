package riptide.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import riptide.modules.RiptideModule;
import riptide.util.RiptideNetworkCaptureState;
import riptide.util.RiptidePacketCapture;
import riptide.util.multi.MultiConnectionContext;

@Mixin({PacketEncoder.class})
public abstract class RiptidePacketEncoderMixin<T extends PacketListener> {
   @Shadow
   @Final
   private ProtocolInfo<T> protocolInfo;

   @WrapMethod(
      method = {"encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V"}
   )
   private void riptide$captureEncodedPlaintext(ChannelHandlerContext ctx, Packet<T> packet, ByteBuf output, Operation<Void> original) {
      long captureState = RiptideNetworkCaptureState.state();
      if (RiptideNetworkCaptureState.mode(captureState) == 0) {
         original.call(new Object[]{ctx, packet, output});
      } else {
         boolean multi = ctx != null && MultiConnectionContext.isMulti(ctx.channel());
         boolean suppressPayloadCodec = multi && RiptideNetworkCaptureState.capturesPayloads(captureState);
         if (suppressPayloadCodec) {
            RiptideNetworkCaptureState.beginMultiCodecSuppression();
         }

         try {
            original.call(new Object[]{ctx, packet, output});
         } finally {
            if (suppressPayloadCodec) {
               RiptideNetworkCaptureState.endMultiCodecSuppression();
            }
         }

         if (!multi) {
            RiptideModule module = RiptideModule.get();
            if (module != null) {
               String protocol = this.protocolInfo.id().id();
               if (RiptideNetworkCaptureState.capturesPlaintext(captureState)) {
                  RiptidePacketCapture.capturePlaintext(packet, "C2S", protocol, packet.type(), output);
               }

               if (RiptideNetworkCaptureState.capturesPayloads(captureState) && riptide$isPayloadCarrier(packet)) {
                  module.captureDecodedPayloadPacket(packet, "C2S", protocol, "encoder");
               }
            }
         }
      }
   }

   @Unique
   private static boolean riptide$isPayloadCarrier(Packet<?> packet) {
      return packet instanceof ServerboundCustomPayloadPacket || packet instanceof BundlePacket;
   }
}
