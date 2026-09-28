package riptide.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.protocol.Packet;
import riptide.gui.vanillaui.components.CompactListRenderer;
import riptide.gui.vanillaui.components.CompactListViewport;
import riptide.gui.vanillaui.components.CompactOverlayButton;
import riptide.gui.vanillaui.components.CompactOverlayControls;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactTextInput;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.ScrollState;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectRenderContext;
import riptide.gui.vanillaui.direct.DirectViewport;
import riptide.modules.RiptideModule;

public class RiptideCustomFilterOverlay extends RiptideOverlayBase {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final int DEFAULT_PANEL_WIDTH = 220;
   private static final int MIN_PANEL_WIDTH = 220;
   private static final int ACTION_HEIGHT = 13;
   private static final int ACTION_GAP = 2;
   private static final int SECTION_GAP = 6;
   private static final int SEARCH_HEIGHT = 16;
   private static final int FILTER_ROW_HEIGHT = 14;
   private static final int FILTER_FOOTER_HEIGHT = 12;
   private static final int FILTER_FOOTER_GAP = 4;
   private static final int FILTER_ROW_STEP = 15;
   private static final int SCROLLBAR_GUTTER = 8;
   private static final int SCROLLBAR_TRACK_WIDTH = 3;
   private static final int SCROLLBAR_NONE = 0;
   private static final int SCROLLBAR_C2S = 1;
   private static final int SCROLLBAR_S2C = 2;
   private final Font textRenderer;
   private final CompactTheme theme = new CompactTheme();
   private final CompactTextInput c2sSearchField;
   private final CompactTextInput s2cSearchField;
   private final RiptidePacketSelectorOverlay packetSelectorOverlay;
   private final RiptideCustomFilterPresetOverlay presetManagerOverlay;
   private final List<RiptideCustomFilterOverlay.ActionButton> buttons = new ArrayList<>();
   private final RiptideCustomFilterOverlay.PacketListState c2sListState = new RiptideCustomFilterOverlay.PacketListState();
   private final RiptideCustomFilterOverlay.PacketListState s2cListState = new RiptideCustomFilterOverlay.PacketListState();
   private int panelX = 260;
   private int panelY = 36;
   private int panelWidth = 220;
   private int panelHeight = 326;
   private boolean visible = false;
   private boolean collapsed = false;
   private boolean dragging = false;
   private double dragOffsetX = 0.0;
   private double dragOffsetY = 0.0;
   private int activeScrollbarDrag = 0;
   private int scrollbarGrabOffset = 0;

   public RiptideCustomFilterOverlay(Font textRenderer) {
      this.textRenderer = textRenderer;
      this.panelWidth = this.defaultPanelWidth();
      this.panelHeight = this.defaultPanelHeight();
      this.c2sSearchField = this.createField("Search C2S packets...");
      this.s2cSearchField = this.createField("Search S2C packets...");
      this.packetSelectorOverlay = new RiptidePacketSelectorOverlay(textRenderer);
      this.presetManagerOverlay = new RiptideCustomFilterPresetOverlay(textRenderer);
      this.c2sSearchField.setOnChange(text -> this.c2sListState.scroll.jumpTo(0, 0));
      this.s2cSearchField.setOnChange(text -> this.s2cListState.scroll.jumpTo(0, 0));
   }

   private CompactTextInput createField(String placeholder) {
      return new CompactTextInput()
         .setPlaceholder(placeholder)
         .setFieldHeight(this.searchHeight())
         .setMinWidth(120.0F)
         .setPreferredWidth(120.0F)
         .setTextTone(UiTone.BODY)
         .setPlaceholderTone(UiTone.MUTED);
   }

   public RiptideCustomFilterPresetOverlay getPresetManagerOverlay() {
      return this.presetManagerOverlay;
   }

   public RiptidePacketSelectorOverlay getPacketSelectorOverlay() {
      return this.packetSelectorOverlay;
   }

   @Override
   public String getOverlayId() {
      return "riptide-custom-filter";
   }

   @Override
   public int getMinWidth() {
      return this.defaultPanelWidth();
   }

   @Override
   public int getMinHeight() {
      return this.defaultPanelHeight();
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
         this.packetSelectorOverlay.close();
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
            this.packetSelectorOverlay.close();
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
      return this.packetSelectorOverlay.isVisible() || this.c2sSearchField.isFocused() || this.s2cSearchField.isFocused();
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
         this.renderWindowFrame(context, mouseX, mouseY, bounds, "Packet Delay", this.collapsed, this.dragging);
         boolean clipBody = this.beginWindowBodyClip(context, bounds, this.collapsed);
         if (!clipBody) {
            this.renderWindowInactiveOverlay(context, bounds, this.collapsed, this.dragging);
         } else {
            try {
               int x = this.panelX + this.panelInset();
               int y = this.panelY + 16 + this.topInset();
               int width = this.panelWidth - this.panelInset() * 2;
               RiptideModule module = RiptideModule.get();
               y = this.drawToggleAction(
                  context,
                  mouseX,
                  mouseY,
                  x,
                  y,
                  width,
                  "Custom Packets " + (module.shouldUseCustomPackets() ? "On" : "Off"),
                  () -> module.setUseCustomPackets(!module.shouldUseCustomPackets()),
                  module.shouldUseCustomPackets(),
                  "custom-filter:enabled"
               );
               RiptideText.draw(
                  context,
                  this.textRenderer,
                  "C2S " + module.getC2SPackets().size() + " | S2C " + module.getS2CPackets().size(),
                  RiptideText.Tone.MUTED,
                  x,
                  y,
                  false
               );
               y += this.infoLineHeight() + this.actionGap();
               y = this.drawAction(context, mouseX, mouseY, x, y, width, "Presets", () -> {
                  this.presetManagerOverlay.toggle();
                  RiptideOverlayManager.get().bringToFront(this.presetManagerOverlay);
               }, CompactOverlayButton.Variant.SECONDARY, true);
               y += this.sectionGap() - this.actionGap();
               int availableHeight = Math.max(this.sectionMinimumHeight() * 2 + this.sectionGap(), this.panelHeight - (y - this.panelY) - this.panelInset());
               int sectionHeight = Math.max(this.sectionMinimumHeight(), (availableHeight - this.sectionGap()) / 2);
               y = this.renderPacketSection(context, mouseX, mouseY, delta, x, y, width, sectionHeight, true, module, this.c2sSearchField, this.c2sListState);
               this.renderPacketSection(context, mouseX, mouseY, delta, x, y, width, sectionHeight, false, module, this.s2cSearchField, this.s2cListState);
            } finally {
               this.endWindowBodyClip(context, clipBody);
               this.renderWindowInactiveOverlay(context, bounds, this.collapsed, this.dragging);
            }

            if (this.packetSelectorOverlay.isVisible()) {
               this.packetSelectorOverlay.render(context, mouseX, mouseY, delta);
            }
         }
      }
   }

   private int renderPacketSection(
      GuiGraphicsExtractor context,
      int mouseX,
      int mouseY,
      float delta,
      int x,
      int y,
      int width,
      int sectionHeight,
      boolean c2s,
      RiptideModule module,
      CompactTextInput searchField,
      RiptideCustomFilterOverlay.PacketListState listState
   ) {
      String label = c2s ? "C2S Packets" : "S2C Packets";
      Set<Class<? extends Packet<?>>> selectedPackets = c2s ? module.getC2SPackets() : module.getS2CPackets();
      List<Class<? extends Packet<?>>> filteredPackets = this.getCachedPackets(listState, selectedPackets, searchField.text());
      CompactListRenderer.drawHeader(context, this.textRenderer, label + " (" + selectedPackets.size() + ")", x, y);
      int var26 = y + this.headerLabelHeight() + 2;
      searchField.setBounds(x, var26, width, this.searchHeight());
      searchField.render(this.renderContext(context, mouseX, mouseY, delta));
      int var27 = var26 + this.searchHeight() + this.actionGap();
      int var28 = this.drawAction(context, mouseX, mouseY, x, var27, width, c2s ? "Add C2S" : "Add S2C", () -> {
         if (c2s) {
            this.packetSelectorOverlay.openToggleC2S((packetClass, selected) -> this.setPacketSelected(true, packetClass, selected), module.getC2SPackets());
         } else {
            this.packetSelectorOverlay.openToggleS2C((packetClass, selected) -> this.setPacketSelected(false, packetClass, selected), module.getS2CPackets());
         }
      }, CompactOverlayButton.Variant.SECONDARY, true);
      int listHeight = Math.max(this.listMinimumHeight(), sectionHeight - (var28 - y) - this.footerHeight() - this.footerGap());
      CompactListViewport.Layout baseLayout = this.packetListLayout(x, var28, width, listHeight, filteredPackets.size(), 0);
      int visualScroll = listState.scroll.tick(delta, baseLayout.maxScroll());
      CompactListViewport.Layout listLayout = this.packetListLayout(x, var28, width, listHeight, filteredPackets.size(), visualScroll);
      boolean focused = RiptideOverlayManager.get().isFocusedOverlay(this) || RiptideOverlayManager.get().isTopOverlay(this);
      listLayout.drawFrame(context, focused);
      int contentWidth = Math.max(24, listLayout.contentWidth());
      listState.setBounds(x, var28, width, listHeight, listLayout.scrollbar());
      if (filteredPackets.isEmpty()) {
         CompactListRenderer.drawEmptyState(context, this.textRenderer, "No packets", x, var28, contentWidth);
      } else {
         listLayout.beginRows(context);

         try {
            listLayout.forEachVisibleRow(
               filteredPackets.size(),
               (index, rowY) -> {
                  Class<? extends Packet<?>> packetClass = filteredPackets.get(index);
                  boolean hovered = mouseX >= x + 2 && mouseX < x + 2 + contentWidth && mouseY >= rowY && mouseY < rowY + this.filterRowHeight();
                  String packetName = RiptidePacketNamer.getFriendlyName(packetClass);
                  CompactListRenderer.drawRow(
                     context,
                     this.textRenderer,
                     packetName,
                     x + 2,
                     rowY,
                     contentWidth,
                     this.filterRowHeight(),
                     hovered,
                     false,
                     c2s ? CompactListRenderer.RowTone.WARNING : CompactListRenderer.RowTone.NORMAL
                  );
                  int iconSize = this.filterRowHeight() - 2;
                  int iconX = x + 2 + contentWidth - iconSize - 1;
                  int iconY = rowY + 1;
                  CompactListRenderer.drawDeleteButton(context, iconX, iconY, iconSize, hovered);
                  CompactListRenderer.drawDivider(context, x + 2, rowY + this.filterRowHeight(), contentWidth);
                  this.buttons
                     .add(
                        new RiptideCustomFilterOverlay.ActionButton(
                           x + 2, rowY, contentWidth, this.filterRowHeight(), () -> this.removePacket(c2s, packetClass), true
                        )
                     );
               }
            );
         } finally {
            listLayout.endRows(context);
         }
      }

      listLayout.drawScrollbar(context, mouseX, mouseY, this.activeScrollbarDrag == (c2s ? 1 : 2));
      String footer = filteredPackets.isEmpty()
         ? ""
         : (
            listLayout.maxScroll() > 0
               ? "Scroll: "
                  + Math.min(filteredPackets.size(), Math.round((float)listState.scroll.targetOffset() / this.filterRowStep()) + 1)
                  + "/"
                  + Math.max(1, Math.round((float)listLayout.maxScroll() / this.filterRowStep()) + 1)
               : "Click a row to remove"
         );
      if (!footer.isEmpty()) {
         RiptideText.draw(context, this.textRenderer, footer, RiptideText.Tone.MUTED, x + 4, var28 + listHeight + this.footerGap(), false);
      }

      return y + sectionHeight + this.sectionGap();
   }

   private List<Class<? extends Packet<?>>> filterPackets(List<Class<? extends Packet<?>>> packets, String query) {
      if (query != null && !query.isBlank()) {
         String lowered = query.toLowerCase(Locale.ROOT);
         List<Class<? extends Packet<?>>> filtered = new ArrayList<>();

         for (Class<? extends Packet<?>> packetClass : packets) {
            String name = RiptidePacketNamer.getFriendlyName(packetClass).toLowerCase(Locale.ROOT);
            String fullName = packetClass.getName().toLowerCase(Locale.ROOT);
            if (name.contains(lowered) || fullName.contains(lowered)) {
               filtered.add(packetClass);
            }
         }

         return filtered;
      } else {
         return packets;
      }
   }

   private List<Class<? extends Packet<?>>> getCachedPackets(
      RiptideCustomFilterOverlay.PacketListState state, Set<Class<? extends Packet<?>>> packets, String query
   ) {
      String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
      int sourceHash = packets == null ? 0 : packets.hashCode();
      int sourceSize = packets == null ? 0 : packets.size();
      if (state.sourceHash == sourceHash && state.sourceSize == sourceSize && state.query.equals(normalizedQuery)) {
         return state.filteredPackets;
      } else {
         List<Class<? extends Packet<?>>> sorted = packets == null ? new ArrayList<>() : new ArrayList<>(packets);
         sorted.sort(Comparator.comparing(RiptidePacketNamer::getFriendlyName, String.CASE_INSENSITIVE_ORDER));
         state.filteredPackets = List.copyOf(this.filterPackets(sorted, normalizedQuery));
         state.sourceHash = sourceHash;
         state.sourceSize = sourceSize;
         state.query = normalizedQuery;
         return state.filteredPackets;
      }
   }

   private void addPacket(boolean c2s, Class<? extends Packet<?>> packetClass) {
      RiptideModule module = RiptideModule.get();
      Set<Class<? extends Packet<?>>> packets = new LinkedHashSet<>(c2s ? module.getC2SPackets() : module.getS2CPackets());
      if (packets.add(packetClass)) {
         if (c2s) {
            module.setC2SPackets(packets);
         } else {
            module.setS2CPackets(packets);
         }
      }
   }

   private void removePacket(boolean c2s, Class<? extends Packet<?>> packetClass) {
      RiptideModule module = RiptideModule.get();
      Set<Class<? extends Packet<?>>> packets = new LinkedHashSet<>(c2s ? module.getC2SPackets() : module.getS2CPackets());
      if (packets.remove(packetClass)) {
         if (c2s) {
            module.setC2SPackets(packets);
         } else {
            module.setS2CPackets(packets);
         }
      }
   }

   private void setPacketSelected(boolean c2s, Class<? extends Packet<?>> packetClass, boolean selected) {
      if (selected) {
         this.addPacket(c2s, packetClass);
      } else {
         this.removePacket(c2s, packetClass);
      }
   }

   private int drawAction(
      GuiGraphicsExtractor context,
      int mouseX,
      int mouseY,
      int x,
      int y,
      int width,
      String label,
      Runnable action,
      CompactOverlayButton.Variant variant,
      boolean enabled
   ) {
      CompactOverlayControls.action(
         context, this.textRenderer, x, y, width, this.actionHeight(), label, enabled ? variant : CompactOverlayButton.Variant.GHOST, enabled, mouseX, mouseY
      );
      this.buttons.add(new RiptideCustomFilterOverlay.ActionButton(x, y, width, this.actionHeight(), action, enabled));
      return y + this.actionHeight() + this.actionGap();
   }

   private int drawToggleAction(
      GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y, int width, String label, Runnable action, boolean enabled, String animationKey
   ) {
      CompactOverlayControls.toggle(context, this.textRenderer, x, y, width, this.actionHeight(), label, enabled, animationKey, mouseX, mouseY);
      this.buttons.add(new RiptideCustomFilterOverlay.ActionButton(x, y, width, this.actionHeight(), action, true));
      return y + this.actionHeight() + this.actionGap();
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.visible) {
         return false;
      } else if (this.packetSelectorOverlay.isVisible()) {
         return this.packetSelectorOverlay.mouseClicked(mouseX, mouseY, button);
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
            if (this.tryStartScrollbarDrag(this.c2sListState, 1, mouseX, mouseY)) {
               return true;
            }

            if (this.tryStartScrollbarDrag(this.s2cListState, 2, mouseX, mouseY)) {
               return true;
            }
         }

         if (this.handleTextFieldClick(this.c2sSearchField, mouseX, mouseY, button)) {
            this.c2sListState.scroll.jumpTo(0, 0);
            return true;
         } else if (this.handleTextFieldClick(this.s2cSearchField, mouseX, mouseY, button)) {
            this.s2cListState.scroll.jumpTo(0, 0);
            return true;
         } else {
            this.clearFocus();
            if (button == 0) {
               for (RiptideCustomFilterOverlay.ActionButton actionButton : this.buttons) {
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
      if (this.packetSelectorOverlay.isVisible() && this.packetSelectorOverlay.mouseReleased(mouseX, mouseY, button)) {
         return true;
      } else if (this.c2sSearchField.mouseReleased(this.inputContext(mouseX, mouseY), (float)mouseX, (float)mouseY, button)) {
         return true;
      } else if (this.s2cSearchField.mouseReleased(this.inputContext(mouseX, mouseY), (float)mouseX, (float)mouseY, button)) {
         return true;
      } else if (button == 0 && this.activeScrollbarDrag != 0) {
         this.activeScrollbarDrag = 0;
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
      if (this.packetSelectorOverlay.isVisible() && this.packetSelectorOverlay.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
         return true;
      } else if (this.c2sSearchField.mouseDragged(this.inputContext(mouseX, mouseY), (float)mouseX, (float)mouseY, button, (float)deltaX, (float)deltaY)) {
         return true;
      } else if (this.s2cSearchField.mouseDragged(this.inputContext(mouseX, mouseY), (float)mouseX, (float)mouseY, button, (float)deltaX, (float)deltaY)) {
         return true;
      } else if (this.activeScrollbarDrag == 1) {
         this.c2sListState.scroll.setFromThumbStepped(this.c2sListState.scrollbarMetrics, mouseY, this.scrollbarGrabOffset, this.filterRowStep());
         return true;
      } else if (this.activeScrollbarDrag == 2) {
         this.s2cListState.scroll.setFromThumbStepped(this.s2cListState.scrollbarMetrics, mouseY, this.scrollbarGrabOffset, this.filterRowStep());
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
      if (this.packetSelectorOverlay.isVisible()) {
         return this.packetSelectorOverlay.mouseScrolled(mouseX, mouseY, amount);
      } else if (this.c2sListState.contains(mouseX, mouseY)) {
         int maxScroll = this.packetListLayout(
               this.c2sListState.x, this.c2sListState.y, this.c2sListState.width, this.c2sListState.height, this.c2sListState.filteredPackets.size(), 0
            )
            .maxScroll();
         this.c2sListState.scroll.nudge(amount, this.filterRowStep(), maxScroll);
         return true;
      } else if (this.s2cListState.contains(mouseX, mouseY)) {
         int maxScroll = this.packetListLayout(
               this.s2cListState.x, this.s2cListState.y, this.s2cListState.width, this.s2cListState.height, this.s2cListState.filteredPackets.size(), 0
            )
            .maxScroll();
         this.s2cListState.scroll.nudge(amount, this.filterRowStep(), maxScroll);
         return true;
      } else {
         return false;
      }
   }

   private CompactListViewport.Layout packetListLayout(int x, int y, int width, int listHeight, int rowCount, int scrollOffset) {
      return CompactListViewport.layout(
         x, y, width, listHeight, rowCount, this.filterRowHeight(), this.filterRowStep(), scrollOffset, this.scrollbarTrackWidth(), this.scrollbarGutter()
      );
   }

   private boolean tryStartScrollbarDrag(RiptideCustomFilterOverlay.PacketListState listState, int dragId, double mouseX, double mouseY) {
      CompactScrollbar.Metrics metrics = listState.scrollbarMetrics;
      if (metrics == null || !metrics.hasScroll()) {
         return false;
      } else if (!metrics.contains(mouseX, mouseY)) {
         return false;
      } else {
         this.activeScrollbarDrag = dragId;
         this.scrollbarGrabOffset = Math.max(0, (int)mouseY - metrics.thumbY());
         listState.scroll.setFromThumbStepped(metrics, mouseY, this.scrollbarGrabOffset, this.filterRowStep());
         return true;
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.packetSelectorOverlay.isVisible()) {
         return this.packetSelectorOverlay.keyPressed(keyCode, scanCode, modifiers);
      } else if (!this.visible) {
         return false;
      } else if (keyCode == 256) {
         this.clearFocus();
         return false;
      } else {
         CompactTextInput focusedField = this.getFocusedField();
         if (focusedField == null) {
            return false;
         } else if (keyCode != 257 && keyCode != 335) {
            focusedField.keyPressed(this.inputContext(0.0, 0.0), keyCode, scanCode, modifiers);
            return true;
         } else {
            return true;
         }
      }
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      if (this.packetSelectorOverlay.isVisible()) {
         return this.packetSelectorOverlay.charTyped(chr, modifiers);
      } else if (!this.visible) {
         return false;
      } else {
         CompactTextInput focusedField = this.getFocusedField();
         return focusedField == null ? false : focusedField.charTyped(this.inputContext(0.0, 0.0), chr, modifiers);
      }
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else if (this.packetSelectorOverlay.isVisible()) {
         return true;
      } else {
         RiptideWindowLayout bounds = this.getBounds();
         int frameHeight = this.collapsed ? 16 : this.panelHeight;
         return mouseX >= bounds.x && mouseX <= bounds.x + bounds.width && mouseY >= bounds.y && mouseY <= bounds.y + frameHeight
            ? true
            : this.isMouseOverField(this.c2sSearchField, mouseX, mouseY) || this.isMouseOverField(this.s2cSearchField, mouseX, mouseY);
      }
   }

   private CompactTextInput getFocusedField() {
      if (this.c2sSearchField.isFocused()) {
         return this.c2sSearchField;
      } else {
         return this.s2cSearchField.isFocused() ? this.s2cSearchField : null;
      }
   }

   private int defaultPanelWidth() {
      return 220;
   }

   private int defaultPanelHeight() {
      return 308;
   }

   private int panelInset() {
      return 10;
   }

   private int topInset() {
      return 6;
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

   private int searchHeight() {
      return 16;
   }

   private int filterRowHeight() {
      return 14;
   }

   private int filterRowStep() {
      return this.filterRowHeight() + 1;
   }

   private int footerHeight() {
      return 12;
   }

   private int footerGap() {
      return 4;
   }

   private int scrollbarGutter() {
      return 8;
   }

   private int scrollbarTrackWidth() {
      return 3;
   }

   private int infoLineHeight() {
      return 10;
   }

   private int headerLabelHeight() {
      return 10;
   }

   private int sectionMinimumHeight() {
      return 86;
   }

   private int listMinimumHeight() {
      return 40;
   }

   private void clearFocus() {
      this.c2sSearchField.setFocused(false);
      this.s2cSearchField.setFocused(false);
   }

   private DirectRenderContext renderContext(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      DirectViewport viewport = DirectViewport.current(1.0F);
      return new DirectRenderContext(context, this.textRenderer, viewport, this.theme, viewport.toUiX(mouseX), viewport.toUiY(mouseY), delta);
   }

   private DirectRenderContext inputContext(double mouseX, double mouseY) {
      DirectViewport viewport = DirectViewport.current(1.0F);
      return new DirectRenderContext(null, this.textRenderer, viewport, this.theme, viewport.toUiX(mouseX), viewport.toUiY(mouseY), 0.0F);
   }

   private boolean isMouseOverField(CompactTextInput field, double mouseX, double mouseY) {
      return mouseX >= field.x() && mouseX <= field.x() + field.width() && mouseY >= field.y() && mouseY <= field.y() + field.height();
   }

   private record ActionButton(int x, int y, int width, int height, Runnable action, boolean enabled) {
      boolean contains(double mouseX, double mouseY) {
         return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
      }
   }

   private static final class PacketListState {
      private int x;
      private int y;
      private int width;
      private int height;
      private final ScrollState scroll = new ScrollState();
      private CompactScrollbar.Metrics scrollbarMetrics = null;
      private List<Class<? extends Packet<?>>> filteredPackets = List.of();
      private int sourceHash;
      private int sourceSize = -1;
      private String query = "";

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
   }
}
