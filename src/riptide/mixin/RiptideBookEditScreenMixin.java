package riptide.mixin;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterExtract;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.RiptideModule;
import riptide.util.IRiptideOverlay;
import riptide.util.RiptideClientMessaging;
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
import riptide.util.RiptideSharedState;
import riptide.util.RiptideSpecialGuiActions;

@Mixin({BookEditScreen.class})
public abstract class RiptideBookEditScreenMixin extends Screen implements RiptideSpecialGuiActions {
   @Unique
   private static final Minecraft MC = Minecraft.getInstance();
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

   protected RiptideBookEditScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void yang$init(CallbackInfo ci) {
      if (this.yang$isRiptideActive()) {
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

         this.launcherOverlay.restoreLayout();
         manager.register(this.launcherOverlay);
         Screen screen = this;
         ScreenEvents.afterExtract(screen).register((AfterExtract)(scrn, drawContext, mouseX, mouseY, tickDelta) -> {
            if (this.yang$isRiptideActive()) {
               RiptideOverlayManager.get().renderAll(drawContext, mouseX, mouseY, tickDelta);
            }
         });
      }
   }

   public void removed() {
      if (this.yang$isRiptideActive()) {
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

      super.removed();
   }

   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      return this.yang$isRiptideActive() && RiptideOverlayManager.get().handleMouseClicked(click.x(), click.y(), click.button())
         ? true
         : super.mouseClicked(click, doubled);
   }

   public boolean mouseReleased(MouseButtonEvent click) {
      return this.yang$isRiptideActive() && RiptideOverlayManager.get().handleMouseReleased(click.x(), click.y(), click.button())
         ? true
         : super.mouseReleased(click);
   }

   public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
      return this.yang$isRiptideActive() && RiptideOverlayManager.get().handleMouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY)
         ? true
         : super.mouseDragged(click, deltaX, deltaY);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      return this.yang$isRiptideActive() && RiptideOverlayManager.get().handleMouseScrolled(mouseX, mouseY, verticalAmount)
         ? true
         : super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
   }

   @Inject(
      method = {"keyPressed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yang$keyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
      if (this.yang$isRiptideActive()) {
         if (RiptideOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) {
            cir.setReturnValue(true);
         }
      }
   }

   public boolean charTyped(CharacterEvent input) {
      return this.yang$isRiptideActive() && RiptideOverlayManager.get().handleCharTyped((char)input.codepoint(), 0) ? true : super.charTyped(input);
   }

   @Unique
   private boolean yang$isRiptideActive() {
      RiptideModule module = RiptideModule.get();
      return module != null && module.isActive();
   }

   @Override
   public void riptide$closeWithPacket() {
      this.riptide$closeWithPacket(true);
   }

   @Override
   public void riptide$closeWithPacket(boolean notify) {
      if (MC.getConnection() != null) {
         RiptideSharedState.get().setForceNextBookEditPacket(true);
         this.riptide$invokeSaveChanges();
      }

      MC.gui.setScreen(null);
   }

   @Override
   public void riptide$closeWithoutPacket() {
      this.riptide$closeWithoutPacket(true);
   }

   @Override
   public void riptide$closeWithoutPacket(boolean notify) {
      MC.gui.setScreen(null);
      if (notify) {
         RiptideClientMessaging.sendPrefixed("Book edit closed without packet.");
      }
   }

   @Override
   public void riptide$desync() {
      this.riptide$desync(true);
   }

   @Override
   public void riptide$desync(boolean notify) {
      if (MC.getConnection() == null) {
         if (notify) {
            RiptideClientMessaging.sendPrefixed("Failed to desync: no network.");
         }
      } else {
         RiptideSharedState.get().setForceNextBookEditPacket(true);
         this.riptide$invokeSaveChanges();
         if (notify) {
            RiptideClientMessaging.sendPrefixed("Book update packet sent; GUI intentionally stays open.");
         }
      }
   }

   @Invoker("saveChanges")
   protected abstract void riptide$invokeSaveChanges();
}
