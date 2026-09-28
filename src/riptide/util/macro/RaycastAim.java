package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public interface RaycastAim {
   boolean isRaycast();

   void setRaycast(boolean var1);

   RaycastAim.Target raycastTarget(Minecraft var1);

   public record Target(BlockPos block, Entity entity, Vec3 point) {
      public static RaycastAim.Target ofBlock(BlockPos pos) {
         return pos == null ? null : new RaycastAim.Target(pos, null, null);
      }

      public static RaycastAim.Target ofEntity(Entity entity) {
         return entity == null ? null : new RaycastAim.Target(null, entity, null);
      }

      public static RaycastAim.Target ofPoint(Vec3 point) {
         return point == null ? null : new RaycastAim.Target(null, null, point);
      }
   }
}
