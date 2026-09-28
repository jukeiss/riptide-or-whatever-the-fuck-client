package riptide.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import riptide.mixin.RiptideRenderTypeStateAccessor;

@Mixin(
   targets = {"gg.essential.model.backend.minecraft.RenderLayerFactory$Companion"},
   remap = false
)
public abstract class RiptideEssentialRenderLayerFactoryMixin {
   @WrapMethod(
      method = {"createRenderLayer"}
   )
   private RenderType riptide$createRenderLayerDirectly(String name, RenderSetup setup, Operation<RenderType> original) {
      return RiptideRenderTypeStateAccessor.riptide$create(name, setup);
   }
}
