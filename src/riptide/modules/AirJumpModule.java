package riptide.modules;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import riptide.api.module.ChoiceSetting;

public final class AirJumpModule extends Module {
   private boolean doubleJump = true;
   private static volatile long lastJumpDownAtMs;

   public AirJumpModule() {
      super("air-jump", "AirJump", ModuleCategory.MOVEMENT, "Jump in mid air.");
      this.add(new ChoiceSetting("mode", "Mode", "JumpFreely", "JumpFreely", "DoubleJump", "GhostBlock").build());
   }

   @Override
   public void preMovementTick() {
      if (MC.player != null && MC.player.onGround()) {
         this.doubleJump = true;
      }
   }

   private boolean allowJump() {
      return "JumpFreely".equals(this.choice("mode")) || "DoubleJump".equals(this.choice("mode")) && this.doubleJump;
   }

   public static boolean shouldAirJump() {
      return ModuleRegistry.get("air-jump") instanceof AirJumpModule airJump && airJump.isEnabled() && airJump.allowJump();
   }

   public static void onJumpFromGround(LivingEntity entity) {
      if (MC != null && MC.player != null && entity == MC.player) {
         if (ModuleRegistry.get("air-jump") instanceof AirJumpModule airJump && airJump.isEnabled() && airJump.doubleJump && !MC.player.onGround()) {
            airJump.doubleJump = false;
         }
      }
   }

   public static VoxelShape ghostBlockShape(VoxelShape original, BlockPos pos) {
      if (MC != null && MC.player != null && MC.options != null) {
         return ModuleRegistry.get("air-jump") instanceof AirJumpModule airJump
               && airJump.isEnabled()
               && "GhostBlock".equals(airJump.choice("mode"))
               && pos.getY() < MC.player.blockPosition().getY()
               && ghostJumpHeld()
            ? Shapes.block()
            : original;
      } else {
         return original;
      }
   }

   public static boolean ghostJumpHeld() {
      if (MC != null && MC.options != null) {
         long now = System.currentTimeMillis();
         if (MC.options.keyJump.isDown()) {
            lastJumpDownAtMs = now;
            return true;
         } else {
            return now - lastJumpDownAtMs <= ghostGraceMs();
         }
      } else {
         return false;
      }
   }

   private static long ghostGraceMs() {
      if (MC.player == null) {
         return 300L;
      } else {
         Vec3 v = MC.player.getDeltaMovement();
         double speed = Math.max(Math.hypot(v.x, v.z), Math.abs(v.y));
         if (speed <= 0.22) {
            return 300L;
         } else {
            return speed >= 0.8 ? 900L : 300L + (long)((speed - 0.22) / 0.58 * 600.0);
         }
      }
   }
}
