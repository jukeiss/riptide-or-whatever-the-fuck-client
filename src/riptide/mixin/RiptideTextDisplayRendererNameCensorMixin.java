package riptide.mixin;

import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import riptide.modules.NameCensorModule;

@Mixin(
   targets = {"net.minecraft.client.renderer.entity.DisplayRenderer$TextDisplayRenderer"}
)
public class RiptideTextDisplayRendererNameCensorMixin {
   @ModifyVariable(
      method = {"splitLines"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0,
      require = 0
   )
   private Component riptide$censorTextDisplay(Component component) {
      return NameCensorModule.censorServerComponent(component);
   }
}
