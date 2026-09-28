package riptide.mixin.security;

import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.fabric.impl.resource.ResourceLoaderImpl;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.security.RiptideProtectorModResolver;
import riptide.security.RiptideProtectorTracker;

@Mixin({ResourceLoaderImpl.class})
public class RiptideProtectorResourceLoaderImplMixin {
   @Inject(
      method = {"registerBuiltinPack(Lnet/minecraft/resources/Identifier;Ljava/lang/String;Lnet/fabricmc/loader/api/ModContainer;Lnet/minecraft/network/chat/Component;Lnet/fabricmc/fabric/api/resource/v1/pack/PackActivationType;)Z"},
      at = {@At("RETURN")}
   )
   private static void riptide$trackBuiltinPackDefaults(
      Identifier id, String subPath, ModContainer container, Component displayName, PackActivationType activationType, CallbackInfoReturnable<Boolean> cir
   ) {
      if (cir.getReturnValueZ() && container != null) {
         String mod = container.getMetadata().getId();
         RiptideProtectorTracker.addDefaultAllowedMod(mod);
         RiptideProtectorTracker.addDefaultAllowedMods(RiptideProtectorModResolver.dependenciesFor(mod));
      }
   }
}
