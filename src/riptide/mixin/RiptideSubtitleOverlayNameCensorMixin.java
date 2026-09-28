package riptide.mixin;

import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import riptide.modules.NameCensorModule;

@Mixin({SubtitleOverlay.class})
public class RiptideSubtitleOverlayNameCensorMixin {
   @ModifyArg(
      method = {"onPlaySound"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/components/SubtitleOverlay$Subtitle;<init>(Lnet/minecraft/network/chat/Component;FLnet/minecraft/world/phys/Vec3;)V"
      ),
      index = 0,
      require = 0
   )
   private Component riptide$censorSubtitle(Component component) {
      return NameCensorModule.censorComponent(component);
   }
}
