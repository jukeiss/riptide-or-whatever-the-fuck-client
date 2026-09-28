package riptide.security;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import riptide.modules.RiptideModule;
import riptide.util.RiptidePayloadSupport;

public final class RiptideSpoofPayloadFilter extends ChannelOutboundHandlerAdapter {
   public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
      if (!(msg instanceof ServerboundCustomPayloadPacket packet)) {
         super.write(ctx, msg, promise);
      } else if (shouldBlockForVanillaSpoof(RiptideModule.get(), packet)) {
         RiptideProtector.consumeUserBypass(packet);
         promise.setSuccess();
      } else {
         RiptideProtectorChannelFilter.Verdict verdict = RiptideProtectorChannelFilter.filter(packet);
         switch (verdict.kind) {
            case DROP:
               RiptideProtector.consumeUserBypass(packet);
               promise.setSuccess();
               return;
            case REPLACE:
               RiptideProtector.consumeUserBypass(packet);
               super.write(ctx, verdict.replacement, promise);
               return;
            case PASS:
               RiptideProtector.consumeUserBypass(packet);
            default:
               break;
         }
      }
   }

   public static boolean shouldBlockForVanillaSpoof(RiptideModule module, Packet<?> packet) {
      if (packet instanceof ServerboundCustomPayloadPacket customPayload) {
         if (RiptideProtector.isUserBypass(packet)) {
            return false;
         } else if (module != null && module.isSpoofClientVanilla()) {
            String channel = RiptidePayloadSupport.payloadChannel(customPayload.payload());
            return !RiptidePayloadSupport.isBrandChannel(channel);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean shouldDropForProtector(Packet<?> packet) {
      RiptideProtectorChannelFilter.Verdict verdict = RiptideProtectorChannelFilter.filter(packet);
      return verdict.kind == RiptideProtectorChannelFilter.Verdict.Kind.DROP;
   }
}
