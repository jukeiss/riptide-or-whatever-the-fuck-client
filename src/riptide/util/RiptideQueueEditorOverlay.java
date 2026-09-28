package riptide.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.components.CompactListRenderer;
import riptide.gui.vanillaui.components.CompactOverlayButton;
import riptide.gui.vanillaui.components.CompactOverlayControls;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.ScrollState;
import riptide.gui.vanillaui.components.Tooltip;
import riptide.gui.vanillaui.components.UiSizing;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectLayout;
import riptide.modules.RiptideModule;

public class RiptideQueueEditorOverlay extends RiptideOverlayBase {
   private static final Minecraft MC = Minecraft.getInstance();
   private int panelX = 100;
   private int panelY = 100;
   private int PANEL_WIDTH = 248;
   private int PANEL_HEIGHT = 176;
   private boolean isDragging = false;
   private double dragOffsetX = 0.0;
   private double dragOffsetY = 0.0;
   private int scrollOffset = 0;
   private boolean collapsed = false;
   private boolean visible = false;
   private boolean configurationOnly = false;
   private int selectedPacketId = -1;
   private boolean keepSelectedPacketVisible = false;
   private long lastObservedQueueChangeMs = 0L;
   private String lastObservedQueueSignature = "";
   private boolean pendingAutoReorder = false;
   private boolean scrollbarDragging = false;
   private int scrollbarGrabOffset = 0;
   private final Font textRenderer;
   private final CompactTheme theme = new CompactTheme();
   private final ScrollState listScrollState = new ScrollState();
   private List<RiptideSharedState.QueuedPacket> cachedDisplayQueue = new ArrayList<>();
   private final List<RiptideQueueEditorOverlay.ClickRegion> toolbarRegions = new ArrayList<>();
   private final List<RiptideQueueEditorOverlay.RowRegion> rowRegions = new ArrayList<>();
   private String hoveredTooltip = null;
   private int tooltipX = 0;
   private int tooltipY = 0;

   public RiptideQueueEditorOverlay(Font textRenderer) {
      this.textRenderer = textRenderer;
      this.PANEL_WIDTH = this.defaultPanelWidth();
      this.PANEL_HEIGHT = this.defaultPanelHeight();
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
      return new RiptideWindowLayout(this.panelX, this.panelY, this.PANEL_WIDTH, this.PANEL_HEIGHT, this.visible, this.collapsed);
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

   public boolean isCaptureMode() {
      return RiptideSharedState.get().isCaptureMode();
   }

   @Override
   public void setVisible(boolean visible) {
      this.visible = visible;
      if (visible) {
         this.scrollOffset = 0;
         this.listScrollState.jumpTo(0, 0);
         RiptideOverlayManager.get().bringToFront(this);
      }

      this.saveState();
   }

   public void toggle() {
      this.setVisible(!this.visible);
   }

   public void setConfigurationOnly(boolean configurationOnly) {
      this.configurationOnly = configurationOnly;
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
   public boolean isMouseOver(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else {
         List<RiptideSharedState.QueuedPacket> displayQueue = this.cachedDisplayQueue.isEmpty() ? this.getCurrentQueue() : this.cachedDisplayQueue;
         int panelHeight = this.collapsed
            ? 16
            : this.clampToScreen(
                  this, new RiptideWindowLayout(this.panelX, this.panelY, this.PANEL_WIDTH, this.getPanelHeight(displayQueue), this.visible, this.collapsed)
               )
               .height;
         return mouseX >= this.panelX && mouseX <= this.panelX + this.PANEL_WIDTH && mouseY >= this.panelY && mouseY <= this.panelY + panelHeight;
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

   public void saveState() {
      RiptideSharedState shared = RiptideSharedState.get();
      shared.setQueueEditorOverlayVisible(this.visible);
      shared.setQueueEditorOverlayX(this.panelX);
      shared.setQueueEditorOverlayY(this.panelY);
      this.saveLayout();
   }

   public void restoreState() {
      RiptideSharedState shared = RiptideSharedState.get();
      this.restoreLayout();
      this.visible = shared.isQueueEditorOverlayVisible();
      this.panelX = shared.getQueueEditorOverlayX();
      this.panelY = shared.getQueueEditorOverlayY();
   }

   private int getContentStartY() {
      return this.panelY + this.getContentStartOffset();
   }

   private int getContentStartOffset() {
      int toolbarRows = RiptideLANSync.getInstance().isInSession() ? 4 : 3;
      return this.toolbarTopOffset() + toolbarRows * this.toolbarButtonHeight() + (toolbarRows - 1) * this.toolbarGap() + this.contentTopGap();
   }

   private int getListX() {
      return this.panelX + this.panelInset();
   }

   private int getListWidth() {
      return DirectLayout.contentWidth(this.PANEL_WIDTH, this.panelInset());
   }

   private int getListContentWidth(boolean hasScrollbar) {
      return Math.max(40, DirectLayout.reserveScrollbar(this.getListWidth(), hasScrollbar, this.listScrollbarGutter()));
   }

   private int getToolbarRowWidth() {
      return this.getListWidth();
   }

   private int fitToolbarButtonWidth(String label, int minWidth, int maxWidth) {
      return DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, label, 5, minWidth, maxWidth);
   }

   private int getListHeight(int panelHeight) {
      return Math.max(this.rowHeight() + 4, panelHeight - this.getContentStartOffset() - this.panelInset());
   }

   private int getlistViewportHeight(int panelHeight) {
      return this.alignViewportHeight(Math.max(0, this.getListHeight(panelHeight) - 2), this.rowHeight());
   }

   private int getListClipTop() {
      return this.getContentStartY() - 1;
   }

   private int getListClipBottom(int panelHeight) {
      return this.getListClipTop() + this.getlistViewportHeight(panelHeight);
   }

   private CompactScrollbar.Metrics getScrollbarMetrics(int panelHeight) {
      int visibleHeight = this.getlistViewportHeight(panelHeight);
      int contentHeight = this.cachedDisplayQueue.size() * this.rowHeight();
      int maxScroll = Math.max(0, contentHeight - visibleHeight);
      return CompactScrollbar.compute(
         contentHeight,
         Math.max(1, visibleHeight),
         this.getListX() + this.getListWidth() - 5,
         this.getListClipTop(),
         4,
         Math.max(1, this.getListHeight(panelHeight) - 2),
         this.listScrollState.tick(0.0F, maxScroll)
      );
   }

   private int getRemoveButtonX(int rowX, int rowWidth) {
      return rowX + rowWidth - this.rowButtonSize() - this.panelInset();
   }

   private int getDelayButtonWidth() {
      return this.fixedDelayButtonWidth();
   }

   private int getDelayButtonX(int rowX, int rowWidth) {
      return this.getRemoveButtonX(rowX, rowWidth) - 4 - this.getDelayButtonWidth();
   }

   private int getRowLabelMaxWidth(int rowX, int rowWidth) {
      return Math.max(40, this.getDelayButtonX(rowX, rowWidth) - 14 - 2 - (rowX + 8) - 6);
   }

   private int getPanelHeight(List<RiptideSharedState.QueuedPacket> displayQueue) {
      int contentHeight = Math.min(displayQueue.size(), this.maxVisibleRows()) * this.rowHeight();
      return Math.max(this.getMinHeight(), this.getContentStartOffset() + contentHeight + this.contentBottomGap());
   }

   private RiptideSharedState.QueuedPacket getSelectedPacket(List<RiptideSharedState.QueuedPacket> queue) {
      if (this.selectedPacketId == -1) {
         return null;
      } else {
         for (RiptideSharedState.QueuedPacket packet : queue) {
            if (packet.getId() == this.selectedPacketId) {
               return packet;
            }
         }

         this.selectedPacketId = -1;
         return null;
      }
   }

   private void clearSelection() {
      this.selectedPacketId = -1;
   }

   private List<RiptideSharedState.QueuedPacket> getCurrentQueue() {
      RiptideSharedState shared = RiptideSharedState.get();
      List<RiptideSharedState.QueuedPacket> queue = shared.getStaggeredQueue();
      if (queue.isEmpty()) {
         queue = shared.getDelayedPackets();
      }

      return queue;
   }

   private int drawAutoToolbarButton(
      GuiGraphicsExtractor context, int x, int y, int h, String label, boolean active, int mouseX, int mouseY, String tip, Runnable action
   ) {
      int width = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, label, 5, 24, 96);
      CompactOverlayControls.toggle(context, this.textRenderer, x, y, width, h, label, active, "queue:auto:" + label + ":" + x + ":" + y, mouseX, mouseY);
      this.toolbarRegions.add(new RiptideQueueEditorOverlay.ClickRegion(x, y, width, h, action));
      this.checkBtnHover(x, y, width, h, mouseX, mouseY, tip);
      return x + width;
   }

   private int drawAutoToolbarButton(GuiGraphicsExtractor context, int x, int y, int h, String label, int mouseX, int mouseY, String tip, Runnable action) {
      int width = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, label, 5, 24, 96);
      CompactOverlayControls.action(context, this.textRenderer, x, y, width, h, label, CompactOverlayButton.Variant.GHOST, true, mouseX, mouseY);
      this.toolbarRegions.add(new RiptideQueueEditorOverlay.ClickRegion(x, y, width, h, action));
      this.checkBtnHover(x, y, width, h, mouseX, mouseY, tip);
      return x + width;
   }

   private int drawAutoToolbarStatusButton(
      GuiGraphicsExtractor context, int x, int y, int h, String label, boolean active, int mouseX, int mouseY, String tip, Runnable action
   ) {
      int width = DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, label, 5, 24, 112);
      CompactOverlayControls.toggle(context, this.textRenderer, x, y, width, h, label, active, "queue:status:" + label + ":" + x + ":" + y, mouseX, mouseY);
      this.toolbarRegions.add(new RiptideQueueEditorOverlay.ClickRegion(x, y, width, h, action));
      this.checkBtnHover(x, y, width, h, mouseX, mouseY, tip);
      return x + width;
   }

   private void drawFixedToolbarButton(
      GuiGraphicsExtractor context, int x, int y, int w, int h, String label, boolean active, int mouseX, int mouseY, String tip, Runnable action
   ) {
      this.drawFixedToolbarButton(context, x, y, w, h, label, CompactOverlayButton.Variant.SECONDARY, active, mouseX, mouseY, tip, action);
   }

   private void drawFixedToolbarButton(
      GuiGraphicsExtractor context,
      int x,
      int y,
      int w,
      int h,
      String label,
      CompactOverlayButton.Variant variant,
      boolean active,
      int mouseX,
      int mouseY,
      String tip,
      Runnable action
   ) {
      CompactOverlayControls.action(context, this.textRenderer, x, y, w, h, label, variant, active, mouseX, mouseY);
      if (active) {
         this.toolbarRegions.add(new RiptideQueueEditorOverlay.ClickRegion(x, y, w, h, action));
      }

      this.checkBtnHover(x, y, w, h, mouseX, mouseY, tip);
   }

   private void drawFixedToolbarButton(
      GuiGraphicsExtractor context, int x, int y, int w, int h, String label, int mouseX, int mouseY, String tip, Runnable action
   ) {
      this.drawFixedToolbarButton(context, x, y, w, h, label, false, mouseX, mouseY, tip, action);
   }

   private void drawFixedToolbarStateButton(
      GuiGraphicsExtractor context, int x, int y, int w, int h, String label, boolean enabled, int mouseX, int mouseY, String tip, Runnable action
   ) {
      CompactOverlayControls.toggle(context, this.textRenderer, x, y, w, h, label, enabled, "queue:state:" + label + ":" + x + ":" + y, mouseX, mouseY);
      this.toolbarRegions.add(new RiptideQueueEditorOverlay.ClickRegion(x, y, w, h, action));
      this.checkBtnHover(x, y, w, h, mouseX, mouseY, tip);
   }

   private boolean isQueueSortedByDelay(List<RiptideSharedState.QueuedPacket> queue) {
      for (int i = 1; i < queue.size(); i++) {
         RiptideSharedState.QueuedPacket previous = queue.get(i - 1);
         RiptideSharedState.QueuedPacket current = queue.get(i);
         if (previous.getDelay() > current.getDelay()) {
            return false;
         }

         if (previous.getDelay() == current.getDelay() && previous.getId() > current.getId()) {
            return false;
         }
      }

      return true;
   }

   private String buildQueueSignature(List<RiptideSharedState.QueuedPacket> queue) {
      if (queue.isEmpty()) {
         return "";
      } else {
         StringBuilder signature = new StringBuilder(queue.size() * 16);

         for (RiptideSharedState.QueuedPacket packet : queue) {
            signature.append(packet.getId()).append(':').append(packet.getDelay()).append(';');
         }

         return signature.toString();
      }
   }

   private void observeQueueChanges(List<RiptideSharedState.QueuedPacket> queue) {
      String signature = this.buildQueueSignature(queue);
      if (!signature.equals(this.lastObservedQueueSignature)) {
         this.lastObservedQueueSignature = signature;
         this.lastObservedQueueChangeMs = System.currentTimeMillis();
         this.pendingAutoReorder = true;
      }
   }

   private void maybeAutoReorderDelayedQueue(RiptideSharedState shared) {
      List<RiptideSharedState.QueuedPacket> delayedQueue = shared.getDelayedPackets();
      this.observeQueueChanges(delayedQueue);
      if (delayedQueue.size() >= 2) {
         if (this.pendingAutoReorder) {
            if (System.currentTimeMillis() - this.lastObservedQueueChangeMs >= 800L) {
               if (this.isQueueSortedByDelay(delayedQueue)) {
                  this.pendingAutoReorder = false;
               } else {
                  shared.sortDelayedPacketsByDelayPreservingIds();
                  this.lastObservedQueueSignature = this.buildQueueSignature(shared.getDelayedPackets());
                  this.pendingAutoReorder = false;
                  this.keepSelectedPacketVisible = true;
               }
            }
         }
      }
   }

   private void ensureSelectedPacketVisible(List<RiptideSharedState.QueuedPacket> queue, RiptideSharedState.QueuedPacket selectedPacket, int panelHeight) {
      if (selectedPacket != null && !queue.isEmpty()) {
         int selectedIndex = -1;

         for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId() == selectedPacket.getId()) {
               selectedIndex = i;
               break;
            }
         }

         if (selectedIndex >= 0) {
            int visibleHeight = this.getlistViewportHeight(panelHeight);
            int maxScroll = Math.max(0, queue.size() * this.rowHeight() - visibleHeight);
            int rowTop = selectedIndex * this.rowHeight();
            int rowBottom = rowTop + this.rowHeight();
            if (rowTop < this.scrollOffset) {
               this.scrollOffset = rowTop;
            } else if (rowBottom > this.scrollOffset + visibleHeight) {
               this.scrollOffset = rowBottom - visibleHeight;
            }

            this.scrollOffset = this.quantizeScrollOffset(this.scrollOffset, this.rowHeight(), maxScroll);
         }
      }
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      if (this.visible) {
         RiptideSharedState shared = RiptideSharedState.get();
         List<RiptideSharedState.QueuedPacket> staggeredQueue = shared.getStaggeredQueue();
         if (staggeredQueue.isEmpty()) {
            this.maybeAutoReorderDelayedQueue(shared);
            this.cachedDisplayQueue = shared.getDelayedPackets();
         } else {
            this.cachedDisplayQueue = new ArrayList<>(staggeredQueue);
         }

         RiptideSharedState.QueuedPacket selectedPacket = this.getSelectedPacket(this.cachedDisplayQueue);
         RiptideWindowLayout clamped = this.clampToScreen(
            this,
            new RiptideWindowLayout(this.panelX, this.panelY, this.PANEL_WIDTH, this.getPanelHeight(this.cachedDisplayQueue), this.visible, this.collapsed)
         );
         this.panelX = clamped.x;
         this.panelY = clamped.y;
         this.PANEL_WIDTH = clamped.width;
         int panelHeight = clamped.height;
         this.PANEL_HEIGHT = panelHeight;
         if (!this.collapsed && this.keepSelectedPacketVisible) {
            this.ensureSelectedPacketVisible(this.cachedDisplayQueue, selectedPacket, panelHeight);
            this.keepSelectedPacketVisible = false;
         }

         this.toolbarRegions.clear();
         this.rowRegions.clear();
         this.hoveredTooltip = null;
         String title = "Queue (" + this.cachedDisplayQueue.size() + ")";
         RiptideWindowLayout bounds = new RiptideWindowLayout(this.panelX, this.panelY, this.PANEL_WIDTH, panelHeight, this.visible, this.collapsed);
         this.renderWindowFrame(context, mouseX, mouseY, bounds, title, this.collapsed, this.isDragging);
         boolean clipBody = this.beginWindowBodyClip(context, bounds, this.collapsed);
         if (!clipBody) {
            this.renderWindowInactiveOverlay(context, bounds, this.collapsed, this.isDragging);
         } else {
            try {
               int gap = this.toolbarGap();
               int row1Y = this.panelY + this.toolbarTopOffset();
               int row2Y = row1Y + this.toolbarButtonHeight() + gap;
               int row3Y = row2Y + this.toolbarButtonHeight() + gap;
               int row4Y = row3Y + this.toolbarButtonHeight() + gap;
               int leftX = this.panelX + this.panelInset();
               int rowWidth = this.getToolbarRowWidth();
               int rowRight = leftX + rowWidth;
               int bx = this.drawAutoToolbarButton(
                     context, leftX, row1Y, this.toolbarButtonHeight(), "Copy", mouseX, mouseY, "Copy queue to clipboard (serialized)", () -> {
                        RiptideClipboardHelper.copyToClipboard(this.getCurrentQueue());
                        RiptideNotifications.copied("Queue copied.");
                     }
                  )
                  + gap;
               bx = this.drawAutoToolbarButton(context, bx, row1Y, this.toolbarButtonHeight(), "Paste", mouseX, mouseY, "Replace queue from clipboard", () -> {
                  if (RiptideSharedState.get().getStaggeredQueue().isEmpty()) {
                     RiptideSharedState.QueuedPacket.resetIdCounter();
                  }

                  List<RiptideSharedState.QueuedPacket> newQueue = RiptideClipboardHelper.pasteFromClipboard();
                  if (newQueue != null) {
                     RiptideSharedState.get().setDelayedPackets(newQueue);
                     this.clearSelection();
                     this.scrollOffset = 0;
                     RiptideClientMessaging.sendPrefixed("§aQueue pasted!");
                  }
               }) + gap;
               bx = this.drawAutoToolbarButton(
                     context, bx, row1Y, this.toolbarButtonHeight(), "Dupe", mouseX, mouseY, "Duplicate all packets in the queue", () -> {
                        List<RiptideSharedState.QueuedPacket> currentQueue = RiptideSharedState.get().getDelayedPackets();
                        if (!currentQueue.isEmpty()) {
                           List<RiptideSharedState.QueuedPacket> duplicatedQueue = new ArrayList<>(currentQueue);

                           for (RiptideSharedState.QueuedPacket packetx : currentQueue) {
                              duplicatedQueue.add(new RiptideSharedState.QueuedPacket(packetx.packet, packetx.getDelay(), packetx.getReplayMode()));
                           }

                           RiptideSharedState.get().setDelayedPackets(duplicatedQueue);
                           this.clearSelection();
                           RiptideClientMessaging.sendPrefixed("§aDuplicated " + currentQueue.size() + " packet(s).");
                        }
                     }
                  )
                  + gap;
               bx = this.drawAutoToolbarButton(
                     context, bx, row1Y, this.toolbarButtonHeight(), "Ins", mouseX, mouseY, "Insert clipboard packets at the end of the queue", () -> {
                        List<RiptideSharedState.QueuedPacket> newPackets = RiptideClipboardHelper.pasteFromClipboard();
                        if (newPackets != null && !newPackets.isEmpty()) {
                           List<RiptideSharedState.QueuedPacket> combinedQueue = new ArrayList<>(RiptideSharedState.get().getDelayedPackets());
                           combinedQueue.addAll(newPackets);
                           RiptideSharedState.get().setDelayedPackets(combinedQueue);
                           this.clearSelection();
                           RiptideClientMessaging.sendPrefixed("§aInserted " + newPackets.size() + " packet(s).");
                        }
                     }
                  )
                  + gap;
               int replayWidth = Math.max(this.fitToolbarButtonWidth("Replay", 48, 84), rowRight - bx);
               this.drawFixedToolbarButton(
                  context,
                  bx,
                  row1Y,
                  replayWidth,
                  this.toolbarButtonHeight(),
                  "Replay",
                  CompactOverlayButton.Variant.GHOST,
                  true,
                  mouseX,
                  mouseY,
                  "Restore the last flushed queue back into the editor",
                  () -> {
                     if (RiptideSharedState.get().restoreLastFlushedQueue()) {
                        this.clearSelection();
                        this.scrollOffset = 0;
                        RiptideClientMessaging.sendPrefixed("§aRestored - packets will regenerate on send");
                     } else {
                        RiptideClientMessaging.sendPrefixed("§cNo history");
                     }
                  }
               );
               boolean flushOnDelay = RiptideModule.get().shouldFlushQueueOnDelayDisable();
               boolean captureAsExact = RiptideModule.get().isCaptureAsExact();
               int clearWidth = this.fitToolbarButtonWidth("Clear", 42, 58);
               int captureModeBtnWidth = this.toolbarButtonHeight();
               int flushWidth = Math.max(this.fitToolbarButtonWidth("FLUSH ON DELAY", 110, 150), rowWidth - clearWidth - captureModeBtnWidth - gap - gap);
               this.drawFixedToolbarStateButton(
                  context,
                  leftX,
                  row2Y,
                  flushWidth,
                  this.toolbarButtonHeight(),
                  "FLUSH ON DELAY",
                  flushOnDelay,
                  mouseX,
                  mouseY,
                  flushOnDelay
                     ? "When delay turns off, queued packets auto-flush"
                     : "When delay turns off, keep the queued packets until you flush or clear them",
                  () -> {
                     RiptideModule module = RiptideModule.get();
                     boolean newValue = !module.shouldFlushQueueOnDelayDisable();
                     module.setFlushQueueOnDelayDisable(newValue);
                     RiptideClientMessaging.sendPrefixed(newValue ? "§aFlush on Delay ON" : "§eFlush on Delay OFF");
                  }
               );
               this.drawFixedToolbarButton(
                  context,
                  leftX + flushWidth + gap,
                  row2Y,
                  captureModeBtnWidth,
                  this.toolbarButtonHeight(),
                  captureAsExact ? "E" : "R",
                  captureAsExact ? CompactOverlayButton.Variant.DANGER : CompactOverlayButton.Variant.SUCCESS,
                  true,
                  mouseX,
                  mouseY,
                  captureAsExact ? "Capture as Exact: new packets stored verbatim" : "Capture as Regenerate: new packets rewritten on send",
                  () -> {
                     RiptideModule module = RiptideModule.get();
                     boolean nowExact = !module.isCaptureAsExact();
                     module.setCaptureAsExact(nowExact);
                     RiptideSharedState.ReplayMode bulkMode = nowExact ? RiptideSharedState.ReplayMode.EXACT : RiptideSharedState.ReplayMode.REGENERATE;
                     int updated = 0;

                     for (RiptideSharedState.QueuedPacket qp : RiptideSharedState.get().getDelayedPackets()) {
                        if (qp.getReplayMode() != bulkMode) {
                           qp.setReplayMode(bulkMode);
                           updated++;
                        }
                     }

                     RiptideClientMessaging.sendPrefixed(
                        nowExact
                           ? "§cCapture mode: EXACT" + (updated > 0 ? " (" + updated + " queued updated)" : "")
                           : "§aCapture mode: REGENERATE" + (updated > 0 ? " (" + updated + " queued updated)" : "")
                     );
                  }
               );
               this.drawFixedToolbarButton(
                  context,
                  leftX + flushWidth + gap + captureModeBtnWidth + gap,
                  row2Y,
                  clearWidth,
                  this.toolbarButtonHeight(),
                  "Clear",
                  CompactOverlayButton.Variant.DANGER,
                  true,
                  mouseX,
                  mouseY,
                  "Remove all packets from the queue",
                  () -> {
                     int cleared = RiptideSharedState.get().clearQueuedPackets();
                     this.clearSelection();
                     this.scrollOffset = 0;
                     RiptideModule.get().notifyClearQueuedPacketsUiResult(cleared);
                  }
               );
               boolean captureModeOn = RiptideSharedState.get().isCaptureMode();
               String delayModeLabel = RiptideSharedState.get().getDelayMode() == RiptideSharedState.DelayMode.TICKS ? "Ticks" : "Ms";
               int modeWidth = this.fitToolbarButtonWidth(delayModeLabel, 34, 44);
               int captureWidth = this.fitToolbarButtonWidth("Capture", 56, 72);
               int plus1Width = this.fitToolbarButtonWidth("+1", 22, 28);
               int plus10Width = this.fitToolbarButtonWidth("+10", 26, 34);
               int plus20Width = this.fitToolbarButtonWidth("+20", 26, 34);
               int sendWidth = Math.max(
                  this.fitToolbarButtonWidth("Send Q", 54, 82), rowWidth - modeWidth - captureWidth - plus1Width - plus10Width - plus20Width - gap * 5
               );
               this.drawFixedToolbarButton(
                  context,
                  leftX,
                  row3Y,
                  modeWidth,
                  this.toolbarButtonHeight(),
                  delayModeLabel,
                  CompactOverlayButton.Variant.GHOST,
                  true,
                  mouseX,
                  mouseY,
                  "Toggle delay unit: Ticks or milliseconds",
                  () -> {
                     RiptideSharedState.get().toggleDelayMode();
                     String newMode = RiptideSharedState.get().getDelayMode() == RiptideSharedState.DelayMode.TICKS ? "Ticks" : "Ms";
                     RiptideClientMessaging.sendPrefixed("§aDelay mode: " + newMode);
                  }
               );
               this.drawFixedToolbarStateButton(
                  context,
                  leftX + modeWidth + gap,
                  row3Y,
                  captureWidth,
                  this.toolbarButtonHeight(),
                  "Capture",
                  captureModeOn,
                  mouseX,
                  mouseY,
                  "Record real timing between captured packets",
                  () -> {
                     boolean newCapture = !RiptideSharedState.get().isCaptureMode();
                     RiptideSharedState.get().setCaptureMode(newCapture);
                     RiptideClientMessaging.sendPrefixed(newCapture ? "§aCapture Real Delays ON" : "§eCapture Real Delays OFF");
                  }
               );
               int adjustX = leftX + modeWidth + gap + captureWidth + gap;
               this.drawFixedToolbarButton(
                  context,
                  adjustX,
                  row3Y,
                  plus1Width,
                  this.toolbarButtonHeight(),
                  "+1",
                  CompactOverlayButton.Variant.GHOST,
                  true,
                  mouseX,
                  mouseY,
                  "Add +1 to the selected packet, or apply a +1 incremental pattern to the whole queue",
                  () -> this.handleIncrementalDelay(1)
               );
               this.drawFixedToolbarButton(
                  context,
                  adjustX + plus1Width + gap,
                  row3Y,
                  plus10Width,
                  this.toolbarButtonHeight(),
                  "+10",
                  CompactOverlayButton.Variant.GHOST,
                  true,
                  mouseX,
                  mouseY,
                  "Add +10 to the selected packet, or apply a +10 incremental pattern to the whole queue",
                  () -> this.handleIncrementalDelay(10)
               );
               this.drawFixedToolbarButton(
                  context,
                  adjustX + plus1Width + gap + plus10Width + gap,
                  row3Y,
                  plus20Width,
                  this.toolbarButtonHeight(),
                  "+20",
                  CompactOverlayButton.Variant.GHOST,
                  true,
                  mouseX,
                  mouseY,
                  "Add +20 to the selected packet, or apply a +20 incremental pattern to the whole queue",
                  () -> this.handleIncrementalDelay(20)
               );
               this.drawFixedToolbarButton(
                  context,
                  rowRight - sendWidth,
                  row3Y,
                  sendWidth,
                  this.toolbarButtonHeight(),
                  this.configurationOnly ? "In Game" : "Send Q",
                  CompactOverlayButton.Variant.SUCCESS,
                  !this.configurationOnly,
                  mouseX,
                  mouseY,
                  this.configurationOnly ? "Join a world to send queued packets" : "Send all queued packets to the server",
                  () -> {
                     if (!this.configurationOnly) {
                        if (MC.getConnection() != null) {
                           int count = RiptideSharedState.get().flushDelayedPackets(MC.getConnection());
                           RiptideModule.get().notifyFlushQueuedPacketsUiResult(count);
                        }
                     }
                  }
               );
               boolean inLanSession = RiptideLANSync.getInstance().isInSession();
               if (inLanSession) {
                  int syncWidth = this.fitToolbarButtonWidth("Sync", 46, 58);
                  int syncExecWidth = Math.max(this.fitToolbarButtonWidth("Sync Exec", 92, 118), rowWidth - syncWidth - gap);
                  this.drawFixedToolbarButton(
                     context,
                     leftX,
                     row4Y,
                     syncWidth,
                     this.toolbarButtonHeight(),
                     "Sync",
                     CompactOverlayButton.Variant.GHOST,
                     !this.configurationOnly,
                     mouseX,
                     mouseY,
                     this.configurationOnly ? "Join a world to share packet queues" : "Broadcast this queue to all LAN peers",
                     this::handleSyncButton
                  );
                  this.drawFixedToolbarButton(
                     context,
                     leftX + syncWidth + gap,
                     row4Y,
                     syncExecWidth,
                     this.toolbarButtonHeight(),
                     "Sync Exec",
                     CompactOverlayButton.Variant.SUCCESS,
                     !this.configurationOnly,
                     mouseX,
                     mouseY,
                     this.configurationOnly ? "Join a world to execute packet queues" : "Tell LAN peers to execute their queues together",
                     () -> RiptideLANSync.getInstance().sendQueuedPackets()
                  );
               }

               if (this.cachedDisplayQueue.isEmpty()) {
                  RiptideText.draw(context, this.textRenderer, "Queue empty", RiptideText.Tone.MUTED, this.panelX + 10, this.getContentStartY(), false);
               } else {
                  int visibleHeight = this.getlistViewportHeight(panelHeight);
                  int totalContentHeight = this.cachedDisplayQueue.size() * this.rowHeight();
                  int maxScroll = Math.max(0, totalContentHeight - visibleHeight);
                  this.scrollOffset = this.quantizeScrollOffset(this.scrollOffset, this.rowHeight(), maxScroll);
                  this.listScrollState.setTarget(this.scrollOffset, maxScroll);
                  int drawScroll = this.listScrollState.tick(delta, maxScroll);
                  int contentStartY = this.getContentStartY();
                  int listX = this.getListX();
                  int listY = contentStartY - 2;
                  int listWidth = this.getListWidth();
                  int listHeight = this.getListHeight(panelHeight);
                  int clipTop = this.getListClipTop();
                  int clipBottom = this.getListClipBottom(panelHeight);
                  CompactListRenderer.drawFrame(context, listX, listY, listWidth, listHeight, selectedPacket != null);
                  CompactScrollbar.Metrics scrollbarMetrics = this.getScrollbarMetrics(panelHeight);
                  int listContentWidth = this.getListContentWidth(scrollbarMetrics.hasScroll());
                  UiScissorStack.global().push(context, UiBounds.of(listX + 1, clipTop, Math.max(0, listWidth - 2), Math.max(0, clipBottom - clipTop)));
                  int baseY = contentStartY - drawScroll;
                  int groupColorIndex = 0;
                  int lastDelay = Integer.MIN_VALUE;

                  for (int i = 0; i < this.cachedDisplayQueue.size(); i++) {
                     RiptideSharedState.QueuedPacket packet = this.cachedDisplayQueue.get(i);
                     int itemY = baseY + i * this.rowHeight();
                     int rowY = itemY - 1;
                     if (rowY + this.rowHeight() > clipTop && rowY < clipBottom) {
                        boolean grouped = false;
                        if (i > 0 && this.cachedDisplayQueue.get(i - 1).getDelay() == packet.getDelay()) {
                           grouped = true;
                        }

                        if (i < this.cachedDisplayQueue.size() - 1 && this.cachedDisplayQueue.get(i + 1).getDelay() == packet.getDelay()) {
                           grouped = true;
                        }

                        int textColor = RiptideColors.textPrimary();
                        if (grouped) {
                           if (packet.getDelay() != lastDelay) {
                              groupColorIndex++;
                           }

                           textColor = groupColorIndex % 2 == 0 ? -12268289 : -21948;
                        }

                        lastDelay = packet.getDelay();
                        int rowX = listX + 1;
                        int rowW = Math.max(36, listContentWidth - 2);
                        boolean selected = selectedPacket != null && selectedPacket.getId() == packet.getId();
                        boolean hovered = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= rowY && mouseY < rowY + this.rowHeight();
                        CompactListRenderer.drawRow(
                           context,
                           this.textRenderer,
                           "",
                           rowX,
                           rowY,
                           rowW,
                           this.rowHeight(),
                           hovered,
                           selected,
                           grouped ? CompactListRenderer.RowTone.WARNING : CompactListRenderer.RowTone.NORMAL
                        );
                        String label = "#" + packet.getId() + " " + RiptidePacketNamer.getFriendlyName(packet.packet);
                        String delayText = String.valueOf(packet.getDelay());
                        int labelMaxWidth = this.getRowLabelMaxWidth(rowX, rowW);
                        String trimmed = UiText.width(this.textRenderer, label, this.theme.fontFor(UiTone.BODY), textColor) > labelMaxWidth
                           ? UiText.trimToWidth(this.textRenderer, label, Math.max(1, labelMaxWidth), this.theme.fontFor(UiTone.BODY), textColor)
                           : label;
                        int rowTextY = UiSizing.alignTextY(rowY, this.rowHeight(), this.theme.fontHeight(UiTone.BODY), this.theme.bodyTextNudge());
                        UiText.draw(
                           context,
                           this.textRenderer,
                           trimmed,
                           this.theme.fontFor(UiTone.BODY),
                           selected ? RiptideColors.rowSelectedText() : textColor,
                           rowX + 7,
                           rowTextY,
                           false
                        );
                        int delayBtnWidth = this.getDelayButtonWidth();
                        int delayBtnX = this.getDelayButtonX(rowX, rowW);
                        int buttonY = rowY + Math.max(0, (this.rowHeight() - this.rowButtonSize()) / 2);
                        int modeBtnW = 14;
                        int modeBtnX = delayBtnX - modeBtnW - 2;
                        boolean exact = packet.isExactReplay();
                        CompactOverlayControls.action(
                           context,
                           this.textRenderer,
                           modeBtnX,
                           buttonY,
                           modeBtnW,
                           this.rowButtonSize(),
                           exact ? "E" : "R",
                           exact ? CompactOverlayButton.Variant.DANGER : CompactOverlayButton.Variant.SUCCESS,
                           true,
                           mouseX,
                           mouseY
                        );
                        CompactOverlayControls.action(
                           context,
                           this.textRenderer,
                           delayBtnX,
                           buttonY,
                           delayBtnWidth,
                           this.rowButtonSize(),
                           delayText,
                           CompactOverlayButton.Variant.PRIMARY,
                           true,
                           mouseX,
                           mouseY
                        );
                        int removeBtnX = this.getRemoveButtonX(rowX, rowW);
                        boolean removeHovered = mouseX >= removeBtnX
                           && mouseX < removeBtnX + this.rowButtonSize()
                           && mouseY >= buttonY
                           && mouseY < buttonY + this.rowButtonSize();
                        CompactListRenderer.drawDeleteButton(context, removeBtnX, buttonY, this.rowButtonSize(), removeHovered);
                        CompactListRenderer.drawDivider(context, rowX, rowY + this.rowHeight(), rowW);
                        this.rowRegions
                           .add(
                              new RiptideQueueEditorOverlay.RowRegion(
                                 packet, rowX, rowY, rowW, this.rowHeight(), delayBtnX, delayBtnWidth, removeBtnX, this.rowButtonSize(), modeBtnX, modeBtnW
                              )
                           );
                     }
                  }

                  UiScissorStack.global().pop(context);
                  CompactScrollbar.draw(context, scrollbarMetrics, scrollbarMetrics.contains(mouseX, mouseY), this.scrollbarDragging);
               }
            } finally {
               this.endWindowBodyClip(context, clipBody);
               this.renderWindowInactiveOverlay(context, bounds, this.collapsed, this.isDragging);
            }

            if (!this.collapsed && this.hoveredTooltip != null) {
               context.nextStratum();
               this.drawTooltip(context, this.hoveredTooltip, this.tooltipX, this.tooltipY);
            }
         }
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.visible) {
         return false;
      } else {
         RiptideSharedState shared = RiptideSharedState.get();
         List<RiptideSharedState.QueuedPacket> displayQueue = this.cachedDisplayQueue.isEmpty() ? this.getCurrentQueue() : this.cachedDisplayQueue;
         int panelHeight = this.collapsed
            ? 16
            : this.clampToScreen(
                  this, new RiptideWindowLayout(this.panelX, this.panelY, this.PANEL_WIDTH, this.getPanelHeight(displayQueue), this.visible, this.collapsed)
               )
               .height;
         if (button == 0 && mouseX >= this.panelX && mouseX <= this.panelX + this.PANEL_WIDTH && mouseY >= this.panelY && mouseY <= this.panelY + 16) {
            RiptideWindowLayout bounds = new RiptideWindowLayout(this.panelX, this.panelY, this.PANEL_WIDTH, panelHeight, this.visible, this.collapsed);
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
            if (button == 0) {
               for (RiptideQueueEditorOverlay.ClickRegion region : this.toolbarRegions) {
                  if (region.contains(mouseX, mouseY)) {
                     region.action.run();
                     return true;
                  }
               }
            }

            CompactScrollbar.Metrics scrollbarMetrics = this.getScrollbarMetrics(panelHeight);
            if (button == 0 && scrollbarMetrics.hasScroll() && scrollbarMetrics.contains(mouseX, mouseY)) {
               this.scrollbarDragging = true;
               this.scrollbarGrabOffset = scrollbarMetrics.overThumb(mouseX, mouseY)
                  ? (int)Math.round(mouseY) - scrollbarMetrics.thumbY()
                  : scrollbarMetrics.thumbHeight() / 2;
               this.scrollOffset = this.quantizeScrollOffset(
                  CompactScrollbar.scrollFromThumb(scrollbarMetrics, mouseY, this.scrollbarGrabOffset), this.rowHeight(), scrollbarMetrics.maxScroll()
               );
               this.listScrollState.jumpTo(this.scrollOffset, scrollbarMetrics.maxScroll());
               return true;
            } else {
               for (RiptideQueueEditorOverlay.RowRegion regionx : this.rowRegions) {
                  if (regionx.contains(mouseX, mouseY)) {
                     if (regionx.overRemove(mouseX, mouseY) && button == 0) {
                        shared.removeQueuedPacket(regionx.packet);
                        if (this.selectedPacketId == regionx.packet.getId()) {
                           this.clearSelection();
                        }

                        return true;
                     }

                     if (regionx.overMode(mouseX, mouseY) && button == 0) {
                        RiptideSharedState.ReplayMode next = regionx.packet.isExactReplay()
                           ? RiptideSharedState.ReplayMode.REGENERATE
                           : RiptideSharedState.ReplayMode.EXACT;
                        regionx.packet.setReplayMode(next);
                        return true;
                     }

                     if (regionx.overDelay(mouseX, mouseY)) {
                        if (button == 0) {
                           shared.updatePacketDelay(regionx.packet, regionx.packet.getDelay() + 1);
                           return true;
                        }

                        if (button == 1) {
                           shared.updatePacketDelay(regionx.packet, Math.max(0, regionx.packet.getDelay() - 1));
                           return true;
                        }
                     }

                     if (button == 0) {
                        this.selectedPacketId = this.selectedPacketId == regionx.packet.getId() ? -1 : regionx.packet.getId();
                        return true;
                     }
                  }
               }

               return this.isMouseOver(mouseX, mouseY);
            }
         }
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (this.isDragging && button == 0) {
         RiptideWindowLayout nextBounds = this.clampToScreen(
            this,
            new RiptideWindowLayout(
               (int)(mouseX - this.dragOffsetX), (int)(mouseY - this.dragOffsetY), this.PANEL_WIDTH, this.PANEL_HEIGHT, this.visible, this.collapsed
            )
         );
         this.panelX = nextBounds.x;
         this.panelY = nextBounds.y;
         return true;
      } else if (this.scrollbarDragging && button == 0) {
         List<RiptideSharedState.QueuedPacket> displayQueue = this.cachedDisplayQueue.isEmpty() ? this.getCurrentQueue() : this.cachedDisplayQueue;
         int panelHeight = this.collapsed
            ? 16
            : this.clampToScreen(
                  this, new RiptideWindowLayout(this.panelX, this.panelY, this.PANEL_WIDTH, this.getPanelHeight(displayQueue), this.visible, this.collapsed)
               )
               .height;
         CompactScrollbar.Metrics scrollbarMetrics = this.getScrollbarMetrics(panelHeight);
         this.scrollOffset = this.quantizeScrollOffset(
            CompactScrollbar.scrollFromThumb(scrollbarMetrics, mouseY, this.scrollbarGrabOffset), this.rowHeight(), scrollbarMetrics.maxScroll()
         );
         this.listScrollState.jumpTo(this.scrollOffset, scrollbarMetrics.maxScroll());
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (button == 0 && this.isDragging) {
         this.isDragging = false;
         this.saveState();
         return true;
      } else if (button == 0 && this.scrollbarDragging) {
         this.scrollbarDragging = false;
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (this.visible && !this.collapsed && this.isMouseOver(mouseX, mouseY)) {
         List<RiptideSharedState.QueuedPacket> displayQueue = this.cachedDisplayQueue.isEmpty() ? this.getCurrentQueue() : this.cachedDisplayQueue;
         int panelHeight = this.clampToScreen(
               this, new RiptideWindowLayout(this.panelX, this.panelY, this.PANEL_WIDTH, this.getPanelHeight(displayQueue), this.visible, this.collapsed)
            )
            .height;
         int visibleHeight = this.getlistViewportHeight(panelHeight);
         int maxScroll = Math.max(0, displayQueue.size() * this.rowHeight() - visibleHeight);
         if (maxScroll <= 0) {
            return false;
         } else {
            this.scrollOffset = this.quantizeScrollOffset(this.scrollOffset - (int)(Math.signum(amount) * this.rowHeight()), this.rowHeight(), maxScroll);
            return true;
         }
      } else {
         return false;
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      return false;
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      return false;
   }

   private void handleSyncButton() {
      if (!this.configurationOnly) {
         RiptideLANSync sync = RiptideLANSync.getInstance();
         if (!sync.isInSession()) {
            RiptideClientMessaging.sendPrefixed("§cNot in LAN session");
         } else {
            List<RiptideSharedState.QueuedPacket> queue = this.getCurrentQueue();
            if (queue.isEmpty()) {
               RiptideClientMessaging.sendPrefixed("§cQueue is empty");
            } else {
               String queueData = RiptideClipboardHelper.serializeQueueToBase64(queue);
               if (queueData == null) {
                  RiptideClientMessaging.sendPrefixed("§cFailed to serialize queue");
               } else {
                  sync.broadcastQueueSync(queueData);
                  RiptideClientMessaging.sendPrefixed("§aQueue sent to " + sync.getConnectedClientCount() + " clients");
               }
            }
         }
      }
   }

   private void checkBtnHover(int x, int y, int w, int h, int mx, int my, String tip) {
      if (mx >= x && mx < x + w && my >= y && my < y + h) {
         this.hoveredTooltip = tip;
         this.tooltipX = mx + 8;
         this.tooltipY = my + 12;
      }
   }

   private void drawTooltip(GuiGraphicsExtractor ctx, String text, int x, int y) {
      Tooltip.render(UiContexts.overlay(ctx, this.textRenderer, x, y), text, x, y, 200);
   }

   private void handleIncrementalDelay(int increment) {
      List<RiptideSharedState.QueuedPacket> queue = this.getCurrentQueue();
      if (queue.isEmpty()) {
         RiptideClientMessaging.sendPrefixed("§cQueue is empty");
      } else if (this.selectedPacketId != -1) {
         for (RiptideSharedState.QueuedPacket packet : queue) {
            if (packet.getId() == this.selectedPacketId) {
               packet.setDelay(packet.getDelay() + increment);
               String modeStr = RiptideSharedState.get().getQueueDisplayDelayMode() == RiptideSharedState.DelayMode.TICKS ? "ticks" : "ms";
               RiptideClientMessaging.sendPrefixed("§aPacket #" + packet.getId() + " delay: " + packet.getDelay() + " " + modeStr);
               return;
            }
         }

         this.clearSelection();
         RiptideClientMessaging.sendPrefixed("§cSelected packet not found");
      } else {
         for (int i = 0; i < queue.size(); i++) {
            queue.get(i).setDelay(i * increment);
         }

         RiptideClientMessaging.sendPrefixed("§aApplied +" + increment + " incremental delays");
      }
   }

   private int defaultPanelWidth() {
      return 248;
   }

   private int defaultPanelHeight() {
      return 176;
   }

   private int minimumPanelHeight() {
      return 168;
   }

   private int rowHeight() {
      return 14;
   }

   private int rowButtonSize() {
      return 12;
   }

   private int toolbarButtonHeight() {
      return 13;
   }

   private int maxVisibleRows() {
      return 6;
   }

   private int fixedDelayButtonWidth() {
      return 40;
   }

   private int listScrollbarGutter() {
      return 12;
   }

   private int panelInset() {
      return 4;
   }

   private int toolbarGap() {
      return 2;
   }

   private int toolbarTopOffset() {
      return 20;
   }

   private int contentTopGap() {
      return 6;
   }

   private int contentBottomGap() {
      return 6;
   }

   private static class ClickRegion {
      final int x;
      final int y;
      final int width;
      final int height;
      final Runnable action;

      private ClickRegion(int x, int y, int width, int height, Runnable action) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         this.action = action;
      }

      private boolean contains(double mouseX, double mouseY) {
         return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
      }
   }

   private static class RowRegion {
      final RiptideSharedState.QueuedPacket packet;
      final int x;
      final int y;
      final int width;
      final int height;
      final int delayX;
      final int delayWidth;
      final int removeX;
      final int removeWidth;
      final int modeX;
      final int modeWidth;

      private RowRegion(
         RiptideSharedState.QueuedPacket packet,
         int x,
         int y,
         int width,
         int height,
         int delayX,
         int delayWidth,
         int removeX,
         int removeWidth,
         int modeX,
         int modeWidth
      ) {
         this.packet = packet;
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         this.delayX = delayX;
         this.delayWidth = delayWidth;
         this.removeX = removeX;
         this.removeWidth = removeWidth;
         this.modeX = modeX;
         this.modeWidth = modeWidth;
      }

      private boolean contains(double mouseX, double mouseY) {
         return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
      }

      private boolean overDelay(double mouseX, double mouseY) {
         return this.contains(mouseX, mouseY) && mouseX >= this.delayX && mouseX < this.delayX + this.delayWidth;
      }

      private boolean overRemove(double mouseX, double mouseY) {
         return this.contains(mouseX, mouseY) && mouseX >= this.removeX && mouseX < this.removeX + this.removeWidth;
      }

      private boolean overMode(double mouseX, double mouseY) {
         return this.contains(mouseX, mouseY) && mouseX >= this.modeX && mouseX < this.modeX + this.modeWidth;
      }
   }
}
