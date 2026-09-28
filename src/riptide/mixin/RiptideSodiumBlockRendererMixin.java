package riptide.mixin;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.GoldenLeverModule;
import riptide.modules.ModuleRenderUtil;

@Pseudo
@Mixin(
   targets = {"net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer"},
   remap = false
)
public abstract class RiptideSodiumBlockRendererMixin {
   @Unique
   private int riptide$xrayAlpha = -1;
   @Unique
   private boolean riptide$goldenLever;
   @Unique
   private int riptide$darkenTint = -1;

   @Inject(
      method = {"renderModel"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$xraySodiumBlockStart(@Coerce Object model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
      boolean xrayActive = ModuleRenderUtil.hasXrayRenderWork();
      boolean goldenLeverActive = GoldenLeverModule.isStylingActive();
      boolean darkenActive = ModuleRenderUtil.hasWorldDarkenWork();
      if (!xrayActive && !goldenLeverActive && !darkenActive) {
         this.riptide$xrayAlpha = -1;
         this.riptide$goldenLever = false;
         this.riptide$darkenTint = -1;
      } else {
         this.riptide$xrayAlpha = xrayActive ? ModuleRenderUtil.xrayAlpha(state, pos) : -1;
         this.riptide$goldenLever = goldenLeverActive && GoldenLeverModule.shouldStyle(state);
         this.riptide$darkenTint = darkenActive ? ModuleRenderUtil.worldDarkenTint(state, pos) : -1;
         if (this.riptide$xrayAlpha == 0) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"renderModel"},
      at = {@At("RETURN")}
   )
   private void riptide$xraySodiumBlockEnd(@Coerce Object model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
      this.riptide$xrayAlpha = -1;
      this.riptide$goldenLever = false;
      this.riptide$darkenTint = -1;
   }

   @Inject(
      method = {"processQuad"},
      at = {@At("HEAD")}
   )
   private void riptide$xraySodiumBlockMaterial(@Coerce Object quad, CallbackInfo ci) {
      int alpha = this.riptide$xrayAlpha;
      if (this.riptide$goldenLever) {
         ModuleRenderUtil.applySodiumQuadTint(quad, -11702);
      }

      if (this.riptide$darkenTint != -1) {
         ModuleRenderUtil.applySodiumQuadTint(quad, this.riptide$darkenTint);
      }

      if (alpha >= 0) {
         ModuleRenderUtil.applySodiumQuadAlpha(quad, alpha);
         if (alpha > 0 && alpha < 255) {
            ModuleRenderUtil.applySodiumQuadRenderLayer(quad, ChunkSectionLayer.TRANSLUCENT);
         }
      }
   }
}
