package riptide.mixin;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterExtract;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.gui.macro.editor.ActionEditorOverlay;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiTextRenderer;
import riptide.gui.vanillaui.components.Banner;
import riptide.modules.InventoryTweaksModule;
import riptide.modules.NameCensorModule;
import riptide.modules.RiptideModule;
import riptide.util.IRiptideOverlay;
import riptide.util.RiptideAdminToolsOverlay;
import riptide.util.RiptideContainerHold;
import riptide.util.RiptideCursorClickHelper;
import riptide.util.RiptideCustomFilterOverlay;
import riptide.util.RiptideCustomFilterPresetOverlay;
import riptide.util.RiptideFabricatorOverlay;
import riptide.util.RiptideInventoryHelper;
import riptide.util.RiptideInventoryMoveHelper;
import riptide.util.RiptideItemNbtInspectOverlay;
import riptide.util.RiptideKeybindOverlay;
import riptide.util.RiptideLANSync;
import riptide.util.RiptideLANSyncOverlay;
import riptide.util.RiptideLauncherOverlay;
import riptide.util.RiptideLiteVariant;
import riptide.util.RiptideMacroEditorOverlay;
import riptide.util.RiptideMacroListOverlay;
import riptide.util.RiptideOverlayManager;
import riptide.util.RiptidePacketClick;
import riptide.util.RiptidePacketLoggerOverlay;
import riptide.util.RiptidePerf;
import riptide.util.RiptideQueueEditorOverlay;
import riptide.util.RiptideServerInfoOverlay;
import riptide.util.RiptideSharedState;
import riptide.util.RiptideShulkerPreview;
import riptide.util.RiptideUiScale;

@Mixin({AbstractContainerScreen.class})
public abstract class RiptideHandledScreenMixin<T extends AbstractContainerMenu> extends Screen {
   @Shadow
   @Nullable
   protected Slot hoveredSlot;
   @Shadow
   protected int leftPos;
   @Shadow
   protected int topPos;
   @Unique
   private static final Minecraft MC = Minecraft.getInstance();
   @Unique
   private Slot riptide$blockedFocusedSlot;
   @Unique
   private RiptideLauncherOverlay launcherOverlay;
   @Unique
   private RiptideFabricatorOverlay fabricatorOverlay;
   @Unique
   private RiptideLANSyncOverlay lanSyncOverlay;
   @Unique
   private RiptideMacroListOverlay macroListOverlay;
   @Unique
   private RiptideQueueEditorOverlay queueEditorOverlay;
   @Unique
   private RiptidePacketLoggerOverlay packetLoggerOverlay;
   @Unique
   private RiptideCustomFilterOverlay customFilterOverlay;
   @Unique
   private RiptideCustomFilterPresetOverlay customFilterPresetOverlay;
   @Unique
   private RiptideMacroEditorOverlay macroEditorOverlay;
   @Unique
   private RiptideItemNbtInspectOverlay itemNbtInspectOverlay;
   @Unique
   private RiptideKeybindOverlay keybindOverlay;
   @Unique
   private RiptideServerInfoOverlay serverInfoOverlay;
   @Unique
   private IRiptideOverlay matchmakingOverlay;
   @Unique
   private IRiptideOverlay profilesOverlay;
   @Unique
   private Button inventoryTweaksStealButton;
   @Unique
   private Button inventoryTweaksDumpButton;
   @Unique
   private boolean riptide$overlaysBuilt;
   @Unique
   private int inventoryTweaksLastShiftDragSlot = -1;
   @Unique
   private ItemStack riptide$cursorClickBeforeCarried = ItemStack.EMPTY;
   @Unique
   private ItemStack riptide$cursorClickBeforeSlot = ItemStack.EMPTY;
   @Unique
   private int riptide$cursorClickBeforeSlotId = -1;
   @Unique
   private int riptide$cursorClickBeforeButton = 0;
   @Unique
   private ContainerInput riptide$cursorClickBeforeInput = null;
   @Unique
   private static long riptide$lastOverlayErrorMs;
   @Unique
   private boolean coreExpanded = true;
   @Unique
   private boolean queueExpanded = false;
   @Unique
   private boolean toolsExpanded = false;
   @Unique
   private int coreButtonsStartY;
   @Unique
   private int queueHeaderY;
   @Unique
   private int queueButtonsStartY;
   @Unique
   private int queueButtonsEndY;
   @Unique
   private int toolsHeaderY;
   @Unique
   private int toolsButtonsStartY;
   @Unique
   private int toolsButtonsEndY;

   @Shadow
   protected abstract void slotClicked(Slot var1, int var2, int var3, ContainerInput var4);

   protected RiptideHandledScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void riptide$syncInvMoveOnInit(CallbackInfo ci) {
      if (this.isRiptideActive()) {
         RiptideInventoryMoveHelper.syncHeldMovementKeysIfSafe();
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")}
   )
   private void riptide$syncInvMoveOnTick(CallbackInfo ci) {
      if (this.isRiptideActive()) {
         RiptideInventoryMoveHelper.syncHeldMovementKeysIfSafe();
      }
   }

   @ModifyArg(
      method = {"extractLabels"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"
      ),
      index = 1,
      require = 0
   )
   private Component riptide$censorContainerLabel(Component component) {
      return NameCensorModule.censorServerComponent(component);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void yang$init(CallbackInfo ci) {
      Screen screen = this;
      ScreenEvents.afterExtract(screen).register((AfterExtract)(scrn, drawContext, mouseX, mouseY, tickDelta) -> {
         if (this.isRiptideActive()) {
            try {
               RiptideOverlayManager.get().renderAll(drawContext, mouseX, mouseY, tickDelta);
               RiptideUiScale.pushOverlayScale(drawContext);

               try {
                  this.renderMacroCaptureBanner(drawContext);
               } finally {
                  RiptideUiScale.popOverlayScale(drawContext);
               }

               this.riptide$renderShulkerPreview(drawContext, mouseX, mouseY);
            } catch (Throwable var10) {
               this.riptide$logOverlayError(var10);
            }
         }
      });

      try {
         long perfStart = RiptidePerf.begin();
         if (!this.riptide$overlaysBuilt || !RiptideOverlayManager.get().hasRegisteredOverlays()) {
            this.riptide$buildInventoryOverlays();
            this.riptide$overlaysBuilt = true;
         }

         RiptidePerf.endSpike("mixin.buildInventoryOverlays", perfStart, 10000000L);
      } catch (Throwable var5) {
         this.riptide$logOverlayError(var5);
      }
   }

   @Unique
   private void riptide$buildInventoryOverlays() {
      RiptideLANSync.getInstance().setOnSessionStateChanged(() -> {});
      AbstractContainerScreen<?> handledScreen = (AbstractContainerScreen<?>)this;
      this.fabricatorOverlay = RiptideFabricatorOverlay.getSharedOverlay(handledScreen);
      this.lanSyncOverlay = RiptideLANSyncOverlay.getSharedOverlay(this.font);
      this.macroListOverlay = new RiptideMacroListOverlay(this.font);
      this.queueEditorOverlay = new RiptideQueueEditorOverlay(this.font);
      this.customFilterOverlay = new RiptideCustomFilterOverlay(this.font);
      this.customFilterPresetOverlay = this.customFilterOverlay.getPresetManagerOverlay();
      this.fabricatorOverlay.restoreState();
      this.lanSyncOverlay.restoreState();
      this.macroListOverlay.restoreState();
      this.queueEditorOverlay.restoreState();
      this.customFilterOverlay.restoreLayout();
      if (this.customFilterPresetOverlay != null) {
         this.customFilterPresetOverlay.restoreLayout();
      }

      this.macroEditorOverlay = RiptideMacroEditorOverlay.getSharedOverlay();
      if (this.macroEditorOverlay != null) {
         this.macroEditorOverlay.restoreState();
      }

      RiptideOverlayManager manager = RiptideOverlayManager.get();
      manager.clear();
      manager.register(this.fabricatorOverlay);
      manager.register(this.lanSyncOverlay);
      manager.register(this.macroListOverlay);
      manager.register(this.queueEditorOverlay);
      manager.register(this.customFilterOverlay);
      if (this.customFilterPresetOverlay != null) {
         manager.register(this.customFilterPresetOverlay);
      }

      if (this.macroEditorOverlay != null) {
         manager.register(this.macroEditorOverlay);
      }

      manager.register(ActionEditorOverlay.getSharedOverlay());
      this.itemNbtInspectOverlay = RiptideItemNbtInspectOverlay.getSharedOverlay(this.font);
      if (this.itemNbtInspectOverlay != null) {
         manager.register(this.itemNbtInspectOverlay);
      }

      RiptideModule riptideModule = RiptideModule.get();
      if (!RiptideLiteVariant.enabled()) {
         IRiptideOverlay multiOverlay = riptideModule == null ? null : riptideModule.getMultiOverlayIfExists();
         if (multiOverlay != null && multiOverlay.isVisible()) {
            manager.register(multiOverlay);
         }
      }

      this.keybindOverlay = new RiptideKeybindOverlay();
      this.keybindOverlay.restoreLayout();
      manager.register(this.keybindOverlay);
      this.launcherOverlay = new RiptideLauncherOverlay(
         this.macroListOverlay, this.fabricatorOverlay, this.lanSyncOverlay, this.queueEditorOverlay, this.packetLoggerOverlay, this.customFilterOverlay
      );
      this.launcherOverlay.setKeybindOverlay(this.keybindOverlay);
      this.launcherOverlay.setPacketLoggerOverlaySupplier(() -> {
         if (this.packetLoggerOverlay == null && riptideModule != null) {
            this.packetLoggerOverlay = riptideModule.getPacketLoggerOverlay();
            if (this.packetLoggerOverlay != null) {
               this.packetLoggerOverlay.restoreState();
            }
         }

         if (this.packetLoggerOverlay != null) {
            manager.register(this.packetLoggerOverlay);
         }

         return this.packetLoggerOverlay;
      });
      this.launcherOverlay.setServerDataOverlaySupplier(() -> {
         if (this.serverInfoOverlay == null) {
            this.serverInfoOverlay = RiptideModule.get().getServerDataOverlay();
         }

         if (this.serverInfoOverlay != null) {
            manager.register(this.serverInfoOverlay);
         }

         return this.serverInfoOverlay;
      });
      if (this.packetLoggerOverlay == null && RiptidePacketLoggerOverlay.shouldRestoreSavedVisible()) {
         this.packetLoggerOverlay = RiptideModule.get().getPacketLoggerOverlay();
         if (this.packetLoggerOverlay != null) {
            this.packetLoggerOverlay.restoreState();
            if (this.packetLoggerOverlay.isVisible()) {
               manager.register(this.packetLoggerOverlay);
            }
         }
      }

      if (this.serverInfoOverlay == null && RiptideServerInfoOverlay.shouldRestoreSavedVisible()) {
         this.serverInfoOverlay = RiptideModule.get().getServerDataOverlay();
         if (this.serverInfoOverlay != null) {
            this.serverInfoOverlay.restoreState();
            if (this.serverInfoOverlay.isVisible()) {
               manager.register(this.serverInfoOverlay);
            }
         }
      }

      if (!RiptideLiteVariant.enabled()) {
         this.matchmakingOverlay = RiptideModule.get().getMatchmakingOverlay();
         if (this.matchmakingOverlay != null && this.matchmakingOverlay.isVisible()) {
            manager.register(this.matchmakingOverlay);
         }

         this.profilesOverlay = RiptideModule.get().getProfilesOverlay();
         if (this.profilesOverlay != null && this.profilesOverlay.isVisible()) {
            manager.register(this.profilesOverlay);
         }
      }

      this.launcherOverlay.restoreLayout();
      manager.register(this.launcherOverlay);
      this.inventoryTweaksStealButton = Button.builder(Component.literal("Steal"), button -> InventoryTweaksModule.stealFromButton())
         .bounds(this.leftPos, this.topPos - 22, 40, 20)
         .build();
      this.inventoryTweaksDumpButton = Button.builder(Component.literal("Dump"), button -> InventoryTweaksModule.dumpFromButton())
         .bounds(this.leftPos + 42, this.topPos - 22, 40, 20)
         .build();
      this.addRenderableWidget(this.inventoryTweaksStealButton);
      this.addRenderableWidget(this.inventoryTweaksDumpButton);
      this.refreshButtonVisibility();
   }

   @Unique
   private void riptide$logOverlayError(Throwable t) {
      long now = System.currentTimeMillis();
      if (now - riptide$lastOverlayErrorMs >= 5000L) {
         riptide$lastOverlayErrorMs = now;
         riptide.RiptideClientAddon.LOG.warn("[Riptide] in-screen overlay render failed; isolated to protect the UI", t);
      }
   }

   @Unique
   private void refreshButtonVisibility() {
      boolean visible = this.isRiptideActive() && MC != null && MC.player != null && InventoryTweaksModule.shouldShowButtons(MC.player.containerMenu);
      if (this.inventoryTweaksStealButton != null) {
         this.inventoryTweaksStealButton.visible = visible;
         this.inventoryTweaksStealButton.active = visible;
         this.inventoryTweaksStealButton.setX(this.leftPos);
         this.inventoryTweaksStealButton.setY(this.topPos - 22);
      }

      if (this.inventoryTweaksDumpButton != null) {
         this.inventoryTweaksDumpButton.visible = visible;
         this.inventoryTweaksDumpButton.active = visible;
         this.inventoryTweaksDumpButton.setX(this.leftPos + 42);
         this.inventoryTweaksDumpButton.setY(this.topPos - 22);
      }
   }

   @Inject(
      method = {"extractRenderState"},
      at = {@At("HEAD")}
   )
   private void yang$blockCoveredSlotHover(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (this.isRiptideActive()) {
         if (RiptideOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) {
            this.riptide$blockedFocusedSlot = this.hoveredSlot;
            this.hoveredSlot = null;
         } else {
            this.riptide$blockedFocusedSlot = null;
         }
      }
   }

   @Inject(
      method = {"getHoveredSlot"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$blockCoveredSlotLookup(double mouseX, double mouseY, CallbackInfoReturnable<Slot> cir) {
      if (this.isRiptideActive()) {
         if (RiptideOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) {
            cir.setReturnValue(null);
         }
      }
   }

   @Inject(
      method = {"isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$blockCoveredSlotHitbox(Slot slot, double pointX, double pointY, CallbackInfoReturnable<Boolean> cir) {
      if (this.isRiptideActive()) {
         if (RiptideOverlayManager.get().shouldBlockUnderlyingHover(pointX, pointY)) {
            cir.setReturnValue(false);
         }
      }
   }

   @Inject(
      method = {"extractTooltip"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$blockCoveredHandledTooltip(GuiGraphicsExtractor context, int x, int y, CallbackInfo ci) {
      if (this.isRiptideActive()) {
         if (RiptideOverlayManager.get().shouldBlockUnderlyingHover(x, y)) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   public void yang$render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (this.riptide$blockedFocusedSlot != null) {
         this.hoveredSlot = this.riptide$blockedFocusedSlot;
         this.riptide$blockedFocusedSlot = null;
      }

      this.refreshButtonVisibility();
   }

   @Unique
   private void renderMacroCaptureBanner(GuiGraphicsExtractor context) {
      ActionEditorOverlay actionEditor = ActionEditorOverlay.getSharedOverlayIfExists();
      RiptideAdminToolsOverlay adminToolsOverlay = null;
      boolean adminCapture = false;
      if (!RiptideLiteVariant.enabled()) {
         adminToolsOverlay = RiptideAdminToolsOverlay.getSharedOverlayIfExists();
         adminCapture = adminToolsOverlay != null && adminToolsOverlay.shouldRenderAbstractContainerScreenCaptureBanner();
      }

      boolean macroCapture = this.macroEditorOverlay != null && this.macroEditorOverlay.shouldRenderAbstractContainerScreenCaptureBanner();
      boolean actionCapture = actionEditor != null && actionEditor.shouldRenderAbstractContainerScreenCaptureBanner();
      if (macroCapture || actionCapture || adminCapture) {
         if (MC != null && MC.getWindow() != null && this.font != null) {
            String title = macroCapture
               ? this.macroEditorOverlay.getAbstractContainerScreenCaptureTitle()
               : (
                  actionCapture
                     ? actionEditor.getAbstractContainerScreenCaptureTitle()
                     : (!RiptideLiteVariant.enabled() ? adminToolsOverlay.getAbstractContainerScreenCaptureTitle() : "")
               );
            String instruction = macroCapture
               ? this.macroEditorOverlay.getAbstractContainerScreenCaptureInstruction()
               : (
                  actionCapture
                     ? actionEditor.getAbstractContainerScreenCaptureInstruction()
                     : (!RiptideLiteVariant.enabled() ? adminToolsOverlay.getAbstractContainerScreenCaptureInstruction() : "")
               );
            String hover = "";
            if (this.hoveredSlot != null) {
               ItemStack stack = this.hoveredSlot.getItem();
               String itemName = stack.isEmpty() ? "" : stack.getHoverName().getString();
               String registryId = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
               hover = macroCapture
                  ? this.macroEditorOverlay.getAbstractContainerScreenCaptureHoverText(this.hoveredSlot, itemName, registryId)
                  : (
                     actionCapture
                        ? actionEditor.getAbstractContainerScreenCaptureHoverText(this.hoveredSlot, itemName, registryId)
                        : (
                           !RiptideLiteVariant.enabled()
                              ? adminToolsOverlay.getAbstractContainerScreenCaptureHoverText(this.hoveredSlot, itemName, registryId)
                              : ""
                        )
                  );
            }

            UiTextRenderer text = UiContexts.textRenderer(this.font);
            int maxTextWidth = Math.max(text.width(title), text.width(instruction));
            if (!hover.isEmpty()) {
               maxTextWidth = Math.max(maxTextWidth, text.width(hover));
            }

            int screenWidth = RiptideUiScale.getVirtualScreenWidth();
            int boxWidth = Math.min(screenWidth - 16, Math.max(250, maxTextWidth + 18));
            int boxX = (screenWidth - boxWidth) / 2;
            int boxY = 0;
            UiContext uiContext = UiContexts.overlay(context, this.font, 0, 0);
            int bannerHeight = Banner.height(uiContext, boxWidth, instruction, hover);
            Banner.render(uiContext, UiBounds.of(boxX, boxY, boxWidth, bannerHeight), title, instruction, hover);
            if (actionCapture && actionEditor != null && actionEditor.hasAbstractContainerScreenCaptureToasts()) {
               actionEditor.renderAbstractContainerScreenCaptureToasts(context, boxX, boxY + bannerHeight + 6, boxWidth);
            }
         }
      }
   }

   @Inject(
      method = {"removed"},
      at = {@At("HEAD")}
   )
   private void yang$removed(CallbackInfo ci) {
      if (this.isRiptideActive()) {
         RiptideInventoryMoveHelper.releaseMovementKeysIfSafe();
         ActionEditorOverlay actionEditor = ActionEditorOverlay.getSharedOverlayIfExists();
         boolean skipTransientCaptureSave = actionEditor != null && actionEditor.hasActiveCaptureSession();
         RiptideOverlayManager.get().restoreClampedAwayBounds();
         long perfStart = RiptidePerf.begin();
         if (!skipTransientCaptureSave) {
            if (this.fabricatorOverlay != null) {
               this.fabricatorOverlay.saveState();
            }

            if (this.lanSyncOverlay != null) {
               this.lanSyncOverlay.saveState();
            }

            if (this.macroListOverlay != null) {
               this.macroListOverlay.saveState();
            }

            if (this.queueEditorOverlay != null) {
               this.queueEditorOverlay.saveState();
            }

            if (this.macroEditorOverlay != null) {
               this.macroEditorOverlay.saveState();
            }

            if (this.launcherOverlay != null) {
               this.launcherOverlay.saveLayout();
            }

            if (this.packetLoggerOverlay != null) {
               this.packetLoggerOverlay.saveState();
            }

            if (this.customFilterOverlay != null) {
               this.customFilterOverlay.saveLayout();
            }

            if (this.customFilterPresetOverlay != null) {
               this.customFilterPresetOverlay.saveLayout();
            }

            if (this.keybindOverlay != null) {
               this.keybindOverlay.saveLayout();
            }

            if (this.serverInfoOverlay != null) {
               this.serverInfoOverlay.saveState();
            }

            if (this.matchmakingOverlay != null) {
               this.matchmakingOverlay.saveLayout();
            }

            if (this.profilesOverlay != null) {
               this.profilesOverlay.saveLayout();
            }
         }

         RiptidePerf.endSpike("mixin.removedSaveState", perfStart, 10000000L);
         RiptideOverlayManager.get().clear();
      }
   }

   @Unique
   private void riptide$renderShulkerPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
      if (this.hoveredSlot != null) {
         if (!RiptideOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) {
            ItemStack stack = this.hoveredSlot.getItem();
            if (RiptideShulkerPreview.shouldPreview(stack)) {
               context.nextStratum();
               RiptideShulkerPreview.render(context, this.font, stack, this.hoveredSlot.index, mouseX, mouseY, this.width, this.height);
            }
         }
      }
   }

   @Unique
   private boolean isRiptideActive() {
      RiptideModule module = RiptideModule.get();
      return module != null && module.isActive();
   }

   @Unique
   private void updateButtonLabels() {
   }

   @Unique
   private static String onOff(boolean value) {
      return value ? "ON" : "OFF";
   }

   @Unique
   private static String stateText(boolean value) {
      return value ? "enabled" : "disabled";
   }

   @Inject(
      method = {"mouseClicked"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yang$mouseClicked(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
      if (this.isRiptideActive()) {
         double mouseX = click.x();
         double mouseY = click.y();
         int button = click.button();
         RiptideOverlayManager manager = RiptideOverlayManager.get();
         if (manager.handleMouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
         } else if (InventoryTweaksModule.handleSortMouse(button, this.hoveredSlot)) {
            cir.setReturnValue(true);
         } else {
            if (button == 1 && this.hoveredSlot != null && click.hasControlDown() && click.hasShiftDown()) {
               ItemStack stack = this.hoveredSlot.getItem();
               if (!stack.isEmpty()) {
                  if (this.itemNbtInspectOverlay == null) {
                     this.itemNbtInspectOverlay = new RiptideItemNbtInspectOverlay(this.font);
                     manager.register(this.itemNbtInspectOverlay);
                  }

                  this.itemNbtInspectOverlay.open(stack, (int)Math.round(mouseX + 8.0), (int)Math.round(mouseY + 8.0));
                  cir.setReturnValue(true);
                  return;
               }
            }

            if (button == 1 && this.hoveredSlot != null) {
               ItemStack captureStack = this.hoveredSlot.getItem();
               String captureItemName = captureStack.isEmpty() ? "" : captureStack.getHoverName().getString();
               String captureRegistryId = captureStack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(captureStack.getItem()).toString();
               RiptideMacroEditorOverlay editor = this.macroEditorOverlay;
               if (editor != null && editor.wantsSlotCapture() && editor.onSlotRightClick(this.hoveredSlot, captureItemName, captureRegistryId)) {
                  cir.setReturnValue(true);
                  return;
               }

               boolean captureSlotNumber = click.hasControlDown() && !click.hasShiftDown();
               ActionEditorOverlay actionEditor = ActionEditorOverlay.getSharedOverlayIfExists();
               if (actionEditor != null
                  && actionEditor.wantsItemSlotCapture()
                  && actionEditor.onInventorySlotCapture(this.hoveredSlot, captureItemName, captureRegistryId, captureSlotNumber)) {
                  cir.setReturnValue(true);
                  return;
               }

               if (!RiptideLiteVariant.enabled()) {
                  RiptideAdminToolsOverlay adminToolsOverlay = RiptideAdminToolsOverlay.getSharedOverlay();
                  if (adminToolsOverlay != null && adminToolsOverlay.wantsItemStackCapture() && adminToolsOverlay.onInventoryItemStackCapture(this.hoveredSlot)
                     )
                   {
                     cir.setReturnValue(true);
                     return;
                  }
               }

               if (RiptideContainerHold.hasPendingCapture()) {
                  RiptidePacketClick.Target captured = this.riptide$buildPacketClickTarget(this.hoveredSlot, captureItemName);
                  if (captured != null && RiptideContainerHold.deliverCapture(captured)) {
                     cir.setReturnValue(true);
                     return;
                  }
               }
            }

            if (this.fabricatorOverlay != null && this.fabricatorOverlay.isVisible() && button == 1 && this.hoveredSlot != null) {
               this.fabricatorOverlay.onSlotClick(this.hoveredSlot, button);
               cir.setReturnValue(true);
            }
         }
      }
   }

   @Unique
   private RiptidePacketClick.Target riptide$buildPacketClickTarget(Slot slot, String itemName) {
      if (MC != null && MC.player != null && slot != null) {
         AbstractContainerMenu handler = MC.player.containerMenu;
         if (handler == null) {
            return null;
         } else {
            Screen screen = MC.gui.screen();
            String screenTitle = screen != null && screen.getTitle() != null ? screen.getTitle().getString() : "";
            String menuClass = handler.getClass().getName();
            int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(MC, slot.index);
            return new RiptidePacketClick.Target(
               handler.containerId,
               handler.getStateId(),
               slot.index,
               visibleSlot,
               screenTitle,
               menuClass,
               itemName == null ? "" : itemName,
               RiptidePacketClick.Mode.RIGHT_CLICK,
               System.currentTimeMillis()
            );
         }
      } else {
         return null;
      }
   }

   @Inject(
      method = {"keyPressed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yang$keyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
      if (this.isRiptideActive()) {
         boolean inventoryKey = MC != null && MC.options != null && MC.options.keyInventory.matches(input);
         if (inventoryKey) {
            if (RiptideSharedState.get().consumeCaptureCancelCallback()) {
               cir.setReturnValue(true);
               return;
            }

            ActionEditorOverlay actionEditor = ActionEditorOverlay.getSharedOverlayIfExists();
            if (actionEditor != null && actionEditor.cancelCaptureIfActive()) {
               cir.setReturnValue(true);
               return;
            }
         }

         if (RiptideOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) {
            cir.setReturnValue(true);
         } else if (InventoryTweaksModule.handleSortKey(input.key(), this.hoveredSlot)) {
            cir.setReturnValue(true);
         } else {
            if (input.key() == 256) {
               if (RiptideSharedState.get().consumeCaptureCancelCallback()) {
                  cir.setReturnValue(true);
                  return;
               }

               ActionEditorOverlay actionEditor = ActionEditorOverlay.getSharedOverlayIfExists();
               if (actionEditor != null && actionEditor.cancelCaptureIfActive()) {
                  cir.setReturnValue(true);
               }
            }

            if (RiptideInventoryMoveHelper.handleKeyEvent(input, true)) {
               cir.setReturnValue(true);
            }
         }
      }
   }

   @Inject(
      method = {"keyReleased"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void yang$keyReleased(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
      if (this.isRiptideActive()) {
         if (RiptideInventoryMoveHelper.handleKeyEvent(input, false)) {
            cir.setReturnValue(true);
         }
      }
   }

   @Inject(
      method = {"mouseReleased"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yang$mouseReleased(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
      if (this.isRiptideActive()) {
         this.inventoryTweaksLastShiftDragSlot = -1;
         if (RiptideOverlayManager.get().handleMouseReleased(click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
         }
      }
   }

   @Inject(
      method = {"slotClicked"},
      at = {@At("HEAD")},
      require = 0
   )
   private void riptide$captureCursorClickOriginBefore(Slot slot, int slotId, int button, ContainerInput actionType, CallbackInfo ci) {
      this.riptide$cursorClickBeforeCarried = ItemStack.EMPTY;
      this.riptide$cursorClickBeforeSlot = ItemStack.EMPTY;
      this.riptide$cursorClickBeforeSlotId = slotId;
      this.riptide$cursorClickBeforeButton = button;
      this.riptide$cursorClickBeforeInput = actionType;
      if (MC.player != null && MC.player.containerMenu != null) {
         AbstractContainerMenu handler = MC.player.containerMenu;
         this.riptide$cursorClickBeforeCarried = handler.getCarried().copy();
         if (slotId >= 0 && slotId < handler.slots.size()) {
            this.riptide$cursorClickBeforeSlot = ((Slot)handler.slots.get(slotId)).getItem().copy();
         }
      }
   }

   @Inject(
      method = {"slotClicked"},
      at = {@At("TAIL")},
      require = 0
   )
   private void riptide$captureCursorClickOriginAfter(Slot slot, int slotId, int button, ContainerInput actionType, CallbackInfo ci) {
      if (MC.player != null && MC.player.containerMenu != null) {
         if (slotId == this.riptide$cursorClickBeforeSlotId
            && button == this.riptide$cursorClickBeforeButton
            && actionType == this.riptide$cursorClickBeforeInput) {
            RiptideCursorClickHelper.recordAfterContainerClick(
               MC, MC.player.containerMenu, slotId, button, actionType, this.riptide$cursorClickBeforeCarried, this.riptide$cursorClickBeforeSlot
            );
         }
      }
   }

   @Inject(
      method = {"mouseDragged"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yang$mouseDragged(MouseButtonEvent click, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
      if (this.isRiptideActive()) {
         if (RiptideOverlayManager.get().handleMouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY)) {
            cir.setReturnValue(true);
         } else {
            if (click.button() == 0
               && click.hasShiftDown()
               && this.hoveredSlot != null
               && !this.hoveredSlot.getItem().isEmpty()
               && InventoryTweaksModule.shouldShiftDragMove()
               && this.inventoryTweaksLastShiftDragSlot != this.hoveredSlot.index) {
               this.inventoryTweaksLastShiftDragSlot = this.hoveredSlot.index;
               this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, 0, ContainerInput.QUICK_MOVE);
               cir.setReturnValue(true);
            }
         }
      }
   }

   @Inject(
      method = {"mouseScrolled"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yang$mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
      if (this.isRiptideActive()) {
         if (RiptideOverlayManager.get().handleMouseScrolled(mouseX, mouseY, verticalAmount)) {
            cir.setReturnValue(true);
         }
      }
   }

   public boolean charTyped(CharacterEvent input) {
      if (!this.isRiptideActive()) {
         return super.charTyped(input);
      } else {
         return RiptideOverlayManager.get().handleCharTyped((char)input.codepoint(), 0) ? true : super.charTyped(input);
      }
   }
}
