package riptide.modules;

import riptide.api.module.IntSetting;
import riptide.util.RiptideHudManager;

public final class CompassModule extends Module {
   private static final String ELEMENT = "compass";

   public CompassModule() {
      super("compass", "Compass", ModuleCategory.RENDER, "A compass strip showing which way you're facing.");
      this.add(new IntSetting("width", "Width", 180, 80, 400, 10).description("How wide the compass strip is drawn.").build());
   }

   @Override
   public void onEnable() {
      RiptideHudManager.setEnabled("compass", true);
      this.push();
   }

   @Override
   public void onDisable() {
      RiptideHudManager.setEnabled("compass", false);
   }

   @Override
   protected void onOptionValueChanged(String var1) {
      if (this.isEnabled()) {
         this.push();
      }
   }

   private void push() {
      RiptideHudManager.setSetting("compass", "compass-width", Integer.toString(this.integer("width")));
   }

   @Override
   public String info() {
      return this.integer("width") + "px";
   }
}
