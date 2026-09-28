package riptide.modules;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import riptide.api.event.AddonEvents;
import riptide.gui.screen.RiptideModuleScreen;
import riptide.gui.screen.RiptideMultiDisclaimerScreen;
import riptide.gui.screen.RiptideOverlayHostScreen;
import riptide.util.IRiptideOverlay;
import riptide.util.RiptideBindUtil;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideClientWake;
import riptide.util.RiptideCompatManager;
import riptide.util.RiptideConfig;
import riptide.util.RiptideHudManager;
import riptide.util.RiptideInputGate;
import riptide.util.RiptideJoinMacroController;
import riptide.util.RiptideLANSync;
import riptide.util.RiptideLiteVariant;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroManager;
import riptide.util.RiptideMultiOverlay;
import riptide.util.RiptideNetworkCaptureState;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideOverlayManager;
import riptide.util.RiptidePacketLoggerOverlay;
import riptide.util.RiptidePacketRegistry;
import riptide.util.RiptidePayloadChannelListeners;
import riptide.util.RiptidePayloadChannelSubscriptionManager;
import riptide.util.RiptidePayloadFilterNotifier;
import riptide.util.RiptidePayloadStudySession;
import riptide.util.RiptidePayloadSupport;
import riptide.util.RiptidePerf;
import riptide.util.RiptidePluginPayloadFingerprints;
import riptide.util.RiptideProfileManager;
import riptide.util.RiptideProfilesOverlay;
import riptide.util.RiptideRuntimeActivity;
import riptide.util.RiptideServerInfoOverlay;
import riptide.util.RiptideSharedState;
import riptide.util.RiptideTheme;
import riptide.util.SodiumTerrainPassGuard;
import riptide.util.macro.MacroConditionRegistry;
import riptide.util.macro.MacroExecutor;
import riptide.util.macro.PacketGateManager;
import riptide.util.macro.PingSpoofController;
import riptide.util.macro.XCarryAction;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiTakeoverState;
import riptide.util.oresim.RiptideOreSimEngine;

public final class RiptideModule {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final RiptideModule INSTANCE = new RiptideModule();
   private static final long PASSIVE_PAYLOAD_CAPTURE_MS = 20000L;
   private static final int PASSIVE_PAYLOAD_RING_CAP = 96;
   private static final int PAYLOAD_FINGERPRINT_CAP = 256;
   private static final boolean PAYLOAD_TRACE = Boolean.getBoolean("riptide.payload.trace");
   private static final Set<Class<?>> C2S_EXCLUDED_DEFAULTS = Set.of(
      ServerboundKeepAlivePacket.class,
      ServerboundPongPacket.class,
      ServerboundClientTickEndPacket.class,
      ServerboundChunkBatchReceivedPacket.class,
      ServerboundClientCommandPacket.class,
      ServerboundContainerClosePacket.class,
      ServerboundSwingPacket.class,
      ServerboundPlayerInputPacket.class,
      ServerboundMovePlayerPacket.class,
      PosRot.class,
      Rot.class,
      StatusOnly.class,
      Pos.class
   );
   private RiptideConfig config;
   private boolean initialized;
   private boolean loadGuiKeyPressed;
   private boolean flushQueueKeyPressed;
   private boolean clearQueueKeyPressed;
   private boolean toggleLoggerKeyPressed;
   private boolean toggleSendKeyPressed;
   private boolean toggleDelayKeyPressed;
   private boolean moduleMenuKeyPressed;
   private final Map<String, Boolean> macroKeyStates = new HashMap<>();
   private List<RiptideMacro> cachedKeyboundMacros = List.of();
   private long cachedMacroKeybindRevision = -1L;
   private int autoSendTickCounter;
   private int packetLoggerTickCounter;
   private RiptidePacketLoggerOverlay packetLoggerOverlay;
   private RiptidePayloadChannelListeners passivePayloadListeners;
   private volatile boolean payloadListenerCacheValid;
   private volatile boolean payloadListenerEnabledCache;
   private RiptideServerInfoOverlay serverInfoOverlay;
   private IRiptideOverlay matchmakingOverlay;
   private IRiptideOverlay profilesOverlay;
   private IRiptideOverlay multiOverlay;
   private final Deque<RiptideModule.PassivePayloadCapture> passivePayloadRing = new ArrayDeque<>(96);
   private final Set<Packet<?>> capturedPayloadPackets = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
   private final Deque<String> capturedRawPayloadFingerprints = new ArrayDeque<>(256);
   private final Set<String> capturedRawPayloadFingerprintSet = new HashSet<>();
   private volatile boolean joinedPlayConnection;
   private volatile boolean spawnedInWorld;
   private long lastTickErrorLogMs;
   private int stuckActiveTicks;
   private long lastWatchdogLogMs;
   private static final int ACTIVE_WATCHDOG_TICKS = 20;
   private volatile long passivePayloadCaptureUntilMs;
   private volatile boolean autoProbePending;
   private volatile long autoProbePendingSince;
   private static final long AUTO_PROBE_CMD_GRACE_MS = 2000L;
   private static final long AUTO_PROBE_GIVE_UP_MS = 25000L;
   private Module xcarryCached;
   private int xcarryRevision = -1;

   private RiptideModule() {
   }

   public static RiptideModule get() {
      return INSTANCE;
   }

   public void initialize() {
      if (!this.initialized) {
         this.config = RiptideConfig.load();
         this.initStep("applyRuntimeDefaults", () -> this.config.applyRuntimeDefaults());
         RiptideConfig.setGlobal(this.config);
         if (!RiptideLiteVariant.enabled()) {
            this.initStep("themeWarm", () -> RiptideTheme.active());
         }

         this.initStep("moduleRegistry", () -> ModuleRegistry.initialize(this.config));
         this.initStep("c2sPackets", () -> {
            if (this.config.c2sPackets.isEmpty()) {
               this.config.c2sPackets = this.encodePackets(this.defaultC2SPackets());
            }
         });
         this.initStep("sharedState", this::applyConfigToSharedState);
         this.initStep("packHide", () -> {
            if (PackHideState.isActive()) {
               PackHideState.stopRuntimeWork();
            }
         });
         this.initStep("lanSync", () -> {
            if (this.config.lanSyncEnabled && !PackHideState.isActive()) {
               RiptideLANSync.getInstance().start();
            }
         });
         if (!RiptideLiteVariant.enabled()) {
            this.initStep("profiles", () -> RiptideProfileManager.get());
         }

         this.initialized = true;
         RiptideNetworkCaptureState.refresh(this);
      }
   }

   private void initStep(String var1, Runnable var2) {
      try {
         var2.run();
      } catch (Throwable var4) {
         riptide.RiptideClientAddon.LOG.warn("[Riptide] init step '{}' failed; isolated so the mod still activates", var1, var4);
      }
   }

   public void applyConfig(RiptideConfig var1) {
      if (var1 != null) {
         var1.applyRuntimeDefaults();
         RiptideConfig var2 = this.config != null ? this.config : RiptideConfig.getGlobal();
         if (var2 != null) {
            var1.welcomeShown = var2.welcomeShown;
            var1.welcomeInstallIdentity = var2.welcomeInstallIdentity;
            var1.serverPluginScans = var2.serverPluginScans;
            var1.lanSyncEnabled = var2.lanSyncEnabled;
         }

         boolean var3 = !RiptideConfig.sameThemeColors(var2, var1);
         boolean var4 = !RiptideConfig.samePayloadRules(var2, var1);
         LinkedHashMap var5 = new LinkedHashMap();

         for (Module var7 : ModuleRegistry.all()) {
            var5.put(var7.id(), var7.isEnabled());
         }

         boolean var12 = PackHideState.isActive();
         boolean var13 = MC != null && MC.level != null;
         this.config = var1;
         RiptideConfig.setGlobal(var1);
         var1.save();

         for (Module var9 : ModuleRegistry.all()) {
            boolean var10 = var9.isEnabled();
            boolean var11 = Boolean.TRUE.equals(var5.get(var9.id()));
            if (var10 != var11 && var13 && (!var10 || !PackHideState.blocksEnable(var9))) {
               ModuleRegistry.fireEnableTransition(var9, var10);
            }
         }

         if (!var13) {
            ModuleRegistry.clearOfflineDeferrals();
         }

         ModuleRegistry.refreshEnabledModuleSettings();
         ModuleRegistry.markModuleEnabledChanged();
         this.applyConfigToSharedState();
         this.invalidatePayloadListenerCache(var4);
         RiptideNetworkCaptureState.refresh(this);
         RiptideHudManager.ensureDefaults();
         if (var3) {
            RiptideTheme.reload();
         }

         boolean var14 = PackHideState.isActive();
         if (var14 && !var12) {
            PackHideState.stopRuntimeWork();
         }

         if (!var14) {
            if (var1.lanSyncEnabled && !RiptideLANSync.getInstance().isRunning()) {
               RiptideLANSync.getInstance().start();
            } else if (!var1.lanSyncEnabled && RiptideLANSync.getInstance().isRunning()) {
               RiptideLANSync.getInstance().stop();
            }
         }
      }
   }

   public void tick() {
      if (this.initialized && MC != null) {
         try {
            this.updateWorldSpawnState();
         } catch (Throwable var3) {
            this.logTickError("worldSpawnState", var3);
         }

         try {
            this.tickWork();
         } catch (Throwable var2) {
            this.logTickError("tickWork", var2);
         }
      }
   }

   private void logTickError(String var1, Throwable var2) {
      long var3 = System.currentTimeMillis();
      if (var3 - this.lastTickErrorLogMs >= 5000L) {
         this.lastTickErrorLogMs = var3;
         riptide.RiptideClientAddon.LOG.warn("[Riptide] tick '{}' failed; isolated to protect the client", var1, var2);
      }
   }

   private void logWatchdogRecovery() {
      long var1 = System.currentTimeMillis();
      if (var1 - this.lastWatchdogLogMs >= 5000L) {
         this.lastWatchdogLogMs = var1;
         riptide.RiptideClientAddon.LOG
            .warn(
               "[Riptide] active-state watchdog: a live world was present but joinedPlayConnection was false; force-restored isActive() (HUD/overlay would otherwise stay hidden until reconnect)"
            );
      }
   }

   private void tickWork() {
      RiptideNetworkCaptureState.refreshIfDue(this);
      if (!RiptideLiteVariant.enabled()) {
         RiptideProfileManager.get().flushMirrorIfDue();
      }

      PingSpoofController.flushDue();
      RiptideBlinkManager.tick();
      RiptideClientWake.tick(MC);
      boolean var1 = PackHideState.isActive();
      if (var1) {
         this.autoProbePending = false;
         this.passivePayloadCaptureUntilMs = 0L;
         RiptidePayloadStudySession.stop();
         RiptidePayloadFilterNotifier.clear();
         RiptideServerInfoOverlay var2 = this.getServerDataOverlayIfExists();
         if (var2 != null) {
            var2.stopPluginScanSilently();
         }
      }

      RiptidePerf.tickJoinWindow();
      RiptidePayloadFilterNotifier.tick();
      if (!var1) {
         RiptidePayloadChannelSubscriptionManager.tick(MC, this.isPacketLoggerCapturing());
      }

      RiptideLANSync var6 = RiptideLANSync.getInstance();
      if (!var1 && var6.hasTickWork()) {
         long var3 = RiptidePerf.beginJoin();
         var6.tick();
         RiptidePerf.endJoinSpike("join.lanSync.tick", var3);
      }

      if (!var1 && MacroConditionRegistry.hasPendingConditions()) {
         long var7 = RiptidePerf.beginJoin();
         MacroConditionRegistry.onTick(MC);
         RiptidePerf.endJoinSpike("join.macroConditions.tick", var7);
      }

      RiptideSharedState var8 = RiptideSharedState.get();
      RiptideJoinGrace.tick();
      if (!var1 && var8.hasStaggeredSendWork()) {
         var8.tickStaggeredSend();
      }

      if (!var1 && RiptideRuntimeActivity.has(1L)) {
         long var4 = RiptidePerf.beginJoin();
         ModuleRegistry.tick();
         RiptidePerf.endJoinSpike("join.modules.tick", var4, 8000000L);
      }

      if (!var1) {
         PackAutoReconnectState.tickCurrentScreen();
      }

      this.updatePassiveXCarryState();
      RiptideServerInfoOverlay var9 = this.getServerDataOverlayIfExists();
      if (!var1 && var9 != null && (var9.isVisible() || var9.shouldRenderBackgroundProbeBanner())) {
         var9.tickBackground();
      }

      if (var8.shouldDelayGuiPackets() && MC.getConnection() != null) {
         if (this.autoSendTickCounter++ >= 1) {
            this.autoSendTickCounter = 0;
         }
      } else {
         this.autoSendTickCounter = 0;
      }

      if (RiptideInputGate.canRunRiptideKeybinds()) {
         this.tickKeybinds();
      } else {
         this.loadGuiKeyPressed = false;
         this.flushQueueKeyPressed = false;
         this.clearQueueKeyPressed = false;
         this.toggleLoggerKeyPressed = false;
         this.toggleSendKeyPressed = false;
         this.toggleDelayKeyPressed = false;
         this.macroKeyStates.clear();
      }

      this.packetLoggerTickCounter++;
      RiptidePacketLoggerOverlay var5 = this.getPacketLoggerOverlayIfExists();
      if (var5 != null) {
         var5.setGameTick(this.packetLoggerTickCounter);
      }

      if (!var1 && AddonEvents.hasTickListeners()) {
         AddonEvents.fireTick(MC);
      }

      this.publishAuxiliaryHudActivity(var1, var8, var9);
   }

   private void publishAuxiliaryHudActivity(boolean var1, RiptideSharedState var2, RiptideServerInfoOverlay var3) {
      boolean var4 = !var1
         && (
            MacroExecutor.isVisibleRunning()
               || var2.shouldDelayGuiPackets()
               || var2.hasDelayedPackets()
               || var2.hasStaggeredPackets()
               || var2.isCaptureMode()
               || var2.isGBreakCapturing()
               || var2.hasCaptureCancelCallback()
               || var2.hasAttackCaptureCallback()
               || var2.hasBlockCaptureCallback()
               || var2.hasEntityCaptureCallback()
               || RiptidePayloadStudySession.isActive()
               || ModuleRenderUtil.has2dEspWork()
               || AntiVanishModule.shouldShowHud()
               || RiptideNotifications.hasVisible()
               || var3 != null && var3.shouldRenderBackgroundProbeBanner()
         );
      RiptideRuntimeActivity.publish(2048L, var4);
   }

   public void onGameJoin() {
      SodiumTerrainPassGuard.armForTransition();
      RiptidePerf.beginJoinWindow();
      long var1 = RiptidePerf.beginJoin();
      this.joinedPlayConnection = true;
      this.spawnedInWorld = false;
      if (PackHideState.isActive()) {
         this.autoProbePending = false;
         this.passivePayloadCaptureUntilMs = 0L;
      } else {
         this.autoProbePending = this.config != null && this.config.autoProbePlugins;
         if (this.autoProbePending) {
            this.autoProbePendingSince = System.currentTimeMillis();
         }

         this.beginPassivePayloadCapture();
         RiptidePayloadChannelSubscriptionManager.requestRefresh();
      }

      this.applyRuntimePacketFlowDefaults();
      if (PackHideState.isActive()) {
         PackHideState.stopRuntimeWork();
      }

      if (!PackHideState.isActive() && this.config != null && this.config.packetLoggerCapturing) {
         this.getPacketLoggerOverlay();
      }

      if (this.config.lanSyncEnabled && !PackHideState.isActive() && !RiptideLANSync.getInstance().isRunning()) {
         RiptideLANSync.getInstance().start();
      }

      if (!PackHideState.isActive()) {
         RiptideLANSync.getInstance().onGameJoined();
      }

      if (!PackHideState.isActive()) {
         ModuleRegistry.onGameJoin();
      }

      if (!PackHideState.isActive()) {
         AddonEvents.fireGameJoin();
      }

      RiptidePerf.endJoinSpike("join.onGameJoin", var1, 8000000L);
   }

   public void onGameLeft() {
      SodiumTerrainPassGuard.armForTransition();
      ModuleWorldRenderer.dropEspMeshes();
      RiptideOreSimEngine.clear();
      RiptideOreSimEngine.forgetDisproven();
      this.joinedPlayConnection = false;
      this.spawnedInWorld = false;
      this.autoProbePending = false;
      RiptidePluginPayloadFingerprints.clearSession();
      RiptidePayloadFilterNotifier.clear();
      RiptidePayloadChannelSubscriptionManager.clear();
      RiptideServerInfoOverlay var1 = this.getServerDataOverlayIfExists();
      if (var1 != null) {
         var1.onConnectionClosed();
      }

      this.passivePayloadCaptureUntilMs = 0L;
      RiptideNetworkCaptureState.refresh(this);
      RiptideSharedState.get().clearRealServerVersion();
      this.loadGuiKeyPressed = false;
      this.flushQueueKeyPressed = false;
      this.clearQueueKeyPressed = false;
      this.toggleLoggerKeyPressed = false;
      this.toggleSendKeyPressed = false;
      this.toggleDelayKeyPressed = false;
      this.moduleMenuKeyPressed = false;
      if (!PackHideState.isActive()) {
         ModuleRegistry.onGameLeft();
      }

      if (!PackHideState.isActive()) {
         AddonEvents.fireGameLeft();
      }
   }

   public boolean isActive() {
      return this.initialized && this.spawnedInWorld && this.isPlayerSpawnedInWorld();
   }

   public boolean isUsable() {
      return this.initialized && !PackHideState.isActive();
   }

   public boolean arePacketHooksActive() {
      if (this.isActive() && !PackHideState.isActive()) {
         RiptideSharedState var1 = RiptideSharedState.get();
         return var1.hasPacketFlowWork()
            || this.isPacketLoggerCapturing()
            || this.isServerInfoPacketObservationActive()
            || ModuleRegistry.hasActivePacketEventModules()
            || AddonEvents.hasPacketListeners()
            || PacketGateManager.hasActiveGates()
            || MacroExecutor.hasPacketObservationWork();
      } else {
         return false;
      }
   }

   public RiptideModule.PacketHookSnapshot packetHookSnapshot(boolean var1) {
      if (PackHideState.isActive()) {
         return RiptideModule.PacketHookSnapshot.inactive();
      } else {
         long var2 = RiptideNetworkCaptureState.state();
         boolean var4 = RiptideNetworkCaptureState.capturesPlaintext(var2);
         boolean var5 = RiptideNetworkCaptureState.capturesPayloads(var2);
         if (!this.isActive()) {
            return !var4 && !var5 ? RiptideModule.PacketHookSnapshot.inactive() : new RiptideModule.PacketHookSnapshot(false, var5, var4, false);
         } else {
            RiptideSharedState var6 = RiptideSharedState.get();
            boolean var7 = var5 && this.isServerInfoPacketObservationActive();
            boolean var8 = var1
               && (
                  var6.hasPacketFlowWork()
                     || var4
                     || var7
                     || ModuleRegistry.hasActivePacketEventModules()
                     || AddonEvents.hasPacketListeners()
                     || PacketGateManager.hasActiveGates()
                     || MacroExecutor.hasPacketObservationWork()
               );
            return !var8 && !var5 && !var4 && !var7
               ? RiptideModule.PacketHookSnapshot.inactive()
               : new RiptideModule.PacketHookSnapshot(var8, var5, var4, var7);
         }
      }
   }

   public boolean hasPassivePayloadCaptureWork() {
      return !PackHideState.isActive()
         && (this.isPassivePayloadCaptureActive() || this.payloadListenersEnabledCached() || RiptidePayloadStudySession.isActive());
   }

   public boolean shouldCapturePacketPlaintext() {
      return PackHideState.isActive() ? false : this.isPacketLoggerCapturing();
   }

   public boolean shouldCapturePayloadBytes() {
      return PackHideState.isActive()
         ? false
         : this.isPacketLoggerCapturing()
            || this.hasPassivePayloadCaptureWork()
            || this.isServerInfoPacketObservationActive()
            || RiptidePayloadStudySession.isActive();
   }

   private boolean isServerInfoPacketObservationActive() {
      if (this.config != null && this.config.autoProbePlugins && this.autoProbePending) {
         return true;
      } else {
         RiptideServerInfoOverlay var1 = this.getServerDataOverlayIfExists();
         return var1 != null && var1.isPacketObservationActive();
      }
   }

   public boolean hasPluginDiscoveryObservationWork() {
      return this.isActive() && !PackHideState.isActive() ? this.isServerInfoPacketObservationActive() : false;
   }

   private RiptideServerInfoOverlay getPluginDiscoveryOverlay() {
      RiptideServerInfoOverlay var1 = this.getServerDataOverlayIfExists();
      if (var1 != null) {
         return var1;
      } else {
         return this.config != null && this.config.autoProbePlugins && this.autoProbePending ? this.getServerDataOverlay() : null;
      }
   }

   public void observePluginDiscoveryPacketSend(Packet<?> var1) {
      if (this.hasPluginDiscoveryObservationWork() && var1 != null) {
         RiptideServerInfoOverlay var2 = this.getPluginDiscoveryOverlay();
         if (var2 != null) {
            var2.onCommandSuggestionRequest(var1);
            var2.onOutgoingCommandPacket(var1);
         }
      }
   }

   public void observePluginDiscoveryPacketReceive(Packet<?> var1) {
      if (this.hasPluginDiscoveryObservationWork() && var1 != null) {
         RiptideServerInfoOverlay var2 = this.getPluginDiscoveryOverlay();
         if (var2 != null) {
            if (var1 instanceof ClientboundCommandSuggestionsPacket var3) {
               var2.onCommandSuggestions(var3.id(), var3);
            }

            if (var1 instanceof ClientboundOpenScreenPacket) {
               var2.onOpenScreenPacket(var1);
            }

            if (var1 instanceof ClientboundCommandsPacket
               && this.spawnedInWorld
               && !this.autoProbePending
               && this.config != null
               && this.config.autoProbePlugins) {
               this.autoProbePending = true;
               this.autoProbePendingSince = System.currentTimeMillis();
            }
         }
      }
   }

   private boolean shouldObservePluginPayloadFingerprints() {
      return this.isPassivePayloadCaptureActive()
         || this.isServerInfoPacketObservationActive()
         || this.isPacketLoggerCapturing()
         || RiptidePayloadStudySession.isActive();
   }

   public void invalidatePayloadListenerCache() {
      this.invalidatePayloadListenerCache(false);
   }

   public void invalidatePayloadListenerCache(boolean var1) {
      this.payloadListenerCacheValid = false;
      if (this.passivePayloadListeners != null) {
         this.passivePayloadListeners.load();
      }

      if (var1) {
         RiptidePayloadChannelSubscriptionManager.requestRefresh();
      }

      RiptideNetworkCaptureState.refresh(this);
   }

   private boolean payloadListenersEnabledCached() {
      if (this.payloadListenerCacheValid) {
         return this.payloadListenerEnabledCache;
      } else {
         boolean var1 = this.computePayloadListenersEnabled();
         this.payloadListenerEnabledCache = var1;
         this.payloadListenerCacheValid = true;
         return var1;
      }
   }

   private boolean computePayloadListenersEnabled() {
      RiptideConfig var1 = RiptideConfig.getGlobal();
      if (var1 != null && var1.packetLoggerPayloadFilters != null) {
         for (RiptideConfig.PayloadChannelFilterRule var3 : var1.packetLoggerPayloadFilters) {
            if (var3 != null && var3.enabled) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public void onConfigurationConnectionStarted() {
      this.beginPassivePayloadCapture();
      RiptidePayloadChannelSubscriptionManager.requestRefresh();
   }

   public boolean isPassivePayloadCaptureActive() {
      return System.currentTimeMillis() <= this.passivePayloadCaptureUntilMs;
   }

   public long passivePayloadCaptureDeadlineMs() {
      return this.passivePayloadCaptureUntilMs;
   }

   public boolean isPacketLoggerCapturing() {
      if (PackHideState.isActive()) {
         return false;
      } else {
         RiptidePacketLoggerOverlay var1 = this.getPacketLoggerOverlayIfExists();
         return var1 != null ? !var1.isPaused() : this.config != null && this.config.packetLoggerCapturing;
      }
   }

   public boolean capturePayloadPacketForLogger(Packet<?> var1, String var2, String var3) {
      return this.capturePayloadPacket(var1, var2, var3, "connection");
   }

   public boolean captureDecodedPayloadPacket(Packet<?> var1, String var2, String var3, String var4) {
      return this.capturePayloadPacket(var1, var2, var3, var4 != null && !var4.isBlank() ? var4 : "codec");
   }

   public boolean captureRawPayloadFrame(byte[] var1, String var2, String var3, String var4) {
      if (!PackHideState.isActive() && var1 != null && var1.length != 0) {
         RiptidePayloadSupport.PayloadSnapshot var5 = RiptidePayloadSupport.snapshotFromEncodedPacketFrame(var1, var2, var3);
         if (var5 == null) {
            return false;
         } else {
            String var6 = this.rawPayloadFingerprint(var5);
            if (this.isRawPayloadCaptured(var6)) {
               return true;
            } else {
               if (this.shouldObservePluginPayloadFingerprints()) {
                  this.observePluginPayloadFingerprint(var5);
               }

               boolean var7 = this.isPacketLoggerCapturing();
               boolean var8 = this.hasPassivePayloadCaptureWork();
               if (!var7 && !var8) {
                  return false;
               } else {
                  RiptidePacketLoggerOverlay var9 = this.getPacketLoggerOverlayIfExists();
                  boolean var10 = false;
                  if (var7) {
                     if (var9 == null) {
                        this.rememberPassivePayload(var5, var2, rawPayloadPacketClass(var2));
                     } else {
                        var9.logPayloadSnapshotSilently(
                           System.currentTimeMillis(), this.packetLoggerTickCounter, var2 == null ? "" : var2, rawPayloadPacketClass(var2), var5
                        );
                     }

                     var10 = true;
                  } else if (var8) {
                     RiptidePayloadChannelListeners.Match var11 = this.matchEnabledPayloadFilter(var5.channel(), var2);
                     if (var11 != null) {
                        if (var9 != null) {
                           var9.logPayloadSnapshotSilently(
                              System.currentTimeMillis(), this.packetLoggerTickCounter, var2 == null ? "" : var2, rawPayloadPacketClass(var2), var5
                           );
                        } else {
                           RiptidePayloadFilterNotifier.onMatch(var5.channel(), var2, var11);
                           this.rememberPassivePayload(var5, var2, rawPayloadPacketClass(var2));
                        }

                        var10 = true;
                     }
                  }

                  if (var10) {
                     this.markRawPayloadCaptured(var6);
                     this.traceRawPayloadCapture(var5, var4);
                  }

                  return var10;
               }
            }
         }
      } else {
         return false;
      }
   }

   private boolean capturePayloadPacket(Packet<?> var1, String var2, String var3, String var4) {
      if (!PackHideState.isActive() && var1 != null) {
         if (var1 instanceof BundlePacket var5) {
            boolean var13 = false;

            for (Packet var15 : bundledPackets(var5)) {
               if (this.capturePayloadPacket(var15, var2, var3, var4)) {
                  var13 = true;
               }
            }

            return var13;
         } else {
            CustomPacketPayload var6 = RiptidePayloadSupport.extractPayload(var1);
            if (var6 == null) {
               return false;
            } else if (this.isPayloadPacketCaptured(var1)) {
               return true;
            } else {
               String var7 = RiptidePayloadSupport.payloadChannel(var6);
               RiptidePayloadSupport.rememberPayloadProtocol(var1, var3);
               if (this.shouldObservePluginPayloadFingerprints()) {
                  this.observePluginPayloadFingerprint(var1, var2);
               }

               boolean var8 = this.isPacketLoggerCapturing();
               boolean var9 = this.hasPassivePayloadCaptureWork();
               if (!var8 && !var9) {
                  return false;
               } else {
                  RiptidePacketLoggerOverlay var10 = this.getPacketLoggerOverlayIfExists();
                  boolean var11 = false;
                  if (var8) {
                     if (var10 == null) {
                        this.rememberPassivePayload(var1, var2);
                     } else {
                        var10.logPayloadPacketSilently(var1, var2);
                     }

                     var11 = true;
                  } else if (var9) {
                     RiptidePayloadChannelListeners.Match var12 = this.matchEnabledPayloadFilter(var7, var2);
                     if (var12 != null) {
                        if (var10 != null) {
                           var10.logPayloadPacketSilently(var1, var2);
                        } else {
                           RiptidePayloadFilterNotifier.onMatch(var7, var2, var12);
                           this.rememberPassivePayload(var1, var2);
                        }

                        var11 = true;
                     }
                  }

                  if (var11) {
                     this.markPayloadPacketCaptured(var1);
                     this.tracePayloadCapture(var1, var7, var2, var3, var4);
                  }

                  return var11;
               }
            }
         }
      } else {
         return false;
      }
   }

   private boolean isPayloadPacketCaptured(Packet<?> var1) {
      synchronized (this.capturedPayloadPackets) {
         return this.capturedPayloadPackets.contains(var1);
      }
   }

   private void markPayloadPacketCaptured(Packet<?> var1) {
      if (var1 != null) {
         synchronized (this.capturedPayloadPackets) {
            this.capturedPayloadPackets.add(var1);
         }
      }
   }

   private void tracePayloadCapture(Packet<?> var1, String var2, String var3, String var4, String var5) {
      if (PAYLOAD_TRACE) {
         int var6 = -1;

         try {
            CustomPacketPayload var7 = RiptidePayloadSupport.extractPayload(var1);
            var6 = RiptidePayloadSupport.extractPayloadBytes(var7).length;
         } catch (Throwable var8) {
         }

         riptide.RiptideClientAddon.LOG
            .info(
               "[Riptide Payload] {} {} {} {}B via {}",
               new Object[]{
                  var3 == null ? "" : var3, var4 == null ? "" : var4, var2 != null && !var2.isBlank() ? var2 : "<unknown>", var6, var5 == null ? "" : var5
               }
            );
      }
   }

   private void traceRawPayloadCapture(RiptidePayloadSupport.PayloadSnapshot var1, String var2) {
      if (PAYLOAD_TRACE && var1 != null) {
         riptide.RiptideClientAddon.LOG
            .info(
               "[Riptide Payload] {} {} {} {}B via {}",
               new Object[]{
                  var1.direction() == null ? "" : var1.direction(),
                  var1.protocolPhase() == null ? "" : var1.protocolPhase(),
                  var1.channel() != null && !var1.channel().isBlank() ? var1.channel() : "<unknown>",
                  var1.sizeBytes(),
                  var2 == null ? "" : var2
               }
            );
      }
   }

   public void capturePassivePayloadPacket(Packet<?> var1, String var2) {
      this.capturePassivePayloadPacket(var1, var2, "");
   }

   public void capturePassivePayloadPacket(Packet<?> var1, String var2, String var3) {
      if (!PackHideState.isActive() && this.hasPassivePayloadCaptureWork()) {
         this.capturePayloadPacket(var1, var2, var3, "passive");
      }
   }

   private void rememberPassivePayload(Packet<?> var1, String var2) {
      long var3 = RiptidePerf.beginJoin();
      RiptidePayloadSupport.PayloadSnapshot var5 = RiptidePayloadSupport.snapshot(var1, var2);
      if (var5 != null) {
         this.rememberPassivePayload(var5, var2, var1.getClass());
         RiptidePerf.endJoinSpike("join.passivePayload.snapshot", var3);
      }
   }

   private void rememberPassivePayload(RiptidePayloadSupport.PayloadSnapshot var1, String var2, Class<?> var3) {
      if (var1 != null) {
         synchronized (this.passivePayloadRing) {
            while (this.passivePayloadRing.size() >= 96) {
               this.passivePayloadRing.removeFirst();
            }

            this.passivePayloadRing
               .addLast(new RiptideModule.PassivePayloadCapture(System.currentTimeMillis(), this.packetLoggerTickCounter, var2 == null ? "" : var2, var3, var1));
         }
      }
   }

   private RiptidePayloadChannelListeners.Match matchEnabledPayloadFilter(String var1, String var2) {
      if (var1 != null && !var1.isBlank()) {
         RiptidePayloadChannelListeners var3 = this.getPassivePayloadListeners();
         return var3.hasEnabledRules() ? var3.matchChannel(var1, var2) : null;
      } else {
         return null;
      }
   }

   private RiptidePayloadChannelListeners getPassivePayloadListeners() {
      if (this.passivePayloadListeners == null) {
         this.passivePayloadListeners = new RiptidePayloadChannelListeners();
      }

      return this.passivePayloadListeners;
   }

   private static List<Packet<?>> bundledPackets(BundlePacket<?> var0) {
      if (var0 == null) {
         return List.of();
      } else {
         ArrayList var1 = new ArrayList();

         try {
            for (Object var3 : var0.subPackets()) {
               if (var3 instanceof Packet var4) {
                  var1.add(var4);
               }
            }
         } catch (Throwable var5) {
         }

         return var1;
      }
   }

   private static Class<?> rawPayloadPacketClass(String var0) {
      return var0 != null && var0.equalsIgnoreCase("C2S") ? ServerboundCustomPayloadPacket.class : ClientboundCustomPayloadPacket.class;
   }

   private String rawPayloadFingerprint(RiptidePayloadSupport.PayloadSnapshot var1) {
      return var1 == null
         ? ""
         : (var1.direction() == null ? "" : var1.direction())
            + "|"
            + (var1.protocolPhase() == null ? "" : var1.protocolPhase())
            + "|"
            + var1.packetId()
            + "|"
            + (var1.channel() == null ? "" : var1.channel())
            + "|"
            + var1.sizeBytes()
            + "|"
            + Arrays.hashCode(var1.rawBytes());
   }

   private boolean isRawPayloadCaptured(String var1) {
      if (var1 != null && !var1.isBlank()) {
         synchronized (this.capturedRawPayloadFingerprintSet) {
            return this.capturedRawPayloadFingerprintSet.contains(var1);
         }
      } else {
         return false;
      }
   }

   private void markRawPayloadCaptured(String var1) {
      if (var1 != null && !var1.isBlank()) {
         synchronized (this.capturedRawPayloadFingerprintSet) {
            if (this.capturedRawPayloadFingerprintSet.add(var1)) {
               this.capturedRawPayloadFingerprints.addLast(var1);

               while (this.capturedRawPayloadFingerprints.size() > 256) {
                  String var3 = this.capturedRawPayloadFingerprints.removeFirst();
                  this.capturedRawPayloadFingerprintSet.remove(var3);
               }
            }
         }
      }
   }

   public void toggle() {
      RiptideClientMessaging.sendPrefixed("Riptide is always enabled in standalone mode.");
   }

   private void beginPassivePayloadCapture() {
      if (!PackHideState.isActive()) {
         this.passivePayloadCaptureUntilMs = Math.max(this.passivePayloadCaptureUntilMs, System.currentTimeMillis() + 20000L);
         RiptideNetworkCaptureState.refresh(this);
      }
   }

   public void appendTooltip(ItemStack var1, List<?> var2) {
      ModuleRegistry.appendTooltip(var1, var2);
   }

   public boolean handlePacketSend(Packet<?> var1) {
      return this.handlePacketSend(var1, false);
   }

   public boolean handlePacketSend(Packet<?> var1, boolean var2) {
      long var3 = RiptidePerf.beginJoin();

      boolean var6;
      try {
         if (PackHideState.isActive()) {
            return false;
         }

         this.observePluginDiscoveryPacketSend(var1);
         if (this.shouldObservePluginPayloadFingerprints()) {
            this.observePluginPayloadFingerprint(var1, "C2S");
         }

         RiptidePacketLoggerOverlay var5 = this.getPacketLoggerOverlayIfExists();
         if (ModuleRegistry.onPacketSend(var1)) {
            return true;
         }

         if (AddonEvents.firePacketSend(var1)) {
            return true;
         }

         if (var5 != null) {
            if (!var2 && !var5.isPacketBlocked(var1.getClass())) {
               var5.logPacket(var1, "C2S");
            }

            return false;
         }

         var6 = false;
      } finally {
         RiptidePerf.endJoinSpike("join.packetSendHook", var3);
      }

      return var6;
   }

   public boolean handlePacketReceive(Packet<?> var1) {
      return this.handlePacketReceive(var1, false);
   }

   public boolean handlePacketReceive(Packet<?> var1, boolean var2) {
      long var3 = RiptidePerf.beginJoin();

      boolean var6;
      try {
         if (PackHideState.isActive()) {
            return false;
         }

         this.observePluginDiscoveryPacketReceive(var1);
         if (this.shouldObservePluginPayloadFingerprints()) {
            this.observePluginPayloadFingerprint(var1, "S2C");
         }

         RiptidePacketLoggerOverlay var5 = this.getPacketLoggerOverlayIfExists();
         if (ModuleRegistry.onPacketReceive(var1)) {
            return true;
         }

         AddonEvents.firePacketReceive(var1);
         if (var5 != null) {
            if (!var2 && !var5.isPacketBlocked(var1.getClass())) {
               var5.logPacket(var1, "S2C");
            }

            return false;
         }

         var6 = false;
      } finally {
         RiptidePerf.endJoinSpike("join.packetReceiveHook", var3);
      }

      return var6;
   }

   private void observePluginPayloadFingerprint(Packet<?> var1, String var2) {
      if (var1 != null && RiptidePacketLoggerOverlay.isPayloadPacket(var1)) {
         CustomPacketPayload var3 = RiptidePayloadSupport.extractPayload(var1);
         if (var3 != null) {
            String var4 = RiptidePayloadSupport.payloadChannel(var3);
            boolean var5 = "S2C".equalsIgnoreCase(var2);
            if (var5) {
               RiptidePayloadChannelSubscriptionManager.rememberObservedChannel(var4);
            }

            try {
               RiptidePayloadSupport.PayloadSnapshot var6 = RiptidePayloadSupport.snapshot(var1, var2);
               RiptidePayloadStudySession.recordPayload(var6);
               if (var5) {
                  RiptidePayloadChannelSubscriptionManager.rememberObservedPayload(var6);
               }
            } catch (Throwable var10) {
            }

            if (var5 && RiptidePluginPayloadFingerprints.shouldObserveChannel(var4)) {
               try {
                  RiptidePayloadSupport.PayloadSnapshot var11 = RiptidePayloadSupport.snapshot(var1, var2);
                  if (var11 == null) {
                     return;
                  }

                  boolean var7 = RiptidePluginPayloadFingerprints.observe(
                     this.currentPayloadFingerprintServerAddress(), this.currentPayloadFingerprintBrand(), var11
                  );
                  if (var7) {
                     RiptideServerInfoOverlay var8 = this.getServerDataOverlayIfExists();
                     if (var8 != null) {
                        var8.onPayloadFingerprintUpdated();
                     }
                  }
               } catch (Throwable var9) {
               }
            }
         }
      }
   }

   private void observePluginPayloadFingerprint(RiptidePayloadSupport.PayloadSnapshot var1) {
      if (var1 != null) {
         String var2 = var1.channel();
         RiptidePayloadStudySession.recordPayload(var1);
         boolean var3 = "S2C".equalsIgnoreCase(var1.direction());
         if (var3) {
            RiptidePayloadChannelSubscriptionManager.rememberObservedChannel(var2);
            RiptidePayloadChannelSubscriptionManager.rememberObservedPayload(var1);
         }

         if (var3 && RiptidePluginPayloadFingerprints.shouldObserveChannel(var2)) {
            try {
               boolean var4 = RiptidePluginPayloadFingerprints.observe(
                  this.currentPayloadFingerprintServerAddress(), this.currentPayloadFingerprintBrand(), var1
               );
               if (var4) {
                  RiptideServerInfoOverlay var5 = this.getServerDataOverlayIfExists();
                  if (var5 != null) {
                     var5.onPayloadFingerprintUpdated();
                  }
               }
            } catch (Throwable var6) {
            }
         }
      }
   }

   private String currentPayloadFingerprintBrand() {
      if (MC != null && MC.getConnection() != null) {
         String var1 = MC.getConnection().serverBrand();
         return var1 == null ? "" : var1;
      } else {
         return "";
      }
   }

   private String currentPayloadFingerprintServerAddress() {
      if (MC == null) {
         return "";
      } else {
         ServerData var1 = MC.getCurrentServer();
         if (var1 != null && var1.ip != null && !var1.ip.isBlank()) {
            return var1.ip.trim().toLowerCase(Locale.ROOT);
         } else {
            if (MC.getConnection() != null && MC.getConnection().getConnection() != null) {
               SocketAddress var2 = MC.getConnection().getConnection().getRemoteAddress();
               if (var2 instanceof InetSocketAddress var3) {
                  String var4 = var3.getHostString();
                  if ((var4 == null || var4.isBlank()) && var3.getAddress() != null) {
                     var4 = var3.getAddress().getHostAddress();
                  }

                  if (var4 != null && !var4.isBlank()) {
                     return (var4 + ":" + var3.getPort()).trim().toLowerCase(Locale.ROOT);
                  }
               } else if (var2 != null) {
                  String var5 = var2.toString();
                  if (var5 != null && !var5.isBlank()) {
                     return var5.replaceFirst("^/", "").trim().toLowerCase(Locale.ROOT);
                  }
               }
            }

            return "";
         }
      }
   }

   public RiptidePacketLoggerOverlay getPacketLoggerOverlay() {
      if (this.packetLoggerOverlay == null && MC != null && MC.font != null) {
         this.packetLoggerOverlay = new RiptidePacketLoggerOverlay(MC.font);
         this.packetLoggerOverlay.restoreState();
         this.hydratePassivePayloads(this.packetLoggerOverlay);
      }

      return this.packetLoggerOverlay;
   }

   public RiptidePacketLoggerOverlay getPacketLoggerOverlayIfExists() {
      return this.packetLoggerOverlay;
   }

   private void hydratePassivePayloads(RiptidePacketLoggerOverlay var1) {
      if (var1 != null) {
         ArrayList var2;
         synchronized (this.passivePayloadRing) {
            if (this.passivePayloadRing.isEmpty()) {
               return;
            }

            var2 = new ArrayList<>(this.passivePayloadRing);
            this.passivePayloadRing.clear();
         }

         for (RiptideModule.PassivePayloadCapture var4 : var2) {
            var1.logPayloadSnapshotSilently(var4.timestampMs(), var4.gameTick(), var4.direction(), var4.packetClass(), var4.snapshot());
         }
      }
   }

   public RiptideServerInfoOverlay getServerDataOverlay() {
      if (this.serverInfoOverlay == null && MC != null && MC.font != null) {
         this.serverInfoOverlay = new RiptideServerInfoOverlay(MC.font);
         this.serverInfoOverlay.restoreState();
      }

      return this.serverInfoOverlay;
   }

   public RiptideServerInfoOverlay getServerDataOverlayIfExists() {
      return this.serverInfoOverlay;
   }

   public boolean isNoPauseOnLostFocus() {
      return this.config.noPauseOnLostFocus;
   }

   public boolean isLANSyncEnabled() {
      return this.config.lanSyncEnabled;
   }

   public boolean isBypassResourcePack() {
      return this.config != null ? this.config.pretendPackAccepted : RiptideConfig.getGlobal().pretendPackAccepted;
   }

   public boolean isSpoofClientVanilla() {
      return this.config != null ? this.config.spoofClientVanilla : RiptideConfig.getGlobal().spoofClientVanilla;
   }

   public boolean isInventoryMoveEnabled() {
      Module var1 = ModuleRegistry.get("inv-move");
      return var1 != null ? var1.isEnabled() : this.config != null && this.config.inventoryMove;
   }

   public void setInventoryMoveEnabled(boolean var1) {
      if (this.config != null) {
         this.config.inventoryMove = var1;
         Module var2 = ModuleRegistry.get("inv-move");
         if (var2 != null && var2.isEnabled() != var1) {
            var2.setEnabledSilently(var1);
         }

         this.saveConfig();
      }
   }

   public boolean isXCarryEnabled() {
      Module var1 = this.xcarryModule();
      return var1 != null ? var1.isEnabled() : this.config != null && this.config.xCarry;
   }

   public void setXCarryEnabled(boolean var1) {
      if (this.config != null) {
         this.config.xCarry = var1;
         Module var2 = ModuleRegistry.get("xcarry");
         if (var2 != null && var2.isEnabled() != var1) {
            var2.setEnabledSilently(var1);
         }

         this.saveConfig();
      }
   }

   public void setBypassResourcePack(boolean var1) {
      this.config.pretendPackAccepted = var1;
      RiptideSharedState.get().setBypassResourcePack(var1);
      this.saveConfig();
   }

   public void setSpoofClientVanilla(boolean var1) {
      if (this.config != null) {
         this.config.spoofClientVanilla = var1;
         this.saveConfig();
      }
   }

   public boolean isForceDenyResourcePack() {
      return this.config != null ? this.config.autoDenyResourcePack : RiptideConfig.getGlobal().autoDenyResourcePack;
   }

   public void setForceDenyResourcePack(boolean var1) {
      this.config.autoDenyResourcePack = var1;
      RiptideSharedState.get().setResourcePackForceDeny(var1);
      this.saveConfig();
   }

   public boolean useMsSleepMode() {
      return this.config.useMsSleepMode;
   }

   public int getMsSleepInterval() {
      return this.config.msSleepInterval;
   }

   public boolean useInstantExecutionMode() {
      return this.config.instantExecutionMode;
   }

   public int getActionDelayUs() {
      return this.config.actionDelayUs;
   }

   public boolean usePacketBurstMode() {
      return this.config.packetBurstMode;
   }

   public boolean shouldUseDirectFlush() {
      return this.config.useDirectFlush;
   }

   public boolean shouldForceChannelFlush() {
      return this.config.forceChannelFlush;
   }

   public boolean shouldFlushQueueOnDelayDisable() {
      return this.config != null && this.config.flushQueueOnDelayDisable;
   }

   public void setFlushQueueOnDelayDisable(boolean var1) {
      if (this.config != null) {
         this.config.flushQueueOnDelayDisable = var1;
         this.saveConfig();
      }
   }

   public boolean isCaptureAsExact() {
      return this.config != null && this.config.captureAsExact;
   }

   public void setCaptureAsExact(boolean var1) {
      if (this.config != null) {
         this.config.captureAsExact = var1;
         this.saveConfig();
      }
   }

   public boolean shouldUseCustomPackets() {
      return this.config.useCustomPackets;
   }

   public void setUseCustomPackets(boolean var1) {
      this.config.useCustomPackets = var1;
      RiptideSharedState.get().setUseCustomPackets(var1);
      this.saveConfig();
   }

   public void setSendGuiPackets(boolean var1) {
      this.config.sendGuiPackets = var1;
      RiptideSharedState.get().setSendGuiPackets(var1);
   }

   public boolean applySendGuiPacketsUiBehavior(boolean var1) {
      this.setSendGuiPackets(var1);
      this.saveConfig();
      return var1;
   }

   public void setDelayGuiPackets(boolean var1) {
      this.config.delayGuiPackets = var1;
      RiptideSharedState.get().setDelayGuiPackets(var1);
   }

   public String getCommandPrefix() {
      return this.config == null ? "" : (this.config.commandPrefix == null ? "" : this.config.commandPrefix);
   }

   public void setCommandPrefix(String var1) {
      if (this.config != null) {
         this.config.commandPrefix = RiptideCompatManager.normalizeStoredCommandPrefix(var1);
         this.saveConfig();
      }
   }

   public Map<Integer, String> getCommandBinds() {
      if (this.config == null) {
         return Collections.emptyMap();
      } else {
         if (this.config.commandBinds == null) {
            this.config.commandBinds = new LinkedHashMap<>();
         }

         return this.config.commandBinds;
      }
   }

   public boolean hasCommandBinds() {
      return this.config != null && this.config.commandBinds != null && !this.config.commandBinds.isEmpty();
   }

   public void setCommandBind(int var1, String var2) {
      if (this.config != null) {
         if (this.config.commandBinds == null) {
            this.config.commandBinds = new LinkedHashMap<>();
         }

         if (var2 != null && !var2.isBlank()) {
            this.config.commandBinds.put(var1, var2);
         } else {
            this.config.commandBinds.remove(var1);
         }

         this.saveConfig();
      }
   }

   public void clearCommandBind(int var1) {
      if (this.config != null && this.config.commandBinds != null) {
         this.config.commandBinds.remove(var1);
         this.saveConfig();
      }
   }

   private Module xcarryModule() {
      int var1 = ModuleRegistry.revision();
      if (var1 != this.xcarryRevision) {
         this.xcarryCached = ModuleRegistry.get("xcarry");
         this.xcarryRevision = var1;
      }

      return this.xcarryCached;
   }

   public boolean isXCarryUseCrafting() {
      Module var1 = this.xcarryModule();
      if (var1 == null) {
         return true;
      } else {
         String var2 = var1.value("use-crafting");
         return var2 == null || var2.isBlank() || Boolean.parseBoolean(var2);
      }
   }

   public boolean isXCarryUseArmor() {
      Module var1 = this.xcarryModule();
      if (var1 == null) {
         return true;
      } else {
         String var2 = var1.value("use-armor");
         return var2 == null || var2.isBlank() || Boolean.parseBoolean(var2);
      }
   }

   public boolean isXCarryUseOffhand() {
      Module var1 = this.xcarryModule();
      if (var1 == null) {
         return true;
      } else {
         String var2 = var1.value("use-offhand");
         return var2 == null || var2.isBlank() || Boolean.parseBoolean(var2);
      }
   }

   public void setXCarryUseCrafting(boolean var1) {
      Module var2 = this.xcarryModule();
      if (var2 != null) {
         var2.setValue("use-crafting", Boolean.toString(var1));
      }
   }

   public void setXCarryUseArmor(boolean var1) {
      Module var2 = this.xcarryModule();
      if (var2 != null) {
         var2.setValue("use-armor", Boolean.toString(var1));
      }
   }

   public void setXCarryUseOffhand(boolean var1) {
      Module var2 = this.xcarryModule();
      if (var2 != null) {
         var2.setValue("use-offhand", Boolean.toString(var1));
      }
   }

   public boolean isXCarryCarryCursor() {
      Module var1 = this.xcarryModule();
      if (var1 == null) {
         return true;
      } else {
         String var2 = var1.value("carry-cursor");
         return var2 == null || var2.isBlank() || Boolean.parseBoolean(var2);
      }
   }

   public void setXCarryCarryCursor(boolean var1) {
      Module var2 = this.xcarryModule();
      if (var2 != null) {
         var2.setValue("carry-cursor", Boolean.toString(var1));
      }
   }

   public Set<Integer> getXCarryModuleSlotMask() {
      LinkedHashSet var1 = new LinkedHashSet();
      if (this.isXCarryUseCrafting()) {
         var1.add(1);
         var1.add(2);
         var1.add(3);
         var1.add(4);
         var1.add(0);
      }

      if (this.isXCarryUseArmor()) {
         var1.add(5);
         var1.add(6);
         var1.add(7);
         var1.add(8);
      }

      if (this.isXCarryUseOffhand()) {
         var1.add(45);
      }

      if (var1.isEmpty()) {
         var1.add(1);
         var1.add(2);
         var1.add(3);
         var1.add(4);
         var1.add(0);
      }

      return var1;
   }

   public int applyDelayGuiPacketsUiBehavior(boolean var1) {
      this.setDelayGuiPackets(var1);
      this.saveConfig();
      return !var1 && this.shouldFlushQueueOnDelayDisable() && MC != null && MC.getConnection() != null
         ? RiptideSharedState.get().flushDelayedPackets(MC.getConnection())
         : 0;
   }

   public int flushQueuedPacketsUiBehavior() {
      return MC != null && MC.getConnection() != null ? RiptideSharedState.get().flushDelayedPackets(MC.getConnection()) : 0;
   }

   public int clearQueuedPacketsUiBehavior() {
      RiptideSharedState var1 = RiptideSharedState.get();
      return var1.clearQueuedPackets();
   }

   public void notifyDelayPacketsUiResult(boolean var1, int var2) {
      RiptideSharedState var3 = RiptideSharedState.get();
      if (var1) {
         RiptideNotifications.show("Delay Packets on", -13248397);
      } else if (var2 > 0) {
         if (var3.isStaggering()) {
            var3.setPendingQueueCompletionMessage("Sent " + var2 + " packet" + (var2 == 1 ? "" : "s") + ".");
            RiptideNotifications.show("Delay Packets off - sending " + var2, -42149);
         } else {
            RiptideNotifications.show("Delay Packets off - sent " + var2, -42149);
         }
      } else if (!this.shouldFlushQueueOnDelayDisable() && (!var3.getDelayedPackets().isEmpty() || !var3.getStaggeredQueue().isEmpty())) {
         RiptideNotifications.show("Delay Packets off - queue kept", -42149);
      } else {
         RiptideNotifications.show("Delay Packets off", -42149);
      }
   }

   public void notifyFlushQueuedPacketsUiResult(int var1) {
      RiptideSharedState var2 = RiptideSharedState.get();
      if (var1 > 0) {
         if (var2.isStaggering()) {
            var2.setPendingQueueCompletionMessage("Sent " + var1 + " packet" + (var1 == 1 ? "" : "s") + ".");
            RiptideNotifications.show("Sending " + var1 + " packet" + (var1 == 1 ? "" : "s"), -14249);
         } else {
            RiptideNotifications.show("Sent " + var1 + " packet" + (var1 == 1 ? "" : "s"), -13248397);
         }
      } else {
         RiptideNotifications.show("Queue empty", -42149);
      }
   }

   public void notifyClearQueuedPacketsUiResult(int var1) {
      RiptideNotifications.show(var1 > 0 ? "Cleared " + var1 + " packet" + (var1 == 1 ? "" : "s") : "Queue empty", var1 > 0 ? -14249 : -42149);
   }

   public boolean togglePacketLoggerUiBehavior() {
      RiptidePacketLoggerOverlay var1 = this.getPacketLoggerOverlay();
      if (var1 == null) {
         return false;
      } else {
         RiptideOverlayManager.get().register(var1);
         var1.toggle();
         return true;
      }
   }

   public IRiptideOverlay getMatchmakingOverlay() {
      return null;
   }

   public boolean toggleMatchmakingUiBehavior() {
      return false;
   }

   public IRiptideOverlay getProfilesOverlay() {
      if (RiptideLiteVariant.enabled()) {
         return null;
      } else {
         if (this.profilesOverlay == null && MC != null && MC.font != null) {
            this.profilesOverlay = new RiptideProfilesOverlay(MC.font);
            this.profilesOverlay.restoreLayout();
         }

         return this.profilesOverlay;
      }
   }

   public IRiptideOverlay getMultiOverlay() {
      if (RiptideLiteVariant.enabled()) {
         return null;
      } else {
         if (this.multiOverlay == null && MC != null && MC.font != null) {
            this.multiOverlay = new RiptideMultiOverlay(MC.font);
            this.multiOverlay.restoreLayout();
         }

         return this.multiOverlay;
      }
   }

   public IRiptideOverlay getMatchmakingOverlayIfExists() {
      return this.matchmakingOverlay;
   }

   public IRiptideOverlay getProfilesOverlayIfExists() {
      return this.profilesOverlay;
   }

   public IRiptideOverlay getMultiOverlayIfExists() {
      return this.multiOverlay;
   }

   public void hideMenuOverlays() {
      if (!RiptideLiteVariant.enabled()) {
         try {
            if (this.matchmakingOverlay != null && this.matchmakingOverlay.isVisible()) {
               this.matchmakingOverlay.setVisible(false);
            }
         } catch (Throwable var3) {
         }

         try {
            if (this.profilesOverlay != null && this.profilesOverlay.isVisible()) {
               this.profilesOverlay.setVisible(false);
            }
         } catch (Throwable var2) {
         }

         try {
            MultiManager var1 = MultiManager.getIfInitialized();
            if ((var1 == null || !var1.isActive()) && this.multiOverlay != null && this.multiOverlay.isVisible()) {
               this.multiOverlay.setVisible(false);
            }
         } catch (Throwable var4) {
         }
      }
   }

   public boolean toggleMultiUiBehavior() {
      if (RiptideLiteVariant.enabled()) {
         return false;
      } else {
         IRiptideOverlay var1 = this.getMultiOverlay();
         if (var1 == null) {
            return false;
         } else {
            RiptideOverlayManager var2 = RiptideOverlayManager.get();
            if (var1.isVisible() && !var2.isTemporarilyHidden(var1)) {
               var1.setVisible(false);
               return true;
            } else {
               RiptideMultiDisclaimerScreen.open(MC, MC == null ? null : MC.gui.screen(), this::showMultiOverlay);
               return true;
            }
         }
      }
   }

   private void showMultiOverlay() {
      IRiptideOverlay var1 = this.getMultiOverlay();
      if (var1 != null) {
         RiptideOverlayManager var2 = RiptideOverlayManager.get();
         var2.register(var1);
         var2.setTemporarilyHidden(var1, false);
         ((RiptideMultiOverlay)var1).openInGameInteractive();
         var2.bringToFront(var1);
      }
   }

   public boolean openMultiUiInGame() {
      if (RiptideLiteVariant.enabled()) {
         return false;
      } else {
         IRiptideOverlay var1 = this.getMultiOverlay();
         if (var1 == null) {
            return false;
         } else {
            this.showMultiOverlay();
            if (MC != null) {
               MC.gui.setScreen(new RiptideOverlayHostScreen(var1, null, false, true));
            }

            return true;
         }
      }
   }

   public boolean toggleProfilesUiBehavior() {
      if (RiptideLiteVariant.enabled()) {
         return false;
      } else {
         IRiptideOverlay var1 = this.getProfilesOverlay();
         if (var1 == null) {
            return false;
         } else if (var1.isVisible()) {
            var1.setVisible(false);
            return true;
         } else {
            RiptideOverlayManager.get().register(var1);
            ((RiptideProfilesOverlay)var1).openInGameInteractive();
            RiptideOverlayManager.get().bringToFront(var1);
            if (MC != null) {
               MC.gui.setScreen(new RiptideOverlayHostScreen(var1, null, false, true));
            }

            return true;
         }
      }
   }

   public boolean restoreSavedScreenUiBehavior() {
      RiptideSharedState var1 = RiptideSharedState.get();
      if (MC == null) {
         return false;
      } else if (var1.getStoredScreen() != null && var1.getStoredAbstractContainerMenu() != null) {
         MC.execute(() -> {
            MC.gui.setScreen(var1.getStoredScreen());
            AbstractContainerMenu var1x = var1.getStoredAbstractContainerMenu();
            if (MC.player != null) {
               MC.player.containerMenu = var1x;
            }
         });
         return true;
      } else {
         return false;
      }
   }

   public Set<Class<? extends Packet<?>>> getC2SPackets() {
      return new LinkedHashSet<>(RiptideSharedState.get().getC2SPackets());
   }

   public Set<Class<? extends Packet<?>>> getS2CPackets() {
      return new LinkedHashSet<>(RiptideSharedState.get().getS2CPackets());
   }

   public void setC2SPackets(Set<Class<? extends Packet<?>>> var1) {
      Object var2 = var1 == null ? this.defaultC2SPackets() : new LinkedHashSet(var1);
      RiptideSharedState.get().setC2SPackets((Set<Class<? extends Packet<?>>>)var2);
      this.config.c2sPackets = this.encodePackets((Set<Class<? extends Packet<?>>>)var2);
      this.saveConfig();
   }

   public void setS2CPackets(Set<Class<? extends Packet<?>>> var1) {
      Object var2 = var1 == null ? this.defaultS2CPackets() : new LinkedHashSet(var1);
      RiptideSharedState.get().setS2CPackets((Set<Class<? extends Packet<?>>>)var2);
      this.config.s2cPackets = this.encodePackets((Set<Class<? extends Packet<?>>>)var2);
      this.saveConfig();
   }

   public void resetC2SPacketsToDefault() {
      this.setC2SPackets(this.defaultC2SPackets());
   }

   public void resetS2CPacketsToDefault() {
      this.setS2CPackets(this.defaultS2CPackets());
   }

   public Set<Class<? extends Packet<?>>> defaultC2SPackets() {
      LinkedHashSet var1 = new LinkedHashSet();

      for (Class var3 : RiptidePacketRegistry.getC2SPackets()) {
         if (!C2S_EXCLUDED_DEFAULTS.contains(var3)) {
            var1.add(var3);
         }
      }

      var1.add(ServerboundChatPacket.class);
      var1.add(ServerboundChatCommandPacket.class);
      var1.add(ServerboundChatCommandSignedPacket.class);
      var1.add(ServerboundSignUpdatePacket.class);
      return var1;
   }

   public Set<Class<? extends Packet<?>>> defaultS2CPackets() {
      return new LinkedHashSet<>();
   }

   private void applyConfigToSharedState() {
      RiptideSharedState var1 = RiptideSharedState.get();
      this.applyRuntimePacketFlowDefaults();
      var1.setUseCustomPackets(this.config.useCustomPackets);
      var1.setC2SPackets(this.resolvePackets(this.config.c2sPackets, true));
      var1.setS2CPackets(this.resolvePackets(this.config.s2cPackets, false));
      var1.setAllowSignEditing(this.config.allowSignEditing);
      var1.setResourcePackForceDeny(this.config.autoDenyResourcePack);
      var1.setBypassResourcePack(this.config.pretendPackAccepted);
      var1.setStaggeredPacketSend(this.config.staggeredPacketSend);
      var1.setStaggeredSendDelay(this.config.staggeredSendDelay);
   }

   private void applyRuntimePacketFlowDefaults() {
      if (this.config != null) {
         this.config.applyRuntimeDefaults();
      }

      RiptideSharedState var1 = RiptideSharedState.get();
      var1.setSendGuiPackets(true);
      var1.setDelayGuiPackets(false);
      var1.setStaggeredPacketSend(false);
      var1.setCaptureMode(false);
   }

   private void updatePassiveXCarryState() {
      RiptideSharedState var1 = RiptideSharedState.get();
      if (!var1.isXCarryForced() && (var1.isXCarryActive() || this.isXCarryEnabled())) {
         if (PackHideState.isActive()) {
            var1.setXCarryActive(false);
         } else {
            boolean var2 = false;
            if (this.isXCarryEnabled() && MC.player != null && MC.player.inventoryMenu != null) {
               var2 = XCarryAction.hasStoredItems(MC.player.inventoryMenu, true);
            }

            var1.setXCarryActive(var2);
         }
      }
   }

   private void updateWorldSpawnState() {
      if (!this.joinedPlayConnection && this.isPlayerSpawnedInWorld()) {
         if (++this.stuckActiveTicks >= 20) {
            this.joinedPlayConnection = true;
            this.spawnedInWorld = true;
            this.stuckActiveTicks = 0;
            this.logWatchdogRecovery();
            return;
         }
      } else {
         this.stuckActiveTicks = 0;
      }

      if (!this.joinedPlayConnection) {
         this.spawnedInWorld = false;
         this.autoProbePending = false;
      } else {
         boolean var1 = this.spawnedInWorld;
         this.spawnedInWorld = this.isPlayerSpawnedInWorld();
         if (!var1 && this.spawnedInWorld && !PackHideState.isActive()) {
            RiptideJoinMacroController.onWorldReady();
            this.autoProbePending = true;
            this.autoProbePendingSince = System.currentTimeMillis();
         }

         this.tickAutoProbe();
      }
   }

   private void tickAutoProbe() {
      if (this.autoProbePending) {
         long var1 = System.currentTimeMillis() - this.autoProbePendingSince;
         if (var1 > 25000L) {
            this.autoProbePending = false;
         } else if (PackHideState.isActive() || this.config == null || !this.config.autoProbePlugins) {
            this.autoProbePending = false;
         } else if (this.spawnedInWorld) {
            RiptideServerInfoOverlay var3 = this.getServerDataOverlay();
            if (var3 != null && var3.isAutoProbeConnectionReady() && (var3.isAutoProbeContextReady() || var1 >= 2000L) && var3.autoProbeOnSpawn()) {
               this.autoProbePending = false;
            }
         }
      }
   }

   private boolean isPlayerSpawnedInWorld() {
      return MC != null && MC.getConnection() != null && MC.player != null && MC.level != null;
   }

   private void saveConfig() {
      if (this.config != null) {
         this.config.save();
      }
   }

   private Set<Class<? extends Packet<?>>> resolvePackets(List<String> var1, boolean var2) {
      LinkedHashSet var3 = new LinkedHashSet();
      if (var1 != null) {
         for (String var5 : var1) {
            if (var5 != null && !var5.isBlank()) {
               Class var6 = RiptidePacketRegistry.getPacket(var5);
               if (var6 == null) {
                  try {
                     Class var7 = Class.forName(var5);
                     if (Packet.class.isAssignableFrom(var7)) {
                        var6 = var7;
                     }
                  } catch (ClassNotFoundException var9) {
                  }
               }

               if (var6 != null) {
                  var3.add(var6);
               }
            }
         }
      }

      if (var3.isEmpty()) {
         return var2 ? this.defaultC2SPackets() : this.defaultS2CPackets();
      } else {
         return var3;
      }
   }

   private List<String> encodePackets(Set<Class<? extends Packet<?>>> var1) {
      ArrayList var2 = new ArrayList();

      for (Class var4 : var1) {
         String var5 = RiptidePacketRegistry.getName(var4);
         var2.add(var5 != null ? var5 : var4.getName());
      }

      return var2;
   }

   private void tickKeybinds() {
      RiptideConfig var1 = this.config;
      if (var1 != null) {
         RiptideSharedState var2 = RiptideSharedState.get();
         this.refreshKeyboundMacroCache();
         if (var1.keybindModuleMenu != -1) {
            boolean var3 = this.isBindPressed(var1.keybindModuleMenu);
            if (var3 && !this.moduleMenuKeyPressed && MC.gui.screen() == null && !RiptideLiteVariant.enabled()) {
               MC.gui.setScreen(new RiptideModuleScreen(null));
            }

            this.moduleMenuKeyPressed = var3;
         }

         if (PackHideState.isActive()) {
            this.loadGuiKeyPressed = false;
            this.flushQueueKeyPressed = false;
            this.clearQueueKeyPressed = false;
            this.toggleLoggerKeyPressed = false;
            this.toggleSendKeyPressed = false;
            this.toggleDelayKeyPressed = false;
            this.macroKeyStates.clear();
         } else {
            if (var1.keybindLoadGui != -1) {
               boolean var8 = this.isBindPressed(var1.keybindLoadGui);
               if (var8 && !this.loadGuiKeyPressed) {
                  if (this.restoreSavedScreenUiBehavior()) {
                     RiptideNotifications.show("GUI restored.", -13248397);
                  } else {
                     RiptideNotifications.error("No stored GUI.");
                  }
               }

               this.loadGuiKeyPressed = var8;
            }

            if (var1.keybindFlushQueue != -1) {
               boolean var9 = this.isBindPressed(var1.keybindFlushQueue);
               if (var9 && !this.flushQueueKeyPressed) {
                  int var4 = this.flushQueuedPacketsUiBehavior();
                  this.notifyFlushQueuedPacketsUiResult(var4);
               }

               this.flushQueueKeyPressed = var9;
            }

            if (var1.keybindClearQueue != -1) {
               boolean var10 = this.isBindPressed(var1.keybindClearQueue);
               if (var10 && !this.clearQueueKeyPressed) {
                  int var15 = this.clearQueuedPacketsUiBehavior();
                  this.notifyClearQueuedPacketsUiResult(var15);
               }

               this.clearQueueKeyPressed = var10;
            }

            if (var1.keybindToggleLogger != -1) {
               boolean var11 = this.isBindPressed(var1.keybindToggleLogger);
               if (var11 && !this.toggleLoggerKeyPressed) {
                  this.togglePacketLoggerUiBehavior();
               }

               this.toggleLoggerKeyPressed = var11;
            }

            if (var1.keybindToggleSend != -1) {
               boolean var12 = this.isBindPressed(var1.keybindToggleSend);
               if (var12 && !this.toggleSendKeyPressed) {
                  boolean var16 = !var2.shouldSendGuiPackets();
                  this.applySendGuiPacketsUiBehavior(var16);
                  RiptideNotifications.show("Send Packets " + (var16 ? "on" : "off"), var16 ? -13248397 : -50373);
               }

               this.toggleSendKeyPressed = var12;
            }

            if (var1.keybindToggleDelay != -1) {
               boolean var13 = this.isBindPressed(var1.keybindToggleDelay);
               if (var13 && !this.toggleDelayKeyPressed) {
                  boolean var17 = !var2.shouldDelayGuiPackets();
                  int var5 = this.applyDelayGuiPacketsUiBehavior(var17);
                  this.notifyDelayPacketsUiResult(var17, var5);
               }

               this.toggleDelayKeyPressed = var13;
            }

            for (RiptideMacro var18 : this.cachedKeyboundMacros) {
               boolean var19 = this.isBindPressed(var18.keyCode);
               boolean var6 = this.macroKeyStates.getOrDefault(var18.name, false);
               if (var19 && !var6) {
                  if (MultiTakeoverState.isActive()) {
                     MultiManager var7 = MultiManager.getIfInitialized();
                     if (var7 != null && var7.isMacroPlayingOnInteractiveScope(var18.name, Set.of())) {
                        var7.stopMacroOnInteractiveScope(Set.of());
                     } else {
                        var18.execute();
                     }
                  } else if (MacroExecutor.isMacroRunning(var18)) {
                     MacroExecutor.stopMacro(var18);
                  } else {
                     var18.execute();
                  }
               }

               this.macroKeyStates.put(var18.name, var19);
            }
         }
      }
   }

   private void refreshKeyboundMacroCache() {
      RiptideMacroManager var1 = RiptideMacroManager.get();
      long var2 = var1.getRevision();
      if (var2 != this.cachedMacroKeybindRevision) {
         ArrayList var4 = new ArrayList();
         HashSet var5 = new HashSet();

         for (RiptideMacro var7 : var1.getAll()) {
            if (var7 != null && var7.keyCode != -1) {
               var4.add(var7);
               var5.add(var7.name);
            }
         }

         this.macroKeyStates.keySet().removeIf(var1x -> !var5.contains(var1x));
         this.cachedKeyboundMacros = var4;
         this.cachedMacroKeybindRevision = var2;
      }
   }

   private boolean isAnyTextFieldFocused() {
      return RiptideOverlayManager.get().isAnyTextFieldFocused();
   }

   private boolean isBindPressed(int var1) {
      return RiptideBindUtil.isBindPressed(MC, var1);
   }

   private void restoreSavedScreen() {
      RiptideSharedState var1 = RiptideSharedState.get();
      if (MC != null) {
         if (var1.getStoredScreen() != null && var1.getStoredAbstractContainerMenu() != null) {
            MC.gui.setScreen(var1.getStoredScreen());
            AbstractContainerMenu var2 = var1.getStoredAbstractContainerMenu();
            if (MC.player != null) {
               MC.player.containerMenu = var2;
            }

            RiptideNotifications.show("GUI restored.", -13248397);
         } else {
            RiptideNotifications.error("No stored GUI.");
         }
      }
   }

   public record PacketHookSnapshot(boolean normalPath, boolean passivePayloadCapture, boolean packetLoggerCapturing, boolean pluginDiscoveryObservation) {
      private static final RiptideModule.PacketHookSnapshot INACTIVE = new RiptideModule.PacketHookSnapshot(false, false, false, false);

      public static RiptideModule.PacketHookSnapshot inactive() {
         return INACTIVE;
      }
   }

   private record PassivePayloadCapture(long timestampMs, int gameTick, String direction, Class<?> packetClass, RiptidePayloadSupport.PayloadSnapshot snapshot) {
   }
}
