package riptide.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import riptide.gui.multi.MultiMenuInput;
import riptide.gui.multi.MultiMenuRenderer;
import riptide.gui.multi.MultiTooltip;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.components.Button;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactWindow;
import riptide.util.multi.MultiClientCommands;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiSession;
import riptide.util.multi.MultiSharedGui;

public final class RiptideMultiGuiOverlay extends RiptideOverlayBase {
   private static final int CELL = 18;
   private static final int PAD = 7;
   private static final int TITLE_H = 13;
   private static final int INFO_H = 12;
   private static final int TOOLBAR_H = 17;
   private static final int GRID_BOTTOM_MARGIN = 3;
   private static final int PANEL_BG = -3750202;
   private static final int SLOT_FILL = -7631989;
   private static final int SLOT_SHADOW = -13158601;
   private static final int SLOT_LIGHT = -1;
   private static final int SLOT_HOVER = -2130706433;
   private static final int TEXT = -855310;
   private static final int BORDER = -12174784;
   private static final int SUCCESS = -13248397;
   private static final int DANGER = -42149;
   private final Minecraft mc = Minecraft.getInstance();
   private final Font font;
   private final List<RiptideMultiGuiOverlay.ActionHit> actions = new ArrayList<>();
   private final List<RiptideMultiGuiOverlay.SlotHit> slotHits = new ArrayList<>();
   private final List<MultiMenuRenderer.MenuHit> widgetHits = new ArrayList<>();
   private final MultiMenuInput menuInput = new MultiMenuInput();
   private String accountId = "";
   private String accountName = "";
   private final boolean shared;
   private String sharedKey = "";
   private List<MultiSharedGui.Group> sharedGroups = List.of();
   private MultiSession.MenuView view;
   private String viewFor = "";
   private long viewRevision = Long.MIN_VALUE;
   private int contentWidth = 18;
   private int contentHeight = 18;
   private int scrollY;
   private int gridX;
   private int gridY;
   private int gridWidth;
   private int gridHeight;
   private boolean dragging;
   private boolean scrollbarDragging;
   private int scrollbarGrab;
   private double dragOffsetX;
   private double dragOffsetY;
   private ItemStack hoveredStack = ItemStack.EMPTY;
   private int hoveredHandler = -1;
   private int hoveredHotbar = -1;
   private int hoveredX;
   private int hoveredY;
   private int[] closeSilentToolbarRect;
   private int lastSizedContentW = -1;
   private int lastSizedContentH = -1;
   private int lastSizedScreenH = -1;

   public RiptideMultiGuiOverlay(Font font, String accountId) {
      this(font, accountId, false);
   }

   public static RiptideMultiGuiOverlay shared(Font font) {
      return new RiptideMultiGuiOverlay(font, "@shared", true);
   }

   private RiptideMultiGuiOverlay(Font font, String accountId, boolean shared) {
      super("riptide-multi-gui-" + (shared ? "shared" : (accountId == null ? "" : accountId)), 230, shared ? 258 : 238);
      this.font = font;
      this.shared = shared;
      this.accountId = !shared && accountId != null ? accountId : "";
      this.panelX = 548;
      this.panelY = 30 + (shared ? 0 : Math.floorMod(this.accountId.hashCode(), 8) * 14);
   }

   public boolean open() {
      if (this.shared) {
         if (!MultiManager.get().isActive()) {
            RiptideNotifications.show("No active Multi batch", -42149);
            return false;
         }

         this.sharedGroups = MultiSharedGui.groups();
         this.adoptSharedGroup();
      } else {
         MultiSession.Snapshot snapshot = this.findSnapshot(this.accountId);
         if (snapshot == null) {
            RiptideNotifications.show("Multi session is unavailable", -42149);
            return false;
         }

         this.accountName = snapshot.accountName();
      }

      this.viewRevision = Long.MIN_VALUE;
      this.scrollY = 0;
      this.refreshView();
      this.sizeForCurrentView();
      this.setCollapsed(false);
      this.setVisible(true);
      return true;
   }

   public boolean isOpenFor(String targetAccountId) {
      return this.visible && !this.shared && targetAccountId != null && targetAccountId.equals(this.accountId);
   }

   void onServerMenuInvalidated(boolean closeHostedViewer) {
      this.view = null;
      this.viewFor = "";
      this.viewRevision = Long.MIN_VALUE;
      this.slotHits.clear();
      this.hoveredStack = ItemStack.EMPTY;
      this.hoveredHandler = -1;
      this.scrollY = 0;
      if (closeHostedViewer) {
         this.setVisible(false);
      }
   }

   private MultiSharedGui.Group currentSharedGroup() {
      return MultiSharedGui.pick(this.sharedGroups, this.sharedKey);
   }

   private List<String> sharedIds() {
      MultiSharedGui.Group group = this.currentSharedGroup();
      return group == null ? List.of() : group.accountIds();
   }

   private void adoptSharedGroup() {
      MultiSharedGui.Group group = this.currentSharedGroup();
      this.accountId = group == null ? "" : group.representativeId();
      this.accountName = group == null ? "Shared GUI" : "Shared GUI - " + group.size() + (group.size() == 1 ? " bot" : " bots");
   }

   private void cycleSharedGroup() {
      if (this.sharedGroups.size() >= 2) {
         MultiSharedGui.Group current = this.currentSharedGroup();
         int index = 0;

         for (int i = 0; i < this.sharedGroups.size(); i++) {
            if (current != null && this.sharedGroups.get(i).key().equals(current.key())) {
               index = i;
               break;
            }
         }

         this.sharedKey = this.sharedGroups.get((index + 1) % this.sharedGroups.size()).key();
         this.scrollY = 0;
      }
   }

   @Override
   public void setVisible(boolean value) {
      if (!value) {
         this.dragging = false;
         this.scrollbarDragging = false;
         this.hoveredStack = ItemStack.EMPTY;
         this.hoveredHandler = -1;
      }

      super.setVisible(value);
   }

   @Override
   public int getMinWidth() {
      return 128;
   }

   @Override
   public int getMinHeight() {
      return 92;
   }

   @Override
   public IRiptideOverlay.OverlayScope getDefaultOverlayScope() {
      return IRiptideOverlay.OverlayScope.BACKGROUND_STATUS;
   }

   @Override
   public boolean persistsAcrossScreenClose() {
      return true;
   }

   @Override
   public boolean wantsKeyboardCapture() {
      return this.visible && !this.collapsed;
   }

   @Override
   public void restoreLayout() {
      RiptideWindowLayout layout = RiptideSharedState.get().getWindowLayout(this.getOverlayId());
      if (layout != null) {
         this.setBounds(new RiptideWindowLayout(layout.x, layout.y, layout.width, layout.height, this.visible, this.collapsed));
      }
   }

   @Override
   public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      if (this.visible) {
         MultiManager manager = MultiManager.getIfInitialized();
         if (manager != null && manager.isActive() && (this.shared || this.findSnapshot(this.accountId) != null)) {
            if (this.mc != null && this.mc.gui.screen() != null) {
               if (this.shared) {
                  this.sharedGroups = MultiSharedGui.groups();
                  this.adoptSharedGroup();
               } else {
                  MultiSession.Snapshot snapshot = this.findSnapshot(this.accountId);
                  if (snapshot == null) {
                     this.setVisible(false);
                     return;
                  }

                  this.accountName = snapshot.accountName();
               }

               this.refreshView();
               RiptideWindowLayout bounds = this.clampToScreen(this);
               this.panelX = bounds.x;
               this.panelY = bounds.y;
               this.panelWidth = bounds.width;
               this.panelHeight = bounds.height;
               this.renderGuiWindowFrame(graphics, mouseX, mouseY);
               if (!this.collapsed) {
                  this.actions.clear();
                  this.slotHits.clear();
                  this.widgetHits.clear();
                  int hoverForInfo = this.hoveredHandler;
                  this.hoveredStack = ItemStack.EMPTY;
                  this.hoveredHandler = -1;
                  this.hoveredHotbar = -1;
                  boolean clipped = this.beginWindowBodyClip(graphics, this.getBounds(), false);
                  int innerX = this.panelX + 7;
                  int innerWidth = Math.max(1, this.panelWidth - 14);
                  int titleY = this.panelY + 16 + 3;
                  Component title = (Component)(this.view != null && this.view.title() != null
                     ? this.view.title()
                     : Component.literal(this.shared ? "Waiting for a bot GUI..." : "GUI"));
                  UiScissorStack.global().push(graphics, UiBounds.of(innerX, titleY, innerWidth, 13));
                  graphics.text(this.font, title.getVisualOrderText(), innerX, titleY, this.text(), false);
                  UiScissorStack.global().pop(graphics);
                  int toolbarY = titleY + 13;
                  if (this.view != null) {
                     UiScissorStack.global().push(graphics, UiBounds.of(innerX, toolbarY, innerWidth, 10));
                     int keyColor = RiptideTheme.recolor(-4743522, RiptideTheme.Channel.TEXT);
                     int firstWidth = this.drawMetricInline(
                        graphics,
                        innerX,
                        toolbarY,
                        "Rev: ",
                        Integer.toString(this.view.stateId()),
                        keyColor,
                        RiptideTheme.recolor(-46518, RiptideTheme.Channel.ACCENT)
                     );
                     firstWidth = this.drawMetricInline(
                        graphics,
                        firstWidth + 8,
                        toolbarY,
                        "SyncID: ",
                        Integer.toString(this.view.syncId()),
                        keyColor,
                        RiptideTheme.recolor(-791321, RiptideTheme.Channel.TEXT)
                     );
                     int visibleSlot = hoverForInfo >= 0 ? MultiManager.get().visibleSlotForHandler(this.accountId, hoverForInfo) : -1;
                     this.drawMetricInline(
                        graphics,
                        firstWidth + 8,
                        toolbarY,
                        "Slot: ",
                        visibleSlot >= 0 ? Integer.toString(visibleSlot) : "--",
                        keyColor,
                        RiptideTheme.recolor(-7350273, RiptideTheme.Channel.ACCENT)
                     );
                     UiScissorStack.global().pop(graphics);
                     toolbarY += 12;
                  }

                  if (this.shared) {
                     MultiSharedGui.Group group = this.currentSharedGroup();
                     String pickerLabel = group == null ? "No GUIs open yet" : "GUI: " + group.label() + (this.sharedGroups.size() > 1 ? "  >" : "");
                     this.action(
                        graphics,
                        innerX,
                        toolbarY,
                        innerWidth,
                        pickerLabel,
                        this.sharedGroups.size() > 1 ? this.success() : this.border(),
                        mouseX,
                        mouseY,
                        this::cycleSharedGroup
                     );
                     toolbarY += 20;
                  }

                  int gap = 3;
                  int firstWidth = Math.max(1, (innerWidth - gap) / 2);
                  this.closeSilentToolbarRect = new int[]{innerX, toolbarY, firstWidth, 17};
                  this.action(graphics, innerX, toolbarY, firstWidth, "Close W/O Pkt", this.border(), mouseX, mouseY, () -> this.toolbarClose(true));
                  this.action(
                     graphics,
                     innerX + firstWidth + gap,
                     toolbarY,
                     innerWidth - firstWidth - gap,
                     "Close",
                     this.border(),
                     mouseX,
                     mouseY,
                     () -> this.toolbarClose(false)
                  );
                  int areaTop = toolbarY + 17 + 6;
                  int availableHeight = Math.max(18, this.panelY + this.panelHeight - 7 - 3 - areaTop);
                  boolean scroll = this.contentHeight > availableHeight;
                  int scrollbarSpace = scroll ? 6 : 0;
                  this.gridWidth = Math.max(1, Math.min(this.contentWidth, innerWidth - scrollbarSpace));
                  this.gridHeight = Math.min(this.contentHeight, availableHeight);
                  this.gridX = innerX + Math.max(0, (innerWidth - this.gridWidth - scrollbarSpace) / 2);
                  this.gridY = areaTop;
                  this.scrollY = clamp(this.scrollY, 0, this.maxScroll());
                  this.renderGrid(graphics, mouseX, mouseY);
                  CompactScrollbar.Metrics scrollbar = this.scrollbarMetrics();
                  CompactScrollbar.draw(graphics, scrollbar, scrollbar.contains(mouseX, mouseY), this.scrollbarDragging);
                  this.endWindowBodyClip(graphics, clipped);
                  ItemStack carried = this.view == null ? ItemStack.EMPTY : this.view.carried();
                  if (carried != null && !carried.isEmpty()) {
                     graphics.nextStratum();
                     graphics.item(carried, mouseX - 8, mouseY - 8);
                     graphics.itemDecorations(this.font, carried, mouseX - 8, mouseY - 8);
                  } else if (!this.hoveredStack.isEmpty()) {
                     this.renderItemTooltip(graphics, this.hoveredStack, this.hoveredX, this.hoveredY, this.hoveredHotbar >= 0);
                  } else if (RiptideConfig.getGlobal().multiShowTooltips
                     && this.closeSilentToolbarRect != null
                     && MultiTooltip.hovered(
                        this.closeSilentToolbarRect[0],
                        this.closeSilentToolbarRect[1],
                        this.closeSilentToolbarRect[2],
                        this.closeSilentToolbarRect[3],
                        mouseX,
                        mouseY
                     )) {
                     MultiTooltip.render(graphics, this.font, "Hide GUI locally, keep it open", mouseX, mouseY);
                  }
               }
            }
         } else {
            this.setVisible(false);
         }
      }
   }

   private void renderGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      UiBounds area = UiBounds.of(this.gridX - 3, this.gridY - 3, this.gridWidth + 6, this.gridHeight + 6);
      UiRenderer.rect(graphics, area, -3750202);
      int selectedHandler = MultiManager.get().selectedHotbarHandler(this.accountId);
      UiScissorStack.global().push(graphics, UiBounds.of(this.gridX, this.gridY, this.gridWidth, this.gridHeight));
      if (this.view != null) {
         for (MultiSession.ViewSlot slot : this.view.slots()) {
            int x = this.gridX + slot.x();
            int y = this.gridY + slot.y() - this.scrollY;
            if (x + 18 > this.gridX && x < this.gridX + this.gridWidth && y + 18 > this.gridY && y < this.gridY + this.gridHeight) {
               this.drawSlotCell(graphics, x, y);
               if (selectedHandler >= 0 && slot.handler() == selectedHandler) {
                  UiRenderer.frame(graphics, UiBounds.of(x, y, 18, 18), 452935744, -1325449152);
               }

               ItemStack stack = slot.item();
               if (stack != null && !stack.isEmpty()) {
                  try {
                     graphics.item(stack, x + 1, y + 1);
                     graphics.itemDecorations(this.font, stack, x + 1, y + 1);
                  } catch (Throwable var12) {
                  }
               }

               this.slotHits.add(new RiptideMultiGuiOverlay.SlotHit(x, y, slot.handler(), stack == null ? ItemStack.EMPTY : stack));
               if (mouseX >= x
                  && mouseX < x + 18
                  && mouseY >= y
                  && mouseY < y + 18
                  && mouseX >= this.gridX
                  && mouseX < this.gridX + this.gridWidth
                  && mouseY >= this.gridY
                  && mouseY < this.gridY + this.gridHeight) {
                  UiRenderer.rect(graphics, UiBounds.of(x + 1, y + 1, 16, 16), -2130706433);
                  this.hoveredStack = stack == null ? ItemStack.EMPTY : stack;
                  this.hoveredHandler = slot.handler();
                  this.hoveredHotbar = MultiManager.get().hotbarIndexForHandler(this.accountId, slot.handler());
                  this.hoveredX = mouseX;
                  this.hoveredY = mouseY;
               }
            }
         }

         this.menuInput.sync(this.viewTypeId());
         MultiMenuRenderer.render(graphics, this.font, this.view, this.gridX, this.gridY, this.scrollY, mouseX, mouseY, this.widgetHits, this.menuInput);
      }

      UiScissorStack.global().pop(graphics);
   }

   private void renderGuiWindowFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      boolean active = this.dragging || RiptideOverlayManager.get().isFocusedOverlay(this) || RiptideOverlayManager.get().isTopOverlay(this);
      CompactWindow.renderFrame(
         UiContexts.overlay(graphics, this.font, mouseX, mouseY),
         UiBounds.of(this.panelX, this.panelY, this.panelWidth, this.collapsed ? 16 : this.panelHeight),
         this.shared ? this.accountName : "GUI - " + this.accountName,
         this.collapsed,
         false,
         true,
         mouseY >= this.panelY && mouseY < this.panelY + 16,
         active,
         4,
         4,
         16
      );
   }

   @Override
   protected boolean isOverCollapseButton(double mouseX, double mouseY, RiptideWindowLayout bounds) {
      return false;
   }

   private void drawSlotCell(GuiGraphicsExtractor graphics, int x, int y) {
      UiRenderer.rect(graphics, UiBounds.of(x, y, 18, 18), -7631989);
      UiRenderer.rect(graphics, UiBounds.of(x, y, 18, 1), -13158601);
      UiRenderer.rect(graphics, UiBounds.of(x, y, 1, 18), -13158601);
      UiRenderer.rect(graphics, UiBounds.of(x, y + 18 - 1, 18, 1), -1);
      UiRenderer.rect(graphics, UiBounds.of(x + 18 - 1, y, 1, 18), -1);
   }

   private void action(GuiGraphicsExtractor graphics, int x, int y, int width, String label, int outline, int mouseX, int mouseY, Runnable callback) {
      boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 17;
      Button.render(UiContexts.overlay(graphics, this.font, mouseX, mouseY), UiBounds.of(x, y, width, 17), label, Button.Tone.NORMAL, hover, false);
      this.actions.add(new RiptideMultiGuiOverlay.ActionHit(x, y, width, 17, callback));
   }

   private void refreshView() {
      long revision = MultiManager.get().menuRevision(this.accountId);
      if (revision == this.viewRevision && this.view != null && this.accountId.equals(this.viewFor)) {
         if (RiptideUiScale.getVirtualScreenHeight() != this.lastSizedScreenH) {
            this.sizeForCurrentView();
         }
      } else {
         this.viewRevision = revision;
         this.viewFor = this.accountId;
         this.view = MultiManager.get().menuView(this.accountId);
         int[] cs = MultiMenuRenderer.contentSize(this.view);
         this.contentWidth = Math.max(18, cs[0]);
         this.contentHeight = Math.max(18, cs[1]);
         this.menuInput.sync(this.viewTypeId());
         if (this.contentWidth != this.lastSizedContentW
            || this.contentHeight != this.lastSizedContentH
            || RiptideUiScale.getVirtualScreenHeight() != this.lastSizedScreenH) {
            this.sizeForCurrentView();
         }

         this.scrollY = clamp(this.scrollY, 0, this.maxScroll());
      }
   }

   private int chromeHeight() {
      int info = this.view != null ? 12 : 0;
      return 32 + info + (this.shared ? 20 : 0) + 17 + 6 + 3 + 7;
   }

   private void sizeForCurrentView() {
      this.lastSizedContentW = this.contentWidth;
      this.lastSizedContentH = this.contentHeight;
      this.lastSizedScreenH = RiptideUiScale.getVirtualScreenHeight();
      int screenH = Math.max(120, this.lastSizedScreenH);
      int maxGridH = Math.max(18, screenH - this.chromeHeight() - 20);
      int gridH = Math.min(this.contentHeight, maxGridH);
      boolean needsScroll = this.contentHeight > gridH;
      int wantedWidth = Math.max(this.getMinWidth(), this.contentWidth + 14 + (needsScroll ? 6 : 0));
      int wantedHeight = Math.max(this.getMinHeight(), this.chromeHeight() + gridH);
      this.setBounds(new RiptideWindowLayout(this.panelX, this.panelY, wantedWidth, wantedHeight, true, false));
   }

   private MultiSession.Snapshot findSnapshot(String id) {
      if (id != null && !id.isBlank()) {
         for (MultiSession.Snapshot snapshot : MultiManager.get().snapshots()) {
            if (id.equals(snapshot.accountId())) {
               return snapshot;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private int maxScroll() {
      return Math.max(0, this.contentHeight - Math.max(18, this.gridHeight));
   }

   private CompactScrollbar.Metrics scrollbarMetrics() {
      return CompactScrollbar.compute(
         this.contentHeight, Math.max(18, this.gridHeight), this.gridX + this.gridWidth + 2, this.gridY, 3, Math.max(18, this.gridHeight), this.scrollY
      );
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.visible) {
         return false;
      } else {
         RiptideWindowLayout bounds = this.getBounds();
         if (this.isOverCloseButton(mouseX, mouseY, bounds)) {
            this.setVisible(false);
            return true;
         } else if (button == 0 && this.scrollbarMetrics().overThumb(mouseX, mouseY)) {
            this.scrollbarDragging = true;
            this.scrollbarGrab = (int)Math.round(mouseY) - this.scrollbarMetrics().thumbY();
            return true;
         } else if (button == 0 && this.isOverDragBar(mouseX, mouseY)) {
            this.dragging = true;
            this.dragOffsetX = mouseX - this.panelX;
            this.dragOffsetY = mouseY - this.panelY;
            return true;
         } else if (this.collapsed) {
            return false;
         } else {
            if (button == 0) {
               for (RiptideMultiGuiOverlay.ActionHit action : this.actions) {
                  if (action.hit(mouseX, mouseY)) {
                     action.callback().run();
                     return true;
                  }
               }
            }

            boolean insideGrid = mouseX >= this.gridX && mouseX < this.gridX + this.gridWidth && mouseY >= this.gridY && mouseY < this.gridY + this.gridHeight;
            if (insideGrid && button == 0) {
               for (MultiMenuRenderer.MenuHit hit : this.widgetHits) {
                  if (!(mouseX < hit.x()) && !(mouseX >= hit.x() + hit.w()) && !(mouseY < hit.y()) && !(mouseY >= hit.y() + hit.h())) {
                     this.dispatchWidget(hit.action());
                     return true;
                  }
               }
            }

            if (insideGrid) {
               for (RiptideMultiGuiOverlay.SlotHit slot : this.slotHits) {
                  if (slot.hit(mouseX, mouseY)) {
                     if (button == 1 && this.ctrlDown() && this.shiftDown()) {
                        if (!slot.stack().isEmpty()) {
                           this.openNbt(slot.stack(), (int)mouseX, (int)mouseY);
                        }

                        return true;
                     }

                     if (slot.handler() < 0) {
                        return true;
                     }

                     if (this.view != null && this.view.interactive()) {
                        MultiClientCommands.ClickSpec spec = MultiClientCommands.fromMouse(button, this.shiftDown(), this.ctrlDown());
                        if (this.shared) {
                           this.handleFanout(MultiManager.get().clickBotSlots(this.sharedIds(), slot.handler(), spec));
                        } else {
                           this.handleClickResult(MultiManager.get().clickBotSlot(this.accountId, slot.handler(), spec));
                        }

                        return true;
                     }

                     RiptideNotifications.show(
                        this.view != null && this.view.synchronizationBlocked() ? "Inventory is waiting for a server update" : "Inventory is synchronizing",
                        this.danger()
                     );
                     return true;
                  }
               }
            }

            return insideGrid ? true : this.isMouseOver(mouseX, mouseY);
         }
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.scrollbarDragging) {
         this.scrollbarDragging = false;
         return true;
      } else if (this.dragging) {
         this.dragging = false;
         this.saveLayout();
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (this.scrollbarDragging) {
         this.scrollY = CompactScrollbar.scrollFromThumb(this.scrollbarMetrics(), mouseY, this.scrollbarGrab);
         return true;
      } else if (this.dragging) {
         RiptideWindowLayout next = this.clampToScreen(
            this,
            new RiptideWindowLayout(
               (int)Math.round(mouseX - this.dragOffsetX),
               (int)Math.round(mouseY - this.dragOffsetY),
               this.panelWidth,
               this.panelHeight,
               this.visible,
               this.collapsed
            )
         );
         this.panelX = next.x;
         this.panelY = next.y;
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (this.visible && !this.collapsed && this.isMouseOver(mouseX, mouseY)) {
         this.scrollY = clamp(this.scrollY - (int)Math.signum(amount) * 18, 0, this.maxScroll());
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.visible) {
         return false;
      } else if (this.menuInput.rename.focused() && this.menuInput.rename.keyPressed(keyCode)) {
         this.sendRename();
         return true;
      } else if (RiptideOverlayManager.get().isAnyTextFieldFocused()) {
         return false;
      } else if (!this.collapsed && keyCode == 81 && this.hoveredHandler >= 0 && this.view != null && this.view.interactive()) {
         boolean wholeStack = (modifiers & 2) != 0 || this.ctrlDown();
         MultiClientCommands.ClickSpec spec = MultiClientCommands.dropSpec(wholeStack);
         if (this.shared) {
            this.handleFanout(MultiManager.get().clickBotSlots(this.sharedIds(), this.hoveredHandler, spec));
         } else {
            this.handleClickResult(MultiManager.get().clickBotSlot(this.accountId, this.hoveredHandler, spec));
         }

         return true;
      } else if (!this.collapsed && this.hoveredHotbar >= 0 && this.view != null && this.view.interactive() && (keyCode == 85 || keyCode == 73)) {
         boolean use = keyCode == 73;
         if (this.shared) {
            this.handleFanout(
               use
                  ? MultiManager.get().useBotHotbars(this.sharedIds(), this.hoveredHotbar)
                  : MultiManager.get().selectBotHotbars(this.sharedIds(), this.hoveredHotbar)
            );
         } else {
            this.handleClickResult(
               use
                  ? MultiManager.get().useBotHotbar(this.accountId, this.hoveredHotbar)
                  : MultiManager.get().selectBotHotbar(this.accountId, this.hoveredHotbar)
            );
         }

         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      if (this.menuInput.rename.focused() && this.menuInput.rename.charTyped(chr)) {
         this.sendRename();
         return true;
      } else {
         return false;
      }
   }

   private void sendRename() {
      if (this.shared) {
         MultiManager.get().renameBotItems(this.sharedIds(), this.menuInput.rename.text(), this.viewTypeId());
      } else {
         MultiManager.get().renameBotItem(this.accountId, this.menuInput.rename.text());
      }
   }

   private String viewTypeId() {
      return this.view != null && this.view.extras() != null ? this.view.extras().typeId() : "";
   }

   private void dispatchWidget(MultiMenuRenderer.MenuAction action) {
      if (action instanceof MultiMenuRenderer.RenameFocusAct) {
         this.menuInput.rename.focus();
         this.menuInput.rename.set("");
      } else if (action instanceof MultiMenuRenderer.BeaconPick p) {
         if (p.secondary()) {
            this.menuInput.beaconSecondary = p.effectId();
         } else {
            this.menuInput.beaconPrimary = p.effectId();
         }
      } else if (this.view != null && this.view.interactive()) {
         MultiManager mgr = MultiManager.get();
         String type = this.viewTypeId();
         if (action instanceof MultiMenuRenderer.ButtonAct b) {
            if (this.shared) {
               this.handleFanout(mgr.buttonClickBots(this.sharedIds(), b.id(), type));
            } else {
               this.handleClickResult(mgr.buttonClickBot(this.accountId, b.id()));
            }
         } else if (action instanceof MultiMenuRenderer.TradeAct t) {
            if (this.shared) {
               this.handleFanout(mgr.selectTradeBots(this.sharedIds(), t.index(), type));
            } else {
               this.handleClickResult(mgr.selectTradeBot(this.accountId, t.index()));
            }
         } else if (action instanceof MultiMenuRenderer.BeaconAct be) {
            if (this.shared) {
               this.handleFanout(mgr.setBeaconBots(this.sharedIds(), be.primary(), be.secondary(), type));
            } else {
               this.handleClickResult(mgr.setBeaconBot(this.accountId, be.primary(), be.secondary()));
            }
         } else if (action instanceof MultiMenuRenderer.RecipeStep rs) {
            this.menuInput.recipeIndex = Math.max(0, this.menuInput.recipeIndex + rs.delta());
            if (this.shared) {
               this.handleFanout(mgr.buttonClickBots(this.sharedIds(), this.menuInput.recipeIndex, type));
            } else {
               this.handleClickResult(mgr.buttonClickBot(this.accountId, this.menuInput.recipeIndex));
            }
         }
      } else {
         RiptideNotifications.show("Inventory is synchronizing", this.danger());
      }
   }

   private void handleActionResult(MultiManager.BroadcastResult result) {
      if (result == null || result.failed() > 0) {
         RiptideNotifications.show("GUI action failed", this.danger());
      } else if (result.sent() == 0 && result.skipped() > 0) {
         RiptideNotifications.show("GUI action skipped", this.border());
      }
   }

   private void handleClickResult(String result) {
      if (!"Sent".equals(result)) {
         String message = result != null && !result.isBlank() ? MultiManager.singleLine(result, 80) : "GUI action failed";
         RiptideNotifications.show(message, this.danger());
      }
   }

   private void handleFanout(MultiManager.BroadcastResult result) {
      if (result != null) {
         int missed = result.failed() + result.skipped();
         if (result.sent() <= 0 || missed != 0) {
            RiptideNotifications.show(
               result.sent() == 0 ? "No bot took the click" : "Sent " + result.sent() + ", missed " + missed,
               result.sent() == 0 ? this.danger() : this.border()
            );
         }
      }
   }

   private void toolbarClose(boolean silent) {
      Set<String> scope = (Set<String>)(this.shared ? new LinkedHashSet<>(this.sharedIds()) : Set.of(this.accountId));
      if (!this.shared || !scope.isEmpty()) {
         this.handleActionResult(silent ? MultiManager.get().closeSilentOnScope(scope) : MultiManager.get().closeOnScope(scope));
      }
   }

   private void openNbt(ItemStack stack, int mouseX, int mouseY) {
      RiptideItemNbtInspectOverlay overlay = RiptideItemNbtInspectOverlay.getSharedOverlay(this.font);
      if (overlay != null) {
         overlay.open(stack, mouseX + 8, mouseY);
         RiptideOverlayManager.get().register(overlay, IRiptideOverlay.OverlayScope.BACKGROUND_STATUS);
         RiptideOverlayManager.get().bringToFront(overlay);
      }
   }

   private void renderItemTooltip(GuiGraphicsExtractor graphics, ItemStack stack, int mouseX, int mouseY, boolean hotbar) {
      try {
         List<Component> base = Screen.getTooltipFromItem(this.mc, stack);
         if (base == null || base.isEmpty()) {
            return;
         }

         List<Component> lines = base;
         if (hotbar) {
            lines = new ArrayList<>(base);
            lines.add(Component.literal("[U] switch to slot"));
            lines.add(Component.literal("[I] switch + use item"));
         }

         int width = 0;

         for (Component line : lines) {
            width = Math.max(width, this.font.width(line));
         }

         int height = lines.size() == 1 ? 8 : lines.size() * 10 - 2;
         int screenWidth = RiptideUiScale.getVirtualScreenWidth();
         int screenHeight = RiptideUiScale.getVirtualScreenHeight();
         int x = mouseX + 12;
         int y = mouseY - 12;
         if (x + width + 4 > screenWidth) {
            x = Math.max(4, mouseX - width - 16);
         }

         if (y + height + 4 > screenHeight) {
            y = screenHeight - height - 4;
         }

         if (y < 4) {
            y = 4;
         }

         graphics.nextStratum();
         UiRenderer.rect(graphics, UiBounds.of(x - 3, y - 3, width + 6, height + 6), -267386864);
         UiRenderer.frame(graphics, UiBounds.of(x - 3, y - 3, width + 6, height + 6), 0, 1347420320);
         int lineY = y;

         for (Component line : lines) {
            graphics.text(this.font, line.getVisualOrderText(), x, lineY, -1, true);
            lineY += 10;
         }
      } catch (Throwable var17) {
      }
   }

   private String trim(String value, int width) {
      String safe = value == null ? "" : value;
      return this.font.width(safe) <= width ? safe : this.font.plainSubstrByWidth(safe, Math.max(1, width - this.font.width("..."))) + "...";
   }

   private boolean ctrlDown() {
      if (this.mc != null && this.mc.getWindow() != null) {
         long window = this.mc.getWindow().handle();
         return GLFW.glfwGetKey(window, 341) == 1 || GLFW.glfwGetKey(window, 345) == 1;
      } else {
         return false;
      }
   }

   private boolean shiftDown() {
      if (this.mc != null && this.mc.getWindow() != null) {
         long window = this.mc.getWindow().handle();
         return GLFW.glfwGetKey(window, 340) == 1 || GLFW.glfwGetKey(window, 344) == 1;
      } else {
         return false;
      }
   }

   private int text() {
      return RiptideTheme.recolor(-855310, RiptideTheme.Channel.TEXT);
   }

   private int border() {
      return RiptideTheme.recolor(-12174784, RiptideTheme.Channel.OUTLINE);
   }

   private int drawMetricInline(GuiGraphicsExtractor graphics, int x, int y, String key, String value, int keyColor, int valueColor) {
      graphics.text(this.font, Component.literal(key).getVisualOrderText(), x, y, keyColor, false);
      int vx = x + this.font.width(key);
      graphics.text(this.font, Component.literal(value).getVisualOrderText(), vx, y, valueColor, false);
      return vx + this.font.width(value);
   }

   private int success() {
      return RiptideTheme.recolor(-13248397, RiptideTheme.Channel.SUCCESS);
   }

   private int danger() {
      return RiptideTheme.recolor(-42149, RiptideTheme.Channel.DANGER);
   }

   private static int tint(int color, int alpha) {
      return alpha << 24 | color & 16777215;
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private record ActionHit(int x, int y, int width, int height, Runnable callback) {
      boolean hit(double mouseX, double mouseY) {
         return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
      }
   }

   private record SlotHit(int x, int y, int handler, ItemStack stack) {
      boolean hit(double mouseX, double mouseY) {
         return mouseX >= this.x && mouseX < this.x + 18 && mouseY >= this.y && mouseY < this.y + 18;
      }
   }
}
