package riptide.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.ParticleFilterModule;

@Mixin({ParticleEngine.class})
public class RiptideParticleFilterMixin {
   @Inject(
      method = {"createParticle"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$dropHiddenParticle(
      ParticleOptions var1, double var2, double var4, double var6, double var8, double var10, double var12, CallbackInfoReturnable<Particle> var14
   ) {
      if (ParticleFilterModule.shouldHide(var1)) {
         var14.setReturnValue(null);
      }
   }
}
