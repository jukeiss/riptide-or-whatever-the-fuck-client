package riptide.addons;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.fabricmc.loader.api.metadata.CustomValue.CvType;
import riptide.api.RiptideAddon;
import riptide.api.custommenu.CustomMenuAdapterRegistry;
import riptide.api.event.AddonEvents;
import riptide.api.hud.HudElements;
import riptide.api.macro.MacroActionRegistry;
import riptide.api.macro.MacroPresetRegistry;
import riptide.commands.RiptideCommands;
import riptide.modules.ModuleRegistry;
import riptide.util.RiptideConfig;
import riptide.util.RiptideNotifications;

public final class AddonManager {
   private static final int MAX_UI_ADDON_NAME_LENGTH = 22;
   private static final List<RiptideAddon> LOADED = new ArrayList<>();
   private static final Map<String, AddonManager.AddonReport> REPORTS = new LinkedHashMap<>();
   private static volatile AddonManager.LoadingContext currentContext = null;
   private static boolean failuresSurfaced = false;

   private AddonManager() {
   }

   public static void init() {
      LOADED.clear();
      REPORTS.clear();
      failuresSurfaced = false;
      RiptideConfig config = RiptideConfig.getGlobal();
      List<AddonManager.LoadedAddon> accepted = new ArrayList<>();

      for (EntrypointContainer<RiptideAddon> container : FabricLoader.getInstance().getEntrypointContainers("riptide", RiptideAddon.class)) {
         ModContainer mod;
         try {
            mod = container.getProvider();
         } catch (Throwable var12) {
            riptide.RiptideClientAddon.LOG.error("[Addons] Failed to inspect an addon entrypoint", var12);
            continue;
         }

         String modId = mod.getMetadata().getId();
         AddonManager.AddonReport report = ensureReport(mod.getMetadata());
         if (isDisabledOnRestart(modId)) {
            report.markDisabled("Disabled in Riptide config. Enable it and restart to load.");
         } else {
            RiptideAddon addon;
            try {
               addon = (RiptideAddon)container.getEntrypoint();
            } catch (Throwable var11) {
               report.markFailed("construct", "Failed to construct entrypoint: " + shortError(var11));
               riptide.RiptideClientAddon.LOG.error("[Addons] Failed to construct addon '{}'", modId, var11);
               continue;
            }

            try {
               int declared = addon.apiVersion();
               report.setApiVersion(declared);
               if (declared <= 0) {
                  report.markFailed("apiVersion", "Invalid API version " + declared + ".");
                  riptide.RiptideClientAddon.LOG.warn("[Addons] Addon '{}' declared invalid API v{}; skipping it.", modId, declared);
               } else if (declared > 3) {
                  report.markNeedsNewerApi("Needs API v" + declared + ", host provides v3.");
                  riptide.RiptideClientAddon.LOG
                     .warn("[Addons] Addon '{}' needs API v{} but this client provides v{}; skipping it.", new Object[]{modId, declared, 3});
               } else {
                  if (declared < 3) {
                     riptide.RiptideClientAddon.LOG.info("[Addons] Addon '{}' built against API v{} (current v{}); loading.", new Object[]{modId, declared, 3});
                  }

                  applyMetadata(addon, mod.getMetadata());
                  report.copyRuntimeMetadata(addon);
                  accepted.add(new AddonManager.LoadedAddon(modId, addon));
               }
            } catch (AbstractMethodError var9) {
               report.markMissingApiVersion("Missing apiVersion() override.");
               riptide.RiptideClientAddon.LOG.error("[Addons] Addon '{}' does not declare apiVersion(); skipping it.", modId, var9);
            } catch (Throwable var10) {
               report.markFailed("prepare", "Failed to prepare: " + shortError(var10));
               riptide.RiptideClientAddon.LOG.error("[Addons] Error preparing addon '{}'", modId, var10);
            }
         }
      }

      List<AddonManager.LoadedAddon> categoryReady = new ArrayList<>();

      for (AddonManager.LoadedAddon la : accepted) {
         if (runPhase(la, "onRegisterCategories", la.addon::onRegisterCategories)) {
            categoryReady.add(la);
         } else {
            invokeUnload(la);
            rollbackRegistrations(la.modId);
         }
      }

      for (AddonManager.LoadedAddon lax : categoryReady) {
         if (runPhase(lax, "onInitialize", lax.addon::onInitialize)) {
            LOADED.add(lax.addon);
            AddonManager.AddonReport report = REPORTS.get(lax.modId);
            if (report != null) {
               report.markLoaded();
            }
         } else {
            invokeUnload(lax);
            rollbackRegistrations(lax.modId);
         }
      }

      if (!LOADED.isEmpty()) {
         riptide.RiptideClientAddon.LOG.info("[Addons] Loaded {} addon(s).", LOADED.size());
      }

      if (config.disabledAddonIds != null) {
         for (String id : config.disabledAddonIds) {
            if (id != null && !id.isBlank() && !REPORTS.containsKey(id)) {
               AddonManager.AddonReport report = new AddonManager.AddonReport(id.trim(), id.trim(), "", "", -5609729);
               report.markDisabled("Disabled in Riptide config, but no matching addon entrypoint was discovered.");
               REPORTS.put(report.modId(), report);
            }
         }
      }
   }

   private static boolean runPhase(AddonManager.LoadedAddon la, String phase, Runnable body) {
      String name = la.addon.name != null && !la.addon.name.isBlank() ? la.addon.name : la.modId;
      currentContext = new AddonManager.LoadingContext(la.modId, name);
      AddonManager.AddonReport report = REPORTS.get(la.modId);
      if (report != null) {
         report.setPhase(phase);
      }

      boolean var6;
      try {
         body.run();
         return true;
      } catch (Throwable var10) {
         if (report != null) {
            report.markFailed(phase, "Threw during " + phase + ": " + shortError(var10));
         }

         riptide.RiptideClientAddon.LOG.error("[Addons] Addon '{}' threw during {}", new Object[]{la.modId, phase, var10});
         var6 = false;
      } finally {
         currentContext = null;
      }

      return var6;
   }

   private static void invokeUnload(AddonManager.LoadedAddon la) {
      if (la != null && la.addon != null) {
         try {
            la.addon.onUnload();
         } catch (Throwable var2) {
            riptide.RiptideClientAddon.LOG.warn("[Addons] Addon '{}' threw during onUnload", la.modId, var2);
         }
      }
   }

   private static void rollbackRegistrations(String addonId) {
      if (addonId != null && !addonId.isBlank()) {
         AddonManager.AddonReport report = REPORTS.get(addonId);
         rollbackStep(addonId, report, "macro actions", () -> MacroActionRegistry.unregisterAddon(addonId));
         rollbackStep(addonId, report, "custom menu adapters", () -> CustomMenuAdapterRegistry.unregisterAddon(addonId));
         rollbackStep(addonId, report, "presets", () -> MacroPresetRegistry.unregisterAddon(addonId));
         rollbackStep(addonId, report, "events", () -> AddonEvents.unregisterAddon(addonId));
         rollbackStep(addonId, report, "hud", () -> HudElements.unregisterAddon(addonId));
         rollbackStep(addonId, report, "modules", () -> ModuleRegistry.unregisterAddonModules(addonId));
         rollbackStep(addonId, report, "commands", () -> RiptideCommands.unregisterAddonCommands(addonId));
         if (report != null) {
            report.addRollback("Rolled back partial registrations after load failure.");
         }
      }
   }

   private static void rollbackStep(String addonId, AddonManager.AddonReport report, String kind, Runnable body) {
      try {
         body.run();
      } catch (Throwable var6) {
         String detail = "Failed to rollback " + kind + ": " + shortError(var6);
         if (report != null) {
            report.addRollback(detail);
         }

         riptide.RiptideClientAddon.LOG.warn("[Addons] Addon '{}' rollback failed for {}", new Object[]{addonId, kind, var6});
      }
   }

   private static AddonManager.AddonReport ensureReport(ModMetadata meta) {
      String modId = meta.getId();
      AddonManager.AddonReport existing = REPORTS.get(modId);
      if (existing != null) {
         return existing;
      } else {
         AddonManager.AddonReport report = new AddonManager.AddonReport(
            modId,
            meta.getName() != null && !meta.getName().isBlank() ? meta.getName() : modId,
            meta.getVersion().getFriendlyString(),
            meta.getAuthors().stream().<CharSequence>map(Person::getName).collect(Collectors.joining(", ")),
            metadataColor(meta, -5609729)
         );
         REPORTS.put(modId, report);
         return report;
      }
   }

   private static void applyMetadata(RiptideAddon addon, ModMetadata meta) {
      addon.name = meta.getName();
      addon.authors = meta.getAuthors().stream().<CharSequence>map(Person::getName).collect(Collectors.joining(", "));
      addon.color = metadataColor(meta, addon.color);
   }

   private static int metadataColor(ModMetadata meta, int fallback) {
      CustomValue color = meta.containsCustomValue("riptide:color") ? meta.getCustomValue("riptide:color") : null;
      return color != null && color.getType() == CvType.STRING ? parseColor(color.getAsString(), fallback) : fallback;
   }

   private static int parseColor(String raw, int fallback) {
      try {
         String[] parts = raw.split(",");
         if (parts.length != 3) {
            return fallback;
         } else {
            int r = clamp(Integer.parseInt(parts[0].trim()));
            int g = clamp(Integer.parseInt(parts[1].trim()));
            int b = clamp(Integer.parseInt(parts[2].trim()));
            return 0xFF000000 | r << 16 | g << 8 | b;
         }
      } catch (Exception var6) {
         return fallback;
      }
   }

   private static int clamp(int v) {
      return Math.max(0, Math.min(255, v));
   }

   private static String currentId() {
      AddonManager.LoadingContext ctx = currentContext;
      return ctx == null ? null : ctx.addonId();
   }

   private static String currentName() {
      AddonManager.LoadingContext ctx = currentContext;
      return ctx == null ? null : ctx.addonName();
   }

   public static String currentAddonId() {
      return currentId();
   }

   public static boolean isLifecycleActive() {
      String id = currentId();
      return id != null && !id.isBlank();
   }

   public static String currentAddonName() {
      return currentName();
   }

   public static String normalizedAddonName(String name, String fallback) {
      String value = name == null ? "" : name.replaceAll("\\s+", " ").trim();
      if (value.isBlank()) {
         value = fallback == null ? "" : fallback.replaceAll("\\s+", " ").trim();
      }

      return value.isBlank() ? "Addon" : value;
   }

   public static String compactAddonName(String name, String fallback) {
      String value = normalizedAddonName(name, fallback);
      return value.length() <= 22 ? value : value.substring(0, Math.max(1, 19)).trim() + "...";
   }

   public static String scopedId(String rawId) {
      String raw = rawId == null ? "" : rawId.trim();
      if (raw.contains(":")) {
         return raw;
      } else {
         String owner = currentId();
         return owner != null && !owner.isBlank() ? owner + ":" + raw : raw;
      }
   }

   public static String scopedCategoryLabel(String customLabel) {
      AddonManager.LoadingContext ctx = currentContext;
      String id = ctx == null ? null : ctx.addonId();
      String name = ctx == null ? null : ctx.addonName();
      if (name == null || name.isBlank()) {
         name = id;
      }

      return compactAddonName(name, id);
   }

   public static List<RiptideAddon> loaded() {
      return Collections.unmodifiableList(LOADED);
   }

   public static List<AddonManager.AddonReport> reports() {
      return Collections.unmodifiableList(new ArrayList<>(REPORTS.values()));
   }

   public static AddonManager.AddonReport report(String addonId) {
      return addonId == null ? null : REPORTS.get(addonId);
   }

   public static File modsFolder() {
      return FabricLoader.getInstance().getGameDir().resolve("mods").toFile();
   }

   public static boolean isDisabledOnRestart(String addonId) {
      if (addonId != null && !addonId.isBlank()) {
         RiptideConfig config = RiptideConfig.getGlobal();
         if (config.disabledAddonIds == null) {
            return false;
         } else {
            String needle = addonId.trim().toLowerCase(Locale.ROOT);

            for (String id : config.disabledAddonIds) {
               if (id != null && needle.equals(id.trim().toLowerCase(Locale.ROOT))) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   public static void setDisabledOnRestart(String addonId, boolean disabled) {
      if (addonId != null && !addonId.isBlank()) {
         RiptideConfig config = RiptideConfig.getGlobal();
         if (config.disabledAddonIds == null) {
            config.disabledAddonIds = new ArrayList<>();
         }

         String normalized = addonId.trim();
         boolean currently = isDisabledOnRestart(normalized);
         if (disabled && !currently) {
            config.disabledAddonIds.add(normalized);
         } else if (!disabled && currently) {
            config.disabledAddonIds.removeIf(id -> id != null && normalized.equalsIgnoreCase(id.trim()));
         }

         config.save();
      }
   }

   public static void recordAcceptedRegistration(String kind, String id) {
      AddonManager.AddonReport report = REPORTS.get(currentId());
      if (report != null) {
         report.recordAccepted(kind, id);
      }
   }

   public static void recordRejectedRegistration(String addonId, String kind, String id, String reason) {
      AddonManager.AddonReport report = REPORTS.get(addonId != null && !addonId.isBlank() ? addonId : currentId());
      if (report != null) {
         report.recordRejected(kind, id, reason);
      }
   }

   public static void recordRuntimeError(String addonId, String detail) {
      AddonManager.AddonReport report = REPORTS.get(addonId);
      if (report != null) {
         report.recordRuntimeError(detail);
      }
   }

   public static void surfaceFailuresOnJoin() {
      if (!failuresSurfaced) {
         int issues = issueCount();
         if (issues > 0) {
            failuresSurfaced = true;
            RiptideNotifications.warning(issues + " addon issue(s). Open Addons for details.");
         }
      }
   }

   private static int issueCount() {
      int count = 0;

      for (AddonManager.AddonReport report : REPORTS.values()) {
         if (report.status().isIssue()) {
            count++;
         }
      }

      return count;
   }

   private static String shortError(Throwable t) {
      if (t == null) {
         return "Unknown error";
      } else {
         String msg = t.getMessage();
         return msg != null && !msg.isBlank() ? t.getClass().getSimpleName() + ": " + msg : t.getClass().getSimpleName();
      }
   }

   public static enum AddonLoadStatus {
      LOADED("Loaded", false),
      DISABLED("Disabled on restart", false),
      FAILED("Failed", true),
      NEEDS_NEWER_API("Needs newer API", true),
      MISSING_API_VERSION("Missing apiVersion", true);

      private final String label;
      private final boolean issue;

      private AddonLoadStatus(String label, boolean issue) {
         this.label = label;
         this.issue = issue;
      }

      public String label() {
         return this.label;
      }

      public boolean isIssue() {
         return this.issue;
      }
   }

   public static final class AddonReport {
      private final String modId;
      private String name;
      private String version;
      private String authors;
      private int color;
      private int apiVersion = -1;
      private int hostApiVersion = 3;
      private AddonManager.AddonLoadStatus status = AddonManager.AddonLoadStatus.FAILED;
      private String phase = "discovered";
      private String failureReason = "";
      private final Map<String, Integer> accepted = new LinkedHashMap<>();
      private final Map<String, Integer> rejected = new LinkedHashMap<>();
      private final List<String> rejectionDetails = new ArrayList<>();
      private final List<String> rollbackDetails = new ArrayList<>();
      private int runtimeErrors;
      private String lastRuntimeError = "";

      private AddonReport(String modId, String name, String version, String authors, int color) {
         this.modId = modId == null ? "" : modId;
         this.name = name != null && !name.isBlank() ? name : this.modId;
         this.version = version == null ? "" : version;
         this.authors = authors == null ? "" : authors;
         this.color = color;
      }

      private void copyRuntimeMetadata(RiptideAddon addon) {
         if (addon != null) {
            if (addon.name != null && !addon.name.isBlank()) {
               this.name = addon.name;
            }

            if (addon.authors != null && !addon.authors.isBlank()) {
               this.authors = addon.authors;
            }

            this.color = addon.color;
         }
      }

      private void setApiVersion(int apiVersion) {
         this.apiVersion = apiVersion;
      }

      private void setPhase(String phase) {
         this.phase = phase != null && !phase.isBlank() ? phase : this.phase;
      }

      private void markLoaded() {
         this.status = AddonManager.AddonLoadStatus.LOADED;
         this.phase = "loaded";
         this.failureReason = "";
      }

      private void markDisabled(String reason) {
         this.status = AddonManager.AddonLoadStatus.DISABLED;
         this.phase = "disabled";
         this.failureReason = reason == null ? "" : reason;
      }

      private void markFailed(String phase, String reason) {
         this.status = AddonManager.AddonLoadStatus.FAILED;
         this.phase = phase != null && !phase.isBlank() ? phase : "failed";
         this.failureReason = reason == null ? "" : reason;
      }

      private void markNeedsNewerApi(String reason) {
         this.status = AddonManager.AddonLoadStatus.NEEDS_NEWER_API;
         this.phase = "apiVersion";
         this.failureReason = reason == null ? "" : reason;
      }

      private void markMissingApiVersion(String reason) {
         this.status = AddonManager.AddonLoadStatus.MISSING_API_VERSION;
         this.phase = "apiVersion";
         this.failureReason = reason == null ? "" : reason;
      }

      private void recordAccepted(String kind, String id) {
         String key = kind != null && !kind.isBlank() ? kind : "item";
         this.accepted.merge(key, 1, Integer::sum);
      }

      private void recordRejected(String kind, String id, String reason) {
         String key = kind != null && !kind.isBlank() ? kind : "item";
         this.rejected.merge(key, 1, Integer::sum);
         String detail = key + (id != null && !id.isBlank() ? " " + id : "") + ": " + (reason != null && !reason.isBlank() ? reason : "rejected");
         this.rejectionDetails.add(detail);
      }

      private void addRollback(String detail) {
         if (detail != null && !detail.isBlank()) {
            this.rollbackDetails.add(detail);
         }
      }

      private void recordRuntimeError(String detail) {
         this.runtimeErrors++;
         this.lastRuntimeError = detail != null && !detail.isBlank() ? detail : "Runtime error";
      }

      public String modId() {
         return this.modId;
      }

      public String name() {
         return this.name;
      }

      public String version() {
         return this.version;
      }

      public String authors() {
         return this.authors;
      }

      public int color() {
         return this.color;
      }

      public int apiVersion() {
         return this.apiVersion;
      }

      public int hostApiVersion() {
         return this.hostApiVersion;
      }

      public AddonManager.AddonLoadStatus status() {
         return this.status;
      }

      public String phase() {
         return this.phase;
      }

      public String failureReason() {
         return this.failureReason;
      }

      public int runtimeErrors() {
         return this.runtimeErrors;
      }

      public String lastRuntimeError() {
         return this.lastRuntimeError;
      }

      public Map<String, Integer> accepted() {
         return Collections.unmodifiableMap(this.accepted);
      }

      public Map<String, Integer> rejected() {
         return Collections.unmodifiableMap(this.rejected);
      }

      public List<String> rejectionDetails() {
         return Collections.unmodifiableList(this.rejectionDetails);
      }

      public List<String> rollbackDetails() {
         return Collections.unmodifiableList(this.rollbackDetails);
      }

      public int acceptedTotal() {
         int total = 0;

         for (int value : this.accepted.values()) {
            total += value;
         }

         return total;
      }

      public int rejectedTotal() {
         int total = 0;

         for (int value : this.rejected.values()) {
            total += value;
         }

         return total;
      }

      public String summaryCounts() {
         if (this.accepted.isEmpty()) {
            return "No registered extensions";
         } else {
            List<String> parts = new ArrayList<>();

            for (Entry<String, Integer> entry : this.accepted.entrySet()) {
               parts.add(entry.getValue() + " " + entry.getKey());
            }

            return String.join(", ", parts);
         }
      }

      public String copyReport() {
         StringBuilder sb = new StringBuilder();
         sb.append("Addon: ").append(this.name).append('\n');
         sb.append("ID: ").append(this.modId).append('\n');
         if (!this.version.isBlank()) {
            sb.append("Version: ").append(this.version).append('\n');
         }

         if (!this.authors.isBlank()) {
            sb.append("Authors: ").append(this.authors).append('\n');
         }

         sb.append("Status: ").append(this.status.label()).append('\n');
         sb.append("Phase: ").append(this.phase).append('\n');
         sb.append("API: addon v")
            .append(this.apiVersion < 0 ? "unknown" : Integer.toString(this.apiVersion))
            .append(", host v")
            .append(this.hostApiVersion)
            .append('\n');
         sb.append("Disabled on restart: ").append(AddonManager.isDisabledOnRestart(this.modId)).append('\n');
         if (!this.failureReason.isBlank()) {
            sb.append("Reason: ").append(this.failureReason).append('\n');
         }

         sb.append("Accepted: ").append(this.accepted).append('\n');
         if (!this.rejected.isEmpty()) {
            sb.append("Rejected: ").append(this.rejected).append('\n');
         }

         for (String detail : this.rejectionDetails) {
            sb.append("- ").append(detail).append('\n');
         }

         for (String detail : this.rollbackDetails) {
            sb.append("- ").append(detail).append('\n');
         }

         if (this.runtimeErrors > 0) {
            sb.append("Runtime errors: ").append(this.runtimeErrors).append('\n');
            sb.append("Last runtime error: ").append(this.lastRuntimeError).append('\n');
         }

         return sb.toString();
      }
   }

   private record LoadedAddon(String modId, RiptideAddon addon) {
   }

   private record LoadingContext(String addonId, String addonName) {
   }
}
