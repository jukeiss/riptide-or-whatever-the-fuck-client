package riptide.mixin;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.NameCensorModule;

@Mixin({PlayerInfo.class})
public class RiptidePlayerInfoSkinMixin {
   @Inject(
      method = {"getSkin"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$defaultSkin(CallbackInfoReturnable<PlayerSkin> cir) {
      PlayerInfo self = (PlayerInfo)this;
      if (NameCensorModule.shouldDisableSkinFor(self.getProfile())) {
         cir.setReturnValue(DefaultPlayerSkin.get(self.getProfile()));
      }
   }
}
