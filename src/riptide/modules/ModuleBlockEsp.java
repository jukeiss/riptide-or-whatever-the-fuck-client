package riptide.modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

final class ModuleBlockEsp {
   private static volatile ModuleBlockEsp.Selection cachedSelection = new ModuleBlockEsp.Selection("", Set.of());
   private static final int PER_CHUNK_CAP = 2048;
   private static final Set<Block> BARRIER_TARGET = Set.of(Blocks.BARRIER);
   private static final int BARRIER_COLOR = -50373;

   private ModuleBlockEsp() {
   }

   static void collectBoth(Module module, ClientLevel level, Player player, BiConsumer<AABB, Integer> boxEmit, BiConsumer<Vec3, Integer> traceEmit) {
      if (module != null && level != null && player != null && (boxEmit != null || traceEmit != null)) {
         ModuleBlockEsp.Selection selection = selection(module.value("blocks"));
         if (!selection.blocks().isEmpty()) {
            int maxTargets = parseInt(module.value("max-targets"), 1024, 64, 8192);
            int color = ModuleRenderUtil.color(module, "color", -855688389);
            collect(level, player, selection.blocks(), selection.value(), maxTargets, color, ModuleEspChunkCache.BLOCK_ESP, boxEmit, traceEmit);
         }
      }
   }

   static void collectBarriers(Module module, ClientLevel level, Player player, BiConsumer<AABB, Integer> boxEmit, BiConsumer<Vec3, Integer> traceEmit) {
      if (module != null && level != null && player != null && (boxEmit != null || traceEmit != null)) {
         int maxTargets = parseInt(module.value("max-targets"), 1024, 64, 8192);
         collect(level, player, BARRIER_TARGET, "minecraft:barrier", maxTargets, -50373, ModuleEspChunkCache.BARRIER_ESP, boxEmit, traceEmit);
      }
   }

   private static void collect(
      ClientLevel level,
      Player player,
      Set<Block> targets,
      String stampKey,
      int maxTargets,
      int color,
      ModuleEspChunkCache cache,
      BiConsumer<AABB, Integer> boxEmit,
      BiConsumer<Vec3, Integer> traceEmit
   ) {
      int chunkRadius = ModuleRenderUtil.effectiveRenderChunkRadius();
      double maxDistSq = 2.0 * (chunkRadius * 16.0 + 16.0) * (chunkRadius * 16.0 + 16.0);
      ModuleEspChunkCache.onLevel(level);
      int playerBlockY = player.getBlockY();
      String stamp = stampKey + "|" + color + "|b" + (playerBlockY >> 3);
      long gameTime = level.getGameTime();
      ClientChunkCache chunks = level.getChunkSource();
      Vec3 playerPos = player.position();
      int playerChunkX = player.chunkPosition().x();
      int playerChunkZ = player.chunkPosition().z();
      int emitted = 0;

      for (int radius = 0; radius <= chunkRadius && emitted < maxTargets; radius++) {
         for (int dx = -radius; dx <= radius && emitted < maxTargets; dx++) {
            for (int dz = -radius; dz <= radius && emitted < maxTargets; dz++) {
               if (Math.max(Math.abs(dx), Math.abs(dz)) == radius) {
                  int chunkX = playerChunkX + dx;
                  int chunkZ = playerChunkZ + dz;
                  if (!(chunkDistSq(playerPos, chunkX, chunkZ) > maxDistSq)) {
                     LevelChunk chunk = chunks.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                     if (chunk != null) {
                        List<ModuleEspChunkCache.Entry> entries = cache.chunkEntries(
                           chunk, gameTime, stamp, (scanned, out) -> scanChunk(level, scanned, targets, color, playerBlockY, out)
                        );
                        if (entries.size() > 1) {
                           entries = new ArrayList<>(entries);
                           entries.sort(Comparator.comparingDouble(e -> sqDist(playerPos, e.trace().x, e.trace().y, e.trace().z)));
                        }

                        for (ModuleEspChunkCache.Entry entry : entries) {
                           if (emitted >= maxTargets) {
                              break;
                           }

                           Vec3 trace = entry.trace();
                           if (!(sqDist(playerPos, trace.x, trace.y, trace.z) > maxDistSq)) {
                              if (boxEmit != null) {
                                 boxEmit.accept(entry.box(), entry.color());
                              }

                              if (traceEmit != null) {
                                 traceEmit.accept(trace, entry.color());
                              }

                              emitted++;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static double chunkDistSq(Vec3 playerPos, int chunkX, int chunkZ) {
      double minX = chunkX << 4;
      double minZ = chunkZ << 4;
      double dx = playerPos.x < minX ? minX - playerPos.x : (playerPos.x > minX + 16.0 ? playerPos.x - minX - 16.0 : 0.0);
      double dz = playerPos.z < minZ ? minZ - playerPos.z : (playerPos.z > minZ + 16.0 ? playerPos.z - minZ - 16.0 : 0.0);
      return dx * dx + dz * dz;
   }

   private static void scanChunk(ClientLevel level, LevelChunk chunk, Set<Block> targets, int color, int playerBlockY, List<ModuleEspChunkCache.Entry> out) {
      LevelChunkSection[] sections = chunk.getSections();
      int minX = chunk.getPos().getMinBlockX();
      int minZ = chunk.getPos().getMinBlockZ();
      int playerBand = playerBlockY >> 4;
      int playerSy = playerBlockY & 15;
      MutableBlockPos mutable = new MutableBlockPos();
      Predicate<BlockState> isTarget = statex -> targets.contains(statex.getBlock());
      Integer[] sectionOrder = new Integer[sections.length];

      for (int i = 0; i < sectionOrder.length; i++) {
         sectionOrder[i] = i;
      }

      Arrays.sort(sectionOrder, Comparator.comparingInt(ix -> Math.abs(chunk.getSectionYFromSectionIndex(ix) - playerBand)));
      Integer[] var31 = sectionOrder;
      int var15 = sectionOrder.length;

      for (int var16 = 0; var16 < var15; var16++) {
         int sectionIndex = var31[var16];
         if (out.size() >= 2048) {
            break;
         }

         LevelChunkSection section = sections[sectionIndex];
         if (section != null && !section.hasOnlyAir() && section.maybeHas(isTarget)) {
            int band = chunk.getSectionYFromSectionIndex(sectionIndex);
            int baseY = band << 4;

            for (int sy : yLevelOrder(band, playerBand, playerSy)) {
               if (out.size() >= 2048) {
                  break;
               }

               int y = baseY + sy;
               if (!level.isOutsideBuildHeight(y)) {
                  for (int sx = 0; sx < 16 && out.size() < 2048; sx++) {
                     int x = minX + sx;

                     for (int sz = 0; sz < 16 && out.size() < 2048; sz++) {
                        int z = minZ + sz;
                        BlockState state = section.getBlockState(sx, sy, sz);
                        if (targets.contains(state.getBlock())) {
                           mutable.set(x, y, z);
                           out.add(new ModuleEspChunkCache.Entry(blockShape(level, mutable, state), new Vec3(x + 0.5, y + 0.5, z + 0.5), color));
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static int[] yLevelOrder(int band, int playerBand, int playerSy) {
      int[] order = new int[16];
      if (band != playerBand) {
         boolean below = band < playerBand;

         for (int i = 0; i < 16; i++) {
            order[i] = below ? 15 - i : i;
         }

         return order;
      } else {
         int index = 0;

         for (int d = 0; d < 16 && index < 16; d++) {
            if (playerSy + d < 16) {
               order[index++] = playerSy + d;
            }

            if (d > 0 && playerSy - d >= 0) {
               order[index++] = playerSy - d;
            }
         }

         return order;
      }
   }

   private static AABB blockShape(ClientLevel level, BlockPos pos, BlockState state) {
      VoxelShape shape = state.getShape(level, pos);
      return shape != null && !shape.isEmpty() ? shape.bounds().move(pos.getX(), pos.getY(), pos.getZ()) : new AABB(pos);
   }

   private static ModuleBlockEsp.Selection selection(String value) {
      String safe = value == null ? "" : value;
      ModuleBlockEsp.Selection cached = cachedSelection;
      if (cached.value().equals(safe)) {
         return cached;
      } else {
         Set<Block> blocks = new LinkedHashSet<>();

         for (String raw : safe.split("\\|")) {
            String id = normalizeId(raw);
            if (!id.isEmpty()) {
               Identifier parsed = Identifier.tryParse(id);
               if (parsed != null) {
                  Block block = BuiltInRegistries.BLOCK.getOptional(parsed).orElse(Blocks.AIR);
                  if (block != Blocks.AIR && block != Blocks.BARRIER) {
                     blocks.add(block);
                  }
               }
            }
         }

         ModuleBlockEsp.Selection next = new ModuleBlockEsp.Selection(safe, Set.copyOf(blocks));
         cachedSelection = next;
         return next;
      }
   }

   private static String normalizeId(String raw) {
      if (raw == null) {
         return "";
      } else {
         String value = raw.trim().toLowerCase(Locale.ROOT);
         if (value.isEmpty()) {
            return "";
         } else {
            return value.contains(":") ? value : "minecraft:" + value;
         }
      }
   }

   private static double sqDist(Vec3 from, double x, double y, double z) {
      double dx = from.x - x;
      double dy = from.y - y;
      double dz = from.z - z;
      return dx * dx + dy * dy + dz * dz;
   }

   private static int parseInt(String value, int fallback, int min, int max) {
      try {
         return Math.max(min, Math.min(max, Integer.parseInt(value)));
      } catch (Exception var5) {
         return fallback;
      }
   }

   private record Selection(String value, Set<Block> blocks) {
   }
}
