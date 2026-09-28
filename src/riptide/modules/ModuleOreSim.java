package riptide.modules;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import riptide.util.oresim.RiptideOreSimEngine;
import riptide.util.oresim.RiptideOreSimOre;
import riptide.util.oresim.RiptideOreSimSeedInput;

public final class ModuleOreSim {
   static final int NEAR_RANGE = 5;
   public static final int DRAW_CAP = 8192;
   private static final int PER_CHUNK_CAP = 2048;
   private static final int NEAR_CAP = 64;
   private static final int VERIFY_INTERVAL = 4;
   private static final int VERIFY_RAY_BUDGET = 96;
   private static final long SEED_SETTLE_NANOS = 400000000L;
   private static final int EXAMINED_RESET_SWEEPS = 25;
   private static int verifyTimer;
   private static int examinedSweeps;
   private static boolean seedObserved;
   private static Long pendingSeed;
   private static long seedReadyAtNanos;
   private static final LongOpenHashSet examinedRecently = new LongOpenHashSet();
   private static final long[] boxPositions = new long[8192];
   private static final int[] boxStates = new int[8192];
   private static final double[] realHeapDist = new double[8192];
   private static final ModuleEspChunkCache.Entry[] realHeapEntry = new ModuleEspChunkCache.Entry[8192];
   static final int OTHER_COLOR = -3355444;
   private static final ModuleOreSim.Selection EMPTY_SELECTION = new ModuleOreSim.Selection("", Set.of(), List.of(), 0);
   private static volatile ModuleOreSim.Selection cachedNormal = EMPTY_SELECTION;
   private static volatile ModuleOreSim.Selection cachedOreSim = EMPTY_SELECTION;
   private static int cachedModeRevision = Integer.MIN_VALUE;
   private static boolean cachedOreSimMode;
   private static boolean cachedEspStyle;
   private static int blockColorRevision = Integer.MIN_VALUE;
   private static final Object2IntOpenHashMap<Block> blockColors = new Object2IntOpenHashMap();
   private static volatile RiptideOreSimSeedInput.Result lastSeedInput = RiptideOreSimSeedInput.parse(null);
   private static final long RETENTION_NANOS = TimeUnit.MINUTES.toNanos(2L);
   private static ModuleOreSim.Selection allowedStatesSource;
   private static boolean[] allowedStates = new boolean[0];
   private static volatile int nearMatched;
   private static volatile int nearTouchingAir;
   private static volatile int nearAlreadyPredicted;
   private static volatile int nearVisible;

   private ModuleOreSim() {
   }

   private static void refreshMode(Module xray) {
      int revision = ModuleRegistry.revision();
      if (revision != cachedModeRevision) {
         cachedModeRevision = revision;
         cachedOreSimMode = xray != null && "OreSim".equals(xray.value("mode"));
         cachedEspStyle = xray != null && "ESP".equals(xray.value("render-style"));
      }
   }

   static boolean oreSimMode(Module xray) {
      if (xray == null) {
         return false;
      } else {
         refreshMode(xray);
         return cachedOreSimMode;
      }
   }

   static boolean espStyle(Module xray) {
      if (xray == null) {
         return false;
      } else {
         refreshMode(xray);
         return cachedEspStyle;
      }
   }

   static boolean drawsBoxes(Module xray) {
      return xray != null && xray.isEnabled() && espStyle(xray);
   }

   static boolean drawsGhosts(Module xray) {
      return xray != null && xray.isEnabled() && oreSimMode(xray) && !espStyle(xray);
   }

   static boolean tintActive(Module xray) {
      return xray != null && xray.isEnabled() && !espStyle(xray);
   }

   static float fillAlpha(Module xray) {
      return 0.3F;
   }

   private static ModuleOreSim.Selection selection(Module xray) {
      if (xray == null) {
         return EMPTY_SELECTION;
      } else if (oreSimMode(xray)) {
         ModuleOreSim.Selection next = parse(xray.value("oresim-ores"), cachedOreSim);
         cachedOreSim = next;
         return next;
      } else {
         ModuleOreSim.Selection next = parse(xray.value("whitelist"), cachedNormal);
         cachedNormal = next;
         return next;
      }
   }

   private static ModuleOreSim.Selection parse(String raw, ModuleOreSim.Selection cached) {
      String safe = raw == null ? "" : raw;
      if (cached.value().equals(safe)) {
         return cached;
      } else {
         Set<Block> blocks = new LinkedHashSet<>();
         List<String> nonOre = new ArrayList<>();
         int mask = 0;

         for (String token : safe.split("\\|")) {
            String id = token.trim().toLowerCase(Locale.ROOT);
            if (!id.isEmpty()) {
               if (!id.contains(":")) {
                  id = "minecraft:" + id;
               }

               Identifier parsed = Identifier.tryParse(id);
               if (parsed != null) {
                  Block block = BuiltInRegistries.BLOCK.getOptional(parsed).orElse(Blocks.AIR);
                  if (block != Blocks.AIR) {
                     blocks.add(block);
                     RiptideOreSimOre.Kind family = RiptideOreSimOre.familyOf(id);
                     if (family != null) {
                        mask |= 1 << family.ordinal();
                     } else if (!nonOre.contains(id)) {
                        nonOre.add(id);
                     }
                  }
               }
            }
         }

         return new ModuleOreSim.Selection(safe, Set.copyOf(blocks), List.copyOf(nonOre), mask);
      }
   }

   static int enabledMask(Module xray) {
      return selection(xray).mask();
   }

   static boolean whitelistHasFamily(Module xray, RiptideOreSimOre.Kind kind) {
      return kind != null && (selection(xray).mask() & 1 << kind.ordinal()) != 0;
   }

   static List<String> nonOreWhitelistIds(Module xray) {
      if (xray == null) {
         return List.of();
      } else {
         ModuleOreSim.Selection next = parse(xray.value("whitelist"), cachedNormal);
         cachedNormal = next;
         return next.nonOreIds();
      }
   }

   static boolean whitelistHasId(Module xray, String id) {
      return nonOreWhitelistIds(xray).contains(id);
   }

   static int colorFor(Module xray, RiptideOreSimOre.Kind kind) {
      return ModuleRenderUtil.color(xray, kind.colorId(), kind.defaultColor);
   }

   static int colorForBlock(Module xray, Block block) {
      if (block == null) {
         return -3355444;
      } else {
         int revision = ModuleRegistry.revision();
         if (revision != blockColorRevision) {
            blockColorRevision = revision;
            blockColors.clear();
         }

         int cached = blockColors.getOrDefault(block, Integer.MIN_VALUE);
         if (cached != Integer.MIN_VALUE) {
            return cached;
         } else {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            RiptideOreSimOre.Kind family = id == null ? null : RiptideOreSimOre.familyOf(id.toString());
            int color = family != null ? colorFor(xray, family) : (id == null ? -3355444 : ModuleRenderUtil.color(xray, "block-color-" + id, -3355444));
            blockColors.put(block, color);
            return color;
         }
      }
   }

   static Long seed(Module xray) {
      RiptideOreSimSeedInput.Result parsed = RiptideOreSimSeedInput.parse(xray == null ? null : xray.value("oresim-seed"));
      lastSeedInput = parsed;
      return parsed.value();
   }

   public static RiptideOreSimSeedInput.Status seedInputStatus(Module xray) {
      seed(xray);
      return lastSeedInput.status();
   }

   public static Long debugSeed(Module xray) {
      return seed(xray);
   }

   public static int debugSelectionSize(Module xray) {
      return selection(xray).blocks().size();
   }

   public static int debugEnabledMask(Module xray) {
      return enabledMask(xray);
   }

   static int simulationRadius(Module xray) {
      int configured = xray == null ? 3 : xray.integer("oresim-radius");
      return Math.max(1, Math.min(configured, 8));
   }

   static void tick(Module xray, ClientLevel level, Player player) {
      if (oreSimMode(xray) && xray != null && xray.isEnabled() && level != null && player != null) {
         RiptideOreSimEngine.resume();
         Long parsedSeed = seed(xray);
         int mask = enabledMask(xray);
         long now = System.nanoTime();
         if (!seedObserved || !Objects.equals(parsedSeed, pendingSeed)) {
            seedObserved = true;
            pendingSeed = parsedSeed;
            seedReadyAtNanos = now + 400000000L;
            RiptideOreSimEngine.clear();
         } else if (parsedSeed != null && mask != 0 && now >= seedReadyAtNanos) {
            RiptideOreSimEngine.tick(level, player.blockPosition(), parsedSeed, simulationRadius(xray), mask);
            if (++verifyTimer >= 4) {
               verifyTimer = 0;
               retractDisproven(xray, level, player);
            }
         } else {
            RiptideOreSimEngine.clear();
         }
      } else {
         RiptideOreSimEngine.suspend();
      }
   }

   public static void tickRetention() {
      if (RiptideOreSimEngine.expireSuspended(RETENTION_NANOS)) {
         seedObserved = false;
         pendingSeed = null;
         seedReadyAtNanos = 0L;
      }
   }

   private static void retractDisproven(Module xray, ClientLevel level, Player player) {
      ModuleOreSim.Selection selection = selection(xray);
      if (!selection.blocks().isEmpty()) {
         Vec3 eyes = player.getEyePosition();
         double maxDistSq = 25.0;
         MutableBlockPos cursor = new MutableBlockPos();
         int[] rays = new int[]{0};
         RiptideOreSimEngine.verifyNear(player.blockPosition(), 5, (stateId, packed) -> {
            BlockState predicted = RiptideOreSimOre.OreStates.state(stateId);
            if (predicted == null) {
               return false;
            } else {
               int x = BlockPos.getX(packed);
               int y = BlockPos.getY(packed);
               int z = BlockPos.getZ(packed);
               double cx = x + 0.5 - eyes.x;
               double cy = y + 0.5 - eyes.y;
               double cz = z + 0.5 - eyes.z;
               if (cx * cx + cy * cy + cz * cz > maxDistSq) {
                  return false;
               } else {
                  cursor.set(x, y, z);
                  BlockState actual = level.getBlockState(cursor);
                  if (actual.getBlock() == predicted.getBlock()) {
                     return false;
                  } else {
                     boolean open = actual.isAir() || touchesAir(level, cursor);
                     if (!open) {
                        return false;
                     } else if (!examinedRecently.add(packed)) {
                        return false;
                     } else {
                        return rays[0] >= 96 ? false : canSee(level, player, eyes, cursor.immutable(), actual.isAir(), rays);
                     }
                  }
               }
            }
         });
         if (++examinedSweeps >= 25 || examinedRecently.size() > 4096) {
            examinedSweeps = 0;
            examinedRecently.clear();
         }
      }
   }

   static long contentKey(Module xray, ClientLevel level) {
      return oreSimMode(xray) ? (long)RiptideOreSimEngine.revision() << 1 | 1L : (long)ModuleEspChunkCache.generation() << 1;
   }

   private static boolean stateAllowed(ModuleOreSim.Selection selection, int stateId) {
      BlockState state = RiptideOreSimOre.OreStates.state(stateId);
      return state != null && selection.blocks().contains(state.getBlock());
   }

   private static boolean[] allowedStates(ModuleOreSim.Selection selection) {
      int count = RiptideOreSimOre.OreStates.count();
      boolean[] table = allowedStates;
      if (selection == allowedStatesSource && table.length == count) {
         return table;
      } else {
         table = new boolean[count];

         for (int id = 0; id < count; id++) {
            table[id] = stateAllowed(selection, id);
         }

         allowedStatesSource = selection;
         allowedStates = table;
         return table;
      }
   }

   private static IntPredicate allowedStatePredicate(ModuleOreSim.Selection selection) {
      boolean[] table = allowedStates(selection);
      return stateId -> stateId >= 0 && stateId < table.length && table[stateId];
   }

   static int collectGhosts(Module xray, Player player, long[] outPos, int[] outState) {
      if (xray != null && xray.isEnabled() && player != null && oreSimMode(xray)) {
         ModuleOreSim.Selection selection = selection(xray);
         if (selection.blocks().isEmpty()) {
            return 0;
         } else {
            Vec3 eyes = player.getEyePosition();
            return RiptideOreSimEngine.selectNearest(eyes.x, eyes.y, eyes.z, simulationRadius(xray), allowedStatePredicate(selection), 8192, outPos, outState);
         }
      } else {
         return 0;
      }
   }

   static void collect(Module xray, ClientLevel level, Player player, BiConsumer<AABB, Integer> emit) {
      if (emit != null && xray != null && xray.isEnabled() && level != null && player != null) {
         if (oreSimMode(xray)) {
            collectSimulated(xray, player, emit);
         } else {
            collectRealBlocks(xray, level, player, emit);
         }
      }
   }

   private static void collectSimulated(Module xray, Player player, BiConsumer<AABB, Integer> emit) {
      ModuleOreSim.Selection selection = selection(xray);
      if (!selection.blocks().isEmpty()) {
         Vec3 eyes = player.getEyePosition();
         int count = RiptideOreSimEngine.selectNearest(
            eyes.x, eyes.y, eyes.z, simulationRadius(xray), allowedStatePredicate(selection), 8192, boxPositions, boxStates
         );
         int[] colors = new int[RiptideOreSimOre.Kind.values().length];

         for (RiptideOreSimOre.Kind kind : RiptideOreSimOre.Kind.values()) {
            colors[kind.ordinal()] = colorFor(xray, kind);
         }

         for (int i = 0; i < count; i++) {
            RiptideOreSimOre.Kind kind = RiptideOreSimOre.OreStates.kind(boxStates[i]);
            emit.accept(boxAt(boxPositions[i]), kind == null ? -3355444 : colors[kind.ordinal()]);
         }
      }
   }

   private static void collectRealBlocks(Module xray, ClientLevel level, Player player, BiConsumer<AABB, Integer> emit) {
      ModuleOreSim.Selection selection = selection(xray);
      if (!selection.blocks().isEmpty()) {
         ModuleEspChunkCache.onLevel(level);
         Object2IntMap<Block> colors = new Object2IntOpenHashMap();
         colors.defaultReturnValue(-3355444);
         int colorsHash = 0;

         for (Block block : selection.blocks()) {
            int color = colorForBlock(xray, block);
            colors.put(block, color);
            colorsHash = colorsHash * 31 + color;
         }

         int chunkRadius = ModuleRenderUtil.effectiveRenderChunkRadius();
         int playerBlockY = player.getBlockY();
         String stamp = selection.value() + "|" + colorsHash + "|b" + (playerBlockY >> 4);
         long gameTime = level.getGameTime();
         ClientChunkCache chunks = level.getChunkSource();
         int playerChunkX = player.chunkPosition().x();
         int playerChunkZ = player.chunkPosition().z();
         Vec3 eyes = player.getEyePosition();
         double[] heapDist = realHeapDist;
         ModuleEspChunkCache.Entry[] heapEntry = realHeapEntry;
         int size = 0;

         for (int ring = 0; ring <= chunkRadius; ring++) {
            if (size == 8192) {
               double ringMin = (ring - 1) * 16.0;
               if (ringMin > 0.0 && ringMin * ringMin > heapDist[0]) {
                  break;
               }
            }

            for (int dx = -ring; dx <= ring; dx++) {
               for (int dz = -ring; dz <= ring; dz++) {
                  if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                     LevelChunk chunk = chunks.getChunk(playerChunkX + dx, playerChunkZ + dz, ChunkStatus.FULL, false);
                     if (chunk != null) {
                        for (ModuleEspChunkCache.Entry entry : ModuleEspChunkCache.XRAY_ESP
                           .chunkEntries(chunk, gameTime, stamp, (scanned, out) -> scanChunk(scanned, selection, colors, playerBlockY, out))) {
                           Vec3 center = entry.trace();
                           double px = center.x - eyes.x;
                           double py = center.y - eyes.y;
                           double pz = center.z - eyes.z;
                           double distSq = px * px + py * py + pz * pz;
                           if (size < 8192) {
                              heapDist[size] = distSq;
                              heapEntry[size] = entry;
                              siftUp(heapDist, heapEntry, size++);
                           } else if (distSq < heapDist[0]) {
                              heapDist[0] = distSq;
                              heapEntry[0] = entry;
                              siftDown(heapDist, heapEntry, size);
                           }
                        }
                     }
                  }
               }
            }
         }

         for (int i = 0; i < size; i++) {
            emit.accept(heapEntry[i].box(), heapEntry[i].color());
            heapEntry[i] = null;
         }
      }
   }

   private static void siftUp(double[] dist, ModuleEspChunkCache.Entry[] entries, int index) {
      while (index > 0) {
         int parent = index - 1 >>> 1;
         if (!(dist[parent] >= dist[index])) {
            swap(dist, entries, parent, index);
            index = parent;
            continue;
         }
         break;
      }
   }

   private static void siftDown(double[] dist, ModuleEspChunkCache.Entry[] entries, int size) {
      int index = 0;

      while (true) {
         int left = index * 2 + 1;
         if (left >= size) {
            break;
         }

         int largest = left;
         int right = left + 1;
         if (right < size && dist[right] > dist[left]) {
            largest = right;
         }

         if (dist[index] >= dist[largest]) {
            break;
         }

         swap(dist, entries, index, largest);
         index = largest;
      }
   }

   private static void swap(double[] dist, ModuleEspChunkCache.Entry[] entries, int a, int b) {
      double d = dist[a];
      dist[a] = dist[b];
      dist[b] = d;
      ModuleEspChunkCache.Entry e = entries[a];
      entries[a] = entries[b];
      entries[b] = e;
   }

   private static void scanChunk(
      LevelChunk chunk, ModuleOreSim.Selection selection, Object2IntMap<Block> colors, int playerBlockY, List<ModuleEspChunkCache.Entry> out
   ) {
      LevelChunkSection[] sections = chunk.getSections();
      int minX = chunk.getPos().getMinBlockX();
      int minZ = chunk.getPos().getMinBlockZ();
      int playerBand = playerBlockY >> 4;
      Set<Block> targets = selection.blocks();
      Predicate<BlockState> isTarget = statex -> targets.contains(statex.getBlock());
      int[] order = new int[sections.length];
      int i = 0;

      while (i < order.length) {
         order[i] = i++;
      }

      for (int ix = 1; ix < order.length; ix++) {
         int value = order[ix];
         int key = Math.abs(chunk.getSectionYFromSectionIndex(value) - playerBand);

         int j;
         for (j = ix - 1; j >= 0 && Math.abs(chunk.getSectionYFromSectionIndex(order[j]) - playerBand) > key; j--) {
            order[j + 1] = order[j];
         }

         order[j + 1] = value;
      }

      for (int index : order) {
         if (out.size() >= 2048) {
            break;
         }

         LevelChunkSection section = sections[index];
         if (section != null && !section.hasOnlyAir() && section.maybeHas(isTarget)) {
            int baseY = chunk.getSectionYFromSectionIndex(index) << 4;

            for (int sy = 0; sy < 16 && out.size() < 2048; sy++) {
               for (int sx = 0; sx < 16 && out.size() < 2048; sx++) {
                  for (int sz = 0; sz < 16 && out.size() < 2048; sz++) {
                     BlockState state = section.getBlockState(sx, sy, sz);
                     Block block = state.getBlock();
                     if (targets.contains(block)) {
                        int x = minX + sx;
                        int y = baseY + sy;
                        int z = minZ + sz;
                        out.add(new ModuleEspChunkCache.Entry(new AABB(new BlockPos(x, y, z)), new Vec3(x + 0.5, y + 0.5, z + 0.5), colors.getInt(block)));
                     }
                  }
               }
            }
         }
      }
   }

   static void collectNearbyReal(Module xray, ClientLevel level, Player player, ModuleOreSim.NearSink sink) {
      if (sink != null && oreSimMode(xray) && level != null && player != null) {
         ModuleOreSim.Selection selection = selection(xray);
         if (!selection.blocks().isEmpty()) {
            BlockPos center = player.blockPosition();
            Vec3 eyes = player.getEyePosition();
            double maxDistSq = 25.0;
            MutableBlockPos cursor = new MutableBlockPos();
            List<BlockPos> candidates = null;
            int matched = 0;
            int touching = 0;

            for (int dx = -5; dx <= 5; dx++) {
               for (int dy = -5; dy <= 5; dy++) {
                  for (int dz = -5; dz <= 5; dz++) {
                     int x = center.getX() + dx;
                     int y = center.getY() + dy;
                     int z = center.getZ() + dz;
                     double cx = x + 0.5 - eyes.x;
                     double cy = y + 0.5 - eyes.y;
                     double cz = z + 0.5 - eyes.z;
                     if (!(cx * cx + cy * cy + cz * cz > maxDistSq)) {
                        cursor.set(x, y, z);
                        BlockState state = level.getBlockState(cursor);
                        if (selection.blocks().contains(state.getBlock())) {
                           matched++;
                           if (touchesAir(level, cursor)) {
                              touching++;
                              if (candidates == null) {
                                 candidates = new ArrayList<>();
                              }

                              candidates.add(cursor.immutable());
                           }
                        }
                     }
                  }
               }
            }

            nearMatched = matched;
            nearTouchingAir = touching;
            if (candidates == null) {
               nearAlreadyPredicted = 0;
               nearVisible = 0;
            } else {
               LongOpenHashSet predicted = new LongOpenHashSet();
               RiptideOreSimEngine.collectNear(center, 6, predicted);
               int emitted = 0;
               int skippedPredicted = 0;
               int[] rays = new int[]{0};

               for (BlockPos pos : candidates) {
                  if (emitted >= 64) {
                     break;
                  }

                  if (predicted.contains(pos.asLong())) {
                     skippedPredicted++;
                  } else if (visible(level, player, eyes, pos, rays)) {
                     sink.accept(pos, level.getBlockState(pos));
                     emitted++;
                  }
               }

               nearAlreadyPredicted = skippedPredicted;
               nearVisible = emitted;
            }
         }
      }
   }

   public static String nearDiagnostics() {
      return "matched=" + nearMatched + " touchingAir=" + nearTouchingAir + " alreadyPredicted=" + nearAlreadyPredicted + " drawn=" + nearVisible;
   }

   private static boolean touchesAir(ClientLevel level, BlockPos pos) {
      MutableBlockPos neighbour = new MutableBlockPos();

      for (Direction direction : Direction.values()) {
         neighbour.setWithOffset(pos, direction);
         if (level.getBlockState(neighbour).isAir()) {
            return true;
         }
      }

      return false;
   }

   private static boolean canSee(ClientLevel level, Player player, Vec3 eyes, BlockPos pos, boolean empty, int[] rays) {
      if (!empty) {
         return visible(level, player, eyes, pos, rays);
      } else {
         rays[0]++;
         BlockHitResult hit = level.clip(new ClipContext(eyes, Vec3.atCenterOf(pos), net.minecraft.world.level.ClipContext.Block.VISUAL, Fluid.NONE, player));
         return hit.getType() == Type.MISS;
      }
   }

   private static boolean visible(ClientLevel level, Player player, Vec3 eyes, BlockPos pos, int[] rays) {
      if (rayReaches(level, player, eyes, Vec3.atCenterOf(pos), pos, rays)) {
         return true;
      } else {
         for (Direction direction : Direction.values()) {
            double faceX = pos.getX() + 0.5 + direction.getStepX() * 0.5;
            double faceY = pos.getY() + 0.5 + direction.getStepY() * 0.5;
            double faceZ = pos.getZ() + 0.5 + direction.getStepZ() * 0.5;
            if (!((eyes.x - faceX) * direction.getStepX() + (eyes.y - faceY) * direction.getStepY() + (eyes.z - faceZ) * direction.getStepZ() <= 0.0)
               && rayReaches(level, player, eyes, new Vec3(faceX, faceY, faceZ), pos, rays)) {
               return true;
            }
         }

         return false;
      }
   }

   private static boolean rayReaches(ClientLevel level, Player player, Vec3 from, Vec3 to, BlockPos target, int[] rays) {
      rays[0]++;
      BlockHitResult hit = level.clip(new ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.VISUAL, Fluid.NONE, player));
      return hit.getType() == Type.MISS || hit.getBlockPos().equals(target);
   }

   private static AABB boxAt(long packed) {
      return new AABB(new BlockPos(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed)));
   }

   static String info(Module xray) {
      if (xray != null && xray.isEnabled() && oreSimMode(xray)) {
         boolean shaderCullMode = !espStyle(xray) && ModuleRenderUtil.xrayUsesShaderCullMode();
         Long parsedSeed = seed(xray);
         if (lastSeedInput.status() == RiptideOreSimSeedInput.Status.INVALID) {
            return "invalid seed";
         } else if (parsedSeed == null) {
            return "no seed";
         } else if (enabledMask(xray) == 0) {
            return "no ores";
         } else if (RiptideOreSimEngine.status() == RiptideOreSimEngine.Status.UNSUPPORTED_DIMENSION) {
            return "unsupported dimension";
         } else if (RiptideOreSimEngine.status() == RiptideOreSimEngine.Status.UNVERIFIED_WORLDGEN) {
            return "unverified worldgen";
         } else if (RiptideOreSimEngine.failed()) {
            return "worldgen failed";
         } else {
            RiptideOreSimEngine.TargetProgress progress = RiptideOreSimEngine.targetProgress();
            if (progress.totalChunks() > 0) {
               return progressInfo(progress);
            } else if (shaderCullMode) {
               return "Shader cull mode";
            } else {
               return RiptideOreSimEngine.status() == RiptideOreSimEngine.Status.LOADING_CONTEXT ? "loading worldgen" : "waiting";
            }
         }
      } else {
         return "";
      }
   }

   static String progressInfo(RiptideOreSimEngine.TargetProgress progress) {
      return progress.completedChunks() + "/" + progress.totalChunks() + " chunks";
   }

   @FunctionalInterface
   public interface NearSink {
      void accept(BlockPos var1, BlockState var2);
   }

   private record Selection(String value, Set<Block> blocks, List<String> nonOreIds, int mask) {
   }
}
