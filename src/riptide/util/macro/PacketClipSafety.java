package riptide.util.macro;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class PacketClipSafety {
   public static final double DIRECT_FALL_LIMIT = 3.0;
   public static final double FALL_RESET_NUDGE = 0.0625;

   private PacketClipSafety() {
   }

   public static List<PacketClipSafety.Step> positionSteps(Vec3 from, Vec3 to, boolean finalOnGround) {
      if (from != null && to != null) {
         if (to.y - from.y >= -3.0) {
            return List.of(new PacketClipSafety.Step(to, finalOnGround));
         } else {
            Vec3 reset = new Vec3(to.x, to.y + 0.0625, to.z);
            return List.of(new PacketClipSafety.Step(to, false), new PacketClipSafety.Step(reset, false), new PacketClipSafety.Step(to, finalOnGround));
         }
      } else {
         return List.of();
      }
   }

   public static Vec3 sweptCollide(Entity entity, Vec3 from, Vec3 to) {
      if (entity != null && entity.level() != null && from != null && to != null) {
         AABB moved = entity.getBoundingBox().move(from.subtract(entity.position()));
         Vec3 delta = to.subtract(from);
         if (delta.lengthSqr() <= 1.0E-10) {
            return delta;
         } else {
            List<VoxelShape> shapes = new ArrayList<>();

            for (VoxelShape shape : entity.level().getBlockCollisions(entity, moved.expandTowards(delta))) {
               shapes.add(shape);
            }

            return Entity.collideBoundingBox(entity, delta, moved, entity.level(), shapes);
         }
      } else {
         return Vec3.ZERO;
      }
   }

   public static boolean sweptClear(Entity entity, Vec3 from, Vec3 to) {
      if (entity != null && from != null && to != null) {
         Vec3 delta = to.subtract(from);
         return delta.lengthSqr() <= 1.0E-10 ? true : sweptCollide(entity, from, to).equals(delta);
      } else {
         return false;
      }
   }

   public record Step(Vec3 position, boolean onGround) {
      public Step(Vec3 position, boolean onGround) {
         if (position == null) {
            position = Vec3.ZERO;
         }

         this.position = position;
         this.onGround = onGround;
      }
   }
}
