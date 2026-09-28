package riptide.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.ModuleRenderUtil;
import riptide.util.RiptideServerRotationView;
import riptide.util.multi.MultiPilot;

@Mixin({EntityRenderDispatcher.class})
public class RiptideLevelRendererEntityMixin {
   @Inject(
      method = {"extractEntity"},
      at = {@At("RETURN")}
   )
   private void riptide$espEntityOutline(Entity entity, float partialTickTime, CallbackInfoReturnable<EntityRenderState> cir) {
      EntityRenderState state = (EntityRenderState)cir.getReturnValue();
      if (state != null) {
         RiptideServerRotationView.applyLocalPlayerPose(entity, state, partialTickTime);
         MultiPilot.applyEntityRenderTruth(entity, state, partialTickTime);
         if (ModuleRenderUtil.hasChamsWork()) {
            ModuleRenderUtil.applyChams(entity, state);
         }

         if (ModuleRenderUtil.hasAnyOutlineWork()) {
            int itemOutline = ModuleRenderUtil.itemOutlineColorOrZero(entity);
            if (itemOutline != 0) {
               state.outlineColor = itemOutline;
            } else {
               int entityOutline = ModuleRenderUtil.entityOutlineColorOrZero(entity);
               if (entityOutline != 0) {
                  state.outlineColor = entityOutline;
               }
            }
         }
      }
   }
}
