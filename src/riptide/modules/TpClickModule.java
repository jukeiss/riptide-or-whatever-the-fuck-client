package riptide.modules;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.KeybindSetting;
import riptide.util.RiptideBindUtil;
import riptide.util.RiptideClientMessaging;
import riptide.util.multi.PacketTeleportController;

public final class TpClickModule extends Module {
   private static final int COLOR_VALID = -12136096;
   private static final int COLOR_ADJUSTED = -20421;
   private static final int COLOR_INVALID = -50373;
   private static final int MAX_CLIMB = 4;
   private static final int SEARCH_HORIZONTAL = 8;
   private static final int SEARCH_DOWN = 2;
   private static final int SEARCH_UP = 8;
   private AABB highlightBox;
   private boolean targetValid;
   private boolean adjustedTarget;
   private Vec3 destination;
   private boolean keyWasDown;

   TpClickModule() {
      super("tp-click", "TpClick", ModuleCategory.PLAYER, "Teleport where you click.");
      this.add(new IntSetting("reach", "Reach", 50, 10, 250, 5).description("Maximum click teleport distance.").build());
      this.add(new KeybindSetting("click-key", "Click Key", RiptideBindUtil.encodeMouseButton(1)).description("Button that teleports you.").build());
      this.add(new BoolSetting("block-movement", "Block Movement", true).description("Freeze movement keys while teleporting.").build());
      this.add(new BoolSetting("no-fall", "No Fall", true).description("Prevent fall damage while teleporting.").build());
      this.add(new BoolSetting("anti-kick", "Anti Kick", true).description("Prevent floating kicks while airborne.").build());
   }

   @Override
   public void onDisable() {
      this.reset();
   }

   @Override
   public void onGameLeft() {
      this.reset();
   }

   private void reset() {
      this.highlightBox = null;
      this.targetValid = false;
      this.adjustedTarget = false;
      this.destination = null;
      this.keyWasDown = false;
   }

   public static boolean blocksMovement() {
      return ModuleRegistry.get("tp-click") instanceof TpClickModule tpClick && tpClick.isEnabled() && tpClick.bool("block-movement");
   }

   public static boolean noFallActive() {
      return ModuleRegistry.get("tp-click") instanceof TpClickModule tpClick && tpClick.bool("no-fall");
   }

   public static boolean antiKickActive() {
      return ModuleRegistry.get("tp-click") instanceof TpClickModule tpClick && tpClick.bool("anti-kick");
   }

   @Override
   public void tick() {
      if (!this.isEnabled()) {
         this.reset();
      } else if (MC != null && MC.player != null && MC.level != null && !PackHideState.isHardLocked()) {
         this.updateTarget();
         int bind = this.bindCode();
         boolean down = bind != -1 && RiptideBindUtil.isBindPressed(MC, bind);
         boolean pressed = down && !this.keyWasDown;
         this.keyWasDown = down;
         if (pressed && this.targetValid && this.destination != null) {
            if (MC.gui.screen() == null) {
               if (!MC.options.keyShift.isDown()) {
                  String result = PacketTeleportController.executeMain(
                     String.format(Locale.ROOT, "%.2f %.2f %.2f", this.destination.x, this.destination.y, this.destination.z)
                  );
                  if (result != null && !result.startsWith("TP started")) {
                     RiptideClientMessaging.sendPrefixed("§e" + result);
                  }
               }
            }
         }
      } else {
         this.reset();
      }
   }

   private void updateTarget() {
      this.highlightBox = null;
      this.targetValid = false;
      this.adjustedTarget = false;
      this.destination = null;
      if (!MC.options.keyShift.isDown()) {
         boolean riding = MC.player.getVehicle() != null;
         HitResult picked = MC.player.pick(Math.max(10, this.integer("reach")), 0.0F, riding);
         if (picked instanceof BlockHitResult blockHit && picked.getType() == Type.BLOCK) {
            BlockPos base = blockHit.getBlockPos();

            for (int climb = 1; climb <= 4; climb++) {
               Vec3 spot = standingSpot(base.above(climb));
               if (spot != null) {
                  this.accept(spot, false);
                  return;
               }
            }

            Vec3 nearest = this.nearestViable(base);
            if (nearest != null) {
               this.accept(nearest, true);
            } else {
               this.highlightBox = new AABB(base).inflate(0.002);
            }
         }
      }
   }

   private void accept(Vec3 spot, boolean adjusted) {
      this.destination = spot;
      this.highlightBox = new AABB(BlockPos.containing(spot).below()).inflate(0.002);
      this.targetValid = true;
      this.adjustedTarget = adjusted;
   }

   private Vec3 nearestViable(BlockPos base) {
      Vec3 best = null;
      double bestScore = Double.MAX_VALUE;

      for (int dy = -2; dy <= 8; dy++) {
         for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
               Vec3 spot = standingSpot(base.offset(dx, dy, dz));
               if (spot != null) {
                  double score = dx * dx + dz * dz + Math.abs(dy) * 4.0;
                  if (score < bestScore) {
                     bestScore = score;
                     best = spot;
                  }
               }
            }
         }
      }

      return best;
   }

   private static Vec3 standingSpot(BlockPos feet) {
      return isFree(feet) && isFree(feet.above()) && isGroundBelow(feet) ? Vec3.atBottomCenterOf(feet) : null;
   }

   private static boolean isFree(BlockPos pos) {
      return MC.level.getBlockState(pos).getCollisionShape(MC.level, pos).isEmpty();
   }

   private static boolean isGroundBelow(BlockPos feet) {
      BlockPos below = feet.below();
      return !MC.level.getBlockState(below).getCollisionShape(MC.level, below).isEmpty()
         ? true
         : MC.player.getVehicle() != null && !MC.level.getFluidState(below).isEmpty();
   }

   private int bindCode() {
      try {
         return Integer.parseInt(this.value("click-key"));
      } catch (NumberFormatException var2) {
         return RiptideBindUtil.encodeMouseButton(1);
      }
   }

   @Override
   public boolean shouldCancelUse(HitResult hitResult, InteractionHand hand) {
      return MC != null && MC.options != null && MC.options.keyShift.isDown()
         ? false
         : this.bindCode() == RiptideBindUtil.encodeMouseButton(1) && this.targetValid;
   }

   public AABB highlightBox() {
      return this.highlightBox;
   }

   public int highlightColor() {
      return this.targetValid ? (this.adjustedTarget ? -20421 : -12136096) : -50373;
   }

   @Override
   public boolean showInModuleMenu() {
      return false;
   }
}
