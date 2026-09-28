package riptide.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.NoRenderState;

@Mixin({Hud.class})
public abstract class RiptideNoRenderHudMixin {
   @Inject(
      method = {"extractPortalOverlay"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noPortalOverlay(GuiGraphicsExtractor ctx, float alpha, CallbackInfo ci) {
      if (NoRenderState.noPortalOverlay()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractSpyglassOverlay"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noSpyglassOverlay(GuiGraphicsExtractor ctx, float scale, CallbackInfo ci) {
      if (NoRenderState.noSpyglassOverlay()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractConfusionOverlay"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noNausea(GuiGraphicsExtractor ctx, float amount, CallbackInfo ci) {
      if (NoRenderState.noNausea()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractVignette"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noVignette(GuiGraphicsExtractor ctx, Entity entity, CallbackInfo ci) {
      if (NoRenderState.noVignette()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractCrosshair"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noCrosshair(GuiGraphicsExtractor ctx, DeltaTracker delta, CallbackInfo ci) {
      if (NoRenderState.noCrosshair()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractTitle"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noTitle(GuiGraphicsExtractor ctx, DeltaTracker delta, CallbackInfo ci) {
      if (NoRenderState.noTitle()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractSelectedItemName"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noHeldItemName(GuiGraphicsExtractor ctx, CallbackInfo ci) {
      if (NoRenderState.noHeldItemName()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractEffects"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noPotionIcons(GuiGraphicsExtractor ctx, DeltaTracker delta, CallbackInfo ci) {
      if (NoRenderState.noPotionIcons()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractScoreboardSidebar"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noScoreboardSidebar(GuiGraphicsExtractor ctx, DeltaTracker delta, CallbackInfo ci) {
      if (NoRenderState.noScoreboard()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"displayScoreboardSidebar"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noScoreboardDisplay(GuiGraphicsExtractor ctx, Objective objective, CallbackInfo ci) {
      if (NoRenderState.noScoreboard()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractTextureOverlay"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noTextureOverlay(GuiGraphicsExtractor ctx, Identifier texture, float alpha, CallbackInfo ci) {
      if (texture != null) {
         String path = texture.getPath();
         if (NoRenderState.noPumpkinOverlay() && path.contains("pumpkin")) {
            ci.cancel();
         } else if (NoRenderState.noPowderedSnowOverlay() && (path.contains("powder") || path.contains("snow"))) {
            ci.cancel();
         }
      }
   }
}
