package riptide.api.module;

public final class SettingGroup {
   private final String name;

   public SettingGroup(String name) {
      this.name = name != null && !name.isBlank() ? name : "General";
   }

   public String name() {
      return this.name;
   }

   public <T, S extends Setting<T, S>> S apply(S setting) {
      if (setting != null) {
         setting.group(this.name);
      }

      return setting;
   }
}
