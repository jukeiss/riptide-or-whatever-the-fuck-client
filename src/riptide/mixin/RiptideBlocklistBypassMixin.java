package riptide.mixin;

import net.minecraft.client.multiplayer.resolver.AddressCheck;
import net.minecraft.client.multiplayer.resolver.RiptideAddressChecks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AddressCheck.class})
public interface RiptideBlocklistBypassMixin {
   @Inject(
      method = {"createFromService"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private static void riptide$starveBlocklist(CallbackInfoReturnable<AddressCheck> cir) {
      cir.setReturnValue(RiptideAddressChecks.allowAll());
   }
}
