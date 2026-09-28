package riptide.modules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.player.ClientInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.RegistryListSetting;
import riptide.util.RegistryListCodec;
import riptide.util.RiptideCombatClicker;
import riptide.util.RiptideFaceScan;
import riptide.util.RiptideHandArbiter;
import riptide.util.RiptideHumanRotation;
import riptide.util.RiptideInventoryHelper;
import riptide.util.RiptideKillAuraRotation;
import riptide.util.RiptidePlacementTick;
import riptide.util.RiptideRemoteView;
import riptide.util.RiptideRotationUtil;
import riptide.util.RiptideServerRotationView;
import riptide.util.RiptideSharedState;
import riptide.util.RiptideSilentAim;
import riptide.util.macro.MacroExecutor;
import riptide.util.multi.MultiPilot;
import riptide.util.multi.PacketTeleportController;

public final class BedDefenderModule extends Module implements RiptideSilentAim.Owner {
   private static final float TURN_CAP = 55.0F;
   private static final double CELL_REACH_SLACK = 1.0;
   private static final int SWITCH_BACK_IDLE_TICKS = 6;
   private static final int OWNERSHIP_TAIL_TICKS = 5;
   private static final int HOTBAR_COUNT_MARGIN = 16;
   private static final int MAX_PLACED_MEMORY = 64;
   private static final int MAX_TRACE_CHARS = 96;
   private static final int MAX_TICK_PROBE_CLIPS = 16;
   private static final int MAX_CANDIDATES = 4096;
   private static final int SHELL_COMPLETE_TICKS = 20;
   private static final int GATE_STALL_TICKS = 8;
   private static final int GATE_STALL_COOLDOWN_TICKS = 60;
   private static final int MAX_GATE_STALLS = 16;
   private static final int MAX_BANNED_CLICKS = 12;
   private static final double CAPPED_CELL_HEIGHT = 0.5;
   private static final int MAX_COMPLETED_BEDS = 16;
   private final List<BlockPos> targets = new ArrayList<>();
   private final Set<BlockPos> placedByUs = new LinkedHashSet<>();
   private BlockPos cachedBed;
   private int sinceSweep;
   private final Set<BlockPos> completedBeds = new LinkedHashSet<>();
   private BlockPos progressBed;
   private BlockPos progressPartner;
   private int sinceProgressTicks;
   private final Map<BlockPos, BedDefenderModule.GateStall> gateStalls = new LinkedHashMap<>();
   private int lastPlaceTick = Integer.MIN_VALUE;
   private int aimTick = Integer.MIN_VALUE;
   private boolean sneakRequested;
   private int sneakRequestTick = Integer.MIN_VALUE;
   private int originalSlot = -1;
   private int switchedToSlot = -1;
   private int idleTicks;
   private final KillAuraModule.TickVerdict throwableVerdict = new KillAuraModule.TickVerdict();
   private final List<RiptideFaceScan.Option> options = new ArrayList<>();
   private int[] optionRank = new int[0];
   private int cursor;
   private long shellKey;
   private long rankKey;
   private boolean shellKeyKnown;
   private BedDefenderModule.Plan[] answers = new BedDefenderModule.Plan[0];
   private int[] answerTick = new int[0];
   private final RiptideFaceScan.Budget rayBudget = new RiptideFaceScan.Budget(16);
   private int planTick = Integer.MIN_VALUE;
   private boolean visitStarved;
   private boolean visitOutranked;
   private final Map<BlockPos, Boolean> obstructedCells = new LinkedHashMap<>();
   private String traceDetail;
   private String cachedFilterRaw;
   private Set<Block> cachedFilterBlocks = Set.of();
   private BlockPos pendingPlacementCell;

   public BedDefenderModule() {
      super("bed-defender", "BedDefender", ModuleCategory.PLAYER, "Walls in a bed with your hardest blocks.");
      this.add(new IntSetting("max-layers", "Max Layers", 1, 1, 5, 1).group("Bed").description("Shell depth around the bed"));
      this.add(new BoolSetting("prefer-sides", "Prefer Sides", true).group("Bed").description("Sides before the top"));
      this.add(new BoolSetting("defend-under", "Defend Under", true).group("Bed").description("Also fill under the bed"));
      this.add(
         new BoolSetting("top-riser", "Top Riser", true).group("Bed").visibleWhen(() -> this.integer("max-layers") == 1).description("Riser to reach the top")
      );
      this.add(new BoolSetting("require-sneak", "Require Sneak", false).group("Bed").description("Only while holding shift"));
      this.add(new IntSetting("rescan-interval", "Rescan Interval", 10, 1, 40, 1).group("Bed").unit("ticks").description("Ticks between full bed sweeps"));
      this.add(new ChoiceSetting("filter-mode", "Filter", "Whitelist", "Whitelist", "Blacklist").group("Blocks").description("How the list is applied"));
      this.add(
         RegistryListSetting.blocks("blocks", "Blocks", "minecraft:obsidian|minecraft:end_stone|minecraft:ender_chest")
            .group("Blocks")
            .description("Defence material")
      );
      this.add(new BoolSetting("allow-chests", "Allow Chests", false).group("Blocks").description("Also allow chests as material"));
      this.add(new BoolSetting("sneak-for-bed", "Sneak For Bed", true).group("Placing").description("Crouch to place on bed"));
      this.add(new BoolSetting("switch-back", "Switch Back", true).group("Placing").description("Return to your old slot"));
      this.add(new BoolSetting("trace-faces", "Trace Faces", false).group("Placing").description("Show why faces were refused"));
   }

   @Override
   public void onEnable() {
      this.reset();
   }

   @Override
   public void onDisable() {
      this.reset();
      RiptideKillAuraRotation.beginWindDown(this.id());
   }

   @Override
   public void onGameLeft() {
      this.reset();
      if (this.id().equals(RiptideKillAuraRotation.currentOwner())) {
         RiptideKillAuraRotation.reset();
      }
   }

   private void reset() {
      this.dropTargets();
      this.placedByUs.clear();
      this.cachedBed = null;
      this.completedBeds.clear();
      this.forgetProgress();
      this.forgetPlans();
      this.gateStalls.clear();
      this.sinceSweep = 0;
      this.lastPlaceTick = Integer.MIN_VALUE;
      this.sneakRequested = false;
      this.originalSlot = -1;
      this.switchedToSlot = -1;
      this.idleTicks = 0;
      RiptideHandArbiter.releaseAll(this.id());
   }

   public static Input modifyMovementInput(ClientInput source, Input original) {
      if (original != null && MC != null && MC.player != null && MC.player.input == source) {
         if (!(ModuleRegistry.get("bed-defender") instanceof BedDefenderModule bed && bed.isEnabled())) {
            return original;
         } else {
            return bed.sneakLive() && !original.shift()
               ? new Input(original.forward(), original.backward(), original.left(), original.right(), original.jump(), true, original.sprint())
               : original;
         }
      } else {
         return original;
      }
   }

   private boolean sneakLive() {
      if (!this.sneakRequested) {
         return false;
      } else if (this.sneakRequestTick != RiptideSharedState.get().getClientTickCounter()) {
         this.sneakRequested = false;
         return false;
      } else {
         return this.sneakPressIsOnlyACrouch();
      }
   }

   private boolean sneakPressIsOnlyACrouch() {
      if (MC == null || MC.player == null) {
         return false;
      } else {
         return !MC.player.isPassenger() && !MC.player.getAbilities().flying ? !PacketTeleportController.ownsMainMovement() && !MultiPilot.isActive() : false;
      }
   }

   @Override
   public boolean ticksWhenDisabled() {
      return true;
   }

   @Override
   public boolean hasDisabledTickWork() {
      return RiptideKillAuraRotation.hasCurrentRotation() && this.id().equals(RiptideKillAuraRotation.currentOwner());
   }

   @Override
   public void tick() {
      if (MC != null && MC.player != null) {
         if (!this.isEnabled()) {
            if (this.id().equals(RiptideKillAuraRotation.currentOwner())) {
               RiptideKillAuraRotation.update(this.id(), MC.player);
            }
         }
      }
   }

   @Override
   public String info() {
      if (!this.targets.isEmpty()) {
         String count = Integer.toString(this.targets.size());
         return this.traceDetail == null ? count : count + " " + this.traceDetail;
      } else {
         return this.cachedBed != null ? "bed" : "";
      }
   }

   public static boolean ownsSilentRotation() {
      if (!(ModuleRegistry.get("bed-defender") instanceof BedDefenderModule bed)) {
         return false;
      } else {
         int age = RiptideSharedState.get().getClientTickCounter() - bed.aimTick;
         return age >= 0 && age <= 5 && "bed-defender".equals(RiptideKillAuraRotation.currentOwner());
      }
   }

   @Override
   public boolean silentCorrectionApplies() {
      boolean enabled = this.isEnabled();
      return !RiptideSilentAim.scaffoldOwnsRotation() && (RiptideKillAuraRotation.isWindingDown() || enabled && this.canRun());
   }

   private boolean canRun() {
      return this.canPlan() && !this.throwableHeldThisTick();
   }

   private boolean canPlan() {
      return MC != null
         && MC.player != null
         && MC.level != null
         && MC.getConnection() != null
         && MC.gui.screen() == null
         && MC.gui.overlay() == null
         && !PackHideState.isActive()
         && !PackFreecamState.isActive()
         && !RiptideRemoteView.isActive()
         && !MultiPilot.isActive()
         && !PacketTeleportController.ownsMainMovement()
         && !MacroExecutor.isRunning()
         && !MC.player.isDeadOrDying()
         && !MC.player.isSpectator()
         && !MC.player.isUsingItem()
         && !MC.player.isHandsBusy()
         && !RiptideSilentAim.scaffoldOwnsRotation()
         && !ScaffoldModule.reservesRageInput()
         && !AutoTotemModule.operationActive()
         && !AutoArmorModule.operationActive();
   }

   private boolean throwableHeldThisTick() {
      return this.throwableVerdict.resolve(RiptideSharedState.get().getClientTickCounter(), this::holdsInstantThrowable);
   }

   private boolean holdsInstantThrowable() {
      if (MC != null && MC.player != null) {
         ItemStack mainHand = MC.player.getMainHandItem();
         return KillAuraModule.isInstantThrowable(mainHand) ? true : mainHand.isEmpty() && KillAuraModule.isInstantThrowable(MC.player.getOffhandItem());
      } else {
         return false;
      }
   }

   private void standDown() {
      if (MC != null && MC.player != null) {
         if (this.id().equals(RiptideKillAuraRotation.currentOwner())) {
            RiptideKillAuraRotation.beginWindDown(this.id());
            RiptideKillAuraRotation.update(this.id(), MC.player);
         }
      }
   }

   @Override
   public void preMovementTick() {
      this.sneakRequested = false;
      if (MC != null && MC.player != null && MC.level != null && MC.getConnection() != null) {
         this.tickPendingPlacement();
         if (this.bool("require-sneak") && !MC.player.isShiftKeyDown()) {
            this.dropTargets();
            this.standDown();
         } else {
            int slot = this.resolveHotbarSlot();
            ItemStack material = slot < 0 ? ItemStack.EMPTY : MC.player.getInventory().getItem(slot);
            RiptideServerRotationView.WireSnapshot wire = RiptideServerRotationView.snapshot();
            RiptideRotationUtil.Rotation wireRotation = wire.initialized() ? new RiptideRotationUtil.Rotation(wire.currentYaw(), wire.currentPitch()) : null;
            RiptideRotationUtil.Rotation from = wireRotation != null ? wireRotation : RiptideRotationUtil.playerRotation(MC.player);
            this.beginPlanTick();
            this.buildTargets(material);
            this.refreshOptions(from, material);
            this.runPlacement(slot, material, wireRotation, from);
         }
      } else {
         this.dropTargets();
         this.standDown();
      }
   }

   private void beginPlanTick() {
      int tick = RiptideSharedState.get().getClientTickCounter();
      if (tick != this.planTick) {
         this.planTick = tick;
         this.rayBudget.reset(16);
         this.forgetPlans();
      }
   }

   private void forgetPlans() {
      this.obstructedCells.clear();
      this.traceDetail = null;
   }

   private void dropTargets() {
      this.targets.clear();
      this.options.clear();
      this.optionRank = new int[0];
      this.answers = new BedDefenderModule.Plan[0];
      this.answerTick = new int[0];
      this.shellKeyKnown = false;
      this.cursor = 0;
   }

   private void buildTargets(ItemStack material) {
      this.targets.clear();
      Vec3 eye = MC.player.getEyePosition();
      double reach = MC.player.blockInteractionRange();
      int layers = this.integer("max-layers");
      BlockPos bedPos = this.resolveBed(eye, reach + layers + 1.0);
      if (bedPos == null) {
         this.forgetProgress();
      } else {
         BlockState bedState = MC.level.getBlockState(bedPos);
         if (!(bedState.getBlock() instanceof BedBlock)) {
            this.cachedBed = null;
            this.forgetProgress();
         } else {
            Direction other = BedBlock.getConnectedDirection(bedState);
            Direction outward = other.getOpposite();
            Direction[] perps = other.getAxis() == Axis.X ? new Direction[]{Direction.NORTH, Direction.SOUTH} : new Direction[]{Direction.WEST, Direction.EAST};
            BlockPos partner = bedPos.relative(other);
            Set<BlockPos> visited = new HashSet<>();
            List<BedDefenderModule.Step> steps = new ArrayList<>();
            collectCells(bedPos, layers, new Direction[]{outward, Direction.UP, perps[0], perps[1]}, visited, steps);
            collectCells(partner, layers, new Direction[]{other, Direction.UP, perps[0], perps[1]}, visited, steps);
            if (this.bool("defend-under")) {
               if (visited.add(bedPos.below())) {
                  steps.add(new BedDefenderModule.Step(bedPos.below(), 1));
               }

               if (visited.add(partner.below())) {
                  steps.add(new BedDefenderModule.Step(partner.below(), 1));
               }
            }

            double limit = reach + 1.0;
            double limitSq = limit * limit;
            int bedY = bedPos.getY();
            boolean sneaking = this.serverSeesSneak() || this.sneakForBed();
            List<BedDefenderModule.ShellCell> pool = new ArrayList<>(steps.size());

            for (BedDefenderModule.Step step : steps) {
               BedDefenderModule.ShellCell candidate = this.admit(step.pos(), step.layer(), bedPos, partner, bedY, eye, limitSq, material, sneaking);
               if (candidate != null) {
                  pool.add(candidate);
               } else {
                  BlockPos capped = this.cappedPromotion(step.pos(), bedY, material, visited);
                  if (capped != null) {
                     BedDefenderModule.ShellCell above = this.admit(capped, step.layer(), bedPos, partner, bedY, eye, limitSq, material, sneaking);
                     if (above != null) {
                        pool.add(above);
                     }
                  }
               }
            }

            this.promoteRisers(pool, visited, bedPos, partner, outward, other, layers, bedY, eye, limitSq, material, sneaking);
            Set<BlockPos> blocked = new HashSet<>();

            for (BedDefenderModule.ShellCell candidate : pool) {
               if (!candidate.supported()) {
                  blocked.add(candidate.pos());
               }
            }

            Axis bedAxis = other.getAxis();
            List<BedDefenderModule.ShellCell> candidates = new ArrayList<>(pool.size());

            for (BedDefenderModule.ShellCell candidatex : pool) {
               int unlocks = 0;

               for (Direction direction : Direction.values()) {
                  if (blocked.contains(candidatex.pos().relative(direction))) {
                     unlocks++;
                  }
               }

               candidates.add(
                  new BedDefenderModule.ShellCell(
                     candidatex.pos(),
                     candidatex.layer(),
                     candidatex.elevated(),
                     candidatex.supported(),
                     unlocks,
                     onBedAxis(candidatex.pos(), bedPos, bedAxis, bedY),
                     candidatex.distSq()
                  )
               );
            }

            boolean preferSides = this.bool("prefer-sides");
            candidates.sort((left, right) -> {
               if (left.layer() != right.layer()) {
                  return Integer.compare(left.layer(), right.layer());
               } else {
                  boolean leftRefill = this.placedByUs.contains(left.pos());
                  if (leftRefill != this.placedByUs.contains(right.pos())) {
                     return leftRefill ? -1 : 1;
                  } else if (left.supported() != right.supported()) {
                     return left.supported() ? -1 : 1;
                  } else if (left.unlocks() != right.unlocks()) {
                     return Integer.compare(right.unlocks(), left.unlocks());
                  } else {
                     if (left.elevated() != right.elevated()) {
                        if (sneaking) {
                           return left.elevated() ? -1 : 1;
                        }

                        if (preferSides) {
                           return left.elevated() ? 1 : -1;
                        }
                     }

                     if (sneaking && left.axisEnd() != right.axisEnd()) {
                        return left.axisEnd() ? -1 : 1;
                     } else {
                        return Double.compare(left.distSq(), right.distSq());
                     }
                  }
               }
            });

            for (BedDefenderModule.ShellCell candidatex : candidates) {
               this.targets.add(candidatex.pos());
            }

            if (!bedPos.equals(this.progressBed)) {
               this.progressBed = bedPos.immutable();
               this.sinceProgressTicks = 0;
               this.completedBeds.remove(bedPos);
               this.completedBeds.remove(partner);
            }

            this.progressPartner = partner.immutable();
         }
      }
   }

   private void forgetProgress() {
      this.progressBed = null;
      this.progressPartner = null;
      this.sinceProgressTicks = 0;
   }

   private boolean chargeProgress() {
      if (this.progressBed != null && this.progressPartner != null) {
         if (++this.sinceProgressTicks < this.shellCompleteTicks()) {
            return false;
         } else {
            this.markShellComplete(this.progressBed, this.progressPartner);
            return true;
         }
      } else {
         return false;
      }
   }

   private int shellCompleteTicks() {
      return 20;
   }

   private static boolean onBedAxis(BlockPos pos, BlockPos bedPos, Axis axis, int bedY) {
      if (pos.getY() != bedY) {
         return false;
      } else {
         return axis == Axis.X ? pos.getZ() == bedPos.getZ() : pos.getX() == bedPos.getX();
      }
   }

   private BedDefenderModule.ShellCell admit(
      BlockPos pos, int layer, BlockPos bedPos, BlockPos partner, int bedY, Vec3 eye, double limitSq, ItemStack material, boolean sneaking
   ) {
      if (pos.equals(bedPos) || pos.equals(partner)) {
         return null;
      } else if (MC.level.isOutsideBuildHeight(pos) || !MC.level.isLoaded(pos)) {
         return null;
      } else if (!this.fillable(pos, material)) {
         return null;
      } else {
         double distSq = pos.distToCenterSqr(eye);
         return distSq > limitSq
            ? null
            : new BedDefenderModule.ShellCell(pos.immutable(), layer, pos.getY() > bedY, this.supportedNow(pos, eye, material, sneaking), 0, false, distSq);
      }
   }

   private BlockPos cappedPromotion(BlockPos pos, int bedY, ItemStack material, Set<BlockPos> visited) {
      if (pos.getY() != bedY) {
         return null;
      } else {
         BlockState state = MC.level.getBlockState(pos);
         if (!state.isAir() && !this.replacedInPlace(state, pos, material)) {
            AABB[] tops = RiptideFaceScan.faceRects(state, pos, Direction.UP, Integer.MAX_VALUE);
            if (tops.length == 0) {
               return null;
            } else {
               for (AABB top : tops) {
                  if (top.maxY - pos.getY() >= 0.5) {
                     return null;
                  }
               }

               BlockPos above = pos.above();
               return visited.add(above) ? above : null;
            }
         } else {
            return null;
         }
      }
   }

   private void promoteRisers(
      List<BedDefenderModule.ShellCell> pool,
      Set<BlockPos> visited,
      BlockPos bedPos,
      BlockPos partner,
      Direction outward,
      Direction other,
      int layers,
      int bedY,
      Vec3 eye,
      double limitSq,
      ItemStack material,
      boolean sneaking
   ) {
      if (layers == 1 && this.bool("top-riser")) {
         this.addRiser(pool, visited, bedPos.above(), bedPos.relative(outward).above(), bedPos, partner, bedY, eye, limitSq, material, sneaking);
         this.addRiser(pool, visited, partner.above(), partner.relative(other).above(), bedPos, partner, bedY, eye, limitSq, material, sneaking);
      }
   }

   private void addRiser(
      List<BedDefenderModule.ShellCell> pool,
      Set<BlockPos> visited,
      BlockPos top,
      BlockPos riser,
      BlockPos bedPos,
      BlockPos partner,
      int bedY,
      Vec3 eye,
      double limitSq,
      ItemStack material,
      boolean sneaking
   ) {
      for (BedDefenderModule.ShellCell candidate : pool) {
         if (candidate.pos().equals(top)) {
            if (!candidate.supported() && visited.add(riser)) {
               BedDefenderModule.ShellCell promoted = this.admit(riser, 2, bedPos, partner, bedY, eye, limitSq, material, sneaking);
               if (promoted != null) {
                  pool.add(promoted);
               }

               return;
            }

            return;
         }
      }
   }

   private boolean fillable(BlockPos pos, ItemStack material) {
      return this.replacedInPlace(MC.level.getBlockState(pos), pos, material);
   }

   private boolean replacedInPlace(BlockState state, BlockPos pos, ItemStack material) {
      if (!state.canBeReplaced()) {
         return false;
      } else if (!state.isAir() && material.getItem() instanceof BlockItem) {
         BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
         return new BlockPlaceContext(MC.player, InteractionHand.MAIN_HAND, material, hit).replacingClickedOnBlock();
      } else {
         return true;
      }
   }

   private boolean supportedNow(BlockPos cell, Vec3 eye, ItemStack material, boolean sneaking) {
      if (RiptideFaceScan.replaceableInPlace(cell, MC.level)) {
         return true;
      } else {
         for (Direction face : RiptideFaceScan.FACE_ORDER_UP_FIRST) {
            BlockPos support = cell.relative(face.getOpposite());
            BlockState state = MC.level.getBlockState(support);
            if (this.isPlaceableSupport(state, support, material, sneaking)
               && !RiptideFaceScan.selfOccluded(eye, support, face, MC.level)
               && this.eyeOutsideFace(state, support, face, eye)) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean eyeOutsideFace(BlockState state, BlockPos support, Direction face, Vec3 eye) {
      for (AABB rect : RiptideFaceScan.faceRects(state, support, face, Integer.MAX_VALUE)) {
         if (RiptideFaceScan.eyePastPlane(eye, rect, face) > 1.0E-4) {
            return true;
         }
      }

      return false;
   }

   private void markShellComplete(BlockPos bedPos, BlockPos partner) {
      this.dropTargets();
      this.forgetProgress();
      this.cachedBed = null;
      this.sinceSweep = this.integer("rescan-interval");
      this.completedBeds.add(bedPos.immutable());
      this.completedBeds.add(partner.immutable());

      while (this.completedBeds.size() > 16) {
         this.completedBeds.remove(this.completedBeds.iterator().next());
      }
   }

   private static void collectCells(BlockPos seed, int layers, Direction[] directions, Set<BlockPos> visited, List<BedDefenderModule.Step> out) {
      ArrayDeque<BedDefenderModule.Step> queue = new ArrayDeque<>();
      queue.add(new BedDefenderModule.Step(seed, 0));
      visited.add(seed);

      while (!queue.isEmpty()) {
         BedDefenderModule.Step step = queue.poll();
         if (step.layer() > 0) {
            out.add(step);
         }

         if (step.layer() < layers) {
            for (Direction direction : directions) {
               BlockPos next = step.pos().relative(direction);
               if (visited.add(next)) {
                  queue.add(new BedDefenderModule.Step(next, step.layer() + 1));
               }
            }
         }
      }
   }

   private BlockPos resolveBed(Vec3 eye, double radius) {
      if (this.sinceSweep < Integer.MAX_VALUE) {
         this.sinceSweep++;
      }

      BlockPos cached = this.cachedBed;
      if (cached != null && this.isDefendableBed(cached, eye, radius)) {
         return cached;
      } else {
         boolean lost = cached != null;
         this.cachedBed = null;
         if (!lost && this.sinceSweep < this.integer("rescan-interval")) {
            return null;
         } else {
            this.sinceSweep = 0;
            this.cachedBed = this.nearestBed(eye, radius, true);
            if (this.cachedBed == null && !this.completedBeds.isEmpty()) {
               this.completedBeds.clear();
               this.cachedBed = this.nearestBed(eye, radius, false);
            }

            return this.cachedBed;
         }
      }
   }

   private boolean isDefendableBed(BlockPos pos, Vec3 eye, double radius) {
      return pos.distToCenterSqr(eye) > radius * radius ? false : MC.level.getBlockState(pos).getBlock() instanceof BedBlock;
   }

   private BlockPos nearestBed(Vec3 eye, double radius, boolean skipCompleted) {
      int minX = Mth.floor(eye.x - radius);
      int maxX = Mth.ceil(eye.x + radius);
      int minZ = Mth.floor(eye.z - radius);
      int maxZ = Mth.ceil(eye.z + radius);
      int minY = Math.max(Mth.floor(eye.y - radius), MC.level.getMinY());
      int maxY = Math.min(Mth.ceil(eye.y + radius), MC.level.getMaxY());
      if (minY > maxY) {
         return null;
      } else {
         BlockPos best = null;
         double bestSq = radius * radius;

         for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            double distSq = pos.distToCenterSqr(eye);
            if (!(distSq > bestSq) && (!skipCompleted || !this.completedBeds.contains(pos)) && MC.level.getBlockState(pos).getBlock() instanceof BedBlock) {
               best = pos.immutable();
               bestSq = distSq;
            }
         }

         return best;
      }
   }

   private void runPlacement(int slot, ItemStack material, RiptideRotationUtil.Rotation wireRotation, RiptideRotationUtil.Rotation from) {
      if (!this.canPlan()) {
         this.standDown();
      } else if (this.throwableHeldThisTick()) {
         this.standDown();
         if (slot >= 0 && slot != MC.player.getInventory().getSelectedSlot()) {
            this.selectSlot(slot);
         }
      } else if (RiptideBlinkManager.holdsActionsWithoutMovement()) {
         this.standDown();
      } else if (this.targets.isEmpty() || slot < 0) {
         this.standDown();
         this.maybeSwitchBack();
      } else if (this.chargeProgress()) {
         this.standDown();
         this.maybeSwitchBack();
      } else {
         boolean sneaking = this.serverSeesSneak();
         this.ageGateStalls();
         BedDefenderModule.Plan plan = this.planNextTarget(from, material, null, sneaking);
         if (plan == null) {
            this.standDown();
            this.maybeSwitchBack();
         } else {
            boolean slotReady = slot == MC.player.getInventory().getSelectedSlot();
            BlockHitResult hit = null;
            if (wireRotation != null) {
               hit = this.wireRay(plan, wireRotation, sneaking);
               boolean sneakPending = plan.needsSneak() && !sneaking;
               if (hit == null && slotReady && !sneakPending && this.chargeGateStall(plan, wireRotation)) {
                  plan = this.planNextTarget(from, material, null, sneaking);
                  hit = plan == null ? null : this.wireRay(plan, wireRotation, sneaking);
               }
            }

            if (plan == null) {
               this.standDown();
               this.maybeSwitchBack();
            } else {
               this.idleTicks = 0;
               boolean fire = hit != null && slotReady && this.cadenceHolds();
               BedDefenderModule.Plan next = null;
               boolean aimed;
               if (fire) {
                  next = this.planNextTarget(from, material, plan.cell(), sneaking);
                  aimed = this.pumpAim(next != null ? next.goal() : plan.goal(), true);
               } else {
                  aimed = this.pumpAim(plan.goal(), false);
               }

               this.sneakRequested = this.sneakForBed() && (plan.needsSneak() || next != null && next.needsSneak());
               this.sneakRequestTick = RiptideSharedState.get().getClientTickCounter();
               if (fire && aimed) {
                  this.commit(plan, hit);
               } else {
                  if (!slotReady) {
                     this.selectSlot(slot);
                  }
               }
            }
         }
      }
   }

   private boolean pumpAim(RiptideRotationUtil.Rotation goal, boolean holdPitch) {
      RiptideKillAuraRotation.setTarget(this.id(), 30, goal);
      this.aimTick = RiptideSharedState.get().getClientTickCounter();
      if (!this.id().equals(RiptideKillAuraRotation.currentOwner())) {
         return false;
      } else {
         RiptideKillAuraRotation.update(this.id(), MC.player, 55.0F, holdPitch ? 0.0F : 55.0F, RiptideHumanRotation.MotionProfile.BED_SHELL);
         return true;
      }
   }

   private boolean chargeGateStall(BedDefenderModule.Plan plan, RiptideRotationUtil.Rotation wire) {
      double band = RiptideHumanRotation.settleBandDegrees(RiptideRotationUtil.sensitivityGcd());
      if (RiptideRotationUtil.angleTo(wire, plan.goal()) > band * 2.0) {
         return false;
      } else {
         BlockPos key = plan.cell().immutable();

         while (!this.gateStalls.containsKey(key) && this.gateStalls.size() >= 16) {
            this.gateStalls.remove(this.gateStalls.keySet().iterator().next());
         }

         BedDefenderModule.GateStall stall = this.gateStalls.computeIfAbsent(key, k -> new BedDefenderModule.GateStall());
         if (stall.idle > 1) {
            stall.charges = 0;
         }

         stall.idle = 0;
         if (++stall.charges < 8) {
            return false;
         } else {
            stall.charges = 0;
            stall.cooldown = 60;
            RiptideFaceScan.Intent refusal = plan.intent();
            if (!stall.banned.contains(refusal)) {
               stall.banned.add(refusal);
            }

            while (stall.banned.size() > 12) {
               stall.banned.remove(0);
            }

            return true;
         }
      }
   }

   private void ageGateStalls() {
      Iterator<Entry<BlockPos, BedDefenderModule.GateStall>> entries = this.gateStalls.entrySet().iterator();

      while (entries.hasNext()) {
         BedDefenderModule.GateStall stall = entries.next().getValue();
         if (stall.cooldown > 0) {
            stall.cooldown--;
         }

         stall.idle++;
         if (stall.idle > 1) {
            stall.charges = 0;
         }

         if (stall.cooldown == 0) {
            stall.banned.clear();
         }

         if (stall.charges == 0 && stall.cooldown == 0) {
            entries.remove();
         }
      }
   }

   private void refreshOptions(RiptideRotationUtil.Rotation from, ItemStack material) {
      long key = RiptideFaceScan.shellKey(this.targets, MC.level);
      if (this.shellKeyKnown && key == this.shellKey) {
         long ranks = this.rankKey();
         if (ranks != this.rankKey) {
            this.rankKey = ranks;
            this.refreshRanks();
         }
      } else {
         this.shellKey = key;
         this.rankKey = this.rankKey();
         this.shellKeyKnown = true;
         this.options.clear();
         this.cursor = 0;
         RiptideFaceScan.Request template = this.scanRequest(from, material, this.serverSeesSneak(), null);
         int[] ranks = new int[Math.min(4096, Math.max(16, this.targets.size() * 8))];
         int count = 0;

         for (int rank = 0; rank < this.targets.size() && this.options.size() < 4096; rank++) {
            RiptideFaceScan.options(template.cell(this.targets.get(rank)), this.options);
            if (this.options.size() > 4096) {
               this.options.subList(4096, this.options.size()).clear();
            }

            if (ranks.length < this.options.size()) {
               ranks = Arrays.copyOf(ranks, Math.max(this.options.size(), ranks.length * 2));
            }

            while (count < this.options.size()) {
               ranks[count++] = rank;
            }
         }

         this.optionRank = Arrays.copyOf(ranks, count);
         this.answers = new BedDefenderModule.Plan[this.options.size()];
         this.answerTick = new int[this.options.size()];
         Arrays.fill(this.answerTick, Integer.MIN_VALUE);
      }
   }

   private long rankKey() {
      long key = 1125899906842597L;

      for (BlockPos cell : this.targets) {
         key = key * 31L + cell.asLong();
      }

      return key;
   }

   private void refreshRanks() {
      Map<BlockPos, Integer> rank = new HashMap<>(this.targets.size() * 2);

      for (int i = 0; i < this.targets.size(); i++) {
         rank.put(this.targets.get(i), i);
      }

      for (int i = 0; i < this.options.size() && i < this.optionRank.length; i++) {
         Integer now = rank.get(this.options.get(i).cell());
         if (now != null) {
            this.optionRank[i] = now;
         }
      }
   }

   private BedDefenderModule.Plan planNextTarget(RiptideRotationUtil.Rotation from, ItemStack material, BlockPos skip, boolean sneaking) {
      int size = this.options.size();
      if (size == 0) {
         return null;
      } else {
         if (this.cursor >= size) {
            this.cursor = 0;
         }

         AABB playerBox = MC.player.getBoundingBox();
         Vec3 delta = MC.player.getDeltaMovement();
         BlockState placed = material.getItem() instanceof BlockItem blockItem ? blockItem.getBlock().defaultBlockState() : null;
         int tick = RiptideSharedState.get().getClientTickCounter();
         StringBuilder trace = this.bool("trace-faces") ? new StringBuilder() : null;
         RiptideFaceScan.Request request = this.scanRequest(from, material, sneaking, trace);
         RiptideFaceScan.Refusal[] refusal = new RiptideFaceScan.Refusal[1];
         BedDefenderModule.Plan best = null;
         float bestAngle = Float.MAX_VALUE;
         int bestRank = Integer.MAX_VALUE;
         int start = this.cursor;

         for (int step = 0; step < size; step++) {
            int index = (start + step) % size;
            RiptideFaceScan.Option option = this.options.get(index);
            BlockPos cell = option.cell();
            int rank = index < this.optionRank.length ? this.optionRank[index] : Integer.MAX_VALUE;
            if (!cell.equals(skip) && rank <= bestRank && !this.cellBlocked(cell, placed, playerBox, delta)) {
               BedDefenderModule.GateStall stall = this.gateStalls.get(cell);
               if (stall != null && stall.cooldown > 0 && stall.banned.contains(option.intent())) {
                  note(trace, option.face(), RiptideFaceScan.Refusal.BANNED);
                  this.cursor = index + 1 == size ? 0 : index + 1;
               } else {
                  BedDefenderModule.Plan rival = rank == bestRank ? best : null;
                  BedDefenderModule.Plan plan;
                  if (this.answerTick[index] == tick) {
                     plan = this.answers[index];
                  } else {
                     plan = this.examine(option, request, refusal, from, rival, bestAngle, trace);
                     if (this.visitStarved) {
                        this.cursor = index;
                        break;
                     }

                     if (!this.visitOutranked) {
                        this.answers[index] = plan;
                        this.answerTick[index] = tick;
                     }
                  }

                  this.cursor = index + 1 == size ? 0 : index + 1;
                  if (plan != null) {
                     float angle = RiptideRotationUtil.angleTo(from, plan.goal());
                     if (best == null || rank != bestRank || beatsIncumbent(plan.face() == Direction.UP, angle, best, bestAngle)) {
                        best = plan;
                        bestAngle = angle;
                        bestRank = rank;
                     }
                  }
               }
            } else {
               this.cursor = index + 1 == size ? 0 : index + 1;
            }
         }

         if (trace != null && this.traceDetail == null && trace.length() > 0) {
            this.traceDetail = trace.toString();
         }

         return best;
      }
   }

   private BedDefenderModule.Plan examine(
      RiptideFaceScan.Option option,
      RiptideFaceScan.Request request,
      RiptideFaceScan.Refusal[] refusal,
      RiptideRotationUtil.Rotation from,
      BedDefenderModule.Plan rival,
      float rivalAngle,
      StringBuilder trace
   ) {
      this.visitStarved = false;
      this.visitOutranked = false;
      boolean up = option.face() == Direction.UP;
      if (rival != null && rival.face() == Direction.UP && !up) {
         this.visitOutranked = true;
         note(trace, option.face(), RiptideFaceScan.Refusal.OUTRANKED);
         return null;
      } else {
         RiptideFaceScan.Aim aim = RiptideFaceScan.solve(option, request.cell(option.cell()), refusal);
         if (aim == null) {
            return null;
         } else if (rival != null && !beatsIncumbent(up, RiptideRotationUtil.angleTo(from, aim.goal()), rival, rivalAngle)) {
            this.visitOutranked = true;
            note(trace, option.face(), RiptideFaceScan.Refusal.OUTRANKED);
            return null;
         } else {
            RiptideFaceScan.Candidate candidate = RiptideFaceScan.probe(option, aim, request, refusal);
            if (candidate == null) {
               this.visitStarved = refusal[0] == RiptideFaceScan.Refusal.NO_BUDGET;
               return null;
            } else {
               return new BedDefenderModule.Plan(option.cell(), option.intent(), candidate);
            }
         }
      }
   }

   private RiptideFaceScan.Request scanRequest(RiptideRotationUtil.Rotation from, ItemStack material, boolean sneaking, StringBuilder trace) {
      Vec3 eye = MC.player.getEyePosition();
      return new RiptideFaceScan.Request(
            null, eye, MC.player.blockInteractionRange(), RiptideFaceScan.blockItem(material, MC.player, InteractionHand.MAIN_HAND)
         )
         .from(from)
         .sneaking(sneaking)
         .sneakAllowed(this.sneakForBed())
         .leadEye(eye.add(MC.player.getDeltaMovement()))
         .budget(this.rayBudget)
         .trace(trace);
   }

   private boolean sneakForBed() {
      return this.bool("sneak-for-bed") && this.sneakPressIsOnlyACrouch();
   }

   private boolean cellBlocked(BlockPos cell, BlockState placed, AABB playerBox, Vec3 delta) {
      Boolean known = this.obstructedCells.get(cell);
      if (known != null) {
         return known;
      } else {
         boolean blocked = this.cellObstructed(cell, placed, playerBox, delta);
         this.obstructedCells.put(cell, blocked);
         return blocked;
      }
   }

   private boolean cellObstructed(BlockPos cell, BlockState placed, AABB playerBox, Vec3 delta) {
      AABB box = new AABB(cell);
      if (box.intersects(playerBox) || box.intersects(playerBox.move(delta)) || box.intersects(playerBox.move(delta.scale(-1.0)))) {
         return true;
      } else {
         return placed != null
            ? !MC.level.isUnobstructed(placed, cell, CollisionContext.empty())
            : !MC.level.getEntities(MC.player, box, EntitySelector.NO_SPECTATORS).isEmpty();
      }
   }

   private static boolean beatsIncumbent(boolean up, float angle, BedDefenderModule.Plan best, float bestAngle) {
      boolean bestUp = best.face() == Direction.UP;
      return up != bestUp ? up : angle < bestAngle;
   }

   private static void note(StringBuilder trace, Direction face, RiptideFaceScan.Refusal reason) {
      if (trace != null && trace.length() < 96) {
         if (trace.length() > 0) {
            trace.append(' ');
         }

         trace.append(face.getSerializedName().charAt(0)).append(':').append(reason.code());
      }
   }

   private boolean isPlaceableSupport(BlockState state, BlockPos support, ItemStack material, boolean sneaking) {
      return !state.isAir() && !this.replacedInPlace(state, support, material) ? RiptideFaceScan.isPlaceableSupport(state, support, sneaking) : false;
   }

   private boolean serverSeesSneak() {
      return MC.player.isShiftKeyDown() && MC.player.getLastSentInput().shift()
         ? !MC.player.getMainHandItem().isEmpty() || !MC.player.getOffhandItem().isEmpty()
         : false;
   }

   private BlockHitResult wireRay(BedDefenderModule.Plan plan, RiptideRotationUtil.Rotation rotation, boolean sneaking) {
      ItemStack held = MC.player.getItemInHand(InteractionHand.MAIN_HAND);
      RiptideFaceScan.Request gate = this.scanRequest(rotation, held, sneaking, null).cell(plan.cell());
      return RiptideFaceScan.confirm(plan.candidate(), rotation, MC.player.getEyePosition(), MC.player.blockInteractionRange(), gate);
   }

   private boolean cadenceHolds() {
      return RiptideSharedState.get().getClientTickCounter() != this.lastPlaceTick;
   }

   private void commit(BedDefenderModule.Plan plan, BlockHitResult hit) {
      InteractionHand hand = InteractionHand.MAIN_HAND;
      if (!ModuleRegistry.shouldCancelUseExcept(hit, hand, this.id())) {
         if (RiptideHandArbiter.beginHandPacketGroup(this.id())) {
            try {
               if (!RiptideCombatClicker.queueUse(hit, hand)) {
                  return;
               }

               if (RiptidePlacementTick.claim(this.id())) {
                  this.bookCadence();
                  this.pendingPlacementCell = plan.cell();
                  return;
               }

               RiptideCombatClicker.cancel();
            } finally {
               RiptideHandArbiter.endHandPacketGroup(this.id());
            }
         }
      }
   }

   private void tickPendingPlacement() {
      if (this.pendingPlacementCell != null) {
         BlockPos cell = this.pendingPlacementCell;
         this.pendingPlacementCell = null;
         if (!MC.level.getBlockState(cell).canBeReplaced()) {
            this.bookPlacement(cell);
         }
      }
   }

   private void bookCadence() {
      this.lastPlaceTick = RiptideSharedState.get().getClientTickCounter();
   }

   @Override
   public boolean shouldCancelUse(HitResult hitResult, InteractionHand hand) {
      return this.lastPlaceTick == RiptideSharedState.get().getClientTickCounter();
   }

   @Override
   public boolean shouldCancelAttack(HitResult hitResult) {
      return hitResult instanceof EntityHitResult && this.id().equals(RiptideKillAuraRotation.currentOwner()) && RiptideKillAuraRotation.hasCurrentRotation();
   }

   private void bookPlacement(BlockPos cell) {
      this.targets.remove(cell);
      this.sinceProgressTicks = 0;
      BlockPos immutable = cell.immutable();
      this.placedByUs.remove(immutable);
      this.placedByUs.add(immutable);

      while (this.placedByUs.size() > 64) {
         this.placedByUs.remove(this.placedByUs.iterator().next());
      }
   }

   private int resolveHotbarSlot() {
      Inventory inventory = MC.player.getInventory();
      int selected = inventory.getSelectedSlot();
      int best = -1;

      for (int slot = 0; slot < 9; slot++) {
         if (!RiptideHandArbiter.slotReserved(slot, this.id())) {
            ItemStack stack = inventory.getItem(slot);
            if (this.isDefenceBlock(stack) && (best < 0 || outranks(stack, inventory.getItem(best), slot == selected, best == selected))) {
               best = slot;
            }
         }
      }

      return best;
   }

   private static boolean outranks(ItemStack candidate, ItemStack incumbent, boolean candidateHeld, boolean incumbentHeld) {
      float candidateHardness = hardnessOf(candidate);
      float incumbentHardness = hardnessOf(incumbent);
      boolean candidateUnbreakable = candidateHardness < 0.0F;
      boolean incumbentUnbreakable = incumbentHardness < 0.0F;
      if (candidateUnbreakable != incumbentUnbreakable) {
         return candidateUnbreakable;
      } else if (candidateHardness != incumbentHardness) {
         return candidateHardness > incumbentHardness;
      } else if (incumbentHeld) {
         return candidate.getCount() >= incumbent.getCount() + 16;
      } else {
         return candidateHeld ? candidate.getCount() + 16 > incumbent.getCount() : candidate.getCount() > incumbent.getCount();
      }
   }

   private static float hardnessOf(ItemStack stack) {
      return stack.getItem() instanceof BlockItem blockItem ? blockItem.getBlock().defaultDestroyTime() : 0.0F;
   }

   private void selectSlot(int slot) {
      if (RiptideHandArbiter.beginHandPacketGroup(this.id())) {
         try {
            int selected = MC.player.getInventory().getSelectedSlot();
            if (this.originalSlot < 0 && this.bool("switch-back")) {
               this.originalSlot = selected;
            }

            RiptideInventoryHelper.selectHotbarSlot(MC, slot);
            this.switchedToSlot = slot;
         } finally {
            RiptideHandArbiter.endHandPacketGroup(this.id());
         }
      }
   }

   private void maybeSwitchBack() {
      if (this.originalSlot >= 0) {
         if (!this.bool("switch-back")) {
            this.originalSlot = -1;
            this.switchedToSlot = -1;
         } else if (this.switchedToSlot >= 0 && MC.player.getInventory().getSelectedSlot() != this.switchedToSlot) {
            this.originalSlot = -1;
            this.switchedToSlot = -1;
         } else if (++this.idleTicks >= 6) {
            if (RiptideHandArbiter.beginHandPacketGroup(this.id())) {
               try {
                  RiptideInventoryHelper.selectHotbarSlot(MC, this.originalSlot);
                  this.originalSlot = -1;
                  this.switchedToSlot = -1;
                  this.idleTicks = 0;
               } finally {
                  RiptideHandArbiter.endHandPacketGroup(this.id());
               }
            }
         }
      }
   }

   private boolean isDefenceBlock(ItemStack stack) {
      if (!(stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem)) {
         return false;
      } else if (!stack.isItemEnabled(MC.level.enabledFeatures())) {
         return false;
      } else {
         Block block = blockItem.getBlock();
         boolean listed = this.filteredBlocks().contains(block);
         if ("Whitelist".equals(this.choice("filter-mode")) != listed) {
            return false;
         } else {
            return block.defaultBlockState().isCollisionShapeFullBlock(MC.level, BlockPos.ZERO)
               ? true
               : this.bool("allow-chests") && block instanceof AbstractChestBlock;
         }
      }
   }

   private Set<Block> filteredBlocks() {
      String raw = this.value("blocks");
      if (raw.equals(this.cachedFilterRaw)) {
         return this.cachedFilterBlocks;
      } else {
         Set<Block> blocks = new HashSet<>();

         for (String entry : this.list("blocks")) {
            Identifier identifier = Identifier.tryParse(RegistryListCodec.normalizeId(entry));
            if (identifier != null) {
               BuiltInRegistries.BLOCK.getOptional(identifier).ifPresent(blocks::add);
            }
         }

         this.cachedFilterRaw = raw;
         this.cachedFilterBlocks = Set.copyOf(blocks);
         return this.cachedFilterBlocks;
      }
   }

   private static final class GateStall {
      private int charges;
      private int cooldown;
      private int idle;
      private final List<RiptideFaceScan.Intent> banned = new ArrayList<>();
   }

   private record Plan(BlockPos cell, RiptideFaceScan.Intent intent, RiptideFaceScan.Candidate candidate) {
      Direction face() {
         return this.candidate.option().face();
      }

      RiptideRotationUtil.Rotation goal() {
         return this.candidate.aim().goal();
      }

      boolean needsSneak() {
         return this.candidate.option().requiresSneak();
      }
   }

   private record ShellCell(BlockPos pos, int layer, boolean elevated, boolean supported, int unlocks, boolean axisEnd, double distSq) {
   }

   private record Step(BlockPos pos, int layer) {
   }
}
