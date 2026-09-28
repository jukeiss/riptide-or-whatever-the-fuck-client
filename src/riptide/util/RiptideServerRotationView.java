package riptide.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import riptide.modules.PackFreecamState;
import riptide.modules.PackHideState;

public final class RiptideServerRotationView {
   private static volatile RiptideServerRotationView.Timeline timeline = RiptideServerRotationView.Timeline.empty();

   private RiptideServerRotationView() {
   }

   public static void onPacketWritten(Packet<?> packet) {
      if (packet instanceof ServerboundMovePlayerPacket movement && movement.hasRotation()) {
         updateAtTick(movement.getYRot(0.0F), movement.getXRot(0.0F), RiptideSharedState.get().getClientTickCounter());
      }
   }

   static void update(float yaw, float pitch) {
      updateAtTick(yaw, pitch, RiptideSharedState.get().getClientTickCounter());
   }

   static synchronized void updateAtTick(float yaw, float pitch, int tick) {
      if (Float.isFinite(yaw) && Float.isFinite(pitch)) {
         RiptideServerRotationView.Rotation next = sanitized(yaw, pitch);
         RiptideServerRotationView.Timeline old = timeline;
         if (old.current() == null) {
            timeline = new RiptideServerRotationView.Timeline(next, next, tick);
         } else if (old.tick() == tick) {
            timeline = new RiptideServerRotationView.Timeline(old.previous(), next, tick);
         } else {
            timeline = new RiptideServerRotationView.Timeline(old.current(), next, tick);
         }
      }
   }

   public static synchronized void reset() {
      timeline = RiptideServerRotationView.Timeline.empty();
   }

   public static RiptideServerRotationView.WireSnapshot snapshot() {
      RiptideServerRotationView.Timeline value = timeline;
      RiptideServerRotationView.Rotation previous = value.previous();
      RiptideServerRotationView.Rotation current = value.current();
      return new RiptideServerRotationView.WireSnapshot(
         previous == null ? Float.NaN : previous.yaw(),
         previous == null ? Float.NaN : previous.pitch(),
         current == null ? Float.NaN : current.yaw(),
         current == null ? Float.NaN : current.pitch(),
         value.tick(),
         current != null
      );
   }

   static RiptideServerRotationView.Rotation currentRotation() {
      return timeline.current();
   }

   static RiptideServerRotationView.Timeline currentTimeline() {
      return timeline;
   }

   public static void applyLocalPlayerPose(Entity entity, EntityRenderState state, float partialTick) {
      Minecraft minecraft = Minecraft.getInstance();
      if (entity != null && entity == minecraft.player && entity == minecraft.getCameraEntity() && state instanceof LivingEntityRenderState livingState) {
         boolean thirdPerson = minecraft.options != null && !minecraft.options.getCameraType().isFirstPerson();
         boolean freecam = PackFreecamState.isActive();
         boolean hidden = PackHideState.isActive();
         if (thirdPerson && !freecam && !hidden) {
            boolean silentOwner = minecraft.player != null && RiptideSilentAim.activeOutgoingRotation(minecraft.player) != null;
            if (silentOwner) {
               RiptideServerRotationView.Rotation rotation = interpolatedRotation(timeline, partialTick, RiptideSharedState.get().getClientTickCounter());
               if (rotation != null) {
                  applyRotation(livingState, rotation);
                  if (state instanceof AvatarRenderState avatarState) {
                     applyFallFlyingRotation(entity, avatarState, rotation);
                  }
               }
            }
         }
      }
   }

   static boolean shouldApply(boolean localPlayer, boolean cameraEntity, boolean thirdPerson, boolean freecam, boolean hidden, boolean silentOwner) {
      return localPlayer && cameraEntity && thirdPerson && !freecam && !hidden && silentOwner;
   }

   static RiptideServerRotationView.Rotation interpolatedRotation(RiptideServerRotationView.Timeline value, float partialTick, int renderTick) {
      if (value != null && value.current() != null) {
         RiptideServerRotationView.Rotation previous = value.previous() == null ? value.current() : value.previous();
         float progress;
         if (renderTick < value.tick()) {
            progress = 0.0F;
         } else if (renderTick == value.tick()) {
            progress = Mth.clamp(partialTick, 0.0F, 1.0F);
         } else {
            progress = 1.0F;
         }

         return interpolate(previous, value.current(), progress);
      } else {
         return null;
      }
   }

   static RiptideServerRotationView.Rotation interpolate(
      RiptideServerRotationView.Rotation previous, RiptideServerRotationView.Rotation current, float progress
   ) {
      if (previous == null) {
         return current;
      } else if (current == null) {
         return previous;
      } else {
         float amount = Mth.clamp(progress, 0.0F, 1.0F);
         float yawDelta = Mth.wrapDegrees(current.yaw() - previous.yaw());
         float yaw = Mth.wrapDegrees(previous.yaw() + yawDelta * amount);
         float pitch = previous.pitch() + (current.pitch() - previous.pitch()) * amount;
         return sanitized(yaw, pitch);
      }
   }

   private static RiptideServerRotationView.Rotation sanitized(float yaw, float pitch) {
      return new RiptideServerRotationView.Rotation(Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0F, 90.0F));
   }

   static void applyRotation(LivingEntityRenderState state, RiptideServerRotationView.Rotation rotation) {
      if (state != null && rotation != null) {
         state.bodyRot = rotation.yaw();
         state.yRot = 0.0F;
         state.xRot = state.isUpsideDown ? -rotation.pitch() : rotation.pitch();
      }
   }

   private static void applyFallFlyingRotation(Entity entity, AvatarRenderState state, RiptideServerRotationView.Rotation rotation) {
      if (state.isFallFlying && entity != null) {
         Vec3 look = Vec3.directionFromRotation(rotation.pitch(), rotation.yaw());
         Vec3 movement = entity.getDeltaMovement();
         if (!(movement.horizontalDistanceSqr() <= 1.0E-5) && !(look.horizontalDistanceSqr() <= 1.0E-5)) {
            Vec3 horizontalMovement = movement.horizontal().normalize();
            Vec3 horizontalLook = look.horizontal().normalize();
            double dot = Mth.clamp(horizontalMovement.dot(horizontalLook), -1.0, 1.0);
            double sign = movement.x * look.z - movement.z * look.x;
            state.shouldApplyFlyingYRot = true;
            state.flyingYRot = (float)(Math.signum(sign) * Math.acos(Math.abs(dot)));
         } else {
            state.shouldApplyFlyingYRot = false;
            state.flyingYRot = 0.0F;
         }
      }
   }

   record Rotation(float yaw, float pitch) {
   }

   record Timeline(RiptideServerRotationView.Rotation previous, RiptideServerRotationView.Rotation current, int tick) {
      static RiptideServerRotationView.Timeline empty() {
         return new RiptideServerRotationView.Timeline(null, null, Integer.MIN_VALUE);
      }
   }

   public record WireSnapshot(float previousYaw, float previousPitch, float currentYaw, float currentPitch, int tick, boolean initialized) {
   }
}
