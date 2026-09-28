package riptide.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.util.RiptideFreecamHighlightRenderer;
import riptide.util.RiptideWorldHighlightRenderer;

@Mixin({LevelRenderer.class})
public abstract class RiptideLevelRendererOutlineMixin {
   @Inject(
      method = {"submitBlockOutline"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$hideVanillaOutlineInFreecam(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState state, CallbackInfo ci) {
      if (RiptideFreecamHighlightRenderer.isReplacingVanillaOutline() || RiptideWorldHighlightRenderer.isActive()) {
         ci.cancel();
      }
   }
}
