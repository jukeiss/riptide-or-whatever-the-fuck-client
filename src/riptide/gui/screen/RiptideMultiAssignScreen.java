package riptide.gui.screen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiSession;

public final class RiptideMultiAssignScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int ROW_H = 22;
   private final Screen parent;
   private final String macroName;
   private final LinkedHashSet<String> selected = new LinkedHashSet<>();
   private final List<int[]> rowRects = new ArrayList<>();
   private int scroll;
   private String status = "";
   private int statusColor = -6645094;

   public RiptideMultiAssignScreen(Screen parent, String macroName, Set<String> preselect) {
      super(Component.literal("Assign Macro"));
      this.parent = parent;
      this.macroName = macroName == null ? "" : macroName;
      if (preselect != null) {
         this.selected.addAll(preselect);
      }
   }

   private List<RiptideMultiAssignScreen.Account> accounts() {
      List<RiptideMultiAssignScreen.Account> out = new ArrayList<>();

      for (MultiSession.Snapshot s : MultiManager.get().snapshots()) {
         String m = MultiManager.get().effectiveMacroName(s.accountId());
         out.add(new RiptideMultiAssignScreen.Account(s.accountId(), s.accountName(), m == null ? "" : m));
      }

      return out;
   }

   protected void init() {
      this.clearWidgets();
      int w = this.panelW();
      int x = (this.screenWidth() - w) / 2;
      int gap = 6;
      int third = Math.max(1, (w - gap * 2) / 3);
      int toolY = 40;
      this.addButton(x, toolY, third, "Select All", Button.Tone.SECONDARY, b -> {
         this.selected.clear();

         for (RiptideMultiAssignScreen.Account a : this.accounts()) {
            this.selected.add(a.id());
         }
      });
      this.addButton(x + third + gap, toolY, third, "Select None", Button.Tone.SECONDARY, b -> this.selected.clear());
      this.addButton(x + (third + gap) * 2, toolY, w - (third + gap) * 2, "Only Without Macro", Button.Tone.SECONDARY, b -> {
         this.selected.clear();

         for (RiptideMultiAssignScreen.Account a : this.accounts()) {
            if (a.macro().isBlank()) {
               this.selected.add(a.id());
            }
         }
      });
      int half = Math.max(1, (w - gap) / 2);
      this.addButton(x, this.screenHeight() - 28, half, "Assign to Selected", Button.Tone.SUCCESS, b -> this.apply());
      this.addButton(x + half + gap, this.screenHeight() - 28, w - half - gap, "Back", Button.Tone.SECONDARY, b -> this.onClose());
   }

   private void apply() {
      if (this.selected.isEmpty()) {
         this.status("Tick at least one account.", -42149);
      } else {
         int changed = MultiManager.get().assignMacroOnScope(new LinkedHashSet<>(this.selected), this.macroName);
         this.status("Assigned \"" + this.macroName + "\" to " + changed + " account(s).", -13248397);
      }
   }

   private void status(String text, int color) {
      this.status = text == null ? "" : text;
      this.statusColor = color;
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (virtualEvent.button() == 0) {
         List<RiptideMultiAssignScreen.Account> list = this.accounts();

         for (int[] rect : this.rowRects) {
            if (virtualEvent.x() >= rect[0]
               && virtualEvent.x() < rect[0] + rect[2]
               && virtualEvent.y() >= rect[1]
               && virtualEvent.y() < rect[1] + rect[3]
               && rect[4] < list.size()) {
               String id = list.get(rect[4]).id();
               if (!this.selected.remove(id)) {
                  this.selected.add(id);
               }

               return true;
            }
         }
      }

      return super.mouseClicked(virtualEvent, doubled);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
      this.scroll = Math.max(0, this.scroll + (vertical < 0.0 ? 1 : -1));
      return true;
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int virtualMouseX = RiptideUiScale.toVirtualInt(mouseX);
      int virtualMouseY = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         UiRenderer.rect(graphics, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), RiptideTheme.recolor(-15856112, RiptideTheme.Channel.BACKDROP));
         int w = this.panelW();
         int x = (this.screenWidth() - w) / 2;
         int border = RiptideTheme.recolor(-9553346, RiptideTheme.Channel.OUTLINE);
         int textColor = RiptideTheme.recolor(-855310, RiptideTheme.Channel.TEXT);
         int muted = RiptideTheme.recolor(-6645094, RiptideTheme.Channel.TEXT);
         UiRenderer.frame(
            graphics,
            UiBounds.of(x - 6, 14, w + 12, Math.max(1, this.screenHeight() - 42)),
            RiptideTheme.recolor(-401074149, RiptideTheme.Channel.BUTTON),
            border
         );
         Identifier fontId = THEME.fontFor(UiTone.BODY);
         UiText.draw(graphics, this.font, "Assign \"" + MultiManager.singleLine(this.macroName, 40) + "\" to accounts:", fontId, textColor, x, 26, false);
         this.rowRects.clear();
         List<RiptideMultiAssignScreen.Account> list = this.accounts();
         int top = 62;
         int bottom = this.screenHeight() - 34;
         int visible = Math.max(1, (bottom - top) / 22);
         this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, list.size() - visible)));
         int success = RiptideTheme.recolor(-13248397, RiptideTheme.Channel.SUCCESS);
         int y = top;

         for (int i = this.scroll; i < Math.min(list.size(), this.scroll + visible); i++) {
            RiptideMultiAssignScreen.Account a = list.get(i);
            boolean sel = this.selected.contains(a.id());
            UiRenderer.rect(graphics, UiBounds.of(x, y, w, 19), sel ? success & 16777215 | 855638016 : 402653184);
            if (sel) {
               UiRenderer.rect(graphics, UiBounds.of(x, y, 2, 19), success);
            }

            String box = sel ? "[x] " : "[ ] ";
            UiText.draw(graphics, this.font, box + MultiManager.singleLine(a.name(), 40), fontId, sel ? success : textColor, x + 6, y + 2, false);
            String cur = a.macro().isBlank() ? "no macro" : "-> " + a.macro();
            UiText.draw(graphics, this.font, cur, fontId, a.macro().isBlank() ? muted : textColor, x + 6, y + 11, false);
            this.rowRects.add(new int[]{x, y, w, 19, i});
            y += 22;
         }

         if (!this.status.isBlank()) {
            UiText.draw(graphics, this.font, this.status, fontId, this.statusColor, x, this.screenHeight() - 40, false);
         }

         super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private int panelW() {
      return Math.max(200, Math.min(360, this.screenWidth() - 60));
   }

   private void addButton(int x, int y, int w, String label, Button.Tone tone, OnPress press) {
      this.addRenderableWidget(new RiptideStyledButton(x, y, Math.max(1, w), 18, Component.literal(label), tone, press));
   }

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   private record Account(String id, String name, String macro) {
   }
}
