package riptide.modules;

import riptide.api.module.ChoiceSetting;

public final class AntiBotModule extends Module {
   public AntiBotModule() {
      super("antibot", "AntiBot", ModuleCategory.MISC, "Ignores fake/bot players.");
      this.add(new ChoiceSetting("mode", "Mode", "Conservative", "Conservative", "Aggressive").description("Detection strictness preset").build());
   }

   @Override
   public void tick() {
      RiptideAntiBot.tick();
   }

   @Override
   public void onDisable() {
      RiptideAntiBot.reset();
   }

   @Override
   public void onGameJoin() {
      RiptideAntiBot.reset();
   }

   @Override
   public void onGameLeft() {
      RiptideAntiBot.reset();
   }
}
