package riptide.gui.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.components.CompactOverlayButton;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectLayout;
import riptide.util.RiptideColors;
import riptide.util.RiptideConfig;
import riptide.util.RiptideUiIcons;
import riptide.util.RiptideUiScale;

public class RiptidePluginLibraryScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int PANEL_WIDTH = 472;
   private static final int PANEL_MARGIN = 12;
   private static final int ROW_HEIGHT = 22;
   private static final int TOP_PANEL_Y = 20;
   private static final int TOP_PANEL_HEIGHT = 86;
   private static final int LIST_TOP = 116;
   private static final int LIST_HEADER_HEIGHT = 20;
   private static final int LIST_BOTTOM_MARGIN = 12;
   private static final int LIST_SCROLLBAR_WIDTH = 4;
   private static final int LIST_SCROLLBAR_GUTTER = 12;
   private static final int CHILD_INDENT = 16;
   private static final int ROW_BTN_H = 16;
   private static final int ROW_BTN_GAP = 5;
   private static final int COPY_W = 42;
   private static final int DELETE_W = 18;
   private final Screen parent;
   private final List<CompactOverlayButton> buttons = new ArrayList<>();
   private final List<RiptidePluginLibraryScreen.RowHit> rowHits = new ArrayList<>();
   private final List<RiptidePluginLibraryScreen.Row> visibleRows = new ArrayList<>();
   private final Set<String> expanded = new HashSet<>();
   private EditBox searchField;
   private String searchQuery = "";
   private RiptidePluginLibraryScreen.Mode mode = RiptidePluginLibraryScreen.Mode.BY_SERVER;
   private List<RiptidePluginLibraryScreen.Row> allRows = List.of();
   private int parentCount;
   private int listScrollOffset;
   private boolean scrollbarDragging;
   private int scrollbarGrabOffset;
   private long lastRevision = Long.MIN_VALUE;

   public RiptidePluginLibraryScreen(Screen parent) {
      super(Component.literal("Plugin Library"));
      this.parent = parent;
   }

   protected void init() {
      int fieldX = this.panelX() + 18;
      int fieldW = Math.max(40, this.panelWidth() - 36);
      this.searchField = new EditBox(this.font, fieldX, 70, fieldW, 18, Component.literal("Search"));
      this.searchField.setHint(Component.literal(this.searchHint()));
      this.searchField.setMaxLength(128);
      this.searchField.setValue(this.searchQuery);
      this.searchField.setResponder(value -> {
         this.searchQuery = safeTrim(value);
         this.listScrollOffset = 0;
         this.rebuild();
      });
      this.addRenderableWidget(this.searchField);
      this.lastRevision = RiptideConfig.getGlobal().pluginScanRevision();
      this.rebuild();
   }

   public void tick() {
      long rev = RiptideConfig.getGlobal().pluginScanRevision();
      if (rev != this.lastRevision) {
         this.lastRevision = rev;
         this.rebuild();
      }
   }

   private String searchHint() {
      return this.mode == RiptidePluginLibraryScreen.Mode.BY_SERVER ? "Search servers or plugins..." : "Search plugins...";
   }

   private void setMode(RiptidePluginLibraryScreen.Mode next) {
      if (this.mode != next) {
         this.mode = next;
         this.expanded.clear();
         this.listScrollOffset = 0;
         if (this.searchField != null) {
            this.searchField.setHint(Component.literal(this.searchHint()));
         }

         this.rebuild();
      }
   }

   private void toggleExpand(String key) {
      if (!this.expanded.remove(key)) {
         this.expanded.add(key);
      }

      this.rebuild();
   }

   private List<RiptidePluginLibraryScreen.ServerRecord> loadServers() {
      Map<String, RiptidePluginLibraryScreen.ServerRecord> byAddress = new LinkedHashMap<>();
      Map<String, RiptideConfig.PluginScanCacheEntry> all = RiptideConfig.getGlobal().allPluginScans();

      for (Entry<String, RiptideConfig.PluginScanCacheEntry> item : all.entrySet()) {
         RiptideConfig.PluginScanCacheEntry entry = item.getValue();
         if (entry != null && entry.plugins != null && !entry.plugins.isEmpty()) {
            String address = entry.serverAddress != null && !entry.serverAddress.isBlank() ? entry.serverAddress : addressFromKey(item.getKey());
            if (address != null && !address.isBlank()) {
               String lower = address.toLowerCase(Locale.ROOT);
               RiptidePluginLibraryScreen.ServerRecord existing = byAddress.get(lower);
               if (existing == null || existing.scannedAtMs < entry.scannedAtMs) {
                  String name = entry.serverName != null && !entry.serverName.isBlank() ? entry.serverName : address;
                  byAddress.put(lower, new RiptidePluginLibraryScreen.ServerRecord(address, name, dedupeSorted(entry.plugins), entry.scannedAtMs));
               }
            }
         }
      }

      List<RiptidePluginLibraryScreen.ServerRecord> servers = new ArrayList<>(byAddress.values());
      servers.sort(Comparator.<RiptidePluginLibraryScreen.ServerRecord>comparingLong(r -> r.scannedAtMs).reversed());
      return servers;
   }

   private static String addressFromKey(String key) {
      if (key == null) {
         return "";
      } else {
         int cut = key.lastIndexOf(124);
         return cut > 0 ? key.substring(0, cut) : key;
      }
   }

   private static List<String> dedupeSorted(List<String> plugins) {
      Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

      for (String p : plugins) {
         if (p != null && !p.isBlank()) {
            set.add(p.trim());
         }
      }

      return new ArrayList<>(set);
   }

   private List<RiptidePluginLibraryScreen.Row> buildRows() {
      String query = this.searchQuery.toLowerCase(Locale.ROOT);
      List<RiptidePluginLibraryScreen.ServerRecord> servers = this.loadServers();
      return this.mode == RiptidePluginLibraryScreen.Mode.BY_SERVER ? this.buildServerRows(servers, query) : this.buildPluginRows(servers, query);
   }

   private List<RiptidePluginLibraryScreen.Row> buildServerRows(List<RiptidePluginLibraryScreen.ServerRecord> servers, String query) {
      List<RiptidePluginLibraryScreen.Row> rows = new ArrayList<>();

      for (RiptidePluginLibraryScreen.ServerRecord server : servers) {
         boolean nameHit = query.isEmpty() || fuzzy(server.name, query) || fuzzy(server.address, query);
         boolean pluginHit = false;
         if (!query.isEmpty()) {
            for (String p : server.plugins) {
               if (fuzzy(p, query)) {
                  pluginHit = true;
                  break;
               }
            }
         }

         if (query.isEmpty() || nameHit || pluginHit) {
            String key = "srv:" + server.address.toLowerCase(Locale.ROOT);
            boolean open = this.expanded.contains(key);
            RiptidePluginLibraryScreen.Row row = RiptidePluginLibraryScreen.Row.parent(
               RiptidePluginLibraryScreen.RowType.SERVER,
               key,
               server.label,
               server.plugins.size() + (server.plugins.size() == 1 ? " plugin  " : " plugins  ") + relativeTime(server.scannedAtMs),
               open
            );
            row.address = server.address;
            row.plugins = server.plugins;
            rows.add(row);
            if (open) {
               List<String> shown = server.plugins;
               if (!query.isEmpty()) {
                  List<String> matched = new ArrayList<>();

                  for (String plugin : server.plugins) {
                     if (fuzzy(plugin, query)) {
                        matched.add(plugin);
                     }
                  }

                  if (!matched.isEmpty()) {
                     shown = matched;
                  }
               }

               for (String pluginx : shown) {
                  rows.add(RiptidePluginLibraryScreen.Row.child(RiptidePluginLibraryScreen.RowType.SERVER_PLUGIN, pluginx, ""));
               }
            }
         }
      }

      this.parentCount = rows.isEmpty() ? 0 : (int)rows.stream().filter(r -> r.type == RiptidePluginLibraryScreen.RowType.SERVER).count();
      return rows;
   }

   private List<RiptidePluginLibraryScreen.Row> buildPluginRows(List<RiptidePluginLibraryScreen.ServerRecord> servers, String query) {
      Map<String, List<RiptidePluginLibraryScreen.ServerRecord>> byPlugin = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

      for (RiptidePluginLibraryScreen.ServerRecord server : servers) {
         for (String plugin : server.plugins) {
            if (query.isEmpty() || fuzzy(plugin, query)) {
               byPlugin.computeIfAbsent(plugin, k -> new ArrayList<>()).add(server);
            }
         }
      }

      List<Entry<String, List<RiptidePluginLibraryScreen.ServerRecord>>> ordered = new ArrayList<>(byPlugin.entrySet());
      ordered.sort(
         Comparator.<Entry<String, List<RiptidePluginLibraryScreen.ServerRecord>>>comparingInt(e -> e.getValue().size())
            .reversed()
            .thenComparing(e -> e.getKey().toLowerCase(Locale.ROOT))
      );
      List<RiptidePluginLibraryScreen.Row> rows = new ArrayList<>();

      for (Entry<String, List<RiptidePluginLibraryScreen.ServerRecord>> entry : ordered) {
         int count = entry.getValue().size();
         String key = "plg:" + entry.getKey().toLowerCase(Locale.ROOT);
         boolean open = this.expanded.contains(key);
         RiptidePluginLibraryScreen.Row row = RiptidePluginLibraryScreen.Row.parent(
            RiptidePluginLibraryScreen.RowType.PLUGIN, key, entry.getKey(), "on " + count + (count == 1 ? " server" : " servers"), open
         );
         row.servers = entry.getValue();
         rows.add(row);
         if (open) {
            for (RiptidePluginLibraryScreen.ServerRecord server : entry.getValue()) {
               rows.add(RiptidePluginLibraryScreen.Row.child(RiptidePluginLibraryScreen.RowType.PLUGIN_SERVER, server.label, relativeTime(server.scannedAtMs)));
            }
         }
      }

      this.parentCount = ordered.size();
      return rows;
   }

   private static boolean fuzzy(String value, String queryLower) {
      if (queryLower.isEmpty()) {
         return true;
      } else if (value == null) {
         return false;
      } else {
         String v = value.toLowerCase(Locale.ROOT);
         if (v.contains(queryLower)) {
            return true;
         } else {
            int vi = 0;
            int qi = 0;
            int misses = 0;

            for (int maxMisses = Math.max(1, queryLower.length() / 3); vi < v.length() && qi < queryLower.length(); vi++) {
               if (v.charAt(vi) == queryLower.charAt(qi)) {
                  qi++;
               } else if (qi > 0) {
                  misses++;
               }

               if (misses > maxMisses) {
                  return false;
               }
            }

            return qi == queryLower.length();
         }
      }
   }

   private static String relativeTime(long ms) {
      if (ms <= 0L) {
         return "unknown";
      } else {
         long d = System.currentTimeMillis() - ms;
         if (d < 60000L) {
            return "just now";
         } else {
            long mins = d / 60000L;
            if (mins < 60L) {
               return mins + "m ago";
            } else {
               long hrs = mins / 60L;
               if (hrs < 24L) {
                  return hrs + "h ago";
               } else {
                  long days = hrs / 24L;
                  if (days < 30L) {
                     return days + "d ago";
                  } else {
                     long months = days / 30L;
                     return months < 12L ? months + "mo ago" : months / 12L + "y ago";
                  }
               }
            }
         }
      }
   }

   private void rebuild() {
      this.buttons.clear();
      this.rowHits.clear();
      this.visibleRows.clear();
      this.buttons.add(CompactOverlayButton.create(10, 10, 56, 20, Component.literal("Back"), b -> this.onClose()));
      int toggleY = 46;
      int toggleW = Math.max(70, Math.min(110, (this.panelWidth() - 36 - 6) / 2));
      int toggleX = this.panelX() + 18;
      this.buttons
         .add(
            CompactOverlayButton.create(
                  toggleX, toggleY, toggleW, 18, Component.literal("By Server"), b -> this.setMode(RiptidePluginLibraryScreen.Mode.BY_SERVER)
               )
               .setVariant(
                  this.mode == RiptidePluginLibraryScreen.Mode.BY_SERVER ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.SECONDARY
               )
         );
      this.buttons
         .add(
            CompactOverlayButton.create(
                  toggleX + toggleW + 6, toggleY, toggleW, 18, Component.literal("By Plugin"), b -> this.setMode(RiptidePluginLibraryScreen.Mode.BY_PLUGIN)
               )
               .setVariant(
                  this.mode == RiptidePluginLibraryScreen.Mode.BY_PLUGIN ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.SECONDARY
               )
         );
      this.allRows = this.buildRows();
      int maxScroll = this.maxScroll(this.allRows.size());
      this.listScrollOffset = Math.max(0, Math.min(this.listScrollOffset, maxScroll));
      int firstVisible = this.listScrollOffset / 22;
      int rowY = this.rowsTop() - this.listScrollOffset % 22;

      for (int i = firstVisible; i < this.allRows.size() && rowY < this.rowsBottom(); rowY += 22) {
         if (rowY + 22 > this.rowsTop()) {
            RiptidePluginLibraryScreen.Row row = this.allRows.get(i);
            row.renderY = rowY;
            this.visibleRows.add(row);
            boolean fullyVisible = rowY >= this.rowsTop() && rowY + 22 <= this.rowsBottom();
            boolean parent = row.type == RiptidePluginLibraryScreen.RowType.SERVER || row.type == RiptidePluginLibraryScreen.RowType.PLUGIN;
            if (parent && fullyVisible) {
               int btnY = rowY + 3;
               int cursor = this.rowRight();
               if (row.type == RiptidePluginLibraryScreen.RowType.SERVER) {
                  cursor -= 18;
                  this.buttons
                     .add(
                        CompactOverlayButton.create(cursor, btnY, 18, 16, Component.empty(), b -> this.deleteServer(row))
                           .setVariant(CompactOverlayButton.Variant.DANGER)
                           .setIcon(RiptideUiIcons.TRASH)
                     );
                  cursor -= 5;
               }

               cursor -= 42;
               this.buttons
                  .add(
                     CompactOverlayButton.create(cursor, btnY, 42, 16, Component.literal("Copy"), b -> this.copyRow(row))
                        .setVariant(CompactOverlayButton.Variant.SECONDARY)
                  );
               this.rowHits.add(new RiptidePluginLibraryScreen.RowHit(this.rowX(), rowY, Math.max(1, cursor - 4 - this.rowX()), 22, row.key));
            }
         }

         i++;
      }
   }

   private void deleteServer(RiptidePluginLibraryScreen.Row row) {
      if (row.type == RiptidePluginLibraryScreen.RowType.SERVER && row.address != null) {
         RiptideConfig.getGlobal().removePluginScan(row.address);
         this.expanded.remove(row.key);
         this.lastRevision = RiptideConfig.getGlobal().pluginScanRevision();
         this.rebuild();
      }
   }

   private void copyRow(RiptidePluginLibraryScreen.Row row) {
      String text;
      String note;
      if (row.type == RiptidePluginLibraryScreen.RowType.SERVER && row.plugins != null) {
         text = String.join("\n", row.plugins);
         note = "Copied " + row.plugins.size() + " plugins from " + row.primary;
      } else {
         if (row.type != RiptidePluginLibraryScreen.RowType.PLUGIN || row.servers == null) {
            return;
         }

         List<String> names = new ArrayList<>();

         for (RiptidePluginLibraryScreen.ServerRecord s : row.servers) {
            names.add(s.label);
         }

         text = String.join("\n", names);
         note = "Copied " + names.size() + " servers running " + row.primary;
      }

      if (!text.isBlank() && this.minecraft != null) {
         this.minecraft.keyboardHandler.setClipboard(text);
         this.toast(note, -13248397);
      }
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int mx = RiptideUiScale.toVirtualInt(mouseX);
      int my = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         UiRenderer.rect(graphics, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), -15856112);
         this.drawPanel(graphics, this.panelX() + 10, 20, this.panelWidth() - 20, 86, -401074149);
         this.drawPanel(graphics, this.listX(), 116, this.listWidth(), this.listPanelHeight(), -1206643689);
         this.drawText(graphics, "Plugin Library", this.panelX() + 18, 29, -855310, this.panelWidth() - 200);
         String count = this.parentCount
            + (
               this.mode == RiptidePluginLibraryScreen.Mode.BY_SERVER
                  ? (this.parentCount == 1 ? " server" : " servers")
                  : (this.parentCount == 1 ? " plugin" : " plugins")
            );
         int countW = UiText.width(this.font, count, bodyFont(), -6645094);
         this.drawText(graphics, count, this.panelX() + this.panelWidth() - 18 - countW, 29, -6645094, countW + 4);
         String header = this.mode == RiptidePluginLibraryScreen.Mode.BY_SERVER ? "Servers you have scanned" : "Plugin -> servers running it";
         this.drawText(graphics, header, this.listX() + 8, 122, -6645094, this.listWidth() - 16);
         String queryLower = this.searchQuery.toLowerCase(Locale.ROOT);
         UiScissorStack.global()
            .push(
               graphics,
               UiBounds.of(this.rowX(), this.rowsTop(), Math.max(0, this.rowRight() + 12 - this.rowX()), Math.max(0, this.rowsBottom() - this.rowsTop()))
            );

         try {
            if (this.allRows.isEmpty()) {
               this.drawText(graphics, this.emptyMessage(), this.rowX() + 4, this.rowsTop() + 6, -6645094, this.rowRight() - this.rowX());
            } else {
               for (RiptidePluginLibraryScreen.Row row : this.visibleRows) {
                  this.renderRow(graphics, row, mx, my, queryLower);
               }
            }
         } finally {
            UiScissorStack.global().pop(graphics);
         }

         for (CompactOverlayButton b : this.buttons) {
            CompactOverlayButton.renderStyled(graphics, this.font, b, mx, my);
         }

         if (!this.compactLayout()) {
            CompactScrollbar.Metrics sb = this.scrollbarMetrics(this.allRows.size());
            CompactScrollbar.draw(graphics, sb, sb.contains(mx, my), this.scrollbarDragging);
         }

         super.extractRenderState(graphics, mx, my, delta);
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private String emptyMessage() {
      return this.searchQuery.isEmpty()
         ? "No saved scans yet. Join a server with Auto Probe on to record its plugins."
         : "Nothing matches \"" + this.searchQuery + "\".";
   }

   private void renderRow(GuiGraphicsExtractor graphics, RiptidePluginLibraryScreen.Row row, int mx, int my, String queryLower) {
      boolean child = row.type == RiptidePluginLibraryScreen.RowType.SERVER_PLUGIN || row.type == RiptidePluginLibraryScreen.RowType.PLUGIN_SERVER;
      int x = this.rowX();
      int y = row.renderY;
      int w = this.rowRight() - this.rowX();
      boolean hovered = my >= y && my < y + 22 && mx >= x && mx < this.rowRight();
      if (!child) {
         int fill = hovered ? 620756991 : 352321535;
         UiRenderer.rect(graphics, UiBounds.of(x, y + 1, w, 20), fill);
         UiRenderer.rect(graphics, UiBounds.of(x, y + 1, 2, 20), row.expanded ? -12588930 : 872415231);
      }

      int textY = y + 7;
      int textLeft = x + 8 + (child ? 16 : 0);
      if (!child) {
         this.drawText(graphics, row.expanded ? "v" : ">", x + 6, textY, -6645094, 8);
         textLeft += 8;
      } else {
         this.drawText(graphics, "-", x + 8 + 16 - 8, textY, -6645094, 8);
      }

      int baseColor = child ? -3158065 : -855310;
      int secondaryColor = -6645094;
      int secondaryW = row.secondary.isEmpty() ? 0 : UiText.width(this.font, row.secondary, bodyFont(), secondaryColor);
      int rowActionReserve = child ? 0 : 47 + (row.type == RiptidePluginLibraryScreen.RowType.SERVER ? 23 : 0);
      int rightLimit = this.rowRight() - rowActionReserve - 4;
      int secondaryX = Math.max(textLeft + 20, rightLimit - secondaryW);
      int primaryMax = Math.max(20, (row.secondary.isEmpty() ? rightLimit : secondaryX - 8) - textLeft);
      this.drawHighlighted(graphics, row.primary, textLeft, textY, primaryMax, baseColor, queryLower);
      if (!row.secondary.isEmpty() && secondaryX > textLeft) {
         this.drawText(graphics, row.secondary, secondaryX, textY, secondaryColor, secondaryW + 2);
      }
   }

   private void drawHighlighted(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int baseColor, String queryLower) {
      Identifier font = bodyFont();
      int idx = queryLower.isEmpty() ? -1 : text.toLowerCase(Locale.ROOT).indexOf(queryLower);
      if (idx >= 0 && UiText.width(this.font, text, font, baseColor) <= maxWidth) {
         String pre = text.substring(0, idx);
         String mid = text.substring(idx, idx + queryLower.length());
         String post = text.substring(idx + queryLower.length());
         int accent = RiptideColors.accent();
         UiText.draw(graphics, this.font, pre, font, baseColor, x, y, false);
         int midX = x + UiText.width(this.font, pre, font, baseColor);
         UiText.draw(graphics, this.font, mid, font, accent, midX, y, false);
         UiText.draw(graphics, this.font, post, font, baseColor, midX + UiText.width(this.font, mid, font, accent), y, false);
      } else {
         UiText.drawFitted(graphics, this.font, text, font, baseColor, x, y, maxWidth, false);
      }
   }

   private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, int maxWidth) {
      UiText.drawFitted(graphics, this.font, text, bodyFont(), color, x, y, Math.max(1, maxWidth), false);
   }

   private static Identifier bodyFont() {
      return THEME.fontFor(UiTone.BODY);
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (this.compactLayout()) {
         return super.mouseClicked(event, doubleClick);
      } else {
         MouseButtonEvent ve = virtualEvent(event);
         double mx = ve.x();
         double my = ve.y();
         if (ve.button() == 0 && !this.compactLayout()) {
            CompactScrollbar.Metrics sb = this.scrollbarMetrics(this.allRows.size());
            if (sb.hasScroll() && sb.contains(mx, my)) {
               this.scrollbarDragging = true;
               this.scrollbarGrabOffset = sb.overThumb(mx, my) ? (int)Math.round(my) - sb.thumbY() : sb.thumbHeight() / 2;
               this.listScrollOffset = CompactScrollbar.scrollFromThumb(sb, my, this.scrollbarGrabOffset);
               this.rebuild();
               return true;
            }
         }

         for (CompactOverlayButton b : this.buttons) {
            if (CompactOverlayButton.fireIfHit(b, mx, my, ve.button())) {
               return true;
            }
         }

         if (ve.button() == 0) {
            for (RiptidePluginLibraryScreen.RowHit hit : this.rowHits) {
               if (hit.contains(mx, my)) {
                  this.toggleExpand(hit.key);
                  return true;
               }
            }
         }

         return super.mouseClicked(ve, doubleClick);
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      if (this.scrollbarDragging) {
         this.scrollbarDragging = false;
         return true;
      } else {
         return super.mouseReleased(virtualEvent(event));
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
      MouseButtonEvent ve = virtualEvent(event);
      if (this.scrollbarDragging) {
         CompactScrollbar.Metrics sb = this.scrollbarMetrics(this.allRows.size());
         this.listScrollOffset = CompactScrollbar.scrollFromThumb(sb, ve.y(), this.scrollbarGrabOffset);
         this.rebuild();
         return true;
      } else {
         return super.mouseDragged(ve, RiptideUiScale.toVirtual(deltaX), RiptideUiScale.toVirtual(deltaY));
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (this.compactLayout()) {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      } else {
         double vy = RiptideUiScale.toVirtual(mouseY);
         double vx = RiptideUiScale.toVirtual(mouseX);
         if (!(vx < this.listX()) && !(vx > this.listX() + this.listWidth()) && !(vy < this.rowsTop()) && !(vy > this.rowsBottom())) {
            int maxScroll = this.maxScroll(this.allRows.size());
            int next = this.listScrollOffset - (int)Math.signum(scrollY) * 22;
            this.listScrollOffset = Math.max(0, Math.min(next, maxScroll));
            this.rebuild();
            return true;
         } else {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
         }
      }
   }

   public boolean keyPressed(KeyEvent input) {
      if (input.key() == 256) {
         this.onClose();
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   private int panelWidth() {
      return DirectLayout.fitPanelDimension(this.screenWidth(), 12, 472);
   }

   private int panelX() {
      return DirectLayout.centerPanel(this.screenWidth(), this.panelWidth(), 12);
   }

   private int listX() {
      return this.panelX() + 10;
   }

   private int listWidth() {
      return this.panelWidth() - 20;
   }

   private int listPanelHeight() {
      return Math.max(42, this.screenHeight() - 116 - 12);
   }

   private int rowsTop() {
      return 136;
   }

   private int rowsBottom() {
      return 116 + this.listPanelHeight() - 6;
   }

   private int viewportHeight() {
      return Math.max(22, this.rowsBottom() - this.rowsTop());
   }

   private int maxScroll(int rowCount) {
      return Math.max(0, rowCount * 22 - this.viewportHeight());
   }

   private int rowX() {
      return this.listX() + 8;
   }

   private int rowRight() {
      return this.listX() + this.listWidth() - 8 - 12;
   }

   private CompactScrollbar.Metrics scrollbarMetrics(int rowCount) {
      int contentPixels = Math.max(0, rowCount) * 22;
      int trackX = this.listX() + this.listWidth() - 8;
      return CompactScrollbar.compute(contentPixels, this.viewportHeight(), trackX, this.rowsTop(), 4, this.viewportHeight(), this.listScrollOffset);
   }

   private boolean compactLayout() {
      return this.panelWidth() < 360 || this.screenHeight() < 160;
   }

   private static enum Mode {
      BY_SERVER,
      BY_PLUGIN;
   }

   private static final class Row {
      RiptidePluginLibraryScreen.RowType type;
      String key = "";
      String primary = "";
      String secondary = "";
      boolean expanded;
      String address;
      List<String> plugins;
      List<RiptidePluginLibraryScreen.ServerRecord> servers;
      int renderY;

      static RiptidePluginLibraryScreen.Row parent(RiptidePluginLibraryScreen.RowType type, String key, String primary, String secondary, boolean expanded) {
         RiptidePluginLibraryScreen.Row row = new RiptidePluginLibraryScreen.Row();
         row.type = type;
         row.key = key;
         row.primary = primary;
         row.secondary = secondary;
         row.expanded = expanded;
         return row;
      }

      static RiptidePluginLibraryScreen.Row child(RiptidePluginLibraryScreen.RowType type, String primary, String secondary) {
         RiptidePluginLibraryScreen.Row row = new RiptidePluginLibraryScreen.Row();
         row.type = type;
         row.primary = primary;
         row.secondary = secondary;
         return row;
      }
   }

   private static final class RowHit {
      final int x;
      final int y;
      final int width;
      final int height;
      final String key;

      RowHit(int x, int y, int width, int height, String key) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         this.key = key;
      }

      boolean contains(double mx, double my) {
         return mx >= this.x && mx <= this.x + this.width && my >= this.y && my <= this.y + this.height;
      }
   }

   private static enum RowType {
      SERVER,
      SERVER_PLUGIN,
      PLUGIN,
      PLUGIN_SERVER;
   }

   private static final class ServerRecord {
      final String address;
      final String name;
      final String label;
      final List<String> plugins;
      final long scannedAtMs;

      ServerRecord(String address, String name, List<String> plugins, long scannedAtMs) {
         this.address = address;
         this.name = name;
         this.label = address;
         this.plugins = plugins;
         this.scannedAtMs = scannedAtMs;
      }
   }
}
