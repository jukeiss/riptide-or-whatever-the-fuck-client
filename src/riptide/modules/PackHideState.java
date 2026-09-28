package riptide.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideConfig;
import riptide.util.RiptideContainerHold;
import riptide.util.RiptideEssentialBridge;
import riptide.util.RiptideInputClicker;
import riptide.util.RiptideInstaBreakRenderer;
import riptide.util.RiptideKillAuraRotation;
import riptide.util.RiptideLANSync;
import riptide.util.RiptideLiteVariant;
import riptide.util.RiptideMeteorBridge;
import riptide.util.RiptideNetworkCaptureState;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideOverlayManager;
import riptide.util.RiptidePayloadChannelSubscriptionManager;
import riptide.util.RiptidePayloadStudySession;
import riptide.util.RiptideSharedState;
import riptide.util.macro.MacroExecutor;
import riptide.util.macro.PacketGateManager;
import riptide.util.macro.PingSpoofController;
import riptide.util.multi.MultiManager;
import riptide.util.multi.PacketTeleportController;

public final class PackHideState {
   public static final String HIDE_ID = "hide";
   private static boolean silentOverride;
   private static volatile PackHideState.ActiveFlag activeFlag;

   private PackHideState() {
   }

   public static boolean isActive() {
      PackHideState.ActiveFlag flag = activeFlag;
      if (flag != null) {
         return flag.active;
      } else {
         RiptideConfig config = RiptideConfig.getGlobal();
         RiptideConfig.ModuleState state = config.modules.get("hide");
         boolean active = state != null && state.enabled;
         activeFlag = new PackHideState.ActiveFlag(active);
         return active;
      }
   }

   public static void publishRuntimeState(RiptideConfig config) {
      RiptideConfig.ModuleState state = config != null && config.modules != null ? config.modules.get("hide") : null;
      activeFlag = new PackHideState.ActiveFlag(state != null && state.enabled);
   }

   public static void refresh() {
      activeFlag = null;
   }

   public static boolean isSilenced() {
      return silentOverride || isActive();
   }

   public static boolean isHardLocked() {
      return isActive();
   }

   public static boolean shouldSuppressClientOutput() {
      return silentOverride || isHardLocked();
   }

   public static boolean isHideModule(Module module) {
      return module != null && "hide".equals(module.id());
   }

   public static boolean isHideModuleName(String idOrName) {
      if (idOrName != null && !idOrName.isBlank()) {
         String normalized = idOrName.toLowerCase(Locale.ROOT).replace(' ', '-').replace("_", "-");
         return "hide".equals(normalized) || "panic-mode".equals(normalized) || "panicmode".equals(normalized) || "panic".equals(normalized);
      } else {
         return false;
      }
   }

   public static boolean blocksEnable(Module module) {
      return isActive() && !isHideModule(module);
   }

   public static void enable(Module hideModule) {
      refresh();
      withSilence(() -> {
         enterHardLockCleanup();
         RiptideClientMessaging.clearClientMessages();
         RiptideConfig config = RiptideConfig.getGlobal();
         Set<String> enabled = new LinkedHashSet<>();

         for (Module module : ModuleRegistry.all()) {
            if (module != null && !isHideModule(module) && module.isEnabled()) {
               enabled.add(module.id());
            }
         }

         config.hideRestoreModules = new ArrayList<>(enabled);
         stopRuntimeWork();

         for (Module modulex : ModuleRegistry.all()) {
            if (modulex != null && !isHideModule(modulex) && modulex.isEnabled()) {
               modulex.setEnabledSilently(false);
            }
         }

         ModuleRenderUtil.refreshWorldRenderer();
         RiptideMeteorBridge.disableAndSave(config);
         RiptideEssentialBridge.disable(config);
         config.save();
      });
   }

   public static void disableAndRestore(Module hideModule) {
      refresh();
      withSilence(() -> {
         RiptideConfig config = RiptideConfig.getGlobal();
         List<String> restore = (List<String>)(config.hideRestoreModules == null ? List.of() : new ArrayList<>(config.hideRestoreModules));
         config.hideRestoreModules = new ArrayList<>();
         config.save();

         for (String id : restore) {
            Module module = ModuleRegistry.get(id);
            if (module != null && !isHideModule(module)) {
               module.setEnabledSilently(true);
            }
         }

         ModuleRenderUtil.refreshWorldRenderer();
         RiptideMeteorBridge.restore(config);
         RiptideEssentialBridge.restore(config);
         config.save();
         if (config.lanSyncEnabled) {
            RiptideLANSync.getInstance().start();
         }

         ModuleRegistry.clearKeyStates();
         RiptideInputClicker.clear();
      });
   }

   public static void enforceStartupHidden() {
      RiptideHideReset.onStartup();
   }

   private static void enterHardLockCleanup() {
      stopRuntimeWork();
      RiptideNotifications.clear();
      RiptideOverlayManager.get().hideAllInteractiveOverlays();
      RiptideInstaBreakRenderer.clear();
      RiptidePayloadStudySession.stop();
      RiptidePayloadChannelSubscriptionManager.clear();
      RiptideClientMessaging.clearClientMessages();
      RiptideClientMessaging.clearChatInputHistory();
      if (!RiptideLiteVariant.enabled()) {
         MultiManager.get().clearHistory();
      }
   }

   public static void stopRuntimeWork() {
      RiptideNetworkCaptureState.disable();
      ModuleRegistry.clearKeyStates();
      RiptideInputClicker.clear();
      RiptideLANSync.getInstance().stopSilently();
      if (MacroExecutor.isRunning()) {
         MacroExecutor.stop();
      }

      RiptideContainerHold.clearAll();
      PacketGateManager.clearAll();
      RiptideBlinkManager.disableAndFlush();
      RiptideKillAuraRotation.reset();
      PingSpoofController.clearAllMacros();
      PingSpoofController.clearModuleOverride();
      PacketTeleportController.cancelAll("panic mode");
      RiptideSharedState shared = RiptideSharedState.get();
      shared.setSendGuiPackets(true);
      shared.setDelayGuiPackets(false);
      shared.setStaggeredPacketSend(false);
      shared.setCaptureMode(false);
      shared.clearQueuedPackets();
      shared.setXCarryForcedTargets(Collections.emptySet(), false);
      shared.setXCarryForced(false);
      shared.setXCarryActive(false);
      shared.setSuppressNextContainerClosePacket(false);
   }

   private static void withSilence(Runnable action) {
      boolean previous = silentOverride;
      silentOverride = true;

      try {
         action.run();
      } finally {
         silentOverride = previous;
      }
   }

   private record ActiveFlag(boolean active) {
   }
}
