package riptide.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.util.multi.MultiPilot;
import riptide.util.multi.MultiTakeoverState;

@Mixin({RemotePlayer.class})
public abstract class RiptideBotPilotMixin extends AbstractClientPlayer {
   protected RiptideBotPilotMixin(ClientLevel level, GameProfile profile) {
      super(level, profile);
   }

   protected boolean isLocalClientAuthoritative() {
      return MultiPilot.isManualControlEntity(this) || super.isLocalClientAuthoritative();
   }

   public boolean canSimulateMovement() {
      return MultiPilot.isManualControlEntity(this) || super.canSimulateMovement();
   }

   public boolean isEffectiveAi() {
      return MultiPilot.isManualControlEntity(this) || super.isEffectiveAi();
   }

   @Inject(
      method = {"aiStep"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$pilotAiStep(CallbackInfo ci) {
      if (MultiPilot.isPilotedEntity(this)) {
         if (!MultiPilot.isManualControlEntity(this)) {
            MultiPilot.observeMacro((RemotePlayer)this);
         } else {
            try {
               if (MultiPilot.prePhysics((RemotePlayer)this)) {
                  this.jumping = MultiPilot.jumpRequested();
                  super.aiStep();
                  MultiPilot.postPhysics((RemotePlayer)this);
               } else {
                  MultiPilot.passiveTick((RemotePlayer)this);
               }

               ci.cancel();
            } catch (Throwable var5) {
               riptide.RiptideClientAddon.LOG.error("POV pilot tick failed - leaving POV", var5);

               try {
                  MultiPilot.abortSimulation((RemotePlayer)this);
                  MultiTakeoverState.exit();
               } catch (Throwable var4) {
               }
            }
         }
      }
   }

   @WrapWithCondition(
      method = {"tick"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/RemotePlayer;calculateEntityAnimation(Z)V"
      )}
   )
   private boolean riptide$skipDoubleAnimation(RemotePlayer self, boolean includeHeight) {
      return !MultiPilot.isManualControlEntity(self);
   }
}
