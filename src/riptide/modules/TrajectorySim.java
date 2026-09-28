package riptide.modules;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.WindChargeItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class TrajectorySim {
   private static final TrajectorySim.Info GENERIC = new TrajectorySim.Info(0.03, 0.25, 1.5, 0.99, 0.8, 0.0F, true, true, false);
   private static final TrajectorySim.Info PERSISTENT = new TrajectorySim.Info(0.05, 0.5, 1.5, 0.99, 0.6, 0.0F, true, false, false);
   private static final TrajectorySim.Info POTION = new TrajectorySim.Info(0.05, 0.25, 0.5, 0.99, 0.8, -20.0F, true, true, false);
   private static final TrajectorySim.Info EXP_BOTTLE = new TrajectorySim.Info(0.07, 0.25, 0.7, 0.99, 0.8, -20.0F, true, true, false);
   private static final TrajectorySim.Info FISHING_ROD = new TrajectorySim.Info(0.04, 0.25, 1.5, 0.92, 0.92, 0.0F, true, true, true);
   private static final TrajectorySim.Info TRIDENT = new TrajectorySim.Info(0.05, 0.5, 2.5, 0.99, 0.99, 0.0F, true, false, false);
   private static final TrajectorySim.Info BOW_FULL_PULL = PERSISTENT.withVelocity(3.0);
   private static final TrajectorySim.Info CROSSBOW_ARROW = new TrajectorySim.Info(0.05, 0.5, 3.15, 0.99, 0.6, 0.0F, false, false, false);
   private static final TrajectorySim.Info FIREWORK_ROCKET = new TrajectorySim.Info(0.0, 0.25, 1.6, 1.0, 1.0, 0.0F, false, false, false);
   private static final TrajectorySim.Info FIREBALL = new TrajectorySim.Info(0.0, 1.0, 1.5, 0.99, 0.6, 0.0F, true, true, false);
   private static final TrajectorySim.Info WIND_CHARGE = new TrajectorySim.Info(0.0, 1.0, 1.5, 1.0, 1.0, 0.0F, true, true, false);

   private TrajectorySim() {
   }

   public static Vec3 inheritedVelocity(Entity owner) {
      if (owner == null) {
         return Vec3.ZERO;
      } else {
         Vec3 delta = owner.getDeltaMovement();
         return new Vec3(delta.x, owner.onGround() ? 0.0 : delta.y, delta.z);
      }
   }

   static TrajectorySim.Info infoForTest(String name) {
      return switch (name) {
         case "fishing" -> FISHING_ROD;
         case "generic" -> GENERIC;
         case "bow" -> BOW_FULL_PULL;
         case "potion" -> POTION;
         default -> throw new IllegalArgumentException(name);
      };
   }

   public static List<TrajectorySim.Shot> shotsFor(Player player, ItemStack stack, boolean alwaysShowBow, boolean multiShot) {
      if (player == null || stack == null || stack.isEmpty()) {
         return List.of();
      } else if (stack.getItem() instanceof BowItem) {
         int useTicks = alwaysShowBow && player.getTicksUsingItem() < 1 ? 40 : player.getTicksUsingItem();
         float power = BowItem.getPowerForTime(useTicks);
         return power < 0.1F ? List.of() : List.of(new TrajectorySim.Shot(BOW_FULL_PULL.withVelocity(power * BOW_FULL_PULL.initialVelocity()), 0.0F));
      } else if (!(stack.getItem() instanceof CrossbowItem)) {
         TrajectorySim.Info info = simpleInfo(stack);
         return info == null ? List.of() : List.of(new TrajectorySim.Shot(info, 0.0F));
      } else {
         ChargedProjectiles charged = (ChargedProjectiles)stack.get(DataComponents.CHARGED_PROJECTILES);
         TrajectorySim.Info info = charged != null && charged.contains(Items.FIREWORK_ROCKET) ? FIREWORK_ROCKET : CROSSBOW_ARROW;
         int loaded = charged == null ? 0 : charged.items().size();
         int count = 1;
         if (multiShot) {
            count = Math.max(1, loaded > 0 ? loaded : 1);
         }

         List<TrajectorySim.Shot> shots = new ArrayList<>(count);

         for (float yaw : yawOffsets(count)) {
            shots.add(new TrajectorySim.Shot(info, yaw));
         }

         return shots;
      }
   }

   private static TrajectorySim.Info simpleInfo(ItemStack stack) {
      return switch (stack.getItem()) {
         case FishingRodItem ignored -> FISHING_ROD;
         case ThrowablePotionItem ignoredx -> POTION;
         case TridentItem ignoredxx -> TRIDENT;
         case SnowballItem ignoredxxx -> GENERIC;
         case EnderpearlItem ignoredxxxx -> GENERIC;
         case EggItem ignoredxxxxx -> GENERIC;
         case ExperienceBottleItem ignoredxxxxxx -> EXP_BOTTLE;
         case FireChargeItem ignoredxxxxxxx -> FIREBALL;
         case WindChargeItem ignoredxxxxxxxx -> WIND_CHARGE;
         default -> null;
      };
   }

   static float[] yawOffsets(int shotCount) {
      if (shotCount <= 1) {
         return new float[]{0.0F};
      } else if (shotCount == 3) {
         return new float[]{-10.0F, 0.0F, 10.0F};
      } else {
         float spread = 20.0F;
         float step = spread / (shotCount - 1);
         float[] out = new float[shotCount];

         for (int i = 0; i < shotCount; i++) {
            out[i] = -spread * 0.5F + step * i;
         }

         return out;
      }
   }

   static Vec3 launchVelocity(float yawDegrees, float pitchDegrees, TrajectorySim.Info info) {
      float yaw = (float)Math.toRadians(yawDegrees);
      float pitch = (float)Math.toRadians(pitchDegrees);
      float pitchWithRoll = (float)Math.toRadians(pitchDegrees + info.roll());
      Vec3 direction = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitchWithRoll), Math.cos(yaw) * Math.cos(pitch));
      double length = direction.length();
      return length < 1.0E-7 ? Vec3.ZERO : direction.scale(info.initialVelocity() / length);
   }

   static Vec3 stepVelocity(Vec3 velocity, TrajectorySim.Info info, boolean inWater) {
      double drag = inWater ? info.dragInWater() : info.drag();
      return new Vec3(velocity.x * drag, velocity.y * drag - info.gravity(), velocity.z * drag);
   }

   public static TrajectorySim.Result simulate(
      Level level, Entity owner, TrajectorySim.Info info, float yawDegrees, float pitchDegrees, int maxTicks, Vec3 inheritedVelocity
   ) {
      List<Vec3> points = new ArrayList<>();
      if (level != null && owner != null) {
         Vec3 pos = new Vec3(owner.getX(), owner.getEyeY() - 0.1F, owner.getZ());
         Vec3 velocity = launchVelocity(yawDegrees, pitchDegrees, info);
         if (info.copiesPlayerVelocity() && inheritedVelocity != null) {
            velocity = velocity.add(inheritedVelocity);
         }

         MutableBlockPos cursor = new MutableBlockPos();
         if (info.applyVelocityBeforeFirstMove()) {
            velocity = stepVelocity(velocity, info, inWater(level, cursor, pos));
         }

         for (int tick = 0; tick < maxTicks && !(pos.y < level.getMinY()); tick++) {
            Vec3 next = pos.add(velocity);
            HitResult hit = trace(level, owner, info, pos, next);
            if (hit != null) {
               points.add(hit.getLocation());
               return new TrajectorySim.Result(points, hit);
            }

            pos = next;
            points.add(next);
            velocity = stepVelocity(velocity, info, inWater(level, cursor, next));
         }

         return new TrajectorySim.Result(points, null);
      } else {
         return new TrajectorySim.Result(points, null);
      }
   }

   private static boolean inWater(Level level, MutableBlockPos cursor, Vec3 pos) {
      cursor.set(pos.x, pos.y, pos.z);
      return !level.getBlockState(cursor).getFluidState().isEmpty();
   }

   private static HitResult trace(Level level, Entity owner, TrajectorySim.Info info, Vec3 from, Vec3 to) {
      Fluid fluidMode = info.stopsOnFluid() ? Fluid.ANY : Fluid.NONE;
      BlockHitResult block = level.clip(new ClipContext(from, to, Block.COLLIDER, fluidMode, owner));
      if (block.getType() != Type.MISS) {
         return block;
      } else {
         AABB sweep = new AABB(
               from.x - info.hitboxRadius(),
               from.y - info.hitboxRadius(),
               from.z - info.hitboxRadius(),
               from.x + info.hitboxRadius(),
               from.y + info.hitboxRadius(),
               from.z + info.hitboxRadius()
            )
            .expandTowards(to.subtract(from))
            .inflate(1.0);
         EntityHitResult entity = ProjectileUtil.getEntityHitResult(
            level,
            owner,
            from,
            to,
            sweep,
            target -> !target.isSpectator() && target.isAlive() && target.isPickable() && !owner.isPassengerOfSameVehicle(target),
            owner instanceof Projectile projectile ? ProjectileUtil.computeMargin(projectile) : 0.0F
         );
         return entity != null && entity.getType() != Type.MISS ? entity : null;
      }
   }

   public record Info(
      double gravity,
      double hitboxRadius,
      double initialVelocity,
      double drag,
      double dragInWater,
      float roll,
      boolean copiesPlayerVelocity,
      boolean applyVelocityBeforeFirstMove,
      boolean stopsOnFluid
   ) {
      TrajectorySim.Info withVelocity(double velocity) {
         return new TrajectorySim.Info(
            this.gravity,
            this.hitboxRadius,
            velocity,
            this.drag,
            this.dragInWater,
            this.roll,
            this.copiesPlayerVelocity,
            this.applyVelocityBeforeFirstMove,
            this.stopsOnFluid
         );
      }

      public boolean isSplashPotion() {
         return this == TrajectorySim.POTION;
      }
   }

   public record Result(List<Vec3> points, HitResult hit) {
   }

   public record Shot(TrajectorySim.Info info, float yawOffsetDegrees) {
   }
}
