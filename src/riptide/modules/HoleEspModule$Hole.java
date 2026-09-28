package riptide.modules;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public record HoleEspModule$Hole(BlockPos pos, HoleEspModule$Kind kind, double distanceSq) {
   public AABB box() {
      return new AABB(this.pos.getX(), this.pos.getY(), this.pos.getZ(), this.pos.getX() + 1.0, this.pos.getY() + 2.0, this.pos.getZ() + 1.0);
   }
}
