package riptide.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.Screen;
import riptide.gui.screen.RiptideModuleScreen;
import riptide.gui.screen.RiptideOverlayHostScreen;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiInputResult;
import riptide.gui.vanillaui.UiInputRouter;
import riptide.gui.vanillaui.UiLayer;
import riptide.gui.vanillaui.UiLayerManager;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.UiTextRenderer;
import riptide.gui.vanillaui.UiTheme;
import riptide.gui.vanillaui.components.OperationalOverlayComponent;
import riptide.modules.PackHideState;

public class RiptideOverlayManager {
   private static final RiptideOverlayManager INSTANCE = new RiptideOverlayManager();
   public static final int HOVER_BLOCKED_MOUSE = -10000;
   private static final double HEADER_CLICK_DRAG_THRESHOLD = 3.0;
   private final List<IRiptideOverlay> overlays = new CopyOnWriteArrayList<>();
   private final List<IRiptideOverlay> renderOverlays = new ArrayList<>();
   private final Map<IRiptideOverlay, OperationalOverlayComponent> overlayComponents = new IdentityHashMap<>();
   private final Map<IRiptideOverlay, IRiptideOverlay.OverlayScope> overlayScopes = new IdentityHashMap<>();
   private final Set<String> temporarilyHiddenOverlayIds = new HashSet<>();
   private final UiTheme uiTheme = new UiTheme();
   private final UiLayerManager overlayLayers = new UiLayerManager();
   private final UiInputRouter overlayInput = new UiInputRouter(this.overlayLayers);
   private UiTextRenderer uiText;
   private boolean overlayLayersDirty = true;
   private IRiptideOverlay focusedOverlay = null;
   private IRiptideOverlay textFieldFocusOverlay;
   private boolean textFieldFocusDirty = true;
   private double cachedHoverBlockMouseX = Double.NaN;
   private double cachedHoverBlockMouseY = Double.NaN;
   private long cachedHoverBlockNanos;
   private boolean cachedHoverBlockResult;
   private final Map<String, RiptideWindowLayout> clampedAwayBounds = new HashMap<>();
   private static final int MAX_CLAMPED_AWAY = 64;
   private IRiptideOverlay draggingOverlay = null;
   private IRiptideOverlay resizingOverlay = null;
   private IRiptideOverlay headerCollapseOverlay = null;
   private int lastScreenWidth = -1;
   private int lastScreenHeight = -1;
   private volatile boolean anyInteractiveOverlays = false;
   private boolean headerCollapseMoved = false;
   private boolean inventoryMouseDown = false;
   private RiptideWindowLayout headerCollapseStartBounds = null;
   private RiptideWindowLayout resizeStartBounds = null;
   private RiptideWindowLayout dragStartBounds = null;
   private double headerCollapseStartMouseX = 0.0;
   private double headerCollapseStartMouseY = 0.0;
   private double resizeStartMouseX = 0.0;
   private double resizeStartMouseY = 0.0;
   private long lastRenderErrorLogMs;

   private RiptideOverlayManager() {
   }

   public static RiptideOverlayManager get() {
      return INSTANCE;
   }

   public List<IRiptideOverlay> getOverlays() {
      return this.overlays;
   }

   public boolean hasRegisteredOverlays() {
      return !this.overlays.isEmpty();
   }

   public boolean hasVisibleOverlay() {
      return PackHideState.isActive() ? false : this.anyInteractiveOverlays;
   }

   public String censusSummary() {
      StringBuilder visible = new StringBuilder();
      int visibleCount = 0;

      for (IRiptideOverlay overlay : this.overlays) {
         if (this.isOverlayInteractive(overlay)) {
            if (visibleCount++ > 0) {
               visible.append(", ");
            }

            visible.append(overlay.getOverlayId());
         }
      }

      return "registered=" + this.overlays.size() + " visible=" + visibleCount + " [" + visible + "]";
   }

   public void register(IRiptideOverlay overlay) {
      this.register(overlay, this.inferScopeForCurrentScreen(overlay));
   }

   public void register(IRiptideOverlay overlay, IRiptideOverlay.OverlayScope scope) {
      if (overlay != null) {
         if (!this.overlays.contains(overlay)) {
            this.overlays.add(overlay);
         }

         this.overlayScopes.put(overlay, scope == null ? overlay.getDefaultOverlayScope() : scope);
         this.overlayComponents.computeIfAbsent(overlay, OperationalOverlayComponent::new);
         this.overlayLayersDirty = true;
         this.textFieldFocusDirty = true;
         this.publishInteractiveOverlays(true);
         this.invalidateHoverBlockCache();
         this.restoreSavedOverlayOrder();
         this.normalizeOverlayStack();
         if (overlay instanceof RiptideLauncherOverlay || "riptide-launcher".equals(overlay.getOverlayId())) {
            this.reclampAllOverlays();
         }
      }
   }

   public void unregister(IRiptideOverlay overlay) {
      if (overlay != null) {
         this.overlays.remove(overlay);
         this.overlayComponents.remove(overlay);
         this.overlayScopes.remove(overlay);
         this.overlayLayersDirty = true;
         this.temporarilyHiddenOverlayIds.remove(overlay.getOverlayId());
         if (this.focusedOverlay == overlay) {
            this.focusedOverlay = null;
         }

         if (this.textFieldFocusOverlay == overlay) {
            this.textFieldFocusOverlay = null;
         }

         this.textFieldFocusDirty = true;
         if (this.overlays.isEmpty()) {
            this.publishInteractiveOverlays(false);
         }

         this.invalidateHoverBlockCache();
         this.saveOverlayOrder();
      }
   }

   public void clear() {
      this.overlays.removeIf(overlay -> {
         if (overlay.persistsAcrossScreenClose()) {
            return false;
         } else {
            this.overlayComponents.remove(overlay);
            this.overlayScopes.remove(overlay);
            return true;
         }
      });
      this.overlayLayers.clear();
      this.overlayLayersDirty = true;
      this.temporarilyHiddenOverlayIds.clear();
      this.draggingOverlay = null;
      this.dragStartBounds = null;
      this.resizingOverlay = null;
      this.headerCollapseOverlay = null;
      this.focusedOverlay = null;
      this.textFieldFocusOverlay = null;
      this.textFieldFocusDirty = false;
      this.headerCollapseMoved = false;
      this.headerCollapseStartBounds = null;
      this.resizeStartBounds = null;
      this.inventoryMouseDown = false;
      this.publishInteractiveOverlays(!this.overlays.isEmpty());
      this.invalidateHoverBlockCache();
   }

   public void setTemporarilyHidden(IRiptideOverlay overlay, boolean hidden) {
      if (overlay != null) {
         String id = overlay.getOverlayId();
         if (id != null && !id.isEmpty()) {
            if (hidden) {
               this.temporarilyHiddenOverlayIds.add(id);
               overlay.clearTextFieldFocus();
               if (this.focusedOverlay == overlay) {
                  this.focusedOverlay = null;
               }

               if (this.draggingOverlay == overlay) {
                  this.draggingOverlay = null;
                  this.dragStartBounds = null;
               }

               if (this.resizingOverlay == overlay) {
                  this.resizingOverlay = null;
               }

               if (this.headerCollapseOverlay == overlay) {
                  this.headerCollapseOverlay = null;
               }
            } else {
               this.temporarilyHiddenOverlayIds.remove(id);
            }

            this.textFieldFocusDirty = true;
            this.invalidateHoverBlockCache();
         }
      }
   }

   public void clearTemporaryHidden() {
      this.temporarilyHiddenOverlayIds.clear();
      this.textFieldFocusDirty = true;
      this.invalidateHoverBlockCache();
   }

   public boolean isTemporarilyHidden(IRiptideOverlay overlay) {
      if (overlay == null) {
         return false;
      } else {
         String id = overlay.getOverlayId();
         return id != null && this.temporarilyHiddenOverlayIds.contains(id);
      }
   }

   private boolean isOverlayInteractive(IRiptideOverlay overlay) {
      Minecraft mc = Minecraft.getInstance();
      return this.isOverlayInteractive(overlay, mc == null ? null : mc.gui.screen());
   }

   private boolean isOverlayInteractive(IRiptideOverlay overlay, Screen screen) {
      return overlay != null && overlay.isVisible() && !this.isTemporarilyHidden(overlay) && this.isScopeValid(overlay, screen);
   }

   private IRiptideOverlay.OverlayScope inferScopeForCurrentScreen(IRiptideOverlay overlay) {
      Minecraft mc = Minecraft.getInstance();
      Screen screen = mc == null ? null : mc.gui.screen();
      if (overlay != null && overlay.getDefaultOverlayScope() == IRiptideOverlay.OverlayScope.BACKGROUND_STATUS) {
         return IRiptideOverlay.OverlayScope.BACKGROUND_STATUS;
      } else if (!RiptideLiteVariant.enabled() && screen instanceof RiptideModuleScreen) {
         return IRiptideOverlay.OverlayScope.MODULE_MENU;
      } else if (!(screen instanceof RiptideOverlayHostScreen) && screen != null && !(screen instanceof ChatScreen)) {
         return IRiptideOverlay.OverlayScope.CONTAINER_GUI;
      } else {
         return overlay == null ? IRiptideOverlay.OverlayScope.HOST_SCREEN : overlay.getDefaultOverlayScope();
      }
   }

   private boolean isScopeValid(IRiptideOverlay overlay, Screen screen) {
      IRiptideOverlay.OverlayScope scope = this.overlayScopes
         .getOrDefault(overlay, overlay == null ? IRiptideOverlay.OverlayScope.HOST_SCREEN : overlay.getDefaultOverlayScope());
      if (scope == IRiptideOverlay.OverlayScope.BACKGROUND_STATUS) {
         return true;
      } else if (screen == null || screen instanceof ChatScreen || screen instanceof InBedChatScreen) {
         return false;
      } else if (!RiptideLiteVariant.enabled() && screen instanceof RiptideModuleScreen) {
         return !this.isLauncherOverlay(overlay);
      } else if (screen instanceof RiptideOverlayHostScreen) {
         return true;
      } else {
         return switch (scope) {
            case BACKGROUND_STATUS -> true;
            case HOST_SCREEN, MODULE_MENU, CONTAINER_GUI -> true;
         };
      }
   }

   public void hideInvalidOverlaysForCurrentScreen() {
      Minecraft mc = Minecraft.getInstance();
      Screen screen = mc == null ? null : mc.gui.screen();
      boolean changed = false;

      for (IRiptideOverlay overlay : this.overlays) {
         if (overlay != null && overlay.isVisible() && !this.isScopeValid(overlay, screen)) {
            overlay.clearTextFieldFocus();
            if (this.focusedOverlay == overlay) {
               this.focusedOverlay = null;
            }

            if (this.draggingOverlay == overlay) {
               this.draggingOverlay = null;
               this.dragStartBounds = null;
            }

            if (this.resizingOverlay == overlay) {
               this.resizingOverlay = null;
            }

            if (this.headerCollapseOverlay == overlay) {
               this.headerCollapseOverlay = null;
            }

            changed = true;
         }
      }

      if (changed) {
         this.textFieldFocusDirty = true;
         this.invalidateHoverBlockCache();
      }
   }

   public void hideAllInteractiveOverlays() {
      for (IRiptideOverlay overlay : this.overlays) {
         if (overlay != null && this.overlayScopes.getOrDefault(overlay, overlay.getDefaultOverlayScope()) != IRiptideOverlay.OverlayScope.BACKGROUND_STATUS) {
            if (overlay.isVisible()) {
               overlay.setVisible(false);
            }

            overlay.clearTextFieldFocus();
         }
      }

      this.temporarilyHiddenOverlayIds.clear();
      this.draggingOverlay = null;
      this.dragStartBounds = null;
      this.resizingOverlay = null;
      this.headerCollapseOverlay = null;
      this.focusedOverlay = null;
      this.textFieldFocusOverlay = null;
      this.textFieldFocusDirty = false;
      this.inventoryMouseDown = false;
      this.invalidateHoverBlockCache();
   }

   public void bringToFront(IRiptideOverlay overlay) {
      if (overlay != null) {
         this.overlays.remove(overlay);
         this.overlays.add(overlay);
         this.overlayLayersDirty = true;
         this.publishInteractiveOverlays(true);
         this.focusedOverlay = overlay;
         this.textFieldFocusDirty = true;
         RiptideSharedState.get().setFocusedOverlayId(overlay.getOverlayId());
         this.invalidateHoverBlockCache();
         this.normalizeOverlayStack();
         this.saveOverlayOrder();
      }
   }

   public void bringToFrontParent(Object childComponent) {
      if (childComponent != null) {
         for (IRiptideOverlay overlay : this.overlays) {
            if (overlay instanceof RiptideCustomFilterOverlay filterOverlay && filterOverlay.getPacketSelectorOverlay() == childComponent) {
               this.bringToFront(overlay);
               return;
            }
         }
      }
   }

   private void restoreSavedOverlayOrder() {
      if (this.overlays.size() < 2) {
         this.restoreFocusedOverlay();
         this.normalizeOverlayStack();
      } else {
         List<String> savedOrder = RiptideSharedState.get().getOverlayOrder();
         if (savedOrder.isEmpty()) {
            this.restoreFocusedOverlay();
            this.normalizeOverlayStack();
         } else {
            String focusedId = RiptideSharedState.get().getFocusedOverlayId();
            Map<String, Integer> positions = new HashMap<>();

            for (int i = 0; i < savedOrder.size(); i++) {
               positions.putIfAbsent(savedOrder.get(i), i);
            }

            List<IRiptideOverlay> ordered = new ArrayList<>(this.overlays);
            ordered.sort(
               Comparator.<IRiptideOverlay>comparingInt(overlay -> positions.getOrDefault(overlay.getOverlayId(), Integer.MAX_VALUE))
                  .thenComparingInt(overlay -> focusedId.equals(overlay.getOverlayId()) ? 1 : 0)
            );
            this.overlays.clear();
            this.overlays.addAll(ordered);
            this.overlayLayersDirty = true;
            this.restoreFocusedOverlay();
            this.normalizeOverlayStack();
         }
      }
   }

   private void restoreFocusedOverlay() {
      String focusedId = RiptideSharedState.get().getFocusedOverlayId();
      if (focusedId.isEmpty()) {
         this.focusedOverlay = null;
      } else {
         this.focusedOverlay = null;

         for (int i = this.overlays.size() - 1; i >= 0; i--) {
            IRiptideOverlay overlay = this.overlays.get(i);
            if (overlay != null && focusedId.equals(overlay.getOverlayId()) && this.isOverlayInteractive(overlay)) {
               this.focusedOverlay = overlay;
               break;
            }
         }
      }
   }

   private void saveOverlayOrder() {
      List<String> order = new ArrayList<>(this.overlays.size());

      for (IRiptideOverlay overlay : this.overlays) {
         String id = overlay.getOverlayId();
         if (id != null && !id.isEmpty() && !order.contains(id) && !this.isLauncherOverlay(overlay) && !this.isTransientOverlay(overlay)) {
            order.add(id);
         }
      }

      RiptideSharedState.get().setOverlayOrder(order);
   }

   private void normalizeOverlayStack() {
      if (!this.overlays.isEmpty()) {
         List<IRiptideOverlay> launchers = new ArrayList<>();
         List<IRiptideOverlay> others = new ArrayList<>();

         for (IRiptideOverlay overlay : this.overlays) {
            if (this.isLauncherOverlay(overlay)) {
               launchers.add(overlay);
            } else {
               others.add(overlay);
            }
         }

         if (!launchers.isEmpty()) {
            this.overlays.clear();
            this.overlays.addAll(launchers);
            this.overlays.addAll(others);
            this.overlayLayersDirty = true;
         }
      }
   }

   private boolean isLauncherOverlay(IRiptideOverlay overlay) {
      return overlay instanceof RiptideLauncherOverlay || overlay != null && "riptide-launcher".equals(overlay.getOverlayId());
   }

   private boolean isTransientOverlay(IRiptideOverlay overlay) {
      return overlay != null && "macro-step-picker".equals(overlay.getOverlayId());
   }

   public void reclampAllOverlays() {
      for (IRiptideOverlay overlay : this.overlays) {
         this.reclampOverlayPreserving(overlay);
      }

      this.pruneClampedAwayBounds();
      this.invalidateHoverBlockCache();
   }

   private void pruneClampedAwayBounds() {
      if (!this.clampedAwayBounds.isEmpty()) {
         int sw = RiptideUiScale.getVirtualScreenWidth();
         int sh = RiptideUiScale.getVirtualScreenHeight();
         if (sw > 0 && sh > 0) {
            this.clampedAwayBounds.values().removeIf(stashed -> fitsOnScreen(stashed, sw, sh));
            if (this.clampedAwayBounds.size() > 64) {
               Set<String> live = new HashSet<>();

               for (IRiptideOverlay overlay : this.overlays) {
                  if (overlay != null && overlay.getOverlayId() != null) {
                     live.add(overlay.getOverlayId());
                  }
               }

               this.clampedAwayBounds.keySet().removeIf(id -> !live.contains(id));
            }
         }
      }
   }

   static boolean fitsOnScreen(RiptideWindowLayout bounds, int screenWidth, int screenHeight) {
      return bounds != null && screenWidth > 0 && screenHeight > 0
         ? samePlacement(RiptideWindow.clampToScreenSize(bounds, bounds.width, bounds.height, screenWidth, screenHeight), bounds)
         : false;
   }

   private void reclampOverlayPreserving(IRiptideOverlay overlay) {
      if (overlay != null) {
         int sw = RiptideUiScale.getVirtualScreenWidth();
         int sh = RiptideUiScale.getVirtualScreenHeight();
         if (sw > 0 && sh > 0) {
            String id = overlay.getOverlayId();
            RiptideWindowLayout stashed = id == null ? null : this.clampedAwayBounds.get(id);
            RiptideWindowLayout basis = stashed != null ? stashed : this.trueBoundsOf(overlay, sw, sh);
            if (basis != null) {
               basis = withLiveState(basis, overlay.getBounds());
               RiptideWindowLayout clamped = RiptideWindow.clampToScreenSize(basis, overlay.getMinWidth(), overlay.getMinHeight(), sw, sh);
               if (samePlacement(clamped, basis)) {
                  if (stashed != null) {
                     this.clampedAwayBounds.remove(id);
                     overlay.setBounds(basis);
                  }
               } else {
                  if (stashed == null && id != null) {
                     this.clampedAwayBounds.put(id, basis);
                  }

                  overlay.setBounds(basis);
               }
            }
         }
      }
   }

   static RiptideWindowLayout withLiveState(RiptideWindowLayout geometry, RiptideWindowLayout live) {
      if (geometry == null) {
         return null;
      } else {
         return live == null ? geometry : new RiptideWindowLayout(geometry.x, geometry.y, geometry.width, geometry.height, live.visible, live.collapsed);
      }
   }

   private RiptideWindowLayout trueBoundsOf(IRiptideOverlay overlay, int sw, int sh) {
      RiptideWindowLayout current = overlay.getBounds();
      if (current == null) {
         return null;
      } else {
         RiptideWindowLayout persisted = RiptideSharedState.get().getWindowLayout(overlay.getOverlayId());
         if (persisted == null) {
            return current;
         } else {
            RiptideWindowLayout clampOfPersisted = RiptideWindow.clampToScreenSize(persisted, overlay.getMinWidth(), overlay.getMinHeight(), sw, sh);
            return !samePlacement(persisted, clampOfPersisted) && samePlacement(current, clampOfPersisted) ? persisted : current;
         }
      }
   }

   private static boolean samePlacement(RiptideWindowLayout a, RiptideWindowLayout b) {
      return a != null && b != null && a.x == b.x && a.y == b.y && a.width == b.width && a.height == b.height;
   }

   public RiptideWindowLayout clampedAwayTrueGeometry(String overlayId) {
      return overlayId == null ? null : this.clampedAwayBounds.get(overlayId);
   }

   public void restoreClampedAwayBounds() {
      if (!this.clampedAwayBounds.isEmpty()) {
         int sw = RiptideUiScale.getVirtualScreenWidth();
         int sh = RiptideUiScale.getVirtualScreenHeight();
         if (sw > 0 && sh > 0) {
            for (IRiptideOverlay overlay : this.overlays) {
               if (overlay != null && overlay.getOverlayId() != null) {
                  RiptideWindowLayout stashed = this.clampedAwayBounds.get(overlay.getOverlayId());
                  if (stashed != null) {
                     stashed = withLiveState(stashed, overlay.getBounds());
                     RiptideWindowLayout clamped = RiptideWindow.clampToScreenSize(stashed, overlay.getMinWidth(), overlay.getMinHeight(), sw, sh);
                     if (samePlacement(clamped, stashed)) {
                        this.clampedAwayBounds.remove(overlay.getOverlayId());
                        overlay.setBounds(stashed);
                     }
                  }
               }
            }
         }
      }
   }

   private void publishInteractiveOverlays(boolean active) {
      if (this.anyInteractiveOverlays != active) {
         this.anyInteractiveOverlays = active;
         RiptideRuntimeActivity.publish(256L, active);
      }
   }

   private void logRenderError(Throwable t) {
      long now = System.currentTimeMillis();
      if (now - this.lastRenderErrorLogMs >= 5000L) {
         this.lastRenderErrorLogMs = now;
         riptide.RiptideClientAddon.LOG.warn("[Overlay] render failed; skipped to protect the UI", t);
      }
   }

   public void renderAll(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      if (!PackHideState.isActive()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null) {
            this.hideInvalidOverlaysForCurrentScreen();
            if (mc.getWindow() != null) {
               int sw = RiptideUiScale.getVirtualScreenWidth();
               int sh = RiptideUiScale.getVirtualScreenHeight();
               if (sw > 0 && sh > 0 && (sw != this.lastScreenWidth || sh != this.lastScreenHeight)) {
                  this.lastScreenWidth = sw;
                  this.lastScreenHeight = sh;
                  this.invalidateHoverBlockCache();

                  for (IRiptideOverlay overlay : this.overlays) {
                     this.reclampOverlayPreserving(overlay);
                  }
               }
            }

            Screen screen = mc.gui.screen();
            int virtualMouseX = (int)Math.round(RiptideUiScale.toVirtual(mouseX));
            int virtualMouseY = (int)Math.round(RiptideUiScale.toVirtual(mouseY));
            this.renderOverlays.clear();

            for (IRiptideOverlay overlay : this.overlays) {
               if (this.isOverlayInteractive(overlay, screen)) {
                  this.renderOverlays.add(overlay);
               }
            }

            this.publishInteractiveOverlays(!this.renderOverlays.isEmpty());
            if (this.renderOverlays.isEmpty()) {
               if (RiptideNotifications.hasVisible()) {
                  RiptideUiScale.pushOverlayScale(context);

                  try {
                     context.nextStratum();
                     RiptideNotifications.render(context);
                  } finally {
                     RiptideUiScale.popOverlayScale(context);
                  }
               }
            } else {
               int hoveredOverlayIndex = -1;

               for (int i = this.renderOverlays.size() - 1; i >= 0; i--) {
                  IRiptideOverlay overlayx = this.renderOverlays.get(i);
                  if (overlayx.isMouseOver(virtualMouseX, virtualMouseY)) {
                     hoveredOverlayIndex = i;
                     break;
                  }
               }

               if (this.uiText == null || this.uiText.font() != mc.font) {
                  this.uiText = UiContexts.textRenderer(mc.font);
               }

               UiContext uiContext = new UiContext(
                  context, this.uiTheme, this.uiText, this.lastScreenWidth, this.lastScreenHeight, virtualMouseX, virtualMouseY, delta
               );
               int visibleIndex = 0;

               for (IRiptideOverlay overlayx : this.overlays) {
                  OperationalOverlayComponent adapter = this.adapterFor(overlayx);
                  boolean interactive = this.isOverlayInteractive(overlayx, screen);
                  adapter.setRenderSuppressed(!interactive);
                  adapter.setInputSuppressed(!interactive);
                  adapter.setHoverBlocked(interactive && hoveredOverlayIndex > visibleIndex);
                  if (interactive) {
                     visibleIndex++;
                  }
               }

               this.rebuildOverlayLayers();
               RiptideUiScale.pushOverlayScale(context);

               try {
                  UiScissorStack.global().clear(context);

                  try {
                     this.overlayLayers.render(uiContext);
                  } catch (Throwable var24) {
                     UiScissorStack.global().clear(context);
                     this.logRenderError(var24);
                  }

                  if (RiptideNotifications.hasVisible()) {
                     context.nextStratum();
                     RiptideNotifications.render(context);
                  }
               } finally {
                  UiScissorStack.global().clear(context);
                  RiptideUiScale.popOverlayScale(context);
                  this.renderOverlays.clear();
               }
            }
         }
      }
   }

   public boolean isMouseOverAnyOverlay(double mouseX, double mouseY) {
      return PackHideState.isActive() ? false : this.isMouseOverAnyOverlayVirtual(RiptideUiScale.toVirtual(mouseX), RiptideUiScale.toVirtual(mouseY));
   }

   private boolean isMouseOverAnyOverlayVirtual(double mouseX, double mouseY) {
      for (IRiptideOverlay overlay : this.overlays) {
         if (this.isOverlayInteractive(overlay) && overlay.isMouseOver(mouseX, mouseY)) {
            return true;
         }
      }

      return false;
   }

   public boolean shouldBlockUnderlyingHover(double mouseX, double mouseY) {
      if (PackHideState.isActive()) {
         return false;
      } else if (this.overlays.isEmpty()) {
         return false;
      } else if (!this.anyInteractiveOverlays) {
         return false;
      } else {
         long now = System.nanoTime();
         if (Double.compare(mouseX, this.cachedHoverBlockMouseX) == 0
            && Double.compare(mouseY, this.cachedHoverBlockMouseY) == 0
            && now - this.cachedHoverBlockNanos < 16000000L) {
            return this.cachedHoverBlockResult;
         } else {
            boolean result = this.isMouseOverAnyOverlayVirtual(RiptideUiScale.toVirtual(mouseX), RiptideUiScale.toVirtual(mouseY));
            this.cachedHoverBlockMouseX = mouseX;
            this.cachedHoverBlockMouseY = mouseY;
            this.cachedHoverBlockNanos = now;
            this.cachedHoverBlockResult = result;
            return result;
         }
      }
   }

   private void invalidateHoverBlockCache() {
      this.cachedHoverBlockMouseX = Double.NaN;
      this.cachedHoverBlockMouseY = Double.NaN;
      this.cachedHoverBlockNanos = 0L;
      this.cachedHoverBlockResult = false;
   }

   private void clearFocusedTextFields() {
      for (IRiptideOverlay overlay : this.overlays) {
         if (this.isOverlayInteractive(overlay) && overlay.hasTextFieldFocused()) {
            overlay.clearTextFieldFocus();
         }
      }

      this.textFieldFocusOverlay = null;
      this.textFieldFocusDirty = false;
   }

   private IRiptideOverlay getTopmostOverlayAt(double mouseX, double mouseY) {
      for (int i = this.overlays.size() - 1; i >= 0; i--) {
         IRiptideOverlay overlay = this.overlays.get(i);
         if (this.isOverlayInteractive(overlay) && overlay.isMouseOver(mouseX, mouseY)) {
            return overlay;
         }
      }

      return null;
   }

   public boolean isTopOverlay(IRiptideOverlay overlay) {
      if (overlay != null && !this.overlays.isEmpty()) {
         for (int i = this.overlays.size() - 1; i >= 0; i--) {
            IRiptideOverlay candidate = this.overlays.get(i);
            if (this.isOverlayInteractive(candidate)) {
               return candidate == overlay;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public boolean isFocusedOverlay(IRiptideOverlay overlay) {
      return overlay != null && overlay == this.focusedOverlay;
   }

   public boolean handleMouseClicked(double mouseX, double mouseY, int button) {
      if (PackHideState.isActive()) {
         return false;
      } else {
         this.inventoryMouseDown = false;
         mouseX = RiptideUiScale.toVirtual(mouseX);
         mouseY = RiptideUiScale.toVirtual(mouseY);
         IRiptideOverlay topOverlay = this.getTopmostOverlayAt(mouseX, mouseY);
         if (topOverlay == null) {
            this.clearFocusedTextFields();
            this.focusedOverlay = null;
            RiptideSharedState.get().setFocusedOverlayId("");
            this.headerCollapseOverlay = null;
            this.headerCollapseMoved = false;
            this.headerCollapseStartBounds = null;
            this.inventoryMouseDown = true;
            return false;
         } else {
            this.clearFocusedTextFields();
            if (button == 0) {
               if (topOverlay.isOverResizeHandle(mouseX, mouseY)) {
                  this.resizingOverlay = topOverlay;
                  this.resizeStartBounds = topOverlay.getBounds();
                  this.resizeStartMouseX = mouseX;
                  this.resizeStartMouseY = mouseY;
                  this.bringToFront(topOverlay);
                  return true;
               }

               if (topOverlay.isOverDragBar(mouseX, mouseY)) {
                  this.draggingOverlay = topOverlay;
                  this.dragStartBounds = topOverlay.getBounds();
                  if (topOverlay.usesSharedHeaderClickCollapse()) {
                     this.headerCollapseOverlay = topOverlay;
                     this.headerCollapseMoved = false;
                     this.headerCollapseStartMouseX = mouseX;
                     this.headerCollapseStartMouseY = mouseY;
                     this.headerCollapseStartBounds = topOverlay.getBounds();
                  } else {
                     this.headerCollapseOverlay = null;
                     this.headerCollapseMoved = false;
                     this.headerCollapseStartBounds = null;
                  }

                  this.bringToFront(topOverlay);
                  this.adapterFor(topOverlay).mouseClicked((int)mouseX, (int)mouseY, button);
                  this.captureTextFieldFocus(topOverlay);
                  return true;
               }
            }

            this.headerCollapseOverlay = null;
            this.headerCollapseMoved = false;
            this.headerCollapseStartBounds = null;
            this.bringToFront(topOverlay);
            this.syncOverlayLayerInput();
            OperationalOverlayComponent topComponent = this.adapterFor(topOverlay);
            UiBounds topHitBounds = topComponent.hitBounds();
            if (topHitBounds != null && topHitBounds.contains((int)mouseX, (int)mouseY)) {
               this.overlayInput.mouseClicked((int)mouseX, (int)mouseY, button);
            } else {
               topComponent.mouseClicked((int)mouseX, (int)mouseY, button);
            }

            this.captureTextFieldFocus(topOverlay);
            return true;
         }
      }
   }

   public boolean handleMouseReleased(double mouseX, double mouseY, int button) {
      if (PackHideState.isActive()) {
         return false;
      } else {
         mouseX = RiptideUiScale.toVirtual(mouseX);
         mouseY = RiptideUiScale.toVirtual(mouseY);
         boolean wasDraggingOrResizing = this.draggingOverlay != null || this.resizingOverlay != null;
         IRiptideOverlay prevDragging = this.draggingOverlay;
         IRiptideOverlay prevResizing = this.resizingOverlay;
         boolean shouldToggleHeaderCollapse = button == 0
            && prevDragging != null
            && prevDragging == this.headerCollapseOverlay
            && prevDragging.usesSharedHeaderClickCollapse()
            && !this.headerCollapseMoved;
         RiptideWindowLayout headerStartBounds = this.headerCollapseStartBounds;
         if (button == 0) {
            if (prevDragging != null
               && prevDragging.getOverlayId() != null
               && this.dragStartBounds != null
               && !samePlacement(prevDragging.getBounds(), this.dragStartBounds)) {
               this.clampedAwayBounds.remove(prevDragging.getOverlayId());
            }

            if (prevResizing != null && prevResizing.getOverlayId() != null) {
               this.clampedAwayBounds.remove(prevResizing.getOverlayId());
            }

            this.draggingOverlay = null;
            this.dragStartBounds = null;
            if (this.resizingOverlay != null) {
               this.resizingOverlay.saveLayout();
            }

            this.resizingOverlay = null;
            this.resizeStartBounds = null;
            this.headerCollapseOverlay = null;
            this.headerCollapseMoved = false;
            this.headerCollapseStartBounds = null;
         }

         if (prevDragging != null) {
            this.adapterFor(prevDragging).mouseReleased((int)mouseX, (int)mouseY, button);
         }

         if (prevResizing != null && prevResizing != prevDragging) {
            this.adapterFor(prevResizing).mouseReleased((int)mouseX, (int)mouseY, button);
         }

         if (shouldToggleHeaderCollapse && this.isOverlayInteractive(prevDragging)) {
            if (headerStartBounds != null) {
               RiptideWindowLayout current = prevDragging.getBounds();
               prevDragging.setBounds(
                  new RiptideWindowLayout(headerStartBounds.x, headerStartBounds.y, current.width, current.height, current.visible, current.collapsed)
               );
            }

            prevDragging.toggleCollapsed();
            prevDragging.saveLayout();
            this.invalidateHoverBlockCache();
            return true;
         } else if (wasDraggingOrResizing) {
            return true;
         } else if (this.inventoryMouseDown) {
            this.inventoryMouseDown = false;
            return false;
         } else {
            for (int i = this.overlays.size() - 1; i >= 0; i--) {
               IRiptideOverlay overlay = this.overlays.get(i);
               if (this.isOverlayInteractive(overlay) && this.adapterFor(overlay).mouseReleased((int)mouseX, (int)mouseY, button) == UiInputResult.HANDLED) {
                  return true;
               }
            }

            return this.isMouseOverAnyOverlayVirtual(mouseX, mouseY);
         }
      }
   }

   public boolean handleMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (PackHideState.isActive()) {
         return false;
      } else {
         mouseX = RiptideUiScale.toVirtual(mouseX);
         mouseY = RiptideUiScale.toVirtual(mouseY);
         deltaX = RiptideUiScale.toVirtual(deltaX);
         deltaY = RiptideUiScale.toVirtual(deltaY);
         if (this.resizingOverlay != null && this.resizeStartBounds != null && this.isOverlayInteractive(this.resizingOverlay)) {
            RiptideWindowLayout current = this.resizingOverlay.getBounds();
            RiptideWindowLayout resized = new RiptideWindowLayout(
               this.resizeStartBounds.x,
               this.resizeStartBounds.y,
               Math.max(this.resizingOverlay.getMinWidth(), this.resizeStartBounds.width + (int)Math.round(mouseX - this.resizeStartMouseX)),
               Math.max(this.resizingOverlay.getMinHeight(), this.resizeStartBounds.height + (int)Math.round(mouseY - this.resizeStartMouseY)),
               current.visible,
               current.collapsed
            );
            this.resizingOverlay.setBounds(resized);
            this.invalidateHoverBlockCache();
            return true;
         } else if (!this.isOverlayInteractive(this.draggingOverlay)) {
            if (this.inventoryMouseDown) {
               return false;
            } else {
               for (int i = this.overlays.size() - 1; i >= 0; i--) {
                  IRiptideOverlay overlay = this.overlays.get(i);
                  if (this.isOverlayInteractive(overlay)
                     && this.adapterFor(overlay).mouseDragged((int)mouseX, (int)mouseY, button, deltaX, deltaY) == UiInputResult.HANDLED) {
                     return true;
                  }
               }

               return this.isMouseOverAnyOverlayVirtual(mouseX, mouseY);
            }
         } else {
            if (this.draggingOverlay == this.headerCollapseOverlay
               && !this.headerCollapseMoved
               && (Math.abs(mouseX - this.headerCollapseStartMouseX) >= 3.0 || Math.abs(mouseY - this.headerCollapseStartMouseY) >= 3.0)) {
               this.headerCollapseMoved = true;
            }

            boolean handled = this.adapterFor(this.draggingOverlay).mouseDragged((int)mouseX, (int)mouseY, button, deltaX, deltaY) == UiInputResult.HANDLED;
            this.invalidateHoverBlockCache();
            return handled;
         }
      }
   }

   public boolean handleMouseScrolled(double mouseX, double mouseY, double amount) {
      if (PackHideState.isActive()) {
         return false;
      } else {
         mouseX = RiptideUiScale.toVirtual(mouseX);
         mouseY = RiptideUiScale.toVirtual(mouseY);
         IRiptideOverlay topOverlay = this.getTopmostOverlayAt(mouseX, mouseY);
         if (topOverlay == null) {
            return false;
         } else {
            this.bringToFront(topOverlay);
            this.syncOverlayLayerInput();
            OperationalOverlayComponent topComponent = this.adapterFor(topOverlay);
            UiBounds topHitBounds = topComponent.hitBounds();
            if (topHitBounds != null && topHitBounds.contains((int)mouseX, (int)mouseY)) {
               this.overlayInput.mouseScrolled((int)mouseX, (int)mouseY, amount);
            } else {
               topComponent.mouseScrolled((int)mouseX, (int)mouseY, amount);
            }

            return true;
         }
      }
   }

   public boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
      if (PackHideState.isActive()) {
         return false;
      } else {
         for (int i = this.overlays.size() - 1; i >= 0; i--) {
            IRiptideOverlay overlay = this.overlays.get(i);
            if (this.isOverlayInteractive(overlay)
               && overlay.wantsKeyboardCapture()
               && this.adapterFor(overlay).keyPressed(keyCode, scanCode, modifiers) == UiInputResult.HANDLED) {
               this.bringToFront(overlay);
               this.captureTextFieldFocus(overlay);
               return true;
            }
         }

         IRiptideOverlay focusedTextOverlay = this.getTextFieldFocusOverlay();
         if (focusedTextOverlay != null) {
            this.adapterFor(focusedTextOverlay).keyPressed(keyCode, scanCode, modifiers);
            this.captureTextFieldFocus(focusedTextOverlay);
            this.focusedOverlay = focusedTextOverlay;
            RiptideSharedState.get().setFocusedOverlayId(focusedTextOverlay.getOverlayId());
            return true;
         } else {
            IRiptideOverlay keyboardTarget = this.getKeyboardTargetOverlay();
            if (keyboardTarget != null && this.adapterFor(keyboardTarget).keyPressed(keyCode, scanCode, modifiers) == UiInputResult.HANDLED) {
               this.captureTextFieldFocus(keyboardTarget);
               return true;
            } else {
               return this.isAnyTextFieldFocused();
            }
         }
      }
   }

   public boolean handleCharTyped(char chr, int modifiers) {
      if (PackHideState.isActive()) {
         return false;
      } else {
         IRiptideOverlay focusedTextOverlay = this.getTextFieldFocusOverlay();
         if (focusedTextOverlay != null) {
            this.adapterFor(focusedTextOverlay).charTyped(chr, modifiers);
            this.captureTextFieldFocus(focusedTextOverlay);
            this.focusedOverlay = focusedTextOverlay;
            RiptideSharedState.get().setFocusedOverlayId(focusedTextOverlay.getOverlayId());
            return true;
         } else {
            IRiptideOverlay keyboardTarget = this.getKeyboardTargetOverlay();
            if (keyboardTarget != null && this.adapterFor(keyboardTarget).charTyped(chr, modifiers) == UiInputResult.HANDLED) {
               this.captureTextFieldFocus(keyboardTarget);
               return true;
            } else {
               return this.isAnyTextFieldFocused();
            }
         }
      }
   }

   private IRiptideOverlay getTextFieldFocusOverlay() {
      if (!this.textFieldFocusDirty) {
         IRiptideOverlay cached = this.textFieldFocusOverlay;
         if (cached != null && this.isOverlayInteractive(cached) && cached.hasTextFieldFocused()) {
            return cached;
         }
      }

      for (int i = this.overlays.size() - 1; i >= 0; i--) {
         IRiptideOverlay overlay = this.overlays.get(i);
         if (this.isOverlayInteractive(overlay) && overlay.hasTextFieldFocused()) {
            this.textFieldFocusOverlay = overlay;
            this.textFieldFocusDirty = false;
            return overlay;
         }
      }

      this.textFieldFocusOverlay = null;
      this.textFieldFocusDirty = false;
      return null;
   }

   private void captureTextFieldFocus(IRiptideOverlay overlay) {
      this.textFieldFocusOverlay = this.isOverlayInteractive(overlay) && overlay.hasTextFieldFocused() ? overlay : null;
      this.textFieldFocusDirty = false;
   }

   private IRiptideOverlay getKeyboardTargetOverlay() {
      if (this.isOverlayInteractive(this.focusedOverlay)) {
         return this.focusedOverlay;
      } else {
         for (int i = this.overlays.size() - 1; i >= 0; i--) {
            IRiptideOverlay overlay = this.overlays.get(i);
            if (this.isOverlayInteractive(overlay)) {
               return overlay;
            }
         }

         return null;
      }
   }

   public boolean isAnyTextFieldFocused() {
      return PackHideState.isActive() ? false : this.getTextFieldFocusOverlay() != null;
   }

   public void clearTextFieldFocus() {
      this.clearFocusedTextFields();
      this.focusedOverlay = null;
      RiptideSharedState.get().setFocusedOverlayId("");
   }

   private OperationalOverlayComponent adapterFor(IRiptideOverlay overlay) {
      return this.overlayComponents.computeIfAbsent(overlay, OperationalOverlayComponent::new);
   }

   private void syncOverlayLayerInput() {
      for (IRiptideOverlay overlay : this.overlays) {
         this.adapterFor(overlay).setInputSuppressed(!this.isOverlayInteractive(overlay));
      }

      this.rebuildOverlayLayers();
   }

   private void rebuildOverlayLayers() {
      if (this.overlayLayersDirty) {
         this.overlayLayers.clear();

         for (IRiptideOverlay overlay : this.overlays) {
            this.overlayLayers.add(this.isTransientOverlay(overlay) ? UiLayer.DROPDOWN : UiLayer.FLOATING, this.adapterFor(overlay));
         }

         this.overlayLayersDirty = false;
      }
   }
}
