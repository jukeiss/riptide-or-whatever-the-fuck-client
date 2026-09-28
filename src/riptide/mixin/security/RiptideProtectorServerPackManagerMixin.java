package riptide.mixin.security;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.server.packs.DownloadQueue.BatchResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.security.RiptideProtectorPackStrip;
import riptide.security.RiptideProtectorServerPackFailureGuard;
import riptide.util.RiptideNotifications;

@Mixin({ServerPackManager.class})
public abstract class RiptideProtectorServerPackManagerMixin {
   @Unique
   private static long riptide$lastRecoveryReloadMs;
   @Unique
   private static long riptide$lastRecoveryToastMs;

   @Shadow
   public abstract void popAll();

   @Inject(
      method = {"onDownload"},
      at = {@At("HEAD")}
   )
   private void riptide$makeFailedServerPackBatchAtomic(Collection<?> data, BatchResult result, CallbackInfo ci) {
      if (result != null && !result.failed().isEmpty()) {
         RiptideProtectorServerPackFailureGuard.suppressServerPacksTemporarily();

         try {
            Map<UUID, ?> downloaded = result.downloaded();
            if (downloaded != null) {
               downloaded.clear();
            }
         } catch (Throwable var5) {
            riptide.RiptideClientAddon.LOG.warn("[RiptideProtector] Failed to clear partial server-pack batch.", var5);
         }
      }
   }

   @Inject(
      method = {"onDownload"},
      at = {@At("TAIL")}
   )
   private void riptide$recoverFromFailedServerPackDownload(Collection<?> data, BatchResult result, CallbackInfo ci) {
      if (result != null && !result.failed().isEmpty()) {
         RiptideProtectorServerPackFailureGuard.suppressServerPacksTemporarily();
         RiptideProtectorPackStrip.clearAll();

         try {
            this.popAll();
         } catch (Throwable var5) {
            riptide.RiptideClientAddon.LOG.warn("[RiptideProtector] Failed to clear server packs after download failure.", var5);
         }

         Minecraft client = Minecraft.getInstance();
         if (client != null) {
            client.execute(() -> {
               try {
                  client.getDownloadedPackSource().popAll();
                  long now = System.currentTimeMillis();
                  if (now - riptide$lastRecoveryToastMs > 5000L) {
                     riptide$lastRecoveryToastMs = now;
                     RiptideNotifications.warning("Server resource pack failed. Restored client resources.");
                  }

                  if (now - riptide$lastRecoveryReloadMs > 1000L) {
                     riptide$lastRecoveryReloadMs = now;
                     client.reloadResourcePacks();
                  }
               } catch (Throwable var3x) {
                  riptide.RiptideClientAddon.LOG.warn("[RiptideProtector] Failed to clear downloaded pack source after download failure.", var3x);
               }
            });
         }
      }
   }
}
