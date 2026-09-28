package riptide.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
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

public final class AnchorAuraModule extends Module implements RiptideSilentAim.Owner {
   public static final String ID = "anchor-aura";
   private static final float POWER = 5.0F;
   private static final int MAX_CHARGES = 4;
   private static final int MAX_AIM_CANDIDATES = 4;
   private static final int MAX_FULL_EVALUATIONS = 32;
   private static final int TARGET_LEAD_TICKS = 6;
   private static final double TARGET_LEAD_MAX = 2.0;
   private static final int RAY_BUDGET = 48;
   private static final int DETONATE_RETRY_TICKS = 10;
   private static final int CHARGE_RETRY_TICKS = 10;
   private static final int BAN_TICKS = 40;
   private static final int ADOPTION_NO_BUDGET_PARKS = 3;
   private static final int CYCLE_TIMEOUT_TICKS = 20;
   private static final int MAX_PLACE_ATTEMPTS = 3;
   private static final int PENDING_COMMIT_TICKS = 25;
   private static final int PENDING_BAN_TICKS = 10;
   private static final int PENDING_COMMIT_STRIKES = 3;
   private static final int FORCE_ENGAGE_TICKS = 3;
   private static final int SHIELD_FUEL_MINIMUM = 2;
   private static final int SHIELD_ROUTE_CELLS = 4;
   private static final int SHIELD_CELLS_PER_ANCHOR = 4;
   private static int[] sphereCache = new int[0];
   private static int sphereRadius = -1;
   private final Random random = new Random();
   private final Map<BlockPos, Integer> banned = new HashMap<>();
   private LivingEntity target;
   private int reservedTick = Integer.MIN_VALUE;
   private int activityTick = Integer.MIN_VALUE;
   private int lastActionTick = Integer.MIN_VALUE;
   private long lastActionNanos = Long.MIN_VALUE;
   private long actionFloorNanos;
   private String cachedEntityListSource;
   private Set<String> cachedEntityIds = Set.of();
   private int previousSlot = -1;
   private int switchedToSlot = -1;
   private int switchBackTicks;
   private int hotbarChangeTick = Integer.MIN_VALUE;
   private BlockPos cycleCell;
   private int cycleDeadline;
   private int abandonDeadline;
   private boolean abandoning;
   private boolean detonateSent;
   private int detonateTick = Integer.MIN_VALUE;
   private boolean chargeSent;
   private int chargeTick = Integer.MIN_VALUE;
   private int placeAttempts;
   private BlockPos placeCursor;
   private BlockPos budgetParkedCell;
   private int budgetParkStreak;
   private BlockPos pendingCell;
   private int pendingSlot = -1;
   private LivingEntity pendingTarget;
   private int pendingExpiry;
   private int pendingStreak;
   private BlockPos pendingShield;
   private boolean pendingShieldObsidian;
   private BlockPos cycleShield;
   private boolean cycleShieldObsidian;
   private int shieldAttempts;
   private int targetSeenTicks;
   private RiptideExplosionDamage.Options damageOptions = RiptideExplosionDamage.Options.DEFAULT;
   private final Map<BlockPos, RiptideExplosionDamage.Options> cellOptions = new HashMap<>();
   private String status = "";

   public AnchorAuraModule() {
      super("anchor-aura", "AnchorAura", ModuleCategory.COMBAT, "Blows targets up with respawn anchors.");
      this.add(new BoolSetting("place", "Place", true).description("Place new respawn anchors.").build());
      this.add(new BoolSetting("charge", "Charge", true).description("Charge anchors with glowstone.").build());
      this.add(new BoolSetting("detonate", "Detonate", true).description("Detonate charged anchors.").build());
      this.add(new DoubleSetting("target-range", "Target Range", 8.0, 1.0, 16.0, 0.5).description("Enemy search radius.").build());
      this.add(new ChoiceSetting("targeting", "Targeting", "Distance", "Distance", "HP", "FOV").description("How the enemy is picked.").build());
      this.add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player").description("Entity types to blast.").build());
      this.add(new IntSetting("hurt-time", "Hurt Time", 10, 0, 10, 1).description("Maximum hurt time.").build());
      this.add(new IntSetting("switch-back-delay", "Switch Back Delay", 20, 1, 100, 1).description("Idle ticks before switching back.").group("Place").build());
      this.add(new BoolSetting("only-above", "Only Above", false).description("Place above target.").group("Place").build());
      this.add(new BoolSetting("force-engage", "Force Engage", true).description("Engage anyway when nothing passes the damage gates.").group("Place").build());
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
      this.add(
         new BoolSetting("glowstone-shield", "Glowstone Shield", true)
            .description("Shield yourself with glowstone when self damage is too high.")
            .group("Damage")
            .build()
      );
      this.add(new IntSetting("step-delay", "Step Delay", 75, 0, 1000, 10).description("Floor between two anchor clicks.").unit("ms").group("Timing").build());
      this.add(new IntSetting("step-jitter", "Step Jitter", 20, 0, 200, 5).description("Random extra delay per click.").unit("ms").group("Timing").build());
   }

   @Override
   public void onEnable() {
      this.resetRuntime();
   }

   @Override
   public void onDisable() {
      this.resetRuntime();
      RiptideKillAuraRotation.beginWindDown("anchor-aura");
   }

   @Override
   public void onGameLeft() {
      this.resetRuntime();
      if ("anchor-aura".equals(RiptideKillAuraRotation.currentOwner())) {
         RiptideKillAuraRotation.reset();
      }
   }

   private void resetRuntime() {
      this.target = null;
      this.reservedTick = Integer.MIN_VALUE;
      this.activityTick = Integer.MIN_VALUE;
      this.lastActionTick = Integer.MIN_VALUE;
      this.lastActionNanos = Long.MIN_VALUE;
      this.actionFloorNanos = 0L;
      this.previousSlot = -1;
      this.switchedToSlot = -1;
      this.switchBackTicks = 0;
      this.hotbarChangeTick = Integer.MIN_VALUE;
      this.cycleCell = null;
      this.abandoning = false;
      this.detonateSent = false;
      this.detonateTick = Integer.MIN_VALUE;
      this.chargeSent = false;
      this.chargeTick = Integer.MIN_VALUE;
      this.placeCursor = null;
      this.budgetParkedCell = null;
      this.budgetParkStreak = 0;
      this.cycleShield = null;
      this.cycleShieldObsidian = false;
      this.shieldAttempts = 0;
      this.targetSeenTicks = 0;
      this.clearPending();
      this.banned.clear();
      this.cellOptions.clear();
      this.status = "";
      RiptideHandArbiter.releaseAll("anchor-aura");
   }

   @Override
   public boolean ticksWhenDisabled() {
      return true;
   }

   @Override
   public boolean hasDisabledTickWork() {
      return "anchor-aura".equals(RiptideKillAuraRotation.currentOwner()) && RiptideKillAuraRotation.hasCurrentRotation();
   }

   @Override
   public void tick() {
      if (!this.isEnabled() && MC != null && MC.player != null) {
         if ("anchor-aura".equals(RiptideKillAuraRotation.currentOwner())) {
            RiptideKillAuraRotation.update("anchor-aura", MC.player);
         }
      }
   }

   @Override
   public String info() {
      if (!this.status.isEmpty()) {
         return this.status;
      } else {
         LivingEntity current = this.target;
         return current == null ? "" : current.getName().getString();
      }
   }

   public static boolean reservesCombatTick() {
      if (ModuleRegistry.get("anchor-aura") instanceof AnchorAuraModule aura && aura.isEnabled()) {
         int age = RiptideSharedState.get().getClientTickCounter() - aura.reservedTick;
         return age >= 0 && age <= 5;
      } else {
         return false;
      }
   }

   public static boolean worksThisTick() {
      return ModuleRegistry.get("anchor-aura") instanceof AnchorAuraModule aura && aura.isEnabled()
         ? aura.activityTick == RiptideSharedState.get().getClientTickCounter()
         : false;
   }

   public static boolean holdsBorrowedSlot(int slot) {
      return ModuleRegistry.get("anchor-aura") instanceof AnchorAuraModule aura && aura.isEnabled()
         ? aura.previousSlot >= 0 && aura.switchedToSlot == slot
         : false;
   }

   public static boolean reservesCycleCell(BlockPos cell) {
      if (cell == null) {
         return false;
      } else {
         return ModuleRegistry.get("anchor-aura") instanceof AnchorAuraModule aura && aura.isEnabled()
            ? cell.equals(aura.pendingCell) || cell.equals(aura.cycleCell) || cell.equals(aura.pendingShield) || cell.equals(aura.cycleShield)
            : false;
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
         && !MC.player.isSecondaryUseActive()
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

   private static boolean higherRungAiming() {
      return BedDefenderModule.ownsSilentRotation()
         || SurroundModule.ownsSilentRotation()
         || CrystalAuraModule.inPrimePosition() && CrystalAuraModule.reservesCombatTick();
   }

   private boolean finishesCommittedWork(AnchorAuraModule.Plan plan) {
      return plan.step() == AnchorAuraModule.Step.DETONATE
         ? true
         : plan.step() == AnchorAuraModule.Step.CHARGE && this.abandoning && plan.cell().equals(this.cycleCell);
   }

   @Override
   public void preMovementTick() {
      if (!RiptideLiteVariant.enabled()) {
         if (MC != null && MC.player != null && MC.level != null) {
            int tick = RiptideSharedState.get().getClientTickCounter();
            this.pruneBans(tick);
            this.tickSwitchBack();
            this.tickDeadline(tick);
            if (!this.canRun()) {
               this.target = null;
               this.clearPending();
               this.standDown();
            } else {
               LivingEntity enemy = this.selectTarget();
               if (enemy == null || enemy != this.target) {
                  this.targetSeenTicks = 0;
               }

               this.target = enemy;
               if (enemy == null) {
                  this.clearPending();
               }

               double range = MC.player.blockInteractionRange();
               RiptideFaceScan.Budget budget = new RiptideFaceScan.Budget(48);
               this.rebuildDamageOptions();
               boolean higherRung = higherRungAiming();
               boolean caPrime = CrystalAuraModule.inPrimePosition();
               boolean caCommitted = caPrime && CrystalAuraModule.hasLiveCommitment();
               if (caPrime) {
                  this.clearPending();
               }

               AnchorAuraModule.Plan plan;
               try (RiptideExplosionDamage.ScanPass pass = RiptideExplosionDamage.beginScan()) {
                  plan = this.planCycle(tick, range, budget);
                  if (plan == null && this.cycleCell == null && enemy != null && !caPrime) {
                     plan = this.planCommittedPlacement(enemy, tick, range, budget, higherRung || caCommitted);
                  }
               }

               if (plan == null) {
                  this.standDown();
               } else {
                  this.status = plan.step().name().toLowerCase(Locale.ROOT);
                  boolean finishing = this.finishesCommittedWork(plan);
                  boolean mayAct = !higherRung && !caCommitted || finishing;
                  if (mayAct) {
                     this.reservedTick = tick;
                  }

                  boolean releasedToDefend = mayAct && !finishing && !this.cadenceReady() && CrystalAuraModule.defendIntends();
                  if (mayAct && !releasedToDefend) {
                     this.activityTick = tick;
                  }

                  RiptideHandArbiter.holdHand("anchor-aura");
                  RiptideRotationUtil.Rotation wire = this.wireRotation();
                  boolean allowed = wire != null && this.cadenceReady() && mayAct;
                  boolean slotReady = allowed && this.ensureSlot(plan.slot(), finishing);
                  boolean finishSwitchRefused = finishing && allowed && !slotReady && MC.player.getInventory().getSelectedSlot() != plan.slot();
                  BlockHitResult hit = slotReady ? this.gateHit(plan, wire) : null;
                  if (mayAct && !releasedToDefend && !finishSwitchRefused) {
                     boolean pinCommittedFire = hit != null && (higherRung || caCommitted);
                     RiptideKillAuraRotation.setTarget("anchor-aura", pinCommittedFire ? 30 : 20, hit != null ? wire : plan.candidate().aim().goal());
                     RiptideKillAuraRotation.update("anchor-aura", MC.player, hit != null);
                  }

                  if (hit != null) {
                     RiptideRotationUtil.Rotation outgoing = RiptideSilentAim.activeOutgoingRotation(MC.player);
                     if (outgoing != null && sameRotation(outgoing, wire)) {
                        this.execute(plan, hit, tick);
                     }
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
      this.status = "";
      if (this.cycleCell == null) {
         RiptideHandArbiter.releaseHand("anchor-aura");
         RiptideHandArbiter.releaseSlots("anchor-aura");
      }

      if ("anchor-aura".equals(RiptideKillAuraRotation.currentOwner())) {
         RiptideKillAuraRotation.beginWindDown("anchor-aura");
         RiptideKillAuraRotation.update("anchor-aura", MC.player);
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

   private void bookAction() {
      this.lastActionTick = RiptideSharedState.get().getClientTickCounter();
      this.lastActionNanos = System.nanoTime();
      int jitter = this.integer("step-jitter");
      long extra = jitter > 0 ? this.random.nextInt(jitter + 1) : 0L;
      this.actionFloorNanos = (this.integer("step-delay") + extra) * 1000000L;
      this.switchBackTicks = this.integer("switch-back-delay");
   }

   @Override
   public boolean shouldCancelUse(HitResult hitResult, InteractionHand hand) {
      return this.lastActionTick == RiptideSharedState.get().getClientTickCounter();
   }

   @Override
   public boolean shouldCancelAttack(HitResult hitResult) {
      return hitResult instanceof EntityHitResult
         && "anchor-aura".equals(RiptideKillAuraRotation.currentOwner())
         && RiptideKillAuraRotation.hasCurrentRotation();
   }

   private AnchorAuraModule.Plan planCycle(int tick, double range, RiptideFaceScan.Budget budget) {
      BlockPos cell = this.cycleCell;
      if (cell == null) {
         return null;
      } else {
         BlockState state = MC.level.getBlockState(cell);
         boolean anchor = state.getBlock() instanceof RespawnAnchorBlock;
         if (!anchor) {
            if (this.detonateSent) {
               this.finishCycle(false);
               return null;
            } else if (this.bool("place") && !this.abandoning && this.placeAttempts < 3) {
               this.chargeSent = false;
               this.chargeTick = Integer.MIN_VALUE;
               return this.placePlan(cell, range, budget);
            } else {
               this.finishCycle(true);
               return null;
            }
         } else {
            int charge = (Integer)state.getValue(RespawnAnchorBlock.CHARGE);
            if (this.cycleShield != null && MC.level.getBlockState(this.cycleShield).canBeReplaced()) {
               if (!this.abandoning && this.shieldAttempts < 3 && this.shieldMaterialsPresent(this.cycleShieldObsidian)) {
                  AnchorAuraModule.Plan shield = this.shieldPlan(this.cycleShield, range, budget);
                  if (charge <= 0 || shield != null && !this.selfSafe(cell)) {
                     return shield;
                  }

                  this.cycleShield = null;
                  this.cycleShieldObsidian = false;
               } else {
                  this.cycleShield = null;
                  this.cycleShieldObsidian = false;
               }
            }

            if (charge <= 0) {
               if (!this.bool("charge")) {
                  return null;
               } else {
                  return this.chargeSent && tick - this.chargeTick < 10 ? null : this.usePlan(cell, AnchorAuraModule.Step.CHARGE, range, budget);
               }
            } else if (!this.bool("detonate") || !this.detonateReady(cell, state)) {
               return null;
            } else {
               return this.detonateSent && tick - this.detonateTick < 10 ? null : this.usePlan(cell, AnchorAuraModule.Step.DETONATE, range, budget);
            }
         }
      }
   }

   private AnchorAuraModule.Plan planNewCycle(LivingEntity enemy, int tick, double range, RiptideFaceScan.Budget budget) {
      BlockPos adopted = this.findAdoptable(enemy, range);
      if (adopted != null) {
         BlockState state = MC.level.getBlockState(adopted);
         AnchorAuraModule.Step step = state.getValue(RespawnAnchorBlock.CHARGE) > 0 ? AnchorAuraModule.Step.DETONATE : AnchorAuraModule.Step.CHARGE;
         boolean usable = step == AnchorAuraModule.Step.DETONATE ? this.bool("detonate") && this.detonateReady(adopted, state) : this.chargedCycleCanFire();
         boolean refused = true;
         if (usable) {
            RiptideFaceScan.Refusal[] outcome = new RiptideFaceScan.Refusal[1];
            AnchorAuraModule.Plan plan = this.usePlan(adopted, step, range, budget, outcome);
            if (plan != null) {
               this.beginCycle(adopted, tick, null, false);
               return plan;
            }

            refused = outcome[0] != RiptideFaceScan.Refusal.NO_BUDGET || this.budgetParkBans(adopted);
         }

         if (refused) {
            this.banned.put(adopted, tick + 40);
         }
      }

      if (!this.bool("place")) {
         return null;
      } else if (!this.chargedCycleCanFire()) {
         return null;
      } else {
         List<BlockPos> cells = this.rankCells(enemy, range);
         if (cells.isEmpty()) {
            if (this.bool("glowstone-shield")) {
               AnchorAuraModule.Plan shielded = this.planShieldRoute(enemy, range, budget);
               if (shielded != null) {
                  return shielded;
               }
            }

            if (this.targetSeenTicks < 3) {
               this.targetSeenTicks++;
            }

            if (this.bool("force-engage") && this.targetSeenTicks >= 3) {
               cells = this.rankForceEngageCells(enemy, range);
            }
         } else {
            this.targetSeenTicks = 0;
         }

         if (cells.isEmpty()) {
            return null;
         } else {
            RiptideFaceScan.Refusal[] outcome = new RiptideFaceScan.Refusal[1];
            int start = this.placeCursorIndex(cells);

            for (int stepx = 0; stepx < cells.size(); stepx++) {
               int index = start + stepx;
               if (index >= cells.size()) {
                  index -= cells.size();
               }

               BlockPos cell = cells.get(index);
               outcome[0] = null;
               AnchorAuraModule.Plan plan = this.placePlan(cell, null, false, range, budget, outcome);
               if (plan != null) {
                  this.placeCursor = cell;
                  return plan;
               }

               if (outcome[0] == RiptideFaceScan.Refusal.NO_BUDGET) {
                  this.placeCursor = cell;
                  return null;
               }

               this.placeCursor = cells.get(index + 1 == cells.size() ? 0 : index + 1);
            }

            return null;
         }
      }
   }

   private int placeCursorIndex(List<BlockPos> cells) {
      if (this.placeCursor == null) {
         return 0;
      } else {
         int index = cells.indexOf(this.placeCursor);
         return index < 0 ? 0 : index;
      }
   }

   private boolean budgetParkBans(BlockPos cell) {
      if (cell.equals(this.budgetParkedCell)) {
         this.budgetParkStreak++;
      } else {
         this.budgetParkedCell = cell.immutable();
         this.budgetParkStreak = 1;
      }

      if (this.budgetParkStreak < 3) {
         return false;
      } else {
         this.budgetParkedCell = null;
         this.budgetParkStreak = 0;
         return true;
      }
   }

   private AnchorAuraModule.Plan planCommittedPlacement(LivingEntity enemy, int tick, double range, RiptideFaceScan.Budget budget, boolean hotbarOwnedAbove) {
      if (this.pendingCell != null) {
         boolean valid = this.bool("place")
            && enemy == this.pendingTarget
            && isAnchorItem(MC.player.getInventory().getItem(this.pendingSlot))
            && !RiptideHandArbiter.slotReserved(this.pendingSlot, "anchor-aura")
            && this.placeable(Blocks.RESPAWN_ANCHOR.defaultBlockState(), this.pendingCell, this.nextTickBox())
            && (
               this.pendingShield == null
                  || this.shieldMaterialsPresent(this.pendingShieldObsidian)
                     && this.placeable(shieldBlock(this.pendingShieldObsidian), this.pendingShield, this.nextTickBox())
            );
         if (valid) {
            if (hotbarOwnedAbove) {
               this.pendingExpiry = tick + 25;
            }

            if (tick - this.pendingExpiry < 0) {
               AnchorAuraModule.Plan held = this.placePlan(this.pendingCell, this.pendingShield, this.pendingShieldObsidian, range, budget, null);
               if (held != null) {
                  this.pendingStreak = 0;
                  return held;
               }

               if (++this.pendingStreak < 3) {
                  return null;
               }
            }

            this.banned.put(this.pendingCell, tick + 10);
         }

         this.clearPending();
      }

      AnchorAuraModule.Plan plan = this.planNewCycle(enemy, tick, range, budget);
      if (plan != null && plan.step() == AnchorAuraModule.Step.PLACE) {
         this.pendingCell = plan.cell();
         this.pendingShield = plan.shield();
         this.pendingShieldObsidian = plan.shieldObsidian();
         this.pendingSlot = plan.slot();
         this.pendingTarget = enemy;
         this.pendingExpiry = tick + 25;
      }

      return plan;
   }

   private void clearPending() {
      this.pendingCell = null;
      this.pendingSlot = -1;
      this.pendingTarget = null;
      this.pendingExpiry = 0;
      this.pendingStreak = 0;
      this.pendingShield = null;
      this.pendingShieldObsidian = false;
   }

   private boolean chargedCycleCanFire() {
      if (!this.bool("charge") || !this.bool("detonate")) {
         return false;
      } else if (this.findHotbarSlot(AnchorAuraModule::isFuel) < 0) {
         return false;
      } else {
         return isFuel(MC.player.getOffhandItem()) ? false : this.triggerSlot() >= 0;
      }
   }

   private void beginCycle(BlockPos cell, int tick, BlockPos shield, boolean shieldObsidian) {
      this.clearPending();
      this.cycleCell = cell.immutable();
      this.cycleShield = shield;
      this.cycleShieldObsidian = shieldObsidian;
      this.shieldAttempts = 0;
      this.cycleDeadline = tick + 20;
      this.abandoning = false;
      this.detonateSent = false;
      this.detonateTick = Integer.MIN_VALUE;
      this.chargeSent = false;
      this.chargeTick = Integer.MIN_VALUE;
      this.placeAttempts = 0;
      this.targetSeenTicks = 0;
      this.placeCursor = null;
   }

   private void tickDeadline(int tick) {
      if (this.cycleCell != null) {
         if (!this.abandoning) {
            if (tick - this.cycleDeadline > 0) {
               this.abandoning = true;
               this.abandonDeadline = tick + 20;
            }
         } else {
            if (tick - this.abandonDeadline > 0) {
               this.finishCycle(true);
            }
         }
      }
   }

   private void finishCycle(boolean ban) {
      BlockPos cell = this.cycleCell;
      if (cell != null && ban) {
         this.banned.put(cell, RiptideSharedState.get().getClientTickCounter() + 40);
      }

      this.cycleCell = null;
      this.cycleShield = null;
      this.cycleShieldObsidian = false;
      this.shieldAttempts = 0;
      this.abandoning = false;
      this.detonateSent = false;
      this.detonateTick = Integer.MIN_VALUE;
      this.chargeSent = false;
      this.chargeTick = Integer.MIN_VALUE;
      RiptideHandArbiter.releaseSlots("anchor-aura");
      RiptideHandArbiter.releaseHand("anchor-aura");
   }

   private void pruneBans(int tick) {
      if (!this.banned.isEmpty()) {
         Iterator<Entry<BlockPos, Integer>> entries = this.banned.entrySet().iterator();

         while (entries.hasNext()) {
            if (tick - entries.next().getValue() >= 0) {
               entries.remove();
            }
         }
      }
   }

   private AnchorAuraModule.Plan placePlan(BlockPos cell, double range, RiptideFaceScan.Budget budget) {
      return this.placePlan(cell, null, false, range, budget, null);
   }

   private AnchorAuraModule.Plan placePlan(
      BlockPos cell, BlockPos shield, boolean shieldObsidian, double range, RiptideFaceScan.Budget budget, RiptideFaceScan.Refusal[] outcome
   ) {
      if (!this.placeable(Blocks.RESPAWN_ANCHOR.defaultBlockState(), cell, this.nextTickBox())) {
         return null;
      } else {
         int slot = this.findHotbarSlot(AnchorAuraModule::isAnchorItem);
         if (slot < 0) {
            return null;
         } else {
            ItemStack material = MC.player.getInventory().getItem(slot);
            RiptideFaceScan.Candidate candidate = RiptideFaceScan.best(this.placeRequest(cell, material, range).budget(budget), outcome);
            return candidate == null ? null : new AnchorAuraModule.Plan(cell, AnchorAuraModule.Step.PLACE, slot, candidate, range, shield, shieldObsidian);
         }
      }
   }

   private AnchorAuraModule.Plan usePlan(BlockPos cell, AnchorAuraModule.Step step, double range, RiptideFaceScan.Budget budget) {
      return this.usePlan(cell, step, range, budget, null);
   }

   private AnchorAuraModule.Plan usePlan(
      BlockPos cell, AnchorAuraModule.Step step, double range, RiptideFaceScan.Budget budget, RiptideFaceScan.Refusal[] outcome
   ) {
      int slot = step == AnchorAuraModule.Step.CHARGE ? this.findHotbarSlot(AnchorAuraModule::isFuel) : this.triggerSlot();
      if (slot < 0) {
         return null;
      } else {
         RiptideFaceScan.Candidate candidate = this.useCandidate(cell, range, budget, outcome);
         return candidate == null ? null : new AnchorAuraModule.Plan(cell, step, slot, candidate, range, null, false);
      }
   }

   private AnchorAuraModule.Plan shieldPlan(BlockPos cell, double range, RiptideFaceScan.Budget budget) {
      boolean obsidian = this.cycleShieldObsidian;
      if (!this.placeable(shieldBlock(obsidian), cell, this.nextTickBox())) {
         return null;
      } else {
         Predicate<ItemStack> material = obsidian ? AnchorAuraModule::isObsidian : AnchorAuraModule::isFuel;
         int slot = this.findHotbarSlot(material);
         if (slot < 0) {
            return null;
         } else {
            ItemStack stack = MC.player.getInventory().getItem(slot);
            RiptideFaceScan.Candidate candidate = RiptideFaceScan.best(
               this.placeRequest(cell, stack, range, this.shieldPlacement(this.cycleCell, stack)).budget(budget)
            );
            return candidate == null ? null : new AnchorAuraModule.Plan(cell, AnchorAuraModule.Step.SHIELD, slot, candidate, range, null, obsidian);
         }
      }
   }

   private RiptideFaceScan.Request placeRequest(BlockPos cell, ItemStack stack, double range) {
      return this.placeRequest(cell, stack, range, RiptideFaceScan.blockItem(stack, MC.player, InteractionHand.MAIN_HAND));
   }

   private RiptideFaceScan.Request placeRequest(BlockPos cell, ItemStack stack, double range, RiptideFaceScan.Placement placement) {
      LocalPlayer player = MC.player;
      return new RiptideFaceScan.Request(cell, player.getEyePosition(), range, placement)
         .from(this.aimReference())
         .pitchLimit(RiptideFaceScan.goalPitchLimit())
         .leadEye(player.getEyePosition().add(player.getDeltaMovement()))
         .sneaking(player.isSecondaryUseActive())
         .sneakAllowed(false);
   }

   private RiptideFaceScan.Request useRequest(BlockPos anchor, double range) {
      LocalPlayer player = MC.player;
      return new RiptideFaceScan.Request(anchor, player.getEyePosition(), range, new AnchorAuraModule.AnchorClick(anchor))
         .from(this.aimReference())
         .pitchLimit(RiptideFaceScan.goalPitchLimit())
         .leadEye(player.getEyePosition().add(player.getDeltaMovement()))
         .sneaking(player.isSecondaryUseActive())
         .sneakAllowed(false);
   }

   private RiptideFaceScan.Placement shieldPlacement(final BlockPos anchor, final ItemStack held) {
      final LocalPlayer player = MC.player;
      return new RiptideFaceScan.Placement() {
         {
            Objects.requireNonNull(AnchorAuraModule.this);
         }

         @Override
         public boolean lands(BlockHitResult hit, BlockPos cell) {
            if (hit != null && cell != null && held.getItem() instanceof BlockItem) {
               BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, held, hit);
               return context.canPlace() && context.getClickedPos().equals(cell);
            } else {
               return false;
            }
         }

         @Override
         public boolean clickable(BlockState state, BlockPos pos, boolean sneaking) {
            return !(state.getBlock() instanceof RespawnAnchorBlock)
               ? !RiptideFaceScan.useActionEatsClick(state, pos, sneaking)
               : anchor != null && anchor.equals(pos) && AnchorAuraModule.isObsidian(held) && (Integer)state.getValue(RespawnAnchorBlock.CHARGE) <= 0;
         }
      };
   }

   private RiptideFaceScan.Candidate useCandidate(BlockPos anchor, double range, RiptideFaceScan.Budget budget, RiptideFaceScan.Refusal[] outcome) {
      BlockState state = MC.level.getBlockState(anchor);
      if (!(state.getBlock() instanceof RespawnAnchorBlock)) {
         return null;
      } else {
         RiptideFaceScan.Request request = this.useRequest(anchor, range).budget(budget);
         Vec3 eye = MC.player.getEyePosition();
         RiptideFaceScan.Refusal[] refusal = outcome != null ? outcome : new RiptideFaceScan.Refusal[1];

         for (Direction face : RiptideFaceScan.FACE_ORDER_UP_FIRST) {
            for (AABB rect : RiptideFaceScan.faceRects(state, anchor, face, 3)) {
               RiptideFaceScan.Option option = new RiptideFaceScan.Option(
                  anchor,
                  anchor,
                  face,
                  1,
                  state,
                  rect,
                  faceArea(rect, face),
                  RiptideFaceScan.edgeConfidence(rect, face, eye),
                  false,
                  new RiptideFaceScan.Intent(anchor, face)
               );
               RiptideFaceScan.Aim aim = RiptideFaceScan.solve(option, request, refusal);
               if (aim != null) {
                  RiptideFaceScan.Candidate candidate = RiptideFaceScan.probe(option, aim, request, refusal);
                  if (candidate != null) {
                     return candidate;
                  }

                  if (refusal[0] == RiptideFaceScan.Refusal.NO_BUDGET) {
                     return null;
                  }
               }
            }
         }

         return null;
      }
   }

   private static double faceArea(AABB rect, Direction face) {
      Axis normal = face.getAxis();
      Axis first = RiptideFaceScan.inPlaneAxis(normal, true);
      Axis second = RiptideFaceScan.inPlaneAxis(normal, false);
      return (rect.max(first) - rect.min(first)) * (rect.max(second) - rect.min(second));
   }

   private BlockHitResult gateHit(AnchorAuraModule.Plan plan, RiptideRotationUtil.Rotation wire) {
      LocalPlayer player = MC.player;

      RiptideFaceScan.Request gate = switch (plan.step()) {
         case PLACE -> this.placeRequest(plan.cell(), player.getMainHandItem(), plan.range());
         case SHIELD -> this.placeRequest(plan.cell(), player.getMainHandItem(), plan.range(), this.shieldPlacement(this.cycleCell, player.getMainHandItem()));
         case CHARGE, DETONATE -> this.useRequest(plan.cell(), plan.range());
      };
      return RiptideFaceScan.confirm(plan.candidate(), wire, player.getEyePosition(), plan.range(), gate);
   }

   private void execute(AnchorAuraModule.Plan plan, BlockHitResult hit, int tick) {
      if (this.stepStillLegal(plan)) {
         if (!ModuleRegistry.shouldCancelUseExcept(hit, InteractionHand.MAIN_HAND, "anchor-aura")) {
            if (RiptideCombatClicker.queueUse(hit, InteractionHand.MAIN_HAND)) {
               if (!RiptidePlacementTick.claim("anchor-aura")) {
                  RiptideCombatClicker.cancel();
               } else {
                  this.bookAction();
                  if (plan.step() == AnchorAuraModule.Step.PLACE) {
                     if (this.cycleCell == null || !this.cycleCell.equals(plan.cell())) {
                        this.beginCycle(plan.cell(), tick, plan.shield(), plan.shieldObsidian());
                     }

                     this.placeAttempts++;
                  }

                  if (plan.step() == AnchorAuraModule.Step.SHIELD) {
                     this.shieldAttempts++;
                  }

                  if (plan.step() == AnchorAuraModule.Step.CHARGE) {
                     this.chargeSent = true;
                     this.chargeTick = tick;
                  }

                  if (plan.step() == AnchorAuraModule.Step.DETONATE) {
                     this.detonateSent = true;
                     this.detonateTick = tick;
                  }
               }
            }
         }
      }
   }

   private boolean stepStillLegal(AnchorAuraModule.Plan plan) {
      BlockState state = MC.level.getBlockState(plan.cell());
      boolean anchor = state.getBlock() instanceof RespawnAnchorBlock;

      return switch (plan.step()) {
         case PLACE -> !anchor && isAnchorItem(MC.player.getMainHandItem());
         case SHIELD -> state.canBeReplaced() && (plan.shieldObsidian() ? isObsidian(MC.player.getMainHandItem()) : isFuel(MC.player.getMainHandItem()));
         case CHARGE -> anchor && state.getValue(RespawnAnchorBlock.CHARGE) < 4 && isFuel(MC.player.getMainHandItem());
         case DETONATE -> anchor && !isFuel(MC.player.getMainHandItem()) && this.detonateReady(plan.cell(), state);
      };
   }

   private boolean detonateReady(BlockPos cell, BlockState state) {
      if (!(state.getBlock() instanceof RespawnAnchorBlock)) {
         return false;
      } else {
         int charge = (Integer)state.getValue(RespawnAnchorBlock.CHARGE);
         if (charge <= 0) {
            return false;
         } else if (isFuel(MC.player.getOffhandItem()) && charge < 4) {
            return false;
         } else if (isFuel(MC.player.getMainHandItem()) && this.triggerSlot() < 0) {
            return false;
         } else {
            return !this.anchorExplodes(cell) ? false : this.selfSafe(cell);
         }
      }
   }

   private boolean anchorExplodes(BlockPos pos) {
      Boolean works = (Boolean)MC.level.environmentAttributes().getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, pos);
      return works == null || !works;
   }

   private LivingEntity selectTarget() {
      LocalPlayer player = MC.player;
      double range = this.decimal("target-range");
      AABB search = player.getBoundingBox().inflate(range);
      List<LivingEntity> found = MC.level
         .getEntitiesOfClass(
            LivingEntity.class,
            search,
            entityx -> entityx != player
               && entityx.isAlive()
               && !entityx.isSpectator()
               && this.matchesEntity(entityx)
               && !RiptideAntiBot.suppress(entityx)
               && !TeamsModule.combatExcluded(entityx, "killaura")
               && RiptideExplosionDamage.effectiveHealth(entityx) > 0.0
               && entityx.hurtTime <= this.integer("hurt-time")
         );
      if (found.isEmpty()) {
         return null;
      } else {
         Vec3 eye = player.getEyePosition();
         double rangeSq = range * range;
         LivingEntity best = null;
         double bestScore = Double.MAX_VALUE;

         for (LivingEntity entity : found) {
            double distanceSq = boxDistanceSqr(entity.getBoundingBox(), eye);
            if (!(distanceSq > rangeSq)) {
               String var18 = this.choice("targeting");

               double score = switch (var18) {
                  case "HP" -> RiptideExplosionDamage.effectiveHealth(entity);
                  case "FOV" -> RiptideRotationUtil.rotationAngleTo(
                     RiptideRotationUtil.playerRotation(player), RiptideRotationUtil.lookingAt(entity.getBoundingBox().getCenter(), eye)
                  );
                  default -> distanceSq;
               };
               if (score < bestScore) {
                  bestScore = score;
                  best = entity;
               }
            }
         }

         return best;
      }
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

   private List<BlockPos> rankCells(LivingEntity enemy, double range) {
      LocalPlayer player = MC.player;
      Vec3 eye = player.getEyePosition();
      BlockPos origin = player.blockPosition();
      int[] offsets = sphere(Mth.ceil(range));
      BlockState anchor = Blocks.RESPAWN_ANCHOR.defaultBlockState();
      double damageFloor = this.decimal("min-target-damage");
      double rangeSq = range * range;
      AABB nextBox = this.nextTickBox();
      AABB lead = this.leadBox(enemy);
      List<AnchorAuraModule.Candidate> candidates = new ArrayList<>();
      MutableBlockPos cursor = new MutableBlockPos();

      for (int i = 0; i + 2 < offsets.length; i += 3) {
         cursor.set(origin.getX() + offsets[i], origin.getY() + offsets[i + 1], origin.getZ() + offsets[i + 2]);
         if (!this.banned.containsKey(cursor) && this.anchorExplodes(cursor) && this.placeable(anchor, cursor, nextBox)) {
            Vec3 centre = centreOf(cursor);
            if (!this.bool("only-above") || !(centre.y < enemy.getY())) {
               double distanceSq = eye.distanceToSqr(centre);
               if (!(distanceSq > rangeSq)) {
                  double bound = RiptideExplosionDamage.maxDamageTo(enemy, centre, 5.0F, this.optionsFor(cursor).withOverrideBox(lead));
                  if (!(bound < damageFloor)) {
                     candidates.add(new AnchorAuraModule.Candidate(cursor.immutable(), centre, bound, distanceSq));
                  }
               }
            }
         }
      }

      if (candidates.isEmpty()) {
         return List.of();
      } else {
         candidates.sort(Comparator.comparingDouble(AnchorAuraModule.Candidate::bound).reversed());
         List<AnchorAuraModule.Scored> accepted = new ArrayList<>();
         double bestDamage = -1.0;
         int evaluated = 0;

         for (AnchorAuraModule.Candidate candidate : candidates) {
            if (accepted.size() >= 4 && candidate.bound() <= bestDamage || evaluated >= 32) {
               break;
            }

            evaluated++;
            RiptideExplosionDamage.Ranking rank = this.rankAtLead(enemy, candidate.centre(), this.optionsFor(candidate.pos()), lead);
            if (this.damagePasses(rank)) {
               accepted.add(new AnchorAuraModule.Scored(candidate.pos(), rank, candidate.distanceSq()));
               bestDamage = Math.max(bestDamage, rank.targetDamage());
            }
         }

         if (accepted.isEmpty()) {
            return List.of();
         } else {
            accepted.sort((first, second) -> {
               int byPreference = comparePreference(first.rank(), second.rank());
               return byPreference != 0 ? byPreference : Double.compare(second.distanceSq(), first.distanceSq());
            });
            List<BlockPos> result = new ArrayList<>(Math.min(4, accepted.size()));

            for (AnchorAuraModule.Scored scored : accepted) {
               if (result.size() >= 4) {
                  break;
               }

               result.add(scored.pos());
            }

            return result;
         }
      }
   }

   private List<BlockPos> rankForceEngageCells(LivingEntity enemy, double range) {
      LocalPlayer player = MC.player;
      Vec3 eye = player.getEyePosition();
      BlockPos origin = player.blockPosition();
      int[] offsets = sphere(Mth.ceil(range));
      BlockState anchor = Blocks.RESPAWN_ANCHOR.defaultBlockState();
      double rangeSq = range * range;
      AABB nextBox = this.nextTickBox();
      AABB enemyRing = enemy.getBoundingBox().inflate(1.0);
      int feetY = Mth.floor(enemy.getY());
      List<AnchorAuraModule.Scored> accepted = new ArrayList<>();
      int evaluated = 0;
      MutableBlockPos cursor = new MutableBlockPos();

      for (int i = 0; i + 2 < offsets.length; i += 3) {
         cursor.set(origin.getX() + offsets[i], origin.getY() + offsets[i + 1], origin.getZ() + offsets[i + 2]);
         if (!this.banned.containsKey(cursor) && this.anchorExplodes(cursor) && this.placeable(anchor, cursor, nextBox)) {
            Vec3 centre = centreOf(cursor);
            double distanceSq = eye.distanceToSqr(centre);
            if (!(distanceSq > rangeSq) && new AABB(cursor).intersects(enemyRing)) {
               if (evaluated >= 32) {
                  break;
               }

               evaluated++;
               RiptideExplosionDamage.Ranking rank = RiptideExplosionDamage.cachedRank(enemy, centre, 5.0F, this.optionsFor(cursor));
               if (this.damagePasses(rank)) {
                  accepted.add(new AnchorAuraModule.Scored(cursor.immutable(), rank, distanceSq));
               }
            }
         }
      }

      if (accepted.isEmpty()) {
         return List.of();
      } else {
         accepted.sort((first, second) -> {
            boolean firstLow = first.pos().getY() <= feetY;
            boolean secondLow = second.pos().getY() <= feetY;
            if (firstLow != secondLow) {
               return firstLow ? -1 : 1;
            } else {
               int byPreference = comparePreference(first.rank(), second.rank());
               return byPreference != 0 ? byPreference : Double.compare(second.distanceSq(), first.distanceSq());
            }
         });
         List<BlockPos> result = new ArrayList<>(Math.min(4, accepted.size()));

         for (AnchorAuraModule.Scored scored : accepted) {
            if (result.size() >= 4) {
               break;
            }

            result.add(scored.pos());
         }

         return result;
      }
   }

   private AnchorAuraModule.Plan planShieldRoute(LivingEntity enemy, double range, RiptideFaceScan.Budget budget) {
      if (!this.shieldMaterialsPresent(false) && !this.shieldMaterialsPresent(true)) {
         return null;
      } else {
         LocalPlayer player = MC.player;
         Vec3 eye = player.getEyePosition();
         BlockPos origin = player.blockPosition();
         int[] offsets = sphere(Mth.ceil(range));
         BlockState anchor = Blocks.RESPAWN_ANCHOR.defaultBlockState();
         double damageFloor = this.decimal("min-target-damage");
         double selfCeiling = this.decimal("max-self-damage");
         double rangeSq = range * range;
         AABB nextBox = this.nextTickBox();
         List<AnchorAuraModule.Scored> needsShield = new ArrayList<>();
         int evaluated = 0;
         MutableBlockPos cursor = new MutableBlockPos();

         for (int i = 0; i + 2 < offsets.length; i += 3) {
            cursor.set(origin.getX() + offsets[i], origin.getY() + offsets[i + 1], origin.getZ() + offsets[i + 2]);
            if (!this.banned.containsKey(cursor) && this.anchorExplodes(cursor) && this.placeable(anchor, cursor, nextBox)) {
               Vec3 centre = centreOf(cursor);
               if (!this.bool("only-above") || !(centre.y < enemy.getY())) {
                  double distanceSq = eye.distanceToSqr(centre);
                  if (!(distanceSq > rangeSq) && !(RiptideExplosionDamage.maxDamageTo(enemy, centre, 5.0F, this.optionsFor(cursor)) < damageFloor)) {
                     if (evaluated >= 32) {
                        break;
                     }

                     evaluated++;
                     RiptideExplosionDamage.Ranking rank = RiptideExplosionDamage.cachedRank(enemy, centre, 5.0F, this.optionsFor(cursor));
                     if (!(rank.targetDamage() < damageFloor) && !(rank.selfDamage() <= selfCeiling) && (!this.bool("efficient") || rank.isEfficient())) {
                        needsShield.add(new AnchorAuraModule.Scored(cursor.immutable(), rank, distanceSq));
                     }
                  }
               }
            }
         }

         if (needsShield.isEmpty()) {
            return null;
         } else {
            needsShield.sort((first, second) -> {
               int byPreference = comparePreference(first.rank(), second.rank());
               return byPreference != 0 ? byPreference : Double.compare(second.distanceSq(), first.distanceSq());
            });
            int tried = 0;

            for (AnchorAuraModule.Scored scored : needsShield) {
               if (tried < 4) {
                  tried++;
                  BlockPos shield = this.verifyShield(enemy, scored.pos(), damageFloor, selfCeiling);
                  if (shield == null) {
                     continue;
                  }

                  boolean obsidian;
                  if (this.shieldMaterialsPresent(false) && this.glowstoneSafeSupport(shield)) {
                     obsidian = false;
                  } else {
                     if (!this.shieldMaterialsPresent(true) || !this.obsidianSupport(shield, scored.pos())) {
                        continue;
                     }

                     obsidian = true;
                  }

                  return this.placePlan(scored.pos(), shield, obsidian, range, budget, null);
               }
               break;
            }

            return null;
         }
      }
   }

   private BlockPos verifyShield(LivingEntity enemy, BlockPos anchorCell, double damageFloor, double selfCeiling) {
      Vec3 centre = centreOf(anchorCell);

      for (BlockPos shieldCell : this.shieldCandidates(anchorCell)) {
         RiptideExplosionDamage.Options verify = this.optionsFor(anchorCell).withInclude(shieldCell);
         RiptideExplosionDamage.Ranking rank = RiptideExplosionDamage.cachedRank(enemy, centre, 5.0F, verify);
         if (!RiptideExplosionDamage.killsSelf(rank.selfDamage()) && !(rank.selfDamage() > selfCeiling) && !(rank.targetDamage() < damageFloor)) {
            return shieldCell;
         }
      }

      return null;
   }

   private List<BlockPos> shieldCandidates(BlockPos anchorCell) {
      LocalPlayer player = MC.player;
      AABB box = player.getBoundingBox();
      AABB nextBox = this.nextTickBox();
      AABB ring = box.inflate(1.0);
      Vec3 playerCentre = box.getCenter();
      Vec3 toAnchor = centreOf(anchorCell).subtract(playerCentre).normalize();
      List<BlockPos> cells = new ArrayList<>();
      MutableBlockPos cursor = new MutableBlockPos();

      for (int x = Mth.floor(ring.minX); x <= Mth.floor(ring.maxX); x++) {
         for (int y = Mth.floor(ring.minY); y <= Mth.floor(ring.maxY); y++) {
            for (int z = Mth.floor(ring.minZ); z <= Mth.floor(ring.maxZ); z++) {
               cursor.set(x, y, z);
               if (!cursor.equals(anchorCell)) {
                  AABB cellBox = new AABB(cursor);
                  if (cellBox.intersects(ring)
                     && !cellBox.intersects(box)
                     && !cellBox.intersects(nextBox)
                     && !(centreOf(cursor).subtract(playerCentre).dot(toAnchor) <= 0.0)
                     && this.placeable(Blocks.GLOWSTONE.defaultBlockState(), cursor, nextBox)) {
                     cells.add(cursor.immutable());
                  }
               }
            }
         }
      }

      cells.sort(
         (first, second) -> {
            boolean firstGround = this.groundSupported(first);
            boolean secondGround = this.groundSupported(second);
            if (firstGround != secondGround) {
               return firstGround ? -1 : 1;
            } else {
               return Double.compare(
                  centreOf(second).subtract(playerCentre).normalize().dot(toAnchor), centreOf(first).subtract(playerCentre).normalize().dot(toAnchor)
               );
            }
         }
      );
      return (List<BlockPos>)(cells.size() <= 4 ? cells : new ArrayList<>(cells.subList(0, 4)));
   }

   private boolean groundSupported(BlockPos cell) {
      BlockPos below = cell.below();
      return RiptideFaceScan.isPlaceableSupport(MC.level.getBlockState(below), below, false);
   }

   private boolean glowstoneSafeSupport(BlockPos cell) {
      for (Direction face : Direction.values()) {
         BlockPos support = cell.relative(face);
         if (RiptideFaceScan.isPlaceableSupport(MC.level.getBlockState(support), support, false)) {
            return true;
         }
      }

      return false;
   }

   private boolean obsidianSupport(BlockPos cell, BlockPos anchorCell) {
      return this.glowstoneSafeSupport(cell) || cell.distManhattan(anchorCell) == 1;
   }

   private AABB nextTickBox() {
      return MC.player.getBoundingBox().move(MC.player.getDeltaMovement());
   }

   private boolean placeable(BlockState anchor, BlockPos cell, AABB nextBox) {
      if (CrystalAuraModule.reservesPlacementCell(cell)) {
         return false;
      } else if (CrystalAuraModule.reservesCommittedCell(cell)) {
         return false;
      } else if (!MC.level.getBlockState(cell).canBeReplaced()) {
         return false;
      } else if (!anchor.canSurvive(MC.level, cell)) {
         return false;
      } else {
         return !MC.level.isUnobstructed(anchor, cell, CollisionContext.placementContext(MC.player)) ? false : !nextBox.intersects(new AABB(cell));
      }
   }

   private BlockPos findAdoptable(LivingEntity enemy, double range) {
      LocalPlayer player = MC.player;
      Vec3 eye = player.getEyePosition();
      BlockPos origin = player.blockPosition();
      int[] offsets = sphere(Mth.ceil(range));
      double rangeSq = range * range;
      BlockPos best = null;
      RiptideExplosionDamage.Ranking bestRank = null;
      double bestDistanceSq = 0.0;
      MutableBlockPos cursor = new MutableBlockPos();

      for (int i = 0; i + 2 < offsets.length; i += 3) {
         cursor.set(origin.getX() + offsets[i], origin.getY() + offsets[i + 1], origin.getZ() + offsets[i + 2]);
         if (!this.banned.containsKey(cursor)) {
            BlockState state = MC.level.getBlockState(cursor);
            if (state.getBlock() instanceof RespawnAnchorBlock && this.anchorExplodes(cursor)) {
               Vec3 centre = centreOf(cursor);
               double distanceSq = eye.distanceToSqr(centre);
               if (!(distanceSq > rangeSq)) {
                  RiptideExplosionDamage.Ranking rank = RiptideExplosionDamage.cachedRank(enemy, centre, 5.0F, this.optionsFor(cursor));
                  if (this.damagePasses(rank)) {
                     int preference = bestRank == null ? -1 : comparePreference(rank, bestRank);
                     if (preference < 0 || preference == 0 && distanceSq > bestDistanceSq) {
                        best = cursor.immutable();
                        bestRank = rank;
                        bestDistanceSq = distanceSq;
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private static boolean isAnchorItem(ItemStack stack) {
      return !stack.isEmpty() && stack.getItem() == Items.RESPAWN_ANCHOR;
   }

   private static boolean isFuel(ItemStack stack) {
      return !stack.isEmpty() && stack.getItem() == Items.GLOWSTONE;
   }

   private static boolean isObsidian(ItemStack stack) {
      return !stack.isEmpty() && stack.getItem() == Items.OBSIDIAN;
   }

   private static BlockState shieldBlock(boolean obsidian) {
      return (obsidian ? Blocks.OBSIDIAN : Blocks.GLOWSTONE).defaultBlockState();
   }

   private boolean shieldMaterialsPresent(boolean obsidian) {
      return !obsidian
         ? this.hotbarFuelCount() >= 2
         : this.findHotbarSlot(AnchorAuraModule::isFuel) >= 0 && this.findHotbarSlot(AnchorAuraModule::isObsidian) >= 0;
   }

   private int hotbarFuelCount() {
      LocalPlayer player = MC.player;
      int count = 0;

      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = player.getInventory().getItem(slot);
         if (isFuel(stack)) {
            count += stack.getCount();
         }
      }

      return count;
   }

   private int triggerSlot() {
      LocalPlayer player = MC.player;
      int selected = player.getInventory().getSelectedSlot();
      if (!isFuel(player.getMainHandItem()) && !RiptideHandArbiter.slotReserved(selected, "anchor-aura")) {
         return selected;
      } else {
         int anchor = this.findHotbarSlot(AnchorAuraModule::isAnchorItem);
         return anchor >= 0 ? anchor : this.findHotbarSlot(stack -> !isFuel(stack));
      }
   }

   private boolean ensureSlot(int slot, boolean finishingWork) {
      if (slot < 0) {
         return true;
      } else {
         LocalPlayer player = MC.player;
         if (!RiptideHandArbiter.reserveSlot("anchor-aura", slot)) {
            return false;
         } else {
            int held = player.getInventory().getSelectedSlot();
            if (held == slot) {
               this.switchBackTicks = this.integer("switch-back-delay");
               return true;
            } else {
               if (this.changeHotbarSlot(slot, finishingWork)) {
                  if (this.previousSlot < 0) {
                     this.previousSlot = held;
                  }

                  this.switchedToSlot = slot;
               }

               this.switchBackTicks = this.integer("switch-back-delay");
               return false;
            }
         }
      }
   }

   private boolean changeHotbarSlot(int slot, boolean finishingWork) {
      if (!BedDefenderModule.ownsSilentRotation() && !SurroundModule.ownsSilentRotation()) {
         if (finishingWork || !CrystalAuraModule.inPrimePosition() || !CrystalAuraModule.reservesCombatTick() && !CrystalAuraModule.hasLiveCommitment()) {
            int tick = RiptideSharedState.get().getClientTickCounter();
            if (tick == this.hotbarChangeTick) {
               return false;
            } else if (!RiptideHandArbiter.beginHandPacketGroup("anchor-aura")) {
               return false;
            } else {
               try {
                  RiptideInventoryHelper.selectHotbarSlot(MC, slot);
               } finally {
                  RiptideHandArbiter.endHandPacketGroup("anchor-aura");
               }

               this.hotbarChangeTick = tick;
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private int findHotbarSlot(Predicate<ItemStack> match) {
      LocalPlayer player = MC.player;
      int selected = player.getInventory().getSelectedSlot();
      int best = -1;
      int bestSteps = Integer.MAX_VALUE;

      for (int slot = 0; slot < 9; slot++) {
         if (match.test(player.getInventory().getItem(slot)) && !RiptideHandArbiter.slotReserved(slot, "anchor-aura")) {
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
               if (!CrystalAuraModule.holdsBorrowedSlot(selected) && !KillAuraModule.holdsBorrowedSlot(selected)) {
                  this.previousSlot = -1;
                  this.switchedToSlot = -1;
                  this.switchBackTicks = 0;
               }
            } else if (this.switchBackTicks > 0) {
               this.switchBackTicks--;
            } else if (!RiptideHandArbiter.slotReserved(this.previousSlot, "anchor-aura")) {
               if (this.changeHotbarSlot(this.previousSlot, false)) {
                  this.previousSlot = -1;
                  this.switchedToSlot = -1;
               }
            }
         }
      }
   }

   private void rebuildDamageOptions() {
      this.damageOptions = RiptideExplosionDamage.Options.DEFAULT.withTerrain(this.bool("terrain")).withEstimateProtection(true);
      this.cellOptions.clear();
   }

   private RiptideExplosionDamage.Options optionsFor(BlockPos cell) {
      BlockPos key = cell.immutable();
      RiptideExplosionDamage.Options cached = this.cellOptions.get(key);
      if (cached != null) {
         return cached;
      } else {
         RiptideExplosionDamage.Options options = this.damageOptions
            .withExclude(List.of(key))
            .withDamageSource(RiptideExplosionDamage.badRespawnPointSource(MC.level, centreOf(key)));
         this.cellOptions.put(key, options);
         return options;
      }
   }

   private AABB leadBox(LivingEntity enemy) {
      double dx = (enemy.getX() - enemy.xo) * 6.0;
      double dz = (enemy.getZ() - enemy.zo) * 6.0;
      double length = Math.hypot(dx, dz);
      AABB live = enemy.getBoundingBox();
      if (length < 0.001) {
         return live;
      } else {
         if (length > 2.0) {
            dx *= 2.0 / length;
            dz *= 2.0 / length;
            length = 2.0;
         }

         Vec3 feet = enemy.position();
         BlockHitResult wall = MC.level.clip(new ClipContext(feet, feet.add(dx, 0.0, dz), Block.COLLIDER, Fluid.NONE, enemy));
         if (wall.getType() != Type.MISS) {
            double allowed = (wall.getLocation().distanceTo(feet) - 0.35) / length;
            if (allowed <= 0.0) {
               return live;
            }

            if (allowed < 1.0) {
               dx *= allowed;
               dz *= allowed;
            }
         }

         return live.move(dx, 0.0, dz);
      }
   }

   private RiptideExplosionDamage.Ranking rankAtLead(LivingEntity enemy, Vec3 centre, RiptideExplosionDamage.Options options, AABB lead) {
      double target = RiptideExplosionDamage.cachedDamageTo(enemy, centre, 5.0F, options.withOverrideBox(lead));
      LocalPlayer player = MC.player;
      double self = enemy == player ? target : RiptideExplosionDamage.cachedSelfDamage(centre, 5.0F, options);
      return new RiptideExplosionDamage.Ranking(target, self);
   }

   private boolean damagePasses(RiptideExplosionDamage.Ranking rank) {
      if (RiptideExplosionDamage.killsSelf(rank.selfDamage())) {
         return false;
      } else if (rank.targetDamage() < this.decimal("min-target-damage")) {
         return false;
      } else {
         return rank.selfDamage() > this.decimal("max-self-damage") ? false : !this.bool("efficient") || rank.isEfficient();
      }
   }

   private boolean selfSafe(BlockPos cell) {
      return !RiptideExplosionDamage.killsSelf(this.selfDamageAt(cell));
   }

   private double selfDamageAt(BlockPos cell) {
      RiptideExplosionDamage.Options options = this.optionsFor(cell);
      if (this.cycleShield != null
         && cell.equals(this.cycleCell)
         && MC.level.getBlockState(this.cycleShield).is(shieldBlock(this.cycleShieldObsidian).getBlock())) {
         options = options.withInclude(this.cycleShield);
      }

      return RiptideExplosionDamage.cachedSelfDamage(centreOf(cell), 5.0F, options);
   }

   private static int comparePreference(RiptideExplosionDamage.Ranking first, RiptideExplosionDamage.Ranking second) {
      int byBucket = Double.compare(damageBucket(second.targetDamage()), damageBucket(first.targetDamage()));
      return byBucket != 0 ? byBucket : Double.compare(first.selfDamage(), second.selfDamage());
   }

   private static double damageBucket(double targetDamage) {
      return Math.floor(targetDamage * 2.0);
   }

   private static Vec3 centreOf(BlockPos pos) {
      return Vec3.atCenterOf(pos);
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

   private record AnchorClick(BlockPos anchor) implements RiptideFaceScan.Placement {
      @Override
      public boolean lands(BlockHitResult hit, BlockPos cell) {
         return hit != null && this.anchor.equals(hit.getBlockPos());
      }

      @Override
      public boolean clickable(BlockState state, BlockPos pos, boolean sneaking) {
         return this.anchor.equals(pos) && state.getBlock() instanceof RespawnAnchorBlock;
      }
   }

   private record Candidate(BlockPos pos, Vec3 centre, double bound, double distanceSq) {
   }

   private record Plan(
      BlockPos cell, AnchorAuraModule.Step step, int slot, RiptideFaceScan.Candidate candidate, double range, BlockPos shield, boolean shieldObsidian
   ) {
   }

   private record Scored(BlockPos pos, RiptideExplosionDamage.Ranking rank, double distanceSq) {
   }

   private static enum Step {
      PLACE,
      SHIELD,
      CHARGE,
      DETONATE;
   }
}
