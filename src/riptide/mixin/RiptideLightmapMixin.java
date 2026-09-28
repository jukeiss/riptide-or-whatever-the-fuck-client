package riptide.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.ModuleRenderUtil;

@Mixin({Lightmap.class})
public abstract class RiptideLightmapMixin {
   @Shadow
   @Final
   private GpuTexture texture;
   @Unique
   private boolean riptide$wasBright;

   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$fullbrightLightmap(LightmapRenderState renderState, CallbackInfo ci) {
      if (ModuleRenderUtil.hasBrightLightmapWork()) {
         ProfilerFiller profiler = Profiler.get();
         profiler.push("riptide_lightmap");
         RenderSystem.getDevice().createCommandEncoder().clearColorTexture(this.texture, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F));
         profiler.pop();
         this.riptide$wasBright = true;
         ci.cancel();
      } else {
         if (this.riptide$wasBright) {
            this.riptide$wasBright = false;
            renderState.needsUpdate = true;
         }
      }
   }
}
