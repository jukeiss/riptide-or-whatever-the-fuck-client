package riptide.gui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import riptide.addons.AddonManager;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.CompactOverlayButton;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.ScrollState;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideUiScale;

public final class RiptideAddonsScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int INFO = -7429889;
   private static final int PANEL_WIDTH = 620;
   private static final int PANEL_MARGIN = 12;
   private static final int TOP_PANEL_Y = 20;
   private static final int TOP_PANEL_HEIGHT = 64;
   private static final int BODY_TOP = 92;
   private static final int BODY_BOTTOM_MARGIN = 12;
   private static final int LEFT_WIDTH = 278;
   private static final int GAP = 8;
   private static final int ROW_HEIGHT = 31;
   private static final int MAX_VISIBLE_ROWS = 8;
   private static final int SCROLLBAR_WIDTH = 4;
   private static final int SCROLLBAR_GUTTER = 10;
   private final Screen parent;
   private final List<CompactOverlayButton> buttons = new ArrayList<>();
   private final ScrollState listScroll = new ScrollState();
   private EditBox searchField;
   private String searchQuery = "";
   private String pendingSearchQuery = "";
   private boolean searchDirty;
   private String selectedAddonId = "";
   private int scrollOffset;
   private boolean scrollbarDragging;
   private int scrollbarGrabOffset;
   private List<AddonManager.AddonReport> reportsSnapshot = List.of();
   private String cachedFilterQuery = "";
   private List<AddonManager.AddonReport> cachedFilteredReports = List.of();

   public RiptideAddonsScreen(Screen parent) {
      super(Component.literal("Addons"));
      this.parent = parent;
   }

   protected void init() {
      this.reportsSnapshot = List.copyOf(AddonManager.reports());
      this.cachedFilterQuery = "\u0000";
      this.cachedFilteredReports = List.of();
      this.searchField = new EditBox(this.font, this.listX() + 12, 118, 254, 16, Component.literal("Search addons"));
      this.searchField.setHint(Component.literal("Search addons..."));
      this.searchField.setMaxLength(96);
      this.searchField.setResponder(value -> {
         this.pendingSearchQuery = safeTrim(value);
         this.searchDirty = true;
      });
      this.addRenderableWidget(this.searchField);
      this.ensureSelection();
      this.rebuildButtons();
   }

   private void rebuildButtons() {
      this.buttons.clear();
      int backW = 70;
      int backX = this.panelX() + this.panelWidth() - 22 - backW;
      this.buttons
         .add(
            CompactOverlayButton.create(backX, 32, backW, 18, Component.literal("Back"), b -> this.minecraft.gui.setScreen(this.parent))
               .setVariant(CompactOverlayButton.Variant.SECONDARY)
         );
      AddonManager.AddonReport selected = this.selectedReport();
      int pad = 12;
      int gap = 8;
      int btnLeft = this.detailX() + pad;
      int areaW = Math.max(80, this.detailWidth() - pad * 2);
      int halfW = (areaW - gap) / 2;
      int rightW = areaW - halfW - gap;
      int rightX = btnLeft + halfW + gap;
      int row2Y = this.bodyBottom() - 26;
      int row1Y = row2Y - 24;
      this.buttons
         .add(
            CompactOverlayButton.create(btnLeft, row1Y, halfW, 18, Component.literal("Copy ID"), b -> this.copyId())
               .setVariant(CompactOverlayButton.Variant.SECONDARY)
         );
      this.buttons
         .add(
            CompactOverlayButton.create(rightX, row1Y, rightW, 18, Component.literal("Copy Report"), b -> this.copyReport())
               .setVariant(CompactOverlayButton.Variant.SECONDARY)
         );
      boolean disabledRestart = selected != null && AddonManager.isDisabledOnRestart(selected.modId());
      CompactOverlayButton toggle = CompactOverlayButton.create(
         btnLeft, row2Y, halfW, 18, Component.literal(disabledRestart ? "Enable Restart" : "Disable Restart"), b -> this.toggleRestartDisabled()
      );
      toggle.setVariant(disabledRestart ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.DANGER);
      toggle.active = selected != null;
      this.buttons.add(toggle);
      this.buttons
         .add(
            CompactOverlayButton.create(rightX, row2Y, rightW, 18, Component.literal("Mods Folder"), b -> this.openModsFolder())
               .setVariant(CompactOverlayButton.Variant.PRIMARY)
         );
   }

   private void ensureSelection() {
      List<AddonManager.AddonReport> reports = this.filteredReports();
      if (reports.isEmpty()) {
         this.selectedAddonId = "";
      } else {
         for (AddonManager.AddonReport report : reports) {
            if (report.modId().equals(this.selectedAddonId)) {
               return;
            }
         }

         this.selectedAddonId = reports.get(0).modId();
      }
   }

   public void tick() {
      super.tick();
      if (this.searchDirty) {
         this.searchDirty = false;
         this.searchQuery = this.pendingSearchQuery;
         this.scrollOffset = 0;
         this.listScroll.jumpTo(0, 0);
         this.ensureSelection();
         this.rebuildButtons();
      }

      this.listScroll.tick(1.0F, 1);
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int mx = RiptideUiScale.toVirtualInt(mouseX);
      int my = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         UiRenderer.rect(graphics, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), -15856112);
         this.drawPanel(graphics, this.panelX() + 10, 20, this.panelWidth() - 20, 64, -401074149);
         this.drawPanel(graphics, this.listX(), 92, 278, this.bodyHeight(), -1206643689);
         this.drawPanel(graphics, this.detailX(), 92, this.detailWidth(), this.bodyHeight(), -1206643689);
         this.drawText(graphics, "Addons", this.panelX() + 22, 30, -855310, false);
         this.drawText(graphics, this.summaryText(), this.panelX() + 22, 48, -6645094, false, this.panelWidth() - 44);
         this.drawText(graphics, "Addon Library", this.listX() + 12, 102, -855310, false);
         this.renderRows(graphics, mx, my);
         this.renderDetails(graphics);

         for (CompactOverlayButton button : this.buttons) {
            CompactOverlayButton.renderStyled(graphics, this.font, button, mx, my);
         }

         CompactScrollbar.Metrics scrollbar = this.scrollbarMetrics(this.filteredReports().size());
         CompactScrollbar.draw(graphics, scrollbar, scrollbar.contains(mx, my), this.scrollbarDragging);
         super.extractRenderState(graphics, mx, my, delta);
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private void renderRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      List<AddonManager.AddonReport> reports = this.filteredReports();
      int maxScroll = this.maxScroll(reports.size());
      this.scrollOffset = quantizeScrollOffset(this.scrollOffset, 31, maxScroll);
      this.listScroll.jumpTo(this.scrollOffset, maxScroll);
      int first = this.scrollOffset / 31;
      int y = this.rowsTop() - this.scrollOffset % 31;

      for (int i = first; i < reports.size() && y + 31 - 3 <= this.rowsBottom(); i++) {
         if (y + 31 - 3 > this.rowsTop()) {
            this.renderRow(graphics, reports.get(i), y, mouseX, mouseY);
         }

         y += 31;
      }

      if (reports.isEmpty()) {
         this.drawText(
            graphics,
            this.reportsSnapshot.isEmpty() ? "No Riptide addons discovered." : "No addons match the search.",
            this.listX() + 12,
            this.rowsTop() + 12,
            -6645094,
            false,
            254
         );
      }
   }

   private void renderRow(GuiGraphicsExtractor graphics, AddonManager.AddonReport report, int y, int mouseX, int mouseY) {
      boolean selected = report.modId().equals(this.selectedAddonId);
      boolean hovered = mouseX >= this.rowX() && mouseX < this.rowRight() && mouseY >= y && mouseY < y + 31 - 3;
      int fill = selected ? 858052714 : (hovered ? 606804509 : 403771667);
      int status = selected ? -12588930 : this.statusColor(report);
      UiRenderer.rect(graphics, UiBounds.of(this.rowX(), y, this.rowWidth(), 28), fill);
      UiRenderer.rect(graphics, UiBounds.of(this.rowX(), y, 2, 28), status);
      UiRenderer.rect(graphics, UiBounds.of(this.rowX() + 4, y + 5, 3, 18), report.color());
      this.drawText(graphics, report.name(), this.rowX() + 12, y + 4, -855310, false, this.rowWidth() - 92);
      this.drawText(
         graphics,
         report.modId() + (report.version().isBlank() ? "" : "  v" + report.version()),
         this.rowX() + 12,
         y + 16,
         -6645094,
         false,
         this.rowWidth() - 92
      );
      this.drawText(graphics, this.displayStatus(report), this.rowRight() - 78, y + 10, this.statusColor(report), false, 70);
   }

   private void renderDetails(GuiGraphicsExtractor graphics) {
      AddonManager.AddonReport report = this.selectedReport();
      int x = this.detailX() + 12;
      int y = 102;
      int width = this.detailWidth() - 24;
      this.drawText(graphics, "Details", x, y, -855310, false, width);
      y += 18;
      if (report == null) {
         this.drawText(graphics, "Select an addon to inspect its status.", x, y, -6645094, false, width);
      } else {
         this.drawText(graphics, report.name(), x, y, -855310, false, width);
         y += 12;
         this.drawText(graphics, report.modId(), x, y, -6645094, false, width);
         y += 16;
         y = this.detailLine(graphics, x, y, width, "Status", this.displayStatus(report), this.statusColor(report));
         y = this.detailLine(graphics, x, y, width, "API", this.apiText(report), -7429889);
         y = this.detailLine(graphics, x, y, width, "Version", report.version().isBlank() ? "unknown" : report.version(), -855310);
         y = this.detailLine(graphics, x, y, width, "Authors", report.authors().isBlank() ? "unknown" : report.authors(), -855310);
         y = this.detailLine(graphics, x, y, width, "Extensions", report.summaryCounts(), -13248397);
         if (report.rejectedTotal() > 0) {
            y = this.detailLine(graphics, x, y, width, "Rejected", Integer.toString(report.rejectedTotal()), -14249);
         }

         if (report.runtimeErrors() > 0) {
            y = this.detailLine(graphics, x, y, width, "Runtime Errors", Integer.toString(report.runtimeErrors()), -42149);
         }

         if (AddonManager.isDisabledOnRestart(report.modId())) {
            y += 4;
            this.drawText(graphics, "Will be disabled after restart.", x, y, -14249, false, width);
            y += 12;
         }

         if (!report.failureReason().isBlank()) {
            y += 4;
            this.drawText(graphics, report.failureReason(), x, y, -42149, false, width);
            y += 14;
         }

         if (!report.rejectionDetails().isEmpty()) {
            y += 2;
            this.drawText(graphics, "Latest rejection:", x, y, -6645094, false, width);
            y += 12;
            this.drawText(graphics, report.rejectionDetails().get(report.rejectionDetails().size() - 1), x, y, -14249, false, width);
         } else if (!report.lastRuntimeError().isBlank()) {
            y += 2;
            this.drawText(graphics, report.lastRuntimeError(), x, y, -14249, false, width);
         }
      }
   }

   private int detailLine(GuiGraphicsExtractor graphics, int x, int y, int width, String label, String value, int valueColor) {
      this.drawText(graphics, label + ":", x, y, -6645094, false, 92);
      this.drawText(graphics, value, x + 76, y, valueColor, false, Math.max(1, width - 76));
      return y + 12;
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      MouseButtonEvent virtual = virtualEvent(event);
      if (virtual.button() != 0) {
         return super.mouseClicked(virtual, doubleClick);
      } else {
         for (CompactOverlayButton button : this.buttons) {
            if (CompactOverlayButton.fireIfHit(button, virtual.x(), virtual.y(), virtual.button())) {
               return true;
            }
         }

         CompactScrollbar.Metrics scrollbar = this.scrollbarMetrics(this.filteredReports().size());
         if (scrollbar.hasScroll() && scrollbar.contains(virtual.x(), virtual.y())) {
            this.scrollbarDragging = true;
            this.scrollbarGrabOffset = scrollbar.overThumb(virtual.x(), virtual.y())
               ? Math.max(0, (int)Math.round(virtual.y()) - scrollbar.thumbY())
               : scrollbar.thumbHeight() / 2;
            this.scrollOffset = quantizeScrollOffset(
               CompactScrollbar.scrollFromThumb(scrollbar, virtual.y(), this.scrollbarGrabOffset), 31, scrollbar.maxScroll()
            );
            this.listScroll.jumpTo(this.scrollOffset, scrollbar.maxScroll());
            return true;
         } else {
            AddonManager.AddonReport row = this.rowAt(virtual.x(), virtual.y());
            if (row != null) {
               this.selectedAddonId = row.modId();
               this.rebuildButtons();
               this.clearInputFocus();
               return true;
            } else {
               return super.mouseClicked(virtual, doubleClick);
            }
         }
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

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      MouseButtonEvent virtual = virtualEvent(event);
      if (this.scrollbarDragging) {
         CompactScrollbar.Metrics scrollbar = this.scrollbarMetrics(this.filteredReports().size());
         this.scrollOffset = quantizeScrollOffset(CompactScrollbar.scrollFromThumb(scrollbar, virtual.y(), this.scrollbarGrabOffset), 31, scrollbar.maxScroll());
         this.listScroll.jumpTo(this.scrollOffset, scrollbar.maxScroll());
         return true;
      } else {
         return super.mouseDragged(virtual, RiptideUiScale.toVirtual(dx), RiptideUiScale.toVirtual(dy));
      }
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      x = RiptideUiScale.toVirtual(x);
      y = RiptideUiScale.toVirtual(y);
      if (!(x < this.listX()) && !(x >= this.listX() + 278) && !(y < 92.0) && !(y >= this.bodyBottom())) {
         int maxScroll = this.maxScroll(this.filteredReports().size());
         if (maxScroll <= 0) {
            return true;
         } else {
            this.scrollOffset = quantizeScrollOffset(this.scrollOffset - (int)Math.signum(scrollY) * 31, 31, maxScroll);
            this.listScroll.setTarget(this.scrollOffset, maxScroll);
            return true;
         }
      } else {
         return super.mouseScrolled(x, y, scrollX, scrollY);
      }
   }

   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   private AddonManager.AddonReport rowAt(double mouseX, double mouseY) {
      if (!(mouseX < this.rowX()) && !(mouseX >= this.rowRight()) && !(mouseY < this.rowsTop()) && !(mouseY >= this.rowsBottom())) {
         List<AddonManager.AddonReport> reports = this.filteredReports();
         int index = ((int)mouseY - this.rowsTop() + this.scrollOffset) / 31;
         return index >= 0 && index < reports.size() ? reports.get(index) : null;
      } else {
         return null;
      }
   }

   private void copyId() {
      AddonManager.AddonReport report = this.selectedReport();
      if (report != null && this.minecraft != null) {
         this.minecraft.keyboardHandler.setClipboard(report.modId());
         RiptideNotifications.copied("Addon ID copied.");
      }
   }

   private void copyReport() {
      AddonManager.AddonReport report = this.selectedReport();
      if (report != null && this.minecraft != null) {
         this.minecraft.keyboardHandler.setClipboard(report.copyReport());
         RiptideNotifications.copied("Addon report copied.");
      }
   }

   private void toggleRestartDisabled() {
      AddonManager.AddonReport report = this.selectedReport();
      if (report != null) {
         boolean next = !AddonManager.isDisabledOnRestart(report.modId());
         AddonManager.setDisabledOnRestart(report.modId(), next);
         RiptideNotifications.show(next ? "Addon disables after restart." : "Addon enables after restart.", next ? -14249 : -13248397);
         this.rebuildButtons();
      }
   }

   private void openModsFolder() {
      Util.getPlatform().openFile(AddonManager.modsFolder());
   }

   private List<AddonManager.AddonReport> filteredReports() {
      String query = normalize(this.searchQuery);
      if (query.equals(this.cachedFilterQuery)) {
         return this.cachedFilteredReports;
      } else {
         this.cachedFilterQuery = query;
         if (query.isEmpty()) {
            this.cachedFilteredReports = this.reportsSnapshot;
            return this.cachedFilteredReports;
         } else {
            List<AddonManager.AddonReport> out = new ArrayList<>();

            for (AddonManager.AddonReport report : this.reportsSnapshot) {
               if (normalize(report.name()).contains(query) || normalize(report.modId()).contains(query) || normalize(report.authors()).contains(query)) {
                  out.add(report);
               }
            }

            this.cachedFilteredReports = List.copyOf(out);
            return this.cachedFilteredReports;
         }
      }
   }

   private AddonManager.AddonReport selectedReport() {
      for (AddonManager.AddonReport report : this.reportsSnapshot) {
         if (report.modId().equals(this.selectedAddonId)) {
            return report;
         }
      }

      return null;
   }

   private String summaryText() {
      int total = this.reportsSnapshot.size();
      int loaded = 0;
      int issues = 0;
      int disabled = 0;

      for (AddonManager.AddonReport report : this.reportsSnapshot) {
         if (report.status() == AddonManager.AddonLoadStatus.LOADED) {
            loaded++;
         }

         if (report.status().isIssue()) {
            issues++;
         }

         if (AddonManager.isDisabledOnRestart(report.modId()) || report.status() == AddonManager.AddonLoadStatus.DISABLED) {
            disabled++;
         }
      }

      return total + " discovered  " + loaded + " loaded  " + issues + " issue(s)  " + disabled + " disabled/restart";
   }

   private String displayStatus(AddonManager.AddonReport report) {
      if (report == null) {
         return "";
      } else {
         return AddonManager.isDisabledOnRestart(report.modId()) && report.status() == AddonManager.AddonLoadStatus.LOADED
            ? "Disables"
            : report.status().label();
      }
   }

   private int statusColor(AddonManager.AddonReport report) {
      if (report == null) {
         return -6645094;
      } else if (report.status().isIssue()) {
         return -42149;
      } else {
         return !AddonManager.isDisabledOnRestart(report.modId()) && report.status() != AddonManager.AddonLoadStatus.DISABLED ? -13248397 : -14249;
      }
   }

   private String apiText(AddonManager.AddonReport report) {
      String addon = report.apiVersion() < 0 ? "unknown" : Integer.toString(report.apiVersion());
      return "addon v" + addon + " / host v" + report.hostApiVersion();
   }

   private CompactScrollbar.Metrics scrollbarMetrics(int rows) {
      int content = rows * 31;
      return CompactScrollbar.compute(
         content,
         Math.max(1, this.rowsBottom() - this.rowsTop()),
         this.listX() + 278 - 10,
         this.rowsTop(),
         4,
         this.rowsBottom() - this.rowsTop(),
         this.scrollOffset
      );
   }

   private int maxScroll(int rows) {
      return Math.max(0, rows * 31 - Math.max(1, this.rowsBottom() - this.rowsTop()));
   }

   private void clearInputFocus() {
      if (this.searchField != null) {
         this.searchField.setFocused(false);
      }

      this.setFocused(null);
   }

   private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean center) {
      this.drawText(graphics, text, x, y, color, center, Integer.MAX_VALUE);
   }

   private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean center, int maxWidth) {
      Font renderer = this.font;
      Identifier fontId = THEME.fontFor(UiTone.BODY);
      String value = text == null ? "" : text;
      if (maxWidth != Integer.MAX_VALUE && !center) {
         UiText.drawEllipsized(graphics, renderer, value, fontId, color, x, y, Math.max(1, maxWidth), false);
      } else {
         if (maxWidth != Integer.MAX_VALUE) {
            value = UiText.trimToWidthEllipsis(renderer, value, maxWidth, fontId, color);
         }

         int w = UiText.width(renderer, value, fontId, color);
         UiText.draw(graphics, renderer, value, fontId, color, center ? x - w / 2 : x, y, false);
      }
   }

   private int panelX() {
      return (this.screenWidth() - this.panelWidth()) / 2;
   }

   private int panelWidth() {
      return Math.min(620, Math.max(320, this.screenWidth() - 24));
   }

   private int listX() {
      return this.panelX() + 10;
   }

   private int detailX() {
      return this.listX() + 278 + 8;
   }

   private int detailWidth() {
      return Math.max(220, this.panelWidth() - 20 - 278 - 8);
   }

   private int bodyBottom() {
      return this.screenHeight() - 12;
   }

   private int bodyHeight() {
      return Math.max(120, this.bodyBottom() - 92);
   }

   private int rowsTop() {
      return 140;
   }

   private int rowsBottom() {
      return Math.min(this.bodyBottom() - 8, this.rowsTop() + 248);
   }

   private int rowX() {
      return this.listX() + 8;
   }

   private int rowRight() {
      return this.listX() + 278 - 16;
   }

   private int rowWidth() {
      return Math.max(1, this.rowRight() - this.rowX());
   }

   private static int quantizeScrollOffset(int value, int step, int maxScroll) {
      int clamped = Math.max(0, Math.min(maxScroll, value));
      if (step <= 0) {
         return clamped;
      } else {
         int rounded = Math.round((float)clamped / step) * step;
         return Math.max(0, Math.min(maxScroll, rounded));
      }
   }

   private static String normalize(String value) {
      return safeTrim(value).toLowerCase(Locale.ROOT);
   }
}
