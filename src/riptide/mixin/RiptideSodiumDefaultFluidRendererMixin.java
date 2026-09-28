package riptide.mixin;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import riptide.modules.ModuleRenderUtil;

@Pseudo
@Mixin(
   targets = {"net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer"},
   remap = false
)
public abstract class RiptideSodiumDefaultFluidRendererMixin {
   @Shadow(
      remap = false
   )
   @Final
   private int[] quadColors;
   @Unique
   private int riptide$xrayAlpha = -1;

   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$xraySodiumFluidStart(
      @Coerce Object level,
      BlockState blockState,
      FluidState fluidState,
      BlockPos blockPos,
      BlockPos offset,
      @Coerce Object collector,
      @Coerce Object buffers,
      @Coerce Object material,
      @Coerce Object colorProvider,
      FluidModel fluidModel,
      CallbackInfo ci
   ) {
      this.riptide$xrayAlpha = -1;
      if (ModuleRenderUtil.hasXrayRenderWork()) {
         this.riptide$xrayAlpha = ModuleRenderUtil.xrayFluidAlpha(fluidState, blockPos);
         if (this.riptide$xrayAlpha == 0) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"render"},
      at = {@At("RETURN")},
      require = 0
   )
   private void riptide$xraySodiumFluidEnd(
      @Coerce Object level,
      BlockState blockState,
      FluidState fluidState,
      BlockPos blockPos,
      BlockPos offset,
      @Coerce Object collector,
      @Coerce Object buffers,
      @Coerce Object material,
      @Coerce Object colorProvider,
      FluidModel fluidModel,
      CallbackInfo ci
   ) {
      this.riptide$xrayAlpha = -1;
   }

   @Inject(
      method = {"writeQuad"},
      at = {@At("HEAD")},
      require = 0
   )
   private void riptide$xraySodiumFluidMaterial(
      @Coerce Object buffers,
      @Coerce Object collector,
      @Coerce Object material,
      BlockPos offset,
      @Coerce Object quad,
      @Coerce Object facing,
      boolean flip,
      CallbackInfo ci
   ) {
      int alpha = this.riptide$xrayAlpha;
      if (alpha >= 0) {
         for (int i = 0; i < this.quadColors.length; i++) {
            this.quadColors[i] = (alpha & 0xFF) << 24 | this.quadColors[i] & 16777215;
         }
      }
   }

   @ModifyArgs(
      method = {"render"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/DefaultFluidRenderer;writeQuad(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/buffers/ChunkModelBuilder;Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/TranslucentGeometryCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;Lnet/minecraft/core/BlockPos;Lnet/caffeinemc/mods/sodium/client/model/quad/ModelQuadView;Lnet/caffeinemc/mods/sodium/client/model/quad/properties/ModelQuadFacing;Z)V"
      ),
      require = 0
   )
   private void riptide$xraySodiumFluidLayer(Args args) {
      int alpha = this.riptide$xrayAlpha;
      if (alpha > 0 && alpha < 255) {
         Object material = args.get(2);
         Object translucent = ModuleRenderUtil.sodiumTranslucentMaterial(material);
         if (translucent != null) {
            args.set(2, translucent);
         }
      }
   }
}
