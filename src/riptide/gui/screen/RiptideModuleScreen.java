package riptide.gui.screen;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import riptide.api.module.Setting;
import riptide.gui.macro.editor.ActionEditorOverlay;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.module.VanillaModuleMenuController;
import riptide.modules.Module;
import riptide.modules.PackHideState;
import riptide.modules.RiptideModule;
import riptide.util.IRiptideOverlay;
import riptide.util.PacketListCodec;
import riptide.util.RiptideAdminToolsOverlay;
import riptide.util.RiptideConfig;
import riptide.util.RiptideCustomFilterOverlay;
import riptide.util.RiptideCustomFilterPresetOverlay;
import riptide.util.RiptideFabricatorOverlay;
import riptide.util.RiptideKeybindOverlay;
import riptide.util.RiptideLANSyncOverlay;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroEditorOverlay;
import riptide.util.RiptideMacroListOverlay;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideOverlayManager;
import riptide.util.RiptidePacketLoggerOverlay;
import riptide.util.RiptidePacketSelectorOverlay;
import riptide.util.RiptideQueueEditorOverlay;
import riptide.util.RiptideServerInfoOverlay;
import riptide.util.RiptideSharedState;
import riptide.util.RiptideUiScale;
import riptide.util.macro.ToggleModuleAction;

public class RiptideModuleScreen extends RiptideScreen {
   private static final Set<String> TEMPORARILY_HIDDEN_UTILITY_OVERLAYS = new LinkedHashSet<>();
   private final Screen parent;
   private final RiptideModuleScreen.Mode mode;
   private VanillaModuleMenuController menu;
   private RiptidePacketSelectorOverlay packetSelectorOverlay;
   private RiptideMacroListOverlay utilityMacroListOverlay;
   private RiptideFabricatorOverlay utilityFabricatorOverlay;
   private RiptideLANSyncOverlay utilityLanSyncOverlay;
   private RiptideQueueEditorOverlay utilityQueueEditorOverlay;
   private RiptideCustomFilterOverlay utilityCustomFilterOverlay;
   private RiptideKeybindOverlay utilityKeybindOverlay;
   private RiptideAdminToolsOverlay utilityAdminToolsOverlay;
   private String returnSettingsModuleId;
   private final Set<String> titleSetupHiddenOverlayIds = new LinkedHashSet<>();
   private boolean menuCloseCleanupDone;

   public RiptideModuleScreen(Screen var1) {
      this(var1, RiptideModuleScreen.Mode.IN_GAME);
   }

   public RiptideModuleScreen(Screen var1, RiptideModuleScreen.Mode var2) {
      super(Component.literal("Riptide Modules"));
      this.parent = var1;
      this.mode = var2 == null ? RiptideModuleScreen.Mode.IN_GAME : var2;
   }

   public boolean isTitleSetup() {
      return this.mode == RiptideModuleScreen.Mode.TITLE_SETUP;
   }

   protected void init() {
      this.menuCloseCleanupDone = false;
      this.syncUtilityOverlays();
      if (this.isTitleSetup()) {
         this.hideRuntimeOverlaysForTitleSetup();
      } else {
         this.restoreTemporarilyHiddenUtilityOverlays();
      }

      if (this.menu == null) {
         this.menu = new VanillaModuleMenuController(new RiptideModuleScreen.ModuleMenuHost());
      }

      this.menu.init();
      if (this.returnSettingsModuleId != null && !this.returnSettingsModuleId.isBlank()) {
         this.menu.openSettingsByModuleId(this.returnSettingsModuleId);
         this.returnSettingsModuleId = null;
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   public boolean blocksGlobalKeybinds() {
      return this.menu != null && this.menu.blocksGlobalKeybinds() || this.packetSelectorOverlay != null && this.packetSelectorOverlay.isVisible();
   }

   public void extractRenderState(GuiGraphicsExtractor var1, int var2, int var3, float var4) {
      if (this.menu == null) {
         this.menu = new VanillaModuleMenuController(new RiptideModuleScreen.ModuleMenuHost());
         this.menu.init();
      }

      int var5 = RiptideUiScale.toVirtualInt(var2);
      int var6 = RiptideUiScale.toVirtualInt(var3);
      boolean var7 = this.packetSelectorOverlay != null && this.packetSelectorOverlay.isVisible();
      this.syncUtilityOverlaysForTopLayer(var7 || this.menu.hasTopLayer());
      boolean var8 = !var7 && !this.menu.hasTopLayer() && RiptideOverlayManager.get().shouldBlockUnderlyingHover(var2, var3);
      int var9 = var8 ? -10000 : var5;
      int var10 = var8 ? -10000 : var6;
      RiptideUiScale.pushOverlayScale(var1);

      try {
         if (var7) {
            UiRenderer.rect(var1, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), -1442511609);
            this.packetSelectorOverlay.render(var1, var5, var6, var4);
         } else {
            this.menu.render(var1, var9, var10, var4, this.screenWidth(), this.screenHeight());
         }
      } finally {
         RiptideUiScale.popOverlayScale(var1);
      }

      if (!PackHideState.isActive() && !var7 && !this.menu.hasSelectedModule() && !this.menu.hasTopLayer()) {
         RiptideOverlayManager.get().renderAll(var1, var2, var3, var4);
      }
   }

   public boolean mouseClicked(MouseButtonEvent var1, boolean var2) {
      int var3 = RiptideUiScale.toVirtualInt(var1.x());
      int var4 = RiptideUiScale.toVirtualInt(var1.y());
      if (this.packetSelectorOverlay != null && this.packetSelectorOverlay.isVisible()) {
         return this.packetSelectorOverlay.mouseClicked(var3, var4, var1.button());
      } else {
         return this.menu != null && !this.menu.hasTopLayer() && RiptideOverlayManager.get().handleMouseClicked(var1.x(), var1.y(), var1.button())
            ? true
            : this.menu == null || this.menu.mouseClicked(var3, var4, var1.button());
      }
   }

   public boolean mouseReleased(MouseButtonEvent var1) {
      int var2 = RiptideUiScale.toVirtualInt(var1.x());
      int var3 = RiptideUiScale.toVirtualInt(var1.y());
      if (this.packetSelectorOverlay != null && this.packetSelectorOverlay.isVisible() && this.packetSelectorOverlay.mouseReleased(var2, var3, var1.button())) {
         return true;
      } else {
         return this.menu != null && !this.menu.hasTopLayer() && RiptideOverlayManager.get().handleMouseReleased(var1.x(), var1.y(), var1.button())
            ? true
            : this.menu == null || this.menu.mouseReleased(var2, var3, var1.button());
      }
   }

   public boolean mouseDragged(MouseButtonEvent var1, double var2, double var4) {
      int var6 = RiptideUiScale.toVirtualInt(var1.x());
      int var7 = RiptideUiScale.toVirtualInt(var1.y());
      if (this.packetSelectorOverlay != null
         && this.packetSelectorOverlay.isVisible()
         && this.packetSelectorOverlay.mouseDragged(var6, var7, var1.button(), var2, var4)) {
         return true;
      } else {
         return this.menu != null && !this.menu.hasTopLayer() && RiptideOverlayManager.get().handleMouseDragged(var1.x(), var1.y(), var1.button(), var2, var4)
            ? true
            : this.menu == null || this.menu.mouseDragged(var6, var7, var1.button(), var2, var4);
      }
   }

   public boolean mouseScrolled(double var1, double var3, double var5, double var7) {
      int var9 = RiptideUiScale.toVirtualInt(var1);
      int var10 = RiptideUiScale.toVirtualInt(var3);
      if (this.packetSelectorOverlay != null && this.packetSelectorOverlay.isVisible()) {
         return this.packetSelectorOverlay.mouseScrolled(var9, var10, var7);
      } else {
         return this.menu != null && !this.menu.hasTopLayer() && RiptideOverlayManager.get().handleMouseScrolled(var1, var3, var7)
            ? true
            : this.menu == null || this.menu.mouseScrolled(var9, var10, var7);
      }
   }

   public boolean keyPressed(KeyEvent var1) {
      if (this.packetSelectorOverlay != null
         && this.packetSelectorOverlay.isVisible()
         && this.packetSelectorOverlay.keyPressed(var1.key(), var1.scancode(), var1.modifiers())) {
         return true;
      } else if (this.menu != null && !this.menu.hasTopLayer() && RiptideOverlayManager.get().handleKeyPressed(var1.key(), var1.scancode(), var1.modifiers())) {
         return true;
      } else if (this.menu != null && this.menu.keyPressed(var1.key(), var1.scancode(), var1.modifiers())) {
         return true;
      } else {
         return this.passMovementKey(var1, true) ? false : super.keyPressed(var1);
      }
   }

   public boolean keyReleased(KeyEvent var1) {
      return this.passMovementKey(var1, false) ? false : super.keyReleased(var1);
   }

   public boolean charTyped(CharacterEvent var1) {
      char var2 = (char)var1.codepoint();
      if (this.packetSelectorOverlay != null && this.packetSelectorOverlay.isVisible() && this.packetSelectorOverlay.charTyped(var2, 0)) {
         return true;
      } else if (this.menu != null && !this.menu.hasTopLayer() && RiptideOverlayManager.get().handleCharTyped(var2, 0)) {
         return true;
      } else {
         return this.menu != null && this.menu.charTyped(var2) ? true : super.charTyped(var1);
      }
   }

   public void onClose() {
      this.runMenuCloseCleanup();
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   public void removed() {
      this.runMenuCloseCleanup();
   }

   private void runMenuCloseCleanup() {
      if (!this.menuCloseCleanupDone) {
         this.menuCloseCleanupDone = true;
         RiptideOverlayManager.get().restoreClampedAwayBounds();
         this.saveUtilityOverlayStates();
         this.hideUtilityOverlaysForMenuClose();
         this.restoreRuntimeOverlaysHiddenForTitleSetup();
         this.resetConfigurationOnlyOverlays();
      }
   }

   private void openPacketSelector(Module var1, Setting<?, ?> var2) {
      if (var1 != null && var2 != null) {
         if (this.packetSelectorOverlay == null) {
            this.packetSelectorOverlay = new RiptidePacketSelectorOverlay(this.font);
         }

         boolean var3 = PacketListCodec.isC2SOption(var2.id());
         Set var4 = PacketListCodec.resolvePackets(var1.value(var2.id()), var3);
         if (var3) {
            this.packetSelectorOverlay.openToggleC2S((var3x, var4x) -> this.setPacketSelected(var1, var2, true, var3x, var4x), var4);
         } else {
            this.packetSelectorOverlay.openToggleS2C((var3x, var4x) -> this.setPacketSelected(var1, var2, false, var3x, var4x), var4);
         }
      }
   }

   private void setPacketSelected(Module var1, Setting<?, ?> var2, boolean var3, Class<? extends Packet<?>> var4, boolean var5) {
      LinkedHashSet var6 = new LinkedHashSet<>(PacketListCodec.resolvePackets(var1.value(var2.id()), var3));
      if (var5) {
         var6.add(var4);
      } else {
         var6.remove(var4);
      }

      String var7 = PacketListCodec.encodePackets(var6);
      if (this.isTitleSetup()) {
         var1.setConfiguredValue(var2.id(), var7);
      } else {
         var1.setValue(var2.id(), var7);
      }
   }

   private void syncUtilityOverlays() {
      RiptideOverlayManager var1 = RiptideOverlayManager.get();
      this.utilityMacroListOverlay = this.findRegisteredOverlay(RiptideMacroListOverlay.class, null);
      if (this.utilityMacroListOverlay == null) {
         this.utilityMacroListOverlay = new RiptideMacroListOverlay(this.font);
         this.utilityMacroListOverlay.restoreState();
      }

      var1.register(this.utilityMacroListOverlay);
      RiptideMacroEditorOverlay var2 = RiptideMacroEditorOverlay.getSharedOverlay();
      this.utilityMacroListOverlay.setConfigurationOnly(this.isTitleSetup());
      if (var2 != null) {
         var2.setConfigurationOnly(this.isTitleSetup());
      }

      if (this.isTitleSetup()) {
         this.utilityLanSyncOverlay = RiptideLANSyncOverlay.getSharedOverlay(this.font);
         this.utilityLanSyncOverlay.restoreState();
         this.utilityLanSyncOverlay.setConfigurationOnly(true);
         var1.register(this.utilityLanSyncOverlay);
         this.utilityQueueEditorOverlay = this.findRegisteredOverlay(RiptideQueueEditorOverlay.class, null);
         if (this.utilityQueueEditorOverlay == null) {
            this.utilityQueueEditorOverlay = new RiptideQueueEditorOverlay(this.font);
            this.utilityQueueEditorOverlay.restoreState();
         }

         this.utilityQueueEditorOverlay.setConfigurationOnly(true);
         var1.register(this.utilityQueueEditorOverlay);
         this.utilityCustomFilterOverlay = this.findRegisteredOverlay(RiptideCustomFilterOverlay.class, null);
         if (this.utilityCustomFilterOverlay == null) {
            this.utilityCustomFilterOverlay = new RiptideCustomFilterOverlay(this.font);
            this.utilityCustomFilterOverlay.restoreLayout();
         }

         var1.register(this.utilityCustomFilterOverlay);
         if (this.utilityCustomFilterOverlay.getPresetManagerOverlay() != null) {
            if (this.findRegisteredOverlay(RiptideCustomFilterPresetOverlay.class, null) == null) {
               this.utilityCustomFilterOverlay.getPresetManagerOverlay().restoreLayout();
            }

            var1.register(this.utilityCustomFilterOverlay.getPresetManagerOverlay());
         }

         this.utilityKeybindOverlay = this.findRegisteredOverlay(RiptideKeybindOverlay.class, null);
         if (this.utilityKeybindOverlay == null) {
            this.utilityKeybindOverlay = new RiptideKeybindOverlay();
            this.utilityKeybindOverlay.restoreLayout();
         }

         var1.register(this.utilityKeybindOverlay);
         var1.setTemporarilyHidden(this.utilityMacroListOverlay, false);
         var1.setTemporarilyHidden(this.utilityKeybindOverlay, false);
         if (var2 != null) {
            if (this.findRegisteredOverlay(RiptideMacroEditorOverlay.class, null) == null) {
               var2.restoreState();
            }

            var1.register(var2);
            var1.setTemporarilyHidden(var2, false);
         }

         ActionEditorOverlay var3 = ActionEditorOverlay.getSharedOverlay();
         var3.setConfigurationOnly(true);
         var1.register(var3);
         var1.setTemporarilyHidden(var3, false);
         RiptideModule var4 = RiptideModule.get();
         if (var4 != null) {
            RiptidePacketLoggerOverlay var5 = var4.getPacketLoggerOverlay();
            if (var5 != null) {
               var5.restoreState();
               var5.setConfigurationOnly(true);
               var1.register(var5);
            }

            RiptideServerInfoOverlay var6 = var4.getServerDataOverlay();
            if (var6 != null) {
               var6.restoreState();
               var6.setConfigurationOnly(true);
               var1.register(var6);
            }
         }
      } else {
         if (this.parent instanceof AbstractContainerScreen var7) {
            this.utilityFabricatorOverlay = this.findRegisteredOverlay(RiptideFabricatorOverlay.class, null);
            if (this.utilityFabricatorOverlay == null) {
               this.utilityFabricatorOverlay = RiptideFabricatorOverlay.getSharedOverlay(var7);
               this.utilityFabricatorOverlay.restoreState();
            }

            var1.register(this.utilityFabricatorOverlay);
         }

         this.utilityLanSyncOverlay = this.findRegisteredOverlay(RiptideLANSyncOverlay.class, null);
         if (this.utilityLanSyncOverlay == null) {
            this.utilityLanSyncOverlay = RiptideLANSyncOverlay.getSharedOverlay(this.font);
            this.utilityLanSyncOverlay.restoreState();
         }

         this.utilityLanSyncOverlay.setConfigurationOnly(false);
         var1.register(this.utilityLanSyncOverlay);
         this.utilityQueueEditorOverlay = this.findRegisteredOverlay(RiptideQueueEditorOverlay.class, null);
         if (this.utilityQueueEditorOverlay == null) {
            this.utilityQueueEditorOverlay = new RiptideQueueEditorOverlay(this.font);
            this.utilityQueueEditorOverlay.restoreState();
         }

         this.utilityQueueEditorOverlay.setConfigurationOnly(false);
         var1.register(this.utilityQueueEditorOverlay);
         this.utilityCustomFilterOverlay = this.findRegisteredOverlay(RiptideCustomFilterOverlay.class, null);
         if (this.utilityCustomFilterOverlay == null) {
            this.utilityCustomFilterOverlay = new RiptideCustomFilterOverlay(this.font);
            this.utilityCustomFilterOverlay.restoreLayout();
         }

         var1.register(this.utilityCustomFilterOverlay);
         if (this.utilityCustomFilterOverlay.getPresetManagerOverlay() != null) {
            if (this.findRegisteredOverlay(RiptideCustomFilterPresetOverlay.class, null) == null) {
               this.utilityCustomFilterOverlay.getPresetManagerOverlay().restoreLayout();
            }

            var1.register(this.utilityCustomFilterOverlay.getPresetManagerOverlay());
         }

         this.utilityKeybindOverlay = this.findRegisteredOverlay(RiptideKeybindOverlay.class, null);
         if (this.utilityKeybindOverlay == null) {
            this.utilityKeybindOverlay = new RiptideKeybindOverlay();
            this.utilityKeybindOverlay.restoreLayout();
         }

         var1.register(this.utilityKeybindOverlay);
         this.utilityAdminToolsOverlay = this.findRegisteredOverlay(RiptideAdminToolsOverlay.class, null);
         if (this.utilityAdminToolsOverlay == null) {
            this.utilityAdminToolsOverlay = RiptideAdminToolsOverlay.getSharedOverlay();
            this.utilityAdminToolsOverlay.restoreLayout();
         }

         var1.register(this.utilityAdminToolsOverlay);
         if (var2 != null) {
            if (this.findRegisteredOverlay(RiptideMacroEditorOverlay.class, null) == null) {
               var2.restoreState();
            }

            var1.register(var2);
         }

         ActionEditorOverlay var8 = ActionEditorOverlay.getSharedOverlay();
         var8.setConfigurationOnly(false);
         var1.register(var8);
         RiptideModule var10 = RiptideModule.get();
         if (var10 != null) {
            RiptidePacketLoggerOverlay var11 = var10.getPacketLoggerOverlay();
            if (var11 != null) {
               var11.restoreState();
               var11.setConfigurationOnly(false);
               var1.register(var11);
            }

            RiptideServerInfoOverlay var12 = var10.getServerDataOverlay();
            if (var12 != null) {
               var12.restoreState();
               var12.setConfigurationOnly(false);
               var1.register(var12);
            }
         }
      }
   }

   private void syncUtilityOverlaysForTopLayer(boolean var1) {
      if (var1) {
         this.hideUtilityOverlaysForMenuClose();
      } else {
         this.restoreTemporarilyHiddenUtilityOverlays();
      }
   }

   private void hideUtilityOverlaysForMenuClose() {
      TEMPORARILY_HIDDEN_UTILITY_OVERLAYS.clear();
      this.setOverlayHiddenForMenuClose(this.utilityMacroListOverlay);
      this.setOverlayHiddenForMenuClose(this.utilityFabricatorOverlay);
      this.setOverlayHiddenForMenuClose(this.utilityLanSyncOverlay);
      this.setOverlayHiddenForMenuClose(this.utilityQueueEditorOverlay);
      this.setOverlayHiddenForMenuClose(this.utilityCustomFilterOverlay);
      this.setOverlayHiddenForMenuClose(this.utilityKeybindOverlay);
      this.setOverlayHiddenForMenuClose(this.utilityAdminToolsOverlay);
      RiptideModule var1 = RiptideModule.get();
      if (var1 != null) {
         this.setOverlayHiddenForMenuClose(var1.getPacketLoggerOverlayIfExists());
         this.setOverlayHiddenForMenuClose(var1.getServerDataOverlayIfExists());
         this.setOverlayHiddenForMenuClose(var1.getMultiOverlayIfExists());
      }

      this.setOverlayHiddenForMenuClose(RiptideMacroEditorOverlay.getSharedOverlay());
      this.setOverlayHiddenForMenuClose(ActionEditorOverlay.getSharedOverlay());
      if (this.utilityCustomFilterOverlay != null) {
         this.setOverlayHiddenForMenuClose(this.utilityCustomFilterOverlay.getPresetManagerOverlay());
      }
   }

   private void setOverlayHiddenForMenuClose(IRiptideOverlay var1) {
      if (var1 != null && var1.isVisible()) {
         TEMPORARILY_HIDDEN_UTILITY_OVERLAYS.add(var1.getOverlayId());
         RiptideOverlayManager.get().setTemporarilyHidden(var1, true);
      }
   }

   private void restoreTemporarilyHiddenUtilityOverlays() {
      if (!TEMPORARILY_HIDDEN_UTILITY_OVERLAYS.isEmpty()) {
         LinkedHashSet var1 = new LinkedHashSet<>(TEMPORARILY_HIDDEN_UTILITY_OVERLAYS);
         TEMPORARILY_HIDDEN_UTILITY_OVERLAYS.clear();

         for (IRiptideOverlay var3 : RiptideOverlayManager.get().getOverlays()) {
            if (var3 != null && var1.contains(var3.getOverlayId())) {
               RiptideOverlayManager.get().setTemporarilyHidden(var3, false);
            }
         }
      }
   }

   private void hideRuntimeOverlaysForTitleSetup() {
      RiptideOverlayManager var1 = RiptideOverlayManager.get();
      this.titleSetupHiddenOverlayIds.clear();

      for (IRiptideOverlay var3 : var1.getOverlays()) {
         if (var3 != null && var3.isVisible() && !this.isTitleSetupOverlay(var3)) {
            this.titleSetupHiddenOverlayIds.add(var3.getOverlayId());
            var1.setTemporarilyHidden(var3, true);
         }
      }
   }

   private boolean isTitleSetupOverlay(IRiptideOverlay var1) {
      return var1 == this.utilityMacroListOverlay
         || var1 == this.utilityLanSyncOverlay
         || var1 == this.utilityQueueEditorOverlay
         || var1 == this.utilityCustomFilterOverlay
         || this.utilityCustomFilterOverlay != null && var1 == this.utilityCustomFilterOverlay.getPresetManagerOverlay()
         || var1 == this.utilityKeybindOverlay
         || RiptideModule.get() != null && var1 == RiptideModule.get().getPacketLoggerOverlayIfExists()
         || RiptideModule.get() != null && var1 == RiptideModule.get().getServerDataOverlayIfExists()
         || var1 == RiptideMacroEditorOverlay.getSharedOverlay()
         || var1 == ActionEditorOverlay.getSharedOverlay();
   }

   private void restoreRuntimeOverlaysHiddenForTitleSetup() {
      if (!this.titleSetupHiddenOverlayIds.isEmpty()) {
         RiptideOverlayManager var1 = RiptideOverlayManager.get();

         for (IRiptideOverlay var3 : var1.getOverlays()) {
            if (var3 != null && this.titleSetupHiddenOverlayIds.contains(var3.getOverlayId())) {
               var1.setTemporarilyHidden(var3, false);
            }
         }

         this.titleSetupHiddenOverlayIds.clear();
      }
   }

   private <T extends IRiptideOverlay> T findRegisteredOverlay(Class<T> var1, String var2) {
      for (IRiptideOverlay var4 : RiptideOverlayManager.get().getOverlays()) {
         if (var4 != null && var1.isInstance(var4) && (var2 == null || var2.equals(var4.getOverlayId()))) {
            return (T)var1.cast(var4);
         }
      }

      return null;
   }

   private void runUtility(String var1) {
      if ("macros".equals(var1)) {
         this.toggleMacroPanel();
      } else if ("keys".equals(var1)) {
         this.toggleOverlay(this.utilityKeybindOverlay);
      } else if (this.isTitleSetup()) {
         switch (var1) {
            case "lan":
               this.toggleOverlay(this.utilityLanSyncOverlay);
               break;
            case "queue":
               this.toggleOverlay(this.utilityQueueEditorOverlay);
               break;
            case "packets":
               this.toggleOverlay(this.utilityCustomFilterOverlay);
               break;
            case "logger":
               RiptideModule var4 = RiptideModule.get();
               if (var4 != null) {
                  this.toggleOverlay(var4.getPacketLoggerOverlay());
               }
               break;
            case "server":
               RiptideModule var5 = RiptideModule.get();
               if (var5 != null) {
                  this.toggleOverlay(var5.getServerDataOverlay());
               }
               break;
            case "multi":
               if (this.minecraft != null) {
                  RiptideMultiDisclaimerScreen.open(this.minecraft, this, () -> this.minecraft.gui.setScreen(new RiptideMultiScreen(this, "")));
               }
         }
      } else {
         RiptideModule var2 = RiptideModule.get();
         if (var2 != null) {
            switch (var1) {
               case "admin":
                  this.toggleOverlay(this.utilityAdminToolsOverlay);
                  break;
               case "lan":
                  this.toggleOverlay(this.utilityLanSyncOverlay);
                  break;
               case "queue":
                  this.toggleOverlay(this.utilityQueueEditorOverlay);
                  break;
               case "logger":
                  RiptidePacketLoggerOverlay var10 = var2.getPacketLoggerOverlay();
                  if (var10 != null) {
                     var10.restoreLayout();
                     RiptideOverlayManager.get().register(var10);
                     this.toggleOverlay(var10);
                  }
                  break;
               case "packets":
                  this.toggleOverlay(this.utilityCustomFilterOverlay);
                  break;
               case "server":
                  RiptideServerInfoOverlay var6 = var2.getServerDataOverlay();
                  if (var6 != null) {
                     RiptideOverlayManager.get().register(var6);
                     this.toggleOverlay(var6);
                  }
                  break;
               case "multi":
                  var2.toggleMultiUiBehavior();
                  break;
               case "send":
                  boolean var13 = !RiptideSharedState.get().shouldSendGuiPackets();
                  var2.applySendGuiPacketsUiBehavior(var13);
                  RiptideNotifications.show("Send Packets " + (var13 ? "on" : "off"), var13 ? -13248397 : -50373);
                  break;
               case "delay":
                  boolean var12 = !RiptideSharedState.get().shouldDelayGuiPackets();
                  int var8 = var2.applyDelayGuiPacketsUiBehavior(var12);
                  var2.notifyDelayPacketsUiResult(var12, var8);
                  break;
               case "flush":
                  int var11 = var2.flushQueuedPacketsUiBehavior();
                  var2.notifyFlushQueuedPacketsUiResult(var11);
                  break;
               case "clear":
                  int var7 = var2.clearQueuedPacketsUiBehavior();
                  var2.notifyClearQueuedPacketsUiResult(var7);
            }
         }
      }
   }

   private void toggleMacroPanel() {
      RiptideMacroEditorOverlay var1 = RiptideMacroEditorOverlay.getSharedOverlay();
      if (var1 != null) {
         RiptideOverlayManager.get().register(var1);
      }

      if (var1 != null && var1.isVisible()) {
         if (this.utilityMacroListOverlay != null) {
            this.utilityMacroListOverlay.setVisible(false);
         }

         RiptideOverlayManager.get().bringToFront(var1);
      } else {
         this.toggleOverlay(this.utilityMacroListOverlay);
      }
   }

   private void toggleOverlay(IRiptideOverlay var1) {
      if (var1 != null) {
         RiptideOverlayManager.get().register(var1);
         var1.setVisible(!var1.isVisible());
         if (var1.isVisible()) {
            RiptideOverlayManager.get().bringToFront(var1);
         }
      }
   }

   private RiptideFabricatorOverlay utilityFabricatorOverlay() {
      if (this.parent instanceof AbstractContainerScreen var1) {
         this.utilityFabricatorOverlay = RiptideFabricatorOverlay.getSharedOverlay(var1);
         this.utilityFabricatorOverlay.restoreState();
         RiptideOverlayManager.get().register(this.utilityFabricatorOverlay);
         return this.utilityFabricatorOverlay;
      } else {
         return null;
      }
   }

   private void addQuickToggleMacroStep(Module var1) {
      if (var1 != null) {
         RiptideMacroEditorOverlay var2 = RiptideMacroEditorOverlay.getSharedOverlay();
         if (var2 != null) {
            var2.setConfigurationOnly(this.isTitleSetup());
            RiptideOverlayManager var3 = RiptideOverlayManager.get();
            var3.register(var2);
            var3.setTemporarilyHidden(var2, false);
            if (var2.isVisible() && RiptideSharedState.get().getEditingMacro() != null) {
               var2.setVisible(true);
               var3.bringToFront(var2);
            } else {
               var2.open(null, true);
            }

            if (this.utilityMacroListOverlay != null) {
               this.utilityMacroListOverlay.setVisible(false);
            }

            var2.addAction(new ToggleModuleAction(var1.name()));
         }
      }
   }

   private void resetConfigurationOnlyOverlays() {
      if (this.utilityMacroListOverlay != null) {
         this.utilityMacroListOverlay.setConfigurationOnly(false);
      }

      if (this.utilityLanSyncOverlay != null) {
         this.utilityLanSyncOverlay.setConfigurationOnly(false);
      }

      if (this.utilityQueueEditorOverlay != null) {
         this.utilityQueueEditorOverlay.setConfigurationOnly(false);
      }

      ActionEditorOverlay var1 = ActionEditorOverlay.getSharedOverlayIfExists();
      if (var1 != null) {
         var1.setConfigurationOnly(false);
      }

      RiptideModule var2 = RiptideModule.get();
      if (var2 != null) {
         RiptidePacketLoggerOverlay var3 = var2.getPacketLoggerOverlayIfExists();
         if (var3 != null) {
            var3.setConfigurationOnly(false);
         }

         RiptideServerInfoOverlay var4 = var2.getServerDataOverlayIfExists();
         if (var4 != null) {
            var4.setConfigurationOnly(false);
         }
      }

      RiptideMacroEditorOverlay var5 = RiptideMacroEditorOverlay.getSharedOverlay();
      if (var5 != null) {
         var5.setConfigurationOnly(false);
      }
   }

   private void saveUtilityOverlayStates() {
      this.saveOverlayState(this.utilityMacroListOverlay);
      this.saveOverlayState(this.utilityFabricatorOverlay);
      this.saveOverlayState(this.utilityLanSyncOverlay);
      this.saveOverlayState(this.utilityQueueEditorOverlay);
      this.saveOverlayState(this.utilityCustomFilterOverlay);
      this.saveOverlayState(this.utilityKeybindOverlay);
      this.saveOverlayState(this.utilityAdminToolsOverlay);
      RiptideModule var1 = RiptideModule.get();
      if (var1 != null) {
         this.saveOverlayState(var1.getPacketLoggerOverlayIfExists());
         this.saveOverlayState(var1.getServerDataOverlayIfExists());
      }

      this.saveOverlayState(RiptideMacroEditorOverlay.getSharedOverlay());
      this.saveOverlayState(ActionEditorOverlay.getSharedOverlay());
      if (this.utilityCustomFilterOverlay != null) {
         this.saveOverlayState(this.utilityCustomFilterOverlay.getPresetManagerOverlay());
      }
   }

   private void saveOverlayState(IRiptideOverlay var1) {
      if (var1 != null) {
         if (var1 instanceof RiptideMacroListOverlay var2) {
            var2.saveState();
         } else if (var1 instanceof RiptideFabricatorOverlay var3) {
            var3.saveState();
         } else if (var1 instanceof RiptideLANSyncOverlay var4) {
            var4.saveState();
         } else if (var1 instanceof RiptideQueueEditorOverlay var5) {
            var5.saveState();
         } else if (var1 instanceof RiptideCustomFilterOverlay var6) {
            var6.saveLayout();
         } else if (var1 instanceof RiptideKeybindOverlay var7) {
            var7.saveLayout();
         } else if (var1 instanceof RiptidePacketLoggerOverlay var8) {
            var8.saveState();
         } else if (var1 instanceof RiptideServerInfoOverlay var9) {
            var9.saveState();
         } else if (var1 instanceof RiptideMacroEditorOverlay var10) {
            var10.saveState();
         } else {
            var1.saveLayout();
         }
      }
   }

   private boolean passMovementKey(KeyEvent var1, boolean var2) {
      if (this.blocksGlobalKeybinds()) {
         return false;
      } else if (this.minecraft != null && this.minecraft.options != null) {
         KeyMapping[] var3 = new KeyMapping[]{
            this.minecraft.options.keyUp,
            this.minecraft.options.keyDown,
            this.minecraft.options.keyLeft,
            this.minecraft.options.keyRight,
            this.minecraft.options.keyJump,
            this.minecraft.options.keyShift,
            this.minecraft.options.keySprint
         };

         for (KeyMapping var7 : var3) {
            if (var7 != null && var7.matches(var1)) {
               var7.setDown(var2);
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static enum Mode {
      IN_GAME,
      TITLE_SETUP;
   }

   private final class ModuleMenuHost implements VanillaModuleMenuController.Host {
      private ModuleMenuHost() {
         Objects.requireNonNull(RiptideModuleScreen.this);
         super();
      }

      @Override
      public boolean offlineSetup() {
         return RiptideModuleScreen.this.isTitleSetup();
      }

      @Override
      public Screen screen() {
         return RiptideModuleScreen.this;
      }

      @Override
      public Font font() {
         return RiptideModuleScreen.this.font;
      }

      @Override
      public void closeMenu() {
         RiptideModuleScreen.this.onClose();
      }

      @Override
      public void saveConfig() {
         RiptideConfig.getGlobal().save();
      }

      @Override
      public void openPacketSelector(Module var1, Setting<?, ?> var2) {
         RiptideModuleScreen.this.openPacketSelector(var1, var2);
      }

      @Override
      public void openStringListEditor(Module var1, Setting<?, ?> var2) {
         if (RiptideModuleScreen.this.minecraft != null) {
            RiptideModuleScreen.this.returnSettingsModuleId = var1 == null ? null : var1.id();
            RiptideModuleScreen.this.minecraft.gui.setScreen(new RiptideStringListSettingScreen(RiptideModuleScreen.this, var1, var2));
         }
      }

      @Override
      public void openRegistryListEditor(Module var1, Setting<?, ?> var2) {
         if (RiptideModuleScreen.this.minecraft != null) {
            RiptideModuleScreen.this.returnSettingsModuleId = var1 == null ? null : var1.id();
            RiptideModuleScreen.this.minecraft.gui.setScreen(new RiptideRegistryListSettingScreen(RiptideModuleScreen.this, var1, var2));
         }
      }

      @Override
      public void openMacroCreator(Module var1, Setting<?, ?> var2) {
         this.openMacroCreator(var1, var2, null);
      }

      @Override
      public void openMacroCreator(Module var1, Setting<?, ?> var2, RiptideMacro var3) {
         RiptideMacroEditorOverlay var4 = RiptideMacroEditorOverlay.getSharedOverlay();
         if (var4 != null) {
            var4.setConfigurationOnly(RiptideModuleScreen.this.isTitleSetup());
            RiptideOverlayManager var5 = RiptideOverlayManager.get();
            var5.register(var4);
            var5.setTemporarilyHidden(var4, false);
            var4.open(var3, true);
            var4.setVisible(true);
            var5.bringToFront(var4);
            if (RiptideModuleScreen.this.utilityMacroListOverlay != null) {
               RiptideModuleScreen.this.utilityMacroListOverlay.setVisible(false);
            }
         }
      }

      @Override
      public void runUtility(String var1) {
         RiptideModuleScreen.this.runUtility(var1);
      }

      @Override
      public void addToggleMacro(Module var1) {
         RiptideModuleScreen.this.addQuickToggleMacroStep(var1);
      }
   }
}
