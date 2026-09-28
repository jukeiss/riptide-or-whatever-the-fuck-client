package riptide.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.ModuleRenderUtil;
import riptide.modules.NameCensorModule;

@Mixin({EntityRenderer.class})
public abstract class RiptideEntityRendererLightMixin<T extends Entity> {
   @Inject(
      method = {"getSkyLightLevel"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void riptide$fullbrightSkyLight(T entity, BlockPos blockPos, CallbackInfoReturnable<Integer> cir) {
      if (ModuleRenderUtil.hasFullbrightLuminanceWork()) {
         int boosted = ModuleRenderUtil.fullbrightLuminance(LightLayer.SKY);
         if (boosted > (Integer)cir.getReturnValue()) {
            cir.setReturnValue(boosted);
         }
      }
   }

   @Inject(
      method = {"getBlockLightLevel"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void riptide$fullbrightBlockLight(T entity, BlockPos blockPos, CallbackInfoReturnable<Integer> cir) {
      if (ModuleRenderUtil.hasFullbrightLuminanceWork()) {
         int boosted = ModuleRenderUtil.fullbrightLuminance(LightLayer.BLOCK);
         if (boosted > (Integer)cir.getReturnValue()) {
            cir.setReturnValue(boosted);
         }
      }
   }

   @Inject(
      method = {"getNameTag"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void riptide$censorNameTag(T entity, CallbackInfoReturnable<Component> cir) {
      if (NameCensorModule.isActive()) {
         if (cir.getReturnValue() != null) {
            cir.setReturnValue(NameCensorModule.censorServerComponent((Component)cir.getReturnValue()));
         }
      }
   }
}
