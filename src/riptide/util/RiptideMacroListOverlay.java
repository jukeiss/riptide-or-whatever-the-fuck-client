package riptide.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.CompactListRenderer;
import riptide.gui.vanillaui.components.CompactListViewport;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactSurfaces;
import riptide.gui.vanillaui.components.CompactTextInput;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.OverlayTopBar;
import riptide.gui.vanillaui.components.ScrollState;
import riptide.gui.vanillaui.components.UiSizing;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectRenderContext;
import riptide.gui.vanillaui.direct.DirectRow;
import riptide.gui.vanillaui.direct.DirectSurface;
import riptide.gui.vanillaui.direct.DirectUiButton;
import riptide.gui.vanillaui.direct.DirectUiInsets;
import riptide.gui.vanillaui.direct.DirectUiLabel;
import riptide.gui.vanillaui.direct.DirectViewport;
import riptide.gui.vanillaui.direct.DirectViewportSlot;
import riptide.gui.vanillaui.direct.DirectWindow;
import riptide.util.macro.MacroExecutor;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiTakeoverState;

public class RiptideMacroListOverlay extends RiptideOverlayBase {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final int MIN_PANEL_WIDTH = 258;
   private static final int ROW_HEIGHT = 18;
   private static final int MAX_VISIBLE_ROWS = 6;
   private static final int PAD = 6;
   private static final int HEADER_CONTROL = 12;
   private static final int HEADER_ARROW_WIDTH = 10;
   private static final int HEADER_ARROW_GAP = 3;
   private static final long METEOR_CACHE_MS = 2000L;
   private static final int ROW_BUTTON_HEIGHT = 14;
   private static final int DELETE_BUTTON_WIDTH = 16;
   private static final int ROW_BUTTON_GAP = 4;
   private static final int ROW_BUTTON_PADDING = 2;
   private static final int VIEWPORT_BORDER = 1;
   private final Font textRenderer;
   private final RiptideMacroEditorOverlay activeEditor;
   private final CompactTheme theme = new CompactTheme();
   private final DirectWindow windowNode = new DirectWindow("Macro Library");
   private final DirectSurface surface = new DirectSurface(this.theme, this.windowNode);
   private final CompactTextInput searchField = new CompactTextInput();
   private final CompactTextInput pasteNameField = new CompactTextInput();
   private final DirectViewportSlot listSlot = new DirectViewportSlot();
   private int panelX = 500;
   private int panelY = 250;
   private int panelWidth = 258;
   private int panelHeight = 220;
   private boolean visible = false;
   private boolean collapsed = false;
   private boolean dragging = false;
   private float dragOffsetX;
   private float dragOffsetY;
   private final ScrollState listScroll = new ScrollState();
   private boolean pasteMode = false;
   private boolean scrollbarDragging = false;
   private int scrollbarGrabOffset = 0;
   private int lastKnownMacroCount = -1;
   private long lastKnownMacroRevision = Long.MIN_VALUE;
   private int lastTitleCount = Integer.MIN_VALUE;
   private String lastTitle = "";
   private boolean lastKnownPasteMode = false;
   private boolean needsUiRebuild = true;
   private boolean configurationOnly;
   private final List<RiptideMacroListOverlay.ClickRegion> clickRegions = new ArrayList<>();
   private final List<RiptideMacroListOverlay.RowButton> rowButtonScratch = new ArrayList<>(4);
   private List<RiptideMacroListOverlay.DisplayItem> currentItems = List.of();
   private List<RiptideMacro> cachedMeteorMacros = Collections.emptyList();
   private long meteorMacroCacheTime = 0L;
   private long cachedVisibleCountRevision = Long.MIN_VALUE;
   private int cachedVisibleLocalMacroCount;

   public RiptideMacroListOverlay(Font textRenderer) {
      this(textRenderer, null);
   }

   public RiptideMacroListOverlay(Font textRenderer, RiptideMacroEditorOverlay activeEditor) {
      this.textRenderer = textRenderer;
      this.activeEditor = activeEditor;
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
      this.searchField.setPlaceholder("Search macros...").setFieldHeight(this.searchFieldHeight()).setGrowX(true).setOnChange(text -> {
         this.listScroll.jumpTo(0, 0);
         this.needsUiRebuild = true;
      });
      this.pasteNameField
         .setPlaceholder("New macro name...")
         .setFieldHeight(this.searchFieldHeight())
         .setGrowX(true)
         .setOnSubmit(text -> this.pasteMacroFromClipboard());
      this.rebuildUi();
   }

   private void rebuildData() {
      RiptideLANSync sync = RiptideLANSync.getInstance();
      this.currentItems = this.buildDisplayItems(
         RiptideMacroManager.get().getAll(), this.getCachedMeteorMacros(), sync.getAllRemoteMacros(), sync.getAllRemoteMeteorMacros(), sync
      );
   }

   private void rebuildUi() {
      this.windowNode.content().clearChildren();
      this.rebuildData();
      this.windowNode.setTitle("Macro Library [" + this.visibleLocalMacroCount() + "]");
      this.windowNode.content().add(this.searchField);
      if (this.currentItems.isEmpty()) {
         this.windowNode.content().add(new DirectUiLabel("No macros match the current filter.", UiTone.MUTED).setTrimToBounds(true));
      } else {
         this.listSlot.setPreferredHeight(this.computeViewportHeight(this.currentItems.size()));
         this.windowNode.content().add(this.listSlot);
      }

      if (this.pasteMode) {
         this.windowNode.content().add(this.pasteNameField);
         DirectRow actions = new DirectRow().setGap(this.actionRowGap());
         actions.add(
            new DirectUiButton("Paste", DirectUiButton.Variant.DANGER, this::pasteMacroFromClipboard).setGrowX(true).setButtonHeight(this.actionButtonHeight())
         );
         actions.add(
            new DirectUiButton("Cancel", DirectUiButton.Variant.SECONDARY, this::cancelPasteMode).setGrowX(true).setButtonHeight(this.actionButtonHeight())
         );
         this.windowNode.content().add(actions);
         this.windowNode.content().add(new DirectUiLabel("Paste a copied macro under a new name.", UiTone.MUTED).setTrimToBounds(true));
      } else {
         DirectRow actions = new DirectRow().setGap(this.actionRowGap());
         actions.add(
            new DirectUiButton("Create New", DirectUiButton.Variant.SUCCESS, this::openCreateNew).setGrowX(true).setButtonHeight(this.actionButtonHeight())
         );
         actions.add(new DirectUiButton("Paste", DirectUiButton.Variant.DANGER, this::beginPasteMode).setGrowX(true).setButtonHeight(this.actionButtonHeight()));
         this.windowNode.content().add(actions);
      }
   }

   private int computeViewportHeight(int itemCount) {
      int visibleRows = Math.max(3, Math.min(this.maxVisibleRows(), itemCount));
      return visibleRows * this.rowHeight() + 2;
   }

   private List<RiptideMacro> getCachedMeteorMacros() {
      long now = System.currentTimeMillis();
      if (now - this.meteorMacroCacheTime > 2000L) {
         this.cachedMeteorMacros = MeteorMacroAdapter.getMeteorMacros();
         this.meteorMacroCacheTime = now;
      }

      return this.cachedMeteorMacros;
   }

   public RiptideMacroEditorOverlay getActiveEditor() {
      return this.activeEditor;
   }

   public void setConfigurationOnly(boolean configurationOnly) {
      if (this.configurationOnly != configurationOnly) {
         this.configurationOnly = configurationOnly;
         this.needsUiRebuild = true;
      }
   }

   public void saveState() {
      RiptideSharedState shared = RiptideSharedState.get();
      shared.setMacroListOverlayVisible(this.visible);
      shared.setMacroListOverlayX(this.panelX);
      shared.setMacroListOverlayY(this.panelY);
      shared.setMacroListOverlayScrollOffset(this.listScroll.targetOffset());
      shared.setMacroListOverlaySearch(this.searchField.text());
      this.saveLayout();
   }

   public void restoreState() {
      RiptideSharedState shared = RiptideSharedState.get();
      this.restoreLayout();
      this.visible = shared.isMacroListOverlayVisible();
      this.panelX = shared.getMacroListOverlayX();
      this.panelY = shared.getMacroListOverlayY();
      this.listScroll.restore(shared.getMacroListOverlayScrollOffset());
      this.searchField.setText(shared.getMacroListOverlaySearch());
      this.needsUiRebuild = true;
      this.windowNode.restoreShowBody(!this.collapsed);
   }

   @Override
   public String getOverlayId() {
      return "riptide-macrolist";
   }

   @Override
   public int getMinWidth() {
      return this.panelMinimumWidth();
   }

   @Override
   public int getMinHeight() {
      return this.theme.headerHeight() + this.searchFieldHeight() + this.actionButtonHeight() + this.panelPadding() * 2 + this.windowContentGap();
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
      }
   }

   @Override
   public void setVisible(boolean visible) {
      if (visible && this.activeEditor != null && this.activeEditor.isVisible()) {
         this.visible = false;
         this.saveState();
         RiptideOverlayManager.get().bringToFront(this.activeEditor);
      } else {
         this.visible = visible;
         if (!visible) {
            this.surface.clearFocusedTextInputs();
            this.dragging = false;
         } else {
            this.needsUiRebuild = true;
            this.windowNode.restoreShowBody(!this.collapsed);
            RiptideOverlayManager.get().bringToFront(this);
         }

         this.saveState();
      }
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
      this.collapsed = collapsed;
      this.dragging = false;
      if (collapsed) {
         this.surface.clearFocusedTextInputs();
      }

      this.saveState();
   }

   @Override
   public boolean usesSharedHeaderClickCollapse() {
      return true;
   }

   @Override
   public boolean hasTextFieldFocused() {
      return this.surface.hasFocusedTextInput();
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
         float uiX = viewport.toUiX(mouseX);
         float uiY = viewport.toUiY(mouseY);
         return uiX >= this.panelX && uiX <= this.panelX + this.panelWidth && uiY >= this.panelY && uiY <= this.panelY + this.panelHeight;
      }
   }

   @Override
   public boolean isOverDragBar(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else {
         DirectViewport viewport = this.surface.viewport();
         float uiX = viewport.toUiX(mouseX);
         float uiY = viewport.toUiY(mouseY);
         return this.isOverHeaderUi(uiX, uiY) && !this.isOverCloseButton(uiX, uiY);
      }
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      if (this.visible && MC != null && MC.font != null) {
         long currentMacroRevision = RiptideMacroManager.get().getRevision();
         int currentMacroCount = this.visibleLocalMacroCount();
         if (currentMacroRevision != this.lastKnownMacroRevision) {
            this.needsUiRebuild = true;
         }

         if (!this.needsUiRebuild && currentMacroCount == this.lastKnownMacroCount && this.pasteMode == this.lastKnownPasteMode) {
            if (currentMacroCount != this.lastTitleCount) {
               this.lastTitleCount = currentMacroCount;
               this.lastTitle = "Macro Library [" + currentMacroCount + "]";
            }

            this.windowNode.setTitle(this.lastTitle);
         } else {
            this.rebuildUi();
            this.lastKnownMacroCount = currentMacroCount;
            this.lastKnownMacroRevision = currentMacroRevision;
            this.lastKnownPasteMode = this.pasteMode;
            this.needsUiRebuild = false;
         }

         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mouseX);
         float uiMouseY = viewport.toUiY(mouseY);
         boolean active = RiptideOverlayManager.get().isFocusedOverlay(this) || RiptideOverlayManager.get().isTopOverlay(this);
         boolean headerHovered = this.isOverHeaderUi(uiMouseX, uiMouseY);
         DirectRenderContext metrics = new DirectRenderContext(context, MC.font, viewport, this.theme, uiMouseX, uiMouseY, delta);
         this.windowNode.setShowBody(!this.collapsed);
         this.windowNode.setActive(active);
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
         if (this.panelHeight > this.theme.headerHeight() + 1 && !this.currentItems.isEmpty()) {
            this.renderlistViewport(context, viewport, uiMouseX, uiMouseY, delta, active);
         } else {
            this.clickRegions.clear();
         }
      }
   }

   private void renderlistViewport(GuiGraphicsExtractor context, DirectViewport viewport, float uiMouseX, float uiMouseY, float delta, boolean active) {
      this.clickRegions.clear();
      int viewX = Math.round(this.listSlot.x());
      int viewY = Math.round(this.listSlot.y());
      int viewW = Math.round(this.listSlot.width());
      int viewH = Math.round(this.listSlot.height());
      if (viewW > 2 && viewH > 2) {
         CompactListViewport.Layout baseLayout = this.macroListLayout(viewX, viewY, viewW, viewH, 0);
         int drawScroll = this.listScroll.tick(delta, baseLayout.maxScroll());
         CompactListViewport.Layout listLayout = this.macroListLayout(viewX, viewY, viewW, viewH, drawScroll);
         float alpha = active ? 1.0F : 0.56F;
         UiRenderer.frame(
            context,
            UiBounds.of(viewX, viewY, viewW, viewH),
            DirectRenderContext.applyAlpha(this.theme.listFill(), alpha),
            DirectRenderContext.applyAlpha(this.theme.borderSoft(), alpha)
         );
         listLayout.beginRows(context);

         try {
            DirectRenderContext rowContext = new DirectRenderContext(context, this.textRenderer, viewport, this.theme, uiMouseX, uiMouseY, delta, alpha);
            listLayout.forEachVisibleRow(this.currentItems.size(), (i, rowY) -> {
               RiptideMacroListOverlay.DisplayItem item = this.currentItems.get(i);
               if (item.type == RiptideMacroListOverlay.ItemType.SECTION_HEADER) {
                  this.renderSectionHeader(context, rowContext, item, viewX + 1, rowY, listLayout.contentWidth());
               } else {
                  this.renderMacroRow(context, rowContext, item, viewX + 1, rowY, listLayout.contentWidth());
               }
            });
         } finally {
            listLayout.endRows(context);
         }

         listLayout.drawScrollbar(context, uiMouseX, uiMouseY, this.scrollbarDragging);
      }
   }

   private CompactScrollbar.Metrics getScrollbarMetrics(int viewX, int viewY, int viewW, int viewH) {
      CompactListViewport.Layout baseLayout = this.macroListLayout(viewX, viewY, viewW, viewH, 0);
      return this.macroListLayout(viewX, viewY, viewW, viewH, this.listScroll.tick(0.0F, baseLayout.maxScroll())).scrollbar();
   }

   private CompactListViewport.Layout macroListLayout(int viewX, int viewY, int viewW, int viewH, int scrollOffset) {
      return CompactListViewport.layout(viewX, viewY, viewW, viewH, this.currentItems.size(), this.rowHeight(), this.rowHeight(), scrollOffset, 3, 0, 1);
   }

   private void renderSectionHeader(
      GuiGraphicsExtractor context, DirectRenderContext rowContext, RiptideMacroListOverlay.DisplayItem item, int x, int y, int width
   ) {
      int textY = UiSizing.alignTextY(y, this.rowHeight(), this.theme.fontHeight(UiTone.LABEL), this.theme.bodyTextNudge());
      CompactSurfaces.divider(
         context, x + this.sectionHeaderInset(), y + this.rowHeight() - 2, width - this.sectionHeaderInset() * 2, rowContext.applyAlpha(1124073471)
      );
      UiText.draw(
         context,
         this.textRenderer,
         item.label,
         this.theme.fontFor(UiTone.LABEL),
         rowContext.applyAlpha(item.color),
         x + this.sectionHeaderInset(),
         textY,
         false
      );
   }

   private void renderMacroRow(GuiGraphicsExtractor context, DirectRenderContext rowContext, RiptideMacroListOverlay.DisplayItem item, int x, int y, int width) {
      boolean hovered = this.uiContains(x, y, width, this.rowHeight(), rowContext.mouseX(), rowContext.mouseY());
      if (hovered) {
         CompactSurfaces.tintedRow(context, x, y, width, this.rowHeight(), rowContext.applyAlpha(RiptideTheme.recolor(452938314, RiptideTheme.Channel.ACCENT)));
      }

      List<RiptideMacroListOverlay.RowButton> buttons = this.buildRowButtons(item);
      int buttonCursor = x + width - this.rowTextInset();
      int buttonY = y + this.rowButtonTopInset();

      for (int i = buttons.size() - 1; i >= 0; i--) {
         RiptideMacroListOverlay.RowButton button = buttons.get(i);
         buttonCursor -= button.width;
         this.drawRowButton(context, rowContext, buttonCursor, buttonY, button.width, button);
         this.clickRegions.add(new RiptideMacroListOverlay.ClickRegion(buttonCursor, buttonY, button.width, this.rowButtonHeight(), item, button.action));
         buttonCursor -= this.rowButtonGap();
      }

      int textLeft = x + this.rowTextInset();
      if (item.type == RiptideMacroListOverlay.ItemType.LOCAL_MACRO) {
         boolean running = macroRunningForCurrentControl(item.macro.name);
         int dotColor = running ? rowContext.applyAlpha(-13379224) : rowContext.applyAlpha(-10132123);
         int dotTop = y + UiSizing.alignMiddle(0, this.rowHeight(), this.rowDotSize());
         CompactSurfaces.indicator(context, textLeft, dotTop, this.rowDotSize(), this.rowDotSize(), dotColor);
         textLeft += this.rowTextInset() + 2;
      }

      int rightLimit = Math.max(textLeft + 20, buttonCursor - 2);
      int labelWidth = Math.max(20, rightLimit - textLeft);

      String primaryText = switch (item.type) {
         case LOCAL_MACRO -> item.macro.name;
         case REMOTE_MACRO -> item.label;
         case METEOR_MACRO -> "Meteor: " + item.macro.name;
         default -> item.label;
      };

      String secondaryText = switch (item.type) {
         case LOCAL_MACRO -> item.macro.actions.size() + " steps";
         case REMOTE_MACRO -> item.remoteSource != null && !item.remoteSource.isBlank() ? item.remoteSource : "";
         case METEOR_MACRO -> item.macro.actions.size() + " steps";
         default -> "";
      };

      int primaryColor = switch (item.type) {
         case LOCAL_MACRO -> rowContext.applyAlpha(this.theme.color(UiTone.BODY));
         case REMOTE_MACRO -> rowContext.applyAlpha(item.remoteMeteor ? -2698497 : -12122);
         case METEOR_MACRO -> rowContext.applyAlpha(-2562305);
         default -> rowContext.applyAlpha(this.theme.color(UiTone.BODY));
      };
      int secondaryColor = rowContext.applyAlpha(this.theme.color(UiTone.MUTED));
      int textY = UiSizing.alignTextY(y, this.rowHeight(), this.theme.fontHeight(UiTone.BODY), this.theme.bodyTextNudge());
      if (item.type == RiptideMacroListOverlay.ItemType.LOCAL_MACRO) {
         int secondaryWidth = UiText.width(this.textRenderer, secondaryText, this.theme.fontFor(UiTone.BODY), secondaryColor);
         int secondaryX = Math.max(textLeft + 10, rightLimit - secondaryWidth);
         int primaryMaxWidth = Math.max(20, secondaryX - textLeft - 8);
         String trimmedPrimary = UiText.trimToWidth(this.textRenderer, primaryText, primaryMaxWidth, this.theme.fontFor(UiTone.BODY), primaryColor);
         UiText.draw(context, this.textRenderer, trimmedPrimary, this.theme.fontFor(UiTone.BODY), primaryColor, textLeft, textY, false);
         UiText.draw(context, this.textRenderer, secondaryText, this.theme.fontFor(UiTone.BODY), secondaryColor, secondaryX, textY, false);
      } else {
         String trimmedPrimary = UiText.trimToWidth(this.textRenderer, primaryText, labelWidth, this.theme.fontFor(UiTone.BODY), primaryColor);
         UiText.draw(context, this.textRenderer, trimmedPrimary, this.theme.fontFor(UiTone.BODY), primaryColor, textLeft, textY, false);
         if (!secondaryText.isEmpty()) {
            int secondaryX = textLeft
               + Math.min(
                  labelWidth - 10,
                  Math.max(UiText.width(this.textRenderer, trimmedPrimary, this.theme.fontFor(UiTone.BODY), primaryColor) + 10, labelWidth - 90)
               );
            int secondaryWidth = Math.max(0, rightLimit - secondaryX);
            if (secondaryWidth > 18) {
               String trimmedSecondary = UiText.trimToWidth(this.textRenderer, secondaryText, secondaryWidth, this.theme.fontFor(UiTone.BODY), secondaryColor);
               UiText.draw(context, this.textRenderer, trimmedSecondary, this.theme.fontFor(UiTone.BODY), secondaryColor, secondaryX, textY, false);
            }
         }
      }
   }

   private void drawRowButton(GuiGraphicsExtractor context, DirectRenderContext rowContext, int x, int y, int width, RiptideMacroListOverlay.RowButton button) {
      if (button.action == RiptideMacroListOverlay.RowAction.DELETE) {
         int size = this.rowButtonHeight();
         CompactListRenderer.drawDeleteButton(
            context,
            x + Math.max(0, (width - size) / 2),
            y,
            size,
            this.uiContains(x, y, width, this.rowButtonHeight(), rowContext.mouseX(), rowContext.mouseY())
         );
      } else {
         int h = this.rowButtonHeight();
         boolean hovered = this.uiContains(x, y, width, h, rowContext.mouseX(), rowContext.mouseY());
         UiContext ui = UiContexts.overlay(
            rowContext.drawContext(), rowContext.textRenderer(), Math.round(rowContext.mouseX()), Math.round(rowContext.mouseY())
         );
         Button.render(ui, UiBounds.of(x, y, width, h), button.label, DirectUiButton.toneFor(button.variant), hovered, false);
         if (button.icon != null) {
            int iconSize = 8;
            RiptideUiIcons.blit(context, button.icon, x + (width - iconSize) / 2, y + (h - iconSize) / 2, iconSize, -218103809);
         }
      }
   }

   private int measureRowButtonWidth(String label, int minWidth, int maxWidth) {
      return UiSizing.fitTextWidthInt(
         this.textRenderer, label, this.theme.fontFor(UiTone.BODY), this.theme.color(UiTone.BODY), this.rowButtonPadding(), minWidth, maxWidth
      );
   }

   private List<RiptideMacroListOverlay.RowButton> buildRowButtons(RiptideMacroListOverlay.DisplayItem item) {
      this.rowButtonScratch.clear();
      switch (item.type) {
         case LOCAL_MACRO:
            boolean running = macroRunningForCurrentControl(item.macro.name);
            if (!this.configurationOnly) {
               this.rowButtonScratch
                  .add(
                     new RiptideMacroListOverlay.RowButton(
                        "",
                        running ? DirectUiButton.Variant.DANGER : DirectUiButton.Variant.SUCCESS,
                        20,
                        RiptideMacroListOverlay.RowAction.RUN_TOGGLE,
                        running ? RiptideUiIcons.STOP : RiptideUiIcons.PLAY
                     )
                  );
            }

            this.rowButtonScratch
               .add(new RiptideMacroListOverlay.RowButton("", DirectUiButton.Variant.DANGER, 20, RiptideMacroListOverlay.RowAction.EDIT, RiptideUiIcons.EDIT));
            this.rowButtonScratch
               .add(
                  new RiptideMacroListOverlay.RowButton(
                     "COPY", DirectUiButton.Variant.DANGER, this.measureRowButtonWidth("COPY", 26, 44), RiptideMacroListOverlay.RowAction.COPY
                  )
               );
            this.rowButtonScratch
               .add(
                  new RiptideMacroListOverlay.RowButton("X", DirectUiButton.Variant.DANGER, this.deleteButtonWidth(), RiptideMacroListOverlay.RowAction.DELETE)
               );
            break;
         case REMOTE_MACRO:
            this.rowButtonScratch
               .add(
                  new RiptideMacroListOverlay.RowButton(
                     "IMPORT", DirectUiButton.Variant.SECONDARY, this.measureRowButtonWidth("IMPORT", 34, 58), RiptideMacroListOverlay.RowAction.IMPORT_REMOTE
                  )
               );
            break;
         case METEOR_MACRO:
            this.rowButtonScratch
               .add(
                  new RiptideMacroListOverlay.RowButton(
                     "REFACTOR",
                     DirectUiButton.Variant.SECONDARY,
                     this.measureRowButtonWidth("REFACTOR", 42, 70),
                     RiptideMacroListOverlay.RowAction.IMPORT_METEOR
                  )
               );
      }

      return this.rowButtonScratch;
   }

   private void handleRowAction(RiptideMacroListOverlay.DisplayItem item, RiptideMacroListOverlay.RowAction action) {
      if (item != null && action != null) {
         switch (action) {
            case RUN_TOGGLE:
               if (this.configurationOnly) {
                  return;
               }

               if (item.macro == null) {
                  return;
               }

               if (macroRunningForCurrentControl(item.macro.name)) {
                  if (MultiTakeoverState.isActive()) {
                     MultiManager multi = MultiManager.getIfInitialized();
                     if (multi != null) {
                        multi.stopMacroOnInteractiveScope(Set.of());
                     }
                  } else {
                     MacroExecutor.stopMacro(item.macro.name);
                  }
               } else {
                  item.macro.execute();
               }
               break;
            case EDIT:
               if (item.macro == null) {
                  return;
               }

               this.setVisible(false);
               RiptideMacroEditorOverlay.getSharedOverlay().open(item.macro, true);
               break;
            case COPY:
               if (item.macro == null) {
                  return;
               }

               try {
                  if (!RiptideClipboardHelper.copyMacroToClipboard(item.macro)) {
                     RiptideNotifications.error("Failed to copy macro.");
                     return;
                  }

                  RiptideNotifications.copied("Copied macro: " + item.macro.name);
               } catch (Exception var4) {
                  RiptideNotifications.error("Failed to copy macro.");
               }
               break;
            case DELETE:
               if (item.macro != null) {
                  RiptideMacroManager.get().delete(item.macro);
               }
               break;
            case IMPORT_REMOTE:
               this.importRemoteMacro(item);
               break;
            case IMPORT_METEOR:
               this.importMeteorMacro(item);
         }
      }
   }

   private static boolean macroRunningForCurrentControl(String name) {
      if (!MultiTakeoverState.isActive()) {
         return MacroExecutor.isMacroRunning(name);
      } else {
         MultiManager multi = MultiManager.getIfInitialized();
         return multi != null && multi.isMacroPlayingOnInteractiveScope(name, Set.of());
      }
   }

   private void importRemoteMacro(RiptideMacroListOverlay.DisplayItem item) {
      if (item.remoteSource != null && !item.remoteSource.isBlank()) {
         RiptideLANSync sync = RiptideLANSync.getInstance();
         Map<String, Map<String, RiptideMacro>> allRemote = item.remoteMeteor ? sync.getAllRemoteMeteorMacros() : sync.getAllRemoteMacros();
         Map<String, RiptideMacro> remoteMacros = allRemote.get(item.remoteSource);
         RiptideMacro source = remoteMacros != null ? remoteMacros.get(MacroNames.key(item.label)) : null;
         if (source == null) {
            RiptideClientMessaging.sendPrefixed("§cRemote macro is no longer available: " + item.label);
         } else {
            RiptideMacro imported = RiptideMacroManager.get().addImportedCopy(source, source.name);
            if (imported == null) {
               RiptideClientMessaging.sendPrefixed("§cFailed to import macro: " + item.label);
            } else {
               String sourceType = item.remoteMeteor ? "Meteor macro" : "macro";
               RiptideClientMessaging.sendPrefixed("§aImported " + sourceType + " as: " + imported.name);
               this.needsUiRebuild = true;
            }
         }
      }
   }

   private void importMeteorMacro(RiptideMacroListOverlay.DisplayItem item) {
      if (item.macro != null) {
         RiptideMacro imported = RiptideMacroManager.get().addImportedCopy(item.macro, item.macro.name);
         if (imported == null) {
            RiptideClientMessaging.sendPrefixed("§cFailed to import from Meteor.");
         } else {
            RiptideClientMessaging.sendPrefixed("§aImported Meteor macro as: " + imported.name);
            this.needsUiRebuild = true;
         }
      }
   }

   private void openCreateNew() {
      this.setVisible(false);
      RiptideMacroEditorOverlay.getSharedOverlay().open(null, true);
   }

   private void beginPasteMode() {
      this.pasteMode = true;
      this.pasteNameField.setText("");
      this.listScroll.jumpTo(0, 0);
      this.needsUiRebuild = true;
   }

   private void cancelPasteMode() {
      this.pasteMode = false;
      this.pasteNameField.setText("");
      this.pasteNameField.setFocused(false);
      this.needsUiRebuild = true;
   }

   private boolean pasteMacroFromClipboard() {
      String name = this.pasteNameField.text().trim();
      if (name.isEmpty()) {
         RiptideClientMessaging.sendPrefixed("§cEnter a name for the pasted macro.");
         return true;
      } else if (RiptideMacroManager.get().get(name) != null) {
         RiptideClientMessaging.sendPrefixed("§cA macro with that name already exists.");
         return true;
      } else {
         RiptideMacro pasted = RiptideClipboardHelper.pasteMacroFromClipboard();
         if (pasted == null) {
            RiptideNotifications.error("Clipboard does not contain a valid macro.");
            return true;
         } else {
            pasted.name = name;
            RiptideMacroManager.get().add(pasted);
            RiptideClientMessaging.sendPrefixed("§aPasted macro: " + pasted.name);
            this.cancelPasteMode();
            return true;
         }
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
            this.setVisible(false);
            return true;
         } else if (button == 0 && this.isOverHeaderUi(uiMouseX, uiMouseY)) {
            this.dragging = true;
            this.dragOffsetX = uiMouseX - this.panelX;
            this.dragOffsetY = uiMouseY - this.panelY;
            return true;
         } else if (!this.collapsed && this.surface.mouseClicked(mouseX, mouseY, button)) {
            return true;
         } else {
            if (!this.collapsed
               && button == 0
               && this.uiContains(this.listSlot.x(), this.listSlot.y(), this.listSlot.width(), this.listSlot.height(), uiMouseX, uiMouseY)) {
               CompactScrollbar.Metrics metrics = this.getScrollbarMetrics(
                  Math.round(this.listSlot.x()), Math.round(this.listSlot.y()), Math.round(this.listSlot.width()), Math.round(this.listSlot.height())
               );
               if (metrics.hasScroll() && metrics.contains(uiMouseX, uiMouseY)) {
                  this.scrollbarDragging = true;
                  this.scrollbarGrabOffset = metrics.overThumb(uiMouseX, uiMouseY) ? Math.round(uiMouseY) - metrics.thumbY() : metrics.thumbHeight() / 2;
                  this.listScroll.setFromThumb(metrics, uiMouseY, this.scrollbarGrabOffset);
                  return true;
               }
            }

            if (!this.collapsed && button == 0) {
               for (int i = this.clickRegions.size() - 1; i >= 0; i--) {
                  RiptideMacroListOverlay.ClickRegion region = this.clickRegions.get(i);
                  if (region.contains(uiMouseX, uiMouseY)) {
                     this.handleRowAction(region.item, region.action);
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
      DirectViewport viewport = this.surface.viewport();
      float uiMouseX = viewport.toUiX(mouseX);
      float uiMouseY = viewport.toUiY(mouseY);
      if (button == 0 && this.dragging) {
         this.dragging = false;
         this.saveState();
         return true;
      } else if (button == 0 && this.scrollbarDragging) {
         this.scrollbarDragging = false;
         this.saveState();
         return true;
      } else {
         return !this.collapsed && this.surface.mouseReleased(mouseX, mouseY, button) ? true : this.isMouseOver(mouseX, mouseY);
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
            RiptideWindowLayout clamped = this.clampToViewport(
               new RiptideWindowLayout(nextX, nextY, this.panelWidth, this.panelHeight, this.visible, this.collapsed)
            );
            this.panelX = clamped.x;
            this.panelY = clamped.y;
            return true;
         } else if (this.scrollbarDragging && button == 0) {
            CompactScrollbar.Metrics metrics = this.getScrollbarMetrics(
               Math.round(this.listSlot.x()), Math.round(this.listSlot.y()), Math.round(this.listSlot.width()), Math.round(this.listSlot.height())
            );
            this.listScroll.setFromThumb(metrics, uiMouseY, this.scrollbarGrabOffset);
            return true;
         } else {
            return !this.collapsed && this.surface.mouseDragged(mouseX, mouseY, button, deltaX, deltaY) ? true : this.isMouseOver(mouseX, mouseY);
         }
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (this.visible && !this.collapsed) {
         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mouseX);
         float uiMouseY = viewport.toUiY(mouseY);
         if (this.surface.mouseScrolled(mouseX, mouseY, amount)) {
            return true;
         } else if (this.uiContains(this.listSlot.x(), this.listSlot.y(), this.listSlot.width(), this.listSlot.height(), uiMouseX, uiMouseY)) {
            CompactListViewport.Layout listLayout = this.macroListLayout(
               Math.round(this.listSlot.x()), Math.round(this.listSlot.y()), Math.round(this.listSlot.width()), Math.round(this.listSlot.height()), 0
            );
            this.listScroll.nudge(amount, this.rowHeight(), listLayout.maxScroll());
            return true;
         } else {
            return this.isMouseOver(mouseX, mouseY);
         }
      } else {
         return false;
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.visible) {
         return false;
      } else if (this.collapsed) {
         return false;
      } else if (this.surface.keyPressed(keyCode, scanCode, modifiers)) {
         return true;
      } else if (keyCode == 256 && this.pasteMode) {
         this.cancelPasteMode();
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      return this.visible && !this.collapsed && this.surface.charTyped(chr, modifiers);
   }

   private List<RiptideMacroListOverlay.DisplayItem> buildDisplayItems(
      List<RiptideMacro> localMacros,
      List<RiptideMacro> meteorMacros,
      Map<String, Map<String, RiptideMacro>> remoteMacros,
      Map<String, Map<String, RiptideMacro>> remoteMeteorMacros,
      RiptideLANSync sync
   ) {
      String filter = this.searchField.text().trim().toLowerCase(Locale.ROOT);
      List<RiptideMacroListOverlay.DisplayItem> items = new ArrayList<>();
      List<RiptideMacroListOverlay.DisplayItem> localSection = new ArrayList<>();

      for (RiptideMacro macro : localMacros) {
         if (!AutoFishStopMacroFactory.isGeneratedStopMacro(macro) && this.matchesFilter(macro.name, filter)) {
            localSection.add(RiptideMacroListOverlay.DisplayItem.localMacro(macro));
         }
      }

      if (!localSection.isEmpty()) {
         items.add(RiptideMacroListOverlay.DisplayItem.section("Riptide Client Macros", RiptideTheme.recolor(-43691, RiptideTheme.Channel.ACCENT)));
         items.addAll(localSection);
      }

      List<RiptideMacroListOverlay.DisplayItem> remoteSection = new ArrayList<>();
      if (sync.isInSession()) {
         for (Entry<String, Map<String, RiptideMacro>> entry : remoteMacros.entrySet()) {
            for (RiptideMacro macrox : entry.getValue().values()) {
               if (!AutoFishStopMacroFactory.isGeneratedStopMacro(macrox)
                  && RiptideMacroManager.get().get(macrox.name) == null
                  && this.matchesFilter(macrox.name, filter)) {
                  remoteSection.add(RiptideMacroListOverlay.DisplayItem.remoteMacro(macrox.name, entry.getKey(), false));
               }
            }
         }
      }

      if (!remoteSection.isEmpty()) {
         if (localSection.isEmpty()) {
            items.add(RiptideMacroListOverlay.DisplayItem.section("Riptide Client Macros", RiptideTheme.recolor(-43691, RiptideTheme.Channel.ACCENT)));
         }

         items.addAll(remoteSection);
      }

      if (!RiptideCompatManager.isMeteorAvailable()) {
         return items;
      } else {
         List<RiptideMacroListOverlay.DisplayItem> meteorSection = new ArrayList<>();

         for (RiptideMacro macroxx : meteorMacros) {
            if (this.matchesFilter(macroxx.name, filter)) {
               meteorSection.add(RiptideMacroListOverlay.DisplayItem.meteorMacro(macroxx));
            }
         }

         if (sync.isInSession()) {
            for (Entry<String, Map<String, RiptideMacro>> entry : remoteMeteorMacros.entrySet()) {
               for (RiptideMacro macroxxx : entry.getValue().values()) {
                  boolean alreadyLocalMeteor = false;

                  for (RiptideMacro localMeteor : meteorMacros) {
                     if (MacroNames.equal(localMeteor.name, macroxxx.name)) {
                        alreadyLocalMeteor = true;
                        break;
                     }
                  }

                  if (!alreadyLocalMeteor && this.matchesFilter(macroxxx.name, filter)) {
                     meteorSection.add(RiptideMacroListOverlay.DisplayItem.remoteMacro(macroxxx.name, entry.getKey(), true));
                  }
               }
            }
         }

         if (!meteorSection.isEmpty()) {
            items.add(RiptideMacroListOverlay.DisplayItem.section("Meteor Macros", -2562305));
            items.addAll(meteorSection);
         }

         return items;
      }
   }

   private int visibleLocalMacroCount() {
      long revision = RiptideMacroManager.get().getRevision();
      if (revision == this.cachedVisibleCountRevision) {
         return this.cachedVisibleLocalMacroCount;
      } else {
         int count = 0;

         for (RiptideMacro macro : RiptideMacroManager.get().getAll()) {
            if (!AutoFishStopMacroFactory.isGeneratedStopMacro(macro)) {
               count++;
            }
         }

         this.cachedVisibleCountRevision = revision;
         this.cachedVisibleLocalMacroCount = count;
         return this.cachedVisibleLocalMacroCount;
      }
   }

   private boolean matchesFilter(String name, String filter) {
      return filter.isEmpty() || name != null && name.toLowerCase(Locale.ROOT).contains(filter);
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
      int width = Math.max(Math.min(this.getMinWidth(), availableW), Math.min(bounds.width, availableW));
      int minHeight = bounds.collapsed ? this.theme.headerHeight() : this.getMinHeight();
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
      return 258;
   }

   private int rowHeight() {
      return 18;
   }

   private int maxVisibleRows() {
      return 6;
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

   private int actionRowGap() {
      return 4;
   }

   private int actionButtonHeight() {
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

   private int rowButtonHeight() {
      return 14;
   }

   private int deleteButtonWidth() {
      return 16;
   }

   private int rowButtonGap() {
      return 4;
   }

   private int rowButtonPadding() {
      return 2;
   }

   private int rowButtonTopInset() {
      return Math.max(1, (this.rowHeight() - this.rowButtonHeight()) / 2);
   }

   private int rowTextInset() {
      return 8;
   }

   private int sectionHeaderInset() {
      return 4;
   }

   private int rowDotSize() {
      return 4;
   }

   private static final class ClickRegion {
      private final int x;
      private final int y;
      private final int width;
      private final int height;
      private final RiptideMacroListOverlay.DisplayItem item;
      private final RiptideMacroListOverlay.RowAction action;

      private ClickRegion(int x, int y, int width, int height, RiptideMacroListOverlay.DisplayItem item, RiptideMacroListOverlay.RowAction action) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         this.item = item;
         this.action = action;
      }

      private boolean contains(float mouseX, float mouseY) {
         return mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY <= this.y + this.height;
      }
   }

   private static final class DisplayItem {
      private RiptideMacroListOverlay.ItemType type;
      private String label;
      private int color;
      private RiptideMacro macro;
      private String remoteSource;
      private boolean remoteMeteor;

      private static RiptideMacroListOverlay.DisplayItem section(String label, int color) {
         RiptideMacroListOverlay.DisplayItem item = new RiptideMacroListOverlay.DisplayItem();
         item.type = RiptideMacroListOverlay.ItemType.SECTION_HEADER;
         item.label = label;
         item.color = color;
         return item;
      }

      private static RiptideMacroListOverlay.DisplayItem localMacro(RiptideMacro macro) {
         RiptideMacroListOverlay.DisplayItem item = new RiptideMacroListOverlay.DisplayItem();
         item.type = RiptideMacroListOverlay.ItemType.LOCAL_MACRO;
         item.macro = macro;
         return item;
      }

      private static RiptideMacroListOverlay.DisplayItem remoteMacro(String label, String source, boolean remoteMeteor) {
         RiptideMacroListOverlay.DisplayItem item = new RiptideMacroListOverlay.DisplayItem();
         item.type = RiptideMacroListOverlay.ItemType.REMOTE_MACRO;
         item.label = label;
         item.remoteSource = source;
         item.remoteMeteor = remoteMeteor;
         return item;
      }

      private static RiptideMacroListOverlay.DisplayItem meteorMacro(RiptideMacro macro) {
         RiptideMacroListOverlay.DisplayItem item = new RiptideMacroListOverlay.DisplayItem();
         item.type = RiptideMacroListOverlay.ItemType.METEOR_MACRO;
         item.macro = macro;
         return item;
      }
   }

   private static enum ItemType {
      SECTION_HEADER,
      LOCAL_MACRO,
      REMOTE_MACRO,
      METEOR_MACRO;
   }

   private static enum RowAction {
      RUN_TOGGLE,
      EDIT,
      COPY,
      DELETE,
      IMPORT_REMOTE,
      IMPORT_METEOR;
   }

   private static final class RowButton {
      private final String label;
      private final DirectUiButton.Variant variant;
      private final int width;
      private final RiptideMacroListOverlay.RowAction action;
      private final Identifier icon;

      private RowButton(String label, DirectUiButton.Variant variant, int width, RiptideMacroListOverlay.RowAction action) {
         this(label, variant, width, action, null);
      }

      private RowButton(String label, DirectUiButton.Variant variant, int width, RiptideMacroListOverlay.RowAction action, Identifier icon) {
         this.label = label;
         this.variant = variant;
         this.width = width;
         this.action = action;
         this.icon = icon;
      }
   }
}
