package riptide.mixin;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.ModuleRenderUtil;

@Pseudo
@Mixin(
   targets = {"net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext"}
)
public class RiptideSodiumBlockContextMixin {
   @Shadow(
      remap = false
   )
   protected BlockState state;
   @Shadow(
      remap = false
   )
   protected BlockAndTintGetter level;
   @Shadow(
      remap = false
   )
   protected BlockPos pos;

   @Inject(
      method = {"shouldDrawSide"},
      at = {@At("RETURN")},
      cancellable = true,
      remap = false
   )
   private void riptide$xraySodiumFaces(Direction direction, CallbackInfoReturnable<Boolean> cir) {
      if (ModuleRenderUtil.hasXrayRenderWork()) {
         cir.setReturnValue(ModuleRenderUtil.modifyXrayFace(this.level, this.state, direction, this.pos, (Boolean)cir.getReturnValue()));
      }
   }
}
