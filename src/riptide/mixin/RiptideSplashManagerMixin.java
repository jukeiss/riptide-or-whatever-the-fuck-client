package riptide.mixin;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.resources.SplashManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.PackHideState;
import riptide.util.RiptideVanillaSplash;

@Mixin(
   value = {SplashManager.class},
   priority = 2000
)
public abstract class RiptideSplashManagerMixin {
   @Inject(
      method = {"prepare"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$prepareOnlyVanillaSplashes(ResourceManager manager, ProfilerFiller profiler, CallbackInfoReturnable<List<Component>> cir) {
      if (PackHideState.isActive()) {
         cir.setReturnValue(RiptideVanillaSplash.components(Minecraft.getInstance()));
      }
   }

   @Inject(
      method = {"getSplash"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$getOnlyVanillaSplash(CallbackInfoReturnable<SplashRenderer> cir) {
      if (PackHideState.isActive()) {
         cir.setReturnValue(RiptideVanillaSplash.pick(Minecraft.getInstance()));
      }
   }
}
