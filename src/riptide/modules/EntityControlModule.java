package riptide.modules;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.DoubleSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.RegistryListSetting;

public final class EntityControlModule extends Module {
   private static final String[] DEFAULT_ENTITY_CANDIDATES = new String[]{
      "minecraft:pig",
      "minecraft:strider",
      "minecraft:horse",
      "minecraft:donkey",
      "minecraft:mule",
      "minecraft:skeleton_horse",
      "minecraft:zombie_horse",
      "minecraft:camel",
      "minecraft:happy_ghast",
      "minecraft:oak_boat",
      "minecraft:spruce_boat",
      "minecraft:birch_boat",
      "minecraft:jungle_boat",
      "minecraft:acacia_boat",
      "minecraft:cherry_boat",
      "minecraft:dark_oak_boat",
      "minecraft:pale_oak_boat",
      "minecraft:mangrove_boat",
      "minecraft:bamboo_raft",
      "minecraft:oak_chest_boat",
      "minecraft:spruce_chest_boat",
      "minecraft:birch_chest_boat",
      "minecraft:jungle_chest_boat",
      "minecraft:acacia_chest_boat",
      "minecraft:cherry_chest_boat",
      "minecraft:dark_oak_chest_boat",
      "minecraft:pale_oak_chest_boat",
      "minecraft:mangrove_chest_boat",
      "minecraft:bamboo_chest_raft"
   };
   private static EntityControlModule instance;
   private final Set<String> selectedEntities = new HashSet<>();
   private String selectedEntitiesValue = "";
   private int antiKickTicks;
   private double lastPacketY = Double.MAX_VALUE;
   private boolean restorePacketPending;
   private boolean sendingSyntheticPacket;

   public EntityControlModule() {
      super("entity-control", "EntityControl", ModuleCategory.MOVEMENT, "Controls selected rideable entities.");
      instance = this;
      this.add(RegistryListSetting.entityTypes("entities", "Entities", defaultEntities()).group("Control").description("Choose controlled mounts.").build());
      this.add(new BoolSetting("spoof-saddle", "Spoof Saddle", true).group("Control").description("Control without saddles.").build());
      this.add(new BoolSetting("max-jump", "Max Jump", true).group("Control").description("Always charge fully.").build());
      this.add(new BoolSetting("lock-yaw", "Lock Yaw", true).group("Control").description("Match your view.").build());
      this.add(new BoolSetting("cancel-server-packets", "Cancel Server Packets", true).group("Control").description("Ignore server corrections.").build());
      this.add(new BoolSetting("speed", "Speed", false).group("Speed").description("Boost mount speed.").build());
      this.add(
         new DoubleSetting("horizontal-speed", "Horizontal Speed", 10.0, 0.0, 100.0, 0.5)
            .sliderRange(0.0, 50.0)
            .group("Speed")
            .visibleWhen(() -> this.bool("speed"))
            .description("Sets horizontal speed.")
            .build()
      );
      this.add(
         new BoolSetting("only-on-ground", "Only Ground", false)
            .group("Speed")
            .visibleWhen(() -> this.bool("speed"))
            .description("Require ground contact.")
            .build()
      );
      this.add(new BoolSetting("in-water", "In Water", true).group("Speed").visibleWhen(() -> this.bool("speed")).description("Allow water speed.").build());
      this.add(new BoolSetting("fly", "Fly", false).group("Flight").description("Enable mount flight.").build());
      this.add(
         new DoubleSetting("vertical-speed", "Vertical Speed", 6.0, 0.0, 100.0, 0.5)
            .sliderRange(0.0, 20.0)
            .group("Flight")
            .visibleWhen(() -> this.bool("fly"))
            .description("Sets vertical speed.")
            .build()
      );
      this.add(
         new DoubleSetting("fall-speed", "Fall Speed", 0.0, 0.0, 100.0, 0.25)
            .sliderRange(0.0, 20.0)
            .group("Flight")
            .visibleWhen(() -> this.bool("fly"))
            .description("Sets downward drift.")
            .build()
      );
      this.add(new BoolSetting("anti-kick", "Anti Kick", true).group("Flight").visibleWhen(() -> this.bool("fly")).description("Reduce flight kicks.").build());
      this.add(
         new IntSetting("anti-kick-delay", "Anti Kick Delay", 40, 1, 80, 1)
            .group("Flight")
            .visibleWhen(() -> this.bool("fly") && this.bool("anti-kick"))
            .description("Sets anti-kick interval.")
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.resetAntiKick();
      this.refreshSelectedEntities();
   }

   @Override
   public void onDisable() {
      this.resetAntiKick();
   }

   @Override
   public void onGameLeft() {
      this.resetAntiKick();
   }

   @Override
   protected void onOptionValueChanged(String optionId) {
      if ("entities".equals(optionId)) {
         this.refreshSelectedEntities();
      }

      if ("anti-kick-delay".equals(optionId)) {
         this.antiKickTicks = this.integer("anti-kick-delay");
      }
   }

   @Override
   protected void onSettingsReset() {
      this.refreshSelectedEntities();
      this.resetAntiKick();
   }

   @Override
   public void preMovementTick() {
      if (MC.player != null && MC.getConnection() != null) {
         if (this.restorePacketPending) {
            Entity vehicle = MC.player.getVehicle();
            if (this.isControlledVehicle(vehicle)) {
               this.sendSynthetic(
                  new ServerboundMoveVehiclePacket(
                     new Vec3(vehicle.getX(), this.lastPacketY, vehicle.getZ()), vehicle.getYRot(), vehicle.getXRot(), vehicle.onGround()
                  )
               );
            }

            this.restorePacketPending = false;
         }

         if (this.antiKickTicks > 0) {
            this.antiKickTicks--;
         }
      }
   }

   @Override
   public boolean onPacketSend(Packet<?> packet) {
      if (this.sendingSyntheticPacket || !this.bool("fly") || !this.bool("anti-kick")) {
         return false;
      } else if (packet instanceof ServerboundMoveVehiclePacket movePacket) {
         Entity vehicle = MC.player == null ? null : MC.player.getVehicle();
         if (this.isControlledVehicle(vehicle) && !vehicle.isFlyingVehicle() && isOnAir(vehicle)) {
            double currentY = movePacket.position().y;
            if (this.antiKickTicks <= 0 && !this.restorePacketPending && this.shouldFlyDown(currentY)) {
               double baseline = this.lastPacketY == Double.MAX_VALUE ? currentY : this.lastPacketY;
               ServerboundMoveVehiclePacket lowered = new ServerboundMoveVehiclePacket(
                  new Vec3(movePacket.position().x, baseline - 0.0313, movePacket.position().z), movePacket.yRot(), movePacket.xRot(), movePacket.onGround()
               );
               this.sendSynthetic(lowered);
               this.restorePacketPending = true;
               this.antiKickTicks = this.integer("anti-kick-delay");
               this.lastPacketY = currentY;
               return true;
            } else {
               this.lastPacketY = currentY;
               return false;
            }
         } else {
            this.lastPacketY = movePacket.position().y;
            return false;
         }
      } else {
         return false;
      }
   }

   @Override
   public boolean onPacketReceive(Packet<?> packet) {
      return this.bool("cancel-server-packets") && packet instanceof ClientboundMoveVehiclePacket;
   }

   static Vec3 modifyVehicleMovement(Entity vehicle, MoverType type, Vec3 movement) {
      EntityControlModule module = instance;
      if (isActive(module) && type == MoverType.SELF && module.isControlledVehicle(vehicle)) {
         double velocityX = movement.x;
         double velocityY = movement.y;
         double velocityZ = movement.z;
         if (module.bool("lock-yaw")) {
            vehicle.setYRot(MC.player.getYRot());
         }

         if (module.bool("speed")
            && (!module.bool("only-on-ground") || vehicle.onGround() || vehicle.isFlyingVehicle())
            && (module.bool("in-water") || !vehicle.isInWater())) {
            Vec3 horizontal = horizontalVelocity(module.decimal("horizontal-speed"));
            velocityX = horizontal.x;
            velocityZ = horizontal.z;
         }

         if (module.bool("fly")) {
            velocityY = -module.decimal("fall-speed") / 20.0;
            if (MC.options.keyJump.isDown()) {
               velocityY += module.decimal("vertical-speed") / 20.0;
            }

            if (MC.options.keySprint.isDown()) {
               velocityY -= module.decimal("vertical-speed") / 20.0;
            }
         }

         return new Vec3(velocityX, velocityY, velocityZ);
      } else {
         return movement;
      }
   }

   public static boolean shouldLockBoatYaw() {
      EntityControlModule module = instance;
      return isActive(module) && module.bool("lock-yaw") && module.isControlledVehicle(MC.player == null ? null : MC.player.getVehicle());
   }

   public static boolean shouldSpoofSaddle(Mob mob) {
      EntityControlModule module = instance;
      return isActive(module) && module.bool("spoof-saddle") && module.isSelected(mob);
   }

   public static boolean shouldMaxJump() {
      EntityControlModule module = instance;
      Entity vehicle = MC.player == null ? null : MC.player.getVehicle();
      return isActive(module) && module.bool("max-jump") && module.isControlledVehicle(vehicle);
   }

   public static boolean shouldCancelRidingJump() {
      EntityControlModule module = instance;
      Entity vehicle = MC.player == null ? null : MC.player.getVehicle();
      return isActive(module) && module.bool("fly") && vehicle instanceof PlayerRideableJumping && module.isControlledVehicle(vehicle);
   }

   public static boolean shouldControlSteer(Entity entity) {
      EntityControlModule module = instance;
      return isActive(module)
         && entity != null
         && MC.player != null
         && MC.player.getVehicle() == entity
         && MC.player == entity.getFirstPassenger()
         && module.isSelected(entity);
   }

   private static String defaultEntities() {
      StringBuilder out = new StringBuilder();

      for (String id : DEFAULT_ENTITY_CANDIDATES) {
         Identifier parsed = Identifier.tryParse(id);
         if (parsed != null && BuiltInRegistries.ENTITY_TYPE.getOptional(parsed).isPresent()) {
            if (!out.isEmpty()) {
               out.append('|');
            }

            out.append(id);
         }
      }

      return out.toString();
   }

   private static boolean isActive(EntityControlModule module) {
      return module != null && module.isEnabled() && !PackHideState.isHardLocked() && MC.player != null;
   }

   private boolean isControlledVehicle(Entity vehicle) {
      return vehicle != null
         && MC.player != null
         && MC.player.getVehicle() == vehicle
         && vehicle.getControllingPassenger() == MC.player
         && this.isSelected(vehicle);
   }

   private boolean isSelected(Entity entity) {
      if (entity == null) {
         return false;
      } else {
         this.refreshSelectedEntities();
         Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
         return id != null && this.selectedEntities.contains(id.toString());
      }
   }

   private void refreshSelectedEntities() {
      String current = this.value("entities");
      if (!current.equals(this.selectedEntitiesValue)) {
         this.selectedEntitiesValue = current;
         this.selectedEntities.clear();
         this.selectedEntities.addAll(this.list("entities"));
      }
   }

   private void resetAntiKick() {
      this.antiKickTicks = this.integer("anti-kick-delay");
      this.lastPacketY = Double.MAX_VALUE;
      this.restorePacketPending = false;
      this.sendingSyntheticPacket = false;
   }

   private void sendSynthetic(ServerboundMoveVehiclePacket packet) {
      if (MC.getConnection() != null) {
         this.sendingSyntheticPacket = true;

         try {
            MC.getConnection().send(packet);
         } finally {
            this.sendingSyntheticPacket = false;
         }
      }
   }

   private boolean shouldFlyDown(double currentY) {
      return currentY >= this.lastPacketY || this.lastPacketY - currentY < 0.0313;
   }

   private static boolean isOnAir(Entity entity) {
      return entity.level().getBlockStates(entity.getBoundingBox().inflate(0.0625).expandTowards(0.0, -0.55, 0.0)).allMatch(BlockStateBase::isAir);
   }

   private static Vec3 horizontalVelocity(double blocksPerSecond) {
      double speed = blocksPerSecond / 20.0;
      float forward = 0.0F;
      float sideways = 0.0F;
      if (MC.options.keyUp.isDown()) {
         forward++;
      }

      if (MC.options.keyDown.isDown()) {
         forward--;
      }

      if (MC.options.keyLeft.isDown()) {
         sideways++;
      }

      if (MC.options.keyRight.isDown()) {
         sideways--;
      }

      if (forward == 0.0F && sideways == 0.0F) {
         return Vec3.ZERO;
      } else {
         double length = Math.sqrt(forward * forward + sideways * sideways);
         forward /= (float)length;
         sideways /= (float)length;
         double yaw = Math.toRadians(MC.player.getYRot());
         double sin = Math.sin(yaw);
         double cos = Math.cos(yaw);
         return new Vec3((sideways * cos - forward * sin) * speed, 0.0, (forward * cos + sideways * sin) * speed);
      }
   }
}
