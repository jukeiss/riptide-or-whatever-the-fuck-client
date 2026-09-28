package riptide.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ModuleStorageEsp {
   public static final List<ModuleStorageEsp.Target> TARGETS = List.of(
      block("minecraft:chest", "Chest", () -> Blocks.CHEST.asItem().getDefaultInstance(), ChestBlockEntity.class),
      block("minecraft:trapped_chest", "Trapped Chest", () -> Blocks.TRAPPED_CHEST.asItem().getDefaultInstance(), TrappedChestBlockEntity.class),
      block("minecraft:ender_chest", "Ender Chest", () -> Blocks.ENDER_CHEST.asItem().getDefaultInstance(), EnderChestBlockEntity.class),
      block("minecraft:barrel", "Barrel", () -> Blocks.BARREL.asItem().getDefaultInstance(), BarrelBlockEntity.class),
      block("minecraft:shulker_box", "Shulker Box", () -> Blocks.SHULKER_BOX.asItem().getDefaultInstance(), ShulkerBoxBlockEntity.class),
      block("minecraft:hopper", "Hopper", () -> Blocks.HOPPER.asItem().getDefaultInstance(), HopperBlockEntity.class),
      block("minecraft:dispenser", "Dispenser", () -> Blocks.DISPENSER.asItem().getDefaultInstance(), DispenserBlockEntity.class),
      block("minecraft:dropper", "Dropper", () -> Blocks.DROPPER.asItem().getDefaultInstance(), DropperBlockEntity.class),
      block("minecraft:furnace", "Furnace", () -> Blocks.FURNACE.asItem().getDefaultInstance(), FurnaceBlockEntity.class),
      block("minecraft:smoker", "Smoker", () -> Blocks.SMOKER.asItem().getDefaultInstance(), SmokerBlockEntity.class),
      block("minecraft:blast_furnace", "Blast Furnace", () -> Blocks.BLAST_FURNACE.asItem().getDefaultInstance(), BlastFurnaceBlockEntity.class),
      block("minecraft:brewing_stand", "Brewing Stand", () -> Items.BREWING_STAND.getDefaultInstance(), BrewingStandBlockEntity.class),
      block("minecraft:crafter", "Crafter", () -> Blocks.CRAFTER.asItem().getDefaultInstance(), CrafterBlockEntity.class),
      block("minecraft:decorated_pot", "Decorated Pot", () -> Blocks.DECORATED_POT.asItem().getDefaultInstance(), DecoratedPotBlockEntity.class),
      block(
         "minecraft:chiseled_bookshelf",
         "Chiseled Bookshelf",
         () -> Blocks.CHISELED_BOOKSHELF.asItem().getDefaultInstance(),
         ChiseledBookShelfBlockEntity.class
      ),
      block("minecraft:campfire", "Campfire", () -> Blocks.CAMPFIRE.asItem().getDefaultInstance(), CampfireBlockEntity.class),
      entity("minecraft:chest_minecart", "Chest Minecart", () -> Items.CHEST_MINECART.getDefaultInstance(), MinecartChest.class),
      entity("minecraft:hopper_minecart", "Hopper Minecart", () -> Items.HOPPER_MINECART.getDefaultInstance(), MinecartHopper.class),
      entity("minecraft:furnace_minecart", "Furnace Minecart", () -> Items.FURNACE_MINECART.getDefaultInstance(), MinecartFurnace.class),
      entity("minecraft:chest_boat", "Chest Boat", () -> Items.OAK_CHEST_BOAT.getDefaultInstance(), AbstractChestBoat.class)
   );
   private static final Map<String, ModuleStorageEsp.Target> BY_ID;
   public static final String DEFAULT_VALUE;
   private static volatile ModuleStorageEsp.TargetSelection cachedSelection = new ModuleStorageEsp.TargetSelection("", List.of(), List.of());

   public static ModuleStorageEsp.Target byId(String id) {
      return id == null ? null : BY_ID.get(id.toLowerCase(Locale.ROOT));
   }

   private static ModuleStorageEsp.Target block(String id, String label, Supplier<ItemStack> iconSupplier, Class<? extends BlockEntity> beClass) {
      return new ModuleStorageEsp.Target(id, label, "Blocks", iconSupplier, beClass, null);
   }

   private static ModuleStorageEsp.Target entity(String id, String label, Supplier<ItemStack> iconSupplier, Class<? extends Entity> entityClass) {
      return new ModuleStorageEsp.Target(id, label, "Entities", iconSupplier, null, entityClass);
   }

   private ModuleStorageEsp() {
   }

   static void collect(Module module, ClientLevel level, Player player, float tickDelta, BiConsumer<AABB, Integer> emit) {
      collectTargets(module, level, player, tickDelta, emit == null ? null : (box, color, meshable) -> emit.accept(box, color), null);
   }

   static void collectTracePoints(Module module, ClientLevel level, Player player, float tickDelta, BiConsumer<Vec3, Integer> emit) {
      collectTargets(module, level, player, tickDelta, null, emit);
   }

   static void collectBoth(
      Module module, ClientLevel level, Player player, float tickDelta, BiConsumer<AABB, Integer> boxEmit, BiConsumer<Vec3, Integer> traceEmit
   ) {
      collectTargets(module, level, player, tickDelta, boxEmit == null ? null : (box, color, meshable) -> boxEmit.accept(box, color), traceEmit);
   }

   static void collectBothDetailed(
      Module module, ClientLevel level, Player player, float tickDelta, ModuleStorageEsp.BoxEmitter boxEmit, BiConsumer<Vec3, Integer> traceEmit
   ) {
      collectTargets(module, level, player, tickDelta, boxEmit, traceEmit);
   }

   private static void collectTargets(
      Module module, ClientLevel level, Player player, float tickDelta, ModuleStorageEsp.BoxEmitter boxEmit, BiConsumer<Vec3, Integer> traceEmit
   ) {
      if (module != null && level != null && player != null && (boxEmit != null || traceEmit != null)) {
         ModuleStorageEsp.TargetSelection selection = targetSelection(module.value("storage-list"));
         List<ModuleStorageEsp.Target> blockTargets = selection.blockTargets();
         List<ModuleStorageEsp.Target> entityTargets = selection.entityTargets();
         if (!blockTargets.isEmpty() || !entityTargets.isEmpty()) {
            ModuleStorageEsp.ColorSet colors = colorSet(module);
            int chunkRadius = ModuleRenderUtil.effectiveRenderChunkRadius();
            double var31 = 2.0 * (chunkRadius * 16.0 + 16.0) * (chunkRadius * 16.0 + 16.0);
            double maxDistSq = RiptideEspExtras.rangeSq(module, var31);
            Vec3 playerPos = player.position();
            int playerChunkX = player.chunkPosition().x();
            int playerChunkZ = player.chunkPosition().z();
            if (!blockTargets.isEmpty()) {
               ModuleEspChunkCache.onLevel(level);
               long hideMask = ModuleStorageStructures.enabledMask(module);
               String stamp = module.value("storage-list") + "|" + colors + "|" + hideMask;
               long gameTime = level.getGameTime();
               ClientChunkCache chunks = level.getChunkSource();

               for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                  for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                     LevelChunk chunk = chunks.getChunk(playerChunkX + dx, playerChunkZ + dz, ChunkStatus.FULL, false);
                     if (chunk != null) {
                        for (ModuleEspChunkCache.Entry entry : ModuleEspChunkCache.STORAGE_BE
                           .chunkEntries(chunk, gameTime, stamp, (scanned, out) -> scanChunkBlockEntities(level, scanned, blockTargets, colors, hideMask, out))) {
                           Vec3 trace = entry.trace();
                           if (!(sqDist(playerPos, trace.x, trace.y, trace.z) > maxDistSq)) {
                              if (boxEmit != null) {
                                 boxEmit.accept(entry.box(), entry.color(), true);
                              }

                              if (traceEmit != null) {
                                 traceEmit.accept(trace, entry.color());
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (!entityTargets.isEmpty()) {
               for (Entity entity : level.entitiesForRendering()) {
                  if (entity != null && entity.isAlive()) {
                     ModuleStorageEsp.Target matched = matchEntityTarget(entity, entityTargets);
                     if (matched != null && !(sqDist(playerPos, entity.getX(), entity.getY(), entity.getZ()) > maxDistSq)) {
                        AABB box = interpolatedBox(entity, tickDelta).inflate(0.05);
                        int color = entityColor(colors, entity);
                        if (boxEmit != null) {
                           boxEmit.accept(box, color, false);
                        }

                        if (traceEmit != null) {
                           traceEmit.accept(box.getCenter(), color);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void scanChunkBlockEntities(
      ClientLevel level,
      LevelChunk chunk,
      List<ModuleStorageEsp.Target> blockTargets,
      ModuleStorageEsp.ColorSet colors,
      long hideMask,
      List<ModuleEspChunkCache.Entry> out
   ) {
      Map<Integer, Long> sectionStructures = (Map<Integer, Long>)(hideMask == 0L ? Map.of() : new HashMap<>());

      for (Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
         BlockPos pos = entry.getKey();
         BlockEntity be = entry.getValue();
         if (pos != null
            && be != null
            && matchBlockTarget(be, blockTargets) != null
            && !ModuleStorageStructures.hidden(chunk, pos, hideMask, sectionStructures)) {
            out.add(
               new ModuleEspChunkCache.Entry(blockShapeBox(level, pos), new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), blockColor(colors, be))
            );
         }
      }
   }

   private static AABB blockShapeBox(ClientLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      VoxelShape shape = state.getShape(level, pos);
      return shape != null && !shape.isEmpty() ? shape.bounds().move(pos.getX(), pos.getY(), pos.getZ()) : new AABB(pos);
   }

   private static ModuleStorageEsp.Target matchBlockTarget(BlockEntity be, List<ModuleStorageEsp.Target> targets) {
      ModuleStorageEsp.Target best = null;
      int bestDepth = -1;

      for (ModuleStorageEsp.Target target : targets) {
         if (target.beClass != null && target.beClass.isInstance(be)) {
            int depth = ancestorDepth(be.getClass(), target.beClass);
            if (depth >= 0 && (best == null || depth < bestDepth)) {
               best = target;
               bestDepth = depth;
            }
         }
      }

      return best;
   }

   private static ModuleStorageEsp.Target matchEntityTarget(Entity entity, List<ModuleStorageEsp.Target> targets) {
      ModuleStorageEsp.Target best = null;
      int bestDepth = -1;

      for (ModuleStorageEsp.Target target : targets) {
         if (target.entityClass != null && target.entityClass.isInstance(entity)) {
            int depth = ancestorDepth(entity.getClass(), target.entityClass);
            if (depth >= 0 && (best == null || depth < bestDepth)) {
               best = target;
               bestDepth = depth;
            }
         }
      }

      return best;
   }

   private static int ancestorDepth(Class<?> clazz, Class<?> target) {
      int depth = 0;

      for (Class<?> c = clazz; c != null; depth++) {
         if (c == target) {
            return depth;
         }

         c = c.getSuperclass();
      }

      return -1;
   }

   private static int blockColor(ModuleStorageEsp.ColorSet colors, BlockEntity be) {
      if (be instanceof TrappedChestBlockEntity) {
         return colors.trappedChest();
      } else if (be instanceof ChestBlockEntity) {
         return colors.chest();
      } else if (be instanceof EnderChestBlockEntity) {
         return colors.enderChest();
      } else if (be instanceof BarrelBlockEntity) {
         return colors.barrel();
      } else if (be instanceof ShulkerBoxBlockEntity) {
         return colors.shulker();
      } else if (be instanceof HopperBlockEntity) {
         return colors.hopper();
      } else if (be instanceof DispenserBlockEntity) {
         return colors.dispenser();
      } else if (be instanceof AbstractFurnaceBlockEntity) {
         return colors.furnace();
      } else if (be instanceof BrewingStandBlockEntity) {
         return colors.furnace();
      } else if (be instanceof CrafterBlockEntity) {
         return colors.crafter();
      } else if (be instanceof DecoratedPotBlockEntity) {
         return colors.crafter();
      } else if (be instanceof ChiseledBookShelfBlockEntity) {
         return colors.crafter();
      } else {
         return be instanceof CampfireBlockEntity ? colors.crafter() : colors.other();
      }
   }

   private static int entityColor(ModuleStorageEsp.ColorSet colors, Entity entity) {
      if (entity instanceof MinecartChest) {
         return colors.chest();
      } else if (entity instanceof AbstractChestBoat) {
         return colors.chest();
      } else if (entity instanceof MinecartHopper) {
         return colors.hopper();
      } else {
         return entity instanceof MinecartFurnace ? colors.furnace() : colors.other();
      }
   }

   private static AABB interpolatedBox(Entity entity, float tickDelta) {
      double dx = Mth.lerp(tickDelta, entity.xOld, entity.getX()) - entity.getX();
      double dy = Mth.lerp(tickDelta, entity.yOld, entity.getY()) - entity.getY();
      double dz = Mth.lerp(tickDelta, entity.zOld, entity.getZ()) - entity.getZ();
      return entity.getBoundingBox().move(dx, dy, dz);
   }

   private static double sqDist(Vec3 from, double x, double y, double z) {
      double dx = from.x - x;
      double dy = from.y - y;
      double dz = from.z - z;
      return dx * dx + dy * dy + dz * dz;
   }

   private static int color(Module module, String option, int fallback) {
      return ModuleRenderUtil.color(module, option, fallback);
   }

   private static ModuleStorageEsp.TargetSelection targetSelection(String value) {
      String safe = value == null ? "" : value;
      ModuleStorageEsp.TargetSelection cached = cachedSelection;
      if (cached.value().equals(safe)) {
         return cached;
      } else {
         List<ModuleStorageEsp.Target> blockTargets = new ArrayList<>();
         List<ModuleStorageEsp.Target> entityTargets = new ArrayList<>();

         for (String raw : safe.split("\\|")) {
            String token = raw.trim();
            if (!token.isEmpty()) {
               ModuleStorageEsp.Target target = byId(token);
               if (target != null) {
                  if (target.isBlock()) {
                     blockTargets.add(target);
                  } else {
                     entityTargets.add(target);
                  }
               }
            }
         }

         ModuleStorageEsp.TargetSelection next = new ModuleStorageEsp.TargetSelection(safe, List.copyOf(blockTargets), List.copyOf(entityTargets));
         cachedSelection = next;
         return next;
      }
   }

   private static ModuleStorageEsp.ColorSet colorSet(Module module) {
      return new ModuleStorageEsp.ColorSet(
         color(module, "trapped-chest-color", -855695328),
         color(module, "chest-color", -855662592),
         color(module, "ender-chest-color", -864550657),
         color(module, "barrel-color", -855662592),
         color(module, "shulker-color", -860395777),
         color(module, "hopper-color", -864253185),
         color(module, "dispenser-color", -860862392),
         color(module, "furnace-color", -859000218),
         color(module, "crafter-color", -858214805),
         color(module, "other-color", -7566196)
      );
   }

   private static Identifier beTypeId(BlockEntityType<?> type) {
      return BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
   }

   static {
      Map<String, ModuleStorageEsp.Target> map = new LinkedHashMap<>();
      StringBuilder def = new StringBuilder();

      for (ModuleStorageEsp.Target target : TARGETS) {
         map.put(target.id, target);
         if (def.length() > 0) {
            def.append('|');
         }

         def.append(target.id);
      }

      BY_ID = Map.copyOf(map);
      DEFAULT_VALUE = def.toString();
   }

   @FunctionalInterface
   interface BoxEmitter {
      void accept(AABB var1, int var2, boolean var3);
   }

   private record ColorSet(int trappedChest, int chest, int enderChest, int barrel, int shulker, int hopper, int dispenser, int furnace, int crafter, int other) {
   }

   public static final class Id {
      public static final String CHEST = "minecraft:chest";
      public static final String TRAPPED_CHEST = "minecraft:trapped_chest";
      public static final String ENDER_CHEST = "minecraft:ender_chest";
      public static final String BARREL = "minecraft:barrel";
      public static final String SHULKER_BOX = "minecraft:shulker_box";
      public static final String HOPPER = "minecraft:hopper";
      public static final String DISPENSER = "minecraft:dispenser";
      public static final String DROPPER = "minecraft:dropper";
      public static final String FURNACE = "minecraft:furnace";
      public static final String SMOKER = "minecraft:smoker";
      public static final String BLAST_FURNACE = "minecraft:blast_furnace";
      public static final String BREWING_STAND = "minecraft:brewing_stand";
      public static final String CRAFTER = "minecraft:crafter";
      public static final String DECORATED_POT = "minecraft:decorated_pot";
      public static final String CHISELED_BOOKSHELF = "minecraft:chiseled_bookshelf";
      public static final String CAMPFIRE = "minecraft:campfire";
      public static final String CHEST_MINECART = "minecraft:chest_minecart";
      public static final String HOPPER_MINECART = "minecraft:hopper_minecart";
      public static final String FURNACE_MINECART = "minecraft:furnace_minecart";
      public static final String CHEST_BOAT = "minecraft:chest_boat";

      private Id() {
      }
   }

   public static final class Target {
      public final String id;
      public final String label;
      public final String group;
      private final Supplier<ItemStack> iconSupplier;
      private final boolean isBlockTarget;
      private final Class<? extends BlockEntity> beClass;
      private final Class<? extends Entity> entityClass;

      private Target(
         String id, String label, String group, Supplier<ItemStack> iconSupplier, Class<? extends BlockEntity> beClass, Class<? extends Entity> entityClass
      ) {
         this.id = id;
         this.label = label;
         this.group = group;
         this.iconSupplier = iconSupplier;
         this.isBlockTarget = beClass != null;
         this.beClass = beClass;
         this.entityClass = entityClass;
      }

      public ItemStack icon() {
         try {
            ItemStack stack = this.iconSupplier.get();
            return stack == null ? ItemStack.EMPTY : stack;
         } catch (Throwable var2) {
            return ItemStack.EMPTY;
         }
      }

      public boolean isBlock() {
         return this.isBlockTarget;
      }
   }

   private record TargetSelection(String value, List<ModuleStorageEsp.Target> blockTargets, List<ModuleStorageEsp.Target> entityTargets) {
   }
}
