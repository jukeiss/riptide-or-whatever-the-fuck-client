package riptide.modules;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.gui.Font.GlyphVisitor;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import riptide.util.RiptideBufferSource;
import riptide.util.RiptideEspMeshBuffer;
import riptide.util.RiptidePerf;
import riptide.util.RiptideWaypoints;
import riptide.util.RiptideWorldGeometry;
import riptide.util.oresim.RiptideOreGhostModels;
import riptide.util.oresim.RiptideOreSimEngine;
import riptide.util.oresim.RiptideOreSimOre;

public final class ModuleWorldRenderer {
   private static final RiptideBufferSource.Holder TRACER_BUFFERS = new RiptideBufferSource.Holder(4194304);
   private static final RiptideBufferSource.Holder ESP_BUFFERS = new RiptideBufferSource.Holder(4194304);
   private static final RiptideBufferSource.Holder WAYPOINT_BUFFERS = new RiptideBufferSource.Holder(786432);
   private static final RiptideEspMeshBuffer ESP_FILL_MESH = new RiptideEspMeshBuffer("riptide_esp_fill");
   private static final RiptideEspMeshBuffer ESP_WIRE_MESH = new RiptideEspMeshBuffer("riptide_esp_wire");
   private static final Pose BAKE_POSE = new PoseStack().last();
   private static boolean espMeshPathAvailable = true;
   private static boolean espFillMeshReady;
   private static boolean espWireMeshReady;
   private static Vec3 espMeshAnchor = Vec3.ZERO;
   private static int espMeshFlags = Integer.MIN_VALUE;
   private static float espMeshXrayAlpha = Float.NaN;
   private static final long[] espMeshContentIds = new long[6];
   private static final double ESP_MESH_ANCHOR_RANGE = 64.0;
   private static boolean initialized;
   private static ModuleWorldRenderer.StorageSnapshot storageSnapshot = ModuleWorldRenderer.StorageSnapshot.empty();
   private static ModuleWorldRenderer.StorageSnapshot blockSnapshot = ModuleWorldRenderer.StorageSnapshot.empty();
   private static ModuleWorldRenderer.StorageSnapshot barrierSnapshot = ModuleWorldRenderer.StorageSnapshot.empty();
   private static ModuleWorldRenderer.StorageSnapshot spawnerSnapshot = ModuleWorldRenderer.StorageSnapshot.empty();
   private static ModuleWorldRenderer.StorageSnapshot xrayEspSnapshot = ModuleWorldRenderer.StorageSnapshot.empty();
   private static ModuleWorldRenderer.WaypointSnapshot waypointSnapshot = ModuleWorldRenderer.WaypointSnapshot.empty();
   private static ClientLevel tracerSelectionLevel;
   private static long tracerSelectionGameTime = Long.MIN_VALUE;
   private static int tracerSelectionRevision = Integer.MIN_VALUE;
   private static List<ModuleWorldRenderer.EntityTraceTarget> tracerSelection = List.of();
   private static List<ModuleWorldRenderer.TracerLine> pendingTracerLines = List.of();
   private static final ArrayList<ModuleWorldRenderer.TracerLine> TRACER_LINES_SCRATCH = new ArrayList<>();
   private static Vec3 pendingTracerCamera = Vec3.ZERO;
   private static ModuleWorldRenderer.WaypointSnapshot pendingWaypointFrame = ModuleWorldRenderer.WaypointSnapshot.empty();
   private static Vec3 pendingWaypointCamera = Vec3.ZERO;
   private static final Quaternionf pendingWaypointOrientation = new Quaternionf();
   private static ModuleWorldRenderer.StorageSnapshot pendingEspStorageFrame = ModuleWorldRenderer.StorageSnapshot.empty();
   private static ModuleWorldRenderer.StorageSnapshot pendingEspBlockFrame = ModuleWorldRenderer.StorageSnapshot.empty();
   private static ModuleWorldRenderer.StorageSnapshot pendingEspBarrierFrame = ModuleWorldRenderer.StorageSnapshot.empty();
   private static ModuleWorldRenderer.StorageSnapshot pendingEspSpawnerFrame = ModuleWorldRenderer.StorageSnapshot.empty();
   private static ModuleWorldRenderer.StorageSnapshot pendingEspXrayFrame = ModuleWorldRenderer.StorageSnapshot.empty();
   private static ModuleWorldRenderer.StorageSnapshot pendingEspXrayNearFrame = ModuleWorldRenderer.StorageSnapshot.empty();
   private static List<TrajectoriesModule.Path> pendingTrajectories = List.of();
   private static AABB pendingEspAirBox;
   private static int pendingEspAirColor;
   private static List<AABB> pendingEspGhostBoxes = List.of();
   private static int pendingEspGhostColor;
   private static boolean pendingEspGhostFill;
   private static boolean pendingEspGhostWire;
   private static AABB pendingEspTpClickBox;
   private static int pendingEspTpClickColor;
   private static boolean pendingEspTpClickFill;
   private static boolean pendingEspTpClickWire;
   private static boolean pendingEspStorageFill;
   private static boolean pendingEspBlockFill;
   private static boolean pendingEspBarrierFill;
   private static boolean pendingEspSpawnerFill;
   private static boolean pendingEspXrayFill;
   private static boolean pendingEspAirFill;
   private static boolean pendingEspStorageWire;
   private static boolean pendingEspBlockWire;
   private static boolean pendingEspSpawnerWire;
   private static boolean pendingEspXrayWire;
   private static boolean pendingEspAirWire;
   private static Vec3 pendingEspCamera = Vec3.ZERO;
   static final int TEXTURED_GHOST_BUDGET = 2048;
   private static final int GHOST_CAPACITY = 2176;
   private static final double GHOST_SORT_MOVE_THRESHOLD_SQ = 0.015625;
   private static final long[] ghostPositions = new long[2176];
   private static final BlockState[] ghostStates = new BlockState[2176];
   private static final double[] ghostDistances = new double[2176];
   private static final long[] ghostScratchPositions = new long[2048];
   private static final int[] ghostScratchStates = new int[2048];
   private static final LongOpenHashSet ghostOccupied = new LongOpenHashSet();
   private static int ghostCount;
   private static long ghostContentKey = Long.MIN_VALUE;
   private static int ghostRevision = -1;
   private static BlockPos ghostAnchor;
   private static long ghostSlot = Long.MIN_VALUE;
   private static int ghostOrderVersion;
   private static int ghostSortedVersion = -1;
   private static int ghostSortedCount = -1;
   private static Vec3 ghostSortedCamera;
   private static int pendingGhostCount;
   private static Module cachedTracers;
   private static Module cachedStorageEsp;
   private static Module cachedBlockEsp;
   private static Module cachedSpawnerEsp;
   private static Module cachedXray;
   private static AirPlaceModule cachedAirPlace;
   private static GhostBlockModule cachedGhostBlock;
   private static TpClickModule cachedTpClick;
   private static Module cachedWaypoints;
   private static int cachedEspFlagsRevision = -1;
   private static boolean cachedStorageFill;
   private static boolean cachedStorageTrace;
   private static boolean cachedBlockFill;
   private static boolean cachedBlockTrace;
   private static boolean cachedBlockBarrier;
   private static boolean cachedSpawnerFill;
   private static boolean cachedSpawnerTrace;
   private static boolean cachedXrayFill;
   private static long xrayContentKey = Long.MIN_VALUE;
   private static float pendingEspXrayFillAlpha = 0.3F;
   private static ModuleWorldRenderer.StorageSnapshot xrayNearSnapshot = ModuleWorldRenderer.StorageSnapshot.empty();
   private static BlockPos xrayNearAnchor;
   private static BlockPos xrayEspAnchor;
   private static long xrayNearSlot = Long.MIN_VALUE;
   private static int xrayNearEngineRevision = -1;
   private static final double TELEPORT_SNAP_DISTANCE_SQ = 256.0;
   private static final int XRAY_ANCHOR_SLACK = 2;
   private static final double WAYPOINT_FULL_SCALE_DIST = 14.0;
   private static final double WAYPOINT_FADE_START_DIST = 6.0;
   private static final double WAYPOINT_HIDE_DIST = 2.5;
   private static final double WAYPOINT_FAR_SCALE_DIVISOR = 12.0;
   private static final double WAYPOINT_MAX_FAR_SCALE = 12.0;
   private static final float WAYPOINT_MIN_SCALE = 0.6F;
   private static final double WAYPOINT_DISC_RADIUS = 0.046;
   private static final double WAYPOINT_DISC_MARGIN = 0.06;
   private static final double WAYPOINT_TEXT_HALF_HEIGHT = 0.115;
   private static final float NAMEPLATE_TEXT_SCALE = 0.03125F;
   private static final float NAMEPLATE_LINE_CENTER_PX = -4.5F;
   private static final int NAMEPLATE_OUTLINE_COLOR = -15066598;
   private static final float WAYPOINT_DISTANCE_LIGHTEN = 0.375F;
   private static final float[][] NAMEPLATE_OUTLINE_OFFSETS = new float[][]{
      {-1.0F, -1.0F}, {0.0F, -1.0F}, {1.0F, -1.0F}, {-1.0F, 0.0F}, {1.0F, 0.0F}, {-1.0F, 1.0F}, {0.0F, 1.0F}, {1.0F, 1.0F}
   };
   private static final Identifier WAYPOINT_DISC_TEXTURE = Identifier.fromNamespaceAndPath("riptide", "dynamic/waypoint_disc");
   private static final int DISC_TEXTURE_SIZE = 32;
   private static final double DISC_INNER_RADIUS = 12.0;
   private static final double DISC_OUTER_RADIUS = 14.0;
   private static final Vector3f DISC_AXIS_SCRATCH = new Vector3f();
   private static boolean waypointDiscRegistered;
   private static boolean waypointDiscFailed;

   private static Module xrayModule() {
      Module m = cachedXray;
      return m != null ? m : (cachedXray = ModuleRegistry.get("xray"));
   }

   private static Module tracersModule() {
      Module m = cachedTracers;
      return m != null ? m : (cachedTracers = ModuleRegistry.get("tracers"));
   }

   private static Module storageEspModule() {
      Module m = cachedStorageEsp;
      return m != null ? m : (cachedStorageEsp = ModuleRegistry.get("storage-esp"));
   }

   private static Module spawnerEspModule() {
      Module m = cachedSpawnerEsp;
      return m != null ? m : (cachedSpawnerEsp = ModuleRegistry.get("spawner-esp"));
   }

   private static Module blockEspModule() {
      Module m = cachedBlockEsp;
      return m != null ? m : (cachedBlockEsp = ModuleRegistry.get("block-esp"));
   }

   private static AirPlaceModule airPlaceModule() {
      AirPlaceModule cached = cachedAirPlace;
      if (cached != null) {
         return cached;
      } else {
         return ModuleRegistry.get("air-place") instanceof AirPlaceModule airPlace ? (cachedAirPlace = airPlace) : null;
      }
   }

   private static GhostBlockModule ghostBlockModule() {
      GhostBlockModule cached = cachedGhostBlock;
      if (cached != null) {
         return cached;
      } else {
         return ModuleRegistry.get("ghostblock") instanceof GhostBlockModule ghostBlock ? (cachedGhostBlock = ghostBlock) : null;
      }
   }

   private static TpClickModule tpClickModule() {
      TpClickModule cached = cachedTpClick;
      if (cached != null) {
         return cached;
      } else {
         return ModuleRegistry.get("tp-click") instanceof TpClickModule tpClick ? (cachedTpClick = tpClick) : null;
      }
   }

   private static Module waypointsModule() {
      Module m = cachedWaypoints;
      return m != null ? m : (cachedWaypoints = ModuleRegistry.get("waypoints"));
   }

   private static void refreshEspFlags(Module storage, Module blockEsp, Module spawnerEsp, Module xray) {
      int rev = ModuleRegistry.revision();
      if (rev != cachedEspFlagsRevision) {
         cachedEspFlagsRevision = rev;
         cachedXrayFill = xray != null && Boolean.parseBoolean(xray.value("fill"));
         cachedStorageFill = storage != null && Boolean.parseBoolean(storage.value("fill"));
         cachedStorageTrace = storage != null && Boolean.parseBoolean(storage.value("tracers"));
         cachedBlockFill = blockEsp != null && Boolean.parseBoolean(blockEsp.value("fill"));
         cachedBlockTrace = blockEsp != null && Boolean.parseBoolean(blockEsp.value("tracers"));
         cachedBlockBarrier = blockEsp != null && Boolean.parseBoolean(blockEsp.value("barriers"));
         cachedSpawnerFill = spawnerEsp != null && Boolean.parseBoolean(spawnerEsp.value("fill"));
         cachedSpawnerTrace = spawnerEsp != null && Boolean.parseBoolean(spawnerEsp.value("tracers"));
      }
   }

   private ModuleWorldRenderer() {
   }

   static void initialize() {
      if (!initialized) {
         initialized = true;
         LevelRenderEvents.COLLECT_SUBMITS
            .register(
               (CollectSubmits)context -> {
                  pendingTracerLines = List.of();
                  pendingWaypointFrame = ModuleWorldRenderer.WaypointSnapshot.empty();
                  pendingEspAirFill = false;
                  pendingEspSpawnerFill = false;
                  pendingEspBarrierFill = false;
                  pendingEspBlockFill = false;
                  pendingEspStorageFill = false;
                  pendingEspAirWire = false;
                  pendingEspSpawnerWire = false;
                  pendingEspBlockWire = false;
                  pendingEspStorageWire = false;
                  pendingEspXrayWire = false;
                  pendingEspXrayFill = false;
                  pendingEspTpClickWire = false;
                  pendingEspTpClickFill = false;
                  pendingTrajectories = List.of();
                  pendingEspAirBox = null;
                  pendingEspTpClickBox = null;
                  pendingEspGhostBoxes = List.of();
                  pendingGhostCount = 0;
                  Minecraft mc = Minecraft.getInstance();
                  if (!PackHideState.isActive()) {
                     if (mc != null && mc.level != null && mc.player != null && !mc.gui.hud.isHidden()) {
                        boolean drawTracers = ModuleRenderUtil.hasWorldTracerWork();
                        Module storage = storageEspModule();
                        Module blockEsp = blockEspModule();
                        Module spawnerEsp = spawnerEspModule();
                        AirPlaceModule airPlace = airPlaceModule();
                        Module waypoints = waypointsModule();
                        Module xray = xrayModule();
                        boolean storageEnabled = storage != null && storage.isEnabled();
                        boolean blockEnabled = blockEsp != null && blockEsp.isEnabled();
                        boolean spawnerEnabled = spawnerEsp != null && spawnerEsp.isEnabled();
                        boolean xrayBoxes = ModuleOreSim.drawsBoxes(xray);
                        boolean xrayGhosts = ModuleOreSim.drawsGhosts(xray);
                        boolean waypointsEnabled = waypoints != null && waypoints.isEnabled();
                        BlockPos airTarget = airPlace != null && airPlace.isEnabled() ? airPlace.renderTarget() : null;
                        boolean airGuide = airTarget != null;
                        GhostBlockModule ghostBlock = ghostBlockModule();
                        List<AABB> ghostBoxes = List.of();
                        if (ghostBlock != null) {
                           List<AABB> boxes = ghostBlock.highlightBoxes();
                           if (!boxes.isEmpty() && ghostBlock.highlightEnabled()) {
                              ghostBoxes = boxes;
                           }
                        }

                        TpClickModule tpClick = tpClickModule();
                        AABB tpClickBox = tpClick != null && tpClick.isEnabled() ? tpClick.highlightBox() : null;
                        ModuleWorldRenderer.WaypointSnapshot waypointFrame = waypointsEnabled
                           ? waypointSnapshot(waypoints, mc, mc.level)
                           : ModuleWorldRenderer.WaypointSnapshot.empty();
                        List<ModuleWorldRenderer.WaypointDot> waypointBoxes = waypointFrame.dots();
                        if (drawTracers
                           || storageEnabled
                           || blockEnabled
                           || spawnerEnabled
                           || xrayBoxes
                           || xrayGhosts
                           || airGuide
                           || !ghostBoxes.isEmpty()
                           || tpClickBox != null
                           || !waypointBoxes.isEmpty()) {
                           if (!ModuleRenderUtil.shouldSuppressEspForUi()) {
                              Module tracer = tracersModule();
                              refreshEspFlags(storage, blockEsp, spawnerEsp, xray);
                              boolean storageFill = storageEnabled && cachedStorageFill;
                              boolean storageTrace = storageEnabled && cachedStorageTrace;
                              boolean blockFill = blockEnabled && cachedBlockFill;
                              boolean blockTrace = blockEnabled && cachedBlockTrace;
                              boolean barrierFill = blockEnabled && cachedBlockBarrier;
                              boolean spawnerFill = spawnerEnabled && cachedSpawnerFill;
                              boolean spawnerTrace = spawnerEnabled && cachedSpawnerTrace;
                              boolean xrayFill = xrayBoxes && cachedXrayFill;
                              boolean airFill = airGuide && airPlace.renderFill();
                              if (drawTracers
                                 || storageFill
                                 || storageEnabled
                                 || storageTrace
                                 || blockFill
                                 || blockEnabled
                                 || blockTrace
                                 || barrierFill
                                 || spawnerFill
                                 || spawnerEnabled
                                 || spawnerTrace
                                 || xrayBoxes
                                 || xrayGhosts
                                 || airGuide
                                 || !ghostBoxes.isEmpty()
                                 || tpClickBox != null
                                 || !waypointBoxes.isEmpty()) {
                                 CameraRenderState cameraState = context.levelState().cameraRenderState;
                                 Vec3 camera = cameraState.pos;
                                 float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                                 Vec3 traceStart = camera.add(cameraForward(cameraState).scale(10.0));
                                 float tracerWidth = parseFloat(tracer == null ? null : tracer.value("line-width"), 2.0F, 2.0F, 6.0F);
                                 ModuleWorldRenderer.StorageSnapshot storageFrame = storageEnabled
                                    ? storageSnapshot(storage, mc.level, mc.player, tickDelta)
                                    : ModuleWorldRenderer.StorageSnapshot.empty();
                                 ModuleWorldRenderer.StorageSnapshot blockFrame = blockEnabled
                                    ? blockSnapshot(blockEsp, mc.level, mc.player)
                                    : ModuleWorldRenderer.StorageSnapshot.empty();
                                 ModuleWorldRenderer.StorageSnapshot barrierFrame = barrierFill
                                    ? barrierSnapshot(blockEsp, mc.level, mc.player)
                                    : ModuleWorldRenderer.StorageSnapshot.empty();
                                 ModuleWorldRenderer.StorageSnapshot spawnerFrame = spawnerEnabled
                                    ? spawnerSnapshot(spawnerEsp, mc.level, mc.player)
                                    : ModuleWorldRenderer.StorageSnapshot.empty();
                                 ModuleWorldRenderer.StorageSnapshot xrayFrame = xrayBoxes
                                    ? xrayEspSnapshot(xray, mc.level, mc.player)
                                    : ModuleWorldRenderer.StorageSnapshot.empty();
                                 ModuleWorldRenderer.StorageSnapshot xrayNearFrame = xrayBoxes
                                    ? xrayNearSnapshot(xray, mc.level, mc.player)
                                    : ModuleWorldRenderer.StorageSnapshot.empty();
                                 pendingGhostCount = xrayGhosts ? ghostFrame(xray, mc.level, mc.player, camera) : 0;
                                 AABB airBox = airGuide ? new AABB(airTarget).inflate(0.002) : null;
                                 int airColor = airGuide ? airPlace.guideColor() : 0;
                                 TRACER_LINES_SCRATCH.clear();
                                 List<ModuleWorldRenderer.TracerLine> tracerLines = TRACER_LINES_SCRATCH;
                                 if (drawTracers) {
                                    boolean heightLine = Boolean.parseBoolean(tracer == null ? null : tracer.value("height-line"));
                                    collectEntityTracers(mc.level, traceStart, tickDelta, tracerWidth, heightLine, tracerLines);
                                 }

                                 if (storageTrace && storageFrame != null) {
                                    float traceW = 2.0F;
                                    storageFrame.forEachTrace(
                                       (target, color) -> tracerLines.add(new ModuleWorldRenderer.TracerLine(traceStart, target, color, 2.0F))
                                    );
                                 }

                                 if (blockTrace && blockFrame != null) {
                                    float traceW = 2.0F;
                                    blockFrame.forEachTrace(
                                       (target, color) -> tracerLines.add(new ModuleWorldRenderer.TracerLine(traceStart, target, color, 2.0F))
                                    );
                                 }

                                 if (spawnerTrace && spawnerFrame != null) {
                                    float traceW = 2.0F;
                                    spawnerFrame.forEachTrace(
                                       (target, color) -> tracerLines.add(new ModuleWorldRenderer.TracerLine(traceStart, target, color, 2.0F))
                                    );
                                 }

                                 if (!tracerLines.isEmpty()) {
                                    pendingTracerLines = tracerLines;
                                    pendingTracerCamera = camera;
                                 }

                                 boolean waypointDraw = !waypointBoxes.isEmpty();
                                 pendingEspStorageFill = storageFill;
                                 pendingEspBlockFill = blockFill;
                                 pendingEspBarrierFill = barrierFill;
                                 pendingEspSpawnerFill = spawnerFill;
                                 pendingEspXrayFill = xrayFill;
                                 pendingEspAirFill = airFill;
                                 pendingEspStorageWire = storageEnabled;
                                 pendingEspBlockWire = blockEnabled;
                                 pendingEspSpawnerWire = spawnerEnabled;
                                 pendingEspXrayWire = xrayBoxes;
                                 pendingEspAirWire = airGuide;
                                 pendingEspStorageFrame = storageFrame;
                                 pendingEspBlockFrame = blockFrame;
                                 pendingEspBarrierFrame = barrierFrame;
                                 pendingEspSpawnerFrame = spawnerFrame;
                                 pendingEspXrayFrame = xrayFrame;
                                 pendingEspXrayNearFrame = xrayNearFrame;
                                 pendingEspXrayFillAlpha = ModuleOreSim.fillAlpha(xray);
                                 pendingEspAirBox = airBox;
                                 pendingEspAirColor = airColor;
                                 pendingEspTpClickFill = pendingEspTpClickWire = tpClickBox != null;
                                 pendingEspTpClickBox = tpClickBox;
                                 pendingEspTpClickColor = tpClickBox != null ? tpClick.highlightColor() : 0;
                                 pendingEspGhostWire = !ghostBoxes.isEmpty();
                                 pendingEspGhostFill = pendingEspGhostWire && ghostBlock.highlightFill();
                                 pendingEspGhostBoxes = ghostBoxes;
                                 pendingEspGhostColor = pendingEspGhostWire ? ghostBlock.highlightColor() : 0;
                                 pendingEspCamera = camera;

                                 try {
                                    TrajectoriesModule.collect(tickDelta);
                                 } catch (Throwable var53) {
                                    setTrajectoryPaths(List.of());
                                    riptide.RiptideClientAddon.LOG.debug("Trajectory collect failed", var53);
                                 }

                                 if (waypointDraw) {
                                    pendingWaypointFrame = waypointFrame;
                                    pendingWaypointCamera = camera;
                                    pendingWaypointOrientation.set(cameraState.orientation);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            );
      }
   }

   public static boolean hasPendingTracers() {
      return !pendingTracerLines.isEmpty();
   }

   public static void flushTracers(PoseStack matrices) {
      List<ModuleWorldRenderer.TracerLine> lines = pendingTracerLines;
      if (!lines.isEmpty()) {
         pendingTracerLines = List.of();
         Vec3 cam = pendingTracerCamera;
         RiptideBufferSource bufferSource = TRACER_BUFFERS.get();
         VertexConsumer buffer = bufferSource.getBuffer(RiptideRenderTypes.tracerEspLines());
         Pose pose = matrices.last();

         for (ModuleWorldRenderer.TracerLine seg : lines) {
            drawTracerLine(
               pose,
               buffer,
               seg.from().x - cam.x,
               seg.from().y - cam.y,
               seg.from().z - cam.z,
               seg.to().x - cam.x,
               seg.to().y - cam.y,
               seg.to().z - cam.z,
               seg.color(),
               seg.width()
            );
         }

         bufferSource.uploadAndDraw();
      }
   }

   public static void setTrajectoryPaths(List<TrajectoriesModule.Path> paths) {
      pendingTrajectories = paths == null ? List.of() : paths;
   }

   public static boolean hasPendingEspWork() {
      return pendingEspStorageFill
         || pendingEspBlockFill
         || pendingEspBarrierFill
         || pendingEspSpawnerFill
         || pendingEspAirFill
         || pendingEspStorageWire
         || pendingEspBlockWire
         || pendingEspSpawnerWire
         || pendingEspAirWire
         || pendingEspXrayFill
         || pendingEspXrayWire
         || pendingEspGhostFill
         || pendingEspGhostWire
         || pendingGhostCount > 0
         || pendingEspTpClickFill
         || pendingEspTpClickWire
         || !pendingTrajectories.isEmpty();
   }

   public static void flushEsp(PoseStack matrices) {
      if (hasPendingEspWork()) {
         long flushPerf = RiptidePerf.beginSampled();

         try {
            flushEspInner(matrices);
         } finally {
            RiptidePerf.end("frame.espFlush", flushPerf);
         }
      }
   }

   private static void flushEspInner(PoseStack matrices) {
      boolean storageFill = pendingEspStorageFill;
      boolean blockFill = pendingEspBlockFill;
      boolean barrierFill = pendingEspBarrierFill;
      boolean spawnerFill = pendingEspSpawnerFill;
      boolean xrayFill = pendingEspXrayFill;
      boolean airFill = pendingEspAirFill;
      boolean storageWire = pendingEspStorageWire;
      boolean blockWire = pendingEspBlockWire;
      boolean spawnerWire = pendingEspSpawnerWire;
      boolean xrayWire = pendingEspXrayWire;
      boolean airWire = pendingEspAirWire;
      boolean tpClickFill = pendingEspTpClickFill;
      boolean tpClickWire = pendingEspTpClickWire;
      boolean ghostFill = pendingEspGhostFill;
      boolean ghostWire = pendingEspGhostWire;
      List<TrajectoriesModule.Path> trajectories = pendingTrajectories;
      pendingTrajectories = List.of();
      pendingEspAirFill = false;
      pendingEspSpawnerFill = false;
      pendingEspBarrierFill = false;
      pendingEspBlockFill = false;
      pendingEspStorageFill = false;
      pendingEspAirWire = false;
      pendingEspSpawnerWire = false;
      pendingEspBlockWire = false;
      pendingEspStorageWire = false;
      pendingEspXrayWire = false;
      pendingEspXrayFill = false;
      pendingEspTpClickWire = false;
      pendingEspTpClickFill = false;
      pendingEspGhostWire = false;
      pendingEspGhostFill = false;
      ModuleWorldRenderer.StorageSnapshot storageFrame = pendingEspStorageFrame;
      ModuleWorldRenderer.StorageSnapshot blockFrame = pendingEspBlockFrame;
      ModuleWorldRenderer.StorageSnapshot barrierFrame = pendingEspBarrierFrame;
      ModuleWorldRenderer.StorageSnapshot spawnerFrame = pendingEspSpawnerFrame;
      ModuleWorldRenderer.StorageSnapshot xrayFrame = pendingEspXrayFrame;
      ModuleWorldRenderer.StorageSnapshot xrayNearFrame = pendingEspXrayNearFrame;
      float xrayFillAlpha = pendingEspXrayFillAlpha;
      AABB airBox = pendingEspAirBox;
      int airColor = pendingEspAirColor;
      AABB tpClickBox = pendingEspTpClickBox;
      int tpClickColor = pendingEspTpClickColor;
      List<AABB> ghostBoxes = pendingEspGhostBoxes;
      int ghostColor = pendingEspGhostColor;
      pendingEspGhostBoxes = List.of();
      Vec3 cam = pendingEspCamera;
      int oreGhosts = pendingGhostCount;
      pendingGhostCount = 0;
      RiptideBufferSource bufferSource = ESP_BUFFERS.get();
      Pose pose = matrices.last();
      boolean meshed = drawEspMeshes(
         pose,
         cam,
         xrayFrame,
         xrayNearFrame,
         storageFrame,
         blockFrame,
         barrierFrame,
         spawnerFrame,
         xrayFill,
         xrayWire,
         storageFill,
         storageWire,
         blockFill,
         blockWire,
         barrierFill,
         spawnerFill,
         spawnerWire,
         xrayFillAlpha
      );
      if (oreGhosts > 0) {
         renderOreGhosts(pose, bufferSource, oreGhosts, cam);
      }

      if (storageFill || blockFill || barrierFill || spawnerFill || xrayFill || airFill || tpClickFill || ghostFill) {
         VertexConsumer buffer = bufferSource.getBuffer(RiptideRenderTypes.storageEspFillSeeThrough());
         if (!meshed) {
            if (xrayFill && xrayFrame != null) {
               xrayFrame.renderFill(pose, buffer, cam, xrayFillAlpha);
            }

            if (xrayFill && xrayNearFrame != null) {
               xrayNearFrame.renderFill(pose, buffer, cam, 0.6F);
            }

            if (storageFill && storageFrame != null) {
               storageFrame.renderFill(pose, buffer, cam, 0.3F);
            }

            if (blockFill && blockFrame != null) {
               blockFrame.renderFill(pose, buffer, cam, 0.3F);
            }

            if (barrierFill && barrierFrame != null) {
               barrierFrame.renderFill(pose, buffer, cam, 0.2F);
            }

            if (spawnerFill && spawnerFrame != null) {
               spawnerFrame.renderFill(pose, buffer, cam, 0.3F);
            }
         }

         if (airFill && airBox != null) {
            fillBox(pose, buffer, airBox.move(-cam.x, -cam.y, -cam.z), withAlpha(airColor, 0.12F));
         }

         if (tpClickFill && tpClickBox != null) {
            fillBox(pose, buffer, tpClickBox.move(-cam.x, -cam.y, -cam.z), withAlpha(tpClickColor, 0.12F));
         }

         if (ghostFill) {
            int color = withAlpha(ghostColor, 0.15F);

            for (AABB box : ghostBoxes) {
               fillBox(pose, buffer, box.move(-cam.x, -cam.y, -cam.z), color);
            }
         }
      }

      if (!trajectories.isEmpty()) {
         try {
            VertexConsumer markerLines = null;
            boolean thin = trajectories.get(0).lineWidth() <= 1.0F;
            VertexConsumer arcs = bufferSource.getBuffer(thin ? RiptideRenderTypes.trajectoryThinLines() : RiptideRenderTypes.trajectoryLines());

            for (TrajectoriesModule.Path path : trajectories) {
               if (thin) {
                  drawTrajectoryThinStrip(pose, arcs, path.points(), cam, path.color());
               } else {
                  drawTrajectoryStrip(pose, arcs, path.points(), cam, path.color(), path.lineWidth());
               }
            }

            boolean anyMarkers = false;

            for (TrajectoriesModule.Path pathx : trajectories) {
               if (!pathx.markers().isEmpty()) {
                  if (!anyMarkers) {
                     anyMarkers = true;
                     markerLines = bufferSource.getBuffer(RiptideRenderTypes.trajectoryLines());
                  }

                  for (TrajectoriesModule.Marker marker : pathx.markers()) {
                     renderStorageBox(pose, markerLines, marker.box().move(-cam.x, -cam.y, -cam.z), withAlpha(marker.color(), 0.85F), 1.5F);
                  }
               }
            }

            if (anyMarkers) {
               VertexConsumer markerFill = bufferSource.getBuffer(RiptideRenderTypes.storageEspFillSeeThrough());

               for (TrajectoriesModule.Path pathxx : trajectories) {
                  for (TrajectoriesModule.Marker marker : pathxx.markers()) {
                     fillBox(pose, markerFill, marker.box().move(-cam.x, -cam.y, -cam.z), withAlpha(marker.color(), 0.15F));
                  }
               }
            }
         } catch (Throwable var44) {
            riptide.RiptideClientAddon.LOG.debug("Trajectory draw failed", var44);
         }
      }

      if (storageWire || blockWire || spawnerWire || xrayWire || airWire || tpClickWire || ghostWire) {
         VertexConsumer bufferx = bufferSource.getBuffer(RiptideRenderTypes.tracerEspLines());
         if (!meshed) {
            if (xrayWire && xrayFrame != null) {
               xrayFrame.renderWire(pose, bufferx, cam, 1.5F);
            }

            if (xrayWire && xrayNearFrame != null) {
               xrayNearFrame.renderWire(pose, bufferx, cam, 1.5F);
            }

            if (storageWire && storageFrame != null) {
               storageFrame.renderWire(pose, bufferx, cam, 1.5F);
            }

            if (blockWire && blockFrame != null) {
               blockFrame.renderWire(pose, bufferx, cam, 1.5F);
            }

            if (spawnerWire && spawnerFrame != null) {
               spawnerFrame.renderWire(pose, bufferx, cam, 1.5F);
            }
         }

         if (airWire && airBox != null) {
            renderStorageBox(pose, bufferx, airBox.move(-cam.x, -cam.y, -cam.z), withAlpha(airColor, 0.8F), 1.5F);
         }

         if (tpClickWire && tpClickBox != null) {
            renderStorageBox(pose, bufferx, tpClickBox.move(-cam.x, -cam.y, -cam.z), withAlpha(tpClickColor, 0.8F), 1.5F);
         }

         if (ghostWire) {
            int color = withAlpha(ghostColor, 0.85F);

            for (AABB box : ghostBoxes) {
               renderStorageBox(pose, bufferx, box.move(-cam.x, -cam.y, -cam.z), color, 1.5F);
            }
         }
      }

      bufferSource.uploadAndDraw();
   }

   private static boolean drawEspMeshes(
      Pose pose,
      Vec3 camera,
      ModuleWorldRenderer.StorageSnapshot xrayFrame,
      ModuleWorldRenderer.StorageSnapshot xrayNearFrame,
      ModuleWorldRenderer.StorageSnapshot storageFrame,
      ModuleWorldRenderer.StorageSnapshot blockFrame,
      ModuleWorldRenderer.StorageSnapshot barrierFrame,
      ModuleWorldRenderer.StorageSnapshot spawnerFrame,
      boolean xrayFill,
      boolean xrayWire,
      boolean storageFill,
      boolean storageWire,
      boolean blockFill,
      boolean blockWire,
      boolean barrierFill,
      boolean spawnerFill,
      boolean spawnerWire,
      float xrayFillAlpha
   ) {
      if (!espMeshPathAvailable) {
         return false;
      } else {
         try {
            Vec3 anchor = espMeshAnchor;
            if (Math.abs(camera.x - anchor.x) > 64.0 || Math.abs(camera.y - anchor.y) > 64.0 || Math.abs(camera.z - anchor.z) > 64.0) {
               anchor = new Vec3(Math.floor(camera.x / 16.0) * 16.0, Math.floor(camera.y / 16.0) * 16.0, Math.floor(camera.z / 16.0) * 16.0);
            }

            int flags = (xrayFill ? 1 : 0)
               | (xrayWire ? 2 : 0)
               | (storageFill ? 4 : 0)
               | (storageWire ? 8 : 0)
               | (blockFill ? 16 : 0)
               | (blockWire ? 32 : 0)
               | (barrierFill ? 64 : 0)
               | (spawnerFill ? 128 : 0)
               | (spawnerWire ? 256 : 0);
            boolean stale = flags != espMeshFlags
               || !anchor.equals(espMeshAnchor)
               || Float.floatToIntBits(xrayFillAlpha) != Float.floatToIntBits(espMeshXrayAlpha)
               || espMeshContentIds[0] != contentId(xrayFrame)
               || espMeshContentIds[1] != contentId(xrayNearFrame)
               || espMeshContentIds[2] != contentId(storageFrame)
               || espMeshContentIds[3] != contentId(blockFrame)
               || espMeshContentIds[4] != contentId(barrierFrame)
               || espMeshContentIds[5] != contentId(spawnerFrame);
            if (stale) {
               long perf = RiptidePerf.beginSampled();
               Vec3 origin = anchor;
               espFillMeshReady = ESP_FILL_MESH.bake(RiptideRenderTypes.storageEspFillSeeThrough(), buffer -> {
                  if (xrayFill && xrayFrame != null) {
                     xrayFrame.renderFill(BAKE_POSE, buffer, origin, xrayFillAlpha);
                  }

                  if (xrayFill && xrayNearFrame != null) {
                     xrayNearFrame.renderFill(BAKE_POSE, buffer, origin, 0.6F);
                  }

                  if (storageFill && storageFrame != null) {
                     storageFrame.renderFill(BAKE_POSE, buffer, origin, 0.3F);
                  }

                  if (blockFill && blockFrame != null) {
                     blockFrame.renderFill(BAKE_POSE, buffer, origin, 0.3F);
                  }

                  if (barrierFill && barrierFrame != null) {
                     barrierFrame.renderFill(BAKE_POSE, buffer, origin, 0.2F);
                  }

                  if (spawnerFill && spawnerFrame != null) {
                     spawnerFrame.renderFill(BAKE_POSE, buffer, origin, 0.3F);
                  }
               });
               espWireMeshReady = ESP_WIRE_MESH.bake(RiptideRenderTypes.tracerEspLines(), buffer -> {
                  if (xrayWire && xrayFrame != null) {
                     xrayFrame.renderWire(BAKE_POSE, buffer, origin, 1.5F);
                  }

                  if (xrayWire && xrayNearFrame != null) {
                     xrayNearFrame.renderWire(BAKE_POSE, buffer, origin, 1.5F);
                  }

                  if (storageWire && storageFrame != null) {
                     storageFrame.renderWire(BAKE_POSE, buffer, origin, 1.5F);
                  }

                  if (blockWire && blockFrame != null) {
                     blockFrame.renderWire(BAKE_POSE, buffer, origin, 1.5F);
                  }

                  if (spawnerWire && spawnerFrame != null) {
                     spawnerFrame.renderWire(BAKE_POSE, buffer, origin, 1.5F);
                  }
               });
               espMeshAnchor = anchor;
               espMeshFlags = flags;
               espMeshXrayAlpha = xrayFillAlpha;
               espMeshContentIds[0] = contentId(xrayFrame);
               espMeshContentIds[1] = contentId(xrayNearFrame);
               espMeshContentIds[2] = contentId(storageFrame);
               espMeshContentIds[3] = contentId(blockFrame);
               espMeshContentIds[4] = contentId(barrierFrame);
               espMeshContentIds[5] = contentId(spawnerFrame);
               RiptidePerf.end("frame.espMeshBake", perf);
            }

            double dx = espMeshAnchor.x - camera.x;
            double dy = espMeshAnchor.y - camera.y;
            double dz = espMeshAnchor.z - camera.z;
            Matrix4fc framePose = pose.pose();
            if (espFillMeshReady) {
               ESP_FILL_MESH.draw(RiptideRenderTypes.storageEspFillSeeThrough(), framePose, dx, dy, dz);
            }

            if (espWireMeshReady) {
               ESP_WIRE_MESH.draw(RiptideRenderTypes.tracerEspLines(), framePose, dx, dy, dz);
            }

            return true;
         } catch (Throwable var29) {
            espMeshPathAvailable = false;
            espWireMeshReady = false;
            espFillMeshReady = false;

            try {
               ESP_FILL_MESH.drop();
               ESP_WIRE_MESH.drop();
            } catch (Throwable var28) {
            }

            riptide.RiptideClientAddon.LOG.warn("ESP mesh buffers disabled, falling back to immediate mode", var29);
            return false;
         }
      }
   }

   private static long contentId(ModuleWorldRenderer.StorageSnapshot frame) {
      return frame == null ? 0L : frame.contentId();
   }

   public static void dropEspMeshes() {
      espWireMeshReady = false;
      espFillMeshReady = false;
      espMeshFlags = Integer.MIN_VALUE;
      Arrays.fill(espMeshContentIds, 0L);
      ESP_FILL_MESH.drop();
      ESP_WIRE_MESH.drop();
   }

   private static void renderOreGhosts(Pose pose, RiptideBufferSource bufferSource, int count, Vec3 cam) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc == null ? null : mc.level;
      if (level != null) {
         CardinalLighting lighting = level.cardinalLighting();
         ensureGhostPainterOrder(count, cam);
         VertexConsumer buffer = bufferSource.getBuffer(RiptideRenderTypes.oreGhostSeeThrough());

         for (int i = 0; i < count; i++) {
            long packed = ghostPositions[i];
            BlockState state = ghostStates[i];
            if (state != null) {
               RiptideOreGhostModels.Template template = RiptideOreGhostModels.of(state);
               if (!template.faces().isEmpty()) {
                  int bx = BlockPos.getX(packed);
                  int by = BlockPos.getY(packed);
                  int bz = BlockPos.getZ(packed);
                  double ox = bx - cam.x;
                  double oy = by - cam.y;
                  double oz = bz - cam.z;
                  double relX = cam.x - (bx + 0.5);
                  double relY = cam.y - (by + 0.5);
                  double relZ = cam.z - (bz + 0.5);

                  for (RiptideOreGhostModels.Face face : template.faces()) {
                     Direction cull = face.cull();
                     if (cull == null
                        || !ghostOccupied.contains(BlockPos.asLong(bx + cull.getStepX(), by + cull.getStepY(), bz + cull.getStepZ()))
                           && !(relX * cull.getStepX() + relY * cull.getStepY() + relZ * cull.getStepZ() <= 0.0)) {
                        int color = shadeColor(face.facing() == null ? 1.0F : lighting.byFace(face.facing()));
                        float[] data = face.data();

                        for (int v = 0; v < 4; v++) {
                           int base = v * 5;
                           buffer.addVertex(pose, (float)(ox + data[base]), (float)(oy + data[base + 1]), (float)(oz + data[base + 2]))
                              .setUv(data[base + 3], data[base + 4])
                              .setColor(color);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static int shadeColor(float shade) {
      int value = Mth.clamp(Math.round(shade * 255.0F), 0, 255);
      return 0xFF000000 | value << 16 | value << 8 | value;
   }

   private static void drawTrajectoryThinStrip(Pose entry, VertexConsumer buffer, List<Vec3> points, Vec3 cam, int color) {
      if (points.size() >= 2) {
         Vec3 previous = points.get(0);

         for (int i = 1; i < points.size(); i++) {
            Vec3 current = points.get(i);
            buffer.addVertex(entry, (float)(previous.x - cam.x), (float)(previous.y - cam.y), (float)(previous.z - cam.z)).setColor(color);
            buffer.addVertex(entry, (float)(current.x - cam.x), (float)(current.y - cam.y), (float)(current.z - cam.z)).setColor(color);
            previous = current;
         }
      }
   }

   private static void drawTrajectoryStrip(Pose entry, VertexConsumer buffer, List<Vec3> points, Vec3 cam, int color, float width) {
      if (points.size() >= 2) {
         Vector3f normal = new Vector3f();
         Vec3 previous = points.get(0);

         for (int i = 1; i < points.size(); i++) {
            Vec3 current = points.get(i);
            float dx = (float)(current.x - previous.x);
            float dy = (float)(current.y - previous.y);
            float dz = (float)(current.z - previous.z);
            if (dx * dx + dy * dy + dz * dz > 1.0E-10F) {
               normal.set(dx, dy, dz).normalize();
               buffer.addVertex(entry, (float)(previous.x - cam.x), (float)(previous.y - cam.y), (float)(previous.z - cam.z))
                  .setColor(color)
                  .setNormal(entry, normal)
                  .setLineWidth(width);
               buffer.addVertex(entry, (float)(current.x - cam.x), (float)(current.y - cam.y), (float)(current.z - cam.z))
                  .setColor(color)
                  .setNormal(entry, normal)
                  .setLineWidth(width);
            }

            previous = current;
         }
      }
   }

   private static void drawTracerLine(
      Pose entry, VertexConsumer buffer, double x1, double y1, double z1, double x2, double y2, double z2, int color, float width
   ) {
      Vector3f normal = new Vector3f((float)(x2 - x1), (float)(y2 - y1), (float)(z2 - z1));
      if (!(normal.lengthSquared() <= 1.0E-8F)) {
         normal.normalize();
         float fx1 = (float)x1;
         float fy1 = (float)y1;
         float fz1 = (float)z1;
         float fx2 = (float)x2;
         float fy2 = (float)y2;
         float fz2 = (float)z2;
         buffer.addVertex(entry, fx1, fy1, fz1).setColor(color).setNormal(entry, normal).setLineWidth(width);
         float t = new Vector3f(fx1, fy1, fz1).negate().dot(normal);
         float length = new Vector3f(fx2, fy2, fz2).sub(fx1, fy1, fz1).length();
         if (t > 0.0F && t < length) {
            Vector3f closeToCam = new Vector3f(normal).mul(t).add(fx1, fy1, fz1);
            buffer.addVertex(entry, closeToCam.x, closeToCam.y, closeToCam.z).setColor(color).setNormal(entry, normal).setLineWidth(width);
            buffer.addVertex(entry, closeToCam.x, closeToCam.y, closeToCam.z).setColor(color).setNormal(entry, normal).setLineWidth(width);
         }

         buffer.addVertex(entry, fx2, fy2, fz2).setColor(color).setNormal(entry, normal).setLineWidth(width);
      }
   }

   private static Vec3 cameraForward(CameraRenderState camera) {
      if (camera == null) {
         return new Vec3(0.0, 0.0, -1.0);
      } else {
         Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F);
         camera.orientation.transform(forward);
         Vec3 result = new Vec3(forward.x, forward.y, forward.z);
         return result.lengthSqr() <= 1.0E-8 ? Vec3.directionFromRotation(camera.xRot, camera.yRot) : result.normalize();
      }
   }

   private static void collectEntityTracers(
      ClientLevel level, Vec3 from, float tickDelta, float width, boolean heightLine, List<ModuleWorldRenderer.TracerLine> out
   ) {
      if (level != null) {
         long gameTime = level.getGameTime();
         int revision = ModuleRegistry.revision();
         if (tracerSelectionLevel != level || tracerSelectionGameTime != gameTime || tracerSelectionRevision != revision) {
            List<ModuleWorldRenderer.EntityTraceTarget> selected = new ArrayList<>();

            for (Entity entity : level.entitiesForRendering()) {
               if (ModuleRenderUtil.shouldTrace(entity)) {
                  selected.add(new ModuleWorldRenderer.EntityTraceTarget(entity, ModuleRenderUtil.tracerColor(entity)));
               }
            }

            tracerSelectionLevel = level;
            tracerSelectionGameTime = gameTime;
            tracerSelectionRevision = revision;
            tracerSelection = List.copyOf(selected);
         }

         for (ModuleWorldRenderer.EntityTraceTarget target : tracerSelection) {
            Entity entityx = target.entity();
            if (entityx != null && !entityx.isRemoved()) {
               Vec3 base = stableInterpolatedPosition(entityx, tickDelta);
               Vec3 top = base.add(0.0, entityx.getBbHeight(), 0.0);
               int color = target.color();
               if (from.distanceToSqr(base) > 1.0E-8) {
                  out.add(new ModuleWorldRenderer.TracerLine(from, base, color, width));
               }

               if (heightLine && base.distanceToSqr(top) > 1.0E-8) {
                  out.add(new ModuleWorldRenderer.TracerLine(base, top, color, width));
               }
            }
         }
      }
   }

   private static Vec3 stableInterpolatedPosition(Entity entity, float tickDelta) {
      double dx = entity.getX() - entity.xOld;
      double dy = entity.getY() - entity.yOld;
      double dz = entity.getZ() - entity.zOld;
      return entity.tickCount > 1 && !(dx * dx + dy * dy + dz * dz > 256.0)
         ? new Vec3(
            Mth.lerp(tickDelta, entity.xOld, entity.getX()), Mth.lerp(tickDelta, entity.yOld, entity.getY()), Mth.lerp(tickDelta, entity.zOld, entity.getZ())
         )
         : entity.position();
   }

   private static void renderEntityBox(Pose pose, VertexConsumer buffer, AABB box, int color) {
      renderStorageBox(pose, buffer, box, color, 1.5F);
   }

   private static void renderStorageBox(Pose pose, VertexConsumer buffer, AABB box, int color, float width) {
      double x1 = box.minX;
      double y1 = box.minY;
      double z1 = box.minZ;
      double x2 = box.maxX;
      double y2 = box.maxY;
      double z2 = box.maxZ;
      line(pose, buffer, x1, y1, z1, x2, y1, z1, color, width);
      line(pose, buffer, x2, y1, z1, x2, y1, z2, color, width);
      line(pose, buffer, x2, y1, z2, x1, y1, z2, color, width);
      line(pose, buffer, x1, y1, z2, x1, y1, z1, color, width);
      line(pose, buffer, x1, y2, z1, x2, y2, z1, color, width);
      line(pose, buffer, x2, y2, z1, x2, y2, z2, color, width);
      line(pose, buffer, x2, y2, z2, x1, y2, z2, color, width);
      line(pose, buffer, x1, y2, z2, x1, y2, z1, color, width);
      line(pose, buffer, x1, y1, z1, x1, y2, z1, color, width);
      line(pose, buffer, x2, y1, z1, x2, y2, z1, color, width);
      line(pose, buffer, x2, y1, z2, x2, y2, z2, color, width);
      line(pose, buffer, x1, y1, z2, x1, y2, z2, color, width);
   }

   private static void clippedLine(Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 planePoint, Vec3 forward, int color, float width) {
      double da = signedDistanceFromPlane(a, planePoint, forward);
      double db = signedDistanceFromPlane(b, planePoint, forward);
      if (!(da < 0.0) || !(db < 0.0)) {
         double ax = a.x;
         double ay = a.y;
         double az = a.z;
         double bx = b.x;
         double by = b.y;
         double bz = b.z;
         if (da < 0.0) {
            double t = -da / (db - da);
            ax = a.x + (b.x - a.x) * t;
            ay = a.y + (b.y - a.y) * t;
            az = a.z + (b.z - a.z) * t;
         } else if (db < 0.0) {
            double t = -db / (da - db);
            bx = b.x + (a.x - b.x) * t;
            by = b.y + (a.y - b.y) * t;
            bz = b.z + (a.z - b.z) * t;
         }

         RiptideWorldGeometry.line(pose, buffer, ax, ay, az, bx, by, bz, color, width);
      }
   }

   private static double signedDistanceFromPlane(Vec3 point, Vec3 planePoint, Vec3 forward) {
      return (point.x - planePoint.x) * forward.x + (point.y - planePoint.y) * forward.y + (point.z - planePoint.z) * forward.z;
   }

   private static void line(Pose pose, VertexConsumer buffer, double x1, double y1, double z1, double x2, double y2, double z2, int color, float width) {
      RiptideWorldGeometry.line(pose, buffer, x1, y1, z1, x2, y2, z2, color, width);
   }

   private static void fillBox(Pose pose, VertexConsumer buffer, AABB box, int color) {
      if ((color >>> 24 & 0xFF) > 0) {
         quad(pose, buffer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, color);
         quad(pose, buffer, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, box.minX, box.maxY, box.minZ, color);
         quad(pose, buffer, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color);
         quad(pose, buffer, box.maxX, box.minY, box.minZ, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, color);
         quad(pose, buffer, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, color);
         quad(pose, buffer, box.maxX, box.minY, box.maxZ, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color);
      }
   }

   private static void quad(
      Pose pose,
      VertexConsumer buffer,
      double x1,
      double y1,
      double z1,
      double x2,
      double y2,
      double z2,
      double x3,
      double y3,
      double z3,
      double x4,
      double y4,
      double z4,
      int color
   ) {
      buffer.addVertex(pose, (float)x1, (float)y1, (float)z1).setColor(color);
      buffer.addVertex(pose, (float)x2, (float)y2, (float)z2).setColor(color);
      buffer.addVertex(pose, (float)x3, (float)y3, (float)z3).setColor(color);
      buffer.addVertex(pose, (float)x4, (float)y4, (float)z4).setColor(color);
   }

   private static int withAlpha(int color, float alphaMultiplier) {
      int alpha = Math.max(0, Math.min(255, (int)((color >>> 24 & 0xFF) * alphaMultiplier)));
      return alpha << 24 | color & 16777215;
   }

   private static int lighten(int color, float factor) {
      int r = color >>> 16 & 0xFF;
      int g = color >>> 8 & 0xFF;
      int b = color & 0xFF;
      r += (int)((255 - r) * factor);
      g += (int)((255 - g) * factor);
      b += (int)((255 - b) * factor);
      return color & 0xFF000000 | r << 16 | g << 8 | b;
   }

   private static float parseFloat(String value, float fallback, float min, float max) {
      try {
         return Mth.clamp(Float.parseFloat(value), min, max);
      } catch (Exception var5) {
         return fallback;
      }
   }

   private static ModuleWorldRenderer.StorageSnapshot storageSnapshot(Module module, ClientLevel level, Player player, float tickDelta) {
      if (module != null && level != null && player != null) {
         long scanKey = ModuleEspChunkCache.generation();
         int revision = ModuleRegistry.revision();
         int chunkX = player.chunkPosition().x();
         int chunkZ = player.chunkPosition().z();
         ModuleWorldRenderer.StorageSnapshot cached = storageSnapshot;
         if (cached.matches(scanKey, revision, chunkX, chunkZ)) {
            return cached;
         } else {
            long perf = RiptidePerf.beginJoin();
            List<ModuleWorldRenderer.StorageBox> boxes = new ArrayList<>();
            List<ModuleWorldRenderer.StorageTrace> traces = new ArrayList<>();
            ModuleStorageEsp.collectBothDetailed(
               module,
               level,
               player,
               tickDelta,
               (box, color, meshable) -> boxes.add(new ModuleWorldRenderer.StorageBox(box, color, meshable)),
               (target, color) -> traces.add(new ModuleWorldRenderer.StorageTrace(target, color))
            );
            ModuleWorldRenderer.StorageSnapshot next = ModuleWorldRenderer.StorageSnapshot.create(
               scanKey, revision, chunkX, chunkZ, boxes, traces, Boolean.parseBoolean(module.value("meshing")), cached
            );
            storageSnapshot = next;
            RiptidePerf.endJoinSpike("join.storageEsp.scan", perf, 6000000L);
            return next;
         }
      } else {
         return ModuleWorldRenderer.StorageSnapshot.empty();
      }
   }

   private static ModuleWorldRenderer.StorageSnapshot blockSnapshot(Module module, ClientLevel level, Player player) {
      if (module != null && level != null && player != null) {
         long scanKey = ModuleEspChunkCache.generation();
         int revision = ModuleRegistry.revision();
         int chunkX = player.chunkPosition().x();
         int chunkZ = player.chunkPosition().z();
         ModuleWorldRenderer.StorageSnapshot cached = blockSnapshot;
         if (cached.matches(scanKey, revision, chunkX, chunkZ)) {
            return cached;
         } else {
            long perf = RiptidePerf.beginJoin();
            List<ModuleWorldRenderer.StorageBox> boxes = new ArrayList<>();
            List<ModuleWorldRenderer.StorageTrace> traces = new ArrayList<>();
            ModuleBlockEsp.collectBoth(
               module,
               level,
               player,
               (box, color) -> boxes.add(new ModuleWorldRenderer.StorageBox(box, color, true)),
               (target, color) -> traces.add(new ModuleWorldRenderer.StorageTrace(target, color))
            );
            ModuleWorldRenderer.StorageSnapshot next = ModuleWorldRenderer.StorageSnapshot.create(
               scanKey, revision, chunkX, chunkZ, boxes, traces, Boolean.parseBoolean(module.value("meshing")), cached
            );
            blockSnapshot = next;
            RiptidePerf.endJoinSpike("join.blockEsp.scan", perf, 6000000L);
            return next;
         }
      } else {
         return ModuleWorldRenderer.StorageSnapshot.empty();
      }
   }

   private static ModuleWorldRenderer.StorageSnapshot spawnerSnapshot(Module module, ClientLevel level, Player player) {
      if (module != null && level != null && player != null) {
         long scanKey = ModuleEspChunkCache.generation();
         int revision = ModuleRegistry.revision();
         int chunkX = player.chunkPosition().x();
         int chunkZ = player.chunkPosition().z();
         ModuleWorldRenderer.StorageSnapshot cached = spawnerSnapshot;
         if (cached.matches(scanKey, revision, chunkX, chunkZ)) {
            return cached;
         } else {
            long perf = RiptidePerf.beginJoin();
            List<ModuleWorldRenderer.StorageBox> boxes = new ArrayList<>();
            List<ModuleWorldRenderer.StorageTrace> traces = new ArrayList<>();
            ModuleSpawnerEsp.collectBoth(
               module,
               level,
               player,
               (box, color) -> boxes.add(new ModuleWorldRenderer.StorageBox(box, color, true)),
               (target, color) -> traces.add(new ModuleWorldRenderer.StorageTrace(target, color))
            );
            ModuleWorldRenderer.StorageSnapshot next = ModuleWorldRenderer.StorageSnapshot.create(
               scanKey, revision, chunkX, chunkZ, boxes, traces, Boolean.parseBoolean(module.value("meshing")), cached
            );
            spawnerSnapshot = next;
            RiptidePerf.endJoinSpike("join.spawnerEsp.scan", perf, 6000000L);
            return next;
         }
      } else {
         return ModuleWorldRenderer.StorageSnapshot.empty();
      }
   }

   private static ModuleWorldRenderer.StorageSnapshot xrayEspSnapshot(Module module, ClientLevel level, Player player) {
      if (module != null && level != null && player != null) {
         int revision = ModuleRegistry.revision();
         long contentKey = ModuleOreSim.contentKey(module, level);
         BlockPos anchor = player.blockPosition();
         int chunkX = player.chunkPosition().x();
         int chunkZ = player.chunkPosition().z();
         ModuleWorldRenderer.StorageSnapshot cached = xrayEspSnapshot;
         if (contentKey == xrayContentKey && cached.revision() == revision && withinAnchorSlack(anchor, xrayEspAnchor)) {
            return cached;
         } else {
            xrayEspAnchor = anchor;
            long perf = RiptidePerf.beginJoin();
            List<ModuleWorldRenderer.StorageBox> boxes = new ArrayList<>();
            ModuleOreSim.collect(module, level, player, (box, color) -> boxes.add(new ModuleWorldRenderer.StorageBox(box, color, true)));
            ModuleWorldRenderer.StorageSnapshot next = ModuleWorldRenderer.StorageSnapshot.create(
               contentKey, revision, chunkX, chunkZ, boxes, List.of(), true, cached
            );
            xrayEspSnapshot = next;
            xrayContentKey = contentKey;
            RiptidePerf.endJoinSpike("join.xrayEsp.scan", perf, 6000000L);
            return next;
         }
      } else {
         return ModuleWorldRenderer.StorageSnapshot.empty();
      }
   }

   private static boolean withinAnchorSlack(BlockPos now, BlockPos previous) {
      return previous != null
         && Math.abs(now.getX() - previous.getX()) < 2
         && Math.abs(now.getY() - previous.getY()) < 2
         && Math.abs(now.getZ() - previous.getZ()) < 2;
   }

   private static ModuleWorldRenderer.StorageSnapshot xrayNearSnapshot(Module module, ClientLevel level, Player player) {
      if (module == null || level == null || player == null) {
         return ModuleWorldRenderer.StorageSnapshot.empty();
      } else if (!ModuleOreSim.oreSimMode(module)) {
         return ModuleWorldRenderer.StorageSnapshot.empty();
      } else {
         int revision = ModuleRegistry.revision();
         BlockPos anchor = player.blockPosition();
         long slot = level.getGameTime() >> 2;
         int engineRevision = RiptideOreSimEngine.revision();
         ModuleWorldRenderer.StorageSnapshot cached = xrayNearSnapshot;
         if (anchor.equals(xrayNearAnchor) && cached.revision() == revision && slot == xrayNearSlot && engineRevision == xrayNearEngineRevision) {
            return cached;
         } else {
            List<ModuleWorldRenderer.StorageBox> boxes = new ArrayList<>();
            ModuleOreSim.collectNearbyReal(
               module,
               level,
               player,
               (pos, state) -> boxes.add(new ModuleWorldRenderer.StorageBox(new AABB(pos), ModuleOreSim.colorForBlock(module, state.getBlock()), true))
            );
            ModuleWorldRenderer.StorageSnapshot next = ModuleWorldRenderer.StorageSnapshot.create(
               level.getGameTime(), revision, player.chunkPosition().x(), player.chunkPosition().z(), boxes, List.of(), true, cached
            );
            xrayNearSnapshot = next;
            xrayNearAnchor = anchor;
            xrayNearSlot = slot;
            xrayNearEngineRevision = engineRevision;
            return next;
         }
      }
   }

   private static int ghostFrame(Module module, ClientLevel level, Player player, Vec3 camera) {
      if (module != null && level != null && player != null) {
         int revision = ModuleRegistry.revision();
         long contentKey = ModuleOreSim.contentKey(module, level);
         BlockPos anchor = player.blockPosition();
         long slot = level.getGameTime() >> 2;
         if (contentKey == ghostContentKey && revision == ghostRevision && anchor.equals(ghostAnchor) && slot == ghostSlot) {
            return ghostCount;
         } else {
            ghostContentKey = contentKey;
            ghostRevision = revision;
            ghostAnchor = anchor;
            ghostSlot = slot;
            long perf = RiptidePerf.beginJoin();
            int simCount = ModuleOreSim.collectGhosts(module, player, ghostScratchPositions, ghostScratchStates);
            ghostOccupied.clear();
            int candidateCount = 0;

            for (int i = 0; i < simCount && candidateCount < 2176; i++) {
               BlockState state = RiptideOreSimOre.OreStates.state(ghostScratchStates[i]);
               if (state != null && ghostOccupied.add(ghostScratchPositions[i])) {
                  ghostPositions[candidateCount] = ghostScratchPositions[i];
                  ghostStates[candidateCount] = state;
                  candidateCount++;
               }
            }

            int[] candidateCountRef = new int[]{candidateCount};
            ModuleOreSim.collectNearbyReal(module, level, player, (pos, state) -> {
               long packed = pos.asLong();
               int index = candidateCountRef[0];
               if (index < 2176 && ghostOccupied.add(packed)) {
                  ghostPositions[index] = packed;
                  ghostStates[index] = state;
                  candidateCountRef[0] = index + 1;
               }
            });
            candidateCount = candidateCountRef[0];
            sortGhostsNearestFirst(candidateCount, player.getEyePosition());
            ghostCount = Math.min(candidateCount, 2048);
            ghostOccupied.clear();

            for (int ix = 0; ix < ghostCount; ix++) {
               ghostOccupied.add(ghostPositions[ix]);
            }

            reverseGhosts(ghostCount);
            ghostOrderVersion++;
            ensureGhostPainterOrder(ghostCount, camera);
            RiptidePerf.endJoinSpike("join.xrayGhost.scan", perf, 6000000L);
            return ghostCount;
         }
      } else {
         return 0;
      }
   }

   private static void sortGhostsNearestFirst(int count, Vec3 eyes) {
      for (int i = 0; i < count; i++) {
         ghostDistances[i] = ghostDistanceSquared(ghostPositions[i], eyes);
      }

      for (int i = 1; i < count; i++) {
         double key = ghostDistances[i];
         long position = ghostPositions[i];
         BlockState state = ghostStates[i];

         int j;
         for (j = i - 1;
            j >= 0 && (ghostDistances[j] > key || Double.compare(ghostDistances[j], key) == 0 && Long.compare(ghostPositions[j], position) > 0);
            j--
         ) {
            ghostDistances[j + 1] = ghostDistances[j];
            ghostPositions[j + 1] = ghostPositions[j];
            ghostStates[j + 1] = ghostStates[j];
         }

         ghostDistances[j + 1] = key;
         ghostPositions[j + 1] = position;
         ghostStates[j + 1] = state;
      }
   }

   private static void ensureGhostPainterOrder(int count, Vec3 cam) {
      if (count <= 1) {
         ghostSortedVersion = ghostOrderVersion;
         ghostSortedCount = count;
         ghostSortedCamera = cam;
      } else if (ghostSortedVersion != ghostOrderVersion
         || ghostSortedCount != count
         || ghostSortedCamera == null
         || !(ghostSortedCamera.distanceToSqr(cam) < 0.015625)) {
         sortGhostsFarthestFirst(count, cam);
         ghostSortedVersion = ghostOrderVersion;
         ghostSortedCount = count;
         ghostSortedCamera = cam;
      }
   }

   private static void reverseGhosts(int count) {
      int left = 0;

      for (int right = count - 1; left < right; right--) {
         long position = ghostPositions[left];
         ghostPositions[left] = ghostPositions[right];
         ghostPositions[right] = position;
         BlockState state = ghostStates[left];
         ghostStates[left] = ghostStates[right];
         ghostStates[right] = state;
         left++;
      }
   }

   private static void sortGhostsFarthestFirst(int count, Vec3 cam) {
      for (int i = 0; i < count; i++) {
         ghostDistances[i] = ghostDistanceSquared(ghostPositions[i], cam);
      }

      for (int i = 1; i < count; i++) {
         double key = ghostDistances[i];
         long position = ghostPositions[i];
         BlockState state = ghostStates[i];

         int j;
         for (j = i - 1;
            j >= 0 && (ghostDistances[j] < key || Double.compare(ghostDistances[j], key) == 0 && Long.compare(ghostPositions[j], position) < 0);
            j--
         ) {
            ghostDistances[j + 1] = ghostDistances[j];
            ghostPositions[j + 1] = ghostPositions[j];
            ghostStates[j + 1] = ghostStates[j];
         }

         ghostDistances[j + 1] = key;
         ghostPositions[j + 1] = position;
         ghostStates[j + 1] = state;
      }
   }

   private static double ghostDistanceSquared(long packed, Vec3 origin) {
      double dx = BlockPos.getX(packed) + 0.5 - origin.x;
      double dy = BlockPos.getY(packed) + 0.5 - origin.y;
      double dz = BlockPos.getZ(packed) + 0.5 - origin.z;
      return dx * dx + dy * dy + dz * dz;
   }

   private static ModuleWorldRenderer.StorageSnapshot barrierSnapshot(Module module, ClientLevel level, Player player) {
      if (module != null && level != null && player != null) {
         long scanKey = ModuleEspChunkCache.generation();
         int revision = ModuleRegistry.revision();
         int chunkX = player.chunkPosition().x();
         int chunkZ = player.chunkPosition().z();
         ModuleWorldRenderer.StorageSnapshot cached = barrierSnapshot;
         if (cached.matches(scanKey, revision, chunkX, chunkZ)) {
            return cached;
         } else {
            long perf = RiptidePerf.beginJoin();
            List<ModuleWorldRenderer.StorageBox> boxes = new ArrayList<>();
            List<ModuleWorldRenderer.StorageTrace> traces = new ArrayList<>();
            ModuleBlockEsp.collectBarriers(
               module,
               level,
               player,
               (box, color) -> boxes.add(new ModuleWorldRenderer.StorageBox(box, color, true)),
               (target, color) -> traces.add(new ModuleWorldRenderer.StorageTrace(target, color))
            );
            ModuleWorldRenderer.StorageSnapshot next = ModuleWorldRenderer.StorageSnapshot.create(
               scanKey, revision, chunkX, chunkZ, boxes, traces, Boolean.parseBoolean(module.value("meshing")), cached
            );
            barrierSnapshot = next;
            RiptidePerf.endJoinSpike("join.barrierEsp.scan", perf, 6000000L);
            return next;
         }
      } else {
         return ModuleWorldRenderer.StorageSnapshot.empty();
      }
   }

   private static ModuleWorldRenderer.WaypointSnapshot waypointSnapshot(Module module, Minecraft mc, ClientLevel level) {
      if (module != null && mc != null && level != null) {
         long gameTime = level.getGameTime();
         RiptideWaypoints store = RiptideWaypoints.get();
         long revision = store.revision();
         String scope = RiptideWaypoints.scopeKey(mc);
         float dotSize = parseFloat(module.value("dot-size"), 3.0F, 1.0F, 10.0F);
         ModuleWorldRenderer.WaypointSnapshot cached = waypointSnapshot;
         if (cached.matches(gameTime, revision, scope, dotSize)) {
            return cached;
         } else {
            List<ModuleWorldRenderer.WaypointDot> previous = cached.revision() == revision && cached.scope().equals(scope) ? cached.dots() : null;
            List<ModuleWorldRenderer.WaypointDot> dots = new ArrayList<>();
            double half = 0.15 * dotSize * 0.5;
            double discRadius = 0.046 * dotSize;
            int index = 0;

            for (RiptideWaypoints.Waypoint waypoint : store.list(scope)) {
               double cx = waypoint.x() + 0.5;
               double cy = waypoint.y() + 0.5;
               double cz = waypoint.z() + 0.5;
               ModuleWorldRenderer.WaypointDot prior = previous != null && index < previous.size() ? previous.get(index) : null;
               FormattedCharSequence name;
               float nameWidth;
               if (prior != null) {
                  name = prior.name();
                  nameWidth = prior.nameWidth();
               } else {
                  name = Component.literal(waypoint.name()).getVisualOrderText();
                  nameWidth = mc.font.width(name);
               }

               dots.add(
                  new ModuleWorldRenderer.WaypointDot(
                     new AABB(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half), waypoint.color(), name, nameWidth
                  )
               );
               index++;
            }

            ModuleWorldRenderer.WaypointSnapshot next = new ModuleWorldRenderer.WaypointSnapshot(
               gameTime, revision, scope, dotSize, discRadius, List.copyOf(dots)
            );
            waypointSnapshot = next;
            return next;
         }
      } else {
         return ModuleWorldRenderer.WaypointSnapshot.empty();
      }
   }

   private static float waypointScale(double dist) {
      if (dist >= 14.0) {
         return (float)Mth.clamp(dist / 12.0, 1.0, 12.0);
      } else {
         return dist >= 6.0 ? 1.0F : 0.6F + 0.39999998F * (float)((dist - 2.5) / 3.5);
      }
   }

   private static float waypointAlpha(double dist) {
      return dist >= 6.0 ? 1.0F : (float)Mth.clamp((dist - 2.5) / 3.5, 0.0, 1.0);
   }

   public static boolean hasPendingWaypointArt() {
      return !pendingWaypointFrame.dots().isEmpty();
   }

   public static void flushWaypointArt(PoseStack matrices) {
      ModuleWorldRenderer.WaypointSnapshot snapshot = pendingWaypointFrame;
      if (!snapshot.dots().isEmpty()) {
         pendingWaypointFrame = ModuleWorldRenderer.WaypointSnapshot.empty();
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.font != null) {
            Vec3 camera = pendingWaypointCamera;
            Quaternionf orientation = pendingWaypointOrientation;
            Vector3f right = orientation.transform(DISC_AXIS_SCRATCH.set(1.0F, 0.0F, 0.0F));
            float rx = right.x;
            float ry = right.y;
            float rz = right.z;
            Vector3f up = orientation.transform(DISC_AXIS_SCRATCH.set(0.0F, 1.0F, 0.0F));
            float ux = up.x;
            float uy = up.y;
            float uz = up.z;
            Pose pose = matrices.last();
            final RiptideBufferSource bufferSource = WAYPOINT_BUFFERS.get();
            final Matrix4f textPose = new Matrix4f();
            GlyphVisitor waypointGlyphs = new GlyphVisitor() {
               public void acceptRenderable(TextRenderable renderable) {
                  renderable.render(textPose, bufferSource.getBuffer(renderable.renderType(DisplayMode.SEE_THROUGH)), 15728880, false);
               }
            };
            Identifier discTexture = waypointDiscTexture(mc);
            double baseRadius = snapshot.discRadius();
            if (discTexture != null) {
               VertexConsumer discBuffer = bufferSource.getBuffer(RiptideRenderTypes.waypointDiscSeeThrough(discTexture));

               for (ModuleWorldRenderer.WaypointDot dot : snapshot.dots()) {
                  double cx = (dot.box().minX + dot.box().maxX) * 0.5;
                  double centerY = (dot.box().minY + dot.box().maxY) * 0.5;
                  double cz = (dot.box().minZ + dot.box().maxZ) * 0.5;
                  double dx = cx - camera.x;
                  double dy = centerY - camera.y;
                  double dz = cz - camera.z;
                  double distSq = dx * dx + dy * dy + dz * dz;
                  if (!(distSq < 6.25)) {
                     double dist = Math.sqrt(distSq);
                     float scale = waypointScale(dist);
                     float alpha = waypointAlpha(dist);
                     if (!(alpha <= 0.0F)) {
                        float radius = (float)(baseRadius * scale);
                        int color = withAlpha(dot.color(), alpha);
                        discVertex(discBuffer, pose, dx + (-rx - ux) * radius, dy + (-ry - uy) * radius, dz + (-rz - uz) * radius, 0.0F, 1.0F, color);
                        discVertex(discBuffer, pose, dx + (-rx + ux) * radius, dy + (-ry + uy) * radius, dz + (-rz + uz) * radius, 0.0F, 0.0F, color);
                        discVertex(discBuffer, pose, dx + (rx + ux) * radius, dy + (ry + uy) * radius, dz + (rz + uz) * radius, 1.0F, 0.0F, color);
                        discVertex(discBuffer, pose, dx + (rx - ux) * radius, dy + (ry - uy) * radius, dz + (rz - uz) * radius, 1.0F, 1.0F, color);
                     }
                  }
               }
            }

            Module waypoints = waypointsModule();
            boolean showDistance = waypoints == null || Boolean.parseBoolean(waypoints.value("show-distance"));

            for (ModuleWorldRenderer.WaypointDot dotx : snapshot.dots()) {
               double cx = (dotx.box().minX + dotx.box().maxX) * 0.5;
               double centerY = (dotx.box().minY + dotx.box().maxY) * 0.5;
               double cz = (dotx.box().minZ + dotx.box().maxZ) * 0.5;
               double dx = cx - camera.x;
               double dy = centerY - camera.y;
               double dz = cz - camera.z;
               double distSq = dx * dx + dy * dy + dz * dz;
               if (!(distSq < 6.25)) {
                  double dist = Math.sqrt(distSq);
                  float scale = waypointScale(dist);
                  float alpha = waypointAlpha(dist);
                  if (!(alpha <= 0.0F)) {
                     double cy = centerY + scale * (baseRadius + 0.06 + 0.115);
                     int color = withAlpha(dotx.color(), alpha);
                     int outline = withAlpha(-15066598, alpha);
                     float poseScale = 0.03125F * scale;
                     String distanceText = showDistance ? " | " + (int)dist + "m" : "";
                     float distanceWidth = distanceText.isEmpty() ? 0.0F : mc.font.width(distanceText);
                     float nameX = -(dotx.nameWidth() + distanceWidth) * 0.5F;
                     textPose.set(pose.pose())
                        .translate((float)dx, (float)(cy - camera.y), (float)dz)
                        .rotate(orientation)
                        .scale(poseScale, -poseScale, poseScale);

                     for (float[] offset : NAMEPLATE_OUTLINE_OFFSETS) {
                        mc.font.prepareText(dotx.name(), nameX + offset[0], -4.5F + offset[1], outline, false, false, 0).visit(waypointGlyphs);
                     }

                     mc.font.prepareText(dotx.name(), nameX, -4.5F, color, false, false, 0).visit(waypointGlyphs);
                     if (showDistance) {
                        int distanceColor = withAlpha(lighten(dotx.color(), 0.375F), alpha);
                        float distanceX = nameX + dotx.nameWidth();

                        for (float[] offset : NAMEPLATE_OUTLINE_OFFSETS) {
                           mc.font.prepareText(distanceText, distanceX + offset[0], -4.5F + offset[1], outline, false, 0).visit(waypointGlyphs);
                        }

                        mc.font.prepareText(distanceText, distanceX, -4.5F, distanceColor, false, 0).visit(waypointGlyphs);
                     }
                  }
               }
            }

            bufferSource.uploadAndDraw();
         }
      }
   }

   private static void discVertex(VertexConsumer buffer, Pose pose, double x, double y, double z, float u, float v, int color) {
      buffer.addVertex(pose, (float)x, (float)y, (float)z).setUv(u, v).setColor(color);
   }

   private static Identifier waypointDiscTexture(Minecraft mc) {
      if (waypointDiscRegistered) {
         return waypointDiscFailed ? null : WAYPOINT_DISC_TEXTURE;
      } else {
         waypointDiscRegistered = true;

         try {
            mc.getTextureManager().register(WAYPOINT_DISC_TEXTURE, new ModuleWorldRenderer.WaypointDiscTexture(buildWaypointDiscImage()));
         } catch (Throwable var2) {
            waypointDiscFailed = true;
            riptide.RiptideClientAddon.LOG.warn("Failed to create the waypoint disc texture", var2);
         }

         return waypointDiscFailed ? null : WAYPOINT_DISC_TEXTURE;
      }
   }

   private static NativeImage buildWaypointDiscImage() {
      NativeImage image = new NativeImage(32, 32, false);
      double center = 15.5;

      for (int y = 0; y < 32; y++) {
         for (int x = 0; x < 32; x++) {
            double dx = x - center;
            double dy = y - center;
            double dist = Math.sqrt(dx * dx + dy * dy);
            image.setPixel(x, y, dist <= 12.0 ? -1 : (dist <= 14.0 ? -15724528 : 0));
         }
      }

      return image;
   }

   private record EntityTraceTarget(Entity entity, int color) {
   }

   private record StorageBox(AABB box, int color, boolean meshable) {
   }

   private record StorageSnapshot(
      long scanKey,
      int revision,
      int chunkX,
      int chunkZ,
      long contentId,
      List<ModuleWorldRenderer.StorageBox> boxes,
      List<ModuleWorldRenderer.StorageTrace> traces,
      boolean meshing,
      List<ModuleWorldRenderer.StorageBox> unmeshedBoxes,
      List<ModuleEspMesh.Box> meshSource,
      ModuleEspMesh.Geometry mesh
   ) {
      private static long nextContentId = 1L;
      private static final ModuleWorldRenderer.StorageSnapshot EMPTY = new ModuleWorldRenderer.StorageSnapshot(
         Long.MIN_VALUE, -1, Integer.MIN_VALUE, Integer.MIN_VALUE, 0L, List.of(), List.of(), false, List.of(), List.of(), ModuleEspMesh.build(List.of())
      );

      private static ModuleWorldRenderer.StorageSnapshot empty() {
         return EMPTY;
      }

      private static ModuleWorldRenderer.StorageSnapshot create(
         long scanKey,
         int revision,
         int chunkX,
         int chunkZ,
         List<ModuleWorldRenderer.StorageBox> mutableBoxes,
         List<ModuleWorldRenderer.StorageTrace> mutableTraces,
         boolean meshing,
         ModuleWorldRenderer.StorageSnapshot previous
      ) {
         List<ModuleWorldRenderer.StorageBox> boxes = List.copyOf(mutableBoxes);
         List<ModuleWorldRenderer.StorageTrace> traces = List.copyOf(mutableTraces);
         if (!meshing) {
            boolean same = previous != null && !previous.meshing() && previous.boxes().size() == boxes.size() && previous.boxes().equals(boxes);
            return new ModuleWorldRenderer.StorageSnapshot(
               scanKey,
               revision,
               chunkX,
               chunkZ,
               same ? previous.contentId() : nextContentId++,
               boxes,
               traces,
               false,
               List.of(),
               List.of(),
               ModuleEspMesh.build(List.of())
            );
         } else {
            List<ModuleWorldRenderer.StorageBox> unmeshed = new ArrayList<>();
            List<ModuleEspMesh.Box> source = new ArrayList<>();

            for (ModuleWorldRenderer.StorageBox target : boxes) {
               if (target.meshable()) {
                  source.add(new ModuleEspMesh.Box(target.box(), target.color()));
               } else {
                  unmeshed.add(target);
               }
            }

            source.sort(ModuleWorldRenderer.StorageSnapshot::compareMeshBoxes);
            List<ModuleEspMesh.Box> frozenSource = List.copyOf(source);
            List<ModuleWorldRenderer.StorageBox> frozenUnmeshed = List.copyOf(unmeshed);
            boolean same = previous != null
               && previous.meshing()
               && previous.meshSource().size() == frozenSource.size()
               && previous.meshSource().equals(frozenSource)
               && previous.unmeshedBoxes().equals(frozenUnmeshed);
            ModuleEspMesh.Geometry geometry = same ? previous.mesh() : ModuleEspMesh.build(frozenSource);
            return new ModuleWorldRenderer.StorageSnapshot(
               scanKey, revision, chunkX, chunkZ, same ? previous.contentId() : nextContentId++, boxes, traces, true, frozenUnmeshed, frozenSource, geometry
            );
         }
      }

      private static int compareMeshBoxes(ModuleEspMesh.Box left, ModuleEspMesh.Box right) {
         int compared = Integer.compare(left.color(), right.color());
         if (compared != 0) {
            return compared;
         } else {
            compared = Double.compare(left.box().minX, right.box().minX);
            if (compared != 0) {
               return compared;
            } else {
               compared = Double.compare(left.box().minY, right.box().minY);
               return compared != 0 ? compared : Double.compare(left.box().minZ, right.box().minZ);
            }
         }
      }

      private boolean matches(long scanKey, int revision, int chunkX, int chunkZ) {
         return this.scanKey == scanKey && this.revision == revision && this.chunkX == chunkX && this.chunkZ == chunkZ;
      }

      private void forEachTrace(BiConsumer<Vec3, Integer> consumer) {
         for (ModuleWorldRenderer.StorageTrace trace : this.traces) {
            consumer.accept(trace.target(), trace.color());
         }
      }

      private void renderFill(Pose pose, VertexConsumer buffer, Vec3 camera, float opacity) {
         if (!this.meshing) {
            for (ModuleWorldRenderer.StorageBox target : this.boxes) {
               ModuleWorldRenderer.fillBox(
                  pose, buffer, target.box().move(-camera.x, -camera.y, -camera.z), ModuleWorldRenderer.withAlpha(target.color(), opacity)
               );
            }
         } else {
            for (ModuleEspMesh.Quad face : this.mesh.quads()) {
               ModuleWorldRenderer.quad(
                  pose,
                  buffer,
                  face.x1() - camera.x,
                  face.y1() - camera.y,
                  face.z1() - camera.z,
                  face.x2() - camera.x,
                  face.y2() - camera.y,
                  face.z2() - camera.z,
                  face.x3() - camera.x,
                  face.y3() - camera.y,
                  face.z3() - camera.z,
                  face.x4() - camera.x,
                  face.y4() - camera.y,
                  face.z4() - camera.z,
                  ModuleWorldRenderer.withAlpha(face.color(), opacity)
               );
            }

            for (ModuleWorldRenderer.StorageBox target : this.unmeshedBoxes) {
               ModuleWorldRenderer.fillBox(
                  pose, buffer, target.box().move(-camera.x, -camera.y, -camera.z), ModuleWorldRenderer.withAlpha(target.color(), opacity)
               );
            }
         }
      }

      private void renderWire(Pose pose, VertexConsumer buffer, Vec3 camera, float width) {
         if (!this.meshing) {
            for (ModuleWorldRenderer.StorageBox target : this.boxes) {
               ModuleWorldRenderer.renderStorageBox(pose, buffer, target.box().move(-camera.x, -camera.y, -camera.z), target.color(), width);
            }
         } else {
            for (ModuleEspMesh.Edge edge : this.mesh.edges()) {
               ModuleWorldRenderer.line(
                  pose,
                  buffer,
                  edge.x1() - camera.x,
                  edge.y1() - camera.y,
                  edge.z1() - camera.z,
                  edge.x2() - camera.x,
                  edge.y2() - camera.y,
                  edge.z2() - camera.z,
                  edge.color(),
                  width
               );
            }

            for (ModuleWorldRenderer.StorageBox target : this.unmeshedBoxes) {
               ModuleWorldRenderer.renderStorageBox(pose, buffer, target.box().move(-camera.x, -camera.y, -camera.z), target.color(), width);
            }
         }
      }
   }

   private record StorageTrace(Vec3 target, int color) {
   }

   private record TracerLine(Vec3 from, Vec3 to, int color, float width) {
   }

   private static final class WaypointDiscTexture extends AbstractTexture {
      private WaypointDiscTexture(NativeImage pixels) {
         this.texture = RenderSystem.getDevice().createTexture("waypoint_disc", 5, GpuFormat.RGBA8_UNORM, pixels.getWidth(), pixels.getHeight(), 1, 1);
         this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
         this.textureView = RenderSystem.getDevice().createTextureView(this.texture);
         RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, pixels);
         pixels.close();
      }
   }

   private record WaypointDot(AABB box, int color, FormattedCharSequence name, float nameWidth) {
   }

   private record WaypointSnapshot(long gameTime, long revision, String scope, float dotSize, double discRadius, List<ModuleWorldRenderer.WaypointDot> dots) {
      private static final ModuleWorldRenderer.WaypointSnapshot EMPTY = new ModuleWorldRenderer.WaypointSnapshot(Long.MIN_VALUE, -1L, "", -1.0F, 0.0, List.of());

      private static ModuleWorldRenderer.WaypointSnapshot empty() {
         return EMPTY;
      }

      private boolean matches(long gameTime, long revision, String scope, float dotSize) {
         return this.gameTime == gameTime && this.revision == revision && this.dotSize == dotSize && this.scope.equals(scope);
      }
   }
}
