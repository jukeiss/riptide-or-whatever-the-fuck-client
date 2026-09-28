package riptide.modules;

import com.google.common.collect.BiMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.Plane;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.DebugStickItem;
import net.minecraft.world.item.EmptyMapItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.FoodOnAStickItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.KnowledgeBookItem;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.WritableBookItem;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.PumpkinBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.RegistryListSetting;
import riptide.mixin.accessor.RiptideMinecraftAccessor;
import riptide.util.RegistryListCodec;
import riptide.util.RiptideCombatClicker;
import riptide.util.RiptideFaceScan;
import riptide.util.RiptideFarmActionWatchdog;
import riptide.util.RiptideFarmBlocks;
import riptide.util.RiptideFarmPlanner;
import riptide.util.RiptideFarmReplantMemory;
import riptide.util.RiptideHandArbiter;
import riptide.util.RiptideKeyMappingBridge;
import riptide.util.RiptideKillAuraRotation;
import riptide.util.RiptideLiteVariant;
import riptide.util.RiptidePathWalker;
import riptide.util.RiptideRotationUtil;
import riptide.util.RiptideServerRotationView;
import riptide.util.RiptideSharedState;
import riptide.util.RiptideSilentAim;

public final class AutoFarmModule extends Module implements RiptideSilentAim.Owner {
   private static final int ARRIVAL_SETTLE_TICKS = 2;
   private static final int SWITCH_BACK_TICKS = 2;
   private static final float ROTATION_MATCH_EPSILON = 0.05F;
   private static final int RECENT_HARVEST_MAX = 4096;
   private static final int TARGET_STICKY_TICKS = 8;
   private static final int CELL_COOLDOWN_TICKS = 80;
   private static final int CELL_DRY_LIMIT = 6;
   private static final int DRY_AFTER_ACTION_LIMIT = 2;
   private static final float AIM_ARRIVED_DEGREES = 3.0F;
   private static final int PRESTAGE_REMAINING = 3;
   private static final int CLUSTER_RADIUS = 2;
   private static final int CLUSTER_MAX = 8;
   private static final int CLUSTER_PRESTAGED_EXTRA = 4;
   private static final int ROW_SAMPLE = 8;
   private static final int ROW_MIN_LINE = 3;
   private static final double ROW_LOOKAHEAD = 1.75;
   private static final int ROW_MAX_WATER_GAP = 2;
   private static final int ROW_PIVOT_NEAR = 2;
   private static final int ROW_PIVOT_MAX_OFFSET = 3;
   private static final int WALK_Y_BAND = 12;
   private static final int WALK_RESCAN_TICKS = 20;
   private static final int WALK_BLACKLIST_TICKS = 200;
   private static final int CONTINUATION_MAX_RING = 12;
   private static final float CONTINUATION_CONE_DEG = 90.0F;
   private static final Set<Block> TILLABLE = Set.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.DIRT_PATH, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT);
   private static final Set<Block> SHOVEL_FLATTENABLE = Set.of(
      Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.MYCELIUM
   );
   private static final Set<Block> AXE_STRIPPABLE = Set.of(
      Blocks.OAK_WOOD,
      Blocks.OAK_LOG,
      Blocks.DARK_OAK_WOOD,
      Blocks.DARK_OAK_LOG,
      Blocks.PALE_OAK_WOOD,
      Blocks.PALE_OAK_LOG,
      Blocks.ACACIA_WOOD,
      Blocks.ACACIA_LOG,
      Blocks.CHERRY_WOOD,
      Blocks.CHERRY_LOG,
      Blocks.BIRCH_WOOD,
      Blocks.BIRCH_LOG,
      Blocks.JUNGLE_WOOD,
      Blocks.JUNGLE_LOG,
      Blocks.SPRUCE_WOOD,
      Blocks.SPRUCE_LOG,
      Blocks.WARPED_STEM,
      Blocks.WARPED_HYPHAE,
      Blocks.CRIMSON_STEM,
      Blocks.CRIMSON_HYPHAE,
      Blocks.MANGROVE_WOOD,
      Blocks.MANGROVE_LOG,
      Blocks.BAMBOO_BLOCK
   );
   private final Random random = new Random();
   private final RiptideFarmReplantMemory<AutoFarmModule.Harvested> recentHarvest = new RiptideFarmReplantMemory<>(4096);
   private int lastActionTick = Integer.MIN_VALUE;
   private int holdTick = Integer.MIN_VALUE;
   private boolean holdKeyAttack;
   private int noHoldUntilTick = Integer.MIN_VALUE;
   private int previousSlot = -1;
   private int farmSlot = -1;
   private int switchBackTicks;
   private int hotbarChangeTick = Integer.MIN_VALUE;
   private int switchSettleTicks;
   private BlockPos walkTarget;
   private int walkScanTick = Integer.MIN_VALUE;
   private BlockPos rowContinuation;
   private int rowContinuationTick = Integer.MIN_VALUE;
   private BlockPos continuationTarget;
   private int continuationScanTick = Integer.MIN_VALUE;
   private BlockPos stickyPos;
   private RiptideFarmPlanner.Kind stickyKind;
   private int stickyTick = Integer.MIN_VALUE;
   private final Long2IntOpenHashMap cellCooldownStamp = new Long2IntOpenHashMap();
   private final Long2ObjectOpenHashMap<Block> cellCooldownBase = new Long2ObjectOpenHashMap();
   private BlockPos dryPos;
   private RiptideFarmPlanner.Kind dryKind;
   private int dryTicks;
   private BlockPos aimMemoPos;
   private RiptideFarmPlanner.Kind aimMemoKind;
   private RiptideRotationUtil.Rotation aimMemoGoal;
   private Vec3 aimMemoEye;
   private static final int NO_PROGRESS_LIMIT = 20;
   private static final int PLAN_BACKOFF_TICKS = 10;
   private BlockPos noProgPos;
   private RiptideFarmPlanner.Kind noProgKind;
   private float noProgBest = Float.MAX_VALUE;
   private int noProgTicks;
   private final Long2IntOpenHashMap planBackoffStamp = new Long2IntOpenHashMap();
   private static final int MICRO_GAP_TICKS = 3;
   private RiptideRotationUtil.Rotation lastAimGoal;
   private int lastAimTick = Integer.MIN_VALUE;
   private int lastActionKindTick = Integer.MIN_VALUE;
   private Axis sweepAxis;
   private int sweepSign = 1;
   private final int[] rowYOut = new int[1];
   private int rowLastCropY;
   private ClientLevel lastLevel;
   private String cachedCropRaw = "";
   private Set<Block> cachedCropBlocks = Set.of();
   private String lastInfo = "";
   private static int[] sphereCache = new int[0];
   private static int sphereRadius = -1;
   private String planningSettings;
   private int searchRangeTick = Integer.MIN_VALUE;
   private int searchRange;
   private boolean replantBeforeMoving;
   private static final int SOLVE_FAIL_LIMIT = 3;
   private BlockPos solveFailPos;
   private int solveFailTicks;
   private int grownOnlyTick = Integer.MIN_VALUE;
   private boolean grownOnly;
   private final Map<IntegerProperty, Integer> maxAgeByProperty = new IdentityHashMap<>();
   private final Map<BlockState, Boolean> grownVerdict = new IdentityHashMap<>();
   private final RiptideFarmActionWatchdog<AutoFarmModule.AttemptState> actionWatchdog = new RiptideFarmActionWatchdog<>();
   private int hotbarTick = Integer.MIN_VALUE;
   private int sweepTick = Integer.MIN_VALUE;
   private RiptideRotationUtil.Rotation sweepCached;
   private int cachedHoeSlot = -1;
   private int cachedFortuneSlot = -1;
   private Item cachedOffhandItem;
   private final Map<Item, Integer> cachedItemSlots = new HashMap<>();
   private Holder<Enchantment> fortuneHolder;
   private ClientLevel fortuneHolderLevel;
   private static final int SCAN_BUDGET_CELLS = 8000;
   private boolean scanning;
   private double scanMinDistSqr;
   private float scanMaxCorrection;
   private int scanMaxRing;
   private int scanManhattan;
   private long scanMaxSqr;
   private int scanOx;
   private int scanOy;
   private int scanOz;
   private double scanPx;
   private double scanPz;
   private Vec3 scanEye;
   private float scanLookYaw;
   private int scanRing;
   private int scanEdge;
   private int scanAy;
   private int scanSy;
   private final List<AutoFarmModule.WalkChoice> walkChoices = new ArrayList<>();

   AutoFarmModule() {
      super("auto-farm", "AutoFarm", ModuleCategory.PLAYER, "Harvests and replants crops.");
      this.add(new BoolSetting("movement", "Movement", false).description("Walk to distant crops").build());
      this.add(new IntSetting("search-range", "Search Range", 16, 4, 16, 1).description("Pathfinding search radius").build());
      this.add(
         RegistryListSetting.crops(
               "crops",
               "Crops",
               "minecraft:wheat|minecraft:carrots|minecraft:potatoes|minecraft:beetroots|minecraft:nether_wart|minecraft:pumpkin|minecraft:melon"
            )
            .description("Crop blocks to farm")
            .build()
      );
      this.add(new BoolSetting("grown-only", "Fully Grown Only", true).description("Harvest mature crops only").build());
      this.add(new BoolSetting("replant", "Replant", true).description("Replant after harvesting").build());
      this.add(new BoolSetting("plant", "Plant", true).description("Plant bare farmland").build());
      this.add(new BoolSetting("bonemeal", "Bonemeal", true).description("Fertilize crops to grow").build());
      this.add(new BoolSetting("fortune", "Prefer Fortune", true).description("Harvest with fortune tool").build());
      this.add(new BoolSetting("till", "Till Dirt", false).description("Hoe dirt into farmland").build());
      this.add(new BoolSetting("switch-back", "Switch Back", true).description("Return to previous slot").build());
   }

   @Override
   public void onEnable() {
      this.resetRuntime();
   }

   @Override
   public void onDisable() {
      this.resetRuntime();
      RiptideKillAuraRotation.beginWindDown(this.id());
      RiptideHandArbiter.releaseAll(this.id());
      RiptidePathWalker.reset();
   }

   @Override
   public void onGameLeft() {
      this.resetRuntime();
      if (this.ownsRotation()) {
         RiptideKillAuraRotation.reset();
      }

      RiptideHandArbiter.releaseAll(this.id());
      RiptidePathWalker.reset();
   }

   private void resetRuntime() {
      this.lastActionTick = Integer.MIN_VALUE;
      this.hotbarTick = Integer.MIN_VALUE;
      this.sweepTick = Integer.MIN_VALUE;
      this.sweepCached = null;
      this.grownOnlyTick = Integer.MIN_VALUE;
      this.searchRangeTick = Integer.MIN_VALUE;
      this.planningSettings = null;
      this.lastLevel = null;
      this.previousSlot = -1;
      this.farmSlot = -1;
      this.switchBackTicks = 0;
      this.hotbarChangeTick = Integer.MIN_VALUE;
      this.recentHarvest.clear();
      this.actionWatchdog.clear();
      this.walkTarget = null;
      this.walkScanTick = Integer.MIN_VALUE;
      this.rowContinuation = null;
      this.rowContinuationTick = Integer.MIN_VALUE;
      this.continuationTarget = null;
      this.continuationScanTick = Integer.MIN_VALUE;
      this.stickyPos = null;
      this.stickyKind = null;
      this.stickyTick = Integer.MIN_VALUE;
      this.cellCooldownStamp.clear();
      this.cellCooldownBase.clear();
      this.dryPos = null;
      this.dryKind = null;
      this.dryTicks = 0;
      this.solveFailPos = null;
      this.solveFailTicks = 0;
      this.aimMemoPos = null;
      this.aimMemoKind = null;
      this.aimMemoGoal = null;
      this.aimMemoEye = null;
      this.noProgPos = null;
      this.noProgKind = null;
      this.noProgBest = Float.MAX_VALUE;
      this.noProgTicks = 0;
      this.planBackoffStamp.clear();
      this.lastAimGoal = null;
      this.lastAimTick = Integer.MIN_VALUE;
      this.lastActionKindTick = Integer.MIN_VALUE;
      this.sweepAxis = null;
      this.sweepSign = 1;
      this.noHoldUntilTick = Integer.MIN_VALUE;
      this.scanning = false;
      this.releaseHoldNow();
      this.lastInfo = "";
   }

   @Override
   public String info() {
      return this.lastInfo;
   }

   @Override
   public void tick() {
      if (!this.isEnabled() && MC != null && MC.player != null && this.ownsRotation()) {
         RiptideKillAuraRotation.update(this.id(), MC.player);
      }
   }

   @Override
   public boolean ticksWhenDisabled() {
      return true;
   }

   @Override
   public boolean hasDisabledTickWork() {
      return RiptideKillAuraRotation.hasCurrentRotation() && this.ownsRotation();
   }

   @Override
   public boolean silentCorrectionApplies() {
      return RiptideSilentAim.scaffoldOwnsRotation() ? false : RiptideKillAuraRotation.isWindingDown() || this.isEnabled() && this.canRun();
   }

   @Override
   public void preMovementTick() {
      if (!RiptideLiteVariant.enabled()) {
         if (MC != null && MC.player != null && MC.level != null && MC.gameMode != null) {
            if (MC.level != this.lastLevel) {
               this.lastLevel = MC.level;
               this.hotbarTick = Integer.MIN_VALUE;
               this.sweepTick = Integer.MIN_VALUE;
               this.sweepCached = null;
               this.planningSettings = null;
               this.recentHarvest.clear();
               this.actionWatchdog.clear();
               this.walkTarget = null;
               this.rowContinuation = null;
               this.rowContinuationTick = Integer.MIN_VALUE;
               this.continuationTarget = null;
               this.continuationScanTick = Integer.MIN_VALUE;
               this.walkScanTick = Integer.MIN_VALUE;
               this.scanning = false;
               this.noHoldUntilTick = Integer.MIN_VALUE;
               this.stickyPos = null;
               this.sweepAxis = null;
               this.cellCooldownStamp.clear();
               this.cellCooldownBase.clear();
               this.dryPos = null;
               this.dryKind = null;
               this.dryTicks = 0;
               this.solveFailPos = null;
               this.solveFailTicks = 0;
               this.aimMemoPos = null;
               this.aimMemoKind = null;
               this.aimMemoGoal = null;
               this.aimMemoEye = null;
               this.noProgPos = null;
               this.noProgKind = null;
               this.noProgBest = Float.MAX_VALUE;
               this.noProgTicks = 0;
               this.planBackoffStamp.clear();
               this.lastAimGoal = null;
               this.lastAimTick = Integer.MIN_VALUE;
               RiptidePathWalker.reset();
               this.releaseHoldNow();
            }

            this.refreshPlanningSettings();
            this.pruneHarvestMemory();
            if (!this.canRun()) {
               this.standDown();
               this.tickSwitchBack();
            } else {
               AutoFarmModule.Target target = this.findTarget();
               if (target == null) {
                  this.lastInfo = this.idleHint();
                  this.releaseHoldUnlessAffirmed();
                  this.dryPos = null;
                  this.dryTicks = 0;
                  RiptideRotationUtil.Rotation graceGoal = this.bool("movement") ? null : this.microGapGoal();
                  if (this.bool("movement")) {
                     this.walkTick();
                  } else {
                     RiptidePathWalker.stop();
                     this.walkTarget = null;
                     this.pumpRotation(graceGoal);
                  }

                  if (graceGoal == null) {
                     this.armSwitchBack();
                     this.tickSwitchBack();
                  }
               } else {
                  this.switchBackTicks = 0;
                  boolean walkingThrough = this.bool("movement") && !this.replantBeforeMoving && this.walkThrough(target);
                  if (!walkingThrough) {
                     RiptidePathWalker.stop();
                  }

                  this.walkTarget = null;
                  RiptideRotationUtil.Rotation acted = this.tryAct(target);
                  if (acted != null) {
                     this.lastActionKindTick = RiptideSharedState.get().getClientTickCounter();
                     this.dryPos = null;
                     this.dryTicks = 0;
                     this.noProgPos = null;
                     this.noProgKind = null;
                     this.noProgBest = Float.MAX_VALUE;
                     this.noProgTicks = 0;

                     this.lastInfo = switch (target.kind()) {
                        case HARVEST -> "Harvest";
                        case BONEMEAL -> "Bonemeal";
                        case TILL -> "Till";
                        default -> "Replant";
                     };
                     this.lastAimGoal = acted;
                     this.lastAimTick = RiptideSharedState.get().getClientTickCounter();
                     this.pumpRotation(acted, true);
                  } else {
                     this.lastInfo = "";
                     this.releaseHoldUnlessAffirmed();
                     int tickNow = RiptideSharedState.get().getClientTickCounter();
                     int settleAge = tickNow - this.hotbarChangeTick;
                     boolean deliberatePause = tickNow == this.lastActionTick
                        || settleAge >= 0 && settleAge <= this.switchSettleTicks + 1
                        || tickNow < this.noHoldUntilTick;
                     if (!deliberatePause) {
                        this.noteDryTick(target, tickNow);
                        this.noteNoProgress(target, tickNow);
                     }

                     this.lastAimGoal = target.rotation();
                     this.lastAimTick = tickNow;
                     this.pumpRotation(target.rotation());
                  }
               }
            }
         } else {
            this.resetRuntime();
            RiptidePathWalker.reset();
         }
      }
   }

   private void refreshPlanningSettings() {
      String settings = this.bool("movement")
         + ":"
         + this.searchRange()
         + ":"
         + this.cropBlocks()
         + ":"
         + this.bool("grown-only")
         + ":"
         + this.bool("replant")
         + ":"
         + this.bool("plant")
         + ":"
         + this.bool("bonemeal")
         + ":"
         + this.bool("fortune")
         + ":"
         + this.bool("till");
      if (!settings.equals(this.planningSettings)) {
         this.planningSettings = settings;
         this.releaseHoldNow();
         RiptidePathWalker.reset();
         this.walkTarget = null;
         this.continuationTarget = null;
         this.rowContinuation = null;
         this.rowContinuationTick = Integer.MIN_VALUE;
         this.walkScanTick = Integer.MIN_VALUE;
         this.continuationScanTick = Integer.MIN_VALUE;
         this.scanning = false;
         this.stickyPos = null;
         this.aimMemoPos = null;
         this.lastAimGoal = null;
         this.cellCooldownStamp.clear();
         this.cellCooldownBase.clear();
         this.planBackoffStamp.clear();
         this.grownOnlyTick = Integer.MIN_VALUE;
         this.grownVerdict.clear();
      }
   }

   private RiptideRotationUtil.Rotation microGapGoal() {
      if (this.lastAimGoal == null) {
         return null;
      } else {
         int age = RiptideSharedState.get().getClientTickCounter() - this.lastAimTick;
         return age >= 0 && age <= 3 ? this.lastAimGoal : null;
      }
   }

   private String idleHint() {
      if (this.bool("till") && !this.hoeAvailable()) {
         return "No hoe";
      } else {
         if (this.bool("replant") || this.bool("plant")) {
            for (Block crop : this.cropBlocks()) {
               if (!RiptideFarmBlocks.isHarvestOnly(crop) && RiptideFarmBlocks.seedFor(crop) == null) {
                  return "No seed";
               }
            }
         }

         return this.bool("bonemeal") && !this.bonemealAvailable() && this.bonemealWorkInReach() ? "No bonemeal" : "";
      }
   }

   private boolean bonemealWorkInReach() {
      Set<Block> crops = this.cropBlocks();
      if (crops.isEmpty()) {
         return false;
      } else {
         Vec3 eye = MC.player.getEyePosition();
         double cullSqr = this.reach() * this.reach();
         int[] offsets = sphere(Mth.ceil(this.reach()) + 3);
         BlockPos origin = MC.player.blockPosition();
         int ox = origin.getX();
         int oy = origin.getY();
         int oz = origin.getZ();
         MutableBlockPos cursor = new MutableBlockPos();

         for (int i = 0; i < offsets.length; i += 3) {
            int cx = ox + offsets[i];
            int cy = oy + offsets[i + 1];
            int cz = oz + offsets[i + 2];
            if (!(cellDistanceSqr(eye, cx, cy, cz) > cullSqr)) {
               BlockState state = MC.level.getBlockState(cursor.set(cx, cy, cz));
               if (crops.contains(state.getBlock()) && this.bonemealable(cursor, state)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private boolean canRun() {
      if (MC == null || MC.player == null || MC.level == null || MC.gameMode == null) {
         return false;
      } else if (PackHideState.isActive() || PackFreecamState.isActive()) {
         return false;
      } else if (!MC.player.isAlive() || MC.player.isSpectator()) {
         return false;
      } else if (RiptideSilentAim.scaffoldOwnsRotation() || ScaffoldModule.reservesRageInput()) {
         return false;
      } else if (MC.player.isUsingItem() || MC.player.isHandsBusy()) {
         return false;
      } else if (this.foreignStreamOwner()) {
         return false;
      } else {
         return MC.gui == null || MC.gui.screen() != null || MC.gui.overlay() != null
            ? false
            : !AutoTotemModule.operationActive() && !AutoArmorModule.operationActive();
      }
   }

   private void standDown() {
      RiptidePathWalker.stop();
      this.releaseHoldNow();
      this.walkTarget = null;
      this.lastInfo = "";
      this.armSwitchBack();
      if (this.ownsRotation()) {
         RiptideKillAuraRotation.beginWindDown(this.id());
         if (MC != null && MC.player != null) {
            RiptideKillAuraRotation.update(this.id(), MC.player);
         }
      }
   }

   private boolean ownsRotation() {
      return this.id().equals(RiptideKillAuraRotation.currentOwner());
   }

   private boolean foreignStreamOwner() {
      String owner = RiptideKillAuraRotation.currentOwner();
      return owner != null && !this.id().equals(owner);
   }

   private void pumpRotation(RiptideRotationUtil.Rotation goal) {
      this.pumpRotation(goal, false);
   }

   private void pumpRotation(RiptideRotationUtil.Rotation goal, boolean pinQuiet) {
      if (goal != null) {
         if (!this.foreignStreamOwner()) {
            RiptideKillAuraRotation.setTarget(this.id(), 5, goal);
         }
      } else if (this.ownsRotation()) {
         RiptideKillAuraRotation.beginWindDown(this.id());
      }

      if (this.ownsRotation()) {
         RiptideKillAuraRotation.update(this.id(), MC.player, pinQuiet);
      }
   }

   private double reach() {
      return MC.player.blockInteractionRange();
   }

   private int searchRange() {
      int tick = RiptideSharedState.get().getClientTickCounter();
      if (tick != this.searchRangeTick) {
         this.searchRangeTick = tick;
         this.searchRange = this.integer("search-range");
      }

      return this.searchRange;
   }

   private AutoFarmModule.Target findTarget() {
      Set<Block> crops = this.cropBlocks();
      if (crops.isEmpty()) {
         return null;
      } else {
         double reach = this.reach();
         Vec3 eye = MC.player.getEyePosition();
         int[] offsets = sphere(Mth.ceil(reach) + 3);
         double cullSqr = reach * reach;
         int tick = RiptideSharedState.get().getClientTickCounter();
         boolean wantReplant = this.bool("replant");
         boolean wantPlant = this.bool("plant");
         int hoeSlot = this.bool("till") ? this.hoeSlot() : -1;
         boolean bonemealOffer = this.bool("bonemeal") && this.bonemealAvailable();
         BlockPos origin = MC.player.blockPosition();
         int ox = origin.getX();
         int oy = origin.getY();
         int oz = origin.getZ();
         MutableBlockPos cursor = new MutableBlockPos();
         RiptideRotationUtil.Rotation sweep = this.sweepRotation();
         BlockPos[][] candidateCells = new BlockPos[RiptideFarmPlanner.Kind.values().length][3];
         double[][] candidateKeys = new double[RiptideFarmPlanner.Kind.values().length][3];
         double[][] candidateDistances = new double[RiptideFarmPlanner.Kind.values().length][3];

         for (double[] keys : candidateKeys) {
            Arrays.fill(keys, Double.POSITIVE_INFINITY);
         }

         this.replantBeforeMoving = false;

         for (int i = 0; i < offsets.length; i += 3) {
            int cx = ox + offsets[i];
            int cy = oy + offsets[i + 1];
            int cz = oz + offsets[i + 2];
            double distanceSqr = cellDistanceSqr(eye, cx, cy, cz);
            if (!(distanceSqr > cullSqr)) {
               BlockState state = MC.level.getBlockState(cursor.set(cx, cy, cz));
               Block block = state.getBlock();
               if (crops.contains(block)) {
                  if (this.grownOk(state)
                     && this.harvestMemoryAvailable(cx, cy, cz, block)
                     && !this.cellCooled(cx, cy, cz, tick)
                     && !this.planBackoff(cx, cy, cz, tick)
                     && this.columnBaseOk(cursor, cx, cy, cz, block)) {
                     this.consider(RiptideFarmPlanner.Kind.HARVEST, cx, cy, cz, distanceSqr, sweep, eye, candidateCells, candidateKeys, candidateDistances);
                  } else if (bonemealOffer
                     && !this.cellCooled(cx, cy, cz, tick)
                     && !this.planBackoff(cx, cy, cz, tick)
                     && this.bonemealable(cursor.set(cx, cy, cz), state)) {
                     this.consider(RiptideFarmPlanner.Kind.BONEMEAL, cx, cy, cz, distanceSqr, sweep, eye, candidateCells, candidateKeys, candidateDistances);
                  }
               } else if ((wantReplant || wantPlant) && state.canBeReplaced()) {
                  if (!this.cellCooled(cx, cy, cz, tick) && !this.planBackoff(cx, cy, cz, tick)) {
                     BlockState below = MC.level.getBlockState(cursor.set(cx, cy - 1, cz));
                     if (wantReplant && this.recentChoiceAt(cx, cy, cz, below, crops, tick) != null) {
                        this.consider(
                           RiptideFarmPlanner.Kind.REPLANT_RECENT, cx, cy, cz, distanceSqr, sweep, eye, candidateCells, candidateKeys, candidateDistances
                        );
                     }

                     if (wantPlant && this.plainChoice(cx, cy, cz, below, crops) != null) {
                        this.consider(RiptideFarmPlanner.Kind.REPLANT, cx, cy, cz, distanceSqr, sweep, eye, candidateCells, candidateKeys, candidateDistances);
                     }
                  }
               } else if (this.bool("till")
                  && this.hoeAvailable()
                  && TILLABLE.contains(block)
                  && (block == Blocks.ROOTED_DIRT || MC.level.getBlockState(cursor.set(cx, cy + 1, cz)).isAir())
                  && !this.cellCooled(cx, cy, cz, tick)
                  && !this.planBackoff(cx, cy, cz, tick)) {
                  this.consider(RiptideFarmPlanner.Kind.TILL, cx, cy, cz, distanceSqr, sweep, eye, candidateCells, candidateKeys, candidateDistances);
               }
            }
         }

         List<RiptideFarmPlanner.Option<AutoFarmModule.Target>> options = new ArrayList<>();

         for (RiptideFarmPlanner.Kind kind : RiptideFarmPlanner.Kind.values()) {
            for (BlockPos cell : candidateCells[kind.ordinal()]) {
               if (cell == null) {
                  break;
               }

               AutoFarmModule.Target candidate = this.rebuildTarget(cell, kind, crops, hoeSlot, tick);
               if (candidate != null) {
                  options.add(this.planningOption(candidate, eye));
                  break;
               }

               this.stampPlanBackoff(cell, tick);
            }
         }

         AutoFarmModule.Target previous = null;
         int stickyAge = tick - this.stickyTick;
         if (this.stickyPos != null
            && stickyAge >= 0
            && stickyAge <= 8
            && cellDistanceSqr(eye, this.stickyPos.getX(), this.stickyPos.getY(), this.stickyPos.getZ()) <= cullSqr
            && !this.cellCooled(this.stickyPos.getX(), this.stickyPos.getY(), this.stickyPos.getZ(), tick)
            && !this.planBackoff(this.stickyPos.getX(), this.stickyPos.getY(), this.stickyPos.getZ(), tick)) {
            previous = this.rebuildTarget(this.stickyPos, this.stickyKind, crops, hoeSlot, tick);
            if (previous != null) {
               AutoFarmModule.Target existing = options.stream()
                  .map(RiptideFarmPlanner.Option::target)
                  .filter(t -> t.kind() == previous.kind() && t.pos().equals(previous.pos()))
                  .findFirst()
                  .orElse(null);
               if (existing != null) {
                  previous = existing;
               } else {
                  options.add(this.planningOption(previous, eye));
               }
            }
         }

         AutoFarmModule.Target chosen = RiptideFarmPlanner.choose(options, sweep.yaw(), sweep.pitch(), MC.player.getInventory().getSelectedSlot(), previous);
         if (chosen == null) {
            this.stickyPos = null;
            return null;
         } else {
            if (!chosen.pos().equals(this.stickyPos) || chosen.kind() != this.stickyKind) {
               this.stickyTick = tick;
            }

            this.stickyPos = chosen.pos();
            this.stickyKind = chosen.kind();
            this.rowContinuationTick = Integer.MIN_VALUE;
            if (chosen.kind() == RiptideFarmPlanner.Kind.HARVEST) {
               AutoFarmModule.Target solved = this.buildHarvest(chosen.pos());
               if (solved != null) {
                  chosen = solved;
               }
            }

            return chosen;
         }
      }
   }

   private RiptideFarmPlanner.Option<AutoFarmModule.Target> planningOption(AutoFarmModule.Target target, Vec3 eye) {
      int slot = target.hotbarSlot();
      if (target.seed() != null) {
         BlockHitResult hit = ScaffoldModule.grimClickRay(eye, target.rotation(), this.reach(), MC.level, MC.player);
         if (hit != null && this.offhandUsable(target.seed(), hit)) {
            slot = -1;
         }
      }

      boolean urgent = target.kind() == RiptideFarmPlanner.Kind.REPLANT_RECENT && this.bool("movement") && this.replantWouldLeaveReach(target.pos(), eye);
      this.replantBeforeMoving |= urgent;
      return new RiptideFarmPlanner.Option<>(target, target.kind(), target.rotation().yaw(), target.rotation().pitch(), slot, urgent);
   }

   private boolean replantWouldLeaveReach(BlockPos cell, Vec3 eye) {
      Vec3 motion = MC.player.getDeltaMovement();
      double dx = motion.x * 8.0;
      double dz = motion.z * 8.0;
      if (dx * dx + dz * dz < 0.04) {
         double yaw = Math.toRadians(this.sweepRotation().yaw());
         dx = -Math.sin(yaw) * 1.6;
         dz = Math.cos(yaw) * 1.6;
      }

      return cellDistanceSqr(eye.add(dx, 0.0, dz), cell.getX(), cell.getY(), cell.getZ()) > this.reach() * this.reach() - 0.5;
   }

   private AutoFarmModule.Target rebuildTarget(BlockPos pos, RiptideFarmPlanner.Kind kind, Set<Block> crops, int hoeSlot, int tick) {
      switch (kind) {
         case HARVEST:
            BlockState state = MC.level.getBlockState(pos);
            if (crops.contains(state.getBlock()) && this.grownOk(state) && this.harvestMemoryAvailable(pos.getX(), pos.getY(), pos.getZ(), state.getBlock())) {
               if (RiptideFarmBlocks.isColumnCrop(state.getBlock()) && !MC.level.getBlockState(pos.below()).is(state.getBlock())) {
                  return null;
               }

               return this.buildHarvest(pos);
            }

            return null;
         case BONEMEAL:
            if (!this.bool("bonemeal")) {
               return null;
            }

            return this.bonemealOfferAt(pos) ? this.buildBonemeal(pos) : null;
         case TILL:
            return this.tillOfferAt(pos) ? this.buildTill(pos, hoeSlot) : null;
         case REPLANT_RECENT:
         case REPLANT:
            if (kind == RiptideFarmPlanner.Kind.REPLANT_RECENT ? this.bool("replant") : this.bool("plant")) {
               return !MC.level.getBlockState(pos).canBeReplaced() ? null : this.buildReplant(pos, kind == RiptideFarmPlanner.Kind.REPLANT_RECENT, tick);
            } else {
               return null;
            }
         default:
            return null;
      }
   }

   private boolean cellCooled(int x, int y, int z, int tick) {
      long key = BlockPos.asLong(x, y, z);
      if (!this.cellCooldownStamp.containsKey(key)) {
         return false;
      } else {
         int stamp = this.cellCooldownStamp.get(key);
         int age = tick - stamp;
         if (age >= 0 && age <= 80) {
            Block base = (Block)this.cellCooldownBase.get(key);
            if (base != null && MC.level.getBlockState(new BlockPos(x, y - 1, z)).getBlock() != base) {
               this.cellCooldownStamp.remove(key);
               this.cellCooldownBase.remove(key);
               return false;
            } else {
               return true;
            }
         } else {
            this.cellCooldownStamp.remove(key);
            this.cellCooldownBase.remove(key);
            return false;
         }
      }
   }

   private void coolCellAfterSolveFail(BlockPos pos, int tick) {
      if (pos.equals(this.solveFailPos)) {
         this.solveFailTicks++;
      } else {
         this.solveFailPos = pos;
         this.solveFailTicks = 1;
      }

      if (this.solveFailTicks >= 3) {
         this.coolCell(pos, tick);
         this.solveFailPos = null;
         this.solveFailTicks = 0;
      }
   }

   private void coolCell(BlockPos pos, int tick) {
      if (this.cellCooldownStamp.size() >= 64) {
         this.cellCooldownStamp.entrySet().removeIf(entry -> {
            int age = tick - (Integer)entry.getValue();
            if (age >= 0 && age <= 80) {
               return false;
            } else {
               this.cellCooldownBase.remove(entry.getKey());
               return true;
            }
         });
      }

      this.cellCooldownStamp.put(pos.asLong(), tick);
      this.cellCooldownBase.put(pos.asLong(), MC.level.getBlockState(pos.below()).getBlock());
   }

   private void noteDryTick(AutoFarmModule.Target target, int tick) {
      if (!this.foreignStreamOwner()
         && !BedDefenderModule.ownsSilentRotation()
         && !SurroundModule.ownsSilentRotation()
         && !CrystalAuraModule.reservesCombatTick()
         && !AnchorAuraModule.reservesCombatTick()
         && !RiptideBlinkManager.holdsActionsWithoutMovement()) {
         if (RiptideServerRotationView.snapshot().initialized()) {
            RiptideRotationUtil.Rotation goal = target.rotation();
            RiptideRotationUtil.Rotation wire = this.sweepRotation();
            boolean arrived = Math.abs(RiptideRotationUtil.angleDifference(wire.yaw(), goal.yaw())) <= 3.0F && Math.abs(wire.pitch() - goal.pitch()) <= 3.0F;
            if (!arrived) {
               this.dryPos = null;
               this.dryTicks = 0;
            } else {
               if (target.pos().equals(this.dryPos) && target.kind() == this.dryKind) {
                  this.dryTicks++;
               } else {
                  this.dryPos = target.pos();
                  this.dryKind = target.kind();
                  this.dryTicks = 1;
               }

               if (this.dryTicks >= this.dryLimit(tick)) {
                  this.coolCell(target.pos(), tick);
                  this.dryPos = null;
                  this.dryTicks = 0;
               }
            }
         }
      }
   }

   private int dryLimit(int tick) {
      int actionAge = tick - this.lastActionKindTick;
      return actionAge >= 0 && actionAge <= 2 ? 2 : 6;
   }

   private void noteNoProgress(AutoFarmModule.Target target, int tick) {
      if (!this.foreignStreamOwner()
         && !BedDefenderModule.ownsSilentRotation()
         && !SurroundModule.ownsSilentRotation()
         && !CrystalAuraModule.reservesCombatTick()
         && !AnchorAuraModule.reservesCombatTick()
         && !RiptideBlinkManager.holdsActionsWithoutMovement()) {
         if (RiptideServerRotationView.snapshot().initialized()) {
            RiptideRotationUtil.Rotation goal = target.rotation();
            RiptideRotationUtil.Rotation wire = this.sweepRotation();
            float distance = Math.abs(RiptideRotationUtil.angleDifference(wire.yaw(), goal.yaw())) + Math.abs(wire.pitch() - goal.pitch());
            if (!target.pos().equals(this.noProgPos) || target.kind() != this.noProgKind) {
               this.noProgPos = target.pos();
               this.noProgKind = target.kind();
               this.noProgBest = distance;
               this.noProgTicks = 0;
            } else if (distance < this.noProgBest - 0.5F) {
               this.noProgBest = distance;
               this.noProgTicks = 0;
            } else {
               this.noProgTicks++;
            }

            if (this.noProgTicks >= 20) {
               this.stampPlanBackoff(target.pos(), tick);
               this.noProgPos = null;
               this.noProgKind = null;
               this.noProgBest = Float.MAX_VALUE;
               this.noProgTicks = 0;
            }
         }
      }
   }

   private void stampPlanBackoff(BlockPos pos, int tick) {
      this.planBackoffStamp.put(pos.asLong(), tick);
   }

   private boolean planBackoff(int x, int y, int z, int tick) {
      long key = BlockPos.asLong(x, y, z);
      int stamp = this.planBackoffStamp.getOrDefault(key, Integer.MIN_VALUE);
      if (stamp == Integer.MIN_VALUE) {
         return false;
      } else {
         int age = tick - stamp;
         if (age >= 0 && age <= 10) {
            return true;
         } else {
            this.planBackoffStamp.remove(key);
            return false;
         }
      }
   }

   private boolean columnBaseOk(MutableBlockPos cursor, int x, int y, int z, Block block) {
      return !RiptideFarmBlocks.isColumnCrop(block) || MC.level.getBlockState(cursor.set(x, y - 1, z)).is(block);
   }

   private void consider(
      RiptideFarmPlanner.Kind kind,
      int x,
      int y,
      int z,
      double distanceSqr,
      RiptideRotationUtil.Rotation sweep,
      Vec3 eye,
      BlockPos[][] cells,
      double[][] keys,
      double[][] distances
   ) {
      int group = kind.ordinal();
      double dx = x + 0.5 - eye.x;
      double dy = y + 0.5 - eye.y;
      double dz = z + 0.5 - eye.z;
      float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
      float pitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
      double key = Math.hypot(RiptideRotationUtil.angleDifference(sweep.yaw(), yaw), pitch - sweep.pitch());
      if (kind == RiptideFarmPlanner.Kind.REPLANT_RECENT && this.bool("movement") && this.replantWouldLeaveReach(new BlockPos(x, y, z), eye)) {
         key -= 360.0;
      }

      for (int i = 0; i < cells[group].length; i++) {
         if (!(key > keys[group][i]) && (key != keys[group][i] || !(distanceSqr >= distances[group][i]))) {
            for (int j = cells[group].length - 1; j > i; j--) {
               cells[group][j] = cells[group][j - 1];
               keys[group][j] = keys[group][j - 1];
               distances[group][j] = distances[group][j - 1];
            }

            cells[group][i] = new BlockPos(x, y, z);
            keys[group][i] = key;
            distances[group][i] = distanceSqr;
            break;
         }
      }
   }

   private RiptideRotationUtil.Rotation sweepRotation() {
      int tick = RiptideSharedState.get().getClientTickCounter();
      if (tick == this.sweepTick && this.sweepCached != null) {
         return this.sweepCached;
      } else {
         this.sweepTick = tick;
         RiptideServerRotationView.WireSnapshot wire = RiptideServerRotationView.snapshot();
         this.sweepCached = wire.initialized()
            ? new RiptideRotationUtil.Rotation(wire.currentYaw(), wire.currentPitch())
            : RiptideRotationUtil.playerRotation(MC.player);
         return this.sweepCached;
      }
   }

   private boolean grownOk(BlockState state) {
      return !this.grownOnly() ? true : this.atMaxAge(state);
   }

   private boolean atMaxAge(BlockState state) {
      Boolean cached = this.grownVerdict.get(state);
      if (cached != null) {
         return cached;
      } else {
         boolean verdict = true;

         for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty age && "age".equals(age.getName())) {
               verdict = (Integer)state.getValue(age) >= this.maxAge(age);
               break;
            }
         }

         this.grownVerdict.put(state, verdict);
         return verdict;
      }
   }

   private boolean grownOnly() {
      int tick = RiptideSharedState.get().getClientTickCounter();
      if (tick != this.grownOnlyTick) {
         this.grownOnlyTick = tick;
         this.grownOnly = this.bool("grown-only");
      }

      return this.grownOnly;
   }

   private int maxAge(IntegerProperty age) {
      return this.maxAgeByProperty.computeIfAbsent(age, key -> Collections.max(key.getPossibleValues()));
   }

   private Set<Block> cropBlocks() {
      String raw = this.value("crops");
      if (raw.equals(this.cachedCropRaw)) {
         return this.cachedCropBlocks;
      } else {
         Set<Block> blocks = new LinkedHashSet<>();

         for (String entry : this.list("crops")) {
            Identifier identifier = Identifier.tryParse(RegistryListCodec.normalizeId(entry));
            if (identifier != null) {
               BuiltInRegistries.BLOCK.getOptional(identifier).filter(RiptideFarmBlocks::isFarmable).ifPresent(blocks::add);
            }
         }

         this.cachedCropRaw = raw;
         this.cachedCropBlocks = Collections.unmodifiableSet(blocks);
         return this.cachedCropBlocks;
      }
   }

   private AutoFarmModule.SeedChoice plainChoice(int x, int y, int z, BlockState below, Set<Block> crops) {
      AutoFarmModule.Harvested ownCell = this.recentHarvest.get(BlockPos.asLong(x, y, z));
      if (ownCell != null) {
         return this.seedChoiceFor(ownCell.crop(), below, crops, x, y, z);
      } else {
         Block preferred = null;
         int bestDistance = Integer.MAX_VALUE;

         for (int distance = 1; distance <= 4 && preferred == null; distance++) {
            for (Direction direction : Plane.HORIZONTAL) {
               int nx = x + direction.getStepX() * distance;
               int nz = z + direction.getStepZ() * distance;
               AutoFarmModule.Harvested entry = this.recentHarvest.get(BlockPos.asLong(nx, y, nz));
               Block neighbor = entry != null ? entry.crop() : MC.level.getBlockState(new BlockPos(nx, y, nz)).getBlock();
               if (crops.contains(neighbor) && this.cropBaseMatches(neighbor, below, x, y, z)) {
                  bestDistance = distance;
                  preferred = neighbor;
                  break;
               }
            }
         }

         if (preferred != null && bestDistance <= 4) {
            return this.seedChoiceFor(preferred, below, crops, x, y, z);
         } else {
            for (Block crop : crops) {
               AutoFarmModule.SeedChoice choice = this.seedChoiceFor(crop, below, crops, x, y, z);
               if (choice != null) {
                  return choice;
               }
            }

            return null;
         }
      }
   }

   private AutoFarmModule.SeedChoice seedChoiceFor(Block crop, BlockState below, Set<Block> crops, int x, int y, int z) {
      if (crops.contains(crop) && this.cropBaseMatches(crop, below, x, y, z)) {
         if (crop instanceof CropBlock && MC.level.getRawBrightness(new BlockPos(x, y, z), 0) < 8) {
            return null;
         } else {
            Item seed = RiptideFarmBlocks.seedFor(crop);
            return seed == null ? null : this.seedChoiceFrom(crop, seed);
         }
      } else {
         return null;
      }
   }

   private boolean cropBaseMatches(Block crop, BlockState below, int x, int y, int z) {
      if (crop != Blocks.COCOA) {
         return RiptideFarmBlocks.baseMatches(crop, below);
      } else {
         BlockPos cell = new BlockPos(x, y, z);

         for (Direction direction : Plane.HORIZONTAL) {
            if (RiptideFarmBlocks.baseMatches(crop, MC.level.getBlockState(cell.relative(direction)))) {
               return true;
            }
         }

         return false;
      }
   }

   private AutoFarmModule.SeedChoice seedChoiceFrom(Block crop, Item seed) {
      int slot = this.findItemSlot(seed);
      if (slot >= 0) {
         return new AutoFarmModule.SeedChoice(crop, seed, slot);
      } else {
         return this.offhandHolds(seed) ? new AutoFarmModule.SeedChoice(crop, seed, -1) : null;
      }
   }

   private AutoFarmModule.SeedChoice recentChoiceAt(int x, int y, int z, BlockState below, Set<Block> crops, int tick) {
      AutoFarmModule.Harvested entry = this.recentHarvest.get(BlockPos.asLong(x, y, z));
      return entry == null ? null : this.seedChoiceFor(entry.crop(), below, crops, x, y, z);
   }

   private AutoFarmModule.Target buildHarvest(BlockPos cell) {
      int slot = this.bool("fortune") ? this.fortuneSlot() : -1;
      RiptideRotationUtil.Rotation rotation = this.solveHarvest(cell);
      return rotation == null
         ? null
         : new AutoFarmModule.Target(RiptideFarmPlanner.Kind.HARVEST, cell, MC.level.getBlockState(cell).getBlock(), null, slot, rotation);
   }

   private boolean arrivedRayWorks(RiptideRotationUtil.Rotation goal, Vec3 eye) {
      BlockHitResult ray = ScaffoldModule.grimClickRay(eye, goal, this.reach(), MC.level, MC.player);
      if (ray == null) {
         return false;
      } else {
         BlockPos cell = ray.getBlockPos();
         BlockState state = MC.level.getBlockState(cell);
         return this.cropBlocks().contains(state.getBlock()) && this.grownOk(state)
            ? !RiptideFarmBlocks.isColumnCrop(state.getBlock()) || MC.level.getBlockState(cell.below()).is(state.getBlock())
            : false;
      }
   }

   private AutoFarmModule.Target buildReplant(BlockPos cell, boolean recent, int tick) {
      Set<Block> crops = this.cropBlocks();
      BlockState below = MC.level.getBlockState(cell.below());
      AutoFarmModule.SeedChoice choice = recent
         ? this.recentChoiceAt(cell.getX(), cell.getY(), cell.getZ(), below, crops, tick)
         : this.plainChoice(cell.getX(), cell.getY(), cell.getZ(), below, crops);
      if (choice == null) {
         return null;
      } else {
         ItemStack stack = choice.slot() >= 0 ? MC.player.getInventory().getItem(choice.slot()) : MC.player.getOffhandItem();
         RiptideFarmPlanner.Kind kind = recent ? RiptideFarmPlanner.Kind.REPLANT_RECENT : RiptideFarmPlanner.Kind.REPLANT;
         Vec3 eye = MC.player.getEyePosition();
         RiptideRotationUtil.Rotation rotation;
         if (this.aimMemoPos != null && this.aimMemoPos.equals(cell) && this.aimMemoKind == kind && this.aimMemoEye.distanceTo(eye) <= 1.0) {
            rotation = this.aimMemoGoal;
         } else {
            rotation = this.solveUse(cell, stack);
            if (rotation != null) {
               this.aimMemoPos = cell.immutable();
               this.aimMemoKind = kind;
               this.aimMemoGoal = rotation;
               this.aimMemoEye = eye;
            }
         }

         return rotation == null ? null : new AutoFarmModule.Target(kind, cell, choice.crop(), choice.seed(), choice.slot(), rotation);
      }
   }

   private AutoFarmModule.Target buildTill(BlockPos cell, int hoeSlot) {
      Item hoe = hoeSlot >= 0 ? MC.player.getInventory().getItem(hoeSlot).getItem() : (this.offhandHoe() ? MC.player.getOffhandItem().getItem() : null);
      if (hoe == null) {
         return null;
      } else {
         RiptideRotationUtil.Rotation rotation = this.solveTill(cell);
         return rotation == null ? null : new AutoFarmModule.Target(RiptideFarmPlanner.Kind.TILL, cell, null, hoe, hoeSlot, rotation);
      }
   }

   private AutoFarmModule.Target buildBonemeal(BlockPos cell) {
      int slot = this.bonemealSlot();
      if (slot < 0 && !this.offhandHolds(Items.BONE_MEAL)) {
         return null;
      } else {
         RiptideRotationUtil.Rotation rotation = this.solveBonemeal(cell);
         return rotation == null
            ? null
            : new AutoFarmModule.Target(RiptideFarmPlanner.Kind.BONEMEAL, cell, MC.level.getBlockState(cell).getBlock(), Items.BONE_MEAL, slot, rotation);
      }
   }

   private RiptideRotationUtil.Rotation solveBonemeal(BlockPos cell) {
      Vec3 eye = MC.player.getEyePosition();
      VoxelShape shape = MC.level.getBlockState(cell).getShape(MC.level, cell);
      AABB box = shape.isEmpty() ? new AABB(cell) : shape.bounds().move(cell);
      RiptideRotationUtil.Rotation goal = RiptideRotationUtil.lookingAt(box.getCenter(), eye);
      if (!placementPitchLegal(goal.pitch())) {
         return null;
      } else {
         return this.arrivedRayBonemealable(goal, eye) ? goal : null;
      }
   }

   private boolean arrivedRayBonemealable(RiptideRotationUtil.Rotation goal, Vec3 eye) {
      BlockHitResult ray = ScaffoldModule.grimClickRay(eye, goal, this.reach(), MC.level, MC.player);
      return ray != null && this.bonemealOfferAt(ray.getBlockPos());
   }

   private boolean bonemealOfferAt(BlockPos pos) {
      BlockState state = MC.level.getBlockState(pos);
      return this.cropBlocks().contains(state.getBlock()) && this.bonemealable(pos, state);
   }

   private boolean bonemealable(BlockPos pos, BlockState state) {
      Block block = state.getBlock();
      return !RiptideFarmBlocks.isColumnCrop(block) && !this.atMaxAge(state)
         ? block instanceof BonemealableBlock b && b.isValidBonemealTarget(MC.level, pos, state)
         : false;
   }

   private RiptideRotationUtil.Rotation solveHarvest(BlockPos cell) {
      Vec3 eye = MC.player.getEyePosition();
      RiptideRotationUtil.Rotation row = this.solveRowSweep(cell, eye);
      if (row != null) {
         return row;
      } else {
         Set<Block> crops = this.cropBlocks();
         double cullSqr = this.reach() * this.reach();
         List<BlockPos> members = new ArrayList<>();
         MutableBlockPos cursor = new MutableBlockPos();

         for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
               for (int dz = -2; dz <= 2; dz++) {
                  int x = cell.getX() + dx;
                  int y = cell.getY() + dy;
                  int z = cell.getZ() + dz;
                  if (!(cellDistanceSqr(eye, x, y, z) > cullSqr)) {
                     BlockState state = MC.level.getBlockState(cursor.set(x, y, z));
                     if (crops.contains(state.getBlock()) && this.grownOk(state) && this.columnBaseOk(cursor, x, y, z, state.getBlock())) {
                        members.add(new BlockPos(x, y, z));
                     }
                  }
               }
            }
         }

         members.sort(Comparator.comparingInt(member -> {
            int dx = member.getX() - cell.getX();
            int dy = member.getY() - cell.getY();
            int dzx = member.getZ() - cell.getZ();
            return dx * dx + dy * dy + dzx * dzx;
         }));
         int bandCount = members.size();
         if (bandCount <= 3) {
            this.foldNextPatch(cell, eye, crops, cullSqr, members);
         }

         int count = Math.min(members.size(), 8);
         double sumX = 0.0;
         double sumY = 0.0;
         double sumZ = 0.0;

         for (int i = 0; i < count; i++) {
            BlockPos member = members.get(i);
            sumX += member.getX() + 0.5;
            sumY += member.getY() + 0.5;
            sumZ += member.getZ() + 0.5;
         }

         Vec3 centroid = new Vec3(sumX / count, sumY / count, sumZ / count);
         if (eye.distanceToSqr(centroid) <= cullSqr) {
            RiptideRotationUtil.Rotation goal = RiptideRotationUtil.lookingAt(centroid, eye);
            if (this.arrivedRayWorks(goal, eye)) {
               return goal;
            }
         }

         if (count > bandCount) {
            count = bandCount;
            sumX = 0.0;
            sumY = 0.0;
            sumZ = 0.0;

            for (int i = 0; i < count; i++) {
               BlockPos member = members.get(i);
               sumX += member.getX() + 0.5;
               sumY += member.getY() + 0.5;
               sumZ += member.getZ() + 0.5;
            }

            centroid = new Vec3(sumX / count, sumY / count, sumZ / count);
            if (eye.distanceToSqr(centroid) <= cullSqr) {
               RiptideRotationUtil.Rotation goal = RiptideRotationUtil.lookingAt(centroid, eye);
               if (this.arrivedRayWorks(goal, eye)) {
                  return goal;
               }
            }
         }

         VoxelShape shape = MC.level.getBlockState(cell).getShape(MC.level, cell);
         AABB box = shape.isEmpty() ? new AABB(cell) : shape.bounds().move(cell);
         RiptideRotationUtil.Rotation boxGoal = RiptideRotationUtil.lookingAt(box.getCenter(), eye);
         return !this.arrivedRayWorks(boxGoal, eye) ? null : boxGoal;
      }
   }

   private void foldNextPatch(BlockPos cell, Vec3 eye, Set<Block> crops, double cullSqr, List<BlockPos> members) {
      RiptideRotationUtil.Rotation sweep = this.sweepRotation();
      int[] offsets = sphere(Mth.ceil(this.reach()) + 3);
      BlockPos origin = MC.player.blockPosition();
      int ox = origin.getX();
      int oy = origin.getY();
      int oz = origin.getZ();
      MutableBlockPos cursor = new MutableBlockPos();
      List<BlockPos> beyond = new ArrayList<>();

      for (int i = 0; i < offsets.length; i += 3) {
         int x = ox + offsets[i];
         int y = oy + offsets[i + 1];
         int z = oz + offsets[i + 2];
         if ((Math.abs(x - cell.getX()) > 2 || Math.abs(y - cell.getY()) > 2 || Math.abs(z - cell.getZ()) > 2) && !(cellDistanceSqr(eye, x, y, z) > cullSqr)) {
            BlockState state = MC.level.getBlockState(cursor.set(x, y, z));
            if (crops.contains(state.getBlock()) && this.grownOk(state) && this.columnBaseOk(cursor, x, y, z, state.getBlock())) {
               beyond.add(new BlockPos(x, y, z));
            }
         }
      }

      beyond.sort(Comparator.comparingDouble(pos -> {
         double dx = pos.getX() + 0.5 - eye.x;
         double dy = pos.getY() + 0.5 - eye.y;
         double dz = pos.getZ() + 0.5 - eye.z;
         float toYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
         float toPitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
         return Math.abs(RiptideRotationUtil.angleDifference(sweep.yaw(), toYaw)) + Math.abs(toPitch - sweep.pitch());
      }));

      for (int ix = 0; ix < Math.min(4, beyond.size()); ix++) {
         members.add(beyond.get(ix));
      }
   }

   private RiptideRotationUtil.Rotation solveRowSweep(BlockPos pick, Vec3 eye) {
      Set<Block> crops = this.cropBlocks();
      int px = pick.getX();
      int py = pick.getY();
      int pz = pick.getZ();
      MutableBlockPos cursor = new MutableBlockPos();
      int countX = 0;
      int countZ = 0;

      for (int d = -8; d <= 8; d++) {
         for (int dy = -1; dy <= 1; dy++) {
            if (crops.contains(MC.level.getBlockState(cursor.set(px + d, py + dy, pz)).getBlock())) {
               countX++;
            }

            if (crops.contains(MC.level.getBlockState(cursor.set(px, py + dy, pz + d)).getBlock())) {
               countZ++;
            }
         }
      }

      if (Math.max(countX, countZ) < 3) {
         return null;
      } else {
         Axis axis;
         if (countX == countZ) {
            axis = this.sweepAxis == Axis.Z ? Axis.Z : Axis.X;
         } else {
            axis = countX > countZ ? Axis.X : Axis.Z;
         }

         int along = axis == Axis.X ? px : pz;
         int perp = axis == Axis.X ? pz : px;
         int sign;
         if (this.sweepAxis == axis) {
            sign = this.sweepSign;
         } else {
            sign = this.wireSignAlong(axis);
            if (sign == 0) {
               sign = 1;
            }
         }

         int end = this.rowEnd(cursor, along, perp, py, axis, sign, crops, this.rowYOut);
         if (end == Integer.MIN_VALUE && this.sweepAxis != axis) {
            sign = -sign;
            end = this.rowEnd(cursor, along, perp, py, axis, sign, crops, this.rowYOut);
         }

         if (end == Integer.MIN_VALUE) {
            return this.solveRowPivot(cursor, along, perp, py, axis, sign, crops, eye);
         } else {
            if ((end - along) * sign <= 3) {
               RiptideRotationUtil.Rotation pivot = this.solveRowPivot(cursor, along, perp, py, axis, sign, crops, eye);
               if (pivot != null) {
                  return pivot;
               }

               this.rowEnd(cursor, along, perp, py, axis, sign, crops, this.rowYOut);
            }

            this.sweepAxis = axis;
            this.sweepSign = sign;
            RiptideRotationUtil.Rotation goal = this.rowGoal(along, perp, this.rowYOut[0], axis, sign, end, eye);
            if (goal != null && !this.arrivedRayWorks(goal, eye)) {
               goal = null;
            }

            if (goal != null) {
               this.stampRowContinuation(end, perp, axis);
            }

            return goal;
         }
      }
   }

   private RiptideRotationUtil.Rotation solveRowPivot(MutableBlockPos cursor, int along, int perp, int py, Axis axis, int sign, Set<Block> crops, Vec3 eye) {
      for (int offset = 1; offset <= 3; offset++) {
         for (int side = -1; side <= 1; side += 2) {
            int rowPerp = perp + side * offset;
            int anchor = Integer.MIN_VALUE;
            int anchorDist = Integer.MAX_VALUE;

            for (int da = -2; da <= 2; da++) {
               if (this.rowCropY(cursor, along + da, rowPerp, py, axis, crops) != Integer.MIN_VALUE && Math.abs(da) < anchorDist) {
                  anchorDist = Math.abs(da);
                  anchor = along + da;
               }
            }

            if (anchor != Integer.MIN_VALUE) {
               int newSign = -sign;
               int end = this.rowEnd(cursor, anchor, rowPerp, py, axis, newSign, crops, this.rowYOut);
               if (end != Integer.MIN_VALUE) {
                  RiptideRotationUtil.Rotation goal = this.rowGoal(anchor, rowPerp, this.rowYOut[0], axis, newSign, end, eye);
                  if (goal != null && !this.arrivedRayWorks(goal, eye)) {
                     goal = null;
                  }

                  if (goal == null) {
                     return null;
                  }

                  this.sweepAxis = axis;
                  this.sweepSign = newSign;
                  this.stampRowContinuation(end, rowPerp, axis);
                  return goal;
               }
            }
         }
      }

      return null;
   }

   private void stampRowContinuation(int end, int perp, Axis axis) {
      this.rowContinuation = axis == Axis.X ? new BlockPos(end, this.rowLastCropY, perp) : new BlockPos(perp, this.rowLastCropY, end);
      this.rowContinuationTick = RiptideSharedState.get().getClientTickCounter();
   }

   private int rowEnd(MutableBlockPos cursor, int along, int perp, int py, Axis axis, int sign, Set<Block> crops, int[] nextY) {
      int end = Integer.MIN_VALUE;
      int gap = 0;
      boolean gapAllWater = true;

      for (int step = 1; step <= 8; step++) {
         int a = along + sign * step;
         int cropY = this.rowCropY(cursor, a, perp, py, axis, crops);
         if (cropY != Integer.MIN_VALUE) {
            if (end == Integer.MIN_VALUE) {
               nextY[0] = cropY;
            }

            end = a;
            this.rowLastCropY = cropY;
            gap = 0;
            gapAllWater = true;
         } else {
            gapAllWater = gapAllWater && this.rowWaterAt(cursor, a, perp, py, axis);
            if (++gap > (gapAllWater ? 2 : 1)) {
               break;
            }
         }
      }

      return end;
   }

   private int rowCropY(MutableBlockPos cursor, int a, int perp, int py, Axis axis, Set<Block> crops) {
      for (int dy = -1; dy <= 1; dy++) {
         if (crops.contains(MC.level.getBlockState(rowSet(cursor, a, perp, py + dy, axis)).getBlock())) {
            return py + dy;
         }
      }

      return Integer.MIN_VALUE;
   }

   private boolean rowWaterAt(MutableBlockPos cursor, int a, int perp, int py, Axis axis) {
      for (int dy = -1; dy <= 1; dy++) {
         Block block = MC.level.getBlockState(rowSet(cursor, a, perp, py + dy, axis)).getBlock();
         if (block == Blocks.WATER || block == Blocks.LILY_PAD) {
            return true;
         }
      }

      return false;
   }

   private static MutableBlockPos rowSet(MutableBlockPos cursor, int a, int perp, int y, Axis axis) {
      return axis == Axis.X ? cursor.set(a, y, perp) : cursor.set(perp, y, a);
   }

   private RiptideRotationUtil.Rotation rowGoal(int along, int perp, int y, Axis axis, int sign, int end, Vec3 eye) {
      double goal = along + sign * 1.75;
      goal = sign > 0 ? Math.min(goal, end + 1.0) : Math.max(goal, end - 1.0);
      Vec3 point = axis == Axis.X ? new Vec3(goal + 0.5, y + 0.5, perp + 0.5) : new Vec3(perp + 0.5, y + 0.5, goal + 0.5);
      return eye.distanceToSqr(point) > this.reach() * this.reach() ? null : RiptideRotationUtil.lookingAt(point, eye);
   }

   private int wireSignAlong(Axis axis) {
      float yaw = this.sweepRotation().yaw();
      double component = axis == Axis.X ? -Math.sin(Math.toRadians(yaw)) : Math.cos(Math.toRadians(yaw));
      return component > 0.05 ? 1 : (component < -0.05 ? -1 : 0);
   }

   private RiptideRotationUtil.Rotation solveUse(BlockPos cell, ItemStack seedStack) {
      RiptideFaceScan.Candidate candidate = RiptideFaceScan.best(
         new RiptideFaceScan.Request(cell, MC.player.getEyePosition(), this.reach(), RiptideFaceScan.blockItem(seedStack, MC.player, InteractionHand.MAIN_HAND))
            .from(this.sweepRotation())
            .pitchLimit(RiptideFaceScan.goalPitchLimit())
            .sneaking(MC.player.isSecondaryUseActive())
            .sneakAllowed(false)
            .budget(new RiptideFaceScan.Budget(48))
      );
      return candidate == null ? null : candidate.aim().goal();
   }

   private RiptideRotationUtil.Rotation solveTill(BlockPos dirt) {
      RiptideRotationUtil.Rotation rotation = this.solveTillAt(dirt, dirt.above());
      if (rotation != null) {
         return rotation;
      } else {
         Direction[] sides = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
         this.sortTillSides(sides, dirt);

         for (Direction side : sides) {
            rotation = this.solveTillAt(dirt, dirt.relative(side));
            if (rotation != null) {
               return rotation;
            }
         }

         return null;
      }
   }

   private RiptideRotationUtil.Rotation solveTillAt(BlockPos dirt, BlockPos cell) {
      RiptideFaceScan.Candidate candidate = RiptideFaceScan.best(
         new RiptideFaceScan.Request(
               cell, MC.player.getEyePosition(), this.reach(), (hit, target) -> hit.getBlockPos().equals(dirt) && hit.getDirection() != Direction.DOWN
            )
            .from(this.sweepRotation())
            .pitchLimit(RiptideFaceScan.goalPitchLimit())
            .sneaking(MC.player.isSecondaryUseActive())
            .sneakAllowed(false)
            .budget(new RiptideFaceScan.Budget(48))
      );
      return candidate == null ? null : candidate.aim().goal();
   }

   private void sortTillSides(Direction[] sides, BlockPos dirt) {
      Vec3 eye = MC.player.getEyePosition();
      float wire = this.sweepRotation().yaw();
      float[] keys = new float[sides.length];

      for (int i = 0; i < sides.length; i++) {
         keys[i] = Math.abs(RiptideRotationUtil.angleDifference(wire, RiptideRotationUtil.lookingAt(Vec3.atCenterOf(dirt.relative(sides[i])), eye).yaw()));
      }

      for (int i = 1; i < sides.length; i++) {
         Direction side = sides[i];
         float key = keys[i];

         int j;
         for (j = i - 1; j >= 0 && keys[j] > key; j--) {
            sides[j + 1] = sides[j];
            keys[j + 1] = keys[j];
         }

         sides[j + 1] = side;
         keys[j + 1] = key;
      }
   }

   private boolean tillOfferAt(BlockPos pos) {
      Block block = MC.level.getBlockState(pos).getBlock();
      return !TILLABLE.contains(block) ? false : block == Blocks.ROOTED_DIRT || MC.level.getBlockState(pos.above()).isAir();
   }

   private RiptideRotationUtil.Rotation tryAct(AutoFarmModule.Target target) {
      int tick = RiptideSharedState.get().getClientTickCounter();
      if (tick == this.lastActionTick) {
         return null;
      } else if (BedDefenderModule.ownsSilentRotation()) {
         return null;
      } else if (SurroundModule.ownsSilentRotation()) {
         return null;
      } else if (CrystalAuraModule.reservesCombatTick()) {
         return null;
      } else if (AnchorAuraModule.reservesCombatTick()) {
         return null;
      } else if (this.foreignStreamOwner()) {
         return null;
      } else {
         int switchAge = tick - this.hotbarChangeTick;
         if (switchAge >= 0 && switchAge <= this.switchSettleTicks) {
            return null;
         } else if (tick < this.noHoldUntilTick) {
            return null;
         } else if (RiptideBlinkManager.holdsActionsWithoutMovement()) {
            return null;
         } else {
            RiptideServerRotationView.WireSnapshot wire = RiptideServerRotationView.snapshot();
            if (!wire.initialized()) {
               return null;
            } else {
               RiptideRotationUtil.Rotation wireRotation = new RiptideRotationUtil.Rotation(wire.currentYaw(), wire.currentPitch());
               if (target.kind() != RiptideFarmPlanner.Kind.HARVEST && !placementPitchLegal(wireRotation.pitch())) {
                  return null;
               } else {
                  RiptideRotationUtil.Rotation silent = RiptideSilentAim.activeOutgoingRotation(MC.player);
                  if (silent != null && !sameRotation(silent, wireRotation)) {
                     return null;
                  } else {
                     BlockHitResult ray = ScaffoldModule.grimClickRay(MC.player.getEyePosition(), wireRotation, this.reach(), MC.level, MC.player);
                     if (ray == null) {
                        return null;
                     } else {
                        return switch (target.kind()) {
                           case HARVEST -> this.tryHarvest(target, ray, wireRotation, tick);
                           case BONEMEAL -> this.tryBonemeal(target, ray, wireRotation, tick);
                           case TILL -> this.tryTill(target, ray, wireRotation, tick);
                           case REPLANT_RECENT, REPLANT -> this.tryReplant(target, ray, wireRotation, tick);
                        };
                     }
                  }
               }
            }
         }
      }
   }

   private RiptideRotationUtil.Rotation tryHarvest(AutoFarmModule.Target target, BlockHitResult ray, RiptideRotationUtil.Rotation wireRotation, int tick) {
      BlockPos cell = ray.getBlockPos();
      BlockState state = MC.level.getBlockState(cell);
      if (this.cropBlocks().contains(state.getBlock()) && this.grownOk(state)) {
         if (RiptideFarmBlocks.isColumnCrop(state.getBlock()) && !MC.level.getBlockState(cell.below()).is(state.getBlock())) {
            return null;
         } else if ((this.bool("replant") || this.bool("plant"))
            && RiptideFarmBlocks.seedFor(state.getBlock()) != null
            && !this.recentHarvest.hasRoomFor(cell.asLong())) {
            return null;
         } else {
            if (target.hotbarSlot() >= 0) {
               if (!this.ensureHand(target.hotbarSlot())) {
                  return null;
               }

               if (this.fortuneLevel(MC.player.getMainHandItem()) <= 0) {
                  return null;
               }
            }

            if (!this.actionProgressAllowed(target, cell, state, tick)) {
               return null;
            } else {
               this.releaseOppositeHold(true);
               if (!RiptideCombatClicker.holdAttack(ray)) {
                  return null;
               } else {
                  this.book(tick);
                  this.holdTick = tick;
                  this.holdKeyAttack = true;
                  this.rememberHarvest(cell, state.getBlock(), tick);
                  return wireRotation;
               }
            }
         }
      } else {
         return null;
      }
   }

   private RiptideRotationUtil.Rotation tryReplant(AutoFarmModule.Target target, BlockHitResult ray, RiptideRotationUtil.Rotation wireRotation, int tick) {
      boolean offhand = this.offhandUsable(target.seed(), ray);
      InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
      if (!offhand && target.hotbarSlot() < 0) {
         if (MC.player.getOffhandItem().is(target.seed()) && !RiptideHandArbiter.offhandClaimedByOther(this.id())) {
            this.clearPreemptingHand(ray);
         }

         return null;
      } else {
         ItemStack planStack = offhand
            ? MC.player.getOffhandItem()
            : (target.hotbarSlot() >= 0 ? MC.player.getInventory().getItem(target.hotbarSlot()) : ItemStack.EMPTY);
         if (!planStack.is(target.seed())) {
            return null;
         } else {
            BlockPlaceContext context = new BlockPlaceContext(MC.player, hand, planStack, ray);
            if (!context.canPlace()) {
               return null;
            } else {
               BlockPos cell = context.getClickedPos();
               if (!MC.level.getBlockState(cell).canBeReplaced()) {
                  return null;
               } else {
                  BlockState below = MC.level.getBlockState(cell.below());
                  AutoFarmModule.SeedChoice actualChoice = this.bool("replant")
                     ? this.recentChoiceAt(cell.getX(), cell.getY(), cell.getZ(), below, this.cropBlocks(), tick)
                     : null;
                  if (actualChoice == null && this.bool("plant")) {
                     actualChoice = this.plainChoice(cell.getX(), cell.getY(), cell.getZ(), below, this.cropBlocks());
                  }

                  if (actualChoice != null && actualChoice.crop() == target.crop() && actualChoice.seed() == target.seed()) {
                     BlockState placed = target.crop().getStateForPlacement(context);
                     if (placed != null
                        && placed.canSurvive(MC.level, cell)
                        && MC.level.isUnobstructed(placed, cell, CollisionContext.placementContext(MC.player))) {
                        BlockPos clicked = ray.getBlockPos();
                        if (!RiptideFaceScan.isPlaceableSupport(MC.level.getBlockState(clicked), clicked, MC.player.isSecondaryUseActive())) {
                           return null;
                        } else {
                           if (!offhand) {
                              if (!this.ensureHand(target.hotbarSlot())) {
                                 return null;
                              }

                              if (!MC.player.getMainHandItem().is(target.seed())) {
                                 return null;
                              }
                           }

                           if (ModuleRegistry.shouldCancelUseExcept(ray, hand, this.id())) {
                              return null;
                           } else if (!this.usePressWindowOpen()) {
                              return null;
                           } else {
                              this.releaseOppositeHold(false);
                              if (offhand && !this.offhandUsable(target.seed(), ray)) {
                                 return null;
                              } else if (!this.actionProgressAllowed(target, cell, MC.level.getBlockState(cell), tick)) {
                                 return null;
                              } else if (!RiptideCombatClicker.holdUse(ray, hand)) {
                                 return null;
                              } else {
                                 this.book(tick);
                                 this.holdTick = tick;
                                 this.holdKeyAttack = false;
                                 return wireRotation;
                              }
                           }
                        }
                     } else {
                        return null;
                     }
                  } else {
                     return null;
                  }
               }
            }
         }
      }
   }

   private boolean mainHandWouldPreempt(BlockHitResult ray) {
      return this.wouldPreempt(MC.player.getMainHandItem(), ray);
   }

   private boolean wouldPreempt(ItemStack main, BlockHitResult ray) {
      BlockState clickedState = MC.level.getBlockState(ray.getBlockPos());
      if (!MC.player.isSecondaryUseActive()
         && clickedState.is(Blocks.SWEET_BERRY_BUSH)
         && (Integer)clickedState.getValue(SweetBerryBushBlock.AGE) > 1
         && !main.is(Items.BONE_MEAL)) {
         return true;
      } else if (main.isEmpty()) {
         return false;
      } else if (MC.player.getCooldowns().isOnCooldown(main)) {
         return false;
      } else {
         Item item = main.getItem();
         if (item instanceof BlockItem) {
            return true;
         } else if (main.getUseAnimation() != ItemUseAnimation.NONE) {
            return true;
         } else if (main.has(DataComponents.CONSUMABLE)
            || main.has(DataComponents.EQUIPPABLE)
            || main.has(DataComponents.BLOCKS_ATTACKS)
            || main.has(DataComponents.KINETIC_WEAPON)) {
            return true;
         } else if (main.is(Items.EXPERIENCE_BOTTLE)
            || main.is(Items.ENDER_PEARL)
            || main.is(Items.SNOWBALL)
            || main.is(Items.EGG)
            || main.is(Items.FIREWORK_ROCKET)
            || main.is(Items.FISHING_ROD)
            || item instanceof BucketItem
            || main.is(Items.WIND_CHARGE)
            || main.is(Items.FIRE_CHARGE)
            || main.is(Items.ENDER_EYE)
            || main.is(Items.SPLASH_POTION)
            || main.is(Items.LINGERING_POTION)
            || main.is(Items.FLINT_AND_STEEL)
            || main.is(Items.BONE_MEAL)
            || main.is(Items.GLASS_BOTTLE)
            || main.is(Items.CARROT_ON_A_STICK)) {
            return true;
         } else if (item instanceof SpawnEggItem
            || item instanceof BoatItem
            || item instanceof MinecartItem
            || item instanceof HangingEntityItem
            || item instanceof ArmorStandItem
            || item instanceof EndCrystalItem
            || item instanceof PotionItem
            || item instanceof MapItem
            || item instanceof EmptyMapItem
            || item instanceof CompassItem
            || item instanceof HoneycombItem
            || item instanceof LeadItem
            || item instanceof BundleItem
            || item instanceof KnowledgeBookItem
            || item instanceof WritableBookItem
            || item instanceof WrittenBookItem
            || item instanceof DebugStickItem
            || item instanceof FoodOnAStickItem) {
            return true;
         } else if (item instanceof HoeItem) {
            return this.hoeWouldFire(ray);
         } else if (item instanceof AxeItem) {
            return this.axeWouldFire(ray.getBlockPos());
         } else if (item instanceof ShovelItem) {
            return this.shovelWouldFire(ray);
         } else {
            return item instanceof ShearsItem ? this.shearsWouldFire(ray.getBlockPos()) : item.getClass() != Item.class && !(item instanceof MaceItem);
         }
      }
   }

   private boolean hoeWouldFire(BlockHitResult ray) {
      return !this.tillOfferAt(ray.getBlockPos())
         ? false
         : MC.level.getBlockState(ray.getBlockPos()).is(Blocks.ROOTED_DIRT) || ray.getDirection() != Direction.DOWN;
   }

   private boolean axeWouldFire(BlockPos pos) {
      BlockState state = MC.level.getBlockState(pos);
      return AXE_STRIPPABLE.contains(state.getBlock())
         || WeatheringCopper.getPrevious(state).isPresent()
         || ((BiMap)HoneycombItem.WAX_OFF_BY_BLOCK.get()).containsKey(state.getBlock());
   }

   private boolean shovelWouldFire(BlockHitResult ray) {
      if (ray.getDirection() == Direction.DOWN) {
         return false;
      } else {
         BlockPos pos = ray.getBlockPos();
         BlockState state = MC.level.getBlockState(pos);
         return state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)
            ? true
            : SHOVEL_FLATTENABLE.contains(state.getBlock()) && MC.level.getBlockState(pos.above()).isAir();
      }
   }

   private boolean shearsWouldFire(BlockPos pos) {
      BlockState state = MC.level.getBlockState(pos);
      Block block = state.getBlock();
      if (block instanceof PumpkinBlock) {
         return true;
      } else {
         return block instanceof BeehiveBlock && state.getValue(BeehiveBlock.HONEY_LEVEL) >= 5
            ? true
            : block instanceof GrowingPlantHeadBlock plant && !plant.isMaxAge(state);
      }
   }

   private boolean clearPreemptingHand(BlockHitResult ray) {
      int selected = MC.player.getInventory().getSelectedSlot();
      int fortune = this.bool("fortune") ? this.fortuneSlot() : -1;
      if (fortune >= 0 && fortune != selected && !this.wouldPreempt(MC.player.getInventory().getItem(fortune), ray)) {
         if (!this.changeHotbarSlot(fortune, selected)) {
            return false;
         } else {
            this.farmSlot = fortune;
            return true;
         }
      } else {
         for (int slot = 0; slot < 9; slot++) {
            if (slot != selected && !RiptideHandArbiter.slotReserved(slot, this.id()) && !this.wouldPreempt(MC.player.getInventory().getItem(slot), ray)) {
               if (!this.changeHotbarSlot(slot, selected)) {
                  return false;
               }

               this.farmSlot = slot;
               return true;
            }
         }

         return false;
      }
   }

   private RiptideRotationUtil.Rotation tryTill(AutoFarmModule.Target target, BlockHitResult ray, RiptideRotationUtil.Rotation wireRotation, int tick) {
      if (!this.hoeWouldFire(ray)) {
         return null;
      } else {
         ItemStack offStack = MC.player.getOffhandItem();
         boolean offhand = isHoe(offStack) && this.offhandUsable(offStack.getItem(), ray);
         InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
         if (!offhand) {
            if (target.hotbarSlot() < 0) {
               if (this.offhandHoe()) {
                  this.clearPreemptingHand(ray);
               }

               return null;
            }

            if (!this.ensureHand(target.hotbarSlot())) {
               return null;
            }

            if (!isHoe(MC.player.getMainHandItem())) {
               return null;
            }
         }

         if (MC.player.getCooldowns().isOnCooldown(MC.player.getItemInHand(hand))) {
            return null;
         } else if (ModuleRegistry.shouldCancelUseExcept(ray, hand, this.id())) {
            return null;
         } else if (!this.usePressWindowOpen()) {
            return null;
         } else {
            this.releaseOppositeHold(false);
            if (offhand && !this.offhandUsable(offStack.getItem(), ray)) {
               return null;
            } else if (!this.actionProgressAllowed(target, ray.getBlockPos(), MC.level.getBlockState(ray.getBlockPos()), tick)) {
               return null;
            } else if (!RiptideCombatClicker.holdUse(ray, hand)) {
               return null;
            } else {
               this.book(tick);
               this.holdTick = tick;
               this.holdKeyAttack = false;
               return wireRotation;
            }
         }
      }
   }

   private RiptideRotationUtil.Rotation tryBonemeal(AutoFarmModule.Target target, BlockHitResult ray, RiptideRotationUtil.Rotation wireRotation, int tick) {
      if (!this.bonemealOfferAt(ray.getBlockPos())) {
         return null;
      } else {
         boolean offhand = this.offhandUsable(Items.BONE_MEAL, ray);
         InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
         if (!offhand) {
            if (target.hotbarSlot() < 0) {
               if (MC.player.getOffhandItem().is(Items.BONE_MEAL) && !RiptideHandArbiter.offhandClaimedByOther(this.id())) {
                  this.clearPreemptingHand(ray);
               }

               return null;
            }

            if (!this.ensureHand(target.hotbarSlot())) {
               return null;
            }

            if (!MC.player.getMainHandItem().is(Items.BONE_MEAL)) {
               return null;
            }
         }

         if (ModuleRegistry.shouldCancelUseExcept(ray, hand, this.id())) {
            return null;
         } else if (!this.usePressWindowOpen()) {
            return null;
         } else {
            this.releaseOppositeHold(false);
            if (offhand && !this.offhandUsable(Items.BONE_MEAL, ray)) {
               return null;
            } else if (MC.player.getCooldowns().isOnCooldown(MC.player.getItemInHand(hand))) {
               return null;
            } else if (!this.actionProgressAllowed(target, ray.getBlockPos(), MC.level.getBlockState(ray.getBlockPos()), tick)) {
               return null;
            } else if (!RiptideCombatClicker.holdUse(ray, hand)) {
               return null;
            } else {
               this.book(tick);
               this.holdTick = tick;
               this.holdKeyAttack = false;
               return wireRotation;
            }
         }
      }
   }

   private boolean usePressWindowOpen() {
      boolean useKeyDown = RiptideCombatClicker.holding() && !this.holdKeyAttack;
      return useKeyDown || ((RiptideMinecraftAccessor)MC).riptide$getRightClickDelay() <= 0;
   }

   private void book(int tick) {
      this.lastActionTick = tick;
   }

   private void releaseOppositeHold(boolean attack) {
      if (RiptideCombatClicker.holding() && this.holdKeyAttack != attack) {
         RiptideCombatClicker.releaseHold();
      }
   }

   private void releaseHoldNow() {
      RiptideCombatClicker.releaseHold();
      this.holdTick = Integer.MIN_VALUE;
   }

   private void releaseHoldUnlessAffirmed() {
      if (this.holdTick != RiptideSharedState.get().getClientTickCounter()) {
         this.releaseHoldNow();
      }
   }

   private boolean harvestMemoryAvailable(int x, int y, int z, Block crop) {
      return !this.bool("replant") && !this.bool("plant") || RiptideFarmBlocks.seedFor(crop) == null || this.recentHarvest.hasRoomFor(BlockPos.asLong(x, y, z));
   }

   private void rememberHarvest(BlockPos pos, Block crop, int tick) {
      this.uncoolAround(pos);
      if ((this.bool("replant") || this.bool("plant")) && RiptideFarmBlocks.seedFor(crop) != null) {
         this.recentHarvest.remember(pos.asLong(), new AutoFarmModule.Harvested(pos.immutable(), crop, tick));
      }
   }

   private void pruneHarvestMemory() {
      int tick = RiptideSharedState.get().getClientTickCounter();
      this.recentHarvest
         .values()
         .removeIf(
            entry -> {
               BlockPos pos = entry.pos();
               if (MC.level.hasChunkAt(pos) && tick != entry.tick()) {
                  BlockState state = MC.level.getBlockState(pos);
                  if (state.is(entry.crop())) {
                     return !this.atMaxAge(state);
                  } else {
                     return !state.canBeReplaced()
                        ? true
                        : !this.cropBaseMatches(entry.crop(), MC.level.getBlockState(pos.below()), pos.getX(), pos.getY(), pos.getZ());
                  }
               } else {
                  return false;
               }
            }
         );
   }

   private boolean actionProgressAllowed(AutoFarmModule.Target target, BlockPos cell, BlockState state, int tick) {
      int limit = 40;
      if (target.kind() == RiptideFarmPlanner.Kind.HARVEST) {
         float progress = state.getDestroyProgress(MC.player, MC.level, cell);
         if (progress > 0.0F && Float.isFinite(progress)) {
            limit += (int)Math.min(1200.0, Math.ceil(1.0 / progress));
         }
      }

      if (this.actionWatchdog.allow(cell.asLong(), new AutoFarmModule.AttemptState(target.kind(), state), tick, limit)) {
         return true;
      } else {
         this.coolCell(cell, tick);
         this.coolCell(target.pos(), tick);
         this.releaseHoldNow();
         return false;
      }
   }

   private void uncoolAround(BlockPos pos) {
      if (!this.cellCooldownStamp.isEmpty()) {
         int px = pos.getX();
         int py = pos.getY();
         int pz = pos.getZ();
         this.cellCooldownStamp.keySet().removeIf(key -> {
            if (Math.abs(BlockPos.getX(key) - px) <= 2 && Math.abs(BlockPos.getY(key) - py) <= 2 && Math.abs(BlockPos.getZ(key) - pz) <= 2) {
               this.cellCooldownBase.remove(key);
               return true;
            } else {
               return false;
            }
         });
      }
   }

   @Override
   public boolean shouldCancelUse(HitResult hitResult, InteractionHand hand) {
      return this.holdOwnsKey();
   }

   @Override
   public boolean shouldCancelAttack(HitResult hitResult) {
      return this.holdOwnsKey()
         ? true
         : hitResult instanceof EntityHitResult && this.id().equals(RiptideKillAuraRotation.currentOwner()) && RiptideKillAuraRotation.hasCurrentRotation();
   }

   private boolean holdOwnsKey() {
      if (this.holdTick != Integer.MIN_VALUE && RiptideCombatClicker.holding()) {
         int tick = RiptideSharedState.get().getClientTickCounter();
         int age = tick - this.holdTick;
         return age >= 0 && age <= 1;
      } else {
         return false;
      }
   }

   private static boolean placementPitchLegal(float pitch) {
      return ScaffoldModule.grimPlacementPitchLegal(Math.abs(pitch));
   }

   private static boolean sameRotation(RiptideRotationUtil.Rotation first, RiptideRotationUtil.Rotation second) {
      float epsilon = (float)Math.max(0.05F, RiptideRotationUtil.sensitivityGcd() + 0.05F);
      return Math.abs(RiptideRotationUtil.angleDifference(first.yaw(), second.yaw())) <= epsilon && Math.abs(first.pitch() - second.pitch()) <= epsilon;
   }

   private boolean ensureHand(int slot) {
      int selected = MC.player.getInventory().getSelectedSlot();
      if (selected == slot) {
         return true;
      } else if (!this.changeHotbarSlot(slot, selected)) {
         return false;
      } else {
         this.farmSlot = slot;
         return false;
      }
   }

   private boolean changeHotbarSlot(int slot, int selected) {
      if (BedDefenderModule.ownsSilentRotation()
         || SurroundModule.ownsSilentRotation()
         || CrystalAuraModule.reservesCombatTick()
         || AnchorAuraModule.reservesCombatTick()) {
         return false;
      } else if (this.foreignStreamOwner()) {
         return false;
      } else {
         int tick = RiptideSharedState.get().getClientTickCounter();
         if (tick == this.hotbarChangeTick) {
            return false;
         } else if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
            return false;
         } else {
            label71: {
               boolean key;
               try {
                  if (this.previousSlot < 0) {
                     this.previousSlot = selected;
                  }

                  if (slot >= 0 && slot < MC.options.keyHotbarSlots.length && !RiptideHandArbiter.slotReserved(slot, this.id())) {
                     RiptideKeyMappingBridge keyx = RiptideKeyMappingBridge.of(MC.options.keyHotbarSlots[slot]);
                     keyx.riptide$simulatePress(true);
                     keyx.riptide$simulatePress(false);
                     keyx.riptide$resetPressedState();
                     break label71;
                  }

                  key = false;
               } finally {
                  RiptideHandArbiter.endHandPacketGroup(this.id());
               }

               return key;
            }

            this.hotbarChangeTick = tick;
            this.switchSettleTicks = this.random.nextInt(3);
            return true;
         }
      }
   }

   private int findItemSlot(Item item) {
      this.refreshHotbar();
      return this.cachedItemSlots.getOrDefault(item, -1);
   }

   private void refreshHotbar() {
      int tick = RiptideSharedState.get().getClientTickCounter();
      if (tick != this.hotbarTick) {
         this.hotbarTick = tick;
         this.cachedHoeSlot = -1;
         this.cachedFortuneSlot = -1;
         this.cachedItemSlots.clear();
         int selected = MC.player.getInventory().getSelectedSlot();
         int bestFortuneDistance = Integer.MAX_VALUE;
         boolean bestFortuneHoe = false;

         for (int slot = 0; slot < 9; slot++) {
            if (!RiptideHandArbiter.slotReserved(slot, this.id())) {
               ItemStack stack = MC.player.getInventory().getItem(slot);
               if (!stack.isEmpty()) {
                  int distance = Math.abs(slot - selected);
                  this.cachedItemSlots
                     .merge(
                        stack.getItem(), slot, (oldSlot, newSlot) -> (Integer)(Math.abs(newSlot - selected) < Math.abs(oldSlot - selected) ? newSlot : oldSlot)
                     );
                  boolean hoe = isHoe(stack);
                  if (hoe && (this.cachedHoeSlot < 0 || distance < Math.abs(this.cachedHoeSlot - selected))) {
                     this.cachedHoeSlot = slot;
                  }

                  if (this.fortuneLevel(stack) > 0
                     && (this.cachedFortuneSlot < 0 || hoe && !bestFortuneHoe || hoe == bestFortuneHoe && distance < bestFortuneDistance)) {
                     this.cachedFortuneSlot = slot;
                     bestFortuneHoe = hoe;
                     bestFortuneDistance = distance;
                  }
               }
            }
         }

         ItemStack offhand = MC.player.getOffhandItem();
         this.cachedOffhandItem = !offhand.isEmpty() && !RiptideHandArbiter.offhandClaimedByOther(this.id()) ? offhand.getItem() : null;
      }
   }

   private boolean offhandHolds(Item item) {
      this.refreshHotbar();
      return this.cachedOffhandItem == item;
   }

   private boolean offhandUsable(Item item, BlockHitResult ray) {
      return MC.player.getOffhandItem().is(item) && !RiptideHandArbiter.offhandClaimedByOther(this.id()) && !this.mainHandWouldPreempt(ray);
   }

   private boolean offhandHoe() {
      return !RiptideHandArbiter.offhandClaimedByOther(this.id()) && isHoe(MC.player.getOffhandItem());
   }

   private boolean hoeAvailable() {
      return this.hoeSlot() >= 0 || this.offhandHoe();
   }

   private int hoeSlot() {
      this.refreshHotbar();
      return this.cachedHoeSlot;
   }

   private int bonemealSlot() {
      return this.findItemSlot(Items.BONE_MEAL);
   }

   private boolean bonemealAvailable() {
      return this.offhandHolds(Items.BONE_MEAL) || this.bonemealSlot() >= 0;
   }

   private int fortuneSlot() {
      this.refreshHotbar();
      return this.cachedFortuneSlot;
   }

   private static boolean isHoe(ItemStack stack) {
      return stack.getItem() instanceof HoeItem;
   }

   private int fortuneLevel(ItemStack stack) {
      try {
         if (MC != null && MC.level != null && stack != null && !stack.isEmpty()) {
            if (this.fortuneHolderLevel != MC.level) {
               this.fortuneHolderLevel = MC.level;
               this.fortuneHolder = MC.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
            }

            return EnchantmentHelper.getItemEnchantmentLevel(this.fortuneHolder, stack);
         } else {
            return 0;
         }
      } catch (Throwable var3) {
         return 0;
      }
   }

   private void armSwitchBack() {
      if (this.previousSlot >= 0 && this.switchBackTicks <= 0) {
         this.switchBackTicks = 2 + this.random.nextInt(3);
      }
   }

   private void tickSwitchBack() {
      if (this.previousSlot >= 0) {
         if (!this.bool("switch-back")) {
            this.previousSlot = -1;
            this.farmSlot = -1;
            this.switchBackTicks = 0;
         } else if (MC.gui != null && MC.gui.screen() == null && MC.gui.overlay() == null) {
            if (this.farmSlot >= 0 && MC.player.getInventory().getSelectedSlot() != this.farmSlot) {
               this.previousSlot = -1;
               this.farmSlot = -1;
               this.switchBackTicks = 0;
            } else if (this.switchBackTicks > 0 && --this.switchBackTicks <= 0) {
               if (!RiptideHandArbiter.slotReserved(this.previousSlot, this.id())
                  && this.changeHotbarSlot(this.previousSlot, MC.player.getInventory().getSelectedSlot())) {
                  this.switchSettleTicks = 0;
                  this.previousSlot = -1;
                  this.farmSlot = -1;
               } else {
                  this.switchBackTicks = 1;
               }
            }
         }
      }
   }

   private void walkTick() {
      int tick = RiptideSharedState.get().getClientTickCounter();
      BlockPos goal = this.walkTarget;
      if (goal != null && (RiptidePathWalker.isBlacklisted(goal) || this.classifyGoal(goal, tick) == null)) {
         goal = null;
         this.walkTarget = null;
         this.walkScanTick = tick - 20;
      }

      if (goal == null && (this.walkScanTick == Integer.MIN_VALUE || tick - this.walkScanTick >= 20)) {
         goal = this.scanWalkTarget();
         if (!this.scanInProgress()) {
            this.walkScanTick = tick;
         }

         this.walkTarget = goal;
      }

      if (goal == null) {
         RiptidePathWalker.stop();
         this.pumpRotation(null);
      } else if (!RiptidePathWalker.tick(goal)) {
         if (RiptidePathWalker.hasArrived(goal)) {
            this.walkTarget = null;
            RiptidePathWalker.stop();
            this.noHoldUntilTick = tick + 2 + this.random.nextInt(3);
            this.walkScanTick = tick - 20;
            this.pumpRotation(null);
         } else {
            RiptidePathWalker.stop();
            RiptidePathWalker.blacklist(goal, 200);
            this.walkTarget = null;
            this.walkScanTick = tick - 20;
            this.pumpRotation(null);
         }
      } else {
         this.lastInfo = "Walk";
         BlockPos walkNode = RiptidePathWalker.currentNode();
         this.pumpRotation(RiptideRotationUtil.lookingAt(Vec3.atCenterOf(walkNode != null ? walkNode : goal), MC.player.getEyePosition()));
      }
   }

   private boolean walkThrough(AutoFarmModule.Target target) {
      int tick = RiptideSharedState.get().getClientTickCounter();
      Vec3 eye = MC.player.getEyePosition();
      double reachSqr = this.reach() * this.reach();
      BlockPos goal = this.continuation(target, eye, reachSqr, tick);
      if (goal == null) {
         return false;
      } else if (RiptidePathWalker.tick(goal)) {
         this.walkScanTick = tick - 20;
         return true;
      } else {
         this.continuationTarget = null;
         if (RiptidePathWalker.hasArrived(goal)) {
            RiptidePathWalker.stop();
            return false;
         } else {
            RiptidePathWalker.stop();
            RiptidePathWalker.blacklist(goal, 200);
            this.continuationScanTick = tick - 20;
            return false;
         }
      }
   }

   private BlockPos continuation(AutoFarmModule.Target target, Vec3 eye, double reachSqr, int tick) {
      if (target.kind() == RiptideFarmPlanner.Kind.HARVEST && this.rowContinuationTick == tick && this.rowContinuation != null) {
         return cellDistanceSqr(eye, this.rowContinuation.getX(), this.rowContinuation.getY(), this.rowContinuation.getZ()) > reachSqr
               && !RiptidePathWalker.isBlacklisted(this.rowContinuation)
               && this.classifyGoal(this.rowContinuation, tick) != null
            ? this.rowContinuation
            : null;
      } else {
         BlockPos goal = this.continuationTarget;
         if (goal != null
            && (
               RiptidePathWalker.isBlacklisted(goal)
                  || this.classifyGoal(goal, tick) == null
                  || cellDistanceSqr(eye, goal.getX(), goal.getY(), goal.getZ()) <= reachSqr
            )) {
            goal = null;
            this.continuationTarget = null;
            this.continuationScanTick = tick - 20;
         }

         if (goal == null && (this.continuationScanTick == Integer.MIN_VALUE || tick - this.continuationScanTick >= 20)) {
            goal = this.scanAhead(reachSqr, 90.0F, Math.min(12, this.searchRange()));
            if (!this.scanInProgress()) {
               this.continuationScanTick = tick;
            }

            this.continuationTarget = goal;
         }

         return goal;
      }
   }

   private RiptideFarmPlanner.Kind classifyGoal(BlockPos goal, int tick) {
      Set<Block> crops = this.cropBlocks();
      if (crops.isEmpty()) {
         return null;
      } else {
         boolean wantReplant = this.bool("replant");
         boolean wantPlant = this.bool("plant");
         boolean wantTill = this.bool("till") && this.hoeAvailable();
         boolean wantBonemeal = this.bool("bonemeal") && this.bonemealAvailable();
         return this.classifyAt(
            new MutableBlockPos(goal.getX(), goal.getY(), goal.getZ()),
            goal.getX(),
            goal.getY(),
            goal.getZ(),
            crops,
            tick,
            wantReplant,
            wantPlant,
            wantTill,
            wantBonemeal
         );
      }
   }

   private BlockPos scanWalkTarget() {
      return this.scanAhead(0.0, 360.0F, this.searchRange());
   }

   private boolean scanInProgress() {
      return this.scanning;
   }

   private BlockPos scanAhead(double minDistSqr, float maxCorrection, int maxRing) {
      Set<Block> crops = this.cropBlocks();
      if (crops.isEmpty()) {
         this.scanning = false;
         return null;
      } else {
         BlockPos origin = MC.player.blockPosition();
         int manhattan = this.searchRange();
         if (this.scanning
            && (
               this.scanMinDistSqr != minDistSqr
                  || this.scanMaxCorrection != maxCorrection
                  || this.scanMaxRing != maxRing
                  || this.scanManhattan != manhattan
                  || origin.getX() != this.scanOx
                  || origin.getY() != this.scanOy
                  || origin.getZ() != this.scanOz
            )) {
            this.scanning = false;
         }

         if (!this.scanning) {
            this.scanning = true;
            this.scanMinDistSqr = minDistSqr;
            this.scanMaxCorrection = maxCorrection;
            this.scanMaxRing = maxRing;
            this.scanManhattan = manhattan;
            this.scanMaxSqr = (long)maxRing * maxRing;
            this.scanOx = origin.getX();
            this.scanOy = origin.getY();
            this.scanOz = origin.getZ();
            this.scanPx = MC.player.getX();
            this.scanPz = MC.player.getZ();
            this.scanEye = MC.player.getEyePosition();
            this.scanLookYaw = this.sweepRotation().yaw();
            this.scanRing = 0;
            this.scanEdge = 0;
            this.scanAy = 0;
            this.scanSy = 0;
            this.walkChoices.clear();
         }

         int tick = RiptideSharedState.get().getClientTickCounter();
         boolean wantReplant = this.bool("replant");
         boolean wantPlant = this.bool("plant");
         boolean wantTill = this.bool("till") && this.hoeAvailable();
         boolean wantBonemeal = this.bool("bonemeal") && this.bonemealAvailable();
         MutableBlockPos cursor = new MutableBlockPos();
         int budget = 8000;

         while (this.scanning) {
            if (budget <= 0) {
               return null;
            }

            if (this.scanRing > this.scanMaxRing) {
               this.scanning = false;
               break;
            }

            int edgeCount = this.scanRing == 0 ? 1 : this.scanRing * 8;
            if (this.scanEdge >= edgeCount) {
               this.scanRing++;
               this.scanEdge = 0;
               this.scanAy = 0;
               this.scanSy = 0;
            } else {
               int dx;
               int dz;
               if (this.scanRing == 0) {
                  dx = 0;
                  dz = 0;
               } else {
                  int side = this.scanEdge / (this.scanRing * 2);
                  int offset = this.scanEdge % (this.scanRing * 2) - this.scanRing;
                  switch (side) {
                     case 0:
                        dx = offset;
                        dz = -this.scanRing;
                        break;
                     case 1:
                        dx = offset + 1;
                        dz = this.scanRing;
                        break;
                     case 2:
                        dx = -this.scanRing;
                        dz = offset + 1;
                        break;
                     default:
                        dx = this.scanRing;
                        dz = offset;
                  }
               }

               long horizontalSqr = (long)dx * dx + (long)dz * dz;
               if (Math.abs(dx) + Math.abs(dz) > this.scanManhattan) {
                  this.scanEdge++;
               } else if (this.scanAy > 12) {
                  this.scanEdge++;
                  this.scanAy = 0;
                  this.scanSy = 0;
               } else {
                  int syLimit = this.scanAy == 0 ? 1 : 2;
                  if (this.scanSy >= syLimit) {
                     this.scanAy++;
                     this.scanSy = 0;
                  } else {
                     int dy = this.scanSy == 0 ? this.scanAy : -this.scanAy;
                     this.scanSy++;
                     if (horizontalSqr + (long)dy * dy <= this.scanMaxSqr) {
                        budget--;
                        int cx = this.scanOx + dx;
                        int cy = this.scanOy + dy;
                        int cz = this.scanOz + dz;
                        cursor.set(cx, cy, cz);
                        if (MC.level.hasChunkAt(cursor)
                           && !RiptidePathWalker.isBlacklisted(cursor)
                           && this.classifyAt(cursor, cx, cy, cz, crops, tick, wantReplant, wantPlant, wantTill, wantBonemeal) != null
                           && (!(this.scanMinDistSqr > 0.0) || !(cellDistanceSqr(this.scanEye, cx, cy, cz) <= this.scanMinDistSqr))) {
                           float correction = Math.abs(RiptideRotationUtil.angleDifference(this.scanLookYaw, yawTowards(this.scanPx, this.scanPz, cx, cz)));
                           if (!(correction > this.scanMaxCorrection)) {
                              this.offerWalkChoice(new BlockPos(cx, cy, cz), Math.sqrt(cellDistanceSqr(this.scanEye, cx, cy, cz)) * 10.0 + correction / 18.0);
                           }
                        }
                     }
                  }
               }
            }
         }

         return this.chooseWalkRoute(tick);
      }
   }

   private void offerWalkChoice(BlockPos cell, double estimate) {
      for (int i = 0; i < this.walkChoices.size(); i++) {
         AutoFarmModule.WalkChoice old = this.walkChoices.get(i);
         if (old.pos().distSqr(cell) <= 9.0) {
            if (old.estimate() > estimate) {
               this.walkChoices.set(i, new AutoFarmModule.WalkChoice(cell, estimate));
            }

            return;
         }
      }

      this.walkChoices.add(new AutoFarmModule.WalkChoice(cell, estimate));
      this.walkChoices.sort(Comparator.comparingDouble(AutoFarmModule.WalkChoice::estimate));
      if (this.walkChoices.size() > 6) {
         this.walkChoices.removeLast();
      }
   }

   private BlockPos chooseWalkRoute(int tick) {
      BlockPos best = null;
      double bestCost = Double.POSITIVE_INFINITY;

      for (AutoFarmModule.WalkChoice choice : this.walkChoices) {
         BlockPos cell = choice.pos();
         if (this.classifyGoal(cell, tick) != null && !RiptidePathWalker.isBlacklisted(cell)) {
            double cost = RiptidePathWalker.estimateTravelCost(cell);
            if (Double.isFinite(cost)) {
               int neighbours = 0;

               for (Direction direction : Plane.HORIZONTAL) {
                  if (this.classifyGoal(cell.relative(direction), tick) != null) {
                     neighbours++;
                  }
               }

               float correction = Math.abs(
                  RiptideRotationUtil.angleDifference(this.sweepRotation().yaw(), yawTowards(MC.player.getX(), MC.player.getZ(), cell.getX(), cell.getZ()))
               );
               cost += correction / 18.0 - neighbours;
               if (cost < bestCost) {
                  bestCost = cost;
                  best = cell;
               }
            }
         }
      }

      this.walkChoices.clear();
      return best;
   }

   private static float yawTowards(double px, double pz, int x, int z) {
      return (float)(Math.toDegrees(Math.atan2(z + 0.5 - pz, x + 0.5 - px)) - 90.0);
   }

   private RiptideFarmPlanner.Kind classifyAt(
      MutableBlockPos cursor,
      int cx,
      int cy,
      int cz,
      Set<Block> crops,
      int tick,
      boolean wantReplant,
      boolean wantPlant,
      boolean wantTill,
      boolean wantBonemeal
   ) {
      BlockState state = MC.level.getBlockState(cursor);
      Block block = state.getBlock();
      if (crops.contains(block)) {
         if (this.grownOk(state) && this.harvestMemoryAvailable(cx, cy, cz, block) && this.columnBaseOk(cursor, cx, cy, cz, block)) {
            return RiptideFarmPlanner.Kind.HARVEST;
         } else {
            return wantBonemeal && this.bonemealable(cursor.set(cx, cy, cz), state) ? RiptideFarmPlanner.Kind.BONEMEAL : null;
         }
      } else if ((wantReplant || wantPlant) && state.canBeReplaced()) {
         BlockState below = MC.level.getBlockState(cursor.set(cx, cy - 1, cz));
         if (wantReplant && this.recentChoiceAt(cx, cy, cz, below, crops, tick) != null) {
            return RiptideFarmPlanner.Kind.REPLANT_RECENT;
         } else {
            return wantPlant && this.plainChoice(cx, cy, cz, below, crops) != null ? RiptideFarmPlanner.Kind.REPLANT : null;
         }
      } else {
         return !wantTill || !TILLABLE.contains(block) || block != Blocks.ROOTED_DIRT && !MC.level.getBlockState(cursor.set(cx, cy + 1, cz)).isAir()
            ? null
            : RiptideFarmPlanner.Kind.TILL;
      }
   }

   private static double cellDistanceSqr(Vec3 eye, int x, int y, int z) {
      double dx = eye.x < x ? x - eye.x : (eye.x > x + 1 ? eye.x - (x + 1) : 0.0);
      double dy = eye.y < y ? y - eye.y : (eye.y > y + 1 ? eye.y - (y + 1) : 0.0);
      double dz = eye.z < z ? z - eye.z : (eye.z > z + 1 ? eye.z - (z + 1) : 0.0);
      return dx * dx + dy * dy + dz * dz;
   }

   private static int[] sphere(int radius) {
      int clamped = Mth.clamp(radius, 0, 8);
      if (clamped == sphereRadius) {
         return sphereCache;
      } else {
         int limit = clamped * clamped;
         List<int[]> cells = new ArrayList<>();

         for (int x = -clamped; x <= clamped; x++) {
            for (int y = -clamped; y <= clamped; y++) {
               for (int z = -clamped; z <= clamped; z++) {
                  if (x * x + y * y + z * z <= limit) {
                     cells.add(new int[]{x, y, z});
                  }
               }
            }
         }

         cells.sort(Comparator.comparingInt(cellx -> cellx[0] * cellx[0] + cellx[1] * cellx[1] + cellx[2] * cellx[2]));
         int[] flat = new int[cells.size() * 3];

         for (int i = 0; i < cells.size(); i++) {
            int[] cell = cells.get(i);
            flat[i * 3] = cell[0];
            flat[i * 3 + 1] = cell[1];
            flat[i * 3 + 2] = cell[2];
         }

         sphereRadius = clamped;
         sphereCache = flat;
         return flat;
      }
   }

   private record AttemptState(RiptideFarmPlanner.Kind kind, BlockState state) {
   }

   private record Harvested(BlockPos pos, Block crop, int tick) {
   }

   private record SeedChoice(Block crop, Item seed, int slot) {
   }

   private record Target(RiptideFarmPlanner.Kind kind, BlockPos pos, Block crop, Item seed, int hotbarSlot, RiptideRotationUtil.Rotation rotation) {
   }

   private record WalkChoice(BlockPos pos, double estimate) {
   }
}
