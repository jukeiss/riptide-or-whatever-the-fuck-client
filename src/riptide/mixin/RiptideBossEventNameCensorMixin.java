package riptide.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.NameCensorModule;

@Mixin({BossEvent.class})
public class RiptideBossEventNameCensorMixin {
   @Inject(
      method = {"getName"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void riptide$censorBossName(CallbackInfoReturnable<Component> cir) {
      cir.setReturnValue(NameCensorModule.censorServerComponent((Component)cir.getReturnValue()));
   }
}
