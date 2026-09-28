package riptide.mixin.compat;

import java.lang.reflect.Proxy;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
   targets = {"com.moulberry.flashback.mixin.record.MixinConnection"},
   remap = false
)
public abstract class RiptideFlashbackConnectionMixin {
   @Inject(
      method = {"genericsFtw"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0,
      remap = false
   )
   private static void riptide$skipMultiBotTraffic(Packet<?> packet, PacketListener packetListener, CallbackInfo ci) {
      if (riptide$isMultiBotListener(packetListener)) {
         ci.cancel();
      }
   }

   private static boolean riptide$isMultiBotListener(PacketListener listener) {
      if (listener != null && Proxy.isProxyClass(listener.getClass())) {
         try {
            return Proxy.getInvocationHandler(listener).getClass().getName().startsWith("riptide.");
         } catch (Throwable var2) {
            return false;
         }
      } else {
         return false;
      }
   }
}
