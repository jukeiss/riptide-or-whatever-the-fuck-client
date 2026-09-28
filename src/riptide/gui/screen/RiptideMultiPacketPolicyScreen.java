package riptide.gui.screen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.util.RiptideConfig;
import riptide.util.RiptidePacketNamer;
import riptide.util.RiptidePacketRegistry;
import riptide.util.RiptidePacketSelectorOverlay;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;
import riptide.util.multi.MultiAutoAccept;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiManualPackets;
import riptide.util.multi.MultiPacketPolicy;

public final class RiptideMultiPacketPolicyScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int PANEL_W = 470;
   private static final int PANEL_H = 236;
   private final Screen parent;
   private final Consumer<MultiPacketPolicy> save;
   private final boolean live;
   private final List<RiptideMultiPacketPolicyScreen.ToggleRow> toggleRows = new ArrayList<>();
   private MultiPacketPolicy policy;
   private MultiAutoAccept autoAccept;
   private final Consumer<MultiAutoAccept> saveAutoAccept;
   private RiptidePacketSelectorOverlay selector;

   public RiptideMultiPacketPolicyScreen(
      Screen parent,
      MultiPacketPolicy policy,
      Consumer<MultiPacketPolicy> save,
      MultiAutoAccept autoAccept,
      Consumer<MultiAutoAccept> saveAutoAccept,
      boolean live
   ) {
      super(Component.literal("Multi Packet Policy"));
      this.parent = parent;
      this.policy = new MultiPacketPolicy(policy);
      this.save = save;
      this.autoAccept = new MultiAutoAccept(autoAccept);
      this.saveAutoAccept = saveAutoAccept;
      this.live = live;
   }

   protected void init() {
      this.rebuild();
   }

   private void rebuild() {
      this.clearWidgets();
      this.toggleRows.clear();
      int x = this.panelX();
      int y = 46;
      this.addToggle(
         x, y, "Gravity", "Bot falls to and rests on the ground. Off: it holds where the server puts it.", this.policy.gravity(), this.policy::setGravity
      );
      y += 30;
      this.addToggle(x, y, "Auto look", "Send a look packet every second (anti-AFK).", this.policy.autoLook(), this.policy::setAutoLook);
      y += 30;
      this.addToggle(x, y, "Auto swing", "Swing the arm every second (anti-AFK).", this.policy.autoSwing(), this.policy::setAutoSwing);
      y += 36;
      int inner = this.panelWidth() - 28;
      int gap = 4;
      int each = Math.max(1, (inner - gap * 2) / 3);
      this.addStyled(x + 14, y, each, 20, "Block C2S", Button.Tone.DANGER, button -> this.openBlocklist(MultiPacketPolicy.Direction.C2S));
      this.addStyled(x + 14 + each + gap, y, each, 20, "Block S2C", Button.Tone.DANGER, button -> this.openBlocklist(MultiPacketPolicy.Direction.S2C));
      this.addStyled(x + 14 + (each + gap) * 2, y, inner - (each + gap) * 2, 20, "Clear", Button.Tone.NORMAL, button -> {
         this.policy.setBlocklist(List.of());
         this.applyLive();
         this.rebuild();
      });
      y += 28;
      this.addStyled(
         x + 14,
         y,
         inner,
         20,
         "Auto-Accept: TPA & Trade...",
         Button.Tone.PRIMARY,
         button -> this.minecraft.gui.setScreen(new RiptideMultiAutoAcceptScreen(this, this.autoAccept, cfg -> {
            this.autoAccept = new MultiAutoAccept(cfg);
            if (this.saveAutoAccept != null) {
               this.saveAutoAccept.accept(cfg);
            }
         }, this.live))
      );
      int botY = 18 + this.panelHeight() - 28;
      boolean tips = RiptideConfig.getGlobal().multiShowTooltips;
      this.addStyled(x + 14, botY, 118, 20, tips ? "Hover tips: On" : "Hover tips: Off", tips ? Button.Tone.SUCCESS : Button.Tone.NORMAL, button -> {
         RiptideConfig config = RiptideConfig.getGlobal();
         config.multiShowTooltips = !config.multiShowTooltips;
         config.save();
         this.rebuild();
      });
      this.addStyled(x + this.panelWidth() - 14 - 96, 18 + this.panelHeight() - 28, 96, 20, "Done", Button.Tone.PRIMARY, button -> this.finish());
   }

   private void addToggle(int x, int y, String label, String description, boolean on, Consumer<Boolean> setter) {
      this.toggleRows.add(new RiptideMultiPacketPolicyScreen.ToggleRow(label, description, y));
      this.addStyled(x + this.panelWidth() - 14 - 60, y + 3, 60, 18, on ? "On" : "Off", on ? Button.Tone.SUCCESS : Button.Tone.NORMAL, button -> {
         setter.accept(!on);
         this.applyLive();
         this.rebuild();
      });
   }

   private int panelX() {
      return Math.max(1, (this.screenWidth() - this.panelWidth()) / 2);
   }

   private int panelWidth() {
      return Math.max(1, Math.min(470, this.screenWidth() - 36));
   }

   private int panelHeight() {
      return Math.max(1, Math.min(236, this.screenHeight() - 36));
   }

   private void openBlocklist(MultiPacketPolicy.Direction direction) {
      this.ensureSelector();
      Set<Class<? extends Packet<?>>> selected = new LinkedHashSet<>();

      for (MultiPacketPolicy.Rule rule : this.policy.blocklist()) {
         if (rule.direction() == direction) {
            Class<? extends Packet<?>> packet = RiptidePacketRegistry.getPacket(rule.packetClass());
            if (packet != null) {
               selected.add(packet);
            }
         }
      }

      BiConsumer<Class<? extends Packet<?>>, Boolean> callback = (packetClass, enabled) -> {
         List<MultiPacketPolicy.Rule> rules = new ArrayList<>(this.policy.blocklist());
         rules.removeIf(rulex -> rulex.direction() == direction && rulex.packetClass().equals(packetClass.getName()));
         if (enabled) {
            rules.add(new MultiPacketPolicy.Rule(direction, packetClass.getName()));
         }

         this.policy.setBlocklist(rules);
         this.applyLive();
      };
      if (direction == MultiPacketPolicy.Direction.S2C) {
         this.selector.openToggleS2C(callback, selected);
      } else {
         this.selector.openToggleC2S(callback, selected, MultiManualPackets.unsafeC2S());
      }
   }

   private void ensureSelector() {
      if (this.selector == null) {
         this.selector = new RiptidePacketSelectorOverlay(this.font);
      }
   }

   private void applyLive() {
      if (this.live && this.save != null) {
         this.save.accept(new MultiPacketPolicy(this.policy));
      }
   }

   private void finish() {
      if (!this.live && this.save != null) {
         this.save.accept(new MultiPacketPolicy(this.policy));
      }

      this.minecraft.gui.setScreen(this.parent);
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int virtualMouseX = RiptideUiScale.toVirtualInt(mouseX);
      int virtualMouseY = RiptideUiScale.toVirtualInt(mouseY);
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
         this.drawFitted(graphics, "Advanced - gravity & blocklist", x + 14, 26, panelW - 28, text);

         for (RiptideMultiPacketPolicyScreen.ToggleRow row : this.toggleRows) {
            this.drawFitted(graphics, row.label(), x + 14, row.y() + 2, panelW - 94, text);
            this.drawFitted(graphics, row.description(), x + 14, row.y() + 13, panelW - 28, muted);
         }

         this.drawFitted(graphics, "Empty blocklist = everything allowed.", x + 14, 18 + panelH - 44, panelW - 28, muted);
         super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
         if (this.selector != null && this.selector.isVisible()) {
            this.selector.render(graphics, virtualMouseX, virtualMouseY, delta);
         }
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (this.selector != null && this.selector.isVisible()) {
         this.selector.mouseClicked((float)virtualEvent.x(), (float)virtualEvent.y(), virtualEvent.button());
         return true;
      } else {
         return super.mouseClicked(virtualEvent, doubled);
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (this.selector != null && this.selector.isVisible()) {
         this.selector.mouseReleased((float)virtualEvent.x(), (float)virtualEvent.y(), virtualEvent.button());
         return true;
      } else {
         return super.mouseReleased(virtualEvent);
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (this.selector != null && this.selector.isVisible()) {
         this.selector
            .mouseDragged(
               (float)virtualEvent.x(),
               (float)virtualEvent.y(),
               virtualEvent.button(),
               (float)RiptideUiScale.toVirtual(dragX),
               (float)RiptideUiScale.toVirtual(dragY)
            );
         return true;
      } else {
         return super.mouseDragged(virtualEvent, RiptideUiScale.toVirtual(dragX), RiptideUiScale.toVirtual(dragY));
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
      double vx = RiptideUiScale.toVirtual(mouseX);
      double vy = RiptideUiScale.toVirtual(mouseY);
      if (this.selector != null && this.selector.isVisible()) {
         this.selector.mouseScrolled((float)vx, (float)vy, vertical);
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
      }
   }

   public boolean keyPressed(KeyEvent event) {
      if (this.selector != null && this.selector.isVisible()) {
         this.selector.keyPressed(event.key(), event.scancode(), event.modifiers());
         return true;
      } else {
         return super.keyPressed(event);
      }
   }

   public boolean charTyped(CharacterEvent event) {
      if (this.selector != null && this.selector.isVisible()) {
         this.selector.charTyped((char)event.codepoint(), 0);
         return true;
      } else {
         return super.charTyped(event);
      }
   }

   public void onClose() {
      this.finish();
   }

   private RiptideStyledButton addStyled(int x, int y, int w, int h, String text, Button.Tone tone, OnPress press) {
      String label = UiText.trimToWidthEllipsis(
         this.font, MultiManager.singleLine(text, 64), Math.max(1, w - 8), THEME.fontFor(UiTone.BODY), RiptideTheme.recolor(-855310, RiptideTheme.Channel.TEXT)
      );
      Button.Tone interactiveTone = tone == Button.Tone.NORMAL ? Button.Tone.SECONDARY : tone;
      RiptideStyledButton button = new RiptideStyledButton(x, y, w, h, Component.literal(label), interactiveTone, press);
      this.addRenderableWidget(button);
      return button;
   }

   private static String shortPacket(String className) {
      Class<? extends Packet<?>> packet = RiptidePacketRegistry.getPacket(className);
      if (packet != null) {
         return RiptidePacketNamer.getFriendlyName(packet);
      } else {
         int dot = className.lastIndexOf(46);
         return dot < 0 ? className : className.substring(dot + 1);
      }
   }

   private void drawFitted(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int color) {
      String safe = UiText.trimToWidthEllipsis(this.font, MultiManager.singleLine(text, 96), Math.max(1, width), THEME.fontFor(UiTone.BODY), color);
      graphics.text(this.font, Component.literal(safe), x, y, color, false);
   }

   private record ToggleRow(String label, String description, int y) {
   }
}
