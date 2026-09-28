package riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.modules.ModuleMovementUtil;

@Mixin({LivingEntity.class})
public abstract class RiptideSprintJumpMixin {
   @ModifyExpressionValue(
      method = {"jumpFromGround"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"
      )}
   )
   private float riptide$omniSprintJumpYaw(float original) {
      return this instanceof LocalPlayer player && ModuleMovementUtil.sprintJumpUsesMovementYaw() ? ModuleMovementUtil.movementDirectionYaw(player) : original;
   }
}
