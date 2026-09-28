package riptide.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.gui.vanillaui.components.CompactListRenderer;
import riptide.gui.vanillaui.components.CompactListViewport;
import riptide.gui.vanillaui.components.CompactOverlayButton;
import riptide.gui.vanillaui.components.CompactOverlayControls;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactTextInput;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.ScrollState;
import riptide.gui.vanillaui.components.UiSizing;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectRenderContext;
import riptide.gui.vanillaui.direct.DirectViewport;
import riptide.modules.RiptideModule;

public class RiptideCustomFilterPresetOverlay extends RiptideOverlayBase {
   private final Font textRenderer;
   private final CompactTheme theme = new CompactTheme();
   private final CompactTextInput nameField;
   private final List<RiptideCustomFilterPresetOverlay.ActionButton> buttons = new ArrayList<>();
   private final RiptideCustomFilterPresetOverlay.PresetListState presetListState = new RiptideCustomFilterPresetOverlay.PresetListState();
   private List<RiptidePresetManager.PresetEntry> cachedBuiltInEntries = List.of();
   private List<RiptidePresetManager.PresetEntry> cachedUserEntries = List.of();
   private List<RiptideCustomFilterPresetOverlay.PresetRow> cachedPresetRows = List.of();
   private int panelX = 570;
   private int panelY = 48;
   private int panelWidth;
   private int panelHeight;
   private boolean visible = false;
   private boolean collapsed = false;
   private boolean dragging = false;
   private double dragOffsetX = 0.0;
   private double dragOffsetY = 0.0;
   private boolean presetScrollbarDragging = false;
   private int presetScrollbarGrabOffset = 0;
   private String selectedPresetName;

   public RiptideCustomFilterPresetOverlay(Font textRenderer) {
      this.textRenderer = textRenderer;
      this.panelWidth = this.defaultPanelWidth();
      this.panelHeight = this.defaultPanelHeight();
      this.nameField = new CompactTextInput()
         .setPlaceholder("Preset name")
         .setFieldHeight(this.inputHeight())
         .setMinWidth(120.0F)
         .setPreferredWidth(120.0F)
         .setTextTone(UiTone.BODY)
         .setPlaceholderTone(UiTone.MUTED);
   }

   @Override
   public String getOverlayId() {
      return "riptide-custom-filter-presets";
   }

   @Override
   public int getMinWidth() {
      return this.defaultPanelWidth();
   }

   @Override
   public int getMinHeight() {
      return this.minimumPanelHeight();
   }

   @Override
   public RiptideWindowLayout getBounds() {
      return new RiptideWindowLayout(this.panelX, this.panelY, this.panelWidth, this.panelHeight, this.visible, this.collapsed);
   }

   @Override
   public void setBounds(RiptideWindowLayout bounds) {
      if (bounds != null) {
         RiptideWindowLayout clamped = this.clampToScreen(this, bounds);
         this.panelX = clamped.x;
         this.panelY = clamped.y;
         this.panelWidth = clamped.width;
         this.panelHeight = clamped.height;
         this.visible = clamped.visible;
         this.collapsed = clamped.collapsed;
      }
   }

   @Override
   public void setVisible(boolean visible) {
      this.visible = visible;
      if (visible) {
         RiptideOverlayManager.get().bringToFront(this);
      } else {
         this.clearFocus();
      }

      this.saveLayout();
   }

   public void toggle() {
      this.setVisible(!this.visible);
   }

   @Override
   public boolean isVisible() {
      return this.visible;
   }

   @Override
   public boolean isCollapsed() {
      return this.collapsed;
   }

   @Override
   public void setCollapsed(boolean collapsed) {
      if (this.collapsed != collapsed) {
         this.collapsed = collapsed;
         if (collapsed) {
            this.clearHiddenInteractionState();
         }

         this.saveLayout();
      }
   }

   @Override
   public boolean usesSharedHeaderClickCollapse() {
      return true;
   }

   @Override
   public boolean isOverDragBar(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else {
         RiptideWindowLayout bounds = this.getBounds();
         return mouseX >= this.panelX
            && mouseX <= this.panelX + this.panelWidth
            && mouseY >= this.panelY
            && mouseY <= this.panelY + 16
            && !this.isOverWindowControl(mouseX, mouseY, bounds);
      }
   }

   @Override
   public boolean hasTextFieldFocused() {
      return this.nameField.isFocused();
   }

   @Override
   public void clearTextFieldFocus() {
      this.clearFocus();
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      if (this.visible) {
         this.buttons.clear();
         RiptideWindowLayout clamped = this.clampToScreen(
            this, new RiptideWindowLayout(this.panelX, this.panelY, this.panelWidth, this.panelHeight, this.visible, this.collapsed)
         );
         this.panelX = clamped.x;
         this.panelY = clamped.y;
         this.panelWidth = clamped.width;
         this.panelHeight = clamped.height;
         RiptideWindowLayout bounds = new RiptideWindowLayout(this.panelX, this.panelY, this.panelWidth, this.panelHeight, this.visible, this.collapsed);
         this.renderWindowFrame(context, mouseX, mouseY, bounds, "Preset Manager", this.collapsed, this.dragging);
         boolean clipBody = this.beginWindowBodyClip(context, bounds, this.collapsed);
         if (!clipBody) {
            this.renderWindowInactiveOverlay(context, bounds, this.collapsed, this.dragging);
         } else {
            try {
               int x = this.panelX + this.panelInset();
               int y = this.panelY + 16 + this.topInset();
               int width = this.panelWidth - this.panelInset() * 2;
               RiptideModule module = RiptideModule.get();
               RiptideText.draw(
                  context,
                  this.textRenderer,
                  "C2S " + module.getC2SPackets().size() + " | S2C " + module.getS2CPackets().size(),
                  RiptideText.Tone.MUTED,
                  x,
                  y,
                  false
               );
               y += this.infoLineHeight();
               CompactListRenderer.drawHeader(context, this.textRenderer, "Preset Name", x, y);
               y += this.sectionHeaderHeight();
               this.nameField.setBounds(x, y, width, this.inputHeight());
               this.nameField.render(this.renderContext(context, mouseX, mouseY, delta));
               y += this.inputHeight() + this.actionGap();
               int halfWidth = (width - this.actionGap()) / 2;
               y = this.drawActionRow(
                  context,
                  mouseX,
                  mouseY,
                  x,
                  y,
                  halfWidth,
                  "Save",
                  this::saveNamedPreset,
                  this.canSaveNamed(),
                  "Load",
                  this::loadSelectedPreset,
                  this.canLoadSelected()
               );
               y = this.drawActionRow(
                  context,
                  mouseX,
                  mouseY,
                  x,
                  y,
                  halfWidth,
                  "Overwrite",
                  this::overwriteSelectedPreset,
                  this.canOverwriteSelected(),
                  "Delete",
                  this::deleteSelectedPreset,
                  this.canDeleteSelected()
               );
               y = this.drawAction(context, mouseX, mouseY, x, y, width, "Reset To Default", this::resetDefaults, true);
               y += this.sectionGap() - this.actionGap();
               y = this.renderPresetSection(
                  context,
                  mouseX,
                  mouseY,
                  delta,
                  x,
                  y,
                  width,
                  Math.max(this.listMinimumHeight(), this.panelHeight - (y - this.panelY) - this.contentBottomPadding())
               );
            } finally {
               this.endWindowBodyClip(context, clipBody);
               this.renderWindowInactiveOverlay(context, bounds, this.collapsed, this.dragging);
            }
         }
      }
   }

   private int renderPresetSection(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, int x, int y, int width, int sectionHeight) {
      CompactListRenderer.drawHeader(context, this.textRenderer, "Saved Presets", x, y);
      y += this.sectionHeaderHeight();
      List<RiptideCustomFilterPresetOverlay.PresetRow> rows = this.getCachedPresetRows();
      this.presetListState.rows = rows;
      int listHeight = Math.max(this.listMinimumHeight(), sectionHeight - this.sectionHeaderHeight() - this.footerHeight() - this.footerGap());
      CompactListViewport.Layout baseLayout = this.presetListLayout(x, y, width, listHeight, rows.size(), 0);
      int visualScroll = this.presetListState.scroll.tick(delta, baseLayout.maxScroll());
      CompactListViewport.Layout listLayout = this.presetListLayout(x, y, width, listHeight, rows.size(), visualScroll);
      int contentWidth = Math.max(40, listLayout.contentWidth());
      this.presetListState.setBounds(x, y, width, listHeight, listLayout.scrollbar());
      boolean focused = RiptideOverlayManager.get().isFocusedOverlay(this) || RiptideOverlayManager.get().isTopOverlay(this);
      listLayout.drawFrame(context, focused);
      if (rows.isEmpty()) {
         CompactListRenderer.drawEmptyState(context, this.textRenderer, "No presets", x, y, contentWidth);
      } else {
         listLayout.beginRows(context);

         try {
            listLayout.forEachVisibleRow(
               rows.size(),
               (index, rowY) -> {
                  RiptideCustomFilterPresetOverlay.PresetRow row = rows.get(index);
                  int rowTextY = UiSizing.alignTextY(rowY, this.presetRowHeight(), this.theme.fontHeight(UiTone.BODY), this.theme.bodyTextNudge());
                  if (row.header) {
                     CompactListRenderer.drawRow(
                        context,
                        this.textRenderer,
                        row.label,
                        x + 2,
                        rowY,
                        contentWidth,
                        this.presetRowHeight(),
                        false,
                        false,
                        CompactListRenderer.RowTone.WARNING
                     );
                  } else if (row.note) {
                     CompactListRenderer.drawRow(
                        context,
                        this.textRenderer,
                        row.label,
                        x + 2,
                        rowY,
                        contentWidth,
                        this.presetRowHeight(),
                        false,
                        false,
                        CompactListRenderer.RowTone.NORMAL
                     );
                  } else if (row.entry != null) {
                     boolean selected = row.entry.name().equals(this.selectedPresetName);
                     boolean hovered = mouseX >= x + 2 && mouseX < x + 2 + contentWidth && mouseY >= rowY && mouseY < rowY + this.presetRowHeight();
                     CompactListRenderer.drawRow(
                        context,
                        this.textRenderer,
                        "",
                        x + 2,
                        rowY,
                        contentWidth,
                        this.presetRowHeight(),
                        hovered,
                        selected,
                        row.entry.builtIn() ? CompactListRenderer.RowTone.WARNING : CompactListRenderer.RowTone.NORMAL
                     );
                     int textColor = selected ? RiptideColors.rowSelectedText() : (row.entry.builtIn() ? -4729601 : RiptideColors.textLight());
                     String prefix = row.entry.builtIn() ? "[DEV] " : "[USR] ";
                     String name = RiptideText.trimToWidth(this.textRenderer, prefix + row.entry.name(), contentWidth - 70, RiptideText.Tone.BODY);
                     RiptideText.draw(context, this.textRenderer, name, textColor, x + 5, rowTextY, false);
                     String counts = row.entry.c2sCount() + " / " + row.entry.s2cCount();
                     int countsWidth = RiptideText.width(this.textRenderer, counts, RiptideText.Tone.BODY);
                     RiptideText.draw(context, this.textRenderer, counts, textColor, x + contentWidth - countsWidth - 1, rowTextY, false);
                  }

                  CompactListRenderer.drawDivider(context, x + 2, rowY + this.presetRowHeight(), contentWidth);
               }
            );
         } finally {
            listLayout.endRows(context);
         }
      }

      listLayout.drawScrollbar(context, mouseX, mouseY, this.presetScrollbarDragging);
      String footer = listLayout.maxScroll() > 0
         ? "Scroll: "
            + Math.min(rows.size(), Math.round((float)this.presetListState.scroll.targetOffset() / this.presetRowStep()) + 1)
            + "/"
            + Math.max(1, Math.round((float)listLayout.maxScroll() / this.presetRowStep()) + 1)
         : (this.selectedPresetName == null ? "Select a preset row" : "Selected: " + this.selectedPresetName);
      RiptideText.draw(context, this.textRenderer, footer, RiptideText.Tone.MUTED, x + 4, y + listHeight + this.footerGap(), false);
      return y + listHeight + this.footerHeight() + this.footerGap();
   }

   private List<RiptideCustomFilterPresetOverlay.PresetRow> getCachedPresetRows() {
      RiptidePresetManager manager = RiptidePresetManager.get();
      List<RiptidePresetManager.PresetEntry> builtInEntries = manager.getBuiltInPresetEntries();
      List<RiptidePresetManager.PresetEntry> userEntries = manager.getUserPresetEntries();
      if (builtInEntries == this.cachedBuiltInEntries && userEntries == this.cachedUserEntries) {
         return this.cachedPresetRows;
      } else {
         List<RiptideCustomFilterPresetOverlay.PresetRow> rows = new ArrayList<>();
         rows.add(RiptideCustomFilterPresetOverlay.PresetRow.header("Developer Templates"));

         for (RiptidePresetManager.PresetEntry entry : builtInEntries) {
            rows.add(RiptideCustomFilterPresetOverlay.PresetRow.entry(entry));
         }

         rows.add(RiptideCustomFilterPresetOverlay.PresetRow.header("User Presets"));
         if (userEntries.isEmpty()) {
            rows.add(RiptideCustomFilterPresetOverlay.PresetRow.note("No user presets yet"));
         } else {
            for (RiptidePresetManager.PresetEntry entry : userEntries) {
               rows.add(RiptideCustomFilterPresetOverlay.PresetRow.entry(entry));
            }
         }

         this.cachedBuiltInEntries = builtInEntries;
         this.cachedUserEntries = userEntries;
         this.cachedPresetRows = List.copyOf(rows);
         return this.cachedPresetRows;
      }
   }

   private void saveNamedPreset() {
      String name = this.sanitizeNameField();
      if (name == null) {
         RiptideClientMessaging.sendPrefixed("Enter a preset name first.");
      } else {
         if (RiptidePresetManager.get().savePreset(name)) {
            this.selectedPresetName = name;
            this.nameField.setText(name);
         }
      }
   }

   private void loadSelectedPreset() {
      String presetName = this.resolveTargetPresetName();
      if (presetName == null) {
         RiptideClientMessaging.sendPrefixed("Select a preset first.");
      } else {
         if (RiptidePresetManager.get().loadPreset(presetName)) {
            this.selectedPresetName = RiptidePresetManager.get().getPresetEntry(presetName) != null
               ? RiptidePresetManager.get().getPresetEntry(presetName).name()
               : presetName;
            this.nameField.setText(this.selectedPresetName);
         }
      }
   }

   private void overwriteSelectedPreset() {
      String presetName = this.resolveTargetPresetName();
      if (presetName == null) {
         RiptideClientMessaging.sendPrefixed("Select a user preset first.");
      } else {
         RiptidePresetManager.PresetEntry entry = RiptidePresetManager.get().getPresetEntry(presetName);
         if (entry != null && !entry.builtIn()) {
            if (RiptidePresetManager.get().overwriteUserPreset(entry.name())) {
               this.selectedPresetName = entry.name();
               this.nameField.setText(entry.name());
            }
         } else {
            RiptideClientMessaging.sendPrefixed("Select a user preset to overwrite.");
         }
      }
   }

   private void deleteSelectedPreset() {
      String presetName = this.resolveTargetPresetName();
      if (presetName == null) {
         RiptideClientMessaging.sendPrefixed("Select a user preset first.");
      } else {
         RiptidePresetManager.PresetEntry entry = RiptidePresetManager.get().getPresetEntry(presetName);
         if (entry != null && !entry.builtIn()) {
            if (RiptidePresetManager.get().deleteUserPreset(entry.name())) {
               if (entry.name().equals(this.selectedPresetName)) {
                  this.selectedPresetName = null;
               }

               this.nameField.setText("");
            }
         } else {
            RiptideClientMessaging.sendPrefixed("Developer presets cannot be deleted.");
         }
      }
   }

   private void resetDefaults() {
      RiptideModule module = RiptideModule.get();
      module.resetC2SPacketsToDefault();
      module.resetS2CPacketsToDefault();
      RiptideClientMessaging.sendPrefixed("Reset current filter to default packet lists.");
   }

   private String sanitizeNameField() {
      String value = this.nameField.text();
      if (value == null) {
         return null;
      } else {
         value = value.trim();
         return value.isEmpty() ? null : value;
      }
   }

   private String resolveTargetPresetName() {
      if (this.selectedPresetName != null) {
         RiptidePresetManager.PresetEntry selectedEntry = RiptidePresetManager.get().getPresetEntry(this.selectedPresetName);
         if (selectedEntry != null) {
            return selectedEntry.name();
         }
      }

      String typedName = this.sanitizeNameField();
      if (typedName == null) {
         return null;
      } else {
         RiptidePresetManager.PresetEntry typedEntry = RiptidePresetManager.get().getPresetEntry(typedName);
         return typedEntry != null ? typedEntry.name() : null;
      }
   }

   private RiptidePresetManager.PresetEntry getSelectedPresetEntry() {
      return this.selectedPresetName == null ? null : RiptidePresetManager.get().getPresetEntry(this.selectedPresetName);
   }

   private boolean canSaveNamed() {
      String name = this.sanitizeNameField();
      return name != null && !RiptidePresetManager.get().isReservedPresetName(name);
   }

   private boolean canLoadSelected() {
      return this.resolveTargetPresetName() != null;
   }

   private boolean canOverwriteSelected() {
      RiptidePresetManager.PresetEntry entry = this.getSelectedPresetEntry();
      return entry != null && !entry.builtIn();
   }

   private boolean canDeleteSelected() {
      return this.canOverwriteSelected();
   }

   private int drawAction(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y, int width, String label, Runnable action, boolean enabled) {
      this.drawActionButton(context, mouseX, mouseY, x, y, width, label, enabled);
      this.buttons.add(new RiptideCustomFilterPresetOverlay.ActionButton(x, y, width, this.actionHeight(), action, enabled));
      return y + this.actionHeight() + this.actionGap();
   }

   private int drawActionRow(
      GuiGraphicsExtractor context,
      int mouseX,
      int mouseY,
      int x,
      int y,
      int buttonWidth,
      String leftLabel,
      Runnable leftAction,
      boolean leftEnabled,
      String rightLabel,
      Runnable rightAction,
      boolean rightEnabled
   ) {
      this.drawActionButton(context, mouseX, mouseY, x, y, buttonWidth, leftLabel, leftEnabled);
      this.buttons.add(new RiptideCustomFilterPresetOverlay.ActionButton(x, y, buttonWidth, this.actionHeight(), leftAction, leftEnabled));
      int rightX = x + buttonWidth + this.actionGap();
      int rightWidth = this.panelWidth - this.panelInset() * 2 - buttonWidth - this.actionGap();
      this.drawActionButton(context, mouseX, mouseY, rightX, y, rightWidth, rightLabel, rightEnabled);
      this.buttons.add(new RiptideCustomFilterPresetOverlay.ActionButton(rightX, y, rightWidth, this.actionHeight(), rightAction, rightEnabled));
      return y + this.actionHeight() + this.actionGap();
   }

   private void drawActionButton(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y, int width, String label, boolean enabled) {
      CompactOverlayControls.action(
         context,
         this.textRenderer,
         x,
         y,
         width,
         this.actionHeight(),
         label,
         enabled ? CompactOverlayButton.Variant.SECONDARY : CompactOverlayButton.Variant.GHOST,
         enabled,
         mouseX,
         mouseY
      );
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.visible) {
         return false;
      } else if (button == 0 && mouseX >= this.panelX && mouseX < this.panelX + this.panelWidth && mouseY >= this.panelY && mouseY < this.panelY + 16) {
         RiptideWindowLayout bounds = this.getBounds();
         if (this.isOverCloseButton(mouseX, mouseY, bounds)) {
            this.setVisible(false);
            return true;
         } else {
            this.dragging = true;
            this.dragOffsetX = mouseX - this.panelX;
            this.dragOffsetY = mouseY - this.panelY;
            this.clearFocus();
            return true;
         }
      } else if (this.collapsed) {
         return false;
      } else {
         if (button == 0) {
            CompactScrollbar.Metrics scrollbar = this.presetListState.scrollbarMetrics;
            if (scrollbar != null && scrollbar.hasScroll() && scrollbar.contains(mouseX, mouseY)) {
               this.presetScrollbarDragging = true;
               this.presetScrollbarGrabOffset = Math.max(0, (int)mouseY - scrollbar.thumbY());
               this.presetListState.scroll.setFromThumbStepped(scrollbar, mouseY, this.presetScrollbarGrabOffset, this.presetRowStep());
               return true;
            }
         }

         if (this.handleTextFieldClick(this.nameField, mouseX, mouseY, button)) {
            return true;
         } else {
            if (button == 0 && this.presetListState.contains(mouseX, mouseY)) {
               RiptideCustomFilterPresetOverlay.PresetRow row = this.presetListState.getRowAt(mouseY);
               if (row != null && row.entry != null) {
                  this.selectedPresetName = row.entry.name();
                  this.nameField.setText(this.selectedPresetName);
                  this.clearFocus();
                  return true;
               }
            }

            this.clearFocus();
            if (button == 0) {
               for (RiptideCustomFilterPresetOverlay.ActionButton actionButton : this.buttons) {
                  if (actionButton.contains(mouseX, mouseY)) {
                     if (!actionButton.enabled()) {
                        return true;
                     }

                     actionButton.action.run();
                     return true;
                  }
               }
            }

            return this.isMouseOver(mouseX, mouseY);
         }
      }
   }

   private boolean handleTextFieldClick(CompactTextInput field, double mouseX, double mouseY, int button) {
      boolean clicked = field.mouseClicked(this.inputContext(mouseX, mouseY), (float)mouseX, (float)mouseY, button);
      if (clicked) {
         this.clearFocus();
         field.setFocused(true);
      }

      return clicked;
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.nameField.mouseReleased(this.inputContext(mouseX, mouseY), (float)mouseX, (float)mouseY, button)) {
         return true;
      } else if (button == 0 && this.presetScrollbarDragging) {
         this.presetScrollbarDragging = false;
         return true;
      } else {
         if (button == 0) {
            if (this.dragging) {
               this.saveLayout();
            }

            this.dragging = false;
         }

         return false;
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (this.nameField.mouseDragged(this.inputContext(mouseX, mouseY), (float)mouseX, (float)mouseY, button, (float)deltaX, (float)deltaY)) {
         return true;
      } else if (this.presetScrollbarDragging) {
         this.presetListState.scroll.setFromThumbStepped(this.presetListState.scrollbarMetrics, mouseY, this.presetScrollbarGrabOffset, this.presetRowStep());
         return true;
      } else if (this.dragging && button == 0) {
         RiptideWindowLayout nextBounds = this.clampToScreen(
            this,
            new RiptideWindowLayout(
               (int)(mouseX - this.dragOffsetX), (int)(mouseY - this.dragOffsetY), this.panelWidth, this.panelHeight, this.visible, this.collapsed
            )
         );
         this.panelX = nextBounds.x;
         this.panelY = nextBounds.y;
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (this.presetListState.contains(mouseX, mouseY)) {
         int maxScroll = this.presetListLayout(
               this.presetListState.x, this.presetListState.y, this.presetListState.width, this.presetListState.height, this.presetListState.rows.size(), 0
            )
            .maxScroll();
         this.presetListState.scroll.nudge(amount, this.presetRowStep(), maxScroll);
         return true;
      } else {
         return false;
      }
   }

   private CompactListViewport.Layout presetListLayout(int x, int y, int width, int listHeight, int rowCount, int scrollOffset) {
      return CompactListViewport.layout(
         x, y, width, listHeight, rowCount, this.presetRowHeight(), this.presetRowStep(), scrollOffset, this.scrollbarTrackWidth(), this.scrollbarGutter()
      );
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.visible) {
         return false;
      } else if (keyCode == 256) {
         if (this.nameField.isFocused()) {
            this.clearFocus();
            return true;
         } else {
            this.setVisible(false);
            return true;
         }
      } else if ((keyCode == 257 || keyCode == 335) && this.nameField.isFocused() && this.canSaveNamed()) {
         this.saveNamedPreset();
         return true;
      } else {
         return !this.nameField.isFocused() ? false : this.nameField.keyPressed(this.inputContext(0.0, 0.0), keyCode, scanCode, modifiers);
      }
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      return this.visible && this.nameField.isFocused() ? this.nameField.charTyped(this.inputContext(0.0, 0.0), chr, modifiers) : false;
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else {
         RiptideWindowLayout bounds = this.getBounds();
         int frameHeight = this.collapsed ? 16 : this.panelHeight;
         return mouseX >= bounds.x && mouseX <= bounds.x + bounds.width && mouseY >= bounds.y && mouseY <= bounds.y + frameHeight
            ? true
            : mouseX >= this.nameField.x()
               && mouseX <= this.nameField.x() + this.nameField.width()
               && mouseY >= this.nameField.y()
               && mouseY <= this.nameField.y() + this.nameField.height();
      }
   }

   private void clearFocus() {
      this.nameField.setFocused(false);
   }

   private DirectRenderContext renderContext(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      DirectViewport viewport = DirectViewport.current(1.0F);
      return new DirectRenderContext(context, this.textRenderer, viewport, this.theme, viewport.toUiX(mouseX), viewport.toUiY(mouseY), delta);
   }

   private DirectRenderContext inputContext(double mouseX, double mouseY) {
      DirectViewport viewport = DirectViewport.current(1.0F);
      return new DirectRenderContext(null, this.textRenderer, viewport, this.theme, viewport.toUiX(mouseX), viewport.toUiY(mouseY), 0.0F);
   }

   private int defaultPanelWidth() {
      return 236;
   }

   private int defaultPanelHeight() {
      return 318;
   }

   private int minimumPanelHeight() {
      return 250;
   }

   private int panelInset() {
      return 10;
   }

   private int topInset() {
      return 6;
   }

   private int contentBottomPadding() {
      return 12;
   }

   private int infoLineHeight() {
      return this.theme.lineHeight(UiTone.MUTED, 1);
   }

   private int sectionHeaderHeight() {
      return this.theme.lineHeight(UiTone.LABEL, 0);
   }

   private int actionHeight() {
      return 13;
   }

   private int actionGap() {
      return 2;
   }

   private int sectionGap() {
      return 6;
   }

   private int inputHeight() {
      return 16;
   }

   private int presetRowHeight() {
      return 14;
   }

   private int presetRowStep() {
      return this.presetRowHeight() + 1;
   }

   private int footerGap() {
      return 4;
   }

   private int footerHeight() {
      return this.theme.lineHeight(UiTone.MUTED, 1);
   }

   private int scrollbarGutter() {
      return 8;
   }

   private int scrollbarTrackWidth() {
      return 3;
   }

   private int listMinimumHeight() {
      return 52;
   }

   private record ActionButton(int x, int y, int width, int height, Runnable action, boolean enabled) {
      boolean contains(double mouseX, double mouseY) {
         return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
      }
   }

   private final class PresetListState {
      private int x;
      private int y;
      private int width;
      private int height;
      private final ScrollState scroll;
      private CompactScrollbar.Metrics scrollbarMetrics;
      private List<RiptideCustomFilterPresetOverlay.PresetRow> rows;

      private PresetListState() {
         Objects.requireNonNull(RiptideCustomFilterPresetOverlay.this);
         super();
         this.scroll = new ScrollState();
         this.scrollbarMetrics = null;
         this.rows = List.of();
      }

      private void setBounds(int x, int y, int width, int height, CompactScrollbar.Metrics scrollbarMetrics) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         this.scrollbarMetrics = scrollbarMetrics;
      }

      private boolean contains(double mouseX, double mouseY) {
         return mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY <= this.y + this.height;
      }

      private RiptideCustomFilterPresetOverlay.PresetRow getRowAt(double mouseY) {
         int idx = (int)((mouseY - this.y - 2.0 + this.scroll.visualOffsetInt()) / RiptideCustomFilterPresetOverlay.this.presetRowStep());
         return idx >= 0 && idx < this.rows.size() ? this.rows.get(idx) : null;
      }
   }

   private static final class PresetRow {
      private final boolean header;
      private final boolean note;
      private final String label;
      private final RiptidePresetManager.PresetEntry entry;

      private PresetRow(boolean header, boolean note, String label, RiptidePresetManager.PresetEntry entry) {
         this.header = header;
         this.note = note;
         this.label = label;
         this.entry = entry;
      }

      private static RiptideCustomFilterPresetOverlay.PresetRow header(String label) {
         return new RiptideCustomFilterPresetOverlay.PresetRow(true, false, label, null);
      }

      private static RiptideCustomFilterPresetOverlay.PresetRow note(String label) {
         return new RiptideCustomFilterPresetOverlay.PresetRow(false, true, label, null);
      }

      private static RiptideCustomFilterPresetOverlay.PresetRow entry(RiptidePresetManager.PresetEntry entry) {
         return new RiptideCustomFilterPresetOverlay.PresetRow(false, false, null, entry);
      }
   }
}
