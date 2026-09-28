package riptide.api.module;

public final class IntSetting extends Setting<Integer, IntSetting> {
   public IntSetting(String name, String title, int defaultValue, int min, int max, int step) {
      super(Kind.INTEGER, name, title, clampStatic(defaultValue, min, Math.max(min, max)));
      this.setRange(min, max);
      this.setSliderRange(min, max);
      this.setStep(Math.max(1, step));
   }

   public IntSetting sliderRange(double min, double max) {
      this.setSliderRange(min, max);
      return this;
   }

   protected Integer decode(String raw) {
      if (raw == null) {
         return this.defaultValueTyped();
      } else {
         try {
            return (int)clampStatic((long)Double.parseDouble(raw.trim()), (long)this.min(), (long)this.max());
         } catch (Exception var3) {
            return this.defaultValueTyped();
         }
      }
   }

   protected String encode(Integer value) {
      int v = value == null ? this.defaultValueTyped() : value;
      return Integer.toString((int)clampStatic((long)v, (long)this.min(), (long)this.max()));
   }

   protected Integer sanitizeTyped(Integer value) {
      int v = value == null ? this.defaultValueTyped() : value;
      return (int)clampStatic((long)v, (long)this.min(), (long)this.max());
   }

   private static int clampStatic(int value, int min, int max) {
      return (int)clampStatic((long)value, (long)min, (long)max);
   }

   private static long clampStatic(long value, long min, long max) {
      return Math.max(min, Math.min(max, value));
   }
}
