package riptide.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.gui.screen.RiptideStyledButton;
import riptide.gui.vanillaui.components.Button;
import riptide.modules.PackAutoReconnectState;
import riptide.modules.PackHideState;
import riptide.util.RiptideTheme;

@Mixin({ConnectScreen.class})
public abstract class RiptideConnectScreenUiMixin extends Screen {
   private static final String[] STEP_KEYS = new String[]{
      "connect.connecting", "connect.authorizing", "connect.encrypting", "connect.negotiating", "connect.joining", null
   };
   private static final String[] STEP_LABELS = new String[]{"Connecting", "Authorizing", "Encrypting", "Negotiating", "Joining World", "Loading Terrain"};
   private static final int DONE_COLOR = -7697782;
   private static final int UPCOMING_COLOR = -11908534;
   private static final char[] SPINNER = new char[]{'|', '/', '-', '\\'};
   private int riptide$step;
   @Unique
   private RiptideStyledButton riptide$reconnectToggle;

   protected RiptideConnectScreenUiMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void riptide$addReconnectToggle(CallbackInfo ci) {
      if (!PackHideState.isActive() && PackAutoReconnectState.canShowToggle()) {
         int w = 148;
         int h = 20;
         this.riptide$reconnectToggle = new RiptideStyledButton(
            this.width / 2 - w / 2,
            this.height - 38,
            w,
            h,
            Component.literal("Auto Reconnect"),
            Button.Tone.PRIMARY,
            PackAutoReconnectState::toggleLabel,
            button -> PackAutoReconnectState.toggle()
         );
         this.addRenderableWidget(this.riptide$reconnectToggle);
      }
   }

   @Inject(
      method = {"extractRenderState"},
      at = {@At("HEAD")}
   )
   private void riptide$hideToggleInPanic(CallbackInfo ci) {
      if (this.riptide$reconnectToggle != null) {
         boolean show = !PackHideState.isActive();
         this.riptide$reconnectToggle.visible = show;
         this.riptide$reconnectToggle.active = show;
      }
   }

   @Inject(
      method = {"updateStatus"},
      at = {@At("HEAD")}
   )
   private void riptide$trackStep(Component status, CallbackInfo ci) {
      if (status.getContents() instanceof TranslatableContents translatable) {
         String var6 = translatable.getKey();

         for (int i = 0; i < STEP_KEYS.length; i++) {
            if (STEP_KEYS[i] != null && STEP_KEYS[i].equals(var6)) {
               if (i > this.riptide$step) {
                  this.riptide$step = i;
               }

               return;
            }
         }
      }
   }

   @Redirect(
      method = {"extractRenderState"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
      )
   )
   private void riptide$renderJoinSteps(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
      if (PackHideState.isActive()) {
         graphics.centeredText(font, text, x, y, color);
      } else {
         int accent = RiptideTheme.recolor(-50373, RiptideTheme.Channel.ACCENT) & 16777215;
         int rowHeight = 12;
         int top = y - STEP_LABELS.length * rowHeight / 2 + 1;
         char spin = SPINNER[(int)(System.currentTimeMillis() / 100L % SPINNER.length)];

         for (int i = 0; i < STEP_LABELS.length; i++) {
            String label;
            int rgb;
            if (i < this.riptide$step) {
               label = STEP_LABELS[i];
               rgb = -7697782;
            } else if (i == this.riptide$step) {
               label = spin + " " + STEP_LABELS[i];
               rgb = 0xFF000000 | accent;
            } else {
               label = STEP_LABELS[i];
               rgb = -11908534;
            }

            graphics.centeredText(font, Component.literal(label), x, top + i * rowHeight, rgb);
         }
      }
   }
}
