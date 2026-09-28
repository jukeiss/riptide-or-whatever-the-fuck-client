package riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.PlayerRideableJumping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.BuiltinModules;
import riptide.modules.EntityControlModule;
import riptide.modules.KillAuraModule;
import riptide.modules.ModuleMovementUtil;
import riptide.modules.ModuleRegistry;
import riptide.modules.PackFreecamState;
import riptide.modules.ScaffoldModule;
import riptide.util.RiptideSilentAim;

@Mixin({LocalPlayer.class})
public class RiptideLocalPlayerMovementMixin {
   @ModifyExpressionValue(
      method = {"sendPosition"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"
      )}
   )
   private float riptide$scaffoldSilentMovementYaw(float original) {
      LocalPlayer player = (LocalPlayer)this;
      float fastExp = BuiltinModules.outgoingFastExpMovementYaw(player, original);
      float scaffold = ScaffoldModule.outgoingMovementYaw(player, fastExp);
      return RiptideSilentAim.outgoingMovementYaw(player, scaffold);
   }

   @ModifyExpressionValue(
      method = {"sendPosition"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"
      )}
   )
   private float riptide$scaffoldSilentMovementPitch(float original) {
      LocalPlayer player = (LocalPlayer)this;
      float fastExp = BuiltinModules.outgoingFastExpMovementPitch(player, original);
      float scaffold = ScaffoldModule.outgoingMovementPitch(player, fastExp);
      return RiptideSilentAim.outgoingMovementPitch(player, scaffold);
   }

   @Inject(
      method = {"isShiftKeyDown"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$flightNoSneak(CallbackInfoReturnable<Boolean> cir) {
      if (ModuleMovementUtil.flightNoSneak((LocalPlayer)this) || PackFreecamState.isActive()) {
         cir.setReturnValue(false);
      }
   }

   @ModifyExpressionValue(
      method = {"aiStep"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;canStartSprinting()Z"
      )}
   )
   private boolean riptide$sprintDecisionMovementTick(boolean original) {
      return ModuleMovementUtil.sprintDecision(original, true) && !KillAuraModule.blocksSprintForCrit() && !ScaffoldModule.blocksSprintWithoutForward();
   }

   @ModifyExpressionValue(
      method = {"aiStep"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/player/Input;sprint()Z"
      )}
   )
   private boolean riptide$sprintDecisionInput(boolean original) {
      return ModuleMovementUtil.sprintDecision(original, false) && !KillAuraModule.blocksSprintForCrit() && !ScaffoldModule.blocksSprintWithoutForward();
   }

   @ModifyExpressionValue(
      method = {"sendIsSprintingIfNeeded"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;isSprinting()Z"
      )}
   )
   private boolean riptide$sprintNetworkDecision(boolean original) {
      return ModuleMovementUtil.sprintNetworkAllowed(original);
   }

   @ModifyExpressionValue(
      method = {"canStartSprinting"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"
      )}
   )
   private boolean riptide$omniSprintForwardImpulse(boolean original) {
      return ModuleMovementUtil.sprintIsOmnidirectional() ? ((LocalPlayer)this).input.getMoveVector().length() > 1.0E-5F : original;
   }

   @ModifyExpressionValue(
      method = {"shouldStopRunSprinting"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/client/player/LocalPlayer;horizontalCollision:Z",
         opcode = 180
      )}
   )
   private boolean riptide$sprintIgnoreCollision(boolean original) {
      return !ModuleMovementUtil.sprintIgnoresCollision() && original;
   }

   @ModifyReturnValue(
      method = {"shouldStopRunSprinting"},
      at = {@At("RETURN")}
   )
   private boolean riptide$sprintForceStop(boolean shouldStop) {
      return shouldStop || ModuleMovementUtil.sprintShouldPrevent() || KillAuraModule.blocksSprintForCrit() || ScaffoldModule.blocksSprintWithoutForward();
   }

   @ModifyExpressionValue(
      method = {"canStartSprinting"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;isMovingSlowly()Z"
      )}
   )
   private boolean riptide$sprintIgnoreBlindness(boolean original) {
      return !ModuleMovementUtil.sprintIgnoresBlindness() && original;
   }

   @Inject(
      method = {"getJumpRidingScale"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void riptide$entityControlMaxJump(CallbackInfoReturnable<Float> cir) {
      if (EntityControlModule.shouldMaxJump()) {
         cir.setReturnValue(1.0F);
      }
   }

   @Inject(
      method = {"jumpableVehicle"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void riptide$entityControlFlightJump(CallbackInfoReturnable<PlayerRideableJumping> cir) {
      if (EntityControlModule.shouldCancelRidingJump()) {
         cir.setReturnValue(null);
      }
   }

   @Inject(
      method = {"sendPosition"},
      at = {@At("HEAD")}
   )
   private void riptide$networkMovementTickPre(CallbackInfo ci) {
      if ((LocalPlayer)this == Minecraft.getInstance().player) {
         ModuleRegistry.onNetworkMovementTickPre();
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V",
         shift = Shift.BEFORE,
         ordinal = 0
      )},
      cancellable = true
   )
   private void riptide$playerTickCancel(CallbackInfo ci) {
      if ((LocalPlayer)this == Minecraft.getInstance().player) {
         if (ModuleRegistry.shouldCancelPlayerTick()) {
            ci.cancel();
         }
      }
   }
}
