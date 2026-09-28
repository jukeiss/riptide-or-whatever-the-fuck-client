package riptide.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.protocol.Packet;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.components.CompactSurfaces;
import riptide.gui.vanillaui.components.CompactTextInput;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.OverlayTopBar;
import riptide.gui.vanillaui.components.SearchableSelector;
import riptide.gui.vanillaui.components.UiSizing;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectRenderContext;
import riptide.gui.vanillaui.direct.DirectScrollViewport;
import riptide.gui.vanillaui.direct.DirectSurface;
import riptide.gui.vanillaui.direct.DirectUiInsets;
import riptide.gui.vanillaui.direct.DirectUiLabel;
import riptide.gui.vanillaui.direct.DirectViewport;
import riptide.gui.vanillaui.direct.DirectViewportSlot;
import riptide.gui.vanillaui.direct.DirectWindow;

public class RiptidePacketSelectorOverlay extends RiptideOverlayBase {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final int MIN_PANEL_WIDTH = 230;
   private static final int ROW_HEIGHT = 16;
   private static final int MAX_VISIBLE_ROWS = 12;
   private static final int PAD = 6;
   private static final int VIEWPORT_BORDER = 1;
   private static final int HEADER_CONTROL = 12;
   private static final int HEADER_ARROW_WIDTH = 10;
   private static final int HEADER_ARROW_GAP = 3;
   private static final float HEADER_CLICK_DRAG_THRESHOLD = 3.0F;
   private static final int PACKET_LIST_SCROLLBAR_WIDTH = 6;
   private final Font textRenderer;
   private final CompactTheme theme = new CompactTheme();
   private final DirectWindow windowNode = new DirectWindow("Select Packet");
   private final DirectSurface surface = new DirectSurface(this.theme, this.windowNode);
   private final CompactTextInput searchField = new CompactTextInput();
   private final DirectUiLabel summaryLabel = new DirectUiLabel("", UiTone.MUTED).setTrimToBounds(true);
   private final DirectViewportSlot listSlot = new DirectViewportSlot();
   private DirectScrollViewport listViewport = null;
   private final List<Class<? extends Packet<?>>> allPackets = new ArrayList<>();
   private final List<Class<? extends Packet<?>>> sortedC2sPackets = new ArrayList<>();
   private final List<Class<? extends Packet<?>>> sortedS2cPackets = new ArrayList<>();
   private final List<Class<? extends Packet<?>>> sortedAnyPackets = new ArrayList<>();
   private final Set<Class<? extends Packet<?>>> c2sPacketSet = new LinkedHashSet<>();
   private final Map<Class<? extends Packet<?>>, String> packetSearchKeys = new HashMap<>();
   private final SearchableSelector<Class<? extends Packet<?>>> selector = new SearchableSelector<>(
      packetClassx -> this.packetSearchKeys.computeIfAbsent(packetClassx, RiptidePacketSelectorOverlay::searchKey)
   );
   private List<Class<? extends Packet<?>>> activePool = List.of();
   private List<Class<? extends Packet<?>>> filteredPackets = List.of();
   private Set<Class<? extends Packet<?>>> excludedPackets = Set.of();
   private Set<Class<? extends Packet<?>>> selectedPackets = Set.of();
   private boolean visible = false;
   private boolean collapsed = false;
   private boolean dragging = false;
   private boolean dragMoved = false;
   private float dragOffsetX;
   private float dragOffsetY;
   private float pressStartUiX;
   private float pressStartUiY;
   private int pressStartPanelX;
   private int pressStartPanelY;
   private int panelX;
   private int panelY;
   private int panelWidth = 230;
   private int panelHeight = 0;
   private int contentHeight = 0;
   private boolean scrollbarDragging = false;
   private int scrollbarGrabOffset = 0;
   private Consumer<Class<? extends Packet<?>>> onSelect;
   private BiConsumer<Class<? extends Packet<?>>, Boolean> onToggleSelect;
   private boolean closeOnSelect = true;
   private boolean toggleMode = false;
   private boolean uiDirty = true;

   public RiptidePacketSelectorOverlay(Font textRenderer) {
      this.textRenderer = textRenderer;
      this.sortedC2sPackets.addAll(RiptidePacketRegistry.getC2SPackets());
      this.sortedC2sPackets.sort(Comparator.comparing(RiptidePacketNamer::getFriendlyName, String.CASE_INSENSITIVE_ORDER));
      this.sortedS2cPackets.addAll(RiptidePacketRegistry.getS2CPackets());
      this.sortedS2cPackets.sort(Comparator.comparing(RiptidePacketNamer::getFriendlyName, String.CASE_INSENSITIVE_ORDER));
      this.c2sPacketSet.addAll(this.sortedC2sPackets);
      this.sortedAnyPackets.addAll(this.sortedC2sPackets);
      this.sortedAnyPackets.addAll(this.sortedS2cPackets);
      this.allPackets.addAll(this.sortedAnyPackets);
      this.allPackets.sort(Comparator.comparing(RiptidePacketNamer::getFriendlyName, String.CASE_INSENSITIVE_ORDER));

      for (Class<? extends Packet<?>> packetClass : this.allPackets) {
         this.packetSearchKeys.put(packetClass, searchKey(packetClass));
      }

      this.activePool = List.copyOf(this.allPackets);
      this.filteredPackets = List.copyOf(this.allPackets);
      this.buildUi();
   }

   private void buildUi() {
      this.panelWidth = this.panelMinimumWidth();
      this.windowNode.setCenterTitle(false);
      this.windowNode.setTitleTone(UiTone.LABEL);
      this.windowNode.setHeaderControls(true, true);
      this.windowNode
         .setTitleAreaInsets(this.panelPadding() + 2, this.panelPadding() + this.headerControlSize() + this.headerArrowWidth() + this.headerArrowGap() + 12);
      this.windowNode.content().setGap(this.windowContentGap()).setPadding(DirectUiInsets.all(this.panelPadding()));
      this.searchField.setPlaceholder("Search packets...").setFieldHeight(this.searchFieldHeight()).setGrowX(true).setOnChange(text -> this.updateFilter(text));
      this.rebuildUi();
   }

   private void rebuildUi() {
      this.windowNode.content().clearChildren();
      this.windowNode.setTitle(this.toggleMode ? "Packet Selector [Toggle]" : "Packet Selector");
      this.windowNode.content().add(this.searchField);
      this.listSlot.setPreferredHeight(this.computeViewportHeight(this.filteredPackets.size()));
      this.windowNode.content().add(this.listSlot);
      this.summaryLabel
         .setText(
            this.toggleMode ? this.filteredPackets.size() + " packets | Selected " + this.selectedPackets.size() : this.filteredPackets.size() + " packets"
         );
      this.windowNode.content().add(this.summaryLabel);
      this.contentHeight = this.filteredPackets.size() * this.rowHeight();
      this.uiDirty = false;
   }

   private int computeViewportHeight(int itemCount) {
      int visibleRows = Math.max(5, Math.min(this.maxVisibleRows(), Math.max(1, itemCount)));
      return visibleRows * this.rowHeight() + 2;
   }

   public void open(Consumer<Class<? extends Packet<?>>> onSelect) {
      this.openWith(onSelect, this.allPackets, Set.of(), true);
   }

   public void openC2S(Consumer<Class<? extends Packet<?>>> onSelect) {
      this.openC2S(onSelect, Set.of(), true);
   }

   public void openC2S(Consumer<Class<? extends Packet<?>>> onSelect, Collection<Class<? extends Packet<?>>> excludedPackets, boolean closeOnSelect) {
      this.openWith(onSelect, this.sortedC2sPackets, excludedPackets, closeOnSelect);
   }

   public void openS2C(Consumer<Class<? extends Packet<?>>> onSelect) {
      this.openS2C(onSelect, Set.of(), true);
   }

   public void openS2C(Consumer<Class<? extends Packet<?>>> onSelect, Collection<Class<? extends Packet<?>>> excludedPackets, boolean closeOnSelect) {
      this.openWith(onSelect, this.sortedS2cPackets, excludedPackets, closeOnSelect);
   }

   public void openAny(Consumer<Class<? extends Packet<?>>> onSelect, Collection<Class<? extends Packet<?>>> excludedPackets, boolean closeOnSelect) {
      this.openWith(onSelect, this.sortedAnyPackets, excludedPackets, closeOnSelect);
   }

   public void openToggleC2S(BiConsumer<Class<? extends Packet<?>>, Boolean> onToggleSelect, Collection<Class<? extends Packet<?>>> selectedPackets) {
      this.openToggleWith(onToggleSelect, this.sortedC2sPackets, selectedPackets, Set.of());
   }

   public void openToggleC2S(
      BiConsumer<Class<? extends Packet<?>>, Boolean> onToggleSelect,
      Collection<Class<? extends Packet<?>>> selectedPackets,
      Collection<Class<? extends Packet<?>>> excludedPackets
   ) {
      this.openToggleWith(onToggleSelect, this.sortedC2sPackets, selectedPackets, excludedPackets);
   }

   public void openToggleS2C(BiConsumer<Class<? extends Packet<?>>, Boolean> onToggleSelect, Collection<Class<? extends Packet<?>>> selectedPackets) {
      this.openToggleWith(onToggleSelect, this.sortedS2cPackets, selectedPackets, Set.of());
   }

   public void openToggleAny(BiConsumer<Class<? extends Packet<?>>, Boolean> onToggleSelect, Collection<Class<? extends Packet<?>>> selectedPackets) {
      this.openToggleWith(onToggleSelect, this.sortedAnyPackets, selectedPackets, Set.of());
   }

   private void openWith(
      Consumer<Class<? extends Packet<?>>> onSelect,
      List<Class<? extends Packet<?>>> pool,
      Collection<Class<? extends Packet<?>>> excludedPackets,
      boolean closeOnSelect
   ) {
      this.visible = true;
      this.collapsed = false;
      this.onSelect = onSelect;
      this.onToggleSelect = null;
      this.closeOnSelect = closeOnSelect;
      this.toggleMode = false;
      this.excludedPackets = (Set<Class<? extends Packet<?>>>)(excludedPackets == null ? Set.of() : new LinkedHashSet<>(excludedPackets));
      this.selectedPackets = Set.of();
      this.activePool = this.filterExcluded(pool);
      this.updateFilter("");
      this.finishOpen();
   }

   private void openToggleWith(
      BiConsumer<Class<? extends Packet<?>>, Boolean> onToggleSelect,
      List<Class<? extends Packet<?>>> pool,
      Collection<Class<? extends Packet<?>>> selectedPackets,
      Collection<Class<? extends Packet<?>>> excludedPackets
   ) {
      this.visible = true;
      this.collapsed = false;
      this.onSelect = null;
      this.onToggleSelect = onToggleSelect;
      this.closeOnSelect = false;
      this.toggleMode = true;
      this.excludedPackets = (Set<Class<? extends Packet<?>>>)(excludedPackets == null ? Set.of() : new LinkedHashSet<>(excludedPackets));
      this.selectedPackets = selectedPackets == null ? new LinkedHashSet<>() : new LinkedHashSet<>(selectedPackets);
      this.activePool = this.filterExcluded(pool);
      this.updateFilter("");
      this.finishOpen();
   }

   private void finishOpen() {
      this.rebuildUi();
      DirectRenderContext metrics = this.surface.measurementContext();
      if (metrics != null) {
         this.panelHeight = Math.round(this.windowNode.preferredHeight(metrics, this.panelWidth));
      }

      DirectViewport viewport = this.surface.viewport();
      this.panelX = Math.max(8, Math.round((viewport.uiWidth() - this.panelWidth) / 2.0F));
      this.panelY = Math.max(8, Math.round((viewport.uiHeight() - this.panelHeight) / 2.0F));
      RiptideWindowLayout clamped = this.clampToViewport(
         new RiptideWindowLayout(this.panelX, this.panelY, this.panelWidth, this.panelHeight, this.visible, this.collapsed)
      );
      this.panelX = clamped.x;
      this.panelY = clamped.y;
      this.panelWidth = clamped.width;
      this.panelHeight = clamped.height;
      this.searchField.setText("");
      this.searchField.setFocused(true);
      this.windowNode.syncShowBody(true);
      RiptideOverlayManager.get().bringToFrontParent(this);
   }

   public void close() {
      this.visible = false;
      this.collapsed = false;
      this.dragging = false;
      this.dragMoved = false;
      this.onSelect = null;
      this.onToggleSelect = null;
      this.excludedPackets = Set.of();
      this.selectedPackets = Set.of();
      this.closeOnSelect = true;
      this.toggleMode = false;
      this.surface.clearFocusedTextInputs();
      this.windowNode.syncShowBody(true);
   }

   @Override
   public boolean isVisible() {
      return this.visible;
   }

   private void updateFilter(String query) {
      List<Class<? extends Packet<?>>> pool = this.filterExcluded(this.activePool);
      this.selector.setItems(pool);
      this.selector.setQuery(query);
      this.filteredPackets = this.selector.items();
      this.contentHeight = this.filteredPackets.size() * this.rowHeight();
      this.rebuildUi();
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      if (this.visible && MC != null && MC.font != null) {
         if (this.uiDirty) {
            this.rebuildUi();
         }

         context.nextStratum();
         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mouseX);
         float uiMouseY = viewport.toUiY(mouseY);
         boolean headerHovered = this.isOverHeaderUi(uiMouseX, uiMouseY);
         DirectRenderContext metrics = new DirectRenderContext(context, MC.font, viewport, this.theme, uiMouseX, uiMouseY, delta);
         this.windowNode.setShowBody(!this.collapsed);
         this.windowNode.setActive(true);
         this.windowNode.setHeaderHovered(headerHovered);
         this.panelHeight = Math.round(this.windowNode.preferredHeight(metrics, this.panelWidth));
         this.panelHeight = Math.max(this.theme.headerHeight(), this.panelHeight);
         RiptideWindowLayout clamped = this.clampToViewport(
            new RiptideWindowLayout(this.panelX, this.panelY, this.panelWidth, this.panelHeight, this.visible, this.collapsed)
         );
         this.panelX = clamped.x;
         this.panelY = clamped.y;
         this.panelWidth = clamped.width;
         this.panelHeight = clamped.height;
         this.windowNode.setBounds(this.panelX, this.panelY, this.panelWidth, this.panelHeight);
         this.surface.render(context, mouseX, mouseY, delta);
         context.nextStratum();
         if (!this.collapsed) {
            this.renderlistViewport(context, viewport, uiMouseX, uiMouseY, delta);
         }
      }
   }

   private void renderlistViewport(GuiGraphicsExtractor context, DirectViewport viewport, float uiMouseX, float uiMouseY, float delta) {
      int viewX = Math.round(this.listSlot.x());
      int viewY = Math.round(this.listSlot.y());
      int viewW = Math.round(this.listSlot.width());
      int viewH = Math.round(this.listSlot.height());
      if (viewW > 2 && viewH > 2) {
         int contentHeight = this.filteredPackets.size() * this.rowHeight();
         if (this.listViewport == null
            || this.listViewport.getX() != viewX
            || this.listViewport.getY() != viewY
            || this.listViewport.getWidth() != viewW
            || this.listViewport.getHeight() != viewH) {
            int preservedScroll = this.listViewport == null ? 0 : this.listViewport.getScrollOffset();
            this.listViewport = new DirectScrollViewport(viewX, viewY, viewW, viewH, this.rowHeight(), 6);
            this.listViewport.setContentHeight(contentHeight);
            this.listViewport.jumpTo(preservedScroll);
         }

         this.listViewport.setContentHeight(contentHeight);
         this.listViewport.beginRender(context, this.theme.borderSoft(), this.theme.listFill());

         try {
            this.listViewport.renderSimple(context, this.filteredPackets.size(), (idx, bnd) -> {
               Class<? extends Packet<?>> packetClass = this.filteredPackets.get(idx);
               this.renderPacketRowSimple(context, packetClass, bnd.x, bnd.y, bnd.width, idx);
            });
         } finally {
            this.listViewport.endRender(context);
         }

         this.renderScrollbar(context, viewX, viewY, viewW, viewH, uiMouseX, uiMouseY);
      }
   }

   private void renderPacketRowSimple(GuiGraphicsExtractor context, Class<? extends Packet<?>> packetClass, int x, int y, int width, int index) {
      boolean c2s = this.c2sPacketSet.contains(packetClass);
      boolean selected = this.toggleMode && this.selectedPackets.contains(packetClass);
      int rowColor = selected ? RiptideColors.packetRowSelectedBg(false) : RiptideColors.packetRowBg(c2s, index, false);
      int color = selected ? RiptideColors.packetRowSelectedText() : RiptideColors.packetRowText(c2s, index);
      CompactSurfaces.tintedRow(context, x, y, width, this.rowHeight(), rowColor);
      String name = RiptidePacketNamer.getFriendlyName(packetClass);
      int textWidth = width - 10;
      String trimmed = UiText.trimToWidth(this.textRenderer, name, textWidth, this.theme.fontFor(UiTone.BODY), color);
      int textY = UiSizing.alignTextY(y, this.rowHeight(), this.theme.fontHeight(UiTone.BODY), this.theme.bodyTextNudge());
      UiText.draw(context, this.textRenderer, trimmed, this.theme.fontFor(UiTone.BODY), color, x + 6, textY, false);
      CompactSurfaces.divider(context, x + 4, y + this.rowHeight() - 1, width - 8, RiptideColors.packetRowDivider());
   }

   private void renderScrollbar(GuiGraphicsExtractor context, int viewX, int viewY, int viewW, int viewH, float uiMouseX, float uiMouseY) {
      if (this.listViewport != null) {
         this.listViewport.renderScrollbar(context, uiMouseX, uiMouseY);
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.visible) {
         return false;
      } else {
         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mouseX);
         float uiMouseY = viewport.toUiY(mouseY);
         if (button == 0 && this.isOverCloseButton(uiMouseX, uiMouseY)) {
            this.close();
            return true;
         } else if (button == 0 && this.isOverHeaderUi(uiMouseX, uiMouseY)) {
            this.dragging = true;
            this.dragMoved = false;
            this.dragOffsetX = uiMouseX - this.panelX;
            this.dragOffsetY = uiMouseY - this.panelY;
            this.pressStartUiX = uiMouseX;
            this.pressStartUiY = uiMouseY;
            this.pressStartPanelX = this.panelX;
            this.pressStartPanelY = this.panelY;
            return true;
         } else if (!this.collapsed && this.surface.mouseClicked(mouseX, mouseY, button)) {
            return true;
         } else if (!this.collapsed
            && button == 0
            && this.uiContains(this.listSlot.x(), this.listSlot.y(), this.listSlot.width(), this.listSlot.height(), uiMouseX, uiMouseY)
            && this.listViewport != null
            && this.listViewport.mouseClicked(uiMouseX, uiMouseY, button)) {
            return true;
         } else {
            if (!this.collapsed
               && button == 0
               && this.uiContains(this.listSlot.x(), this.listSlot.y(), this.listSlot.width(), this.listSlot.height(), uiMouseX, uiMouseY)) {
               int index = (int)((uiMouseY - this.listSlot.y() + this.listViewport.getScrollOffset()) / this.rowHeight());
               if (index >= 0 && index < this.filteredPackets.size()) {
                  Class<? extends Packet<?>> selectedPacket = this.filteredPackets.get(index);
                  if (this.toggleMode) {
                     this.selectedPackets = new LinkedHashSet<>(this.selectedPackets);
                     boolean nowSelected;
                     if (this.selectedPackets.contains(selectedPacket)) {
                        this.selectedPackets.remove(selectedPacket);
                        nowSelected = false;
                     } else {
                        this.selectedPackets.add(selectedPacket);
                        nowSelected = true;
                     }

                     if (this.onToggleSelect != null) {
                        this.onToggleSelect.accept(selectedPacket, nowSelected);
                     }

                     this.rebuildUi();
                     this.searchField.setFocused(true);
                  } else {
                     if (this.onSelect != null) {
                        this.onSelect.accept(selectedPacket);
                     }

                     if (this.closeOnSelect) {
                        this.close();
                     } else {
                        this.excludedPackets = new LinkedHashSet<>(this.excludedPackets);
                        this.excludedPackets.add(selectedPacket);
                        this.activePool = this.filterExcluded(this.activePool);
                        this.updateFilter(this.searchField.text());
                        this.searchField.setFocused(true);
                     }
                  }

                  return true;
               }
            }

            if (!this.uiContains(this.panelX, this.panelY, this.panelWidth, this.panelHeight, uiMouseX, uiMouseY)) {
               this.surface.clearFocusedTextInputs();
               return true;
            } else {
               return true;
            }
         }
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      DirectViewport viewport = this.surface.viewport();
      float uiMouseX = viewport.toUiX(mouseX);
      float uiMouseY = viewport.toUiY(mouseY);
      if (button == 0 && this.dragging) {
         boolean moved = this.dragMoved
            || Math.abs(uiMouseX - this.pressStartUiX) >= 3.0F
            || Math.abs(uiMouseY - this.pressStartUiY) >= 3.0F
            || this.panelX != this.pressStartPanelX
            || this.panelY != this.pressStartPanelY;
         this.dragging = false;
         this.dragMoved = false;
         if (!moved && this.isOverHeaderUi(uiMouseX, uiMouseY) && !this.isOverCloseButton(uiMouseX, uiMouseY)) {
            this.setCollapsed(!this.collapsed);
         }

         return true;
      } else {
         if (button == 0 && this.listViewport != null) {
            this.listViewport.mouseReleased();
         }

         return !this.collapsed && this.surface.mouseReleased(mouseX, mouseY, button)
            ? true
            : this.visible && this.uiContains(this.panelX, this.panelY, this.panelWidth, this.panelHeight, uiMouseX, uiMouseY);
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (!this.visible) {
         return false;
      } else {
         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mouseX);
         float uiMouseY = viewport.toUiY(mouseY);
         if (this.dragging && button == 0) {
            int nextX = Math.round(uiMouseX - this.dragOffsetX);
            int nextY = Math.round(uiMouseY - this.dragOffsetY);
            if (nextX != this.panelX || nextY != this.panelY) {
               this.dragMoved = true;
            }

            RiptideWindowLayout clamped = this.clampToViewport(
               new RiptideWindowLayout(nextX, nextY, this.panelWidth, this.panelHeight, this.visible, this.collapsed)
            );
            this.panelX = clamped.x;
            this.panelY = clamped.y;
            return true;
         } else if (!this.collapsed
            && this.uiContains(this.listSlot.x(), this.listSlot.y(), this.listSlot.width(), this.listSlot.height(), uiMouseX, uiMouseY)
            && this.listViewport != null
            && this.listViewport.isScrollbarDragging()) {
            this.listViewport.mouseDragged(uiMouseX, uiMouseY);
            return true;
         } else {
            return !this.collapsed && this.surface.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
               ? true
               : this.visible && this.uiContains(this.panelX, this.panelY, this.panelWidth, this.panelHeight, uiMouseX, uiMouseY);
         }
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.visible) {
         return false;
      } else if (keyCode == 256) {
         if (this.searchField.isFocused()) {
            this.searchField.setFocused(false);
            return true;
         } else {
            this.close();
            return true;
         }
      } else if (this.collapsed) {
         return false;
      } else {
         this.surface.keyPressed(keyCode, scanCode, modifiers);
         return this.searchField.isFocused();
      }
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      return this.visible && !this.collapsed && this.surface.charTyped(chr, modifiers);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (!this.visible) {
         return false;
      } else {
         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mouseX);
         float uiMouseY = viewport.toUiY(mouseY);
         if (!this.collapsed
            && this.uiContains(this.listSlot.x(), this.listSlot.y(), this.listSlot.width(), this.listSlot.height(), uiMouseX, uiMouseY)
            && this.listViewport != null) {
            this.listViewport.mouseScrolled(uiMouseX, uiMouseY, amount);
            return true;
         } else {
            return this.searchField.isFocused() ? false : this.uiContains(this.panelX, this.panelY, this.panelWidth, this.panelHeight, uiMouseX, uiMouseY);
         }
      }
   }

   private List<Class<? extends Packet<?>>> filterExcluded(List<Class<? extends Packet<?>>> packets) {
      if (packets != null && !packets.isEmpty()) {
         if (this.excludedPackets.isEmpty()) {
            return new ArrayList<>(packets);
         } else {
            List<Class<? extends Packet<?>>> filtered = new ArrayList<>(packets.size());

            for (Class<? extends Packet<?>> packetClass : packets) {
               if (!this.excludedPackets.contains(packetClass)) {
                  filtered.add(packetClass);
               }
            }

            return filtered;
         }
      } else {
         return List.of();
      }
   }

   private static String searchKey(Class<? extends Packet<?>> packetClass) {
      return packetClass == null ? "" : (packetClass.getSimpleName() + "\n" + RiptidePacketNamer.getFriendlyName(packetClass)).toLowerCase(Locale.ROOT);
   }

   @Override
   public boolean hasTextFieldFocused() {
      return this.visible && this.surface.hasFocusedTextInput();
   }

   @Override
   public RiptideWindowLayout getBounds() {
      return new RiptideWindowLayout(this.panelX, this.panelY, this.panelWidth, this.panelHeight, this.visible, this.collapsed);
   }

   @Override
   public void setBounds(RiptideWindowLayout bounds) {
      if (bounds != null) {
         RiptideWindowLayout clamped = this.clampToViewport(bounds);
         this.panelX = clamped.x;
         this.panelY = clamped.y;
         this.panelWidth = clamped.width;
         this.panelHeight = clamped.height;
         this.visible = clamped.visible;
         this.collapsed = clamped.collapsed;
         this.windowNode.syncShowBody(!this.collapsed);
      }
   }

   @Override
   public boolean isCollapsed() {
      return this.collapsed;
   }

   @Override
   public void setCollapsed(boolean collapsed) {
      if (this.collapsed != collapsed) {
         this.collapsed = collapsed;
         this.dragging = false;
         this.dragMoved = false;
         this.windowNode.syncShowBody(!collapsed);
         if (collapsed) {
            this.clearHiddenInteractionState();
         }

         this.saveLayout();
      }
   }

   @Override
   public void clearTextFieldFocus() {
      this.surface.clearFocusedTextInputs();
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else {
         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mouseX);
         float uiMouseY = viewport.toUiY(mouseY);
         int renderedHeight = this.collapsed ? this.theme.headerHeight() : this.panelHeight;
         return this.uiContains(this.panelX, this.panelY, this.panelWidth, renderedHeight, uiMouseX, uiMouseY);
      }
   }

   @Override
   public boolean isOverDragBar(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else {
         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mouseX);
         float uiMouseY = viewport.toUiY(mouseY);
         return this.isOverHeaderUi(uiMouseX, uiMouseY) && !this.isOverCloseButton(uiMouseX, uiMouseY);
      }
   }

   @Override
   public int getMinWidth() {
      return this.panelMinimumWidth();
   }

   @Override
   public int getMinHeight() {
      return this.theme.headerHeight() + 20;
   }

   private boolean isOverHeaderUi(float uiMouseX, float uiMouseY) {
      return uiMouseX >= this.panelX
         && uiMouseX < this.panelX + this.panelWidth
         && uiMouseY >= this.panelY
         && uiMouseY < this.panelY + this.theme.headerHeight();
   }

   private boolean isOverCloseButton(float uiMouseX, float uiMouseY) {
      return OverlayTopBar.isOverClose(
         UiBounds.of(this.panelX, this.panelY, this.panelWidth, Math.max(this.theme.headerHeight(), this.panelHeight)),
         this.theme.headerHeight(),
         uiMouseX,
         uiMouseY
      );
   }

   private RiptideWindowLayout clampToViewport(RiptideWindowLayout bounds) {
      DirectViewport viewport = this.surface.viewport();
      int margin = 4;
      int viewportW = Math.round(viewport.uiWidth());
      int viewportH = Math.round(viewport.uiHeight());
      int availableW = Math.max(1, viewportW - margin * 2);
      int availableH = Math.max(this.theme.headerHeight(), viewportH - margin * 2);
      int width = Math.max(Math.min(this.panelMinimumWidth(), availableW), Math.min(bounds.width, availableW));
      int minHeight = bounds.collapsed ? this.theme.headerHeight() : this.theme.headerHeight() + 20;
      int height = Math.max(Math.min(minHeight, availableH), Math.min(bounds.height, availableH));
      int renderedHeight = bounds.collapsed ? this.theme.headerHeight() : height;
      int x = Math.max(margin, Math.min(bounds.x, Math.max(margin, viewportW - margin - width)));
      int y = Math.max(margin, Math.min(bounds.y, Math.max(margin, viewportH - margin - renderedHeight)));
      return new RiptideWindowLayout(x, y, width, height, bounds.visible, bounds.collapsed);
   }

   private boolean uiContains(float x, float y, float width, float height, float mouseX, float mouseY) {
      return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
   }

   private int panelMinimumWidth() {
      return 230;
   }

   private int rowHeight() {
      return 16;
   }

   private int maxVisibleRows() {
      return 12;
   }

   private int panelPadding() {
      return 6;
   }

   private int windowContentGap() {
      return 4;
   }

   private int searchFieldHeight() {
      return 16;
   }

   private int headerControlSize() {
      return 12;
   }

   private int headerArrowWidth() {
      return 10;
   }

   private int headerArrowGap() {
      return 3;
   }
}
