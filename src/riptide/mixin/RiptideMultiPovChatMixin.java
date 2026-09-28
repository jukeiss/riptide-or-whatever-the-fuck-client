package riptide.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ChatComponent.State;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.util.multi.MultiPovChat;

@Mixin({ChatComponent.class})
public class RiptideMultiPovChatMixin {
   @Unique
   private State riptide$povMessageState;
   @Unique
   private State riptide$povDeleteState;
   @Unique
   private State riptide$povClearState;

   @Inject(
      method = {"addMessage"},
      at = {@At("HEAD")}
   )
   private void riptide$beforeMainChatMessage(Component content, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
      this.riptide$povMessageState = MultiPovChat.beginRenderedClientMutation((ChatComponent)this);
   }

   @Inject(
      method = {"addMessage"},
      at = {@At("RETURN")}
   )
   private void riptide$afterMainChatMessage(Component content, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
      State state = this.riptide$povMessageState;
      this.riptide$povMessageState = null;
      MultiPovChat.endRenderedClientMutation((ChatComponent)this, state);
   }

   @Inject(
      method = {"deleteMessage"},
      at = {@At("HEAD")}
   )
   private void riptide$beforeMainChatDelete(MessageSignature signature, CallbackInfo ci) {
      this.riptide$povDeleteState = MultiPovChat.beginRenderedClientMutation((ChatComponent)this);
   }

   @Inject(
      method = {"deleteMessage"},
      at = {@At("RETURN")}
   )
   private void riptide$afterMainChatDelete(MessageSignature signature, CallbackInfo ci) {
      State state = this.riptide$povDeleteState;
      this.riptide$povDeleteState = null;
      MultiPovChat.endRenderedClientMutation((ChatComponent)this, state);
   }

   @Inject(
      method = {"clearMessages"},
      at = {@At("HEAD")}
   )
   private void riptide$beforeMainChatClear(boolean clearHistory, CallbackInfo ci) {
      this.riptide$povClearState = MultiPovChat.beginRenderedClientMutation((ChatComponent)this);
   }

   @Inject(
      method = {"clearMessages"},
      at = {@At("RETURN")}
   )
   private void riptide$afterMainChatClear(boolean clearHistory, CallbackInfo ci) {
      State state = this.riptide$povClearState;
      this.riptide$povClearState = null;
      MultiPovChat.endRenderedClientMutation((ChatComponent)this, state);
   }
}
