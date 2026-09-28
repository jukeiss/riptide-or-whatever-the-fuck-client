package riptide.mixin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.util.RiptidePayloadSupport;

@Mixin({DiscardedPayload.class})
public abstract class RiptideUnknownCustomPayloadMixin {
   @Inject(
      method = {"codec"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private static <T extends FriendlyByteBuf> void riptide$wrapUnknownCodec(
      Identifier id, int maxBytes, CallbackInfoReturnable<StreamCodec<T, DiscardedPayload>> cir
   ) {
      StreamCodec<T, DiscardedPayload> original = (StreamCodec<T, DiscardedPayload>)cir.getReturnValue();
      if (original != null) {
         StreamCodec<T, DiscardedPayload> wrapped = (StreamCodec<T, DiscardedPayload>)RiptidePayloadSupport.wrapUnknownCodec(id, maxBytes, original);
         cir.setReturnValue(wrapped);
      }
   }
}
