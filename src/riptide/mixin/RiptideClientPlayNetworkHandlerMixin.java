package riptide.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.commands.RiptideCommands;
import riptide.modules.AntiVanishModule;
import riptide.modules.InventoryTweaksModule;
import riptide.modules.ModuleRegistry;
import riptide.modules.PackHideState;
import riptide.modules.RiptideModule;
import riptide.modules.ScaffoldModule;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideConfig;
import riptide.util.RiptideLiteVariant;
import riptide.util.RiptideRotationUtil;
import riptide.util.RiptideRuntimeActivity;
import riptide.util.RiptideServerInfoOverlay;
import riptide.util.RiptideSharedState;
import riptide.util.SodiumTerrainPassGuard;
import riptide.util.macro.MacroConditionRegistry;
import riptide.util.mm.MmCardActions;
import riptide.util.multi.PacketTeleportController;

@Mixin({ClientPacketListener.class})
public abstract class RiptideClientPlayNetworkHandlerMixin {
   @Unique
   private boolean riptide$viewCaptured;
   @Unique
   private float riptide$viewYaw;
   @Unique
   private float riptide$viewPitch;
   @Unique
   private float riptide$viewYawO;
   @Unique
   private float riptide$viewPitchO;

   @Inject(
      method = {"handleBlockChangedAck"},
      at = {@At("RETURN")}
   )
   private void riptide$onBlockPredictionAckApplied(ClientboundBlockChangedAckPacket packet, CallbackInfo ci) {
      ScaffoldModule.onBlockChangedAckHandled(packet.sequence());
   }

   @Inject(
      method = {"handleBlockUpdate"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/multiplayer/ClientLevel;setServerVerifiedBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)V",
         shift = Shift.BEFORE
      )}
   )
   private void riptide$observeSingleBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
      AntiVanishModule.observeSingleBlockUpdate(packet);
   }

   @Unique
   private void riptide$captureView() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         RiptideRotationUtil.Rotation wire = ScaffoldModule.wireContinuityRotation();
         if (wire != null) {
            this.riptide$viewYaw = mc.player.getYRot();
            this.riptide$viewPitch = mc.player.getXRot();
            this.riptide$viewYawO = mc.player.yRotO;
            this.riptide$viewPitchO = mc.player.xRotO;
            this.riptide$viewCaptured = true;
            mc.player.setYRot(wire.yaw());
            mc.player.setXRot(wire.pitch());
         }
      }
   }

   @Unique
   private void riptide$restoreView(Minecraft mc) {
      if (this.riptide$viewCaptured) {
         this.riptide$viewCaptured = false;
         if (mc.player != null) {
            ScaffoldModule.onServerRotationApplied(mc.player.getYRot(), mc.player.getXRot());
            mc.player.setYRot(this.riptide$viewYaw);
            mc.player.setXRot(this.riptide$viewPitch);
            mc.player.yRotO = this.riptide$viewYawO;
            mc.player.xRotO = this.riptide$viewPitchO;
         }
      }
   }

   @Inject(
      method = {"handleMovePlayer"},
      at = {@At("HEAD")}
   )
   private void riptide$disarmViewCaptureMove(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
      this.riptide$viewCaptured = false;
   }

   @Inject(
      method = {"handleRotatePlayer"},
      at = {@At("HEAD")}
   )
   private void riptide$disarmViewCaptureRotate(ClientboundPlayerRotationPacket packet, CallbackInfo ci) {
      this.riptide$viewCaptured = false;
   }

   @Inject(
      method = {"handleMovePlayer"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z"
      )}
   )
   private void riptide$captureViewBeforeTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
      this.riptide$captureView();
   }

   @Inject(
      method = {"handleMovePlayer"},
      at = {@At("RETURN")}
   )
   private void riptide$onServerPositionCorrection(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
      SodiumTerrainPassGuard.armForPositionCorrection();
      Minecraft mc = Minecraft.getInstance();
      this.riptide$restoreView(mc);
      if (mc.player != null) {
         PacketTeleportController.onMainCorrection(mc.player.position());
         ScaffoldModule.onServerPositionCorrection();
      }
   }

   @Inject(
      method = {"handleRotatePlayer"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/player/Player;setYRot(F)V"
      )}
   )
   private void riptide$captureViewBeforeRotate(ClientboundPlayerRotationPacket packet, CallbackInfo ci) {
      this.riptide$captureView();
   }

   @Inject(
      method = {"handleRotatePlayer"},
      at = {@At("RETURN")}
   )
   private void riptide$onServerRotationCorrection(ClientboundPlayerRotationPacket packet, CallbackInfo ci) {
      this.riptide$restoreView(Minecraft.getInstance());
   }

   @Inject(
      method = {"handleMoveVehicle"},
      at = {@At("RETURN")}
   )
   private void riptide$onServerVehicleCorrection(ClientboundMoveVehiclePacket packet, CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.player.getVehicle() != null) {
         PacketTeleportController.onMainVehicleCorrection(mc.player.getVehicle().position());
      }
   }

   @Inject(
      method = {"handleCommands"},
      at = {@At("RETURN")}
   )
   private void riptide$onCommandTreeApplied(ClientboundCommandsPacket packet, CallbackInfo ci) {
      RiptideModule module = RiptideModule.get();
      if (module != null) {
         RiptideServerInfoOverlay overlay = module.getServerDataOverlayIfExists();
         if (overlay != null) {
            overlay.onCommandTreeChanged();
         }
      }
   }

   @Inject(
      method = {"sendUnattendedCommand"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$interceptCardClick(String command, Screen screen, CallbackInfo ci) {
      if (!RiptideLiteVariant.enabled()) {
         if (MmCardActions.handleClickCommand(command)) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"sendChat"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$infiniChatSplit(String message, CallbackInfo ci) {
      if (RiptideConfig.getGlobal().infiniChat
         && message != null
         && message.length() > 256
         && (RiptideCommands.plainChatBypass() || !RiptideCommands.isRiptideCommandMessage(message))) {
         ClientPacketListener self = (ClientPacketListener)this;

         for (int i = 0; i < message.length(); i += 256) {
            self.sendChat(message.substring(i, Math.min(message.length(), i + 256)));
         }

         ci.cancel();
      }
   }

   @Inject(
      method = {"sendChat"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$dispatchRiptideCommand(String message, CallbackInfo ci) {
      try {
         if (RiptideCommands.plainChatBypass()) {
            return;
         }

         if (!RiptideCommands.isRiptideCommandMessage(message)) {
            return;
         }

         if (RiptideCommands.isBlockedPanicCommandMessage(message)) {
            ci.cancel();
            return;
         }

         String body = RiptideCommands.commandBody(message);
         if (body.isBlank()) {
            ci.cancel();
            return;
         }

         RiptideClientMessaging.rememberRecentChat(message);
         RiptideCommands.dispatch(body);
         ci.cancel();
      } catch (Throwable var4) {
         riptide.RiptideClientAddon.LOG.warn("[Commands] sendChat interception failed for '{}'", message, var4);
         ci.cancel();
      }
   }

   @Inject(
      method = {"handleContainerContent"},
      at = {@At("RETURN")}
   )
   private void yang$onInventory(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         MacroConditionRegistry.recordInventorySync();
         boolean macroWaits = MacroConditionRegistry.hasPendingInventoryConditions();
         boolean inventoryTweaks = InventoryTweaksModule.hasContainerSyncWork();
         if (macroWaits || inventoryTweaks) {
            if (macroWaits) {
               MacroConditionRegistry.onInventorySync(Minecraft.getInstance());
            }

            if (inventoryTweaks) {
               InventoryTweaksModule.onContainerSynced(packet.containerId());
            }
         }
      }
   }

   @Inject(
      method = {"handleContainerSetSlot"},
      at = {@At("RETURN")}
   )
   private void yang$onSlotUpdate(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         MacroConditionRegistry.recordInventorySync();
         boolean macroWaits = MacroConditionRegistry.hasPendingInventoryConditions();
         boolean inventoryTweaks = InventoryTweaksModule.hasContainerSyncWork();
         if (macroWaits || inventoryTweaks) {
            if (macroWaits) {
               MacroConditionRegistry.onSlotUpdate(packet.getSlot());
            }

            if (inventoryTweaks) {
               InventoryTweaksModule.onContainerSynced(packet.getContainerId());
            }
         }
      }
   }

   @Inject(
      method = {"handleSetCursorItem"},
      at = {@At("RETURN")}
   )
   private void riptide$onSetCursorItem(ClientboundSetCursorItemPacket packet, CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         MacroConditionRegistry.recordInventorySync();
         if (MacroConditionRegistry.hasPendingInventoryConditions()) {
            MacroConditionRegistry.onInventorySync(Minecraft.getInstance());
         }
      }
   }

   @Inject(
      method = {"handleSetPlayerInventory"},
      at = {@At("RETURN")}
   )
   private void riptide$onSetPlayerInventory(ClientboundSetPlayerInventoryPacket packet, CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         MacroConditionRegistry.recordInventorySync();
         if (MacroConditionRegistry.hasPendingInventoryConditions()) {
            MacroConditionRegistry.onInventorySync(Minecraft.getInstance());
         }
      }
   }

   @Inject(
      method = {"handleSoundEvent"},
      at = {@At("RETURN")}
   )
   private void yang$onPlaySound(ClientboundSoundPacket packet, CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         boolean macroWaits = MacroConditionRegistry.hasPendingSoundConditions();
         boolean moduleHooks = RiptideRuntimeActivity.has(32L);
         if (macroWaits || moduleHooks) {
            if (macroWaits) {
               riptide$dispatchMacroSound(packet);
            }

            if (moduleHooks) {
               ModuleRegistry.onSoundPacket(packet);
            }
         }
      }
   }

   @Inject(
      method = {"handleSoundEntityEvent"},
      at = {@At("RETURN")}
   )
   private void riptide$onPlayEntitySound(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         if (MacroConditionRegistry.hasPendingSoundConditions()) {
            try {
               Minecraft mc = Minecraft.getInstance();
               if (mc == null || mc.level == null || packet == null) {
                  return;
               }

               Entity entity = mc.level.getEntity(packet.getId());
               if (entity == null) {
                  return;
               }

               String soundId = ((SoundEvent)packet.getSound().value()).location().toString();
               MacroConditionRegistry.onSoundPacket(soundId, entity.getX(), entity.getY(), entity.getZ());
            } catch (Exception var6) {
            }
         }
      }
   }

   @Inject(
      method = {"handleSetTime"},
      at = {@At("RETURN")}
   )
   private void yang$onWorldTimeUpdate(ClientboundSetTimePacket packet, CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         if (this.riptide$packetHooksActive()) {
            RiptideSharedState.get().onServerTimeSyncReceived();
         }
      }
   }

   @Unique
   private boolean riptide$packetHooksActive() {
      RiptideModule module = RiptideModule.get();
      return module != null && module.arePacketHooksActive();
   }

   @Unique
   private static void riptide$dispatchMacroSound(ClientboundSoundPacket packet) {
      try {
         if (packet == null) {
            return;
         }

         String soundId = ((SoundEvent)packet.getSound().value()).location().toString();
         MacroConditionRegistry.onSoundPacket(soundId, packet.getX(), packet.getY(), packet.getZ());
      } catch (Exception var2) {
      }
   }
}
