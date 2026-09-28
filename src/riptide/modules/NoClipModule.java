package riptide.modules;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.DoubleSetting;
import riptide.util.RiptideLiteVariant;

public final class NoClipModule extends Module {
   private static NoClipModule instance;
   private boolean noClipSet;
   private Entity forcedVehicle;
   private volatile boolean setbackPending;

   public NoClipModule() {
      super("no-clip", "NoClip", ModuleCategory.MOVEMENT, "Move through blocks.");
      instance = this;
      this.add(new DoubleSetting("speed", "Speed", 0.32, 0.1, 0.4, 0.01).description("Movement speed.").build());
      this.add(new BoolSetting("only-in-vehicle", "Only In Vehicle", false).description("Only while riding.").build());
      this.add(new BoolSetting("disable-on-setback", "Disable On Setback", true).description("Disable on server setback.").build());
   }

   @Override
   public void preMovementTick() {
      if (!RiptideLiteVariant.enabled()) {
         if (this.setbackPending) {
            this.setbackPending = false;
            this.disableWithToggleMessage("NoClip disabled: server set you back.");
         } else {
            LocalPlayer player = MC.player;
            if (player != null) {
               if (this.paused()) {
                  if (this.noClipSet) {
                     this.restore();
                  }
               } else {
                  this.noClipSet = true;
                  player.noPhysics = true;
                  player.fallDistance = 0.0;
                  player.setOnGround(false);
                  double speed = this.decimal("speed");
                  Entity vehicle = player.getControlledVehicle();
                  this.releaseForcedVehicle(vehicle);
                  if (vehicle != null) {
                     vehicle.noPhysics = true;
                     this.forcedVehicle = vehicle;
                     if (!ModuleRegistry.isModuleEnabled("boat-fly")) {
                        applyStrafe(vehicle, player, speed);
                     }
                  } else {
                     applyStrafe(player, player, speed);
                  }
               }
            }
         }
      }
   }

   @Override
   public boolean onPacketReceive(Packet<?> packet) {
      if (packet instanceof ClientboundPlayerPositionPacket && this.bool("disable-on-setback") && !this.paused()) {
         this.setbackPending = true;
      }

      return false;
   }

   @Override
   public void onEnable() {
      this.noClipSet = false;
      this.setbackPending = false;
   }

   @Override
   public void onDisable() {
      this.restore();
   }

   @Override
   public void onGameLeft() {
      this.restore();
   }

   public static boolean holdsNoPhysics() {
      NoClipModule module = instance;
      return module != null && module.isEnabled() && !PackHideState.isHardLocked() && !module.paused();
   }

   private boolean paused() {
      LocalPlayer player = MC.player;
      return this.bool("only-in-vehicle") && (player == null || player.getControlledVehicle() == null);
   }

   private void restore() {
      this.noClipSet = false;
      if (MC.player != null) {
         MC.player.noPhysics = false;
         Entity vehicle = MC.player.getControlledVehicle();
         if (vehicle != null) {
            vehicle.noPhysics = false;
         }
      }

      this.releaseForcedVehicle(null);
   }

   private void releaseForcedVehicle(Entity keep) {
      if (this.forcedVehicle != null && this.forcedVehicle != keep) {
         this.forcedVehicle.noPhysics = false;
         this.forcedVehicle = null;
      }
   }

   private static void applyStrafe(Entity target, LocalPlayer player, double speed) {
      double y = MC.options.keyJump.isDown() ? speed : (MC.options.keyShift.isDown() ? -speed : 0.0);
      target.setDeltaMovement(ModuleMovementUtil.withStrafe(player, new Vec3(0.0, y, 0.0), speed));
   }
}
