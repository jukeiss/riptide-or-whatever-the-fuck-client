package riptide.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.EndCityPieces.EndCityPiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.util.worldgen.mc26_2.RiptideSyntheticLevel;

@Mixin({EndCityPiece.class})
public abstract class RiptideOreSimEndCityPieceMixin {
   @Inject(
      method = {"handleDataMarker"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$skipSyntheticEntityMarker(
      String marker, BlockPos position, ServerLevelAccessor level, RandomSource random, BoundingBox chunkBox, CallbackInfo ci
   ) {
      if (level instanceof RiptideSyntheticLevel && (marker.startsWith("Sentry") || marker.startsWith("Elytra"))) {
         ci.cancel();
      }
   }
}
