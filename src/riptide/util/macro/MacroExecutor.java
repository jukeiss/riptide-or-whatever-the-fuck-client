package riptide.util.macro;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import riptide.addons.AddonManager;
import riptide.api.macro.AddonCondition;
import riptide.api.macro.ContextMacroAction;
import riptide.api.macro.MacroActionRegistry;
import riptide.api.macro.MacroExecutionContext;
import riptide.mixin.accessor.RiptideMultiPlayerGameModeAccessor;
import riptide.modules.AutoTotemModule;
import riptide.modules.PackHideState;
import riptide.modules.RiptideModule;
import riptide.util.PacketRegenerator;
import riptide.util.RiptideAutoTool;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideCommandSuggestionIds;
import riptide.util.RiptideCompatManager;
import riptide.util.RiptideContainerHold;
import riptide.util.RiptideContainerTarget;
import riptide.util.RiptideCraftingHelper;
import riptide.util.RiptideGuiActions;
import riptide.util.RiptideInstaBreakRenderer;
import riptide.util.RiptideInventoryClickHelper;
import riptide.util.RiptideInventoryHelper;
import riptide.util.RiptideLANSync;
import riptide.util.RiptideLiteVariant;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroSneak;
import riptide.util.RiptideNameHarvest;
import riptide.util.RiptideNotifications;
import riptide.util.RiptidePacketNamer;
import riptide.util.RiptidePacketRegistry;
import riptide.util.RiptidePlayerProbe;
import riptide.util.RiptideRotationUtil;
import riptide.util.RiptideSharedState;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiProfile;
import riptide.util.multi.MultiProfileManager;
import riptide.util.multi.MultiSession;
import riptide.util.multi.PacketTeleportController;

public class MacroExecutor {
   private static final Gson GSON = new Gson();
   private static final long MACRO_WAIT_POLL_NANOS = 500000L;
   private static final long CAPTURE_SETTLE_MS = 300L;
   private static final AtomicBoolean isRunning = new AtomicBoolean(false);
   public static final int MAX_CONCURRENT_MACROS = 4;
   private static final Object RUN_LOCK = new Object();
   private static final EnumMap<MacroActionResources.SharedClientResource, ReentrantLock> SHARED_RESOURCE_LOCKS = new EnumMap<>(
      MacroActionResources.SharedClientResource.class
   );
   private static final AtomicLong NEXT_RUN_ID;
   private static final LinkedHashMap<Long, MacroExecutor.RunState> ACTIVE_RUNS;
   private static final ConcurrentHashMap<Long, MacroExecutor.RunState> RUNS_BY_ID;
   private static final AtomicLong RUN_STATE_REVISION;
   private static final ThreadLocal<MacroExecutor.RunState> CURRENT_RUN;
   private static final ThreadLocal<Boolean> SUPPRESS_POST_GUI_AFTER;
   private static volatile MacroExecutor.RunState primaryRun;
   private static volatile boolean visibleRunActive;
   private static RiptideMacro currentMacro;
   private static Thread macroThread;
   private static final AtomicReference<String> lastReceivedPacket;
   private static String currentStatus;
   private static volatile int currentStepIndex;
   private static volatile int totalSteps;
   private static final AtomicBoolean isWaitingForPacket;
   private static final AtomicReference<String> waitingPacketName;
   private static final Object packetWaitLock;
   private static final AtomicBoolean isWaitingForChat;
   private static final AtomicReference<String> waitingChatPattern;
   private static final AtomicBoolean waitingChatIsRegex;
   private static final AtomicReference<String> waitingChatPatternJson;
   private static final AtomicInteger waitingChatFuzzyPercent;
   private static final AtomicReference<String> lastMatchedChat;
   private static final Object recentChatLock;
   private static final Deque<MacroExecutor.RecentChatMessage> recentChatMessages;
   private static final int MAX_RECENT_CHAT_MESSAGES = 256;
   private static final Object recentGuiLock;
   private static final Deque<MacroExecutor.RecentGuiTitle> recentGuiTitles;
   private static final int MAX_RECENT_GUI_TITLES = 128;
   private static String recentChatServerKey;
   private static volatile CompletableFuture<Void> activePacketFuture;
   private static volatile CompletableFuture<Void> activeChatFuture;
   private static volatile CompletableFuture<Void> activeBaritoneGoalFuture;
   private static final AtomicReference<MacroExecutor.RunState> BARITONE_OWNER;
   private static final AtomicBoolean isRotating;
   private static final AtomicReference<MacroExecutor.RunState> ROTATION_OWNER;
   private static final Object ROTATION_STATE_LOCK;
   private static volatile float targetYaw;
   private static volatile float targetPitch;
   private static volatile double rotationSpeed;
   private static volatile int rotationAlignedFrames;
   private static final CopyOnWriteArrayList<MacroAction> persistentActions;
   private static final ConcurrentHashMap<MoveAction, int[]> backgroundMoves;
   private static final ThreadLocal<MacroExecutor.RepeatPacketContext> REPEAT_PACKET_CONTEXT;
   private static final ThreadLocal<Integer> REPEAT_PACKET_SKIP;
   private static final int FLOW_JUMP_BURST_CAP = 100000;
   private static final int FLOW_JUMP_HARD_CAP = 5000000;
   private static volatile List<MacroExecutor.RunState> ACTIVE_RUN_LIST;
   private static final ConcurrentHashMap<String, MacroExecutor.ParsedPacketTarget> PARSED_TARGET_CACHE;
   private static final MacroExecutor.ParsedPacketTarget EMPTY_PACKET_TARGET;
   private static final ConcurrentHashMap<String, MacroExecutor.CachedSuggestions> SUGGESTION_CACHE;
   private static final ConcurrentHashMap<String, MacroExecutor.PendingFetch> PENDING_FETCHES;
   private static final int SUGGESTION_CACHE_MAX = 256;
   private static final long SUGGESTION_CACHE_IDLE_RESET_MS = 3000L;
   private static final int TRUNCATED_REPLY_SIZE = 1000;
   private static final int MIN_SWEEP_CAP = 8;
   private static final int MAX_SWEEP_NAMES = 60000;
   private static final String DRILL_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789_";
   private static volatile long allRunsStoppedAtMs;
   private static final ConcurrentHashMap<String, MacroExecutor.SuggestionUnion> SUGGESTION_UNIONS;
   private static final int MAIN_BARRIER_EVERY = 256;
   private static final ThreadLocal<CompletableFuture<Void>> MAIN_DRAIN_BARRIER;
   private static final ThreadLocal<int[]> INSTANT_BURST;
   private static final ConcurrentLinkedQueue<Runnable> TICK_ALIGNED;
   private static final long ONE_SHOT_EXPIRE_NANOS;
   private static final List<MacroExecutor.OneShotPacketListener> ONE_SHOT_RECV;
   private static final List<MacroExecutor.OneShotPacketListener> ONE_SHOT_SEND;
   private static final List<MacroExecutor.TimedOneShotPacketListener> TIMED_ONE_SHOT_RECV;
   private static final List<MacroExecutor.TimedOneShotPacketListener> TIMED_ONE_SHOT_SEND;
   private static volatile String recentChatServerKeyMirror;

   private static Map<String, Integer> flowLabels(MacroExecutor.RunState run, RiptideMacro macro) {
      Map<String, Integer> cached = run.labelIndex;
      if (cached != null) {
         return cached;
      } else {
         LinkedHashMap<String, Integer> built = new LinkedHashMap<>();

         for (int k = 0; k < macro.actions.size(); k++) {
            if (macro.actions.get(k) instanceof LabelAction la) {
               String key = la.normalizedName();
               if (!key.isEmpty() && !built.containsKey(key)) {
                  built.put(key, k);
               }
            }
         }

         run.labelIndex = built;
         return built;
      }
   }

   private static Integer resolveFlowTarget(MacroExecutor.RunState run, RiptideMacro macro, int i, FlowAction flow, Minecraft mc) {
      return switch (flow.target) {
         case STOP -> null;
         case TOP -> 0;
         case END -> macro.actions.size();
         case FORWARD -> i + 1 + Math.max(0, flow.amount);
         case BACK -> i - Math.max(0, flow.amount);
         case STEP -> Math.max(1, flow.amount) - 1;
         case LABEL -> {
            String name = LabelAction.normalize(MacroVariables.expand(flow.labelName, mc));
            Integer idx = flowLabels(run, macro).get(name);
            if (idx == null) {
               RiptideClientMessaging.sendPrefixed("§eFlow: label '" + flow.labelName + "' not found.");
               yield flow.onMissingLabel == FlowAction.MissingPolicy.STOP ? null : i + 1;
            } else {
               yield idx;
            }
         }
      };
   }

   private static int performFlowJump(MacroExecutor.RunState run, RiptideMacro macro, int currentI, FlowAction flow, Minecraft mc) {
      run.branchElseStart = -1;
      run.branchElseCount = 0;
      Integer dest = resolveFlowTarget(run, macro, currentI, flow, mc);
      if (dest == null) {
         return Integer.MIN_VALUE;
      } else {
         int size = macro.actions.size();
         int target = Math.max(0, Math.min(size, dest));
         if (target <= currentI) {
            run.totalJumpsThisRun++;
            run.jumpBudget++;
            if (run.totalJumpsThisRun > 5000000) {
               RiptideClientMessaging.sendPrefixed("§cMacro aborted: jump limit exceeded (runaway loop).");
               return Integer.MIN_VALUE;
            }

            if (run.jumpBudget >= 100000) {
               run.jumpBudget = 0;

               try {
                  waitForCondition(MacroConditionRegistry.waitForNextTick());
               } catch (Exception var9) {
               }
            }
         } else {
            run.jumpBudget = 0;
         }

         return target - 1;
      }
   }

   private static boolean isCurrentRunActive() {
      if (PackHideState.isHardLocked()) {
         return false;
      } else {
         MacroExecutor.RunState run = CURRENT_RUN.get();
         return run != null ? run.running.get() : isRunning.get();
      }
   }

   public static boolean isCurrentActionRunActive() {
      return isCurrentRunActive();
   }

   private static CopyOnWriteArrayList<MacroAction> currentPersistentActions() {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      return run != null ? run.persistentActions : persistentActions;
   }

   private static ConcurrentHashMap<MoveAction, int[]> currentBackgroundMoves() {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      return run != null ? run.backgroundMoves : backgroundMoves;
   }

   private static void setCurrentStatus(String status) {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      if (run != null) {
         run.status = status == null ? "" : status;
      }

      currentStatus = status == null ? "" : status;
   }

   private static void setCurrentStep(int step, int total) {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      if (run != null) {
         run.currentStepIndex = step;
         run.totalSteps = total;
      }

      currentStepIndex = step;
      totalSteps = total;
   }

   private static void markStepCompleted(int stepOneBased) {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      if (run != null) {
         run.lastCompletedStep = Math.max(run.lastCompletedStep, stepOneBased);
      }
   }

   private static int stepOverrideEndIndex(RiptideMacro macro, int headerIndex, int coveredRows) {
      int last = macro == null ? headerIndex : macro.actions.size() - 1;
      long end = (long)headerIndex + Math.max(0, coveredRows);
      return (int)Math.max((long)headerIndex, Math.min((long)last, end));
   }

   private static void armFpsStepOverride(MacroExecutor.RunState run, int headerIndex, int endIndexInclusive, int fpsLimit) {
      if (run != null) {
         run.fpsStepHeaderIndex = headerIndex;
         run.fpsStepEndIndex = endIndexInclusive;
         FpsLimitController.applyUntilCleared(run.id, fpsLimit);
      }
   }

   private static void armPingStepOverride(
      MacroExecutor.RunState run, int headerIndex, int endIndexInclusive, int delayMs, boolean realIncoming, boolean realOutgoing
   ) {
      if (run != null) {
         run.pingStepHeaderIndex = headerIndex;
         run.pingStepEndIndex = endIndexInclusive;
         PingSpoofController.applyUntilCleared(run.id, delayMs, realIncoming, realOutgoing);
      }
   }

   private static void clearFpsStepOverride(MacroExecutor.RunState run) {
      if (run != null && run.fpsStepHeaderIndex >= 0) {
         run.fpsStepHeaderIndex = -1;
         run.fpsStepEndIndex = -1;
         FpsLimitController.clear(run.id);
      }
   }

   private static void clearPingStepOverride(MacroExecutor.RunState run) {
      if (run != null && run.pingStepHeaderIndex >= 0) {
         run.pingStepHeaderIndex = -1;
         run.pingStepEndIndex = -1;
         PingSpoofController.clearMacro(run.id);
      }
   }

   private static void awaitBackgroundTimedOverrides(MacroExecutor.RunState run) throws InterruptedException {
      if (run != null) {
         long deadline = Math.max(run.fpsBackgroundDeadlineNanos, run.pingBackgroundDeadlineNanos);

         long remaining;
         while (isCurrentRunActive() && (remaining = deadline - System.nanoTime()) > 0L) {
            Thread.sleep(Math.min(25L, remaining / 1000000L + 1L));
         }
      }
   }

   private static void updateStepBoundOverrides(MacroExecutor.RunState run, int index) {
      if (run != null) {
         if (run.fpsStepHeaderIndex >= 0 && (index > run.fpsStepEndIndex || index <= run.fpsStepHeaderIndex)) {
            clearFpsStepOverride(run);
         }

         if (run.pingStepHeaderIndex >= 0 && (index > run.pingStepEndIndex || index <= run.pingStepHeaderIndex)) {
            clearPingStepOverride(run);
         }
      }
   }

   private static void stopCurrentRun() {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      if (run != null) {
         run.running.set(false);
      } else {
         stop();
      }
   }

   static void stopCurrentActionRun() {
      stopCurrentRun();
   }

   private static EnumSet<MacroActionResources.SharedClientResource> resourcesForAction(MacroAction action) {
      return MacroActionResources.resourcesForAction(action);
   }

   private static EnumSet<MacroActionResources.SharedClientResource> acquireClientResources(MacroAction action) throws InterruptedException {
      return acquireClientResources(resourcesForAction(action));
   }

   private static EnumSet<MacroActionResources.SharedClientResource> acquireClientResources(EnumSet<MacroActionResources.SharedClientResource> resources) throws InterruptedException {
      EnumSet<MacroActionResources.SharedClientResource> acquired = EnumSet.noneOf(MacroActionResources.SharedClientResource.class);

      try {
         for (MacroActionResources.SharedClientResource resource : MacroActionResources.SharedClientResource.values()) {
            if (resources.contains(resource)) {
               if (!isCurrentRunActive()) {
                  throw new InterruptedException("Macro stopped");
               }

               SHARED_RESOURCE_LOCKS.get(resource).lockInterruptibly();
               acquired.add(resource);
               if (!isCurrentRunActive()) {
                  throw new InterruptedException("Macro stopped");
               }
            }
         }

         return acquired;
      } catch (InterruptedException var6) {
         releaseClientResources(acquired);
         throw var6;
      }
   }

   private static EnumSet<MacroActionResources.SharedClientResource> resourcesForActions(List<MacroAction> actions) {
      return MacroActionResources.resourcesForActions(actions);
   }

   private static void releaseClientResources(EnumSet<MacroActionResources.SharedClientResource> resources) {
      if (resources != null && !resources.isEmpty()) {
         MacroActionResources.SharedClientResource[] ordered = MacroActionResources.SharedClientResource.values();

         for (int i = ordered.length - 1; i >= 0; i--) {
            MacroActionResources.SharedClientResource resource = ordered[i];
            if (resources.contains(resource)) {
               ReentrantLock lock = SHARED_RESOURCE_LOCKS.get(resource);
               if (lock.isHeldByCurrentThread()) {
                  lock.unlock();
               }
            }
         }
      }
   }

   static MacroVariableContext currentVariableContext() {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      return run == null ? null : run.variables;
   }

   private static void withRunContext(MacroExecutor.RunState run, Runnable task) {
      MacroExecutor.RunState previous = CURRENT_RUN.get();
      if (run != null) {
         CURRENT_RUN.set(run);
      }

      try {
         task.run();
      } finally {
         if (previous != null) {
            CURRENT_RUN.set(previous);
         } else {
            CURRENT_RUN.remove();
         }
      }
   }

   private static MacroExecutor.InlineNextActions collectInlineNextActions(List<MacroAction> actions, int currentIndex) {
      if (actions != null && currentIndex >= 0 && currentIndex < actions.size() - 1) {
         ArrayList<MacroAction> collected = new ArrayList<>();
         int lastIndex = -1;

         for (int j = currentIndex + 1; j < actions.size(); j++) {
            MacroAction candidate = actions.get(j);
            if (candidate instanceof DisconnectAction
               || candidate instanceof RaceAction
               || candidate instanceof ReportAction
               || candidate instanceof RepeatAction
               || candidate instanceof BranchAction
               || candidate instanceof FinallyAction) {
               break;
            }

            if (candidate != null && candidate.isEnabled() && RaceAction.isBodyAction(candidate)) {
               collected.add(candidate);
               lastIndex = j;
            }
         }

         return collected.isEmpty() ? MacroExecutor.InlineNextActions.empty() : new MacroExecutor.InlineNextActions(List.copyOf(collected), lastIndex);
      } else {
         return MacroExecutor.InlineNextActions.empty();
      }
   }

   private static void rebuildActiveRunListLocked() {
      ACTIVE_RUN_LIST = List.copyOf(ACTIVE_RUNS.values());
   }

   private static List<MacroExecutor.RunState> activeRunSnapshot() {
      return ACTIVE_RUN_LIST;
   }

   private static void finishCurrentRun() {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      if (run != null) {
         if (run.finished.compareAndSet(false, true)) {
            requestStop(run, false);
            clearFpsStepOverride(run);
            clearPingStepOverride(run);
            synchronized (RUN_LOCK) {
               ACTIVE_RUNS.remove(run.id);
               RUNS_BY_ID.remove(run.id);
               rebuildActiveRunListLocked();
               if (ACTIVE_RUNS.isEmpty()) {
                  allRunsStoppedAtMs = System.currentTimeMillis();
               }

               RUN_STATE_REVISION.incrementAndGet();
               refreshPrimaryRunLocked();
            }

            if (!isRunning.get()) {
               RiptideInstaBreakRenderer.clear();
               FpsLimitController.clearAll();
               PingSpoofController.clearAllMacros();
            }

            CURRENT_RUN.remove();
         }
      }
   }

   private static boolean hasRunningRunsLocked() {
      for (MacroExecutor.RunState run : ACTIVE_RUNS.values()) {
         if (run.running.get()) {
            return true;
         }
      }

      return false;
   }

   private static MacroExecutor.RunState firstVisibleRunLocked() {
      for (MacroExecutor.RunState run : ACTIVE_RUNS.values()) {
         if (run.running.get() && !run.options.hiddenFromProgressHud()) {
            return run;
         }
      }

      return null;
   }

   private static int activeRunCountLocked() {
      int count = 0;

      for (MacroExecutor.RunState run : ACTIVE_RUNS.values()) {
         if (run.running.get()) {
            count++;
         }
      }

      return count;
   }

   static boolean hasMacroCapacity(int activeCount) {
      return Math.max(0, activeCount) < 4;
   }

   private static void refreshPrimaryRunLocked() {
      primaryRun = firstVisibleRunLocked();
      visibleRunActive = primaryRun != null;
      currentMacro = primaryRun != null ? primaryRun.macro : null;
      currentStatus = primaryRun != null ? primaryRun.status : "";
      currentStepIndex = primaryRun != null ? primaryRun.currentStepIndex : -1;
      totalSteps = primaryRun != null ? primaryRun.totalSteps : 0;
      isRunning.set(hasRunningRunsLocked());
   }

   private static boolean hasRunningRuns() {
      synchronized (RUN_LOCK) {
         return hasRunningRunsLocked();
      }
   }

   public static boolean isVisibleRunning() {
      return visibleRunActive;
   }

   public static boolean isControllingInput() {
      ReentrantLock inputLock = SHARED_RESOURCE_LOCKS.get(MacroActionResources.SharedClientResource.INPUT);
      if (inputLock != null && inputLock.isLocked()) {
         return true;
      } else if (!backgroundMoves.isEmpty()) {
         return true;
      } else {
         for (MacroAction action : persistentActions) {
            if (action instanceof MoveAction || action instanceof JumpAction || action instanceof SneakAction || action instanceof SprintAction) {
               return true;
            }
         }

         synchronized (RUN_LOCK) {
            for (MacroExecutor.RunState run : ACTIVE_RUNS.values()) {
               if (run != null && run.running.get()) {
                  if (!run.backgroundMoves.isEmpty()) {
                     return true;
                  }

                  for (MacroAction actionx : run.persistentActions) {
                     if (actionx instanceof MoveAction || actionx instanceof JumpAction || actionx instanceof SneakAction || actionx instanceof SprintAction) {
                        return true;
                     }
                  }
               }
            }

            return false;
         }
      }
   }

   private static void broadcastStepProgress(MacroExecutor.RunState run, int step, int total, String macroName) {
      if (run == null || !run.options.skipLanProgress()) {
         try {
            RiptideLANSync sync = RiptideLANSync.getInstance();
            if (sync.isInSession()) {
               sync.broadcastStepProgress(step, total, macroName);
            }
         } catch (Exception var5) {
         }
      }
   }

   private static void requestStop(MacroExecutor.RunState run, boolean interruptThread) {
      if (run != null) {
         if (run.running.getAndSet(false)) {
            RUN_STATE_REVISION.incrementAndGet();
         }

         run.persistentActions.clear();

         for (MacroExecutor.PrearmedWait wait : new ArrayList<>(run.prearmedWaits.values())) {
            wait.cleanup();
         }

         run.prearmedWaits.clear();
         releaseBackgroundMoves(run.backgroundMoves, Minecraft.getInstance(), run);
         run.backgroundMoves.clear();
         reconcilePersistentInput(Minecraft.getInstance());
         synchronized (run.packetWaitLock) {
            if (run.packetFuture != null) {
               run.packetFuture.cancel(true);
            }

            run.packetFuture = null;
            run.waitingForPacket.set(false);
            run.waitingPacketName.set("");
         }

         CompletableFuture<Void> chatFuture = run.chatFuture;
         if (chatFuture != null) {
            chatFuture.cancel(true);
         }

         run.chatFuture = null;
         run.waitingForChat.set(false);
         run.waitingChatAction = null;
         CompletableFuture<Void> baritoneFuture = run.baritoneGoalFuture;
         if (baritoneFuture != null) {
            baritoneFuture.cancel(true);
         }

         run.baritoneGoalFuture = null;
         run.activeMineTracker = null;
         Minecraft mc = Minecraft.getInstance();
         PacketGateManager.clearOwnerAndFlushConfigured(run.id, mc == null ? null : mc.getConnection());
         FpsLimitController.clear(run.id);
         PingSpoofController.clearMacro(run.id);
         cancelRotation(run);
         requestOwnedBaritoneStop(run, mc);
         Thread thread = run.macroThread;
         if (interruptThread && thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
         }
      }
   }

   private static void releaseBackgroundMoves(ConcurrentHashMap<MoveAction, int[]> moves, Minecraft mc, MacroExecutor.RunState owner) {
      if (moves != null && !moves.isEmpty() && mc != null) {
         List<MoveAction> toRelease = new ArrayList<>(moves.keySet());
         mc.execute(() -> {
            for (MoveAction move : toRelease) {
               try {
                  releaseMoveIfUnused(owner, move, mc);
               } catch (Exception var6) {
               }
            }
         });
      }
   }

   private static void releaseMoveIfUnused(MacroExecutor.RunState owner, MoveAction move, Minecraft mc) {
      if (move != null && mc != null) {
         boolean stillOwned = false;
         synchronized (RUN_LOCK) {
            label63:
            for (MacroExecutor.RunState active : ACTIVE_RUNS.values()) {
               if (active != owner && active.running.get()) {
                  Iterator var7 = active.backgroundMoves.keySet().iterator();

                  while (true) {
                     if (var7.hasNext()) {
                        MoveAction other = (MoveAction)var7.next();
                        if (other.direction != move.direction) {
                           continue;
                        }

                        stillOwned = true;
                     }

                     if (stillOwned) {
                        break label63;
                     }
                     break;
                  }
               }
            }
         }

         if (!stillOwned && owner != null) {
            for (MoveAction other : backgroundMoves.keySet()) {
               if (other.direction == move.direction) {
                  stillOwned = true;
                  break;
               }
            }
         }

         if (!stillOwned) {
            move.release(mc);
         }
      }
   }

   private static void reconcilePersistentInput(Minecraft mc) {
      if (mc != null) {
         mc.execute(() -> {
            applyAggregateSneakState(mc);
            applyAggregateSprintState(mc);
         });
      }
   }

   private static void applyAggregateSneakState(Minecraft mc) {
      if (mc != null && mc.options != null && mc.options.keyShift != null) {
         boolean held = hasPersistentInput(SneakAction.class);
         mc.options.keyShift.setDown(held);
      }
   }

   private static void applyAggregateSprintState(Minecraft mc) {
      if (mc != null) {
         boolean held = hasPersistentInput(SprintAction.class);
         if (mc.options != null && mc.options.keySprint != null) {
            mc.options.keySprint.setDown(held);
         }

         if (mc.player != null) {
            mc.player.setSprinting(held);
         }
      }
   }

   private static boolean hasPersistentInput(Class<? extends MacroAction> type) {
      for (MacroAction action : persistentActions) {
         if (type.isInstance(action)) {
            return true;
         }
      }

      synchronized (RUN_LOCK) {
         for (MacroExecutor.RunState run : ACTIVE_RUNS.values()) {
            if (run.running.get()) {
               for (MacroAction actionx : run.persistentActions) {
                  if (type.isInstance(actionx)) {
                     return true;
                  }
               }
            }
         }

         return false;
      }
   }

   private static String macroKey(String macroName) {
      return macroName == null ? "" : macroName.trim().toLowerCase(Locale.ROOT);
   }

   private static String macroName(RiptideMacro macro) {
      return macro != null && macro.name != null ? macro.name : "";
   }

   private static boolean matchesMacro(MacroExecutor.RunState run, RiptideMacro macro, String macroName) {
      if (run != null && run.running.get()) {
         if (macro == null || run.sourceMacro != macro && run.macro != macro) {
            String targetKey = macroKey(macroName);
            if (targetKey.isEmpty() && macro != null) {
               targetKey = macroKey(macro.name);
            }

            return !targetKey.isEmpty() && macroKey(macroName(run.macro)).equals(targetKey);
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   public static void execute(RiptideMacro macro) {
      executeTracked(macro);
   }

   public static long executeTracked(RiptideMacro macro) {
      return executeTracked(macro, MacroExecutor.RunOptions.NORMAL);
   }

   public static long executeTracked(RiptideMacro macro, MacroExecutor.RunOptions options) {
      if (PackHideState.isHardLocked()) {
         return -1L;
      } else {
         MacroExecutor.RunOptions runOptions = options == null ? MacroExecutor.RunOptions.NORMAL : options;
         if (macro != null && macro.actions != null && !macro.actions.isEmpty()) {
            RiptideMacro executionMacro;
            try {
               executionMacro = macro.deepCopy();
            } catch (Throwable var15) {
               if (!runOptions.silentLifecycle()) {
                  RiptideClientMessaging.sendPrefixed("§cCould not prepare macro run.");
               }

               return -1L;
            }

            if (executionMacro != null && executionMacro.actions != null && !executionMacro.actions.isEmpty()) {
               MacroExecutor.RunState runState;
               synchronized (RUN_LOCK) {
                  String name = macro.name == null ? "" : macro.name;
                  String nameKey = macroKey(name);
                  if (runOptions.backgroundRun()) {
                     if (!hasMacroCapacity(activeRunCountLocked())) {
                        return -1L;
                     }
                  } else {
                     for (MacroExecutor.RunState active : ACTIVE_RUNS.values()) {
                        if (!active.options.backgroundRun()) {
                           String activeName = active.macro != null && active.macro.name != null ? active.macro.name : "";
                           if (active.running.get() && macroKey(activeName).equals(nameKey)) {
                              if (!runOptions.silentLifecycle()) {
                                 RiptideClientMessaging.sendPrefixed("§eMacro already running: " + name);
                              }

                              return -1L;
                           }
                        }
                     }

                     if (!hasMacroCapacity(activeRunCountLocked())) {
                        if (!runOptions.silentLifecycle()) {
                           RiptideClientMessaging.sendPrefixed("§cOnly 4 macros can run at the same time.");
                        }

                        return -1L;
                     }
                  }

                  runState = new MacroExecutor.RunState(NEXT_RUN_ID.getAndIncrement(), macro, executionMacro, runOptions);
                  if (ACTIVE_RUNS.isEmpty()) {
                     expireSuggestionCacheAfterIdle();
                  }

                  ACTIVE_RUNS.put(runState.id, runState);
                  RUNS_BY_ID.put(runState.id, runState);
                  rebuildActiveRunListLocked();
                  RUN_STATE_REVISION.incrementAndGet();
                  refreshPrimaryRunLocked();
               }

               if (!runOptions.silentLifecycle()) {
                  RiptideClientMessaging.sendPrefixed("§aStarted macro: " + macro.name);
               }

               broadcastStepProgress(runState, 0, executionMacro.actions.size(), executionMacro.name);
               Thread thread = new Thread(() -> run(runState), "Riptide-Macro-" + runState.id);
               thread.setDaemon(true);
               macroThread = thread;
               runState.macroThread = thread;

               try {
                  thread.start();
               } catch (Throwable var14) {
                  requestStop(runState, false);
                  synchronized (RUN_LOCK) {
                     ACTIVE_RUNS.remove(runState.id);
                     RUNS_BY_ID.remove(runState.id);
                     rebuildActiveRunListLocked();
                     if (ACTIVE_RUNS.isEmpty()) {
                        allRunsStoppedAtMs = System.currentTimeMillis();
                     }

                     RUN_STATE_REVISION.incrementAndGet();
                     refreshPrimaryRunLocked();
                     return -1L;
                  }
               }

               return runState.id;
            } else {
               return -1L;
            }
         } else if (runOptions.silentLifecycle()) {
            return -1L;
         } else {
            RiptideClientMessaging.sendPrefixed("§cMacro has no actions!");
            return -1L;
         }
      }
   }

   public static boolean isRunActive(long id) {
      if (id < 0L) {
         return false;
      } else {
         MacroExecutor.RunState run = RUNS_BY_ID.get(id);
         return run != null && run.running.get();
      }
   }

   public static long runStateRevision() {
      return RUN_STATE_REVISION.get();
   }

   public static long currentRunId() {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      return run == null ? -1L : run.id;
   }

   public static void stopRun(long id) {
      if (id >= 0L) {
         MacroExecutor.RunState run;
         synchronized (RUN_LOCK) {
            run = ACTIVE_RUNS.get(id);
         }

         if (run != null) {
            requestStop(run, true);
            synchronized (RUN_LOCK) {
               refreshPrimaryRunLocked();
            }
         }
      }
   }

   private static void run(MacroExecutor.RunState runState) {
      CURRENT_RUN.set(runState);

      try {
         runInternal(runState);
      } catch (Throwable var8) {
         Throwable fatal = var8;
         if (!runState.options.silentLifecycle()) {
            try {
               RiptideClientMessaging.sendPrefixed("§cMacro crashed: " + fatal.getMessage());
            } catch (Throwable var7) {
            }
         }
      } finally {
         finishCurrentRun();
         CURRENT_RUN.remove();
      }
   }

   private static void runInternal(MacroExecutor.RunState runState) {
      CURRENT_RUN.set(runState);
      MacroVariables.clear();
      RiptideMacro macro = runState.macro;
      Minecraft mc = Minecraft.getInstance();
      int loopCount = 0;
      int maxLoops = macro.loop ? (macro.loopCount == -1 ? Integer.MAX_VALUE : macro.loopCount) : 1;
      currentPersistentActions().clear();
      currentBackgroundMoves().clear();
      Thread persistentThread = new Thread(() -> {
         CURRENT_RUN.set(runState);

         try {
            while (isCurrentRunActive()) {
               if (!currentPersistentActions().isEmpty() || !currentBackgroundMoves().isEmpty()) {
                  MacroExecutor.RunState scheduledRunx = CURRENT_RUN.get();
                  mc.execute(() -> withRunContext(scheduledRunx, () -> {
                     ReentrantLock inputLock = SHARED_RESOURCE_LOCKS.get(MacroActionResources.SharedClientResource.INPUT);
                     inputLock.lock();

                     try {
                        for (MacroAction pa : currentPersistentActions()) {
                           try {
                              pa.execute(mc);
                           } catch (Exception var10x) {
                           }
                        }

                        for (Entry<MoveAction, int[]> entry : currentBackgroundMoves().entrySet()) {
                           MoveAction bma = entry.getKey();
                           int[] ticks = entry.getValue();
                           if (ticks[0] > 0) {
                              bma.execute(mc);
                              ticks[0]--;
                           } else {
                              releaseMoveIfUnused(scheduledRunx, bma, mc);
                              currentBackgroundMoves().remove(bma);
                           }
                        }
                     } finally {
                        inputLock.unlock();
                     }
                  }));
               }

               try {
                  CompletableFuture<Void> tf = MacroConditionRegistry.waitForNextTick();
                  tf.get(100L, TimeUnit.MILLISECONDS);
               } catch (Exception var6x) {
                  if (!isCurrentRunActive()) {
                     break;
                  }
               }
            }
         } finally {
            releaseBackgroundMoves(currentBackgroundMoves(), mc, runState);
            currentBackgroundMoves().clear();
            CURRENT_RUN.remove();
         }
      }, "Riptide-Macro-Persistent-" + runState.id);
      persistentThread.setDaemon(true);
      persistentThread.start();

      try {
         for (; isCurrentRunActive() && loopCount < maxLoops; loopCount++) {
            if (macro.actions.isEmpty()) {
               if (!runState.options.silentLifecycle()) {
                  RiptideClientMessaging.sendPrefixed("§cMacro stopped: no actions left.");
               }

               stopCurrentRun();
               break;
            }

            setCurrentStatus("Loop " + (loopCount + 1) + (maxLoops == Integer.MAX_VALUE ? "" : "/" + maxLoops));
            RiptideModule module = RiptideModule.get();
            boolean burstMode = module.usePacketBurstMode();
            totalSteps = macro.actions.size();
            runState.branchElseStart = -1;
            runState.branchElseCount = 0;
            runState.jumpBudget = 0;
            runState.lastCompletedStep = 0;

            for (int i = 0; i < macro.actions.size() && isCurrentRunActive(); i++) {
               setCurrentStep(i, macro.actions.size());
               updateStepBoundOverrides(runState, i);
               if (runState.branchElseStart == i && runState.branchElseCount > 0) {
                  i += runState.branchElseCount - 1;
                  runState.branchElseStart = -1;
                  runState.branchElseCount = 0;
               } else {
                  MacroAction action = macro.actions.get(i);
                  if (!action.isEnabled()) {
                     if (action instanceof RaceAction disabledRace) {
                        i += disabledRace.normalizedBodyCount(macro.actions, i);
                     } else if (action instanceof ReportAction disabledReport) {
                        i += disabledReport.normalizedConditionCount(macro.actions, i);
                     } else if (action instanceof MultiAction disabledMulti) {
                        i += disabledMulti.normalizedStepCount(macro.actions, i);
                     }
                  } else if (MacroDynamicBindings.apply(action, mc) && dynamicTextInputsAvailable(action, mc)) {
                     if (!hasPrearmedWait(action)) {
                        clearOwnCaptureOutput(action);
                     }

                     if (action instanceof BranchAction branch) {
                        boolean branchMatches = branch.matches(mc);
                        if (branchMatches) {
                           runState.branchElseStart = i + 1 + Math.max(0, branch.thenSteps);
                           runState.branchElseCount = Math.max(0, branch.elseSteps);
                        } else {
                           i += Math.max(0, branch.thenSteps);
                        }
                     } else if (action instanceof FinallyAction cleanup) {
                        int bodyCount = Math.max(0, cleanup.bodyCount);
                        runState.finallyActions.clear();

                        for (int c = 1; c <= bodyCount && i + c < macro.actions.size(); c++) {
                           MacroAction body = macro.actions.get(i + c);
                           if (body != null && body.isEnabled()) {
                              runState.finallyActions.add(body);
                           }
                        }

                        i += bodyCount;
                     } else if (action instanceof IfAction ifA) {
                        runState.branchElseStart = -1;
                        runState.branchElseCount = 0;
                        if (ifA.evaluate(mc)) {
                           runState.branchElseStart = i + 1 + Math.max(0, ifA.thenSteps);
                           runState.branchElseCount = Math.max(0, ifA.elseSteps);
                        } else {
                           i += Math.max(0, ifA.thenSteps);
                        }
                     } else if (!(action instanceof LabelAction)) {
                        if (action instanceof FlowAction flow) {
                           if (flow.shouldTake(mc)) {
                              int newI = performFlowJump(runState, macro, i, flow, mc);
                              if (newI == Integer.MIN_VALUE) {
                                 broadcastStepProgress(runState, i + 1, macro.actions.size(), macro.name);
                                 stopCurrentRun();
                                 break;
                              }

                              i = newI;
                           }
                        } else {
                           prearmNextActionWait(mc, macro.actions, i);
                           EnumSet<MacroActionResources.SharedClientResource> heldResources = acquireClientResources(action);

                           try {
                              if (waitForGuiBeforeAction(mc, action)) {
                                 beginRaycastAim(mc, action);
                                 CompletableFuture<Void> postGuiFuture = !(action instanceof ItemAction)
                                       && !(action instanceof DropAction)
                                       && !isTargetWaitInteractionAction(action)
                                    ? createPostGuiFuture(mc, action)
                                    : null;
                                 if (burstMode && action instanceof SendPacketAction) {
                                    List<SendPacketAction> packetBatch = new ArrayList<>();

                                    int batchEnd;
                                    for (batchEnd = i; batchEnd < macro.actions.size() && macro.actions.get(batchEnd) instanceof SendPacketAction; batchEnd++) {
                                       packetBatch.add((SendPacketAction)macro.actions.get(batchEnd));
                                    }

                                    executeBurstPackets(mc, packetBatch);
                                    i = batchEnd - 1;
                                 } else {
                                    if (action instanceof DelayAction da) {
                                       if (da.useTicks) {
                                          for (int t = 0; t < da.delayTicks && isCurrentRunActive(); t++) {
                                             CompletableFuture<Void> future = MacroConditionRegistry.waitForNextTick();
                                             waitForCondition(future);
                                          }
                                       } else {
                                          Thread.sleep(da.delayMs);
                                       }
                                    } else if (action instanceof CustomMenuAction customMenu) {
                                       setCurrentStatus(customMenu.getDisplayName());
                                       customMenu.execute(mc);
                                    } else if (action instanceof FpsAction fps) {
                                       setCurrentStatus(fps.getDisplayName());
                                       if (fps.endMode() == FpsAction.EndMode.TIMEOUT) {
                                          clearFpsStepOverride(runState);
                                          long fpsDurationNanos = fps.durationMillis() * 1000000L;
                                          long fpsDeadline = System.nanoTime() + fpsDurationNanos;
                                          FpsLimitController.apply(runState.id, Math.max(0, fps.maxFps), fpsDurationNanos);
                                          if (fps.continueNextActions) {
                                             runState.fpsBackgroundDeadlineNanos = fpsDeadline;
                                          } else {
                                             long fpsRemaining;
                                             while (isCurrentRunActive() && (fpsRemaining = fpsDeadline - System.nanoTime()) > 0L) {
                                                Thread.sleep(Math.min(25L, fpsRemaining / 1000000L + 1L));
                                             }

                                             FpsLimitController.clear(runState.id);
                                             runState.fpsBackgroundDeadlineNanos = 0L;
                                          }
                                       } else {
                                          int covered = fps.endMode() == FpsAction.EndMode.END ? macro.actions.size() - 1 - i : fps.normalizedStepCount();
                                          int endIndex = stepOverrideEndIndex(macro, i, covered);
                                          armFpsStepOverride(runState, i, endIndex, Math.max(1, fps.maxFps));
                                          runState.fpsBackgroundDeadlineNanos = 0L;
                                       }
                                    } else if (action instanceof SPingAction sping) {
                                       setCurrentStatus(sping.getDisplayName());
                                       if (sping.endMode() == SPingAction.EndMode.TIMEOUT) {
                                          clearPingStepOverride(runState);
                                          long spingDurationNanos = sping.durationMillis() * 1000000L;
                                          long spingDeadline = System.nanoTime() + spingDurationNanos;
                                          PingSpoofController.apply(
                                             runState.id, Math.max(0, sping.pingDelayMs), sping.realIncoming, sping.realOutgoing, spingDurationNanos
                                          );
                                          if (sping.continueNextActions) {
                                             runState.pingBackgroundDeadlineNanos = spingDeadline;
                                          } else {
                                             long spingRemaining;
                                             while (isCurrentRunActive() && (spingRemaining = spingDeadline - System.nanoTime()) > 0L) {
                                                Thread.sleep(Math.min(25L, spingRemaining / 1000000L + 1L));
                                             }

                                             PingSpoofController.clearMacro(runState.id);
                                             runState.pingBackgroundDeadlineNanos = 0L;
                                          }
                                       } else {
                                          int covered = sping.endMode() == SPingAction.EndMode.END ? macro.actions.size() - 1 - i : sping.normalizedStepCount();
                                          int endIndex = stepOverrideEndIndex(macro, i, covered);
                                          armPingStepOverride(runState, i, endIndex, Math.max(0, sping.pingDelayMs), sping.realIncoming, sping.realOutgoing);
                                          runState.pingBackgroundDeadlineNanos = 0L;
                                       }
                                    } else if (action instanceof WaitForPacketAction packetAction) {
                                       packetAction.matchedPacket = null;
                                       packetAction.matchedDirection = "";
                                       MacroExecutor.RunState packetRun = CURRENT_RUN.get();
                                       if (packetRun != null) {
                                          packetRun.lastMatchedPacket = null;
                                          packetRun.lastMatchedPacketDirection = "";
                                       }

                                       List<String> targets = packetAction.effectiveList();
                                       MacroExecutor.PrearmedWait prearmed = consumePrearmedWait(action);
                                       int startTarget = 0;
                                       if (prearmed != null) {
                                          try {
                                             waitForFutureDone(prearmed.future, 0L);
                                             startTarget = prearmed.completedPacketTargets;
                                          } finally {
                                             prearmed.cleanup();
                                          }
                                       }

                                       if (targets.isEmpty()) {
                                          if (prearmed == null) {
                                             awaitPacket("");
                                          }
                                       } else {
                                          for (int targetIndex = Math.min(startTarget, targets.size());
                                             targetIndex < targets.size() && isCurrentRunActive();
                                             targetIndex++
                                          ) {
                                             awaitPacket(targets.get(targetIndex));
                                          }
                                       }

                                       copyLastMatchedPacket(packetAction);
                                    } else if (action instanceof WaitPacketMatchAction wpma) {
                                       setCurrentStatus(wpma.getDisplayName());
                                       if (!waitForPrearmed(wpma, wpma.timeoutMs)) {
                                          waitForPacketMatch(wpma);
                                       }
                                    } else if (action instanceof WaitInventoryPredicateAction wipa) {
                                       setCurrentStatus(wipa.getDisplayName());
                                       if (!waitForPrearmed(wipa, wipa.timeoutMs)) {
                                          waitForPredicate(() -> wipa.matches(mc), wipa.timeoutMs);
                                       }
                                    } else if (action instanceof WaitDurabilityAction wda) {
                                       setCurrentStatus(wda.getDisplayName());
                                       if (wda.useNext) {
                                          waitForDurabilityUseNext(mc, wda);
                                       } else if (!waitForPrearmed(wda, wda.timeoutMs)) {
                                          waitForPredicate(() -> wda.matches(mc), wda.timeoutMs);
                                       }
                                    } else if (action instanceof WaitFreeSlotsAction wfsa) {
                                       setCurrentStatus(wfsa.getDisplayName());
                                       if (!waitForPrearmed(wfsa, wfsa.timeoutMs)) {
                                          waitForPredicate(() -> wfsa.matches(mc), wfsa.timeoutMs);
                                       }
                                    } else if (action instanceof WaitEntityTargetAction weta) {
                                       setCurrentStatus(weta.getDisplayName());
                                       if (!waitForPrearmed(weta, weta.timeoutMs)) {
                                          waitForPredicate(() -> weta.matches(mc), weta.timeoutMs);
                                       }
                                    } else if (action instanceof WaitGuiTypeAction wgta) {
                                       setCurrentStatus(wgta.getDisplayName());
                                       if (!hasPrearmedWait(wgta)) {
                                          clearCaptureOutputs(wgta);
                                       }

                                       if (!waitForPrearmed(wgta, wgta.timeoutMs)) {
                                          if (wgta.waitMode == WaitGuiTypeAction.WaitMode.CLOSE) {
                                             waitForPredicate(guiClosePredicate(wgta, mc), wgta.timeoutMs);
                                             commitStagedCapture(wgta);
                                          } else {
                                             waitForPredicate(() -> {
                                                Optional<Map<String, MacroValue>> captured = wgta.capture(mc);
                                                captured.ifPresent(values -> stageCapture(wgta, (Map<String, MacroValue>)values));
                                                return captured.isPresent();
                                             }, wgta.timeoutMs);
                                             commitStagedCapture(wgta);
                                          }
                                       }
                                    } else if (action instanceof WaitForHealthAction wh) {
                                       setCurrentStatus(wh.waitingStatusText());

                                       try {
                                          if (!waitForPrearmed(wh, 0L)) {
                                             CompletableFuture<Void> future = MacroConditionRegistry.waitForHealth(wh.healthThreshold, wh.below);
                                             waitForCondition(future);
                                          }
                                       } catch (CancellationException var223) {
                                       }
                                    } else if (action instanceof WaitForBlockAction wba) {
                                       setCurrentStatus(wba.getDisplayName());

                                       try {
                                          if (!waitForPrearmed(wba, 0L)) {
                                             CompletableFuture<Void> future = MacroConditionRegistry.waitForBlock(wba);
                                             waitForCondition(future);
                                          }
                                       } catch (CancellationException var222) {
                                       }
                                    } else if (action instanceof WaitForGuiAction wga) {
                                       if (!hasPrearmedWait(wga)) {
                                          clearCaptureOutputs(wga);
                                       }

                                       if (!waitForPrearmed(wga, 0L)) {
                                          if (wga.waitMode == WaitForGuiAction.WaitMode.CLOSE) {
                                             setCurrentStatus("Waiting for GUI close: " + wga.guiTitle);
                                             waitForPredicate(guiClosePredicate(wga, mc), wga.timeoutMs);
                                             commitStagedCapture(wga);
                                          } else {
                                             setCurrentStatus("Waiting for GUI: " + wga.guiTitle);
                                             waitForPredicate(() -> {
                                                Optional<Map<String, MacroValue>> captured = wga.captureGui(mc.gui.screen());
                                                captured.ifPresent(values -> stageCapture(wga, (Map<String, MacroValue>)values));
                                                return captured.isPresent();
                                             }, wga.timeoutMs);
                                             commitStagedCapture(wga);
                                          }
                                       }
                                    } else if (action instanceof WaitForCooldownAction wca) {
                                       ItemTarget cooldownTarget = resolveItemTarget(wca.itemTarget, wca.itemName).resolveTemplate(mc);
                                       if (cooldownTarget == null) {
                                          continue;
                                       }

                                       String cooldownLabel = describeItemTarget(cooldownTarget);
                                       setCurrentStatus(
                                          "Waiting for cooldown: "
                                             + (!cooldownLabel.isEmpty() ? cooldownLabel : (wca.checkMainInteractionHand ? "Main Hand" : "Off Hand"))
                                       );

                                       try {
                                          if (!waitForPrearmed(wca, 0L)) {
                                             CompletableFuture<Void> future = MacroConditionRegistry.waitForCooldown(
                                                cooldownTarget, wca.checkMainInteractionHand
                                             );
                                             waitForCondition(future);
                                          }
                                       } catch (CancellationException var221) {
                                       }
                                    } else if (action instanceof WaitPosAction wpa) {
                                       setCurrentStatus("Waiting for Pos: " + String.format("%.0f, %.0f, %.0f", wpa.x, wpa.y, wpa.z));

                                       try {
                                          if (!waitForPrearmed(wpa, 0L)) {
                                             CompletableFuture<Void> future = MacroConditionRegistry.waitForPos(
                                                wpa.x, wpa.y, wpa.z, wpa.leeway, wpa.checkRotation, wpa.yaw, wpa.pitch, wpa.rotLeeway
                                             );
                                             waitForCondition(future);
                                          }
                                       } catch (CancellationException var220) {
                                       }
                                    } else if (action instanceof JumpAction ja) {
                                       mc.execute(() -> ja.execute(mc));
                                       if (ja.tap) {
                                          CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                                          waitForCondition(tickFuture);
                                       } else {
                                          for (int t = 0; t < ja.durationTicks && isCurrentRunActive(); t++) {
                                             CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                                             waitForCondition(tickFuture);
                                          }
                                       }

                                       mc.execute(() -> ja.release(mc));
                                    } else if (action instanceof SneakAction sna) {
                                       if (sna.persistent && sna.sneak) {
                                          currentPersistentActions().removeIf(p -> p instanceof SneakAction);
                                          currentPersistentActions().add(sna);
                                       } else if (!sna.sneak) {
                                          currentPersistentActions().removeIf(p -> p instanceof SneakAction);
                                       }

                                       if (!sna.persistent && sna.sneak) {
                                          postOnClientThread(mc, () -> sna.execute(mc), sna.isTickAligned());
                                       } else {
                                          postOnClientThread(mc, () -> applyAggregateSneakState(mc), sna.isTickAligned());
                                       }
                                    } else if (action instanceof SprintAction spa) {
                                       if (spa.persistent && spa.sprint) {
                                          currentPersistentActions().removeIf(p -> p instanceof SprintAction);
                                          currentPersistentActions().add(spa);
                                       } else if (!spa.sprint) {
                                          currentPersistentActions().removeIf(p -> p instanceof SprintAction);
                                       }

                                       if (!spa.persistent && spa.sprint) {
                                          postOnClientThread(mc, () -> spa.execute(mc), spa.isTickAligned());
                                       } else {
                                          postOnClientThread(mc, () -> applyAggregateSprintState(mc), spa.isTickAligned());
                                       }
                                    } else {
                                       if (action instanceof DisconnectAction da2) {
                                          broadcastStepProgress(runState, i + 1, macro.actions.size(), macro.name);
                                          if (da2.mode == DisconnectAction.DisconnectMode.DISCONNECT) {
                                             if (da2.delayMs > 0) {
                                                Thread.sleep(da2.delayMs);
                                             }

                                             mc.execute(() -> da2.execute(mc));
                                          } else if (da2.mode == DisconnectAction.DisconnectMode.AUTO_DISCONNECT) {
                                             setCurrentStatus("Auto Disconnect: waiting for " + da2.trigger.name() + "...");
                                             da2.execute(mc);
                                             setCurrentStatus("Auto Disconnect: executed");
                                          } else if (da2.mode == DisconnectAction.DisconnectMode.KICK_DUPE && da2.useNextAction) {
                                             List<MacroAction> nextActs = new ArrayList<>();
                                             int lastSkip = -1;

                                             for (int j = i + 1; j < macro.actions.size(); j++) {
                                                MacroAction candidate = macro.actions.get(j);
                                                if (candidate instanceof DisconnectAction) {
                                                   break;
                                                }

                                                if (candidate.isEnabled()
                                                   && !(candidate instanceof DelayAction)
                                                   && !(candidate instanceof WaitForHealthAction)
                                                   && !(candidate instanceof WaitForBlockAction)
                                                   && !(candidate instanceof WaitForGuiAction)
                                                   && !(candidate instanceof WaitForCooldownAction)
                                                   && !(candidate instanceof WaitPosAction)
                                                   && !(candidate instanceof WaitMovementAction)
                                                   && !(candidate instanceof WaitForEntityAction)
                                                   && !(candidate instanceof WaitForSoundAction)
                                                   && !(candidate instanceof WaitForSlotChangeAction)
                                                   && !(candidate instanceof WaitForPacketAction)
                                                   && !(candidate instanceof WaitForChatAction)
                                                   && !(candidate instanceof GoToAction)) {
                                                   nextActs.add(candidate);
                                                   lastSkip = j;
                                                }
                                             }

                                             da2.setNextActions(nextActs);
                                             mc.execute(() -> da2.execute(mc));
                                             if (lastSkip > i) {
                                                ;
                                             }
                                          } else {
                                             mc.execute(() -> da2.execute(mc));
                                          }

                                          stopCurrentRun();
                                          break;
                                       }

                                       if (action instanceof RaceAction race) {
                                          int bodyCount = runRaceGroup(mc, module, macro.actions, i, race);
                                          if (bodyCount > 0) {
                                             i += bodyCount;
                                          }
                                       } else if (action instanceof MultiAction multi) {
                                          int stepCount = runMultiGroup(mc, module, macro.name, macro.actions, i, multi);
                                          if (stepCount > 0) {
                                             i += stepCount;
                                          }
                                       } else if (action instanceof ReportAction reportAction) {
                                          int conditionCount = runReportGroup(mc, macro.actions, i, reportAction);
                                          if (conditionCount > 0) {
                                             i += conditionCount;
                                          }
                                       } else if (action instanceof NbtBookAction nba) {
                                          int totalBooks = Math.max(1, nba.bookCount);
                                          long delayMs = Math.max(1, nba.delayTicks) * 50L;
                                          boolean signedAny = false;

                                          for (int b = 0; b < totalBooks && isCurrentRunActive(); b++) {
                                             CompletableFuture<Boolean> result = new CompletableFuture<>();
                                             mc.execute(() -> result.complete(nba.executeSingleBook(mc, b, totalBooks)));
                                             Boolean success = result.get();
                                             if (!success) {
                                                break;
                                             }

                                             signedAny = true;
                                             if (b < totalBooks - 1) {
                                                Thread.sleep(delayMs);
                                             }
                                          }

                                          if (signedAny && nba.disconnectAfter) {
                                             mc.execute(() -> nba.afterSigning(mc));
                                          }
                                       } else if (action instanceof GoToAction ga) {
                                          runGoToAction(mc, ga);
                                       } else if (action instanceof MoveAction ma) {
                                          if (ma.nonBlocking) {
                                             mc.execute(() -> ma.execute(mc));
                                             currentBackgroundMoves().put(ma, new int[]{ma.durationTicks - 1});
                                          } else {
                                             for (int t = 0; t < ma.durationTicks && isCurrentRunActive(); t++) {
                                                mc.execute(() -> ma.execute(mc));
                                                CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                                                waitForCondition(tickFuture);
                                             }

                                             MacroExecutor.RunState movementRun = CURRENT_RUN.get();
                                             mc.execute(() -> releaseMoveIfUnused(movementRun, ma, mc));
                                          }
                                       } else if (action instanceof LookAtBlockAction la) {
                                          LookAtBlockAction.RotationTarget rotationTarget = resolveLookAtTargetOnClient(mc, la);
                                          if (rotationTarget == null) {
                                             continue;
                                          }

                                          if (la.smooth) {
                                             startSmoothRotation(rotationTarget.yaw(), rotationTarget.pitch(), la.getRotationStep());
                                             if (la.waitForCompletion) {
                                                waitForSmoothRotationCompletion();
                                             }
                                          } else {
                                             mc.execute(() -> {
                                                if (mc.player != null) {
                                                   mc.player.setYRot(rotationTarget.yaw());
                                                   mc.player.setXRot(rotationTarget.pitch());
                                                }
                                             });
                                          }
                                       } else if (action instanceof RepeatAction ra) {
                                          int startIdx = i + 1;
                                          int endIdx = Math.min(startIdx + ra.stepCount, macro.actions.size());

                                          for (int rep = 0; rep < ra.repeatCount && isCurrentRunActive(); rep++) {
                                             setCurrentStatus("Repeat " + (rep + 1) + "/" + ra.repeatCount);

                                             for (int j = startIdx; j < endIdx && isCurrentRunActive(); j++) {
                                                MacroAction subAction = macro.actions.get(j);
                                                if (subAction.isEnabled()) {
                                                   REPEAT_PACKET_CONTEXT.set(new MacroExecutor.RepeatPacketContext(macro, j + 1, endIdx));
                                                   REPEAT_PACKET_SKIP.set(0);

                                                   try {
                                                      executeSingleActionWithWaits(mc, subAction, module);
                                                      j += Math.max(0, REPEAT_PACKET_SKIP.get());
                                                   } finally {
                                                      REPEAT_PACKET_SKIP.set(0);
                                                      REPEAT_PACKET_CONTEXT.remove();
                                                   }
                                                }
                                             }
                                          }

                                          i = endIdx - 1;
                                       } else if (action instanceof CaptureValueAction captureValue) {
                                          runCaptureValueAction(mc, captureValue);
                                       } else if (action instanceof WaitForChatAction wca) {
                                          setCurrentStatus("Waiting for chat: " + wca.pattern);
                                          if (!waitForPrearmed(wca, wca.timeoutMs)) {
                                             awaitChat(wca);
                                          }
                                       } else if (action instanceof WaitForEntityAction wea) {
                                          setCurrentStatus(wea.getDisplayName());

                                          try {
                                             if (!waitForPrearmed(wea, 0L)) {
                                                waitForPredicate(() -> wea.matchesEntityTarget(mc), wea.timeoutMs);
                                             }
                                          } catch (CancellationException var218) {
                                          }
                                       } else if (action instanceof WaitForSoundAction wsa) {
                                          String sndDesc = wsa.soundIds.isEmpty() ? "any" : wsa.soundIds.get(0);
                                          setCurrentStatus("Waiting for sound: " + sndDesc);

                                          try {
                                             if (!waitForPrearmed(wsa, 0L)) {
                                                CompletableFuture<Void> future = MacroConditionRegistry.waitForSound(wsa);
                                                waitForCondition(future);
                                             }
                                          } catch (CancellationException var217) {
                                          }
                                       } else if (action instanceof MineAction mna) {
                                          runMineAction(mc, mna);
                                       } else if (action instanceof InstaBreakAction) {
                                          runInstaBreakAction(mc, (InstaBreakAction)action);
                                       } else if (action instanceof BreakAction breakAction) {
                                          MacroExecutor.InlineNextActions nextActions = MacroExecutor.InlineNextActions.empty();
                                          if (breakAction.interact && breakAction.runNextSteps) {
                                             nextActions = collectInlineNextActions(macro.actions, i);
                                             breakAction.setNextActions(nextActions.actions());
                                          }

                                          runBreakAction(mc, breakAction);
                                          if (breakAction.didRunNextActions() && nextActions.lastIndex() > i) {
                                             i = nextActions.lastIndex();
                                          }
                                       } else if (action instanceof WaitForSlotChangeAction wsca) {
                                          setCurrentStatus(wsca.getDisplayName());

                                          try {
                                             if (!waitForPrearmed(wsca, 0L)) {
                                                CompletableFuture<Void> future = MacroConditionRegistry.waitForSlotChange(wsca);
                                                waitForCondition(future);
                                             }
                                          } catch (CancellationException var216) {
                                          }
                                       } else if (action instanceof WaitForWorldChangeAction world) {
                                          setCurrentStatus(world.getDisplayName());

                                          try {
                                             if (!waitForPrearmed(world, 0L)) {
                                                CompletableFuture<Void> future = MacroConditionRegistry.waitForWorldChange(world);
                                                waitForCondition(future);
                                             }
                                          } catch (CancellationException var215) {
                                          }
                                       } else if (action instanceof WaitForPositionDeltaAction wpda) {
                                          setCurrentStatus(wpda.getDisplayName());

                                          try {
                                             if (!waitForPrearmed(wpda, 0L)) {
                                                CompletableFuture<Void> future = MacroConditionRegistry.waitForPositionDelta(wpda.distance, wpda.horizontalOnly);
                                                waitForCondition(future);
                                             }
                                          } catch (CancellationException var214) {
                                          }
                                       } else if (action instanceof WaitForTeleportAction teleport) {
                                          setCurrentStatus(teleport.getDisplayName());

                                          try {
                                             if (!waitForPrearmed(teleport, 0L)) {
                                                CompletableFuture<?> future = raceConditionFuture(mc, teleport);
                                                waitForFutureDone(future, 0L);
                                             }
                                          } catch (CancellationException var213) {
                                          }
                                       } else if (action instanceof WaitGamemodeChangeAction gm) {
                                          setCurrentStatus(gm.getDisplayName());

                                          try {
                                             if (!waitForPrearmed(gm, gm.timeoutMs)) {
                                                CompletableFuture<Void> future = MacroConditionRegistry.waitForGamemodeChange(gm);
                                                waitForFutureDone(future, gm.timeoutMs);
                                             }
                                          } catch (CancellationException var212) {
                                          }
                                       } else if (action instanceof WaitMovementAction wm) {
                                          setCurrentStatus(wm.getDisplayName());

                                          try {
                                             if (!waitForPrearmed(wm, 0L)) {
                                                CompletableFuture<?> future = raceConditionFuture(mc, wm.resolveSubAction());
                                                waitForFutureDone(future, 0L);
                                             }
                                          } catch (CancellationException var211) {
                                          }
                                       } else if (action instanceof WaitForLanStepAction wla) {
                                          setCurrentStatus(wla.getDisplayName());

                                          try {
                                             if (!waitForPrearmed(wla, 0L)) {
                                                CompletableFuture<Void> future = MacroConditionRegistry.waitForLanStep(wla);
                                                waitForCondition(future);
                                             }
                                          } catch (CancellationException var210) {
                                          }
                                       } else if (action instanceof WaitForMacroStepAction wma) {
                                          setCurrentStatus(wma.getDisplayName());
                                          if (!waitForPrearmed(wma, wma.timeoutMs)) {
                                             waitForMacroStep(wma);
                                          }
                                       } else {
                                          if (action instanceof StopMacroAction) {
                                             setCurrentStatus("Stopping Macro");
                                             StopMacroAction stopAction = (StopMacroAction)action;
                                             if (stopAction.target != StopMacroAction.StopTarget.SELF) {
                                                stopAction.execute(mc);
                                             } else {
                                                broadcastStepProgress(runState, i + 1, macro.actions.size(), macro.name);
                                                stopCurrentRun();
                                             }
                                             break;
                                          }

                                          if (action instanceof TickSyncAction tsa) {
                                             if (mc.level == null) {
                                                continue;
                                             }

                                             List<Packet<?>> preGenerated = preGeneratePackets(macro, i + 1, tsa.preGenCount);
                                             long targetTick = mc.level.getGameTime() + tsa.tickOffset;
                                             setCurrentStatus("Tick Sync -> " + targetTick + " (" + preGenerated.size() + " pkts)");

                                             while (isCurrentRunActive() && mc.level.getGameTime() < targetTick) {
                                                LockSupport.parkNanos(1000000L);
                                             }

                                             if (isCurrentRunActive()) {
                                                executePreGeneratedBurst(mc, preGenerated);
                                                i += countPreGeneratedActions(macro, i + 1, preGenerated.size());
                                                RiptideClientMessaging.sendPrefixed("Tick sync: " + preGenerated.size() + " pkts sent");
                                             }
                                          } else if (action instanceof RevisionSyncAction rsa) {
                                             if (mc.player == null || mc.player.containerMenu == null) {
                                                continue;
                                             }

                                             List<Packet<?>> preGenerated = preGeneratePackets(macro, i + 1, rsa.preGenCount);
                                             int baseRevision = mc.player.containerMenu.getStateId();
                                             int targetRevision = baseRevision + rsa.revisionOffset;
                                             setCurrentStatus("Rev Sync -> " + targetRevision + " (" + preGenerated.size() + " pkts)");

                                             while (
                                                isCurrentRunActive()
                                                   && mc.player != null
                                                   && mc.player.containerMenu != null
                                                   && mc.player.containerMenu.getStateId() < targetRevision
                                             ) {
                                                LockSupport.parkNanos(500000L);
                                             }

                                             if (isCurrentRunActive() && mc.player != null && mc.player.containerMenu != null) {
                                                executePreGeneratedBurst(mc, preGenerated);
                                                i += countPreGeneratedActions(macro, i + 1, preGenerated.size());
                                                RiptideClientMessaging.sendPrefixed("Rev sync: " + preGenerated.size() + " pkts sent");
                                             }
                                          } else if (action instanceof ServerTickSyncAction stsa) {
                                             if (mc.getConnection() == null) {
                                                continue;
                                             }

                                             List<Packet<?>> preGenerated = preGeneratePackets(macro, i + 1, stsa.preGenCount);

                                             for (int sampleWaitMs = 0;
                                                isCurrentRunActive() && !ServerTickTracker.isReady() && sampleWaitMs < stsa.maxWaitMs;
                                                sampleWaitMs += 50
                                             ) {
                                                float progress = ServerTickTracker.getWarmupProgress();
                                                setCurrentStatus(
                                                   String.format(
                                                      "ServerSync warmup %.0f%% (%d samples, %dms)",
                                                      progress * 100.0F,
                                                      ServerTickTracker.getSampleCount(),
                                                      ServerTickTracker.getTrackingTimeMs()
                                                   )
                                                );
                                                Thread.sleep(50L);
                                             }

                                             if (!isCurrentRunActive()) {
                                                continue;
                                             }

                                             long optimalTime = ServerTickTracker.getOptimalSendTime(stsa.bufferMs, stsa.ignorePing);
                                             long msUntil = (optimalTime - System.nanoTime()) / 1000000L;
                                             int ping = ServerTickTracker.getPingMs();
                                             String pingStr = stsa.ignorePing ? " (no ping)" : " ping:" + ping + "ms";
                                             setCurrentStatus("ServerSync in " + Math.max(0L, msUntil) + "ms" + pingStr + " (" + preGenerated.size() + " pkts)");
                                             long startWait = System.nanoTime();
                                             long maxWaitNanos = stsa.maxWaitMs * 1000000L;

                                             while (isCurrentRunActive()) {
                                                long now = System.nanoTime();
                                                if (now - startWait >= maxWaitNanos || now >= optimalTime) {
                                                   break;
                                                }

                                                long remaining = optimalTime - now;
                                                if (remaining > 2000000L) {
                                                   LockSupport.parkNanos(remaining - 2000000L);
                                                } else {
                                                   Thread.onSpinWait();
                                                }
                                             }

                                             if (isCurrentRunActive()) {
                                                executePreGeneratedBurst(mc, preGenerated);
                                                i += countPreGeneratedActions(macro, i + 1, preGenerated.size());
                                                long actualDelay = (System.nanoTime() - optimalTime) / 1000000L;
                                                RiptideClientMessaging.sendPrefixed("ServerSync: " + preGenerated.size() + " pkts (+" + actualDelay + "ms)");
                                             }
                                          } else if (action instanceof RotateAction ra) {
                                             if (ra.smooth) {
                                                startSmoothRotation(ra.yaw, ra.pitch, ra.getRotationStep());
                                                if (ra.waitForCompletion) {
                                                   waitForSmoothRotationCompletion();
                                                }
                                             } else {
                                                mc.execute(() -> action.execute(mc));
                                             }
                                          } else if (action instanceof PacedTpAction pacedTp) {
                                             runPacedTpAction(mc, pacedTp);
                                          } else if (action instanceof LookAtBlockAction la) {
                                             LookAtBlockAction.RotationTarget rotationTargetx = resolveLookAtTargetOnClient(mc, la);
                                             if (rotationTargetx == null) {
                                                continue;
                                             }

                                             if (la.smooth) {
                                                startSmoothRotation(rotationTargetx.yaw(), rotationTargetx.pitch(), la.getRotationStep());
                                                if (la.waitForCompletion) {
                                                   waitForSmoothRotationCompletion();
                                                }
                                             } else {
                                                mc.execute(() -> {
                                                   if (mc.player != null) {
                                                      mc.player.setYRot(rotationTarget.yaw());
                                                      mc.player.setXRot(rotationTarget.pitch());
                                                   }
                                                });
                                             }
                                          } else if (action instanceof UseItemAction) {
                                             runUseItemAction(mc, (UseItemAction)action);
                                          } else if (action instanceof ItemAction ia) {
                                             if (ia.waitForItem) {
                                                try {
                                                   waitForItemActionTargets(mc, ia);
                                                } catch (CancellationException var209) {
                                                }
                                             }

                                             postGuiFuture = createPostGuiFuture(mc, ia);
                                             if (ia.useClickDelay && ia.clickDelayMs > 0) {
                                                runItemActionPaced(mc, ia);
                                             } else {
                                                runMacroActionOnClientThread(mc, action, "ItemAction");
                                             }
                                          } else if (!(action instanceof DropAction) && !(action instanceof SwapSlotsAction)) {
                                             if (action instanceof CraftAction craftAction) {
                                                runCraftAction(mc, craftAction);
                                             } else if (action instanceof SendPacketAction) {
                                                if (module != null && module.useInstantExecutionMode()) {
                                                   try {
                                                      action.execute(mc);
                                                   } catch (Exception var208) {
                                                      RiptideClientMessaging.sendPrefixed("§cError in SendPacket: " + var208.getMessage());
                                                   }
                                                } else {
                                                   runOnClientThread(mc, () -> {
                                                      try {
                                                         action.execute(mc);
                                                      } catch (Exception var3x) {
                                                         RiptideClientMessaging.sendPrefixed("§cError in SendPacket: " + var3x.getMessage());
                                                      }
                                                   });
                                                }
                                             } else if (action instanceof StoreItemAction sia) {
                                                setCurrentStatus((sia.mode == StoreItemAction.Mode.LOOT ? "Looting" : "Storing") + (sia.persistent ? " ∞" : ""));
                                                if (sia.persistent) {
                                                   while (isCurrentRunActive()) {
                                                      try {
                                                         runStoreItemAction(mc, sia);
                                                      } catch (InterruptedException var225) {
                                                         Thread.currentThread().interrupt();
                                                         break;
                                                      } catch (Exception var226) {
                                                         RiptideClientMessaging.sendPrefixed("Store error: " + var226.getMessage());
                                                      }

                                                      CompletableFuture<Void> siTick = MacroConditionRegistry.waitForNextTick();
                                                      waitForCondition(siTick);
                                                   }
                                                } else {
                                                   try {
                                                      runStoreItemAction(mc, sia);
                                                   } catch (InterruptedException var206) {
                                                      Thread.currentThread().interrupt();
                                                   } catch (Exception var207) {
                                                      RiptideClientMessaging.sendPrefixed("Store error: " + var207.getMessage());
                                                   }
                                                }
                                             } else if (action instanceof ClickAction ca) {
                                                runClickActionBurst(mc, ca);
                                             } else if (action instanceof PayAction) {
                                                runPayAction(mc, (PayAction)action);
                                             } else if (action instanceof XCarryAction xsa) {
                                                if (xsa.mode == XCarryAction.Mode.PUT_IN
                                                   && (
                                                      xsa.transferMode == XCarryAction.TransferMode.CLICK
                                                         || xsa.transferMode == XCarryAction.TransferMode.SAFE_CLICK
                                                   )) {
                                                   if (xsa.transferMode == XCarryAction.TransferMode.SAFE_CLICK) {
                                                      setCurrentStatus("XCarry Safe");
                                                      runXCarrySafeClickPutIn(mc, xsa);
                                                   } else {
                                                      setCurrentStatus("XCarry Click");
                                                      runXCarryClickPutIn(mc, xsa);
                                                   }
                                                } else {
                                                   setCurrentStatus(switch (xsa.mode) {
                                                      case PUT_IN -> "XCarry";
                                                      case TAKE_OUT -> "XCarry Out";
                                                      case DROP -> "XCarry Drop";
                                                   });
                                                   runOnClientThread(mc, () -> {
                                                      try {
                                                         xsa.execute(mc);
                                                      } catch (Exception var3x) {
                                                         RiptideClientMessaging.sendPrefixed("§cXCarry: " + var3x.getMessage());
                                                      }
                                                   });
                                                }
                                             } else if (action instanceof InventoryAuditAction auditAction
                                                && (
                                                   auditAction.mode == InventoryAuditAction.Mode.DUPE
                                                      || auditAction.mode == InventoryAuditAction.Mode.DUPE_SPAM
                                                )) {
                                                setCurrentStatus("Dupe: " + auditAction.dupeVector.name() + " " + auditAction.openMode.name());

                                                try {
                                                   auditAction.executeDupe(mc);
                                                } catch (InterruptedException var204) {
                                                   Thread.currentThread().interrupt();
                                                } catch (Exception var205) {
                                                   RiptideClientMessaging.sendPrefixed("§cDupe error: " + var205.getMessage());
                                                }
                                             } else if (action instanceof ContainerClickSequenceAction sequence) {
                                                runContainerClickSequenceAction(mc, sequence);
                                             } else if (action instanceof PacketBurstAction burst) {
                                                runPacketBurstAction(mc, burst);
                                             } else if (action instanceof BundleDupeV2Action bundleV2) {
                                                setCurrentStatus("Bundle Dupe V2");
                                                bundleV2.execute(mc);
                                             } else if (action instanceof RollbackAction rollback) {
                                                setCurrentStatus("Rollback");

                                                try {
                                                   rollback.executeRollback(mc);
                                                } catch (InterruptedException var202) {
                                                   RiptideClientMessaging.sendPrefixed("§eRollback canceled safely.");
                                                } catch (Exception var203) {
                                                   RiptideClientMessaging.sendPrefixed("§cRollback failed: " + safeThrowableMessage(var203));
                                                }
                                             } else if (action instanceof PacketGateAction pga) {
                                                runPacketGateAction(mc, pga);
                                             } else if (isTargetWaitInteractionAction(action)) {
                                                postGuiFuture = runTargetWaitInteractionAction(mc, action);
                                             } else if (action instanceof MacroVariablesAction || action instanceof AssertAction) {
                                                action.execute(mc);
                                             } else if (action instanceof UseItemPhaseAction phaseAction) {
                                                runUseItemPhaseAction(mc, phaseAction);
                                             } else if (action instanceof ContextMacroAction contextAction) {
                                                runContextMacroAction(contextAction);
                                             } else if (action instanceof PlaceAction placeInteract && placeInteract.interact) {
                                                runPlaceAction(mc, placeInteract);
                                             } else if (action instanceof PlaceAction place && place.waitForItem) {
                                                ItemTarget placeTarget = place.resolvedItemTarget().resolveTemplate(mc);
                                                if (placeTarget == null) {
                                                   postGuiFuture = null;
                                                } else {
                                                   if (placeTarget.hasIdentity() || placeTarget.hasSlot()) {
                                                      setCurrentStatus("Waiting for " + describeItemTarget(placeTarget));

                                                      try {
                                                         waitForItemActionEntry(mc, placeTarget);
                                                      } catch (CancellationException var201) {
                                                      }
                                                   }

                                                   if (isCurrentRunActive()) {
                                                      postOnClientThread(mc, () -> {
                                                         if (!PackHideState.isHardLocked()) {
                                                            try {
                                                               place.execute(mc);
                                                            } catch (Exception var3x) {
                                                               RiptideClientMessaging.sendPrefixed("§cError in Place: " + var3x.getMessage());
                                                            }
                                                         }
                                                      }, place.isTickAligned());
                                                   }
                                                }
                                             } else if (usesTickAlignedDispatch(action)) {
                                                runMacroActionOnClientThread(mc, action, action.getDisplayName());
                                             } else if (module != null && module.useInstantExecutionMode()) {
                                                CountDownLatch latch = new CountDownLatch(1);
                                                MacroExecutor.RunState scheduledRun = CURRENT_RUN.get();
                                                mc.execute(() -> withRunContext(scheduledRun, () -> {
                                                   try {
                                                      if (!PackHideState.isHardLocked()) {
                                                         action.execute(mc);
                                                         return;
                                                      }
                                                   } catch (Exception var7x) {
                                                      RiptideClientMessaging.sendPrefixed("§cError in action: " + var7x.getMessage());
                                                      return;
                                                   } finally {
                                                      latch.countDown();
                                                   }
                                                }));

                                                try {
                                                   latch.await(100L, TimeUnit.MILLISECONDS);
                                                } catch (InterruptedException var200) {
                                                   Thread.currentThread().interrupt();
                                                }
                                             } else {
                                                runMacroActionOnClientThread(mc, action, action.getDisplayName());
                                             }
                                          } else {
                                             if (action instanceof DropAction dropAction) {
                                                postGuiFuture = createPostGuiFuture(mc, dropAction);
                                             }

                                             runMacroActionOnClientThread(mc, action, action.getDisplayName());
                                          }
                                       }
                                    }

                                    commitConditionOutput(action, mc);
                                    if (postGuiFuture != null && isCurrentRunActive()) {
                                       try {
                                          waitForCondition(postGuiFuture);
                                       } catch (CancellationException var199) {
                                       }
                                    }

                                    if (isCurrentRunActive()) {
                                       markStepCompleted(i + 1);
                                       broadcastStepProgress(runState, i + 1, macro.actions.size(), macro.name);
                                    }

                                    if (isCurrentRunActive() && !isInstantInventoryAction(action) && module != null && module.useInstantExecutionMode()) {
                                       int delayUs = module.getActionDelayUs();
                                       if (delayUs > 0) {
                                          LockSupport.parkNanos(delayUs * 1000L);
                                       } else {
                                          Thread.onSpinWait();
                                       }

                                       yieldToMainThread(mc);
                                    }
                                 }
                              }
                           } finally {
                              MacroRaycastAim.release();
                              releaseClientResources(heldResources);
                           }
                        }
                     }
                  }
               }
            }

            if (macro.actions.isEmpty()) {
               if (!runState.options.silentLifecycle()) {
                  RiptideClientMessaging.sendPrefixed("§cMacro stopped: no actions left.");
               }

               stopCurrentRun();
               break;
            }

            if (isCurrentRunActive()) {
               PacketGateManager.clearOwnerAndFlushConfigured(runState.id, mc == null ? null : mc.getConnection());
            }

            if (macro.loop && loopCount < maxLoops - 1 && isCurrentRunActive()) {
               if (module != null && module.useInstantExecutionMode()) {
                  int delayUs = module.getActionDelayUs();
                  if (delayUs > 0) {
                     LockSupport.parkNanos(delayUs * 1000L);
                  } else {
                     Thread.onSpinWait();
                  }

                  yieldToMainThread(mc);
               } else if (module != null && module.useMsSleepMode()) {
                  int sleepMs = module.getMsSleepInterval();
                  Thread.sleep(sleepMs);
               } else {
                  try {
                     CompletableFuture<Void> syncFuture = MacroConditionRegistry.waitForNextTick();
                     waitForCondition(syncFuture);
                  } catch (CancellationException var198) {
                  }
               }
            }
         }

         awaitBackgroundTimedOverrides(runState);
         if (isCurrentRunActive() && !runState.options.silentLifecycle()) {
            RiptideClientMessaging.sendPrefixed("§aMacro finished: " + macro.name);
         }
      } catch (InterruptedException var228) {
      } catch (Exception var229) {
         if (!runState.options.silentLifecycle()) {
            RiptideClientMessaging.sendPrefixed("§cMacro crashed: " + var229.getMessage());
         }

         riptide.RiptideClientAddon.LOG.warn("[Riptide] Macro crashed: {}", macro.name, var229);
      } finally {
         if (!PackHideState.isHardLocked()) {
            runFinallyActions(runState, mc);
         }

         PacketGateManager.clearOwnerAndFlushConfigured(runState.id, mc == null ? null : mc.getConnection());
         finishCurrentRun();
         cancelRotation(runState);
         if (!isRunning.get()) {
            MacroConditionRegistry.cancelAll();
         }

         broadcastStepProgress(runState, -1, 0, "");
      }
   }

   private static String normalizePacketKey(String value) {
      return RiptidePacketNamer.normalizePacketKey(value);
   }

   private static boolean packetKeyMatches(String normalizedExpected, String candidate) {
      if (!normalizedExpected.isEmpty() && candidate != null && !candidate.isEmpty()) {
         String normalizedCandidate = normalizePacketKey(candidate);
         if (normalizedCandidate.isEmpty()) {
            return false;
         } else if (normalizedExpected.equals(normalizedCandidate)) {
            return true;
         } else {
            if (normalizedCandidate.endsWith("packet")) {
               String strippedCandidate = normalizedCandidate.substring(0, normalizedCandidate.length() - "packet".length());
               if (normalizedExpected.equals(strippedCandidate)) {
                  return true;
               }
            }

            return normalizedExpected.endsWith(normalizedCandidate) || normalizedCandidate.endsWith(normalizedExpected);
         }
      } else {
         return false;
      }
   }

   private static boolean packetNameMatches(String expected, String candidate) {
      return expected != null && !expected.isEmpty() ? packetKeyMatches(normalizePacketKey(expected), candidate) : false;
   }

   private static MacroExecutor.ParsedPacketTarget parsedPacketTarget(String target) {
      if (target == null) {
         return EMPTY_PACKET_TARGET;
      } else {
         if (PARSED_TARGET_CACHE.size() > 256) {
            PARSED_TARGET_CACHE.clear();
         }

         return PARSED_TARGET_CACHE.computeIfAbsent(target, t -> {
            String expectedName = WaitForPacketAction.getPacketName(t);
            return new MacroExecutor.ParsedPacketTarget(WaitForPacketAction.getDirection(t), expectedName, normalizePacketKey(expectedName));
         });
      }
   }

   private static boolean matchesPacketTarget(String target, Packet<?> packet, String direction) {
      if (packet == null) {
         return false;
      } else {
         MacroExecutor.ParsedPacketTarget parsed = parsedPacketTarget(target);
         if (!parsed.direction().isEmpty() && !parsed.direction().equalsIgnoreCase(direction)) {
            return false;
         } else if (parsed.expectedName().isEmpty()) {
            return true;
         } else {
            String normalizedExpected = parsed.normalizedExpected();
            if (packetKeyMatches(normalizedExpected, RiptidePacketNamer.getFriendlyName(packet, direction))) {
               return true;
            } else if (packetKeyMatches(normalizedExpected, RiptidePacketNamer.getFriendlyName(packet))) {
               return true;
            } else if (packetKeyMatches(normalizedExpected, packet.getClass().getSimpleName())) {
               return true;
            } else {
               String fullName = RiptidePacketRegistry.getName(packet.getClass());
               return packetKeyMatches(normalizedExpected, fullName);
            }
         }
      }
   }

   private static void awaitPacket(String target) {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      if (run != null) {
         CompletableFuture<Void> future = new CompletableFuture<>();
         String normalizedTarget = WaitForPacketAction.normalizeTarget(target);
         synchronized (run.packetWaitLock) {
            run.waitingPacketName.set(normalizedTarget);
            run.packetFuture = future;
            run.waitingForPacket.set(true);
         }

         try {
            setCurrentStatus(
               normalizedTarget.isEmpty() ? "Waiting for packet: Any" : "Waiting for packet: " + WaitForPacketAction.getDisplayLabel(normalizedTarget)
            );
            waitForCondition(future);
         } finally {
            synchronized (run.packetWaitLock) {
               if (run.packetFuture == future) {
                  run.waitingForPacket.set(false);
                  run.waitingPacketName.set("");
                  run.packetFuture = null;
               }
            }
         }
      } else {
         CompletableFuture<Void> future = new CompletableFuture<>();
         String normalizedTarget = WaitForPacketAction.normalizeTarget(target);
         synchronized (packetWaitLock) {
            waitingPacketName.set(normalizedTarget);
            lastReceivedPacket.set("");
            activePacketFuture = future;
            isWaitingForPacket.set(true);
         }

         try {
            setCurrentStatus(
               normalizedTarget.isEmpty() ? "Waiting for packet: Any" : "Waiting for packet: " + WaitForPacketAction.getDisplayLabel(normalizedTarget)
            );
            waitForCondition(future);
         } finally {
            synchronized (packetWaitLock) {
               if (activePacketFuture == future) {
                  isWaitingForPacket.set(false);
                  waitingPacketName.set("");
                  activePacketFuture = null;
               }
            }
         }
      }
   }

   private static void awaitChat(WaitForChatAction wca) {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      if (run != null) {
         clearCaptureOutputs(wca);
         CompletableFuture<Void> future = new CompletableFuture<>();
         run.chatFuture = future;
         run.waitingChatAction = wca;
         run.waitingForChat.set(true);

         try {
            if (wca.timeoutMs > 0) {
               future.completeOnTimeout(null, wca.timeoutMs, TimeUnit.MILLISECONDS);
            }

            waitForCondition(future);
            commitStagedCapture(wca);
         } finally {
            if (run.chatFuture == future) {
               run.waitingForChat.set(false);
               run.waitingChatAction = null;
               run.chatFuture = null;
            }
         }
      } else {
         CompletableFuture<Void> future = new CompletableFuture<>();
         activeChatFuture = future;
         isWaitingForChat.set(true);
         waitingChatPattern.set(normalizeManualText(wca.pattern));
         waitingChatPatternJson.set(wca.patternJson == null ? "" : wca.patternJson);
         waitingChatIsRegex.set(wca.useRegex);
         waitingChatFuzzyPercent.set(WaitForChatAction.clampFuzzyPercent(wca.fuzzyPercent));
         lastMatchedChat.set("");

         try {
            if (wca.timeoutMs > 0) {
               future.completeOnTimeout(null, wca.timeoutMs, TimeUnit.MILLISECONDS);
            }

            waitForCondition(future);
         } finally {
            isWaitingForChat.set(false);
            waitingChatPattern.set("");
            waitingChatPatternJson.set("");
            waitingChatIsRegex.set(false);
            waitingChatFuzzyPercent.set(100);
            activeChatFuture = null;
         }
      }
   }

   private static void awaitCaptureValueChat(CaptureValueAction action) {
      if (action != null) {
         MacroExecutor.RunState run = CURRENT_RUN.get();
         clearCaptureOutputs(action);
         MacroExecutor.OneShotPacketListener listener = awaitReceive(
            packet -> {
               MacroExecutor.ChatCapture capture = extractIncomingChat(packet);
               if (capture == null) {
                  return false;
               } else {
                  MacroExecutor.RecentChatMessage message = new MacroExecutor.RecentChatMessage(
                     capture.sender() == null ? "" : capture.sender(),
                     capture.message() == null ? "" : capture.message(),
                     capture.displayText() == null ? "" : capture.displayText(),
                     capture.displayComponent(),
                     capture.source(),
                     System.currentTimeMillis()
                  );
                  CaptureValueAction.Preview preview = action.previewChat(message);
                  if (!preview.success()) {
                     return false;
                  } else {
                     if (run != null) {
                        run.stagedCaptures.put(action, preview.values());
                     } else {
                        MacroVariables.setAll(preview.values());
                     }

                     return true;
                  }
               }
            }
         );

         try {
            if (waitForFutureDone(listener.future, 0L) && run != null) {
               commitStagedCapture(action);
            }
         } finally {
            listener.cancel();
         }
      }
   }

   static CaptureListSelector.State captureSelectionState(MacroAction action) {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      if (run != null && action != null) {
         synchronized (run.captureSelection) {
            return run.captureSelection.computeIfAbsent(action, a -> new CaptureListSelector.State());
         }
      } else {
         return null;
      }
   }

   private static void expireSuggestionCacheAfterIdle() {
      long stoppedAt = allRunsStoppedAtMs;
      if (stoppedAt > 0L && System.currentTimeMillis() - stoppedAt > 3000L) {
         SUGGESTION_CACHE.clear();
         PENDING_FETCHES.clear();
         SUGGESTION_UNIONS.clear();
      }
   }

   static String buildAutofillQuery(String resolvedCommand, CaptureListSelector.Filter filter, String filterText) {
      String query = resolvedCommand.startsWith("/") ? resolvedCommand : "/" + resolvedCommand;
      if (!query.contains(" ")) {
         query = query + " ";
      }

      String prefix = filter == CaptureListSelector.Filter.PREFIX && filterText != null ? filterText.trim() : "";
      if (!prefix.isEmpty() && !prefix.contains(" ") && query.endsWith(" ")) {
         int end = query.length();

         while (end > 0 && query.charAt(end - 1) == ' ') {
            end--;
         }

         query = query.substring(0, end) + " " + prefix;
      }

      return query;
   }

   private static List<String> capturePool(ClientPacketListener connection, CaptureValueAction action, String query, List<String> base) {
      if (!action.autofillCacheList && base.size() < 1000) {
         return base;
      } else {
         MacroExecutor.SuggestionUnion union = SUGGESTION_UNIONS.compute(
            query,
            (key, existing) -> (MacroExecutor.SuggestionUnion)(existing != null && existing.connection == connection
               ? existing
               : new MacroExecutor.SuggestionUnion(connection, query))
         );
         synchronized (union) {
            union.names.addAll(base);
            union.cap = Math.max(union.cap, base.size());
            advanceSweep(union, connection, action.autofillTimeoutMs);
            List<String> pool = new ArrayList<>(union.names);
            boolean ordered = action.listSelection == CaptureListSelector.Selection.SEQUENTIAL
               || action.listSelection == CaptureListSelector.Selection.FIRST
               || action.listSelection == CaptureListSelector.Selection.LAST
               || action.listSelection == CaptureListSelector.Selection.POSITION;
            if (ordered) {
               pool.sort(String.CASE_INSENSITIVE_ORDER);
            }

            return pool;
         }
      }
   }

   private static void advanceSweep(MacroExecutor.SuggestionUnion union, ClientPacketListener connection, int timeoutMs) {
      if (union.names.size() >= 60000) {
         union.sweepQueue.clear();
      } else {
         while (!union.sweepQueue.isEmpty()) {
            if (union.alphabetCursor < "abcdefghijklmnopqrstuvwxyz0123456789_".length()) {
               String target = union.sweepQueue.peek() + "abcdefghijklmnopqrstuvwxyz0123456789_".charAt(union.alphabetCursor);
               MacroExecutor.CachedSuggestions cached = SUGGESTION_CACHE.get(target);
               if (cached == null || cached.connection() != connection) {
                  fireSuggestionFetch(connection, target, timeoutMs);
                  return;
               }

               union.names.addAll(cached.suggestions());
               int size = cached.suggestions().size();
               boolean truncated = size >= union.cap && union.cap >= 8;
               union.cap = Math.max(union.cap, size);
               if (truncated) {
                  union.sweepQueue.add(target);
               }

               union.alphabetCursor++;
            } else {
               union.sweepQueue.poll();
               union.alphabetCursor = 0;
            }
         }
      }
   }

   private static void applyCaptureSuggestions(MacroExecutor.RunState run, CaptureValueAction action, List<String> suggestions, String query) {
      CaptureValueAction.Preview preview = action.previewSuggestions(suggestions, query);
      if (preview.success()) {
         if (run != null) {
            run.variables.setAll(preview.values());
         } else {
            MacroVariables.setAll(preview.values());
         }
      }
   }

   private static MacroExecutor.PendingFetch fireSuggestionFetch(ClientPacketListener connection, String query, int timeoutMs) {
      MacroExecutor.PendingFetch existing = PENDING_FETCHES.get(query);
      if (existing != null && existing.connection() == connection && !existing.future().isDone()) {
         return existing;
      } else if (PENDING_FETCHES.size() > 256) {
         return existing;
      } else {
         int requestId = RiptideCommandSuggestionIds.nextMacroId();
         CompletableFuture<List<String>> future = new CompletableFuture<>();
         MacroExecutor.OneShotPacketListener listener = awaitReceive(packet -> {
            if (packet instanceof ClientboundCommandSuggestionsPacket reply && reply.id() == requestId) {
               List<String> texts = new ArrayList<>();
               if (reply.suggestions() != null) {
                  for (net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket.Entry entry : reply.suggestions()) {
                     if (entry != null && entry.text() != null && !entry.text().isBlank()) {
                        texts.add(entry.text());
                     }
                  }
               }

               future.complete(texts);
               return true;
            } else {
               return false;
            }
         });
         MacroExecutor.PendingFetch pending = new MacroExecutor.PendingFetch(future, connection);
         PENDING_FETCHES.put(query, pending);
         future.completeOnTimeout(null, timeoutMs > 0 ? Math.max(1000, timeoutMs) : 30000L, TimeUnit.MILLISECONDS);
         future.whenComplete((texts, error) -> {
            listener.cancel();
            PENDING_FETCHES.remove(query, pending);
            if (texts != null) {
               if (SUGGESTION_CACHE.size() >= 256) {
                  SUGGESTION_CACHE.clear();
               }

               SUGGESTION_CACHE.put(query, new MacroExecutor.CachedSuggestions(List.copyOf(texts), connection));
            }
         });

         try {
            connection.send(new ServerboundCommandSuggestionPacket(requestId, query));
         } catch (Throwable var9) {
            future.complete(null);
         }

         return pending;
      }
   }

   private static void runCaptureCommandAutofill(Minecraft mc, CaptureValueAction action) {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      clearCaptureOutputs(action);
      MacroTemplate.Resolution resolved = MacroVariables.resolve(action.autofillCommand, mc);
      if (resolved.success() && !resolved.value().isBlank()) {
         String query = buildAutofillQuery(resolved.value(), action.listFilter, action.listFilterText);
         ClientPacketListener connection = mc.getConnection();
         if (connection != null) {
            if (action.autofillCacheList) {
               MacroExecutor.CachedSuggestions listCache = SUGGESTION_CACHE.get(query);
               if (listCache != null && listCache.connection() == connection && !listCache.suggestions().isEmpty()) {
                  setCurrentStatus("Using cached suggestions");
                  applyCaptureSuggestions(run, action, listCache.suggestions(), query);
               } else {
                  setCurrentStatus("Sweeping command suggestions");
                  List<String> all = sweepAutofillCommand(connection, query);
                  if (!all.isEmpty()) {
                     if (SUGGESTION_CACHE.size() >= 256) {
                        SUGGESTION_CACHE.clear();
                     }

                     SUGGESTION_CACHE.put(query, new MacroExecutor.CachedSuggestions(List.copyOf(all), connection));
                     applyCaptureSuggestions(run, action, all, query);
                  }
               }
            } else {
               MacroExecutor.CachedSuggestions cached = SUGGESTION_CACHE.get(query);
               if (cached != null && (cached.connection() != connection || cached.suggestions().isEmpty())) {
                  cached = null;
               }

               if (cached != null) {
                  setCurrentStatus("Using cached suggestions");
                  applyCaptureSuggestions(run, action, capturePool(connection, action, query, cached.suggestions()), query);
                  fireSuggestionFetch(connection, query, action.autofillTimeoutMs);
               } else {
                  setCurrentStatus("Requesting command suggestions");
                  MacroExecutor.PendingFetch pending = fireSuggestionFetch(connection, query, action.autofillTimeoutMs);
                  if (pending != null) {
                     if (waitForFutureDone(pending.future(), 0L)) {
                        List<String> suggestions = pending.future().getNow(null);
                        if (suggestions != null && !suggestions.isEmpty()) {
                           applyCaptureSuggestions(run, action, capturePool(connection, action, query, suggestions), query);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static List<String> sweepAutofillCommand(final ClientPacketListener connection, String query) {
      RiptideNameHarvest.Control control = new RiptideNameHarvest.Control() {
         @Override
         public boolean cancelled() {
            Minecraft mc = Minecraft.getInstance();
            return !MacroExecutor.isCurrentRunActive() || mc == null || mc.getConnection() != connection;
         }

         @Override
         public boolean paused() {
            return false;
         }

         @Override
         public int limit() {
            return 60000;
         }

         @Override
         public int maxQueries() {
            return 50000;
         }

         @Override
         public boolean usernamesOnly() {
            return false;
         }
      };
      List<String> all = RiptideNameHarvest.sweepCommand(connection, query, control);
      all.sort(String.CASE_INSENSITIVE_ORDER);
      return all;
   }

   private static void runCaptureNamescrape(Minecraft mc, CaptureValueAction action) {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      clearCaptureOutputs(action);
      ClientPacketListener connection = mc.getConnection();
      if (connection != null) {
         String cacheKey = "\u0000namescrape";
         MacroExecutor.CachedSuggestions cached = SUGGESTION_CACHE.get(cacheKey);
         if (cached != null && cached.connection() == connection && !cached.suggestions().isEmpty()) {
            setCurrentStatus("Using cached names");
            applyCaptureSuggestions(run, action, cached.suggestions(), "");
         } else {
            setCurrentStatus("Scraping player names");
            List<String> names = scrapeAllNames(mc, connection);
            if (!names.isEmpty()) {
               if (SUGGESTION_CACHE.size() >= 256) {
                  SUGGESTION_CACHE.clear();
               }

               SUGGESTION_CACHE.put(cacheKey, new MacroExecutor.CachedSuggestions(List.copyOf(names), connection));
               applyCaptureSuggestions(run, action, names, "");
            }
         }
      }
   }

   private static List<String> scrapeAllNames(Minecraft mc, final ClientPacketListener connection) {
      String self = mc.player == null ? "" : mc.player.getName().getString();
      LinkedHashMap<String, String> names = RiptideNameHarvest.instantNames(mc, self);
      List<RiptideNameHarvest.Vector> vectors = RiptideNameHarvest.discoverVectors(connection);
      RiptideNameHarvest.Control control = new RiptideNameHarvest.Control() {
         @Override
         public boolean cancelled() {
            Minecraft m = Minecraft.getInstance();
            return !MacroExecutor.isCurrentRunActive() || m == null || m.getConnection() != connection;
         }

         @Override
         public boolean paused() {
            return false;
         }

         @Override
         public int limit() {
            return 60000;
         }

         @Override
         public int maxQueries() {
            return 100000;
         }
      };
      RiptideNameHarvest.sweep(connection, names, self, vectors, control);
      List<String> out = new ArrayList<>(names.values());
      out.sort(String.CASE_INSENSITIVE_ORDER);
      return out;
   }

   private static void runCaptureValueAction(Minecraft mc, CaptureValueAction action) {
      if (action != null) {
         String modifierError = action.numberModifierError();
         if (!modifierError.isBlank()) {
            setCurrentStatus("Capture failed: " + modifierError);
            RiptideNotifications.error("Capture Value: " + modifierError);
         } else if (action.source == CaptureValueAction.Source.COMMAND_AUTOFILL) {
            setCurrentStatus("Requesting command suggestions");
            runCaptureCommandAutofill(mc, action);
            reportMissingCapture(action);
         } else if (action.source == CaptureValueAction.Source.NAMESCRAPE) {
            setCurrentStatus("Scraping player names");
            runCaptureNamescrape(mc, action);
            reportMissingCapture(action);
         } else if (action.waitForTrigger) {
            CaptureValueAction.Source source = action.source == null ? CaptureValueAction.Source.GUI_TITLE : action.source;
            setCurrentStatus("Waiting for capture: " + source.name().toLowerCase(Locale.ROOT).replace('_', ' '));
            if (source == CaptureValueAction.Source.RECENT_CHAT) {
               awaitCaptureValueChat(action);
            } else if (source == CaptureValueAction.Source.SCOREBOARD) {
               awaitCaptureValueScoreboard(mc, action);
            } else {
               awaitCaptureValueState(mc, action);
            }
         } else {
            MacroExecutor.RunState run = CURRENT_RUN.get();
            AtomicReference<CaptureValueAction.Preview> result = new AtomicReference<>(CaptureValueAction.Preview.unavailable("Not available"));
            long deadline = System.nanoTime() + 300000000L;

            while (isCurrentRunActive()) {
               CompletableFuture<Void> done = new CompletableFuture<>();
               mc.execute(() -> withRunContext(run, () -> {
                  try {
                     result.set(action.run(mc));
                     done.complete(null);
                  } catch (Throwable var5x) {
                     done.completeExceptionally(var5x);
                  }
               }));
               waitForFutureDone(done, 0L);
               if (result.get().success() || System.nanoTime() >= deadline) {
                  break;
               }

               waitOneTick();
            }

            CaptureValueAction.Preview preview = result.get();
            if (!preview.success()) {
               setCurrentStatus("Capture failed: " + preview.message());
               RiptideNotifications.error("Capture " + captureOutputLabel(action) + ": " + preview.message());
            }
         }
      }
   }

   private static void reportMissingCapture(CaptureValueAction action) {
      String name = MacroVariableContext.cleanRootName(action.saveAs);
      if (!name.isBlank() && !MacroVariables.value(name).isPresent()) {
         setCurrentStatus("Capture failed: nothing captured");
         RiptideNotifications.error("Capture " + captureOutputLabel(action) + ": nothing captured");
      }
   }

   private static String captureOutputLabel(CaptureValueAction action) {
      String name = MacroVariableContext.cleanRootName(action.saveAs);
      return name.isBlank() ? "value" : "{" + name + "}";
   }

   private static void awaitCaptureValueState(Minecraft mc, CaptureValueAction action) {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      clearCaptureOutputs(action);

      for (String lastMessage = ""; isCurrentRunActive(); waitOneTick()) {
         CaptureValueAction.Preview preview;
         try {
            preview = callOnClientThread(mc, () -> action.previewCurrent(mc), CaptureValueAction.Preview.unavailable("Not available"));
         } catch (InterruptedException var6) {
            Thread.currentThread().interrupt();
            return;
         }

         if (preview.success()) {
            if (run != null) {
               run.variables.setAll(preview.values());
            } else {
               MacroVariables.setAll(preview.values());
            }

            return;
         }

         if (!preview.message().isBlank() && !preview.message().equals(lastMessage)) {
            lastMessage = preview.message();
            setCurrentStatus("Capture waiting: " + lastMessage);
         }
      }
   }

   private static void awaitCaptureValueScoreboard(Minecraft mc, CaptureValueAction action) {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      clearCaptureOutputs(action);
      String trackedKey = action.scoreboardRow;
      int trackedRow = action.scoreboardRowIndex;
      String trackedObjective = action.scoreboardObjective;
      String previousText = null;
      String lastMessage = "";

      for (boolean baselineReady = false; isCurrentRunActive(); waitOneTick()) {
         CaptureValueAction.ScoreboardLine line;
         try {
            String keySnapshot = trackedKey;
            int rowSnapshot = trackedRow;
            String objectiveSnapshot = trackedObjective;
            line = callOnClientThread(mc, () -> action.resolveScoreboardLine(mc, keySnapshot, rowSnapshot, objectiveSnapshot), null);
         } catch (InterruptedException var13) {
            Thread.currentThread().interrupt();
            return;
         }

         if (line != null) {
            String currentText = line.text() == null ? "" : line.text();
            if (!baselineReady) {
               trackedKey = line.key();
               trackedRow = line.row();
               trackedObjective = line.objective();
               previousText = currentText;
               baselineReady = true;
            } else if (!currentText.equals(previousText)) {
               CaptureValueAction.Preview preview = action.previewScoreboardLine(line);
               if (preview.success()) {
                  if (run != null) {
                     run.variables.setAll(preview.values());
                  } else {
                     MacroVariables.setAll(preview.values());
                  }

                  return;
               }

               if (!preview.message().isBlank() && !preview.message().equals(lastMessage)) {
                  lastMessage = preview.message();
                  setCurrentStatus("Capture waiting: " + lastMessage);
               }

               previousText = currentText;
            }
         }
      }
   }

   private static void waitForCondition(CompletableFuture<Void> future) {
      Thread t = Thread.currentThread();
      future.whenComplete((v, e) -> LockSupport.unpark(t));

      while (!future.isDone() && isCurrentRunActive()) {
         LockSupport.parkNanos(500000L);
         if (Thread.interrupted()) {
            break;
         }
      }

      if (!isCurrentRunActive()) {
         future.cancel(true);
      }
   }

   private static void runPacedTpAction(Minecraft mc, PacedTpAction action) throws InterruptedException {
      AtomicReference<PacketTeleportController.MacroHandle> handleRef = new AtomicReference<>();
      runOnClientThread(mc, () -> handleRef.set(PacketTeleportController.startMacro(action)));
      PacketTeleportController.MacroHandle handle = handleRef.get();
      if (handle != null) {
         setCurrentStatus("TP " + action.commandArguments());

         try {
            while (isCurrentRunActive() && !handle.completion().isDone()) {
               waitOneTick();
            }
         } finally {
            if (!handle.completion().isDone()) {
               mc.execute(() -> PacketTeleportController.cancelMacro(handle, "macro stopped"));
            }
         }
      }
   }

   private static void yieldToMainThread(Minecraft mc) {
      if (mc != null && !mc.isSameThread()) {
         int[] burst = INSTANT_BURST.get();
         if (++burst[0] >= 256) {
            burst[0] = 0;
            CompletableFuture<Void> prev = MAIN_DRAIN_BARRIER.get();
            if (prev != null && !prev.isDone()) {
               waitForCondition(prev);
            }

            if (isCurrentRunActive()) {
               CompletableFuture<Void> next = new CompletableFuture<>();
               MAIN_DRAIN_BARRIER.set(next);

               try {
                  mc.execute(() -> next.complete(null));
               } catch (Throwable var5) {
                  next.complete(null);
               }
            }
         }
      }
   }

   private static void waitForPredicate(BooleanSupplier predicate, int timeoutMs) {
      long start = System.currentTimeMillis();

      while (isCurrentRunActive()) {
         try {
            if (predicate.getAsBoolean()) {
               return;
            }
         } catch (Throwable var5) {
         }

         if (timeoutMs > 0 && System.currentTimeMillis() - start >= timeoutMs) {
            return;
         }

         CompletableFuture<Void> tick = MacroConditionRegistry.waitForNextTick();
         waitForCondition(tick);
      }
   }

   private static BooleanSupplier guiClosePredicate(WaitForGuiAction action, Minecraft mc) {
      AtomicBoolean sawMatchingGui = new AtomicBoolean(action.checkGui(mc));
      return () -> {
         Optional<Map<String, MacroValue>> capture = mc == null ? Optional.empty() : action.captureGui(mc.gui.screen());
         boolean matches = capture.isPresent();
         if (matches) {
            capture.ifPresent(values -> stageCapture(action, (Map<String, MacroValue>)values));
            sawMatchingGui.set(true);
            return false;
         } else {
            return sawMatchingGui.get();
         }
      };
   }

   private static BooleanSupplier guiClosePredicate(WaitGuiTypeAction action, Minecraft mc) {
      AtomicBoolean sawMatchingGui = new AtomicBoolean(action.matches(mc));
      return () -> {
         Optional<Map<String, MacroValue>> capture = action.capture(mc);
         boolean matches = capture.isPresent();
         if (matches) {
            capture.ifPresent(values -> stageCapture(action, (Map<String, MacroValue>)values));
            sawMatchingGui.set(true);
            return false;
         } else {
            return sawMatchingGui.get();
         }
      };
   }

   private static void waitForDurabilityUseNext(Minecraft mc, WaitDurabilityAction action) throws InterruptedException {
      AtomicReference<MacroExecutor.DurabilityUseNextSession> sessionRef = new AtomicReference<>();
      long start = System.currentTimeMillis();

      while (isCurrentRunActive()) {
         if (Boolean.TRUE.equals(callOnClientThread(mc, () -> tickDurabilityUseNext(mc, action, sessionRef), Boolean.FALSE))) {
            return;
         }

         if (action.timeoutMs > 0 && System.currentTimeMillis() - start >= action.timeoutMs) {
            return;
         }

         waitOneTick();
      }
   }

   private static boolean durabilityObservationMatches(Minecraft mc, WaitDurabilityAction action) {
      if (action != null && action.useNext) {
         MacroExecutor.DurabilityUseNextSession session = createDurabilityUseNextSession(mc, action);
         if (session == null || session.fallbackOnly) {
            return action.matches(mc);
         } else if (session.firstSlotPending) {
            ItemStack stack = stackForVisibleSlot(mc, session.activeSlot);
            if (session.lockedRegistryId.isBlank() && canLockDurabilityInitialStack(action, session, stack)) {
               session.lockedRegistryId = stackRegistryId(stack);
            }

            return session.lockedRegistryId.isBlank()
               ? false
               : isSameDamageableItem(stack, session.lockedRegistryId)
                  && action.matchesStack(stack)
                  && findDurabilityReplacement(mc, action, session, session.activeSlot) == null;
         } else {
            return session.lockedRegistryId.isBlank() ? action.matches(mc) : findDurabilityReplacement(mc, action, session, -1) == null;
         }
      } else {
         return action != null && action.matches(mc);
      }
   }

   private static boolean tickDurabilityUseNext(Minecraft mc, WaitDurabilityAction action, AtomicReference<MacroExecutor.DurabilityUseNextSession> sessionRef) {
      if (mc != null && mc.player != null && action != null) {
         MacroExecutor.DurabilityUseNextSession session = sessionRef.get();
         if (session == null) {
            session = createDurabilityUseNextSession(mc, action);
            sessionRef.set(session);
         }

         if (session == null || session.fallbackOnly) {
            return action.matches(mc);
         } else if (session.firstSlotPending) {
            return tickDurabilityInitialSlot(mc, action, session);
         } else {
            adoptCurrentSameItem(mc, session);
            ItemStack active = stackForVisibleSlot(mc, session.activeSlot);
            return isSameDamageableItem(active, session.lockedRegistryId) && !action.matchesStack(active)
               ? false
               : switchToNextDurabilityItemOrComplete(mc, action, session, session.activeSlot);
         }
      } else {
         return false;
      }
   }

   private static boolean tickDurabilityInitialSlot(Minecraft mc, WaitDurabilityAction action, MacroExecutor.DurabilityUseNextSession session) {
      ItemStack stack = stackForVisibleSlot(mc, session.activeSlot);
      if (session.lockedRegistryId.isBlank() && canLockDurabilityInitialStack(action, session, stack)) {
         session.lockedRegistryId = stackRegistryId(stack);
      }

      if (!session.lockedRegistryId.isBlank() && isSameDamageableItem(stack, session.lockedRegistryId)) {
         session.observedInitialSlot = true;
         if (!action.matchesStack(stack)) {
            return false;
         } else {
            session.firstSlotPending = false;
            return switchToNextDurabilityItemOrComplete(mc, action, session, session.activeSlot);
         }
      } else if (session.observedInitialSlot) {
         session.firstSlotPending = false;
         return switchToNextDurabilityItemOrComplete(mc, action, session, session.activeSlot);
      } else {
         return false;
      }
   }

   private static boolean switchToNextDurabilityItemOrComplete(
      Minecraft mc, WaitDurabilityAction action, MacroExecutor.DurabilityUseNextSession session, int excludeSlot
   ) {
      MacroExecutor.DurabilityCandidate replacement = findDurabilityReplacement(mc, action, session, excludeSlot);
      if (replacement == null) {
         return true;
      } else {
         return !switchToDurabilityReplacement(mc, session, replacement) ? false : false;
      }
   }

   private static MacroExecutor.DurabilityUseNextSession createDurabilityUseNextSession(Minecraft mc, WaitDurabilityAction action) {
      if (mc != null && mc.player != null && action != null) {
         MacroExecutor.DurabilityUseNextSession session = new MacroExecutor.DurabilityUseNextSession();
         session.preferredHotbarSlot = Math.max(0, Math.min(8, mc.player.getInventory().getSelectedSlot()));
         ItemTarget target = action.targetMode == WaitDurabilityAction.TargetMode.ITEM ? ItemTarget.fromLegacyEntry(action.itemName) : new ItemTarget();
         session.initialTarget = target;
         switch (action.targetMode) {
            case SLOT:
               session.activeSlot = Math.max(0, Math.min(40, action.slot));
               session.firstSlotPending = true;
               ItemStack stack = stackForVisibleSlot(mc, session.activeSlot);
               if (canLockDurabilityInitialStack(action, session, stack)) {
                  session.lockedRegistryId = stackRegistryId(stack);
                  session.observedInitialSlot = true;
               }
               break;
            case HELD:
               initializeHeldDurabilitySession(mc, action, session);
               break;
            case ITEM:
               initializeItemDurabilitySession(mc, action, session, target);
         }

         if (session.lockedRegistryId == null) {
            session.lockedRegistryId = "";
         }

         if (session.lockedRegistryId.isBlank() && !session.firstSlotPending) {
            session.fallbackOnly = true;
         }

         return session;
      } else {
         return null;
      }
   }

   private static void initializeHeldDurabilitySession(Minecraft mc, WaitDurabilityAction action, MacroExecutor.DurabilityUseNextSession session) {
      int selected = Math.max(0, Math.min(8, mc.player.getInventory().getSelectedSlot()));
      ItemStack main = mc.player.getInventory().getItem(selected);
      if (main != null && !main.isEmpty() && main.isDamageableItem()) {
         session.activeSlot = selected;
         session.lockedRegistryId = stackRegistryId(main);
      } else {
         ItemStack offhand = mc.player.getOffhandItem();
         if (offhand != null && !offhand.isEmpty() && offhand.isDamageableItem()) {
            session.activeSlot = 40;
            session.lockedRegistryId = stackRegistryId(offhand);
         } else {
            session.fallbackOnly = true;
         }
      }
   }

   private static void initializeItemDurabilitySession(
      Minecraft mc, WaitDurabilityAction action, MacroExecutor.DurabilityUseNextSession session, ItemTarget target
   ) {
      if (target != null && target.hasSlot()) {
         session.activeSlot = Math.max(0, Math.min(40, target.slot));
         session.firstSlotPending = true;
         String explicitId = target.hasRegistryId() ? canonicalItemRegistryId(target.registryId) : "";
         if (!explicitId.isBlank()) {
            session.lockedRegistryId = explicitId;
         }

         ItemStack stack = stackForVisibleSlot(mc, session.activeSlot);
         if (canLockDurabilityInitialStack(action, session, stack)) {
            session.lockedRegistryId = stackRegistryId(stack);
            session.observedInitialSlot = true;
         }
      } else if (target != null && target.hasRegistryId()) {
         session.lockedRegistryId = canonicalItemRegistryId(target.registryId);
         session.activeSlot = findCurrentUsableDurabilitySlot(mc, action, session.lockedRegistryId, target);
      } else if (target != null && target.hasIdentity()) {
         int slot = findInitialDurabilitySlot(mc, action, "", target);
         if (slot >= 0) {
            session.activeSlot = slot;
            session.lockedRegistryId = stackRegistryId(stackForVisibleSlot(mc, slot));
         } else {
            session.fallbackOnly = true;
         }
      } else {
         initializeHeldDurabilitySession(mc, action, session);
      }
   }

   private static int findCurrentUsableDurabilitySlot(Minecraft mc, WaitDurabilityAction action, String lockedRegistryId, ItemTarget target) {
      if (mc != null && mc.player != null) {
         int selected = Math.max(0, Math.min(8, mc.player.getInventory().getSelectedSlot()));
         if (initialDurabilitySlotMatches(mc, action, lockedRegistryId, target, selected)) {
            return selected;
         } else {
            return initialDurabilitySlotMatches(mc, action, lockedRegistryId, target, 40) ? 40 : -1;
         }
      } else {
         return -1;
      }
   }

   private static boolean canLockDurabilityInitialStack(WaitDurabilityAction action, MacroExecutor.DurabilityUseNextSession session, ItemStack stack) {
      if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) {
         return false;
      } else {
         return action != null
               && action.targetMode == WaitDurabilityAction.TargetMode.ITEM
               && session != null
               && session.initialTarget != null
               && session.initialTarget.hasIdentity()
            ? session.initialTarget.score(stack, session.activeSlot) >= 0
            : true;
      }
   }

   private static int findInitialDurabilitySlot(Minecraft mc, WaitDurabilityAction action, String lockedRegistryId, ItemTarget target) {
      if (mc != null && mc.player != null) {
         int selected = Math.max(0, Math.min(8, mc.player.getInventory().getSelectedSlot()));
         if (initialDurabilitySlotMatches(mc, action, lockedRegistryId, target, selected)) {
            return selected;
         } else {
            for (int slot = 0; slot < 9; slot++) {
               if (slot != selected && initialDurabilitySlotMatches(mc, action, lockedRegistryId, target, slot)) {
                  return slot;
               }
            }

            for (int slotx = 9; slotx < Math.min(36, mc.player.getInventory().getContainerSize()); slotx++) {
               if (initialDurabilitySlotMatches(mc, action, lockedRegistryId, target, slotx)) {
                  return slotx;
               }
            }

            return initialDurabilitySlotMatches(mc, action, lockedRegistryId, target, 40) ? 40 : -1;
         }
      } else {
         return -1;
      }
   }

   private static boolean initialDurabilitySlotMatches(Minecraft mc, WaitDurabilityAction action, String lockedRegistryId, ItemTarget target, int slot) {
      ItemStack stack = stackForVisibleSlot(mc, slot);
      if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) {
         return false;
      } else {
         return lockedRegistryId != null && !lockedRegistryId.isBlank() && !isSameDamageableItem(stack, lockedRegistryId)
            ? false
            : target == null || !target.hasIdentity() || target.score(stack, slot) >= 0;
      }
   }

   private static void adoptCurrentSameItem(Minecraft mc, MacroExecutor.DurabilityUseNextSession session) {
      if (mc != null && mc.player != null && session != null && !session.lockedRegistryId.isBlank()) {
         int selected = Math.max(0, Math.min(8, mc.player.getInventory().getSelectedSlot()));
         if (isSameDamageableItem(mc.player.getInventory().getItem(selected), session.lockedRegistryId)) {
            session.activeSlot = selected;
            session.preferredHotbarSlot = selected;
         } else {
            if (isSameDamageableItem(mc.player.getOffhandItem(), session.lockedRegistryId)) {
               ItemStack active = stackForVisibleSlot(mc, session.activeSlot);
               if (!isSameDamageableItem(active, session.lockedRegistryId)) {
                  session.activeSlot = 40;
               }
            }
         }
      }
   }

   private static MacroExecutor.DurabilityCandidate findDurabilityReplacement(
      Minecraft mc, WaitDurabilityAction action, MacroExecutor.DurabilityUseNextSession session, int excludeSlot
   ) {
      if (mc != null && mc.player != null && action != null && session != null && !session.lockedRegistryId.isBlank()) {
         MacroExecutor.DurabilityCandidate hotbar = null;
         MacroExecutor.DurabilityCandidate inventory = null;
         MacroExecutor.DurabilityCandidate offhand = null;

         for (int slot = 0; slot < 9; slot++) {
            MacroExecutor.DurabilityCandidate candidate = durabilityCandidate(mc, action, session.lockedRegistryId, slot, excludeSlot);
            if (candidate != null && (hotbar == null || candidate.score() > hotbar.score())) {
               hotbar = candidate;
            }
         }

         int size = Math.min(36, mc.player.getInventory().getContainerSize());

         for (int slotx = 9; slotx < size; slotx++) {
            MacroExecutor.DurabilityCandidate candidate = durabilityCandidate(mc, action, session.lockedRegistryId, slotx, excludeSlot);
            if (candidate != null && (inventory == null || candidate.score() > inventory.score())) {
               inventory = candidate;
            }
         }

         offhand = durabilityCandidate(mc, action, session.lockedRegistryId, 40, excludeSlot);
         if (hotbar != null) {
            return hotbar;
         } else {
            return inventory != null ? inventory : offhand;
         }
      } else {
         return null;
      }
   }

   private static MacroExecutor.DurabilityCandidate durabilityCandidate(Minecraft mc, WaitDurabilityAction action, String registryId, int slot, int excludeSlot) {
      if (slot == excludeSlot) {
         return null;
      } else {
         ItemStack stack = stackForVisibleSlot(mc, slot);
         if (!isSameDamageableItem(stack, registryId)) {
            return null;
         } else {
            return action.matchesStack(stack) ? null : new MacroExecutor.DurabilityCandidate(slot, remainingDurability(stack));
         }
      }
   }

   private static boolean switchToDurabilityReplacement(
      Minecraft mc, MacroExecutor.DurabilityUseNextSession session, MacroExecutor.DurabilityCandidate replacement
   ) {
      if (mc != null && mc.player != null && replacement != null) {
         int slot = replacement.slot();
         if (slot >= 0 && slot < 9) {
            RiptideInventoryHelper.selectHotbarSlot(mc, slot);
            session.activeSlot = slot;
            session.preferredHotbarSlot = slot;
            return true;
         } else if (slot >= 9 && slot < 36) {
            int target = Math.max(0, Math.min(8, session.preferredHotbarSlot));
            if (!RiptideInventoryHelper.swapInventorySlots(mc, slot, target)) {
               return false;
            } else {
               RiptideInventoryHelper.selectHotbarSlot(mc, target);
               session.activeSlot = target;
               session.preferredHotbarSlot = target;
               return true;
            }
         } else if (slot == 40) {
            session.activeSlot = 40;
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static ItemStack stackForVisibleSlot(Minecraft mc, int visibleSlot) {
      if (mc == null || mc.player == null) {
         return ItemStack.EMPTY;
      } else if (visibleSlot == 40) {
         return mc.player.getOffhandItem();
      } else {
         return visibleSlot >= 0 && visibleSlot < mc.player.getInventory().getContainerSize() ? mc.player.getInventory().getItem(visibleSlot) : ItemStack.EMPTY;
      }
   }

   private static boolean isSameDamageableItem(ItemStack stack, String registryId) {
      return stack != null && !stack.isEmpty() && stack.isDamageableItem() && stackRegistryId(stack).equals(registryId);
   }

   private static int remainingDurability(ItemStack stack) {
      return stack != null && stack.isDamageableItem() ? Math.max(0, stack.getMaxDamage() - stack.getDamageValue()) : 0;
   }

   private static String stackRegistryId(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
         return id == null ? "" : id.toString();
      } else {
         return "";
      }
   }

   private static String canonicalItemRegistryId(String raw) {
      if (raw != null && !raw.isBlank()) {
         String trimmed = raw.trim().toLowerCase(Locale.ROOT);
         Identifier parsed = Identifier.tryParse(trimmed.contains(":") ? trimmed : "minecraft:" + trimmed);
         return parsed == null ? "" : BuiltInRegistries.ITEM.getOptional(parsed).map(item -> BuiltInRegistries.ITEM.getKey(item).toString()).orElse("");
      } else {
         return "";
      }
   }

   private static CompletableFuture<Void> predicateFuture(BooleanSupplier predicate, int timeoutMs) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      MacroExecutor.RunState run = CURRENT_RUN.get();
      Thread worker = new Thread(() -> withRunContext(run, () -> {
         long start = System.currentTimeMillis();

         while (!future.isDone() && isCurrentRunActive()) {
            try {
               if (predicate.getAsBoolean()) {
                  future.complete(null);
                  return;
               }
            } catch (Throwable var6) {
            }

            if (timeoutMs > 0 && System.currentTimeMillis() - start >= timeoutMs) {
               future.complete(null);
               return;
            }

            LockSupport.parkNanos(500000L);
            if (Thread.interrupted()) {
               return;
            }
         }
      }), "MacroPredicateWait");
      worker.setDaemon(true);
      worker.start();
      future.whenComplete((v, e) -> worker.interrupt());
      return future;
   }

   private static void prearmNextActionWait(Minecraft mc, List<MacroAction> actions, int currentIndex) {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      if (run != null && actions != null) {
         int nextIndex = currentIndex + 1;
         if (nextIndex >= 0 && nextIndex < actions.size()) {
            MacroAction next = actions.get(nextIndex);
            if (next != null && next.isEnabled() && next.listensDuringPreviousAction()) {
               if (MacroDynamicBindings.apply(next, mc)) {
                  if (dynamicTextInputsAvailable(next, mc)) {
                     if (isWaitConditionAction(next)) {
                        synchronized (run.prearmedWaits) {
                           if (!run.prearmedWaits.containsKey(next)) {
                              clearCaptureOutputs(next);
                              clearOwnCaptureOutput(next);
                              MacroExecutor.PrearmedWait wait = createPrearmedWait(mc, next);
                              if (wait != null) {
                                 run.prearmedWaits.put(next, wait);
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

   private static boolean isWaitConditionAction(MacroAction action) {
      return action instanceof WaitForPacketAction
         || action instanceof WaitPacketMatchAction
         || action instanceof WaitForChatAction
         || action instanceof WaitForGuiAction
         || action instanceof WaitGuiTypeAction
         || action instanceof WaitForSoundAction
         || action instanceof WaitForSlotChangeAction
         || action instanceof WaitInventoryPredicateAction
         || action instanceof WaitDurabilityAction
         || action instanceof WaitFreeSlotsAction
         || action instanceof WaitForHealthAction
         || action instanceof WaitForBlockAction
         || action instanceof WaitForEntityAction
         || action instanceof WaitEntityTargetAction
         || action instanceof WaitForCooldownAction
         || action instanceof WaitPosAction
         || action instanceof WaitForPositionDeltaAction
         || action instanceof WaitForTeleportAction
         || action instanceof WaitGamemodeChangeAction
         || action instanceof WaitForWorldChangeAction
         || action instanceof WaitMovementAction
         || action instanceof WaitForLanStepAction
         || action instanceof WaitForMacroStepAction;
   }

   private static MacroExecutor.PrearmedWait consumePrearmedWait(MacroAction action) {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      if (run != null && action != null) {
         synchronized (run.prearmedWaits) {
            return run.prearmedWaits.remove(action);
         }
      } else {
         return null;
      }
   }

   private static boolean waitForPrearmed(MacroAction action, long timeoutMs) {
      MacroExecutor.PrearmedWait wait = consumePrearmedWait(action);
      if (wait == null) {
         return false;
      } else {
         boolean var4;
         try {
            waitForFutureDone(wait.future, timeoutMs);
            commitStagedCapture(action);
            var4 = true;
         } finally {
            wait.cleanup();
         }

         return var4;
      }
   }

   private static MacroExecutor.PrearmedWait createPrearmedWait(Minecraft mc, MacroAction action) {
      if (action instanceof WaitForPacketAction packetAction) {
         List<String> targets = packetAction.effectiveList();
         String firstTarget = targets.isEmpty() ? "" : targets.get(0);
         return packetTargetFuture(packetAction, firstTarget, 1);
      } else if (action instanceof WaitPacketMatchAction packetMatch) {
         return packetMatchFuture(packetMatch);
      } else if (action instanceof WaitInventoryPredicateAction inv) {
         inv.resetBaseline();
         return predicatePrearm(action, () -> inv.matches(mc), inv.timeoutMs);
      } else if (action instanceof WaitDurabilityAction durability) {
         return durability.useNext ? null : predicatePrearm(action, () -> durabilityObservationMatches(mc, durability), durability.timeoutMs);
      } else if (action instanceof WaitFreeSlotsAction freeSlots) {
         return predicatePrearm(action, () -> freeSlots.matches(mc), freeSlots.timeoutMs);
      } else if (action instanceof WaitEntityTargetAction entityTarget) {
         return predicatePrearm(action, () -> entityTarget.matches(mc), entityTarget.timeoutMs);
      } else if (action instanceof WaitGuiTypeAction guiType) {
         return predicatePrearm(action, guiType.waitMode == WaitGuiTypeAction.WaitMode.CLOSE ? guiClosePredicate(guiType, mc) : () -> {
            Optional<Map<String, MacroValue>> capture = guiType.capture(mc);
            capture.ifPresent(values -> stageCapture(guiType, (Map<String, MacroValue>)values));
            return capture.isPresent();
         }, guiType.timeoutMs);
      } else if (action instanceof WaitForChatAction chat) {
         MacroExecutor.RunState run = CURRENT_RUN.get();
         MacroExecutor.OneShotPacketListener listener = awaitReceive(packet -> {
            MacroExecutor.ChatCapture capture = extractIncomingChat(packet);
            Optional<Map<String, MacroValue>> matched = captureChatAction(chat, capture, run == null ? null : run.variables);
            if (matched.isEmpty()) {
               return false;
            } else {
               if (run != null) {
                  run.stagedCaptures.put(chat, matched.get());
               }

               return true;
            }
         });
         CompletableFuture<?> future = listener.future;
         if (chat.timeoutMs > 0) {
            future.completeOnTimeout(null, chat.timeoutMs, TimeUnit.MILLISECONDS);
         }

         return new MacroExecutor.PrearmedWait(action, future, listener::cancel, 0);
      } else if (action instanceof WaitForMacroStepAction macroStep) {
         return predicatePrearm(action, () -> isMacroStepSatisfied(macroStep), macroStep.timeoutMs);
      } else if (action instanceof WaitForLanStepAction lanStep) {
         CompletableFuture<Void> future = MacroConditionRegistry.waitForLanStep(lanStep);
         return new MacroExecutor.PrearmedWait(action, future, () -> {
            if (!future.isDone()) {
               future.cancel(true);
            }
         }, 0);
      } else {
         CompletableFuture<?> future = raceConditionFuture(mc, action);
         if (future == null) {
            return null;
         } else {
            int timeout = action instanceof WaitForChatAction chat ? chat.timeoutMs : 0;
            if (timeout > 0) {
               future.completeOnTimeout(null, timeout, TimeUnit.MILLISECONDS);
            }

            return new MacroExecutor.PrearmedWait(action, future, () -> {
               if (!future.isDone()) {
                  future.cancel(true);
               }
            }, 0);
         }
      }
   }

   private static boolean hasPrearmedWait(MacroAction action) {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      if (run != null && action != null) {
         synchronized (run.prearmedWaits) {
            return run.prearmedWaits.containsKey(action);
         }
      } else {
         return false;
      }
   }

   private static void recordMatchedPacket(MacroAction action, Packet<?> packet, String direction) {
      if (action instanceof WaitForPacketAction wait) {
         wait.matchedPacket = packet;
         wait.matchedDirection = direction == null ? "" : direction;
      } else if (action instanceof WaitPacketMatchAction wait) {
         wait.matchedPacket = packet;
         wait.matchedDirection = direction == null ? "" : direction;
      }
   }

   private static void copyLastMatchedPacket(WaitForPacketAction action) {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      if (action != null && run != null && run.lastMatchedPacket != null) {
         action.matchedPacket = run.lastMatchedPacket;
         action.matchedDirection = run.lastMatchedPacketDirection;
      }
   }

   private static void clearCaptureOutputs(MacroAction action) {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      if (run != null && action != null) {
         run.stagedCaptures.remove(action);
         if (action instanceof WaitForChatAction chat) {
            for (String name : MacroCapturePattern.declaredNames(chat.effectiveMatchMode(), chat.pattern)) {
               run.variables.remove(name);
            }

            if (chat.saveAs != null && !chat.saveAs.isBlank()) {
               run.variables.remove(chat.saveAs);
            }
         } else if (action instanceof WaitForGuiAction gui) {
            for (String name : MacroCapturePattern.declaredNames(gui.matchMode, gui.guiTitle)) {
               run.variables.remove(name);
            }

            if (gui.saveAs != null && !gui.saveAs.isBlank()) {
               run.variables.remove(gui.saveAs);
            }
         } else if (action instanceof WaitGuiTypeAction gui) {
            for (String name : MacroCapturePattern.declaredNames(gui.matchMode, gui.title)) {
               run.variables.remove(name);
            }

            if (gui.saveAs != null && !gui.saveAs.isBlank()) {
               run.variables.remove(gui.saveAs);
            }
         } else if (action instanceof CaptureValueAction capture) {
            for (String name : MacroCapturePattern.declaredNames(capture.matchMode, capture.pattern)) {
               run.variables.remove(name);
            }

            if (capture.saveAs != null && !capture.saveAs.isBlank()) {
               run.variables.remove(capture.saveAs);
            }
         }
      }
   }

   private static void clearOwnCaptureOutput(MacroAction action) {
      if (action instanceof MacroCaptureOutput output) {
         String name = output.getSaveAs();
         if (name != null && !name.isBlank()) {
            MacroVariables.remove(name);
         }

         if (action instanceof WaitForSoundAction sound) {
            sound.matchedSoundId = "";
         }

         if (action instanceof WaitForPacketAction packet) {
            packet.matchedPacket = null;
            packet.matchedDirection = "";
         }

         if (action instanceof WaitPacketMatchAction packet) {
            packet.matchedPacket = null;
            packet.matchedDirection = "";
         }
      }
   }

   private static void commitConditionOutput(MacroAction action, Minecraft mc) throws InterruptedException {
      if (action instanceof MacroCaptureOutput output) {
         String name = MacroVariableContext.cleanRootName(output.getSaveAs());
         if (!name.isEmpty()) {
            MacroValue value = callOnClientThread(mc, () -> conditionOutputValue(action, mc), null);
            if (value != null) {
               MacroVariables.setValue(name, value);
            }
         }
      }
   }

   private static MacroValue conditionOutputValue(MacroAction action, Minecraft mc) {
      if (action instanceof WaitForHealthAction healthAction && mc != null && mc.player != null) {
         float health = mc.player.getHealth();
         return (healthAction.below ? !(health >= healthAction.healthThreshold) : !(health <= healthAction.healthThreshold))
            ? MacroValue.structured(
               MacroValue.Kind.NUMBER,
               Float.toString(health),
               Map.of(
                  "health",
                  MacroValue.number(health),
                  "max",
                  MacroValue.number(mc.player.getMaxHealth()),
                  "absorption",
                  MacroValue.number(mc.player.getAbsorptionAmount())
               )
            )
            : null;
      } else if (action instanceof WaitForEntityAction entityAction) {
         Entity entity = entityAction.findMatchingEntity(mc);
         if (entity == null) {
            return null;
         } else {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            double distance = mc != null && mc.player != null ? mc.player.distanceTo(entity) : 0.0;
            return MacroValue.structured(
               MacroValue.Kind.ENTITY,
               entity.getName().getString(),
               Map.of(
                  "name",
                  MacroValue.text(entity.getName().getString()),
                  "id",
                  MacroValue.identifier(id),
                  "uuid",
                  MacroValue.text(entity.getStringUUID()),
                  "x",
                  MacroValue.number(entity.getX()),
                  "y",
                  MacroValue.number(entity.getY()),
                  "z",
                  MacroValue.number(entity.getZ()),
                  "distance",
                  MacroValue.number(distance)
               )
            );
         }
      } else if (action instanceof WaitForSoundAction sound) {
         return sound.matchedSoundId != null && !sound.matchedSoundId.isBlank()
            ? MacroValue.structured(
               MacroValue.Kind.IDENTIFIER,
               sound.matchedSoundId,
               Map.of(
                  "id",
                  MacroValue.identifier(sound.matchedSoundId),
                  "x",
                  MacroValue.number(sound.matchedX),
                  "y",
                  MacroValue.number(sound.matchedY),
                  "z",
                  MacroValue.number(sound.matchedZ)
               )
            )
            : null;
      } else if (action instanceof WaitForPacketAction packet) {
         return packetMacroValue(packet.matchedPacket, packet.matchedDirection);
      } else if (action instanceof WaitPacketMatchAction packet) {
         return packetMacroValue(packet.matchedPacket, packet.matchedDirection);
      } else if (action instanceof WaitInventoryPredicateAction inventory) {
         return !inventory.matches(mc) ? null : inventoryMacroValue(mc, inventory.itemName, inventory.slot, inventory.condition.name());
      } else if (action instanceof WaitForSlotChangeAction slotWait) {
         if (slotWait.entries.isEmpty()) {
            return inventoryMacroValue(mc, "", -1, "slot_change");
         } else {
            ItemTarget target = slotWait.entries.get(0).resolvedTarget().resolveTemplate(mc);
            return target == null ? null : itemTargetMacroValue(mc, target);
         }
      } else if (action instanceof WaitPosAction || action instanceof WaitForTeleportAction || action instanceof WaitForPositionDeltaAction) {
         return playerPositionMacroValue(mc);
      } else if (action instanceof WaitForWorldChangeAction && mc != null && mc.level != null) {
         String dimension = mc.level.dimension().identifier().toString();
         return MacroValue.structured(MacroValue.Kind.IDENTIFIER, dimension, Map.of("id", MacroValue.identifier(dimension), "name", MacroValue.text(dimension)));
      } else if (action instanceof WaitGamemodeChangeAction && mc != null && mc.gameMode != null) {
         String mode = mc.gameMode.getPlayerMode().getName();
         WaitGamemodeChangeAction wait = (WaitGamemodeChangeAction)action;
         return wait.match == WaitGamemodeChangeAction.Match.TO_MODE && !wait.accepts(mc.gameMode.getPlayerMode())
            ? null
            : MacroValue.structured(MacroValue.Kind.TEXT, mode, Map.of("name", MacroValue.text(mode)));
      } else {
         return null;
      }
   }

   private static MacroValue playerPositionMacroValue(Minecraft mc) {
      if (mc != null && mc.player != null) {
         String display = String.format(Locale.ROOT, "%.3f, %.3f, %.3f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
         return MacroValue.structured(
            MacroValue.Kind.POSITION,
            display,
            Map.of(
               "x",
               MacroValue.number(mc.player.getX()),
               "y",
               MacroValue.number(mc.player.getY()),
               "z",
               MacroValue.number(mc.player.getZ()),
               "yaw",
               MacroValue.number(mc.player.getYRot()),
               "pitch",
               MacroValue.number(mc.player.getXRot())
            )
         );
      } else {
         return null;
      }
   }

   private static MacroValue inventoryMacroValue(Minecraft mc, String itemTemplate, int slot, String condition) {
      if (mc != null && mc.player != null) {
         MacroTemplate.Resolution item = MacroVariables.resolve(itemTemplate, mc);
         if (!item.success()) {
            return null;
         } else {
            ItemTarget target = ItemTarget.fromLegacyEntry(item.value());
            if (slot >= 0) {
               int safeSlot = Math.max(0, Math.min(mc.player.getInventory().getContainerSize() - 1, slot));
               MacroValue stack = stackMacroValue(mc.player.getInventory().getItem(safeSlot), safeSlot);
               return stack == null
                  ? MacroValue.structured(
                     MacroValue.Kind.SLOT,
                     Integer.toString(slot),
                     Map.of("slot", MacroValue.slot(slot), "empty", MacroValue.text("true"), "condition", MacroValue.text(condition))
                  )
                  : stack;
            } else {
               if (target.hasIdentity()) {
                  MacroValue matched = itemTargetMacroValue(mc, target);
                  if (matched != null) {
                     return matched.withProperty("condition", MacroValue.text(condition));
                  }
               }

               return MacroValue.structured(
                  MacroValue.Kind.TEXT,
                  condition,
                  Map.of("condition", MacroValue.text(condition), "selected_slot", MacroValue.slot(mc.player.getInventory().getSelectedSlot()))
               );
            }
         }
      } else {
         return null;
      }
   }

   private static MacroValue itemTargetMacroValue(Minecraft mc, ItemTarget target) {
      if (mc != null && mc.player != null && target != null) {
         if (mc.player.containerMenu != null) {
            for (Slot slot : mc.player.containerMenu.slots) {
               if (slot != null && !slot.getItem().isEmpty()) {
                  int visible = RiptideInventoryHelper.toUserVisibleSlot(mc, slot.index);
                  if (target.score(slot.getItem(), visible) >= 0) {
                     return stackMacroValue(slot.getItem(), visible);
                  }
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static MacroValue stackMacroValue(ItemStack stack, int slot) {
      if (stack != null && !stack.isEmpty()) {
         Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
         String itemId = id == null ? "" : id.toString();
         String name = stack.getHoverName().getString();
         return MacroValue.structured(
            MacroValue.Kind.ITEM,
            name,
            Map.of(
               "name", MacroValue.text(name), "id", MacroValue.identifier(itemId), "count", MacroValue.number(stack.getCount()), "slot", MacroValue.slot(slot)
            )
         );
      } else {
         return null;
      }
   }

   private static MacroValue packetMacroValue(Packet<?> packet, String direction) {
      if (packet == null) {
         return null;
      } else {
         LinkedHashMap<String, MacroValue> properties = new LinkedHashMap<>();
         String packetName = RiptidePacketNamer.getFriendlyName(packet, direction);
         properties.put("name", MacroValue.text(packetName));
         properties.put("class", MacroValue.text(packet.getClass().getName()));
         properties.put("direction", MacroValue.text(direction));
         RecordComponent[] components = packet.getClass().getRecordComponents();
         if (components != null) {
            for (RecordComponent component : components) {
               if (properties.size() >= 64) {
                  break;
               }

               try {
                  Object value = component.getAccessor().invoke(packet);
                  properties.put(component.getName().toLowerCase(Locale.ROOT), MacroValue.text(value));
               } catch (ReflectiveOperationException var10) {
               }
            }
         }

         return MacroValue.structured(MacroValue.Kind.PACKET, packetName, properties);
      }
   }

   private static void stageCapture(MacroAction action, Map<String, MacroValue> values) {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      if (run != null && action != null && values != null) {
         run.stagedCaptures.put(action, Map.copyOf(values));
      }
   }

   private static void commitStagedCapture(MacroAction action) {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      if (run != null && action != null) {
         Map<String, MacroValue> values = run.stagedCaptures.remove(action);
         if (values != null) {
            run.variables.setAll(values);
         }
      }
   }

   private static MacroExecutor.PrearmedWait predicatePrearm(MacroAction action, BooleanSupplier predicate, int timeoutMs) {
      AtomicBoolean active = new AtomicBoolean(true);
      CompletableFuture<Void> future = new CompletableFuture<>();
      MacroExecutor.RunState run = CURRENT_RUN.get();
      Thread worker = new Thread(() -> withRunContext(run, () -> {
         long start = System.currentTimeMillis();

         while (active.get() && isCurrentRunActive() && !future.isDone()) {
            try {
               if (predicate.getAsBoolean()) {
                  future.complete(null);
                  return;
               }
            } catch (Throwable var7) {
            }

            if (timeoutMs > 0 && System.currentTimeMillis() - start >= timeoutMs) {
               future.complete(null);
               return;
            }

            LockSupport.parkNanos(500000L);
            if (Thread.interrupted()) {
               return;
            }
         }
      }), "MacroPrearmedWait");
      worker.setDaemon(true);
      worker.start();
      return new MacroExecutor.PrearmedWait(action, future, () -> {
         active.set(false);
         if (!future.isDone()) {
            future.cancel(true);
         }

         worker.interrupt();
      }, 0);
   }

   private static MacroExecutor.PrearmedWait packetTargetFuture(MacroAction action, String target, int completedPacketTargets) {
      String normalizedTarget = WaitForPacketAction.normalizeTarget(target);
      String direction = WaitForPacketAction.getDirection(normalizedTarget);
      CompletableFuture<Packet<?>> matched = new CompletableFuture<>();
      MacroExecutor.OneShotPacketListener send = null;
      MacroExecutor.OneShotPacketListener recv = null;
      if (!"S2C".equalsIgnoreCase(direction)) {
         send = awaitSend(packet -> matchesPacketTarget(normalizedTarget, packet, "C2S"));
         send.future.thenAccept(packet -> {
            recordMatchedPacket(action, (Packet<?>)packet, "C2S");
            matched.complete((Packet<?>)packet);
         });
      }

      if (!"C2S".equalsIgnoreCase(direction)) {
         recv = awaitReceive(packet -> matchesPacketTarget(normalizedTarget, packet, "S2C"));
         recv.future.thenAccept(packet -> {
            recordMatchedPacket(action, (Packet<?>)packet, "S2C");
            matched.complete((Packet<?>)packet);
         });
      }

      MacroExecutor.OneShotPacketListener finalSend = send;
      MacroExecutor.OneShotPacketListener finalRecv = recv;
      return new MacroExecutor.PrearmedWait(action, matched, () -> {
         if (finalSend != null) {
            finalSend.cancel();
         }

         if (finalRecv != null) {
            finalRecv.cancel();
         }

         if (!matched.isDone()) {
            matched.cancel(true);
         }
      }, completedPacketTargets);
   }

   private static MacroExecutor.PrearmedWait packetMatchFuture(WaitPacketMatchAction action) {
      MacroExecutor.OneShotPacketListener send = null;
      MacroExecutor.OneShotPacketListener recv = null;
      CompletableFuture<Packet<?>> matched = new CompletableFuture<>();
      if (action.direction != WaitPacketMatchAction.Direction.S2C) {
         send = awaitSend(packet -> action.matches(packet, "C2S"));
         send.future.thenAccept(packet -> {
            recordMatchedPacket(action, (Packet<?>)packet, "C2S");
            matched.complete((Packet<?>)packet);
         });
      }

      if (action.direction != WaitPacketMatchAction.Direction.C2S) {
         recv = awaitReceive(packet -> action.matches(packet, "S2C"));
         recv.future.thenAccept(packet -> {
            recordMatchedPacket(action, (Packet<?>)packet, "S2C");
            matched.complete((Packet<?>)packet);
         });
      }

      if (send == null && recv == null) {
         return null;
      } else {
         if (action.timeoutMs > 0) {
            matched.completeOnTimeout(null, action.timeoutMs, TimeUnit.MILLISECONDS);
         }

         MacroExecutor.OneShotPacketListener finalSend = send;
         MacroExecutor.OneShotPacketListener finalRecv = recv;
         return new MacroExecutor.PrearmedWait(action, matched, () -> {
            if (finalSend != null) {
               finalSend.cancel();
            }

            if (finalRecv != null) {
               finalRecv.cancel();
            }

            if (!matched.isDone()) {
               matched.cancel(true);
            }
         }, 0);
      }
   }

   private static void waitForPacketMatch(WaitPacketMatchAction action) {
      if (action != null) {
         MacroExecutor.OneShotPacketListener send = null;
         MacroExecutor.OneShotPacketListener recv = null;

         try {
            if (action.direction != WaitPacketMatchAction.Direction.S2C) {
               send = awaitSend(packet -> {
                  boolean matches = action.matches(packet, "C2S");
                  if (matches) {
                     recordMatchedPacket(action, packet, "C2S");
                  }

                  return matches;
               });
            }

            if (action.direction != WaitPacketMatchAction.Direction.C2S) {
               recv = awaitReceive(packet -> {
                  boolean matches = action.matches(packet, "S2C");
                  if (matches) {
                     recordMatchedPacket(action, packet, "S2C");
                  }

                  return matches;
               });
            }

            CompletableFuture<?> future;
            if (send != null && recv != null) {
               future = CompletableFuture.anyOf(send.future, recv.future);
            } else if (send != null) {
               future = send.future;
            } else {
               if (recv == null) {
                  return;
               }

               future = recv.future;
            }

            if (action.timeoutMs > 0) {
               future.completeOnTimeout(null, action.timeoutMs, TimeUnit.MILLISECONDS);
            }

            waitForFutureDone(future, action.timeoutMs);
         } finally {
            if (send != null) {
               send.cancel();
            }

            if (recv != null) {
               recv.cancel();
            }
         }
      }
   }

   private static void runPacketGateAction(Minecraft mc, PacketGateAction action) {
      if (action != null) {
         action.execute(mc);
         if (action.mode != PacketGateAction.GateMode.DISABLE_GATE) {
            switch (action.durationMode) {
               case UNTIL_PACKET:
                  WaitPacketMatchAction wait = new WaitPacketMatchAction();

                  wait.direction = switch (action.direction) {
                     case C2S -> WaitPacketMatchAction.Direction.C2S;
                     case S2C -> WaitPacketMatchAction.Direction.S2C;
                     case ANY -> WaitPacketMatchAction.Direction.ANY;
                  };
                  wait.packetName = action.untilPacketName != null && !action.untilPacketName.isBlank()
                     ? action.untilPacketName
                     : (action.packetNames.isEmpty() ? "" : action.packetNames.get(0));
                  wait.fieldName = action.untilPacketField;
                  wait.operator = MacroStringList.enumValue(
                     WaitPacketMatchAction.Operator.class, action.untilPacketOperator, WaitPacketMatchAction.Operator.EXISTS
                  );
                  wait.value = action.untilPacketValue;
                  wait.timeoutMs = Math.max(0, action.durationValue);
                  waitForPacketMatch(wait);
                  disablePacketGate(mc, action);
                  break;
               case UNTIL_GUI:
                  waitForPredicate(() -> MacroGuiMatcher.matches(mc.gui.screen(), action.untilGuiType, action.untilGuiTitle), Math.max(0, action.durationValue));
                  disablePacketGate(mc, action);
                  break;
               case UNTIL_INVENTORY:
                  WaitInventoryPredicateAction wait = new WaitInventoryPredicateAction();
                  wait.condition = MacroStringList.enumValue(
                     WaitInventoryPredicateAction.InventoryCondition.class,
                     action.untilInventoryCondition,
                     WaitInventoryPredicateAction.InventoryCondition.ITEM_EXISTS
                  );
                  wait.itemName = action.untilInventoryItem;
                  wait.count = action.untilInventoryCount;
                  wait.slot = action.untilInventorySlot;
                  wait.timeoutMs = Math.max(0, action.durationValue);
                  wait.resetBaseline();
                  waitForPredicate(() -> wait.matches(mc), wait.timeoutMs);
                  disablePacketGate(mc, action);
            }
         }
      }
   }

   private static void disablePacketGate(Minecraft mc, PacketGateAction action) {
      PacketGateManager.disableAndFlushConfigured(
         action.gateId != null && !action.gateId.isBlank() ? action.gateId : "auto",
         currentRunId(),
         mc == null ? null : mc.getConnection(),
         action.flushOnDisable
      );
   }

   private static void runFinallyActions(MacroExecutor.RunState run, Minecraft mc) {
      if (run != null && !run.finallyActions.isEmpty()) {
         ArrayList<MacroAction> actions = new ArrayList<>(run.finallyActions);
         run.finallyActions.clear();

         for (MacroAction action : actions) {
            if (action != null && action.isEnabled()) {
               try {
                  executeSingleActionWithWaits(mc, action, RiptideModule.get());
               } catch (InterruptedException var6) {
                  Thread.currentThread().interrupt();
                  return;
               } catch (Throwable var7) {
                  RiptideClientMessaging.sendPrefixed("§cCleanup failed: " + safeThrowableMessage(var7));
               }
            }
         }
      }
   }

   private static void waitForMacroStep(WaitForMacroStepAction action) {
      long startMs = System.currentTimeMillis();

      while (isCurrentRunActive()) {
         if (isMacroStepSatisfied(action)) {
            return;
         }

         if (action.timeoutMs > 0 && System.currentTimeMillis() - startMs >= action.timeoutMs) {
            return;
         }

         CompletableFuture<Void> tick = MacroConditionRegistry.waitForNextTick();
         waitForCondition(tick);
      }
   }

   private static boolean isMacroStepSatisfied(WaitForMacroStepAction action) {
      String target = action.macroName == null ? "" : action.macroName.trim();
      if (target.isEmpty()) {
         return true;
      } else {
         synchronized (RUN_LOCK) {
            for (MacroExecutor.RunState run : ACTIVE_RUNS.values()) {
               String name = run.macro != null && run.macro.name != null ? run.macro.name : "";
               if (name.equalsIgnoreCase(target)) {
                  return switch (action.mode) {
                     case STARTED_STEP -> run.currentStepIndex + 1 >= Math.max(1, action.step);
                     case COMPLETED_STEP -> run.lastCompletedStep >= Math.max(1, action.step);
                     case FINISHED -> !run.running.get();
                  };
               }
            }
         }

         return action.mode == WaitForMacroStepAction.WaitMode.FINISHED;
      }
   }

   private static int runRaceGroup(Minecraft mc, RiptideModule module, List<MacroAction> actions, int headerIndex, RaceAction race) throws InterruptedException {
      int bodyCount = race.normalizedBodyCount(actions, headerIndex);
      List<MacroAction> conditions = new ArrayList<>();
      List<MacroAction> bodyActions = new ArrayList<>();

      for (int offset = 1; offset <= bodyCount && headerIndex + offset < actions.size(); offset++) {
         MacroAction child = actions.get(headerIndex + offset);
         if (child != null && child.isEnabled()) {
            if (RaceAction.isConditionAction(child)) {
               conditions.add(child);
            } else if (RaceAction.isBodyAction(child)) {
               bodyActions.add(child);
            }
         }
      }

      setCurrentStatus(
         conditions.isEmpty()
            ? "Race: dispatching"
            : "Race: waiting for " + (conditions.size() == 1 ? conditions.get(0).getDisplayName() : conditions.size() + " conditions")
      );
      MacroExecutor.RunState run = CURRENT_RUN.get();
      CompletableFuture<Void> bodyFuture = new CompletableFuture<>();
      AtomicBoolean dispatched = new AtomicBoolean(false);
      CompletableFuture<?> triggerFuture = raceTriggerBarrier(mc, conditions);
      triggerFuture.thenRun(() -> {
         if (dispatched.compareAndSet(false, true)) {
            if (run != null && !run.running.get()) {
               bodyFuture.cancel(true);
            } else {
               dispatchRaceBody(mc, module, bodyActions, run, bodyFuture);
            }
         }
      }).exceptionally(error -> {
         if (!bodyFuture.isDone()) {
            bodyFuture.completeExceptionally(error);
         }

         return null;
      });
      boolean fired = waitForTimedFuture(triggerFuture, Math.max(0, race.timeoutMs));
      if (fired) {
         setCurrentStatus("Race: fired");
         waitForTimedFuture(bodyFuture, 250L);
         return bodyCount;
      } else {
         triggerFuture.cancel(true);
         bodyFuture.cancel(true);
         RiptideClientMessaging.sendPrefixed("§eRace timed out: " + (race.label != null && !race.label.isBlank() ? race.label : "Race"));
         setCurrentStatus("Race: timeout");
         return bodyCount;
      }
   }

   private static CompletableFuture<?> raceTriggerBarrier(Minecraft mc, List<MacroAction> conditions) {
      if (conditions != null && !conditions.isEmpty()) {
         MacroExecutor.RunState run = CURRENT_RUN.get();
         List<CompletableFuture<MacroExecutor.MacroEvent>> baseFutures = new ArrayList<>();
         List<CompletableFuture<MacroExecutor.MacroEvent>> futures = new ArrayList<>();

         for (MacroAction condition : conditions) {
            clearOwnCaptureOutput(condition);
            CompletableFuture<MacroExecutor.MacroEvent> base = timedConditionFuture(mc, condition);
            baseFutures.add(base);
            futures.add(base.thenApply(event -> {
               if (event.packet() != null) {
                  recordMatchedPacket(condition, event.packet(), event.direction());
               }

               if (condition instanceof MacroCaptureOutput output) {
                  String name = MacroVariableContext.cleanRootName(output.getSaveAs());
                  if (name.isEmpty()) {
                     return (MacroExecutor.MacroEvent)event;
                  } else {
                     MacroValue value = conditionOutputValue(condition, mc);
                     if (value == null) {
                        return (MacroExecutor.MacroEvent)event;
                     } else {
                        LinkedHashMap<String, MacroValue> captures = new LinkedHashMap<>(event.captures());
                        captures.put(name, value);
                        return new MacroExecutor.MacroEvent(event.packet(), event.direction(), event.nanoTime(), event.chatCapture(), captures);
                     }
                  }
               } else {
                  return (MacroExecutor.MacroEvent)event;
               }
            }));
         }

         CompletableFuture<Object> first = CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]));
         CompletableFuture<Object> out = new CompletableFuture<>();
         first.whenComplete((value, e) -> {
            if (e != null) {
               out.completeExceptionally(e);
            } else {
               if (value instanceof MacroExecutor.MacroEvent event && run != null && !event.captures().isEmpty()) {
                  run.variables.setAll(event.captures());
               }

               out.complete(value);
            }
         });
         out.whenComplete((v, e) -> {
            for (CompletableFuture<MacroExecutor.MacroEvent> future : baseFutures) {
               if (!future.isDone()) {
                  future.cancel(true);
               }
            }
         });
         return out;
      } else {
         return CompletableFuture.completedFuture(null);
      }
   }

   private static int runReportGroup(Minecraft mc, List<MacroAction> actions, int headerIndex, ReportAction report) throws InterruptedException {
      int conditionCount = report.normalizedConditionCount(actions, headerIndex);
      if (conditionCount >= 2 && headerIndex + 2 < actions.size()) {
         MacroAction startCondition = actions.get(headerIndex + 1);
         MacroAction endCondition = actions.get(headerIndex + 2);
         if (RaceAction.isBodyAction(startCondition) && RaceAction.isConditionAction(endCondition)) {
            int timeoutMs = Math.max(100, report.timeoutMs);
            String label = report.reportLabel != null && !report.reportLabel.isBlank() ? report.reportLabel.trim() : "Report";
            setCurrentStatus("Report: start " + startCondition.getDisplayName());
            CompletableFuture<MacroExecutor.MacroEvent> startAnchor = startActionAnchorFuture(startCondition);
            executeSingleActionWithWaits(mc, startCondition, null);
            if (!isCurrentRunActive()) {
               return conditionCount;
            } else {
               MacroExecutor.MacroEvent startEvent = waitForAnchorOrNow(startAnchor, 250, startCondition.getDisplayName(), "client action");
               setCurrentStatus("Report: waiting end " + endCondition.getDisplayName());
               CompletableFuture<MacroExecutor.MacroEvent> endFuture = timedConditionFuture(mc, endCondition);
               MacroExecutor.MacroEvent endEvent = waitForTimedEvent(endFuture, timeoutMs);
               if (endEvent == null) {
                  endFuture.cancel(true);
                  RiptideClientMessaging.sendPrefixed("§eReport \"" + label + "\": timeout waiting for end condition");
                  return conditionCount;
               } else {
                  long deltaNanos = Math.max(0L, endEvent.nanoTime() - startEvent.nanoTime());
                  long deltaMs = deltaNanos / 1000000L;
                  String start = startEvent.label();
                  String end = endEvent.label();
                  RiptideClientMessaging.sendPrefixed("§b" + label + ": " + formatNanos(deltaNanos) + " (" + start + " -> " + end + ")");
                  if (report.stashToSharedState) {
                     RiptideSharedState.get().pushReport(new RiptideSharedState.ReportSample(label, deltaMs, start, end, System.currentTimeMillis()));
                  }

                  return conditionCount;
               }
            }
         } else if (RaceAction.isConditionAction(startCondition) && RaceAction.isConditionAction(endCondition)) {
            int timeoutMs = Math.max(100, report.timeoutMs);
            long startWaitNanos = System.nanoTime();
            setCurrentStatus("Report: waiting start " + startCondition.getDisplayName());
            boolean startFired = waitForMacroCondition(mc, startCondition, timeoutMs);
            if (!startFired) {
               RiptideClientMessaging.sendPrefixed("§eReport \"" + report.reportLabel + "\": timeout waiting for start condition");
               return conditionCount;
            } else {
               MacroExecutor.MacroEvent startEvent = new MacroExecutor.MacroEvent(null, "condition " + startCondition.getDisplayName(), System.nanoTime(), null);
               long spentMs = (startEvent.nanoTime() - startWaitNanos) / 1000000L;
               int remainingMs = (int)Math.max(1L, timeoutMs - spentMs);
               setCurrentStatus("Report: waiting end " + endCondition.getDisplayName());
               CompletableFuture<MacroExecutor.MacroEvent> endFuture = timedConditionFuture(mc, endCondition);
               MacroExecutor.MacroEvent endEvent = waitForTimedEvent(endFuture, remainingMs);
               if (endEvent == null) {
                  endFuture.cancel(true);
                  RiptideClientMessaging.sendPrefixed("§eReport \"" + report.reportLabel + "\": timeout waiting for end condition");
                  return conditionCount;
               } else {
                  long deltaNanos = Math.max(0L, endEvent.nanoTime() - startEvent.nanoTime());
                  long deltaMs = deltaNanos / 1000000L;
                  String label = report.reportLabel != null && !report.reportLabel.isBlank() ? report.reportLabel.trim() : "Report";
                  String start = startEvent.label();
                  String end = endEvent.label();
                  RiptideClientMessaging.sendPrefixed("§b" + label + ": " + formatNanos(deltaNanos) + " (" + start + " -> " + end + ")");
                  if (report.stashToSharedState) {
                     RiptideSharedState.get().pushReport(new RiptideSharedState.ReportSample(label, deltaMs, start, end, System.currentTimeMillis()));
                  }

                  return conditionCount;
               }
            }
         } else {
            report.execute(mc);
            return conditionCount;
         }
      } else {
         setCurrentStatus(report.getDisplayName());
         report.execute(mc);
         return conditionCount;
      }
   }

   private static boolean waitForRaceTrigger(Minecraft mc, RaceAction race) throws InterruptedException {
      MacroAction trigger = (MacroAction)(race.triggerAction == null ? new DelayAction(0) : race.triggerAction);
      int timeoutMs = Math.max(0, race.timeoutMs);
      return waitForMacroCondition(mc, trigger, timeoutMs);
   }

   private static int runMultiGroup(Minecraft mc, RiptideModule module, String macroName, List<MacroAction> actions, int headerIndex, MultiAction multi) throws InterruptedException {
      if (RiptideLiteVariant.enabled()) {
         return 0;
      } else {
         int stepCount = multi.normalizedStepCount(actions, headerIndex);
         if (stepCount <= 0) {
            return 0;
         } else {
            MultiManager manager = MultiManager.get();
            MultiProfile profile = manager.isActive() ? manager.activeProfile() : MultiProfileManager.get().find(MultiProfileManager.get().selectedId());
            if (profile == null) {
               RiptideClientMessaging.sendPrefixed("§eMulti: no Multi profile to start");
               return stepCount;
            } else {
               LinkedHashSet<String> scope = new LinkedHashSet<>();
               Set<String> profileIds = new HashSet<>();

               for (MultiProfile.SessionSpec spec : profile.sessions) {
                  if (spec != null && spec.accountId() != null && !spec.accountId().isBlank()) {
                     profileIds.add(spec.accountId());
                  }
               }

               for (String accountId : multi.effectiveAccountIds()) {
                  if (profileIds.contains(accountId)) {
                     scope.add(accountId);
                  }
               }

               if (scope.isEmpty()) {
                  RiptideClientMessaging.sendPrefixed("§eMulti: no selected accounts in the active Multi profile");
                  return stepCount;
               } else {
                  if (multi.connectIfDown) {
                     if (!manager.isActive()) {
                        setCurrentStatus("Multi: starting batch");
                        MultiManager.StartResult started = manager.start(profile);
                        if (!started.ok()) {
                           RiptideClientMessaging.sendPrefixed("§eMulti: could not start the Multi batch");
                           return stepCount;
                        }
                     } else {
                        Map<String, MultiSession.Snapshot> byId = multiSnapshotsById(manager);

                        for (String accountIdx : scope) {
                           MultiSession.Snapshot snapshot = byId.get(accountIdx);
                           if (snapshot == null || MultiManager.isRetryable(snapshot.status())) {
                              manager.retry(accountIdx);
                           }
                        }
                     }
                  }

                  if (!isCurrentRunActive()) {
                     return stepCount;
                  } else {
                     int waitMs = Math.max(0, multi.waitMs);
                     if ("Until ready".equals(multi.waitMode)) {
                        setCurrentStatus("Multi: waiting for " + scope.size() + (scope.size() == 1 ? " account" : " accounts"));
                        multiWaitUntilReady(manager, scope, waitMs);
                     } else if ("Fixed delay".equals(multi.waitMode) && waitMs > 0) {
                        setCurrentStatus("Multi: delay " + waitMs + " ms");
                        multiSleepCancellable(waitMs);
                     }

                     if (!isCurrentRunActive()) {
                        return stepCount;
                     } else {
                        RiptideMacro subMacro = new RiptideMacro();
                        subMacro.name = (macroName != null && !macroName.isBlank() ? macroName : "Macro") + " (Multi)";

                        for (int offset = 1; offset <= stepCount && headerIndex + offset < actions.size(); offset++) {
                           MacroAction child = actions.get(headerIndex + offset);
                           if (child != null) {
                              MacroAction copy = RiptideMacro.createActionFromTag(RiptideMacro.serializeAction(child));
                              if (copy != null) {
                                 subMacro.actions.add(copy);
                              }
                           }
                        }

                        setCurrentStatus("Multi: running on " + scope.size() + (scope.size() == 1 ? " account" : " accounts"));
                        MultiManager.BroadcastResult result = manager.runMacroDirect(subMacro, scope);
                        RiptideClientMessaging.sendPrefixed("§bMulti: " + result.summary());
                        setCurrentStatus("Multi: waiting for completion");
                        multiWaitForCompletion(manager, scope, subMacro.name);
                        if (!isCurrentRunActive()) {
                           return stepCount;
                        } else {
                           if (multi.disconnectAfter) {
                              for (String accountIdxx : scope) {
                                 if (!manager.hasQueuedMacroIntent(accountIdxx, subMacro.name)) {
                                    manager.disconnectSession(accountIdxx);
                                 }
                              }
                           }

                           return stepCount;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static Map<String, MultiSession.Snapshot> multiSnapshotsById(MultiManager manager) {
      Map<String, MultiSession.Snapshot> byId = new HashMap<>();

      for (MultiSession.Snapshot snapshot : manager.snapshots()) {
         if (snapshot != null) {
            byId.put(snapshot.accountId(), snapshot);
         }
      }

      return byId;
   }

   private static void multiSleepCancellable(long totalMs) throws InterruptedException {
      long deadline = System.nanoTime() + Math.max(0L, totalMs) * 1000000L;

      long remaining;
      while ((remaining = deadline - System.nanoTime()) > 0L) {
         if (!isCurrentRunActive()) {
            return;
         }

         Thread.sleep(Math.min(100L, Math.max(1L, remaining / 1000000L)));
      }
   }

   private static void multiWaitUntilReady(MultiManager manager, Set<String> scope, long timeoutMs) throws InterruptedException {
      long deadline = System.nanoTime() + Math.max(0L, timeoutMs) * 1000000L;

      while (isCurrentRunActive()) {
         boolean allReady = true;
         Map<String, MultiSession.Snapshot> byId = multiSnapshotsById(manager);

         for (String accountId : scope) {
            MultiSession.Snapshot snapshot = byId.get(accountId);
            if (snapshot == null || !snapshot.ready()) {
               allReady = false;
               break;
            }
         }

         if (allReady) {
            return;
         }

         if (System.nanoTime() >= deadline) {
            RiptideClientMessaging.sendPrefixed("§eMulti: timed out waiting for accounts to be ready");
            return;
         }

         Thread.sleep(150L);
      }
   }

   private static void multiWaitForCompletion(MultiManager manager, Set<String> scope, String subMacroName) throws InterruptedException {
      long deadline = System.nanoTime() + 600000000000L;
      Thread.sleep(200L);

      while (isCurrentRunActive()) {
         boolean anyPending = false;
         Map<String, MultiSession.Snapshot> byId = multiSnapshotsById(manager);

         for (String accountId : scope) {
            if (manager.hasQueuedMacroIntent(accountId, subMacroName)) {
               anyPending = true;
               break;
            }

            MultiSession.Snapshot snapshot = byId.get(accountId);
            MultiSession.MacroProgress progress = snapshot == null ? null : snapshot.macroProgress();
            if (progress != null && progress.running()) {
               anyPending = true;
               break;
            }
         }

         if (!anyPending) {
            return;
         }

         if (System.nanoTime() >= deadline) {
            RiptideClientMessaging.sendPrefixed("§eMulti: timed out waiting for " + scope.size() + (scope.size() == 1 ? " account" : " accounts"));
            return;
         }

         Thread.sleep(200L);
      }
   }

   public static boolean waitForMacroCondition(Minecraft mc, MacroAction trigger, int timeoutMs) throws InterruptedException {
      if (trigger == null) {
         return true;
      } else {
         CompletableFuture<?> future = raceConditionFuture(mc, trigger);
         if (future == null) {
            executeSingleActionWithWaits(mc, trigger, null);
            return true;
         } else {
            boolean fired = waitForFutureDone(future, timeoutMs);
            if (!fired) {
               future.cancel(true);
            }

            return fired;
         }
      }
   }

   private static void dispatchRaceBody(
      Minecraft mc, RiptideModule module, List<MacroAction> bodyActions, MacroExecutor.RunState run, CompletableFuture<Void> bodyFuture
   ) {
      if (bodyActions != null && !bodyActions.isEmpty()) {
         Runnable task = () -> {
            MacroExecutor.RunState previous = CURRENT_RUN.get();
            if (run != null) {
               CURRENT_RUN.set(run);
            }

            EnumSet<MacroActionResources.SharedClientResource> heldResources = EnumSet.noneOf(MacroActionResources.SharedClientResource.class);

            try {
               try {
                  heldResources = acquireClientResources(resourcesForActions(bodyActions));
                  Throwable firstFailure = null;

                  for (MacroAction body : bodyActions) {
                     if (body != null && body.isEnabled() && RaceAction.isBodyAction(body)) {
                        if (run != null && !run.running.get()) {
                           bodyFuture.cancel(true);
                           return;
                        }

                        try {
                           executeRaceBodyActionFast(mc, body);
                        } catch (InterruptedException var16) {
                           throw var16;
                        } catch (Throwable var17) {
                           if (firstFailure == null) {
                              firstFailure = var17;
                           }

                           RiptideClientMessaging.sendPrefixed("§cRace action failed (" + body.getDisplayName() + "): " + safeThrowableMessage(var17));
                        }
                     }
                  }

                  if (firstFailure == null) {
                     bodyFuture.complete(null);
                  } else {
                     bodyFuture.completeExceptionally(firstFailure);
                  }

                  return;
               } catch (InterruptedException var18) {
                  Thread.currentThread().interrupt();
                  bodyFuture.cancel(true);
               } catch (Throwable var19) {
                  RiptideClientMessaging.sendPrefixed("§cRace action failed: " + safeThrowableMessage(var19));
                  bodyFuture.completeExceptionally(var19);
               }
            } finally {
               releaseClientResources(heldResources);
               if (previous != null) {
                  CURRENT_RUN.set(previous);
               } else {
                  CURRENT_RUN.remove();
               }
            }
         };
         if (bodyActions.stream().allMatch(MacroExecutor::isRaceDirectPacketAction)) {
            task.run();
         } else {
            mc.execute(task);
         }
      } else {
         bodyFuture.complete(null);
      }
   }

   private static void executeRaceBodyActionFast(Minecraft mc, MacroAction body) throws InterruptedException {
      if (body != null && body.isEnabled() && RaceAction.isBodyAction(body)) {
         if (MacroDynamicBindings.apply(body, mc)) {
            body.execute(mc);
         }
      }
   }

   private static boolean isRaceDirectPacketAction(MacroAction action) {
      if (action instanceof SendChatAction chat) {
         return !chat.isWaitForGuiBefore() && !chat.isWaitForGuiAfter();
      } else {
         return !(action instanceof SendPacketAction sendPacket)
            ? action instanceof PacketClickAction
            : !sendPacket.isWaitForGuiBefore() && !sendPacket.isWaitForGuiAfter();
      }
   }

   private static CompletableFuture<MacroExecutor.MacroEvent> startActionAnchorFuture(MacroAction action) {
      if (action instanceof PacketClickAction) {
         return awaitTimedSend(packet -> packet instanceof ServerboundContainerClickPacket).future;
      } else if (action instanceof ItemAction) {
         return awaitTimedSend(packet -> packet instanceof ServerboundContainerClickPacket).future;
      } else if (action instanceof CloseGuiAction) {
         return awaitTimedSend(packet -> packet instanceof ServerboundContainerClosePacket).future;
      } else if (action instanceof SendChatAction) {
         return awaitTimedSend(
               packet -> packet instanceof ServerboundChatPacket
                  || packet instanceof ServerboundChatCommandPacket
                  || packet instanceof ServerboundChatCommandSignedPacket
            )
            .future;
      } else {
         return !(action instanceof SendPacketAction) && !(action instanceof PayloadAction) ? null : awaitTimedSend(packet -> true).future;
      }
   }

   private static MacroExecutor.MacroEvent waitForAnchorOrNow(
      CompletableFuture<MacroExecutor.MacroEvent> anchor, int timeoutMs, String label, String fallbackDirection
   ) {
      if (anchor != null) {
         MacroExecutor.MacroEvent event = waitForTimedEvent(anchor, timeoutMs);
         if (event != null) {
            return event;
         }

         anchor.cancel(true);
      }

      return new MacroExecutor.MacroEvent(null, fallbackDirection + " " + (label == null ? "" : label), System.nanoTime(), null);
   }

   private static MacroExecutor.MacroEvent waitForTimedEvent(CompletableFuture<MacroExecutor.MacroEvent> future, long timeoutMs) {
      if (future == null) {
         return null;
      } else {
         try {
            return timeoutMs <= 0L ? future.get() : future.get(timeoutMs, TimeUnit.MILLISECONDS);
         } catch (InterruptedException var4) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return null;
         } catch (TimeoutException | CancellationException | ExecutionException var5) {
            return null;
         }
      }
   }

   private static boolean waitForTimedFuture(CompletableFuture<?> future, long timeoutMs) {
      if (future == null) {
         return true;
      } else {
         try {
            if (timeoutMs <= 0L) {
               future.get();
            } else {
               future.get(timeoutMs, TimeUnit.MILLISECONDS);
            }

            return !future.isCancelled() && isCurrentRunActive();
         } catch (InterruptedException var4) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return false;
         } catch (TimeoutException | CancellationException | ExecutionException var5) {
            return false;
         }
      }
   }

   private static String formatNanos(long nanos) {
      return String.format(Locale.ROOT, "%.3f ms", nanos / 1000000.0);
   }

   private static String safeThrowableMessage(Throwable throwable) {
      if (throwable == null) {
         return "unknown error";
      } else {
         String message = throwable.getMessage();
         return message != null && !message.isBlank() ? message : throwable.getClass().getSimpleName();
      }
   }

   private static boolean isWorldTransitionPacket(Packet<?> packet) {
      return packet instanceof ClientboundRespawnPacket || packet instanceof ClientboundLoginPacket;
   }

   private static CompletableFuture<MacroExecutor.MacroEvent> timedConditionFuture(Minecraft mc, MacroAction trigger) {
      if (trigger == null) {
         return CompletableFuture.completedFuture(new MacroExecutor.MacroEvent(null, "condition", System.nanoTime(), null));
      } else if (trigger instanceof WaitForChatAction chat) {
         MacroVariableContext context = currentVariableContext();
         AtomicReference<Map<String, MacroValue>> captures = new AtomicReference<>(Map.of());
         MacroExecutor.TimedOneShotPacketListener listener = awaitTimedReceive((packet, direction, now) -> {
            MacroExecutor.ChatCapture capture = extractIncomingChat(packet);
            Optional<Map<String, MacroValue>> matched = captureChatAction(chat, capture, context);
            matched.ifPresent(captures::set);
            return matched.isPresent();
         });
         CompletableFuture<MacroExecutor.MacroEvent> out = new CompletableFuture<>();
         listener.future
            .whenComplete(
               (event, e) -> {
                  if (e != null) {
                     out.completeExceptionally(e);
                  } else {
                     out.complete(
                        new MacroExecutor.MacroEvent(event.packet(), event.direction(), event.nanoTime(), extractIncomingChat(event.packet()), captures.get())
                     );
                  }
               }
            );
         out.whenComplete((v, e) -> listener.cancel());
         return out;
      } else if (trigger instanceof WaitForPacketAction packetAction) {
         List<String> targets = packetAction.effectiveList();
         CompletableFuture<MacroExecutor.MacroEvent> matched = new CompletableFuture<>();
         MacroExecutor.TimedOneShotPacketListener recv = awaitTimedReceive((packet, direction, now) -> {
            if (targets.isEmpty()) {
               return true;
            } else {
               for (String target : targets) {
                  if (matchesPacketTarget(target, packet, "S2C")) {
                     return true;
                  }
               }

               return false;
            }
         });
         MacroExecutor.TimedOneShotPacketListener send = awaitTimedSend((packet, direction, now) -> {
            if (targets.isEmpty()) {
               return true;
            } else {
               for (String target : targets) {
                  if (matchesPacketTarget(target, packet, "C2S")) {
                     return true;
                  }
               }

               return false;
            }
         });
         recv.future.thenAccept(matched::complete);
         send.future.thenAccept(matched::complete);
         matched.whenComplete((v, e) -> {
            recv.cancel();
            send.cancel();
         });
         return matched;
      } else if (trigger instanceof WaitForWorldChangeAction world) {
         CompletableFuture<MacroExecutor.MacroEvent> done = new CompletableFuture<>();
         CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForWorldChange(world);
         MacroExecutor.TimedOneShotPacketListener worldListener = awaitTimedReceive((packet, direction, now) -> isWorldTransitionPacket(packet));
         tickFuture.whenComplete((v, e) -> {
            if (e != null) {
               if (!done.isDone()) {
                  done.cancel(true);
               }
            } else {
               done.complete(new MacroExecutor.MacroEvent(null, "world change", System.nanoTime(), null));
            }
         });
         worldListener.future.whenComplete((event, error) -> {
            if (error != null) {
               if (!done.isDone()) {
                  done.cancel(true);
               }
            } else {
               if (world.targetDimension == null || world.targetDimension.isBlank()) {
                  done.complete(event);
               }
            }
         });
         done.whenComplete((v, e) -> {
            worldListener.cancel();
            if (!tickFuture.isDone()) {
               tickFuture.cancel(true);
            }
         });
         return done;
      } else if (trigger instanceof WaitForTeleportAction teleport) {
         Vec3 origin = mc.player == null ? Vec3.ZERO : mc.player.position();
         CompletableFuture<MacroExecutor.MacroEvent> done = new CompletableFuture<>();
         MacroExecutor.TimedOneShotPacketListener teleListener = awaitTimedReceive(
            (packet, direction, now) -> packet instanceof ClientboundPlayerPositionPacket || isWorldTransitionPacket(packet)
         );
         teleListener.future.whenComplete((event, error) -> {
            teleListener.cancel();
            if (error != null) {
               done.cancel(true);
            } else if (teleport.minDistance <= 0.0) {
               done.complete(event);
            } else {
               MacroConditionRegistry.waitForPositionDeltaFrom(origin, teleport.minDistance, teleport.horizontalOnly).whenComplete((v, e) -> {
                  if (e != null) {
                     done.cancel(true);
                  } else {
                     done.complete(new MacroExecutor.MacroEvent(event.packet(), event.direction(), System.nanoTime(), event.chatCapture()));
                  }
               });
            }
         });
         done.whenComplete((v, e) -> teleListener.cancel());
         return done;
      } else {
         CompletableFuture<?> base = raceConditionFuture(mc, trigger);
         if (base == null) {
            CompletableFuture<MacroExecutor.MacroEvent> done = new CompletableFuture<>();
            MacroExecutor.RunState run = CURRENT_RUN.get();
            mc.execute(() -> withRunContext(run, () -> {
               try {
                  executeSingleActionWithWaits(mc, trigger, null);
                  done.complete(new MacroExecutor.MacroEvent(null, trigger.getDisplayName(), System.nanoTime(), null));
               } catch (InterruptedException var4x) {
                  Thread.currentThread().interrupt();
                  done.cancel(true);
               } catch (Throwable var5x) {
                  done.completeExceptionally(var5x);
               }
            }));
            return done;
         } else {
            CompletableFuture<MacroExecutor.MacroEvent> timed = new CompletableFuture<>();
            base.whenComplete((v, e) -> {
               if (e != null) {
                  timed.cancel(true);
               } else {
                  timed.complete(new MacroExecutor.MacroEvent(null, trigger.getDisplayName(), System.nanoTime(), null));
               }
            });
            timed.whenComplete((v, e) -> {
               if (!base.isDone()) {
                  base.cancel(true);
               }
            });
            return timed;
         }
      }
   }

   private static CompletableFuture<?> raceConditionFuture(Minecraft mc, MacroAction trigger) {
      if (trigger instanceof WaitMovementAction wm) {
         return raceConditionFuture(mc, wm.resolveSubAction());
      } else if (trigger instanceof WaitGamemodeChangeAction gm) {
         return MacroConditionRegistry.waitForGamemodeChange(gm);
      } else if (trigger instanceof DelayAction delay) {
         MacroExecutor.RunState run = CURRENT_RUN.get();
         return CompletableFuture.runAsync(() -> withRunContext(run, () -> {
            try {
               if (delay.useTicks) {
                  for (int i = 0; i < delay.delayTicks && isCurrentRunActive(); i++) {
                     waitForCondition(MacroConditionRegistry.waitForNextTick());
                  }
               } else {
                  Thread.sleep(Math.max(0, delay.delayMs));
               }
            } catch (InterruptedException var2x) {
               Thread.currentThread().interrupt();
            }
         }));
      } else if (trigger instanceof WaitForGuiAction gui) {
         CompletableFuture<MacroExecutor.MacroEvent> done = new CompletableFuture<>();
         MacroExecutor.RunState run = CURRENT_RUN.get();
         Thread worker = new Thread(() -> withRunContext(run, () -> {
            long start = System.currentTimeMillis();
            boolean saw = false;
            Map<String, MacroValue> observed = Map.of();

            while (!done.isDone() && isCurrentRunActive()) {
               Optional<Map<String, MacroValue>> captured = gui.captureGui(Minecraft.getInstance().gui.screen());
               if (captured.isPresent()) {
                  saw = true;
                  observed = captured.get();
                  if (gui.waitMode == WaitForGuiAction.WaitMode.OPEN) {
                     done.complete(new MacroExecutor.MacroEvent(null, "GUI", System.nanoTime(), null, observed));
                     return;
                  }
               } else if (saw && gui.waitMode == WaitForGuiAction.WaitMode.CLOSE) {
                  done.complete(new MacroExecutor.MacroEvent(null, "GUI close", System.nanoTime(), null, observed));
                  return;
               }

               if (gui.timeoutMs > 0 && System.currentTimeMillis() - start >= gui.timeoutMs) {
                  done.cancel(false);
                  return;
               }

               LockSupport.parkNanos(500000L);
            }
         }), "MacroGuiCaptureWait");
         worker.setDaemon(true);
         worker.start();
         done.whenComplete((v, e) -> worker.interrupt());
         return done;
      } else if (trigger instanceof WaitForChatAction chat) {
         MacroVariableContext context = currentVariableContext();
         MacroExecutor.OneShotPacketListener listener = awaitReceive(packet -> {
            MacroExecutor.ChatCapture capture = extractIncomingChat(packet);
            return capture != null && captureChatAction(chat, capture, context).isPresent();
         });
         listener.future.whenComplete((v, e) -> listener.cancel());
         return listener.future;
      } else if (trigger instanceof WaitForPacketAction packetAction) {
         List<String> targets = packetAction.effectiveList();
         CompletableFuture<Packet<?>> matched = new CompletableFuture<>();
         MacroExecutor.OneShotPacketListener recv = awaitReceive(packet -> {
            if (targets.isEmpty()) {
               return true;
            } else {
               for (String target : targets) {
                  if (matchesPacketTarget(target, packet, "S2C")) {
                     return true;
                  }
               }

               return false;
            }
         });
         MacroExecutor.OneShotPacketListener send = awaitSend(packet -> {
            if (targets.isEmpty()) {
               return true;
            } else {
               for (String target : targets) {
                  if (matchesPacketTarget(target, packet, "C2S")) {
                     return true;
                  }
               }

               return false;
            }
         });
         recv.future.thenAccept(matched::complete);
         send.future.thenAccept(matched::complete);
         matched.whenComplete((v, e) -> {
            recv.cancel();
            send.cancel();
         });
         return matched;
      } else if (trigger instanceof WaitForHealthAction health) {
         return MacroConditionRegistry.waitForHealth(health.healthThreshold, health.below);
      } else if (trigger instanceof WaitForBlockAction block) {
         return MacroConditionRegistry.waitForBlock(block);
      } else if (trigger instanceof WaitForCooldownAction cooldown) {
         ItemTarget target = resolveItemTarget(cooldown.itemTarget, cooldown.itemName);
         return MacroConditionRegistry.waitForCooldown(target, cooldown.checkMainInteractionHand);
      } else if (trigger instanceof WaitPosAction pos) {
         return MacroConditionRegistry.waitForPos(pos.x, pos.y, pos.z, pos.leeway, pos.checkRotation, pos.yaw, pos.pitch, pos.rotLeeway);
      } else if (trigger instanceof WaitForEntityAction entity) {
         return predicateFuture(() -> entity.matchesEntityTarget(Minecraft.getInstance()), entity.timeoutMs);
      } else if (trigger instanceof WaitForSoundAction sound) {
         return MacroConditionRegistry.waitForSound(sound);
      } else if (trigger instanceof WaitForSlotChangeAction slot) {
         return MacroConditionRegistry.waitForSlotChange(slot);
      } else if (trigger instanceof WaitDurabilityAction durability) {
         return predicateFuture(() -> durabilityObservationMatches(Minecraft.getInstance(), durability), durability.timeoutMs);
      } else if (trigger instanceof WaitFreeSlotsAction freeSlots) {
         return predicateFuture(() -> freeSlots.matches(Minecraft.getInstance()), freeSlots.timeoutMs);
      } else if (trigger instanceof TickSyncAction) {
         return MacroConditionRegistry.waitForNextTick();
      } else if (trigger instanceof ServerTickSyncAction serverTick) {
         return MacroConditionRegistry.waitForServerTick(serverTick.bufferMs, serverTick.maxWaitMs, serverTick.ignorePing);
      } else if (trigger instanceof WaitForWorldChangeAction world) {
         CompletableFuture<Void> done = new CompletableFuture<>();
         CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForWorldChange(world);
         MacroExecutor.OneShotPacketListener worldListener = awaitReceive(MacroExecutor::isWorldTransitionPacket);
         tickFuture.whenComplete((v, e) -> {
            if (e != null) {
               if (!done.isDone()) {
                  done.cancel(true);
               }
            } else {
               done.complete(null);
            }
         });
         worldListener.future.whenComplete((packet, error) -> {
            if (error != null) {
               if (!done.isDone()) {
                  done.cancel(true);
               }
            } else {
               if (world.targetDimension == null || world.targetDimension.isBlank()) {
                  done.complete(null);
               }
            }
         });
         done.whenComplete((v, e) -> {
            worldListener.cancel();
            if (!tickFuture.isDone()) {
               tickFuture.cancel(true);
            }
         });
         return done;
      } else if (trigger instanceof WaitForPositionDeltaAction delta) {
         return MacroConditionRegistry.waitForPositionDelta(delta.distance, delta.horizontalOnly);
      } else if (trigger instanceof WaitForTeleportAction teleport) {
         Vec3 origin = mc.player == null ? Vec3.ZERO : mc.player.position();
         CompletableFuture<Void> done = new CompletableFuture<>();
         MacroExecutor.OneShotPacketListener teleListener = awaitReceive(
            packet -> packet instanceof ClientboundPlayerPositionPacket || isWorldTransitionPacket(packet)
         );
         teleListener.future.whenComplete((packet, error) -> {
            teleListener.cancel();
            if (error != null) {
               done.cancel(true);
            } else if (teleport.minDistance <= 0.0) {
               done.complete(null);
            } else {
               MacroConditionRegistry.waitForPositionDeltaFrom(origin, teleport.minDistance, teleport.horizontalOnly).whenComplete((v, e) -> {
                  if (e != null) {
                     done.cancel(true);
                  } else {
                     done.complete(null);
                  }
               });
            }
         });
         return done;
      } else if (trigger instanceof RevisionSyncAction) {
         MacroExecutor.RunState run = CURRENT_RUN.get();
         return CompletableFuture.runAsync(() -> withRunContext(run, () -> {
            try {
               executeSingleActionWithWaits(mc, trigger, null);
            } catch (InterruptedException var3x) {
               Thread.currentThread().interrupt();
            }
         }));
      } else {
         return null;
      }
   }

   private static boolean waitForFutureDone(CompletableFuture<?> future, long timeoutMs) {
      Thread t = Thread.currentThread();
      long start = System.nanoTime();
      long timeoutNanos = timeoutMs <= 0L ? Long.MAX_VALUE : timeoutMs * 1000000L;
      future.whenComplete((v, e) -> LockSupport.unpark(t));

      while (!future.isDone() && isCurrentRunActive() && System.nanoTime() - start < timeoutNanos) {
         LockSupport.parkNanos(500000L);
         if (Thread.interrupted()) {
            break;
         }
      }

      return future.isDone() && !future.isCancelled() && isCurrentRunActive();
   }

   private static void executeSingleActionWithWaits(Minecraft mc, MacroAction action, RiptideModule module) throws InterruptedException {
      if (MacroDynamicBindings.apply(action, mc)) {
         if (dynamicTextInputsAvailable(action, mc)) {
            clearOwnCaptureOutput(action);
            EnumSet<MacroActionResources.SharedClientResource> heldResources = acquireClientResources(action);

            try {
               if (!waitForGuiBeforeAction(mc, action)) {
                  return;
               }

               beginRaycastAim(mc, action);
               CompletableFuture<Void> postGuiFuture = !(action instanceof ItemAction)
                     && !(action instanceof DropAction)
                     && !isTargetWaitInteractionAction(action)
                  ? createPostGuiFuture(mc, action)
                  : null;
               if (action instanceof DelayAction da) {
                  if (da.useTicks) {
                     for (int t = 0; t < da.delayTicks && isCurrentRunActive(); t++) {
                        CompletableFuture<Void> tf = MacroConditionRegistry.waitForNextTick();
                        waitForCondition(tf);
                     }
                  } else {
                     Thread.sleep(da.delayMs);
                  }
               } else if (action instanceof CustomMenuAction customMenu) {
                  setCurrentStatus(customMenu.getDisplayName());
                  customMenu.execute(mc);
               } else if (action instanceof WaitForPacketAction packetAction) {
                  packetAction.matchedPacket = null;
                  packetAction.matchedDirection = "";
                  MacroExecutor.RunState packetRun = CURRENT_RUN.get();
                  if (packetRun != null) {
                     packetRun.lastMatchedPacket = null;
                     packetRun.lastMatchedPacketDirection = "";
                  }

                  List<String> targets = packetAction.effectiveList();
                  if (targets.isEmpty()) {
                     awaitPacket("");
                  } else {
                     for (String target : targets) {
                        if (!isCurrentRunActive()) {
                           break;
                        }

                        awaitPacket(target);
                     }
                  }

                  copyLastMatchedPacket(packetAction);
               } else if (action instanceof WaitPacketMatchAction wpma) {
                  setCurrentStatus(wpma.getDisplayName());
                  waitForPacketMatch(wpma);
               } else if (action instanceof WaitInventoryPredicateAction wipa) {
                  setCurrentStatus(wipa.getDisplayName());
                  waitForPredicate(() -> wipa.matches(mc), wipa.timeoutMs);
               } else if (action instanceof WaitDurabilityAction wda) {
                  setCurrentStatus(wda.getDisplayName());
                  if (wda.useNext) {
                     waitForDurabilityUseNext(mc, wda);
                  } else {
                     waitForPredicate(() -> wda.matches(mc), wda.timeoutMs);
                  }
               } else if (action instanceof WaitFreeSlotsAction wfsa) {
                  setCurrentStatus(wfsa.getDisplayName());
                  waitForPredicate(() -> wfsa.matches(mc), wfsa.timeoutMs);
               } else if (action instanceof WaitEntityTargetAction weta) {
                  setCurrentStatus(weta.getDisplayName());
                  waitForPredicate(() -> weta.matches(mc), weta.timeoutMs);
               } else if (action instanceof WaitGuiTypeAction wgta) {
                  setCurrentStatus(wgta.getDisplayName());
                  clearCaptureOutputs(wgta);
                  if (wgta.waitMode == WaitGuiTypeAction.WaitMode.CLOSE) {
                     waitForPredicate(guiClosePredicate(wgta, mc), wgta.timeoutMs);
                  } else {
                     waitForPredicate(() -> {
                        Optional<Map<String, MacroValue>> captured = wgta.capture(mc);
                        captured.ifPresent(values -> stageCapture(wgta, (Map<String, MacroValue>)values));
                        return captured.isPresent();
                     }, wgta.timeoutMs);
                  }

                  commitStagedCapture(wgta);
               } else if (action instanceof PacketGateAction pga) {
                  runPacketGateAction(mc, pga);
               } else if (isTargetWaitInteractionAction(action)) {
                  postGuiFuture = runTargetWaitInteractionAction(mc, action);
               } else if (action instanceof WaitForHealthAction wh) {
                  setCurrentStatus(wh.waitingStatusText());

                  try {
                     CompletableFuture<Void> future = MacroConditionRegistry.waitForHealth(wh.healthThreshold, wh.below);
                     waitForCondition(future);
                  } catch (CancellationException var96) {
                  }
               } else if (action instanceof WaitForBlockAction wba) {
                  setCurrentStatus(wba.getDisplayName());

                  try {
                     CompletableFuture<Void> future = MacroConditionRegistry.waitForBlock(wba);
                     waitForCondition(future);
                  } catch (CancellationException var95) {
                  }
               } else if (action instanceof WaitForGuiAction wga) {
                  clearCaptureOutputs(wga);
                  if (wga.waitMode == WaitForGuiAction.WaitMode.CLOSE) {
                     setCurrentStatus("Waiting for GUI close: " + wga.guiTitle);
                     waitForPredicate(guiClosePredicate(wga, mc), wga.timeoutMs);
                  } else {
                     setCurrentStatus("Waiting for GUI: " + wga.guiTitle);
                     waitForPredicate(() -> {
                        Optional<Map<String, MacroValue>> captured = wga.captureGui(mc.gui.screen());
                        captured.ifPresent(values -> stageCapture(wga, (Map<String, MacroValue>)values));
                        return captured.isPresent();
                     }, wga.timeoutMs);
                  }

                  commitStagedCapture(wga);
               } else if (action instanceof WaitForCooldownAction wca) {
                  ItemTarget cooldownTarget = resolveItemTarget(wca.itemTarget, wca.itemName).resolveTemplate(mc);
                  if (cooldownTarget == null) {
                     return;
                  }

                  String cooldownLabel = describeItemTarget(cooldownTarget);
                  setCurrentStatus(
                     "Waiting for cooldown: " + (!cooldownLabel.isEmpty() ? cooldownLabel : (wca.checkMainInteractionHand ? "Main Hand" : "Off Hand"))
                  );

                  try {
                     CompletableFuture<Void> future = MacroConditionRegistry.waitForCooldown(cooldownTarget, wca.checkMainInteractionHand);
                     waitForCondition(future);
                  } catch (CancellationException var94) {
                  }
               } else if (action instanceof WaitPosAction wpa) {
                  setCurrentStatus("Waiting for Pos: " + String.format("%.0f, %.0f, %.0f", wpa.x, wpa.y, wpa.z));

                  try {
                     CompletableFuture<Void> future = MacroConditionRegistry.waitForPos(
                        wpa.x, wpa.y, wpa.z, wpa.leeway, wpa.checkRotation, wpa.yaw, wpa.pitch, wpa.rotLeeway
                     );
                     waitForCondition(future);
                  } catch (CancellationException var93) {
                  }
               } else if (action instanceof CaptureValueAction captureValue) {
                  runCaptureValueAction(mc, captureValue);
               } else if (action instanceof WaitForChatAction wca) {
                  setCurrentStatus("Waiting for chat: " + wca.pattern);
                  awaitChat(wca);
               } else if (action instanceof WaitForEntityAction wea) {
                  setCurrentStatus(wea.getDisplayName());

                  try {
                     waitForPredicate(() -> wea.matchesEntityTarget(mc), wea.timeoutMs);
                  } catch (CancellationException var92) {
                  }
               } else if (action instanceof WaitForSoundAction wsa) {
                  String sndDesc = wsa.soundIds.isEmpty() ? "any" : wsa.soundIds.get(0);
                  setCurrentStatus("Waiting for sound: " + sndDesc);

                  try {
                     CompletableFuture<Void> future = MacroConditionRegistry.waitForSound(wsa);
                     waitForCondition(future);
                  } catch (CancellationException var91) {
                  }
               } else if (action instanceof WaitForSlotChangeAction wsca) {
                  setCurrentStatus(wsca.getDisplayName());

                  try {
                     CompletableFuture<Void> future = MacroConditionRegistry.waitForSlotChange(wsca);
                     waitForCondition(future);
                  } catch (CancellationException var90) {
                  }
               } else if (action instanceof WaitForWorldChangeAction world) {
                  setCurrentStatus(world.getDisplayName());

                  try {
                     CompletableFuture<?> future = raceConditionFuture(mc, world);
                     waitForFutureDone(future, 0L);
                  } catch (CancellationException var89) {
                  }
               } else if (action instanceof WaitForPositionDeltaAction wpda) {
                  setCurrentStatus(wpda.getDisplayName());

                  try {
                     CompletableFuture<Void> future = MacroConditionRegistry.waitForPositionDelta(wpda.distance, wpda.horizontalOnly);
                     waitForCondition(future);
                  } catch (CancellationException var88) {
                  }
               } else if (action instanceof WaitForTeleportAction teleport) {
                  setCurrentStatus(teleport.getDisplayName());

                  try {
                     CompletableFuture<?> future = raceConditionFuture(mc, teleport);
                     waitForFutureDone(future, 0L);
                  } catch (CancellationException var87) {
                  }
               } else if (action instanceof WaitGamemodeChangeAction gm) {
                  setCurrentStatus(gm.getDisplayName());

                  try {
                     CompletableFuture<Void> future = MacroConditionRegistry.waitForGamemodeChange(gm);
                     waitForFutureDone(future, gm.timeoutMs);
                  } catch (CancellationException var86) {
                  }
               } else if (action instanceof WaitMovementAction wm) {
                  setCurrentStatus(wm.getDisplayName());

                  try {
                     CompletableFuture<?> future = raceConditionFuture(mc, wm.resolveSubAction());
                     waitForFutureDone(future, 0L);
                  } catch (CancellationException var85) {
                  }
               } else if (action instanceof WaitForLanStepAction wla) {
                  setCurrentStatus(wla.getDisplayName());

                  try {
                     CompletableFuture<Void> future = MacroConditionRegistry.waitForLanStep(wla);
                     waitForCondition(future);
                  } catch (CancellationException var84) {
                  }
               } else if (action instanceof WaitForMacroStepAction wma) {
                  setCurrentStatus(wma.getDisplayName());
                  waitForMacroStep(wma);
               } else if (action instanceof GoToAction ga) {
                  runGoToAction(mc, ga);
               } else if (action instanceof MoveAction ma) {
                  if (ma.nonBlocking) {
                     mc.execute(() -> ma.execute(mc));
                     currentBackgroundMoves().put(ma, new int[]{ma.durationTicks - 1});
                  } else {
                     for (int t = 0; t < ma.durationTicks && isCurrentRunActive(); t++) {
                        mc.execute(() -> ma.execute(mc));
                        CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                        waitForCondition(tickFuture);
                     }

                     MacroExecutor.RunState movementRun = CURRENT_RUN.get();
                     mc.execute(() -> releaseMoveIfUnused(movementRun, ma, mc));
                  }
               } else if (action instanceof LookAtBlockAction la) {
                  LookAtBlockAction.RotationTarget rotationTarget = resolveLookAtTargetOnClient(mc, la);
                  if (rotationTarget == null) {
                     return;
                  }

                  if (la.smooth) {
                     startSmoothRotation(rotationTarget.yaw(), rotationTarget.pitch(), la.getRotationStep());
                     if (la.waitForCompletion) {
                        waitForSmoothRotationCompletion();
                     }
                  } else {
                     mc.execute(() -> {
                        if (mc.player != null) {
                           mc.player.setYRot(rotationTarget.yaw());
                           mc.player.setXRot(rotationTarget.pitch());
                        }
                     });
                  }
               } else if (action instanceof RotateAction ra) {
                  if (ra.smooth) {
                     startSmoothRotation(ra.yaw, ra.pitch, ra.getRotationStep());
                     if (ra.waitForCompletion) {
                        waitForSmoothRotationCompletion();
                     }
                  } else {
                     mc.execute(() -> action.execute(mc));
                  }
               } else if (action instanceof MineAction mna) {
                  runMineAction(mc, mna);
               } else if (action instanceof InstaBreakAction) {
                  runInstaBreakAction(mc, (InstaBreakAction)action);
               } else if (action instanceof BreakAction) {
                  runBreakAction(mc, (BreakAction)action);
               } else if (action instanceof PlaceAction) {
                  runPlaceAction(mc, (PlaceAction)action);
               } else if (action instanceof UseItemAction) {
                  runUseItemAction(mc, (UseItemAction)action);
               } else if (action instanceof JumpAction ja) {
                  mc.execute(() -> ja.execute(mc));
                  if (ja.tap) {
                     CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                     waitForCondition(tickFuture);
                  } else {
                     for (int t = 0; t < ja.durationTicks && isCurrentRunActive(); t++) {
                        CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                        waitForCondition(tickFuture);
                     }
                  }

                  mc.execute(() -> ja.release(mc));
               } else if (action instanceof SneakAction sna) {
                  if (sna.persistent && sna.sneak) {
                     currentPersistentActions().removeIf(p -> p instanceof SneakAction);
                     currentPersistentActions().add(sna);
                  } else if (!sna.sneak) {
                     currentPersistentActions().removeIf(p -> p instanceof SneakAction);
                  }

                  if (!sna.persistent && sna.sneak) {
                     postOnClientThread(mc, () -> sna.execute(mc), sna.isTickAligned());
                  } else {
                     postOnClientThread(mc, () -> applyAggregateSneakState(mc), sna.isTickAligned());
                  }
               } else if (action instanceof SprintAction spa) {
                  if (spa.persistent && spa.sprint) {
                     currentPersistentActions().removeIf(p -> p instanceof SprintAction);
                     currentPersistentActions().add(spa);
                  } else if (!spa.sprint) {
                     currentPersistentActions().removeIf(p -> p instanceof SprintAction);
                  }

                  if (!spa.persistent && spa.sprint) {
                     postOnClientThread(mc, () -> spa.execute(mc), spa.isTickAligned());
                  } else {
                     postOnClientThread(mc, () -> applyAggregateSprintState(mc), spa.isTickAligned());
                  }
               } else if (action instanceof NbtBookAction nba) {
                  int totalBooks = Math.max(1, nba.bookCount);
                  long delayMs = Math.max(1, nba.delayTicks) * 50L;
                  boolean signedAny = false;

                  for (int b = 0; b < totalBooks && isCurrentRunActive(); b++) {
                     int bookIdx = b;
                     CompletableFuture<Boolean> result = new CompletableFuture<>();
                     mc.execute(() -> result.complete(nba.executeSingleBook(mc, bookIdx, totalBooks)));

                     try {
                        Boolean success = result.get();
                        if (!success) {
                           break;
                        }

                        signedAny = true;
                     } catch (ExecutionException var97) {
                        break;
                     }

                     if (b < totalBooks - 1) {
                        Thread.sleep(delayMs);
                     }
                  }

                  if (signedAny && nba.disconnectAfter) {
                     mc.execute(() -> nba.afterSigning(mc));
                  }
               } else if (action instanceof ItemAction ia) {
                  if (ia.waitForItem) {
                     try {
                        waitForItemActionTargets(mc, ia);
                     } catch (CancellationException var83) {
                     }
                  }

                  postGuiFuture = createPostGuiFuture(mc, ia);
                  runMacroActionOnClientThread(mc, action, "ItemAction");
               } else if (!(action instanceof DropAction) && !(action instanceof SwapSlotsAction)) {
                  if (action instanceof SendPacketAction) {
                     if (module != null && module.useInstantExecutionMode()) {
                        try {
                           action.execute(mc);
                        } catch (Exception var82) {
                           RiptideClientMessaging.sendPrefixed("§cError in SendPacket: " + var82.getMessage());
                        }
                     } else {
                        mc.execute(() -> {
                           try {
                              action.execute(mc);
                           } catch (Exception var3x) {
                              RiptideClientMessaging.sendPrefixed("§cError in SendPacket: " + var3x.getMessage());
                           }
                        });
                     }
                  } else if (action instanceof ClickAction ca) {
                     runClickActionBurst(mc, ca);
                  } else if (action instanceof PayAction) {
                     runPayAction(mc, (PayAction)action);
                  } else if (action instanceof XCarryAction xsa) {
                     if (xsa.mode == XCarryAction.Mode.PUT_IN
                        && (xsa.transferMode == XCarryAction.TransferMode.CLICK || xsa.transferMode == XCarryAction.TransferMode.SAFE_CLICK)) {
                        if (xsa.transferMode == XCarryAction.TransferMode.SAFE_CLICK) {
                           setCurrentStatus("XCarry Safe");
                           runXCarrySafeClickPutIn(mc, xsa);
                        } else {
                           setCurrentStatus("XCarry Click");
                           runXCarryClickPutIn(mc, xsa);
                        }
                     } else {
                        setCurrentStatus(switch (xsa.mode) {
                           case PUT_IN -> "XCarry";
                           case TAKE_OUT -> "XCarry Out";
                           case DROP -> "XCarry Drop";
                        });
                        runOnClientThread(mc, () -> {
                           try {
                              xsa.execute(mc);
                           } catch (Exception var3x) {
                              RiptideClientMessaging.sendPrefixed("§cXCarry: " + var3x.getMessage());
                           }
                        });
                     }
                  } else if (action instanceof CraftAction) {
                     runCraftAction(mc, (CraftAction)action);
                  } else if (action instanceof StoreItemAction sia) {
                     setCurrentStatus((sia.mode == StoreItemAction.Mode.LOOT ? "Looting" : "Storing") + (sia.persistent ? " ∞" : ""));
                     if (sia.persistent) {
                        while (isCurrentRunActive()) {
                           try {
                              runStoreItemAction(mc, sia);
                           } catch (InterruptedException var100) {
                              Thread.currentThread().interrupt();
                              break;
                           } catch (Exception var101) {
                              RiptideClientMessaging.sendPrefixed("Store error: " + var101.getMessage());
                           }

                           CompletableFuture<Void> siTick = MacroConditionRegistry.waitForNextTick();
                           waitForCondition(siTick);
                        }
                     } else {
                        try {
                           runStoreItemAction(mc, sia);
                        } catch (InterruptedException var80) {
                           Thread.currentThread().interrupt();
                        } catch (Exception var81) {
                           RiptideClientMessaging.sendPrefixed("Store error: " + var81.getMessage());
                        }
                     }
                  } else if (action instanceof InventoryAuditAction auditAction
                     && (auditAction.mode == InventoryAuditAction.Mode.DUPE || auditAction.mode == InventoryAuditAction.Mode.DUPE_SPAM)) {
                     setCurrentStatus("Dupe: " + auditAction.dupeVector.name() + " " + auditAction.openMode.name());

                     try {
                        auditAction.executeDupe(mc);
                     } catch (InterruptedException var98) {
                        Thread.currentThread().interrupt();
                     } catch (Throwable var99) {
                        RiptideClientMessaging.sendPrefixed(
                           "§cDupe error: " + (var99.getMessage() == null ? var99.getClass().getSimpleName() : var99.getMessage())
                        );
                     }
                  } else if (action instanceof ContainerClickSequenceAction sequence) {
                     runContainerClickSequenceAction(mc, sequence);
                  } else if (action instanceof PacketBurstAction burst) {
                     runPacketBurstAction(mc, burst);
                  } else if (action instanceof TickSyncAction tsa) {
                     if (mc.level == null) {
                        return;
                     }

                     List<Packet<?>> preGenerated = preGeneratePacketsForRepeat(tsa.preGenCount, mc);
                     long targetTick = mc.level.getGameTime() + tsa.tickOffset;
                     setCurrentStatus("Tick Sync -> " + targetTick + " (" + preGenerated.size() + " pkts)");

                     while (isCurrentRunActive() && mc.level.getGameTime() < targetTick) {
                        LockSupport.parkNanos(1000000L);
                     }

                     if (isCurrentRunActive()) {
                        executePreGeneratedBurst(mc, preGenerated);
                        MacroExecutor.RepeatPacketContext context = REPEAT_PACKET_CONTEXT.get();
                        if (context != null) {
                           REPEAT_PACKET_SKIP.set(countPreGeneratedActions(context.macro(), context.startIdx(), preGenerated.size(), context.endIdx()));
                        }

                        RiptideClientMessaging.sendPrefixed("Tick sync: " + preGenerated.size() + " pkts sent");
                     }
                  } else if (action instanceof RevisionSyncAction rsa) {
                     if (mc.player == null || mc.player.containerMenu == null) {
                        return;
                     }

                     List<Packet<?>> preGenerated = preGeneratePacketsForRepeat(rsa.preGenCount, mc);
                     int baseRevision = mc.player.containerMenu.getStateId();
                     int targetRevision = baseRevision + rsa.revisionOffset;
                     setCurrentStatus("Rev Sync -> " + targetRevision + " (" + preGenerated.size() + " pkts)");

                     while (
                        isCurrentRunActive() && mc.player != null && mc.player.containerMenu != null && mc.player.containerMenu.getStateId() < targetRevision
                     ) {
                        LockSupport.parkNanos(500000L);
                     }

                     if (isCurrentRunActive() && mc.player != null && mc.player.containerMenu != null) {
                        executePreGeneratedBurst(mc, preGenerated);
                        MacroExecutor.RepeatPacketContext context = REPEAT_PACKET_CONTEXT.get();
                        if (context != null) {
                           REPEAT_PACKET_SKIP.set(countPreGeneratedActions(context.macro(), context.startIdx(), preGenerated.size(), context.endIdx()));
                        }

                        RiptideClientMessaging.sendPrefixed("Rev sync: " + preGenerated.size() + " pkts sent");
                     }
                  } else if (action instanceof ServerTickSyncAction stsa) {
                     if (mc.getConnection() == null) {
                        return;
                     }

                     List<Packet<?>> preGenerated = preGeneratePacketsForRepeat(stsa.preGenCount, mc);

                     for (int sampleWaitMs = 0; isCurrentRunActive() && !ServerTickTracker.isReady() && sampleWaitMs < stsa.maxWaitMs; sampleWaitMs += 50) {
                        float progress = ServerTickTracker.getWarmupProgress();
                        setCurrentStatus(
                           String.format(
                              "ServerSync warmup %.0f%% (%d samples, %dms)",
                              progress * 100.0F,
                              ServerTickTracker.getSampleCount(),
                              ServerTickTracker.getTrackingTimeMs()
                           )
                        );
                        Thread.sleep(50L);
                     }

                     if (!isCurrentRunActive()) {
                        return;
                     }

                     long optimalTime = ServerTickTracker.getOptimalSendTime(stsa.bufferMs, stsa.ignorePing);
                     long msUntil = (optimalTime - System.nanoTime()) / 1000000L;
                     int ping = ServerTickTracker.getPingMs();
                     String pingStr = stsa.ignorePing ? " (no ping)" : " ping:" + ping + "ms";
                     setCurrentStatus("ServerSync in " + Math.max(0L, msUntil) + "ms" + pingStr + " (" + preGenerated.size() + " pkts)");
                     long startWait = System.nanoTime();
                     long maxWaitNanos = stsa.maxWaitMs * 1000000L;

                     while (isCurrentRunActive()) {
                        long now = System.nanoTime();
                        if (now - startWait >= maxWaitNanos || now >= optimalTime) {
                           break;
                        }

                        long remaining = optimalTime - now;
                        if (remaining > 2000000L) {
                           LockSupport.parkNanos(remaining - 2000000L);
                        } else {
                           Thread.onSpinWait();
                        }
                     }

                     if (isCurrentRunActive()) {
                        executePreGeneratedBurst(mc, preGenerated);
                        MacroExecutor.RepeatPacketContext context = REPEAT_PACKET_CONTEXT.get();
                        if (context != null) {
                           REPEAT_PACKET_SKIP.set(countPreGeneratedActions(context.macro(), context.startIdx(), preGenerated.size(), context.endIdx()));
                        }

                        long actualDelay = (System.nanoTime() - optimalTime) / 1000000L;
                        RiptideClientMessaging.sendPrefixed("ServerSync: " + preGenerated.size() + " pkts (+" + actualDelay + "ms)");
                     }
                  } else if (action instanceof BundleDupeV2Action bundleV2) {
                     setCurrentStatus("Bundle Dupe V2");
                     bundleV2.execute(mc);
                  } else if (action instanceof RollbackAction rollback) {
                     setCurrentStatus("Rollback");

                     try {
                        rollback.executeRollback(mc);
                     } catch (InterruptedException var78) {
                        RiptideClientMessaging.sendPrefixed("§eRollback canceled safely.");
                     } catch (Exception var79) {
                        RiptideClientMessaging.sendPrefixed("§cRollback failed: " + safeThrowableMessage(var79));
                     }
                  } else if (action instanceof StopMacroAction stopAction) {
                     if (stopAction.target == StopMacroAction.StopTarget.SELF) {
                        stopCurrentRun();
                     } else {
                        stopAction.execute(mc);
                     }
                  } else if (action instanceof UseItemPhaseAction phaseAction) {
                     runUseItemPhaseAction(mc, phaseAction);
                  } else if (action instanceof ContextMacroAction contextAction) {
                     runContextMacroAction(contextAction);
                  } else if (usesTickAlignedDispatch(action)) {
                     runMacroActionOnClientThread(mc, action, action.getDisplayName());
                  } else if (module != null && module.useInstantExecutionMode()) {
                     CountDownLatch latch = new CountDownLatch(1);
                     MacroExecutor.RunState scheduledRun = CURRENT_RUN.get();
                     mc.execute(() -> withRunContext(scheduledRun, () -> {
                        try {
                           action.execute(mc);
                        } catch (Exception var7x) {
                           RiptideClientMessaging.sendPrefixed("§cError in action: " + var7x.getMessage());
                        } finally {
                           latch.countDown();
                        }
                     }));

                     try {
                        latch.await(100L, TimeUnit.MILLISECONDS);
                     } catch (InterruptedException var77) {
                        Thread.currentThread().interrupt();
                     }
                  } else {
                     runMacroActionOnClientThread(mc, action, action.getDisplayName());
                  }
               } else {
                  if (action instanceof DropAction dropAction) {
                     postGuiFuture = createPostGuiFuture(mc, dropAction);
                  }

                  runMacroActionOnClientThread(mc, action, action.getDisplayName());
               }

               commitConditionOutput(action, mc);
               if (postGuiFuture != null && isCurrentRunActive()) {
                  try {
                     waitForCondition(postGuiFuture);
                  } catch (CancellationException var76) {
                  }
               }

               if (!isCurrentRunActive() || isInstantInventoryAction(action) || module == null || !module.useInstantExecutionMode()) {
                  return;
               }

               int delayUs = module.getActionDelayUs();
               if (delayUs > 0) {
                  LockSupport.parkNanos(delayUs * 1000L);
               } else {
                  Thread.onSpinWait();
               }
            } finally {
               MacroRaycastAim.release();
               releaseClientResources(heldResources);
            }
         }
      }
   }

   private static void beginRaycastAim(Minecraft mc, MacroAction action) {
      if (action instanceof RaycastAim aim && aim.isRaycast()) {
         RiptideRotationUtil.Rotation rotation;
         try {
            rotation = callOnClientThread(mc, () -> MacroRaycastAim.aimFor(mc, aim.raycastTarget(mc)), null);
         } catch (InterruptedException var5) {
            Thread.currentThread().interrupt();
            return;
         }

         if (rotation != null) {
            MacroRaycastAim.hold(rotation);
            waitOneTick();
         }
      }
   }

   private static boolean dynamicTextInputsAvailable(MacroAction action, Minecraft mc) {
      if (action instanceof WaitForChatAction chat && chat.effectiveMatchMode() == MacroCapturePattern.Mode.MATCH) {
         return MacroVariables.resolve(chat.pattern, mc).success();
      } else if (action instanceof WaitForGuiAction gui && gui.matchMode == MacroCapturePattern.Mode.MATCH) {
         return MacroVariables.resolve(gui.guiTitle, mc).success();
      } else if (action instanceof WaitGuiTypeAction gui && gui.matchMode == MacroCapturePattern.Mode.MATCH) {
         return MacroVariables.resolve(gui.title, mc).success();
      } else if (action instanceof WaitInventoryPredicateAction inventory) {
         return MacroVariables.resolve(inventory.itemName, mc).success();
      } else if (action instanceof WaitDurabilityAction durability && durability.targetMode == WaitDurabilityAction.TargetMode.ITEM) {
         return MacroVariables.resolve(durability.itemName, mc).success();
      } else {
         return action instanceof WaitEntityTargetAction entity ? MacroVariables.resolve(entity.entityId, mc).success() : true;
      }
   }

   private static List<Packet<?>> preGeneratePacketsForRepeat(int count, Minecraft mc) {
      MacroExecutor.RepeatPacketContext context = REPEAT_PACKET_CONTEXT.get();
      return (List<Packet<?>>)(context != null && context.macro() != null
         ? preGeneratePackets(context.macro(), context.startIdx(), count, context.endIdx())
         : new ArrayList<>());
   }

   private static boolean isTargetWaitInteractionAction(MacroAction action) {
      return action instanceof OpenContainerAction || action instanceof InteractEntityAction;
   }

   private static CompletableFuture<Void> runTargetWaitInteractionAction(Minecraft mc, MacroAction action) throws InterruptedException {
      boolean waitForTarget = switch (action) {
         case OpenContainerAction open -> open.waitForTarget;
         case InteractEntityAction interact -> interact.waitForTarget;
         default -> false;
      };
      if (!waitForTarget) {
         CompletableFuture<Void> postGuiFuture = createPostGuiFuture(mc, action);
         runTargetInteractionAttempt(mc, action);
         return postGuiFuture;
      } else {
         setCurrentStatus("Waiting target: " + action.getDisplayName());

         while (isCurrentRunActive()) {
            if (!canRunTargetInteractionNow(mc, action)) {
               LockSupport.parkNanos(500000L);
               if (Thread.interrupted()) {
                  throw new InterruptedException();
               }
            } else {
               CompletableFuture<Void> postGuiFuture = createPostGuiFuture(mc, action);
               if (runTargetInteractionAttempt(mc, action)) {
                  return postGuiFuture;
               }

               if (postGuiFuture != null) {
                  postGuiFuture.cancel(true);
               }

               LockSupport.parkNanos(500000L);
               if (Thread.interrupted()) {
                  throw new InterruptedException();
               }
            }
         }

         return null;
      }
   }

   private static boolean canRunTargetInteractionNow(Minecraft mc, MacroAction action) throws InterruptedException {
      return callOnClientThread(mc, () -> {
         if (action instanceof OpenContainerAction open) {
            return open.canExecuteNow(mc);
         } else {
            return action instanceof InteractEntityAction interact ? interact.canExecuteNow(mc) : true;
         }
      }, Boolean.FALSE);
   }

   private static boolean runTargetInteractionAttempt(Minecraft mc, MacroAction action) throws InterruptedException {
      return callOnClientThread(mc, () -> {
         try {
            if (action instanceof OpenContainerAction open) {
               return open.tryExecute(mc);
            } else if (action instanceof InteractEntityAction interact) {
               return interact.tryExecute(mc);
            } else {
               action.execute(mc);
               return true;
            }
         } catch (Exception var3) {
            RiptideClientMessaging.sendPrefixed("§cError in " + action.getDisplayName() + ": " + var3.getMessage());
            return true;
         }
      }, Boolean.FALSE, usesTickAlignedDispatch(action));
   }

   private static CompletableFuture<Void> createPostGuiFuture(Minecraft mc, MacroAction action) {
      if (Boolean.TRUE.equals(SUPPRESS_POST_GUI_AFTER.get())) {
         return null;
      } else {
         return action instanceof WaitsForGui wfg && wfg.isWaitForGuiAfter() ? createWaitsForGuiFuture(mc, wfg) : null;
      }
   }

   private static CompletableFuture<Void> createWaitsForGuiFuture(Minecraft mc, WaitsForGui wfg) {
      MacroTemplate.Resolution guiName = MacroVariables.resolve(wfg.getWaitGuiName(), mc);
      if (!guiName.success()) {
         return CompletableFuture.completedFuture(null);
      } else {
         return wfg.isWaitForGuiChange() ? MacroConditionRegistry.waitForGuiChange(mc.gui.screen()) : MacroConditionRegistry.waitForGui(guiName.value());
      }
   }

   private static boolean waitForGuiBeforeAction(Minecraft mc, MacroAction action) throws InterruptedException {
      return action instanceof WaitsForGui wfg ? waitForGuiBeforeAction(mc, wfg) : true;
   }

   private static boolean waitForGuiBeforeAction(Minecraft mc, WaitsForGui action) throws InterruptedException {
      if (mc != null && action != null && action.isWaitForGuiBefore()) {
         MacroTemplate.Resolution guiName = MacroVariables.resolve(action.getWaitGuiName(), mc);
         if (!guiName.success()) {
            return false;
         } else if (isWaitGuiPresent(mc, guiName.value())) {
            waitForCurrentGuiReady(mc);
            return true;
         } else {
            CompletableFuture<Void> future = MacroConditionRegistry.waitForGui(guiName.value());
            waitForCondition(future);
            waitForCurrentGuiReady(mc);
            return true;
         }
      } else {
         return true;
      }
   }

   private static boolean isWaitGuiPresent(Minecraft mc, String guiName) {
      if (mc == null || mc.gui.screen() == null) {
         return false;
      } else if (MacroGuiMatcher.isOwnScreen(mc.gui.screen())) {
         return false;
      } else {
         String expected = guiName == null ? "" : guiName.trim();
         if (expected.isEmpty()) {
            return true;
         } else {
            String actual = mc.gui.screen().getTitle() == null ? "" : mc.gui.screen().getTitle().getString();
            return matchesWaitGuiTitle(expected, actual);
         }
      }
   }

   private static boolean matchesWaitGuiTitle(String expected, String actual) {
      if (expected != null && !expected.isBlank() && actual != null && !actual.isBlank()) {
         String expectedLower = expected.toLowerCase(Locale.ROOT);
         String actualLower = actual.toLowerCase(Locale.ROOT);
         if (!expectedLower.equals(actualLower) && !actualLower.contains(expectedLower)) {
            for (String word : expectedLower.split("\\s+")) {
               if (!word.isBlank() && !actualLower.contains(word)) {
                  return false;
               }
            }

            return true;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   private static void waitForCurrentGuiReady(Minecraft mc) throws InterruptedException {
      long deadline = System.nanoTime() + 500000000L;

      while (isCurrentRunActive()) {
         Boolean ready = callOnClientThread(mc, () -> {
            if (mc.player == null || mc.gui.screen() == null) {
               return false;
            } else {
               return mc.player.containerMenu != null && mc.player.containerMenu != mc.player.inventoryMenu ? mc.player.containerMenu.getStateId() > 0 : true;
            }
         }, Boolean.FALSE);
         if (Boolean.TRUE.equals(ready)) {
            return;
         }

         if (System.nanoTime() >= deadline) {
            return;
         }

         LockSupport.parkNanos(500000L);
         if (Thread.interrupted()) {
            return;
         }
      }
   }

   private static void runMacroActionOnClientThread(Minecraft mc, MacroAction action, String label) throws InterruptedException {
      Runnable body = () -> {
         try {
            action.execute(mc);
         } catch (Throwable var4) {
            recordAddonActionError(action, "Action threw " + var4.getClass().getSimpleName());
            RiptideClientMessaging.sendPrefixed(
               "§cError in " + label + ": " + (var4.getMessage() == null ? var4.getClass().getSimpleName() : var4.getMessage())
            );
         }
      };
      if (usesTickAlignedDispatch(action)) {
         runOnClientTick(mc, body);
      } else {
         runOnClientThread(mc, body);
      }
   }

   private static boolean usesTickAlignedDispatch(MacroAction action) {
      return action instanceof PacketOrdered ordered && ordered.isTickAligned();
   }

   private static void runItemActionPaced(Minecraft mc, ItemAction ia) throws InterruptedException {
      setCurrentStatus(ia.getDisplayName());
      List<Runnable> plan = callOnClientThread(mc, () -> ia.buildClickPlan(mc), Collections.emptyList());
      if (plan != null && !plan.isEmpty()) {
         long delayMs = Math.max(0, ia.clickDelayMs);
         MacroExecutor.RunState scheduledRun = CURRENT_RUN.get();
         boolean first = true;

         for (Runnable op : plan) {
            if (!isCurrentRunActive()) {
               break;
            }

            if (!first && delayMs > 0L) {
               Thread.sleep(delayMs);
            }

            first = false;
            mc.execute(
               () -> withRunContext(
                  scheduledRun,
                  () -> {
                     if (!PackHideState.isHardLocked()) {
                        try {
                           op.run();
                        } catch (Throwable var2x) {
                           RiptideClientMessaging.sendPrefixed(
                              "§cError in ItemAction: " + (var2x.getMessage() == null ? var2x.getClass().getSimpleName() : var2x.getMessage())
                           );
                        }
                     }
                  }
               )
            );
         }
      }
   }

   private static void runContextMacroAction(ContextMacroAction action) {
      if (!PackHideState.isHardLocked()) {
         if (action != null) {
            try {
               action.run(new MacroExecutor.ApiExecutionContext(CURRENT_RUN.get()));
            } catch (InterruptedException var2) {
               Thread.currentThread().interrupt();
            } catch (Throwable var3) {
               recordAddonActionError(action, "Context action threw " + var3.getClass().getSimpleName());
               RiptideClientMessaging.sendPrefixed("§cAddon action error: " + (var3.getMessage() == null ? var3.getClass().getSimpleName() : var3.getMessage()));
            }
         }
      }
   }

   private static void recordAddonActionError(MacroAction action, String detail) {
      if (action != null) {
         String owner = MacroActionRegistry.ownerOf(action.getTypeId());
         if (owner != null && !owner.isBlank()) {
            AddonManager.recordRuntimeError(owner, detail);
         }
      }
   }

   private static void runUseItemPhaseAction(Minecraft mc, UseItemPhaseAction action) throws InterruptedException {
      int times = action.repeatTimes();

      for (int i = 0; i < times && isCurrentRunActive(); i++) {
         runOnClientThread(mc, () -> {
            try {
               action.sendUsePacket(mc);
            } catch (Exception var3x) {
               RiptideClientMessaging.sendPrefixed("§cError in Use phase: " + var3x.getMessage());
            }
         });
         boolean[] gateInstalled = new boolean[]{false};
         if (action.shouldGate()) {
            runOnClientThread(mc, () -> {
               try {
                  gateInstalled[0] = action.installGate(mc);
               } catch (Exception var4x) {
                  RiptideClientMessaging.sendPrefixed("§cError in Use gate: " + var4x.getMessage());
               }
            });
         }

         try {
            for (int t = 0; t < action.holdTicks && isCurrentRunActive(); t++) {
               CompletableFuture<Void> tf = MacroConditionRegistry.waitForNextTick();
               waitForCondition(tf);
            }
         } finally {
            if (gateInstalled[0]) {
               boolean wasOnThread = mc.isSameThread();
               if (wasOnThread) {
                  action.removeGate();
               } else {
                  runOnClientThread(mc, action::removeGate);
               }
            }
         }

         if (!isCurrentRunActive()) {
            break;
         }

         runOnClientThread(mc, () -> {
            try {
               action.finishRelease(mc);
            } catch (Exception var3x) {
               RiptideClientMessaging.sendPrefixed("§cError in Use release: " + var3x.getMessage());
            }
         });
      }
   }

   private static void runClickActionBurst(Minecraft mc, ClickAction action) throws InterruptedException {
      int times = Math.max(1, action.clickCount);
      runOnClientThread(mc, () -> {
         for (int c = 0; c < times && isCurrentRunActive(); c++) {
            try {
               action.execute(mc);
            } catch (Exception var5) {
               RiptideClientMessaging.sendPrefixed("§cError in Click: " + var5.getMessage());
               break;
            }
         }
      }, action.isTickAligned());
   }

   private static boolean isInstantInventoryAction(MacroAction action) {
      return !(action instanceof ItemAction)
            && !(action instanceof DropAction)
            && !(action instanceof SwapSlotsAction)
            && !(action instanceof ClickAction)
            && !(action instanceof XCarryAction)
         ? action instanceof StoreItemAction storeItemAction && !storeItemAction.persistent
         : true;
   }

   private static void runStoreItemAction(Minecraft mc, StoreItemAction action) throws InterruptedException {
      if (action != null) {
         int delayTicks = StoreItemAction.clampDelayTicks(action.delayTicks);
         if (delayTicks <= 0) {
            runOnClientThread(mc, () -> action.doTransfer(mc));
         } else {
            List<Integer> slots = callOnClientThread(mc, () -> action.collectTransferSlots(mc), List.of());

            for (int i = 0; i < slots.size() && isCurrentRunActive(); i++) {
               RiptideInventoryClickHelper.click(mc, slots.get(i), 0, ContainerInput.QUICK_MOVE);
               if (i + 1 < slots.size()) {
                  waitTicks(delayTicks);
               }
            }

            if (action.closeAfter && !action.persistent) {
               runOnClientThread(mc, () -> RiptideGuiActions.closeCurrentScreen(mc, action.closeSendPkt, false));
            }
         }
      }
   }

   private static void waitTicks(int ticks) {
      for (int i = 0; i < ticks && isCurrentRunActive(); i++) {
         CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
         waitForCondition(tickFuture);
      }
   }

   private static void runUseItemAction(Minecraft mc, UseItemAction action) throws InterruptedException {
      if (action != null) {
         boolean sneaking = action.sneak;

         try {
            if (action.useMode == UseItemAction.UseMode.CUSTOM_HOLD) {
               runOnClientThread(mc, () -> {
                  try {
                     if (sneaking) {
                        RiptideMacroSneak.forceSneak(mc, true);
                     }

                     action.execute(mc);
                  } catch (Exception var4x) {
                     RiptideClientMessaging.sendPrefixed("§cError in UseItem: " + var4x.getMessage());
                  }
               }, action.isTickAligned());
               if (action.holdTicks <= 0) {
                  return;
               }

               waitTicks(action.holdTicks);
               if (isCurrentRunActive()) {
                  runOnClientThread(mc, () -> {
                     try {
                        action.sendRelease(mc);
                     } catch (Exception var3x) {
                        RiptideClientMessaging.sendPrefixed("§cError in UseItem release: " + var3x.getMessage());
                     }
                  }, action.isTickAligned());
                  if (action.waitForFinish) {
                     waitForUseItemFinish(mc, action.holdTicks + 8);
                  }

                  return;
               }

               return;
            }

            int times = Math.max(1, action.useCount);

            for (int i = 0; i < times && isCurrentRunActive(); i++) {
               runOnClientThread(mc, () -> {
                  try {
                     if (sneaking) {
                        RiptideMacroSneak.forceSneak(mc, true);
                     }

                     action.execute(mc);
                  } catch (Exception var4x) {
                     RiptideClientMessaging.sendPrefixed("§cError in UseItem: " + var4x.getMessage());
                  }
               }, action.isTickAligned());
               if (action.waitForFinish) {
                  waitForUseItemFinish(mc, 0);
               }

               if (i + 1 < times && isCurrentRunActive()) {
                  waitOneTick();
               }
            }
         } finally {
            if (sneaking) {
               mc.execute(() -> RiptideMacroSneak.forceSneak(mc, false));
            }
         }
      }
   }

   private static void runContainerClickSequenceAction(Minecraft mc, ContainerClickSequenceAction action) throws InterruptedException {
      if (action != null) {
         List<Integer> slots = action.resolvedSlots();
         int repeats = Math.max(1, action.repeatCount);
         int delay = Math.max(0, action.delayTicks);
         int total = repeats * slots.size();
         int sent = 0;

         for (int repeat = 0; repeat < repeats && isCurrentRunActive(); repeat++) {
            for (int slot : slots) {
               if (!isCurrentRunActive()) {
                  return;
               }

               runOnClientThread(mc, () -> action.executeClick(mc, slot));
               if (delay > 0 && ++sent < total) {
                  waitTicks(delay);
               }
            }
         }
      }
   }

   private static void runPacketBurstAction(Minecraft mc, PacketBurstAction action) {
      if (action != null && mc != null && mc.getConnection() != null) {
         if (action.flushBefore) {
            RiptideSharedState.get().flushDelayedPackets(mc.getConnection());
         }

         int total = action.effectiveCount();
         int delay = Math.max(0, action.delayTicks);

         for (int i = 0; i < total && isCurrentRunActive(); i++) {
            action.sendOne(mc);
            if (delay > 0 && i + 1 < total) {
               waitTicks(delay);
            }
         }
      }
   }

   private static void waitForUseItemFinish(Minecraft mc, int fallbackTicks) throws InterruptedException {
      if (mc != null && isCurrentRunActive()) {
         if (Boolean.TRUE.equals(callOnClientThread(mc, () -> mc.player != null && mc.player.isUsingItem(), Boolean.FALSE))) {
            int remaining = callOnClientThread(mc, () -> mc.player == null ? 0 : Math.max(0, mc.player.getUseItemRemainingTicks()), 0);
            int maxTicks = Math.max(8, remaining > 0 ? remaining + 8 : fallbackTicks + 8);
            maxTicks = Math.min(maxTicks, 72000);

            for (int i = 0; i < maxTicks && isCurrentRunActive(); i++) {
               if (!Boolean.TRUE.equals(callOnClientThread(mc, () -> mc.player != null && mc.player.isUsingItem(), Boolean.FALSE))) {
                  return;
               }

               CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
               waitForCondition(tickFuture);
            }
         }
      }
   }

   private static void runXCarryClickPutIn(Minecraft mc, XCarryAction xsa) throws InterruptedException {
      if (mc != null && mc.player != null && mc.gameMode != null) {
         if (xsa.requiresCursorStorage()) {
            setCurrentStatus("XCarry Cursor");
            runOnClientThread(mc, () -> xsa.execute(mc));
         } else {
            RiptideSharedState sharedState = RiptideSharedState.get();
            boolean prevBypass = sharedState.isXCarryArmorBypass();
            sharedState.setXCarryArmorBypass(true);

            try {
               AtomicReference<AbstractContainerMenu> containerHandlerRef = new AtomicReference<>();
               AtomicReference<RiptideContainerTarget> containerTargetRef = new AtomicReference<>();
               AtomicReference<List<Integer>> containerSlotIdsRef = new AtomicReference<>(List.of());
               AtomicBoolean usingContainer = new AtomicBoolean(false);
               runOnClientThread(mc, () -> {
                  if (mc.player != null) {
                     AbstractContainerMenu current = mc.player.containerMenu;
                     if (current != null && current != mc.player.inventoryMenu) {
                        usingContainer.set(true);
                        containerHandlerRef.set(current);
                        containerTargetRef.set(RiptideSharedState.get().getLastContainerTarget());
                        containerSlotIdsRef.set(new ArrayList<>(xsa.collectContainerTransferSlots(current, mc.player.inventoryMenu)));
                     }
                  }
               });
               if (usingContainer.get() && containerTargetRef.get() == null) {
                  RiptideClientMessaging.sendPrefixed("XCarry click mode: missing container target, using fast mode.");
                  runOnClientThread(mc, () -> xsa.execute(mc));
               } else {
                  if (usingContainer.get()) {
                     for (int slotId : containerSlotIdsRef.get()) {
                        if (!isCurrentRunActive()) {
                           return;
                        }

                        setCurrentStatus("XCarry Click: collect");
                        runOnClientThread(mc, () -> {
                           if (mc.player != null && mc.gameMode != null) {
                              AbstractContainerMenu handler = containerHandlerRef.get();
                              if (handler != null && mc.player.containerMenu == handler) {
                                 if (slotId >= 0 && slotId < handler.slots.size()) {
                                    if (!((Slot)handler.slots.get(slotId)).getItem().isEmpty()) {
                                       mc.gameMode.handleContainerInput(handler.containerId, slotId, 0, ContainerInput.QUICK_MOVE, mc.player);
                                    }
                                 }
                              }
                           }
                        });
                        waitOneTick();
                     }

                     runOnClientThread(mc, () -> {
                        if (mc.player != null && mc.getConnection() != null) {
                           AbstractContainerMenu handler = containerHandlerRef.get();
                           if (handler != null) {
                              mc.getConnection().send(new ServerboundContainerClosePacket(handler.containerId));
                           }
                        }
                     });
                     waitOneTick();
                     runOnClientThread(mc, () -> {
                        if (mc.player != null) {
                           mc.player.containerMenu = mc.player.inventoryMenu;
                        }
                     });
                     waitOneTick();
                  }

                  runOnClientThread(mc, xsa::preparePutInStorageGuard);
                  int xCarryAttempts = 0;

                  while (isCurrentRunActive() && xCarryAttempts++ < 512) {
                     XCarryAction.PutInMove move = callOnClientThread(mc, () -> mc.player == null ? null : xsa.findNextPutInMove(mc.player.inventoryMenu), null);
                     if (move == null) {
                        break;
                     }

                     setCurrentStatus("XCarry Click -> " + move.targetSlotId + " x" + move.count);
                     int targetBefore = callOnClientThread(
                        mc,
                        () -> {
                           if (mc.player == null) {
                              return 0;
                           } else {
                              InventoryMenu handler = mc.player.inventoryMenu;
                              return move.targetSlotId >= 0 && move.targetSlotId < handler.slots.size()
                                 ? ((Slot)handler.slots.get(move.targetSlotId)).getItem().getCount()
                                 : 0;
                           }
                        },
                        0
                     );
                     boolean moved = callOnClientThread(mc, () -> {
                        if (mc.player != null && mc.gameMode != null) {
                           InventoryMenu handler = mc.player.inventoryMenu;
                           mc.player.containerMenu = handler;
                           return XCarryAction.movePlannedCount(handler, mc, move.sourceSlotId, move.targetSlotId, move.count);
                        } else {
                           return false;
                        }
                     }, Boolean.FALSE);
                     if (!moved) {
                        break;
                     }

                     boolean placed = waitForXCarryCondition(
                        mc,
                        500L,
                        () -> {
                           if (mc.player == null) {
                              return false;
                           } else {
                              InventoryMenu handler = mc.player.inventoryMenu;
                              return move.targetSlotId >= 0 && move.targetSlotId < handler.slots.size()
                                 ? handler.getCarried().isEmpty()
                                    && ((Slot)handler.slots.get(move.targetSlotId)).getItem().getCount() >= targetBefore + move.count
                                 : false;
                           }
                        }
                     );
                     if (!placed) {
                        rollbackXCarryCursor(mc, move.sourceSlotId);
                        break;
                     }

                     waitOneTick();
                  }

                  runOnClientThread(mc, () -> {
                     if (mc.player != null) {
                        boolean active = XCarryAction.hasStoredItems(mc.player.inventoryMenu, xsa.carryCursor, xsa.activeStorageSlotIds());
                        RiptideSharedState shared = RiptideSharedState.get();
                        if (active) {
                           shared.mergeXCarryForcedTargets(xsa.activeStorageSlotIds(), xsa.carryCursor);
                        }

                        shared.setXCarryForced(active);
                        shared.setXCarryActive(active);
                     }
                  });
                  if (usingContainer.get()) {
                     runOnClientThread(mc, () -> {
                        if (mc.player != null) {
                           AbstractContainerMenu containerHandler = containerHandlerRef.get();
                           if (containerHandler != null) {
                              mc.player.containerMenu = containerHandler;
                           }

                           RiptideContainerTarget containerTarget = containerTargetRef.get();
                           if (containerTarget != null) {
                              XCarryAction.sendOpenTarget(mc, containerTarget);
                           }
                        }
                     });
                     waitOneTick();
                  }
               }
            } finally {
               sharedState.setXCarryArmorBypass(prevBypass);
            }
         }
      }
   }

   private static void runXCarrySafeClickPutIn(Minecraft mc, XCarryAction xsa) throws InterruptedException {
      if (mc != null && mc.player != null && mc.gameMode != null) {
         if (xsa.requiresCursorStorage()) {
            setCurrentStatus("XCarry Safe: Cursor");
            runOnClientThread(mc, () -> xsa.execute(mc));
         } else {
            RiptideSharedState sharedState = RiptideSharedState.get();
            boolean prevBypass = sharedState.isXCarryArmorBypass();
            sharedState.setXCarryArmorBypass(true);

            try {
               AtomicReference<AbstractContainerMenu> containerHandlerRef = new AtomicReference<>();
               AtomicReference<RiptideContainerTarget> containerTargetRef = new AtomicReference<>();
               AtomicReference<List<Integer>> containerSlotIdsRef = new AtomicReference<>(List.of());
               AtomicBoolean usingContainer = new AtomicBoolean(false);
               runOnClientThread(mc, () -> {
                  if (mc.player != null) {
                     AbstractContainerMenu current = mc.player.containerMenu;
                     if (current != null && current != mc.player.inventoryMenu) {
                        usingContainer.set(true);
                        containerHandlerRef.set(current);
                        containerTargetRef.set(RiptideSharedState.get().getLastContainerTarget());
                        containerSlotIdsRef.set(new ArrayList<>(xsa.collectContainerTransferSlots(current, mc.player.inventoryMenu)));
                     }
                  }
               });
               if (usingContainer.get() && containerTargetRef.get() == null) {
                  RiptideClientMessaging.sendPrefixed("XCarry safe mode: missing container target, using fast mode.");
                  runOnClientThread(mc, () -> xsa.execute(mc));
               } else {
                  if (usingContainer.get()) {
                     for (int slotId : containerSlotIdsRef.get()) {
                        if (!isCurrentRunActive()) {
                           return;
                        }

                        setCurrentStatus("XCarry Safe: collect");
                        sendContainerClickThenTick(mc, containerHandlerRef.get(), slotId, 0, ContainerInput.QUICK_MOVE);
                     }

                     runOnClientThread(mc, () -> {
                        if (mc.player != null && mc.getConnection() != null) {
                           AbstractContainerMenu handler = containerHandlerRef.get();
                           if (handler != null) {
                              mc.getConnection().send(new ServerboundContainerClosePacket(handler.containerId));
                           }
                        }
                     });
                     waitOneTick();
                     runOnClientThread(mc, () -> {
                        if (mc.player != null) {
                           mc.player.containerMenu = mc.player.inventoryMenu;
                        }
                     });
                     waitOneTick();
                  }

                  runOnClientThread(mc, xsa::preparePutInStorageGuard);
                  int safeDelayTicks = XCarryAction.clampSafeClickDelayTicks(xsa.safeClickDelayTicks);
                  boolean delayAfterPickup = xsa.safeClickDelayAfterPickup;
                  boolean delayBeforeReturn = xsa.safeClickDelayBeforeReturn;
                  int attempts = 0;

                  while (isCurrentRunActive() && attempts++ < 512) {
                     List<XCarryAction.PutInMove> group = callOnClientThread(mc, () -> nextXCarrySafeGroup(mc, xsa), List.of());
                     if (group.isEmpty()) {
                        break;
                     }

                     XCarryAction.PutInMove first = group.get(0);
                     int total = 0;

                     for (XCarryAction.PutInMove move : group) {
                        total += Math.max(0, move.count());
                     }

                     setCurrentStatus("XCarry Safe -> " + group.size() + " slots x" + total);
                     if (!executeXCarrySafeGroup(mc, group, safeDelayTicks, delayAfterPickup, delayBeforeReturn)) {
                        rollbackXCarryCursorSafe(mc, first.sourceSlotId(), safeDelayTicks, delayBeforeReturn);
                        break;
                     }
                  }

                  runOnClientThread(mc, () -> {
                     if (mc.player != null) {
                        boolean active = XCarryAction.hasStoredItems(mc.player.inventoryMenu, xsa.carryCursor, xsa.activeStorageSlotIds());
                        RiptideSharedState shared = RiptideSharedState.get();
                        if (active) {
                           shared.mergeXCarryForcedTargets(xsa.activeStorageSlotIds(), xsa.carryCursor);
                        }

                        shared.setXCarryForced(active);
                        shared.setXCarryActive(active);
                     }
                  });
                  if (usingContainer.get()) {
                     runOnClientThread(mc, () -> {
                        if (mc.player != null) {
                           AbstractContainerMenu containerHandler = containerHandlerRef.get();
                           if (containerHandler != null) {
                              mc.player.containerMenu = containerHandler;
                           }

                           RiptideContainerTarget containerTarget = containerTargetRef.get();
                           if (containerTarget != null) {
                              XCarryAction.sendOpenTarget(mc, containerTarget);
                           }
                        }
                     });
                     waitOneTick();
                  }
               }
            } finally {
               sharedState.setXCarryArmorBypass(prevBypass);
            }
         }
      }
   }

   private static List<XCarryAction.PutInMove> nextXCarrySafeGroup(Minecraft mc, XCarryAction xsa) {
      if (mc != null && mc.player != null && xsa != null) {
         InventoryMenu handler = mc.player.inventoryMenu;
         List<XCarryAction.PutInMove> planned = xsa.planPutInMoves(handler);
         if (planned.isEmpty()) {
            return List.of();
         } else {
            XCarryAction.PutInMove first = planned.get(0);
            if (first == null) {
               return List.of();
            } else if (first.sourceSlotId() >= 0 && first.sourceSlotId() < handler.slots.size()) {
               ItemStack source = ((Slot)handler.slots.get(first.sourceSlotId())).getItem();
               if (source.isEmpty()) {
                  return List.of();
               } else {
                  ArrayList<XCarryAction.PutInMove> group = new ArrayList<>();
                  int remainingSource = source.getCount();

                  for (XCarryAction.PutInMove move : planned) {
                     if (move == null
                        || move.sourceSlotId() != first.sourceSlotId()
                        || move.count() <= 0
                        || move.count() > remainingSource
                        || !canSafelyPlaceXCarryMove(handler, source, move)) {
                        break;
                     }

                     group.add(move);
                     remainingSource -= move.count();
                  }

                  return group;
               }
            } else {
               return List.of();
            }
         }
      } else {
         return List.of();
      }
   }

   private static boolean executeXCarrySafeGroup(
      Minecraft mc, List<XCarryAction.PutInMove> group, int delayTicks, boolean delayAfterPickup, boolean delayBeforeReturn
   ) throws InterruptedException {
      if (mc != null && mc.player != null && mc.gameMode != null && group != null && !group.isEmpty()) {
         MacroExecutor.SafeGroupSnapshot snapshot = callOnClientThread(mc, () -> snapshotXCarrySafeGroup(mc, group), MacroExecutor.SafeGroupSnapshot.invalid());
         if (!snapshot.valid) {
            return false;
         } else {
            MacroExecutor.SafeGroupSnapshot expected = executeXCarrySafeScript(mc, group, snapshot, delayTicks, delayAfterPickup, delayBeforeReturn);
            return expected.valid;
         }
      } else {
         return false;
      }
   }

   private static MacroExecutor.SafeGroupSnapshot executeXCarrySafeScript(
      Minecraft mc,
      List<XCarryAction.PutInMove> group,
      MacroExecutor.SafeGroupSnapshot snapshot,
      int delayTicks,
      boolean delayAfterPickup,
      boolean delayBeforeReturn
   ) throws InterruptedException {
      if (mc != null && mc.player != null && mc.gameMode != null && group != null && !group.isEmpty() && snapshot != null && snapshot.valid) {
         XCarryAction.PutInMove first = group.get(0);
         if (!Boolean.TRUE.equals(callOnClientThread(mc, () -> {
            InventoryMenu handler = mc.player == null ? null : mc.player.inventoryMenu;
            return handler != null && handler.getCarried().isEmpty() && xcarrySafeSnapshotStillMatches(handler, group, snapshot);
         }, Boolean.FALSE))) {
            return MacroExecutor.SafeGroupSnapshot.invalid();
         } else {
            MacroExecutor.SafeQuickCraftPlan quickCraftPlan = callOnClientThread(mc, () -> {
               InventoryMenu handler = mc.player == null ? null : mc.player.inventoryMenu;
               return createXCarryQuickCraftPlan(handler, group, snapshot);
            }, MacroExecutor.SafeQuickCraftPlan.invalid());
            MacroExecutor.SafeGroupSnapshot expected = snapshot;
            if (quickCraftPlan.valid) {
               setCurrentStatus("XCarry Safe drag -> " + group.size() + " slots x" + quickCraftPlan.placeCount);
               int start = AbstractContainerMenu.getQuickcraftMask(0, quickCraftPlan.quickCraftType);
               int add = AbstractContainerMenu.getQuickcraftMask(1, quickCraftPlan.quickCraftType);
               int end = AbstractContainerMenu.getQuickcraftMask(2, quickCraftPlan.quickCraftType);
               int nextDragIndex = 0;
               if (delayAfterPickup) {
                  if (!sendXCarrySafeInventoryClick(mc, first.sourceSlotId(), 0, ContainerInput.PICKUP)) {
                     return MacroExecutor.SafeGroupSnapshot.invalid();
                  }

                  waitXCarrySafeAfterPickupDelay(delayTicks, true);
                  if (!Boolean.TRUE.equals(callOnClientThread(mc, () -> {
                     InventoryMenu handler = mc.player == null ? null : mc.player.inventoryMenu;
                     return xcarrySafeCarryingNow(handler, snapshot.sourceStack, snapshot.sourceCount);
                  }, Boolean.FALSE))) {
                     return MacroExecutor.SafeGroupSnapshot.invalid();
                  }

                  if (!sendXCarrySafeInventoryClick(mc, -999, start, ContainerInput.QUICK_CRAFT)) {
                     return MacroExecutor.SafeGroupSnapshot.invalid();
                  }
               } else {
                  ArrayList<MacroExecutor.SafeInventoryClick> dragClicks = new ArrayList<>(group.size() + 3);
                  dragClicks.add(new MacroExecutor.SafeInventoryClick(first.sourceSlotId(), 0, ContainerInput.PICKUP));
                  dragClicks.add(new MacroExecutor.SafeInventoryClick(-999, start, ContainerInput.QUICK_CRAFT));

                  for (XCarryAction.PutInMove move : group) {
                     if (move != null) {
                        dragClicks.add(new MacroExecutor.SafeInventoryClick(move.targetSlotId(), add, ContainerInput.QUICK_CRAFT));
                     }
                  }

                  dragClicks.add(new MacroExecutor.SafeInventoryClick(-999, end, ContainerInput.QUICK_CRAFT));
                  if (!sendXCarrySafeInventoryClickBatch(mc, dragClicks.toArray(MacroExecutor.SafeInventoryClick[]::new))) {
                     return MacroExecutor.SafeGroupSnapshot.invalid();
                  }

                  nextDragIndex = group.size();
               }

               for (int i = nextDragIndex; i < group.size(); i++) {
                  XCarryAction.PutInMove movex = group.get(i);
                  if (i > nextDragIndex || nextDragIndex > 0) {
                     waitXCarrySafeDelayTicks(delayTicks);
                  }

                  if (!sendXCarrySafeInventoryClick(mc, movex.targetSlotId(), add, ContainerInput.QUICK_CRAFT)) {
                     return MacroExecutor.SafeGroupSnapshot.invalid();
                  }
               }

               if (nextDragIndex < group.size() && !sendXCarrySafeInventoryClick(mc, -999, end, ContainerInput.QUICK_CRAFT)) {
                  return MacroExecutor.SafeGroupSnapshot.invalid();
               }

               boolean firstResidual = true;

               for (Entry<Integer, Integer> residual : quickCraftPlan.residualCounts.entrySet()) {
                  int targetSlotId = residual.getKey();
                  int count = residual.getValue() == null ? 0 : residual.getValue();
                  if (count > 0) {
                     if (!firstResidual) {
                        waitXCarrySafeDelayTicks(delayTicks);
                     }

                     firstResidual = false;
                     if (!placeXCarrySafeDirectCount(mc, targetSlotId, count, delayTicks)) {
                        return MacroExecutor.SafeGroupSnapshot.invalid();
                     }
                  }
               }

               expected = snapshot.withExpectedTargetCounts(quickCraftPlan.expectedTargetCounts);
            } else {
               int nextMoveIndex = 0;
               if (delayAfterPickup) {
                  if (!sendXCarrySafeInventoryClick(mc, first.sourceSlotId(), 0, ContainerInput.PICKUP)) {
                     return MacroExecutor.SafeGroupSnapshot.invalid();
                  }

                  waitXCarrySafeAfterPickupDelay(delayTicks, true);
               } else {
                  XCarryAction.PutInMove firstMove = group.get(0);
                  int firstButton = firstMove.count() >= snapshot.sourceCount ? 0 : 1;
                  if (!sendXCarrySafeInventoryClickBatch(
                     mc,
                     new MacroExecutor.SafeInventoryClick(first.sourceSlotId(), 0, ContainerInput.PICKUP),
                     new MacroExecutor.SafeInventoryClick(firstMove.targetSlotId(), firstButton, ContainerInput.PICKUP)
                  )) {
                     return MacroExecutor.SafeGroupSnapshot.invalid();
                  }

                  int remainingFirst = firstButton == 0 ? 0 : Math.max(0, firstMove.count() - 1);
                  if (remainingFirst > 0) {
                     waitXCarrySafeDelayTicks(delayTicks);
                     if (!placeXCarrySafeDirectCount(mc, firstMove.targetSlotId(), remainingFirst, delayTicks)) {
                        return MacroExecutor.SafeGroupSnapshot.invalid();
                     }
                  }

                  nextMoveIndex = 1;
               }

               for (int i = nextMoveIndex; i < group.size(); i++) {
                  XCarryAction.PutInMove movexx = group.get(i);
                  if (movexx != null && movexx.count() > 0 && !placeXCarrySafeDirectCount(mc, movexx.targetSlotId(), movexx.count(), delayTicks)) {
                     return MacroExecutor.SafeGroupSnapshot.invalid();
                  }
               }
            }

            boolean hasLeftover = Boolean.TRUE.equals(callOnClientThread(mc, () -> {
               InventoryMenu handler = mc.player == null ? null : mc.player.inventoryMenu;
               return handler != null && !handler.getCarried().isEmpty();
            }, Boolean.FALSE));
            if (hasLeftover) {
               if (delayBeforeReturn) {
                  waitXCarrySafeDelayTicks(delayTicks);
               }

               int returnSlot = callOnClientThread(mc, () -> findXCarrySafeReturnSlot(mc, first.sourceSlotId()), -1);
               if (returnSlot < 0) {
                  return MacroExecutor.SafeGroupSnapshot.invalid();
               }

               if (!sendXCarrySafeInventoryClick(mc, returnSlot, 0, ContainerInput.PICKUP)) {
                  return MacroExecutor.SafeGroupSnapshot.invalid();
               }
            }

            boolean cursorEmpty = Boolean.TRUE.equals(callOnClientThread(mc, () -> {
               InventoryMenu handler = mc.player == null ? null : mc.player.inventoryMenu;
               return handler != null && handler.getCarried().isEmpty();
            }, Boolean.FALSE));
            return cursorEmpty ? expected : MacroExecutor.SafeGroupSnapshot.invalid();
         }
      } else {
         return MacroExecutor.SafeGroupSnapshot.invalid();
      }
   }

   private static boolean placeXCarrySafeDirectCount(Minecraft mc, int targetSlotId, int count, int delayTicks) throws InterruptedException {
      if (mc != null && count > 0) {
         int remaining = count;

         while (remaining > 0 && isCurrentRunActive()) {
            int carriedCount = callOnClientThread(mc, () -> {
               InventoryMenu handler = mc.player == null ? null : mc.player.inventoryMenu;
               return handler == null ? 0 : handler.getCarried().getCount();
            }, 0);
            if (carriedCount <= 0) {
               return false;
            }

            int button = remaining >= carriedCount ? 0 : 1;
            if (!sendXCarrySafeInventoryClick(mc, targetSlotId, button, ContainerInput.PICKUP)) {
               return false;
            }

            if (button == 0) {
               remaining = 0;
            } else {
               remaining--;
            }

            if (remaining > 0) {
               waitXCarrySafeDelayTicks(delayTicks);
            }
         }

         return remaining <= 0;
      } else {
         return false;
      }
   }

   private static MacroExecutor.SafeGroupSnapshot snapshotXCarrySafeGroup(Minecraft mc, List<XCarryAction.PutInMove> group) {
      if (mc != null && mc.player != null && group != null && !group.isEmpty()) {
         InventoryMenu handler = mc.player.inventoryMenu;
         if (handler != null && handler.getCarried().isEmpty()) {
            XCarryAction.PutInMove first = group.get(0);
            if (first.sourceSlotId() >= 0 && first.sourceSlotId() < handler.slots.size()) {
               Slot sourceSlot = (Slot)handler.slots.get(first.sourceSlotId());
               ItemStack source = sourceSlot.getItem();
               if (source.isEmpty()) {
                  return MacroExecutor.SafeGroupSnapshot.invalid();
               } else {
                  int total = 0;
                  LinkedHashMap<Integer, Integer> startCounts = new LinkedHashMap<>();
                  LinkedHashMap<Integer, Integer> expectedCounts = new LinkedHashMap<>();

                  for (XCarryAction.PutInMove move : group) {
                     if (move == null || move.sourceSlotId() != first.sourceSlotId()) {
                        return MacroExecutor.SafeGroupSnapshot.invalid();
                     }

                     if (!canSafelyPlaceXCarryMove(handler, source, move)) {
                        return MacroExecutor.SafeGroupSnapshot.invalid();
                     }

                     int start = startCounts.computeIfAbsent(move.targetSlotId(), slotId -> safeSlotCount(handler, slotId));
                     if (start < 0) {
                        return MacroExecutor.SafeGroupSnapshot.invalid();
                     }

                     expectedCounts.put(move.targetSlotId(), expectedCounts.getOrDefault(move.targetSlotId(), start) + move.count());
                     total += move.count();
                  }

                  return total > 0 && total <= source.getCount()
                     ? new MacroExecutor.SafeGroupSnapshot(true, source.copy(), source.getCount(), startCounts, expectedCounts)
                     : MacroExecutor.SafeGroupSnapshot.invalid();
               }
            } else {
               return MacroExecutor.SafeGroupSnapshot.invalid();
            }
         } else {
            return MacroExecutor.SafeGroupSnapshot.invalid();
         }
      } else {
         return MacroExecutor.SafeGroupSnapshot.invalid();
      }
   }

   private static boolean canSafelyPlaceXCarryMove(InventoryMenu handler, ItemStack source, XCarryAction.PutInMove move) {
      if (handler != null && source != null && !source.isEmpty() && move != null && move.count() > 0) {
         if (move.targetSlotId() < 0 || move.targetSlotId() >= handler.slots.size()) {
            return false;
         } else if (move.sourceSlotId() == move.targetSlotId()) {
            return false;
         } else {
            Slot targetSlot = (Slot)handler.slots.get(move.targetSlotId());
            if (targetSlot != null && targetSlot.mayPlace(source)) {
               ItemStack target = targetSlot.getItem();
               if (!target.isEmpty() && !ItemStack.isSameItemSameComponents(target, source)) {
                  return false;
               } else {
                  int current = target.isEmpty() ? 0 : target.getCount();
                  int max = Math.min(source.getMaxStackSize(), targetSlot.getMaxStackSize(source));
                  return current + move.count() <= max;
               }
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private static boolean xcarrySafeCarryingNow(InventoryMenu handler, ItemStack expected, int count) {
      if (handler != null && expected != null && !expected.isEmpty()) {
         ItemStack carried = handler.getCarried();
         return !carried.isEmpty() && carried.getCount() == count && ItemStack.isSameItemSameComponents(carried, expected);
      } else {
         return false;
      }
   }

   private static boolean xcarrySafeSnapshotStillMatches(InventoryMenu handler, List<XCarryAction.PutInMove> group, MacroExecutor.SafeGroupSnapshot snapshot) {
      if (handler != null && group != null && snapshot != null && snapshot.valid) {
         XCarryAction.PutInMove first = group.isEmpty() ? null : group.get(0);
         if (first != null && first.sourceSlotId() >= 0 && first.sourceSlotId() < handler.slots.size()) {
            ItemStack source = ((Slot)handler.slots.get(first.sourceSlotId())).getItem();
            if (!source.isEmpty() && source.getCount() == snapshot.sourceCount && ItemStack.isSameItemSameComponents(source, snapshot.sourceStack)) {
               for (XCarryAction.PutInMove move : group) {
                  if (move != null && move.sourceSlotId() == first.sourceSlotId()) {
                     int current = safeSlotCount(handler, move.targetSlotId());
                     Integer expectedStart = snapshot.startTargetCounts.get(move.targetSlotId());
                     if (expectedStart != null && current == expectedStart) {
                        if (!canSafelyPlaceXCarryMove(handler, source, move)) {
                           return false;
                        }
                        continue;
                     }

                     return false;
                  }

                  return false;
               }

               return true;
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static MacroExecutor.SafeQuickCraftPlan createXCarryQuickCraftPlan(
      InventoryMenu handler, List<XCarryAction.PutInMove> group, MacroExecutor.SafeGroupSnapshot snapshot
   ) {
      if (handler != null && group != null && group.size() >= 2 && snapshot != null && snapshot.valid) {
         LinkedHashMap<Integer, Integer> desiredAdds = new LinkedHashMap<>();

         for (XCarryAction.PutInMove move : group) {
            if (move != null && move.count() > 0) {
               if (move.targetSlotId() >= 0 && move.targetSlotId() < handler.slots.size()) {
                  Slot targetSlot = (Slot)handler.slots.get(move.targetSlotId());
                  if (!isXCarryQuickCraftTarget(handler, targetSlot, snapshot.sourceStack)) {
                     return MacroExecutor.SafeQuickCraftPlan.invalid();
                  }

                  int start = snapshot.startTargetCounts.getOrDefault(move.targetSlotId(), safeSlotCount(handler, move.targetSlotId()));
                  int finalForMove = snapshot.expectedTargetCounts.getOrDefault(move.targetSlotId(), start);
                  int desiredAdd = finalForMove - start;
                  if (desiredAdd <= 0) {
                     return MacroExecutor.SafeQuickCraftPlan.invalid();
                  }

                  int max = Math.min(snapshot.sourceStack.getMaxStackSize(), targetSlot.getMaxStackSize(snapshot.sourceStack));
                  if (start + desiredAdd > max) {
                     return MacroExecutor.SafeQuickCraftPlan.invalid();
                  }

                  desiredAdds.put(move.targetSlotId(), desiredAdd);
                  continue;
               }

               return MacroExecutor.SafeQuickCraftPlan.invalid();
            }

            return MacroExecutor.SafeQuickCraftPlan.invalid();
         }

         MacroExecutor.SafeQuickCraftPlan best = MacroExecutor.SafeQuickCraftPlan.invalid();
         MacroExecutor.SafeQuickCraftPlan even = createXCarryQuickCraftCandidate(handler, snapshot, desiredAdds, 0);
         MacroExecutor.SafeQuickCraftPlan rightDrag = createXCarryQuickCraftCandidate(handler, snapshot, desiredAdds, 1);
         if (even.valid) {
            best = even;
         }

         if (rightDrag.valid && (!best.valid || rightDrag.cost < best.cost)) {
            best = rightDrag;
         }

         return best;
      } else {
         return MacroExecutor.SafeQuickCraftPlan.invalid();
      }
   }

   private static MacroExecutor.SafeQuickCraftPlan createXCarryQuickCraftCandidate(
      InventoryMenu handler, MacroExecutor.SafeGroupSnapshot snapshot, LinkedHashMap<Integer, Integer> desiredAdds, int quickCraftType
   ) {
      if (handler != null && snapshot != null && snapshot.valid && desiredAdds != null && desiredAdds.size() >= 2) {
         int slotCount = desiredAdds.size();

         int placeCount = switch (quickCraftType) {
            case 0 -> snapshot.sourceCount / slotCount;
            case 1 -> 1;
            default -> 0;
         };
         if (placeCount > 0 && snapshot.sourceCount >= placeCount * slotCount) {
            LinkedHashMap<Integer, Integer> residual = new LinkedHashMap<>();
            LinkedHashMap<Integer, Integer> expected = new LinkedHashMap<>();
            int residualTotal = 0;

            for (Entry<Integer, Integer> entry : desiredAdds.entrySet()) {
               int targetSlotId = entry.getKey();
               int desiredAdd = entry.getValue();
               if (desiredAdd < placeCount) {
                  return MacroExecutor.SafeQuickCraftPlan.invalid();
               }

               Slot targetSlot = targetSlotId >= 0 && targetSlotId < handler.slots.size() ? (Slot)handler.slots.get(targetSlotId) : null;
               if (!isXCarryQuickCraftTarget(handler, targetSlot, snapshot.sourceStack)) {
                  return MacroExecutor.SafeQuickCraftPlan.invalid();
               }

               int start = snapshot.startTargetCounts.getOrDefault(targetSlotId, safeSlotCount(handler, targetSlotId));
               int max = Math.min(snapshot.sourceStack.getMaxStackSize(), targetSlot.getMaxStackSize(snapshot.sourceStack));
               if (start + placeCount > max) {
                  return MacroExecutor.SafeQuickCraftPlan.invalid();
               }

               int leftover = desiredAdd - placeCount;
               if (leftover > 0) {
                  residual.put(targetSlotId, leftover);
                  residualTotal += leftover;
               }

               expected.put(targetSlotId, start + desiredAdd);
            }

            int carriedAfterQuickCraft = snapshot.sourceCount - placeCount * slotCount;
            if (residualTotal > carriedAfterQuickCraft) {
               return MacroExecutor.SafeQuickCraftPlan.invalid();
            } else {
               int cost = slotCount + 2 + residualTotal;
               return new MacroExecutor.SafeQuickCraftPlan(true, quickCraftType, placeCount, cost, residual, expected);
            }
         } else {
            return MacroExecutor.SafeQuickCraftPlan.invalid();
         }
      } else {
         return MacroExecutor.SafeQuickCraftPlan.invalid();
      }
   }

   private static boolean isXCarryQuickCraftTarget(InventoryMenu handler, Slot targetSlot, ItemStack sourceStack) {
      if (handler == null || targetSlot == null || sourceStack == null || sourceStack.isEmpty()) {
         return false;
      } else if (targetSlot.index == 0) {
         return false;
      } else {
         return targetSlot.mayPlace(sourceStack) && handler.canDragTo(targetSlot)
            ? AbstractContainerMenu.canItemQuickReplace(targetSlot, sourceStack, true)
            : false;
      }
   }

   private static boolean sendXCarrySafeInventoryClick(Minecraft mc, int slotId, int button, ContainerInput input) throws InterruptedException {
      return sendXCarrySafeInventoryClickBatch(mc, new MacroExecutor.SafeInventoryClick(slotId, button, input));
   }

   private static boolean sendXCarrySafeInventoryClickBatch(Minecraft mc, MacroExecutor.SafeInventoryClick... clicks) throws InterruptedException {
      return mc != null && clicks != null && clicks.length != 0 ? Boolean.TRUE.equals(callOnClientThread(mc, () -> {
         if (mc.player != null && mc.gameMode != null) {
            InventoryMenu handler = mc.player.inventoryMenu;
            if (handler == null) {
               return false;
            } else {
               mc.player.containerMenu = handler;
               MacroExecutor.SafeInventoryClick[] arr$ = clicks;
               int len$ = clicks.length;
               int i$ = 0;

               while (i$ < len$) {
                  MacroExecutor.SafeInventoryClick click = arr$[i$];
                  if (click != null && click.input != null) {
                     if (click.slotId == -999 || click.slotId >= 0 && click.slotId < handler.slots.size()) {
                        mc.gameMode.handleContainerInput(handler.containerId, click.slotId, click.button, click.input, mc.player);
                        i$++;
                        continue;
                     }

                     return false;
                  }

                  return false;
               }

               return true;
            }
         } else {
            return false;
         }
      }, Boolean.FALSE)) : false;
   }

   private static void waitXCarrySafeDelayTicks(int delayTicks) {
      int ticks = XCarryAction.clampSafeClickDelayTicks(delayTicks);

      for (int i = 0; i < ticks && isCurrentRunActive(); i++) {
         waitOneTick();
      }
   }

   private static void waitXCarrySafeAfterPickupDelay(int delayTicks, boolean enabled) {
      if (enabled) {
         waitXCarrySafeDelayTicks(delayTicks);
      }
   }

   private static boolean sendInventoryClickThenTick(Minecraft mc, int slotId, int button, ContainerInput input) throws InterruptedException {
      if (mc != null && mc.player != null && mc.gameMode != null) {
         boolean clicked = callOnClientThread(mc, () -> {
            if (mc.player != null && mc.gameMode != null) {
               InventoryMenu handler = mc.player.inventoryMenu;
               mc.player.containerMenu = handler;
               if (slotId >= 0 && slotId < handler.slots.size()) {
                  mc.gameMode.handleContainerInput(handler.containerId, slotId, button, input, mc.player);
                  return true;
               } else {
                  return false;
               }
            } else {
               return false;
            }
         }, Boolean.FALSE);
         if (!clicked) {
            return false;
         } else {
            waitOneTick();
            return true;
         }
      } else {
         return false;
      }
   }

   private static boolean sendContainerClickThenTick(Minecraft mc, AbstractContainerMenu handler, int slotId, int button, ContainerInput input) throws InterruptedException {
      if (mc != null && handler != null && input != null) {
         boolean clicked = callOnClientThread(mc, () -> {
            if (mc.player == null || mc.gameMode == null || mc.player.containerMenu != handler) {
               return false;
            } else if (slotId < 0 || slotId >= handler.slots.size()) {
               return false;
            } else if (((Slot)handler.slots.get(slotId)).getItem().isEmpty()) {
               return false;
            } else {
               mc.gameMode.handleContainerInput(handler.containerId, slotId, button, input, mc.player);
               return true;
            }
         }, Boolean.FALSE);
         if (!clicked) {
            return false;
         } else {
            waitOneTick();
            return true;
         }
      } else {
         return false;
      }
   }

   private static boolean waitForXCarrySafeLocal(Minecraft mc, int ticks, BooleanSupplier accepted) throws InterruptedException {
      int maxTicks = Math.max(1, ticks);

      for (int i = 0; i < maxTicks && isCurrentRunActive(); i++) {
         if (Boolean.TRUE.equals(callOnClientThread(mc, () -> accepted != null && accepted.getAsBoolean(), Boolean.FALSE))) {
            return true;
         }

         waitOneTick();
      }

      return Boolean.TRUE.equals(callOnClientThread(mc, () -> accepted != null && accepted.getAsBoolean(), Boolean.FALSE));
   }

   private static void waitForXCarrySafeSettle(Minecraft mc, long startRevision, int minTicks, long maxMs) throws InterruptedException {
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(50L, maxMs));
      int ticks = Math.max(1, minTicks);

      for (int i = 0; i < ticks && isCurrentRunActive(); i++) {
         waitOneTick();
      }

      while (isCurrentRunActive() && System.nanoTime() < deadline) {
         long revision = MacroConditionRegistry.inventorySyncRevision();
         if (revision != startRevision) {
            waitOneTick();
            return;
         }

         waitOneTick();
      }
   }

   private static boolean waitForXCarrySafeFinalState(Minecraft mc, MacroExecutor.SafeGroupSnapshot snapshot, long startRevision) throws InterruptedException {
      waitForXCarrySafeSettle(mc, startRevision, 2, 250L);
      if (Boolean.TRUE.equals(callOnClientThread(mc, () -> xcarrySafeFinalMatches(mc, snapshot), Boolean.FALSE))) {
         return true;
      } else {
         waitForXCarrySafeSettle(mc, MacroConditionRegistry.inventorySyncRevision(), 3, 350L);
         return Boolean.TRUE.equals(callOnClientThread(mc, () -> xcarrySafeFinalMatches(mc, snapshot), Boolean.FALSE));
      }
   }

   private static boolean xcarrySafeFinalMatches(Minecraft mc, MacroExecutor.SafeGroupSnapshot snapshot) {
      if (mc != null && mc.player != null && snapshot != null && snapshot.valid) {
         InventoryMenu handler = mc.player.inventoryMenu;
         if (handler != null && handler.getCarried().isEmpty()) {
            for (Entry<Integer, Integer> entry : snapshot.expectedTargetCounts.entrySet()) {
               int slotId = entry.getKey();
               if (slotId >= 0 && slotId < handler.slots.size()) {
                  ItemStack stack = ((Slot)handler.slots.get(slotId)).getItem();
                  if (entry.getValue() <= 0) {
                     if (!stack.isEmpty()) {
                        return false;
                     }
                     continue;
                  }

                  if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, snapshot.sourceStack)) {
                     if (stack.getCount() != entry.getValue()) {
                        return false;
                     }
                     continue;
                  }

                  return false;
               }

               return false;
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean returnXCarrySafeLeftovers(Minecraft mc, int preferredSlotId, ItemStack expected, int delayTicks, boolean delayBeforeReturn) throws InterruptedException {
      if (mc != null && mc.player != null && expected != null && !expected.isEmpty()) {
         if (callOnClientThread(mc, () -> mc.player != null && mc.player.inventoryMenu.getCarried().isEmpty(), Boolean.FALSE)) {
            return true;
         } else {
            int returnSlot = callOnClientThread(mc, () -> findXCarrySafeReturnSlot(mc, preferredSlotId), -1);
            if (returnSlot < 0) {
               return false;
            } else {
               if (delayBeforeReturn) {
                  waitXCarrySafeDelayTicks(delayTicks);
               }

               return sendInventoryClickThenTick(mc, returnSlot, 0, ContainerInput.PICKUP)
                  && waitForXCarrySafeLocal(mc, 3, () -> mc.player != null && mc.player.inventoryMenu.getCarried().isEmpty());
            }
         }
      } else {
         return false;
      }
   }

   private static int findXCarrySafeReturnSlot(Minecraft mc, int preferredSlotId) {
      if (mc != null && mc.player != null) {
         InventoryMenu handler = mc.player.inventoryMenu;
         ItemStack carried = handler.getCarried();
         if (carried.isEmpty()) {
            return -1;
         } else if (canAcceptXCarryLeftover(handler, preferredSlotId, carried)) {
            return preferredSlotId;
         } else {
            for (Slot slot : handler.slots) {
               if (slot != null && slot.index >= 9 && slot.index != 45 && canAcceptXCarryLeftover(handler, slot.index, carried)) {
                  return slot.index;
               }
            }

            return -1;
         }
      } else {
         return -1;
      }
   }

   private static boolean canAcceptXCarryLeftover(InventoryMenu handler, int slotId, ItemStack carried) {
      if (handler == null || carried == null || carried.isEmpty()) {
         return false;
      } else if (slotId >= 0 && slotId < handler.slots.size()) {
         Slot slot = (Slot)handler.slots.get(slotId);
         if (slot != null && slot.mayPlace(carried)) {
            ItemStack stack = slot.getItem();
            return stack.isEmpty()
               ? carried.getCount() <= slot.getMaxStackSize(carried)
               : ItemStack.isSameItemSameComponents(stack, carried)
                  && stack.getCount() + carried.getCount() <= Math.min(carried.getMaxStackSize(), slot.getMaxStackSize(carried));
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static int safeSlotCount(Minecraft mc, int slotId) {
      return mc != null && mc.player != null ? safeSlotCount(mc.player.inventoryMenu, slotId) : -1;
   }

   private static int safeSlotCount(InventoryMenu handler, int slotId) {
      if (handler == null) {
         return -1;
      } else if (slotId >= 0 && slotId < handler.slots.size()) {
         ItemStack stack = ((Slot)handler.slots.get(slotId)).getItem();
         return stack.isEmpty() ? 0 : stack.getCount();
      } else {
         return -1;
      }
   }

   private static void rollbackXCarryCursorSafe(Minecraft mc, int sourceSlotId, int delayTicks, boolean delayBeforeReturn) throws InterruptedException {
      if (mc != null && mc.player != null && mc.gameMode != null) {
         if (!callOnClientThread(mc, () -> mc.player != null && mc.player.inventoryMenu.getCarried().isEmpty(), Boolean.FALSE)) {
            int returnSlot = callOnClientThread(mc, () -> findXCarrySafeReturnSlot(mc, sourceSlotId), -1);
            if (returnSlot >= 0) {
               if (delayBeforeReturn) {
                  waitXCarrySafeDelayTicks(delayTicks);
               }

               sendInventoryClickThenTick(mc, returnSlot, 0, ContainerInput.PICKUP);
               waitForXCarrySafeLocal(mc, 3, () -> mc.player != null && mc.player.inventoryMenu.getCarried().isEmpty());
            } else {
               rollbackXCarryCursor(mc, sourceSlotId);
            }
         }
      }
   }

   private static void rollbackXCarryCursor(Minecraft mc, int sourceSlotId) throws InterruptedException {
      runOnClientThread(mc, () -> {
         if (mc.player != null && mc.gameMode != null) {
            InventoryMenu handler = mc.player.inventoryMenu;
            mc.player.containerMenu = handler;
            if (!handler.getCarried().isEmpty()) {
               if (sourceSlotId >= 0 && sourceSlotId < handler.slots.size()) {
                  mc.gameMode.handleContainerInput(handler.containerId, sourceSlotId, 0, ContainerInput.PICKUP, mc.player);
               }
            }
         }
      });
      waitForXCarryCondition(mc, 300L, () -> mc.player != null && mc.player.inventoryMenu.getCarried().isEmpty());
   }

   private static boolean waitForXCarryCondition(Minecraft mc, long timeoutMs, Supplier<Boolean> condition) throws InterruptedException {
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);

      while (isCurrentRunActive()) {
         if (Boolean.TRUE.equals(callOnClientThread(mc, condition, Boolean.FALSE))) {
            return true;
         }

         if (System.nanoTime() >= deadline) {
            break;
         }

         waitOneTick();
      }

      return Boolean.TRUE.equals(callOnClientThread(mc, condition, Boolean.FALSE));
   }

   private static void waitOneTick() {
      CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
      waitForCondition(tickFuture);
   }

   private static void runInstaBreakAction(Minecraft mc, InstaBreakAction action) throws InterruptedException {
      if (mc != null && mc.player != null && mc.level != null && mc.getConnection() != null && mc.gameMode != null) {
         BlockPos target = action.blockPos == null ? BlockPos.ZERO : action.blockPos;
         Direction direction = action.direction == null ? Direction.UP : action.direction;
         int delayTicks = Math.max(0, action.delayTicks);
         int targetTimes = Math.max(0, action.times);
         int completed = 0;
         boolean wasSolid = !isInstaBreakAir(mc, target);
         if (ensureInstaBreakPickaxe(mc, action)) {
            RiptideInstaBreakRenderer.setTarget(target);

            try {
               long lastAttemptTick = RiptideSharedState.get().getClientTickCounter() - (delayTicks + 1L);
               long lastSneakTick = Long.MIN_VALUE;

               while (isCurrentRunActive() && isActionStillInCurrentMacro(action)) {
                  if (action.sneak) {
                     long sneakTick = RiptideSharedState.get().getClientTickCounter();
                     if (sneakTick != lastSneakTick) {
                        lastSneakTick = sneakTick;
                        String sneakMode = action.sneakMode;
                        runOnClientThread(mc, () -> holdMacroSneak(mc, sneakMode), action.isTickAligned());
                     }
                  }

                  boolean isAir = isInstaBreakAir(mc, target);
                  if (wasSolid && isAir) {
                     setCurrentStatus("InstaBreak " + ++completed + "/" + (targetTimes == 0 ? "∞" : targetTimes));
                     if (targetTimes > 0 && completed >= targetTimes) {
                        return;
                     }

                     wasSolid = false;
                  } else if (!isAir) {
                     wasSolid = true;
                  }

                  if (!isAir) {
                     long currentTick = RiptideSharedState.get().getClientTickCounter();
                     if (currentTick != lastAttemptTick && currentTick - lastAttemptTick >= delayTicks) {
                        lastAttemptTick = currentTick;
                        if (!ensureInstaBreakPickaxe(mc, action)) {
                           return;
                        }

                        if (action.interact) {
                           InteractTiming t = action.interactTiming;
                           boolean interactFirst = t == InteractTiming.BEFORE
                              || t == InteractTiming.WITH
                              || t == InteractTiming.CUSTOM && action.interactCustomMs < 0;
                           long ns = t == InteractTiming.CUSTOM ? Math.abs((long)action.interactCustomMs) * 1000000L : 0L;
                           if (interactFirst) {
                              runOnClientThread(mc, () -> sendBlockInteract(mc, target, direction), action.isTickAligned());
                              if (ns > 0L) {
                                 LockSupport.parkNanos(ns);
                              }

                              sendInstaBreakAttempt(mc, target, direction, action.isTickAligned());
                           } else if (t == InteractTiming.AFTER_PLUS) {
                              sendInstaBreakAttempt(mc, target, direction, action.isTickAligned());
                              waitOneTick();
                              runOnClientThread(mc, () -> sendBlockInteract(mc, target, direction), action.isTickAligned());
                           } else {
                              sendInstaBreakAttempt(mc, target, direction, action.isTickAligned());
                              if (ns > 0L) {
                                 LockSupport.parkNanos(ns);
                              }

                              runOnClientThread(mc, () -> sendBlockInteract(mc, target, direction), action.isTickAligned());
                           }
                        } else {
                           sendInstaBreakAttempt(mc, target, direction, action.isTickAligned());
                        }
                     }
                  }

                  setCurrentStatus(
                     "InstaBreak " + target.getX() + "," + target.getY() + "," + target.getZ() + " " + completed + "/" + (targetTimes == 0 ? "∞" : targetTimes)
                  );
                  Thread.sleep(1L);
               }
            } finally {
               if (action.sneak) {
                  String sneakMode = action.sneakMode;

                  try {
                     runOnClientThread(mc, () -> releaseMacroSneak(mc, sneakMode));
                  } catch (InterruptedException var27) {
                     Thread.currentThread().interrupt();
                  }
               }

               RiptideInstaBreakRenderer.clearTarget(target);
            }
         }
      } else {
         RiptideClientMessaging.sendPrefixed("§cInstaBreak: missing world or connection");
      }
   }

   private static boolean isInstaBreakAir(Minecraft mc, BlockPos pos) {
      return mc.level == null || mc.level.isOutsideBuildHeight(pos) || mc.level.getBlockState(pos).isAir();
   }

   private static boolean isActionStillInCurrentMacro(MacroAction action) {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      return run != null && run.macro != null && run.macro.actions != null ? run.macro.actions.contains(action) : true;
   }

   private static boolean ensureInstaBreakPickaxe(Minecraft mc, InstaBreakAction action) throws InterruptedException {
      return callOnClientThread(mc, () -> {
         if (mc.player == null) {
            return false;
         } else if (mc.player.getMainHandItem().is(ItemTags.PICKAXES)) {
            return true;
         } else if (!action.autoPickaxe) {
            return false;
         } else {
            int preferred = Math.max(0, Math.min(8, mc.player.getInventory().getSelectedSlot()));

            for (int slot = 0; slot < 9; slot++) {
               if (mc.player.getInventory().getItem(slot).is(ItemTags.PICKAXES)) {
                  RiptideInventoryHelper.selectHotbarSlot(mc, slot);
                  return true;
               }
            }

            for (int slotx = 9; slotx < 36; slotx++) {
               if (mc.player.getInventory().getItem(slotx).is(ItemTags.PICKAXES)) {
                  if (!RiptideInventoryHelper.swapInventorySlots(mc, slotx, preferred)) {
                     return false;
                  }

                  RiptideInventoryHelper.selectHotbarSlot(mc, preferred);
                  return true;
               }
            }

            return false;
         }
      }, false, action.isTickAligned());
   }

   private static void sendInstaBreakAttempt(Minecraft mc, BlockPos pos, Direction direction, boolean tickAligned) throws InterruptedException {
      runOnClientThread(mc, () -> {
         if (mc.player != null && mc.level != null && mc.gameMode != null && mc.getConnection() != null) {
            BlockPos blockPos = pos.immutable();
            Direction dir = direction == null ? Direction.UP : direction;
            if (mc.gameMode instanceof RiptideMultiPlayerGameModeAccessor accessor) {
               accessor.riptide$startPrediction(mc.level, sequence -> new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, blockPos, dir, sequence));
               accessor.riptide$startPrediction(mc.level, sequence -> new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, blockPos, dir, sequence));
            } else {
               mc.getConnection().send(new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, blockPos, dir));
               mc.getConnection().send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, blockPos, dir));
            }

            mc.player.swing(InteractionHand.MAIN_HAND);
         }
      }, tickAligned);
   }

   private static void runBreakAction(Minecraft mc, BreakAction action) throws InterruptedException {
      if (mc != null && mc.player != null && mc.level != null && mc.getConnection() != null && mc.gameMode != null) {
         BlockPos target = action.blockPos == null ? BlockPos.ZERO : action.blockPos;
         Direction direction = action.direction == null ? Direction.UP : action.direction;
         int startDelay = Math.max(0, action.delayTicks);
         int targetTimes = Math.max(0, action.times);
         int completed = 0;
         if (startDelay > 0) {
            waitBreakClientTicks(startDelay);
         }

         boolean[] started = new boolean[]{false};
         boolean useNextSteps = action.interact && action.runNextSteps;
         boolean[] nextStepsFired = new boolean[]{false};
         boolean[] missingNextStepsWarned = new boolean[]{false};
         RiptideInstaBreakRenderer.setTarget(target);
         long lastTick = -1L;

         try {
            while (isCurrentRunActive() && isActionStillInCurrentMacro(action)) {
               long now = RiptideSharedState.get().getClientTickCounter();
               if (now == lastTick) {
                  Thread.sleep(1L);
               } else {
                  lastTick = now;
                  int result;
                  if (!started[0]) {
                     result = useNextSteps
                        ? startBreakWithNextSteps(mc, action, target, direction, started, nextStepsFired, missingNextStepsWarned)
                        : callOnClientThread(mc, () -> startBreakOnClient(mc, action, target, direction, started), -1, action.isTickAligned());
                  } else if (action.interact && action.interactTiming == InteractTiming.CUSTOM) {
                     result = useNextSteps
                        ? breakCustomTickWithNextSteps(mc, action, target, direction, nextStepsFired, missingNextStepsWarned)
                        : breakCustomTick(mc, action, target, direction);
                  } else {
                     result = useNextSteps
                        ? breakContinueWithNextSteps(mc, action, target, direction, nextStepsFired, missingNextStepsWarned)
                        : callOnClientThread(mc, () -> breakContinueOnClient(mc, action, target, direction), -1, action.isTickAligned());
                  }

                  if (result < 0) {
                     return;
                  }

                  if (result != 1) {
                     setCurrentStatus(
                        "Break " + target.getX() + "," + target.getY() + "," + target.getZ() + " " + completed + "/" + (targetTimes == 0 ? "∞" : targetTimes)
                     );
                  } else {
                     boolean completedStartedBreak = started[0];
                     if (useNextSteps && completedStartedBreak) {
                        runBreakNextStepsAfterCompletion(mc, action, nextStepsFired, missingNextStepsWarned);
                     } else if (action.interact && action.interactTiming == InteractTiming.AFTER_PLUS) {
                        waitOneTick();
                        runOnClientThread(mc, () -> sendBlockInteract(mc, target, direction), action.isTickAligned());
                     }

                     setCurrentStatus("Break " + ++completed + "/" + (targetTimes == 0 ? "∞" : targetTimes));
                     if (targetTimes > 0 && completed >= targetTimes) {
                        return;
                     }

                     started[0] = false;
                     nextStepsFired[0] = false;
                     waitForBreakSolid(mc, target);
                  }
               }
            }
         } finally {
            boolean wasMining = started[0];
            runOnClientThread(mc, () -> {
               if (wasMining && mc.gameMode != null && mc.gameMode.isDestroying()) {
                  mc.gameMode.stopDestroyBlock();
               }

               if (action.sneak) {
                  releaseMacroSneak(mc, action.sneakMode);
               }
            }, action.isTickAligned());
            RiptideInstaBreakRenderer.clearTarget(target);
         }
      } else {
         RiptideClientMessaging.sendPrefixed("§cBreak: missing world or connection");
      }
   }

   private static int startBreakWithNextSteps(
      Minecraft mc, BreakAction action, BlockPos pos, Direction dir, boolean[] started, boolean[] nextStepsFired, boolean[] missingNextStepsWarned
   ) throws InterruptedException {
      MacroExecutor.BreakStartPlan plan = callOnClientThread(
         mc, () -> prepareBreakStartOnClient(mc, action, pos), new MacroExecutor.BreakStartPlan(-1, false, false)
      );
      if (plan.result() != 0) {
         return plan.result();
      } else {
         if (plan.instant() && plan.interactFirst()) {
            runBreakNextStepsOnce(mc, action, nextStepsFired, missingNextStepsWarned);
            if (!isCurrentRunActive()) {
               return -1;
            }
         }

         return callOnClientThread(mc, () -> startPreparedBreakOnClient(mc, action, pos, dir, started), -1, action.isTickAligned());
      }
   }

   private static MacroExecutor.BreakStartPlan prepareBreakStartOnClient(Minecraft mc, BreakAction action, BlockPos pos) {
      if (mc.level == null || mc.gameMode == null || mc.player == null || mc.getConnection() == null) {
         return new MacroExecutor.BreakStartPlan(-1, false, false);
      } else if (mc.level.isOutsideBuildHeight(pos)) {
         return new MacroExecutor.BreakStartPlan(1, false, false);
      } else {
         BlockState state = mc.level.getBlockState(pos);
         if (state.isAir()) {
            return new MacroExecutor.BreakStartPlan(1, false, false);
         } else {
            if (action.sneak) {
               holdMacroSneak(mc, action.sneakMode);
            }

            if (action.autoTool) {
               RiptideAutoTool.equipBestTool(mc, state, action.considerInventory, true);
               state = mc.level.getBlockState(pos);
               if (state.isAir()) {
                  return new MacroExecutor.BreakStartPlan(1, false, false);
               }
            }

            float startDelta = state.getDestroyProgress(mc.player, mc.player.level(), pos);
            boolean instant = startDelta >= 1.0F;
            boolean interactFirst = action.interact
               && (
                  action.interactTiming == InteractTiming.BEFORE
                     || action.interactTiming == InteractTiming.WITH
                     || action.interactTiming == InteractTiming.CUSTOM && action.interactCustomMs < 0
               );
            return new MacroExecutor.BreakStartPlan(0, instant, interactFirst);
         }
      }
   }

   private static int startPreparedBreakOnClient(Minecraft mc, BreakAction action, BlockPos pos, Direction dir, boolean[] started) {
      if (mc.level == null || mc.gameMode == null || mc.player == null || mc.getConnection() == null) {
         return -1;
      } else if (!mc.level.isOutsideBuildHeight(pos) && !mc.level.getBlockState(pos).isAir()) {
         if (action.sneak) {
            holdMacroSneak(mc, action.sneakMode);
         }

         mc.gameMode.startDestroyBlock(pos, dir);
         mc.player.swing(InteractionHand.MAIN_HAND);
         started[0] = true;
         return mc.level.getBlockState(pos).isAir() ? 1 : 0;
      } else {
         return 1;
      }
   }

   private static int startBreakOnClient(Minecraft mc, BreakAction action, BlockPos pos, Direction dir, boolean[] started) {
      if (mc.level != null && mc.gameMode != null && mc.player != null && mc.getConnection() != null) {
         BlockState state = mc.level.getBlockState(pos);
         if (!state.isAir() && !mc.level.isOutsideBuildHeight(pos)) {
            if (action.sneak) {
               holdMacroSneak(mc, action.sneakMode);
            }

            if (action.autoTool) {
               RiptideAutoTool.equipBestTool(mc, state, action.considerInventory, true);
               state = mc.level.getBlockState(pos);
            }

            float startDelta = state.getDestroyProgress(mc.player, mc.player.level(), pos);
            boolean instant = startDelta >= 1.0F;
            boolean interactFirst = action.interact
               && (
                  action.interactTiming == InteractTiming.BEFORE
                     || action.interactTiming == InteractTiming.WITH
                     || action.interactTiming == InteractTiming.CUSTOM && action.interactCustomMs < 0
               );
            if (instant && interactFirst) {
               sendBlockInteract(mc, pos, dir);
            }

            mc.gameMode.startDestroyBlock(pos, dir);
            mc.player.swing(InteractionHand.MAIN_HAND);
            started[0] = true;
            if (instant && action.interact && !interactFirst && action.interactTiming != InteractTiming.AFTER_PLUS) {
               sendBlockInteract(mc, pos, dir);
            }

            return mc.level.getBlockState(pos).isAir() ? 1 : 0;
         } else {
            return 1;
         }
      } else {
         return -1;
      }
   }

   private static int breakContinueWithNextSteps(
      Minecraft mc, BreakAction action, BlockPos pos, Direction dir, boolean[] nextStepsFired, boolean[] missingNextStepsWarned
   ) throws InterruptedException {
      MacroExecutor.BreakContinuePlan plan = callOnClientThread(
         mc, () -> inspectBreakContinueOnClient(mc, action, pos), new MacroExecutor.BreakContinuePlan(-1, false, false)
      );
      if (plan.result() != 0) {
         return plan.result();
      } else {
         InteractTiming timing = action.interactTiming;
         if (timing == InteractTiming.BEFORE && plan.breaksNextTick()) {
            runBreakNextStepsOnce(mc, action, nextStepsFired, missingNextStepsWarned);
            if (!isCurrentRunActive()) {
               return -1;
            }
         }

         if (timing == InteractTiming.WITH && plan.breaksThisTick()) {
            runBreakNextStepsOnce(mc, action, nextStepsFired, missingNextStepsWarned);
            if (!isCurrentRunActive()) {
               return -1;
            }
         }

         return callOnClientThread(mc, () -> continueBreakOnClient(mc, action, pos, dir), -1, action.isTickAligned());
      }
   }

   private static MacroExecutor.BreakContinuePlan inspectBreakContinueOnClient(Minecraft mc, BreakAction action, BlockPos pos) {
      if (mc.level != null && mc.gameMode != null && mc.player != null && mc.getConnection() != null) {
         BlockState state = mc.level.getBlockState(pos);
         if (state.isAir()) {
            return new MacroExecutor.BreakContinuePlan(1, false, false);
         } else {
            if (action.sneak) {
               holdMacroSneak(mc, action.sneakMode);
            }

            boolean breaksThisTick = willBreakThisTick(mc, pos, state);
            boolean breaksNextTick = !breaksThisTick && willBreakNextTick(mc, pos, state);
            return new MacroExecutor.BreakContinuePlan(0, breaksThisTick, breaksNextTick);
         }
      } else {
         return new MacroExecutor.BreakContinuePlan(-1, false, false);
      }
   }

   private static int continueBreakOnClient(Minecraft mc, BreakAction action, BlockPos pos, Direction dir) {
      if (mc.level == null || mc.gameMode == null || mc.player == null || mc.getConnection() == null) {
         return -1;
      } else if (mc.level.getBlockState(pos).isAir()) {
         return 1;
      } else {
         if (action.sneak) {
            holdMacroSneak(mc, action.sneakMode);
         }

         mc.gameMode.continueDestroyBlock(pos, dir);
         mc.player.swing(InteractionHand.MAIN_HAND);
         return mc.level.getBlockState(pos).isAir() ? 1 : 0;
      }
   }

   private static int breakContinueOnClient(Minecraft mc, BreakAction action, BlockPos pos, Direction dir) {
      if (mc.level != null && mc.gameMode != null && mc.player != null && mc.getConnection() != null) {
         BlockState state = mc.level.getBlockState(pos);
         if (state.isAir()) {
            return 1;
         } else {
            if (action.sneak) {
               holdMacroSneak(mc, action.sneakMode);
            }

            boolean breaksThisTick = willBreakThisTick(mc, pos, state);
            boolean breaksNextTick = !breaksThisTick && willBreakNextTick(mc, pos, state);
            InteractTiming t = action.interactTiming;
            if (action.interact && t == InteractTiming.BEFORE && breaksNextTick) {
               sendBlockInteract(mc, pos, dir);
            }

            if (action.interact && t == InteractTiming.WITH && breaksThisTick) {
               sendBlockInteract(mc, pos, dir);
            }

            mc.gameMode.continueDestroyBlock(pos, dir);
            mc.player.swing(InteractionHand.MAIN_HAND);
            if (action.interact && t == InteractTiming.AFTER && breaksThisTick) {
               sendBlockInteract(mc, pos, dir);
            }

            return mc.level.getBlockState(pos).isAir() ? 1 : 0;
         }
      } else {
         return -1;
      }
   }

   private static int breakCustomTickWithNextSteps(
      Minecraft mc, BreakAction action, BlockPos pos, Direction dir, boolean[] nextStepsFired, boolean[] missingNextStepsWarned
   ) throws InterruptedException {
      boolean willBreak = Boolean.TRUE.equals(callOnClientThread(mc, () -> {
         if (mc.level != null && mc.player != null) {
            BlockState state = mc.level.getBlockState(pos);
            return state.isAir() ? Boolean.FALSE : willBreakThisTick(mc, pos, state);
         } else {
            return Boolean.FALSE;
         }
      }, Boolean.FALSE));
      if (!willBreak) {
         return callOnClientThread(mc, () -> continueBreakOnClient(mc, action, pos, dir), -1, action.isTickAligned());
      } else {
         long ns = Math.abs((long)action.interactCustomMs) * 1000000L;
         int broke;
         if (action.interactCustomMs < 0) {
            runBreakNextStepsOnce(mc, action, nextStepsFired, missingNextStepsWarned);
            if (!isCurrentRunActive()) {
               return -1;
            }

            if (ns > 0L) {
               LockSupport.parkNanos(ns);
            }

            broke = callOnClientThread(mc, () -> breakAndDetectOnClient(mc, pos, dir), -1, action.isTickAligned());
         } else {
            broke = callOnClientThread(mc, () -> breakAndDetectOnClient(mc, pos, dir), -1, action.isTickAligned());
         }

         return broke;
      }
   }

   private static int breakCustomTick(Minecraft mc, BreakAction action, BlockPos pos, Direction dir) throws InterruptedException {
      boolean willBreak = Boolean.TRUE.equals(callOnClientThread(mc, () -> {
         if (mc.level != null && mc.player != null) {
            BlockState s = mc.level.getBlockState(pos);
            return s.isAir() ? Boolean.FALSE : willBreakThisTick(mc, pos, s);
         } else {
            return Boolean.FALSE;
         }
      }, Boolean.FALSE));
      if (!willBreak) {
         return callOnClientThread(mc, () -> {
            if (mc.level == null || mc.gameMode == null || mc.player == null) {
               return -1;
            } else if (mc.level.getBlockState(pos).isAir()) {
               return 1;
            } else {
               if (action.sneak) {
                  holdMacroSneak(mc, action.sneakMode);
               }

               mc.gameMode.continueDestroyBlock(pos, dir);
               mc.player.swing(InteractionHand.MAIN_HAND);
               return mc.level.getBlockState(pos).isAir() ? 1 : 0;
            }
         }, -1, action.isTickAligned());
      } else {
         long ns = Math.abs((long)action.interactCustomMs) * 1000000L;
         int broke;
         if (action.interactCustomMs < 0) {
            runOnClientThread(mc, () -> sendBlockInteract(mc, pos, dir), action.isTickAligned());
            if (ns > 0L) {
               LockSupport.parkNanos(ns);
            }

            broke = callOnClientThread(mc, () -> breakAndDetectOnClient(mc, pos, dir), -1, action.isTickAligned());
         } else {
            broke = callOnClientThread(mc, () -> breakAndDetectOnClient(mc, pos, dir), -1, action.isTickAligned());
            if (ns > 0L) {
               LockSupport.parkNanos(ns);
            }

            runOnClientThread(mc, () -> sendBlockInteract(mc, pos, dir), action.isTickAligned());
         }

         return broke;
      }
   }

   private static int breakAndDetectOnClient(Minecraft mc, BlockPos pos, Direction dir) {
      if (mc.level == null || mc.gameMode == null || mc.player == null) {
         return -1;
      } else if (mc.level.getBlockState(pos).isAir()) {
         return 1;
      } else {
         mc.gameMode.continueDestroyBlock(pos, dir);
         mc.player.swing(InteractionHand.MAIN_HAND);
         return mc.level.getBlockState(pos).isAir() ? 1 : 0;
      }
   }

   private static boolean willBreakThisTick(Minecraft mc, BlockPos pos, BlockState state) {
      float progress = mc.gameMode instanceof RiptideMultiPlayerGameModeAccessor acc ? acc.riptide$getDestroyProgress() : 0.0F;
      float delta = state.getDestroyProgress(mc.player, mc.player.level(), pos);
      return progress > 0.0F && progress + delta >= 1.0F;
   }

   private static boolean willBreakNextTick(Minecraft mc, BlockPos pos, BlockState state) {
      float progress = mc.gameMode instanceof RiptideMultiPlayerGameModeAccessor acc ? acc.riptide$getDestroyProgress() : 0.0F;
      float delta = state.getDestroyProgress(mc.player, mc.player.level(), pos);
      return progress > 0.0F && progress + 2.0F * delta >= 1.0F;
   }

   private static void runBreakNextStepsOnce(Minecraft mc, BreakAction action, boolean[] fired, boolean[] missingWarned) throws InterruptedException {
      if (fired == null || !fired[0]) {
         List<MacroAction> actions = action.getNextActions();
         if (actions != null && !actions.isEmpty()) {
            if (fired != null) {
               fired[0] = true;
            }

            action.markNextActionsRan();
            String names = actions.size() == 1 ? actions.get(0).getDisplayName() : actions.size() + " steps";
            setCurrentStatus("Break: next " + names);
            RiptideModule module = RiptideModule.get();
            boolean previousSuppressPostGui = Boolean.TRUE.equals(SUPPRESS_POST_GUI_AFTER.get());

            try {
               SUPPRESS_POST_GUI_AFTER.set(true);

               for (MacroAction next : actions) {
                  if (next != null && next.isEnabled() && isCurrentRunActive()) {
                     try {
                        executeSingleActionWithWaits(mc, next, module);
                     } catch (InterruptedException var15) {
                        throw var15;
                     } catch (Throwable var16) {
                        RiptideClientMessaging.sendPrefixed("§cBreak next step failed (" + next.getDisplayName() + "): " + safeThrowableMessage(var16));
                     }
                  }
               }
            } finally {
               SUPPRESS_POST_GUI_AFTER.set(previousSuppressPostGui);
            }
         } else {
            if (missingWarned != null && !missingWarned[0]) {
               missingWarned[0] = true;
               RiptideClientMessaging.sendPrefixed("§cBreak: no next steps found. Add action(s) after this Break step.");
            }
         }
      }
   }

   private static void runBreakNextStepsAfterCompletion(Minecraft mc, BreakAction action, boolean[] fired, boolean[] missingWarned) throws InterruptedException {
      if (action.interact && action.runNextSteps && action.interactTiming != null) {
         switch (action.interactTiming) {
            case AFTER:
               runBreakNextStepsOnce(mc, action, fired, missingWarned);
               break;
            case AFTER_PLUS:
               waitOneTick();
               runBreakNextStepsOnce(mc, action, fired, missingWarned);
               break;
            case CUSTOM:
               if (action.interactCustomMs >= 0) {
                  long ns = Math.abs((long)action.interactCustomMs) * 1000000L;
                  if (ns > 0L) {
                     LockSupport.parkNanos(ns);
                  }

                  runBreakNextStepsOnce(mc, action, fired, missingWarned);
               }
         }
      }
   }

   private static void sendBlockInteract(Minecraft mc, BlockPos pos, Direction dir) {
      if (mc.gameMode != null && mc.player != null) {
         Vec3 hit = Vec3.atCenterOf(pos).add(dir.getStepX() * 0.5, dir.getStepY() * 0.5, dir.getStepZ() * 0.5);
         BlockHitResult blockHit = new BlockHitResult(hit, dir, pos, false);
         mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
      }
   }

   private static void waitForBreakSolid(Minecraft mc, BlockPos pos) throws InterruptedException {
      while (isCurrentRunActive()) {
         if (!isInstaBreakAir(mc, pos)) {
            return;
         }

         Thread.sleep(1L);
      }
   }

   private static void waitBreakClientTicks(int ticks) throws InterruptedException {
      long start = RiptideSharedState.get().getClientTickCounter();

      while (isCurrentRunActive() && RiptideSharedState.get().getClientTickCounter() - start < ticks) {
         Thread.sleep(1L);
      }
   }

   private static void holdMacroSneak(Minecraft mc, String mode) {
      RiptideMacroSneak.hold(mc, mode);
   }

   private static void releaseMacroSneak(Minecraft mc, String mode) {
      RiptideMacroSneak.release(mc, mode);
      if (!RiptideMacroSneak.isPacket(mode)) {
         applyAggregateSneakState(mc);
      }
   }

   private static void runOnClientThread(Minecraft mc, Runnable runnable) throws InterruptedException {
      if (!PackHideState.isHardLocked()) {
         MacroExecutor.RunState scheduledRun = CURRENT_RUN.get();
         Runnable contextual = () -> withRunContext(scheduledRun, runnable);
         if (mc.isSameThread()) {
            if (!PackHideState.isHardLocked()) {
               contextual.run();
            }
         } else {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean pending = new AtomicBoolean(true);
            mc.execute(() -> {
               try {
                  if (!pending.compareAndSet(true, false)) {
                     return;
                  }

                  if (!PackHideState.isHardLocked()) {
                     contextual.run();
                     return;
                  }
               } finally {
                  latch.countDown();
               }
            });
            if (!latch.await(2000L, TimeUnit.MILLISECONDS)) {
               pending.set(false);
            }
         }
      }
   }

   private static void runOnClientThread(Minecraft mc, Runnable runnable, boolean tickAligned) throws InterruptedException {
      if (tickAligned) {
         runOnClientTick(mc, runnable);
      } else {
         runOnClientThread(mc, runnable);
      }
   }

   private static void postOnClientThread(Minecraft mc, Runnable runnable, boolean tickAligned) throws InterruptedException {
      if (tickAligned) {
         runOnClientTick(mc, runnable);
      } else if (!PackHideState.isHardLocked()) {
         MacroExecutor.RunState scheduledRun = CURRENT_RUN.get();
         mc.execute(() -> {
            if (!PackHideState.isHardLocked()) {
               withRunContext(scheduledRun, runnable);
            }
         });
      }
   }

   private static void runOnClientTick(Minecraft mc, Runnable runnable) throws InterruptedException {
      if (!PackHideState.isHardLocked()) {
         MacroExecutor.RunState scheduledRun = CURRENT_RUN.get();
         Runnable contextual = () -> withRunContext(scheduledRun, runnable);
         if (mc.isSameThread()) {
            if (!PackHideState.isHardLocked()) {
               contextual.run();
            }
         } else {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean pending = new AtomicBoolean(true);
            TICK_ALIGNED.add(() -> {
               try {
                  if (!pending.compareAndSet(true, false)) {
                     return;
                  }

                  if (!PackHideState.isHardLocked()) {
                     contextual.run();
                     return;
                  }
               } finally {
                  latch.countDown();
               }
            });
            if (!latch.await(2000L, TimeUnit.MILLISECONDS)) {
               pending.set(false);
            }
         }
      }
   }

   public static void drainTickAligned() {
      if (!AutoTotemModule.operationActive()) {
         Runnable task;
         while ((task = TICK_ALIGNED.poll()) != null) {
            try {
               task.run();
            } catch (Throwable var2) {
               riptide.RiptideClientAddon.LOG.debug("[Riptide] Tick-aligned macro task failed", var2);
            }
         }
      }
   }

   private static <T> T callOnClientThread(Minecraft mc, Supplier<T> supplier, T fallback) throws InterruptedException {
      if (PackHideState.isHardLocked()) {
         return fallback;
      } else {
         MacroExecutor.RunState scheduledRun = CURRENT_RUN.get();
         Supplier<T> contextual = () -> {
            AtomicReference<T> value = new AtomicReference<>(fallback);
            withRunContext(scheduledRun, () -> value.set(supplier.get()));
            return value.get();
         };
         if (mc.isSameThread()) {
            return PackHideState.isHardLocked() ? fallback : contextual.get();
         } else {
            AtomicReference<T> result = new AtomicReference<>(fallback);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean pending = new AtomicBoolean(true);
            mc.execute(() -> {
               try {
                  if (!pending.compareAndSet(true, false)) {
                     return;
                  }

                  if (!PackHideState.isHardLocked()) {
                     result.set(contextual.get());
                     return;
                  }
               } finally {
                  latch.countDown();
               }
            });
            if (!latch.await(2000L, TimeUnit.MILLISECONDS)) {
               pending.set(false);
            }

            return result.get();
         }
      }
   }

   private static <T> T callOnClientThread(Minecraft mc, Supplier<T> supplier, T fallback, boolean tickAligned) throws InterruptedException {
      return tickAligned ? callOnClientTick(mc, supplier, fallback) : callOnClientThread(mc, supplier, fallback);
   }

   private static <T> T callOnClientTick(Minecraft mc, Supplier<T> supplier, T fallback) throws InterruptedException {
      if (PackHideState.isHardLocked()) {
         return fallback;
      } else {
         MacroExecutor.RunState scheduledRun = CURRENT_RUN.get();
         Supplier<T> contextual = () -> {
            AtomicReference<T> value = new AtomicReference<>(fallback);
            withRunContext(scheduledRun, () -> value.set(supplier.get()));
            return value.get();
         };
         if (mc.isSameThread()) {
            return PackHideState.isHardLocked() ? fallback : contextual.get();
         } else {
            AtomicReference<T> result = new AtomicReference<>(fallback);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean pending = new AtomicBoolean(true);
            TICK_ALIGNED.add(() -> {
               try {
                  if (!pending.compareAndSet(true, false)) {
                     return;
                  }

                  if (!PackHideState.isHardLocked()) {
                     result.set(contextual.get());
                     return;
                  }
               } finally {
                  latch.countDown();
               }
            });
            if (!latch.await(2000L, TimeUnit.MILLISECONDS)) {
               pending.set(false);
            }

            return result.get();
         }
      }
   }

   private static void runPlaceAction(Minecraft mc, PlaceAction action) throws InterruptedException {
      if (mc != null && mc.player != null && mc.level != null) {
         ItemTarget target = action.resolvedItemTarget().resolveTemplate(mc);
         if (target != null) {
            if (action.waitForItem && target != null && (target.hasIdentity() || target.hasSlot())) {
               setCurrentStatus("Waiting for " + describeItemTarget(target));
               waitForItemActionEntry(mc, target);
            }

            if (!action.interact) {
               runMacroActionOnClientThread(mc, action, "Place");
            } else {
               BlockPos placePos = action.blockPos == null ? BlockPos.ZERO : action.blockPos;
               Direction face = action.manualDirection && action.direction != null ? action.direction : Direction.UP;
               InteractTiming t = action.interactTiming;
               boolean interactFirst = t == InteractTiming.BEFORE || t == InteractTiming.CUSTOM && action.interactCustomMs < 0;
               long ns = t == InteractTiming.CUSTOM ? Math.abs((long)action.interactCustomMs) * 1000000L : 0L;
               if (interactFirst) {
                  runOnClientThread(mc, () -> sendBlockInteract(mc, placePos, face), action.isTickAligned());
                  if (ns > 0L) {
                     LockSupport.parkNanos(ns);
                  }

                  runMacroActionOnClientThread(mc, action, "Place");
               } else if (t == InteractTiming.WITH) {
                  runOnClientThread(mc, () -> {
                     try {
                        action.execute(mc);
                     } catch (Exception var5x) {
                        riptide.RiptideClientAddon.LOG.debug("[Riptide] Macro WITH-interact action failed", var5x);
                     }

                     sendBlockInteract(mc, placePos, face);
                  }, action.isTickAligned());
               } else if (t == InteractTiming.AFTER_PLUS) {
                  runMacroActionOnClientThread(mc, action, "Place");
                  waitOneTick();
                  runOnClientThread(mc, () -> sendBlockInteract(mc, placePos, face), action.isTickAligned());
               } else {
                  runMacroActionOnClientThread(mc, action, "Place");
                  if (ns > 0L) {
                     LockSupport.parkNanos(ns);
                  }

                  runOnClientThread(mc, () -> sendBlockInteract(mc, placePos, face), action.isTickAligned());
               }
            }
         }
      }
   }

   private static void waitForItemInSpecificHandlerSlot(Minecraft mc, int slotId, ItemTarget target) throws InterruptedException {
      while (isCurrentRunActive()) {
         if (Boolean.TRUE.equals(callOnClientThread(mc, () -> handlerSlotMatchesItem(mc, slotId, target), Boolean.FALSE))) {
            return;
         }

         waitOneTick();
      }
   }

   private static void waitForAnyItemInSpecificHandlerSlot(Minecraft mc, int slotId) throws InterruptedException {
      while (isCurrentRunActive()) {
         if (Boolean.TRUE.equals(callOnClientThread(mc, () -> handlerSlotHasAnyItem(mc, slotId), Boolean.FALSE))) {
            return;
         }

         waitOneTick();
      }
   }

   private static boolean handlerSlotMatchesItem(Minecraft mc, int slotId, ItemTarget target) {
      if (mc.player != null && mc.player.containerMenu != null) {
         int handlerSlot = RiptideInventoryHelper.resolveConfiguredHandlerSlot(mc, slotId);
         if (handlerSlot >= 0 && handlerSlot < mc.player.containerMenu.slots.size()) {
            Slot slot = (Slot)mc.player.containerMenu.slots.get(handlerSlot);
            if (slot != null && !slot.getItem().isEmpty()) {
               ItemTarget resolvedTarget = target == null ? ItemTarget.slotOnly(slotId) : target.copy();
               if (!resolvedTarget.hasSlot()) {
                  resolvedTarget.slot = slotId;
               }

               return resolvedTarget.matches(slot.getItem(), slotId);
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean handlerSlotHasAnyItem(Minecraft mc, int slotId) {
      if (mc.player != null && mc.player.containerMenu != null) {
         int handlerSlot = RiptideInventoryHelper.resolveConfiguredHandlerSlot(mc, slotId);
         if (handlerSlot >= 0 && handlerSlot < mc.player.containerMenu.slots.size()) {
            Slot slot = (Slot)mc.player.containerMenu.slots.get(handlerSlot);
            return slot != null && !slot.getItem().isEmpty();
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static void waitForItemActionTargets(Minecraft mc, ItemAction ia) throws InterruptedException {
      if (ia != null) {
         List<ItemTarget> targets = resolveItemTargets(mc, ia.itemTargets, ia.itemNames);
         if (targets.isEmpty()) {
            if (ia.useSlot && ia.targetSlot >= 0) {
               setCurrentStatus("Waiting for slot " + ia.targetSlot);
               waitForAnyItemInSpecificHandlerSlot(mc, ia.targetSlot);
            }
         } else {
            for (ItemTarget target : targets) {
               waitForItemActionEntry(mc, target);
            }
         }
      }
   }

   private static void waitForItemActionEntry(Minecraft mc, ItemTarget target) throws InterruptedException {
      ItemTarget resolvedTarget = target == null ? null : target.resolveTemplate(mc);
      if (resolvedTarget != null) {
         String itemLabel = describeItemTarget(resolvedTarget);
         int slot = resolvedTarget.hasSlot() ? resolvedTarget.slot : -1;
         if (slot >= 0 && resolvedTarget.hasIdentity()) {
            setCurrentStatus("Waiting for " + itemLabel);
            waitForItemInSpecificHandlerSlot(mc, slot, resolvedTarget);
         } else if (slot >= 0) {
            setCurrentStatus("Waiting for slot " + slot);
            waitForAnyItemInSpecificHandlerSlot(mc, slot);
         } else {
            if (resolvedTarget.hasIdentity()) {
               setCurrentStatus("Waiting for item in GUI: " + itemLabel);
               CompletableFuture<Void> waitFuture = MacroConditionRegistry.waitForItemInHandler(resolvedTarget);
               waitForCondition(waitFuture);
            }
         }
      }
   }

   private static ItemTarget resolveItemTarget(ItemTarget target, String legacyEntry) {
      return target == null || !target.hasSlot() && !target.hasIdentity() ? ItemTarget.fromLegacyEntry(legacyEntry) : target.copy();
   }

   private static List<ItemTarget> resolveItemTargets(Minecraft mc, List<ItemTarget> targets, List<String> legacyEntries) {
      List<ItemTarget> resolved = new ArrayList<>();
      if (targets != null) {
         for (ItemTarget target : targets) {
            if (target != null && (target.hasSlot() || target.hasIdentity())) {
               ItemTarget runtimeTarget = target.resolveTemplate(mc);
               if (runtimeTarget != null) {
                  resolved.add(runtimeTarget);
               }
            }
         }
      }

      if (!resolved.isEmpty()) {
         return resolved;
      } else if (legacyEntries == null) {
         return resolved;
      } else {
         for (String entry : legacyEntries) {
            ItemTarget parsed = ItemTarget.fromLegacyEntry(entry);
            ItemTarget runtimeTarget = parsed.resolveTemplate(mc);
            if (runtimeTarget != null && (runtimeTarget.hasSlot() || runtimeTarget.hasIdentity())) {
               resolved.add(runtimeTarget);
            }
         }

         return resolved;
      }
   }

   private static String describeItemTarget(ItemTarget target) {
      if (target == null) {
         return "";
      } else {
         String summary = target.summaryText();
         if (summary != null && !summary.isBlank()) {
            return summary;
         } else {
            String legacy = target.toLegacyEntry();
            return legacy == null ? "" : legacy;
         }
      }
   }

   private static void runCraftAction(Minecraft mc, CraftAction craftAction) throws InterruptedException {
      if (craftAction != null && craftAction.hasEntries()) {
         for (int index = 0; index < craftAction.entries.size(); index++) {
            CraftAction.CraftEntry entry = craftAction.entries.get(index);
            if (entry != null && entry.hasRecipe()) {
               setCurrentStatus(
                  entry.useMaxAmount
                     ? "Crafting " + entry.resultName + " [Max] (" + (index + 1) + "/" + craftAction.entries.size() + ")"
                     : "Crafting " + entry.resultName + " x" + Math.max(1, entry.amount) + " (" + (index + 1) + "/" + craftAction.entries.size() + ")"
               );
               RiptideCraftingHelper.CraftableRecipeOption option = RiptideCraftingHelper.findCraftableRecipe(mc, entry.recipeKey);
               if (option == null) {
                  option = RiptideCraftingHelper.findCraftableRecipe(mc, entry.recipeId);
               }

               if (option == null) {
                  setCurrentStatus("Recipe not found");
                  RiptideClientMessaging.sendPrefixed(
                     "§cRecipe not found: " + (entry.resultName != null && !entry.resultName.isBlank() ? entry.resultName : "unknown")
                  );
                  return;
               }

               int desiredAmount = RiptideCraftingHelper.getEffectiveRequestedOutput(option, entry.amount, entry.useMaxAmount);
               if (desiredAmount <= 0) {
                  setCurrentStatus("No space or materials");
                  RiptideClientMessaging.sendPrefixed("§cNo space or materials for " + entry.resultName + ".");
                  return;
               }

               RiptideCraftingHelper.CraftExecutionResult result = RiptideCraftingHelper.executeCraftImmediately(
                  mc, entry.recipeKey, entry.recipeId, desiredAmount
               );
               setCurrentStatus(result.message);
               if (!result.success) {
                  RiptideClientMessaging.sendPrefixed("§c" + result.message);
                  return;
               }

               if (isCurrentRunActive()) {
                  CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                  waitForCondition(tickFuture);
               }
            }
         }
      } else {
         setCurrentStatus("Crafting");
         RiptideClientMessaging.sendPrefixed("§cNo craft recipes selected.");
      }
   }

   private static void runGoToAction(Minecraft mc, GoToAction goToAction) throws InterruptedException {
      setCurrentStatus(String.format("Going to %.0f,%.0f,%.0f", goToAction.x, goToAction.y, goToAction.z));
      if (!RiptideCompatManager.isBaritoneAvailable()) {
         RiptideClientMessaging.sendPrefixed("Baritone not installed; skipping goto action.");
      } else {
         int goalX = (int)Math.floor(goToAction.x);
         int goalY = (int)Math.floor(goToAction.y);
         int goalZ = (int)Math.floor(goToAction.z);
         MacroExecutor.RunState run = CURRENT_RUN.get();
         if (!runBaritoneStart(mc, () -> RiptideCompatManager.startBaritoneGoTo(mc, goalX, goalY, goalZ))) {
            RiptideClientMessaging.sendPrefixed("Failed to start Baritone goto.");
         } else {
            try {
               if (!goToAction.waitForArrival) {
                  setCurrentStatus("Goto started");
               } else {
                  long startMs = System.currentTimeMillis();
                  boolean sawBaritoneActivity = false;

                  while (isCurrentRunActive()) {
                     boolean goalActive = RiptideCompatManager.isBaritoneGoalActive();
                     boolean baritoneBusy = RiptideCompatManager.isBaritoneBusy();
                     if (goalActive || baritoneBusy) {
                        sawBaritoneActivity = true;
                     }

                     if (sawBaritoneActivity && !goalActive && !baritoneBusy) {
                        setCurrentStatus("Goto finished");
                        return;
                     }

                     if (!sawBaritoneActivity && System.currentTimeMillis() - startMs >= 1500L && !goalActive && !baritoneBusy) {
                        setCurrentStatus("Goto finished");
                        return;
                     }

                     CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                     waitForCondition(tickFuture);
                  }
               }
            } finally {
               if (!isCurrentRunActive()) {
                  requestOwnedBaritoneStop(run, mc);
               } else {
                  releaseBaritoneOwner(run);
               }
            }
         }
      }
   }

   private static void runMineAction(Minecraft mc, MineAction mineAction) throws InterruptedException {
      String mineDesc = mineAction.targetBlocks.isEmpty() ? "none" : mineAction.targetBlocks.get(0);
      setCurrentStatus("Mining: " + mineDesc);
      if (!mineAction.targetBlocks.isEmpty()) {
         if (!RiptideCompatManager.isBaritoneAvailable()) {
            RiptideClientMessaging.sendPrefixed("Baritone not installed; skipping mine action.");
         } else {
            List<String> blockNames = new ArrayList<>();

            for (String id : mineAction.targetBlocks) {
               if (id != null && !id.isBlank()) {
                  blockNames.add(id.startsWith("minecraft:") ? id.substring(10) : id);
               }
            }

            if (!blockNames.isEmpty()) {
               MacroExecutor.RunState run = CURRENT_RUN.get();
               MacroExecutor.MineProgressTracker mineTracker = mineAction.stopMinedCount
                  ? MacroExecutor.MineProgressTracker.create(mc, mineAction.targetBlocks)
                  : null;
               if (mineTracker != null) {
                  mineTracker.poll(mc);
                  if (run != null) {
                     run.activeMineTracker = mineTracker;
                  }
               }

               try {
                  if (!runBaritoneStart(mc, () -> RiptideCompatManager.startBaritoneMine(mc, blockNames), mineAction.isTickAligned())) {
                     RiptideClientMessaging.sendPrefixed("Failed to start Baritone mine.");
                  } else {
                     long mineStartMs = System.currentTimeMillis();
                     boolean sawBaritoneActivity = false;

                     while (isCurrentRunActive()) {
                        boolean mineActive = RiptideCompatManager.isBaritoneMineActive();
                        boolean baritoneBusy = RiptideCompatManager.isBaritoneBusy();
                        if (mineActive || baritoneBusy) {
                           sawBaritoneActivity = true;
                        }

                        String stopReason = null;
                        int minedDelta = 0;
                        if (mineTracker != null) {
                           mineTracker.poll(mc);
                           minedDelta = mineTracker.destroyedCount();
                           setCurrentStatus("Mining: " + mineDesc + " (" + minedDelta + "/" + mineAction.minedCountTarget + ")");
                           if (minedDelta >= mineAction.minedCountTarget) {
                              stopReason = "Mine target reached";
                           }
                        }

                        if (stopReason == null && mineAction.stopInventoryFull && isInventoryFull(mc)) {
                           stopReason = "Mine stop: inventory full";
                        }

                        if (stopReason == null && mineAction.stopSlotsUsed) {
                           int used = countUsedMainInventorySlots(mc);
                           if (used >= mineAction.slotsUsedThreshold) {
                              stopReason = "Mine stop: slots used";
                           }
                        }

                        if (stopReason == null && mineAction.stopAfterTime && System.currentTimeMillis() - mineStartMs >= mineAction.timeoutSeconds * 1000L) {
                           stopReason = "Mine timeout";
                        }

                        if (stopReason != null) {
                           setCurrentStatus(stopReason);
                           requestOwnedBaritoneStop(run, mc);
                           waitForBaritoneIdle();
                           return;
                        }

                        if (sawBaritoneActivity && !mineActive && !baritoneBusy) {
                           setCurrentStatus("Mine finished");
                           return;
                        }

                        if (!sawBaritoneActivity && System.currentTimeMillis() - mineStartMs >= 1500L && !mineActive && !baritoneBusy) {
                           setCurrentStatus("Mine finished");
                           return;
                        }

                        CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                        waitForCondition(tickFuture);
                     }
                  }
               } finally {
                  if (run != null && run.activeMineTracker == mineTracker) {
                     run.activeMineTracker = null;
                  }

                  if (!isCurrentRunActive()) {
                     requestOwnedBaritoneStop(run, mc);
                  } else {
                     releaseBaritoneOwner(run);
                  }
               }
            }
         }
      }
   }

   private static boolean runBaritoneStart(Minecraft mc, BooleanSupplier starter) throws InterruptedException {
      return runBaritoneStart(mc, starter, false);
   }

   private static boolean runBaritoneStart(Minecraft mc, BooleanSupplier starter, boolean tickAligned) throws InterruptedException {
      MacroExecutor.RunState run = CURRENT_RUN.get();
      if (run != null) {
         BARITONE_OWNER.set(run);
      }

      CountDownLatch latch = new CountDownLatch(1);
      AtomicBoolean started = new AtomicBoolean(false);
      Runnable task = () -> {
         try {
            started.set(starter.getAsBoolean());
         } finally {
            latch.countDown();
         }
      };
      if (tickAligned && !mc.isSameThread()) {
         TICK_ALIGNED.add(task);
      } else {
         mc.execute(task);
      }

      latch.await(2L, TimeUnit.SECONDS);
      if (!started.get()) {
         releaseBaritoneOwner(run);
      }

      return started.get();
   }

   private static void requestBaritoneStop(Minecraft mc) {
      if (mc != null && RiptideCompatManager.isBaritoneAvailable()) {
         mc.execute(() -> RiptideCompatManager.stopBaritone(mc));
      }
   }

   private static void requestOwnedBaritoneStop(MacroExecutor.RunState run, Minecraft mc) {
      if (run != null && BARITONE_OWNER.compareAndSet(run, null)) {
         requestBaritoneStop(mc);
      }
   }

   private static void releaseBaritoneOwner(MacroExecutor.RunState run) {
      if (run != null) {
         BARITONE_OWNER.compareAndSet(run, null);
      }
   }

   private static void waitForBaritoneIdle() {
      long waitStartMs = System.currentTimeMillis();

      while (isCurrentRunActive() && RiptideCompatManager.isBaritoneBusy() && System.currentTimeMillis() - waitStartMs < 3000L) {
         CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
         waitForCondition(tickFuture);
      }
   }

   private static boolean isInventoryFull(Minecraft mc) {
      if (mc.player == null) {
         return false;
      } else {
         for (int slot = 9; slot < 36; slot++) {
            if (mc.player.getInventory().getItem(slot).isEmpty()) {
               return false;
            }
         }

         return true;
      }
   }

   private static int countUsedMainInventorySlots(Minecraft mc) {
      if (mc.player == null) {
         return 0;
      } else {
         int used = 0;

         for (int slot = 9; slot < 36; slot++) {
            if (!mc.player.getInventory().getItem(slot).isEmpty()) {
               used++;
            }
         }

         return used;
      }
   }

   private static void runPayAction(Minecraft mc, PayAction payAction) throws InterruptedException {
      payAction.delayMs = PayAction.normalizeDelay(payAction.delayMs);
      List<String> targets = new ArrayList<>();

      for (String player : payAction.players) {
         if (player != null) {
            MacroTemplate.Resolution playerResolution = MacroVariables.resolve(player, mc);
            if (playerResolution.success()) {
               String trimmed = playerResolution.value().trim();
               if (!trimmed.isEmpty() && !targets.contains(trimmed)) {
                  targets.add(trimmed);
               }
            }
         }
      }

      if (targets.isEmpty()) {
         targets.addAll(RiptidePlayerProbe.everyone(mc, payAction.probeHidden));
      }

      if (!targets.isEmpty() && mc.getConnection() != null) {
         MacroTemplate.Resolution amountResolution = MacroVariables.resolve(payAction.amountInput, mc);
         if (amountResolution.success()) {
            long resolvedAmount = Math.max(0L, PayAction.parseAmount(amountResolution.value()));
            long[] dividedAmounts = payAction.divideEnabled ? PayAction.distribute(resolvedAmount, targets.size()) : null;
            int sent = 0;

            for (int i = 0; i < targets.size() && isCurrentRunActive(); i++) {
               String playerx = targets.get(i);
               long perAmount = dividedAmounts != null ? dividedAmounts[i] : resolvedAmount;
               if (perAmount > 0L) {
                  String amountValue = String.valueOf(perAmount);
                  String amountLabel = PayAction.formatAmount(perAmount);
                  String template = payAction.commandTemplate != null && !payAction.commandTemplate.isBlank()
                     ? payAction.commandTemplate
                     : "/pay <player> <amount>";
                  String command = template.replace("<player>", playerx)
                     .replace("{player}", playerx)
                     .replace("<amount>", amountValue)
                     .replace("{amount}", amountValue)
                     .trim();
                  MacroTemplate.Resolution commandResolution = MacroVariables.resolve(command, mc);
                  if (commandResolution.success()) {
                     command = commandResolution.value().trim();
                     if (!command.isEmpty()) {
                        if (sent > 0 && payAction.delayEnabled && payAction.delayMs > 0) {
                           Thread.sleep(payAction.delayMs);
                        }

                        setCurrentStatus("Paying " + playerx + " " + amountLabel + " (" + (sent + 1) + "/" + targets.size() + ")");
                        runOnClientThread(mc, () -> {
                           try {
                              if (PackHideState.isHardLocked() || mc.getConnection() == null) {
                                 return;
                              }

                              if (command.startsWith("/") && command.length() > 1) {
                                 mc.getConnection().sendCommand(command.substring(1));
                              } else {
                                 mc.getConnection().sendChat(command);
                              }
                           } catch (Exception var3) {
                              RiptideClientMessaging.sendPrefixed("§cPay failed: " + var3.getMessage());
                           }
                        });
                        sent++;
                        if (payAction.confirmMacro != null && !payAction.confirmMacro.isBlank()) {
                           setCurrentStatus("Confirming " + playerx);
                           PayAction.runConfirmMacro(payAction.confirmMacro, () -> !isCurrentRunActive());
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static void stop() {
      String macroName = currentMacro != null && currentMacro.name != null ? currentMacro.name : "";

      for (MacroExecutor.RunState run : activeRunSnapshot()) {
         requestStop(run, true);
      }

      synchronized (RUN_LOCK) {
         ACTIVE_RUNS.clear();
         RUNS_BY_ID.clear();
         rebuildActiveRunListLocked();
         primaryRun = null;
         visibleRunActive = false;
      }

      isRunning.set(false);

      try {
         Minecraft mcInst = Minecraft.getInstance();
         RiptideContainerHold.releaseAllAndFlush(mcInst == null ? null : mcInst.getConnection());
      } catch (Throwable var8) {
      }

      persistentActions.clear();
      releaseBackgroundMoves(backgroundMoves, Minecraft.getInstance(), null);
      backgroundMoves.clear();
      reconcilePersistentInput(Minecraft.getInstance());
      MacroConditionRegistry.cancelAll();
      drainOneShotListeners();
      isWaitingForChat.set(false);
      isWaitingForPacket.set(false);
      synchronized (packetWaitLock) {
         waitingPacketName.set("");
         CompletableFuture<Void> pf = activePacketFuture;
         if (pf != null) {
            pf.cancel(true);
            activePacketFuture = null;
         }
      }

      CompletableFuture<Void> cf = activeChatFuture;
      if (cf != null) {
         cf.cancel(true);
         activeChatFuture = null;
      }

      CompletableFuture<Void> bf = activeBaritoneGoalFuture;
      if (bf != null) {
         bf.cancel(true);
         activeBaritoneGoalFuture = null;
      }

      RiptideCompatManager.stopBaritone(Minecraft.getInstance());
      BARITONE_OWNER.set(null);
      RiptideInstaBreakRenderer.clear();
      Minecraft mc = Minecraft.getInstance();
      PacketGateManager.clearAllAndFlushConfigured(mc == null ? null : mc.getConnection());
      FpsLimitController.clearAll();
      PingSpoofController.clearAllMacros();
      MacroVariables.clear();

      try {
         RiptideLANSync sync = RiptideLANSync.getInstance();
         if (sync.isInSession()) {
            sync.broadcastStepProgress(-1, 0, "");
         }
      } catch (Exception var7) {
      }

      macroThread = null;
      currentMacro = null;
      setCurrentStatus("");
      synchronized (ROTATION_STATE_LOCK) {
         ROTATION_OWNER.set(null);
         isRotating.set(false);
         rotationAlignedFrames = 0;
      }

      if (!macroName.isEmpty()) {
         RiptideClientMessaging.sendPrefixed("§cMacro stopped: " + macroName);
      } else {
         RiptideClientMessaging.sendPrefixed("§cMacro stopped.");
      }
   }

   public static boolean isRunning() {
      return isRunning.get();
   }

   public static int getActiveRunCount() {
      synchronized (RUN_LOCK) {
         return activeRunCountLocked();
      }
   }

   public static boolean hasPacketObservationWork() {
      return PackHideState.isHardLocked()
         ? false
         : isRunning.get()
            || activePacketFuture != null
            || isWaitingForPacket.get()
            || !ONE_SHOT_RECV.isEmpty()
            || !ONE_SHOT_SEND.isEmpty()
            || !TIMED_ONE_SHOT_RECV.isEmpty()
            || !TIMED_ONE_SHOT_SEND.isEmpty();
   }

   public static boolean isMacroRunning(String macroName) {
      if (macroName == null) {
         return false;
      } else {
         String targetKey = macroKey(macroName);

         for (MacroExecutor.RunState run : RUNS_BY_ID.values()) {
            if (run.running.get() && macroKey(macroName(run.macro)).equals(targetKey)) {
               return true;
            }
         }

         return false;
      }
   }

   public static boolean isMacroRunning(RiptideMacro macro) {
      if (macro == null) {
         return false;
      } else {
         for (MacroExecutor.RunState run : RUNS_BY_ID.values()) {
            if (matchesMacro(run, macro, macro.name)) {
               return true;
            }
         }

         return false;
      }
   }

   public static List<MacroExecutor.MacroRunSnapshot> getActiveRunSnapshots() {
      synchronized (RUN_LOCK) {
         ArrayList<MacroExecutor.MacroRunSnapshot> snapshots = new ArrayList<>(ACTIVE_RUNS.size());

         for (MacroExecutor.RunState run : ACTIVE_RUNS.values()) {
            if (run.running.get() && !run.options.hiddenFromProgressHud()) {
               RiptideMacro macro = run.macro;
               String name = macro != null && macro.name != null ? macro.name : "";
               snapshots.add(
                  new MacroExecutor.MacroRunSnapshot(
                     macro, name, run.status == null ? "" : run.status, run.currentStepIndex, run.totalSteps, run.lastCompletedStep
                  )
               );
            }
         }

         return snapshots;
      }
   }

   public static void stopMacro(String macroName) {
      if (macroName != null && !macroName.isBlank()) {
         String targetKey = macroKey(macroName);
         boolean stopped = false;

         for (MacroExecutor.RunState run : activeRunSnapshot()) {
            if (run.running.get() && macroKey(macroName(run.macro)).equals(targetKey)) {
               requestStop(run, true);
               stopped = true;
            }
         }

         if (stopped) {
            synchronized (RUN_LOCK) {
               refreshPrimaryRunLocked();
            }

            RiptideClientMessaging.sendPrefixed("§cMacro stopped: " + macroName);
         }
      }
   }

   public static void stopMacro(RiptideMacro macro) {
      if (macro != null) {
         boolean stopped = false;
         String displayName = macroName(macro);

         for (MacroExecutor.RunState run : activeRunSnapshot()) {
            if (matchesMacro(run, macro, displayName)) {
               requestStop(run, true);
               stopped = true;
            }
         }

         if (stopped) {
            synchronized (RUN_LOCK) {
               refreshPrimaryRunLocked();
            }

            RiptideClientMessaging.sendPrefixed("§cMacro stopped: " + (displayName.isBlank() ? "macro" : displayName));
         }
      }
   }

   public static String getCurrentMacroName() {
      RiptideMacro m = currentMacro;
      return m != null && m.name != null ? m.name : "";
   }

   public static RiptideMacro getCurrentMacro() {
      return currentMacro;
   }

   public static String getCurrentStatus() {
      MacroExecutor.RunState run = primaryRun;
      return run != null ? run.status : currentStatus;
   }

   public static int getCurrentStepIndex() {
      MacroExecutor.RunState run = primaryRun;
      return run != null ? run.currentStepIndex : currentStepIndex;
   }

   public static int getTotalSteps() {
      MacroExecutor.RunState run = primaryRun;
      return run != null ? run.totalSteps : totalSteps;
   }

   public static List<MacroExecutor.RecentChatMessage> getRecentChatMessages() {
      synchronized (recentChatLock) {
         return new ArrayList<>(recentChatMessages);
      }
   }

   public static List<MacroExecutor.RecentGuiTitle> getRecentGuiTitles() {
      synchronized (recentGuiLock) {
         return new ArrayList<>(recentGuiTitles);
      }
   }

   public static void recordRecentGuiScreen(Screen screen) {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null || mc.player == null || mc.level == null) {
         clearRecentCaptureHistory();
      } else if (screen != null && !MacroGuiMatcher.isOwnScreen(screen)) {
         String title = screen.getTitle() == null ? "" : screen.getTitle().getString().trim();
         if (!title.isEmpty()) {
            String type = MacroGuiMatcher.semanticName(screen);
            MacroExecutor.RecentGuiTitle entry = new MacroExecutor.RecentGuiTitle(title, type == null ? "" : type, System.currentTimeMillis());
            synchronized (recentGuiLock) {
               recentGuiTitles.removeIf(existing -> existing.title().equals(entry.title()) && existing.type().equals(entry.type()));
               recentGuiTitles.addFirst(entry);

               while (recentGuiTitles.size() > 128) {
                  recentGuiTitles.removeLast();
               }
            }
         }
      }
   }

   public static void clearRecentCaptureHistory() {
      synchronized (recentChatLock) {
         recentChatMessages.clear();
         recentChatServerKey = "";
         recentChatServerKeyMirror = "";
      }

      synchronized (recentGuiLock) {
         recentGuiTitles.clear();
      }
   }

   public static MacroExecutor.OneShotPacketListener awaitReceive(Predicate<Packet<?>> match) {
      return new MacroExecutor.OneShotPacketListener(ONE_SHOT_RECV, match);
   }

   public static MacroExecutor.OneShotPacketListener awaitSend(Predicate<Packet<?>> match) {
      return new MacroExecutor.OneShotPacketListener(ONE_SHOT_SEND, match);
   }

   private static MacroExecutor.TimedOneShotPacketListener awaitTimedReceive(MacroExecutor.TimedPacketMatch match) {
      return new MacroExecutor.TimedOneShotPacketListener(TIMED_ONE_SHOT_RECV, match);
   }

   private static MacroExecutor.TimedOneShotPacketListener awaitTimedSend(Predicate<Packet<?>> match) {
      return awaitTimedSend((packet, direction, nanoTime) -> match.test(packet));
   }

   private static MacroExecutor.TimedOneShotPacketListener awaitTimedSend(MacroExecutor.TimedPacketMatch match) {
      return new MacroExecutor.TimedOneShotPacketListener(TIMED_ONE_SHOT_SEND, match);
   }

   private static void fireOneShotObservers(List<MacroExecutor.OneShotPacketListener> list, Packet<?> packet) {
      if (!list.isEmpty()) {
         long now = System.nanoTime();
         ArrayList<MacroExecutor.OneShotPacketListener> matched = null;

         for (MacroExecutor.OneShotPacketListener listener : list) {
            boolean drop;
            try {
               drop = listener.offer(packet, now);
            } catch (Throwable var9) {
               drop = true;
            }

            if (drop) {
               if (matched == null) {
                  matched = new ArrayList<>(4);
               }

               matched.add(listener);
            }
         }

         if (matched != null) {
            list.removeAll(matched);
         }
      }
   }

   private static void fireTimedObservers(List<MacroExecutor.TimedOneShotPacketListener> list, MacroExecutor.MacroEvent event) {
      if (!list.isEmpty()) {
         ArrayList<MacroExecutor.TimedOneShotPacketListener> matched = null;

         for (MacroExecutor.TimedOneShotPacketListener listener : list) {
            boolean drop;
            try {
               drop = listener.offer(event);
            } catch (Throwable var7) {
               drop = true;
            }

            if (drop) {
               if (matched == null) {
                  matched = new ArrayList<>(4);
               }

               matched.add(listener);
            }
         }

         if (matched != null) {
            list.removeAll(matched);
         }
      }
   }

   private static void drainOneShotListeners() {
      for (MacroExecutor.OneShotPacketListener listener : ONE_SHOT_RECV) {
         listener.cancel();
      }

      for (MacroExecutor.OneShotPacketListener listener : ONE_SHOT_SEND) {
         listener.cancel();
      }

      for (MacroExecutor.TimedOneShotPacketListener listener : TIMED_ONE_SHOT_RECV) {
         listener.cancel();
      }

      for (MacroExecutor.TimedOneShotPacketListener listener : TIMED_ONE_SHOT_SEND) {
         listener.cancel();
      }
   }

   public static String oneShotCensus() {
      return "runs="
         + getActiveRunCount()
         + " recv="
         + ONE_SHOT_RECV.size()
         + " send="
         + ONE_SHOT_SEND.size()
         + " timedRecv="
         + TIMED_ONE_SHOT_RECV.size()
         + " timedSend="
         + TIMED_ONE_SHOT_SEND.size();
   }

   public static void onPacketSent(Packet<?> packet) {
      if (!PackHideState.isHardLocked()) {
         if (!TIMED_ONE_SHOT_SEND.isEmpty()) {
            fireTimedObservers(TIMED_ONE_SHOT_SEND, new MacroExecutor.MacroEvent(packet, "C2S", System.nanoTime(), null));
         }

         fireOneShotObservers(ONE_SHOT_SEND, packet);
         onPacketObserved(packet, "C2S");
      }
   }

   public static void onPacketReceived(Packet<?> packet) {
      if (!PackHideState.isHardLocked()) {
         if (!TIMED_ONE_SHOT_RECV.isEmpty()) {
            fireTimedObservers(TIMED_ONE_SHOT_RECV, new MacroExecutor.MacroEvent(packet, "S2C", System.nanoTime(), null));
         }

         fireOneShotObservers(ONE_SHOT_RECV, packet);
         if (packet instanceof ClientboundRespawnPacket || packet instanceof ClientboundLoginPacket) {
            MacroConditionRegistry.onRespawnPacket();
         }

         if (packet instanceof ClientboundPlayerPositionPacket) {
            MacroConditionRegistry.onTeleportPacket();
         }

         onPacketObserved(packet, "S2C");
      }
   }

   public static void onPacketObserved(Packet<?> packet, String direction) {
      if (!PackHideState.isHardLocked()) {
         syncRecentChatServerContext();
         List<MacroExecutor.RunState> runs = activeRunSnapshot();
         boolean s2c = "S2C".equals(direction);
         boolean chatWork = false;
         boolean mineWork = false;
         boolean packetWork = false;

         for (int i = 0; i < runs.size(); i++) {
            MacroExecutor.RunState run = runs.get(i);
            if (run.running.get()) {
               if (run.waitingForChat.get()) {
                  chatWork = true;
               }

               if (run.activeMineTracker != null) {
                  mineWork = true;
               }

               if (run.waitingForPacket.get()) {
                  packetWork = true;
               }
            }
         }

         if (s2c && (chatWork || isWaitingForChat.get())) {
            MacroExecutor.ChatCapture capture = extractIncomingChat(packet);
            if (capture != null && capture.message() != null && !capture.message().isBlank()) {
               for (MacroExecutor.RunState run : runs) {
                  Optional<Map<String, MacroValue>> matched = run.running.get() && run.waitingForChat.get()
                     ? captureChatAction(run.waitingChatAction, capture, run.variables)
                     : Optional.empty();
                  if (matched.isPresent()) {
                     run.stagedCaptures.put(run.waitingChatAction, matched.get());
                     CompletableFuture<Void> f = run.chatFuture;
                     if (f != null) {
                        f.complete(null);
                     }
                  }
               }

               if (isCurrentRunActive() && isWaitingForChat.get() && matchesWaitingChat(capture)) {
                  lastMatchedChat.set(capture.displayText());
                  CompletableFuture<Void> f = activeChatFuture;
                  if (f != null) {
                     f.complete(null);
                  }
               }
            }
         }

         if (s2c && mineWork) {
            Minecraft mc = Minecraft.getInstance();

            for (MacroExecutor.RunState runx : runs) {
               MacroExecutor.MineProgressTracker tracker = runx.running.get() ? runx.activeMineTracker : null;
               if (tracker != null) {
                  tracker.observePacket(mc, packet);
               }
            }
         }

         if (isCurrentRunActive()) {
            if (packetWork) {
               for (MacroExecutor.RunState runxx : runs) {
                  if (runxx.running.get() && runxx.waitingForPacket.get()) {
                     CompletableFuture<Void> f = null;
                     synchronized (runxx.packetWaitLock) {
                        if (runxx.waitingForPacket.get() && matchesPacketTarget(runxx.waitingPacketName.get(), packet, direction)) {
                           runxx.lastMatchedPacket = packet;
                           runxx.lastMatchedPacketDirection = direction;
                           f = runxx.packetFuture;
                        }
                     }

                     if (f != null) {
                        f.complete(null);
                     }
                  }
               }
            }

            if (isWaitingForPacket.get()) {
               CompletableFuture<Void> f = null;
               synchronized (packetWaitLock) {
                  if (!isWaitingForPacket.get()) {
                     return;
                  }

                  String target = waitingPacketName.get();
                  if (matchesPacketTarget(target, packet, direction)) {
                     lastReceivedPacket.set(direction + " " + RiptidePacketNamer.getFriendlyName(packet, direction));
                     f = activePacketFuture;
                  }
               }

               if (f != null) {
                  f.complete(null);
               }
            }
         }
      }
   }

   public static void observeIncomingChat(Packet<?> packet) {
      if (!PackHideState.isHardLocked()) {
         MacroExecutor.ChatCapture capture = extractIncomingChat(packet);
         if (capture != null && capture.message() != null && !capture.message().isBlank()) {
            syncRecentChatServerContext();
            recordRecentChat(capture);
         }
      }
   }

   private static void recordRecentChat(MacroExecutor.ChatCapture capture) {
      MacroExecutor.RecentChatMessage message = new MacroExecutor.RecentChatMessage(
         capture.sender() == null ? "" : capture.sender(),
         capture.message(),
         capture.displayText(),
         capture.displayComponent(),
         capture.source(),
         System.currentTimeMillis()
      );
      synchronized (recentChatLock) {
         recentChatMessages.removeIf(existing -> existing.displayText().equals(message.displayText()));
         recentChatMessages.addFirst(message);

         while (recentChatMessages.size() > 256) {
            recentChatMessages.removeLast();
         }
      }
   }

   private static void syncRecentChatServerContext() {
      String currentServerKey = getCurrentMultiplayerServerKey();
      if (!currentServerKey.isEmpty()) {
         if (!currentServerKey.equals(recentChatServerKeyMirror)) {
            synchronized (recentChatLock) {
               if (recentChatServerKey.isEmpty()) {
                  recentChatServerKey = currentServerKey;
                  recentChatServerKeyMirror = currentServerKey;
               } else {
                  if (!recentChatServerKey.equals(currentServerKey)) {
                     recentChatMessages.clear();
                     recentChatServerKey = currentServerKey;
                     recentChatServerKeyMirror = currentServerKey;
                     synchronized (recentGuiLock) {
                        recentGuiTitles.clear();
                     }
                  } else {
                     recentChatServerKeyMirror = currentServerKey;
                  }
               }
            }
         }
      }
   }

   private static String getCurrentMultiplayerServerKey() {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && !mc.isLocalServer()) {
         ServerData entry = mc.getCurrentServer();
         if (entry != null && entry.ip != null && !entry.ip.isBlank()) {
            return normalizeServerKey(entry.ip);
         } else {
            if (mc.getConnection() != null && mc.getConnection().getConnection() != null) {
               SocketAddress address = mc.getConnection().getConnection().getRemoteAddress();
               if (address instanceof InetSocketAddress inet) {
                  String host = inet.getHostString();
                  if ((host == null || host.isBlank()) && inet.getAddress() != null) {
                     host = inet.getAddress().getHostAddress();
                  }

                  if (host != null && !host.isBlank()) {
                     return normalizeServerKey(host + ":" + inet.getPort());
                  }
               } else if (address != null) {
                  String raw = address.toString();
                  if (raw != null && !raw.isBlank()) {
                     return normalizeServerKey(raw);
                  }
               }
            }

            return "";
         }
      } else {
         return "";
      }
   }

   private static String normalizeServerKey(String address) {
      if (address == null) {
         return "";
      } else {
         String stripped = address.startsWith("/") ? address.substring(1) : address;
         return stripped.trim().toLowerCase(Locale.ROOT);
      }
   }

   private static boolean matchesWaitingChat(MacroExecutor.ChatCapture capture) {
      String patternJson = waitingChatPatternJson.get();
      if (!waitingChatIsRegex.get() && patternJson != null && !patternJson.isBlank() && capture.displayComponent() != null) {
         String captureJson = serializeTextComponent(capture.displayComponent());
         if (patternJson.equals(captureJson)) {
            return true;
         }
      }

      String pattern = normalizeManualText(waitingChatPattern.get());
      if (pattern == null || pattern.isBlank()) {
         return true;
      } else if (waitingChatIsRegex.get()) {
         try {
            Pattern compiled = MacroCapturePattern.compiled(pattern);
            return compiled.matcher(capture.message()).matches() || compiled.matcher(capture.displayText()).matches();
         } catch (Exception var4) {
            return false;
         }
      } else {
         int threshold = waitingChatFuzzyPercent.get();
         return fuzzyChatMatch(pattern, capture.message(), threshold) || fuzzyChatMatch(pattern, capture.displayText(), threshold);
      }
   }

   private static boolean matchesChatAction(WaitForChatAction action, MacroExecutor.ChatCapture capture) {
      return captureChatAction(action, capture, currentVariableContext()).isPresent();
   }

   private static Optional<Map<String, MacroValue>> captureChatAction(WaitForChatAction action, MacroExecutor.ChatCapture capture) {
      return captureChatAction(action, capture, currentVariableContext());
   }

   private static Optional<Map<String, MacroValue>> captureChatAction(WaitForChatAction action, MacroExecutor.ChatCapture capture, MacroVariableContext context) {
      if (action != null && capture != null) {
         MacroCapturePattern.Mode mode = action.effectiveMatchMode();
         if (mode == MacroCapturePattern.Mode.MATCH && action.patternJson != null && !action.patternJson.isBlank() && capture.displayComponent() != null) {
            String captureJson = serializeTextComponent(capture.displayComponent());
            if (action.patternJson.equals(captureJson)) {
               return Optional.of(chatValues(action, capture, Map.of()));
            }
         }

         String pattern = normalizeManualText(action.pattern);
         if (mode == MacroCapturePattern.Mode.MATCH) {
            MacroTemplate.Resolution patternResolution = MacroTemplate.resolve(pattern, context, Minecraft.getInstance());
            if (!patternResolution.success()) {
               return Optional.empty();
            }

            pattern = patternResolution.value();
         }

         if (pattern == null || pattern.isBlank()) {
            return Optional.of(chatValues(action, capture, Map.of()));
         } else if (mode != MacroCapturePattern.Mode.MATCH) {
            Optional<MacroCapturePattern.Result> messageResult = MacroCapturePattern.match(mode, pattern, capture.message());
            Optional<MacroCapturePattern.Result> result = messageResult.isPresent()
               ? messageResult
               : MacroCapturePattern.match(mode, pattern, capture.displayText());
            return result.map(value -> chatValues(action, capture, value.values()));
         } else {
            int threshold = WaitForChatAction.clampFuzzyPercent(action.fuzzyPercent);
            boolean matched = fuzzyChatMatch(pattern, capture.message(), threshold) || fuzzyChatMatch(pattern, capture.displayText(), threshold);
            return matched ? Optional.of(chatValues(action, capture, Map.of())) : Optional.empty();
         }
      } else {
         return Optional.empty();
      }
   }

   private static Map<String, MacroValue> chatValues(WaitForChatAction action, MacroExecutor.ChatCapture capture, Map<String, MacroValue> captures) {
      Map<String, MacroValue> values = new LinkedHashMap<>(captures);
      if (action.saveAs != null && !action.saveAs.isBlank()) {
         Map<String, MacroValue> properties = new LinkedHashMap<>();
         properties.put("sender", MacroValue.text(capture.sender()));
         properties.put("message", MacroValue.text(capture.message()));
         properties.put("display", MacroValue.text(capture.displayText()));
         properties.put("source", MacroValue.text(capture.source().name()));
         values.put(action.saveAs, MacroValue.structured(MacroValue.Kind.TEXT, capture.displayText(), properties));
      }

      return Map.copyOf(values);
   }

   private static MacroExecutor.ChatCapture extractIncomingChat(Packet<?> packet) {
      if (packet instanceof ClientboundSystemChatPacket systemChat) {
         Component content = systemChat.content();
         if (content == null) {
            return null;
         } else {
            String raw = content.getString();
            return raw != null && !raw.isBlank() ? buildChatCapture("", raw, raw, content, MacroExecutor.ChatSource.PLAYER) : null;
         }
      } else if (packet instanceof ClientboundDisguisedChatPacket disguisedChat) {
         Component content = disguisedChat.message();
         String text = content == null ? null : content.getString();
         if (text != null && !text.isBlank()) {
            Component decorated = disguisedChat.chatType().decorate(content);
            String sender = disguisedChat.chatType().name().getString();
            return buildChatCapture(sender, text, null, decorated, MacroExecutor.ChatSource.PLAYER);
         } else {
            return null;
         }
      } else if (packet instanceof ClientboundPlayerChatPacket playerChat) {
         Component content = (Component)(playerChat.unsignedContent() != null ? playerChat.unsignedContent() : Component.literal(playerChat.body().content()));
         String message = content.getString();
         if (message == null || message.isBlank()) {
            message = playerChat.body().content();
         }

         if (message != null && !message.isBlank()) {
            Component decorated = playerChat.chatType().decorate(content);
            String sender = playerChat.chatType().name().getString();
            if (sender == null || sender.isBlank()) {
               String byUuid = resolvePlayerName(playerChat.sender());
               if (byUuid != null && !byUuid.isBlank()) {
                  sender = byUuid;
               }
            }

            return buildChatCapture(sender, message, null, decorated, MacroExecutor.ChatSource.PLAYER);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static MacroExecutor.ChatCapture buildChatCapture(
      String sender, String message, String displayText, Component displayComponent, MacroExecutor.ChatSource source
   ) {
      String cleanMessage = sanitizeChatText(message);
      if (cleanMessage.isBlank()) {
         return null;
      } else {
         String cleanSender = sanitizeChatText(sender);
         String cleanDisplay = sanitizeChatText(displayText);
         Component rendered = literalizeTextComponent(displayComponent);
         String display = cleanDisplay;
         if (rendered == null || rendered.getString().isBlank()) {
            rendered = cleanSender.isBlank()
               ? Component.literal(cleanMessage)
               : Component.literal("<" + cleanSender + "> ").append(Component.literal(cleanMessage));
         }

         if (cleanDisplay == null || cleanDisplay.isBlank()) {
            display = sanitizeChatText(rendered.getString());
         }

         MacroExecutor.ChatSource resolvedSource = source != null ? source : MacroExecutor.ChatSource.PLAYER;
         return new MacroExecutor.ChatCapture(cleanSender, cleanMessage, sanitizeChatText(display), rendered, resolvedSource);
      }
   }

   private static String sanitizeChatText(String text) {
      if (text == null) {
         return "";
      } else {
         String stripped = stripLegacyFormatting(text).replace('\n', ' ').replace('\r', ' ');
         StringBuilder out = new StringBuilder(stripped.length());
         int offset = 0;

         while (offset < stripped.length()) {
            int cp = stripped.codePointAt(offset);
            offset += Character.charCount(cp);
            int type = Character.getType(cp);
            if (type != 16 && type != 15 && cp != 8203 && cp != 8204 && cp != 8205 && cp != 8288 && cp != 65038 && cp != 65039) {
               out.appendCodePoint(cp);
            }
         }

         return out.toString().trim();
      }
   }

   public static String normalizeManualText(String text) {
      if (text != null && !text.isBlank()) {
         String stripped = stripLegacyFormatting(text).trim();
         String flattened = flattenManualTextComponentJson(stripped);
         return sanitizeChatText(flattened != null ? flattened : stripped);
      } else {
         return "";
      }
   }

   private static String flattenManualTextComponentJson(String text) {
      if (text != null && !text.isBlank()) {
         String trimmed = text.trim();
         if (!trimmed.startsWith("{") && !trimmed.startsWith("[") && !trimmed.startsWith("\"")) {
            return null;
         } else {
            try {
               JsonElement element = JsonParser.parseString(trimmed);
               if (element != null && !element.isJsonNull()) {
                  if (element.isJsonArray()) {
                     StringBuilder out = new StringBuilder();

                     for (JsonElement child : element.getAsJsonArray()) {
                        Component component = parseTextComponentElement(child);
                        if (component != null) {
                           out.append(component.getString());
                        }
                     }

                     return out.toString();
                  } else {
                     Component component = parseTextComponentElement(element);
                     return component == null ? null : component.getString();
                  }
               } else {
                  return "";
               }
            } catch (RuntimeException var7) {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private static Component parseTextComponentElement(JsonElement element) {
      try {
         Component component = (Component)ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow();
         return literalizeTextComponent(component);
      } catch (RuntimeException var2) {
         return null;
      }
   }

   private static String stripLegacyFormatting(String text) {
      if (text != null && !text.isEmpty()) {
         StringBuilder out = new StringBuilder(text.length());

         for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch == 167 || ch == '&') && i + 1 < text.length()) {
               char next = Character.toLowerCase(text.charAt(i + 1));
               if (next >= '0' && next <= '9' || next >= 'a' && next <= 'f' || next >= 'k' && next <= 'o' || next == 'r' || next == 'x') {
                  i++;
                  continue;
               }
            }

            out.append(ch);
         }

         return out.toString();
      } else {
         return "";
      }
   }

   private static boolean isLikelyPlayerSender(String sender) {
      String cleanSender = sanitizeChatText(sender);
      if (cleanSender.isBlank()) {
         return false;
      } else {
         try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
               for (PlayerInfo entry : mc.getConnection().getListedOnlinePlayers()) {
                  String playerName = entry != null && entry.getProfile() != null ? extractTextValue(entry.getProfile()) : null;
                  if (playerName != null && cleanSender.equalsIgnoreCase(playerName)) {
                     return true;
                  }
               }
            }
         } catch (Throwable var7) {
         }

         try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
               for (AbstractClientPlayer player : mc.level.players()) {
                  String playerName = player != null && player.getGameProfile() != null ? extractTextValue(player.getGameProfile()) : null;
                  if (playerName != null && cleanSender.equalsIgnoreCase(playerName)) {
                     return true;
                  }
               }
            }
         } catch (Throwable var6) {
         }

         return false;
      }
   }

   public static boolean fuzzyChatMatch(String pattern, String candidate, int thresholdPercent) {
      String normalizedPattern = normalizeChatText(pattern);
      String normalizedCandidate = normalizeChatText(candidate);
      if (normalizedPattern.isBlank()) {
         return true;
      } else if (normalizedCandidate.isBlank()) {
         return false;
      } else if (normalizedCandidate.contains(normalizedPattern)) {
         return true;
      } else {
         List<String> patternTokens = tokenizeChat(normalizedPattern);
         List<String> candidateTokens = tokenizeChat(normalizedCandidate);
         if (patternTokens.isEmpty()) {
            return true;
         } else if (thresholdPercent >= 100) {
            return allPatternTokensPresent(patternTokens, normalizedCandidate);
         } else {
            double tokenScore = computeTokenCoverage(patternTokens, candidateTokens);
            double charScore = similarityRatio(normalizedPattern.replace(" ", ""), normalizedCandidate.replace(" ", ""));
            double score = Math.max(tokenScore, tokenScore * 0.7 + charScore * 0.3);
            return score + 1.0E-9 >= Math.max(40, Math.min(100, thresholdPercent)) / 100.0;
         }
      }
   }

   private static String normalizeChatForCompare(String text) {
      if (text == null) {
         return "";
      } else {
         String normalized = Normalizer.normalize(text, Form.NFKC).toLowerCase(Locale.ROOT);
         StringBuilder out = new StringBuilder(normalized.length());

         for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
               out.append(ch);
            } else if (Character.isWhitespace(ch)) {
               out.append(' ');
            } else if (ch != '\'' && ch != '"' && ch != '`' && ch != '-' && ch != '_' && ch != 8217) {
               out.append(' ');
            }
         }

         return out.toString().trim().replaceAll("\\s+", " ");
      }
   }

   public static String normalizeChatText(String text) {
      if (text == null) {
         return "";
      } else {
         String normalized = Normalizer.normalize(text, Form.NFKD).toLowerCase(Locale.ROOT);
         StringBuilder out = new StringBuilder(normalized.length());
         boolean lastWasSpace = true;
         int offset = 0;

         while (offset < normalized.length()) {
            int cp = normalized.codePointAt(offset);
            offset += Character.charCount(cp);
            cp = foldStyledLatinCodePoint(cp);
            int type = Character.getType(cp);
            if (type != 6 && type != 8 && type != 7 && type != 16 && type != 15 && type != 18 && type != 19) {
               if (Character.isLetterOrDigit(cp)) {
                  out.appendCodePoint(cp);
                  lastWasSpace = false;
               } else if (Character.isWhitespace(cp)) {
                  if (!lastWasSpace) {
                     out.append(' ');
                  }

                  lastWasSpace = true;
               } else if (!isJoinerPunctuation(cp)) {
                  if (!lastWasSpace) {
                     out.append(' ');
                  }

                  lastWasSpace = true;
               }
            }
         }

         offset = out.length();
         if (offset > 0 && out.charAt(offset - 1) == ' ') {
            out.setLength(offset - 1);
         }

         return out.toString();
      }
   }

   private static boolean isJoinerPunctuation(int cp) {
      return switch (cp) {
         case 34, 39, 45, 95, 96, 700, 8208, 8209, 8210, 8211, 8212, 8213, 8216, 8217, 8219, 8242, 8722, 65287 -> true;
         default -> false;
      };
   }

   private static int foldStyledLatinCodePoint(int cp) {
      return switch (cp) {
         case 223 -> 115;
         case 230 -> 97;
         case 339 -> 111;
         case 491 -> 113;
         case 610 -> 103;
         case 618 -> 105;
         case 628 -> 110;
         case 640 -> 114;
         case 655 -> 121;
         case 665 -> 98;
         case 668 -> 104;
         case 671 -> 108;
         case 739 -> 120;
         case 7424 -> 97;
         case 7428 -> 99;
         case 7429 -> 100;
         case 7431 -> 101;
         case 7434 -> 106;
         case 7435 -> 107;
         case 7437 -> 109;
         case 7439 -> 111;
         case 7448 -> 112;
         case 7451 -> 116;
         case 7452 -> 117;
         case 7456 -> 118;
         case 7457 -> 119;
         case 7458 -> 122;
         case 42800 -> 102;
         case 42801 -> 115;
         default -> cp;
      };
   }

   private static List<String> tokenizeChat(String normalized) {
      return normalized != null && !normalized.isBlank() ? List.of(normalized.split(" ")) : List.of();
   }

   private static boolean allPatternTokensPresent(List<String> patternTokens, String normalizedCandidate) {
      for (String token : patternTokens) {
         if (!normalizedCandidate.contains(token)) {
            return false;
         }
      }

      return true;
   }

   private static double computeTokenCoverage(List<String> patternTokens, List<String> candidateTokens) {
      if (patternTokens.isEmpty()) {
         return 1.0;
      } else if (candidateTokens.isEmpty()) {
         return 0.0;
      } else {
         double total = 0.0;

         for (String patternToken : patternTokens) {
            double best = 0.0;

            for (String candidateToken : candidateTokens) {
               best = Math.max(best, tokenSimilarity(patternToken, candidateToken));
               if (best >= 1.0) {
                  break;
               }
            }

            total += best;
         }

         return total / patternTokens.size();
      }
   }

   private static double tokenSimilarity(String left, String right) {
      if (left.equals(right)) {
         return 1.0;
      } else if (left.isBlank() || right.isBlank()) {
         return 0.0;
      } else {
         return !left.contains(right) && !right.contains(left)
            ? similarityRatio(left, right)
            : (double)Math.min(left.length(), right.length()) / Math.max(left.length(), right.length());
      }
   }

   private static double similarityRatio(String left, String right) {
      if (left.equals(right)) {
         return 1.0;
      } else {
         int maxLen = Math.max(left.length(), right.length());
         if (maxLen == 0) {
            return 1.0;
         } else {
            int distance = levenshteinDistance(left, right);
            return Math.max(0.0, 1.0 - (double)distance / maxLen);
         }
      }
   }

   private static int levenshteinDistance(String left, String right) {
      int[] previous = new int[right.length() + 1];
      int[] current = new int[right.length() + 1];
      int j = 0;

      while (j <= right.length()) {
         previous[j] = j++;
      }

      for (int i = 1; i <= left.length(); i++) {
         current[0] = i;

         for (int jx = 1; jx <= right.length(); jx++) {
            int cost = left.charAt(i - 1) == right.charAt(jx - 1) ? 0 : 1;
            current[jx] = Math.min(Math.min(current[jx - 1] + 1, previous[jx] + 1), previous[jx - 1] + cost);
         }

         int[] temp = previous;
         previous = current;
         current = temp;
      }

      return previous[right.length()];
   }

   private static String extractChatName(Object value) {
      Object direct = firstNonNull(
         invokeFirstNoArg(value, "name", "getName", "targetName", "getTargetName"),
         getRecordComponentValue(value, 0),
         findRecordComponentByMethodNames(value, 2, "name", "targetName", "type")
      );
      return sanitizeChatText(extractTextValue(direct));
   }

   private static String resolvePlayerName(UUID uuid) {
      if (uuid == null) {
         return "";
      } else {
         try {
            if (Minecraft.getInstance().getConnection() != null) {
               PlayerInfo entry = Minecraft.getInstance().getConnection().getPlayerInfo(uuid);
               if (entry != null && entry.getProfile() != null) {
                  String profileName = extractTextValue(entry.getProfile());
                  if (profileName != null && !profileName.isBlank()) {
                     return profileName;
                  }
               }
            }
         } catch (Throwable var4) {
         }

         try {
            if (Minecraft.getInstance().level != null) {
               for (AbstractClientPlayer player : Minecraft.getInstance().level.players()) {
                  if (uuid.equals(player.getUUID()) && player.getGameProfile() != null) {
                     String profileName = extractTextValue(player.getGameProfile());
                     if (profileName != null && !profileName.isBlank()) {
                        return profileName;
                     }
                  }
               }
            }
         } catch (Throwable var5) {
         }

         return "";
      }
   }

   private static Object firstNonNull(Object... values) {
      if (values == null) {
         return null;
      } else {
         for (Object value : values) {
            if (value != null) {
               return value;
            }
         }

         return null;
      }
   }

   private static Object invokeFirstNoArg(Object target, String... names) {
      if (target != null && names != null) {
         Class<?> type = target.getClass();

         for (String name : names) {
            try {
               Method method = type.getMethod(name);
               method.setAccessible(true);
               return method.invoke(target);
            } catch (Throwable var8) {
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static Object getRecordComponentValue(Object target, int index) {
      if (target == null) {
         return null;
      } else {
         try {
            RecordComponent[] components = target.getClass().getRecordComponents();
            if (components != null && index >= 0 && index < components.length) {
               Method accessor = components[index].getAccessor();
               accessor.setAccessible(true);
               return accessor.invoke(target);
            } else {
               return null;
            }
         } catch (Throwable var4) {
            return null;
         }
      }
   }

   private static Object findRecordComponentByValueType(Object target, Class<?> type) {
      if (target != null && type != null) {
         try {
            RecordComponent[] components = target.getClass().getRecordComponents();
            if (components == null) {
               return null;
            }

            for (RecordComponent component : components) {
               if (type.isAssignableFrom(component.getType())) {
                  Method accessor = component.getAccessor();
                  accessor.setAccessible(true);
                  return accessor.invoke(target);
               }
            }
         } catch (Throwable var8) {
         }

         return null;
      } else {
         return null;
      }
   }

   private static Object findRecordComponentByMethodNames(Object target, int fallbackIndex, String... names) {
      Object direct = invokeFirstNoArg(target, names);
      return direct != null ? direct : getRecordComponentValue(target, fallbackIndex);
   }

   private static Object findRecordTextComponent(Object target, Object... excludedValues) {
      if (target == null) {
         return null;
      } else {
         try {
            RecordComponent[] components = target.getClass().getRecordComponents();
            if (components == null) {
               return null;
            }

            label52:
            for (RecordComponent component : components) {
               Method accessor = component.getAccessor();
               accessor.setAccessible(true);
               Object value = accessor.invoke(target);
               if (value != null) {
                  for (Object excluded : excludedValues) {
                     if (excluded == value) {
                        continue label52;
                     }
                  }

                  if (value instanceof Component || value instanceof CharSequence || value instanceof Optional) {
                     return value;
                  }
               }
            }
         } catch (Throwable var13) {
         }

         return null;
      }
   }

   private static Component extractTextComponent(Object value) {
      if (value == null) {
         return null;
      } else if (value instanceof Optional<?> optional) {
         return optional.isPresent() ? extractTextComponent(optional.get()) : null;
      } else if (value instanceof Component text) {
         return literalizeTextComponent(text);
      } else if (value instanceof CharSequence chars) {
         return Component.literal(chars.toString());
      } else if (value instanceof UUID uuid) {
         return Component.literal(resolvePlayerName(uuid));
      } else {
         try {
            Method getComponent = value.getClass().getMethod("getText");
            getComponent.setAccessible(true);
            Object result = getComponent.invoke(value);
            Component text = extractTextComponent(result);
            if (text != null) {
               return text;
            }
         } catch (Throwable var5) {
         }

         try {
            Method getString = value.getClass().getMethod("getString");
            getString.setAccessible(true);
            Object result = getString.invoke(value);
            if (result != null) {
               return Component.literal(String.valueOf(result));
            }
         } catch (Throwable var4) {
         }

         return null;
      }
   }

   public static Component literalizeTextComponent(Component text) {
      if (text == null) {
         return null;
      } else {
         MutableComponent literalized = Component.empty();

         try {
            text.visit((style, string) -> {
               if (string != null && !string.isEmpty()) {
                  literalized.append(Component.literal(string).setStyle(copyVisualStyle(style)));
               }

               return Optional.empty();
            }, Style.EMPTY);
         } catch (Throwable var3) {
         }

         return !literalized.getString().isEmpty() ? literalized : Component.literal(text.getString());
      }
   }

   private static Style copyVisualStyle(Style style) {
      if (style != null && !style.isEmpty()) {
         Style safe = Style.EMPTY;
         if (style.getFont() != null) {
            safe = safe.withFont(style.getFont());
         }

         if (style.getColor() != null) {
            safe = safe.withColor(style.getColor());
         }

         if (style.isBold()) {
            safe = safe.withBold(true);
         }

         if (style.isItalic()) {
            safe = safe.withItalic(true);
         }

         if (style.isUnderlined()) {
            safe = safe.withUnderlined(true);
         }

         if (style.isStrikethrough()) {
            safe = safe.withStrikethrough(true);
         }

         if (style.isObfuscated()) {
            safe = safe.withObfuscated(true);
         }

         return safe;
      } else {
         return Style.EMPTY;
      }
   }

   public static String serializeTextComponent(Component text) {
      if (text == null) {
         return "";
      } else {
         try {
            Component safeComponent = literalizeTextComponent(text);
            return safeComponent == null
               ? ""
               : GSON.toJson((JsonElement)ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, safeComponent).getOrThrow());
         } catch (Throwable var2) {
            return "";
         }
      }
   }

   public static Component deserializeTextComponent(String json) {
      if (json != null && !json.isBlank()) {
         try {
            return literalizeTextComponent(((Component)ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow()).copy());
         } catch (Throwable var2) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static String extractTextValue(Object value) {
      if (value == null) {
         return null;
      } else if (value instanceof Optional<?> optional) {
         return optional.isPresent() ? extractTextValue(optional.get()) : null;
      } else if (value instanceof Component text) {
         return text.getString();
      } else if (value instanceof CharSequence chars) {
         return chars.toString();
      } else if (value instanceof UUID uuid) {
         return resolvePlayerName(uuid);
      } else {
         try {
            Method getString = value.getClass().getMethod("getString");
            getString.setAccessible(true);
            Object result = getString.invoke(value);
            if (result != null) {
               return String.valueOf(result);
            }
         } catch (Throwable var5) {
         }

         try {
            Method getName = value.getClass().getMethod("getName");
            getName.setAccessible(true);
            if (getName.invoke(value) instanceof CharSequence chars) {
               return chars.toString();
            }
         } catch (Throwable var4) {
         }

         return null;
      }
   }

   public static void onRender(float tickDelta) {
      if (isRotating.get()) {
         MacroExecutor.RunState owner = ROTATION_OWNER.get();
         if (owner != null && !owner.running.get()) {
            cancelRotation(owner);
         } else if (owner == null && !isRunning.get()) {
            synchronized (ROTATION_STATE_LOCK) {
               if (ROTATION_OWNER.get() == null) {
                  isRotating.set(false);
               }
            }
         } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
               float currentYaw = mc.player.getYRot();
               float currentPitch = mc.player.getXRot();
               double deltaYaw = Mth.wrapDegrees(targetYaw - currentYaw);
               double deltaPitch = targetPitch - currentPitch;
               double diagonalDistance = Math.hypot(deltaYaw, deltaPitch);
               if (diagonalDistance <= 0.35) {
                  mc.player.setYRot(targetYaw);
                  mc.player.setXRot(targetPitch);
                  rotationAlignedFrames++;
                  if (rotationAlignedFrames >= 2) {
                     synchronized (ROTATION_STATE_LOCK) {
                        if (ROTATION_OWNER.get() == owner) {
                           isRotating.set(false);
                           ROTATION_OWNER.set(null);
                        }
                     }
                  }
               } else {
                  rotationAlignedFrames = 0;
                  double frameStep = Math.min(diagonalDistance, rotationSpeed * Math.max(tickDelta, 1.0F));
                  double stepYaw = deltaYaw / diagonalDistance * frameStep;
                  double stepPitch = deltaPitch / diagonalDistance * frameStep;
                  mc.player.setYRot(currentYaw + (float)stepYaw);
                  mc.player.setXRot(currentPitch + (float)stepPitch);
               }
            }
         }
      }
   }

   public static boolean hasRenderWork() {
      return isRotating.get();
   }

   private static void startSmoothRotation(float yaw, float pitch, double speed) {
      MacroExecutor.RunState run;
      for (run = CURRENT_RUN.get(); run != null && run.running.get(); LockSupport.parkNanos(500000L)) {
         synchronized (ROTATION_STATE_LOCK) {
            MacroExecutor.RunState owner = ROTATION_OWNER.get();
            if (owner == null || owner == run || !owner.running.get()) {
               ROTATION_OWNER.set(run);
               targetYaw = yaw;
               targetPitch = pitch;
               rotationSpeed = speed;
               rotationAlignedFrames = 0;
               isRotating.set(true);
               return;
            }
         }
      }

      if (run == null) {
         synchronized (ROTATION_STATE_LOCK) {
            if (ROTATION_OWNER.get() != null) {
               return;
            }

            targetYaw = yaw;
            targetPitch = pitch;
            rotationSpeed = speed;
            rotationAlignedFrames = 0;
            isRotating.set(true);
         }
      }
   }

   private static void waitForSmoothRotationCompletion() {
      MacroExecutor.RunState run = CURRENT_RUN.get();

      while (isCurrentRunActive() && isRotating.get() && (run == null || ROTATION_OWNER.get() == run)) {
         LockSupport.parkNanos(1000000L);
      }
   }

   private static void cancelRotation(MacroExecutor.RunState run) {
      if (run != null) {
         synchronized (ROTATION_STATE_LOCK) {
            if (ROTATION_OWNER.get() == run) {
               isRotating.set(false);
               rotationAlignedFrames = 0;
               ROTATION_OWNER.set(null);
            }
         }
      }
   }

   private static LookAtBlockAction.RotationTarget resolveLookAtTargetOnClient(Minecraft mc, LookAtBlockAction action) {
      if (mc != null && action != null) {
         AtomicReference<LookAtBlockAction.RotationTarget> targetRef = new AtomicReference<>();
         CountDownLatch latch = new CountDownLatch(1);
         mc.execute(() -> {
            try {
               targetRef.set(action.resolveRotationTarget(mc));
            } finally {
               latch.countDown();
            }
         });

         while (isCurrentRunActive()) {
            try {
               if (latch.await(10L, TimeUnit.MILLISECONDS)) {
                  break;
               }
            } catch (InterruptedException var5) {
               Thread.currentThread().interrupt();
               break;
            }
         }

         return targetRef.get();
      } else {
         return null;
      }
   }

   public static void stopAll() {
      stop();
   }

   private static void executeBurstPackets(Minecraft mc, List<SendPacketAction> batch) {
      if (mc.getConnection() == null) {
         RiptideClientMessaging.sendPrefixed("§cNo network connection for burst!");
      } else {
         List<RiptideSharedState.QueuedPacket> allQueuedPackets = new ArrayList<>();

         for (SendPacketAction action : batch) {
            allQueuedPackets.addAll(action.getPackets());
         }

         if (!allQueuedPackets.isEmpty()) {
            List<Packet<?>> regeneratedPackets = new ArrayList<>(allQueuedPackets.size());

            for (RiptideSharedState.QueuedPacket qp : allQueuedPackets) {
               if (qp.packet != null) {
                  regeneratedPackets.add(qp.packet);
               }
            }

            if (regeneratedPackets.isEmpty()) {
               RiptideClientMessaging.sendPrefixed("§cBurst: All packets failed to regenerate!");
            } else {
               ClientPacketListener handler = mc.getConnection();

               for (Packet<?> packet : regeneratedPackets) {
                  Packet<?> toSend = packet;
                  if (packet instanceof ServerboundContainerClickPacket) {
                     toSend = PacketRegenerator.regenerate(packet);
                     if (toSend == null) {
                        continue;
                     }
                  }

                  handler.send(toSend);
               }

               if (regeneratedPackets.size() > 1) {
                  RiptideClientMessaging.sendPrefixed("§a Burst sent " + regeneratedPackets.size() + " packets");
               }
            }
         }
      }
   }

   private static List<Packet<?>> preGeneratePackets(RiptideMacro macro, int startIdx, int count) {
      new ArrayList();
      int maxIdx = count < 0 ? macro.actions.size() : Math.min(startIdx + count, macro.actions.size());
      return preGeneratePackets(macro, startIdx, count, maxIdx);
   }

   private static List<Packet<?>> preGeneratePackets(RiptideMacro macro, int startIdx, int count, int endIdx) {
      List<Packet<?>> result = new ArrayList<>();
      int maxIdx = Math.min(endIdx, count < 0 ? macro.actions.size() : Math.min(startIdx + count, macro.actions.size()));
      Minecraft mc = Minecraft.getInstance();

      for (int j = startIdx; j < maxIdx; j++) {
         MacroAction act = macro.actions.get(j);
         if (!(act instanceof SendPacketAction spa)) {
            break;
         }

         for (RiptideSharedState.QueuedPacket qp : spa.getPackets()) {
            if (qp.packet != null) {
               result.add(qp.packet);
            }
         }
      }

      return result;
   }

   private static void executePreGeneratedBurst(Minecraft mc, List<Packet<?>> packets) {
      if (!packets.isEmpty() && mc.getConnection() != null) {
         ClientPacketListener handler = mc.getConnection();

         for (Packet<?> p : packets) {
            Packet<?> toSend = p;
            if (p instanceof ServerboundContainerClickPacket) {
               toSend = PacketRegenerator.regenerate(p);
               if (toSend == null) {
                  continue;
               }
            }

            handler.send(toSend);
         }

         RiptideModule module = RiptideModule.get();
         if (module.shouldForceChannelFlush()) {
            try {
               Connection connection = handler.getConnection();
               if (connection != null) {
                  connection.flushChannel();
               }
            } catch (Exception var6) {
            }
         }
      }
   }

   private static int countPreGeneratedActions(RiptideMacro macro, int startIdx, int packetCount) {
      return countPreGeneratedActions(macro, startIdx, packetCount, macro.actions.size());
   }

   private static int countPreGeneratedActions(RiptideMacro macro, int startIdx, int packetCount, int endIdx) {
      int actionsToSkip = 0;
      int packetsRemaining = packetCount;

      for (int j = startIdx; j < endIdx && packetsRemaining > 0; j++) {
         MacroAction act = macro.actions.get(j);
         if (!(act instanceof SendPacketAction spa)) {
            break;
         }

         int actionPackets = spa.getPackets().size();
         packetsRemaining -= actionPackets;
         actionsToSkip++;
      }

      return actionsToSkip;
   }

   static {
      for (MacroActionResources.SharedClientResource resource : MacroActionResources.SharedClientResource.values()) {
         SHARED_RESOURCE_LOCKS.put(resource, new ReentrantLock(true));
      }

      NEXT_RUN_ID = new AtomicLong(1L);
      ACTIVE_RUNS = new LinkedHashMap<>();
      RUNS_BY_ID = new ConcurrentHashMap<>();
      RUN_STATE_REVISION = new AtomicLong();
      CURRENT_RUN = new ThreadLocal<>();
      SUPPRESS_POST_GUI_AFTER = ThreadLocal.withInitial(() -> false);
      primaryRun = null;
      currentMacro = null;
      macroThread = null;
      lastReceivedPacket = new AtomicReference<>("");
      currentStatus = "";
      currentStepIndex = -1;
      totalSteps = 0;
      isWaitingForPacket = new AtomicBoolean(false);
      waitingPacketName = new AtomicReference<>("");
      packetWaitLock = new Object();
      isWaitingForChat = new AtomicBoolean(false);
      waitingChatPattern = new AtomicReference<>("");
      waitingChatIsRegex = new AtomicBoolean(false);
      waitingChatPatternJson = new AtomicReference<>("");
      waitingChatFuzzyPercent = new AtomicInteger(100);
      lastMatchedChat = new AtomicReference<>("");
      recentChatLock = new Object();
      recentChatMessages = new ArrayDeque<>();
      recentGuiLock = new Object();
      recentGuiTitles = new ArrayDeque<>();
      recentChatServerKey = "";
      activePacketFuture = null;
      activeChatFuture = null;
      activeBaritoneGoalFuture = null;
      BARITONE_OWNER = new AtomicReference<>();
      isRotating = new AtomicBoolean(false);
      ROTATION_OWNER = new AtomicReference<>();
      ROTATION_STATE_LOCK = new Object();
      persistentActions = new CopyOnWriteArrayList<>();
      backgroundMoves = new ConcurrentHashMap<>();
      REPEAT_PACKET_CONTEXT = new ThreadLocal<>();
      REPEAT_PACKET_SKIP = ThreadLocal.withInitial(() -> 0);
      ACTIVE_RUN_LIST = List.of();
      PARSED_TARGET_CACHE = new ConcurrentHashMap<>();
      EMPTY_PACKET_TARGET = new MacroExecutor.ParsedPacketTarget("", "", "");
      SUGGESTION_CACHE = new ConcurrentHashMap<>();
      PENDING_FETCHES = new ConcurrentHashMap<>();
      SUGGESTION_UNIONS = new ConcurrentHashMap<>();
      MAIN_DRAIN_BARRIER = new ThreadLocal<>();
      INSTANT_BURST = ThreadLocal.withInitial(() -> new int[1]);
      TICK_ALIGNED = new ConcurrentLinkedQueue<>();
      ONE_SHOT_EXPIRE_NANOS = TimeUnit.MINUTES.toNanos(10L);
      ONE_SHOT_RECV = new CopyOnWriteArrayList<>();
      ONE_SHOT_SEND = new CopyOnWriteArrayList<>();
      TIMED_ONE_SHOT_RECV = new CopyOnWriteArrayList<>();
      TIMED_ONE_SHOT_SEND = new CopyOnWriteArrayList<>();
      recentChatServerKeyMirror = "";
   }

   private static final class ApiExecutionContext implements MacroExecutionContext {
      private final MacroExecutor.RunState run;

      private ApiExecutionContext(MacroExecutor.RunState run) {
         this.run = run;
      }

      @Override
      public Minecraft mc() {
         return Minecraft.getInstance();
      }

      @Override
      public void runOnClientThread(Runnable r) {
         if (r != null) {
            Runnable safe = () -> MacroExecutor.withRunContext(
               this.run,
               () -> {
                  try {
                     r.run();
                  } catch (Throwable var2x) {
                     RiptideClientMessaging.sendPrefixed(
                        "§cAddon action (client task) error: " + (var2x.getMessage() == null ? var2x.getClass().getSimpleName() : var2x.getMessage())
                     );
                  }
               }
            );

            try {
               MacroExecutor.runOnClientThread(Minecraft.getInstance(), safe);
            } catch (InterruptedException var4) {
               Thread.currentThread().interrupt();
            }
         }
      }

      @Override
      public <T> T callOnClientThread(Supplier<T> s) {
         if (s == null) {
            return null;
         } else {
            Supplier<T> safe = () -> {
               AtomicReference<T> value = new AtomicReference<>();
               MacroExecutor.withRunContext(
                  this.run,
                  () -> {
                     try {
                        value.set(s.get());
                     } catch (Throwable var3) {
                        RiptideClientMessaging.sendPrefixed(
                           "§cAddon action (client task) error: " + (var3.getMessage() == null ? var3.getClass().getSimpleName() : var3.getMessage())
                        );
                     }
                  }
               );
               return value.get();
            };

            try {
               return MacroExecutor.callOnClientThread(Minecraft.getInstance(), safe, null);
            } catch (InterruptedException var4) {
               Thread.currentThread().interrupt();
               return null;
            }
         }
      }

      @Override
      public void waitTicks(int ticks) {
         MacroExecutor.withRunContext(this.run, () -> {
            for (int i = 0; i < ticks && MacroExecutor.isCurrentRunActive(); i++) {
               MacroExecutor.waitForCondition(MacroConditionRegistry.waitForNextTick());
            }
         });
      }

      @Override
      public void awaitCondition(CompletableFuture<Void> future) {
         if (future != null) {
            MacroExecutor.withRunContext(this.run, () -> MacroExecutor.waitForCondition(future));
         }
      }

      @Override
      public void awaitCondition(AddonCondition condition) {
         if (condition != null) {
            MacroExecutor.withRunContext(this.run, () -> MacroExecutor.waitForCondition(MacroConditionRegistry.await(condition)));
         }
      }

      @Override
      public boolean isActive() {
         return this.run != null && this.run.running.get() && !PackHideState.isHardLocked();
      }

      @Override
      public void setStatus(String status) {
         MacroExecutor.withRunContext(this.run, () -> MacroExecutor.setCurrentStatus(status));
      }

      @Override
      public void sendPacket(Packet<?> packet) {
         if (packet != null) {
            this.runOnClientThread(() -> {
               Minecraft mc = Minecraft.getInstance();
               if (mc.getConnection() != null) {
                  mc.getConnection().send(packet);
               }
            });
         }
      }

      @Override
      public Optional<MacroValue> variable(String name) {
         return this.run == null ? Optional.empty() : this.run.variables.get(name);
      }

      @Override
      public void setVariable(String name, MacroValue value) {
         if (this.run != null) {
            this.run.variables.set(name, value);
         }
      }

      @Override
      public void removeVariable(String name) {
         if (this.run != null) {
            this.run.variables.remove(name);
         }
      }

      @Override
      public Map<String, MacroValue> variables() {
         return this.run == null ? Map.of() : this.run.variables.snapshot();
      }

      @Override
      public MacroTemplate.Resolution resolveTemplate(String template) {
         return MacroTemplate.resolve(template, this.run == null ? null : this.run.variables, Minecraft.getInstance());
      }
   }

   private record BreakContinuePlan(int result, boolean breaksThisTick, boolean breaksNextTick) {
   }

   private record BreakStartPlan(int result, boolean instant, boolean interactFirst) {
   }

   private record CachedSuggestions(List<String> suggestions, Object connection) {
   }

   private record ChatCapture(String sender, String message, String displayText, Component displayComponent, MacroExecutor.ChatSource source) {
   }

   public static enum ChatSource {
      PLAYER,
      SERVER;
   }

   private record DurabilityCandidate(int slot, int score) {
   }

   private static final class DurabilityUseNextSession {
      private String lockedRegistryId = "";
      private int activeSlot = -1;
      private int preferredHotbarSlot = 0;
      private boolean firstSlotPending = false;
      private boolean observedInitialSlot = false;
      private boolean fallbackOnly = false;
      private ItemTarget initialTarget = new ItemTarget();
   }

   private record InlineNextActions(List<MacroAction> actions, int lastIndex) {
      static MacroExecutor.InlineNextActions empty() {
         return new MacroExecutor.InlineNextActions(List.of(), -1);
      }
   }

   private record MacroEvent(Packet<?> packet, String direction, long nanoTime, MacroExecutor.ChatCapture chatCapture, Map<String, MacroValue> captures) {
      MacroEvent(Packet<?> packet, String direction, long nanoTime, MacroExecutor.ChatCapture chatCapture) {
         this(packet, direction, nanoTime, chatCapture, Map.of());
      }

      MacroEvent(Packet<?> packet, String direction, long nanoTime, MacroExecutor.ChatCapture chatCapture, Map<String, MacroValue> captures) {
         captures = captures == null ? Map.of() : Map.copyOf(captures);
         this.packet = packet;
         this.direction = direction;
         this.nanoTime = nanoTime;
         this.chatCapture = chatCapture;
         this.captures = captures;
      }

      String label() {
         if (this.packet == null) {
            return this.direction != null && !this.direction.isBlank() ? this.direction : "client event";
         } else {
            String dir = this.direction != null && !this.direction.isBlank() ? this.direction + " " : "";
            return dir + RiptidePacketNamer.getFriendlyName(this.packet, this.direction == null ? "" : this.direction);
         }
      }
   }

   public record MacroRunSnapshot(RiptideMacro macro, String name, String status, int currentStepIndex, int totalSteps, int lastCompletedStep) {
   }

   private static final class MineProgressTracker {
      private static final int POLL_RADIUS = 8;
      private final Set<Block> targetBlocks;
      private final HashMap<Long, Boolean> observedTargetPositions = new HashMap<>();
      private final HashSet<Long> countedPositions = new HashSet<>();
      private int destroyed;

      private MineProgressTracker(Set<Block> targetBlocks) {
         this.targetBlocks = targetBlocks;
      }

      static MacroExecutor.MineProgressTracker create(Minecraft mc, List<String> rawTargets) {
         Set<Block> blocks = new LinkedHashSet<>();
         if (rawTargets != null) {
            for (String raw : rawTargets) {
               Block block = resolveBlock(raw);
               if (block != null && block != Blocks.AIR) {
                  blocks.add(block);
               }
            }
         }

         return blocks.isEmpty() ? null : new MacroExecutor.MineProgressTracker(blocks);
      }

      private static Block resolveBlock(String raw) {
         if (raw != null && !raw.isBlank()) {
            String value = raw.trim();

            try {
               Identifier id = value.contains(":") ? Identifier.parse(value) : Identifier.fromNamespaceAndPath("minecraft", value);
               return !BuiltInRegistries.BLOCK.containsKey(id) ? null : (Block)BuiltInRegistries.BLOCK.getValue(id);
            } catch (Throwable var3) {
               return null;
            }
         } else {
            return null;
         }
      }

      synchronized int destroyedCount() {
         return this.destroyed;
      }

      synchronized void poll(Minecraft mc) {
         if (mc != null && mc.player != null && mc.level != null) {
            BlockPos center = mc.player.blockPosition();
            int minY = Math.max(mc.level.getMinY(), center.getY() - 8);
            int maxY = Math.min(mc.level.getMaxY() - 1, center.getY() + 8);

            for (int x = center.getX() - 8; x <= center.getX() + 8; x++) {
               for (int y = minY; y <= maxY; y++) {
                  for (int z = center.getZ() - 8; z <= center.getZ() + 8; z++) {
                     BlockPos pos = new BlockPos(x, y, z);
                     this.updateObservedPosition(pos, mc.level.getBlockState(pos));
                  }
               }
            }
         }
      }

      synchronized void observePacket(Minecraft mc, Packet<?> packet) {
         if (mc != null && mc.level != null && packet != null) {
            if (packet instanceof ClientboundBlockUpdatePacket blockUpdate) {
               this.observeBlockChange(mc, blockUpdate.getPos(), blockUpdate.getBlockState());
            } else if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate) {
               try {
                  sectionUpdate.runUpdates((pos, state) -> {
                     if (pos != null && state != null) {
                        this.observeBlockChange(mc, pos, state);
                     }
                  });
               } catch (Throwable var6) {
               }
            }
         }
      }

      private void observeBlockChange(Minecraft mc, BlockPos pos, BlockState newState) {
         if (mc != null && mc.level != null && pos != null && newState != null) {
            if (this.isNearPlayer(mc, pos)) {
               BlockPos stablePos = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
               BlockState oldState = mc.level.getBlockState(stablePos);
               if (this.isTarget(oldState) && !this.isTarget(newState)) {
                  this.countDestroyed(stablePos.asLong());
               }

               this.updateObservedPosition(stablePos, newState);
            }
         }
      }

      private boolean isNearPlayer(Minecraft mc, BlockPos pos) {
         if (mc != null && mc.player != null && pos != null) {
            BlockPos playerPos = mc.player.blockPosition();
            int radius = 12;
            return Math.abs(pos.getX() - playerPos.getX()) <= radius
               && Math.abs(pos.getY() - playerPos.getY()) <= radius
               && Math.abs(pos.getZ() - playerPos.getZ()) <= radius;
         } else {
            return false;
         }
      }

      private void updateObservedPosition(BlockPos pos, BlockState state) {
         if (pos != null && state != null) {
            long key = pos.asLong();
            boolean nowTarget = this.isTarget(state);
            boolean wasTarget = Boolean.TRUE.equals(this.observedTargetPositions.get(key));
            if (nowTarget) {
               this.observedTargetPositions.put(key, Boolean.TRUE);
            } else {
               if (wasTarget) {
                  this.countDestroyed(key);
               }

               this.observedTargetPositions.remove(key);
            }
         }
      }

      private boolean isTarget(BlockState state) {
         return state != null && this.targetBlocks.contains(state.getBlock());
      }

      private void countDestroyed(long posKey) {
         if (this.countedPositions.add(posKey)) {
            this.destroyed++;
         }
      }
   }

   public static final class OneShotPacketListener {
      public final CompletableFuture<Packet<?>> future = new CompletableFuture<>();
      private final List<MacroExecutor.OneShotPacketListener> registry;
      private final Predicate<Packet<?>> match;
      private final long expireAtNanos;

      OneShotPacketListener(List<MacroExecutor.OneShotPacketListener> registry, Predicate<Packet<?>> match) {
         this.registry = registry;
         this.match = match;
         this.expireAtNanos = System.nanoTime() + MacroExecutor.ONE_SHOT_EXPIRE_NANOS;
         registry.add(this);
      }

      boolean offer(Packet<?> packet, long nowNanos) {
         if (this.future.isDone()) {
            return true;
         } else if (nowNanos - this.expireAtNanos >= 0L) {
            this.future.cancel(true);
            return true;
         } else if (this.match.test(packet)) {
            this.future.complete(packet);
            return true;
         } else {
            return false;
         }
      }

      public boolean isDone() {
         return this.future.isDone();
      }

      public Packet<?> getDoneOrNull() {
         if (!this.future.isDone()) {
            return null;
         } else {
            try {
               return this.future.getNow(null);
            } catch (Throwable var2) {
               return null;
            }
         }
      }

      public void cancel() {
         this.registry.remove(this);
         if (!this.future.isDone()) {
            this.future.cancel(true);
         }
      }
   }

   private record ParsedPacketTarget(String direction, String expectedName, String normalizedExpected) {
   }

   private record PendingFetch(CompletableFuture<List<String>> future, Object connection) {
   }

   private static final class PrearmedWait {
      final MacroAction action;
      final CompletableFuture<?> future;
      final Runnable cleanup;
      final int completedPacketTargets;

      PrearmedWait(MacroAction action, CompletableFuture<?> future, Runnable cleanup, int completedPacketTargets) {
         this.action = action;
         this.future = future;
         this.cleanup = cleanup == null ? () -> {} : cleanup;
         this.completedPacketTargets = Math.max(0, completedPacketTargets);
      }

      void cleanup() {
         try {
            this.cleanup.run();
         } catch (Throwable var2) {
         }
      }
   }

   public record RecentChatMessage(
      String sender, String message, String displayText, Component displayComponent, MacroExecutor.ChatSource source, long timestampMs
   ) {
   }

   public record RecentGuiTitle(String title, String type, long timestampMs) {
   }

   private record RepeatPacketContext(RiptideMacro macro, int startIdx, int endIdx) {
   }

   public record RunOptions(boolean silentLifecycle, boolean hiddenFromProgressHud, boolean skipLanProgress, boolean backgroundRun) {
      public static final MacroExecutor.RunOptions NORMAL = new MacroExecutor.RunOptions(false, false, false, false);

      public static MacroExecutor.RunOptions silentBackground() {
         return new MacroExecutor.RunOptions(true, true, true, true);
      }
   }

   private static final class RunState {
      final long id;
      final RiptideMacro sourceMacro;
      final RiptideMacro macro;
      final AtomicBoolean running = new AtomicBoolean(true);
      final AtomicBoolean finished = new AtomicBoolean(false);
      final CopyOnWriteArrayList<MacroAction> persistentActions = new CopyOnWriteArrayList<>();
      final ConcurrentHashMap<MoveAction, int[]> backgroundMoves = new ConcurrentHashMap<>();
      final CopyOnWriteArrayList<MacroAction> finallyActions = new CopyOnWriteArrayList<>();
      final Object packetWaitLock = new Object();
      final AtomicBoolean waitingForPacket = new AtomicBoolean(false);
      final AtomicReference<String> waitingPacketName = new AtomicReference<>("");
      volatile CompletableFuture<Void> packetFuture = null;
      volatile Packet<?> lastMatchedPacket = null;
      volatile String lastMatchedPacketDirection = "";
      final AtomicBoolean waitingForChat = new AtomicBoolean(false);
      volatile WaitForChatAction waitingChatAction = null;
      volatile CompletableFuture<Void> chatFuture = null;
      volatile CompletableFuture<Void> baritoneGoalFuture = null;
      volatile MacroExecutor.MineProgressTracker activeMineTracker = null;
      volatile Thread macroThread = null;
      volatile String status = "";
      volatile int currentStepIndex = -1;
      volatile int totalSteps = 0;
      volatile int lastCompletedStep = 0;
      volatile int branchElseStart = -1;
      volatile int branchElseCount = 0;
      volatile int fpsStepHeaderIndex = -1;
      volatile int fpsStepEndIndex = -1;
      volatile int pingStepHeaderIndex = -1;
      volatile int pingStepEndIndex = -1;
      volatile long fpsBackgroundDeadlineNanos = 0L;
      volatile long pingBackgroundDeadlineNanos = 0L;
      volatile Map<String, Integer> labelIndex = null;
      volatile int totalJumpsThisRun = 0;
      volatile int jumpBudget = 0;
      final IdentityHashMap<MacroAction, MacroExecutor.PrearmedWait> prearmedWaits = new IdentityHashMap<>();
      final Map<MacroAction, Map<String, MacroValue>> stagedCaptures = Collections.synchronizedMap(new IdentityHashMap<>());
      final Map<MacroAction, CaptureListSelector.State> captureSelection = Collections.synchronizedMap(new IdentityHashMap<>());
      final MacroExecutor.RunOptions options;
      final MacroVariableContext variables = new MacroVariableContext();

      RunState(long id, RiptideMacro sourceMacro, RiptideMacro macro, MacroExecutor.RunOptions options) {
         this.id = id;
         this.sourceMacro = sourceMacro;
         this.macro = macro;
         this.options = options == null ? MacroExecutor.RunOptions.NORMAL : options;
         this.totalSteps = macro != null ? macro.actions.size() : 0;
      }
   }

   private static final class SafeGroupSnapshot {
      final boolean valid;
      final ItemStack sourceStack;
      final int sourceCount;
      final Map<Integer, Integer> startTargetCounts;
      final Map<Integer, Integer> expectedTargetCounts;

      SafeGroupSnapshot(
         boolean valid, ItemStack sourceStack, int sourceCount, Map<Integer, Integer> startTargetCounts, Map<Integer, Integer> expectedTargetCounts
      ) {
         this.valid = valid;
         this.sourceStack = sourceStack == null ? ItemStack.EMPTY : sourceStack;
         this.sourceCount = sourceCount;
         this.startTargetCounts = startTargetCounts == null ? Map.of() : Map.copyOf(startTargetCounts);
         this.expectedTargetCounts = expectedTargetCounts == null ? Map.of() : Map.copyOf(expectedTargetCounts);
      }

      MacroExecutor.SafeGroupSnapshot withExpectedTargetCounts(Map<Integer, Integer> expectedTargetCounts) {
         return new MacroExecutor.SafeGroupSnapshot(this.valid, this.sourceStack, this.sourceCount, this.startTargetCounts, expectedTargetCounts);
      }

      static MacroExecutor.SafeGroupSnapshot invalid() {
         return new MacroExecutor.SafeGroupSnapshot(false, ItemStack.EMPTY, 0, Map.of(), Map.of());
      }
   }

   private record SafeInventoryClick(int slotId, int button, ContainerInput input) {
   }

   private static final class SafeQuickCraftPlan {
      final boolean valid;
      final int quickCraftType;
      final int placeCount;
      final int cost;
      final Map<Integer, Integer> residualCounts;
      final Map<Integer, Integer> expectedTargetCounts;

      SafeQuickCraftPlan(
         boolean valid, int quickCraftType, int placeCount, int cost, Map<Integer, Integer> residualCounts, Map<Integer, Integer> expectedTargetCounts
      ) {
         this.valid = valid;
         this.quickCraftType = quickCraftType;
         this.placeCount = placeCount;
         this.cost = cost;
         this.residualCounts = residualCounts == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(residualCounts));
         this.expectedTargetCounts = expectedTargetCounts == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(expectedTargetCounts));
      }

      static MacroExecutor.SafeQuickCraftPlan invalid() {
         return new MacroExecutor.SafeQuickCraftPlan(false, 0, 0, Integer.MAX_VALUE, Map.of(), Map.of());
      }
   }

   private static final class SuggestionUnion {
      final Object connection;
      final LinkedHashSet<String> names = new LinkedHashSet<>();
      final ArrayDeque<String> sweepQueue = new ArrayDeque<>();
      int alphabetCursor;
      int cap;

      SuggestionUnion(Object connection, String baseQuery) {
         this.connection = connection;
         this.sweepQueue.add(baseQuery);
      }
   }

   private static final class TimedOneShotPacketListener {
      public final CompletableFuture<MacroExecutor.MacroEvent> future = new CompletableFuture<>();
      private final List<MacroExecutor.TimedOneShotPacketListener> registry;
      private final MacroExecutor.TimedPacketMatch match;
      private final long expireAtNanos;

      TimedOneShotPacketListener(List<MacroExecutor.TimedOneShotPacketListener> registry, MacroExecutor.TimedPacketMatch match) {
         this.registry = registry;
         this.match = match;
         this.expireAtNanos = System.nanoTime() + MacroExecutor.ONE_SHOT_EXPIRE_NANOS;
         registry.add(this);
      }

      boolean offer(MacroExecutor.MacroEvent event) {
         if (this.future.isDone()) {
            return true;
         } else if (event.nanoTime() - this.expireAtNanos >= 0L) {
            this.future.cancel(true);
            return true;
         } else if (this.match.test(event.packet(), event.direction(), event.nanoTime())) {
            this.future.complete(event);
            return true;
         } else {
            return false;
         }
      }

      public void cancel() {
         this.registry.remove(this);
         if (!this.future.isDone()) {
            this.future.cancel(true);
         }
      }
   }

   private interface TimedPacketMatch {
      boolean test(Packet<?> var1, String var2, long var3);
   }
}
