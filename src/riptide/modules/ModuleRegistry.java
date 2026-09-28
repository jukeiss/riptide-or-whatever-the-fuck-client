package riptide.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import riptide.addons.AddonManager;
import riptide.api.AddonRegistrationResult;
import riptide.api.module.Setting;
import riptide.util.RiptideBindUtil;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideConfig;
import riptide.util.RiptideEssentialBridge;
import riptide.util.RiptideInputGate;
import riptide.util.RiptidePerf;
import riptide.util.RiptideRuntimeActivity;
import riptide.util.macro.ToggleModuleAction;
import riptide.util.multi.PacketTeleportController;

public final class ModuleRegistry {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final Map<String, Module> MODULES = new LinkedHashMap<>();
   private static final Map<String, Boolean> KEY_STATES = new LinkedHashMap<>();
   private static final Map<String, Boolean> OFFLINE_ENABLE_BASELINES = new LinkedHashMap<>();
   private static final Map<String, Boolean> OFFLINE_ENABLE_TRANSITIONS = new LinkedHashMap<>();
   private static final Map<String, Set<String>> OFFLINE_SETTING_CHANGES = new LinkedHashMap<>();
   private static final Set<String> OFFLINE_SETTING_RESETS = new LinkedHashSet<>();
   private static final Map<ModuleCategory, List<Module>> CATEGORY_CACHE = new LinkedHashMap<>();
   private static int revision;
   private static int activeRevision;
   private static boolean initialized;
   private static boolean menuKeyDown;
   private static final ModuleRegistry.RevCache<List<Module>> ACTIVE_MODULES = new ModuleRegistry.RevCache<>(() -> activeRevision, () -> {
      List<Module> modules = new ArrayList<>();

      for (Module module : MODULES.values()) {
         if (module.isEnabled() && !Boolean.TRUE.equals(OFFLINE_ENABLE_TRANSITIONS.get(module.id()))) {
            modules.add(module);
         }
      }

      return Collections.unmodifiableList(modules);
   });
   private static final ModuleRegistry.RevCache<List<Module>> DISABLED_TICK_MODULES = new ModuleRegistry.RevCache<>(() -> revision, () -> {
      List<Module> modules = new ArrayList<>();

      for (Module module : MODULES.values()) {
         if (module.ticksWhenDisabled()) {
            modules.add(module);
         }
      }

      return Collections.unmodifiableList(modules);
   });
   private static final ModuleRegistry.RevCache<List<Module>> KEYBOUND_MODULES = new ModuleRegistry.RevCache<>(() -> revision, () -> {
      List<Module> modules = new ArrayList<>();

      for (Module module : MODULES.values()) {
         if (module.keybind() != -1) {
            modules.add(module);
         }
      }

      return Collections.unmodifiableList(modules);
   });
   private static final ModuleRegistry.RevCache<List<Module>> PACKET_SEND_MODULES = new ModuleRegistry.RevCache<>(
      () -> activeRevision, () -> activeOverrideModules("onPacketSend", Packet.class)
   );
   private static final ModuleRegistry.RevCache<List<Module>> PACKET_RECEIVE_MODULES = new ModuleRegistry.RevCache<>(
      () -> activeRevision, () -> activeOverrideModules("onPacketReceive", Packet.class)
   );
   private static final ModuleRegistry.RevCache<List<Module>> DISABLED_PACKET_RECEIVE_MODULES = new ModuleRegistry.RevCache<>(
      () -> revision, () -> disabledOverrideModules("onPacketReceive", Packet.class)
   );
   private static final ModuleRegistry.RevCache<List<Module>> SOUND_MODULES = new ModuleRegistry.RevCache<>(
      () -> activeRevision, () -> activeOverrideModules("onSoundPacket", ClientboundSoundPacket.class)
   );
   private static final ModuleRegistry.RevCache<List<Module>> RENDER_MODULES = new ModuleRegistry.RevCache<>(
      () -> activeRevision, () -> activeOverrideModules("onRenderLevel", float.class)
   );
   private static final ModuleRegistry.RevCache<List<Module>> BLOCK_BREAKING_PROGRESS_MODULES = new ModuleRegistry.RevCache<>(
      () -> activeRevision, () -> activeOverrideModules("onBlockBreakingProgress", BlockPos.class, Direction.class)
   );
   private static final ModuleRegistry.RevCache<List<Module>> START_BREAKING_MODULES = new ModuleRegistry.RevCache<>(
      () -> activeRevision,
      () -> {
         List<Module> modules = new ArrayList<>();

         for (Module module : activeModules()) {
            if (overridesModuleMethod(module, "shouldCancelStartBreakingBlock", BlockPos.class, Direction.class)
               || overridesModuleMethod(module, "onStartBreakingBlock", BlockPos.class, Direction.class)
               || overridesModuleMethod(module, "onStartDestroyBlock", BlockPos.class, Direction.class)) {
               modules.add(module);
            }
         }

         return Collections.unmodifiableList(modules);
      }
   );
   private static final ModuleRegistry.RevCache<List<Module>> PRE_MOVEMENT_MODULES = new ModuleRegistry.RevCache<>(
      () -> activeRevision, () -> activeOverrideModules("preMovementTick")
   );
   private static final ModuleRegistry.RevCache<List<Module>> NETWORK_MOVEMENT_MODULES = new ModuleRegistry.RevCache<>(
      () -> activeRevision, () -> activeOverrideModules("onNetworkMovementTickPre")
   );
   private static final ModuleRegistry.RevCache<List<Module>> PACKET_PROCESS_FRAME_MODULES = new ModuleRegistry.RevCache<>(
      () -> activeRevision, () -> activeOverrideModules("onPacketProcessFrame")
   );
   private static final ModuleRegistry.RevCache<List<Module>> PLAYER_TICK_CANCEL_MODULES = new ModuleRegistry.RevCache<>(
      () -> activeRevision, () -> activeOverrideModules("shouldCancelPlayerTick")
   );
   private static final ModuleRegistry.RevCache<List<Module>> PLAYER_MOVE_MODULES = new ModuleRegistry.RevCache<>(
      () -> activeRevision, () -> activeOverrideModules("onPlayerMove", MoverType.class, Vec3.class)
   );
   private static final ModuleRegistry.RevCache<List<Module>> MOUSE_ROTATION_MODULES = new ModuleRegistry.RevCache<>(
      () -> activeRevision, () -> activeOverrideModules("onMouseRotation", double.class, double.class)
   );
   private static final ModuleRegistry.RevCache<List<Module>> TOOLTIP_MODULES = new ModuleRegistry.RevCache<>(
      () -> activeRevision, () -> activeOverrideModules("appendTooltip", ItemStack.class, List.class)
   );
   private static final ModuleRegistry.RevCache<List<Module>> ATTACK_USE_MODULES = new ModuleRegistry.RevCache<>(
      () -> activeRevision,
      () -> {
         List<Module> modules = new ArrayList<>();

         for (Module module : activeModules()) {
            if (overridesModuleMethod(module, "shouldCancelAttack", HitResult.class)
               || overridesModuleMethod(module, "shouldCancelUse", HitResult.class, InteractionHand.class)) {
               modules.add(module);
            }
         }

         return Collections.unmodifiableList(modules);
      }
   );
   private static final ModuleRegistry.RevCache<Boolean> ACTIVE_PACKET_EVENT_MODULES = new ModuleRegistry.RevCache<>(
      () -> activeRevision, () -> !packetSendModules().isEmpty() || !packetReceiveModules().isEmpty()
   );
   private static final Set<String> MISS_CACHE = new HashSet<>();
   private static int missCacheRevision = Integer.MIN_VALUE;

   private ModuleRegistry() {
   }

   public static void initialize(RiptideConfig config) {
      if (!initialized) {
         BuiltinModules.register();
         NameCensorModule.refreshFastFlagsFromRegistry();
         ModuleWorldRenderer.initialize();
         initialized = true;
         ModuleRenderUtil.refreshFastFlags();
         RiptideRuntimeActivity.publishModuleRevision(revision);
         PackHideState.enforceStartupHidden();
         RiptideEssentialBridge.restoreIfOrphaned(config);
      }
   }

   static void register(Module module) {
      if (module != null) {
         if (module.id() != null && !module.id().isBlank()) {
            if (MODULES.containsKey(module.id())) {
               throw new IllegalStateException("Duplicate built-in module id: " + module.id());
            } else {
               MODULES.put(module.id(), module);
               invalidateCaches(true);
            }
         } else {
            throw new IllegalArgumentException("Built-in module id cannot be blank");
         }
      }
   }

   public static boolean registerAddonModule(Module module, String addonId) {
      return registerAddonModuleDetailed(module, addonId).accepted();
   }

   public static AddonRegistrationResult registerAddonModuleDetailed(Module module, String addonId) {
      if (module == null) {
         return AddonRegistrationResult.rejected("module", "", "module was null");
      } else if (addonId != null && !addonId.isBlank()) {
         String id = module.id();
         if (id == null || !id.contains(":")) {
            return rejectAddonModule(addonId, id, "non-namespaced id - scope it with RiptideAddons.id(...) so it becomes addonId:localId");
         } else if (!id.startsWith(addonId + ":")) {
            return rejectAddonModule(addonId, id, "foreign namespace - the id must start with your addon id; scope it with RiptideAddons.id(...)");
         } else if (MODULES.containsKey(id)) {
            return rejectAddonModule(addonId, id, "duplicate id");
         } else {
            module.markAddon();
            if (module.category() == null) {
               module.assignCategory(ModuleCategory.registerAddon(addonId, AddonManager.scopedCategoryLabel(null)));
            }

            MODULES.put(id, module);
            invalidateCaches(true);
            AddonManager.recordAcceptedRegistration("module", id);
            return AddonRegistrationResult.accepted("module", id);
         }
      } else {
         return rejectAddonModule(addonId, "", "registration outside an addon lifecycle - register from onInitialize() or onRegisterCategories()");
      }
   }

   private static AddonRegistrationResult rejectAddonModule(String addonId, String id, String reason) {
      riptide.RiptideClientAddon.LOG.warn("[Modules] Rejecting addon module '{}': {}", id, reason);
      AddonManager.recordRejectedRegistration(addonId, "module", id, reason);
      return AddonRegistrationResult.rejected("module", id, reason);
   }

   public static void unregisterAddonModules(String addonId) {
      if (addonId != null && !addonId.isBlank()) {
         boolean removed = false;
         Iterator<Entry<String, Module>> it = MODULES.entrySet().iterator();

         while (it.hasNext()) {
            Entry<String, Module> entry = it.next();
            Module module = entry.getValue();
            if (module != null && module.isAddon() && entry.getKey().startsWith(addonId + ":")) {
               try {
                  module.setEnabled(false);
               } catch (Throwable var6) {
               }

               it.remove();
               KEY_STATES.remove(entry.getKey());
               removed = true;
            }
         }

         if (removed) {
            invalidateCaches(true);
         }
      }
   }

   private static void runGuarded(Module module, String hook, Runnable body) {
      try {
         body.run();
      } catch (Throwable var4) {
         reportAddonModuleError(module, hook, var4);
      }
   }

   private static boolean runGuardedBool(Module module, String hook, BooleanSupplier body) {
      try {
         return body.getAsBoolean();
      } catch (Throwable var4) {
         reportAddonModuleError(module, hook, var4);
         return false;
      }
   }

   private static void reportAddonModuleError(Module module, String hook, Throwable t) {
      riptide.RiptideClientAddon.LOG.error("[Modules] Addon module '{}' threw in {}; disabling it.", new Object[]{module.id(), hook, t});
      String id = module.id();
      int colon = id == null ? -1 : id.indexOf(58);
      if (colon > 0) {
         AddonManager.recordRuntimeError(id.substring(0, colon), "Module " + id + " threw in " + hook);
      }

      try {
         module.setEnabled(false);
      } catch (Throwable var7) {
      }

      try {
         RiptideClientMessaging.sendPrefixed("§cAddon module '" + module.name() + "' errored and was disabled.");
      } catch (Throwable var6) {
      }
   }

   public static Collection<Module> all() {
      return Collections.unmodifiableCollection(MODULES.values());
   }

   public static List<Module> byCategory(ModuleCategory category) {
      if (category == null) {
         return List.of();
      } else {
         List<Module> cached = CATEGORY_CACHE.get(category);
         if (cached != null) {
            return cached;
         } else {
            List<Module> modules = new ArrayList<>();

            for (Module module : MODULES.values()) {
               if (module.category() == category && module.showInModuleMenu()) {
                  modules.add(module);
               }
            }

            List<Module> immutable = Collections.unmodifiableList(modules);
            CATEGORY_CACHE.put(category, immutable);
            return immutable;
         }
      }
   }

   public static Module get(String idOrName) {
      if (idOrName != null && !idOrName.isBlank()) {
         Module exact = MODULES.get(idOrName);
         if (exact != null) {
            return exact;
         } else {
            String needle = normalize(idOrName);
            Module direct = MODULES.get(needle);
            if (direct != null) {
               return direct;
            } else {
               if (missCacheRevision != revision) {
                  missCacheRevision = revision;
                  MISS_CACHE.clear();
               }

               if (MISS_CACHE.contains(idOrName)) {
                  return null;
               } else {
                  for (Module module : MODULES.values()) {
                     if (normalize(module.name()).equals(needle)) {
                        return module;
                     }
                  }

                  String squashed = squash(idOrName);

                  for (Module modulex : MODULES.values()) {
                     if (squash(modulex.name()).equals(squashed) || squash(modulex.id()).equals(squashed)) {
                        return modulex;
                     }
                  }

                  MISS_CACHE.add(idOrName);
                  return null;
               }
            }
         }
      } else {
         return null;
      }
   }

   private static String squash(String value) {
      return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
   }

   public static boolean toggle(String idOrName, ToggleModuleAction.ToggleMode mode) {
      Module module = get(idOrName);
      if (module == null) {
         return false;
      } else {
         ToggleModuleAction.ToggleMode resolvedMode = mode == null ? ToggleModuleAction.ToggleMode.TOGGLE : mode;
         if (!PackHideState.isActive()
            || PackHideState.isHideModule(module)
            || resolvedMode != ToggleModuleAction.ToggleMode.ENABLE && resolvedMode != ToggleModuleAction.ToggleMode.TOGGLE) {
            switch (resolvedMode) {
               case ENABLE:
                  module.setEnabled(true);
                  break;
               case DISABLE:
                  module.setEnabled(false);
                  break;
               default:
                  module.toggle();
            }

            return true;
         } else {
            return false;
         }
      }
   }

   public static List<String> names() {
      List<String> names = new ArrayList<>();

      for (Module module : MODULES.values()) {
         if (module.showInModuleMenu()) {
            names.add(module.name());
         }
      }

      return names;
   }

   public static List<Module> activeModules() {
      return ACTIVE_MODULES.get();
   }

   public static boolean hasActiveModules() {
      return !activeModules().isEmpty();
   }

   public static boolean isModuleEnabled(String idOrName) {
      Module module = get(idOrName);
      return module != null && module.isEnabled();
   }

   public static boolean hasActivePacketEventModules() {
      return ACTIVE_PACKET_EVENT_MODULES.get();
   }

   private static List<Module> disabledTickModules() {
      return DISABLED_TICK_MODULES.get();
   }

   private static List<Module> keyboundModules() {
      return KEYBOUND_MODULES.get();
   }

   private static List<Module> activeOverrideModules(String methodName, Class<?>... parameterTypes) {
      List<Module> modules = new ArrayList<>();

      for (Module module : activeModules()) {
         if (overridesModuleMethod(module, methodName, parameterTypes)) {
            modules.add(module);
         }
      }

      return Collections.unmodifiableList(modules);
   }

   private static List<Module> disabledOverrideModules(String methodName, Class<?>... parameterTypes) {
      List<Module> modules = new ArrayList<>();

      for (Module module : disabledTickModules()) {
         if (!module.isEnabled() && overridesModuleMethod(module, methodName, parameterTypes)) {
            modules.add(module);
         }
      }

      return Collections.unmodifiableList(modules);
   }

   private static List<Module> packetSendModules() {
      return PACKET_SEND_MODULES.get();
   }

   private static List<Module> packetReceiveModules() {
      return PACKET_RECEIVE_MODULES.get();
   }

   private static List<Module> disabledPacketReceiveModules() {
      return DISABLED_PACKET_RECEIVE_MODULES.get();
   }

   private static List<Module> soundModules() {
      return SOUND_MODULES.get();
   }

   private static List<Module> renderModules() {
      return RENDER_MODULES.get();
   }

   private static List<Module> blockBreakingProgressModules() {
      return BLOCK_BREAKING_PROGRESS_MODULES.get();
   }

   private static List<Module> startBreakingModules() {
      return START_BREAKING_MODULES.get();
   }

   private static List<Module> preMovementModules() {
      return PRE_MOVEMENT_MODULES.get();
   }

   private static List<Module> networkMovementModules() {
      return NETWORK_MOVEMENT_MODULES.get();
   }

   private static List<Module> packetProcessFrameModules() {
      return PACKET_PROCESS_FRAME_MODULES.get();
   }

   private static List<Module> playerTickCancelModules() {
      return PLAYER_TICK_CANCEL_MODULES.get();
   }

   private static List<Module> playerMoveModules() {
      return PLAYER_MOVE_MODULES.get();
   }

   private static List<Module> mouseRotationModules() {
      return MOUSE_ROTATION_MODULES.get();
   }

   private static List<Module> tooltipModules() {
      return TOOLTIP_MODULES.get();
   }

   private static List<Module> attackUseModules() {
      return ATTACK_USE_MODULES.get();
   }

   private static boolean hasDisabledTickWorkModules() {
      for (Module module : disabledTickModules()) {
         if (!module.isEnabled() && module.hasDisabledTickWork()) {
            return true;
         }
      }

      return false;
   }

   public static boolean hasTickWork() {
      return initialized && MC != null ? !keyboundModules().isEmpty() || !activeModules().isEmpty() || hasDisabledTickWorkModules() : false;
   }

   public static boolean hasPreMovementHooks() {
      return !preMovementModules().isEmpty();
   }

   public static boolean hasMovementHooks() {
      return !preMovementModules().isEmpty() || !playerMoveModules().isEmpty();
   }

   public static boolean hasMouseRotationHooks() {
      return !mouseRotationModules().isEmpty();
   }

   public static boolean hasRenderLevelHooks() {
      return !renderModules().isEmpty();
   }

   public static boolean hasTooltipHooks() {
      return !tooltipModules().isEmpty();
   }

   public static boolean hasAttackUseHooks() {
      return !attackUseModules().isEmpty();
   }

   public static boolean hasSoundHooks() {
      return !soundModules().isEmpty();
   }

   public static boolean hasBlockBreakingProgressHooks() {
      return !blockBreakingProgressModules().isEmpty();
   }

   public static boolean hasStartBreakingHooks() {
      return !startBreakingModules().isEmpty();
   }

   static void markModuleSettingsChanged() {
      invalidateCaches(false);
   }

   static void markModuleEnabledChanged() {
      invalidateCaches(true);
   }

   static void fireEnableTransition(Module module, boolean enabled) {
      if (module != null) {
         if (module.isAddon()) {
            runGuarded(module, enabled ? "onEnable" : "onDisable", enabled ? module::onEnable : module::onDisable);
         } else if (enabled) {
            module.onEnable();
         } else {
            module.onDisable();
         }
      }
   }

   static void clearOfflineDeferrals() {
      OFFLINE_ENABLE_BASELINES.clear();
      OFFLINE_ENABLE_TRANSITIONS.clear();
      OFFLINE_SETTING_CHANGES.clear();
      OFFLINE_SETTING_RESETS.clear();
   }

   static void refreshEnabledModuleSettings() {
      for (Module module : MODULES.values()) {
         if (module.isEnabled()) {
            Set<String> ids = new LinkedHashSet<>();

            for (Setting<?, ?> setting : module.settings()) {
               ids.add(setting.id());
            }

            module.applyConfiguredSettings(ids, false);
         }
      }
   }

   static void recordOfflineConfiguredState(Module module, boolean oldEnabled, boolean newEnabled) {
      if (module != null) {
         String id = module.id();
         boolean baseline = OFFLINE_ENABLE_BASELINES.computeIfAbsent(id, ignored -> oldEnabled);
         if (newEnabled == baseline) {
            OFFLINE_ENABLE_BASELINES.remove(id);
            OFFLINE_ENABLE_TRANSITIONS.remove(id);
         } else {
            OFFLINE_ENABLE_TRANSITIONS.put(id, newEnabled);
         }
      }
   }

   static void recordOfflineConfiguredOption(Module module, String optionId, boolean reset) {
      if (module != null) {
         if (reset) {
            OFFLINE_SETTING_RESETS.add(module.id());
            OFFLINE_SETTING_CHANGES.remove(module.id());
         } else if (optionId != null && !optionId.isBlank()) {
            OFFLINE_SETTING_CHANGES.computeIfAbsent(module.id(), ignored -> new LinkedHashSet<>()).add(optionId);
         }
      }
   }

   public static int revision() {
      return revision;
   }

   public static int activeRevision() {
      return activeRevision;
   }

   private static void invalidateCaches(boolean activeChanged) {
      revision++;
      CATEGORY_CACHE.clear();
      DISABLED_TICK_MODULES.invalidate();
      KEYBOUND_MODULES.invalidate();
      DISABLED_PACKET_RECEIVE_MODULES.invalidate();
      if (activeChanged) {
         activeRevision++;
         ACTIVE_MODULES.invalidate();
         ACTIVE_PACKET_EVENT_MODULES.invalidate();
         PACKET_SEND_MODULES.invalidate();
         PACKET_RECEIVE_MODULES.invalidate();
         SOUND_MODULES.invalidate();
         RENDER_MODULES.invalidate();
         BLOCK_BREAKING_PROGRESS_MODULES.invalidate();
         START_BREAKING_MODULES.invalidate();
         PRE_MOVEMENT_MODULES.invalidate();
         NETWORK_MOVEMENT_MODULES.invalidate();
         PACKET_PROCESS_FRAME_MODULES.invalidate();
         PLAYER_TICK_CANCEL_MODULES.invalidate();
         PLAYER_MOVE_MODULES.invalidate();
         MOUSE_ROTATION_MODULES.invalidate();
         TOOLTIP_MODULES.invalidate();
         ATTACK_USE_MODULES.invalidate();
      }

      if (initialized) {
         ModuleRenderUtil.refreshFastFlags();
         RiptideRuntimeActivity.publishModuleRevision(revision);
      }
   }

   public static void tick() {
      if (initialized && MC != null && !PackHideState.isActive()) {
         if (hasTickWork()) {
            if (!keyboundModules().isEmpty()) {
               tickModuleKeybinds();
            }

            boolean profileJoin = RiptidePerf.isJoinWindowActive();

            for (Module module : activeModules()) {
               if (!PacketTeleportController.ownsMainMovement() || !isPacedTeleportMovementModule(module)) {
                  if (profileJoin) {
                     long perf = RiptidePerf.beginJoin();
                     tickModule(module);
                     RiptidePerf.endJoinSpike("join.module.tick." + module.id(), perf);
                  } else {
                     tickModule(module);
                  }
               }
            }

            for (Module modulex : disabledTickModules()) {
               if (!modulex.isEnabled() && modulex.hasDisabledTickWork()) {
                  if (profileJoin) {
                     long perf = RiptidePerf.beginJoin();
                     tickModule(modulex);
                     RiptidePerf.endJoinSpike("join.module.tick." + modulex.id(), perf);
                  } else {
                     tickModule(modulex);
                  }
               }
            }
         }
      }
   }

   private static void tickModule(Module module) {
      if (module.isAddon()) {
         runGuarded(module, "tick", module::tick);
      } else {
         module.tick();
      }
   }

   private static boolean isPacedTeleportMovementModule(Module module) {
      if (module == null) {
         return false;
      } else {
         String var1 = module.id();

         return switch (var1) {
            case "flight", "speed", "sprint", "sneak", "no-fall", "boat-fly", "entity-control" -> true;
            default -> false;
         };
      }
   }

   public static boolean sneakHoldsShift() {
      return get("sneak") instanceof BuiltinModules.SneakModule sneak && sneak.holdsSneakDown();
   }

   public static void preMovementTick() {
      if (initialized && MC != null && !PackHideState.isActive()) {
         for (Module module : preMovementModules()) {
            if (module.isAddon()) {
               runGuarded(module, "preMovementTick", module::preMovementTick);
            } else {
               module.preMovementTick();
            }
         }
      }
   }

   public static void onNetworkMovementTickPre() {
      if (initialized && MC != null && !PackHideState.isActive()) {
         for (Module module : networkMovementModules()) {
            if (module.isAddon()) {
               runGuarded(module, "onNetworkMovementTickPre", module::onNetworkMovementTickPre);
            } else {
               module.onNetworkMovementTickPre();
            }
         }
      }
   }

   public static void onPacketProcessFrame() {
      if (initialized && MC != null && !PackHideState.isActive()) {
         for (Module module : packetProcessFrameModules()) {
            if (module.isAddon()) {
               runGuarded(module, "onPacketProcessFrame", module::onPacketProcessFrame);
            } else {
               module.onPacketProcessFrame();
            }
         }
      }
   }

   public static boolean shouldCancelPlayerTick() {
      if (initialized && MC != null && !PackHideState.isActive()) {
         for (Module module : playerTickCancelModules()) {
            boolean cancel = module.isAddon()
               ? runGuardedBool(module, "shouldCancelPlayerTick", module::shouldCancelPlayerTick)
               : module.shouldCancelPlayerTick();
            if (cancel) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static void onRenderLevel(float partialTick) {
      if (initialized && MC != null && MC.level != null && MC.player != null && MC.getConnection() != null && !PackHideState.isActive()) {
         if (hasRenderLevelHooks()) {
            for (Module module : renderModules()) {
               if (module.isAddon()) {
                  runGuarded(module, "onRenderLevel", () -> module.onRenderLevel(partialTick));
               } else {
                  module.onRenderLevel(partialTick);
               }
            }
         }
      }
   }

   public static void onMouseRotation(double deltaYaw, double deltaPitch) {
      if (initialized && MC != null && !PackHideState.isActive()) {
         for (Module module : mouseRotationModules()) {
            if (module.isAddon()) {
               runGuarded(module, "onMouseRotation", () -> module.onMouseRotation(deltaYaw, deltaPitch));
            } else {
               module.onMouseRotation(deltaYaw, deltaPitch);
            }
         }
      }
   }

   public static Vec3 onPlayerMove(MoverType type, Vec3 movement) {
      if (initialized && MC != null && movement != null) {
         if (PackHideState.isActive()) {
            return movement;
         } else {
            Vec3 adjusted = movement;

            for (Module module : playerMoveModules()) {
               if (module.isAddon()) {
                  Vec3 in = adjusted;
                  adjusted = callGuarded(module, "onPlayerMove", () -> module.onPlayerMove(type, in), in);
               } else {
                  adjusted = module.onPlayerMove(type, adjusted);
               }
            }

            return adjusted;
         }
      } else {
         return movement;
      }
   }

   private static <T> T callGuarded(Module module, String hook, Supplier<T> body, T fallback) {
      try {
         return body.get();
      } catch (Throwable var5) {
         reportAddonModuleError(module, hook, var5);
         return fallback;
      }
   }

   public static boolean tickMenuKey(int keyCode) {
      if (MC != null && MC.getWindow() != null && keyCode != -1) {
         boolean pressed = RiptideBindUtil.isBindPressed(MC, keyCode);
         boolean justPressed = pressed && !menuKeyDown;
         menuKeyDown = pressed;
         return justPressed;
      } else {
         return false;
      }
   }

   public static void onGameJoin() {
      applyOfflineConfiguredTransitions();
      boolean profileJoin = RiptidePerf.isJoinWindowActive();

      for (Module module : MODULES.values()) {
         if (module.isEnabled() || module.ticksWhenDisabled()) {
            if (profileJoin) {
               long perf = RiptidePerf.beginJoin();
               joinModule(module);
               RiptidePerf.endJoinSpike("join.module.onGameJoin." + module.id(), perf);
            } else {
               joinModule(module);
            }
         }
      }
   }

   private static void joinModule(Module module) {
      if (module.isAddon()) {
         runGuarded(module, "onGameJoin", module::onGameJoin);
      } else {
         module.onGameJoin();
      }
   }

   public static void onGameLeft() {
      for (Module module : MODULES.values()) {
         if (module.isEnabled() || module.ticksWhenDisabled()) {
            if (module.isAddon()) {
               runGuarded(module, "onGameLeft", module::onGameLeft);
            } else {
               module.onGameLeft();
            }
         }
      }

      KEY_STATES.clear();
      menuKeyDown = false;
   }

   public static boolean onPacketSend(Packet<?> packet) {
      if (PackHideState.isActive()) {
         return false;
      } else if (!hasActivePacketEventModules()) {
         return false;
      } else {
         for (Module module : packetSendModules()) {
            boolean cancel = module.isAddon() ? runGuardedBool(module, "onPacketSend", () -> module.onPacketSend(packet)) : module.onPacketSend(packet);
            if (cancel) {
               return true;
            }
         }

         return false;
      }
   }

   public static boolean onPacketReceive(Packet<?> packet) {
      if (PackHideState.isActive()) {
         return false;
      } else if (!hasActivePacketEventModules() && disabledPacketReceiveModules().isEmpty()) {
         return false;
      } else {
         for (Module module : packetReceiveModules()) {
            boolean cancel = module.isAddon()
               ? runGuardedBool(module, "onPacketReceive", () -> module.onPacketReceive(packet))
               : module.onPacketReceive(packet);
            if (cancel) {
               return true;
            }
         }

         for (Module modulex : disabledPacketReceiveModules()) {
            boolean cancel = modulex.isAddon()
               ? runGuardedBool(modulex, "onPacketReceive", () -> module.onPacketReceive(packet))
               : modulex.onPacketReceive(packet);
            if (cancel) {
               return true;
            }
         }

         return false;
      }
   }

   public static void onSoundPacket(ClientboundSoundPacket packet) {
      if (initialized && !PackHideState.isActive()) {
         if (hasSoundHooks()) {
            for (Module module : soundModules()) {
               if (module.isAddon()) {
                  runGuarded(module, "onSoundPacket", () -> module.onSoundPacket(packet));
               } else {
                  module.onSoundPacket(packet);
               }
            }
         }
      }
   }

   public static void onBlockBreakingProgress(BlockPos pos, Direction direction) {
      if (initialized && !PackHideState.isActive()) {
         if (hasBlockBreakingProgressHooks()) {
            for (Module module : blockBreakingProgressModules()) {
               if (module.isAddon()) {
                  runGuarded(module, "onBlockBreakingProgress", () -> module.onBlockBreakingProgress(pos, direction));
               } else {
                  module.onBlockBreakingProgress(pos, direction);
               }
            }
         }
      }
   }

   private static void applyOfflineConfiguredTransitions() {
      if (!OFFLINE_ENABLE_TRANSITIONS.isEmpty() || !OFFLINE_SETTING_CHANGES.isEmpty() || !OFFLINE_SETTING_RESETS.isEmpty()) {
         Map<String, Boolean> transitions = new LinkedHashMap<>(OFFLINE_ENABLE_TRANSITIONS);
         Map<String, Set<String>> settingChanges = new LinkedHashMap<>();
         OFFLINE_SETTING_CHANGES.forEach((id, values) -> settingChanges.put(id, new LinkedHashSet<>(values)));
         Set<String> settingResets = new LinkedHashSet<>(OFFLINE_SETTING_RESETS);
         OFFLINE_ENABLE_TRANSITIONS.clear();
         OFFLINE_ENABLE_BASELINES.clear();
         OFFLINE_SETTING_CHANGES.clear();
         OFFLINE_SETTING_RESETS.clear();

         for (Module module : MODULES.values()) {
            if (module.isEnabled()
               && !Boolean.TRUE.equals(transitions.get(module.id()))
               && (settingResets.contains(module.id()) || settingChanges.containsKey(module.id()))) {
               try {
                  module.applyConfiguredSettings(settingChanges.getOrDefault(module.id(), Set.of()), settingResets.contains(module.id()));
               } catch (Throwable var8) {
                  if (module.isAddon()) {
                     reportAddonModuleError(module, "deferred settings", var8);
                  } else {
                     riptide.RiptideClientAddon.LOG.error("[Modules] Deferred title-screen settings failed for '{}'.", module.id(), var8);
                  }
               }
            }
         }

         for (Entry<String, Boolean> entry : transitions.entrySet()) {
            Module modulex = MODULES.get(entry.getKey());
            if (modulex != null && modulex.isEnabled() == entry.getValue()) {
               try {
                  if (entry.getValue()) {
                     modulex.onEnable();
                  } else {
                     modulex.onDisable();
                  }
               } catch (Throwable var7) {
                  if (modulex.isAddon()) {
                     reportAddonModuleError(modulex, entry.getValue() ? "deferred onEnable" : "deferred onDisable", var7);
                  } else {
                     riptide.RiptideClientAddon.LOG.error("[Modules] Deferred title-screen transition failed for '{}'.", modulex.id(), var7);
                  }
               }
            }
         }

         invalidateCaches(true);
      }
   }

   public static float modifyBlockDestroyProgress(float original, BlockPos pos) {
      if (initialized && !PackHideState.isActive()) {
         if (get("fast-break") instanceof BuiltinModules.FastBreakModule fastBreak
            && fastBreak.isEnabled()
            && fastBreak.usesNormalDestroyModifier()
            && MC != null
            && MC.level != null) {
            BlockState state = pos == null ? null : MC.level.getBlockState(pos);
            return fastBreak.modifyNormalDestroyProgress(original, state, pos);
         } else {
            return original;
         }
      } else {
         return original;
      }
   }

   public static boolean onStartDestroyBlock(BlockPos pos, Direction direction) {
      if (initialized && !PackHideState.isActive()) {
         if (!hasStartBreakingHooks()) {
            return false;
         } else {
            for (Module module : startBreakingModules()) {
               boolean handled = module.isAddon()
                  ? runGuardedBool(module, "onStartDestroyBlock", () -> module.onStartDestroyBlock(pos, direction))
                  : module.onStartDestroyBlock(pos, direction);
               if (handled) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean dispatchStartBreakingBlock(BlockPos pos, Direction direction) {
      if (initialized && !PackHideState.isHardLocked() && hasStartBreakingHooks()) {
         for (Module module : startBreakingModules()) {
            boolean cancel = module.isAddon()
               ? runGuardedBool(module, "shouldCancelStartBreakingBlock", () -> module.shouldCancelStartBreakingBlock(pos, direction))
               : module.shouldCancelStartBreakingBlock(pos, direction);
            if (cancel) {
               return true;
            }

            if (module.isAddon()) {
               runGuarded(module, "onStartBreakingBlock", () -> module.onStartBreakingBlock(pos, direction));
            } else {
               module.onStartBreakingBlock(pos, direction);
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static boolean shouldCancelAttack(HitResult hitResult) {
      if (initialized && !PackHideState.isHardLocked() && hasAttackUseHooks()) {
         for (Module module : attackUseModules()) {
            boolean cancel = module.isAddon()
               ? runGuardedBool(module, "shouldCancelAttack", () -> module.shouldCancelAttack(hitResult))
               : module.shouldCancelAttack(hitResult);
            if (cancel) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static boolean shouldCancelUse(HitResult hitResult, InteractionHand hand) {
      return shouldCancelUseExcept(hitResult, hand, null);
   }

   public static boolean shouldCancelUseExcept(HitResult hitResult, InteractionHand hand, String excludedModuleId) {
      if (initialized && !PackHideState.isHardLocked() && hasAttackUseHooks()) {
         for (Module module : attackUseModules()) {
            if (excludedModuleId == null || !excludedModuleId.equals(module.id())) {
               boolean cancel = module.isAddon()
                  ? runGuardedBool(module, "shouldCancelUse", () -> module.shouldCancelUse(hitResult, hand))
                  : module.shouldCancelUse(hitResult, hand);
               if (cancel) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static void appendTooltip(ItemStack stack, List<?> lines) {
      if (initialized && !PackHideState.isHardLocked() && hasTooltipHooks()) {
         for (Module module : tooltipModules()) {
            if (module.isAddon()) {
               runGuarded(module, "appendTooltip", () -> module.appendTooltip(stack, lines));
            } else {
               module.appendTooltip(stack, lines);
            }
         }
      }
   }

   private static void tickModuleKeybinds() {
      if (PackHideState.isActive()) {
         KEY_STATES.clear();
      } else if (keyboundModules().isEmpty()) {
         KEY_STATES.clear();
      } else {
         for (Module module : keyboundModules()) {
            int bind = module.keybind();
            boolean pressed = RiptideBindUtil.isBindPressed(MC, bind);
            boolean wasPressed = KEY_STATES.getOrDefault(module.id(), false);
            if (module.holdToActivate()) {
               if (pressed != wasPressed && RiptideInputGate.canRunRiptideKeybinds() && module.hasActivationToggle() && module.isEnabled() != pressed) {
                  module.setEnabled(pressed);
               }
            } else if (pressed && !wasPressed && RiptideInputGate.canRunRiptideKeybinds() && module.hasActivationToggle()) {
               module.toggle();
            }

            KEY_STATES.put(module.id(), pressed);
         }
      }
   }

   private static boolean overridesPacketEvents(Module module) {
      return module == null
         ? false
         : overridesModuleMethod(module, "onPacketSend", Packet.class) || overridesModuleMethod(module, "onPacketReceive", Packet.class);
   }

   public static List<Module> startBreakingModulesForDispatch() {
      return startBreakingModules();
   }

   public static List<Module> attackUseModulesForDispatch() {
      return attackUseModules();
   }

   public static List<Module> tooltipModulesForDispatch() {
      return tooltipModules();
   }

   private static boolean overridesModuleMethod(Module module, String methodName, Class<?>... parameterTypes) {
      if (module != null && methodName != null && !methodName.isBlank()) {
         try {
            return module.getClass().getMethod(methodName, parameterTypes).getDeclaringClass() != Module.class;
         } catch (NoSuchMethodException var4) {
            return false;
         }
      } else {
         return false;
      }
   }

   public static void clearKeyStates() {
      KEY_STATES.clear();
      menuKeyDown = false;
   }

   private static String normalize(String value) {
      return value.toLowerCase(Locale.ROOT).replace(' ', '-').replace("_", "-");
   }

   private static final class RevCache<T> {
      private final IntSupplier currentRevision;
      private final Supplier<T> builder;
      private T value;
      private int cachedRevision = -1;

      RevCache(IntSupplier currentRevision, Supplier<T> builder) {
         this.currentRevision = currentRevision;
         this.builder = builder;
      }

      T get() {
         int rev = this.currentRevision.getAsInt();
         if (this.cachedRevision == rev) {
            return this.value;
         } else {
            this.value = this.builder.get();
            this.cachedRevision = rev;
            return this.value;
         }
      }

      void invalidate() {
         this.cachedRevision = -1;
         this.value = null;
      }
   }
}
