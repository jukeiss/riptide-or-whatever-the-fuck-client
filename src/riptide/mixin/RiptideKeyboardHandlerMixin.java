package riptide.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.util.Util;
import net.minecraft.util.Util.OS;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.commands.RiptideCommands;
import riptide.util.RiptideClipboard;

@Mixin({KeyboardHandler.class})
public class RiptideKeyboardHandlerMixin {
   private static long riptide$lastScreenSeenMs;

   @Inject(
      method = {"charTyped"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$prefixOpensChat(long window, CharacterEvent event, CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && event != null && mc.player != null) {
         if (mc.gui.screen() != null) {
            riptide$lastScreenSeenMs = System.currentTimeMillis();
         } else if (System.currentTimeMillis() - riptide$lastScreenSeenMs >= 250L) {
            String prefix = RiptideCommands.effectivePrefix();
            if (prefix != null && prefix.length() == 1 && !"/".equals(prefix)) {
               if (event.codepoint() == prefix.charAt(0)) {
                  mc.gui.setScreen(new ChatScreen(prefix, false));
                  riptide$lastScreenSeenMs = System.currentTimeMillis();
                  ci.cancel();
               }
            }
         }
      }
   }

   @Inject(
      method = {"getClipboard"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$linuxClipboardGet(CallbackInfoReturnable<String> cir) {
      if (Util.getPlatform() == OS.LINUX) {
         cir.setReturnValue(RiptideClipboard.get());
      }
   }

   @Inject(
      method = {"setClipboard"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$linuxClipboardSet(String text, CallbackInfo ci) {
      if (Util.getPlatform() == OS.LINUX) {
         RiptideClipboard.set(text);
         ci.cancel();
      }
   }
}
