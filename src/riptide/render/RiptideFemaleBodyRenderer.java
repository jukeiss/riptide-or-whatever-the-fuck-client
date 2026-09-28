package riptide.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelPart.Cube;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.model.EquipmentClientInfo.LayerType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.joml.Vector3f;
import riptide.modules.GoldenLeverModule;
import riptide.modules.PackHideState;

public final class RiptideFemaleBodyRenderer {
   private static final float ARM_WIDTH_SCALE = 0.78F;
   private static final Set<PlayerModel> MAIN_MODELS = Collections.newSetFromMap(new IdentityHashMap<>());
   private static final Set<PlayerModel> ARMOR_MODELS = Collections.newSetFromMap(new IdentityHashMap<>());
   private static final RiptideFemaleBodyRenderer.MeshSet FULL_MESHES = RiptideFemaleBodyRenderer.MeshSet.build(
      RiptideFemaleBodyRenderer.MeshBuilder.Detail.FULL
   );
   private static final RiptideFemaleBodyRenderer.MeshSet MEDIUM_MESHES = RiptideFemaleBodyRenderer.MeshSet.build(
      RiptideFemaleBodyRenderer.MeshBuilder.Detail.MEDIUM
   );
   private static final RiptideFemaleBodyRenderer.MeshSet FAR_MESHES = RiptideFemaleBodyRenderer.MeshSet.build(RiptideFemaleBodyRenderer.MeshBuilder.Detail.FAR);
   private static final RiptideFemaleBodyRenderer.MeshSet CROWD_MESHES = RiptideFemaleBodyRenderer.MeshSet.build(
      RiptideFemaleBodyRenderer.MeshBuilder.Detail.CROWD
   );
   private static final double FULL_DETAIL_DISTANCE_SQ = 36.0;
   private static final double MEDIUM_DETAIL_DISTANCE_SQ = 324.0;
   private static final double CROWD_SAMPLE_DISTANCE_SQ = 1024.0;
   private static final int CROWD_DETAIL_PLAYER_COUNT = 24;
   private static final Map<Identifier, RiptideFemaleBodyRenderer.JacketAlpha> JACKET_ALPHA_CACHE = new HashMap<>();
   private static Object crowdSampleLevel;
   private static long crowdSampleTick = Long.MIN_VALUE;
   private static int nearbyPlayerCount;
   private static boolean initialized;
   private static volatile boolean layerReady;

   private RiptideFemaleBodyRenderer() {
   }

   public static void initialize() {
      if (!initialized) {
         initialized = true;
         LivingEntityRenderLayerRegistrationCallback.EVENT.register((LivingEntityRenderLayerRegistrationCallback)(entityType, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatarRenderer) {
               PlayerModel playerModel = (PlayerModel)avatarRenderer.getModel();
               MAIN_MODELS.add(playerModel);
               helper.register(new RiptideFemaleBodyRenderer.FemaleBodyLayer(avatarRenderer, context.getEquipmentRenderer()));
            }
         });
      }
   }

   public static void markArmorModels(ArmorModelSet<?> modelSet) {
      if (modelSet != null) {
         markArmorModel(modelSet.head());
         markArmorModel(modelSet.chest());
         markArmorModel(modelSet.legs());
         markArmorModel(modelSet.feet());
      }
   }

   private static void markArmorModel(Object model) {
      if (model instanceof PlayerModel playerModel) {
         ARMOR_MODELS.add(playerModel);
      }
   }

   public static void applyModelVisibility(PlayerModel model, AvatarRenderState state) {
      if (model != null && state != null && layerReady && GoldenLeverModule.shouldApplyFemaleBody(state.id) && !PackHideState.isHardLocked()) {
         model.leftArm.xScale = 0.78F;
         model.rightArm.xScale = 0.78F;
         if (MAIN_MODELS.contains(model)) {
            model.body.visible = false;
            model.jacket.visible = false;
         } else if (ARMOR_MODELS.contains(model)) {
            model.body.visible = false;
         }
      }
   }

   public static void compensateHeldItemArmScale(AvatarRenderState state, PoseStack poseStack) {
      if (state != null && poseStack != null && GoldenLeverModule.shouldApplyFemaleBody(state.id)) {
         poseStack.scale(1.2820513F, 1.0F, 1.0F);
      }
   }

   private static final class FemaleBodyLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
      private final EquipmentLayerRenderer equipmentRenderer;

      private FemaleBodyLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EquipmentLayerRenderer equipmentRenderer) {
         super(renderer);
         this.equipmentRenderer = equipmentRenderer;
      }

      public void submit(PoseStack poseStack, SubmitNodeCollector output, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
         if (GoldenLeverModule.shouldApplyFemaleBody(state.id) && !PackHideState.isHardLocked() && !state.isSpectator) {
            RiptideFemaleBodyRenderer.layerReady = true;
            poseStack.pushPose();
            PlayerModel parent = (PlayerModel)this.getParentModel();
            parent.root().translateAndRotate(poseStack);
            parent.body.translateAndRotate(poseStack);
            RiptideFemaleBodyRenderer.MeshSet meshes = meshesFor(state);
            renderSkin(meshes, state, poseStack, output, lightCoords);
            this.renderArmorPiece(state.chestEquipment, EquipmentSlot.CHEST, LayerType.HUMANOID, meshes.chestArmor, state, poseStack, output, lightCoords, 3);
            this.renderArmorPiece(
               state.legsEquipment, EquipmentSlot.LEGS, LayerType.HUMANOID_LEGGINGS, meshes.leggingsArmor, state, poseStack, output, lightCoords, 8
            );
            poseStack.popPose();
         }
      }

      private static void renderSkin(
         RiptideFemaleBodyRenderer.MeshSet meshes, AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector output, int lightCoords
      ) {
         Identifier texture = state.skin.body().texturePath();
         int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
         RiptideFemaleBodyRenderer.JacketAlpha jacketAlpha = state.showJacket ? jacketAlpha(texture) : RiptideFemaleBodyRenderer.JacketAlpha.EMPTY;
         if (!state.isInvisible) {
            if (meshes == RiptideFemaleBodyRenderer.CROWD_MESHES) {
               RiptideFemaleBodyRenderer.MeshModel crowdMesh = jacketAlpha == RiptideFemaleBodyRenderer.JacketAlpha.EMPTY ? meshes.body : meshes.bodyWithJacket;
               output.order(0)
                  .submitModel(crowdMesh, state, poseStack, RenderTypes.entityTranslucent(texture), lightCoords, overlay, -1, null, state.outlineColor, null);
            } else {
               output.order(0)
                  .submitModel(meshes.body, state, poseStack, RenderTypes.entitySolid(texture), lightCoords, overlay, -1, null, state.outlineColor, null);
               if (jacketAlpha != RiptideFemaleBodyRenderer.JacketAlpha.EMPTY) {
                  RenderType jacketRenderType = switch (jacketAlpha) {
                     case UNKNOWN, TRANSLUCENT -> RiptideRenderTypes.femaleBodyTranslucentCull(texture);
                     case EMPTY -> throw new IllegalStateException("Empty jacket submitted");
                     case OPAQUE -> RenderTypes.entitySolid(texture);
                     case CUTOUT -> RenderTypes.entityCutoutCull(texture);
                  };
                  output.order(0).submitModel(meshes.jacket, state, poseStack, jacketRenderType, lightCoords, overlay, -1, null, state.outlineColor, null);
               }
            }
         } else {
            RiptideFemaleBodyRenderer.MeshModel visibleMesh = jacketAlpha == RiptideFemaleBodyRenderer.JacketAlpha.EMPTY ? meshes.body : meshes.bodyWithJacket;
            if (!state.isInvisibleToPlayer) {
               output.order(0)
                  .submitModel(
                     visibleMesh,
                     state,
                     poseStack,
                     RiptideRenderTypes.femaleBodyTranslucentCull(texture),
                     lightCoords,
                     overlay,
                     654311423,
                     null,
                     state.outlineColor,
                     null
                  );
            } else if (state.appearsGlowing()) {
               output.order(0)
                  .submitModel(visibleMesh, state, poseStack, RenderTypes.outline(texture), lightCoords, overlay, -1, null, state.outlineColor, null);
            }
         }
      }

      private static RiptideFemaleBodyRenderer.MeshSet meshesFor(AvatarRenderState state) {
         Minecraft minecraft = Minecraft.getInstance();
         if (minecraft.player != null && minecraft.player.getId() != state.id && sampleNearbyPlayerCount(minecraft) >= 24) {
            return RiptideFemaleBodyRenderer.CROWD_MESHES;
         } else {
            double distanceToCameraSq = state.distanceToCameraSq;
            if (distanceToCameraSq <= 36.0) {
               return RiptideFemaleBodyRenderer.FULL_MESHES;
            } else {
               return distanceToCameraSq <= 324.0 ? RiptideFemaleBodyRenderer.MEDIUM_MESHES : RiptideFemaleBodyRenderer.FAR_MESHES;
            }
         }
      }

      private static int sampleNearbyPlayerCount(Minecraft minecraft) {
         if (minecraft.level != null && minecraft.player != null) {
            long tick = minecraft.level.getGameTime();
            if (minecraft.level == RiptideFemaleBodyRenderer.crowdSampleLevel && tick == RiptideFemaleBodyRenderer.crowdSampleTick) {
               return RiptideFemaleBodyRenderer.nearbyPlayerCount;
            } else {
               RiptideFemaleBodyRenderer.crowdSampleLevel = minecraft.level;
               RiptideFemaleBodyRenderer.crowdSampleTick = tick;
               int count = 0;

               for (AbstractClientPlayer player : minecraft.level.players()) {
                  if (player.distanceToSqr(minecraft.player) <= 1024.0) {
                     count++;
                  }
               }

               RiptideFemaleBodyRenderer.nearbyPlayerCount = count;
               return count;
            }
         } else {
            return 0;
         }
      }

      private static RiptideFemaleBodyRenderer.JacketAlpha jacketAlpha(Identifier textureId) {
         RiptideFemaleBodyRenderer.JacketAlpha cached = RiptideFemaleBodyRenderer.JACKET_ALPHA_CACHE.get(textureId);
         if (cached != null) {
            return cached;
         } else if (!(Minecraft.getInstance().getTextureManager().getTexture(textureId) instanceof DynamicTexture dynamicTexture)) {
            RiptideFemaleBodyRenderer.JACKET_ALPHA_CACHE.put(textureId, RiptideFemaleBodyRenderer.JacketAlpha.UNKNOWN);
            return RiptideFemaleBodyRenderer.JacketAlpha.UNKNOWN;
         } else {
            NativeImage pixels = dynamicTexture.getPixels();
            if (pixels != null && !pixels.isClosed() && pixels.getWidth() >= 64 && pixels.getHeight() >= 64) {
               boolean hasTransparent = false;
               boolean hasVisible = false;
               boolean hasPartial = false;

               for (int y = 32; y < 48; y++) {
                  for (int x = 16; x < 40; x++) {
                     int alpha = ARGB.alpha(pixels.getPixel(x, y));
                     hasTransparent |= alpha == 0;
                     hasVisible |= alpha != 0;
                     hasPartial |= alpha > 0 && alpha < 255;
                  }
               }

               RiptideFemaleBodyRenderer.JacketAlpha result;
               if (!hasVisible) {
                  result = RiptideFemaleBodyRenderer.JacketAlpha.EMPTY;
               } else if (hasPartial) {
                  result = RiptideFemaleBodyRenderer.JacketAlpha.TRANSLUCENT;
               } else if (hasTransparent) {
                  result = RiptideFemaleBodyRenderer.JacketAlpha.CUTOUT;
               } else {
                  result = RiptideFemaleBodyRenderer.JacketAlpha.OPAQUE;
               }

               RiptideFemaleBodyRenderer.JACKET_ALPHA_CACHE.put(textureId, result);
               return result;
            } else {
               return RiptideFemaleBodyRenderer.JacketAlpha.UNKNOWN;
            }
         }
      }

      private void renderArmorPiece(
         ItemStack stack,
         EquipmentSlot expectedSlot,
         LayerType layerType,
         RiptideFemaleBodyRenderer.MeshModel model,
         AvatarRenderState state,
         PoseStack poseStack,
         SubmitNodeCollector output,
         int lightCoords,
         int order
      ) {
         if (stack != null && !stack.isEmpty()) {
            Equippable equippable = (Equippable)stack.get(DataComponents.EQUIPPABLE);
            if (equippable != null && equippable.slot() == expectedSlot && !equippable.assetId().isEmpty()) {
               this.equipmentRenderer
                  .renderLayers(
                     layerType,
                     (ResourceKey)equippable.assetId().orElseThrow(),
                     model,
                     state,
                     stack,
                     poseStack,
                     output,
                     lightCoords,
                     state.skin.body().texturePath(),
                     state.outlineColor,
                     order
                  );
            }
         }
      }
   }

   private static enum JacketAlpha {
      UNKNOWN,
      EMPTY,
      OPAQUE,
      CUTOUT,
      TRANSLUCENT;
   }

   private static final class MeshBuilder {
      private static final int FRONT_COLUMNS = 29;
      private static final int[] FLAT_COLUMNS = new int[]{0, 1, 27, 28};
      private static final float[] ROW_Y = new float[]{
         0.0F, 0.5F, 1.0F, 1.5F, 2.0F, 2.5F, 3.0F, 3.5F, 4.0F, 4.5F, 5.1F, 5.5F, 5.8F, 6.15F, 6.45F, 6.7F, 7.0F, 8.5F, 12.0F
      };
      private static final float[] SHAPE_Y = new float[]{0.0F, 1.0F, 2.5F, 4.8F, 6.15F, 8.5F, 12.0F};
      private static final float[] HALF_WIDTH = new float[]{4.4F, 4.66F, 4.78F, 4.55F, 4.2F, 3.35F, 4.2F};
      private static final float[] FRONT_DEPTH = new float[]{-2.02F, -2.05F, -2.08F, -2.03F, -2.0F, -1.94F, -2.05F};
      private static final float[] BACK_DEPTH = new float[]{2.0F, 2.02F, 2.04F, 2.02F, 2.0F, 1.94F, 2.05F};
      private static final float BREAST_CENTER_X = 1.55F;
      private static final float BREAST_CENTER_Y = 2.55F;
      private static final float BREAST_INNER_RADIUS_X = 2.65F;
      private static final float BREAST_OUTER_RADIUS_X = 3.4F;
      private static final float BREAST_UPPER_RADIUS_Y = 2.85F;
      private static final float BREAST_LOWER_RADIUS_Y = 4.05F;
      private static final float BREAST_DEPTH = 2.7F;
      private static final double BLEND_POWER = 6.0;

      private static RiptideFemaleBodyRenderer.MeshModel build(
         RiptideFemaleBodyRenderer.UvLayout uv, float shellOffset, RiptideFemaleBodyRenderer.MeshBuilder.Detail detail
      ) {
         return model(List.of(new RiptideFemaleBodyRenderer.MeshBuilder.MeshCube(buildQuads(uv, shellOffset, detail))));
      }

      private static RiptideFemaleBodyRenderer.MeshModel buildCombined(
         RiptideFemaleBodyRenderer.UvLayout firstUv,
         float firstOffset,
         RiptideFemaleBodyRenderer.UvLayout secondUv,
         float secondOffset,
         RiptideFemaleBodyRenderer.MeshBuilder.Detail detail
      ) {
         return model(
            List.of(
               new RiptideFemaleBodyRenderer.MeshBuilder.MeshCube(buildQuads(firstUv, firstOffset, detail)),
               new RiptideFemaleBodyRenderer.MeshBuilder.MeshCube(buildQuads(secondUv, secondOffset, detail))
            )
         );
      }

      private static RiptideFemaleBodyRenderer.MeshModel model(List<Cube> cubes) {
         return new RiptideFemaleBodyRenderer.MeshModel(new ModelPart(cubes, Map.of()));
      }

      private static List<RiptideFemaleBodyRenderer.MeshBuilder.Quad> buildQuads(
         RiptideFemaleBodyRenderer.UvLayout uv, float shellOffset, RiptideFemaleBodyRenderer.MeshBuilder.Detail detail
      ) {
         RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint[][] front = buildSurface(true, shellOffset);
         RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint[][] back = buildSurface(false, shellOffset);
         List<RiptideFemaleBodyRenderer.MeshBuilder.Quad> quads = new ArrayList<>(640);

         for (int segment = 0; segment < detail.rows.length - 1; segment++) {
            int topRow = detail.rows[segment];
            int bottomRow = detail.rows[segment + 1];
            int[] frontColumns = intersectsChest(ROW_Y[topRow], ROW_Y[bottomRow]) ? detail.columns : FLAT_COLUMNS;
            addFrontRow(quads, front, topRow, bottomRow, frontColumns, uv);
            addBackRow(quads, back, topRow, bottomRow, uv);
            addSideQuad(quads, front, back, topRow, bottomRow, false, uv);
            addSideQuad(quads, front, back, topRow, bottomRow, true, uv);
         }

         addCapQuads(quads, front, back, detail.rows[0], false, detail.columns, uv);
         addCapQuads(quads, front, back, detail.rows[detail.rows.length - 1], true, FLAT_COLUMNS, uv);
         return List.copyOf(quads);
      }

      private static void addFrontRow(
         List<RiptideFemaleBodyRenderer.MeshBuilder.Quad> quads,
         RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint[][] surface,
         int topRow,
         int bottomRow,
         int[] columns,
         RiptideFemaleBodyRenderer.UvLayout uv
      ) {
         for (int segment = 0; segment < columns.length - 1; segment++) {
            int left = columns[segment];
            int right = columns[segment + 1];
            quads.add(
               quad(
                  meshVertex(surface[topRow][right], frontU(right), frontV(topRow), uv),
                  meshVertex(surface[topRow][left], frontU(left), frontV(topRow), uv),
                  meshVertex(surface[bottomRow][left], frontU(left), frontV(bottomRow), uv),
                  meshVertex(surface[bottomRow][right], frontU(right), frontV(bottomRow), uv)
               )
            );
         }
      }

      private static void addBackRow(
         List<RiptideFemaleBodyRenderer.MeshBuilder.Quad> quads,
         RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint[][] surface,
         int topRow,
         int bottomRow,
         RiptideFemaleBodyRenderer.UvLayout uv
      ) {
         for (int segment = 0; segment < FLAT_COLUMNS.length - 1; segment++) {
            int left = FLAT_COLUMNS[segment];
            int right = FLAT_COLUMNS[segment + 1];
            quads.add(
               quad(
                  meshVertex(surface[topRow][left], backU(left), frontV(topRow), uv),
                  meshVertex(surface[topRow][right], backU(right), frontV(topRow), uv),
                  meshVertex(surface[bottomRow][right], backU(right), frontV(bottomRow), uv),
                  meshVertex(surface[bottomRow][left], backU(left), frontV(bottomRow), uv)
               )
            );
         }
      }

      private static boolean intersectsChest(float topY, float bottomY) {
         return bottomY > -0.29999995F && topY < 6.6000004F;
      }

      private static RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint[][] buildSurface(boolean frontSurface, float shellOffset) {
         RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint[][] base = new RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint[ROW_Y.length][29];

         for (int row = 0; row < ROW_Y.length; row++) {
            float y = ROW_Y[row];
            float halfWidth = interpolate(y, HALF_WIDTH);
            float baseDepth = interpolate(y, frontSurface ? FRONT_DEPTH : BACK_DEPTH);

            for (int column = 0; column < 29; column++) {
               float factor = factor(column);
               float x = factor * halfWidth;
               float z = frontSurface ? baseDepth - breastProjection(x, y) : baseDepth;
               base[row][column] = new RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint(x, y, z, new Vector3f());
            }
         }

         calculateSmoothNormals(base, frontSurface);
         if (shellOffset == 0.0F) {
            return base;
         } else {
            RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint[][] expanded = new RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint[ROW_Y.length][29];

            for (int row = 0; row < ROW_Y.length; row++) {
               for (int column = 0; column < 29; column++) {
                  RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint point = base[row][column];
                  Vector3f normal = point.normal;
                  expanded[row][column] = new RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint(
                     point.x + normal.x() * shellOffset, point.y + normal.y() * shellOffset, point.z + normal.z() * shellOffset, new Vector3f(normal)
                  );
               }
            }

            return expanded;
         }
      }

      private static void calculateSmoothNormals(RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint[][] surface, boolean frontSurface) {
         int lastRow = surface.length - 1;
         int lastColumn = surface[0].length - 1;

         for (int row = 0; row <= lastRow; row++) {
            int previousRow = Math.max(0, row - 1);
            int nextRow = Math.min(lastRow, row + 1);

            for (int column = 0; column <= lastColumn; column++) {
               int previousColumn = Math.max(0, column - 1);
               int nextColumn = Math.min(lastColumn, column + 1);
               Vector3f horizontal = difference(surface[row][nextColumn], surface[row][previousColumn]);
               Vector3f vertical = difference(surface[nextRow][column], surface[previousRow][column]);
               Vector3f normal = frontSurface ? vertical.cross(horizontal) : horizontal.cross(vertical);
               if (normal.lengthSquared() < 1.0E-6F) {
                  normal.set(0.0F, 0.0F, frontSurface ? -1.0F : 1.0F);
               } else {
                  normal.normalize();
               }

               surface[row][column].normal.set(normal);
            }
         }
      }

      private static float breastProjection(float x, float y) {
         float leftDx = x + 1.55F;
         float rightDx = x - 1.55F;
         float left = dome(leftDx, y - 2.55F, leftDx < 0.0F ? 3.4F : 2.65F);
         float right = dome(rightDx, y - 2.55F, rightDx > 0.0F ? 3.4F : 2.65F);
         if (left <= 0.0F) {
            return right * 2.7F;
         } else if (right <= 0.0F) {
            return left * 2.7F;
         } else {
            double blended = Math.pow(Math.pow(left, 6.0) + Math.pow(right, 6.0), 0.16666666666666666);
            return (float)blended * 2.7F;
         }
      }

      private static float dome(float dx, float dy, float radiusX) {
         float nx = dx / radiusX;
         float ny = dy / (dy < 0.0F ? 2.85F : 4.05F);
         float radiusSquared = nx * nx + ny * ny;
         if (radiusSquared >= 1.0F) {
            return 0.0F;
         } else {
            float inside = 1.0F - radiusSquared;
            return inside * inside * (3.0F - 2.0F * inside);
         }
      }

      private static void addSideQuad(
         List<RiptideFemaleBodyRenderer.MeshBuilder.Quad> quads,
         RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint[][] front,
         RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint[][] back,
         int topRow,
         int bottomRow,
         boolean east,
         RiptideFemaleBodyRenderer.UvLayout uv
      ) {
         int column = east ? 28 : 0;
         RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint frontTop = front[topRow][column];
         RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint backTop = back[topRow][column];
         RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint frontBottom = front[bottomRow][column];
         RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint backBottom = back[bottomRow][column];
         float frontU = east ? 28.0F : 20.0F;
         float backU = east ? 32.0F : 16.0F;
         Vector3f normal = faceNormal(east ? backTop : frontTop, east ? frontTop : backTop, east ? frontBottom : backBottom);
         if (east) {
            quads.add(
               quad(
                  meshVertex(backTop, backU, frontV(topRow), normal, uv),
                  meshVertex(frontTop, frontU, frontV(topRow), normal, uv),
                  meshVertex(frontBottom, frontU, frontV(bottomRow), normal, uv),
                  meshVertex(backBottom, backU, frontV(bottomRow), normal, uv)
               )
            );
         } else {
            quads.add(
               quad(
                  meshVertex(frontTop, frontU, frontV(topRow), normal, uv),
                  meshVertex(backTop, backU, frontV(topRow), normal, uv),
                  meshVertex(backBottom, backU, frontV(bottomRow), normal, uv),
                  meshVertex(frontBottom, frontU, frontV(bottomRow), normal, uv)
               )
            );
         }
      }

      private static void addCapQuads(
         List<RiptideFemaleBodyRenderer.MeshBuilder.Quad> quads,
         RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint[][] front,
         RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint[][] back,
         int row,
         boolean bottom,
         int[] columns,
         RiptideFemaleBodyRenderer.UvLayout uv
      ) {
         Vector3f normal = new Vector3f(0.0F, bottom ? 1.0F : -1.0F, 0.0F);

         for (int segment = 0; segment < columns.length - 1; segment++) {
            int left = columns[segment];
            int right = columns[segment + 1];
            RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint frontLeft = front[row][left];
            RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint frontRight = front[row][right];
            RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint backLeft = back[row][left];
            RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint backRight = back[row][right];
            float uLeft = (bottom ? 32.0F : 24.0F) + factor(left) * 4.0F;
            float uRight = (bottom ? 32.0F : 24.0F) + factor(right) * 4.0F;
            float frontCapV = uv.topV + 4.0F;
            float backCapV = uv.topV;
            if (bottom) {
               quads.add(
                  quad(
                     meshVertexAbsoluteUv(frontRight, uRight, frontCapV, normal, uv),
                     meshVertexAbsoluteUv(frontLeft, uLeft, frontCapV, normal, uv),
                     meshVertexAbsoluteUv(backLeft, uLeft, backCapV, normal, uv),
                     meshVertexAbsoluteUv(backRight, uRight, backCapV, normal, uv)
                  )
               );
            } else {
               quads.add(
                  quad(
                     meshVertexAbsoluteUv(backRight, uRight, backCapV, normal, uv),
                     meshVertexAbsoluteUv(backLeft, uLeft, backCapV, normal, uv),
                     meshVertexAbsoluteUv(frontLeft, uLeft, frontCapV, normal, uv),
                     meshVertexAbsoluteUv(frontRight, uRight, frontCapV, normal, uv)
                  )
               );
            }
         }
      }

      private static int[] sequence(int length) {
         int[] values = new int[length];
         int index = 0;

         while (index < length) {
            values[index] = index++;
         }

         return values;
      }

      private static float interpolate(float y, float[] values) {
         if (y <= SHAPE_Y[0]) {
            return values[0];
         } else {
            for (int i = 1; i < SHAPE_Y.length; i++) {
               if (y <= SHAPE_Y[i]) {
                  float progress = (y - SHAPE_Y[i - 1]) / (SHAPE_Y[i] - SHAPE_Y[i - 1]);
                  return values[i - 1] + (values[i] - values[i - 1]) * progress;
               }
            }

            return values[values.length - 1];
         }
      }

      private static float factor(int column) {
         return -1.0F + 2.0F * column / 28.0F;
      }

      private static float frontU(int column) {
         return 24.0F + factor(column) * 4.0F;
      }

      private static float backU(int column) {
         return 36.0F - factor(column) * 4.0F;
      }

      private static float frontV(int row) {
         return ROW_Y[row];
      }

      private static Vector3f difference(RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint a, RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint b) {
         return new Vector3f(a.x - b.x, a.y - b.y, a.z - b.z);
      }

      private static Vector3f faceNormal(
         RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint a,
         RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint b,
         RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint c
      ) {
         Vector3f ab = difference(b, a);
         Vector3f ac = difference(c, a);
         Vector3f normal = ab.cross(ac);
         return normal.lengthSquared() < 1.0E-6F ? new Vector3f(0.0F, 0.0F, -1.0F) : normal.normalize();
      }

      private static RiptideFemaleBodyRenderer.MeshBuilder.MeshVertex meshVertex(
         RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint point, float u, float v, RiptideFemaleBodyRenderer.UvLayout uv
      ) {
         return meshVertex(point, u, v, point.normal, uv);
      }

      private static RiptideFemaleBodyRenderer.MeshBuilder.MeshVertex meshVertex(
         RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint point, float u, float v, Vector3f normal, RiptideFemaleBodyRenderer.UvLayout uv
      ) {
         return new RiptideFemaleBodyRenderer.MeshBuilder.MeshVertex(
            point.x, point.y, point.z, u / uv.textureWidth, (uv.frontV + v) / uv.textureHeight, normal.x(), normal.y(), normal.z()
         );
      }

      private static RiptideFemaleBodyRenderer.MeshBuilder.MeshVertex meshVertexAbsoluteUv(
         RiptideFemaleBodyRenderer.MeshBuilder.SurfacePoint point, float u, float v, Vector3f normal, RiptideFemaleBodyRenderer.UvLayout uv
      ) {
         return new RiptideFemaleBodyRenderer.MeshBuilder.MeshVertex(
            point.x, point.y, point.z, u / uv.textureWidth, v / uv.textureHeight, normal.x(), normal.y(), normal.z()
         );
      }

      private static RiptideFemaleBodyRenderer.MeshBuilder.Quad quad(
         RiptideFemaleBodyRenderer.MeshBuilder.MeshVertex a,
         RiptideFemaleBodyRenderer.MeshBuilder.MeshVertex b,
         RiptideFemaleBodyRenderer.MeshBuilder.MeshVertex c,
         RiptideFemaleBodyRenderer.MeshBuilder.MeshVertex d
      ) {
         return new RiptideFemaleBodyRenderer.MeshBuilder.Quad(a, b, c, d);
      }

      private record Detail(int[] columns, int[] rows) {
         private static final RiptideFemaleBodyRenderer.MeshBuilder.Detail FULL = new RiptideFemaleBodyRenderer.MeshBuilder.Detail(
            RiptideFemaleBodyRenderer.MeshBuilder.sequence(29),
            RiptideFemaleBodyRenderer.MeshBuilder.sequence(RiptideFemaleBodyRenderer.MeshBuilder.ROW_Y.length)
         );
         private static final RiptideFemaleBodyRenderer.MeshBuilder.Detail MEDIUM = new RiptideFemaleBodyRenderer.MeshBuilder.Detail(
            new int[]{0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28}, new int[]{0, 1, 3, 5, 7, 9, 10, 12, 14, 16, 17, 18}
         );
         private static final RiptideFemaleBodyRenderer.MeshBuilder.Detail FAR = new RiptideFemaleBodyRenderer.MeshBuilder.Detail(
            new int[]{0, 4, 8, 12, 16, 20, 24, 28}, new int[]{0, 2, 5, 8, 10, 13, 16, 17, 18}
         );
         private static final RiptideFemaleBodyRenderer.MeshBuilder.Detail CROWD = new RiptideFemaleBodyRenderer.MeshBuilder.Detail(
            new int[]{0, 7, 10, 14, 18, 21, 28}, new int[]{0, 5, 8, 10, 13, 16, 18}
         );
      }

      private static final class MeshCube extends Cube {
         private final RiptideFemaleBodyRenderer.MeshBuilder.MeshVertex[] vertices;
         private final Vector3f transformedNormal = new Vector3f();
         private final Vector3f transformedPosition = new Vector3f();

         private MeshCube(List<RiptideFemaleBodyRenderer.MeshBuilder.Quad> quads) {
            super(0, 0, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, false, 64.0F, 64.0F, Set.of());
            this.vertices = new RiptideFemaleBodyRenderer.MeshBuilder.MeshVertex[quads.size() * 4];
            int index = 0;

            for (RiptideFemaleBodyRenderer.MeshBuilder.Quad quad : quads) {
               for (RiptideFemaleBodyRenderer.MeshBuilder.MeshVertex point : quad.points) {
                  this.vertices[index++] = point;
               }
            }
         }

         public void compile(Pose pose, VertexConsumer output, int lightCoords, int overlayCoords, int color) {
            for (int index = 0; index < this.vertices.length; index++) {
               RiptideFemaleBodyRenderer.MeshBuilder.MeshVertex point = this.vertices[index];
               pose.transformNormal(point.nx, point.ny, point.nz, this.transformedNormal);
               pose.pose().transformPosition(point.x / 16.0F, point.y / 16.0F, point.z / 16.0F, this.transformedPosition);
               output.addVertex(
                  this.transformedPosition.x(),
                  this.transformedPosition.y(),
                  this.transformedPosition.z(),
                  color,
                  point.u,
                  point.v,
                  overlayCoords,
                  lightCoords,
                  this.transformedNormal.x(),
                  this.transformedNormal.y(),
                  this.transformedNormal.z()
               );
            }
         }
      }

      private record MeshVertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
      }

      private static final class Quad {
         private final RiptideFemaleBodyRenderer.MeshBuilder.MeshVertex[] points;

         private Quad(
            RiptideFemaleBodyRenderer.MeshBuilder.MeshVertex a,
            RiptideFemaleBodyRenderer.MeshBuilder.MeshVertex b,
            RiptideFemaleBodyRenderer.MeshBuilder.MeshVertex c,
            RiptideFemaleBodyRenderer.MeshBuilder.MeshVertex d
         ) {
            this.points = new RiptideFemaleBodyRenderer.MeshBuilder.MeshVertex[]{a, b, c, d};
         }
      }

      private static final class SurfacePoint {
         private final float x;
         private final float y;
         private final float z;
         private final Vector3f normal;

         private SurfacePoint(float x, float y, float z, Vector3f normal) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.normal = normal;
         }
      }
   }

   private static final class MeshModel extends Model<AvatarRenderState> {
      private MeshModel(ModelPart root) {
         super(root, RenderTypes::entityTranslucent);
      }

      public void setupAnim(AvatarRenderState state) {
      }
   }

   private record MeshSet(
      RiptideFemaleBodyRenderer.MeshModel body,
      RiptideFemaleBodyRenderer.MeshModel jacket,
      RiptideFemaleBodyRenderer.MeshModel bodyWithJacket,
      RiptideFemaleBodyRenderer.MeshModel chestArmor,
      RiptideFemaleBodyRenderer.MeshModel leggingsArmor
   ) {
      private static RiptideFemaleBodyRenderer.MeshSet build(RiptideFemaleBodyRenderer.MeshBuilder.Detail detail) {
         return new RiptideFemaleBodyRenderer.MeshSet(
            RiptideFemaleBodyRenderer.MeshBuilder.build(RiptideFemaleBodyRenderer.UvLayout.SKIN_BODY, 0.0F, detail),
            RiptideFemaleBodyRenderer.MeshBuilder.build(RiptideFemaleBodyRenderer.UvLayout.SKIN_JACKET, 0.25F, detail),
            RiptideFemaleBodyRenderer.MeshBuilder.buildCombined(
               RiptideFemaleBodyRenderer.UvLayout.SKIN_BODY, 0.0F, RiptideFemaleBodyRenderer.UvLayout.SKIN_JACKET, 0.25F, detail
            ),
            RiptideFemaleBodyRenderer.MeshBuilder.build(RiptideFemaleBodyRenderer.UvLayout.ARMOR, 0.58F, detail),
            RiptideFemaleBodyRenderer.MeshBuilder.build(RiptideFemaleBodyRenderer.UvLayout.ARMOR, 0.32F, detail)
         );
      }
   }

   private record UvLayout(float textureWidth, float textureHeight, float frontV, float topV) {
      private static final RiptideFemaleBodyRenderer.UvLayout SKIN_BODY = new RiptideFemaleBodyRenderer.UvLayout(64.0F, 64.0F, 20.0F, 16.0F);
      private static final RiptideFemaleBodyRenderer.UvLayout SKIN_JACKET = new RiptideFemaleBodyRenderer.UvLayout(64.0F, 64.0F, 36.0F, 32.0F);
      private static final RiptideFemaleBodyRenderer.UvLayout ARMOR = new RiptideFemaleBodyRenderer.UvLayout(64.0F, 32.0F, 20.0F, 16.0F);
   }
}
