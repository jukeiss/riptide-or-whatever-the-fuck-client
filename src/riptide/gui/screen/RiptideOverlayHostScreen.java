package riptide.gui.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.Button;
import riptide.modules.RiptideModule;
import riptide.util.IRiptideOverlay;
import riptide.util.RiptideHostScreenOverlays;
import riptide.util.RiptideItemNbtInspectOverlay;
import riptide.util.RiptideOverlayManager;

public class RiptideOverlayHostScreen extends Screen {
   private static final int BACK_W = 200;
   private static final int BACK_H = 20;
   private static final int BACK_BOTTOM_MARGIN = 27;
   private final IRiptideOverlay tiedOverlay;
   private final Screen returnScreen;
   private final boolean showBackButton;
   private final boolean dismissOnClose;
   private boolean darkenBackground;
   private RiptideHostScreenOverlays overlaySet;

   public RiptideOverlayHostScreen() {
      this(null, null, false);
   }

   public RiptideOverlayHostScreen(IRiptideOverlay tiedOverlay) {
      this(tiedOverlay, null, false);
   }

   public RiptideOverlayHostScreen(IRiptideOverlay tiedOverlay, Screen returnScreen) {
      this(tiedOverlay, returnScreen, false);
   }

   public RiptideOverlayHostScreen(IRiptideOverlay tiedOverlay, Screen returnScreen, boolean showBackButton) {
      this(tiedOverlay, returnScreen, showBackButton, false);
   }

   public RiptideOverlayHostScreen(IRiptideOverlay tiedOverlay, Screen returnScreen, boolean showBackButton, boolean dismissOnClose) {
      super(Component.literal("Riptide Overlays"));
      this.tiedOverlay = tiedOverlay;
      this.returnScreen = returnScreen;
      this.showBackButton = showBackButton;
      this.dismissOnClose = dismissOnClose;
   }

   public RiptideOverlayHostScreen withDarkenedBackground() {
      this.darkenBackground = true;
      return this;
   }

   public boolean hostsOverlay(IRiptideOverlay overlay) {
      return overlay != null && this.tiedOverlay == overlay;
   }

   public boolean isPauseScreen() {
      return false;
   }

   protected void init() {
      super.init();
      if (this.overlaySet == null && !this.showBackButton && this.minecraft != null && this.minecraft.level != null) {
         RiptideModule module = RiptideModule.get();
         if (module != null && module.isActive()) {
            try {
               this.overlaySet = RiptideHostScreenOverlays.build(this.font);
            } catch (Throwable var3) {
               riptide.RiptideClientAddon.LOG.warn("Host screen window set failed to build", var3);
            }

            if (this.tiedOverlay != null) {
               RiptideOverlayManager.get().register(this.tiedOverlay);
               RiptideOverlayManager.get().bringToFront(this.tiedOverlay);
            }
         }
      }
   }

   public void removed() {
      if (this.overlaySet != null) {
         try {
            this.overlaySet.saveAndClear();
         } catch (Throwable var2) {
            riptide.RiptideClientAddon.LOG.warn("Host screen window set failed to save", var2);
         }

         this.overlaySet = null;
      }

      super.removed();
   }

   public void tick() {
      super.tick();
      if (this.tiedOverlay != null && !this.tiedOverlay.isVisible() && this.minecraft != null && this.minecraft.gui.screen() == this) {
         this.minecraft.gui.setScreen(this.returnScreen);
      }
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      if (this.darkenBackground) {
         UiRenderer.rect(graphics, UiBounds.of(0, 0, this.width, this.height), -1072689136);
      }

      RiptideOverlayManager.get().renderAll(graphics, mouseX, mouseY, delta);
      if (this.showBackButton && this.font != null) {
         int bw = Math.min(200, Math.max(120, this.width - 40));
         int bx = (this.width - bw) / 2;
         int by = this.height - 27;
         boolean hovered = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 20;
         Button.render(UiContexts.overlay(graphics, this.font, mouseX, mouseY), UiBounds.of(bx, by, bw, 20), "Back", Button.Tone.NORMAL, hovered, false);
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
      if (this.showBackButton) {
         int bw = Math.min(200, Math.max(120, this.width - 40));
         int bx = (this.width - bw) / 2;
         int by = this.height - 27;
         if (event.x() >= bx && event.x() < bx + bw && event.y() >= by && event.y() < by + 20) {
            this.goBack();
            return true;
         }
      }

      return RiptideOverlayManager.get().handleMouseClicked(event.x(), event.y(), event.button());
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      return RiptideOverlayManager.get().handleMouseReleased(event.x(), event.y(), event.button());
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      return RiptideOverlayManager.get().handleMouseDragged(event.x(), event.y(), event.button(), dx, dy);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      return RiptideOverlayManager.get().handleMouseScrolled(mouseX, mouseY, scrollY);
   }

   public boolean keyPressed(KeyEvent input) {
      boolean inventoryKey = this.minecraft != null
         && this.minecraft.options != null
         && this.minecraft.options.keyInventory != null
         && this.minecraft.options.keyInventory.matches(input);
      boolean closeKey = input.key() == 256 || inventoryKey;
      if (closeKey && this.minecraft != null && !RiptideOverlayManager.get().isAnyTextFieldFocused()) {
         this.goBack();
         return true;
      } else if (RiptideOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) {
         return true;
      } else if (closeKey && this.minecraft != null) {
         this.goBack();
         return true;
      } else {
         return false;
      }
   }

   public boolean charTyped(CharacterEvent input) {
      return RiptideOverlayManager.get().handleCharTyped((char)input.codepoint(), 0);
   }

   private void goBack() {
      if (this.tiedOverlay != null && (this.showBackButton || this.dismissOnClose)) {
         this.tiedOverlay.setVisible(false);
      }

      if (this.dismissOnClose) {
         if (this.tiedOverlay != null) {
            RiptideOverlayManager.get().unregister(this.tiedOverlay);
         }

         RiptideItemNbtInspectOverlay.dismissShared();
      }

      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.returnScreen);
      }
   }
}
