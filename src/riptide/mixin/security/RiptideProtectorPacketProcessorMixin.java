package riptide.mixin.security;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.security.RiptideProtector;
import riptide.security.RiptideProtectorPacketContext;

@Mixin(
   targets = {"net.minecraft.network.PacketProcessor$ListenerAndPacket"}
)
public class RiptideProtectorPacketProcessorMixin {
   @WrapOperation(
      method = {"handle"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"
      )}
   )
   private <T extends PacketListener> void riptide$wrapHandle(Packet<?> instance, T listener, Operation<Void> original) {
      if (instance instanceof ClientboundCustomPayloadPacket && RiptideProtector.shouldTagPacketComponents()) {
         RiptideProtectorPacketContext.setProcessingPacket(true);

         try {
            original.call(new Object[]{instance, listener});
         } finally {
            RiptideProtectorPacketContext.setProcessingPacket(false);
         }
      } else {
         original.call(new Object[]{instance, listener});
      }
   }
}
