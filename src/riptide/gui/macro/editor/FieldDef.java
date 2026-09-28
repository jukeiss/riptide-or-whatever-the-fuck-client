package riptide.gui.macro.editor;

import java.util.Collections;
import java.util.List;

public final class FieldDef {
   private final String key;
   private final String label;
   private final FieldType type;
   private final int min;
   private final int max;
   private final double decMin;
   private final double decMax;
   private final List<String> enumOptions;
   private final String showWhenKey;
   private final boolean showWhenInverted;
   private final String showWhenValue;
   private final String addLabel;
   private final String[] xyzKeys;
   private final boolean xyzDouble;
   private final CaptureMode captureMode;
   private final String[] mutuallyExclusiveWith;
   private final boolean dynamic;

   FieldDef(
      String key,
      String label,
      FieldType type,
      int min,
      int max,
      double decMin,
      double decMax,
      List<String> enumOptions,
      String showWhenKey,
      boolean showWhenInverted,
      String showWhenValue,
      String addLabel,
      String[] xyzKeys,
      boolean xyzDouble,
      CaptureMode captureMode,
      String[] mutuallyExclusiveWith,
      boolean dynamic
   ) {
      this.key = key;
      this.label = label;
      this.type = type;
      this.min = min;
      this.max = max;
      this.decMin = decMin;
      this.decMax = decMax;
      this.enumOptions = enumOptions != null ? Collections.unmodifiableList(enumOptions) : Collections.emptyList();
      this.showWhenKey = showWhenKey;
      this.showWhenInverted = showWhenInverted;
      this.showWhenValue = showWhenValue;
      this.addLabel = addLabel != null ? addLabel : "Add";
      this.xyzKeys = xyzKeys != null ? (String[])xyzKeys.clone() : new String[]{"x", "y", "z"};
      this.xyzDouble = xyzDouble;
      this.captureMode = captureMode != null ? captureMode : CaptureMode.NONE;
      this.mutuallyExclusiveWith = mutuallyExclusiveWith != null ? (String[])mutuallyExclusiveWith.clone() : new String[0];
      this.dynamic = dynamic;
   }

   public String key() {
      return this.key;
   }

   public String label() {
      return this.label;
   }

   public FieldType type() {
      return this.type;
   }

   public int min() {
      return this.min;
   }

   public int max() {
      return this.max;
   }

   public double decMin() {
      return this.decMin;
   }

   public double decMax() {
      return this.decMax;
   }

   public List<String> enumOptions() {
      return this.enumOptions;
   }

   public String showWhenKey() {
      return this.showWhenKey;
   }

   public boolean showWhenInverted() {
      return this.showWhenInverted;
   }

   public String showWhenValue() {
      return this.showWhenValue;
   }

   public String addLabel() {
      return this.addLabel;
   }

   public String[] xyzKeys() {
      return (String[])this.xyzKeys.clone();
   }

   public boolean xyzDouble() {
      return this.xyzDouble;
   }

   public boolean hasShowWhen() {
      return this.showWhenKey != null && !this.showWhenKey.isEmpty();
   }

   public CaptureMode captureMode() {
      return this.captureMode;
   }

   public String[] mutuallyExclusiveWith() {
      return (String[])this.mutuallyExclusiveWith.clone();
   }

   public boolean hasMutualExclusion() {
      return this.mutuallyExclusiveWith.length > 0;
   }

   public boolean dynamic() {
      return this.dynamic;
   }
}
