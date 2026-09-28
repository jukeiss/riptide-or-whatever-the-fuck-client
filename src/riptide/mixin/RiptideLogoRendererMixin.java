package riptide.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.util.RiptideMenuPrefs;
import riptide.util.RiptideTheme;
import riptide.util.RiptideThemeTextures;

@Mixin({LogoRenderer.class})
public class RiptideLogoRendererMixin {
   @Unique
   private static final Identifier PACKUTIL_LOGO = Identifier.fromNamespaceAndPath("riptide", "textures/gui/title/riptide_client_logo.png");
   @Unique
   private static final int PACKUTIL_LOGO_TEXTURE_WIDTH = 516;
   @Unique
   private static final int PACKUTIL_LOGO_TEXTURE_HEIGHT = 144;
   @Unique
   private static final int PACKUTIL_LOGO_MAX_WIDTH = 320;
   @Unique
   private static final int PACKUTIL_LOGO_MAX_HEIGHT = 72;
   @Unique
   private static final int PACKUTIL_LOGO_Y_OFFSET = 0;

   @Inject(
      method = {"extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IFI)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$renderHighResolutionLogo(GuiGraphicsExtractor graphics, int width, float alpha, int heightOffset, CallbackInfo ci) {
      if (!RiptideMenuPrefs.vanillaMenuVisuals()) {
         int maxWidth = Math.min(320, Math.max(180, width - 40));
         float scale = Math.min(maxWidth / 516.0F, 0.5F);
         int drawWidth = Math.round(516.0F * scale);
         int drawHeight = Math.round(144.0F * scale);
         int logoX = width / 2 - drawWidth / 2;
         int logoY = heightOffset + 0;
         graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            RiptideThemeTextures.recolored(PACKUTIL_LOGO, RiptideTheme.Channel.ACCENT),
            logoX,
            logoY,
            0.0F,
            0.0F,
            drawWidth,
            drawHeight,
            516,
            144,
            516,
            144,
            ARGB.white(alpha)
         );
         ci.cancel();
      }
   }
}
