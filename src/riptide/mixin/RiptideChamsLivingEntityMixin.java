package riptide.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.ModuleRenderUtil;
import riptide.util.RiptideChamsContext;
import riptide.util.RiptideChamsHolder;

@Mixin({LivingEntityRenderer.class})
public abstract class RiptideChamsLivingEntityMixin {
   @Inject(
      method = {"submit"},
      at = {@At("HEAD")},
      require = 0
   )
   private void riptide$chamsContextStart(
      LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci
   ) {
      RiptideChamsContext.clear();
      if (ModuleRenderUtil.hasChamsWork() && state instanceof RiptideChamsHolder holder && holder.riptide$chamsActive()) {
         RiptideChamsContext.set(holder.riptide$chamsVisible(), holder.riptide$chamsOccluded(), ModuleRenderUtil.chamsDrawArmor());
      }
   }

   @Inject(
      method = {"submit"},
      at = {@At("RETURN")},
      require = 0
   )
   private void riptide$chamsContextEnd(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
      RiptideChamsContext.clear();
   }
}
