package riptide.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.gui.screen.RiptideThemeColorScreen;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiInputResult;
import riptide.gui.vanillaui.components.CompactFieldFactory;
import riptide.gui.vanillaui.components.CompactKeybindButton;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.ConnectedButton;
import riptide.gui.vanillaui.components.Dropdown;
import riptide.gui.vanillaui.components.OverlayTopBar;
import riptide.gui.vanillaui.components.Toggle;
import riptide.gui.vanillaui.components.Tooltip;
import riptide.gui.vanillaui.components.UiSizing;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectCompactRow;
import riptide.gui.vanillaui.direct.DirectFormRow;
import riptide.gui.vanillaui.direct.DirectRenderContext;
import riptide.gui.vanillaui.direct.DirectSurface;
import riptide.gui.vanillaui.direct.DirectUiButton;
import riptide.gui.vanillaui.direct.DirectUiInsets;
import riptide.gui.vanillaui.direct.DirectUiLabel;
import riptide.gui.vanillaui.direct.DirectUiNode;
import riptide.gui.vanillaui.direct.DirectViewport;
import riptide.gui.vanillaui.direct.DirectWindow;
import riptide.modules.RiptideModule;

public class RiptideKeybindOverlay extends RiptideOverlayBase {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final String WINDOW_TITLE = "Settings";
   private static final String INSIDE_GUI_TOOLTIP = "Fire keybinds while a container screen is open. Blocked in chat/sign/book editors and focused text fields.";
   private static final String CUSTOM_MENU_TOOLTIP = "Use our custom main menu. Turn off for the normal vanilla/Fabric title screen (mods stay visible).";
   private static final int MIN_PANEL_WIDTH = 170;
   private static final int ROW_HEIGHT = 16;
   private static final int PAD = 3;
   private static final int HEADER_CONTROL = 10;
   private static final int ROW_LABEL_GAP = 3;
   private static final float HEADER_CLICK_DRAG_THRESHOLD = 3.0F;
   private final CompactTheme theme = new CompactTheme();
   private final DirectWindow windowNode = new DirectWindow("Settings");
   private final DirectSurface surface = new DirectSurface(this.theme, this.windowNode);
   private final List<RiptideKeybindOverlay.KeybindEntry> entries = new ArrayList<>();
   private final List<RiptideKeybindOverlay.KeybindRowNode> rowNodes = new ArrayList<>();
   private boolean dragging = false;
   private float dragOffsetX;
   private float dragOffsetY;
   private float pressStartUiX;
   private float pressStartUiY;
   private int pressStartPanelX;
   private int pressStartPanelY;
   private boolean dragMoved = false;
   private int capturingIndex = -1;
   private String hoveredTooltip = null;
   private float tooltipUiX;
   private float tooltipUiY;
   private int cachedLabelColumnWidth = -1;
   private int cachedPanelWidth = -1;
   private Dropdown prefixDropdown;

   public RiptideKeybindOverlay() {
      super("riptide-keybinds", 170, 142);
      this.panelX = 160;
      this.panelY = 46;
      if (!RiptideLiteVariant.enabled()) {
         this.entries
            .add(
               new RiptideKeybindOverlay.KeybindEntry(
                  "Module Menu", "Opens the Riptide module click GUI", () -> this.getConfig().keybindModuleMenu, v -> this.getConfig().keybindModuleMenu = v
               )
            );
      }

      this.entries
         .add(
            new RiptideKeybindOverlay.KeybindEntry(
               "Load GUI", "Restores the last saved GUI screen and handler", () -> this.getConfig().keybindLoadGui, v -> this.getConfig().keybindLoadGui = v
            )
         );
      this.entries
         .add(
            new RiptideKeybindOverlay.KeybindEntry(
               "Flush Queue", "Sends all queued packets to the server", () -> this.getConfig().keybindFlushQueue, v -> this.getConfig().keybindFlushQueue = v
            )
         );
      this.entries
         .add(
            new RiptideKeybindOverlay.KeybindEntry(
               "Clear Queue", "Discards all queued packets", () -> this.getConfig().keybindClearQueue, v -> this.getConfig().keybindClearQueue = v
            )
         );
      this.entries
         .add(
            new RiptideKeybindOverlay.KeybindEntry(
               "Toggle Logger",
               "Shows or hides the packet logger overlay",
               () -> this.getConfig().keybindToggleLogger,
               v -> this.getConfig().keybindToggleLogger = v
            )
         );
      this.entries
         .add(
            new RiptideKeybindOverlay.KeybindEntry(
               "Toggle Send", "Turns packet sending on or off", () -> this.getConfig().keybindToggleSend, v -> this.getConfig().keybindToggleSend = v
            )
         );
      this.entries
         .add(
            new RiptideKeybindOverlay.KeybindEntry(
               "Toggle Delay", "Turns packet delay on or off", () -> this.getConfig().keybindToggleDelay, v -> this.getConfig().keybindToggleDelay = v
            )
         );
      this.buildUi();
   }

   private void buildUi() {
      this.windowNode.setCenterTitle(false);
      this.windowNode.setTitleTone(UiTone.LABEL);
      this.windowNode.setHeaderControls(true, true);
      this.windowNode.setTitleAreaInsets(this.panelPadding() + 2, this.panelPadding() + this.headerControlSize() * 2 + 8);
      this.windowNode.content().clearChildren();
      this.windowNode.content().setGap(this.windowContentGap()).setPadding(DirectUiInsets.all(this.panelPadding()));
      this.windowNode
         .content()
         .add(
            new RiptideKeybindOverlay.ToggleRowNode(
               "Inside GUI",
               "Fire keybinds while a container screen is open. Blocked in chat/sign/book editors and focused text fields.",
               () -> this.getConfig().keybindInsideGui,
               v -> this.getConfig().keybindInsideGui = v
            )
         );
      if (!RiptideLiteVariant.enabled()) {
         this.windowNode
            .content()
            .add(
               new RiptideKeybindOverlay.ToggleRowNode(
                  "Custom Menu",
                  "Use our custom main menu. Turn off for the normal vanilla/Fabric title screen (mods stay visible).",
                  () -> this.getConfig().customMainMenu,
                  v -> {
                     RiptideConfig config = this.getConfig();
                     config.customMainMenu = v;
                     config.save();
                  }
               )
            );
      }

      this.windowNode.content().add(new RiptideKeybindOverlay.ScaleRowNode());
      this.windowNode
         .content()
         .add(new RiptideKeybindOverlay.ToggleRowNode("Auto Probe", "Auto-Scans on join.", () -> this.getConfig().autoProbePlugins, v -> {
            RiptideConfig config = this.getConfig();
            config.autoProbePlugins = v;
            config.save();
         }));
      this.windowNode
         .content()
         .add(new RiptideKeybindOverlay.ToggleRowNode("InfiniChat", "Remove chat length limit.", () -> this.getConfig().infiniChat, v -> {
            RiptideConfig config = this.getConfig();
            config.infiniChat = v;
            config.save();
         }));
      this.windowNode
         .content()
         .add(new RiptideKeybindOverlay.ToggleRowNode("Stop On Leave", "Stop macros on disconnect.", () -> this.getConfig().stopMacroOnLeave, v -> {
            RiptideConfig config = this.getConfig();
            config.stopMacroOnLeave = v;
            config.save();
         }));
      this.windowNode.content().add(new RiptideKeybindOverlay.PrefixRowNode());
      this.rowNodes.clear();

      for (int i = 0; i < this.entries.size(); i++) {
         RiptideKeybindOverlay.KeybindRowNode row = new RiptideKeybindOverlay.KeybindRowNode(i, this.entries.get(i));
         this.rowNodes.add(row);
         this.windowNode.content().add(row);
      }

      if (!RiptideLiteVariant.enabled()) {
         DirectCompactRow settingsPages = new DirectCompactRow().setGap(0).setPadding(DirectUiInsets.NONE).setUnderlineOnHover(false);
         settingsPages.setGrowX(true);
         settingsPages.add(
            new DirectUiButton("Theme Color", DirectUiButton.Variant.PRIMARY, this::openThemeColor)
               .setGrowX(true)
               .setConnectedStyle(true)
               .setConnectedEdges(ConnectedButton.LEFT_CELL)
               .setButtonHeight(this.chooserButtonHeight())
         );
         settingsPages.add(
            new DirectUiButton("Profiles", DirectUiButton.Variant.PRIMARY, this::openProfiles)
               .setGrowX(true)
               .setConnectedStyle(true)
               .setConnectedEdges(ConnectedButton.RIGHT_CELL)
               .setButtonHeight(this.chooserButtonHeight())
         );
         this.windowNode.content().add(settingsPages);
      }
   }

   private void openThemeColor() {
      MC.gui.setScreen(new RiptideThemeColorScreen(MC.gui.screen()));
   }

   private void openProfiles() {
      if (!RiptideLiteVariant.enabled()) {
         RiptideModule module = RiptideModule.get();
         IRiptideOverlay profiles = module == null ? null : module.getProfilesOverlay();
         if (profiles != null) {
            ((RiptideProfilesOverlay)profiles).setMainMenuMode(false);
            profiles.setVisible(true);
            RiptideOverlayManager manager = RiptideOverlayManager.get();
            manager.register(profiles, IRiptideOverlay.OverlayScope.BACKGROUND_STATUS);
            manager.bringToFront(profiles);
         }
      }
   }

   private RiptideConfig getConfig() {
      return RiptideConfig.getGlobal();
   }

   @Override
   public int getMinWidth() {
      return this.computePanelWidth();
   }

   @Override
   public int getMinHeight() {
      return this.theme.headerHeight() + this.bodyMinimumHeight();
   }

   @Override
   public RiptideWindowLayout getBounds() {
      return new RiptideWindowLayout(this.panelX, this.panelY, this.panelWidth, this.panelHeight, this.visible, this.collapsed);
   }

   @Override
   public void setBounds(RiptideWindowLayout bounds) {
      RiptideWindowLayout clamped = this.clampToViewport(bounds);
      this.panelX = clamped.x;
      this.panelY = clamped.y;
      this.panelWidth = clamped.width;
      this.panelHeight = clamped.height;
      this.visible = clamped.visible;
      this.collapsed = clamped.collapsed;
      this.windowNode.syncShowBody(!this.collapsed);
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      if (this.visible) {
         this.hoveredTooltip = null;
         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mouseX);
         float uiMouseY = viewport.toUiY(mouseY);
         this.panelWidth = this.computePanelWidth();
         boolean active = RiptideOverlayManager.get().isFocusedOverlay(this) || RiptideOverlayManager.get().isTopOverlay(this);
         boolean headerHovered = uiMouseX >= this.panelX
            && uiMouseX < this.panelX + this.panelWidth
            && uiMouseY >= this.panelY
            && uiMouseY < this.panelY + this.theme.headerHeight();
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
         if (!this.collapsed && this.prefixDropdown != null && this.prefixDropdown.isOpen()) {
            context.nextStratum();
            viewport.push(context);

            try {
               this.prefixDropdown.render(UiContexts.overlay(context, MC.font, Math.round(uiMouseX), Math.round(uiMouseY)));
            } finally {
               viewport.pop(context);
            }
         }

         if (!this.collapsed && this.hoveredTooltip != null) {
            context.nextStratum();
            this.renderTooltip(context, viewport, this.hoveredTooltip, this.tooltipUiX, this.tooltipUiY);
         }
      }
   }

   private void renderTooltip(GuiGraphicsExtractor context, DirectViewport viewport, String text, float uiX, float uiY) {
      viewport.push(context);

      try {
         Tooltip.render(UiContexts.overlay(context, MC.font, Math.round(uiX), Math.round(uiY)), text, Math.round(uiX), Math.round(uiY), this.tooltipMaxWidth());
      } finally {
         viewport.pop(context);
      }
   }

   public static String getKeyName(int keyCode) {
      return RiptideBindUtil.getBindName(keyCode);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.visible) {
         return false;
      } else {
         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mouseX);
         float uiMouseY = viewport.toUiY(mouseY);
         if (this.prefixDropdown != null && this.prefixDropdown.isOpen()) {
            UiInputResult result = this.prefixDropdown.mouseClicked(Math.round(uiMouseX), Math.round(uiMouseY), button);
            if (!this.prefixDropdown.isOpen()) {
               this.prefixDropdown = null;
            }

            return result == UiInputResult.HANDLED;
         } else if (this.isOverCloseButton(uiMouseX, uiMouseY)) {
            this.visible = false;
            this.capturingIndex = -1;
            this.dragging = false;
            this.dragMoved = false;
            return true;
         } else if (button == 0 && this.isOverDragBarUi(uiMouseX, uiMouseY)) {
            this.dragging = true;
            this.dragMoved = false;
            this.dragOffsetX = uiMouseX - this.panelX;
            this.dragOffsetY = uiMouseY - this.panelY;
            this.pressStartUiX = uiMouseX;
            this.pressStartUiY = uiMouseY;
            this.pressStartPanelX = this.panelX;
            this.pressStartPanelY = this.panelY;
            return true;
         } else if (this.collapsed) {
            return this.isMouseOver(mouseX, mouseY);
         } else if (this.capturingIndex >= 0 && RiptideBindUtil.isAllowedMouseButton(button)) {
            this.captureBind(RiptideBindUtil.encodeMouseButton(button));
            return true;
         } else {
            return this.surface.mouseClicked(mouseX, mouseY, button) ? true : this.isMouseOver(mouseX, mouseY);
         }
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.visible || this.collapsed) {
         return false;
      } else if (this.prefixDropdown != null && this.prefixDropdown.isOpen() && keyCode == 256) {
         this.prefixDropdown.close();
         this.prefixDropdown = null;
         return true;
      } else if (this.surface.keyPressed(keyCode, scanCode, modifiers)) {
         return true;
      } else if (this.visible && this.capturingIndex >= 0) {
         this.captureBind(CompactKeybindButton.keyOrClear(keyCode));
         return true;
      } else {
         return false;
      }
   }

   private void captureBind(int bindCode) {
      for (int i = 0; i < this.entries.size(); i++) {
         if (i != this.capturingIndex && this.entries.get(i).getter.getAsInt() == bindCode) {
            this.entries.get(i).setter.accept(-1);
         }
      }

      this.entries.get(this.capturingIndex).setter.accept(bindCode);
      this.getConfig().save();
      this.capturingIndex = -1;
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.prefixDropdown != null && this.prefixDropdown.isOpen()) {
         DirectViewport viewport = this.surface.viewport();
         return this.prefixDropdown.mouseReleased(Math.round(viewport.toUiX(mouseX)), Math.round(viewport.toUiY(mouseY)), button) == UiInputResult.HANDLED;
      } else if (this.dragging) {
         this.dragging = false;
         this.saveLayout();
         return true;
      } else {
         return this.collapsed ? false : this.surface.mouseReleased(mouseX, mouseY, button);
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (this.prefixDropdown != null && this.prefixDropdown.isOpen()) {
         DirectViewport viewport = this.surface.viewport();
         return this.prefixDropdown.mouseDragged(Math.round(viewport.toUiX(mouseX)), Math.round(viewport.toUiY(mouseY)), button, deltaX, deltaY)
            == UiInputResult.HANDLED;
      } else if (!this.dragging) {
         return this.collapsed ? false : this.surface.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
      } else {
         DirectViewport viewport = this.surface.viewport();
         float uiMouseX = viewport.toUiX(mouseX);
         float uiMouseY = viewport.toUiY(mouseY);
         RiptideWindowLayout clamped = this.clampToViewport(
            new RiptideWindowLayout(
               Math.round(uiMouseX - this.dragOffsetX),
               Math.round(uiMouseY - this.dragOffsetY),
               this.panelWidth,
               this.panelHeight,
               this.visible,
               this.collapsed
            )
         );
         this.panelX = clamped.x;
         this.panelY = clamped.y;
         this.dragMoved = this.dragMoved
            || Math.abs(uiMouseX - this.pressStartUiX) >= 3.0F
            || Math.abs(uiMouseY - this.pressStartUiY) >= 3.0F
            || this.panelX != this.pressStartPanelX
            || this.panelY != this.pressStartPanelY;
         return true;
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (this.collapsed) {
         return false;
      } else if (this.prefixDropdown != null && this.prefixDropdown.isOpen()) {
         DirectViewport viewport = this.surface.viewport();
         return this.prefixDropdown.mouseScrolled(Math.round(viewport.toUiX(mouseX)), Math.round(viewport.toUiY(mouseY)), amount) == UiInputResult.HANDLED;
      } else {
         return this.surface.mouseScrolled(mouseX, mouseY, amount);
      }
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      return this.visible && !this.collapsed ? this.surface.charTyped(chr, modifiers) : false;
   }

   @Override
   public boolean isVisible() {
      return this.visible;
   }

   @Override
   public void setVisible(boolean v) {
      this.visible = v;
      if (!v) {
         this.capturingIndex = -1;
         this.dragging = false;
         this.dragMoved = false;
         this.closePrefixDropdown();
      } else {
         this.windowNode.syncShowBody(!this.collapsed);
         RiptideOverlayManager.get().bringToFront(this);
      }
   }

   public void toggle() {
      this.setVisible(!this.visible);
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else {
         DirectViewport viewport = this.surface.viewport();
         float uiX = viewport.toUiX(mouseX);
         float uiY = viewport.toUiY(mouseY);
         return uiX >= this.panelX && uiX < this.panelX + this.panelWidth && uiY >= this.panelY && uiY < this.panelY + this.panelHeight;
      }
   }

   @Override
   public boolean isOverDragBar(double mouseX, double mouseY) {
      if (!this.visible) {
         return false;
      } else {
         DirectViewport viewport = this.surface.viewport();
         return this.isOverDragBarUi(viewport.toUiX(mouseX), viewport.toUiY(mouseY));
      }
   }

   private boolean isOverDragBarUi(float uiMouseX, float uiMouseY) {
      return OverlayTopBar.isOverDragArea(this.currentWindowBounds(), this.theme.headerHeight(), false, true, uiMouseX, uiMouseY);
   }

   private boolean isOverHeaderUi(float uiMouseX, float uiMouseY) {
      return OverlayTopBar.isOverHeader(this.currentWindowBounds(), this.theme.headerHeight(), uiMouseX, uiMouseY);
   }

   @Override
   public boolean isCollapsed() {
      return this.collapsed;
   }

   @Override
   public boolean usesSharedHeaderClickCollapse() {
      return true;
   }

   @Override
   public void setCollapsed(boolean c) {
      if (this.collapsed != c) {
         this.collapsed = c;
         this.windowNode.syncShowBody(!this.collapsed);
         if (c) {
            this.capturingIndex = -1;
            this.closePrefixDropdown();
            this.clearHiddenInteractionState();
         }

         this.saveLayout();
      }
   }

   @Override
   public boolean hasTextFieldFocused() {
      return this.surface.hasFocusedTextInput();
   }

   @Override
   public boolean wantsKeyboardCapture() {
      return this.visible && this.capturingIndex >= 0;
   }

   @Override
   public void clearTextFieldFocus() {
      this.closePrefixDropdown();
      this.surface.clearFocusedTextInputs();
   }

   private boolean isOverCloseButton(float uiMouseX, float uiMouseY) {
      return OverlayTopBar.isOverClose(this.currentWindowBounds(), this.theme.headerHeight(), uiMouseX, uiMouseY);
   }

   private UiBounds currentWindowBounds() {
      return UiBounds.of(this.panelX, this.panelY, this.panelWidth, Math.max(this.theme.headerHeight(), this.panelHeight));
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

   private int computeLabelColumnWidth() {
      if (MC.font == null) {
         return this.labelColumnFallbackWidth();
      } else if (this.cachedLabelColumnWidth > 0) {
         return this.cachedLabelColumnWidth;
      } else {
         List<String> labels = new ArrayList<>(this.entries.size() + 3);
         labels.add("Inside GUI");
         labels.add("Custom Menu");
         labels.add("Overlay Scale");
         labels.add("Auto Probe");
         labels.add("Command Prefix");

         for (RiptideKeybindOverlay.KeybindEntry entry : this.entries) {
            labels.add(entry.label);
         }

         this.cachedLabelColumnWidth = Math.max(
            this.labelColumnMinimumWidth(),
            UiSizing.measureWidestText(MC.font, this.theme.fontFor(UiTone.BODY), this.theme.color(UiTone.BODY), labels) + this.labelColumnExtraWidth()
         );
         return this.cachedLabelColumnWidth;
      }
   }

   private int computeBodyWidth() {
      return MC.font == null ? 164 : this.computeLabelColumnWidth() + this.rowLabelGap() + this.bindButtonWidth();
   }

   private int computePanelWidth() {
      if (MC.font == null) {
         return 170;
      } else if (this.cachedPanelWidth > 0) {
         return this.cachedPanelWidth;
      } else {
         int headerReserve = this.headerControlSize() * 2 + this.panelPadding() * 2 + 14;
         int titleWidth = UiText.width(MC.font, "Settings", this.theme.fontFor(UiTone.TITLE), this.theme.color(UiTone.TITLE));
         this.cachedPanelWidth = Math.max(
            this.panelMinimumWidth(), Math.max(this.computeBodyWidth() + this.panelPadding() * 2, titleWidth + headerReserve + this.panelPadding())
         );
         return this.cachedPanelWidth;
      }
   }

   private void selectCommandPrefix(String requested) {
      String selected = RiptideCompatManager.normalizeStoredCommandPrefix(requested);
      RiptideModule module = RiptideModule.get();
      if (module != null) {
         module.setCommandPrefix(selected);
      } else {
         this.getConfig().commandPrefix = selected;
         this.getConfig().save();
      }

      if (!selected.equals(requested)) {
         RiptideNotifications.warning("Meteor uses '.'. Prefix kept as '%'.");
      }
   }

   private void closePrefixDropdown() {
      if (this.prefixDropdown != null) {
         this.prefixDropdown.close();
      }

      this.prefixDropdown = null;
   }

   private int panelMinimumWidth() {
      return 170;
   }

   private int panelPadding() {
      return 3;
   }

   private int windowContentGap() {
      return 0;
   }

   private int bodyMinimumHeight() {
      return 20;
   }

   private int headerControlSize() {
      return 10;
   }

   private int headerControlInset() {
      return 2;
   }

   private int tooltipMaxWidth() {
      return 170;
   }

   private int tooltipPadding() {
      return 3;
   }

   private int tooltipOffsetX() {
      return 8;
   }

   private int tooltipOffsetY() {
      return 2;
   }

   private int labelColumnFallbackWidth() {
      return 82;
   }

   private int labelColumnMinimumWidth() {
      return 68;
   }

   private int labelColumnExtraWidth() {
      return 0;
   }

   private int rowLabelGap() {
      return 3;
   }

   private int bindButtonWidth() {
      return 34;
   }

   private int bindButtonPadding() {
      return 2;
   }

   private int chooserButtonHeight() {
      return 14;
   }

   private int rowHeight() {
      return 16;
   }

   private static final class KeybindButtonNode extends DirectUiNode {
      private final IntSupplier bindCode;
      private final BooleanSupplier capturing;
      private final Runnable onPress;

      private KeybindButtonNode(IntSupplier bindCode, BooleanSupplier capturing, Runnable onPress) {
         this.bindCode = bindCode;
         this.capturing = capturing;
         this.onPress = onPress;
         this.width = 34.0F;
         this.height = 11.0F;
      }

      @Override
      public float preferredWidth(DirectRenderContext context) {
         return 34.0F;
      }

      @Override
      public float preferredHeight(DirectRenderContext context, float availableWidth) {
         return 11.0F;
      }

      @Override
      public void render(DirectRenderContext context) {
         if (this.visible) {
            UiBounds bounds = UiBounds.of(Math.round(this.x), Math.round(this.y), Math.round(this.width), Math.round(this.height));
            boolean hovered = this.enabled && this.contains(context.mouseX(), context.mouseY());
            CompactKeybindButton.render(
               UiContexts.overlay(context.drawContext(), context.textRenderer(), Math.round(context.mouseX()), Math.round(context.mouseY())),
               bounds,
               this.bindCode.getAsInt(),
               this.capturing.getAsBoolean(),
               hovered
            );
         }
      }

      @Override
      public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
         if (this.enabled && this.contains(mouseX, mouseY) && button == 0) {
            if (this.onPress != null) {
               this.onPress.run();
            }

            return true;
         } else {
            return false;
         }
      }
   }

   private static class KeybindEntry {
      final String label;
      final String tooltip;
      final IntSupplier getter;
      final IntConsumer setter;

      KeybindEntry(String label, String tooltip, IntSupplier getter, IntConsumer setter) {
         this.label = label;
         this.tooltip = tooltip;
         this.getter = getter;
         this.setter = setter;
      }
   }

   private final class KeybindRowNode extends DirectFormRow {
      private final int index;
      private final RiptideKeybindOverlay.KeybindEntry entry;
      private final DirectUiLabel labelNode;

      private KeybindRowNode(int index, RiptideKeybindOverlay.KeybindEntry entry) {
         Objects.requireNonNull(RiptideKeybindOverlay.this);
         super(
            new DirectUiLabel(entry.label, UiTone.BODY).setTrimToBounds(true),
            new RiptideKeybindOverlay.KeybindButtonNode(
               entry.getter,
               () -> RiptideKeybindOverlay.this.capturingIndex == index,
               () -> RiptideKeybindOverlay.this.capturingIndex = RiptideKeybindOverlay.this.capturingIndex == index ? -1 : index
            )
         );
         this.index = index;
         this.entry = entry;
         this.height = 16.0F;
         this.growX = true;
         this.labelNode = (DirectUiLabel)this.labelNode();
         this.setLabelWidth(RiptideKeybindOverlay.this.computeLabelColumnWidth());
         this.setGap(RiptideKeybindOverlay.this.rowLabelGap());
         this.setAlignControlEnd(true);
         this.setPadding(DirectUiInsets.NONE);
      }

      @Override
      public float preferredWidth(DirectRenderContext context) {
         return RiptideKeybindOverlay.this.panelWidth - RiptideKeybindOverlay.this.panelPadding() * 2;
      }

      @Override
      public float preferredHeight(DirectRenderContext context, float availableWidth) {
         return RiptideKeybindOverlay.this.rowHeight();
      }

      @Override
      public void render(DirectRenderContext context) {
         this.labelNode.setTone(UiTone.BODY);
         super.render(context);
         if (this.labelNode.contains(context.mouseX(), context.mouseY())) {
            RiptideKeybindOverlay.this.hoveredTooltip = this.entry.tooltip;
            RiptideKeybindOverlay.this.tooltipUiX = context.mouseX() + RiptideKeybindOverlay.this.tooltipOffsetX();
            RiptideKeybindOverlay.this.tooltipUiY = context.mouseY() - RiptideKeybindOverlay.this.tooltipOffsetY();
         }
      }

      @Override
      public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
         return super.mouseClicked(context, mouseX, mouseY, button);
      }
   }

   private final class PrefixRowNode extends DirectUiNode {
      private UiBounds controlBounds;

      private PrefixRowNode() {
         Objects.requireNonNull(RiptideKeybindOverlay.this);
         super();
         this.controlBounds = UiBounds.of(0, 0, 1, 1);
         this.height = 16.0F;
         this.growX = true;
      }

      @Override
      public float preferredWidth(DirectRenderContext context) {
         return RiptideKeybindOverlay.this.panelWidth - RiptideKeybindOverlay.this.panelPadding() * 2;
      }

      @Override
      public float preferredHeight(DirectRenderContext context, float availableWidth) {
         return RiptideKeybindOverlay.this.rowHeight();
      }

      @Override
      public void render(DirectRenderContext context) {
         int rowX = Math.round(this.x);
         int rowY = Math.round(this.y);
         int rowW = Math.round(this.width);
         int rowH = Math.round(this.height);
         int controlW = RiptideKeybindOverlay.this.bindButtonWidth();
         this.controlBounds = UiBounds.of(rowX + Math.max(0, rowW - controlW), rowY + 1, controlW, Math.max(12, rowH - 2));
         UiText.drawFitted(
            context.drawContext(),
            context.textRenderer(),
            "Command Prefix",
            RiptideKeybindOverlay.this.theme.fontFor(UiTone.BODY),
            RiptideKeybindOverlay.this.theme.color(UiTone.BODY),
            rowX,
            rowY + Math.max(0, (rowH - UiText.fontHeight(RiptideKeybindOverlay.this.theme.fontFor(UiTone.BODY))) / 2),
            Math.max(1, rowW - controlW - RiptideKeybindOverlay.this.rowLabelGap()),
            false
         );
         String value = RiptideCompatManager.effectiveCommandPrefix();
         CompactFieldFactory.dropdown(
            UiContexts.overlay(context.drawContext(), context.textRenderer(), Math.round(context.mouseX()), Math.round(context.mouseY())),
            this.controlBounds,
            value,
            this.controlBounds.contains(Math.round(context.mouseX()), Math.round(context.mouseY())),
            RiptideKeybindOverlay.this.prefixDropdown != null && RiptideKeybindOverlay.this.prefixDropdown.isOpen()
         );
      }

      @Override
      public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
         if (button == 0 && this.controlBounds.contains(Math.round(mouseX), Math.round(mouseY))) {
            RiptideKeybindOverlay.this.prefixDropdown = new Dropdown(
               this.controlBounds,
               RiptideCompatManager.COMMAND_PREFIX_CHOICES,
               RiptideCompatManager.effectiveCommandPrefix(),
               RiptideKeybindOverlay.this::selectCommandPrefix
            );
            RiptideKeybindOverlay.this.prefixDropdown.open();
            return true;
         } else {
            return false;
         }
      }
   }

   private final class ScaleRowNode extends DirectFormRow {
      private final DirectUiLabel labelNode;
      private final DirectUiButton scaleButton;
      private final String tooltip;

      private ScaleRowNode() {
         Objects.requireNonNull(RiptideKeybindOverlay.this);
         super(
            new DirectUiLabel("Overlay Scale", UiTone.BODY).setTrimToBounds(true),
            new DirectUiButton("1x", DirectUiButton.Variant.SECONDARY, null)
               .setPreferredWidth(RiptideKeybindOverlay.this.bindButtonWidth())
               .setMinWidth(RiptideKeybindOverlay.this.bindButtonWidth())
               .setMaxWidth(RiptideKeybindOverlay.this.bindButtonWidth())
               .setHorizontalPadding(RiptideKeybindOverlay.this.bindButtonPadding())
               .setButtonHeight(RiptideKeybindOverlay.this.chooserButtonHeight())
               .setConnectedEdges(ConnectedButton.FULL)
               .setTextYOffset(0)
         );
         this.tooltip = "Scales every Riptide overlay/window. Left-click increases, right-click decreases. 1x is default; 1.5x/2x are for 4K/high-DPI screens.";
         this.labelNode = (DirectUiLabel)this.labelNode();
         this.scaleButton = (DirectUiButton)this.controlNode();
         this.scaleButton.setOnPress(() -> {
            RiptideUiScale.setOverlayScaleMultiplier(RiptideUiScale.nextOverlayScaleMultiplier());
            RiptideKeybindOverlay.this.surface.invalidateLayout();
         });
         this.height = 16.0F;
         this.growX = true;
         this.setLabelWidth(RiptideKeybindOverlay.this.computeLabelColumnWidth());
         this.setGap(RiptideKeybindOverlay.this.rowLabelGap());
         this.setAlignControlEnd(true);
         this.setPadding(DirectUiInsets.NONE);
      }

      @Override
      public float preferredWidth(DirectRenderContext context) {
         return RiptideKeybindOverlay.this.panelWidth - RiptideKeybindOverlay.this.panelPadding() * 2;
      }

      @Override
      public float preferredHeight(DirectRenderContext context, float availableWidth) {
         return RiptideKeybindOverlay.this.rowHeight();
      }

      @Override
      public void render(DirectRenderContext context) {
         double scale = RiptideUiScale.getOverlayScaleMultiplier();
         this.scaleButton.setText(RiptideUiScale.formatOverlayScale(scale));
         this.scaleButton.setVariant(scale > 1.0 ? DirectUiButton.Variant.PRIMARY : DirectUiButton.Variant.SECONDARY);
         super.render(context);
         if (this.labelNode.contains(context.mouseX(), context.mouseY())) {
            RiptideKeybindOverlay.this.hoveredTooltip = "Scales every Riptide overlay/window. Left-click increases, right-click decreases. 1x is default; 1.5x/2x are for 4K/high-DPI screens.";
            RiptideKeybindOverlay.this.tooltipUiX = context.mouseX() + RiptideKeybindOverlay.this.tooltipOffsetX();
            RiptideKeybindOverlay.this.tooltipUiY = context.mouseY() - RiptideKeybindOverlay.this.tooltipOffsetY();
         }
      }

      @Override
      public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
         if ((button == 0 || button == 1) && this.scaleButton.contains(mouseX, mouseY)) {
            RiptideUiScale.setOverlayScaleMultiplier(
               button == 1 ? RiptideUiScale.previousOverlayScaleMultiplier() : RiptideUiScale.nextOverlayScaleMultiplier()
            );
            RiptideKeybindOverlay.this.surface.invalidateLayout();
            return true;
         } else {
            return super.mouseClicked(context, mouseX, mouseY, button);
         }
      }
   }

   private final class ToggleRowNode extends DirectFormRow {
      private final BooleanSupplier getter;
      private final Consumer<Boolean> setter;
      private final String tooltip;
      private final DirectUiLabel labelNode;
      private final DirectUiButton toggleButton;

      private ToggleRowNode(String label, String tooltip, BooleanSupplier getter, Consumer<Boolean> setter) {
         Objects.requireNonNull(RiptideKeybindOverlay.this);
         super(
            new DirectUiLabel(label, UiTone.BODY).setTrimToBounds(true),
            new DirectUiButton("OFF", DirectUiButton.Variant.SECONDARY, null)
               .setConnectedToggle(true)
               .setPreferredWidth(RiptideKeybindOverlay.this.bindButtonWidth())
               .setMinWidth(RiptideKeybindOverlay.this.bindButtonWidth())
               .setMaxWidth(RiptideKeybindOverlay.this.bindButtonWidth())
               .setHorizontalPadding(RiptideKeybindOverlay.this.bindButtonPadding())
               .setButtonHeight(RiptideKeybindOverlay.this.chooserButtonHeight())
               .setConnectedEdges(ConnectedButton.FULL)
               .setTextYOffset(0)
         );
         this.getter = getter;
         this.setter = setter;
         this.tooltip = tooltip;
         this.labelNode = (DirectUiLabel)this.labelNode();
         this.toggleButton = (DirectUiButton)this.controlNode();
         this.toggleButton.setOnPress(() -> {
            setter.accept(!getter.getAsBoolean());
            RiptideKeybindOverlay.this.getConfig().save();
         });
         this.height = 16.0F;
         this.growX = true;
         this.setLabelWidth(RiptideKeybindOverlay.this.computeLabelColumnWidth());
         this.setGap(RiptideKeybindOverlay.this.rowLabelGap());
         this.setAlignControlEnd(true);
         this.setPadding(DirectUiInsets.NONE);
      }

      @Override
      public float preferredWidth(DirectRenderContext context) {
         return RiptideKeybindOverlay.this.panelWidth - RiptideKeybindOverlay.this.theme.scale(6);
      }

      @Override
      public float preferredHeight(DirectRenderContext context, float availableWidth) {
         return RiptideKeybindOverlay.this.rowHeight();
      }

      @Override
      public void render(DirectRenderContext context) {
         boolean on = this.getter.getAsBoolean();
         this.labelNode.render(context);
         UiContext ui = UiContexts.overlay(context.drawContext(), context.textRenderer(), Math.round(context.mouseX()), Math.round(context.mouseY()));
         UiBounds pillBounds = UiBounds.of(
            Math.round(this.toggleButton.x()), Math.round(this.toggleButton.y()), Math.round(this.toggleButton.width()), Math.round(this.toggleButton.height())
         );
         Toggle.render(ui, pillBounds, on, pillBounds.contains(ui.mouseX(), ui.mouseY()), "keybind:" + this.tooltip);
         if (this.labelNode.contains(context.mouseX(), context.mouseY())) {
            RiptideKeybindOverlay.this.hoveredTooltip = this.tooltip;
            RiptideKeybindOverlay.this.tooltipUiX = context.mouseX() + RiptideKeybindOverlay.this.tooltipOffsetX();
            RiptideKeybindOverlay.this.tooltipUiY = context.mouseY() - RiptideKeybindOverlay.this.tooltipOffsetY();
         }
      }
   }
}
