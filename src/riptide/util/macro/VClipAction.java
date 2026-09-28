package riptide.util.macro;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import riptide.util.RiptideClientMessaging;
import riptide.util.multi.PacketTeleportController;

public class VClipAction implements MacroAction {
   private static final double AUTO_SCAN_STEP = 0.5;
   private static final double AUTO_SCAN_RANGE = 128.0;
   public VClipAction.Mode mode = VClipAction.Mode.MANUAL;
   public double deltaY = 0.0;
   public boolean useSegmented = true;
   public int segmentBlocks = 10;
   public int maxPackets = 20;
   public boolean updateLocalPosition = true;
   public boolean tryVehicleFirst = true;
   public boolean forceGrounded = true;
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
      VClipAction.Options options = new VClipAction.Options();
      options.mode = this.mode;
      options.blocks = this.deltaY;
      options.useSegmented = this.useSegmented;
      options.segmentBlocks = this.segmentBlocks;
      options.maxPackets = this.maxPackets;
      options.updateLocalPosition = this.updateLocalPosition;
      options.tryVehicleFirst = this.tryVehicleFirst;
      options.forceGrounded = this.forceGrounded;
      perform(mc, options);
   }

   public static VClipAction.Result perform(Minecraft mc, VClipAction.Options options) {
      if (options == null) {
         options = new VClipAction.Options();
      }

      if (mc != null && mc.player != null && mc.getConnection() != null) {
         LocalPlayer player = mc.player;
         double blocks = options.blocks;
         if (options.mode != VClipAction.Mode.MANUAL) {
            VClipAction.AutoVerticalTarget target = resolveAutoVerticalTarget(player, options.mode);
            if (!target.success()) {
               if (options.showMessage) {
                  RiptideClientMessaging.sendPrefixed("§cVClip: " + target.message());
               }

               return new VClipAction.Result(false, 0, target.message());
            }

            blocks = target.deltaY();
         }

         int segment = Math.max(1, options.segmentBlocks);
         int maxPackets = Math.max(1, options.maxPackets);
         int packetsRequired = options.useSegmented ? (int)Math.ceil(Math.abs(blocks) / segment) : 1;
         if (packetsRequired > maxPackets) {
            packetsRequired = 1;
         }

         if (packetsRequired <= 0) {
            packetsRequired = 1;
         }

         Entity vehicle = options.tryVehicleFirst ? player.getVehicle() : null;
         if (vehicle != null) {
            try {
               for (int i = 0; i < packetsRequired - 1; i++) {
                  sendPacket(mc, ServerboundMoveVehiclePacket.fromEntity(vehicle));
               }

               vehicle.setPos(vehicle.getX(), vehicle.getY() + blocks, vehicle.getZ());
               sendPacket(mc, ServerboundMoveVehiclePacket.fromEntity(vehicle));
            } catch (Throwable var18) {
               String message = "Vehicle vclip failed: " + var18.getMessage();
               if (options.showMessage) {
                  RiptideClientMessaging.sendPrefixed("§c" + message);
               }

               return new VClipAction.Result(false, packetsRequired, message);
            }
         } else {
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();
            boolean grounded = options.forceGrounded || player.onGround();
            boolean horizontalCollision = player.horizontalCollision;
            if (blocks < -3.0) {
               int padding = options.useSegmented ? Math.max(0, packetsRequired - 1) : 0;
               packetsRequired = sendFallSafeDownClip(mc, player, x, y, z, blocks, padding, options.updateLocalPosition);
            } else {
               player.resetFallDistance();

               for (int i = 0; i < packetsRequired - 1; i++) {
                  sendPacket(mc, new StatusOnly(grounded, horizontalCollision));
               }

               sendPacket(mc, new Pos(x, y + blocks, z, grounded, horizontalCollision));
               if (options.updateLocalPosition) {
                  player.setPos(x, y + blocks, z);
                  clearLocalFallState(player);
               }
            }
         }

         String prefix = options.mode == VClipAction.Mode.MANUAL
            ? "vclip " + blocks
            : "vclip " + options.mode.name().toLowerCase(Locale.ROOT) + " -> " + String.format(Locale.ROOT, "%.2f", blocks);
         String message = prefix + " (" + packetsRequired + " packet" + (packetsRequired == 1 ? "" : "s") + ")";
         if (options.showMessage) {
            RiptideClientMessaging.sendPrefixed("§a" + message);
         }

         return new VClipAction.Result(true, packetsRequired, message);
      } else {
         if (options.showMessage) {
            RiptideClientMessaging.sendPrefixed("§cVClip: no world / connection.");
         }

         return new VClipAction.Result(false, 0, "No world / connection");
      }
   }

   private static VClipAction.AutoVerticalTarget resolveAutoVerticalTarget(LocalPlayer player, VClipAction.Mode mode) {
      if (mode == VClipAction.Mode.TOP) {
         return resolveTopTarget(player);
      } else {
         return mode == VClipAction.Mode.BOTTOM ? resolveBottomTarget(player) : new VClipAction.AutoVerticalTarget(true, 0.0, "manual");
      }
   }

   private static VClipAction.AutoVerticalTarget resolveTopTarget(LocalPlayer player) {
      Vec3 start = player.position();
      Vec3 candidate = PacketRoutePlanner.findTopLanding(PacketRoutePlanner.forEntity(player), start, 128.0);
      return candidate != null
         ? new VClipAction.AutoVerticalTarget(true, candidate.y - start.y, "top")
         : new VClipAction.AutoVerticalTarget(false, 0.0, "no direct safe top target");
   }

   private static VClipAction.AutoVerticalTarget resolveBottomTarget(LocalPlayer player) {
      Vec3 start = player.position();

      for (double offset = 0.5; offset <= 128.0; offset += 0.5) {
         Vec3 candidate = new Vec3(start.x, start.y - offset, start.z);
         if (isPositionLoaded(player, candidate) && isPositionClear(player, candidate) && hasSupportBelow(player, candidate)) {
            return new VClipAction.AutoVerticalTarget(true, candidate.y - start.y, "bottom");
         }
      }

      return new VClipAction.AutoVerticalTarget(false, 0.0, "no direct safe bottom target");
   }

   private static boolean isPositionLoaded(LocalPlayer player, Vec3 pos) {
      return player != null && player.level() != null && player.level().isLoaded(BlockPos.containing(pos));
   }

   private static boolean isPositionClear(LocalPlayer player, Vec3 pos) {
      Vec3 delta = pos.subtract(player.position());
      return player.level().noCollision(player, player.getBoundingBox().move(delta));
   }

   private static boolean hasSupportBelow(LocalPlayer player, Vec3 pos) {
      Vec3 delta = pos.subtract(player.position());
      AABB moved = player.getBoundingBox().move(delta);
      return !player.level().noCollision(player, moved.move(0.0, -0.0625, 0.0));
   }

   private static int sendFallSafeDownClip(
      Minecraft mc, LocalPlayer player, double x, double y, double z, double blocks, int paddingPackets, boolean updateLocalPosition
   ) {
      double targetY = y + blocks;
      clearLocalFallState(player);
      int sent = 0;

      for (int i = 0; i < paddingPackets; i++) {
         sendPacket(mc, new StatusOnly(true, false));
         sent++;
      }

      sendPacket(mc, new StatusOnly(true, false));
      sent++;
      Vec3 from = new Vec3(x, y, z);
      Vec3 target = new Vec3(x, targetY, z);

      for (PacketClipSafety.Step step : PacketClipSafety.positionSteps(from, target, true)) {
         sendPacket(mc, new Pos(step.position(), step.onGround(), false));
         sent++;
      }

      if (updateLocalPosition) {
         player.setPos(x, targetY, z);
         clearLocalFallState(player);
      }

      return sent;
   }

   private static void sendPacket(Minecraft mc, Packet<?> packet) {
      PacketTeleportController.runAtomicClipSend(() -> mc.getConnection().send(packet));
   }

   private static void clearLocalFallState(LocalPlayer player) {
      player.resetFallDistance();
      Vec3 velocity = player.getDeltaMovement();
      if (velocity.y < 0.0) {
         player.setDeltaMovement(velocity.x, 0.0, velocity.z);
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("mode", this.mode.name());
      tag.putDouble("deltaY", this.deltaY);
      tag.putBoolean("useSegmented", this.useSegmented);
      tag.putInt("segmentBlocks", Math.max(1, this.segmentBlocks));
      tag.putInt("maxPackets", Math.max(1, this.maxPackets));
      tag.putBoolean("updateLocalPosition", this.updateLocalPosition);
      tag.putBoolean("tryVehicleFirst", this.tryVehicleFirst);
      tag.putBoolean("forceGrounded", this.forceGrounded);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.mode = parseMode(tag.getStringOr("mode", VClipAction.Mode.MANUAL.name()));
      this.deltaY = tag.getDoubleOr("deltaY", 0.0);
      this.useSegmented = tag.getBooleanOr("useSegmented", true);
      this.segmentBlocks = Math.max(1, tag.getIntOr("segmentBlocks", 10));
      this.maxPackets = Math.max(1, tag.getIntOr("maxPackets", 20));
      this.updateLocalPosition = tag.getBooleanOr("updateLocalPosition", true);
      this.tryVehicleFirst = tag.getBooleanOr("tryVehicleFirst", true);
      this.forceGrounded = tag.getBooleanOr("forceGrounded", true);
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.VCLIP;
   }

   @Override
   public String getDisplayName() {
      if (this.mode == VClipAction.Mode.TOP) {
         return "VClip Top";
      } else {
         return this.mode == VClipAction.Mode.BOTTOM ? "VClip Bottom" : "VClip Y=" + String.format(Locale.ROOT, "%.2f", this.deltaY);
      }
   }

   @Override
   public String getIcon() {
      return "VC";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   private static VClipAction.Mode parseMode(String value) {
      try {
         return VClipAction.Mode.valueOf(value == null ? VClipAction.Mode.MANUAL.name() : value.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var2) {
         return VClipAction.Mode.MANUAL;
      }
   }

   private record AutoVerticalTarget(boolean success, double deltaY, String message) {
   }

   public static enum Mode {
      MANUAL,
      TOP,
      BOTTOM;
   }

   public static final class Options {
      public VClipAction.Mode mode = VClipAction.Mode.MANUAL;
      public double blocks = 0.0;
      public boolean useSegmented = true;
      public int segmentBlocks = 10;
      public int maxPackets = 20;
      public boolean updateLocalPosition = true;
      public boolean tryVehicleFirst = true;
      public boolean forceGrounded = true;
      public boolean showMessage = false;

      public static VClipAction.Options defaults(double blocks) {
         VClipAction.Options options = new VClipAction.Options();
         options.blocks = blocks;
         return options;
      }

      public VClipAction.Options singlePacket() {
         this.useSegmented = false;
         return this;
      }
   }

   public record Result(boolean success, int packetsRequired, String message) {
   }
}
