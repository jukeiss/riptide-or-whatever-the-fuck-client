package riptide.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.util.multi.MultiPilot;

@Mixin({ClientCommonPacketListenerImpl.class})
public class RiptidePilotEditRerouteMixin {
   @Inject(
      method = {"send(Lnet/minecraft/network/protocol/Packet;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$rerouteEditsToBot(Packet<?> packet, CallbackInfo ci) {
      if (packet instanceof ServerboundSignUpdatePacket || packet instanceof ServerboundEditBookPacket) {
         if (MultiPilot.isActive()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == this) {
               if (MultiPilot.rerouteEditPacket(packet)) {
                  ci.cancel();
               }
            }
         }
      }
   }
}
