package riptide.mixin;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.Utf8String;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ByteBufCodecs.class})
public interface RiptideBookTitleCodecMixin {
   @Inject(
      method = {"stringUtf8(I)Lnet/minecraft/network/codec/StreamCodec;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void riptide$allowLongBookTitle(int maxStringLength, CallbackInfoReturnable<StreamCodec<ByteBuf, String>> cir) {
      if (maxStringLength == 32) {
         cir.setReturnValue(new StreamCodec<ByteBuf, String>() {
            public String decode(ByteBuf input) {
               return Utf8String.read(input, 32767);
            }

            public void encode(ByteBuf output, String value) {
               Utf8String.write(output, value, 32767);
            }
         });
      }
   }
}
