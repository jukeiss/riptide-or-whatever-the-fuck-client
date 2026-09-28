package riptide.mixin;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.ducks.RiptideDisconnectedScreenAccess;
import riptide.gui.screen.RiptideStyledButton;
import riptide.gui.vanillaui.components.Button;
import riptide.modules.PackAutoReconnectState;
import riptide.modules.PackHideState;

@Mixin({DisconnectedScreen.class})
public abstract class RiptideDisconnectedScreenMixin extends Screen implements RiptideDisconnectedScreenAccess {
   @Shadow
   @Final
   private Screen parent;

   protected RiptideDisconnectedScreenMixin(Component title) {
      super(title);
   }

   @Override
   public Screen riptide$parent() {
      return this.parent;
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void riptide$addReconnectButton(CallbackInfo ci) {
      if (!PackHideState.isActive()) {
         if (PackAutoReconnectState.canShowToggle()) {
            int w = 148;
            this.addRenderableWidget(
               new RiptideStyledButton(
                  this.width / 2 - w / 2,
                  this.height - 38,
                  w,
                  20,
                  Component.literal("Auto Reconnect"),
                  Button.Tone.PRIMARY,
                  PackAutoReconnectState::toggleLabel,
                  button -> PackAutoReconnectState.toggle()
               )
            );
         }
      }
   }
}
