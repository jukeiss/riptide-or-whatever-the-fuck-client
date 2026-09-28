package riptide.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.gui.vanillaui.TextWrapLayout;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.CompactListRenderer;
import riptide.gui.vanillaui.components.CompactOverlayButton;
import riptide.gui.vanillaui.components.CompactOverlayControls;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.InspectorLayout;
import riptide.gui.vanillaui.components.ProgressBar;
import riptide.gui.vanillaui.components.UiSizing;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectLayout;
import riptide.gui.vanillaui.direct.DirectScrollViewport;

public class RiptidePacketInspectOverlay extends RiptideOverlayBase {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final int CONTENT_RESERVE = 0;
   private static final int AUTO_WIDTH_STEP = 8;
   private final Font textRenderer;
   private final CompactTheme theme = new CompactTheme();
   private boolean visible;
   private boolean collapsed;
   private boolean dragging;
   private double dragOffsetX;
   private double dragOffsetY;
   private int panelX = 120;
   private int panelY = 50;
   private int panelWidth = 220;
   private int panelHeight = 173;
   private RiptidePacketInspector.PacketInspection inspection;
   private RiptidePacketLoggerOverlay.LogEntry sourceEntry;
   private List<RiptidePacketInspectOverlay.WrappedInspectionLine> wrappedLines = Collections.emptyList();
   private boolean wrapDirty = true;
   private int wrappedWidth = -1;
   private DirectScrollViewport listViewport = null;
   private String currentScrollKey = "";
   private int pendingScrollOffset = 0;
   private AtomicReference<RiptidePacketInspector.PayloadAnalysisView> payloadAnalysisRef;
   private RiptidePacketInspector.PayloadAnalysisView currentPayloadAnalysis;
   private CompletableFuture<?> payloadAnalysisFuture;
   private String payloadAnalysisKey = "";
   private boolean payloadAnalysisStarted;
   private static final int INSPECT_SCROLLBAR_WIDTH = 4;
   private static final int MAX_REMEMBERED_SCROLLS = 128;
   private static final Map<String, Integer> REMEMBERED_SCROLL_OFFSETS = new LinkedHashMap<String, Integer>() {
      @Override
      protected boolean removeEldestEntry(Entry<String, Integer> eldest) {
         return this.size() > 128;
      }
   };

   public RiptidePacketInspectOverlay(Font textRenderer) {
      this.textRenderer = textRenderer;
      this.panelWidth = this.defaultPanelWidth();
      this.panelHeight = this.defaultPanelHeight();
   }

   public void open(RiptidePacketInspector.PacketInspection inspection, RiptidePacketLoggerOverlay.LogEntry sourceEntry, int anchorX, int anchorY) {
      if (inspection != null) {
         RiptideOverlayManager manager = RiptideOverlayManager.get();
         manager.register(this);
         this.rememberScrollOffset();
         this.clearPayloadAnalysisState();
         this.inspection = inspection;
         this.sourceEntry = sourceEntry;
         this.payloadAnalysisStarted = false;
         this.visible = true;
         this.collapsed = false;
         this.currentScrollKey = this.scrollKey(inspection, sourceEntry);
         this.pendingScrollOffset = REMEMBERED_SCROLL_OFFSETS.getOrDefault(this.currentScrollKey, 0);
         this.listViewport = null;
         this.wrapDirty = true;
         this.startPayloadAnalysisAutomatically();
         if (sourceEntry != null && this.currentPayloadAnalysis != null) {
            this.inspection = this.buildInspectionForActiveTab();
         }

         this.fitWindowToInspection(anchorX, anchorY);
         manager.bringToFront(this);
      }
   }

   public void open(RiptidePacketLoggerOverlay.LogEntry sourceEntry, int anchorX, int anchorY) {
      if (sourceEntry != null) {
         RiptideOverlayManager manager = RiptideOverlayManager.get();
         manager.register(this);
         this.rememberScrollOffset();
         this.clearPayloadAnalysisState();
         this.sourceEntry = sourceEntry;
         this.payloadAnalysisStarted = false;
         this.visible = true;
         this.collapsed = false;
         this.currentScrollKey = this.scrollKey(null, sourceEntry);
         this.pendingScrollOffset = REMEMBERED_SCROLL_OFFSETS.getOrDefault(this.currentScrollKey, 0);
         this.listViewport = null;
         this.wrapDirty = true;
         this.startPayloadAnalysisAutomatically();
         this.inspection = this.buildInspectionForActiveTab();
         this.fitWindowToInspection(anchorX, anchorY);
         manager.bringToFront(this);
      }
   }

   public void close() {
      this.rememberScrollOffset();
      this.visible = false;
      this.dragging = false;
      this.sourceEntry = null;
      this.clearPayloadAnalysisState();
   }

   private void clearPayloadAnalysisState() {
      this.payloadAnalysisKey = "";
      this.payloadAnalysisRef = null;
      this.currentPayloadAnalysis = null;
      this.payloadAnalysisStarted = false;
      if (this.payloadAnalysisFuture != null) {
         this.payloadAnalysisFuture.cancel(true);
         this.payloadAnalysisFuture = null;
      }
   }

   private void startPayloadAnalysisAutomatically() {
      if (this.sourceEntry != null) {
         RiptidePayloadSupport.PayloadSnapshot snapshot = RiptidePayloadSupport.snapshotFromEntry(this.sourceEntry);
         if (snapshot != null) {
            if (!this.payloadAnalysisStarted || this.payloadAnalysisFuture == null || this.payloadAnalysisFuture.isDone()) {
               this.payloadAnalysisStarted = true;
               String baseKey = this.scrollKey(null, this.sourceEntry);
               this.currentPayloadAnalysis = RiptidePacketInspector.payloadAnalysisLoading(snapshot);
               this.payloadAnalysisRef = new AtomicReference<>(this.currentPayloadAnalysis);
               this.payloadAnalysisKey = baseKey;
               this.startPayloadAnalysis(snapshot, baseKey);
            }
         }
      }
   }

   private void startPayloadAnalysis(RiptidePayloadSupport.PayloadSnapshot snapshot, String analysisKey) {
      if (snapshot != null && analysisKey != null && !analysisKey.isBlank()) {
         this.payloadAnalysisFuture = CompletableFuture.runAsync(
            () -> {
               try {
                  RiptidePacketInspector.PayloadAnalysisView result = RiptidePacketInspector.analyzePayload(
                     snapshot, view -> this.publishPayloadAnalysis(analysisKey, view)
                  );
                  this.publishPayloadAnalysis(analysisKey, result);
               } catch (Throwable var4) {
                  this.publishPayloadAnalysis(analysisKey, RiptidePacketInspector.payloadAnalysisFailed(RiptidePayloadSupport.safeMessage(var4)));
               }
            }
         );
      }
   }

   private void publishPayloadAnalysis(String analysisKey, RiptidePacketInspector.PayloadAnalysisView view) {
      AtomicReference<RiptidePacketInspector.PayloadAnalysisView> ref = this.payloadAnalysisRef;
      if (ref != null && view != null && analysisKey.equals(this.payloadAnalysisKey)) {
         ref.set(view);
      }
   }

   private void refreshPayloadAnalysisInspection() {
      if (this.payloadAnalysisRef != null && this.sourceEntry != null) {
         RiptidePacketInspector.PayloadAnalysisView next = this.payloadAnalysisRef.get();
         if (next != null && next != this.currentPayloadAnalysis) {
            int oldScroll = this.listViewport != null ? this.listViewport.getScrollOffset() : this.pendingScrollOffset;
            this.currentPayloadAnalysis = next;
            this.inspection = this.buildInspectionForActiveTab();
            this.pendingScrollOffset = Math.max(0, oldScroll);
            this.wrapDirty = true;
            if (this.listViewport != null) {
               this.listViewport.jumpTo(this.pendingScrollOffset);
            }
         }
      }
   }

   private RiptidePacketInspector.PacketInspection buildInspectionForActiveTab() {
      return this.sourceEntry == null ? this.inspection : RiptidePacketInspector.inspectSafe(this.sourceEntry, this.currentPayloadAnalysis, true);
   }

   private void rebuildInspectionForActiveTab(boolean keepScroll) {
      this.rememberScrollOffset();
      int oldScroll = keepScroll && this.listViewport != null ? this.listViewport.getScrollOffset() : 0;
      this.currentScrollKey = this.scrollKey(null, this.sourceEntry);
      this.pendingScrollOffset = keepScroll ? Math.max(0, oldScroll) : REMEMBERED_SCROLL_OFFSETS.getOrDefault(this.currentScrollKey, 0);
      this.listViewport = null;
      this.wrappedLines = Collections.emptyList();
      this.wrapDirty = true;
      this.inspection = this.buildInspectionForActiveTab();
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      if (this.visible) {
         this.refreshPayloadAnalysisInspection();
         RiptideWindowLayout bounds = this.getBounds();
         String title = this.inspection == null ? "Packet Inspect" : this.inspection.getTitle();
         this.renderWindowFrame(context, mouseX, mouseY, bounds, title, this.collapsed, this.dragging);
         boolean clipBody = this.beginWindowBodyClip(context, bounds, this.collapsed);
         if (!clipBody) {
            this.renderWindowInactiveOverlay(context, bounds, this.collapsed, this.dragging);
         } else {
            try {
               int listX = this.panelX + this.outerPad();
               int tabsHeight = this.tabStripHeight();
               int tabsY = this.panelY + 16 + this.bodyTopGap();
               if (tabsHeight > 0) {
                  this.renderTabStrip(context, listX, tabsY, this.panelWidth - this.outerPad() * 2, mouseX, mouseY);
               }

               int progressHeight = this.analysisProgressHeight();
               int progressY = tabsY + tabsHeight;
               if (progressHeight > 0) {
                  this.renderPayloadAnalysisProgress(
                     context, listX, progressY, this.panelWidth - this.outerPad() * 2, progressHeight, this.currentPayloadAnalysis
                  );
               }

               int listY = progressY + progressHeight;
               int listWidth = this.panelWidth - this.outerPad() * 2;
               int listHeight = Math.max(1, this.panelHeight - 16 - this.footerHeight() - this.bodyVerticalGap() - tabsHeight - progressHeight);
               int innerX = listX + this.innerPad();
               int innerY = listY + this.innerPad();
               int innerWidth = InspectorLayout.contentWidth(listWidth, this.innerPad());
               int innerHeight = listHeight - this.innerPad() * 2;
               int lineH = this.lineHeight();
               if (!RiptideOverlayManager.get().isFocusedOverlay(this) && !RiptideOverlayManager.get().isTopOverlay(this)) {
                  boolean var35 = false;
               } else {
                  boolean var10000 = true;
               }

               this.ensureWrappedLines(innerWidth);
               if (this.wrappedLines.isEmpty()) {
                  CompactListRenderer.drawEmptyState(context, this.textRenderer, "No inspection data.", listX, listY, listWidth);
               } else {
                  int contentHeight = this.wrappedLines.size() * lineH;
                  if (this.listViewport == null
                     || this.listViewport.getX() != listX
                     || this.listViewport.getY() != listY
                     || this.listViewport.getWidth() != listWidth
                     || this.listViewport.getHeight() != listHeight) {
                     int previousScrollOffset = this.listViewport != null ? this.listViewport.getScrollOffset() : this.pendingScrollOffset;
                     this.listViewport = new DirectScrollViewport(listX, listY, listWidth, listHeight, lineH, 4);
                     this.listViewport.setContentHeight(contentHeight);
                     this.listViewport.jumpTo(previousScrollOffset);
                     this.pendingScrollOffset = this.listViewport.getScrollOffset();
                  }

                  this.listViewport.setContentHeight(contentHeight);
                  if (this.pendingScrollOffset > 0 && this.listViewport.getScrollOffset() == 0) {
                     this.listViewport.jumpTo(this.pendingScrollOffset);
                  }

                  this.listViewport.beginRender(context, this.theme.borderSoft(), this.theme.listFill());

                  try {
                     this.listViewport
                        .renderSimple(
                           context,
                           this.wrappedLines.size(),
                           (idx, bnd) -> {
                              RiptidePacketInspectOverlay.WrappedInspectionLine ln = this.wrappedLines.get(idx);
                              this.drawInspectionLine(
                                 context, ln, innerX, UiSizing.alignTextY(bnd.y, lineH, this.theme.fontHeight(UiTone.BODY), this.theme.bodyTextNudge())
                              );
                           }
                        );
                  } finally {
                     this.listViewport.endRender(context);
                  }

                  this.listViewport.renderScrollbar(context, mouseX, mouseY);
               }

               int footerY = this.panelY + this.panelHeight - this.footerHeight() + this.footerTopInset();
               UiText.draw(
                  context,
                  this.textRenderer,
                  this.wrappedLines.size() + " lines",
                  this.theme.fontFor(UiTone.MUTED),
                  this.theme.color(UiTone.MUTED),
                  this.panelX + this.outerPad() + 5,
                  footerY + this.footerLabelInset(),
                  false
               );

               for (RiptidePacketInspectOverlay.FooterButton button : this.buildFooterButtons(footerY)) {
                  CompactOverlayButton.Variant variant = switch (button.action()) {
                     case WAIT, QUEUE, ADD_PAYLOAD -> CompactOverlayButton.Variant.SECONDARY;
                     case SEND -> CompactOverlayButton.Variant.SUCCESS;
                     case EDIT_PAYLOAD -> CompactOverlayButton.Variant.PRIMARY;
                     case COPY_RAW -> CompactOverlayButton.Variant.PRIMARY;
                     case COPY -> CompactOverlayButton.Variant.GHOST;
                  };
                  CompactOverlayControls.action(
                     context, this.textRenderer, button.x(), button.y(), button.width(), this.buttonHeight(), button.label(), variant, true, mouseX, mouseY
                  );
               }
            } finally {
               this.endWindowBodyClip(context, clipBody);
               this.renderWindowInactiveOverlay(context, bounds, this.collapsed, this.dragging);
            }
         }
      }
   }

   private void ensureWrappedLines(int innerWidth) {
      if (this.wrapDirty || this.wrappedWidth != innerWidth) {
         if (this.inspection == null) {
            this.wrappedLines = Collections.emptyList();
            this.wrapDirty = false;
            this.wrappedWidth = innerWidth;
         } else {
            List<RiptidePacketInspectOverlay.WrappedInspectionLine> wrapped = new ArrayList<>();

            for (RiptidePacketInspector.InspectionLine line : this.inspection.getLines()) {
               this.wrapLine(line, innerWidth, wrapped);
            }

            this.wrappedLines = wrapped;
            this.wrapDirty = false;
            this.wrappedWidth = innerWidth;
         }
      }
   }

   private void wrapLine(RiptidePacketInspector.InspectionLine line, int maxWidth, List<RiptidePacketInspectOverlay.WrappedInspectionLine> target) {
      String text = line.getText();
      if (text == null || text.isEmpty()) {
         target.add(RiptidePacketInspectOverlay.WrappedInspectionLine.plain("", 0, line.getColor()));
      } else if (this.isSectionLine(text)) {
         this.wrapPlainText(text, 0, line.getColor(), maxWidth, target);
      } else {
         int leadingSpaces = this.countLeadingSpaces(text);
         String indentComponent = " ".repeat(leadingSpaces);
         int indentWidth = this.styledWidth(indentComponent);
         int colonIndex = text.indexOf(58, leadingSpaces);
         if (colonIndex > leadingSpaces && colonIndex < text.length() - 1) {
            String label = text.substring(leadingSpaces, colonIndex + 1).trim();
            String value = text.substring(colonIndex + 1).stripLeading();
            String prefix = indentComponent + label + " ";
            int prefixWidth = this.styledWidth(prefix);
            int continuationOffset = Math.min(prefixWidth, indentWidth + this.continuationIndent());
            this.wrapKeyValue(prefix, value, line.getColor(), prefixWidth, continuationOffset, maxWidth, target);
         } else {
            this.wrapPlainText(text.substring(leadingSpaces), indentWidth, line.getColor(), maxWidth, target);
         }
      }
   }

   private void wrapKeyValue(
      String prefix,
      String value,
      int valueColor,
      int prefixWidth,
      int continuationOffset,
      int maxWidth,
      List<RiptidePacketInspectOverlay.WrappedInspectionLine> target
   ) {
      int safeContinuationOffset = InspectorLayout.clampTextOffset(maxWidth, continuationOffset);
      if (prefixWidth >= maxWidth) {
         this.wrapPlainText(prefix.stripTrailing(), 0, RiptideColors.packetGray(), maxWidth, target);
         this.wrapPlainText(value, safeContinuationOffset, valueColor, maxWidth, target);
      } else if (value != null && !value.isEmpty()) {
         String remaining = value;

         for (boolean firstLine = true; !remaining.isEmpty(); firstLine = false) {
            int offset = firstLine ? prefixWidth : safeContinuationOffset;
            int availableWidth = InspectorLayout.remainingTextWidth(maxWidth, offset);
            String current = remaining;
            int split = TextWrapLayout.nextLineEnd(
               current, 0, current.length(), availableWidth, (start, end) -> this.styledWidth(current.substring(start, end))
            );
            String part = remaining.substring(0, split).stripTrailing();
            target.add(new RiptidePacketInspectOverlay.WrappedInspectionLine(firstLine ? prefix : null, RiptideColors.packetGray(), part, valueColor, offset));
            remaining = remaining.substring(split).stripLeading();
         }
      } else {
         target.add(new RiptidePacketInspectOverlay.WrappedInspectionLine(prefix, RiptideColors.packetGray(), "", valueColor, prefixWidth));
      }
   }

   private void wrapPlainText(String text, int offset, int color, int maxWidth, List<RiptidePacketInspectOverlay.WrappedInspectionLine> target) {
      if (text != null) {
         if (text.isEmpty()) {
            target.add(RiptidePacketInspectOverlay.WrappedInspectionLine.plain("", offset, color));
         } else {
            int safeOffset = InspectorLayout.clampTextOffset(maxWidth, offset);
            String remaining = text;

            while (!remaining.isEmpty()) {
               int availableWidth = InspectorLayout.remainingTextWidth(maxWidth, safeOffset);
               String current = remaining;
               int split = TextWrapLayout.nextLineEnd(
                  current, 0, current.length(), availableWidth, (start, end) -> this.styledWidth(current.substring(start, end))
               );
               String part = remaining.substring(0, split).stripTrailing();
               target.add(RiptidePacketInspectOverlay.WrappedInspectionLine.plain(part, safeOffset, color));
               remaining = remaining.substring(split).stripLeading();
            }
         }
      }
   }

   private void drawInspectionLine(GuiGraphicsExtractor context, RiptidePacketInspectOverlay.WrappedInspectionLine line, int x, int y) {
      if (line != null && line.valueText() != null) {
         if (line.prefixText() != null && !line.prefixText().isEmpty()) {
            UiText.draw(context, this.textRenderer, line.prefixText(), this.theme.fontFor(UiTone.BODY), line.prefixColor(), x, y, false);
         }

         int valueX = x + line.valueOffset();
         UiText.draw(context, this.textRenderer, line.valueText(), this.theme.fontFor(UiTone.BODY), line.valueColor(), valueX, y, false);
      }
   }

   private void renderPayloadAnalysisProgress(
      GuiGraphicsExtractor context, int x, int y, int width, int height, RiptidePacketInspector.PayloadAnalysisView view
   ) {
      if (view != null && width > 0 && height > 0) {
         int fill = -1441722090;
         UiRenderer.frame(context, UiBounds.of(x, y, width, height - 2), fill, -2007870113);
         String status = view.status() != null && !view.status().isBlank() ? view.status() : "Decoding";
         int percent = Math.max(0, Math.min(100, (int)Math.round(view.progress() * 100.0)));
         String label = "Analyzer: " + status + " - progress " + percent + "%";
         UiText.draw(
            context,
            this.textRenderer,
            label,
            this.theme.fontFor(UiTone.MUTED),
            view.failed() ? RiptideColors.dangerText() : RiptideColors.textSecondary(),
            x + 4,
            y + 2,
            false
         );
         int barX = x + 4;
         int barY = y + height - 8;
         int barWidth = Math.max(1, width - 8);
         int barHeight = 4;
         ProgressBar.render(UiContexts.overlay(context, this.textRenderer, -1, -1), UiBounds.of(barX, barY, barWidth, barHeight), view.progress());
      }
   }

   private void renderTabStrip(GuiGraphicsExtractor context, int x, int y, int width, int mouseX, int mouseY) {
   }

   private boolean isSectionLine(String text) {
      String trimmed = text == null ? "" : text.trim();
      return trimmed.startsWith("[") && trimmed.endsWith("]");
   }

   private int countLeadingSpaces(String text) {
      int count = 0;

      while (count < text.length() && text.charAt(count) == ' ') {
         count++;
      }

      return count;
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.visible) {
         return false;
      } else {
         RiptideWindowLayout bounds = this.getBounds();
         if (button == 0 && mouseY >= this.panelY && mouseY <= this.panelY + 16 && mouseX >= this.panelX && mouseX <= this.panelX + this.panelWidth) {
            if (this.isOverCloseButton(mouseX, mouseY, bounds)) {
               this.close();
               return true;
            } else {
               this.dragging = true;
               this.dragOffsetX = mouseX - this.panelX;
               this.dragOffsetY = mouseY - this.panelY;
               return true;
            }
         } else if (this.collapsed) {
            return true;
         } else {
            int tabsHeight = this.tabStripHeight();
            int progressHeight = this.analysisProgressHeight();
            int listY = this.panelY + 16 + this.bodyTopGap() + tabsHeight + progressHeight;
            int listHeight = Math.max(1, this.panelHeight - 16 - this.footerHeight() - this.bodyVerticalGap() - tabsHeight - progressHeight);
            int listX = this.panelX + this.outerPad();
            int listWidth = this.panelWidth - this.outerPad() * 2;
            if (button == 0
               && this.listViewport != null
               && this.listViewport.contains(mouseX, mouseY)
               && this.listViewport.mouseClicked(mouseX, mouseY, button)) {
               return true;
            } else {
               int footerY = this.panelY + this.panelHeight - this.footerHeight() + this.footerTopInset();
               if (button == 0) {
                  for (RiptidePacketInspectOverlay.FooterButton footerButton : this.buildFooterButtons(footerY)) {
                     if (mouseX >= footerButton.x()
                        && mouseX < footerButton.x() + footerButton.width()
                        && mouseY >= footerButton.y()
                        && mouseY < footerButton.y() + this.buttonHeight()) {
                        this.handleFooterAction(footerButton.action());
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
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (button == 0 && this.dragging) {
         this.dragging = false;
         this.rememberScrollOffset();
         this.saveLayout();
         return true;
      } else if (button == 0 && this.listViewport != null) {
         this.listViewport.mouseReleased();
         this.rememberScrollOffset();
         return true;
      } else {
         this.dragging = false;
         return false;
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (this.listViewport != null) {
         this.listViewport.mouseDragged(mouseX, mouseY);
         this.rememberScrollOffset();
      }

      if (this.visible && button == 0 && this.dragging) {
         this.setBounds(
            new RiptideWindowLayout(
               (int)Math.round(mouseX - this.dragOffsetX),
               (int)Math.round(mouseY - this.dragOffsetY),
               this.panelWidth,
               this.panelHeight,
               this.visible,
               this.collapsed
            )
         );
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (this.visible && !this.collapsed) {
         int tabsHeight = this.tabStripHeight();
         int progressHeight = this.analysisProgressHeight();
         int listY = this.panelY + 16 + this.bodyTopGap() + tabsHeight + progressHeight;
         int listHeight = Math.max(1, this.panelHeight - 16 - this.footerHeight() - this.bodyVerticalGap() - tabsHeight - progressHeight);
         int listX = this.panelX + this.outerPad();
         int listWidth = this.panelWidth - this.outerPad() * 2;
         if (mouseX < listX || mouseX > listX + listWidth || mouseY < listY || mouseY > listY + listHeight) {
            return false;
         } else if (this.listViewport != null) {
            boolean handled = this.listViewport.mouseScrolled(mouseX, mouseY, amount);
            this.rememberScrollOffset();
            return handled;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.visible) {
         return false;
      } else if (keyCode == 256) {
         this.close();
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      return false;
   }

   @Override
   public boolean isVisible() {
      return this.visible;
   }

   @Override
   public void setVisible(boolean visible) {
      this.visible = visible;
      if (!visible) {
         this.close();
      }
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else {
         int height = this.collapsed ? 16 : this.panelHeight;
         return mouseX >= this.panelX && mouseX <= this.panelX + this.panelWidth && mouseY >= this.panelY && mouseY <= this.panelY + height;
      }
   }

   @Override
   public boolean isOverDragBar(double mouseX, double mouseY) {
      return !this.visible
         ? false
         : mouseX >= this.panelX
            && mouseX <= this.panelX + this.panelWidth
            && mouseY >= this.panelY
            && mouseY <= this.panelY + 16
            && !this.isOverWindowControl(mouseX, mouseY, this.getBounds());
   }

   @Override
   public int getZLevel() {
      return 12;
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
   public RiptideWindowLayout getBounds() {
      return new RiptideWindowLayout(this.panelX, this.panelY, this.panelWidth, this.panelHeight, this.visible, this.collapsed);
   }

   @Override
   public void setBounds(RiptideWindowLayout bounds) {
      if (bounds != null) {
         RiptideWindowLayout clamped = this.clampToScreen(this, bounds);
         boolean sizeChanged = this.panelWidth != clamped.width || this.panelHeight != clamped.height;
         this.panelX = clamped.x;
         this.panelY = clamped.y;
         this.panelWidth = clamped.width;
         this.panelHeight = clamped.height;
         this.visible = clamped.visible;
         this.collapsed = clamped.collapsed;
         if (sizeChanged) {
            this.wrapDirty = true;
         }
      }
   }

   @Override
   public int getMinWidth() {
      return this.minimumWidth();
   }

   @Override
   public int getMinHeight() {
      return this.minimumHeight();
   }

   private void handleFooterAction(RiptidePacketInspectOverlay.FooterAction action) {
      if (action != null) {
         switch (action) {
            case WAIT:
               RiptidePacketEntryActions.addWaitActionToVisibleMacro(this.sourceEntry);
               break;
            case SEND:
               this.directSendEntry(this.sourceEntry);
               break;
            case QUEUE:
               RiptidePacketEntryActions.queue(this.sourceEntry);
               break;
            case EDIT_PAYLOAD:
               RiptidePacketEntryActions.openPayloadEditor(this.sourceEntry);
               break;
            case ADD_PAYLOAD:
               RiptidePacketEntryActions.addPayloadActionToVisibleMacro(this.sourceEntry);
               break;
            case COPY_RAW:
               this.copyRawPacketData();
               break;
            case COPY:
               if (this.inspection != null) {
                  MC.keyboardHandler.setClipboard(this.inspection.getCopyText());
                  RiptideNotifications.copied("Copied packet inspection.");
               }
         }
      }
   }

   private void copyRawPacketData() {
      String export = this.buildRawPacketExport(this.sourceEntry);
      if (export != null && !export.isBlank()) {
         MC.keyboardHandler.setClipboard(export);
         RiptideNotifications.copied("Copied raw packet bytes.");
      } else {
         RiptideNotifications.warning("No raw packet bytes were captured.");
      }
   }

   private String buildRawPacketExport(RiptidePacketLoggerOverlay.LogEntry entry) {
      if (entry == null) {
         return "";
      } else {
         RiptidePayloadSupport.PayloadSnapshot payload = RiptidePayloadSupport.snapshotFromEntry(entry);
         RiptidePacketCapture.PacketSnapshot packet = RiptidePacketCapture.snapshot(entry.packetRef);
         byte[] payloadBytes = payload == null ? new byte[0] : payload.rawBytes();
         byte[] packetBytes = packet == null ? new byte[0] : packet.plaintextBytes();
         if (payloadBytes.length == 0 && packetBytes.length == 0) {
            return "";
         } else {
            StringBuilder out = new StringBuilder(Math.max(256, (payloadBytes.length + packetBytes.length) * 4));
            out.append("# Riptide Packet Raw Export\n");
            out.append("Name: ").append(entry.shortName).append('\n');
            out.append("Direction: ").append(entry.direction).append('\n');
            out.append("Class: ").append(entry.packetClass == null ? "unknown" : entry.packetClass.getName()).append('\n');
            out.append("Tick: ").append(entry.gameTick).append('\n');
            out.append("Time: ").append(Instant.ofEpochMilli(entry.timestampMs)).append('\n');
            if (payload != null) {
               out.append("Payload Channel: ").append(payload.channel()).append('\n');
               out.append("Payload Protocol: ").append(payload.protocolPhase()).append('\n');
               out.append("Payload Class: ").append(payload.payloadClassName()).append('\n');
               out.append("Payload Packet ID: ").append(payload.packetId()).append('\n');
            }

            if (packet != null) {
               out.append("Captured Protocol: ").append(packet.protocolPhase()).append('\n');
               out.append("Captured Packet ID: ").append(packet.numericPacketId()).append('\n');
               out.append("Captured Packet Type: ").append(packet.packetType()).append('\n');
            }

            String decoded = RiptideNormalPacketAnalyzer.exportDecodedText(entry);
            if (decoded != null && !decoded.isBlank()) {
               out.append('\n').append(decoded).append('\n');
            }

            this.appendRawByteSection(out, "Payload Body", payloadBytes);
            this.appendRawByteSection(out, "Plaintext Packet", packetBytes);
            return out.toString();
         }
      }
   }

   private void appendRawByteSection(StringBuilder out, String title, byte[] bytes) {
      if (out != null && bytes != null && bytes.length != 0) {
         out.append('\n').append('[').append(title).append("]\n");
         out.append("Length: ").append(bytes.length).append(" bytes\n");
         out.append("Hex: ").append(this.toFullHex(bytes, false)).append('\n');
         out.append("Hex Spaced:\n").append(this.toWrappedHex(bytes, 32)).append('\n');
         out.append("Base64: ").append(Base64.getEncoder().encodeToString(bytes)).append('\n');
         String utf8 = RiptidePayloadSupport.decodeLikelyUtf8Text(bytes);
         if (utf8 != null && !utf8.isBlank()) {
            out.append("UTF-8 Escaped:\n").append(this.escapeClipboardText(utf8)).append('\n');
         }
      }
   }

   private String escapeClipboardText(String text) {
      if (text != null && !text.isEmpty()) {
         StringBuilder sb = new StringBuilder(text.length() + 16);

         for (int i = 0; i < text.length(); i++) {
            char chr = text.charAt(i);
            switch (chr) {
               case '\u0000':
                  sb.append("\\0\n");
                  break;
               case '\t':
                  sb.append("\\t");
                  break;
               case '\n':
                  sb.append('\n');
                  break;
               case '\r':
                  if (i + 1 >= text.length() || text.charAt(i + 1) != '\n') {
                     sb.append('\n');
                  }
                  break;
               default:
                  if (Character.isISOControl(chr)) {
                     sb.append(String.format("\\u%04X", Integer.valueOf(chr)));
                  } else {
                     sb.append(chr);
                  }
            }
         }

         return sb.toString();
      } else {
         return "";
      }
   }

   private String toFullHex(byte[] bytes, boolean spaced) {
      if (bytes != null && bytes.length != 0) {
         StringBuilder sb = new StringBuilder(bytes.length * (spaced ? 3 : 2));

         for (int i = 0; i < bytes.length; i++) {
            if (spaced && i > 0) {
               sb.append(' ');
            }

            this.appendHexByte(sb, bytes[i] & 255);
         }

         return sb.toString();
      } else {
         return "";
      }
   }

   private String toWrappedHex(byte[] bytes, int bytesPerLine) {
      if (bytes != null && bytes.length != 0) {
         int perLine = Math.max(1, bytesPerLine);
         StringBuilder sb = new StringBuilder(bytes.length * 3 + bytes.length / perLine + 8);

         for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
               sb.append((char)(i % perLine == 0 ? '\n' : ' '));
            }

            this.appendHexByte(sb, bytes[i] & 255);
         }

         return sb.toString();
      } else {
         return "";
      }
   }

   private void appendHexByte(StringBuilder sb, int value) {
      char[] digits = "0123456789ABCDEF".toCharArray();
      sb.append(digits[value >>> 4 & 15]);
      sb.append(digits[value & 15]);
   }

   private void directSendEntry(RiptidePacketLoggerOverlay.LogEntry entry) {
      if (entry != null && entry.packetRef != null && "C2S".equalsIgnoreCase(entry.direction)) {
         RiptidePacketEntryActions.directSend(entry);
      } else {
         RiptideClientMessaging.sendPrefixed("§cOnly C2S packets can be sent.");
      }
   }

   private void fitWindowToInspection(int anchorX, int anchorY) {
      int screenWidth = MC.getWindow() != null ? RiptideUiScale.getVirtualScreenWidth() : 600;
      int screenHeight = MC.getWindow() != null ? RiptideUiScale.getVirtualScreenHeight() : 400;
      int lineHeight = this.lineHeight();
      int lineCount = this.inspection == null ? 0 : this.inspection.getLines().size();
      int footerInfoWidth = this.styledWidth(lineCount + " lines") + this.outerPad() + 15;
      int footerButtonsWidth = this.computeFooterButtonsWidth();
      int titleWidth = this.styledWidth(this.inspection == null ? "Packet Inspect" : this.inspection.getTitle()) + this.titleWidthPadding();
      int minWidth = Math.max(this.minimumWidth(), Math.max(titleWidth, footerInfoWidth + footerButtonsWidth + this.footerContentPadding()));
      int maxWidth = Math.max(minWidth, Math.min(this.maxAutoWidth(), screenWidth - 12));
      int maxHeight = Math.max(this.minimumHeight(), Math.min(this.maxAutoHeight(), screenHeight - 12));
      int desiredWidth = this.chooseBestAutoWidth(minWidth, maxWidth);
      RiptidePacketInspectOverlay.FitMetrics fit = this.measureFitForPanelWidth(desiredWidth);
      int desiredVisibleLines = Math.max(3, Math.min(fit.lineCount(), this.maxVisibleLines()));
      int desiredListHeight = Math.max(this.listMinimumHeight(), desiredVisibleLines * lineHeight + this.innerPad() * 2 + 2);
      int desiredHeight = 16
         + this.bodyTopGap()
         + this.tabStripHeight()
         + this.analysisProgressHeight()
         + desiredListHeight
         + this.footerHeight()
         + this.footerBottomGap();
      desiredHeight = Math.min(maxHeight, Math.max(this.minimumHeight(), desiredHeight));
      this.setBounds(new RiptideWindowLayout(anchorX, anchorY, desiredWidth, desiredHeight, true, false));
   }

   private void rememberScrollOffset() {
      if (this.currentScrollKey != null && !this.currentScrollKey.isEmpty()) {
         int offset = this.listViewport != null ? this.listViewport.getScrollOffset() : this.pendingScrollOffset;
         this.pendingScrollOffset = Math.max(0, offset);
         REMEMBERED_SCROLL_OFFSETS.put(this.currentScrollKey, this.pendingScrollOffset);
      }
   }

   private String scrollKey(RiptidePacketInspector.PacketInspection inspection, RiptidePacketLoggerOverlay.LogEntry entry) {
      if (entry != null) {
         String className = entry.packetClass == null ? "" : entry.packetClass.getName();
         return entry.direction + "|" + entry.shortName + "|" + className + "|" + entry.timestampMs + "|" + entry.gameTick;
      } else {
         return inspection == null ? "" : inspection.getTitle();
      }
   }

   private int chooseBestAutoWidth(int minPanelWidth, int maxPanelWidth) {
      int rawLineCount = this.inspection == null ? 0 : this.inspection.getLines().size();
      int bestWidth = minPanelWidth;
      long bestScore = Long.MAX_VALUE;

      for (int width = minPanelWidth; width <= maxPanelWidth; width += 8) {
         long score = this.scoreAutoWidth(width, rawLineCount);
         if (score < bestScore || score == bestScore && width < bestWidth) {
            bestScore = score;
            bestWidth = width;
         }
      }

      if (bestWidth != maxPanelWidth) {
         long score = this.scoreAutoWidth(maxPanelWidth, rawLineCount);
         if (score < bestScore || score == bestScore && maxPanelWidth < bestWidth) {
            bestWidth = maxPanelWidth;
         }
      }

      return bestWidth;
   }

   private long scoreAutoWidth(int panelWidthCandidate, int rawLineCount) {
      RiptidePacketInspectOverlay.FitMetrics fit = this.measureFitForPanelWidth(panelWidthCandidate);
      int extraLines = Math.max(0, fit.lineCount() - rawLineCount);
      int wastedPixels = Math.max(0, fit.contentWidth() - fit.longestWrappedLineWidth());
      return panelWidthCandidate / 6L + extraLines * 52L + wastedPixels * 4L;
   }

   private RiptidePacketInspectOverlay.FitMetrics measureFitForPanelWidth(int panelWidthCandidate) {
      int listWidth = Math.max(32, panelWidthCandidate - this.outerPad() * 2);
      int contentWidth = InspectorLayout.contentWidth(listWidth, this.innerPad());
      List<RiptidePacketInspectOverlay.WrappedInspectionLine> measured = new ArrayList<>();
      if (this.inspection != null) {
         for (RiptidePacketInspector.InspectionLine line : this.inspection.getLines()) {
            this.wrapLine(line, contentWidth, measured);
         }
      }

      int longestWrappedLineWidth = 0;

      for (RiptidePacketInspectOverlay.WrappedInspectionLine line : measured) {
         longestWrappedLineWidth = Math.max(longestWrappedLineWidth, line.renderedWidth(this.textRenderer));
      }

      return new RiptidePacketInspectOverlay.FitMetrics(contentWidth, measured.size(), longestWrappedLineWidth);
   }

   private List<RiptidePacketInspectOverlay.FooterButton> buildFooterButtons(int footerY) {
      List<RiptidePacketInspectOverlay.FooterButton> buttons = new ArrayList<>();
      int cursorX = this.panelX + this.panelWidth - this.outerPad();

      for (RiptidePacketInspectOverlay.FooterAction action : this.getVisibleFooterActions()) {
         cursorX = this.addFooterButton(buttons, cursorX, footerY, action);
      }

      return buttons;
   }

   private List<RiptidePacketInspectOverlay.FooterAction> getVisibleFooterActions() {
      List<RiptidePacketInspectOverlay.FooterAction> actions = new ArrayList<>();
      actions.add(RiptidePacketInspectOverlay.FooterAction.COPY);
      if (this.hasRawPacketCopyData(this.sourceEntry)) {
         actions.add(RiptidePacketInspectOverlay.FooterAction.COPY_RAW);
      }

      if (RiptidePacketEntryActions.canQueue(this.sourceEntry)) {
         actions.add(RiptidePacketInspectOverlay.FooterAction.QUEUE);
      }

      if (RiptidePacketEntryActions.canEditPayload(this.sourceEntry)) {
         actions.add(RiptidePacketInspectOverlay.FooterAction.EDIT_PAYLOAD);
      }

      if (RiptidePacketEntryActions.canAddPayloadAction(this.sourceEntry)) {
         actions.add(RiptidePacketInspectOverlay.FooterAction.ADD_PAYLOAD);
      }

      if (RiptidePacketEntryActions.canAddSendAction(this.sourceEntry)) {
         actions.add(RiptidePacketInspectOverlay.FooterAction.SEND);
      }

      if (RiptidePacketEntryActions.canAddWaitAction(this.sourceEntry)) {
         actions.add(RiptidePacketInspectOverlay.FooterAction.WAIT);
      }

      return actions;
   }

   private boolean hasRawPacketCopyData(RiptidePacketLoggerOverlay.LogEntry entry) {
      if (entry == null) {
         return false;
      } else {
         RiptidePayloadSupport.PayloadSnapshot payload = RiptidePayloadSupport.snapshotFromEntry(entry);
         if (payload != null && payload.rawBytes().length > 0) {
            return true;
         } else {
            RiptidePacketCapture.PacketSnapshot packet = RiptidePacketCapture.snapshot(entry.packetRef);
            return packet != null && packet.plaintextBytes().length > 0;
         }
      }
   }

   private int computeFooterButtonsWidth() {
      int width = 0;

      for (RiptidePacketInspectOverlay.FooterAction action : this.getVisibleFooterActions()) {
         if (width > 0) {
            width += this.buttonGap();
         }

         width += this.footerButtonWidth(action);
      }

      return width;
   }

   private int addFooterButton(
      List<RiptidePacketInspectOverlay.FooterButton> buttons, int cursorX, int footerY, RiptidePacketInspectOverlay.FooterAction action
   ) {
      int width = this.footerButtonWidth(action);
      int x = cursorX - width;
      buttons.add(new RiptidePacketInspectOverlay.FooterButton(action, x, footerY, width));
      return x - this.buttonGap();
   }

   private int footerButtonWidth(RiptidePacketInspectOverlay.FooterAction action) {
      return DirectLayout.fitOverlayButtonWidth(this.textRenderer, this.theme, UiTone.BODY, action.label, 5, 36, 68);
   }

   private int styledWidth(String text) {
      return text != null && !text.isEmpty() ? UiText.width(this.textRenderer, text, this.theme.fontFor(UiTone.BODY), RiptideColors.packetWhite()) : 0;
   }

   private int minimumWidth() {
      return 176;
   }

   private int minimumHeight() {
      return 132;
   }

   private int defaultPanelWidth() {
      return 220;
   }

   private int defaultPanelHeight() {
      return 173;
   }

   private int footerHeight() {
      return 26;
   }

   private int outerPad() {
      return 5;
   }

   private int innerPad() {
      return 3;
   }

   private int buttonGap() {
      return 4;
   }

   private int buttonHeight() {
      return 16;
   }

   private int continuationIndent() {
      return 32;
   }

   private int maxAutoWidth() {
      return 392;
   }

   private int maxAutoHeight() {
      return 300;
   }

   private int maxVisibleLines() {
      return 13;
   }

   private int lineHeight() {
      return this.theme.lineHeight(UiTone.BODY, 2);
   }

   private int bodyTopGap() {
      return 4;
   }

   private int analysisProgressHeight() {
      return this.currentPayloadAnalysis != null && !this.currentPayloadAnalysis.done() ? 22 : 0;
   }

   private int tabStripHeight() {
      return 0;
   }

   private int tabButtonHeight() {
      return 16;
   }

   private boolean hasPayloadSnapshot() {
      return this.sourceEntry != null && RiptidePayloadSupport.snapshotFromEntry(this.sourceEntry) != null;
   }

   private int bodyVerticalGap() {
      return this.bodyTopGap() + this.footerBottomGap();
   }

   private int footerTopInset() {
      return 3;
   }

   private int footerBottomGap() {
      return 3;
   }

   private int footerLabelInset() {
      return 5;
   }

   private int titleWidthPadding() {
      return 54;
   }

   private int footerContentPadding() {
      return 16;
   }

   private int listMinimumHeight() {
      return 32;
   }

   private record FitMetrics(int contentWidth, int lineCount, int longestWrappedLineWidth) {
   }

   private static enum FooterAction {
      WAIT("Wait"),
      SEND("Send"),
      QUEUE("Queue"),
      EDIT_PAYLOAD("Edit Payload"),
      ADD_PAYLOAD("+Payload"),
      COPY_RAW("Raw Hex"),
      COPY("Copy");

      private final String label;

      private FooterAction(String label) {
         this.label = label;
      }
   }

   private record FooterButton(RiptidePacketInspectOverlay.FooterAction action, int x, int y, int width) {
      private String label() {
         return this.action.label;
      }
   }

   private record WrappedInspectionLine(String prefixText, int prefixColor, String valueText, int valueColor, int valueOffset) {
      static RiptidePacketInspectOverlay.WrappedInspectionLine plain(String text, int offset, int color) {
         return new RiptidePacketInspectOverlay.WrappedInspectionLine(null, color, text, color, offset);
      }

      int renderedWidth(Font textRenderer) {
         int width = this.valueOffset + UiText.width(textRenderer, this.valueText, new CompactTheme().fontFor(UiTone.BODY), RiptideColors.packetWhite());
         if (this.prefixText != null && !this.prefixText.isEmpty()) {
            width = Math.max(width, UiText.width(textRenderer, this.prefixText, new CompactTheme().fontFor(UiTone.BODY), RiptideColors.packetWhite()));
         }

         return width;
      }
   }
}
