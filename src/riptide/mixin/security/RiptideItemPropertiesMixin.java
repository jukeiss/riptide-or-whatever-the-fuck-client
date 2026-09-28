package riptide.mixin.security;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item.Properties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.security.RiptideRegistryComponentCompat;

@Mixin({Properties.class})
public abstract class RiptideItemPropertiesMixin {
   @WrapOperation(
      method = {"lambda$delayedHolderComponent$0"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/core/HolderLookup$Provider;getOrThrow(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference;"
      )},
      require = 0
   )
   private static <T> Reference<T> riptide$skipMissingTrimMaterialHolder(Provider context, ResourceKey<T> valueKey, Operation<Reference<T>> original) {
      try {
         return (Reference<T>)original.call(new Object[]{context, valueKey});
      } catch (RuntimeException var4) {
         if (RiptideRegistryComponentCompat.shouldSkipMissingDelayedHolder(valueKey, var4)) {
            RiptideRegistryComponentCompat.reportSkippedMissingTrimMaterial(valueKey);
            return null;
         } else if (RiptideRegistryComponentCompat.shouldSkipMissingComponentData(var4)) {
            RiptideRegistryComponentCompat.reportSkippedMissingComponent(var4);
            return null;
         } else {
            throw var4;
         }
      }
   }
}
