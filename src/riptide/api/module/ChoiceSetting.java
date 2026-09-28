package riptide.api.module;

import java.util.Arrays;
import java.util.List;

public final class ChoiceSetting extends Setting<String, ChoiceSetting> {
   public ChoiceSetting(String name, String title, String defaultValue, String... choices) {
      super(Kind.ENUM, name, title, defaultValue == null ? "" : defaultValue);
      this.setChoices(choices == null ? List.of() : Arrays.asList(choices));
   }

   public ChoiceSetting(String name, String title, String defaultValue, List<String> choices) {
      super(Kind.ENUM, name, title, defaultValue == null ? "" : defaultValue);
      this.setChoices(choices);
   }

   protected String decode(String raw) {
      if (raw == null) {
         return this.defaultValueTyped();
      } else {
         String needle = raw.trim();
         List<String> options = this.choices();
         if (options.isEmpty()) {
            return needle;
         } else {
            for (String choice : options) {
               if (choice.equalsIgnoreCase(needle)) {
                  return choice;
               }
            }

            return this.defaultValueTyped();
         }
      }
   }

   protected String encode(String value) {
      if (value == null) {
         return this.defaultValueTyped();
      } else {
         List<String> options = this.choices();
         if (options.isEmpty()) {
            return value;
         } else {
            for (String choice : options) {
               if (choice.equalsIgnoreCase(value)) {
                  return choice;
               }
            }

            return this.defaultValueTyped();
         }
      }
   }
}
