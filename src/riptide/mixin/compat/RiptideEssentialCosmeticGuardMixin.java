package riptide.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({LevelRenderer.class})
public abstract class RiptideEssentialCosmeticGuardMixin {
   @WrapMethod(
      method = {"submitEntities"}
   )
   private void riptide$catchEssentialCosmeticNpe(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector output, Operation<Void> original) {
      try {
         original.call(new Object[]{poseStack, levelRenderState, output});
      } catch (NullPointerException var7) {
         String msg = var7.getMessage();
         if (msg == null || !msg.contains("\"o\" is null")) {
            throw var7;
         }
      }
   }
}
