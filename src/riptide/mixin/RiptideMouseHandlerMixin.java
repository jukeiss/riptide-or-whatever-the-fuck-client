package riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.AutoArmorModule;
import riptide.modules.AutoTotemModule;
import riptide.modules.FreeLookModule;
import riptide.modules.ModuleRegistry;
import riptide.modules.PackFreecamState;
import riptide.util.RiptideCpsTracker;
import riptide.util.RiptideMouseInputSimulator;
import riptide.util.RiptideRuntimeActivity;
import riptide.util.multi.MultiPilot;

@Mixin({MouseHandler.class})
public abstract class RiptideMouseHandlerMixin {
   @Shadow
   @Final
   private Minecraft minecraft;
   @Shadow
   private double accumulatedDX;
   @Shadow
   private double accumulatedDY;
   @Unique
   private float riptide$turnStartYaw;
   @Unique
   private float riptide$turnStartPitch;
   @Unique
   private boolean riptide$turnHadPlayer;
   @Unique
   private double riptide$simulatedDX;
   @Unique
   private double riptide$simulatedDY;

   @WrapOperation(
      method = {"turnPlayer"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
      )}
   )
   private void riptide$freeLookTurn(LocalPlayer player, double x, double y, Operation<Void> original) {
      double assistX = this.riptide$simulatedDX;
      double assistY = this.riptide$simulatedDY;
      if (assistX != 0.0 || assistY != 0.0) {
         this.riptide$simulatedDX = 0.0;
         this.riptide$simulatedDY = 0.0;
         if (FreeLookModule.consumeMouseTurn(x, y)) {
            original.call(new Object[]{player, assistX, assistY});
         } else {
            original.call(new Object[]{player, x + assistX, y + assistY});
         }
      } else if (!FreeLookModule.consumeMouseTurn(x, y)) {
         original.call(new Object[]{player, x, y});
      }
   }

   @Inject(
      method = {"handleAccumulatedMovement"},
      at = {@At("HEAD")}
   )
   private void riptide$clearQueuedMouseInputWhenUnavailable(CallbackInfo ci) {
      RiptideMouseInputSimulator.clearIfUnavailable();
   }

   @Inject(
      method = {"onButton"},
      at = {@At("HEAD")}
   )
   private void riptide$trackCps(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
      if (action == 1 && button != null) {
         if (this.minecraft != null && this.minecraft.gui.screen() == null) {
            int b = button.button();
            if (b == 0) {
               RiptideCpsTracker.recordLeft();
            } else if (b == 1) {
               RiptideCpsTracker.recordRight();
            }
         }
      }
   }

   @Inject(
      method = {"onScroll"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$freecamScrollSpeed(long window, double xOffset, double yOffset, CallbackInfo ci) {
      if (AutoTotemModule.operationActive() || AutoArmorModule.operationActive()) {
         ci.cancel();
      } else if (PackFreecamState.onMouseScroll(yOffset)) {
         ci.cancel();
      } else {
         if (this.minecraft != null && this.minecraft.gui.screen() == null && MultiPilot.handleHotbarScroll(yOffset)) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"handleAccumulatedMovement"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/MouseHandler;turnPlayer(D)V"
      )}
   )
   private void riptide$applyQueuedRawMouseInput(CallbackInfo ci) {
      RiptideMouseInputSimulator.Delta delta = RiptideMouseInputSimulator.consume();
      if (RiptideMouseInputSimulator.hasExclusiveInput()) {
         this.accumulatedDX = delta.x();
         this.accumulatedDY = delta.y();
      } else if (!delta.isZero()) {
         if (this.minecraft.player != null && FreeLookModule.lookingInstance() != null) {
            this.riptide$simulatedDX = this.riptide$simulatedDX + delta.x();
            this.riptide$simulatedDY = this.riptide$simulatedDY + delta.y();
         } else {
            this.accumulatedDX = this.accumulatedDX + riptide$additiveAssist(this.accumulatedDX, delta.x());
            this.accumulatedDY = this.accumulatedDY + riptide$additiveAssist(this.accumulatedDY, delta.y());
         }
      }
   }

   @ModifyExpressionValue(
      method = {"turnPlayer"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/client/Options;smoothCamera:Z"
      )}
   )
   private boolean riptide$avoidDoubleSmoothingTelly(boolean original) {
      return original && !RiptideMouseInputSimulator.hasExclusiveInput(RiptideMouseInputSimulator.Source.SCAFFOLD_TELLY);
   }

   @Unique
   private static double riptide$additiveAssist(double realInput, double assistInput) {
      if (Math.abs(assistInput) < 1.0E-7) {
         return 0.0;
      } else if (Math.abs(realInput) < 1.0E-4) {
         return assistInput;
      } else {
         return Math.signum(realInput) == Math.signum(assistInput) ? assistInput : 0.0;
      }
   }

   @Inject(
      method = {"turnPlayer"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$swallowTurnDuringTotemOperation(double deltaTime, CallbackInfo ci) {
      if (AutoTotemModule.operationActive() || AutoArmorModule.movementInputPaused()) {
         this.accumulatedDX = 0.0;
         this.accumulatedDY = 0.0;
         this.riptide$simulatedDX = 0.0;
         this.riptide$simulatedDY = 0.0;
         RiptideMouseInputSimulator.clear();
         ci.cancel();
      }
   }

   @Inject(
      method = {"turnPlayer"},
      at = {@At("HEAD")}
   )
   private void riptide$beforeTurnPlayer(double deltaTime, CallbackInfo ci) {
      if (!RiptideRuntimeActivity.has(16L)) {
         this.riptide$turnHadPlayer = false;
      } else {
         this.riptide$turnHadPlayer = this.minecraft != null && this.minecraft.player != null;
         if (this.riptide$turnHadPlayer) {
            this.riptide$turnStartYaw = this.minecraft.player.getYRot();
            this.riptide$turnStartPitch = this.minecraft.player.getXRot();
         }
      }
   }

   @Inject(
      method = {"turnPlayer"},
      at = {@At("TAIL")}
   )
   private void riptide$afterTurnPlayer(double deltaTime, CallbackInfo ci) {
      if (this.riptide$turnHadPlayer && this.minecraft != null && this.minecraft.player != null) {
         double deltaYaw = this.minecraft.player.getYRot() - this.riptide$turnStartYaw;
         double deltaPitch = this.minecraft.player.getXRot() - this.riptide$turnStartPitch;
         if (Math.abs(deltaYaw) > 1.0E-6 || Math.abs(deltaPitch) > 1.0E-6) {
            ModuleRegistry.onMouseRotation(deltaYaw, deltaPitch);
         }
      }
   }
}
