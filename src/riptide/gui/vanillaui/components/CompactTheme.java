package riptide.gui.vanillaui.components;

import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.assets.UiAssets;
import riptide.gui.vanillaui.direct.DirectUiButton;
import riptide.gui.vanillaui.direct.DirectUiInsets;
import riptide.util.RiptideTheme;

public final class CompactTheme {
   public static final float DEFAULT_DENSITY = 2.0F;
   private static final int BODY_FONT_HEIGHT = 11;
   private static final int LABEL_FONT_HEIGHT = 12;
   private static final int TITLE_FONT_HEIGHT = 13;
   private static final int HEADER_HEIGHT = 16;
   private static final int CONTENT_PADDING = 4;
   private static final int ROW_GAP = 2;
   private static final int BUTTON_HEIGHT = 14;
   private static final int LABEL_GAP = 1;
   private final int windowRadius = 0;

   private static int rc(int color, RiptideTheme.Channel channel) {
      return RiptideTheme.recolor(color, channel);
   }

   public static int lessTransparent30(int color) {
      return boostAlpha(color, 13, 10);
   }

   private static int boostAlpha(int color, int numerator, int denominator) {
      int alpha = color >>> 24 & 0xFF;
      int boosted = Math.min(255, alpha * numerator / denominator);
      return boosted << 24 | color & 16777215;
   }

   public Identifier fontFor(UiTone tone) {
      return switch (tone) {
         case TITLE -> UiAssets.FONT_TITLE;
         case LABEL -> UiAssets.FONT_LABEL;
         case BODY, MUTED, ACCENT -> UiAssets.FONT_BODY;
      };
   }

   public int windowRadius() {
      return 0;
   }

   public int headerHeight() {
      return Math.max(16, this.lineHeight(UiTone.LABEL, 3));
   }

   public int contentPadding() {
      return 4;
   }

   public int rowGap() {
      return 2;
   }

   public int buttonHeight() {
      return Math.max(14, this.lineHeight(UiTone.BODY, 3));
   }

   public int labelGap() {
      return 1;
   }

   public float scaleFactor() {
      return 1.0F;
   }

   public int scale(int value) {
      return value;
   }

   public float scale(float value) {
      return value;
   }

   public DirectUiInsets scale(DirectUiInsets insets) {
      return insets == null
         ? DirectUiInsets.NONE
         : new DirectUiInsets(this.scale(insets.left()), this.scale(insets.top()), this.scale(insets.right()), this.scale(insets.bottom()));
   }

   public int fontHeight(UiTone tone) {
      int vanillaHeight = UiText.fontHeight(this.fontFor(tone));
      if (vanillaHeight > 0) {
         return vanillaHeight;
      } else {
         return switch (tone) {
            case TITLE -> 13;
            case LABEL -> 12;
            case BODY, MUTED, ACCENT -> 11;
         };
      }
   }

   public int lineHeight(UiTone tone, int extraSpacing) {
      int minimumSpacing = switch (tone) {
         case TITLE -> 3;
         case LABEL -> 2;
         case BODY, MUTED, ACCENT -> 2;
      };
      return this.fontHeight(tone) + Math.max(this.scale(extraSpacing), minimumSpacing);
   }

   public int bodyTextNudge() {
      return 1;
   }

   public int buttonTextNudge() {
      return 1;
   }

   public int fieldTextNudge() {
      return 1;
   }

   public int color(UiTone tone) {
      return switch (tone) {
         case TITLE, LABEL, BODY -> rc(-791321, RiptideTheme.Channel.TEXT);
         case MUTED -> rc(-4743522, RiptideTheme.Channel.TEXT);
         case ACCENT -> rc(-45747, RiptideTheme.Channel.ACCENT);
      };
   }

   public int overlaySurface(int rgb) {
      return -1476395008 | rgb & 16777215;
   }

   public int overlaySurfaceSoft(int rgb) {
      return -1610612736 | rgb & 16777215;
   }

   public int overlaySurfaceStrong(int rgb) {
      return -1375731712 | rgb & 16777215;
   }

   public int windowFill() {
      return this.overlaySurface(657932);
   }

   public int windowFillInactive() {
      return this.overlaySurfaceSoft(460553);
   }

   public int headerFill() {
      return this.overlaySurface(1118484);
   }

   public int headerFillInactive() {
      return this.overlaySurfaceSoft(657932);
   }

   public int headerAccent() {
      return rc(-50373, RiptideTheme.Channel.HEADER);
   }

   public int borderColor() {
      return rc(-2996152, RiptideTheme.Channel.OUTLINE);
   }

   public int borderSoft() {
      return rc(lessTransparent30(-1063699910), RiptideTheme.Channel.OUTLINE);
   }

   public int hoverFill() {
      return rc(1713245712, RiptideTheme.Channel.HOVER);
   }

   public int dangerFill() {
      return rc(this.overlaySurface(2756114), RiptideTheme.Channel.DANGER);
   }

   public int dangerBorder() {
      return rc(-33411, RiptideTheme.Channel.DANGER);
   }

   public int buttonFill(DirectUiButton.Variant variant) {
      return switch (variant) {
         case PRIMARY -> rc(this.overlaySurface(9379620), RiptideTheme.Channel.BUTTON);
         case SECONDARY -> rc(this.overlaySurface(1183249), RiptideTheme.Channel.BUTTON);
         case GHOST -> rc(this.overlaySurfaceSoft(1182991), RiptideTheme.Channel.BUTTON);
         case DANGER -> rc(this.overlaySurface(2297105), RiptideTheme.Channel.DANGER);
         case SUCCESS -> rc(this.overlaySurface(1058330), RiptideTheme.Channel.SUCCESS);
      };
   }

   public int buttonFill(DirectUiButton.Variant variant, boolean active) {
      return !active ? this.buttonFillInactive(variant) : this.buttonFill(variant);
   }

   private int buttonFillInactive(DirectUiButton.Variant variant) {
      return switch (variant) {
         case PRIMARY -> rc(this.overlaySurfaceSoft(9379620), RiptideTheme.Channel.BUTTON);
         case SECONDARY -> this.overlaySurfaceSoft(1184534);
         case GHOST -> 1880232726;
         case DANGER -> this.overlaySurfaceSoft(1184534);
         case SUCCESS -> this.overlaySurfaceSoft(1184534);
      };
   }

   public int buttonBorder(DirectUiButton.Variant variant) {
      return switch (variant) {
         case PRIMARY -> rc(-30841, RiptideTheme.Channel.OUTLINE);
         case SECONDARY, GHOST -> this.borderSoft();
         case DANGER -> rc(-4896696, RiptideTheme.Channel.DANGER);
         case SUCCESS -> rc(-11544186, RiptideTheme.Channel.SUCCESS);
      };
   }

   public int buttonBorderInactive(DirectUiButton.Variant variant) {
      return switch (variant) {
         case PRIMARY -> rc(-30841, RiptideTheme.Channel.OUTLINE);
         case SECONDARY, GHOST -> rc(-9744304, RiptideTheme.Channel.OUTLINE);
         case DANGER -> rc(-4896696, RiptideTheme.Channel.DANGER);
         case SUCCESS -> rc(-11544186, RiptideTheme.Channel.SUCCESS);
      };
   }

   public int buttonBorderGlow(DirectUiButton.Variant variant) {
      return switch (variant) {
         case PRIMARY -> rc(-23388, RiptideTheme.Channel.HOVER);
         case SECONDARY, GHOST -> rc(-39836, RiptideTheme.Channel.HOVER);
         case DANGER -> rc(-28528, RiptideTheme.Channel.DANGER);
         case SUCCESS -> rc(-5575744, RiptideTheme.Channel.SUCCESS);
      };
   }

   public int buttonTextColor(DirectUiButton.Variant variant) {
      return switch (variant) {
         case PRIMARY -> rc(-2828, RiptideTheme.Channel.TEXT);
         case SECONDARY, GHOST -> this.color(UiTone.BODY);
         case DANGER -> rc(-42406, RiptideTheme.Channel.DANGER);
         case SUCCESS -> rc(-4656181, RiptideTheme.Channel.SUCCESS);
      };
   }

   public int buttonTextColorInactive(DirectUiButton.Variant variant) {
      return switch (variant) {
         case PRIMARY -> rc(-2828, RiptideTheme.Channel.TEXT);
         case SECONDARY, GHOST -> this.color(UiTone.MUTED);
         case DANGER -> rc(-6521989, RiptideTheme.Channel.DANGER);
         case SUCCESS -> rc(-7821680, RiptideTheme.Channel.SUCCESS);
      };
   }

   public int overlayButtonFill(CompactOverlayButton.Variant variant, boolean active) {
      if (variant == CompactOverlayButton.Variant.FILTER_ON) {
         return 1430088703;
      } else {
         return variant == CompactOverlayButton.Variant.FILTER_OFF ? 1713842993 : this.buttonFill(this.toButtonVariant(variant), active);
      }
   }

   private int overlayButtonFillInactive(CompactOverlayButton.Variant variant) {
      return this.buttonFillInactive(this.toButtonVariant(variant));
   }

   public int overlayButtonBorder(CompactOverlayButton.Variant variant, boolean active) {
      if (variant == CompactOverlayButton.Variant.FILTER_ON) {
         return -9466369;
      } else if (variant == CompactOverlayButton.Variant.FILTER_OFF) {
         return -11117210;
      } else {
         return active ? this.buttonBorder(this.toButtonVariant(variant)) : this.buttonBorderInactive(this.toButtonVariant(variant));
      }
   }

   private int overlayButtonBorderInactive(CompactOverlayButton.Variant variant) {
      return this.buttonBorderInactive(this.toButtonVariant(variant));
   }

   public int overlayButtonBorderGlow(CompactOverlayButton.Variant variant) {
      if (variant == CompactOverlayButton.Variant.FILTER_ON) {
         return -7231489;
      } else {
         return variant == CompactOverlayButton.Variant.FILTER_OFF ? -9143161 : this.buttonBorderGlow(this.toButtonVariant(variant));
      }
   }

   public int overlayButtonTextColor(CompactOverlayButton.Variant variant, boolean active) {
      if (variant == CompactOverlayButton.Variant.FILTER_ON) {
         return -1;
      } else if (variant == CompactOverlayButton.Variant.FILTER_OFF) {
         return -4669753;
      } else {
         return active ? this.buttonTextColor(this.toButtonVariant(variant)) : this.buttonTextColorInactive(this.toButtonVariant(variant));
      }
   }

   private int overlayButtonTextColorInactive(CompactOverlayButton.Variant variant) {
      return this.buttonTextColorInactive(this.toButtonVariant(variant));
   }

   private DirectUiButton.Variant toButtonVariant(CompactOverlayButton.Variant variant) {
      return switch (variant) {
         case PRIMARY -> DirectUiButton.Variant.PRIMARY;
         case SECONDARY -> DirectUiButton.Variant.SECONDARY;
         case GHOST -> DirectUiButton.Variant.GHOST;
         case DANGER -> DirectUiButton.Variant.DANGER;
         case SUCCESS -> DirectUiButton.Variant.SUCCESS;
         case FILTER_ON -> DirectUiButton.Variant.PRIMARY;
         case FILTER_OFF -> DirectUiButton.Variant.GHOST;
      };
   }

   public int listFill() {
      return this.overlaySurface(1052692);
   }

   public int listFillFocused() {
      return this.overlaySurfaceStrong(1250328);
   }

   public int rowFillNormal() {
      return 639113758;
   }

   public int rowFillHovered() {
      return rc(1327766045, RiptideTheme.Channel.HOVER);
   }

   public int rowFillSelected() {
      return rc(this.overlaySurface(2308139), RiptideTheme.Channel.TOGGLE);
   }

   public int inlineBannerFill() {
      return rc(this.overlaySurface(1183762), RiptideTheme.Channel.BUTTON);
   }

   public int inactiveCoverFill() {
      return 905969664;
   }

   public int inactiveBodyFadeFill() {
      return this.overlaySurface(592139);
   }
}
