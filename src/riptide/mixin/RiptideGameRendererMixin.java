package riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.ModuleRegistry;
import riptide.util.RiptideRuntimeActivity;

@Mixin({GameRenderer.class})
public abstract class RiptideGameRendererMixin {
   @ModifyExpressionValue(
      method = {"update", "extract", "render"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/client/Minecraft;level:Lnet/minecraft/client/multiplayer/ClientLevel;"
      )}
   )
   private ClientLevel riptide$treatLevelAsUnavailableWithoutPlayer(ClientLevel level) {
      Minecraft minecraft = Minecraft.getInstance();
      return minecraft.player == null ? null : level;
   }

   @Inject(
      method = {"renderLevel"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$onRenderLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
      if (Minecraft.getInstance().player == null) {
         ci.cancel();
      } else if (RiptideRuntimeActivity.has(2L)) {
         ModuleRegistry.onRenderLevel(deltaTracker.getGameTimeDeltaPartialTick(false));
      }
   }
}
