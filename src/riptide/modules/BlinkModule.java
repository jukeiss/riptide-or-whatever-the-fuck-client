package riptide.modules;

import java.util.Locale;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;

public final class BlinkModule extends Module {
   public BlinkModule() {
      super("blink", "Blink", ModuleCategory.MISC, "Suspends packets to/from the server, released on disable.");
      this.add(new BoolSetting("outgoing", "Outgoing", true).description("Hold sent packets").build());
      this.add(new BoolSetting("incoming", "Incoming", false).description("Hold received packets").build());
      this.add(new BoolSetting("hold-movement", "Hold Movement", true).visibleWhen(() -> this.bool("outgoing")).description("Delay movement packets").build());
      this.add(
         new BoolSetting("hold-actions", "Hold Attack/Interact", true).visibleWhen(() -> this.bool("outgoing")).description("Delay action packets").build()
      );
      this.add(new BoolSetting("show-position", "Show Position", true).description("Draw server position").build());
      this.add(new BoolSetting("auto-reset", "Auto Reset", false).description("Flush periodically").build());
      this.add(
         new IntSetting("reset-after", "Reset After (ticks)", 50, 1, 100000, 10)
            .sliderRange(1.0, 100.0)
            .visibleWhen(() -> this.bool("auto-reset"))
            .description("Ticks between flushes")
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.pushConfig();
      RiptideBlinkManager.captureServerPos();
   }

   @Override
   public void onDisable() {
      RiptideBlinkManager.disableAndFlush();
   }

   @Override
   protected void onOptionValueChanged(String optionId) {
      if (this.isEnabled()) {
         this.pushConfig();
      }
   }

   @Override
   public String info() {
      int held = RiptideBlinkManager.held();
      if (held <= 0) {
         return "";
      } else {
         int reset = RiptideBlinkManager.ticksUntilReset();
         return reset >= 0 ? held + " | " + String.format(Locale.ROOT, "%.1fs", reset / 20.0) : Integer.toString(held);
      }
   }

   private void pushConfig() {
      RiptideBlinkManager.setDirections(this.bool("incoming"), this.bool("outgoing"));
      RiptideBlinkManager.setScope(this.bool("hold-movement"), this.bool("hold-actions"));
      RiptideBlinkManager.setShowPosition(this.bool("show-position"));
      RiptideBlinkManager.setAutoReset(this.bool("auto-reset"), this.integer("reset-after"));
   }
}
