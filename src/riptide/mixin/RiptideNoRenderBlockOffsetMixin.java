package riptide.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.NoRenderState;

@Mixin({BlockStateBase.class})
public abstract class RiptideNoRenderBlockOffsetMixin {
   @Inject(
      method = {"getOffset"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noOffset(BlockPos pos, CallbackInfoReturnable<Vec3> cir) {
      if (NoRenderState.noTextureRotations()) {
         cir.setReturnValue(Vec3.ZERO);
      }
   }
}
