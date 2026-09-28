package riptide.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionPieces.WoodlandMansionPiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.util.worldgen.mc26_2.RiptideSyntheticLevel;

@Mixin({WoodlandMansionPiece.class})
public abstract class RiptideOreSimWoodlandMansionPieceMixin {
   @Inject(
      method = {"handleDataMarker"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$skipSyntheticMobMarker(
      String marker, BlockPos position, ServerLevelAccessor level, RandomSource random, BoundingBox chunkBox, CallbackInfo ci
   ) {
      if (level instanceof RiptideSyntheticLevel synthetic) {
         if ("Mage".equals(marker) || "Warrior".equals(marker) || "Group of Allays".equals(marker)) {
            if ("Group of Allays".equals(marker)) {
               synthetic.getRandom().nextInt(3);
            }

            level.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
            ci.cancel();
         }
      }
   }
}
