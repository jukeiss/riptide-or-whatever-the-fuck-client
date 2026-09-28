package riptide.mixin;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.ModuleRenderUtil;

@Mixin({Block.class})
public class RiptideBlockRenderFaceMixin {
   @Inject(
      method = {"shouldRenderFace"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void riptide$xrayForceSelectedFaces(BlockState state, BlockState neighborState, Direction direction, CallbackInfoReturnable<Boolean> cir) {
      if (ModuleRenderUtil.hasXrayRenderWork()) {
         if (ModuleRenderUtil.shouldForceXrayFace(state, neighborState, direction)) {
            cir.setReturnValue(true);
         }
      }
   }
}
