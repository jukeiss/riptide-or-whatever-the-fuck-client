package riptide.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public final class RiptideChamsRenderQueue {
   private static final List<RiptideChamsRenderQueue.BodySubmit> BODIES = new ArrayList<>();
   private static final List<RiptideChamsRenderQueue.ModelSubmit> LAYERS = new ArrayList<>();
   private static final RiptideBufferSource.Holder BUFFERS = new RiptideBufferSource.Holder(4194304);

   private RiptideChamsRenderQueue() {
   }

   public static void clear() {
      BODIES.clear();
      LAYERS.clear();
   }

   public static boolean hasPending() {
      return !BODIES.isEmpty();
   }

   public static void submitBody(
      Model<?> model,
      Object state,
      Pose pose,
      RenderType visibleType,
      RenderType occludedType,
      int light,
      int overlay,
      int visibleColor,
      int occludedColor,
      TextureAtlasSprite sprite
   ) {
      BODIES.add(new RiptideChamsRenderQueue.BodySubmit(model, state, pose, visibleType, occludedType, light, overlay, visibleColor, occludedColor, sprite));
   }

   public static void submitLayer(Model<?> model, Object state, Pose pose, RenderType type, int light, int overlay, int color, TextureAtlasSprite sprite) {
      LAYERS.add(new RiptideChamsRenderQueue.ModelSubmit(model, state, pose, type, light, overlay, color, sprite));
   }

   public static void flush(PoseStack framePose) {
      if (!BODIES.isEmpty()) {
         List<RiptideChamsRenderQueue.BodySubmit> bodies = List.copyOf(BODIES);
         List<RiptideChamsRenderQueue.ModelSubmit> layers = List.copyOf(LAYERS);
         clear();
         RiptideBufferSource buffers = BUFFERS.get();

         for (RiptideChamsRenderQueue.BodySubmit body : bodies) {
            draw(
               buffers,
               framePose,
               body.model(),
               body.state(),
               body.pose(),
               body.occludedType(),
               body.light(),
               body.overlay(),
               body.occludedColor(),
               body.sprite()
            );
         }

         for (RiptideChamsRenderQueue.BodySubmit body : bodies) {
            draw(
               buffers,
               framePose,
               body.model(),
               body.state(),
               body.pose(),
               body.visibleType(),
               body.light(),
               body.overlay(),
               body.visibleColor(),
               body.sprite()
            );
         }

         for (RiptideChamsRenderQueue.ModelSubmit layer : layers) {
            draw(buffers, framePose, layer.model(), layer.state(), layer.pose(), layer.type(), layer.light(), layer.overlay(), layer.color(), layer.sprite());
         }

         buffers.uploadAndDraw();
      }
   }

   private static void draw(
      RiptideBufferSource buffers,
      PoseStack framePose,
      Model<?> model,
      Object state,
      Pose modelPose,
      RenderType type,
      int light,
      int overlay,
      int color,
      TextureAtlasSprite sprite
   ) {
      framePose.pushPose();

      try {
         framePose.mulPose(modelPose.pose());
         VertexConsumer consumer = buffers.getBuffer(type);
         if (sprite != null) {
            consumer = sprite.wrap(consumer);
         }

         model.setupAnim(state);
         model.renderToBuffer(framePose, consumer, light, overlay, color);
      } finally {
         framePose.popPose();
      }
   }

   private record BodySubmit(
      Model<?> model,
      Object state,
      Pose pose,
      RenderType visibleType,
      RenderType occludedType,
      int light,
      int overlay,
      int visibleColor,
      int occludedColor,
      TextureAtlasSprite sprite
   ) {
   }

   private record ModelSubmit(Model<?> model, Object state, Pose pose, RenderType type, int light, int overlay, int color, TextureAtlasSprite sprite) {
   }
}
