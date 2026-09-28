package riptide.modules;

import net.minecraft.client.KeyMapping;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.util.RiptideKeyMappingBridge;

public final class AutoWalkModule extends Module {
   public AutoWalkModule() {
      super("auto-walk", "Auto Walk", ModuleCategory.MOVEMENT, "Holds a movement key for you.");
      this.add(new ChoiceSetting("direction", "Direction", "Forward", "Forward", "Backward", "Left", "Right").description("Which way to walk.").build());
      this.add(new BoolSetting("sprint", "Sprint", true).description("Hold sprint as well.").build());
      this.add(new BoolSetting("stop-on-input", "Stop On Input", true).description("Turn off as soon as you press a movement key yourself.").build());
   }

   @Override
   public String info() {
      return this.choice("direction");
   }

   @Override
   public void onDisable() {
      this.release();
   }

   @Override
   public void onGameLeft() {
      this.release();
   }

   @Override
   public void tick() {
      if (MC.player == null || MC.options == null || MC.gui.screen() != null) {
         this.release();
      } else if (this.bool("stop-on-input") && this.userIsSteering()) {
         this.release();
         this.disableWithToggleMessage("Auto Walk: off (you took over)");
      } else {
         this.release();
         press(this.key(), true);
         if (this.bool("sprint")) {
            press(MC.options.keySprint, true);
         }
      }
   }

   private KeyMapping key() {
      String var1 = this.choice("direction");

      return switch (var1) {
         case "Backward" -> MC.options.keyDown;
         case "Left" -> MC.options.keyLeft;
         case "Right" -> MC.options.keyRight;
         default -> MC.options.keyUp;
      };
   }

   private boolean userIsSteering() {
      return physicallyDown(MC.options.keyUp)
         || physicallyDown(MC.options.keyDown)
         || physicallyDown(MC.options.keyLeft)
         || physicallyDown(MC.options.keyRight);
   }

   private static boolean physicallyDown(KeyMapping var0) {
      return var0 != null && RiptideKeyMappingBridge.of(var0).riptide$isActuallyDown();
   }

   private void release() {
      if (MC.options != null) {
         press(MC.options.keyUp, false);
         press(MC.options.keyDown, false);
         press(MC.options.keyLeft, false);
         press(MC.options.keyRight, false);
         press(MC.options.keySprint, false);
      }
   }

   private static void press(KeyMapping var0, boolean var1) {
      if (var0 != null) {
         RiptideKeyMappingBridge.of(var0).riptide$simulatePress(var1);
      }
   }
}
