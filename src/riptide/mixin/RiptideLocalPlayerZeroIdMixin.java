package riptide.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Entity.class})
public abstract class RiptideLocalPlayerZeroIdMixin {
   @Unique
   private boolean riptide$localPlayerZeroIdAssigned;

   @Inject(
      method = {"setId"},
      at = {@At("HEAD")}
   )
   private void riptide$rememberExplicitLocalPlayerId(int id, CallbackInfo ci) {
      Minecraft minecraft = Minecraft.getInstance();
      this.riptide$localPlayerZeroIdAssigned = id == 0 && minecraft != null && minecraft.player == this;
   }

   @Inject(
      method = {"getId"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$allowExplicitLocalPlayerZeroId(CallbackInfoReturnable<Integer> cir) {
      if (this.riptide$localPlayerZeroIdAssigned) {
         Minecraft minecraft = Minecraft.getInstance();
         if (minecraft != null && minecraft.player == this) {
            cir.setReturnValue(0);
         }
      }
   }
}
