package riptide.mixin.security;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponentInitializers.SingleComponentInitializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.security.RiptideRegistryComponentCompat;

@Mixin({SingleComponentInitializer.class})
public interface RiptideDataComponentInitializerMixin {
   @WrapOperation(
      method = {"lambda$asInitializer$0"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/core/component/DataComponentInitializers$SingleComponentInitializer;create(Lnet/minecraft/core/HolderLookup$Provider;)Ljava/lang/Object;"
      )},
      require = 0
   )
   private static Object riptide$skipMissingRemoteComponent(SingleComponentInitializer<?> self, Provider context, Operation<Object> original) {
      try {
         return original.call(new Object[]{self, context});
      } catch (RuntimeException var4) {
         if (!RiptideRegistryComponentCompat.shouldSkipMissingComponentData(var4)) {
            throw var4;
         } else {
            RiptideRegistryComponentCompat.reportSkippedMissingComponent(var4);
            return null;
         }
      }
   }
}
