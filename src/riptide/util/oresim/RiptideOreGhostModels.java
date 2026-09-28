package riptide.util.oresim;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

public final class RiptideOreGhostModels {
   public static final int VERTEX_STRIDE = 5;
   private static final RandomSource RANDOM = RandomSource.create(42L);
   private static final Map<BlockState, RiptideOreGhostModels.Template> CACHE = new IdentityHashMap<>();
   private static final List<BlockStateModelPart> PARTS = new ArrayList<>();
   private static Object modelSetToken;

   private RiptideOreGhostModels() {
   }

   public static void clear() {
      CACHE.clear();
      modelSetToken = null;
   }

   public static RiptideOreGhostModels.Template of(BlockState state) {
      if (state == null) {
         return RiptideOreGhostModels.Template.EMPTY;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc == null) {
            return RiptideOreGhostModels.Template.EMPTY;
         } else {
            BlockStateModelSet set = mc.getModelManager().getBlockStateModelSet();
            if (set == null) {
               return RiptideOreGhostModels.Template.EMPTY;
            } else {
               if (set != modelSetToken) {
                  CACHE.clear();
                  modelSetToken = set;
               }

               RiptideOreGhostModels.Template cached = CACHE.get(state);
               if (cached != null) {
                  return cached;
               } else {
                  RiptideOreGhostModels.Template built;
                  try {
                     built = build(set, state);
                  } catch (Throwable var6) {
                     riptide.RiptideClientAddon.LOG.debug("OreSim ghost model build failed", var6);
                     built = RiptideOreGhostModels.Template.EMPTY;
                  }

                  CACHE.put(state, built);
                  return built;
               }
            }
         }
      }
   }

   private static RiptideOreGhostModels.Template build(BlockStateModelSet set, BlockState state) {
      PARTS.clear();
      RANDOM.setSeed(42L);
      set.get(state).collectParts(RANDOM, PARTS);
      List<RiptideOreGhostModels.Face> faces = new ArrayList<>();

      for (BlockStateModelPart part : PARTS) {
         for (Direction direction : Direction.values()) {
            collect(part.getQuads(direction), direction, faces);
         }

         collect(part.getQuads(null), null, faces);
      }

      PARTS.clear();
      return faces.isEmpty() ? RiptideOreGhostModels.Template.EMPTY : new RiptideOreGhostModels.Template(List.copyOf(faces));
   }

   private static void collect(List<BakedQuad> quads, Direction cull, List<RiptideOreGhostModels.Face> out) {
      if (quads != null && !quads.isEmpty()) {
         for (BakedQuad quad : quads) {
            float[] data = new float[20];

            for (int i = 0; i < 4; i++) {
               Vector3fc position = quad.position(i);
               long uv = quad.packedUV(i);
               int base = i * 5;
               data[base] = position.x();
               data[base + 1] = position.y();
               data[base + 2] = position.z();
               data[base + 3] = UVPair.unpackU(uv);
               data[base + 4] = UVPair.unpackV(uv);
            }

            Direction facing = quad.materialInfo().shade() ? quad.direction() : null;
            out.add(new RiptideOreGhostModels.Face(cull, facing, data));
         }
      }
   }

   public record Face(Direction cull, Direction facing, float[] data) {
   }

   public record Template(List<RiptideOreGhostModels.Face> faces) {
      static final RiptideOreGhostModels.Template EMPTY = new RiptideOreGhostModels.Template(List.of());
   }
}
