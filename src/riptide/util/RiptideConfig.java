package riptide.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import riptide.modules.PackHideState;
import riptide.security.RiptideProtector;

public final class RiptideConfig implements Cloneable {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static RiptideConfig globalInstance;
   public static Runnable afterSave;
   static Consumer<RiptideConfig> afterPersistenceSnapshot;
   public transient boolean sendGuiPackets = true;
   public transient boolean delayGuiPackets = false;
   public boolean useCustomPackets = false;
   public List<String> c2sPackets = new ArrayList<>();
   public List<String> s2cPackets = new ArrayList<>();
   public List<String> packetLoggerBlocked = new ArrayList<>();
   public boolean packetLoggerBlockedInit = false;
   public int packetLoggerBlockedDefaultsVersion = 0;
   public List<RiptideConfig.PayloadChannelFilterRule> packetLoggerPayloadFilters = new ArrayList<>();
   public List<RiptideConfig.PayloadChannelRegistrationRule> packetLoggerPayloadRegistrations = new ArrayList<>();
   @Deprecated
   public List<RiptideConfig.PayloadChannelListenerRule> packetLoggerPayloadListeners = new ArrayList<>();
   public boolean payloadRegistrationUnlocked = false;
   public int payloadRegistrationWarningAcceptedVersion = 0;
   public boolean packetLoggerCapturing = false;
   public boolean allowSignEditing = true;
   public boolean autoDenyResourcePack = false;
   public boolean pretendPackAccepted = false;
   public int packResponseDelayMs = 20000;
   public boolean resourcePackChoiceInitialized = false;
   public boolean spoofClientVanilla = true;
   public boolean protectorEnabled = true;
   public boolean protectorSpoofBrand = true;
   public boolean protectorFilterChannels = true;
   public boolean protectorTranslationProtection = true;
   public boolean protectorDisableTelemetry = true;
   public boolean protectorBlockLocalUrls = true;
   public boolean protectorIsolatePackCache = true;
   public boolean protectorStripServerPacks = false;
   public boolean protectorChatSigningOff = false;
   public boolean performanceDebug = false;
   public boolean inventoryMove = false;
   public boolean xCarry = true;
   public boolean noPauseOnLostFocus = true;
   public boolean showItemIds = true;
   public boolean autoProbePlugins = false;
   public transient Map<String, RiptideConfig.PluginScanCacheEntry> serverPluginScans = new LinkedHashMap<>();
   public boolean lanSyncEnabled = true;
   public boolean staggeredPacketSend = false;
   public int staggeredSendDelay = 1;
   public String executionPreset = "DEFAULT";
   public boolean packetBurstMode = true;
   public boolean useMsSleepMode = false;
   public int msSleepInterval = 5;
   public boolean instantExecutionMode = true;
   public int actionDelayUs = 0;
   public boolean useDirectFlush = true;
   public boolean forceChannelFlush = true;
   public boolean flushQueueOnDelayDisable = true;
   public boolean captureAsExact = false;
   public String commandPrefix = "";
   public boolean joinMacroEnabled = false;
   public String joinMacroName = "";
   public String joinMacroTiming = "WORLD";
   public String joinMacroTriggerJoin = "FIRST";
   public boolean joinMacroKeepEnabled = false;
   public boolean stopMacroOnLeave = true;
   public Map<String, String> joinMacroFormValues = new LinkedHashMap<>();
   public Map<Integer, String> commandBinds = new LinkedHashMap<>();
   public Map<String, RiptideConfig.ModuleState> modules = new LinkedHashMap<>();
   public List<String> hideRestoreModules = new ArrayList<>();
   public Map<String, RiptideConfig.ModuleCategoryLayout> moduleCategoryLayouts = new LinkedHashMap<>();
   public Map<String, List<String>> moduleCategoryOrder = new LinkedHashMap<>();
   public Map<String, RiptideConfig.HudElementState> hudElements = new LinkedHashMap<>();
   public boolean hudLayoutMigrated = false;
   public boolean hudLayoutNormalizedV2 = false;
   public int hudSnapRange = 10;
   public int hudEdgePadding = 4;
   public boolean hudEditorGrid = true;
   public int keybindLoadGui = 86;
   public int keybindModuleMenu = 344;
   public int keybindFlushQueue = -1;
   public int keybindClearQueue = -1;
   public int keybindToggleLogger = -1;
   public int keybindToggleSend = -1;
   public int keybindToggleDelay = -1;
   public boolean keybindInsideGui = false;
   public boolean customMainMenu = true;
   public boolean infiniChat = true;
   public double overlayScale = 0.85;
   public int tpMaxPackets = 20;
   public int tpPauseMs = 500;
   public boolean welcomeShown = false;
   public String welcomeInstallIdentity = "";
   public boolean voiceChatModdedPromptShown = false;
   public boolean multiDisclaimerAccepted = false;
   public boolean multiShowTooltips = true;
   public boolean multiAutoSolveCaptcha = true;
   public int multiMacroStartDelayMs = 0;
   public boolean accountGenSetPassword = true;
   public String accountGenPasswordMode = "Generate";
   public String accountGenSharedPassword = "";
   public String activeProfileId = "";
   public List<String> hideRestoreMeteorModules = new ArrayList<>();
   public boolean hideMeteorHudActive = true;
   public boolean essentialHiddenByPanic = false;
   public boolean essentialSavedEnabled = true;
   public List<String> disabledAddonIds = new ArrayList<>();
   public RiptideConfig.ThemeColors themeColors = new RiptideConfig.ThemeColors();
   private static final Object SAVE_LOCK = new Object();

   public static RiptideConfig getGlobal() {
      if (globalInstance == null) {
         globalInstance = load();
         RiptidePerf.publishConfigState(globalInstance);
         PackHideState.publishRuntimeState(globalInstance);
         RiptideProtector.publishRuntimeState(globalInstance);
      }

      return globalInstance;
   }

   public static void setGlobal(RiptideConfig var0) {
      globalInstance = var0;
      RiptidePerf.publishConfigState(var0);
      PackHideState.publishRuntimeState(var0);
      RiptideProtector.publishRuntimeState(var0);
   }

   public static RiptideConfig load() {
      File var0 = configFile();
      RiptideConfig var1 = null;
      if (var0.exists()) {
         var1 = tryRead(var0);
         if (var1 == null) {
            backupCorruptConfig();
            File var2 = backupFile();
            if (var2.exists()) {
               var1 = tryRead(var2);
               if (var1 != null) {
                  riptide.RiptideClientAddon.LOG.warn("Riptide config was corrupt; recovered from backup {}", var2.getName());
               }
            }
         }
      }

      if (var1 == null) {
         var1 = new RiptideConfig();
      }

      Map var5 = var1.serverPluginScans;
      ServerPluginScanCache var3 = ServerPluginScanCache.get();
      boolean var4 = var3.mergeLegacy(var5);
      var1.serverPluginScans = var3.sharedView();
      var1.applyRuntimeDefaults();
      if (var4) {
         var1.save();
      }

      return var1;
   }

   private static RiptideConfig tryRead(File var0) {
      try {
         RiptideConfig var9;
         try (FileReader var1 = new FileReader(var0)) {
            JsonElement var2 = JsonParser.parseReader(var1);
            RiptideConfig var3 = (RiptideConfig)GSON.fromJson(var2, RiptideConfig.class);
            if (var3 != null && var2 instanceof JsonObject var4 && var4.has("serverPluginScans")) {
               Map var5 = (Map)GSON.fromJson(var4.get("serverPluginScans"), (new TypeToken<Map<String, RiptideConfig.PluginScanCacheEntry>>() {}).getType());
               var3.serverPluginScans = (Map<String, RiptideConfig.PluginScanCacheEntry>)(var5 == null ? new LinkedHashMap<>() : var5);
            }

            var9 = var3 != null ? var3 : new RiptideConfig();
         }

         return var9;
      } catch (Throwable var8) {
         riptide.RiptideClientAddon.LOG.error("Failed to read Riptide config from {}", var0.getName(), var8);
         return null;
      }
   }

   private static File backupFile() {
      return new File(configFile().getParentFile(), "config.json.bak");
   }

   static File configFile() {
      return new File(riptide.RiptideClientAddon.FOLDER, "config.json");
   }

   private static void backupCorruptConfig() {
      File var0 = configFile();

      try {
         File var1 = new File(var0.getParentFile(), "config.json.corrupt-" + System.currentTimeMillis());
         Files.move(var0.toPath(), var1.toPath(), StandardCopyOption.REPLACE_EXISTING);
         riptide.RiptideClientAddon.LOG.error("Riptide config was corrupt; moved it to {}", var1.getName());
      } catch (Throwable var2) {
         riptide.RiptideClientAddon.LOG.error("Failed to set aside corrupt Riptide config", var2);
      }
   }

   public void save() {
      RiptidePerf.publishConfigState(this);
      PackHideState.publishRuntimeState(this);
      RiptideProtector.publishRuntimeState(this);
      RiptideConfigWriter.request(this);
      Runnable var1 = afterSave;
      if (var1 != null) {
         try {
            var1.run();
         } catch (Throwable var3) {
            riptide.RiptideClientAddon.LOG.error("Riptide config afterSave hook failed", var3);
         }
      }
   }

   public static void enqueuePendingSaveNow() {
      RiptideConfigWriter.capturePendingNow();
      if (!RiptideLiteVariant.enabled()) {
         RiptideProfileManager.flushPendingMirrorIfInitialized();
      }
   }

   public static void flushPendingSaves(long var0) {
      enqueuePendingSaveNow();
      RiptideConfigWriter.flushBlocking(var0);
   }

   static String toJson(RiptideConfig var0) {
      return toJson(var0, ServerPluginScanCache.get());
   }

   static String toJson(RiptideConfig var0, ServerPluginScanCache var1) {
      Map var2 = var1.pendingLegacyFallback();
      if (var2.isEmpty()) {
         return GSON.toJson(var0);
      } else {
         JsonObject var3 = GSON.toJsonTree(var0).getAsJsonObject();
         var3.add("serverPluginScans", GSON.toJsonTree(var2));
         return GSON.toJson(var3);
      }
   }

   static void onPersistenceSnapshot(RiptideConfig var0) {
      Consumer var1 = afterPersistenceSnapshot;
      if (var1 != null) {
         try {
            var1.accept(var0);
         } catch (Throwable var3) {
            riptide.RiptideClientAddon.LOG.error("Riptide config snapshot hook failed", var3);
         }
      }
   }

   static void writeToDisk(String var0) {
      synchronized (SAVE_LOCK) {
         long var2 = RiptidePerf.begin();
         File var4 = configFile();
         var4.getParentFile().mkdirs();
         File var5 = new File(var4.getParentFile(), "config.json.tmp");

         try (FileWriter var6 = new FileWriter(var5)) {
            var6.write(var0);
         } catch (Throwable var15) {
            riptide.RiptideClientAddon.LOG.error("Failed to write Riptide config", var15);
            var5.delete();
            return;
         }

         try {
            if (var4.exists()) {
               Files.copy(var4.toPath(), backupFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
         } catch (Throwable var13) {
            riptide.RiptideClientAddon.LOG.warn("Failed to back up Riptide config before save", var13);
         }

         try {
            Files.move(var5.toPath(), var4.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (Throwable var12) {
            try {
               Files.move(var5.toPath(), var4.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable var11) {
               riptide.RiptideClientAddon.LOG.error("Failed to swap in Riptide config", var11);
            }
         }

         RiptidePerf.endSpike("config.write", var2, 100000000L);
      }
   }

   public RiptideConfig deepCopy() {
      return RiptideConfigSnapshot.copyOf(this);
   }

   public RiptideConfig snapshotForProfile() {
      return stripMachineStateForProfile(this.deepCopy());
   }

   static RiptideConfig profileSnapshotFromDetached(RiptideConfig var0) {
      return var0 == null ? stripMachineStateForProfile(new RiptideConfig()) : stripMachineStateForProfile(var0.shallowDetachedCopy());
   }

   private RiptideConfig shallowDetachedCopy() {
      try {
         return (RiptideConfig)super.clone();
      } catch (CloneNotSupportedException var2) {
         throw new AssertionError(var2);
      }
   }

   private static RiptideConfig stripMachineStateForProfile(RiptideConfig var0) {
      var0.activeProfileId = "";
      var0.packetLoggerCapturing = false;
      var0.serverPluginScans = new LinkedHashMap<>();
      var0.welcomeShown = false;
      var0.welcomeInstallIdentity = "";
      var0.essentialHiddenByPanic = false;
      return var0;
   }

   public static boolean sameThemeColors(RiptideConfig var0, RiptideConfig var1) {
      return var0 != null && var1 != null ? GSON.toJson(var0.themeColors).equals(GSON.toJson(var1.themeColors)) : var0 == var1;
   }

   public static boolean samePayloadRules(RiptideConfig var0, RiptideConfig var1) {
      return var0 != null && var1 != null
         ? GSON.toJson(var0.packetLoggerPayloadFilters).equals(GSON.toJson(var1.packetLoggerPayloadFilters))
            && GSON.toJson(var0.packetLoggerPayloadRegistrations).equals(GSON.toJson(var1.packetLoggerPayloadRegistrations))
         : var0 == var1;
   }

   public void applyRuntimeDefaults() {
      this.sendGuiPackets = true;
      this.delayGuiPackets = false;
      this.staggeredPacketSend = false;
      if (this.c2sPackets == null) {
         this.c2sPackets = new ArrayList<>();
      }

      if (this.s2cPackets == null) {
         this.s2cPackets = new ArrayList<>();
      }

      if (this.packetLoggerPayloadFilters == null) {
         this.packetLoggerPayloadFilters = new ArrayList<>();
      }

      if (this.packetLoggerPayloadRegistrations == null) {
         this.packetLoggerPayloadRegistrations = new ArrayList<>();
      }

      if (this.packetLoggerPayloadListeners == null) {
         this.packetLoggerPayloadListeners = new ArrayList<>();
      }

      if (this.packetLoggerPayloadFilters.isEmpty() && !this.packetLoggerPayloadListeners.isEmpty()) {
         for (RiptideConfig.PayloadChannelListenerRule var2 : this.packetLoggerPayloadListeners) {
            if (var2 != null) {
               RiptideConfig.PayloadChannelFilterRule var3 = new RiptideConfig.PayloadChannelFilterRule();
               var3.label = var2.label;
               var3.pattern = var2.pattern;
               var3.enabled = var2.enabled;
               var3.preset = var2.preset;
               this.packetLoggerPayloadFilters.add(var3);
            }
         }
      }

      if (!this.packetLoggerPayloadListeners.isEmpty()) {
         this.packetLoggerPayloadListeners = new ArrayList<>();
      }

      if (this.modules == null) {
         this.modules = new LinkedHashMap<>();
      }

      if (this.hideRestoreModules == null) {
         this.hideRestoreModules = new ArrayList<>();
      }

      if (this.hideRestoreMeteorModules == null) {
         this.hideRestoreMeteorModules = new ArrayList<>();
      }

      if (this.moduleCategoryLayouts == null) {
         this.moduleCategoryLayouts = new LinkedHashMap<>();
      }

      if (this.moduleCategoryOrder == null) {
         this.moduleCategoryOrder = new LinkedHashMap<>();
      }

      if (this.hudElements == null) {
         this.hudElements = new LinkedHashMap<>();
      }

      if (this.serverPluginScans == null) {
         this.serverPluginScans = new LinkedHashMap<>();
      }

      if (this.disabledAddonIds == null) {
         this.disabledAddonIds = new ArrayList<>();
      }

      if (this.themeColors == null) {
         this.themeColors = new RiptideConfig.ThemeColors();
      }

      this.overlayScale = RiptideUiScale.nearestAllowedOverlayScale(this.overlayScale);
      this.tpMaxPackets = Math.max(1, Math.min(100, this.tpMaxPackets));
      this.tpPauseMs = Math.max(50, Math.min(10000, this.tpPauseMs));
      if (this.hudSnapRange <= 0) {
         this.hudSnapRange = 10;
      }

      if (this.hudEdgePadding < 0) {
         this.hudEdgePadding = 4;
      }

      this.commandPrefix = RiptideCompatManager.normalizeStoredCommandPrefix(this.commandPrefix);
      if (!this.resourcePackChoiceInitialized) {
         this.pretendPackAccepted = false;
         this.resourcePackChoiceInitialized = true;
      }

      for (RiptideConfig.HudElementState var5 : this.hudElements.values()) {
         if (var5 != null && var5.settings == null) {
            var5.settings = new LinkedHashMap<>();
         }
      }
   }

   private static String pluginScanKey(String var0, String var1) {
      String var2 = var0 == null ? "" : var0.trim().toLowerCase(Locale.ROOT);
      String var3 = var1 == null ? "" : var1.trim().toLowerCase(Locale.ROOT);
      return var3.isEmpty() ? var2 : var2 + "|" + Integer.toHexString(var3.hashCode());
   }

   public RiptideConfig.PluginScanCacheEntry getPluginScan(String var1, String var2) {
      return var1 != null && !var1.isBlank() ? ServerPluginScanCache.get().get(pluginScanKey(var1, var2)) : null;
   }

   public long getPluginScanTimestamp(String var1, String var2) {
      RiptideConfig.PluginScanCacheEntry var3 = this.getPluginScan(var1, var2);
      return var3 == null ? 0L : var3.scannedAtMs;
   }

   public RiptideConfig.PluginScanCacheEntry getPluginScanByAddress(String var1) {
      return var1 != null && !var1.isBlank() ? ServerPluginScanCache.get().newestForAddress(var1.trim().toLowerCase(Locale.ROOT)) : null;
   }

   public long getPluginScanTimestampByAddress(String var1) {
      RiptideConfig.PluginScanCacheEntry var2 = this.getPluginScanByAddress(var1);
      return var2 == null ? 0L : var2.scannedAtMs;
   }

   public Map<String, RiptideConfig.PluginScanCacheEntry> allPluginScans() {
      return ServerPluginScanCache.get().snapshot();
   }

   public long pluginScanRevision() {
      return ServerPluginScanCache.get().revision();
   }

   public void putPluginScan(String var1, String var2, List<String> var3, Map<String, List<String>> var4, Map<String, String> var5) {
      this.putPluginScan(var1, var2, var3, var4, var5, Map.of());
   }

   public void putPluginScan(
      String var1, String var2, List<String> var3, Map<String, List<String>> var4, Map<String, String> var5, Map<String, List<String>> var6
   ) {
      this.putPluginScan(var1, var2, var3, var4, var5, var6, Map.of(), Map.of());
   }

   public void putPluginScan(
      String var1,
      String var2,
      List<String> var3,
      Map<String, List<String>> var4,
      Map<String, String> var5,
      Map<String, List<String>> var6,
      Map<String, List<String>> var7,
      Map<String, String> var8
   ) {
      this.putPluginScan(var1, var2, var3, var4, var5, var6, var7, var8, Map.of(), Map.of());
   }

   public void putPluginScan(
      String var1,
      String var2,
      List<String> var3,
      Map<String, List<String>> var4,
      Map<String, String> var5,
      Map<String, List<String>> var6,
      Map<String, List<String>> var7,
      Map<String, String> var8,
      Map<String, String> var9,
      Map<String, List<String>> var10
   ) {
      this.putPluginScan(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, "COMPLETE", 0, 0, 0, 0, "", "");
   }

   public void putPluginScan(
      String var1,
      String var2,
      List<String> var3,
      Map<String, List<String>> var4,
      Map<String, String> var5,
      Map<String, List<String>> var6,
      Map<String, List<String>> var7,
      Map<String, String> var8,
      Map<String, String> var9,
      Map<String, List<String>> var10,
      String var11,
      int var12,
      int var13,
      int var14,
      int var15,
      String var16,
      String var17
   ) {
      if (var1 != null && !var1.isBlank()) {
         RiptideConfig.PluginScanCacheEntry var18 = new RiptideConfig.PluginScanCacheEntry();
         var18.contextSignature = var2 == null ? "" : var2;
         var18.serverName = var16 == null ? "" : var16;
         var18.serverAddress = var17 != null && !var17.isBlank() ? var17 : var1;
         var18.plugins = var3 == null ? new ArrayList<>() : new ArrayList<>(var3);
         var18.commands = var4 == null ? new LinkedHashMap<>() : new LinkedHashMap<>(var4);
         var18.evidence = var5 == null ? new LinkedHashMap<>() : new LinkedHashMap<>(var5);
         var18.channels = var6 == null ? new LinkedHashMap<>() : new LinkedHashMap<>(var6);
         var18.guis = var7 == null ? new LinkedHashMap<>() : new LinkedHashMap<>(var7);
         var18.confidence = var8 == null ? new LinkedHashMap<>() : new LinkedHashMap<>(var8);
         var18.copyEvidence = var9 == null ? new LinkedHashMap<>() : new LinkedHashMap<>(var9);
         var18.copyCommands = var10 == null ? new LinkedHashMap<>() : new LinkedHashMap<>(var10);
         var18.scanStatus = var11 != null && !var11.isBlank() ? var11 : "COMPLETE";
         var18.totalProbes = Math.max(0, var12);
         var18.answeredProbes = Math.max(0, var13);
         var18.retriedProbes = Math.max(0, var14);
         var18.failedProbes = Math.max(0, var15);
         var18.scannedAtMs = System.currentTimeMillis();
         ServerPluginScanCache.get().put(pluginScanKey(var1, var2), var18);
         this.serverPluginScans = ServerPluginScanCache.get().sharedView();
      }
   }

   public void removePluginScan(String var1, String var2) {
      if (var1 != null) {
         ServerPluginScanCache.get().remove(pluginScanKey(var1, var2));
         this.serverPluginScans = ServerPluginScanCache.get().sharedView();
      }
   }

   public void removePluginScan(String var1) {
      if (var1 != null) {
         String var2 = pluginScanKey(var1, "");
         ServerPluginScanCache.get().removeAddress(var2);
         this.serverPluginScans = ServerPluginScanCache.get().sharedView();
      }
   }

   public static final class HudElementState {
      public boolean enabled = true;
      public int x = 8;
      public int y = 8;
      public double scale = 1.0;
      public String anchor = "TOP_LEFT";
      public Map<String, String> settings = new LinkedHashMap<>();
   }

   public static final class ModuleCategoryLayout {
      public int x = -1;
      public int y = -1;
      public boolean collapsed = false;
      public int visibleRows = 0;
   }

   public static final class ModuleState {
      public boolean enabled = false;
      public int keybind = -1;
      public Map<String, String> settings = new LinkedHashMap<>();
   }

   public static class PayloadChannelFilterRule {
      public String label = "";
      public String pattern = "";
      public boolean enabled = true;
      public boolean preset = false;
   }

   @Deprecated
   public static final class PayloadChannelListenerRule extends RiptideConfig.PayloadChannelFilterRule {
      public String direction = "ANY";
   }

   public static class PayloadChannelRegistrationRule {
      public String label = "";
      public String channel = "";
      public boolean enabled = true;
      public String source = "custom";
   }

   public static final class PluginScanCacheEntry {
      public String contextSignature = "";
      public String serverName = "";
      public String serverAddress = "";
      public List<String> plugins = new ArrayList<>();
      public Map<String, List<String>> commands = new LinkedHashMap<>();
      public Map<String, String> evidence = new LinkedHashMap<>();
      public Map<String, List<String>> channels = new LinkedHashMap<>();
      public Map<String, List<String>> guis = new LinkedHashMap<>();
      public Map<String, String> confidence = new LinkedHashMap<>();
      public Map<String, String> copyEvidence = new LinkedHashMap<>();
      public Map<String, List<String>> copyCommands = new LinkedHashMap<>();
      public String scanStatus = "COMPLETE";
      public int totalProbes = 0;
      public int answeredProbes = 0;
      public int retriedProbes = 0;
      public int failedProbes = 0;
      public long scannedAtMs = 0L;
   }

   public static final class ThemeColors {
      public boolean advanced = true;
      public int master = -13058568;
      public int accent = -13058568;
      public int outline = -14783341;
      public int text = -1379077;
      public int toggle = -13058568;
      public int backdrop = -14202271;
      public int success = -13248397;
      public int danger = -1938838;
      public int button = -15575942;
      public int header = -13058568;
      public int hover = -8530948;
   }
}
