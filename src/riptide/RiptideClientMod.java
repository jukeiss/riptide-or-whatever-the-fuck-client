package riptide;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndLevelTick;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents.Init;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import riptide.addons.AddonManager;
import riptide.commands.RiptideCommands;
import riptide.gui.vanillaui.components.UiText;
import riptide.modules.HoleEspModule;
import riptide.modules.ModuleOreSim;
import riptide.modules.PackAutoReconnectState;
import riptide.modules.RiptideJoinGrace;
import riptide.modules.RiptideModule;
import riptide.modules.ScaffoldModule;
import riptide.modules.SusChunkFinderModule;
import riptide.render.RiptideFemaleBodyRenderer;
import riptide.security.RiptideItemNbtSanity;
import riptide.security.RiptidePackResponseScheduler;
import riptide.security.RiptideProtector;
import riptide.security.RiptideProtectorPackStrip;
import riptide.security.RiptideProtectorServerPackFailureGuard;
import riptide.security.RiptideProtectorTracker;
import riptide.security.RiptideProtectorVanillaKeys;
import riptide.util.RiptideBlockNbtCapture;
import riptide.util.RiptideConfig;
import riptide.util.RiptideFakeGamemode;
import riptide.util.RiptideFavorites;
import riptide.util.RiptideFreecamHighlightRenderer;
import riptide.util.RiptideInstaBreakRenderer;
import riptide.util.RiptideJoinMacroController;
import riptide.util.RiptideKeyLock;
import riptide.util.RiptideKillAuraRenderer;
import riptide.util.RiptideLANSync;
import riptide.util.RiptideLagWatchdog;
import riptide.util.RiptideLiteVariant;
import riptide.util.RiptideMacroEditorOverlay;
import riptide.util.RiptideOverlayManager;
import riptide.util.RiptideRemoteView;
import riptide.util.RiptideScaffoldPlaceRenderer;
import riptide.util.RiptideSkeletonRenderer;
import riptide.util.RiptideSvgHudLogo;
import riptide.util.RiptideWindowBranding;
import riptide.util.RiptideWorldHighlightRenderer;
import riptide.util.macro.MacroExecutor;
import riptide.util.multi.MultiTakeoverState;
import riptide.util.multi.PacketTeleportController;

public final class RiptideClientMod implements ClientModInitializer {
   private static final ConcurrentHashMap<String, Long> LAST_TICK_ERROR_MS = new ConcurrentHashMap<>();

   private static void runSafe(String var0, Runnable var1) {
      try {
         var1.run();
      } catch (Throwable var3) {
         riptide.RiptideClientAddon.LOG.warn("[Riptide] client-event '{}' failed; isolated to protect the client", var0, var3);
      }
   }

   private static void logTickError(String var0, Throwable var1) {
      long var2 = System.currentTimeMillis();
      Long var4 = LAST_TICK_ERROR_MS.get(var0);
      if (var4 == null || var2 - var4 >= 5000L) {
         LAST_TICK_ERROR_MS.put(var0, var2);
         riptide.RiptideClientAddon.LOG.warn("[Riptide] tick '{}' failed; isolated to protect the client", var0, var1);
      }
   }

   public void onInitializeClient() {
      RiptideFavorites.init();
      RiptideKeyLock.verify();
      riptide.RiptideClientAddon.FOLDER.mkdirs();
      runSafe("initialize", () -> RiptideModule.get().initialize());
      RiptideProtectorTracker.bootstrap();
      if (!RiptideProtector.isOverlapExternalProtectorPresent()) {
         RiptideProtectorVanillaKeys.primeAsync();
      }

      if (RiptideProtector.isFullExternalProtectorPresent()) {
         riptide.RiptideClientAddon.LOG.info("[RiptideProtector] External protection mod detected; deferring all anti-fingerprint mixins to it.");
      } else if (RiptideProtector.isExploitPreventerPresent()) {
         riptide.RiptideClientAddon.LOG
            .info("[RiptideProtector] ExploitPreventer detected; deferring overlapping protections while keeping brand/channel hiding active.");
      } else {
         riptide.RiptideClientAddon.LOG.info("[RiptideProtector] Built-in anti-fingerprint layer active.");
      }

      RiptideInstaBreakRenderer.initialize();
      RiptideFreecamHighlightRenderer.initialize();
      RiptideSkeletonRenderer.initialize();
      RiptideWorldHighlightRenderer.initialize();
      RiptideScaffoldPlaceRenderer.initialize();
      RiptideKillAuraRenderer.initialize();
      HoleEspModule.initialize();
      SusChunkFinderModule.initialize();
      RiptideFemaleBodyRenderer.initialize();
      RiptideCommands.init();
      AddonManager.init();
      ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
         {
            Objects.requireNonNull(RiptideClientMod.this);
         }

         public Identifier getFabricId() {
            return Identifier.fromNamespaceAndPath("riptide", "ui_assets");
         }

         public void onResourceManagerReload(ResourceManager var1) {
            UiText.onClientResourceReload();
            RiptideSvgHudLogo.clear();
         }
      });
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)var0 -> {
         try {
            if (RiptideBlockNbtCapture.hasTickWork()) {
               RiptideBlockNbtCapture.tick(var0);
            }
         } catch (Throwable var12) {
            logTickError("blockNbtCapture", var12);
         }

         try {
            ScaffoldModule.endMovementTick();
         } catch (Throwable var11) {
            logTickError("scaffoldEndClick", var11);
         }

         try {
            RiptideWindowBranding.apply(var0);
         } catch (Throwable var10) {
         }

         try {
            RiptidePackResponseScheduler.tick();
         } catch (Throwable var9) {
            logTickError("packResponses", var9);
         }

         try {
            RiptideModule.get().tick();
         } catch (Throwable var8) {
            logTickError("module", var8);
         }

         try {
            ModuleOreSim.tickRetention();
         } catch (Throwable var7) {
            logTickError("oreSimRetention", var7);
         }

         try {
            PacketTeleportController.tick(var0);
         } catch (Throwable var6) {
            logTickError("pacedTp", var6);
         }

         try {
            RiptideJoinMacroController.onClientTick(var0);
         } catch (Throwable var5) {
            logTickError("joinMacro", var5);
         }

         try {
            RiptideLagWatchdog.onClientTick(var0);
         } catch (Throwable var4) {
            logTickError("lagWatchdog", var4);
         }

         try {
            if (!RiptideLiteVariant.enabled()) {
               MultiTakeoverState.tick();
            }
         } catch (Throwable var3) {
            logTickError("multiTakeover", var3);
         }

         try {
            RiptideRemoteView.tick();
         } catch (Throwable var2) {
            logTickError("remoteView", var2);
         }
      });
      ClientTickEvents.END_LEVEL_TICK
         .register((EndLevelTick)var0 -> runSafe("lan.levelTick", () -> RiptideLANSync.getInstance().onLevelTick(var0.getGameTime())));
      ClientConfigurationConnectionEvents.INIT.register((Init)(var0, var1) -> {
         runSafe("cfg.hideMenuOverlays", () -> RiptideModule.get().hideMenuOverlays());
         runSafe("cfg.configStarted", () -> RiptideModule.get().onConfigurationConnectionStarted());
         runSafe("cfg.joinMacro", () -> RiptideJoinMacroController.onConfigurationInit(var0));
      });
      ClientConfigurationConnectionEvents.DISCONNECT.register((Disconnect)(var0, var1) -> {
         runSafe("cfg.remoteView", () -> RiptideRemoteView.stop(false));
         runSafe("cfg.persist", RiptideConfig::enqueuePendingSaveNow);
         runSafe("cfg.pacedTp", () -> PacketTeleportController.cancelAll("connection changed"));
         runSafe("cfg.fakeGamemode", RiptideFakeGamemode::clear);
         runSafe("cfg.instaBreak", RiptideInstaBreakRenderer::clear);
         runSafe("cfg.packFailureGuard", RiptideProtectorServerPackFailureGuard::clear);
         runSafe("cfg.packResponses", RiptidePackResponseScheduler::clearAll);
         runSafe("cfg.hideOverlays", () -> RiptideOverlayManager.get().hideAllInteractiveOverlays());
         runSafe("cfg.onGameLeft", () -> RiptideModule.get().onGameLeft());
         runSafe("cfg.joinMacro", RiptideJoinMacroController::onConfigurationDisconnect);
      });
      ClientPlayConnectionEvents.JOIN.register((Join)(var0, var1, var2) -> {
         runSafe("join.pacedTp", () -> PacketTeleportController.cancelAll("connection changed"));
         runSafe("join.movementGrace", RiptideJoinGrace::onJoin);
         runSafe("join.hideMenuOverlays", () -> RiptideModule.get().hideMenuOverlays());
         runSafe("join.remember", () -> PackAutoReconnectState.remember(var2.getCurrentServer()));
         runSafe("join.onGameJoin", () -> RiptideModule.get().onGameJoin());
         runSafe("join.joinMacro", RiptideJoinMacroController::onPlayJoin);
         runSafe("join.surfaceFailures", AddonManager::surfaceFailuresOnJoin);
         runSafe("join.lagWatchdog", RiptideLagWatchdog::reset);
         runSafe("join.macroEditor", RiptideMacroEditorOverlay::onPlayJoin);
      });
      ClientPlayConnectionEvents.DISCONNECT.register((net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect)(var0, var1) -> {
         runSafe("leave.remoteView", () -> RiptideRemoteView.stop(false));
         runSafe("leave.persist", RiptideConfig::enqueuePendingSaveNow);
         runSafe("leave.stopMacro", () -> {
            if (RiptideConfig.getGlobal().stopMacroOnLeave) {
               MacroExecutor.stop();
            }
         });
         runSafe("leave.pacedTp", () -> PacketTeleportController.cancelAll("disconnected"));
         runSafe("leave.movementGrace", RiptideJoinGrace::clear);
         runSafe("leave.fakeGamemode", RiptideFakeGamemode::clear);
         runSafe("leave.instaBreak", RiptideInstaBreakRenderer::clear);
         runSafe("leave.packStrip", RiptideProtectorPackStrip::clearAll);
         runSafe("leave.packResponses", RiptidePackResponseScheduler::clearAll);
         runSafe("leave.packFailureGuard", RiptideProtectorServerPackFailureGuard::clear);
         runSafe("leave.macroEditor", RiptideMacroEditorOverlay::onPlayDisconnect);
         runSafe("leave.lanSync", () -> RiptideLANSync.getInstance().onGameDisconnected());
         runSafe("leave.hideOverlays", () -> RiptideOverlayManager.get().hideAllInteractiveOverlays());
         runSafe("leave.joinMacro", RiptideJoinMacroController::onGameLeft);
         runSafe("leave.onGameLeft", () -> RiptideModule.get().onGameLeft());
         runSafe("leave.lagWatchdog", RiptideLagWatchdog::reset);
      });
      ItemTooltipCallback.EVENT.register((ItemTooltipCallback)(var0, var1, var2, var3) -> {
         RiptideItemNbtSanity.scrubUnsafeTooltipLines(var3);
         RiptideItemNbtSanity.trimTooltipLines(var3);
         RiptideModule.get().appendTooltip(var0, var3);
      });
      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)var0 -> RiptideConfig.flushPendingSaves(2000L));
      Runtime.getRuntime().addShutdownHook(new Thread(() -> RiptideConfig.flushPendingSaves(2000L), "riptide-shutdown-save"));
   }
}
