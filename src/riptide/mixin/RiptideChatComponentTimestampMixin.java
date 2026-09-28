package riptide.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import riptide.modules.ChatTimestampsModule;

@Mixin({ChatComponent.class})
public class RiptideChatComponentTimestampMixin {
   @ModifyVariable(
      method = {"addServerSystemMessage"},
      at = {@At("HEAD")},
      argsOnly = true,
      ordinal = 0
   )
   private Component riptide$stampServerSystemChat(Component var1) {
      return ChatTimestampsModule.decorate(var1);
   }

   @ModifyVariable(
      method = {"addPlayerMessage"},
      at = {@At("HEAD")},
      argsOnly = true,
      ordinal = 0
   )
   private Component riptide$stampPlayerChat(Component var1) {
      return ChatTimestampsModule.decorate(var1);
   }
}
