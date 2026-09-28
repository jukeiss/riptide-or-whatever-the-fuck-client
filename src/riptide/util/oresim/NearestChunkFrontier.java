package riptide.util.oresim;

import java.util.function.LongPredicate;
import net.minecraft.world.level.ChunkPos;

final class NearestChunkFrontier {
   private NearestChunkFrontier() {
   }

   static NearestChunkFrontier.Chunk nearestMissing(double playerX, double playerZ, int centerChunkX, int centerChunkZ, int radius, LongPredicate completed) {
      NearestChunkFrontier.Chunk nearest = null;

      for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
         for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
            if (!completed.test(ChunkPos.pack(chunkX, chunkZ))) {
               double distance = distanceSquared(playerX, playerZ, chunkX, chunkZ);
               if (nearest == null || compare(distance, chunkX, chunkZ, nearest.distanceSquared(), nearest.x(), nearest.z()) < 0) {
                  nearest = new NearestChunkFrontier.Chunk(chunkX, chunkZ, distance);
               }
            }
         }
      }

      return nearest;
   }

   static boolean canPublish(double playerX, double playerZ, int chunkX, int chunkZ, NearestChunkFrontier.Chunk nearestMissing) {
      return nearestMissing == null
         ? true
         : compare(distanceSquared(playerX, playerZ, chunkX, chunkZ), chunkX, chunkZ, nearestMissing.distanceSquared(), nearestMissing.x(), nearestMissing.z())
            <= 0;
   }

   static double distanceSquared(double x, double z, int chunkX, int chunkZ) {
      double minX = chunkX * 16.0 + 0.5;
      double minZ = chunkZ * 16.0 + 0.5;
      double maxX = (chunkX + 1) * 16.0 - 0.5;
      double maxZ = (chunkZ + 1) * 16.0 - 0.5;
      double dx = x < minX ? minX - x : (x > maxX ? x - maxX : 0.0);
      double dz = z < minZ ? minZ - z : (z > maxZ ? z - maxZ : 0.0);
      return dx * dx + dz * dz;
   }

   static int compare(double leftDistance, int leftX, int leftZ, double rightDistance, int rightX, int rightZ) {
      int distanceOrder = Double.compare(leftDistance, rightDistance);
      if (distanceOrder != 0) {
         return distanceOrder;
      } else {
         int xOrder = Integer.compare(leftX, rightX);
         return xOrder != 0 ? xOrder : Integer.compare(leftZ, rightZ);
      }
   }

   record Chunk(int x, int z, double distanceSquared) {
   }
}
