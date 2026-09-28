package riptide.modules;

import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.util.macro.PingSpoofController;

public final class PingSpoofModule extends Module {
   public PingSpoofModule() {
      super("ping-spoof", "PingSpoof", ModuleCategory.MISC, "Adds latency to your ping by delaying keep-alives.");
      this.add(new IntSetting("delay", "Delay (ms)", 250, 0, 500, 25).description("Latency added to ping.").build());
      this.add(new BoolSetting("real-incoming", "Real: Incoming", false).description("Delay packets you receive.").build());
      this.add(new BoolSetting("real-outgoing", "Real: Outgoing", false).description("Delay packets you send.").build());
   }

   @Override
   public void onEnable() {
      this.pushOverride();
   }

   @Override
   public void onDisable() {
      PingSpoofController.clearModuleOverride();
   }

   @Override
   protected void onOptionValueChanged(String optionId) {
      if (this.isEnabled()) {
         this.pushOverride();
      }
   }

   private void pushOverride() {
      PingSpoofController.setModuleOverride(this.integer("delay"), this.bool("real-incoming"), this.bool("real-outgoing"));
   }
}
