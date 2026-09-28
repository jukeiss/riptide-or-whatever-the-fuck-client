package riptide.mixin;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.NoRenderState;

@Mixin({BlockEntityRenderDispatcher.class})
public abstract class RiptideNoRenderBlockEntityMixin {
   @Inject(
      method = {"tryExtractRenderState"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noBlockEntity(
      BlockEntity blockEntity, float partialTick, CrumblingOverlay crumbling, boolean crumblingOnly, CallbackInfoReturnable<BlockEntityRenderState> cir
   ) {
      if (blockEntity != null && NoRenderState.noBlockEntity(blockEntity.getBlockState().getBlock())) {
         cir.setReturnValue(null);
      }
   }
}
