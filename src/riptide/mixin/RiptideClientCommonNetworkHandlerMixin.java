package riptide.mixin;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.PackHideState;
import riptide.modules.RiptideModule;
import riptide.security.RiptidePackResponseScheduler;
import riptide.security.RiptideProtectorPackStrip;
import riptide.security.RiptideResourcePackTruthGuard;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideSharedState;
import riptide.util.custommenu.CustomMenuTracker;

@Mixin({ClientCommonPacketListenerImpl.class})
public abstract class RiptideClientCommonNetworkHandlerMixin {
   @Shadow
   @Final
   protected Minecraft minecraft;

   @Shadow
   public abstract void send(Packet<?> var1);

   @Inject(
      method = {"handleResourcePackPush"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yang$onResourcePackSend(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
      if (!PackHideState.isHardLocked()) {
         RiptideSharedState shared = RiptideSharedState.get();
         RiptideModule module = RiptideModule.get();
         boolean shouldForceDeny = shared.shouldForceDenyResourcePack() || module != null && module.isForceDenyResourcePack();
         boolean shouldBypass = shared.shouldBypassResourcePack() || module != null && module.isBypassResourcePack();
         RiptideResourcePackTruthGuard.Verdict verdict = RiptideResourcePackTruthGuard.classify(packet, shouldForceDeny, shouldBypass);
         if (verdict.shouldCancelVanilla()) {
            RiptideProtectorPackStrip.onPop(packet.id());
            Consumer<ServerboundResourcePackPacket> sender = this::send;
            switch (verdict.kind()) {
               case BYPASS_SUCCESS: {
                  long accepted = RiptidePackResponseScheduler.acceptDelayMs();
                  long downloaded = RiptidePackResponseScheduler.downloadedDelayMs(accepted);
                  long applied = RiptidePackResponseScheduler.appliedDelayMs(downloaded);
                  RiptidePackResponseScheduler.schedule(packet.id(), Action.ACCEPTED, accepted, sender);
                  RiptidePackResponseScheduler.schedule(packet.id(), Action.DOWNLOADED, downloaded, sender);
                  RiptidePackResponseScheduler.schedule(packet.id(), Action.SUCCESSFULLY_LOADED, applied, sender);
                  break;
               }
               case DECLINE:
                  RiptidePackResponseScheduler.schedule(
                     packet.id(),
                     Action.DECLINED,
                     RiptidePackResponseScheduler.declineDelayMs(),
                     sender,
                     () -> RiptideClientMessaging.sendPrefixed("Riptide denied server resource pack.")
                  );
                  break;
               case INVALID_URL:
                  this.send(new ServerboundResourcePackPacket(packet.id(), Action.INVALID_URL));
                  break;
               case FAILED_DOWNLOAD: {
                  long accepted = RiptidePackResponseScheduler.acceptDelayMs();
                  RiptidePackResponseScheduler.schedule(packet.id(), Action.ACCEPTED, accepted, sender);
                  RiptidePackResponseScheduler.schedule(packet.id(), Action.FAILED_DOWNLOAD, RiptidePackResponseScheduler.failedDelayMs(accepted), sender);
               }
            }

            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"handleShowDialog"},
      at = {@At("TAIL")}
   )
   private void riptide$trackCustomMenu(ClientboundShowDialogPacket packet, CallbackInfo ci) {
      String phase = this instanceof ClientConfigurationPacketListener ? "CONFIGURATION" : "PLAY";
      CustomMenuTracker.accept(packet, phase);
   }

   @Inject(
      method = {"handleClearDialog"},
      at = {@At("TAIL")}
   )
   private void riptide$clearCustomMenu(ClientboundClearDialogPacket packet, CallbackInfo ci) {
      String phase = this instanceof ClientConfigurationPacketListener ? "CONFIGURATION" : "PLAY";
      CustomMenuTracker.accept(packet, phase);
   }
}
