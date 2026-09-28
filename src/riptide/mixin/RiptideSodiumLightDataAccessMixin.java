package riptide.mixin;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import riptide.modules.ModuleRenderUtil;

@Pseudo
@Mixin(
   targets = {"net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess"},
   remap = false
)
public abstract class RiptideSodiumLightDataAccessMixin {
   @Shadow(
      remap = false
   )
   protected BlockAndTintGetter level;
   @Shadow(
      remap = false
   )
   @Final
   private MutableBlockPos pos;

   @ModifyVariable(
      method = {"compute"},
      at = @At("TAIL"),
      name = {"bl"},
      remap = false
   )
   private int riptide$xraySodiumBlockLight(int bl) {
      if (!ModuleRenderUtil.hasXrayRenderWork()) {
         return bl;
      } else {
         BlockState state = this.level.getBlockState(this.pos);
         return !ModuleRenderUtil.isXrayBlocked(state, this.pos) ? ModuleRenderUtil.sodiumFullLight() : bl;
      }
   }

   @ModifyVariable(
      method = {"compute"},
      at = @At("STORE"),
      name = {"sl"},
      remap = false
   )
   private int riptide$fullbrightSodiumSkyLight(int sl) {
      if (!ModuleRenderUtil.hasFullbrightLuminanceWork()) {
         return sl;
      } else {
         int boosted = ModuleRenderUtil.fullbrightLuminance(LightLayer.SKY);
         return boosted > sl ? boosted : sl;
      }
   }

   @ModifyVariable(
      method = {"compute"},
      at = @At("STORE"),
      name = {"bl"},
      remap = false
   )
   private int riptide$fullbrightSodiumBlockLight(int bl) {
      if (!ModuleRenderUtil.hasFullbrightLuminanceWork()) {
         return bl;
      } else {
         int boosted = ModuleRenderUtil.fullbrightLuminance(LightLayer.BLOCK);
         return boosted > bl ? boosted : bl;
      }
   }
}
