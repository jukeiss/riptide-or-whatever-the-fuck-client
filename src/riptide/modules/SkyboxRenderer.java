package riptide.modules;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import riptide.util.RiptideBufferSource;
import riptide.util.RiptideTheme;
import riptide.util.RiptideThemeTextures;

public final class SkyboxRenderer {
   private static final RiptideBufferSource.Holder BUFFERS = new RiptideBufferSource.Holder(786432);
   private static final float[][] FACES = new float[][]{
      {-1.0F, -1.0F, 1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, -1.0F, 1.0F},
      {1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, -1.0F, 1.0F, -1.0F, -1.0F},
      {1.0F, -1.0F, -1.0F, 1.0F, 1.0F, -1.0F, -1.0F, 1.0F, -1.0F, -1.0F, -1.0F, -1.0F},
      {-1.0F, -1.0F, -1.0F, -1.0F, 1.0F, -1.0F, -1.0F, 1.0F, 1.0F, -1.0F, -1.0F, 1.0F},
      {-1.0F, 1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, -1.0F},
      {-1.0F, -1.0F, -1.0F, -1.0F, -1.0F, 1.0F, 1.0F, -1.0F, 1.0F, 1.0F, -1.0F, -1.0F}
   };
   private static final float[][] UVS = new float[][]{
      {0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F},
      {0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F},
      {0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F},
      {0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F},
      {0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F},
      {0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F}
   };
   private static final Identifier[] FACE_TEXTURES = new Identifier[]{
      face("panorama_0"), face("panorama_1"), face("panorama_2"), face("panorama_3"), face("panorama_5"), face("panorama_4")
   };
   private static final float SCALE = 64.0F;
   private static final float TILT_X = 32.0F;
   private static final float SPIN_DEGREES_PER_SECOND = 0.286F;
   private static float spinAngleDeg;
   private static long spinLastNanos;

   private SkyboxRenderer() {
   }

   public static boolean isActive() {
      if (PackHideState.isActive()) {
         return false;
      } else {
         WorldModule w = world();
         return w != null && w.isEnabled() && w.bool("skybox") && Minecraft.getInstance().level != null;
      }
   }

   public static void render(float partialTick) {
      Minecraft mc = Minecraft.getInstance();
      WorldModule w = world();
      if (mc.level != null && w != null) {
         boolean recolor = w.bool("skybox-recolor");
         int customColor = ModuleRenderUtil.color(w, "skybox-color", -50373);
         float speed = (float)Math.max(0.5, Math.min(3.0, w.decimal("skybox-speed")));
         float spin = spinAngle(w.bool("skybox-spin"), speed);
         Matrix4f rotation = new Matrix4f().rotationY(-spin * (float) (Math.PI / 180.0)).rotateX(3.700098F);
         Vector3f corner = new Vector3f();

         try {
            RiptideBufferSource bufferSource = BUFFERS.get();

            for (int face = 0; face < FACES.length; face++) {
               Identifier texture = recolor
                  ? RiptideThemeTextures.recoloredTo(FACE_TEXTURES[face], RiptideTheme.active().colorOf(RiptideTheme.Channel.BACKDROP))
                  : RiptideThemeTextures.recoloredTo(FACE_TEXTURES[face], customColor);
               if (texture != null) {
                  VertexConsumer buffer = bufferSource.getBuffer(RiptideRenderTypes.waypointDiscSeeThrough(texture));
                  float[] verts = FACES[face];
                  float[] uvs = UVS[face];

                  for (int i = 0; i < 4; i++) {
                     rotation.transformPosition(verts[i * 3], verts[i * 3 + 1], verts[i * 3 + 2], corner);
                     buffer.addVertex(corner.x * 64.0F, corner.y * 64.0F, corner.z * 64.0F).setUv(uvs[i * 2], uvs[i * 2 + 1]).setColor(-1);
                  }
               }
            }

            bufferSource.uploadAndDraw();
         } catch (Throwable var16) {
         }
      }
   }

   private static float spinAngle(boolean spinning, float speed) {
      long now = System.nanoTime();
      if (spinLastNanos == 0L) {
         spinLastNanos = now;
      }

      float dt = (float)(now - spinLastNanos) / 1.0E9F;
      spinLastNanos = now;
      if (spinning && dt > 0.0F) {
         spinAngleDeg = (spinAngleDeg + Math.min(dt, 0.25F) * 0.286F * speed) % 360.0F;
      }

      return spinning ? spinAngleDeg : 0.0F;
   }

   private static Identifier face(String name) {
      return Identifier.fromNamespaceAndPath("riptide", "textures/gui/title/background/" + name + ".png");
   }

   private static WorldModule world() {
      return ModuleRegistry.get("world") instanceof WorldModule w ? w : null;
   }
}
