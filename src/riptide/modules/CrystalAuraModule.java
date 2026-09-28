package riptide.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.DoubleSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.RegistryListSetting;
import riptide.mixin.accessor.RiptideMinecraftAccessor;
import riptide.mixin.accessor.RiptideMultiPlayerGameModeAccessor;
import riptide.util.RiptideCombatClicker;
import riptide.util.RiptideExplosionDamage;
import riptide.util.RiptideFaceScan;
import riptide.util.RiptideHandArbiter;
import riptide.util.RiptideInventoryHelper;
import riptide.util.RiptideKillAuraRotation;
import riptide.util.RiptideLiteVariant;
import riptide.util.RiptidePlacementTick;
import riptide.util.RiptideRemoteView;
import riptide.util.RiptideRotationUtil;
import riptide.util.RiptideServerRotationView;
import riptide.util.RiptideSharedState;
import riptide.util.RiptideSilentAim;
import riptide.util.macro.MacroExecutor;
import riptide.util.multi.MultiPilot;
import riptide.util.multi.PacketTeleportController;

public final class CrystalAuraModule extends Module implements RiptideSilentAim.Owner {
   public static final String ID = "crystal-aura";
   private static final float POWER = 6.0F;
   private static final double CELL_HEIGHT = 2.0;
   private static final int PLACE_RAY_BUDGET = 48;
   private static final double OBSIDIAN_UPGRADE_BAR = 8.0;
   private static final double OBSIDIAN_MIN_GAIN = 5.0;
   private static final double OBSIDIAN_MIN_RATIO = 2.0;
   private static final int OBSIDIAN_COMMIT_TICKS = 12;
   private static final int PLACE_COMMIT_TICKS = 6;
   private static final int BUILDER_BAN_STALL_TICKS = 4;
   private static final int PRIME_STREAK_TICKS = 2;
   private static final int PRIME_SUPPORT_RADIUS = 1;
   private static final double PRIME_MAX_SPEED_SQR = 0.0625;
   private static final double SPAM_DAMAGE_MIN_BONUS = 2.0;
   private static final int PRIME_STREAK_IDLE_TICKS = 1;
   private static final double PRIME_VULNERABLE_MIN_DROP = 1.0;
   private static final double PRIME_VULNERABLE_MAX_DROP = 5.0;
   private static final int PRIME_VULNERABLE_STREAK_TICKS = 2;
   private static final double DEFEND_MIN_DROP = 0.75;
   private static final double DEFEND_THREAT_RANGE = 5.0;
   private static final double DEFEND_MIN_REDUCTION = 2.0;
   private static final int DEFEND_MAX_BLOCKS_PER_WINDOW = 3;
   private static final int DEFEND_WINDOW_TICKS = 20;
   private static final int DEFEND_COOLDOWN_TICKS = 2;
   private static final int DEFEND_MAX_THREATS = 4;
   private static final int ACTION_SYNC_TICKS = 3;
   private static final int RECENT_PLACED_MAX = 4;
   private static final double COMMIT_MAX_ENEMY_DRIFT_SQR = 2.25;
   private static final int PRIME_BLAST_GRACE_TICKS = 2;
   private static final int DEFEND_OWN_TRACK_TICKS = 40;
   private static final int DEFEND_OWN_TRACK_MAX = 16;
   private static final double DEFEND_LOW_RING_DROP = 1.5;
   private static final double DEFEND_DIRECT_BELOW_SQR = 0.25;
   private RiptideExplosionDamage.Options options = RiptideExplosionDamage.Options.DEFAULT.withTerrain(true);
   private static final EnumSet<Direction> ALL_FACES = EnumSet.allOf(Direction.class);
   private static final double[] BOX_SAMPLES = new double[]{0.3, 0.5, 0.7};
   private static final int MAX_AIM_CANDIDATES = 4;
   private static final int MAX_FULL_EVALUATIONS = 32;
   private static int[] sphereCache = new int[0];
   private static int sphereRadius = -1;
   private static volatile boolean viewOn;
   private static volatile float viewSize = 0.3F;
   private static volatile float viewY = -0.5F;
   private static volatile float viewSpin;
   private static volatile float viewBounce = 0.25F;
   private final Random random = new Random();
   private LivingEntity target;
   private int reservedTick = Integer.MIN_VALUE;
   private BlockPos reservedCell;
   private int lastActionTick = Integer.MIN_VALUE;
   private long lastActionNanos = Long.MIN_VALUE;
   private long actionFloorNanos;
   private String cachedEntityListSource;
   private Set<String> cachedEntityIds = Set.of();
   private int previousSlot = -1;
   private int switchedToSlot = -1;
   private int switchBackTicks;
   private int hotbarChangeTick = Integer.MIN_VALUE;
   private int placeScanCursor;
   private BlockPos committedObsidianCell;
   private int committedObsidianSlot = -1;
   private int committedObsidianTarget = -1;
   private int committedObsidianUntil = Integer.MIN_VALUE;
   private double committedObsidianDamage;
   private BlockPos committedPlaceSupport;
   private int committedPlaceTarget = -1;
   private int committedPlaceUntil = Integer.MIN_VALUE;
   private BlockPos primeSupport;
   private int primeStreak;
   private int primeIdleTicks;
   private int primeVulnerableStreak;
   private int primeVulnerableTarget = -1;
   private int primeVulnerableIdleTicks;
   private int lastDefendTick = Integer.MIN_VALUE;
   private int defendWindowStart = Integer.MIN_VALUE;
   private int defendWindowCount;
   private final Map<BlockPos, Integer> recentPlacedCells = new LinkedHashMap<>();
   private int lastBreakCrystalId = -1;
   private int lastBreakTick = Integer.MIN_VALUE;
   private BlockPos builderBanSupport;
   private int builderBanTarget = -1;
   private int builderBanStallTicks;
   private BlockPos supportScanPlayerPos;
   private BlockPos supportScanEnemyPos;
   private Vec3 committedObsidianEnemyPos;
   private final Map<BlockPos, Integer> ownObsidianCells = new LinkedHashMap<>();
   private static volatile int defendIntendTick = Integer.MIN_VALUE;
   private static final double TARGET_RANGE_GRACE = 0.5;
   private static final double TARGET_DISTANCE_SWITCH_MARGIN = 1.5;
   private static final double TARGET_HP_SWITCH_RATIO = 0.75;
   private static final double TARGET_FOV_SWITCH_RATIO = 0.7;
   private static final double TARGET_FOV_SWITCH_FLOOR = 1.5;

   public CrystalAuraModule() {
      super("crystal-aura", "CrystalAura", ModuleCategory.COMBAT, "Places and detonates end crystals.");
      this.add(new BoolSetting("place", "Place", true).description("Place on obsidian and bedrock.").build());
      this.add(new BoolSetting("destroy", "Destroy", true).description("Attack crystals in range.").build());
      this.add(new BoolSetting("obsidian", "Place Obsidian", true).description("Build a support when none exists.").build());
      this.add(new DoubleSetting("target-range", "Target Range", 8.0, 1.0, 16.0, 0.5).description("Enemy search radius.").build());
      this.add(new ChoiceSetting("targeting", "Targeting", "Distance", "Distance", "HP", "FOV").description("How the enemy is picked.").build());
      this.add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player").description("Entity types to blast.").build());
      this.add(new IntSetting("hurt-time", "Hurt Time", 10, 0, 10, 1).description("Maximum hurt time.").build());
      this.add(
         new IntSetting("switch-back-delay", "Switch Back Delay", 20, 1, 100, 1)
            .description("Idle ticks before switching back.")
            .group("Place")
            .visibleWhen(() -> this.bool("place"))
            .build()
      );
      this.add(
         new BoolSetting("only-above", "Only Above", false).description("Place above target.").group("Place").visibleWhen(() -> this.bool("place")).build()
      );
      this.add(
         new DoubleSetting("min-target-damage", "Min Target Damage", 5.0, 0.0, 20.0, 0.5)
            .description("Minimum blast damage to target.")
            .group("Damage")
            .build()
      );
      this.add(
         new DoubleSetting("max-self-damage", "Max Self Damage", 4.0, 0.0, 20.0, 0.5).description("Maximum blast damage to self.").group("Damage").build()
      );
      this.add(new BoolSetting("efficient", "Efficient", true).description("Require efficient trades.").group("Damage").build());
      this.add(new BoolSetting("terrain", "Terrain", true).description("Model terrain damage.").group("Damage").build());
      this.add(new IntSetting("place-delay", "Place Delay", 75, 0, 1000, 10).description("Floor between places.").unit("ms").group("Timing").build());
      this.add(new IntSetting("destroy-delay", "Destroy Delay", 75, 0, 1000, 10).description("Floor between breaks.").unit("ms").group("Timing").build());
      this.add(new IntSetting("jitter", "Delay Jitter", 20, 0, 200, 5).description("Random extra delay.").unit("ms").group("Timing").build());
      this.add(new BoolSetting("spam", "Spam", true).description("Run at the tick floor in a prime position.").group("Timing").build());
      this.add(
         new IntSetting("spam-delay", "Spam Delay", 0, 0, 200, 10)
            .description("Floor between actions while spamming.")
            .unit("ms")
            .group("Timing")
            .visibleWhen(() -> this.bool("spam"))
            .build()
      );
      this.add(
         new BoolSetting("vulnerable-spam", "Vulnerable Spam", true)
            .description("Prime fast with the enemy trapped above; gates cadence and anchor priority.")
            .group("Timing")
            .build()
      );
      this.add(new BoolSetting("defend", "Defend", true).description("Block incoming crystals with obsidian.").group("Defend").build());
      this.add(new BoolSetting("crystal-view", "CrystalView", false).description("Shrink and slow crystal models.").group("CrystalView").build());
      this.add(
         new DoubleSetting("view-size", "Size", 0.3, 0.1, 1.5, 0.05)
            .description("Crystal model scale.")
            .group("CrystalView")
            .visibleWhen(() -> this.bool("crystal-view"))
            .build()
      );
      this.add(
         new DoubleSetting("view-y", "Y Translate", -0.5, -2.0, 2.0, 0.05)
            .description("Vertical offset in blocks.")
            .group("CrystalView")
            .visibleWhen(() -> this.bool("crystal-view"))
            .build()
      );
      this.add(
         new DoubleSetting("view-spin", "Spin Speed", 0.0, 0.0, 5.0, 0.05)
            .description("Rotation speed. 0 freezes it.")
            .group("CrystalView")
            .visibleWhen(() -> this.bool("crystal-view"))
            .build()
      );
      this.add(
         new DoubleSetting("view-bounce", "Bounce", 0.25, -1.0, 1.0, 0.05)
            .description("Bob height. Negative inverts it.")
            .group("CrystalView")
            .visibleWhen(() -> this.bool("crystal-view"))
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.resetRuntime();
      this.pushCrystalView();
   }

   @Override
   public void onDisable() {
      this.resetRuntime();
      this.pushCrystalView();
      RiptideKillAuraRotation.beginWindDown("crystal-aura");
   }

   @Override
   public void onGameLeft() {
      this.resetRuntime();
      if ("crystal-aura".equals(RiptideKillAuraRotation.currentOwner())) {
         RiptideKillAuraRotation.reset();
      }
   }

   private void resetRuntime() {
      this.target = null;
      this.reservedTick = Integer.MIN_VALUE;
      this.reservedCell = null;
      this.lastActionTick = Integer.MIN_VALUE;
      this.lastActionNanos = Long.MIN_VALUE;
      this.actionFloorNanos = 0L;
      this.previousSlot = -1;
      this.switchedToSlot = -1;
      this.switchBackTicks = 0;
      this.hotbarChangeTick = Integer.MIN_VALUE;
      this.placeScanCursor = 0;
      this.primeSupport = null;
      this.primeStreak = 0;
      this.primeIdleTicks = 0;
      this.primeVulnerableStreak = 0;
      this.primeVulnerableTarget = -1;
      this.primeVulnerableIdleTicks = 0;
      this.lastDefendTick = Integer.MIN_VALUE;
      this.defendWindowStart = Integer.MIN_VALUE;
      this.defendWindowCount = 0;
      this.recentPlacedCells.clear();
      this.lastBreakCrystalId = -1;
      this.lastBreakTick = Integer.MIN_VALUE;
      this.builderBanSupport = null;
      this.builderBanTarget = -1;
      this.builderBanStallTicks = 0;
      defendIntendTick = Integer.MIN_VALUE;
      this.supportScanPlayerPos = null;
      this.supportScanEnemyPos = null;
      this.committedObsidianEnemyPos = null;
      this.ownObsidianCells.clear();
      this.clearPlanCommitments();
      RiptideHandArbiter.releaseAll("crystal-aura");
   }

   @Override
   public boolean ticksWhenDisabled() {
      return true;
   }

   @Override
   public boolean hasDisabledTickWork() {
      return viewOn
         || this.bool("crystal-view")
         || "crystal-aura".equals(RiptideKillAuraRotation.currentOwner()) && RiptideKillAuraRotation.hasCurrentRotation();
   }

   @Override
   public void tick() {
      this.pushCrystalView();
      if (!this.isEnabled() && MC != null && MC.player != null) {
         if ("crystal-aura".equals(RiptideKillAuraRotation.currentOwner())) {
            RiptideKillAuraRotation.update("crystal-aura", MC.player);
         }
      }
   }

   @Override
   protected void onOptionValueChanged(String settingId) {
      this.pushCrystalView();
      if ("vulnerable-spam".equals(settingId)) {
         this.primeSupport = null;
         this.primeStreak = 0;
         this.primeIdleTicks = 0;
         this.primeVulnerableStreak = 0;
         this.primeVulnerableTarget = -1;
         this.primeVulnerableIdleTicks = 0;
      }
   }

   @Override
   public String info() {
      LivingEntity current = this.target;
      return current == null ? "" : current.getName().getString();
   }

   public static boolean reservesCombatTick() {
      if (ModuleRegistry.get("crystal-aura") instanceof CrystalAuraModule aura && aura.isEnabled()) {
         int age = RiptideSharedState.get().getClientTickCounter() - aura.reservedTick;
         return age >= 0 && age <= 5;
      } else {
         return false;
      }
   }

   public static boolean ownsSilentRotation() {
      return reservesCombatTick() && "crystal-aura".equals(RiptideKillAuraRotation.currentOwner());
   }

   public static boolean reservesPlacementCell(BlockPos cell) {
      if (cell == null) {
         return false;
      } else if (!(ModuleRegistry.get("crystal-aura") instanceof CrystalAuraModule aura && aura.isEnabled())) {
         return false;
      } else if (aura.reservedTick != RiptideSharedState.get().getClientTickCounter()) {
         return false;
      } else {
         BlockPos reserved = aura.reservedCell;
         return reserved == null
            ? false
            : cell.getX() == reserved.getX() && cell.getZ() == reserved.getZ() && cell.getY() >= reserved.getY() && cell.getY() < reserved.getY() + 2;
      }
   }

   public static boolean holdsBorrowedSlot(int slot) {
      return ModuleRegistry.get("crystal-aura") instanceof CrystalAuraModule aura && aura.isEnabled()
         ? aura.previousSlot >= 0 && aura.switchedToSlot == slot
         : false;
   }

   public static boolean hasLiveCommitment() {
      return ModuleRegistry.get("crystal-aura") instanceof CrystalAuraModule aura && aura.isEnabled()
         ? aura.committedObsidianCell != null || aura.committedPlaceSupport != null
         : false;
   }

   public static boolean inPrimePosition() {
      return ModuleRegistry.get("crystal-aura") instanceof CrystalAuraModule aura && aura.isEnabled() ? aura.primePosition() : false;
   }

   public static boolean defendIntends() {
      int age = RiptideSharedState.get().getClientTickCounter() - defendIntendTick;
      return age >= 0 && age <= 1;
   }

   public static boolean reservesCommittedCell(BlockPos cell) {
      if (cell == null) {
         return false;
      } else if (ModuleRegistry.get("crystal-aura") instanceof CrystalAuraModule aura && aura.isEnabled()) {
         BlockPos obsidian = aura.committedObsidianCell;
         if (obsidian != null
            && cell.getX() == obsidian.getX()
            && cell.getZ() == obsidian.getZ()
            && cell.getY() >= obsidian.getY()
            && cell.getY() < obsidian.getY() + 2) {
            return true;
         } else {
            BlockPos support = aura.committedPlaceSupport;
            return support != null && cell.getX() == support.getX() && cell.getZ() == support.getZ() && cell.getY() == support.getY() + 1;
         }
      } else {
         return false;
      }
   }

   @Override
   public boolean silentCorrectionApplies() {
      boolean enabled = this.isEnabled();
      return !RiptideSilentAim.scaffoldOwnsRotation() && (RiptideKillAuraRotation.isWindingDown() || enabled && this.canRun());
   }

   private boolean canRun() {
      return MC != null
         && MC.player != null
         && MC.level != null
         && MC.gameMode != null
         && MC.getConnection() != null
         && MC.gui.screen() == null
         && MC.gui.overlay() == null
         && !MC.player.isDeadOrDying()
         && !MC.player.isSpectator()
         && !MC.player.isUsingItem()
         && !MC.player.isHandsBusy()
         && !PackHideState.isActive()
         && !PackFreecamState.isActive()
         && !RiptideRemoteView.isActive()
         && !MultiPilot.isActive()
         && !MacroExecutor.isRunning()
         && !PacketTeleportController.ownsMainMovement()
         && !RiptideBlinkManager.holdsActionsWithoutMovement()
         && !RiptideSilentAim.scaffoldOwnsRotation()
         && !ScaffoldModule.reservesRageInput()
         && !AutoTotemModule.operationActive()
         && !AutoArmorModule.operationActive();
   }

   @Override
   public void preMovementTick() {
      if (!RiptideLiteVariant.enabled()) {
         if (MC != null && MC.player != null && MC.level != null) {
            this.tickSwitchBack();
            if (!this.canRun()) {
               this.target = null;
               this.clearPlanCommitments();
               this.primeSupport = null;
               this.primeStreak = 0;
               this.primeIdleTicks = 0;
               this.primeVulnerableStreak = 0;
               this.primeVulnerableTarget = -1;
               this.primeVulnerableIdleTicks = 0;
               this.standDown();
            } else {
               LivingEntity enemy = this.selectTarget();
               this.target = enemy;
               if (enemy == null) {
                  this.clearPlanCommitments();
                  this.primeSupport = null;
                  this.primeStreak = 0;
                  this.primeIdleTicks = 0;
                  this.primeVulnerableStreak = 0;
                  this.primeVulnerableTarget = -1;
                  this.primeVulnerableIdleTicks = 0;
                  this.standDown();
               } else {
                  this.options = RiptideExplosionDamage.Options.DEFAULT.withTerrain(this.bool("terrain"));
                  CrystalAuraModule.PlacePlan place = null;
                  CrystalAuraModule.ObsidianPlan obsidian = null;
                  CrystalAuraModule.ObsidianPlan defend = null;

                  CrystalAuraModule.DestroyPlan destroy;
                  try (RiptideExplosionDamage.ScanPass pass = RiptideExplosionDamage.beginScan()) {
                     destroy = this.planDestroy(enemy);
                     if (destroy == null) {
                        RiptideFaceScan.Budget budget = new RiptideFaceScan.Budget(48);
                        if (this.revalidateObsidianCommit(enemy)) {
                           obsidian = this.aimCommittedObsidian(budget);
                        } else if (this.revalidatePlaceCommit(enemy)) {
                           place = this.aimCommittedPlace(enemy, budget);
                        } else {
                           place = this.planPlace(enemy, budget);
                           boolean builderSuppressed = false;
                           if (this.bool("place") && this.bool("obsidian") && this.hasCrystalAvailable() && (place == null || place.targetDamage() < 8.0)) {
                              if (this.builderBanned(enemy)) {
                                 builderSuppressed = true;
                              } else if (!this.awaitingSupportSync()) {
                                 obsidian = this.planObsidian(enemy, budget, place == null ? -1.0 : place.targetDamage());
                                 if (obsidian != null) {
                                    place = null;
                                    this.commitObsidian(enemy, obsidian);
                                 }
                              }
                           }

                           if (obsidian == null && place != null) {
                              this.commitPlace(enemy, place);
                           }

                           if (builderSuppressed && place == null) {
                              if (++this.builderBanStallTicks >= 4) {
                                 this.builderBanSupport = null;
                                 this.builderBanStallTicks = 0;
                              }
                           } else {
                              this.builderBanStallTicks = 0;
                           }
                        }

                        if (place == null && obsidian == null && this.committedObsidianCell == null && this.committedPlaceSupport == null) {
                           defend = this.planDefense(budget);
                           if (defend != null) {
                              defendIntendTick = RiptideSharedState.get().getClientTickCounter();
                           }
                        }
                     }
                  }

                  this.trackPrimePosition(enemy, destroy, place, obsidian);
                  if (destroy == null && place == null && obsidian == null && defend == null) {
                     this.standDown();
                  } else if (this.primePosition()
                     || !AnchorAuraModule.reservesCombatTick()
                     || destroy == null && place == null && obsidian == null && defend != null && !AnchorAuraModule.worksThisTick()) {
                     this.reservedTick = RiptideSharedState.get().getClientTickCounter();
                     this.reservedCell = null;
                     boolean borrowsHand = place != null && place.hand() != InteractionHand.OFF_HAND
                        || obsidian != null && obsidian.hand() != InteractionHand.OFF_HAND
                        || defend != null && defend.hand() != InteractionHand.OFF_HAND
                        || this.previousSlot >= 0;
                     if (borrowsHand) {
                        RiptideHandArbiter.holdHand("crystal-aura");
                     } else {
                        RiptideHandArbiter.releaseHand("crystal-aura");
                     }

                     RiptideRotationUtil.Rotation wire = this.wireRotation();
                     RiptideRotationUtil.Rotation goal = destroy != null
                        ? destroy.goal()
                        : (
                           place != null
                              ? place.candidate().aim().goal()
                              : (obsidian != null ? obsidian.candidate().aim().goal() : defend.candidate().aim().goal())
                        );
                     boolean ready = wire != null && this.cadenceReady();
                     Vec3 destroyHit = ready && destroy != null ? this.destroyHit(destroy, wire) : null;
                     BlockHitResult placeHit = ready && destroy == null && place != null ? this.placeLands(place, wire) : null;
                     BlockHitResult obsidianHit = ready && destroy == null && place == null && obsidian != null ? this.obsidianLands(obsidian, wire) : null;
                     BlockHitResult defendHit = ready && destroy == null && place == null && obsidian == null && defend != null
                        ? this.obsidianLands(defend, wire)
                        : null;
                     boolean fire = ready
                        && (
                           destroy != null
                              ? destroyHit != null
                              : (place != null ? placeHit != null : (obsidian != null ? obsidianHit != null : defendHit != null))
                        );
                     fire = fire && !BedDefenderModule.ownsSilentRotation() && !SurroundModule.ownsSilentRotation();
                     RiptideKillAuraRotation.setTarget("crystal-aura", 18, fire ? wire : goal);
                     RiptideKillAuraRotation.update("crystal-aura", MC.player, fire);
                     if (fire) {
                        RiptideRotationUtil.Rotation outgoing = RiptideSilentAim.activeOutgoingRotation(MC.player);
                        if (outgoing != null && sameRotation(outgoing, wire)) {
                           if (destroy != null) {
                              if (this.executeDestroy(destroy, destroyHit)) {
                                 this.clearPlanCommitments();
                              }
                           } else if (place != null) {
                              if (this.executePlace(place, placeHit)) {
                                 this.clearPlanCommitments();
                              }
                           } else if (obsidian != null) {
                              if (this.executeObsidian(obsidian, obsidianHit)) {
                                 this.clearPlanCommitments();
                              }
                           } else {
                              this.executeDefend(defend, defendHit);
                           }
                        }
                     }
                  } else {
                     this.standDown();
                  }
               }
            }
         }
      }
   }

   private static boolean sameRotation(RiptideRotationUtil.Rotation first, RiptideRotationUtil.Rotation second) {
      return Math.abs(RiptideRotationUtil.angleDifference(first.yaw(), second.yaw())) <= 0.05F && Math.abs(first.pitch() - second.pitch()) <= 0.05F;
   }

   private void standDown() {
      RiptideHandArbiter.releaseHand("crystal-aura");
      if ("crystal-aura".equals(RiptideKillAuraRotation.currentOwner())) {
         RiptideKillAuraRotation.beginWindDown("crystal-aura");
         RiptideKillAuraRotation.update("crystal-aura", MC.player);
      }
   }

   private RiptideRotationUtil.Rotation wireRotation() {
      RiptideServerRotationView.WireSnapshot snapshot = RiptideServerRotationView.snapshot();
      return !snapshot.initialized() ? null : new RiptideRotationUtil.Rotation(snapshot.currentYaw(), snapshot.currentPitch());
   }

   private RiptideRotationUtil.Rotation aimReference() {
      RiptideRotationUtil.Rotation wire = this.wireRotation();
      return wire != null ? wire : RiptideRotationUtil.playerRotation(MC.player);
   }

   private boolean cadenceReady() {
      int tick = RiptideSharedState.get().getClientTickCounter();
      if (tick == this.lastActionTick) {
         return false;
      } else {
         return this.lastActionNanos == Long.MIN_VALUE ? true : System.nanoTime() - this.lastActionNanos >= this.actionFloorNanos;
      }
   }

   private void bookAction(boolean destroy) {
      this.lastActionTick = RiptideSharedState.get().getClientTickCounter();
      this.lastActionNanos = System.nanoTime();
      if (this.spamCadence()) {
         this.actionFloorNanos = this.integer("spam-delay") * 1000000L;
      } else {
         int jitter = this.integer("jitter");
         long extra = jitter > 0 ? this.random.nextInt(jitter + 1) : 0L;
         this.actionFloorNanos = (this.integer(destroy ? "destroy-delay" : "place-delay") + extra) * 1000000L;
      }

      this.switchBackTicks = this.integer("switch-back-delay");
   }

   private void trackPrimePosition(
      LivingEntity enemy, CrystalAuraModule.DestroyPlan destroy, CrystalAuraModule.PlacePlan place, CrystalAuraModule.ObsidianPlan obsidian
   ) {
      if (place == null) {
         boolean held = destroy != null || obsidian != null || this.committedObsidianCell != null || this.committedPlaceSupport != null;
         if (held) {
            this.primeIdleTicks = 0;
            this.primeVulnerableIdleTicks = 0;
         } else {
            if (++this.primeIdleTicks > 1) {
               this.primeSupport = null;
               this.primeStreak = 0;
            }

            if (++this.primeVulnerableIdleTicks > 1) {
               this.primeVulnerableStreak = 0;
               this.primeVulnerableTarget = -1;
            }
         }
      } else {
         this.primeIdleTicks = 0;
         BlockPos support = place.support();
         boolean strong = place.targetDamage() >= this.decimal("min-target-damage") + 2.0;
         boolean sameArea = this.primeSupport != null
            && Math.abs(support.getX() - this.primeSupport.getX()) <= 1
            && Math.abs(support.getY() - this.primeSupport.getY()) <= 1
            && Math.abs(support.getZ() - this.primeSupport.getZ()) <= 1;
         this.primeSupport = support;
         if (!strong) {
            this.primeStreak = 0;
         } else {
            this.primeStreak = sameArea ? this.primeStreak + 1 : 1;
         }

         if (this.vulnerableGeometry(enemy)) {
            this.primeVulnerableIdleTicks = 0;
            this.primeVulnerableStreak = enemy.getId() == this.primeVulnerableTarget ? this.primeVulnerableStreak + 1 : 1;
            this.primeVulnerableTarget = enemy.getId();
         } else if (++this.primeVulnerableIdleTicks > 1) {
            this.primeVulnerableStreak = 0;
            this.primeVulnerableTarget = -1;
         }
      }
   }

   private boolean primePosition() {
      LivingEntity enemy = this.target;
      if (enemy != null && MC.player != null) {
         int age = RiptideSharedState.get().getClientTickCounter() - this.lastBreakTick;
         boolean blastGrace = age >= 0 && age <= 2;
         if (!this.bool("vulnerable-spam") || this.primeVulnerableStreak < 2 || !this.vulnerableGeometry(enemy) && this.primeVulnerableIdleTicks > 1) {
            if (this.primeStreak < 2) {
               return false;
            } else {
               return blastGrace ? true : MC.player.getDeltaMovement().lengthSqr() <= 0.0625 && enemy.getDeltaMovement().lengthSqr() <= 0.0625;
            }
         } else {
            return blastGrace ? true : horizontalSpeedSqr(MC.player) <= 0.0625 && horizontalSpeedSqr(enemy) <= 0.0625;
         }
      } else {
         return false;
      }
   }

   private boolean vulnerableGeometry(LivingEntity enemy) {
      double drop = enemy.getY() - MC.player.getY();
      if (!(drop < 1.0) && !(drop > 5.0)) {
         double dx = enemy.getX() - MC.player.getX();
         double dz = enemy.getZ() - MC.player.getZ();
         double range = MC.player.blockInteractionRange();
         return dx * dx + dz * dz <= range * range;
      } else {
         return false;
      }
   }

   private static double horizontalSpeedSqr(Entity entity) {
      Vec3 delta = entity.getDeltaMovement();
      return delta.x * delta.x + delta.z * delta.z;
   }

   private boolean spamCadence() {
      return this.bool("spam") && this.primePosition();
   }

   private LivingEntity selectTarget() {
      LocalPlayer player = MC.player;
      double range = this.decimal("target-range");
      AABB search = player.getBoundingBox().inflate(range);
      List<LivingEntity> found = MC.level
         .getEntitiesOfClass(
            LivingEntity.class,
            search,
            entity -> entity != player
               && entity.isAlive()
               && !entity.isSpectator()
               && this.matchesEntity(entity)
               && !RiptideAntiBot.suppress(entity)
               && !TeamsModule.combatExcluded(entity, "killaura")
               && entity.hurtTime <= this.integer("hurt-time")
               && RiptideExplosionDamage.effectiveHealth(entity) > 0.0
         );
      if (found.isEmpty()) {
         return null;
      } else {
         Vec3 eye = player.getEyePosition();
         double rangeSq = range * range;
         String mode = this.choice("targeting");
         LivingEntity best = null;
         double bestScore = Double.MAX_VALUE;

         for (LivingEntity entity : found) {
            double distanceSq = boxDistanceSqr(entity.getBoundingBox(), eye);
            if (!(distanceSq > rangeSq)) {
               double score = this.targetingScore(mode, entity, distanceSq, eye);
               if (score < bestScore) {
                  bestScore = score;
                  best = entity;
               }
            }
         }

         LivingEntity incumbent = this.target;
         if (incumbent != null && incumbent != best && found.contains(incumbent)) {
            double grace = range + 0.5;
            double incumbentDistanceSq = boxDistanceSqr(incumbent.getBoundingBox(), eye);
            if (incumbentDistanceSq <= grace * grace
               && (best == null || !clearlyBeats(mode, bestScore, this.targetingScore(mode, incumbent, incumbentDistanceSq, eye)))) {
               return incumbent;
            }
         }

         return best;
      }
   }

   private double targetingScore(String mode, LivingEntity entity, double distanceSq, Vec3 eye) {
      return switch (mode) {
         case "HP" -> RiptideExplosionDamage.effectiveHealth(entity);
         case "FOV" -> RiptideRotationUtil.rotationAngleTo(
            RiptideRotationUtil.playerRotation(MC.player), RiptideRotationUtil.lookingAt(entity.getBoundingBox().getCenter(), eye)
         );
         default -> distanceSq;
      };
   }

   private static boolean clearlyBeats(String mode, double challengerScore, double incumbentScore) {
      return switch (mode) {
         case "HP" -> challengerScore <= incumbentScore * 0.75;
         case "FOV" -> challengerScore <= incumbentScore * 0.7 && challengerScore <= incumbentScore - 1.5;
         default -> Math.sqrt(challengerScore) <= Math.sqrt(incumbentScore) - 1.5;
      };
   }

   private boolean matchesEntity(Entity entity) {
      String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().toLowerCase(Locale.ROOT);
      Set<String> ids = this.cachedEntityIds();
      int separator = id.indexOf(58);
      return ids.contains(id) || separator >= 0 && ids.contains(id.substring(separator + 1));
   }

   private Set<String> cachedEntityIds() {
      List<String> entries = this.list("entities");
      String source = String.join("|", entries);
      if (source.equals(this.cachedEntityListSource)) {
         return this.cachedEntityIds;
      } else {
         Set<String> normalized = new LinkedHashSet<>();

         for (String entry : entries) {
            if (entry != null) {
               String value = entry.trim().toLowerCase(Locale.ROOT);
               if (!value.isEmpty()) {
                  normalized.add(value);
                  int separator = value.indexOf(58);
                  if (separator >= 0 && separator + 1 < value.length()) {
                     normalized.add(value.substring(separator + 1));
                  }
               }
            }
         }

         this.cachedEntityListSource = source;
         this.cachedEntityIds = Set.copyOf(normalized);
         return this.cachedEntityIds;
      }
   }

   private CrystalAuraModule.DestroyPlan planDestroy(LivingEntity enemy) {
      if (!this.bool("destroy")) {
         return null;
      } else {
         LocalPlayer player = MC.player;
         double range = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
         if (range <= 0.0) {
            return null;
         } else {
            Vec3 eye = player.getEyePosition();
            AABB search = player.getBoundingBox().inflate(range + 2.0);
            List<EndCrystal> crystals = MC.level.getEntitiesOfClass(EndCrystal.class, search, Entity::isAlive);
            if (crystals.isEmpty()) {
               return null;
            } else {
               double rangeSq = range * range;
               int tick = RiptideSharedState.get().getClientTickCounter();
               if (this.lastBreakCrystalId >= 0) {
                  Entity tracked = MC.level.getEntity(this.lastBreakCrystalId);
                  if (tracked == null || !tracked.isAlive()) {
                     this.lastBreakCrystalId = -1;
                  }
               }

               if (!this.recentPlacedCells.isEmpty()) {
                  for (EndCrystal crystal : crystals) {
                     this.recentPlacedCells.remove(BlockPos.containing(crystal.position()));
                  }
               }

               EndCrystal best = null;
               RiptideExplosionDamage.Ranking bestRank = null;

               for (EndCrystal crystal : crystals) {
                  if ((crystal.getId() != this.lastBreakCrystalId || tick - this.lastBreakTick < 0 || tick - this.lastBreakTick >= 3)
                     && !(boxDistanceSqr(crystal.getBoundingBox(), eye) > rangeSq)) {
                     RiptideExplosionDamage.Ranking rank = RiptideExplosionDamage.cachedRank(enemy, crystal.position(), 6.0F, this.options);
                     if (this.damagePasses(enemy, rank) && (bestRank == null || this.betterRank(rank, bestRank))) {
                        best = crystal;
                        bestRank = rank;
                     }
                  }
               }

               if (best == null) {
                  return null;
               } else {
                  RiptideRotationUtil.Rotation goal = this.destroyAim(best, range);
                  return goal == null ? null : new CrystalAuraModule.DestroyPlan(best, goal, range);
               }
            }
         }
      }
   }

   private RiptideRotationUtil.Rotation destroyAim(EndCrystal crystal, double range) {
      Vec3 eye = MC.player.getEyePosition();
      RiptideRotationUtil.Rotation wire = this.aimReference();
      AABB box = crystal.getBoundingBox();
      RiptideRotationUtil.Rotation best = null;
      float bestDelta = Float.MAX_VALUE;

      for (double fx : BOX_SAMPLES) {
         for (double fy : BOX_SAMPLES) {
            for (double fz : BOX_SAMPLES) {
               Vec3 point = new Vec3(Mth.lerp(fx, box.minX, box.maxX), Mth.lerp(fy, box.minY, box.maxY), Mth.lerp(fz, box.minZ, box.maxZ));
               double distance = eye.distanceTo(point);
               if (!(distance > range) && !this.blockedByTerrain(eye, point)) {
                  RiptideRotationUtil.Rotation rotation = RiptideRotationUtil.lookingAt(point, eye);
                  float delta = RiptideRotationUtil.rotationAngleTo(rotation, wire);
                  if (delta < bestDelta) {
                     bestDelta = delta;
                     best = rotation;
                  }
               }
            }
         }
      }

      return best;
   }

   private Vec3 destroyHit(CrystalAuraModule.DestroyPlan plan, RiptideRotationUtil.Rotation wire) {
      if (!plan.crystal().isAlive()) {
         return null;
      } else if (((RiptideMinecraftAccessor)MC).riptide$getMissTime() > 0) {
         return null;
      } else {
         Vec3 eye = MC.player.getEyePosition();
         Vec3 look = Vec3.directionFromRotation(wire.pitch(), wire.yaw());
         Optional<Vec3> hit = plan.crystal().getBoundingBox().clip(eye, eye.add(look.scale(plan.range())));
         if (hit.isEmpty()) {
            return null;
         } else if (eye.distanceTo(hit.get()) > plan.range()) {
            return null;
         } else {
            return this.blockedByTerrain(eye, hit.get()) ? null : hit.get();
         }
      }
   }

   private boolean executeDestroy(CrystalAuraModule.DestroyPlan plan, Vec3 hitPoint) {
      EndCrystal crystal = plan.crystal();
      if (!crystal.isAlive()) {
         return false;
      } else if (!RiptideHandArbiter.beginHandPacketGroup("crystal-aura")) {
         return false;
      } else {
         try {
            ((RiptideMultiPlayerGameModeAccessor)MC.gameMode).riptide$ensureHasSentCarriedItem();
         } finally {
            RiptideHandArbiter.endHandPacketGroup("crystal-aura");
         }

         if (!RiptideCombatClicker.queueAttack(new EntityHitResult(crystal, hitPoint))) {
            return false;
         } else {
            this.bookAction(true);
            this.lastBreakCrystalId = crystal.getId();
            this.lastBreakTick = RiptideSharedState.get().getClientTickCounter();
            MC.player.resetAttackStrengthTicker();
            return true;
         }
      }
   }

   private CrystalAuraModule.PlacePlan planPlace(LivingEntity enemy, RiptideFaceScan.Budget budget) {
      if (!this.bool("place")) {
         return null;
      } else {
         LocalPlayer player = MC.player;
         double range = player.blockInteractionRange();
         if (range <= 0.0) {
            return null;
         } else if (!this.hasCrystalAvailable()) {
            return null;
         } else {
            List<BlockPos> supports = this.rankSupports(enemy, range);
            if (supports.isEmpty()) {
               return null;
            } else {
               Vec3 eye = player.getEyePosition();
               RiptideRotationUtil.Rotation wire = this.aimReference();
               RiptideFaceScan.Refusal[] refusal = new RiptideFaceScan.Refusal[1];
               List<CrystalAuraModule.ScanEntry> entries = new ArrayList<>();
               List<RiptideFaceScan.Option> scan = new ArrayList<>();

               for (BlockPos support : supports) {
                  BlockPos cell = support.above();
                  BlockState state = MC.level.getBlockState(support);
                  RiptideFaceScan.Request request = new RiptideFaceScan.Request(cell, eye, range, RiptideFaceScan.onSupport(support, ALL_FACES))
                     .from(wire)
                     .leadEye(eye.add(player.getDeltaMovement()))
                     .budget(budget);
                  scan.clear();
                  this.supportOptions(support, state, cell, eye, wire, scan);

                  for (RiptideFaceScan.Option option : scan) {
                     entries.add(new CrystalAuraModule.ScanEntry(support, request, option));
                  }
               }

               if (entries.isEmpty()) {
                  return null;
               } else {
                  int size = entries.size();
                  int start = this.placeScanCursor >= 0 && this.placeScanCursor < size ? this.placeScanCursor : 0;

                  for (int step = 0; step < size; step++) {
                     int index = (start + step) % size;
                     CrystalAuraModule.ScanEntry entry = entries.get(index);
                     RiptideFaceScan.Aim aim = RiptideFaceScan.solve(entry.option(), entry.request(), refusal);
                     if (aim != null) {
                        RiptideFaceScan.Candidate candidate = RiptideFaceScan.probe(entry.option(), aim, entry.request(), refusal);
                        if (candidate != null) {
                           InteractionHand hand = this.ensureCrystalHand();
                           this.placeScanCursor = 0;
                           BlockPos chosen = entry.support();
                           Vec3 chosenSource = new Vec3(chosen.getX() + 0.5, chosen.getY() + 1.0, chosen.getZ() + 0.5);
                           double chosenDamage = RiptideExplosionDamage.cachedRank(enemy, chosenSource, 6.0F, this.options).targetDamage();
                           return new CrystalAuraModule.PlacePlan(chosen, hand, range, candidate, entry.request(), chosenDamage);
                        }

                        if (refusal[0] == RiptideFaceScan.Refusal.NO_BUDGET) {
                           this.placeScanCursor = index;
                           return null;
                        }
                     }
                  }

                  this.placeScanCursor = 0;
                  return null;
               }
            }
         }
      }
   }

   private void supportOptions(BlockPos support, BlockState state, BlockPos cell, Vec3 eye, RiptideRotationUtil.Rotation wire, List<RiptideFaceScan.Option> out) {
      boolean requiresSneak = RiptideFaceScan.sneakUnlocks(state, support);
      List<CrystalAuraModule.TurnKeyedOption> keyed = new ArrayList<>();

      for (Direction face : RiptideFaceScan.FACE_ORDER_UP_FIRST) {
         RiptideFaceScan.Intent intent = new RiptideFaceScan.Intent(support, face);

         for (AABB rect : RiptideFaceScan.faceRects(state, support, face, 3)) {
            RiptideFaceScan.Option option = new RiptideFaceScan.Option(
               cell, support, face, 1, state, rect, rectArea(rect, face), RiptideFaceScan.edgeConfidence(rect, face, eye), requiresSneak, intent
            );
            double turn = RiptideRotationUtil.rotationAngleTo(RiptideRotationUtil.lookingAt(RiptideFaceScan.faceCentre(rect, face), eye), wire);
            keyed.add(new CrystalAuraModule.TurnKeyedOption(option, turn));
         }
      }

      keyed.sort(Comparator.comparingDouble(CrystalAuraModule.TurnKeyedOption::turn));

      for (CrystalAuraModule.TurnKeyedOption entry : keyed) {
         out.add(entry.option());
      }
   }

   private static double rectArea(AABB rect, Direction face) {
      Axis normal = face.getAxis();
      Axis first = RiptideFaceScan.inPlaneAxis(normal, true);
      Axis second = RiptideFaceScan.inPlaneAxis(normal, false);
      return (rect.max(first) - rect.min(first)) * (rect.max(second) - rect.min(second));
   }

   private List<BlockPos> rankSupports(LivingEntity enemy, double range) {
      LocalPlayer player = MC.player;
      Vec3 eye = player.getEyePosition();
      BlockPos origin = player.blockPosition();
      int[] offsets = sphere(Mth.ceil(range));
      double floor = this.decimal("min-target-damage");
      double rangeSq = range * range;
      AABB nextBox = player.getBoundingBox().move(player.getDeltaMovement());
      List<CrystalAuraModule.Candidate> candidates = new ArrayList<>();
      MutableBlockPos cursor = new MutableBlockPos();

      for (int i = 0; i + 2 < offsets.length; i += 3) {
         cursor.set(origin.getX() + offsets[i], origin.getY() + offsets[i + 1], origin.getZ() + offsets[i + 2]);
         if (this.supportsCrystal(cursor) && MC.level.isEmptyBlock(cursor.above())) {
            Vec3 source = new Vec3(cursor.getX() + 0.5, cursor.getY() + 1.0, cursor.getZ() + 0.5);
            if (!this.bool("only-above") || !(source.y < enemy.getY())) {
               double distanceSq = eye.distanceToSqr(source);
               if (!(distanceSq > rangeSq)) {
                  double bound = RiptideExplosionDamage.maxDamageTo(enemy, source, 6.0F, this.options);
                  if (!(bound < floor) && this.cellIsFree(cursor, nextBox) && !this.blockedByUnsyncedPlacement(cursor)) {
                     candidates.add(new CrystalAuraModule.Candidate(cursor.immutable(), source, bound, distanceSq));
                  }
               }
            }
         }
      }

      if (candidates.isEmpty()) {
         return List.of();
      } else {
         candidates.sort(Comparator.comparingDouble(CrystalAuraModule.Candidate::bound).reversed());
         List<CrystalAuraModule.Scored> accepted = new ArrayList<>();
         double bestDamage = -1.0;
         int evaluated = 0;

         for (CrystalAuraModule.Candidate candidate : candidates) {
            if (accepted.size() >= 4 && candidate.bound() <= bestDamage || evaluated >= 32) {
               break;
            }

            if (this.supportVisible(eye, candidate.pos(), candidate.source())) {
               evaluated++;
               RiptideExplosionDamage.Ranking rank = RiptideExplosionDamage.cachedRank(enemy, candidate.source(), 6.0F, this.options);
               if (this.damagePasses(enemy, rank)) {
                  accepted.add(new CrystalAuraModule.Scored(candidate.pos(), rank, candidate.distanceSq()));
                  bestDamage = Math.max(bestDamage, rank.targetDamage());
               }
            }
         }

         if (accepted.isEmpty()) {
            return List.of();
         } else {
            accepted.sort((first, second) -> {
               int byBucket = Double.compare(damageBucket(second.rank().targetDamage()), damageBucket(first.rank().targetDamage()));
               if (byBucket != 0) {
                  return byBucket;
               } else {
                  int bySelf = Double.compare(first.rank().selfDamage(), second.rank().selfDamage());
                  return bySelf != 0 ? bySelf : Double.compare(second.distanceSq(), first.distanceSq());
               }
            });
            List<BlockPos> result = new ArrayList<>(Math.min(4, accepted.size()));

            for (CrystalAuraModule.Scored scored : accepted) {
               if (result.size() >= 4) {
                  break;
               }

               result.add(scored.pos());
            }

            return result;
         }
      }
   }

   private static double damageBucket(double targetDamage) {
      return Math.floor(targetDamage * 2.0);
   }

   private boolean supportsCrystal(BlockPos pos) {
      BlockState state = MC.level.getBlockState(pos);
      return state.getBlock() == Blocks.OBSIDIAN || state.getBlock() == Blocks.BEDROCK;
   }

   private boolean cellIsFree(BlockPos support, AABB nextBox) {
      BlockPos cell = support.above();
      if (!MC.level.isEmptyBlock(cell)) {
         return false;
      } else {
         AABB box = new AABB(cell.getX(), cell.getY(), cell.getZ(), cell.getX() + 1.0, cell.getY() + 2.0, cell.getZ() + 1.0);
         if (nextBox.intersects(box)) {
            return false;
         } else {
            List<Entity> occupants = MC.level.getEntities((Entity)null, box);
            if (!occupants.isEmpty()) {
               this.retireSyncedPlacement(cell, occupants);
            }

            return occupants.isEmpty();
         }
      }
   }

   private boolean blockedByUnsyncedPlacement(BlockPos support) {
      Integer until = this.recentPlacedCells.get(support.above());
      if (until == null) {
         return false;
      } else if (RiptideSharedState.get().getClientTickCounter() - until >= 0) {
         this.recentPlacedCells.remove(support.above());
         return false;
      } else {
         return true;
      }
   }

   private void retireSyncedPlacement(BlockPos cell, List<Entity> occupants) {
      if (!this.recentPlacedCells.isEmpty()) {
         for (Entity occupant : occupants) {
            if (occupant instanceof EndCrystal) {
               this.recentPlacedCells.remove(cell);
               return;
            }
         }
      }
   }

   private void notePlacedCell(BlockPos cell) {
      int tick = RiptideSharedState.get().getClientTickCounter();
      this.recentPlacedCells.put(cell, tick + 3);
      this.recentPlacedCells.entrySet().removeIf(entry -> tick - entry.getValue() >= 0);

      while (this.recentPlacedCells.size() > 4) {
         this.recentPlacedCells.remove(this.recentPlacedCells.keySet().iterator().next());
      }
   }

   private boolean awaitingSupportSync() {
      int tick = RiptideSharedState.get().getClientTickCounter();
      int radius = Mth.clamp(Mth.ceil(MC.player.blockInteractionRange()), 0, 8);
      BlockPos origin = MC.player.blockPosition();
      if (this.lastBreakCrystalId >= 0 && tick - this.lastBreakTick >= 0 && tick - this.lastBreakTick < 3) {
         Entity dying = MC.level.getEntity(this.lastBreakCrystalId);
         if (dying != null && inCandidateSphere(origin, BlockPos.containing(dying.position()).below(), radius)) {
            return true;
         }
      }

      if (!this.recentPlacedCells.isEmpty()) {
         for (Entry<BlockPos, Integer> entry : this.recentPlacedCells.entrySet()) {
            if (tick - entry.getValue() < 0 && inCandidateSphere(origin, entry.getKey().below(), radius)) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean inCandidateSphere(BlockPos origin, BlockPos support, int radius) {
      int dx = support.getX() - origin.getX();
      int dy = support.getY() - origin.getY();
      int dz = support.getZ() - origin.getZ();
      return dx * dx + dy * dy + dz * dz <= radius * radius;
   }

   private BlockHitResult placeLands(CrystalAuraModule.PlacePlan plan, RiptideRotationUtil.Rotation wire) {
      if (plan.hand() == null) {
         return null;
      } else if (plan.hand() == InteractionHand.OFF_HAND && RiptideCombatClicker.mainHandWouldPreempt()) {
         return null;
      } else {
         BlockPos support = plan.support();
         if (!this.supportsCrystal(support)) {
            return null;
         } else {
            AABB nextBox = MC.player.getBoundingBox().move(MC.player.getDeltaMovement());
            if (this.cellIsFree(support, nextBox) && !this.blockedByUnsyncedPlacement(support)) {
               return !isCrystal(MC.player.getItemInHand(plan.hand()))
                  ? null
                  : RiptideFaceScan.confirm(plan.candidate(), wire, MC.player.getEyePosition(), plan.range(), plan.request());
            } else {
               return null;
            }
         }
      }
   }

   private boolean executePlace(CrystalAuraModule.PlacePlan plan, BlockHitResult hit) {
      if (hit == null) {
         return false;
      } else {
         InteractionHand hand = plan.hand();
         if (ModuleRegistry.shouldCancelUseExcept(hit, hand, "crystal-aura")) {
            return false;
         } else if (!RiptideCombatClicker.queueUse(hit, hand)) {
            return false;
         } else if (!RiptidePlacementTick.claim("crystal-aura")) {
            RiptideCombatClicker.cancel();
            return false;
         } else {
            this.reservedCell = plan.support().above();
            this.notePlacedCell(this.reservedCell);
            this.bookAction(false);
            return true;
         }
      }
   }

   @Override
   public boolean shouldCancelUse(HitResult hitResult, InteractionHand hand) {
      return this.lastActionTick == RiptideSharedState.get().getClientTickCounter();
   }

   @Override
   public boolean shouldCancelAttack(HitResult hitResult) {
      return hitResult instanceof EntityHitResult
         && "crystal-aura".equals(RiptideKillAuraRotation.currentOwner())
         && RiptideKillAuraRotation.hasCurrentRotation();
   }

   private CrystalAuraModule.ObsidianPlan planObsidian(LivingEntity enemy, RiptideFaceScan.Budget budget, double rivalDamage) {
      LocalPlayer player = MC.player;
      double range = player.blockInteractionRange();
      if (range <= 0.0) {
         return null;
      } else {
         boolean offhand = isObsidian(player.getOffhandItem())
            && !RiptideHandArbiter.offhandClaimedByOther("crystal-aura")
            && !RiptideCombatClicker.mainHandWouldPreempt();
         int slot = offhand ? -1 : this.findObsidianHotbarSlot();
         if (!offhand && slot < 0) {
            return null;
         } else {
            InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack material = offhand ? player.getOffhandItem() : player.getInventory().getItem(slot);
            Vec3 eye = player.getEyePosition();
            BlockPos origin = player.blockPosition();
            int[] offsets = sphere(Mth.ceil(range));
            double floor = this.decimal("min-target-damage");
            double rangeSq = range * range;
            AABB nextBox = player.getBoundingBox().move(player.getDeltaMovement());
            BlockState obsidianState = Blocks.OBSIDIAN.defaultBlockState();
            List<CrystalAuraModule.Candidate> candidates = new ArrayList<>();
            MutableBlockPos cursor = new MutableBlockPos();

            for (int i = 0; i + 2 < offsets.length; i += 3) {
               cursor.set(origin.getX() + offsets[i], origin.getY() + offsets[i + 1], origin.getZ() + offsets[i + 2]);
               Vec3 source = new Vec3(cursor.getX() + 0.5, cursor.getY() + 1.0, cursor.getZ() + 0.5);
               if (!this.bool("only-above") || !(source.y < enemy.getY())) {
                  if (this.supportsCrystal(cursor)) {
                     double supportBound = RiptideExplosionDamage.maxDamageTo(enemy, source, 6.0F, this.options);
                     if (supportBound >= floor && this.cellIsFree(cursor, nextBox) && this.supportVisible(eye, cursor, source)) {
                        RiptideExplosionDamage.Ranking supportRank = RiptideExplosionDamage.cachedRank(enemy, source, 6.0F, this.options);
                        if (this.damagePasses(enemy, supportRank)) {
                           rivalDamage = Math.max(rivalDamage, supportRank.targetDamage());
                        }
                     }
                  } else if (MC.level.getBlockState(cursor).canBeReplaced()
                     && !AnchorAuraModule.reservesCycleCell(cursor)
                     && MC.level.isUnobstructed(obsidianState, cursor, CollisionContext.empty())) {
                     double distanceSq = eye.distanceToSqr(source);
                     if (!(distanceSq > rangeSq)) {
                        double bound = RiptideExplosionDamage.maxDamageTo(enemy, source, 6.0F, this.options);
                        if (!(bound < floor) && this.cellIsFree(cursor, nextBox) && !this.blockedByUnsyncedPlacement(cursor)) {
                           candidates.add(new CrystalAuraModule.Candidate(cursor.immutable(), source, bound, distanceSq));
                        }
                     }
                  }
               }
            }

            if (candidates.isEmpty()) {
               return null;
            } else {
               candidates.sort(Comparator.comparingDouble(CrystalAuraModule.Candidate::bound).reversed());
               List<CrystalAuraModule.Scored> accepted = new ArrayList<>();
               double bestDamage = -1.0;
               int evaluated = 0;

               for (CrystalAuraModule.Candidate candidate : candidates) {
                  if (accepted.size() >= 4 && candidate.bound() <= bestDamage || evaluated >= 32) {
                     break;
                  }

                  evaluated++;
                  RiptideExplosionDamage.Ranking rank = RiptideExplosionDamage.cachedRank(enemy, candidate.source(), 6.0F, this.options);
                  if (this.damagePasses(enemy, rank)) {
                     accepted.add(new CrystalAuraModule.Scored(candidate.pos(), rank, candidate.distanceSq()));
                     bestDamage = Math.max(bestDamage, rank.targetDamage());
                  }
               }

               if (accepted.isEmpty()) {
                  return null;
               } else {
                  accepted.sort((first, second) -> {
                     int byBucket = Double.compare(damageBucket(second.rank().targetDamage()), damageBucket(first.rank().targetDamage()));
                     if (byBucket != 0) {
                        return byBucket;
                     } else {
                        int bySelf = Double.compare(first.rank().selfDamage(), second.rank().selfDamage());
                        return bySelf != 0 ? bySelf : Double.compare(second.distanceSq(), first.distanceSq());
                     }
                  });
                  double builderBest = accepted.get(0).rank().targetDamage();
                  if (rivalDamage >= 0.0 && !worthUpgrade(rivalDamage, builderBest)) {
                     return null;
                  } else {
                     RiptideRotationUtil.Rotation wire = this.aimReference();
                     RiptideFaceScan.Refusal[] refusal = new RiptideFaceScan.Refusal[1];
                     int limit = Math.min(4, accepted.size());

                     for (int ix = 0; ix < limit; ix++) {
                        BlockPos cell = accepted.get(ix).pos();
                        RiptideFaceScan.Request request = this.obsidianRequest(cell, material, hand, range, eye, wire).budget(budget);
                        RiptideFaceScan.Candidate candidate = RiptideFaceScan.best(request, refusal);
                        if (candidate != null) {
                           InteractionHand resolved = offhand ? InteractionHand.OFF_HAND : this.ensureMainHandForObsidian(slot);
                           return new CrystalAuraModule.ObsidianPlan(cell, resolved, range, candidate, accepted.get(ix).rank().targetDamage(), slot);
                        }

                        if (refusal[0] == RiptideFaceScan.Refusal.NO_BUDGET) {
                           return null;
                        }

                        refusal[0] = null;
                     }

                     return null;
                  }
               }
            }
         }
      }
   }

   private static boolean worthUpgrade(double rivalDamage, double builderDamage) {
      return builderDamage >= rivalDamage + 5.0 && builderDamage >= rivalDamage * 2.0;
   }

   private RiptideFaceScan.Request obsidianRequest(
      BlockPos cell, ItemStack stack, InteractionHand hand, double range, Vec3 eye, RiptideRotationUtil.Rotation wire
   ) {
      return new RiptideFaceScan.Request(cell, eye, range, RiptideFaceScan.blockItem(stack, MC.player, hand))
         .from(wire)
         .pitchLimit(RiptideFaceScan.goalPitchLimit())
         .leadEye(eye.add(MC.player.getDeltaMovement()))
         .sneaking(MC.player.isSecondaryUseActive())
         .sneakAllowed(false);
   }

   private BlockHitResult obsidianLands(CrystalAuraModule.ObsidianPlan plan, RiptideRotationUtil.Rotation wire) {
      if (plan.hand() == null) {
         return null;
      } else if (plan.hand() == InteractionHand.OFF_HAND && RiptideCombatClicker.mainHandWouldPreempt()) {
         return null;
      } else {
         BlockPos cell = plan.cell();
         if (!MC.level.getBlockState(cell).canBeReplaced()) {
            return null;
         } else if (!MC.level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), cell, CollisionContext.empty())) {
            return null;
         } else {
            ItemStack held = MC.player.getItemInHand(plan.hand());
            if (!isObsidian(held)) {
               return null;
            } else {
               Vec3 eye = MC.player.getEyePosition();
               return RiptideFaceScan.confirm(
                  plan.candidate(), wire, eye, plan.range(), this.obsidianRequest(cell, held, plan.hand(), plan.range(), eye, this.aimReference())
               );
            }
         }
      }
   }

   private boolean executeObsidian(CrystalAuraModule.ObsidianPlan plan, BlockHitResult hit) {
      if (hit == null) {
         return false;
      } else {
         InteractionHand hand = plan.hand();
         if (ModuleRegistry.shouldCancelUseExcept(hit, hand, "crystal-aura")) {
            return false;
         } else if (!RiptideCombatClicker.queueUse(hit, hand)) {
            return false;
         } else if (!RiptidePlacementTick.claim("crystal-aura")) {
            RiptideCombatClicker.cancel();
            return false;
         } else {
            this.reservedCell = plan.cell().above();
            this.ownObsidianCells.put(plan.cell().immutable(), RiptideSharedState.get().getClientTickCounter() + 40);

            while (this.ownObsidianCells.size() > 16) {
               this.ownObsidianCells.remove(this.ownObsidianCells.keySet().iterator().next());
            }

            this.bookAction(false);
            return true;
         }
      }
   }

   private boolean ownPlacedCell(BlockPos cell) {
      Integer until = this.ownObsidianCells.get(cell);
      if (until == null) {
         return false;
      } else if (RiptideSharedState.get().getClientTickCounter() - until >= 0) {
         this.ownObsidianCells.remove(cell);
         return false;
      } else {
         return true;
      }
   }

   private static boolean isObsidian(ItemStack stack) {
      return !stack.isEmpty() && stack.is(Items.OBSIDIAN);
   }

   private int findObsidianHotbarSlot() {
      LocalPlayer player = MC.player;
      int selected = player.getInventory().getSelectedSlot();
      int best = -1;
      int bestSteps = Integer.MAX_VALUE;

      for (int slot = 0; slot < 9; slot++) {
         if (isObsidian(player.getInventory().getItem(slot)) && !RiptideHandArbiter.slotReserved(slot, "crystal-aura")) {
            int steps = Math.abs(slot - selected);
            steps = Math.min(steps, 9 - steps);
            if (steps < bestSteps) {
               bestSteps = steps;
               best = slot;
            }
         }
      }

      return best;
   }

   private InteractionHand ensureMainHandForObsidian(int slot) {
      return this.ensureMainHandForObsidian(slot, false);
   }

   private InteractionHand ensureMainHandForObsidian(int slot, boolean defensive) {
      LocalPlayer player = MC.player;
      if (player.getInventory().getSelectedSlot() == slot) {
         this.switchBackTicks = this.integer("switch-back-delay");
         return InteractionHand.MAIN_HAND;
      } else {
         int held = player.getInventory().getSelectedSlot();
         if (this.changeHotbarSlot(slot, defensive)) {
            if (this.previousSlot < 0) {
               this.previousSlot = held;
            }

            this.switchedToSlot = slot;
         }

         this.switchBackTicks = this.integer("switch-back-delay");
         return null;
      }
   }

   private CrystalAuraModule.ObsidianPlan planDefense(RiptideFaceScan.Budget budget) {
      if (!this.bool("defend")) {
         return null;
      } else {
         LocalPlayer player = MC.player;
         if (!SurroundModule.ownsSilentRotation() && !BedDefenderModule.ownsSilentRotation()) {
            if (this.primePosition()) {
               return null;
            } else {
               int tick = RiptideSharedState.get().getClientTickCounter();
               int cooldownAge = tick - this.lastDefendTick;
               if (cooldownAge >= 0 && cooldownAge < 2) {
                  return null;
               } else {
                  int windowAge = tick - this.defendWindowStart;
                  if (windowAge < 0 || windowAge >= 20) {
                     this.defendWindowStart = tick;
                     this.defendWindowCount = 0;
                  }

                  if (this.defendWindowCount >= 3) {
                     return null;
                  } else {
                     LivingEntity enemy = this.defendThreat();
                     if (enemy == null) {
                        return null;
                     } else {
                        double range = player.blockInteractionRange();
                        if (range <= 0.0) {
                           return null;
                        } else {
                           List<CrystalAuraModule.DefendThreat> threats = this.defendThreatSources(player);
                           if (threats.isEmpty()) {
                              return null;
                           } else {
                              boolean offhand = isObsidian(player.getOffhandItem())
                                 && !RiptideHandArbiter.offhandClaimedByOther("crystal-aura")
                                 && !RiptideCombatClicker.mainHandWouldPreempt();
                              int slot = offhand ? -1 : this.findObsidianHotbarSlot();
                              if (!offhand && slot < 0) {
                                 return null;
                              } else {
                                 InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                                 ItemStack material = offhand ? player.getOffhandItem() : player.getInventory().getItem(slot);
                                 Vec3 eye = player.getEyePosition();
                                 double rangeSq = range * range;
                                 AABB playerBox = player.getBoundingBox();
                                 Vec3 delta = player.getDeltaMovement();
                                 BlockPos feet = player.blockPosition();
                                 AABB nextBox = playerBox.move(delta);
                                 BlockState obsidianState = Blocks.OBSIDIAN.defaultBlockState();
                                 double enemyDx = enemy.getX() - player.getX();
                                 double enemyDz = enemy.getZ() - player.getZ();
                                 double worst = threats.get(0).selfDamage();
                                 boolean lowRing = player.getY() - enemy.getY() >= 1.5;
                                 boolean directBelow = enemyDx * enemyDx + enemyDz * enemyDz <= 0.25;
                                 List<CrystalAuraModule.DefendCell> cells = new ArrayList<>();

                                 for (int yOff = lowRing ? -1 : 0; yOff <= 0; yOff++) {
                                    for (int dx = -1; dx <= 1; dx++) {
                                       for (int dz = -1; dz <= 1; dz++) {
                                          if ((dx != 0 || dz != 0) && (directBelow || !(dx * enemyDx + dz * enemyDz <= 0.0))) {
                                             BlockPos cell = feet.offset(dx, yOff, dz);
                                             if (MC.level.getBlockState(cell).canBeReplaced() && !AnchorAuraModule.reservesCycleCell(cell)) {
                                                AABB cellBox = new AABB(cell);
                                                if (!cellBox.intersects(playerBox)
                                                   && !cellBox.intersects(nextBox)
                                                   && !cellBox.intersects(playerBox.move(delta.scale(-1.0)))
                                                   && MC.level.isUnobstructed(obsidianState, cell, CollisionContext.empty())) {
                                                   Vec3 centre = new Vec3(cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5);
                                                   if (!(eye.distanceToSqr(centre) > rangeSq)) {
                                                      double after = 0.0;

                                                      for (CrystalAuraModule.DefendThreat threat : threats) {
                                                         after = Math.max(
                                                            after,
                                                            RiptideExplosionDamage.cachedDamageTo(player, threat.source(), 6.0F, this.options.withInclude(cell))
                                                         );
                                                      }

                                                      double reduction = worst - after;
                                                      if (reduction >= 2.0) {
                                                         cells.add(new CrystalAuraModule.DefendCell(cell, reduction));
                                                      }
                                                   }
                                                }
                                             }
                                          }
                                       }
                                    }
                                 }

                                 if (cells.isEmpty()) {
                                    return null;
                                 } else {
                                    cells.sort(Comparator.comparingDouble(CrystalAuraModule.DefendCell::reduction).reversed());
                                    RiptideFaceScan.Refusal[] refusal = new RiptideFaceScan.Refusal[1];
                                    int limit = Math.min(4, cells.size());

                                    for (int i = 0; i < limit; i++) {
                                       CrystalAuraModule.DefendCell cell = cells.get(i);
                                       RiptideFaceScan.Request request = this.obsidianRequest(cell.pos(), material, hand, range, eye, this.aimReference())
                                          .budget(budget);
                                       RiptideFaceScan.Candidate candidate = RiptideFaceScan.best(request, refusal);
                                       if (candidate != null) {
                                          InteractionHand resolved = offhand ? InteractionHand.OFF_HAND : this.ensureMainHandForObsidian(slot, true);
                                          return new CrystalAuraModule.ObsidianPlan(cell.pos(), resolved, range, candidate, worst, slot);
                                       }

                                       if (refusal[0] == RiptideFaceScan.Refusal.NO_BUDGET) {
                                          return null;
                                       }

                                       refusal[0] = null;
                                    }

                                    return null;
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         } else {
            return null;
         }
      }
   }

   private LivingEntity defendThreat() {
      LocalPlayer player = MC.player;
      double range = this.decimal("target-range");
      AABB search = player.getBoundingBox().inflate(range);
      List<Player> found = MC.level
         .getEntitiesOfClass(
            Player.class,
            search,
            entityx -> entityx != player
               && entityx.isAlive()
               && !entityx.isSpectator()
               && !RiptideAntiBot.suppress(entityx)
               && !TeamsModule.combatExcluded(entityx, "killaura")
               && entityx.getY() <= player.getY() - 0.75
         );
      Vec3 eye = player.getEyePosition();
      double rangeSq = range * range;
      Player best = null;
      double bestDistanceSq = Double.MAX_VALUE;

      for (Player entity : found) {
         double distanceSq = boxDistanceSqr(entity.getBoundingBox(), eye);
         if (!(distanceSq > rangeSq) && distanceSq < bestDistanceSq) {
            bestDistanceSq = distanceSq;
            best = entity;
         }
      }

      return best;
   }

   private List<CrystalAuraModule.DefendThreat> defendThreatSources(LocalPlayer player) {
      double maxSelf = this.decimal("max-self-damage");
      List<CrystalAuraModule.DefendThreat> threats = new ArrayList<>();
      AABB near = player.getBoundingBox().inflate(5.0);

      for (EndCrystal crystal : MC.level.getEntitiesOfClass(EndCrystal.class, near, Entity::isAlive)) {
         if (!this.ownPlacedCell(BlockPos.containing(crystal.position()).below())) {
            double self = RiptideExplosionDamage.cachedDamageTo(player, crystal.position(), 6.0F, this.options);
            if (self > maxSelf) {
               threats.add(new CrystalAuraModule.DefendThreat(crystal.position(), self));
            }
         }
      }

      BlockPos origin = player.blockPosition();
      int[] offsets = sphere(Mth.ceil(5.0));
      AABB nextBox = player.getBoundingBox().move(player.getDeltaMovement());
      MutableBlockPos cursor = new MutableBlockPos();

      for (int i = 0; i + 2 < offsets.length; i += 3) {
         cursor.set(origin.getX() + offsets[i], origin.getY() + offsets[i + 1], origin.getZ() + offsets[i + 2]);
         if (this.supportsCrystal(cursor) && MC.level.isEmptyBlock(cursor.above()) && !this.ownPlacedCell(cursor)) {
            Vec3 source = new Vec3(cursor.getX() + 0.5, cursor.getY() + 1.0, cursor.getZ() + 0.5);
            if (!(RiptideExplosionDamage.maxSelfDamage(source, 6.0F, this.options) <= maxSelf) && this.cellIsFree(cursor, nextBox)) {
               double self = RiptideExplosionDamage.cachedDamageTo(player, source, 6.0F, this.options);
               if (self > maxSelf) {
                  threats.add(new CrystalAuraModule.DefendThreat(source, self));
               }
            }
         }
      }

      threats.sort(Comparator.comparingDouble(CrystalAuraModule.DefendThreat::selfDamage).reversed());
      return (List<CrystalAuraModule.DefendThreat>)(threats.size() <= 4 ? threats : new ArrayList<>(threats.subList(0, 4)));
   }

   private boolean executeDefend(CrystalAuraModule.ObsidianPlan plan, BlockHitResult hit) {
      if (!this.executeObsidian(plan, hit)) {
         return false;
      } else {
         this.lastDefendTick = RiptideSharedState.get().getClientTickCounter();
         this.defendWindowCount++;
         return true;
      }
   }

   private void commitObsidian(LivingEntity enemy, CrystalAuraModule.ObsidianPlan plan) {
      this.committedObsidianCell = plan.cell();
      this.committedObsidianSlot = plan.slot();
      this.committedObsidianTarget = enemy.getId();
      this.committedObsidianDamage = plan.targetDamage();
      this.committedObsidianEnemyPos = enemy.position();
      this.committedObsidianUntil = RiptideSharedState.get().getClientTickCounter() + 12;
      this.committedPlaceSupport = null;
   }

   private void commitPlace(LivingEntity enemy, CrystalAuraModule.PlacePlan plan) {
      this.committedPlaceSupport = plan.support();
      this.committedPlaceTarget = enemy.getId();
      this.committedPlaceUntil = RiptideSharedState.get().getClientTickCounter() + 6;
      this.committedObsidianCell = null;
   }

   private void clearPlanCommitments() {
      this.committedObsidianCell = null;
      this.committedPlaceSupport = null;
   }

   private boolean revalidateObsidianCommit(LivingEntity enemy) {
      if (this.committedObsidianCell == null) {
         return false;
      } else if (RiptideSharedState.get().getClientTickCounter() > this.committedObsidianUntil
         || enemy.getId() != this.committedObsidianTarget
         || !this.bool("place")
         || !this.bool("obsidian")
         || !this.hasCrystalAvailable()
         || !MC.level.getBlockState(this.committedObsidianCell).canBeReplaced()
         || !MC.level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), this.committedObsidianCell, CollisionContext.empty())
         || !this.cellIsFree(this.committedObsidianCell, MC.player.getBoundingBox().move(MC.player.getDeltaMovement()))
         || !this.committedObsidianMaterial()) {
         this.committedObsidianCell = null;
         return false;
      } else if (this.committedObsidianEnemyPos != null && enemy.position().distanceToSqr(this.committedObsidianEnemyPos) > 2.25) {
         this.committedObsidianCell = null;
         return false;
      } else {
         BlockPos playerPos = MC.player.blockPosition();
         BlockPos enemyPos = enemy.blockPosition();
         if (!playerPos.equals(this.supportScanPlayerPos) || !enemyPos.equals(this.supportScanEnemyPos)) {
            this.supportScanPlayerPos = playerPos.immutable();
            this.supportScanEnemyPos = enemyPos.immutable();
            List<BlockPos> supports = this.rankSupports(enemy, MC.player.blockInteractionRange());
            if (!supports.isEmpty()) {
               this.builderBanTarget = enemy.getId();
               this.builderBanSupport = supports.get(0);
               this.builderBanStallTicks = 0;
               this.committedObsidianCell = null;
               return false;
            }
         }

         return true;
      }
   }

   private boolean builderBanned(LivingEntity enemy) {
      if (this.builderBanSupport != null && enemy.getId() == this.builderBanTarget) {
         if (this.supportsCrystal(this.builderBanSupport)
            && this.cellIsFree(this.builderBanSupport, MC.player.getBoundingBox().move(MC.player.getDeltaMovement()))
            && this.damagePasses(
               enemy,
               RiptideExplosionDamage.cachedRank(
                  enemy,
                  new Vec3(this.builderBanSupport.getX() + 0.5, this.builderBanSupport.getY() + 1.0, this.builderBanSupport.getZ() + 0.5),
                  6.0F,
                  this.options
               )
            )) {
            return true;
         } else {
            this.builderBanSupport = null;
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean committedObsidianMaterial() {
      LocalPlayer player = MC.player;
      return this.committedObsidianSlot < 0
         ? isObsidian(player.getOffhandItem()) && !RiptideHandArbiter.offhandClaimedByOther("crystal-aura")
         : !RiptideHandArbiter.slotReserved(this.committedObsidianSlot, "crystal-aura")
            && isObsidian(player.getInventory().getItem(this.committedObsidianSlot));
   }

   private CrystalAuraModule.ObsidianPlan aimCommittedObsidian(RiptideFaceScan.Budget budget) {
      LocalPlayer player = MC.player;
      double range = player.blockInteractionRange();
      if (range <= 0.0) {
         return null;
      } else {
         Vec3 eye = player.getEyePosition();
         boolean offhand = this.committedObsidianSlot < 0;
         InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
         ItemStack material = offhand ? player.getOffhandItem() : player.getInventory().getItem(this.committedObsidianSlot);
         RiptideFaceScan.Request request = this.obsidianRequest(this.committedObsidianCell, material, hand, range, eye, this.aimReference()).budget(budget);
         RiptideFaceScan.Candidate candidate = RiptideFaceScan.best(request, new RiptideFaceScan.Refusal[1]);
         if (candidate == null) {
            return null;
         } else {
            InteractionHand resolved = offhand ? InteractionHand.OFF_HAND : this.ensureMainHandForObsidian(this.committedObsidianSlot);
            return new CrystalAuraModule.ObsidianPlan(
               this.committedObsidianCell, resolved, range, candidate, this.committedObsidianDamage, this.committedObsidianSlot
            );
         }
      }
   }

   private boolean revalidatePlaceCommit(LivingEntity enemy) {
      if (this.committedPlaceSupport == null) {
         return false;
      } else if (RiptideSharedState.get().getClientTickCounter() <= this.committedPlaceUntil
         && enemy.getId() == this.committedPlaceTarget
         && this.bool("place")
         && this.hasCrystalAvailable()
         && this.supportsCrystal(this.committedPlaceSupport)
         && this.cellIsFree(this.committedPlaceSupport, MC.player.getBoundingBox().move(MC.player.getDeltaMovement()))) {
         RiptideExplosionDamage.Ranking rank = RiptideExplosionDamage.cachedRank(
            enemy,
            new Vec3(this.committedPlaceSupport.getX() + 0.5, this.committedPlaceSupport.getY() + 1.0, this.committedPlaceSupport.getZ() + 0.5),
            6.0F,
            this.options
         );
         if (!RiptideExplosionDamage.killsSelf(rank.selfDamage()) && !(rank.selfDamage() > this.decimal("max-self-damage"))) {
            return true;
         } else {
            this.committedPlaceSupport = null;
            return false;
         }
      } else {
         this.committedPlaceSupport = null;
         return false;
      }
   }

   private CrystalAuraModule.PlacePlan aimCommittedPlace(LivingEntity enemy, RiptideFaceScan.Budget budget) {
      LocalPlayer player = MC.player;
      double range = player.blockInteractionRange();
      if (range <= 0.0) {
         return null;
      } else {
         Vec3 eye = player.getEyePosition();
         RiptideRotationUtil.Rotation wire = this.aimReference();
         BlockPos support = this.committedPlaceSupport;
         BlockPos cell = support.above();
         BlockState state = MC.level.getBlockState(support);
         RiptideFaceScan.Request request = new RiptideFaceScan.Request(cell, eye, range, RiptideFaceScan.onSupport(support, ALL_FACES))
            .from(wire)
            .leadEye(eye.add(player.getDeltaMovement()))
            .budget(budget);
         List<RiptideFaceScan.Option> scan = new ArrayList<>();
         this.supportOptions(support, state, cell, eye, wire, scan);
         RiptideFaceScan.Refusal[] refusal = new RiptideFaceScan.Refusal[1];

         for (RiptideFaceScan.Option option : scan) {
            RiptideFaceScan.Aim aim = RiptideFaceScan.solve(option, request, refusal);
            if (aim != null) {
               RiptideFaceScan.Candidate candidate = RiptideFaceScan.probe(option, aim, request, refusal);
               if (candidate != null) {
                  InteractionHand hand = this.ensureCrystalHand();
                  Vec3 source = new Vec3(support.getX() + 0.5, support.getY() + 1.0, support.getZ() + 0.5);
                  double damage = RiptideExplosionDamage.cachedRank(enemy, source, 6.0F, this.options).targetDamage();
                  return new CrystalAuraModule.PlacePlan(support, hand, range, candidate, request, damage);
               }

               if (refusal[0] == RiptideFaceScan.Refusal.NO_BUDGET) {
                  return null;
               }
            }
         }

         return null;
      }
   }

   private static boolean isCrystal(ItemStack stack) {
      return !stack.isEmpty() && stack.getItem() == Items.END_CRYSTAL;
   }

   private boolean hasCrystalAvailable() {
      LocalPlayer player = MC.player;
      return isCrystal(player.getOffhandItem()) && !RiptideHandArbiter.offhandClaimedByOther("crystal-aura") ? true : this.findCrystalHotbarSlot() >= 0;
   }

   private InteractionHand ensureCrystalHand() {
      LocalPlayer player = MC.player;
      if (isCrystal(player.getOffhandItem()) && !RiptideHandArbiter.offhandClaimedByOther("crystal-aura") && !RiptideCombatClicker.mainHandWouldPreempt()) {
         return InteractionHand.OFF_HAND;
      } else if (isCrystal(player.getMainHandItem())) {
         this.switchBackTicks = this.integer("switch-back-delay");
         return InteractionHand.MAIN_HAND;
      } else {
         int slot = this.findCrystalHotbarSlot();
         if (slot < 0) {
            return null;
         } else {
            int held = player.getInventory().getSelectedSlot();
            if (this.changeHotbarSlot(slot)) {
               if (this.previousSlot < 0) {
                  this.previousSlot = held;
               }

               this.switchedToSlot = slot;
            }

            this.switchBackTicks = this.integer("switch-back-delay");
            return null;
         }
      }
   }

   private boolean changeHotbarSlot(int slot) {
      return this.changeHotbarSlot(slot, false);
   }

   private boolean changeHotbarSlot(int slot, boolean defensive) {
      if (!BedDefenderModule.ownsSilentRotation()
         && !SurroundModule.ownsSilentRotation()
         && (this.primePosition() || (defensive ? !AnchorAuraModule.worksThisTick() : !AnchorAuraModule.reservesCombatTick()))) {
         int tick = RiptideSharedState.get().getClientTickCounter();
         if (tick == this.hotbarChangeTick) {
            return false;
         } else if (!RiptideHandArbiter.beginHandPacketGroup("crystal-aura")) {
            return false;
         } else {
            try {
               RiptideInventoryHelper.selectHotbarSlot(MC, slot);
            } finally {
               RiptideHandArbiter.endHandPacketGroup("crystal-aura");
            }

            this.hotbarChangeTick = tick;
            return true;
         }
      } else {
         return false;
      }
   }

   private int findCrystalHotbarSlot() {
      LocalPlayer player = MC.player;
      int selected = player.getInventory().getSelectedSlot();
      int best = -1;
      int bestSteps = Integer.MAX_VALUE;

      for (int slot = 0; slot < 9; slot++) {
         if (isCrystal(player.getInventory().getItem(slot)) && !RiptideHandArbiter.slotReserved(slot, "crystal-aura")) {
            int steps = Math.abs(slot - selected);
            steps = Math.min(steps, 9 - steps);
            if (steps < bestSteps) {
               bestSteps = steps;
               best = slot;
            }
         }
      }

      return best;
   }

   private void tickSwitchBack() {
      if (this.previousSlot >= 0) {
         if (MC.gui.screen() == null && MC.gui.overlay() == null) {
            if (this.switchedToSlot >= 0 && MC.player.getInventory().getSelectedSlot() != this.switchedToSlot) {
               int selected = MC.player.getInventory().getSelectedSlot();
               if (!AnchorAuraModule.holdsBorrowedSlot(selected) && !KillAuraModule.holdsBorrowedSlot(selected)) {
                  this.previousSlot = -1;
                  this.switchedToSlot = -1;
                  this.switchBackTicks = 0;
               }
            } else if (this.switchBackTicks > 0) {
               this.switchBackTicks--;
            } else if (!RiptideHandArbiter.slotReserved(this.previousSlot, "crystal-aura")) {
               if (this.changeHotbarSlot(this.previousSlot)) {
                  this.previousSlot = -1;
                  this.switchedToSlot = -1;
               }
            }
         }
      }
   }

   private boolean damagePasses(LivingEntity enemy, RiptideExplosionDamage.Ranking rank) {
      if (RiptideExplosionDamage.killsSelf(rank.selfDamage())) {
         return false;
      } else if (rank.targetDamage() < this.decimal("min-target-damage")) {
         return false;
      } else {
         return rank.selfDamage() > this.decimal("max-self-damage") ? false : !this.bool("efficient") || rank.isEfficient();
      }
   }

   private boolean betterRank(RiptideExplosionDamage.Ranking candidate, RiptideExplosionDamage.Ranking incumbent) {
      int byTarget = Double.compare(candidate.targetDamage(), incumbent.targetDamage());
      return byTarget != 0 ? byTarget > 0 : candidate.selfDamage() < incumbent.selfDamage();
   }

   private boolean supportVisible(Vec3 eye, BlockPos support, Vec3 source) {
      HitResult hit = MC.level.clip(new ClipContext(eye, source, Block.COLLIDER, Fluid.NONE, MC.player));
      return hit.getType() == Type.MISS ? true : hit instanceof BlockHitResult block && block.getBlockPos().equals(support);
   }

   private boolean blockedByTerrain(Vec3 eye, Vec3 point) {
      HitResult hit = MC.level.clip(new ClipContext(eye, point, Block.COLLIDER, Fluid.NONE, MC.player));
      return hit.getType() != Type.MISS;
   }

   private static double boxDistanceSqr(AABB box, Vec3 point) {
      double dx = Math.max(Math.max(box.minX - point.x, point.x - box.maxX), 0.0);
      double dy = Math.max(Math.max(box.minY - point.y, point.y - box.maxY), 0.0);
      double dz = Math.max(Math.max(box.minZ - point.z, point.z - box.maxZ), 0.0);
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
                  int distance = x * x + y * y + z * z;
                  if (distance <= limit) {
                     cells.add(new int[]{x, y, z, distance});
                  }
               }
            }
         }

         cells.sort(Comparator.comparingInt(cellx -> cellx[3]));
         int[] flat = new int[cells.size() * 3];

         for (int i = 0; i < cells.size(); i++) {
            int[] cell = cells.get(i);
            flat[i * 3] = cell[0];
            flat[i * 3 + 1] = cell[1];
            flat[i * 3 + 2] = cell[2];
         }

         sphereCache = flat;
         sphereRadius = clamped;
         return flat;
      }
   }

   private void pushCrystalView() {
      if (!this.bool("crystal-view")) {
         viewOn = false;
      } else {
         viewSize = (float)this.decimal("view-size");
         viewY = (float)this.decimal("view-y");
         viewSpin = (float)this.decimal("view-spin");
         viewBounce = (float)this.decimal("view-bounce");
         viewOn = true;
      }
   }

   public static boolean crystalViewActive() {
      return viewOn;
   }

   public static float crystalViewSize() {
      return viewSize;
   }

   public static float crystalViewYTranslate() {
      return viewY;
   }

   public static float crystalViewSpin() {
      return viewSpin;
   }

   public static float crystalViewBounce() {
      return viewBounce;
   }

   private record Candidate(BlockPos pos, Vec3 source, double bound, double distanceSq) {
   }

   private record DefendCell(BlockPos pos, double reduction) {
   }

   private record DefendThreat(Vec3 source, double selfDamage) {
   }

   private record DestroyPlan(EndCrystal crystal, RiptideRotationUtil.Rotation goal, double range) {
   }

   private record ObsidianPlan(BlockPos cell, InteractionHand hand, double range, RiptideFaceScan.Candidate candidate, double targetDamage, int slot) {
   }

   private record PlacePlan(
      BlockPos support, InteractionHand hand, double range, RiptideFaceScan.Candidate candidate, RiptideFaceScan.Request request, double targetDamage
   ) {
   }

   private record ScanEntry(BlockPos support, RiptideFaceScan.Request request, RiptideFaceScan.Option option) {
   }

   private record Scored(BlockPos pos, RiptideExplosionDamage.Ranking rank, double distanceSq) {
   }

   private record TurnKeyedOption(RiptideFaceScan.Option option, double turn) {
   }
}
