package riptide.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerScoreEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.NameCensorModule;

@Mixin({PlayerScoreEntry.class})
public class RiptidePlayerScoreEntryMixin {
   @Inject(
      method = {"ownerName"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void riptide$censorScoreOwner(CallbackInfoReturnable<Component> cir) {
      cir.setReturnValue(NameCensorModule.censorServerComponent((Component)cir.getReturnValue()));
   }
}
