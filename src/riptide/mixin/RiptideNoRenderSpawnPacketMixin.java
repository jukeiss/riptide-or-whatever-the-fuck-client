package riptide.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.NoRenderState;

@Mixin({ClientPacketListener.class})
public abstract class RiptideNoRenderSpawnPacketMixin {
   @Inject(
      method = {"handleAddEntity"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$dropSpawnPacket(ClientboundAddEntityPacket packet, CallbackInfo ci) {
      if (packet != null && NoRenderState.dropSpawnPacket(packet.getType())) {
         ci.cancel();
      }
   }
}
