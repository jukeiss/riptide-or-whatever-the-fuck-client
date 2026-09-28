package riptide.modules;

import riptide.api.module.BoolSetting;
import riptide.util.RiptideClientMessaging;

public final class WeatherNotifierModule extends Module {
   private int last = -1;

   public WeatherNotifierModule() {
      super("weather-notifier", "Weather Notifier", ModuleCategory.MISC, "Announces in chat when the weather changes.");
      this.add(new BoolSetting("rain", "Rain", true).description("Announce when it starts or stops raining.").build());
      this.add(new BoolSetting("thunder", "Thunder", true).description("Announce when a thunderstorm starts or ends.").build());
   }

   @Override
   public void onEnable() {
      this.last = -1;
   }

   @Override
   public void tick() {
      if (MC.level == null) {
         this.last = -1;
      } else {
         int var1 = MC.level.isThundering() ? 2 : (MC.level.isRaining() ? 1 : 0);
         if (this.last == -1) {
            this.last = var1;
         } else if (var1 != this.last) {
            boolean var2 = this.bool("rain");
            boolean var3 = this.bool("thunder");

            String var4 = switch (var1) {
               case 1 -> var2 ? "It started raining." : null;
               case 2 -> var3 ? "Thunderstorm rolling in." : null;
               default -> this.last == 2 && var3 ? "The thunderstorm cleared." : (this.last == 1 && var2 ? "The rain stopped." : null);
            };
            this.last = var1;
            if (var4 != null) {
               RiptideClientMessaging.sendPrefixed(var4);
            }
         }
      }
   }
}
