package riptide.api.custommenu;

import java.util.List;

public record CustomMenuInput(
   int index,
   String key,
   String label,
   CustomMenuInput.Kind kind,
   String initialValue,
   int maxLength,
   double min,
   double max,
   double step,
   List<String> options
) {
   public CustomMenuInput(
      int index,
      String key,
      String label,
      CustomMenuInput.Kind kind,
      String initialValue,
      int maxLength,
      double min,
      double max,
      double step,
      List<String> options
   ) {
      key = key == null ? "" : key;
      label = label == null ? "" : label;
      kind = kind == null ? CustomMenuInput.Kind.TEXT : kind;
      initialValue = initialValue == null ? "" : initialValue;
      options = options == null ? List.of() : List.copyOf(options);
      this.index = index;
      this.key = key;
      this.label = label;
      this.kind = kind;
      this.initialValue = initialValue;
      this.maxLength = maxLength;
      this.min = min;
      this.max = max;
      this.step = step;
      this.options = options;
   }

   public static enum Kind {
      TEXT,
      BOOLEAN,
      NUMBER,
      OPTION;
   }
}
