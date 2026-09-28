package riptide.gui.vanillaui.components;

import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.HoverFades;
import riptide.gui.vanillaui.StateFades;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiColors;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiRenderer;
import riptide.util.RiptideTheme;
import riptide.util.RiptideThemeTextures;

public final class Button {
   private Button() {
   }

   public static void render(UiContext context, UiBounds bounds, String label, Button.Tone tone, boolean hovered, boolean active) {
      render(context, bounds, label, null, tone, hovered, active, StateFades.get(StateFades.key(bounds), active));
   }

   public static void renderIcon(
      UiContext context, UiBounds bounds, String label, Identifier icon, Button.Tone tone, boolean hovered, boolean active, float activeProgress
   ) {
      render(context, bounds, label, icon, tone, hovered, active, activeProgress);
   }

   private static void render(
      UiContext context, UiBounds bounds, String label, Identifier icon, Button.Tone tone, boolean hovered, boolean active, float activeProgress
   ) {
      UiColors colors = context.theme().colors();
      float progress = Math.min(1.0F, Math.max(0.0F, activeProgress));
      boolean coloredTone = tone == Button.Tone.SUCCESS || tone == Button.Tone.DANGER || tone == Button.Tone.PRIMARY;
      UiRenderer.frame(context.graphics(), bounds, -1071766483, colors.buttonBorder);
      if (progress > 0.001F) {
         if (coloredTone) {
            int toneFill = switch (tone) {
               case PRIMARY -> colors.accentDark;
               case SUCCESS -> RiptideTheme.recolor(-736144845, RiptideTheme.Channel.SUCCESS);
               case DANGER -> RiptideTheme.recolor(-732751072, RiptideTheme.Channel.DANGER);
               default -> -869781959;
            };

            int onBorder = switch (tone) {
               case PRIMARY -> colors.accent;
               case SUCCESS -> colors.success;
               case DANGER -> colors.bad;
               default -> colors.borderSoft;
            };
            UiRenderer.rect(context.graphics(), bounds.inset(1), UiRenderer.applyAlpha(toneFill, progress));
            UiRenderer.outline(context.graphics(), bounds, UiRenderer.applyAlpha(onBorder, progress));
         } else {
            UiRenderer.rect(context.graphics(), bounds.inset(1), UiRenderer.applyAlpha(-869781959, progress));
         }
      }

      float hoverT = HoverFades.get(HoverFades.key(bounds), hovered);
      if (hoverT > 0.001F) {
         UiRenderer.rect(context.graphics(), bounds.inset(1), Math.round(20.0F * hoverT) << 24 | 16777215);
      }

      if (icon == null) {
         context.text().drawCentered(context.graphics(), label, bounds, colors.text);
      } else {
         int iconSize = Math.min(12, Math.max(8, bounds.height() - 5));
         int textW = context.text().width(label);
         int groupW = iconSize + 4 + textW;
         int iconX = bounds.x() + Math.max(3, (bounds.width() - groupW) / 2);
         int iconY = bounds.y() + Math.max(1, (bounds.height() - iconSize) / 2);
         context.graphics()
            .blit(RiptideThemeTextures.recolored(icon, RiptideTheme.Channel.ACCENT), iconX, iconY, iconX + iconSize, iconY + iconSize, 0.0F, 1.0F, 0.0F, 1.0F);
         context.text()
            .drawFitted(
               context.graphics(),
               label,
               iconX + iconSize + 4,
               context.text().centeredY(bounds),
               Math.max(1, bounds.right() - (iconX + iconSize + 4) - 4),
               colors.text
            );
      }
   }

   public static enum Tone {
      NORMAL,
      SECONDARY,
      PRIMARY,
      SUCCESS,
      DANGER;
   }
}
