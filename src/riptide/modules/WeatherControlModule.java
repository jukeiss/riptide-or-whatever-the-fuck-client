package riptide.modules;

import riptide.api.module.BoolSetting;

public final class WeatherControlModule extends Module {
   private static volatile boolean hideRain;
   private static volatile boolean hideThunder;
   private static volatile boolean hideParticles;

   public WeatherControlModule() {
      super("weather-control", "WeatherControl", ModuleCategory.RENDER, "Hides rain, snow and storm darkening. Local only — the server still has its weather.");
      this.add(
         new BoolSetting("rain", "No Rain", true).description("Stop rain and snow being drawn, and stop them darkening the world.").group("General").build()
      );
      this.add(new BoolSetting("thunder", "No Storm Darkening", true).description("Stop a thunderstorm dimming everything.").group("General").build());
      this.add(
         new BoolSetting("particles", "No Splashes", false).description("Also stop the splash particles rain makes on the ground.").group("General").build()
      );
   }

   @Override
   public void onEnable() {
      this.refresh();
   }

   @Override
   public void onDisable() {
      hideRain = false;
      hideThunder = false;
      hideParticles = false;
   }

   @Override
   protected void onOptionValueChanged(String var1) {
      this.refresh();
   }

   @Override
   protected void onSettingsReset() {
      this.refresh();
   }

   private void refresh() {
      boolean var1 = this.isEnabled();
      hideRain = var1 && this.bool("rain");
      hideThunder = var1 && this.bool("thunder");
      hideParticles = var1 && this.bool("particles");
   }

   public static float rainLevelOverride() {
      return hideRain ? 0.0F : -1.0F;
   }

   public static float thunderLevelOverride() {
      return hideThunder ? 0.0F : -1.0F;
   }

   public static boolean suppressWeatherRender() {
      return hideRain;
   }

   public static boolean suppressRainParticles() {
      return hideParticles;
   }

   @Override
   public String info() {
      if (hideRain && hideThunder) {
         return "clear";
      } else if (hideRain) {
         return "no rain";
      } else {
         return hideThunder ? "no storm" : "";
      }
   }
}
