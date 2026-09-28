package riptide.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.util.RiptideSpotifyBar;

@Mixin({Screen.class})
public abstract class RiptideSpotifyBarMixin {
   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void riptide$renderSpotifyBar(GuiGraphicsExtractor var1, int var2, int var3, float var4, CallbackInfo var5) {
      RiptideSpotifyBar.render(var1, (Screen)this);
   }
}
