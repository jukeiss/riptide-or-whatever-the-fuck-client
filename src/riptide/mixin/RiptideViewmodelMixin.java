package riptide.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.ViewmodelState;

@Mixin({ItemInHandRenderer.class})
public abstract class RiptideViewmodelMixin {
   @Shadow
   private ItemStack offHandItem;
   @Shadow
   @Final
   private static float ITEM_POS_Y;

   @Inject(
      method = {"submitArmWithItem"},
      at = {@At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
         shift = Shift.AFTER
      )},
      require = 0
   )
   private void riptide$handTransforms(
      AbstractClientPlayer player,
      float frameInterp,
      float xRot,
      InteractionHand hand,
      float attack,
      ItemStack itemStack,
      float inverseArmHeight,
      PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector,
      int lightCoords,
      CallbackInfo ci
   ) {
      if (ViewmodelState.active()) {
         boolean bothHands = hand == InteractionHand.MAIN_HAND && itemStack.has(DataComponents.MAP_ID) && this.offHandItem.isEmpty();
         boolean main = ViewmodelState.mainHandOn();
         boolean off = ViewmodelState.offHandOn();
         if (bothHands && main && off) {
            riptide$applyTransformations(
               poseStack,
               (ViewmodelState.mainHandX() + ViewmodelState.offHandX()) / 2.0F,
               (ViewmodelState.mainHandY() + ViewmodelState.offHandY()) / 2.0F,
               (ViewmodelState.mainHandScale() + ViewmodelState.offHandScale()) / 2.0F,
               (ViewmodelState.mainHandRotX() + ViewmodelState.offHandRotX()) / 2.0F,
               (ViewmodelState.mainHandRotY() + ViewmodelState.offHandRotY()) / 2.0F,
               (ViewmodelState.mainHandRotZ() + ViewmodelState.offHandRotZ()) / 2.0F
            );
         } else if (bothHands && main) {
            poseStack.translate(0.0F, 0.0F, ViewmodelState.mainHandScale());
         } else if (hand == InteractionHand.MAIN_HAND && main) {
            riptide$applyTransformations(
               poseStack,
               ViewmodelState.mainHandX(),
               ViewmodelState.mainHandY(),
               ViewmodelState.mainHandScale(),
               ViewmodelState.mainHandRotX(),
               ViewmodelState.mainHandRotY(),
               ViewmodelState.mainHandRotZ()
            );
         } else if (off) {
            riptide$applyTransformations(
               poseStack,
               ViewmodelState.offHandX(),
               ViewmodelState.offHandY(),
               ViewmodelState.offHandScale(),
               ViewmodelState.offHandRotX(),
               ViewmodelState.offHandRotY(),
               ViewmodelState.offHandRotZ()
            );
         }
      }
   }

   @Unique
   private static void riptide$applyTransformations(PoseStack matrices, float tx, float ty, float tz, float rx, float ry, float rz) {
      matrices.translate(tx, ty, tz);
      matrices.mulPose(Axis.XP.rotationDegrees(rx));
      matrices.mulPose(Axis.YP.rotationDegrees(ry));
      matrices.mulPose(Axis.ZP.rotationDegrees(rz));
   }

   @Unique
   private static void riptide$applySwingOffset(PoseStack m, HumanoidArm arm, float swing) {
      int side = arm == HumanoidArm.RIGHT ? 1 : -1;
      float f = Mth.sin((float)(swing * swing * Math.PI));
      m.mulPose(Axis.YP.rotationDegrees(side * (45.0F + f * -20.0F)));
      float g = Mth.sin((float)(Mth.sqrt(swing) * Math.PI));
      m.mulPose(Axis.ZP.rotationDegrees(side * g * -20.0F));
      m.mulPose(Axis.XP.rotationDegrees(g * -80.0F));
      m.mulPose(Axis.YP.rotationDegrees(side * -45.0F));
   }

   @Unique
   private static void riptide$applyBlockAnimation(PoseStack m, HumanoidArm arm, float swing) {
      if (ViewmodelState.blockAnim() == 1) {
         m.translate(arm == HumanoidArm.RIGHT ? -0.1F : 0.1F, 0.1F, 0.0F);
         float g = Mth.sin((float)(Mth.sqrt(swing) * Math.PI));
         m.mulPose(Axis.ZP.rotationDegrees((arm == HumanoidArm.RIGHT ? 1 : -1) * g * 10.0F));
         m.mulPose(Axis.XP.rotationDegrees(g * -35.0F));
      } else {
         m.translate(arm == HumanoidArm.RIGHT ? -0.1F : 0.1F, ViewmodelState.oneSevenY(), 0.0F);
         riptide$applySwingOffset(m, arm, swing * ViewmodelState.oneSevenSwingScale());
      }
   }

   @Inject(
      method = {"submitArmWithItem"},
      slice = {@Slice(
         from = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getUseAnimation()Lnet/minecraft/world/item/ItemUseAnimation;"
         )
      )},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V",
         ordinal = 0,
         shift = Shift.AFTER
      )},
      require = 0
   )
   private void riptide$blockAnimation(
      AbstractClientPlayer player,
      float frameInterp,
      float xRot,
      InteractionHand hand,
      float attack,
      ItemStack itemStack,
      float inverseArmHeight,
      PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector,
      int lightCoords,
      CallbackInfo ci
   ) {
      if (ViewmodelState.active() && itemStack.is(ItemTags.SWORDS)) {
         HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
         riptide$applyBlockAnimation(poseStack, arm, attack);
      }
   }

   @ModifyArg(
      method = {"submitArmWithItem"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V",
         ordinal = 3
      ),
      index = 2,
      require = 0
   )
   private float riptide$ignoreBlocking(float equipProgress) {
      return ViewmodelState.equipOffsetOn() && ViewmodelState.ignoreBlocking() ? 0.0F : equipProgress;
   }

   @Inject(
      method = {"itemUsed"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$ignorePlace(InteractionHand hand, CallbackInfo ci) {
      if (ViewmodelState.active() && ViewmodelState.ignorePlace()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"shouldInstantlyReplaceVisibleItem"},
      at = {@At("RETURN")},
      cancellable = true,
      require = 0
   )
   private void riptide$ignoreAmount(ItemStack currentlyVisibleItem, ItemStack expectedItem, CallbackInfoReturnable<Boolean> cir) {
      if (ViewmodelState.active() && !cir.getReturnValueZ()) {
         cir.setReturnValue(
            !ViewmodelState.equipOffsetOn()
               || (currentlyVisibleItem.getCount() == expectedItem.getCount() || ViewmodelState.ignoreAmount())
                  && ItemStack.isSameItemSameComponents(currentlyVisibleItem, expectedItem)
         );
      }
   }

   @ModifyArg(
      method = {"applyItemArmTransform"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"
      ),
      index = 1,
      require = 0
   )
   private float riptide$disableEquipOffset(float y) {
      return ViewmodelState.active() && !ViewmodelState.equipOffsetOn() ? ITEM_POS_Y : y;
   }
}
