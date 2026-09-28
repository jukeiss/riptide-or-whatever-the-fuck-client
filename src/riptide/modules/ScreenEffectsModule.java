package riptide.modules;

import riptide.api.module.BoolSetting;

public final class ScreenEffectsModule extends Module {
   private static volatile boolean noHurtCam;
   private static volatile boolean noFire;
   private static volatile boolean noWater;
   private static volatile boolean noBlockOverlay;

   public ScreenEffectsModule() {
      super("screen-effects", "ScreenEffects", ModuleCategory.RENDER, "Removes the fire, water and hurt overlays that cover the screen.");
      this.add(new BoolSetting("hurt-cam", "No Hurt Camera", true).description("Stop the camera tilting when you take damage.").group("General").build());
      this.add(new BoolSetting("fire", "No Fire Overlay", true).description("Stop flames covering the screen while you are burning.").group("General").build());
      this.add(
         new BoolSetting("water", "No Water Overlay", false)
            .description("Stop the blue tint underwater. Off by default, since it is the only thing that tells you that you are in water.")
            .group("General")
            .build()
      );
      this.add(
         new BoolSetting("block-overlay", "No Pumpkin Overlay", true)
            .description("Stop a worn pumpkin or a block you are inside narrowing the view.")
            .group("General")
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.refresh();
   }

   @Override
   public void onDisable() {
      noBlockOverlay = false;
      noWater = false;
      noFire = false;
      noHurtCam = false;
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
      noHurtCam = var1 && this.bool("hurt-cam");
      noFire = var1 && this.bool("fire");
      noWater = var1 && this.bool("water");
      noBlockOverlay = var1 && this.bool("block-overlay");
   }

   public static boolean suppressHurtCamera() {
      return noHurtCam;
   }

   public static boolean suppressFire() {
      return noFire;
   }

   public static boolean suppressWater() {
      return noWater;
   }

   public static boolean suppressBlockOverlay() {
      return noBlockOverlay;
   }
}
