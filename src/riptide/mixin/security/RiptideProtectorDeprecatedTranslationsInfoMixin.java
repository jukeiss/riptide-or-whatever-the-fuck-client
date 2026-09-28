package riptide.mixin.security;

import java.util.List;
import java.util.Map;
import net.minecraft.locale.DeprecatedTranslationsInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.security.RiptideProtectorTracker;

@Mixin({DeprecatedTranslationsInfo.class})
public abstract class RiptideProtectorDeprecatedTranslationsInfoMixin {
   @Shadow
   public abstract List<String> removed();

   @Shadow
   public abstract Map<String, String> renamed();

   @Inject(
      method = {"applyToMap"},
      at = {@At("HEAD")}
   )
   private void riptide$trackDeprecatedTranslations(Map<String, String> translations, CallbackInfo ci) {
      RiptideProtectorTracker.applyDeprecatedTranslations(this.removed(), this.renamed());
   }
}
