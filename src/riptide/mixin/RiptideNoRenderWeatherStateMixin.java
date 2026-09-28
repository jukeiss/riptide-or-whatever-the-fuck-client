package riptide.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.NoRenderState;

@Mixin({Level.class})
public abstract class RiptideNoRenderWeatherStateMixin {
   @Inject(
      method = {"getRainLevel"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$rain(float partialTick, CallbackInfoReturnable<Float> cir) {
      if (this.riptide$override()) {
         cir.setReturnValue(NoRenderState.rainLevel());
      }
   }

   @Inject(
      method = {"getThunderLevel"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$thunder(float partialTick, CallbackInfoReturnable<Float> cir) {
      if (this.riptide$override()) {
         cir.setReturnValue(NoRenderState.thunderLevel());
      }
   }

   @Unique
   private boolean riptide$override() {
      return NoRenderState.weatherChanged() && ((Level)this).isClientSide();
   }
}
