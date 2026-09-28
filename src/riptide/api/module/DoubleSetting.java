package riptide.api.module;

public final class DoubleSetting extends Setting<Double, DoubleSetting> {
   public DoubleSetting(String name, String title, double defaultValue, double min, double max, double step) {
      super(Kind.DOUBLE, name, title, clamp(defaultValue, min, Math.max(min, max)));
      this.setRange(min, max);
      this.setSliderRange(min, max);
      this.setStep(step <= 0.0 ? 0.1 : step);
   }

   public DoubleSetting sliderRange(double min, double max) {
      this.setSliderRange(min, max);
      return this;
   }

   protected Double decode(String raw) {
      if (raw == null) {
         return this.defaultValueTyped();
      } else {
         try {
            return clamp(Double.parseDouble(raw.trim()), this.min(), this.max());
         } catch (Exception var3) {
            return this.defaultValueTyped();
         }
      }
   }

   protected String encode(Double value) {
      double v = value == null ? this.defaultValueTyped() : value;
      return Double.toString(clamp(v, this.min(), this.max()));
   }

   protected Double sanitizeTyped(Double value) {
      double v = value == null ? this.defaultValueTyped() : value;
      return clamp(v, this.min(), this.max());
   }

   private static double clamp(double value, double min, double max) {
      return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
   }
}
