package riptide.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.util.multi.MultiPilot;

@Mixin({ClientPacketListener.class})
public class RiptidePilotChatMixin {
   @Inject(
      method = {"sendChat"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$pilotChat(String content, CallbackInfo ci) {
      if (this.riptide$reroute(content)) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"sendCommand"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$pilotCommand(String command, CallbackInfo ci) {
      if (this.riptide$reroute("/" + command)) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"sendUnattendedCommand"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$pilotUnattendedCommand(String command, Screen screenAfter, CallbackInfo ci) {
      if (this.riptide$reroute("/" + command)) {
         ci.cancel();
      }
   }

   private boolean riptide$reroute(String line) {
      if (!MultiPilot.isActive()) {
         return false;
      } else {
         return this != Minecraft.getInstance().getConnection() ? false : MultiPilot.rerouteChat(line);
      }
   }
}
