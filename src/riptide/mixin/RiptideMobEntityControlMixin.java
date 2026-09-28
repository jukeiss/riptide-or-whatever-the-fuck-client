package riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.modules.EntityControlModule;

@Mixin({Mob.class})
public abstract class RiptideMobEntityControlMixin {
   @ModifyReturnValue(
      method = {"isSaddled"},
      at = {@At("RETURN")}
   )
   private boolean riptide$entityControlSaddle(boolean original) {
      return original || EntityControlModule.shouldSpoofSaddle((Mob)this);
   }
}
