package riptide.modules;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import riptide.api.module.DoubleSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.RangeSetting;
import riptide.api.module.ValueRange;

public final class SafeWalkModule extends Module {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final double DROP_PROBE_DEPTH = 0.55;
   private static final double PROBE_INSET = 0.05;
   static final double MIN_EDGE_DISTANCE = 0.15;
   private static final int RELEASE_CLEAR_TICKS = 3;
   private final Random random = new Random();
   private long holdUntilMs;
   private boolean holding;
   private int clearTicks;

   static double safeEdgeDistance(double configured) {
      return Math.max(0.15, configured);
   }

   public SafeWalkModule() {
      super("safe-walk", "SafeWalk", ModuleCategory.MOVEMENT, "Sneaks at ledges.");
      this.add(new IntSetting("look-down", "Look Down", 25, 0, 90, 1).unit("deg").description("Minimum downward look"));
      this.add(new DoubleSetting("edge-distance", "Edge Distance", 0.3, 0.15, 1.5, 0.05).unit("blocks").description("Ledge probe distance"));
      this.add(new RangeSetting("hold-time", "Hold Time", new ValueRange(100, 200), 0.0, 500.0, 5.0).unit("ms").description("Random sneak hold time"));
   }

   @Override
   public void onDisable() {
      this.holding = false;
      this.holdUntilMs = 0L;
      this.clearTicks = 0;
   }

   public static Input modifyMovementInput(ClientInput source, Input original) {
      if (original != null && MC != null && MC.player != null && MC.player.input == source) {
         if (ModuleRegistry.get("safe-walk") instanceof SafeWalkModule safeWalk) {
            if (!safeWalk.isEnabled()) {
               safeWalk.holding = false;
               return original;
            } else if (!safeWalk.sneakRequested()) {
               return original;
            } else {
               return original.shift()
                  ? original
                  : new Input(original.forward(), original.backward(), original.left(), original.right(), original.jump(), true, original.sprint());
            }
         } else {
            return original;
         }
      } else {
         return original;
      }
   }

   private boolean sneakRequested() {
      if (scaffoldOwnsTheEdge()) {
         this.holding = false;
         return false;
      } else {
         boolean atEdge = this.conditionsMet();
         long now = System.currentTimeMillis();
         this.clearTicks = atEdge ? 0 : this.clearTicks + 1;
         if (this.holding && now < this.holdUntilMs) {
            return true;
         } else if (atEdge) {
            this.holding = true;
            this.holdUntilMs = now + this.holdMillis();
            return true;
         } else if (holdsThroughFlicker(this.holding, this.clearTicks, 3)) {
            return true;
         } else {
            this.holding = false;
            return false;
         }
      }
   }

   static boolean holdsThroughFlicker(boolean holding, int clearTicks, int releaseTicks) {
      return holding && clearTicks < releaseTicks;
   }

   private long holdMillis() {
      ValueRange band = ValueRange.parse(this.value("hold-time"), new ValueRange(100, 200));
      return Math.round(band.random(this.random));
   }

   private boolean conditionsMet() {
      LocalPlayer player = MC.player;
      if (player == null || MC.level == null) {
         return false;
      } else if (player.onGround() && !player.isPassenger() && !player.getAbilities().flying) {
         return player.getXRot() < this.integer("look-down") ? false : this.dropAhead(safeEdgeDistance(this.decimal("edge-distance")));
      } else {
         return false;
      }
   }

   private boolean dropAhead(double distance) {
      LocalPlayer player = MC.player;
      Vec3 velocity = player.getDeltaMovement();
      Vec3 direction = velocity.horizontalDistanceSqr() > 1.0E-6
         ? new Vec3(velocity.x, 0.0, velocity.z).normalize()
         : Vec3.directionFromRotation(0.0F, player.getYRot());
      Vec3 probe = player.position().add(direction.scale(distance));
      BlockPos underProbe = BlockPos.containing(probe).below();
      if (!MC.level.isOutsideBuildHeight(underProbe)
         && ScaffoldModule.standableSupportState(MC.level.getBlockState(underProbe), MC.level, underProbe, CollisionContext.of(player))) {
         return false;
      } else {
         double half = Math.max(0.05, player.getBbWidth() / 2.0 - 0.05);
         AABB below = new AABB(probe.x - half, probe.y - 0.55, probe.z - half, probe.x + half, probe.y, probe.z + half);
         return MC.level.noCollision(player, below);
      }
   }

   private static boolean scaffoldOwnsTheEdge() {
      Module scaffold = ModuleRegistry.get("scaffold");
      return scaffold != null && scaffold.isEnabled();
   }
}
