package riptide.util.worldgen.mc26_2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.Registry.PendingTags;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;

public final class RiptideWorldgenContext {
   private static final Object LOOKUP_LOCK = new Object();
   private static volatile RiptideWorldgenContext.VanillaBootstrap cachedBootstrap;
   private final long seed;
   private final ResourceKey<Level> dimension;
   private final Provider lookup;
   private final RegistryAccess registryAccess;
   private final ChunkGenerator generator;
   private final RandomState randomState;
   private final ChunkGeneratorStructureState structureState;
   private final StructureTemplateManager structureTemplates;
   private final DimensionType dimensionType;
   private final LevelHeightAccessor heightAccessor;

   private RiptideWorldgenContext(
      long seed,
      ResourceKey<Level> dimension,
      RiptideWorldgenContext.VanillaBootstrap bootstrap,
      ChunkGenerator generator,
      RandomState randomState,
      DimensionType dimensionType
   ) {
      this.seed = seed;
      this.dimension = dimension;
      this.lookup = bootstrap.registries();
      this.registryAccess = bootstrap.registries();
      this.generator = generator;
      this.randomState = randomState;
      this.structureState = generator.createState(this.lookup.lookupOrThrow(Registries.STRUCTURE_SET), randomState, seed);
      this.structureState.ensureStructuresGenerated();
      this.structureTemplates = bootstrap.structureTemplates();
      this.dimensionType = dimensionType;
      this.heightAccessor = LevelHeightAccessor.create(dimensionType.minY(), dimensionType.height());
   }

   public static RiptideWorldgenContext create(long seed, ResourceKey<Level> dimension) {
      RiptideWorldgenContext.VanillaBootstrap bootstrap = sharedBootstrap();
      Provider lookup = bootstrap.registries();
      ResourceKey<LevelStem> stemKey = stemKeyFor(dimension);
      WorldPreset preset = (WorldPreset)lookup.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value();
      LevelStem stem = (LevelStem)preset.createWorldDimensions().dimensions().get(stemKey);
      if (stem == null) {
         throw new IllegalStateException("No level stem for " + dimension);
      } else {
         ChunkGenerator generator = stem.generator();
         DimensionType dimensionType = (DimensionType)stem.type().value();
         RandomState randomState = randomStateFor(generator, lookup, seed);
         return new RiptideWorldgenContext(seed, dimension, bootstrap, generator, randomState, dimensionType);
      }
   }

   private static RandomState randomStateFor(ChunkGenerator generator, Provider lookup, long seed) {
      if (generator instanceof NoiseBasedChunkGenerator noise) {
         NoiseGeneratorSettings settings = (NoiseGeneratorSettings)noise.generatorSettings().value();
         return RandomState.create(settings, lookup.lookupOrThrow(Registries.NOISE), seed);
      } else {
         throw new IllegalStateException("Unsupported generator: " + generator.getClass().getName());
      }
   }

   private static ResourceKey<LevelStem> stemKeyFor(ResourceKey<Level> dimension) {
      if (Level.OVERWORLD.equals(dimension)) {
         return LevelStem.OVERWORLD;
      } else if (Level.NETHER.equals(dimension)) {
         return LevelStem.NETHER;
      } else if (Level.END.equals(dimension)) {
         return LevelStem.END;
      } else {
         throw new IllegalArgumentException("Unsupported vanilla dimension: " + dimension.identifier());
      }
   }

   private static RiptideWorldgenContext.VanillaBootstrap sharedBootstrap() {
      RiptideWorldgenContext.VanillaBootstrap bootstrap = cachedBootstrap;
      if (bootstrap != null) {
         return bootstrap;
      } else {
         synchronized (LOOKUP_LOCK) {
            if (cachedBootstrap == null) {
               cachedBootstrap = loadVanillaBootstrap();
            }

            return cachedBootstrap;
         }
      }
   }

   private static RiptideWorldgenContext.VanillaBootstrap loadVanillaBootstrap() {
      VanillaPackResources vanilla = ServerPacksSource.createVanillaPackSource();
      CloseableResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(vanilla));
      Executor direct = Runnable::run;
      LayeredRegistryAccess<RegistryLayer> initial = RegistryLayer.createRegistryAccess();
      List<PendingTags<?>> staticTags = TagLoader.loadTagsForExistingRegistries(resources, initial.getLayer(RegistryLayer.STATIC));
      Map<Identifier, List<Identifier>> vanillaBlockTags = ensureVanillaBlockTags(staticTags);
      List<RegistryLookup<?>> worldgenContext = TagLoader.buildUpdatedLookups(initial.getAccessForLoading(RegistryLayer.WORLDGEN), staticTags);
      Frozen worldgen = (Frozen)RegistryDataLoader.load(resources, worldgenContext, RegistryDataLoader.WORLDGEN_REGISTRIES, direct).join();
      List<RegistryLookup<?>> dimensionContext = Stream.concat(worldgenContext.stream(), worldgen.listRegistries()).toList();
      Frozen dimensions = (Frozen)RegistryDataLoader.load(resources, dimensionContext, RegistryDataLoader.DIMENSION_REGISTRIES, direct).join();
      Frozen registries = initial.replaceFrom(RegistryLayer.WORLDGEN, new Frozen[]{worldgen, dimensions}).compositeAccess();

      try {
         Path scratch = Files.createTempDirectory("riptide-oresim-26_2-");
         scratch.toFile().deleteOnExit();
         LevelStorageSource storageSource = LevelStorageSource.createDefault(scratch);
         LevelStorageAccess storage = storageSource.createAccess("templates");
         storage.getLevelPath(LevelResource.ROOT).toFile().deleteOnExit();
         StructureTemplateManager templates = new StructureTemplateManager(
            resources, storage, DataFixers.getDataFixer(), registries.lookupOrThrow(Registries.BLOCK).filterFeatures(FeatureFlags.DEFAULT_FLAGS)
         );
         return new RiptideWorldgenContext.VanillaBootstrap(registries, resources, storage, templates, vanillaBlockTags);
      } catch (IOException var15) {
         resources.close();
         throw new IllegalStateException("Could not create OreSim's temporary structure-template context", var15);
      }
   }

   private static Map<Identifier, List<Identifier>> ensureVanillaBlockTags(List<PendingTags<?>> pendingTags) {
      PendingTags<Block> vanillaBlocks = (PendingTags<Block>)pendingTags.stream()
         .filter(tags -> tags.key().equals(Registries.BLOCK))
         .findFirst()
         .orElseThrow(() -> new IllegalStateException("Vanilla datapack supplied no block tags"));
      Map<Identifier, List<Identifier>> expected = blockTagContents(vanillaBlocks.lookup());
      Map<Identifier, List<Identifier>> current = blockTagContents(BuiltInRegistries.BLOCK);
      if (current.isEmpty()) {
         pendingTags.forEach(PendingTags::apply);
         current = blockTagContents(BuiltInRegistries.BLOCK);
      }

      return Map.copyOf(expected);
   }

   private static Map<Identifier, List<Identifier>> blockTagContents(RegistryLookup<Block> lookup) {
      Map<Identifier, List<Identifier>> result = new TreeMap<>();

      try {
         lookup.listTags().forEach(tag -> {
            if ("minecraft".equals(tag.key().location().getNamespace())) {
               List<Identifier> blocks = tag.stream().map(holder -> ((ResourceKey)holder.unwrapKey().orElseThrow()).identifier()).sorted().toList();
               result.put(tag.key().location(), blocks);
            }
         });
      } catch (IllegalStateException var3) {
      }

      return result;
   }

   public long seed() {
      return this.seed;
   }

   public ResourceKey<Level> dimension() {
      return this.dimension;
   }

   public Provider lookup() {
      return this.lookup;
   }

   public RegistryAccess registryAccess() {
      return this.registryAccess;
   }

   public ChunkGenerator generator() {
      return this.generator;
   }

   public NoiseBasedChunkGenerator noiseGenerator() {
      return (NoiseBasedChunkGenerator)this.generator;
   }

   public BiomeSource biomeSource() {
      return this.generator.getBiomeSource();
   }

   public RandomState randomState() {
      return this.randomState;
   }

   public ChunkGeneratorStructureState structureState() {
      return this.structureState;
   }

   public StructureTemplateManager structureTemplates() {
      return this.structureTemplates;
   }

   public boolean vanillaBlockTagsVerified() {
      Map<Identifier, List<Identifier>> current = blockTagContents(BuiltInRegistries.BLOCK);

      for (Entry<Identifier, List<Identifier>> entry : cachedBootstrap.vanillaBlockTags().entrySet()) {
         if (!entry.getValue().equals(current.get(entry.getKey()))) {
            return false;
         }
      }

      return true;
   }

   public DimensionType dimensionType() {
      return this.dimensionType;
   }

   public LevelHeightAccessor heightAccessor() {
      return this.heightAccessor;
   }

   public PalettedContainerFactory palettedContainerFactory() {
      final List<Reference<Biome>> biomes = this.lookup.lookupOrThrow(Registries.BIOME).listElements().toList();
      final Map<Holder<Biome>, Integer> ids = new IdentityHashMap<>();

      for (int i = 0; i < biomes.size(); i++) {
         ids.put((Holder<Biome>)biomes.get(i), i);
      }

      IdMap<Holder<Biome>> biomeIds = new IdMap<Holder<Biome>>() {
         {
            Objects.requireNonNull(RiptideWorldgenContext.this);
         }

         public int getId(Holder<Biome> value) {
            Integer id = ids.get(value);
            return id == null ? -1 : id;
         }

         public Holder<Biome> byId(int id) {
            return id >= 0 && id < biomes.size() ? (Holder)biomes.get(id) : null;
         }

         public int size() {
            return biomes.size();
         }

         public Iterator<Holder<Biome>> iterator() {
            return biomes.stream().map(reference -> (Holder<Biome>)reference).iterator();
         }
      };
      Strategy<BlockState> blockStrategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
      BlockState defaultBlock = Blocks.AIR.defaultBlockState();
      Holder<Biome> defaultBiome = this.lookup.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
      return new PalettedContainerFactory(blockStrategy, defaultBlock, null, Strategy.createForBiomes(biomeIds), defaultBiome, null);
   }

   private record VanillaBootstrap(
      Frozen registries,
      CloseableResourceManager resources,
      LevelStorageAccess storage,
      StructureTemplateManager structureTemplates,
      Map<Identifier, List<Identifier>> vanillaBlockTags
   ) {
   }
}
