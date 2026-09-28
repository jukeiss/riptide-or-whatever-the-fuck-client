package riptide.api.macro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import riptide.addons.AddonManager;
import riptide.api.AddonRegistrationResult;
import riptide.util.macro.MacroAction;

public final class MacroPresetRegistry {
   private static final List<MacroPresetRegistry.PresetEntry> ENTRIES = new ArrayList<>();
   private static final Map<MacroPresetRegistry.PresetEntry, String> ENTRY_OWNERS = new IdentityHashMap<>();
   private static final Map<String, String> CATEGORY_LABELS = new LinkedHashMap<>();
   private static final Map<String, String> CATEGORY_OWNERS = new LinkedHashMap<>();

   private MacroPresetRegistry() {
   }

   public static void registerCategory(String id, String label) {
      registerCategory(id, label, AddonManager.currentAddonId());
   }

   public static String ensureScopedCategory(String rawId) {
      String scopedId = MacroActionRegistry.scopeCategoryId(rawId);
      if (!CATEGORY_LABELS.containsKey(scopedId)) {
         registerCategory(scopedId, AddonManager.scopedCategoryLabel(null), AddonManager.currentAddonId());
      }

      return scopedId;
   }

   public static void registerScopedCategory(String rawId, String label) {
      String visible = AddonManager.isLifecycleActive() ? AddonManager.scopedCategoryLabel(null) : label;
      registerCategory(MacroActionRegistry.scopeCategoryId(rawId), visible, AddonManager.currentAddonId());
   }

   private static void registerCategory(String id, String label, String ownerAddonId) {
      if (id != null && !id.isBlank()) {
         CATEGORY_LABELS.put(id, label != null && !label.isBlank() ? label : id);
         if (ownerAddonId != null && !ownerAddonId.isBlank()) {
            CATEGORY_OWNERS.put(id, ownerAddonId);
         }
      }
   }

   public static void register(String categoryId, String label, String tip, Supplier<List<MacroAction>> builder) {
      registerDetailed(categoryId, label, tip, builder);
   }

   public static AddonRegistrationResult registerDetailed(String categoryId, String label, String tip, Supplier<List<MacroAction>> builder) {
      String ownerAddonId = AddonManager.currentAddonId();
      if (ownerAddonId == null || ownerAddonId.isBlank()) {
         return reject(ownerAddonId, label, "registration outside an addon lifecycle - register from onInitialize() or onRegisterCategories()");
      } else if (categoryId == null || categoryId.isBlank()) {
         return reject(ownerAddonId, label, "missing category");
      } else if (label == null || label.isBlank()) {
         return reject(ownerAddonId, label, "missing label");
      } else if (builder == null) {
         return reject(ownerAddonId, label, "missing builder");
      } else {
         AddonRegistrationResult validation = validateBuilder(ownerAddonId, label, builder);
         if (!validation.accepted()) {
            return validation;
         } else {
            MacroPresetRegistry.PresetEntry entry = new MacroPresetRegistry.PresetEntry(categoryId, label, tip == null ? "" : tip, builder);
            ENTRIES.add(entry);
            ENTRY_OWNERS.put(entry, ownerAddonId);
            AddonManager.recordAcceptedRegistration("preset", label);
            return AddonRegistrationResult.accepted("preset", label);
         }
      }
   }

   private static AddonRegistrationResult validateBuilder(String ownerAddonId, String label, Supplier<List<MacroAction>> builder) {
      List<MacroAction> actions;
      try {
         actions = builder.get();
      } catch (Throwable var5) {
         riptide.RiptideClientAddon.LOG.warn("[MacroPresets] Rejecting preset '{}': builder threw", label, var5);
         AddonManager.recordRejectedRegistration(ownerAddonId, "preset", label, "builder threw " + var5.getClass().getSimpleName());
         return AddonRegistrationResult.rejected("preset", label, "builder threw " + var5.getClass().getSimpleName());
      }

      if (actions == null) {
         return reject(ownerAddonId, label, "builder returned null");
      } else {
         for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i) == null) {
               return reject(ownerAddonId, label, "builder returned null action at index " + i);
            }
         }

         return AddonRegistrationResult.accepted("preset", label);
      }
   }

   private static AddonRegistrationResult reject(String ownerAddonId, String label, String reason) {
      riptide.RiptideClientAddon.LOG.warn("[MacroPresets] Rejecting preset '{}': {}", label, reason);
      AddonManager.recordRejectedRegistration(ownerAddonId, "preset", label, reason);
      return AddonRegistrationResult.rejected("preset", label, reason);
   }

   public static void unregisterAddon(String addonId) {
      if (addonId != null && !addonId.isBlank()) {
         List<MacroPresetRegistry.PresetEntry> remove = new ArrayList<>();

         for (MacroPresetRegistry.PresetEntry entry : ENTRIES) {
            if (addonId.equals(ENTRY_OWNERS.get(entry))) {
               remove.add(entry);
            }
         }

         ENTRIES.removeAll(remove);

         for (MacroPresetRegistry.PresetEntry entryx : remove) {
            ENTRY_OWNERS.remove(entryx);
         }

         CATEGORY_LABELS.entrySet().removeIf(entryx -> addonId.equals(CATEGORY_OWNERS.get(entryx.getKey())));
         CATEGORY_OWNERS.entrySet().removeIf(entryx -> addonId.equals(entryx.getValue()));
      }
   }

   public static List<MacroPresetRegistry.PresetEntry> entries() {
      return Collections.unmodifiableList(ENTRIES);
   }

   public static String categoryLabel(String id) {
      return CATEGORY_LABELS.get(id);
   }

   public record PresetEntry(String categoryId, String label, String tip, Supplier<List<MacroAction>> builder) {
   }
}
