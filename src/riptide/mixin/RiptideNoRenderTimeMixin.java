package riptide.mixin;

import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.NoRenderState;

@Mixin({ClientClockManager.class})
public abstract class RiptideNoRenderTimeMixin {
   @Inject(
      method = {"getTotalTicks"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$forceTime(Holder<WorldClock> definition, CallbackInfoReturnable<Long> cir) {
      if (NoRenderState.timeChanged() && definition.is(WorldClocks.OVERWORLD)) {
         cir.setReturnValue(NoRenderState.timeTicks());
      }
   }
}
