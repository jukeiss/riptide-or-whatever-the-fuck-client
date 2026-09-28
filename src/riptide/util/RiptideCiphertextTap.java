package riptide.util;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public final class RiptideCiphertextTap extends ChannelDuplexHandler {
   private final String direction;

   public RiptideCiphertextTap(String direction) {
      this.direction = direction;
   }

   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (packetHooksActive() && msg instanceof ByteBuf buf) {
         RiptidePacketCapture.captureCiphertext(ctx.channel(), this.direction, buf);
      }

      super.channelRead(ctx, msg);
   }

   public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
      if (packetHooksActive() && msg instanceof ByteBuf buf) {
         RiptidePacketCapture.captureCiphertext(ctx.channel(), this.direction, buf);
      }

      super.write(ctx, msg, promise);
   }

   private static boolean packetHooksActive() {
      return RiptideNetworkCaptureState.capturesPlaintext();
   }
}
