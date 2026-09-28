package riptide.mixin;

import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.gui.screen.RiptideStyledButton;
import riptide.gui.vanillaui.components.Button;
import riptide.modules.RiptideModule;
import riptide.util.RiptideClientWake;

@Mixin({InBedChatScreen.class})
public abstract class RiptideSleepingChatScreenMixin extends Screen {
   protected RiptideSleepingChatScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"init()V"},
      at = {@At("TAIL")}
   )
   private void riptide$init(CallbackInfo ci) {
      RiptideModule module = RiptideModule.get();
      if (module != null && module.isActive()) {
         this.addRenderableWidget(
            new RiptideStyledButton(5, 5, 140, 20, Component.literal("Client wake up"), Button.Tone.PRIMARY, button -> RiptideClientWake.wake())
         );
      }
   }
}
