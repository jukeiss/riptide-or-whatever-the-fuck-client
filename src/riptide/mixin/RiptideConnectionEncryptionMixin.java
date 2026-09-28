package riptide.mixin;

import io.netty.channel.Channel;
import javax.crypto.Cipher;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.util.RiptideCiphertextTap;
import riptide.util.RiptideNetworkCaptureState;
import riptide.util.RiptidePacketCapture;
import riptide.util.multi.MultiConnectionContext;

@Mixin({Connection.class})
public abstract class RiptideConnectionEncryptionMixin {
   @Shadow
   private Channel channel;

   @Inject(
      method = {"setEncryptionKey"},
      at = {@At("TAIL")}
   )
   private void riptide$observeEncryptionBoundary(Cipher decryptCipher, Cipher encryptCipher, CallbackInfo ci) {
      if (!MultiConnectionContext.isMulti((Connection)this)) {
         if (this.channel != null) {
            if (RiptideNetworkCaptureState.capturesPlaintext()) {
               RiptidePacketCapture.markEncryptionEnabled(this.channel);
            }

            try {
               if (this.channel.pipeline().get("riptide_ciphertext_in") == null && this.channel.pipeline().get("decrypt") != null) {
                  this.channel.pipeline().addBefore("decrypt", "riptide_ciphertext_in", new RiptideCiphertextTap("S2C"));
               }

               if (this.channel.pipeline().get("riptide_ciphertext_out") == null && this.channel.pipeline().get("encrypt") != null) {
                  this.channel.pipeline().addBefore("encrypt", "riptide_ciphertext_out", new RiptideCiphertextTap("C2S"));
               }
            } catch (Throwable var5) {
            }
         }
      }
   }
}
