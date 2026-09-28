package riptide.util.macro;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

final class MacroStringList {
   private MacroStringList() {
   }

   static ListTag toTag(List<String> values) {
      ListTag list = new ListTag();
      if (values == null) {
         return list;
      } else {
         for (String value : values) {
            if (value != null && !value.isBlank()) {
               list.add(StringTag.valueOf(value));
            }
         }

         return list;
      }
   }

   static ArrayList<String> fromTag(ListTag list) {
      ArrayList<String> values = new ArrayList<>();
      if (list == null) {
         return values;
      } else {
         for (int i = 0; i < list.size(); i++) {
            String value = list.getString(i).orElse("");
            if (!value.isBlank()) {
               values.add(value);
            }
         }

         return values;
      }
   }

   static <E extends Enum<E>> E enumValue(Class<E> type, String raw, E fallback) {
      if (raw != null && !raw.isBlank()) {
         try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException var4) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }
}
