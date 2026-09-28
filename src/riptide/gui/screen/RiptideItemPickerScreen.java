package riptide.gui.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.components.CompactScreenPanel;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactSurfaces;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectLayout;
import riptide.util.RiptideChatField;
import riptide.util.RiptideDisplayItemUtils;
import riptide.util.RiptideTheme;
import riptide.util.RiptideUiScale;

public final class RiptideItemPickerScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int PANEL_W = 420;
   private static final int PANEL_H = 330;
   private static final int HEADER_H = 24;
   private static final int SEARCH_H = 20;
   private static final int ROW_H = 28;
   private static final int ICON_SIZE = 20;
   private static final int BG = -872086265;
   private static final int ROW = -1441722092;
   private static final int ROW_HOVER = -1441000940;
   private static final int ROW_SELECTED = -1440345836;
   private static final int TEXT = -791321;
   private static final int MUTED = -4743522;
   private static final int RED = -50373;
   private final Screen parent;
   private final Consumer<String> onPick;
   private final List<RiptideItemPickerScreen.Entry> entries;
   private final List<RiptideItemPickerScreen.Hit> hits = new ArrayList<>();
   private String selectedId;
   private RiptideChatField searchField;
   private int scroll;
   private boolean scrollbarDragging;
   private int scrollbarGrabOffset;
   private String cachedSearch;
   private List<RiptideItemPickerScreen.Entry> cachedRows = List.of();

   private static int muted() {
      return RiptideTheme.recolor(-4743522, RiptideTheme.Channel.TEXT);
   }

   public RiptideItemPickerScreen(Screen parent, String selectedId, Consumer<String> onPick) {
      super(Component.literal("Pick Item"));
      this.parent = parent;
      this.selectedId = normalize(selectedId);
      this.onPick = onPick == null ? ignored -> {} : onPick;
      this.entries = this.buildEntries();
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
         this.drawTopBar(graphics, x, y, panelW, panelH, mx, my);
         if (panelW < 180 || panelH < 140) {
            this.drawText(graphics, "Window too small.", x + 8, y + 24 + 10, muted(), Math.max(0, panelW - 16));
            return;
         }

         int searchX = x + 10;
         int searchY = y + 24 + 9;
         this.ensureSearchField(searchX, searchY, panelW - 20, 20);
         this.searchField.render(graphics, mx, my, delta);
         int listX = x + 10;
         int listY = searchY + 20 + 9;
         int listW = panelW - 20;
         int listH = y + panelH - listY - 10;
         this.frame(graphics, listX, listY, listW, listH, -1442248437, THEME.borderSoft());
         UiBounds clip = UiBounds.of(listX + 1, listY + 1, Math.max(1, listW - 8), Math.max(1, listH - 2));
         UiScissorStack.global().push(graphics, clip);

         try {
            this.drawRows(graphics, listX + 3, listY + 3, listW - 9, Math.max(1, listH - 6), mx, my);
         } finally {
            UiScissorStack.global().pop(graphics);
         }

         CompactScrollbar.Metrics metrics = this.scrollbarMetrics(listX, listY, listW, listH);
         CompactScrollbar.draw(graphics, metrics, metrics.contains(mx, my), this.scrollbarDragging);
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private void drawRows(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mx, int my) {
      List<RiptideItemPickerScreen.Entry> rows = this.filteredRows();
      int maxScroll = Math.max(0, rows.size() * 28 - h + 4);
      this.scroll = clamp(this.scroll, 0, maxScroll);
      if (rows.isEmpty()) {
         this.drawText(graphics, "No items matched.", x + 4, y + 6, muted(), w - 8);
      } else {
         int first = clamp(this.scroll / 28, 0, Math.max(0, rows.size() - 1));
         int last = clamp((this.scroll + h - 1) / 28 + 1, 0, rows.size() - 1);

         for (int i = first; i <= last; i++) {
            RiptideItemPickerScreen.Entry entry = rows.get(i);
            int rowY = y - this.scroll + i * 28;
            if (rowY + 28 > y && rowY < y + h) {
               this.drawRow(graphics, entry, x, rowY, w, mx, my);
            }
         }
      }
   }

   private void drawRow(GuiGraphicsExtractor graphics, RiptideItemPickerScreen.Entry entry, int x, int y, int w, int mx, int my) {
      boolean selected = entry.id.equals(this.selectedId);
      boolean over = mx >= x && mx < x + w && my >= y && my < y + 28;
      CompactSurfaces.tintedRow(graphics, x, y, w, 27, selected ? -1440345836 : (over ? -1441000940 : -1441722092));
      this.drawIcon(graphics, entry.icon, x + 6, y + Math.max(2, 4));
      int textX = x + 6 + 20 + 7;
      int textMax = Math.max(1, w - (textX - x) - 8);
      this.drawText(graphics, entry.label, textX, y + 4, selected ? -50373 : -791321, textMax);
      this.drawText(graphics, entry.id, textX, y + 17, muted(), textMax);
      this.hits.add(new RiptideItemPickerScreen.Hit(RiptideItemPickerScreen.HitType.ITEM, x, y, w, 28, entry.id));
   }

   private void drawIcon(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
      if (stack != null && !stack.isEmpty()) {
         graphics.pose().pushMatrix();
         graphics.pose().scale(1.25F, 1.25F);
         graphics.item(stack, Math.round(x / 1.25F), Math.round(y / 1.25F));
         graphics.pose().popMatrix();
      }
   }

   private void drawTopBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int mx, int my) {
      UiBounds bounds = UiBounds.of(x, y, width, height);
      CompactScreenPanel.render(UiContexts.overlay(graphics, this.font, mx, my), bounds, 24, "Pick Item", mx >= x && mx < x + width && my >= y && my < y + 24);
      UiBounds close = CompactScreenPanel.closeButton(bounds, 24);
      this.hits.add(new RiptideItemPickerScreen.Hit(RiptideItemPickerScreen.HitType.CLOSE, close.x(), close.y(), close.width(), close.height(), ""));
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      int mx = RiptideUiScale.toVirtualInt(event.x());
      int my = RiptideUiScale.toVirtualInt(event.y());
      if (event.button() != 0) {
         return true;
      } else if (this.searchField != null && this.searchField.mouseClicked(mx, my, event.button())) {
         return true;
      } else {
         CompactScrollbar.Metrics metrics = this.scrollbarMetrics();
         if (metrics.hasScroll() && metrics.contains(mx, my)) {
            this.scrollbarDragging = true;
            this.scrollbarGrabOffset = metrics.overThumb(mx, my) ? my - metrics.thumbY() : metrics.thumbHeight() / 2;
            this.scroll = CompactScrollbar.scrollFromThumb(metrics, my, this.scrollbarGrabOffset);
            return true;
         } else {
            for (int i = this.hits.size() - 1; i >= 0; i--) {
               RiptideItemPickerScreen.Hit hit = this.hits.get(i);
               if (hit.contains(mx, my)) {
                  switch (hit.type) {
                     case CLOSE:
                        this.onClose();
                        break;
                     case ITEM:
                        this.pick(hit.value);
                  }

                  return true;
               }
            }

            return true;
         }
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      this.scrollbarDragging = false;
      if (this.searchField != null) {
         this.searchField.mouseReleased(RiptideUiScale.toVirtualInt(event.x()), RiptideUiScale.toVirtualInt(event.y()), event.button());
      }

      return true;
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      int mx = RiptideUiScale.toVirtualInt(event.x());
      int my = RiptideUiScale.toVirtualInt(event.y());
      if (this.scrollbarDragging) {
         this.scroll = CompactScrollbar.scrollFromThumb(this.scrollbarMetrics(), my, this.scrollbarGrabOffset);
         return true;
      } else {
         return this.searchField != null && this.searchField.mouseDragged(mx, my, event.button(), dx, dy) ? true : true;
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      int amount = scrollY < 0.0 ? 28 : -28;
      this.scroll = clamp(this.scroll + amount, 0, this.scrollbarMetrics().maxScroll());
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
         this.searchField = new RiptideChatField(Minecraft.getInstance(), this.font, x, y, w, h, true);
         this.searchField.setPlaceholder(Component.literal("Search item id or name"));
         this.searchField.setChangedListener(value -> this.scroll = 0);
         this.searchField.setSubmitHandler(value -> {
            List<RiptideItemPickerScreen.Entry> rows = this.filteredRows();
            if (!rows.isEmpty()) {
               this.pick(rows.get(0).id);
            }

            return true;
         });
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

   private void pick(String id) {
      this.selectedId = normalize(id);
      this.onPick.accept(this.selectedId);
      this.onClose();
   }

   private List<RiptideItemPickerScreen.Entry> filteredRows() {
      String needle = this.searchText().trim().toLowerCase(Locale.ROOT);
      if (needle.equals(this.cachedSearch)) {
         return this.cachedRows;
      } else {
         this.cachedSearch = needle;
         if (needle.isEmpty()) {
            this.cachedRows = this.entries;
            return this.cachedRows;
         } else {
            List<RiptideItemPickerScreen.Entry> out = new ArrayList<>();

            for (RiptideItemPickerScreen.Entry entry : this.entries) {
               if (entry.id.contains(needle) || entry.labelLower.contains(needle)) {
                  out.add(entry);
               }
            }

            this.cachedRows = List.copyOf(out);
            return this.cachedRows;
         }
      }
   }

   private List<RiptideItemPickerScreen.Entry> buildEntries() {
      List<RiptideItemPickerScreen.Entry> out = new ArrayList<>();
      BuiltInRegistries.ITEM.forEach(item -> {
         if (item != Items.AIR) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null) {
               ItemStack stack = RiptideDisplayItemUtils.toStack(item);
               String itemId = id.toString().toLowerCase(Locale.ROOT);
               out.add(new RiptideItemPickerScreen.Entry(itemId, item.getName(item.getDefaultInstance()).getString(), stack));
            }
         }
      });
      out.sort(Comparator.comparing(RiptideItemPickerScreen.Entry::labelLower).thenComparing(entry -> entry.id));
      return List.copyOf(out);
   }

   private CompactScrollbar.Metrics scrollbarMetrics() {
      int x = this.panelX() + 10;
      int y = this.panelY() + 24 + 9 + 20 + 9;
      int w = this.panelW() - 20;
      int h = this.panelY() + this.panelH() - y - 10;
      return this.scrollbarMetrics(x, y, w, h);
   }

   private CompactScrollbar.Metrics scrollbarMetrics(int listX, int listY, int listW, int listH) {
      int viewH = Math.max(1, listH - 6);
      int contentH = this.filteredRows().size() * 28;
      return CompactScrollbar.compute(contentH, viewH, listX + listW - 6, listY + 1, 4, Math.max(1, listH - 2), this.scroll);
   }

   private int panelX() {
      return DirectLayout.centerPanel(this.screenWidth(), this.panelW(), 4);
   }

   private int panelY() {
      return DirectLayout.centerPanel(this.screenHeight(), this.panelH(), 4);
   }

   private int panelW() {
      return DirectLayout.fitPanelDimension(this.screenWidth(), 4, 420);
   }

   private int panelH() {
      return DirectLayout.fitPanelDimension(this.screenHeight(), 4, 330);
   }

   private void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill, int border) {
      UiRenderer.frame(graphics, UiBounds.of(x, y, w, h), fill, border);
   }

   private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, int maxWidth) {
      String display = UiText.trimToWidth(this.font, text == null ? "" : text, Math.max(0, maxWidth), THEME.fontFor(UiTone.BODY), color);
      UiText.draw(graphics, this.font, display, THEME.fontFor(UiTone.BODY), color, x, y, false);
   }

   private static String normalize(String id) {
      return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private record Entry(String id, String label, String labelLower, ItemStack icon) {
      private Entry(String id, String label, ItemStack icon) {
         this(id, label != null && !label.isBlank() ? label : id, (label == null ? id : label).toLowerCase(Locale.ROOT), icon == null ? ItemStack.EMPTY : icon);
      }
   }

   private record Hit(RiptideItemPickerScreen.HitType type, int x, int y, int w, int h, String value) {
      boolean contains(int mx, int my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   private static enum HitType {
      CLOSE,
      ITEM;
   }
}
