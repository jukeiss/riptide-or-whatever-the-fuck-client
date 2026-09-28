package riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.core.Direction;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.ModCheck;
import net.minecraft.util.ModCheck.Confidence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.commands.RiptideCommands;
import riptide.gui.RiptideLoadingOverlay;
import riptide.gui.macro.editor.ActionEditorOverlay;
import riptide.gui.screen.RiptidePanicTitleScreen;
import riptide.gui.screen.RiptideTitleScreen;
import riptide.modules.AutoArmorModule;
import riptide.modules.AutoTotemModule;
import riptide.modules.BuiltinModules;
import riptide.modules.ModuleMovementUtil;
import riptide.modules.ModuleRegistry;
import riptide.modules.ModuleRenderUtil;
import riptide.modules.PackFreecamState;
import riptide.modules.PackHideState;
import riptide.modules.RiptideBlinkManager;
import riptide.modules.RiptideModule;
import riptide.modules.ScaffoldModule;
import riptide.security.RiptideFabricRegisterMimicry;
import riptide.util.RiptideBlockNbtCapture;
import riptide.util.RiptideChamsHit;
import riptide.util.RiptideCombatClicker;
import riptide.util.RiptideContainerHold;
import riptide.util.RiptideInputClicker;
import riptide.util.RiptideLiteVariant;
import riptide.util.RiptideMenuPrefs;
import riptide.util.RiptidePayloadStudySession;
import riptide.util.RiptideRemoteView;
import riptide.util.RiptideSharedState;
import riptide.util.RiptideWindowBranding;
import riptide.util.macro.MacroExecutor;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiPilot;

@Mixin({Minecraft.class})
public class RiptideMinecraftClientMixin {
   @Unique
   private static final String PACKUTIL_WINDOW_TITLE = "Riptide Client";
   @Unique
   private boolean riptide$escapeWasDown;
   @Unique
   private boolean riptide$inventoryWasDown;
   @Unique
   private boolean riptide$freecamPickSwapped;
   @Unique
   private double riptide$fcX;
   @Unique
   private double riptide$fcY;
   @Unique
   private double riptide$fcZ;
   @Unique
   private double riptide$fcXOld;
   @Unique
   private double riptide$fcYOld;
   @Unique
   private double riptide$fcZOld;
   @Unique
   private float riptide$fcYRot;
   @Unique
   private float riptide$fcXRot;
   @Unique
   private float riptide$fcYRotO;
   @Unique
   private float riptide$fcXRotO;
   @Unique
   private Entity riptide$remoteViewPickCamera;
   @Unique
   private final Map<Integer, Boolean> riptide$commandBindWasDown = new HashMap<>();

   @Inject(
      method = {"pick"},
      at = {@At("HEAD")}
   )
   private void riptide$remoteViewPickHead(float partialTicks, CallbackInfo ci) {
      this.riptide$remoteViewPickCamera = RiptideRemoteView.beginMainPlayerPick((Minecraft)this);
   }

   @Inject(
      method = {"pick"},
      at = {@At("RETURN")}
   )
   private void riptide$remoteViewPickReturn(float partialTicks, CallbackInfo ci) {
      Entity restore = this.riptide$remoteViewPickCamera;
      this.riptide$remoteViewPickCamera = null;
      RiptideRemoteView.endMainPlayerPick((Minecraft)this, restore);
   }

   @Inject(
      method = {"close"},
      at = {@At("HEAD")}
   )
   private void riptide$disconnectMultiOnClose(CallbackInfo ci) {
      MultiManager multi = MultiManager.getIfInitialized();
      if (multi != null) {
         multi.shutdown();
      }
   }

   @Inject(
      method = {"pick"},
      at = {@At("HEAD")}
   )
   private void riptide$freecamPickHead(float partialTicks, CallbackInfo ci) {
      if (PackFreecamState.isActive() && PackFreecamState.interactEnabled()) {
         Entity cam = ((Minecraft)this).getCameraEntity();
         if (cam != null) {
            double eyeOffset = cam.getEyeY() - cam.getY();
            this.riptide$fcX = cam.getX();
            this.riptide$fcY = cam.getY();
            this.riptide$fcZ = cam.getZ();
            this.riptide$fcXOld = cam.xOld;
            this.riptide$fcYOld = cam.yOld;
            this.riptide$fcZOld = cam.zOld;
            this.riptide$fcYRot = cam.getYRot();
            this.riptide$fcXRot = cam.getXRot();
            this.riptide$fcYRotO = cam.yRotO;
            this.riptide$fcXRotO = cam.xRotO;
            double fx = PackFreecamState.getX(partialTicks);
            double fy = PackFreecamState.getY(partialTicks) - eyeOffset;
            double fz = PackFreecamState.getZ(partialTicks);
            float fyaw = PackFreecamState.getYaw(partialTicks);
            float fpitch = PackFreecamState.getPitch(partialTicks);
            cam.setPos(fx, fy, fz);
            cam.xOld = fx;
            cam.yOld = fy;
            cam.zOld = fz;
            cam.setYRot(fyaw);
            cam.setXRot(fpitch);
            cam.yRotO = fyaw;
            cam.xRotO = fpitch;
            this.riptide$freecamPickSwapped = true;
         }
      }
   }

   @Inject(
      method = {"pick"},
      at = {@At("RETURN")}
   )
   private void riptide$freecamPickReturn(float partialTicks, CallbackInfo ci) {
      if (this.riptide$freecamPickSwapped) {
         this.riptide$freecamPickSwapped = false;
         Entity cam = ((Minecraft)this).getCameraEntity();
         if (cam != null) {
            cam.setPos(this.riptide$fcX, this.riptide$fcY, this.riptide$fcZ);
            cam.xOld = this.riptide$fcXOld;
            cam.yOld = this.riptide$fcYOld;
            cam.zOld = this.riptide$fcZOld;
            cam.setYRot(this.riptide$fcYRot);
            cam.setXRot(this.riptide$fcXRot);
            cam.yRotO = this.riptide$fcYRotO;
            cam.xRotO = this.riptide$fcXRotO;
         }
      }
   }

   @Inject(
      method = {"pick"},
      at = {@At("RETURN")}
   )
   private void riptide$multiPovAuthoritativePick(float partialTicks, CallbackInfo ci) {
      if (MultiPilot.isActive()) {
         Minecraft client = (Minecraft)this;
         HitResult vanillaHit = client.hitResult;
         HitResult result = MultiPilot.authoritativePick(client, partialTicks, vanillaHit);
         client.hitResult = result;
         client.crosshairPickEntity = result instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
      }
   }

   @Redirect(
      method = {"*"},
      at = @At(
         value = "NEW",
         target = "net/minecraft/client/gui/screens/LoadingOverlay"
      )
   )
   private static LoadingOverlay riptide$replaceLoadingOverlay(
      Minecraft minecraft, ReloadInstance reload, Consumer<Optional<Throwable>> onFinish, boolean fadeIn
   ) {
      return RiptideLoadingOverlay.create(minecraft, reload, onFinish, fadeIn);
   }

   @Inject(
      method = {"createTitle"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$createCustomWindowTitle(CallbackInfoReturnable<String> cir) {
      if (!PackHideState.isActive() && !RiptideLiteVariant.enabled()) {
         cir.setReturnValue("Riptide Client");
      }
   }

   @Inject(
      method = {"checkModStatus"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void riptide$reportVanillaWhileHidden(CallbackInfoReturnable<ModCheck> cir) {
      if (PackHideState.isActive()) {
         cir.setReturnValue(new ModCheck(Confidence.PROBABLY_NOT, "Client jar signature and brand is untouched"));
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")}
   )
   private void riptide$onTickHead(CallbackInfo ci) {
      RiptideWindowBranding.tick((Minecraft)this);
      RiptideSharedState.get().onClientTickStart();
      RiptideInputClicker.onClientTickStart();
      RiptideChamsHit.tick();
      ModuleRenderUtil.flushWorldRendererRefresh();
      if (PackHideState.isHardLocked()) {
         RiptideInputClicker.releaseOwnedInput();
      } else {
         RiptideFabricRegisterMimicry.onClientTick((Minecraft)this);
         ModuleMovementUtil.preMovementTick();
         MacroExecutor.drainTickAligned();
         if (RiptideContainerHold.hasExpiryWork()) {
            RiptideContainerHold.tickExpiry();
         }

         RiptideModule riptide = RiptideModule.get();
         if (!PackHideState.isActive() && riptide.hasCommandBinds()) {
            this.riptide$pollCommandBinds(riptide);
         }
      }
   }

   @Unique
   private void riptide$pollCommandBinds(RiptideModule module) {
      Minecraft client = (Minecraft)this;
      if (client.getWindow() != null) {
         if (!(client.gui.screen() instanceof ChatScreen)) {
            if (!(client.gui.screen() instanceof SignEditScreen)) {
               Map<Integer, String> binds = module.getCommandBinds();
               if (!binds.isEmpty()) {
                  long handle = client.getWindow().handle();

                  for (Entry<Integer, String> entry : binds.entrySet()) {
                     int key = entry.getKey();
                     boolean down = GLFW.glfwGetKey(handle, key) == 1;
                     boolean wasDown = this.riptide$commandBindWasDown.getOrDefault(key, false);
                     this.riptide$commandBindWasDown.put(key, down);
                     if (down && !wasDown) {
                        String cmd = entry.getValue();
                        if (cmd != null && !cmd.isBlank()) {
                           RiptideCommands.dispatch(cmd);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void riptide$repairStrayTitleScreen(CallbackInfo ci) {
      Minecraft client = (Minecraft)this;
      if (client.gui.screen() instanceof TitleScreen) {
         if (PackHideState.isActive()) {
            client.gui.setScreen(new RiptidePanicTitleScreen());
         } else if (!RiptideLiteVariant.enabled() && RiptideMenuPrefs.customMainMenuEnabled()) {
            client.gui.setScreen(new RiptideTitleScreen());
         }
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void riptide$swapMenuOnPanicChange(CallbackInfo ci) {
      if (!RiptideLiteVariant.enabled()) {
         Minecraft client = (Minecraft)this;
         if (PackHideState.isActive() && client.gui.screen() instanceof RiptideTitleScreen) {
            client.gui.setScreen(new RiptidePanicTitleScreen());
         } else if (!PackHideState.isActive() && client.gui.screen() instanceof RiptidePanicTitleScreen && RiptideMenuPrefs.customMainMenuEnabled()) {
            client.gui.setScreen(new RiptideTitleScreen());
         }
      }
   }

   @Inject(
      method = {"startAttack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$onDoAttack(CallbackInfoReturnable<Boolean> cir) {
      if (!PackHideState.isHardLocked()) {
         if (RiptideCombatClicker.attackInFlight()) {
            if (!RiptideCombatClicker.beginAttack()) {
               cir.setReturnValue(false);
            }
         } else {
            Minecraft client = (Minecraft)this;
            if (RiptideRemoteView.isActive()) {
               cir.setReturnValue(false);
            } else if (MultiPilot.isActive() && MultiPilot.handleStartAttack(client)) {
               cir.setReturnValue(false);
            } else if (RiptideSharedState.get().hasEntityCaptureCallback()) {
               cir.setReturnValue(false);
            } else if (ModuleRegistry.shouldCancelAttack(client.hitResult)) {
               cir.setReturnValue(false);
            } else {
               if (client.hitResult instanceof EntityHitResult entityHit) {
                  RiptideChamsHit.onAttack(entityHit.getEntity());
               }

               if (RiptideSharedState.get().hasAttackCaptureCallback()) {
                  RiptideSharedState.get().consumeAttackCaptureCallback();
                  cir.setReturnValue(false);
               }
            }
         }
      }
   }

   @Inject(
      method = {"continueAttack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$pilotContinueAttack(boolean leftClick, CallbackInfo ci) {
      if (RiptideRemoteView.isActive()) {
         ci.cancel();
      } else if (AutoTotemModule.operationActive() || AutoArmorModule.operationActive()) {
         ci.cancel();
      } else if (MultiPilot.isActive()) {
         MultiPilot.handleContinueAttack((Minecraft)this, leftClick);
         ci.cancel();
      }
   }

   @Inject(
      method = {"startUseItem"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$cancelUseForModules(CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         if (RiptideCombatClicker.useInFlight()) {
            if (!RiptideCombatClicker.beginUse()) {
               ci.cancel();
            }
         } else {
            Minecraft client = (Minecraft)this;
            if (RiptideRemoteView.isActive()) {
               ci.cancel();
            } else if (RiptideBlockNbtCapture.handleStartUse(client)) {
               ci.cancel();
            } else if (MultiPilot.isActive() && MultiPilot.handleStartUseItem(client)) {
               ci.cancel();
            } else {
               RiptideSharedState captureState = RiptideSharedState.get();
               if (captureState.hasEntityCaptureCallback()) {
                  if (client.hitResult instanceof EntityHitResult entityHit) {
                     captureState.consumeEntityCaptureCallback(entityHit.getEntity());
                  }

                  ci.cancel();
               } else if (captureState.hasBlockCaptureCallback()) {
                  if (client.hitResult instanceof BlockHitResult blockHit) {
                     captureState.consumeBlockCaptureCallback(blockHit.getBlockPos(), blockHit.getDirection());
                  }

                  ci.cancel();
               } else if (ModuleRegistry.shouldCancelUse(client.hitResult, InteractionHand.MAIN_HAND)) {
                  ci.cancel();
               } else if (BuiltinModules.ownsManualFastUse()) {
                  if (!BuiltinModules.beginManualFastUseClick()) {
                     ci.cancel();
                  }
               } else if (ScaffoldModule.ownsGrimUseInput()) {
                  if (!ScaffoldModule.beginGrimUseInput()) {
                     ci.cancel();
                  }
               }
            }
         }
      }
   }

   @ModifyExpressionValue(
      method = {"startUseItem"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;isWithinEntityInteractionRange(Lnet/minecraft/world/entity/Entity;D)Z"
      )}
   )
   private boolean riptide$freecamEntityUseReach(boolean original) {
      return original || PackFreecamState.isActive() && PackFreecamState.interactEnabled();
   }

   @ModifyExpressionValue(
      method = {"startAttack"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/item/component/AttackRange;isInRange(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/Vec3;)Z"
      )}
   )
   private boolean riptide$freecamEntityAttackReach(boolean original) {
      return original || PackFreecamState.isActive() && PackFreecamState.interactEnabled();
   }

   @ModifyExpressionValue(
      method = {"startUseItem"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/client/Minecraft;hitResult:Lnet/minecraft/world/phys/HitResult;"
      )}
   )
   private HitResult riptide$fastExpUseMissesTargets(HitResult original) {
      if (!RiptideInputClicker.isFastExpUseInProgress()) {
         return original;
      } else {
         Minecraft client = (Minecraft)this;
         return (HitResult)(client.player == null
            ? original
            : BlockHitResult.miss(client.player.getEyePosition(), Direction.DOWN, client.player.blockPosition()));
      }
   }

   @Inject(
      method = {"handleKeybinds"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$cancelCaptureOnEscape(CallbackInfo ci) {
      ScaffoldModule.beforeHandleKeybinds();
      RiptideCombatClicker.beforeHandleKeybinds();
      RiptideInputClicker.beforeHandleKeybinds();
      Minecraft client = (Minecraft)this;
      if (client.getWindow() == null) {
         RiptideCombatClicker.afterHandleKeybinds();
         RiptideInputClicker.afterHandleKeybinds();
      } else if (PackHideState.isHardLocked()) {
         RiptideCombatClicker.afterHandleKeybinds();
         RiptideInputClicker.afterHandleKeybinds();
      } else {
         if (MultiPilot.isActive()) {
            MultiPilot.drainKeybinds(client);
         }

         boolean escapeDown = GLFW.glfwGetKey(client.getWindow().handle(), 256) == 1;
         boolean justPressed = escapeDown && !this.riptide$escapeWasDown;
         this.riptide$escapeWasDown = escapeDown;
         boolean inventoryDown = client.options != null && client.options.keyInventory.isDown();
         boolean inventoryJustPressed = inventoryDown && !this.riptide$inventoryWasDown;
         this.riptide$inventoryWasDown = inventoryDown;
         if (justPressed || inventoryJustPressed) {
            if (justPressed && RiptidePayloadStudySession.finishFromEscape()) {
               RiptideCombatClicker.afterHandleKeybinds();
               RiptideInputClicker.afterHandleKeybinds();
               ci.cancel();
               return;
            }

            if (RiptideSharedState.get().consumeCaptureCancelCallback()) {
               RiptideCombatClicker.afterHandleKeybinds();
               RiptideInputClicker.afterHandleKeybinds();
               ci.cancel();
               return;
            }

            ActionEditorOverlay actionEditor = ActionEditorOverlay.getSharedOverlayIfExists();
            if (actionEditor != null && actionEditor.cancelCaptureIfActive()) {
               RiptideCombatClicker.afterHandleKeybinds();
               RiptideInputClicker.afterHandleKeybinds();
               ci.cancel();
            }
         }
      }
   }

   @Inject(
      method = {"handleKeybinds"},
      at = {@At("TAIL")}
   )
   private void riptide$releaseQueuedClicks(CallbackInfo ci) {
      RiptideCombatClicker.afterHandleKeybinds();
      RiptideInputClicker.afterHandleKeybinds();
   }

   @Inject(
      method = {"pauseGame"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$cancelLostFocusPause(boolean suppressPauseMenuIfWeReallyArePausing, CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         Minecraft client = (Minecraft)this;
         if (RiptidePayloadStudySession.finishFromEscape()) {
            ci.cancel();
         } else if (RiptideSharedState.get().consumeCaptureCancelCallback()) {
            ci.cancel();
         } else {
            ActionEditorOverlay actionEditor = ActionEditorOverlay.getSharedOverlayIfExists();
            if (actionEditor != null && actionEditor.hasActiveCaptureSession()) {
               if (actionEditor.cancelCaptureIfActive()) {
                  ci.cancel();
               } else {
                  ci.cancel();
               }
            } else {
               if (client.getWindow() != null && !client.getWindow().isFocused()) {
                  RiptideModule module = RiptideModule.get();
                  if (module != null && module.isActive() && module.isNoPauseOnLostFocus()) {
                     ci.cancel();
                  }
               }
            }
         }
      }
   }

   @Inject(
      method = {"getTickTargetMillis"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void riptide$applySpeedTimer(float defaultTickTargetMillis, CallbackInfoReturnable<Float> cir) {
      if (!PackHideState.isHardLocked()) {
         if (((Minecraft)this).player != null) {
            float multiplier = ModuleMovementUtil.speedTimerMultiplier();
            if (multiplier != 1.0F) {
               cir.setReturnValue((Float)cir.getReturnValue() / multiplier);
            }
         }
      }
   }

   @Inject(
      method = {"runTick"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/Minecraft;runAllTasks()V",
         shift = Shift.BEFORE
      )}
   )
   private void riptide$onPacketProcessFrame(CallbackInfo ci) {
      ModuleRegistry.onPacketProcessFrame();
      RiptideBlinkManager.onPacketProcessFrame();
   }
}
