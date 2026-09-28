package riptide.gui.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import riptide.api.module.Kind;
import riptide.api.module.RegistryListSetting;
import riptide.api.module.Setting;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.components.CompactListRenderer;
import riptide.gui.vanillaui.components.CompactScreenPanel;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactSurfaces;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectLayout;
import riptide.modules.Module;
import riptide.modules.ModuleStorageEsp;
import riptide.modules.ScaffoldModule;
import riptide.util.RegistryListCodec;
import riptide.util.RiptideChatField;
import riptide.util.RiptideDisplayItemUtils;
import riptide.util.RiptideFarmBlocks;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;
import riptide.util.StringListCodec;
import riptide.util.oresim.RiptideOreSimOre;

public class RiptideRegistryListSettingScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int PANEL_W = 620;
   private static final int PANEL_H = 360;
   private static final int HEADER_H = 24;
   private static final int SEARCH_H = 20;
   private static final int ROW_H = 34;
   private static final int ICON_SIZE = 24;
   private static final int GROUP_H = 16;
   private static final Identifier EMPTY_SPAWN_EGG = Identifier.fromNamespaceAndPath("riptide", "textures/gui/riptide/empty_spawn_egg.png");
   private static final int BG = -872086265;
   private static final int ROW = -1441722092;
   private static final int ROW_HOVER = -1441000940;
   private static final int TEXT = -791321;
   private static final int MUTED = -4743522;
   private static final int RED = -50373;
   private final Screen parent;
   private final Module module;
   private final Setting<?, ?> option;
   private final List<RiptideRegistryListSettingScreen.Entry> entries;
   private final Map<String, RiptideRegistryListSettingScreen.Entry> entriesById = new HashMap<>();
   private final List<RiptideRegistryListSettingScreen.Hit> hits = new ArrayList<>();
   private RiptideChatField searchField;
   private int availableScroll;
   private int selectedScroll;
   private boolean availableScrollbarDragging;
   private boolean selectedScrollbarDragging;
   private int scrollbarGrabOffset;
   private String cachedRawValue;
   private String cachedSearch;
   private Set<String> cachedSelectedIds = Set.of();
   private List<RiptideRegistryListSettingScreen.SelectedRow> cachedSelectedRows = List.of();
   private List<RiptideRegistryListSettingScreen.Entry> cachedAvailableRows = List.of();

   private static int muted() {
      return RiptideTheme.recolor(-4743522, RiptideTheme.Channel.TEXT);
   }

   public RiptideRegistryListSettingScreen(Screen parent, Module module, Setting<?, ?> option) {
      super(Component.literal(option == null ? "Select" : "Select " + option.label()));
      this.parent = parent;
      this.module = module;
      this.option = option;
      this.entries = this.buildEntries(option == null ? Kind.ITEM_LIST : option.kind());

      for (RiptideRegistryListSettingScreen.Entry entry : this.entries) {
         this.entriesById.put(entry.id, entry);
      }
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
         this.drawTopBar(graphics, x, y, panelW, panelH, this.titleText(), mx, my);
         if (panelW >= 260 && panelH >= 150) {
            int searchX = x + 10;
            int searchY = y + 24 + 9;
            this.ensureSearchField(searchX, searchY, panelW - 20, 20);
            this.searchField.render(graphics, mx, my, delta);
            int paneY = searchY + 20 + 10;
            int paneH = y + panelH - paneY - 12;
            int paneW = (panelW - 30) / 2;
            int leftX = x + 10;
            int rightX = leftX + paneW + 10;
            this.drawPane(graphics, leftX, paneY, paneW, paneH, "Available", true, mx, my);
            this.drawPane(graphics, rightX, paneY, paneW, paneH, "Selected", false, mx, my);
            return;
         }

         this.drawText(graphics, "Window too small.", x + 8, y + 24 + 10, muted(), Math.max(0, panelW - 16));
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private void drawPane(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String title, boolean available, int mx, int my) {
      this.frame(graphics, x, y, w, h, -1442248437, THEME.borderSoft());
      this.drawText(graphics, title, x + 6, y + 6, -791321, w - 12);
      int listTop = y + 24;
      int listH = h - 28;
      UiScissorStack.global().push(graphics, UiBounds.of(x + 1, listTop, Math.max(0, w - 2), Math.max(0, listH)));

      try {
         if (available) {
            this.drawAvailableRows(graphics, x + 3, listTop, w - 12, listH, mx, my);
         } else {
            this.drawSelectedRows(graphics, x + 3, listTop, w - 12, listH, mx, my);
         }
      } finally {
         UiScissorStack.global().pop(graphics);
      }

      CompactScrollbar.Metrics metrics = this.registryScrollbarMetrics(available, x, listTop, w, listH);
      CompactScrollbar.draw(graphics, metrics, metrics.contains(mx, my), available ? this.availableScrollbarDragging : this.selectedScrollbarDragging);
   }

   private void drawAvailableRows(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mx, int my) {
      List<RiptideRegistryListSettingScreen.Entry> rows = this.filteredAvailable();
      int maxScroll = Math.max(0, this.availableContentHeight(rows) - h + 8);
      this.availableScroll = this.clamp(this.availableScroll, 0, maxScroll);
      int rowY = y - this.availableScroll;
      String lastGroup = "";

      for (RiptideRegistryListSettingScreen.Entry entry : rows) {
         if (showsGroupHeaders(this.option) && !entry.group.equals(lastGroup)) {
            this.drawGroup(graphics, entry.group, x, rowY, w);
            rowY += 16;
            lastGroup = entry.group;
         }

         if (rowY + 34 > y && rowY < y + h) {
            this.drawEntryRow(graphics, entry, x, rowY, w, RiptideRegistryListSettingScreen.HitType.ADD, entry.id, mx, my, false);
         }

         rowY += 34;
      }
   }

   private void drawSelectedRows(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mx, int my) {
      List<RiptideRegistryListSettingScreen.SelectedRow> rows = this.selectedRows();
      int maxScroll = Math.max(0, rows.size() * 34 - h + 8);
      this.selectedScroll = this.clamp(this.selectedScroll, 0, maxScroll);
      int rowY = y - this.selectedScroll;
      if (rows.isEmpty()) {
         this.drawText(graphics, "Nothing selected.", x + 4, y + 6, muted(), w - 8);
      } else {
         int first = this.clamp(this.selectedScroll / 34, 0, Math.max(0, rows.size() - 1));
         int last = this.clamp((this.selectedScroll + h - 1) / 34, 0, rows.size() - 1);

         for (int i = first; i <= last; i++) {
            RiptideRegistryListSettingScreen.SelectedRow row = rows.get(i);
            rowY = y - this.selectedScroll + i * 34;
            if (rowY + 34 > y && rowY < y + h) {
               this.drawEntryRow(graphics, row.entry(), x, rowY, w, RiptideRegistryListSettingScreen.HitType.REMOVE, row.id, mx, my, row.invalid);
            }
         }
      }
   }

   private void drawGroup(GuiGraphicsExtractor graphics, String label, int x, int y, int w) {
      CompactSurfaces.header(graphics, x, y + 2, w, 13);
      this.drawText(graphics, label, x + 5, y + 4, muted(), w - 10);
   }

   private void drawEntryRow(
      GuiGraphicsExtractor graphics,
      RiptideRegistryListSettingScreen.Entry entry,
      int x,
      int y,
      int w,
      RiptideRegistryListSettingScreen.HitType type,
      String value,
      int mx,
      int my,
      boolean invalid
   ) {
      String label = entry == null ? value : entry.label;
      String id = entry == null ? value : entry.id;
      boolean over = mx >= x && mx < x + w && my >= y && my < y + 34;
      CompactSurfaces.tintedRow(graphics, x, y, w, 33, over ? -1441000940 : -1441722092);
      int textX = x + 8;
      if (entry != null && !invalid) {
         this.drawEntryIcon(graphics, entry, x + 6, y + Math.max(2, 5));
         textX = x + 6 + 24 + 7;
      }

      int actionW = 14;
      int textMax = x + w - actionW - 6 - textX;
      this.drawText(graphics, label, textX, y + 5, invalid ? RiptideTheme.recolor(-50373, RiptideTheme.Channel.DANGER) : -791321, textMax);
      this.drawText(graphics, id, textX, y + 19, muted(), textMax);
      if (type == RiptideRegistryListSettingScreen.HitType.ADD) {
         CompactListRenderer.drawStructuralButton(graphics, x + w - actionW - 3, y + 10, actionW, 14, "+", over, false);
      } else {
         CompactListRenderer.drawDeleteButton(graphics, x + w - actionW - 3, y + 10, actionW, 14, over);
      }

      this.hits.add(new RiptideRegistryListSettingScreen.Hit(type, x, y, w, 34, value));
   }

   private void drawEntryIcon(GuiGraphicsExtractor graphics, RiptideRegistryListSettingScreen.Entry entry, int x, int y) {
      if (entry.iconFallback()) {
         graphics.blit(EMPTY_SPAWN_EGG, x, y, x + 24, y + 24, 0.0F, 1.0F, 0.0F, 1.0F);
      } else {
         ItemStack stack = entry.icon();
         if (stack != null && !stack.isEmpty()) {
            graphics.pose().pushMatrix();
            graphics.pose().scale(1.5F, 1.5F);
            graphics.item(stack, Math.round(x / 1.5F), Math.round(y / 1.5F));
            graphics.pose().popMatrix();
         }
      }
   }

   private void drawTopBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, String title, int mx, int my) {
      UiBounds bounds = UiBounds.of(x, y, width, height);
      CompactScreenPanel.render(UiContexts.overlay(graphics, this.font, mx, my), bounds, 24, title, mx >= x && mx < x + width && my >= y && my < y + 24);
      UiBounds close = CompactScreenPanel.closeButton(bounds, 24);
      this.hits
         .add(new RiptideRegistryListSettingScreen.Hit(RiptideRegistryListSettingScreen.HitType.CLOSE, close.x(), close.y(), close.width(), close.height(), ""));
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      int mx = RiptideUiScale.toVirtualInt(event.x());
      int my = RiptideUiScale.toVirtualInt(event.y());
      if (event.button() != 0) {
         return true;
      } else if (this.searchField != null && this.searchField.mouseClicked(mx, my, event.button())) {
         return true;
      } else {
         CompactScrollbar.Metrics availableMetrics = this.registryScrollbarMetrics(true);
         if (availableMetrics.hasScroll() && availableMetrics.contains(mx, my)) {
            this.availableScrollbarDragging = true;
            this.scrollbarGrabOffset = availableMetrics.overThumb(mx, my) ? my - availableMetrics.thumbY() : availableMetrics.thumbHeight() / 2;
            this.availableScroll = CompactScrollbar.scrollFromThumb(availableMetrics, my, this.scrollbarGrabOffset);
            return true;
         } else {
            CompactScrollbar.Metrics selectedMetrics = this.registryScrollbarMetrics(false);
            if (selectedMetrics.hasScroll() && selectedMetrics.contains(mx, my)) {
               this.selectedScrollbarDragging = true;
               this.scrollbarGrabOffset = selectedMetrics.overThumb(mx, my) ? my - selectedMetrics.thumbY() : selectedMetrics.thumbHeight() / 2;
               this.selectedScroll = this.snapSelectedScroll(CompactScrollbar.scrollFromThumb(selectedMetrics, my, this.scrollbarGrabOffset));
               return true;
            } else {
               for (int i = this.hits.size() - 1; i >= 0; i--) {
                  RiptideRegistryListSettingScreen.Hit hit = this.hits.get(i);
                  if (hit.contains(mx, my)
                     && (hit.type != RiptideRegistryListSettingScreen.HitType.ADD || this.insideAvailablePane(mx, my))
                     && (hit.type != RiptideRegistryListSettingScreen.HitType.REMOVE || this.insideSelectedPane(mx, my))) {
                     switch (hit.type) {
                        case ADD:
                           this.add(hit.value);
                           break;
                        case REMOVE:
                           this.remove(hit.value);
                           break;
                        case CLOSE:
                           this.onClose();
                     }

                     return true;
                  }
               }

               return true;
            }
         }
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      this.availableScrollbarDragging = false;
      this.selectedScrollbarDragging = false;
      if (this.searchField != null) {
         this.searchField.mouseReleased(RiptideUiScale.toVirtualInt(event.x()), RiptideUiScale.toVirtualInt(event.y()), event.button());
      }

      return true;
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      int mx = RiptideUiScale.toVirtualInt(event.x());
      int my = RiptideUiScale.toVirtualInt(event.y());
      if (this.availableScrollbarDragging) {
         this.availableScroll = CompactScrollbar.scrollFromThumb(this.registryScrollbarMetrics(true), my, this.scrollbarGrabOffset);
         return true;
      } else if (this.selectedScrollbarDragging) {
         this.selectedScroll = this.snapSelectedScroll(CompactScrollbar.scrollFromThumb(this.registryScrollbarMetrics(false), my, this.scrollbarGrabOffset));
         return true;
      } else {
         return this.searchField != null && this.searchField.mouseDragged(mx, my, event.button(), dx, dy) ? true : true;
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      int mx = RiptideUiScale.toVirtualInt(mouseX);
      int my = RiptideUiScale.toVirtualInt(mouseY);
      int x = this.panelX() + 10;
      int y = this.panelY() + 24 + 9 + 20 + 10;
      int paneW = (this.panelW() - 30) / 2;
      int paneH = this.panelY() + this.panelH() - y - 12;
      int amount = scrollY < 0.0 ? 34 : -34;
      if (this.insideAvailablePane(mx, my)) {
         this.availableScroll += amount;
      } else {
         if (!this.insideSelectedPane(mx, my)) {
            return true;
         }

         this.selectedScroll += amount;
      }

      this.availableScroll = this.clamp(this.availableScroll, 0, this.registryScrollbarMetrics(true).maxScroll());
      this.selectedScroll = this.clamp(this.selectedScroll, 0, this.registryScrollbarMetrics(false).maxScroll());
      return true;
   }

   public boolean keyPressed(KeyEvent input) {
      if (input.key() == 256) {
         this.onClose();
         return true;
      } else {
         return this.searchField != null && this.searchField.keyPressed(input) ? true : true;
      }
   }

   public boolean charTyped(CharacterEvent input) {
      if (this.searchField != null) {
         this.searchField.charTyped(input);
      }

      return true;
   }

   private void ensureSearchField(int x, int y, int w, int h) {
      if (this.searchField == null) {
         this.searchField = new RiptideChatField(Minecraft.getInstance(), this.font, x, y, w, h, false);
         this.searchField.setPlaceholder(Component.literal("Search"));
         this.searchField.setChangedListener(value -> this.clampPaneScrolls());
         this.searchField.setFocused(true);
      }

      this.searchField.setX(x);
      this.searchField.setY(y);
      this.searchField.setWidth(w);
      this.searchField.setHeight(h);
   }

   private String searchText() {
      return this.searchField == null ? "" : this.searchField.getText();
   }

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   private void add(String id) {
      List<String> tokens = StringListCodec.parse(this.module.value(this.option.id()));
      LinkedHashSet<String> valid = this.validIds(tokens);
      List<String> invalid = this.invalidTokens(tokens);
      valid.add(id);
      this.write(valid, invalid);
      this.clampPaneScrolls();
   }

   private void remove(String id) {
      List<String> tokens = StringListCodec.parse(this.module.value(this.option.id()));
      LinkedHashSet<String> valid = this.validIds(tokens);
      List<String> invalid = this.invalidTokens(tokens);
      String normalized = RegistryListCodec.normalizeId(id);
      valid.remove(normalized);
      invalid.removeIf(token -> token.equals(id) || RegistryListCodec.normalizeId(token).equals(normalized));
      this.write(valid, invalid);
      this.clampPaneScrolls();
   }

   private void write(Set<String> valid, List<String> invalid) {
      List<String> out = new ArrayList<>(valid);
      out.addAll(invalid);
      String encoded = StringListCodec.encode(out);
      if (this.parent instanceof RiptideModuleScreen moduleScreen && moduleScreen.isTitleSetup()) {
         this.module.setConfiguredValue(this.option.id(), encoded);
      } else {
         this.module.setValue(this.option.id(), encoded);
      }
   }

   private Set<String> selectedValidIds() {
      this.ensureListCache();
      return this.cachedSelectedIds;
   }

   private LinkedHashSet<String> validIds(List<String> tokens) {
      LinkedHashSet<String> out = new LinkedHashSet<>();

      for (String token : tokens) {
         String normalized = RegistryListCodec.normalizeId(token);
         if (RegistryListCodec.exists(this.option.kind(), normalized) && (!this.filtered() || this.entriesById.containsKey(normalized))) {
            out.add(normalized);
         }
      }

      return out;
   }

   private List<String> invalidTokens(List<String> tokens) {
      if (!this.filtered()) {
         return RegistryListCodec.invalidTokens(this.option.kind(), tokens);
      } else {
         List<String> invalid = new ArrayList<>();

         for (String token : tokens) {
            String normalized = RegistryListCodec.normalizeId(token);
            if (!RegistryListCodec.exists(this.option.kind(), normalized) || !this.entriesById.containsKey(normalized)) {
               invalid.add(token);
            }
         }

         return invalid;
      }
   }

   private RegistryListSetting.BlockFilter blockFilter() {
      return this.option instanceof RegistryListSetting registry ? registry.blockFilter() : RegistryListSetting.BlockFilter.NONE;
   }

   private boolean filtered() {
      return this.blockFilter() != RegistryListSetting.BlockFilter.NONE;
   }

   private List<RiptideRegistryListSettingScreen.Entry> filteredAvailable() {
      this.ensureListCache();
      return this.cachedAvailableRows;
   }

   private List<RiptideRegistryListSettingScreen.SelectedRow> selectedRows() {
      this.ensureListCache();
      return this.cachedSelectedRows;
   }

   private void ensureListCache() {
      String rawValue = this.module.value(this.option.id());
      String needle = this.searchText().trim().toLowerCase(Locale.ROOT);
      if (!rawValue.equals(this.cachedRawValue) || !needle.equals(this.cachedSearch)) {
         List<String> tokens = StringListCodec.parse(this.module.value(this.option.id()));
         LinkedHashSet<String> selected = this.validIds(tokens);
         List<RiptideRegistryListSettingScreen.SelectedRow> selectedRows = new ArrayList<>();

         for (String id : selected) {
            RiptideRegistryListSettingScreen.Entry entry = this.entry(id);
            selectedRows.add(new RiptideRegistryListSettingScreen.SelectedRow(id, entry == null ? this.fallbackEntry(id) : entry, false));
         }

         for (String invalid : this.invalidTokens(tokens)) {
            selectedRows.add(new RiptideRegistryListSettingScreen.SelectedRow(invalid, this.fallbackEntry("Invalid: " + invalid, invalid), true));
         }

         List<RiptideRegistryListSettingScreen.Entry> available = new ArrayList<>();

         for (RiptideRegistryListSettingScreen.Entry entry : this.entries) {
            if (!selected.contains(entry.id) && (needle.isEmpty() || entry.labelLower().contains(needle) || entry.id.contains(needle))) {
               available.add(entry);
            }
         }

         if (!needle.isEmpty()) {
            available.sort(Comparator.comparingInt(entryx -> matchRank(entryx, needle)));
         }

         this.cachedRawValue = rawValue;
         this.cachedSearch = needle;
         this.cachedSelectedIds = Set.copyOf(selected);
         this.cachedSelectedRows = List.copyOf(selectedRows);
         this.cachedAvailableRows = List.copyOf(available);
      }
   }

   private RiptideRegistryListSettingScreen.Entry entry(String id) {
      return this.entriesById.get(id);
   }

   private static int matchRank(RiptideRegistryListSettingScreen.Entry entry, String needle) {
      String path = entry.id();
      int colon = path.indexOf(58);
      if (colon >= 0 && colon + 1 < path.length()) {
         path = path.substring(colon + 1);
      }

      if (path.equals(needle)) {
         return 0;
      } else if (path.startsWith(needle)) {
         return 1;
      } else if (path.contains(needle)) {
         return 2;
      } else {
         String label = entry.labelLower();
         if (label.equals(needle)) {
            return 3;
         } else {
            return label.startsWith(needle) ? 4 : 5;
         }
      }
   }

   private List<RiptideRegistryListSettingScreen.Entry> buildEntries(Kind type) {
      List<RiptideRegistryListSettingScreen.Entry> out = new ArrayList<>();
      switch (type) {
         case ITEM_LIST:
            BuiltInRegistries.ITEM
               .forEach(
                  item -> {
                     if (item != Items.AIR) {
                        out.add(
                           new RiptideRegistryListSettingScreen.Entry(
                              this.id(BuiltInRegistries.ITEM.getKey(item)),
                              item.getName(item.getDefaultInstance()).getString(),
                              "",
                              RiptideDisplayItemUtils.toStack(item),
                              false
                           )
                        );
                     }
                  }
               );
            break;
         case BLOCK_LIST:
            BuiltInRegistries.BLOCK
               .forEach(
                  block -> {
                     Identifier id = BuiltInRegistries.BLOCK.getKey(block);
                     if (block != Blocks.AIR && block != Blocks.BARRIER && id != null && !id.getPath().endsWith("_wall_banner")) {
                        switch (this.blockFilter()) {
                           case NONE:
                              break;
                           case PLACEABLE:
                              if (!ScaffoldModule.isPlaceableBlockChoice(block)) {
                                 return;
                              }
                              break;
                           case ORE_SIM:
                              if (!RiptideOreSimOre.isOreSimBlock(block)) {
                                 return;
                              }
                              break;
                           case CROPS:
                              if (!RiptideFarmBlocks.isFarmable(block)) {
                                 return;
                              }
                              break;
                           default:
                              throw new MatchException(null, null);
                        }

                        out.add(
                           new RiptideRegistryListSettingScreen.Entry(
                              this.id(id), block.getName().getString(), "", RiptideDisplayItemUtils.toStack(block), false
                           )
                        );
                     }
                  }
               );
            break;
         case ENTITY_TYPE_LIST:
            Map<EntityType<?>, ItemStack> spawnEggs = this.spawnEggIcons();
            BuiltInRegistries.ENTITY_TYPE
               .forEach(
                  typeEntry -> {
                     if (typeEntry != EntityTypes.ITEM) {
                        ItemStack iconx = spawnEggs.getOrDefault(typeEntry, ItemStack.EMPTY);
                        out.add(
                           new RiptideRegistryListSettingScreen.Entry(
                              this.id(BuiltInRegistries.ENTITY_TYPE.getKey(typeEntry)),
                              typeEntry.getDescription().getString(),
                              this.entityGroup(typeEntry),
                              iconx,
                              iconx.isEmpty()
                           )
                        );
                     }
                  }
               );
            break;
         case SOUND_EVENT_LIST:
            BuiltInRegistries.SOUND_EVENT
               .forEach(
                  sound -> {
                     Identifier soundId = BuiltInRegistries.SOUND_EVENT.getKey(sound);
                     if (soundId != null) {
                        out.add(
                           new RiptideRegistryListSettingScreen.Entry(
                              this.id(soundId), this.soundLabel(soundId), this.soundGroup(soundId), this.soundIcon(soundId, this.spawnEggIcons()), false
                           )
                        );
                     }
                  }
               );
            break;
         case STORAGE_LIST:
            for (ModuleStorageEsp.Target target : ModuleStorageEsp.TARGETS) {
               ItemStack icon = target.icon();
               out.add(new RiptideRegistryListSettingScreen.Entry(target.id, target.label, target.group, icon, icon.isEmpty()));
            }
      }

      out.sort(
         Comparator.<RiptideRegistryListSettingScreen.Entry, String>comparing(entry -> entry.group)
            .thenComparing(RiptideRegistryListSettingScreen.Entry::labelLower)
      );
      return out;
   }

   private Map<EntityType<?>, ItemStack> spawnEggIcons() {
      Map<EntityType<?>, ItemStack> out = new HashMap<>();
      BuiltInRegistries.ITEM.forEach(item -> {
         TypedEntityData<EntityType<?>> data = (TypedEntityData<EntityType<?>>)item.components().get(DataComponents.ENTITY_DATA);
         if (data != null) {
            out.put((EntityType<?>)data.type(), RiptideDisplayItemUtils.toStack(item));
         }
      });
      return out;
   }

   private ItemStack soundIcon(Identifier soundId, Map<EntityType<?>, ItemStack> spawnEggs) {
      if (soundId == null) {
         return RiptideDisplayItemUtils.toStack(Items.NOTE_BLOCK);
      } else {
         String path = soundId.getPath().toLowerCase(Locale.ROOT);
         String normalized = path.replace('.', '_');
         String[] parts = normalized.split("_+");
         ItemStack known = this.knownSoundIcon(normalized);
         if (!known.isEmpty()) {
            return known;
         } else {
            if (path.startsWith("entity.")) {
               ItemStack entity = this.resolveEntityIcon(parts, spawnEggs);
               if (!entity.isEmpty()) {
                  return entity;
               }
            }

            if (path.startsWith("block.")) {
               ItemStack block = this.resolveBlockSoundIcon(parts);
               if (!block.isEmpty()) {
                  return block;
               }
            }

            if (path.startsWith("item.")) {
               ItemStack item = this.resolveItemSoundIcon(parts);
               if (!item.isEmpty()) {
                  return item;
               }
            }

            ItemStack registry = this.resolveAnyRegistryIcon(parts, spawnEggs);
            return registry.isEmpty() ? RiptideDisplayItemUtils.toStack(Items.NOTE_BLOCK) : registry;
         }
      }
   }

   private ItemStack knownSoundIcon(String normalizedPath) {
      if (normalizedPath.contains("fishing_bobber")) {
         return RiptideDisplayItemUtils.toStack(Items.FISHING_ROD);
      } else if (normalizedPath.contains("lava")) {
         return RiptideDisplayItemUtils.toStack(Items.LAVA_BUCKET);
      } else if (normalizedPath.contains("water") || normalizedPath.contains("rain") || normalizedPath.contains("bubble")) {
         return RiptideDisplayItemUtils.toStack(Items.WATER_BUCKET);
      } else if (normalizedPath.contains("bucket_empty_lava") || normalizedPath.contains("bucket_fill_lava")) {
         return RiptideDisplayItemUtils.toStack(Items.LAVA_BUCKET);
      } else if (normalizedPath.contains("bucket_empty") || normalizedPath.contains("bucket_fill")) {
         return RiptideDisplayItemUtils.toStack(Items.BUCKET);
      } else if (normalizedPath.contains("firework")) {
         return RiptideDisplayItemUtils.toStack(Items.FIREWORK_ROCKET);
      } else if (normalizedPath.contains("experience_orb")) {
         return RiptideDisplayItemUtils.toStack(Items.EXPERIENCE_BOTTLE);
      } else if (normalizedPath.contains("lightning") || normalizedPath.contains("thunder")) {
         return RiptideDisplayItemUtils.toStack((Item)Items.LIGHTNING_ROD.weathering().unaffected());
      } else if (normalizedPath.contains("music_disc_")) {
         return this.resolveItemIcon(normalizedPath.substring(normalizedPath.indexOf("music_disc_")));
      } else if (normalizedPath.startsWith("music_") || normalizedPath.startsWith("record_")) {
         return RiptideDisplayItemUtils.toStack(Items.JUKEBOX);
      } else if (normalizedPath.contains("goat_horn")) {
         return RiptideDisplayItemUtils.toStack(Items.GOAT_HORN);
      } else if (normalizedPath.contains("note_block")) {
         return RiptideDisplayItemUtils.toStack(Items.NOTE_BLOCK);
      } else if (normalizedPath.contains("ui_") || normalizedPath.startsWith("ui")) {
         return RiptideDisplayItemUtils.toStack(Items.COMPASS);
      } else if (normalizedPath.contains("ambient_cave")) {
         return RiptideDisplayItemUtils.toStack(Items.TORCH);
      } else {
         return normalizedPath.contains("ambient_underwater") ? RiptideDisplayItemUtils.toStack(Items.WATER_BUCKET) : ItemStack.EMPTY;
      }
   }

   private ItemStack resolveEntityIcon(String[] parts, Map<EntityType<?>, ItemStack> spawnEggs) {
      for (String candidate : this.candidates(parts, 1)) {
         EntityType<?> type = (EntityType<?>)BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.withDefaultNamespace(candidate)).orElse(null);
         if (type != null) {
            ItemStack icon = spawnEggs.getOrDefault(type, ItemStack.EMPTY);
            if (!icon.isEmpty()) {
               return icon;
            }
         }
      }

      if (this.containsPart(parts, "player")) {
         return RiptideDisplayItemUtils.toStack(Items.PLAYER_HEAD);
      } else if (this.containsPart(parts, "arrow")) {
         return RiptideDisplayItemUtils.toStack(Items.ARROW);
      } else if (this.containsPart(parts, "trident")) {
         return RiptideDisplayItemUtils.toStack(Items.TRIDENT);
      } else if (this.containsPart(parts, "boat")) {
         return RiptideDisplayItemUtils.toStack(Items.OAK_BOAT);
      } else if (this.containsPart(parts, "minecart")) {
         return RiptideDisplayItemUtils.toStack(Items.MINECART);
      } else {
         return this.containsPart(parts, "item") ? RiptideDisplayItemUtils.toStack(Items.ITEM_FRAME) : ItemStack.EMPTY;
      }
   }

   private ItemStack resolveBlockSoundIcon(String[] parts) {
      for (String candidate : this.candidates(parts, 1)) {
         ItemStack icon = this.resolveBlockIcon(candidate);
         if (!icon.isEmpty()) {
            return icon;
         }
      }

      return ItemStack.EMPTY;
   }

   private ItemStack resolveItemSoundIcon(String[] parts) {
      for (String candidate : this.candidates(parts, 1)) {
         ItemStack icon = this.resolveItemIcon(candidate);
         if (!icon.isEmpty()) {
            return icon;
         }
      }

      return ItemStack.EMPTY;
   }

   private ItemStack resolveAnyRegistryIcon(String[] parts, Map<EntityType<?>, ItemStack> spawnEggs) {
      for (String candidate : this.candidates(parts, 0)) {
         EntityType<?> type = (EntityType<?>)BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.withDefaultNamespace(candidate)).orElse(null);
         if (type != null) {
            ItemStack icon = spawnEggs.getOrDefault(type, ItemStack.EMPTY);
            if (!icon.isEmpty()) {
               return icon;
            }
         }

         ItemStack item = this.resolveItemIcon(candidate);
         if (!item.isEmpty()) {
            return item;
         }

         ItemStack block = this.resolveBlockIcon(candidate);
         if (!block.isEmpty()) {
            return block;
         }
      }

      return ItemStack.EMPTY;
   }

   private List<String> candidates(String[] parts, int start) {
      List<String> out = new ArrayList<>();

      for (int len = Math.min(4, parts.length - start); len >= 1; len--) {
         for (int i = start; i + len <= parts.length; i++) {
            String candidate = this.joinParts(parts, i, len);
            if (!candidate.isBlank() && !this.isSoundEventWord(candidate)) {
               out.add(candidate);
            }
         }
      }

      return out;
   }

   private String joinParts(String[] parts, int start, int len) {
      StringBuilder builder = new StringBuilder();

      for (int i = 0; i < len; i++) {
         String part = parts[start + i];
         if (!part.isBlank()) {
            if (!builder.isEmpty()) {
               builder.append('_');
            }

            builder.append(part);
         }
      }

      return builder.toString();
   }

   private boolean isSoundEventWord(String candidate) {
      return switch (candidate) {
         case "ambient", "angry", "attack", "big", "break", "breathe", "burp", "charge", "click", "clicking", "close", "crack", "death", "dig", "drink", "eat", "empty", "fall", "fill", "flap", "fly", "growl", "hit", "hurt", "idle", "land", "loop", "open", "place", "pop", "ready", "shoot", "small", "splash", "step", "swim", "throw", "use", "walk" -> true;
         default -> false;
      };
   }

   private boolean containsPart(String[] parts, String part) {
      for (String value : parts) {
         if (part.equals(value)) {
            return true;
         }
      }

      return false;
   }

   private ItemStack resolveItemIcon(String path) {
      Item item = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(path)).orElse(Items.AIR);
      return item == Items.AIR ? ItemStack.EMPTY : RiptideDisplayItemUtils.toStack(item);
   }

   private ItemStack resolveBlockIcon(String path) {
      Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(path)).orElse(Blocks.AIR);
      return block != Blocks.AIR && block.asItem() != Items.AIR ? RiptideDisplayItemUtils.toStack(block) : ItemStack.EMPTY;
   }

   private RiptideRegistryListSettingScreen.Entry fallbackEntry(String id) {
      return this.fallbackEntry(id, id);
   }

   private RiptideRegistryListSettingScreen.Entry fallbackEntry(String label, String id) {
      return new RiptideRegistryListSettingScreen.Entry(id, label, "", ItemStack.EMPTY, false);
   }

   private String entityGroup(EntityType<?> type) {
      MobCategory category = type.getCategory();

      return switch (category) {
         case CREATURE -> "Animals";
         case WATER_AMBIENT, WATER_CREATURE, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> "Water Animals";
         case MONSTER -> "Monsters";
         case AMBIENT -> "Ambient";
         default -> "Misc";
      };
   }

   private String soundLabel(Identifier id) {
      if (id == null) {
         return "";
      } else {
         String path = id.getPath().replace('.', ' ').replace('_', ' ');
         String[] parts = path.split(" ");
         StringBuilder out = new StringBuilder();

         for (String part : parts) {
            if (!part.isBlank()) {
               if (!out.isEmpty()) {
                  out.append(' ');
               }

               out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
         }

         return out.isEmpty() ? id.toString() : out.toString();
      }
   }

   private String soundGroup(Identifier id) {
      if (id == null) {
         return "";
      } else {
         String path = id.getPath();
         if (path.startsWith("entity.")) {
            return "Entities";
         } else if (path.startsWith("block.")) {
            return "Blocks";
         } else if (path.startsWith("item.")) {
            return "Items";
         } else if (path.startsWith("music.") || path.startsWith("music_disc.") || path.startsWith("record.")) {
            return "Music";
         } else if (path.startsWith("weather.")) {
            return "Weather";
         } else if (path.startsWith("ambient.")) {
            return "Ambient";
         } else {
            return path.startsWith("ui.") ? "UI" : id.getNamespace();
         }
      }
   }

   private String id(Identifier id) {
      return id == null ? "" : id.toString().toLowerCase(Locale.ROOT);
   }

   private CompactScrollbar.Metrics registryScrollbarMetrics(boolean available) {
      int x = this.panelX() + 10;
      int y = this.panelY() + 24 + 9 + 20 + 10;
      int paneW = (this.panelW() - 30) / 2;
      int paneH = this.panelY() + this.panelH() - y - 12;
      int paneX = available ? x : x + paneW + 10;
      int listTop = y + 24;
      int listH = paneH - 28;
      return this.registryScrollbarMetrics(available, paneX, listTop, paneW, listH);
   }

   private CompactScrollbar.Metrics registryScrollbarMetrics(boolean available, int paneX, int listTop, int paneW, int listH) {
      int contentH = available ? this.availableContentHeight(this.filteredAvailable()) : this.selectedRows().size() * 34;
      int scroll = available ? this.availableScroll : this.selectedScroll;
      return CompactScrollbar.compute(contentH, Math.max(1, listH), paneX + paneW - 6, listTop, 4, Math.max(1, listH), scroll);
   }

   private int availableContentHeight(List<RiptideRegistryListSettingScreen.Entry> rows) {
      int height = 0;
      String lastGroup = "";

      for (RiptideRegistryListSettingScreen.Entry entry : rows) {
         if (this.option != null && showsGroupHeaders(this.option) && !entry.group.equals(lastGroup)) {
            height += 16;
            lastGroup = entry.group;
         }

         height += 34;
      }

      return height;
   }

   private static boolean showsGroupHeaders(Setting<?, ?> option) {
      return option.kind() == Kind.ENTITY_TYPE_LIST || option.kind() == Kind.SOUND_EVENT_LIST || option.kind() == Kind.STORAGE_LIST;
   }

   private boolean insideAvailablePane(int mx, int my) {
      int x = this.panelX() + 10;
      int y = this.panelY() + 24 + 9 + 20 + 10;
      int paneW = (this.panelW() - 30) / 2;
      int paneH = this.panelY() + this.panelH() - y - 12;
      int listTop = y + 24;
      int listH = paneH - 28;
      return mx >= x && mx < x + paneW && my >= listTop && my < listTop + listH;
   }

   private boolean insideSelectedPane(int mx, int my) {
      int x = this.panelX() + 10;
      int y = this.panelY() + 24 + 9 + 20 + 10;
      int paneW = (this.panelW() - 30) / 2;
      int paneH = this.panelY() + this.panelH() - y - 12;
      int rightX = x + paneW + 10;
      int listTop = y + 24;
      int listH = paneH - 28;
      return mx >= rightX && mx < rightX + paneW && my >= listTop && my < listTop + listH;
   }

   private void clampPaneScrolls() {
      this.availableScroll = this.clamp(this.availableScroll, 0, this.registryScrollbarMetrics(true).maxScroll());
      this.selectedScroll = this.clamp(this.selectedScroll, 0, this.registryScrollbarMetrics(false).maxScroll());
   }

   private int snapSelectedScroll(int offset) {
      return Math.max(0, offset / 34) * 34;
   }

   private String titleText() {
      return this.option == null ? "Select" : "Select " + this.option.label();
   }

   private int panelX() {
      return DirectLayout.centerPanel(this.screenWidth(), this.panelW(), 4);
   }

   private int panelY() {
      return DirectLayout.centerPanel(this.screenHeight(), this.panelH(), 4);
   }

   private int panelW() {
      return DirectLayout.fitPanelDimension(this.screenWidth(), 4, 620);
   }

   private int panelH() {
      return DirectLayout.fitPanelDimension(this.screenHeight(), 4, 360);
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

   private record Entry(String id, String label, String group, ItemStack icon, boolean iconFallback) {
      private Entry(String id, String label, String group, ItemStack icon, boolean iconFallback) {
         label = label != null && !label.isBlank() ? label : id;
         group = group == null ? "" : group;
         icon = icon == null ? ItemStack.EMPTY : icon;
         this.id = id;
         this.label = label;
         this.group = group;
         this.icon = icon;
         this.iconFallback = iconFallback;
      }

      String labelLower() {
         return this.label.toLowerCase(Locale.ROOT);
      }
   }

   private record Hit(RiptideRegistryListSettingScreen.HitType type, int x, int y, int w, int h, String value) {
      boolean contains(int mx, int my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   private static enum HitType {
      ADD,
      REMOVE,
      CLOSE;
   }

   private record SelectedRow(String id, RiptideRegistryListSettingScreen.Entry entry, boolean invalid) {
   }
}
