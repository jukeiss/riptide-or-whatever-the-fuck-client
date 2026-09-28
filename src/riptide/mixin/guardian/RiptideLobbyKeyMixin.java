package riptide.mixin.guardian;

import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.util.mm.MatchmakingManager;
import riptide.util.mm.guardian.impl.LobbyUnwrap;

@Mixin({MatchmakingManager.class})
public class RiptideLobbyKeyMixin {
   @ModifyReturnValue(
      method = {"resolveLobbyKey"},
      at = {@At("RETURN")}
   )
   private static byte[] riptide$unwrap(byte[] original, JsonObject b) {
      return LobbyUnwrap.apply(original, b);
   }
}
