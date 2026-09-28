package riptide.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class RiptidePacketSchemaRegistry {
   private static volatile Map<String, RiptidePacketSchemaRegistry.PacketSchema> schemas;

   private RiptidePacketSchemaRegistry() {
   }

   public static RiptidePacketSchemaRegistry.PacketSchema find(Class<?> packetClass) {
      if (packetClass == null) {
         return null;
      } else {
         Map<String, RiptidePacketSchemaRegistry.PacketSchema> map = load();
         RiptidePacketSchemaRegistry.PacketSchema exact = map.get(packetClass.getName());
         if (exact != null) {
            return exact;
         } else {
            exact = map.get(packetClass.getSimpleName());
            if (exact != null) {
               return exact;
            } else {
               for (Class<?> parent = packetClass.getSuperclass(); parent != null && parent != Object.class; parent = parent.getSuperclass()) {
                  exact = map.get(parent.getName());
                  if (exact != null) {
                     return exact.asFallbackFor(packetClass);
                  }
               }

               return null;
            }
         }
      }
   }

   public static int schemaCount() {
      return load().values().stream().map(RiptidePacketSchemaRegistry.PacketSchema::className).collect(Collectors.toSet()).size();
   }

   private static Map<String, RiptidePacketSchemaRegistry.PacketSchema> load() {
      Map<String, RiptidePacketSchemaRegistry.PacketSchema> current = schemas;
      if (current != null) {
         return current;
      } else {
         synchronized (RiptidePacketSchemaRegistry.class) {
            current = schemas;
            if (current != null) {
               return current;
            } else {
               Map<String, RiptidePacketSchemaRegistry.PacketSchema> loaded = new LinkedHashMap<>();

               try (InputStream in = RiptidePacketSchemaRegistry.class.getResourceAsStream("/riptide-packet-schemas.tsv")) {
                  if (in != null) {
                     String line;
                     try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        while ((line = reader.readLine()) != null) {
                           if (!line.isBlank() && !line.startsWith("#")) {
                              RiptidePacketSchemaRegistry.PacketSchema schema = parse(line);
                              if (schema != null) {
                                 loaded.put(schema.className(), schema);
                                 loaded.put(schema.simpleName(), schema);
                              }
                           }
                        }
                     }
                  }
               } catch (Throwable var12) {
                  loaded.clear();
               }

               schemas = Collections.unmodifiableMap(loaded);
               return schemas;
            }
         }
      }
   }

   private static RiptidePacketSchemaRegistry.PacketSchema parse(String line) {
      String[] parts = line.split("\t", -1);
      if (parts.length < 8) {
         return null;
      } else {
         List<RiptidePacketSchemaRegistry.FieldSchema> fields = new ArrayList<>();
         if (!parts[7].isBlank()) {
            for (String fieldRaw : parts[7].split("\\|", -1)) {
               String[] fieldParts = fieldRaw.split("~", -1);
               if (fieldParts.length >= 4) {
                  fields.add(
                     new RiptidePacketSchemaRegistry.FieldSchema(
                        fieldParts[0], fieldParts[1], fieldParts[2].toLowerCase(Locale.ROOT), Boolean.parseBoolean(fieldParts[3])
                     )
                  );
               }
            }
         }

         return new RiptidePacketSchemaRegistry.PacketSchema(
            parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], Boolean.parseBoolean(parts[6]), fields, false
         );
      }
   }

   private static String blankTo(String value, String fallback) {
      return value != null && !value.isBlank() ? value : fallback;
   }

   public record FieldSchema(String name, String javaType, String valueKind, boolean editableCandidate) {
      public FieldSchema(String name, String javaType, String valueKind, boolean editableCandidate) {
         name = RiptidePacketSchemaRegistry.blankTo(name, "field");
         javaType = RiptidePacketSchemaRegistry.blankTo(javaType, "Object");
         valueKind = RiptidePacketSchemaRegistry.blankTo(valueKind, "object");
         this.name = name;
         this.javaType = javaType;
         this.valueKind = valueKind;
         this.editableCandidate = editableCandidate;
      }
   }

   public record PacketSchema(
      String className,
      String protocol,
      String direction,
      String codecStyle,
      String packetType,
      String source,
      boolean complete,
      List<RiptidePacketSchemaRegistry.FieldSchema> fields,
      boolean inheritedFallback
   ) {
      public PacketSchema(
         String className,
         String protocol,
         String direction,
         String codecStyle,
         String packetType,
         String source,
         boolean complete,
         List<RiptidePacketSchemaRegistry.FieldSchema> fields,
         boolean inheritedFallback
      ) {
         protocol = RiptidePacketSchemaRegistry.blankTo(protocol, "unknown");
         direction = RiptidePacketSchemaRegistry.blankTo(direction, "ANY");
         codecStyle = RiptidePacketSchemaRegistry.blankTo(codecStyle, "unknown");
         packetType = packetType == null ? "" : packetType;
         source = RiptidePacketSchemaRegistry.blankTo(source, "fallback");
         fields = fields == null ? List.of() : List.copyOf(fields);
         this.className = className;
         this.protocol = protocol;
         this.direction = direction;
         this.codecStyle = codecStyle;
         this.packetType = packetType;
         this.source = source;
         this.complete = complete;
         this.fields = fields;
         this.inheritedFallback = inheritedFallback;
      }

      public String simpleName() {
         String name = this.className == null ? "" : this.className.substring(this.className.lastIndexOf(46) + 1);
         return name.replace('$', '.');
      }

      public RiptidePacketSchemaRegistry.PacketSchema asFallbackFor(Class<?> concreteClass) {
         return concreteClass == null
            ? this
            : new RiptidePacketSchemaRegistry.PacketSchema(
               concreteClass.getName(), this.protocol, this.direction, this.codecStyle, this.packetType, this.source, this.complete, this.fields, true
            );
      }
   }
}
