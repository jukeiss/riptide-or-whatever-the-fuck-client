package riptide.mixin;

import net.minecraft.client.renderer.Panorama;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import riptide.util.RiptideMenuPrefs;
import riptide.util.RiptideThemeTextures;

@Mixin({Panorama.class})
public abstract class RiptidePanoramaOverlayMixin {
   @Unique
   private static final Identifier RIPTIDE_PANORAMA_OVERLAY = Identifier.fromNamespaceAndPath("riptide", "textures/gui/title/background/panorama_overlay.png");

   @ModifyArg(
      method = {"extractRenderState"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIII)V"
      ),
      index = 1
   )
   private Identifier riptide$swapOverlay(Identifier original) {
      return RiptideMenuPrefs.vanillaMenuVisuals() ? original : RiptideThemeTextures.panoramaOverlay(RIPTIDE_PANORAMA_OVERLAY);
   }
}
