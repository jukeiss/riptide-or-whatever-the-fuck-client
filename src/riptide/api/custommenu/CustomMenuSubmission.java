package riptide.api.custommenu;

import java.util.Map;

public record CustomMenuSubmission(Map<String, String> values, CustomMenuButton button) {
   public CustomMenuSubmission(Map<String, String> values, CustomMenuButton button) {
      values = values == null ? Map.of() : Map.copyOf(values);
      this.values = values;
      this.button = button;
   }
}
