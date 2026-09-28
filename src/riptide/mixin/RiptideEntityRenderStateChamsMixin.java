package riptide.mixin;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import riptide.util.RiptideChamsHolder;

@Mixin({EntityRenderState.class})
public class RiptideEntityRenderStateChamsMixin implements RiptideChamsHolder {
   @Unique
   private boolean riptide$active;
   @Unique
   private int riptide$visible;
   @Unique
   private int riptide$occluded;

   @Override
   public void riptide$setChams(boolean active, int visibleColor, int occludedColor) {
      this.riptide$active = active;
      this.riptide$visible = visibleColor;
      this.riptide$occluded = occludedColor;
   }

   @Override
   public boolean riptide$chamsActive() {
      return this.riptide$active;
   }

   @Override
   public int riptide$chamsVisible() {
      return this.riptide$visible;
   }

   @Override
   public int riptide$chamsOccluded() {
      return this.riptide$occluded;
   }
}
