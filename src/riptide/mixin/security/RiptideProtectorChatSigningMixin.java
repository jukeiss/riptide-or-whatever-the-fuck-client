package riptide.mixin.security;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.chat.SignedMessageChain.Encoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.security.RiptideProtector;

@Mixin({ClientPacketListener.class})
public abstract class RiptideProtectorChatSigningMixin {
   @WrapOperation(
      method = {"sendChat"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/chat/SignedMessageChain$Encoder;pack(Lnet/minecraft/network/chat/SignedMessageBody;)Lnet/minecraft/network/chat/MessageSignature;"
      )}
   )
   private MessageSignature riptide$skipSigning(Encoder encoder, SignedMessageBody body, Operation<MessageSignature> original) {
      return RiptideProtector.shouldSkipChatSigning() ? null : (MessageSignature)original.call(new Object[]{encoder, body});
   }
}
