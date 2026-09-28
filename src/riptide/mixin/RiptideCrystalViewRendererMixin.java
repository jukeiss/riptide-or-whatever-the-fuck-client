package riptide.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.modules.CrystalAuraModule;

@Mixin({EndCrystalRenderer.class})
public abstract class RiptideCrystalViewRendererMixin {
   @WrapOperation(
      method = {"submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"
      )},
      require = 0
   )
   private void riptide$crystalScale(PoseStack instance, float x, float y, float z, Operation<Void> original) {
      if (!CrystalAuraModule.crystalViewActive()) {
         original.call(new Object[]{instance, x, y, z});
      } else {
         instance.translate(0.0F, CrystalAuraModule.crystalViewYTranslate(), 0.0F);
         float scale = CrystalAuraModule.crystalViewSize();
         original.call(new Object[]{instance, x * scale, y * scale, z * scale});
      }
   }
}
