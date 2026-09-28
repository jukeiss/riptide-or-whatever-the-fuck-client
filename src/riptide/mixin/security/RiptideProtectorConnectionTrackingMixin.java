package riptide.mixin.security;

import io.netty.channel.ChannelHandlerContext;
import java.net.InetSocketAddress;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.security.RiptideProtectorLocalAddressUtil;
import riptide.util.multi.MultiConnectionContext;

@Mixin({Connection.class})
public class RiptideProtectorConnectionTrackingMixin {
   @Inject(
      method = {"channelActive"},
      at = {@At("HEAD")}
   )
   private void riptide$onChannelActive(ChannelHandlerContext context, CallbackInfo ci) {
      if (!MultiConnectionContext.isMulti((Connection)this) && (context == null || !MultiConnectionContext.isMulti(context.channel()))) {
         try {
            if (context.channel() == null) {
               return;
            }

            if (context.channel().remoteAddress() instanceof InetSocketAddress inet && inet.getAddress() != null) {
               RiptideProtectorLocalAddressUtil.serverAddress = inet.getAddress().getHostAddress();
            } else {
               RiptideProtectorLocalAddressUtil.serverAddress = null;
            }
         } catch (Throwable var5) {
            RiptideProtectorLocalAddressUtil.serverAddress = null;
         }
      }
   }

   @Inject(
      method = {"channelInactive"},
      at = {@At("HEAD")}
   )
   private void riptide$onChannelInactive(ChannelHandlerContext context, CallbackInfo ci) {
      if (!MultiConnectionContext.isMulti((Connection)this) && (context == null || !MultiConnectionContext.isMulti(context.channel()))) {
         RiptideProtectorLocalAddressUtil.serverAddress = null;
      }
   }
}
