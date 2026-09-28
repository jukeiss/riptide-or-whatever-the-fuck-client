package riptide.mixin;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterExtract;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.Remove;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.gui.screens.dialog.WaitingForResponseScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.gui.macro.editor.ActionEditorOverlay;
import riptide.modules.RiptideModule;
import riptide.util.IRiptideOverlay;
import riptide.util.RiptideCustomFilterOverlay;
import riptide.util.RiptideCustomFilterPresetOverlay;
import riptide.util.RiptideKeybindOverlay;
import riptide.util.RiptideLANSync;
import riptide.util.RiptideLANSyncOverlay;
import riptide.util.RiptideLauncherOverlay;
import riptide.util.RiptideLiteVariant;
import riptide.util.RiptideMacroEditorOverlay;
import riptide.util.RiptideMacroListOverlay;
import riptide.util.RiptideOverlayManager;
import riptide.util.RiptidePacketLoggerOverlay;
import riptide.util.RiptideQueueEditorOverlay;
import riptide.util.RiptideServerInfoOverlay;

@Mixin({DialogScreen.class, WaitingForResponseScreen.class})
public abstract class RiptideDialogScreenMixin extends Screen {
   @Unique
   private RiptideLauncherOverlay launcherOverlay;
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
   private RiptideKeybindOverlay keybindOverlay;
   @Unique
   private RiptideServerInfoOverlay serverInfoOverlay;
   @Unique
   private boolean riptide$overlaysBuilt;

   protected RiptideDialogScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void riptide$init(CallbackInfo ci) {
      Screen screen = this;
      ScreenEvents.afterExtract(screen).register((AfterExtract)(scrn, drawContext, mouseX, mouseY, tickDelta) -> {
         if (this.riptide$isRiptideActive()) {
            try {
               RiptideOverlayManager.get().renderAll(drawContext, mouseX, mouseY, tickDelta);
            } catch (Throwable var7) {
            }
         }
      });
      ScreenEvents.remove(screen).register((Remove)scrn -> {
         Minecraft mc = Minecraft.getInstance();
         boolean stillOnDialog = mc != null && (mc.gui.screen() instanceof DialogScreen || mc.gui.screen() instanceof WaitingForResponseScreen);
         if (!stillOnDialog) {
            this.riptide$overlaysBuilt = false;
            this.riptide$saveOverlays();
         }
      });
      if (this.riptide$isRiptideActive()) {
         if (this.riptide$overlaysBuilt && RiptideOverlayManager.get().hasRegisteredOverlays()) {
            if (this.launcherOverlay != null) {
               this.launcherOverlay.setVisible(true);
            }
         } else {
            try {
               this.riptide$buildOverlays();
               this.riptide$overlaysBuilt = true;
            } catch (Throwable var4) {
            }
         }
      }
   }

   @Unique
   private void riptide$buildOverlays() {
      RiptideLANSync.getInstance().setOnSessionStateChanged(() -> {});
      this.lanSyncOverlay = RiptideLANSyncOverlay.getSharedOverlay(this.font);
      this.macroListOverlay = new RiptideMacroListOverlay(this.font);
      this.queueEditorOverlay = new RiptideQueueEditorOverlay(this.font);
      this.customFilterOverlay = new RiptideCustomFilterOverlay(this.font);
      this.customFilterPresetOverlay = this.customFilterOverlay.getPresetManagerOverlay();
      this.lanSyncOverlay.restoreState();
      this.macroListOverlay.restoreState();
      this.queueEditorOverlay.restoreState();
      this.customFilterOverlay.restoreLayout();
      if (this.customFilterPresetOverlay != null) {
         this.customFilterPresetOverlay.restoreLayout();
      }

      Minecraft mc = Minecraft.getInstance();
      boolean inWorld = mc != null && mc.player != null && mc.level != null;
      this.macroEditorOverlay = RiptideMacroEditorOverlay.getSharedOverlay();
      if (this.macroEditorOverlay != null) {
         this.macroEditorOverlay.restoreState();
         this.macroEditorOverlay.setConfigurationOnly(!inWorld);
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

      ActionEditorOverlay actionEditor = ActionEditorOverlay.getSharedOverlay();
      actionEditor.setWorldCaptureAllowed(inWorld);
      manager.register(actionEditor);
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

      if (!RiptideLiteVariant.enabled()) {
         IRiptideOverlay matchmakingOverlay = RiptideModule.get().getMatchmakingOverlay();
         if (matchmakingOverlay != null && matchmakingOverlay.isVisible()) {
            manager.register(matchmakingOverlay);
         }

         IRiptideOverlay profilesOverlay = RiptideModule.get().getProfilesOverlay();
         if (profilesOverlay != null && profilesOverlay.isVisible()) {
            manager.register(profilesOverlay);
         }
      }

      this.launcherOverlay.restoreLayout();
      this.launcherOverlay.setVisible(true);
      manager.register(this.launcherOverlay);
   }

   @Unique
   private void riptide$saveOverlays() {
      if (this.riptide$isRiptideActive()) {
         try {
            RiptideOverlayManager.get().restoreClampedAwayBounds();
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
         } finally {
            RiptideOverlayManager.get().clear();
         }
      }
   }

   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      return this.riptide$isRiptideActive() && RiptideOverlayManager.get().handleMouseClicked(click.x(), click.y(), click.button())
         ? true
         : super.mouseClicked(click, doubled);
   }

   public boolean mouseReleased(MouseButtonEvent click) {
      return this.riptide$isRiptideActive() && RiptideOverlayManager.get().handleMouseReleased(click.x(), click.y(), click.button())
         ? true
         : super.mouseReleased(click);
   }

   public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
      return this.riptide$isRiptideActive() && RiptideOverlayManager.get().handleMouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY)
         ? true
         : super.mouseDragged(click, deltaX, deltaY);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      return this.riptide$isRiptideActive() && RiptideOverlayManager.get().handleMouseScrolled(mouseX, mouseY, verticalAmount)
         ? true
         : super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
   }

   public boolean keyPressed(KeyEvent input) {
      return this.riptide$isRiptideActive() && RiptideOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())
         ? true
         : super.keyPressed(input);
   }

   public boolean charTyped(CharacterEvent input) {
      return this.riptide$isRiptideActive() && RiptideOverlayManager.get().handleCharTyped((char)input.codepoint(), 0) ? true : super.charTyped(input);
   }

   @Unique
   private boolean riptide$isRiptideActive() {
      RiptideModule module = RiptideModule.get();
      return module != null && module.isUsable();
   }
}
