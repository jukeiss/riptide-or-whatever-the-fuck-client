package riptide.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TemplateFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TemplateFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import riptide.util.worldgen.mc26_2.RiptideSyntheticFeatureBridge;
import riptide.util.worldgen.mc26_2.RiptideSyntheticLevel;

@Mixin({TemplateFeature.class})
public abstract class RiptideOreSimTemplateFeatureMixin {
   @WrapMethod(
      method = {"place"}
   )
   private boolean riptide$placeInSyntheticWorld(FeaturePlaceContext<TemplateFeatureConfiguration> context, Operation<Boolean> original) {
      return context.level() instanceof RiptideSyntheticLevel synthetic
         ? RiptideSyntheticFeatureBridge.placeTemplate(context, synthetic)
         : (Boolean)original.call(new Object[]{context});
   }
}
