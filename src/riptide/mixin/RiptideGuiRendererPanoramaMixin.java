package riptide.mixin;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.util.RiptideMenuPrefs;
import riptide.util.RiptideThemeTextures;

@Mixin({GuiRenderer.class})
public abstract class RiptideGuiRendererPanoramaMixin {
   @Unique
   private final CubeMap riptide$customCubeMap = new CubeMap(Identifier.fromNamespaceAndPath("riptide", "textures/gui/title/background/panorama"));

   @Inject(
      method = {"registerPanoramaTextures"},
      at = {@At("TAIL")}
   )
   private void riptide$registerCustomPanorama(TextureManager textureManager, CallbackInfo ci) {
      RiptideThemeTextures.registerPanorama(textureManager);
   }

   @Redirect(
      method = {"render"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/CubeMap;render(FF)V"
      )
   )
   private void riptide$renderPanorama(CubeMap vanillaCubeMap, float rotX, float rotY) {
      boolean custom = !RiptideMenuPrefs.vanillaMenuVisuals() && RiptideThemeTextures.isPanoramaAvailable();
      CubeMap target = custom ? this.riptide$customCubeMap : vanillaCubeMap;
      target.render(rotX, rotY);
   }

   @Inject(
      method = {"close"},
      at = {@At("TAIL")}
   )
   private void riptide$closeCustomPanorama(CallbackInfo ci) {
      this.riptide$customCubeMap.close();
   }
}
