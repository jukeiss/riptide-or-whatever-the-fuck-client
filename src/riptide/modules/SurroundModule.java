package riptide.modules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
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

public final class SurroundModule extends Module implements RiptideSilentAim.Owner {
   private static final Direction[] DIRECTIONS_EXCLUDING_UP = new Direction[]{Direction.DOWN, Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
   private static final Direction[] RING_CYCLE = new Direction[]{Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH};
   private static final float BLAST_RESISTANT_AT = 600.0F;
   private static final double XZ_SPEED_LIMIT_SQR = 0.25;
   private static final int SCAN_RAY_BUDGET = 4;
   private static final double FEET_EPSILON = 0.001;
   private static final double COLUMN_INSET = 0.001;
   private static final int SWITCH_BACK_IDLE_TICKS = 6;
   private static final int OWNERSHIP_TAIL_TICKS = 5;
   private static final int MAX_TRACKED_BREAKERS = 256;
   private static final long BREAKING_EXPIRY_NANOS = 2000000000L;
   private static final int MAX_PLACED_MEMORY = 64;
   private final Map<Integer, SurroundModule.Breaking> breaking = new ConcurrentHashMap<>();
   private final Set<BlockPos> broken = new HashSet<>();
   private final List<BlockPos> targets = new ArrayList<>();
   private final Set<BlockPos> placedByUs = new LinkedHashSet<>();
   private boolean armed;
   private double startY;
   private double centerX;
   private double centerZ;
   private double lastX;
   private double lastZ;
   private boolean lastPositionValid;
   private int lastPlaceTick = Integer.MIN_VALUE;
   private int aimTick = Integer.MIN_VALUE;
   private final RiptideFaceScan.Budget scanBudget = new RiptideFaceScan.Budget(4);
   private int originalSlot = -1;
   private int switchedToSlot = -1;
   private int idleTicks;
   private final KillAuraModule.TickVerdict throwableVerdict = new KillAuraModule.TickVerdict();
   private String cachedFilterRaw;
   private Set<Block> cachedFilterBlocks = Set.of();
   private BlockPos pendingPlacementCell;

   public SurroundModule() {
      super("surround", "Surround", ModuleCategory.PLAYER, "Places blocks around your feet.");
      this.add(new BoolSetting("down", "Down", true).group("Features").description("Fill under the surround floor"));
      this.add(new ChoiceSetting("ring", "Ring", "Center", "Center", "Hitbox").group("Features").description("Ring around center or hitbox"));
      this.add(
         new BoolSetting("no-waste", "NoWaste", true)
            .group("Features")
            .visibleWhen(() -> "Hitbox".equals(this.choice("ring")))
            .description("Skip extra cells inside holes")
      );
      this.add(new BoolSetting("disable-on-y-change", "YChange", true).group("DisableOn").description("Turn off on Y change"));
      this.add(new BoolSetting("disable-on-xz-move", "XZMove", false).group("DisableOn").description("Turn off leaving start block"));
      this.add(new BoolSetting("disable-on-xz-speed", "XZSpeed", false).group("DisableOn").description("Turn off when moving fast"));
      this.add(new ChoiceSetting("filter-mode", "Filter", "Whitelist", "Whitelist", "Blacklist").group("Blocks").description("How the list is applied"));
      this.add(
         RegistryListSetting.blocks("blocks", "Blocks", "minecraft:obsidian|minecraft:crying_obsidian|minecraft:ender_chest")
            .group("Blocks")
            .description("Surround material")
      );
      this.add(new IntSetting("aim-speed", "Aim Speed", 3, 1, 5, 1).group("Placing").description("Turn and place speed"));
      this.add(new BoolSetting("switch-back", "Switch Back", true).group("Placing").description("Return to your old slot"));
      this.add(new BoolSetting("protect", "Protect", true).group("Protect").description("Refill blocks others are mining"));
      this.add(
         new IntSetting("min-destroy-progress", "Min Destroy Progress", 4, 0, 9, 1)
            .group("Protect")
            .unit("stage")
            .visibleWhen(() -> this.bool("protect"))
            .description("Stage to start reacting at")
      );
      this.add(
         new BoolSetting("extra-layer", "Extra Layer", true)
            .group("Protect")
            .visibleWhen(() -> this.bool("protect"))
            .description("Second ring around mined cells")
      );
      this.add(
         new BoolSetting("extra-layer-corners", "Corners", false)
            .group("Protect")
            .visibleWhen(() -> this.bool("protect") && this.bool("extra-layer"))
            .description("Also fill the diagonals")
      );
      this.add(new BoolSetting("force-extra-layer", "Force Extra Layer", false).group("Protect").description("Extra layer on every cell"));
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
      this.armed = false;
      this.lastPositionValid = false;
      this.targets.clear();
      this.placedByUs.clear();
      this.broken.clear();
      this.breaking.clear();
      this.lastPlaceTick = Integer.MIN_VALUE;
      this.originalSlot = -1;
      this.switchedToSlot = -1;
      this.idleTicks = 0;
      RiptideHandArbiter.releaseAll(this.id());
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
      if (!this.isEnabled() && MC != null && MC.player != null) {
         if (this.id().equals(RiptideKillAuraRotation.currentOwner())) {
            RiptideKillAuraRotation.update(this.id(), MC.player);
         }
      }
   }

   @Override
   public String info() {
      int remaining = this.targets.size();
      return remaining <= 0 ? "" : Integer.toString(remaining);
   }

   public static boolean ownsSilentRotation() {
      if (!(ModuleRegistry.get("surround") instanceof SurroundModule surround)) {
         return false;
      } else {
         int age = RiptideSharedState.get().getClientTickCounter() - surround.aimTick;
         return age >= 0 && age <= 5 && "surround".equals(RiptideKillAuraRotation.currentOwner());
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
   public boolean onPacketReceive(Packet<?> packet) {
      if (packet instanceof ClientboundBlockDestructionPacket destruction) {
         if (this.isEnabled() && this.bool("protect")) {
            if (MC != null && MC.player != null && destruction.getId() == MC.player.getId()) {
               return false;
            } else {
               int progress = destruction.getProgress();
               if (progress >= 0 && progress <= 9) {
                  if (this.breaking.size() >= 256 && !this.breaking.containsKey(destruction.getId())) {
                     return false;
                  } else {
                     this.breaking.put(destruction.getId(), new SurroundModule.Breaking(destruction.getPos().asLong(), progress, System.nanoTime()));
                     return false;
                  }
               } else {
                  this.breaking.remove(destruction.getId());
                  return false;
               }
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void collectBrokenCells() {
      this.broken.clear();
      if (!this.bool("protect")) {
         this.breaking.clear();
      } else {
         long now = System.nanoTime();
         int minStage = this.integer("min-destroy-progress");
         this.breaking.values().removeIf(entryx -> now - entryx.stampNanos() > 2000000000L);

         for (SurroundModule.Breaking entry : this.breaking.values()) {
            if (entry.stage() >= minStage) {
               this.broken.add(BlockPos.of(entry.pos()));
            }
         }
      }
   }

   @Override
   public void preMovementTick() {
      if (!RiptideLiteVariant.enabled()) {
         if (MC != null && MC.player != null && MC.level != null && MC.getConnection() != null) {
            if (!this.armed) {
               this.captureStartPose();
            }

            this.tickPendingPlacement();
            double x = MC.player.getX();
            double y = MC.player.getY();
            double z = MC.player.getZ();
            String reason = this.disableReason(x, y, z);
            this.lastX = x;
            this.lastZ = z;
            this.lastPositionValid = true;
            if (reason != null) {
               this.disableWithToggleMessage("Surround disabled: " + reason + ".");
            } else {
               this.collectBrokenCells();
               this.buildTargets();
               this.runPlacement();
            }
         } else {
            this.targets.clear();
            this.standDown();
         }
      }
   }

   private void captureStartPose() {
      this.startY = MC.player.getY();
      BlockPos block = MC.player.blockPosition();
      this.centerX = block.getX() + 0.5;
      this.centerZ = block.getZ() + 0.5;
      this.lastPositionValid = false;
      this.armed = true;
   }

   private String disableReason(double x, double y, double z) {
      if (this.bool("disable-on-y-change") && y != this.startY) {
         return "your Y changed";
      } else if (!this.bool("disable-on-xz-move") || !(Math.abs(x - this.centerX) > 0.5) && !(Math.abs(z - this.centerZ) > 0.5)) {
         if (this.bool("disable-on-xz-speed") && this.lastPositionValid) {
            double dx = x - this.lastX;
            double dz = z - this.lastZ;
            if (dx * dx + dz * dz >= 0.25) {
               return "you moved too fast";
            }
         }

         return null;
      } else {
         return "you left the block";
      }
   }

   private void buildTargets() {
      this.targets.clear();
      AABB box = MC.player.getBoundingBox();
      int feetY = Mth.floor(box.minY + 0.001);
      List<BlockPos> hole = this.holeColumns(box, feetY);
      Set<BlockPos> seen = new HashSet<>(hole);
      List<BlockPos> ring = new ArrayList<>();
      List<BlockPos> down = new ArrayList<>();
      List<BlockPos> extra = new ArrayList<>();
      boolean addDown = this.bool("down");
      boolean forceExtra = this.bool("force-extra-layer");
      boolean extraLayer = this.bool("protect") && this.bool("extra-layer");
      boolean corners = extraLayer && this.bool("extra-layer-corners");
      Direction[] order = this.walkOrder();

      for (BlockPos holePos : hole) {
         for (Direction direction : order) {
            BlockPos pos = holePos.relative(direction);
            if (seen.add(pos)) {
               if (direction == Direction.DOWN) {
                  down.add(pos);
                  if (addDown) {
                     addUnique(down, seen, holePos.below(2));
                  }
               } else {
                  ring.add(pos);
                  if (forceExtra || extraLayer && this.broken.contains(pos)) {
                     addUnique(extra, seen, pos.relative(direction));
                     addUnique(extra, seen, pos.above());
                     if (corners) {
                        addUnique(extra, seen, pos.relative(direction.getClockWise()));
                     }
                  }
               }
            }
         }
      }

      this.appendGroup(ring);
      this.appendGroup(down);
      this.appendGroup(extra);
   }

   private Direction[] walkOrder() {
      if (this.integer("aim-speed") <= 1) {
         return DIRECTIONS_EXCLUDING_UP;
      } else {
         RiptideServerRotationView.WireSnapshot wire = RiptideServerRotationView.snapshot();
         float fromYaw = wire.initialized() ? wire.currentYaw() : MC.player.getYRot();
         int start = 0;
         float best = Float.MAX_VALUE;

         for (int i = 0; i < RING_CYCLE.length; i++) {
            float turn = Math.abs(RiptideRotationUtil.angleDifference(RING_CYCLE[i].toYRot(), fromYaw));
            if (turn < best) {
               best = turn;
               start = i;
            }
         }

         Direction[] order = new Direction[RING_CYCLE.length + 1];
         order[0] = Direction.DOWN;

         for (int ix = 0; ix < RING_CYCLE.length; ix++) {
            order[ix + 1] = RING_CYCLE[(start + ix) % RING_CYCLE.length];
         }

         return order;
      }
   }

   private static void addUnique(List<BlockPos> target, Set<BlockPos> seen, BlockPos pos) {
      if (seen.add(pos)) {
         target.add(pos);
      }
   }

   private void appendGroup(List<BlockPos> group) {
      for (BlockPos pos : group) {
         if (this.placedByUs.contains(pos) && MC.level.getBlockState(pos).canBeReplaced()) {
            this.targets.add(pos);
         }
      }

      for (BlockPos posx : group) {
         if (!this.placedByUs.contains(posx) && MC.level.getBlockState(posx).canBeReplaced()) {
            this.targets.add(posx);
         }
      }
   }

   private List<BlockPos> holeColumns(AABB box, int feetY) {
      BlockPos feet = new BlockPos(Mth.floor((box.minX + box.maxX) * 0.5), feetY, Mth.floor((box.minZ + box.maxZ) * 0.5));
      if (!"Hitbox".equals(this.choice("ring"))) {
         return List.of(feet);
      } else if (this.bool("no-waste") && this.isInHole(feet)) {
         return List.of(feet);
      } else {
         int minColX = Mth.floor(box.minX + 0.001);
         int maxColX = Mth.floor(box.maxX - 0.001);
         int minColZ = Mth.floor(box.minZ + 0.001);
         int maxColZ = Mth.floor(box.maxZ - 0.001);
         List<BlockPos> columns = new ArrayList<>(4);

         for (int columnX = minColX; columnX <= maxColX; columnX++) {
            for (int columnZ = minColZ; columnZ <= maxColZ; columnZ++) {
               columns.add(new BlockPos(columnX, feetY, columnZ));
            }
         }

         return columns.isEmpty() ? List.of(feet) : columns;
      }
   }

   private boolean isInHole(BlockPos feet) {
      for (Direction direction : DIRECTIONS_EXCLUDING_UP) {
         float resistance = MC.level.getBlockState(feet.relative(direction)).getBlock().getExplosionResistance();
         if (resistance < 600.0F) {
            return false;
         }
      }

      return true;
   }

   private void runPlacement() {
      if (!this.canRun()) {
         this.standDown();
      } else {
         int slot = this.resolveHotbarSlot();
         if (this.throwableHeldThisTick()) {
            this.standDown();
            if (slot >= 0 && slot != MC.player.getInventory().getSelectedSlot()) {
               this.selectSlot(slot);
            }
         } else if (RiptideBlinkManager.holdsActionsWithoutMovement()) {
            this.standDown();
         } else if (!this.targets.isEmpty() && slot >= 0) {
            RiptideServerRotationView.WireSnapshot wire = RiptideServerRotationView.snapshot();
            RiptideRotationUtil.Rotation wireRotation = wire.initialized() ? new RiptideRotationUtil.Rotation(wire.currentYaw(), wire.currentPitch()) : null;
            RiptideRotationUtil.Rotation from = wireRotation != null ? wireRotation : RiptideRotationUtil.playerRotation(MC.player);
            ItemStack material = MC.player.getInventory().getItem(slot);
            SurroundModule.Plan plan = this.planNextTarget(from, material, null);
            if (plan == null) {
               this.standDown();
               this.maybeSwitchBack();
            } else {
               this.idleTicks = 0;
               boolean slotReady = slot == MC.player.getInventory().getSelectedSlot();
               BlockHitResult hit = wireRotation == null ? null : this.wireRay(plan, wireRotation);
               boolean fire = hit != null && slotReady && !BedDefenderModule.ownsSilentRotation() && this.cadenceHolds();
               boolean aimed;
               if (fire) {
                  SurroundModule.Plan next = this.planNextTarget(from, material, plan.cell());
                  aimed = this.pumpAim(next != null ? next.goal() : plan.goal(), true);
               } else {
                  aimed = this.pumpAim(plan.goal(), false);
               }

               if (fire && aimed) {
                  RiptideRotationUtil.Rotation outgoing = RiptideSilentAim.activeOutgoingRotation(MC.player);
                  if (outgoing != null && !(Math.abs(outgoing.pitch() - wireRotation.pitch()) > 0.05F)) {
                     this.commit(plan, hit);
                  }
               } else {
                  if (!slotReady) {
                     this.selectSlot(slot);
                  }
               }
            }
         } else {
            this.standDown();
            this.maybeSwitchBack();
         }
      }
   }

   private boolean pumpAim(RiptideRotationUtil.Rotation goal, boolean holdPitch) {
      RiptideKillAuraRotation.setTarget(this.id(), 25, goal);
      this.aimTick = RiptideSharedState.get().getClientTickCounter();
      if (!this.id().equals(RiptideKillAuraRotation.currentOwner())) {
         return false;
      } else {
         int speed = this.integer("aim-speed");
         RiptideKillAuraRotation.update(this.id(), MC.player, turnCap(speed), holdPitch ? 0.0F : turnCap(speed), aimProfile(speed));
         return true;
      }
   }

   private static RiptideHumanRotation.MotionProfile aimProfile(int speed) {
      return switch (speed) {
         case 2 -> RiptideHumanRotation.MotionProfile.SURROUND_FAST_2;
         case 3 -> RiptideHumanRotation.MotionProfile.SURROUND_FAST_3;
         case 4 -> RiptideHumanRotation.MotionProfile.SURROUND_FAST_4;
         case 5 -> RiptideHumanRotation.MotionProfile.SURROUND_FAST_5;
         default -> RiptideHumanRotation.MotionProfile.STANDARD;
      };
   }

   private static float turnCap(int speed) {
      return switch (speed) {
         case 3 -> 90.0F;
         case 4 -> 135.0F;
         case 5 -> 180.0F;
         default -> 72.0F;
      };
   }

   private SurroundModule.Plan planNextTarget(RiptideRotationUtil.Rotation from, ItemStack material, BlockPos skip) {
      double reach = Math.max(MC.player.blockInteractionRange(), MC.player.entityInteractionRange());
      Vec3 eye = MC.player.getEyePosition();
      AABB playerBox = MC.player.getBoundingBox();
      Vec3 delta = MC.player.getDeltaMovement();
      BlockState placed = material.getItem() instanceof BlockItem blockItem ? blockItem.getBlock().defaultBlockState() : null;
      RiptideFaceScan.Request request = new RiptideFaceScan.Request(null, eye, reach, RiptideFaceScan.blockItem(material, MC.player, InteractionHand.MAIN_HAND))
         .from(from)
         .sneaking(MC.player.isShiftKeyDown())
         .leadEye(eye.add(delta))
         .budget(this.scanBudget);

      for (BlockPos cell : this.targets) {
         if (!cell.equals(skip) && !this.cellObstructed(cell, placed, playerBox, delta)) {
            this.scanBudget.reset(4);
            RiptideFaceScan.Candidate candidate = RiptideFaceScan.best(request.cell(cell));
            if (candidate != null) {
               return new SurroundModule.Plan(cell, candidate);
            }
         }
      }

      return null;
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

   private BlockHitResult wireRay(SurroundModule.Plan plan, RiptideRotationUtil.Rotation rotation) {
      double reach = Math.max(MC.player.blockInteractionRange(), MC.player.entityInteractionRange());
      Vec3 eye = MC.player.getEyePosition();
      RiptideFaceScan.Request gate = new RiptideFaceScan.Request(
            plan.cell(), eye, reach, RiptideFaceScan.blockItem(MC.player.getItemInHand(InteractionHand.MAIN_HAND), MC.player, InteractionHand.MAIN_HAND)
         )
         .sneaking(MC.player.isShiftKeyDown());
      return RiptideFaceScan.confirm(plan.candidate(), rotation, eye, reach, gate);
   }

   private boolean cadenceHolds() {
      return RiptideSharedState.get().getClientTickCounter() != this.lastPlaceTick;
   }

   private void commit(SurroundModule.Plan plan, BlockHitResult hit) {
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
            if (this.isSurroundBlock(stack)) {
               if (slot == selected) {
                  return slot;
               }

               if (best < 0 || stack.getCount() > inventory.getItem(best).getCount()) {
                  best = slot;
               }
            }
         }
      }

      return best;
   }

   private void selectSlot(int slot) {
      if (!BedDefenderModule.ownsSilentRotation()) {
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

   private boolean isSurroundBlock(ItemStack stack) {
      if (!(stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem)) {
         return false;
      } else if (!stack.isItemEnabled(MC.level.enabledFeatures())) {
         return false;
      } else {
         boolean listed = this.filteredBlocks().contains(blockItem.getBlock());
         return "Whitelist".equals(this.choice("filter-mode")) == listed;
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

   private record Breaking(long pos, int stage, long stampNanos) {
   }

   private record Plan(BlockPos cell, RiptideFaceScan.Candidate candidate) {
      RiptideRotationUtil.Rotation goal() {
         return this.candidate.aim().goal();
      }
   }
}
