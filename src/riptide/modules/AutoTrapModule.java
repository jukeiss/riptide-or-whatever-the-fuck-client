package riptide.modules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Attackable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.RegistryListSetting;
import riptide.util.RiptideCombatClicker;
import riptide.util.RiptideFaceScan;
import riptide.util.RiptideHandArbiter;
import riptide.util.RiptideInventoryHelper;
import riptide.util.RiptideKillAuraRotation;
import riptide.util.RiptidePlacementTick;
import riptide.util.RiptideRotationUtil;
import riptide.util.RiptideServerRotationView;
import riptide.util.RiptideSharedState;
import riptide.util.RiptideSilentAim;

public final class AutoTrapModule extends Module implements RiptideSilentAim.Owner {
   private static final double SIMULATION_DISTANCE = 10.0;
   private static final int SIMULATION_TICKS = 25;
   private static final int SIMULATION_HISTORY = 10;
   private static final int MIN_EVIDENCE = 5;
   private static final double MAX_LANDING_SPREAD = 1.5;
   private static final int LEAD_TICKS = 4;
   private static final double STATIONARY_SPEED_SQR = 0.001;
   private static final int LOOK_AHEAD_TICKS = 5;
   private static final double MAX_SWEEP_SIZE = 30.0;
   private static final int GROUND_SNAP_BLOCKS = 3;
   private static final double VERTICAL_DRAG = 0.98;
   private static final int MAX_COMBAT_WAIT_TICKS = 40;
   private static final int PLAN_DECAY_TICKS = 10;
   private static final int GATE_PATIENCE_TICKS = 10;
   private static final int COMBO_MAX_WAIT_TICKS = 10;
   private static final int LAVA_PICKUP_TICKS = 30;
   private static final int LAVA_PICKUP_GIVE_UP_TICKS = 200;
   private static final long PLACE_FLOOR_MS = 100L;
   private static final int PLACE_JITTER_MS = 25;
   private static final long PLACE_CLOCK_SLACK_MS = 3L;
   private static final int SWITCH_BACK_TICKS = 2;
   private static final float ROTATION_MATCH_EPSILON = 0.05F;
   private static final double MIN_DISTANCE_SQR = 1.0;
   private static final Set<Item> WEB_ITEMS = Set.of(Items.COBWEB);
   private static final Set<Block> WEB_BLOCKS = Set.of(Blocks.COBWEB);
   private static final Set<Item> IGNITE_ITEMS = Set.of(Items.LAVA_BUCKET, Items.FLINT_AND_STEEL);
   private static final Set<Block> IGNITE_BLOCKS = Set.of(Blocks.LAVA, Blocks.FIRE);
   private final Map<Integer, ArrayDeque<AutoTrapModule.Landing>> landings = new LinkedHashMap<>();
   private final Map<Integer, Integer> gateMisses = new LinkedHashMap<>();
   private final Random random = new Random();
   private volatile AutoTrapModule.Plan currentPlan;
   private int planDecayTicks;
   private int lockedTargetId = -1;
   private AutoTrapModule.LavaSource lavaSource;
   private int comboTargetId = -1;
   private int comboWaitTicks;
   private int combatWaitTicks;
   private int delayTicks;
   private int lastPlaceTick = Integer.MIN_VALUE;
   private long lastPlaceNanos = Long.MIN_VALUE;
   private int placeJitterMs;
   private int previousSlot = -1;
   private int trapSlot = -1;
   private int switchBackTicks;
   private int hotbarChangeTick = Integer.MIN_VALUE;
   private String cachedEntityListSource;
   private Set<String> cachedEntityIds = Set.of();

   AutoTrapModule() {
      super("auto-trap", "AutoTrap", ModuleCategory.COMBAT, "Traps enemies in blocks.");
      this.add(new IntSetting("delay", "Delay", 20, 0, 400, 5).unit("ticks").description("Cooldown after a placed trap").build());
      this.add(new BoolSetting("switch-back", "Switch Back", true).description("Return to previous hotbar slot").build());
      this.add(new BoolSetting("web", "AutoWeb", true).group("AutoWeb").description("Place cobwebs; tried before Ignite").build());
      this.add(new BoolSetting("ignite", "Ignite", true).group("Ignite").description("Place lava or set fire").build());
      this.add(new BoolSetting("predict", "Predict Landing", true).group("Target").description("Trap the predicted landing spot").build());
      this.add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player").group("Target").description("Entity types to trap").build());
      this.add(new IntSetting("fov", "FOV", 180, 0, 180, 5).group("Target").unit("degrees").description("Cone width around crosshair").build());
      this.add(new IntSetting("hurt-time", "Hurt Time", 10, 0, 10, 1).group("Target").unit("ticks").description("Maximum target hurt time").build());
      this.add(new ChoiceSetting("priority", "Priority", "Type", "Type", "Health", "Distance").group("Target").description("Main target sort key").build());
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
   }

   @Override
   public void onGameLeft() {
      this.resetRuntime();
      if (this.ownsRotation()) {
         RiptideKillAuraRotation.reset();
      }

      RiptideHandArbiter.releaseAll(this.id());
   }

   private void resetRuntime() {
      this.currentPlan = null;
      this.planDecayTicks = 0;
      this.lockedTargetId = -1;
      this.lavaSource = null;
      this.comboTargetId = -1;
      this.comboWaitTicks = 0;
      this.combatWaitTicks = 0;
      this.delayTicks = 0;
      this.previousSlot = -1;
      this.trapSlot = -1;
      this.switchBackTicks = 0;
      this.hotbarChangeTick = Integer.MIN_VALUE;
      this.landings.clear();
      this.gateMisses.clear();
   }

   @Override
   public String info() {
      AutoTrapModule.Plan plan = this.currentPlan;
      return plan == null ? "" : (plan.pickup() ? "Pickup" : (plan.web() ? "Web" : "Ignite"));
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
      if (MC != null && MC.player != null && MC.level != null && MC.gameMode != null) {
         this.tickSwitchBack();
         this.runSimulations();
         if (!this.canRun()) {
            this.standDown();
         } else {
            if (this.comboTargetId >= 0 && ++this.comboWaitTicks > 10) {
               this.comboTargetId = -1;
               this.delayTicks = this.integer("delay");
            }

            if (this.delayTicks > 0) {
               this.delayTicks--;
               this.currentPlan = null;
               this.planDecayTicks = 0;
               this.pumpRotation(null);
               this.armSwitchBack();
            } else {
               AutoTrapModule.Plan plan = this.currentPlan;
               boolean holdingForAura = plan != null && this.waitForCombatTiming();
               if (plan != null && !holdingForAura) {
                  RiptideRotationUtil.Rotation placed = this.tryPlace(plan);
                  if (placed != null) {
                     this.combatWaitTicks = 0;
                     this.planDecayTicks = 0;
                     this.currentPlan = null;
                     if (plan.pickup()) {
                        this.comboTargetId = -1;
                     } else if (plan.web() && this.bool("ignite") && this.findSlot(IGNITE_ITEMS) != null && !this.targetOnFire(plan.targetId())) {
                        this.comboTargetId = plan.targetId();
                        this.comboWaitTicks = 0;
                     } else {
                        this.comboTargetId = -1;
                        this.delayTicks = this.integer("delay");
                     }

                     this.pumpRotation(placed, true);
                     this.armSwitchBack();
                     return;
                  }
               }

               AutoTrapModule.Plan planned = this.plan();
               if (planned != null) {
                  this.currentPlan = planned;
                  this.planDecayTicks = 0;
               } else if (this.currentPlan != null && (this.targetHandled(this.currentPlan) || ++this.planDecayTicks >= 10)) {
                  this.currentPlan = null;
                  this.lockedTargetId = -1;
                  this.planDecayTicks = 0;
               }

               AutoTrapModule.Plan live = this.currentPlan;
               this.pumpRotation(!holdingForAura && live != null ? live.rotation() : null);
               this.armSwitchBack();
            }
         }
      } else {
         this.resetRuntime();
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
      } else {
         return MC.gui == null || MC.gui.screen() != null || MC.gui.overlay() != null
            ? false
            : !AutoTotemModule.operationActive() && !AutoArmorModule.operationActive();
      }
   }

   private void standDown() {
      this.currentPlan = null;
      this.planDecayTicks = 0;
      this.combatWaitTicks = 0;
      this.lockedTargetId = -1;
      this.comboTargetId = -1;
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

   private void pumpRotation(RiptideRotationUtil.Rotation goal) {
      this.pumpRotation(goal, false);
   }

   private void pumpRotation(RiptideRotationUtil.Rotation goal, boolean pinQuiet) {
      if (goal != null) {
         RiptideKillAuraRotation.setTarget(this.id(), 15, goal);
      } else if (this.ownsRotation()) {
         RiptideKillAuraRotation.beginWindDown(this.id());
      }

      if (this.ownsRotation()) {
         RiptideKillAuraRotation.update(this.id(), MC.player, pinQuiet);
      }
   }

   private boolean waitForCombatTiming() {
      boolean wait = this.killAuraHasTarget() && (MC.player.getAttackStrengthScale(0.5F) > 0.9F || this.wouldDoCriticalHit());
      this.combatWaitTicks = wait ? this.combatWaitTicks + 1 : 0;
      return wait && this.combatWaitTicks < 40;
   }

   private boolean killAuraHasTarget() {
      return ModuleRegistry.get("kill-aura") instanceof KillAuraModule aura && aura.isEnabled() && aura.currentTarget() != null;
   }

   private boolean wouldDoCriticalHit() {
      return MC.player.fallDistance > 0.0
         && !MC.player.onGround()
         && !MC.player.onClimbable()
         && !MC.player.isInWater()
         && !MC.player.hasEffect(MobEffects.BLINDNESS)
         && !MC.player.isPassenger();
   }

   private AutoTrapModule.Plan plan() {
      List<LivingEntity> enemies = this.targets();
      if (this.lockedTargetId >= 0) {
         boolean stillValid = false;

         for (LivingEntity enemy : enemies) {
            if (enemy.getId() == this.lockedTargetId) {
               stillValid = true;
               break;
            }
         }

         if (!stillValid) {
            this.lockedTargetId = -1;
         }
      }

      AutoTrapModule.Plan pickup = this.planLavaPickup();
      if (pickup != null) {
         return pickup;
      } else if (enemies.isEmpty()) {
         return null;
      } else if (this.comboTargetId < 0) {
         AutoTrapModule.Plan plan = this.bool("web") ? this.planTrap(enemies, true) : null;
         if (plan == null && this.bool("ignite")) {
            plan = this.planTrap(enemies, false);
         }

         return plan;
      } else {
         LivingEntity combo = null;

         for (LivingEntity enemyx : enemies) {
            if (enemyx.getId() == this.comboTargetId) {
               combo = enemyx;
               break;
            }
         }

         if (combo != null && !combo.isOnFire() && this.bool("ignite")) {
            return this.planTrap(List.of(combo), false);
         } else {
            this.comboTargetId = -1;
            this.delayTicks = this.integer("delay");
            return null;
         }
      }
   }

   private boolean targetOnFire(int targetId) {
      return MC.level.getEntity(targetId) instanceof LivingEntity living && living.isOnFire();
   }

   private AutoTrapModule.Plan planLavaPickup() {
      AutoTrapModule.LavaSource source = this.lavaSource;
      if (source == null) {
         return null;
      } else {
         BlockPos cell = source.cell();
         if (!isLavaSource(MC.level.getBlockState(cell))) {
            this.lavaSource = null;
            return null;
         } else {
            int tick = RiptideSharedState.get().getClientTickCounter();
            if (tick - source.placedTick() >= 200) {
               this.lavaSource = null;
               return null;
            } else if (!this.targetOnFire(source.targetId()) && MC.level.getEntity(source.targetId()) != null && tick - source.placedTick() < 30) {
               return null;
            } else {
               ItemStack stack = source.hand() == InteractionHand.OFF_HAND ? MC.player.getOffhandItem() : MC.player.getInventory().getItem(source.hotbarSlot());
               if (!stack.is(Items.BUCKET)) {
                  this.lavaSource = null;
                  return null;
               } else {
                  if (source.hand() == InteractionHand.OFF_HAND) {
                     if (RiptideHandArbiter.offhandClaimedByOther(this.id()) || RiptideCombatClicker.mainHandWouldPreempt()) {
                        return null;
                     }
                  } else if (RiptideHandArbiter.slotReserved(source.hotbarSlot(), this.id())) {
                     return null;
                  }

                  Vec3 eye = MC.player.getEyePosition();
                  if (Vec3.atCenterOf(cell).distanceToSqr(eye) > this.reach() * this.reach()) {
                     return null;
                  } else {
                     RiptideRotationUtil.Rotation rotation = RiptideRotationUtil.lookingAt(Vec3.atCenterOf(cell), eye);
                     return Math.abs(rotation.pitch()) > RiptideFaceScan.goalPitchLimit()
                        ? null
                        : new AutoTrapModule.Plan(false, source.targetId(), new AABB(cell), source.hotbarSlot(), source.hand(), rotation, true);
                  }
               }
            }
         }
      }
   }

   private static boolean isLavaSource(BlockState state) {
      return state.is(Blocks.LAVA) && state.getFluidState().isSource();
   }

   private boolean targetHandled(AutoTrapModule.Plan plan) {
      if (plan.pickup()) {
         return this.lavaSource == null;
      } else if (MC.level.getEntity(plan.targetId()) instanceof LivingEntity living && living.isAlive()) {
         boolean webDone = !this.bool("web") || this.findSlot(WEB_ITEMS) == null || this.boxTouchesWeb(living.getBoundingBox());
         boolean igniteDone = !this.bool("ignite") || this.findSlot(IGNITE_ITEMS) == null || living.isOnFire();
         return webDone && igniteDone;
      } else {
         return true;
      }
   }

   private boolean boxTouchesWeb(AABB box) {
      BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
      BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);

      for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
         if (WEB_BLOCKS.contains(MC.level.getBlockState(pos).getBlock())) {
            return true;
         }
      }

      return false;
   }

   private AutoTrapModule.Plan planTrap(List<LivingEntity> enemies, boolean web) {
      AutoTrapModule.TrapSlot slot = this.findSlot(web ? WEB_ITEMS : IGNITE_ITEMS);
      if (slot == null) {
         return null;
      } else {
         Set<Block> trapBlocks = web ? WEB_BLOCKS : IGNITE_BLOCKS;
         BlockState placedState = this.placedState(web, slot.stack());
         Vec3 eye = MC.player.getEyePosition();
         double reach = this.reach();

         for (LivingEntity target : enemies) {
            if (web || !target.isOnFire()) {
               Vec3 trapPos = this.findPosForTrap(target, target.getId() == this.lockedTargetId);
               if (trapPos != null) {
                  trapPos = this.grounded(trapPos);
                  BlockPos origin = BlockPos.containing(trapPos);
                  if (!trapBlocks.contains(MC.level.getBlockState(origin).getBlock())) {
                     EntityDimensions dimensions = target.getDimensions(web ? Pose.STANDING : target.getPose());
                     boolean mustBeOnGround = slot.stack().is(web ? Items.COBWEB : Items.FLINT_AND_STEEL);
                     Vec3 velocity = target.position().subtract(target.oldPosition());

                     for (BlockPos offset : this.findOffsets(trapPos, dimensions, velocity, mustBeOnGround, web, trapBlocks, eye)) {
                        RiptideRotationUtil.Rotation rotation = this.solvePlacement(origin.offset(offset), placedState, eye, reach, slot.stack(), slot.hand());
                        if (rotation != null) {
                           this.lockedTargetId = target.getId();
                           return new AutoTrapModule.Plan(
                              web, target.getId(), dimensions.makeBoundingBox(trapPos), slot.hotbarSlot(), slot.hand(), rotation, false
                           );
                        }
                     }
                  }
               }
            }
         }

         return null;
      }
   }

   private double reach() {
      return MC.player.blockInteractionRange();
   }

   private BlockState placedState(boolean web, ItemStack stack) {
      if (web) {
         return Blocks.COBWEB.defaultBlockState();
      } else {
         return stack.is(Items.LAVA_BUCKET) ? Blocks.LAVA.defaultBlockState() : Blocks.FIRE.defaultBlockState();
      }
   }

   private AutoTrapModule.TrapSlot findSlot(Set<Item> items) {
      ItemStack offhand = MC.player.getOffhandItem();
      if (items.contains(offhand.getItem())
         && (this.lavaSource == null || !offhand.is(Items.LAVA_BUCKET))
         && !RiptideHandArbiter.offhandClaimedByOther(this.id())
         && !RiptideCombatClicker.mainHandWouldPreempt()) {
         return new AutoTrapModule.TrapSlot(-1, InteractionHand.OFF_HAND, offhand);
      } else {
         int selected = MC.player.getInventory().getSelectedSlot();
         int best = -1;
         int bestDistance = Integer.MAX_VALUE;

         for (int slot = 0; slot < 9; slot++) {
            if (!RiptideHandArbiter.slotReserved(slot, this.id())) {
               ItemStack stack = MC.player.getInventory().getItem(slot);
               if (items.contains(stack.getItem()) && (this.lavaSource == null || !stack.is(Items.LAVA_BUCKET))) {
                  int distance = Math.abs(slot - selected);
                  if (distance < bestDistance) {
                     bestDistance = distance;
                     best = slot;
                  }
               }
            }
         }

         return best < 0 ? null : new AutoTrapModule.TrapSlot(best, InteractionHand.MAIN_HAND, MC.player.getInventory().getItem(best));
      }
   }

   private List<LivingEntity> targets() {
      double minimum = 1.0;
      double reach = this.reach();
      double maximum = reach * reach;
      Vec3 eyes = MC.player.getEyePosition();
      List<LivingEntity> found = new ArrayList<>();

      for (Entity entity : MC.level.entitiesForRendering()) {
         if (entity instanceof LivingEntity living && this.validate(living, eyes, minimum, maximum)) {
            found.add(living);
         }
      }

      if (found.size() > 1) {
         found.sort(this.targetComparator());
      }

      return found;
   }

   private boolean validate(LivingEntity entity, Vec3 eyes, double minimumSqr, double maximumSqr) {
      if (entity == MC.player || entity.isRemoved() || !entity.isAlive()) {
         return false;
      } else if (!(entity instanceof Attackable)) {
         return false;
      } else if (!EntitySelector.CAN_BE_PICKED.test(entity)) {
         return false;
      } else if (entity.hasPassenger(MC.player)) {
         return false;
      } else if (entity.hurtTime > this.integer("hurt-time")) {
         return false;
      } else if (RiptideAntiBot.suppress(entity)) {
         return false;
      } else if (TeamsModule.combatExcluded(entity, "killaura")) {
         return false;
      } else if (entity instanceof Player player && player.isSleeping()) {
         return false;
      } else if (!this.matchesEntity(entity)) {
         return false;
      } else {
         double distanceSqr = this.boxedDistanceToPlayerSqr(entity);
         return !(distanceSqr < minimumSqr) && !(distanceSqr > maximumSqr)
            ? this.integer("fov") >= 180 || this.crosshairAngleTo(entity, eyes) <= this.integer("fov") * 0.5F
            : false;
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

   private Comparator<LivingEntity> targetComparator() {
      Comparator<LivingEntity> byType = Comparator.comparingInt(entity -> entity instanceof Player ? 0 : 1);
      Comparator<LivingEntity> byHealth = Comparator.comparingDouble(entity -> entity.getHealth() + entity.getAbsorptionAmount());
      Comparator<LivingEntity> byDistance = Comparator.comparingDouble(this::boxedDistanceToPlayerSqr);
      String var4 = this.choice("priority");

      return switch (var4) {
         case "Health" -> byHealth.thenComparing(byType).thenComparing(byDistance);
         case "Distance" -> byDistance.thenComparing(byType).thenComparing(byHealth);
         default -> byType.thenComparing(byHealth).thenComparing(byDistance);
      };
   }

   private double boxedDistanceToPlayerSqr(Entity entity) {
      return entity.getBoundingBox().inflate(entity.getPickRadius()).distanceToSqr(MC.player.getEyePosition());
   }

   private float crosshairAngleTo(Entity entity, Vec3 eyes) {
      RiptideRotationUtil.Rotation toCenter = RiptideRotationUtil.lookingAt(entity.getBoundingBox().getCenter(), eyes);
      return RiptideRotationUtil.rotationAngleTo(RiptideRotationUtil.playerRotation(MC.player), toCenter);
   }

   private void runSimulations() {
      if (!this.bool("predict")) {
         this.landings.clear();
      } else {
         Set<Integer> seen = new HashSet<>();
         double maximumSqr = 100.0;

         for (Player player : MC.level.players()) {
            if (player != MC.player && !player.isRemoved() && player.isAlive() && !(player.distanceToSqr(MC.player) > maximumSqr)) {
               seen.add(player.getId());
               ArrayDeque<AutoTrapModule.Landing> history = this.landings.computeIfAbsent(player.getId(), key -> new ArrayDeque<>());
               history.addLast(this.simulate(player));

               while (history.size() > 10) {
                  history.removeFirst();
               }
            }
         }

         this.landings.keySet().retainAll(seen);
         this.gateMisses.keySet().retainAll(seen);
      }
   }

   private AutoTrapModule.Landing simulate(Player player) {
      Vec3 current = player.position();
      Vec3 velocity = current.subtract(player.oldPosition());
      boolean stationary = velocity.lengthSqr() < 0.001;
      if (player.onGround() && stationary) {
         Vec3[] still = new Vec3[4];
         Arrays.fill(still, current);
         return new AutoTrapModule.Landing(null, 0, current, true, still);
      } else {
         AABB box = player.getBoundingBox();
         Vec3 position = current;
         double gravity = player.getAttributeValue(Attributes.GRAVITY);
         boolean wasAirborne = !player.onGround();
         boolean freeFalling = !player.onClimbable() && !player.isInWater();
         Vec3[] leadPath = new Vec3[4];
         Vec3 landing = null;
         int landingTick = 0;

         for (int tick = 1; tick <= 25; tick++) {
            List<VoxelShape> shapes = this.collisionShapes(player, box, velocity);
            double dy = Shapes.collide(Axis.Y, box, shapes, velocity.y);
            box = box.move(0.0, dy, 0.0);
            double dx = Shapes.collide(Axis.X, box, shapes, velocity.x);
            box = box.move(dx, 0.0, 0.0);
            double dz = Shapes.collide(Axis.Z, box, shapes, velocity.z);
            box = box.move(0.0, 0.0, dz);
            position = new Vec3(position.x + dx, position.y + dy, position.z + dz);
            if (tick <= 4) {
               leadPath[tick - 1] = position;
            }

            boolean onGround = velocity.y <= 0.0 && dy != velocity.y;
            if (wasAirborne && onGround && landing == null) {
               landing = position;
               landingTick = tick;
               if (tick >= 4) {
                  return new AutoTrapModule.Landing(position, tick, current, false, leadPath);
               }
            }

            wasAirborne = !onGround;
            double verticalSpeed = onGround ? 0.0 : (freeFalling ? (velocity.y - gravity) * 0.98 : velocity.y);
            velocity = new Vec3(dx == velocity.x ? velocity.x : 0.0, verticalSpeed, dz == velocity.z ? velocity.z : 0.0);
            if (onGround && velocity.horizontalDistanceSqr() < 1.0E-6) {
               break;
            }
         }

         for (int i = 0; i < leadPath.length; i++) {
            if (leadPath[i] == null) {
               leadPath[i] = position;
            }
         }

         return new AutoTrapModule.Landing(landing, landingTick, current, stationary, leadPath);
      }
   }

   private List<VoxelShape> collisionShapes(Entity entity, AABB box, Vec3 velocity) {
      List<VoxelShape> shapes = new ArrayList<>();

      for (VoxelShape shape : MC.level.getBlockCollisions(entity, box.expandTowards(velocity))) {
         shapes.add(shape);
      }

      return shapes;
   }

   private Vec3 findPosForTrap(LivingEntity target, boolean locked) {
      if (target instanceof Player && this.bool("predict")) {
         ArrayDeque<AutoTrapModule.Landing> history = this.landings.get(target.getId());
         if (history != null && !history.isEmpty()) {
            AutoTrapModule.Landing last = history.peekLast();
            if (stationaryOverWindow(history)) {
               this.gateMisses.remove(target.getId());
               return last.current();
            } else {
               int leadTicks = adaptiveLeadTicks(history);
               boolean longFall = last.landing() != null && last.ticksToGround() > 4;
               List<Vec3> positions = new ArrayList<>();

               for (AutoTrapModule.Landing entry : history) {
                  Vec3 future = longFall ? entry.landing() : entry.leadAt(leadTicks);
                  if (future != null) {
                     positions.add(future);
                  }
               }

               if (positions.size() < 5) {
                  return locked ? last.current() : this.gateFallback(target, last, leadTicks);
               } else {
                  Vec3 average = Vec3.ZERO;

                  for (Vec3 position : positions) {
                     average = average.add(position);
                  }

                  average = average.scale(1.0 / positions.size());
                  double squared = 0.0;

                  for (Vec3 position : positions) {
                     squared += position.subtract(average).lengthSqr();
                  }

                  double spread = Math.sqrt(squared / positions.size());
                  if (spread < 1.5) {
                     this.gateMisses.remove(target.getId());
                     return positions.get(positions.size() - 1);
                  } else {
                     return this.gateFallback(target, last, leadTicks);
                  }
               }
            }
         } else {
            return locked ? target.position() : this.gateFallback(target, null, 4);
         }
      } else {
         return target.position();
      }
   }

   private static boolean stationaryOverWindow(ArrayDeque<AutoTrapModule.Landing> history) {
      if (history.peekLast().stationary()) {
         return true;
      } else {
         int elapsed = history.size() - 1;
         if (elapsed <= 0) {
            return false;
         } else {
            double perTickSqr = history.peekFirst().current().distanceToSqr(history.peekLast().current()) / (elapsed * elapsed);
            return perTickSqr < 0.001;
         }
      }
   }

   private static int adaptiveLeadTicks(ArrayDeque<AutoTrapModule.Landing> history) {
      Vec3 previous = null;
      double walked = 0.0;
      double displacementX = 0.0;
      double displacementZ = 0.0;

      for (AutoTrapModule.Landing entry : history) {
         Vec3 current = entry.current();
         if (previous != null) {
            double dx = current.x - previous.x;
            double dz = current.z - previous.z;
            walked += Math.sqrt(dx * dx + dz * dz);
            displacementX += dx;
            displacementZ += dz;
         }

         previous = current;
      }

      if (walked < 1.0E-6) {
         return 4;
      } else {
         double straight = Math.sqrt(displacementX * displacementX + displacementZ * displacementZ) / walked;
         return 1 + (int)Math.round(straight * 3.0);
      }
   }

   private Vec3 gateFallback(LivingEntity target, AutoTrapModule.Landing last, int leadTicks) {
      int misses = this.gateMisses.getOrDefault(target.getId(), 0) + 1;
      this.gateMisses.put(target.getId(), misses);
      if (misses < 10) {
         return null;
      } else if (last != null) {
         return last.landing() != null && last.ticksToGround() > 4 ? last.landing() : last.leadAt(leadTicks);
      } else {
         return target.position();
      }
   }

   private List<BlockPos> findOffsets(
      Vec3 position, EntityDimensions dimensions, Vec3 velocity, boolean mustBeOnGround, boolean web, Set<Block> trapBlocks, Vec3 eye
   ) {
      BlockPos origin = BlockPos.containing(position);
      AABB start = dimensions.makeBoundingBox(position).move(-origin.getX(), -origin.getY(), -origin.getZ());
      AABB end = start.move(velocity.x * 5.0, 0.0, velocity.z * 5.0);
      if (velocity.horizontalDistance() * 5.0 > 30.0) {
         return this.placeable(origin, BlockPos.ZERO, mustBeOnGround, web, trapBlocks) ? List.of(BlockPos.ZERO) : List.of();
      } else {
         record Ranked(BlockPos offset, double rank, double eyeDistanceSqr) {
         }

         List<Ranked> ranked = new ArrayList<>();
         int minX = Mth.floor(start.minX);
         int maxX = Mth.ceil(start.maxX) - 1;
         int minY = Mth.floor(start.minY);
         int maxY = Mth.ceil(start.maxY) - 1;
         int minZ = Mth.floor(start.minZ);
         int maxZ = Mth.ceil(start.maxZ) - 1;

         for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
               for (int z = minZ; z <= maxZ; z++) {
                  BlockPos offset = new BlockPos(x, y, z);
                  AABB unit = new AABB(offset);
                  if ((start.intersects(unit) || end.intersects(unit)) && this.placeable(origin, offset, mustBeOnGround, web, trapBlocks)) {
                     double rank = overlap(start, unit) + overlap(end, unit) * 0.5;
                     ranked.add(new Ranked(offset, rank, Vec3.atCenterOf(origin.offset(offset)).distanceToSqr(eye)));
                  }
               }
            }
         }

         ranked.sort(Comparator.comparingDouble(Ranked::rank).reversed().thenComparingDouble(Ranked::eyeDistanceSqr));
         List<BlockPos> offsets = new ArrayList<>(ranked.size());

         for (Ranked entry : ranked) {
            offsets.add(entry.offset());
         }

         return offsets;
      }
   }

   private Vec3 grounded(Vec3 position) {
      BlockPos pos = BlockPos.containing(position);

      int drop;
      for (drop = 0; drop < 3 && !MC.level.isOutsideBuildHeight(pos.below()) && MC.level.getBlockState(pos.below()).isAir(); drop++) {
         pos = pos.below();
      }

      return drop == 0 ? position : position.subtract(0.0, drop, 0.0);
   }

   private boolean placeable(BlockPos origin, BlockPos offset, boolean mustBeOnGround, boolean web, Set<Block> trapBlocks) {
      BlockPos cell = origin.offset(offset);
      if (MC.level.isOutsideBuildHeight(cell)) {
         return false;
      } else {
         BlockState state = MC.level.getBlockState(cell);
         if (trapBlocks.contains(state.getBlock()) || !state.canBeReplaced()) {
            return false;
         } else {
            return web && !state.getFluidState().isEmpty() ? false : !mustBeOnGround || !MC.level.getBlockState(cell.below()).isAir();
         }
      }
   }

   private static double overlap(AABB box, AABB unit) {
      return box.intersects(unit) ? box.intersect(unit).getSize() : 0.0;
   }

   private RiptideRotationUtil.Rotation solvePlacement(BlockPos cell, BlockState placedState, Vec3 eye, double reach, ItemStack material, InteractionHand hand) {
      if (MC.level.isOutsideBuildHeight(cell)) {
         return null;
      } else {
         BlockState cellState = MC.level.getBlockState(cell);
         if (!cellState.canBeReplaced()) {
            return null;
         } else if (placedState.is(Blocks.COBWEB) && !cellState.getFluidState().isEmpty()) {
            return null;
         } else if (Vec3.atCenterOf(cell).distanceToSqr(eye) < 1.0) {
            return null;
         } else if (!MC.level.isUnobstructed(placedState, cell, CollisionContext.empty())) {
            return null;
         } else {
            RiptideFaceScan.Candidate candidate = RiptideFaceScan.best(
               new RiptideFaceScan.Request(cell, eye, reach, this.placementFor(material, hand))
                  .from(RiptideRotationUtil.playerRotation(MC.player))
                  .pitchLimit(RiptideFaceScan.goalPitchLimit())
                  .sneaking(MC.player.isSecondaryUseActive())
                  .sneakAllowed(false)
                  .budget(new RiptideFaceScan.Budget(48))
            );
            return candidate == null ? null : candidate.aim().goal();
         }
      }
   }

   private RiptideFaceScan.Placement placementFor(ItemStack material, InteractionHand hand) {
      return material.getItem() instanceof BlockItem
         ? RiptideFaceScan.blockItem(material, MC.player, hand)
         : (hit, target) -> hit.getBlockPos().relative(hit.getDirection()).equals(target);
   }

   private ItemStack planStack(AutoTrapModule.Plan plan) {
      return plan.hand() == InteractionHand.OFF_HAND ? MC.player.getOffhandItem() : MC.player.getInventory().getItem(plan.hotbarSlot());
   }

   private BlockPos placementCell(BlockHitResult ray, ItemStack stack) {
      return stack.getItem() instanceof BlockItem
         ? new BlockPlaceContext(MC.player, InteractionHand.MAIN_HAND, stack, ray).getClickedPos()
         : ray.getBlockPos().relative(ray.getDirection());
   }

   private boolean landsInCell(BlockHitResult hit, BlockPos cell, ItemStack stack) {
      if (!(stack.getItem() instanceof BlockItem)) {
         return true;
      } else {
         BlockPlaceContext context = new BlockPlaceContext(MC.player, InteractionHand.MAIN_HAND, stack, hit);
         return context.canPlace() && context.getClickedPos().equals(cell);
      }
   }

   private static boolean placementPitchLegal(float pitch) {
      return ScaffoldModule.grimPlacementPitchLegal(Math.abs(pitch));
   }

   private RiptideRotationUtil.Rotation tryPlace(AutoTrapModule.Plan plan) {
      int tick = RiptideSharedState.get().getClientTickCounter();
      if (tick == this.lastPlaceTick) {
         return null;
      } else if (BedDefenderModule.ownsSilentRotation()) {
         return null;
      } else if (SurroundModule.ownsSilentRotation()) {
         return null;
      } else if (CrystalAuraModule.reservesCombatTick()) {
         return null;
      } else if (AnchorAuraModule.reservesCombatTick()) {
         return null;
      } else if (!this.paceHolds()) {
         return null;
      } else if (RiptideBlinkManager.holdsActionsWithoutMovement()) {
         return null;
      } else {
         RiptideServerRotationView.WireSnapshot wire = RiptideServerRotationView.snapshot();
         if (!wire.initialized()) {
            return null;
         } else {
            RiptideRotationUtil.Rotation wireRotation = new RiptideRotationUtil.Rotation(wire.currentYaw(), wire.currentPitch());
            if (!placementPitchLegal(wireRotation.pitch())) {
               return null;
            } else {
               RiptideRotationUtil.Rotation silent = RiptideSilentAim.activeOutgoingRotation(MC.player);
               if (silent != null && !sameRotation(silent, wireRotation)) {
                  return null;
               } else if (plan.pickup()) {
                  return this.tryPickup(plan, tick, wireRotation);
               } else {
                  double reach = this.reach();
                  BlockHitResult ray = ScaffoldModule.grimClickRay(MC.player.getEyePosition(), wireRotation, reach, MC.level, MC.player);
                  if (!this.landsInTarget(plan, ray)) {
                     return null;
                  } else {
                     BlockPos clicked = ray.getBlockPos();
                     if (!RiptideFaceScan.isPlaceableSupport(MC.level.getBlockState(clicked), clicked, MC.player.isSecondaryUseActive())) {
                        return null;
                     } else if (!this.ensureHand(plan)) {
                        return null;
                     } else if (plan.hand() == InteractionHand.OFF_HAND && RiptideCombatClicker.mainHandWouldPreempt()) {
                        return null;
                     } else {
                        ItemStack stack = MC.player.getItemInHand(plan.hand());
                        if (!this.trapItems(plan.web()).contains(stack.getItem())) {
                           return null;
                        } else {
                           BlockPos placed = this.placementCell(ray, stack);
                           if (plan.web() && !MC.level.getBlockState(placed).getFluidState().isEmpty()) {
                              return null;
                           } else if (Vec3.atCenterOf(placed).distanceToSqr(MC.player.getEyePosition()) < 1.0) {
                              return null;
                           } else if (!MC.level.isUnobstructed(this.placedState(plan.web(), stack), placed, CollisionContext.empty())) {
                              return null;
                           } else if (ModuleRegistry.shouldCancelUseExcept(ray, plan.hand(), this.id())) {
                              return null;
                           } else {
                              this.lastPlaceTick = tick;
                              this.lastPlaceNanos = System.nanoTime();
                              this.placeJitterMs = this.random.nextInt(26);
                              if (!this.dispatch(plan, ray, stack)) {
                                 return null;
                              } else {
                                 if (!plan.web() && stack.is(Items.LAVA_BUCKET) && !MC.player.hasInfiniteMaterials()) {
                                    this.lavaSource = new AutoTrapModule.LavaSource(placed, plan.targetId(), plan.hotbarSlot(), plan.hand(), tick);
                                 }

                                 this.lockedTargetId = plan.targetId();
                                 return wireRotation;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private RiptideRotationUtil.Rotation tryPickup(AutoTrapModule.Plan plan, int tick, RiptideRotationUtil.Rotation wireRotation) {
      AutoTrapModule.LavaSource source = this.lavaSource;
      if (source == null) {
         return null;
      } else {
         BlockHitResult ray = this.sourceRay(wireRotation);
         if (ray == null || !ray.getBlockPos().equals(source.cell())) {
            return null;
         } else if (!isLavaSource(MC.level.getBlockState(source.cell()))) {
            this.lavaSource = null;
            return null;
         } else if (!this.ensureHand(plan)) {
            return null;
         } else if (plan.hand() == InteractionHand.OFF_HAND && RiptideCombatClicker.mainHandWouldPreempt()) {
            return null;
         } else {
            ItemStack stack = MC.player.getItemInHand(plan.hand());
            if (!stack.is(Items.BUCKET)) {
               return null;
            } else if (ModuleRegistry.shouldCancelUseExcept(ray, plan.hand(), this.id())) {
               return null;
            } else {
               this.lastPlaceTick = tick;
               this.lastPlaceNanos = System.nanoTime();
               this.placeJitterMs = this.random.nextInt(26);
               return !this.dispatch(plan, ray, stack) ? null : wireRotation;
            }
         }
      }
   }

   private BlockHitResult sourceRay(RiptideRotationUtil.Rotation rotation) {
      Vec3 eye = MC.player.getEyePosition();
      Vec3 end = eye.add(Vec3.directionFromRotation(rotation.pitch(), rotation.yaw()).scale(this.reach()));
      HitResult result = MC.level
         .clip(new ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE, Fluid.SOURCE_ONLY, CollisionContext.of(MC.player)));
      return result instanceof BlockHitResult hit && result.getType() == Type.BLOCK ? hit : null;
   }

   private boolean landsInTarget(AutoTrapModule.Plan plan, BlockHitResult ray) {
      if (ray == null) {
         return false;
      } else {
         BlockPos placed = this.placementCell(ray, this.planStack(plan));
         if (!new AABB(placed).intersects(plan.targetBox())) {
            return false;
         } else if (MC.level.isOutsideBuildHeight(placed)) {
            return false;
         } else {
            BlockState state = MC.level.getBlockState(placed);
            return plan.web() && !state.getFluidState().isEmpty() ? false : state.canBeReplaced() && !this.trapBlocks(plan.web()).contains(state.getBlock());
         }
      }
   }

   private Set<Item> trapItems(boolean web) {
      return web ? WEB_ITEMS : IGNITE_ITEMS;
   }

   private Set<Block> trapBlocks(boolean web) {
      return web ? WEB_BLOCKS : IGNITE_BLOCKS;
   }

   private boolean paceHolds() {
      if (this.lastPlaceNanos == Long.MIN_VALUE) {
         return true;
      } else {
         long elapsedMs = (System.nanoTime() - this.lastPlaceNanos) / 1000000L;
         return elapsedMs >= 100L + this.placeJitterMs - 3L;
      }
   }

   @Override
   public boolean shouldCancelUse(HitResult hitResult, InteractionHand hand) {
      return this.lastPlaceTick == RiptideSharedState.get().getClientTickCounter();
   }

   @Override
   public boolean shouldCancelAttack(HitResult hitResult) {
      return hitResult instanceof EntityHitResult && this.id().equals(RiptideKillAuraRotation.currentOwner()) && RiptideKillAuraRotation.hasCurrentRotation();
   }

   private boolean ensureHand(AutoTrapModule.Plan plan) {
      if (plan.hand() == InteractionHand.OFF_HAND) {
         return true;
      } else {
         int selected = MC.player.getInventory().getSelectedSlot();
         if (selected == plan.hotbarSlot()) {
            return true;
         } else if (!this.changeHotbarSlot(plan.hotbarSlot(), selected)) {
            return false;
         } else {
            this.trapSlot = plan.hotbarSlot();
            return false;
         }
      }
   }

   private boolean changeHotbarSlot(int slot, int selected) {
      if (!BedDefenderModule.ownsSilentRotation()
         && !SurroundModule.ownsSilentRotation()
         && !CrystalAuraModule.reservesCombatTick()
         && !AnchorAuraModule.reservesCombatTick()) {
         int tick = RiptideSharedState.get().getClientTickCounter();
         if (tick == this.hotbarChangeTick) {
            return false;
         } else if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
            return false;
         } else {
            try {
               if (this.previousSlot < 0) {
                  this.previousSlot = selected;
               }

               RiptideInventoryHelper.selectHotbarSlot(MC, slot);
            } finally {
               RiptideHandArbiter.endHandPacketGroup(this.id());
            }

            this.hotbarChangeTick = tick;
            return true;
         }
      } else {
         return false;
      }
   }

   private boolean dispatch(AutoTrapModule.Plan plan, BlockHitResult ray, ItemStack stack) {
      if (stack.isEmpty() || !stack.isItemEnabled(MC.level.enabledFeatures())) {
         return false;
      } else {
         return !RiptidePlacementTick.claim(this.id()) ? false : RiptideCombatClicker.queueUse(ray, plan.hand());
      }
   }

   private static boolean sameRotation(RiptideRotationUtil.Rotation first, RiptideRotationUtil.Rotation second) {
      return Math.abs(RiptideRotationUtil.angleDifference(first.yaw(), second.yaw())) <= 0.05F && Math.abs(first.pitch() - second.pitch()) <= 0.05F;
   }

   private void armSwitchBack() {
      if (this.previousSlot >= 0 && this.currentPlan == null && this.switchBackTicks <= 0) {
         this.switchBackTicks = 2;
      }
   }

   private void tickSwitchBack() {
      if (this.previousSlot >= 0) {
         if (!this.bool("switch-back")) {
            this.previousSlot = -1;
            this.trapSlot = -1;
            this.switchBackTicks = 0;
         } else if (MC.gui != null && MC.gui.screen() == null && MC.gui.overlay() == null) {
            if (this.trapSlot >= 0 && MC.player.getInventory().getSelectedSlot() != this.trapSlot) {
               this.previousSlot = -1;
               this.trapSlot = -1;
               this.switchBackTicks = 0;
            } else if (this.switchBackTicks > 0 && --this.switchBackTicks <= 0) {
               if (!RiptideHandArbiter.slotReserved(this.previousSlot, this.id())
                  && this.changeHotbarSlot(this.previousSlot, MC.player.getInventory().getSelectedSlot())) {
                  this.previousSlot = -1;
                  this.trapSlot = -1;
               } else {
                  this.switchBackTicks = 1;
               }
            }
         }
      }
   }

   private record Landing(Vec3 landing, int ticksToGround, Vec3 current, boolean stationary, Vec3[] leadPath) {
      Vec3 leadAt(int ticks) {
         int index = ticks < 1 ? 0 : (ticks > this.leadPath.length ? this.leadPath.length - 1 : ticks - 1);
         return this.leadPath[index];
      }
   }

   private record LavaSource(BlockPos cell, int targetId, int hotbarSlot, InteractionHand hand, int placedTick) {
   }

   private record Plan(boolean web, int targetId, AABB targetBox, int hotbarSlot, InteractionHand hand, RiptideRotationUtil.Rotation rotation, boolean pickup) {
   }

   private record TrapSlot(int hotbarSlot, InteractionHand hand, ItemStack stack) {
   }
}
