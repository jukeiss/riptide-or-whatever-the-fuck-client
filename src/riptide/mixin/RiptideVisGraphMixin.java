package riptide.mixin;

import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.ModuleRenderUtil;

@Mixin({VisGraph.class})
public class RiptideVisGraphMixin {
   @Inject(
      method = {"setOpaque"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$xrayDisableChunkOcclusion(BlockPos pos, CallbackInfo ci) {
      if (ModuleRenderUtil.hasXrayRenderWork()) {
         ci.cancel();
      }
   }
}
