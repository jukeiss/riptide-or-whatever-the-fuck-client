package riptide.gui.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.assets.UiAssets;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiSizing;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.util.RiptideTheme;
import riptide.util.RiptideThemeTextures;
import riptide.util.RiptideUiScale;

public class RiptideWelcomeScreen extends Screen {
   private static final Identifier FONT_TITLE = UiAssets.FONT_TITLE;
   private static final Identifier FONT_LABEL = UiAssets.FONT_LABEL;
   private static final Identifier FONT_BODY = UiAssets.FONT_BODY;
   private static final long UNLOCK_DELAY_MS = 7000L;
   private static final int TITLE_COLOR = -2828;
   private static final int HEADER_COLOR = -45747;
   private static final int CARD_FILL = -234223092;
   private static final int CARD_BORDER = -46518;
   private static final int DIVIDER_COLOR = 1728006730;
   private static final Identifier HUD_LOGO = Identifier.fromNamespaceAndPath("riptide", "textures/gui/hud/riptide_welcome.png");
   private static final int HUD_LOGO_TEXTURE_WIDTH = 878;
   private static final int HUD_LOGO_TEXTURE_HEIGHT = 83;
   private static final int HUD_LOGO_DISPLAY_WIDTH = 878;
   private static final int HUD_LOGO_DISPLAY_HEIGHT = 83;
   private static final Identifier DONATE_SUPPORT_ICON = Identifier.fromNamespaceAndPath("riptide", "textures/gui/title/icons/donate.png");
   private static final int SUPPORT_ICON_WIDTH = 32;
   private static final int SUPPORT_ICON_HEIGHT = 32;
   private static final int SUPPORT_ICON_DRAW_SIZE = 16;
   private static final String TITLE_TEXT = "Thanks for using";
   private static final String[][] SECTIONS = new String[][]{
      {"Welcome", "Quality comes first. Open Modules & Macros from the title screen to set things up, or press the menu key in game."}
   };
   private static final int PAD = 12;
   private static final int CARD_MAX_WIDTH = 340;
   private static final int BUTTON_HEIGHT = 20;
   private static final int BUTTON_GAP = 6;
   private final CompactTheme theme = new CompactTheme();
   private final long createdAtMs = System.currentTimeMillis();
   private final RiptideWelcomeScreen.Btn continueBtn = new RiptideWelcomeScreen.Btn(
      "continue", "Continue", () -> this.minecraft.gui.setScreen(new TitleScreen())
   );
   private int layoutScreenWidth = -1;
   private int layoutScreenHeight = -1;
   private int wrappedWidth = -1;
   private List<List<String>> wrappedSections = List.of();

   public RiptideWelcomeScreen() {
      super(Component.literal("Thanks for using"));
   }

   public boolean isPauseScreen() {
      return false;
   }

   public boolean shouldCloseOnEsc() {
      return false;
   }

   public void extractBackground(GuiGraphicsExtractor var1, int var2, int var3, float var4) {
   }

   private boolean continueUnlocked() {
      return System.currentTimeMillis() - this.createdAtMs >= 7000L;
   }

   private int continueSecondsLeft() {
      long var1 = 7000L - (System.currentTimeMillis() - this.createdAtMs);
      return (int)Math.max(1.0, Math.ceil(var1 / 1000.0));
   }

   public void extractRenderState(GuiGraphicsExtractor var1, int var2, int var3, float var4) {
      this.minecraft.gameRenderer.panorama().extractRenderState(var1, this.width, this.height);
      float var5 = (float)RiptideUiScale.toVirtual(var2);
      float var6 = (float)RiptideUiScale.toVirtual(var3);
      this.layout();
      RiptideUiScale.pushOverlayScale(var1);

      try {
         int var7 = RiptideUiScale.getVirtualScreenWidth();
         int var8 = RiptideUiScale.getVirtualScreenHeight();
         UiRenderer.rect(var1, UiBounds.of(0, 0, var7, var8), -939524096);
         int var9 = this.cardWidth();
         int var10 = (var7 - var9) / 2;
         int var11 = this.cardHeight();
         int var12 = Math.max(8, (var8 - var11) / 2);
         int var13 = var9 - 24;
         int var14 = var10 + 12;
         UiRenderer.rect(var1, UiBounds.of(var10, var12, var9, var11), -234223092);
         drawThickBorder(var1, var10, var12, var9, var11, RiptideTheme.recolor(-46518, RiptideTheme.Channel.OUTLINE), 2);
         int var15 = var12 + 12 + 2;
         this.drawCentered(var1, "Thanks for using", FONT_TITLE, RiptideTheme.recolor(-2828, RiptideTheme.Channel.TEXT), var14, var13, var15);
         var15 += UiText.fontHeight(FONT_TITLE) + 7;
         int var16 = welcomeLogoWidth(var13);
         int var17 = welcomeLogoHeight(var16);
         int var18 = var14 + (var13 - var16) / 2;
         var1.blit(
            RenderPipelines.GUI_TEXTURED,
            RiptideThemeTextures.recolored(HUD_LOGO, RiptideTheme.Channel.ACCENT),
            var18,
            var15,
            0.0F,
            0.0F,
            var16,
            var17,
            878,
            83,
            878,
            83
         );
         var15 += var17 + 9;
         UiRenderer.rect(var1, UiBounds.of(var14, var15, var13, 1), RiptideTheme.recolor(1728006730, RiptideTheme.Channel.OUTLINE));
         var15 += 8;
         int var19 = UiText.fontHeight(FONT_LABEL);
         int var20 = UiText.fontHeight(FONT_LABEL) + 3;
         int var21 = this.theme.color(UiTone.MUTED);
         List var22 = this.wrappedSections(var13);

         for (int var23 = 0; var23 < SECTIONS.length; var23++) {
            String[] var24 = SECTIONS[var23];
            UiText.draw(var1, this.font, var24[0], FONT_LABEL, RiptideTheme.recolor(-45747, RiptideTheme.Channel.ACCENT), var14, var15, false);
            var15 += var19 + 3;

            for (String var26 : (List)var22.get(var23)) {
               UiText.draw(var1, this.font, var26, FONT_LABEL, var21, var14, var15, false);
               var15 += var20;
            }

            var15 += 8;
         }

         boolean var34 = this.continueUnlocked();
         this.continueBtn.label = var34 ? "Continue" : "Continue (" + this.continueSecondsLeft() + ")";
         this.renderButton(var1, this.continueBtn, var5, var6, var34);
      } finally {
         RiptideUiScale.popOverlayScale(var1);
      }
   }

   public boolean mouseClicked(MouseButtonEvent var1, boolean var2) {
      if (var1.button() != 0) {
         return false;
      } else {
         float var3 = (float)RiptideUiScale.toVirtual(var1.x());
         float var4 = (float)RiptideUiScale.toVirtual(var1.y());
         this.layout();
         if (this.continueUnlocked() && this.continueBtn.contains(var3, var4)) {
            this.press(this.continueBtn, var3, var4);
            return true;
         } else {
            return false;
         }
      }
   }

   private void press(RiptideWelcomeScreen.Btn var1, float var2, float var3) {
      var1.action.run();
   }

   public void removed() {
   }

   private int cardWidth() {
      int var1 = RiptideUiScale.getVirtualScreenWidth();
      return Math.max(1, Math.min(Math.max(1, var1 - 12), 340));
   }

   private int cardHeight() {
      int var1 = Math.max(1, this.cardWidth() - 24);
      int var2 = UiText.fontHeight(FONT_LABEL);
      int var3 = UiText.fontHeight(FONT_LABEL) + 3;
      int var4 = welcomeLogoWidth(var1);
      int var5 = 12 + UiText.fontHeight(FONT_TITLE) + 7 + welcomeLogoHeight(var4) + 9 + 1 + 8;
      List var6 = this.wrappedSections(var1);

      for (int var7 = 0; var7 < SECTIONS.length; var7++) {
         var5 += var2 + 3 + ((List)var6.get(var7)).size() * var3 + 8;
      }

      return var5 + 32;
   }

   private void layout() {
      int var1 = RiptideUiScale.getVirtualScreenWidth();
      int var2 = RiptideUiScale.getVirtualScreenHeight();
      if (this.layoutScreenWidth != var1 || this.layoutScreenHeight != var2) {
         this.layoutScreenWidth = var1;
         this.layoutScreenHeight = var2;
         int var3 = this.cardWidth();
         int var4 = (var1 - var3) / 2;
         int var5 = this.cardHeight();
         int var6 = Math.max(8, (var2 - var5) / 2);
         int var7 = Math.max(1, var3 - 24);
         int var8 = var6 + var5 - 12 - 20;
         int var9 = var4 + 12;
         this.continueBtn.set(var9, var8, var4 + var3 - 12 - var9, 20);
      }
   }

   private List<String> wrap(String var1, int var2) {
      ArrayList var3 = new ArrayList();
      StringBuilder var4 = new StringBuilder();

      for (String var8 : var1.split(" ")) {
         String var9 = var4.length() == 0 ? var8 : var4 + " " + var8;
         if (var4.length() != 0 && UiText.width(this.font, var9, FONT_LABEL, -1) > var2) {
            var3.add(var4.toString());
            var4.setLength(0);
            var4.append(var8);
         } else {
            var4.setLength(0);
            var4.append(var9);
         }
      }

      if (var4.length() > 0) {
         var3.add(var4.toString());
      }

      return var3;
   }

   private List<List<String>> wrappedSections(int var1) {
      if (this.wrappedWidth == var1 && this.wrappedSections.size() == SECTIONS.length) {
         return this.wrappedSections;
      } else {
         ArrayList var2 = new ArrayList(SECTIONS.length);

         for (String[] var6 : SECTIONS) {
            var2.add(List.copyOf(this.wrap(var6[1], var1)));
         }

         this.wrappedWidth = var1;
         this.wrappedSections = List.copyOf(var2);
         return this.wrappedSections;
      }
   }

   private void drawCentered(GuiGraphicsExtractor var1, String var2, Identifier var3, int var4, int var5, int var6, int var7) {
      int var8 = UiText.width(this.font, var2, var3, var4);
      UiText.draw(var1, this.font, var2, var3, var4, var5 + (var6 - var8) / 2, var7, false);
   }

   private static int welcomeLogoWidth(int var0) {
      return Math.max(170, Math.min(var0 - 18, 286));
   }

   private static int welcomeLogoHeight(int var0) {
      return Math.max(1, Math.round(var0 * 0.094533026F));
   }

   private void renderButton(GuiGraphicsExtractor var1, RiptideWelcomeScreen.Btn var2, float var3, float var4, boolean var5) {
      boolean var6 = var5 && var2.contains(var3, var4);
      UiContext var7 = UiContexts.overlay(var1, this.font, (int)var3, (int)var4);
      UiBounds var8 = UiBounds.of(var2.x, var2.y, var2.w, var2.h);
      if (var2.icon != null) {
         Button.renderIcon(var7, var8, var2.label, var2.icon, Button.Tone.SECONDARY, var6, false, 0.0F);
      } else {
         Button.render(var7, var8, var2.label, Button.Tone.SECONDARY, var6, false);
      }

      if (!var5) {
         UiRenderer.rect(var1, var8, 1711276032);
      }
   }

   private void drawCenteredButtonLabel(GuiGraphicsExtractor var1, RiptideWelcomeScreen.Btn var2, int var3) {
      int var4 = var2.x + 3;
      int var5 = Math.max(1, var2.w - 6);
      if (var2.icon != null) {
         int var6 = Math.min(16, Math.max(1, Math.min(var2.w - 4, var2.h - 4)));
         var4 = var2.x + 4 + var6 + 7;
         var5 = Math.max(1, var2.x + var2.w - 5 - var4);
      }

      String var10 = UiText.trimToWidth(this.font, var2.label, var5, FONT_LABEL, var3);
      int var7 = UiText.width(this.font, var10, FONT_LABEL, var3);
      int var8 = var2.icon != null ? var4 : var4 + (var5 - var7 + 1) / 2;
      if ("crypto".equals(var2.id)) {
         int var9 = var2.x + (var2.w - var7 + 1) / 2;
         var8 = Math.max(var4, var9);
      }

      int var11 = UiSizing.alignTextY(var2.y, var2.h, UiText.fontHeight(FONT_LABEL), this.theme.buttonTextNudge());
      UiText.draw(var1, this.font, var10, FONT_LABEL, var3, var8, var11, false);
   }

   private static void drawThickBorder(GuiGraphicsExtractor var0, int var1, int var2, int var3, int var4, int var5, int var6) {
      if (var3 > 0 && var4 > 0) {
         UiRenderer.rect(var0, UiBounds.of(var1, var2, var3, var6), var5);
         UiRenderer.rect(var0, UiBounds.of(var1, var2 + var4 - var6, var3, var6), var5);
         UiRenderer.rect(var0, UiBounds.of(var1, var2, var6, var4), var5);
         UiRenderer.rect(var0, UiBounds.of(var1 + var3 - var6, var2, var6, var4), var5);
      }
   }

   private static final class Btn {
      private final String id;
      private String label;
      private final Identifier icon;
      private final int iconTextureWidth;
      private final int iconTextureHeight;
      private final Runnable action;
      private int x;
      private int y;
      private int w;
      private int h;

      private Btn(String var1, String var2, Runnable var3) {
         this(var1, var2, null, 1, 1, var3);
      }

      private Btn(String var1, String var2, Identifier var3, int var4, int var5, Runnable var6) {
         this.id = var1;
         this.label = var2;
         this.icon = var3;
         this.iconTextureWidth = Math.max(1, var4);
         this.iconTextureHeight = Math.max(1, var5);
         this.action = var6;
      }

      private void set(int var1, int var2, int var3, int var4) {
         this.x = var1;
         this.y = var2;
         this.w = var3;
         this.h = var4;
      }

      private boolean contains(float var1, float var2) {
         return var1 >= this.x && var2 >= this.y && var1 < this.x + this.w && var2 < this.y + this.h;
      }
   }
}
