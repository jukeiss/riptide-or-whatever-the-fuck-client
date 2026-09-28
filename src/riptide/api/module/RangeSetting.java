package riptide.api.module;

public final class RangeSetting extends Setting<ValueRange, RangeSetting> {
   private double minSeparation;

   public RangeSetting(String name, String title, ValueRange defaultValue, double min, double max, double step) {
      super(Kind.STRING, name, title, (defaultValue == null ? new ValueRange(min, max) : defaultValue).clamp(min, Math.max(min, max)));
      this.setRange(min, max);
      this.setSliderRange(min, max);
      this.setStep(step);
      this.displayMode(DisplayMode.RANGE_SLIDER);
   }

   public RangeSetting(String name, String title, double defaultMin, double defaultMax, double min, double max, double step) {
      this(name, title, new ValueRange(defaultMin, defaultMax), min, max, step);
   }

   public RangeSetting minSeparation(double separation) {
      this.minSeparation = Math.max(0.0, separation);
      return this;
   }

   public double minSeparation() {
      return this.minSeparation;
   }

   protected ValueRange decode(String raw) {
      return this.sanitizeTyped(ValueRange.parse(raw, this.defaultValueTyped()));
   }

   protected String encode(ValueRange value) {
      return this.sanitizeTyped(value).toString();
   }

   protected ValueRange sanitizeTyped(ValueRange value) {
      ValueRange range = value == null ? this.defaultValueTyped() : value;
      return range.withMinSeparation(this.minSeparation, this.min(), this.max(), false);
   }
}
