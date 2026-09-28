package riptide.mixin.security;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.DownloadQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import riptide.security.RiptideProtector;

@Mixin({DownloadQueue.class})
public class RiptideProtectorDownloadQueueMixin {
   @Shadow
   @Final
   private Path cacheDir;

   @ModifyExpressionValue(
      method = {"lambda$runDownload$0", "method_55485"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/nio/file/Path;resolve(Ljava/lang/String;)Ljava/nio/file/Path;"
      )},
      require = 0
   )
   private Path riptide$isolatePackCache(Path original, @Local(argsOnly = true) UUID packId) {
      if (!RiptideProtector.shouldIsolatePackCache()) {
         return original;
      } else if (original != null && this.cacheDir != null && packId != null) {
         Path parent = original.getParent();
         if (parent != null && parent.equals(this.cacheDir)) {
            UUID accountId = Minecraft.getInstance().getUser().getProfileId();
            if (accountId == null) {
               riptide.RiptideClientAddon.LOG.warn("[RiptideProtector] Cannot isolate resource-pack cache: account UUID is null.");
               return original;
            } else {
               return this.cacheDir.resolve(accountId.toString()).resolve(packId.toString());
            }
         } else {
            return original;
         }
      } else {
         return original;
      }
   }
}
