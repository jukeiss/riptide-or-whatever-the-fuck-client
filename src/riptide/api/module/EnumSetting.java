package riptide.api.module;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class EnumSetting<E extends Enum<E>> extends Setting<E, EnumSetting<E>> {
   private final E[] values;
   private final Function<E, String> tokenizer;

   public EnumSetting(String name, String title, E defaultValue, E[] values, Function<E, String> tokenizer) {
      super(Kind.ENUM, name, title, defaultValue);
      this.values = (E[])(values == null ? newArray(defaultValue) : values);
      this.tokenizer = tokenizer == null ? Enum::name : tokenizer;
      List<String> labels = new ArrayList<>();

      for (E value : this.values) {
         labels.add(this.token(value));
      }

      this.setChoices(labels);
   }

   public EnumSetting(String name, String title, E defaultValue, E[] values) {
      this(name, title, defaultValue, values, EnumSetting::titleCase);
   }

   private String token(E value) {
      if (value == null) {
         return "";
      } else {
         try {
            String label = this.tokenizer.apply(value);
            return label != null && !label.isBlank() ? label : value.name();
         } catch (Throwable var3) {
            return value.name();
         }
      }
   }

   protected E decode(String raw) {
      if (raw == null) {
         return this.defaultValueTyped();
      } else {
         String needle = raw.trim();
         if (needle.isEmpty()) {
            return this.defaultValueTyped();
         } else {
            for (E value : this.values) {
               if (this.token(value).equalsIgnoreCase(needle)) {
                  return value;
               }
            }

            for (E valuex : this.values) {
               if (valuex.name().equalsIgnoreCase(needle)) {
                  return valuex;
               }
            }

            return this.defaultValueTyped();
         }
      }
   }

   protected String encode(E value) {
      return this.token(value == null ? this.defaultValueTyped() : value);
   }

   private static <E extends Enum<E>> E[] newArray(E single) {
      E[] array = (E[])Array.newInstance(single.getClass(), 1);
      array[0] = single;
      return array;
   }

   public static String titleCase(Enum<?> value) {
      String[] words = value.name().toLowerCase(Locale.ROOT).split("_");
      StringBuilder out = new StringBuilder();

      for (String word : words) {
         if (!word.isEmpty()) {
            if (out.length() > 0) {
               out.append(' ');
            }

            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
         }
      }

      return out.toString();
   }
}
