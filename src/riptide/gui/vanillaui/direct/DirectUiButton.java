package riptide.gui.vanillaui.direct;

import java.util.Objects;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.ConnectedButton;
import riptide.gui.vanillaui.components.UiSizing;
import riptide.gui.vanillaui.components.UiTone;

public class DirectUiButton extends DirectUiNode {
   private String text;
   private DirectUiButton.Variant variant;
   private Runnable onPress;
   private float minWidth = 26.0F;
   private float maxWidth = Float.MAX_VALUE;
   private float preferredWidth = -1.0F;
   private int horizontalPadding = 8;
   private int fixedHeight = -1;
   private int textYOffset = 0;
   private Identifier leadingIcon;
   private int iconSize = 10;
   private int iconGap = 4;
   private DirectUiButton.ContentAlignment contentAlignment = DirectUiButton.ContentAlignment.CENTER;
   private UiTone tone = UiTone.BODY;
   private boolean connectedStyle;
   private boolean connectedToggle;
   private ConnectedButton.Edges connectedEdges = ConnectedButton.FULL;
   private float connectedToggleProgress;
   private boolean connectedToggleProgressInitialized;
   private long lastConnectedAnimationNanos = System.nanoTime();

   private Identifier resolvedFont(CompactTheme theme, int height) {
      return theme.fontFor(this.tone);
   }

   public DirectUiButton(String text, DirectUiButton.Variant variant, Runnable onPress) {
      this.text = text == null ? "" : text;
      this.variant = variant == null ? DirectUiButton.Variant.SECONDARY : variant;
      this.onPress = onPress;
      this.height = 16.0F;
   }

   public DirectUiButton setText(String text) {
      String next = text == null ? "" : text;
      if (!this.text.equals(next)) {
         this.text = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectUiButton setVariant(DirectUiButton.Variant variant) {
      this.variant = variant == null ? DirectUiButton.Variant.SECONDARY : variant;
      return this;
   }

   public DirectUiButton setOnPress(Runnable onPress) {
      this.onPress = onPress;
      return this;
   }

   public DirectUiButton setMinWidth(float minWidth) {
      float next = Math.max(0.0F, minWidth);
      if (Float.compare(this.minWidth, next) != 0) {
         this.minWidth = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectUiButton setMaxWidth(float maxWidth) {
      float next = Math.max(this.minWidth, maxWidth);
      if (Float.compare(this.maxWidth, next) != 0) {
         this.maxWidth = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectUiButton setPreferredWidth(float preferredWidth) {
      if (Float.compare(this.preferredWidth, preferredWidth) != 0) {
         this.preferredWidth = preferredWidth;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectUiButton setHorizontalPadding(int horizontalPadding) {
      int next = Math.max(0, horizontalPadding);
      if (this.horizontalPadding != next) {
         this.horizontalPadding = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectUiButton setButtonHeight(int fixedHeight) {
      int next = Math.max(1, fixedHeight);
      if (this.fixedHeight != next) {
         this.fixedHeight = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectUiButton setTextYOffset(int textYOffset) {
      this.textYOffset = textYOffset;
      return this;
   }

   public DirectUiButton setLeadingIcon(Identifier leadingIcon) {
      if (!Objects.equals(this.leadingIcon, leadingIcon)) {
         this.leadingIcon = leadingIcon;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectUiButton setIconSize(int iconSize) {
      int next = Math.max(1, iconSize);
      if (this.iconSize != next) {
         this.iconSize = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectUiButton setIconGap(int iconGap) {
      int next = Math.max(0, iconGap);
      if (this.iconGap != next) {
         this.iconGap = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectUiButton setContentAlignment(DirectUiButton.ContentAlignment contentAlignment) {
      this.contentAlignment = contentAlignment == null ? DirectUiButton.ContentAlignment.CENTER : contentAlignment;
      return this;
   }

   public DirectUiButton setTone(UiTone tone) {
      UiTone next = tone == null ? UiTone.BODY : tone;
      if (this.tone != next) {
         this.tone = next;
         this.markLayoutDirty();
      }

      return this;
   }

   public DirectUiButton setConnectedStyle(boolean connectedStyle) {
      this.connectedStyle = connectedStyle;
      return this;
   }

   public DirectUiButton setConnectedToggle(boolean connectedToggle) {
      this.connectedToggle = connectedToggle;
      if (connectedToggle) {
         this.connectedStyle = true;
      }

      return this;
   }

   public DirectUiButton setConnectedEdges(ConnectedButton.Edges connectedEdges) {
      this.connectedEdges = connectedEdges == null ? ConnectedButton.LEFT_CELL : connectedEdges;
      return this;
   }

   public DirectUiButton setGrowX(boolean growX) {
      super.setGrowX(growX);
      return this;
   }

   @Override
   public float preferredWidth(DirectRenderContext context) {
      int measureHeight = this.fixedHeight > 0 ? this.fixedHeight : context.theme().buttonHeight();
      Identifier font = this.resolvedFont(context.theme(), measureHeight);
      int scaledHorizontalPadding = context.theme().scale(this.horizontalPadding);
      int scaledIconSize = context.theme().scale(this.iconSize);
      int scaledIconGap = context.theme().scale(this.iconGap);
      float fittedWidth = UiSizing.fitTextWidth(
         context.textRenderer(), this.text, font, context.theme().color(UiTone.BODY), scaledHorizontalPadding, this.minWidth, this.maxWidth
      );
      if (this.leadingIcon != null) {
         fittedWidth += scaledIconSize + scaledIconGap;
      }

      return this.preferredWidth > 0.0F
         ? UiSizing.clamp(Math.max(this.preferredWidth, fittedWidth), this.minWidth, this.maxWidth)
         : UiSizing.clamp(fittedWidth, this.minWidth, this.maxWidth);
   }

   @Override
   public float preferredHeight(DirectRenderContext context, float availableWidth) {
      int resolvedFixedHeight = this.fixedHeight > 0 ? this.fixedHeight : context.theme().buttonHeight();
      int contentHeight = context.theme().fontHeight(this.tone) + context.theme().scale(4);
      return Math.max(12, Math.max(resolvedFixedHeight, contentHeight));
   }

   @Override
   public void render(DirectRenderContext context) {
      if (this.visible) {
         int drawX = Math.round(this.x);
         int drawY = Math.round(this.y);
         int drawW = Math.round(this.width);
         int drawH = Math.round(this.height);
         boolean hovered = this.enabled && this.contains(context.mouseX(), context.mouseY());
         if (this.connectedStyle) {
            this.renderConnected(context, drawX, drawY, drawW, drawH, hovered);
         } else {
            this.renderRetained(context, drawX, drawY, drawW, drawH, hovered);
         }
      }
   }

   private void renderRetained(DirectRenderContext context, int drawX, int drawY, int drawW, int drawH, boolean hovered) {
      UiContext ui = UiContexts.overlay(context.drawContext(), context.textRenderer(), Math.round(context.mouseX()), Math.round(context.mouseY()));
      UiBounds bounds = UiBounds.of(drawX, drawY, drawW, drawH);
      Button.Tone buttonTone = this.buttonTone();
      if (this.leadingIcon == null) {
         Button.render(ui, bounds, this.text, buttonTone, hovered, false);
      } else {
         Button.renderIcon(ui, bounds, this.text, this.leadingIcon, buttonTone, hovered, false, 0.0F);
      }

      if (!this.enabled) {
         UiRenderer.rect(context.drawContext(), bounds.inset(1), 1711276032);
      }
   }

   private void renderConnected(DirectRenderContext context, int drawX, int drawY, int drawW, int drawH, boolean hovered) {
      UiContext ui = UiContexts.overlay(context.drawContext(), context.textRenderer(), Math.round(context.mouseX()), Math.round(context.mouseY()));
      UiBounds bounds = UiBounds.of(drawX, drawY, drawW, drawH);
      if (this.connectedToggle) {
         float target = this.variant == DirectUiButton.Variant.SUCCESS ? 1.0F : 0.0F;
         if (!this.connectedToggleProgressInitialized) {
            this.connectedToggleProgress = target;
            this.connectedToggleProgressInitialized = true;
         } else {
            this.connectedToggleProgress = this.animateConnectedProgress(this.connectedToggleProgress, target);
         }

         ConnectedButton.renderToggle(ui, bounds, this.text, this.leadingIcon, hovered, this.connectedToggleProgress, this.connectedEdges);
      } else {
         ConnectedButton.renderAction(ui, bounds, this.text, this.leadingIcon, this.buttonTone(), hovered, this.connectedEdges);
      }
   }

   boolean isConnectedCell() {
      return this.connectedStyle;
   }

   int connectedSeamColor(UiContext ui) {
      return this.connectedToggle
         ? ConnectedButton.toggleBorderColor(ui, this.connectedToggleProgress)
         : ConnectedButton.toneBorderColor(ui, this.buttonTone());
   }

   float connectedSeamWeight() {
      return ConnectedButton.seamWeight(this.buttonTone(), this.connectedToggle, this.connectedToggle ? this.connectedToggleProgress : 0.0F);
   }

   private Button.Tone buttonTone() {
      return toneFor(this.variant);
   }

   public static Button.Tone toneFor(DirectUiButton.Variant variant) {
      return switch (variant) {
         case PRIMARY -> Button.Tone.PRIMARY;
         case SECONDARY, GHOST -> Button.Tone.NORMAL;
         case DANGER -> Button.Tone.DANGER;
         case SUCCESS -> Button.Tone.SUCCESS;
      };
   }

   private float animateConnectedProgress(float current, float target) {
      long now = System.nanoTime();
      float delta = Math.max(0.0F, Math.min(0.05F, (float)(now - this.lastConnectedAnimationNanos) / 1.0E9F));
      this.lastConnectedAnimationNanos = now;
      float step = delta / 0.16F;
      if (current < target) {
         return Math.min(target, current + step);
      } else {
         return current > target ? Math.max(target, current - step) : current;
      }
   }

   @Override
   public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
      if (this.enabled && this.contains(mouseX, mouseY) && button == 0) {
         if (this.onPress != null) {
            this.onPress.run();
         }

         return true;
      } else {
         return false;
      }
   }

   public static enum ContentAlignment {
      CENTER,
      START;
   }

   public static enum Variant {
      PRIMARY,
      SECONDARY,
      GHOST,
      DANGER,
      SUCCESS;
   }
}
