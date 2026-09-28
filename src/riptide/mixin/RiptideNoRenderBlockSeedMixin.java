package riptide.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.NoRenderState;

@Mixin({BlockBehaviour.class})
public abstract class RiptideNoRenderBlockSeedMixin {
   @Inject(
      method = {"getSeed"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$constantSeed(BlockState state, BlockPos pos, CallbackInfoReturnable<Long> cir) {
      if (NoRenderState.noTextureRotations()) {
         cir.setReturnValue(0L);
      }
   }
}
