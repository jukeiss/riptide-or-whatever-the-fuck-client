package riptide.util;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.Packet;
import riptide.gui.multi.MultiPanel;
import riptide.gui.screen.RiptideAccountsScreen;
import riptide.gui.screen.RiptideFormValuesScreen;
import riptide.gui.screen.RiptideMultiAutoAcceptScreen;
import riptide.gui.screen.RiptideMultiProxyPickerScreen;
import riptide.gui.screen.RiptideOverlayHostScreen;
import riptide.util.multi.MultiAutoAccept;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiManualPackets;
import riptide.util.multi.MultiPacketPolicy;
import riptide.util.multi.MultiProfile;
import riptide.util.multi.MultiTakeoverState;

public final class RiptideMultiOverlay extends RiptideOverlayBase implements MultiPanel.Host {
   private static final String OVERLAY_ID = "riptide-multi";
   private final Minecraft mc = Minecraft.getInstance();
   private final Font font;
   private final MultiPanel panel;
   private final RiptidePacketSelectorOverlay packetSelector;
   private final Map<String, RiptideMultiGuiOverlay> guiViewers = new LinkedHashMap<>();
   private RiptideMultiGuiOverlay sharedGuiViewer;
   private boolean dragging;
   private boolean suppressForAccounts;
   private Screen accountsReturnScreen;
   private double dragOffsetX;
   private double dragOffsetY;
   private static volatile RiptideMultiOverlay instance;

   public static void openGuiViewer(String accountId) {
      RiptideMultiOverlay live = instance;
      if (live != null && accountId != null) {
         live.openGui(accountId);
      }
   }

   public static void showGuiViewer(String accountId) {
      RiptideMultiOverlay live = instance;
      if (live != null && accountId != null && !live.isGuiOpen(accountId)) {
         live.openGui(accountId);
      }
   }

   public static boolean isGuiViewerOpen(String accountId) {
      RiptideMultiOverlay live = instance;
      return live != null && accountId != null && live.isGuiOpen(accountId);
   }

   public static void openGuiViewerHosted(String accountId) {
      RiptideMultiOverlay live = instance;
      if (live != null && accountId != null && !accountId.isBlank()) {
         live.ensureGuiOpen(accountId);
         RiptideMultiGuiOverlay viewer = live.guiViewers.get(accountId);
         Minecraft client = Minecraft.getInstance();
         if (viewer != null && client != null && !(client.gui.screen() instanceof RiptideOverlayHostScreen)) {
            client.gui.setScreen(new RiptideOverlayHostScreen(viewer, null, false, true).withDarkenedBackground());
         }
      }
   }

   public static void hideGuiViewer(String accountId) {
      RiptideMultiOverlay live = instance;
      if (live != null && accountId != null) {
         RiptideMultiGuiOverlay viewer = live.guiViewers.get(accountId);
         if (viewer != null) {
            viewer.setVisible(false);
         }
      }
   }

   private void ensureGuiOpen(String accountId) {
      RiptideMultiGuiOverlay viewer = this.guiViewers.get(accountId);
      if (viewer == null) {
         viewer = new RiptideMultiGuiOverlay(this.font, accountId);
         viewer.restoreLayout();
         this.guiViewers.put(accountId, viewer);
      }

      RiptideOverlayManager manager = RiptideOverlayManager.get();
      manager.register(viewer, IRiptideOverlay.OverlayScope.BACKGROUND_STATUS);
      if (!viewer.isOpenFor(accountId)) {
         viewer.open();
      }

      manager.bringToFront(viewer);
   }

   public RiptideMultiOverlay(Font font) {
      super("riptide-multi", 470, 300);
      this.font = font;
      this.panelX = 70;
      this.panelY = 30;
      this.panel = new MultiPanel(this, font);
      instance = this;
      this.packetSelector = new RiptidePacketSelectorOverlay(font);
      MultiManager.setUiLifecycleListener(new MultiManager.UiLifecycleListener() {
         {
            Objects.requireNonNull(RiptideMultiOverlay.this);
         }

         @Override
         public void batchEnded() {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
               client.execute(RiptideMultiOverlay.this::closeAllGuiViewers);
            }
         }

         @Override
         public void sessionDropped(String accountId) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
               client.execute(() -> RiptideMultiOverlay.this.closeGuiViewer(accountId));
            }
         }

         @Override
         public void menuClosed(String accountId) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
               client.execute(() -> RiptideMultiOverlay.this.handleMenuInvalidated(accountId));
            }
         }
      });
   }

   private void handleMenuInvalidated(String accountId) {
      RiptideMultiGuiOverlay viewer = this.guiViewers.get(accountId);
      Minecraft client = Minecraft.getInstance();
      boolean hosted = viewer != null && client != null && client.gui.screen() instanceof RiptideOverlayHostScreen host && host.hostsOverlay(viewer);
      if (viewer != null) {
         viewer.onServerMenuInvalidated(hosted);
      }

      if (this.sharedGuiViewer != null) {
         this.sharedGuiViewer.onServerMenuInvalidated(false);
      }
   }

   private void closeGuiViewer(String accountId) {
      RiptideMultiGuiOverlay viewer = this.guiViewers.remove(accountId);
      if (viewer != null) {
         viewer.setVisible(false);
         RiptideOverlayManager.get().unregister(viewer);
      }
   }

   private void closeAllGuiViewers() {
      for (RiptideMultiGuiOverlay viewer : this.guiViewers.values()) {
         viewer.setVisible(false);
         RiptideOverlayManager.get().unregister(viewer);
      }

      this.guiViewers.clear();
      if (this.sharedGuiViewer != null) {
         this.sharedGuiViewer.setVisible(false);
         RiptideOverlayManager.get().unregister(this.sharedGuiViewer);
         this.sharedGuiViewer = null;
      }
   }

   public void toggle() {
      this.setVisible(!this.visible);
      if (this.visible) {
         this.panel.opened();
         RiptideOverlayManager.get().bringToFront(this);
      }
   }

   public void openInGameInteractive() {
      this.setVisible(true);
      this.panel.opened();
   }

   @Override
   public void setVisible(boolean value) {
      if (!value) {
         this.panel.flushPendingEdit();
         this.packetSelector.close();
         this.suppressForAccounts = false;
         this.accountsReturnScreen = null;
      }

      super.setVisible(value);
   }

   @Override
   public int getMinWidth() {
      return 370;
   }

   @Override
   public int getMinHeight() {
      return 220;
   }

   @Override
   public IRiptideOverlay.OverlayScope getDefaultOverlayScope() {
      return IRiptideOverlay.OverlayScope.BACKGROUND_STATUS;
   }

   @Override
   public boolean usesSharedHeaderClickCollapse() {
      return true;
   }

   @Override
   public boolean hasTextFieldFocused() {
      return this.panel.hasFocusedTextInput() || this.packetSelector.hasTextFieldFocused();
   }

   @Override
   public void clearTextFieldFocus() {
      this.panel.clearFocus();
      this.packetSelector.clearTextFieldFocus();
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      if (this.visible && !this.suppressedForAccounts()) {
         RiptideWindowLayout bounds = this.clampToScreen(this);
         this.panelX = bounds.x;
         this.panelY = bounds.y;
         this.panelWidth = bounds.width;
         this.panelHeight = bounds.height;
         this.renderWindowFrame(
            context,
            mouseX,
            mouseY,
            this.getBounds(),
            MultiManager.get().isActive() ? "Multi " + MultiManager.get().readyFraction() : "Multi",
            this.collapsed,
            this.dragging
         );
         if (!this.collapsed) {
            boolean clipped = this.beginWindowBodyClip(context, this.getBounds(), false);
            this.panel.render(context, this.panelX + 2, this.panelY + 16 + 1, this.panelWidth - 4, this.panelHeight - 16 - 3, mouseX, mouseY, delta);
            this.endWindowBodyClip(context, clipped);
         }

         if (this.packetSelector.isVisible()) {
            this.packetSelector.render(context, mouseX, mouseY, delta);
         }
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.visible || this.suppressedForAccounts()) {
         return false;
      } else if (this.packetSelector.isVisible()) {
         this.packetSelector.mouseClicked(mouseX, mouseY, button);
         return true;
      } else {
         RiptideWindowLayout bounds = this.getBounds();
         if (this.isOverCloseButton(mouseX, mouseY, bounds)) {
            this.setVisible(false);
            this.dragging = false;
            return true;
         } else if (button == 0 && this.isOverDragBar(mouseX, mouseY)) {
            this.dragging = true;
            this.dragOffsetX = mouseX - this.panelX;
            this.dragOffsetY = mouseY - this.panelY;
            return true;
         } else if (this.collapsed) {
            return false;
         } else {
            return this.panel.mouseClicked((int)Math.round(mouseX), (int)Math.round(mouseY), button) ? true : this.isMouseOver(mouseX, mouseY);
         }
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (!this.visible || this.suppressedForAccounts()) {
         return false;
      } else if (this.packetSelector.isVisible()) {
         this.packetSelector.mouseReleased(mouseX, mouseY, button);
         return true;
      } else if (this.dragging) {
         this.dragging = false;
         this.saveLayout();
         return true;
      } else {
         return this.panel.mouseReleased((int)Math.round(mouseX), (int)Math.round(mouseY), button);
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (!this.visible || this.suppressedForAccounts()) {
         return false;
      } else if (this.packetSelector.isVisible()) {
         this.packetSelector.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
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
         return !this.collapsed && this.panel.mouseDragged((int)Math.round(mouseX), (int)Math.round(mouseY), button, deltaX, deltaY);
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (!this.visible || this.suppressedForAccounts()) {
         return false;
      } else if (this.packetSelector.isVisible()) {
         this.packetSelector.mouseScrolled(mouseX, mouseY, amount);
         return true;
      } else {
         return !this.collapsed && this.isMouseOver(mouseX, mouseY) && this.panel.mouseScrolled((int)Math.round(mouseX), (int)Math.round(mouseY), amount);
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.visible || this.suppressedForAccounts()) {
         return false;
      } else if (this.packetSelector.isVisible()) {
         this.packetSelector.keyPressed(keyCode, scanCode, modifiers);
         return true;
      } else {
         return !this.collapsed && this.panel.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      if (!this.visible || this.suppressedForAccounts()) {
         return false;
      } else if (this.packetSelector.isVisible()) {
         this.packetSelector.charTyped(chr, modifiers);
         return true;
      } else {
         return !this.collapsed && this.panel.charTyped(chr, modifiers);
      }
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      return this.visible && !this.suppressedForAccounts()
         ? this.packetSelector.isVisible() && this.packetSelector.isMouseOver(mouseX, mouseY) || super.isMouseOver(mouseX, mouseY)
         : false;
   }

   @Override
   public void manageAccounts() {
      if (this.mc != null) {
         Screen parent = this.mc.gui.screen();
         this.suppressForAccounts = true;
         this.accountsReturnScreen = parent;
         this.panel.clearFocus();
         this.mc.gui.setScreen(new RiptideAccountsScreen(parent));
      }
   }

   @Override
   public void pickManualProxy(String title, String serverAddress, String selectedProxyId, Map<String, Integer> profileUsage, Consumer<String> callback) {
      if (this.mc != null) {
         Screen parent = this.mc.gui.screen();
         this.suppressForAccounts = true;
         this.accountsReturnScreen = parent;
         this.panel.clearFocus();
         this.mc.gui.setScreen(new RiptideMultiProxyPickerScreen(parent, title, serverAddress, selectedProxyId, profileUsage, callback));
      }
   }

   @Override
   public void openGui(String accountId) {
      if (accountId != null && !accountId.isBlank()) {
         RiptideMultiGuiOverlay viewer = this.guiViewers.get(accountId);
         if (viewer != null && viewer.isOpenFor(accountId)) {
            viewer.setVisible(false);
         } else {
            if (viewer == null) {
               viewer = new RiptideMultiGuiOverlay(this.font, accountId);
               viewer.restoreLayout();
               this.guiViewers.put(accountId, viewer);
            }

            RiptideOverlayManager manager = RiptideOverlayManager.get();
            manager.register(viewer, IRiptideOverlay.OverlayScope.BACKGROUND_STATUS);
            if (viewer.open()) {
               manager.bringToFront(viewer);
            }
         }
      }
   }

   @Override
   public boolean isGuiOpen(String accountId) {
      RiptideMultiGuiOverlay viewer = this.guiViewers.get(accountId);
      return viewer != null && viewer.isOpenFor(accountId);
   }

   @Override
   public void toggleTakeover(String accountId) {
      MultiTakeoverState.toggle(accountId, this.mc == null ? null : this.mc.gui.screen());
   }

   @Override
   public boolean isTakeoverActive(String accountId) {
      return MultiTakeoverState.isActive(accountId);
   }

   @Override
   public boolean canTakeover(String accountId) {
      return MultiTakeoverState.available(accountId);
   }

   @Override
   public void openSharedGui() {
      if (this.sharedGuiViewer != null && this.sharedGuiViewer.isVisible()) {
         this.sharedGuiViewer.setVisible(false);
      } else {
         if (this.sharedGuiViewer == null) {
            this.sharedGuiViewer = RiptideMultiGuiOverlay.shared(this.font);
            this.sharedGuiViewer.restoreLayout();
         }

         RiptideOverlayManager manager = RiptideOverlayManager.get();
         manager.register(this.sharedGuiViewer, IRiptideOverlay.OverlayScope.BACKGROUND_STATUS);
         if (this.sharedGuiViewer.open()) {
            manager.bringToFront(this.sharedGuiViewer);
         }
      }
   }

   @Override
   public boolean isSharedGuiOpen() {
      return this.sharedGuiViewer != null && this.sharedGuiViewer.isVisible();
   }

   private boolean suppressedForAccounts() {
      if (!this.suppressForAccounts) {
         return false;
      } else {
         Screen current = this.mc != null && this.mc.gui != null ? this.mc.gui.screen() : null;
         if (current != this.accountsReturnScreen) {
            return true;
         } else {
            this.suppressForAccounts = false;
            this.accountsReturnScreen = null;
            return false;
         }
      }
   }

   @Override
   public void pickQuickPacket(Consumer<Class<? extends Packet<?>>> callback) {
      this.packetSelector.openC2S(callback, MultiManualPackets.unsafeC2S(), true);
   }

   @Override
   public void editBlocklist(
      MultiPacketPolicy.Direction direction, Collection<Class<? extends Packet<?>>> selected, BiConsumer<Class<? extends Packet<?>>, Boolean> callback
   ) {
      if (direction == MultiPacketPolicy.Direction.S2C) {
         this.packetSelector.openToggleS2C(callback, selected);
      } else {
         this.packetSelector.openToggleC2S(callback, selected, MultiManualPackets.unsafeC2S());
      }
   }

   @Override
   public void editMacro(RiptideMacro macro, Consumer<RiptideMacro> onSaved) {
      RiptideMacroEditorOverlay editor = RiptideMacroEditorOverlay.getSharedOverlay();
      if (editor != null) {
         RiptideOverlayManager.get().register(editor);
         boolean inWorld = this.mc != null && this.mc.player != null && this.mc.level != null;
         editor.setConfigurationOnly(!inWorld);
         editor.openForMulti(macro, onSaved);
         RiptideOverlayManager.get().bringToFront(editor);
      }
   }

   @Override
   public void editFormValues(MultiProfile profile, Set<String> selectedAccounts, Consumer<MultiProfile> onSaved) {
      if (this.mc != null) {
         Screen parent = this.mc.gui.screen();
         this.suppressForAccounts = true;
         this.accountsReturnScreen = parent;
         this.panel.clearFocus();
         this.mc.gui.setScreen(new RiptideFormValuesScreen(parent, profile, selectedAccounts, updated -> onSaved.accept(updated)));
      }
   }

   @Override
   public void editAutoAccept(MultiAutoAccept config, Consumer<MultiAutoAccept> onSaved, boolean live) {
      if (this.mc != null) {
         Screen parent = this.mc.gui.screen();
         this.suppressForAccounts = true;
         this.accountsReturnScreen = parent;
         this.panel.clearFocus();
         this.mc.gui.setScreen(new RiptideMultiAutoAcceptScreen(parent, config, onSaved, live));
      }
   }
}
