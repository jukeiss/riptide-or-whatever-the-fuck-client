package riptide.util.mm.msg;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import riptide.util.mm.MmMessageType;

public final class MmMessages {
   private MmMessages() {
   }

   public static final class BlobOffer extends MmMessages.Msg {
      public String kind = "";
      public String friendlyName = "";
      public int count;
      public String data = "";

      public BlobOffer() {
      }

      public BlobOffer(String kind, String friendlyName, int count, String data) {
         this.kind = kind == null ? "" : kind;
         this.friendlyName = friendlyName == null ? "" : friendlyName;
         this.count = count;
         this.data = data == null ? "" : data;
      }

      @Override
      public MmMessageType type() {
         return MmMessageType.BLOB_OFFER;
      }

      @Override
      protected void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.kind);
         out.writeUTF(this.friendlyName);
         out.writeInt(this.count);
         byte[] d = this.data.getBytes(StandardCharsets.UTF_8);
         out.writeInt(d.length);
         out.write(d);
      }

      @Override
      protected void read(DataInputStream in) throws IOException {
         this.kind = in.readUTF();
         this.friendlyName = in.readUTF();
         this.count = in.readInt();
         int len = in.readInt();
         if (len >= 0 && len <= 4000000) {
            byte[] d = new byte[len];
            in.readFully(d);
            this.data = new String(d, StandardCharsets.UTF_8);
         } else {
            throw new IOException("blob too large");
         }
      }
   }

   public static final class Chat extends MmMessages.Msg {
      public String text = "";

      public Chat() {
      }

      public Chat(String text) {
         this.text = text != null ? text : "";
      }

      @Override
      public MmMessageType type() {
         return MmMessageType.CHAT;
      }

      @Override
      protected void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.text);
      }

      @Override
      protected void read(DataInputStream in) throws IOException {
         this.text = in.readUTF();
      }
   }

   public static final class CommandOffer extends MmMessages.Msg {
      public static final byte KIND_VANILLA = 0;
      public static final byte KIND_RIPTIDE = 1;
      public static final byte KIND_CHAT = 2;
      public byte kind;
      public String body = "";

      public CommandOffer() {
      }

      public CommandOffer(byte kind, String body) {
         this.kind = kind;
         this.body = body != null ? body : "";
      }

      @Override
      public MmMessageType type() {
         return MmMessageType.COMMAND_OFFER;
      }

      @Override
      protected void write(DataOutputStream out) throws IOException {
         out.writeByte(this.kind);
         out.writeUTF(this.body);
      }

      @Override
      protected void read(DataInputStream in) throws IOException {
         this.kind = in.readByte();
         this.body = in.readUTF();
      }
   }

   public static final class Kick extends MmMessages.Msg {
      public byte[] bannedFp = new byte[8];

      public Kick() {
      }

      public Kick(byte[] bannedFp) {
         if (bannedFp != null && bannedFp.length == 8) {
            this.bannedFp = (byte[])bannedFp.clone();
         }
      }

      @Override
      public MmMessageType type() {
         return MmMessageType.KICK;
      }

      @Override
      protected void write(DataOutputStream out) throws IOException {
         out.write(this.bannedFp, 0, 8);
      }

      @Override
      protected void read(DataInputStream in) throws IOException {
         byte[] b = new byte[8];
         in.readFully(b);
         this.bannedFp = b;
      }
   }

   public static final class Leave extends MmMessages.Msg {
      @Override
      public MmMessageType type() {
         return MmMessageType.LEAVE;
      }

      @Override
      protected void write(DataOutputStream out) {
      }

      @Override
      protected void read(DataInputStream in) {
      }
   }

   public static final class Location extends MmMessages.Msg {
      public String dimension = "";
      public double x;
      public double y;
      public double z;

      public Location() {
      }

      public Location(String dimension, double x, double y, double z) {
         this.dimension = dimension != null ? dimension : "";
         this.x = x;
         this.y = y;
         this.z = z;
      }

      @Override
      public MmMessageType type() {
         return MmMessageType.LOCATION;
      }

      @Override
      protected void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.dimension);
         out.writeDouble(this.x);
         out.writeDouble(this.y);
         out.writeDouble(this.z);
      }

      @Override
      protected void read(DataInputStream in) throws IOException {
         this.dimension = in.readUTF();
         this.x = in.readDouble();
         this.y = in.readDouble();
         this.z = in.readDouble();
      }
   }

   public static final class MacroOffer extends MmMessages.Msg {
      public String macroName = "";
      public int actionCount;
      public String singleActionLabel = "";
      public String hash = "";

      @Override
      public MmMessageType type() {
         return MmMessageType.MACRO_OFFER;
      }

      @Override
      protected void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.macroName);
         out.writeInt(this.actionCount);
         out.writeUTF(this.singleActionLabel);
         out.writeUTF(this.hash);
      }

      @Override
      protected void read(DataInputStream in) throws IOException {
         this.macroName = in.readUTF();
         this.actionCount = in.readInt();
         this.singleActionLabel = in.readUTF();
         this.hash = in.readUTF();
      }
   }

   public abstract static class Msg {
      public abstract MmMessageType type();

      protected abstract void write(DataOutputStream var1) throws IOException;

      protected abstract void read(DataInputStream var1) throws IOException;

      public final byte[] encode() {
         try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            this.write(new DataOutputStream(bos));
            return bos.toByteArray();
         } catch (IOException var2) {
            throw new UncheckedIOException(var2);
         }
      }

      public static <T extends MmMessages.Msg> T decodeInto(T target, byte[] payload) {
         try {
            target.read(new DataInputStream(new ByteArrayInputStream(payload)));
            return target;
         } catch (Throwable var3) {
            return null;
         }
      }
   }

   public static final class PacketOffer extends MmMessages.Msg {
      public String friendlyName = "";
      public String direction = "";
      public String data = "";

      @Override
      public MmMessageType type() {
         return MmMessageType.PACKET_OFFER;
      }

      @Override
      protected void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.friendlyName);
         out.writeUTF(this.direction);
         out.writeUTF(this.data);
      }

      @Override
      protected void read(DataInputStream in) throws IOException {
         this.friendlyName = in.readUTF();
         this.direction = in.readUTF();
         this.data = in.readUTF();
      }
   }

   public static final class Presence extends MmMessages.Msg {
      public String nickname = "";
      public boolean shareServer;
      public String serverName = "";
      public String serverIp = "";
      public boolean shareLocation;
      public int dupeStatus = -1;
      public String identityToken = "";

      public Presence() {
      }

      public Presence(String nickname) {
         this.nickname = nickname != null ? nickname : "";
      }

      @Override
      public MmMessageType type() {
         return MmMessageType.PRESENCE;
      }

      @Override
      protected void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.nickname);
         out.writeBoolean(this.shareServer);
         out.writeUTF(this.serverName);
         out.writeUTF(this.serverIp);
         out.writeBoolean(this.shareLocation);
         out.writeInt(this.dupeStatus);
         out.writeUTF(this.identityToken);
      }

      @Override
      protected void read(DataInputStream in) throws IOException {
         this.nickname = in.readUTF();
         this.shareServer = in.readBoolean();
         this.serverName = in.readUTF();
         this.serverIp = in.readUTF();
         this.shareLocation = in.readBoolean();
         if (in.available() > 0) {
            this.dupeStatus = in.readInt();
         }

         if (in.available() > 0) {
            this.identityToken = in.readUTF();
         }
      }
   }

   public static final class Receipt extends MmMessages.Msg {
      public byte[] msgId = new byte[16];

      public Receipt() {
      }

      public Receipt(byte[] msgId) {
         if (msgId != null && msgId.length == 16) {
            this.msgId = (byte[])msgId.clone();
         }
      }

      @Override
      public MmMessageType type() {
         return MmMessageType.RECEIPT;
      }

      @Override
      protected void write(DataOutputStream out) throws IOException {
         out.write(this.msgId, 0, 16);
      }

      @Override
      protected void read(DataInputStream in) throws IOException {
         byte[] b = new byte[16];
         in.readFully(b);
         this.msgId = b;
      }
   }
}
