package riptide.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import riptide.util.RiptideRuntimeActivity;
import riptide.util.multi.MultiPilot;
import riptide.util.multi.MultiPovModuleController;
import riptide.util.multi.PacketTeleportController;

public final class ModuleMovementUtil {
   private static final Minecraft MC = Minecraft.getInstance();
   private static volatile ModuleMovementUtil.SprintState sprintState = ModuleMovementUtil.SprintState.inactive(-1);
   private static volatile ModuleMovementUtil.MovementState movementState = ModuleMovementUtil.MovementState.inactive(-1);

   private ModuleMovementUtil() {
   }

   public static boolean shouldCancelNoFallBounce(Entity entity) {
      return MultiPovModuleController.isControlling(entity)
         ? MultiPovModuleController.cancelNoFallBounce(entity)
         : movementState().noFallAntiBounce() && MC != null && entity == MC.player;
   }

   public static float speedTimerMultiplier() {
      if (MultiPilot.isActive()) {
         return 1.0F;
      } else {
         float multiplier = 1.0F;

         for (Module module : ModuleRegistry.activeModules()) {
            if (module.shouldApplySpeedTimer()) {
               multiplier = Math.max(multiplier, module.speedTimerMultiplier());
            }
         }

         return Math.max(0.01F, Math.min(10.0F, multiplier));
      }
   }

   public static float flightFlyingSpeed(Player player) {
      if (MultiPilot.isActive()) {
         return MultiPovModuleController.flyingSpeed(player);
      } else {
         BuiltinModules.FlightModule flight = movementState().flight();
         return flight != null ? flight.getFlyingSpeed() : -1.0F;
      }
   }

   public static boolean flightNoSneak(Player player) {
      if (MultiPilot.isActive()) {
         return MultiPovModuleController.flightNoSneak(player);
      } else {
         BuiltinModules.FlightModule flight = movementState().flight();
         return flight != null && flight.noSneak();
      }
   }

   public static boolean sprintKeepsRunning() {
      return sprintIsOmnidirectional();
   }

   public static boolean sprintIsOmnidirectional() {
      return sprintState().omnidirectional();
   }

   public static boolean sprintIgnoresCollision() {
      return sprintState().ignoreCollision();
   }

   public static boolean sprintIgnoresBlindness() {
      return sprintState().ignoreBlindness();
   }

   public static boolean sprintDecision(boolean original, boolean movementTick) {
      if (MultiPilot.isActive()) {
         return original;
      } else {
         return ModuleRegistry.get("sprint") instanceof BuiltinModules.SprintModule sprint && sprint.isEnabled()
            ? sprint.sprintDecision(original, movementTick)
            : original;
      }
   }

   public static boolean sprintShouldPrevent() {
      return MultiPilot.isActive()
         ? false
         : ModuleRegistry.get("sprint") instanceof BuiltinModules.SprintModule sprint && sprint.isEnabled() && sprint.shouldPreventSprintPublic();
   }

   public static boolean sprintNetworkAllowed(boolean original) {
      if (MultiPilot.isActive()) {
         return original;
      } else {
         return AntiHungerModule.noSprintRequested() ? false : original;
      }
   }

   public static boolean sprintJumpUsesMovementYaw() {
      return ModuleRegistry.get("sprint") instanceof BuiltinModules.SprintModule sprint && sprint.isEnabled() && sprint.jumpUsesMovementYaw();
   }

   public static float movementDirectionYaw(LocalPlayer player) {
      Input keys = player.input.keyPresses;
      float yaw = player.getYRot();
      float multiplier;
      if (keys.backward() && !keys.forward()) {
         yaw += 180.0F;
         multiplier = -0.5F;
      } else if (keys.forward() && !keys.backward()) {
         multiplier = 0.5F;
      } else {
         multiplier = 1.0F;
      }

      if (keys.left() && !keys.right()) {
         yaw -= 90.0F * multiplier;
      }

      if (keys.right() && !keys.left()) {
         yaw += 90.0F * multiplier;
      }

      return yaw;
   }

   public static Vec3 withStrafe(LocalPlayer player, Vec3 velocity, double speed) {
      Input keys = player.input.keyPresses;
      if (keys.forward() == keys.backward() && keys.left() == keys.right()) {
         return new Vec3(0.0, velocity.y, 0.0);
      } else {
         double angle = Math.toRadians(movementDirectionYaw(player));
         return new Vec3(-Math.sin(angle) * speed, velocity.y, Math.cos(angle) * speed);
      }
   }

   public static boolean sprintModuleEnabled() {
      return ModuleRegistry.get("sprint") instanceof BuiltinModules.SprintModule sprint && sprint.isEnabled();
   }

   private static ModuleMovementUtil.SprintState sprintState() {
      int revision = ModuleRegistry.revision();
      ModuleMovementUtil.SprintState snapshot = sprintState;
      if (snapshot.revision() == revision) {
         return snapshot;
      } else {
         if (ModuleRegistry.get("sprint") instanceof BuiltinModules.SprintModule sprint && sprint.isEnabled()) {
            snapshot = new ModuleMovementUtil.SprintState(revision, sprint.omnidirectional(), sprint.ignoreCollision(), sprint.ignoreBlindness());
         } else {
            snapshot = ModuleMovementUtil.SprintState.inactive(revision);
         }

         sprintState = snapshot;
         return snapshot;
      }
   }

   private static ModuleMovementUtil.MovementState movementState() {
      int revision = ModuleRegistry.revision();
      ModuleMovementUtil.MovementState snapshot = movementState;
      if (snapshot.revision() == revision) {
         return snapshot;
      } else {
         BuiltinModules.FlightModule flight = ModuleRegistry.get("flight") instanceof BuiltinModules.FlightModule typed && typed.isEnabled() ? typed : null;
         BuiltinModules.SpeedModule speed = ModuleRegistry.get("speed") instanceof BuiltinModules.SpeedModule typedx && typedx.isEnabled() ? typedx : null;
         Module noFall = ModuleRegistry.get("no-fall");
         boolean noFallAntiBounce = noFall != null && noFall.isEnabled() && Boolean.parseBoolean(noFall.value("anti-bounce"));
         snapshot = new ModuleMovementUtil.MovementState(revision, flight, speed, noFallAntiBounce);
         movementState = snapshot;
         return snapshot;
      }
   }

   public static void preMovementTick() {
      if (!MultiPilot.isActive()) {
         if (!PacketTeleportController.ownsMainMovement()) {
            if (RiptideRuntimeActivity.has(4L)) {
               ModuleRegistry.preMovementTick();
            }
         }
      }
   }

   public static Vec3 onPlayerMove(Entity entity, MoverType type, Vec3 movement) {
      if (MultiPovModuleController.isControlling(entity)) {
         return MultiPovModuleController.modifyMovement(entity, type, movement);
      } else if (!PacketTeleportController.ownsMainMovement() || MC.player == null || entity != MC.player && entity != MC.player.getVehicle()) {
         if (AutoTotemModule.operationActive()) {
            return movement;
         } else if (entity != MC.player) {
            if (MC.player != null && entity == MC.player.getVehicle() && !PackHideState.isActive()) {
               Vec3 adjusted = EntityControlModule.modifyVehicleMovement(entity, type, movement);
               adjusted = BoatFlyModule.modifyVehicleMovement(entity, type, adjusted);
               if (type == MoverType.SELF && adjusted != movement) {
                  entity.setDeltaMovement(adjusted);
               }

               return adjusted;
            } else {
               return movement;
            }
         } else if (MultiPilot.isActive()) {
            return movement;
         } else if (!RiptideRuntimeActivity.has(8L)) {
            return movement;
         } else {
            Vec3 adjusted = ModuleRegistry.onPlayerMove(type, movement);
            if (type == MoverType.SELF && adjusted != movement) {
               MC.player.setDeltaMovement(adjusted);
            }

            return adjusted;
         }
      } else {
         return Vec3.ZERO;
      }
   }

   public static void applySpeedAfterLiquidTravel(Entity entity) {
      if (!PacketTeleportController.ownsMainMovement() || MC.player == null || entity != MC.player && entity != MC.player.getVehicle()) {
         if (MultiPovModuleController.isControlling(entity)) {
            Vec3 movement = entity.getDeltaMovement();
            Vec3 adjusted = MultiPovModuleController.afterLiquidTravel(entity, movement);
            if (adjusted != null && adjusted != movement) {
               entity.setDeltaMovement(adjusted);
            }
         } else if (entity == MC.player && !PackHideState.isActive()) {
            ModuleMovementUtil.MovementState state = movementState();
            BuiltinModules.SpeedModule speed = state.speed();
            if (speed != null) {
               Vec3 movement = entity.getDeltaMovement();
               Vec3 adjusted = speed.afterLiquidTravel(movement);
               if (adjusted != null && adjusted != movement) {
                  entity.setDeltaMovement(adjusted);
               }
            }
         }
      }
   }

   private record MovementState(int revision, BuiltinModules.FlightModule flight, BuiltinModules.SpeedModule speed, boolean noFallAntiBounce) {
      static ModuleMovementUtil.MovementState inactive(int revision) {
         return new ModuleMovementUtil.MovementState(revision, null, null, false);
      }
   }

   private record SprintState(int revision, boolean omnidirectional, boolean ignoreCollision, boolean ignoreBlindness) {
      static ModuleMovementUtil.SprintState inactive(int revision) {
         return new ModuleMovementUtil.SprintState(revision, false, false, false);
      }
   }
}
