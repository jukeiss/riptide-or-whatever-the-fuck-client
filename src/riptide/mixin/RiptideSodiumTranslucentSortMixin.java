package riptide.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.ModuleRenderUtil;

@Pseudo
@Mixin(
   targets = {"net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector"},
   remap = false
)
public abstract class RiptideSodiumTranslucentSortMixin {
   @Inject(
      method = {"filterSortType"},
      at = {@At("RETURN")},
      cancellable = true,
      require = 0,
      remap = false
   )
   private static void riptide$xrayDisableTranslucentSort(CallbackInfoReturnable<Object> cir) {
      if (ModuleRenderUtil.hasXrayRenderWork()) {
         Object current = cir.getReturnValue();
         if (current instanceof Enum<?> sort) {
            String name = sort.name();
            if ("STATIC_TOPO".equals(name) || "DYNAMIC".equals(name)) {
               Object none = ModuleRenderUtil.sodiumNoneSortType(current);
               if (none != current) {
                  cir.setReturnValue(none);
               }
            }
         }
      }
   }
}
