package riptide.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import riptide.gui.vanillaui.HoverFades;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.assets.UiAssets;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;

public final class RiptideKeyScreen extends Screen {
   private static final Identifier LOGO = Identifier.fromNamespaceAndPath("riptide", "textures/gui/title/riptide_client_logo.png");
   private static final int LOGO_TEX_W = 516;
   private static final int LOGO_TEX_H = 144;
   private static final SoundEvent CLICK = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("riptide", "gui.main_menu_click"));
   private static final int PANEL_W = 260;
   private static final int PANEL_H = 132;
   private static final int FIELD_H = 20;
   private static final int BUTTON_H = 20;
   private final CompactTheme theme = new CompactTheme();
   private final long openedAt = System.nanoTime();
   private EditBox keyField;
   private String errorMsg = "";
   private long errorAt;
   private int panelX;
   private int panelY;
   private int logoX;
   private int logoY;
   private int logoW;
   private int logoH;
   private UiBounds fieldBox;
   private UiBounds activateBox;
   private UiBounds quitBox;

   public RiptideKeyScreen() {
      super(Component.literal("Riptide Client"));
   }

   protected void init() {
      this.logoW = Math.min(260, this.width - 40);
      this.logoH = this.logoW * 144 / 516;
      int var1 = this.logoH + 6 + 132;
      this.logoX = (this.width - this.logoW) / 2;
      this.logoY = Math.max(8, (this.height - var1) / 2);
      this.panelX = (this.width - 260) / 2;
      this.panelY = this.logoY + this.logoH + 6;
      short var2 = 236;
      int var3 = this.panelX + 12;
      this.fieldBox = UiBounds.of(var3, this.panelY + 38, var2, 20);
      int var4 = (var2 - 6) / 2;
      int var5 = this.panelY + 38 + 20 + 10;
      this.activateBox = UiBounds.of(var3, var5, var4, 20);
      this.quitBox = UiBounds.of(var3 + var4 + 6, var5, var2 - var4 - 6, 20);
      String var6 = this.keyField == null ? "" : this.keyField.getValue();
      this.keyField = new EditBox(this.font, var3 + 6, this.panelY + 38 + 6, var2 - 12, 10, Component.literal("Key"));
      this.keyField.setBordered(false);
      this.keyField.setCentered(true);
      this.keyField.setMaxLength(19);
      this.keyField.setTextColor(-1);
      this.keyField.setValue(var6);
      this.keyField.setResponder(var1x -> {
         String var2x = var1x.toUpperCase();
         if (!var2x.equals(var1x)) {
            this.keyField.setValue(var2x);
         }
      });
      this.addRenderableWidget(this.keyField);
      this.setInitialFocus(this.keyField);
   }

   private void tryActivate() {
      String var1 = this.keyField.getValue().trim().toUpperCase();
      if (RiptideKeyLock.checkKey(var1)) {
         RiptideKeyLock.markPassed();
         RiptideKeyLock.saveKey(var1);
         this.minecraft.gui.setScreen(new TitleScreen());
      } else {
         this.errorMsg = var1.isEmpty() ? "Enter your key first." : "Key is wrong. Please try again with the correct one.";
         this.errorAt = System.nanoTime();
         this.keyField.setValue("");
         this.setFocused(this.keyField);
      }
   }

   private void click() {
      this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(CLICK, 1.0F, 1.0F));
   }

   public boolean keyPressed(KeyEvent var1) {
      if (var1.key() != 257 && var1.key() != 335) {
         return super.keyPressed(var1);
      } else {
         this.tryActivate();
         return true;
      }
   }

   public boolean mouseClicked(MouseButtonEvent var1, boolean var2) {
      if (var1.button() == 0) {
         double var3 = var1.x();
         double var5 = var1.y();
         if (contains(this.activateBox, var3, var5)) {
            this.click();
            this.tryActivate();
            return true;
         }

         if (contains(this.quitBox, var3, var5)) {
            this.click();
            this.minecraft.stop();
            return true;
         }

         if (contains(this.fieldBox, var3, var5)) {
            this.setFocused(this.keyField);
            this.keyField.setFocused(true);
         }
      }

      return super.mouseClicked(var1, var2);
   }

   private static boolean contains(UiBounds var0, double var1, double var3) {
      return var1 >= var0.x() && var3 >= var0.y() && var1 < var0.x() + var0.width() && var3 < var0.y() + var0.height();
   }

   public void extractBackground(GuiGraphicsExtractor var1, int var2, int var3, float var4) {
      this.minecraft.gameRenderer.panorama().extractRenderState(var1, this.width, this.height);
      var1.fill(0, 0, this.width, this.height, 1711276032);
   }

   public void extractRenderState(GuiGraphicsExtractor var1, int var2, int var3, float var4) {
      float var5 = Math.min(1.0F, (float)(System.nanoTime() - this.openedAt) / 3.0E8F);
      int var6 = this.theme.color(UiTone.ACCENT);
      var1.blit(
         RenderPipelines.GUI_TEXTURED,
         RiptideThemeTextures.recolored(LOGO, RiptideTheme.Channel.ACCENT),
         this.logoX,
         this.logoY,
         0.0F,
         0.0F,
         this.logoW,
         this.logoH,
         516,
         144,
         516,
         144,
         UiRenderer.applyAlpha(-1, var5)
      );
      this.panelChrome(var1, this.panelX, this.panelY, 260, 132, "ACTIVATION", var5, var6);
      String var7 = "Enter your license key to continue";
      int var8 = UiRenderer.applyAlpha(this.theme.color(UiTone.MUTED), var5);
      UiText.draw(
         var1,
         this.font,
         var7,
         UiAssets.FONT_BODY,
         var8,
         this.panelX + (260 - UiText.width(this.font, var7, UiAssets.FONT_BODY, var8)) / 2,
         this.panelY + 22,
         false
      );
      boolean var9 = this.keyField.isFocused();
      boolean var10 = !this.errorMsg.isEmpty() && System.nanoTime() - this.errorAt < 600000000L;
      int var11 = var10 ? this.theme.dangerBorder() : (var9 ? var6 : this.theme.borderColor());
      UiRenderer.frame(var1, this.fieldBox, UiRenderer.applyAlpha(this.theme.listFill(), var5), UiRenderer.applyAlpha(var11, var5));
      if (var9) {
         UiRenderer.rect(var1, UiBounds.of(this.fieldBox.x(), this.fieldBox.y(), 2, this.fieldBox.height()), UiRenderer.applyAlpha(var6, var5));
      }

      if (this.keyField.getValue().isEmpty()) {
         String var12 = "XXXX-XXXX-XXXX-XXXX";
         int var13 = UiRenderer.applyAlpha(-9539976, var5);
         var1.text(this.font, var12, this.fieldBox.x() + (this.fieldBox.width() - this.font.width(var12)) / 2, this.keyField.getY(), var13, false);
      }

      this.button(var1, this.activateBox, "Activate", var2, var3, var5, var6, true);
      this.button(var1, this.quitBox, "Quit", var2, var3, var5, var6, false);
      if (!this.errorMsg.isEmpty()) {
         int var16 = UiRenderer.applyAlpha(this.theme.dangerBorder() | 0xFF000000, var5);
         int var17 = this.activateBox.y() + 20 + 7;

         for (FormattedCharSequence var15 : this.font.split(Component.literal(this.errorMsg), 236)) {
            var1.text(this.font, var15, this.panelX + (260 - this.font.width(var15)) / 2, var17, var16, false);
            var17 += 10;
         }
      }

      super.extractRenderState(var1, var2, var3, var4);
   }

   private void panelChrome(GuiGraphicsExtractor var1, int var2, int var3, int var4, int var5, String var6, float var7, int var8) {
      UiRenderer.frame(
         var1, UiBounds.of(var2, var3, var4, var5), UiRenderer.applyAlpha(this.theme.windowFill(), var7), UiRenderer.applyAlpha(this.theme.borderSoft(), var7)
      );
      int var9 = UiRenderer.applyAlpha(var8, var7);
      UiRenderer.horizontalEdge(var1, var2 + 1, var3 + 1, 5, var9);
      UiRenderer.verticalEdge(var1, var2 + 1, var3 + 1, 5, var9);
      UiRenderer.horizontalEdge(var1, var2 + var4 - 6, var3 + 1, 5, var9);
      UiRenderer.verticalEdge(var1, var2 + var4 - 2, var3 + 1, 5, var9);
      UiRenderer.horizontalEdge(var1, var2 + 1, var3 + var5 - 2, 5, var9);
      UiRenderer.verticalEdge(var1, var2 + 1, var3 + var5 - 6, 5, var9);
      UiRenderer.horizontalEdge(var1, var2 + var4 - 6, var3 + var5 - 2, 5, var9);
      UiRenderer.verticalEdge(var1, var2 + var4 - 2, var3 + var5 - 6, 5, var9);
      String var10 = "// " + var6 + " //";
      int var11 = UiText.width(this.font, var10, UiAssets.FONT_LABEL, var9);
      UiText.draw(var1, this.font, var10, UiAssets.FONT_LABEL, var9, var2 + (var4 - var11) / 2, var3 + 6, false);
   }

   private void button(GuiGraphicsExtractor var1, UiBounds var2, String var3, int var4, int var5, float var6, int var7, boolean var8) {
      boolean var9 = contains(var2, var4, var5);
      float var10 = HoverFades.get(HoverFades.key(var2), var9);
      int var11 = RiptideTheme.recolor(-1207302650, RiptideTheme.Channel.BUTTON);
      int var12 = RiptideTheme.recolor(-1721357268, RiptideTheme.Channel.OUTLINE);
      UiRenderer.frame(var1, var2, UiRenderer.applyAlpha(var11, var6), UiRenderer.applyAlpha(var12, var6));
      float var13 = var8 ? Math.max(0.35F, var10) : var10;
      if (var13 > 0.001F) {
         UiRenderer.rect(var1, UiBounds.of(var2.x() + 1, var2.y() + 1, var2.width() - 2, var2.height() - 2), Math.round(20.0F * var10 * var6) << 24 | 16777215);
         UiRenderer.outline(var1, var2, UiRenderer.applyAlpha(var7, var13 * 0.8F * var6));
         UiRenderer.rect(var1, UiBounds.of(var2.x(), var2.y(), 2, var2.height()), UiRenderer.applyAlpha(var7, var13 * var6));
         UiRenderer.chevron(
            var1,
            UiBounds.of(var2.x() + 4 + Math.round(var10 * 3.0F), var2.y() + (var2.height() - 8) / 2, 7, 8),
            false,
            UiRenderer.applyAlpha(var7, var13 * var6)
         );
      }

      int var14 = UiRenderer.applyAlpha(!var9 && !var8 ? this.theme.color(UiTone.BODY) : this.theme.color(UiTone.TITLE), var6);
      int var15 = UiText.width(this.font, var3, UiAssets.FONT_BODY, var14);
      UiText.draw(var1, this.font, var3, UiAssets.FONT_BODY, var14, var2.x() + (var2.width() - var15) / 2, var2.y() + (var2.height() - 8) / 2, false);
   }

   public boolean shouldCloseOnEsc() {
      return false;
   }

   public void onClose() {
   }
}
