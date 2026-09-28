package riptide.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.NameCensorModule;

@Mixin({PlayerTabOverlay.class})
public class RiptidePlayerTabOverlayMixin {
   @Inject(
      method = {"getNameForDisplay"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void riptide$censorTabName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
      cir.setReturnValue(NameCensorModule.censorServerComponent((Component)cir.getReturnValue()));
   }

   @ModifyArg(
      method = {"extractRenderState"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"
      ),
      index = 0,
      require = 0
   )
   private FormattedText riptide$censorTabHeaderFooter(FormattedText text) {
      return (FormattedText)(text instanceof Component component ? NameCensorModule.censorServerComponent(component) : text);
   }
}
