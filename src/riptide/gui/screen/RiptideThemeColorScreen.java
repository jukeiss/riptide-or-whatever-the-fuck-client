package riptide.gui.screen;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import riptide.commands.args.KeyArgumentType;
import riptide.gui.RiptideThemeApplyOverlay;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.assets.UiAssets;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.Toggle;
import riptide.gui.vanillaui.components.UiText;
import riptide.modules.Module;
import riptide.modules.ModuleRegistry;
import riptide.util.RiptideBackgroundTasks;
import riptide.util.RiptideChatField;
import riptide.util.RiptideConfig;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideTheme;
import riptide.util.RiptideThemeTextures;
import riptide.util.RiptideUiScale;

public class RiptideThemeColorScreen extends Screen {
   private static final int FONT_BODY_COLOR = -791321;
   private static final int FONT_MUTED = -4743522;
   private final Screen parent;
   private final RiptideConfig cfg;
   private final RiptideConfig.ThemeColors pending = new RiptideConfig.ThemeColors();
   private RiptideTheme.State pendingState;
   private final RiptideThemeColorScreen.Picker picker = new RiptideThemeColorScreen.Picker();
   private int selected;
   private RiptideThemeTextures.Preview logoPreview;
   private RiptideThemeTextures.Preview panoramaPreview;
   private RiptideThemeTextures.Preview iconPreview;
   private UiBounds simpleTab;
   private UiBounds advancedTab;
   private UiBounds resetBtn;
   private UiBounds cancelBtn;
   private UiBounds applyBtn;
   private final List<UiBounds> rowBounds = new ArrayList<>();
   private UiBounds pickerArea;
   private List<RiptideThemeColorScreen.Chan> cachedRows;
   private boolean cachedRowsAdvanced;
   private List<Module> cachedPreview;
   private int cachedPreviewRev = Integer.MIN_VALUE;

   public RiptideThemeColorScreen(Screen parent) {
      super(Component.literal("Theme Color"));
      this.parent = parent;
      this.cfg = RiptideConfig.getGlobal();
      copy(this.cfg.themeColors, this.pending);
      this.rebuildState();
      this.loadSelectedIntoPicker();
   }

   protected void init() {
      if (this.logoPreview == null) {
         this.logoPreview = RiptideThemeTextures.preview(
            Identifier.fromNamespaceAndPath("riptide", "textures/gui/title/riptide_client_logo.png"), RiptideTheme.Channel.ACCENT, 2048
         );
      }

      if (this.panoramaPreview == null) {
         this.panoramaPreview = RiptideThemeTextures.preview(
            Identifier.fromNamespaceAndPath("riptide", "textures/gui/title/background/panorama_0.png"), RiptideTheme.Channel.BACKDROP, 640
         );
      }

      if (this.iconPreview == null) {
         this.iconPreview = RiptideThemeTextures.preview(UiAssets.ICON_PACKET_CATEGORY, RiptideTheme.Channel.ACCENT, 128);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   private List<RiptideThemeColorScreen.Chan> rows() {
      if (this.cachedRows != null && this.cachedRowsAdvanced == this.pending.advanced) {
         return this.cachedRows;
      } else {
         List<RiptideThemeColorScreen.Chan> rows = new ArrayList<>();
         if (!this.pending.advanced) {
            rows.add(new RiptideThemeColorScreen.Chan("Theme Color", () -> this.pending.master, v -> this.pending.master = v, RiptideTheme.Channel.ACCENT));
         } else {
            rows.add(new RiptideThemeColorScreen.Chan("Accent", () -> this.pending.accent, v -> this.pending.accent = v, RiptideTheme.Channel.ACCENT));
            rows.add(new RiptideThemeColorScreen.Chan("Button Fill", () -> this.pending.button, v -> this.pending.button = v, RiptideTheme.Channel.BUTTON));
            rows.add(
               new RiptideThemeColorScreen.Chan("Outline / Border", () -> this.pending.outline, v -> this.pending.outline = v, RiptideTheme.Channel.OUTLINE)
            );
            rows.add(new RiptideThemeColorScreen.Chan("Header", () -> this.pending.header, v -> this.pending.header = v, RiptideTheme.Channel.HEADER));
            rows.add(
               new RiptideThemeColorScreen.Chan("Toggle / Highlight", () -> this.pending.toggle, v -> this.pending.toggle = v, RiptideTheme.Channel.TOGGLE)
            );
            rows.add(new RiptideThemeColorScreen.Chan("Hover / Glow", () -> this.pending.hover, v -> this.pending.hover = v, RiptideTheme.Channel.HOVER));
            rows.add(new RiptideThemeColorScreen.Chan("Text", () -> this.pending.text, v -> this.pending.text = v, RiptideTheme.Channel.TEXT));
            rows.add(
               new RiptideThemeColorScreen.Chan(
                  "Background / Panorama", () -> this.pending.backdrop, v -> this.pending.backdrop = v, RiptideTheme.Channel.BACKDROP
               )
            );
            rows.add(new RiptideThemeColorScreen.Chan("Danger", () -> this.pending.danger, v -> this.pending.danger = v, RiptideTheme.Channel.DANGER));
            rows.add(new RiptideThemeColorScreen.Chan("Success", () -> this.pending.success, v -> this.pending.success = v, RiptideTheme.Channel.SUCCESS));
         }

         this.cachedRows = rows;
         this.cachedRowsAdvanced = this.pending.advanced;
         return rows;
      }
   }

   private void rebuildState() {
      this.pendingState = RiptideTheme.State.from(this.pending);
   }

   private void loadSelectedIntoPicker() {
      List<RiptideThemeColorScreen.Chan> rows = this.rows();
      if (this.selected >= rows.size()) {
         this.selected = 0;
      }

      this.picker.setColor(rows.get(this.selected).get().getAsInt());
      this.picker.onChange = argb -> {
         this.rows().get(this.selected).set().accept(argb);
         this.rebuildState();
      };
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int mx = RiptideUiScale.toVirtualInt(mouseX);
      int my = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         int sw = RiptideUiScale.getVirtualScreenWidth();
         int sh = RiptideUiScale.getVirtualScreenHeight();
         UiContext ctx = UiContexts.overlay(graphics, this.font, mx, my);
         UiRenderer.rect(graphics, UiBounds.of(0, 0, sw, sh), -267843831);
         int margin = 14;
         int contentW = sw - margin * 2;
         int contentH = sh - margin * 2;
         int leftW = Math.max(250, Math.round(contentW * 0.44F));
         int gap = 12;
         int rightW = contentW - leftW - gap;
         UiBounds left = UiBounds.of(margin, margin, leftW, contentH);
         UiBounds right = UiBounds.of(margin + leftW + gap, margin, Math.max(120, rightW), contentH);
         this.renderLeft(ctx, left, mx, my);
         this.renderPreview(ctx, right);
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private void renderLeft(UiContext ctx, UiBounds area, int mx, int my) {
      GuiGraphicsExtractor g = ctx.graphics();
      UiRenderer.frame(g, area, this.RiptideColorsBg(), this.RiptideThemeBorder());
      int x = area.x() + 12;
      int y = area.y() + 10;
      int innerW = area.width() - 24;
      this.draw(g, "THEME COLOR", UiAssets.FONT_TITLE, this.p(-45747, RiptideTheme.Channel.ACCENT), x, y);
      y += 20;
      int tabW = (innerW - 6) / 2;
      this.simpleTab = UiBounds.of(x, y, tabW, 18);
      this.advancedTab = UiBounds.of(x + tabW + 6, y, innerW - tabW - 6, 18);
      this.tab(g, this.simpleTab, "Simple", !this.pending.advanced, this.simpleTab.contains(mx, my));
      this.tab(g, this.advancedTab, "Advanced", this.pending.advanced, this.advancedTab.contains(mx, my));
      y += 24;
      this.draw(g, this.pending.advanced ? "Channels" : "One color recolors the whole theme", UiAssets.FONT_LABEL, this.pText(-4743522), x, y);
      y += 14;
      this.rowBounds.clear();
      List<RiptideThemeColorScreen.Chan> rows = this.rows();
      int rowH = this.pending.advanced ? 17 : 20;

      for (int i = 0; i < rows.size(); i++) {
         RiptideThemeColorScreen.Chan c = rows.get(i);
         UiBounds rb = UiBounds.of(x, y, innerW, rowH - 2);
         this.rowBounds.add(rb);
         boolean sel = i == this.selected;
         boolean over = rb.contains(mx, my);
         UiRenderer.frame(
            g,
            rb,
            sel ? this.p(1428429341, RiptideTheme.Channel.BUTTON) : (over ? this.p(1075845661, RiptideTheme.Channel.BUTTON) : 639113758),
            sel ? this.p(-39836, RiptideTheme.Channel.ACCENT) : this.p(1717972012, RiptideTheme.Channel.OUTLINE)
         );
         UiBounds swatch = UiBounds.of(rb.x() + 4, rb.y() + 3, 24, rb.height() - 6);
         UiRenderer.frame(g, swatch, 0xFF000000 | c.get().getAsInt() & 16777215, -16777216);
         this.draw(g, c.label(), UiAssets.FONT_BODY, sel ? this.pText(-791321) : this.pText(-4743522), rb.x() + 34, rb.y() + (rb.height() - 8) / 2);
         String hex = hex6(c.get().getAsInt());
         int hexW = UiText.width(this.font, hex, UiAssets.FONT_BODY, -4743522);
         this.draw(g, hex, UiAssets.FONT_BODY, this.pText(-4743522), rb.right() - hexW - 6, rb.y() + (rb.height() - 8) / 2);
         y += rowH;
      }

      y += 6;
      this.picker.showAlpha = this.pending.advanced;
      this.picker.preview = this.pendingState;
      this.pickerArea = UiBounds.of(x, y, innerW, this.pending.advanced ? 156 : 142);
      this.picker.render(ctx, this.pickerArea);
      int footerY = area.bottom() - 28;
      int bw = (innerW - 12) / 3;
      this.resetBtn = UiBounds.of(x, footerY, bw, 20);
      this.cancelBtn = UiBounds.of(x + bw + 6, footerY, bw, 20);
      this.applyBtn = UiBounds.of(x + (bw + 6) * 2, footerY, innerW - (bw + 6) * 2, 20);
      this.button(g, this.resetBtn, "Reset", this.p(1717972012, RiptideTheme.Channel.OUTLINE), this.resetBtn.contains(mx, my));
      this.button(g, this.cancelBtn, "Cancel", this.p(1717972012, RiptideTheme.Channel.OUTLINE), this.cancelBtn.contains(mx, my));
      this.button(g, this.applyBtn, "Apply", this.p(-39836, RiptideTheme.Channel.ACCENT), this.applyBtn.contains(mx, my));
   }

   private void renderPreview(UiContext ctx, UiBounds area) {
      GuiGraphicsExtractor g = ctx.graphics();
      UiRenderer.frame(g, area, this.RiptideColorsBg(), this.RiptideThemeBorder());
      int x = area.x() + 14;
      int y = area.y() + 12;
      int innerW = area.width() - 28;
      int logoW = this.logoPreview != null ? Math.min(innerW, 340) : 0;
      int logoH = this.logoPreview != null ? Math.max(1, Math.round(logoW * ((float)this.logoPreview.height() / this.logoPreview.width()))) : 0;
      int middleContentH = 197;
      this.draw(g, "LIVE PREVIEW", UiAssets.FONT_LABEL, this.p(-45747, RiptideTheme.Channel.ACCENT), x, y);
      y += 18;
      UiRenderer.frame(g, UiBounds.of(x, y, innerW, 24), -1475276524, this.p(-5035221, RiptideTheme.Channel.HEADER));
      if (this.iconPreview != null) {
         this.iconPreview.update(this.pendingState);
         g.blit(
            RenderPipelines.GUI_TEXTURED,
            this.iconPreview.id(),
            x + 6,
            y + 5,
            0.0F,
            0.0F,
            14,
            14,
            this.iconPreview.width(),
            this.iconPreview.height(),
            this.iconPreview.width(),
            this.iconPreview.height(),
            -1
         );
      }

      this.draw(g, "Riptide Client", UiAssets.FONT_LABEL, this.p(-2828, RiptideTheme.Channel.TEXT), x + 26, y + 8);
      UiRenderer.rect(g, UiBounds.of(x, y + 24, innerW, 2), this.p(-50373, RiptideTheme.Channel.HEADER));
      y += 34;
      if (this.panoramaPreview != null) {
         this.panoramaPreview.update(this.pendingState);
         int bannerMaxBottom = area.bottom() - 10 - logoH - middleContentH;
         int bh = Math.max(60, Math.min(Math.round(innerW * 0.56F), bannerMaxBottom - y));
         int regionH = Math.max(1, Math.min(this.panoramaPreview.height(), Math.round(this.panoramaPreview.height() * ((float)bh / innerW))));
         float vOff = Math.max(0.0F, (this.panoramaPreview.height() - regionH) / 2.0F);
         g.blit(
            RenderPipelines.GUI_TEXTURED,
            this.panoramaPreview.id(),
            x,
            y,
            0.0F,
            vOff,
            innerW,
            bh,
            this.panoramaPreview.width(),
            regionH,
            this.panoramaPreview.width(),
            this.panoramaPreview.height(),
            -1
         );
         UiRenderer.outline(g, UiBounds.of(x, y, innerW, bh), this.p(-7392975, RiptideTheme.Channel.OUTLINE));
         y += bh + 12;
      }

      this.draw(g, "MODULES", UiAssets.FONT_LABEL, this.pText(-4743522), x, y);
      y += 13;
      List<Module> preview = this.previewModules();

      for (int i = 0; i < preview.size(); i++) {
         Module m = preview.get(i);
         String key = m.keybind() >= 0 ? KeyArgumentType.keyName(m.keybind()) : "";
         y = this.previewModuleRow(g, x, y, innerW, m.name(), key, m.isEnabled(), i == 0, i == 1);
      }

      y += 10;
      this.previewToggle(g, x, y, true);
      this.previewToggle(g, x + 38, y, false);
      this.chip(g, x + 82, y - 1, 66, "Success", this.p(-9448565, RiptideTheme.Channel.SUCCESS));
      this.chip(g, x + 154, y - 1, 66, "Danger", this.p(-1938838, RiptideTheme.Channel.DANGER));
      y += 24;
      UiBounds btn = UiBounds.of(x, y, 84, 16);
      UiRenderer.frame(g, btn, this.p(-1467015388, RiptideTheme.Channel.BUTTON), this.p(-30841, RiptideTheme.Channel.OUTLINE));
      int btnTextW = UiText.width(this.font, "Button", UiAssets.FONT_BODY, -791321);
      this.draw(g, "Button", UiAssets.FONT_BODY, this.p(-2828, RiptideTheme.Channel.TEXT), btn.x() + (84 - btnTextW) / 2, btn.y() + 4);
      UiBounds field = UiBounds.of(x + 92, y, Math.max(60, innerW - 92), 16);
      UiRenderer.frame(g, field, -653192681, this.p(-50373, RiptideTheme.Channel.OUTLINE));
      this.draw(g, "search…", UiAssets.FONT_BODY, this.pText(-4743522), field.x() + 5, field.y() + 4);
      y += 24;
      this.draw(g, "Primary text sample", UiAssets.FONT_BODY, this.p(-791321, RiptideTheme.Channel.TEXT), x, y);
      y += 12;
      this.draw(g, "Secondary / muted text", UiAssets.FONT_BODY, this.p(-4743522, RiptideTheme.Channel.TEXT), x, y);
      y += 12;
      this.draw(g, "Accent text", UiAssets.FONT_BODY, this.p(-45747, RiptideTheme.Channel.ACCENT), x, y);
      y += 18;
      if (this.logoPreview != null) {
         this.logoPreview.update(this.pendingState);
         int lx = x + (innerW - logoW) / 2;
         int ly = area.bottom() - 10 - logoH;
         g.blit(
            RenderPipelines.GUI_TEXTURED,
            this.logoPreview.id(),
            lx,
            ly,
            0.0F,
            0.0F,
            logoW,
            logoH,
            this.logoPreview.width(),
            this.logoPreview.height(),
            this.logoPreview.width(),
            this.logoPreview.height(),
            -1
         );
      }
   }

   private int previewModuleRow(GuiGraphicsExtractor g, int x, int y, int w, String name, String keybind, boolean on, boolean selected, boolean hovered) {
      int h = 18;
      UiBounds row = UiBounds.of(x, y, w, h);
      int fill = selected ? this.p(-1474086869, RiptideTheme.Channel.TOGGLE) : (hovered ? this.p(1327766045, RiptideTheme.Channel.HOVER) : 639113758);
      UiRenderer.rect(g, row, fill);
      if (selected) {
         UiRenderer.rect(g, UiBounds.of(x, y, 2, h), this.p(-10035062, RiptideTheme.Channel.TOGGLE));
      }

      this.draw(g, name, UiAssets.FONT_BODY, on ? this.p(-791321, RiptideTheme.Channel.TEXT) : this.pText(-4743522), x + 8, y + 5);
      if (keybind != null && !keybind.isEmpty()) {
         int nameW = UiText.width(this.font, name, UiAssets.FONT_BODY, -791321);
         this.draw(g, "[" + keybind + "]", UiAssets.FONT_BODY, this.p(-39836, RiptideTheme.Channel.ACCENT), x + 14 + nameW, y + 5);
      }

      this.previewToggle(g, x + w - 38, y + 2, on);
      return y + h + 3;
   }

   private static String hex6(int rgb) {
      rgb &= 16777215;
      char[] out = new char[7];
      out[0] = '#';

      for (int i = 6; i >= 1; i--) {
         int nibble = rgb & 15;
         out[i] = (char)(nibble < 10 ? 48 + nibble : 65 + (nibble - 10));
         rgb >>= 4;
      }

      return new String(out);
   }

   private List<Module> previewModules() {
      int rev = ModuleRegistry.revision();
      if (this.cachedPreview != null && this.cachedPreviewRev == rev) {
         return this.cachedPreview;
      } else {
         List<Module> out = new ArrayList<>();

         for (Module m : ModuleRegistry.all()) {
            if (m.showInModuleMenu()) {
               out.add(m);
               if (out.size() == 4) {
                  break;
               }
            }
         }

         this.cachedPreview = out;
         this.cachedPreviewRev = rev;
         return out;
      }
   }

   private void previewToggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
      UiBounds b = UiBounds.of(x, y, 30, 14);
      Toggle.renderLabeled(UiContexts.overlay(g, this.font, -1, -1), b, null, on, false, "themePreviewToggle:" + x + ":" + y);
   }

   private void chip(GuiGraphicsExtractor g, int x, int y, int w, String label, int color) {
      UiRenderer.frame(g, UiBounds.of(x, y, w, 16), color & 16777215 | 855638016, color);
      this.draw(g, label, UiAssets.FONT_BODY, color, x + 8, y + 4);
   }

   private int p(int color, RiptideTheme.Channel ch) {
      return RiptideTheme.recolor(color, ch, this.pendingState);
   }

   private int pText(int color) {
      return this.p(color, RiptideTheme.Channel.TEXT);
   }

   private int RiptideColorsBg() {
      return -435549684;
   }

   private int RiptideThemeBorder() {
      return this.p(-5035221, RiptideTheme.Channel.OUTLINE);
   }

   private void tab(GuiGraphicsExtractor g, UiBounds b, String label, boolean active, boolean hover) {
      Button.render(UiContexts.overlay(g, this.font, -1, -1), b, label, active ? Button.Tone.PRIMARY : Button.Tone.NORMAL, hover, active);
   }

   private void button(GuiGraphicsExtractor g, UiBounds b, String label, int border, boolean hover) {
      Button.render(
         UiContexts.overlay(g, this.font, -1, -1),
         b,
         label,
         border == this.p(-39836, RiptideTheme.Channel.ACCENT) ? Button.Tone.PRIMARY : Button.Tone.NORMAL,
         hover,
         false
      );
   }

   private void draw(GuiGraphicsExtractor g, String text, Identifier font, int color, int x, int y) {
      UiText.draw(g, this.font, text, font, color, x, y, false);
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
      int mx = RiptideUiScale.toVirtualInt(event.x());
      int my = RiptideUiScale.toVirtualInt(event.y());
      if (event.button() != 0) {
         return true;
      } else if (this.picker.mouseClicked(mx, my, event.button())) {
         return true;
      } else if (this.simpleTab != null && this.simpleTab.contains(mx, my)) {
         this.setMode(false);
         return true;
      } else if (this.advancedTab != null && this.advancedTab.contains(mx, my)) {
         this.setMode(true);
         return true;
      } else {
         for (int i = 0; i < this.rowBounds.size(); i++) {
            if (this.rowBounds.get(i).contains(mx, my)) {
               this.selected = i;
               this.loadSelectedIntoPicker();
               return true;
            }
         }

         if (this.resetBtn != null && this.resetBtn.contains(mx, my)) {
            this.reset();
            return true;
         } else if (this.cancelBtn != null && this.cancelBtn.contains(mx, my)) {
            this.onClose();
            return true;
         } else if (this.applyBtn != null && this.applyBtn.contains(mx, my)) {
            this.apply();
            return true;
         } else {
            return true;
         }
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      this.picker.mouseDragged(RiptideUiScale.toVirtualInt(event.x()), RiptideUiScale.toVirtualInt(event.y()), event.button(), dx, dy);
      return true;
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      this.picker.mouseReleased(RiptideUiScale.toVirtualInt(event.x()), RiptideUiScale.toVirtualInt(event.y()), event.button());
      return true;
   }

   public boolean keyPressed(KeyEvent input) {
      if (this.picker.keyPressed(input)) {
         return true;
      } else if (input.key() == 256) {
         this.onClose();
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   public boolean charTyped(CharacterEvent input) {
      return this.picker.charTyped(input) ? true : super.charTyped(input);
   }

   private void setMode(boolean advanced) {
      if (this.pending.advanced != advanced) {
         if (advanced) {
            int defaultMaster = RiptideTheme.DEFAULTS[RiptideTheme.Channel.ACCENT.ordinal()];
            boolean masterChanged = (this.pending.master & 16777215) != (defaultMaster & 16777215) || (this.pending.master >>> 24 & 0xFF) != 255;
            if (masterChanged) {
               this.pending.accent = this.pending.master;
               this.pending.button = this.pending.master;
               this.pending.outline = this.pending.master;
               this.pending.header = this.pending.master;
               this.pending.toggle = this.pending.master;
               this.pending.hover = this.pending.master;
               this.pending.backdrop = this.pending.master;
               this.pending.danger = this.pending.master;
            } else {
               this.pending.accent = RiptideTheme.DEFAULTS[RiptideTheme.Channel.ACCENT.ordinal()];
               this.pending.button = RiptideTheme.DEFAULTS[RiptideTheme.Channel.BUTTON.ordinal()];
               this.pending.outline = RiptideTheme.DEFAULTS[RiptideTheme.Channel.OUTLINE.ordinal()];
               this.pending.header = RiptideTheme.DEFAULTS[RiptideTheme.Channel.HEADER.ordinal()];
               this.pending.toggle = RiptideTheme.DEFAULTS[RiptideTheme.Channel.TOGGLE.ordinal()];
               this.pending.hover = RiptideTheme.DEFAULTS[RiptideTheme.Channel.HOVER.ordinal()];
               this.pending.backdrop = RiptideTheme.DEFAULTS[RiptideTheme.Channel.BACKDROP.ordinal()];
               this.pending.danger = RiptideTheme.DEFAULTS[RiptideTheme.Channel.DANGER.ordinal()];
            }
         }

         this.pending.advanced = advanced;
         this.selected = 0;
         this.rebuildState();
         this.loadSelectedIntoPicker();
      }
   }

   private void reset() {
      RiptideConfig.ThemeColors def = new RiptideConfig.ThemeColors();
      def.advanced = this.pending.advanced;
      copy(def, this.pending);
      this.rebuildState();
      this.loadSelectedIntoPicker();
      RiptideNotifications.success("Theme reset to defaults");
   }

   private void apply() {
      copy(this.pending, this.cfg.themeColors);
      RiptideTheme.reload();
      Runnable saved = RiptideThemeApplyOverlay.beginJob("Saving config");
      RiptideBackgroundTasks.runTracked("theme-config-save", () -> {
         try {
            this.cfg.save();
         } finally {
            saved.run();
         }
      });
      RiptideNotifications.success("Theme applied");
   }

   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   public void removed() {
      if (this.logoPreview != null) {
         this.logoPreview.close();
         this.logoPreview = null;
      }

      if (this.panoramaPreview != null) {
         this.panoramaPreview.close();
         this.panoramaPreview = null;
      }

      if (this.iconPreview != null) {
         this.iconPreview.close();
         this.iconPreview = null;
      }
   }

   private static void copy(RiptideConfig.ThemeColors from, RiptideConfig.ThemeColors to) {
      to.advanced = from.advanced;
      to.master = from.master;
      to.accent = from.accent;
      to.outline = from.outline;
      to.text = from.text;
      to.toggle = from.toggle;
      to.backdrop = from.backdrop;
      to.success = from.success;
      to.danger = from.danger;
      to.button = from.button;
      to.header = from.header;
      to.hover = from.hover;
   }

   private record Chan(String label, IntSupplier get, IntConsumer set, RiptideTheme.Channel previewChannel) {
   }

   private static final class GradientTex extends AbstractTexture {
      private static final int HUE_BUCKETS = 360;
      private static RiptideThemeColorScreen.GradientTex hueTex;
      private static RiptideThemeColorScreen.GradientTex svTex;
      private static int svBucket = Integer.MIN_VALUE;
      private final Identifier id;
      private final NativeImage pixels;
      private final int w;
      private final int h;

      private GradientTex(Identifier id, String label, int w, int h) {
         this.id = id;
         this.w = w;
         this.h = h;
         this.pixels = new NativeImage(w, h, true);
         this.texture = RenderSystem.getDevice().createTexture("RIPTIDE " + label, 5, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
         this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
         this.textureView = RenderSystem.getDevice().createTextureView(this.texture);
         Minecraft.getInstance().getTextureManager().register(id, this);
      }

      static RiptideThemeColorScreen.GradientTex hue() {
         if (hueTex == null) {
            hueTex = new RiptideThemeColorScreen.GradientTex(Identifier.fromNamespaceAndPath("riptide", "dynamic/theme/picker_hue"), "theme picker hue", 14, 96);

            for (int y = 0; y < hueTex.h; y++) {
               int color = 0xFF000000 | Color.HSBtoRGB(1.0F - (float)y / (hueTex.h - 1), 1.0F, 1.0F) & 16777215;

               for (int x = 0; x < hueTex.w; x++) {
                  hueTex.pixels.setPixel(x, y, color);
               }
            }

            hueTex.upload();
         }

         return hueTex;
      }

      static RiptideThemeColorScreen.GradientTex sv(float hue) {
         int bucket = Math.round(RiptideThemeColorScreen.Picker.clamp01(hue) * 360.0F);
         if (svTex == null) {
            svTex = new RiptideThemeColorScreen.GradientTex(Identifier.fromNamespaceAndPath("riptide", "dynamic/theme/picker_sv"), "theme picker sv", 130, 96);
         }

         if (svBucket != bucket) {
            float hh = bucket / 360.0F;

            for (int x = 0; x < svTex.w; x++) {
               float s = (float)x / (svTex.w - 1);

               for (int y = 0; y < svTex.h; y++) {
                  float v = 1.0F - (float)y / (svTex.h - 1);
                  svTex.pixels.setPixel(x, y, 0xFF000000 | Color.HSBtoRGB(hh, s, v) & 16777215);
               }
            }

            svTex.upload();
            svBucket = bucket;
         }

         return svTex;
      }

      private void upload() {
         RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, this.pixels);
      }

      private void blit(GuiGraphicsExtractor g, UiBounds area) {
         g.blit(RenderPipelines.GUI_TEXTURED, this.id, area.x(), area.y(), 0.0F, 0.0F, area.width(), area.height(), this.w, this.h, this.w, this.h, -1);
      }
   }

   private static final class Picker {
      private static final int SV_W = 130;
      private static final int SV_H = 90;
      private static final int HUE_W = 14;
      private IntConsumer onChange;
      private boolean showAlpha;
      private RiptideTheme.State preview;
      private float hue;
      private float sat;
      private float bri;
      private int r;
      private int g;
      private int b;
      private int a = 255;
      private UiBounds sv;
      private UiBounds hueStrip;
      private UiBounds rSlider;
      private UiBounds gSlider;
      private UiBounds bSlider;
      private UiBounds aSlider;
      private UiBounds hexField;
      private int drag;
      private RiptideChatField hexInput;

      private int pp(int color, RiptideTheme.Channel ch) {
         return RiptideTheme.recolor(color, ch, this.preview);
      }

      void setColor(int argb) {
         this.a = argb >>> 24 & 0xFF;
         this.r = argb >>> 16 & 0xFF;
         this.g = argb >>> 8 & 0xFF;
         this.b = argb & 0xFF;
         float[] hsb = Color.RGBtoHSB(this.r, this.g, this.b, null);
         this.hue = hsb[0];
         this.sat = hsb[1];
         this.bri = hsb[2];
         if (this.hexInput != null) {
            this.hexInput.setFocused(false);
         }

         this.syncHex();
      }

      private int argb() {
         return (this.showAlpha ? this.a : 255) << 24 | this.r << 16 | this.g << 8 | this.b;
      }

      private void ensureHexInput() {
         if (this.hexInput == null) {
            this.hexInput = new RiptideChatField(mc(), mc().font, 0, 0, 20, 16, false);
            this.hexInput.setMaxLength(6);
            this.hexInput.setFilter(next -> next.chars().allMatch(ch -> isHex((char)ch)));
            this.hexInput.setChangedListener(value -> this.applyHex());
            this.hexInput.setText(String.format(Locale.ROOT, "%06X", this.r << 16 | this.g << 8 | this.b));
         }
      }

      private void syncHex() {
         if (this.hexInput != null && !this.hexInput.isFocused()) {
            this.hexInput.setText(String.format(Locale.ROOT, "%06X", this.r << 16 | this.g << 8 | this.b));
         }
      }

      private void fromHsb() {
         int rgb = Color.HSBtoRGB(this.hue, this.sat, this.bri) & 16777215;
         this.r = rgb >> 16 & 0xFF;
         this.g = rgb >> 8 & 0xFF;
         this.b = rgb & 0xFF;
         this.syncHex();
         this.fire();
      }

      private void fromRgb() {
         float[] hsb = Color.RGBtoHSB(this.r, this.g, this.b, null);
         this.hue = hsb[0];
         this.sat = hsb[1];
         this.bri = hsb[2];
         this.syncHex();
         this.fire();
      }

      private void fire() {
         if (this.onChange != null) {
            this.onChange.accept(this.argb());
         }
      }

      void render(UiContext ctx, UiBounds area) {
         GuiGraphicsExtractor g2 = ctx.graphics();
         this.sv = UiBounds.of(area.x(), area.y(), 130, 90);
         this.hueStrip = UiBounds.of(this.sv.right() + 8, area.y(), 14, 90);
         RiptideThemeColorScreen.GradientTex.sv(this.hue).blit(g2, this.sv);
         UiRenderer.outline(g2, this.sv, this.pp(-1721357268, RiptideTheme.Channel.OUTLINE));
         int cx = this.sv.x() + Math.round(this.sat * (this.sv.width() - 1));
         int cy = this.sv.y() + Math.round((1.0F - this.bri) * (this.sv.height() - 1));
         UiRenderer.rect(g2, UiBounds.of(cx - 4, cy, 9, 1), -1);
         UiRenderer.rect(g2, UiBounds.of(cx, cy - 4, 1, 9), -1);
         RiptideThemeColorScreen.GradientTex.hue().blit(g2, this.hueStrip);
         UiRenderer.outline(g2, this.hueStrip, this.pp(-1721357268, RiptideTheme.Channel.OUTLINE));
         int hy = this.hueStrip.y() + Math.round((1.0F - this.hue) * (this.hueStrip.height() - 1));
         UiRenderer.outline(g2, UiBounds.of(this.hueStrip.x() - 1, hy - 1, this.hueStrip.width() + 2, 3), -1);
         int rx = this.hueStrip.right() + 10;
         int rw = Math.max(58, area.right() - rx);
         UiBounds swatch = UiBounds.of(rx, area.y(), rw, 20);
         UiRenderer.rect(g2, swatch, -12961222);
         UiRenderer.rect(g2, swatch, this.argb());
         UiRenderer.outline(g2, swatch, -16777216);
         this.hexField = UiBounds.of(rx, area.y() + 24, rw, 16);
         this.ensureHexInput();
         this.hexInput.setX(this.hexField.x());
         this.hexInput.setY(this.hexField.y());
         this.hexInput.setWidth(this.hexField.width());
         this.hexInput.setHeight(this.hexField.height());
         this.hexInput.render(g2, ctx.mouseX(), ctx.mouseY(), 0.0F);
         int sy = this.sv.bottom() + 8;
         this.rSlider = UiBounds.of(area.x() + 14, sy, area.width() - 52, 9);
         this.gSlider = UiBounds.of(area.x() + 14, sy + 13, area.width() - 52, 9);
         this.bSlider = UiBounds.of(area.x() + 14, sy + 26, area.width() - 52, 9);
         this.slider(g2, this.rSlider, "R", this.r, -43691);
         this.slider(g2, this.gSlider, "G", this.g, -11149961);
         this.slider(g2, this.bSlider, "B", this.b, -10053121);
         if (this.showAlpha) {
            this.aSlider = UiBounds.of(area.x() + 14, sy + 39, area.width() - 52, 9);
            this.slider(g2, this.aSlider, "A", this.a, -4210753);
         } else {
            this.aSlider = null;
         }
      }

      private void slider(GuiGraphicsExtractor g2, UiBounds b2, String label, int value, int accent) {
         UiText.draw(g2, mc().font, label, UiAssets.FONT_BODY, this.pp(-4743522, RiptideTheme.Channel.TEXT), b2.x() - 12, b2.y() + 1, false);
         UiRenderer.frame(g2, b2, -653192681, this.pp(-1721357268, RiptideTheme.Channel.OUTLINE));
         int fillW = Math.max(0, Math.round((b2.width() - 2) * (value / 255.0F)));
         if (fillW > 0) {
            UiRenderer.rect(g2, UiBounds.of(b2.x() + 1, b2.y() + 1, fillW, b2.height() - 2), accent);
         }

         int knobX = b2.x() + 1 + Math.round((b2.width() - 3) * (value / 255.0F));
         UiRenderer.rect(g2, UiBounds.of(knobX, b2.y(), 2, b2.height()), -1);
         UiText.draw(g2, mc().font, Integer.toString(value), UiAssets.FONT_BODY, this.pp(-791321, RiptideTheme.Channel.TEXT), b2.right() + 5, b2.y() + 1, false);
      }

      boolean mouseClicked(int mx, int my, int button) {
         if (this.sv == null) {
            return false;
         } else {
            this.ensureHexInput();
            if (this.hexInput.mouseClicked(mx, my, button)) {
               return true;
            } else if (this.sv.contains(mx, my)) {
               this.drag = 1;
               this.updateSv(mx, my);
               return true;
            } else if (this.hueStrip.contains(mx, my)) {
               this.drag = 2;
               this.updateHue(my);
               return true;
            } else if (this.rSlider.contains(mx, my)) {
               this.drag = 3;
               this.updateChannel(mx, this.rSlider, 0);
               return true;
            } else if (this.gSlider.contains(mx, my)) {
               this.drag = 4;
               this.updateChannel(mx, this.gSlider, 1);
               return true;
            } else if (this.bSlider.contains(mx, my)) {
               this.drag = 5;
               this.updateChannel(mx, this.bSlider, 2);
               return true;
            } else if (this.aSlider != null && this.aSlider.contains(mx, my)) {
               this.drag = 6;
               this.updateChannel(mx, this.aSlider, 3);
               return true;
            } else {
               return false;
            }
         }
      }

      void mouseDragged(int mx, int my, int button, double dx, double dy) {
         if (this.hexInput == null || !this.hexInput.mouseDragged(mx, my, button, dx, dy)) {
            switch (this.drag) {
               case 1:
                  this.updateSv(mx, my);
                  break;
               case 2:
                  this.updateHue(my);
                  break;
               case 3:
                  this.updateChannel(mx, this.rSlider, 0);
                  break;
               case 4:
                  this.updateChannel(mx, this.gSlider, 1);
                  break;
               case 5:
                  this.updateChannel(mx, this.bSlider, 2);
                  break;
               case 6:
                  if (this.aSlider != null) {
                     this.updateChannel(mx, this.aSlider, 3);
                  }
            }
         }
      }

      void mouseReleased(int mx, int my, int button) {
         this.drag = 0;
         if (this.hexInput != null) {
            this.hexInput.mouseReleased(mx, my, button);
         }
      }

      boolean keyPressed(KeyEvent input) {
         if (this.hexInput != null && this.hexInput.isFocused()) {
            int key = input.key();
            if (key != 257 && key != 335 && key != 256) {
               this.hexInput.keyPressed(input);
               return true;
            } else {
               this.hexInput.setFocused(false);
               this.syncHex();
               return true;
            }
         } else {
            return false;
         }
      }

      boolean charTyped(CharacterEvent input) {
         if (this.hexInput != null && this.hexInput.isFocused()) {
            this.hexInput.charTyped(input);
            return true;
         } else {
            return false;
         }
      }

      private void applyHex() {
         String hexText = this.hexInput == null ? "" : this.hexInput.getText();
         if (hexText.length() == 6) {
            try {
               int rgb = Integer.parseInt(hexText, 16) & 16777215;
               this.r = rgb >> 16 & 0xFF;
               this.g = rgb >> 8 & 0xFF;
               this.b = rgb & 0xFF;
               float[] hsb = Color.RGBtoHSB(this.r, this.g, this.b, null);
               this.hue = hsb[0];
               this.sat = hsb[1];
               this.bri = hsb[2];
               this.fire();
            } catch (NumberFormatException var4) {
            }
         }
      }

      private void updateSv(int mx, int my) {
         this.sat = clamp01((float)(mx - this.sv.x()) / Math.max(1, this.sv.width() - 1));
         this.bri = clamp01(1.0F - (float)(my - this.sv.y()) / Math.max(1, this.sv.height() - 1));
         this.fromHsb();
      }

      private void updateHue(int my) {
         this.hue = clamp01(1.0F - (float)(my - this.hueStrip.y()) / Math.max(1, this.hueStrip.height() - 1));
         this.fromHsb();
      }

      private void updateChannel(int mx, UiBounds bounds, int channel) {
         int value = Math.round(clamp01((float)(mx - bounds.x()) / Math.max(1, bounds.width())) * 255.0F);
         if (channel == 0) {
            this.r = value;
         } else if (channel == 1) {
            this.g = value;
         } else {
            if (channel != 2) {
               this.a = value;
               this.fire();
               return;
            }

            this.b = value;
         }

         this.fromRgb();
      }

      private static boolean isHex(char c) {
         return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
      }

      private static float clamp01(float v) {
         return Math.max(0.0F, Math.min(1.0F, v));
      }

      private static Minecraft mc() {
         return Minecraft.getInstance();
      }
   }
}
