package riptide.mixin.security;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.resources.server.PackReloadConfig.IdAndPath;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.FilePackResources.FileResourcesSupplier;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.Pack.Metadata;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.security.RiptideProtector;
import riptide.security.RiptideProtectorLangOnlyPackResources;
import riptide.security.RiptideProtectorPackStrip;
import riptide.security.RiptideProtectorServerPackFailureGuard;

@Mixin({DownloadedPackSource.class})
public abstract class RiptideProtectorDownloadedPackSourceMixin {
   @Inject(
      method = {"createRepositorySource"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void riptide$suppressDownloadedPackSourceAfterFailure(CallbackInfoReturnable<RepositorySource> cir) {
      RepositorySource original = (RepositorySource)cir.getReturnValue();
      cir.setReturnValue((RepositorySource)output -> {
         if (!RiptideProtectorServerPackFailureGuard.shouldSuppressServerPacks()) {
            original.loadPacks(output);
         }
      });
   }

   @WrapOperation(
      method = {"loadRequestedPacks"},
      at = {@At(
         value = "NEW",
         target = "(Ljava/nio/file/Path;)Lnet/minecraft/server/packs/FilePackResources$FileResourcesSupplier;"
      )}
   )
   private FileResourcesSupplier riptide$wrapFilePackSupplier(Path file, Operation<FileResourcesSupplier> original, @Local IdAndPath idAndPath) {
      final FileResourcesSupplier real = (FileResourcesSupplier)original.call(new Object[]{file});
      if (!RiptideProtector.shouldStripServerPacks()) {
         return real;
      } else {
         UUID packId = idAndPath.id();
         return !RiptideProtectorPackStrip.isWrapped(packId) ? real : new FileResourcesSupplier(file) {
            {
               Objects.requireNonNull(RiptideProtectorDownloadedPackSourceMixin.this);
            }

            public PackResources openPrimary(PackLocationInfo loc) {
               return new RiptideProtectorLangOnlyPackResources(real.openPrimary(loc));
            }

            public PackResources openFull(PackLocationInfo loc, Metadata md) {
               return new RiptideProtectorLangOnlyPackResources(real.openFull(loc, md));
            }
         };
      }
   }
}
