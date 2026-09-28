package riptide.util.worldgen.mc26_2;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.VegetationFeatures;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.FossilFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TemplateFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TemplateFeatureConfiguration.TemplateEntry;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator.Context;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.apache.commons.lang3.mutable.MutableInt;

public final class RiptideSyntheticFeatureBridge {
   private RiptideSyntheticFeatureBridge() {
   }

   public static boolean placeFossil(FeaturePlaceContext<FossilFeatureConfiguration> context, RiptideSyntheticLevel synthetic) {
      RandomSource random = context.random();
      WorldGenLevel level = context.level();
      BlockPos origin = context.origin();
      Rotation rotation = Rotation.getRandom(random);
      FossilFeatureConfiguration config = (FossilFeatureConfiguration)context.config();
      int fossilIndex = random.nextInt(config.fossilStructures.size());
      StructureTemplateManager templates = synthetic.structureTemplates();
      StructureTemplate fossilBase = templates.getOrCreate((Identifier)config.fossilStructures.get(fossilIndex));
      StructureTemplate fossilOverlay = templates.getOrCreate((Identifier)config.overlayStructures.get(fossilIndex));
      ChunkPos chunkPos = ChunkPos.containing(origin);
      BoundingBox boundingBox = new BoundingBox(
         chunkPos.getMinBlockX() - 16,
         level.getMinY(),
         chunkPos.getMinBlockZ() - 16,
         chunkPos.getMaxBlockX() + 16,
         level.getMaxY(),
         chunkPos.getMaxBlockZ() + 16
      );
      StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setBoundingBox(boundingBox).setRandom(random);
      Vec3i size = fossilBase.getSize(rotation);
      BlockPos lowCorner = origin.offset(-size.getX() / 2, 0, -size.getZ() / 2);
      int lowestSurfaceY = origin.getY();

      for (int xscan = 0; xscan < size.getX(); xscan++) {
         for (int zscan = 0; zscan < size.getZ(); zscan++) {
            lowestSurfaceY = Math.min(lowestSurfaceY, level.getHeight(Types.OCEAN_FLOOR_WG, lowCorner.getX() + xscan, lowCorner.getZ() + zscan));
         }
      }

      int targetY = Math.max(lowestSurfaceY - 15 - random.nextInt(10), level.getMinY() + 10);
      BlockPos targetPos = fossilBase.getZeroPositionWithTransform(lowCorner.atY(targetY), Mirror.NONE, rotation);
      if (countEmptyCorners(level, fossilBase.getBoundingBox(settings, targetPos)) > config.maxEmptyCornersAllowed) {
         return false;
      } else {
         settings.clearProcessors();
         ((StructureProcessorList)config.fossilProcessors.value()).list().forEach(settings::addProcessor);
         fossilBase.placeInWorld(level, targetPos, targetPos, settings, random, 260);
         settings.clearProcessors();
         ((StructureProcessorList)config.overlayProcessors.value()).list().forEach(settings::addProcessor);
         fossilOverlay.placeInWorld(level, targetPos, targetPos, settings, random, 260);
         return true;
      }
   }

   private static int countEmptyCorners(WorldGenLevel level, BoundingBox structureBounds) {
      MutableInt count = new MutableInt(0);
      structureBounds.forAllCorners(pos -> {
         BlockState state = level.getBlockState(pos);
         if (state.isAir() || state.is(Blocks.LAVA) || state.is(Blocks.WATER)) {
            count.add(1);
         }
      });
      return count.intValue();
   }

   public static boolean placeTemplate(FeaturePlaceContext<TemplateFeatureConfiguration> context, RiptideSyntheticLevel synthetic) {
      RandomSource random = context.random();
      WorldGenLevel level = context.level();
      TemplateFeatureConfiguration config = (TemplateFeatureConfiguration)context.config();
      TemplateEntry entry = (TemplateEntry)config.templates().getRandomOrThrow(random);
      Rotation rotation = (Rotation)Util.getRandom(entry.rotations(), random);
      StructureTemplate template = synthetic.structureTemplates().getOrCreate(entry.template());
      Vec3i offsetX = rotatedOffset(rotation, Axis.X, template);
      Vec3i offsetZ = rotatedOffset(rotation, Axis.Z, template);
      BlockPos pos = context.origin().offset(offsetX).offset(offsetZ);
      StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setRandom(random);
      return template.placeInWorld(level, pos, pos, settings, random, 3);
   }

   private static Vec3i rotatedOffset(Rotation rotation, Axis axis, StructureTemplate template) {
      return rotation.rotate(axis.getNegative()).getUnitVec3i().multiply(template.getSize().get(axis) / 2);
   }

   public static void placePaleMoss(Context context, RiptideSyntheticLevel synthetic, float leavesProbability, float trunkProbability, float groundProbability) {
      RandomSource random = context.random();
      WorldGenLevel level = context.level();
      List<BlockPos> logs = Util.shuffledCopy(context.logs(), random);
      if (!logs.isEmpty()) {
         BlockPos origin = Collections.min(logs, Comparator.comparingInt(Vec3i::getY));
         if (random.nextFloat() < groundProbability) {
            level.registryAccess()
               .lookup(Registries.CONFIGURED_FEATURE)
               .flatMap(registry -> registry.get(VegetationFeatures.PALE_MOSS_PATCH))
               .ifPresent(mossPatch -> ((ConfiguredFeature)mossPatch.value()).place(level, synthetic.chunkGenerator(), random, origin.above()));
         }

         context.logs().forEach(pos -> {
            if (random.nextFloat() < trunkProbability) {
               BlockPos down = pos.below();
               if (context.isAir(down)) {
                  addMossHanger(down, context);
               }
            }
         });
         context.leaves().forEach(pos -> {
            if (random.nextFloat() < leavesProbability) {
               BlockPos down = pos.below();
               if (context.isAir(down)) {
                  addMossHanger(down, context);
               }
            }
         });
      }
   }

   private static void addMossHanger(BlockPos pos, Context context) {
      while (context.isAir(pos.below()) && !(context.random().nextFloat() < 0.5F)) {
         context.setBlock(pos, (BlockState)Blocks.PALE_HANGING_MOSS.defaultBlockState().setValue(HangingMossBlock.TIP, false));
         pos = pos.below();
      }

      context.setBlock(pos, (BlockState)Blocks.PALE_HANGING_MOSS.defaultBlockState().setValue(HangingMossBlock.TIP, true));
   }
}
