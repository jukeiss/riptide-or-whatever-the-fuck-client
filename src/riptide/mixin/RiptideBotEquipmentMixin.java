package riptide.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.util.multi.MultiPilot;

@Mixin({ClientPacketListener.class})
public class RiptideBotEquipmentMixin {
   @Inject(
      method = {"handleSetEquipment"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$suppressPilotedEquipmentEcho(ClientboundSetEquipmentPacket packet, CallbackInfo ci) {
      if (MultiPilot.isActive() && MultiPilot.isPilotedEntityId(packet.getEntity())) {
         ci.cancel();
      }
   }
}
