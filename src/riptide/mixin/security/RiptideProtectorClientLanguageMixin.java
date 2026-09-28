package riptide.mixin.security;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.io.InputStream;
import java.util.List;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.impl.resource.pack.ModNioPackResources;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.CompositePackResources;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.security.RiptideProtectorModResolver;
import riptide.security.RiptideProtectorTracker;

@Mixin({ClientLanguage.class})
public class RiptideProtectorClientLanguageMixin {
   @Inject(
      method = {"loadFrom"},
      at = {@At("HEAD")}
   )
   private static void riptide$clearLanguageTracking(
      ResourceManager resourceManager, List<String> languageStack, boolean defaultRightToLeft, CallbackInfoReturnable<ClientLanguage> cir
   ) {
      RiptideProtectorTracker.resetTranslations();
   }

   @WrapOperation(
      method = {"appendFrom"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/locale/Language;loadFromJson(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V"
      )}
   )
   private static void riptide$trackTranslations(InputStream stream, BiConsumer<String, String> output, Operation<Void> original, @Local Resource resource) {
      PackResources source = resource.source();
      if (source instanceof VanillaPackResources) {
         original.call(new Object[]{stream, trackingOutput(output, (key, value) -> RiptideProtectorTracker.addVanillaTranslation(key))});
      } else if (source instanceof FilePackResources || source instanceof CompositePackResources) {
         original.call(new Object[]{stream, trackingOutput(output, RiptideProtectorTracker::addServerTranslation)});
      } else if (source instanceof PathPackResources) {
         original.call(new Object[]{stream, output});
      } else {
         String modId = source instanceof ModNioPackResources modPack
            ? modPack.getFabricModMetadata().getId()
            : RiptideProtectorModResolver.modFromClass(source.getClass());
         if (modId == null) {
            original.call(new Object[]{stream, output});
         } else {
            original.call(new Object[]{stream, trackingOutput(output, (key, value) -> RiptideProtectorTracker.addModTranslation(key, modId))});
         }
      }
   }

   private static BiConsumer<String, String> trackingOutput(BiConsumer<String, String> output, BiConsumer<String, String> tracker) {
      return (key, value) -> {
         tracker.accept(key, value);
         output.accept(key, value);
      };
   }
}
