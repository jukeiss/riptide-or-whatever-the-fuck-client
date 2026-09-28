package riptide.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class RiptideAndromeda {
   private RiptideAndromeda() {
   }

   public static List<BlockPos> cells(BlockPos var0, BlockPos var1, int var2) {
      ArrayList var3 = new ArrayList(8);
      if (var0 == null) {
         return var3;
      } else {
         addPair(var3, var0);
         if (var1 != null && (var1.getX() != 0 || var1.getZ() != 0)) {
            boolean var4 = var1.getX() != 0 && var1.getZ() != 0;

            for (int var5 = 1; var5 <= Math.max(0, var2); var5++) {
               BlockPos var6 = var0.offset(var1.getX() * var5, 0, var1.getZ() * var5);
               if (var4) {
                  BlockPos var7 = var0.offset(var1.getX() * var5, 0, var1.getZ() * (var5 - 1));
                  BlockPos var8 = var0.offset(var1.getX() * (var5 - 1), 0, var1.getZ() * var5);
                  if (!solid(var7)) {
                     addPair(var3, var7);
                  }

                  if (!solid(var8)) {
                     addPair(var3, var8);
                  }
               }

               addPair(var3, var6);
            }

            return var3;
         } else {
            return var3;
         }
      }
   }

   private static void addPair(List<BlockPos> var0, BlockPos var1) {
      var0.add(var1);
      var0.add(var1.above());
   }

   private static boolean solid(BlockPos var0) {
      Minecraft var1 = Minecraft.getInstance();
      return var1.level != null && !var1.level.isOutsideBuildHeight(var0) ? !var1.level.getBlockState(var0).canBeReplaced() : true;
   }
}
