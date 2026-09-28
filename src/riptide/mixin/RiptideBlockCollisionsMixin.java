package riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import riptide.modules.AirJumpModule;
import riptide.modules.PhaseModule;

@Mixin({BlockCollisions.class})
public abstract class RiptideBlockCollisionsMixin {
   @Shadow
   @Final
   private MutableBlockPos pos;

   @ModifyExpressionValue(
      method = {"computeNext"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/phys/shapes/CollisionContext;getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
      )}
   )
   private VoxelShape riptide$ghostBlockShape(VoxelShape original, @Local(ordinal = 0) BlockState state) {
      return PhaseModule.blockShape(AirJumpModule.ghostBlockShape(original, this.pos), state, this.pos);
   }
}
