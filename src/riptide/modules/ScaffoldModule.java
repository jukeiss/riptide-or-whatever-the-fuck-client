package riptide.modules;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Direction.Plane;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResult.Fail;
import net.minecraft.world.InteractionResult.Pass;
import net.minecraft.world.InteractionResult.Success;
import net.minecraft.world.InteractionResult.SwingSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.RegistryListSetting;
import riptide.mixin.accessor.RiptideMinecraftAccessor;
import riptide.util.QuantizedRotationSmoother;
import riptide.util.RegistryListCodec;
import riptide.util.RiptideAndromeda;
import riptide.util.RiptideCpsTracker;
import riptide.util.RiptideHandArbiter;
import riptide.util.RiptideHumanRotation;
import riptide.util.RiptideInputClicker;
import riptide.util.RiptideKeyMappingBridge;
import riptide.util.RiptidePlacementTick;
import riptide.util.RiptideRemoteView;
import riptide.util.RiptideRotationUtil;
import riptide.util.RiptideScaffoldPlaceRenderer;
import riptide.util.RiptideServerRotationView;
import riptide.util.RiptideSharedState;
import riptide.util.RiptideTraceLog;
import riptide.util.macro.MacroExecutor;
import riptide.util.multi.MultiPilot;
import riptide.util.multi.PacketTeleportController;

public final class ScaffoldModule extends Module {
   static final int SLOT_RESET_TICKS = 5;
   static final int ROTATION_RESET_TICKS = 5;
   static final float ROTATION_RESET_THRESHOLD = 2.0F;
   static final float GRIM_AIM_ACCEL_BUDGET = 180.0F;
   static final double MIN_FACE_DISTANCE = 0.0;
   static final float TIMER_MULTIPLIER = 1.0F;
   static final boolean SWITCH_BACK_DEFAULT = true;
   static final String SWITCH_BACK_TIP = "Restore previous hotbar slot.";
   static final String REMOVE_LIMITS_TIP = "Faster rotations and placements, no climb brake.";
   static final float GRIM_REMOVE_LIMITS_ROTATION_SCALE = 4.0F;
   private static final double FACE_INSET = 0.15;
   private static final double GEOMETRY_EPSILON = 1.0E-9;
   static final float GRIM_MAX_PITCH_STEP = 20.0F;
   static final float GRIM_PLACE_MAX_PITCH_STEP = 10.0F;
   private static final int GRIM_BODY_CLEAR_LOOKAHEAD_TICKS = 2;
   private static final int GRIM_FACE_PLANE_LEAD_TICKS = 4;
   private static final double TRAJECTORY_DRIFT_LEAD_TICKS = 2.0;
   private static final double TRAJECTORY_DRIFT_MIN_SPEED = 0.05;
   private static final double GRIM_GUARD_MAX_DISTANCE = 2.0;
   private static final double GRIM_GUARD_SEED_BUCKET = 0.5;
   private static final double COURSE_STABILIZER_MIN_SPEED = 0.05;
   private static final double GRIM_LANE_INPUT_LOOKAHEAD = 1.1;
   private static final double GRIM_LANE_INPUT_PREDICT_TICKS = 4.0;
   static final float GRIM_LANE_INPUT_MAX_DEGREES = 38.0F;
   private static final double GRIM_LANE_INPUT_DEADBAND = 0.22;
   static final double GRIM_LANE_CORRECT_ENGAGE = 0.15;
   static final double GRIM_LANE_CORRECT_RELEASE = 0.06;
   static final int GRIM_LANE_CORRECT_MAX_HOLD_TICKS = 2;
   static final int GRIM_LANE_CORRECT_RELOCK_TICKS = 3;
   static final double GRIM_LANE_CORRECT_SETTLE_LEAD = 2.2;
   static final double GRIM_LANE_INPUT_LOOKAHEAD_LEGIT = 0.23;
   private static final float GRIM_STEP_HOLD_DEGREES = 37.5F;
   private static final int GRIM_STEP_COMMIT_TICKS = 2;
   static final int COURSE_STEP_UNSET = -1;
   static final int GRIM_INPUT_SIDEWAYS_MIN_HOLD = 4;
   static final float GRIM_INPUT_OCTANT_BOUNDARY_DEGREES = 30.0F;
   static final float GRIM_INPUT_SIDEWAYS_BREAK_DEGREES = 36.0F;
   static final float GRIM_INPUT_REFERENCE_BREAK_DEGREES = 22.5F;
   private static final double GRIM_INPUT_OCTANT_ENTER = 0.62;
   private static final double GRIM_INPUT_OCTANT_EXIT = 0.38;
   static final float GRIM_LANE_OCTANT_MAX_RESIDUAL = 12.0F;
   static final float GRIM_LANE_SWEEP_SETTLED_DEGREES = 2.0F;
   private static final int GRIM_LANE_SWEEP_MAX_TICKS = 8;
   static final int GRIM_YAW_VETO_RETIRE_TICKS = 2;
   private static final int GRIM_LANE_COAST_MAX_TICKS = 6;
   static final double GRIM_FOOTING_OWED_OVERLAP = 0.3;
   private static final int GRIM_FOOTING_OWED_MAX_TICKS = 10;
   private static final VoxelShape STANDABLE_CENTER_SHAPE = Block.column(2.0, 0.0, 10.0);
   private static final double GRIM_LATERAL_BRINK_OFFSET = 0.45;
   private static final double GRIM_COURSE_DIVERGENCE_DEGREES = 60.0;
   private static final double GRIM_LATERAL_BRINK_PROBE = 0.35;
   private static final double GRIM_BOX_HALF_WIDTH = 0.3;
   private static final int GRIM_RISE_COLUMN_LEAD_TICKS = 3;
   private static final double GRIM_RISE_COLUMN_MIN_OVERLAP = 0.15;
   private static final double GRIM_BEHIND_MARGIN = 0.8;
   private static final int GRIM_AIM_HOLD_MAX_TICKS = 3;
   private static final int GRIM_BRIDGING_RECENT_TICKS = 20;
   private static final int GRIM_ROW_LOCK_TICKS = 10;
   private static final int GRIM_CLIMB_CONTINUATION_TICKS = 30;
   private static final int GRIM_STICKY_MAX_TICKS = 6;
   private static final double GRIM_AIM_CENTER_WINDOW = 0.2;
   static final float GRIM_MAX_YAW_STEP = 18.0F;
   static final float GRIM_ANGLE_SNAP_MARGIN = 0.2F;
   private static final int GRIM_YAW_FIX_MAX_COUNTS = 64;
   static final float GRIM_PLACE_MAX_PITCH = 89.0F;
   static final float GRIM_PLACE_MAX_PITCH_HARD = 89.5F;
   private static final int GRIM_WIND_DOWN_SLACK_TICKS = 4;
   private static final int GRIM_WIND_DOWN_MIN_TICKS = 8;
   private static final int GRIM_WIND_DOWN_MAX_TICKS = 27;
   static final float GRIM_WIND_DOWN_MAX_YAW_STEP = 37.0F;
   static final float GRIM_WIND_DOWN_MAX_PITCH_STEP = 37.0F;
   static final float GRIM_WIND_DOWN_FIRST_STEP_MAX = 20.0F;
   private static final int GRIM_DESCENT_LOOKAHEAD_TICKS = 12;
   private static final double GRIM_AIR_COUNTER_IMPULSE = 0.02;
   static final double GRIM_HORIZONTAL_ZERO_THRESHOLD = 0.003;
   private static final double GRIM_LANDING_HALF_WIDTH = 0.29;
   private static final double GRIM_LANDING_MIN_OVERLAP = 0.15;
   private static final int GRIM_LANDING_MAX_AIR_TICKS = 20;
   static final double GRIM_LEG_SWAP_MARGIN = 0.25;
   private static final double GRIM_FREE_AIM_BEARING_MIN_RUN = 0.75;
   private static final double GRIM_ARC_BRAKE_MIN_LANE_SPEED = 0.03;
   private static final double GRIM_XING_STANDDOWN_MAX_TRAVEL = 0.35;
   private static final double GRIM_OCCLUDER_PLANE_EPS = 0.01;
   private static final int GRIM_ARC_BRAKE_LOOKAHEAD_TICKS = 24;
   private static final double GRIM_JUMP_TAKEOFF_VELOCITY = 0.42;
   private static final double GRIM_RISE_TAKEOFF_REACH = 1.7;
   private static final int GRIM_AIM_MISS_FALLBACK_TICKS = 3;
   private static final int GRIM_AIM_WINDOW_WAIT_MAX_TICKS = 6;
   private static final double GRIM_SUPPORT_MAX_DISTANCE = 2.5;
   private static final int GRIM_RISE_TAKEOFF_LATCH_TICKS = 4;
   static final int GRIM_PLACEMENT_RECONCILE_TICKS = 2;
   private static final int GRIM_PREDICTION_HISTORY = 64;
   private static final int GRIM_INTAVE_SNEAK_MEMORY_TICKS = 150;
   private static final int GRIM_PACE_SAMPLES = 8;
   private static final long GRIM_PACE_CROSS_Y_MS = 1000L;
   static final long GRIM_INTAVE_PLACE_MEAN_MS = 400L;
   static final long GRIM_INTAVE_PLACE_SAMPLE_CAP_MS = 1000L;
   static final float GRIM_INTAVE_PLACE_PITCH_MIN = 85.0F;
   static final float GRIM_INTAVE_FLICK_DIFF_MIN = 3.0F;
   static final float GRIM_INTAVE_FLICK_DIFF_MAX = 20.0F;
   static final float GRIM_INTAVE_FLICK_PITCH_MIN = 70.0F;
   static final long GRIM_INTAVE_FLICK_GAP_MS = 800L;
   static final int GRIM_INTAVE_FLICK_CELLS = 5;
   static final long GRIM_INTAVE_FLICK_CELL_TTL_MS = 5000L;
   static final float GRIM_INTAVE_FLICK_DIFF_SAFE_MARGIN = 0.5F;
   static final float GRIM_INTAVE_ROTATION_SAFE_PITCH = 84.5F;
   static final int GRIM_INTAVE_ROTATION_PARK_LOOKAHEAD_TICKS = 2;
   static final int GRIM_INTAVE_ROTATION_PARK_MAX_TICKS = 3;
   static final int GRIM_INTAVE_PARK_LOOKAHEAD_GROUNDED_TICKS = 5;
   static final int GRIM_INTAVE_PARK_MAX_GROUNDED_TICKS = 6;
   private static final long GRIM_PACE_RECENT_JUMP_WINDOW_MS = 750L;
   private static final double GRIM_PACE_SAFETY = 1.08;
   static final int GRIM_PACE_JITTER_MS = 25;
   static final double GRIM_FACE_OVERHEAD_MARGIN = 1.0;
   static final double GRIM_SNEAK_INPUT_SCALE = 0.3;
   static final double GRIM_OWN_RISER_MAX_CARRY = 0.8;
   static final double GRIM_PACE_BRINK_OVERLAP = 0.12;
   static final double GRIM_PACE_ACK_LEAD_OVERLAP = 0.3;
   private static final int GRIM_SNEAK_REFRESH_TICKS = 90;
   private static final int GRIM_SNEAK_MIN_HOLD_TICKS = 2;
   private static final int GRIM_BRIDGE_ACTIVE_TICKS = 40;
   private static final float PREAIM_PITCH_MIN = 68.0F;
   private static final float PREAIM_PITCH_SPAN = 12.0F;
   private static final double PREAIM_MIN_SPEED = 0.03;
   private static final double[] PREAIM_EDGE_DISTANCES = new double[]{1.5, 2.0, 2.5};
   static final int GRIM_BRIDGE_HOLD_TICKS = 20;
   private static final float TELLY_LOOK_OFFSET_MIN = 0.18F;
   private static final float TELLY_LOOK_OFFSET_SPAN = 0.22F;
   private static final double[] SUPPORT_SAMPLES = new double[]{0.301, 0.0, -0.301};
   private static final int MAX_LAST_PLACED_BLOCKS = 4;
   private static final int MAX_PLACEMENT_OFFSETS = 4;
   private static final double SUPPORT_SURFACE_EPSILON = 0.001;
   private static final double SUPPORT_OVERLAP_HYSTERESIS = 0.02;
   private static final double PREDICTION_BACKOFF = 0.2;
   private static final double PREDICTION_CUTOFF_DISTANCE = 0.05;
   private static final double PREDICTION_LINE_LENGTH = 3.0;
   private static final double GRIM_EDGE_TARGET_EPSILON = 1.0E-4;
   static final double GRIM_GOAL_EYE_LEAD = 0.25;
   static final double GRIM_PIN_CROSS_DEPTH = 0.5;
   static final double GRIM_PIN_ACQUIRE_MARGIN = 0.1;
   static final double GRIM_PIN_RELEASE_MARGIN = 0.02;
   static final double GRIM_PIN_SIDE_ACQUIRE_MARGIN = 0.04;
   static final double GRIM_PIN_CORNER_ACQUIRE_MARGIN = -0.12;
   static final double GRIM_PIN_CORNER_RELEASE_MARGIN = -0.14;
   static final double GRIM_PIN_DIAGONAL_LOOK_TOLERANCE = 0.05;
   static final int GRIM_PIN_STALE_TICKS = 14;
   static final double GRIM_PIN_SIDE_RELEASE_MARGIN = -0.12;
   static final int GRIM_PIN_LOOKAHEAD_TICKS = 2;
   static final int GRIM_GRID_FLIP_MAX_STEPS = 2;
   static final double GRIM_FACE_TOWARD_TRACK_MIN = 0.25;
   private static final double GRIM_LANE_AIR_DRAG = 0.91;
   private static final double GRIM_GROUND_TAKEOFF_DRAG = 0.546;
   private static final double GRIM_GROUND_WALK_ACCEL = 0.096;
   private static final double GRIM_COURSE_DOT_EPSILON = 0.999;
   private static final int PREDICTION_WARMUP_PLACEMENTS = 2;
   private static final List<BlockPos> NORMAL_OFFSETS = normalOffsets();
   static final int RAGE_BLOCKS_MAX = 5;
   static final int RAGE_BLOCKS_DEFAULT = 0;
   private static final boolean TELLY_LIVE_TRACE = true;
   private static final boolean DEBUG_LOGS = false;
   private int originalSlot = -1;
   private int requestedSlot = -1;
   private int switchedToSlot = -1;
   private int slotResetTicks;
   private boolean selectionPending;
   private RiptideRotationUtil.Rotation serverRotation;
   private ScaffoldModule.MovementLine currentMovementLine;
   private final ArrayDeque<BlockPos> lastPlacedBlocks = new ArrayDeque<>(4);
   private final ArrayDeque<Vec3> placementOffsets = new ArrayDeque<>(5);
   private BlockPos lastSupportPosition;
   private ScaffoldModule.SupportReference lastSupportReference;
   private int supportMissTicks;
   private int supportMissClientTick = Integer.MIN_VALUE;
   private ScaffoldModule.MovementLine grimEdgeLockedLine;
   private int grimCourseStep = -1;
   private int grimCourseStepCandidate = -1;
   private int grimCourseStepDwell;
   private int grimLaneOctant;
   private float grimPostureYawHeld = Float.NaN;
   private float grimPostureYawCandidate = Float.NaN;
   private int grimPostureYawStreak;
   private int grimPostureYawTick = Integer.MIN_VALUE;
   private String cachedFilterRaw = "";
   private Set<Block> cachedFilterBlocks = Set.of();
   private RiptideRotationUtil.Rotation grimSilentRotation;
   private int grimRotationResetTicks;
   private int grimWindDownTicks;
   private final QuantizedRotationSmoother grimAimSmoother = new QuantizedRotationSmoother();
   private RiptideRotationUtil.Rotation grimAimPrevGoal;
   private float grimAimDirectionChange;
   private final RiptideHumanRotation.Stream tellyStream = new RiptideHumanRotation.Stream();
   private int grimRotationStepTick = Integer.MIN_VALUE;
   private int lastGrimPlacementTick = Integer.MIN_VALUE;
   private final Random rotationRandom = new Random();
   private boolean grimWindingDown;
   private boolean grimEdgeSneakActive;
   private float sessionPitchOffset = 74.0F;
   private float tellyLookYawOffset;
   private ScaffoldModule.TellyPhase tellyPhase = ScaffoldModule.TellyPhase.IDLE;
   private ScaffoldModule.TellyMotion tellyMotion = ScaffoldModule.TellyMotion.RELEASED;
   private boolean tellyOwnsInput;
   private boolean tellyStopRequested;
   private boolean tellyJumpThisTick;
   private boolean tellySneakThisTick;
   private boolean tellyPhysicalSpaceWasDown;
   private boolean tellyRiseQueued;
   private boolean tellySpaceHeld;
   private boolean tellyFinishing;
   private boolean tellyCycleRises;
   private boolean tellyRaisedBlockPlaced;
   private boolean tellyAimCommitted;
   private boolean tellyPlacementQueued;
   private boolean tellyWalkOffCatch;
   private int tellyWalkOffGraceTicks;
   private int tellyClickCooldown;
   private int tellyAirTicks;
   private int tellyFlatPlacements;
   private int tellyFailedClicks;
   private int tellyForwardDwellTicks;
   private int tellyBridgeY;
   private double tellyTakeoffY;
   private double tellyTakeoffProgress;
   private float tellyAnchorYaw;
   private float tellyForwardPitch;
   private double tellyLaneCenter;
   private int tellyRecoveryTicks;
   private boolean tellyCourseLatched;
   private boolean tellyGroundSteeringActive;
   private float tellyLaneBias;
   private float tellyGroundSteerOffset;
   private ScaffoldModule.TellyStrafe tellyAirStrafeThisTick = ScaffoldModule.TellyStrafe.NONE;
   private ScaffoldModule.TellyStrafe tellyAirLastStrafe = ScaffoldModule.TellyStrafe.NONE;
   private int tellyAirStrafeCooldown;
   private int tellyAirStrafePulses;
   private int tellyCourseDeviationTicks;
   private int tellyEdgeHoldTicks;
   private boolean tellyRotationHeldForPlacement;
   private int tellyRotationStepTick = Integer.MIN_VALUE;
   private int tellyFaceOffsetIndex = -1;
   private boolean tellyReturnCompleted = true;
   private int tellyHoldWatchdogTicks;
   private boolean tellyGroundLaunchAllowed;
   private RiptideRotationUtil.Rotation tellySmoothedRotation;
   private boolean tellyTurnSettling;
   private int tellySettleHoldTicks;
   private int tellySettleDwellTicks;
   private BlockPos tellyLastBridge;
   private BlockPos tellyLastGroundedSupport;
   private int tellyLastGroundedTick = Integer.MIN_VALUE;
   private BlockPos tellyRaisedCell;
   private BlockPos tellyQueuedBlock;
   private BlockPos tellyTurnReserveSupport;
   private BlockPos tellyTurnReserveCell;
   private boolean tellyTurnReserveQueued;
   private Vec3 tellyLineOrigin;
   private ScaffoldModule.TellyPlacement tellyTarget;
   private long tellyCycleSerial;
   private static final String[] COURSE_STEP_NAMES = new String[]{"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
   private int grimAirLeadTick = Integer.MIN_VALUE;
   private Vec3 grimAirLeadAccel = Vec3.ZERO;
   private int grimArcTicks;
   private int grimArcPlacements;
   private Vec3 grimArcCarryOrigin;
   private BlockPos grimAimMissSupport;
   private Direction grimAimMissFace;
   private int grimAimMissStreak;
   private int grimAimWindowWaitTicks;
   private int grimNoTargetTicks;
   private int grimPaceJitterMs;
   static final long GRIM_PACE_CLOCK_SLACK_MS = 3L;
   static final long GRIM_LANDING_LAST_CHANCE_FLOOR_MS = 50L;
   static final long GRIM_PACE_RISER_FLOOR_MS = 100L;
   static final long GRIM_MATRIX_MIN_PLACE_CARDINAL_MS = 350L;
   static final long GRIM_MATRIX_MIN_PLACE_DIAGONAL_MS = 250L;
   private BlockPos grimPaceRiserHoldCell = null;
   private int grimPaceRiserHoldTick = Integer.MIN_VALUE;
   private static final int GRIM_PACE_RISER_HOLD_STAMP_TICKS = 8;
   private final ArrayDeque<ScaffoldModule.GrimPaceSample> grimPaceSamples = new ArrayDeque<>();
   private final ArrayDeque<Long> grimIntavePlaceGaps = new ArrayDeque<>();
   private final ArrayDeque<BlockPos> grimIntavePlaceCells = new ArrayDeque<>();
   private long grimIntavePlaceNanos = Long.MIN_VALUE;
   private float grimIntavePlacePitch = Float.NaN;
   private long grimPaceLastBookedNanos = Long.MIN_VALUE;
   private long grimPaceLastJumpNanos = Long.MIN_VALUE;
   private long grimPaceQueuedNanos = Long.MIN_VALUE;
   private boolean grimPaceWasOnGround;
   private BlockPos grimIntaveParkSupport;
   private Direction grimIntaveParkFace;
   private int grimIntaveParkTicks;
   private int grimIntaveParkClientTick = Integer.MIN_VALUE;
   private int grimLastSneakTick = Integer.MIN_VALUE;
   private int grimSneakHoldTicks;
   private int grimAimOccludedTicks;
   private final Map<BlockPos, Integer> grimDeadCells = new HashMap<>();
   private static final int GRIM_DEAD_CELL_TICKS = 10;
   private static final int GRIM_DEAD_CELL_MAX = 32;
   static final double GRIM_EYE_PAST_FACE_MARGIN = 0.05;
   private int grimLiveTraceTicks = -1;
   private int grimTraceLastPlaceTick = Integer.MIN_VALUE;
   private int tellyLiveTraceTicks = -1;
   private int tellyTracePrintedTick = Integer.MIN_VALUE;
   private boolean tellyTraceDelay;
   private ScaffoldModule.PlacementTarget grimAimHoldTarget;
   private BlockPos grimUpFaceSwapCell;
   private double grimTraceDiagonalPaceMean = Double.NaN;
   private Direction grimCrossingWaitFace;
   private int grimCrossingWaitTick = Integer.MIN_VALUE;
   private double grimXingStandSpent;
   private int grimXingStandTick = Integer.MIN_VALUE;
   private int grimAimHoldTick = -1;
   private boolean grimAimHoldServed;
   private int grimAimHoldServedTick = Integer.MIN_VALUE;
   private ScaffoldModule.PlacementTarget grimStickyTarget;
   private int grimStickySetTick = -1;
   private int grimStickyBandMissTicks;
   private int grimStickyBandTick = Integer.MIN_VALUE;
   private static final ConcurrentLinkedQueue<ScaffoldModule.GrimFinalUseWrite> GRIM_FINAL_USE_WRITES = new ConcurrentLinkedQueue<>();
   private static final AtomicReference<ScaffoldModule.GrimFinalMoveWrite> GRIM_FINAL_MOVE_WRITE = new AtomicReference<>();
   private static final ReferenceQueue<Packet<?>> GRIM_QUEUED_USE_GC = new ReferenceQueue<>();
   private static final Map<ScaffoldModule.GrimPacketIdentity, ScaffoldModule.GrimQueuedUse> GRIM_QUEUED_USES = new HashMap<>();
   private ScaffoldModule.PlacementTarget grimRealPendingTarget;
   private ScaffoldModule.MovementLine grimRealPendingLine;
   private Vec3 grimRealPendingFallOff;
   private int grimRealQueuedTick = Integer.MIN_VALUE;
   private ScaffoldModule.GrimPlacementAttemptState grimAttemptState = ScaffoldModule.GrimPlacementAttemptState.IDLE;
   private long grimAttemptGeneration;
   private long grimNextAttemptGeneration;
   private InteractionHand grimAttemptHand;
   private boolean grimAttemptBuildsPlannedCell;
   private int grimAttemptSubmittedCount;
   private boolean grimAttemptDuplicateSubmitted;
   private int grimAttemptWriteCount;
   private RiptideRotationUtil.Rotation grimCommittedClickRotation;
   private RiptideRotationUtil.Rotation grimCommittedPreviousRotation;
   private int grimAttemptSequence = -1;
   private boolean grimAttemptResultSeen;
   private boolean grimAttemptResultConsumed;
   private boolean grimAttemptPaceBooked;
   private String grimAttemptResult = "--";
   private final ArrayDeque<ScaffoldModule.GrimPredictedPlacement> grimPredictedPlacements = new ArrayDeque<>();
   private final Map<BlockPos, Integer> grimUntrustedPredictions = new HashMap<>();
   private ClientLevel grimPredictionLevel;
   private volatile int grimHighestObservedAck = Integer.MIN_VALUE;
   private int grimHighestProcessedAck = Integer.MIN_VALUE;
   private boolean grimFinalMoveSeen;
   private boolean grimFinalWireGround;
   private boolean grimFinalWireHorizontalCollision;
   private boolean grimFinalWireHasPosition;
   private int grimSprintNoForwardTick = Integer.MIN_VALUE;
   private BlockPos grimEffCell;
   private Vec3 grimPrevTickPos;
   private Vec3 grimLastTickStep = Vec3.ZERO;
   private float grimLaneInputBias;
   private int grimInputForwardOctant;
   private int grimInputSidewaysOctant;
   private int grimInputSidewaysHold;
   private boolean grimInputSidewaysFromCorrection;
   private float grimInputRawForward;
   private float grimInputRawSideways;
   private float grimInputDeltaYaw = Float.NaN;
   private int grimLaneCoastTicks;
   private BlockPos grimLaunchReservedSupport;
   private BlockPos grimLaunchReservedConnector;
   private BlockPos grimLaunchReservedRiser;
   private BlockPos grimLaunchReservedStep;
   private boolean grimLaunchReservationAirborne;
   private String grimLaunchReservationStage = "--";
   private boolean grimPhysicalClimbIntent;
   private int grimLastRescueTick = Integer.MIN_VALUE;
   private int grimPitchFreedTick = Integer.MIN_VALUE;
   private boolean grimTraceEdgeDanger;
   private boolean grimTraceFallDanger;
   private boolean grimTraceLateralBrink;
   private String grimTraceBrake = "--";
   private String grimTraceArcCarry = "--";
   private double grimTraceArcTravel;
   private String grimTraceArcStand = "--";
   private Vec3 grimArcTravelStart;
   private boolean grimTraceFootingOwed;
   private int grimFootingOwedTicks;
   private boolean grimTracePaceBrink;
   private boolean grimTraceLastChance;
   private String grimTraceJump = "-";
   private BlockPos grimTraceRiseTakeoff;
   private boolean grimTraceRiseAllowed;
   private boolean grimTraceClickFeasible;
   private boolean grimTraceClickLands;
   private String grimTraceLaneAnchor = "cam";
   private String grimTraceRiserFail = "--";
   private String grimTraceLaunchLedger = "--";
   private final StringBuilder grimTraceStrip = new StringBuilder();
   private String grimTraceClickNumbers = "--";
   private int grimArcStartTick = -1;
   private int grimArcStartClientTick = -1;
   private Vec3 grimArcStartPos;
   private String grimArcStartGoal = "--";
   private int grimArcPlaceCount;
   private int grimArcSetCount;
   private int grimArcAimTicks;
   private int grimArcPaceTicks;
   private int grimArcNoTargetTicks;
   private int grimArcDropTicks;
   private String grimTraceReserveWhy = "--";
   private BlockPos grimArcChainSupport;
   private BlockPos grimArcChainRiser;
   private int grimArcChainSupportOk = -1;
   private int grimArcChainSupportSet = -1;
   private int grimArcChainRiserFirst = -1;
   private int grimArcChainRiserLast = -1;
   private int grimArcChainRiserSet = -1;
   private String grimArcChainRiserWhy = "--";
   private String grimArcChainRiserFail = "--";
   private int grimArcChainRelatch;
   private String grimTraceWhy = "--";
   private String grimTraceRiseDropWhy = "--";
   private long grimTracePaceSince = -1L;
   private long grimTracePaceFloor = -1L;
   private boolean grimTracePaceIntave;
   private boolean grimTracePrevGnd = true;
   private boolean grimTraceFallNoted;
   private int grimSegTicks;
   private int grimSegPlaces;
   private int grimSegSettled;
   private int grimSegMiss;
   private int grimSegAim;
   private int grimSegPace;
   private int grimSegNoTarget;
   private int grimSegDrop;
   private int grimSegReplan;
   private int grimSegVeto;
   private int grimSegRow = Integer.MIN_VALUE;
   private int grimEffCellRefreshTick = Integer.MIN_VALUE;
   private String grimLastPlanFail = "?";
   private final StringBuilder grimPlanDetail = new StringBuilder();
   private String grimLastGoalEye = "?";
   private String grimLastPick = "?";
   private BlockPos grimPinSupport;
   private Direction grimPinFace;
   private BlockPos grimStaleSupport;
   private Direction grimStaleFace;
   private int grimStaleTicks;
   private float grimTracePrevYaw = Float.NaN;
   private float grimTracePrevPitch = Float.NaN;
   private double grimTraceYawSum;
   private double grimTracePitchSum;
   private int grimTraceMoveTicks;
   private int grimTracePrevTick = Integer.MIN_VALUE;
   private float grimTracePrevSentYaw = Float.NaN;
   private BlockPos grimTraceSettledCell;
   private int tellyDriftLastTick = Integer.MIN_VALUE;
   private boolean tellyDriftWasGrounded = true;
   private int tellyDriftTicks;
   private int tellyDriftGroundTicks;
   private int tellyDriftSprintTicks;
   private double tellyDriftResidualSum;
   private double tellyDriftLateralSum;
   private double tellyDriftRunningLateral;
   private double tellyDriftLaneAtTakeoff = Double.NaN;
   private double tellyDriftCourseAtTakeoff = Double.NaN;
   private int tellyDriftPairTick = Integer.MIN_VALUE;
   private boolean tellyDriftPairGrounded;
   private boolean tellyDriftPairJumped;
   private float tellyDriftPairAnchorYaw = Float.NaN;
   private float grimEmittedPitchStep;
   private float grimLastPlaceYaw = Float.NaN;
   private RiptideRotationUtil.Rotation grimAimRaw;
   static final float GRIM_AIM_MIDPOINT = 0.35F;
   private int grimDitherCounts;
   private BlockHitResult grimLastArmRay;
   private float grimBridgePitchHold = Float.NaN;
   private int grimWindDownElapsed;
   private static final double TELLY_LANE_ENTER = 0.08;
   private static final double TELLY_LANE_EXIT = 0.035;
   private static final double TELLY_LANE_VELOCITY_EXIT = 0.012;
   private static final double TELLY_LANE_PREDICT_TICKS = 3.0;
   private static final double TELLY_LANE_PREDICT_ENTER = 0.1;
   private static final double TELLY_LANE_VELOCITY_GAIN = 0.18;
   private static final double TELLY_LANE_MAX_VELOCITY = 0.055;
   private static final float TELLY_LANE_MIN_STEER = 8.0F;
   private static final float TELLY_LANE_MAX_STEER = 15.0F;
   private static final float TELLY_LANE_OUTWARD_SLEW = 5.0F;
   private static final float TELLY_LANE_RETURN_SLEW = 7.0F;
   private static final double TELLY_LANE_RETURN_MARGIN = 0.1;
   private static final double TELLY_FACE_VISIBILITY_EPSILON = 0.0125;
   static final double TELLY_AIR_CONTROL = 0.0196;
   private static final double TELLY_AIR_DRAG = 0.91;
   private static final double TELLY_AIR_LANE_ENTER = 0.3;
   private static final double TELLY_AIR_LANE_EXIT = 0.22;
   private static final double TELLY_AIR_LANE_EMERGENCY = 0.48;
   private static final int TELLY_AIR_STRAFE_COOLDOWN_TICKS = 2;
   private static final int TELLY_AIR_ROUTINE_PULSE_LIMIT = 2;
   private static final int TELLY_AIR_EMERGENCY_PULSE_LIMIT = 3;
   private static final double TELLY_SAFE_OVERLAP = 0.12;
   private static final int TELLY_FORWARD_DWELL_TICKS = 0;
   private static final double[] TELLY_FACE_OFFSETS = new double[]{0.0, -0.16, 0.16, -0.28, 0.28};
   private static final float TELLY_GROUND_MAX_STEP = 130.0F;
   static final int TELLY_RETURN_LEAD_TICKS = 2;
   static final double TELLY_LANE_TICK_AUTHORITY = 0.026;
   static final float TELLY_LANE_BIAS_MAX = 15.0F;
   static final float TELLY_LANE_BIAS_SLEW = 3.0F;
   private static final float TELLY_MOUSE_BURST_MAX_STEP = 95.0F;
   private static final float TELLY_MOUSE_BURST_RESCUE_STEP = 179.5F;
   private static final int TELLY_AIM_SWEEP_RESERVE_TICKS = 1;
   private static final float TELLY_SETTLE_YAW_EPSILON = 1.0F;
   private static final float TELLY_LAUNCH_YAW_EPSILON = 2.0F;
   private static final double TELLY_SETTLE_SPEED_FLOOR = 0.08;
   private static final float TELLY_SETTLE_VELOCITY_ANGLE = 25.0F;
   private static final double TELLY_SETTLE_LANE_EPSILON = 0.45;
   private static final int TELLY_SETTLE_DWELL_TICKS = 1;
   private static final int TELLY_SETTLE_TIMEOUT_TICKS = 30;
   private int tellyPipelineTick = Integer.MIN_VALUE;
   private static final int TELLY_HELD_YAW_DITHER_PERCENT = 40;
   private String grimTraceTakeoffWhy = "air";
   private BlockPos grimRiseFloorCell;
   private int grimRiseFloorTick = Integer.MIN_VALUE;
   private BlockPos grimRiseTakeoffLatch;
   private int grimRiseTakeoffLatchTicks;
   private ScaffoldModule.TellyStrafe tellyHoldStrafe = ScaffoldModule.TellyStrafe.NONE;
   private static float grimSentYaw = Float.NaN;
   private static int grimSentYawTick = Integer.MIN_VALUE;
   private int grimPaceWaitTicks;
   static final int GRIM_WALK_LEAD_MAX_TICKS = 4;
   private int grimLaneCorrectSide;
   private int grimLaneCorrectHoldTicks;
   private int grimLaneCorrectLockTicks;
   private boolean grimLaneSweepActive;
   private int grimLaneSweepTicks;
   private static final double[] TELLY_STRAFE_FLIP_BOUNDARIES = new double[]{30.0, 150.0, -150.0, -30.0};
   private int grimYawVetoTicks;
   private int grimGoalVetoTicks;
   private float grimGoalVetoLastErr = Float.NaN;
   private int grimStickyPitchMissTicks;
   private int grimFootingSurfaceY = Integer.MIN_VALUE;
   private int grimLastRowGainTick = Integer.MIN_VALUE;
   private int grimAirborneBuiltRow = Integer.MIN_VALUE;
   private double grimTraceCrossing = Double.NaN;
   private static BlockPos grimSupportBoxKey;
   private static BlockState grimSupportBoxState;
   private static AABB grimSupportBoxValue;
   static final float GRIM_PLACE_PITCH_PARK = 89.3F;
   private static final double GRIM_FACE_SPAN_MARGIN = 0.06;

   ScaffoldModule() {
      super("scaffold", "Scaffold", ModuleCategory.MOVEMENT, "Places blocks beneath you.");
      this.add(new ChoiceSetting("mode", "Mode", "Legit", "Legit", "Rage", "Telly", "Andromeda").description("Choose scaffold mode.").build());
      this.add(
         new BoolSetting("remove-limits", "Remove Limits", false)
            .visibleWhen(this::isGrimMode)
            .description("Faster rotations and placements, no climb brake.")
            .build()
      );
      this.add(new BoolSetting("switch-back", "Switch Back", true).description("Restore previous hotbar slot.").build());
      this.add(new ChoiceSetting("filter-mode", "Filter", "Off", "Off", "Whitelist", "Blacklist").description("Choose filter mode.").build());
      this.add(new IntSetting("rage-blocks", "Blocks Ahead", 0, 0, 5, 1).visibleWhen(this::isRageMode).description("Blocks built ahead.").build());
      this.add(
         RegistryListSetting.placeableBlocks("blocks", "Blocks")
            .visibleWhen(() -> !"Off".equals(this.choice("filter-mode")))
            .description("Choose filtered blocks.")
            .build()
      );
      this.add(new BoolSetting("place-animation", "Place Animation", true).description("Placement ripple effect").group("Animation").build());
      this.add(
         new BoolSetting("animation-custom", "Custom Color", false)
            .description("Custom ripple color")
            .group("Animation")
            .visibleWhen(() -> this.bool("place-animation"))
            .build()
      );
      this.add(
         new ColorSetting("animation-color", "Color", -50373)
            .description("Ripple color")
            .group("Animation")
            .visibleWhen(() -> this.bool("place-animation") && this.bool("animation-custom"))
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.clearRuntime(true);
      this.rollGrimSessionOffsets();
      this.grimLiveTraceTicks = -1;
      this.tellyLiveTraceTicks = this.tellyTraceArmTicks();
      this.tellyTracePrintedTick = Integer.MIN_VALUE;
      this.grimTracePrevYaw = Float.NaN;
      this.grimTracePrevPitch = Float.NaN;
      this.grimTraceYawSum = 0.0;
      this.grimTracePitchSum = 0.0;
      this.grimTraceMoveTicks = 0;
      this.pushAnimation();
   }

   @Override
   public String info() {
      return this.choice("mode");
   }

   @Override
   public void onDisable() {
      RiptideRotationUtil.Rotation live = this.grimSilentRotation;
      RiptideRotationUtil.Rotation liveRaw = this.grimAimRaw;
      float liveSent = grimSentYaw;
      boolean tellyLive = RiptideHumanRotation.isInitialized(this.tellyStream);
      this.clearRuntime(false);
      if (live != null && MC != null && MC.player != null) {
         if (tellyLive) {
            RiptideHumanRotation.seed(this.tellyStream, live);
         }

         this.grimSilentRotation = live;
         this.grimAimRaw = liveRaw != null ? liveRaw : live;
         grimSentYaw = liveSent;
         this.grimRotationResetTicks = 5;
         this.grimWindingDown = true;
      }

      RiptideScaffoldPlaceRenderer.disable();
   }

   @Override
   public void onGameLeft() {
      this.clearRuntime(false);
   }

   @Override
   protected void onOptionValueChanged(String settingId) {
      if ("blocks".equals(settingId) || "filter-mode".equals(settingId)) {
         this.cachedFilterRaw = null;
      }

      if ("switch-back".equals(settingId) && !this.bool("switch-back")) {
         this.originalSlot = -1;
         this.slotResetTicks = 0;
      }

      if ("mode".equals(settingId)) {
         this.clearRuntime(true);
         this.grimLiveTraceTicks = -1;
         this.tellyLiveTraceTicks = this.tellyTraceArmTicks();
         this.tellyTracePrintedTick = Integer.MIN_VALUE;
      }

      this.pushAnimation();
   }

   private int tellyTraceArmTicks() {
      if (this.isTellyMode()) {
      }

      return -1;
   }

   private void pushAnimation() {
      RiptideScaffoldPlaceRenderer.push(
         this.isEnabled() && this.bool("place-animation"), this.bool("animation-custom"), ModuleRenderUtil.color(this, "animation-color", -50373)
      );
   }

   @Override
   public void preMovementTick() {
      if (this.isTellyMode()) {
         this.snapshotGrimTickStep();
         this.runTellyTick();
      } else if (this.isGrimFamily()) {
         this.ensureGrimPredictionLevel();
         this.grimTraceSettledCell = null;
         this.grimTickArcBudget();
         this.settleGrimRealClick();
         this.snapshotGrimTickStep();
         this.runGrimPlacement();
      } else if (this.isAndromedaMode()) {
         this.runAndromedaPlacement();
      } else {
         this.runRagePlacement();
      }
   }

   static int compassStep(float yaw) {
      int step = Math.round(Mth.wrapDegrees(yaw) / 45.0F);
      return (step % 8 + 8) % 8;
   }

   static float compassStepYaw(int step) {
      return Mth.wrapDegrees(step * 45.0F);
   }

   static boolean compassStepIsDiagonal(int step) {
      return step >= 0 && (step & 1) == 1;
   }

   static int[] nextCourseStep(int held, int candidate, int dwell, float cameraYaw, boolean frozen) {
      int want = compassStep(cameraYaw);
      if (held == -1) {
         return new int[]{want, -1, 0};
      } else if (frozen) {
         return new int[]{held, candidate, dwell};
      } else if (want == held) {
         return new int[]{held, -1, 0};
      } else if (Math.abs(Mth.wrapDegrees(cameraYaw - compassStepYaw(held))) <= 37.5F) {
         return new int[]{held, -1, 0};
      } else {
         int nextDwell = want == candidate ? dwell + 1 : 1;
         return nextDwell >= 2 ? new int[]{want, -1, 0} : new int[]{held, want, nextDwell};
      }
   }

   static float[] nextPostureYaw(float held, float candidate, int streak, float requested) {
      if (Float.isNaN(held)) {
         return new float[]{requested, Float.NaN, 0.0F};
      } else if (requested == held) {
         return new float[]{held, Float.NaN, 0.0F};
      } else {
         int nextStreak = requested == candidate ? streak + 1 : 1;
         return nextStreak >= 2 ? new float[]{requested, Float.NaN, 0.0F} : new float[]{held, requested, nextStreak};
      }
   }

   private void updateGrimCourseStep() {
      if (MC.player != null) {
         int[] next = nextCourseStep(this.grimCourseStep, this.grimCourseStepCandidate, this.grimCourseStepDwell, MC.player.getYRot(), this.grimCourseFrozen());
         this.grimCourseStep = next[0];
         this.grimCourseStepCandidate = next[1];
         this.grimCourseStepDwell = next[2];
      }
   }

   private float grimCourseStepYaw() {
      return this.grimCourseStep == -1 ? (MC.player == null ? 0.0F : MC.player.getYRot()) : compassStepYaw(this.grimCourseStep);
   }

   static float inputOctantDegrees(Input input) {
      float yaw = 0.0F;
      float forwardMultiplier;
      if (input.backward() && !input.forward()) {
         yaw += 180.0F;
         forwardMultiplier = -0.5F;
      } else if (input.forward() && !input.backward()) {
         forwardMultiplier = 0.5F;
      } else {
         forwardMultiplier = 1.0F;
      }

      if (input.left() && !input.right()) {
         yaw -= 90.0F * forwardMultiplier;
      }

      if (input.right() && !input.left()) {
         yaw += 90.0F * forwardMultiplier;
      }

      return yaw;
   }

   static int inputOctantSteps(Input input) {
      if (!hasDirectionalInput(input)) {
         return 0;
      } else {
         int steps = Math.round(inputOctantDegrees(input) / 45.0F);
         return (steps % 8 + 8) % 8;
      }
   }

   static int laneStep(int courseStep, Input input) {
      return laneStep(courseStep, inputOctantSteps(input));
   }

   static int laneStep(int courseStep, int octantSteps) {
      return courseStep == -1 ? -1 : ((courseStep + octantSteps) % 8 + 8) % 8;
   }

   private void updateGrimLaneOctant(Input input) {
      if (hasDirectionalInput(input) && !this.grimCourseFrozen()) {
         this.grimLaneOctant = inputOctantSteps(input);
      }
   }

   private int grimLaneStep() {
      return laneStep(this.grimCourseStep, this.grimLaneOctant);
   }

   private float grimLaneStepYaw() {
      int lane = this.grimLaneStep();
      return lane == -1 ? this.grimCourseStepYaw() : compassStepYaw(lane);
   }

   private Vec3 grimLaneStepDirection() {
      return Vec3.directionFromRotation(0.0F, this.grimLaneStepYaw());
   }

   private float grimSteeredPostureYaw() {
      if (this.grimTowerActive() && this.grimLaneStep() == -1) {
         return this.grimSilentRotation != null ? this.grimSilentRotation.yaw() : MC.player.getYRot();
      } else {
         float requested = grimPlacementPostureYaw(this.grimLaneStepYaw());
         int tick = RiptideSharedState.get().getClientTickCounter();
         if (tick != this.grimPostureYawTick) {
            float[] next = nextPostureYaw(this.grimPostureYawHeld, this.grimPostureYawCandidate, this.grimPostureYawStreak, requested);
            this.grimPostureYawHeld = next[0];
            this.grimPostureYawCandidate = next[1];
            this.grimPostureYawStreak = (int)next[2];
            this.grimPostureYawTick = tick;
         }

         return this.grimPostureYawHeld;
      }
   }

   static Vec3 tellyTickStep(Vec3 previous, Vec3 current) {
      return previous == null ? Vec3.ZERO : current.subtract(previous);
   }

   private void snapshotGrimTickStep() {
      if (MC.player != null) {
         Vec3 pos = MC.player.position();
         this.grimLastTickStep = tellyTickStep(this.grimPrevTickPos, pos);
         this.grimPrevTickPos = pos;
         this.grimTraceRiseAllowed = false;
      }
   }

   private Vec3 grimLeadStep() {
      if (MC.player == null) {
         return Vec3.ZERO;
      } else if (MC.player.onGround()) {
         return new Vec3(this.grimLastTickStep.x, 0.0, this.grimLastTickStep.z);
      } else {
         Vec3 delta = MC.player.getDeltaMovement();
         int tick = RiptideSharedState.get().getClientTickCounter();
         if (tick != this.grimAirLeadTick) {
            this.grimAirLeadTick = tick;
            Input keys = MC.player.input == null ? null : MC.player.input.keyPresses;
            boolean directional = hasDirectionalInput(keys);
            boolean braking = directional && (this.grimArcBrake(true, false) || this.grimDiagonalClimbBrake(true, false));
            this.grimAirLeadAccel = grimAirLeadAccel(delta, directional, braking, keys != null && keys.shift());
         }

         return delta.add(this.grimAirLeadAccel);
      }
   }

   static Vec3 grimAirLeadAccel(Vec3 velocity, boolean directional, boolean braking, boolean sneaking) {
      double speed = Math.hypot(velocity.x, velocity.z);
      if (directional && !(speed < 0.003)) {
         double impulse = 0.02 * (sneaking ? 0.3 : 1.0) / speed;
         if (braking) {
            impulse = -impulse;
         }

         return new Vec3(velocity.x * impulse, 0.0, velocity.z * impulse);
      } else {
         return Vec3.ZERO;
      }
   }

   private void runRagePlacement() {
      if (!this.canRun()) {
         this.currentMovementLine = null;
         this.tickSlotReset();
      } else if (!RiptideBlinkManager.holdsActionsWithoutMovement()) {
         this.selectionPending = false;
         InteractionHand hand = this.ensurePlacementHand();
         if (hand == null) {
            if (this.selectionPending) {
               this.refreshSelectionReset();
            } else {
               this.tickSlotReset();
            }
         } else {
            this.refreshSelectionReset();
            ItemStack stack = MC.player.getItemInHand(hand);
            if (this.isValidBlock(stack)) {
               BlockPos base = this.rageWalkwayCell();
               if (base != null) {
                  BlockPos step = this.rageCourseStep();
                  int ahead = step == null ? 0 : Math.max(0, Math.min(5, this.integer("rage-blocks")));
                  if (RiptidePlacementTick.claim(this.id())) {
                     for (BlockPos cell : rageLaneCells(base, step, ahead, this::solidAt)) {
                        this.placeRageCell(cell, hand, stack);
                     }
                  }
               }
            }
         }
      }
   }

   static List<BlockPos> rageLaneCells(BlockPos base, BlockPos step, int ahead, Predicate<BlockPos> solid) {
      List<BlockPos> cells = new ArrayList<>();
      if (base == null) {
         return cells;
      } else if (step == null) {
         cells.add(base);
         return cells;
      } else {
         boolean diagonal = step.getX() != 0 && step.getZ() != 0;

         for (int i = 0; i <= Math.max(0, ahead); i++) {
            BlockPos lane = base.offset(step.getX() * i, 0, step.getZ() * i);
            cells.add(lane);
            if (diagonal && i != 0) {
               BlockPos alongX = lane.offset(-step.getX(), 0, 0);
               BlockPos alongZ = lane.offset(0, 0, -step.getZ());
               if (!solid.test(alongX) && !solid.test(alongZ)) {
                  cells.add(alongX);
               }
            }
         }

         return cells;
      }
   }

   private BlockPos rageWalkwayCell() {
      Vec3 vec = MC.player.position().add(MC.player.getDeltaMovement()).add(0.0, -0.75, 0.0);
      BlockPos cell = BlockPos.containing(vec.x, vec.y, vec.z);
      int footY = MC.player.blockPosition().getY();
      if (cell.getY() >= footY) {
         cell = new BlockPos(cell.getX(), footY - 1, cell.getZ());
      }

      return MC.level.isOutsideBuildHeight(cell) ? null : cell;
   }

   private BlockPos rageCourseStep() {
      if (MC.options == null) {
         return null;
      } else {
         Vec3 look = Vec3.directionFromRotation(0.0F, MC.player.getYRot());
         double x = 0.0;
         double z = 0.0;
         if (physicallyDown(MC.options.keyUp)) {
            x += look.x;
            z += look.z;
         }

         if (physicallyDown(MC.options.keyDown)) {
            x -= look.x;
            z -= look.z;
         }

         if (physicallyDown(MC.options.keyLeft)) {
            x += look.z;
            z -= look.x;
         }

         if (physicallyDown(MC.options.keyRight)) {
            x -= look.z;
            z += look.x;
         }

         return rageStepFromDirection(x, z);
      }
   }

   static BlockPos rageStepFromDirection(double x, double z) {
      if (x * x + z * z <= 1.0E-8) {
         return null;
      } else {
         double yaw = Math.toRadians(compassStepYaw(compassStep((float)Math.toDegrees(Math.atan2(-x, z)))));
         int stepX = (int)Math.round(-Math.sin(yaw));
         int stepZ = (int)Math.round(Math.cos(yaw));
         return stepX == 0 && stepZ == 0 ? null : new BlockPos(stepX, 0, stepZ);
      }
   }

   private boolean placeRageCell(BlockPos cell, InteractionHand hand, ItemStack stack) {
      if (cell != null && !MC.level.isOutsideBuildHeight(cell)) {
         BlockState state = MC.level.getBlockState(cell);
         if (state.canBeReplaced() && !this.isSolidSupport(state, cell)) {
            if (!grimCellClearOfBody(MC.player.getBoundingBox(), MC.player.getDeltaMovement(), cell)) {
               return false;
            } else {
               Direction side = this.ragePlaceSide(cell);
               if (side == null) {
                  return false;
               } else {
                  BlockPos neighbour = cell.relative(side);
                  Vec3 hitPos = grimFaceCentre(grimSupportBox(neighbour), side.getOpposite());
                  Vec3 eye = MC.player.getEyePosition();
                  double reach = Math.max(MC.player.blockInteractionRange(), MC.player.entityInteractionRange());
                  if (eye.distanceToSqr(hitPos) > reach * reach) {
                     return false;
                  } else {
                     BlockHitResult hit = new BlockHitResult(hitPos, side.getOpposite(), neighbour, false);
                     if (ModuleRegistry.shouldCancelUseExcept(hit, hand, this.id())) {
                        return false;
                     } else {
                        ScaffoldModule.PlacementTarget target = new ScaffoldModule.PlacementTarget(
                           neighbour, cell, side.getOpposite(), hit, RiptideRotationUtil.lookingAt(hitPos, eye), cell.getY()
                        );
                        return this.place(target, hand, stack);
                     }
                  }
               }
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private Direction ragePlaceSide(BlockPos cell) {
      Vec3 look = Vec3.atCenterOf(cell).subtract(MC.player.getEyePosition());
      double bestRelevancy = -Double.MAX_VALUE;
      Direction best = null;

      for (Direction side : Direction.values()) {
         BlockPos neighbour = cell.relative(side);
         if (!MC.level.isOutsideBuildHeight(neighbour)) {
            BlockState state = MC.level.getBlockState(neighbour);
            if (!state.canBeReplaced() && state.getFluidState().isEmpty()) {
               double relevancy = side.getAxis().choose(look.x, look.y, look.z) * side.getAxisDirection().getStep();
               if (relevancy > bestRelevancy) {
                  bestRelevancy = relevancy;
                  best = side;
               }
            }
         }
      }

      return best;
   }

   private void runGrimPlacement() {
      if (!this.canRun()) {
         this.currentMovementLine = null;
         this.grimEdgeSneakActive = false;
         this.grimEdgeLockedLine = null;
         this.advanceGrimIdleStream(false);
         this.tickSlotReset();
      } else {
         this.grimTraceClickLands = false;
         this.grimTracePaceBrink = false;
         this.grimTraceLastChance = false;
         this.grimTracePaceIntave = false;
         if (this.grimAttemptBlocksRearm()) {
            this.grimTraceWhy = "attempt-" + this.grimAttemptState.name().toLowerCase(Locale.ROOT);
            this.traceGrim("real-wait", this.grimRealPendingTarget);
            if (this.grimRealPendingTarget != null) {
               this.advanceGrimRotation(this.grimAimGoalWithMissFallback(this.grimRealPendingTarget));
            } else {
               this.advanceGrimNoTarget();
            }
         } else if (RiptideBlinkManager.holdsActionsWithoutMovement()) {
            this.grimTraceWhy = "blink-action-only";
            this.traceGrim("real-wait", this.grimRealPendingTarget);
            this.tickSlotReset();
         } else {
            this.grimEffectiveFootCell();
            ItemStack planningStack = this.planningStack();
            ScaffoldModule.PlacementTarget target = this.findPlacementTarget(planningStack);
            if (target == null) {
               this.grimNoTargetTicks++;
               this.grimNotePin(null);
               this.grimTraceWhy = "plan";
               this.traceGrim("no-target " + this.grimLastPlanFail, null);
               this.advanceGrimNoTarget();
               this.tickSlotReset();
            } else {
               this.selectionPending = false;
               InteractionHand hand = this.ensurePlacementHand();
               if (hand == null) {
                  if (this.selectionPending) {
                     this.refreshSelectionReset();
                  } else {
                     this.tickSlotReset();
                  }

                  this.grimNotePin(target);
                  this.grimTraceWhy = "hand";
                  this.traceGrim("hand-switch", target);
                  this.advanceGrimRotation(target.rotation());
               } else {
                  this.refreshSelectionReset();
                  ItemStack stack = MC.player.getItemInHand(hand);
                  target = this.findPlacementTarget(stack);
                  if (target == null) {
                     this.grimNoTargetTicks++;
                     this.grimNotePin(null);
                     this.grimTraceWhy = "plan";
                     this.traceGrim("no-target-2 " + this.grimLastPlanFail, null);
                     this.advanceGrimNoTarget();
                  } else {
                     this.grimNoTargetTicks = 0;
                     if (this.grimRiseDropApplies(target)) {
                        this.grimStickyTarget = null;
                        this.grimNotePin(null);
                        boolean droppedTopHasASide = false;
                        if (target.face() == Direction.UP) {
                           ScaffoldModule.PlacementTarget viaSide = this.grimRiserSideFallback(
                              target.placedBlock(),
                              target.supportBlock(),
                              this.predictedPlacementPosition(this.currentMovementLine),
                              stack,
                              this.currentMovementLine == null ? null : this.currentMovementLine.direction()
                           );
                           if (viaSide != null && !this.grimRiseDropApplies(viaSide)) {
                              target = viaSide;
                              this.grimSegReplan++;
                              droppedTopHasASide = true;
                           }
                        }

                        if (!droppedTopHasASide) {
                           if (!grimRiseDropReselects(MC.player.onGround(), this.grimJumpKeyHeld())) {
                              this.grimTraceWhy = "drop-" + this.grimTraceRiseDropWhy;
                              this.traceGrim("rise-drop", target);
                              this.advanceGrimRotation(this.grimRestPoseGoal());
                              return;
                           }

                           this.grimMarkCellDead(target.placedBlock());
                           ScaffoldModule.PlacementTarget replanned = this.findPlacementTarget(stack);
                           if (replanned == null || this.grimRiseDropApplies(replanned)) {
                              this.grimTraceWhy = "drop-" + this.grimTraceRiseDropWhy;
                              this.traceGrim("rise-drop", target);
                              this.advanceGrimRotation(this.grimRestPoseGoal());
                              return;
                           }

                           target = replanned;
                           this.grimSegReplan++;
                        }
                     }

                     this.grimNotePin(target);
                     this.grimBridgePitchHold = target.rotation().pitch();
                     ScaffoldModule.GrimWireClickRotation wireClick = this.grimWireClickRotation();
                     RiptideRotationUtil.Rotation clickRotation = wireClick == null ? null : wireClick.current();
                     boolean rayLands = clickRotation != null && this.grimRealClickLands(target, clickRotation);
                     ScaffoldModule.PlacementTarget laneRay = null;
                     if (clickRotation != null && !rayLands) {
                        laneRay = this.grimLaneRayTarget(target, this.grimLastArmRay);
                        if (laneRay != null && this.grimRiseDropApplies(laneRay)) {
                           laneRay = null;
                        }
                     }

                     ScaffoldModule.PlacementTarget clickable = rayLands ? target : laneRay;
                     boolean paceFreesPitch = false;
                     if (clickable != null && this.grimPaceHolds(clickable, clickRotation)) {
                        paceFreesPitch = true;
                        this.grimPitchFreedTick = RiptideSharedState.get().getClientTickCounter();
                     }

                     this.advanceGrimRotation(this.grimAimGoalWithMissFallback(target), grimWirePitchFrozen(rayLands || laneRay != null, paceFreesPitch));
                     if (wireClick == null) {
                        this.grimTraceWhy = "wire-wait";
                        this.traceGrim("aim-hold miss=no-wire", target);
                     } else {
                        if (!rayLands) {
                           if (laneRay == null) {
                              if (this.grimTargetOutOfReach(target)) {
                                 this.grimNoTargetTicks = Math.max(this.grimNoTargetTicks, 1);
                              }

                              this.grimNoteAimMiss(target, clickRotation);
                              String waitCode = this.grimAimWaitCode(target);
                              this.grimNoteCrossingWait(target, waitCode);
                              this.grimTraceWhy = "aim-" + waitCode;
                              this.traceGrim("aim-hold" + this.traceClickMiss(target), target);
                              return;
                           }

                           target = laneRay;
                           this.grimNotePin(laneRay);
                        }

                        if (clickRotation != null) {
                           float emittedPitch = clickRotation.pitch();
                           if (!grimPlacementPitchLegal(emittedPitch)) {
                              this.grimTraceWhy = "pveto-" + (int)emittedPitch;
                              this.traceGrim("pitch-veto", target);
                              return;
                           }

                           if (this.grimYawOffPosture(clickRotation)) {
                              if (++this.grimYawVetoTicks > 2) {
                                 this.grimMarkCellDead(target.placedBlock());
                                 this.grimStickyTarget = null;
                                 this.grimYawVetoTicks = 0;
                              }

                              float yawResidual = this.grimLaneStep() == -1 ? Float.NaN : grimLaneOctantResidual(this.grimLaneStepYaw(), clickRotation.yaw());
                              this.grimTraceWhy = "yawoff-" + (Float.isNaN(yawResidual) ? "?" : String.format(Locale.ROOT, "%.1f", yawResidual));
                              this.traceGrim("yaw-veto", target);
                              return;
                           }

                           this.grimYawVetoTicks = 0;
                           float goalErr = Math.abs(Mth.wrapDegrees(target.rotation().yaw() - clickRotation.yaw()));
                           if (target.face().getAxis().isHorizontal() && grimGoalYawUnconverged(target.rotation().yaw(), clickRotation.yaw())) {
                              boolean closing = grimGoalErrorClosing(goalErr, this.grimGoalVetoLastErr);
                              this.grimGoalVetoLastErr = goalErr;
                              this.grimGoalVetoTicks = closing ? 0 : this.grimGoalVetoTicks + 1;
                              if (this.grimGoalVetoTicks > 2) {
                                 this.grimMarkCellDead(target.placedBlock());
                                 this.grimStickyTarget = null;
                                 this.grimGoalVetoTicks = 0;
                                 this.grimGoalVetoLastErr = Float.NaN;
                              }

                              this.grimTraceWhy = "goaloff-" + (int)goalErr;
                              this.traceGrim("yaw-veto", target);
                              return;
                           }

                           this.grimGoalVetoTicks = 0;
                           this.grimGoalVetoLastErr = Float.NaN;
                           float clickPitchStep = wireClick.previous() == null ? 0.0F : Math.abs(clickRotation.pitch() - wireClick.previous().pitch());
                           boolean descending = !MC.player.onGround() && MC.player.getDeltaMovement().y < 0.0;
                           if (!descending && clickPitchStep > 10.0F) {
                              this.grimTraceWhy = "pstep-" + (int)clickPitchStep;
                              this.traceGrim("pitch-step", target);
                              return;
                           }
                        }

                        this.grimAimMissStreak = 0;
                        this.grimAimWindowWaitTicks = 0;
                        this.grimAimOccludedTicks = 0;
                        int tick = RiptideSharedState.get().getClientTickCounter();
                        if (tick == this.lastGrimPlacementTick) {
                           this.grimTraceWhy = "sametick";
                           this.traceGrim("pace-hold", target);
                        } else {
                           boolean pitchFreed = this.grimPitchFreedTick == tick;
                           if (!paceFreesPitch && !pitchFreed && !this.grimPaceHolds(target, clickRotation)) {
                              if (RiptideBlinkManager.holdsActionsWithoutMovement()) {
                                 this.grimTraceWhy = "blink-action-only";
                                 this.traceGrim("real-wait", target);
                              } else {
                                 this.grimRealPendingTarget = target;
                                 this.grimRealPendingLine = this.currentMovementLine;
                                 this.grimRealPendingFallOff = this.findFallOffPosition(this.currentMovementLine);
                                 this.grimRealQueuedTick = tick;
                                 this.grimAttemptState = ScaffoldModule.GrimPlacementAttemptState.ARMED;
                                 this.grimNextAttemptGeneration = grimNextAttemptGeneration(this.grimNextAttemptGeneration);
                                 this.grimAttemptGeneration = this.grimNextAttemptGeneration;
                                 this.grimAttemptHand = hand;
                                 this.grimAttemptBuildsPlannedCell = this.grimHitBuildsPlannedCell(this.grimLastArmRay, target);
                                 this.grimAttemptSubmittedCount = 0;
                                 this.grimAttemptDuplicateSubmitted = false;
                                 this.grimAttemptWriteCount = 0;
                                 this.grimCommittedClickRotation = clickRotation;
                                 this.grimCommittedPreviousRotation = wireClick.previous();
                                 this.grimAttemptSequence = -1;
                                 this.grimAttemptResultSeen = false;
                                 this.grimAttemptResultConsumed = false;
                                 this.grimAttemptPaceBooked = false;
                                 this.grimAttemptResult = "queued";
                                 this.lastGrimPlacementTick = this.grimRealQueuedTick;
                                 this.grimPaceQueuedNanos = Long.MIN_VALUE;
                                 this.grimPaceJitterMs = this.rotationRandom.nextInt(25);
                                 this.grimTraceClickLands = true;
                                 this.grimTraceWhy = "ok";
                                 RiptideInputClicker.queueScaffoldUseClick(this.grimAttemptGeneration);
                                 ((RiptideMinecraftAccessor)MC).riptide$setRightClickDelay(0);
                                 this.traceGrim("PLACE", target);
                              }
                           } else {
                              if (target.placedBlock().getY() > target.supportBlock().getY() || this.grimLandingChainCell(target.placedBlock())) {
                                 this.grimPaceRiserHoldCell = target.placedBlock().immutable();
                                 this.grimPaceRiserHoldTick = RiptideSharedState.get().getClientTickCounter();
                              }

                              this.grimTraceWhy = !paceFreesPitch && pitchFreed
                                 ? "pace-pfree"
                                 : "pace-" + this.grimTracePaceSince + (this.grimTracePaceIntave ? "/imean" : "/" + this.grimTracePaceFloor + "ms");
                              this.traceGrim("pace-hold", target);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void settleGrimRealClick() {
      this.drainGrimFinalUseWrite();
      this.reconcileGrimPlacementAcks();
      ScaffoldModule.PlacementTarget pending = this.grimRealPendingTarget;
      if (pending != null && MC.level != null && MC.player != null) {
         int tick = RiptideSharedState.get().getClientTickCounter();
         int age = Math.max(0, tick - this.grimRealQueuedTick);
         BlockPos cell = pending.placedBlock();
         boolean solid = this.isSolidSupport(MC.level.getBlockState(cell), cell);
         ScaffoldModule.GrimAttemptDecision decision = grimReduceAttempt(
            this.grimAttemptState,
            age,
            this.grimAttemptResultSeen,
            this.grimAttemptResultConsumed,
            solid,
            grimAckCovers(this.grimAttemptSequence, this.grimHighestObservedAck)
         );
         if (decision.state() == ScaffoldModule.GrimPlacementAttemptState.FAILED) {
            String reason = "use".equals(decision.failure()) ? "use-" + this.grimAttemptResult : decision.failure();
            this.failGrimPlacementAttempt(reason);
         } else {
            this.grimAttemptState = decision.state();
         }

         if (this.grimAttemptState == ScaffoldModule.GrimPlacementAttemptState.RECONCILING) {
            this.grimTraceWhy = "reconcile";
            this.traceGrim("real-wait", pending);
         } else if (this.grimAttemptState == ScaffoldModule.GrimPlacementAttemptState.PREDICTED && solid) {
            this.grimUntrustedPredictions.remove(cell);
            this.trackSuccessfulPlacement(cell, this.grimRealPendingLine, this.grimRealPendingFallOff);
            this.grimTraceSettledCell = cell;
            if (!MC.player.onGround()) {
               this.grimArcPlacements++;
               if (!cell.equals(this.grimLaunchReservedStep)) {
                  this.grimAirborneBuiltRow = Math.max(this.grimAirborneBuiltRow, cell.getY());
               }
            }

            this.rememberGrimPrediction(this.grimAttemptSequence, cell);
            RiptideScaffoldPlaceRenderer.recordPlacement(cell);
            this.grimTraceWhy = "ok";
            this.traceGrim("settle-ok", pending);
            this.clearGrimPlacementAttempt();
         } else {
            if (this.grimAttemptState == ScaffoldModule.GrimPlacementAttemptState.FAILED) {
               this.grimTraceWhy = this.grimAttemptResult;
               this.traceGrim("real-miss", pending);
               this.clearGrimPlacementAttempt();
            }
         }
      } else {
         if (pending == null && this.grimAttemptState != ScaffoldModule.GrimPlacementAttemptState.IDLE) {
            this.clearGrimPlacementAttempt();
         }
      }
   }

   private boolean grimAttemptBlocksRearm() {
      return grimAttemptBlocksRearm(this.grimAttemptState);
   }

   private void failGrimPlacementAttempt(String reason) {
      if (this.grimRealPendingTarget != null && MC != null && MC.level != null) {
         BlockPos cell = this.grimRealPendingTarget.placedBlock();
         if (this.solidAt(cell)) {
            this.quarantineGrimPrediction(cell, this.grimAttemptSequence);
         }
      }

      this.grimAttemptState = ScaffoldModule.GrimPlacementAttemptState.FAILED;
      this.grimAttemptResult = reason == null ? "failed" : reason;
      if (this.grimStickyTarget != null
         && this.grimRealPendingTarget != null
         && this.grimStickyTarget.placedBlock().equals(this.grimRealPendingTarget.placedBlock())) {
         this.grimStickyTarget = null;
      }
   }

   private void clearGrimPlacementAttempt() {
      this.grimRealPendingTarget = null;
      this.grimRealPendingLine = null;
      this.grimRealPendingFallOff = null;
      this.grimRealQueuedTick = Integer.MIN_VALUE;
      this.grimAttemptState = ScaffoldModule.GrimPlacementAttemptState.IDLE;
      this.grimAttemptGeneration = 0L;
      this.grimAttemptHand = null;
      this.grimAttemptBuildsPlannedCell = false;
      this.grimAttemptSubmittedCount = 0;
      this.grimAttemptDuplicateSubmitted = false;
      this.grimAttemptWriteCount = 0;
      this.grimCommittedClickRotation = null;
      this.grimCommittedPreviousRotation = null;
      this.grimAttemptSequence = -1;
      this.grimAttemptResultSeen = false;
      this.grimAttemptResultConsumed = false;
      this.grimAttemptPaceBooked = false;
      this.grimAttemptResult = "--";
   }

   private void rememberGrimPrediction(int sequence, BlockPos cell) {
      if (sequence >= 0 && cell != null && !grimAckCovers(sequence, this.grimHighestObservedAck)) {
         this.grimPredictedPlacements.addLast(new ScaffoldModule.GrimPredictedPlacement(sequence, cell.immutable()));

         while (this.grimPredictedPlacements.size() > 64) {
            this.grimPredictedPlacements.removeFirst();
         }
      }
   }

   private void quarantineGrimPrediction(BlockPos cell, int sequence) {
      if (cell != null) {
         this.grimUntrustedPredictions.merge(cell.immutable(), sequence, (oldSequence, newSequence) -> Math.max(oldSequence, newSequence));
      }
   }

   private void ensureGrimPredictionLevel() {
      ClientLevel level = MC == null ? null : MC.level;
      if (grimPredictionEpochChanged(this.grimPredictionLevel, level)) {
         this.grimPredictionLevel = level;
         this.grimPredictedPlacements.clear();
         this.grimUntrustedPredictions.clear();
         this.grimHighestObservedAck = Integer.MIN_VALUE;
         this.grimHighestProcessedAck = Integer.MIN_VALUE;
         GRIM_FINAL_USE_WRITES.clear();
         if (this.grimRealPendingTarget != null || this.grimAttemptState != ScaffoldModule.GrimPlacementAttemptState.IDLE) {
            this.clearGrimPlacementAttempt();
         }

         this.resetGrimLaunchReservation();
         this.grimLastRowGainTick = Integer.MIN_VALUE;
      }
   }

   static boolean grimPredictionEpochChanged(Object previous, Object current) {
      return previous != current;
   }

   private void reconcileGrimPlacementAcks() {
      int acknowledged = this.grimHighestObservedAck;
      if (acknowledged != Integer.MIN_VALUE && acknowledged > this.grimHighestProcessedAck && MC.level != null) {
         this.grimHighestProcessedAck = acknowledged;
         Iterator<ScaffoldModule.GrimPredictedPlacement> iterator = this.grimPredictedPlacements.iterator();

         while (iterator.hasNext()) {
            ScaffoldModule.GrimPredictedPlacement predicted = iterator.next();
            if (grimAckCovers(predicted.sequence(), acknowledged)) {
               iterator.remove();
               this.grimUntrustedPredictions.remove(predicted.cell());
               if (!this.isSolidSupport(MC.level.getBlockState(predicted.cell()), predicted.cell())) {
                  this.invalidateGrimPredictedPlacement(predicted.cell());
               }
            }
         }
      }
   }

   private void reconcileGrimUntrustedAck(int acknowledged) {
      Iterator<Entry<BlockPos, Integer>> iterator = this.grimUntrustedPredictions.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<BlockPos, Integer> entry = iterator.next();
         int highestSequence = entry.getValue();
         if (grimAckRetiresQuarantine(highestSequence, acknowledged)) {
            iterator.remove();
         }
      }
   }

   static boolean grimAckRetiresQuarantine(int highestSequence, int acknowledged) {
      return highestSequence >= 0 && grimAckCovers(highestSequence, acknowledged);
   }

   private void invalidateGrimPredictedPlacement(BlockPos cell) {
      if (cell != null) {
         this.lastPlacedBlocks.removeIf(cell::equals);
         this.placementOffsets.clear();
         if (this.grimStickyTarget != null && cell.equals(this.grimStickyTarget.placedBlock())) {
            this.grimStickyTarget = null;
         }

         this.grimEdgeLockedLine = null;
         this.grimEffCell = null;
         this.grimLastRescueTick = Integer.MIN_VALUE;
         this.grimTraceWhy = "ack-air";
      }
   }

   private void grimTickArcBudget() {
      if (MC.player != null && !MC.player.onGround()) {
         this.grimArcTicks++;
      } else {
         this.grimArcTicks = 0;
         this.grimArcPlacements = 0;
         this.grimArcCarryOrigin = MC.player == null ? null : MC.player.position();
      }
   }

   private String traceArcCarry() {
      if (MC.player != null && !MC.player.onGround() && this.grimArcCarryOrigin != null) {
         Vec3 now = MC.player.position();
         double dx = now.x - this.grimArcCarryOrigin.x;
         double dz = now.z - this.grimArcCarryOrigin.z;
         return String.format(Locale.ROOT, "carry=%.2f/%.2f", Math.sqrt(dx * dx + dz * dz), 0.8);
      } else {
         return "carry=--";
      }
   }

   private RiptideRotationUtil.Rotation grimAimGoalWithMissFallback(ScaffoldModule.PlacementTarget target) {
      if (MC.player != null
         && target.face().getAxis().isHorizontal()
         && this.grimAimMissStreak >= 3
         && this.grimAimOccludedTicks == 0
         && target.supportBlock().equals(this.grimAimMissSupport)
         && target.face() == this.grimAimMissFace) {
         this.grimLastGoalEye = "miss-direct";
         RiptideRotationUtil.Rotation direct = RiptideRotationUtil.lookingAt(target.hit().getLocation(), MC.player.getEyePosition());
         return new RiptideRotationUtil.Rotation(direct.yaw(), grimPlacementPitchCap(direct.pitch()));
      } else {
         return target.rotation();
      }
   }

   static boolean grimHitInFrontOfFace(Vec3 eye, Vec3 hit, BlockPos support, Direction face) {
      double eyePast = grimEyePastPlane(eye, support, face);
      double hitPast = grimEyePastPlane(hit, support, face);
      return eyePast > 0.0 ? hitPast > 0.01 && hitPast < eyePast : hitPast < -0.01 && hitPast > eyePast;
   }

   private void grimNoteAimMiss(ScaffoldModule.PlacementTarget target, RiptideRotationUtil.Rotation clickRotation) {
      if (!target.supportBlock().equals(this.grimAimMissSupport) || target.face() != this.grimAimMissFace) {
         this.grimAimMissSupport = target.supportBlock();
         this.grimAimMissFace = target.face();
         this.grimAimMissStreak = 0;
         this.grimAimOccludedTicks = 0;
         this.grimAimWindowWaitTicks = 0;
      }

      boolean occluded = this.grimLastArmRay != null
         && !this.grimLastArmRay.getBlockPos().equals(target.supportBlock())
         && this.solidAt(this.grimLastArmRay.getBlockPos())
         && MC.player != null
         && grimHitInFrontOfFace(MC.player.getEyePosition(), this.grimLastArmRay.getLocation(), target.supportBlock(), target.face());
      boolean blank = this.grimLastArmRay == null;
      boolean aimArrived = clickRotation == null || !grimGoalYawUnconverged(target.rotation().yaw(), clickRotation.yaw());
      boolean pastAndBlank = blank && MC.player != null && this.grimEyePastTargetFace(target) && aimArrived;
      if (occluded || pastAndBlank) {
         if (++this.grimAimOccludedTicks >= 2) {
            this.grimMarkCellDead(target.placedBlock());
            this.grimStickyTarget = null;
            if (target.supportBlock().equals(this.grimPinSupport) && target.face() == this.grimPinFace) {
               this.grimPinSupport = null;
               this.grimPinFace = null;
            }

            this.grimAimOccludedTicks = 0;
         }
      } else if (!blank) {
         this.grimAimOccludedTicks = 0;
      }

      if (MC.player == null || !this.grimEyePastTargetFace(target)) {
         this.grimAimMissStreak = 0;
         this.grimAimWindowWaitTicks = 0;
      } else if (this.grimAimWindowOpening(target) && this.grimAimWindowWaitTicks < 6) {
         this.grimAimMissStreak = 0;
         this.grimAimWindowWaitTicks++;
      } else {
         this.grimAimMissStreak++;
         this.grimAimWindowWaitTicks = 0;
      }
   }

   private boolean grimAimWindowOpening(ScaffoldModule.PlacementTarget target) {
      if (MC.player != null && !target.face().getAxis().isVertical()) {
         float yaw = target.rotation().yaw();
         Vec3 lead = this.grimLeadStep();
         if (lead.horizontalDistanceSqr() < 1.0E-4 && this.currentMovementLine != null) {
            lead = new Vec3(this.currentMovementLine.direction().x, 0.0, this.currentMovementLine.direction().z).normalize().scale(0.05);
         }

         Vec3 eye = MC.player.getEyePosition();

         for (int step = 1; step <= 2; step++) {
            Vec3 projected = eye.add(lead.scale(step));
            if (grimCrossingLandsOnFace(projected, target.supportBlock(), target.face(), yaw, true)) {
               float solve = grimSideWindowSolvePitch(projected, target.supportBlock(), target.face(), yaw);
               if (Float.isFinite(solve) && solve <= 89.5F) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   static boolean grimGoalYawUnconverged(float goalYaw, float emittedYaw) {
      return Math.abs(Mth.wrapDegrees(goalYaw - emittedYaw)) > 20.0F;
   }

   private boolean grimFaceOutOfReachThroughApproach(Vec3 eye, BlockPos support, Direction face, float yaw) {
      Vec3 lead = this.grimLeadStep();

      for (int step = 0; step <= 2; step++) {
         if (!grimPitchOutOfReach(grimSideWindowSolvePitch(eye.add(lead.scale(step)), support, face, yaw))) {
            return false;
         }
      }

      return true;
   }

   static boolean grimFaceSelfOccluded(Vec3 eye, BlockPos support, Direction face, Predicate<BlockPos> fullCubeAt) {
      if (eye == null || support == null || face == null || fullCubeAt == null) {
         return false;
      } else if (!face.getAxis().isHorizontal()) {
         return false;
      } else if (Mth.floor(eye.x) == support.getX() && Mth.floor(eye.z) == support.getZ()) {
         BlockPos lid = support.above();
         return eye.y >= lid.getY() + 1.0 && fullCubeAt.test(lid);
      } else {
         return false;
      }
   }

   private boolean grimSelfOccludedThroughApproach(BlockPos support, Direction face) {
      if (MC.player != null && MC.level != null) {
         Vec3 eye = MC.player.getEyePosition();
         Vec3 lead = this.grimLeadStep();
         int horizon = Math.max(2, grimWalkLeadTicks(this.grimPaceWaitTicks));

         for (int step = 0; step <= horizon; step++) {
            if (!grimFaceSelfOccluded(eye.add(lead.scale(step)), support, face, this::grimFullCubeAt)) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private boolean grimFullCubeAt(BlockPos pos) {
      return MC.level != null && pos != null ? MC.level.getBlockState(pos).isCollisionShapeFullBlock(MC.level, pos) : false;
   }

   static float grimSideWindowSolvePitch(Vec3 eye, BlockPos support, Direction face, float yaw) {
      return grimSideWindowSolvePitch(eye, grimSupportBox(support), face, yaw);
   }

   static float grimSideWindowSolvePitch(Vec3 eye, AABB support, Direction face, float yaw) {
      if (support == null) {
         return Float.NaN;
      } else {
         double past = grimEyePastPlane(eye, support, face);
         if (past <= 0.0) {
            return Float.NaN;
         } else {
            double yawRad = Math.toRadians(yaw);
            double lookX = -Math.sin(yawRad);
            double lookZ = Math.cos(yawRad);
            double toward = -(lookX * face.getStepX() + lookZ * face.getStepZ());
            if (toward <= 0.1) {
               return Float.NaN;
            } else {
               double run = past / toward;
               double drop = eye.y - grimFaceCrossDepthY(support);
               return (float)Math.toDegrees(Math.atan2(drop, run));
            }
         }
      }
   }

   static boolean grimRaySubstitutionIsNoOp(BlockPos plannedCell, Direction plannedFace, BlockPos rayCell, Direction rayFace) {
      return grimRaySubstitutionIsNoOp(plannedCell, plannedFace, null, rayCell, rayFace, null);
   }

   static boolean grimRaySubstitutionIsNoOp(
      BlockPos plannedCell, Direction plannedFace, BlockPos plannedSupport, BlockPos rayCell, Direction rayFace, BlockPos raySupport
   ) {
      return rayCell != null && rayCell.equals(plannedCell) && rayFace == plannedFace
         ? plannedSupport == null || raySupport == null || plannedSupport.equals(raySupport)
         : false;
   }

   private ScaffoldModule.PlacementTarget grimLaneRayTarget(ScaffoldModule.PlacementTarget planned, BlockHitResult ray) {
      if (MC.player != null && MC.level != null && this.grimSilentRotation != null && ray != null) {
         BlockPos support = ray.getBlockPos().immutable();
         Direction face = ray.getDirection();
         BlockPos cell = this.grimPlacedCellFor(ray, this.planningStack());
         if (cell == null) {
            return null;
         } else if (grimRaySubstitutionIsNoOp(planned.placedBlock(), planned.face(), planned.supportBlock(), cell, face, support)) {
            return null;
         } else if (!MC.level.getBlockState(cell).canBeReplaced()) {
            return null;
         } else if (!grimCellClearOfBody(MC.player.getBoundingBox(), MC.player.getDeltaMovement(), cell, this.grimFallingCatchPlan(cell))) {
            return null;
         } else if (!this.grimLaneFrontierCell(cell)) {
            return null;
         } else if (!grimRiseRowSubstitutionAllowed(
            cell, this.grimOracleFootingRow(), this.grimLaunchReservedSupport, this.currentMovementLine == null ? this.grimOwnRiserSupport() : null
         )) {
            return null;
         } else {
            return this.grimIsOwnFootingCell(planned.placedBlock()) && !this.grimIsOwnFootingCell(cell)
               ? null
               : new ScaffoldModule.PlacementTarget(support, cell, face, ray, this.grimSilentRotation, cell.getY());
         }
      } else {
         return null;
      }
   }

   static boolean grimRiseRowSubstitutionAllowed(BlockPos cell, int oracleRow, BlockPos reservedSupport, BlockPos ownSupport) {
      return cell.getY() <= oracleRow ? true : grimSameColumn(cell, reservedSupport) || grimSameColumn(cell, ownSupport);
   }

   private boolean grimIsOwnFootingCell(BlockPos cell) {
      return MC.player != null && grimIsOwnFootingCell(cell, this.grimLaneFootCell());
   }

   private BlockPos grimLaneFootCell() {
      int row = this.grimOracleFootingRow();
      return row == Integer.MIN_VALUE ? this.grimEffectiveFootCell() : this.grimFootCellAtRow(row);
   }

   private String grimAimWaitCode(ScaffoldModule.PlacementTarget target) {
      this.grimNoteClickNumbers(target);
      if (this.grimLastArmRay == null) {
         return this.grimEyePastTargetFace(target) ? "blank" : "plane";
      } else if (!this.grimLastArmRay.getBlockPos().equals(target.supportBlock())) {
         return "occ";
      } else if (this.grimLastArmRay.getDirection() != target.face()) {
         return this.grimLastArmRay.getDirection() == Direction.UP ? "u" : "side";
      } else {
         return this.grimTargetOutOfReach(target) ? "reach" : "near";
      }
   }

   private void grimNoteCrossingWait(ScaffoldModule.PlacementTarget target, String waitCode) {
      this.grimCrossingWaitFace = null;
      if (MC.player != null && target != null) {
         if ("plane".equals(waitCode) || "u".equals(waitCode)) {
            if (target.face().getAxis().isHorizontal()) {
               if (!this.grimEyePastTargetFace(target)) {
                  this.grimCrossingWaitFace = target.face();
                  this.grimCrossingWaitTick = RiptideSharedState.get().getClientTickCounter();
               }
            }
         }
      }
   }

   static boolean grimBrakeStarvesCrossing(Direction waitFace, int waitAge, boolean rising, Vec3 lane) {
      return waitFace != null && waitAge == 0 && rising && lane != null ? waitFace.getStepX() * lane.x + waitFace.getStepZ() * lane.z > 0.0 : false;
   }

   static boolean grimCrossingStanddownAllowed(boolean starves, double spentBlocks) {
      return starves && spentBlocks <= 0.35;
   }

   private boolean grimCrossingStanddown(boolean rising, Vec3 lane) {
      int tick = RiptideSharedState.get().getClientTickCounter();
      boolean starves = grimBrakeStarvesCrossing(this.grimCrossingWaitFace, grimTicksSince(tick, this.grimCrossingWaitTick), rising, lane);
      if (tick != this.grimXingStandTick) {
         this.grimXingStandTick = tick;
         this.grimXingStandSpent = starves ? this.grimXingStandSpent + this.grimLaneTravel(lane) : 0.0;
      }

      return grimCrossingStanddownAllowed(starves, this.grimXingStandSpent);
   }

   private double grimLaneTravel(Vec3 lane) {
      if (MC.player != null && lane != null) {
         Vec3 velocity = MC.player.getDeltaMovement();
         return Math.abs(velocity.x * lane.x + velocity.z * lane.z);
      } else {
         return 0.0;
      }
   }

   static boolean grimIsOwnFootingCell(BlockPos cell, BlockPos foot) {
      return cell != null && foot != null && cell.getY() == foot.getY() - 1 && cell.getX() == foot.getX() && cell.getZ() == foot.getZ();
   }

   private boolean grimLaneFrontierCell(BlockPos cell) {
      if (this.currentMovementLine == null) {
         return false;
      } else {
         Vec3 direction = this.currentMovementLine.direction();
         return grimLaneFrontierCell(cell, this.grimLaneFootCell(), horizontalStep(direction.x), horizontalStep(direction.z));
      }
   }

   static boolean grimLaneFrontierCell(BlockPos cell, BlockPos foot, int dx, int dz) {
      if (dx == 0 && dz == 0) {
         return false;
      } else if (cell.getY() != foot.getY() - 1 && cell.getY() != foot.getY()) {
         return false;
      } else {
         int rx = foot.getX() - cell.getX();
         int rz = foot.getZ() - cell.getZ();
         return (rx == 0 || rx == dx) && (rz == 0 || rz == dz);
      }
   }

   private boolean grimRiseDropApplies(ScaffoldModule.PlacementTarget target) {
      if (target != null && MC.player != null && target.face().getAxis().isVertical()) {
         boolean stairRiser = target.face() == Direction.UP && !MC.player.onGround() && target.placedBlock().getY() > this.grimOracleFootingRow();
         Vec3 dropCarry = MC.player.onGround() ? Vec3.ZERO : MC.player.getDeltaMovement();
         boolean own = stairRiser && grimSameColumn(target.placedBlock(), this.grimOwnRiserSupport());
         boolean drop = stairRiser
            ? (
               own
                  ? !grimBoxOverColumn(MC.player.position(), target.placedBlock())
                  : !this.grimArcLandsOnColumnLive(MC.player.position(), MC.player.getDeltaMovement(), target.placedBlock())
            )
            : !footprintOverlapsColumn(MC.player.position(), dropCarry, target.placedBlock());
         if (drop) {
            this.grimTraceRiseDropWhy = stairRiser ? (own ? "own" : "arc") : "foot";
         }

         return drop;
      } else {
         return false;
      }
   }

   static boolean grimRiseDropReselects(boolean onGround, boolean jumpHeld) {
      return onGround && jumpHeld;
   }

   static int grimTicksSince(int tick, int lastTick) {
      if (lastTick == Integer.MIN_VALUE) {
         return -1;
      } else {
         int since = tick - lastTick;
         return since < 0 ? -1 : since;
      }
   }

   private boolean grimPaceHolds(ScaffoldModule.PlacementTarget target, RiptideRotationUtil.Rotation clickRotation) {
      if (MC.player == null) {
         return false;
      } else {
         long nowNanos = System.nanoTime();
         long elapsed = this.grimPaceElapsedMs(nowNanos);
         this.grimTracePaceSince = elapsed;
         if (this.grimRemoveLimits()) {
            this.grimPaceWaitTicks = 0;
            this.grimTracePaceFloor = 0L;
            return false;
         } else {
            boolean airborne = !MC.player.onGround();
            boolean descending = airborne && MC.player.getDeltaMovement().y < 0.0;
            boolean chainCell = airborne && this.grimLandingChainCell(target.placedBlock());
            boolean footCatch = this.grimFallingCatchPlan(target.placedBlock());
            boolean fallingCatch = footCatch && MC.player.fallDistance >= 1.0;
            if (!this.grimLandingLastChance(target) && (!chainCell || !descending) && (!chainCell || !this.grimChainWindowClosing(target)) && !fallingCatch) {
               if (grimPacesAsRiser(target.placedBlock(), target.supportBlock(), this.grimUpFaceSwapCell)) {
                  boolean landingRiser = !MC.player.onGround()
                     && MC.player.getDeltaMovement().y < 0.0
                     && target.placedBlock().getY() > target.supportBlock().getY();
                  long floor = landingRiser ? 50L : 100L;
                  this.grimTracePaceFloor = floor;
                  return grimPaceFloorHolds(elapsed, floor);
               } else if (chainCell || footCatch) {
                  this.grimTracePaceBrink = true;
                  this.grimTracePaceFloor = 100L;
                  return grimPaceFloorHolds(elapsed, 100L);
               } else if (this.grimRiseFloorAwaiting(target.placedBlock())) {
                  this.grimTracePaceBrink = true;
                  this.grimTracePaceFloor = 100L;
                  return grimPaceFloorHolds(elapsed, 100L);
               } else if (!this.grimFootingBrink(target)) {
                  List<BlockPos> booked = new ArrayList<>(this.grimPaceSamples.size() + 1);

                  for (ScaffoldModule.GrimPaceSample sample : this.grimPaceSamples) {
                     booked.add(sample.placed());
                  }

                  booked.add(target.placedBlock());
                  long limit = grimPaceLimitMs(
                     grimPaceOneLine(booked),
                     grimPaceYawBanded(clickRotation == null ? MC.player.getYRot() : clickRotation.yaw()),
                     this.grimSneakedRecently(),
                     this.grimPaceRecentJump()
                  );
                  long floor = grimPaceFloorMs(limit) + this.grimPaceJitterMs;
                  this.grimPaceWaitTicks = grimPaceWaitTicks(elapsed, floor);
                  this.grimTracePaceFloor = floor;
                  if (grimPaceFloorHolds(elapsed, floor)) {
                     return true;
                  } else if (!this.grimPaceSamples.isEmpty() && this.grimPaceLastBookedNanos != Long.MIN_VALUE) {
                     long[] recent = new long[this.grimPaceSamples.size()];
                     int index = 0;

                     for (ScaffoldModule.GrimPaceSample sample : this.grimPaceSamples) {
                        recent[index++] = sample.millis();
                     }

                     double mean = grimPaceProspectiveMean(recent, elapsed, 8);
                     this.grimTracePaceFloor = 0L;
                     return mean < limit * 1.08 ? true : this.grimIntavePlaceHolds(target, clickRotation, nowNanos);
                  } else {
                     return this.grimIntavePlaceHolds(target, clickRotation, nowNanos);
                  }
               } else if (this.grimNoFootingUnderfoot()) {
                  this.grimTraceLastChance = true;
                  this.grimTracePaceFloor = 50L;
                  return grimPaceFloorHolds(elapsed, 50L);
               } else {
                  this.grimTracePaceBrink = true;
                  this.grimTracePaceFloor = 100L;
                  return grimPaceFloorHolds(elapsed, 100L);
               }
            } else {
               this.grimTraceLastChance = true;
               this.grimTracePaceFloor = 50L;
               return grimPaceFloorHolds(elapsed, 50L);
            }
         }
      }
   }

   private boolean grimIntavePlaceHolds(ScaffoldModule.PlacementTarget target, RiptideRotationUtil.Rotation clickRotation, long nowNanos) {
      if (MC.player == null || !MC.player.onGround()) {
         return false;
      } else if (!grimIntaveRecordsPlacement(target.face())) {
         return false;
      } else if (!this.grimIntavePlaceGaps.isEmpty() && this.grimIntavePlaceNanos != Long.MIN_VALUE) {
         long candidate = this.grimIntavePlaceGap(nowNanos);
         if (candidate < 0L) {
            return false;
         } else if (candidate >= 1000L) {
            return false;
         } else {
            float pitch = clickRotation == null ? MC.player.getXRot() : clickRotation.pitch();
            List<BlockPos> cells = new ArrayList<>(this.grimIntavePlaceCells.size() + 1);
            if (candidate < 5000L) {
               cells.addAll(this.grimIntavePlaceCells);
            }

            while (cells.size() > 4) {
               cells.remove(0);
            }

            cells.add(target.placedBlock());
            if (!grimIntaveMeanWorthHolding(pitch, this.grimIntavePlacePitch, candidate, grimIntaveFlickOneLine(cells))) {
               return false;
            } else {
               long[] recent = new long[this.grimIntavePlaceGaps.size()];
               int index = 0;

               for (long gap : this.grimIntavePlaceGaps) {
                  recent[index++] = gap;
               }

               if (grimPaceProspectiveMean(recent, candidate, 8) >= 432.0) {
                  return false;
               } else {
                  this.grimTracePaceSince = candidate;
                  this.grimTracePaceIntave = true;
                  return true;
               }
            }
         }
      } else {
         return false;
      }
   }

   static long grimPaceFloorMs(long limitMs) {
      return Math.round(limitMs * 1.08);
   }

   private long grimPaceElapsedMs(long nowNanos) {
      return grimMonotonicElapsedMs(nowNanos, this.grimPaceLastBookedNanos);
   }

   static long grimMonotonicElapsedMs(long nowNanos, long sinceNanos) {
      if (sinceNanos == Long.MIN_VALUE) {
         return -1L;
      } else {
         return nowNanos <= sinceNanos ? 0L : (nowNanos - sinceNanos) / 1000000L;
      }
   }

   static long grimMonotonicTimestamp(long previousNanos, long observedNanos) {
      return previousNanos == Long.MIN_VALUE ? observedNanos : Math.max(previousNanos, observedNanos);
   }

   static boolean grimPaceFloorHolds(long elapsedMs, long floorMs) {
      return elapsedMs >= 0L && elapsedMs < floorMs - 3L;
   }

   static boolean grimWirePitchFrozen(boolean rayClickable, boolean paceRefused) {
      return rayClickable && !paceRefused;
   }

   static long grimMatrixMinPlaceMs(boolean diagonal) {
      return diagonal ? 250L : 350L;
   }

   static long grimPaceExemptFloorMs(long exemptionMs, boolean diagonal) {
      return Math.max(exemptionMs, grimMatrixMinPlaceMs(diagonal));
   }

   static int grimPaceWaitTicks(long elapsedMs, long floorMs) {
      return elapsedMs >= 0L && elapsedMs < floorMs ? (int)Math.ceil((floorMs - elapsedMs) / 50.0) : 0;
   }

   private int grimStandingRow() {
      if (MC.player == null) {
         return Integer.MIN_VALUE;
      } else {
         return MC.player.onGround() ? this.grimOracleFootingRow() : this.grimBuiltFloorRow();
      }
   }

   private boolean grimFootingBrink(ScaffoldModule.PlacementTarget target) {
      if (MC.player != null && MC.level != null) {
         int row = this.grimStandingRow();
         if (row == Integer.MIN_VALUE) {
            return false;
         } else {
            return !grimIsOwnFootingCell(target.placedBlock(), this.grimFootCellAtRow(row))
               ? false
               : this.grimFootingOverlap(MC.player.position(), row) <= grimBrinkOverlapFor(MC.player.onGround());
         }
      } else {
         return false;
      }
   }

   static double grimBrinkOverlapFor(boolean grounded) {
      return grounded ? 0.3 : 0.12;
   }

   private BlockPos grimFootCellAtRow(int row) {
      Vec3 position = MC.player.position();
      return new BlockPos(Mth.floor(position.x), row + 1, Mth.floor(position.z));
   }

   private boolean grimLandingLastChance(ScaffoldModule.PlacementTarget target) {
      if (MC.player == null || MC.level == null) {
         return false;
      } else if (!MC.player.onGround() && !(MC.player.getDeltaMovement().y >= 0.0)) {
         int row = this.grimStandingRow();
         if (row == Integer.MIN_VALUE) {
            return false;
         } else {
            Vec3 landing = grimDescentCrossing(MC.player.position(), MC.player.getDeltaMovement(), row + 1.0, 12);
            if (landing == null) {
               return false;
            } else {
               return !target.placedBlock().equals(BlockPos.containing(landing.x, row + 0.5, landing.z))
                  ? false
                  : this.grimFootingOverlap(MC.player.position(), row) <= 0.12;
            }
         }
      } else {
         return false;
      }
   }

   private boolean grimLandingChainCell(BlockPos placed) {
      return this.grimRiseFloorAwaiting(placed)
         || placed.equals(this.grimLaunchReservedSupport)
         || placed.equals(this.grimLaunchReservedConnector)
         || placed.equals(this.grimLaunchReservedStep)
         || placed.equals(this.grimLaunchReservedRiser);
   }

   private boolean grimChainWindowClosing(ScaffoldModule.PlacementTarget target) {
      if (target.face().getAxis().isVertical()) {
         return false;
      } else {
         Vec3 eye = MC.player.getEyePosition();
         Vec3 velocity = MC.player.getDeltaMovement();
         float yaw = target.rotation().yaw();

         for (int step = 1; step <= 2; step++) {
            Vec3 projected = eye.add(velocity.scale(step));
            if (!(grimEyePastPlane(projected, target.supportBlock(), target.face()) <= 0.0)
               && grimCrossingLandsOnFace(projected, target.supportBlock(), target.face(), yaw, false)) {
               float solve = grimSideWindowSolvePitch(projected, target.supportBlock(), target.face(), yaw);
               if (Float.isFinite(solve) && solve <= 89.5F) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   static boolean grimDyingRiserPickSteal(
      boolean grounded, boolean descending, boolean stampFresh, boolean stampedCell, boolean faceHorizontal, boolean eyePastPlane, boolean windowAlive
   ) {
      return !grounded && descending && stampFresh && !stampedCell && faceHorizontal && eyePastPlane && !windowAlive;
   }

   private void grimPaceBook(BlockPos placed, BlockPos against, Direction face, float wirePitch) {
      long observed = this.grimPaceQueuedNanos == Long.MIN_VALUE ? System.nanoTime() : this.grimPaceQueuedNanos;
      long now = grimMonotonicTimestamp(this.grimPaceLastBookedNanos, observed);
      long interval = this.grimPaceLastBookedNanos == Long.MIN_VALUE ? 1000L : grimMonotonicElapsedMs(now, this.grimPaceLastBookedNanos);
      if (placed.getY() != against.getY()) {
         interval += 1000L;
      }

      this.grimPaceLastBookedNanos = now;
      this.grimPaceSamples.addLast(new ScaffoldModule.GrimPaceSample(interval, placed));

      while (this.grimPaceSamples.size() > 8) {
         this.grimPaceSamples.removeFirst();
      }

      if (grimIntaveRecordsPlacement(face)) {
         this.grimIntavePlaceBook(now, placed, wirePitch);
      }

      this.grimPaceRiserHoldCell = null;
      this.grimPaceRiserHoldTick = Integer.MIN_VALUE;
      this.grimIntaveParkClear();
   }

   static boolean grimIntaveRecordsPlacement(Direction face) {
      return face != null && face.getAxis().isHorizontal();
   }

   private void grimIntavePlaceBook(long now, BlockPos placed, float wirePitch) {
      long gap = this.grimIntavePlaceGap(now);
      if (gap < 0L) {
         gap = 1000L;
      }

      if (gap >= 5000L) {
         this.grimIntavePlaceCells.clear();
      }

      this.grimIntavePlaceNanos = now;
      this.grimIntavePlacePitch = wirePitch;
      this.grimIntavePlaceGaps.addLast(gap);

      while (this.grimIntavePlaceGaps.size() > 8) {
         this.grimIntavePlaceGaps.removeFirst();
      }

      this.grimIntavePlaceCells.addLast(placed.immutable());

      while (this.grimIntavePlaceCells.size() > 5) {
         this.grimIntavePlaceCells.removeFirst();
      }
   }

   private long grimIntavePlaceGap(long nowNanos) {
      long elapsed = grimMonotonicElapsedMs(nowNanos, this.grimIntavePlaceNanos);
      return elapsed < 0L ? -1L : Math.min(1000L, elapsed);
   }

   private boolean grimPaceRecentJump() {
      long elapsed = grimMonotonicElapsedMs(System.nanoTime(), this.grimPaceLastJumpNanos);
      return elapsed >= 0L && elapsed < 750L;
   }

   private void updateGrimTakeoffClock() {
      if (MC.player != null) {
         boolean onGround = MC.player.onGround();
         if (this.grimPaceWasOnGround && !onGround && MC.player.getDeltaMovement().y > 0.0) {
            this.grimPaceLastJumpNanos = System.nanoTime();
            this.grimDeadCells.clear();
         }

         this.grimPaceWasOnGround = onGround;
      }
   }

   static boolean grimIntaveFlickOneLine(List<BlockPos> blocks) {
      int lastX = 0;
      int lastY = 0;
      int lastZ = 0;
      boolean lockedOnX = false;
      boolean lockedOnZ = false;
      boolean first = true;
      int yTolerance = 1;

      for (BlockPos block : blocks) {
         if (!first) {
            if (lastY != block.getY()) {
               if (yTolerance-- <= 0) {
                  return false;
               }
            } else {
               if (lastX == block.getX()) {
                  lockedOnX = true;
               } else if (lockedOnX) {
                  return false;
               }

               if (lastZ == block.getZ()) {
                  lockedOnZ = true;
               } else if (lockedOnZ) {
                  return false;
               }
            }
         }

         lastX = block.getX();
         lastY = block.getY();
         lastZ = block.getZ();
         first = false;
      }

      return lockedOnX || lockedOnZ;
   }

   static boolean grimIntaveMeanWorthHolding(float pitch, float lastPitch, long gapMs, boolean oneLine) {
      if (pitch > 85.0F) {
         return true;
      } else if (oneLine || gapMs >= 800L) {
         return false;
      } else if (!(pitch > 70.0F)) {
         return false;
      } else {
         float diff = Math.abs(pitch - lastPitch);
         return diff > 3.0F && diff < 20.0F;
      }
   }

   static boolean grimIntaveFlickSafeDiff(float pitch, float lastPitch) {
      float diff = Math.abs(pitch - lastPitch);
      return !(diff > 3.0F) || !(diff < 20.0F);
   }

   static float grimIntaveFlickPitchNudge(float goal, float lastPitch, double windowLow, double windowHigh) {
      if (grimIntaveFlickSafeDiff(goal, lastPitch)) {
         return goal;
      } else {
         float bound = Double.isFinite(windowLow) && Double.isFinite(windowHigh) && windowLow <= windowHigh && windowLow > 89.0 ? 89.3F : 89.0F;
         double lo = Double.isFinite(windowLow) ? windowLow : Double.NEGATIVE_INFINITY;
         double hi = Math.min(Double.isFinite(windowHigh) ? windowHigh : Double.POSITIVE_INFINITY, (double)bound);
         if (lo > hi) {
            return goal;
         } else {
            float sign = Math.signum(goal - lastPitch);
            float below = (float)Mth.clamp(lastPitch + sign * 2.5F, lo, hi);
            float above = (float)Mth.clamp(lastPitch + sign * 20.5F, lo, hi);
            Float chosen = null;
            if (grimIntaveFlickSafeDiff(below, lastPitch)) {
               chosen = below;
            }

            if (grimIntaveFlickSafeDiff(above, lastPitch) && (chosen == null || Math.abs(above - goal) < Math.abs(chosen - goal))) {
               chosen = above;
            }

            return chosen == null ? goal : chosen;
         }
      }
   }

   private float grimIntaveFlickPitchGoal(float goal, double windowLow, double windowHigh, Direction face, BlockPos placedBlock) {
      if (!this.isGrimFamily() || !grimIntaveRecordsPlacement(face)) {
         return goal;
      } else if (!Float.isNaN(this.grimIntavePlacePitch) && goal > 70.0F) {
         long gap = this.grimIntavePlaceGap(System.nanoTime());
         if (gap < 0L || gap >= 800L) {
            return goal;
         } else if (grimIntaveFlickSafeDiff(goal, this.grimIntavePlacePitch)) {
            return goal;
         } else {
            List<BlockPos> cells = new ArrayList<>(this.grimIntavePlaceCells.size() + 1);
            if (gap < 5000L) {
               cells.addAll(this.grimIntavePlaceCells);
            }

            while (cells.size() > 4) {
               cells.remove(0);
            }

            cells.add(placedBlock);
            return !grimIntaveFlickOneLine(cells) && !this.grimIntaveRotationArmable()
               ? goal
               : grimIntaveFlickPitchNudge(goal, this.grimIntavePlacePitch, windowLow, windowHigh);
         }
      } else {
         return goal;
      }
   }

   static float grimIntaveSafeShipPitch(float lastPitch, long gapMs) {
      float park = 84.5F;
      if (!Float.isNaN(lastPitch) && gapMs >= 0L && gapMs < 800L) {
         float bandTop = lastPitch + 2.5F;
         return !(bandTop >= park) && bandTop > 70.0F ? bandTop : park;
      } else {
         return park;
      }
   }

   static float grimIntaveRotationPitchDecision(float goal, double windowLow, boolean armable, boolean shallowAhead, boolean parkAllowed, float safePitch) {
      if (!armable || !(goal > safePitch)) {
         return goal;
      } else if (Double.isFinite(windowLow) && windowLow <= safePitch) {
         return safePitch;
      } else {
         return shallowAhead && parkAllowed ? safePitch : goal;
      }
   }

   static boolean grimIntaveShallowAhead(Vec3 eye, Vec3 step, BlockPos support, Direction face, float yaw, float safePitch, int lookaheadTicks) {
      if (step.lengthSqr() < 1.0E-6) {
         return false;
      } else {
         Vec3 travelled = Vec3.ZERO;
         Vec3 perTick = step;

         for (int k = 1; k <= lookaheadTicks; k++) {
            travelled = travelled.add(perTick);
            perTick = perTick.scale(0.91);
            double[] win = grimFaceCrossingWindow(eye.add(travelled), support, face, yaw);
            if (win != null && win[0] <= safePitch) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean grimIntaveRotationArmable() {
      if (this.grimIntavePlaceGaps.size() < 7) {
         return true;
      } else {
         long candidate = this.grimIntavePlaceGap(System.nanoTime());
         if (candidate < 0L) {
            return true;
         } else {
            long[] recent = new long[this.grimIntavePlaceGaps.size()];
            int index = 0;

            for (long gap : this.grimIntavePlaceGaps) {
               recent[index++] = gap;
            }

            return grimPaceProspectiveMean(recent, candidate, 8) < 432.0;
         }
      }
   }

   private boolean grimIntaveParkAdvance(BlockPos support, Direction face, int maxTicks) {
      int tick = RiptideSharedState.get().getClientTickCounter();
      if (support.equals(this.grimIntaveParkSupport) && face == this.grimIntaveParkFace) {
         if (tick != this.grimIntaveParkClientTick) {
            this.grimIntaveParkTicks++;
            this.grimIntaveParkClientTick = tick;
         }

         return this.grimIntaveParkTicks <= maxTicks;
      } else {
         this.grimIntaveParkSupport = support.immutable();
         this.grimIntaveParkFace = face;
         this.grimIntaveParkTicks = 1;
         this.grimIntaveParkClientTick = tick;
         return true;
      }
   }

   private boolean grimParkStarvesChain(BlockPos placed) {
      return MC.player != null && !MC.player.onGround() && this.grimLandingChainCell(placed);
   }

   private void grimIntaveParkClear() {
      this.grimIntaveParkSupport = null;
      this.grimIntaveParkFace = null;
      this.grimIntaveParkTicks = 0;
      this.grimIntaveParkClientTick = Integer.MIN_VALUE;
   }

   private float grimIntaveRotationPitchGoal(float goal, double windowLow, Direction face, Vec3 probeEye, Vec3 leadStep, float yaw, BlockPos support) {
      if (this.isGrimFamily() && grimIntaveRecordsPlacement(face)) {
         float safe = grimIntaveSafeShipPitch(this.grimIntavePlacePitch, this.grimIntavePlaceGap(System.nanoTime()));
         if (goal > safe && this.grimIntaveRotationArmable()) {
            boolean grounded = MC.player != null && MC.player.onGround();
            boolean immediate = Double.isFinite(windowLow) && windowLow <= safe;
            if (immediate && support.equals(this.grimIntaveParkSupport) && face == this.grimIntaveParkFace) {
               this.grimIntaveParkClear();
            }

            boolean shallowAhead = !immediate
               && !this.grimCourseFrozen()
               && !this.grimParkStarvesChain(support.relative(face))
               && grimIntaveShallowAhead(probeEye, leadStep, support, face, yaw, safe, grounded ? 5 : 2);
            boolean parkAllowed = shallowAhead && this.grimIntaveParkAdvance(support, face, grounded ? 6 : 3);
            return grimIntaveRotationPitchDecision(goal, windowLow, true, shallowAhead, parkAllowed, safe);
         } else {
            return goal;
         }
      } else {
         return goal;
      }
   }

   static boolean grimPaceOneLine(List<BlockPos> booked) {
      if (booked.size() < 2) {
         return true;
      } else {
         BlockPos first = booked.get(0);
         boolean sameX = true;
         boolean sameZ = true;

         for (BlockPos pos : booked) {
            if (pos.getY() != first.getY()) {
               return false;
            }

            if (pos.getX() != first.getX()) {
               sameX = false;
            }

            if (pos.getZ() != first.getZ()) {
               sameZ = false;
            }
         }

         return sameX || sameZ;
      }
   }

   static boolean grimPaceYawBanded(float emittedYaw) {
      double band = Math.abs(emittedYaw) % 90.0;
      return band < 10.0 || band > 80.0;
   }

   static long grimPaceLimitMs(boolean oneLine, boolean banded, boolean sneakedRecently, boolean recentJump) {
      if (oneLine) {
         if (recentJump) {
            return 300L;
         } else if (banded) {
            return sneakedRecently ? 350L : 500L;
         } else {
            return sneakedRecently ? 200L : 350L;
         }
      } else {
         return !banded && sneakedRecently ? 150L : 300L;
      }
   }

   static double grimPaceProspectiveMean(long[] recent, long candidateMs, int capacity) {
      int keep = Math.min(recent.length, capacity - 1);
      double sum = candidateMs;

      for (int i = recent.length - keep; i < recent.length; i++) {
         sum += recent[i];
      }

      return sum / (keep + 1.0);
   }

   private boolean grimSneakedRecently() {
      int since = grimTicksSince(RiptideSharedState.get().getClientTickCounter(), this.grimLastSneakTick);
      return since >= 0 && since <= 150;
   }

   private boolean grimWireSneak(boolean wanted) {
      int tick = RiptideSharedState.get().getClientTickCounter();
      if (this.grimSneakHoldTicks > 0) {
         this.grimSneakHoldTicks--;
         this.grimLastSneakTick = tick;
         return true;
      } else if (wanted || this.grimBridgeRunning(tick) && grimSneakRefreshDue(grimTicksSince(tick, this.grimLastSneakTick))) {
         this.grimSneakHoldTicks = 1;
         this.grimLastSneakTick = tick;
         return true;
      } else {
         return false;
      }
   }

   static boolean grimSneakRefreshDue(int sinceSneak) {
      return sinceSneak < 0 || sinceSneak >= 90;
   }

   private void grimMarkCellDead(BlockPos cell) {
      if (cell != null) {
         int tick = RiptideSharedState.get().getClientTickCounter();
         this.grimDeadCells.entrySet().removeIf(entry -> grimTicksSince(tick, entry.getValue()) >= 10);
         if (this.grimDeadCells.size() < 32) {
            this.grimDeadCells.put(cell.immutable(), tick);
         }
      }
   }

   private boolean grimCellOnCooldown(BlockPos cell) {
      if (this.grimDeadCells.isEmpty()) {
         return false;
      } else {
         Integer marked = this.grimDeadCells.get(cell);
         if (marked == null) {
            return false;
         } else if (grimTicksSince(RiptideSharedState.get().getClientTickCounter(), marked) >= 10) {
            this.grimDeadCells.remove(cell);
            return false;
         } else {
            return MC.player == null || !grimBoxOverColumn(MC.player.position(), cell);
         }
      }
   }

   private boolean grimEyePastTargetFace(ScaffoldModule.PlacementTarget target) {
      return grimEyePastPlane(MC.player.getEyePosition(), target.supportBlock(), target.face()) > 0.05;
   }

   public static void endMovementTick() {
   }

   private boolean grimBridgeRunning(int tick) {
      return this.lastGrimPlacementTick != Integer.MIN_VALUE && tick - this.lastGrimPlacementTick <= 40;
   }

   static boolean grimAttemptBlocksRearm(ScaffoldModule.GrimPlacementAttemptState state) {
      return state == ScaffoldModule.GrimPlacementAttemptState.ARMED
         || state == ScaffoldModule.GrimPlacementAttemptState.SENT
         || state == ScaffoldModule.GrimPlacementAttemptState.RECONCILING;
   }

   static long grimNextAttemptGeneration(long previous) {
      return Math.incrementExact(Math.max(0L, previous));
   }

   static boolean grimAckCovers(int sequence, int acknowledged) {
      return sequence >= 0 && acknowledged >= sequence;
   }

   static ScaffoldModule.GrimAttemptDecision grimReduceAttempt(
      ScaffoldModule.GrimPlacementAttemptState state, int age, boolean resultSeen, boolean resultConsumed, boolean solid, boolean ackCovered
   ) {
      if (state == null) {
         state = ScaffoldModule.GrimPlacementAttemptState.IDLE;
      }

      if (state == ScaffoldModule.GrimPlacementAttemptState.IDLE || state == ScaffoldModule.GrimPlacementAttemptState.FAILED) {
         return new ScaffoldModule.GrimAttemptDecision(state, null);
      } else if (resultSeen && !resultConsumed) {
         return new ScaffoldModule.GrimAttemptDecision(ScaffoldModule.GrimPlacementAttemptState.FAILED, "use");
      } else if (state == ScaffoldModule.GrimPlacementAttemptState.ARMED) {
         return age >= 2
            ? new ScaffoldModule.GrimAttemptDecision(ScaffoldModule.GrimPlacementAttemptState.FAILED, "not-sent")
            : new ScaffoldModule.GrimAttemptDecision(state, null);
      } else if (resultSeen && resultConsumed && solid) {
         return new ScaffoldModule.GrimAttemptDecision(ScaffoldModule.GrimPlacementAttemptState.PREDICTED, null);
      } else {
         if (state == ScaffoldModule.GrimPlacementAttemptState.PREDICTED && !solid) {
            state = ScaffoldModule.GrimPlacementAttemptState.RECONCILING;
         } else if (state == ScaffoldModule.GrimPlacementAttemptState.SENT && resultSeen && resultConsumed) {
            state = ScaffoldModule.GrimPlacementAttemptState.RECONCILING;
         }

         if (state == ScaffoldModule.GrimPlacementAttemptState.RECONCILING) {
            if (ackCovered) {
               return new ScaffoldModule.GrimAttemptDecision(ScaffoldModule.GrimPlacementAttemptState.FAILED, "ack-air");
            }

            if (age >= 2) {
               return new ScaffoldModule.GrimAttemptDecision(ScaffoldModule.GrimPlacementAttemptState.FAILED, "timeout-air");
            }
         } else if (state == ScaffoldModule.GrimPlacementAttemptState.SENT && age >= 2) {
            return new ScaffoldModule.GrimAttemptDecision(ScaffoldModule.GrimPlacementAttemptState.FAILED, "result-timeout");
         }

         return new ScaffoldModule.GrimAttemptDecision(state, null);
      }
   }

   private static void purgeCollectedGrimQueuedUses() {
      ScaffoldModule.GrimPacketIdentity collected;
      while ((collected = (ScaffoldModule.GrimPacketIdentity)GRIM_QUEUED_USE_GC.poll()) != null) {
         GRIM_QUEUED_USES.remove(collected);
      }
   }

   static ScaffoldModule.GrimReservationNeed grimReservationNeed(boolean supportSolid, int supportDeficit, boolean riserSolid) {
      if (!supportSolid) {
         return supportDeficit <= 1 ? ScaffoldModule.GrimReservationNeed.SUPPORT : ScaffoldModule.GrimReservationNeed.CONNECTOR;
      } else {
         return riserSolid ? ScaffoldModule.GrimReservationNeed.READY : ScaffoldModule.GrimReservationNeed.RISER;
      }
   }

   private boolean traceArmed() {
      return this.grimLiveTraceTicks >= 0;
   }

   private void traceGrim(String outcome, ScaffoldModule.PlacementTarget target) {
      if (this.traceArmed() && MC.player != null) {
         if (this.grimLiveTraceTicks == 0) {
            RiptideTraceLog.println("[scaffold-live] capture start");
            RiptideTraceLog.println(
               "[scaffold-why] legend lr=the reservation ledger, advisory only - it decides no key since the 12:52 ruling (known=landing solved, deficit=blocks still owed, pend=click already on the wire, landsolid=is the landing column's support already a block - winners take off T, losers F); strip=<stage>:<-removed+added> per stage, F/B/L/R/J/S/P keys, net=physical vs emitted - the jump and travel keys are ALWAYS the player's own; the only writers left are wire-sneak/lip-stop/reconcile/empty-stack (each sneak-only), lane (grounded steering) and the sprint drop for the non-sprint predictor; clk=<why code> past=<eye past face plane>/<margin needed> demand=<pitch the crossing needs>/<hard cap> drop=<eye height over the face>; rsv=<reserved chain cell>:<its own verdict> - oob/cooldown/behind/solid/no-repl/rowlock/ok or plan[<per-face codes>], where ip:<box|side|strict> is the body-clearance refusal and strict means the CATCH PREDICATE was shut; pick=<selection tier> plan=<last plan reject>; arcend=one line per landing, dy=+0.00 placed=0 is a jump that achieved nothing; arcchain=the same arc's two-click window - the landing support (planned/became a block) against every tick the riser above it was body-legal. clear ending BEFORE set means the level was never placeable, not merely missed"
            );
         }

         Vec3 p = MC.player.position();
         Vec3 v = MC.player.getDeltaMovement();
         if (this.grimTracePrevGnd && !MC.player.onGround() && this.grimJumpKeyHeld()) {
            RiptideTraceLog.println(
               String.format(
                  Locale.ROOT,
                  "[scaffold-live] t%03d takeoff     vel=%.3f,%.3f,%.3f arc=%s foot=%s",
                  this.grimLiveTraceTicks,
                  v.x,
                  v.y,
                  v.z,
                  this.traceArcBudget(),
                  this.traceFootCell()
               )
            );
            this.grimArcStartTick = this.grimLiveTraceTicks;
            this.grimArcStartClientTick = RiptideSharedState.get().getClientTickCounter();
            this.grimArcStartPos = p;
            this.grimArcStartGoal = (this.grimLaunchReservedSupport == null ? "--" : this.grimLaunchReservedSupport.toShortString())
               + ":"
               + this.grimLaunchReservationStage;
            this.grimArcPlaceCount = 0;
            this.grimArcSetCount = 0;
            this.grimArcAimTicks = 0;
            this.grimArcPaceTicks = 0;
            this.grimArcNoTargetTicks = 0;
            this.grimArcDropTicks = 0;
         }

         if (!MC.player.onGround()) {
            this.grimArcNote(outcome);
         }

         this.grimSampleArcChain();
         if (!this.grimTracePrevGnd && MC.player.onGround() && this.grimArcStartTick >= 0) {
            RiptideTraceLog.println(
               String.format(
                  Locale.ROOT,
                  "[scaffold-why] t%03d arcend     from=t%03d ticks=%d lines=%d dy=%+.2f dxz=%.2f placed=%d set=%d aim=%d pace=%d notgt=%d drop=%d goal=%s end=%s",
                  this.grimLiveTraceTicks,
                  this.grimArcStartTick,
                  this.grimArcStartClientTick < 0 ? -1 : RiptideSharedState.get().getClientTickCounter() - this.grimArcStartClientTick,
                  this.grimLiveTraceTicks - this.grimArcStartTick,
                  this.grimArcStartPos == null ? Double.NaN : p.y - this.grimArcStartPos.y,
                  this.grimArcStartPos == null ? Double.NaN : Math.hypot(p.x - this.grimArcStartPos.x, p.z - this.grimArcStartPos.z),
                  this.grimArcPlaceCount,
                  this.grimArcSetCount,
                  this.grimArcAimTicks,
                  this.grimArcPaceTicks,
                  this.grimArcNoTargetTicks,
                  this.grimArcDropTicks,
                  this.grimArcStartGoal,
                  this.grimTraceWhy
               )
            );
            RiptideTraceLog.println(
               String.format(
                  Locale.ROOT,
                  "[scaffold-why] t%03d arcchain   sup=%s plan=%s set=%s | ris=%s clear=%s..%s set=%s clk=%s rfail=%s relatch=%d land=%s",
                  this.grimLiveTraceTicks,
                  traceCell(this.grimArcChainSupport),
                  traceTick(this.grimArcChainSupportOk),
                  traceTick(this.grimArcChainSupportSet),
                  traceCell(this.grimArcChainRiser),
                  traceTick(this.grimArcChainRiserFirst),
                  traceTick(this.grimArcChainRiserLast),
                  traceTick(this.grimArcChainRiserSet),
                  this.grimArcChainRiserWhy,
                  this.grimArcChainRiserFail,
                  this.grimArcChainRelatch,
                  BlockPos.containing(p.x, p.y - 0.05, p.z).toShortString()
               )
            );
            this.grimArcStartTick = -1;
            this.grimArcStartClientTick = -1;
            this.grimArcStartPos = null;
            this.grimArcChainRelatch = 0;
            this.grimResetArcChain(null);
         }

         if (!this.grimTraceFallNoted && MC.player != null && !MC.player.onGround() && MC.player.fallDistance >= 0.5 && MC.player.getDeltaMovement().y < 0.0) {
            this.grimTraceFallNoted = true;
            int placeAge = this.grimTraceLastPlaceTick == Integer.MIN_VALUE
               ? -1
               : RiptideSharedState.get().getClientTickCounter() - this.grimTraceLastPlaceTick;
            ScaffoldModule.PlacementTarget held = this.grimRealPendingTarget != null ? this.grimRealPendingTarget : this.grimStickyTarget;
            RiptideTraceLog.println(
               String.format(
                  Locale.ROOT,
                  "[scaffold-live] t%03d fall-start pos=%.2f,%.2f,%.2f vel=%.3f,%.3f,%.3f fall=%.1f placeage=%d arc=%s tgt=%s",
                  this.grimLiveTraceTicks,
                  p.x,
                  p.y,
                  p.z,
                  v.x,
                  v.y,
                  v.z,
                  MC.player.fallDistance,
                  placeAge,
                  this.traceArcBudget(),
                  fmtTarget(held)
               )
            );
         }

         if (MC.player.onGround()) {
            this.grimTraceFallNoted = false;
         }

         this.grimTracePrevGnd = MC.player.onGround();
         RiptideTraceLog.println(
            String.format(
               Locale.ROOT,
               "[scaffold-live] t%03d %-11s pos=%.2f,%.2f,%.2f vel=%.3f,%.3f,%.3f step=%.3f,%.3f gnd=%s fall=%.1f sneak=%s twr=%s %s %s %s %s %s %s emitted=%s click=%s goal=%s %s %s src=%s eye=%s eff=%s target=%s %s",
               this.grimLiveTraceTicks,
               outcome,
               p.x,
               p.y,
               p.z,
               v.x,
               v.y,
               v.z,
               this.grimLastTickStep.x,
               this.grimLastTickStep.z,
               MC.player.onGround() ? "T" : "F",
               MC.player.fallDistance,
               this.grimEdgeSneakActive ? "T" : "F",
               this.grimTowerActive() ? "T" : "F",
               this.traceKeys(),
               this.traceCourse(),
               this.traceLane(),
               this.traceLock(),
               this.traceGates(),
               this.traceArcCarry(),
               fmtRot(this.grimSilentRotation),
               fmtRot(this.grimCommittedClickRotation),
               fmtRot(this.grimAimPrevGoal),
               this.traceRotationBudget(),
               this.tracePlacementProtocol(),
               this.grimLastPick,
               this.grimLastGoalEye,
               this.grimEffCell == null ? "null" : this.grimEffCell.toShortString(),
               fmtTarget(target),
               !"place".equals(outcome) && !outcome.startsWith("PLACE") ? "" : this.traceClickGeometry(target)
            )
         );
         this.traceGrimWhy(target);
         this.grimTraceStrip.setLength(0);
         this.grimTraceClickNumbers = "--";
         this.grimLiveTraceTicks++;
         this.grimSegNote(outcome);
      }
   }

   private void grimArcNote(String outcome) {
      if (outcome.startsWith("PLACE")) {
         this.grimArcPlaceCount++;
      } else if (outcome.startsWith("settle-ok")) {
         this.grimArcSetCount++;
      } else if (outcome.startsWith("aim-hold")) {
         this.grimArcAimTicks++;
      } else if (outcome.startsWith("pace-hold")) {
         this.grimArcPaceTicks++;
      } else if (outcome.startsWith("no-target")) {
         this.grimArcNoTargetTicks++;
      } else if (outcome.startsWith("rise-drop")) {
         this.grimArcDropTicks++;
      }
   }

   private void grimSegNote(String outcome) {
      this.grimSegTicks++;
      if (outcome.startsWith("PLACE")) {
         this.grimSegPlaces++;
      } else if (outcome.startsWith("settle-ok")) {
         this.grimSegSettled++;
      } else if (outcome.startsWith("real-miss")) {
         this.grimSegMiss++;
      } else if (outcome.startsWith("aim-hold")) {
         this.grimSegAim++;
      } else if (outcome.startsWith("pace-hold")) {
         this.grimSegPace++;
      } else if (outcome.startsWith("no-target")) {
         this.grimSegNoTarget++;
      } else if (outcome.startsWith("rise-drop")) {
         this.grimSegDrop++;
      } else if (outcome.startsWith("yaw-veto") || outcome.startsWith("pitch")) {
         this.grimSegVeto++;
      }

      int row = this.grimOracleFootingRow();
      if (row != Integer.MIN_VALUE) {
         if (this.grimSegRow == Integer.MIN_VALUE) {
            this.grimSegRow = row;
         } else if (row != this.grimSegRow) {
            RiptideTraceLog.println(
               String.format(
                  Locale.ROOT,
                  "[scaffold-live] rollup y=%d>%d ticks=%d place=%d set=%d miss=%d aim=%d pace=%d notgt=%d drop=%d repl=%d veto=%d",
                  this.grimSegRow,
                  row,
                  this.grimSegTicks,
                  this.grimSegPlaces,
                  this.grimSegSettled,
                  this.grimSegMiss,
                  this.grimSegAim,
                  this.grimSegPace,
                  this.grimSegNoTarget,
                  this.grimSegDrop,
                  this.grimSegReplan,
                  this.grimSegVeto
               )
            );
            this.grimSegRow = row;
            this.grimSegTicks = 0;
            this.grimSegPlaces = 0;
            this.grimSegSettled = 0;
            this.grimSegMiss = 0;
            this.grimSegAim = 0;
            this.grimSegPace = 0;
            this.grimSegNoTarget = 0;
            this.grimSegDrop = 0;
            this.grimSegReplan = 0;
            this.grimSegVeto = 0;
         }
      }
   }

   private String traceClickGeometry(ScaffoldModule.PlacementTarget target) {
      if (target != null && target.hit() != null) {
         Vec3 loc = target.hit().getLocation();
         BlockPos block = target.hit().getBlockPos();
         int tick = RiptideSharedState.get().getClientTickCounter();
         int gap = this.grimTraceLastPlaceTick == Integer.MIN_VALUE ? -1 : tick - this.grimTraceLastPlaceTick;
         this.grimTraceLastPlaceTick = tick;
         RiptideRotationUtil.Rotation click = this.grimCommittedClickRotation;
         float yawOff = click == null ? Float.NaN : Mth.wrapDegrees(click.yaw() - this.grimCourseStepYaw());
         float residual = click != null && this.grimLaneStep() != -1 ? grimLaneOctantResidual(this.grimLaneStepYaw(), click.yaw()) : Float.NaN;
         return String.format(
            Locale.ROOT,
            "hit=%.3f,%.3f,%.3f face=%s gap=%d yawoff=%.1f res=%.1f",
            loc.x - block.getX(),
            loc.y - block.getY(),
            loc.z - block.getZ(),
            target.face(),
            gap,
            yawOff,
            residual
         );
      } else {
         return "hit=--";
      }
   }

   private String traceRotationBudget() {
      if (this.grimSilentRotation == null) {
         return "d=--,-- sum=--";
      } else {
         float yaw = this.grimSilentRotation.yaw();
         float pitch = this.grimSilentRotation.pitch();
         float dYaw = Float.isNaN(this.grimTracePrevYaw) ? 0.0F : Mth.wrapDegrees(yaw - this.grimTracePrevYaw);
         float dPitch = Float.isNaN(this.grimTracePrevPitch) ? 0.0F : pitch - this.grimTracePrevPitch;
         this.grimTracePrevYaw = yaw;
         this.grimTracePrevPitch = pitch;
         float sent = grimSentYaw;
         float rawDelta = !Float.isNaN(sent) && !Float.isNaN(this.grimTracePrevSentYaw) ? Math.abs(sent - this.grimTracePrevSentYaw) : 0.0F;
         this.grimTracePrevSentYaw = sent;
         this.grimTraceYawSum += rawDelta;
         this.grimTracePitchSum = this.grimTracePitchSum + Math.abs(dPitch);
         if (rawDelta > 1.0E-4F || Math.abs(dPitch) > 1.0E-4F) {
            this.grimTraceMoveTicks++;
         }

         int tick = RiptideSharedState.get().getClientTickCounter();
         int dt = this.grimTracePrevTick == Integer.MIN_VALUE ? 1 : tick - this.grimTracePrevTick;
         this.grimTracePrevTick = tick;
         return String.format(
            Locale.ROOT,
            "d=%+.2f,%+.2f sum=%.0f,%.0f mv=%d dt=%d sent=%.2f/%.1f rd=%.2f",
            dYaw,
            dPitch,
            this.grimTraceYawSum,
            this.grimTracePitchSum,
            this.grimTraceMoveTicks,
            dt,
            sent,
            this.grimSilentRotation == null ? Float.NaN : this.grimSilentRotation.pitch(),
            rawDelta
         );
      }
   }

   private String traceGroundClaim() {
      return MC.player == null
         ? " ovl=? set=--"
         : String.format(
            Locale.ROOT,
            " ovl=%.2f/%s set=%s",
            this.grimFootingOverlap(MC.player.position(), this.grimOracleFootingRow()),
            MC.player.onGround() ? "T" : "F",
            this.grimTraceSettledCell == null ? "--" : this.grimTraceSettledCell.toShortString()
         );
   }

   private ScaffoldModule.TellyDriftSample tellyDriftSample() {
      Input emitted = MC.player.input == null ? Input.EMPTY : MC.player.input.keyPresses;
      int forwardImpulse = (emitted.forward() ? 1 : 0) - (emitted.backward() ? 1 : 0);
      int sidewaysImpulse = (emitted.left() ? 1 : 0) - (emitted.right() ? 1 : 0);
      double octant = emittedOctantDegrees(forwardImpulse, sidewaysImpulse);
      double flip = tellyStrafeFlipMargin(this.grimInputDeltaYaw);
      boolean paired = this.tellyDriftPairTick == RiptideSharedState.get().getClientTickCounter() - 1;
      if (paired && !Double.isNaN(octant) && !Float.isNaN(grimSentYaw) && !Float.isNaN(this.tellyDriftPairAnchorYaw)) {
         double residual = Mth.wrapDegrees(grimSentYaw + octant - this.tellyDriftPairAnchorYaw);
         double speed = tellyDriftSpeed(this.tellyDriftPairGrounded, emitted.sprint());
         double radians = residual * (float) (Math.PI / 180.0);
         double forwardAccel = speed * Math.cos(radians);
         double lateralAccel = -speed * Math.sin(radians);
         if (this.tellyDriftPairJumped && emitted.sprint()) {
            double impulse = (residual - octant) * (float) (Math.PI / 180.0);
            forwardAccel += 0.2 * Math.cos(impulse);
            lateralAccel += -0.2 * Math.sin(impulse);
         }

         return new ScaffoldModule.TellyDriftSample(octant, residual, forwardAccel, lateralAccel, flip, emitted.sprint(), this.tellyDriftPairGrounded);
      } else {
         return new ScaffoldModule.TellyDriftSample(octant, Double.NaN, Double.NaN, Double.NaN, flip, emitted.sprint(), this.tellyDriftPairGrounded);
      }
   }

   static double tellyDriftSpeed(boolean groundedWhenEmitted, boolean sprintWhenEmitted) {
      return (groundedWhenEmitted ? 0.1 : 0.0196) * (sprintWhenEmitted ? 1.3 : 1.0);
   }

   static double tellyDriftNextPerp(double perp, double lateralAccel, boolean groundedWhenEmitted) {
      return (perp + lateralAccel) * (groundedWhenEmitted ? 0.546 : 0.91);
   }

   private String traceTellyDrift(ScaffoldModule.TellyDriftSample drift) {
      return "oct="
         + (Double.isNaN(drift.octant()) ? "--" : fmtDeg(drift.octant()))
         + " res="
         + fmtDeg(drift.residual())
         + " afwd="
         + fmtAccel(drift.forwardAccel())
         + " alat="
         + fmtAccel(drift.lateralAccel())
         + " dlat="
         + fmtAccel(this.tellyDriftRunningLateral)
         + " drg="
         + String.format(Locale.ROOT, "%.3f", drift.grounded() ? 0.546 : 0.91)
         + " spr="
         + (drift.sprint() ? "T" : "F")
         + " flip="
         + (Double.isNaN(drift.flipMargin()) ? "--" : String.format(Locale.ROOT, "%.0f/%.0f", drift.flipMargin(), 15.0F));
   }

   private static String fmtDeg(double value) {
      return Double.isNaN(value) ? "--" : String.format(Locale.ROOT, "%+.1f", value);
   }

   private static String fmtAccel(double value) {
      return Double.isNaN(value) ? "--" : String.format(Locale.ROOT, "%+.4f", value);
   }

   private double tellyCourseCoordinate(Vec3 pos) {
      Vec3 forward = this.tellyForwardVector();
      return forward.horizontalDistanceSqr() <= 1.0E-8 ? Double.NaN : pos.dot(forward.normalize());
   }

   private void advanceTellyDrift(ScaffoldModule.TellyDriftSample drift, double laneError, Vec3 pos) {
      int tick = RiptideSharedState.get().getClientTickCounter();
      if (tick != this.tellyDriftLastTick) {
         this.tellyDriftLastTick = tick;
         if (!Double.isNaN(drift.lateralAccel())) {
            this.tellyDriftRunningLateral = this.tellyDriftRunningLateral + drift.lateralAccel();
         }

         boolean grounded = MC.player.onGround();
         double course = this.tellyCourseCoordinate(pos);
         if (!grounded) {
            if (this.tellyDriftWasGrounded) {
               this.tellyDriftTicks = 0;
               this.tellyDriftSprintTicks = 0;
               this.tellyDriftResidualSum = 0.0;
               this.tellyDriftLateralSum = 0.0;
            }

            this.tellyDriftTicks++;
            if (drift.sprint()) {
               this.tellyDriftSprintTicks++;
            }

            if (!Double.isNaN(drift.residual())) {
               this.tellyDriftResidualSum = this.tellyDriftResidualSum + Math.abs(drift.residual());
            }

            if (!Double.isNaN(drift.lateralAccel())) {
               this.tellyDriftLateralSum = this.tellyDriftLateralSum + drift.lateralAccel();
            }
         } else {
            if (!this.tellyDriftWasGrounded && this.tellyDriftTicks > 0) {
               this.printTellyCycle(laneError, course);
               this.tellyDriftLaneAtTakeoff = laneError;
               this.tellyDriftCourseAtTakeoff = course;
               this.tellyDriftGroundTicks = 0;
            }

            this.tellyDriftGroundTicks++;
         }

         this.tellyDriftWasGrounded = grounded;
      }
   }

   static double tellyCycleBlocksPerSecond(double travel, int groundTicks, int airTicks) {
      int ticks = groundTicks + airTicks;
      return ticks > 0 && !Double.isNaN(travel) ? travel * 20.0 / ticks : Double.NaN;
   }

   private void printTellyCycle(double laneError, double course) {
      double laneMoved = Double.isNaN(this.tellyDriftLaneAtTakeoff) ? Double.NaN : laneError - this.tellyDriftLaneAtTakeoff;
      double travel = Double.isNaN(this.tellyDriftCourseAtTakeoff) ? Double.NaN : Math.abs(course - this.tellyDriftCourseAtTakeoff);
      double bps = tellyCycleBlocksPerSecond(travel, this.tellyDriftGroundTicks, this.tellyDriftTicks);
      RiptideTraceLog.println(
         "[telly-cycle] air="
            + this.tellyDriftTicks
            + " ground="
            + this.tellyDriftGroundTicks
            + " sprint="
            + this.tellyDriftSprintTicks
            + "/"
            + this.tellyDriftTicks
            + " res="
            + fmtDeg(this.tellyDriftTicks == 0 ? Double.NaN : this.tellyDriftResidualSum / this.tellyDriftTicks)
            + " dv="
            + fmtAccel(this.tellyDriftLateralSum)
            + " dlane="
            + fmtAccel(laneMoved)
            + " travel="
            + fmtAccel(travel)
            + " bps="
            + fmtAccel(bps)
      );
   }

   private void traceTelly(String outcome) {
      if (MC.player != null && this.tellyLiveTraceTicks >= 0) {
         if (this.tellyLiveTraceTicks == 0) {
            RiptideTraceLog.println("[telly-live] capture start");
         }

         Vec3 pos = MC.player.position();
         Vec3 vel = MC.player.getDeltaMovement();
         double laneError = this.tellyLastBridge == null ? Double.NaN : this.tellyLaneCenter - laneCoordinate(pos, this.tellyAnchorYaw);
         double perp = vel.dot(this.tellyLeftVector());
         ScaffoldModule.TellyDriftSample drift = this.tellyDriftSample();
         this.advanceTellyDrift(drift, laneError, pos);
         RiptideTraceLog.println(
            String.format(
               Locale.ROOT,
               "[telly-live] t%03d %-9s pos=%.2f,%.2f,%.2f vel=%.3f,%.3f,%.3f step=%.3f,%.3f gnd=%s fall=%.1f sneak=%s %s anchor=%.0f emitted=%s %s lane=%+.3f/perp%+.3f/bias%+.1f/steer%+.1f%s off=%s air=%s/cd%d/p%d hold=%s phase=%s motion=%s bridgeY=%d bridge=%s tgt=%s raised=%s queued=%s cd=%d fail=%d delay=%s aimed=%s %s",
               this.tellyLiveTraceTicks,
               outcome,
               pos.x,
               pos.y,
               pos.z,
               vel.x,
               vel.y,
               vel.z,
               this.grimLastTickStep.x,
               this.grimLastTickStep.z,
               MC.player.onGround() ? "T" : "F",
               MC.player.fallDistance,
               this.tellySneakThisTick ? "T" : "F",
               this.traceKeys(),
               this.tellyAnchorYaw,
               fmtRot(this.grimSilentRotation),
               this.traceRotationBudget(),
               laneError,
               perp,
               this.grimLaneInputBias,
               this.tellyGroundSteerOffset,
               this.tellyGroundSteeringActive ? "*" : "",
               this.tellyLandingOffLaneSafe() ? "T" : "F",
               this.tellyAirStrafeThisTick,
               this.tellyAirStrafeCooldown,
               this.tellyAirStrafePulses,
               this.tellyHoldStrafe,
               this.tellyPhase,
               this.tellyMotion,
               this.tellyBridgeY,
               this.tellyLastBridge == null ? "--" : this.tellyLastBridge.toShortString(),
               this.tellyTarget == null ? "--" : this.tellyTarget.target().placedBlock().toShortString(),
               this.tellyTarget != null && this.tellyTarget.raised() ? "T" : "F",
               this.tellyPlacementQueued ? "T" : "F",
               this.tellyClickCooldown,
               this.tellyFailedClicks,
               this.tellyTraceDelay ? "T" : "F",
               this.tellyAimCommitted ? "T" : "F",
               this.traceTellyDrift(drift)
            )
         );
         this.tellyLiveTraceTicks++;
      }
   }

   private boolean tellyLandingOffLaneSafe() {
      try {
         return MC.player != null && this.tellyLastBridge != null && this.tellyLandingOffLane(MC.player);
      } catch (RuntimeException var2) {
         return false;
      }
   }

   private void traceTellyTick() {
      int tick = RiptideSharedState.get().getClientTickCounter();
      if (tick != this.tellyTracePrintedTick) {
         this.tellyTracePrintedTick = tick;
         this.traceTelly(this.tellyOwnsInput ? "tick" : "idle");
         this.tellyDriftPairTick = tick;
         this.tellyDriftPairGrounded = MC.player != null && MC.player.onGround();
         this.tellyDriftPairJumped = this.tellyJumpThisTick;
         this.tellyDriftPairAnchorYaw = this.tellyAnchorYaw;
      }
   }

   private String traceKeys() {
      Input emitted = MC.player.input == null ? Input.EMPTY : MC.player.input.keyPresses;
      return "keys="
         + (physicallyDown(MC.options.keyUp) ? "W" : "-")
         + (physicallyDown(MC.options.keyLeft) ? "A" : "-")
         + (physicallyDown(MC.options.keyDown) ? "S" : "-")
         + (physicallyDown(MC.options.keyRight) ? "D" : "-")
         + (this.grimJumpKeyHeld() ? "J" : "-")
         + (physicallyDown(MC.options.keyShift) ? "N" : "-")
         + (physicallyDown(MC.options.keySprint) ? "R" : "-")
         + " inPrev="
         + (emitted.forward() ? "W" : "-")
         + (emitted.left() ? "A" : "-")
         + (emitted.backward() ? "S" : "-")
         + (emitted.right() ? "D" : "-")
         + (emitted.jump() ? "J" : "-")
         + (emitted.shift() ? "N" : "-")
         + (emitted.sprint() ? "R" : "-");
   }

   private String traceCourse() {
      String cand = this.grimCourseStepCandidate == -1 ? "--" : COURSE_STEP_NAMES[this.grimCourseStepCandidate] + ":" + this.grimCourseStepDwell;
      return String.format(
         Locale.ROOT,
         "crs=%s/%+.0f go=%s/%+.0f cam=%+.0f cand=%s dia=%s frz=%s lin=%+.0f",
         this.grimCourseStep == -1 ? "--" : COURSE_STEP_NAMES[this.grimCourseStep],
         this.grimCourseStepYaw(),
         this.grimLaneStep() == -1 ? "--" : COURSE_STEP_NAMES[this.grimLaneStep()],
         this.grimLaneStepYaw(),
         MC.player.getYRot(),
         cand,
         this.currentMovementLine != null && isGrimDiagonalDirection(this.currentMovementLine.direction()) ? "T" : "F",
         this.grimCourseFrozen() ? "T" : "F",
         this.grimSteeredPostureYaw()
      );
   }

   private String traceLane() {
      ScaffoldModule.MovementLine line = this.currentMovementLine;
      if (line != null && !(line.direction().horizontalDistanceSqr() <= 1.0E-8)) {
         Vec3 d = line.direction().normalize();
         Vec3 left = new Vec3(d.z, 0.0, -d.x);
         Vec3 position = MC.player.position();
         double error = grimLaneError(line, position);
         Vec3 velocity = MC.player.getDeltaMovement();
         double perp = new Vec3(velocity.x, 0.0, velocity.z).dot(left);
         return String.format(Locale.ROOT, "lane=%+.3f/perp%+.3f", error, perp);
      } else {
         return "lane=--";
      }
   }

   private String traceLock() {
      ScaffoldModule.GrimRowLock lock = this.isGrimFamily() ? this.grimActiveRowLock() : null;
      int age = this.grimStickySetTick < 0 ? -1 : RiptideSharedState.get().getClientTickCounter() - this.grimStickySetTick;
      String sticky = "stk=" + (this.grimStickyTarget == null ? "none" : age + "/" + this.grimStickyBandMissTicks);
      return lock == null
         ? "lock=none " + sticky
         : String.format(
            Locale.ROOT, "lock=%s/y%d/p%d/f[%d,%d] %s", lock.xAxis() ? "x" : "z", lock.rowY(), lock.rowPerp(), lock.frontNeg(), lock.frontPos(), sticky
         );
   }

   private void grimNoteStrip(String owner, Input before, Input after) {
      if (this.traceArmed() && owner != null && before != null && after != null) {
         StringBuilder diff = new StringBuilder();
         grimNoteBit(diff, 'F', before.forward(), after.forward());
         grimNoteBit(diff, 'B', before.backward(), after.backward());
         grimNoteBit(diff, 'L', before.left(), after.left());
         grimNoteBit(diff, 'R', before.right(), after.right());
         grimNoteBit(diff, 'J', before.jump(), after.jump());
         grimNoteBit(diff, 'S', before.shift(), after.shift());
         grimNoteBit(diff, 'P', before.sprint(), after.sprint());
         if (diff.length() != 0) {
            if (this.grimTraceStrip.length() > 0) {
               this.grimTraceStrip.append(',');
            }

            this.grimTraceStrip.append(owner).append(':').append((CharSequence)diff);
         }
      }
   }

   private static void grimNoteBit(StringBuilder out, char name, boolean before, boolean after) {
      if (before != after) {
         out.append((char)(after ? '+' : '-')).append(name);
      }
   }

   private void grimNoteClickNumbers(ScaffoldModule.PlacementTarget target) {
      if (MC.player != null && target != null) {
         Vec3 eye = MC.player.getEyePosition();
         float yaw = this.grimSilentRotation != null ? this.grimSilentRotation.yaw() : MC.player.getYRot();
         float demand = grimSideWindowSolvePitch(eye, target.supportBlock(), target.face(), yaw);
         double xing = grimCrossingFraction(eye, target.supportBlock(), target.face(), yaw);
         this.grimTraceClickNumbers = String.format(
            Locale.ROOT,
            "past=%+.3f/%.2f demand=%s/%.1f drop=%.2f xing=%s",
            grimEyePastPlane(eye, target.supportBlock(), target.face()),
            0.05,
            Float.isNaN(demand) ? "nan" : String.format(Locale.ROOT, "%.1f", demand),
            89.5F,
            eye.y - (target.supportBlock().getY() + 1.0 - 0.5),
            Double.isNaN(xing) ? "--" : String.format(Locale.ROOT, "%+.2f", xing)
         );
      } else {
         this.grimTraceClickNumbers = "--";
      }
   }

   private void traceGrimWhy(ScaffoldModule.PlacementTarget target) {
      RiptideTraceLog.println(
         String.format(
            Locale.ROOT,
            "[scaffold-why] t%03d lr=%s | strip=%s | clk=%s %s | rsv=%s | pick=%s plan=%s | tgt=%s",
            this.grimLiveTraceTicks,
            this.grimTraceLaunchLedger,
            this.grimTraceStrip.length() == 0 ? "none" : this.grimTraceStrip.toString(),
            this.grimTraceWhy,
            this.grimTraceClickNumbers,
            this.grimTraceReserveWhy,
            this.grimLastPick,
            this.grimLastPlanFail.isEmpty() ? "ok" : this.grimLastPlanFail,
            fmtTarget(target)
         )
      );
   }

   private String traceGates() {
      return "gate=edge"
         + (this.grimTraceEdgeDanger ? "1" : "0")
         + ".fall"
         + (this.grimTraceFallDanger ? "1" : "0")
         + ".brink"
         + (this.grimTraceLateralBrink ? "1" : "0")
         + ".owed"
         + (this.grimTraceFootingOwed ? "1" : "0")
         + ".pbrink"
         + (this.grimTracePaceBrink ? "1" : "0")
         + ".last"
         + (this.grimTraceLastChance ? "1" : "0")
         + ".nofoot"
         + (this.grimNoFootingUnderfoot() ? "1" : "0")
         + ".resc"
         + (this.grimRecentlyRescued() ? "1" : "0")
         + ".div"
         + (this.grimCourseDiverged() ? "1" : "0")
         + ".rise"
         + (this.grimTraceRiseAllowed ? "1" : "0")
         + " brake="
         + this.grimTraceBrake
         + (Double.isNaN(this.grimTraceDiagonalPaceMean) ? "" : String.format(Locale.ROOT, " dclimb=%.0f/%d", this.grimTraceDiagonalPaceMean, 400L))
         + " lovl="
         + this.grimTraceArcCarry
         + " stand="
         + this.grimTraceArcStand
         + String.format(Locale.ROOT, " trv=%.2f", this.grimTraceArcTravel)
         + " jmp="
         + this.grimTraceJump
         + " arc="
         + this.traceArcBudget()
         + " "
         + this.traceLaunchReservation()
         + " tkoff="
         + (this.grimTraceRiseTakeoff == null ? "--" : this.grimTraceRiseTakeoff.toShortString())
         + ":"
         + this.grimTraceTakeoffWhy
         + " riser="
         + this.grimTraceRiserFail
         + " click="
         + (this.grimTraceClickLands ? "1" : "0")
         + " free="
         + (this.grimTraceClickFeasible ? "1" : "0")
         + " anch="
         + this.grimTraceLaneAnchor
         + this.traceGroundClaim()
         + " pin="
         + (
            this.grimPinFace == null
               ? "--"
               : this.grimPinFace.getName().charAt(0) + "@" + (this.grimPinSupport == null ? "?" : this.grimPinSupport.toShortString())
         )
         + (Double.isNaN(this.grimTraceCrossing) ? " cross=--" : String.format(Locale.ROOT, " cross=%.2f", this.grimTraceCrossing))
         + " foot="
         + this.traceFootCell()
         + " held="
         + this.planningStack().getCount()
         + " why="
         + this.grimTraceWhy;
   }

   private String traceArcBudget() {
      if (MC.player != null && MC.level != null && !MC.player.onGround()) {
         String budget = this.grimArcTicks + "/" + this.grimArcPlacements;
         int row = this.grimOracleFootingRow();
         Vec3 landing = grimDescentCrossing(MC.player.position(), MC.player.getDeltaMovement(), row + 1.0, 12, 0.02);
         if (landing == null) {
            return budget + "@--";
         } else {
            BlockPos cell = BlockPos.containing(landing.x, row + 0.5, landing.z);
            if (MC.level.isOutsideBuildHeight(cell)) {
               return budget + "@oob";
            } else {
               String at = "@" + cell.getX() + "," + cell.getZ();
               int deficit = this.grimCellDeficit(cell);
               return budget + at + (deficit == 0 ? ":ok" : (deficit == 1 ? ":n1" : ":n0"));
            }
         }
      } else {
         return "--";
      }
   }

   private void grimSampleArcChain() {
      if (MC.player != null && MC.level != null && !MC.player.onGround()) {
         BlockPos support = this.grimLaunchReservedSupport;
         if (support != null) {
            if (!support.equals(this.grimArcChainSupport)) {
               if (this.grimArcChainSupport != null) {
                  this.grimArcChainRelatch++;
               }

               this.grimResetArcChain(support);
            }

            if (this.grimArcChainSupportOk < 0 && "support".equals(this.grimLaunchReservationStage)) {
               this.grimArcChainSupportOk = this.grimLiveTraceTicks;
            }

            if (this.grimArcChainSupportSet < 0 && this.solidAt(this.grimArcChainSupport)) {
               this.grimArcChainSupportSet = this.grimLiveTraceTicks;
            }

            BlockPos riser = this.grimArcChainRiser;
            if (riser != null && !MC.level.isOutsideBuildHeight(riser)) {
               if (this.grimArcChainRiserSet < 0 && this.solidAt(riser)) {
                  this.grimArcChainRiserSet = this.grimLiveTraceTicks;
               }

               String clear = grimCellClearReason(MC.player.getBoundingBox(), MC.player.getDeltaMovement(), riser, this.grimFallingCatchPlan(riser));
               if ("ok".equals(clear)) {
                  if (this.grimArcChainRiserFirst < 0) {
                     this.grimArcChainRiserFirst = this.grimLiveTraceTicks;
                  }

                  this.grimArcChainRiserLast = this.grimLiveTraceTicks;
                  this.grimArcChainRiserFail = this.grimTraceRiserFail;
               } else {
                  this.grimArcChainRiserWhy = clear;
               }
            }
         }
      }
   }

   private void grimResetArcChain(BlockPos support) {
      this.grimArcChainSupport = support == null ? null : support.immutable();
      this.grimArcChainRiser = support == null ? null : support.above().immutable();
      this.grimArcChainSupportOk = -1;
      this.grimArcChainSupportSet = -1;
      this.grimArcChainRiserFirst = -1;
      this.grimArcChainRiserLast = -1;
      this.grimArcChainRiserSet = -1;
      this.grimArcChainRiserWhy = "--";
      this.grimArcChainRiserFail = "--";
   }

   private static String traceTick(int tick) {
      return tick < 0 ? "--" : String.format(Locale.ROOT, "t%03d", tick);
   }

   private String traceLaunchReservation() {
      String support = this.grimLaunchReservedSupport == null ? "--" : this.grimLaunchReservedSupport.toShortString();
      String connector = this.grimLaunchReservedConnector == null ? "--" : this.grimLaunchReservedConnector.toShortString();
      String deficit = "?";
      if (this.grimLaunchReservedSupport != null && MC.level != null && !MC.level.isOutsideBuildHeight(this.grimLaunchReservedSupport)) {
         int count = this.grimCellDeficit(this.grimLaunchReservedSupport);
         deficit = count == 0 ? "ok" : (count == 1 ? "n1" : "n0");
      }

      return "reserve=" + this.grimLaunchReservationStage + "@" + support + ":" + deficit + "/" + connector;
   }

   private String traceFootCell() {
      if (MC.level == null) {
         return "?";
      } else {
         BlockPos support = BlockPos.containing(MC.player.position()).below();
         if (MC.level.isOutsideBuildHeight(support)) {
            return "oob";
         } else {
            return this.isSolidSupport(MC.level.getBlockState(support), support) ? "solid" : "AIR";
         }
      }
   }

   private static String fmtRot(RiptideRotationUtil.Rotation rotation) {
      return rotation == null ? "null" : String.format(Locale.ROOT, "%.1f,%.1f", rotation.yaw(), rotation.pitch());
   }

   private static String fmtTarget(ScaffoldModule.PlacementTarget target) {
      if (target == null) {
         return "none";
      } else {
         Vec3 p = target.hit().getLocation();
         return target.face()
            + "@"
            + target.supportBlock().toShortString()
            + "->"
            + target.placedBlock().toShortString()
            + String.format(Locale.ROOT, " pt=%.2f,%.2f,%.2f", p.x, p.y, p.z);
      }
   }

   private void advanceGrimNoTarget() {
      this.advanceGrimIdleStream(true);
   }

   private RiptideRotationUtil.Rotation advanceGrimRotation(RiptideRotationUtil.Rotation goal) {
      return this.advanceGrimRotation(goal, false);
   }

   private RiptideRotationUtil.Rotation advanceGrimRotation(RiptideRotationUtil.Rotation goal, boolean holdPitch) {
      int tick = RiptideSharedState.get().getClientTickCounter();
      if (this.grimRotationStepTick == tick && this.grimSilentRotation != null) {
         return this.grimSilentRotation;
      } else if (MC.player == null) {
         return this.grimSilentRotation;
      } else {
         this.grimWindingDown = false;
         this.grimWindDownElapsed = 0;
         if (this.grimAimRaw == null) {
            this.grimAimRaw = RiptideRotationUtil.playerRotation(MC.player);
            this.grimAimSmoother.reset(this.rotationRandom.nextLong());
            this.grimAimPrevGoal = null;
            this.grimAimDirectionChange = 0.0F;
         }

         double gcd = RiptideRotationUtil.sensitivityGcd();
         float capScale = this.grimRemoveLimits() ? 4.0F : 1.0F;
         this.grimAimRaw = stepGrimAimRotation(
            this.grimAimSmoother,
            this.grimAimRaw,
            RiptideRotationUtil.angleDifference(goal.yaw(), this.grimAimRaw.yaw()),
            holdPitch ? 0.0F : Mth.clamp(goal.pitch(), -90.0F, 90.0F) - this.grimAimRaw.pitch(),
            this.grimAimDirectionSample(goal),
            gcd,
            18.0F * capScale,
            20.0F * capScale
         );
         float previousPitch = this.grimSilentRotation == null ? Float.NaN : this.grimSilentRotation.pitch();
         this.grimSilentRotation = grimShapeOutgoing(this.grimAimRaw, this.grimSilentRotation, gcd, this.grimNextDitherCounts(), this.grimLastPlaceYaw);
         this.grimEmittedPitchStep = Float.isNaN(previousPitch) ? 0.0F : Math.abs(this.grimSilentRotation.pitch() - previousPitch);
         this.grimRotationResetTicks = 5;
         this.grimRotationStepTick = tick;
         return this.grimSilentRotation;
      }
   }

   static RiptideRotationUtil.Rotation stepGrimAimRotation(
      QuantizedRotationSmoother smoother, RiptideRotationUtil.Rotation current, RiptideRotationUtil.Rotation goal, float directionChange, double gcd
   ) {
      return stepGrimAimRotation(
         smoother,
         current,
         RiptideRotationUtil.angleDifference(goal.yaw(), current.yaw()),
         Mth.clamp(goal.pitch(), -90.0F, 90.0F) - current.pitch(),
         directionChange,
         gcd
      );
   }

   static RiptideRotationUtil.Rotation stepGrimAimRotation(
      QuantizedRotationSmoother smoother, RiptideRotationUtil.Rotation current, float yawError, float pitchError, float directionChange, double gcd
   ) {
      return stepGrimAimRotation(smoother, current, yawError, pitchError, directionChange, gcd, 18.0F, 20.0F);
   }

   static RiptideRotationUtil.Rotation stepGrimAimRotation(
      QuantizedRotationSmoother smoother,
      RiptideRotationUtil.Rotation current,
      float yawError,
      float pitchError,
      float directionChange,
      double gcd,
      float maxYawStep,
      float maxPitchStep
   ) {
      QuantizedRotationSmoother.Step step = smoother.stepCapped(yawError, pitchError, gcd, maxYawStep, maxPitchStep, 180.0F, directionChange, 0.35F, true, true);
      RiptideRotationUtil.Rotation delta = new QuantizedRotationSmoother.Step(
            grimAimDeadband(grimCapCounts(step.yawCounts(), maxYawStep, gcd), yawError, gcd),
            grimAimDeadband(grimCapCounts(step.pitchCounts(), maxPitchStep, gcd), pitchError, gcd)
         )
         .asDelta(gcd);
      return new RiptideRotationUtil.Rotation(Mth.wrapDegrees(current.yaw() + delta.yaw()), Mth.clamp(current.pitch() + delta.pitch(), -90.0F, 90.0F));
   }

   static long grimAimDeadband(long counts, float error, double gcd) {
      if (Double.isFinite(gcd) && !(gcd <= 1.0E-9)) {
         return Math.abs(error) < gcd * 0.5 ? 0L : counts;
      } else {
         return counts;
      }
   }

   static long grimCapCounts(long counts, float maxDegrees, double gcd) {
      if (Double.isFinite(gcd) && !(gcd <= 1.0E-9)) {
         long limit = Math.max(1L, (long)Math.floor(maxDegrees / gcd));
         return Math.max(-limit, Math.min(limit, counts));
      } else {
         return counts;
      }
   }

   private float grimAimDirectionSample(RiptideRotationUtil.Rotation goal) {
      float change = this.grimAimPrevGoal == null ? 0.0F : Math.min(1.0F, RiptideRotationUtil.rotationAngleTo(this.grimAimPrevGoal, goal) / 45.0F);
      this.grimAimPrevGoal = goal;
      this.grimAimDirectionChange = this.grimAimDirectionChange * 0.65F + change * 0.35F;
      return this.grimAimDirectionChange;
   }

   static float grimDeintegrifyAngle(float angle, double gcd) {
      if (angle % 1.0F == 0.0F && angle != 0.0F && angle != 90.0F && angle != -90.0F) {
         float step = (float)(gcd > 1.0E-6 ? gcd : 0.0225);
         float nudged = angle + step;
         return nudged != 90.0F && nudged != -90.0F ? nudged : angle - step;
      } else {
         return angle;
      }
   }

   static float grimFortyFiveOffset(float yaw) {
      return (float)(yaw - Math.round(yaw / 45.0) * 45.0);
   }

   static boolean grimYawLegal(float yaw) {
      return Math.abs(grimFortyFiveOffset(yaw)) >= 0.2F && yaw % 1.0F != 0.0F;
   }

   static float grimLegalYaw(float candidate, double gcd, int preferSign, float forbidden, float lastPlaceYaw) {
      float step = (float)(gcd > 1.0E-6 ? gcd : 0.0225);
      int direction = preferSign != 0 ? preferSign : (grimFortyFiveOffset(candidate) >= 0.0F ? 1 : -1);
      if (grimYawLegal(candidate) && candidate != forbidden && candidate != lastPlaceYaw) {
         return candidate;
      } else {
         for (int ring = 1; ring <= 64; ring++) {
            float near = candidate + direction * ring * step;
            if (grimYawLegal(near) && near != forbidden && near != lastPlaceYaw) {
               return near;
            }

            float far = candidate - direction * ring * step;
            if (grimYawLegal(far) && far != forbidden && far != lastPlaceYaw) {
               return far;
            }
         }

         return candidate + step;
      }
   }

   static RiptideRotationUtil.Rotation grimShapeOutgoing(
      RiptideRotationUtil.Rotation raw, RiptideRotationUtil.Rotation previousEmitted, double gcd, int ditherCounts, float lastPlaceYaw
   ) {
      if (raw == null) {
         return null;
      } else {
         float previousYaw = previousEmitted == null ? Float.NaN : previousEmitted.yaw();
         float candidate = Mth.wrapDegrees((float)(raw.yaw() + ditherCounts * gcd));
         int prefer = previousEmitted == null ? 0 : (int)Math.signum(RiptideRotationUtil.angleDifference(raw.yaw(), previousYaw));
         return new RiptideRotationUtil.Rotation(
            grimLegalYaw(candidate, gcd, prefer, previousYaw, lastPlaceYaw), grimDeintegrifyAngle(Mth.clamp(raw.pitch(), -90.0F, 90.0F), gcd)
         );
      }
   }

   private int grimNextDitherCounts() {
      int next = this.grimDitherCounts;

      while (next == this.grimDitherCounts) {
         next = this.rotationRandom.nextInt(3) - 1;
      }

      this.grimDitherCounts = next;
      return next;
   }

   private ScaffoldModule.GrimWireClickRotation grimWireClickRotation() {
      return grimWireClickRotation(RiptideServerRotationView.snapshot());
   }

   static ScaffoldModule.GrimWireClickRotation grimWireClickRotation(RiptideServerRotationView.WireSnapshot snapshot) {
      if (snapshot != null && snapshot.initialized() && Float.isFinite(snapshot.currentYaw()) && Float.isFinite(snapshot.currentPitch())) {
         RiptideRotationUtil.Rotation current = new RiptideRotationUtil.Rotation(snapshot.currentYaw(), snapshot.currentPitch());
         RiptideRotationUtil.Rotation previous = Float.isFinite(snapshot.previousYaw()) && Float.isFinite(snapshot.previousPitch())
            ? new RiptideRotationUtil.Rotation(snapshot.previousYaw(), snapshot.previousPitch())
            : current;
         return new ScaffoldModule.GrimWireClickRotation(previous, current, snapshot.tick());
      } else {
         return null;
      }
   }

   static float grimWireClickPitchStep(RiptideServerRotationView.WireSnapshot snapshot) {
      ScaffoldModule.GrimWireClickRotation wire = grimWireClickRotation(snapshot);
      return wire == null ? Float.NaN : Math.abs(wire.current().pitch() - wire.previous().pitch());
   }

   private String tracePlacementProtocol() {
      this.drainGrimFinalMoveWrite();
      String wire = !this.grimFinalMoveSeen
         ? "--"
         : (this.grimFinalWireGround ? "T" : "F") + (this.grimFinalWireHorizontalCollision ? "H" : "-") + (this.grimFinalWireHasPosition ? "P" : "S");
      return String.format(
            Locale.ROOT,
            "use=%s/g%d/o%d:%d/s%d/%s%s ack=%d/%d q=%d+%d wire=%s",
            this.grimAttemptState.name().toLowerCase(Locale.ROOT),
            this.grimAttemptGeneration,
            this.grimAttemptSubmittedCount,
            this.grimAttemptWriteCount,
            this.grimAttemptSequence,
            this.grimAttemptResult,
            this.grimAttemptResultConsumed ? "C" : "-",
            this.grimHighestProcessedAck,
            this.grimHighestObservedAck,
            this.grimPredictedPlacements.size(),
            this.grimUntrustedPredictions.size(),
            wire
         )
         + (this.grimStandingOnUnackedPlacement() ? " pend=T" : "");
   }

   private boolean grimStandingOnUnackedPlacement() {
      if (MC.player != null && !this.grimPredictedPlacements.isEmpty()) {
         AABB box = MC.player.getBoundingBox();
         int below = Mth.floor(box.minY - 0.001);

         for (ScaffoldModule.GrimPredictedPlacement placement : this.grimPredictedPlacements) {
            BlockPos cell = placement.cell();
            if (cell.getY() == below && cell.getX() + 1 > box.minX && cell.getX() < box.maxX && cell.getZ() + 1 > box.minZ && cell.getZ() < box.maxZ) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean grimRealClickLands(ScaffoldModule.PlacementTarget target, RiptideRotationUtil.Rotation clickRotation) {
      if (MC.player != null && MC.level != null && clickRotation != null) {
         double reach = Math.max(MC.player.blockInteractionRange(), MC.player.entityInteractionRange());
         BlockHitResult ray = grimClickRay(MC.player.getEyePosition(), clickRotation, reach, MC.level, MC.player);
         this.grimLastArmRay = ray;
         return grimClickFeasible(ray, target, this.grimHitBuildsPlannedCell(ray, target));
      } else {
         return false;
      }
   }

   private String traceClickMiss(ScaffoldModule.PlacementTarget target) {
      if (!this.traceArmed()) {
         return "";
      } else {
         RiptideRotationUtil.Rotation rotation = this.grimCommittedClickRotation;
         if (rotation == null) {
            ScaffoldModule.GrimWireClickRotation wire = this.grimWireClickRotation();
            rotation = wire == null ? null : wire.current();
         }

         if (MC.player != null && MC.level != null && rotation != null) {
            double reach = Math.max(MC.player.blockInteractionRange(), MC.player.entityInteractionRange());
            BlockHitResult ray = grimClickRay(MC.player.getEyePosition(), rotation, reach, MC.level, MC.player);
            return ray == null
               ? " miss=no-hit"
               : " miss=hit:"
                  + ray.getBlockPos().toShortString()
                  + "/"
                  + ray.getDirection().getName().charAt(0)
                  + String.format(Locale.ROOT, "@%.2f", ray.getLocation().y)
                  + " want:"
                  + target.supportBlock().toShortString()
                  + "/"
                  + target.face().getName().charAt(0)
                  + String.format(Locale.ROOT, ">=%.2f", target.minPlacementY());
         } else {
            return " miss=no-state";
         }
      }
   }

   private void advanceGrimIdleStream(boolean allowPreAim) {
      if (MC != null && MC.player != null) {
         if (allowPreAim && this.grimTowerActive()) {
            this.advanceGrimRotation(this.grimTowerPreAimGoal());
         } else if (!allowPreAim || !this.grimPreAimApplies() && !this.grimAirbornePreAimApplies()) {
            if (allowPreAim && this.grimBridgeHoldApplies()) {
               this.advanceGrimRotation(this.grimRestPoseGoal());
            } else {
               if (this.grimSilentRotation != null) {
                  this.grimWindingDown = true;
               }

               this.advanceGrimWindDown();
            }
         } else {
            this.advanceGrimRotation(this.grimRestPoseGoal());
         }
      }
   }

   private boolean grimPreAimApplies() {
      LocalPlayer player = MC.player;
      if (!player.onGround()) {
         return false;
      } else {
         Vec3 motion = player.getDeltaMovement();
         if (!this.isValidBlock(this.planningStack())) {
            return false;
         } else {
            Vec3 direction;
            if (this.currentMovementLine != null && this.currentMovementLine.direction().horizontalDistanceSqr() > 1.0E-8) {
               direction = this.currentMovementLine.direction();
            } else {
               if (!(motion.horizontalDistance() > 0.03)) {
                  return false;
               }

               direction = new Vec3(motion.x, 0.0, motion.z).normalize();
            }

            Vec3 position = player.position();

            for (double distance : PREAIM_EDGE_DISTANCES) {
               if (MC.level.getBlockState(targetedBase(position.add(direction.scale(distance)))).isAir()) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   private boolean grimAirbornePreAimApplies() {
      boolean hasCourse = this.currentMovementLine != null && this.currentMovementLine.direction().horizontalDistanceSqr() > 1.0E-8;
      return grimAirbornePreAimEligible(MC.player.onGround(), hasCourse, this.isValidBlock(this.planningStack()));
   }

   static boolean grimAirbornePreAimEligible(boolean onGround, boolean hasCourse, boolean hasPlaceableBlock) {
      return !onGround && hasCourse && hasPlaceableBlock;
   }

   private boolean grimBridgeHoldApplies() {
      return !this.isValidBlock(this.planningStack())
         ? false
         : grimBridgeHoldEligible(
            physicallyDown(MC.options.keyUp) || physicallyDown(MC.options.keyDown) || physicallyDown(MC.options.keyLeft) || physicallyDown(MC.options.keyRight),
            this.currentMovementLine == null ? null : this.currentMovementLine.direction(),
            this.findFallOffPosition(this.currentMovementLine),
            RiptideSharedState.get().getClientTickCounter(),
            this.lastGrimPlacementTick
         );
   }

   static boolean grimBridgeHoldEligible(boolean directionalKeyHeld, Vec3 courseDirection, Vec3 fallOff, int clientTick, int lastPlacementTick) {
      int sincePlacement = clientTick - lastPlacementTick;
      return sincePlacement >= 0 && sincePlacement <= 20
         ? true
         : directionalKeyHeld && courseDirection != null && courseDirection.horizontalDistanceSqr() > 1.0E-8 && fallOff != null;
   }

   private RiptideRotationUtil.Rotation grimRestPoseGoal() {
      float pitch = this.sessionPitchOffset;
      if (Float.isFinite(this.grimBridgePitchHold) && this.grimBridgeRunning(RiptideSharedState.get().getClientTickCounter())) {
         pitch = this.grimBridgePitchHold;
      }

      float yaw = this.grimSteeredPostureYaw();
      if (this.grimRealQueuedTick != RiptideSharedState.get().getClientTickCounter() && this.grimCourseStepCandidate != -1 && this.grimCourseStepDwell >= 2) {
         int candidateLane = laneStep(this.grimCourseStepCandidate, this.grimLaneOctant);
         if (candidateLane != -1) {
            yaw = grimPlacementPostureYaw(compassStepYaw(candidateLane));
         }
      }

      return new RiptideRotationUtil.Rotation(yaw, pitch);
   }

   static float grimPlacementPitchCap(float solved) {
      return Math.min(solved, 89.0F);
   }

   static float grimPlacementPitchGoal(float solved, double windowLow, double windowHigh) {
      float goal = solved;
      float bound = 89.0F;
      if (Double.isFinite(windowLow) && Double.isFinite(windowHigh) && windowLow <= windowHigh) {
         goal = (float)Mth.clamp(solved, windowLow, windowHigh);
         if (windowLow > 89.0) {
            bound = 89.3F;
         }

         if (windowLow > 89.3F) {
            bound = 89.5F;
         }
      }

      return Math.min(goal, bound);
   }

   static boolean grimPlacementPitchLegal(float emitted) {
      return emitted <= 89.5F;
   }

   static float grimPlacementPostureYaw(float movementYaw) {
      return Mth.wrapDegrees(movementYaw + 180.0F);
   }

   static float grimHandbackDebt(float sentYaw, float vanillaYaw) {
      return Float.isNaN(sentYaw) ? 0.0F : sentYaw - vanillaYaw;
   }

   static int grimWindDownBudget(float yawDebt, float pitchDelta) {
      int yawTicks = (int)Math.ceil(Math.abs(yawDebt) / 37.0F);
      int pitchTicks = (int)Math.ceil(Math.abs(pitchDelta) / 37.0F);
      return Mth.clamp(Math.max(yawTicks, pitchTicks) + 4, 8, 27);
   }

   static boolean grimWindDownReleasable(float yawDebt, float pitchDelta) {
      return Math.abs(yawDebt) <= 2.0F && Math.abs(pitchDelta) <= 2.0F;
   }

   private void advanceGrimWindDown() {
      if (this.grimWindingDown) {
         if (MC != null && MC.player != null && this.grimSilentRotation != null) {
            if (this.grimAimRaw == null) {
               this.grimAimRaw = this.grimSilentRotation;
            }

            RiptideRotationUtil.Rotation camera = RiptideRotationUtil.playerRotation(MC.player);
            double gcd = RiptideRotationUtil.sensitivityGcd();
            float debt = grimHandbackDebt(grimSentYaw, MC.player.getYRot());
            float pitchDelta = camera.pitch() - this.grimAimRaw.pitch();
            float yawCap = this.grimWindDownElapsed == 0 ? 20.0F : 37.0F;
            float pitchCap = this.grimWindDownElapsed == 0 ? 20.0F : 37.0F;
            if (RiptideHumanRotation.isInitialized(this.tellyStream)) {
               this.grimSilentRotation = RiptideHumanRotation.step(this.tellyStream, camera, yawCap, pitchCap, gcd, false);
               this.grimAimRaw = this.grimSilentRotation;
            } else {
               this.grimAimRaw = stepGrimAimRotation(
                  this.grimAimSmoother, this.grimAimRaw, -debt, pitchDelta, this.grimAimDirectionSample(camera), gcd, yawCap, pitchCap
               );
               this.grimSilentRotation = grimShapeOutgoing(this.grimAimRaw, this.grimSilentRotation, gcd, this.grimNextDitherCounts(), this.grimLastPlaceYaw);
            }

            this.grimWindDownElapsed++;
            this.grimWindDownTicks = Math.max(this.grimWindDownTicks, Math.min(grimWindDownBudget(debt, pitchDelta), 27 - this.grimWindDownElapsed));
            if (this.grimWindDownTicks > 0) {
               this.grimWindDownTicks--;
            }

            this.grimRotationResetTicks = Math.max(this.grimRotationResetTicks, 1);
            if (grimWindDownReleasable(debt, pitchDelta)) {
               this.releaseGrimStreamNow();
            } else if (this.grimWindDownElapsed >= 27) {
               this.traceGrim("wind-down-forced d=" + String.format(Locale.ROOT, "%.2f", debt), null);
               this.releaseGrimStreamNow();
            }
         } else {
            this.releaseGrimStreamNow();
         }
      }
   }

   void releaseGrimStreamNow() {
      this.grimSilentRotation = null;
      this.grimAimRaw = null;
      this.grimRotationResetTicks = 0;
      this.grimRotationStepTick = Integer.MIN_VALUE;
      this.grimWindingDown = false;
      this.grimWindDownTicks = 0;
      this.grimWindDownElapsed = 0;
      this.grimDitherCounts = 0;
      this.grimAimSmoother.halt();
      this.grimAimPrevGoal = null;
      this.grimAimDirectionChange = 0.0F;
      RiptideHumanRotation.clear(this.tellyStream);
   }

   void rollGrimSessionOffsets() {
      this.sessionPitchOffset = 68.0F + this.rotationRandom.nextFloat() * 12.0F;
   }

   private void rollTellyLookOffset() {
      float magnitude = 0.18F + this.rotationRandom.nextFloat() * 0.22F;
      this.tellyLookYawOffset = this.rotationRandom.nextBoolean() ? magnitude : -magnitude;
   }

   private void runTellyTick() {
      this.tellyJumpThisTick = false;
      this.tellySneakThisTick = false;
      this.tellyGroundLaunchAllowed = false;
      this.tellyAirStrafeThisTick = ScaffoldModule.TellyStrafe.NONE;
      this.tellyHoldStrafe = ScaffoldModule.TellyStrafe.NONE;
      this.maintainTellyClickPipeline();
      if (!this.canRun()) {
         this.resetTellyState();
         this.advanceGrimWindDown();
         this.tickSlotReset();
      } else {
         LocalPlayer player = MC.player;
         boolean physicalForward = physicallyDown(MC.options.keyUp);
         boolean physicalSpace = physicallyDown(MC.options.keyJump);
         boolean physicalMoving = physicalForward
            || physicallyDown(MC.options.keyDown)
            || physicallyDown(MC.options.keyLeft)
            || physicallyDown(MC.options.keyRight);
         this.tellySpaceHeld = physicalSpace;
         if (shouldQueueTellyRise(physicalForward, physicalSpace, this.tellyPhysicalSpaceWasDown, this.tellyOwnsInput)) {
            this.tellyRiseQueued = true;
         }

         this.tellyPhysicalSpaceWasDown = physicalSpace;
         if (!this.tellyOwnsInput) {
            if (player.onGround()) {
               BlockPos groundedSupport = this.solidBlockUnder(player);
               if (groundedSupport != null) {
                  this.tellyLastGroundedSupport = groundedSupport.immutable();
                  this.tellyLastGroundedTick = RiptideSharedState.get().getClientTickCounter();
               }
            }

            if (physicalMoving && !player.onGround() && this.tellyEmergencyCatchApplies(player)) {
               this.beginTellyEmergencyCatch(player);
            } else {
               if (!physicalMoving || !player.onGround()) {
                  if (!physicalMoving) {
                     this.tellyCourseLatched = false;
                  }

                  this.advanceGrimIdleStream(false);
                  this.tickSlotReset();
                  return;
               }

               BlockPos under = this.solidBlockUnder(player);
               if (under == null) {
                  this.advanceGrimIdleStream(false);
                  this.tickSlotReset();
                  return;
               }

               if (!this.isValidBlock(this.planningStack())) {
                  this.advanceGrimIdleStream(false);
                  this.tickSlotReset();
                  return;
               }

               this.beginTellyControl(player, under);
            }
         }

         if (this.tellyCourseLatched) {
            this.updateTellyCourseIntent(player);
         }

         this.grimSilentRotation = this.advanceTellyRotationStream(player);
         this.grimRotationResetTicks = 5;
         this.tellyStopRequested = !physicalMoving;
         if (player.onGround()) {
            this.runGroundedTelly(player);
         } else if (this.tellyPhase == ScaffoldModule.TellyPhase.RECOVERING) {
            this.beginTellyWalkOffCatch(player);
            this.runAirborneTelly(player);
         } else {
            this.runAirborneTelly(player);
         }

         this.continueTellyReturnAfterPlanning(player);
         if (this.tellyOwnsInput
            && player.onGround()
            && this.tellyMotion == ScaffoldModule.TellyMotion.HOLD
            && !this.tellyStopRequested
            && !this.tellyFinishing) {
            if (++this.tellyHoldWatchdogTicks >= 30) {
               this.tellyHoldWatchdogTicks = 0;
               this.tellyCourseDeviationTicks = 0;
               this.tellyTurnSettling = false;
               this.tellySettleHoldTicks = 0;
               this.tellySettleDwellTicks = 0;
               this.tellyEdgeHoldTicks = 0;
               this.clearTellyTurnReserve();
               if (this.tellyPhase == ScaffoldModule.TellyPhase.RECOVERING) {
                  this.tellyPhase = ScaffoldModule.TellyPhase.FORWARD_DWELL;
                  this.tellyRecoveryTicks = 0;
               }
            }
         } else {
            this.tellyHoldWatchdogTicks = 0;
         }

         this.tickSlotReset();
      }
   }

   private void beginTellyControl(LocalPlayer player, BlockPos under) {
      this.tellyOwnsInput = true;
      this.tellyStopRequested = false;
      this.tellyFinishing = false;
      this.tellyPhase = ScaffoldModule.TellyPhase.FORWARD_DWELL;
      this.tellyMotion = ScaffoldModule.TellyMotion.FORWARD;
      this.grimWindingDown = false;
      this.grimWindDownElapsed = 0;
      if (!RiptideHumanRotation.isInitialized(this.tellyStream)) {
         RiptideHumanRotation.seed(this.tellyStream, this.serverRotation());
      }

      this.tellySmoothedRotation = RiptideHumanRotation.current(this.tellyStream);
      float lookYaw = this.tellyMovementYaw(player);
      boolean hadCourse = this.tellyCourseLatched && Float.isFinite(this.tellyAnchorYaw);
      float previousAnchor = this.tellyAnchorYaw;
      this.clearTellyTurnReserve();
      if (hadCourse) {
         float lookDeviation = Math.abs(RiptideRotationUtil.angleDifference(this.tellyAnchorYaw, lookYaw));
         if (lookDeviation > 45.0F) {
            float snapped = snapTellyYaw(lookYaw);
            if (Float.compare(snapped, snapTellyYaw(previousAnchor)) != 0) {
               this.rollTellyLookOffset();
            }

            this.tellyAnchorYaw = Mth.wrapDegrees(snapped);
         } else {
            this.tellyAnchorYaw = Mth.wrapDegrees(this.tellyAnchorYaw);
         }
      } else {
         this.rollTellyLookOffset();
         this.tellyAnchorYaw = Mth.wrapDegrees(snapTellyYaw(lookYaw));
      }

      this.tellyCourseLatched = true;
      if (hadCourse && Float.compare(snapTellyYaw(previousAnchor), snapTellyYaw(this.tellyAnchorYaw)) != 0) {
         this.armTellyTurnReserve(under, tellyDirectionForYaw(previousAnchor));
         this.beginTellyTurnSettle();
      }

      this.tellyForwardPitch = Mth.clamp(player.getXRot(), -82.0F, 82.0F);
      this.tellyLineOrigin = laneOrigin(under, player.position(), this.tellyAnchorYaw);
      this.tellyLaneCenter = laneCoordinate(this.tellyLineOrigin, this.tellyAnchorYaw);
      this.tellyCourseDeviationTicks = 0;
      this.tellyGroundSteeringActive = false;
      this.tellyGroundSteerOffset = 0.0F;
      this.clearTellyAirCorrection();
      this.tellyLastBridge = under.immutable();
      this.tellyBridgeY = under.getY();
      this.tellyForwardDwellTicks = 0;
      this.tellyAirTicks = 0;
      this.tellyEdgeHoldTicks = 0;
      this.tellyTarget = null;
      this.tellyQueuedBlock = null;
      this.tellyPlacementQueued = false;
      this.tellyFaceOffsetIndex = -1;
      this.tellyReturnCompleted = true;
   }

   private void updateTellyCourseIntent(LocalPlayer player) {
      float deviation = Math.abs(RiptideRotationUtil.angleDifference(this.tellyAnchorYaw, this.tellyMovementYaw(player)));
      this.tellyCourseDeviationTicks = nextTellyCourseDeviationTicks(this.tellyCourseDeviationTicks, deviation);
   }

   static int nextTellyCourseDeviationTicks(int current, float deviation) {
      if (deviation > 45.0F) {
         return current + 1;
      } else {
         return deviation < 35.0F ? 0 : Math.max(0, current - 1);
      }
   }

   private void applyTellyCourseTurn(LocalPlayer player, BlockPos laneSupport) {
      if (laneSupport != null && this.tellyCourseDeviationTicks >= 3) {
         this.tellyCourseDeviationTicks = 0;
         float previousAnchor = this.tellyAnchorYaw;
         Direction previousDirection = tellyDirectionForYaw(previousAnchor);
         float snapped = snapTellyYaw(this.tellyMovementYaw(player));
         if (Float.compare(snapped, snapTellyYaw(previousAnchor)) != 0) {
            this.rollTellyLookOffset();
         }

         this.tellyAnchorYaw = Mth.wrapDegrees(snapped);
         this.tellyCourseLatched = true;
         this.tellyLineOrigin = laneOrigin(laneSupport, player.position(), this.tellyAnchorYaw);
         this.tellyLaneCenter = laneCoordinate(this.tellyLineOrigin, this.tellyAnchorYaw);
         this.clearTellyGroundSteering();
         this.clearTellyAirCorrection();
         if (Float.compare(snapTellyYaw(previousAnchor), snapTellyYaw(this.tellyAnchorYaw)) != 0) {
            this.armTellyTurnReserve(laneSupport, previousDirection);
            this.beginTellyTurnSettle();
         }
      }
   }

   private void armTellyTurnReserve(BlockPos support, Direction entryDirection) {
      this.clearTellyTurnReserve();
      if (support != null && entryDirection != null && entryDirection.getAxis().isHorizontal()) {
         this.tellyTurnReserveSupport = support.immutable();
         this.tellyTurnReserveCell = support.relative(entryDirection).immutable();
      }
   }

   private void clearTellyTurnReserve() {
      this.tellyTurnReserveSupport = null;
      this.tellyTurnReserveCell = null;
      this.tellyTurnReserveQueued = false;
   }

   private boolean tellyTurnIntentPending() {
      return this.tellyCourseDeviationTicks >= 2;
   }

   private void beginTellyTurnSettle() {
      this.tellyTarget = null;
      this.tellyFaceOffsetIndex = -1;
      if (!this.tellyTurnSettling) {
         this.tellyTurnSettling = true;
         this.tellySettleHoldTicks = 0;
         this.tellySettleDwellTicks = 1;
         this.clearTellyGroundSteering();
      }
   }

   private void runGroundedTelly(LocalPlayer player) {
      BlockPos under = this.solidBlockUnder(player);
      if (this.tellyPhase == ScaffoldModule.TellyPhase.RECOVERING) {
         this.runTellyRunupRecovery(player, under != null ? under : this.tellyLastBridge);
      } else {
         if (under != null) {
            float anchorBeforeTurn = this.tellyAnchorYaw;
            this.applyTellyCourseTurn(player, under);
            double laneError = this.tellyLaneCenter - laneCoordinate(player.position(), this.tellyAnchorYaw);
            if (anchorBeforeTurn != this.tellyAnchorYaw && Math.abs(laneError) > 0.55) {
               this.tellyLineOrigin = laneOrigin(under, player.position(), this.tellyAnchorYaw);
               this.tellyLaneCenter = laneCoordinate(this.tellyLineOrigin, this.tellyAnchorYaw);
               this.clearTellyGroundSteering();
            }
         }

         if (under == null) {
            if (this.tellyLastBridge == null) {
               this.releaseTellyControl();
            } else {
               if (this.tellyAirTicks > 0
                  || this.tellyPhase == ScaffoldModule.TellyPhase.LAUNCH
                  || this.tellyPhase == ScaffoldModule.TellyPhase.AIMING
                  || this.tellyPhase == ScaffoldModule.TellyPhase.RETURNING) {
                  this.finishTellyCycleOnGround(this.tellyLastBridge);
               }

               if (!this.tellyStopRequested && !this.tellyFinishing) {
                  this.applyTellyCourseTurn(player, this.tellyLastBridge);
                  if (!this.tellyTurnSettling || !this.runTellySettleHold(player, this.tellyLastBridge)) {
                     if (this.tellyTarget == null) {
                        ItemStack lipStack = this.planningStack();
                        if (this.isValidBlock(lipStack)) {
                           this.tellyTarget = this.nextFlatTellyPlacement(player, lipStack);
                        }
                     }

                     if (this.tellyTarget != null
                        && !this.tellyTurnIntentPending()
                        && this.isTellyLaunchCatchable(player, this.tellyLastBridge)
                        && this.tellyFlickBackForLaunch()) {
                        this.startTellyLaunch(player, this.tellyTarget);
                     } else {
                        if (this.tellyTurnIntentPending()) {
                           this.tellyEdgeHoldTicks++;
                           if (this.tellyEdgeHoldTicks < 20) {
                              this.tellyMotion = ScaffoldModule.TellyMotion.HOLD;
                              this.tellySneakThisTick = true;
                              return;
                           }

                           this.tellyCourseDeviationTicks = 0;
                           this.tellyEdgeHoldTicks = 0;
                        }

                        if (!this.isValidBlock(this.planningStack())) {
                           this.releaseTellyControl();
                        } else {
                           this.tellyMotion = ScaffoldModule.TellyMotion.FORWARD;
                           this.tellySneakThisTick = false;
                        }
                     }
                  }
               } else {
                  this.tellyMotion = ScaffoldModule.TellyMotion.HOLD;
                  this.tellySneakThisTick = true;
                  this.tellyEdgeHoldTicks++;
                  if (this.tellyEdgeHoldTicks >= 40 || !this.tryTellyLipSecurePlacement(player)) {
                     if (player.onGround() && player.getDeltaMovement().horizontalDistance() < 0.02) {
                        this.releaseTellyControl();
                     }
                  }
               }
            }
         } else if (!this.tellyTurnSettling || !this.runTellySettleHold(player, under)) {
            if (this.tellyFinishing) {
               this.runTellyGroundedStopHold(player, under);
            } else {
               if (this.tellyAirTicks > 0
                  || this.tellyPhase == ScaffoldModule.TellyPhase.LAUNCH
                  || this.tellyPhase == ScaffoldModule.TellyPhase.AIMING
                  || this.tellyPhase == ScaffoldModule.TellyPhase.RETURNING) {
                  this.finishTellyCycleOnGround(under);
                  this.completeTellyReturn(95.0F);
                  if (tellyLandingTransition(this.atTellyEdge(player, under)) == ScaffoldModule.TellyLandingTransition.DWELL) {
                     return;
                  }

                  this.tellyPhase = ScaffoldModule.TellyPhase.RUNNING;
                  this.tellyForwardDwellTicks = 0;
               }

               if (this.tellyPhase != ScaffoldModule.TellyPhase.FORWARD_DWELL || !this.runTellyForwardDwell(player, under)) {
                  boolean nearEdge = this.atTellyEdge(player, under);
                  this.tellyGroundLaunchAllowed = !nearEdge && this.tellyRunwayRemaining(player, under) > 4.5;
                  if (this.tellyStopRequested) {
                     this.runTellyGroundedStopHold(player, under);
                  } else {
                     this.tellyMotion = ScaffoldModule.TellyMotion.FORWARD;
                     this.updateTellyGroundSteering(player, under);
                     this.selectionPending = false;
                     InteractionHand hand = this.ensurePlacementHand();
                     if (hand == null) {
                        if (!this.selectionPending) {
                           this.releaseTellyControl();
                        } else {
                           this.tellyMotion = nearEdge ? ScaffoldModule.TellyMotion.HOLD : ScaffoldModule.TellyMotion.FORWARD;
                           this.tellySneakThisTick = nearEdge;
                           if (this.selectionPending) {
                              this.refreshSelectionReset();
                           }
                        }
                     } else {
                        this.refreshSelectionReset();
                        ItemStack stack = player.getItemInHand(hand);
                        if (!this.isValidBlock(stack)) {
                           if (!this.selectionPending) {
                              this.releaseTellyControl();
                           } else {
                              this.tellyMotion = nearEdge ? ScaffoldModule.TellyMotion.HOLD : ScaffoldModule.TellyMotion.FORWARD;
                              this.tellySneakThisTick = nearEdge;
                           }
                        } else {
                           this.tellyLastBridge = under.immutable();
                           this.tellyBridgeY = under.getY();
                           ScaffoldModule.TellyPlacement first = this.nextFlatTellyPlacement(player, stack);
                           this.tellyTarget = first;
                           if (this.tellyGroundLaunchAllowed && this.tellySpaceHeld && !this.tellyTurnIntentPending()) {
                              this.startTellyLaunch(player, first);
                           } else if (!nearEdge) {
                              this.tellyEdgeHoldTicks = 0;
                              if (this.tellyEarlyLaunchAllowed(player, under, first)) {
                                 this.startTellyLaunch(player, first);
                              }
                           } else if (first == null) {
                              this.tellyEdgeHoldTicks++;
                              if (this.tellyEdgeHoldTicks >= 40) {
                                 this.releaseTellyControl();
                              } else {
                                 this.tellyMotion = ScaffoldModule.TellyMotion.HOLD;
                                 this.tellySneakThisTick = true;
                              }
                           } else {
                              this.tellyEdgeHoldTicks = 0;
                              if (this.tellyTurnIntentPending()) {
                                 this.tellyEdgeHoldTicks++;
                                 if (this.tellyEdgeHoldTicks < 20) {
                                    this.tellyMotion = ScaffoldModule.TellyMotion.HOLD;
                                    this.tellySneakThisTick = true;
                                    return;
                                 }

                                 this.tellyCourseDeviationTicks = 0;
                                 this.tellyEdgeHoldTicks = 0;
                              }

                              boolean launchCatchable = !this.tellyRiseQueued && !this.tellySpaceHeld
                                 ? this.isTellyLaunchCatchable(player, under)
                                 : this.isTellyRiseLaunchCatchable(player, under);
                              if (requiresTellyRunupRecovery(launchCatchable)) {
                                 this.beginTellyRunupRecovery(player, under);
                              } else if (!this.tellyFlickBackForLaunch()) {
                                 this.tellyMotion = ScaffoldModule.TellyMotion.HOLD;
                                 this.tellySneakThisTick = true;
                              } else {
                                 this.startTellyLaunch(player, first);
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

   private boolean runTellySettleHold(LocalPlayer player, BlockPos under) {
      if (this.tellyStopRequested) {
         this.tellyTurnSettling = false;
         return false;
      } else {
         if (this.tellyAirTicks > 0
            || this.tellyPhase == ScaffoldModule.TellyPhase.LAUNCH
            || this.tellyPhase == ScaffoldModule.TellyPhase.AIMING
            || this.tellyPhase == ScaffoldModule.TellyPhase.RETURNING) {
            this.finishTellyCycleOnGround(under);
         }

         Vec3 velocity = player.getDeltaMovement();
         Vec3 forward = this.tellyForwardVector();
         Vec3 left = this.tellyLeftVector();
         double laneError = this.tellyLaneCenter - laneCoordinate(player.position(), this.tellyAnchorYaw);
         if (Math.abs(laneError) > 0.55) {
            this.tellyLineOrigin = laneOrigin(under, player.position(), this.tellyAnchorYaw);
            this.tellyLaneCenter = laneCoordinate(this.tellyLineOrigin, this.tellyAnchorYaw);
            laneError = this.tellyLaneCenter - laneCoordinate(player.position(), this.tellyAnchorYaw);
         }

         boolean settled = this.tellySmoothedRotation != null
            && tellyTurnSettled(
               this.tellySmoothedRotation.yaw(),
               this.tellyCourseLookYaw(),
               velocity.x * forward.x + velocity.z * forward.z,
               velocity.x * left.x + velocity.z * left.z,
               laneError,
               player.onGround()
            );
         if (settled && this.tellySettleDwellTicks <= 0) {
            this.tellyTurnSettling = false;
            this.clearTellyTurnReserve();
            this.tellyPhase = ScaffoldModule.TellyPhase.FORWARD_DWELL;
            this.tellyMotion = ScaffoldModule.TellyMotion.FORWARD;
            this.tellyRecoveryTicks = 0;
            this.tellyForwardDwellTicks = 0;
            return true;
         } else {
            if (settled) {
               this.tellySettleDwellTicks--;
            }

            this.clearTellyGroundSteering();
            this.tellyMotion = ScaffoldModule.TellyMotion.HOLD;
            double settleSpeed = velocity.horizontalDistance();
            Direction overhang = this.tellyOverhangDirection(player);
            this.tellySneakThisTick = settleSpeed >= 0.08 || overhang != null || this.atTellyEdge(player, under);
            if (overhang != null
               && isTellyTurnReserveDirection(this.tellyTurnReserveSupport, this.tellyTurnReserveCell, overhang)
               && this.tryTellyTurnReservePlacement(player)) {
               this.tellySettleHoldTicks++;
               if (this.tellySettleHoldTicks >= 30) {
                  this.clearTellyTurnReserve();
                  this.releaseTellyControl();
               }

               return true;
            } else {
               this.tellyHoldStrafe = this.tellyHoldLaneStrafe(player);
               this.tellySettleHoldTicks++;
               if (this.tellySettleHoldTicks >= 30) {
                  this.releaseTellyControl();
               }

               return true;
            }
         }
      }
   }

   private void runTellyGroundedStopHold(LocalPlayer player, BlockPos under) {
      this.clearTellyGroundSteering();
      this.tellyMotion = ScaffoldModule.TellyMotion.HOLD;
      this.tellySneakThisTick = true;
      boolean atEdge = this.atTellyEdge(player, under);
      Direction overhang = this.tellyOverhangDirection(player);
      if (atEdge || overhang != null) {
         this.tellyEdgeHoldTicks++;
         if (this.tellyEdgeHoldTicks < 40) {
            if (overhang != null
               && isTellyTurnReserveDirection(this.tellyTurnReserveSupport, this.tellyTurnReserveCell, overhang)
               && this.tryTellyTurnReservePlacement(player)) {
               return;
            }

            if (atEdge && this.tryTellyLipSecurePlacement(player)) {
               return;
            }
         }
      }

      double releaseSpeed = !atEdge && overhang == null ? 0.06 : 0.02;
      if (player.onGround() && player.getDeltaMovement().horizontalDistance() < releaseSpeed) {
         this.releaseTellyControl();
      }
   }

   private boolean runTellyForwardDwell(LocalPlayer player, BlockPos under) {
      this.clearTellyGroundSteering();
      boolean nearEdge = this.atTellyEdge(player, under);
      if (this.tellyStopRequested) {
         this.tellyMotion = ScaffoldModule.TellyMotion.HOLD;
         this.tellySneakThisTick = true;
         if (player.onGround() && player.getDeltaMovement().horizontalDistance() < 0.06) {
            this.releaseTellyControl();
         }

         return true;
      } else {
         this.tellyForwardDwellTicks = nextTellyForwardDwellTicks(this.tellyForwardDwellTicks, true);
         if (!tellyForwardDwellComplete(this.tellyForwardDwellTicks)) {
            this.tellyMotion = nearEdge ? ScaffoldModule.TellyMotion.HOLD : ScaffoldModule.TellyMotion.FORWARD;
            this.tellySneakThisTick = nearEdge;
            return true;
         } else {
            this.tellyPhase = ScaffoldModule.TellyPhase.RUNNING;
            this.tellyMotion = ScaffoldModule.TellyMotion.FORWARD;
            this.tellySneakThisTick = false;
            this.tellyForwardDwellTicks = 0;
            return false;
         }
      }
   }

   private void beginTellyRunupRecovery(LocalPlayer player, BlockPos support) {
      if (this.tellyPhase != ScaffoldModule.TellyPhase.RECOVERING && player != null && support != null) {
         this.tellyPhase = ScaffoldModule.TellyPhase.RECOVERING;
         this.tellyMotion = ScaffoldModule.TellyMotion.HOLD;
         this.tellySneakThisTick = true;
         this.tellyRecoveryTicks = 0;
         this.tellyTarget = null;
         this.tellyPlacementQueued = false;
         this.tellyQueuedBlock = null;
         this.tellyLastBridge = support.immutable();
         this.clearTellyGroundSteering();
         this.clearTellyAirCorrection();
      }
   }

   private void runTellyRunupRecovery(LocalPlayer player, BlockPos support) {
      if (player == null || support == null) {
         this.releaseTellyControl();
      } else if (this.tellyStopRequested) {
         this.tellyMotion = ScaffoldModule.TellyMotion.HOLD;
         this.tellySneakThisTick = true;
         if (player.onGround() && player.getDeltaMovement().horizontalDistance() < 0.06) {
            this.releaseTellyControl();
         }
      } else {
         this.applyTellyCourseTurn(player, support);
         this.tellyMotion = ScaffoldModule.TellyMotion.HOLD;
         this.tellySneakThisTick = true;
         this.tellyHoldStrafe = this.tellyHoldLaneStrafe(player);
         this.tellyRecoveryTicks++;
         if (this.tellyRecoveryTicks >= 60) {
            this.releaseTellyControl();
         } else {
            if (this.isTellyLaunchCatchable(player, support)) {
               this.tellyPhase = ScaffoldModule.TellyPhase.FORWARD_DWELL;
               this.tellyMotion = ScaffoldModule.TellyMotion.FORWARD;
               this.tellySneakThisTick = false;
               this.tellyLineOrigin = laneOrigin(support, player.position(), this.tellyAnchorYaw);
               this.tellyLaneCenter = laneCoordinate(this.tellyLineOrigin, this.tellyAnchorYaw);
               this.tellyLastBridge = support.immutable();
               this.tellyBridgeY = support.getY();
               this.tellyTarget = null;
               this.tellyRecoveryTicks = 0;
               this.tellyForwardDwellTicks = 0;
            }
         }
      }
   }

   private void startTellyLaunch(LocalPlayer player, ScaffoldModule.TellyPlacement first) {
      this.clearTellyTurnReserve();
      this.tellyCycleSerial++;
      this.tellyCycleRises = this.tellyRiseQueued || this.tellySpaceHeld;
      this.tellyRiseQueued = false;
      this.tellyFinishing = false;
      this.tellyRaisedBlockPlaced = false;
      this.tellyAimCommitted = false;
      this.tellyRaisedCell = null;
      this.tellyFlatPlacements = 0;
      this.tellyWalkOffCatch = false;
      this.tellyWalkOffGraceTicks = 0;
      this.tellyTakeoffY = player.getY();
      this.tellyTakeoffProgress = player.position().dot(this.tellyForwardVector());
      this.tellyFailedClicks = 0;
      this.tellyAirTicks = 0;
      this.tellyEdgeHoldTicks = 0;
      this.tellyTarget = first;
      this.tellyReturnCompleted = true;
      this.tellyPhase = ScaffoldModule.TellyPhase.LAUNCH;
      this.tellyMotion = ScaffoldModule.TellyMotion.FORWARD;
      this.clearTellyGroundSteering();
      this.clearTellyAirCorrection();
      this.clearTellyLaunchStrafe();
      this.tellyJumpThisTick = true;
      this.tellySneakThisTick = false;
   }

   private void clearTellyLaunchStrafe() {
      this.grimInputSidewaysOctant = 0;
      this.grimInputSidewaysHold = 0;
      this.grimInputSidewaysFromCorrection = false;
   }

   private void runAirborneTelly(LocalPlayer player) {
      this.tellyAirTicks++;
      if (this.tellyLastBridge == null) {
         this.releaseTellyControl();
      } else if (this.tellyFinishing) {
         this.tellyMotion = ScaffoldModule.TellyMotion.FORWARD;
         this.tellyTarget = null;
      } else {
         if (this.tellyPhase == ScaffoldModule.TellyPhase.RUNNING || this.tellyPhase == ScaffoldModule.TellyPhase.FORWARD_DWELL) {
            if (this.isTellyLandingSupported(player, this.tellyBridgeY)) {
               this.tellyMotion = ScaffoldModule.TellyMotion.FORWARD;
               return;
            }

            this.beginTellyWalkOffCatch(player);
         }

         this.tellyMotion = ScaffoldModule.TellyMotion.FORWARD;
         ItemStack stack = this.planningStack();
         if (this.tellyRaisedBlockPlaced
            && this.tellyRaisedCell != null
            && !this.isTellySupport(MC.level.getBlockState(this.tellyRaisedCell), this.tellyRaisedCell)) {
            this.tellyRaisedBlockPlaced = false;
            if (this.tellyLastBridge != null && this.tellyLastBridge.getY() > this.tellyBridgeY) {
               this.tellyLastBridge = new BlockPos(this.tellyLastBridge.getX(), this.tellyBridgeY, this.tellyLastBridge.getZ());
            }

            this.tellyRaisedCell = null;
         }

         int landingBlockY = this.tellyRaisedBlockPlaced ? this.tellyBridgeY + 1 : this.tellyBridgeY;
         boolean hasConfirmedCatchBlock = this.tellyFlatPlacements > 0;
         boolean descending = player.getDeltaMovement().y < -0.035;
         boolean landingSecured = hasConfirmedCatchBlock && this.isTellyLandingSupported(player, landingBlockY);
         if (!landingSecured) {
            this.updateTellyAirCorrection(player, landingBlockY + 1.0);
         } else {
            this.tellyAirStrafeCooldown = Math.max(0, this.tellyAirStrafeCooldown - 1);
         }

         if (!landingSecured && this.tellyRaisedBlockPlaced && descending && this.tellyRiseOvershootCoast(player)) {
            this.tellyMotion = ScaffoldModule.TellyMotion.HOLD;
         }

         boolean turnPending = this.tellyTurnIntentPending();
         boolean normalRunwayCovered = landingSecured && this.tellyLandingRunwayCovered(player, landingBlockY, 1);
         boolean turnReserveCovered = !turnPending
            || landingSecured && this.tellyLandingRunwayCovered(player, landingBlockY, tellyRunwayReserveBlocks(turnPending));
         boolean chainCovered = tellyChainCovered(landingSecured, normalRunwayCovered, turnPending, turnReserveCovered);
         boolean walkOffGrace = this.tellyWalkOffCatch && this.tellyWalkOffGraceTicks > 0;
         if (this.tellyWalkOffGraceTicks > 0) {
            this.tellyWalkOffGraceTicks--;
         }

         if (!landingSecured
            && !walkOffGrace
            && !this.tellyPlacementQueued
            && this.tellyClickCooldown <= 0
            && (this.tellyLandingOffLane(player) || this.tellyCatchPlaneImminent(player, descending))) {
            this.tryTellyUrgentChainPlacement(player, stack);
         }

         if (!landingSecured && this.tellyFailedClicks >= 3) {
            this.abortMissedTelly(player);
         } else if (!landingSecured && !walkOffGrace && this.missedTellyCatchWindow(player, descending)) {
            if (!this.tellyPlacementQueued && this.tellyClickCooldown <= 0 && !this.tryTellyUrgentChainPlacement(player, stack)) {
               this.abortMissedTelly(player);
            }
         } else if (!this.isValidBlock(stack)) {
            this.tellyPhase = ScaffoldModule.TellyPhase.RETURNING;
         } else {
            ScaffoldModule.TellyPhase coveredPhase = nextTellyCoveragePhase(this.tellyPhase, chainCovered, this.tellyCycleRises && !this.tellyRaisedBlockPlaced);
            if (coveredPhase == ScaffoldModule.TellyPhase.RETURNING && this.tellyPhase != ScaffoldModule.TellyPhase.RETURNING) {
               this.tellyTarget = null;
               this.tellyPhase = coveredPhase;
            } else {
               if (landingSecured
                  && this.tellyCycleRises
                  && !this.tellyRaisedBlockPlaced
                  && player.getDeltaMovement().y < 0.0
                  && player.getY() < this.tellyBridgeY + 2.0) {
                  this.tellyCycleRises = false;
                  this.tellyTarget = null;
                  if (chainCovered) {
                     this.tellyPhase = ScaffoldModule.TellyPhase.RETURNING;
                     return;
                  }
               }

               if (this.tellyPhase != ScaffoldModule.TellyPhase.RETURNING) {
                  if (this.tellyTarget == null) {
                     if (chainCovered) {
                        if (!this.tellyCycleRises || this.tellyRaisedBlockPlaced) {
                           this.tellyPhase = nextTellyCoveragePhase(this.tellyPhase, true, false);
                        }

                        return;
                     }

                     if (this.tellyRiseStillPossible(player) && this.tellyRiseSupportCell(player) != null) {
                        return;
                     }

                     this.tellyTarget = this.nextFlatTellyPlacement(player, stack);
                  }

                  if (this.tellyTarget != null) {
                     this.tellyPhase = ScaffoldModule.TellyPhase.AIMING;
                     this.selectionPending = false;
                     InteractionHand hand = this.ensurePlacementHand();
                     if (hand == null) {
                        if (this.selectionPending) {
                           this.refreshSelectionReset();
                        }
                     } else {
                        this.refreshSelectionReset();
                        ItemStack held = player.getItemInHand(hand);
                        if (this.isValidBlock(held)) {
                           landingBlockY = this.tellyRaisedBlockPlaced ? this.tellyBridgeY + 1 : this.tellyBridgeY;
                           hasConfirmedCatchBlock = this.tellyFlatPlacements > 0;
                           landingSecured = hasConfirmedCatchBlock && this.isTellyLandingSupported(player, landingBlockY);
                           turnPending = this.tellyTurnIntentPending();
                           normalRunwayCovered = landingSecured && this.tellyLandingRunwayCovered(player, landingBlockY, 1);
                           turnReserveCovered = !turnPending
                              || landingSecured && this.tellyLandingRunwayCovered(player, landingBlockY, tellyRunwayReserveBlocks(turnPending));
                           chainCovered = tellyChainCovered(landingSecured, normalRunwayCovered, turnPending, turnReserveCovered);
                           if (chainCovered && (!this.tellyCycleRises || this.tellyRaisedBlockPlaced)) {
                              this.tellyPhase = nextTellyCoveragePhase(this.tellyPhase, true, false);
                              this.tellyTarget = null;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean tellyEmergencyCatchApplies(LocalPlayer player) {
      if (!this.isValidBlock(this.planningStack())) {
         return false;
      } else if (this.tellyLastGroundedSupport == null) {
         return false;
      } else {
         int sinceGrounded = RiptideSharedState.get().getClientTickCounter() - this.tellyLastGroundedTick;
         return sinceGrounded >= 0 && sinceGrounded <= 6 ? !this.hasSupportBelow(player.getX(), player.getY(), player.getZ(), 2) : false;
      }
   }

   private void beginTellyEmergencyCatch(LocalPlayer player) {
      this.beginTellyControl(player, this.tellyLastGroundedSupport);
      this.beginTellyWalkOffCatch(player);
   }

   private void beginTellyWalkOffCatch(LocalPlayer player) {
      this.tellyCycleSerial++;
      this.tellyCycleRises = false;
      this.tellyRaisedBlockPlaced = false;
      this.tellyAimCommitted = false;
      this.tellyRaisedCell = null;
      this.tellyFlatPlacements = 0;
      this.tellyWalkOffCatch = true;
      this.tellyWalkOffGraceTicks = 3;
      this.tellyPlacementQueued = false;
      this.tellyQueuedBlock = null;
      this.tellyReturnCompleted = true;
      this.tellyFailedClicks = 0;
      this.tellyAirTicks = Math.max(this.tellyAirTicks, 3);
      this.tellyBridgeY = this.tellyLastBridge.getY();
      this.tellyTakeoffY = this.tellyBridgeY + 1.0;
      this.tellyTakeoffProgress = player.position().dot(this.tellyForwardVector()) - 1.45;
      this.tellyPhase = ScaffoldModule.TellyPhase.LAUNCH;
      this.tellyMotion = ScaffoldModule.TellyMotion.FORWARD;
      this.clearTellyGroundSteering();
      this.clearTellyAirCorrection();
   }

   public static void beforeHandleKeybinds() {
      if (MC != null && MC.player != null && MC.level != null) {
         if (ModuleRegistry.get("scaffold") instanceof ScaffoldModule scaffold && scaffold.isEnabled()) {
            if (scaffold.isGrimFamily()) {
               scaffold.refreshGrimRealClickHit();
            } else if (scaffold.isTellyMode() && scaffold.tellyOwnsInput && scaffold.canRun()) {
               scaffold.refreshTellyRealClickHit();
               if (!MC.player.onGround()) {
                  scaffold.armTellySilentPlacement();
               }
            }
         }
      }
   }

   public static boolean ownsGrimUseInput() {
      return ModuleRegistry.get("scaffold") instanceof ScaffoldModule scaffold
         && scaffold.isEnabled()
         && scaffold.ownsRealClickPipeline()
         && scaffold.grimAttemptState == ScaffoldModule.GrimPlacementAttemptState.ARMED
         && scaffold.grimRealQueuedTick == RiptideSharedState.get().getClientTickCounter();
   }

   public static boolean beginGrimUseInput() {
      if (ownsGrimUseInput() && !RiptideBlinkManager.holdsActionsWithoutMovement()) {
         Module module = ModuleRegistry.get("scaffold");
         ScaffoldModule scaffold = (ScaffoldModule)module;
         return RiptideInputClicker.beginScaffoldUseClick(scaffold.grimAttemptGeneration);
      } else {
         return false;
      }
   }

   public static void onPacketQueued(Packet<?> packet) {
      if (packet instanceof ServerboundUseItemOnPacket use) {
         synchronized (GRIM_QUEUED_USES) {
            purgeCollectedGrimQueuedUses();
            if (GRIM_QUEUED_USES.containsKey(new ScaffoldModule.GrimPacketIdentity(packet))) {
               return;
            }
         }

         long edgeGeneration = RiptideInputClicker.scaffoldUseGenerationInProgress();
         if (edgeGeneration > 0L) {
            if (ModuleRegistry.get("scaffold") instanceof ScaffoldModule scaffold && scaffold.isEnabled() && scaffold.ownsRealClickPipeline()) {
               ScaffoldModule.PlacementTarget pending = scaffold.grimRealPendingTarget;
               BlockHitResult hit = use.getHitResult();
               if (pending != null
                  && scaffold.grimAttemptState == ScaffoldModule.GrimPlacementAttemptState.ARMED
                  && edgeGeneration == scaffold.grimAttemptGeneration
                  && use.getHand() == scaffold.grimAttemptHand
                  && grimClickFeasible(hit, pending, scaffold.grimAttemptBuildsPlannedCell)) {
                  synchronized (GRIM_QUEUED_USES) {
                     purgeCollectedGrimQueuedUses();
                     ScaffoldModule.GrimPacketIdentity lookup = new ScaffoldModule.GrimPacketIdentity(packet);
                     if (!GRIM_QUEUED_USES.containsKey(lookup)) {
                        int ordinal = scaffold.grimAttemptSubmittedCount++;
                        ScaffoldModule.GrimQueuedUse queued = new ScaffoldModule.GrimQueuedUse(
                           scaffold.grimAttemptGeneration,
                           ordinal,
                           use.getHand(),
                           pending.placedBlock().immutable(),
                           pending.supportBlock().immutable(),
                           pending.face(),
                           scaffold.grimCommittedClickRotation
                        );
                        GRIM_QUEUED_USES.put(new ScaffoldModule.GrimPacketIdentity(packet, GRIM_QUEUED_USE_GC), queued);
                        if (ordinal > 0) {
                           scaffold.grimAttemptDuplicateSubmitted = true;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static void onPacketAbandoned(Packet<?> packet) {
      if (packet instanceof ServerboundUseItemOnPacket) {
         synchronized (GRIM_QUEUED_USES) {
            purgeCollectedGrimQueuedUses();
            GRIM_QUEUED_USES.remove(new ScaffoldModule.GrimPacketIdentity(packet));
         }
      }
   }

   private void refreshGrimRealClickHit() {
      if (this.grimRealQueuedTick == RiptideSharedState.get().getClientTickCounter()
         && this.grimRealPendingTarget != null
         && this.grimCommittedClickRotation != null
         && this.grimAttemptState == ScaffoldModule.GrimPlacementAttemptState.ARMED) {
         double reach = Math.max(MC.player.blockInteractionRange(), MC.player.entityInteractionRange());
         BlockHitResult ray = grimClickRay(MC.player.getEyePosition(), this.grimCommittedClickRotation, reach, MC.level, MC.player);
         if (ray != null) {
            MC.hitResult = ray;
         }
      }
   }

   private void armTellySilentPlacement() {
      this.maintainTellyClickPipeline();
      if (!this.tellyPlacementQueued && this.tellyClickCooldown <= 0 && !this.tellyFinishing) {
         this.selectionPending = false;
         InteractionHand hand = this.ensurePlacementHand();
         if (hand == null) {
            if (this.selectionPending) {
               this.refreshSelectionReset();
            }
         } else {
            this.refreshSelectionReset();
            ItemStack held = MC.player.getItemInHand(hand);
            if (this.isValidBlock(held)) {
               if (this.tellyRiseStillPossible(MC.player) && this.tellyLastBridge != null) {
                  ScaffoldModule.TellyPlacement rise = this.pendingTellyRiseTarget(MC.player, held);
                  if (rise != null) {
                     ScaffoldModule.TellyPlacement live = this.liveTellyPlacement(MC.player, rise);
                     if (live != null && !ModuleRegistry.shouldCancelUseExcept(live.target().hit(), hand, this.id())) {
                        this.commitTellyPlacement(live, hand, held, null);
                        return;
                     }
                  }
               }

               if (this.tellyTarget == null
                  && (this.tellyPhase == ScaffoldModule.TellyPhase.LAUNCH || this.tellyPhase == ScaffoldModule.TellyPhase.AIMING)
                  && (!this.tellyRiseStillPossible(MC.player) || this.tellyRiseSupportCell(MC.player) == null)) {
                  this.tellyTarget = this.nextFlatTellyPlacement(MC.player, held);
               }

               if (this.tellyTarget != null) {
                  boolean delayFirstClick = this.tellyShouldDelayFirstClick(MC.player);
                  this.tellyTraceDelay = delayFirstClick;
                  if (!delayFirstClick) {
                     ScaffoldModule.TellyPlacement live = this.liveTellyPlacement(MC.player, this.tellyTarget);
                     if (live != null) {
                        if (!ModuleRegistry.shouldCancelUseExcept(live.target().hit(), hand, this.id())) {
                           this.commitTellyPlacement(live, hand, held, null);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean missedTellyCatchWindow(LocalPlayer player, boolean descending) {
      if (!descending) {
         return false;
      } else {
         double targetTop = this.tellyBridgeY + 1.0;
         return player.getY() < targetTop + 0.05 || player.getY() < this.tellyTakeoffY - 1.15 || this.tellyAirTicks > 14;
      }
   }

   private boolean tellyCatchPlaneImminent(LocalPlayer player, boolean descending) {
      if (descending && player != null) {
         double catchTop = this.tellyRaisedBlockPlaced ? this.tellyBridgeY + 2.0 : this.tellyBridgeY + 1.0;
         double fall = Math.max(0.0, -player.getDeltaMovement().y);
         return player.getY() <= catchTop + Math.max(0.35, fall * 2.0);
      } else {
         return false;
      }
   }

   private boolean tellyLandingOffLane(LocalPlayer player) {
      double catchTop = this.tellyRaisedBlockPlaced ? this.tellyBridgeY + 2.0 : this.tellyBridgeY + 1.0;
      double feetY = Math.min(catchTop, player.getY());
      Vec3 landing = projectTellyLandingWithInput(player.position(), player.getDeltaMovement(), feetY, this.tellyEffectiveForward(player));
      double lane = laneCoordinate(new Vec3(landing.x, player.getY(), landing.z), this.tellyAnchorYaw);
      return Math.abs(lane - this.tellyLaneCenter) > 0.4;
   }

   private boolean tryTellyUrgentChainPlacement(LocalPlayer player, ItemStack stack) {
      if (this.tellyPlacementQueued) {
         return true;
      } else if (stack == null) {
         return false;
      } else if (this.tellyTarget != null && this.tellyTarget.raised()) {
         return true;
      } else {
         this.selectionPending = false;
         InteractionHand hand = this.ensurePlacementHand();
         if (hand == null) {
            if (this.selectionPending) {
               this.refreshSelectionReset();
               return true;
            } else {
               return false;
            }
         } else {
            this.refreshSelectionReset();
            ItemStack held = player.getItemInHand(hand);
            if (!this.isValidBlock(held)) {
               return false;
            } else {
               ScaffoldModule.TellyPlacement urgent = this.tellyTarget;
               if (urgent == null
                  || !isTellyForwardChainPlacement(
                     urgent.target().supportBlock(), urgent.target().placedBlock(), urgent.target().face(), this.tellyForwardDirection()
                  )) {
                  urgent = this.nextFlatTellyPlacement(player, held);
               }

               if (urgent != null
                  && isTellyForwardChainPlacement(
                     urgent.target().supportBlock(), urgent.target().placedBlock(), urgent.target().face(), this.tellyForwardDirection()
                  )) {
                  this.tellyTarget = urgent;
                  ScaffoldModule.TellyPlacement live = this.liveTellyPlacement(player, urgent);
                  if (live == null) {
                     return true;
                  } else if (ModuleRegistry.shouldCancelUseExcept(live.target().hit(), hand, this.id())) {
                     return false;
                  } else {
                     this.commitTellyPlacement(live, hand, held, null, true);
                     return true;
                  }
               } else {
                  return false;
               }
            }
         }
      }
   }

   static boolean isTellyForwardChainPlacement(BlockPos support, BlockPos placed, Direction face, Direction forward) {
      return support != null
         && placed != null
         && face != null
         && forward != null
         && face == forward
         && placed.equals(support.relative(forward))
         && placed.getY() == support.getY();
   }

   private void abortMissedTelly(LocalPlayer player) {
      this.tellyFinishing = true;
      this.tellyTarget = null;
      this.tellyPlacementQueued = false;
      this.tellyQueuedBlock = null;
      this.tellyPhase = ScaffoldModule.TellyPhase.RETURNING;
      this.tellyMotion = ScaffoldModule.TellyMotion.FORWARD;
   }

   private void maintainTellyClickPipeline() {
      if (MC != null && MC.player != null && MC.level != null) {
         int tick = RiptideSharedState.get().getClientTickCounter();
         if (tick != this.tellyPipelineTick) {
            this.tellyPipelineTick = tick;
            this.settleTellyRealClick();
            if (this.tellyClickCooldown > 0) {
               this.tellyClickCooldown--;
            }

            this.confirmTellyPlacement();
         }
      }
   }

   private void settleTellyRealClick() {
      if (this.tellyUsesRealClicks()) {
         this.drainGrimFinalUseWrite();
         this.resolveGrimUseOutcome();
         if (this.grimAttemptState != ScaffoldModule.GrimPlacementAttemptState.IDLE) {
            int age = Math.max(0, RiptideSharedState.get().getClientTickCounter() - this.grimRealQueuedTick);
            if (this.grimAttemptState == ScaffoldModule.GrimPlacementAttemptState.PREDICTED
               || this.grimAttemptState == ScaffoldModule.GrimPlacementAttemptState.FAILED
               || age >= 2) {
               this.clearGrimPlacementAttempt();
            }
         }
      }
   }

   private void confirmTellyPlacement() {
      if (this.tellyPlacementQueued && this.tellyQueuedBlock != null) {
         this.tellyPlacementQueued = false;
         BlockPos placed = this.tellyQueuedBlock;
         this.tellyQueuedBlock = null;
         boolean turnReserve = this.tellyTurnReserveQueued && this.tellyTurnReserveCell != null && this.tellyTurnReserveCell.equals(placed);
         this.tellyTurnReserveQueued = false;
         if (!this.isTellySupport(MC.level.getBlockState(placed), placed)) {
            if (!turnReserve) {
               this.tellyFailedClicks++;
            }
         } else if (turnReserve) {
            this.tellyFailedClicks = 0;
            this.tellyTarget = null;
            this.clearTellyTurnReserve();
         } else {
            boolean raised = this.tellyTarget != null && this.tellyTarget.target().placedBlock().equals(placed) && this.tellyTarget.raised();
            this.tellyLastBridge = placed.immutable();
            if (raised) {
               this.tellyRaisedBlockPlaced = true;
               this.tellyRaisedCell = placed.immutable();
            } else {
               this.tellyFlatPlacements++;
            }

            this.tellyFailedClicks = 0;
            this.tellyTarget = null;
         }
      }
   }

   private void finishTellyCycleOnGround(BlockPos under) {
      this.tellyPhase = ScaffoldModule.TellyPhase.FORWARD_DWELL;
      this.tellyMotion = ScaffoldModule.TellyMotion.FORWARD;
      this.clearTellyAirCorrection();
      this.tellyAirTicks = 0;
      this.tellyFlatPlacements = 0;
      this.tellyFailedClicks = 0;
      this.tellyForwardDwellTicks = 0;
      this.tellyCycleRises = false;
      this.tellyRaisedBlockPlaced = false;
      this.tellyAimCommitted = false;
      this.tellyRaisedCell = null;
      this.tellyPlacementQueued = false;
      this.tellyWalkOffCatch = false;
      this.tellyWalkOffGraceTicks = 0;
      this.tellyQueuedBlock = null;
      this.tellyTarget = null;
      this.tellyReturnCompleted = true;
      this.tellyLastBridge = under.immutable();
      this.tellyBridgeY = under.getY();
   }

   private boolean atTellyEdge(LocalPlayer player, BlockPos under) {
      Direction direction = this.tellyForwardDirection();
      BlockPos ahead = under.relative(direction);
      if (this.isTellySupport(MC.level.getBlockState(ahead), ahead)) {
         return false;
      } else {
         double remaining = switch (direction) {
            case EAST -> under.getX() + 1.0 - player.getX();
            case WEST -> player.getX() - under.getX();
            case SOUTH -> under.getZ() + 1.0 - player.getZ();
            case NORTH -> player.getZ() - under.getZ();
            default -> Double.POSITIVE_INFINITY;
         };
         Vec3 forward = this.tellyForwardVector();
         Vec3 velocity = player.getDeltaMovement();
         double forwardSpeed = Math.max(0.0, velocity.x * forward.x + velocity.z * forward.z);
         return shouldLaunchTelly(remaining, forwardSpeed);
      }
   }

   static boolean shouldLaunchTelly(double supportRemaining, double forwardSpeed) {
      return supportRemaining <= tellyLaunchPoint(forwardSpeed);
   }

   private static double tellyLaunchPoint(double forwardSpeed) {
      return Mth.clamp(0.52 + Math.max(0.0, forwardSpeed) * 0.12, 0.52, 0.64);
   }

   private BlockPos solidBlockUnder(LocalPlayer player) {
      AABB box = player.getBoundingBox();
      int y = Mth.floor(player.getY() - 0.08);
      BlockPos best = null;
      double bestDistance = Double.POSITIVE_INFINITY;
      int minX = Mth.floor(box.minX + 0.04);
      int maxX = Mth.floor(box.maxX - 0.04);
      int minZ = Mth.floor(box.minZ + 0.04);
      int maxZ = Mth.floor(box.maxZ - 0.04);

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (this.isTellySupport(MC.level.getBlockState(pos), pos)) {
               double distance = Vec3.atCenterOf(pos).distanceToSqr(player.position());
               if (distance < bestDistance) {
                  bestDistance = distance;
                  best = pos.immutable();
               }
            }
         }
      }

      return best;
   }

   private ScaffoldModule.TellyPlacement nextFlatTellyPlacement(LocalPlayer player, ItemStack stack) {
      if (this.tellyLastBridge != null && this.tellyLineOrigin != null && this.isValidBlock(stack)) {
         BlockPos support = this.tellySolidChainRoot();
         if (support == null) {
            return null;
         } else {
            Direction direction = this.tellyForwardDirection();

            for (int i = 0; i < 8; i++) {
               BlockPos next = support.relative(direction);
               if (MC.level.isOutsideBuildHeight(next)) {
                  return null;
               }

               BlockState state = MC.level.getBlockState(next);
               if (!this.isTellySupport(state, next)) {
                  if (!state.isAir() && !state.canBeReplaced()) {
                     return null;
                  }

                  Vec3 aim = this.tellySideAim(support, direction);
                  ScaffoldModule.PlacementTarget target = new ScaffoldModule.PlacementTarget(
                     support.immutable(),
                     next.immutable(),
                     direction,
                     new BlockHitResult(aim, direction, support, false),
                     RiptideRotationUtil.lookingAt(aim, player.getEyePosition()),
                     support.getY()
                  );
                  this.tellyLastBridge = support.immutable();
                  return new ScaffoldModule.TellyPlacement(target, false);
               }

               support = next;
            }

            return null;
         }
      } else {
         return null;
      }
   }

   private ScaffoldModule.TellyPlacement raisedTellyPlacement(LocalPlayer player, ItemStack stack, BlockPos support) {
      if (support != null && this.isValidBlock(stack)) {
         BlockPos raised = support.above();
         if (MC.level.isOutsideBuildHeight(raised)) {
            return null;
         } else {
            BlockState state = MC.level.getBlockState(raised);
            if (!state.isAir() && !state.canBeReplaced()) {
               return null;
            } else {
               Vec3 forward = this.tellyForwardVector();
               double cross = Math.sin(this.tellyCycleSerial * 1.731) * 0.11;
               Vec3 lateral = new Vec3(-forward.z, 0.0, forward.x);
               AABB box = grimSupportBox(support);
               Vec3 aim = grimFaceCentre(box, Direction.UP).add(lateral.scale(cross));
               ScaffoldModule.PlacementTarget target = new ScaffoldModule.PlacementTarget(
                  support.immutable(),
                  raised.immutable(),
                  Direction.UP,
                  new BlockHitResult(aim, Direction.UP, support, false),
                  RiptideRotationUtil.lookingAt(aim, player.getEyePosition()),
                  box.maxY - 0.1 * box.getYsize()
               );
               return new ScaffoldModule.TellyPlacement(target, true);
            }
         }
      } else {
         return null;
      }
   }

   private Vec3 tellySideAim(BlockPos support, Direction direction) {
      double faceY = Mth.clamp(0.78 - this.tellyFlatPlacements * 0.11, 0.28, 0.78);
      AABB box = grimSupportBox(support);
      Vec3 centre = grimFaceCentre(box, direction);
      return new Vec3(centre.x, box.minY + faceY * box.getYsize(), centre.z);
   }

   private boolean tryTellyTurnReservePlacement(LocalPlayer player) {
      BlockPos support = this.tellyTurnReserveSupport;
      BlockPos cell = this.tellyTurnReserveCell;
      Direction direction = tellyTurnReserveDirection(support, cell);
      if (support != null && cell != null && direction != null) {
         if (this.isTellySupport(MC.level.getBlockState(cell), cell)) {
            if (this.tellyTarget != null && cell.equals(this.tellyTarget.target().placedBlock())) {
               this.tellyTarget = null;
            }

            this.clearTellyTurnReserve();
            return false;
         } else if (!this.isTellySupport(MC.level.getBlockState(support), support)) {
            this.clearTellyTurnReserve();
            return false;
         } else {
            BlockState targetState = MC.level.getBlockState(cell);
            if (!targetState.isAir() && !targetState.canBeReplaced()) {
               this.clearTellyTurnReserve();
               return false;
            } else if (!this.tellyPlacementQueued && this.tellyClickCooldown <= 0) {
               this.selectionPending = false;
               InteractionHand hand = this.ensurePlacementHand();
               if (hand != null) {
                  this.refreshSelectionReset();
                  ItemStack held = player.getItemInHand(hand);
                  if (!this.isValidBlock(held)) {
                     return false;
                  } else {
                     Vec3 aim = this.tellySideAim(support, direction);
                     ScaffoldModule.PlacementTarget target = new ScaffoldModule.PlacementTarget(
                        support,
                        cell,
                        direction,
                        new BlockHitResult(aim, direction, support, false),
                        RiptideRotationUtil.lookingAt(aim, player.getEyePosition()),
                        support.getY()
                     );
                     ScaffoldModule.TellyPlacement placement = new ScaffoldModule.TellyPlacement(target, false);
                     this.tellyTarget = placement;
                     ScaffoldModule.TellyPlacement live = this.liveTellyPlacement(player, placement);
                     if (live == null) {
                        return true;
                     } else if (ModuleRegistry.shouldCancelUseExcept(live.target().hit(), hand, this.id())) {
                        return false;
                     } else {
                        if (this.commitTellyPlacement(live, hand, held, cell)) {
                           this.tellyTurnReserveQueued = true;
                        }

                        return true;
                     }
                  }
               } else if (this.selectionPending) {
                  this.refreshSelectionReset();
                  return true;
               } else {
                  return false;
               }
            } else {
               return true;
            }
         }
      } else {
         return false;
      }
   }

   static Direction tellyTurnReserveDirection(BlockPos support, BlockPos cell) {
      if (support != null && cell != null && support.getY() == cell.getY()) {
         for (Direction direction : Plane.HORIZONTAL) {
            if (support.relative(direction).equals(cell)) {
               return direction;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   static boolean isTellyTurnReserveDirection(BlockPos support, BlockPos cell, Direction overhang) {
      return overhang != null && overhang == tellyTurnReserveDirection(support, cell);
   }

   static boolean cellBlocksPlayer(BlockPos cell, AABB playerBox) {
      return cell != null && playerBox != null && new AABB(cell).intersects(playerBox);
   }

   private boolean commitTellyPlacement(ScaffoldModule.TellyPlacement live, InteractionHand hand, ItemStack held, BlockPos queuedCell) {
      return this.commitTellyPlacement(live, hand, held, queuedCell, false);
   }

   private boolean commitTellyPlacement(ScaffoldModule.TellyPlacement live, InteractionHand hand, ItemStack held, BlockPos queuedCell, boolean lastChance) {
      if (cellBlocksPlayer(live.target().placedBlock(), MC.player.getBoundingBox())) {
         return false;
      } else {
         ScaffoldModule.PlacementTarget aimed = this.tellyStreamAimedTarget(live.target());
         if (aimed == null) {
            aimed = this.tellyMouseBurstTarget(live.target(), lastChance ? 179.5F : 95.0F);
         }

         if (aimed == null) {
            return false;
         } else {
            this.adoptTellyPlacementRotation(aimed.rotation());
            if (!this.armTellyRealClick(aimed, hand)) {
               return false;
            } else {
               this.tellyTarget = live;
               this.tellyPlacementQueued = true;
               this.tellyQueuedBlock = queuedCell != null ? queuedCell : aimed.placedBlock().immutable();
               this.tellyClickCooldown = 1;
               this.traceTelly("PLACE");
               return true;
            }
         }
      }
   }

   private boolean armTellyRealClick(ScaffoldModule.PlacementTarget aimed, InteractionHand hand) {
      if (MC.player != null && aimed != null && hand != null) {
         int tick = RiptideSharedState.get().getClientTickCounter();
         if (tick == this.lastGrimPlacementTick || this.grimAttemptBlocksRearm()) {
            return false;
         } else if (RiptideBlinkManager.holdsActionsWithoutMovement()) {
            return false;
         } else {
            this.grimRealPendingTarget = aimed;
            this.grimRealPendingLine = this.currentMovementLine;
            this.grimRealPendingFallOff = null;
            this.grimRealQueuedTick = tick;
            this.grimAttemptState = ScaffoldModule.GrimPlacementAttemptState.ARMED;
            this.grimNextAttemptGeneration = grimNextAttemptGeneration(this.grimNextAttemptGeneration);
            this.grimAttemptGeneration = this.grimNextAttemptGeneration;
            this.grimAttemptHand = hand;
            this.grimAttemptBuildsPlannedCell = this.grimHitBuildsPlannedCell(aimed.hit(), aimed);
            this.grimAttemptSubmittedCount = 0;
            this.grimAttemptDuplicateSubmitted = false;
            this.grimAttemptWriteCount = 0;
            this.grimCommittedClickRotation = aimed.rotation();
            this.grimCommittedPreviousRotation = this.grimSilentRotation;
            this.grimAttemptSequence = -1;
            this.grimAttemptResultSeen = false;
            this.grimAttemptResultConsumed = false;
            this.grimAttemptPaceBooked = false;
            this.grimAttemptResult = "queued";
            this.lastGrimPlacementTick = tick;
            this.grimPaceQueuedNanos = Long.MIN_VALUE;
            MC.hitResult = aimed.hit();
            RiptideInputClicker.queueScaffoldUseClick(this.grimAttemptGeneration);
            ((RiptideMinecraftAccessor)MC).riptide$setRightClickDelay(0);
            return true;
         }
      } else {
         return false;
      }
   }

   private void cancelTellyRealClick() {
      if (this.grimAttemptState != ScaffoldModule.GrimPlacementAttemptState.IDLE || this.grimRealPendingTarget != null) {
         RiptideInputClicker.cancelScaffoldUseClick();
         this.clearGrimPlacementAttempt();
      }
   }

   private void refreshTellyRealClickHit() {
      if (this.grimRealQueuedTick == RiptideSharedState.get().getClientTickCounter()
         && this.grimRealPendingTarget != null
         && this.grimAttemptState == ScaffoldModule.GrimPlacementAttemptState.ARMED) {
         MC.hitResult = this.grimRealPendingTarget.hit();
      }
   }

   private ScaffoldModule.TellyPlacement liveTellyPlacement(LocalPlayer player, ScaffoldModule.TellyPlacement placement) {
      ScaffoldModule.PlacementTarget target = placement.target();
      Vec3 eye = player.getEyePosition(1.0F);
      if (target.face().getAxis().isVertical()) {
         if (!tellyRiseCellClear(player.getBoundingBox(), player.getDeltaMovement(), target.placedBlock())) {
            return null;
         } else {
            RiptideRotationUtil.Rotation rotation = RiptideRotationUtil.lookingAt(target.hit().getLocation(), eye);
            BlockHitResult ray = this.raytrace(rotation, player.blockInteractionRange());
            return ray != null && ray.getBlockPos().equals(target.supportBlock()) && ray.getDirection() == target.face()
               ? new ScaffoldModule.TellyPlacement(
                  new ScaffoldModule.PlacementTarget(target.supportBlock(), target.placedBlock(), target.face(), ray, rotation, target.minPlacementY()),
                  placement.raised()
               )
               : null;
         }
      } else {
         BlockState supportState = MC.level.getBlockState(target.supportBlock());
         VoxelShape shape = supportState.getShape(MC.level, target.supportBlock(), CollisionContext.of(player));
         if (shape.isEmpty()) {
            return null;
         } else {
            Vec3 normal = new Vec3(target.face().getStepX(), target.face().getStepY(), target.face().getStepZ());
            Vec3 left = this.tellyLeftVector();
            double desiredY = Mth.clamp(
               target.supportBlock().getY() + 0.78 - this.tellyFlatPlacements * 0.11 - Math.max(0.0, this.tellyTakeoffY - player.getY()) * 0.18,
               target.supportBlock().getY() + 0.22,
               target.supportBlock().getY() + 0.8
            );
            double reach = player.blockInteractionRange();
            RiptideRotationUtil.Rotation current = this.serverRotation();
            ScaffoldModule.TellyFaceSample[] samples = new ScaffoldModule.TellyFaceSample[TELLY_FACE_OFFSETS.length];
            double[] costs = new double[TELLY_FACE_OFFSETS.length];
            Arrays.fill(costs, Double.POSITIVE_INFINITY);
            Vec3 blockOffset = new Vec3(target.supportBlock().getX(), target.supportBlock().getY(), target.supportBlock().getZ());

            for (AABB localBox : shape.toAabbs()) {
               ScaffoldModule.FaceRect face = ScaffoldModule.FaceRect.fromBox(localBox, target.face()).trim(0.12).offset(blockOffset);
               if (!(face.area() <= 1.0E-9) && !(eye.subtract(face.center()).dot(normal) <= 0.0125)) {
                  for (int offsetIndex = 0; offsetIndex < TELLY_FACE_OFFSETS.length; offsetIndex++) {
                     double offset = TELLY_FACE_OFFSETS[offsetIndex];
                     Vec3 center = face.center().add(left.scale(offset));
                     Vec3 point = new Vec3(
                        Mth.clamp(center.x, face.from().x, face.to().x),
                        Mth.clamp(desiredY, face.from().y, face.to().y),
                        Mth.clamp(center.z, face.from().z, face.to().z)
                     );
                     if (!(eye.distanceToSqr(point) > reach * reach + 1.0E-7)) {
                        RiptideRotationUtil.Rotation rotation = RiptideRotationUtil.lookingAt(point, eye);
                        BlockHitResult ray = this.raytrace(rotation, reach);
                        if (ray != null && ray.getBlockPos().equals(target.supportBlock()) && ray.getDirection() == target.face()) {
                           double cost = rotationAngle(current, rotation) + Math.abs(offset) * 4.0 + eye.distanceTo(point) * 0.015;
                           if (cost < costs[offsetIndex]) {
                              costs[offsetIndex] = cost;
                              samples[offsetIndex] = new ScaffoldModule.TellyFaceSample(face, point, rotation, ray, offsetIndex);
                           }
                        }
                     }
                  }
               }
            }

            int selectedOffset = selectTellyFaceOffset(this.tellyFaceOffsetIndex, costs);
            ScaffoldModule.TellyFaceSample best = selectedOffset < 0 ? null : samples[selectedOffset];
            if (best == null) {
               return null;
            } else {
               this.tellyFaceOffsetIndex = selectedOffset;
               ScaffoldModule.PlacementTarget live = new ScaffoldModule.PlacementTarget(
                  target.supportBlock(), target.placedBlock(), target.face(), best.verifiedHit(), best.rotation(), best.worldFace().from().y
               );
               return new ScaffoldModule.TellyPlacement(live, placement.raised());
            }
         }
      }
   }

   static int selectTellyFaceOffset(int lockedIndex, double[] costs) {
      if (costs != null && costs.length != 0) {
         if (lockedIndex >= 0 && lockedIndex < costs.length && Double.isFinite(costs[lockedIndex])) {
            return lockedIndex;
         } else {
            int best = -1;
            double bestCost = Double.POSITIVE_INFINITY;

            for (int index = 0; index < costs.length; index++) {
               double cost = costs[index];
               if (Double.isFinite(cost) && cost < bestCost) {
                  best = index;
                  bestCost = cost;
               }
            }

            return best;
         }
      } else {
         return -1;
      }
   }

   private ScaffoldModule.PlacementTarget tellyStreamAimedTarget(ScaffoldModule.PlacementTarget target) {
      if (this.tellySmoothedRotation == null) {
         return null;
      } else {
         BlockHitResult ray = this.raytrace(this.tellySmoothedRotation, MC.player.blockInteractionRange());
         return ray != null && ray.getBlockPos().equals(target.supportBlock()) && ray.getDirection() == target.face()
            ? new ScaffoldModule.PlacementTarget(
               target.supportBlock(), target.placedBlock(), target.face(), ray, this.tellySmoothedRotation, target.minPlacementY()
            )
            : null;
      }
   }

   private ScaffoldModule.PlacementTarget tellyMouseBurstTarget(ScaffoldModule.PlacementTarget live) {
      return this.tellyMouseBurstTarget(live, 95.0F);
   }

   private ScaffoldModule.PlacementTarget tellyMouseBurstTarget(ScaffoldModule.PlacementTarget live, float cap) {
      RiptideRotationUtil.Rotation from = this.serverRotation();
      RiptideRotationUtil.Rotation stepped = tellyMouseBurstRotation(from, live.rotation(), cap, cap, RiptideRotationUtil.sensitivityGcd());
      BlockHitResult ray = this.raytrace(stepped, MC.player.blockInteractionRange());
      if (ray != null && ray.getBlockPos().equals(live.supportBlock()) && ray.getDirection() == live.face()) {
         return new ScaffoldModule.PlacementTarget(live.supportBlock(), live.placedBlock(), live.face(), ray, stepped, live.minPlacementY());
      } else {
         double gcd = normalizedTellyMouseGcd(RiptideRotationUtil.sensitivityGcd());

         for (int radius = 1; radius <= 3; radius++) {
            for (int yawCounts = -radius; yawCounts <= radius; yawCounts++) {
               int pitchCounts = radius - Math.abs(yawCounts);
               ScaffoldModule.PlacementTarget adjusted = this.verifiedTellyMouseTarget(live, from, stepped, yawCounts, pitchCounts, gcd, cap);
               if (adjusted != null) {
                  return adjusted;
               }

               if (pitchCounts != 0) {
                  adjusted = this.verifiedTellyMouseTarget(live, from, stepped, yawCounts, -pitchCounts, gcd, cap);
                  if (adjusted != null) {
                     return adjusted;
                  }
               }
            }
         }

         return null;
      }
   }

   private ScaffoldModule.PlacementTarget verifiedTellyMouseTarget(
      ScaffoldModule.PlacementTarget live,
      RiptideRotationUtil.Rotation from,
      RiptideRotationUtil.Rotation base,
      int yawCounts,
      int pitchCounts,
      double gcd,
      float cap
   ) {
      RiptideRotationUtil.Rotation candidate = new RiptideRotationUtil.Rotation(
         Mth.wrapDegrees(base.yaw() + (float)(yawCounts * gcd)), Mth.clamp(base.pitch() + (float)(pitchCounts * gcd), -89.9F, 89.9F)
      );
      float yawDelta = Math.abs(RiptideRotationUtil.angleDifference(candidate.yaw(), from.yaw()));
      float pitchDelta = Math.abs(candidate.pitch() - from.pitch());
      double countSlack = gcd * 0.501;
      if (!(yawDelta > cap + countSlack) && !(pitchDelta > cap + countSlack)) {
         BlockHitResult ray = this.raytrace(candidate, MC.player.blockInteractionRange());
         return ray != null && ray.getBlockPos().equals(live.supportBlock()) && ray.getDirection() == live.face()
            ? new ScaffoldModule.PlacementTarget(live.supportBlock(), live.placedBlock(), live.face(), ray, candidate, live.minPlacementY())
            : null;
      } else {
         return null;
      }
   }

   private boolean tryTellyLipSecurePlacement(LocalPlayer player) {
      if (this.tellyPlacementQueued) {
         return true;
      } else if (this.tellyClickCooldown > 0) {
         return true;
      } else {
         this.selectionPending = false;
         InteractionHand hand = this.ensurePlacementHand();
         if (hand == null) {
            if (this.selectionPending) {
               this.refreshSelectionReset();
               return true;
            } else {
               return false;
            }
         } else {
            this.refreshSelectionReset();
            ItemStack held = player.getItemInHand(hand);
            if (!this.isValidBlock(held)) {
               return false;
            } else {
               if (this.tellyTarget == null) {
                  this.tellyTarget = this.nextFlatTellyPlacement(player, held);
               }

               if (this.tellyTarget == null) {
                  return false;
               } else {
                  ScaffoldModule.TellyPlacement live = this.liveTellyPlacement(player, this.tellyTarget);
                  if (live == null) {
                     return false;
                  } else if (ModuleRegistry.shouldCancelUseExcept(live.target().hit(), hand, this.id())) {
                     return false;
                  } else {
                     this.commitTellyPlacement(live, hand, held, null);
                     return true;
                  }
               }
            }
         }
      }
   }

   private boolean tellyRiseOvershootCoast(LocalPlayer player) {
      if (this.tellyRaisedCell == null) {
         return false;
      } else {
         double feetY = this.tellyRaisedCell.getY() + 1.0;
         Vec3 projected = projectTellyLandingWithInput(player.position(), player.getDeltaMovement(), feetY, this.tellyEffectiveForward(player));
         Vec3 forward = this.tellyForwardVector();
         double landingProgress = projected.subtract(player.position()).dot(forward);
         double farEdge = Vec3.atCenterOf(this.tellyRaisedCell).subtract(player.position()).dot(forward) + 0.5 - 0.12;
         return landingProgress > farEdge;
      }
   }

   private boolean tellyRiseStillPossible(LocalPlayer player) {
      return this.tellyCycleRises && !this.tellyRaisedBlockPlaced ? player.getDeltaMovement().y > 0.0 || player.getY() > this.tellyBridgeY + 2.0 : false;
   }

   private BlockPos tellyRiseSupportCell(LocalPlayer player) {
      if (this.tellyLastBridge == null) {
         return null;
      } else {
         double feetY = this.tellyBridgeY + 2.0;
         Vec3 projected = projectTellyLandingWithInput(player.position(), player.getDeltaMovement(), feetY, this.tellyEffectiveForward(player));
         BlockPos cell = this.tellyLaneCell(BlockPos.containing(projected.x, this.tellyBridgeY, projected.z));
         Direction back = this.tellyForwardDirection().getOpposite();

         for (int step = 0; step <= 1; step++) {
            BlockPos candidate = step == 0 ? cell : cell.relative(back, step);
            if (!MC.level.isOutsideBuildHeight(candidate) && this.isTellySupport(MC.level.getBlockState(candidate), candidate)) {
               BlockState above = MC.level.getBlockState(candidate.above());
               if (above.isAir() || above.canBeReplaced()) {
                  return candidate;
               }
            }
         }

         return null;
      }
   }

   private Vec3 tellyEffectiveForward(LocalPlayer player) {
      Vec3 velocity = player.getDeltaMovement();
      double hx = velocity.x;
      double hz = velocity.z;
      double length = Math.sqrt(hx * hx + hz * hz);
      return length < 0.05 ? this.tellyForwardVector() : new Vec3(hx / length, 0.0, hz / length);
   }

   private boolean isTellyLandingSupported(LocalPlayer player, int blockY) {
      double feetY = blockY + 1.0;
      Vec3 projected = projectTellyLandingWithInput(player.position(), player.getDeltaMovement(), feetY, this.tellyEffectiveForward(player));
      return this.tellyLandingSupportedAt(player, blockY, projected, feetY);
   }

   private boolean tellyLandingRunwayCovered(LocalPlayer player, int blockY, int distance) {
      double feetY = blockY + 1.0;
      Vec3 projected = projectTellyLandingWithInput(player.position(), player.getDeltaMovement(), feetY, this.tellyEffectiveForward(player));
      BlockPos runway = this.tellyLaneCell(BlockPos.containing(projected.x, blockY, projected.z)).relative(this.tellyForwardDirection(), Math.max(1, distance));
      return this.isTellySupport(MC.level.getBlockState(runway), runway);
   }

   static boolean tellyChainCovered(boolean landingSecured, boolean runwayCovered, boolean turnPending, boolean turnReserveCovered) {
      return landingSecured && runwayCovered && (!turnPending || turnReserveCovered);
   }

   private boolean tellyLandingSupportedAt(LocalPlayer player, int blockY, Vec3 projected, double feetY) {
      AABB moved = player.getBoundingBox().move(projected.x - player.getX(), feetY - player.getY(), projected.z - player.getZ());
      int minX = Mth.floor(moved.minX + 1.0E-4);
      int maxX = Mth.floor(moved.maxX - 1.0E-4);
      int minZ = Mth.floor(moved.minZ + 1.0E-4);
      int maxZ = Mth.floor(moved.maxZ - 1.0E-4);

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            BlockPos pos = new BlockPos(x, blockY, z);
            if (this.isTellySupport(MC.level.getBlockState(pos), pos) && tellyFootprintOverlaps(moved, pos)) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean wouldTellyBlockCatch(LocalPlayer player, BlockPos block) {
      if (block == null) {
         return false;
      } else {
         double feetY = block.getY() + 1.0;
         Vec3 projected = projectTellyLandingWithInput(player.position(), player.getDeltaMovement(), feetY, this.tellyForwardVector());
         AABB moved = player.getBoundingBox().move(projected.x - player.getX(), feetY - player.getY(), projected.z - player.getZ());
         return tellyFootprintOverlaps(moved, block);
      }
   }

   static boolean tellyFootprintOverlaps(AABB footprint, BlockPos block) {
      return tellyFootprintOverlaps(footprint, block, 0.12);
   }

   static boolean tellyFootprintOverlaps(AABB footprint, BlockPos block, double requiredOverlap) {
      if (footprint != null && block != null) {
         double overlapX = Math.min(footprint.maxX, block.getX() + 1.0) - Math.max(footprint.minX, (double)block.getX());
         double overlapZ = Math.min(footprint.maxZ, block.getZ() + 1.0) - Math.max(footprint.minZ, (double)block.getZ());
         return overlapX >= requiredOverlap && overlapZ >= requiredOverlap;
      } else {
         return false;
      }
   }

   static Vec3 projectTellyLanding(Vec3 position, Vec3 velocity, double feetY) {
      Vec3 projected = position;
      Vec3 motion = velocity;

      for (int tick = 0; tick < 14; tick++) {
         projected = projected.add(motion);
         if (motion.y <= 0.0 && projected.y <= feetY + 0.08) {
            return new Vec3(projected.x, feetY, projected.z);
         }

         motion = new Vec3(motion.x * 0.91, (motion.y - 0.08) * 0.98, motion.z * 0.91);
      }

      return new Vec3(projected.x, feetY, projected.z);
   }

   static int tellyTicksUntilCatch(Vec3 position, Vec3 velocity, double feetY) {
      Vec3 projected = position;
      Vec3 motion = velocity;

      for (int tick = 1; tick <= 14; tick++) {
         projected = projected.add(motion);
         if (motion.y <= 0.0 && projected.y <= feetY + 0.08) {
            return tick;
         }

         motion = new Vec3(motion.x * 0.91, (motion.y - 0.08) * 0.98, motion.z * 0.91);
      }

      return 14;
   }

   static boolean tellyLateFlickBudgetAllows(int ticksLeft, int blocksNeeded, double nextTickFaceDistance, double reach) {
      return nextTickFaceDistance > reach - 0.3 ? false : ticksLeft > blocksNeeded + 1;
   }

   static Vec3 projectTellyLandingWithInput(Vec3 position, Vec3 velocity, double feetY, Vec3 forward) {
      Vec3 projected = position;
      Vec3 motion = velocity;
      Vec3 airControl = forward != null && !(forward.horizontalDistanceSqr() <= 1.0E-10)
         ? new Vec3(forward.x, 0.0, forward.z).normalize().scale(0.0196)
         : Vec3.ZERO;

      for (int tick = 0; tick < 14; tick++) {
         motion = motion.add(airControl);
         projected = projected.add(motion);
         if (motion.y <= 0.0 && projected.y <= feetY + 0.08) {
            return new Vec3(projected.x, feetY, projected.z);
         }

         motion = new Vec3(motion.x * 0.91, (motion.y - 0.08) * 0.98, motion.z * 0.91);
      }

      return new Vec3(projected.x, feetY, projected.z);
   }

   private Vec3 predictedTellyLaunchVelocity(LocalPlayer player) {
      double groundAcceleration = Math.max(0.0, (double)player.getSpeed()) * 0.98;
      return predictedTellyGroundLaunch(player.getDeltaMovement(), this.tellyAnchorYaw, groundAcceleration, this.willTellySprintOnLaunch(player));
   }

   private boolean willTellySprintOnLaunch(LocalPlayer player) {
      return player.isSprinting() || player.canSprint() && !player.isMovingSlowly() && !player.isUsingItem() && !player.isFallFlying();
   }

   static Vec3 predictedTellyGroundLaunch(Vec3 velocity, float visibleYawDegrees, double groundAcceleration, boolean sprinting) {
      if (velocity == null) {
         velocity = Vec3.ZERO;
      }

      float visibleYaw = visibleYawDegrees * (float) (Math.PI / 180.0);
      Vec3 jumpDirection = new Vec3(-Mth.sin(visibleYaw), 0.0, Mth.cos(visibleYaw));
      double sprintJumpBoost = sprinting ? 0.2 : 0.0;
      double visibleForwardImpulse = sprintJumpBoost + groundAcceleration;
      return new Vec3(velocity.x + jumpDirection.x * visibleForwardImpulse, Math.max(velocity.y, 0.42), velocity.z + jumpDirection.z * visibleForwardImpulse);
   }

   private boolean isTellyLaunchCatchable(LocalPlayer player, BlockPos support) {
      if (player != null && support != null && MC.level != null) {
         Vec3 launchVelocity = this.predictedTellyLaunchVelocity(player);
         double launchDrag = MC.level.getBlockState(support).getBlock().getFriction() * 0.91;
         Vec3 forward = this.tellyForwardVector();
         Vec3 landing = projectTellyLaunchLanding(player.position(), launchVelocity, player.getY(), forward, launchDrag);
         Direction direction = this.tellyForwardDirection();
         BlockPos catchBlock = BlockPos.containing(landing.x, support.getY(), landing.z);
         if (this.isTellySupport(MC.level.getBlockState(catchBlock), catchBlock)) {
            return true;
         } else {
            int steps = (catchBlock.getX() - support.getX()) * direction.getStepX() + (catchBlock.getZ() - support.getZ()) * direction.getStepZ();
            if (steps >= 1 && steps <= 6) {
               for (int step = 1; step <= steps; step++) {
                  BlockPos cell = support.relative(direction, step);
                  if (MC.level.isOutsideBuildHeight(cell)) {
                     return false;
                  }

                  BlockState state = MC.level.getBlockState(cell);
                  if (!this.isTellySupport(state, cell) && !state.isAir() && !state.canBeReplaced()) {
                     return false;
                  }
               }

               double feetY = support.getY() + 1.0;
               AABB landingBox = player.getBoundingBox().move(landing.x - player.getX(), feetY - player.getY(), landing.z - player.getZ());
               return tellyFootprintOverlaps(landingBox, catchBlock, 0.05);
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private boolean tellyEarlyLaunchAllowed(LocalPlayer player, BlockPos under, ScaffoldModule.TellyPlacement first) {
      if (player == null || under == null || first == null) {
         return false;
      } else if (this.tellyStopRequested || this.tellyTurnIntentPending() || this.tellyTurnSettling) {
         return false;
      } else if (!this.tellyStreamAlignedForLaunch()) {
         return false;
      } else {
         boolean catchable = !this.tellyRiseQueued && !this.tellySpaceHeld
            ? this.isTellyLaunchCatchable(player, under)
            : this.isTellyRiseLaunchCatchable(player, under);
         if (!catchable) {
            return false;
         } else {
            return !tellyEarlyLaunchLaneClear(this.tellyLaneError(player)) ? false : tellyEarlyLaunchSteps(this.tellyProjectedLaunchSteps(player, under));
         }
      }
   }

   private double tellyLaneError(LocalPlayer player) {
      return player == null ? 0.0 : this.tellyLaneCenter - laneCoordinate(player.position(), this.tellyAnchorYaw);
   }

   static boolean tellyEarlyLaunchSteps(int steps) {
      return steps >= 2 && steps <= 4;
   }

   static boolean tellyEarlyLaunchLaneClear(double laneError) {
      return Math.abs(laneError) <= 0.08;
   }

   private int tellyProjectedLaunchSteps(LocalPlayer player, BlockPos support) {
      if (player != null && support != null && MC.level != null) {
         Vec3 launchVelocity = this.predictedTellyLaunchVelocity(player);
         double launchDrag = MC.level.getBlockState(support).getBlock().getFriction() * 0.91;
         Vec3 landing = projectTellyLaunchLanding(player.position(), launchVelocity, player.getY(), this.tellyForwardVector(), launchDrag);
         Direction direction = this.tellyForwardDirection();
         BlockPos catchBlock = BlockPos.containing(landing.x, support.getY(), landing.z);
         return (catchBlock.getX() - support.getX()) * direction.getStepX() + (catchBlock.getZ() - support.getZ()) * direction.getStepZ();
      } else {
         return -1;
      }
   }

   private boolean isTellyRiseLaunchCatchable(LocalPlayer player, BlockPos support) {
      return !this.isTellyLaunchCatchable(player, support)
         ? false
         : apexReachesHeight(player.position(), this.predictedTellyLaunchVelocity(player), support.getY() + 2.0);
   }

   private static boolean apexReachesHeight(Vec3 position, Vec3 launchVelocity, double targetFeetY) {
      double y = position.y + launchVelocity.y;
      if (y >= targetFeetY) {
         return true;
      } else {
         double motionY = (launchVelocity.y - 0.08) * 0.98;

         for (int tick = 1; tick < 14; tick++) {
            y += motionY;
            if (y >= targetFeetY) {
               return true;
            }

            if (motionY <= 0.0) {
               return false;
            }

            motionY = (motionY - 0.08) * 0.98;
         }

         return false;
      }
   }

   static boolean requiresTellyRunupRecovery(boolean projectedLandingCatchable) {
      return !projectedLandingCatchable;
   }

   static ScaffoldModule.TellyLandingTransition tellyLandingTransition(boolean imminentEdge) {
      return imminentEdge ? ScaffoldModule.TellyLandingTransition.CHAIN : ScaffoldModule.TellyLandingTransition.DWELL;
   }

   static ScaffoldModule.TellyPhase nextTellyCoveragePhase(ScaffoldModule.TellyPhase current, boolean chainCovered, boolean risePending) {
      if (current == ScaffoldModule.TellyPhase.RETURNING) {
         return ScaffoldModule.TellyPhase.RETURNING;
      } else {
         return chainCovered && !risePending ? ScaffoldModule.TellyPhase.RETURNING : current;
      }
   }

   private static int requiredTellyPlacements(Vec3 position, Vec3 velocity, double feetY, Vec3 forward, BlockPos support, double launchDrag) {
      if (position != null && velocity != null && support != null) {
         Vec3 landing = projectTellyLaunchLanding(position, velocity, feetY, forward, launchDrag);
         return requiredTellyBlocksToLanding(landing, forward, support);
      } else {
         return 1;
      }
   }

   static int requiredTellyBlocksToLanding(Vec3 landing, Vec3 forward, BlockPos support) {
      if (landing != null && forward != null && support != null) {
         int landingX = Mth.floor(landing.x);
         int landingZ = Mth.floor(landing.z);
         int gridDistance;
         if (Math.abs(forward.x) >= Math.abs(forward.z)) {
            gridDistance = (int)Math.round((landingX - support.getX()) * Math.signum(forward.x));
         } else {
            gridDistance = (int)Math.round((landingZ - support.getZ()) * Math.signum(forward.z));
         }

         return Mth.clamp(gridDistance, 1, 6);
      } else {
         return 1;
      }
   }

   static Vec3 projectTellyLaunchLanding(Vec3 position, Vec3 launchVelocity, double feetY, Vec3 forward) {
      return projectTellyLaunchLanding(position, launchVelocity, feetY, forward, 0.546);
   }

   static Vec3 projectTellyLaunchLanding(Vec3 position, Vec3 launchVelocity, double feetY, Vec3 forward, double launchDrag) {
      if (position != null && launchVelocity != null) {
         Vec3 projected = position.add(launchVelocity);
         double horizontalDrag = Mth.clamp(launchDrag, 0.0, 1.2);
         Vec3 motion = new Vec3(launchVelocity.x * horizontalDrag, (launchVelocity.y - 0.08) * 0.98, launchVelocity.z * horizontalDrag);
         Vec3 airControl = forward != null && !(forward.horizontalDistanceSqr() <= 1.0E-10)
            ? new Vec3(forward.x, 0.0, forward.z).normalize().scale(0.0196)
            : Vec3.ZERO;

         for (int tick = 1; tick < 14; tick++) {
            motion = motion.add(airControl);
            projected = projected.add(motion);
            if (motion.y <= 0.0 && projected.y <= feetY + 0.08) {
               return new Vec3(projected.x, feetY, projected.z);
            }

            motion = new Vec3(motion.x * 0.91, (motion.y - 0.08) * 0.98, motion.z * 0.91);
         }

         return new Vec3(projected.x, feetY, projected.z);
      } else {
         return position;
      }
   }

   static float snapTellyYaw(float yaw) {
      return Mth.wrapDegrees(Math.round(Mth.wrapDegrees(yaw) / 90.0F) * 90.0F);
   }

   static Direction tellyOverhangDirection(double fracX, double fracZ, boolean northVoid, boolean southVoid, boolean westVoid, boolean eastVoid) {
      double half = 0.3;
      double best = 0.05;
      Direction result = null;
      double north = half - fracZ;
      if (northVoid && north > best) {
         best = north;
         result = Direction.NORTH;
      }

      double south = fracZ + half - 1.0;
      if (southVoid && south > best) {
         best = south;
         result = Direction.SOUTH;
      }

      double west = half - fracX;
      if (westVoid && west > best) {
         best = west;
         result = Direction.WEST;
      }

      double east = fracX + half - 1.0;
      if (eastVoid && east > best) {
         result = Direction.EAST;
      }

      return result;
   }

   private Direction tellyOverhangDirection(LocalPlayer player) {
      Vec3 pos = player.position();
      BlockPos cell = BlockPos.containing(pos.x, pos.y - 0.5, pos.z);
      if (!this.isTellySupport(MC.level.getBlockState(cell), cell)) {
         for (Direction direction : Plane.HORIZONTAL) {
            BlockPos neighbor = cell.relative(direction);
            if (this.isTellySupport(MC.level.getBlockState(neighbor), neighbor)) {
               return direction.getOpposite();
            }
         }

         return null;
      } else {
         double fracX = pos.x - Math.floor(pos.x);
         double fracZ = pos.z - Math.floor(pos.z);
         return tellyOverhangDirection(
            fracX,
            fracZ,
            !this.isTellySupport(MC.level.getBlockState(cell.north()), cell.north()),
            !this.isTellySupport(MC.level.getBlockState(cell.south()), cell.south()),
            !this.isTellySupport(MC.level.getBlockState(cell.west()), cell.west()),
            !this.isTellySupport(MC.level.getBlockState(cell.east()), cell.east())
         );
      }
   }

   private BlockPos tellyLaneCell(BlockPos raw) {
      if (this.tellyLastBridge == null) {
         return raw;
      } else {
         return this.tellyForwardDirection().getAxis() == Axis.Z
            ? new BlockPos(this.tellyLastBridge.getX(), raw.getY(), raw.getZ())
            : new BlockPos(raw.getX(), raw.getY(), this.tellyLastBridge.getZ());
      }
   }

   private BlockPos tellySolidChainRoot() {
      if (this.tellyLastBridge == null) {
         return null;
      } else if (this.isTellySupport(MC.level.getBlockState(this.tellyLastBridge), this.tellyLastBridge)) {
         return this.tellyLastBridge;
      } else {
         Direction back = this.tellyForwardDirection().getOpposite();

         for (int step = 1; step <= 3; step++) {
            BlockPos candidate = this.tellyLastBridge.relative(back, step);
            if (this.isTellySupport(MC.level.getBlockState(candidate), candidate)) {
               this.tellyLastBridge = candidate.immutable();
               return this.tellyLastBridge;
            }
         }

         return null;
      }
   }

   static boolean tellyRiseCellClear(AABB currentBox, Vec3 velocity, BlockPos cell) {
      AABB cellBox = new AABB(cell);
      return !currentBox.intersects(cellBox) && !currentBox.move(-velocity.x, -velocity.y, -velocity.z).intersects(cellBox);
   }

   static boolean grimCellClearOfBody(AABB box, Vec3 velocity, BlockPos cell) {
      return grimCellClearOfBody(box, velocity, cell, false);
   }

   static boolean grimCellClearOfBody(AABB box, Vec3 velocity, BlockPos cell, boolean fallingCatch) {
      if (box != null && cell != null) {
         AABB cellBox = new AABB(cell);
         if (box.intersects(cellBox)) {
            return false;
         } else if (velocity != null && !(velocity.y >= 0.0)) {
            double feet = box.minY;
            double top = cellBox.maxY;
            if (feet <= top) {
               return true;
            } else {
               double x = 0.0;
               double z = 0.0;
               double vy = velocity.y;
               boolean crossed = false;

               for (int tick = 0; tick < 2; tick++) {
                  x += velocity.x;
                  feet += vy;
                  z += velocity.z;
                  vy = (vy - 0.08) * 0.98;
                  if (!(feet >= top)) {
                     boolean firstCross = !crossed;
                     crossed = true;
                     AABB swept = box.move(x, 0.0, z);
                     boolean over = swept.maxX > cellBox.minX && swept.minX < cellBox.maxX && swept.maxZ > cellBox.minZ && swept.minZ < cellBox.maxZ;
                     if (fallingCatch && firstCross && over) {
                        return true;
                     }

                     if (over) {
                        return false;
                     }
                  }
               }

               return true;
            }
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   static String grimCellClearReason(AABB box, Vec3 velocity, BlockPos cell, boolean fallingCatch) {
      if (box != null && cell != null) {
         AABB cellBox = new AABB(cell);
         if (box.intersects(cellBox)) {
            return "box";
         } else if (velocity != null && !(velocity.y >= 0.0)) {
            double feet = box.minY;
            double top = cellBox.maxY;
            if (feet <= top) {
               return "ok";
            } else {
               double x = 0.0;
               double z = 0.0;
               double vy = velocity.y;
               boolean crossed = false;

               for (int tick = 0; tick < 2; tick++) {
                  x += velocity.x;
                  feet += vy;
                  z += velocity.z;
                  vy = (vy - 0.08) * 0.98;
                  if (!(feet >= top)) {
                     boolean firstCross = !crossed;
                     crossed = true;
                     AABB swept = box.move(x, 0.0, z);
                     boolean over = swept.maxX > cellBox.minX && swept.minX < cellBox.maxX && swept.maxZ > cellBox.minZ && swept.minZ < cellBox.maxZ;
                     if (fallingCatch && firstCross && over) {
                        return "ok";
                     }

                     if (over) {
                        return !fallingCatch ? "strict" : "side";
                     }
                  }
               }

               return "ok";
            }
         } else {
            return "ok";
         }
      } else {
         return "ok";
      }
   }

   static RiptideRotationUtil.Rotation stepCappedRotation(
      RiptideRotationUtil.Rotation current, RiptideRotationUtil.Rotation goal, float yawCap, float pitchCap, double gcd
   ) {
      if (current == null) {
         return goal;
      } else if (goal == null) {
         return current;
      } else {
         RiptideRotationUtil.Rotation stepped = RiptideRotationUtil.towardsLinear(current, goal, yawCap, pitchCap);
         if (gcd <= 0.0) {
            return stepped;
         } else {
            float yawDiff = RiptideRotationUtil.angleDifference(stepped.yaw(), current.yaw());
            float pitchDiff = RiptideRotationUtil.angleDifference(stepped.pitch(), current.pitch());
            float yaw = current.yaw() + (float)(Math.round(yawDiff / gcd) * gcd);
            float pitch = current.pitch() + (float)(Math.round(pitchDiff / gcd) * gcd);
            return new RiptideRotationUtil.Rotation(yaw, Mth.clamp(pitch, -90.0F, 90.0F));
         }
      }
   }

   static RiptideRotationUtil.Rotation stepTellyRotation(RiptideRotationUtil.Rotation current, RiptideRotationUtil.Rotation goal, float stepCap, double gcd) {
      return stepCappedRotation(current, goal, stepCap, stepCap, gcd);
   }

   static RiptideRotationUtil.Rotation tellyMouseBurstRotation(
      RiptideRotationUtil.Rotation current, RiptideRotationUtil.Rotation goal, float yawCap, float pitchCap, double gcd
   ) {
      if (current == null) {
         return goal;
      } else if (goal == null) {
         return current;
      } else {
         double quantum = normalizedTellyMouseGcd(gcd);
         int yawCounts = cappedTellyMouseCounts(RiptideRotationUtil.angleDifference(goal.yaw(), current.yaw()), yawCap, quantum);
         int pitchCounts = cappedTellyMouseCounts(goal.pitch() - current.pitch(), pitchCap, quantum);
         return new RiptideRotationUtil.Rotation(
            Mth.wrapDegrees(current.yaw() + (float)(yawCounts * quantum)), Mth.clamp(current.pitch() + (float)(pitchCounts * quantum), -89.9F, 89.9F)
         );
      }
   }

   private static int cappedTellyMouseCounts(double delta, float cap, double gcd) {
      if (Double.isFinite(delta) && Float.isFinite(cap) && !(cap <= 0.0F)) {
         long wanted = Math.round(delta / gcd);
         long maximum = Math.max(0L, (long)Math.floor(cap / gcd + 1.0E-9));
         return (int)Math.max(-maximum, Math.min(maximum, wanted));
      } else {
         return 0;
      }
   }

   private static double normalizedTellyMouseGcd(double gcd) {
      return Double.isFinite(gcd) && gcd > 0.0 ? gcd : 0.15;
   }

   static boolean tellyTurnSettled(float smoothedYaw, float anchorYaw, double velAlongCourse, double velCrossCourse, double laneError, boolean onGround) {
      if (!onGround) {
         return false;
      } else if (Math.abs(RiptideRotationUtil.angleDifference(anchorYaw, smoothedYaw)) > 1.0F) {
         return false;
      } else if (Math.abs(laneError) > 0.45) {
         return false;
      } else {
         double speed = Math.sqrt(velAlongCourse * velAlongCourse + velCrossCourse * velCrossCourse);
         if (speed < 0.08) {
            return true;
         } else if (velAlongCourse <= 0.0) {
            return false;
         } else {
            double angle = Math.toDegrees(Math.atan2(Math.abs(velCrossCourse), velAlongCourse));
            return angle <= 25.0;
         }
      }
   }

   static float retainTellyCourseYaw(boolean latched, float retainedYaw, float measuredTravelYaw) {
      return latched && Float.isFinite(retainedYaw) ? Mth.wrapDegrees(retainedYaw) : snapTellyYaw(measuredTravelYaw);
   }

   static boolean shouldQueueTellyRise(boolean physicalForward, boolean physicalSpace, boolean physicalSpaceWasDown, boolean ownsInput) {
      return physicalForward && physicalSpace ? !physicalSpaceWasDown || !ownsInput : false;
   }

   static boolean shouldRestoreTellyCourseOnGround(ScaffoldModule.TellyPhase phase) {
      return phase != null && phase != ScaffoldModule.TellyPhase.IDLE;
   }

   private Vec3 tellyForwardVector() {
      return tellyForwardVector(this.tellyAnchorYaw);
   }

   static Vec3 tellyForwardVector(float anchorYaw) {
      float radians = anchorYaw * (float) (Math.PI / 180.0);
      return new Vec3(-Mth.sin(radians), 0.0, Mth.cos(radians));
   }

   private Vec3 tellyLeftVector() {
      Vec3 forward = this.tellyForwardVector();
      return new Vec3(forward.z, 0.0, -forward.x);
   }

   private Direction tellyForwardDirection() {
      return tellyDirectionForYaw(this.tellyAnchorYaw);
   }

   static Direction tellyDirectionForYaw(float yaw) {
      int quadrant = Math.floorMod(Math.round(yaw / 90.0F), 4);

      return switch (quadrant) {
         case 0 -> Direction.SOUTH;
         case 1 -> Direction.WEST;
         case 2 -> Direction.NORTH;
         default -> Direction.EAST;
      };
   }

   static Vec3 laneOrigin(BlockPos support, Vec3 playerPosition, float yaw) {
      boolean alongZ = Math.floorMod(Math.round(yaw / 90.0F), 2) == 0;
      return alongZ ? new Vec3(support.getX() + 0.5, playerPosition.y, playerPosition.z) : new Vec3(playerPosition.x, playerPosition.y, support.getZ() + 0.5);
   }

   private static double laneCoordinate(Vec3 position, float yaw) {
      float radians = yaw * (float) (Math.PI / 180.0);
      Vec3 left = new Vec3(Mth.cos(radians), 0.0, Mth.sin(radians));
      return position.dot(left);
   }

   private RiptideRotationUtil.Rotation tellyForwardRotation() {
      return new RiptideRotationUtil.Rotation(this.tellyCourseLookYaw(), this.tellyForwardPitch);
   }

   private RiptideRotationUtil.Rotation tellyGroundSteeringRotation() {
      return new RiptideRotationUtil.Rotation(
         Mth.wrapDegrees(this.tellyAnchorYaw + this.tellyGroundSteerOffset + this.tellyLookYawOffset), this.tellyForwardPitch
      );
   }

   private float tellyCourseLookYaw() {
      return tellyCourseLookYaw(this.tellyAnchorYaw, this.tellyLookYawOffset);
   }

   static float tellyCourseLookYaw(float anchorYaw, float lookOffset) {
      return Mth.wrapDegrees(anchorYaw + lookOffset);
   }

   private float tellyGroundStep() {
      return 130.0F;
   }

   private RiptideRotationUtil.Rotation advanceTellyRotationStream(LocalPlayer player) {
      if (this.tellyRotationHeldForPlacement) {
         this.tellyRotationHeldForPlacement = false;
         if (this.tellySmoothedRotation != null) {
            return this.tellySmoothedRotation;
         }
      }

      if (this.tellyRotationAdvancedThisTick() && this.tellySmoothedRotation != null) {
         return this.tellySmoothedRotation;
      } else {
         ScaffoldModule.TellyRotationGoal goal = this.selectTellyRotationGoal(player);
         if (goal.intent() == ScaffoldModule.TellyRotationIntent.RETURN) {
            return this.completeTellyReturn();
         } else if (usesTellyAirFlick(goal.intent())) {
            this.tellyReturnCompleted = false;
            return this.applyTellyMouseBurst(goal.rotation());
         } else if (goal.intent() == ScaffoldModule.TellyRotationIntent.FORWARD && !player.onGround()) {
            return this.completeTellyReturn();
         } else {
            return goal.intent() != ScaffoldModule.TellyRotationIntent.HOLD && player.onGround()
               ? this.stepTellyStreamOnce(player, goal.rotation(), this.tellyGroundStep(), RiptideHumanRotation.MotionProfile.TELLY_FLICK)
               : this.holdTellyRotation();
         }
      }
   }

   static boolean usesTellyAirFlick(ScaffoldModule.TellyRotationIntent intent) {
      return intent == ScaffoldModule.TellyRotationIntent.PLACEMENT;
   }

   private boolean tellyRotationAdvancedThisTick() {
      return this.tellyRotationStepTick == RiptideSharedState.get().getClientTickCounter();
   }

   private RiptideRotationUtil.Rotation holdTellyRotation() {
      if (this.tellySmoothedRotation == null) {
         this.tellySmoothedRotation = this.serverRotation();
      }

      this.tellyRotationStepTick = RiptideSharedState.get().getClientTickCounter();
      this.grimSilentRotation = this.tellySmoothedRotation;
      this.grimRotationResetTicks = 5;
      return this.tellySmoothedRotation;
   }

   private RiptideRotationUtil.Rotation stepTellyStreamOnce(
      LocalPlayer player, RiptideRotationUtil.Rotation goal, float cap, RiptideHumanRotation.MotionProfile profile
   ) {
      if (this.tellyRotationAdvancedThisTick() && this.tellySmoothedRotation != null) {
         return this.tellySmoothedRotation;
      } else {
         if (!RiptideHumanRotation.isInitialized(this.tellyStream)) {
            RiptideHumanRotation.seed(this.tellyStream, this.tellySmoothedRotation != null ? this.tellySmoothedRotation : this.serverRotation());
         }

         this.tellySmoothedRotation = RiptideHumanRotation.step(this.tellyStream, goal, cap, cap, RiptideRotationUtil.sensitivityGcd(), false, profile);
         this.tellyRotationStepTick = RiptideSharedState.get().getClientTickCounter();
         this.grimSilentRotation = this.tellySmoothedRotation;
         this.grimRotationResetTicks = 5;
         return this.tellySmoothedRotation;
      }
   }

   private RiptideRotationUtil.Rotation applyTellyMouseBurst(RiptideRotationUtil.Rotation goal) {
      return this.applyTellyMouseBurst(goal, 95.0F);
   }

   private RiptideRotationUtil.Rotation applyTellyMouseBurst(RiptideRotationUtil.Rotation goal, float cap) {
      this.tellySmoothedRotation = tellyMouseBurstRotation(this.serverRotation(), goal, cap, cap, RiptideRotationUtil.sensitivityGcd());
      RiptideHumanRotation.seed(this.tellyStream, this.tellySmoothedRotation);
      this.tellyRotationStepTick = RiptideSharedState.get().getClientTickCounter();
      this.grimSilentRotation = this.tellySmoothedRotation;
      this.grimRotationResetTicks = 5;
      return this.tellySmoothedRotation;
   }

   private void adoptTellyPlacementRotation(RiptideRotationUtil.Rotation rotation) {
      this.tellySmoothedRotation = rotation;
      RiptideHumanRotation.seed(this.tellyStream, rotation);
      this.grimSilentRotation = rotation;
      this.grimRotationResetTicks = 5;
      this.tellyRotationStepTick = RiptideSharedState.get().getClientTickCounter();
      this.tellyRotationHeldForPlacement = true;
      this.tellyAimCommitted = true;
      this.tellyReturnCompleted = false;
   }

   private ScaffoldModule.TellyRotationGoal selectTellyRotationGoal(LocalPlayer player) {
      if (player.onGround()) {
         if (this.tellyMotion == ScaffoldModule.TellyMotion.HOLD) {
            boolean turnReserveTarget = this.tellyTarget != null
               && this.tellyTurnReserveCell != null
               && this.tellyTurnReserveCell.equals(this.tellyTarget.target().placedBlock());
            ScaffoldModule.TellyPlacement heldTarget = !tellyGroundHoldUsesChainTarget(this.tellyStopRequested, this.tellyFinishing) && !turnReserveTarget
               ? null
               : this.tellyTarget;
            if (heldTarget != null) {
               return new ScaffoldModule.TellyRotationGoal(this.tellyPlacementRotationGoal(player, heldTarget), ScaffoldModule.TellyRotationIntent.PLACEMENT);
            }
         }

         return new ScaffoldModule.TellyRotationGoal(
            this.tellyGroundSteeringActive ? this.tellyGroundSteeringRotation() : this.tellyForwardRotation(), ScaffoldModule.TellyRotationIntent.FORWARD
         );
      } else if (this.tellyPhase != ScaffoldModule.TellyPhase.RETURNING && !this.tellyFinishing) {
         boolean aimingPhase = !this.tellyFinishing
            && (this.tellyPhase == ScaffoldModule.TellyPhase.LAUNCH || this.tellyPhase == ScaffoldModule.TellyPhase.AIMING);
         if (aimingPhase && this.tellyCycleRises && !this.tellyRaisedBlockPlaced) {
            ScaffoldModule.TellyPlacement rise = this.pendingTellyRiseTarget(player, this.planningStack());
            if (rise != null) {
               return new ScaffoldModule.TellyRotationGoal(
                  this.tellyPlacementRotationGoal(player, rise),
                  tellyAirRotationIntent(this.tellyPhase, true, this.tellyAimCommitted, this.tellyFinishing, this.tellyLandingImminent(player))
               );
            }
         }

         if (aimingPhase && this.tellyTarget != null && !this.tellyShouldDelayFirstClick(player)) {
            return new ScaffoldModule.TellyRotationGoal(
               this.tellyPlacementRotationGoal(player, this.tellyTarget),
               tellyAirRotationIntent(this.tellyPhase, true, this.tellyAimCommitted, this.tellyFinishing, this.tellyLandingImminent(player))
            );
         } else {
            ScaffoldModule.TellyRotationIntent gapIntent = tellyAirRotationIntent(
               this.tellyPhase, false, this.tellyAimCommitted, this.tellyFinishing, this.tellyLandingImminent(player)
            );
            return gapIntent == ScaffoldModule.TellyRotationIntent.HOLD && this.tellySmoothedRotation != null
               ? new ScaffoldModule.TellyRotationGoal(this.tellySmoothedRotation, gapIntent)
               : new ScaffoldModule.TellyRotationGoal(this.tellyForwardRotation(), ScaffoldModule.TellyRotationIntent.FORWARD);
         }
      } else {
         return new ScaffoldModule.TellyRotationGoal(
            this.tellyForwardRotation(), tellyAirRotationIntent(this.tellyPhase, false, this.tellyAimCommitted, this.tellyFinishing, true)
         );
      }
   }

   static ScaffoldModule.TellyRotationIntent tellyAirRotationIntent(
      ScaffoldModule.TellyPhase phase, boolean placementGoal, boolean aimCommitted, boolean finishing, boolean landingImminent
   ) {
      if (finishing || phase == ScaffoldModule.TellyPhase.RETURNING) {
         return ScaffoldModule.TellyRotationIntent.RETURN;
      } else if (placementGoal) {
         return ScaffoldModule.TellyRotationIntent.PLACEMENT;
      } else if (landingImminent) {
         return ScaffoldModule.TellyRotationIntent.FORWARD;
      } else {
         return !aimCommitted || phase != ScaffoldModule.TellyPhase.LAUNCH && phase != ScaffoldModule.TellyPhase.AIMING
            ? ScaffoldModule.TellyRotationIntent.FORWARD
            : ScaffoldModule.TellyRotationIntent.HOLD;
      }
   }

   private boolean tellyLandingImminent(LocalPlayer player) {
      if (player != null && !player.onGround()) {
         Vec3 velocity = player.getDeltaMovement();
         if (velocity.y >= 0.0) {
            return false;
         } else {
            double catchFeetY = this.tellyBridgeY + (this.tellyRaisedBlockPlaced ? 2.0 : 1.0);
            return tellyTicksUntilCatch(player.position(), velocity, catchFeetY) <= 2;
         }
      } else {
         return false;
      }
   }

   private ScaffoldModule.TellyPlacement pendingTellyRiseTarget(LocalPlayer player, ItemStack stack) {
      if (player != null && this.tellyLastBridge != null && this.tellyRiseStillPossible(player) && this.isValidBlock(stack)) {
         BlockPos riseCell = this.tellyRiseSupportCell(player);
         return riseCell == null ? null : this.raisedTellyPlacement(player, stack, riseCell);
      } else {
         return null;
      }
   }

   static boolean tellyGroundHoldUsesChainTarget(boolean stopRequested, boolean finishing) {
      return stopRequested || finishing;
   }

   private void continueTellyReturnAfterPlanning(LocalPlayer player) {
      if (this.tellyOwnsInput && player != null && !player.onGround() && this.tellyPhase == ScaffoldModule.TellyPhase.RETURNING) {
         this.completeTellyReturn();
      }
   }

   private RiptideRotationUtil.Rotation completeTellyReturn() {
      return this.completeTellyReturn(95.0F);
   }

   private RiptideRotationUtil.Rotation completeTellyReturn(float cap) {
      if (this.tellyReturnCompleted && this.tellyStreamAlignedForLaunch()) {
         return this.holdTellyRotation();
      } else {
         RiptideRotationUtil.Rotation returned = this.applyTellyMouseBurst(this.tellyForwardRotation(), cap);
         this.tellyReturnCompleted = true;
         this.tellyAimCommitted = false;
         return returned;
      }
   }

   private RiptideRotationUtil.Rotation tellyPlacementRotationGoal(LocalPlayer player, ScaffoldModule.TellyPlacement placement) {
      ScaffoldModule.TellyPlacement live = this.liveTellyPlacement(player, placement);
      if (live != null) {
         return live.target().rotation();
      } else {
         RiptideRotationUtil.Rotation stored = RiptideRotationUtil.lookingAt(placement.target().hit().getLocation(), player.getEyePosition(1.0F));
         return this.tellySmoothedRotation != null
               && tellyHoldsAimWhileFaceIsAhead(
                  false, this.tellyAimCommitted, player.onGround(), placement.target().face().getAxis().isVertical(), this.tellyLandingImminent(player)
               )
            ? new RiptideRotationUtil.Rotation(this.tellyHeldYawWithDither(this.tellySmoothedRotation.yaw()), stored.pitch())
            : stored;
      }
   }

   static boolean tellyHoldsAimWhileFaceIsAhead(boolean hasLiveSample, boolean aimCommitted, boolean grounded, boolean verticalFace, boolean landingImminent) {
      if (hasLiveSample || !aimCommitted || grounded) {
         return false;
      } else {
         return verticalFace ? false : !landingImminent;
      }
   }

   private float tellyHeldYawWithDither(float heldYaw) {
      double quantum = RiptideRotationUtil.sensitivityGcd();
      return !(quantum <= 0.0) && this.rotationRandom.nextInt(100) < 40 ? heldYaw + (float)(this.rotationRandom.nextBoolean() ? quantum : -quantum) : heldYaw;
   }

   private boolean tellyStreamAlignedForLaunch() {
      return this.tellySmoothedRotation != null
         && Math.abs(RiptideRotationUtil.angleDifference(this.tellyCourseLookYaw(), this.tellySmoothedRotation.yaw())) <= 2.0F;
   }

   private boolean tellyShouldDelayFirstClick(LocalPlayer player) {
      if (this.tellyAimCommitted) {
         return false;
      } else if (this.tellyTurnIntentPending()) {
         this.tellyAimCommitted = true;
         return false;
      } else if (this.tellyFlatPlacements > 0 || this.tellyWalkOffCatch || this.tellyFinishing) {
         this.tellyAimCommitted = true;
         return false;
      } else if (this.tellyTarget == null) {
         return false;
      } else {
         Vec3 velocity = player.getDeltaMovement();
         boolean risePending = this.tellyCycleRises && !this.tellyRaisedBlockPlaced;
         double catchFeetY = this.tellyBridgeY + (risePending ? 2.0 : 1.0);
         int ticksLeft = tellyTicksUntilCatch(player.position(), velocity, catchFeetY);
         Vec3 landing = projectTellyLandingWithInput(player.position(), velocity, catchFeetY, this.tellyEffectiveForward(player));
         int blocksNeeded = requiredTellyBlocksToLanding(landing, this.tellyForwardVector(), this.tellyLastBridge)
            + tellyRunwayReserveBlocks(this.tellyTurnIntentPending());
         double nextTickFaceDistance = player.getEyePosition().add(velocity).distanceTo(this.tellyTarget.target().hit().getLocation());
         boolean delay = tellyAimDelayAllowed(ticksLeft, blocksNeeded, nextTickFaceDistance, player.blockInteractionRange(), risePending);
         this.tellyAimCommitted = nextTellyAimCommitted(this.tellyAimCommitted, delay);
         return delay;
      }
   }

   static boolean nextTellyAimCommitted(boolean committed, boolean delayAllowed) {
      return committed || !delayAllowed;
   }

   static int tellyRunwayReserveBlocks(boolean turnPending) {
      return turnPending ? 2 : 1;
   }

   static boolean tellyAimDelayAllowed(int rawTicksLeft, int blocksNeeded, double nextTickFaceDistance, double reach) {
      return tellyAimDelayAllowed(rawTicksLeft, blocksNeeded, nextTickFaceDistance, reach, false);
   }

   static boolean tellyAimDelayAllowed(int rawTicksLeft, int blocksNeeded, double nextTickFaceDistance, double reach, boolean risePending) {
      int reserve = tellyAimSweepReserveTicks(blocksNeeded, risePending);
      return tellyLateFlickBudgetAllows(rawTicksLeft - reserve, blocksNeeded, nextTickFaceDistance, reach);
   }

   static int tellyAimSweepReserveTicks(int blocksNeeded, boolean risePending) {
      return 1;
   }

   private boolean tellyFlickBackForLaunch() {
      if (this.tellyStreamAlignedForLaunch()) {
         return true;
      } else {
         this.completeTellyReturn(179.5F);
         return this.tellyStreamAlignedForLaunch();
      }
   }

   static int nextTellyForwardDwellTicks(int currentTicks, boolean forwardAligned) {
      return !forwardAligned ? 0 : Math.min(0, Math.max(0, currentTicks) + 1);
   }

   static boolean tellyForwardDwellComplete(int alignedTicks) {
      return alignedTicks >= 0;
   }

   private static boolean physicallyDown(KeyMapping mapping) {
      return mapping != null && RiptideKeyMappingBridge.of(mapping).riptide$isActuallyDown();
   }

   private void releaseTellyRotationStream() {
      this.tellySmoothedRotation = null;
      if (!this.grimWindingDown) {
         if (this.grimSilentRotation != null && RiptideHumanRotation.isInitialized(this.tellyStream)) {
            this.grimWindingDown = true;
         } else if (this.grimSilentRotation == null) {
            this.grimRotationResetTicks = 0;
            RiptideHumanRotation.clear(this.tellyStream);
         }
      }
   }

   private void resetTellyState() {
      this.releaseTellyRotationStream();
      this.clearTellyAirCorrection();
      this.clearTellyLaneBias();
      this.cancelTellyRealClick();
      this.tellyHoldStrafe = ScaffoldModule.TellyStrafe.NONE;
      this.tellyPhase = ScaffoldModule.TellyPhase.IDLE;
      this.tellyMotion = ScaffoldModule.TellyMotion.RELEASED;
      this.tellyOwnsInput = false;
      this.tellyStopRequested = false;
      this.tellyJumpThisTick = false;
      this.tellySneakThisTick = false;
      this.tellyPhysicalSpaceWasDown = false;
      this.tellyRiseQueued = false;
      this.tellySpaceHeld = false;
      this.tellyFinishing = false;
      this.tellyCycleRises = false;
      this.tellyRaisedBlockPlaced = false;
      this.tellyAimCommitted = false;
      this.tellyRaisedCell = null;
      this.tellyPlacementQueued = false;
      this.tellyWalkOffCatch = false;
      this.tellyWalkOffGraceTicks = 0;
      this.tellyClickCooldown = 0;
      this.tellyAirTicks = 0;
      this.tellyFlatPlacements = 0;
      this.tellyFailedClicks = 0;
      this.tellyForwardDwellTicks = 0;
      this.tellyBridgeY = 0;
      this.tellyTakeoffY = 0.0;
      this.tellyTakeoffProgress = 0.0;
      this.tellyAnchorYaw = 0.0F;
      this.tellyLookYawOffset = 0.0F;
      this.tellyRotationStepTick = Integer.MIN_VALUE;
      this.tellyPipelineTick = Integer.MIN_VALUE;
      this.tellyForwardPitch = 0.0F;
      this.tellyLaneCenter = 0.0;
      this.tellyRecoveryTicks = 0;
      this.tellyCourseLatched = false;
      this.tellyCourseDeviationTicks = 0;
      this.tellyEdgeHoldTicks = 0;
      this.tellyGroundSteeringActive = false;
      this.tellyGroundSteerOffset = 0.0F;
      this.tellyTurnSettling = false;
      this.tellySettleHoldTicks = 0;
      this.tellySettleDwellTicks = 0;
      this.tellyRotationHeldForPlacement = false;
      this.tellyFaceOffsetIndex = -1;
      this.tellyReturnCompleted = true;
      this.tellyHoldWatchdogTicks = 0;
      this.tellyLastBridge = null;
      this.tellyQueuedBlock = null;
      this.clearTellyTurnReserve();
      this.tellyLineOrigin = null;
      this.tellyTarget = null;
   }

   private void releaseTellyControl() {
      this.releaseTellyRotationStream();
      this.clearTellyAirCorrection();
      this.clearTellyLaneBias();
      this.cancelTellyRealClick();
      this.tellyHoldStrafe = ScaffoldModule.TellyStrafe.NONE;
      this.tellyPhase = ScaffoldModule.TellyPhase.IDLE;
      this.tellyMotion = ScaffoldModule.TellyMotion.RELEASED;
      this.tellyOwnsInput = false;
      this.tellyStopRequested = false;
      this.tellyJumpThisTick = false;
      this.tellySneakThisTick = false;
      this.tellyAirTicks = 0;
      this.tellyForwardDwellTicks = 0;
      this.tellyGroundSteeringActive = false;
      this.tellyGroundSteerOffset = 0.0F;
      this.tellyWalkOffCatch = false;
      this.tellyWalkOffGraceTicks = 0;
      this.tellyRecoveryTicks = 0;
      this.tellyRiseQueued = false;
      this.tellyFinishing = false;
      this.tellyAimCommitted = false;
      this.tellyCourseDeviationTicks = 0;
      this.tellyEdgeHoldTicks = 0;
      this.tellyTurnSettling = false;
      this.tellySettleHoldTicks = 0;
      this.tellySettleDwellTicks = 0;
      this.tellyRotationHeldForPlacement = false;
      this.tellyFaceOffsetIndex = -1;
      this.tellyReturnCompleted = true;
      this.tellyRotationStepTick = Integer.MIN_VALUE;
      this.tellyPipelineTick = Integer.MIN_VALUE;
      this.tellyHoldWatchdogTicks = 0;
      this.tellyTarget = null;
      this.tellyQueuedBlock = null;
      this.tellyPlacementQueued = false;
      this.clearTellyTurnReserve();
   }

   @Override
   public void tick() {
      if (!this.isEnabled()) {
         this.advanceGrimWindDown();
      }
   }

   @Override
   public boolean ticksWhenDisabled() {
      return true;
   }

   @Override
   public boolean hasDisabledTickWork() {
      return this.grimWindingDown && this.grimSilentRotation != null;
   }

   @Override
   public boolean onPacketSend(Packet<?> packet) {
      if (packet instanceof ServerboundMovePlayerPacket movement && movement.hasRotation()) {
         RiptideRotationUtil.Rotation base = this.serverRotation();
         this.serverRotation = new RiptideRotationUtil.Rotation(movement.getYRot(base.yaw()), movement.getXRot(base.pitch()));
      }

      return false;
   }

   public static void onFinalPacketWritten(Packet<?> packet) {
      ScaffoldModule.GrimQueuedUse queued = null;
      if (packet instanceof ServerboundUseItemOnPacket) {
         synchronized (GRIM_QUEUED_USES) {
            purgeCollectedGrimQueuedUses();
            queued = GRIM_QUEUED_USES.remove(new ScaffoldModule.GrimPacketIdentity(packet));
         }
      }

      if (ModuleRegistry.get("scaffold") instanceof ScaffoldModule scaffold && scaffold.isEnabled() && scaffold.ownsRealClickPipeline()) {
         if (packet instanceof ServerboundMovePlayerPacket movement) {
            GRIM_FINAL_MOVE_WRITE.set(new ScaffoldModule.GrimFinalMoveWrite(movement.isOnGround(), movement.horizontalCollision(), movement.hasPosition()));
         } else if (packet instanceof ServerboundUseItemOnPacket use) {
            if (queued != null) {
               BlockHitResult hit = use.getHitResult();
               GRIM_FINAL_USE_WRITES.offer(
                  new ScaffoldModule.GrimFinalUseWrite(
                     use.getSequence(),
                     hit.getBlockPos().immutable(),
                     hit.getDirection(),
                     hit.getLocation(),
                     System.nanoTime(),
                     use.getHand(),
                     queued,
                     RiptideServerRotationView.snapshot()
                  )
               );
            }
         }
      }
   }

   public static void onConnectionClosed() {
      synchronized (GRIM_QUEUED_USES) {
         GRIM_QUEUED_USES.clear();

         while (GRIM_QUEUED_USE_GC.poll() != null) {
         }
      }

      GRIM_FINAL_USE_WRITES.clear();
      GRIM_FINAL_MOVE_WRITE.set(null);
   }

   private void drainGrimFinalUseWrite() {
      ScaffoldModule.GrimFinalUseWrite written;
      while ((written = GRIM_FINAL_USE_WRITES.poll()) != null) {
         this.grimOnFinalUseWritten(written);
      }
   }

   private void drainGrimFinalMoveWrite() {
      ScaffoldModule.GrimFinalMoveWrite written = GRIM_FINAL_MOVE_WRITE.getAndSet(null);
      if (written != null) {
         this.grimFinalMoveSeen = true;
         this.grimFinalWireGround = written.onGround();
         this.grimFinalWireHorizontalCollision = written.horizontalCollision();
         this.grimFinalWireHasPosition = written.hasPosition();
      }
   }

   private void grimOnFinalUseWritten(ScaffoldModule.GrimFinalUseWrite written) {
      ScaffoldModule.GrimQueuedUse queued = written == null ? null : written.queued();
      if (queued != null) {
         ScaffoldModule.GrimWireClickRotation writtenWire = grimWireClickRotation(written.wire());
         RiptideRotationUtil.Rotation actualClick = writtenWire == null ? null : writtenWire.current();
         this.grimPaceQueuedNanos = written.nanos();
         this.grimPaceBook(queued.placed(), queued.against(), written.face(), actualClick == null ? Float.NaN : actualClick.pitch());
         ScaffoldModule.PlacementTarget pending = this.grimRealPendingTarget;
         boolean matches = grimFinalUseMatches(pending, written, this.grimAttemptBuildsPlannedCell);
         if (grimFinalUseBelongsToAttempt(written, this.grimAttemptGeneration)) {
            this.grimAttemptWriteCount++;
            this.grimAttemptSequence = Math.max(this.grimAttemptSequence, written.sequence());
            if (pending == null || !matches || written.hand() != queued.hand() || written.face() != queued.face()) {
               this.failGrimPlacementAttempt("packet-mismatch");
            } else if (grimFinalWriteIsDuplicate(queued, this.grimAttemptDuplicateSubmitted, this.grimAttemptState, this.grimAttemptWriteCount)) {
               this.failGrimPlacementAttempt("duplicate-write");
            } else if (!this.tellyUsesRealClicks() && !grimFinalWireMatches(queued, written.wire())) {
               this.failGrimPlacementAttempt("wire-rotation");
            } else {
               this.grimAttemptState = ScaffoldModule.GrimPlacementAttemptState.SENT;
               this.grimAttemptResult = "sent";
               this.grimAttemptPaceBooked = true;
               if (actualClick != null) {
                  this.grimLastPlaceYaw = actualClick.yaw();
               }

               this.resolveGrimUseOutcome();
            }
         } else {
            if (queued.ordinal() > 0 || this.grimUntrustedPredictions.containsKey(queued.placed())) {
               this.quarantineGrimPrediction(queued.placed(), written.sequence());
            }
         }
      }
   }

   static boolean grimFinalUseMatches(ScaffoldModule.PlacementTarget pending, ScaffoldModule.GrimFinalUseWrite written) {
      return grimFinalUseMatches(pending, written, false);
   }

   static boolean grimFinalUseMatches(ScaffoldModule.PlacementTarget pending, ScaffoldModule.GrimFinalUseWrite written, boolean buildsPlannedCell) {
      if (pending != null && written != null) {
         if (!written.support().equals(pending.supportBlock())) {
            return false;
         } else {
            return buildsPlannedCell ? true : written.face() == pending.face() && written.location().y >= pending.minPlacementY();
         }
      } else {
         return false;
      }
   }

   static boolean grimFinalUseBelongsToAttempt(ScaffoldModule.GrimFinalUseWrite written, long generation) {
      return written != null && written.queued() != null && written.queued().generation() == generation;
   }

   static boolean grimFirstFinalWriteForAttempt(ScaffoldModule.GrimPlacementAttemptState state, int writeCount) {
      return state == ScaffoldModule.GrimPlacementAttemptState.ARMED && writeCount == 1;
   }

   static boolean grimFinalWriteIsDuplicate(
      ScaffoldModule.GrimQueuedUse queued, boolean duplicateSubmitted, ScaffoldModule.GrimPlacementAttemptState state, int writeCount
   ) {
      return queued == null || queued.ordinal() != 0 || duplicateSubmitted || !grimFirstFinalWriteForAttempt(state, writeCount);
   }

   static boolean grimFinalWireMatches(ScaffoldModule.GrimQueuedUse queued, RiptideServerRotationView.WireSnapshot snapshot) {
      if (queued != null && queued.clickRotation() != null && snapshot != null) {
         ScaffoldModule.GrimWireClickRotation wire = grimWireClickRotation(snapshot);
         return wire != null && sameRotation(queued.clickRotation(), wire.current());
      } else {
         return false;
      }
   }

   public static void onVanillaUseItemOnResult(InteractionHand hand, BlockHitResult hit, InteractionResult result) {
      long generation = RiptideInputClicker.scaffoldUseGenerationInProgress();
      if (generation > 0L) {
         if (ModuleRegistry.get("scaffold") instanceof ScaffoldModule scaffold && scaffold.isEnabled() && scaffold.ownsRealClickPipeline()) {
            scaffold.grimOnVanillaUseResult(generation, hand, hit, result);
         }
      }
   }

   private void grimOnVanillaUseResult(long generation, InteractionHand hand, BlockHitResult hit, InteractionResult result) {
      ScaffoldModule.PlacementTarget pending = this.grimRealPendingTarget;
      if (this.grimAttemptState != ScaffoldModule.GrimPlacementAttemptState.IDLE
         && grimUseResultMatchesAttempt(
            generation, this.grimAttemptGeneration, hand, this.grimAttemptHand, this.grimAttemptResultSeen, hit, pending, this.grimAttemptBuildsPlannedCell
         )) {
         this.grimAttemptResultSeen = true;
         this.grimAttemptResultConsumed = result != null && result.consumesAction();
         this.grimAttemptResult = result == null ? "null" : result.getClass().getSimpleName().toLowerCase(Locale.ROOT);
         this.drainGrimFinalUseWrite();
         this.resolveGrimUseOutcome();
      }
   }

   static boolean grimUseResultMatchesAttempt(
      long generation,
      long activeGeneration,
      InteractionHand hand,
      InteractionHand expectedHand,
      boolean resultSeen,
      BlockHitResult hit,
      ScaffoldModule.PlacementTarget pending
   ) {
      return grimUseResultMatchesAttempt(generation, activeGeneration, hand, expectedHand, resultSeen, hit, pending, false);
   }

   static boolean grimUseResultMatchesAttempt(
      long generation,
      long activeGeneration,
      InteractionHand hand,
      InteractionHand expectedHand,
      boolean resultSeen,
      BlockHitResult hit,
      ScaffoldModule.PlacementTarget pending,
      boolean buildsPlannedCell
   ) {
      return !resultSeen
         && generation > 0L
         && generation == activeGeneration
         && hand != null
         && hand == expectedHand
         && pending != null
         && hit != null
         && grimClickFeasible(hit, pending, buildsPlannedCell);
   }

   private void resolveGrimUseOutcome() {
      ScaffoldModule.PlacementTarget pending = this.grimRealPendingTarget;
      if (pending != null && this.grimAttemptResultSeen && this.grimAttemptState == ScaffoldModule.GrimPlacementAttemptState.SENT) {
         if (MC.level != null) {
            BlockPos cell = pending.placedBlock();
            int age = Math.max(0, RiptideSharedState.get().getClientTickCounter() - this.grimRealQueuedTick);
            ScaffoldModule.GrimAttemptDecision decision = grimReduceAttempt(
               this.grimAttemptState,
               age,
               true,
               this.grimAttemptResultConsumed,
               this.isSolidSupport(MC.level.getBlockState(cell), cell),
               grimAckCovers(this.grimAttemptSequence, this.grimHighestObservedAck)
            );
            if (decision.state() == ScaffoldModule.GrimPlacementAttemptState.FAILED) {
               this.failGrimPlacementAttempt("use".equals(decision.failure()) ? "use-" + this.grimAttemptResult : decision.failure());
            } else {
               this.grimAttemptState = decision.state();
            }
         }
      }
   }

   public static void onBlockChangedAckHandled(int sequence) {
      if (MC != null && MC.level != null) {
         if (ModuleRegistry.get("scaffold") instanceof ScaffoldModule scaffold && scaffold.isEnabled() && scaffold.ownsRealClickPipeline()) {
            scaffold.ensureGrimPredictionLevel();
            if (sequence > scaffold.grimHighestObservedAck) {
               scaffold.grimHighestObservedAck = sequence;
            }

            scaffold.reconcileGrimPlacementAcks();
            scaffold.reconcileGrimUntrustedAck(sequence);
            ScaffoldModule.PlacementTarget active = scaffold.grimRealPendingTarget;
            if (active != null && grimAckCovers(scaffold.grimAttemptSequence, sequence)) {
               boolean solid = scaffold.solidAt(active.placedBlock());
               if (scaffold.grimAttemptState == ScaffoldModule.GrimPlacementAttemptState.FAILED) {
                  return;
               }

               int age = Math.max(0, RiptideSharedState.get().getClientTickCounter() - scaffold.grimRealQueuedTick);
               ScaffoldModule.GrimAttemptDecision decision = grimReduceAttempt(
                  scaffold.grimAttemptState, age, scaffold.grimAttemptResultSeen, scaffold.grimAttemptResultConsumed, solid, true
               );
               if (decision.state() == ScaffoldModule.GrimPlacementAttemptState.FAILED) {
                  scaffold.failGrimPlacementAttempt(decision.failure());
               } else {
                  scaffold.grimAttemptState = decision.state();
               }
            }
         }
      }
   }

   private boolean canRun() {
      return MC != null
         && MC.player != null
         && MC.level != null
         && MC.gameMode != null
         && MC.getConnection() != null
         && MC.gui.screen() == null
         && MC.gui.overlay() == null
         && !MC.player.isSpectator()
         && !MC.player.isHandsBusy()
         && !PackHideState.isActive()
         && !PackFreecamState.isActive()
         && !RiptideRemoteView.isActive()
         && !BuiltinModules.ownsManualFastExp()
         && !MultiPilot.isActive()
         && !PacketTeleportController.ownsMainMovement()
         && !MacroExecutor.isRunning()
         && !AutoTotemModule.operationActive()
         && !AutoArmorModule.operationActive();
   }

   public static boolean ownsTellyInput() {
      return ModuleRegistry.get("scaffold") instanceof ScaffoldModule scaffold && scaffold.isEnabled() && scaffold.isTellyMode() && scaffold.tellyOwnsInput;
   }

   public static boolean reservesTellyInput() {
      return ModuleRegistry.get("scaffold") instanceof ScaffoldModule scaffold
         && scaffold.isEnabled()
         && scaffold.isTellyMode()
         && (
            scaffold.tellyOwnsInput
               || MC != null
                  && MC.options != null
                  && (
                     physicallyDown(MC.options.keyUp)
                        || physicallyDown(MC.options.keyDown)
                        || physicallyDown(MC.options.keyLeft)
                        || physicallyDown(MC.options.keyRight)
                  )
         );
   }

   public static boolean reservesRageInput() {
      return ModuleRegistry.get("scaffold") instanceof ScaffoldModule scaffold && scaffold.isEnabled() && scaffold.isRageMode();
   }

   public static Input modifyMovementInput(ClientInput source, Input original) {
      if (original != null && MC != null && MC.player != null && MC.player.input == source) {
         if (!(ModuleRegistry.get("scaffold") instanceof ScaffoldModule scaffold)) {
            return original;
         } else if (!silentCorrectionOwnsInput(scaffold.isEnabled(), scaffold.canRun())) {
            scaffold.resetGrimLaunchReservation();
            return scaffold.grimWindingDown ? scaffold.transformSilentMovementInput(original) : original;
         } else if (scaffold.isTellyMode()) {
            scaffold.resetGrimLaunchReservation();
            Input authored = scaffold.tellyMovementInput(original);
            Input result = scaffold.tellyOwnsInput ? scaffold.transformTellyAuthoredInput(authored) : scaffold.transformSilentMovementInput(authored);
            scaffold.traceTellyTick();
            return result;
         } else {
            if (scaffold.isGrimFamily()) {
               scaffold.updateGrimCourseStep();
               scaffold.updateGrimFootingSurface();
               scaffold.updateGrimTakeoffClock();
               scaffold.updateGrimLaneOctant(original);
            }

            boolean directionalInput = hasDirectionalInput(original);
            ScaffoldModule.MovementLine previousLine = scaffold.currentMovementLine;
            ScaffoldModule.MovementLine requestedLine = directionalInput ? scaffold.buildMovementLine(original) : null;
            boolean courseChanged = grimRequestedCourseChange(previousLine, requestedLine);
            scaffold.currentMovementLine = requestedLine;
            if (!scaffold.isGrimFamily()) {
               scaffold.resetGrimLaunchReservation();
               return original;
            } else if (scaffold.grimAttemptDuplicateSubmitted || scaffold.grimHasUntrustedPrediction()) {
               scaffold.grimSprintNoForwardTick = RiptideSharedState.get().getClientTickCounter();
               scaffold.grimTraceJump = "reconcile";
               Input reconciled = new Input(original.forward(), original.backward(), original.left(), original.right(), original.jump(), true, false);
               reconciled = scaffold.transformGrimLegitInput(reconciled, original);
               scaffold.grimNoteStrip("reconcile", original, reconciled);
               scaffold.grimNoteStrip("net", original, reconciled);
               scaffold.grimTraceLaunchLedger = "reconcile dup="
                  + (scaffold.grimAttemptDuplicateSubmitted ? "T" : "F")
                  + " untrusted="
                  + (scaffold.grimHasUntrustedPrediction() ? "T" : "F");
               return reconciled;
            } else if (!scaffold.isValidBlock(scaffold.planningStack()) && scaffold.shouldSneakAtEdge()) {
               scaffold.resetGrimLaunchReservation();
               scaffold.grimEdgeSneakActive = true;
               scaffold.grimTraceEdgeDanger = false;
               scaffold.grimTraceFallDanger = false;
               scaffold.grimTraceLateralBrink = false;
               scaffold.grimTraceFootingOwed = false;
               scaffold.grimFootingOwedTicks = 0;
               scaffold.grimTraceJump = "empty";
               Input stopped = new Input(original.forward(), original.backward(), original.left(), original.right(), original.jump(), true, false);
               stopped = scaffold.transformGrimLegitInput(stopped, original);
               scaffold.grimNoteStrip("empty-stack", original, stopped);
               scaffold.grimNoteStrip("net", original, stopped);
               scaffold.grimTraceLaunchLedger = "empty-stack";
               return stopped;
            } else {
               boolean carryArc = scaffold.grimCarryArcActive();
               boolean atEdge = scaffold.shouldSneakAtEdge() && !carryArc;
               boolean fallAhead = scaffold.predictFallRisk() == ScaffoldModule.FallRisk.IMMINENT && !carryArc;
               boolean clickArmed = scaffold.grimClickReadyForFullSpeed();
               boolean clickLands = (atEdge || fallAhead) && clickArmed;
               boolean edgeDanger = atEdge && !clickLands;
               boolean fallDanger = fallAhead && !clickLands;
               boolean lateralBrink = scaffold.grimLateralBrink();
               boolean footingOwedRaw = !clickArmed && scaffold.grimFootingOwed();
               scaffold.grimFootingOwedTicks = footingOwedRaw ? scaffold.grimFootingOwedTicks + 1 : 0;
               boolean footingOwed = footingOwedRaw && scaffold.grimFootingOwedTicks <= 10;
               scaffold.grimEdgeSneakActive = edgeDanger || fallDanger || lateralBrink || footingOwed;
               scaffold.grimTraceEdgeDanger = edgeDanger;
               scaffold.grimTraceFallDanger = fallDanger;
               scaffold.grimTraceLateralBrink = lateralBrink;
               scaffold.grimTraceFootingOwed = footingOwed;
               scaffold.currentMovementLine = scaffold.retainGrimEdgeLine(requestedLine, scaffold.grimEdgeSneakActive);
               if (courseChanged) {
                  if (MC.player == null || !MC.player.onGround()) {
                     scaffold.grimStickyTarget = null;
                  }

                  scaffold.resetGrimInputOctant();
               }

               Input adjusted = original;
               BlockPos riseTakeoffCell = scaffold.grimRiseTakeoffCell();
               scaffold.grimTraceRiseTakeoff = riseTakeoffCell;
               boolean physicalClimbIntent = directionalInput && scaffold.grimJumpKeyHeld();
               scaffold.grimPhysicalClimbIntent = physicalClimbIntent;
               scaffold.grimRiseFloorPending();
               if (scaffold.grimRiseFloorCell != null) {
                  scaffold.grimTraceTakeoffWhy = "floor";
               }

               scaffold.updateGrimLaunchReservation(physicalClimbIntent, courseChanged);
               boolean wireSneak = scaffold.grimWireSneak(original.shift() || scaffold.grimEdgeSneakActive);
               if (!original.shift() && wireSneak) {
                  adjusted = new Input(original.forward(), original.backward(), original.left(), original.right(), original.jump(), true, original.sprint());
                  scaffold.grimNoteStrip("wire-sneak", original, adjusted);
               }

               boolean lipStop = grimLipStopApplies(MC.player.onGround(), clickArmed, footingOwedRaw, scaffold.grimFootingOwedTicks, 10);
               if (lipStop && !adjusted.shift()) {
                  Input beforeLip = adjusted;
                  adjusted = new Input(adjusted.forward(), adjusted.backward(), adjusted.left(), adjusted.right(), adjusted.jump(), true, false);
                  scaffold.grimNoteStrip("lip-stop", beforeLip, adjusted);
               }

               scaffold.grimTraceJump = "-";
               if (grimSuppressHeldArcSprint(physicalClimbIntent, MC.player.onGround(), scaffold.grimLaunchReservationAirborne)) {
                  Input beforeSprint = adjusted;
                  adjusted = grimWithoutSprint(adjusted);
                  scaffold.grimNoteStrip("sprint-drop", beforeSprint, adjusted);
                  scaffold.grimSprintNoForwardTick = RiptideSharedState.get().getClientTickCounter();
               }

               Input emitted = scaffold.transformGrimLegitInput(adjusted, original);
               scaffold.grimNoteStrip("lane", adjusted, emitted);
               if (MC.player.onGround()) {
                  scaffold.grimArcTravelStart = MC.player.position();
               }

               boolean arcBrake = scaffold.grimArcBrake(directionalInput, true);
               boolean climbBrake = scaffold.grimDiagonalClimbBrake(directionalInput, true);
               scaffold.grimTraceBrake = arcBrake ? (climbBrake ? "arc+dclimb" : "arc") : (climbBrake ? "dclimb" : "--");
               arcBrake |= climbBrake;
               if (arcBrake) {
                  Input beforeBrake = emitted;
                  emitted = grimCounterMovement(emitted);
                  scaffold.grimNoteStrip("brake", beforeBrake, emitted);
               }

               scaffold.grimNoteStrip("net", original, emitted);
               return emitted;
            }
         }
      } else {
         return original;
      }
   }

   static boolean grimSuppressHeldArcSprint(boolean physicalIntent, boolean grounded, boolean reservationAirborne) {
      return physicalIntent && (grounded || reservationAirborne);
   }

   static Input grimWithoutSprint(Input input) {
      return input != null && input.sprint()
         ? new Input(input.forward(), input.backward(), input.left(), input.right(), input.jump(), input.shift(), false)
         : input;
   }

   private boolean grimClickReadyForFullSpeed() {
      this.drainGrimFinalUseWrite();
      this.resolveGrimUseOutcome();
      boolean ready = this.grimRealQueuedTick == RiptideSharedState.get().getClientTickCounter()
         && this.grimAttemptState == ScaffoldModule.GrimPlacementAttemptState.PREDICTED
         && !this.grimAttemptDuplicateSubmitted
         && this.grimRealPendingTarget != null
         && this.solidAt(this.grimRealPendingTarget.placedBlock());
      this.grimTraceClickFeasible = ready;
      return ready;
   }

   static boolean grimClickFeasible(BlockHitResult hit, ScaffoldModule.PlacementTarget pending) {
      return grimClickFeasible(hit, pending, false);
   }

   static boolean grimClickFeasible(BlockHitResult hit, ScaffoldModule.PlacementTarget pending, boolean buildsPlannedCell) {
      if (hit != null && pending != null) {
         if (!hit.getBlockPos().equals(pending.supportBlock())) {
            return false;
         } else {
            return buildsPlannedCell ? true : hit.getDirection() == pending.face() && hit.getLocation().y >= pending.minPlacementY();
         }
      } else {
         return false;
      }
   }

   static boolean advancesCourse(Vec3 from, Vec3 lane, BlockPos cell) {
      if (lane == null) {
         return false;
      } else {
         double dx = cell.getX() + 0.5 - from.x;
         double dz = cell.getZ() + 0.5 - from.z;
         return dx * lane.x + dz * lane.z > 0.0;
      }
   }

   private int grimOracleFootingRow() {
      if (MC.player == null) {
         return Integer.MIN_VALUE;
      } else {
         return !MC.player.onGround() && this.grimFootingSurfaceY != Integer.MIN_VALUE ? this.grimFootingSurfaceY : this.grimFootingRowUnderFeet();
      }
   }

   private int grimFootingRowUnderFeet() {
      int row = grimFootingRowFor(MC.player.getY());
      if (MC.level == null) {
         return row;
      } else {
         for (int drop = 0; drop < 2 && !this.grimRowReachesFeet(row, MC.player.getY()); drop++) {
            row--;
         }

         return row;
      }
   }

   static int grimFootingRowFor(double feetY) {
      return Mth.floor(feetY - 0.001);
   }

   private boolean grimRowReachesFeet(int row, double feetY) {
      AABB box = MC.player.getBoundingBox();

      for (int x = Mth.floor(box.minX + 0.001); x <= Mth.floor(box.maxX - 0.001); x++) {
         for (int z = Mth.floor(box.minZ + 0.001); z <= Mth.floor(box.maxZ - 0.001); z++) {
            BlockPos cell = new BlockPos(x, row, z);
            VoxelShape shape = MC.level.getBlockState(cell).getCollisionShape(MC.level, cell);
            if (!shape.isEmpty() && row + shape.max(Axis.Y) >= feetY - 0.001) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean grimJumpKeyHeld() {
      return MC.player != null && (physicallyDown(MC.options.keyJump) || MC.player.input != null && MC.player.input.keyPresses.jump());
   }

   private void updateGrimLaunchReservation(boolean physicalClimbIntent, boolean courseChanged) {
      if (courseChanged) {
         this.resetGrimLaunchReservation();
      }

      if (MC.player != null && MC.level != null) {
         if (MC.player.onGround() || !this.grimLaunchReservationAirborne && this.grimLaunchReservedSupport == null && !physicalClimbIntent) {
            if (physicalClimbIntent && this.currentMovementLine != null) {
               if (this.grimLaunchReservationAirborne) {
                  this.resetGrimLaunchReservation();
               }

               int footingRow = this.grimOracleFootingRow();
               BlockPos landingSupport = this.grimArcLandingSupport(footingRow);
               boolean landingKnown = landingSupport != null && !MC.level.isOutsideBuildHeight(landingSupport);
               BlockPos beforeGrounded = this.grimLaunchReservedSupport;
               this.grimLaunchReservedSupport = grimReservedSupportAfterSample(
                  this.grimLaunchReservedSupport, landingKnown ? landingSupport.immutable() : null, true, true, false
               );
               if (beforeGrounded == null || !beforeGrounded.equals(this.grimLaunchReservedSupport)) {
                  this.grimLaunchReservedRiser = null;
                  this.grimLaunchReservedStep = null;
               }

               this.grimLaunchReservedConnector = null;
               int deficit = landingKnown ? this.grimCellDeficit(landingSupport) : 2;
               if (landingKnown
                  && deficit <= 0
                  && (
                     !this.grimTrustedSolidAt(landingSupport)
                        || this.grimAttemptDuplicateSubmitted
                        || !grimAttemptSupportReady(this.grimAttemptState, this.grimRealPendingTarget, landingSupport)
                  )) {
                  deficit = 1;
               }

               this.grimTraceLaunchLedger = "grounded known="
                  + (landingKnown ? "T" : "F")
                  + " deficit="
                  + deficit
                  + " pend="
                  + (this.grimRealPendingTarget != null ? "T" : "F")
                  + " land="
                  + (landingSupport == null ? "--" : landingSupport.toShortString())
                  + " landsolid="
                  + (landingSupport == null ? "--" : (this.isSolidSupport(MC.level.getBlockState(landingSupport), landingSupport) ? "T" : "F"))
                  + " riser="
                  + (landingKnown && this.grimCourseAscends() ? (this.solidAt(landingSupport.above()) ? "T" : "F") : "--");
            } else {
               this.resetGrimLaunchReservation();
               this.grimTraceLaunchLedger = "not-asked intent=" + (physicalClimbIntent ? "T" : "F") + " line=" + (this.currentMovementLine == null ? "F" : "T");
            }
         } else {
            BlockPos before = this.grimLaunchReservedSupport;
            BlockPos advanced = this.grimArcLandingSupport(this.grimOracleFootingRow());
            boolean stillLands = before != null
               && (
                  this.grimArcLandsOnColumnLive(MC.player.position(), MC.player.getDeltaMovement(), before)
                     || this.grimArcLandsOnColumnLive(MC.player.position(), MC.player.getDeltaMovement(), before.above())
               );
            this.grimLaunchReservedSupport = grimReservedSupportAfterSample(
               before,
               advanced == null ? null : advanced.immutable(),
               false,
               true,
               false,
               this.currentMovementLine == null ? null : this.currentMovementLine.direction(),
               stillLands
            );
            this.grimLaunchReservationAirborne = true;
            boolean moved = before != null && !before.equals(this.grimLaunchReservedSupport);
            if (moved) {
               this.grimLaunchReservedRiser = null;
               this.grimLaunchReservedConnector = null;
               this.grimLaunchReservedStep = null;
            }

            this.grimTraceLaunchLedger = "airborne reserved="
               + (this.grimLaunchReservedSupport == null ? "--" : this.grimLaunchReservedSupport.toShortString())
               + (moved ? "<" + before.toShortString() : "");
         }
      } else {
         this.resetGrimLaunchReservation();
         this.grimTraceLaunchLedger = "not-asked";
      }
   }

   static boolean grimAttemptSupportReady(ScaffoldModule.GrimPlacementAttemptState state, ScaffoldModule.PlacementTarget pending, BlockPos support) {
      return pending == null || support == null || !pending.placedBlock().equals(support) || state == ScaffoldModule.GrimPlacementAttemptState.PREDICTED;
   }

   private void resetGrimLaunchReservation() {
      this.grimLaunchReservedSupport = null;
      this.grimLaunchReservedConnector = null;
      this.grimLaunchReservedRiser = null;
      this.grimLaunchReservedStep = null;
      this.grimLaunchReservationAirborne = false;
      this.grimLaunchReservationStage = "--";
   }

   static BlockPos grimReservedSupportAfterSample(BlockPos reserved, BlockPos projected, boolean grounded, boolean physicalIntent, boolean courseChanged) {
      return grimReservedSupportAfterSample(reserved, projected, grounded, physicalIntent, courseChanged, null, true);
   }

   static BlockPos grimReservedSupportAfterSample(
      BlockPos reserved, BlockPos projected, boolean grounded, boolean physicalIntent, boolean courseChanged, Vec3 lane, boolean reservedStillLands
   ) {
      if (!physicalIntent || courseChanged) {
         return null;
      } else if (grounded) {
         return projected;
      } else if (reserved == null) {
         return projected;
      } else if (projected != null && !reservedStillLands && lane != null) {
         if (projected.getY() != reserved.getY()) {
            return reserved;
         } else {
            double ahead = (projected.getX() - reserved.getX()) * lane.x + (projected.getZ() - reserved.getZ()) * lane.z;
            return ahead > 0.0 ? projected : reserved;
         }
      } else {
         return reserved;
      }
   }

   private BlockPos grimOwnRiserSupport() {
      if (MC.player == null) {
         return null;
      } else {
         BlockPos at = BlockPos.containing(MC.player.position());
         return new BlockPos(at.getX(), this.grimOracleFootingRow(), at.getZ());
      }
   }

   static boolean grimSameColumn(BlockPos a, BlockPos b) {
      return a != null && b != null && a.getX() == b.getX() && a.getZ() == b.getZ();
   }

   private int grimBuiltFloorRow() {
      ScaffoldModule.GrimRowLock lock = this.grimActiveRowLock();
      return grimBuiltFloorRow(this.grimOracleFootingRow(), lock == null ? Integer.MIN_VALUE : lock.rowY(), this.grimAirborneBuiltRow);
   }

   static int grimBuiltFloorRow(int oracleRow, int lockRow, int airborneBuiltRow) {
      int floor = oracleRow;
      if (lockRow != Integer.MIN_VALUE) {
         floor = oracleRow == Integer.MIN_VALUE ? lockRow : Math.max(oracleRow, lockRow);
      }

      if (airborneBuiltRow != Integer.MIN_VALUE) {
         floor = floor == Integer.MIN_VALUE ? airborneBuiltRow : Math.max(floor, airborneBuiltRow);
      }

      return floor;
   }

   static boolean grimFloorGateRefuses(int candidateY, int footingRow, boolean descending) {
      return candidateY < footingRow && !descending;
   }

   static boolean grimTierServesBelowBuiltFloor(int placedY, int builtFloorRow, boolean fallingBelowFooting, boolean arcLandsOnColumn) {
      return grimFloorGateRefuses(placedY, builtFloorRow, fallingBelowFooting) && !arcLandsOnColumn;
   }

   private boolean grimBelowBuiltFloor(ScaffoldModule.PlacementTarget target) {
      if (target != null && MC.player != null) {
         BlockPos placed = target.placedBlock();
         return grimTierServesBelowBuiltFloor(
            placed.getY(),
            this.grimBuiltFloorRow(),
            this.grimDescendingBelowFooting(),
            heldArcLandsOnColumn(MC.player.position(), MC.player.getDeltaMovement(), placed)
         );
      } else {
         return false;
      }
   }

   static boolean grimFallServesUncatchable(boolean fallingBelowFooting, int placedY, double feetY, double feetVelY) {
      return fallingBelowFooting && placedY + 1.0 > feetY + Math.min(feetVelY, 0.0) + 0.001;
   }

   private boolean grimFallUncatchable(ScaffoldModule.PlacementTarget target) {
      return target != null && MC.player != null
         ? grimFallServesUncatchable(this.grimDescendingBelowFooting(), target.placedBlock().getY(), MC.player.position().y, MC.player.getDeltaMovement().y)
         : false;
   }

   static boolean grimGoalErrorClosing(float error, float lastError) {
      return Float.isNaN(lastError) || error < lastError - 0.5F;
   }

   private BlockPos grimRiseTakeoffCellNow() {
      if (MC.player != null && MC.level != null && MC.player.onGround() && this.grimJumpKeyHeld()) {
         if (this.currentMovementLine != null && this.currentMovementLine.direction() != null) {
            int footingRow = this.grimOracleFootingRow();
            Vec3 lane = this.currentMovementLine.direction();
            Vec3 position = MC.player.position();
            int riseY = footingRow + 1;
            ScaffoldModule.GrimRowLock rowLock = this.grimActiveRowLock();
            BlockPos last = null;
            BlockPos far = null;

            for (double step = 0.5; step <= 1.7; step += 0.3) {
               BlockPos cell = BlockPos.containing(position.x + lane.x * step, riseY + 0.5, position.z + lane.z * step);
               if (!cell.equals(last)) {
                  last = cell;
                  if (!grimCellBehind(position, lane, cell, 0.0)) {
                     BlockPos below = cell.below();
                     if ((!this.solidAt(below) || !grimBoxOverColumn(position, below)) && !MC.level.isOutsideBuildHeight(cell)) {
                        BlockState state = MC.level.getBlockState(cell);
                        if (!this.isSolidSupport(state, cell) && (state.isAir() || state.canBeReplaced())) {
                           boolean belowReady = this.grimTrustedSolidAt(below);
                           if (belowReady && (rowLock == null || rowLock.allows(cell, true))) {
                              double[] landing = grimHeldTakeoffLanding(position, MC.player.getDeltaMovement(), position.y);
                              if (landing != null && this.solidAt(new BlockPos((int)Math.floor(landing[0]), riseY - 1, (int)Math.floor(landing[1])))) {
                                 this.grimTraceTakeoffWhy = "lands";
                                 return null;
                              }

                              if (far == null) {
                                 far = cell;
                              }

                              if (heldArcLandsOnColumnFromGround(position, MC.player.getDeltaMovement(), cell)) {
                                 this.grimTraceTakeoffWhy = "cell";
                                 return cell;
                              }
                           }
                        }
                     }
                  }
               }
            }

            BlockPos own = this.grimOwnColumnRiseCell(position, riseY, rowLock);
            if (own != null) {
               this.grimTraceTakeoffWhy = "own";
               return own;
            } else {
               this.grimTraceTakeoffWhy = far != null ? "far" : "none";
               return far;
            }
         } else {
            this.grimTraceTakeoffWhy = "nolane";
            return null;
         }
      } else {
         this.grimTraceTakeoffWhy = "air";
         return null;
      }
   }

   private BlockPos grimOwnColumnRiseCell(Vec3 position, int riseY, ScaffoldModule.GrimRowLock rowLock) {
      BlockPos cell = this.grimOwnColumnRiseCellIgnoringFloor(position, riseY, rowLock);
      return cell != null && this.solidAt(cell.below()) ? cell : null;
   }

   private BlockPos grimOwnColumnRiseCellIgnoringFloor(Vec3 position, int riseY, ScaffoldModule.GrimRowLock rowLock) {
      if (!this.grimBridgeRunning(RiptideSharedState.get().getClientTickCounter())) {
         return null;
      } else {
         BlockPos foot = this.grimEffectiveFootCell();
         BlockPos cell = new BlockPos(foot.getX(), riseY, foot.getZ());
         if (MC.level.isOutsideBuildHeight(cell)) {
            return null;
         } else {
            BlockState state = MC.level.getBlockState(cell);
            if (!this.isSolidSupport(state, cell) && (state.isAir() || state.canBeReplaced())) {
               if (!grimBoxOverColumn(position, cell.below())) {
                  return null;
               } else {
                  return rowLock != null && !rowLock.allows(cell, true) ? null : cell;
               }
            } else {
               return null;
            }
         }
      }
   }

   private boolean grimRiseFloorPending() {
      this.grimRiseFloorCell = null;
      if (MC.player == null || MC.level == null) {
         return false;
      } else if (!this.grimJumpKeyHeld() && !this.grimLaunchReservationAirborne) {
         return false;
      } else {
         BlockPos support = this.grimArcLandingSupport(this.grimOracleFootingRow());
         if (support != null && !this.solidAt(support)) {
            if (this.grimCellDeficit(support) >= 2) {
               return false;
            } else {
               this.grimRiseFloorCell = support;
               this.grimRiseFloorTick = RiptideSharedState.get().getClientTickCounter();
               return true;
            }
         } else {
            return false;
         }
      }
   }

   private int grimCellDeficit(BlockPos cell) {
      if (MC.level != null && cell != null && !MC.level.isOutsideBuildHeight(cell)) {
         if (this.isSolidSupport(MC.level.getBlockState(cell), cell)) {
            return 0;
         } else {
            for (Direction face : Direction.values()) {
               BlockPos neighbour = cell.relative(face);
               if (!MC.level.isOutsideBuildHeight(neighbour) && this.isSolidSupport(MC.level.getBlockState(neighbour), neighbour)) {
                  return 1;
               }
            }

            return 2;
         }
      } else {
         return 0;
      }
   }

   private boolean grimRiseFloorAwaiting(BlockPos cell) {
      return this.grimRiseFloorCell != null && this.grimRiseFloorCell.equals(cell) && this.grimRiseFloorTick == RiptideSharedState.get().getClientTickCounter();
   }

   private BlockPos grimRiseTakeoffCell() {
      BlockPos now = this.grimRiseTakeoffCellNow();
      if (now != null) {
         this.grimRiseTakeoffLatch = now;
         this.grimRiseTakeoffLatchTicks = 4;
         return now;
      } else if (this.grimRiseTakeoffLatch == null) {
         return null;
      } else {
         boolean climbing = MC.player != null && MC.player.onGround() && this.grimJumpKeyHeld();
         if (climbing && this.grimRiseTakeoffLatchTicks > 0 && !this.solidAt(this.grimRiseTakeoffLatch.below())) {
            this.grimRiseTakeoffLatchTicks--;
            this.grimTraceTakeoffWhy = "latch";
            return this.grimRiseTakeoffLatch;
         } else {
            this.grimRiseTakeoffLatch = null;
            this.grimRiseTakeoffLatchTicks = 0;
            return null;
         }
      }
   }

   static boolean grimLipStopApplies(boolean onGround, boolean clickArmed, boolean footingOwed, int owedTicks, int maxTicks) {
      return onGround && !clickArmed && footingOwed && owedTicks > maxTicks;
   }

   static double[] grimHeldArcLanding(Vec3 position, Vec3 velocity, double initialVy, double surfaceY, boolean grounded) {
      return grimHeldArcLanding(position, velocity, initialVy, surfaceY, grounded, 0);
   }

   static double[] grimHeldArcLanding(Vec3 position, Vec3 velocity, double initialVy, double surfaceY, boolean grounded, int sneakedTicks) {
      return grimHeldArcLanding(position, velocity, initialVy, surfaceY, grounded, sneakedTicks, null, null, null);
   }

   static double[] grimHeldArcLanding(Vec3 position, Vec3 velocity, double initialVy, double surfaceY, boolean grounded, int sneakedTicks, Vec3 inputDirection) {
      return grimHeldArcLanding(position, velocity, initialVy, surfaceY, grounded, sneakedTicks, inputDirection, null, null);
   }

   static double grimHeldControlMagnitude(Vec3 normalizedDirection, boolean sneaking) {
      if (normalizedDirection == null) {
         return 0.0;
      } else {
         double maxAxis = Math.max(Math.abs(normalizedDirection.x), Math.abs(normalizedDirection.z));
         if (maxAxis <= 1.0E-9) {
            return 0.0;
         } else {
            double scaled = 0.98 * (sneaking ? 0.3 : 1.0);
            return Math.min(1.0, scaled / maxAxis);
         }
      }
   }

   private static double[] grimHeldArcLanding(
      Vec3 position,
      Vec3 velocity,
      double initialVy,
      double surfaceY,
      boolean grounded,
      int sneakedTicks,
      Vec3 inputDirection,
      AABB initialBox,
      ScaffoldModule.GrimArcCollisionResolver collisionResolver
   ) {
      if (position != null && velocity != null) {
         double x = position.x;
         double y = position.y;
         double z = position.z;
         double vx = velocity.x;
         double vy = initialVy;
         double vz = velocity.z;
         Vec3 heldDirection = null;
         if (inputDirection != null && inputDirection.horizontalDistanceSqr() > 1.0E-12) {
            double length = Math.sqrt(inputDirection.x * inputDirection.x + inputDirection.z * inputDirection.z);
            heldDirection = new Vec3(inputDirection.x / length, 0.0, inputDirection.z / length);
         }

         AABB box = initialBox;
         boolean first = grounded;
         boolean descending = initialVy < 0.0;
         if (descending && y < surfaceY) {
            return null;
         } else {
            for (int tick = 0; tick < 20; tick++) {
               if (vx * vx + vz * vz < 9.0E-6) {
                  vx = 0.0;
                  vz = 0.0;
               }

               if (Math.abs(vy) < 0.003) {
                  vy = 0.0;
               }

               double speed = Math.sqrt(vx * vx + vz * vz);
               double scale = tick < sneakedTicks ? 0.3 : 1.0;
               double directionX = heldDirection == null ? (speed <= 1.0E-6 ? 0.0 : vx / speed) : heldDirection.x;
               double directionZ = heldDirection == null ? (speed <= 1.0E-6 ? 0.0 : vz / speed) : heldDirection.z;
               double controlMagnitude = heldDirection == null ? scale : grimHeldControlMagnitude(heldDirection, tick < sneakedTicks);
               double movedVx;
               double movedVz;
               if (first) {
                  double acceleration = heldDirection == null ? 0.096 * scale : 0.1 * controlMagnitude;
                  movedVx = vx + directionX * acceleration;
                  movedVz = vz + directionZ * acceleration;
               } else {
                  double acceleration = heldDirection == null ? 0.02 * scale : 0.02 * controlMagnitude;
                  movedVx = vx + directionX * acceleration;
                  movedVz = vz + directionZ * acceleration;
               }

               double previousX = x;
               double previousY = y;
               double previousZ = z;
               Vec3 intended = new Vec3(movedVx, vy, movedVz);
               Vec3 movement = collisionResolver != null && box != null ? collisionResolver.resolve(box, intended) : intended;
               if (movement == null) {
                  movement = intended;
               }

               x += movement.x;
               y += movement.y;
               z += movement.z;
               if (box != null) {
                  box = box.move(movement);
               }

               boolean xCollision = !Mth.equal(intended.x, movement.x);
               boolean yCollision = !Mth.equal(intended.y, movement.y);
               boolean zCollision = !Mth.equal(intended.z, movement.z);
               if (xCollision) {
                  movedVx = 0.0;
               }

               if (zCollision) {
                  movedVz = 0.0;
               }

               if (yCollision) {
                  if (intended.y < 0.0) {
                     return new double[]{x, z};
                  }

                  vy = 0.0;
               }

               if (vy < 0.0) {
                  descending = true;
               }

               if (descending && previousY >= surfaceY && y <= surfaceY) {
                  double drop = previousY - y;
                  double t = drop <= 1.0E-12 ? 1.0 : Mth.clamp((previousY - surfaceY) / drop, 0.0, 1.0);
                  return new double[]{previousX + movement.x * t, previousZ + movement.z * t};
               }

               if (first) {
                  vx = movedVx * 0.546;
                  vz = movedVz * 0.546;
                  first = false;
               } else {
                  vx = movedVx * 0.91;
                  vz = movedVz * 0.91;
               }

               vy = (vy - 0.08) * 0.98;
            }

            return null;
         }
      } else {
         return null;
      }
   }

   private double[] grimHeldArcLandingLive(Vec3 position, Vec3 velocity, double initialVy, double surfaceY, boolean grounded, int sneakedTicks) {
      Vec3 lane = this.currentMovementLine == null ? null : this.currentMovementLine.direction();
      return MC.player != null && MC.level != null
         ? grimHeldArcLanding(
            position,
            velocity,
            initialVy,
            surfaceY,
            grounded,
            sneakedTicks,
            lane,
            MC.player.getBoundingBox(),
            (box, movement) -> Entity.collideBoundingBox(
               MC.player, movement, box, MC.level, MC.level.getEntityCollisions(MC.player, box.expandTowards(movement))
            )
         )
         : grimHeldArcLanding(position, velocity, initialVy, surfaceY, grounded, sneakedTicks, lane);
   }

   static boolean heldArcLandsOnColumn(Vec3 position, Vec3 velocity, double initialVy, BlockPos cell, boolean grounded) {
      if (cell == null) {
         return false;
      } else {
         double[] landing = grimHeldArcLanding(position, velocity, initialVy, cell.getY() + 1.0, grounded);
         return landing != null && grimLandingOverlaps(landing, cell);
      }
   }

   static boolean heldArcLandsOnColumn(Vec3 position, Vec3 velocity, BlockPos cell) {
      return heldArcLandsOnColumn(position, velocity, velocity == null ? 0.0 : velocity.y, cell, false);
   }

   private boolean grimArcLandsOnColumnLive(Vec3 position, Vec3 velocity, BlockPos cell) {
      if (cell == null) {
         return false;
      } else {
         double[] landing = this.grimHeldArcLandingLive(
            position, velocity, velocity == null ? 0.0 : velocity.y, cell.getY() + 1.0, MC.player != null && MC.player.onGround(), this.grimArcSneakTicks()
         );
         return landing != null && grimLandingOverlaps(landing, cell);
      }
   }

   private int grimArcSneakTicks() {
      int latchedCurrent = MC.player != null && MC.player.isCrouching() ? 1 : 0;
      return Math.max(latchedCurrent, Math.max(0, this.grimSneakHoldTicks));
   }

   static boolean heldArcLandsOnColumnFromGround(Vec3 position, Vec3 velocity, BlockPos cell) {
      return heldArcLandsOnColumn(position, velocity, 0.42, cell, true);
   }

   static double[] grimHeldTakeoffLanding(Vec3 position, Vec3 velocity, double surfaceY) {
      return grimHeldArcLanding(position, velocity, 0.42, surfaceY, true);
   }

   static boolean grimLandingOverlaps(double[] landing, BlockPos cell) {
      if (landing != null && cell != null) {
         double overlapX = Math.min(landing[0] + 0.29, cell.getX() + 1.0) - Math.max(landing[0] - 0.29, (double)cell.getX());
         double overlapZ = Math.min(landing[1] + 0.29, cell.getZ() + 1.0) - Math.max(landing[1] - 0.29, (double)cell.getZ());
         return overlapX >= 0.15 && overlapZ >= 0.15;
      } else {
         return false;
      }
   }

   private boolean grimCarryArcActive() {
      return MC.player != null && grimCarryArcActive(MC.player.onGround(), this.grimJumpKeyHeld());
   }

   static boolean grimCarryArcActive(boolean onGround, boolean jumpHeld) {
      return jumpHeld && !onGround;
   }

   static boolean grimRequestedCourseChange(ScaffoldModule.MovementLine previous, ScaffoldModule.MovementLine requested) {
      return previous != null && requested != null && !sameGrimCourse(previous.direction(), requested.direction());
   }

   private Input tellyMovementInput(Input original) {
      if (!this.tellyOwnsInput || this.tellyMotion == ScaffoldModule.TellyMotion.RELEASED) {
         return original;
      } else if (this.tellyMotion == ScaffoldModule.TellyMotion.HOLD) {
         ScaffoldModule.TellyStrafe strafe = this.tellyHoldStrafe != ScaffoldModule.TellyStrafe.NONE ? this.tellyHoldStrafe : this.tellyAirStrafeThisTick;
         this.tellyHoldStrafe = ScaffoldModule.TellyStrafe.NONE;
         return new Input(
            false,
            false,
            strafe == ScaffoldModule.TellyStrafe.LEFT,
            strafe == ScaffoldModule.TellyStrafe.RIGHT,
            this.tellyJumpThisTick,
            this.tellySneakThisTick,
            false
         );
      } else {
         return MC.player != null && MC.player.onGround() && usesTellyGroundWOnly(this.tellyPhase)
            ? tellyGroundForwardInput(this.tellyJumpThisTick, this.tellySneakThisTick)
            : tellyAirForwardInput(this.tellyAirStrafeThisTick, this.tellyJumpThisTick, this.tellySneakThisTick);
      }
   }

   private ScaffoldModule.TellyStrafe tellyHoldLaneStrafe(LocalPlayer player) {
      if (player != null && this.tellyLastBridge != null) {
         double error = this.tellyLaneCenter - laneCoordinate(player.position(), this.tellyAnchorYaw);
         if (!(Math.abs(error) <= 0.15) && !(Math.abs(error) > 0.55)) {
            return error > 0.0 ? ScaffoldModule.TellyStrafe.LEFT : ScaffoldModule.TellyStrafe.RIGHT;
         } else {
            return ScaffoldModule.TellyStrafe.NONE;
         }
      } else {
         return ScaffoldModule.TellyStrafe.NONE;
      }
   }

   static Input tellyAirForwardInput(ScaffoldModule.TellyStrafe strafe, boolean jump, boolean sneak) {
      ScaffoldModule.TellyStrafe safe = strafe == null ? ScaffoldModule.TellyStrafe.NONE : strafe;
      return new Input(true, false, safe == ScaffoldModule.TellyStrafe.LEFT, safe == ScaffoldModule.TellyStrafe.RIGHT, jump, sneak, true);
   }

   static ScaffoldModule.TellyAirCorrectionState nextTellyAirCorrection(
      int cooldown, ScaffoldModule.TellyStrafe lastPulse, int pulsesUsed, double laneError, double lateralVelocity, int ticksUntilLanding
   ) {
      int cooled = Math.max(0, cooldown - 1);
      int used = Math.max(0, pulsesUsed);
      ScaffoldModule.TellyStrafe previous = lastPulse == null ? ScaffoldModule.TellyStrafe.NONE : lastPulse;
      if (Double.isFinite(laneError) && Double.isFinite(lateralVelocity)) {
         double predictedError = tellyPredictedLaneError(laneError, lateralVelocity, ticksUntilLanding);
         double projectedDistance = Math.abs(predictedError);
         if (projectedDistance <= 0.22 && Math.abs(laneError) <= 0.3) {
            previous = ScaffoldModule.TellyStrafe.NONE;
         }

         if (!(projectedDistance <= 0.3) && cooldown <= 0) {
            double desired = Mth.clamp(laneError * 0.18, -0.055, 0.055);
            ScaffoldModule.TellyStrafe requested = tellyLaneDamperArm(lateralVelocity, desired);
            if (requested == ScaffoldModule.TellyStrafe.NONE) {
               return new ScaffoldModule.TellyAirCorrectionState(ScaffoldModule.TellyStrafe.NONE, cooled, previous, used);
            } else {
               boolean reversing = previous != ScaffoldModule.TellyStrafe.NONE && requested != previous;
               if (reversing && projectedDistance < 0.48) {
                  return new ScaffoldModule.TellyAirCorrectionState(ScaffoldModule.TellyStrafe.NONE, cooled, previous, used);
               } else {
                  int pulseLimit = projectedDistance >= 0.48 ? 3 : 2;
                  return used >= pulseLimit
                     ? new ScaffoldModule.TellyAirCorrectionState(ScaffoldModule.TellyStrafe.NONE, cooled, previous, used)
                     : new ScaffoldModule.TellyAirCorrectionState(requested, 2, requested, used + 1);
               }
            }
         } else {
            return new ScaffoldModule.TellyAirCorrectionState(ScaffoldModule.TellyStrafe.NONE, cooled, previous, used);
         }
      } else {
         return new ScaffoldModule.TellyAirCorrectionState(ScaffoldModule.TellyStrafe.NONE, cooled, previous, used);
      }
   }

   static ScaffoldModule.TellyStrafe tellyLaneDamperArm(double lateralVelocity, double desiredVelocity) {
      if (!Double.isFinite(lateralVelocity) || !Double.isFinite(desiredVelocity)) {
         return ScaffoldModule.TellyStrafe.NONE;
      } else if (lateralVelocity < desiredVelocity - 0.026) {
         return ScaffoldModule.TellyStrafe.LEFT;
      } else {
         return lateralVelocity > desiredVelocity + 0.026 ? ScaffoldModule.TellyStrafe.RIGHT : ScaffoldModule.TellyStrafe.NONE;
      }
   }

   static double tellyPredictedLaneError(double laneError, double lateralVelocity, int ticksUntilLanding) {
      int ticks = Mth.clamp(ticksUntilLanding, 1, 14);
      double travel = 0.0;
      double motion = lateralVelocity;

      for (int tick = 0; tick < ticks; tick++) {
         travel += motion;
         motion *= 0.91;
      }

      return laneError - travel;
   }

   private void updateTellyAirCorrection(LocalPlayer player, double catchFeetY) {
      if (player != null && this.tellyLastBridge != null) {
         Vec3 left = this.tellyLeftVector();
         double laneError = this.tellyLaneCenter - laneCoordinate(player.position(), this.tellyAnchorYaw);
         double lateralVelocity = player.getDeltaMovement().dot(left);
         int ticksUntilLanding = tellyTicksUntilCatch(player.position(), player.getDeltaMovement(), catchFeetY);
         ScaffoldModule.TellyAirCorrectionState next = nextTellyAirCorrection(
            this.tellyAirStrafeCooldown, this.tellyAirLastStrafe, this.tellyAirStrafePulses, laneError, lateralVelocity, ticksUntilLanding
         );
         this.tellyAirStrafeThisTick = next.pulse();
         this.tellyAirStrafeCooldown = next.cooldown();
         this.tellyAirLastStrafe = next.lastPulse();
         this.tellyAirStrafePulses = next.pulsesUsed();
      }
   }

   private void clearTellyAirCorrection() {
      this.tellyAirStrafeThisTick = ScaffoldModule.TellyStrafe.NONE;
      this.tellyAirLastStrafe = ScaffoldModule.TellyStrafe.NONE;
      this.tellyAirStrafeCooldown = 0;
      this.tellyAirStrafePulses = 0;
   }

   static Input tellyGroundForwardInput(boolean jump, boolean sneak) {
      return new Input(true, false, false, false, jump, sneak, true);
   }

   static boolean usesTellyGroundWOnly(ScaffoldModule.TellyPhase phase) {
      return phase == ScaffoldModule.TellyPhase.RUNNING;
   }

   private void updateTellyGroundSteering(LocalPlayer player, BlockPos support) {
      if (player != null && support != null && player.onGround() && this.tellyPhase == ScaffoldModule.TellyPhase.RUNNING) {
         Vec3 left = this.tellyLeftVector();
         double error = this.tellyLaneCenter - laneCoordinate(player.position(), this.tellyAnchorYaw);
         double lateralVelocity = player.getDeltaMovement().dot(left);
         BlockState supportState = MC.level.getBlockState(support);
         double drag = supportState.getBlock().getFriction() * 0.91;
         double groundAcceleration = Math.max(0.025, player.getSpeed() * 0.98);
         double forwardSpeed = Math.max(0.0, player.getDeltaMovement().dot(this.tellyForwardVector()));
         double runwayRemaining = this.tellyRunwayRemaining(player, support);
         double launchPoint = tellyLaunchPoint(forwardSpeed);
         boolean returnToCourse = runwayRemaining <= tellySteeringReturnDistance(launchPoint, forwardSpeed, this.tellyGroundSteerOffset);
         ScaffoldModule.TellyGroundSteeringState next = nextTellyGroundSteering(
            this.tellyGroundSteeringActive, this.tellyGroundSteerOffset, error, lateralVelocity, groundAcceleration, drag, returnToCourse
         );
         this.tellyGroundSteeringActive = next.active();
         this.tellyGroundSteerOffset = next.offsetDegrees();
      } else {
         this.clearTellyGroundSteering();
      }
   }

   private double tellyRunwayRemaining(LocalPlayer player, BlockPos support) {
      Direction direction = this.tellyForwardDirection();

      double remaining = switch (direction) {
         case EAST -> support.getX() + 1.0 - player.getX();
         case WEST -> player.getX() - support.getX();
         case SOUTH -> support.getZ() + 1.0 - player.getZ();
         case NORTH -> player.getZ() - support.getZ();
         default -> Double.POSITIVE_INFINITY;
      };

      for (int step = 1; step <= 8; step++) {
         BlockPos ahead = support.relative(direction, step);
         if (!this.isTellySupport(MC.level.getBlockState(ahead), ahead)) {
            return remaining + step - 1.0;
         }
      }

      return Double.POSITIVE_INFINITY;
   }

   static ScaffoldModule.TellyGroundSteeringState nextTellyGroundSteering(
      boolean active, float currentOffset, double laneError, double lateralVelocity, double groundAcceleration, double drag, boolean returnToCourse
   ) {
      boolean nextActive = active;
      double predictedError = laneError - lateralVelocity * 3.0;
      if (Math.abs(laneError) > 0.55) {
         nextActive = false;
      } else if (returnToCourse) {
         nextActive = false;
      } else if (active) {
         if (Math.abs(laneError) <= 0.035 && Math.abs(lateralVelocity) <= 0.012) {
            nextActive = false;
         }
      } else if (Math.abs(laneError) > 0.08 || Math.abs(predictedError) > 0.1) {
         nextActive = true;
      }

      float targetOffset = 0.0F;
      if (nextActive) {
         double safeDrag = Mth.clamp(drag, 0.2, 0.99);
         double safeAcceleration = Math.max(0.025, Math.abs(groundAcceleration));
         double desiredLateralVelocity = Mth.clamp(laneError * 0.18, -0.055, 0.055);
         double requiredLateralAcceleration = desiredLateralVelocity / safeDrag - lateralVelocity;
         double steerProgress = Mth.clamp((Math.abs(laneError) - 0.08) / 0.15999999999999998, 0.0, 1.0);
         double maxSteer = Mth.lerp(steerProgress, 8.0, 15.0);
         double maximumRatio = Math.sin(maxSteer * (float) (Math.PI / 180.0));
         double steeringRatio = Mth.clamp(requiredLateralAcceleration / safeAcceleration, -maximumRatio, maximumRatio);
         targetOffset = (float)(-Math.toDegrees(Math.asin(steeringRatio)));
      }

      boolean movingOutward = targetOffset != 0.0F
         && (currentOffset == 0.0F || Math.signum(targetOffset) == Math.signum(currentOffset) && Math.abs(targetOffset) > Math.abs(currentOffset));
      float maximumStep = movingOutward ? 5.0F : 7.0F;
      float nextOffset = approachTellyAngle(currentOffset, targetOffset, maximumStep);
      if (!nextActive && Math.abs(nextOffset) < 0.05F) {
         nextOffset = 0.0F;
      }

      return new ScaffoldModule.TellyGroundSteeringState(nextActive, nextOffset);
   }

   static double tellySteeringReturnDistance(double launchPoint, double forwardSpeed, float steeringOffset) {
      int returnTicks = Math.max(1, Mth.ceil(Math.abs(steeringOffset) / 7.0F));
      return launchPoint + Math.max(0.0, forwardSpeed) * (returnTicks + 1) + 0.1;
   }

   private static float approachTellyAngle(float current, float target, float maximumStep) {
      float difference = target - current;
      return Math.abs(difference) <= maximumStep ? target : current + Math.copySign(maximumStep, difference);
   }

   private void clearTellyGroundSteering() {
      this.tellyGroundSteeringActive = false;
      this.tellyGroundSteerOffset = 0.0F;
      this.clearTellyLaneBias();
   }

   private void clearTellyLaneBias() {
      this.tellyLaneBias = 0.0F;
      this.grimLaneInputBias = 0.0F;
      this.grimInputDeltaYaw = Float.NaN;
   }

   public static float correctedMovementYaw(Entity entity, float vanillaYaw) {
      if (entity == null || MC == null || entity != MC.player) {
         return vanillaYaw;
      } else if (BuiltinModules.ownsManualFastExp()) {
         return vanillaYaw;
      } else if (ModuleRegistry.get("scaffold") instanceof ScaffoldModule scaffold && scaffold.silentRotationApplies()) {
         RiptideRotationUtil.Rotation rotation = scaffold.grimSilentRotation;
         return rotation != null && scaffold.grimRotationResetTicks > 0 ? outgoingMovementYaw(MC.player, vanillaYaw) : vanillaYaw;
      } else {
         return vanillaYaw;
      }
   }

   public static float outgoingMovementYaw(LocalPlayer player, float vanillaYaw) {
      RiptideRotationUtil.Rotation rotation = activeGrimRotation(player);
      int tick = RiptideSharedState.get().getClientTickCounter();
      if (tick != grimSentYawTick) {
         grimSentYawTick = tick;
         float anchor = Float.isNaN(grimSentYaw) ? vanillaYaw : grimSentYaw;
         grimSentYaw = rotation == null ? vanillaYaw : grimContinuousYaw(anchor, rotation.yaw());
      }

      return grimSentYaw;
   }

   static float grimContinuousYaw(float previousSentYaw, float silentYaw) {
      return previousSentYaw + Mth.wrapDegrees(silentYaw - previousSentYaw);
   }

   public static float outgoingMovementPitch(LocalPlayer player, float vanillaPitch) {
      RiptideRotationUtil.Rotation rotation = activeGrimRotation(player);
      return rotation == null ? vanillaPitch : rotation.pitch();
   }

   public static Vec3 correctedJumpImpulse(LivingEntity entity, Vec3 vanillaImpulse) {
      RiptideRotationUtil.Rotation rotation = activeGrimRotationValue(entity);
      if (rotation == null) {
         return vanillaImpulse;
      } else {
         float sent = MC != null && entity == MC.player ? outgoingMovementYaw(MC.player, rotation.yaw()) : rotation.yaw();
         float yaw = sent * (float) (Math.PI / 180.0);
         return new Vec3(-Mth.sin(yaw) * 0.2F, vanillaImpulse.y, Mth.cos(yaw) * 0.2F);
      }
   }

   public static float correctedFallFlyingPitch(LivingEntity entity, float vanillaPitch) {
      RiptideRotationUtil.Rotation rotation = activeGrimRotationValue(entity);
      return rotation == null ? vanillaPitch : rotation.pitch();
   }

   public static Vec3 correctedFallFlyingLook(LivingEntity entity, Vec3 vanillaLook) {
      RiptideRotationUtil.Rotation rotation = activeGrimRotationValue(entity);
      return rotation == null ? vanillaLook : Vec3.directionFromRotation(rotation.pitch(), rotation.yaw());
   }

   private static RiptideRotationUtil.Rotation activeGrimRotationValue(LivingEntity entity) {
      if (entity == null || MC == null || entity != MC.player) {
         return null;
      } else if (BuiltinModules.ownsManualFastExp()) {
         return null;
      } else if (ModuleRegistry.get("scaffold") instanceof ScaffoldModule scaffold && scaffold.silentRotationApplies()) {
         RiptideRotationUtil.Rotation rotation = scaffold.grimSilentRotation;
         return rotation != null && scaffold.grimRotationResetTicks > 0 ? rotation : null;
      } else {
         return null;
      }
   }

   private static RiptideRotationUtil.Rotation activeGrimRotation(LocalPlayer player) {
      if (player == null || MC == null || player != MC.player) {
         return null;
      } else if (BuiltinModules.ownsManualFastExp()) {
         return null;
      } else {
         return ModuleRegistry.get("scaffold") instanceof ScaffoldModule scaffold && scaffold.silentRotationApplies() && scaffold.grimRotationResetTicks > 0
            ? scaffold.grimSilentRotation
            : null;
      }
   }

   private boolean grimRiseAllowed(boolean jumping) {
      boolean rising = grimRiseAllowed(jumping, MC.player == null || MC.player.onGround()) && this.grimCourseAscends();
      this.grimTraceRiseAllowed = rising;
      return rising;
   }

   static boolean grimRiseAllowed(boolean jumpKeyHeld, boolean onGround) {
      return jumpKeyHeld || !onGround;
   }

   public static boolean hasActiveSilentMovementRotation() {
      return MC != null && activeGrimRotation(MC.player) != null;
   }

   @Override
   public boolean shouldCancelAttack(HitResult hitResult) {
      return hitResult instanceof EntityHitResult && hasActiveSilentMovementRotation();
   }

   public static RiptideRotationUtil.Rotation activeOutgoingRotation() {
      return MC == null ? null : activeGrimRotation(MC.player);
   }

   public static RiptideRotationUtil.Rotation wireContinuityRotation() {
      RiptideRotationUtil.Rotation active = activeOutgoingRotation();
      if (active == null) {
         return null;
      } else {
         float yaw = Float.isNaN(grimSentYaw) ? active.yaw() : grimContinuousYaw(grimSentYaw, active.yaw());
         return new RiptideRotationUtil.Rotation(yaw, active.pitch());
      }
   }

   public static Vec3 silentViewVector(LocalPlayer player, Vec3 original) {
      if (ModuleRegistry.get("scaffold") instanceof ScaffoldModule scaffold && !scaffold.isTellyMode()) {
         RiptideRotationUtil.Rotation rotation = activeGrimRotation(player);
         return rotation == null ? original : Vec3.directionFromRotation(rotation.pitch(), rotation.yaw());
      } else {
         return original;
      }
   }

   private boolean isGrimMode() {
      return !this.isRageMode() && !this.isTellyMode() && !this.isAndromedaMode();
   }

   private boolean isRageMode() {
      String mode = this.choice("mode");
      return "Rage".equals(mode) || "Fast".equals(mode);
   }

   private boolean isTellyMode() {
      return "Telly".equals(this.choice("mode"));
   }

   private boolean isGrimFamily() {
      return this.isGrimMode();
   }

   private boolean tellyUsesRealClicks() {
      return this.isTellyMode() && this.tellyOwnsInput;
   }

   private boolean ownsRealClickPipeline() {
      return this.isGrimFamily() || this.tellyUsesRealClicks();
   }

   private boolean grimRemoveLimits() {
      return this.isGrimFamily() && this.bool("remove-limits");
   }

   private boolean usesSilentRotationPath() {
      return this.isGrimFamily() || this.isTellyMode() && this.tellyOwnsInput;
   }

   private boolean silentRotationApplies() {
      return silentCorrectionApplies(this.grimWindingDown, this.isEnabled(), this.usesSilentRotationPath(), this.canRun());
   }

   static boolean silentCorrectionApplies(boolean windingDown, boolean enabled, boolean silentPath, boolean canRun) {
      return windingDown || enabled && silentPath && canRun;
   }

   static boolean silentCorrectionOwnsInput(boolean enabled, boolean canRun) {
      return enabled && canRun;
   }

   static boolean sameGrimCourse(Vec3 previous, Vec3 current) {
      if (previous != null && current != null && !(previous.horizontalDistanceSqr() <= 1.0E-12) && !(current.horizontalDistanceSqr() <= 1.0E-12)) {
         Vec3 first = new Vec3(previous.x, 0.0, previous.z).normalize();
         Vec3 second = new Vec3(current.x, 0.0, current.z).normalize();
         return first.dot(second) >= 0.999;
      } else {
         return false;
      }
   }

   private static double courseSignedAngleDegrees(Vec3 from, Vec3 to) {
      double cross = from.x * to.z - from.z * to.x;
      double dot = from.x * to.x + from.z * to.z;
      return Math.toDegrees(Math.atan2(cross, dot));
   }

   private boolean grimBridgingContext(Vec3 position, ScaffoldModule.MovementLine line) {
      if (this.grimActiveRowLock() != null) {
         return true;
      } else {
         int since = RiptideSharedState.get().getClientTickCounter() - this.lastGrimPlacementTick;
         if (since >= 0 && since <= 20) {
            return true;
         } else {
            Vec3 heading = this.grimBridgingHeading(line);
            return heading != null && this.grimVoidAhead(position, heading, 1.0);
         }
      }
   }

   private Vec3 grimBridgingHeading(ScaffoldModule.MovementLine line) {
      Vec3 velocity = MC.player.getDeltaMovement();
      Vec3 horizontal = new Vec3(velocity.x, 0.0, velocity.z);
      if (horizontal.length() >= 0.05) {
         return horizontal.normalize();
      } else if (line != null && !(line.direction().horizontalDistanceSqr() <= 1.0E-8)) {
         Vec3 direction = line.direction();
         return new Vec3(direction.x, 0.0, direction.z).normalize();
      } else {
         return null;
      }
   }

   private boolean grimLateralBrink() {
      if (MC.player == null || MC.level == null || !MC.player.onGround()) {
         return false;
      } else if (this.grimNoFootingUnderfoot()) {
         return true;
      } else if (this.grimWalkOffNextTick()) {
         return true;
      } else if (this.grimCourseDiverged()) {
         return true;
      } else {
         ScaffoldModule.MovementLine line = this.currentMovementLine;
         if (line != null && !(line.direction().horizontalDistanceSqr() <= 1.0E-8)) {
            Vec3 position = MC.player.position();
            if (Math.abs(grimLaneError(line, position)) <= 0.45) {
               return false;
            } else {
               Vec3 probe = grimLateralDriftProbe(line, position, 0.35);
               return !this.hasSupportBelow(probe.x, position.y, probe.z, 1);
            }
         } else {
            return false;
         }
      }
   }

   private boolean grimWalkOffNextTick() {
      if (MC.player != null && MC.level != null && MC.player.onGround()) {
         Vec3 velocity = MC.player.getDeltaMovement();
         if (velocity.horizontalDistanceSqr() <= 1.0E-8) {
            return false;
         } else {
            Vec3 next = grimNextStepPosition(MC.player.position(), velocity);
            return grimNoFootingUnderfoot(this.grimFootingOverlap(next));
         }
      } else {
         return false;
      }
   }

   static Vec3 grimNextStepPosition(Vec3 position, Vec3 velocity) {
      return position != null && velocity != null ? position.add(velocity.x, 0.0, velocity.z) : position;
   }

   static Input grimCounterMovement(Input input) {
      return input == null ? null : new Input(input.backward(), input.forward(), input.right(), input.left(), input.jump(), input.shift(), false);
   }

   static double grimScoredGapMean(long[] gaps, long sinceLastMs, int window) {
      if (gaps != null && gaps.length != 0 && window > 0 && sinceLastMs >= 0L) {
         long[] clamped = new long[gaps.length];

         for (int i = 0; i < gaps.length; i++) {
            clamped[i] = Math.min(1000L, Math.max(0L, gaps[i]));
         }

         return grimPaceProspectiveMean(clamped, Math.min(1000L, sinceLastMs), window);
      } else {
         return Double.NaN;
      }
   }

   static boolean grimDiagonalClimbBrakeApplies(
      boolean grimFamily,
      boolean diagonalLane,
      boolean ascending,
      boolean directionalInput,
      boolean airborne,
      boolean rising,
      double laneSpeed,
      double scoredMeanMs,
      long sinceLastMs
   ) {
      if (!grimFamily || !diagonalLane || !ascending || !directionalInput || !airborne) {
         return false;
      } else if (!rising) {
         return false;
      } else if (laneSpeed < 0.03) {
         return false;
      } else if (sinceLastMs < 0L || sinceLastMs >= 1000L) {
         return false;
      } else {
         return Double.isNaN(scoredMeanMs) ? false : scoredMeanMs < 432.0;
      }
   }

   private boolean grimDiagonalClimbBrake(boolean directionalInput, boolean traced) {
      if (traced) {
         this.grimTraceDiagonalPaceMean = Double.NaN;
      }

      if (this.isGrimFamily() && MC.player != null) {
         Vec3 lane = this.currentMovementLine == null ? null : this.currentMovementLine.direction();
         if (!isGrimDiagonalDirection(lane)) {
            return false;
         } else {
            long[] gaps = new long[this.grimIntavePlaceGaps.size()];
            int index = 0;

            for (long gap : this.grimIntavePlaceGaps) {
               gaps[index++] = gap;
            }

            long sinceLast = this.grimIntavePlaceGap(System.nanoTime());
            double mean = grimScoredGapMean(gaps, sinceLast, 8);
            if (traced) {
               this.grimTraceDiagonalPaceMean = mean;
            }

            if (this.grimRemoveLimits()) {
               return false;
            } else {
               Vec3 velocity = MC.player.getDeltaMovement();
               double length = Math.sqrt(lane.x * lane.x + lane.z * lane.z);
               double laneSpeed = length < 1.0E-6 ? 0.0 : (velocity.x * lane.x + velocity.z * lane.z) / length;
               return grimDiagonalClimbBrakeApplies(
                  true, true, this.grimCourseAscends(), directionalInput, !MC.player.onGround(), velocity.y > 0.0, laneSpeed, mean, sinceLast
               );
            }
         }
      } else {
         return false;
      }
   }

   private double grimLandingCarry(Vec3 landing, int row) {
      if (landing != null && MC.level != null) {
         double[] point = new double[]{landing.x, landing.z};
         double best = Double.NaN;
         int minX = Mth.floor(landing.x - 0.29);
         int maxX = Mth.floor(landing.x + 0.29);
         int minZ = Mth.floor(landing.z - 0.29);
         int maxZ = Mth.floor(landing.z + 0.29);

         for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
               BlockPos cell = new BlockPos(x, row, z);
               if (!MC.level.isOutsideBuildHeight(cell) && this.grimLandingRowCellHolds(cell)) {
                  double overlap = grimLandingMinOverlap(point, cell);
                  if (Double.isNaN(best) || overlap > best) {
                     best = overlap;
                  }
               }
            }
         }

         return best;
      } else {
         return Double.NaN;
      }
   }

   private boolean grimLandingRowCellHolds(BlockPos cell) {
      BlockState state = MC.level.getBlockState(cell);
      if (this.isSolidSupport(state, cell)) {
         return true;
      } else if (!state.canBeReplaced()) {
         return false;
      } else {
         BlockPos below = cell.below();
         return !MC.level.isOutsideBuildHeight(below) && this.isSolidSupport(MC.level.getBlockState(below), below);
      }
   }

   private boolean grimArcBrake(boolean directionalInput, boolean traced) {
      if (traced) {
         this.grimTraceArcTravel = 0.0;
         this.grimTraceArcCarry = "--";
         this.grimTraceArcStand = "--";
      }

      if (this.grimRemoveLimits()) {
         if (traced) {
            this.grimTraceArcStand = "off";
         }

         return false;
      } else if (this.isGrimFamily() && MC.player != null && !MC.player.onGround()) {
         if (directionalInput && this.grimCourseAscends()) {
            BlockPos support = this.grimLaunchReservedSupport;
            if (support == null) {
               if (traced) {
                  this.grimTraceArcStand = "nosup";
               }

               return false;
            } else {
               Vec3 velocity = MC.player.getDeltaMovement();
               ScaffoldModule.MovementLine line = this.currentMovementLine;
               Vec3 lane = line == null ? null : line.direction();
               if (lane != null) {
                  double length = Math.sqrt(lane.x * lane.x + lane.z * lane.z);
                  if (length > 1.0E-6 && (velocity.x * lane.x + velocity.z * lane.z) / length < 0.03) {
                     if (traced) {
                        this.grimTraceArcStand = "speed";
                     }

                     return false;
                  }
               }

               Vec3 landing = grimDescentCrossing(MC.player.position(), velocity, support.getY() + 2.0, 24, 0.02);
               if (landing == null) {
                  if (traced) {
                     this.grimTraceArcStand = "noland";
                  }

                  return false;
               } else {
                  Vec3 start = this.grimArcTravelStart;
                  if (traced) {
                     this.grimTraceArcTravel = start == null ? 0.0 : Math.hypot(landing.x - start.x, landing.z - start.z);
                  }

                  double carry = this.grimLandingCarry(landing, support.getY() + 1);
                  if (traced) {
                     this.grimTraceArcCarry = Double.isNaN(carry) ? "bare" : String.format(Locale.ROOT, "%+.2f", carry);
                  }

                  if (carry >= 0.15) {
                     return false;
                  } else if (this.grimCrossingStanddown(velocity.y > 0.0, lane)) {
                     if (traced) {
                        this.grimTraceArcStand = "xing";
                     }

                     return false;
                  } else {
                     return true;
                  }
               }
            }
         } else {
            if (traced) {
               this.grimTraceArcStand = "noasc";
            }

            return false;
         }
      } else {
         if (traced) {
            this.grimTraceArcStand = "nogrim";
         }

         return false;
      }
   }

   private boolean grimNoFootingUnderfoot() {
      return MC.player != null && MC.level != null && MC.player.onGround() ? grimNoFootingUnderfoot(this.grimFootingOverlap(MC.player.position())) : false;
   }

   static boolean grimNoFootingUnderfoot(double overlap) {
      return overlap <= 0.0;
   }

   private double grimFootingOverlap(Vec3 point) {
      return this.grimFootingOverlap(point, Mth.floor(point.y) - 1);
   }

   private double grimFootingOverlap(Vec3 point, int y) {
      double half = MC.player.getBbWidth() * 0.5;
      double minX = point.x - half;
      double maxX = point.x + half;
      double minZ = point.z - half;
      double maxZ = point.z + half;
      double best = 0.0;

      for (int cx = Mth.floor(minX); cx <= Mth.floor(maxX); cx++) {
         for (int cz = Mth.floor(minZ); cz <= Mth.floor(maxZ); cz++) {
            BlockPos pos = new BlockPos(cx, y, cz);
            if (!MC.level.isOutsideBuildHeight(pos) && this.isSolidSupport(MC.level.getBlockState(pos), pos)) {
               double overlapX = Math.min(maxX, cx + 1.0) - Math.max(minX, (double)cx);
               double overlapZ = Math.min(maxZ, cz + 1.0) - Math.max(minZ, (double)cz);
               best = Math.max(best, Math.min(overlapX, overlapZ));
            }
         }
      }

      return best;
   }

   private boolean grimFootingOwed() {
      if (MC.player != null && MC.level != null && MC.player.onGround()) {
         BlockPos footing = this.grimEffectiveFootCell().below();
         if (!MC.level.getBlockState(footing).canBeReplaced()) {
            return false;
         } else {
            Vec3 position = MC.player.position();
            Vec3 lead = this.grimLeadStep().scale(grimWalkLeadTicks(this.grimPaceWaitTicks));
            return grimFootingOwed(this.grimFootingOverlap(position), this.grimFootingOverlap(position.add(lead)));
         }
      } else {
         return false;
      }
   }

   static boolean grimFootingOwed(double overlap, double leadOverlap) {
      return overlap <= 0.3 && leadOverlap <= overlap;
   }

   static int grimWalkLeadTicks(int paceWaitTicks) {
      return Mth.clamp(1 + paceWaitTicks, 1, 4);
   }

   private boolean grimRecentlyRescued() {
      int since = RiptideSharedState.get().getClientTickCounter() - this.grimLastRescueTick;
      return since >= 0 && since <= 6;
   }

   private boolean grimCourseDiverged() {
      ScaffoldModule.MovementLine line = this.currentMovementLine;
      if (line != null && !(line.direction().horizontalDistanceSqr() <= 1.0E-8)) {
         Vec3 velocity = MC.player.getDeltaMovement();
         Vec3 horizontal = new Vec3(velocity.x, 0.0, velocity.z);
         if (horizontal.length() < 0.05) {
            return false;
         } else {
            return Math.abs(courseSignedAngleDegrees(horizontal, line.direction())) <= 60.0 ? false : this.grimBridgingContext(MC.player.position(), line);
         }
      } else {
         return false;
      }
   }

   static double grimLaneError(ScaffoldModule.MovementLine line, Vec3 position) {
      Vec3 direction = line.direction().normalize();
      Vec3 left = new Vec3(direction.z, 0.0, -direction.x);
      return nearestPointOnLine(line, position).subtract(position).dot(left);
   }

   static Vec3 grimLateralDriftProbe(ScaffoldModule.MovementLine line, Vec3 position, double distance) {
      Vec3 direction = line.direction().normalize();
      Vec3 left = new Vec3(direction.z, 0.0, -direction.x);
      double error = grimLaneError(line, position);
      return position.add(left.scale(error > 0.0 ? -distance : distance));
   }

   private boolean grimVoidAhead(Vec3 position, Vec3 horizontal, double speed) {
      Vec3 step = horizontal.scale(1.0 / speed);

      for (double distance = 0.5; distance <= 2.5; distance += 0.5) {
         BlockPos feet = BlockPos.containing(position.add(step.scale(distance)));
         boolean supported = this.isSolidSupport(MC.level.getBlockState(feet), feet)
            || this.isSolidSupport(MC.level.getBlockState(feet.below()), feet.below())
            || this.isSolidSupport(MC.level.getBlockState(feet.below(2)), feet.below(2));
         if (!supported) {
            return true;
         }
      }

      return false;
   }

   private Input transformSilentMovementInput(Input input) {
      boolean tellyWindDown = this.grimWindingDown && RiptideHumanRotation.isInitialized(this.tellyStream);
      if ((this.usesSilentRotationPath() || tellyWindDown) && this.grimSilentRotation != null && this.grimRotationResetTicks > 0) {
         if (this.isTellyMode()) {
            return this.transformSilentMovementInputStable(input, MC.player.getYRot(), this.grimSilentRotation.yaw());
         } else {
            Input result = transformSilentMovementInput(input, MC.player.getYRot(), this.grimSilentRotation.yaw());
            if (result != null && !result.forward() && hasDirectionalInput(input)) {
               this.grimSprintNoForwardTick = RiptideSharedState.get().getClientTickCounter();
            }

            return result;
         }
      } else {
         return input;
      }
   }

   static float grimInputWorldYaw(float forward, float sideways, float referenceYaw) {
      return Mth.wrapDegrees(referenceYaw - (float)Math.toDegrees(Math.atan2(sideways, forward)));
   }

   static boolean grimInputSteering(Input physical) {
      return physical != null && physical.left() != physical.right();
   }

   static float grimSnapYawToLane(float laneYaw, float freeYaw) {
      float off = Mth.wrapDegrees(freeYaw - laneYaw);
      return Mth.wrapDegrees(laneYaw + Math.round(off / 45.0F) * 45.0F);
   }

   static float grimLaneOctantResidual(float laneYaw, float emittedYaw) {
      float off = Mth.wrapDegrees(emittedYaw - laneYaw);
      return Mth.wrapDegrees(off - Math.round(off / 45.0F) * 45.0F);
   }

   static boolean grimLaneAnchorStandsDown(float bias) {
      return Math.abs(bias) > 37.5F;
   }

   private float grimLaneCentringBias() {
      ScaffoldModule.MovementLine line = this.currentMovementLine;
      if (line != null && MC.player != null) {
         Vec3 direction = line.direction();
         if (direction.horizontalDistanceSqr() <= 1.0E-8) {
            this.grimLaneCorrectStandDown();
            return 0.0F;
         } else {
            Vec3 unit = direction.normalize();
            Vec3 left = new Vec3(unit.z, 0.0, -unit.x);
            double offset = grimLaneError(line, MC.player.position());
            int previous = this.grimLaneCorrectSide;
            this.grimLaneCorrectSide = grimLaneCorrectLatch(
               previous, this.grimLaneCorrectHoldTicks, this.grimLaneCorrectLockTicks, offset, MC.player.getDeltaMovement().dot(left)
            );
            if (this.grimLaneCorrectSide == 0) {
               if (previous != 0) {
                  this.grimLaneCorrectHoldTicks = 0;
                  this.grimLaneCorrectLockTicks = 3;
               } else {
                  this.grimLaneCorrectStandDown();
               }

               return 0.0F;
            } else {
               this.grimLaneCorrectHoldTicks++;
               this.grimLaneCorrectLockTicks = 0;
               double demand = Math.max(Math.abs(offset), 0.06) * this.grimLaneCorrectSide;
               return grimLaneInputTarget(unit, left.scale(demand), Vec3.ZERO, 0.0, 0.23);
            }
         }
      } else {
         this.grimLaneCorrectStandDown();
         return 0.0F;
      }
   }

   private void grimLaneCorrectStandDown() {
      this.grimLaneCorrectSide = 0;
      this.grimLaneCorrectHoldTicks = 0;
      if (this.grimLaneCorrectLockTicks > 0) {
         this.grimLaneCorrectLockTicks--;
      }
   }

   static int grimLaneCorrectLatch(int side, int heldTicks, int lockTicks, double offset, double perpendicularVelocity) {
      double predicted = offset - perpendicularVelocity * 4.0;
      if (side != 0) {
         if (Math.abs(offset) <= 0.06) {
            return 0;
         } else if (heldTicks >= 2) {
            return 0;
         } else {
            double settles = offset - perpendicularVelocity * 2.2;
            return side * settles <= 0.06 ? 0 : side;
         }
      } else if (lockTicks > 0) {
         return 0;
      } else if (Math.abs(predicted) < 0.15) {
         return 0;
      } else if (Math.abs(offset) <= 0.06) {
         return 0;
      } else {
         return offset > 0.0 ? 1 : -1;
      }
   }

   private Input transformGrimLegitInput(Input input, Input physical) {
      if (this.usesSilentRotationPath() && this.grimSilentRotation != null && this.grimRotationResetTicks > 0) {
         float forward = inputImpulse(input.forward(), input.backward());
         float sideways = inputImpulse(input.left(), input.right());
         float emitted = this.grimSilentRotation.yaw();
         this.grimTraceLaneAnchor = "cam";
         this.grimLaneInputBias = 0.0F;
         this.grimLaneSweepActive = false;
         if (grimInputSteering(physical)) {
            this.grimTraceLaneAnchor = "steer";
            this.grimLaneCoastTicks = 0;
            this.grimLaneCorrectStandDown();
         } else if (forward == 0.0F && sideways == 0.0F) {
            this.grimLaneCoastTicks = 0;
            this.grimLaneCorrectStandDown();
         } else if (this.grimLaneStep() != -1) {
            float laneYaw = this.grimLaneStepYaw();
            float bias = Mth.wrapDegrees(laneYaw - grimInputWorldYaw(forward, sideways, MC.player.getYRot()));
            if (grimLaneAnchorStandsDown(bias)) {
               this.grimLaneInputBias = Mth.clamp(this.grimLaneCentringBias(), -38.0F, 38.0F);
               this.grimLaneCoastTicks = 0;
            } else {
               this.grimLaneInputBias = bias + Mth.clamp(this.grimLaneCentringBias(), -38.0F, 38.0F);
               this.grimTraceLaneAnchor = "lane";
               float residual = Math.abs(grimLaneOctantResidual(laneYaw, emitted));
               if (residual > 2.0F) {
                  this.grimLaneSweepActive = ++this.grimLaneSweepTicks <= 8;
               } else {
                  this.grimLaneSweepTicks = 0;
               }

               if (residual > 12.0F) {
                  if (++this.grimLaneCoastTicks <= 6) {
                     Input coast = grimStripLateral(this.grimCoastInput(input));
                     if (coast != null) {
                        this.grimTraceLaneAnchor = this.grimLaneSweepActive ? "sweep" : "coast";
                        if (!coast.forward()) {
                           this.grimSprintNoForwardTick = RiptideSharedState.get().getClientTickCounter();
                        }

                        return coast;
                     }
                  }
               } else {
                  this.grimLaneCoastTicks = 0;
               }

               if (this.grimLaneSweepActive) {
                  this.grimTraceLaneAnchor = "sweep";
               }
            }
         }

         Input result = this.transformSilentMovementInputStable(input, MC.player.getYRot(), emitted);
         if (this.grimLaneSweepActive) {
            result = grimStripLateral(result);
         }

         if (result != null && !result.forward() && hasDirectionalInput(input)) {
            this.grimSprintNoForwardTick = RiptideSharedState.get().getClientTickCounter();
         }

         return result;
      } else {
         return input;
      }
   }

   static Input grimStripLateral(Input input) {
      return input != null && input.left() != input.right()
         ? new Input(input.forward(), input.backward(), false, false, input.jump(), input.shift(), silentSprintAllowed(input, input.forward()))
         : input;
   }

   static boolean silentSprintAllowed(Input input, boolean emittedForward) {
      return input != null && input.sprint() && emittedForward;
   }

   private Input grimCoastInput(Input input) {
      return this.grimInputForwardOctant == 0 && this.grimInputSidewaysOctant == 0
         ? null
         : new Input(
            this.grimInputForwardOctant > 0,
            this.grimInputForwardOctant < 0,
            this.grimInputSidewaysOctant > 0,
            this.grimInputSidewaysOctant < 0,
            input.jump(),
            input.shift(),
            silentSprintAllowed(input, this.grimInputForwardOctant > 0)
         );
   }

   public static boolean blocksSprintWithoutForward() {
      return ModuleRegistry.get("scaffold") instanceof ScaffoldModule scaffold
         && scaffold.grimSprintNoForwardTick == RiptideSharedState.get().getClientTickCounter();
   }

   private void updateTellyLaneBias(float courseYaw) {
      this.tellyLaneBias = approachTellyLaneBias(this.tellyLaneBias, this.tellyLaneBiasTarget(courseYaw));
      this.grimLaneInputBias = this.tellyLaneBias;
   }

   private float tellyLaneBiasTarget(float courseYaw) {
      if (MC.player != null && this.tellyLastBridge != null && !Float.isNaN(this.tellyAnchorYaw)) {
         double error = this.tellyLaneCenter - laneCoordinate(MC.player.position(), this.tellyAnchorYaw);
         if (Double.isFinite(error) && !(Math.abs(error) > 0.55)) {
            Vec3 forward = this.tellyForwardVector();
            if (forward.horizontalDistanceSqr() <= 1.0E-8) {
               return 0.0F;
            } else {
               Vec3 left = this.tellyLeftVector();
               return grimLaneInputTarget(forward.normalize(), left.scale(error), MC.player.getDeltaMovement());
            }
         } else {
            return 0.0F;
         }
      } else {
         return 0.0F;
      }
   }

   static float approachTellyLaneBias(float current, float target) {
      float wanted = Mth.clamp(target, -15.0F, 15.0F);
      float step = Mth.clamp(wanted - current, -3.0F, 3.0F);
      return Mth.clamp(current + step, -15.0F, 15.0F);
   }

   private Input transformTellyAuthoredInput(Input input) {
      if (this.usesSilentRotationPath() && this.grimSilentRotation != null && this.grimRotationResetTicks > 0) {
         float courseYaw = this.tellyGroundSteeringActive ? Mth.wrapDegrees(this.tellyAnchorYaw + this.tellyGroundSteerOffset) : this.tellyAnchorYaw;
         this.updateTellyLaneBias(courseYaw);
         return this.transformSilentMovementInputStable(input, courseYaw, this.grimSilentRotation.yaw());
      } else {
         return input;
      }
   }

   public static Input transformSilentMovementInput(Input input, float playerYaw, float silentYaw) {
      if (input == null) {
         return null;
      } else {
         float forward = inputImpulse(input.forward(), input.backward());
         float sideways = inputImpulse(input.left(), input.right());
         float deltaYaw = (playerYaw - silentYaw) * (float) (Math.PI / 180.0);
         float cosine = Mth.cos(deltaYaw);
         float sine = Mth.sin(deltaYaw);
         float transformedSideways = sideways * cosine - forward * sine;
         float transformedForward = forward * cosine + sideways * sine;
         int roundedSideways = Math.round(transformedSideways);
         int roundedForward = Math.round(transformedForward);
         return new Input(
            roundedForward > 0,
            roundedForward < 0,
            roundedSideways > 0,
            roundedSideways < 0,
            input.jump(),
            input.shift(),
            silentSprintAllowed(input, roundedForward > 0)
         );
      }
   }

   private Input transformSilentMovementInputStable(Input input, float playerYaw, float silentYaw) {
      if (input == null) {
         return null;
      } else {
         float forward = inputImpulse(input.forward(), input.backward());
         float sideways = inputImpulse(input.left(), input.right());
         if (forward == 0.0F && sideways == 0.0F) {
            this.grimInputForwardOctant = 0;
            this.grimInputSidewaysOctant = 0;
            this.grimInputSidewaysHold = 0;
            this.grimInputSidewaysFromCorrection = false;
            this.grimInputRawForward = 0.0F;
            this.grimInputRawSideways = 0.0F;
            this.grimInputDeltaYaw = Float.NaN;
            return input;
         } else {
            float deltaDegrees = Mth.wrapDegrees(playerYaw + this.grimLaneInputBias - silentYaw);
            boolean referenceMoved = Float.isFinite(this.grimInputDeltaYaw) && Math.abs(Mth.wrapDegrees(deltaDegrees - this.grimInputDeltaYaw)) >= 22.5F;
            this.grimInputDeltaYaw = deltaDegrees;
            float deltaYaw = deltaDegrees * (float) (Math.PI / 180.0);
            float cosine = Mth.cos(deltaYaw);
            float sine = Mth.sin(deltaYaw);
            float transformedSideways = sideways * cosine - forward * sine;
            float transformedForward = forward * cosine + sideways * sine;
            int steadyForward = octantWithHysteresis(transformedForward, this.grimInputForwardOctant);
            int steadySideways = this.dwellSideways(Math.round(transformedSideways), forward, sideways, referenceMoved);
            if (steadyForward == 0 && steadySideways == 0) {
               steadyForward = Math.round(transformedForward);
               steadySideways = Math.round(transformedSideways);
            }

            this.grimInputForwardOctant = steadyForward;
            this.grimInputSidewaysOctant = steadySideways;
            return new Input(
               steadyForward > 0,
               steadyForward < 0,
               steadySideways > 0,
               steadySideways < 0,
               input.jump(),
               input.shift(),
               silentSprintAllowed(input, steadyForward > 0)
            );
         }
      }
   }

   private int dwellSideways(int requested, float rawForward, float rawSideways, boolean referenceMoved) {
      boolean playerChangedKeys = rawForward != this.grimInputRawForward || rawSideways != this.grimInputRawSideways;
      this.grimInputRawForward = rawForward;
      this.grimInputRawSideways = rawSideways;
      if (this.grimWindingDown) {
         this.grimInputSidewaysHold = 0;
         this.grimInputSidewaysFromCorrection = false;
         return requested;
      } else if (requested == this.grimInputSidewaysOctant) {
         this.grimInputSidewaysHold++;
         if (this.grimLaneCorrectSide != 0 && requested != 0) {
            this.grimInputSidewaysFromCorrection = true;
         }

         return requested;
      } else if (!dwellReleases(
         playerChangedKeys,
         this.grimInputSidewaysFromCorrection && this.grimLaneCorrectSide == 0,
         this.grimInputSidewaysHold,
         this.grimLaneInputBias,
         referenceMoved
      )) {
         this.grimInputSidewaysHold++;
         return this.grimInputSidewaysOctant;
      } else {
         this.grimInputSidewaysHold = 0;
         this.grimInputSidewaysFromCorrection = this.grimLaneCorrectSide != 0 && requested != 0;
         return requested;
      }
   }

   static boolean dwellReleases(boolean playerChangedKeys, boolean correctionEnded, int hold, float bias, boolean referenceMoved) {
      return playerChangedKeys || correctionEnded || referenceMoved || hold >= 4 || Math.abs(bias) >= 36.0F;
   }

   static int octantWithHysteresis(double component, int previous) {
      double magnitude = Math.abs(component);
      double threshold = previous == 0 ? 0.62 : 0.38;
      if (magnitude < threshold) {
         return 0;
      } else {
         return component < 0.0 ? -1 : 1;
      }
   }

   static double emittedOctantDegrees(int forwardImpulse, int sidewaysImpulse) {
      return forwardImpulse == 0 && sidewaysImpulse == 0 ? Double.NaN : Math.toDegrees(Math.atan2(-sidewaysImpulse, forwardImpulse));
   }

   static double tellyStrafeFlipMargin(double deltaDegrees) {
      if (!Double.isFinite(deltaDegrees)) {
         return Double.NaN;
      } else {
         double best = Double.MAX_VALUE;

         for (double boundary : TELLY_STRAFE_FLIP_BOUNDARIES) {
            best = Math.min(best, Math.abs(Mth.wrapDegrees(deltaDegrees - boundary)));
         }

         return best;
      }
   }

   private void resetGrimInputOctant() {
      this.grimInputForwardOctant = 0;
      this.grimInputSidewaysOctant = 0;
      this.grimInputSidewaysHold = 0;
      this.grimInputSidewaysFromCorrection = false;
      this.grimInputRawForward = 0.0F;
      this.grimInputRawSideways = 0.0F;
      this.grimLaneInputBias = 0.0F;
      this.grimLaneCoastTicks = 0;
      this.grimLaneSweepTicks = 0;
      this.grimLaneCorrectStandDown();
      this.grimLaneCorrectLockTicks = 0;
   }

   static float grimLaneInputTarget(Vec3 laneDirection, Vec3 laneOffset, Vec3 horizontalVel) {
      return grimLaneInputTarget(laneDirection, laneOffset, horizontalVel, 0.22, 1.1);
   }

   static float grimLaneInputTarget(Vec3 laneDirection, Vec3 laneOffset, Vec3 horizontalVel, double deadband, double lookahead) {
      Vec3 left = new Vec3(laneDirection.z, 0.0, -laneDirection.x);
      double error = laneOffset.dot(left) - horizontalVel.dot(left) * 4.0;
      if (Math.abs(error) < deadband) {
         return 0.0F;
      } else {
         Vec3 desired = laneDirection.scale(lookahead).add(left.scale(error));
         return desired.lengthSqr() <= 1.0E-12 ? 0.0F : (float)Mth.clamp(courseSignedAngleDegrees(laneDirection, desired), -38.0, 38.0);
      }
   }

   private static float inputImpulse(boolean positive, boolean negative) {
      if (positive == negative) {
         return 0.0F;
      } else {
         return positive ? 1.0F : -1.0F;
      }
   }

   private ScaffoldModule.GrimRowLock grimActiveRowLock() {
      if (MC.player != null && this.currentMovementLine != null) {
         Vec3 direction = this.currentMovementLine.direction();
         if (isGrimDiagonalDirection(direction)) {
            return null;
         } else {
            int stepX = horizontalStep(direction.x);
            int stepZ = horizontalStep(direction.z);
            if (stepX == 0 && stepZ == 0) {
               return null;
            } else {
               int since = RiptideSharedState.get().getClientTickCounter() - this.lastGrimPlacementTick;
               if (since >= 0 && since <= 10) {
                  BlockPos lastPlaced = this.lastPlacedBlocks.peekLast();
                  if (lastPlaced == null) {
                     return null;
                  } else if (!this.isSolidSupport(MC.level.getBlockState(lastPlaced), lastPlaced)) {
                     return null;
                  } else {
                     int rowY = grimLockRowFor(lastPlaced.getY(), this.grimOracleFootingRow());
                     if (BlockPos.containing(MC.player.position()).getY() - 1 < rowY) {
                        return null;
                     } else {
                        boolean xAxis = stepX != 0;
                        BlockPos playerCell = BlockPos.containing(MC.player.position());
                        return new ScaffoldModule.GrimRowLock(
                           xAxis,
                           xAxis ? lastPlaced.getZ() : lastPlaced.getX(),
                           rowY,
                           xAxis ? lastPlaced.getX() : lastPlaced.getZ(),
                           this.grimRowFrontierIndex(lastPlaced, xAxis, -1),
                           this.grimRowFrontierIndex(lastPlaced, xAxis, 1),
                           xAxis ? playerCell.getZ() : playerCell.getX()
                        );
                     }
                  }
               } else {
                  return null;
               }
            }
         }
      } else {
         return null;
      }
   }

   static int grimLockRowFor(int placedRow, int footingRow) {
      return footingRow == placedRow + 1 ? footingRow : placedRow;
   }

   private int grimRowFrontierIndex(BlockPos lastPlaced, boolean xAxis, int sign) {
      for (int step = 1; step <= 12; step++) {
         BlockPos pos = xAxis ? lastPlaced.offset(sign * step, 0, 0) : lastPlaced.offset(0, 0, sign * step);
         if (MC.level.isOutsideBuildHeight(pos)) {
            return Integer.MIN_VALUE;
         }

         if (!this.isSolidSupport(MC.level.getBlockState(pos), pos)) {
            return xAxis ? pos.getX() : pos.getZ();
         }
      }

      return Integer.MIN_VALUE;
   }

   static Vec3 grimDescentCrossing(Vec3 position, Vec3 velocity, double feetY, int maxTicks) {
      return grimDescentCrossing(position, velocity, feetY, maxTicks, 0.0);
   }

   static Vec3 grimDescentCrossing(Vec3 position, Vec3 velocity, double feetY, int maxTicks, double impulse) {
      double x = position.x;
      double y = position.y;
      double z = position.z;
      double vx = velocity.x;
      double vy = velocity.y;
      double vz = velocity.z;

      for (int tick = 0; tick < maxTicks; tick++) {
         double previousY = y;
         x += vx;
         y += vy;
         z += vz;
         if (vy < 0.0 && previousY >= feetY && y < feetY) {
            double t = (previousY - feetY) / (previousY - y);
            return new Vec3(x - vx + vx * t, feetY, z - vz + vz * t);
         }

         double speed = Math.sqrt(vx * vx + vz * vz);
         double push = speed <= 1.0E-6 ? 0.0 : impulse / speed;
         vx = vx * 0.91 + vx * push;
         vy = (vy - 0.08) * 0.98;
         vz = vz * 0.91 + vz * push;
      }

      return null;
   }

   private ScaffoldModule.PlacementTarget grimFootingRescueTarget(Vec3 predicted, ItemStack stack) {
      if (MC.player != null && MC.level != null) {
         Vec3 velocity = MC.player.getDeltaMovement();
         if (!MC.player.onGround() && velocity.y > 0.0) {
            return null;
         } else {
            Vec3 lane = this.currentMovementLine == null ? null : this.currentMovementLine.direction();
            boolean jumping = this.grimJumpKeyHeld();
            if (!MC.player.onGround()) {
               int row = this.grimBuiltFloorRow();
               Vec3 landing = grimDescentCrossing(MC.player.position(), velocity, row + 1.0, 12);
               if (landing != null) {
                  List<BlockPos> columns = grimCatchColumns(new Vec3(landing.x, row + 1.5, landing.z));
                  BlockPos centered = columns.get(0);
                  if (MC.level.isOutsideBuildHeight(centered)) {
                     return null;
                  } else if (this.isSolidSupport(MC.level.getBlockState(centered), centered)) {
                     return null;
                  } else {
                     for (BlockPos cell : columns) {
                        if (!MC.level.isOutsideBuildHeight(cell)) {
                           BlockState state = MC.level.getBlockState(cell);
                           if (state.canBeReplaced() && !this.isSolidSupport(state, cell)) {
                              ScaffoldModule.PlacementTarget target = this.planTargetForCandidate(cell, predicted, false, lane, jumping);
                              if (!this.grimPlannedFaceBlind(target) && target != null) {
                                 return target;
                              }
                           }
                        }
                     }

                     return null;
                  }
               } else {
                  return null;
               }
            } else {
               return this.grimBelowFeetCatchTarget(predicted, lane, jumping);
            }
         }
      } else {
         return null;
      }
   }

   private ScaffoldModule.PlacementTarget grimBelowFeetCatchTarget(Vec3 predicted, Vec3 lane, boolean jumping) {
      Vec3 position = MC.player.position();
      List<BlockPos> columns = grimCatchColumns(position);
      BlockPos centered = columns.get(0);
      if (MC.level.isOutsideBuildHeight(centered)) {
         return null;
      } else if (this.isSolidSupport(MC.level.getBlockState(centered), centered)) {
         return null;
      } else {
         for (BlockPos support : columns) {
            if (!MC.level.isOutsideBuildHeight(support)) {
               BlockState state = MC.level.getBlockState(support);
               if (state.canBeReplaced() && !this.isSolidSupport(state, support)) {
                  ScaffoldModule.PlacementTarget target = this.planTargetForCandidate(support, predicted, false, lane, jumping);
                  if (!this.grimPlannedFaceBlind(target) && target != null) {
                     return target;
                  }
               }
            }
         }

         return null;
      }
   }

   private boolean grimPlannedFaceBlind(ScaffoldModule.PlacementTarget target) {
      return target != null && MC.player != null && !grimLegRayLands(target, MC.player.getEyePosition(), this.grimLeadStep());
   }

   static List<BlockPos> grimCatchColumns(Vec3 position) {
      BlockPos centered = BlockPos.containing(position).below();
      List<BlockPos> columns = new ArrayList<>(4);
      columns.add(centered);
      double fx = position.x - Math.floor(position.x);
      double fz = position.z - Math.floor(position.z);
      int sx = fx < 0.3 ? -1 : (fx > 0.7 ? 1 : 0);
      int sz = fz < 0.3 ? -1 : (fz > 0.7 ? 1 : 0);
      double overlapX = sx == 0 ? 0.0 : (sx < 0 ? 0.3 - fx : fx - 0.7);
      double overlapZ = sz == 0 ? 0.0 : (sz < 0 ? 0.3 - fz : fz - 0.7);
      BlockPos xSide = sx == 0 ? null : centered.offset(sx, 0, 0);
      BlockPos zSide = sz == 0 ? null : centered.offset(0, 0, sz);
      BlockPos first = overlapX >= overlapZ ? xSide : zSide;
      BlockPos second = overlapX >= overlapZ ? zSide : xSide;
      if (first != null) {
         columns.add(first);
      }

      if (second != null) {
         columns.add(second);
      }

      if (sx != 0 && sz != 0) {
         columns.add(centered.offset(sx, 0, sz));
      }

      return columns;
   }

   private boolean grimTowerActive() {
      if (MC == null || MC.player == null || MC.options == null) {
         return false;
      } else if (!this.isGrimFamily() || !this.grimJumpKeyHeld()) {
         return false;
      } else if (!this.isValidBlock(this.planningStack())) {
         return false;
      } else {
         boolean directional = physicallyDown(MC.options.keyUp)
            || physicallyDown(MC.options.keyDown)
            || physicallyDown(MC.options.keyLeft)
            || physicallyDown(MC.options.keyRight);
         return !directional || MC.player.horizontalCollision;
      }
   }

   private boolean grimCourseAscends() {
      if (this.grimTowerActive()) {
         return true;
      } else {
         Vec3 lane = this.currentMovementLine == null ? null : this.currentMovementLine.direction();
         if (lane == null || isGrimDiagonalDirection(lane)) {
            return true;
         } else if (!this.grimPhysicalClimbIntent && !this.grimLaunchReservationAirborne) {
            int since = grimTicksSince(RiptideSharedState.get().getClientTickCounter(), this.grimLastRowGainTick);
            return since >= 0 && since <= 30;
         } else {
            return true;
         }
      }
   }

   private RiptideRotationUtil.Rotation grimTowerPreAimGoal() {
      BlockPos support = BlockPos.containing(MC.player.position()).below();
      float yaw = this.grimSteeredPostureYaw();
      float pitch = grimTopCrossingPitch(MC.player.getEyePosition(), support, yaw);
      if (Float.isNaN(pitch)) {
         BlockPos cell = BlockPos.containing(MC.player.position());
         Vec3 point = new Vec3(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
         pitch = RiptideRotationUtil.lookingAt(point, MC.player.getEyePosition()).pitch();
      }

      return new RiptideRotationUtil.Rotation(yaw, grimPlacementPitchCap(pitch));
   }

   private ScaffoldModule.PlacementTarget grimTowerTarget() {
      if (this.grimTowerActive() && !MC.player.onGround() && MC.level != null) {
         BlockPos cell = BlockPos.containing(MC.player.position());

         for (int depth = 0; depth < 3; depth++) {
            if (MC.level.isOutsideBuildHeight(cell)) {
               return null;
            }

            if (!MC.level.getBlockState(cell).canBeReplaced()) {
               return null;
            }

            BlockPos below = cell.below();
            if (MC.level.isOutsideBuildHeight(below)) {
               return null;
            }

            if (!MC.level.getBlockState(below).canBeReplaced()) {
               return this.planTargetForCandidate(cell, MC.player.position(), false, null, true, Direction.UP);
            }

            cell = below;
         }

         return null;
      } else {
         return null;
      }
   }

   private ScaffoldModule.PlacementTarget grimStaircaseRiserTarget() {
      this.grimTraceRiserFail = "--";
      if (MC.player != null && MC.level != null) {
         if (!MC.player.onGround() && this.grimJumpKeyHeld()) {
            if (!this.grimCourseAscends()) {
               this.grimTraceRiserFail = "flat";
               return null;
            } else {
               Vec3 position = MC.player.position();
               Vec3 velocity = MC.player.getDeltaMovement();
               Vec3 lane = this.currentMovementLine == null ? null : this.currentMovementLine.direction();
               StringBuilder fails = new StringBuilder();
               ScaffoldModule.PlacementTarget footing = null;
               BlockPos arcLanding = this.grimArcRiserSupport(this.grimOracleFootingRow());
               BlockPos ownSupport = this.grimOwnRiserSupport();

               for (BlockPos support : this.grimRiserSupportCandidates()) {
                  BlockPos riser = support.above();
                  if (!MC.level.isOutsideBuildHeight(riser)) {
                     boolean own = grimSameColumn(support, ownSupport) && lane == null;
                     String fail;
                     if (this.grimCellOnCooldown(riser)) {
                        fail = "dead";
                     } else if (!MC.level.getBlockState(riser).canBeReplaced()) {
                        fail = "occupied";
                     } else if (own ? !grimBoxOverColumn(position, support) : !this.grimArcLandsOnColumnLive(position, velocity, riser)) {
                        fail = "arc";
                        if (this.traceArmed() && !own) {
                           double[] landing = this.grimHeldArcLandingLive(position, velocity, velocity.y, riser.getY() + 1.0, false, this.grimArcSneakTicks());
                           fail = landing == null ? "arc(null)" : String.format(Locale.ROOT, "arc(ovl%.2f)", grimLandingMinOverlap(landing, riser));
                        }
                     } else if (!this.isSolidSupport(MC.level.getBlockState(support), support)) {
                        ScaffoldModule.PlacementTarget sideReach = this.planTargetForCandidate(riser, position, false, lane, true, null);
                        if (sideReach != null) {
                           this.grimTraceRiserFail = "--";
                           return sideReach;
                        }

                        if (footing == null && support.equals(arcLanding) && !this.grimCellOnCooldown(support)) {
                           footing = this.planTargetForCandidate(support, position, false, lane, true, null);
                        }

                        fail = footing == null ? "no-support" : "footing";
                     } else {
                        ScaffoldModule.PlacementTarget plan = this.planTargetForCandidate(riser, position, false, lane, true, Direction.UP);
                        if (plan != null) {
                           this.grimTraceRiserFail = "--";
                           return plan;
                        }

                        fail = "plan";
                     }

                     if (fails.length() > 0) {
                        fails.append(',');
                     }

                     fails.append(support.getX()).append('/').append(support.getZ()).append(':').append(fail);
                  }
               }

               if (this.traceArmed() && fails.length() > 0) {
                  fails.append(";own=").append(ownSupport == null ? "--" : ownSupport.getX() + "/" + ownSupport.getZ());
                  fails.append(";land=").append(arcLanding == null ? "--" : arcLanding.getX() + "/" + arcLanding.getZ());
               }

               this.grimTraceRiserFail = fails.length() == 0 ? "none" : fails.toString();
               return footing;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static double grimLandingMinOverlap(double[] landing, BlockPos cell) {
      double overlapX = Math.min(landing[0] + 0.29, cell.getX() + 1.0) - Math.max(landing[0] - 0.29, (double)cell.getX());
      double overlapZ = Math.min(landing[1] + 0.29, cell.getZ() + 1.0) - Math.max(landing[1] - 0.29, (double)cell.getZ());
      return Math.min(overlapX, overlapZ);
   }

   private BlockPos grimArcRiserSupport(int footingRow) {
      if (MC.player == null) {
         return null;
      } else {
         Vec3 position = MC.player.position();
         Vec3 velocity = MC.player.getDeltaMovement();
         boolean grounded = MC.player.onGround();
         double[] landing = this.grimHeldArcLandingLive(position, velocity, grounded ? 0.42 : velocity.y, footingRow + 2.0, grounded, this.grimArcSneakTicks());
         return landing == null ? null : new BlockPos(Mth.floor(landing[0]), footingRow, Mth.floor(landing[1]));
      }
   }

   private BlockPos grimArcLandingSupport(int footingRow) {
      if (MC.player == null) {
         return null;
      } else {
         Vec3 position = MC.player.position();
         Vec3 velocity = MC.player.getDeltaMovement();
         boolean grounded = MC.player.onGround();
         double takeoffVy = grounded ? 0.42 : velocity.y;
         double[] landing = null;
         if (this.grimCourseAscends()) {
            landing = this.grimHeldArcLandingLive(position, velocity, takeoffVy, footingRow + 2.0, grounded, this.grimArcSneakTicks());
         }

         if (landing == null) {
            landing = this.grimHeldArcLandingLive(position, velocity, takeoffVy, footingRow + 1.0, grounded, this.grimArcSneakTicks());
         }

         return landing == null ? null : new BlockPos(Mth.floor(landing[0]), footingRow, Mth.floor(landing[1]));
      }
   }

   private List<BlockPos> grimRiserSupportCandidates() {
      List<BlockPos> candidates = new ArrayList<>(6);
      int footingRow = this.grimOracleFootingRow();
      BlockPos foot = this.grimEffectiveFootCell();
      BlockPos own = new BlockPos(foot.getX(), footingRow, foot.getZ());
      BlockPos landing = this.grimArcRiserSupport(footingRow);
      candidates.addAll(grimPrimaryRiserSupports(own, landing, isGrimDiagonalDirection(this.grimLaneStepDirection())));
      Vec3 step = this.grimLaneStepDirection();
      BlockPos ahead = new BlockPos(own.getX() + horizontalStep(step.x), footingRow, own.getZ() + horizontalStep(step.z));
      if (!ahead.equals(own) && !candidates.contains(ahead)) {
         candidates.add(ahead);
      }

      int tried = 0;

      for (Iterator<BlockPos> recent = this.lastPlacedBlocks.descendingIterator(); recent.hasNext() && tried < 3; tried++) {
         BlockPos support = recent.next();
         if (!candidates.contains(support)) {
            candidates.add(support);
         }
      }

      return candidates;
   }

   static List<BlockPos> grimPrimaryRiserSupports(BlockPos own, BlockPos landing, boolean diagonal) {
      List<BlockPos> ordered = new ArrayList<>(2);
      BlockPos first = diagonal ? landing : own;
      BlockPos second = diagonal ? own : landing;
      if (first != null) {
         ordered.add(first);
      }

      if (second != null && !ordered.contains(second)) {
         ordered.add(second);
      }

      return ordered;
   }

   static List<BlockPos> grimLandingConnectorCandidates(BlockPos landing, Vec3 laneDirection) {
      List<BlockPos> connectors = new ArrayList<>(2);
      if (landing != null && laneDirection != null) {
         int stepX = horizontalStep(laneDirection.x);
         int stepZ = horizontalStep(laneDirection.z);
         if (stepX != 0) {
            connectors.add(landing.offset(-stepX, 0, 0));
         }

         if (stepZ != 0) {
            BlockPos zLeg = landing.offset(0, 0, -stepZ);
            if (!connectors.contains(zLeg)) {
               connectors.add(zLeg);
            }
         }

         return connectors;
      } else {
         return connectors;
      }
   }

   private ScaffoldModule.PlacementTarget grimRiserSideFallback(BlockPos riser, BlockPos landing, Vec3 predicted, ItemStack stack, Vec3 lane) {
      if (riser != null && landing != null) {
         for (BlockPos connector : grimLandingConnectorCandidates(landing, lane)) {
            Direction face = grimRiserSideFace(landing, connector);
            if (face != null) {
               ScaffoldModule.PlacementTarget viaStep = this.grimPlanReservedCell(riser, predicted, stack, lane, face);
               if (viaStep != null) {
                  return viaStep;
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   static Direction grimRiserSideFace(BlockPos landing, BlockPos connector) {
      if (landing != null && connector != null) {
         int dx = landing.getX() - connector.getX();
         int dz = landing.getZ() - connector.getZ();
         if (dx != 0 && dz != 0) {
            return null;
         } else if (dx != 0) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
         } else if (dz != 0) {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private ScaffoldModule.PlacementTarget grimLaunchReservationTarget(Vec3 predicted, ItemStack stack) {
      if (MC.player != null && MC.level != null && this.grimPlanningJump() && this.currentMovementLine != null) {
         Vec3 lane = this.currentMovementLine.direction();
         BlockPos landing = this.grimLaunchReservedSupport;
         if (landing == null) {
            landing = this.grimArcLandingSupport(this.grimOracleFootingRow());
            if (landing == null || MC.level.isOutsideBuildHeight(landing)) {
               this.grimLaunchReservationStage = "unknown";
               return null;
            }

            this.grimLaunchReservedSupport = landing.immutable();
         }

         boolean supportSolid = this.solidAt(landing);
         int deficit = supportSolid ? 0 : this.grimCellDeficit(landing);
         BlockPos riser = landing.above();
         boolean riserSolid = !MC.level.isOutsideBuildHeight(riser) && this.solidAt(riser);
         ScaffoldModule.GrimReservationNeed need = grimReservationNeed(supportSolid, deficit, riserSolid);
         if (need == ScaffoldModule.GrimReservationNeed.READY || MC.level.isOutsideBuildHeight(riser)) {
            this.grimLaunchReservedConnector = null;
            this.grimLaunchReservationStage = "ready";
            return null;
         } else if (need == ScaffoldModule.GrimReservationNeed.RISER) {
            this.grimLaunchReservedConnector = null;
            if (!this.grimCourseAscends()) {
               this.grimLaunchReservedRiser = null;
               this.grimLaunchReservationStage = "ready";
               return null;
            } else {
               this.grimLaunchReservedRiser = riser.immutable();
               ScaffoldModule.PlacementTarget rise = this.grimPlanReservedCell(riser, predicted, stack, lane, Direction.UP);
               if (rise == null) {
                  rise = this.grimRiserSideFallback(riser, landing, predicted, stack, lane);
               }

               this.grimLaunchReservationStage = rise == null ? "riser-wait" : "riser";
               return rise;
            }
         } else if (need == ScaffoldModule.GrimReservationNeed.SUPPORT) {
            this.grimLaunchReservedConnector = null;
            ScaffoldModule.PlacementTarget step = this.grimClimbStepTarget(landing, predicted, stack, lane);
            if (step != null) {
               this.grimLaunchReservationStage = "step";
               return step;
            } else {
               ScaffoldModule.PlacementTarget support = this.grimPlanReservedCell(landing, predicted, stack, lane, null);
               this.grimLaunchReservationStage = support == null ? "support-wait" : "support";
               return support;
            }
         } else {
            List<ScaffoldModule.PlacementTarget> legs = new ArrayList<>(2);

            for (BlockPos connector : grimLandingConnectorCandidates(landing, lane)) {
               if (!MC.level.isOutsideBuildHeight(connector)
                  && !this.solidAt(connector)
                  && this.grimCellDeficit(connector) <= 1
                  && !grimCellBehind(MC.player.position(), lane, connector, 0.8)) {
                  ScaffoldModule.PlacementTarget leg = this.grimPlanReservedCell(connector, predicted, stack, lane, null);
                  if (leg != null) {
                     legs.add(leg);
                  }
               }
            }

            if (legs.isEmpty()) {
               this.grimLaunchReservedConnector = null;
               this.grimLaunchReservationStage = "leg-wait";
               return null;
            } else {
               legs.sort(grimSeedOrder(predicted, this.grimRestPoseGoal()));
               ScaffoldModule.PlacementTarget leg = grimNearestPlaneLeg(
                  legs, MC.player.getEyePosition(), this.grimLeadStep(), landing, this.grimLaunchReservedConnector
               );
               this.grimLaunchReservedConnector = leg.placedBlock().immutable();
               this.grimLaunchReservationStage = leg == legs.get(0) ? "leg" : "leg-plane";
               return leg;
            }
         }
      } else {
         this.grimLaunchReservationStage = "--";
         return null;
      }
   }

   static ScaffoldModule.PlacementTarget grimNearestPlaneLeg(List<ScaffoldModule.PlacementTarget> legs, Vec3 eye, BlockPos held) {
      ScaffoldModule.PlacementTarget best = legs.get(0);
      if (legs.size() >= 2 && best.face().getAxis().isHorizontal()) {
         double bestPast = grimEyePastPlane(eye, best.supportBlock(), best.face());

         for (int i = 1; i < legs.size(); i++) {
            ScaffoldModule.PlacementTarget leg = legs.get(i);
            if (leg.face().getAxis().isHorizontal()) {
               double past = grimEyePastPlane(eye, leg.supportBlock(), leg.face());
               if (past > bestPast) {
                  best = leg;
                  bestPast = past;
               }
            }
         }

         if (held != null && !best.placedBlock().equals(held)) {
            for (ScaffoldModule.PlacementTarget leg : legs) {
               if (leg.placedBlock().equals(held) && leg.face().getAxis().isHorizontal()) {
                  double heldPast = grimEyePastPlane(eye, leg.supportBlock(), leg.face());
                  if (!grimSwapAcrossClickMargin(bestPast, heldPast) && bestPast - heldPast <= 0.25) {
                     return leg;
                  }
                  break;
               }
            }

            return best;
         } else {
            return best;
         }
      } else {
         return best;
      }
   }

   static boolean grimSwapAcrossClickMargin(double bestPast, double heldPast) {
      return bestPast >= 0.05 && heldPast < 0.05;
   }

   static ScaffoldModule.PlacementTarget grimNearestPlaneLeg(List<ScaffoldModule.PlacementTarget> legs, Vec3 eye, Vec3 lead, BlockPos held) {
      return grimNearestPlaneLeg(legs, eye, lead, null, held);
   }

   static ScaffoldModule.PlacementTarget grimNearestPlaneLeg(List<ScaffoldModule.PlacementTarget> legs, Vec3 eye, Vec3 lead, BlockPos landing, BlockPos held) {
      List<ScaffoldModule.PlacementTarget> live = grimSightedLegs(legs, eye, lead);
      if (held == null) {
         ScaffoldModule.PlacementTarget successor = grimSuccessorReachableLeg(live, eye, lead, landing);
         if (successor != null) {
            return successor;
         }
      }

      return grimNearestPlaneLeg(live, eye, held);
   }

   static ScaffoldModule.PlacementTarget grimSuccessorReachableLeg(List<ScaffoldModule.PlacementTarget> legs, Vec3 eye, Vec3 lead, BlockPos landing) {
      if (legs != null && legs.size() >= 2 && landing != null && eye != null) {
         ScaffoldModule.PlacementTarget only = null;
         double bestPast = -Double.MAX_VALUE;

         for (ScaffoldModule.PlacementTarget leg : legs) {
            bestPast = Math.max(bestPast, grimEyePastPlane(eye, leg.supportBlock(), leg.face()));
            if (grimSuccessorRayLands(leg, landing, eye, lead)) {
               if (only != null) {
                  return null;
               }

               only = leg;
            }
         }

         if (only == null) {
            return null;
         } else {
            double past = grimEyePastPlane(eye, only.supportBlock(), only.face());
            return bestPast - past <= 0.25 ? only : null;
         }
      } else {
         return null;
      }
   }

   static boolean grimSuccessorRayLands(ScaffoldModule.PlacementTarget leg, BlockPos landing, Vec3 eye, Vec3 lead) {
      if (leg != null && landing != null) {
         Direction face = grimRiserSideFace(landing, leg.placedBlock());
         if (face == null) {
            return false;
         } else {
            Vec3 at = lead == null ? eye : eye.add(lead.scale(2.0));
            return grimCrossingInSquare(at, leg.placedBlock(), face, leg.rotation().yaw());
         }
      } else {
         return false;
      }
   }

   static List<ScaffoldModule.PlacementTarget> grimSightedLegs(List<ScaffoldModule.PlacementTarget> legs, Vec3 eye, Vec3 lead) {
      if (legs != null && legs.size() >= 2) {
         List<ScaffoldModule.PlacementTarget> sighted = new ArrayList<>(legs.size());

         for (ScaffoldModule.PlacementTarget leg : legs) {
            if (grimLegRayLands(leg, eye, lead)) {
               sighted.add(leg);
            }
         }

         return !sighted.isEmpty() && sighted.size() != legs.size() ? sighted : legs;
      } else {
         return legs;
      }
   }

   static boolean grimLegRayLands(ScaffoldModule.PlacementTarget leg, Vec3 eye, Vec3 lead) {
      if (leg != null && eye != null && leg.face().getAxis().isHorizontal()) {
         float yaw = leg.rotation().yaw();

         for (int step = 0; step <= 2; step++) {
            Vec3 at = lead == null ? eye : eye.add(lead.scale(step));
            if (grimEyePastPlane(at, leg.supportBlock(), leg.face()) <= 0.0) {
               return true;
            }

            if (grimCrossingInSquare(at, leg.supportBlock(), leg.face(), yaw)) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private ScaffoldModule.PlacementTarget grimPlanReservedCell(BlockPos candidate, Vec3 predicted, ItemStack stack, Vec3 lane, Direction preferredFace) {
      if (candidate == null || MC.level.isOutsideBuildHeight(candidate)) {
         return this.grimNoteReserveWhy(candidate, "oob", null);
      } else if (this.grimCellOnCooldown(candidate)) {
         return this.grimNoteReserveWhy(candidate, "cooldown", null);
      } else if (grimCellBehind(MC.player.position(), lane, candidate, 0.8)) {
         return this.grimNoteReserveWhy(candidate, "behind", null);
      } else {
         BlockState state = MC.level.getBlockState(candidate);
         if (!this.isSolidSupport(state, candidate) && state.canBeReplaced()) {
            boolean replaceExisting = !state.isAir() && state.getFluidState().isEmpty();
            if (replaceExisting && !this.canBeReplacedWith(state, candidate, stack)) {
               return this.grimNoteReserveWhy(candidate, "no-repl", null);
            } else {
               ScaffoldModule.GrimRowLock rowLock = this.grimActiveRowLock();
               boolean rising = candidate.getY() > this.grimOracleFootingRow();
               if (rowLock != null && !rowLock.allows(candidate, rising)) {
                  return this.grimNoteReserveWhy(candidate, "rowlock", null);
               } else {
                  Direction onlyFace = preferredFace != null ? preferredFace : (rowLock == null ? null : rowLock.pinnedFace(candidate));
                  ScaffoldModule.PlacementTarget plan = this.planTargetForCandidate(candidate, predicted, replaceExisting, lane, true, onlyFace, true);
                  return plan != null && this.grimPlannedFaceBlind(plan)
                     ? this.grimNoteReserveWhy(candidate, "blind", null)
                     : this.grimNoteReserveWhy(candidate, plan == null ? "plan" : "ok", plan);
               }
            }
         } else {
            return this.grimNoteReserveWhy(candidate, "solid", null);
         }
      }
   }

   static List<BlockPos> grimClimbStepCandidates(BlockPos landing, Vec3 lane) {
      List<BlockPos> candidates = new ArrayList<>(2);
      if (landing != null && lane != null) {
         int backX = -horizontalStep(lane.x);
         int backZ = -horizontalStep(lane.z);
         if (backX != 0) {
            candidates.add(landing.offset(backX, 1, 0));
         }

         if (backZ != 0) {
            candidates.add(landing.offset(0, 1, backZ));
         }

         return candidates;
      } else {
         return candidates;
      }
   }

   private ScaffoldModule.PlacementTarget grimClimbStepTarget(BlockPos landing, Vec3 predicted, ItemStack stack, Vec3 lane) {
      if (landing != null && lane != null && MC.level != null && this.grimCourseAscends()) {
         BlockPos rise = landing.above();
         if (!MC.level.isOutsideBuildHeight(rise) && !this.solidAt(rise)) {
            for (BlockPos step : grimClimbStepCandidates(landing, lane)) {
               if (!MC.level.isOutsideBuildHeight(step) && !this.solidAt(step) && this.solidAt(step.below())) {
                  ScaffoldModule.PlacementTarget plan = this.grimPlanReservedCell(step, predicted, stack, lane, Direction.UP);
                  if (plan != null) {
                     this.grimLaunchReservedStep = step.immutable();
                     return plan;
                  }
               }
            }

            return null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private ScaffoldModule.PlacementTarget grimNoteReserveWhy(BlockPos cell, String why, ScaffoldModule.PlacementTarget plan) {
      if (this.traceArmed() && this.grimTraceReserveWhy.length() <= 220) {
         String detail = "plan".equals(why) && this.grimPlanDetail.length() > 0 ? "plan[" + this.grimPlanDetail.toString().trim() + "]" : why;
         if (plan != null && MC.player != null && plan.face().getAxis().isHorizontal()) {
            detail = detail + String.format(Locale.ROOT, "/past%+.2f", grimEyePastPlane(MC.player.getEyePosition(), plan.supportBlock(), plan.face()));
         }

         this.grimTraceReserveWhy = ("--".equals(this.grimTraceReserveWhy) ? "" : this.grimTraceReserveWhy + ",") + traceCell(cell) + ":" + detail;
         return plan;
      } else {
         return plan;
      }
   }

   private static String traceCell(BlockPos cell) {
      return cell == null ? "--" : cell.getX() + "," + cell.getY() + "," + cell.getZ();
   }

   private ScaffoldModule.PlacementTarget findPlacementTarget(ItemStack stack) {
      this.grimAimHoldServed = false;
      this.grimTraceReserveWhy = "--";
      ScaffoldModule.PlacementTarget target = this.selectPlacementTarget(stack);
      if (this.isGrimFamily() && target != null && MC.player != null) {
         int stampAge = grimTicksSince(RiptideSharedState.get().getClientTickCounter(), this.grimPaceRiserHoldTick);
         boolean stampFresh = this.grimPaceRiserHoldCell != null && stampAge >= 0 && stampAge <= 8;
         boolean windowAlive = this.grimAimWindowOpening(target)
            || grimCrossingLandsOnFace(MC.player.getEyePosition(), target.supportBlock(), target.face(), target.rotation().yaw(), true);
         if (grimDyingRiserPickSteal(
            MC.player.onGround(),
            MC.player.getDeltaMovement().y < 0.0,
            stampFresh,
            target.placedBlock().equals(this.grimPaceRiserHoldCell),
            target.face().getAxis().isHorizontal(),
            this.grimEyePastTargetFace(target),
            windowAlive
         )) {
            this.grimLastPick = "steal-veto:" + target.placedBlock().toShortString();
            this.grimStickyTarget = null;
            return null;
         }
      }

      target = this.grimUpFaceUpgrade(target);
      if (this.isGrimFamily() && !this.grimAimHoldServed) {
         this.grimNoteAimCommit(target);
      }

      return target;
   }

   private ScaffoldModule.PlacementTarget grimUpFaceUpgrade(ScaffoldModule.PlacementTarget target) {
      if (target != null && this.isGrimFamily() && MC.player != null && MC.level != null) {
         BlockPos placed = target.placedBlock();
         BlockState state = MC.level.getBlockState(placed);
         boolean replaceExisting = !state.isAir() && state.getFluidState().isEmpty();
         if (!grimUpFaceUpgradeApplies(target.face(), replaceExisting, this.grimFullCubeAt(placed.below()))) {
            return target;
         } else {
            String planFail = this.grimLastPlanFail;
            ScaffoldModule.PlacementTarget up = this.planTargetForCandidate(
               placed,
               this.predictedPlacementPosition(this.currentMovementLine),
               replaceExisting,
               this.currentMovementLine == null ? null : this.currentMovementLine.direction(),
               this.grimPlanningJump(),
               Direction.UP
            );
            this.grimLastPlanFail = planFail;
            if (up != null && up.placedBlock().equals(placed) && up.face() == Direction.UP) {
               this.grimUpFaceSwapCell = placed.immutable();
               return up;
            } else {
               return target;
            }
         }
      } else {
         return target;
      }
   }

   private ScaffoldModule.PlacementTarget selectPlacementTarget(ItemStack stack) {
      if (!this.isValidBlock(stack)) {
         return null;
      } else {
         Vec3 predicted = this.predictedPlacementPosition(this.currentMovementLine);
         BlockPos predictedBase = targetedBase(predicted);
         if (!this.isGrimFamily()) {
            return this.findFromBase(predictedBase, predicted, stack);
         } else {
            ScaffoldModule.PlacementTarget tower = this.grimTowerTarget();
            if (tower != null) {
               this.grimStickyTarget = tower;
               this.grimStickySetTick = RiptideSharedState.get().getClientTickCounter();
               this.grimStickyBandMissTicks = 0;
               this.grimLastPick = "tower:" + tower.placedBlock().toShortString();
               return tower;
            } else {
               ScaffoldModule.PlacementTarget rescue = this.grimFootingRescueTarget(predicted, stack);
               if (rescue != null) {
                  this.grimLastRescueTick = RiptideSharedState.get().getClientTickCounter();
                  this.grimStickyTarget = rescue;
                  this.grimStickySetTick = RiptideSharedState.get().getClientTickCounter();
                  this.grimStickyBandMissTicks = 0;
                  this.grimLastPick = "rescue:" + rescue.placedBlock().toShortString();
                  return rescue;
               } else {
                  ScaffoldModule.PlacementTarget riser = this.grimStaircaseRiserTarget();
                  if (riser != null) {
                     this.grimStickyTarget = riser;
                     this.grimStickySetTick = RiptideSharedState.get().getClientTickCounter();
                     this.grimStickyBandMissTicks = 0;
                     this.grimLastPick = "riser:" + riser.placedBlock().toShortString();
                     return riser;
                  } else {
                     ScaffoldModule.PlacementTarget reservation = this.grimLaunchReservationTarget(predicted, stack);
                     if (reservation != null && !this.grimBelowBuiltFloor(reservation) && !this.grimFallUncatchable(reservation)) {
                        this.grimStickyTarget = reservation;
                        this.grimStickySetTick = RiptideSharedState.get().getClientTickCounter();
                        this.grimStickyBandMissTicks = 0;
                        this.grimLastPick = "reserve-" + this.grimLaunchReservationStage + ":" + reservation.placedBlock().toShortString();
                        return reservation;
                     } else {
                        if (MC.player != null && !MC.player.onGround() && MC.player.getDeltaMovement().y < 0.0 && this.grimDescendingBelowFootingSoon()) {
                           ScaffoldModule.PlacementTarget catchBelow = this.grimBelowFeetCatchTarget(
                              predicted, this.currentMovementLine == null ? null : this.currentMovementLine.direction(), this.grimJumpKeyHeld()
                           );
                           if (catchBelow != null) {
                              this.grimLastRescueTick = RiptideSharedState.get().getClientTickCounter();
                              this.grimStickyTarget = catchBelow;
                              this.grimStickySetTick = RiptideSharedState.get().getClientTickCounter();
                              this.grimStickyBandMissTicks = 0;
                              this.grimLastPick = "catch:" + catchBelow.placedBlock().toShortString();
                              return catchBelow;
                           }
                        }

                        boolean rowLocked = this.grimActiveRowLock() != null;
                        if (!rowLocked) {
                           predictedBase = this.grimRowLockedBase(predictedBase);
                           ScaffoldModule.PlacementTarget drift = this.findTrajectoryDriftTarget(predictedBase);
                           if (drift != null) {
                              this.grimStickyTarget = drift;
                              this.grimStickySetTick = RiptideSharedState.get().getClientTickCounter();
                              this.grimStickyBandMissTicks = 0;
                              return drift;
                           }
                        }

                        ScaffoldModule.PlacementTarget held = this.grimAimCommitHold();
                        if (held != null && !this.grimFallUncatchable(held)) {
                           this.grimLastPick = "hold:" + held.placedBlock().toShortString();
                           return held;
                        } else {
                           ScaffoldModule.PlacementTarget sticky = this.replanGrimStickyTarget(predicted);
                           if (sticky != null && !this.grimBelowBuiltFloor(sticky) && !this.grimFallUncatchable(sticky)) {
                              this.grimLastPick = "sticky";
                              return sticky;
                           } else {
                              ScaffoldModule.PlacementTarget fresh = this.findFromBase(predictedBase, predicted, stack);
                              String freshTier = rowLocked ? "row" : "lane";
                              if (fresh != null && this.grimFallUncatchable(fresh)) {
                                 fresh = null;
                                 this.grimLastPick = freshTier + "-uncatch";
                              } else {
                                 this.grimLastPick = fresh != null ? freshTier + ":" + fresh.placedBlock().toShortString() : freshTier + "-null";
                              }

                              this.grimStickyTarget = fresh;
                              this.grimStickySetTick = RiptideSharedState.get().getClientTickCounter();
                              this.grimStickyBandMissTicks = 0;
                              return fresh;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private BlockPos grimRowLockedBase(BlockPos base) {
      if (base != null && this.grimEffCell != null && this.currentMovementLine != null) {
         Vec3 direction = this.currentMovementLine.direction();
         if (isGrimDiagonalDirection(direction)) {
            return base;
         } else {
            boolean northSouth = Math.abs(direction.z) > Math.abs(direction.x);
            int row = northSouth ? this.grimEffCell.getX() : this.grimEffCell.getZ();
            if (northSouth) {
               return base.getX() == row ? base : new BlockPos(row, base.getY(), base.getZ());
            } else {
               return base.getZ() == row ? base : new BlockPos(base.getX(), base.getY(), row);
            }
         }
      } else {
         return base;
      }
   }

   private BlockPos grimEffectiveFootCell() {
      Vec3 velocity = MC.player.getDeltaMovement();
      Vec3 position = MC.player.position();
      Vec3 future = position.add(velocity.x * 2.0, 0.0, velocity.z * 2.0);
      BlockPos foot = BlockPos.containing(future);
      int tick = RiptideSharedState.get().getClientTickCounter();
      if (this.grimEffCellRefreshTick == tick && this.grimEffCell != null) {
         return new BlockPos(this.grimEffCell.getX(), foot.getY(), this.grimEffCell.getZ());
      } else {
         this.grimEffCellRefreshTick = tick;
         BlockPos committed = this.grimEffCell;
         if (committed != null && (foot.getX() != committed.getX() || foot.getZ() != committed.getZ())) {
            boolean projectionFirm = true;
            if (foot.getX() != committed.getX()) {
               projectionFirm &= grimBoundaryCrossedFirmly(future.x, foot.getX() > committed.getX());
            }

            if (foot.getZ() != committed.getZ()) {
               projectionFirm &= grimBoundaryCrossedFirmly(future.z, foot.getZ() > committed.getZ());
            }

            if (projectionFirm) {
               this.grimEffCell = foot;
            } else {
               BlockPos currentCell = BlockPos.containing(position);
               if (currentCell.getX() != committed.getX() || currentCell.getZ() != committed.getZ()) {
                  boolean positionFirm = true;
                  if (currentCell.getX() != committed.getX()) {
                     positionFirm &= grimInsideCellFirmly(position.x, currentCell.getX() > committed.getX());
                  }

                  if (currentCell.getZ() != committed.getZ()) {
                     positionFirm &= grimInsideCellFirmly(position.z, currentCell.getZ() > committed.getZ());
                  }

                  if (positionFirm) {
                     this.grimEffCell = currentCell;
                  }
               }
            }

            return new BlockPos(this.grimEffCell.getX(), foot.getY(), this.grimEffCell.getZ());
         } else {
            this.grimEffCell = foot;
            return foot;
         }
      }
   }

   private static boolean grimInsideCellFirmly(double coordinate, boolean positive) {
      double fraction = coordinate - Math.floor(coordinate);
      return positive ? fraction > 0.1 : fraction < 0.9;
   }

   private static boolean grimBoundaryCrossedFirmly(double coordinate, boolean positive) {
      double fraction = coordinate - Math.floor(coordinate);
      return positive ? fraction > 0.15 : fraction < 0.85;
   }

   private ScaffoldModule.PlacementTarget replanGrimStickyTarget(Vec3 predicted) {
      ScaffoldModule.PlacementTarget sticky = this.grimStickyTarget;
      if (sticky == null) {
         return null;
      } else {
         this.grimStickyTarget = null;
         int tick = RiptideSharedState.get().getClientTickCounter();
         if (this.grimStickySetTick >= 0 && tick - this.grimStickySetTick <= 6) {
            BlockPos placed = sticky.placedBlock();
            if (!MC.level.isOutsideBuildHeight(placed) && !this.isSolidSupport(MC.level.getBlockState(placed), placed)) {
               BlockPos support = sticky.supportBlock();
               if (!MC.level.isOutsideBuildHeight(support) && !MC.level.getBlockState(support).canBeReplaced()) {
                  if (MC.player != null && MC.player.onGround() && placed.getY() < BlockPos.containing(MC.player.position()).getY() - 1) {
                     return null;
                  } else {
                     Vec3 laneDirection = this.currentMovementLine == null ? null : this.currentMovementLine.direction();
                     if (MC.player != null && !this.grimDescendingBelowFooting() && grimCellBehind(MC.player.position(), laneDirection, placed, 0.8)) {
                        return null;
                     } else if (sticky.face().getAxis().isVertical()
                        && MC.player != null
                        && !footprintOverlapsColumn(MC.player.position(), MC.player.getDeltaMovement(), placed)) {
                        return null;
                     } else if (MC.player != null && supportTooFarToHold(MC.player.position(), support, 2.5)) {
                        return null;
                     } else {
                        boolean jumping = this.grimPlanningJump();
                        ScaffoldModule.GrimRowLock rowLock = this.grimActiveRowLock();
                        if (rowLock != null && !rowLock.allows(placed, this.grimRiseAllowed(jumping))) {
                           return null;
                        } else {
                           ScaffoldModule.PlacementTarget replanned = this.planTargetForCandidate(
                              placed, predicted, false, laneDirection, jumping, sticky.face()
                           );
                           if (replanned == null || !replanned.supportBlock().equals(support) || replanned.face() != sticky.face()) {
                              return null;
                           } else if (MC.player != null && !grimLegRayLands(replanned, MC.player.getEyePosition(), this.grimLeadStep())) {
                              return null;
                           } else {
                              if (replanned.face().getAxis().isHorizontal() && MC.player != null) {
                                 boolean bandExit = grimEyeOutsideFaceBand(MC.player.getEyePosition(), replanned.supportBlock(), replanned.face());
                                 boolean outOfReach = grimPitchOutOfReach(
                                    grimSideWindowSolvePitch(MC.player.getEyePosition(), replanned.supportBlock(), replanned.face(), replanned.rotation().yaw())
                                 );
                                 if (this.grimStickyBandTick != tick) {
                                    this.grimStickyBandTick = tick;
                                    this.grimStickyBandMissTicks = bandExit ? this.grimStickyBandMissTicks + 1 : 0;
                                    this.grimStickyPitchMissTicks = outOfReach ? this.grimStickyPitchMissTicks + 1 : 0;
                                 }

                                 if (this.grimStickyBandMissTicks >= 2) {
                                    this.grimStickyBandMissTicks = 0;
                                    return null;
                                 }

                                 if (this.grimStickyPitchMissTicks >= 2) {
                                    this.grimStickyPitchMissTicks = 0;
                                    return null;
                                 }
                              }

                              this.grimStickyTarget = replanned;
                              return replanned;
                           }
                        }
                     }
                  }
               } else {
                  return null;
               }
            } else {
               return null;
            }
         } else {
            return null;
         }
      }
   }

   static boolean grimPitchOutOfReach(float requiredPitch) {
      return Float.isFinite(requiredPitch) && requiredPitch > 89.5F;
   }

   private float grimSnapFreeAimYaw(float freeYaw) {
      if (this.grimLaneStep() == -1) {
         return freeYaw;
      } else {
         return this.grimNoTargetTicks < 1 && !this.grimDescendingBelowFooting() ? grimSnapYawToLane(this.grimLaneStepYaw(), freeYaw) : freeYaw;
      }
   }

   private boolean grimYawOffPosture() {
      return this.grimYawOffPosture(this.grimSilentRotation);
   }

   private boolean grimYawOffPosture(RiptideRotationUtil.Rotation rotation) {
      if (rotation == null || this.grimLaneStep() == -1) {
         return false;
      } else {
         return this.grimNoTargetTicks < 1 && !this.grimDescendingBelowFooting()
            ? Math.abs(grimLaneOctantResidual(this.grimLaneStepYaw(), rotation.yaw())) > 12.0F
            : false;
      }
   }

   private boolean grimTargetOutOfReach(ScaffoldModule.PlacementTarget target) {
      return target != null && MC.player != null && target.face().getAxis().isHorizontal()
         ? grimPitchOutOfReach(grimSideWindowSolvePitch(MC.player.getEyePosition(), target.supportBlock(), target.face(), target.rotation().yaw()))
         : false;
   }

   static boolean grimEyeOutsideFaceBand(Vec3 eye, BlockPos support, Direction face) {
      boolean xAxisFace = face.getStepX() != 0;
      double lateral = xAxisFace ? eye.z : eye.x;
      double edge = xAxisFace ? support.getZ() : support.getX();
      return lateral < edge || lateral > edge + 1.0;
   }

   private ScaffoldModule.PlacementTarget findTrajectoryDriftTarget(BlockPos laneBase) {
      if (MC.player != null && laneBase != null) {
         Vec3 velocity = MC.player.getDeltaMovement();
         double horizontalSpeed = Math.hypot(velocity.x, velocity.z);
         if (horizontalSpeed < 0.05) {
            return null;
         } else {
            BlockPos footCell = this.grimEffectiveFootCell();
            if (footCell.getY() - 1 != laneBase.getY()) {
               return null;
            } else {
               BlockPos driftBase = new BlockPos(footCell.getX(), laneBase.getY(), footCell.getZ());
               if (driftBase.equals(laneBase)) {
                  return null;
               } else if (this.grimCourseFrozen() && MC.player.position().y < driftBase.getY() + 1.5) {
                  return null;
               } else if (Vec3.atCenterOf(driftBase).subtract(MC.player.position()).horizontalDistance() > 2.0) {
                  return null;
               } else if (MC.level.isOutsideBuildHeight(driftBase)) {
                  return null;
               } else if (this.isSolidSupport(MC.level.getBlockState(driftBase), driftBase)) {
                  return null;
               } else {
                  Vec3 future = MC.player.position().add(velocity.x * 2.0, 0.0, velocity.z * 2.0);
                  Vec3 laneDirection = this.currentMovementLine == null ? null : this.currentMovementLine.direction();
                  boolean jumping = this.grimPlanningJump();
                  if (!this.grimDescendingBelowFooting() && grimCellBehind(MC.player.position(), laneDirection, driftBase, 0.8)) {
                     return null;
                  } else {
                     ScaffoldModule.PlacementTarget direct = this.planTargetForCandidate(driftBase, future, false, laneDirection, jumping);
                     if (direct != null) {
                        this.grimLastPick = "guard:" + driftBase.toShortString();
                        return direct;
                     } else {
                        List<ScaffoldModule.PlacementTarget> seedPlans = new ArrayList<>(4);

                        for (Direction direction : Plane.HORIZONTAL) {
                           BlockPos seed = driftBase.relative(direction);
                           if (!MC.level.isOutsideBuildHeight(seed) && !(Vec3.atCenterOf(seed).subtract(MC.player.position()).horizontalDistance() > 2.0)) {
                              BlockState seedState = MC.level.getBlockState(seed);
                              if (seedState.isAir()) {
                                 ScaffoldModule.PlacementTarget seeded = this.planTargetForCandidate(seed, future, false, laneDirection, jumping);
                                 if (seeded != null) {
                                    seedPlans.add(seeded);
                                 }
                              }
                           }
                        }

                        Vec3 ahead = new Vec3(velocity.x, 0.0, velocity.z);
                        seedPlans.removeIf(plan -> Vec3.atCenterOf(plan.placedBlock()).subtract(MC.player.position()).dot(ahead) < 0.0);
                        if (seedPlans.isEmpty()) {
                           return null;
                        } else {
                           seedPlans.sort(grimSeedOrder(future, this.grimRestPoseGoal()));
                           this.grimLastPick = "gseed:" + seedPlans.get(0).placedBlock().toShortString();
                           return seedPlans.get(0);
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

   static Comparator<ScaffoldModule.PlacementTarget> grimSeedOrder(Vec3 future, RiptideRotationUtil.Rotation posture) {
      return Comparator.<ScaffoldModule.PlacementTarget>comparingDouble(plan -> Math.ceil(Vec3.atCenterOf(plan.placedBlock()).distanceTo(future) / 0.5))
         .thenComparingDouble(plan -> rotationAngle(posture, plan.rotation()));
   }

   static BlockPos targetedBase(Vec3 position) {
      return BlockPos.containing(position).below();
   }

   private BlockPos grimCourseAheadBase(BlockPos realBase) {
      if (MC.player == null || !MC.player.onGround()) {
         return null;
      } else if (this.currentMovementLine == null) {
         return null;
      } else {
         Vec3 direction = this.currentMovementLine.direction();
         if (direction == null) {
            return null;
         } else {
            int stepX = horizontalStep(direction.x);
            int stepZ = horizontalStep(direction.z);
            if (stepX == 0 && stepZ == 0) {
               return null;
            } else {
               BlockPos ahead = realBase.offset(stepX, 0, stepZ);
               if (MC.level.isOutsideBuildHeight(ahead)) {
                  return null;
               } else {
                  return this.isSolidSupport(MC.level.getBlockState(ahead), ahead) ? null : ahead;
               }
            }
         }
      }
   }

   private ScaffoldModule.PlacementTarget findFromBase(BlockPos base, Vec3 plannedPosition, ItemStack stack) {
      if (base == null) {
         this.grimLastPlanFail = "base-null";
         return null;
      } else if (MC.level.isOutsideBuildHeight(base)) {
         this.grimLastPlanFail = "base-outside-height " + base.toShortString();
         return null;
      } else {
         BlockState baseState = MC.level.getBlockState(base);
         if (this.isSolidSupport(baseState, base)) {
            BlockPos realBase = targetedBase(MC.player.position());
            if (this.isGrimFamily()
               && !realBase.equals(base)
               && !MC.level.isOutsideBuildHeight(realBase)
               && !this.isSolidSupport(MC.level.getBlockState(realBase), realBase)) {
               base = realBase;
            } else {
               BlockPos ahead = this.grimCourseAheadBase(realBase);
               if (ahead == null) {
                  this.grimLastPlanFail = "base-solid " + base.toShortString();
                  return null;
               }

               base = ahead;
            }
         }

         Vec3 laneDirection = this.currentMovementLine == null ? null : this.currentMovementLine.direction();
         boolean jumping = this.grimPlanningJump();
         boolean diagonal = isGrimDiagonalDirection(laneDirection);
         List<BlockPos> offsets = this.isGrimFamily()
            ? this.orderedOffsetsForWorld(grimCandidateOffsets(), base, plannedPosition, this.currentMovementLine)
            : this.orderedOffsetsForWorld(NORMAL_OFFSETS, base, plannedPosition, this.currentMovementLine);
         int solidSkips = 0;
         int replaceSkips = 0;
         int planNulls = 0;
         int gateAbove = 0;
         int gateFloor = 0;
         int gateBehind = 0;
         int gateColumnHold = 0;
         int gateRiseVeto = 0;
         int gateRowLock = 0;
         int gateHeight = 0;
         int gateDead = 0;
         int gateBelow = 0;
         String firstPlanDetail = null;
         this.grimLastPlanFail = "";
         ScaffoldModule.GrimRowLock rowLock = this.isGrimFamily() ? this.grimActiveRowLock() : null;
         boolean gateRise = this.isGrimFamily() && !this.grimDescendingBelowFooting();
         Vec3 riseFrom = MC.player.position();
         Vec3 riseCarry = MC.player.getDeltaMovement();
         boolean gateColumn = this.isGrimFamily() && this.grimFootingSurfaceY != Integer.MIN_VALUE;
         boolean riseAllowed = this.grimRiseAllowed(jumping);
         boolean preferHeight = gateRise && riseAllowed;
         int footingRow = this.grimOracleFootingRow();
         int planTick = RiptideSharedState.get().getClientTickCounter();
         ScaffoldModule.PlacementTarget firstPlan = null;

         for (BlockPos offset : offsets) {
            BlockPos candidate = base.offset(offset);
            if (offset.getY() > 0 && MC.player.onGround() && !jumping) {
               gateAbove++;
            } else if (gateRise
               && offset.getY() > 0
               && grimBoxOverColumn(riseFrom, candidate)
               && !grimRiseFloorReady(this.grimLaneStep(), candidate.below(), advancesCourse(riseFrom, laneDirection, candidate), this::solidAt)) {
               gateFloor++;
            } else if (gateRise && grimCellBehind(riseFrom, laneDirection, candidate, 0.8)) {
               gateBehind++;
            } else if (gateColumn
               && candidate.getY() > this.grimFootingSurfaceY
               && grimBoxOverColumn(riseFrom, candidate)
               && !grimRiseColumnHeld(riseFrom, riseCarry, candidate, 3, 0.15)) {
               gateColumnHold++;
            } else if (grimRiseVetoApplies(preferHeight, candidate.getY(), footingRow)
               && !heldArcLandsOnColumn(riseFrom, riseCarry, candidate)
               && !footprintOverlapsColumn(riseFrom, riseCarry, candidate)) {
               gateRiseVeto++;
            } else if (this.isGrimFamily() && grimFloorGateRefuses(candidate.getY(), this.grimBuiltFloorRow(), this.grimDescendingBelowFooting())) {
               gateBelow++;
            } else {
               Direction pinnedFace = null;
               if (rowLock != null) {
                  if (!rowLock.allows(candidate, riseAllowed)) {
                     gateRowLock++;
                     continue;
                  }

                  pinnedFace = rowLock.pinnedFace(candidate);
               }

               if (MC.level.isOutsideBuildHeight(candidate)) {
                  gateHeight++;
               } else if (this.grimCellOnCooldown(candidate)) {
                  gateDead++;
               } else {
                  BlockState candidateState = MC.level.getBlockState(candidate);
                  if (this.isSolidSupport(candidateState, candidate)) {
                     solidSkips++;
                  } else {
                     boolean replaceExisting = !candidateState.isAir() && candidateState.getFluidState().isEmpty();
                     if (replaceExisting && !this.canBeReplacedWith(candidateState, candidate, stack)) {
                        replaceSkips++;
                     } else {
                        ScaffoldModule.PlacementTarget target = this.planTargetForCandidate(
                           candidate, plannedPosition, replaceExisting, laneDirection, jumping, pinnedFace
                        );
                        if (target != null) {
                           if (!preferHeight) {
                              return target;
                           }

                           if (target.placedBlock().getY() > footingRow) {
                              return target;
                           }

                           if (firstPlan == null) {
                              firstPlan = target;
                           }
                        }

                        if (firstPlanDetail == null) {
                           firstPlanDetail = candidate.toShortString() + "{" + this.grimPlanDetail.toString().trim() + "}";
                        }

                        planNulls++;
                     }
                  }
               }
            }
         }

         if (firstPlan != null) {
            return firstPlan;
         } else {
            this.grimLastPlanFail = "base="
               + base.toShortString()
               + " solid:"
               + solidSkips
               + " repl:"
               + replaceSkips
               + " gate[abv:"
               + gateAbove
               + " flr:"
               + gateFloor
               + " bhd:"
               + gateBehind
               + " col:"
               + gateColumnHold
               + " veto:"
               + gateRiseVeto
               + " lock:"
               + gateRowLock
               + " hgt:"
               + gateHeight
               + " dead:"
               + gateDead
               + " blw:"
               + gateBelow
               + "] null:"
               + planNulls
               + (this.grimLastPlanFail.isEmpty() ? "" : " [" + this.grimLastPlanFail + "]")
               + (firstPlanDetail == null ? "" : " first=" + firstPlanDetail);
            return null;
         }
      }
   }

   static List<BlockPos> grimCandidateOffsets() {
      return NORMAL_OFFSETS;
   }

   static double grimFaceLaneDot(Direction face, Vec3 lane) {
      if (face != null && lane != null) {
         double length = Math.sqrt(lane.x * lane.x + lane.z * lane.z);
         return length < 1.0E-6 ? 0.0 : (face.getStepX() * lane.x + face.getStepZ() * lane.z) / length;
      } else {
         return 0.0;
      }
   }

   private static boolean isGrimDiagonalDirection(Vec3 direction) {
      return direction != null && horizontalStep(direction.x) != 0 && horizontalStep(direction.z) != 0;
   }

   private static int horizontalStep(double component) {
      if (component > 1.0E-6) {
         return 1;
      } else {
         return component < -1.0E-6 ? -1 : 0;
      }
   }

   private List<BlockPos> orderedOffsetsForWorld(List<BlockPos> source, BlockPos base, Vec3 predictedPosition, ScaffoldModule.MovementLine optimalLine) {
      List<BlockPos> ordered = new ArrayList<>(source);
      ordered.sort(
         Comparator.comparingInt(ScaffoldModule::grimCandidatePriority)
            .thenComparingDouble(offset -> this.blockDistancePriority(base.offset(offset), predictedPosition, optimalLine))
            .thenComparingDouble(offset -> offset.distSqr(BlockPos.ZERO))
            .thenComparingInt(Vec3i::getY)
            .thenComparingInt(Vec3i::getX)
            .thenComparingInt(Vec3i::getZ)
      );
      return ordered;
   }

   static int grimCandidatePriority(BlockPos offset) {
      return BlockPos.ZERO.equals(offset) ? 0 : 1;
   }

   private double blockDistancePriority(BlockPos pos, Vec3 predictedPosition, ScaffoldModule.MovementLine optimalLine) {
      VoxelShape shape = MC.level.getBlockState(pos).getShape(MC.level, pos, CollisionContext.of(MC.player));
      if (shape.isEmpty()) {
         return optimalLine == null ? Vec3.atCenterOf(pos).distanceToSqr(predictedPosition) : distanceToLineSqr(optimalLine, Vec3.atCenterOf(pos));
      } else {
         double best = Double.POSITIVE_INFINITY;

         for (AABB local : shape.toAabbs()) {
            AABB box = local.move(pos);
            double distance = optimalLine == null ? distanceToBoxSqr(predictedPosition, box) : distanceToBoxSqr(optimalLine, box);
            best = Math.min(best, distance);
         }

         return best;
      }
   }

   private ScaffoldModule.PlacementTarget planTargetForCandidate(
      BlockPos candidate, Vec3 plannedPosition, boolean replaceExisting, Vec3 laneDirection, boolean jumping
   ) {
      return this.planTargetForCandidate(candidate, plannedPosition, replaceExisting, laneDirection, jumping, null);
   }

   private ScaffoldModule.PlacementTarget planTargetForCandidate(
      BlockPos candidate, Vec3 plannedPosition, boolean replaceExisting, Vec3 laneDirection, boolean jumping, Direction onlyFace
   ) {
      return this.planTargetForCandidate(candidate, plannedPosition, replaceExisting, laneDirection, jumping, onlyFace, false);
   }

   private ScaffoldModule.PlacementTarget planTargetForCandidate(
      BlockPos candidate, Vec3 plannedPosition, boolean replaceExisting, Vec3 laneDirection, boolean jumping, Direction onlyFace, boolean planeLead
   ) {
      ScaffoldModule.TargetPlan bestPlan = null;
      double bestFaceAngle = Double.POSITIVE_INFINITY;
      this.grimPlanDetail.setLength(0);
      if (!replaceExisting && MC.player != null) {
         boolean catchMode = this.grimFallingCatchPlan(candidate);
         if (!grimCellClearOfBody(MC.player.getBoundingBox(), MC.player.getDeltaMovement(), candidate, catchMode)) {
            this.grimLastPlanFail = "inside-player";
            this.grimPlanDetail.append("ip:").append(grimCellClearReason(MC.player.getBoundingBox(), MC.player.getDeltaMovement(), candidate, catchMode));
            return null;
         }
      }

      Vec3 aimPosition = grimPlacementAimPosition(plannedPosition, MC.player.position(), this.isGrimFamily());
      Vec3 aimEye = aimPosition.add(0.0, MC.player.getEyeHeight(), 0.0);
      Vec3 selectionPosition = grimFaceSelectionPosition(plannedPosition, aimPosition);
      Vec3 selectionEye = selectionPosition.add(0.0, MC.player.getEyeHeight(), 0.0);
      Vec3 planeLeadEye = null;
      if (planeLead && this.isGrimFamily() && !MC.player.onGround()) {
         Vec3 travel = MC.player.getDeltaMovement();
         planeLeadEye = aimEye.add(travel.x * 4.0, 0.0, travel.z * 4.0);
      }

      RiptideRotationUtil.Rotation from = this.isGrimFamily() ? this.grimRestPoseGoal() : this.serverRotation();
      boolean pinnable = this.isGrimFamily() && MC.player != null;
      Vec3 pinEye = pinnable ? MC.player.getEyePosition().add(this.grimLeadStep()) : null;
      Vec3 pinLead = pinnable ? this.grimLeadStep() : Vec3.ZERO;
      float pinYaw = pinnable ? this.grimSteeredPostureYaw() : 0.0F;
      boolean heldPinStillPins = pinnable
         && this.grimPinFace != null
         && grimHeldPinOwnsCandidate(this.grimPinSupport, this.grimPinFace, candidate)
         && grimFacePinsSoon(pinEye, pinLead, this.grimPinSupport, this.grimPinFace, pinYaw, true, 2);
      boolean bestFacePins = false;
      ScaffoldModule.FaceSample bestSample = null;
      ScaffoldModule.GrimRowLock visibilityLock = this.isGrimFamily() ? this.grimActiveRowLock() : null;
      boolean lockPinnedFace = visibilityLock != null && onlyFace != null && onlyFace == visibilityLock.pinnedFace(candidate);
      boolean requireExactVisibility = !lockPinnedFace
         && grimExactCornerVisibility(this.isGrimFamily(), this.grimEdgeSneakActive, MC.player.onGround(), jumping);
      double visibilityReach = requireExactVisibility ? Math.max(MC.player.blockInteractionRange(), MC.player.entityInteractionRange()) : 0.0;

      for (Direction face : Direction.values()) {
         if (onlyFace == null || face == onlyFace) {
            char faceCode = face.getName().charAt(0);
            BlockPos supportPos = replaceExisting ? candidate : candidate.relative(face.getOpposite());
            if (!MC.level.isOutsideBuildHeight(supportPos)) {
               BlockState supportState = MC.level.getBlockState(supportPos);
               if (!replaceExisting && supportState.canBeReplaced()) {
                  this.grimLastPlanFail = "no-support(" + face + ")";
                  this.grimPlanDetail.append(faceCode).append(":ns ");
               } else if (this.isGrimFamily()
                  && face.getAxis().isHorizontal()
                  && laneDirection != null
                  && !isGrimDiagonalDirection(laneDirection)
                  && grimFaceLaneDot(face, laneDirection) <= -0.5) {
                  this.grimLastPlanFail = "reach-around(" + face + ")";
                  this.grimPlanDetail.append(faceCode).append(":ra ");
               } else {
                  Vec3 faceCenter = Vec3.atCenterOf(supportPos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
                  Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
                  boolean leadPasses = planeLeadEye != null && planeLeadEye.subtract(faceCenter).dot(normal) >= 0.0;
                  if (!leadPasses && selectionEye.subtract(faceCenter).dot(normal) < 0.0 && aimEye.subtract(faceCenter).dot(normal) < 0.0) {
                     this.grimLastPlanFail = "normal-side(" + face + ")";
                     this.grimPlanDetail.append(faceCode).append(":sd ");
                  } else if (this.isGrimFamily() && this.grimSelfOccludedThroughApproach(supportPos, face)) {
                     this.grimLastPlanFail = "self-occ(" + face + ")";
                     this.grimPlanDetail.append(faceCode).append(":so ");
                  } else {
                     boolean facePins = pinnable && grimFacePinsSoon(pinEye, pinLead, supportPos, face, pinYaw, this.grimPinHeldFor(supportPos, face), 2);
                     if (facePins && grimLatchSuppressesPin(heldPinStillPins, this.grimPinHeldFor(supportPos, face))) {
                        facePins = false;
                     }

                     RiptideRotationUtil.Rotation faceRotation = RiptideRotationUtil.lookingAt(faceCenter, aimEye);
                     double faceAngle = rotationAngle(from, faceRotation);
                     if (this.isGrimFamily()
                        && face.getAxis().isHorizontal()
                        && aimPosition.y > supportPos.getY() + 1.0 + 1.0
                        && this.grimFaceOutOfReachThroughApproach(aimEye, supportPos, face, faceRotation.yaw())) {
                        this.grimLastPlanFail = "pitch-cap(" + face + ")";
                        this.grimPlanDetail.append(faceCode).append(":pc ");
                     } else if (betterFace(facePins, faceAngle, bestFacePins, bestFaceAngle)) {
                        VoxelShape shape = supportState.getShape(MC.level, supportPos, CollisionContext.of(MC.player));
                        ScaffoldModule.FaceSample planSample = null;

                        for (AABB localBox : shape.toAabbs()) {
                           ScaffoldModule.FaceRect sampledFace = ScaffoldModule.FaceRect.fromBox(localBox, face);
                           ScaffoldModule.FaceRect searchFace = sampledFace;
                           if (sampledFace.to().y >= 0.9) {
                              ScaffoldModule.FaceRect truncated = sampledFace.truncateY(0.6);
                              if (truncated.area() > 1.0E-9) {
                                 searchFace = truncated;
                              }
                           }

                           Vec3 point = this.stabilizedPointOnFace(searchFace, sampledFace, supportPos, aimEye, from, this.currentMovementLine);
                           if (point == null) {
                              this.grimLastPlanFail = "no-point(" + face + ")";
                              this.grimPlanDetail.append(faceCode).append(":np ");
                           } else {
                              if (requireExactVisibility) {
                                 Vec3 candidatePoint = point.add(supportPos.getX(), supportPos.getY(), supportPos.getZ());
                                 RiptideRotationUtil.Rotation visibleRotation = RiptideRotationUtil.lookingAt(candidatePoint, aimEye);
                                 BlockHitResult visibleHit = this.raytrace(visibleRotation, visibilityReach);
                                 if (visibleHit == null || !visibleHit.getBlockPos().equals(supportPos) || visibleHit.getDirection() != face) {
                                    this.grimLastPlanFail = "visibility(" + face + ")";
                                    this.grimPlanDetail.append(faceCode).append(":vs ");
                                    continue;
                                 }
                              }

                              ScaffoldModule.FaceSample sample = new ScaffoldModule.FaceSample(sampledFace, point, face);
                              if (planSample == null || compareFaceSamples(sample, planSample) > 0) {
                                 planSample = sample;
                              }
                           }
                        }

                        if (planSample != null) {
                           bestFaceAngle = faceAngle;
                           bestFacePins = facePins;
                           bestPlan = new ScaffoldModule.TargetPlan(supportPos.immutable(), face);
                           bestSample = planSample;
                        }
                     }
                  }
               }
            }
         }
      }

      if (bestPlan != null && bestSample != null) {
         Vec3 worldPoint = bestSample.point().add(bestPlan.supportBlock().getX(), bestPlan.supportBlock().getY(), bestPlan.supportBlock().getZ());
         ScaffoldModule.GrimRowLock goalLock = this.isGrimFamily() ? this.grimActiveRowLock() : null;
         Direction goalPinnedFace = goalLock == null ? null : goalLock.pinnedFace(candidate);
         RiptideRotationUtil.Rotation rotation;
         if (this.isGrimFamily() && MC.player != null && !MC.player.onGround()) {
            Vec3 airEye = MC.player.getEyePosition().add(this.grimLeadStep());
            float airPitch = Float.NaN;
            float airYaw = pinYaw;
            if (bestFacePins && bestPlan.face().getAxis().isHorizontal()) {
               this.grimLastGoalEye = "air-pin";
               if (this.grimLaneStep() != -1) {
                  float landing = grimLandingNudge(
                     MC.player.getEyePosition(), this.grimLeadStep(), bestPlan.supportBlock(), bestPlan.face(), pinYaw, this.grimLaneStepYaw()
                  );
                  if (!Float.isNaN(landing)) {
                     airYaw = landing;
                     this.grimLastGoalEye = "air-land";
                  }

                  float flip = grimGridFlipYaw(MC.player.getEyePosition(), this.grimLeadStep(), bestPlan.supportBlock(), bestPlan.face(), airYaw);
                  if (!Float.isNaN(flip)) {
                     airYaw = flip;
                     this.grimLastGoalEye = "air-flip";
                  }
               }

               airPitch = grimTwoEyeCrossingPitch(airEye, MC.player.getEyePosition(), bestPlan.supportBlock(), bestPlan.face(), airYaw, 89.5F);
            } else if (bestPlan.face() == Direction.UP) {
               airPitch = grimTopCrossingPitch(airEye, bestPlan.supportBlock(), pinYaw);
               if (Float.isNaN(airPitch)) {
                  airPitch = grimTopCrossingPitch(MC.player.getEyePosition(), bestPlan.supportBlock(), pinYaw);
               }

               if (!Float.isNaN(airPitch)) {
                  this.grimLastGoalEye = "air-top";
               } else if (this.grimLaneStep() != -1) {
                  float topFlip = grimTopGridFlipYaw(MC.player.getEyePosition(), this.grimLeadStep(), bestPlan.supportBlock(), pinYaw);
                  if (!Float.isNaN(topFlip)) {
                     float flipPitch = grimTopCrossingPitch(airEye, bestPlan.supportBlock(), topFlip);
                     if (Float.isNaN(flipPitch)) {
                        flipPitch = grimTopCrossingPitch(MC.player.getEyePosition(), bestPlan.supportBlock(), topFlip);
                     }

                     if (!Float.isNaN(flipPitch)) {
                        airYaw = topFlip;
                        airPitch = flipPitch;
                        this.grimLastGoalEye = "air-flip";
                     }
                  }
               }
            }

            if (!Float.isNaN(airPitch)) {
               rotation = new RiptideRotationUtil.Rotation(airYaw, airPitch);
            } else {
               if (bestPlan.face().getAxis().isHorizontal()) {
                  double lowY = bestPlan.supportBlock().getY() + bestSample.face().from().y + 0.15;
                  if (lowY < worldPoint.y) {
                     worldPoint = new Vec3(worldPoint.x, lowY, worldPoint.z);
                  }
               }

               this.grimLastGoalEye = "air-direct";
               rotation = this.grimAirbornePredictedGoal(RiptideRotationUtil.lookingAt(worldPoint, MC.player.getEyePosition()), worldPoint);
               double bearingRun = Math.hypot(worldPoint.x - MC.player.getEyePosition().x, worldPoint.z - MC.player.getEyePosition().z);
               float chosen = bestPlan.face().getAxis().isHorizontal() && bearingRun >= 0.75 ? this.grimSnapFreeAimYaw(rotation.yaw()) : pinYaw;
               if (this.grimLaneStep() != -1) {
                  float landingx = grimLandingNudge(
                     MC.player.getEyePosition(), this.grimLeadStep(), bestPlan.supportBlock(), bestPlan.face(), chosen, this.grimLaneStepYaw()
                  );
                  if (!Float.isNaN(landingx)) {
                     chosen = landingx;
                     this.grimLastGoalEye = "air-land";
                  }

                  float flip = grimGridFlipYaw(MC.player.getEyePosition(), this.grimLeadStep(), bestPlan.supportBlock(), bestPlan.face(), chosen);
                  if (!Float.isNaN(flip)) {
                     chosen = flip;
                     this.grimLastGoalEye = "air-flip";
                     float flipPitchx = grimTwoEyeCrossingPitch(airEye, MC.player.getEyePosition(), bestPlan.supportBlock(), bestPlan.face(), flip, 89.5F);
                     if (!Float.isNaN(flipPitchx)) {
                        rotation = new RiptideRotationUtil.Rotation(rotation.yaw(), flipPitchx);
                     }
                  }
               }

               rotation = new RiptideRotationUtil.Rotation(chosen, rotation.pitch());
            }
         } else if (this.isGrimFamily() && MC.player != null && bestPlan.face().getAxis().isHorizontal()) {
            Vec3 leadEye = pinEye == null ? MC.player.getEyePosition().add(this.grimLeadStep()) : pinEye;
            boolean postured = false;
            float goalYaw = 0.0F;
            if (bestFacePins) {
               this.grimLastGoalEye = MC.player.onGround() ? "lead-pin" : "air-pin";
               goalYaw = pinYaw;
               postured = true;
            }

            if (!postured) {
               Vec3 goalEye = this.grimGoalEyeFor(bestPlan.supportBlock(), bestPlan.face(), selectionEye, laneDirection);
               RiptideRotationUtil.Rotation pointGoal = RiptideRotationUtil.lookingAt(worldPoint, goalEye);
               goalYaw = this.grimSnapFreeAimYaw(this.grimAirbornePredictedGoal(pointGoal, worldPoint).yaw());
            }

            rotation = new RiptideRotationUtil.Rotation(
               goalYaw, grimTwoEyeCrossingPitch(MC.player.getEyePosition(), leadEye, bestPlan.supportBlock(), bestPlan.face(), goalYaw, 89.3F)
            );
         } else if (bestFacePins
            && bestPlan.face() == Direction.UP
            && !Float.isNaN(
               grimTopCrossingPitch(pinEye == null ? MC.player.getEyePosition().add(this.grimLeadStep()) : pinEye, bestPlan.supportBlock(), pinYaw)
            )) {
            this.grimLastGoalEye = MC.player.onGround() ? "lead-top" : "air-top";
            rotation = new RiptideRotationUtil.Rotation(
               pinYaw, grimTopCrossingPitch(pinEye == null ? MC.player.getEyePosition().add(this.grimLeadStep()) : pinEye, bestPlan.supportBlock(), pinYaw)
            );
         } else {
            Vec3 goalEye = this.grimGoalEyeFor(bestPlan.supportBlock(), bestPlan.face(), selectionEye, laneDirection);
            rotation = RiptideRotationUtil.lookingAt(worldPoint, goalEye);
            rotation = this.grimAirbornePredictedGoal(rotation, worldPoint);
            if (goalPinnedFace != null) {
               float postureYaw = this.grimSteeredPostureYaw();
               if (bestPlan.face() == Direction.UP && MC.player != null) {
                  Vec3 topEye = pinEye == null ? MC.player.getEyePosition().add(this.grimLeadStep()) : pinEye;
                  float topPitch = grimTopCrossingPitch(topEye, bestPlan.supportBlock(), postureYaw);
                  if (Float.isNaN(topPitch)) {
                     this.grimPlanDetail.append("u:tx ");
                     return null;
                  }

                  this.grimLastGoalEye = MC.player.onGround() ? "lead-top" : "air-top";
                  rotation = new RiptideRotationUtil.Rotation(postureYaw, topPitch);
               } else {
                  rotation = new RiptideRotationUtil.Rotation(postureYaw, rotation.pitch());
               }
            } else {
               rotation = new RiptideRotationUtil.Rotation(this.grimSnapFreeAimYaw(rotation.yaw()), rotation.pitch());
            }
         }

         if (this.isGrimFamily()) {
            Vec3 clampEye = MC.player.getEyePosition();
            Vec3 clampLead = clampEye.add(this.grimLeadStep());
            double[] window = MC.player.onGround()
               ? grimTwoEyeCrossingWindow(clampEye, clampLead, bestPlan.supportBlock(), bestPlan.face(), rotation.yaw())
               : grimTwoEyeCrossingWindow(clampLead, clampEye, bestPlan.supportBlock(), bestPlan.face(), rotation.yaw());
            double windowLow = window == null ? Double.NaN : window[0];
            double windowHigh = window == null ? Double.NaN : window[1];
            float goalPitch = grimPlacementPitchGoal(rotation.pitch(), windowLow, windowHigh);
            float dodged = this.grimIntaveRotationPitchGoal(
               goalPitch, windowLow, bestPlan.face(), MC.player.onGround() ? clampEye : clampLead, this.grimLeadStep(), rotation.yaw(), bestPlan.supportBlock()
            );
            double flickHigh = dodged < goalPitch ? (Double.isNaN(windowHigh) ? dodged : Math.min(windowHigh, (double)dodged)) : windowHigh;
            goalPitch = this.grimIntaveFlickPitchGoal(dodged, windowLow, flickHigh, bestPlan.face(), candidate.immutable());
            rotation = new RiptideRotationUtil.Rotation(rotation.yaw(), goalPitch);
         }

         BlockHitResult plannedHit = new BlockHitResult(worldPoint, bestPlan.face(), bestPlan.supportBlock(), false);
         return new ScaffoldModule.PlacementTarget(
            bestPlan.supportBlock(), candidate.immutable(), bestPlan.face(), plannedHit, rotation, bestPlan.supportBlock().getY() + bestSample.face().from().y
         );
      } else {
         return null;
      }
   }

   private boolean grimPlanningJump() {
      return this.grimJumpKeyHeld() || this.grimLaunchReservationAirborne && MC.player != null && !MC.player.onGround();
   }

   private boolean solidAt(BlockPos pos) {
      return MC.level != null && !MC.level.isOutsideBuildHeight(pos) ? this.isSolidSupport(MC.level.getBlockState(pos), pos) : false;
   }

   private boolean grimTrustedSolidAt(BlockPos pos) {
      if (!this.solidAt(pos)) {
         this.grimUntrustedPredictions.remove(pos);
         return false;
      } else {
         return !this.grimUntrustedPredictions.containsKey(pos);
      }
   }

   private boolean grimHasUntrustedPrediction() {
      Iterator<BlockPos> iterator = this.grimUntrustedPredictions.keySet().iterator();

      while (iterator.hasNext()) {
         BlockPos cell = iterator.next();
         if (!this.solidAt(cell)) {
            iterator.remove();
         }
      }

      return !this.grimUntrustedPredictions.isEmpty();
   }

   static boolean grimRiseFloorReady(int laneStep, BlockPos below, Predicate<BlockPos> solid) {
      return grimRiseFloorReady(laneStep, below, false, solid);
   }

   static boolean grimRiseFloorReady(int laneStep, BlockPos below, boolean aheadRise, Predicate<BlockPos> solid) {
      if (laneStep != -1 && below != null) {
         double yaw = Math.toRadians(compassStepYaw(laneStep));
         int dx = (int)Math.round(-Math.sin(yaw));
         int dz = (int)Math.round(Math.cos(yaw));
         if (dx == 0 && dz == 0) {
            return true;
         } else if (!solid.test(below.offset(dx, 0, dz))) {
            return false;
         } else if (dx == 0 || dz == 0) {
            return true;
         } else {
            return aheadRise ? true : solid.test(below.offset(dx, 0, 0)) || solid.test(below.offset(0, 0, dz));
         }
      } else {
         return true;
      }
   }

   static boolean grimRiseVetoApplies(boolean preferHeight, int candidateY, int footingRow) {
      return preferHeight && candidateY > footingRow;
   }

   private void updateGrimFootingSurface() {
      if (MC.player != null && MC.player.onGround()) {
         int row = this.grimFootingRowUnderFeet();
         if (this.grimFootingSurfaceY != Integer.MIN_VALUE && row > this.grimFootingSurfaceY) {
            this.grimLastRowGainTick = RiptideSharedState.get().getClientTickCounter();
         } else if (this.grimFootingSurfaceY != Integer.MIN_VALUE && row < this.grimFootingSurfaceY) {
            this.grimLastRowGainTick = Integer.MIN_VALUE;
         }

         this.grimFootingSurfaceY = row;
         this.grimAirborneBuiltRow = Integer.MIN_VALUE;
      }
   }

   private boolean grimDescendingBelowFooting() {
      return !this.grimCourseFrozen() ? false : this.grimFootingSurfaceY == Integer.MIN_VALUE || MC.player.getY() < this.grimFootingSurfaceY + 1.0;
   }

   static boolean grimFeetCrossFootingSoon(double feet, double vy, int footingRow, int lookahead) {
      double plane = footingRow + 1.0;
      if (feet < plane) {
         return true;
      } else {
         for (int tick = 0; tick < lookahead; tick++) {
            feet += vy;
            vy = (vy - 0.08) * 0.98;
            if (feet < plane) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean grimDescendingBelowFootingSoon() {
      if (this.grimDescendingBelowFooting()) {
         return true;
      } else {
         return !this.grimCourseFrozen() ? false : grimFeetCrossFootingSoon(MC.player.getY(), MC.player.getDeltaMovement().y, this.grimFootingSurfaceY, 2);
      }
   }

   private boolean grimFallingCatchPlan(BlockPos candidate) {
      if (!this.isGrimFamily() || MC.player == null || MC.player.onGround()) {
         return false;
      } else if (MC.player.getDeltaMovement().y >= 0.0) {
         return false;
      } else if (candidate.getY() + 1.0 > MC.player.getBoundingBox().minY) {
         return false;
      } else if (this.grimDescendingBelowFooting()) {
         return true;
      } else {
         Vec3 landing = grimDescentCrossing(MC.player.position(), MC.player.getDeltaMovement(), candidate.getY() + 1.0, 2);
         return landing != null && Mth.floor(landing.x) == candidate.getX() && Mth.floor(landing.z) == candidate.getZ();
      }
   }

   private static double grimAxisOverlap(double centre, int cell) {
      double low = Math.max(centre - 0.3, (double)cell);
      double high = Math.min(centre + 0.3, cell + 1.0);
      return Math.max(0.0, high - low);
   }

   static boolean grimHoldRefreshBlocked(int servedTick, int now, BlockPos held, BlockPos noted) {
      return servedTick == now && held != null && held.equals(noted);
   }

   private void grimNoteAimCommit(ScaffoldModule.PlacementTarget target) {
      if (target != null) {
         int tick = RiptideSharedState.get().getClientTickCounter();
         if (!grimHoldRefreshBlocked(
            this.grimAimHoldServedTick, tick, this.grimAimHoldTarget == null ? null : this.grimAimHoldTarget.placedBlock(), target.placedBlock()
         )) {
            this.grimAimHoldTarget = target;
            this.grimAimHoldTick = tick;
         }
      }
   }

   static boolean grimAimHoldApplies(int heldTick, int now, int maxTicks, boolean cellOpen, boolean supported) {
      if (heldTick < 0) {
         return false;
      } else {
         int age = now - heldTick;
         return age >= 0 && age <= maxTicks && cellOpen && supported;
      }
   }

   static boolean grimHoldRowAllowed(int heldRow, int footingRow) {
      return footingRow == Integer.MIN_VALUE || heldRow >= footingRow;
   }

   static int grimHoldFloorRow(int footingRow, int lastPlacedRow) {
      if (footingRow == Integer.MIN_VALUE) {
         return lastPlacedRow;
      } else {
         return lastPlacedRow == Integer.MIN_VALUE ? footingRow : Math.max(footingRow, lastPlacedRow);
      }
   }

   static boolean grimHoldBehindLane(Vec3 position, Vec3 laneDirection, BlockPos placed, boolean descending) {
      return !descending && grimCellBehind(position, laneDirection, placed, 0.8);
   }

   private ScaffoldModule.PlacementTarget grimAimCommitHold() {
      ScaffoldModule.PlacementTarget held = this.grimAimHoldTarget;
      if (held != null && MC.level != null) {
         BlockPos placed = held.placedBlock();
         BlockPos support = held.supportBlock();
         if (!MC.level.isOutsideBuildHeight(placed) && !MC.level.isOutsideBuildHeight(support)) {
            BlockPos lastPlaced = this.lastPlacedBlocks.peekLast();
            if (!grimHoldRowAllowed(placed.getY(), grimHoldFloorRow(this.grimOracleFootingRow(), lastPlaced == null ? Integer.MIN_VALUE : lastPlaced.getY()))) {
               this.grimAimHoldTarget = null;
               return null;
            } else if (MC.player != null
               && grimHoldBehindLane(
                  MC.player.position(),
                  this.currentMovementLine == null ? null : this.currentMovementLine.direction(),
                  placed,
                  this.grimDescendingBelowFooting()
               )) {
               this.grimAimHoldTarget = null;
               return null;
            } else if (MC.player != null
               && !grimCellClearOfBody(MC.player.getBoundingBox(), MC.player.getDeltaMovement(), placed, this.grimFallingCatchPlan(placed))) {
               this.grimAimHoldTarget = null;
               return null;
            } else if (this.grimCellOnCooldown(placed)) {
               this.grimAimHoldTarget = null;
               return null;
            } else if (this.grimRiseDropApplies(held)) {
               this.grimAimHoldTarget = null;
               return null;
            } else if (MC.player != null && !grimLegRayLands(held, MC.player.getEyePosition(), this.grimLeadStep())) {
               this.grimAimHoldTarget = null;
               return null;
            } else {
               boolean cellOpen = MC.level.getBlockState(placed).canBeReplaced();
               boolean supported = this.isSolidSupport(MC.level.getBlockState(support), support);
               if (!grimAimHoldApplies(this.grimAimHoldTick, RiptideSharedState.get().getClientTickCounter(), 3, cellOpen, supported)) {
                  this.grimAimHoldTarget = null;
                  return null;
               } else {
                  this.grimAimHoldServed = true;
                  this.grimAimHoldServedTick = RiptideSharedState.get().getClientTickCounter();
                  ScaffoldModule.PlacementTarget resolved = this.grimReSolveHeldTarget(held);
                  return resolved == null ? held : resolved;
               }
            }
         } else {
            this.grimAimHoldTarget = null;
            return null;
         }
      } else {
         return null;
      }
   }

   static boolean grimPacesAsRiser(BlockPos placed, BlockPos support, BlockPos upFaceSwapCell) {
      return placed.getY() != support.getY() && !placed.equals(upFaceSwapCell);
   }

   static boolean grimUpFaceUpgradeApplies(Direction heldFace, boolean replaceExisting, boolean fullCubeBelow) {
      return heldFace != null && heldFace.getAxis().isHorizontal() ? replaceExisting || fullCubeBelow : false;
   }

   private ScaffoldModule.PlacementTarget grimReSolveHeldTarget(ScaffoldModule.PlacementTarget held) {
      if (MC.player != null && MC.level != null) {
         BlockPos placed = held.placedBlock();
         BlockState state = MC.level.getBlockState(placed);
         boolean replaceExisting = !state.isAir() && state.getFluidState().isEmpty();
         String planFail = this.grimLastPlanFail;
         ScaffoldModule.PlacementTarget upgraded = this.grimUpFaceUpgrade(held);
         if (upgraded != held) {
            this.grimAimHoldTarget = upgraded;
            return upgraded;
         } else {
            ScaffoldModule.PlacementTarget fresh = this.planTargetForCandidate(
               placed,
               this.predictedPlacementPosition(this.currentMovementLine),
               replaceExisting,
               this.currentMovementLine == null ? null : this.currentMovementLine.direction(),
               this.grimPlanningJump(),
               held.face()
            );
            this.grimLastPlanFail = planFail;
            return fresh != null && fresh.placedBlock().equals(placed) && fresh.face() == held.face() ? fresh : null;
         }
      } else {
         return null;
      }
   }

   static boolean grimCellBehind(Vec3 position, Vec3 laneDirection, BlockPos cell, double margin) {
      if (position != null && laneDirection != null && cell != null) {
         double lx = laneDirection.x;
         double lz = laneDirection.z;
         double length = Math.sqrt(lx * lx + lz * lz);
         if (length <= 1.0E-6) {
            return false;
         } else {
            double along = (cell.getX() + 0.5 - position.x) * (lx / length) + (cell.getZ() + 0.5 - position.z) * (lz / length);
            return along < -margin;
         }
      } else {
         return false;
      }
   }

   static boolean grimBoxOverColumn(Vec3 position, BlockPos column) {
      return position != null && column != null && grimAxisOverlap(position.x, column.getX()) > 0.0 && grimAxisOverlap(position.z, column.getZ()) > 0.0;
   }

   static boolean grimRiseColumnHeld(Vec3 position, Vec3 velocity, BlockPos column, int leadTicks, double minOverlap) {
      if (position != null && column != null) {
         double x = position.x + (velocity == null ? 0.0 : velocity.x) * leadTicks;
         double z = position.z + (velocity == null ? 0.0 : velocity.z) * leadTicks;
         return grimAxisOverlap(x, column.getX()) >= minOverlap && grimAxisOverlap(z, column.getZ()) >= minOverlap;
      } else {
         return true;
      }
   }

   private boolean grimPinHeldFor(BlockPos support, Direction face) {
      return this.grimPinFace == face && support != null && support.equals(this.grimPinSupport);
   }

   static boolean grimHeldPinOwnsCandidate(BlockPos pinSupport, Direction pinFace, BlockPos candidate) {
      return pinSupport != null && pinFace != null && pinSupport.relative(pinFace).equals(candidate);
   }

   static boolean grimLatchSuppressesPin(boolean heldPinStillPins, boolean heldForThisFace) {
      return heldPinStillPins && !heldForThisFace;
   }

   static boolean grimFacePins(Vec3 pinEye, BlockPos support, Direction face, float pinYaw, boolean holding) {
      return face.getAxis().isHorizontal()
         ? grimCrossingLandsOnFace(pinEye, support, face, pinYaw, holding)
         : face == Direction.UP && grimTopCrossingLandsOnFace(pinEye, support, pinYaw, holding);
   }

   static boolean betterFace(boolean pins, double angle, boolean bestPins, double bestAngle) {
      return pins != bestPins ? pins : angle < bestAngle;
   }

   private void grimNotePin(ScaffoldModule.PlacementTarget target) {
      this.grimTraceCrossing = target != null && MC.player != null
         ? grimCrossingFraction(MC.player.getEyePosition().add(this.grimLeadStep()), target.supportBlock(), target.face(), this.grimSteeredPostureYaw())
         : Double.NaN;
      if (target != null && MC.player != null && this.isGrimFamily()) {
         if (!this.grimPinIsStale(target)
            && grimFacePinsSoon(
               MC.player.getEyePosition().add(this.grimLeadStep()),
               this.grimLeadStep(),
               target.supportBlock(),
               target.face(),
               this.grimSteeredPostureYaw(),
               this.grimPinHeldFor(target.supportBlock(), target.face()),
               2
            )) {
            this.grimPinSupport = target.supportBlock();
            this.grimPinFace = target.face();
         } else {
            this.grimPinSupport = null;
            this.grimPinFace = null;
         }
      } else {
         this.grimPinSupport = null;
         this.grimPinFace = null;
      }
   }

   private boolean grimPinIsStale(ScaffoldModule.PlacementTarget target) {
      if (target == null) {
         this.grimStaleSupport = null;
         this.grimStaleFace = null;
         this.grimStaleTicks = 0;
         return false;
      } else {
         boolean same = this.grimStaleFace == target.face() && target.supportBlock().equals(this.grimStaleSupport);
         this.grimStaleSupport = target.supportBlock();
         this.grimStaleFace = target.face();
         this.grimStaleTicks = grimStaleCount(this.grimStaleTicks, same, this.grimTraceClickLands);
         return this.grimStaleTicks > 14;
      }
   }

   static int grimStaleCount(int previous, boolean sameTarget, boolean clickLanded) {
      if (clickLanded) {
         return 0;
      } else {
         return sameTarget ? previous + 1 : 1;
      }
   }

   private RiptideRotationUtil.Rotation grimAirbornePredictedGoal(RiptideRotationUtil.Rotation pushGoal, Vec3 worldPoint) {
      if (this.isGrimFamily() && MC.player != null && !MC.player.onGround()) {
         Vec3 eye = MC.player.getEyePosition();
         RiptideRotationUtil.Rotation direct = RiptideRotationUtil.lookingAt(worldPoint, eye);
         RiptideRotationUtil.Rotation predicted = RiptideRotationUtil.lookingAt(worldPoint, eye.add(MC.player.getDeltaMovement()));
         return grimAirbornePredictionGate(pushGoal, direct, predicted);
      } else {
         return pushGoal;
      }
   }

   static RiptideRotationUtil.Rotation grimAirbornePredictionGate(
      RiptideRotationUtil.Rotation pushGoal, RiptideRotationUtil.Rotation direct, RiptideRotationUtil.Rotation predicted
   ) {
      return Math.abs(RiptideRotationUtil.angleDifference(predicted.yaw(), pushGoal.yaw())) > 100.0F
         ? pushGoal
         : new RiptideRotationUtil.Rotation(predicted.yaw(), Math.min(predicted.pitch(), 90.0F));
   }

   private boolean place(ScaffoldModule.PlacementTarget target, InteractionHand hand, ItemStack stack) {
      return this.place(target, hand, stack, true, true);
   }

   private boolean place(
      ScaffoldModule.PlacementTarget target, InteractionHand hand, ItemStack stack, boolean restoreClientRotation, boolean sendPlacementRotation
   ) {
      RiptideRotationUtil.Rotation rotation = target.rotation();
      ScaffoldModule.MovementLine placementLine = this.currentMovementLine;
      Vec3 previousFallOff = this.findFallOffPosition(placementLine);
      float clientYaw = MC.player.getYRot();
      float clientPitch = MC.player.getXRot();
      if (sendPlacementRotation && !sameRotation(rotation, this.serverRotation())) {
         this.sendRotation(rotation);
      }

      int oldCount = stack.getCount();

      boolean wasStackUsed;
      try {
         InteractionResult result = MC.gameMode.useItemOn(MC.player, hand, target.hit());
         if (result instanceof Fail) {
            return false;
         }

         if (!(result instanceof Pass)) {
            if (!result.consumesAction()) {
               return false;
            }

            if (!(result instanceof Success success && success.swingSource() != SwingSource.CLIENT)) {
               MC.player.swing(hand);
               RiptideCpsTracker.recordRight();
               RiptideScaffoldPlaceRenderer.recordPlacement(target.placedBlock());
               this.trackSuccessfulPlacement(target.placedBlock(), placementLine, previousFallOff);
               wasStackUsed = !stack.isEmpty() && (stack.getCount() != oldCount || MC.player.hasInfiniteMaterials());
               if (wasStackUsed) {
                  MC.gameRenderer.itemInHandRenderer.itemUsed(hand);
               }

               return true;
            }

            return true;
         }

         if (!stack.isEmpty()) {
            if (MC.gameMode.useItem(MC.player, hand) instanceof Success success) {
               if (success.swingSource() == SwingSource.CLIENT) {
                  MC.player.swing(hand);
               }

               MC.gameRenderer.itemInHandRenderer.itemUsed(hand);
            }

            return false;
         }

         wasStackUsed = false;
      } finally {
         if (restoreClientRotation) {
            RiptideRotationUtil.Rotation clientRotation = new RiptideRotationUtil.Rotation(clientYaw, clientPitch);
            if (!sameRotation(this.serverRotation(), clientRotation)) {
               this.sendRotation(clientRotation);
            }
         }
      }

      return wasStackUsed;
   }

   static Vec3 grimPlacementAimPosition(Vec3 predictedPosition, Vec3 actualPosition, boolean edgeHoldActive) {
      if (predictedPosition == null) {
         return actualPosition;
      } else if (actualPosition == null) {
         return predictedPosition;
      } else {
         return edgeHoldActive ? actualPosition : predictedPosition;
      }
   }

   static Vec3 grimFaceSelectionPosition(Vec3 predictedPosition, Vec3 aimPosition) {
      return predictedPosition == null ? aimPosition : predictedPosition;
   }

   private void trackSuccessfulPlacement(BlockPos placed, ScaffoldModule.MovementLine line, Vec3 previousFallOff) {
      this.grimEdgeLockedLine = null;
      BlockPos immutable = placed.immutable();
      if (!immutable.equals(this.lastPlacedBlocks.peekLast())) {
         while (this.lastPlacedBlocks.size() >= 4) {
            this.lastPlacedBlocks.removeFirst();
         }

         this.lastPlacedBlocks.addLast(immutable);
      }

      if (line != null && previousFallOff != null) {
         float angle = (float)Math.atan2(line.direction().z, line.direction().x);
         Vec3 unrotatedOffset = MC.player.position().subtract(previousFallOff).yRot(angle);
         this.placementOffsets.addLast(unrotatedOffset);

         while (this.placementOffsets.size() > 4) {
            this.placementOffsets.removeFirst();
         }
      }
   }

   private void sendRotation(RiptideRotationUtil.Rotation rotation) {
      if (rotation != null && MC.getConnection() != null && MC.player != null) {
         this.serverRotation = rotation;
         MC.getConnection()
            .send(
               new PosRot(
                  MC.player.getX(), MC.player.getY(), MC.player.getZ(), rotation.yaw(), rotation.pitch(), MC.player.onGround(), MC.player.horizontalCollision
               )
            );
      }
   }

   private static boolean sameRotation(RiptideRotationUtil.Rotation first, RiptideRotationUtil.Rotation second) {
      return first != null && second != null && Float.compare(first.yaw(), second.yaw()) == 0 && Float.compare(first.pitch(), second.pitch()) == 0;
   }

   private BlockHitResult raytrace(RiptideRotationUtil.Rotation rotation, double reach) {
      return rotation == null ? null : grimClickRay(MC.player.getEyePosition(), rotation, reach, MC.level, MC.player);
   }

   static BlockHitResult grimClickRay(Vec3 eye, RiptideRotationUtil.Rotation rotation, double reach, BlockGetter world, Entity entity) {
      Vec3 look = lookVector(rotation);
      Vec3 end = eye.add(look.scale(reach));
      HitResult result = world.clip(
         new ClipContext(
            eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE, Fluid.NONE, entity == null ? CollisionContext.empty() : CollisionContext.of(entity)
         )
      );
      return result instanceof BlockHitResult blockHit && result.getType() == Type.BLOCK ? blockHit : null;
   }

   private ItemStack planningStack() {
      ItemStack main = MC.player.getMainHandItem();
      if (this.isValidBlock(main)) {
         return main;
      } else {
         int hotbar = this.findBestBlockSlot();
         return hotbar >= 0 ? MC.player.getInventory().getItem(hotbar) : ItemStack.EMPTY;
      }
   }

   private BlockPos grimPlacedCellFor(BlockHitResult hit, ItemStack stack) {
      if (hit == null) {
         return null;
      } else {
         return MC.player != null && stack != null && !stack.isEmpty()
            ? new BlockPlaceContext(MC.player, InteractionHand.MAIN_HAND, stack, hit).getClickedPos().immutable()
            : hit.getBlockPos().relative(hit.getDirection());
      }
   }

   private boolean grimHitBuildsPlannedCell(BlockHitResult hit, ScaffoldModule.PlacementTarget pending) {
      return hit != null && pending != null ? pending.placedBlock().equals(this.grimPlacedCellFor(hit, this.planningStack())) : false;
   }

   static AABB grimSupportBox(BlockPos support) {
      if (support == null) {
         return null;
      } else if (MC != null && MC.level != null) {
         BlockState state = MC.level.getBlockState(support);
         if (support.equals(grimSupportBoxKey) && state == grimSupportBoxState) {
            return grimSupportBoxValue;
         } else {
            AABB box = grimUnitBox(support);
            VoxelShape shape = state.getShape(MC.level, support, MC.player == null ? CollisionContext.empty() : CollisionContext.of(MC.player));
            if (!shape.isEmpty()) {
               box = shape.bounds().move(support.getX(), support.getY(), support.getZ());
            }

            grimSupportBoxKey = support.immutable();
            grimSupportBoxState = state;
            grimSupportBoxValue = box;
            return box;
         }
      } else {
         return grimUnitBox(support);
      }
   }

   static Vec3 grimFaceCentre(AABB support, Direction face) {
      Vec3 c = support.getCenter();

      return switch (face) {
         case EAST -> new Vec3(support.maxX, c.y, c.z);
         case WEST -> new Vec3(support.minX, c.y, c.z);
         case SOUTH -> new Vec3(c.x, c.y, support.maxZ);
         case NORTH -> new Vec3(c.x, c.y, support.minZ);
         case DOWN -> new Vec3(c.x, support.minY, c.z);
         case UP -> new Vec3(c.x, support.maxY, c.z);
         default -> throw new MatchException(null, null);
      };
   }

   static AABB grimUnitBox(BlockPos support) {
      return new AABB(support.getX(), support.getY(), support.getZ(), support.getX() + 1.0, support.getY() + 1.0, support.getZ() + 1.0);
   }

   private boolean canBeReplacedWith(BlockState state, BlockPos pos, ItemStack stack) {
      BlockPlaceContext context = new BlockPlaceContext(
         MC.player, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atLowerCornerOf(pos), Direction.UP, pos, false)
      );
      return state.canBeReplaced(context);
   }

   private InteractionHand ensurePlacementHand() {
      ItemStack main = MC.player.getMainHandItem();
      if (this.isValidBlock(main)) {
         if (this.requestedSlot == MC.player.getInventory().getSelectedSlot()) {
            this.requestedSlot = -1;
         }

         return InteractionHand.MAIN_HAND;
      } else {
         int slot = this.findBestBlockSlot();
         if (slot < 0) {
            return null;
         } else {
            int selected = MC.player.getInventory().getSelectedSlot();
            if (selected == slot) {
               return InteractionHand.MAIN_HAND;
            } else if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
               return null;
            } else {
               try {
                  if (this.bool("switch-back") && this.originalSlot < 0) {
                     this.originalSlot = selected;
                  }

                  this.requestedSlot = slot;
                  this.switchedToSlot = slot;
                  this.selectionPending = true;
                  RiptideInputClicker.queueHotbarSlot(slot);
               } finally {
                  RiptideHandArbiter.endHandPacketGroup(this.id());
               }

               return null;
            }
         }
      }
   }

   private int findBestBlockSlot() {
      int best = this.findBestBlockSlot(true);
      return best >= 0 ? best : this.findBestBlockSlot(false);
   }

   private int findBestBlockSlot(boolean requireReserve) {
      int best = -1;

      for (int slot = 0; slot < 9; slot++) {
         if (!RiptideHandArbiter.slotReserved(slot, this.id())) {
            ItemStack stack = MC.player.getInventory().getItem(slot);
            if (this.isValidBlock(stack)
               && (!requireReserve || stack.getCount() > 1)
               && (best < 0 || this.compareBlockStacks(stack, MC.player.getInventory().getItem(best)) > 0)) {
               best = slot;
            }
         }
      }

      return best;
   }

   private int compareBlockStacks(ItemStack first, ItemStack second) {
      Block firstBlock = ((BlockItem)first.getItem()).getBlock();
      Block secondBlock = ((BlockItem)second.getItem()).getBlock();
      BlockState firstState = firstBlock.defaultBlockState();
      BlockState secondState = secondBlock.defaultBlockState();
      int result = Boolean.compare(!this.isUnfavorable(firstBlock, firstState), !this.isUnfavorable(secondBlock, secondState));
      if (result != 0) {
         return result;
      } else {
         result = Boolean.compare(firstState.isRedstoneConductor(MC.level, BlockPos.ZERO), secondState.isRedstoneConductor(MC.level, BlockPos.ZERO));
         if (result != 0) {
            return result;
         } else {
            result = Boolean.compare(
               firstState.isCollisionShapeFullBlock(MC.level, BlockPos.ZERO), secondState.isCollisionShapeFullBlock(MC.level, BlockPos.ZERO)
            );
            if (result != 0) {
               return result;
            } else {
               result = Float.compare(firstBlock.getFriction(), secondBlock.getFriction());
               if (result != 0) {
                  return result;
               } else {
                  result = Float.compare(Math.abs(firstBlock.getJumpFactor() - 1.0F), Math.abs(secondBlock.getJumpFactor() - 1.0F));
                  if (result != 0) {
                     return result;
                  } else {
                     result = Float.compare(Math.abs(firstBlock.getSpeedFactor() - 1.0F), Math.abs(secondBlock.getSpeedFactor() - 1.0F));
                     if (result != 0) {
                        return result;
                     } else {
                        result = Double.compare(this.hardnessDistance(secondState, true), this.hardnessDistance(firstState, true));
                        if (result != 0) {
                           return result;
                        } else {
                           result = Integer.compare(second.getCount(), first.getCount());
                           return result != 0 ? result : Double.compare(this.hardnessDistance(secondState, false), this.hardnessDistance(firstState, false));
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isUnfavorable(Block block, BlockState state) {
      return block.getFriction() > 0.6F
         || block.getSpeedFactor() < 1.0F
         || block.getJumpFactor() < 1.0F
         || block instanceof BaseEntityBlock
         || !state.isCollisionShapeFullBlock(MC.level, BlockPos.ZERO)
         || block == Blocks.CRAFTING_TABLE
         || block == Blocks.JIGSAW
         || block == Blocks.SMITHING_TABLE
         || block == Blocks.FLETCHING_TABLE
         || block == Blocks.ENCHANTING_TABLE
         || block == Blocks.CAULDRON
         || block == Blocks.MAGMA_BLOCK;
   }

   private double hardnessDistance(BlockState state, boolean neutralRange) {
      double hardness = state.getDestroySpeed(MC.level, BlockPos.ZERO);
      return neutralRange && hardness >= 0.8 && hardness <= 2.0 ? 0.0 : Math.abs(1.7 - hardness);
   }

   private boolean isValidBlock(ItemStack stack) {
      if (!(stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem)) {
         return false;
      } else if (!stack.isItemEnabled(MC.level.enabledFeatures())) {
         return false;
      } else {
         Block block = blockItem.getBlock();
         if (isPlaceableBlockChoice(block) && this.filterAllows(block)) {
            BlockState state = block.defaultBlockState();
            return state.entityCanStandOnFace(MC.level, BlockPos.ZERO, MC.player, Direction.UP);
         } else {
            return false;
         }
      }
   }

   public static boolean isPlaceableBlockChoice(Block block) {
      if (block == null || !(block.asItem() instanceof BlockItem blockItem && blockItem.getBlock() == block)) {
         return false;
      } else if (!(block instanceof FallingBlock) && block != Blocks.TNT && block != Blocks.COBWEB && block != Blocks.NETHER_PORTAL) {
         try {
            VoxelShape collision = block.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
            return Block.isFaceFull(collision, Direction.UP);
         } catch (Throwable var3) {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean filterAllows(Block block) {
      String mode = this.choice("filter-mode");
      if ("Off".equals(mode)) {
         return true;
      } else {
         boolean listed = this.filteredBlocks().contains(block);
         return "Whitelist".equals(mode) ? listed : !listed;
      }
   }

   private Set<Block> filteredBlocks() {
      String raw = this.value("blocks");
      if (raw.equals(this.cachedFilterRaw)) {
         return this.cachedFilterBlocks;
      } else {
         Set<Block> blocks = new HashSet<>();

         for (String entry : this.list("blocks")) {
            Identifier id = Identifier.tryParse(RegistryListCodec.normalizeId(entry));
            if (id != null) {
               BuiltInRegistries.BLOCK.getOptional(id).ifPresent(block -> {
                  if (isPlaceableBlockChoice(block)) {
                     blocks.add(block);
                  }
               });
            }
         }

         this.cachedFilterRaw = raw;
         this.cachedFilterBlocks = Set.copyOf(blocks);
         return this.cachedFilterBlocks;
      }
   }

   private void refreshSlotReset() {
      if (this.bool("switch-back") && this.originalSlot >= 0) {
         this.slotResetTicks = 5;
      }
   }

   private void refreshSelectionReset() {
      this.refreshSlotReset();
   }

   private void tickSlotReset() {
      if (!this.bool("switch-back")) {
         this.originalSlot = -1;
         this.switchedToSlot = -1;
         this.slotResetTicks = 0;
      } else if (this.originalSlot >= 0 && MC != null && MC.player != null) {
         if (this.switchedToSlot >= 0 && MC.player.getInventory().getSelectedSlot() != this.switchedToSlot) {
            this.originalSlot = -1;
            this.requestedSlot = -1;
            this.switchedToSlot = -1;
            this.slotResetTicks = 0;
         } else if (this.slotResetTicks > 0) {
            this.slotResetTicks--;
         } else {
            int selected = MC.player.getInventory().getSelectedSlot();
            if (selected == this.originalSlot) {
               this.originalSlot = -1;
               this.requestedSlot = -1;
               this.switchedToSlot = -1;
            } else if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
               this.slotResetTicks = 1;
            } else {
               try {
                  this.requestedSlot = this.originalSlot;
                  RiptideInputClicker.queueHotbarSlot(this.originalSlot);
               } finally {
                  RiptideHandArbiter.endHandPacketGroup(this.id());
               }
            }
         }
      }
   }

   private RiptideRotationUtil.Rotation serverRotation() {
      if (this.serverRotation != null) {
         return this.serverRotation;
      } else {
         return MC != null && MC.player != null ? RiptideRotationUtil.playerRotation(MC.player) : new RiptideRotationUtil.Rotation(0.0F, 0.0F);
      }
   }

   private static double rotationAngle(RiptideRotationUtil.Rotation first, RiptideRotationUtil.Rotation second) {
      double cosine = Mth.clamp(lookVector(first).dot(lookVector(second)), -1.0, 1.0);
      return Math.toDegrees(Math.acos(cosine));
   }

   private static Vec3 lookVector(RiptideRotationUtil.Rotation rotation) {
      float yaw = rotation.yaw() * (float) (Math.PI / 180.0);
      float pitch = rotation.pitch() * (float) (Math.PI / 180.0);
      float cosPitch = Mth.cos(pitch);
      return new Vec3(-Mth.sin(yaw) * cosPitch, -Mth.sin(pitch), Mth.cos(yaw) * cosPitch);
   }

   private ScaffoldModule.MovementLine buildMovementLine(Input input) {
      Vec3 direction = this.grimLaneStepDirection();
      ScaffoldModule.SupportReference support = this.findSupportReferenceUnderPlayer();
      if (support == null) {
         if (!this.isGrimFamily()) {
            return null;
         } else {
            Vec3 position = MC.player.position();
            ScaffoldModule.MovementLine previous = this.currentMovementLine;
            Vec3 anchor = grimCornerLineAnchor(
               position,
               previous == null ? null : previous.origin(),
               previous == null ? null : previous.direction(),
               direction,
               this.supportMissTicks <= 2 ? this.lastSupportPosition : null
            );
            return new ScaffoldModule.MovementLine(new Vec3(anchor.x, position.y, anchor.z), direction);
         }
      } else {
         this.lastSupportReference = support;
         ScaffoldModule.MovementLine placedLine = this.fitLineThroughLastPlacements();
         Vec3 anchor;
         if (placedLine != null && placedLine.direction().dot(direction) >= 0.5) {
            anchor = nearestPointOnLine(placedLine, MC.player.position());
         } else {
            anchor = new Vec3(support.blockPos().getX() + 0.5, MC.player.getY(), support.blockPos().getZ() + 0.5);
         }

         return new ScaffoldModule.MovementLine(new Vec3(anchor.x, MC.player.getY(), anchor.z), direction);
      }
   }

   private ScaffoldModule.MovementLine retainGrimEdgeLine(ScaffoldModule.MovementLine requested, boolean edgeActive) {
      ScaffoldModule.MovementLine retained = grimEdgeIntentLine(requested, this.grimEdgeLockedLine, edgeActive, MC.player.getY());
      if (edgeActive && requested != null) {
         if (this.grimEdgeLockedLine == null) {
            this.grimEdgeLockedLine = requested;
         }

         return retained;
      } else {
         this.grimEdgeLockedLine = null;
         return retained;
      }
   }

   static ScaffoldModule.MovementLine grimEdgeIntentLine(
      ScaffoldModule.MovementLine requested, ScaffoldModule.MovementLine locked, boolean edgeActive, double playerY
   ) {
      if (edgeActive && requested != null) {
         ScaffoldModule.MovementLine selected = locked == null ? requested : locked;
         Vec3 origin = selected.origin();
         return new ScaffoldModule.MovementLine(new Vec3(origin.x, playerY, origin.z), selected.direction());
      } else {
         return requested;
      }
   }

   static Vec3 grimCornerLineAnchor(Vec3 position, Vec3 previousOrigin, Vec3 previousDirection, Vec3 requestedDirection, BlockPos lastSupport) {
      if (position == null) {
         return Vec3.ZERO;
      } else if (previousOrigin != null
         && previousDirection != null
         && requestedDirection != null
         && previousDirection.dot(requestedDirection) >= 0.5
         && previousDirection.lengthSqr() > 1.0E-12) {
         double parameter = position.subtract(previousOrigin).dot(previousDirection) / previousDirection.lengthSqr();
         return previousOrigin.add(previousDirection.scale(parameter));
      } else {
         return lastSupport != null ? new Vec3(lastSupport.getX() + 0.5, position.y, lastSupport.getZ() + 0.5) : position;
      }
   }

   private ScaffoldModule.MovementLine fitLineThroughLastPlacements() {
      if (this.lastPlacedBlocks.size() < 2) {
         return null;
      } else {
         Iterator<BlockPos> iterator = this.lastPlacedBlocks.descendingIterator();
         BlockPos last = iterator.next();
         Vec3 lastCenter = new Vec3(last.getX() + 0.5, last.getY(), last.getZ() + 0.5);

         while (iterator.hasNext()) {
            BlockPos previous = iterator.next();
            Vec3 previousCenter = new Vec3(previous.getX() + 0.5, previous.getY(), previous.getZ() + 0.5);
            Vec3 direction = new Vec3(lastCenter.x - previousCenter.x, 0.0, lastCenter.z - previousCenter.z);
            if (!(direction.lengthSqr() <= 1.0E-8)) {
               return new ScaffoldModule.MovementLine(previousCenter.add(lastCenter).scale(0.5), direction.normalize());
            }
         }

         return null;
      }
   }

   private ScaffoldModule.SupportReference findSupportReferenceUnderPlayer() {
      List<ScaffoldModule.SupportCandidate> candidates = new ArrayList<>(SUPPORT_SAMPLES.length * SUPPORT_SAMPLES.length);
      Set<BlockPos> visited = new HashSet<>();
      Vec3 playerPosition = MC.player.position();

      for (double xOffset : SUPPORT_SAMPLES) {
         for (double zOffset : SUPPORT_SAMPLES) {
            BlockPos pos = BlockPos.containing(playerPosition.x + xOffset, playerPosition.y - 1.0, playerPosition.z + zOffset);
            if (visited.add(pos)) {
               ScaffoldModule.SupportCandidate candidate = this.createSupportCandidate(pos);
               if (candidate != null) {
                  candidates.add(candidate);
               }
            }
         }
      }

      if (!candidates.isEmpty()) {
         ScaffoldModule.SupportCandidate best = candidates.stream().min(ScaffoldModule::compareSupportCandidates).orElse(null);
         if (best == null) {
            return null;
         } else {
            this.supportMissTicks = 0;
            this.supportMissClientTick = Integer.MIN_VALUE;
            ScaffoldModule.SupportCandidate chosen = this.stableSupportCandidate(candidates, best);
            this.lastSupportPosition = chosen.blockPos();
            return new ScaffoldModule.SupportReference(
               chosen.blockPos(), playerPosition.x - (chosen.blockPos().getX() + 0.5), playerPosition.z - (chosen.blockPos().getZ() + 0.5)
            );
         }
      } else if (!this.isGrimFamily()) {
         this.lastSupportPosition = null;
         this.lastSupportReference = null;
         this.supportMissTicks = 0;
         this.supportMissClientTick = Integer.MIN_VALUE;
         return null;
      } else {
         int clientTick = RiptideSharedState.get().getClientTickCounter();
         if (this.supportMissClientTick != clientTick) {
            this.supportMissClientTick = clientTick;
            this.supportMissTicks++;
         }

         if (this.supportMissTicks > 2) {
            this.lastSupportPosition = null;
            this.lastSupportReference = null;
         }

         return null;
      }
   }

   private ScaffoldModule.SupportCandidate createSupportCandidate(BlockPos pos) {
      BlockState state = MC.level.getBlockState(pos);
      VoxelShape shape = state.getCollisionShape(MC.level, pos, CollisionContext.of(MC.player));
      if (shape.isEmpty()) {
         return null;
      } else {
         AABB playerBox = MC.player.getBoundingBox();
         double bestSurfaceDelta = Double.POSITIVE_INFINITY;
         double overlapAtBestSurface = 0.0;

         for (AABB local : shape.toAabbs()) {
            double minX = pos.getX() + local.minX;
            double maxX = pos.getX() + local.maxX;
            double maxY = pos.getY() + local.maxY;
            double minZ = pos.getZ() + local.minZ;
            double maxZ = pos.getZ() + local.maxZ;
            double overlapX = Math.min(playerBox.maxX, maxX) - Math.max(playerBox.minX, minX);
            double overlapZ = Math.min(playerBox.maxZ, maxZ) - Math.max(playerBox.minZ, minZ);
            if (!(overlapX <= 0.0) && !(overlapZ <= 0.0)) {
               double surfaceDelta = Math.abs(playerBox.minY - maxY);
               double overlap = overlapX * overlapZ;
               if (surfaceDelta + 0.001 < bestSurfaceDelta) {
                  bestSurfaceDelta = surfaceDelta;
                  overlapAtBestSurface = overlap;
               } else if (Math.abs(surfaceDelta - bestSurfaceDelta) <= 0.001) {
                  overlapAtBestSurface += overlap;
               }
            }
         }

         if (!Double.isFinite(bestSurfaceDelta)) {
            return null;
         } else {
            double dx = MC.player.getX() - (pos.getX() + 0.5);
            double dz = MC.player.getZ() - (pos.getZ() + 0.5);
            return new ScaffoldModule.SupportCandidate(pos.immutable(), overlapAtBestSurface, bestSurfaceDelta, dx * dx + dz * dz);
         }
      }
   }

   private ScaffoldModule.SupportCandidate stableSupportCandidate(List<ScaffoldModule.SupportCandidate> candidates, ScaffoldModule.SupportCandidate best) {
      BlockPos lastPlaced = this.lastPlacedBlocks.peekLast();
      ScaffoldModule.SupportCandidate preferred = candidateAt(candidates, lastPlaced);
      if (preferred != null && supportIsStable(preferred, best)) {
         return preferred;
      } else {
         preferred = candidateAt(candidates, this.lastSupportPosition);
         return preferred != null && supportIsStable(preferred, best) ? preferred : best;
      }
   }

   private static ScaffoldModule.SupportCandidate candidateAt(List<ScaffoldModule.SupportCandidate> candidates, BlockPos position) {
      if (position == null) {
         return null;
      } else {
         for (ScaffoldModule.SupportCandidate candidate : candidates) {
            if (candidate.blockPos().equals(position)) {
               return candidate;
            }
         }

         return null;
      }
   }

   private static boolean supportIsStable(ScaffoldModule.SupportCandidate candidate, ScaffoldModule.SupportCandidate best) {
      return candidate.surfaceDelta() <= best.surfaceDelta() + 0.001 && candidate.overlapArea() + 0.02 >= best.overlapArea();
   }

   private static int compareSupportCandidates(ScaffoldModule.SupportCandidate first, ScaffoldModule.SupportCandidate second) {
      if (first.surfaceDelta() + 0.001 < second.surfaceDelta()) {
         return -1;
      } else if (second.surfaceDelta() + 0.001 < first.surfaceDelta()) {
         return 1;
      } else if (first.overlapArea() > second.overlapArea() + 0.02) {
         return -1;
      } else {
         return first.overlapArea() + 0.02 < second.overlapArea() ? 1 : Double.compare(first.horizontalDistanceSqr(), second.horizontalDistanceSqr());
      }
   }

   private Vec3 predictedPlacementPosition(ScaffoldModule.MovementLine line) {
      Vec3 playerPosition = MC.player.position();
      if (line == null) {
         return playerPosition;
      } else {
         boolean nearEdge = this.isCloseToEdge(0.05, playerPosition);
         Vec3 fallOff = this.findFallOffPosition(line);
         if (fallOff == null) {
            return playerPosition;
         } else if (grimGapPredictionApplies(this.isGrimFamily(), nearEdge, this.grimEdgeSneakActive)) {
            return grimEdgePlacementPrediction(fallOff, line.direction());
         } else if (nearEdge) {
            return playerPosition;
         } else {
            Vec3 delta = fallOff.subtract(playerPosition);
            double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            Vec3 bootstrap = horizontalDistance <= 1.0E-8 ? fallOff : fallOff.subtract(new Vec3(delta.x, 0.0, delta.z).scale(0.2 / horizontalDistance));
            Vec3 average = this.averagePlacementOffset();
            if (average == null) {
               ScaffoldModule.SupportReference support = this.lastSupportReference;
               return support == null ? bootstrap : bootstrap.add(support.offsetX(), 0.0, support.offsetZ());
            } else {
               float angle = (float)Math.atan2(line.direction().z, line.direction().x);
               Vec3 historyPosition = fallOff.add(average.yRot(-angle));
               double blend = Math.min(1.0, this.placementOffsets.size() / 2.0);
               return bootstrap.lerp(historyPosition, blend);
            }
         }
      }
   }

   static boolean footprintOverlapsColumn(Vec3 position, Vec3 velocity, BlockPos cell) {
      if (position != null && velocity != null && cell != null) {
         double surfaceY = cell.getY() + 1.0;
         double x = position.x;
         double y = position.y;
         double z = position.z;
         double vx = velocity.x;
         double vy = velocity.y;
         double vz = velocity.z;

         for (int tick = 0; tick < 20 && y > surfaceY; tick++) {
            x += vx;
            y += vy;
            z += vz;
            double speed = Math.sqrt(vx * vx + vz * vz);
            double push = speed <= 1.0E-6 ? 0.0 : 0.02 / speed;
            vx = vx * 0.91 + vx * push;
            vz = vz * 0.91 + vz * push;
            vy = (vy - 0.08) * 0.98;
         }

         double overlapX = Math.min(x + 0.29, cell.getX() + 1.0) - Math.max(x - 0.29, (double)cell.getX());
         double overlapZ = Math.min(z + 0.29, cell.getZ() + 1.0) - Math.max(z - 0.29, (double)cell.getZ());
         return overlapX >= 0.15 && overlapZ >= 0.15;
      } else {
         return false;
      }
   }

   static boolean supportTooFarToHold(Vec3 position, BlockPos support, double maxDistance) {
      if (position != null && support != null) {
         double dx = support.getX() + 0.5 - position.x;
         double dz = support.getZ() + 0.5 - position.z;
         return dx * dx + dz * dz > maxDistance * maxDistance;
      } else {
         return false;
      }
   }

   static Vec3 grimEdgePlacementPrediction(Vec3 fallOff, Vec3 direction) {
      if (fallOff == null) {
         return null;
      } else if (direction == null) {
         return fallOff;
      } else {
         Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
         return horizontal.lengthSqr() <= 1.0E-12 ? fallOff : fallOff.add(horizontal.normalize().scale(1.0E-4));
      }
   }

   static Vec3 grimClickGoalEye(Vec3 playerEye, Vec3 faceCenter, Vec3 normal, double lead) {
      double playerPast = playerEye.subtract(faceCenter).dot(normal);
      return playerPast >= lead ? playerEye : playerEye.add(normal.scale(lead - playerPast));
   }

   private Vec3 grimGoalEyeFor(BlockPos support, Direction face, Vec3 selectionEye, Vec3 laneDirection) {
      if (this.isGrimFamily() && MC.player != null) {
         if (laneDirection != null && !(laneDirection.horizontalDistanceSqr() <= 1.0E-12)) {
            Vec3 course = new Vec3(laneDirection.x, 0.0, laneDirection.z).normalize();
            Vec3 leadEye = MC.player.getEyePosition().add(MC.player.getDeltaMovement());
            Vec3 goalEye = grimClickGoalEyeForFace(leadEye, selectionEye, support, face, course, 0.25);
            this.grimLastGoalEye = goalEye == leadEye ? "lead" : "push";
            return goalEye;
         } else {
            return selectionEye;
         }
      } else {
         return selectionEye;
      }
   }

   static Vec3 grimClickGoalEyeForFace(Vec3 playerEye, Vec3 selectionEye, BlockPos support, Direction face, Vec3 course, double lead) {
      if (course == null) {
         return selectionEye;
      } else if (face == Direction.UP) {
         Vec3 farEdge = Vec3.atCenterOf(support).add(course.scale(0.5));
         farEdge = new Vec3(farEdge.x, playerEye.y, farEdge.z);
         return grimClickGoalEye(playerEye, farEdge, course, lead);
      } else {
         Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
         Vec3 faceCenter = Vec3.atCenterOf(support).add(normal.scale(0.5));
         return grimClickGoalEye(playerEye, faceCenter, normal, lead);
      }
   }

   static float grimCrossingPitch(Vec3 eye, BlockPos support, Direction face, float yaw) {
      return grimCrossingPitch(eye, support, face, yaw, 89.3F);
   }

   private static double[] grimFaceCrossingWindow(Vec3 eye, BlockPos support, Direction face, float yaw) {
      return grimFaceCrossingWindow(eye, grimSupportBox(support), face, yaw);
   }

   static double[] grimFaceCrossingWindow(Vec3 eye, AABB support, Direction face, float yaw) {
      if (support == null) {
         return null;
      } else {
         double past = grimEyePastPlane(eye, support, face);
         if (past <= 0.0) {
            return null;
         } else {
            double yawRad = Math.toRadians(yaw);
            double lookX = -Math.sin(yawRad);
            double lookZ = Math.cos(yawRad);
            double toward = -(lookX * face.getStepX() + lookZ * face.getStepZ());
            if (toward <= 0.1) {
               return null;
            } else {
               double run = past / toward;
               double margin = 0.06 * support.getYsize();
               double shallowest = Math.toDegrees(Math.atan2(eye.y - (support.maxY - margin), run));
               double steepest = Math.toDegrees(Math.atan2(eye.y - (support.minY + margin), run));
               return new double[]{shallowest, steepest, run};
            }
         }
      }
   }

   static float grimTwoEyeCrossingPitch(Vec3 primaryEye, Vec3 secondaryEye, BlockPos support, Direction face, float yaw, float maxPitch) {
      return grimTwoEyeCrossingPitch(primaryEye, secondaryEye, grimSupportBox(support), face, yaw, maxPitch);
   }

   static float grimTwoEyeCrossingPitch(Vec3 primaryEye, Vec3 secondaryEye, AABB support, Direction face, float yaw, float maxPitch) {
      double[] window = grimTwoEyeCrossingWindow(primaryEye, secondaryEye, support, face, yaw);
      if (window == null) {
         return Math.min(maxPitch, 89.3F);
      } else {
         double preferred = Math.toDegrees(Math.atan2(primaryEye.y - grimFaceCrossDepthY(support), window[2]));
         return (float)Mth.clamp(Mth.clamp(preferred, window[0], window[1]), -maxPitch, maxPitch);
      }
   }

   static double grimFaceCrossDepthY(AABB support) {
      return support.maxY - 0.5 * support.getYsize();
   }

   static double[] grimTwoEyeCrossingWindow(Vec3 primaryEye, Vec3 secondaryEye, BlockPos support, Direction face, float yaw) {
      return grimTwoEyeCrossingWindow(primaryEye, secondaryEye, grimSupportBox(support), face, yaw);
   }

   static double[] grimTwoEyeCrossingWindow(Vec3 primaryEye, Vec3 secondaryEye, AABB support, Direction face, float yaw) {
      double[] primary = grimFaceCrossingWindow(primaryEye, support, face, yaw);
      if (primary == null) {
         return null;
      } else {
         double low = primary[0];
         double high = primary[1];
         double[] secondary = grimFaceCrossingWindow(secondaryEye, support, face, yaw);
         if (secondary != null) {
            double bothLow = Math.max(low, secondary[0]);
            double bothHigh = Math.min(high, secondary[1]);
            if (bothLow <= bothHigh) {
               low = bothLow;
               high = bothHigh;
            }
         }

         return new double[]{low, high, primary[2]};
      }
   }

   static float grimCrossingPitch(Vec3 eye, BlockPos support, Direction face, float yaw, float maxPitch) {
      return grimCrossingPitch(eye, grimSupportBox(support), face, yaw, maxPitch);
   }

   static float grimCrossingPitch(Vec3 eye, AABB support, Direction face, float yaw, float maxPitch) {
      float park = Math.min(maxPitch, 89.3F);
      double past = grimEyePastPlane(eye, support, face);
      if (past <= 0.0) {
         return park;
      } else {
         double yawRad = Math.toRadians(yaw);
         double lookX = -Math.sin(yawRad);
         double lookZ = Math.cos(yawRad);
         double toward = -(lookX * face.getStepX() + lookZ * face.getStepZ());
         if (toward <= 0.1) {
            return park;
         } else {
            double run = past / toward;
            double drop = eye.y - grimFaceCrossDepthY(support);
            return (float)Mth.clamp(Math.toDegrees(Math.atan2(drop, run)), -maxPitch, maxPitch);
         }
      }
   }

   static double grimEyePastPlane(Vec3 eye, BlockPos support, Direction face) {
      return grimEyePastPlane(eye, grimSupportBox(support), face);
   }

   static double grimEyePastPlane(Vec3 eye, AABB support, Direction face) {
      if (support == null) {
         return 0.0;
      } else {
         double plane = switch (face) {
            case EAST -> support.maxX;
            case WEST -> support.minX;
            case SOUTH -> support.maxZ;
            case NORTH -> support.minZ;
            case DOWN -> support.minY;
            case UP -> support.maxY;
            default -> throw new MatchException(null, null);
         };

         double along = switch (face.getAxis()) {
            case X -> eye.x;
            case Y -> eye.y;
            case Z -> eye.z;
            default -> throw new MatchException(null, null);
         };
         return (along - plane) * (face.getAxisDirection() == AxisDirection.POSITIVE ? 1.0 : -1.0);
      }
   }

   static boolean grimCrossingLandsOnFace(Vec3 eye, BlockPos support, Direction face, float yaw) {
      return grimCrossingLandsOnFace(eye, support, face, yaw, false);
   }

   static double grimTopCrossingRun(Vec3 eye, BlockPos support, float yaw, double margin) {
      return grimTopCrossingRun(eye, grimSupportBox(support), yaw, margin);
   }

   static double grimTopCrossingRun(Vec3 eye, AABB support, float yaw, double margin) {
      double[] span = grimTopCrossingSpan(eye, support, yaw, margin);
      return span == null ? Double.NaN : (span[0] + span[1]) * 0.5;
   }

   static double[] grimTopCrossingSpan(Vec3 eye, BlockPos support, float yaw, double margin) {
      return grimTopCrossingSpan(eye, grimSupportBox(support), yaw, margin);
   }

   static double[] grimTopCrossingSpan(Vec3 eye, AABB support, float yaw, double margin) {
      if (support == null) {
         return null;
      } else {
         double drop = eye.y - support.maxY;
         if (drop <= 1.0E-4) {
            return null;
         } else {
            double yawRad = Math.toRadians(yaw);
            double[] origin = new double[]{eye.x, eye.z};
            double[] look = new double[]{-Math.sin(yawRad), Math.cos(yawRad)};
            double[] low = new double[]{support.minX + margin, support.minZ + margin};
            double[] high = new double[]{support.maxX - margin, support.maxZ - margin};
            double enter = 0.0;
            double exit = Double.MAX_VALUE;

            for (int axis = 0; axis < 2; axis++) {
               if (Math.abs(look[axis]) < 1.0E-6) {
                  if (origin[axis] < low[axis] || origin[axis] > high[axis]) {
                     return null;
                  }
               } else {
                  double first = (low[axis] - origin[axis]) / look[axis];
                  double second = (high[axis] - origin[axis]) / look[axis];
                  enter = Math.max(enter, Math.min(first, second));
                  exit = Math.min(exit, Math.max(first, second));
               }
            }

            return exit <= enter ? null : new double[]{enter, exit};
         }
      }
   }

   static boolean grimTopCrossingLandsOnFace(Vec3 eye, BlockPos support, float yaw, boolean holding) {
      return grimTopCrossingLandsOnFace(eye, grimSupportBox(support), yaw, holding);
   }

   static boolean grimTopCrossingLandsOnFace(Vec3 eye, AABB support, float yaw, boolean holding) {
      return !Double.isNaN(grimTopCrossingRun(eye, support, yaw, holding ? 0.02 : 0.1));
   }

   static float grimTopCrossingPitch(Vec3 eye, BlockPos support, float yaw) {
      return grimTopCrossingPitch(eye, grimSupportBox(support), yaw);
   }

   static float grimTopCrossingPitch(Vec3 eye, AABB support, float yaw) {
      double[] span = grimTopCrossingSpan(eye, support, yaw, 0.1);
      if (span == null) {
         return Float.NaN;
      } else {
         double drop = eye.y - support.maxY;
         double middle = (span[0] + span[1]) * 0.5;
         return (float)Math.toDegrees(Math.atan2(drop, Math.max(middle, 1.0E-4)));
      }
   }

   static boolean grimCrossingLandsOnFace(Vec3 eye, BlockPos support, Direction face, float yaw, boolean holding) {
      return grimCrossingLandsOnFace(eye, grimSupportBox(support), face, yaw, holding);
   }

   static boolean grimCrossingLandsOnFace(Vec3 eye, AABB support, Direction face, float yaw, boolean holding) {
      if (support == null) {
         return false;
      } else {
         double past = grimEyePastPlane(eye, support, face);
         if (past <= 0.0) {
            return true;
         } else {
            double yawRad = Math.toRadians(yaw);
            double lookX = -Math.sin(yawRad);
            double lookZ = Math.cos(yawRad);
            double toward = -(lookX * face.getStepX() + lookZ * face.getStepZ());
            if (toward <= 0.1) {
               return false;
            } else {
               double run = past / toward;
               boolean xAxisFace = face.getStepX() != 0;
               double crossing = xAxisFace ? eye.z + lookZ * run : eye.x + lookX * run;
               double edge = xAxisFace ? support.minZ : support.minX;
               double far = xAxisFace ? support.maxZ : support.maxX;
               boolean corner = Math.abs(Math.abs(lookX) - Math.abs(lookZ)) <= 0.05;
               double margin = corner ? (holding ? -0.14 : -0.12) : (holding ? -0.12 : 0.04);
               return crossing >= edge + margin && crossing <= far - margin;
            }
         }
      }
   }

   static double grimCrossingFraction(Vec3 eye, BlockPos support, Direction face, float yaw) {
      return grimCrossingFraction(eye, grimSupportBox(support), face, yaw);
   }

   static double grimCrossingFraction(Vec3 eye, AABB support, Direction face, float yaw) {
      if (support != null && face.getAxis().isHorizontal()) {
         double past = grimEyePastPlane(eye, support, face);
         if (past <= 0.0) {
            return Double.NaN;
         } else {
            double yawRad = Math.toRadians(yaw);
            double lookX = -Math.sin(yawRad);
            double lookZ = Math.cos(yawRad);
            double toward = -(lookX * face.getStepX() + lookZ * face.getStepZ());
            if (toward <= 0.1) {
               return Double.NaN;
            } else {
               double run = past / toward;
               boolean xAxisFace = face.getStepX() != 0;
               double crossing = xAxisFace ? eye.z + lookZ * run : eye.x + lookX * run;
               double low = xAxisFace ? support.minZ : support.minX;
               double span = xAxisFace ? support.getZsize() : support.getXsize();
               return span <= 1.0E-9 ? Double.NaN : (crossing - low) / span;
            }
         }
      } else {
         return Double.NaN;
      }
   }

   private static boolean grimCrossingInSquare(Vec3 eye, BlockPos support, Direction face, float yaw) {
      double frac = grimCrossingFraction(eye, support, face, yaw);
      return !Double.isNaN(frac) && frac >= 0.04 && frac <= 0.96;
   }

   private static float grimLandingNudge(Vec3 eye, Vec3 lead, BlockPos support, Direction face, float fromYaw, float laneYaw) {
      if (!face.getAxis().isHorizontal()) {
         return Float.NaN;
      } else {
         for (int step = 0; step <= 2; step++) {
            if (grimCrossingInSquare(eye.add(lead.scale(step)), support, face, fromYaw)) {
               return Float.NaN;
            }
         }

         for (float off = 1.0F; off <= 18.0F; off++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
               float yaw = Mth.wrapDegrees(fromYaw + sign * off);
               if (grimCrossingInSquare(eye, support, face, yaw) && !(Math.abs(grimLaneOctantResidual(laneYaw, yaw)) > 12.0F)) {
                  return yaw;
               }
            }
         }

         return Float.NaN;
      }
   }

   static float grimGridFlipYaw(Vec3 eye, Vec3 lead, BlockPos support, Direction face, float fromYaw) {
      if (!face.getAxis().isHorizontal()) {
         return Float.NaN;
      } else {
         if (!grimYawCannotTrackFace(eye, lead, support, face, fromYaw)) {
            for (int step = 0; step <= 2; step++) {
               if (grimCrossingInSquare(eye.add(lead.scale(step)), support, face, fromYaw)) {
                  return Float.NaN;
               }
            }
         }

         for (int gridStep = 1; gridStep <= 2; gridStep++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
               float yaw = Mth.wrapDegrees(fromYaw + sign * gridStep * 45.0F);
               if (!grimYawCannotTrackFace(eye, lead, support, face, yaw)) {
                  boolean lands = true;

                  for (int stepx = 0; stepx <= 2 && lands; stepx++) {
                     lands = grimCrossingInSquare(eye.add(lead.scale(stepx)), support, face, yaw);
                  }

                  if (lands) {
                     return yaw;
                  }
               }
            }
         }

         return Float.NaN;
      }
   }

   static float grimTopGridFlipYaw(Vec3 eye, Vec3 lead, BlockPos support, float fromYaw) {
      for (int step = 0; step <= 2; step++) {
         if (!Float.isNaN(grimTopCrossingPitch(eye.add(lead.scale(step)), support, fromYaw))) {
            return Float.NaN;
         }
      }

      for (int gridStep = 1; gridStep <= 2; gridStep++) {
         for (int sign = 1; sign >= -1; sign -= 2) {
            float yaw = Mth.wrapDegrees(fromYaw + sign * gridStep * 45.0F);
            boolean lands = true;

            for (int stepx = 0; stepx <= 2 && lands; stepx++) {
               lands = !Float.isNaN(grimTopCrossingPitch(eye.add(lead.scale(stepx)), support, yaw));
            }

            if (lands) {
               return yaw;
            }
         }
      }

      return Float.NaN;
   }

   static boolean grimYawCannotTrackFace(Vec3 eye, Vec3 lead, BlockPos support, Direction face, float yaw) {
      if (!face.getAxis().isHorizontal()) {
         return false;
      } else {
         double yawRad = Math.toRadians(yaw);
         double toward = -(-Math.sin(yawRad) * face.getStepX() + Math.cos(yawRad) * face.getStepZ());
         if (toward >= 0.25) {
            return false;
         } else {
            double[] real = grimFaceCrossingWindow(eye, support, face, yaw);
            if (real == null) {
               return false;
            } else {
               double[] ahead = grimFaceCrossingWindow(eye.add(lead), support, face, yaw);
               return ahead == null ? false : ahead[1] < real[0] || real[1] < ahead[0];
            }
         }
      }
   }

   static boolean grimFacePinsSoon(Vec3 pinEye, Vec3 leadStep, BlockPos support, Direction face, float pinYaw, boolean holding, int lookahead) {
      for (int step = 0; step <= lookahead; step++) {
         Vec3 eye = step == 0 ? pinEye : pinEye.add(leadStep.scale(step));
         if (grimFacePins(eye, support, face, pinYaw, holding)) {
            return true;
         }
      }

      return false;
   }

   static boolean grimGapPredictionApplies(boolean grimFamily, boolean nearEdge, boolean edgeSneakActive) {
      return grimFamily && (nearEdge || edgeSneakActive);
   }

   static boolean grimExactCornerVisibility(boolean grimFamily, boolean edgeSneakActive, boolean onGround, boolean jumping) {
      return grimFamily && edgeSneakActive && onGround && !jumping;
   }

   private Vec3 findFallOffPosition(ScaffoldModule.MovementLine line) {
      if (line == null) {
         return null;
      } else {
         Vec3 nearest = nearestPointOnLine(line, MC.player.position());
         Vec3 from = nearest.add(0.0, -0.1, 0.0);
         Vec3 to = from.add(line.direction().scale(3.0));
         Vec3 collision = this.findEdgeCollision(from, to);
         return collision == null ? null : new Vec3(collision.x, MC.player.getY(), collision.z);
      }
   }

   private Vec3 grimEdgeProbeDirection() {
      Input input = MC.player.input == null ? Input.EMPTY : MC.player.input.keyPresses;
      Vec3 nextVelocity = MC.player.getDeltaMovement();
      if (nextVelocity.horizontalDistanceSqr() > 9.0E-6) {
         return new Vec3(nextVelocity.x, 0.0, nextVelocity.z).normalize();
      } else {
         return hasDirectionalInput(input) ? Vec3.directionFromRotation(0.0F, this.movementYaw(input)) : Vec3.directionFromRotation(0.0F, MC.player.getYRot());
      }
   }

   private boolean isCloseToEdge(double distance, Vec3 position) {
      Vec3 nextVelocity = MC.player.getDeltaMovement();
      Vec3 direction = this.grimEdgeProbeDirection();
      Vec3 from = position.add(0.0, -0.1, 0.0);
      if (this.findEdgeCollision(from, from.add(direction.scale(distance))) != null) {
         return true;
      } else {
         Vec3 nextPosition = position.add(nextVelocity.x, nextVelocity.y, nextVelocity.z);
         Vec3 positionInTwoTicks = nextPosition.add(nextVelocity.x, 0.0, nextVelocity.z);
         return this.wouldBeCloseToFallOff(position) || this.wouldBeCloseToFallOff(positionInTwoTicks);
      }
   }

   private boolean shouldSneakAtEdge() {
      if (MC.player.onGround()) {
         double lookahead = Mth.clamp(0.12 + MC.player.getDeltaMovement().horizontalDistance() * 1.0, 0.12, 0.35);
         return this.isCloseToEdge(lookahead, MC.player.position());
      } else {
         return MC.player.fallDistance > 0.0 && this.isCloseToEdge(0.1, MC.player.position());
      }
   }

   private ScaffoldModule.FallRisk predictFallRisk() {
      if (MC.player.onGround() && !this.isCloseToEdge(0.35, MC.player.position())) {
         return ScaffoldModule.FallRisk.NONE;
      } else {
         Vec3 position = MC.player.position();
         Vec3 velocity = MC.player.getDeltaMovement();
         double x = position.x;
         double y = position.y;
         double z = position.z;
         double vx = velocity.x;
         double vy = velocity.y;
         double vz = velocity.z;
         double startY = y;

         for (int tick = 0; tick < 12; tick++) {
            vx *= 0.91;
            vy = (vy - 0.08) * 0.98;
            vz *= 0.91;
            x += vx;
            y += vy;
            z += vz;
            if (this.hasSupportBelow(x, y, z, 2)) {
               return ScaffoldModule.FallRisk.NONE;
            }

            double drop = startY - y;
            if (drop > 2.0) {
               return ScaffoldModule.FallRisk.IMMINENT;
            }

            if (drop > 0.6 && tick < 4) {
               return ScaffoldModule.FallRisk.IMMINENT;
            }
         }

         return ScaffoldModule.FallRisk.NONE;
      }
   }

   private boolean hasSupportBelow(double x, double y, double z, int blocks) {
      MutableBlockPos pos = new MutableBlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));

      for (int step = 0; step <= blocks; step++) {
         if (this.isSolidSupport(MC.level.getBlockState(pos), pos)) {
            return true;
         }

         pos.set(pos.getX(), pos.getY() - 1, pos.getZ());
      }

      return false;
   }

   private boolean wouldBeCloseToFallOff(Vec3 position) {
      AABB hitbox = MC.player
         .getDimensions(MC.player.getPose())
         .makeBoundingBox(position)
         .inflate(-0.05, 0.0, -0.05)
         .move(0.0, MC.player.fallDistance - MC.player.maxUpStep(), 0.0);
      return MC.level.noCollision(MC.player, hitbox);
   }

   private Vec3 findEdgeCollision(Vec3 from, Vec3 to) {
      Vec3 line = to.subtract(from);
      if (line.lengthSqr() <= 1.0E-12) {
         return null;
      } else {
         List<AABB> boxes = this.collectSupportBoxes(from, to);
         Vec3 current = from;
         Vec3 extendedFrom = from.add(line.scale(-1000.0));
         Vec3 extendedTo = to.add(line.scale(1000.0));

         while (true) {
            List<AABB> containing = new ArrayList<>();

            for (AABB box : boxes) {
               if (box.contains(current)) {
                  containing.add(box);
               }
            }

            if (containing.isEmpty()) {
               return current;
            }

            for (AABB boxx : containing) {
               if (boxx.contains(to)) {
                  return null;
               }
            }

            Vec3 next = null;
            double nearestToDestination = Double.POSITIVE_INFINITY;

            for (AABB boxxx : containing) {
               Vec3 clipped = (Vec3)boxxx.clip(extendedTo, extendedFrom).orElse(null);
               if (clipped != null) {
                  double distance = clipped.distanceToSqr(to);
                  if (distance < nearestToDestination) {
                     nearestToDestination = distance;
                     next = clipped;
                  }
               }
            }

            if (next == null) {
               return current;
            }

            current = next;
            boxes.removeAll(containing);
         }
      }
   }

   private List<AABB> collectSupportBoxes(Vec3 from, Vec3 to) {
      AABB fromBox = MC.player.getDimensions(Pose.STANDING).makeBoundingBox(from);
      AABB toBox = MC.player.getDimensions(Pose.STANDING).makeBoundingBox(to);
      AABB union = fromBox.minmax(toBox);
      int minX = Mth.floor(union.minX - 0.3 - 1.0E-7);
      int maxX = Mth.floor(union.maxX + 0.3 + 1.0E-7);
      int minY = Mth.floor(union.minY - 0.5 - 1.0E-7);
      int maxY = Mth.floor(union.minY + 1.0E-7);
      int minZ = Mth.floor(union.minZ - 0.3 - 1.0E-7);
      int maxZ = Mth.floor(union.maxZ + 0.3 + 1.0E-7);
      Vec3 line = to.subtract(from);
      Vec3 extendedFrom = from.add(line.scale(-1000.0));
      Vec3 extendedTo = to.add(line.scale(1000.0));
      List<AABB> boxes = new ArrayList<>();

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               BlockPos pos = new BlockPos(x, y, z);
               VoxelShape shape = MC.level.getBlockState(pos).getCollisionShape(MC.level, pos);

               for (AABB local : shape.toAabbs()) {
                  AABB adjusted = new AABB(
                     x + local.minX - 0.3, y + local.minY - 1.0, z + local.minZ - 0.3, x + local.maxX + 0.3, y + local.maxY + 0.55, z + local.maxZ + 0.3
                  );
                  if (adjusted.clip(extendedFrom, extendedTo).isPresent()) {
                     boxes.add(adjusted);
                  }
               }
            }
         }
      }

      return boxes;
   }

   private Vec3 averagePlacementOffset() {
      if (this.placementOffsets.isEmpty()) {
         return null;
      } else {
         Vec3 sum = Vec3.ZERO;

         for (Vec3 offset : this.placementOffsets) {
            sum = sum.add(offset);
         }

         return sum.scale(1.0 / this.placementOffsets.size());
      }
   }

   private static Vec3 nearestPointOnLine(ScaffoldModule.MovementLine line, Vec3 point) {
      Vec3 delta = point.subtract(line.origin());
      double projection = delta.dot(line.direction());
      return line.origin().add(line.direction().scale(projection));
   }

   private static double distanceToLineSqr(ScaffoldModule.MovementLine line, Vec3 point) {
      return nearestPointOnLine(line, point).distanceToSqr(point);
   }

   private static double distanceToBoxSqr(Vec3 point, AABB box) {
      double x = Mth.clamp(point.x, box.minX, box.maxX);
      double y = Mth.clamp(point.y, box.minY, box.maxY);
      double z = Mth.clamp(point.z, box.minZ, box.maxZ);
      return point.distanceToSqr(new Vec3(x, y, z));
   }

   private static double distanceToBoxSqr(ScaffoldModule.MovementLine movementLine, AABB box) {
      ScaffoldModule.InfiniteLine line = new ScaffoldModule.InfiniteLine(movementLine.origin(), movementLine.direction());
      if (lineIntersectsBox(line, box)) {
         return 0.0;
      } else {
         Vec3 p000 = new Vec3(box.minX, box.minY, box.minZ);
         Vec3 p001 = new Vec3(box.minX, box.minY, box.maxZ);
         Vec3 p010 = new Vec3(box.minX, box.maxY, box.minZ);
         Vec3 p011 = new Vec3(box.minX, box.maxY, box.maxZ);
         Vec3 p100 = new Vec3(box.maxX, box.minY, box.minZ);
         Vec3 p101 = new Vec3(box.maxX, box.minY, box.maxZ);
         Vec3 p110 = new Vec3(box.maxX, box.maxY, box.minZ);
         Vec3 p111 = new Vec3(box.maxX, box.maxY, box.maxZ);
         ScaffoldModule.LineSegment3[] edges = new ScaffoldModule.LineSegment3[]{
            new ScaffoldModule.LineSegment3(p000, p001),
            new ScaffoldModule.LineSegment3(p000, p010),
            new ScaffoldModule.LineSegment3(p000, p100),
            new ScaffoldModule.LineSegment3(p111, p110),
            new ScaffoldModule.LineSegment3(p111, p101),
            new ScaffoldModule.LineSegment3(p111, p011),
            new ScaffoldModule.LineSegment3(p001, p011),
            new ScaffoldModule.LineSegment3(p001, p101),
            new ScaffoldModule.LineSegment3(p010, p011),
            new ScaffoldModule.LineSegment3(p010, p110),
            new ScaffoldModule.LineSegment3(p100, p101),
            new ScaffoldModule.LineSegment3(p100, p110)
         };
         double best = Double.POSITIVE_INFINITY;

         for (ScaffoldModule.LineSegment3 edge : edges) {
            ScaffoldModule.NearestPair pair = nearestPoints(edge, line);
            if (pair != null) {
               best = Math.min(best, pair.first().distanceToSqr(pair.second()));
            }
         }

         return best;
      }
   }

   private static boolean lineIntersectsBox(ScaffoldModule.InfiniteLine line, AABB box) {
      double enter = Double.NEGATIVE_INFINITY;
      double exit = Double.POSITIVE_INFINITY;
      double[] anchors = new double[]{line.anchor().x, line.anchor().y, line.anchor().z};
      double[] directions = new double[]{line.direction().x, line.direction().y, line.direction().z};
      double[] minimums = new double[]{box.minX, box.minY, box.minZ};
      double[] maximums = new double[]{box.maxX, box.maxY, box.maxZ};

      for (int axis = 0; axis < 3; axis++) {
         if (Mth.equal(directions[axis], 0.0)) {
            if (anchors[axis] < minimums[axis] || anchors[axis] > maximums[axis]) {
               return false;
            }
         } else {
            double first = (minimums[axis] - anchors[axis]) / directions[axis];
            double second = (maximums[axis] - anchors[axis]) / directions[axis];
            enter = Math.max(enter, Math.min(first, second));
            exit = Math.min(exit, Math.max(first, second));
            if (enter > exit + 1.0E-9) {
               return false;
            }
         }
      }

      return true;
   }

   private static boolean hasDirectionalInput(Input input) {
      return input != null && (input.forward() != input.backward() || input.left() != input.right());
   }

   private float movementYaw(Input input) {
      return MC.player.getYRot() + inputOctantDegrees(input);
   }

   private float tellyMovementYaw(LocalPlayer player) {
      Input move = new Input(
         physicallyDown(MC.options.keyUp),
         physicallyDown(MC.options.keyDown),
         physicallyDown(MC.options.keyLeft),
         physicallyDown(MC.options.keyRight),
         false,
         false,
         false
      );
      if (!hasDirectionalInput(move)) {
         return Float.isFinite(this.tellyAnchorYaw) ? this.tellyAnchorYaw : player.getYRot();
      } else {
         return this.movementYaw(move);
      }
   }

   private boolean grimCourseFrozen() {
      return MC.player != null && !MC.player.onGround() && MC.player.getDeltaMovement().y < -0.08;
   }

   private Vec3 stabilizedPointOnFace(
      ScaffoldModule.FaceRect face,
      ScaffoldModule.FaceRect fullFace,
      BlockPos targetPos,
      Vec3 eye,
      RiptideRotationUtil.Rotation currentRotation,
      ScaffoldModule.MovementLine optimalLine
   ) {
      Vec3 offset = new Vec3(targetPos.getX(), targetPos.getY(), targetPos.getZ());
      ScaffoldModule.FaceRect trimmed = face.trim(0.15).offset(offset);
      ScaffoldModule.FaceRect targetFace = this.stabilizedTargetFace(trimmed, eye, optimalLine);
      Vec3 point = nearestPointToFace(targetFace, new ScaffoldModule.InfiniteLine(eye, lookVector(currentRotation)));
      if (this.isGrimFamily()) {
         point = clampToCenterWindow(point, fullFace, offset);
      }

      return point.subtract(offset);
   }

   private static Vec3 clampToCenterWindow(Vec3 point, ScaffoldModule.FaceRect face, Vec3 offset) {
      double cx = offset.x + (face.from().x + face.to().x) * 0.5;
      double cy = offset.y + (face.from().y + face.to().y) * 0.5;
      double cz = offset.z + (face.from().z + face.to().z) * 0.5;
      double hx = centerWindowHalf(face.to().x - face.from().x);
      double hy = centerWindowHalf(face.to().y - face.from().y);
      double hz = centerWindowHalf(face.to().z - face.from().z);
      return new Vec3(Mth.clamp(point.x, cx - hx, cx + hx), Mth.clamp(point.y, cy - hy, cy + hy), Mth.clamp(point.z, cz - hz, cz + hz));
   }

   private static double centerWindowHalf(double span) {
      return Math.max(0.0, Math.min(0.2, span * 0.5 - 0.15));
   }

   private ScaffoldModule.FaceRect stabilizedTargetFace(ScaffoldModule.FaceRect trimmedFace, Vec3 eye, ScaffoldModule.MovementLine optimalLine) {
      if (optimalLine == null) {
         return trimmedFace;
      } else {
         Vec3 nearest = nearestPointOnLine(optimalLine, MC.player.position());
         Vec3 directionToLine = MC.player.position().subtract(nearest).normalize();
         Vec3 collision = planeIntersection(trimmedFace, new ScaffoldModule.InfiniteLine(eye, optimalLine.direction()));
         if (collision == null) {
            return trimmedFace;
         } else {
            Vec3 b = MC.player.position().add(directionToLine.scale(2.0));
            AABB crop = new AABB(collision.x, MC.player.getY() - 2.0, collision.z, b.x, MC.player.getY() + 1.0, b.z);
            ScaffoldModule.FaceRect clamped = trimmedFace.clamp(crop);
            return clamped.area() < 1.0E-4 ? trimmedFace : clamped;
         }
      }
   }

   private static int compareFaceSamples(ScaffoldModule.FaceSample first, ScaffoldModule.FaceSample second) {
      int normal = Double.compare(faceNormalDistance(first), faceNormalDistance(second));
      return normal != 0 ? normal : Double.compare(first.point().y, second.point().y);
   }

   private static double faceNormalDistance(ScaffoldModule.FaceSample sample) {
      Vec3 centered = sample.point().subtract(0.5, 0.5, 0.5);
      double x = centered.x * sample.side().getStepX();
      double y = centered.y * sample.side().getStepY();
      double z = centered.z * sample.side().getStepZ();
      return x * x + y * y + z * z;
   }

   private static Vec3 nearestPointToFace(ScaffoldModule.FaceRect face, ScaffoldModule.InfiniteLine line) {
      Vec3 intersection = planeIntersection(face, line);
      List<ScaffoldModule.LineSegment3> edges = face.edges();
      Vec3 center = face.center();
      if (intersection != null) {
         boolean inside = true;
         Iterator bestDistance = edges.iterator();

         while (true) {
            if (bestDistance.hasNext()) {
               ScaffoldModule.LineSegment3 edge = (ScaffoldModule.LineSegment3)bestDistance.next();
               Vec3 edgeCenter = edge.pointAt(0.5);
               if (!(edgeCenter.subtract(intersection).dot(edgeCenter.subtract(center)) <= 0.0)) {
                  continue;
               }

               inside = false;
            }

            if (edges.isEmpty() || inside) {
               return intersection;
            }
            break;
         }
      }

      Vec3 bestPoint = null;
      double bestDistance = Double.POSITIVE_INFINITY;

      for (ScaffoldModule.LineSegment3 edge : edges) {
         ScaffoldModule.NearestPair pair = nearestPoints(edge, line);
         if (pair != null) {
            double distance = pair.first().distanceToSqr(pair.second());
            if (distance < bestDistance) {
               bestDistance = distance;
               bestPoint = pair.first();
            }
         }
      }

      return bestPoint != null ? bestPoint : (intersection != null ? intersection : center);
   }

   private static Vec3 planeIntersection(ScaffoldModule.FaceRect face, ScaffoldModule.InfiniteLine line) {
      Vec3 dimensions = face.dimensions();
      double plane;
      double anchor;
      double direction;
      if (Mth.equal(dimensions.x, 0.0)) {
         plane = face.from().x;
         anchor = line.anchor().x;
         direction = line.direction().x;
      } else if (Mth.equal(dimensions.y, 0.0)) {
         plane = face.from().y;
         anchor = line.anchor().y;
         direction = line.direction().y;
      } else {
         if (!Mth.equal(dimensions.z, 0.0)) {
            return null;
         }

         plane = face.from().z;
         anchor = line.anchor().z;
         direction = line.direction().z;
      }

      if (Mth.equal(direction, 0.0)) {
         return null;
      } else {
         double parameter = (plane - anchor) / direction;
         return Double.isFinite(parameter) ? line.pointAt(parameter) : null;
      }
   }

   private static ScaffoldModule.NearestPair nearestPoints(ScaffoldModule.LineSegment3 segment, ScaffoldModule.InfiniteLine line) {
      Vec3 firstDirection = segment.direction();
      Vec3 secondDirection = line.direction();
      Vec3 delta = segment.start().subtract(line.anchor());
      double a = firstDirection.dot(firstDirection);
      double b = firstDirection.dot(secondDirection);
      double c = secondDirection.dot(secondDirection);
      double d = firstDirection.dot(delta);
      double e = secondDirection.dot(delta);
      double determinant = a * c - b * b;
      ScaffoldModule.NearestCandidate best = null;
      if (Math.abs(determinant) > 1.0E-9) {
         best = chooseNearest(best, segment, line, (b * e - c * d) / determinant, (a * e - b * d) / determinant);
      }

      best = chooseNearest(best, segment, line, 0.0, e / c);
      best = chooseNearest(best, segment, line, 1.0, (b + e) / c);
      best = chooseNearest(best, segment, line, Mth.clamp(-d / a, 0.0, 1.0), 0.0);
      best = chooseNearest(best, segment, line, 0.0, e / c);
      return best == null ? null : new ScaffoldModule.NearestPair(best.first(), best.second());
   }

   private static ScaffoldModule.NearestCandidate chooseNearest(
      ScaffoldModule.NearestCandidate best,
      ScaffoldModule.LineSegment3 segment,
      ScaffoldModule.InfiniteLine line,
      double firstParameter,
      double secondParameter
   ) {
      if (Double.isFinite(firstParameter) && Double.isFinite(secondParameter) && !(firstParameter < -1.0E-9) && !(firstParameter > 1.000000001)) {
         double first = Mth.clamp(firstParameter, 0.0, 1.0);
         Vec3 firstPoint = segment.pointAt(first);
         Vec3 secondPoint = line.pointAt(secondParameter);
         double distance = firstPoint.distanceToSqr(secondPoint);
         return best != null && !(distance < best.distance() - 1.0E-9) ? best : new ScaffoldModule.NearestCandidate(firstPoint, secondPoint, distance);
      } else {
         return best;
      }
   }

   private boolean isSolidSupport(BlockState state, BlockPos pos) {
      return standableSupportState(state, MC.level, pos, CollisionContext.of(MC.player));
   }

   static boolean standableSupportState(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      if (state == null || state.isAir()) {
         return false;
      } else if (state.isFaceSturdy(level, pos, Direction.UP, SupportType.CENTER)) {
         return true;
      } else {
         VoxelShape collision = state.getCollisionShape(level, pos, context);
         return collision.isEmpty() ? false : !Shapes.joinIsNotEmpty(collision.getFaceShape(Direction.UP), STANDABLE_CENTER_SHAPE, BooleanOp.ONLY_SECOND);
      }
   }

   private boolean isTellySupport(BlockState state, BlockPos pos) {
      return tellySupportState(state, MC.level, pos, CollisionContext.of(MC.player));
   }

   static boolean tellySupportState(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return !state.isAir() && !state.canBeReplaced() && !state.getCollisionShape(level, pos, context).isEmpty();
   }

   public static void onServerPositionCorrection() {
      if (ModuleRegistry.get("scaffold") instanceof ScaffoldModule scaffold && scaffold.isEnabled()) {
         scaffold.grimServerCorrectionReset();
      }
   }

   public static void onServerRotationApplied(float appliedYaw, float appliedPitch) {
      grimSentYaw = appliedYaw;
      grimSentYawTick = RiptideSharedState.get().getClientTickCounter();
      if (ModuleRegistry.get("scaffold") instanceof ScaffoldModule scaffold && scaffold.isEnabled()) {
         scaffold.serverRotation = new RiptideRotationUtil.Rotation(appliedYaw, appliedPitch);
         scaffold.grimTracePrevSentYaw = appliedYaw;
      }
   }

   private void grimServerCorrectionReset() {
      this.resetGrimLaunchReservation();
      this.grimStickyTarget = null;
      this.grimStickySetTick = -1;
      this.grimStickyBandMissTicks = 0;
      this.grimStickyPitchMissTicks = 0;
      this.grimPinSupport = null;
      this.grimPinFace = null;
      this.grimRiseTakeoffLatch = null;
      this.grimRiseTakeoffLatchTicks = 0;
      this.grimRiseFloorCell = null;
      this.grimRiseFloorTick = Integer.MIN_VALUE;
      this.grimAimMissSupport = null;
      this.grimAimMissFace = null;
      this.grimAimMissStreak = 0;
      this.grimAimWindowWaitTicks = 0;
      this.grimAimOccludedTicks = 0;
      this.grimStaleSupport = null;
      this.grimStaleFace = null;
      this.grimStaleTicks = 0;
      this.grimEffCell = null;
      this.grimEffCellRefreshTick = Integer.MIN_VALUE;
      this.grimPrevTickPos = null;
      this.grimLastTickStep = Vec3.ZERO;
      this.grimArcTicks = 0;
      this.grimArcPlacements = 0;
      this.grimAirborneBuiltRow = Integer.MIN_VALUE;
      this.grimPredictedPlacements.clear();
      if (this.grimAttemptState != ScaffoldModule.GrimPlacementAttemptState.IDLE) {
         this.failGrimPlacementAttempt("position-correction");
      }

      if (this.traceArmed() && MC != null && MC.player != null) {
         Vec3 p = MC.player.position();
         RiptideTraceLog.println(
            String.format(Locale.ROOT, "[scaffold-live] t%03d setback     pos=%.2f,%.2f,%.2f - position state cleared", this.grimLiveTraceTicks, p.x, p.y, p.z)
         );
      }
   }

   private void clearRuntime(boolean restoreSlot) {
      RiptideInputClicker.cancelScaffoldUseClick();
      if (restoreSlot
         && this.bool("switch-back")
         && this.originalSlot >= 0
         && MC != null
         && MC.player != null
         && !MultiPilot.isActive()
         && !MacroExecutor.isRunning()
         && MC.player.getInventory().getSelectedSlot() != this.originalSlot
         && RiptideHandArbiter.beginHandPacketGroup(this.id())) {
         try {
            RiptideInputClicker.queueHotbarSlot(this.originalSlot);
         } finally {
            RiptideHandArbiter.endHandPacketGroup(this.id());
         }
      }

      this.originalSlot = -1;
      this.requestedSlot = -1;
      this.switchedToSlot = -1;
      this.slotResetTicks = 0;
      this.selectionPending = false;
      this.serverRotation = MC != null && MC.player != null ? RiptideRotationUtil.playerRotation(MC.player) : null;
      this.releaseGrimStreamNow();
      this.rollGrimSessionOffsets();
      this.grimStickyTarget = null;
      this.grimStickySetTick = -1;
      this.grimRealPendingTarget = null;
      this.grimRealPendingLine = null;
      this.grimRealPendingFallOff = null;
      this.grimRealQueuedTick = Integer.MIN_VALUE;
      this.grimAttemptState = ScaffoldModule.GrimPlacementAttemptState.IDLE;
      this.grimAttemptGeneration = 0L;
      this.grimAttemptHand = null;
      this.grimAttemptBuildsPlannedCell = false;
      this.grimAttemptSubmittedCount = 0;
      this.grimAttemptDuplicateSubmitted = false;
      this.grimAttemptWriteCount = 0;
      this.grimCommittedClickRotation = null;
      this.grimCommittedPreviousRotation = null;
      this.grimAttemptSequence = -1;
      this.grimAttemptResultSeen = false;
      this.grimAttemptResultConsumed = false;
      this.grimAttemptPaceBooked = false;
      this.grimAttemptResult = "--";
      this.grimPredictedPlacements.clear();
      this.grimUntrustedPredictions.clear();
      this.grimPredictionLevel = null;
      this.grimHighestObservedAck = Integer.MIN_VALUE;
      this.grimHighestProcessedAck = Integer.MIN_VALUE;
      GRIM_FINAL_USE_WRITES.clear();
      GRIM_FINAL_MOVE_WRITE.set(null);
      this.grimFinalMoveSeen = false;
      this.grimFinalWireGround = false;
      this.grimFinalWireHorizontalCollision = false;
      this.grimFinalWireHasPosition = false;
      this.grimSprintNoForwardTick = Integer.MIN_VALUE;
      this.grimPaceWaitTicks = 0;
      this.grimPitchFreedTick = Integer.MIN_VALUE;
      this.grimStickyBandMissTicks = 0;
      this.grimStickyPitchMissTicks = 0;
      this.grimStickyBandTick = Integer.MIN_VALUE;
      this.grimEffCell = null;
      this.grimEffCellRefreshTick = Integer.MIN_VALUE;
      this.grimPrevTickPos = null;
      this.grimLastTickStep = Vec3.ZERO;
      this.grimTraceEdgeDanger = false;
      this.grimTraceFallDanger = false;
      this.grimTraceLateralBrink = false;
      this.grimTraceFootingOwed = false;
      this.grimFootingOwedTicks = 0;
      this.grimTraceBrake = "--";
      this.grimTraceDiagonalPaceMean = Double.NaN;
      this.grimTraceArcCarry = "--";
      this.grimTraceArcStand = "--";
      this.grimTraceArcTravel = 0.0;
      this.grimCrossingWaitFace = null;
      this.grimCrossingWaitTick = Integer.MIN_VALUE;
      this.grimXingStandSpent = 0.0;
      this.grimXingStandTick = Integer.MIN_VALUE;
      this.grimTracePaceBrink = false;
      this.grimTraceLastChance = false;
      this.grimLastPlaceYaw = Float.NaN;
      this.grimTraceJump = "-";
      this.grimArcTicks = 0;
      this.grimArcPlacements = 0;
      this.grimLaneCorrectHoldTicks = 0;
      this.grimLaneCorrectLockTicks = 0;
      this.grimLastRescueTick = Integer.MIN_VALUE;
      this.grimTraceRiseTakeoff = null;
      this.grimTraceTakeoffWhy = "air";
      this.grimTraceRiseAllowed = false;
      this.grimAimHoldTarget = null;
      this.grimUpFaceSwapCell = null;
      this.grimAimHoldTick = -1;
      this.grimAimHoldServedTick = Integer.MIN_VALUE;
      this.grimTraceClickFeasible = false;
      this.grimTraceClickLands = false;
      this.grimTracePrevTick = Integer.MIN_VALUE;
      this.grimTracePrevSentYaw = Float.NaN;
      this.grimTraceWhy = "--";
      this.grimTraceRiseDropWhy = "--";
      this.grimTracePaceSince = -1L;
      this.grimTracePaceFloor = -1L;
      this.grimTracePaceIntave = false;
      this.grimTracePrevGnd = true;
      this.grimTraceFallNoted = false;
      this.grimArcCarryOrigin = null;
      this.grimTraceLaunchLedger = "--";
      this.grimTraceStrip.setLength(0);
      this.grimTraceClickNumbers = "--";
      this.grimArcStartTick = -1;
      this.grimArcStartClientTick = -1;
      this.grimArcStartPos = null;
      this.grimArcStartGoal = "--";
      this.grimArcPlaceCount = 0;
      this.grimArcSetCount = 0;
      this.grimArcAimTicks = 0;
      this.grimArcPaceTicks = 0;
      this.grimArcNoTargetTicks = 0;
      this.grimArcDropTicks = 0;
      this.grimArcChainRelatch = 0;
      this.grimTraceReserveWhy = "--";
      this.grimResetArcChain(null);
      this.grimSegTicks = 0;
      this.grimSegPlaces = 0;
      this.grimSegSettled = 0;
      this.grimSegMiss = 0;
      this.grimSegAim = 0;
      this.grimSegPace = 0;
      this.grimSegNoTarget = 0;
      this.grimSegDrop = 0;
      this.grimSegReplan = 0;
      this.grimSegVeto = 0;
      this.grimSegRow = Integer.MIN_VALUE;
      grimSentYaw = Float.NaN;
      grimSentYawTick = Integer.MIN_VALUE;
      this.grimPinSupport = null;
      this.grimPinFace = null;
      this.resetGrimLaunchReservation();
      this.grimPhysicalClimbIntent = false;
      this.grimFootingSurfaceY = Integer.MIN_VALUE;
      this.grimLastRowGainTick = Integer.MIN_VALUE;
      this.grimAirborneBuiltRow = Integer.MIN_VALUE;
      this.resetGrimInputOctant();
      this.grimStaleSupport = null;
      this.grimStaleFace = null;
      this.grimStaleTicks = 0;
      this.grimBridgePitchHold = Float.NaN;
      this.grimTraceCrossing = Double.NaN;
      this.resetTellyState();
      this.lastGrimPlacementTick = Integer.MIN_VALUE;
      this.tellyLastGroundedSupport = null;
      this.tellyLastGroundedTick = Integer.MIN_VALUE;
      this.currentMovementLine = null;
      this.grimEdgeSneakActive = false;
      this.grimRiseFloorCell = null;
      this.grimRiseFloorTick = Integer.MIN_VALUE;
      this.grimAimMissSupport = null;
      this.grimAimMissFace = null;
      this.grimAimMissStreak = 0;
      this.grimAimWindowWaitTicks = 0;
      this.grimAimOccludedTicks = 0;
      this.grimIntaveParkClear();
      this.grimLastArmRay = null;
      this.grimNoTargetTicks = 0;
      this.grimPaceJitterMs = 0;
      this.grimPaceSamples.clear();
      this.grimPaceLastBookedNanos = Long.MIN_VALUE;
      this.grimIntavePlaceGaps.clear();
      this.grimIntavePlaceCells.clear();
      this.grimIntavePlaceNanos = Long.MIN_VALUE;
      this.grimIntavePlacePitch = Float.NaN;
      this.grimPaceLastJumpNanos = Long.MIN_VALUE;
      this.grimPaceQueuedNanos = Long.MIN_VALUE;
      this.grimPaceRiserHoldCell = null;
      this.grimPaceRiserHoldTick = Integer.MIN_VALUE;
      this.grimTraceSettledCell = null;
      this.grimYawVetoTicks = 0;
      this.grimGoalVetoTicks = 0;
      this.grimGoalVetoLastErr = Float.NaN;
      this.grimDeadCells.clear();
      this.grimPaceWasOnGround = false;
      this.grimLastSneakTick = Integer.MIN_VALUE;
      this.grimSneakHoldTicks = 0;
      this.grimRiseTakeoffLatch = null;
      this.grimRiseTakeoffLatchTicks = 0;
      this.lastPlacedBlocks.clear();
      this.placementOffsets.clear();
      this.lastSupportPosition = null;
      this.lastSupportReference = null;
      this.supportMissTicks = 0;
      this.supportMissClientTick = Integer.MIN_VALUE;
      this.grimEdgeLockedLine = null;
      this.grimCourseStep = -1;
      this.grimCourseStepCandidate = -1;
      this.grimCourseStepDwell = 0;
      this.grimLaneOctant = 0;
      this.grimPostureYawHeld = Float.NaN;
      this.grimPostureYawCandidate = Float.NaN;
      this.grimPostureYawStreak = 0;
      this.grimPostureYawTick = Integer.MIN_VALUE;
   }

   private static List<BlockPos> normalOffsets() {
      List<BlockPos> offsets = new ArrayList<>(27);

      for (int x = -1; x <= 1; x++) {
         for (int z = -1; z <= 1; z++) {
            offsets.add(new BlockPos(x, 0, z));
            offsets.add(new BlockPos(x, -1, z));
            offsets.add(new BlockPos(x, 1, z));
         }
      }

      offsets.sort(
         Comparator.<BlockPos>comparingDouble(pos -> pos.distSqr(BlockPos.ZERO))
            .thenComparingInt(Vec3i::getY)
            .thenComparingInt(Vec3i::getX)
            .thenComparingInt(Vec3i::getZ)
      );
      return List.copyOf(offsets);
   }

   private boolean isAndromedaMode() {
      return "Andromeda".equals(this.choice("mode"));
   }

   private void runAndromedaPlacement() {
      if (!this.canRun()) {
         this.currentMovementLine = null;
         this.tickSlotReset();
      } else if (!RiptideBlinkManager.holdsActionsWithoutMovement()) {
         if (!MC.player.isSprinting()) {
            this.tickSlotReset();
         } else {
            this.selectionPending = false;
            InteractionHand var1 = this.ensurePlacementHand();
            if (var1 == null) {
               if (this.selectionPending) {
                  this.refreshSelectionReset();
               } else {
                  this.tickSlotReset();
               }
            } else {
               this.refreshSelectionReset();
               ItemStack var2 = MC.player.getItemInHand(var1);
               if (this.isValidBlock(var2)) {
                  BlockPos var3 = this.rageWalkwayCell();
                  if (var3 != null) {
                     BlockPos var4 = this.rageCourseStep();
                     if (RiptidePlacementTick.claim(this.id())) {
                        for (BlockPos var7 : this.andromedaStackCells(var3, var4, 1)) {
                           this.placeRageCell(var7, var1, var2);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private List andromedaStackCells(BlockPos var1, BlockPos var2, int var3) {
      return RiptideAndromeda.cells(var1, var2, var3);
   }

   private record FaceRect(Vec3 from, Vec3 to) {
      private FaceRect(Vec3 from, Vec3 to) {
         Vec3 minimum = new Vec3(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z));
         Vec3 maximum = new Vec3(Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z));
         this.from = minimum;
         this.to = maximum;
      }

      static ScaffoldModule.FaceRect fromBox(AABB box, Direction side) {
         return switch (side) {
            case EAST -> new ScaffoldModule.FaceRect(new Vec3(box.maxX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ));
            case WEST -> new ScaffoldModule.FaceRect(new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.minX, box.maxY, box.maxZ));
            case SOUTH -> new ScaffoldModule.FaceRect(new Vec3(box.minX, box.minY, box.maxZ), new Vec3(box.maxX, box.maxY, box.maxZ));
            case NORTH -> new ScaffoldModule.FaceRect(new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.minZ));
            case DOWN -> new ScaffoldModule.FaceRect(new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.maxZ));
            case UP -> new ScaffoldModule.FaceRect(new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ));
            default -> throw new MatchException(null, null);
         };
      }

      Vec3 dimensions() {
         return this.to.subtract(this.from);
      }

      Vec3 center() {
         return this.from.lerp(this.to, 0.5);
      }

      double area() {
         Vec3 dimensions = this.dimensions();
         return dimensions.x * dimensions.y + dimensions.y * dimensions.z + dimensions.x * dimensions.z;
      }

      ScaffoldModule.FaceRect truncateY(double minimumY) {
         return new ScaffoldModule.FaceRect(
            new Vec3(this.from.x, Math.max(this.from.y, minimumY), this.from.z), new Vec3(this.to.x, Math.max(this.to.y, minimumY), this.to.z)
         );
      }

      ScaffoldModule.FaceRect trim(double amount) {
         Vec3 inset = this.dimensions().scale(amount);
         return new ScaffoldModule.FaceRect(this.from.add(inset), this.to.subtract(inset));
      }

      ScaffoldModule.FaceRect offset(Vec3 offset) {
         return new ScaffoldModule.FaceRect(this.from.add(offset), this.to.add(offset));
      }

      ScaffoldModule.FaceRect clamp(AABB box) {
         return new ScaffoldModule.FaceRect(clampPoint(this.from, box), clampPoint(this.to, box));
      }

      List<ScaffoldModule.LineSegment3> edges() {
         Vec3 dimensions = this.dimensions();
         Vec3 first;
         Vec3 second;
         if (Mth.equal(dimensions.x, 0.0)) {
            first = new Vec3(0.0, dimensions.y, 0.0);
            second = new Vec3(0.0, 0.0, dimensions.z);
         } else if (Mth.equal(dimensions.y, 0.0)) {
            first = new Vec3(dimensions.x, 0.0, 0.0);
            second = new Vec3(0.0, 0.0, dimensions.z);
         } else {
            if (!Mth.equal(dimensions.z, 0.0)) {
               return List.of();
            }

            first = new Vec3(0.0, dimensions.y, 0.0);
            second = new Vec3(dimensions.x, 0.0, 0.0);
         }

         List<ScaffoldModule.LineSegment3> edges = new ArrayList<>(4);
         if (first.lengthSqr() > 1.0E-9) {
            edges.add(new ScaffoldModule.LineSegment3(this.from, this.from.add(first)));
            edges.add(new ScaffoldModule.LineSegment3(this.to, this.to.subtract(first)));
         }

         if (second.lengthSqr() > 1.0E-9) {
            edges.add(new ScaffoldModule.LineSegment3(this.from, this.from.add(second)));
            edges.add(new ScaffoldModule.LineSegment3(this.to, this.to.subtract(second)));
         }

         return edges;
      }

      private static Vec3 clampPoint(Vec3 point, AABB box) {
         return new Vec3(Mth.clamp(point.x, box.minX, box.maxX), Mth.clamp(point.y, box.minY, box.maxY), Mth.clamp(point.z, box.minZ, box.maxZ));
      }
   }

   private record FaceSample(ScaffoldModule.FaceRect face, Vec3 point, Direction side) {
   }

   private static enum FallRisk {
      NONE,
      IMMINENT;
   }

   @FunctionalInterface
   private interface GrimArcCollisionResolver {
      Vec3 resolve(AABB var1, Vec3 var2);
   }

   record GrimAttemptDecision(ScaffoldModule.GrimPlacementAttemptState state, String failure) {
   }

   private record GrimFinalMoveWrite(boolean onGround, boolean horizontalCollision, boolean hasPosition) {
   }

   record GrimFinalUseWrite(
      int sequence,
      BlockPos support,
      Direction face,
      Vec3 location,
      long nanos,
      InteractionHand hand,
      ScaffoldModule.GrimQueuedUse queued,
      RiptideServerRotationView.WireSnapshot wire
   ) {
      GrimFinalUseWrite(int sequence, BlockPos support, Direction face, Vec3 location, long nanos) {
         this(sequence, support, face, location, nanos, InteractionHand.MAIN_HAND, null, null);
      }
   }

   private record GrimPaceSample(long millis, BlockPos placed) {
   }

   private static final class GrimPacketIdentity extends WeakReference<Packet<?>> {
      private final int identityHash;

      GrimPacketIdentity(Packet<?> packet) {
         super(packet);
         this.identityHash = System.identityHashCode(packet);
      }

      GrimPacketIdentity(Packet<?> packet, ReferenceQueue<Packet<?>> queue) {
         super(packet, queue);
         this.identityHash = System.identityHashCode(packet);
      }

      @Override
      public int hashCode() {
         return this.identityHash;
      }

      @Override
      public boolean equals(Object other) {
         if (this == other) {
            return true;
         } else if (!(other instanceof ScaffoldModule.GrimPacketIdentity identity)) {
            return false;
         } else {
            Packet<?> packet = this.get();
            return packet != null && packet == identity.get();
         }
      }
   }

   static enum GrimPlacementAttemptState {
      IDLE,
      ARMED,
      SENT,
      PREDICTED,
      RECONCILING,
      FAILED;
   }

   private record GrimPredictedPlacement(int sequence, BlockPos cell) {
   }

   record GrimQueuedUse(
      long generation, int ordinal, InteractionHand hand, BlockPos placed, BlockPos against, Direction face, RiptideRotationUtil.Rotation clickRotation
   ) {
   }

   static enum GrimReservationNeed {
      CONNECTOR,
      SUPPORT,
      RISER,
      READY;
   }

   record GrimRowLock(boolean xAxis, int rowPerp, int rowY, int lastIndex, int frontNeg, int frontPos, int playerPerp) {
      boolean allows(BlockPos pos, boolean rising) {
         int perp = this.xAxis ? pos.getZ() : pos.getX();
         int index = this.xAxis ? pos.getX() : pos.getZ();
         boolean onRow = perp == this.rowPerp;
         boolean ownColumn = perp == this.playerPerp;
         if (!onRow && !ownColumn) {
            return false;
         } else {
            return pos.getY() == this.rowY
               ? ownColumn || index == this.frontNeg || index == this.frontPos
               : rising && pos.getY() == this.rowY + 1 && (ownColumn || index == this.lastIndex || index == this.frontNeg || index == this.frontPos);
         }
      }

      Direction pinnedFace(BlockPos pos) {
         if (pos.getY() != this.rowY) {
            return null;
         } else {
            int perp = this.xAxis ? pos.getZ() : pos.getX();
            if (perp != this.rowPerp) {
               return null;
            } else {
               int index = this.xAxis ? pos.getX() : pos.getZ();
               if (this.xAxis) {
                  return index > this.lastIndex ? Direction.EAST : Direction.WEST;
               } else {
                  return index > this.lastIndex ? Direction.SOUTH : Direction.NORTH;
               }
            }
         }
      }
   }

   record GrimWireClickRotation(RiptideRotationUtil.Rotation previous, RiptideRotationUtil.Rotation current, int tick) {
   }

   private record InfiniteLine(Vec3 anchor, Vec3 direction) {
      Vec3 pointAt(double parameter) {
         return this.anchor.add(this.direction.scale(parameter));
      }
   }

   private record LineSegment3(Vec3 start, Vec3 end) {
      Vec3 direction() {
         return this.end.subtract(this.start);
      }

      Vec3 pointAt(double parameter) {
         return this.start.add(this.direction().scale(parameter));
      }
   }

   record MovementLine(Vec3 origin, Vec3 direction) {
   }

   private record NearestCandidate(Vec3 first, Vec3 second, double distance) {
   }

   private record NearestPair(Vec3 first, Vec3 second) {
   }

   record PlacementTarget(
      BlockPos supportBlock, BlockPos placedBlock, Direction face, BlockHitResult hit, RiptideRotationUtil.Rotation rotation, double minPlacementY
   ) {
   }

   private record SupportCandidate(BlockPos blockPos, double overlapArea, double surfaceDelta, double horizontalDistanceSqr) {
   }

   private record SupportReference(BlockPos blockPos, double offsetX, double offsetZ) {
   }

   record TargetPlan(BlockPos supportBlock, Direction face) {
   }

   record TellyAirCorrectionState(ScaffoldModule.TellyStrafe pulse, int cooldown, ScaffoldModule.TellyStrafe lastPulse, int pulsesUsed) {
   }

   private record TellyDriftSample(
      double octant, double residual, double forwardAccel, double lateralAccel, double flipMargin, boolean sprint, boolean grounded
   ) {
   }

   private record TellyFaceSample(
      ScaffoldModule.FaceRect worldFace, Vec3 point, RiptideRotationUtil.Rotation rotation, BlockHitResult verifiedHit, int offsetIndex
   ) {
   }

   record TellyGroundSteeringState(boolean active, float offsetDegrees) {
   }

   static enum TellyLandingTransition {
      DWELL,
      CHAIN;
   }

   private static enum TellyMotion {
      RELEASED,
      FORWARD,
      HOLD;
   }

   static enum TellyPhase {
      IDLE,
      RUNNING,
      FORWARD_DWELL,
      RECOVERING,
      LAUNCH,
      AIMING,
      RETURNING;
   }

   private record TellyPlacement(ScaffoldModule.PlacementTarget target, boolean raised) {
   }

   private record TellyRotationGoal(RiptideRotationUtil.Rotation rotation, ScaffoldModule.TellyRotationIntent intent) {
   }

   static enum TellyRotationIntent {
      FORWARD,
      PLACEMENT,
      HOLD,
      RETURN;
   }

   static enum TellyStrafe {
      NONE,
      LEFT,
      RIGHT;
   }
}
