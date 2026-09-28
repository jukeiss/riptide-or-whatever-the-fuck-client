package riptide.mixin.security;

import com.mojang.authlib.minecraft.TelemetrySession;
import com.mojang.authlib.yggdrasil.YggdrasilUserApiService;
import java.util.concurrent.Executor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.security.RiptideProtector;

@Mixin(
   value = {YggdrasilUserApiService.class},
   remap = false
)
public class RiptideProtectorYggdrasilMixin {
   @Inject(
      method = {"newTelemetrySession"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$disableTelemetrySession(Executor executor, CallbackInfoReturnable<TelemetrySession> info) {
      if (RiptideProtector.shouldDisableTelemetry()) {
         info.setReturnValue(TelemetrySession.DISABLED);
      }
   }
}
