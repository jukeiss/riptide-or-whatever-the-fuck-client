package riptide.mixin.indigo;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.ModuleRenderUtil;

@Pseudo
@Mixin(
   targets = {"net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl"},
   remap = false
)
public abstract class RiptideIndigoAltModelBlockRendererMixin {
   @Shadow
   private BlockAndTintGetter level;
   @Shadow
   private BlockPos pos;
   @Shadow
   private BlockState blockState;

   @Inject(
      method = {"shouldCullFace"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void riptide$xrayCullFace(Direction direction, CallbackInfoReturnable<Boolean> cir) {
      if (direction != null && ModuleRenderUtil.hasXrayRenderWork()) {
         boolean shouldDraw = ModuleRenderUtil.modifyXrayFace(this.level, this.blockState, direction, this.pos, !(Boolean)cir.getReturnValue());
         cir.setReturnValue(!shouldDraw);
      }
   }

   @Inject(
      method = {"transform"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void riptide$xrayTransform(MutableQuadView quad, CallbackInfoReturnable<Boolean> cir) {
      if ((Boolean)cir.getReturnValue()) {
         boolean xray = ModuleRenderUtil.hasXrayRenderWork();
         boolean darken = ModuleRenderUtil.hasWorldDarkenWork();
         if (xray || darken) {
            if (darken) {
               int tint = ModuleRenderUtil.worldDarkenTint(this.blockState, this.pos);
               if (tint != -1) {
                  for (int i = 0; i < 4; i++) {
                     quad.color(i, ARGB.multiply(quad.color(i), tint));
                  }
               }
            }

            if (xray) {
               int alpha = ModuleRenderUtil.xrayAlpha(this.blockState, this.pos);
               if (alpha == 0) {
                  cir.setReturnValue(false);
               } else if (alpha != -1) {
                  if (alpha > 0 && alpha < 255) {
                     quad.chunkLayer(ChunkSectionLayer.TRANSLUCENT);
                  }

                  int alphaBits = (alpha & 0xFF) << 24;

                  for (int i = 0; i < 4; i++) {
                     quad.color(i, alphaBits | quad.color(i) & 16777215);
                  }
               }
            }
         }
      }
   }
}
