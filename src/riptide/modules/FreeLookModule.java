package riptide.modules;

import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;

public final class FreeLookModule extends Module {
   private float cameraYaw;
   private float cameraPitch;
   private CameraType previousPerspective;

   public FreeLookModule() {
      super("free-look", "FreeLook", ModuleCategory.RENDER, "Look around without rotating the player.");
      this.add(new ChoiceSetting("perspective", "Perspective", "Back", "Back", "Front").build());
      this.add(new BoolSetting("no-pitch-limit", "No Pitch Limit", true).build());
   }

   @Override
   public boolean holdToActivate() {
      return true;
   }

   @Override
   public void onEnable() {
      if (MC.player != null) {
         this.cameraYaw = MC.player.getYRot();
         this.cameraPitch = MC.player.getXRot();
      }

      if (MC.options != null) {
         this.previousPerspective = MC.options.getCameraType();
         MC.options.setCameraType(this.desiredPerspective());
      }
   }

   @Override
   public void onDisable() {
      if (MC.options != null && this.previousPerspective != null) {
         MC.options.setCameraType(this.previousPerspective);
      }

      this.previousPerspective = null;
   }

   @Override
   public void onGameLeft() {
      if (this.isEnabled()) {
         this.setEnabled(false);
      }
   }

   @Override
   public void preMovementTick() {
      if (MC.options != null && MC.options.getCameraType() != this.desiredPerspective()) {
         MC.options.setCameraType(this.desiredPerspective());
      }
   }

   private CameraType desiredPerspective() {
      return this.invertedView() ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK;
   }

   private boolean invertedView() {
      return "Front".equals(this.choice("perspective"));
   }

   private static FreeLookModule activeInstance() {
      return ModuleRegistry.get("free-look") instanceof FreeLookModule freeLook && freeLook.isEnabled() ? freeLook : null;
   }

   public static FreeLookModule lookingInstance() {
      return MC != null && MC.player != null ? activeInstance() : null;
   }

   public boolean isInvertedView() {
      return this.invertedView();
   }

   public float cameraYaw() {
      return this.cameraYaw;
   }

   public float cameraPitch() {
      return this.cameraPitch;
   }

   public static boolean consumeMouseTurn(double deltaX, double deltaY) {
      FreeLookModule freeLook = activeInstance();
      if (freeLook != null && MC.player != null) {
         freeLook.cameraYaw += (float)(deltaX * 0.15);
         freeLook.cameraPitch += (float)(deltaY * 0.15);
         if (!freeLook.bool("no-pitch-limit")) {
            freeLook.cameraPitch = Mth.clamp(freeLook.cameraPitch, -90.0F, 90.0F);
         }

         return true;
      } else {
         return false;
      }
   }
}
