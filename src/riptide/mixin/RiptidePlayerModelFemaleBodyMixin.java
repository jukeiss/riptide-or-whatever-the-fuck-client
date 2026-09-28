package riptide.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.render.RiptideFemaleBodyRenderer;

@Mixin({PlayerModel.class})
public class RiptidePlayerModelFemaleBodyMixin {
   @Inject(
      method = {"setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V"},
      at = {@At("TAIL")}
   )
   private void riptide$applyFemaleBodyVisibility(AvatarRenderState state, CallbackInfo ci) {
      RiptideFemaleBodyRenderer.applyModelVisibility((PlayerModel)this, state);
   }

   @Inject(
      method = {"translateToHand(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V"},
      at = {@At("RETURN")}
   )
   private void riptide$preserveHeldItemScale(AvatarRenderState state, HumanoidArm arm, PoseStack poseStack, CallbackInfo ci) {
      RiptideFemaleBodyRenderer.compensateHeldItemArmScale(state, poseStack);
   }
}
