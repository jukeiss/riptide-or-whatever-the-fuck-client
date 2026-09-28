package riptide.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.util.RiptideChams;
import riptide.util.RiptideChamsContext;
import riptide.util.RiptideChamsRenderQueue;

@Mixin({SubmitNodeCollection.class})
public abstract class RiptideChamsSubmitMixin {
   @Inject(
      method = {"submitModel"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$chamsModel(
      Model model,
      Object object,
      PoseStack pose,
      RenderType type,
      int light,
      int overlay,
      int tint,
      TextureAtlasSprite sprite,
      int outlineColor,
      CrumblingOverlay crumbling,
      CallbackInfo ci
   ) {
      if (RiptideChamsContext.active()) {
         if (!RiptideChamsContext.claimBody()) {
            if (!RiptideChamsContext.drawArmor()) {
               ci.cancel();
            } else {
               RiptideChamsRenderQueue.submitLayer(model, object, pose.last().copy(), type, light, overlay, tint, sprite);
               ci.cancel();
            }
         } else {
            RenderType visible = RiptideChams.chamsVisible(type);
            RenderType occluded = RiptideChams.chamsOccluded(type);
            if (visible != null && occluded != null) {
               RiptideChamsRenderQueue.submitBody(
                  model,
                  object,
                  pose.last().copy(),
                  visible,
                  occluded,
                  15728880,
                  overlay,
                  RiptideChamsContext.visible(),
                  RiptideChamsContext.occluded(),
                  sprite
               );
               ci.cancel();
            }
         }
      }
   }
}
