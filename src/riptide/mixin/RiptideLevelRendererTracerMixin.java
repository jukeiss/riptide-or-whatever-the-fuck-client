package riptide.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.ModuleWorldRenderer;
import riptide.util.RiptideChamsRenderQueue;
import riptide.util.RiptideSkeletonRenderer;

@Mixin({LevelRenderer.class})
public class RiptideLevelRendererTracerMixin {
   @Inject(
      method = {"render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"},
      at = {@At("HEAD")}
   )
   private void riptide$beginDelayedOverlays(
      GraphicsResourceAllocator allocator,
      DeltaTracker tickCounter,
      boolean renderBlockOutline,
      CameraRenderState cameraState,
      Matrix4fc positionMatrix,
      GpuBufferSlice gpuBufferSlice,
      Vector4f vector4f,
      boolean shouldRenderSky,
      CallbackInfo ci
   ) {
      RiptideChamsRenderQueue.clear();
   }

   @Inject(
      method = {"render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"},
      at = {@At("RETURN")}
   )
   private void riptide$drawTracers(
      GraphicsResourceAllocator allocator,
      DeltaTracker tickCounter,
      boolean renderBlockOutline,
      CameraRenderState cameraState,
      Matrix4fc positionMatrix,
      GpuBufferSlice gpuBufferSlice,
      Vector4f vector4f,
      boolean shouldRenderSky,
      CallbackInfo ci
   ) {
      boolean chams = RiptideChamsRenderQueue.hasPending();
      boolean tracers = ModuleWorldRenderer.hasPendingTracers();
      boolean esp = ModuleWorldRenderer.hasPendingEspWork();
      boolean waypoints = ModuleWorldRenderer.hasPendingWaypointArt();
      boolean skeleton = RiptideSkeletonRenderer.hasPending();
      if (chams || tracers || esp || waypoints || skeleton) {
         PoseStack matrixStack = new PoseStack();
         matrixStack.mulPose(positionMatrix);
         if (chams) {
            RiptideChamsRenderQueue.flush(matrixStack);
         }

         if (tracers) {
            ModuleWorldRenderer.flushTracers(matrixStack);
         }

         if (esp) {
            ModuleWorldRenderer.flushEsp(matrixStack);
         }

         if (skeleton) {
            RiptideSkeletonRenderer.flush(matrixStack);
         }

         if (waypoints) {
            ModuleWorldRenderer.flushWaypointArt(matrixStack);
         }
      }
   }
}
