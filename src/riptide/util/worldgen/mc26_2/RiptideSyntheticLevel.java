package riptide.util.worldgen.mc26_2;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEvent.Context;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.WorldGenTickAccess;

public final class RiptideSyntheticLevel implements WorldGenLevel {
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();
   private static final FluidState EMPTY_FLUID = Blocks.AIR.defaultBlockState().getFluidState();
   private final RiptideWorldgenContext context;
   private final Map<Long, ChunkAccess> chunks;
   private final BiomeManager biomeManager;
   private final LevelLightEngine lightEngine;
   private RandomSource random;
   private long subTickCount;
   private int centerChunkX;
   private int centerChunkZ;
   private int writeRadius;
   private final WorldGenTickAccess<Block> blockTicks = new WorldGenTickAccess(pos -> this.chunkFor(pos).getBlockTicks());
   private final WorldGenTickAccess<Fluid> fluidTicks = new WorldGenTickAccess(pos -> this.chunkFor(pos).getFluidTicks());

   public RiptideSyntheticLevel(RiptideWorldgenContext context, Map<Long, ChunkAccess> chunks) {
      this.context = context;
      this.chunks = chunks;
      this.biomeManager = new BiomeManager(this, BiomeManager.obfuscateSeed(context.seed()));
      this.lightEngine = new LevelLightEngine(new LightChunkGetter() {
         {
            Objects.requireNonNull(RiptideSyntheticLevel.this);
         }

         public LightChunk getChunkForLighting(int chunkX, int chunkZ) {
            return RiptideSyntheticLevel.this.chunkAt(chunkX, chunkZ);
         }

         public BlockGetter getLevel() {
            return RiptideSyntheticLevel.this;
         }
      }, true, context.dimensionType().hasSkyLight());
      this.beginGeneration(new ChunkPos(0, 0), 0);
   }

   public void beginGeneration(ChunkPos center, int writeRadius) {
      this.random = this.context
         .randomState()
         .getOrCreateRandomFactory(Identifier.withDefaultNamespace("worldgen_region_random"))
         .at(center.getWorldPosition());
      this.subTickCount = 0L;
      this.centerChunkX = center.x();
      this.centerChunkZ = center.z();
      this.writeRadius = Math.max(0, writeRadius);
   }

   private ChunkAccess chunkAt(int chunkX, int chunkZ) {
      return this.chunks.get(ChunkPos.pack(chunkX, chunkZ));
   }

   private ChunkAccess chunkFor(BlockPos pos) {
      ChunkAccess chunk = this.chunkAt(pos.getX() >> 4, pos.getZ() >> 4);
      if (chunk == null) {
         throw new IllegalStateException("Generation touched unprepared chunk at " + pos);
      } else {
         return chunk;
      }
   }

   StructureTemplateManager structureTemplates() {
      return this.context.structureTemplates();
   }

   ChunkGenerator chunkGenerator() {
      return this.context.generator();
   }

   public LevelTickAccess<Block> getBlockTicks() {
      return this.blockTicks;
   }

   public LevelTickAccess<Fluid> getFluidTicks() {
      return this.fluidTicks;
   }

   public long getSeed() {
      return this.context.seed();
   }

   public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean require) {
      ChunkAccess chunk = this.chunkAt(chunkX, chunkZ);
      if (chunk == null && require) {
         throw new IllegalStateException("Generation asked for unprepared chunk " + chunkX + ", " + chunkZ);
      } else {
         return chunk;
      }
   }

   public boolean hasChunk(int chunkX, int chunkZ) {
      return this.chunkAt(chunkX, chunkZ) != null;
   }

   public BlockState getBlockState(BlockPos pos) {
      if (this.isOutsideBuildHeight(pos)) {
         return AIR;
      } else {
         ChunkAccess chunk = this.chunkAt(pos.getX() >> 4, pos.getZ() >> 4);
         return chunk == null ? AIR : chunk.getBlockState(pos);
      }
   }

   public FluidState getFluidState(BlockPos pos) {
      if (this.isOutsideBuildHeight(pos)) {
         return EMPTY_FLUID;
      } else {
         ChunkAccess chunk = this.chunkAt(pos.getX() >> 4, pos.getZ() >> 4);
         return chunk == null ? EMPTY_FLUID : chunk.getFluidState(pos);
      }
   }

   public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
      if (!this.isOutsideBuildHeight(pos) && this.ensureCanWrite(pos)) {
         ChunkAccess chunk = this.chunkAt(pos.getX() >> 4, pos.getZ() >> 4);
         if (chunk == null) {
            return false;
         } else {
            BlockState oldState = chunk.setBlockState(pos, state, flags);
            if (state.hasBlockEntity()) {
               if (chunk.getPersistedStatus().getChunkType() == ChunkType.LEVELCHUNK) {
                  BlockEntity blockEntity = ((EntityBlock)state.getBlock()).newBlockEntity(pos, state);
                  if (blockEntity != null) {
                     chunk.setBlockEntity(blockEntity);
                  } else {
                     chunk.removeBlockEntity(pos);
                  }
               } else {
                  CompoundTag tag = new CompoundTag();
                  tag.putInt("x", pos.getX());
                  tag.putInt("y", pos.getY());
                  tag.putInt("z", pos.getZ());
                  tag.putString("id", "DUMMY");
                  chunk.setBlockEntityNbt(tag);
               }
            } else if (oldState != null && oldState.hasBlockEntity()) {
               chunk.removeBlockEntity(pos);
            }

            if ((flags & 16) == 0) {
               BlockPos postProcess = state.getPostProcessPos(this, pos);
               if (postProcess != null) {
                  this.chunkFor(postProcess).markPosForPostProcessing(postProcess);
               }
            }

            return true;
         }
      } else {
         return false;
      }
   }

   public boolean ensureCanWrite(BlockPos pos) {
      int chunkX = SectionPos.blockToSectionCoord(pos.getX());
      int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
      return Math.abs(this.centerChunkX - chunkX) <= this.writeRadius && Math.abs(this.centerChunkZ - chunkZ) <= this.writeRadius;
   }

   public int getHeight(Types type, int x, int z) {
      ChunkAccess chunk = this.chunkAt(x >> 4, z >> 4);
      return chunk == null ? this.getMinY() : chunk.getHeight(type, x & 15, z & 15) + 1;
   }

   public Holder<Biome> getUncachedNoiseBiome(int quartX, int quartY, int quartZ) {
      return this.context.biomeSource().getNoiseBiome(quartX, quartY, quartZ, this.context.randomState().sampler());
   }

   public BiomeManager getBiomeManager() {
      return this.biomeManager;
   }

   public RegistryAccess registryAccess() {
      return this.context.registryAccess();
   }

   public DimensionType dimensionType() {
      return this.context.dimensionType();
   }

   public int getMinY() {
      return this.context.heightAccessor().getMinY();
   }

   public int getHeight() {
      return this.context.heightAccessor().getHeight();
   }

   public int getSeaLevel() {
      return this.context.generator().getSeaLevel();
   }

   public void setCurrentlyGenerating(Supplier<String> currentlyGenerating) {
   }

   public long getGameTime() {
      return 0L;
   }

   public ServerLevel getLevel() {
      throw new UnsupportedOperationException("Synthetic worldgen level has no server level");
   }

   public MinecraftServer getServer() {
      return null;
   }

   public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
      throw new UnsupportedOperationException("Synthetic worldgen level has no difficulty");
   }

   public WorldBorder getWorldBorder() {
      return new WorldBorder();
   }

   public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate) {
      return predicate.test(this.getFluidState(pos));
   }

   public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
      return predicate.test(this.getBlockState(pos));
   }

   public <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> type) {
      BlockEntity blockEntity = this.getBlockEntity(pos);
      return blockEntity != null && blockEntity.getType() == type ? Optional.of((T)blockEntity) : Optional.empty();
   }

   public BlockPos getHeightmapPos(Types type, BlockPos pos) {
      return new BlockPos(pos.getX(), this.getHeight(type, pos.getX(), pos.getZ()), pos.getZ());
   }

   public List<Player> players() {
      return List.of();
   }

   public List<Entity> getEntities(Entity except, AABB area, Predicate<? super Entity> filter) {
      return List.of();
   }

   public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> test, AABB area, Predicate<? super T> filter) {
      return List.of();
   }

   public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
      return this.chunkAt(chunkX, chunkZ);
   }

   public List<VoxelShape> getEntityCollisions(Entity entity, AABB area) {
      return List.of();
   }

   public ChunkSource getChunkSource() {
      throw new UnsupportedOperationException("Synthetic worldgen level has no chunk source");
   }

   public LevelData getLevelData() {
      throw new UnsupportedOperationException("Synthetic worldgen level has no level data");
   }

   public LevelLightEngine getLightEngine() {
      return this.lightEngine;
   }

   public BlockEntity getBlockEntity(BlockPos pos) {
      ChunkAccess chunk = this.chunkFor(pos);
      BlockEntity blockEntity = chunk.getBlockEntity(pos);
      if (blockEntity != null) {
         return blockEntity;
      } else {
         CompoundTag tag = chunk.getBlockEntityNbt(pos);
         BlockState state = chunk.getBlockState(pos);
         if (tag == null) {
            return null;
         } else {
            if ("DUMMY".equals(tag.getStringOr("id", ""))) {
               if (!state.hasBlockEntity()) {
                  return null;
               }

               blockEntity = ((EntityBlock)state.getBlock()).newBlockEntity(pos, state);
            } else {
               blockEntity = BlockEntity.loadStatic(pos, state, tag, this.registryAccess());
            }

            if (blockEntity != null) {
               chunk.setBlockEntity(blockEntity);
            }

            return blockEntity;
         }
      }
   }

   public boolean removeBlock(BlockPos pos, boolean move) {
      return this.setBlock(pos, AIR, 3, 512);
   }

   public boolean destroyBlock(BlockPos pos, boolean drop, Entity breaker, int recursionLeft) {
      return this.setBlock(pos, AIR, 3, recursionLeft);
   }

   public RandomSource getRandom() {
      return this.random;
   }

   public long nextSubTickCount() {
      return this.subTickCount++;
   }

   public int getSkyDarken() {
      return 0;
   }

   public boolean isClientSide() {
      return false;
   }

   public FeatureFlagSet enabledFeatures() {
      return FeatureFlags.DEFAULT_FLAGS;
   }

   public EnvironmentAttributeReader environmentAttributes() {
      throw new UnsupportedOperationException("Synthetic worldgen level has no environment attributes");
   }

   public void playSound(Entity source, BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch) {
   }

   public void addParticle(ParticleOptions particle, double x, double y, double z, double dx, double dy, double dz) {
   }

   public void levelEvent(Entity source, int type, BlockPos pos, int data) {
   }

   public void gameEvent(Holder<GameEvent> event, Vec3 pos, Context context) {
   }
}
