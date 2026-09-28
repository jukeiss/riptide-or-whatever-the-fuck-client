package riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.modules.ScaffoldModule;
import riptide.util.RiptideSilentAim;
import riptide.util.macro.MacroRaycastAim;

@Mixin({LocalPlayer.class})
public class RiptideKillAuraRotationMixin {
   @ModifyExpressionValue(
      method = {"tick"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"
      )}
   )
   private float riptide$killAuraSilentYaw(float original) {
      LocalPlayer self = (LocalPlayer)this;
      return MacroRaycastAim.isActive() ? MacroRaycastAim.outgoingYaw(self, original) : RiptideSilentAim.outgoingMovementYaw(self, original);
   }

   @ModifyExpressionValue(
      method = {"tick"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"
      )}
   )
   private float riptide$killAuraSilentPitch(float original) {
      LocalPlayer self = (LocalPlayer)this;
      return MacroRaycastAim.isActive() ? MacroRaycastAim.outgoingPitch(self, original) : RiptideSilentAim.outgoingMovementPitch(self, original);
   }

   @ModifyExpressionValue(
      method = {"pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/Entity;getViewVector(F)Lnet/minecraft/world/phys/Vec3;"
      )}
   )
   private static Vec3 riptide$killAuraSilentViewVector(
      Vec3 original, Entity camera, double blockInteractionRange, double entityInteractionRange, float tickDelta
   ) {
      if (camera != Minecraft.getInstance().player) {
         return original;
      } else if (MacroRaycastAim.isActive()) {
         return MacroRaycastAim.viewVector((LocalPlayer)camera, original);
      } else {
         Vec3 scaffold = ScaffoldModule.silentViewVector((LocalPlayer)camera, original);
         return RiptideSilentAim.silentViewVector((LocalPlayer)camera, scaffold);
      }
   }
}
