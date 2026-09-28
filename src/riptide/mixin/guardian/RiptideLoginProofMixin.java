package riptide.mixin.guardian;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import riptide.util.RiptideDiscordLogin;
import riptide.util.mm.guardian.impl.LoginProof;

@Mixin({RiptideDiscordLogin.class})
public class RiptideLoginProofMixin {
   @ModifyArg(
      method = {"exchangeCode", "freshJwt"},
      at = @At(
         value = "INVOKE",
         target = "Lriptide/util/RiptideHttp;postForm(Ljava/lang/String;Ljava/lang/String;)Lcom/google/gson/JsonObject;"
      ),
      index = 1
   )
   private static String riptide$appendProof(String body) {
      return body + LoginProof.build();
   }
}
