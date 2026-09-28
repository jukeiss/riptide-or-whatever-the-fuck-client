package riptide.gui;

import com.mojang.blaze3d.platform.Window;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Vector4f;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.modules.PackHideState;
import riptide.util.RiptideColors;
import riptide.util.RiptideLiteVariant;
import riptide.util.RiptideTheme;
import riptide.util.RiptideThemeTextures;

public class RiptideLoadingOverlay extends LoadingOverlay {
   private static final Identifier CUSTOM_LOGO = Identifier.fromNamespaceAndPath("riptide", "textures/gui/title/loading_logo.png");
   private static final int LOGO_WIDTH = 2106;
   private static final int LOGO_HEIGHT = 1297;
   private static final int BG_COLOR = RiptideColors.loadingBg();
   private static final int BAR_R = 236;
   private static final int BAR_G = 32;
   private static final int BAR_B = 39;
   private static final long FADE_OUT_TIME = 1000L;
   private static final long FADE_IN_TIME = 500L;
   private final Minecraft riptide$minecraft;
   private final ReloadInstance riptide$reload;
   private final Consumer<Optional<Throwable>> riptide$onFinish;
   private final boolean riptide$fadeIn;
   private float riptide$currentProgress;
   private long riptide$fadeOutStart = -1L;
   private long riptide$fadeInStart = -1L;

   public static LoadingOverlay create(Minecraft minecraft, ReloadInstance reload, Consumer<Optional<Throwable>> onFinish, boolean fadeIn) {
      return (LoadingOverlay)(!PackHideState.isActive() && !RiptideLiteVariant.enabled()
         ? new RiptideLoadingOverlay(minecraft, reload, onFinish, fadeIn)
         : new LoadingOverlay(minecraft, reload, onFinish, fadeIn));
   }

   public RiptideLoadingOverlay(Minecraft minecraft, ReloadInstance reload, Consumer<Optional<Throwable>> onFinish, boolean fadeIn) {
      super(minecraft, reload, onFinish, fadeIn);
      this.riptide$minecraft = minecraft;
      this.riptide$reload = reload;
      this.riptide$onFinish = onFinish;
      this.riptide$fadeIn = fadeIn;
   }

   private boolean riptide$handOffToVanillaWhileHidden() {
      if (!PackHideState.isActive()) {
         return false;
      } else {
         if (this.riptide$fadeOutStart == -1L) {
            this.riptide$minecraft.gui.setOverlay(new LoadingOverlay(this.riptide$minecraft, this.riptide$reload, this.riptide$onFinish, false));
         } else {
            this.riptide$minecraft.gui.setOverlay(null);
         }

         return true;
      }
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      if (!this.riptide$handOffToVanillaWhileHidden()) {
         int width = graphics.guiWidth();
         int height = graphics.guiHeight();
         long now = Util.getMillis();
         if (this.riptide$fadeIn && this.riptide$fadeInStart == -1L) {
            this.riptide$fadeInStart = now;
         }

         float fadeOutAnim = this.riptide$fadeOutStart > -1L ? (float)(now - this.riptide$fadeOutStart) / 1000.0F : -1.0F;
         float fadeInAnim = this.riptide$fadeInStart > -1L ? (float)(now - this.riptide$fadeInStart) / 500.0F : -1.0F;
         float logoAlpha;
         if (fadeOutAnim >= 1.0F) {
            if (this.riptide$minecraft.gui.screen() != null) {
               this.riptide$tryRenderScreen(graphics, 0, 0, a);
            } else {
               this.riptide$minecraft.gui.hud.extractDeferredSubtitles();
            }

            int alpha = Mth.ceil((1.0F - Mth.clamp(fadeOutAnim - 1.0F, 0.0F, 1.0F)) * 255.0F);
            graphics.nextStratum();
            UiRenderer.rect(graphics, UiBounds.of(0, 0, width, height), replaceAlpha(BG_COLOR, alpha));
            logoAlpha = 1.0F - Mth.clamp(fadeOutAnim - 1.0F, 0.0F, 1.0F);
         } else if (this.riptide$fadeIn) {
            if (this.riptide$minecraft.gui.screen() != null && fadeInAnim < 1.0F) {
               this.riptide$tryRenderScreen(graphics, mouseX, mouseY, a);
            } else {
               this.riptide$minecraft.gui.hud.extractDeferredSubtitles();
            }

            int alpha = Mth.ceil(Mth.clamp(fadeInAnim, 0.15, 1.0) * 255.0);
            graphics.nextStratum();
            UiRenderer.rect(graphics, UiBounds.of(0, 0, width, height), replaceAlpha(BG_COLOR, alpha));
            logoAlpha = Mth.clamp(fadeInAnim, 0.0F, 1.0F);
         } else {
            this.riptide$minecraft.gameRenderer.gameRenderState().guiRenderState.clearColorOverride = colorVector(BG_COLOR);
            logoAlpha = 1.0F;
         }

         if (logoAlpha > 0.0F) {
            this.drawCustomLogo(graphics, width, height, logoAlpha);
         }

         float actualProgress = this.riptide$reload.getActualProgress();
         this.riptide$currentProgress = Mth.clamp(this.riptide$currentProgress * 0.95F + actualProgress * 0.050000012F, 0.0F, 1.0F);
         if (fadeOutAnim < 1.0F) {
            this.drawProgressBar(graphics, width, height, fadeOutAnim);
         }

         if (fadeOutAnim >= 2.0F) {
            this.riptide$minecraft.gui.setOverlay(null);
         }
      }
   }

   private void riptide$tryRenderScreen(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      Screen screen = this.riptide$minecraft.gui.screen();
      if (screen != null) {
         try {
            screen.extractRenderStateWithTooltipAndSubtitles(graphics, mouseX, mouseY, a);
         } catch (Exception var7) {
         }
      }
   }

   private void drawCustomLogo(GuiGraphicsExtractor graphics, int width, int height, float alpha) {
      int centerX = width / 2;
      int centerY = height / 2;
      double maxW = width * 0.85;
      double maxH = height * 0.85;
      double scale = Math.min(maxW / 2106.0, maxH / 1297.0) * 0.95;
      int drawW = (int)(2106.0 * scale);
      int drawH = (int)(1297.0 * scale);
      int x = centerX - drawW / 2;
      int y = centerY - drawH / 2;
      int color = ARGB.white(alpha);
      graphics.blit(
         RenderPipelines.GUI_TEXTURED,
         RiptideThemeTextures.recolored(CUSTOM_LOGO, RiptideTheme.Channel.ACCENT),
         x,
         y,
         0.0F,
         0.0F,
         drawW,
         drawH,
         2106,
         1297,
         2106,
         1297,
         color
      );
   }

   private void drawProgressBar(GuiGraphicsExtractor graphics, int width, int height, float fadeOutAnim) {
      float barFade = 1.0F - Mth.clamp(fadeOutAnim, 0.0F, 1.0F);
      double maxW = width * 0.85;
      double maxH = height * 0.85;
      double scale = Math.min(maxW / 2106.0, maxH / 1297.0) * 0.95;
      int drawW = (int)(2106.0 * scale);
      int drawH = (int)(1297.0 * scale);
      int centerX = width / 2;
      int logoBottom = height / 2 + drawH / 2;
      int barY = logoBottom + 10;
      int x0 = centerX - drawW / 2;
      int y0 = barY - 5;
      int x1 = centerX + drawW / 2;
      int y1 = barY + 5;
      int barWidth = Mth.ceil((x1 - x0 - 2) * this.riptide$currentProgress);
      int alpha = Math.round(barFade * 255.0F);
      int barColor = RiptideTheme.recolor(ARGB.color(alpha, 236, 32, 39), RiptideTheme.Channel.ACCENT);
      if (barWidth > 0) {
         UiRenderer.rect(graphics, UiBounds.of(x0 + 2, y0 + 2, barWidth, y1 - y0 - 4), barColor);
      }

      UiRenderer.outline(graphics, UiBounds.of(x0, y0, x1 - x0, y1 - y0), barColor);
   }

   private static int replaceAlpha(int color, int alpha) {
      return color & 16777215 | alpha << 24;
   }

   private static Vector4f colorVector(int color) {
      return new Vector4f((color >>> 16 & 0xFF) / 255.0F, (color >>> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, (color >>> 24 & 0xFF) / 255.0F);
   }

   public void tick() {
      if (!this.riptide$handOffToVanillaWhileHidden()) {
         if (this.riptide$fadeOutStart == -1L && this.riptide$reload.isDone() && this.riptide$isReadyToFadeOut()) {
            try {
               this.riptide$reload.checkExceptions();
               this.riptide$onFinish.accept(Optional.empty());
            } catch (Throwable var2) {
               this.riptide$onFinish.accept(Optional.of(var2));
            }

            this.riptide$fadeOutStart = Util.getMillis();
            if (this.riptide$minecraft.gui.screen() != null) {
               Window window = this.riptide$minecraft.getWindow();
               this.riptide$minecraft.gui.screen().init(window.getGuiScaledWidth(), window.getGuiScaledHeight());
            }
         }
      }
   }

   private boolean riptide$isReadyToFadeOut() {
      return !this.riptide$fadeIn || this.riptide$fadeInStart > -1L && Util.getMillis() - this.riptide$fadeInStart >= 1000L;
   }

   public boolean isPausing() {
      return true;
   }
}
