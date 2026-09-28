package riptide.api.macro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import riptide.addons.AddonManager;
import riptide.api.AddonRegistrationResult;
import riptide.gui.macro.editor.ActionFieldSchema;
import riptide.gui.macro.editor.FieldDef;
import riptide.gui.macro.editor.FieldType;
import riptide.util.macro.MacroAction;
import riptide.util.macro.MacroActionType;

public final class MacroActionRegistry {
   private static final Map<String, MacroActionEntry> ENTRIES = new LinkedHashMap<>();
   private static final Map<String, MacroActionRegistry.ActionCategory> CATEGORIES = new LinkedHashMap<>();
   private static final Map<String, String> ENTRY_OWNERS = new LinkedHashMap<>();
   private static final Map<String, String> CATEGORY_OWNERS = new LinkedHashMap<>();
   private static final Map<String, ActionFieldSchema> RESOLVED_SCHEMAS = new LinkedHashMap<>();
   public static final int ADDON_AUTO_CATEGORY_COLOR = -5609729;
   private static final String ADDON_CAT_PREFIX = "addon:";

   private MacroActionRegistry() {
   }

   public static String scopeCategoryId(String rawId) {
      if (AddonManager.isLifecycleActive()) {
         String addonId = AddonManager.currentAddonId();
         return "addon:" + (addonId != null && !addonId.isBlank() ? addonId : "shared");
      } else if (rawId != null && rawId.startsWith("addon:")) {
         return rawId;
      } else {
         String addonId = AddonManager.currentAddonId();
         String base = addonId != null && !addonId.isBlank() ? addonId : "shared";
         return rawId != null && !rawId.isBlank() ? "addon:" + base + "/" + rawId : "addon:" + base;
      }
   }

   public static String ensureScopedCategory(String rawId) {
      String scopedId = scopeCategoryId(rawId);
      if (!CATEGORIES.containsKey(scopedId)) {
         registerCategoryInternal(scopedId, AddonManager.scopedCategoryLabel(null), -5609729, AddonManager.currentAddonId());
      }

      return scopedId;
   }

   public static void registerScopedCategory(String rawId, String label, int color) {
      String visible = AddonManager.isLifecycleActive() ? AddonManager.scopedCategoryLabel(null) : label;
      registerCategoryInternal(scopeCategoryId(rawId), visible, color, AddonManager.currentAddonId());
   }

   public static void registerCategory(String id, String label, int color) {
      registerCategoryInternal(id, label, color, null);
   }

   private static void registerCategoryInternal(String id, String label, int color, String ownerAddonId) {
      if (id != null && !id.isBlank()) {
         CATEGORIES.put(id, new MacroActionRegistry.ActionCategory(id, label != null && !label.isBlank() ? label : id, color));
         if (ownerAddonId != null && !ownerAddonId.isBlank()) {
            CATEGORY_OWNERS.put(id, ownerAddonId);
         }
      }
   }

   public static MacroActionRegistry.ActionCategory category(String id) {
      return id == null ? null : CATEGORIES.get(id);
   }

   public static boolean register(MacroActionEntry entry) {
      return registerDetailed(entry).accepted();
   }

   public static AddonRegistrationResult registerDetailed(MacroActionEntry entry) {
      if (entry == null) {
         return AddonRegistrationResult.rejected("macro action", "", "entry was null");
      } else {
         String ownerAddonId = AddonManager.currentAddonId();
         if (ownerAddonId != null && !ownerAddonId.isBlank()) {
            String typeId = entry.typeId();
            if (typeId == null || !typeId.contains(":")) {
               return reject(ownerAddonId, "macro action", typeId, "non-namespaced type id - scope it with RiptideAddons.id(...) so it becomes addonId:localId");
            } else if (!typeId.startsWith(ownerAddonId + ":")) {
               return reject(
                  ownerAddonId, "macro action", typeId, "foreign namespace - the id must start with your addon id; scope it with RiptideAddons.id(...)"
               );
            } else if (entry.factory() == null) {
               return reject(ownerAddonId, "macro action", typeId, "missing factory");
            } else if (isBuiltinName(typeId)) {
               return reject(ownerAddonId, "macro action", typeId, "collides with a built-in type");
            } else if (ENTRIES.containsKey(typeId)) {
               return reject(ownerAddonId, "macro action", typeId, "duplicate type id");
            } else {
               if (entry.wantsPicker()) {
                  entry.setPickerCategory(ensureScopedCategory(entry.pickerCategory()));
               }

               ActionFieldSchema effectiveSchema = entry.schema();

               MacroAction probe;
               try {
                  probe = entry.factory().get();
               } catch (Throwable var6) {
                  riptide.RiptideClientAddon.LOG.warn("[MacroActions] Rejecting action '{}' because its factory threw during validation", typeId, var6);
                  return reject(ownerAddonId, "macro action", typeId, "factory threw: " + errorName(var6));
               }

               if (probe == null) {
                  return reject(ownerAddonId, "macro action", typeId, "factory returned null");
               } else {
                  if (effectiveSchema == null) {
                     ActionSchema declared = AddonAction.schemaOf(probe);
                     if (declared != null) {
                        effectiveSchema = declared.internal();
                     }
                  }

                  String validationError = validateProbe(typeId, entry, probe, effectiveSchema);
                  if (validationError != null) {
                     return reject(ownerAddonId, "macro action", typeId, validationError);
                  } else {
                     ENTRIES.put(typeId, entry);
                     ENTRY_OWNERS.put(typeId, ownerAddonId);
                     if (effectiveSchema != null) {
                        RESOLVED_SCHEMAS.put(typeId, effectiveSchema);
                     }

                     AddonManager.recordAcceptedRegistration("macro action", typeId);
                     return AddonRegistrationResult.accepted("macro action", typeId);
                  }
               }
            }
         } else {
            return reject(ownerAddonId, "macro action", "", "registration outside an addon lifecycle - register from onInitialize() or onRegisterCategories()");
         }
      }
   }

   private static AddonRegistrationResult reject(String ownerAddonId, String kind, String id, String reason) {
      riptide.RiptideClientAddon.LOG.warn("[MacroActions] Rejecting {} '{}': {}", new Object[]{kind, id, reason});
      AddonManager.recordRejectedRegistration(ownerAddonId, kind, id, reason);
      return AddonRegistrationResult.rejected(kind, id, reason);
   }

   private static String validateProbe(String typeId, MacroActionEntry entry, MacroAction probe, ActionFieldSchema schema) {
      String writtenType;
      CompoundTag tag;
      try {
         tag = probe.toTag();
         writtenType = tag.getStringOr("type", "");
      } catch (Throwable var13) {
         riptide.RiptideClientAddon.LOG.warn("[MacroActions] Rejecting '{}' because toTag() threw", typeId, var13);
         return "toTag() threw: " + errorName(var13);
      }

      if (!typeId.equals(writtenType)) {
         return "writes type=\"" + writtenType + "\" instead of \"" + typeId + "\"";
      } else {
         if (schema != null) {
            for (FieldDef field : schema.fields()) {
               String key = field.key();
               if (field.type() == FieldType.BLOCK_POS) {
                  for (String axisKey : field.xyzKeys()) {
                     if (axisKey != null && !axisKey.isEmpty() && !tag.contains(axisKey)) {
                        return "schema field '" + axisKey + "' is not written by toTag()/save()";
                     }
                  }
               } else if (key != null && !key.isEmpty() && !tag.contains(key)) {
                  return "schema field '" + key + "' is not written by toTag()/save()";
               }
            }
         }

         return entry.isCondition() && !(probe instanceof ContextMacroAction) ? "conditions must implement ContextMacroAction" : null;
      }
   }

   public static void unregisterAddon(String addonId) {
      if (addonId != null && !addonId.isBlank()) {
         List<String> removeTypes = new ArrayList<>();

         for (Entry<String, String> owner : ENTRY_OWNERS.entrySet()) {
            if (addonId.equals(owner.getValue())) {
               removeTypes.add(owner.getKey());
            }
         }

         for (String typeId : removeTypes) {
            ENTRIES.remove(typeId);
            RESOLVED_SCHEMAS.remove(typeId);
            ENTRY_OWNERS.remove(typeId);
         }

         List<String> removeCategories = new ArrayList<>();

         for (Entry<String, String> ownerx : CATEGORY_OWNERS.entrySet()) {
            if (addonId.equals(ownerx.getValue())) {
               removeCategories.add(ownerx.getKey());
            }
         }

         for (String categoryId : removeCategories) {
            CATEGORIES.remove(categoryId);
            CATEGORY_OWNERS.remove(categoryId);
         }
      }
   }

   public static Supplier<MacroAction> factory(String typeId) {
      MacroActionEntry e = typeId == null ? null : ENTRIES.get(typeId);
      return e == null ? null : e.factory();
   }

   public static ActionFieldSchema schema(String typeId) {
      return typeId == null ? null : RESOLVED_SCHEMAS.get(typeId);
   }

   public static boolean isCondition(String typeId) {
      MacroActionEntry e = typeId == null ? null : ENTRIES.get(typeId);
      return e != null && e.isCondition();
   }

   public static String ownerOf(String typeId) {
      return typeId == null ? null : ENTRY_OWNERS.get(typeId);
   }

   public static List<MacroActionEntry> entries() {
      return Collections.unmodifiableList(new ArrayList<>(ENTRIES.values()));
   }

   private static boolean isBuiltinName(String typeId) {
      try {
         MacroActionType.valueOf(typeId.toUpperCase(Locale.ROOT));
         return true;
      } catch (IllegalArgumentException var2) {
         return false;
      }
   }

   private static String errorName(Throwable t) {
      if (t == null) {
         return "unknown";
      } else {
         String message = t.getMessage();
         return message != null && !message.isBlank() ? t.getClass().getSimpleName() + ": " + message : t.getClass().getSimpleName();
      }
   }

   public record ActionCategory(String id, String label, int color) {
   }
}
