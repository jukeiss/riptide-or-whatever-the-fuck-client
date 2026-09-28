package riptide.util;

import net.minecraft.client.gui.Font;
import riptide.gui.macro.editor.ActionEditorOverlay;
import riptide.modules.RiptideModule;

public final class RiptideHostScreenOverlays {
   private final RiptideLANSyncOverlay lanSyncOverlay;
   private final RiptideMacroListOverlay macroListOverlay;
   private final RiptideQueueEditorOverlay queueEditorOverlay;
   private final RiptideCustomFilterOverlay customFilterOverlay;
   private final RiptideCustomFilterPresetOverlay customFilterPresetOverlay;
   private final RiptideMacroEditorOverlay macroEditorOverlay;
   private final RiptideKeybindOverlay keybindOverlay;
   private final RiptideLauncherOverlay launcherOverlay;
   private RiptidePacketLoggerOverlay packetLoggerOverlay;
   private RiptideServerInfoOverlay serverInfoOverlay;

   private RiptideHostScreenOverlays(Font font) {
      RiptideLANSync.getInstance().setOnSessionStateChanged(() -> {});
      this.lanSyncOverlay = RiptideLANSyncOverlay.getSharedOverlay(font);
      this.macroListOverlay = new RiptideMacroListOverlay(font);
      this.queueEditorOverlay = new RiptideQueueEditorOverlay(font);
      this.customFilterOverlay = new RiptideCustomFilterOverlay(font);
      this.customFilterPresetOverlay = this.customFilterOverlay.getPresetManagerOverlay();
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
      RiptideItemNbtInspectOverlay itemNbtInspectOverlay = RiptideItemNbtInspectOverlay.getSharedOverlay(font);
      if (itemNbtInspectOverlay != null) {
         manager.register(itemNbtInspectOverlay);
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
         this.macroListOverlay, null, this.lanSyncOverlay, this.queueEditorOverlay, this.packetLoggerOverlay, this.customFilterOverlay
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

      if (!RiptideLiteVariant.enabled() && riptideModule != null) {
         IRiptideOverlay matchmakingOverlay = riptideModule.getMatchmakingOverlay();
         if (matchmakingOverlay != null && matchmakingOverlay.isVisible()) {
            manager.register(matchmakingOverlay);
         }

         IRiptideOverlay profilesOverlay = riptideModule.getProfilesOverlay();
         if (profilesOverlay != null && profilesOverlay.isVisible()) {
            manager.register(profilesOverlay);
         }
      }

      this.launcherOverlay.restoreLayout();
      manager.register(this.launcherOverlay);
   }

   public static RiptideHostScreenOverlays build(Font font) {
      return new RiptideHostScreenOverlays(font);
   }

   public void saveAndClear() {
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

      RiptideOverlayManager.get().clear();
   }
}
