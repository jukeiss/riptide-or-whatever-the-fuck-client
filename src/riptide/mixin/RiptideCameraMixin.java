package riptide.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.FreeLookModule;
import riptide.modules.ModuleRenderUtil;
import riptide.modules.PackFreecamState;

@Mixin({Camera.class})
public abstract class RiptideCameraMixin {
   @Shadow
   private boolean detached;
   @Shadow
   private Entity entity;

   @Shadow
   protected abstract void setPosition(Vec3 var1);

   @Shadow
   protected abstract void setRotation(float var1, float var2);

   @Shadow
   protected abstract void move(float var1, float var2, float var3);

   @Shadow
   protected abstract float getMaxZoom(float var1);

   @Inject(
      method = {"update(Lnet/minecraft/client/DeltaTracker;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V",
         shift = Shift.AFTER
      )}
   )
   private void riptide$freecamCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
      if (PackFreecamState.isActive()) {
         float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
         this.detached = true;
         this.setPosition(new Vec3(PackFreecamState.getX(partialTicks), PackFreecamState.getY(partialTicks), PackFreecamState.getZ(partialTicks)));
         this.setRotation(PackFreecamState.getYaw(partialTicks), PackFreecamState.getPitch(partialTicks));
      }
   }

   @Inject(
      method = {"update(Lnet/minecraft/client/DeltaTracker;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V",
         shift = Shift.AFTER
      )}
   )
   private void riptide$freeLookCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
      FreeLookModule freeLook = FreeLookModule.lookingInstance();
      if (freeLook != null && !PackFreecamState.isActive()) {
         float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
         this.detached = true;
         this.setPosition(
            new Vec3(
               Mth.lerp(partialTicks, this.entity.xo, this.entity.getX()),
               Mth.lerp(partialTicks, this.entity.yo, this.entity.getY()) + this.entity.getEyeHeight(),
               Mth.lerp(partialTicks, this.entity.zo, this.entity.getZ())
            )
         );
         if (freeLook.isInvertedView()) {
            this.setRotation(freeLook.cameraYaw() + 180.0F, -freeLook.cameraPitch());
         } else {
            this.setRotation(freeLook.cameraYaw(), freeLook.cameraPitch());
         }

         float scale = this.entity instanceof LivingEntity living ? living.getScale() : 1.0F;
         this.move(-this.getMaxZoom(4.0F * scale), 0.0F, 0.0F);
      }
   }

   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void riptide$disableSmartCull(CameraRenderState cameraState, float cameraEntityPartialTicks, CallbackInfo ci) {
      if (ModuleRenderUtil.shouldBypassOcclusionCulling()) {
         cameraState.smartCull = false;
      }
   }
}
