package riptide.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.NoClipModule;

@Mixin({Player.class})
public class RiptidePlayerNoPhysicsMixin {
   @Inject(
      method = {"tick"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/player/Player;isSpectator()Z",
         ordinal = 1,
         shift = Shift.BEFORE
      )}
   )
   private void riptide$noClipNoPhysics(CallbackInfo ci) {
      Player self = (Player)this;
      if (self == Minecraft.getInstance().player) {
         if (!self.noPhysics && NoClipModule.holdsNoPhysics()) {
            self.noPhysics = true;
         }
      }
   }
}
