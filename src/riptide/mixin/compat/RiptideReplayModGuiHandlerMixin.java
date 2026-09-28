package riptide.mixin.compat;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.PackHideState;

@Pseudo
@Mixin(
   targets = {"com.replaymod.recording.handler.GuiHandler"},
   remap = false
)
public abstract class RiptideReplayModGuiHandlerMixin {
   @Inject(
      method = {"onGuiInit"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false
   )
   private void riptide$skipBuiltInRecordCheckbox(Screen screen, CallbackInfo ci) {
      if (screen != null) {
         String className = screen.getClass().getName();
         if (!className.endsWith(".JoinMultiplayerScreen") && !className.endsWith(".MultiplayerScreen") && !className.endsWith(".SelectWorldScreen")) {
            if (PackHideState.isActive()) {
               ci.cancel();
            }
         } else {
            ci.cancel();
         }
      }
   }
}
