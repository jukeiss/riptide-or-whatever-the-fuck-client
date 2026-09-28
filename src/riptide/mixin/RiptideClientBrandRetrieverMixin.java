package riptide.mixin;

import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.RiptideModule;
import riptide.security.RiptideProtector;

@Mixin({ClientBrandRetriever.class})
public class RiptideClientBrandRetrieverMixin {
   @Inject(
      method = {"getClientModName"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false
   )
   private static void riptide$spoofClientBrand(CallbackInfoReturnable<String> cir) {
      if (!RiptideProtector.isFullExternalProtectorPresent()) {
         RiptideModule module = RiptideModule.get();
         if (module != null && module.isSpoofClientVanilla()) {
            cir.setReturnValue("vanilla");
         }
      }
   }
}
