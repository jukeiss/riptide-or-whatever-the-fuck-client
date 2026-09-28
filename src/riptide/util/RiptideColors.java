package riptide.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.CompactTheme;

public class RiptideColors {
   private static final CompactTheme PACK_UI_THEME = new CompactTheme();
   private static final int ACCENT = -5093304;
   private static final int ACCENT_SOFT = 1722959944;
   private static final int SURFACE = PACK_UI_THEME.windowFill();
   private static final int SURFACE_ALT = PACK_UI_THEME.listFill();
   private static final int SURFACE_HOVER = PACK_UI_THEME.overlaySurface(2103837);
   private static final int HEADER = PACK_UI_THEME.headerFill();
   private static final int HEADER_ACTIVE = PACK_UI_THEME.headerFill();
   private static final int DIVIDER = 1715151911;
   private static final int OUTER = PACK_UI_THEME.windowFillInactive();
   private static final int BORDER = PACK_UI_THEME.borderSoft();
   private static final int TEXT_PRIMARY = -791058;
   private static final int TEXT_SECONDARY = -3361096;
   private static final int TEXT_MUTED = -6388344;
   private static final int DANGER = -1938838;
   private static final int DANGER_BG = PACK_UI_THEME.dangerFill();
   private static final int SUCCESS = -11018872;
   private static final int SUCCESS_BG = PACK_UI_THEME.overlaySurface(2110506);
   private static final int TOOLTIP = PACK_UI_THEME.overlaySurface(526604);
   private static final int PACKET_GREEN = -8592483;
   private static final int PACKET_PINK = -27948;
   private static final int PACKET_ORANGE = -19091;
   private static final int PACKET_BLUE = -8340993;
   private static final int LOADING_BG = -16777216;
   private static final int PACKET_YELLOW = -10134;
   private static final int PACKET_CYAN = -9115393;
   private static final int PACKET_WHITE = -461586;
   private static final int PACKET_GRAY = -5195582;
   private static final int PACKET_LIGHT_YELLOW = -3921;

   public static int lessTransparent30(int color) {
      return boostAlpha(color, 11, 10);
   }

   public static int lessTransparent60(int color) {
      return boostAlpha(color, 13, 10);
   }

   private static int boostAlpha(int color, int numerator, int denominator) {
      int alpha = color >>> 24 & 0xFF;
      int boosted = Math.min(255, alpha * numerator / denominator);
      return boosted << 24 | color & 16777215;
   }

   public static int accent() {
      return RiptideTheme.recolor(-5093304, RiptideTheme.Channel.ACCENT);
   }

   public static int accentSoft() {
      return RiptideTheme.recolor(1722959944, RiptideTheme.Channel.ACCENT);
   }

   public static int primary() {
      return RiptideTheme.recolor(-5093304, RiptideTheme.Channel.ACCENT);
   }

   public static int primaryDark() {
      return RiptideTheme.recolor(-7522248, RiptideTheme.Channel.ACCENT);
   }

   public static int secondary() {
      return SURFACE_ALT;
   }

   public static int background() {
      return SURFACE;
   }

   public static int border() {
      return PACK_UI_THEME.borderSoft();
   }

   public static int windowOuter() {
      return OUTER;
   }

   public static int windowGlass() {
      return SURFACE;
   }

   public static int header() {
      return HEADER;
   }

   public static int headerActive() {
      return HEADER_ACTIVE;
   }

   public static int divider() {
      return RiptideTheme.recolor(1715151911, RiptideTheme.Channel.OUTLINE);
   }

   public static int buttonBg() {
      return SURFACE_ALT;
   }

   public static int buttonHoverBg() {
      return SURFACE_HOVER;
   }

   public static int buttonBorder() {
      return PACK_UI_THEME.borderSoft();
   }

   public static int buttonHover() {
      return buttonHoverBg();
   }

   public static int closeHover() {
      return RiptideTheme.recolor(-5093304, RiptideTheme.Channel.ACCENT);
   }

   public static int closeNormal() {
      return RiptideTheme.recolor(-6388344, RiptideTheme.Channel.TEXT);
   }

   public static int sectionHeaderBg() {
      return lessTransparent30(857679146);
   }

   public static int sectionHeaderText() {
      return RiptideTheme.recolor(-3361096, RiptideTheme.Channel.TEXT);
   }

   public static int actionBg() {
      return SURFACE_ALT;
   }

   public static int actionBorder() {
      return PACK_UI_THEME.borderSoft();
   }

   public static int dangerBg() {
      return PACK_UI_THEME.dangerFill();
   }

   public static int dangerBorder() {
      return RiptideTheme.recolor(-1938838, RiptideTheme.Channel.DANGER);
   }

   public static int dangerText() {
      return RiptideTheme.recolor(-1938838, RiptideTheme.Channel.DANGER);
   }

   public static int successBg() {
      return SUCCESS_BG;
   }

   public static int successBorder() {
      return RiptideTheme.recolor(-11018872, RiptideTheme.Channel.SUCCESS);
   }

   public static int successText() {
      return RiptideTheme.recolor(-11018872, RiptideTheme.Channel.SUCCESS);
   }

   public static int packetGreen() {
      return -8592483;
   }

   public static int packetPink() {
      return -27948;
   }

   public static int packetOrange() {
      return -19091;
   }

   public static int packetBlue() {
      return -8340993;
   }

   public static int packetYellow() {
      return -10134;
   }

   public static int packetCyan() {
      return -9115393;
   }

   public static int packetWhite() {
      return -461586;
   }

   public static int packetGray() {
      return -5195582;
   }

   public static int packetLightYellow() {
      return -3921;
   }

   public static int listBg() {
      return SURFACE_ALT;
   }

   public static int rowNormal() {
      return PACK_UI_THEME.rowFillNormal();
   }

   public static int rowHover() {
      return PACK_UI_THEME.rowFillHovered();
   }

   public static int rowSelected() {
      return PACK_UI_THEME.rowFillSelected();
   }

   public static int rowSelectedBorder() {
      return RiptideTheme.recolor(-10035062, RiptideTheme.Channel.TOGGLE);
   }

   public static int rowSelectedAccent() {
      return RiptideTheme.recolor(-12531347, RiptideTheme.Channel.TOGGLE);
   }

   public static int rowSelectedText() {
      return RiptideTheme.recolor(-2828, RiptideTheme.Channel.TEXT);
   }

   public static int packetRowBg(boolean c2s, int rowIndex, boolean hovered) {
      if (hovered) {
         return c2s ? lessTransparent30(1145068152) : lessTransparent30(1146173989);
      } else if (c2s) {
         return (rowIndex & 1) == 0 ? lessTransparent30(539243587) : lessTransparent30(405355858);
      } else {
         return (rowIndex & 1) == 0 ? lessTransparent30(539632411) : lessTransparent30(405939741);
      }
   }

   public static int packetRowText(boolean c2s, int rowIndex) {
      if (c2s) {
         return (rowIndex & 1) == 0 ? -6496513 : -8861448;
      } else {
         return (rowIndex & 1) == 0 ? -14193 : -20889;
      }
   }

   public static int packetRowSelectedBg(boolean hovered) {
      return hovered ? PACK_UI_THEME.overlaySurface(5413731) : PACK_UI_THEME.overlaySurfaceSoft(4225104);
   }

   public static int packetRowSelectedText() {
      return -4787259;
   }

   public static int packetRowSelectedAccent() {
      return -9184882;
   }

   public static int packetRowBlockedBg(boolean hovered) {
      return hovered ? lessTransparent30(-2000670660) : lessTransparent30(1721245482);
   }

   public static int packetRowBlockedAccent() {
      return -1549714;
   }

   public static int packetRowDivider() {
      return lessTransparent30(822083583);
   }

   public static int textPrimary() {
      return RiptideTheme.recolor(-791058, RiptideTheme.Channel.TEXT);
   }

   public static int textSecondary() {
      return RiptideTheme.recolor(-3361096, RiptideTheme.Channel.TEXT);
   }

   public static int textDim() {
      return RiptideTheme.recolor(-6388344, RiptideTheme.Channel.TEXT);
   }

   public static int textMuted() {
      return RiptideTheme.recolor(-6388344, RiptideTheme.Channel.TEXT);
   }

   public static int textLight() {
      return RiptideTheme.recolor(-791058, RiptideTheme.Channel.TEXT);
   }

   public static int subPanelBorder() {
      return PACK_UI_THEME.borderSoft();
   }

   public static int tooltipBg() {
      return TOOLTIP;
   }

   public static int loadingBg() {
      return -16777216;
   }

   public static int popupBg() {
      return PACK_UI_THEME.overlaySurface(658190);
   }

   public static int popupHover() {
      return lessTransparent60(822083583);
   }

   public static int slotNormal() {
      return -4668985;
   }

   public static int slotHover() {
      return -1511694;
   }

   public static int slotSelectedA() {
      return RiptideTheme.recolor(-5093304, RiptideTheme.Channel.ACCENT);
   }

   public static int slotSelectedB() {
      return RiptideTheme.recolor(-7522248, RiptideTheme.Channel.ACCENT);
   }

   public static int slotBorder() {
      return -9997443;
   }

   public static void drawBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c) {
      UiRenderer.outline(ctx, UiBounds.of(x, y, w, h), c);
   }

   public static void drawDivider(GuiGraphicsExtractor ctx, int x, int y, int width, int color) {
      UiRenderer.rect(ctx, UiBounds.of(x, y, width, 1), color);
   }

   public static void drawResizeHandle(GuiGraphicsExtractor ctx, int x, int y, int size, int color) {
      for (int i = 0; i < 3; i++) {
         int start = x + size - 3 - i * 4;
         UiRenderer.rect(ctx, UiBounds.of(start, y + size - 2, 2, 2), color);
         UiRenderer.rect(ctx, UiBounds.of(x + size - 2, y + size - 3 - i * 4, 2, 2), color);
      }
   }

   public static void drawInsetPanel(GuiGraphicsExtractor ctx, int x, int y, int w, int h, boolean focused) {
      UiRenderer.frame(ctx, UiBounds.of(x, y, w, h), listBg(), focused ? accent() : subPanelBorder());
   }
}
