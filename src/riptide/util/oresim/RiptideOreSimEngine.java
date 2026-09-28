package riptide.util.oresim;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import riptide.util.worldgen.mc26_2.RiptideRegionGenerator;
import riptide.util.worldgen.mc26_2.RiptideWorldgenContext;

public final class RiptideOreSimEngine {
   public static final int MAX_SIM_RADIUS = 8;
   private static final int TILE_SIZE = 4;
   private static final int REGION_SIZE = 8;
   private static final int INITIAL_REGION_SIZE = 5;
   private static final int MAX_POSITIONS = 600000;
   private static final int MAX_CONTEXTS = 3;
   private static final long TAG_VERIFY_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5L);
   private static final Object STATE_LOCK = new Object();
   private static final Map<Long, RiptideOreSimEngine.ChunkOres> CHUNKS = new ConcurrentHashMap<>();
   private static final Map<Long, RiptideOreSimEngine.ChunkOres> PENDING_CHUNKS = new HashMap<>();
   private static final LongOpenHashSet DISPROVEN = new LongOpenHashSet();
   private static final int MAX_DISPROVEN = 200000;
   private static final RiptideOreSimWorker WORKER = new RiptideOreSimWorker("Riptide-OreSim-Worldgen");
   private static final LinkedHashMap<RiptideOreSimEngine.ContextKey, RiptideWorldgenContext> CONTEXT_CACHE = new LinkedHashMap<RiptideOreSimEngine.ContextKey, RiptideWorldgenContext>(
      4, 0.75F, true
   ) {
      @Override
      protected boolean removeEldestEntry(Entry<RiptideOreSimEngine.ContextKey, RiptideWorldgenContext> eldest) {
         return this.size() > 3;
      }
   };
   private static RiptideOreSimEngine.ContextKey activeKey;
   private static volatile RiptideWorldgenContext activeContext;
   private static volatile long epoch;
   private static volatile boolean contextLoading;
   private static volatile boolean generationInFlight;
   private static boolean tagVerificationInFlight;
   private static volatile boolean failed;
   private static volatile boolean unsupportedDimension;
   private static volatile boolean unverifiedWorldgen;
   private static volatile String failureMessage = "";
   private static int playerBlockX;
   private static int playerBlockZ;
   private static int targetRadius = 1;
   private static boolean initialChunkGenerated;
   private static final AtomicInteger REVISION = new AtomicInteger();
   private static volatile int totalPositions;
   private static volatile boolean positionCapHit;
   private static volatile boolean selectionTruncated;
   private static volatile long suspendedAtNanos;
   private static long nextTagVerificationNanos;

   private RiptideOreSimEngine() {
   }

   public static RiptideOreSimEngine.Status status() {
      if (failed) {
         return RiptideOreSimEngine.Status.ERROR;
      } else if (unsupportedDimension) {
         return RiptideOreSimEngine.Status.UNSUPPORTED_DIMENSION;
      } else if (unverifiedWorldgen) {
         return RiptideOreSimEngine.Status.UNVERIFIED_WORLDGEN;
      } else if (activeKey == null) {
         return RiptideOreSimEngine.Status.IDLE;
      } else if (contextLoading || activeContext == null) {
         return RiptideOreSimEngine.Status.LOADING_CONTEXT;
      } else {
         return generationInFlight ? RiptideOreSimEngine.Status.GENERATING : RiptideOreSimEngine.Status.READY;
      }
   }

   public static boolean ready() {
      return activeContext != null && !failed && !unsupportedDimension && !unverifiedWorldgen;
   }

   public static boolean loading() {
      return contextLoading || generationInFlight;
   }

   public static boolean failed() {
      return failed;
   }

   public static String failureMessage() {
      return failureMessage;
   }

   public static int chunkCount() {
      return CHUNKS.size();
   }

   public static RiptideOreSimEngine.TargetProgress targetProgress() {
      synchronized (STATE_LOCK) {
         if (activeKey == null) {
            return new RiptideOreSimEngine.TargetProgress(epoch, 0, 0);
         } else {
            int centerX = SectionPos.blockToSectionCoord(playerBlockX);
            int centerZ = SectionPos.blockToSectionCoord(playerBlockZ);
            int radius = Math.max(1, Math.min(targetRadius, 8));
            int diameter = radius * 2 + 1;
            int complete = 0;

            for (int dx = -radius; dx <= radius; dx++) {
               for (int dz = -radius; dz <= radius; dz++) {
                  long key = ChunkPos.pack(centerX + dx, centerZ + dz);
                  if (CHUNKS.containsKey(key) || PENDING_CHUNKS.containsKey(key)) {
                     complete++;
                  }
               }
            }

            return new RiptideOreSimEngine.TargetProgress(epoch, complete, diameter * diameter);
         }
      }
   }

   public static int storedPositions() {
      return totalPositions;
   }

   public static boolean capped() {
      return positionCapHit;
   }

   public static int revision() {
      return REVISION.get();
   }

   public static void clear() {
      suspendedAtNanos = 0L;
      synchronized (STATE_LOCK) {
         if (activeKey != null
            || !CHUNKS.isEmpty()
            || !PENDING_CHUNKS.isEmpty()
            || contextLoading
            || generationInFlight
            || tagVerificationInFlight
            || failed
            || unsupportedDimension
            || unverifiedWorldgen
            || !failureMessage.isEmpty()
            || !DISPROVEN.isEmpty()
            || !WORKER.isIdle()) {
            epoch++;
            discardQueuedWork();
            activeKey = null;
            activeContext = null;
            contextLoading = false;
            generationInFlight = false;
            tagVerificationInFlight = false;
            initialChunkGenerated = false;
            failed = false;
            unsupportedDimension = false;
            unverifiedWorldgen = false;
            failureMessage = "";
            positionCapHit = false;
            nextTagVerificationNanos = 0L;
            totalPositions = 0;
            CHUNKS.clear();
            PENDING_CHUNKS.clear();
            DISPROVEN.clear();
            REVISION.incrementAndGet();
         }
      }
   }

   public static void suspend() {
      if (suspendedAtNanos == 0L) {
         suspendedAtNanos = System.nanoTime();
      }
   }

   public static void resume() {
      suspendedAtNanos = 0L;
   }

   public static boolean isSuspended() {
      return suspendedAtNanos != 0L;
   }

   public static boolean expireSuspended(long retentionNanos) {
      long since = suspendedAtNanos;
      if (since != 0L && System.nanoTime() - since >= retentionNanos) {
         suspendedAtNanos = 0L;
         clear();
         return true;
      } else {
         return false;
      }
   }

   public static void forgetDisproven() {
      if (!DISPROVEN.isEmpty()) {
         DISPROVEN.clear();
         REVISION.incrementAndGet();
      }
   }

   public static void tick(ClientLevel level, BlockPos playerPos, Long worldSeed, int radius, int enabledMask) {
      if (level != null && playerPos != null && worldSeed != null) {
         ResourceKey<Level> dimension = level.dimension();
         if (!isSupportedDimension(dimension)) {
            synchronized (STATE_LOCK) {
               if (!unsupportedDimension
                  || activeKey != null
                  || !CHUNKS.isEmpty()
                  || !PENDING_CHUNKS.isEmpty()
                  || contextLoading
                  || generationInFlight
                  || unverifiedWorldgen) {
                  epoch++;
                  discardQueuedWork();
                  activeKey = null;
                  activeContext = null;
                  contextLoading = false;
                  generationInFlight = false;
                  tagVerificationInFlight = false;
                  initialChunkGenerated = false;
                  CHUNKS.clear();
                  PENDING_CHUNKS.clear();
                  DISPROVEN.clear();
                  totalPositions = 0;
                  REVISION.incrementAndGet();
               }

               failed = false;
               unsupportedDimension = true;
               unverifiedWorldgen = false;
               failureMessage = "Unsupported dimension " + dimension.identifier();
            }
         } else {
            RiptideOreSimEngine.ContextKey wanted = new RiptideOreSimEngine.ContextKey(worldSeed, dimension);
            synchronized (STATE_LOCK) {
               if (!wanted.equals(activeKey)) {
                  activate(wanted);
               }

               playerBlockX = playerPos.getX();
               playerBlockZ = playerPos.getZ();
               targetRadius = Math.max(1, Math.min(radius, 8));
               pruneOutOfRange();
               publishReadyPending();
               if (activeContext != null && !generationInFlight && !tagVerificationInFlight && System.nanoTime() >= nextTagVerificationNanos) {
                  startTagVerification(activeContext, epoch);
               } else if (!unverifiedWorldgen && !tagVerificationInFlight) {
                  if (enabledMask != 0 && !failed && !positionCapHit && activeContext != null && !generationInFlight) {
                     if (!initialChunkGenerated) {
                        int centerChunkX = SectionPos.blockToSectionCoord(playerBlockX);
                        int centerChunkZ = SectionPos.blockToSectionCoord(playerBlockZ);
                        long centerKey = ChunkPos.pack(centerChunkX, centerChunkZ);
                        if (!CHUNKS.containsKey(centerKey) && !PENDING_CHUNKS.containsKey(centerKey)) {
                           startInitialChunkGeneration(centerChunkX, centerChunkZ, activeContext, epoch);
                           return;
                        }

                        initialChunkGenerated = true;
                     }

                     RiptideOreSimEngine.Tile tile = nearestMissingTile(playerBlockX, playerBlockZ, targetRadius);
                     if (tile != null) {
                        startTileGeneration(tile, activeContext, epoch);
                     }
                  }
               }
            }
         }
      } else {
         clear();
      }
   }

   private static boolean isSupportedDimension(ResourceKey<Level> dimension) {
      return Level.OVERWORLD.equals(dimension) || Level.NETHER.equals(dimension) || Level.END.equals(dimension);
   }

   private static void activate(RiptideOreSimEngine.ContextKey wanted) {
      epoch++;
      discardQueuedWork();
      activeKey = wanted;
      activeContext = CONTEXT_CACHE.get(wanted);
      contextLoading = activeContext == null;
      generationInFlight = false;
      tagVerificationInFlight = false;
      initialChunkGenerated = false;
      failed = false;
      unsupportedDimension = false;
      unverifiedWorldgen = false;
      failureMessage = "";
      positionCapHit = false;
      nextTagVerificationNanos = 0L;
      totalPositions = 0;
      DISPROVEN.clear();
      if (!CHUNKS.isEmpty()) {
         CHUNKS.clear();
      }

      PENDING_CHUNKS.clear();
      REVISION.incrementAndGet();
      if (activeContext == null) {
         startContextLoad(wanted, epoch);
      }
   }

   private static void startContextLoad(RiptideOreSimEngine.ContextKey key, long token) {
      WORKER.submit(() -> {
         RiptideWorldgenContext built;
         boolean tagsVerified;
         try {
            built = RiptideWorldgenContext.create(key.seed(), key.dimension());
            tagsVerified = built.vanillaBlockTagsVerified();
         } catch (Throwable var8) {
            fail(token, "Minecraft 26.2 worldgen bootstrap failed", var8);
            return;
         }

         synchronized (STATE_LOCK) {
            if (token == epoch && key.equals(activeKey)) {
               CONTEXT_CACHE.put(key, built);
               activeContext = built;
               contextLoading = false;
               nextTagVerificationNanos = System.nanoTime() + TAG_VERIFY_INTERVAL_NANOS;
               if (!tagsVerified) {
                  markUnverifiedLocked("Minecraft block tags differ from the vanilla 26.2 worldgen profile");
               }
            }
         }
      });
   }

   private static void startInitialChunkGeneration(int chunkX, int chunkZ, RiptideWorldgenContext context, long token) {
      generationInFlight = true;
      WORKER.submit(() -> {
         try {
            try {
               if (!context.vanillaBlockTagsVerified()) {
                  markUnverified(token, "Minecraft block tags changed before initial chunk generation");
                  return;
               }

               int regionMinX = chunkX - 2;
               int regionMinZ = chunkZ - 2;
               Map<Long, ChunkAccess> generated = new RiptideRegionGenerator(context).generate(regionMinX, regionMinZ, 5, () -> token != epoch);
               long chunkKey = ChunkPos.pack(chunkX, chunkZ);
               ChunkAccess chunk = generated.get(chunkKey);
               if (chunk == null) {
                  throw new IllegalStateException("Generator omitted initial complete chunk " + chunkX + ", " + chunkZ);
               }

               RiptideOreSimEngine.ChunkOres ores = extract(chunk, () -> token != epoch);
               if (!context.vanillaBlockTagsVerified()) {
                  markUnverified(token, "Minecraft block tags changed during initial chunk generation");
                  return;
               }

               synchronized (STATE_LOCK) {
                  if (token != epoch || context != activeContext) {
                     return;
                  }

                  if (!CHUNKS.containsKey(chunkKey) && !PENDING_CHUNKS.containsKey(chunkKey)) {
                     if (totalPositions + ores.size() > 600000) {
                        positionCapHit = true;
                        return;
                     }

                     CHUNKS.put(chunkKey, ores);
                     totalPositions = totalPositions + ores.size();
                     REVISION.incrementAndGet();
                  }

                  nextTagVerificationNanos = System.nanoTime() + TAG_VERIFY_INTERVAL_NANOS;
                  initialChunkGenerated = true;
               }
            } catch (CancellationException var37) {
            } catch (Throwable var38) {
               fail(token, "Minecraft 26.2 initial chunk generation failed at " + chunkX + ", " + chunkZ, var38);
            }
         } finally {
            synchronized (STATE_LOCK) {
               if (token == epoch) {
                  generationInFlight = false;
               }
            }
         }
      });
   }

   private static void startTileGeneration(RiptideOreSimEngine.Tile tile, RiptideWorldgenContext context, long token) {
      generationInFlight = true;
      LongOpenHashSet alreadyComplete = new LongOpenHashSet(16);

      for (int dx = 0; dx < 4; dx++) {
         for (int dz = 0; dz < 4; dz++) {
            long key = ChunkPos.pack(tile.minChunkX() + dx, tile.minChunkZ() + dz);
            if (CHUNKS.containsKey(key) || PENDING_CHUNKS.containsKey(key)) {
               alreadyComplete.add(key);
            }
         }
      }

      WORKER.submit(() -> {
         try {
            if (!context.vanillaBlockTagsVerified()) {
               markUnverified(token, "Minecraft block tags changed before region generation");
            } else {
               int regionMinX = tile.minChunkX() - 2;
               int regionMinZ = tile.minChunkZ() - 2;
               Map<Long, ChunkAccess> generated = new RiptideRegionGenerator(context).generate(regionMinX, regionMinZ, 8, () -> token != epoch);
               Map<Long, RiptideOreSimEngine.ChunkOres> completed = new HashMap<>(16);

               for (int dxx = 0; dxx < 4; dxx++) {
                  for (int dzx = 0; dzx < 4; dzx++) {
                     int chunkX = tile.minChunkX() + dxx;
                     int chunkZ = tile.minChunkZ() + dzx;
                     long chunkKey = ChunkPos.pack(chunkX, chunkZ);
                     if (!alreadyComplete.contains(chunkKey)) {
                        ChunkAccess chunk = generated.get(chunkKey);
                        if (chunk == null) {
                           throw new IllegalStateException("Generator omitted complete chunk " + chunkX + ", " + chunkZ);
                        }

                        RiptideOreSimEngine.ChunkOres ores = extract(chunk, () -> token != epoch);
                        completed.put(chunkKey, ores);
                     }
                  }
               }

               if (!context.vanillaBlockTagsVerified()) {
                  markUnverified(token, "Minecraft block tags changed during region generation");
               } else {
                  synchronized (STATE_LOCK) {
                     if (token == epoch && context == activeContext) {
                        int added = 0;

                        for (Entry<Long, RiptideOreSimEngine.ChunkOres> entry : completed.entrySet()) {
                           if (!CHUNKS.containsKey(entry.getKey()) && !PENDING_CHUNKS.containsKey(entry.getKey())) {
                              added += entry.getValue().size();
                           }
                        }

                        if (totalPositions + added > 600000) {
                           positionCapHit = true;
                        } else {
                           for (Entry<Long, RiptideOreSimEngine.ChunkOres> entryx : completed.entrySet()) {
                              if (!CHUNKS.containsKey(entryx.getKey()) && !PENDING_CHUNKS.containsKey(entryx.getKey())) {
                                 PENDING_CHUNKS.put(entryx.getKey(), entryx.getValue());
                                 totalPositions = totalPositions + entryx.getValue().size();
                              }
                           }

                           nextTagVerificationNanos = System.nanoTime() + TAG_VERIFY_INTERVAL_NANOS;
                        }
                     }
                  }
               }
            }
         } catch (CancellationException var41) {
         } catch (Throwable var42) {
            fail(token, "Minecraft 26.2 region generation failed at tile " + tile.minChunkX() + ", " + tile.minChunkZ(), var42);
         } finally {
            synchronized (STATE_LOCK) {
               if (token == epoch) {
                  generationInFlight = false;
               }
            }
         }
      });
   }

   private static RiptideOreSimEngine.ChunkOres extract(ChunkAccess chunk, BooleanSupplier cancelled) {
      LongArrayList positions = new LongArrayList();
      IntArrayList stateIds = new IntArrayList();
      RiptideRegionGenerator.forEachBlock(chunk, state -> RiptideOreSimOre.isOreSimBlock(state.getBlock()), (x, y, z, state) -> {
         int stateId = RiptideOreSimOre.OreStates.internGenerated(state);
         if (stateId >= 0) {
            positions.add(BlockPos.asLong(x, y, z));
            stateIds.add(stateId);
         }
      }, cancelled);
      return positions.isEmpty() ? RiptideOreSimEngine.ChunkOres.EMPTY : new RiptideOreSimEngine.ChunkOres(positions.toLongArray(), stateIds.toIntArray());
   }

   private static void fail(long token, String message, Throwable error) {
      boolean accepted = false;
      synchronized (STATE_LOCK) {
         if (token != epoch) {
            return;
         }

         failed = true;
         contextLoading = false;
         generationInFlight = false;
         tagVerificationInFlight = false;
         initialChunkGenerated = false;
         failureMessage = message + ": " + error.getClass().getSimpleName() + (error.getMessage() == null ? "" : " - " + error.getMessage());
         activeContext = null;
         CHUNKS.clear();
         PENDING_CHUNKS.clear();
         totalPositions = 0;
         unverifiedWorldgen = false;
         REVISION.incrementAndGet();
         accepted = true;
      }

      if (accepted) {
         riptide.RiptideClientAddon.LOG.warn(message, error);
      }
   }

   private static void markUnverified(long token, String message) {
      synchronized (STATE_LOCK) {
         if (token == epoch) {
            markUnverifiedLocked(message);
         }
      }
   }

   private static void markUnverifiedLocked(String message) {
      epoch++;
      discardQueuedWork();
      contextLoading = false;
      generationInFlight = false;
      tagVerificationInFlight = false;
      initialChunkGenerated = false;
      failed = false;
      unsupportedDimension = false;
      unverifiedWorldgen = true;
      failureMessage = message;
      positionCapHit = false;
      nextTagVerificationNanos = System.nanoTime() + TAG_VERIFY_INTERVAL_NANOS;
      totalPositions = 0;
      CHUNKS.clear();
      PENDING_CHUNKS.clear();
      DISPROVEN.clear();
      REVISION.incrementAndGet();
   }

   private static void discardQueuedWork() {
      WORKER.cancelAll();
   }

   private static void startTagVerification(RiptideWorldgenContext context, long token) {
      tagVerificationInFlight = true;
      WORKER.submit(
         () -> {
            boolean verified = false;
            Throwable failure = null;

            try {
               verified = context.vanillaBlockTagsVerified();
            } catch (Throwable var8) {
               failure = var8;
            }

            if (failure != null) {
               riptide.RiptideClientAddon.LOG.warn("OreSim could not verify Minecraft block tags", failure);
            }

            synchronized (STATE_LOCK) {
               if (token == epoch && context == activeContext) {
                  tagVerificationInFlight = false;
                  nextTagVerificationNanos = System.nanoTime() + TAG_VERIFY_INTERVAL_NANOS;
                  if (!verified) {
                     markUnverifiedLocked(
                        failure == null ? "Minecraft block tags differ from the vanilla 26.2 worldgen profile" : "Minecraft block tags could not be verified"
                     );
                  } else {
                     if (unverifiedWorldgen) {
                        epoch++;
                        unverifiedWorldgen = false;
                        failureMessage = "";
                        initialChunkGenerated = false;
                        REVISION.incrementAndGet();
                     }
                  }
               }
            }
         }
      );
   }

   private static RiptideOreSimEngine.Tile nearestMissingTile(int blockX, int blockZ, int radius) {
      int centerChunkX = SectionPos.blockToSectionCoord(blockX);
      int centerChunkZ = SectionPos.blockToSectionCoord(blockZ);
      int minTileX = Math.floorDiv(centerChunkX - radius, 4);
      int maxTileX = Math.floorDiv(centerChunkX + radius, 4);
      int minTileZ = Math.floorDiv(centerChunkZ - radius, 4);
      int maxTileZ = Math.floorDiv(centerChunkZ + radius, 4);
      RiptideOreSimEngine.Tile best = null;

      for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
         for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
            int minChunkX = tileX * 4;
            int minChunkZ = tileZ * 4;
            if (!tileComplete(minChunkX, minChunkZ)) {
               double distance = distanceToTileSq(blockX + 0.5, blockZ + 0.5, minChunkX, minChunkZ);
               if (best == null || distance < best.distanceSq() || distance == best.distanceSq() && tileOrderBefore(minChunkX, minChunkZ, best)) {
                  best = new RiptideOreSimEngine.Tile(minChunkX, minChunkZ, distance);
               }
            }
         }
      }

      return best;
   }

   private static boolean tileComplete(int minChunkX, int minChunkZ) {
      for (int dx = 0; dx < 4; dx++) {
         for (int dz = 0; dz < 4; dz++) {
            long key = ChunkPos.pack(minChunkX + dx, minChunkZ + dz);
            if (!CHUNKS.containsKey(key) && !PENDING_CHUNKS.containsKey(key)) {
               return false;
            }
         }
      }

      return true;
   }

   private static double distanceToTileSq(double x, double z, int minChunkX, int minChunkZ) {
      double minX = minChunkX * 16.0 + 0.5;
      double minZ = minChunkZ * 16.0 + 0.5;
      double maxX = (minChunkX + 4) * 16.0 - 0.5;
      double maxZ = (minChunkZ + 4) * 16.0 - 0.5;
      double dx = x < minX ? minX - x : (x > maxX ? x - maxX : 0.0);
      double dz = z < minZ ? minZ - z : (z > maxZ ? z - maxZ : 0.0);
      return dx * dx + dz * dz;
   }

   private static boolean tileOrderBefore(int x, int z, RiptideOreSimEngine.Tile other) {
      return x < other.minChunkX() || x == other.minChunkX() && z < other.minChunkZ();
   }

   private static void publishReadyPending() {
      if (!PENDING_CHUNKS.isEmpty()) {
         NearestChunkFrontier.Chunk missing = nearestMissingChunk();
         boolean changed = false;
         Iterator<Entry<Long, RiptideOreSimEngine.ChunkOres>> iterator = PENDING_CHUNKS.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry<Long, RiptideOreSimEngine.ChunkOres> entry = iterator.next();
            int chunkX = ChunkPos.getX(entry.getKey());
            int chunkZ = ChunkPos.getZ(entry.getKey());
            if (NearestChunkFrontier.canPublish(playerBlockX + 0.5, playerBlockZ + 0.5, chunkX, chunkZ, missing)) {
               CHUNKS.put(entry.getKey(), entry.getValue());
               iterator.remove();
               changed = true;
            }
         }

         if (changed) {
            REVISION.incrementAndGet();
         }
      }
   }

   private static NearestChunkFrontier.Chunk nearestMissingChunk() {
      int centerX = SectionPos.blockToSectionCoord(playerBlockX);
      int centerZ = SectionPos.blockToSectionCoord(playerBlockZ);
      return NearestChunkFrontier.nearestMissing(
         playerBlockX + 0.5, playerBlockZ + 0.5, centerX, centerZ, targetRadius, key -> CHUNKS.containsKey(key) || PENDING_CHUNKS.containsKey(key)
      );
   }

   private static void pruneOutOfRange() {
      int centerX = SectionPos.blockToSectionCoord(playerBlockX);
      int centerZ = SectionPos.blockToSectionCoord(playerBlockZ);
      int keep = targetRadius + 4;
      int removed = 0;

      for (Entry<Long, RiptideOreSimEngine.ChunkOres> entry : CHUNKS.entrySet()) {
         long key = entry.getKey();
         if ((Math.abs(ChunkPos.getX(key) - centerX) > keep || Math.abs(ChunkPos.getZ(key) - centerZ) > keep) && CHUNKS.remove(key, entry.getValue())) {
            removed += entry.getValue().size();
         }
      }

      Iterator<Entry<Long, RiptideOreSimEngine.ChunkOres>> pending = PENDING_CHUNKS.entrySet().iterator();

      while (pending.hasNext()) {
         Entry<Long, RiptideOreSimEngine.ChunkOres> entryx = pending.next();
         long key = entryx.getKey();
         if (Math.abs(ChunkPos.getX(key) - centerX) > keep || Math.abs(ChunkPos.getZ(key) - centerZ) > keep) {
            removed += entryx.getValue().size();
            pending.remove();
         }
      }

      if (removed > 0) {
         totalPositions = Math.max(0, totalPositions - removed);
         positionCapHit = false;
         REVISION.incrementAndGet();
      }
   }

   public static boolean selectionTruncated() {
      return selectionTruncated;
   }

   public static int selectNearest(double eyeX, double eyeY, double eyeZ, int radius, IntPredicate stateAllowed, int cap, long[] outPos, int[] outState) {
      selectionTruncated = false;
      if (cap > 0 && outPos != null && outState != null && !CHUNKS.isEmpty()) {
         int limit = Math.min(cap, Math.min(outPos.length, outState.length));
         if (limit <= 0) {
            return 0;
         } else {
            NearestPositionSelector nearest = new NearestPositionSelector(limit);
            int centerX = SectionPos.blockToSectionCoord((int)Math.floor(eyeX));
            int centerZ = SectionPos.blockToSectionCoord((int)Math.floor(eyeZ));
            int drawRadius = Math.max(1, Math.min(radius, 8));
            boolean anyDisproven = !DISPROVEN.isEmpty();

            for (int ring = 0; ring <= drawRadius; ring++) {
               if (nearest.isFull()) {
                  double ringMin = Math.max(0.0, (ring - 1) * 16.0);
                  if (ringMin * ringMin > nearest.farthestDistanceSquared()) {
                     break;
                  }
               }

               for (int dx = -ring; dx <= ring; dx++) {
                  for (int dz = -ring; dz <= ring; dz++) {
                     if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                        RiptideOreSimEngine.ChunkOres ores = CHUNKS.get(ChunkPos.pack(centerX + dx, centerZ + dz));
                        if (ores != null && ores.positions.length != 0) {
                           for (int i = 0; i < ores.positions.length; i++) {
                              int stateId = ores.stateIds[i];
                              if (stateAllowed == null || stateAllowed.test(stateId)) {
                                 long packed = ores.positions[i];
                                 if (!anyDisproven || !DISPROVEN.contains(packed)) {
                                    double px = BlockPos.getX(packed) + 0.5 - eyeX;
                                    double py = BlockPos.getY(packed) + 0.5 - eyeY;
                                    double pz = BlockPos.getZ(packed) + 0.5 - eyeZ;
                                    double distance = px * px + py * py + pz * pz;
                                    nearest.offer(distance, packed, stateId);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            selectionTruncated = nearest.truncated();
            return nearest.writeNearestFirst(outPos, outState);
         }
      } else {
         return 0;
      }
   }

   public static void collectNear(BlockPos center, int blockRadius, LongOpenHashSet out) {
      if (center != null && out != null && !CHUNKS.isEmpty()) {
         int minX = center.getX() - blockRadius;
         int maxX = center.getX() + blockRadius;
         int minY = center.getY() - blockRadius;
         int maxY = center.getY() + blockRadius;
         int minZ = center.getZ() - blockRadius;
         int maxZ = center.getZ() + blockRadius;

         for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
               RiptideOreSimEngine.ChunkOres ores = CHUNKS.get(ChunkPos.pack(chunkX, chunkZ));
               if (ores != null) {
                  for (long packed : ores.positions) {
                     if (!DISPROVEN.contains(packed)) {
                        int x = BlockPos.getX(packed);
                        int y = BlockPos.getY(packed);
                        int z = BlockPos.getZ(packed);
                        if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) {
                           out.add(packed);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static void verifyNear(BlockPos center, int blockRadius, RiptideOreSimEngine.PositionJudge judge) {
      if (center != null && judge != null && !CHUNKS.isEmpty()) {
         int minX = center.getX() - blockRadius;
         int maxX = center.getX() + blockRadius;
         int minY = center.getY() - blockRadius;
         int maxY = center.getY() + blockRadius;
         int minZ = center.getZ() - blockRadius;
         int maxZ = center.getZ() + blockRadius;
         boolean changed = false;

         for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
               RiptideOreSimEngine.ChunkOres ores = CHUNKS.get(ChunkPos.pack(chunkX, chunkZ));
               if (ores != null) {
                  for (int i = 0; i < ores.positions.length; i++) {
                     long packed = ores.positions[i];
                     if (!DISPROVEN.contains(packed)) {
                        int x = BlockPos.getX(packed);
                        int y = BlockPos.getY(packed);
                        int z = BlockPos.getZ(packed);
                        if (x >= minX
                           && x <= maxX
                           && y >= minY
                           && y <= maxY
                           && z >= minZ
                           && z <= maxZ
                           && judge.shouldDrop(ores.stateIds[i], packed)
                           && DISPROVEN.size() < 200000) {
                           changed |= DISPROVEN.add(packed);
                        }
                     }
                  }
               }
            }
         }

         if (changed) {
            REVISION.incrementAndGet();
         }
      }
   }

   public static int accuracyPercent() {
      return -1;
   }

   private static final class ChunkOres {
      static final RiptideOreSimEngine.ChunkOres EMPTY = new RiptideOreSimEngine.ChunkOres(new long[0], new int[0]);
      final long[] positions;
      final int[] stateIds;

      ChunkOres(long[] positions, int[] stateIds) {
         this.positions = positions;
         this.stateIds = stateIds;
      }

      int size() {
         return this.positions.length;
      }
   }

   private record ContextKey(long seed, ResourceKey<Level> dimension) {
   }

   @FunctionalInterface
   public interface PositionJudge {
      boolean shouldDrop(int var1, long var2);
   }

   public static enum Status {
      IDLE,
      LOADING_CONTEXT,
      GENERATING,
      READY,
      UNVERIFIED_WORLDGEN,
      UNSUPPORTED_DIMENSION,
      ERROR;
   }

   public record TargetProgress(long loadId, int completedChunks, int totalChunks) {
      public TargetProgress(long loadId, int completedChunks, int totalChunks) {
         totalChunks = Math.max(0, totalChunks);
         completedChunks = Math.max(0, Math.min(completedChunks, totalChunks));
         this.loadId = loadId;
         this.completedChunks = completedChunks;
         this.totalChunks = totalChunks;
      }

      public double fraction() {
         return this.totalChunks == 0 ? 0.0 : (double)this.completedChunks / this.totalChunks;
      }

      public boolean complete() {
         return this.totalChunks > 0 && this.completedChunks >= this.totalChunks;
      }
   }

   private record Tile(int minChunkX, int minChunkZ, double distanceSq) {
      long key() {
         return ChunkPos.pack(this.minChunkX, this.minChunkZ);
      }
   }
}
