package riptide.gui.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.components.ColorPicker;
import riptide.gui.vanillaui.components.CompactDropdown;
import riptide.gui.vanillaui.components.CompactOverlayButton;
import riptide.gui.vanillaui.components.CompactOverlayControls;
import riptide.gui.vanillaui.components.CompactScreenPanel;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactSurfaces;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.Slider;
import riptide.gui.vanillaui.components.Toggle;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectLayout;
import riptide.modules.Module;
import riptide.modules.ModuleRegistry;
import riptide.util.RiptideChatField;
import riptide.util.RiptideHudManager;
import riptide.util.RiptideRegistryLabels;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;

public class RiptideHudElementSettingsScreen extends Screen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int TEXT = -791321;
   private static final int MUTED = -4743522;
   private static final int RED = -50373;
   private static final int GREEN = -10682470;
   private static final int PANEL_W = 368;
   private static final int HEADER_H = 24;
   private static final int FOOTER_H = 36;
   private static final int ROW_H = 26;
   private static final int FIELD_H = 18;
   private final Screen parent;
   private final String id;
   private int scroll;
   private boolean scrollbarDragging;
   private int scrollbarGrabOffset;
   private String focusedKey;
   private RiptideChatField editField;
   private String colorPickerKey;
   private ColorPicker colorPicker;
   private final List<CompactDropdown> enumDropdowns = new ArrayList<>();
   private final Map<String, CompactDropdown> enumDropdownCache = new HashMap<>();
   private boolean modulePickerOpen;
   private int modulePickerScroll;
   private RiptideHudElementSettingsScreen.Row draggingSlider;
   private static final Map<String, RiptideHudElementSettingsScreen.CachedRows> cachedRowLists = new HashMap<>();

   private static int muted() {
      return RiptideTheme.recolor(-4743522, RiptideTheme.Channel.TEXT);
   }

   private static int themed(int argb) {
      return RiptideTheme.recolor(argb, RiptideTheme.Channel.ACCENT);
   }

   public RiptideHudElementSettingsScreen(Screen parent, String id) {
      super(Component.literal("HUD Element Settings"));
      this.parent = parent;
      this.id = id;
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int mx = RiptideUiScale.toVirtualInt(mouseX);
      int my = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         int sw = RiptideUiScale.getVirtualScreenWidth();
         int sh = RiptideUiScale.getVirtualScreenHeight();
         int[] panel = this.panelBounds();
         int x = panel[0];
         int y = panel[1];
         int w = panel[2];
         int h = panel[3];
         UiRenderer.rect(graphics, UiBounds.of(0, 0, sw, sh), 1711276032);
         this.drawTopBar(graphics, x, y, w, h, 24, RiptideHudManager.label(this.id), mx, my);
         if (this.compactLayout(w, h)) {
            return;
         }

         int bodyTop = y + 24 + 7;
         int bodyBottom = y + h - 36 - 5;
         int viewH = Math.max(30, bodyBottom - bodyTop);
         List<RiptideHudElementSettingsScreen.Row> rows = this.rows();
         int contentH = this.contentHeight(rows);
         this.scroll = this.clamp(this.scroll, 0, Math.max(0, contentH - viewH));
         boolean menuOpen = CompactDropdown.isMenuOpen(this.enumDropdowns);
         int hoverX = menuOpen ? Integer.MIN_VALUE : mx;
         int hoverY = menuOpen ? Integer.MIN_VALUE : my;
         this.enumDropdowns.clear();
         UiScissorStack.global().push(graphics, UiBounds.of(x + 2, bodyTop, Math.max(0, w - 9), Math.max(0, bodyBottom - bodyTop)));

         try {
            this.renderRows(graphics, rows, x + 8, bodyTop - this.scroll, w - 20, hoverX, hoverY, bodyTop, bodyBottom);
            CompactDropdown.renderButtons(graphics, this.font, this.enumDropdowns, mx, my);
         } finally {
            UiScissorStack.global().pop(graphics);
         }

         CompactScrollbar.Metrics metrics = CompactScrollbar.compute(contentH, viewH, x + w - 6, bodyTop, 4, viewH, this.scroll);
         CompactScrollbar.draw(graphics, metrics, metrics.contains(hoverX, hoverY), this.scrollbarDragging);
         int footerY = y + h - 36 + 7;
         this.button(graphics, "Back", x + 8, footerY, 78, 20, hoverX, hoverY, CompactOverlayButton.Variant.PRIMARY);
         this.button(graphics, "Reset", x + 94, footerY, 78, 20, hoverX, hoverY, CompactOverlayButton.Variant.PRIMARY);
         boolean enabled = RiptideHudManager.state(this.id).enabled;
         int switchX = x + w - 32;
         int labelW = UiText.width(this.font, "Enabled", THEME.fontFor(UiTone.BODY), -791321);
         this.draw(graphics, "Enabled", switchX - 6 - labelW, footerY + 6, enabled ? -791321 : muted(), labelW + 2);
         this.renderSwitch(graphics, enabled, switchX, footerY + 2, "hud-elem-settings:" + this.id + ":enabled");
         CompactDropdown.renderOpenMenu(graphics, this.font, this.enumDropdowns, mx, my);
         if (this.modulePickerOpen) {
            this.renderModulePicker(graphics, sw, sh, mx, my);
         }

         if (this.colorPicker != null) {
            graphics.nextStratum();
            this.colorPicker.render(UiContexts.overlay(graphics, this.font, mx, my));
            if (!this.colorPicker.isOpen()) {
               this.clearColorPicker();
            }
         }
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private void renderRows(
      GuiGraphicsExtractor graphics, List<RiptideHudElementSettingsScreen.Row> rows, int x, int y, int w, int mx, int my, int clipTop, int clipBottom
   ) {
      int cy = y;

      for (RiptideHudElementSettingsScreen.Row row : rows) {
         if (row.section()) {
            if (this.visible(cy, 22, clipTop, clipBottom)) {
               this.section(graphics, row.label, x, cy, w);
            }

            cy += 22;
         } else {
            if (this.visible(cy, 26, clipTop, clipBottom)) {
               this.row(graphics, row, x, cy, w, mx, my);
            }

            cy += 26;
         }
      }
   }

   private void section(GuiGraphicsExtractor graphics, String label, int x, int y, int w) {
      CompactSurfaces.tintedRow(graphics, x, y + 3, w, 16, themed(1142492176));
      CompactSurfaces.divider(graphics, x, y + 3, w, themed(1720655652));
      CompactSurfaces.divider(graphics, x, y + 18, w, themed(1720655652));
      this.draw(graphics, label, x + 6, y + 7, muted(), w - 12);
   }

   private void row(GuiGraphicsExtractor graphics, RiptideHudElementSettingsScreen.Row row, int x, int y, int w, int mx, int my) {
      boolean over = this.hover(mx, my, x, y, w, 24);
      CompactSurfaces.tintedRow(graphics, x, y, w, 24, over ? themed(1714756383) : 856888344);
      this.draw(graphics, row.label, x + 6, y + 7, -791321, w / 2 - 8);
      int valueX = x + w / 2;
      int valueW = w / 2 - 8;
      if (row.type == RiptideHudElementSettingsScreen.RowType.BOOL) {
         boolean on = this.bool(row.key);
         this.renderSwitch(graphics, on, x + w - 34, y + 4, "hud-elem-settings:" + this.id + ":" + row.key);
      } else if (row.type == RiptideHudElementSettingsScreen.RowType.ENUM) {
         this.renderDropdown(row, valueX, y + 4, valueW, 18);
      } else if (row.type == RiptideHudElementSettingsScreen.RowType.COLOR) {
         int pickW = 44;
         int color = RiptideHudManager.parseColor(RiptideHudManager.setting(this.id, row.key), this.defaultColor(row.key));
         int swatchY = y + 5;
         UiRenderer.rect(graphics, UiBounds.of(valueX, swatchY, 28, 12), color | 0xFF000000);
         this.frame(graphics, valueX, swatchY, 28, 12, 0, THEME.borderSoft());
         this.draw(graphics, "#" + String.format(Locale.ROOT, "%06X", color & 16777215), valueX + 34, y + 7, -791321, Math.max(1, valueW - pickW - 40));
         this.button(graphics, "Pick", valueX + valueW - pickW, y + 3, pickW, 18, mx, my, CompactOverlayButton.Variant.PRIMARY);
      } else if (row.type == RiptideHudElementSettingsScreen.RowType.MODULE_LIST) {
         this.button(
            graphics, "Edit (" + this.hiddenModuleIds().size() + ")", valueX + valueW - 86, y + 3, 86, 18, mx, my, CompactOverlayButton.Variant.PRIMARY
         );
      } else if (row.type == RiptideHudElementSettingsScreen.RowType.NUMBER) {
         double value = RiptideHudManager.doubleSetting(this.id, row.key, row.min);
         this.renderSlider(
            graphics, row, valueX, y + 5, valueW, 12, value, row.key != null && row.key.equals(this.focusedKey) || row == this.draggingSlider, mx, my
         );
      } else if (row.type == RiptideHudElementSettingsScreen.RowType.ITEM) {
         int pickW = 44;
         String raw = RiptideHudManager.setting(this.id, row.key);
         String itemName = raw != null && !raw.isBlank() ? RiptideRegistryLabels.item(raw.contains(":") ? raw : "minecraft:" + raw) : "None";
         this.draw(graphics, itemName, valueX, y + 7, -791321, Math.max(1, valueW - pickW - 8));
         this.button(graphics, "Pick", valueX + valueW - pickW, y + 3, pickW, 18, mx, my, CompactOverlayButton.Variant.PRIMARY);
      }
   }

   private void renderDropdown(RiptideHudElementSettingsScreen.Row row, int x, int y, int w, int h) {
      List<String> choices = row.choices;
      int selected = Math.max(0, choices.indexOf(RiptideHudManager.setting(this.id, row.key)));
      CompactDropdown dropdown = this.enumDropdownCache.computeIfAbsent(row.key, ignored -> new CompactDropdown(x, y, w, h, choices, selected, index -> {}));
      dropdown.setBounds(x, y, w, h).setOptions(choices).setSelectedIndex(selected).setOnSelect(index -> {
         RiptideHudManager.setSetting(this.id, row.key, choices.get(index));
         this.clearHudFocus();
      });
      this.enumDropdowns.add(dropdown);
   }

   private void renderSlider(
      GuiGraphicsExtractor graphics, RiptideHudElementSettingsScreen.Row row, int x, int y, int w, int h, double value, boolean editing, int mx, int my
   ) {
      int fieldW = 50;
      int sliderW = Math.max(20, w - fieldW - 7);
      double ratio = Slider.ratio(value, row.min, row.max);
      Slider.render(UiContexts.overlay(graphics, this.font, x, y), UiBounds.of(x, y, sliderW, h), ratio, editing);
      int fieldY = y - 3;
      if (editing) {
         this.positionEditField(x + sliderW + 7, fieldY, fieldW, 18);
         this.editField.render(graphics, mx, my, 0.0F);
      } else {
         this.frame(graphics, x + sliderW + 7, fieldY, fieldW, 18, -15594224, THEME.borderSoft());
         this.drawCentered(graphics, this.formatNumber(value, row.step), x + sliderW + 7, fieldY, fieldW, 18, -791321);
      }
   }

   private void positionEditField(int x, int y, int w, int h) {
      if (this.editField == null) {
         this.editField = new RiptideChatField(Minecraft.getInstance(), this.font, x, y, w, h, false);
      }

      this.editField.setX(x);
      this.editField.setY(y);
      this.editField.setWidth(w);
      this.editField.setHeight(h);
   }

   private void startEditing(String key, String value) {
      this.focusedKey = key;
      if (this.editField == null) {
         this.editField = new RiptideChatField(Minecraft.getInstance(), this.font, 0, 0, 20, 18, false);
      }

      this.editField.setText(value == null ? "" : value);
      this.editField.setFocused(true);
      this.editField.setSelectionEnd(this.editField.getText().length());
   }

   private void renderSwitch(GuiGraphicsExtractor graphics, boolean checked, int x, int y, String animationKey) {
      int w = 24;
      int h = 16;
      Toggle.render(UiContexts.overlay(graphics, this.font, x, y), UiBounds.of(x, y + 2, w, h - 4), checked, false, animationKey);
   }

   private void button(GuiGraphicsExtractor graphics, String label, int x, int y, int w, int h, int mx, int my, CompactOverlayButton.Variant variant) {
      CompactOverlayControls.action(graphics, this.font, x, y, w, h, label, variant, true, mx, my);
   }

   private void renderModulePicker(GuiGraphicsExtractor graphics, int sw, int sh, int mx, int my) {
      int w = DirectLayout.fitPanelDimension(sw, 12, 260);
      List<Module> modules = this.moduleRows();
      int rowH = 20;
      int contentH = 28 + modules.size() * rowH;
      int h = DirectLayout.fitPanelDimension(sh, 12, Math.max(128, contentH));
      int x = DirectLayout.centerPanel(sw, w, 12);
      int y = DirectLayout.centerPanel(sh, h, 12);
      int viewTop = y + 28;
      this.modulePickerScroll = this.clamp(this.modulePickerScroll, 0, Math.max(0, contentH - h));
      UiRenderer.rect(graphics, UiBounds.of(0, 0, sw, sh), 1140850688);
      this.drawTopBar(graphics, x, y, w, h, 24, "Hidden Modules", mx, my);
      UiScissorStack.global().push(graphics, UiBounds.of(x + 2, viewTop, Math.max(0, w - 4), Math.max(0, y + h - 4 - viewTop)));

      try {
         List<String> hidden = this.hiddenModuleIds();
         int cy = viewTop - this.modulePickerScroll;

         for (Module module : modules) {
            if (this.visible(cy, rowH, viewTop, y + h - 4)) {
               boolean selected = hidden.contains(module.id());
               boolean over = this.hover(mx, my, x + 5, cy, w - 10, rowH - 2);
               CompactSurfaces.tintedRow(graphics, x + 5, cy, w - 10, rowH - 2, over ? themed(1714756383) : 856888344);
               this.draw(graphics, module.name(), x + 10, cy + 5, selected ? -791321 : muted(), w - 52);
               this.renderSwitch(graphics, selected, x + w - 34, cy + 2, "hud-hidden-modules:" + module.id());
            }

            cy += rowH;
         }
      } finally {
         UiScissorStack.global().pop(graphics);
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
      int mx = RiptideUiScale.toVirtualInt(event.x());
      int my = RiptideUiScale.toVirtualInt(event.y());
      int[] panel = this.panelBounds();
      int x = panel[0];
      int y = panel[1];
      int w = panel[2];
      int h = panel[3];
      if (event.button() != 0 && event.button() != 1) {
         return true;
      } else if (this.colorPicker != null) {
         this.colorPicker.mouseClicked(mx, my, event.button());
         if (!this.colorPicker.isOpen()) {
            this.clearColorPicker();
         }

         return true;
      } else if (this.modulePickerOpen) {
         this.handleModulePickerClick(mx, my);
         return true;
      } else if (this.isOverTopBarClose(mx, my, x, y, w, h, 24)) {
         this.minecraft.gui.setScreen(this.parent);
         return true;
      } else if (CompactDropdown.mouseClicked(this.enumDropdowns, mx, my, event.button())) {
         this.clearHudFocus();
         return true;
      } else if (CompactDropdown.isMenuOpen(this.enumDropdowns)) {
         return true;
      } else if (this.focusedKey != null && this.editField != null && this.editField.mouseClicked(mx, my, event.button())) {
         return true;
      } else {
         int bodyTop = y + 24 + 7;
         int bodyBottom = y + h - 36 - 8;
         int viewH = Math.max(30, bodyBottom - bodyTop);
         CompactScrollbar.Metrics metrics = CompactScrollbar.compute(this.contentHeight(this.rows()), viewH, x + w - 6, bodyTop, 4, viewH, this.scroll);
         if (metrics.hasScroll() && metrics.contains(mx, my)) {
            this.scrollbarDragging = true;
            this.scrollbarGrabOffset = metrics.overThumb(mx, my) ? my - metrics.thumbY() : metrics.thumbHeight() / 2;
            this.scroll = CompactScrollbar.scrollFromThumb(metrics, my, this.scrollbarGrabOffset);
            return true;
         } else {
            int footerY = y + h - 36 + 7;
            if (this.hover(mx, my, x + 8, footerY, 78, 20)) {
               this.minecraft.gui.setScreen(this.parent);
               return true;
            } else if (this.hover(mx, my, x + 94, footerY, 78, 20)) {
               RiptideHudManager.resetElement(this.id);
               this.clearHudFocus();
               return true;
            } else if (this.hover(mx, my, x + w - 88, footerY, 82, 20)) {
               RiptideHudManager.toggle(this.id);
               return true;
            } else if (my >= bodyTop && my < bodyBottom) {
               RiptideHudElementSettingsScreen.Row row = this.rowAt(mx, my, x + 8, bodyTop - this.scroll, w - 20, bodyTop, bodyBottom);
               if (row != null && !row.section()) {
                  if (row.type != RiptideHudElementSettingsScreen.RowType.ENUM) {
                     this.handleRowClick(row, event.button(), mx, my, x + 8 + (w - 20) / 2, (w - 20) / 2 - 8);
                  }

                  return true;
               } else {
                  this.clearHudFocus();
                  return true;
               }
            } else {
               this.clearHudFocus();
               return true;
            }
         }
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      this.scrollbarDragging = false;
      this.draggingSlider = null;
      if (this.colorPicker != null) {
         int mx = RiptideUiScale.toVirtualInt(event.x());
         int my = RiptideUiScale.toVirtualInt(event.y());
         this.colorPicker.mouseReleased(mx, my, event.button());
         if (!this.colorPicker.isOpen()) {
            this.clearColorPicker();
         }

         return true;
      } else if (CompactDropdown.mouseReleased(this.enumDropdowns)) {
         return true;
      } else {
         if (this.editField != null) {
            this.editField.mouseReleased(RiptideUiScale.toVirtualInt(event.x()), RiptideUiScale.toVirtualInt(event.y()), event.button());
         }

         return true;
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      int mx = RiptideUiScale.toVirtualInt(event.x());
      int my = RiptideUiScale.toVirtualInt(event.y());
      if (this.draggingSlider != null) {
         int[] panel = this.panelBounds();
         int valueX = panel[0] + 8 + (panel[2] - 20) / 2;
         int valueW = (panel[2] - 20) / 2 - 8;
         this.setNumeric(
            this.draggingSlider,
            Slider.valueFromMouse(mx, valueX, Math.max(20, valueW - 57), this.draggingSlider.min, this.draggingSlider.max, this.draggingSlider.step)
         );
         return true;
      } else if (this.colorPicker != null) {
         this.colorPicker.mouseDragged(mx, my, event.button(), dx, dy);
         if (!this.colorPicker.isOpen()) {
            this.clearColorPicker();
         }

         return true;
      } else if (CompactDropdown.mouseDragged(this.enumDropdowns, mx, my, event.button())) {
         return true;
      } else if (this.focusedKey != null && this.editField != null && this.editField.mouseDragged(mx, my, event.button(), dx, dy)) {
         return true;
      } else if (!this.scrollbarDragging) {
         return true;
      } else {
         int[] panel = this.panelBounds();
         int bodyTop = panel[1] + 24 + 7;
         int viewH = Math.max(30, panel[1] + panel[3] - 36 - 8 - bodyTop);
         CompactScrollbar.Metrics metrics = CompactScrollbar.compute(
            this.contentHeight(this.rows()), viewH, panel[0] + panel[2] - 6, bodyTop, 4, viewH, this.scroll
         );
         this.scroll = CompactScrollbar.scrollFromThumb(metrics, my, this.scrollbarGrabOffset);
         return true;
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      double virtualX = RiptideUiScale.toVirtual(mouseX);
      double virtualY = RiptideUiScale.toVirtual(mouseY);
      if (this.colorPicker != null) {
         this.colorPicker.mouseScrolled((int)Math.round(virtualX), (int)Math.round(virtualY), scrollY);
         return true;
      } else if (CompactDropdown.mouseScrolled(this.enumDropdowns, virtualX, virtualY, scrollY)) {
         return true;
      } else if (this.modulePickerOpen) {
         this.modulePickerScroll = this.clamp(this.modulePickerScroll + (scrollY < 0.0 ? 26 : -26), 0, this.modulePickerContentOverflow());
         return true;
      } else {
         int[] panel = this.panelBounds();
         int bodyTop = panel[1] + 24 + 7;
         int bodyBottom = panel[1] + panel[3] - 36 - 8;
         int contentH = this.contentHeight(this.rows());
         this.scroll = this.clamp(this.scroll + (scrollY < 0.0 ? 26 : -26), 0, Math.max(0, contentH - Math.max(30, bodyBottom - bodyTop)));
         return true;
      }
   }

   public boolean keyPressed(KeyEvent input) {
      if (this.colorPicker != null) {
         this.colorPicker.keyPressed(input.key(), input.scancode(), input.modifiers());
         if (!this.colorPicker.isOpen()) {
            this.clearColorPicker();
         }

         return true;
      } else if (input.key() == 256) {
         if (this.modulePickerOpen) {
            this.modulePickerOpen = false;
         } else if (this.focusedKey != null) {
            this.clearHudFocus();
         } else {
            this.minecraft.gui.setScreen(this.parent);
         }

         return true;
      } else {
         if (this.focusedKey != null) {
            if (input.key() == 257 || input.key() == 335) {
               this.commitFocus();
               return true;
            }

            if (this.editField != null && this.editField.keyPressed(input)) {
               return true;
            }
         }

         return true;
      }
   }

   public boolean charTyped(CharacterEvent input) {
      if (this.colorPicker != null) {
         this.colorPicker.charTyped((char)input.codepoint());
         if (!this.colorPicker.isOpen()) {
            this.clearColorPicker();
         }

         return true;
      } else {
         if (this.focusedKey != null && this.editField != null) {
            this.editField.charTyped(input);
         }

         return true;
      }
   }

   private void handleRowClick(RiptideHudElementSettingsScreen.Row row, int button, int mx, int my, int valueX, int valueW) {
      int dir = button == 1 ? -1 : 1;
      if (row.type == RiptideHudElementSettingsScreen.RowType.BOOL) {
         RiptideHudManager.setSetting(this.id, row.key, Boolean.toString(!this.bool(row.key)));
         this.clearHudFocus();
      } else if (row.type == RiptideHudElementSettingsScreen.RowType.COLOR) {
         this.openColorPicker(row.key, UiBounds.of(valueX, Math.max(0, my - 13), valueW, 26));
         this.clearHudFocus();
      } else if (row.type == RiptideHudElementSettingsScreen.RowType.MODULE_LIST) {
         this.modulePickerOpen = true;
         this.modulePickerScroll = 0;
         this.clearHudFocus();
      } else if (row.type == RiptideHudElementSettingsScreen.RowType.NUMBER) {
         int fieldX = valueX + valueW - 50;
         if (mx >= fieldX && mx < fieldX + 50) {
            this.startEditing(row.key, RiptideHudManager.setting(this.id, row.key));
            return;
         }

         double value = Slider.valueFromMouse(mx, valueX, Math.max(20, valueW - 57), row.min, row.max, row.step);
         this.setNumeric(row, value);
         this.draggingSlider = row;
         this.clearHudFocus();
      } else if (row.type == RiptideHudElementSettingsScreen.RowType.ITEM) {
         this.clearHudFocus();
         this.minecraft
            .gui
            .setScreen(
               new RiptideItemPickerScreen(this, RiptideHudManager.setting(this.id, row.key), valuex -> RiptideHudManager.setSetting(this.id, row.key, valuex))
            );
      }
   }

   private void openColorPicker(String key, UiBounds anchor) {
      if (key != null && !key.isBlank()) {
         this.colorPickerKey = key;
         this.modulePickerOpen = false;
         CompactDropdown.closeOpenMenu(this.enumDropdowns);
         this.clearHudFocus();
         int fallback = this.defaultColor(key);
         int initial = RiptideHudManager.parseColor(RiptideHudManager.setting(this.id, key), fallback);
         this.colorPicker = new ColorPicker(
            anchor, initial, fallback, RiptideUiScale.getVirtualScreenWidth(), RiptideUiScale.getVirtualScreenHeight(), argb -> {
               RiptideHudManager.setSetting(this.id, key, String.format(Locale.ROOT, "%08X", argb));
               this.clearColorPicker();
            }
         );
      }
   }

   private void clearColorPicker() {
      if (this.colorPicker != null) {
         this.colorPicker.closeCancel();
      }

      this.colorPicker = null;
      this.colorPickerKey = null;
   }

   private int defaultColor(String key) {
      return RiptideHudManager.parseColor(RiptideHudManager.defaultSetting(this.id, key), -1);
   }

   private void setNumeric(RiptideHudElementSettingsScreen.Row row, double value) {
      double clamped = Math.max(row.min, Math.min(row.max, value));
      RiptideHudManager.setSetting(this.id, row.key, this.formatNumber(clamped, row.step));
   }

   private void commitFocus() {
      RiptideHudElementSettingsScreen.Row row = this.findRow(this.focusedKey);
      if (row == null) {
         this.clearHudFocus();
      } else {
         String editingText = this.editField == null ? "" : this.editField.getText();
         if (row.type == RiptideHudElementSettingsScreen.RowType.NUMBER) {
            try {
               this.setNumeric(row, Double.parseDouble(editingText.trim()));
            } catch (NumberFormatException var4) {
            }
         }

         this.clearHudFocus();
      }
   }

   private void clearHudFocus() {
      this.focusedKey = null;
      if (this.editField != null) {
         this.editField.setFocused(false);
         this.editField.setText("");
      }
   }

   private RiptideHudElementSettingsScreen.Row rowAt(int mx, int my, int x, int y, int w, int clipTop, int clipBottom) {
      int cy = y;

      for (RiptideHudElementSettingsScreen.Row row : this.rows()) {
         int h = row.section() ? 22 : 26;
         if (!row.section() && my >= clipTop && my < clipBottom && this.hover(mx, my, x, cy, w, h - 2)) {
            return row;
         }

         cy += h;
      }

      return null;
   }

   private RiptideHudElementSettingsScreen.Row findRow(String key) {
      if (key == null) {
         return null;
      } else {
         for (RiptideHudElementSettingsScreen.Row row : this.rows()) {
            if (key.equals(row.key)) {
               return row;
            }
         }

         return null;
      }
   }

   private List<RiptideHudElementSettingsScreen.Row> rows() {
      long rev = hudSettingsRevision();
      RiptideHudElementSettingsScreen.CachedRows cached = cachedRowLists.get(this.id);
      if (cached != null && cached.revision == rev) {
         return cached.rows;
      } else {
         List<RiptideHudElementSettingsScreen.Row> rows = new ArrayList<>();
         boolean isArrayList = "active_modules".equals(this.id);
         boolean customColors = isArrayList || RiptideHudManager.boolSetting(this.id, "use-custom-colors");
         rows.add(RiptideHudElementSettingsScreen.Row.section("Visibility"));
         if (!this.visualOnlyElement()) {
            rows.add(RiptideHudElementSettingsScreen.Row.enumRow("alignment", "Alignment", "Left", "Center", "Right"));
         }

         if (!isArrayList) {
            rows.add(RiptideHudElementSettingsScreen.Row.bool("use-custom-colors", "Custom Colors"));
         }

         if ("watermark".equals(this.id)) {
            rows.add(RiptideHudElementSettingsScreen.Row.section("Logo"));
            rows.add(RiptideHudElementSettingsScreen.Row.number("logo-width", "Width", RiptideHudElementSettingsScreen.RowType.NUMBER, 48.0, 420.0, 1.0));
         } else if (customColors && !"armor".equals(this.id) && !"inventory".equals(this.id)) {
            rows.add(RiptideHudElementSettingsScreen.Row.section("Text"));
            if (!"compass".equals(this.id)) {
               rows.add(RiptideHudElementSettingsScreen.Row.color("label-color", "Label Color"));
               rows.add(RiptideHudElementSettingsScreen.Row.color("value-color", "Value Color"));
            }

            rows.add(RiptideHudElementSettingsScreen.Row.color("accent-color", "Accent Color"));
         }

         rows.add(RiptideHudElementSettingsScreen.Row.section("Background"));
         rows.add(RiptideHudElementSettingsScreen.Row.bool("background", "Background"));
         if (customColors && RiptideHudManager.boolSetting(this.id, "background")) {
            rows.add(RiptideHudElementSettingsScreen.Row.color("background-color", "Background Color"));
         }

         rows.add(RiptideHudElementSettingsScreen.Row.section("Outline"));
         rows.add(RiptideHudElementSettingsScreen.Row.bool("outline", "Outline"));
         if (RiptideHudManager.boolSetting(this.id, "outline")) {
            if (customColors) {
               rows.add(RiptideHudElementSettingsScreen.Row.color("outline-color", "Outline Color"));
            }

            rows.add(
               RiptideHudElementSettingsScreen.Row.number("outline-width", "Outline Width", RiptideHudElementSettingsScreen.RowType.NUMBER, 1.0, 6.0, 1.0)
            );
         }

         if ("armor".equals(this.id) || "inventory".equals(this.id)) {
            rows.add(RiptideHudElementSettingsScreen.Row.section("Slots"));
            rows.add(RiptideHudElementSettingsScreen.Row.enumRow("slot-style", "Slot Style", "Textured", "Flat"));
         }

         if ("active_modules".equals(this.id)) {
            rows.add(RiptideHudElementSettingsScreen.Row.section("Active Modules"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("module-info", "Module Info"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("show-keybind", "Show Keybind"));
            rows.add(RiptideHudElementSettingsScreen.Row.enumRow("sort", "Sort", "Width", "Name", "Category"));
            rows.add(RiptideHudElementSettingsScreen.Row.enumRow("color-mode", "Color Mode", "Flat", "Random", "Rainbow", "Gradient"));
            String mode = RiptideHudManager.setting(this.id, "color-mode");
            if ("Flat".equals(mode)) {
               rows.add(RiptideHudElementSettingsScreen.Row.color("flat-color", "Flat Color"));
            }

            if ("Gradient".equals(mode)) {
               rows.add(RiptideHudElementSettingsScreen.Row.color("gradient-start-color", "Gradient Start"));
               rows.add(RiptideHudElementSettingsScreen.Row.color("gradient-end-color", "Gradient End"));
            }

            if (RiptideHudManager.boolSetting(this.id, "module-info") || RiptideHudManager.boolSetting(this.id, "show-keybind")) {
               rows.add(RiptideHudElementSettingsScreen.Row.color("module-info-color", "Info Color"));
            }

            if ("Rainbow".equals(mode) || "Gradient".equals(mode)) {
               rows.add(
                  RiptideHudElementSettingsScreen.Row.number("rainbow-speed", "Animation Speed", RiptideHudElementSettingsScreen.RowType.NUMBER, 0.1, 2.0, 0.05)
               );
               rows.add(
                  RiptideHudElementSettingsScreen.Row.number("rainbow-spread", "Row Spread", RiptideHudElementSettingsScreen.RowType.NUMBER, 0.001, 0.12, 0.001)
               );
            }

            if ("Rainbow".equals(mode)) {
               rows.add(
                  RiptideHudElementSettingsScreen.Row.number("rainbow-saturation", "Saturation", RiptideHudElementSettingsScreen.RowType.NUMBER, 0.0, 1.0, 0.05)
               );
               rows.add(
                  RiptideHudElementSettingsScreen.Row.number("rainbow-brightness", "Brightness", RiptideHudElementSettingsScreen.RowType.NUMBER, 0.0, 1.0, 0.05)
               );
               rows.add(RiptideHudElementSettingsScreen.Row.enumRow("rainbow-direction", "Direction", "Forward", "Reverse"));
            }

            rows.add(RiptideHudElementSettingsScreen.Row.number("stair-snap", "Stair Snap", RiptideHudElementSettingsScreen.RowType.NUMBER, 0.0, 24.0, 1.0));
            rows.add(RiptideHudElementSettingsScreen.Row.moduleList("hidden-modules", "Hidden Modules"));
         }

         if ("tps".equals(this.id)) {
            rows.add(RiptideHudElementSettingsScreen.Row.section("TPS"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("tps-precise", "Precise"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("tps-color-threshold", "Color Thresholds"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("tps-show-jitter", "Show Min"));
         }

         if ("ping".equals(this.id)) {
            rows.add(RiptideHudElementSettingsScreen.Row.section("Ping"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("ping-color-threshold", "Color Thresholds"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("ping-show-jitter", "Show Jitter"));
         }

         if ("cps".equals(this.id)) {
            rows.add(RiptideHudElementSettingsScreen.Row.section("CPS"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("show-total", "Show Total"));
         }

         if ("durability".equals(this.id)) {
            rows.add(RiptideHudElementSettingsScreen.Row.section("Durability"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("show-item-name", "Show Item Name"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("low-durability-warn", "Low Durability Warning"));
         }

         if ("looking_at".equals(this.id)) {
            rows.add(RiptideHudElementSettingsScreen.Row.section("Looking At"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("show-distance", "Show Distance"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("show-block-id", "Show Block ID"));
         }

         if ("breaking_progress".equals(this.id)) {
            rows.add(RiptideHudElementSettingsScreen.Row.section("Breaking Progress"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("show-block-name", "Show Block Name"));
         }

         if ("weather".equals(this.id)) {
            rows.add(RiptideHudElementSettingsScreen.Row.section("Weather"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("show-temperature", "Show Temperature"));
         }

         if ("world_time".equals(this.id)) {
            rows.add(RiptideHudElementSettingsScreen.Row.section("World Time"));
            rows.add(RiptideHudElementSettingsScreen.Row.enumRow("world-time-format", "Format", "24h", "ticks"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("show-day", "Show Day"));
         }

         if ("real_time".equals(this.id)) {
            rows.add(RiptideHudElementSettingsScreen.Row.section("Real Time"));
            rows.add(RiptideHudElementSettingsScreen.Row.enumRow("real-time-format", "Format", "12h", "24h"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("show-date", "Show Date"));
         }

         if ("memory".equals(this.id)) {
            rows.add(RiptideHudElementSettingsScreen.Row.section("Memory"));
            rows.add(RiptideHudElementSettingsScreen.Row.enumRow("memory-format", "Format", "used/max", "used%"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("show-bar", "Show Bar"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("show-percent", "Show Percentage"));
         }

         if ("server_ip".equals(this.id)) {
            rows.add(RiptideHudElementSettingsScreen.Row.section("Server IP"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("show-port", "Show Port"));
         }

         if ("fps_graph".equals(this.id)) {
            rows.add(RiptideHudElementSettingsScreen.Row.section("FPS Graph"));
            rows.add(RiptideHudElementSettingsScreen.Row.number("graph-samples", "Samples", RiptideHudElementSettingsScreen.RowType.NUMBER, 50.0, 200.0, 10.0));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("show-current-fps", "Show Current FPS"));
         }

         if ("item_counter".equals(this.id)) {
            rows.add(RiptideHudElementSettingsScreen.Row.section("Item Counter"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("item-count-held", "Count Held Item"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("item-show-name", "Show Item Name"));
            if (!RiptideHudManager.boolSetting(this.id, "item-count-held")) {
               rows.add(RiptideHudElementSettingsScreen.Row.item("item-id", "Item"));
            }
         }

         if ("keystrokes".equals(this.id)) {
            rows.add(RiptideHudElementSettingsScreen.Row.section("Keystrokes"));
            rows.add(RiptideHudElementSettingsScreen.Row.number("keystroke-size", "Key Size", RiptideHudElementSettingsScreen.RowType.NUMBER, 12.0, 40.0, 1.0));
            if (customColors) {
               rows.add(RiptideHudElementSettingsScreen.Row.color("keystroke-active-color", "Fill Color"));
               rows.add(RiptideHudElementSettingsScreen.Row.color("keystroke-idle-color", "Key Color"));
               rows.add(RiptideHudElementSettingsScreen.Row.color("keystroke-text-color", "Text Color"));
            }

            rows.add(RiptideHudElementSettingsScreen.Row.bool("keystroke-show-space", "Show Space"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("keystroke-show-mouse", "Show Mouse"));
         }

         if ("spotify".equals(this.id)) {
            rows.add(RiptideHudElementSettingsScreen.Row.section("Spotify"));
            rows.add(RiptideHudElementSettingsScreen.Row.number("spotify-width", "Width", RiptideHudElementSettingsScreen.RowType.NUMBER, 140.0, 260.0, 1.0));
            rows.add(
               RiptideHudElementSettingsScreen.Row.number(
                  "spotify-scroll-speed", "Scroll Speed", RiptideHudElementSettingsScreen.RowType.NUMBER, 10.0, 60.0, 1.0
               )
            );
            rows.add(RiptideHudElementSettingsScreen.Row.enumRow("spotify-source", "Source", "Spotify", "Any Media"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("spotify-menu-strip", "Menu Strip"));
            rows.add(RiptideHudElementSettingsScreen.Row.section("Colors"));
            rows.add(RiptideHudElementSettingsScreen.Row.enumRow("spotify-color-mode", "Color Mode", "Theme", "Custom", "Rainbow"));
            String spotifyMode = RiptideHudManager.setting(this.id, "spotify-color-mode");
            if ("Custom".equals(spotifyMode) || "Flat".equals(spotifyMode)) {
               rows.add(RiptideHudElementSettingsScreen.Row.color("spotify-title-color", "Title Color"));
               rows.add(RiptideHudElementSettingsScreen.Row.color("spotify-artist-color", "Artist Color"));
               rows.add(RiptideHudElementSettingsScreen.Row.color("spotify-time-color", "Time Color"));
               rows.add(RiptideHudElementSettingsScreen.Row.color("spotify-progress-color", "Progress Color"));
            } else if ("Rainbow".equals(spotifyMode)) {
               rows.add(RiptideHudElementSettingsScreen.Row.bool("spotify-rainbow-artist", "Rainbow Artist"));
               rows.add(RiptideHudElementSettingsScreen.Row.bool("spotify-rainbow-title", "Rainbow Title"));
               rows.add(RiptideHudElementSettingsScreen.Row.bool("spotify-rainbow-time", "Rainbow Time"));
               rows.add(RiptideHudElementSettingsScreen.Row.bool("spotify-rainbow-progress", "Rainbow Timeline"));
               rows.add(
                  RiptideHudElementSettingsScreen.Row.number("rainbow-speed", "Animation Speed", RiptideHudElementSettingsScreen.RowType.NUMBER, 0.1, 2.0, 0.05)
               );
               rows.add(
                  RiptideHudElementSettingsScreen.Row.number(
                     "rainbow-spread", "Two-Tone Spread", RiptideHudElementSettingsScreen.RowType.NUMBER, 0.001, 0.12, 0.001
                  )
               );
               rows.add(
                  RiptideHudElementSettingsScreen.Row.number("rainbow-saturation", "Saturation", RiptideHudElementSettingsScreen.RowType.NUMBER, 0.0, 1.0, 0.05)
               );
               rows.add(
                  RiptideHudElementSettingsScreen.Row.number("rainbow-brightness", "Brightness", RiptideHudElementSettingsScreen.RowType.NUMBER, 0.0, 1.0, 0.05)
               );
               rows.add(RiptideHudElementSettingsScreen.Row.enumRow("spotify-rainbow-direction", "Direction", "Forward", "Reverse"));
               rows.add(RiptideHudElementSettingsScreen.Row.color("spotify-title-color", "Title Color"));
               rows.add(RiptideHudElementSettingsScreen.Row.color("spotify-artist-color", "Artist Color"));
               rows.add(RiptideHudElementSettingsScreen.Row.color("spotify-time-color", "Time Color"));
               rows.add(RiptideHudElementSettingsScreen.Row.color("spotify-progress-color", "Progress Color"));
            }

            rows.add(RiptideHudElementSettingsScreen.Row.section("Parts"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("spotify-part-art", "Album Art"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("spotify-part-artist", "Artist"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("spotify-part-time", "Time"));
            rows.add(RiptideHudElementSettingsScreen.Row.enumRow("spotify-time-position", "Time Position", "Top", "Bottom"));
            rows.add(RiptideHudElementSettingsScreen.Row.bool("spotify-part-progress", "Progress Bar"));
         }

         List<RiptideHudElementSettingsScreen.Row> frozen = List.copyOf(rows);
         cachedRowLists.put(this.id, new RiptideHudElementSettingsScreen.CachedRows(rev, frozen));
         return frozen;
      }
   }

   private static long hudSettingsRevision() {
      return (long)RiptideHudManager.settingsRevision() << 32 | ModuleRegistry.revision() & 4294967295L;
   }

   private List<Module> moduleRows() {
      return new ArrayList<>(ModuleRegistry.all());
   }

   private List<String> hiddenModuleIds() {
      List<String> ids = new ArrayList<>();
      String raw = RiptideHudManager.setting("active_modules", "hidden-modules");
      if (raw != null && !raw.isBlank()) {
         for (String token : raw.split("\\|")) {
            String parsed = token.trim().toLowerCase(Locale.ROOT);
            if (!parsed.isBlank() && !ids.contains(parsed)) {
               ids.add(parsed);
            }
         }

         return ids;
      } else {
         return ids;
      }
   }

   private void toggleHiddenModule(String moduleId) {
      if (moduleId != null && !moduleId.isBlank()) {
         String normalized = moduleId.trim().toLowerCase(Locale.ROOT);
         List<String> ids = this.hiddenModuleIds();
         if (ids.contains(normalized)) {
            ids.remove(normalized);
         } else {
            ids.add(normalized);
         }

         RiptideHudManager.setSetting("active_modules", "hidden-modules", String.join("|", ids));
      }
   }

   private int modulePickerContentOverflow() {
      int sh = RiptideUiScale.getVirtualScreenHeight();
      int rowH = 20;
      int contentH = 28 + this.moduleRows().size() * rowH;
      int h = DirectLayout.fitPanelDimension(sh, 12, Math.max(128, contentH));
      return Math.max(0, contentH - h);
   }

   private void handleModulePickerClick(int mx, int my) {
      int sw = RiptideUiScale.getVirtualScreenWidth();
      int sh = RiptideUiScale.getVirtualScreenHeight();
      int w = DirectLayout.fitPanelDimension(sw, 12, 260);
      List<Module> modules = this.moduleRows();
      int rowH = 20;
      int contentH = 28 + modules.size() * rowH;
      int h = DirectLayout.fitPanelDimension(sh, 12, Math.max(128, contentH));
      int x = DirectLayout.centerPanel(sw, w, 12);
      int y = DirectLayout.centerPanel(sh, h, 12);
      if (!this.isOverTopBarClose(mx, my, x, y, w, h, 24) && this.hover(mx, my, x, y, w, h)) {
         int viewTop = y + 28;
         if (this.hover(mx, my, x + 2, viewTop, w - 4, h - 32)) {
            int index = (my - viewTop + this.modulePickerScroll) / rowH;
            if (index >= 0 && index < modules.size()) {
               this.toggleHiddenModule(modules.get(index).id());
            }
         }
      } else {
         this.modulePickerOpen = false;
      }
   }

   private boolean visualOnlyElement() {
      return "armor".equals(this.id) || "inventory".equals(this.id) || "compass".equals(this.id) || "watermark".equals(this.id) || "keystrokes".equals(this.id);
   }

   private void drawTopBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int headerHeight, String title, int mx, int my) {
      UiBounds bounds = UiBounds.of(x, y, width, height);
      CompactScreenPanel.render(
         UiContexts.overlay(graphics, this.font, mx, my), bounds, headerHeight, title, mx >= x && mx < x + width && my >= y && my < y + headerHeight
      );
   }

   private boolean isOverTopBarClose(int mx, int my, int x, int y, int width, int height, int headerHeight) {
      return CompactScreenPanel.isOverClose(UiBounds.of(x, y, width, height), headerHeight, mx, my);
   }

   private int preferredHeight() {
      return 60 + Math.min(430, this.contentHeight(this.rows()) + 18);
   }

   private int contentHeight(List<RiptideHudElementSettingsScreen.Row> rows) {
      int h = 0;

      for (RiptideHudElementSettingsScreen.Row row : rows) {
         h += row.section() ? 22 : 26;
      }

      return h;
   }

   private int[] panelBounds() {
      int sw = RiptideUiScale.getVirtualScreenWidth();
      int sh = RiptideUiScale.getVirtualScreenHeight();
      int w = DirectLayout.fitPanelDimension(sw, 8, 368);
      int h = DirectLayout.fitPanelDimension(sh, 8, Math.max(180, this.preferredHeight()));
      return new int[]{DirectLayout.centerPanel(sw, w, 8), DirectLayout.centerPanel(sh, h, 8), w, h};
   }

   private boolean compactLayout(int width, int height) {
      return width < 220 || height < 92;
   }

   private boolean bool(String key) {
      return RiptideHudManager.boolSetting(this.id, key);
   }

   private String formatNumber(double value, double step) {
      return step >= 1.0 ? Integer.toString((int)Math.round(value)) : String.format(Locale.ROOT, step < 0.01 ? "%.3f" : "%.2f", value);
   }

   private boolean visible(int y, int h, int top, int bottom) {
      return y + h >= top && y <= bottom;
   }

   private int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private boolean hover(int mx, int my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }

   private void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill, int border) {
      UiRenderer.frame(graphics, UiBounds.of(x, y, w, h), fill, border);
   }

   private void draw(GuiGraphicsExtractor graphics, String text, int x, int y, int color, int maxW) {
      String trimmed = UiText.trimToWidth(this.font, text, maxW, THEME.fontFor(UiTone.BODY), color);
      UiText.draw(graphics, this.font, trimmed, THEME.fontFor(UiTone.BODY), color, x, y, false);
   }

   private int textYInset(int h) {
      return Math.max(3, (h - THEME.fontHeight(UiTone.BODY) + 1) / 2 + 1);
   }

   private void drawCentered(GuiGraphicsExtractor graphics, String text, int x, int y, int w, int h, int color) {
      String trimmed = UiText.trimToWidth(this.font, text, w - 8, THEME.fontFor(UiTone.BODY), color);
      int tw = UiText.width(this.font, trimmed, THEME.fontFor(UiTone.BODY), color);
      UiText.draw(graphics, this.font, trimmed, THEME.fontFor(UiTone.BODY), color, x + Math.max(4, (w - tw) / 2), y + this.textYInset(h), false);
   }

   private record CachedRows(long revision, List<RiptideHudElementSettingsScreen.Row> rows) {
   }

   private record Row(String key, String label, RiptideHudElementSettingsScreen.RowType type, double min, double max, double step, List<String> choices) {
      static RiptideHudElementSettingsScreen.Row section(String label) {
         return new RiptideHudElementSettingsScreen.Row("", label, RiptideHudElementSettingsScreen.RowType.SECTION, 0.0, 0.0, 1.0, List.of());
      }

      static RiptideHudElementSettingsScreen.Row bool(String key, String label) {
         return new RiptideHudElementSettingsScreen.Row(key, label, RiptideHudElementSettingsScreen.RowType.BOOL, 0.0, 1.0, 1.0, List.of());
      }

      static RiptideHudElementSettingsScreen.Row enumRow(String key, String label, String... choices) {
         return new RiptideHudElementSettingsScreen.Row(key, label, RiptideHudElementSettingsScreen.RowType.ENUM, 0.0, 1.0, 1.0, List.of(choices));
      }

      static RiptideHudElementSettingsScreen.Row color(String key, String label) {
         return new RiptideHudElementSettingsScreen.Row(key, label, RiptideHudElementSettingsScreen.RowType.COLOR, 0.0, 1.0, 1.0, List.of());
      }

      static RiptideHudElementSettingsScreen.Row number(
         String key, String label, RiptideHudElementSettingsScreen.RowType type, double min, double max, double step
      ) {
         return new RiptideHudElementSettingsScreen.Row(key, label, type, min, max, step, List.of());
      }

      static RiptideHudElementSettingsScreen.Row item(String key, String label) {
         return new RiptideHudElementSettingsScreen.Row(key, label, RiptideHudElementSettingsScreen.RowType.ITEM, 0.0, 1.0, 1.0, List.of());
      }

      static RiptideHudElementSettingsScreen.Row moduleList(String key, String label) {
         return new RiptideHudElementSettingsScreen.Row(key, label, RiptideHudElementSettingsScreen.RowType.MODULE_LIST, 0.0, 1.0, 1.0, List.of());
      }

      boolean section() {
         return this.type == RiptideHudElementSettingsScreen.RowType.SECTION;
      }
   }

   private static enum RowType {
      BOOL,
      ENUM,
      COLOR,
      NUMBER,
      ITEM,
      MODULE_LIST,
      SECTION;
   }
}
