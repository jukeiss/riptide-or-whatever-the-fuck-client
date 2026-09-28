package riptide.mixin;

import net.minecraft.client.renderer.blockentity.SpawnerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.NoRenderState;

@Mixin({SpawnerRenderer.class})
public abstract class RiptideNoRenderSpawnerMixin {
   @Inject(
      method = {"submitEntityInSpawner"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private static void riptide$noSpawnerEntity(CallbackInfo ci) {
      if (NoRenderState.noSpawnerEntities()) {
         ci.cancel();
      }
   }
}
