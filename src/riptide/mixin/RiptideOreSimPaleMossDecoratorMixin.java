package riptide.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.feature.treedecorators.PaleMossDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator.Context;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import riptide.util.worldgen.mc26_2.RiptideSyntheticFeatureBridge;
import riptide.util.worldgen.mc26_2.RiptideSyntheticLevel;

@Mixin({PaleMossDecorator.class})
public abstract class RiptideOreSimPaleMossDecoratorMixin {
   @Shadow
   @Final
   private float leavesProbability;
   @Shadow
   @Final
   private float trunkProbability;
   @Shadow
   @Final
   private float groundProbability;

   @WrapMethod(
      method = {"place"}
   )
   private void riptide$placeInSyntheticWorld(Context context, Operation<Void> original) {
      if (context.level() instanceof RiptideSyntheticLevel synthetic) {
         RiptideSyntheticFeatureBridge.placePaleMoss(context, synthetic, this.leavesProbability, this.trunkProbability, this.groundProbability);
      } else {
         original.call(new Object[]{context});
      }
   }
}
