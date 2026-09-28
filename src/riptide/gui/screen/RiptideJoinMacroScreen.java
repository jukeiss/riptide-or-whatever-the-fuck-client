package riptide.gui.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.CompactDropdown;
import riptide.gui.vanillaui.components.CompactOverlayButton;
import riptide.gui.vanillaui.components.CompactOverlayControls;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.util.IRiptideOverlay;
import riptide.util.RiptideChatField;
import riptide.util.RiptideJoinMacroController;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroEditorOverlay;
import riptide.util.RiptideMacroManager;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideOverlayManager;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;
import riptide.util.multi.MultiProfile;

public final class RiptideJoinMacroScreen extends RiptideScreen {
   private static final int PANEL_W = 382;
   private static final int PANEL_MARGIN = 12;
   private static final int TOP_PANEL_Y = 20;
   private static final int TOP_PANEL_H = 154;
   private static final int LIST_TOP = 180;
   private static final int LIST_BOTTOM_MARGIN = 12;
   private static final int LIST_HEADER_H = 22;
   private static final int ROW_H = 22;
   private static final int GAP = 6;
   private final Screen parent;
   private final List<RiptideJoinMacroScreen.Row> rows = new ArrayList<>();
   private final List<RiptideJoinMacroScreen.HitButton> buttons = new ArrayList<>();
   private RiptideChatField searchField;
   private int scroll;
   private String lastQuery = "";
   private long lastMacroRevision = Long.MIN_VALUE;
   private boolean rowsDirty = true;
   private final List<CompactDropdown> dropdowns = new ArrayList<>();
   private CompactDropdown methodDropdown;
   private CompactDropdown triggerDropdown;
   private boolean macroScrollbarDragging;
   private int macroScrollbarGrabOffset;

   public RiptideJoinMacroScreen(Screen parent) {
      super(Component.literal("Join Macro"));
      this.parent = parent;
   }

   public boolean isPauseScreen() {
      return false;
   }

   protected void init() {
      int x = this.panelX();
      int y = 20;
      if (this.searchField == null) {
         this.searchField = new RiptideChatField(this.minecraft, this.font, x + 22, y + 48, this.panelW() - 44, 18, false);
         this.searchField.setPlaceholder(Component.literal("Search macros..."));
         this.searchField.setMaxLength(64);
         this.searchField.setChangedListener(value -> {
            this.scroll = 0;
            this.rowsDirty = true;
         });
      }

      this.syncSearchBounds();
      this.rebuildRows();
   }

   public void tick() {
      super.tick();
      this.rebuildRowsIfNeeded();
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int virtualMouseX = RiptideUiScale.toVirtualInt(mouseX);
      int virtualMouseY = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         this.syncSearchBounds();
         this.buttons.clear();
         this.dropdowns.clear();
         graphics.fill(0, 0, this.screenWidth(), this.screenHeight(), -15856112);
         int x = this.panelX();
         int panelW = this.panelW();
         this.addButton(10, 10, 76, 18, "Back", CompactOverlayButton.Variant.SECONDARY, true, () -> this.minecraft.gui.setScreen(this.parent));
         UiRenderer.frame(graphics, UiBounds.of(x + 10, 20, panelW - 20, 154), -401074149, RiptideTheme.recolor(-9553346, RiptideTheme.Channel.OUTLINE));
         UiRenderer.frame(
            graphics,
            UiBounds.of(this.listX(), 180, this.listW(), this.listPanelH()),
            -1206643689,
            RiptideTheme.recolor(-9553346, RiptideTheme.Channel.OUTLINE)
         );
         graphics.text(this.font, "Join Macro", x + 22, 30, -855310, false);
         this.renderStatus(graphics, x + 22, 44, panelW - 44);
         boolean menuOpen = CompactDropdown.isMenuOpen(this.dropdowns);
         int hoverX = menuOpen ? Integer.MIN_VALUE : virtualMouseX;
         int hoverY = menuOpen ? Integer.MIN_VALUE : virtualMouseY;
         this.searchField.render(graphics, virtualMouseX, virtualMouseY, delta);
         this.renderActionRow(hoverX, hoverY);
         this.renderMethodRow(graphics, hoverX, hoverY);
         this.renderRows(graphics, hoverX, hoverY);
         this.renderButtons(graphics, hoverX, hoverY);
         CompactDropdown.renderButtons(graphics, this.font, this.dropdowns, virtualMouseX, virtualMouseY);
         CompactDropdown.renderOpenMenu(graphics, this.font, this.dropdowns, virtualMouseX, virtualMouseY);
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private void renderActionRow(int mouseX, int mouseY) {
      int x = this.panelX() + 22;
      int y = 92;
      this.addButton(x, y, 58, 18, "Edit", CompactOverlayButton.Variant.SECONDARY, this.selectedMacro() != null, () -> this.openEditor(this.selectedMacro()));
      this.addButton(x + 64, y, 62, 18, "Create", CompactOverlayButton.Variant.SUCCESS, true, () -> this.openEditor(null));
      this.addButton(x + 132, y, 92, 18, "Passwords", CompactOverlayButton.Variant.SECONDARY, true, this::openFormValues);
   }

   private void openFormValues() {
      MultiProfile profile = new MultiProfile();
      profile.name = "Rendered client";
      profile.sessions.clear();
      profile.sessions.add(new MultiProfile.SessionSpec("default", ""));
      RiptideJoinMacroController.openFormValues().forEach((name, value) -> profile.setFormValue("default", name, value));
      this.minecraft.gui.setScreen(new RiptideFormValuesScreen(this, profile, Set.of("default"), updated -> {
         if (!RiptideJoinMacroController.setFormValues(updated.openFormValues("default"))) {
            RiptideNotifications.error("Secure encryption is unavailable; values were not saved.");
         }
      }, false));
   }

   private void renderMethodRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      int x = this.panelX() + 22;
      int y = 118;
      graphics.text(this.font, "Execution Method", x, y + 5, -1054492, false);
      int dropdownX = x + 104;
      int dropdownW = 104;
      RiptideJoinMacroController.Timing[] timings = RiptideJoinMacroController.Timing.values();
      List<String> methodOptions = new ArrayList<>();

      for (RiptideJoinMacroController.Timing t : timings) {
         methodOptions.add(t.label());
      }

      int methodSel = RiptideJoinMacroController.timing().ordinal();
      this.methodDropdown = this.updateDropdown(
         this.methodDropdown,
         dropdownX,
         y,
         dropdownW,
         18,
         methodOptions,
         methodSel,
         idx -> RiptideJoinMacroController.setTiming(timings[Math.max(0, Math.min(idx, timings.length - 1))])
      );
      this.dropdowns.add(this.methodDropdown);
      boolean keep = RiptideJoinMacroController.keepEnabled();
      this.addButton(
         dropdownX + dropdownW + 6,
         y,
         118,
         18,
         keep ? "Stays Enabled" : "Clears After",
         keep ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.SECONDARY,
         true,
         () -> RiptideJoinMacroController.setKeepEnabled(!RiptideJoinMacroController.keepEnabled())
      );
      int triggerRowY = y + 24;
      graphics.text(this.font, keep ? "Repeat On" : "Run On", x, triggerRowY + 5, -1054492, false);
      RiptideJoinMacroController.TriggerJoin[] triggerOpts = this.triggerOptions();
      List<String> triggerLabels = new ArrayList<>();
      int triggerSel = 0;

      for (int i = 0; i < triggerOpts.length; i++) {
         triggerLabels.add(this.triggerLabel(triggerOpts[i]));
         if (triggerOpts[i] == RiptideJoinMacroController.triggerJoin()) {
            triggerSel = i;
         }
      }

      this.triggerDropdown = this.updateDropdown(
         this.triggerDropdown,
         dropdownX,
         triggerRowY,
         118,
         18,
         triggerLabels,
         triggerSel,
         idx -> RiptideJoinMacroController.setTriggerJoin(triggerOpts[Math.max(0, Math.min(idx, triggerOpts.length - 1))])
      );
      this.dropdowns.add(this.triggerDropdown);
   }

   private CompactDropdown updateDropdown(CompactDropdown existing, int x, int y, int w, int h, List<String> options, int selected, IntConsumer onSelect) {
      if (existing == null) {
         return new CompactDropdown(x, y, w, h, options, selected, onSelect);
      } else {
         existing.setBounds(x, y, w, h).setOptions(options).setSelectedIndex(selected).setOnSelect(onSelect);
         return existing;
      }
   }

   private void renderStatus(GuiGraphicsExtractor graphics, int x, int y, int maxWidth) {
      String selected = RiptideJoinMacroController.selectedMacroName();
      if (selected.isBlank()) {
         graphics.text(this.font, "Click a macro to select it.", x, y, -6645094, false);
      } else {
         String selectedLine = "Selected: " + selected;
         String modeLine = RiptideJoinMacroController.modeSummary();
         String combined = selectedLine + "  " + modeLine;
         if (this.font.width(combined) <= maxWidth) {
            graphics.text(this.font, combined, x, y, -13248397, false);
         } else {
            graphics.text(this.font, this.fit(selectedLine, maxWidth), x, y, -13248397, false);
            graphics.text(this.font, this.fit(modeLine, maxWidth), x, y + 10, -13248397, false);
         }
      }
   }

   private void renderRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      int listX = this.listX();
      int listY = this.listY();
      int listW = this.listW();
      CompactScrollbar.Metrics scrollbar = this.macroScrollbarMetrics();
      boolean hasScrollbar = scrollbar.hasScroll();
      int rowRightInset = hasScrollbar ? 18 : 8;
      int titleY = listY + 10;
      int visibleRows = this.visibleRows();
      String title = this.rows.size() <= visibleRows
         ? "Macros"
         : "Macros  showing " + (this.scroll + 1) + "-" + Math.min(this.rows.size(), this.scroll + visibleRows) + " / " + this.rows.size();
      graphics.text(this.font, this.fit(title, listW - 24), listX + 12, titleY, -855310, false);
      if (this.rows.isEmpty()) {
         graphics.text(this.font, "No macros found.", listX + 12, this.rowsTop() + 8, -6645094, false);
      } else {
         String selected = RiptideJoinMacroController.selectedMacroName();
         int max = Math.min(this.rows.size(), this.scroll + visibleRows);

         for (int i = this.scroll; i < max; i++) {
            RiptideJoinMacroScreen.Row row = this.rows.get(i);
            int rowY = this.rowsTop() + (i - this.scroll) * 22;
            boolean hovered = mouseX >= listX + 1 && mouseX < listX + listW - 1 && mouseY >= rowY && mouseY < rowY + 22;
            boolean active = row.name.equalsIgnoreCase(selected);
            UiBounds rowBounds = UiBounds.of(listX + 8, rowY, listW - 8 - rowRightInset, 19);
            this.renderMacroSelectionRow(graphics, rowBounds, row.name, active, hovered);
            String stepsText = row.steps + (row.steps == 1 ? " step" : " steps");
            int stepsW = this.font.width(stepsText);
            int rightX = listX + listW - rowRightInset - 4 - stepsW;
            int nameW = Math.max(1, rightX - (listX + 20) - 6);
            graphics.text(this.font, this.fit(row.name, nameW), listX + 20, rowY + 5, -1054492, false);
            graphics.text(this.font, stepsText, rightX, rowY + 5, active ? -13248397 : -6645094, false);
         }

         CompactScrollbar.draw(graphics, scrollbar, scrollbar.contains(mouseX, mouseY), this.macroScrollbarDragging);
      }
   }

   private void renderButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      for (RiptideJoinMacroScreen.HitButton button : this.buttons) {
         CompactOverlayControls.action(
            graphics, this.font, button.x, button.y, button.w, button.h, button.label, button.variant, button.enabled, mouseX, mouseY
         );
      }
   }

   private void addButton(int x, int y, int w, int h, String label, CompactOverlayButton.Variant variant, boolean enabled, Runnable action) {
      this.buttons.add(new RiptideJoinMacroScreen.HitButton(x, y, w, h, label, variant, enabled, action));
   }

   private void renderMacroSelectionRow(GuiGraphicsExtractor graphics, UiBounds bounds, String key, boolean selected, boolean hovered) {
      UiRenderer.rect(
         graphics,
         bounds,
         selected
            ? RiptideTheme.recolor(976607347, RiptideTheme.Channel.SUCCESS)
            : (hovered ? RiptideTheme.recolor(606804509, RiptideTheme.Channel.ACCENT) : 403771667)
      );
      if (selected) {
         UiRenderer.rect(graphics, bounds.inset(1, 1, 1, 1), RiptideTheme.recolor(hovered ? 1463146611 : 976607347, RiptideTheme.Channel.SUCCESS));
         UiRenderer.rect(graphics, UiBounds.of(bounds.x(), bounds.y(), 2, bounds.height()), -13248397);
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
      event = virtualEvent(event);
      if (CompactDropdown.mouseClicked(this.dropdowns, event.x(), event.y(), event.button())) {
         return true;
      } else if (this.searchField != null && this.searchField.mouseClicked(event.x(), event.y(), event.button())) {
         return true;
      } else {
         if (event.button() == 0) {
            CompactScrollbar.Metrics scrollbar = this.macroScrollbarMetrics();
            if (scrollbar.hasScroll() && scrollbar.contains(event.x(), event.y())) {
               this.macroScrollbarDragging = true;
               this.macroScrollbarGrabOffset = scrollbar.overThumb(event.x(), event.y())
                  ? Math.max(0, (int)Math.round(event.y()) - scrollbar.thumbY())
                  : scrollbar.thumbHeight() / 2;
               this.setScrollFromPixels(CompactScrollbar.scrollFromThumb(scrollbar, event.y(), this.macroScrollbarGrabOffset));
               if (this.searchField != null) {
                  this.searchField.setFocused(false);
               }

               return true;
            }

            for (RiptideJoinMacroScreen.HitButton button : this.buttons) {
               if (button.contains(event.x(), event.y())) {
                  if (button.enabled && button.action != null) {
                     button.action.run();
                  }

                  return true;
               }
            }

            int rowIndex = this.rowAt(event.x(), event.y());
            if (rowIndex >= 0) {
               RiptideJoinMacroScreen.Row row = this.rows.get(rowIndex);
               String selected = RiptideJoinMacroController.selectedMacroName();
               RiptideJoinMacroController.setSelectedMacro(row.name.equalsIgnoreCase(selected) ? "" : row.name);
               return true;
            }
         }

         if (this.searchField != null) {
            this.searchField.setFocused(false);
         }

         return true;
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      event = virtualEvent(event);
      if (CompactDropdown.mouseReleased(this.dropdowns)) {
         return true;
      } else if (this.macroScrollbarDragging) {
         this.macroScrollbarDragging = false;
         return true;
      } else {
         return this.searchField != null && this.searchField.mouseReleased(event.x(), event.y(), event.button());
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      event = virtualEvent(event);
      dx = RiptideUiScale.toVirtual(dx);
      dy = RiptideUiScale.toVirtual(dy);
      if (CompactDropdown.mouseDragged(this.dropdowns, event.x(), event.y(), event.button())) {
         return true;
      } else if (this.macroScrollbarDragging) {
         this.setScrollFromPixels(CompactScrollbar.scrollFromThumb(this.macroScrollbarMetrics(), event.y(), this.macroScrollbarGrabOffset));
         return true;
      } else {
         return this.searchField != null && this.searchField.mouseDragged(event.x(), event.y(), event.button(), dx, dy);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      mouseX = RiptideUiScale.toVirtual(mouseX);
      mouseY = RiptideUiScale.toVirtual(mouseY);
      if (CompactDropdown.mouseScrolled(this.dropdowns, mouseX, mouseY, scrollY)) {
         return true;
      } else if (this.searchField != null && this.searchField.mouseScrolled(mouseX, mouseY, scrollY)) {
         return true;
      } else if (mouseX >= this.listX() && mouseX < this.listX() + this.listW() && mouseY >= this.listY() && mouseY < this.listY() + this.listPanelH()) {
         int maxScroll = Math.max(0, this.rows.size() - this.visibleRows());
         if (maxScroll > 0) {
            this.scroll = Math.max(0, Math.min(maxScroll, this.scroll - (int)Math.signum(scrollY)));
         }

         return true;
      } else {
         return true;
      }
   }

   public boolean keyPressed(KeyEvent input) {
      if (this.searchField != null && this.searchField.keyPressed(input)) {
         return true;
      } else if (input.key() == 256) {
         if (CompactDropdown.closeOpenMenu(this.dropdowns)) {
            return true;
         } else {
            this.minecraft.gui.setScreen(this.parent);
            return true;
         }
      } else {
         return true;
      }
   }

   public boolean charTyped(CharacterEvent input) {
      return this.searchField != null && this.searchField.charTyped(input);
   }

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   private void rebuildRowsIfNeeded() {
      String query = this.searchText();
      long revision = RiptideMacroManager.get().getRevision();
      if (this.rowsDirty || !query.equals(this.lastQuery) || revision != this.lastMacroRevision) {
         this.rebuildRows();
      }
   }

   private void rebuildRows() {
      String query = this.searchText();
      this.lastQuery = query;
      this.lastMacroRevision = RiptideMacroManager.get().getRevision();
      this.rowsDirty = false;
      this.rows.clear();

      for (RiptideMacro macro : RiptideMacroManager.get().getAll()) {
         if (macro != null && macro.name != null && !macro.name.isBlank() && (query.isEmpty() || macro.name.toLowerCase(Locale.ROOT).contains(query))) {
            this.rows.add(new RiptideJoinMacroScreen.Row(macro.name, macro.actions == null ? 0 : macro.actions.size()));
         }
      }

      this.rows.sort(Comparator.comparing(row -> row.name.toLowerCase(Locale.ROOT)));
      this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.rows.size() - this.visibleRows())));
   }

   private String searchText() {
      return this.searchField == null ? "" : this.searchField.getText().trim().toLowerCase(Locale.ROOT);
   }

   private RiptideMacro selectedMacro() {
      String selected = RiptideJoinMacroController.selectedMacroName();
      return selected.isBlank() ? null : RiptideMacroManager.get().get(selected);
   }

   private void openEditor(RiptideMacro macro) {
      RiptideMacroEditorOverlay editor = RiptideMacroEditorOverlay.getSharedOverlay();
      RiptideOverlayManager.get().register(editor, IRiptideOverlay.OverlayScope.HOST_SCREEN);
      editor.openForJoinMacroMenu(macro, saved -> {
         if (saved != null && saved.name != null && !saved.name.isBlank()) {
            RiptideJoinMacroController.setSelectedMacro(saved.name);
            RiptideNotifications.show("Join macro selected: " + saved.name, -10035062);
         }
      });
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(new RiptideOverlayHostScreen(editor, new RiptideJoinMacroScreen(this.parent)));
      }
   }

   private int rowAt(double mouseX, double mouseY) {
      int x = this.listX();
      int y = this.rowsTop();
      int h = this.visibleRows() * 22;
      if (!(mouseX < x + 8) && !(mouseX >= x + this.listW() - 8) && !(mouseY < y) && !(mouseY >= y + h)) {
         int visibleIndex = ((int)mouseY - y) / 22;
         int index = this.scroll + visibleIndex;
         return index >= 0 && index < this.rows.size() ? index : -1;
      } else {
         return -1;
      }
   }

   private void syncSearchBounds() {
      if (this.searchField != null) {
         int x = this.panelX();
         this.searchField.setX(x + 22);
         this.searchField.setY(68);
         this.searchField.setWidth(this.panelW() - 44);
         this.searchField.setHeight(18);
      }
   }

   private int panelX() {
      return Math.max(12, (this.screenWidth() - this.panelW()) / 2);
   }

   private int panelW() {
      return Math.max(1, Math.min(382, this.screenWidth() - 24));
   }

   private int listX() {
      return this.panelX() + 10;
   }

   private int listY() {
      return 180;
   }

   private int listW() {
      return this.panelW() - 20;
   }

   private int listPanelH() {
      return Math.max(22, this.screenHeight() - 180 - 12);
   }

   private int rowsTop() {
      return 202;
   }

   private int visibleRows() {
      return Math.max(1, (this.listPanelH() - 22 - 8) / 22);
   }

   private CompactScrollbar.Metrics macroScrollbarMetrics() {
      int trackX = this.listX() + this.listW() - 10;
      int trackY = this.rowsTop();
      int trackH = Math.max(1, this.visibleRows() * 22 - 3);
      int contentPixels = Math.max(0, this.rows.size() * 22);
      int viewPixels = Math.max(1, this.visibleRows() * 22);
      return CompactScrollbar.compute(contentPixels, viewPixels, trackX, trackY, 4, trackH, this.scroll * 22);
   }

   private void setScrollFromPixels(int scrollPixels) {
      int maxScrollRows = Math.max(0, this.rows.size() - this.visibleRows());
      this.scroll = Math.max(0, Math.min(maxScrollRows, Math.round(scrollPixels / 22.0F)));
   }

   private RiptideJoinMacroController.TriggerJoin[] triggerOptions() {
      return RiptideJoinMacroController.keepEnabled()
         ? new RiptideJoinMacroController.TriggerJoin[]{
            RiptideJoinMacroController.TriggerJoin.ANY,
            RiptideJoinMacroController.TriggerJoin.SECOND,
            RiptideJoinMacroController.TriggerJoin.THIRD,
            RiptideJoinMacroController.TriggerJoin.FOURTH,
            RiptideJoinMacroController.TriggerJoin.FIFTH,
            RiptideJoinMacroController.TriggerJoin.SIXTH_PLUS
         }
         : new RiptideJoinMacroController.TriggerJoin[]{
            RiptideJoinMacroController.TriggerJoin.FIRST,
            RiptideJoinMacroController.TriggerJoin.SECOND,
            RiptideJoinMacroController.TriggerJoin.THIRD,
            RiptideJoinMacroController.TriggerJoin.FOURTH,
            RiptideJoinMacroController.TriggerJoin.FIFTH,
            RiptideJoinMacroController.TriggerJoin.SIXTH_PLUS
         };
   }

   private String triggerLabel(RiptideJoinMacroController.TriggerJoin triggerJoin) {
      return triggerJoin.displayLabel(RiptideJoinMacroController.keepEnabled());
   }

   private String fit(String value, int maxWidth) {
      if (value == null) {
         return "";
      } else {
         return this.font.width(value) <= maxWidth ? value : this.font.plainSubstrByWidth(value, Math.max(1, maxWidth - 4));
      }
   }

   private record HitButton(int x, int y, int w, int h, String label, CompactOverlayButton.Variant variant, boolean enabled, Runnable action) {
      boolean contains(double mx, double my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   private record Row(String name, int steps) {
   }
}
