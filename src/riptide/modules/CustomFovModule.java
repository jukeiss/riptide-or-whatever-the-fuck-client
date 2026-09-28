package riptide.modules;

import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;

public final class CustomFovModule extends Module {
   private static volatile boolean active;
   private static volatile int target = 100;
   private static volatile boolean fixed;

   public CustomFovModule() {
      super("custom-fov", "Custom FOV", ModuleCategory.RENDER, "Sets your field of view past the vanilla 30-110 range.");
      this.add(new IntSetting("fov", "FOV", 120, 10, 170, 1).unit("degrees").description("Field of view to use in place of the vanilla slider.").build());
      this.add(
         new BoolSetting("static", "Static", false)
            .description("Ignore sprinting, speed effects and bows changing your FOV. Zoom still works.")
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.sync();
   }

   @Override
   public void onDisable() {
      active = false;
   }

   @Override
   public void tick() {
      this.sync();
   }

   private void sync() {
      target = this.integer("fov");
      fixed = this.bool("static");
      active = this.isEnabled();
   }

   @Override
   public String info() {
      return this.integer("fov") + "°";
   }

   // Called from RiptideCameraZoomMixin before zoom is applied.
   public static float apply(float fov) {
      if (!active || PackHideState.isActive()) {
         return fov;
      } else if (fixed) {
         return target;
      } else {
         try {
            int base = MC.options.fov().get();
            // Keep vanilla's dynamic multipliers (sprint, speed, bow) by rescaling around the slider value.
            return base <= 0 ? fov : fov * target / base;
         } catch (Throwable var2) {
            return fov;
         }
      }
   }
}
