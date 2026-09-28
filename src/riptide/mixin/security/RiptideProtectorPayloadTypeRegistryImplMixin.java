package riptide.mixin.security;

import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.TypeAndCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.security.RiptideProtectorModResolver;
import riptide.security.RiptideProtectorTracker;

@Mixin({PayloadTypeRegistryImpl.class})
public class RiptideProtectorPayloadTypeRegistryImplMixin {
   @Inject(
      method = {"register"},
      at = {@At("RETURN")}
   )
   private void riptide$trackPayloadDefaultMod(Type<?> type, StreamCodec<?, ?> codec, CallbackInfoReturnable<TypeAndCodec<?, ?>> cir) {
      for (String mod : RiptideProtectorModResolver.modsFromStacktrace()) {
         RiptideProtectorTracker.addDefaultAllowedMod(mod);
         RiptideProtectorTracker.addDefaultAllowedMods(RiptideProtectorModResolver.dependenciesFor(mod));
      }
   }
}
