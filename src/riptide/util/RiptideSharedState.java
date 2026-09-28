package riptide.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import riptide.modules.PackHideState;
import riptide.modules.RiptideModule;
import riptide.util.macro.CraftAction;

public final class RiptideSharedState {
   private static final RiptideSharedState INSTANCE = new RiptideSharedState();
   private volatile boolean sendGuiPackets = true;
   private volatile boolean delayGuiPackets;
   private volatile boolean allowSignEditing = true;
   private volatile boolean allowBookUpdate = true;
   private volatile boolean bypassResourcePack;
   private volatile boolean resourcePackForceDeny;
   private volatile boolean xCarryActive;
   private volatile boolean xCarryForced;
   private volatile boolean xCarryArmorBypass;
   private volatile Set<Integer> xCarryForcedSlotMask = Collections.emptySet();
   private volatile boolean xCarryForcedCarryCursor;
   private volatile BlockPos lastInteractedBlockPos = null;
   private volatile String lastSentMessage = "";
   private volatile RiptideContainerTarget lastContainerTarget = null;
   private volatile boolean useCustomPackets = false;
   private final Set<Class<? extends Packet<?>>> c2sPackets = ConcurrentHashMap.newKeySet();
   private final Set<Class<? extends Packet<?>>> s2cPackets = ConcurrentHashMap.newKeySet();
   private final List<RiptideSharedState.QueuedPacket> delayedPackets = new CopyOnWriteArrayList<>();
   private volatile boolean staggeredPacketSend = false;
   private volatile int staggeredSendDelay = 1;
   private final List<RiptideSharedState.QueuedPacket> staggeredQueue = new CopyOnWriteArrayList<>();
   private final List<RiptideSharedState.QueuedPacket> staggeredDisplayQueue = new CopyOnWriteArrayList<>();
   private final List<RiptideSharedState.QueuedPacket> lastFlushedQueue = new CopyOnWriteArrayList<>();
   private final AtomicInteger queueRenderRevision = new AtomicInteger();
   private int staggeredTickCounter = 0;
   private volatile ClientPacketListener staggeredNetworkHandler = null;
   private int staggeredTotal = 0;
   private boolean activeSendUsesExplicitDelays = false;
   private static final int REPORT_RING_SIZE = 32;
   private final Deque<RiptideSharedState.ReportSample> reportRing = new ArrayDeque<>(32);
   private volatile RiptideSharedState.DelayMode delayMode = RiptideSharedState.DelayMode.TICKS;
   private RiptideSharedState.DelayMode staggeredDelayMode = RiptideSharedState.DelayMode.TICKS;
   private long flushStartNanos = 0L;
   private int flushStartClientTick = 0;
   private long activeCaptureLastNanos = -1L;
   private int activeCaptureLastClientTick = -1;
   private int activeCaptureTailDelay = 0;
   private String pendingQueueCompletionMessage = null;
   private Screen storedScreen;
   private AbstractContainerMenu storedAbstractContainerMenu;
   private boolean isFlushing = false;
   private boolean fabricatorOverlayVisible = false;
   private int fabricatorOverlayX = 500;
   private int fabricatorOverlayY = 5;
   private String fabricatorSlotValue = "0";
   private String fabricatorItemNameValue = "";
   private String fabricatorTimesValue = "1";
   private int fabricatorActionIndex = 0;
   private int fabricatorButtonIndex = 0;
   private boolean fabricatorDropWholeStack = false;
   private boolean fabricatorCraftUseMaxAmount = false;
   private String fabricatorCraftSearchValue = "";
   private int fabricatorCraftSelectedRecipeId = -1;
   private String fabricatorCraftSelectedRecipeKey = "";
   private int fabricatorCraftScrollOffset = 0;
   private final List<CraftAction.CraftEntry> fabricatorCraftPlanEntries = new ArrayList<>();
   private int fabricatorCraftPlanSelectedIndex = -1;
   private int fabricatorCraftPlanScrollOffset = 0;
   private boolean lanSyncOverlayVisible = false;
   private int lanSyncOverlayX = 500;
   private int lanSyncOverlayY = 5;
   private int lanSyncOverlayActiveTab = 0;
   private int lanSyncOverlayScrollOffset = 0;
   private boolean lanSyncOverlayPerUserMode = false;
   private String lanSyncOverlaySelectedMacroName = "";
   private String lanSyncOverlayExpandedExecutePeer = "";
   private String lanSyncOverlaySelectedPeer = "";
   private boolean macroListOverlayVisible = false;
   private int macroListOverlayX = 500;
   private int macroListOverlayY = 250;
   private int macroListOverlayScrollOffset = 0;
   private String macroListOverlaySearch = "";
   private boolean queueEditorOverlayVisible = false;
   private int queueEditorOverlayX = 100;
   private int queueEditorOverlayY = 100;
   private int serverInfoOverlayActiveTab = 0;
   private int serverInfoOverlayPluginScrollOffset = 0;
   private String serverInfoOverlaySelectedPlugin = "";
   private String serverInfoOverlayStateAddress = "";
   private volatile String realServerVersion = "";
   private volatile String realServerVersionAddress = "";
   private int serverInfoOverlayProbeDelayMs = 50;
   private int serverInfoOverlayInfoWidth = 252;
   private int serverInfoOverlayInfoHeight = 258;
   private int serverInfoOverlayPluginWidth = 280;
   private int serverInfoOverlayPluginHeight = 280;
   private boolean macroEditorVisible = false;
   private RiptideMacro editingMacro = null;
   private int macroEditorPanelX = 400;
   private int macroEditorPanelY = 100;
   private boolean blockSelectorVisible = false;
   private volatile boolean gbreakCaptureMode = false;
   private volatile int gbreakPacketCount = 0;
   private Runnable gbreakCallback = null;
   private final List<RiptideSharedState.QueuedPacket> gbreakCapturedPackets = new ArrayList<>();
   private volatile boolean captureMode = false;
   private volatile long lastCaptureNanos = -1L;
   private volatile int lastCaptureClientTick = -1;
   private volatile int clientTickCounter = 0;
   private volatile double serverMsPerTick = 50.0;
   private volatile long lastTimeSyncMs = -1L;
   private final Map<String, RiptideWindowLayout> windowLayouts = new HashMap<>();
   private final Map<String, RiptideSharedState.ServerPluginScan> serverPluginScans = new HashMap<>();
   private final List<String> overlayOrder = new ArrayList<>();
   private String focusedOverlayId = "";
   private static final boolean PAYLOAD_TRACE = Boolean.getBoolean("riptide.payload.trace");
   private volatile boolean suppressNextContainerClosePacket = false;
   private volatile boolean suppressNextSignUpdatePacket = false;
   private volatile boolean suppressNextBookEditPacket = false;
   private volatile boolean forceNextSignUpdatePacket = false;
   private volatile boolean forceNextBookEditPacket = false;
   private volatile Consumer<BlockPos> blockCaptureCallback = null;
   private volatile BiConsumer<BlockPos, Direction> directionalBlockCaptureCallback = null;
   private volatile Runnable attackCaptureCallback = null;
   private volatile Runnable captureCancelCallback = null;
   private volatile Consumer<String> entityCaptureCallback = null;
   private volatile boolean entityCaptureSpecific = false;
   private volatile boolean placeCaptureActive = false;

   public synchronized void pushReport(RiptideSharedState.ReportSample sample) {
      if (sample != null) {
         this.reportRing.addFirst(sample);

         while (this.reportRing.size() > 32) {
            this.reportRing.removeLast();
         }
      }
   }

   public synchronized RiptideSharedState.ReportSample getLatestReport(String label) {
      if (label == null) {
         return this.reportRing.isEmpty() ? null : this.reportRing.peekFirst();
      } else {
         for (RiptideSharedState.ReportSample s : this.reportRing) {
            if (label.equals(s.label)) {
               return s;
            }
         }

         return null;
      }
   }

   public synchronized List<RiptideSharedState.ReportSample> getRecentReports(int n) {
      List<RiptideSharedState.ReportSample> out = new ArrayList<>(Math.min(n, this.reportRing.size()));
      int i = 0;

      for (RiptideSharedState.ReportSample s : this.reportRing) {
         if (i++ >= n) {
            break;
         }

         out.add(s);
      }

      return out;
   }

   private RiptideSharedState() {
   }

   public static RiptideSharedState get() {
      return INSTANCE;
   }

   public boolean isMacroEditorVisible() {
      return this.macroEditorVisible;
   }

   public void setMacroEditorVisible(boolean visible) {
      this.macroEditorVisible = visible;
   }

   public RiptideMacro getEditingMacro() {
      return this.editingMacro;
   }

   public void setEditingMacro(RiptideMacro macro) {
      this.editingMacro = macro;
   }

   public int getMacroEditorPanelX() {
      return this.macroEditorPanelX;
   }

   public void setMacroEditorPanelX(int x) {
      this.macroEditorPanelX = x;
   }

   public int getMacroEditorPanelY() {
      return this.macroEditorPanelY;
   }

   public void setMacroEditorPanelY(int y) {
      this.macroEditorPanelY = y;
   }

   public synchronized RiptideWindowLayout getWindowLayout(String id) {
      RiptideWindowLayout layout = this.windowLayouts.get(id);
      return layout == null ? null : new RiptideWindowLayout(layout.x, layout.y, layout.width, layout.height, layout.visible, layout.collapsed);
   }

   public synchronized void setWindowLayout(String id, RiptideWindowLayout layout) {
      if (id != null && !id.isEmpty() && layout != null) {
         RiptideWindowLayout trueGeometry = RiptideOverlayManager.get().clampedAwayTrueGeometry(id);
         if (trueGeometry != null) {
            layout = new RiptideWindowLayout(trueGeometry.x, trueGeometry.y, trueGeometry.width, trueGeometry.height, layout.visible, layout.collapsed);
         }

         this.windowLayouts.put(id, new RiptideWindowLayout(layout.x, layout.y, layout.width, layout.height, layout.visible, layout.collapsed));
      }
   }

   public synchronized List<String> getOverlayOrder() {
      return new ArrayList<>(this.overlayOrder);
   }

   public synchronized void setOverlayOrder(List<String> order) {
      this.overlayOrder.clear();
      if (order != null) {
         for (String id : order) {
            if (id != null && !id.isEmpty() && !this.overlayOrder.contains(id)) {
               this.overlayOrder.add(id);
            }
         }
      }
   }

   public synchronized String getFocusedOverlayId() {
      return this.focusedOverlayId == null ? "" : this.focusedOverlayId;
   }

   public synchronized void setFocusedOverlayId(String overlayId) {
      this.focusedOverlayId = overlayId == null ? "" : overlayId.trim();
   }

   public synchronized RiptideSharedState.ServerPluginScan getServerPluginScan(String address) {
      return this.getServerPluginScan(address, "");
   }

   public synchronized RiptideSharedState.ServerPluginScan getServerPluginScan(String address, String contextSignature) {
      String key = buildServerPluginScanKey(address, contextSignature);
      if (key.isEmpty()) {
         return null;
      } else {
         RiptideSharedState.ServerPluginScan scan = this.serverPluginScans.get(key);
         return scan == null ? null : scan.copy();
      }
   }

   public synchronized void setServerPluginScan(String address, List<String> plugins, Map<String, List<String>> pluginCommands) {
      this.setServerPluginScan(address, "", plugins, pluginCommands, Map.of());
   }

   public synchronized void setServerPluginScan(String address, String contextSignature, List<String> plugins, Map<String, List<String>> pluginCommands) {
      this.setServerPluginScan(address, contextSignature, plugins, pluginCommands, Map.of());
   }

   public synchronized void setServerPluginScan(
      String address, String contextSignature, List<String> plugins, Map<String, List<String>> pluginCommands, Map<String, String> pluginEvidence
   ) {
      String normalizedAddress = normalizeServerAddress(address);
      String normalizedContext = normalizeServerPluginContext(contextSignature);
      String key = buildServerPluginScanKey(normalizedAddress, normalizedContext);
      if (!key.isEmpty()) {
         this.serverPluginScans
            .put(key, new RiptideSharedState.ServerPluginScan(normalizedAddress, normalizedContext, plugins, pluginCommands, pluginEvidence));
      }
   }

   public synchronized void clearServerPluginScan(String address) {
      String normalized = normalizeServerAddress(address);
      if (!normalized.isEmpty()) {
         this.serverPluginScans.keySet().removeIf(key -> key.equals(normalized) || key.startsWith(normalized + "|"));
      }
   }

   private static String normalizeServerAddress(String address) {
      return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
   }

   private static String normalizeServerPluginContext(String contextSignature) {
      return contextSignature == null ? "" : contextSignature.trim().toLowerCase(Locale.ROOT);
   }

   private static String buildServerPluginScanKey(String address, String contextSignature) {
      String normalizedAddress = normalizeServerAddress(address);
      if (normalizedAddress.isEmpty()) {
         return "";
      } else {
         String normalizedContext = normalizeServerPluginContext(contextSignature);
         return normalizedContext.isEmpty() ? normalizedAddress + "|default" : normalizedAddress + "|" + normalizedContext;
      }
   }

   public boolean isBlockSelectorVisible() {
      return this.blockSelectorVisible;
   }

   public void setBlockSelectorVisible(boolean visible) {
      this.blockSelectorVisible = visible;
   }

   public boolean shouldSendGuiPackets() {
      return this.sendGuiPackets;
   }

   public void setSendGuiPackets(boolean value) {
      this.sendGuiPackets = value;
   }

   public boolean shouldDelayGuiPackets() {
      return this.delayGuiPackets;
   }

   public void setDelayGuiPackets(boolean value) {
      if (!value || !PackHideState.isHardLocked()) {
         this.delayGuiPackets = value;
      }
   }

   public boolean isXCarryActive() {
      return this.xCarryActive;
   }

   public void setXCarryActive(boolean value) {
      this.xCarryActive = value;
   }

   public boolean isXCarryForced() {
      return this.xCarryForced;
   }

   public void setXCarryForced(boolean value) {
      if (!value || this.xCarryForcedSlotMask != null && !this.xCarryForcedSlotMask.isEmpty() || this.xCarryForcedCarryCursor) {
         this.xCarryForced = value;
         if (!value) {
            this.xCarryForcedSlotMask = Collections.emptySet();
            this.xCarryForcedCarryCursor = false;
         }
      }
   }

   public Set<Integer> getXCarryForcedSlotMask() {
      return this.xCarryForcedSlotMask;
   }

   public boolean isXCarryForcedCarryCursor() {
      return this.xCarryForcedCarryCursor;
   }

   public void setXCarryForcedTargets(Set<Integer> slotMask, boolean carryCursor) {
      LinkedHashSet<Integer> copy = new LinkedHashSet<>();
      if (slotMask != null) {
         for (Integer slot : slotMask) {
            if (slot != null && slot >= 0) {
               copy.add(slot);
            }
         }
      }

      this.xCarryForcedSlotMask = Collections.unmodifiableSet(copy);
      this.xCarryForcedCarryCursor = carryCursor;
   }

   public void mergeXCarryForcedTargets(Set<Integer> slotMask, boolean carryCursor) {
      LinkedHashSet<Integer> merged = new LinkedHashSet<>();
      if (this.xCarryForced && this.xCarryForcedSlotMask != null) {
         for (Integer slot : this.xCarryForcedSlotMask) {
            if (slot != null && slot >= 0) {
               merged.add(slot);
            }
         }
      }

      if (slotMask != null) {
         for (Integer slotx : slotMask) {
            if (slotx != null && slotx >= 0) {
               merged.add(slotx);
            }
         }
      }

      this.xCarryForcedSlotMask = Collections.unmodifiableSet(merged);
      this.xCarryForcedCarryCursor = this.xCarryForced && this.xCarryForcedCarryCursor || carryCursor;
   }

   public boolean isXCarryArmorBypass() {
      return this.xCarryArmorBypass;
   }

   public void setXCarryArmorBypass(boolean value) {
      this.xCarryArmorBypass = value;
   }

   public String getLastSentMessage() {
      return this.lastSentMessage == null ? "" : this.lastSentMessage;
   }

   public void setLastSentMessage(String message) {
      this.lastSentMessage = message == null ? "" : message;
   }

   public BlockPos getLastInteractedBlockPos() {
      return this.lastInteractedBlockPos;
   }

   public void setLastInteractedBlockPos(BlockPos pos) {
      this.lastInteractedBlockPos = pos;
   }

   public RiptideContainerTarget getLastContainerTarget() {
      return this.lastContainerTarget;
   }

   public void setLastContainerTarget(RiptideContainerTarget target) {
      this.lastContainerTarget = target;
      if (target != null && target.isBlock()) {
         this.lastInteractedBlockPos = target.blockPos();
      }
   }

   public synchronized List<RiptideSharedState.QueuedPacket> getDelayedPackets() {
      return new ArrayList<>(this.delayedPackets);
   }

   public int getQueueRenderRevision() {
      return this.queueRenderRevision.get();
   }

   public synchronized RiptideSharedState.QueueRenderSnapshot getQueueRenderSnapshot(boolean sending, int maxLines) {
      List<RiptideSharedState.QueuedPacket> source = sending ? this.staggeredQueue : this.delayedPackets;
      int totalCount = source.size();
      if (totalCount != 0 && maxLines > 0) {
         int limit = Math.min(totalCount, maxLines);
         ArrayList<RiptideSharedState.QueuedPacket> visiblePackets = new ArrayList<>(limit);

         for (RiptideSharedState.QueuedPacket packet : source) {
            if (packet != null && packet.packet != null) {
               int insertAt = visiblePackets.size();

               for (int i = 0; i < visiblePackets.size(); i++) {
                  RiptideSharedState.QueuedPacket existing = visiblePackets.get(i);
                  int byDelay = Integer.compare(existing.getDelay(), packet.getDelay());
                  if (byDelay > 0 || byDelay == 0 && existing.getId() > packet.getId()) {
                     insertAt = i;
                     break;
                  }
               }

               if (insertAt < limit) {
                  visiblePackets.add(insertAt, packet);
                  if (visiblePackets.size() > limit) {
                     visiblePackets.remove(limit);
                  }
               } else if (visiblePackets.size() < limit) {
                  visiblePackets.add(packet);
               }
            }
         }

         return new RiptideSharedState.QueueRenderSnapshot(visiblePackets, totalCount);
      } else {
         return new RiptideSharedState.QueueRenderSnapshot(List.of(), totalCount);
      }
   }

   public boolean hasDelayedPackets() {
      return !this.delayedPackets.isEmpty();
   }

   public synchronized void setDelayedPackets(List<RiptideSharedState.QueuedPacket> packets) {
      this.delayedPackets.clear();
      if (packets != null && !packets.isEmpty()) {
         this.delayedPackets.addAll(this.rebuildQueueWithSequentialIds(packets));
      }

      this.checkAndResetIdCounter();
      this.markQueueRenderDirty();
   }

   public synchronized void clearDelayedPackets() {
      if (!this.delayedPackets.isEmpty()) {
         this.saveToHistory();
      }

      this.delayedPackets.clear();
      this.checkAndResetIdCounter();
      this.markQueueRenderDirty();
   }

   private void checkAndResetIdCounter() {
      if (this.delayedPackets.isEmpty() && this.staggeredQueue.isEmpty()) {
         this.resetCaptureTiming();
         RiptideSharedState.QueuedPacket.resetIdCounter();
      }
   }

   private void resetCaptureTiming() {
      this.lastCaptureNanos = -1L;
      this.lastCaptureClientTick = -1;
   }

   private void saveToHistory() {
      this.lastFlushedQueue.clear();
      if (!this.delayedPackets.isEmpty()) {
         this.lastFlushedQueue.addAll(this.rebuildQueueWithSequentialIds(this.delayedPackets));
      }
   }

   private void saveQueueToHistory(List<RiptideSharedState.QueuedPacket> queue) {
      this.lastFlushedQueue.clear();
      if (queue != null && !queue.isEmpty()) {
         this.lastFlushedQueue.addAll(this.rebuildQueueWithSequentialIds(queue));
      }
   }

   private void stopStaggeredSendLocked() {
      this.staggeredQueue.clear();
      this.staggeredDisplayQueue.clear();
      this.staggeredNetworkHandler = null;
      this.staggeredTickCounter = 0;
      this.flushStartNanos = 0L;
      this.flushStartClientTick = 0;
      this.staggeredTotal = 0;
      this.activeSendUsesExplicitDelays = false;
      this.activeCaptureLastNanos = -1L;
      this.activeCaptureLastClientTick = -1;
      this.activeCaptureTailDelay = 0;
      this.staggeredDelayMode = this.delayMode;
      this.markQueueRenderDirty();
   }

   private void markQueueRenderDirty() {
      this.queueRenderRevision.incrementAndGet();
   }

   private boolean isActiveStaggerSendLocked() {
      return this.staggeredNetworkHandler != null && !this.staggeredQueue.isEmpty();
   }

   private int getElapsedForDelayModeLocked(RiptideSharedState.DelayMode mode) {
      if (mode == RiptideSharedState.DelayMode.MS) {
         return this.flushStartNanos <= 0L ? 0 : (int)Math.max(0L, (System.nanoTime() - this.flushStartNanos) / 1000000L);
      } else {
         return Math.max(0, this.clientTickCounter - this.flushStartClientTick);
      }
   }

   private int getQueueTailDelayLocked(List<RiptideSharedState.QueuedPacket> queue) {
      int maxDelay = 0;
      if (queue != null && !queue.isEmpty()) {
         for (RiptideSharedState.QueuedPacket qp : queue) {
            if (qp != null && !qp.isSent()) {
               maxDelay = Math.max(maxDelay, qp.getDelay());
            }
         }

         return maxDelay;
      } else {
         return maxDelay;
      }
   }

   private void insertQueuedPacketSorted(List<RiptideSharedState.QueuedPacket> queue, RiptideSharedState.QueuedPacket packet) {
      if (queue != null && packet != null) {
         int insertAt = queue.size();

         for (int i = 0; i < queue.size(); i++) {
            RiptideSharedState.QueuedPacket existing = queue.get(i);
            if (existing != null) {
               int byDelay = Integer.compare(existing.getDelay(), packet.getDelay());
               if (byDelay > 0 || byDelay == 0 && existing.getId() > packet.getId()) {
                  insertAt = i;
                  break;
               }
            }
         }

         queue.add(insertAt, packet);
      }
   }

   private int computeActiveQueueDelayLocked() {
      int elapsed = this.getElapsedForDelayModeLocked(this.staggeredDelayMode);
      if (this.activeSendUsesExplicitDelays || this.captureMode) {
         return elapsed;
      } else if (this.staggeredPacketSend) {
         int tailDelay = this.getQueueTailDelayLocked(this.staggeredQueue);
         return Math.max(elapsed, tailDelay) + this.staggeredSendDelay;
      } else {
         return elapsed;
      }
   }

   private int computeActiveCapturedQueueDelayLocked() {
      if (this.staggeredDelayMode == RiptideSharedState.DelayMode.MS) {
         long now = System.nanoTime();
         long reference = this.activeCaptureLastNanos > 0L ? this.activeCaptureLastNanos : this.flushStartNanos;
         int delta = reference > 0L ? (int)Math.max(0L, (now - reference) / 1000000L) : 0;
         this.activeCaptureLastNanos = now;
         this.activeCaptureLastClientTick = this.clientTickCounter;
         this.activeCaptureTailDelay += delta;
         return this.activeCaptureTailDelay;
      } else {
         int tick = this.clientTickCounter;
         int reference = this.activeCaptureLastClientTick >= 0 ? this.activeCaptureLastClientTick : this.flushStartClientTick;
         int delta = Math.max(0, tick - reference);
         this.activeCaptureLastClientTick = tick;
         this.activeCaptureLastNanos = System.nanoTime();
         this.activeCaptureTailDelay += delta;
         return this.activeCaptureTailDelay;
      }
   }

   public synchronized boolean restoreLastFlushedQueue() {
      if (this.lastFlushedQueue.isEmpty()) {
         return false;
      } else {
         this.delayedPackets.clear();
         this.resetCaptureTiming();
         if (!this.lastFlushedQueue.isEmpty()) {
            this.delayedPackets.addAll(this.rebuildQueueWithSequentialIds(this.lastFlushedQueue));
         }

         this.markQueueRenderDirty();
         return true;
      }
   }

   public synchronized void enqueuePacket(Packet<?> packet) {
      this.enqueuePacket(packet, RiptideSharedState.ReplayMode.REGENERATE);
   }

   public synchronized void enqueueExactPacket(Packet<?> packet) {
      this.enqueuePacket(packet, RiptideSharedState.ReplayMode.EXACT);
   }

   public synchronized void enqueuePacket(Packet<?> packet, RiptideSharedState.ReplayMode replayMode) {
      if (!PackHideState.isHardLocked()) {
         if (packet != null) {
            RiptideSharedState.ReplayMode mode = this.normalizeReplayModeForEnqueue(packet, replayMode);
            if (this.isActiveStaggerSendLocked()) {
               int activeDelay = this.captureMode ? this.computeActiveCapturedQueueDelayLocked() : this.computeActiveQueueDelayLocked();
               RiptideSharedState.QueuedPacket queuedPacket = new RiptideSharedState.QueuedPacket(packet, activeDelay, mode);
               this.insertQueuedPacketSorted(this.staggeredQueue, queuedPacket);
               this.insertQueuedPacketSorted(this.staggeredDisplayQueue, queuedPacket);
               this.staggeredTotal++;
               this.markQueueRenderDirty();
               return;
            }

            int delay = 0;
            if (this.captureMode) {
               if (this.delayedPackets.isEmpty() && this.staggeredQueue.isEmpty()) {
                  this.resetCaptureTiming();
               }

               if (this.delayMode == RiptideSharedState.DelayMode.TICKS) {
                  int tick = this.clientTickCounter;
                  if (this.lastCaptureClientTick < 0) {
                     this.lastCaptureClientTick = tick;
                     delay = 0;
                  } else {
                     delay = tick - this.lastCaptureClientTick;
                  }
               } else {
                  long now = System.nanoTime();
                  if (this.lastCaptureNanos < 0L) {
                     this.lastCaptureNanos = now;
                     delay = 0;
                  } else {
                     delay = (int)((now - this.lastCaptureNanos) / 1000000L);
                  }
               }
            } else if (this.staggeredPacketSend) {
               delay = this.delayedPackets.size() * this.staggeredSendDelay;
            }

            this.delayedPackets.add(new RiptideSharedState.QueuedPacket(packet, delay, mode));
            this.markQueueRenderDirty();
         }
      }
   }

   private RiptideSharedState.ReplayMode normalizeReplayModeForEnqueue(Packet<?> packet, RiptideSharedState.ReplayMode replayMode) {
      RiptideSharedState.ReplayMode mode = replayMode == null ? RiptideSharedState.ReplayMode.REGENERATE : replayMode;
      return mode == RiptideSharedState.ReplayMode.REGENERATE
            && packet instanceof ServerboundContainerClosePacket closePacket
            && this.hasExactContainerClickForContainer(this.readContainerCloseId(closePacket))
         ? RiptideSharedState.ReplayMode.EXACT
         : mode;
   }

   private int readContainerCloseId(ServerboundContainerClosePacket packet) {
      if (packet == null) {
         return -1;
      } else {
         for (String methodName : new String[]{"containerId", "syncId", "getSyncId"}) {
            try {
               Method method = packet.getClass().getDeclaredMethod(methodName);
               method.setAccessible(true);
               if (method.invoke(packet) instanceof Number number) {
                  return number.intValue();
               }
            } catch (ReflectiveOperationException var10) {
            }
         }

         if (packet.getClass().isRecord()) {
            for (RecordComponent component : packet.getClass().getRecordComponents()) {
               try {
                  if (component.getAccessor().invoke(packet) instanceof Number number) {
                     return number.intValue();
                  }
               } catch (ReflectiveOperationException var9) {
               }
            }
         }

         for (Field field : packet.getClass().getDeclaredFields()) {
            if (field.getType() == int.class) {
               try {
                  field.setAccessible(true);
                  return field.getInt(packet);
               } catch (IllegalAccessException var11) {
               }
            }
         }

         return -1;
      }
   }

   private boolean hasExactContainerClickForContainer(int containerId) {
      for (RiptideSharedState.QueuedPacket queuedPacket : this.delayedPackets) {
         if (this.isExactClickForContainer(queuedPacket, containerId)) {
            return true;
         }
      }

      for (RiptideSharedState.QueuedPacket queuedPacketx : this.staggeredQueue) {
         if (this.isExactClickForContainer(queuedPacketx, containerId)) {
            return true;
         }
      }

      return false;
   }

   private boolean isExactClickForContainer(RiptideSharedState.QueuedPacket queuedPacket, int containerId) {
      return queuedPacket != null
         && queuedPacket.isExactReplay()
         && queuedPacket.packet instanceof ServerboundContainerClickPacket clickPacket
         && clickPacket.containerId() == containerId;
   }

   public void storeScreen(Screen screen, AbstractContainerMenu handler) {
      this.storedScreen = screen;
      this.storedAbstractContainerMenu = handler;
   }

   public Screen getStoredScreen() {
      return this.storedScreen;
   }

   public AbstractContainerMenu getStoredAbstractContainerMenu() {
      return this.storedAbstractContainerMenu;
   }

   public synchronized int flushDelayedPackets(ClientPacketListener networkHandler) {
      if (PackHideState.isHardLocked()) {
         return 0;
      } else if (!this.staggeredQueue.isEmpty()) {
         RiptideClientMessaging.sendPrefixed("§cAlready sending - wait for current queue to finish");
         return 0;
      } else {
         this.pendingQueueCompletionMessage = null;
         int count = this.delayedPackets.size();
         if (count == 0) {
            return 0;
         } else {
            this.resetCaptureTiming();
            RiptideModule module = RiptideModule.get();
            boolean hasDelays = this.delayedPackets.stream().anyMatch(qpx -> qpx.delay > 0);
            if (module.shouldUseDirectFlush() && !hasDelays) {
               return this.flushDelayedPacketsDirect(networkHandler);
            } else {
               this.saveToHistory();
               if (networkHandler != null) {
                  boolean shouldStagger = this.staggeredPacketSend || hasDelays;
                  if (shouldStagger) {
                     this.staggeredDisplayQueue.clear();
                     this.staggeredQueue.addAll(this.delayedPackets);
                     this.staggeredDisplayQueue.addAll(this.delayedPackets);
                     this.staggeredNetworkHandler = networkHandler;
                     this.staggeredTickCounter = 0;
                     this.staggeredTotal = count;
                     this.activeSendUsesExplicitDelays = hasDelays;
                     this.staggeredDelayMode = this.delayMode;
                     this.flushStartNanos = System.nanoTime();
                     this.flushStartClientTick = this.clientTickCounter;
                     this.activeCaptureLastNanos = this.flushStartNanos;
                     this.activeCaptureLastClientTick = this.flushStartClientTick;
                     this.activeCaptureTailDelay = this.getQueueTailDelayLocked(this.staggeredQueue);
                  } else {
                     this.isFlushing = true;

                     try {
                        for (RiptideSharedState.QueuedPacket qp : this.delayedPackets) {
                           if (RiptidePacketRegistry.getC2SPackets().contains(qp.packet.getClass())) {
                              Packet<?> packetToSend = this.packetForQueuedSend(qp);
                              if (packetToSend != null) {
                                 RiptidePacketSender.send(packetToSend);
                              }
                           } else {
                              riptide.RiptideClientAddon.LOG
                                 .warn("[Riptide] Skipped sending invalid C2S packet during flush: {}", qp.packet.getClass().getName());
                           }
                        }
                     } finally {
                        this.isFlushing = false;
                     }

                     RiptideSharedState.QueuedPacket.resetIdCounter();
                  }
               }

               this.delayedPackets.clear();
               this.markQueueRenderDirty();
               return count;
            }
         }
      }
   }

   public void sendPacketBypassDelay(ClientPacketListener networkHandler, Packet<?> packet) {
      if (!PackHideState.isHardLocked()) {
         if (networkHandler != null && packet != null) {
            this.isFlushing = true;

            try {
               networkHandler.getConnection().send(packet, null);
            } finally {
               this.isFlushing = false;
            }
         }
      }
   }

   private int flushDelayedPacketsDirect(ClientPacketListener networkHandler) {
      if (PackHideState.isHardLocked()) {
         return 0;
      } else {
         int count = this.delayedPackets.size();
         if (count == 0) {
            return 0;
         } else {
            this.saveToHistory();
            if (networkHandler != null) {
               this.isFlushing = true;

               try {
                  for (RiptideSharedState.QueuedPacket qp : this.delayedPackets) {
                     if (RiptidePacketRegistry.getC2SPackets().contains(qp.packet.getClass())) {
                        Packet<?> packetToSend = this.packetForQueuedSend(qp);
                        if (packetToSend != null) {
                           networkHandler.getConnection().send(packetToSend, null);
                        }
                     } else {
                        riptide.RiptideClientAddon.LOG.warn("[Riptide] Skipped invalid C2S packet during bypass flush: {}", qp.packet.getClass().getName());
                     }
                  }
               } finally {
                  this.isFlushing = false;
               }
            }

            this.delayedPackets.clear();
            this.checkAndResetIdCounter();
            this.markQueueRenderDirty();
            return count;
         }
      }
   }

   public synchronized void regenerateQueue() {
      if (!this.delayedPackets.isEmpty()) {
         List<RiptideSharedState.QueuedPacket> newPackets = new ArrayList<>(this.delayedPackets.size());
         int skipped = 0;
         int deferredClickSlot = 0;

         for (RiptideSharedState.QueuedPacket qp : this.delayedPackets) {
            if (qp.packet instanceof ServerboundContainerClickPacket) {
               newPackets.add(new RiptideSharedState.QueuedPacket(qp.packet, qp.delay, qp.id, qp.replayMode));
               deferredClickSlot++;
            } else if (qp.isExactReplay()) {
               newPackets.add(new RiptideSharedState.QueuedPacket(qp.packet, qp.delay, qp.id, qp.replayMode));
            } else {
               Packet<?> regenerated = PacketRegenerator.regenerate(qp.packet);
               if (regenerated != null) {
                  newPackets.add(new RiptideSharedState.QueuedPacket(regenerated, qp.delay, qp.id, qp.replayMode));
               } else {
                  skipped++;
               }
            }
         }

         this.delayedPackets.clear();
         this.delayedPackets.addAll(this.rebuildQueueWithSequentialIds(newPackets));
         this.markQueueRenderDirty();
         if (skipped <= 0 && deferredClickSlot <= 0) {
            riptide.RiptideClientAddon.LOG.info("[Riptide] Regenerated {} queued packets (IDs preserved).", newPackets.size());
         } else {
            riptide.RiptideClientAddon.LOG
               .warn(
                  "[Riptide] Regenerated {} packets, skipped {}, deferred {} ClickSlot (IDs preserved).",
                  new Object[]{newPackets.size() - deferredClickSlot, skipped, deferredClickSlot}
               );
         }
      }
   }

   public synchronized void tickStaggeredSend() {
      if (PackHideState.isHardLocked()) {
         this.staggeredQueue.clear();
         this.staggeredDisplayQueue.clear();
         this.staggeredNetworkHandler = null;
         this.markQueueRenderDirty();
      } else if (!this.staggeredQueue.isEmpty() && this.staggeredNetworkHandler != null) {
         List<RiptideSharedState.QueuedPacket> toRemove = new ArrayList<>();

         for (RiptideSharedState.QueuedPacket qp : this.staggeredQueue) {
            boolean shouldSend;
            if (this.staggeredDelayMode == RiptideSharedState.DelayMode.MS) {
               long elapsedMs = (System.nanoTime() - this.flushStartNanos) / 1000000L;
               shouldSend = elapsedMs >= qp.getDelay() && !qp.isSent();
            } else {
               int elapsedTicks = this.clientTickCounter - this.flushStartClientTick;
               shouldSend = elapsedTicks >= qp.getDelay() && !qp.isSent();
            }

            if (shouldSend) {
               this.isFlushing = true;

               try {
                  if (RiptidePacketRegistry.getC2SPackets().contains(qp.packet.getClass())) {
                     Packet<?> packetToSend = this.packetForQueuedSend(qp);
                     if (packetToSend == null) {
                        qp.markAsSent();
                        toRemove.add(qp);
                     } else {
                        this.staggeredNetworkHandler.getConnection().send(packetToSend, null);
                        qp.markAsSent();
                        toRemove.add(qp);
                     }
                  } else {
                     riptide.RiptideClientAddon.LOG.warn("[Riptide] Skipped sending invalid C2S packet: {}", qp.packet.getClass().getName());
                     qp.markAsSent();
                     toRemove.add(qp);
                  }
               } catch (Exception var10) {
                  qp.markAsSent();
                  toRemove.add(qp);
                  riptide.RiptideClientAddon.LOG.error("[Riptide] Failed to send queued packet: {}", qp.packet.getClass().getName(), var10);
               } finally {
                  this.isFlushing = false;
               }
            }
         }

         this.staggeredQueue.removeAll(toRemove);
         if (!toRemove.isEmpty()) {
            this.markQueueRenderDirty();
         }

         this.staggeredTickCounter++;
         if (this.staggeredQueue.isEmpty()) {
            int total = this.staggeredTotal;
            String completionMessage = this.consumePendingQueueCompletionMessageLocked();
            this.stopStaggeredSendLocked();
            if (this.delayedPackets.isEmpty()) {
               this.resetCaptureTiming();
               RiptideSharedState.QueuedPacket.resetIdCounter();
            }

            if (completionMessage == null || completionMessage.isBlank()) {
               completionMessage = "Finished sending " + total + " packets.";
            }

            RiptideNotifications.show(completionMessage, -13248397);
         }
      }
   }

   public boolean hasStaggeredSendWork() {
      return !this.staggeredQueue.isEmpty() && this.staggeredNetworkHandler != null;
   }

   public boolean hasPacketFlowWork() {
      return this.delayGuiPackets
         || !this.sendGuiPackets
         || this.useCustomPackets
         || this.isFlushing
         || this.suppressNextContainerClosePacket
         || this.suppressNextSignUpdatePacket
         || this.suppressNextBookEditPacket
         || this.forceNextSignUpdatePacket
         || this.forceNextBookEditPacket
         || !this.allowSignEditing
         || !this.allowBookUpdate
         || this.gbreakCaptureMode
         || this.captureMode
         || this.blockCaptureCallback != null
         || this.directionalBlockCaptureCallback != null
         || this.attackCaptureCallback != null
         || this.entityCaptureCallback != null
         || this.captureCancelCallback != null;
   }

   public boolean isStaggering() {
      return !this.staggeredQueue.isEmpty() && this.staggeredNetworkHandler != null;
   }

   public synchronized void setPendingQueueCompletionMessage(String message) {
      this.pendingQueueCompletionMessage = message != null && !message.isBlank() ? message : null;
   }

   private String consumePendingQueueCompletionMessageLocked() {
      String message = this.pendingQueueCompletionMessage;
      this.pendingQueueCompletionMessage = null;
      return message;
   }

   public synchronized List<RiptideSharedState.QueuedPacket> getStaggeredQueue() {
      return new ArrayList<>(this.staggeredQueue);
   }

   public boolean hasStaggeredPackets() {
      return !this.staggeredQueue.isEmpty();
   }

   public synchronized List<RiptideSharedState.QueuedPacket> getStaggeredDisplayQueue() {
      return new ArrayList<>(this.staggeredDisplayQueue);
   }

   public synchronized int clearQueuedPackets() {
      if (!this.staggeredQueue.isEmpty()) {
         int count = this.staggeredQueue.size() + this.delayedPackets.size();
         List<RiptideSharedState.QueuedPacket> combined = new ArrayList<>(this.staggeredQueue.size() + this.delayedPackets.size());
         combined.addAll(this.staggeredQueue);
         combined.addAll(this.delayedPackets);
         this.saveQueueToHistory(combined);
         this.pendingQueueCompletionMessage = null;
         this.stopStaggeredSendLocked();
         this.delayedPackets.clear();
         this.checkAndResetIdCounter();
         return count;
      } else {
         int count = this.delayedPackets.size();
         this.clearDelayedPackets();
         return count;
      }
   }

   public synchronized void removeQueuedPacket(RiptideSharedState.QueuedPacket packet) {
      if (packet != null) {
         this.staggeredDisplayQueue.remove(packet);
         boolean removedFromStaggered = this.staggeredQueue.remove(packet);
         if (removedFromStaggered && this.staggeredQueue.isEmpty()) {
            this.stopStaggeredSendLocked();
         }

         if (!removedFromStaggered) {
            this.delayedPackets.remove(packet);
            if (!this.delayedPackets.isEmpty()) {
               List<RiptideSharedState.QueuedPacket> rebuilt = this.rebuildQueueWithSequentialIds(this.delayedPackets);
               this.delayedPackets.clear();
               this.delayedPackets.addAll(rebuilt);
            }
         }

         this.checkAndResetIdCounter();
         this.markQueueRenderDirty();
      }
   }

   public synchronized void updatePacketDelay(RiptideSharedState.QueuedPacket packet, int newDelay) {
      if (packet != null) {
         packet.setDelay(newDelay);
         this.markQueueRenderDirty();
      }
   }

   public synchronized void sortDelayedPacketsByDelayPreservingIds() {
      if (this.delayedPackets.size() >= 2) {
         List<RiptideSharedState.QueuedPacket> ordered = new ArrayList<>(this.delayedPackets);
         ordered.sort((a, b) -> {
            int byDelay = Integer.compare(a.getDelay(), b.getDelay());
            return byDelay != 0 ? byDelay : Integer.compare(a.getId(), b.getId());
         });
         this.delayedPackets.clear();
         this.delayedPackets.addAll(ordered);
         this.markQueueRenderDirty();
      }
   }

   private List<RiptideSharedState.QueuedPacket> rebuildQueueWithSequentialIds(List<RiptideSharedState.QueuedPacket> source) {
      List<RiptideSharedState.QueuedPacket> rebuilt = new ArrayList<>();
      if (source != null && !source.isEmpty()) {
         List<RiptideSharedState.QueuedPacket> ordered = new ArrayList<>();

         for (RiptideSharedState.QueuedPacket qp : source) {
            if (qp != null && qp.packet != null) {
               ordered.add(qp);
            }
         }

         ordered.sort((a, b) -> {
            int byDelay = Integer.compare(a.getDelay(), b.getDelay());
            return byDelay != 0 ? byDelay : Integer.compare(a.getId(), b.getId());
         });
         RiptideSharedState.QueuedPacket.resetIdCounter();

         for (RiptideSharedState.QueuedPacket qpx : ordered) {
            rebuilt.add(new RiptideSharedState.QueuedPacket(qpx.packet, qpx.getDelay(), qpx.getReplayMode()));
         }

         return rebuilt;
      } else {
         return rebuilt;
      }
   }

   private Packet<?> packetForQueuedSend(RiptideSharedState.QueuedPacket queuedPacket) {
      if (queuedPacket != null && queuedPacket.packet != null) {
         return queuedPacket.isExactReplay() ? queuedPacket.packet : PacketRegenerator.regenerate(queuedPacket.packet);
      } else {
         return null;
      }
   }

   public boolean isFlushing() {
      return this.isFlushing;
   }

   public void setFlushing(boolean value) {
      this.isFlushing = value;
   }

   public void clearStoredScreen() {
      this.storedScreen = null;
      this.storedAbstractContainerMenu = null;
   }

   public synchronized void resetAll() {
      this.sendGuiPackets = true;
      this.delayGuiPackets = false;
      this.allowSignEditing = true;
      this.allowBookUpdate = true;
      this.bypassResourcePack = false;
      this.resourcePackForceDeny = false;
      this.xCarryActive = false;
      this.xCarryForced = false;
      this.lastInteractedBlockPos = null;
      this.lastContainerTarget = null;
      this.delayedPackets.clear();
      this.staggeredQueue.clear();
      this.staggeredDisplayQueue.clear();
      this.staggeredNetworkHandler = null;
      this.staggeredTickCounter = 0;
      this.staggeredTotal = 0;
      RiptideSharedState.QueuedPacket.resetIdCounter();
      this.activeSendUsesExplicitDelays = false;
      this.flushStartNanos = 0L;
      this.flushStartClientTick = 0;
      this.activeCaptureLastNanos = -1L;
      this.activeCaptureLastClientTick = -1;
      this.activeCaptureTailDelay = 0;
      this.staggeredDelayMode = RiptideSharedState.DelayMode.TICKS;
      this.lastCaptureNanos = -1L;
      this.lastCaptureClientTick = -1;
      this.markQueueRenderDirty();
      this.storedScreen = null;
      this.storedAbstractContainerMenu = null;
      this.serverPluginScans.clear();
      this.serverInfoOverlayActiveTab = 0;
      this.serverInfoOverlayPluginScrollOffset = 0;
      this.serverInfoOverlaySelectedPlugin = "";
      this.serverInfoOverlayStateAddress = "";
      this.realServerVersion = "";
      this.realServerVersionAddress = "";
      this.serverInfoOverlayProbeDelayMs = 50;
   }

   public boolean shouldEditSigns() {
      return this.allowSignEditing;
   }

   public void setAllowSignEditing(boolean value) {
      this.allowSignEditing = value;
   }

   public boolean shouldUpdateBook() {
      return this.allowBookUpdate;
   }

   public void setAllowBookUpdate(boolean value) {
      this.allowBookUpdate = value;
   }

   public boolean shouldBypassResourcePack() {
      return this.bypassResourcePack;
   }

   public void setBypassResourcePack(boolean value) {
      this.bypassResourcePack = value;
   }

   public boolean shouldForceDenyResourcePack() {
      return this.resourcePackForceDeny;
   }

   public void setResourcePackForceDeny(boolean value) {
      this.resourcePackForceDeny = value;
   }

   public boolean shouldUseCustomPackets() {
      return this.useCustomPackets;
   }

   public void setUseCustomPackets(boolean value) {
      this.useCustomPackets = value;
   }

   public Set<Class<? extends Packet<?>>> getC2SPackets() {
      return this.c2sPackets;
   }

   public void setC2SPackets(Set<Class<? extends Packet<?>>> packets) {
      this.c2sPackets.clear();
      this.c2sPackets.addAll(packets);
   }

   public Set<Class<? extends Packet<?>>> getS2CPackets() {
      return this.s2cPackets;
   }

   public void setS2CPackets(Set<Class<? extends Packet<?>>> packets) {
      this.s2cPackets.clear();
      this.s2cPackets.addAll(packets);
   }

   public boolean isStaggeredPacketSend() {
      return this.staggeredPacketSend;
   }

   public void setStaggeredPacketSend(boolean value) {
      if (!value || !PackHideState.isHardLocked()) {
         this.staggeredPacketSend = value;
      }
   }

   public int getStaggeredSendDelay() {
      return this.staggeredSendDelay;
   }

   public void setStaggeredSendDelay(int delay) {
      this.staggeredSendDelay = Math.max(1, Math.min(40, delay));
   }

   public RiptideSharedState.DelayMode getDelayMode() {
      return this.delayMode;
   }

   public synchronized RiptideSharedState.DelayMode getQueueDisplayDelayMode() {
      return this.isActiveStaggerSendLocked() ? this.staggeredDelayMode : this.delayMode;
   }

   public void setDelayMode(RiptideSharedState.DelayMode mode) {
      this.delayMode = mode;
   }

   public void toggleDelayMode() {
      this.delayMode = this.delayMode == RiptideSharedState.DelayMode.TICKS ? RiptideSharedState.DelayMode.MS : RiptideSharedState.DelayMode.TICKS;
   }

   public boolean isFabricatorOverlayVisible() {
      return this.fabricatorOverlayVisible;
   }

   public void setFabricatorOverlayVisible(boolean visible) {
      this.fabricatorOverlayVisible = visible;
   }

   public int getFabricatorOverlayX() {
      return this.fabricatorOverlayX;
   }

   public void setFabricatorOverlayX(int x) {
      this.fabricatorOverlayX = x;
   }

   public int getFabricatorOverlayY() {
      return this.fabricatorOverlayY;
   }

   public void setFabricatorOverlayY(int y) {
      this.fabricatorOverlayY = y;
   }

   public String getFabricatorSlotValue() {
      return this.fabricatorSlotValue;
   }

   public void setFabricatorSlotValue(String value) {
      this.fabricatorSlotValue = value != null ? value : "0";
   }

   public String getFabricatorItemNameValue() {
      return this.fabricatorItemNameValue;
   }

   public void setFabricatorItemNameValue(String value) {
      this.fabricatorItemNameValue = value != null ? value : "";
   }

   public String getFabricatorTimesValue() {
      return this.fabricatorTimesValue;
   }

   public void setFabricatorTimesValue(String value) {
      this.fabricatorTimesValue = value != null ? value : "1";
   }

   public int getFabricatorActionIndex() {
      return this.fabricatorActionIndex;
   }

   public void setFabricatorActionIndex(int index) {
      this.fabricatorActionIndex = Math.max(0, index);
   }

   public boolean isFabricatorCraftUseMaxAmount() {
      return this.fabricatorCraftUseMaxAmount;
   }

   public void setFabricatorCraftUseMaxAmount(boolean useMaxAmount) {
      this.fabricatorCraftUseMaxAmount = useMaxAmount;
   }

   public int getFabricatorButtonIndex() {
      return this.fabricatorButtonIndex;
   }

   public void setFabricatorButtonIndex(int index) {
      this.fabricatorButtonIndex = Math.max(0, index);
   }

   public boolean isFabricatorDropWholeStack() {
      return this.fabricatorDropWholeStack;
   }

   public void setFabricatorDropWholeStack(boolean value) {
      this.fabricatorDropWholeStack = value;
   }

   public String getFabricatorCraftSearchValue() {
      return this.fabricatorCraftSearchValue;
   }

   public void setFabricatorCraftSearchValue(String value) {
      this.fabricatorCraftSearchValue = value != null ? value : "";
   }

   public int getFabricatorCraftSelectedRecipeId() {
      return this.fabricatorCraftSelectedRecipeId;
   }

   public void setFabricatorCraftSelectedRecipeId(int recipeId) {
      this.fabricatorCraftSelectedRecipeId = recipeId;
   }

   public String getFabricatorCraftSelectedRecipeKey() {
      return this.fabricatorCraftSelectedRecipeKey;
   }

   public void setFabricatorCraftSelectedRecipeKey(String recipeKey) {
      this.fabricatorCraftSelectedRecipeKey = recipeKey != null ? recipeKey : "";
   }

   public int getFabricatorCraftScrollOffset() {
      return this.fabricatorCraftScrollOffset;
   }

   public void setFabricatorCraftScrollOffset(int offset) {
      this.fabricatorCraftScrollOffset = Math.max(0, offset);
   }

   public synchronized List<CraftAction.CraftEntry> getFabricatorCraftPlanEntries() {
      List<CraftAction.CraftEntry> copies = new ArrayList<>(this.fabricatorCraftPlanEntries.size());

      for (CraftAction.CraftEntry entry : this.fabricatorCraftPlanEntries) {
         if (entry != null) {
            copies.add(entry.copy());
         }
      }

      return copies;
   }

   public synchronized void setFabricatorCraftPlanEntries(List<CraftAction.CraftEntry> entries) {
      this.fabricatorCraftPlanEntries.clear();
      if (entries != null) {
         for (CraftAction.CraftEntry entry : entries) {
            if (entry != null) {
               this.fabricatorCraftPlanEntries.add(entry.copy());
            }
         }
      }
   }

   public int getFabricatorCraftPlanSelectedIndex() {
      return this.fabricatorCraftPlanSelectedIndex;
   }

   public void setFabricatorCraftPlanSelectedIndex(int index) {
      this.fabricatorCraftPlanSelectedIndex = index;
   }

   public int getFabricatorCraftPlanScrollOffset() {
      return this.fabricatorCraftPlanScrollOffset;
   }

   public void setFabricatorCraftPlanScrollOffset(int offset) {
      this.fabricatorCraftPlanScrollOffset = Math.max(0, offset);
   }

   public boolean isLanSyncOverlayVisible() {
      return this.lanSyncOverlayVisible;
   }

   public void setLanSyncOverlayVisible(boolean visible) {
      this.lanSyncOverlayVisible = visible;
   }

   public int getLanSyncOverlayX() {
      return this.lanSyncOverlayX;
   }

   public void setLanSyncOverlayX(int x) {
      this.lanSyncOverlayX = x;
   }

   public int getLanSyncOverlayY() {
      return this.lanSyncOverlayY;
   }

   public void setLanSyncOverlayY(int y) {
      this.lanSyncOverlayY = y;
   }

   public int getLanSyncOverlayActiveTab() {
      return this.lanSyncOverlayActiveTab;
   }

   public void setLanSyncOverlayActiveTab(int activeTab) {
      this.lanSyncOverlayActiveTab = Math.max(0, activeTab);
   }

   public int getLanSyncOverlayScrollOffset() {
      return this.lanSyncOverlayScrollOffset;
   }

   public void setLanSyncOverlayScrollOffset(int scrollOffset) {
      this.lanSyncOverlayScrollOffset = Math.max(0, scrollOffset);
   }

   public boolean isLanSyncOverlayPerUserMode() {
      return this.lanSyncOverlayPerUserMode;
   }

   public void setLanSyncOverlayPerUserMode(boolean perUserMode) {
      this.lanSyncOverlayPerUserMode = perUserMode;
   }

   public String getLanSyncOverlaySelectedMacroName() {
      return this.lanSyncOverlaySelectedMacroName;
   }

   public void setLanSyncOverlaySelectedMacroName(String selectedMacroName) {
      this.lanSyncOverlaySelectedMacroName = selectedMacroName == null ? "" : selectedMacroName;
   }

   public String getLanSyncOverlayExpandedExecutePeer() {
      return this.lanSyncOverlayExpandedExecutePeer;
   }

   public void setLanSyncOverlayExpandedExecutePeer(String expandedExecutePeer) {
      this.lanSyncOverlayExpandedExecutePeer = expandedExecutePeer == null ? "" : expandedExecutePeer;
   }

   public String getLanSyncOverlaySelectedPeer() {
      return this.lanSyncOverlaySelectedPeer;
   }

   public void setLanSyncOverlaySelectedPeer(String selectedPeer) {
      this.lanSyncOverlaySelectedPeer = selectedPeer == null ? "" : selectedPeer;
   }

   public boolean isMacroListOverlayVisible() {
      return this.macroListOverlayVisible;
   }

   public void setMacroListOverlayVisible(boolean visible) {
      this.macroListOverlayVisible = visible;
   }

   public int getMacroListOverlayX() {
      return this.macroListOverlayX;
   }

   public void setMacroListOverlayX(int x) {
      this.macroListOverlayX = x;
   }

   public int getMacroListOverlayY() {
      return this.macroListOverlayY;
   }

   public void setMacroListOverlayY(int y) {
      this.macroListOverlayY = y;
   }

   public int getMacroListOverlayScrollOffset() {
      return this.macroListOverlayScrollOffset;
   }

   public void setMacroListOverlayScrollOffset(int scrollOffset) {
      this.macroListOverlayScrollOffset = Math.max(0, scrollOffset);
   }

   public String getMacroListOverlaySearch() {
      return this.macroListOverlaySearch;
   }

   public void setMacroListOverlaySearch(String search) {
      this.macroListOverlaySearch = search == null ? "" : search;
   }

   public boolean isQueueEditorOverlayVisible() {
      return this.queueEditorOverlayVisible;
   }

   public void setQueueEditorOverlayVisible(boolean visible) {
      this.queueEditorOverlayVisible = visible;
   }

   public int getQueueEditorOverlayX() {
      return this.queueEditorOverlayX;
   }

   public void setQueueEditorOverlayX(int x) {
      this.queueEditorOverlayX = x;
   }

   public int getQueueEditorOverlayY() {
      return this.queueEditorOverlayY;
   }

   public void setQueueEditorOverlayY(int y) {
      this.queueEditorOverlayY = y;
   }

   public int getServerDataOverlayActiveTab() {
      return this.serverInfoOverlayActiveTab;
   }

   public void setServerDataOverlayActiveTab(int tab) {
      this.serverInfoOverlayActiveTab = Math.max(0, tab);
   }

   public int getServerDataOverlayPluginScrollOffset() {
      return this.serverInfoOverlayPluginScrollOffset;
   }

   public void setServerDataOverlayPluginScrollOffset(int offset) {
      this.serverInfoOverlayPluginScrollOffset = Math.max(0, offset);
   }

   public String getServerDataOverlaySelectedPlugin() {
      return this.serverInfoOverlaySelectedPlugin;
   }

   public void setServerDataOverlaySelectedPlugin(String plugin) {
      this.serverInfoOverlaySelectedPlugin = plugin == null ? "" : plugin;
   }

   public String getServerDataOverlayStateAddress() {
      return this.serverInfoOverlayStateAddress;
   }

   public void setServerDataOverlayStateAddress(String address) {
      this.serverInfoOverlayStateAddress = normalizeServerAddress(address);
   }

   public String getRealServerVersion(String address) {
      String normalizedAddress = normalizeServerAddress(address);
      if (this.realServerVersion == null || this.realServerVersion.isBlank()) {
         return "";
      } else if (normalizedAddress.isEmpty()) {
         return this.realServerVersion;
      } else {
         String normalizedStored = normalizeServerAddress(this.realServerVersionAddress);
         if (normalizedStored.isEmpty()) {
            return this.realServerVersion;
         } else {
            String addrNoPort = this.stripPort(normalizedAddress);
            String storedNoPort = this.stripPort(normalizedStored);
            if (addrNoPort.equals(storedNoPort)) {
               return this.realServerVersion;
            } else {
               return normalizedAddress.equals(normalizedStored) ? this.realServerVersion : "";
            }
         }
      }
   }

   private String stripPort(String address) {
      if (address != null && !address.isBlank()) {
         int colonIdx = address.lastIndexOf(58);
         if (colonIdx >= 0) {
            String potentialPort = address.substring(colonIdx + 1);
            if (potentialPort.matches("\\d+")) {
               return address.substring(0, colonIdx);
            }
         }

         return address;
      } else {
         return "";
      }
   }

   public void setRealServerVersion(String address, String version) {
      String normalizedAddress = normalizeServerAddress(address);
      this.realServerVersionAddress = normalizedAddress;
      this.realServerVersion = version == null ? "" : version.trim();
   }

   public void clearRealServerVersion() {
      this.realServerVersion = "";
      this.realServerVersionAddress = "";
   }

   public int getServerDataOverlayProbeDelayMs() {
      return this.serverInfoOverlayProbeDelayMs;
   }

   public void setServerDataOverlayProbeDelayMs(int delayMs) {
      this.serverInfoOverlayProbeDelayMs = Math.max(10, Math.min(500, delayMs));
   }

   public int getServerDataOverlayInfoWidth() {
      return this.serverInfoOverlayInfoWidth;
   }

   public void setServerDataOverlayInfoWidth(int width) {
      this.serverInfoOverlayInfoWidth = Math.max(252, width);
   }

   public int getServerDataOverlayInfoHeight() {
      return this.serverInfoOverlayInfoHeight;
   }

   public void setServerDataOverlayInfoHeight(int height) {
      this.serverInfoOverlayInfoHeight = Math.max(258, height);
   }

   public int getServerDataOverlayPluginWidth() {
      return this.serverInfoOverlayPluginWidth;
   }

   public void setServerDataOverlayPluginWidth(int width) {
      this.serverInfoOverlayPluginWidth = Math.max(280, width);
   }

   public int getServerDataOverlayPluginHeight() {
      return this.serverInfoOverlayPluginHeight;
   }

   public void setServerDataOverlayPluginHeight(int height) {
      this.serverInfoOverlayPluginHeight = Math.max(280, height);
   }

   public void startGBreakCapture(Runnable onCaptureComplete) {
      this.gbreakCaptureMode = true;
      this.gbreakPacketCount = 0;
      this.gbreakCapturedPackets.clear();
      this.gbreakCallback = onCaptureComplete;
   }

   public boolean isGBreakCapturing() {
      return this.gbreakCaptureMode;
   }

   public void onGBreakPacket(Packet<?> packet) {
      if (this.gbreakCaptureMode) {
         this.gbreakPacketCount++;
         if (PAYLOAD_TRACE) {
            riptide.RiptideClientAddon.LOG.info("[GBreak] Packet #{}: {}", this.gbreakPacketCount, packet.getClass().getSimpleName());
         }

         if (this.gbreakPacketCount == 2) {
            this.gbreakCapturedPackets.clear();
            this.gbreakCapturedPackets.add(new RiptideSharedState.QueuedPacket(packet, 0));
            this.finishGBreakCapture();
         }
      }
   }

   public List<RiptideSharedState.QueuedPacket> getGBreakCapturedPackets() {
      return new ArrayList<>(this.gbreakCapturedPackets);
   }

   private void finishGBreakCapture() {
      this.gbreakCaptureMode = false;
      if (this.gbreakCallback != null) {
         this.gbreakCallback.run();
         this.gbreakCallback = null;
      }
   }

   public void cancelGBreakCapture() {
      this.gbreakCaptureMode = false;
      this.gbreakPacketCount = 0;
      this.gbreakCapturedPackets.clear();
      this.gbreakCallback = null;
   }

   public void setSuppressNextClosePacket(boolean v) {
      this.setSuppressNextContainerClosePacket(v);
   }

   public void setSuppressNextContainerClosePacket(boolean v) {
      this.suppressNextContainerClosePacket = v;
   }

   public void setSuppressNextSignUpdatePacket(boolean v) {
      this.suppressNextSignUpdatePacket = v;
   }

   public void setSuppressNextBookEditPacket(boolean v) {
      this.suppressNextBookEditPacket = v;
   }

   public void setForceNextSignUpdatePacket(boolean v) {
      this.forceNextSignUpdatePacket = v;
   }

   public void setForceNextBookEditPacket(boolean v) {
      this.forceNextBookEditPacket = v;
   }

   public boolean consumeSuppressNextClosePacket() {
      boolean v = this.suppressNextContainerClosePacket;
      this.suppressNextContainerClosePacket = false;
      return v;
   }

   public boolean consumeSuppressNextContainerClosePacket() {
      return this.consumeSuppressNextClosePacket();
   }

   public boolean consumeSuppressNextSignUpdatePacket() {
      boolean v = this.suppressNextSignUpdatePacket;
      this.suppressNextSignUpdatePacket = false;
      return v;
   }

   public boolean consumeSuppressNextBookEditPacket() {
      boolean v = this.suppressNextBookEditPacket;
      this.suppressNextBookEditPacket = false;
      return v;
   }

   public boolean consumeForceNextSignUpdatePacket() {
      boolean v = this.forceNextSignUpdatePacket;
      this.forceNextSignUpdatePacket = false;
      return v;
   }

   public boolean consumeForceNextBookEditPacket() {
      boolean v = this.forceNextBookEditPacket;
      this.forceNextBookEditPacket = false;
      return v;
   }

   public void setBlockCaptureCallback(Consumer<BlockPos> cb) {
      this.blockCaptureCallback = cb;
   }

   public void setDirectionalBlockCaptureCallback(BiConsumer<BlockPos, Direction> cb) {
      this.directionalBlockCaptureCallback = cb;
   }

   public boolean consumeBlockCaptureCallback(BlockPos pos) {
      return this.consumeBlockCaptureCallback(pos, Direction.UP);
   }

   public boolean consumeBlockCaptureCallback(BlockPos pos, Direction direction) {
      BiConsumer<BlockPos, Direction> dcb = this.directionalBlockCaptureCallback;
      if (dcb != null) {
         this.directionalBlockCaptureCallback = null;
         dcb.accept(pos, direction == null ? Direction.UP : direction);
         return true;
      } else {
         Consumer<BlockPos> cb = this.blockCaptureCallback;
         if (cb != null) {
            this.blockCaptureCallback = null;
            cb.accept(pos);
            return true;
         } else {
            return false;
         }
      }
   }

   public boolean hasBlockCaptureCallback() {
      return this.blockCaptureCallback != null || this.directionalBlockCaptureCallback != null;
   }

   public void setAttackCaptureCallback(Runnable cb) {
      this.attackCaptureCallback = cb;
   }

   public boolean consumeAttackCaptureCallback() {
      Runnable cb = this.attackCaptureCallback;
      if (cb != null) {
         this.attackCaptureCallback = null;
         cb.run();
         return true;
      } else {
         return false;
      }
   }

   public boolean hasAttackCaptureCallback() {
      return this.attackCaptureCallback != null;
   }

   public void setCaptureCancelCallback(Runnable cb) {
      this.captureCancelCallback = cb;
   }

   public boolean consumeCaptureCancelCallback() {
      Runnable cb = this.captureCancelCallback;
      if (cb != null) {
         this.captureCancelCallback = null;
         cb.run();
         return true;
      } else {
         return false;
      }
   }

   public boolean hasCaptureCancelCallback() {
      return this.captureCancelCallback != null;
   }

   public void setEntityCaptureCallback(Consumer<String> cb) {
      this.entityCaptureCallback = cb;
   }

   public boolean consumeEntityCaptureCallback(String payload) {
      Consumer<String> cb = this.entityCaptureCallback;
      if (cb != null) {
         this.entityCaptureCallback = null;
         cb.accept(payload);
         return true;
      } else {
         return false;
      }
   }

   public boolean consumeEntityCaptureCallback(Entity targeted) {
      if (targeted != null && this.entityCaptureCallback != null) {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && targeted == mc.player) {
            return false;
         } else {
            String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(targeted.getType()).toString();
            String payload;
            if (this.entityCaptureSpecific) {
               String name = targeted.getDisplayName().getString().replaceAll("§.", "").trim();
               payload = "~" + targeted.getStringUUID() + "~" + typeId + "~" + name;
               this.entityCaptureSpecific = false;
            } else {
               payload = typeId;
            }

            return this.consumeEntityCaptureCallback(payload);
         }
      } else {
         return false;
      }
   }

   public boolean hasEntityCaptureCallback() {
      return this.entityCaptureCallback != null;
   }

   public void setEntityCaptureSpecific(boolean v) {
      this.entityCaptureSpecific = v;
   }

   public boolean isEntityCaptureSpecific() {
      return this.entityCaptureSpecific;
   }

   public boolean isCaptureMode() {
      return this.captureMode;
   }

   public void setCaptureMode(boolean v) {
      if (!v || !PackHideState.isHardLocked()) {
         this.captureMode = v;
         if (v) {
            this.lastCaptureNanos = -1L;
            this.lastCaptureClientTick = -1;
         }
      }
   }

   public void onServerTimeSyncReceived() {
      long now = System.currentTimeMillis();
      if (this.lastTimeSyncMs > 0L) {
         long wallDeltaMs = now - this.lastTimeSyncMs;
         if (wallDeltaMs > 0L) {
            double measured = wallDeltaMs / 20.0;
            measured = Math.max(50.0, Math.min(200.0, measured));
            this.serverMsPerTick = this.serverMsPerTick * 0.3 + measured * 0.7;
         }
      }

      this.lastTimeSyncMs = now;
   }

   public double getServerMsPerTick() {
      return this.serverMsPerTick;
   }

   public double getEstimatedTps() {
      return this.lastTimeSyncMs <= 0L ? -1.0 : 1000.0 / this.serverMsPerTick;
   }

   public void onClientTickStart() {
      this.clientTickCounter++;
   }

   public int getClientTickCounter() {
      return this.clientTickCounter;
   }

   public boolean isPlaceCaptureActive() {
      return this.placeCaptureActive;
   }

   public void setPlaceCaptureActive(boolean active) {
      this.placeCaptureActive = active;
   }

   public static enum DelayMode {
      TICKS,
      MS;
   }

   public record QueueRenderSnapshot(List<RiptideSharedState.QueuedPacket> packets, int totalCount) {
   }

   public static class QueuedPacket {
      private static final AtomicInteger ID_COUNTER = new AtomicInteger(1);
      public final Packet<?> packet;
      public final int id;
      private volatile RiptideSharedState.ReplayMode replayMode;
      private volatile int delay;
      private volatile boolean sent;

      public QueuedPacket(Packet<?> packet, int delay) {
         this(packet, delay, RiptideSharedState.ReplayMode.REGENERATE);
      }

      public QueuedPacket(Packet<?> packet, int delay, RiptideSharedState.ReplayMode replayMode) {
         this.packet = packet;
         this.id = ID_COUNTER.getAndIncrement();
         this.delay = delay;
         this.sent = false;
         this.replayMode = replayMode == null ? RiptideSharedState.ReplayMode.REGENERATE : replayMode;
      }

      public QueuedPacket(Packet<?> packet, int delay, int existingId) {
         this(packet, delay, existingId, RiptideSharedState.ReplayMode.REGENERATE);
      }

      public QueuedPacket(Packet<?> packet, int delay, int existingId, RiptideSharedState.ReplayMode replayMode) {
         this.packet = packet;
         this.id = existingId;
         this.delay = delay;
         this.sent = false;
         this.replayMode = replayMode == null ? RiptideSharedState.ReplayMode.REGENERATE : replayMode;
      }

      public int getId() {
         return this.id;
      }

      public int getDelay() {
         return this.delay;
      }

      public RiptideSharedState.ReplayMode getReplayMode() {
         return this.replayMode;
      }

      public boolean isExactReplay() {
         return this.replayMode == RiptideSharedState.ReplayMode.EXACT;
      }

      public synchronized void setDelay(int newDelay) {
         if (!this.sent) {
            this.delay = newDelay;
         }
      }

      public synchronized void setReplayMode(RiptideSharedState.ReplayMode mode) {
         if (!this.sent) {
            this.replayMode = mode == null ? RiptideSharedState.ReplayMode.REGENERATE : mode;
         }
      }

      public boolean isSent() {
         return this.sent;
      }

      synchronized void markAsSent() {
         this.sent = true;
      }

      public static void resetIdCounter() {
         ID_COUNTER.set(1);
      }
   }

   public static enum ReplayMode {
      REGENERATE,
      EXACT;
   }

   public static final class ReportSample {
      public final String label;
      public final long deltaMs;
      public final String startEvent;
      public final String endEvent;
      public final long capturedAtMs;

      public ReportSample(String label, long deltaMs, String startEvent, String endEvent, long capturedAtMs) {
         this.label = label == null ? "" : label;
         this.deltaMs = deltaMs;
         this.startEvent = startEvent == null ? "" : startEvent;
         this.endEvent = endEvent == null ? "" : endEvent;
         this.capturedAtMs = capturedAtMs;
      }
   }

   public static final class ServerPluginScan {
      private final String address;
      private final String contextSignature;
      private final List<String> plugins;
      private final Map<String, List<String>> pluginCommands;
      private final Map<String, String> pluginEvidence;

      private ServerPluginScan(
         String address, String contextSignature, List<String> plugins, Map<String, List<String>> pluginCommands, Map<String, String> pluginEvidence
      ) {
         this.address = address;
         this.contextSignature = contextSignature == null ? "" : contextSignature;
         this.plugins = (List<String>)(plugins == null ? List.of() : new ArrayList<>(plugins));
         this.pluginCommands = copyPluginCommands(pluginCommands);
         this.pluginEvidence = copyPluginEvidence(pluginEvidence);
      }

      public String getAddress() {
         return this.address;
      }

      public List<String> getPlugins() {
         return new ArrayList<>(this.plugins);
      }

      public String getContextSignature() {
         return this.contextSignature;
      }

      public Map<String, List<String>> getPluginCommands() {
         return copyPluginCommands(this.pluginCommands);
      }

      public Map<String, String> getPluginEvidence() {
         return copyPluginEvidence(this.pluginEvidence);
      }

      private RiptideSharedState.ServerPluginScan copy() {
         return new RiptideSharedState.ServerPluginScan(this.address, this.contextSignature, this.plugins, this.pluginCommands, this.pluginEvidence);
      }

      private static Map<String, List<String>> copyPluginCommands(Map<String, List<String>> source) {
         if (source != null && !source.isEmpty()) {
            Map<String, List<String>> copy = new LinkedHashMap<>();

            for (Entry<String, List<String>> entry : source.entrySet()) {
               if (entry.getKey() != null) {
                  copy.put(entry.getKey(), (List<String>)(entry.getValue() == null ? List.of() : new ArrayList<>(entry.getValue())));
               }
            }

            return copy;
         } else {
            return Collections.emptyMap();
         }
      }

      private static Map<String, String> copyPluginEvidence(Map<String, String> source) {
         if (source != null && !source.isEmpty()) {
            Map<String, String> copy = new LinkedHashMap<>();

            for (Entry<String, String> entry : source.entrySet()) {
               if (entry.getKey() != null && !entry.getKey().isBlank()) {
                  copy.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
               }
            }

            return copy;
         } else {
            return Collections.emptyMap();
         }
      }
   }
}
