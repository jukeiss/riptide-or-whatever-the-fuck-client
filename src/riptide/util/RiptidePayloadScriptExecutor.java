package riptide.util;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

public final class RiptidePayloadScriptExecutor {
   private RiptidePayloadScriptExecutor() {
   }

   public static RiptidePayloadScriptExecutor.ScriptResult execute(String javaSource, RiptidePayloadScriptExecutor.Context context) {
      RiptidePayloadScriptExecutor.Context safeContext = context == null
         ? new RiptidePayloadScriptExecutor.Context("minecraft:empty", new byte[0], null)
         : context;
      String trimmed = javaSource == null ? "" : javaSource.trim();
      if (!trimmed.isEmpty()) {
         RiptideClientMessaging.sendPrefixed("§eNote: Java scripting is disabled. Sending raw payload bytes.");
      }

      return safeContext.result(safeContext.channel(), safeContext.rawBytes());
   }

   public static final class Context {
      private final String channel;
      private final byte[] rawBytes;
      private final Integer commandApiValue;

      public Context(String channel, byte[] rawBytes, Integer commandApiValue) {
         this.channel = channel == null ? "minecraft:empty" : channel;
         this.rawBytes = rawBytes == null ? new byte[0] : (byte[])rawBytes.clone();
         this.commandApiValue = commandApiValue;
      }

      public String channel() {
         return this.channel;
      }

      public Identifier channelId() {
         return RiptidePayloadSupport.parseChannel(this.channel);
      }

      public byte[] rawBytes() {
         return (byte[])this.rawBytes.clone();
      }

      public Integer commandApiValue() {
         return this.commandApiValue;
      }

      public FriendlyByteBuf copyRawBuf() {
         FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
         if (this.rawBytes.length > 0) {
            buf.writeBytes(this.rawBytes);
         }

         return buf;
      }

      public FriendlyByteBuf newBuffer() {
         return new FriendlyByteBuf(Unpooled.buffer());
      }

      public byte[] toByteArray(FriendlyByteBuf buf) {
         return RiptidePayloadSupport.toByteArray(buf);
      }

      public byte[] parseBytes(String text) {
         return RiptidePayloadSupport.parsePayloadBytes(text);
      }

      public String toHex(byte[] bytes) {
         return RiptidePayloadSupport.toHex(bytes);
      }

      public byte[] commandApiBytes(int value) {
         return RiptidePayloadSupport.withCommandApiValue(this.rawBytes, value);
      }

      public RiptidePayloadScriptExecutor.ScriptResult result(String nextChannel, byte[] bytes) {
         return new RiptidePayloadScriptExecutor.ScriptResult(nextChannel != null && !nextChannel.isBlank() ? nextChannel : this.channel, bytes);
      }
   }

   public record ScriptResult(String channel, byte[] bytes) {
      public ScriptResult(String channel, byte[] bytes) {
         bytes = bytes == null ? new byte[0] : (byte[])bytes.clone();
         this.channel = channel;
         this.bytes = bytes;
      }

      public byte[] bytes() {
         return (byte[])this.bytes.clone();
      }
   }
}
