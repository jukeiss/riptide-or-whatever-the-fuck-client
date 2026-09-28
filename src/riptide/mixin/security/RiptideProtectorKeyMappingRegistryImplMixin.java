package riptide.mixin.security;

import java.util.LinkedHashSet;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.security.RiptideProtectorModResolver;
import riptide.security.RiptideProtectorTracker;

@Mixin(
   targets = {"net.fabricmc.fabric.impl.client.keymapping.KeyMappingRegistryImpl"}
)
public class RiptideProtectorKeyMappingRegistryImplMixin {
   @Inject(
      method = {"registerKeyMapping"},
      at = {@At("RETURN")}
   )
   private static void riptide$trackModKeyMapping(KeyMapping keyMapping, CallbackInfoReturnable<KeyMapping> cir) {
      LinkedHashSet<String> mods = RiptideProtectorModResolver.modsFromStacktrace();
      if (!mods.isEmpty()) {
         RiptideProtectorTracker.addModKeybind(keyMapping.getName(), mods.getLast());
      }
   }
}
