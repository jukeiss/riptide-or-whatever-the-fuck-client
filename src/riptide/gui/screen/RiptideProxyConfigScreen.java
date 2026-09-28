package riptide.gui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.CompactDropdown;
import riptide.gui.vanillaui.components.CompactOverlayButton;
import riptide.gui.vanillaui.components.CompactScreenPanel;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.SectionPanel;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectLayout;
import riptide.util.RiptideProxyManager;
import riptide.util.RiptideUiScale;

public class RiptideProxyConfigScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int PANEL_WIDTH = 430;
   private static final int ROW_HEIGHT = 30;
   private static final int[] TIMEOUT_VALUES = new int[]{1000, 1500, 2000, 2500, 3000, 5000, 7500, 10000, 15000};
   private static final int[] THREAD_VALUES = new int[]{1, 2, 4, 8, 12, 16, 24, 32, 48, 64, 96, 128, 192, 256};
   private static final int[] RETRY_VALUES = new int[]{0, 1, 2, 3, 5};
   private static final int[] PRUNE_LATENCY_VALUES = new int[]{0, 500, 1000, 1500, 2000, 3000, 5000, 10000};
   private static final int[] PRUNE_COUNT_VALUES = new int[]{0, 25, 50, 100, 250, 500, 1000};
   private final Screen parent;
   private final List<CompactOverlayButton> buttons = new ArrayList<>();
   private final List<CompactDropdown> dropdowns = new ArrayList<>();
   private final List<RiptideProxyConfigScreen.ConfigRow> rows = new ArrayList<>();
   private int scroll;
   private int layoutWidth;
   private int layoutHeight;

   public RiptideProxyConfigScreen(Screen parent) {
      super(Component.literal("Proxy Config"));
      this.parent = parent;
   }

   protected void init() {
      this.rebuildRows();
   }

   private void rebuildRows() {
      this.buttons.clear();
      this.dropdowns.clear();
      this.rows.clear();
      int panelX = this.panelX();
      int panelW = this.panelW();
      int y = this.contentTop() - this.scroll;
      RiptideProxyManager mgr = RiptideProxyManager.get();
      this.addDropdownRow(
         "Timeout", "How long each proxy check can take.", mgr.getTimeoutMs(), mgr::setTimeoutMs, TIMEOUT_VALUES, this::formatMs, panelX, panelW, y
      );
      y += 30;
      this.addDropdownRow(
         "Threads", "How many proxies can be checked at once.", mgr.getThreads(), mgr::setThreads, THREAD_VALUES, Integer::toString, panelX, panelW, y
      );
      y += 30;
      this.addDropdownRow(
         "Retries", "Extra attempts when a proxy check times out.", mgr.getRetries(), mgr::setRetries, RETRY_VALUES, Integer::toString, panelX, panelW, y
      );
      y += 30;
      this.addDropdownRow(
         "Prune latency",
         "Cleanup removes alive proxies slower than this.",
         mgr.getPruneLatency(),
         mgr::setPruneLatency,
         PRUNE_LATENCY_VALUES,
         value -> value <= 0 ? "Off" : this.formatMs(value),
         panelX,
         panelW,
         y
      );
      y += 30;
      this.addDropdownRow(
         "Prune limit",
         "Cleanup keeps only the fastest proxies when enabled.",
         mgr.getPruneToCount(),
         mgr::setPruneToCount,
         PRUNE_COUNT_VALUES,
         value -> value <= 0 ? "No limit" : Integer.toString(value),
         panelX,
         panelW,
         y
      );
      y += 30;
      this.addToggleRow(
         "Sort by latency",
         mgr.isSortByLatency(),
         "Refresh/cancel sorts alive proxies from fastest to slowest.",
         value -> mgr.setSortByLatency(value),
         panelX,
         panelW,
         y
      );
      y += 30;
      this.addToggleRow("Prune dead", mgr.isPruneDead(), "Cleanup removes proxies marked as dead.", value -> mgr.setPruneDead(value), panelX, panelW, y);
      if (!this.compactLayout()) {
         this.buttons
            .add(
               CompactOverlayButton.create(
                     panelX + 12, this.panelBottom() - 30, 92, 18, Component.literal("Back"), b -> this.minecraft.gui.setScreen(this.parent)
                  )
                  .setVariant(CompactOverlayButton.Variant.SECONDARY)
            );
         this.buttons
            .add(
               CompactOverlayButton.create(panelX + panelW - 104, this.panelBottom() - 30, 92, 18, Component.literal("Defaults"), b -> this.resetDefaults())
                  .setVariant(CompactOverlayButton.Variant.SECONDARY)
            );
      }

      this.layoutWidth = this.screenWidth();
      this.layoutHeight = this.screenHeight();
   }

   private void addDropdownRow(
      String label,
      String hint,
      int current,
      IntConsumer setter,
      int[] options,
      RiptideProxyConfigScreen.ValueFormatter formatter,
      int panelX,
      int panelW,
      int y
   ) {
      this.rows.add(new RiptideProxyConfigScreen.ConfigRow(label, "", hint, y, false, false));
      if (this.isRowVisible(y)) {
         int controlW = this.controlWidth(panelW);
         this.dropdowns
            .add(
               new CompactDropdown(
                  panelX + panelW - controlW - 14,
                  y + 6,
                  controlW,
                  18,
                  this.optionLabels(options, formatter),
                  this.selectedOptionIndex(current, options),
                  index -> {
                     if (index >= 0 && index < options.length) {
                        setter.accept(options[index]);
                        this.rebuildRows();
                     }
                  }
               )
            );
      }
   }

   private void addToggleRow(String label, boolean enabled, String hint, RiptideProxyConfigScreen.BooleanConsumer setter, int panelX, int panelW, int y) {
      this.rows.add(new RiptideProxyConfigScreen.ConfigRow(label, enabled ? "Enabled" : "Disabled", hint, y, true, enabled));
      if (this.isRowVisible(y)) {
         int controlW = this.controlWidth(panelW);
         this.buttons
            .add(
               CompactOverlayButton.create(panelX + panelW - controlW - 14, y + 6, controlW, 18, Component.literal(enabled ? "Enabled" : "Disabled"), b -> {
                     setter.accept(!enabled);
                     this.rebuildRows();
                  })
                  .setVariant(enabled ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.DANGER)
                  .setToggleState(enabled)
                  .setAnimationKey("proxy-config:" + label)
            );
      }
   }

   private void resetDefaults() {
      RiptideProxyManager mgr = RiptideProxyManager.get();
      mgr.setTimeoutMs(3000);
      mgr.setThreads(64);
      mgr.setRetries(0);
      mgr.setPruneLatency(2000);
      mgr.setPruneToCount(0);
      mgr.setSortByLatency(true);
      mgr.setPruneDead(true);
      this.rebuildRows();
   }

   private int selectedOptionIndex(int current, int[] options) {
      if (options.length == 0) {
         return current;
      } else {
         int index = 0;
         int bestDistance = Integer.MAX_VALUE;

         for (int i = 0; i < options.length; i++) {
            int distance = Math.abs(options[i] - current);
            if (distance < bestDistance) {
               bestDistance = distance;
               index = i;
            }
         }

         return index;
      }
   }

   private List<String> optionLabels(int[] options, RiptideProxyConfigScreen.ValueFormatter formatter) {
      List<String> labels = new ArrayList<>();

      for (int option : options) {
         labels.add(formatter.format(option));
      }

      return labels;
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int virtualMouseX = RiptideUiScale.toVirtualInt(mouseX);
      int virtualMouseY = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         if (this.layoutWidth != this.screenWidth() || this.layoutHeight != this.screenHeight()) {
            this.rebuildRows();
         }

         UiRenderer.rect(graphics, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), -15856112);
         int panelX = this.panelX();
         int panelW = this.panelW();
         int panelTop = this.panelTop();
         UiBounds panelBounds = UiBounds.of(panelX, panelTop, panelW, this.panelBottom() - panelTop);
         CompactScreenPanel.render(
            UiContexts.overlay(graphics, this.font, virtualMouseX, virtualMouseY),
            panelBounds,
            20,
            "Proxy Settings",
            panelBounds.contains(virtualMouseX, virtualMouseY)
         );
         SectionPanel.renderBody(
            UiContexts.overlay(graphics, this.font, virtualMouseX, virtualMouseY),
            UiBounds.of(panelX + 10, panelTop + 28, Math.max(0, panelW - 20), Math.max(0, this.panelBottom() - panelTop - 64)),
            -1206643689
         );
         boolean suppressUnderlyingPointer = CompactDropdown.shouldSuppressUnderlyingPointer();
         boolean dropdownMenuHovered = CompactDropdown.isInsideOpenMenu(this.dropdowns, virtualMouseX, virtualMouseY);
         int buttonMouseX = !dropdownMenuHovered && !suppressUnderlyingPointer ? virtualMouseX : Integer.MIN_VALUE;
         int buttonMouseY = !dropdownMenuHovered && !suppressUnderlyingPointer ? virtualMouseY : Integer.MIN_VALUE;
         if (!this.compactLayout()) {
            for (RiptideProxyConfigScreen.ConfigRow row : this.rows) {
               this.renderRow(graphics, row, panelX, panelW);
            }

            for (CompactOverlayButton button : this.buttons) {
               CompactOverlayButton.renderStyled(graphics, this.font, button, buttonMouseX, buttonMouseY);
            }

            CompactDropdown.renderButtons(graphics, this.font, this.dropdowns, virtualMouseX, virtualMouseY);
            if (CompactDropdown.isMenuOpen(this.dropdowns)) {
               CompactDropdown.renderOpenMenu(graphics, this.font, this.dropdowns, virtualMouseX, virtualMouseY);
            }

            return;
         }

         this.drawText(graphics, "Window too small.", panelX + 14, panelTop + 34, -6645094, false, Math.max(0, panelW - 28));
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private void renderRow(GuiGraphicsExtractor graphics, RiptideProxyConfigScreen.ConfigRow row, int panelX, int panelW) {
      if (this.isRowVisible(row.y())) {
         int x = panelX + 12;
         int y = row.y();
         int w = panelW - 24;
         int fill = row.toggle() && row.enabled() ? 571483674 : 403771667;
         UiRenderer.rect(graphics, UiBounds.of(x, y, w, 26), fill);
         if (row.toggle() && row.enabled()) {
            UiRenderer.rect(graphics, UiBounds.of(x, y, 2, 26), -13248397);
         }

         int textMaxWidth = Math.max(1, panelW - this.controlWidth(panelW) - 48);
         this.drawText(graphics, row.label(), x + 10, y + 5, -855310, false, textMaxWidth);
         this.drawText(graphics, row.hint(), x + 10, y + 17, -6645094, false, textMaxWidth);
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      MouseButtonEvent virtualEvent = virtualEvent(event);
      if (virtualEvent.button() == 0
         && CompactScreenPanel.isOverClose(
            UiBounds.of(this.panelX(), this.panelTop(), this.panelW(), this.panelBottom() - this.panelTop()), 20, (int)virtualEvent.x(), (int)virtualEvent.y()
         )) {
         this.minecraft.gui.setScreen(this.parent);
         return true;
      } else if (CompactDropdown.mouseClicked(this.dropdowns, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) {
         return true;
      } else if (virtualEvent.button() != 0) {
         return super.mouseClicked(virtualEvent, doubleClick);
      } else {
         for (CompactOverlayButton button : this.buttons) {
            if (CompactOverlayButton.fireIfHit(button, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) {
               return true;
            }
         }

         return super.mouseClicked(virtualEvent, doubleClick);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      double virtualX = RiptideUiScale.toVirtual(mouseX);
      double virtualY = RiptideUiScale.toVirtual(mouseY);
      if (CompactDropdown.mouseScrolled(this.dropdowns, virtualX, virtualY, scrollY)) {
         return true;
      } else if (!(virtualX < this.panelX())
         && !(virtualX > this.panelX() + this.panelW())
         && !(virtualY < this.contentTop())
         && !(virtualY > this.contentBottom())) {
         this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll + (scrollY < 0.0 ? 30 : -30)));
         this.rebuildRows();
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
   }

   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   private int panelX() {
      return DirectLayout.centerPanel(this.screenWidth(), this.panelW(), 8);
   }

   private int panelW() {
      return DirectLayout.fitPanelDimension(this.screenWidth(), 8, 430);
   }

   private int panelTop() {
      return DirectLayout.centerPanel(this.screenHeight(), this.panelH(), 8);
   }

   private int panelH() {
      return DirectLayout.fitPanelDimension(this.screenHeight(), 8, 286);
   }

   private int panelBottom() {
      return this.panelTop() + this.panelH();
   }

   private int contentTop() {
      return this.panelTop() + 36;
   }

   private int contentBottom() {
      return this.panelBottom() - 40;
   }

   private int maxScroll() {
      return Math.max(0, 210 - Math.max(0, this.contentBottom() - this.contentTop()));
   }

   private boolean isRowVisible(int y) {
      return y + 30 > this.contentTop() && y < this.contentBottom();
   }

   private boolean compactLayout() {
      return this.panelW() < 220 || this.panelH() < 130;
   }

   private int controlWidth(int panelW) {
      return Math.max(72, Math.min(118, panelW / 3));
   }

   private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean right) {
      this.drawText(graphics, text, x, y, color, right, Integer.MAX_VALUE);
   }

   private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean right, int maxWidth) {
      Font renderer = this.font;
      Identifier font = THEME.fontFor(UiTone.BODY);
      if (maxWidth != Integer.MAX_VALUE && !right) {
         UiText.drawFitted(graphics, renderer, text, font, color, x, y, Math.max(1, maxWidth), false);
      } else {
         String display = maxWidth == Integer.MAX_VALUE ? text : UiText.trimToWidth(renderer, text, maxWidth, font, color);
         int w = UiText.width(renderer, display, font, color);
         int drawX = right ? x - w : x;
         UiText.draw(graphics, renderer, display, font, color, drawX, y, false);
      }
   }

   private String formatMs(int value) {
      return value % 1000 == 0 ? value / 1000 + "s" : value / 1000.0 + "s";
   }

   @FunctionalInterface
   private interface BooleanConsumer {
      void accept(boolean var1);
   }

   private record ConfigRow(String label, String value, String hint, int y, boolean toggle, boolean enabled) {
   }

   @FunctionalInterface
   private interface ValueFormatter {
      String format(int var1);
   }
}
