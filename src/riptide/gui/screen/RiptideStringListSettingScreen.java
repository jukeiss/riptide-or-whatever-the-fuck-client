package riptide.gui.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import riptide.api.module.DisplayMode;
import riptide.api.module.Setting;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.CompactListRenderer;
import riptide.gui.vanillaui.components.CompactListViewport;
import riptide.gui.vanillaui.components.CompactOverlayButton;
import riptide.gui.vanillaui.components.CompactOverlayControls;
import riptide.gui.vanillaui.components.CompactScreenPanel;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactSurfaces;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectLayout;
import riptide.modules.Module;
import riptide.modules.ModuleRegistry;
import riptide.util.RiptideBackgroundTasks;
import riptide.util.RiptideChatField;
import riptide.util.RiptideNotifications;
import riptide.util.RiptidePlayerProbe;
import riptide.util.RiptidePlayerScanner;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;
import riptide.util.StringListCodec;

public class RiptideStringListSettingScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int PANEL_W = 460;
   private static final int PANEL_H = 330;
   private static final int HEADER_H = 24;
   private static final int FIELD_H = 20;
   private static final int ROW_H = 22;
   private static final int BG = -872086265;
   private static final int ROW = -1441722092;
   private static final int ROW_HOVER = -1441000940;
   private static final int TEXT = -791321;
   private static final int MUTED = -4743522;
   private static final int RED = -50373;
   private final Screen parent;
   private final Module module;
   private final Setting<?, ?> option;
   private final List<RiptideStringListSettingScreen.Hit> hits = new ArrayList<>();
   private RiptideChatField entryField;
   private RiptideChatField searchField;
   private int scroll;
   private boolean scrollbarDragging;
   private int scrollbarGrabOffset;
   private int editingIndex = -1;
   private int suggestionIndex = 0;
   private String cachedRawValue;
   private String cachedFilter;
   private List<String> cachedValues = List.of();
   private List<String> cachedFilteredValues = List.of();
   private final List<String> scannedPlayers = new ArrayList<>();
   private final Map<String, String> playerRanks = new HashMap<>();
   private boolean playerScanDone;

   private static int muted() {
      return RiptideTheme.recolor(-4743522, RiptideTheme.Channel.TEXT);
   }

   public RiptideStringListSettingScreen(Screen parent, Module module, Setting<?, ?> option) {
      super(Component.literal(option == null ? "Edit List" : "Edit " + option.label()));
      this.parent = parent;
      this.module = module;
      this.option = option;
   }

   protected void init() {
      this.ensureFields();
      this.syncFieldBounds();
      this.syncFieldBounds();
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int mx = RiptideUiScale.toVirtualInt(mouseX);
      int my = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         this.hits.clear();
         UiRenderer.rect(graphics, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), -872086265);
         int x = this.panelX();
         int y = this.panelY();
         int panelW = this.panelW();
         int panelH = this.panelH();
         this.ensureFields();
         this.syncFieldBounds();
         this.drawTopBar(graphics, x, y, panelW, panelH, this.titleText(), mx, my);
         if (panelW < 180 || panelH < 160) {
            this.drawText(graphics, "Window too small.", x + 8, y + 24 + 10, muted(), Math.max(0, panelW - 16));
            return;
         }

         int fieldX = x + 10;
         int fieldY = y + 24 + 10;
         this.entryField.render(graphics, mx, my, delta);
         this.button(
            graphics,
            this.editingIndex >= 0 ? "Save" : "Add",
            x + panelW - 70,
            fieldY,
            60,
            20,
            CompactOverlayButton.Variant.SUCCESS,
            RiptideStringListSettingScreen.HitType.ADD,
            -1,
            mx,
            my
         );
         this.drawPlayerSuggestions(graphics, fieldX, fieldY, panelW - 88, mx, my);
         int searchY = fieldY + 20 + 7;
         this.searchField.render(graphics, mx, my, delta);
         int actionsY = searchY + 20 + 7;
         this.button(graphics, "Clear", fieldX, actionsY, 58, 18, CompactOverlayButton.Variant.DANGER, RiptideStringListSettingScreen.HitType.CLEAR, -1, mx, my);
         int nextX = fieldX + 64;
         String importLabel = this.importLabel();
         if (importLabel != null) {
            this.button(
               graphics, importLabel, nextX, actionsY, 88, 18, CompactOverlayButton.Variant.PRIMARY, RiptideStringListSettingScreen.HitType.IMPORT, -1, mx, my
            );
            nextX += 94;
         }

         if (this.supportsRankTagScan() || this.supportsPlayerRankPicker()) {
            this.button(
               graphics, "Scan", nextX, actionsY, 58, 18, CompactOverlayButton.Variant.PRIMARY, RiptideStringListSettingScreen.HitType.SCAN, -1, mx, my
            );
            nextX += 64;
         }

         int summaryX = nextX + 6;
         String summary = this.showingCatalog()
            ? this.scannedPlayers.size() + " scanned | " + this.snapshotValues().size() + " picked"
            : this.filteredValues().size() + " shown | " + this.snapshotValues().size() + " total";
         this.drawText(graphics, summary, summaryX, actionsY + 5, muted(), x + panelW - 10 - summaryX);
         int listY = actionsY + 24;
         int listH = y + panelH - listY - 10;
         CompactListViewport.Layout listLayout = this.stringListLayout(listY, listH);
         this.frame(graphics, fieldX, listY, panelW - 20, listH, -1442248437, THEME.borderSoft());
         listLayout.beginRows(graphics);

         try {
            this.drawRows(graphics, listLayout, mx, my);
         } finally {
            listLayout.endRows(graphics);
         }

         listLayout.drawScrollbar(graphics, mx, my, this.scrollbarDragging);
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private void drawRows(GuiGraphicsExtractor graphics, CompactListViewport.Layout layout, int mx, int my) {
      if (this.showingCatalog()) {
         this.drawCatalogRows(graphics, layout, mx, my);
      } else {
         List<String> rows = this.filteredValues();
         this.scroll = this.clamp(this.scroll, 0, layout.maxScroll());
         int x = layout.x() + layout.contentInset();
         int w = layout.contentWidth();
         if (rows.isEmpty()) {
            this.drawText(graphics, "No entries.", x + 4, layout.y() + layout.contentInset() + 6, muted(), w - 8);
         } else {
            List<String> allValues = this.snapshotValues();
            layout.forEachVisibleRow(
               rows.size(),
               (i, rowY) -> {
                  String value = rows.get(i);
                  boolean over = mx >= x && mx < x + w && my >= rowY && my < rowY + 22;
                  CompactSurfaces.tintedRow(graphics, x, rowY, w, 21, over ? -1441000940 : -1441722092);
                  this.drawText(graphics, value, x + 6, rowY + 7, -791321, w - 30);
                  CompactListRenderer.drawDeleteButton(graphics, x + w - 22, rowY + 4, 18, 14, over);
                  int index = this.indexForFilteredRow(rows, allValues, i);
                  this.hits
                     .add(new RiptideStringListSettingScreen.Hit(RiptideStringListSettingScreen.HitType.EDIT_ENTRY, x, rowY, Math.max(1, w - 26), 22, index));
                  this.hits.add(new RiptideStringListSettingScreen.Hit(RiptideStringListSettingScreen.HitType.REMOVE, x + w - 22, rowY + 4, 18, 14, index));
               }
            );
         }
      }
   }

   private void drawCatalogRows(GuiGraphicsExtractor graphics, CompactListViewport.Layout layout, int mx, int my) {
      List<RiptideStringListSettingScreen.CatalogRow> rows = this.catalogRows();
      this.scroll = this.clamp(this.scroll, 0, layout.maxScroll());
      int x = layout.x() + layout.contentInset();
      int w = layout.contentWidth();
      if (rows.isEmpty()) {
         this.drawText(graphics, "No players found.", x + 4, layout.y() + layout.contentInset() + 6, muted(), w - 8);
      } else {
         layout.forEachVisibleRow(rows.size(), (i, rowY) -> {
            RiptideStringListSettingScreen.CatalogRow row = rows.get(i);
            boolean over = mx >= x && mx < x + w && my >= rowY && my < rowY + 22;
            if (row.player() == null) {
               CompactSurfaces.tintedRow(graphics, x, rowY, w, 21, over ? -1441000940 : -1441327333);
               this.drawText(graphics, row.rank(), x + 6, rowY + 7, -791321, w - 70);
               this.drawText(graphics, row.picked() + "/" + row.total(), x + w - 58, rowY + 7, muted(), 52);
               this.hits.add(new RiptideStringListSettingScreen.Hit(RiptideStringListSettingScreen.HitType.TOGGLE_RANK, x, rowY, w, 22, i));
            } else {
               boolean picked = row.picked() > 0;
               CompactSurfaces.tintedRow(graphics, x, rowY, w, 21, over ? -1441000940 : -1441722092);
               this.drawText(graphics, (picked ? "[x] " : "[ ] ") + row.player(), x + 12, rowY + 7, picked ? -791321 : muted(), w - 20);
               this.hits.add(new RiptideStringListSettingScreen.Hit(RiptideStringListSettingScreen.HitType.TOGGLE_PLAYER, x, rowY, w, 22, i));
            }
         });
      }
   }

   private void drawPlayerSuggestions(GuiGraphicsExtractor graphics, int fieldX, int fieldY, int fieldW, int mx, int my) {
      if (this.supportsPlayerSuggestions() && this.entryField != null && this.entryField.isFocused()) {
         List<String> suggestions = this.playerSuggestions();
         if (!suggestions.isEmpty()) {
            int visible = Math.min(5, suggestions.size());
            int rowH = 14;
            int popupH = visible * rowH + 2;
            int popupY = Math.max(4, fieldY - popupH - 2);
            int popupW = Math.min(fieldW, 170);
            int popupX = fieldX;
            this.frame(graphics, fieldX, popupY, popupW, popupH, -301397749, RiptideTheme.recolor(-50373, RiptideTheme.Channel.OUTLINE));
            int start = Math.max(0, Math.min(this.suggestionIndex - visible + 1, suggestions.size() - visible));

            for (int i = 0; i < visible; i++) {
               int idx = start + i;
               String suggestion = suggestions.get(idx);
               int y = popupY + 1 + i * rowH;
               boolean selected = idx == this.suggestionIndex;
               boolean over = mx >= popupX && mx < popupX + popupW && my >= y && my < y + rowH;
               CompactSurfaces.tintedRow(graphics, popupX + 1, y, popupW - 2, rowH - 1, !selected && !over ? -1441722092 : -1441000940);
               this.drawText(graphics, suggestion, popupX + 5, y + 4, -791321, popupW - 10);
               this.hits.add(new RiptideStringListSettingScreen.Hit(RiptideStringListSettingScreen.HitType.SUGGESTION, popupX + 1, y, popupW - 2, rowH, idx));
            }
         }
      }
   }

   private void button(
      GuiGraphicsExtractor graphics,
      String label,
      int x,
      int y,
      int w,
      int h,
      CompactOverlayButton.Variant variant,
      RiptideStringListSettingScreen.HitType type,
      int index,
      int mx,
      int my
   ) {
      CompactOverlayControls.action(graphics, this.font, x, y, w, h, label, variant, true, mx, my);
      this.hits.add(new RiptideStringListSettingScreen.Hit(type, x, y, w, h, index));
   }

   private void drawTopBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, String title, int mx, int my) {
      UiBounds bounds = UiBounds.of(x, y, width, height);
      CompactScreenPanel.render(UiContexts.overlay(graphics, this.font, mx, my), bounds, 24, title, mx >= x && mx < x + width && my >= y && my < y + 24);
      UiBounds close = CompactScreenPanel.closeButton(bounds, 24);
      this.hits
         .add(new RiptideStringListSettingScreen.Hit(RiptideStringListSettingScreen.HitType.CLOSE, close.x(), close.y(), close.width(), close.height(), -1));
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      int mx = RiptideUiScale.toVirtualInt(event.x());
      int my = RiptideUiScale.toVirtualInt(event.y());
      if (event.button() != 0) {
         return true;
      } else {
         CompactScrollbar.Metrics metrics = this.stringScrollbarMetrics();
         if (metrics.hasScroll() && metrics.contains(mx, my)) {
            this.scrollbarDragging = true;
            this.scrollbarGrabOffset = metrics.overThumb(mx, my) ? my - metrics.thumbY() : metrics.thumbHeight() / 2;
            this.scroll = this.snapScrollToRows(CompactScrollbar.scrollFromThumb(metrics, my, this.scrollbarGrabOffset));
            return true;
         } else {
            this.ensureFields();
            if (this.entryField.mouseClicked(mx, my, event.button())) {
               this.searchField.setFocused(false);
               return true;
            } else if (this.searchField.mouseClicked(mx, my, event.button())) {
               this.entryField.setFocused(false);
               return true;
            } else {
               for (int i = this.hits.size() - 1; i >= 0; i--) {
                  RiptideStringListSettingScreen.Hit hit = this.hits.get(i);
                  if (hit.contains(mx, my) && (hit.type != RiptideStringListSettingScreen.HitType.REMOVE || this.insideListBody(mx, my))) {
                     switch (hit.type) {
                        case ADD:
                           this.addEntry();
                           break;
                        case EDIT_ENTRY:
                           this.beginEditEntry(hit.index);
                           break;
                        case REMOVE:
                           this.removeEntry(hit.index);
                           break;
                        case SUGGESTION:
                           this.acceptSuggestion(hit.index);
                           break;
                        case CLEAR:
                           this.setOptionValue("");
                           this.cancelEdit();
                           this.clampScrollToList();
                           break;
                        case SCAN:
                           if (this.supportsPlayerRankPicker()) {
                              this.scanPlayers();
                           } else {
                              this.scanRankTags();
                           }
                           break;
                        case TOGGLE_PLAYER:
                           List<RiptideStringListSettingScreen.CatalogRow> rowsx = this.catalogRows();
                           if (hit.index >= 0 && hit.index < rowsx.size()) {
                              this.togglePlayer(rowsx.get(hit.index).player());
                           }
                           break;
                        case TOGGLE_RANK:
                           List<RiptideStringListSettingScreen.CatalogRow> rows = this.catalogRows();
                           if (hit.index >= 0 && hit.index < rows.size()) {
                              this.toggleRank(rows.get(hit.index).rank());
                           }
                           break;
                        case IMPORT:
                           this.importFromLinkedList();
                           break;
                        case CLOSE:
                           this.onClose();
                     }

                     return true;
                  }
               }

               this.entryField.setFocused(false);
               this.searchField.setFocused(false);
               return true;
            }
         }
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      int my = RiptideUiScale.toVirtualInt(mouseY);
      int mx = RiptideUiScale.toVirtualInt(mouseX);
      if (!this.insideListBody(mx, my)) {
         return true;
      } else {
         this.scroll += scrollY < 0.0 ? 22 : -22;
         this.scroll = this.clamp(this.scroll, 0, this.stringScrollbarMetrics().maxScroll());
         return true;
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      int mx = RiptideUiScale.toVirtualInt(event.x());
      int my = RiptideUiScale.toVirtualInt(event.y());
      this.scrollbarDragging = false;
      if (this.entryField != null && this.entryField.mouseReleased(mx, my, event.button())) {
         return true;
      } else {
         return this.searchField != null && this.searchField.mouseReleased(mx, my, event.button()) ? true : true;
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      int my = RiptideUiScale.toVirtualInt(event.y());
      int mx = RiptideUiScale.toVirtualInt(event.x());
      if (this.entryField != null && this.entryField.mouseDragged(mx, my, event.button(), dx, dy)) {
         return true;
      } else if (this.searchField != null && this.searchField.mouseDragged(mx, my, event.button(), dx, dy)) {
         return true;
      } else if (!this.scrollbarDragging) {
         return true;
      } else {
         this.scroll = this.snapScrollToRows(CompactScrollbar.scrollFromThumb(this.stringScrollbarMetrics(), my, this.scrollbarGrabOffset));
         return true;
      }
   }

   public boolean keyPressed(KeyEvent input) {
      this.ensureFields();
      if (input.key() == 256) {
         if (this.editingIndex >= 0) {
            if (!this.entryText().trim().isEmpty()) {
               this.addEntry();
            } else {
               this.cancelEdit();
            }

            return true;
         } else {
            this.onClose();
            return true;
         }
      } else if (this.entryField.isFocused() && this.supportsPlayerSuggestions() && input.key() == 258) {
         List<String> suggestions = this.playerSuggestions();
         if (!suggestions.isEmpty()) {
            this.suggestionIndex = Math.floorMod(this.suggestionIndex + ((input.modifiers() & 1) != 0 ? -1 : 1), suggestions.size());
            this.setEntryText(this.applySuggestionToEntry(this.entryText(), suggestions.get(this.suggestionIndex)));
         }

         return true;
      } else if (input.key() != 257 && input.key() != 335) {
         if (this.entryField.keyPressed(input)) {
            return true;
         } else {
            return this.searchField.keyPressed(input) ? true : true;
         }
      } else {
         if (this.entryField.isFocused()) {
            this.addEntry();
         }

         return true;
      }
   }

   public boolean charTyped(CharacterEvent input) {
      this.ensureFields();
      if (this.entryField.charTyped(input)) {
         return true;
      } else {
         return this.searchField.charTyped(input) ? true : true;
      }
   }

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   private void addEntry() {
      String value = this.entryText().trim();
      if (!value.isEmpty()) {
         List<String> values = this.values();
         if (this.editingIndex >= 0 && this.editingIndex < values.size()) {
            values.set(this.editingIndex, value);
         } else {
            values.add(value);
         }

         this.setOptionValue(StringListCodec.encode(values));
         this.setEntryText("");
         this.editingIndex = -1;
         this.suggestionIndex = 0;
         this.clampScrollToList();
      }
   }

   private void beginEditEntry(int index) {
      List<String> values = this.snapshotValues();
      if (index >= 0 && index < values.size()) {
         this.editingIndex = index;
         this.setEntryText(values.get(index));
         if (this.entryField != null) {
            this.entryField.setFocused(true);
         }

         if (this.searchField != null) {
            this.searchField.setFocused(false);
         }

         this.suggestionIndex = 0;
      }
   }

   private void cancelEdit() {
      this.editingIndex = -1;
      this.setEntryText("");
      this.suggestionIndex = 0;
   }

   private void removeEntry(int index) {
      List<String> values = this.values();
      if (index >= 0 && index < values.size()) {
         values.remove(index);
         this.setOptionValue(StringListCodec.encode(values));
         if (this.editingIndex == index) {
            this.cancelEdit();
         } else if (this.editingIndex > index) {
            this.editingIndex--;
         }

         this.clampScrollToList();
      }
   }

   private boolean isTitleSetup() {
      return this.parent instanceof RiptideModuleScreen moduleScreen && moduleScreen.isTitleSetup();
   }

   private void setOptionValue(String value) {
      if (this.isTitleSetup()) {
         this.module.setConfiguredValue(this.option.id(), value);
      } else {
         this.module.setValue(this.option.id(), value);
      }
   }

   private List<String> filteredValues() {
      String needle = this.searchText().trim().toLowerCase(Locale.ROOT);
      List<String> values = this.snapshotValues();
      if (needle.equals(this.cachedFilter)) {
         return this.cachedFilteredValues;
      } else {
         this.cachedFilter = needle;
         if (needle.isEmpty()) {
            this.cachedFilteredValues = values;
            return this.cachedFilteredValues;
         } else {
            List<String> out = new ArrayList<>();

            for (String value : values) {
               if (value.toLowerCase(Locale.ROOT).contains(needle)) {
                  out.add(value);
               }
            }

            this.cachedFilteredValues = List.copyOf(out);
            return this.cachedFilteredValues;
         }
      }
   }

   private List<String> values() {
      return new ArrayList<>(this.snapshotValues());
   }

   private List<String> snapshotValues() {
      String rawValue = this.module.value(this.option.id());
      if (!rawValue.equals(this.cachedRawValue)) {
         this.cachedRawValue = rawValue;
         this.cachedValues = List.copyOf(StringListCodec.parse(rawValue));
         this.cachedFilter = null;
         this.cachedFilteredValues = List.of();
      }

      return this.cachedValues;
   }

   private int indexForFilteredRow(List<String> rows, List<String> allValues, int filteredIndex) {
      if (filteredIndex >= 0 && filteredIndex < rows.size()) {
         String value = rows.get(filteredIndex);
         int occurrence = 0;

         for (int i = 0; i <= filteredIndex; i++) {
            if (value.equals(rows.get(i))) {
               occurrence++;
            }
         }

         for (int ix = 0; ix < allValues.size(); ix++) {
            if (value.equals(allValues.get(ix))) {
               if (--occurrence == 0) {
                  return ix;
               }
            }
         }

         return allValues.indexOf(value);
      } else {
         return -1;
      }
   }

   private boolean supportsPlayerSuggestions() {
      return this.option != null && this.option.displayMode() == DisplayMode.PLAYER_NAME_LIST;
   }

   private boolean supportsRankTagScan() {
      return this.option != null && this.option.displayMode() == DisplayMode.RANK_TAG_LIST;
   }

   private boolean supportsPlayerRankPicker() {
      return this.option != null && this.option.displayMode() == DisplayMode.PLAYER_RANK_PICKER;
   }

   private boolean showingCatalog() {
      return this.supportsPlayerRankPicker() && this.playerScanDone;
   }

   private void scanPlayers() {
      if (this.minecraft != null && this.minecraft.getConnection() != null) {
         this.scannedPlayers.clear();
         this.playerRanks.clear();
         this.playerScanDone = true;
         this.scroll = 0;

         for (RiptidePlayerScanner.ScannedPlayer player : RiptidePlayerScanner.scan(this.minecraft)) {
            if (player != null && player.name() != null && !player.name().isBlank() && !containsIgnoreCase(this.scannedPlayers, player.name())) {
               this.scannedPlayers.add(player.name());
               if (player.hasPrefix()) {
                  this.playerRanks.put(player.name().toLowerCase(Locale.ROOT), player.prefix().trim());
               }
            }
         }

         this.scannedPlayers.sort(String::compareToIgnoreCase);
         RiptideNotifications.show("Scanned " + this.scannedPlayers.size() + " players.", -13248397);
         RiptideBackgroundTasks.runTracked("player-rank-scan", () -> {
            List<String> probed = RiptidePlayerProbe.everyone(this.minecraft, true);
            if (!probed.isEmpty()) {
               this.minecraft.execute(() -> {
                  boolean changed = false;

                  for (String name : probed) {
                     if (name != null && !name.isBlank() && !containsIgnoreCase(this.scannedPlayers, name)) {
                        this.scannedPlayers.add(name);
                        changed = true;
                     }
                  }

                  if (changed) {
                     this.scannedPlayers.sort(String::compareToIgnoreCase);
                  }
               });
            }
         });
      } else {
         RiptideNotifications.warning("Join a server first.");
      }
   }

   private String rankOf(String name) {
      String rank = name == null ? "" : this.playerRanks.getOrDefault(name.toLowerCase(Locale.ROOT), "");
      return rank != null && !rank.isBlank() ? rank : "No rank";
   }

   private List<RiptideStringListSettingScreen.CatalogRow> catalogRows() {
      String needle = this.searchText().trim().toLowerCase(Locale.ROOT);
      List<String> selected = this.snapshotValues();
      LinkedHashMap<String, List<String>> groups = new LinkedHashMap<>();
      List<String> manual = new ArrayList<>();

      for (String value : selected) {
         if (value != null && !value.isBlank() && !containsIgnoreCase(this.scannedPlayers, value)) {
            manual.add(value);
         }
      }

      if (!manual.isEmpty()) {
         groups.put("Not online", manual);
      }

      TreeMap<String, List<String>> byRank = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

      for (String name : this.scannedPlayers) {
         byRank.computeIfAbsent(this.rankOf(name), ignored -> new ArrayList<>()).add(name);
      }

      List<String> rankless = byRank.remove("No rank");
      groups.putAll(byRank);
      if (rankless != null) {
         groups.put("No rank", rankless);
      }

      List<RiptideStringListSettingScreen.CatalogRow> rows = new ArrayList<>();
      groups.forEach((rank, members) -> {
         List<String> shown = new ArrayList<>();

         for (String name : members) {
            if (needle.isEmpty() || name.toLowerCase(Locale.ROOT).contains(needle) || rank.toLowerCase(Locale.ROOT).contains(needle)) {
               shown.add(name);
            }
         }

         if (!shown.isEmpty()) {
            int picked = 0;

            for (String namex : shown) {
               if (containsIgnoreCase(selected, namex)) {
                  picked++;
               }
            }

            rows.add(new RiptideStringListSettingScreen.CatalogRow(rank, null, picked, shown.size()));

            for (String namexx : shown) {
               rows.add(new RiptideStringListSettingScreen.CatalogRow(rank, namexx, containsIgnoreCase(selected, namexx) ? 1 : 0, 1));
            }
         }
      });
      return rows;
   }

   private void togglePlayer(String name) {
      if (name != null && !name.isBlank()) {
         List<String> values = this.values();
         if (!removeIgnoreCase(values, name)) {
            values.add(name);
         }

         this.setOptionValue(StringListCodec.encode(values));
      }
   }

   private void toggleRank(String rank) {
      if (rank != null) {
         List<String> members = new ArrayList<>();

         for (RiptideStringListSettingScreen.CatalogRow row : this.catalogRows()) {
            if (row.player() != null && row.rank().equals(rank)) {
               members.add(row.player());
            }
         }

         if (!members.isEmpty()) {
            List<String> values = this.values();
            boolean allPicked = true;

            for (String name : members) {
               if (!containsIgnoreCase(values, name)) {
                  allPicked = false;
                  break;
               }
            }

            for (String namex : members) {
               if (allPicked) {
                  removeIgnoreCase(values, namex);
               } else if (!containsIgnoreCase(values, namex)) {
                  values.add(namex);
               }
            }

            this.setOptionValue(StringListCodec.encode(values));
         }
      }
   }

   private static boolean removeIgnoreCase(List<String> values, String target) {
      for (int i = 0; i < values.size(); i++) {
         if (values.get(i).equalsIgnoreCase(target)) {
            values.remove(i);
            return true;
         }
      }

      return false;
   }

   private void scanRankTags() {
      if (this.supportsRankTagScan() && this.minecraft != null && this.minecraft.getConnection() != null) {
         List<String> values = this.values();
         int added = 0;

         for (RiptidePlayerScanner.ScannedPlayer player : RiptidePlayerScanner.scan(this.minecraft)) {
            if (player != null && player.hasPrefix()) {
               String tag = player.prefix().trim();
               if (!tag.isEmpty() && !containsIgnoreCase(values, tag)) {
                  values.add(tag);
                  added++;
               }
            }
         }

         if (added > 0) {
            this.setOptionValue(StringListCodec.encode(values));
            this.clampScrollToList();
            RiptideNotifications.show("Rank scan added " + added + " tag" + (added == 1 ? "." : "s."), -13248397);
         } else {
            RiptideNotifications.warning("Rank scan found nothing.");
         }
      } else {
         RiptideNotifications.warning("Join a server first.");
      }
   }

   private static boolean containsIgnoreCase(List<String> values, String target) {
      for (String value : values) {
         if (value.equalsIgnoreCase(target)) {
            return true;
         }
      }

      return false;
   }

   private String importLabel() {
      if (this.module == null || this.option == null) {
         return null;
      } else if ("teams".equals(this.module.id()) && "friends".equals(this.option.id())) {
         return "Import Names";
      } else {
         return "name-censor".equals(this.module.id()) && "names".equals(this.option.id()) ? "Import Teams" : null;
      }
   }

   private void importFromLinkedList() {
      String sourceModuleId = null;
      String sourceOptionId = null;
      if (this.module != null && this.option != null) {
         if ("teams".equals(this.module.id()) && "friends".equals(this.option.id())) {
            sourceModuleId = "name-censor";
            sourceOptionId = "names";
         } else if ("name-censor".equals(this.module.id()) && "names".equals(this.option.id())) {
            sourceModuleId = "teams";
            sourceOptionId = "friends";
         }
      }

      Module source = sourceModuleId == null ? null : ModuleRegistry.get(sourceModuleId);
      if (source == null) {
         RiptideNotifications.warning("Nothing to import.");
      } else {
         List<String> values = this.values();
         int added = 0;

         for (String entry : StringListCodec.parse(source.value(sourceOptionId))) {
            if (entry != null && !entry.isBlank() && !containsIgnoreCase(values, entry)) {
               values.add(entry.trim());
               added++;
            }
         }

         if (added > 0) {
            this.setOptionValue(StringListCodec.encode(values));
            this.clampScrollToList();
            RiptideNotifications.show("Imported " + added + " name" + (added == 1 ? "." : "s."), -13248397);
         } else {
            RiptideNotifications.warning("Nothing new to import.");
         }
      }
   }

   private List<String> playerSuggestions() {
      if (this.supportsPlayerSuggestions() && this.minecraft != null && this.minecraft.getConnection() != null) {
         String query = this.playerSuggestionQuery(this.entryText()).toLowerCase(Locale.ROOT);
         List<String> names = new ArrayList<>();

         for (PlayerInfo info : this.minecraft.getConnection().getOnlinePlayers()) {
            if (info != null && info.getProfile() != null && info.getProfile().name() != null) {
               String name = info.getProfile().name();
               if (this.isPlayerSuggestionName(name) && (query.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(query)) && !names.contains(name)) {
                  names.add(name);
               }
            }
         }

         if (this.minecraft.level != null) {
            for (AbstractClientPlayer player : this.minecraft.level.players()) {
               if (player != null && player.getGameProfile() != null && player.getGameProfile().name() != null) {
                  String name = player.getGameProfile().name();
                  if (this.isPlayerSuggestionName(name) && (query.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(query)) && !names.contains(name)) {
                     names.add(name);
                  }
               }
            }
         }

         names.sort(String.CASE_INSENSITIVE_ORDER);
         if (this.suggestionIndex >= names.size()) {
            this.suggestionIndex = Math.max(0, names.size() - 1);
         }

         return names;
      } else {
         return List.of();
      }
   }

   private boolean isPlayerSuggestionName(String name) {
      String trimmed = name == null ? "" : name.trim();
      if (trimmed.length() < 4) {
         return false;
      } else {
         String lower = trimmed.toLowerCase(Locale.ROOT);
         if (lower.startsWith("slot_") && lower.length() > 5) {
            for (int i = 5; i < lower.length(); i++) {
               if (!Character.isDigit(lower.charAt(i))) {
                  return true;
               }
            }

            return false;
         } else {
            return true;
         }
      }
   }

   private String playerSuggestionQuery(String entry) {
      String value = entry == null ? "" : entry;
      int eq = value.indexOf(61);
      if (eq >= 0) {
         value = value.substring(0, eq);
      }

      return value.trim();
   }

   private String applySuggestionToEntry(String entry, String suggestion) {
      String value = entry == null ? "" : entry;
      int eq = value.indexOf(61);
      return eq >= 0 ? suggestion + value.substring(eq) : suggestion;
   }

   private void acceptSuggestion(int index) {
      List<String> suggestions = this.playerSuggestions();
      if (index >= 0 && index < suggestions.size()) {
         this.suggestionIndex = index;
         this.setEntryText(this.applySuggestionToEntry(this.entryText(), suggestions.get(index)));
         if (this.entryField != null) {
            this.entryField.setFocused(true);
         }

         if (this.searchField != null) {
            this.searchField.setFocused(false);
         }
      }
   }

   private void ensureFields() {
      if (this.entryField == null) {
         this.entryField = new RiptideChatField(this.minecraft, this.font, 0, 0, 1, 20, false);
         this.entryField.setMaxLength(512);
         this.entryField.setChangedListener(value -> this.suggestionIndex = 0);
      }

      if (this.searchField == null) {
         this.searchField = new RiptideChatField(this.minecraft, this.font, 0, 0, 1, 20, false);
         this.searchField.setMaxLength(128);
         this.searchField.setPlaceholder(Component.literal("Search"));
         this.searchField.setChangedListener(value -> {
            this.cachedFilter = null;
            this.clampScrollToList();
         });
      }
   }

   private void syncFieldBounds() {
      if (this.entryField != null && this.searchField != null) {
         int x = this.panelX();
         int y = this.panelY();
         int panelW = this.panelW();
         int fieldX = x + 10;
         int fieldY = y + 24 + 10;
         this.entryField.setX(fieldX);
         this.entryField.setY(fieldY);
         this.entryField.setWidth(panelW - 88);
         this.entryField.setHeight(20);
         this.entryField.setPlaceholder(Component.literal(this.editingIndex >= 0 ? "Edit entry" : "New entry"));
         int searchY = fieldY + 20 + 7;
         this.searchField.setX(fieldX);
         this.searchField.setY(searchY);
         this.searchField.setWidth(panelW - 20);
         this.searchField.setHeight(20);
      }
   }

   private String entryText() {
      return this.entryField == null ? "" : this.entryField.getText();
   }

   private void setEntryText(String value) {
      this.ensureFields();
      this.entryField.setText(value == null ? "" : value);
      this.entryField.setSelectionEnd(this.entryField.getText().length());
   }

   private String searchText() {
      return this.searchField == null ? "" : this.searchField.getText();
   }

   private String titleText() {
      return this.option == null ? "Edit List" : "Edit " + this.option.label();
   }

   private int panelX() {
      return DirectLayout.centerPanel(this.screenWidth(), this.panelW(), 4);
   }

   private int panelY() {
      return DirectLayout.centerPanel(this.screenHeight(), this.panelH(), 4);
   }

   private int panelW() {
      return DirectLayout.fitPanelDimension(this.screenWidth(), 4, 460);
   }

   private int panelH() {
      return DirectLayout.fitPanelDimension(this.screenHeight(), 4, 330);
   }

   private int listRowCount() {
      return this.showingCatalog() ? this.catalogRows().size() : this.filteredValues().size();
   }

   private CompactScrollbar.Metrics stringScrollbarMetrics() {
      int y = this.panelY();
      int fieldY = y + 24 + 10;
      int searchY = fieldY + 20 + 7;
      int actionsY = searchY + 20 + 7;
      int listY = actionsY + 24;
      int listH = y + this.panelH() - listY - 10;
      return this.stringScrollbarMetrics(listY, listH);
   }

   private CompactScrollbar.Metrics stringScrollbarMetrics(int listY, int listH) {
      return this.stringListLayout(listY, listH).scrollbar();
   }

   private CompactListViewport.Layout stringListLayout(int listY, int listH) {
      return CompactListViewport.layout(this.panelX() + 10, listY, this.panelW() - 20, listH, this.listRowCount(), 22, 22, this.scroll, 4, 0, 3, 6, false);
   }

   private boolean insideListBody(int mx, int my) {
      int y = this.panelY();
      int fieldY = y + 24 + 10;
      int searchY = fieldY + 20 + 7;
      int actionsY = searchY + 20 + 7;
      int listY = actionsY + 24;
      int listH = y + this.panelH() - listY - 10;
      CompactListViewport.Layout listLayout = this.stringListLayout(listY, listH);
      return mx >= listLayout.x() + listLayout.contentInset()
         && mx < listLayout.x() + listLayout.contentInset() + listLayout.contentWidth()
         && my >= listLayout.y() + listLayout.contentInset()
         && my < listLayout.y() + listLayout.contentInset() + listLayout.viewHeight();
   }

   private void clampScrollToList() {
      this.scroll = this.clamp(this.scroll, 0, this.stringScrollbarMetrics().maxScroll());
   }

   private int snapScrollToRows(int offset) {
      return Math.max(0, offset / 22) * 22;
   }

   private int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill, int border) {
      UiRenderer.frame(graphics, UiBounds.of(x, y, w, h), fill, border);
   }

   private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, int maxWidth) {
      String display = UiText.trimToWidth(this.font, text == null ? "" : text, Math.max(0, maxWidth), THEME.fontFor(UiTone.BODY), color);
      UiText.draw(graphics, this.font, display, THEME.fontFor(UiTone.BODY), color, x, y, false);
   }

   private void drawCentered(GuiGraphicsExtractor graphics, String text, int x, int y, int w, int h, int color) {
      String display = UiText.trimToWidth(this.font, text == null ? "" : text, Math.max(0, w - 4), THEME.fontFor(UiTone.BODY), color);
      int tw = UiText.width(this.font, display, THEME.fontFor(UiTone.BODY), color);
      int th = THEME.fontHeight(UiTone.BODY);
      int drawX = x + Math.max(2, (w - tw) / 2);
      int drawY = y + Math.max(1, (h - th + 1) / 2 + 1);
      UiText.draw(graphics, this.font, display, THEME.fontFor(UiTone.BODY), color, drawX, drawY, false);
   }

   private record CatalogRow(String rank, String player, int picked, int total) {
   }

   private record Hit(RiptideStringListSettingScreen.HitType type, int x, int y, int w, int h, int index) {
      boolean contains(int mx, int my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   private static enum HitType {
      ADD,
      EDIT_ENTRY,
      REMOVE,
      SUGGESTION,
      CLEAR,
      SCAN,
      TOGGLE_PLAYER,
      TOGGLE_RANK,
      IMPORT,
      CLOSE;
   }
}
