package riptide.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.ModuleRenderUtil;

@Pseudo
@Mixin(
   targets = {"net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager"},
   remap = false
)
public abstract class RiptideSodiumRenderSectionManagerMixin {
   @Inject(
      method = {"shouldUseOcclusionCulling"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$disableSodiumOcclusion(Camera camera, boolean spectator, CallbackInfoReturnable<Boolean> cir) {
      if (ModuleRenderUtil.shouldBypassOcclusionCulling()) {
         cir.setReturnValue(false);
      }
   }
}
