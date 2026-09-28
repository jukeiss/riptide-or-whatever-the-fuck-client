package riptide.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SplashRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.PackHideState;
import riptide.util.RiptideMenuPrefs;
import riptide.util.RiptideVanillaSplash;

@Mixin(
   value = {SplashRenderer.class},
   priority = 2000
)
public class RiptideSplashRendererMixin {
   @Inject(
      method = {"extractRenderState"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$hideSplashText(GuiGraphicsExtractor graphics, int screenWidth, Font font, float alpha, CallbackInfo ci) {
      if (PackHideState.isActive()) {
         if (!(Boolean)Minecraft.getInstance().options.hideSplashTexts().get()) {
            RiptideVanillaSplash.renderPanicSplash(Minecraft.getInstance(), graphics, screenWidth, font, alpha);
         }

         ci.cancel();
      } else if (!RiptideMenuPrefs.vanillaMenuVisuals()) {
         ci.cancel();
      }
   }
}
