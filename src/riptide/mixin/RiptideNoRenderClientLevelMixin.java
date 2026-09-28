package riptide.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.NoRenderState;

@Mixin({ClientLevel.class})
public abstract class RiptideNoRenderClientLevelMixin {
   @Inject(
      method = {"addDestroyBlockEffect"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noDestroyParticles(BlockPos pos, BlockState state, CallbackInfo ci) {
      if (NoRenderState.noBlockBreakParticles()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"addBreakingBlockEffect"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noBreakingParticles(BlockPos pos, Direction direction, CallbackInfo ci) {
      if (NoRenderState.noBlockBreakParticles()) {
         ci.cancel();
      }
   }
}
