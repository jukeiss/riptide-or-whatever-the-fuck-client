package riptide.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import riptide.util.macro.PayloadAction;

public final class RiptidePayloadTemplate {
   private static final int WARN_PAYLOAD_BYTES = 8192;
   private static final int MAX_PAYLOAD_BYTES = 32767;

   private RiptidePayloadTemplate() {
   }

   public static RiptidePayloadTemplate.Template fromAction(PayloadAction action) {
      if (action == null) {
         return defaultBrandTemplate();
      } else {
         String channel = action.channel != null && !action.channel.isBlank() ? action.channel.strip() : "minecraft:brand";
         RiptidePayloadTemplate.PayloadDirection direction = RiptidePayloadTemplate.PayloadDirection.parse(
            action.payloadDirection != null && !action.payloadDirection.isBlank() ? action.payloadDirection : action.sourceDirection
         );
         RiptidePayloadTemplate.PayloadPhase phase = RiptidePayloadTemplate.PayloadPhase.parse(
            action.payloadPhase != null && !action.payloadPhase.isBlank() ? action.payloadPhase : action.sourceProtocol
         );
         RiptidePayloadTemplate.EncodingMode mode = RiptidePayloadTemplate.EncodingMode.parse(action.payloadEncodingMode, channel);
         List<RiptidePayloadTemplate.Field> fields = parseFields(action.payloadFields);
         if (fields.isEmpty() && action.payloadData != null && !action.payloadData.isBlank()) {
            fields = List.of(new RiptidePayloadTemplate.Field(RiptidePayloadTemplate.FieldType.HEX_BYTES, action.payloadData, true));
            mode = RiptidePayloadTemplate.EncodingMode.MANUAL_HEX;
         }

         if (fields.isEmpty()) {
            if (RiptidePayloadSupport.isBrandChannel(channel)) {
               fields = List.of(
                  new RiptidePayloadTemplate.Field(RiptidePayloadTemplate.FieldType.MINECRAFT_STRING, RiptidePayloadSupport.defaultBrandPayloadString(), true)
               );
               mode = RiptidePayloadTemplate.EncodingMode.MINECRAFT_BYTEBUF;
            } else {
               fields = List.of(new RiptidePayloadTemplate.Field(RiptidePayloadTemplate.FieldType.RAW_UTF8_STRING, "", true));
            }
         }

         return new RiptidePayloadTemplate.Template(channel, direction, phase, mode, fields);
      }
   }

   public static RiptidePayloadTemplate.Template defaultBrandTemplate() {
      return new RiptidePayloadTemplate.Template(
         "minecraft:brand",
         RiptidePayloadTemplate.PayloadDirection.C2S,
         RiptidePayloadTemplate.PayloadPhase.PLAY,
         RiptidePayloadTemplate.EncodingMode.MINECRAFT_BYTEBUF,
         List.of(new RiptidePayloadTemplate.Field(RiptidePayloadTemplate.FieldType.MINECRAFT_STRING, RiptidePayloadSupport.defaultBrandPayloadString(), true))
      );
   }

   public static RiptidePayloadTemplate.Template presetPaperPing() {
      return new RiptidePayloadTemplate.Template(
         "testmod:ping",
         RiptidePayloadTemplate.PayloadDirection.C2S,
         RiptidePayloadTemplate.PayloadPhase.PLAY,
         RiptidePayloadTemplate.EncodingMode.RAW_UTF8,
         List.of(new RiptidePayloadTemplate.Field(RiptidePayloadTemplate.FieldType.RAW_UTF8_STRING, "hello-from-client", true))
      );
   }

   public static RiptidePayloadTemplate.Template presetBrand(String brand) {
      return new RiptidePayloadTemplate.Template(
         "minecraft:brand",
         RiptidePayloadTemplate.PayloadDirection.C2S,
         RiptidePayloadTemplate.PayloadPhase.PLAY,
         RiptidePayloadTemplate.EncodingMode.MINECRAFT_BYTEBUF,
         List.of(
            new RiptidePayloadTemplate.Field(
               RiptidePayloadTemplate.FieldType.MINECRAFT_STRING,
               brand != null && !brand.isBlank() ? brand : RiptidePayloadSupport.defaultBrandPayloadString(),
               true
            )
         )
      );
   }

   public static RiptidePayloadTemplate.Template presetBungeeGetServer() {
      return new RiptidePayloadTemplate.Template(
         "bungeecord:main",
         RiptidePayloadTemplate.PayloadDirection.C2S,
         RiptidePayloadTemplate.PayloadPhase.PLAY,
         RiptidePayloadTemplate.EncodingMode.JAVA_DATA_OUTPUT,
         List.of(new RiptidePayloadTemplate.Field(RiptidePayloadTemplate.FieldType.JAVA_WRITE_UTF, "GetServer", true))
      );
   }

   public static RiptidePayloadTemplate.Template presetBungeePlayerCount() {
      return new RiptidePayloadTemplate.Template(
         "bungeecord:main",
         RiptidePayloadTemplate.PayloadDirection.C2S,
         RiptidePayloadTemplate.PayloadPhase.PLAY,
         RiptidePayloadTemplate.EncodingMode.JAVA_DATA_OUTPUT,
         List.of(
            new RiptidePayloadTemplate.Field(RiptidePayloadTemplate.FieldType.JAVA_WRITE_UTF, "PlayerCount", true),
            new RiptidePayloadTemplate.Field(RiptidePayloadTemplate.FieldType.JAVA_WRITE_UTF, "ALL", true)
         )
      );
   }

   public static String serializeFields(List<RiptidePayloadTemplate.Field> fields) {
      if (fields != null && !fields.isEmpty()) {
         StringBuilder sb = new StringBuilder();

         for (RiptidePayloadTemplate.Field field : fields) {
            if (field != null) {
               if (!sb.isEmpty()) {
                  sb.append('\n');
               }

               if (!field.enabled()) {
                  sb.append("# ");
               }

               sb.append(field.type().label()).append(" = ").append(field.value());
            }
         }

         return sb.toString();
      } else {
         return "";
      }
   }

   public static List<RiptidePayloadTemplate.Field> parseFields(String text) {
      if (text != null && !text.isBlank()) {
         List<RiptidePayloadTemplate.Field> fields = new ArrayList<>();
         String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");

         for (String rawLine : lines) {
            if (rawLine != null) {
               String line = rawLine.strip();
               if (!line.isEmpty()) {
                  boolean enabled = true;
                  if (line.startsWith("#")) {
                     enabled = false;
                     line = line.substring(1).strip();
                  }

                  int split = line.indexOf(61);
                  if (split < 0) {
                     split = line.indexOf(58);
                  }

                  String typeText = split < 0 ? "RAW_UTF8_STRING" : line.substring(0, split).strip();
                  String value = split < 0 ? line : line.substring(split + 1).strip();
                  fields.add(new RiptidePayloadTemplate.Field(RiptidePayloadTemplate.FieldType.parse(typeText), value, enabled));
               }
            }
         }

         return fields;
      } else {
         return Collections.emptyList();
      }
   }

   public static String nextModeName(String currentMode, String channel) {
      RiptidePayloadTemplate.EncodingMode current = RiptidePayloadTemplate.EncodingMode.parse(currentMode, channel);
      RiptidePayloadTemplate.EncodingMode[] values = RiptidePayloadTemplate.EncodingMode.values();
      return values[(current.ordinal() + 1) % values.length].name();
   }

   public static String nextDirectionName(String current) {
      return RiptidePayloadTemplate.PayloadDirection.parse(current) == RiptidePayloadTemplate.PayloadDirection.C2S
         ? RiptidePayloadTemplate.PayloadDirection.S2C.name()
         : RiptidePayloadTemplate.PayloadDirection.C2S.name();
   }

   public static String nextPhaseName(String current) {
      return RiptidePayloadTemplate.PayloadPhase.parse(current) == RiptidePayloadTemplate.PayloadPhase.PLAY
         ? RiptidePayloadTemplate.PayloadPhase.CONFIGURATION.name()
         : RiptidePayloadTemplate.PayloadPhase.PLAY.name();
   }

   public static RiptidePayloadTemplate.Preview preview(byte[] bytes) {
      byte[] raw = bytes == null ? new byte[0] : bytes;
      return new RiptidePayloadTemplate.Preview(RiptidePayloadSupport.toCompactHex(raw, 96), previewUtf8(raw), previewMinecraftString(raw), previewJavaUtf(raw));
   }

   public static void applyTemplate(PayloadAction action, RiptidePayloadTemplate.Template template) {
      if (action != null && template != null) {
         action.channel = template.channel();
         action.payloadDirection = template.direction().name();
         action.payloadPhase = template.phase().name();
         action.payloadEncodingMode = template.mode().name();
         action.payloadFields = serializeFields(template.fields());
         action.sourceDirection = action.payloadDirection;
         action.sourceProtocol = "";
         RiptidePayloadTemplate.BuildResult result = template.build();
         if (result.ok()) {
            action.payloadData = RiptidePayloadSupport.toHex(result.bytes());
         }
      }
   }

   private static void validateHeader(
      String channel,
      RiptidePayloadTemplate.PayloadDirection direction,
      RiptidePayloadTemplate.PayloadPhase phase,
      RiptidePayloadTemplate.EncodingMode mode,
      List<String> warnings,
      List<String> errors
   ) {
      if (direction == RiptidePayloadTemplate.PayloadDirection.S2C) {
         warnings.add("S2C is inspect/replay metadata only. Sending from macros is serverbound C2S.");
      }

      if (phase == RiptidePayloadTemplate.PayloadPhase.CONFIGURATION) {
         warnings.add("Configuration payloads usually cannot be replayed during Play.");
      }

      try {
         RiptidePayloadSupport.parseChannel(channel);
      } catch (Exception var7) {
         errors.add(RiptidePayloadSupport.safeMessage(var7));
      }

      if (channel != null && !channel.contains(":")) {
         warnings.add("Use a namespaced channel like namespace:path.");
      }

      if (RiptidePayloadSupport.isBrandChannel(channel) && mode != RiptidePayloadTemplate.EncodingMode.MINECRAFT_BYTEBUF) {
         warnings.add("minecraft:brand normally expects a Minecraft String.");
      }

      if ("bungeecord:main".equalsIgnoreCase(channel == null ? "" : channel.strip()) && mode != RiptidePayloadTemplate.EncodingMode.JAVA_DATA_OUTPUT) {
         warnings.add("bungeecord:main normally uses Java DataOutput/writeUTF fields.");
      }
   }

   private static void writeField(
      ByteArrayOutputStream out, RiptidePayloadTemplate.Field field, RiptidePayloadTemplate.EncodingMode mode, List<String> warnings
   ) throws Exception {
      String value = field.value();
      switch (field.type()) {
         case BYTE:
            out.write((byte)parseLong(value, -128L, 127L));
            break;
         case UNSIGNED_BYTE:
            out.write((byte)parseLong(value, 0L, 255L));
            break;
         case BOOLEAN:
            out.write(Boolean.parseBoolean(value.strip()) ? 1 : 0);
            break;
         case SHORT:
            writeShort(out, (short)parseLong(value, -32768L, 32767L));
            break;
         case UNSIGNED_SHORT:
            writeShort(out, (short)parseLong(value, 0L, 65535L));
            break;
         case CHAR:
            writeShort(out, (short)parseChar(value));
            break;
         case INT:
            writeInt(out, (int)parseLong(value, -2147483648L, 2147483647L));
            break;
         case LONG:
            writeLong(out, parseLong(value, Long.MIN_VALUE, Long.MAX_VALUE));
            break;
         case FLOAT:
            writeInt(out, Float.floatToIntBits(Float.parseFloat(value.strip())));
            break;
         case DOUBLE:
            writeLong(out, Double.doubleToLongBits(Double.parseDouble(value.strip())));
            break;
         case JAVA_WRITE_UTF:
            DataOutputStream dos = new DataOutputStream(out);
            dos.writeUTF(decodeEscapedText(value));
            dos.flush();
            break;
         case RAW_BYTES:
         case HEX_BYTES:
            out.write(parseByteValue(value));
            break;
         case BYTE_ARRAY:
            writeByteArray(out, value);
            break;
         case STRING_BYTES:
         case RAW_UTF8_STRING:
            out.write(decodeEscapedText(value).getBytes(StandardCharsets.UTF_8));
            break;
         case VAR_INT:
         case ENUM_VAR_INT:
            writeVarInt(out, (int)parseLong(value, -2147483648L, 2147483647L));
            break;
         case VAR_LONG:
            writeVarLong(out, parseLong(value, Long.MIN_VALUE, Long.MAX_VALUE));
            break;
         case MINECRAFT_STRING:
         case IDENTIFIER:
            out.write(RiptidePayloadSupport.encodeMinecraftStringPayload(decodeEscapedText(value)));
            break;
         case UUID_FIELD:
            writeUuid(out, UUID.fromString(value.strip()));
            break;
         case BLOCK_POS:
            writeLong(out, packBlockPos(value));
            break;
         case OPTIONAL_VALUE:
            writeOptional(out, value, mode, warnings);
            break;
         case NBT:
            writeNbt(out, value);
            break;
         case ITEM_STACK:
            writeItemStack(out, value);
            break;
         case TEXT_COMPONENT:
         case JSON_STRING:
            writeComponent(out, value);
      }
   }

   private static void writeNbt(ByteArrayOutputStream out, String value) throws Exception {
      String text = value != null && !value.isBlank() ? value.strip() : "{}";
      CompoundTag tag = TagParser.parseCompoundFully(text);
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

      try {
         buf.writeNbt(tag);
         writeBuffer(out, buf);
      } finally {
         buf.release();
      }
   }

   private static void writeComponent(ByteArrayOutputStream out, String value) {
      Component component = parseComponent(value);
      RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess());

      try {
         ComponentSerialization.STREAM_CODEC.encode(buf, component);
         writeBuffer(out, buf);
      } finally {
         buf.release();
      }
   }

   private static Component parseComponent(String value) {
      String text = value != null && !value.isBlank() ? value.strip() : "{\"text\":\"\"}";

      try {
         JsonElement element = JsonParser.parseString(text);
         return (Component)(element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
            ? Component.literal(element.getAsString())
            : (Component)ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow());
      } catch (Throwable var3) {
         return Component.literal(decodeEscapedText(value));
      }
   }

   private static void writeItemStack(ByteArrayOutputStream out, String value) {
      ItemStack stack = parseItemStack(value);
      RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess());

      try {
         ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
         writeBuffer(out, buf);
      } finally {
         buf.release();
      }
   }

   private static ItemStack parseItemStack(String value) {
      String text = value == null ? "" : value.strip();
      if (text.isEmpty() || "{}".equals(text)) {
         return ItemStack.EMPTY;
      } else if (looksLikeSimpleItemId(text)) {
         return itemStackFromId(text, 1);
      } else {
         try {
            JsonElement json = JsonParser.parseString(text);
            return json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()
               ? itemStackFromId(json.getAsString(), 1)
               : (ItemStack)ItemStack.CODEC.parse(registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
         } catch (Throwable var6) {
            try {
               Tag tag = TagParser.parseCompoundFully(text);
               return (ItemStack)ItemStack.CODEC.parse(registryAccess().createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
            } catch (Throwable var5) {
               IllegalArgumentException ex = new IllegalArgumentException("Invalid item stack JSON/SNBT/id");
               ex.addSuppressed(var6);
               ex.addSuppressed(var5);
               throw ex;
            }
         }
      }
   }

   private static boolean looksLikeSimpleItemId(String text) {
      return text != null && text.matches("[a-z0-9_./-]+(:[a-z0-9_./-]+)?");
   }

   private static ItemStack itemStackFromId(String rawId, int count) {
      String idText = rawId == null ? "" : rawId.strip();
      Identifier id = Identifier.parse(idText.contains(":") ? idText : "minecraft:" + idText);
      if (!BuiltInRegistries.ITEM.containsKey(id)) {
         throw new IllegalArgumentException("Unknown item id: " + id);
      } else {
         Item item = (Item)BuiltInRegistries.ITEM.getValue(id);
         if (item == null) {
            throw new IllegalArgumentException("Unknown item id: " + id);
         } else {
            return new ItemStack(item, Math.max(1, count));
         }
      }
   }

   private static RegistryAccess registryAccess() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         return mc.level.registryAccess();
      } else {
         return mc.getConnection() != null ? mc.getConnection().registryAccess() : RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
      }
   }

   private static void writeBuffer(ByteArrayOutputStream out, FriendlyByteBuf buf) {
      byte[] bytes = RiptidePayloadSupport.toByteArray(buf);
      out.write(bytes, 0, bytes.length);
   }

   private static byte[] parseByteValue(String value) {
      String text = value == null ? "" : value.strip();
      if (text.isEmpty()) {
         return new byte[0];
      } else {
         try {
            return RiptidePayloadSupport.parsePayloadBytes(text);
         } catch (Exception var3) {
            return Base64.getDecoder().decode(text);
         }
      }
   }

   private static String decodeEscapedText(String value) {
      if (value != null && !value.isEmpty()) {
         StringBuilder sb = new StringBuilder(value.length());
         boolean escaping = false;

         for (int i = 0; i < value.length(); i++) {
            char chr = value.charAt(i);
            if (!escaping) {
               if (chr == '\\') {
                  escaping = true;
               } else {
                  sb.append(chr);
               }
            } else {
               switch (chr) {
                  case '\\':
                     sb.append('\\');
                     break;
                  case 'n':
                     sb.append('\n');
                     break;
                  case 'r':
                     sb.append('\r');
                     break;
                  case 't':
                     sb.append('\t');
                     break;
                  default:
                     sb.append('\\');
                     sb.append(chr);
               }

               escaping = false;
            }
         }

         if (escaping) {
            sb.append('\\');
         }

         return sb.toString();
      } else {
         return "";
      }
   }

   private static long parseLong(String value, long min, long max) {
      String text = value == null ? "" : value.strip();
      long parsed = Long.decode(text.isEmpty() ? "0" : text);
      if (parsed >= min && parsed <= max) {
         return parsed;
      } else {
         throw new IllegalArgumentException("Value out of range: " + parsed);
      }
   }

   private static int parseChar(String value) {
      String text = value == null ? "" : value.strip();
      if (text.isEmpty()) {
         return 0;
      } else {
         return text.length() == 1 ? text.charAt(0) : (int)parseLong(text, 0L, 65535L);
      }
   }

   private static void writeByteArray(ByteArrayOutputStream out, String value) {
      String text = value == null ? "" : value.strip();
      String prefix = "raw";
      String body = text;
      int split = text.indexOf(58);
      if (split > 0) {
         prefix = text.substring(0, split).strip().toLowerCase(Locale.ROOT);
         body = text.substring(split + 1).strip();
      }

      byte[] bytes = parseByteValue(body);
      switch (prefix) {
         case "varint":
         case "var":
            writeVarInt(out, bytes.length);
            break;
         case "short":
         case "ushort":
            writeShort(out, (short)bytes.length);
            break;
         case "int":
            writeInt(out, bytes.length);
         case "raw":
         case "":
            break;
         default:
            throw new IllegalArgumentException("Byte Array prefix must be raw, varint, short, or int");
      }

      out.write(bytes, 0, bytes.length);
   }

   private static void writeOptional(ByteArrayOutputStream out, String value, RiptidePayloadTemplate.EncodingMode mode, List<String> warnings) throws Exception {
      String text = value == null ? "" : value.strip();
      if (!text.isEmpty() && !"false".equalsIgnoreCase(text) && !"empty".equalsIgnoreCase(text)) {
         out.write(1);
         String body = text;
         int split = text.indexOf(58);
         RiptidePayloadTemplate.FieldType nestedType = RiptidePayloadTemplate.FieldType.MINECRAFT_STRING;
         if (split > 0) {
            nestedType = RiptidePayloadTemplate.FieldType.parse(text.substring(0, split));
            body = text.substring(split + 1).strip();
         }

         writeField(out, new RiptidePayloadTemplate.Field(nestedType, body, true), mode, warnings);
      } else {
         out.write(0);
      }
   }

   private static void writeShort(ByteArrayOutputStream out, short value) {
      out.write(value >>> 8 & 0xFF);
      out.write(value & 255);
   }

   private static void writeInt(ByteArrayOutputStream out, int value) {
      out.write(value >>> 24 & 0xFF);
      out.write(value >>> 16 & 0xFF);
      out.write(value >>> 8 & 0xFF);
      out.write(value & 0xFF);
   }

   private static void writeLong(ByteArrayOutputStream out, long value) {
      for (int shift = 56; shift >= 0; shift -= 8) {
         out.write((int)(value >>> shift & 255L));
      }
   }

   private static void writeVarInt(ByteArrayOutputStream out, int value) {
      while ((value & -128) != 0) {
         out.write(value & 127 | 128);
         value >>>= 7;
      }

      out.write(value);
   }

   private static void writeVarLong(ByteArrayOutputStream out, long value) {
      while ((value & -128L) != 0L) {
         out.write((int)(value & 127L) | 128);
         value >>>= 7;
      }

      out.write((int)value);
   }

   private static void writeUuid(ByteArrayOutputStream out, UUID uuid) {
      writeLong(out, uuid.getMostSignificantBits());
      writeLong(out, uuid.getLeastSignificantBits());
   }

   private static long packBlockPos(String value) {
      String[] parts = (value == null ? "" : value).split(",");
      if (parts.length != 3) {
         throw new IllegalArgumentException("Block Pos must be x,y,z");
      } else {
         long x = Long.parseLong(parts[0].strip()) & 67108863L;
         long y = Long.parseLong(parts[1].strip()) & 4095L;
         long z = Long.parseLong(parts[2].strip()) & 67108863L;
         return x << 38 | z << 12 | y;
      }
   }

   private static String previewUtf8(byte[] bytes) {
      if (bytes != null && bytes.length != 0) {
         String text = RiptidePayloadSupport.decodeLikelyUtf8Text(bytes);
         return text.isBlank() ? "<binary>" : shorten(text.replace('\n', ' '), 96);
      } else {
         return "";
      }
   }

   private static String previewMinecraftString(byte[] bytes) {
      String decoded = RiptidePayloadSupport.decodeMinecraftStringPayload(bytes);
      return decoded == null ? "<not a full Minecraft String>" : shorten(decoded, 96);
   }

   private static String previewJavaUtf(byte[] bytes) {
      if (bytes != null && bytes.length >= 2) {
         int len = ByteBuffer.wrap(bytes, 0, 2).order(ByteOrder.BIG_ENDIAN).getShort() & '\uffff';
         if (len + 2 != bytes.length) {
            return "<not writeUTF>";
         } else {
            try {
               return shorten(new String(bytes, 2, len, StandardCharsets.UTF_8), 96);
            } catch (Throwable var3) {
               return "<invalid writeUTF>";
            }
         }
      } else {
         return "<not writeUTF>";
      }
   }

   private static String shorten(String value, int max) {
      if (value == null) {
         return "";
      } else {
         String text = value.strip();
         return text.length() <= max ? text : text.substring(0, Math.max(0, max - 3)) + "...";
      }
   }

   public record BuildResult(byte[] bytes, List<String> warnings, List<String> errors, RiptidePayloadTemplate.Preview preview) {
      public BuildResult(byte[] bytes, List<String> warnings, List<String> errors, RiptidePayloadTemplate.Preview preview) {
         bytes = bytes == null ? new byte[0] : (byte[])bytes.clone();
         warnings = warnings == null ? List.of() : List.copyOf(warnings);
         errors = errors == null ? List.of() : List.copyOf(errors);
         preview = preview == null ? RiptidePayloadTemplate.preview(bytes) : preview;
         this.bytes = bytes;
         this.warnings = warnings;
         this.errors = errors;
         this.preview = preview;
      }

      public byte[] bytes() {
         return (byte[])this.bytes.clone();
      }

      public boolean ok() {
         return this.errors.isEmpty();
      }
   }

   public static enum EncodingMode {
      RAW_UTF8("Raw UTF-8"),
      MANUAL_HEX("Manual Hex"),
      JAVA_DATA_OUTPUT("Java DataOutput"),
      MINECRAFT_BYTEBUF("Minecraft ByteBuf"),
      JSON_TEXT("JSON/Text"),
      ADVANCED_MIXED("Advanced Mixed");

      private final String label;

      private EncodingMode(String label) {
         this.label = label;
      }

      public String label() {
         return this.label;
      }

      public static RiptidePayloadTemplate.EncodingMode parse(String value, String channel) {
         if (value != null && !value.isBlank()) {
            String normalized = value.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');

            for (RiptidePayloadTemplate.EncodingMode mode : values()) {
               if (mode.name().equals(normalized)) {
                  return mode;
               }
            }
         }

         if (RiptidePayloadSupport.isBrandChannel(channel)) {
            return MINECRAFT_BYTEBUF;
         } else {
            return "bungeecord:main".equalsIgnoreCase(channel == null ? "" : channel.strip()) ? JAVA_DATA_OUTPUT : ADVANCED_MIXED;
         }
      }
   }

   public record Field(RiptidePayloadTemplate.FieldType type, String value, boolean enabled) {
      public Field(RiptidePayloadTemplate.FieldType type, String value, boolean enabled) {
         type = type == null ? RiptidePayloadTemplate.FieldType.RAW_UTF8_STRING : type;
         value = value == null ? "" : value;
         this.type = type;
         this.value = value;
         this.enabled = enabled;
      }

      public String displayLine() {
         return this.type.label() + " = " + this.value;
      }
   }

   public static enum FieldType {
      BYTE("writeByte", "0"),
      UNSIGNED_BYTE("writeUnsignedByte", "0"),
      BOOLEAN("writeBoolean", "true"),
      SHORT("writeShort", "0"),
      UNSIGNED_SHORT("writeUnsignedShort", "0"),
      CHAR("writeChar", "A"),
      INT("writeInt", "0"),
      LONG("writeLong", "0"),
      FLOAT("writeFloat", "0.0"),
      DOUBLE("writeDouble", "0.0"),
      JAVA_WRITE_UTF("writeUTF", "GetServer"),
      RAW_BYTES("writeBytes", ""),
      HEX_BYTES("writeBytesHex", ""),
      BYTE_ARRAY("writeByteArray", "raw:"),
      STRING_BYTES("writeStringBytes", "hello-from-client"),
      RAW_UTF8_STRING("writeStringBytes", "hello-from-client"),
      VAR_INT("writeVarInt", "0"),
      VAR_LONG("writeVarLong", "0"),
      MINECRAFT_STRING("writeMcString", RiptidePayloadSupport.defaultBrandPayloadString()),
      IDENTIFIER("writeIdentifier", "minecraft:brand"),
      UUID_FIELD("writeUUID", "00000000-0000-0000-0000-000000000000"),
      BLOCK_POS("writeBlockPos", "0,64,0"),
      ENUM_VAR_INT("writeEnumVarInt", "0"),
      OPTIONAL_VALUE("writeOptional", "false"),
      NBT("writeNbt", "{}"),
      ITEM_STACK("writeItemStack", "{\"id\":\"minecraft:stone\",\"count\":1}"),
      TEXT_COMPONENT("writeComponent", "{\"text\":\"hello\"}"),
      JSON_STRING("writeComponent", "{\"text\":\"hello\"}");

      private final String label;
      private final String defaultValue;

      private FieldType(String label, String defaultValue) {
         this.label = label;
         this.defaultValue = defaultValue;
      }

      public String label() {
         return this.label;
      }

      public String defaultValue() {
         return this.defaultValue;
      }

      public static RiptidePayloadTemplate.FieldType parse(String value) {
         if (value != null && !value.isBlank()) {
            String normalized = value.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if ("UUID".equals(normalized)) {
               normalized = "UUID_FIELD";
            }

            if ("UTF".equals(normalized) || "WRITEUTF".equals(normalized)) {
               normalized = "JAVA_WRITE_UTF";
            }

            if ("MINECRAFT_STRING".equals(normalized) || "MC_STRING".equals(normalized)) {
               normalized = "MINECRAFT_STRING";
            }

            if ("TEXT_COMPONENT".equals(normalized) || "COMPONENT".equals(normalized)) {
               normalized = "TEXT_COMPONENT";
            }

            for (RiptidePayloadTemplate.FieldType type : values()) {
               if (type.name().equals(normalized)) {
                  return type;
               }

               if (type.label().equalsIgnoreCase(value.strip())) {
                  return type;
               }
            }

            return RAW_UTF8_STRING;
         } else {
            return RAW_UTF8_STRING;
         }
      }
   }

   public static enum PayloadDirection {
      C2S,
      S2C;

      public static RiptidePayloadTemplate.PayloadDirection parse(String value) {
         if (value == null) {
            return C2S;
         } else {
            return "S2C".equalsIgnoreCase(value.strip()) ? S2C : C2S;
         }
      }
   }

   public static enum PayloadPhase {
      PLAY,
      CONFIGURATION;

      public static RiptidePayloadTemplate.PayloadPhase parse(String value) {
         if (value == null) {
            return PLAY;
         } else {
            String normalized = value.strip().toUpperCase(Locale.ROOT);
            return normalized.contains("CONFIG") ? CONFIGURATION : PLAY;
         }
      }
   }

   public record Preview(String hex, String utf8, String minecraftString, String javaWriteUtf) {
   }

   public record Template(
      String channel,
      RiptidePayloadTemplate.PayloadDirection direction,
      RiptidePayloadTemplate.PayloadPhase phase,
      RiptidePayloadTemplate.EncodingMode mode,
      List<RiptidePayloadTemplate.Field> fields
   ) {
      public Template(
         String channel,
         RiptidePayloadTemplate.PayloadDirection direction,
         RiptidePayloadTemplate.PayloadPhase phase,
         RiptidePayloadTemplate.EncodingMode mode,
         List<RiptidePayloadTemplate.Field> fields
      ) {
         channel = channel != null && !channel.isBlank() ? channel.strip() : "minecraft:brand";
         direction = direction == null ? RiptidePayloadTemplate.PayloadDirection.C2S : direction;
         phase = phase == null ? RiptidePayloadTemplate.PayloadPhase.PLAY : phase;
         mode = mode == null ? RiptidePayloadTemplate.EncodingMode.parse("", channel) : mode;
         fields = fields == null ? List.of() : List.copyOf(fields);
         this.channel = channel;
         this.direction = direction;
         this.phase = phase;
         this.mode = mode;
         this.fields = fields;
      }

      public RiptidePayloadTemplate.BuildResult build() {
         List<String> warnings = new ArrayList<>();
         List<String> errors = new ArrayList<>();
         RiptidePayloadTemplate.validateHeader(this.channel, this.direction, this.phase, this.mode, warnings, errors);
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         List<RiptidePayloadTemplate.Field> enabledFields = this.fields.stream().filter(RiptidePayloadTemplate.Field::enabled).toList();
         if (enabledFields.isEmpty()) {
            warnings.add("Payload is empty. Some servers reject empty custom payloads.");
         }

         for (RiptidePayloadTemplate.Field field : enabledFields) {
            try {
               RiptidePayloadTemplate.writeField(out, field, this.mode, warnings);
            } catch (Exception var8) {
               errors.add(field.type().label() + ": " + RiptidePayloadSupport.safeMessage(var8));
            }
         }

         byte[] bytes = out.toByteArray();
         if (bytes.length > 8192) {
            warnings.add("Payload is large (" + bytes.length + "B). Use low repeat rates.");
         }

         if (bytes.length > 32767) {
            errors.add("Payload is " + bytes.length + "B; serverbound custom payload max is 32767B.");
         }

         RiptidePayloadTemplate.Preview preview = RiptidePayloadTemplate.preview(bytes);
         return new RiptidePayloadTemplate.BuildResult(bytes, warnings, errors, preview);
      }
   }
}
