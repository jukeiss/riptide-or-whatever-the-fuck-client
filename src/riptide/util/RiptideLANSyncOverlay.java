package riptide.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.components.CompactControlGlyphs;
import riptide.gui.vanillaui.components.CompactListRenderer;
import riptide.gui.vanillaui.components.CompactOverlayButton;
import riptide.gui.vanillaui.components.CompactOverlayControls;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactSurfaces;
import riptide.gui.vanillaui.components.CompactSymbolButton;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.ScrollState;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectLayout;
import riptide.gui.vanillaui.direct.DirectScrollList;
import riptide.gui.vanillaui.direct.DirectScrollViewport;
import riptide.modules.RiptideModule;
import riptide.util.macro.MacroAction;
import riptide.util.macro.MacroExecutor;

public class RiptideLANSyncOverlay extends RiptideOverlayBase {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final CompactTheme PACKUI_THEME = new CompactTheme();
   private static RiptideLANSyncOverlay sharedOverlay;
   private static final int CONTENT_SCROLLBAR_WIDTH = 4;
   private static final int CONTENT_SCROLLBAR_GUTTER = 12;
   private static final int SAME_MACRO_LIST_MAX_VISIBLE_ROWS = 6;
   private static final int SAME_MACRO_LIST_SCROLLBAR_WIDTH = 3;
   private int PANEL_BASE_WIDTH = 292;
   private int panelX = 500;
   private int panelY = 5;
   private int PANEL_WIDTH = this.PANEL_BASE_WIDTH;
   private int PANEL_HEIGHT = 220;
   private static final int HEADER_HEIGHT = 16;
   private int TAB_HEIGHT = 16;
   private int BUTTON_HEIGHT = 16;
   private int ROW_HEIGHT = 14;
   private int CONTENT_PADDING = 8;
   private int CHAT_FIELD_HEIGHT = 16;
   private int CHAT_AREA_HEIGHT = this.CHAT_FIELD_HEIGHT + 10;
   private boolean isDragging = false;
   private double dragOffsetX = 0.0;
   private double dragOffsetY = 0.0;
   private boolean collapsed = false;
   private boolean visible = false;
   private boolean configurationOnly = false;
   private final Font textRenderer;
   private static final String[] TAB_NAMES = new String[]{"Info", "Peers", "Macros", "Queue"};
   private int activeTab = 0;
   private int scrollOffset = 0;
   private int maxScroll = 0;
   private int renderScrollOffset = 0;
   private boolean scrollbarDragging = false;
   private int scrollbarGrabOffset = 0;
   private final ScrollState contentScrollState = new ScrollState();
   private DirectScrollList<RiptideMacro> sameMacroList = null;
   private int sameMacroListX = 0;
   private int sameMacroListY = 0;
   private int sameMacroListWidth = 0;
   private int sameMacroListHeight = 0;
   private DirectScrollViewport perUserlistViewport = null;
   private int perUserListX = 0;
   private int perUserListY = 0;
   private int perUserListWidth = 0;
   private int perUserListHeight = 0;
   private int tickCounter = 0;
   private static final int REFRESH_INTERVAL = 10;
   private String selectedMacroName = null;
   private boolean perUserMode = false;
   private final Map<String, String> perUserAssignments = new LinkedHashMap<>();
   private String expandedExecutePeer = null;
   private String selectedPeer = null;
   private final List<RiptideLANSyncOverlay.ClickRegion> clickRegions = new ArrayList<>();
   private RiptideChatField lanChatField;

   public RiptideLANSyncOverlay(Font textRenderer) {
      this.textRenderer = textRenderer;
      this.applyPresetMetrics();
      this.PANEL_WIDTH = this.PANEL_BASE_WIDTH;
      this.PANEL_HEIGHT = this.defaultPanelHeight();
      RiptideLANSync sync = RiptideLANSync.getInstance();
      sync.setOnClientJoined(() -> {});
      sync.setOnClientLeft(() -> {});
      sync.setOnSyncStateChanged(() -> {});
      sync.setOnSpreadCalculated(() -> {});
      sync.setOnPeerStatusChanged(() -> {});
   }

   public static synchronized RiptideLANSyncOverlay getSharedOverlay(Font textRenderer) {
      if (sharedOverlay == null) {
         sharedOverlay = new RiptideLANSyncOverlay(textRenderer);
         sharedOverlay.restoreState();
      }

      return sharedOverlay;
   }

   @Override
   public int getMinWidth() {
      return this.PANEL_BASE_WIDTH;
   }

   @Override
   public int getMinHeight() {
      return this.minimumPanelHeight();
   }

   @Override
   public RiptideWindowLayout getBounds() {
      return new RiptideWindowLayout(this.panelX, this.panelY, this.PANEL_WIDTH, this.getPanelHeight(), this.visible, this.collapsed);
   }

   @Override
   public void setBounds(RiptideWindowLayout bounds) {
      if (bounds != null) {
         RiptideWindowLayout clamped = this.clampToScreen(this, bounds);
         this.panelX = clamped.x;
         this.panelY = clamped.y;
         this.PANEL_WIDTH = clamped.width;
         this.PANEL_HEIGHT = clamped.height;
         this.visible = clamped.visible;
         this.collapsed = clamped.collapsed;
      }
   }

   public void toggle() {
      this.setVisible(!this.visible);
   }

   public void setConfigurationOnly(boolean configurationOnly) {
      this.configurationOnly = configurationOnly;
      RiptideLANSync.getInstance().setConfigurationOnly(configurationOnly);
      if (configurationOnly && this.lanChatField != null) {
         this.lanChatField.setFocused(false);
      }
   }

   @Override
   public void setVisible(boolean visible) {
      this.visible = visible;
      if (visible) {
         RiptideLANSync sync = RiptideLANSync.getInstance();
         RiptideModule module = RiptideModule.get();
         if ((this.configurationOnly || module.isActive() && module.isLANSyncEnabled()) && !sync.isRunning()) {
            sync.start();
         }

         RiptideOverlayManager.get().bringToFront(this);
      } else if (this.lanChatField != null) {
         this.lanChatField.setFocused(false);
      }

      this.saveState();
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

         this.saveState();
      }
   }

   @Override
   public boolean usesSharedHeaderClickCollapse() {
      return true;
   }

   @Override
   public boolean hasTextFieldFocused() {
      return this.lanChatField != null && this.lanChatField.isFocused();
   }

   @Override
   public void clearTextFieldFocus() {
      if (this.lanChatField != null && this.lanChatField.isFocused()) {
         this.lanChatField.setFocused(false);
      }
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else {
         int h = this.collapsed ? 16 : this.getPanelHeight();
         return mouseX >= this.panelX && mouseX <= this.panelX + this.PANEL_WIDTH && mouseY >= this.panelY && mouseY <= this.panelY + h;
      }
   }

   @Override
   public boolean isOverDragBar(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else {
         RiptideWindowLayout bounds = this.getBounds();
         return mouseX >= this.panelX
            && mouseX <= this.panelX + this.PANEL_WIDTH
            && mouseY >= this.panelY
            && mouseY <= this.panelY + 16
            && !this.isOverWindowControl(mouseX, mouseY, bounds);
      }
   }

   private int getPanelHeight() {
      RiptideLANSync sync = RiptideLANSync.getInstance();
      if (!sync.isRunning()) {
         return 56;
      } else if (!sync.isInSession()) {
         return sync.isSearching() ? 76 : 86;
      } else {
         int contentH = this.estimateTabContentHeight(sync);
         int total = 16 + this.TAB_HEIGHT + 2 + contentH + this.CHAT_AREA_HEIGHT + 8;
         return Math.max(140, Math.min(total, 500));
      }
   }

   private int estimateTabContentHeight(RiptideLANSync sync) {
      switch (this.activeTab) {
         case 0:
            int hx = 70;
            if (sync.getLastSpreadResult() != null) {
               hx += 12;
            }

            if (!sync.getSpreadHistory().isEmpty()) {
               hx += 14;
            }

            return hx + 4 + this.BUTTON_HEIGHT + 4;
         case 1:
            int peerCount = sync.getConnectedCount();
            int hx = 42 + peerCount * (this.ROW_HEIGHT + 1);
            if (this.selectedPeer != null) {
               hx += 64;
            }

            return Math.max(hx + 4, 60);
         case 2:
            int h = 4 + this.BUTTON_HEIGHT + 6;
            if (this.perUserMode) {
               int listContentHeight = this.estimatePerUserListContentHeight(sync);
               h += 14 + this.cappedlistViewportHeight(listContentHeight);
               h += 18 + this.BUTTON_HEIGHT + 6;
            } else {
               int macros = RiptideMacroManager.get().getAll().size();
               h += 14 + (macros == 0 ? 12 : this.macrolistViewportHeight(macros)) + 8;
               if (this.selectedMacroName != null) {
                  h += 48;
               }

               h += this.BUTTON_HEIGHT + 6;
            }

            if (MacroExecutor.isVisibleRunning()) {
               h += this.BUTTON_HEIGHT + 6;
            }

            return h + 35;
         case 3:
            return 16 + this.BUTTON_HEIGHT + 4 + 12 + 12 + 16 + this.BUTTON_HEIGHT + 8 + this.BUTTON_HEIGHT + 8 + this.BUTTON_HEIGHT + 4;
         default:
            return 120;
      }
   }

   public void saveState() {
      RiptideSharedState shared = RiptideSharedState.get();
      shared.setLanSyncOverlayVisible(this.visible);
      shared.setLanSyncOverlayX(this.panelX);
      shared.setLanSyncOverlayY(this.panelY);
      shared.setLanSyncOverlayActiveTab(this.activeTab);
      shared.setLanSyncOverlayScrollOffset(this.scrollOffset);
      shared.setLanSyncOverlayPerUserMode(this.perUserMode);
      shared.setLanSyncOverlaySelectedMacroName(this.selectedMacroName);
      shared.setLanSyncOverlayExpandedExecutePeer(this.expandedExecutePeer);
      shared.setLanSyncOverlaySelectedPeer(this.selectedPeer);
      this.saveLayout();
   }

   public void restoreState() {
      RiptideSharedState shared = RiptideSharedState.get();
      this.restoreLayout();
      this.visible = shared.isLanSyncOverlayVisible();
      this.panelX = shared.getLanSyncOverlayX();
      this.panelY = shared.getLanSyncOverlayY();
      this.activeTab = Math.max(0, Math.min(TAB_NAMES.length - 1, shared.getLanSyncOverlayActiveTab()));
      this.scrollOffset = Math.max(0, shared.getLanSyncOverlayScrollOffset());
      this.contentScrollState.restore(this.scrollOffset);
      this.perUserMode = shared.isLanSyncOverlayPerUserMode();
      this.selectedMacroName = emptyToNull(shared.getLanSyncOverlaySelectedMacroName());
      this.expandedExecutePeer = emptyToNull(shared.getLanSyncOverlayExpandedExecutePeer());
      this.selectedPeer = emptyToNull(shared.getLanSyncOverlaySelectedPeer());
      this.PANEL_WIDTH = this.PANEL_BASE_WIDTH;
   }

   private void clampScrollOffset() {
      this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, this.maxScroll));
   }

   private CompactScrollbar.Metrics getContentScrollbarMetrics(int contentY, int contentHeight) {
      int totalContentHeight = this.maxScroll + contentHeight;
      return CompactScrollbar.compute(
         totalContentHeight,
         contentHeight,
         this.panelX + this.PANEL_WIDTH - 6,
         contentY,
         4,
         contentHeight,
         this.contentScrollState.tick(0.0F, Math.max(0, totalContentHeight - contentHeight))
      );
   }

   private boolean hasScrollableSessionContent(RiptideLANSync sync) {
      return sync.isRunning() && sync.isInSession() && !sync.isSearching();
   }

   private int getTabContentY() {
      return this.panelY + 16 + this.TAB_HEIGHT + 2;
   }

   private int getTabContentHeight(int panelHeight) {
      int chatTopY = this.panelY + panelHeight - this.CHAT_AREA_HEIGHT;
      return Math.max(40, chatTopY - 4 - this.getTabContentY());
   }

   private int getContentWidth() {
      return DirectLayout.contentWidth(this.PANEL_WIDTH, this.CONTENT_PADDING);
   }

   private int getListWidth(boolean hasScroll) {
      return DirectLayout.reserveScrollbar(this.getContentWidth(), hasScroll, 12);
   }

   private int sameMacroListRowPitch() {
      return this.ROW_HEIGHT + 1;
   }

   private int macrolistViewportHeight(int macroCount) {
      int visibleRows = Math.max(1, Math.min(6, macroCount));
      return Math.max(this.sameMacroListRowPitch(), visibleRows * this.sameMacroListRowPitch());
   }

   private int macroListContentHeight(int macroCount) {
      return macroCount <= 0 ? 0 : macroCount * this.sameMacroListRowPitch();
   }

   private int cappedlistViewportHeight(int contentHeight) {
      int maxVisibleRows = Math.max(1, 6);
      int maxHeight = maxVisibleRows * this.sameMacroListRowPitch();
      int clampedHeight = Math.max(this.sameMacroListRowPitch(), Math.min(contentHeight, maxHeight));
      return (clampedHeight + this.sameMacroListRowPitch() - 1) / this.sameMacroListRowPitch() * this.sameMacroListRowPitch();
   }

   private void clearSameMacrolistViewport() {
      this.sameMacroList = null;
      this.sameMacroListX = 0;
      this.sameMacroListY = 0;
      this.sameMacroListWidth = 0;
      this.sameMacroListHeight = 0;
   }

   private boolean hasSameMacrolistViewport() {
      return this.sameMacroList != null && this.sameMacroListWidth > 0 && this.sameMacroListHeight > 0;
   }

   private boolean isOverSameMacroList(double mouseX, double mouseY) {
      return this.hasSameMacrolistViewport()
         && mouseX >= this.sameMacroListX
         && mouseX < this.sameMacroListX + this.sameMacroListWidth
         && mouseY >= this.sameMacroListY
         && mouseY < this.sameMacroListY + this.sameMacroListHeight;
   }

   private void clearperUserlistViewport() {
      this.perUserlistViewport = null;
      this.perUserListX = 0;
      this.perUserListY = 0;
      this.perUserListWidth = 0;
      this.perUserListHeight = 0;
   }

   private boolean hasperUserlistViewport() {
      return this.perUserlistViewport != null && this.perUserListWidth > 0 && this.perUserListHeight > 0;
   }

   private boolean isOverPerUserList(double mouseX, double mouseY) {
      return this.hasperUserlistViewport()
         && mouseX >= this.perUserListX
         && mouseX < this.perUserListX + this.perUserListWidth
         && mouseY >= this.perUserListY
         && mouseY < this.perUserListY + this.perUserListHeight;
   }

   private void syncPerUserAssignments(RiptideLANSync sync) {
      Set<String> connectedPeers = sync.getConnectedClients().keySet();

      for (String peer : connectedPeers) {
         this.perUserAssignments.putIfAbsent(peer, "");
      }

      this.perUserAssignments.keySet().retainAll(connectedPeers);
      Map<String, String> synced = sync.getSyncedAssignments();

      for (Entry<String, String> se : synced.entrySet()) {
         if (connectedPeers.contains(se.getKey())) {
            this.perUserAssignments.put(se.getKey(), se.getValue());
         }
      }
   }

   private int estimatePerUserListContentHeight(RiptideLANSync sync) {
      this.syncPerUserAssignments(sync);
      int contentHeight = 0;

      for (Entry<String, String> entry : new ArrayList<>(this.perUserAssignments.entrySet())) {
         String peer = entry.getKey();
         contentHeight += this.sameMacroListRowPitch();
         if (peer.equals(this.expandedExecutePeer)) {
            contentHeight += this.estimateExpandedPeerPickerHeight(sync, peer) + 4;
         }
      }

      return Math.max(this.sameMacroListRowPitch(), contentHeight);
   }

   private int estimateExpandedPeerPickerHeight(RiptideLANSync sync, String peer) {
      int contentHeight = 0;
      List<RiptideMacro> myMacroList = RiptideMacroManager.get().getAll();
      Set<String> myMacroKeys = new LinkedHashSet<>();

      for (RiptideMacro macro : myMacroList) {
         myMacroKeys.add(MacroNames.key(macro.name));
      }

      if (peer.equals(sync.getMyUsername())) {
         return myMacroList.size() * this.sameMacroListRowPitch();
      } else {
         Map<String, Map<String, RiptideMacro>> allRemote = sync.getAllRemoteMacros();
         Map<String, RiptideMacro> peerMacroData = allRemote.getOrDefault(peer, Collections.emptyMap());
         Set<String> peerMacroKeys = new LinkedHashSet<>();

         for (RiptideMacro macro : peerMacroData.values()) {
            if (macro != null) {
               peerMacroKeys.add(MacroNames.key(macro.name));
            }
         }

         List<String> shared = new ArrayList<>();
         Map<String, Integer> different = new LinkedHashMap<>();
         List<String> peerOnly = new ArrayList<>();
         List<String> mineOnly = new ArrayList<>();

         for (String key : peerMacroKeys) {
            if (myMacroKeys.contains(key)) {
               int diffs = countActionDifferences(RiptideMacroManager.get().get(key), peerMacroData.get(key));
               if (diffs == 0) {
                  shared.add(key);
               } else {
                  different.put(key, diffs);
               }
            } else {
               peerOnly.add(key);
            }
         }

         for (String keyx : myMacroKeys) {
            if (!peerMacroKeys.contains(keyx)) {
               mineOnly.add(keyx);
            }
         }

         int rowPitch = this.sameMacroListRowPitch();
         if (!shared.isEmpty()) {
            contentHeight += shared.size() * rowPitch;
         }

         contentHeight += different.size() * rowPitch;
         if (!peerOnly.isEmpty()) {
            contentHeight += peerOnly.size() * rowPitch;
         }

         if (!mineOnly.isEmpty()) {
            contentHeight += mineOnly.size() * rowPitch;
         }

         if (shared.isEmpty() && different.isEmpty() && peerOnly.isEmpty() && mineOnly.isEmpty()) {
            contentHeight += rowPitch;
         }

         return Math.max(rowPitch, contentHeight);
      }
   }

   private void drawUiText(GuiGraphicsExtractor ctx, String text, UiTone tone, int color, int x, int y) {
      UiText.draw(ctx, this.textRenderer, text, PACKUI_THEME.fontFor(tone), color, x, y, false);
   }

   private void drawOverlayButton(
      GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, CompactOverlayButton.Variant variant, boolean enabled, int mx, int my
   ) {
      this.drawOverlayButton(ctx, x, y, w, h, label, variant, enabled, UiTone.BODY, mx, my);
   }

   private void drawOverlayButton(
      GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, CompactOverlayButton.Variant variant, boolean enabled, UiTone tone, int mx, int my
   ) {
      CompactOverlayControls.action(ctx, this.textRenderer, x, y, w, h, label, variant, enabled, tone, mx, my);
   }

   private static String emptyToNull(String value) {
      return value != null && !value.isBlank() ? value : null;
   }

   @Override
   public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      if (this.visible) {
         RiptideLANSync sync = RiptideLANSync.getInstance();
         RiptideModule module = RiptideModule.get();
         this.tickCounter++;
         if (this.tickCounter >= 10) {
            this.tickCounter = 0;
            if (!sync.isRunning() && (this.configurationOnly || module.isActive() && module.isLANSyncEnabled())) {
               sync.start();
            }
         }

         this.clickRegions.clear();
         int panelHeight = this.getPanelHeight();
         RiptideWindowLayout bounds = new RiptideWindowLayout(this.panelX, this.panelY, this.PANEL_WIDTH, panelHeight, this.visible, this.collapsed);
         this.renderWindowFrame(ctx, mouseX, mouseY, bounds, "LAN Sync", this.collapsed, this.isDragging);
         boolean clipBody = this.beginWindowBodyClip(ctx, bounds, this.collapsed);
         if (!clipBody) {
            this.renderWindowInactiveOverlay(ctx, bounds, this.collapsed, this.isDragging);
         } else {
            try {
               this.ensureLanChatField(panelHeight);
               int contentY = this.panelY + 16;
               int chatTopY = this.panelY + panelHeight - this.CHAT_AREA_HEIGHT;
               if (!sync.isRunning()) {
                  this.renderNotRunning(ctx, mouseX, mouseY, contentY);
               } else if (!sync.isInSession() && !sync.isSearching()) {
                  this.renderNoSession(ctx, mouseX, mouseY, contentY);
               } else if (sync.isSearching()) {
                  this.renderSearching(ctx, mouseX, mouseY, contentY);
               } else {
                  this.renderTabBar(ctx, mouseX, mouseY, contentY);
                  int tabContentY = this.getTabContentY();
                  int actualTabContentH = this.getTabContentHeight(panelHeight);
                  this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, this.maxScroll));
                  this.contentScrollState.setTarget(this.scrollOffset, this.maxScroll);
                  this.renderScrollOffset = this.contentScrollState.tick(delta, this.maxScroll);
                  this.maxScroll = 0;
                  UiScissorStack.global().push(ctx, UiBounds.of(this.panelX, tabContentY, this.PANEL_WIDTH, actualTabContentH));
                  switch (this.activeTab) {
                     case 0:
                        this.renderDashboard(ctx, mouseX, mouseY, tabContentY, actualTabContentH);
                        break;
                     case 1:
                        this.renderPeers(ctx, mouseX, mouseY, tabContentY, actualTabContentH);
                        break;
                     case 2:
                        this.renderExecute(ctx, mouseX, mouseY, tabContentY, actualTabContentH);
                        break;
                     case 3:
                        this.renderQueue(ctx, mouseX, mouseY, tabContentY, actualTabContentH);
                  }

                  UiScissorStack.global().pop(ctx);
                  CompactScrollbar.Metrics scrollbarMetrics = this.getContentScrollbarMetrics(tabContentY, actualTabContentH);
                  CompactScrollbar.draw(ctx, scrollbarMetrics, scrollbarMetrics.contains(mouseX, mouseY), this.scrollbarDragging);
               }

               if (sync.isRunning() && sync.isInSession()) {
                  this.renderLanChatField(ctx, mouseX, mouseY);
               }
            } finally {
               this.endWindowBodyClip(ctx, clipBody);
               this.renderWindowInactiveOverlay(ctx, bounds, this.collapsed, this.isDragging);
            }
         }
      }
   }

   private void renderNotRunning(GuiGraphicsExtractor ctx, int mx, int my, int y) {
      int lx = this.panelX + this.CONTENT_PADDING;
      this.drawUiText(ctx, "Not running", UiTone.LABEL, RiptideTheme.recolor(-43691, RiptideTheme.Channel.DANGER), lx, y + 8);
      this.drawUiText(ctx, "LAN Sync is currently stopped", UiTone.MUTED, RiptideColors.textSecondary(), lx, y + 22);
   }

   private void renderNoSession(GuiGraphicsExtractor ctx, int mx, int my, int y) {
      int lx = this.panelX + this.CONTENT_PADDING;
      this.drawUiText(ctx, "Quick Start", UiTone.LABEL, PACKUI_THEME.color(UiTone.BODY), lx, y + 6);
      this.drawUiText(ctx, "Host creates, others search & join", UiTone.MUTED, RiptideColors.textSecondary(), lx, y + 20);
      int btnY = y + 40;
      int halfW = (this.PANEL_WIDTH - 24) / 2;
      this.drawOverlayButton(ctx, this.panelX + 8, btnY, halfW, this.BUTTON_HEIGHT, "Create", CompactOverlayButton.Variant.SECONDARY, true, mx, my);
      this.clickRegions
         .add(new RiptideLANSyncOverlay.ClickRegion(this.panelX + 8, btnY, halfW, this.BUTTON_HEIGHT, () -> RiptideLANSync.getInstance().createSession()));
      this.drawOverlayButton(ctx, this.panelX + 12 + halfW, btnY, halfW, this.BUTTON_HEIGHT, "Search", CompactOverlayButton.Variant.SECONDARY, true, mx, my);
      this.clickRegions
         .add(
            new RiptideLANSyncOverlay.ClickRegion(
               this.panelX + 12 + halfW, btnY, halfW, this.BUTTON_HEIGHT, () -> RiptideLANSync.getInstance().startSearching()
            )
         );
   }

   private void renderSearching(GuiGraphicsExtractor ctx, int mx, int my, int y) {
      int secondsLeft = RiptideLANSync.getInstance().getSearchSecondsRemaining();
      String text = "Searching... " + secondsLeft + "s";
      int tw = UiText.width(this.textRenderer, text, PACKUI_THEME.fontFor(UiTone.LABEL), -22016);
      this.drawUiText(ctx, text, UiTone.LABEL, -22016, this.panelX + (this.PANEL_WIDTH - tw) / 2, y + 10);
      int btnY = y + 35;
      this.drawOverlayButton(
         ctx, this.panelX + 30, btnY, this.PANEL_WIDTH - 60, this.BUTTON_HEIGHT, "Cancel Search", CompactOverlayButton.Variant.SECONDARY, true, mx, my
      );
      this.clickRegions
         .add(
            new RiptideLANSyncOverlay.ClickRegion(
               this.panelX + 30, btnY, this.PANEL_WIDTH - 60, this.BUTTON_HEIGHT, () -> RiptideLANSync.getInstance().cancelSearch()
            )
         );
   }

   private void ensureLanChatField(int panelHeight) {
      if (this.lanChatField == null) {
         this.lanChatField = new RiptideChatField(
            MC, this.textRenderer, this.panelX + this.CONTENT_PADDING, this.panelY, this.PANEL_WIDTH - this.CONTENT_PADDING * 2, this.CHAT_FIELD_HEIGHT, true
         );
         this.lanChatField.setHistoryNavigationEnabled(true);
         this.lanChatField.setSubmitHandler(message -> RiptideLANSync.getInstance().sendChatMessage(message));
      }

      int fieldX = this.panelX + this.CONTENT_PADDING;
      int fieldY = this.panelY + panelHeight - this.CHAT_FIELD_HEIGHT - 6;
      int fieldWidth = this.PANEL_WIDTH - this.CONTENT_PADDING * 2;
      this.lanChatField.setX(fieldX);
      this.lanChatField.setY(fieldY);
      this.lanChatField.setWidth(fieldWidth);
      boolean enabled = !this.configurationOnly && RiptideLANSync.getInstance().isRunning() && RiptideLANSync.getInstance().isInSession();
      this.lanChatField.setEditable(enabled);
      this.lanChatField.setSubmitOnEnter(enabled);
      this.lanChatField
         .setPlaceholder(
            Component.literal(
               this.configurationOnly ? "Available after joining a world" : (enabled ? "Sync message to all peers..." : "Join or create a session first")
            )
         );
      if (!enabled) {
         this.lanChatField.setFocused(false);
      }
   }

   private void renderLanChatField(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
      if (this.lanChatField != null) {
         int labelY = this.lanChatField.getY() - 10;
         RiptideText.draw(ctx, this.textRenderer, "Sync Send", RiptideText.Tone.MUTED, this.lanChatField.getX(), labelY, false);
         this.lanChatField.render(ctx, mouseX, mouseY, 0.0F);
      }
   }

   private void renderTabBar(GuiGraphicsExtractor ctx, int mx, int my, int y) {
      int tabW = this.PANEL_WIDTH / TAB_NAMES.length;

      for (int i = 0; i < TAB_NAMES.length; i++) {
         int tx = this.panelX + i * tabW;
         CompactOverlayControls.tab(ctx, this.textRenderer, tx, y, tabW, this.TAB_HEIGHT, TAB_NAMES[i], i == this.activeTab, mx, my);
         int tabIdx = i;
         this.clickRegions.add(new RiptideLANSyncOverlay.ClickRegion(tx, y, tabW, this.TAB_HEIGHT, () -> {
            this.activeTab = tabIdx;
            this.scrollOffset = 0;
            this.contentScrollState.jumpTo(0, 0);
            this.clearSameMacrolistViewport();
            this.clearperUserlistViewport();
            this.saveState();
         }));
      }
   }

   private void renderDashboard(GuiGraphicsExtractor ctx, int mx, int my, int y, int h) {
      RiptideLANSync sync = RiptideLANSync.getInstance();
      int lx = this.panelX + this.CONTENT_PADDING;
      int cy = y + 4;
      this.drawUiText(ctx, "Session", UiTone.MUTED, RiptideColors.textSecondary(), lx, cy);
      this.drawUiText(ctx, sync.getSessionId(), UiTone.BODY, -1, lx + 50, cy);
      cy += 12;
      String role = sync.isHost() ? "Host" : "Client";
      int roleColor = sync.isHost() ? -22016 : -11141291;
      this.drawUiText(ctx, "Role", UiTone.MUTED, RiptideColors.textSecondary(), lx, cy);
      this.drawUiText(ctx, role, UiTone.BODY, roleColor, lx + 50, cy);
      cy += 12;
      this.drawUiText(ctx, "Peers", UiTone.MUTED, RiptideColors.textSecondary(), lx, cy);
      this.drawUiText(ctx, String.valueOf(sync.getConnectedCount()), UiTone.BODY, -1, lx + 50, cy);
      cy += 12;
      if (sync.isReconnecting()) {
         this.drawUiText(ctx, "Reconnecting...", UiTone.LABEL, -22016, lx, cy);
         cy += 12;
      }

      cy += 4;
      RiptideLANSync.SyncState state = sync.getSyncState();
      String stateStr = state.name();
      int stateColor = this.getSyncStateColor(state);
      this.drawUiText(ctx, "Sync", UiTone.MUTED, RiptideColors.textSecondary(), lx, cy);
      this.drawUiText(ctx, stateStr, UiTone.BODY, stateColor, lx + 50, cy);
      cy += 14;
      RiptideLANSync.SpreadResult spread = sync.getLastSpreadResult();
      if (spread != null) {
         String spreadStr = spread.executionMethod == RiptideLANSync.ExecutionMethod.TICK
            ? (spread.perfectTickSync ? "Tick perfect" : spread.maxTickOffset + " tick off")
            : String.format("%.1fms", spread.getSpreadMs());
         int spreadColor = this.getSpreadColor(spread.getSpreadMs());
         this.drawUiText(ctx, "Spread", UiTone.MUTED, RiptideColors.textSecondary(), lx, cy);
         this.drawUiText(ctx, spreadStr, UiTone.BODY, spreadColor, lx + 50, cy);
         if (spread.lateClient != null && !spread.lateClient.isEmpty() && spread.getSpreadMs() > 0.0) {
            this.drawUiText(
               ctx,
               " (" + spread.lateClient + ")",
               UiTone.MUTED,
               RiptideColors.textDim(),
               lx + 50 + UiText.width(this.textRenderer, spreadStr, PACKUI_THEME.fontFor(UiTone.BODY), spreadColor),
               cy
            );
         }

         cy += 12;
      }

      List<RiptideLANSync.SpreadResult> history = sync.getSpreadHistory();
      if (!history.isEmpty()) {
         this.drawUiText(ctx, "History", UiTone.MUTED, RiptideColors.textSecondary(), lx, cy);
         int dotX = lx + 52;

         for (RiptideLANSync.SpreadResult sr : history) {
            int dotColor = this.getSpreadColor(sr.getSpreadMs());
            CompactSurfaces.indicator(ctx, dotX, cy + 2, 6, 6, dotColor);
            dotX += 9;
         }

         cy += 14;
      }

      int btnY = y + h - this.BUTTON_HEIGHT - 4;
      this.drawOverlayButton(
         ctx,
         this.panelX + this.CONTENT_PADDING,
         btnY,
         this.PANEL_WIDTH - this.CONTENT_PADDING * 2,
         this.BUTTON_HEIGHT,
         "Leave Session",
         CompactOverlayButton.Variant.SECONDARY,
         true,
         mx,
         my
      );
      this.clickRegions
         .add(
            new RiptideLANSyncOverlay.ClickRegion(
               this.panelX + this.CONTENT_PADDING,
               btnY,
               this.PANEL_WIDTH - this.CONTENT_PADDING * 2,
               this.BUTTON_HEIGHT,
               () -> RiptideLANSync.getInstance().leaveSession()
            )
         );
   }

   private void renderPeers(GuiGraphicsExtractor ctx, int mx, int my, int y, int h) {
      RiptideLANSync sync = RiptideLANSync.getInstance();
      int lx = this.panelX + this.CONTENT_PADDING;
      int cy = y + 4;
      int rowWidth = this.getContentWidth() + 4;
      Map<String, RiptideLANSync.ClientInfo> clients = sync.getConnectedClients();
      List<RiptideLANSync.ClientInfo> sortedClients = new ArrayList<>(clients.values());
      sortedClients.sort((a, b) -> Boolean.compare(b.isHost, a.isHost));
      this.drawUiText(ctx, "Execution Method", UiTone.MUTED, RiptideColors.textSecondary(), lx, cy);
      cy += 12;
      int methodW = (rowWidth - 4) / 2;
      boolean instant = sync.getExecutionMethod() == RiptideLANSync.ExecutionMethod.INSTANT;
      boolean syncIdle = sync.getSyncState() == RiptideLANSync.SyncState.IDLE || sync.getSyncState() == RiptideLANSync.SyncState.DONE;
      this.drawOverlayButton(
         ctx,
         lx,
         cy,
         methodW,
         this.BUTTON_HEIGHT,
         "Instant",
         instant ? CompactOverlayButton.Variant.PRIMARY : CompactOverlayButton.Variant.SECONDARY,
         syncIdle,
         mx,
         my
      );
      if (syncIdle) {
         this.clickRegions
            .add(
               new RiptideLANSyncOverlay.ClickRegion(lx, cy, methodW, this.BUTTON_HEIGHT, () -> sync.setExecutionMethod(RiptideLANSync.ExecutionMethod.INSTANT))
            );
      }

      this.drawOverlayButton(
         ctx,
         lx + methodW + 4,
         cy,
         rowWidth - methodW - 4,
         this.BUTTON_HEIGHT,
         "Tick",
         !instant ? CompactOverlayButton.Variant.PRIMARY : CompactOverlayButton.Variant.SECONDARY,
         syncIdle,
         mx,
         my
      );
      if (syncIdle) {
         this.clickRegions
            .add(
               new RiptideLANSyncOverlay.ClickRegion(
                  lx + methodW + 4, cy, rowWidth - methodW - 4, this.BUTTON_HEIGHT, () -> sync.setExecutionMethod(RiptideLANSync.ExecutionMethod.TICK)
               )
            );
      }

      cy += this.BUTTON_HEIGHT + 4;
      String methodHint = instant ? "Current: immediate GO-sync" : "Current: executes +" + sync.getTickExecutionDelayTicks() + " server ticks";
      if (sync.isInSession() && !sync.isHost()) {
         methodHint = methodHint + " (host verified)";
      }

      this.drawUiText(ctx, methodHint, UiTone.MUTED, RiptideColors.textDim(), lx, cy);
      cy += 14;

      for (RiptideLANSync.ClientInfo client : sortedClients) {
         boolean isSelected = client.username.equals(this.selectedPeer);
         boolean isHov = this.isHovered(mx, my, lx - 2, cy, this.PANEL_WIDTH - this.CONTENT_PADDING * 2 + 4, this.ROW_HEIGHT);
         CompactListRenderer.drawRow(ctx, this.textRenderer, "", lx - 2, cy, rowWidth, this.ROW_HEIGHT, isHov, isSelected, CompactListRenderer.RowTone.NORMAL);
         CompactControlGlyphs.drawChevron(
            ctx,
            lx + 1,
            cy + 3,
            8,
            isSelected ? CompactControlGlyphs.ChevronDirection.DOWN : CompactControlGlyphs.ChevronDirection.RIGHT,
            isSelected ? -593937 : -1911596,
            RiptideTheme.recolor(-1204284392, RiptideTheme.Channel.OUTLINE),
            1.0F
         );
         RiptideLANSync.PeerStatus status = sync.getPeerStatus(client.username);
         int dotColor = status == RiptideLANSync.PeerStatus.HEALTHY ? -11141291 : (status == RiptideLANSync.PeerStatus.STALE ? -22016 : -7829368);
         CompactSurfaces.indicator(ctx, lx + 10, cy + 4, 5, 5, dotColor);
         boolean isSelf = client.username.equals(sync.getMyUsername());
         int nameColor = isHov ? -2245633 : (client.isHost ? -22016 : -1);
         int rightReserve = 4;
         if (isSelf) {
            rightReserve += UiText.width(this.textRenderer, " (YOU)", PACKUI_THEME.fontFor(UiTone.MUTED), RiptideColors.textSecondary()) + 2;
         }

         if (client.isHost) {
            rightReserve += UiText.width(this.textRenderer, " [HOST]", PACKUI_THEME.fontFor(UiTone.LABEL), -22016) + 2;
         }

         int offset = client.delayOffsetMs;
         if (offset > 0) {
            rightReserve += UiText.width(this.textRenderer, "+" + offset + "ms", PACKUI_THEME.fontFor(UiTone.BODY), -11149825) + 4;
         }

         int nameMaxW = Math.max(20, rowWidth - 18 - rightReserve);
         String displayName = RiptideText.trimToWidth(this.textRenderer, client.username, nameMaxW, RiptideText.Tone.BODY);
         UiText.draw(ctx, this.textRenderer, displayName, PACKUI_THEME.fontFor(UiTone.BODY), nameColor, lx + 18, cy + 3, false);
         int badgeX = lx + 18 + UiText.width(this.textRenderer, displayName, PACKUI_THEME.fontFor(UiTone.BODY), nameColor) + 4;
         if (isSelf) {
            this.drawUiText(ctx, "(YOU)", UiTone.MUTED, RiptideColors.textSecondary(), badgeX, cy + 3);
            badgeX += UiText.width(this.textRenderer, "(YOU)", PACKUI_THEME.fontFor(UiTone.MUTED), RiptideColors.textSecondary()) + 4;
         }

         if (client.isHost) {
            this.drawUiText(ctx, "[HOST]", UiTone.LABEL, -22016, badgeX, cy + 3);
         }

         if (offset > 0) {
            String offsetStr = "+" + offset + "ms";
            int ofw = UiText.width(this.textRenderer, offsetStr, PACKUI_THEME.fontFor(UiTone.BODY), -11149825);
            this.drawUiText(ctx, offsetStr, UiTone.BODY, -11149825, this.panelX + this.PANEL_WIDTH - this.CONTENT_PADDING - ofw, cy + 3);
         }

         String peerName = client.username;
         this.clickRegions.add(new RiptideLANSyncOverlay.ClickRegion(lx - 2, cy, rowWidth, this.ROW_HEIGHT, () -> {
            this.selectedPeer = peerName.equals(this.selectedPeer) ? null : peerName;
            this.saveState();
         }));
         cy += this.ROW_HEIGHT + 1;
         if (isSelected) {
            int detailH = 62;
            CompactSurfaces.insetPanel(ctx, lx + 4, cy, this.panelX + this.PANEL_WIDTH - this.CONTENT_PADDING - lx - 8, detailH, RiptideColors.secondary());
            int dx = lx + 10;
            int dy = cy + 4;
            this.drawUiText(ctx, "Status: " + status.name(), UiTone.BODY, status == RiptideLANSync.PeerStatus.HEALTHY ? -11141291 : -22016, dx, dy);
            dy += 12;
            this.drawUiText(ctx, isSelf ? "(This is you)" : "Connected peer", UiTone.MUTED, RiptideColors.textDim(), dx, dy);
            dy += 13;
            this.drawUiText(ctx, "Exec delay", UiTone.MUTED, RiptideColors.textSecondary(), dx, dy);
            dy += 11;
            int ctrlX = dx + 4;
            int btnS = 14;
            CompactSymbolButton.render(
               ctx, this.textRenderer, UiBounds.of(ctrlX, dy, btnS, btnS), "-", mx >= ctrlX && mx < ctrlX + btnS && my >= dy && my < dy + btnS, true, false
            );
            this.clickRegions.add(new RiptideLANSyncOverlay.ClickRegion(ctrlX, dy, btnS, btnS, () -> {
               int cur = sync.getPlayerDelayOffset(peerName);
               sync.setPlayerDelayOffset(peerName, Math.max(0, cur - 1));
            }));
            ctrlX += btnS + 3;
            String valStr = offset + "ms";
            int valW = UiText.width(this.textRenderer, valStr, PACKUI_THEME.fontFor(UiTone.BODY), -1) + 8;
            CompactSurfaces.valueField(ctx, ctrlX, dy, valW, btnS);
            this.drawUiText(ctx, valStr, UiTone.BODY, -1, ctrlX + 4, dy + 2);
            ctrlX += valW + 3;
            CompactSymbolButton.render(
               ctx, this.textRenderer, UiBounds.of(ctrlX, dy, btnS, btnS), "+", mx >= ctrlX && mx < ctrlX + btnS && my >= dy && my < dy + btnS, true, false
            );
            this.clickRegions.add(new RiptideLANSyncOverlay.ClickRegion(ctrlX, dy, btnS, btnS, () -> {
               int cur = sync.getPlayerDelayOffset(peerName);
               sync.setPlayerDelayOffset(peerName, cur + 1);
            }));
            ctrlX += btnS + 5;
            int resetW = UiText.width(this.textRenderer, "Reset", PACKUI_THEME.fontFor(UiTone.BODY), PACKUI_THEME.color(UiTone.BODY)) + 10;
            this.drawOverlayButton(ctx, ctrlX, dy, resetW, btnS, "Reset", CompactOverlayButton.Variant.SECONDARY, true, mx, my);
            this.clickRegions.add(new RiptideLANSyncOverlay.ClickRegion(ctrlX, dy, resetW, btnS, () -> sync.setPlayerDelayOffset(peerName, 0)));
            cy += detailH + 2;
         }
      }

      this.maxScroll = 0;
      this.clampScrollOffset();
   }

   private void renderQueue(GuiGraphicsExtractor ctx, int mx, int my, int y, int h) {
      RiptideLANSync sync = RiptideLANSync.getInstance();
      int lx = this.panelX + this.CONTENT_PADDING;
      int cy = y + 6;
      RiptideSharedState state = RiptideSharedState.get();
      int queueSize = state.getDelayedPackets() != null ? state.getDelayedPackets().size() : 0;
      int btnW = this.PANEL_WIDTH - this.CONTENT_PADDING * 2;
      this.drawUiText(ctx, "Execution Method", UiTone.MUTED, RiptideColors.textSecondary(), lx, cy);
      cy += 12;
      int methodW = (btnW - 4) / 2;
      boolean instant = sync.getExecutionMethod() == RiptideLANSync.ExecutionMethod.INSTANT;
      boolean syncIdle = sync.getSyncState() == RiptideLANSync.SyncState.IDLE || sync.getSyncState() == RiptideLANSync.SyncState.DONE;
      this.drawOverlayButton(
         ctx,
         lx,
         cy,
         methodW,
         this.BUTTON_HEIGHT,
         "Instant",
         instant ? CompactOverlayButton.Variant.PRIMARY : CompactOverlayButton.Variant.SECONDARY,
         syncIdle,
         mx,
         my
      );
      if (syncIdle) {
         this.clickRegions
            .add(
               new RiptideLANSyncOverlay.ClickRegion(lx, cy, methodW, this.BUTTON_HEIGHT, () -> sync.setExecutionMethod(RiptideLANSync.ExecutionMethod.INSTANT))
            );
      }

      this.drawOverlayButton(
         ctx,
         lx + methodW + 4,
         cy,
         btnW - methodW - 4,
         this.BUTTON_HEIGHT,
         "Tick",
         !instant ? CompactOverlayButton.Variant.PRIMARY : CompactOverlayButton.Variant.SECONDARY,
         syncIdle,
         mx,
         my
      );
      if (syncIdle) {
         this.clickRegions
            .add(
               new RiptideLANSyncOverlay.ClickRegion(
                  lx + methodW + 4, cy, btnW - methodW - 4, this.BUTTON_HEIGHT, () -> sync.setExecutionMethod(RiptideLANSync.ExecutionMethod.TICK)
               )
            );
      }

      cy += this.BUTTON_HEIGHT + 4;
      this.drawUiText(ctx, "My Queue", UiTone.LABEL, -1, lx, cy);
      cy += 12;
      this.drawUiText(ctx, queueSize + " packets queued", UiTone.BODY, queueSize > 0 ? -11141291 : RiptideColors.textDim(), lx, cy);
      cy += 16;
      boolean delayOn = state.shouldDelayGuiPackets();
      String delayLabel = delayOn ? "Sync Delay OFF" : "Sync Delay ON";
      this.drawOverlayButton(
         ctx,
         lx,
         cy,
         btnW,
         this.BUTTON_HEIGHT,
         this.configurationOnly ? "Delay: In Game" : delayLabel,
         delayOn ? CompactOverlayButton.Variant.DANGER : CompactOverlayButton.Variant.SUCCESS,
         syncIdle && !this.configurationOnly,
         mx,
         my
      );
      if (syncIdle && !this.configurationOnly) {
         this.clickRegions
            .add(
               new RiptideLANSyncOverlay.ClickRegion(
                  lx, cy, btnW, this.BUTTON_HEIGHT, () -> sync.setDelayPacketsSynchronized(!RiptideSharedState.get().shouldDelayGuiPackets())
               )
            );
      }

      cy += this.BUTTON_HEIGHT + 8;
      this.drawOverlayButton(ctx, lx, cy, btnW, this.BUTTON_HEIGHT, "Share Queue", CompactOverlayButton.Variant.SECONDARY, !this.configurationOnly, mx, my);
      if (!this.configurationOnly) {
         this.clickRegions.add(new RiptideLANSyncOverlay.ClickRegion(lx, cy, btnW, this.BUTTON_HEIGHT, () -> {
            try {
               String queueData = RiptideClipboardHelper.serializeQueueToBase64(RiptideSharedState.get().getDelayedPackets());
               sync.broadcastQueueSync(queueData);
               RiptideClientMessaging.sendPrefixed("§aQueue broadcasted to " + (sync.getConnectedCount() - 1) + " peers");
            } catch (Exception var2x) {
               RiptideClientMessaging.sendPrefixed("§cFailed to broadcast queue");
            }
         }));
      }

      cy += this.BUTTON_HEIGHT + 8;
      this.drawOverlayButton(
         ctx,
         lx,
         cy,
         btnW,
         this.BUTTON_HEIGHT,
         this.configurationOnly ? "Execute: In Game" : "Sync Execute",
         CompactOverlayButton.Variant.PRIMARY,
         syncIdle && !this.configurationOnly,
         mx,
         my
      );
      if (syncIdle && !this.configurationOnly) {
         this.clickRegions.add(new RiptideLANSyncOverlay.ClickRegion(lx, cy, btnW, this.BUTTON_HEIGHT, () -> sync.sendQueuedPackets()));
      }

      this.maxScroll = 0;
   }

   private void renderExecute(GuiGraphicsExtractor ctx, int mx, int my, int y, int h) {
      RiptideLANSync sync = RiptideLANSync.getInstance();
      int lx = this.panelX + this.CONTENT_PADDING;
      int cy = y + 4;
      int btnW = this.PANEL_WIDTH - this.CONTENT_PADDING * 2;
      String modeLabel = this.perUserMode ? "Mode: Per-User" : "Mode: Shared Macro";
      this.drawOverlayButton(ctx, lx, cy, btnW, this.BUTTON_HEIGHT, modeLabel, CompactOverlayButton.Variant.PRIMARY, true, mx, my);
      this.clickRegions.add(new RiptideLANSyncOverlay.ClickRegion(lx, cy, btnW, this.BUTTON_HEIGHT, () -> {
         this.perUserMode = !this.perUserMode;
         this.expandedExecutePeer = null;
         this.scrollOffset = 0;
         this.contentScrollState.jumpTo(0, 0);
         this.clearSameMacrolistViewport();
         this.clearperUserlistViewport();
         if (this.perUserMode) {
            this.perUserAssignments.clear();
            Map<String, String> synced = sync.getSyncedAssignments();

            for (String peer : sync.getConnectedClients().keySet()) {
               this.perUserAssignments.put(peer, synced.getOrDefault(peer, ""));
            }
         }

         this.saveState();
      }));
      cy += this.BUTTON_HEIGHT + 6;
      if (this.perUserMode) {
         this.clearSameMacrolistViewport();
         cy = this.renderExecutePerUser(ctx, mx, my, cy, lx, btnW, sync);
      } else {
         this.clearperUserlistViewport();
         cy = this.renderExecuteSameMacro(ctx, mx, my, cy, lx, btnW, sync);
      }

      boolean anyRunning = MacroExecutor.isVisibleRunning();
      if (anyRunning && !this.configurationOnly) {
         this.drawOverlayButton(ctx, lx, cy, btnW, this.BUTTON_HEIGHT, "Sync Stop All", CompactOverlayButton.Variant.DANGER, true, mx, my);
         this.clickRegions.add(new RiptideLANSyncOverlay.ClickRegion(lx, cy, btnW, this.BUTTON_HEIGHT, () -> sync.broadcastStopAll()));
         cy += this.BUTTON_HEIGHT + 6;
      }

      CompactSurfaces.divider(ctx, lx - 2, cy, this.panelX + this.PANEL_WIDTH - this.CONTENT_PADDING - lx + 4);
      cy += 6;
      RiptideLANSync.SyncState state = sync.getSyncState();
      String stateStr = this.formatSyncState(state, sync);
      int stateColor = this.getSyncStateColor(state);
      this.drawUiText(ctx, "Status: " + stateStr, UiTone.BODY, stateColor, lx, cy);
      cy += 14;
      String setBy = sync.getAssignmentSetBy();
      if (this.perUserMode && !setBy.isEmpty()) {
         this.drawUiText(ctx, "Assigned by: " + setBy, UiTone.MUTED, RiptideColors.textDim(), lx, cy);
         cy += 12;
      }

      RiptideLANSync.SpreadResult spread = sync.getLastSpreadResult();
      if (spread != null) {
         String spreadStr = spread.executionMethod == RiptideLANSync.ExecutionMethod.TICK
            ? (
               spread.perfectTickSync
                  ? "Last: perfect tick " + spread.targetTick + " (" + spread.clientCount + " clients)"
                  : "Last: " + spread.maxTickOffset + " tick off @ " + spread.targetTick + " (" + spread.clientCount + " clients)"
            )
            : String.format("Last spread: %.1fms (%d clients)", spread.getSpreadMs(), spread.clientCount);
         this.drawUiText(ctx, spreadStr, UiTone.BODY, this.getSpreadColor(spread.getSpreadMs()), lx, cy);
         cy += 14;
      }

      this.maxScroll = 0;
      this.clampScrollOffset();
   }

   private int renderExecuteSameMacro(GuiGraphicsExtractor ctx, int mx, int my, int cy, int lx, int btnW, RiptideLANSync sync) {
      CompactListRenderer.drawHeader(ctx, this.textRenderer, "Local Macros", lx, cy);
      cy += 14;
      List<RiptideMacro> localMacros = RiptideMacroManager.get().getAll();
      if (localMacros.isEmpty()) {
         this.clearSameMacrolistViewport();
         this.drawUiText(ctx, "No macros", UiTone.MUTED, RiptideColors.textDim(), lx, cy);
         cy += 12;
      } else {
         this.sameMacroListX = lx - 2;
         this.sameMacroListY = cy;
         this.sameMacroListWidth = this.getContentWidth() + 4;
         this.sameMacroListHeight = this.macrolistViewportHeight(localMacros.size());
         if (this.sameMacroList == null
            || this.sameMacroList.getX() != this.sameMacroListX
            || this.sameMacroList.getY() != this.sameMacroListY
            || this.sameMacroList.getWidth() != this.sameMacroListWidth
            || this.sameMacroList.getHeight() != this.sameMacroListHeight) {
            this.sameMacroList = new DirectScrollList<>(
               this.sameMacroListX,
               this.sameMacroListY,
               this.sameMacroListWidth,
               this.sameMacroListHeight,
               this.sameMacroListRowPitch(),
               3,
               macro -> macro.name,
               (macro, selected) -> {
                  if (selected) {
                     this.selectedMacroName = macro.name;
                  }
               }
            );
            this.sameMacroList.setSecondaryTextExtractor(macro -> {
               int actionCount = macro.actions != null ? macro.actions.size() : 0;
               return "(" + actionCount + ")";
            });
            this.sameMacroList.setSelectedIndex(-1);
         }

         this.sameMacroList.setItems(localMacros);

         for (int i = 0; i < localMacros.size(); i++) {
            if (localMacros.get(i).name.equals(this.selectedMacroName)) {
               this.sameMacroList.setSelectedIndex(i);
               break;
            }
         }

         this.sameMacroList.render(ctx, this.textRenderer, mx, my);
         cy += this.sameMacroListHeight;
      }

      cy += 8;
      RiptideLANSyncOverlay.MacroSyncStatus syncStatus = this.analyzeSameMacroStatus(sync, this.selectedMacroName);
      if (this.selectedMacroName != null) {
         String selectedLine = RiptideText.trimToWidth(this.textRenderer, "Selected: " + this.selectedMacroName, btnW, RiptideText.Tone.BODY);
         this.drawUiText(ctx, selectedLine, UiTone.BODY, -1, lx, cy);
         cy += 12;
         if (!syncStatus.missingPeers.isEmpty()) {
            String missingLine = RiptideText.trimToWidth(
               this.textRenderer, "Missing on: " + this.formatNameSummary(syncStatus.missingPeers), btnW, RiptideText.Tone.BODY
            );
            this.drawUiText(ctx, missingLine, UiTone.BODY, RiptideTheme.recolor(-38037, RiptideTheme.Channel.DANGER), lx, cy);
            cy += 12;
         }

         if (!syncStatus.differentPeers.isEmpty()) {
            String differentLine = RiptideText.trimToWidth(
               this.textRenderer, "Different on: " + this.formatDifferenceSummary(syncStatus.differentPeers), btnW, RiptideText.Tone.BODY
            );
            this.drawUiText(ctx, differentLine, UiTone.BODY, -14249, lx, cy);
            cy += 12;
         }

         if (syncStatus.missingPeers.isEmpty() && syncStatus.differentPeers.isEmpty()) {
            String readyLine = sync.getConnectedCount() > 1 ? "All peers have this macro" : "Ready to execute";
            this.drawUiText(ctx, readyLine, UiTone.BODY, -9837157, lx, cy);
            cy += 12;
         } else if (syncStatus.missingPeers.isEmpty()) {
            this.drawUiText(ctx, "Execution allowed in Same Macro mode", UiTone.BODY, -9837157, lx, cy);
            cy += 12;
         }
      }

      int shareW = Math.min(
         112, Math.max(94, UiText.width(this.textRenderer, "Send To Peers", PACKUI_THEME.fontFor(UiTone.BODY), RiptideColors.textPrimary()) + 12)
      );
      int executeX = lx + shareW + 4;
      int executeW = btnW - shareW - 4;
      boolean shareEnabled = this.selectedMacroName != null && sync.getConnectedCount() > 1;
      boolean executeEnabled = !this.configurationOnly && this.selectedMacroName != null && syncStatus.canExecute();
      if (shareEnabled) {
         this.drawOverlayButton(ctx, lx, cy, shareW, this.BUTTON_HEIGHT, "Send To Peers", CompactOverlayButton.Variant.SUCCESS, true, mx, my);
         this.clickRegions.add(new RiptideLANSyncOverlay.ClickRegion(lx, cy, shareW, this.BUTTON_HEIGHT, () -> {
            if (this.selectedMacroName != null) {
               sync.shareMacroWithPeers(this.selectedMacroName);
            }
         }));
      } else {
         this.drawOverlayButton(ctx, lx, cy, shareW, this.BUTTON_HEIGHT, "Send To Peers", CompactOverlayButton.Variant.SUCCESS, false, mx, my);
      }

      String executeLabel;
      if (this.selectedMacroName == null) {
         executeLabel = "Select a macro";
      } else if (this.configurationOnly) {
         executeLabel = "Execute: In Game";
      } else if (!executeEnabled) {
         executeLabel = "Need all peers to have it";
      } else {
         executeLabel = "Sync Execute";
      }

      if (executeEnabled) {
         this.drawOverlayButton(ctx, executeX, cy, executeW, this.BUTTON_HEIGHT, executeLabel, CompactOverlayButton.Variant.PRIMARY, true, mx, my);
         this.clickRegions.add(new RiptideLANSyncOverlay.ClickRegion(executeX, cy, executeW, this.BUTTON_HEIGHT, () -> {
            if (this.selectedMacroName != null) {
               sync.executeMacroSynchronized(this.selectedMacroName);
            }
         }));
      } else {
         this.drawOverlayButton(ctx, executeX, cy, executeW, this.BUTTON_HEIGHT, executeLabel, CompactOverlayButton.Variant.PRIMARY, false, mx, my);
      }

      return cy + this.BUTTON_HEIGHT + 6;
   }

   private int renderExecutePerUser(GuiGraphicsExtractor ctx, int mx, int my, int cy, int lx, int btnW, RiptideLANSync sync) {
      this.syncPerUserAssignments(sync);
      int rowW = this.getContentWidth() + 4;
      this.perUserListX = lx - 2;
      this.perUserListY = cy;
      this.perUserListWidth = rowW;
      int perUserListContentHeight = this.estimatePerUserListContentHeight(sync);
      this.perUserListHeight = this.cappedlistViewportHeight(perUserListContentHeight);
      if (this.perUserlistViewport == null
         || this.perUserlistViewport.getX() != this.perUserListX
         || this.perUserlistViewport.getY() != this.perUserListY
         || this.perUserlistViewport.getWidth() != this.perUserListWidth
         || this.perUserlistViewport.getHeight() != this.perUserListHeight) {
         this.perUserlistViewport = new DirectScrollViewport(
            this.perUserListX, this.perUserListY, this.perUserListWidth, this.perUserListHeight, this.sameMacroListRowPitch(), 3
         );
      }

      this.perUserlistViewport.setContentHeight(perUserListContentHeight);
      int drawScroll = this.perUserlistViewport.getScrollOffset();
      int clipTop = this.perUserListY + 1;
      int clipBottom = this.perUserListY + this.perUserListHeight - 1;
      int rowPitch = this.perUserlistViewport.getRowHeight();
      UiRenderer.frame(
         ctx,
         UiBounds.of(this.perUserListX, this.perUserListY, this.perUserListWidth, this.perUserListHeight),
         PACKUI_THEME.listFill(),
         PACKUI_THEME.borderSoft()
      );
      UiScissorStack.global()
         .push(ctx, UiBounds.of(this.perUserListX + 1, this.perUserListY + 1, Math.max(0, this.perUserListWidth - 2), Math.max(0, this.perUserListHeight - 2)));

      try {
         cy = this.perUserListY - drawScroll;

         for (Entry<String, String> entry : new ArrayList<>(this.perUserAssignments.entrySet())) {
            String peer = entry.getKey();
            String assigned = entry.getValue();
            boolean isExpanded = peer.equals(this.expandedExecutePeer);
            boolean isMe = peer.equals(sync.getMyUsername());
            boolean rowVisible = cy + rowPitch > clipTop && cy < clipBottom;
            boolean rowHov = rowVisible && this.isHovered(mx, my, lx - 2, cy, rowW, rowPitch);
            String peerLabel = peer + (isMe ? " (you)" : "");
            String assignLabel = assigned.isEmpty() ? "(none)" : assigned;
            int assignColor = assigned.isEmpty() ? RiptideColors.textDim() : -11141291;
            int maxAssignW = Math.min((rowW - 12) / 2, UiText.width(this.textRenderer, assignLabel, PACKUI_THEME.fontFor(UiTone.BODY), assignColor));
            int peerNameMaxW = Math.max(20, rowW - 10 - maxAssignW - 8 - 12);
            if (rowVisible) {
               int rowTop = Math.max(cy, clipTop);
               int rowBottom = Math.min(cy + rowPitch, clipBottom);
               if (rowBottom > rowTop) {
                  CompactSurfaces.row(ctx, this.perUserListX + 1, rowTop, this.perUserListWidth - 2, rowBottom - rowTop, rowHov, isExpanded);
                  int chevronY = Math.max(cy + 3, clipTop + 1);
                  if (chevronY < clipBottom - 10) {
                     CompactControlGlyphs.drawChevron(
                        ctx,
                        lx + 1,
                        chevronY,
                        8,
                        isExpanded ? CompactControlGlyphs.ChevronDirection.DOWN : CompactControlGlyphs.ChevronDirection.RIGHT,
                        isExpanded ? -593937 : -1911596,
                        RiptideTheme.recolor(-1204284392, RiptideTheme.Channel.OUTLINE),
                        1.0F
                     );
                  }

                  int textY = Math.max(cy + 3, clipTop + 1);
                  if (textY < clipBottom - 2) {
                     String truncPeerLabel = RiptideText.trimToWidth(this.textRenderer, peerLabel, peerNameMaxW, RiptideText.Tone.BODY);
                     this.drawUiText(ctx, truncPeerLabel, UiTone.BODY, isMe ? RiptideColors.textSecondary() : -1, lx + 10, textY);
                     String truncAssign = RiptideText.trimToWidth(this.textRenderer, assignLabel, maxAssignW, RiptideText.Tone.BODY);
                     int truncAssignW = UiText.width(this.textRenderer, truncAssign, PACKUI_THEME.fontFor(UiTone.BODY), assignColor);
                     this.drawUiText(ctx, truncAssign, UiTone.BODY, assignColor, DirectLayout.rightAlign(lx, rowW - 12, truncAssignW, 4), textY);
                  }
               }
            }

            String peerKey = peer;
            if (rowVisible) {
               this.clickRegions.add(new RiptideLANSyncOverlay.ClickRegion(lx - 2, cy, rowW, rowPitch, () -> {
                  this.expandedExecutePeer = peerKey.equals(this.expandedExecutePeer) ? null : peerKey;
                  this.saveState();
               }));
            }

            cy += rowPitch;
            if (isExpanded) {
               int detailLx = lx + 6;
               int detailRx = this.panelX + this.PANEL_WIDTH - this.CONTENT_PADDING - 4;
               List<RiptideMacro> myMacroList = RiptideMacroManager.get().getAll();
               Map<String, String> displayNames = new LinkedHashMap<>();
               Set<String> myMacroKeys = new LinkedHashSet<>();

               for (RiptideMacro m : myMacroList) {
                  String key = MacroNames.key(m.name);
                  myMacroKeys.add(key);
                  displayNames.put(key, m.name);
               }

               if (isMe) {
                  for (RiptideMacro macro : myMacroList) {
                     cy = this.renderPickerRowClipped(
                        ctx, mx, my, cy, detailLx, detailRx, rowW, macro.name, assigned, null, peerKey, sync, false, clipTop, clipBottom, rowPitch
                     );
                  }
               } else {
                  Map<String, Map<String, RiptideMacro>> allRemote = sync.getAllRemoteMacros();
                  Map<String, RiptideMacro> peerMacroData = allRemote.getOrDefault(peer, Collections.emptyMap());
                  Set<String> peerMacroKeys = new LinkedHashSet<>();

                  for (RiptideMacro m : peerMacroData.values()) {
                     if (m != null) {
                        String key = MacroNames.key(m.name);
                        peerMacroKeys.add(key);
                        displayNames.putIfAbsent(key, m.name);
                     }
                  }

                  List<String> shared = new ArrayList<>();
                  Map<String, Integer> different = new LinkedHashMap<>();
                  List<String> peerOnly = new ArrayList<>();
                  List<String> mineOnly = new ArrayList<>();

                  for (String key : peerMacroKeys) {
                     String name = displayNames.getOrDefault(key, key);
                     if (myMacroKeys.contains(key)) {
                        int diffs = countActionDifferences(RiptideMacroManager.get().get(key), peerMacroData.get(key));
                        if (diffs == 0) {
                           shared.add(name);
                        } else {
                           different.put(name, diffs);
                        }
                     } else {
                        peerOnly.add(name);
                     }
                  }

                  for (String keyx : myMacroKeys) {
                     if (!peerMacroKeys.contains(keyx)) {
                        mineOnly.add(displayNames.getOrDefault(keyx, keyx));
                     }
                  }

                  if (!shared.isEmpty()) {
                     for (String name : shared) {
                        cy = this.renderPickerRowClipped(
                           ctx, mx, my, cy, detailLx, detailRx, rowW, name, assigned, null, peerKey, sync, false, clipTop, clipBottom, rowPitch
                        );
                     }
                  }

                  if (!different.isEmpty()) {
                     for (Entry<String, Integer> de : different.entrySet()) {
                        String name = de.getKey();
                        int diff = de.getValue();
                        String badge = " +" + diff + " Difference" + (diff > 1 ? "s" : "");
                        cy = this.renderPickerRowClipped(
                           ctx, mx, my, cy, detailLx, detailRx, rowW, name, assigned, badge, peerKey, sync, false, clipTop, clipBottom, rowPitch
                        );
                     }
                  }

                  if (!peerOnly.isEmpty()) {
                     for (String name : peerOnly) {
                        cy = this.renderPickerRowClipped(
                           ctx, mx, my, cy, detailLx, detailRx, rowW, name, assigned, null, peerKey, sync, true, clipTop, clipBottom, rowPitch
                        );
                     }
                  }

                  if (!mineOnly.isEmpty()) {
                     for (String name : mineOnly) {
                        cy = this.renderPickerRowClipped(
                           ctx, mx, my, cy, detailLx, detailRx, rowW, name, assigned, null, peerKey, sync, false, clipTop, clipBottom, rowPitch
                        );
                     }
                  }

                  if (shared.isEmpty() && different.isEmpty() && peerOnly.isEmpty() && mineOnly.isEmpty()) {
                     if (cy + rowPitch > clipTop && cy < clipBottom) {
                        int rowTop = Math.max(cy, clipTop);
                        int rowBottom = Math.min(cy + rowPitch, clipBottom);
                        if (rowBottom > rowTop) {
                           CompactSurfaces.row(ctx, detailLx - 2, rowTop, detailRx - detailLx + 2, rowBottom - rowTop, true, false);
                           int textY = Math.max(cy + 3, clipTop + 1);
                           if (textY < clipBottom - 2) {
                              this.drawUiText(ctx, "No macros available", UiTone.MUTED, RiptideColors.textDim(), detailLx, textY);
                           }
                        }
                     }

                     cy += rowPitch;
                  }
               }

               cy += 4;
            }
         }
      } finally {
         UiScissorStack.global().pop(ctx);
      }

      this.perUserlistViewport.renderScrollbar(ctx, mx, my);
      cy = this.perUserListY + this.perUserListHeight;
      RiptideLANSyncOverlay.AssignmentSyncStatus var51 = this.analyzeAssignmentStatus(sync);
      LinkedHashSet var52 = this.getShareableMissingMacros(var51);
      cy += 6;
      if (!var51.missingAssignments.isEmpty()) {
         String missingLine = RiptideText.trimToWidth(
            this.textRenderer, "Missing: " + this.formatMissingAssignmentsSummary(var51.missingAssignments), btnW, RiptideText.Tone.BODY
         );
         this.drawUiText(ctx, missingLine, UiTone.BODY, RiptideTheme.recolor(-38037, RiptideTheme.Channel.DANGER), lx, cy);
         cy += 12;
      } else if (var51.hasAssignments()) {
         this.drawUiText(ctx, "Assigned macros are ready", UiTone.BODY, -9837157, lx, cy);
         cy += 12;
      }

      int shareW = Math.min(
         112, Math.max(96, UiText.width(this.textRenderer, "Send Missing", PACKUI_THEME.fontFor(UiTone.BODY), RiptideColors.textPrimary()) + 12)
      );
      int executeX = lx + shareW + 4;
      int executeW = btnW - shareW - 4;
      if (!var52.isEmpty()) {
         String sendLabel = var52.size() > 1 ? "Send Missing (" + var52.size() + ")" : "Send Missing";
         this.drawOverlayButton(ctx, lx, cy, shareW, this.BUTTON_HEIGHT, sendLabel, CompactOverlayButton.Variant.SUCCESS, true, mx, my);
         this.clickRegions.add(new RiptideLANSyncOverlay.ClickRegion(lx, cy, shareW, this.BUTTON_HEIGHT, () -> {
            for (String macroName : var52) {
               sync.shareMacroWithPeers(macroName);
            }
         }));
      } else {
         this.drawOverlayButton(ctx, lx, cy, shareW, this.BUTTON_HEIGHT, "Send Missing", CompactOverlayButton.Variant.SUCCESS, false, mx, my);
      }

      String puLabel;
      if (!var51.hasAssignments()) {
         puLabel = "Assign macros to users above";
      } else if (!var51.canExecute()) {
         puLabel = "Need missing macros first";
      } else {
         puLabel = "Sync Execute (Per-User)";
      }

      if (var51.canExecute() && !this.configurationOnly) {
         this.drawOverlayButton(ctx, executeX, cy, executeW, this.BUTTON_HEIGHT, puLabel, CompactOverlayButton.Variant.PRIMARY, true, mx, my);
         this.clickRegions.add(new RiptideLANSyncOverlay.ClickRegion(executeX, cy, executeW, this.BUTTON_HEIGHT, () -> {
            if (!var51.assignments.isEmpty()) {
               sync.executeMacrosSynchronized(var51.assignments);
            }
         }));
      } else {
         this.drawOverlayButton(
            ctx,
            executeX,
            cy,
            executeW,
            this.BUTTON_HEIGHT,
            this.configurationOnly && var51.hasAssignments() ? "Execute: In Game" : puLabel,
            CompactOverlayButton.Variant.PRIMARY,
            false,
            mx,
            my
         );
      }

      return cy + this.BUTTON_HEIGHT + 6;
   }

   private RiptideLANSyncOverlay.MacroSyncStatus analyzeSameMacroStatus(RiptideLANSync sync, String macroName) {
      List<String> missingPeers = new ArrayList<>(sync.getPeersMissingMacro(macroName));
      Map<String, Integer> differentPeers = new LinkedHashMap<>();
      if (macroName != null && !macroName.isBlank()) {
         RiptideMacro localMacro = RiptideMacroManager.get().get(macroName);
         if (localMacro == null) {
            return new RiptideLANSyncOverlay.MacroSyncStatus(missingPeers, differentPeers);
         } else {
            Set<String> missingPeerSet = new HashSet<>(missingPeers);
            Map<String, Map<String, RiptideMacro>> allRemote = sync.getAllRemoteMacros();
            List<String> peers = new ArrayList<>(sync.getConnectedClients().keySet());
            Collections.sort(peers);

            for (String peer : peers) {
               if (!peer.equals(sync.getMyUsername()) && !missingPeerSet.contains(peer)) {
                  Map<String, RiptideMacro> peerMacros = allRemote.get(peer);
                  if (peerMacros != null) {
                     RiptideMacro remoteMacro = peerMacros.get(MacroNames.key(macroName));
                     int differences = countActionDifferences(localMacro, remoteMacro);
                     if (differences > 0) {
                        differentPeers.put(peer, differences);
                     }
                  }
               }
            }

            return new RiptideLANSyncOverlay.MacroSyncStatus(missingPeers, differentPeers);
         }
      } else {
         return new RiptideLANSyncOverlay.MacroSyncStatus(missingPeers, differentPeers);
      }
   }

   private RiptideLANSyncOverlay.AssignmentSyncStatus analyzeAssignmentStatus(RiptideLANSync sync) {
      Map<String, String> assignments = new LinkedHashMap<>();
      Set<String> connectedPeers = new LinkedHashSet<>(sync.getConnectedClients().keySet());

      for (Entry<String, String> entry : new LinkedHashMap<>(this.perUserAssignments).entrySet()) {
         String username = entry.getKey();
         String macroName = entry.getValue();
         if (connectedPeers.contains(username) && macroName != null && !macroName.isBlank()) {
            assignments.put(username, macroName);
         }
      }

      Map<String, String> missingAssignments = sync.getMissingAssignedMacros(assignments);
      return new RiptideLANSyncOverlay.AssignmentSyncStatus(assignments, missingAssignments);
   }

   private LinkedHashSet<String> getShareableMissingMacros(RiptideLANSyncOverlay.AssignmentSyncStatus status) {
      LinkedHashSet<String> shareable = new LinkedHashSet<>();

      for (String macroName : status.missingAssignments.values()) {
         if (macroName != null && !macroName.isBlank() && RiptideMacroManager.get().get(macroName) != null) {
            shareable.add(macroName);
         }
      }

      return shareable;
   }

   private String formatNameSummary(List<String> names) {
      return names != null && !names.isEmpty() ? String.join(", ", names) : "none";
   }

   private String formatDifferenceSummary(Map<String, Integer> differences) {
      if (differences != null && !differences.isEmpty()) {
         List<String> parts = new ArrayList<>();

         for (Entry<String, Integer> entry : differences.entrySet()) {
            int diffCount = Math.max(1, entry.getValue());
            parts.add(entry.getKey() + " (+" + diffCount + ")");
         }

         return String.join(", ", parts);
      } else {
         return "none";
      }
   }

   private String formatMissingAssignmentsSummary(Map<String, String> missingAssignments) {
      if (missingAssignments != null && !missingAssignments.isEmpty()) {
         List<String> parts = new ArrayList<>();

         for (Entry<String, String> entry : missingAssignments.entrySet()) {
            parts.add(entry.getKey() + "=" + entry.getValue());
         }

         return String.join(", ", parts);
      } else {
         return "none";
      }
   }

   private int renderPickerRowClipped(
      GuiGraphicsExtractor ctx,
      int mx,
      int my,
      int cy,
      int detailLx,
      int detailRx,
      int rowW,
      String macroName,
      String assigned,
      String badge,
      String peerKey,
      RiptideLANSync sync,
      boolean allowImport,
      int clipTop,
      int clipBottom,
      int rowPitch
   ) {
      boolean isCurrent = MacroNames.equal(macroName, assigned);
      int importW = allowImport ? 42 : 0;
      int assignW = Math.max(24, detailRx - (detailLx - 2) - importW - 2);
      boolean rowVisible = cy + rowPitch > clipTop && cy < clipBottom;
      if (!rowVisible) {
         return cy + rowPitch;
      } else {
         int drawY = Math.max(cy, clipTop);
         int drawH = Math.min(cy + rowPitch, clipBottom) - drawY;
         if (drawH <= 0) {
            return cy + rowPitch;
         } else {
            boolean mHov = this.isHovered(mx, my, detailLx - 2, cy, assignW, rowPitch);
            CompactListRenderer.drawRow(
               ctx,
               this.textRenderer,
               "",
               detailLx - 2,
               drawY,
               detailRx - (detailLx - 2),
               drawH,
               mHov,
               isCurrent,
               badge != null ? CompactListRenderer.RowTone.WARNING : CompactListRenderer.RowTone.NORMAL
            );
            int nameMaxW = Math.max(24, detailRx - detailLx - importW - (badge != null ? 74 : 4));
            String displayName = RiptideText.trimToWidth(this.textRenderer, macroName, nameMaxW, RiptideText.Tone.BODY);
            int textY = Math.max(cy + 3, clipTop + 1);
            if (textY < clipBottom - 2) {
               UiText.draw(
                  ctx,
                  this.textRenderer,
                  displayName,
                  PACKUI_THEME.fontFor(UiTone.BODY),
                  isCurrent ? RiptideColors.rowSelectedText() : -1,
                  detailLx,
                  textY,
                  false
               );
            }

            if (badge != null && textY < clipBottom - 2) {
               int badgeX = detailLx
                  + UiText.width(this.textRenderer, displayName, PACKUI_THEME.fontFor(UiTone.BODY), isCurrent ? RiptideColors.rowSelectedText() : -1)
                  + 4;
               this.drawUiText(ctx, badge, UiTone.MUTED, -22016, badgeX, textY);
            }

            if (rowVisible) {
               this.clickRegions.add(new RiptideLANSyncOverlay.ClickRegion(detailLx - 2, cy, assignW, rowPitch, () -> {
                  this.perUserAssignments.put(peerKey, macroName.equals(this.perUserAssignments.get(peerKey)) ? "" : macroName);
                  sync.broadcastAssignments(this.perUserAssignments);
               }));
            }

            if (allowImport) {
               int importX = detailRx - importW + 2;
               int importY = Math.max(cy + 1, clipTop + 1);
               if (importY < clipBottom - 4) {
                  this.drawOverlayButton(
                     ctx,
                     importX,
                     importY,
                     importW - 4,
                     Math.min(rowPitch - 2, clipBottom - importY - 1),
                     "Import",
                     CompactOverlayButton.Variant.SECONDARY,
                     true,
                     mx,
                     my
                  );
               }

               if (rowVisible) {
                  this.clickRegions.add(new RiptideLANSyncOverlay.ClickRegion(importX, cy, importW - 4, rowPitch, () -> {
                     Map<String, Map<String, RiptideMacro>> allRemote = sync.getAllRemoteMacros();
                     Map<String, RiptideMacro> peerMacros = allRemote.get(peerKey);
                     RiptideMacro sourceMacro = peerMacros != null ? peerMacros.get(MacroNames.key(macroName)) : null;
                     if (sourceMacro == null) {
                        RiptideClientMessaging.sendPrefixed("§cMacro not found: " + macroName);
                     } else {
                        RiptideMacro imported = RiptideMacroManager.get().addImportedCopy(sourceMacro, sourceMacro.name);
                        if (imported == null) {
                           RiptideClientMessaging.sendPrefixed("§cFailed to import macro: " + macroName);
                        } else {
                           RiptideClientMessaging.sendPrefixed("§aImported macro: " + imported.name);
                        }
                     }
                  }));
               }
            }

            return cy + rowPitch;
         }
      }
   }

   private static int countActionDifferences(RiptideMacro a, RiptideMacro b) {
      if (a != null && b != null) {
         List<MacroAction> aActions = a.actions != null ? a.actions : Collections.emptyList();
         List<MacroAction> bActions = b.actions != null ? b.actions : Collections.emptyList();
         int diffs = 0;
         int maxLen = Math.max(aActions.size(), bActions.size());

         for (int i = 0; i < maxLen; i++) {
            if (i < aActions.size() && i < bActions.size()) {
               CompoundTag tagA = aActions.get(i).toTag();
               CompoundTag tagB = bActions.get(i).toTag();
               if (!tagA.equals(tagB)) {
                  diffs++;
               }
            } else {
               diffs++;
            }
         }

         return diffs;
      } else {
         return -1;
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.visible) {
         return false;
      } else if (button == 0 && mouseX >= this.panelX && mouseX <= this.panelX + this.PANEL_WIDTH && mouseY >= this.panelY && mouseY <= this.panelY + 16) {
         RiptideWindowLayout bounds = new RiptideWindowLayout(
            this.panelX, this.panelY, this.PANEL_WIDTH, this.collapsed ? 16 : this.getPanelHeight(), this.visible, this.collapsed
         );
         if (this.isOverCloseButton(mouseX, mouseY, bounds)) {
            this.setVisible(false);
            return true;
         } else {
            this.isDragging = true;
            this.dragOffsetX = mouseX - this.panelX;
            this.dragOffsetY = mouseY - this.panelY;
            return true;
         }
      } else if (this.collapsed) {
         return false;
      } else {
         if (this.lanChatField != null) {
            MouseButtonEvent click = new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0));
            if (this.lanChatField.mouseClicked(click, false)) {
               this.lanChatField.setFocused(true);
               return true;
            }

            this.lanChatField.setFocused(false);
         }

         RiptideLANSync sync = RiptideLANSync.getInstance();
         if (button == 0 && this.hasScrollableSessionContent(sync)) {
            int panelHeight = this.getPanelHeight();
            int tabContentY = this.getTabContentY();
            int tabContentH = this.getTabContentHeight(panelHeight);
            CompactScrollbar.Metrics scrollbarMetrics = this.getContentScrollbarMetrics(tabContentY, tabContentH);
            if (scrollbarMetrics.hasScroll() && scrollbarMetrics.contains(mouseX, mouseY)) {
               this.scrollbarDragging = true;
               this.scrollbarGrabOffset = Math.max(0, (int)Math.round(mouseY) - scrollbarMetrics.thumbY());
               this.scrollOffset = CompactScrollbar.scrollFromThumb(scrollbarMetrics, (int)Math.round(mouseY), this.scrollbarGrabOffset);
               this.contentScrollState.jumpTo(this.scrollOffset, scrollbarMetrics.maxScroll());
               this.saveState();
               return true;
            }
         }

         if (button == 0
            && this.activeTab == 2
            && !this.perUserMode
            && this.hasSameMacrolistViewport()
            && this.sameMacroList.contains(mouseX, mouseY)
            && this.sameMacroList.mouseClicked(mouseX, mouseY, button)) {
            if (!this.sameMacroList.isScrollbarDragging()) {
               RiptideMacro selected = this.sameMacroList.getSelectedItem();
               if (selected != null) {
                  this.selectedMacroName = selected.name;
                  this.saveState();
               }
            }

            return true;
         } else if (button == 0
            && this.activeTab == 2
            && this.perUserMode
            && this.hasperUserlistViewport()
            && this.perUserlistViewport.contains(mouseX, mouseY)
            && this.perUserlistViewport.mouseClicked(mouseX, mouseY, button)) {
            return true;
         } else {
            if (button == 0) {
               for (RiptideLANSyncOverlay.ClickRegion region : this.clickRegions) {
                  if (region.contains(mouseX, mouseY)) {
                     region.action.run();
                     return true;
                  }
               }
            }

            return this.isMouseOver(mouseX, mouseY);
         }
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (button == 0) {
         if (this.hasSameMacrolistViewport()) {
            this.sameMacroList.mouseReleased();
         }

         if (this.hasperUserlistViewport()) {
            this.perUserlistViewport.mouseReleased();
         }

         if (this.scrollbarDragging) {
            this.saveState();
         }

         this.scrollbarDragging = false;
         if (this.isDragging) {
            this.saveState();
         }

         this.isDragging = false;
      }

      return this.lanChatField != null && this.lanChatField.mouseReleased(mouseX, mouseY, button);
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (this.scrollbarDragging) {
         int panelHeight = this.getPanelHeight();
         int tabContentY = this.getTabContentY();
         int tabContentH = this.getTabContentHeight(panelHeight);
         CompactScrollbar.Metrics scrollbarMetrics = this.getContentScrollbarMetrics(tabContentY, tabContentH);
         this.scrollOffset = CompactScrollbar.scrollFromThumb(scrollbarMetrics, (int)Math.round(mouseY), this.scrollbarGrabOffset);
         this.contentScrollState.jumpTo(this.scrollOffset, scrollbarMetrics.maxScroll());
         return true;
      } else if (this.hasSameMacrolistViewport() && this.sameMacroList.isScrollbarDragging()) {
         this.sameMacroList.mouseDragged(mouseX, mouseY);
         return true;
      } else if (this.hasperUserlistViewport() && this.perUserlistViewport.isScrollbarDragging()) {
         this.perUserlistViewport.mouseDragged(mouseX, mouseY);
         return true;
      } else if (this.isDragging) {
         RiptideWindowLayout nextBounds = this.clampToScreen(
            this,
            new RiptideWindowLayout(
               (int)(mouseX - this.dragOffsetX), (int)(mouseY - this.dragOffsetY), this.PANEL_WIDTH, this.PANEL_HEIGHT, this.visible, this.collapsed
            )
         );
         this.panelX = nextBounds.x;
         this.panelY = nextBounds.y;
         return true;
      } else {
         return this.lanChatField != null && this.lanChatField.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
      }
   }

   @Override
   public boolean keyPressed(int key, int scancode, int modifiers) {
      if (!this.visible || this.collapsed) {
         return false;
      } else if (this.lanChatField == null || !this.lanChatField.isFocused()) {
         return false;
      } else if (key == 256) {
         this.lanChatField.setFocused(false);
         return true;
      } else {
         this.lanChatField.keyPressed(new KeyEvent(key, scancode, modifiers));
         return true;
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (!this.visible || this.collapsed || !this.isMouseOver(mouseX, mouseY)) {
         return false;
      } else if (this.activeTab == 2 && !this.perUserMode && this.hasSameMacrolistViewport()) {
         return this.sameMacroList.mouseScrolled(mouseX, mouseY, amount);
      } else if (this.activeTab == 2 && this.perUserMode && this.hasperUserlistViewport()) {
         return this.perUserlistViewport.mouseScrolled(mouseX, mouseY, amount);
      } else {
         this.scrollOffset = Math.max(0, Math.min(this.maxScroll, this.scrollOffset - (int)(amount * 14.0)));
         this.saveState();
         return true;
      }
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      if (!this.visible || this.collapsed) {
         return false;
      } else {
         return this.lanChatField != null && this.lanChatField.isFocused() ? this.lanChatField.charTyped(new CharacterEvent(chr)) : false;
      }
   }

   private boolean isHovered(int mx, int my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }

   private int getSyncStateColor(RiptideLANSync.SyncState state) {
      switch (state) {
         case IDLE:
            return RiptideColors.textDim();
         case PREPARING:
            return -22016;
         case ALL_READY:
            return -11141291;
         case DISPATCHING_GO:
            return -11141121;
         case EXECUTING:
            return -11141291;
         case REPORTING:
            return -22016;
         case DONE:
            return -11141291;
         default:
            return RiptideColors.textSecondary();
      }
   }

   private String formatSyncState(RiptideLANSync.SyncState state, RiptideLANSync sync) {
      switch (state) {
         case IDLE:
            return "Idle";
         case PREPARING:
            int ready = 0;
            int total = sync.getConnectedCount();
            return "Preparing (" + ready + "/" + total + " ready)";
         case ALL_READY:
            return "All Ready!";
         case DISPATCHING_GO:
            return "GO!";
         case EXECUTING:
            return "Executing...";
         case REPORTING:
            return "Collecting results...";
         case DONE:
            return "Done";
         default:
            return state.name();
      }
   }

   private int getSpreadColor(double spreadMs) {
      if (spreadMs <= 1.0) {
         return -11141291;
      } else {
         return spreadMs <= 5.0 ? -22016 : RiptideTheme.recolor(-43691, RiptideTheme.Channel.DANGER);
      }
   }

   private void applyPresetMetrics() {
      this.PANEL_BASE_WIDTH = 292;
      this.TAB_HEIGHT = 16;
      this.BUTTON_HEIGHT = 16;
      this.ROW_HEIGHT = 14;
      this.CONTENT_PADDING = 8;
      this.CHAT_FIELD_HEIGHT = 16;
      this.CHAT_AREA_HEIGHT = this.CHAT_FIELD_HEIGHT + 10;
   }

   private int minimumPanelHeight() {
      return 140;
   }

   private int defaultPanelHeight() {
      return 220;
   }

   private static class AssignmentSyncStatus {
      final Map<String, String> assignments;
      final Map<String, String> missingAssignments;

      AssignmentSyncStatus(Map<String, String> assignments, Map<String, String> missingAssignments) {
         this.assignments = assignments;
         this.missingAssignments = missingAssignments;
      }

      boolean hasAssignments() {
         return !this.assignments.isEmpty();
      }

      boolean canExecute() {
         return this.hasAssignments() && this.missingAssignments.isEmpty();
      }
   }

   private static class ClickRegion {
      final int x;
      final int y;
      final int width;
      final int height;
      final Runnable action;

      ClickRegion(int x, int y, int width, int height, Runnable action) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         this.action = action;
      }

      boolean contains(double mx, double my) {
         return mx >= this.x && mx < this.x + this.width && my >= this.y && my < this.y + this.height;
      }
   }

   private static class MacroSyncStatus {
      final List<String> missingPeers;
      final Map<String, Integer> differentPeers;

      MacroSyncStatus(List<String> missingPeers, Map<String, Integer> differentPeers) {
         this.missingPeers = missingPeers;
         this.differentPeers = differentPeers;
      }

      boolean canExecute() {
         return this.missingPeers.isEmpty();
      }
   }
}
