package riptide.gui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;
import riptide.util.multi.MultiAutoAccept;
import riptide.util.multi.MultiManager;

public final class RiptideMultiAutoAcceptScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int MASK = -15594737;
   private static final int PANEL_W = 474;
   private static final int PANEL_H = 300;
   private static final int PAD = 12;
   private static final int HEADER_H = 30;
   private static final int FOOTER_H = 40;
   private static final int CARD_GAP = 8;
   private static final int CARD_PAD = 8;
   private static final int ROW_H = 22;
   private static final int HEADER_ROW_H = 22;
   private static final int FIELD_H = 18;
   private static final int LABEL_W = 92;
   private static final int TOGGLE_W = 48;
   private static final int SCROLLBAR_W = 4;
   private static final int TPA_H = 140;
   private static final int TRADE_H = 118;
   private static final int CUSTOM_H = 96;
   private static final int ADD_H = 26;
   private final Screen parent;
   private final Consumer<MultiAutoAccept> onSave;
   private final boolean live;
   private final MultiAutoAccept config;
   private final List<RiptideMultiAutoAcceptScreen.Draft> drafts = new ArrayList<>();
   private final List<Runnable> captureOps = new ArrayList<>();
   private final List<UiBounds> cardFrames = new ArrayList<>();
   private final List<RiptideMultiAutoAcceptScreen.TextDraw> texts = new ArrayList<>();
   private int scroll;
   private int maxScroll;
   private int vpTop;
   private int vpBottom;
   private int vpLeft;
   private int vpRight;
   private boolean scrollable;
   private int doneX;
   private int doneY;
   private int doneW;
   private int doneH;
   private boolean scrollbarDragging;
   private int scrollbarGrabOffset;

   public RiptideMultiAutoAcceptScreen(Screen parent, MultiAutoAccept config, Consumer<MultiAutoAccept> onSave, boolean live) {
      super(Component.literal("Auto-Accept"));
      this.parent = parent;
      this.onSave = onSave;
      this.live = live;
      this.config = new MultiAutoAccept(config);

      for (MultiAutoAccept.Responder responder : this.config.responders) {
         RiptideMultiAutoAcceptScreen.Draft draft = new RiptideMultiAutoAcceptScreen.Draft();
         draft.trigger = responder.trigger();
         draft.response = responder.response();
         draft.useMacro = responder.useMacro();
         draft.macroName = responder.macroName();
         draft.delayMs = responder.delayMs();
         this.drafts.add(draft);
      }
   }

   protected void init() {
      this.rebuild();
   }

   private void rebuild() {
      this.clearWidgets();
      this.captureOps.clear();
      this.cardFrames.clear();
      this.texts.clear();
      this.vpLeft = this.panelX() + 12;
      this.vpRight = this.panelX() + this.panelWidth() - 12;
      this.vpTop = 48;
      this.vpBottom = 18 + this.panelHeight() - 40;
      int viewportH = Math.max(1, this.vpBottom - this.vpTop);
      List<Integer> heights = new ArrayList<>();
      heights.add(140);
      heights.add(118);

      for (int i = 0; i < this.drafts.size(); i++) {
         heights.add(96);
      }

      heights.add(26);
      int contentH = 0;

      for (int h : heights) {
         contentH += h;
      }

      contentH += 8 * Math.max(0, heights.size() - 1);
      this.scrollable = contentH > viewportH;
      this.maxScroll = Math.max(0, contentH - viewportH);
      this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll));
      int cardX = this.vpLeft;
      int cardW = this.vpRight - this.vpLeft - (this.scrollable ? 8 : 0);
      List<RiptideMultiAutoAcceptScreen.Section> sections = new ArrayList<>();
      sections.add(new RiptideMultiAutoAcceptScreen.Section(140, yx -> this.layoutTpa(cardX, yx, cardW)));
      sections.add(new RiptideMultiAutoAcceptScreen.Section(118, yx -> this.layoutTrade(cardX, yx, cardW)));

      for (int i = 0; i < this.drafts.size(); i++) {
         int index = i;
         sections.add(new RiptideMultiAutoAcceptScreen.Section(96, yx -> this.layoutCustom(cardX, yx, cardW, index)));
      }

      sections.add(new RiptideMultiAutoAcceptScreen.Section(26, yx -> this.layoutAdd(cardX, yx, cardW)));
      int y = this.vpTop - this.scroll;

      for (RiptideMultiAutoAcceptScreen.Section section : sections) {
         if (y + section.height() > this.vpTop && y < this.vpBottom) {
            section.layout().accept(y);
         }

         y += section.height() + 8;
      }

      this.doneW = 96;
      this.doneH = 20;
      this.doneX = this.panelX() + this.panelWidth() - 12 - this.doneW;
      this.doneY = 18 + this.panelHeight() - 28;
   }

   private void layoutTpa(int cardX, int top, int cardW) {
      this.frame(cardX, top, cardW, 140, "TPA");
      this.enableToggle(cardX, top, cardW, this.config.tpaEnabled, () -> this.config.tpaEnabled = !this.config.tpaEnabled);
      int innerX = cardX + 8;
      int innerW = cardW - 16;
      int ry = top + 22;
      this.acceptRow(
         innerX,
         ry,
         innerW,
         this.config.tpaUseMacro,
         this.config.tpaAcceptCommand,
         this.config.tpaMacroName,
         "/tpaccept {name}",
         v -> this.config.tpaAcceptCommand = v,
         () -> this.config.tpaUseMacro = !this.config.tpaUseMacro,
         () -> this.pickMacro(this.config.tpaMacroName, n -> this.config.tpaMacroName = n)
      );
      ry += 22;
      this.label("Arm window ms", innerX, ry);
      this.intField(innerX + 92 + 4, ry, innerW - 92 - 4, "15000", this.config.tpaArmWindowMs, v -> this.config.tpaArmWindowMs = v);
      ry += 22;
      this.label("Accept delay ms", innerX, ry);
      this.intField(innerX + 92 + 4, ry, innerW - 92 - 4, "300", this.config.tpaAcceptDelayMs, v -> this.config.tpaAcceptDelayMs = v);
      ry += 22;
      this.label("TPA to me", innerX, ry);
      this.field(innerX + 92 + 4, ry, innerW - 92 - 4, "/tpahere {bot}", this.config.tpaToMeCommand, v -> this.config.tpaToMeCommand = v);
      ry += 22;
      this.label("TPA to bot", innerX, ry);
      this.field(innerX + 92 + 4, ry, innerW - 92 - 4, "/tpa {bot}", this.config.tpaToBotCommand, v -> this.config.tpaToBotCommand = v);
   }

   private void layoutTrade(int cardX, int top, int cardW) {
      this.frame(cardX, top, cardW, 118, "Trade");
      this.enableToggle(cardX, top, cardW, this.config.tradeEnabled, () -> this.config.tradeEnabled = !this.config.tradeEnabled);
      int innerX = cardX + 8;
      int innerW = cardW - 16;
      int ry = top + 22;
      this.acceptRow(
         innerX,
         ry,
         innerW,
         this.config.tradeUseMacro,
         this.config.tradeAcceptCommand,
         this.config.tradeMacroName,
         "/trade accept",
         v -> this.config.tradeAcceptCommand = v,
         () -> this.config.tradeUseMacro = !this.config.tradeUseMacro,
         () -> this.pickMacro(this.config.tradeMacroName, n -> this.config.tradeMacroName = n)
      );
      ry += 22;
      this.label("Arm window ms", innerX, ry);
      this.intField(innerX + 92 + 4, ry, innerW - 92 - 4, "15000", this.config.tradeArmWindowMs, v -> this.config.tradeArmWindowMs = v);
      ry += 22;
      this.label("Accept delay ms", innerX, ry);
      this.intField(innerX + 92 + 4, ry, innerW - 92 - 4, "300", this.config.tradeAcceptDelayMs, v -> this.config.tradeAcceptDelayMs = v);
      ry += 22;
      this.label("Trade command", innerX, ry);
      this.field(innerX + 92 + 4, ry, innerW - 92 - 4, "/trade {bot}", this.config.tradeCommand, v -> this.config.tradeCommand = v);
   }

   private void layoutCustom(int cardX, int top, int cardW, int index) {
      RiptideMultiAutoAcceptScreen.Draft draft = this.drafts.get(index);
      this.frame(cardX, top, cardW, 96, "Custom " + (index + 1));
      this.addStyled(cardX + cardW - 8 - 64, top + 2, 64, 18, "Remove", Button.Tone.NORMAL, b -> {
         this.capture();
         if (index < this.drafts.size()) {
            this.drafts.remove(index);
         }

         this.applyLive();
         this.rebuild();
      });
      int innerX = cardX + 8;
      int innerW = cardW - 16;
      int ry = top + 22;
      this.label("Trigger text", innerX, ry);
      this.field(innerX + 92 + 4, ry, innerW - 92 - 4, "text to watch for", draft.trigger, v -> draft.trigger = v);
      ry += 22;
      this.acceptRow(
         innerX,
         ry,
         innerW,
         draft.useMacro,
         draft.response,
         draft.macroName,
         "/reply",
         v -> draft.response = v,
         () -> draft.useMacro = !draft.useMacro,
         () -> this.pickMacro(draft.macroName, n -> draft.macroName = n)
      );
      ry += 22;
      this.label("Delay ms", innerX, ry);
      this.intField(innerX + 92 + 4, ry, innerW - 92 - 4, "300", draft.delayMs, v -> draft.delayMs = v);
   }

   private void layoutAdd(int cardX, int top, int cardW) {
      if (this.drafts.size() >= 6) {
         this.texts.add(new RiptideMultiAutoAcceptScreen.TextDraw("Maximum of 6 custom responders reached.", cardX, top + 6, true, cardW));
      } else {
         this.addStyled(cardX, top, cardW, 20, "+ Add custom responder", Button.Tone.SECONDARY, b -> {
            this.capture();
            if (this.drafts.size() < 6) {
               this.drafts.add(new RiptideMultiAutoAcceptScreen.Draft());
            }

            this.applyLive();
            this.rebuild();
         });
      }
   }

   private void acceptRow(
      int innerX,
      int y,
      int innerW,
      boolean useMacro,
      String textValue,
      String macroName,
      String hint,
      Consumer<String> textSink,
      Runnable toggleMode,
      Runnable pick
   ) {
      this.label("Accept", innerX, y);
      int fieldX = innerX + 92 + 4;
      int fieldW = innerW - 92 - 4 - 48 - 4;
      if (useMacro) {
         this.addStyled(fieldX, y, fieldW, 18, "Macro: " + orNone(macroName), Button.Tone.NORMAL, b -> pick.run());
      } else {
         this.field(fieldX, y, fieldW, hint, textValue, textSink);
      }

      this.addStyled(fieldX + fieldW + 4, y, 48, 18, useMacro ? "Macro" : "Text", useMacro ? Button.Tone.PRIMARY : Button.Tone.NORMAL, b -> {
         this.capture();
         toggleMode.run();
         this.applyLive();
         this.rebuild();
      });
   }

   private void enableToggle(int cardX, int top, int cardW, boolean on, Runnable toggle) {
      this.addStyled(cardX + cardW - 8 - 46, top + 2, 46, 18, on ? "On" : "Off", on ? Button.Tone.SUCCESS : Button.Tone.NORMAL, b -> {
         this.capture();
         toggle.run();
         this.applyLive();
         this.rebuild();
      });
   }

   private void frame(int x, int y, int w, int h, String title) {
      this.cardFrames.add(UiBounds.of(x, y, w, h));
      this.texts.add(new RiptideMultiAutoAcceptScreen.TextDraw(title, x + 8, y + 7, false, w - 16 - 50));
   }

   private void label(String text, int x, int y) {
      this.texts.add(new RiptideMultiAutoAcceptScreen.TextDraw(text, x, y + 5, true, 92));
   }

   private void field(int x, int y, int w, String hint, String value, Consumer<String> sink) {
      EditBox box = new EditBox(this.font, x, y, Math.max(1, w), 18, Component.literal(hint));
      box.setMaxLength(96);
      box.setValue(value == null ? "" : value);
      box.setHint(Component.literal(hint));
      this.addRenderableWidget(box);
      this.captureOps.add(() -> sink.accept(box.getValue()));
   }

   private void intField(int x, int y, int w, String hint, int value, IntConsumer sink) {
      EditBox box = new EditBox(this.font, x, y, Math.max(1, w), 18, Component.literal(hint));
      box.setMaxLength(8);
      box.setValue(Integer.toString(value));
      box.setHint(Component.literal(hint));
      this.addRenderableWidget(box);
      this.captureOps.add(() -> {
         try {
            sink.accept(Integer.parseInt(box.getValue().trim()));
         } catch (RuntimeException var3x) {
         }
      });
   }

   private RiptideStyledButton addStyled(int x, int y, int w, int h, String label, Button.Tone tone, OnPress press) {
      Button.Tone interactive = tone == Button.Tone.NORMAL ? Button.Tone.SECONDARY : tone;
      String fit = UiText.trimToWidthEllipsis(
         this.font, label, Math.max(1, w - 6), THEME.fontFor(UiTone.BODY), RiptideTheme.recolor(-855310, RiptideTheme.Channel.TEXT)
      );
      RiptideStyledButton button = new RiptideStyledButton(x, y, Math.max(1, w), h, Component.literal(fit), interactive, press);
      this.addRenderableWidget(button);
      return button;
   }

   private void capture() {
      for (Runnable op : this.captureOps) {
         op.run();
      }

      this.config.responders.clear();

      for (RiptideMultiAutoAcceptScreen.Draft draft : this.drafts) {
         this.config.responders.add(new MultiAutoAccept.Responder(draft.trigger, draft.response, draft.useMacro, draft.macroName, draft.delayMs));
      }

      this.config.normalize();
   }

   private void pickMacro(String current, Consumer<String> setter) {
      this.capture();
      this.minecraft.gui.setScreen(new RiptideMultiMacroPickerScreen(this, current, name -> {
         setter.accept(name == null ? "" : name);
         this.applyLive();
      }));
   }

   private void applyLive() {
      if (this.live && this.onSave != null) {
         this.onSave.accept(new MultiAutoAccept(this.config));
      }
   }

   private void finish() {
      this.capture();
      if (this.onSave != null) {
         this.onSave.accept(new MultiAutoAccept(this.config));
      }

      this.minecraft.gui.setScreen(this.parent);
   }

   public void onClose() {
      this.finish();
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int vmx = RiptideUiScale.toVirtualInt(mouseX);
      int vmy = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         UiRenderer.rect(graphics, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), RiptideTheme.recolor(-15856112, RiptideTheme.Channel.BACKDROP));
         int x = this.panelX();
         int panelW = this.panelWidth();
         int panelH = this.panelHeight();
         int text = RiptideTheme.recolor(-855310, RiptideTheme.Channel.TEXT);
         int muted = RiptideTheme.recolor(-6645094, RiptideTheme.Channel.TEXT);
         UiRenderer.frame(
            graphics,
            UiBounds.of(x, 18, panelW, panelH),
            RiptideTheme.recolor(-401074149, RiptideTheme.Channel.BUTTON),
            RiptideTheme.recolor(-9553346, RiptideTheme.Channel.OUTLINE)
         );
         this.drawText(graphics, "Auto-Accept - TPA & Trade", x + 14, 26, text, panelW - 28);
         RiptideUiScale.enableOverlayScissor(graphics, this.vpLeft - 2, this.vpTop, this.vpRight + 4 + 2, this.vpBottom);
         int cardFill = RiptideTheme.recolor(-15856112, RiptideTheme.Channel.BUTTON);
         int cardBorder = RiptideTheme.recolor(-9553346, RiptideTheme.Channel.OUTLINE);

         for (UiBounds card : this.cardFrames) {
            UiRenderer.frame(graphics, card, cardFill, cardBorder);
         }

         for (RiptideMultiAutoAcceptScreen.TextDraw td : this.texts) {
            this.drawText(graphics, td.text(), td.x(), td.y(), td.muted() ? muted : text, td.width());
         }

         super.extractRenderState(graphics, vmx, vmy, delta);
         graphics.disableScissor();
         CompactScrollbar.Metrics metrics = this.scrollbarMetrics();
         CompactScrollbar.draw(graphics, metrics, metrics.contains(vmx, vmy), this.scrollbarDragging);
         int tipX = x + 14;
         int tipW = Math.max(1, this.doneX - tipX - 8);
         List<String> tipLines = this.wrapToWidth("{bot} = bot name. {name}/{me} = your on-server name. Bots watch chat for these.", tipW, muted);
         int tipY = tipLines.size() > 1 ? this.doneY + 1 : this.doneY + 6;

         for (int i = 0; i < tipLines.size(); i++) {
            this.drawText(graphics, tipLines.get(i), tipX, tipY + i * 10, muted, tipW);
         }

         UiBounds doneBounds = UiBounds.of(this.doneX, this.doneY, this.doneW, this.doneH);
         UiRenderer.rect(graphics, doneBounds, RiptideTheme.recolor(-15594737, RiptideTheme.Channel.BUTTON));
         boolean doneHover = vmx >= this.doneX && vmx < this.doneX + this.doneW && vmy >= this.doneY && vmy < this.doneY + this.doneH;
         Button.render(UiContexts.overlay(graphics, this.font, vmx, vmy), doneBounds, "Done", Button.Tone.PRIMARY, doneHover, false);
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private void drawText(GuiGraphicsExtractor graphics, String value, int x, int y, int color, int width) {
      String shown = UiText.trimToWidthEllipsis(this.font, MultiManager.singleLine(value, 96), Math.max(1, width), THEME.fontFor(UiTone.BODY), color);
      graphics.text(this.font, Component.literal(shown), x, y, color, false);
   }

   private List<String> wrapToWidth(String value, int width, int color) {
      List<String> lines = new ArrayList<>();
      StringBuilder line = new StringBuilder();

      for (String word : value.split(" ")) {
         String candidate = line.isEmpty() ? word : line + " " + word;
         if (!line.isEmpty() && UiText.width(this.font, candidate, THEME.fontFor(UiTone.BODY), color) > width) {
            lines.add(line.toString());
            line = new StringBuilder(word);
         } else {
            line = new StringBuilder(candidate);
         }
      }

      if (!line.isEmpty()) {
         lines.add(line.toString());
      }

      while (lines.size() > 2) {
         String tail = lines.remove(2);
         lines.set(1, lines.get(1) + " " + tail);
      }

      return lines;
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
      MouseButtonEvent virtual = virtualEvent(event);
      int vmx = (int)virtual.x();
      int vmy = (int)virtual.y();
      if (vmx >= this.doneX && vmx < this.doneX + this.doneW && vmy >= this.doneY && vmy < this.doneY + this.doneH) {
         this.finish();
         return true;
      } else {
         CompactScrollbar.Metrics metrics = this.scrollbarMetrics();
         if (metrics.hasScroll() && metrics.contains(vmx, vmy)) {
            this.scrollbarDragging = true;
            this.scrollbarGrabOffset = metrics.overThumb(vmx, vmy) ? vmy - metrics.thumbY() : metrics.thumbHeight() / 2;
            this.setScroll(CompactScrollbar.scrollFromThumb(metrics, vmy, this.scrollbarGrabOffset));
            return true;
         } else {
            return vmy >= this.vpTop && vmy < this.vpBottom ? super.mouseClicked(virtual, doubled) : true;
         }
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      this.scrollbarDragging = false;
      return super.mouseReleased(virtualEvent(event));
   }

   public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      MouseButtonEvent virtual = virtualEvent(event);
      if (this.scrollbarDragging) {
         this.setScroll(CompactScrollbar.scrollFromThumb(this.scrollbarMetrics(), virtual.y(), this.scrollbarGrabOffset));
         return true;
      } else {
         return super.mouseDragged(virtual, RiptideUiScale.toVirtual(dragX), RiptideUiScale.toVirtual(dragY));
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
      int vmx = RiptideUiScale.toVirtualInt(mouseX);
      int vmy = RiptideUiScale.toVirtualInt(mouseY);
      if (vmx >= this.panelX() && vmx < this.panelX() + this.panelWidth() && vmy >= this.vpTop && vmy < this.vpBottom && this.maxScroll > 0) {
         this.capture();
         this.scroll = Math.max(0, Math.min(this.maxScroll, this.scroll - (int)(vertical * 22.0)));
         this.rebuild();
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
      }
   }

   private CompactScrollbar.Metrics scrollbarMetrics() {
      int viewportH = Math.max(1, this.vpBottom - this.vpTop);
      return CompactScrollbar.compute(viewportH + this.maxScroll, viewportH, this.vpRight + 2, this.vpTop, 4, viewportH, this.scroll);
   }

   private void setScroll(int value) {
      this.capture();
      this.scroll = Math.max(0, Math.min(this.maxScroll, value));
      this.rebuild();
   }

   private int panelX() {
      return Math.max(1, (this.screenWidth() - this.panelWidth()) / 2);
   }

   private int panelWidth() {
      return Math.max(1, Math.min(474, this.screenWidth() - 36));
   }

   private int panelHeight() {
      return Math.max(1, Math.min(300, this.screenHeight() - 36));
   }

   private static String orNone(String name) {
      return name != null && !name.isBlank() ? name : "none";
   }

   private static final class Draft {
      String trigger = "";
      String response = "";
      boolean useMacro = false;
      String macroName = "";
      int delayMs = 300;
   }

   private record Section(int height, IntConsumer layout) {
   }

   private record TextDraw(String text, int x, int y, boolean muted, int width) {
   }
}
