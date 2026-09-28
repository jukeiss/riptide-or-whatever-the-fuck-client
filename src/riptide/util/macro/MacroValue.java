package riptide.util.macro;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MacroValue {
   private final MacroValue.Kind kind;
   private final String value;
   private final Map<String, MacroValue> properties;

   private MacroValue(MacroValue.Kind kind, String value, Map<String, MacroValue> properties) {
      this.kind = kind == null ? MacroValue.Kind.TEXT : kind;
      this.value = sanitize(value);
      this.properties = properties != null && !properties.isEmpty() ? Collections.unmodifiableMap(new LinkedHashMap<>(properties)) : Map.of();
   }

   public static MacroValue text(Object value) {
      return new MacroValue(MacroValue.Kind.TEXT, value == null ? "" : String.valueOf(value), Map.of());
   }

   public static MacroValue number(Number value) {
      String rendered = value == null ? "0" : new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
      return new MacroValue(MacroValue.Kind.NUMBER, rendered, Map.of());
   }

   public static MacroValue identifier(String value) {
      return new MacroValue(MacroValue.Kind.IDENTIFIER, value, Map.of());
   }

   public static MacroValue slot(int value) {
      return new MacroValue(MacroValue.Kind.SLOT, Integer.toString(value), Map.of());
   }

   public static MacroValue structured(MacroValue.Kind kind, String value, Map<String, MacroValue> properties) {
      return new MacroValue(kind, value, properties);
   }

   public MacroValue.Kind kind() {
      return this.kind;
   }

   public String value() {
      return this.value;
   }

   public Map<String, MacroValue> properties() {
      return this.properties;
   }

   public Optional<MacroValue> property(String path) {
      if (path != null && !path.isBlank()) {
         MacroValue current = this;

         for (String segment : path.split("\\.")) {
            if (!segment.isBlank()) {
               current = current.properties.get(segment.toLowerCase(Locale.ROOT));
               if (current == null) {
                  return Optional.empty();
               }
            }
         }

         return Optional.of(current);
      } else {
         return Optional.of(this);
      }
   }

   public MacroValue withProperty(String name, MacroValue property) {
      if (name != null && !name.isBlank() && property != null) {
         Map<String, MacroValue> copy = new LinkedHashMap<>(this.properties);
         copy.put(name.trim().toLowerCase(Locale.ROOT), property);
         return new MacroValue(this.kind, this.value, copy);
      } else {
         return this;
      }
   }

   private static String sanitize(String raw) {
      if (raw != null && !raw.isEmpty()) {
         StringBuilder out = new StringBuilder(Math.min(raw.length(), 4096));
         int offset = 0;

         while (offset < raw.length() && out.length() < 4096) {
            int cp = raw.codePointAt(offset);
            offset += Character.charCount(cp);
            if (cp != 10 && cp != 13 && !Character.isISOControl(cp)) {
               out.appendCodePoint(cp);
            } else {
               out.append(' ');
            }
         }

         return out.toString().trim();
      } else {
         return "";
      }
   }

   public static enum Kind {
      TEXT,
      NUMBER,
      BOOLEAN,
      IDENTIFIER,
      ITEM,
      SLOT,
      ENTITY,
      PACKET,
      POSITION,
      GUI;
   }
}
