package riptide.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.FossilFeature;
import net.minecraft.world.level.levelgen.feature.FossilFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import riptide.util.worldgen.mc26_2.RiptideSyntheticFeatureBridge;
import riptide.util.worldgen.mc26_2.RiptideSyntheticLevel;

@Mixin({FossilFeature.class})
public abstract class RiptideOreSimFossilFeatureMixin {
   @WrapMethod(
      method = {"place"}
   )
   private boolean riptide$placeInSyntheticWorld(FeaturePlaceContext<FossilFeatureConfiguration> context, Operation<Boolean> original) {
      return context.level() instanceof RiptideSyntheticLevel synthetic
         ? RiptideSyntheticFeatureBridge.placeFossil(context, synthetic)
         : (Boolean)original.call(new Object[]{context});
   }
}
