package riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.AirJumpModule;
import riptide.modules.ModuleMovementUtil;
import riptide.modules.ScaffoldModule;
import riptide.util.RiptideSilentAim;

@Mixin({LivingEntity.class})
public class RiptideLivingEntityMovementMixin {
   @Shadow
   protected boolean jumping;
   @Shadow
   private int noJumpDelay;

   @Inject(
      method = {"aiStep"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/world/entity/LivingEntity;jumping:Z",
         opcode = 180
      )}
   )
   private void riptide$airJump(CallbackInfo ci) {
      if (this.jumping && this.noJumpDelay == 0 && AirJumpModule.shouldAirJump()) {
         ((LivingEntity)this).jumpFromGround();
         this.noJumpDelay = 10;
      }
   }

   @Inject(
      method = {"jumpFromGround"},
      at = {@At("HEAD")}
   )
   private void riptide$airJumpConsume(CallbackInfo ci) {
      AirJumpModule.onJumpFromGround((LivingEntity)this);
   }

   @ModifyExpressionValue(
      method = {"jumpFromGround"},
      at = {@At(
         value = "NEW",
         target = "(DDD)Lnet/minecraft/world/phys/Vec3;"
      )}
   )
   private Vec3 riptide$killAuraSilentJump(Vec3 original) {
      LivingEntity entity = (LivingEntity)this;
      Vec3 scaffold = ScaffoldModule.correctedJumpImpulse(entity, original);
      return RiptideSilentAim.correctedJumpImpulse(entity, scaffold);
   }

   @ModifyExpressionValue(
      method = {"updateFallFlyingMovement"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F"
      )}
   )
   private float riptide$killAuraSilentGlidePitch(float original) {
      LivingEntity entity = (LivingEntity)this;
      float scaffold = ScaffoldModule.correctedFallFlyingPitch(entity, original);
      return RiptideSilentAim.correctedFallFlyingPitch(entity, scaffold);
   }

   @ModifyExpressionValue(
      method = {"updateFallFlyingMovement"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"
      )}
   )
   private Vec3 riptide$killAuraSilentGlideLook(Vec3 original) {
      LivingEntity entity = (LivingEntity)this;
      Vec3 scaffold = ScaffoldModule.correctedFallFlyingLook(entity, original);
      return RiptideSilentAim.correctedFallFlyingLook(entity, scaffold);
   }

   @Inject(
      method = {"travelInFluid"},
      at = {@At("RETURN")}
   )
   private void riptide$restoreLiquidSpeed(Vec3 input, CallbackInfo ci) {
      ModuleMovementUtil.applySpeedAfterLiquidTravel((LivingEntity)this);
   }
}
