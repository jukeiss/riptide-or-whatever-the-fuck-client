package riptide.api.module;

public final class KeybindSetting extends Setting<Integer, KeybindSetting> {
   public KeybindSetting(String name, String title, int defaultKey) {
      super(Kind.KEYBIND, name, title, defaultKey);
   }

   protected Integer decode(String raw) {
      if (raw == null) {
         return this.defaultValueTyped();
      } else {
         try {
            return Integer.parseInt(raw.trim());
         } catch (Exception var3) {
            return this.defaultValueTyped();
         }
      }
   }

   protected String encode(Integer value) {
      return Integer.toString(value == null ? this.defaultValueTyped() : value);
   }
}
