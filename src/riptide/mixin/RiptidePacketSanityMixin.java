package riptide.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.Parameters;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.security.RiptideComponentSanity;
import riptide.security.RiptideNumericSanity;

@Mixin({ClientPacketListener.class})
public abstract class RiptidePacketSanityMixin {
   @Inject(
      method = {"handleSetEntityMotion"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$saneSetEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
      if (RiptideNumericSanity.motionOutOfRange(packet.movement())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"handleExplosion"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$saneExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
      if (RiptideNumericSanity.outOfRange(packet.center())
         || RiptideNumericSanity.outOfRange(packet.radius())
         || packet.playerKnockback().isPresent() && RiptideNumericSanity.motionOutOfRange((Vec3)packet.playerKnockback().get())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"handleMovePlayer"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$saneMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
      if (RiptideNumericSanity.positionMoveOutOfRange(packet.change())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"handleTeleportEntity"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$saneTeleportEntity(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
      if (RiptideNumericSanity.positionMoveOutOfRange(packet.change())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"handleEntityPositionSync"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$saneEntityPositionSync(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
      if (RiptideNumericSanity.positionMoveOutOfRange(packet.values())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"handleAddEntity"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$saneAddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
      if (RiptideNumericSanity.outOfRange(packet.getX())
         || RiptideNumericSanity.outOfRange(packet.getY())
         || RiptideNumericSanity.outOfRange(packet.getZ())
         || RiptideNumericSanity.motionOutOfRange(packet.getMovement())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"handleParticleEvent"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$saneParticles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
      if (RiptideNumericSanity.outOfRange(packet.getX())
         || RiptideNumericSanity.outOfRange(packet.getY())
         || RiptideNumericSanity.outOfRange(packet.getZ())
         || RiptideNumericSanity.outOfRange(packet.getXDist())
         || RiptideNumericSanity.outOfRange(packet.getYDist())
         || RiptideNumericSanity.outOfRange(packet.getZDist())
         || RiptideNumericSanity.outOfRange(packet.getMaxSpeed())
         || packet.getCount() > 100000) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"handleMoveVehicle"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$saneMoveVehicle(ClientboundMoveVehiclePacket packet, CallbackInfo ci) {
      if (RiptideNumericSanity.outOfRange(packet.position())
         || RiptideNumericSanity.outOfRange(packet.yRot())
         || RiptideNumericSanity.outOfRange(packet.xRot())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"handleAddObjective"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$saneObjective(ClientboundSetObjectivePacket packet, CallbackInfo ci) {
      if (!RiptideComponentSanity.isSafe(packet.getDisplayName())
         || packet.getNumberFormat().isPresent() && !RiptideComponentSanity.isSafe((NumberFormat)packet.getNumberFormat().get())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"handleSetScore"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$saneScore(ClientboundSetScorePacket packet, CallbackInfo ci) {
      if (packet.display().isPresent() && !RiptideComponentSanity.isSafe((Component)packet.display().get())
         || packet.numberFormat().isPresent() && !RiptideComponentSanity.isSafe((NumberFormat)packet.numberFormat().get())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"handleSetPlayerTeamPacket"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$saneTeam(ClientboundSetPlayerTeamPacket packet, CallbackInfo ci) {
      if (!packet.getParameters().isEmpty()) {
         Parameters parameters = (Parameters)packet.getParameters().get();
         if (!RiptideComponentSanity.isSafe(parameters.displayName())
            || !RiptideComponentSanity.isSafe(parameters.playerPrefix())
            || !RiptideComponentSanity.isSafe(parameters.playerSuffix())) {
            ci.cancel();
         }
      }
   }
}
