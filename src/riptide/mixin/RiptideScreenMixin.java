package riptide.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.ducks.RiptideExternalButtonScreen;
import riptide.gui.vanillaui.components.ScreenButton;
import riptide.mixin.accessor.RiptideScreenAccessor;
import riptide.modules.PackHideState;
import riptide.modules.RiptideModule;
import riptide.util.RiptideConfig;
import riptide.util.RiptideLecternButtons;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideOverlayManager;
import riptide.util.RiptidePacketLoggerOverlay;
import riptide.util.RiptideQueueEditorOverlay;
import riptide.util.RiptideUiScale;

@Mixin({Screen.class})
public abstract class RiptideScreenMixin {
   @Unique
   private static final Minecraft MC = Minecraft.getInstance();
   @Unique
   private boolean riptide$lecternInitialized;
   @Unique
   private RiptideQueueEditorOverlay riptide$queueEditorOverlay;
   @Unique
   private RiptidePacketLoggerOverlay riptide$packetLoggerOverlay;
   @Unique
   private static boolean riptide$toastFailureLogged;

   @Inject(
      method = {"init()V"},
      at = {@At("TAIL")}
   )
   private void riptide$onInit(CallbackInfo ci) {
      Screen screen = (Screen)this;
      if (screen instanceof LecternScreen && this.riptide$isModuleActive()) {
         if (this.riptide$lecternInitialized) {
            if (this.riptide$queueEditorOverlay != null) {
               this.riptide$queueEditorOverlay.restoreState();
            }

            if (this.riptide$queueEditorOverlay != null) {
               RiptideOverlayManager.get().register(this.riptide$queueEditorOverlay);
            }
         } else {
            Font textRenderer = ((RiptideScreenAccessor)this).getFont();
            this.riptide$queueEditorOverlay = new RiptideQueueEditorOverlay(textRenderer);
            this.riptide$queueEditorOverlay.restoreState();
            RiptideOverlayManager.get().register(this.riptide$queueEditorOverlay);
            this.riptide$lecternInitialized = true;
         }
      }
   }

   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void riptide$render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      Screen screen = (Screen)this;
      if (screen instanceof RiptideExternalButtonScreen externalButtonScreen) {
         externalButtonScreen.riptide$renderExternalButtons(context, mouseX, mouseY, delta);
      }

      if (screen instanceof LecternScreen && MC.player != null) {
         if (this.riptide$isModuleActive()) {
            Font textRenderer = ((RiptideScreenAccessor)this).getFont();
            if (textRenderer != null) {
               AbstractContainerMenu handler = MC.player.containerMenu;
               if (handler != null) {
                  int virtualMouseX = RiptideUiScale.toVirtualInt(mouseX);
                  int virtualMouseY = RiptideUiScale.toVirtualInt(mouseY);
                  RiptideUiScale.pushOverlayScale(context);

                  try {
                     for (ScreenButton button : RiptideLecternButtons.build(MC, this.riptide$queueEditorOverlay)) {
                        button.render(context, textRenderer, virtualMouseX, virtualMouseY);
                     }
                  } finally {
                     RiptideUiScale.popOverlayScale(context);
                  }
               }
            }
         }
      }
   }

   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void riptide$renderTopmostNotifications(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (RiptideNotifications.hasVisible()) {
         context.nextStratum();
         RiptideUiScale.pushOverlayScale(context);

         try {
            RiptideNotifications.render(context);
         } catch (Throwable var10) {
            if (!riptide$toastFailureLogged) {
               riptide$toastFailureLogged = true;
               riptide.RiptideClientAddon.LOG.warn("[UI] Notification render failed", var10);
            }
         } finally {
            RiptideUiScale.popOverlayScale(context);
         }
      }
   }

   @Inject(
      method = {"onClose"},
      at = {@At("HEAD")}
   )
   private void riptide$onClose(CallbackInfo ci) {
      RiptideConfig.enqueuePendingSaveNow();
      Screen screen = (Screen)this;
      if (screen instanceof LecternScreen) {
         RiptideOverlayManager.get().unregister(this.riptide$queueEditorOverlay);
         RiptideOverlayManager.get().unregister(this.riptide$packetLoggerOverlay);
      }
   }

   @Unique
   private boolean riptide$isModuleActive() {
      if (PackHideState.isHardLocked()) {
         return false;
      } else {
         RiptideModule module = RiptideModule.get();
         return module != null && module.isActive();
      }
   }
}
