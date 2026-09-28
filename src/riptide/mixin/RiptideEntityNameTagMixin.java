package riptide.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.ModuleNameTagRenderer;
import riptide.security.RiptideComponentSanity;

@Mixin({EntityRenderer.class})
public class RiptideEntityNameTagMixin {
   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void riptide$suppressVanillaNameTag(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
      if (state != null && state.nameTag != null && ModuleNameTagRenderer.tags(entity)) {
         state.nameTag = null;
      }
   }

   @Inject(
      method = {"submit"},
      at = {@At("HEAD")}
   )
   private void riptide$scrubHostileNameTags(
      EntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, CallbackInfo ci
   ) {
      if (state != null) {
         if (state.nameTag != null && !RiptideComponentSanity.isSafe(state.nameTag)) {
            state.nameTag = Component.literal("[unsafe name removed]").withStyle(ChatFormatting.DARK_GRAY);
         }

         if (state.scoreText != null && !RiptideComponentSanity.isSafe(state.scoreText)) {
            state.scoreText = Component.literal("[unsafe score removed]").withStyle(ChatFormatting.DARK_GRAY);
         }
      }
   }
}
