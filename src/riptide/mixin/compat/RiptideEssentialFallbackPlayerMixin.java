package riptide.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(
   targets = {"gg.essential.gui.common.UI3DPlayer$FallbackPlayer"},
   remap = false
)
public abstract class RiptideEssentialFallbackPlayerMixin {
   @WrapMethod(
      method = {"render"}
   )
   private void riptide$skipBrokenFallbackFrame(@Coerce Object matrix, @Coerce Object commandQueue, @Coerce Object vertexConsumers, Operation<Void> original) {
      try {
         original.call(new Object[]{matrix, commandQueue, vertexConsumers});
      } catch (NullPointerException var7) {
         String message = var7.getMessage();
         if (message == null || !message.contains("\"o\" is null")) {
            throw var7;
         }
      }
   }
}
