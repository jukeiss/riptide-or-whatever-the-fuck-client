package riptide.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import riptide.modules.NameCensorModule;

@Mixin({Hud.class})
public class RiptideGuiNameCensorMixin {
   @ModifyVariable(
      method = {"setTitle"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private Component riptide$censorTitle(Component component) {
      return NameCensorModule.censorServerComponent(component);
   }

   @ModifyVariable(
      method = {"setSubtitle"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private Component riptide$censorSubtitle(Component component) {
      return NameCensorModule.censorServerComponent(component);
   }

   @ModifyVariable(
      method = {"setOverlayMessage"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private Component riptide$censorOverlayMessage(Component component) {
      return NameCensorModule.censorServerComponent(component);
   }
}
