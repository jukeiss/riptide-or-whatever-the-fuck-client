package riptide.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.util.worldgen.mc26_2.RiptideSyntheticLevel;

@Mixin(
   targets = {"net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces$MineShaftCorridor"}
)
public abstract class RiptideOreSimMineshaftPieceMixin {
   @Inject(
      method = {"createChest"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/WorldGenLevel;getLevel()Lnet/minecraft/server/level/ServerLevel;"
      )},
      cancellable = true
   )
   private void riptide$skipSyntheticChestEntity(
      WorldGenLevel level,
      BoundingBox chunkBox,
      RandomSource random,
      int x,
      int y,
      int z,
      ResourceKey<LootTable> lootTable,
      CallbackInfoReturnable<Boolean> cir
   ) {
      if (level instanceof RiptideSyntheticLevel) {
         random.nextLong();
         cir.setReturnValue(true);
      }
   }
}
