package riptide.gui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.util.RiptidePacketSelectorOverlay;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiManualPackets;
import riptide.util.multi.MultiQuickAction;

public final class RiptideMultiQuickActionScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private final Screen parent;
   private final int slot;
   private final Consumer<MultiQuickAction> save;
   private final Function<MultiQuickAction, MultiManager.BroadcastResult> test;
   private final String initialName;
   private final List<String[]> editSteps = new ArrayList<>();
   private final List<EditBox> argFields = new ArrayList<>();
   private EditBox nameField;
   private RiptidePacketSelectorOverlay selector;
   private String status = "";
   private int statusColor = -6645094;

   public RiptideMultiQuickActionScreen(
      Screen parent, int slot, MultiQuickAction action, Consumer<MultiQuickAction> save, Function<MultiQuickAction, MultiManager.BroadcastResult> test
   ) {
      super(Component.literal("Quick Action"));
      this.parent = parent;
      this.slot = slot;
      this.save = save;
      this.test = test;
      this.initialName = action == null ? "" : action.name;
      if (action != null) {
         for (MultiQuickAction.Step step : action.steps) {
            this.editSteps.add(new String[]{step.packetClass(), step.arguments()});
         }
      }

      if (this.editSteps.isEmpty()) {
         this.editSteps.add(new String[]{"", ""});
      }
   }

   protected void init() {
      this.rebuild();
   }

   private void rebuild() {
      String name = this.nameField == null ? this.initialName : this.nameField.getValue();
      this.captureArgs();
      this.clearWidgets();
      this.argFields.clear();
      int w = this.panelWidth();
      int x = (this.screenWidth() - w) / 2;
      this.nameField = new EditBox(this.font, x + 14, 40, w - 28, 18, Component.literal("Name"));
      this.nameField.setMaxLength(32);
      this.nameField.setHint(Component.literal("Preset name (optional)"));
      this.nameField.setValue(name);
      this.addRenderableWidget(this.nameField);
      int y = 78;

      for (int i = 0; i < this.editSteps.size(); i++) {
         int index = i;
         String[] step = this.editSteps.get(i);
         String packetLabel = step[0].isBlank() ? "Choose packet" : MultiQuickAction.shortLabel(step[0]);
         this.addStyled(x + 14, y, 150, 18, packetLabel, step[0].isBlank() ? Button.Tone.PRIMARY : Button.Tone.NORMAL, b -> this.openPacketSelector(index));
         EditBox args = new EditBox(this.font, x + 170, y, w - 170 - 14 - 28, 18, Component.literal("Args"));
         args.setMaxLength(2048);
         args.setHint(Component.literal("packet args"));
         args.setValue(step[1]);
         this.addRenderableWidget(args);
         this.argFields.add(args);
         this.addStyled(x + w - 14 - 22, y, 22, 18, "X", Button.Tone.DANGER, b -> this.removeStep(index));
         y += 20;
      }

      if (this.editSteps.size() < 6) {
         this.addStyled(x + 14, y, 110, 18, "+ Add packet", Button.Tone.NORMAL, b -> this.addStep());
      }

      int by = this.screenHeight() - 34;
      int gap = 4;
      int each = Math.max(1, (w - 28 - gap * 3) / 4);
      int fx = x + 14;
      this.addStyled(fx, by, each, 18, "Save", Button.Tone.SUCCESS, b -> this.saveDraft());
      this.addStyled(fx + each + gap, by, each, 18, "Test", Button.Tone.PRIMARY, b -> this.testDraft());
      this.addStyled(fx + (each + gap) * 2, by, each, 18, "Clear", Button.Tone.DANGER, b -> this.clearDraft());
      this.addStyled(fx + (each + gap) * 3, by, w - 14 - (fx + (each + gap) * 3 - x), 18, "Cancel", Button.Tone.NORMAL, b -> this.onClose());
   }

   private void captureArgs() {
      for (int i = 0; i < this.argFields.size() && i < this.editSteps.size(); i++) {
         this.editSteps.get(i)[1] = this.argFields.get(i).getValue();
      }
   }

   private void openPacketSelector(int index) {
      if (this.selector == null) {
         this.selector = new RiptidePacketSelectorOverlay(this.font);
      }

      this.selector.openC2S(packetClass -> {
         this.editSteps.get(index)[0] = packetClass.getName();
         if (this.nameField == null || this.nameField.getValue().isBlank()) {
            String label = MultiQuickAction.shortLabel(packetClass.getName());
            if (this.nameField != null) {
               this.nameField.setValue(label);
            }
         }

         this.status = "Selected";
         this.statusColor = -13248397;
         this.rebuild();
      }, MultiManualPackets.unsafeC2S(), true);
   }

   private void addStep() {
      this.captureArgs();
      if (this.editSteps.size() < 6) {
         this.editSteps.add(new String[]{"", ""});
      }

      this.rebuild();
   }

   private void removeStep(int index) {
      this.captureArgs();
      if (index >= 0 && index < this.editSteps.size()) {
         this.editSteps.remove(index);
      }

      if (this.editSteps.isEmpty()) {
         this.editSteps.add(new String[]{"", ""});
      }

      this.rebuild();
   }

   private MultiQuickAction buildAction() {
      this.captureArgs();
      MultiQuickAction action = new MultiQuickAction();
      action.name = this.nameField == null ? "" : this.nameField.getValue();

      for (String[] step : this.editSteps) {
         if (!step[0].isBlank()) {
            action.steps.add(new MultiQuickAction.Step(step[0], step[1]));
         }
      }

      action.normalize();
      return action;
   }

   private void saveDraft() {
      if (this.save != null) {
         this.save.accept(this.buildAction());
      }

      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   private void clearDraft() {
      if (this.save != null) {
         this.save.accept(new MultiQuickAction());
      }

      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   private void testDraft() {
      MultiQuickAction action = this.buildAction();
      if (action.empty()) {
         this.status = "Add a packet first";
         this.statusColor = -6645094;
      } else {
         MultiManager.BroadcastResult result = this.test == null ? null : this.test.apply(action);
         this.status = shortResult(result);
         this.statusColor = result != null && result.failed() <= 0 ? (result.skipped() > 0 ? -6645094 : -13248397) : -42149;
      }
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int virtualMouseX = RiptideUiScale.toVirtualInt(mouseX);
      int virtualMouseY = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         UiRenderer.rect(graphics, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), RiptideTheme.recolor(-15856112, RiptideTheme.Channel.BACKDROP));
         int w = this.panelWidth();
         int x = (this.screenWidth() - w) / 2;
         UiRenderer.frame(
            graphics,
            UiBounds.of(x, 24, w, this.screenHeight() - 48),
            RiptideTheme.recolor(-401074149, RiptideTheme.Channel.BUTTON),
            RiptideTheme.recolor(-9553346, RiptideTheme.Channel.OUTLINE)
         );
         this.drawText(graphics, "Edit Slot " + (this.slot + 1), x + 14, 30, RiptideTheme.recolor(-855310, RiptideTheme.Channel.TEXT));
         this.drawText(graphics, "Packets sent together (" + this.packetCount() + "/6)", x + 14, 66, RiptideTheme.recolor(-6645094, RiptideTheme.Channel.TEXT));
         if (!this.status.isBlank()) {
            this.drawFitted(graphics, this.status, x + 180, 66, Math.max(1, w - 194), themeStatusColor(this.statusColor));
         }

         super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
         if (this.selector != null && this.selector.isVisible()) {
            this.selector.render(graphics, virtualMouseX, virtualMouseY, delta);
         }
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private int packetCount() {
      int count = 0;

      for (String[] step : this.editSteps) {
         if (!step[0].isBlank()) {
            count++;
         }
      }

      return count;
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

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   private RiptideStyledButton addStyled(int x, int y, int w, int h, String text, Button.Tone tone, OnPress press) {
      Button.Tone interactiveTone = tone == Button.Tone.NORMAL ? Button.Tone.SECONDARY : tone;
      RiptideStyledButton button = new RiptideStyledButton(x, y, Math.max(1, w), h, Component.literal(this.fitLabel(text, w - 8)), interactiveTone, press);
      this.addRenderableWidget(button);
      return button;
   }

   private String fitLabel(String text, int width) {
      return UiText.trimToWidthEllipsis(
         this.font, MultiManager.singleLine(text, 64), Math.max(1, width), THEME.fontFor(UiTone.BODY), RiptideTheme.recolor(-855310, RiptideTheme.Channel.TEXT)
      );
   }

   private int panelWidth() {
      return Math.max(1, Math.min(560, this.screenWidth() - 40));
   }

   private void drawFitted(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int color) {
      graphics.text(this.font, Component.literal(this.fitLabel(text, width)), x, y, color, false);
   }

   private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
      Identifier fontId = THEME.fontFor(UiTone.BODY);
      UiText.draw(graphics, this.font, MultiManager.singleLine(text, 140), fontId, color, x, y, false);
   }

   private static String shortResult(MultiManager.BroadcastResult result) {
      if (result == null) {
         return "Failed";
      } else if (result.failed() > 0) {
         return "Failed";
      } else if (result.sent() > 0 && result.skipped() == 0) {
         return "Sent";
      } else if (result.sent() > 0) {
         return "Sent " + result.sent();
      } else {
         return result.skipped() > 0 ? "Skipped" : "Failed";
      }
   }

   private static int themeStatusColor(int color) {
      if (color == -13248397) {
         return RiptideTheme.recolor(-13248397, RiptideTheme.Channel.SUCCESS);
      } else {
         return color == -42149 ? RiptideTheme.recolor(-42149, RiptideTheme.Channel.DANGER) : RiptideTheme.recolor(-6645094, RiptideTheme.Channel.TEXT);
      }
   }
}
