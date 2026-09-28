package riptide.gui.macro.editor;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.components.CompactListRenderer;
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
import riptide.util.RiptideColors;
import riptide.util.RiptideOverlayBase;
import riptide.util.RiptideOverlayManager;
import riptide.util.RiptideWindowLayout;

public final class RaceStepSelectorOverlay extends RiptideOverlayBase {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final int MIN_PANEL_WIDTH = 260;
   private static final int ROW_HEIGHT = 18;
   private static final int MAX_VISIBLE_ROWS = 12;
   private static final int PAD = 6;
   private static final int HEADER_CONTROL = 12;
   private static final int HEADER_ARROW_WIDTH = 10;
   private static final int HEADER_ARROW_GAP = 3;
   private static final int SCROLLBAR_WIDTH = 6;
   private final Font textRenderer;
   private final CompactTheme theme = new CompactTheme();
   private final DirectWindow windowNode = new DirectWindow("Race Selector");
   private final DirectSurface surface = new DirectSurface(this.theme, this.windowNode);
   private final CompactTextInput searchField = new CompactTextInput();
   private final DirectUiLabel summaryLabel = new DirectUiLabel("", UiTone.MUTED).setTrimToBounds(true);
   private final DirectViewportSlot listSlot = new DirectViewportSlot();
   private DirectScrollViewport listViewport;
   private final SearchableSelector<RaceStepSelectorOverlay.Option> selector = new SearchableSelector<>(
      option -> option.id() + "\n" + option.label() + "\n" + option.category() + "\n" + option.description()
   );
   private List<RaceStepSelectorOverlay.Option> allOptions = List.of();
   private List<RaceStepSelectorOverlay.Option> filteredOptions = List.of();
   private Consumer<RaceStepSelectorOverlay.Option> onSelect;
   private boolean visible;
   private boolean collapsed;
   private boolean dragging;
   private boolean dragMoved;
   private float dragOffsetX;
   private float dragOffsetY;
   private int panelX;
   private int panelY;
   private int panelWidth = 260;
   private int panelHeight;

   public RaceStepSelectorOverlay(Font textRenderer) {
      this.textRenderer = textRenderer;
      this.windowNode.setCenterTitle(false);
      this.windowNode.setTitleTone(UiTone.LABEL);
      this.windowNode.setHeaderControls(true, true);
      this.windowNode.setTitleAreaInsets(8, 41);
      this.windowNode.content().setGap(4).setPadding(DirectUiInsets.all(6));
      this.searchField.setPlaceholder("Search...").setFieldHeight(16).setGrowX(true).setOnChange(this::updateFilter);
      this.rebuildUi();
   }

   public void open(String title, List<RaceStepSelectorOverlay.Option> options, Consumer<RaceStepSelectorOverlay.Option> onSelect) {
      this.visible = true;
      this.collapsed = false;
      this.dragging = false;
      this.dragMoved = false;
      this.onSelect = onSelect;
      this.allOptions = options == null ? List.of() : List.copyOf(options);
      this.selector.setItems(this.allOptions);
      this.selector.setQuery("");
      this.filteredOptions = this.selector.items();
      this.windowNode.setTitle(title != null && !title.isBlank() ? title : "Race Selector");
      this.searchField.setText("");
      this.searchField.setFocused(true);
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
      this.windowNode.syncShowBody(true);
      RiptideOverlayManager.get().bringToFrontParent(this);
   }

   public void close() {
      this.visible = false;
      this.collapsed = false;
      this.dragging = false;
      this.dragMoved = false;
      this.onSelect = null;
      this.allOptions = List.of();
      this.filteredOptions = List.of();
      this.selector.setItems(List.of());
      this.surface.clearFocusedTextInputs();
      this.windowNode.syncShowBody(true);
   }

   @Override
   public boolean isVisible() {
      return this.visible;
   }

   @Override
   public boolean hasTextFieldFocused() {
      return this.visible && this.searchField.isFocused();
   }

   private void updateFilter(String query) {
      this.selector.setItems(this.allOptions);
      this.selector.setQuery(query);
      this.filteredOptions = this.selector.items();
      if (this.listViewport != null) {
         this.listViewport.jumpTo(0);
      }

      this.rebuildUi();
   }

   private void rebuildUi() {
      this.windowNode.content().clearChildren();
      this.windowNode.content().add(this.searchField);
      this.listSlot.setPreferredHeight(this.computeViewportHeight(this.filteredOptions.size()));
      this.windowNode.content().add(this.listSlot);
      this.summaryLabel.setText(this.filteredOptions.size() + " choices");
      this.windowNode.content().add(this.summaryLabel);
   }

   private int computeViewportHeight(int count) {
      int rows = Math.max(5, Math.min(12, Math.max(1, count)));
      return rows * 18 + 2;
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      if (this.visible && MC != null && MC.font != null) {
         context.nextStratum();
         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mouseX);
         float uiMouseY = viewport.toUiY(mouseY);
         DirectRenderContext metrics = new DirectRenderContext(context, MC.font, viewport, this.theme, uiMouseX, uiMouseY, delta);
         this.windowNode.setShowBody(!this.collapsed);
         this.windowNode.setActive(true);
         this.windowNode.setHeaderHovered(this.isOverHeader(uiMouseX, uiMouseY));
         this.panelHeight = Math.max(this.theme.headerHeight(), Math.round(this.windowNode.preferredHeight(metrics, this.panelWidth)));
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
            this.renderList(context, uiMouseX, uiMouseY);
         }
      }
   }

   private void renderList(GuiGraphicsExtractor context, float uiMouseX, float uiMouseY) {
      int x = Math.round(this.listSlot.x());
      int y = Math.round(this.listSlot.y());
      int w = Math.round(this.listSlot.width());
      int h = Math.round(this.listSlot.height());
      if (w > 2 && h > 2) {
         if (this.filteredOptions.isEmpty()) {
            CompactListRenderer.drawEmptyState(context, this.textRenderer, "Nothing to pick.", x, y, w - 6 - 1);
         } else {
            if (this.listViewport == null
               || this.listViewport.getX() != x
               || this.listViewport.getY() != y
               || this.listViewport.getWidth() != w
               || this.listViewport.getHeight() != h) {
               int oldScroll = this.listViewport == null ? 0 : this.listViewport.getScrollOffset();
               this.listViewport = new DirectScrollViewport(x, y, w, h, 18, 6);
               this.listViewport.jumpTo(oldScroll);
            }

            this.listViewport.setContentHeight(this.filteredOptions.size() * 18);
            this.listViewport.beginRender(context, this.theme.borderSoft(), this.theme.listFill());

            try {
               this.listViewport
                  .renderSimple(
                     context,
                     this.filteredOptions.size(),
                     (idx, bnd) -> this.renderRow(context, this.filteredOptions.get(idx), bnd.x, bnd.y, bnd.width, idx, uiMouseX, uiMouseY)
                  );
            } finally {
               this.listViewport.endRender(context);
            }

            this.listViewport.renderScrollbar(context, uiMouseX, uiMouseY);
         }
      }
   }

   private void renderRow(GuiGraphicsExtractor context, RaceStepSelectorOverlay.Option option, int x, int y, int width, int index, float mouseX, float mouseY) {
      boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 18;
      int bg = hovered ? this.theme.rowFillHovered() : this.theme.rowFillNormal();
      int accent = "Condition".equalsIgnoreCase(option.category()) ? RiptideColors.packetCyan() : RiptideColors.accent();
      CompactSurfaces.tintedRow(context, x, y, width, 18, bg);
      CompactSurfaces.indicator(context, x, y, 2, 18, accent);
      String label = option.category() + " / " + option.label();
      String trimmed = UiText.trimToWidth(this.textRenderer, label, Math.max(1, width - 10), this.theme.fontFor(UiTone.BODY), this.theme.color(UiTone.BODY));
      int textY = UiSizing.alignTextY(y, 18, this.theme.fontHeight(UiTone.BODY), this.theme.bodyTextNudge());
      UiText.draw(context, this.textRenderer, trimmed, this.theme.fontFor(UiTone.BODY), this.theme.color(UiTone.BODY), x + 6, textY, false);
      CompactSurfaces.divider(context, x + 4, y + 18 - 1, width - 8, RiptideColors.packetRowDivider());
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.visible) {
         return false;
      } else {
         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mouseX);
         float uiMouseY = viewport.toUiY(mouseY);
         if (button == 0 && this.isOverClose(uiMouseX, uiMouseY)) {
            this.close();
            return true;
         } else if (button == 0 && this.isOverHeader(uiMouseX, uiMouseY)) {
            this.dragging = true;
            this.dragMoved = false;
            this.dragOffsetX = uiMouseX - this.panelX;
            this.dragOffsetY = uiMouseY - this.panelY;
            return true;
         } else if (!this.collapsed && this.surface.mouseClicked(mouseX, mouseY, button)) {
            return true;
         } else {
            if (!this.collapsed
               && button == 0
               && this.uiContains(this.listSlot.x(), this.listSlot.y(), this.listSlot.width(), this.listSlot.height(), uiMouseX, uiMouseY)) {
               if (this.listViewport != null && this.listViewport.mouseClicked(uiMouseX, uiMouseY, button)) {
                  return true;
               }

               int index = (int)((uiMouseY - this.listSlot.y() + (this.listViewport == null ? 0 : this.listViewport.getScrollOffset())) / 18.0F);
               if (index >= 0 && index < this.filteredOptions.size()) {
                  RaceStepSelectorOverlay.Option selected = this.filteredOptions.get(index);
                  if (this.onSelect != null) {
                     this.onSelect.accept(selected);
                  }

                  this.close();
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
         this.dragging = false;
         if (!this.dragMoved && this.isOverHeader(uiMouseX, uiMouseY) && !this.isOverClose(uiMouseX, uiMouseY)) {
            this.setCollapsed(!this.collapsed);
         }

         this.dragMoved = false;
         return true;
      } else {
         if (button == 0 && this.listViewport != null) {
            this.listViewport.mouseReleased();
         }

         return !this.collapsed && this.surface.mouseReleased(mouseX, mouseY, button) ? true : this.visible;
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
         } else if (!this.collapsed && this.listViewport != null && this.listViewport.isScrollbarDragging()) {
            this.listViewport.mouseDragged(uiMouseX, uiMouseY);
            return true;
         } else {
            return !this.collapsed && this.surface.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
         }
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (this.visible && !this.collapsed && this.listViewport != null) {
         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mouseX);
         float uiMouseY = viewport.toUiY(mouseY);
         if (this.uiContains(this.listSlot.x(), this.listSlot.y(), this.listSlot.width(), this.listSlot.height(), uiMouseX, uiMouseY)) {
            this.listViewport.mouseScrolled(uiMouseX, uiMouseY, amount);
            return true;
         } else {
            return this.visible;
         }
      } else {
         return this.visible;
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
         return this.collapsed ? false : this.surface.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      return this.visible && !this.collapsed && this.surface.charTyped(chr, modifiers);
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
         return this.isOverHeader(uiMouseX, uiMouseY) && !this.isOverClose(uiMouseX, uiMouseY);
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
   public void clearTextFieldFocus() {
      this.surface.clearFocusedTextInputs();
   }

   @Override
   public int getMinWidth() {
      return 260;
   }

   @Override
   public int getMinHeight() {
      return this.theme.headerHeight() + 20;
   }

   private boolean isOverHeader(float x, float y) {
      return x >= this.panelX && x < this.panelX + this.panelWidth && y >= this.panelY && y < this.panelY + this.theme.headerHeight();
   }

   private boolean isOverClose(float x, float y) {
      return OverlayTopBar.isOverClose(
         UiBounds.of(this.panelX, this.panelY, this.panelWidth, Math.max(this.theme.headerHeight(), this.panelHeight)), this.theme.headerHeight(), x, y
      );
   }

   private boolean uiContains(float x, float y, float w, float h, float mx, float my) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }

   private RiptideWindowLayout clampToViewport(RiptideWindowLayout layout) {
      DirectViewport viewport = this.surface.viewport();
      int margin = 4;
      int viewportW = Math.round(viewport.uiWidth());
      int viewportH = Math.round(viewport.uiHeight());
      int availableW = Math.max(1, viewportW - margin * 2);
      int availableH = Math.max(this.theme.headerHeight(), viewportH - margin * 2);
      int width = Math.max(Math.min(260, availableW), Math.min(layout.width, availableW));
      int minHeight = layout.collapsed ? this.theme.headerHeight() : this.theme.headerHeight() + 20;
      int height = Math.max(Math.min(minHeight, availableH), Math.min(layout.height, availableH));
      int renderedHeight = layout.collapsed ? this.theme.headerHeight() : height;
      int x = Math.max(margin, Math.min(layout.x, Math.max(margin, viewportW - margin - width)));
      int y = Math.max(margin, Math.min(layout.y, Math.max(margin, viewportH - margin - renderedHeight)));
      return new RiptideWindowLayout(x, y, width, height, layout.visible, layout.collapsed);
   }

   public record Option(String category, String id, String label, String description) {
   }
}
