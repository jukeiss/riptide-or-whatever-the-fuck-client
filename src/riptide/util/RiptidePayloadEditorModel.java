package riptide.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import riptide.util.macro.PayloadAction;

public final class RiptidePayloadEditorModel {
   private static final char[] HEX = "0123456789ABCDEF".toCharArray();
   public String direction = "C2S";
   public String phase = "PLAY";
   public String channel = "minecraft:brand";
   public String packetClass = "";
   public int packetId = -1;
   public String sourceProtocol = "";
   public String encodingMode = "";
   public List<RiptidePayloadTemplate.Field> bodyFields = new ArrayList<>();
   public byte[] bodyBytes = new byte[0];
   public byte[] lastValidBytes = new byte[0];
   public RiptidePayloadEditorModel.Provenance provenance = RiptidePayloadEditorModel.Provenance.USER_EDITED;
   public String validationError = "";
   public String decodedKind = "Binary";
   public boolean commandApiRecognized = false;
   public boolean commandApiOverride = false;
   public int commandApiValue = 2147483639;
   private long revision = 1L;
   private long packetScriptRevision = Long.MIN_VALUE;
   private String cachedPacketScript = "";
   private long bodyHexRevision = Long.MIN_VALUE;
   private String cachedBodyHex = "";
   private long utf8Revision = Long.MIN_VALUE;
   private String cachedUtf8 = "";
   private long logicalTextRevision = Long.MIN_VALUE;
   private String cachedLogicalText = "";

   public long revision() {
      return this.revision;
   }

   public void touch() {
      this.revision++;
   }

   public static RiptidePayloadEditorModel fromAction(PayloadAction action) {
      RiptidePayloadEditorModel model = new RiptidePayloadEditorModel();
      if (action == null) {
         model.bodyFields = new ArrayList<>(RiptidePayloadTemplate.defaultBrandTemplate().fields());
         model.rebuildFromFields(false);
         return model;
      } else {
         model.direction = cleanDirection(
            action.payloadDirection != null && !action.payloadDirection.isBlank() ? action.payloadDirection : action.sourceDirection
         );
         model.phase = cleanPhase(action.payloadPhase);
         model.channel = action.channel != null && !action.channel.isBlank() ? action.channel.strip() : "minecraft:brand";
         model.packetClass = action.payloadClassName == null ? "" : action.payloadClassName;
         model.packetId = action.payloadPacketId;
         model.sourceProtocol = action.sourceProtocol == null ? "" : action.sourceProtocol;
         model.encodingMode = action.payloadEncodingMode == null ? "" : action.payloadEncodingMode;
         model.provenance = RiptidePayloadEditorModel.Provenance.parse(action.payloadProvenance);
         model.commandApiRecognized = action.commandApiRecognized;
         model.commandApiOverride = action.commandApiOverride;
         model.commandApiValue = action.commandApiValue;
         byte[] raw = new byte[0];
         boolean haveRaw = false;
         if (action.payloadData != null && !action.payloadData.isBlank()) {
            try {
               raw = RiptidePayloadSupport.parsePayloadBytes(action.payloadData);
               haveRaw = true;
            } catch (Throwable var5) {
            }
         }

         List<RiptidePayloadTemplate.Field> fields = RiptidePayloadTemplate.parseFields(action.payloadFields);
         if (!fields.isEmpty()) {
            model.bodyFields = new ArrayList<>(fields);
            if (!model.rebuildFromFields(false) && haveRaw) {
               model.setBodyBytes(raw, RiptidePayloadEditorModel.Provenance.parse(action.payloadProvenance));
            }
         } else {
            model.setBodyBytes(raw, RiptidePayloadEditorModel.Provenance.parse(action.payloadProvenance));
         }

         return model;
      }
   }

   public PayloadAction toAction(PayloadAction base) {
      PayloadAction action = new PayloadAction();
      if (base != null) {
         action.fromTag(base.toTag());
      }

      action.channel = this.channel != null && !this.channel.isBlank() ? this.channel.strip() : "minecraft:brand";
      action.payloadDirection = cleanDirection(this.direction);
      action.sourceDirection = action.payloadDirection;
      action.payloadPhase = cleanPhase(this.phase);
      action.sourceProtocol = this.sourceProtocol != null && !this.sourceProtocol.isBlank()
         ? this.sourceProtocol
         : ("CONFIGURATION".equals(action.payloadPhase) ? "CONFIGURATION" : "");
      action.payloadClassName = this.packetClass == null ? "" : this.packetClass;
      action.payloadPacketId = this.packetId;
      action.payloadProvenance = this.provenance == null ? RiptidePayloadEditorModel.Provenance.USER_EDITED.tag() : this.provenance.tag();
      action.payloadEncodingMode = this.inferMode();
      action.payloadFields = RiptidePayloadTemplate.serializeFields(this.bodyFields);
      action.payloadData = RiptidePayloadSupport.toHex(this.bodyBytes);
      action.payloadJson = "";
      action.commandApiRecognized = this.commandApiRecognized;
      action.commandApiOverride = this.commandApiOverride;
      action.commandApiValue = this.commandApiValue;
      action.payloadScriptEnabled = false;
      action.javaSource = "";
      return action;
   }

   public RiptidePayloadTemplate.Template toTemplate() {
      return new RiptidePayloadTemplate.Template(
         this.channel,
         RiptidePayloadTemplate.PayloadDirection.parse(this.direction),
         RiptidePayloadTemplate.PayloadPhase.parse(this.phase),
         RiptidePayloadTemplate.EncodingMode.parse(this.inferMode(), this.channel),
         this.bodyFields == null ? List.of() : this.bodyFields
      );
   }

   public boolean applyPacketScript(String script) {
      RiptidePayloadEditorModel parsed = this.copy();
      List<String> writerLines = new ArrayList<>();
      String[] lines = (script == null ? "" : script).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

      for (String raw : lines) {
         String line = raw == null ? "" : raw.strip();
         if (line.isEmpty()) {
            writerLines.add(raw == null ? "" : raw);
         } else {
            int split = firstDelimiter(line);
            if (split > 0) {
               String key = line.substring(0, split).strip().toLowerCase(Locale.ROOT);
               String value = line.substring(split + 1).strip();
               switch (key) {
                  case "direction":
                     parsed.direction = cleanDirection(value);
                     break;
                  case "phase":
                     parsed.phase = cleanPhase(value);
                     break;
                  case "channel":
                     parsed.channel = value.isBlank() ? "minecraft:brand" : value;
                     break;
                  case "packetclass":
                  case "packet_class":
                     parsed.packetClass = value;
                     break;
                  case "packetid":
                  case "packet_id":
                     parsed.packetId = parseIntOr(value, -1);
                     break;
                  case "sourceprotocol":
                  case "source_protocol":
                     parsed.sourceProtocol = value;
                     break;
                  default:
                     writerLines.add(raw == null ? "" : raw);
               }
            } else {
               writerLines.add(raw == null ? "" : raw);
            }
         }
      }

      parsed.bodyFields = new ArrayList<>(RiptidePayloadTemplate.parseFields(String.join("\n", writerLines)));
      if (!parsed.rebuildFromFields(true)) {
         this.validationError = parsed.validationError;
         this.touch();
         return false;
      } else {
         this.copyFrom(parsed);
         this.provenance = RiptidePayloadEditorModel.Provenance.USER_EDITED;
         this.touch();
         return true;
      }
   }

   public boolean applyBodyHex(String hex) {
      try {
         this.setBodyBytes(parseStrictHex(hex), RiptidePayloadEditorModel.Provenance.USER_EDITED);
         this.validationError = "";
         return true;
      } catch (Throwable var3) {
         this.validationError = RiptidePayloadSupport.safeMessage(var3);
         this.touch();
         return false;
      }
   }

   public boolean applyUtf8(String text) {
      this.setBodyBytes((text == null ? "" : text).getBytes(StandardCharsets.UTF_8), RiptidePayloadEditorModel.Provenance.USER_EDITED);
      return true;
   }

   public boolean applyLogicalText(String text) {
      RiptidePayloadEditorModel parsed = this.copy();
      String bodyHex = null;
      String logicalHex = null;
      String[] lines = (text == null ? "" : text).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

      for (String raw : lines) {
         String line = raw == null ? "" : raw.strip();
         if (!line.isEmpty() && !line.startsWith("#")) {
            int split = firstDelimiter(line);
            if (split >= 0) {
               String key = line.substring(0, split).strip().toLowerCase(Locale.ROOT);
               String value = line.substring(split + 1).strip();
               switch (key) {
                  case "direction":
                     parsed.direction = cleanDirection(value);
                     break;
                  case "phase":
                     parsed.phase = cleanPhase(value);
                     break;
                  case "channel":
                     parsed.channel = value.isBlank() ? parsed.channel : value;
                     break;
                  case "packetid":
                  case "packet_id":
                     parsed.packetId = parseIntOr(value, -1);
                     break;
                  case "bodyhex":
                  case "body_hex":
                     bodyHex = value;
                     break;
                  case "logicalhex":
                  case "logical_hex":
                     logicalHex = value;
                     break;
                  case "packetclass":
                  case "packet_class":
                     parsed.packetClass = value;
                     break;
                  case "sourceprotocol":
                  case "source_protocol":
                     parsed.sourceProtocol = value;
               }
            }
         }
      }

      try {
         if (logicalHex != null && !logicalHex.isBlank()) {
            RiptidePayloadEditorModel.LogicalPacket logical = parseLogicalPacketHex(logicalHex);
            if (parsed.packetId >= 0 && logical.packetId != parsed.packetId) {
               throw new IllegalArgumentException("Logical packet id does not match packetId");
            }

            parsed.packetId = logical.packetId;
            parsed.channel = logical.channel;
            parsed.setBodyBytes(logical.body, RiptidePayloadEditorModel.Provenance.USER_EDITED);
         } else if (bodyHex != null) {
            parsed.setBodyBytes(parseStrictHex(bodyHex), RiptidePayloadEditorModel.Provenance.USER_EDITED);
         }
      } catch (Throwable var16) {
         this.validationError = RiptidePayloadSupport.safeMessage(var16);
         this.touch();
         return false;
      }

      this.copyFrom(parsed);
      this.provenance = RiptidePayloadEditorModel.Provenance.USER_EDITED;
      this.validationError = "";
      this.touch();
      return true;
   }

   public String packetScript() {
      if (this.packetScriptRevision == this.revision) {
         return this.cachedPacketScript;
      } else {
         StringBuilder sb = new StringBuilder();
         sb.append("direction = ").append(cleanDirection(this.direction)).append('\n');
         sb.append("phase = ").append(cleanPhase(this.phase)).append('\n');
         sb.append("channel = ").append(this.channel != null && !this.channel.isBlank() ? this.channel.strip() : "minecraft:brand").append('\n');
         if (this.packetId >= 0) {
            sb.append("packetId = ").append(this.packetId).append('\n');
         }

         if (this.packetClass != null && !this.packetClass.isBlank()) {
            sb.append("packetClass = ").append(this.packetClass).append('\n');
         }

         if (this.sourceProtocol != null && !this.sourceProtocol.isBlank()) {
            sb.append("sourceProtocol = ").append(this.sourceProtocol).append('\n');
         }

         sb.append('\n');
         sb.append(RiptidePayloadTemplate.serializeFields(this.bodyFields));
         this.cachedPacketScript = sb.toString();
         this.packetScriptRevision = this.revision;
         return this.cachedPacketScript;
      }
   }

   public String bodyHexMultiline() {
      if (this.bodyBytes != null && this.bodyBytes.length != 0) {
         if (this.bodyHexRevision == this.revision) {
            return this.cachedBodyHex;
         } else {
            StringBuilder sb = new StringBuilder(this.bodyBytes.length * 3 + this.bodyBytes.length / 16 * 10);

            for (int i = 0; i < this.bodyBytes.length; i++) {
               if (i > 0) {
                  sb.append((char)(i % 16 == 0 ? '\n' : ' '));
               }

               int value = this.bodyBytes[i] & 255;
               sb.append(HEX[value >>> 4]).append(HEX[value & 15]);
            }

            this.cachedBodyHex = sb.toString();
            this.bodyHexRevision = this.revision;
            return this.cachedBodyHex;
         }
      } else {
         return "";
      }
   }

   public String utf8View() {
      if (this.utf8Revision == this.revision) {
         return this.cachedUtf8;
      } else {
         String text = RiptidePayloadSupport.decodeLikelyUtf8Text(this.bodyBytes);
         this.cachedUtf8 = text == null ? "" : text;
         this.utf8Revision = this.revision;
         return this.cachedUtf8;
      }
   }

   public String logicalText() {
      if (this.logicalTextRevision == this.revision) {
         return this.cachedLogicalText;
      } else {
         StringBuilder sb = new StringBuilder();
         sb.append("direction = ").append(cleanDirection(this.direction)).append('\n');
         sb.append("phase = ").append(cleanPhase(this.phase)).append('\n');
         sb.append("channel = ").append(this.channel == null ? "" : this.channel).append('\n');
         sb.append("packetId = ").append(this.packetId >= 0 ? this.packetId : "unavailable").append('\n');
         if (this.packetClass != null && !this.packetClass.isBlank()) {
            sb.append("packetClass = ").append(this.packetClass).append('\n');
         }

         if (this.sourceProtocol != null && !this.sourceProtocol.isBlank()) {
            sb.append("sourceProtocol = ").append(this.sourceProtocol).append('\n');
         }

         sb.append("provenance = ")
            .append(this.provenance == null ? RiptidePayloadEditorModel.Provenance.USER_EDITED.tag() : this.provenance.tag())
            .append('\n');
         sb.append("bodyBytes = ").append(this.bodyBytes == null ? 0 : this.bodyBytes.length).append('\n');
         sb.append("decoded = ").append(this.decodedSummary()).append('\n');
         sb.append("bodyHex = ").append(RiptidePayloadSupport.toHex(this.bodyBytes)).append('\n');
         if (this.packetId >= 0) {
            sb.append("logicalHex = ").append(RiptidePayloadSupport.toHex(this.logicalBytes())).append('\n');
         } else {
            sb.append("logicalHex = unavailable; packet id was not captured/resolved\n");
         }

         this.cachedLogicalText = sb.toString();
         this.logicalTextRevision = this.revision;
         return this.cachedLogicalText;
      }
   }

   public byte[] logicalBytes() {
      if (this.packetId < 0) {
         return new byte[0];
      } else {
         byte[] packet = encodeVarIntBytes(this.packetId);
         byte[] channelBytes = RiptidePayloadSupport.encodeMinecraftStringPayload(this.channel == null ? "" : this.channel);
         byte[] body = this.bodyBytes == null ? new byte[0] : this.bodyBytes;
         byte[] out = new byte[packet.length + channelBytes.length + body.length];
         System.arraycopy(packet, 0, out, 0, packet.length);
         System.arraycopy(channelBytes, 0, out, packet.length, channelBytes.length);
         System.arraycopy(body, 0, out, packet.length + channelBytes.length, body.length);
         return out;
      }
   }

   public String decodedSummary() {
      return this.bodyFields != null && !this.bodyFields.isEmpty() ? this.bodyFields.get(0).displayLine() : "empty";
   }

   private boolean rebuildFromFields(boolean strict) {
      RiptidePayloadTemplate.Template template = this.toTemplate();
      RiptidePayloadTemplate.BuildResult built = template.build();
      if (!built.ok()) {
         this.validationError = String.join("; ", built.errors());
         if (strict) {
            this.touch();
            return false;
         }
      }

      this.channel = template.channel();
      this.direction = template.direction().name();
      this.phase = template.phase().name();
      this.encodingMode = template.mode().name();
      this.bodyBytes = built.bytes();
      this.lastValidBytes = (byte[])this.bodyBytes.clone();
      this.decodedKind = inferDecodedKind(this.bodyBytes);
      this.refreshCommandApiFromBytes();
      this.validationError = "";
      this.touch();
      return true;
   }

   private void setBodyBytes(byte[] bytes, RiptidePayloadEditorModel.Provenance source) {
      this.bodyBytes = bytes == null ? new byte[0] : (byte[])bytes.clone();
      this.lastValidBytes = (byte[])this.bodyBytes.clone();
      this.bodyFields = new ArrayList<>(RiptidePayloadSupport.inferEditablePayloadFields(this.bodyBytes));
      this.encodingMode = RiptidePayloadSupport.inferEditablePayloadMode(this.channel, this.bodyBytes);
      this.decodedKind = inferDecodedKind(this.bodyBytes);
      this.provenance = source == null ? RiptidePayloadEditorModel.Provenance.USER_EDITED : source;
      this.refreshCommandApiFromBytes();
      this.validationError = "";
      this.touch();
   }

   private void refreshCommandApiFromBytes() {
      Integer parsed = RiptidePayloadSupport.tryParseCommandApiValue(null, this.channel, this.bodyBytes);
      if (parsed != null) {
         this.commandApiRecognized = true;
         if (!this.commandApiOverride) {
            this.commandApiValue = parsed;
         }
      } else if (!this.commandApiOverride) {
         this.commandApiRecognized = false;
      }
   }

   private String inferMode() {
      return this.encodingMode != null && !this.encodingMode.isBlank()
         ? this.encodingMode
         : RiptidePayloadSupport.inferEditablePayloadMode(this.channel, this.bodyBytes);
   }

   private static String inferDecodedKind(byte[] bytes) {
      if (RiptidePayloadSupport.decodeMinecraftStringPayload(bytes) != null) {
         return "Minecraft String";
      } else if (RiptidePayloadSupport.decodeJavaWriteUtfPayload(bytes) != null) {
         return "Java writeUTF";
      } else {
         String utf8 = RiptidePayloadSupport.decodeLikelyUtf8Text(bytes);
         return utf8 != null && !utf8.isBlank() ? "Raw UTF-8" : "Binary Hex";
      }
   }

   private RiptidePayloadEditorModel copy() {
      RiptidePayloadEditorModel copy = new RiptidePayloadEditorModel();
      copy.copyFrom(this);
      return copy;
   }

   private void copyFrom(RiptidePayloadEditorModel other) {
      this.direction = other.direction;
      this.phase = other.phase;
      this.channel = other.channel;
      this.packetClass = other.packetClass;
      this.packetId = other.packetId;
      this.sourceProtocol = other.sourceProtocol;
      this.encodingMode = other.encodingMode;
      this.bodyFields = new ArrayList<>(other.bodyFields == null ? List.of() : other.bodyFields);
      this.bodyBytes = other.bodyBytes == null ? new byte[0] : (byte[])other.bodyBytes.clone();
      this.lastValidBytes = other.lastValidBytes == null ? new byte[0] : (byte[])other.lastValidBytes.clone();
      this.provenance = other.provenance;
      this.validationError = other.validationError;
      this.decodedKind = other.decodedKind;
      this.commandApiRecognized = other.commandApiRecognized;
      this.commandApiOverride = other.commandApiOverride;
      this.commandApiValue = other.commandApiValue;
      this.touch();
   }

   private static byte[] parseStrictHex(String text) {
      String value = text == null ? "" : text.strip();
      if (value.isEmpty()) {
         return new byte[0];
      } else {
         String compact = value.replaceAll("(?i)0x", "").replaceAll("\\s+", "");
         if (compact.isEmpty()) {
            return new byte[0];
         } else if ((compact.length() & 1) != 0) {
            throw new IllegalArgumentException("Hex needs byte pairs");
         } else if (!compact.matches("(?i)[0-9a-f]+")) {
            throw new IllegalArgumentException("Only hex digits are allowed");
         } else {
            byte[] out = new byte[compact.length() / 2];

            for (int i = 0; i < compact.length(); i += 2) {
               out[i / 2] = (byte)Integer.parseInt(compact.substring(i, i + 2), 16);
            }

            return out;
         }
      }
   }

   private static RiptidePayloadEditorModel.LogicalPacket parseLogicalPacketHex(String hex) {
      byte[] bytes = parseStrictHex(hex);
      int[] index = new int[]{0};
      Integer packetId = readVarInt(bytes, index);
      if (packetId != null && packetId >= 0) {
         String channel = readMinecraftString(bytes, index);
         byte[] body = new byte[Math.max(0, bytes.length - index[0])];
         if (body.length > 0) {
            System.arraycopy(bytes, index[0], body, 0, body.length);
         }

         return new RiptidePayloadEditorModel.LogicalPacket(packetId, channel, body);
      } else {
         throw new IllegalArgumentException("Missing logical packet id");
      }
   }

   private static String readMinecraftString(byte[] bytes, int[] index) {
      Integer len = readVarInt(bytes, index);
      if (len != null && len >= 0) {
         if (index[0] + len > bytes.length) {
            throw new IllegalArgumentException("Channel length exceeds logical packet");
         } else {
            String value = new String(bytes, index[0], len, StandardCharsets.UTF_8);
            index[0] += len;
            if (value.isBlank()) {
               throw new IllegalArgumentException("Channel cannot be blank");
            } else {
               return value;
            }
         }
      } else {
         throw new IllegalArgumentException("Invalid channel length");
      }
   }

   private static Integer readVarInt(byte[] bytes, int[] indexRef) {
      int value = 0;
      int position = 0;
      int index = Math.max(0, indexRef[0]);

      while (index < bytes.length) {
         byte currentByte = bytes[index++];
         value |= (currentByte & 127) << position;
         if ((currentByte & 128) == 0) {
            indexRef[0] = index;
            return value;
         }

         position += 7;
         if (position >= 32) {
            return null;
         }
      }

      return null;
   }

   private static byte[] encodeVarIntBytes(int value) {
      byte[] out = new byte[5];
      int index = 0;

      while ((value & -128) != 0) {
         out[index++] = (byte)(value & 127 | 128);
         value >>>= 7;
      }

      out[index++] = (byte)value;
      byte[] trimmed = new byte[index];
      System.arraycopy(out, 0, trimmed, 0, index);
      return trimmed;
   }

   private static int firstDelimiter(String line) {
      int eq = line.indexOf(61);
      int colon = line.indexOf(58);
      if (eq < 0) {
         return colon;
      } else {
         return colon < 0 ? eq : Math.min(eq, colon);
      }
   }

   private static String cleanDirection(String raw) {
      return "S2C".equalsIgnoreCase(raw == null ? "" : raw.strip()) ? "S2C" : "C2S";
   }

   private static String cleanPhase(String raw) {
      String value = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
      return value.contains("CONFIG") ? "CONFIGURATION" : "PLAY";
   }

   private static int parseIntOr(String value, int fallback) {
      try {
         return Integer.parseInt(value == null ? "" : value.strip());
      } catch (Throwable var3) {
         return fallback;
      }
   }

   private record LogicalPacket(int packetId, String channel, byte[] body) {
   }

   public static enum Provenance {
      CAPTURED_EXACT("capturedExact"),
      REENCODED_FROM_PAYLOAD("reencodedFromPayload"),
      USER_EDITED("userEdited");

      private final String tag;

      private Provenance(String tag) {
         this.tag = tag;
      }

      public String tag() {
         return this.tag;
      }

      public static RiptidePayloadEditorModel.Provenance parse(String raw) {
         if (raw != null && !raw.isBlank()) {
            String normalized = raw.strip().toLowerCase(Locale.ROOT);

            for (RiptidePayloadEditorModel.Provenance value : values()) {
               if (value.tag.equalsIgnoreCase(normalized) || value.name().equalsIgnoreCase(normalized)) {
                  return value;
               }
            }

            return USER_EDITED;
         } else {
            return USER_EDITED;
         }
      }
   }
}
