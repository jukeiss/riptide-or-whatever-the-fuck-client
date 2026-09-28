package riptide.gui.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.assets.UiAssets;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.modules.RiptideModule;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;

public class RiptideVoiceChatPromptScreen extends Screen {
   private static final Identifier FONT_TITLE = UiAssets.FONT_TITLE;
   private static final Identifier FONT_LABEL = UiAssets.FONT_LABEL;
   private static final long UNLOCK_DELAY_MS = 5000L;
   private static final int TITLE_COLOR = -2828;
   private static final int HEADER_COLOR = -45747;
   private static final int CARD_FILL = -234223092;
   private static final int CARD_BORDER = -46518;
   private static final int DIVIDER_COLOR = 1728006730;
   private static final int PAD = 12;
   private static final int CARD_MAX_WIDTH = 340;
   private static final int BUTTON_HEIGHT = 20;
   private static final int BUTTON_GAP = 6;
   private static final String TITLE_TEXT = "Simple Voice Chat detected";
   private static final String[][] SECTIONS = new String[][]{
      {"Voice chat won't fucking work right now", "Client fakes being vanilla so servers can't see it", "That hides the voicechat channel, so it can't connect"},
      {
            "Switch to Modded and here's what changes",
            "Voice chat starts working",
            "Servers can now tell you're on Fabric",
            "Kept: module hiding, channel filter, payload spoof",
            "Lost: the vanilla disguise. Not full protection anymore"
      }
   };
   private final Screen parent;
   private final CompactTheme theme = new CompactTheme();
   private final long createdAtMs = System.currentTimeMillis();
   private final RiptideVoiceChatPromptScreen.Btn keepVanilla = new RiptideVoiceChatPromptScreen.Btn("Keep Vanilla", () -> this.choose(true));
   private final RiptideVoiceChatPromptScreen.Btn switchModded = new RiptideVoiceChatPromptScreen.Btn("Switch to Modded", () -> this.choose(false));
   private int layoutScreenWidth = -1;
   private int layoutScreenHeight = -1;
   private int wrappedWidth = -1;
   private List<List<String>> wrappedSections = List.of();

   public RiptideVoiceChatPromptScreen(Screen parent) {
      super(Component.literal("Simple Voice Chat detected"));
      this.parent = parent;
   }

   private void choose(boolean keepVanillaMode) {
      RiptideModule module = RiptideModule.get();
      if (module != null) {
         module.setSpoofClientVanilla(keepVanillaMode);
      }

      this.minecraft.gui.setScreen(this.parent);
   }

   public boolean isPauseScreen() {
      return false;
   }

   public boolean shouldCloseOnEsc() {
      return false;
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
   }

   private boolean unlocked() {
      return System.currentTimeMillis() - this.createdAtMs >= 5000L;
   }

   private int secondsLeft() {
      long remaining = 5000L - (System.currentTimeMillis() - this.createdAtMs);
      return (int)Math.max(1.0, Math.ceil(remaining / 1000.0));
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      float uiMouseX = (float)RiptideUiScale.toVirtual(mouseX);
      float uiMouseY = (float)RiptideUiScale.toVirtual(mouseY);
      this.layout();
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         int screenW = RiptideUiScale.getVirtualScreenWidth();
         int screenH = RiptideUiScale.getVirtualScreenHeight();
         UiRenderer.rect(graphics, UiBounds.of(0, 0, screenW, screenH), -939524096);
         int cardW = this.cardWidth();
         int cardX = (screenW - cardW) / 2;
         int cardH = this.cardHeight();
         int cardY = Math.max(8, (screenH - cardH) / 2);
         int innerW = cardW - 24;
         int x = cardX + 12;
         UiRenderer.rect(graphics, UiBounds.of(cardX, cardY, cardW, cardH), -234223092);
         drawThickBorder(graphics, cardX, cardY, cardW, cardH, RiptideTheme.recolor(-46518, RiptideTheme.Channel.OUTLINE), 2);
         int y = cardY + 12 + 2;
         this.drawCentered(graphics, "Simple Voice Chat detected", FONT_TITLE, RiptideTheme.recolor(-2828, RiptideTheme.Channel.TEXT), x, innerW, y);
         y += UiText.fontHeight(FONT_TITLE) + 7;
         UiRenderer.rect(graphics, UiBounds.of(x, y, innerW, 1), RiptideTheme.recolor(1728006730, RiptideTheme.Channel.OUTLINE));
         y += 8;
         int headerH = UiText.fontHeight(FONT_LABEL);
         int lineH = UiText.fontHeight(FONT_LABEL) + 3;
         int bodyColor = this.theme.color(UiTone.MUTED);
         List<List<String>> linesBySection = this.wrappedSections(innerW);

         for (int i = 0; i < SECTIONS.length; i++) {
            UiText.draw(graphics, this.font, SECTIONS[i][0], FONT_LABEL, RiptideTheme.recolor(-45747, RiptideTheme.Channel.ACCENT), x, y, false);
            y += headerH + 3;

            for (String line : linesBySection.get(i)) {
               UiText.draw(graphics, this.font, line, FONT_LABEL, bodyColor, x, y, false);
               y += lineH;
            }

            y += 8;
         }

         boolean ready = this.unlocked();
         this.switchModded.label = ready ? "Switch to Modded" : "Switch to Modded (" + this.secondsLeft() + ")";
         this.renderButton(graphics, this.keepVanilla, uiMouseX, uiMouseY, ready);
         this.renderButton(graphics, this.switchModded, uiMouseX, uiMouseY, ready);
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (event.button() == 0 && this.unlocked()) {
         float mx = (float)RiptideUiScale.toVirtual(event.x());
         float my = (float)RiptideUiScale.toVirtual(event.y());
         this.layout();
         if (this.keepVanilla.contains(mx, my)) {
            this.keepVanilla.action.run();
            return true;
         } else if (this.switchModded.contains(mx, my)) {
            this.switchModded.action.run();
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private int cardWidth() {
      int screenW = RiptideUiScale.getVirtualScreenWidth();
      return Math.max(1, Math.min(Math.max(1, screenW - 12), 340));
   }

   private int cardHeight() {
      int innerW = Math.max(1, this.cardWidth() - 24);
      int headerH = UiText.fontHeight(FONT_LABEL);
      int lineH = UiText.fontHeight(FONT_LABEL) + 3;
      int h = 12 + UiText.fontHeight(FONT_TITLE) + 7 + 1 + 8;
      List<List<String>> linesBySection = this.wrappedSections(innerW);

      for (int i = 0; i < SECTIONS.length; i++) {
         h += headerH + 3 + linesBySection.get(i).size() * lineH + 8;
      }

      return h + 32;
   }

   private void layout() {
      int screenW = RiptideUiScale.getVirtualScreenWidth();
      int screenH = RiptideUiScale.getVirtualScreenHeight();
      if (this.layoutScreenWidth != screenW || this.layoutScreenHeight != screenH) {
         this.layoutScreenWidth = screenW;
         this.layoutScreenHeight = screenH;
         int cardW = this.cardWidth();
         int cardX = (screenW - cardW) / 2;
         int cardH = this.cardHeight();
         int cardY = Math.max(8, (screenH - cardH) / 2);
         int innerW = Math.max(1, cardW - 24);
         int btnW = Math.max(1, (innerW - 6) / 2);
         int btnY = cardY + cardH - 12 - 20;
         int bx = cardX + 12;
         this.keepVanilla.set(bx, btnY, btnW, 20);
         this.switchModded.set(bx + btnW + 6, btnY, cardX + cardW - 12 - (bx + btnW + 6), 20);
      }
   }

   private List<String> wrap(String text, int maxWidth) {
      List<String> lines = new ArrayList<>();
      StringBuilder current = new StringBuilder();

      for (String word : text.split(" ")) {
         String candidate = current.length() == 0 ? word : current + " " + word;
         if (current.length() != 0 && UiText.width(this.font, candidate, FONT_LABEL, -1) > maxWidth) {
            lines.add(current.toString());
            current.setLength(0);
            current.append(word);
         } else {
            current.setLength(0);
            current.append(candidate);
         }
      }

      if (current.length() > 0) {
         lines.add(current.toString());
      }

      return lines;
   }

   private List<List<String>> wrappedSections(int innerWidth) {
      if (this.wrappedWidth == innerWidth && this.wrappedSections.size() == SECTIONS.length) {
         return this.wrappedSections;
      } else {
         List<List<String>> next = new ArrayList<>(SECTIONS.length);

         for (String[] section : SECTIONS) {
            List<String> lines = new ArrayList<>();

            for (int b = 1; b < section.length; b++) {
               lines.addAll(this.wrap("- " + section[b], innerWidth));
            }

            next.add(List.copyOf(lines));
         }

         this.wrappedWidth = innerWidth;
         this.wrappedSections = List.copyOf(next);
         return this.wrappedSections;
      }
   }

   private void drawCentered(GuiGraphicsExtractor graphics, String text, Identifier fontId, int color, int x, int width, int y) {
      int textW = UiText.width(this.font, text, fontId, color);
      UiText.draw(graphics, this.font, text, fontId, color, x + (width - textW) / 2, y, false);
   }

   private void renderButton(GuiGraphicsExtractor graphics, RiptideVoiceChatPromptScreen.Btn btn, float mouseX, float mouseY, boolean enabled) {
      boolean hovered = enabled && btn.contains(mouseX, mouseY);
      UiContext ctx = UiContexts.overlay(graphics, this.font, (int)mouseX, (int)mouseY);
      UiBounds bounds = UiBounds.of(btn.x, btn.y, btn.w, btn.h);
      Button.render(ctx, bounds, btn.label, Button.Tone.SECONDARY, hovered, false);
      if (!enabled) {
         UiRenderer.rect(graphics, bounds, 1711276032);
      }
   }

   private static void drawThickBorder(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color, int t) {
      if (width > 0 && height > 0) {
         UiRenderer.rect(graphics, UiBounds.of(x, y, width, t), color);
         UiRenderer.rect(graphics, UiBounds.of(x, y + height - t, width, t), color);
         UiRenderer.rect(graphics, UiBounds.of(x, y, t, height), color);
         UiRenderer.rect(graphics, UiBounds.of(x + width - t, y, t, height), color);
      }
   }

   private static final class Btn {
      private String label;
      private final Runnable action;
      private int x;
      private int y;
      private int w;
      private int h;

      private Btn(String label, Runnable action) {
         this.label = label;
         this.action = action;
      }

      private void set(int x, int y, int w, int h) {
         this.x = x;
         this.y = y;
         this.w = w;
         this.h = h;
      }

      private boolean contains(float mx, float my) {
         return mx >= this.x && my >= this.y && mx < this.x + this.w && my < this.y + this.h;
      }
   }
}
