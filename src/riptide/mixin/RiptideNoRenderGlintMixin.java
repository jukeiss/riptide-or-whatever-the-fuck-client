package riptide.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import riptide.modules.NoRenderState;

@Mixin({LayerRenderState.class})
public abstract class RiptideNoRenderGlintMixin {
   @ModifyVariable(
      method = {"setFoilType"},
      at = @At("HEAD"),
      argsOnly = true,
      require = 0
   )
   private FoilType riptide$noGlint(FoilType foilType) {
      return NoRenderState.noEnchantGlint() ? FoilType.NONE : foilType;
   }
}
