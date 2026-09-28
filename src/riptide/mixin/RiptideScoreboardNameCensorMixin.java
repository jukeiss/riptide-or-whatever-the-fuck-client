package riptide.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import riptide.modules.NameCensorModule;

@Mixin({Hud.class})
public abstract class RiptideScoreboardNameCensorMixin {
   @ModifyArg(
      method = {"displayScoreboardSidebar"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"
      )},
      index = 1
   )
   private Component riptide$censorSidebarRow(Component var1) {
      try {
         return var1 != null && NameCensorModule.isActive() ? NameCensorModule.censorComponent(var1) : var1;
      } catch (Throwable var3) {
         return var1;
      }
   }
}
