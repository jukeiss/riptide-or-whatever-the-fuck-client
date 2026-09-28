package riptide.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;
import riptide.modules.PackHideState;

public final class RiptideMacroSneak {
   private RiptideMacroSneak() {
   }

   public static boolean isPacket(String mode) {
      return "Packet".equalsIgnoreCase(mode);
   }

   public static void hold(Minecraft mc, String mode) {
      if (mc != null) {
         if (isPacket(mode)) {
            sendInput(mc, true);
         } else if (mc.options != null && mc.options.keyShift != null) {
            mc.options.keyShift.setDown(true);
         }
      }
   }

   public static void release(Minecraft mc, String mode) {
      if (mc != null) {
         if (isPacket(mode)) {
            sendInput(mc, false);
         }

         if (mc.options != null && mc.options.keyShift != null) {
            mc.options.keyShift.setDown(false);
         }
      }
   }

   public static void forceSneak(Minecraft mc, boolean sneak) {
      if (mc != null) {
         if (mc.options != null && mc.options.keyShift != null) {
            mc.options.keyShift.setDown(sneak);
         }

         if (mc.player != null) {
            mc.player.setShiftKeyDown(sneak);
         }

         sendInput(mc, sneak);
      }
   }

   public static void sendInput(Minecraft mc, boolean sneak) {
      if (!PackHideState.isHardLocked()) {
         if (mc != null && mc.options != null && mc.getConnection() != null) {
            Input input = new Input(
               mc.options.keyUp.isDown(),
               mc.options.keyDown.isDown(),
               mc.options.keyLeft.isDown(),
               mc.options.keyRight.isDown(),
               mc.options.keyJump.isDown(),
               sneak,
               mc.options.keySprint.isDown()
            );
            mc.getConnection().send(new ServerboundPlayerInputPacket(input));
         }
      }
   }
}
