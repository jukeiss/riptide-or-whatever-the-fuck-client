package riptide.modules;

import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;

public final class FreeLookZoomModule extends Module {
   private static FreeLookZoomModule cached;
   private static float distance = -1.0F;
   private static boolean wasLooking;

   public FreeLookZoomModule() {
      super("free-look-zoom", "FreeLook Zoom", ModuleCategory.RENDER, "Adds scroll-wheel zoom and render-around to FreeLook.");
      this.add(
         new IntSetting("distance", "Start Distance", 40, 10, 320, 5)
            .description("Camera distance when FreeLook starts, in tenths of a block (40 = 4 blocks, vanilla).")
            .build()
      );
      this.add(new BoolSetting("scroll", "Scroll Zoom", true).description("Mouse wheel moves the camera in and out while FreeLook is active.").build());
      this.add(new IntSetting("step", "Scroll Step", 5, 1, 40, 1).description("How far one wheel notch moves the camera, in tenths of a block.").build());
      this.add(new IntSetting("min", "Min Distance", 10, 10, 320, 5).description("Closest the camera can get, in tenths of a block.").build());
      this.add(new IntSetting("max", "Max Distance", 160, 10, 320, 5).description("Furthest the camera can get, in tenths of a block.").build());
      this.add(new BoolSetting("keep", "Remember Zoom", false).description("Keep your last zoom between uses instead of resetting to Start Distance.").build());
      this.add(
         new BoolSetting("render-around", "Render Around", true)
            .description("Turn off occlusion culling while looking, so chunks behind walls still draw.")
            .build()
      );
   }

   @Override
   public String info() {
      return distance > 0.0F ? String.format("%.1f", distance) : "";
   }

   @Override
   public void onDisable() {
      wasLooking = false;
      if (!this.bool("keep")) {
         distance = -1.0F;
      }
   }

   @Override
   public void tick() {
      boolean var1 = FreeLookModule.lookingInstance() != null;
      if (var1 && !wasLooking && (!this.bool("keep") || distance < 0.0F)) {
         distance = this.integer("distance") / 10.0F;
      }

      wasLooking = var1;
   }

   private float clamp(float var1) {
      float var2 = this.integer("min") / 10.0F;
      float var3 = Math.max(var2, this.integer("max") / 10.0F);
      return Math.max(var2, Math.min(var3, var1));
   }

   private static FreeLookZoomModule active() {
      FreeLookZoomModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("free-look-zoom") instanceof FreeLookZoomModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0 != null && var0.isEnabled() && FreeLookModule.lookingInstance() != null ? var0 : null;
   }

   public static float activeDistance() {
      FreeLookZoomModule var0 = active();
      if (var0 == null) {
         return -1.0F;
      } else {
         if (distance < 0.0F) {
            distance = var0.integer("distance") / 10.0F;
         }

         distance = var0.clamp(distance);
         return distance;
      }
   }

   public static boolean handleScroll(double var0) {
      FreeLookZoomModule var2 = active();
      if (var2 != null && var2.bool("scroll") && var0 != 0.0 && MC.gui.screen() == null) {
         float var3 = distance < 0.0F ? var2.integer("distance") / 10.0F : distance;
         distance = var2.clamp(var3 - (float)var0 * (var2.integer("step") / 10.0F));
         return true;
      } else {
         return false;
      }
   }

   public static boolean renderAround() {
      FreeLookZoomModule var0 = active();
      return var0 != null && var0.bool("render-around");
   }
}
