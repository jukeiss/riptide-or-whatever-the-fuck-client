package riptide.mixin.security;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.security.RiptidePackResponseScheduler;
import riptide.security.RiptideProtectorPackStrip;
import riptide.security.RiptideResourcePackTruthGuard;

@Mixin({ClientCommonPacketListenerImpl.class})
public abstract class RiptideProtectorClientCommonNetworkHandlerMixin {
   @Inject(
      method = {"handleResourcePackPush"},
      at = {@At("HEAD")}
   )
   private void riptide$onPackPush(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
      RiptideProtectorPackStrip.onPackPush(packet.id());
   }

   @Inject(
      method = {"handleResourcePackPop"},
      at = {@At("HEAD")}
   )
   private void riptide$onPackPop(ClientboundResourcePackPopPacket packet, CallbackInfo ci) {
      Optional<UUID> id = packet.id();
      RiptideProtectorPackStrip.onPop(id.orElse(null));
      RiptideResourcePackTruthGuard.onPop(id.orElse(null));
      RiptidePackResponseScheduler.cancel(id.orElse(null));
   }
}
