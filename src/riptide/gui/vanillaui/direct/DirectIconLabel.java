package riptide.gui.vanillaui.direct;

import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.components.ConnectedButton;
import riptide.util.RiptideTheme;
import riptide.util.RiptideThemeTextures;

public final class DirectIconLabel extends DirectUiNode {
   private String text;
   private Identifier icon;
   private int iconSize = 12;
   private int iconGap = 5;
   private boolean connectedStyle;

   public DirectIconLabel(String text, Identifier icon) {
      this.text = text == null ? "" : text;
      this.icon = icon;
      this.height = 14.0F;
   }

   public DirectIconLabel setIconSize(int iconSize) {
      this.iconSize = Math.max(1, iconSize);
      this.markLayoutDirty();
      return this;
   }

   public DirectIconLabel setConnectedStyle(boolean connectedStyle) {
      this.connectedStyle = connectedStyle;
      return this;
   }

   @Override
   public float preferredWidth(DirectRenderContext context) {
      int textWidth = UiContexts.textRenderer(context.textRenderer()).width(this.text);
      return textWidth + (this.icon != null ? this.iconSize + this.iconGap : 0);
   }

   @Override
   public float preferredHeight(DirectRenderContext context, float availableWidth) {
      return this.connectedStyle ? 15.0F : Math.max(11, this.iconSize + 1);
   }

   @Override
   public void render(DirectRenderContext context) {
      if (this.visible) {
         int drawX = Math.round(this.x);
         int drawY = Math.round(this.y);
         int drawH = Math.round(this.height);
         if (this.connectedStyle) {
            ConnectedButton.renderCategory(
               UiContexts.overlay(context.drawContext(), context.textRenderer(), Math.round(context.mouseX()), Math.round(context.mouseY())),
               UiBounds.of(drawX, drawY, Math.round(this.width), drawH),
               this.text,
               this.icon
            );
         } else {
            int contentX = drawX;
            if (this.icon != null) {
               int iconY = drawY + Math.max(1, (drawH - this.iconSize) / 2);
               context.drawTexturedQuad(
                  RiptideThemeTextures.recolored(this.icon, RiptideTheme.Channel.ACCENT), drawX, iconY, drawX + this.iconSize, iconY + this.iconSize
               );
               contentX = drawX + this.iconSize + this.iconGap;
            }

            UiContexts.textRenderer(context.textRenderer())
               .draw(
                  context.drawContext(),
                  this.text,
                  contentX,
                  drawY + Math.max(1, (drawH - 9) / 2),
                  context.applyAlpha(RiptideTheme.recolor(-791321, RiptideTheme.Channel.TEXT))
               );
         }
      }
   }
}
