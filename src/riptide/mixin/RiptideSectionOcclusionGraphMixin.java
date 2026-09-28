package riptide.mixin;

import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import riptide.modules.ModuleRenderUtil;

@Mixin({SectionOcclusionGraph.class})
public class RiptideSectionOcclusionGraphMixin {
   @ModifyVariable(
      method = {"runUpdates"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private boolean riptide$disableSmartCull(boolean smartCull) {
      return ModuleRenderUtil.shouldBypassOcclusionCulling() ? false : smartCull;
   }
}
