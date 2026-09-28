package riptide.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.ModuleMovementUtil;
import riptide.modules.PackFreecamState;
import riptide.util.RiptideRemoteView;
import riptide.util.multi.MultiPilot;

@Mixin({Entity.class})
public class RiptideEntityMovementMixin {
   @ModifyVariable(
      method = {"move"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private Vec3 riptide$modifyLocalPlayerMovement(Vec3 movement, MoverType type) {
      return ModuleMovementUtil.onPlayerMove((Entity)this, type, movement);
   }

   @Redirect(
      method = {"getBlockBounciness"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/block/Block;getBounceRestitution()F"
      )
   )
   private float riptide$antiBounceRestitution(Block block) {
      Entity entity = (Entity)this;
      return block instanceof SlimeBlock && ModuleMovementUtil.shouldCancelNoFallBounce(entity) ? 0.0F : block.getBounceRestitution();
   }

   @Inject(
      method = {"turn"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$freecamTurn(double deltaYaw, double deltaPitch, CallbackInfo ci) {
      if (this == Minecraft.getInstance().player) {
         if (PackFreecamState.isActive()) {
            PackFreecamState.turn(deltaYaw, deltaPitch);
            ci.cancel();
         } else if (MultiPilot.isActive()) {
            MultiPilot.handleTurn(deltaYaw, deltaPitch);
            ci.cancel();
         } else {
            if (RiptideRemoteView.isActive()) {
               ci.cancel();
            }
         }
      }
   }
}
