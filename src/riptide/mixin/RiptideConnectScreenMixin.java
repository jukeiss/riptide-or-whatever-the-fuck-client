package riptide.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.PackAutoReconnectState;
import riptide.util.RiptideLiteVariant;
import riptide.util.RiptideProfileManager;

@Mixin({ConnectScreen.class})
public abstract class RiptideConnectScreenMixin {
   @Inject(
      method = {"startConnecting"},
      at = {@At("HEAD")}
   )
   private static void riptide$rememberConnectAttempt(
      Screen parent, Minecraft minecraft, ServerAddress hostAndPort, ServerData data, boolean isQuickPlay, TransferState transferState, CallbackInfo ci
   ) {
      PackAutoReconnectState.remember(data, hostAndPort);
      if (!RiptideLiteVariant.enabled()) {
         try {
            RiptideProfileManager.get().applyForServerConnect(hostAndPort, data);
         } catch (Throwable var8) {
            riptide.RiptideClientAddon.LOG.error("Profiles: failed to apply on-connect profile", var8);
         }
      }
   }
}
