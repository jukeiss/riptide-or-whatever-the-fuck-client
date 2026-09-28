package riptide.api.module;

import java.util.Locale;

public final class ColorSetting extends Setting<Integer, ColorSetting> {
   public ColorSetting(String name, String title, int defaultArgb) {
      super(Kind.COLOR, name, title, defaultArgb);
   }

   protected Integer decode(String raw) {
      Integer parsed = parseColor(raw);
      return parsed == null ? this.defaultValueTyped() : parsed;
   }

   protected String encode(Integer value) {
      int argb = value == null ? this.defaultValueTyped() : value;
      return String.format(Locale.ROOT, "%08X", argb);
   }

   static Integer parseColor(String value) {
      if (value == null) {
         return null;
      } else {
         String text = value.trim();
         if (text.isEmpty()) {
            return null;
         } else {
            if (text.startsWith("#")) {
               text = text.substring(1);
            } else if (text.startsWith("0x") || text.startsWith("0X")) {
               text = text.substring(2);
            }

            try {
               long parsed = Long.parseUnsignedLong(text, 16);
               if (text.length() <= 6) {
                  parsed |= 4278190080L;
               }

               return (int)parsed;
            } catch (Exception var4) {
               return null;
            }
         }
      }
   }
}
