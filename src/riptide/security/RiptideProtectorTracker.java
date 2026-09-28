package riptide.security;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.Identifier;

public final class RiptideProtectorTracker {
   private static final Set<String> CORE_IDS = Set.of("minecraft", "java", "mixinextras", "fabricloader", "fabric-loader", "fabric", "fabric-api", "fabric_api");
   private static final Set<String> INTEROP_ALLOWED = Set.of("voicechat");
   private static final Set<String> DEFAULT_ALLOWED_MODS = ConcurrentHashMap.newKeySet();
   private static final Set<String> MOD_IDS = ConcurrentHashMap.newKeySet();
   private static final Set<String> ALWAYS_ALLOWED_CHANNEL_NAMESPACES = ConcurrentHashMap.newKeySet();
   private static final Set<String> VANILLA_TRANSLATIONS = ConcurrentHashMap.newKeySet();
   private static final Map<String, String> SERVER_TRANSLATIONS = new ConcurrentHashMap<>();
   private static final Map<String, Set<String>> MOD_TRANSLATIONS = new ConcurrentHashMap<>();
   private static final Set<String> VANILLA_KEYBINDS = ConcurrentHashMap.newKeySet();
   private static final Map<String, Set<String>> MOD_KEYBINDS = new ConcurrentHashMap<>();
   private static final int CACHE_LIMIT = 8192;
   private static final Map<String, RiptideProtectorTracker.TranslationDecision> TRANSLATION_DECISION_CACHE = new ConcurrentHashMap<>();
   private static final Map<String, Boolean> KEYBIND_DECISION_CACHE = new ConcurrentHashMap<>();
   private static final Map<String, Boolean> MOD_KEY_CACHE = new ConcurrentHashMap<>();
   private static final AtomicBoolean BOOTSTRAPPED = new AtomicBoolean(false);

   private RiptideProtectorTracker() {
   }

   public static void bootstrap() {
      if (BOOTSTRAPPED.compareAndSet(false, true)) {
         try {
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
               String normalized = normalizeModId(mod.getMetadata().getId());
               if (normalized != null && !CORE_IDS.contains(normalized)) {
                  if (!normalized.startsWith("fabric-") && !normalized.startsWith("fabric_")) {
                     MOD_IDS.add(normalized);
                  } else {
                     ALWAYS_ALLOWED_CHANNEL_NAMESPACES.add(normalized);
                  }
               }
            }

            ALWAYS_ALLOWED_CHANNEL_NAMESPACES.add("fabric");
            ALWAYS_ALLOWED_CHANNEL_NAMESPACES.add("fabric-api");
            ALWAYS_ALLOWED_CHANNEL_NAMESPACES.addAll(INTEROP_ALLOWED);

            for (String id : INTEROP_ALLOWED) {
               addDefaultAllowedMod(id);
            }

            seedDefaultAllowedMods();
            clearDecisionCaches();
         } catch (Throwable var3) {
            riptide.RiptideClientAddon.LOG.warn("[RiptideProtector] Failed to bootstrap mod ID set: {}", var3.getMessage());
         }
      }
   }

   private static void seedDefaultAllowedMods() {
      addDefaultAllowedMod("fabric-resource-loader-v1");
      addDefaultAllowedMod("fabric-creative-tab-api-v1");
      addDefaultAllowedMod("fabric-registry-sync-v0");
      addDefaultAllowedMod("fabric-convention-tags-v2");
      addDefaultAllowedMod("fabric-data-attachment-api-v1");
   }

   public static void resetTranslations() {
      VANILLA_TRANSLATIONS.clear();
      SERVER_TRANSLATIONS.clear();
      MOD_TRANSLATIONS.clear();
      clearDecisionCaches();
   }

   public static void addVanillaTranslation(String key) {
      if (key != null && !key.isBlank() && VANILLA_TRANSLATIONS.add(key)) {
         clearDecisionCaches();
      }
   }

   public static void addServerTranslation(String key, String value) {
      if (key != null && !key.isBlank() && value != null) {
         SERVER_TRANSLATIONS.put(key, value);
         clearDecisionCaches();
      }
   }

   public static void addModTranslation(String key, String modId) {
      String normalized = normalizeModId(modId);
      if (key != null && !key.isBlank() && normalized != null) {
         if (MOD_TRANSLATIONS.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(normalized)) {
            clearDecisionCaches();
         }
      }
   }

   public static void applyDeprecatedTranslations(Iterable<String> removed, Map<String, String> renamed) {
      if (removed != null) {
         for (String key : removed) {
            VANILLA_TRANSLATIONS.remove(key);
            SERVER_TRANSLATIONS.remove(key);
            MOD_TRANSLATIONS.remove(key);
         }
      }

      if (renamed != null) {
         for (Entry<String, String> entry : renamed.entrySet()) {
            String from = entry.getKey();
            String to = entry.getValue();
            VANILLA_TRANSLATIONS.remove(from);
            SERVER_TRANSLATIONS.remove(from);
            MOD_TRANSLATIONS.remove(from);
            if (to != null && !to.isBlank()) {
               VANILLA_TRANSLATIONS.add(to);
            }
         }
      }

      clearDecisionCaches();
   }

   public static void addVanillaKeybind(String key) {
      if (key != null && !key.isBlank() && VANILLA_KEYBINDS.add(key)) {
         clearDecisionCaches();
      }
   }

   public static void addModKeybind(String key, String modId) {
      String normalized = normalizeModId(modId);
      if (key != null && !key.isBlank() && normalized != null) {
         if (MOD_KEYBINDS.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(normalized)) {
            clearDecisionCaches();
         }
      }
   }

   public static void addDefaultAllowedMod(String modId) {
      String normalized = normalizeModId(modId);
      if (normalized != null && !CORE_IDS.contains(normalized)) {
         if (DEFAULT_ALLOWED_MODS.add(normalized)) {
            clearDecisionCaches();
         }
      }
   }

   public static void addDefaultAllowedMods(Iterable<String> modIds) {
      if (modIds != null) {
         for (String modId : modIds) {
            addDefaultAllowedMod(modId);
         }
      }
   }

   public static String translationReplacement(String key, String defaultValue) {
      if (key != null && !key.isEmpty()) {
         RiptideProtectorTracker.TranslationDecision decision = TRANSLATION_DECISION_CACHE.get(key);
         if (decision == null) {
            decision = computeTranslationDecision(key);
            putBounded(TRANSLATION_DECISION_CACHE, key, decision);
         }
         return switch (decision.action()) {
            case 1 -> defaultValue;
            case 2 -> decision.value();
            default -> null;
         };
      } else {
         return null;
      }
   }

   public static boolean shouldBlockKeybind(String key) {
      if (key != null && !key.isEmpty()) {
         Boolean cached = KEYBIND_DECISION_CACHE.get(key);
         if (cached != null) {
            return cached;
         } else {
            boolean blocked = computeShouldBlockKeybind(key);
            putBounded(KEYBIND_DECISION_CACHE, key, blocked);
            return blocked;
         }
      } else {
         return false;
      }
   }

   public static boolean shouldBlockKey(String key) {
      return translationReplacement(key, key) != null ? true : shouldBlockKeybind(key);
   }

   public static boolean isModKey(String key) {
      if (key != null && !key.isEmpty() && !MOD_IDS.isEmpty()) {
         Boolean cached = MOD_KEY_CACHE.get(key);
         if (cached != null) {
            return cached;
         } else {
            boolean result = false;
            int start = 0;
            int len = key.length();

            for (int i = 0; i <= len; i++) {
               if (i == len || key.charAt(i) == '.') {
                  if (i > start) {
                     String segment = key.substring(start, i).toLowerCase(Locale.ROOT);
                     if (MOD_IDS.contains(segment)) {
                        result = true;
                        break;
                     }
                  }

                  start = i + 1;
               }
            }

            putBounded(MOD_KEY_CACHE, key, result);
            return result;
         }
      } else {
         return false;
      }
   }

   public static boolean isWhitelistedChannel(Identifier channel) {
      if (channel == null) {
         return false;
      } else {
         String namespace = channel.getNamespace();
         if (namespace == null) {
            return false;
         } else {
            String lower = namespace.toLowerCase(Locale.ROOT);
            return !"minecraft".equals(lower) && !"c".equals(lower) ? ALWAYS_ALLOWED_CHANNEL_NAMESPACES.contains(lower) : true;
         }
      }
   }

   private static boolean isAllowedOwner(String modId) {
      String normalized = normalizeModId(modId);
      return normalized != null && DEFAULT_ALLOWED_MODS.contains(normalized);
   }

   private static RiptideProtectorTracker.TranslationDecision computeTranslationDecision(String key) {
      if (!VANILLA_TRANSLATIONS.contains(key) && !RiptideProtectorVanillaKeys.contains(key)) {
         String serverValue = SERVER_TRANSLATIONS.get(key);
         if (serverValue != null) {
            return RiptideProtectorTracker.TranslationDecision.server(serverValue);
         } else {
            Set<String> owners = MOD_TRANSLATIONS.get(key);
            if (owners != null && !owners.isEmpty()) {
               for (String owner : owners) {
                  if (isAllowedOwner(owner)) {
                     return RiptideProtectorTracker.TranslationDecision.ALLOW_DECISION;
                  }
               }

               return RiptideProtectorTracker.TranslationDecision.BLOCK_DECISION;
            } else {
               return isModKey(key) ? RiptideProtectorTracker.TranslationDecision.BLOCK_DECISION : RiptideProtectorTracker.TranslationDecision.ALLOW_DECISION;
            }
         }
      } else {
         return RiptideProtectorTracker.TranslationDecision.ALLOW_DECISION;
      }
   }

   private static boolean computeShouldBlockKeybind(String key) {
      if (VANILLA_KEYBINDS.contains(key)) {
         return false;
      } else {
         Set<String> owners = MOD_KEYBINDS.get(key);
         if (owners != null && !owners.isEmpty()) {
            for (String owner : owners) {
               if (isAllowedOwner(owner)) {
                  return false;
               }
            }

            return true;
         } else {
            return isModKey(key);
         }
      }
   }

   private static <T> void putBounded(Map<String, T> cache, String key, T value) {
      if (cache.size() >= 8192) {
         cache.clear();
      }

      cache.put(key, value);
   }

   private static void clearDecisionCaches() {
      TRANSLATION_DECISION_CACHE.clear();
      KEYBIND_DECISION_CACHE.clear();
      MOD_KEY_CACHE.clear();
   }

   private static String normalizeModId(String modId) {
      return modId != null && !modId.isBlank() ? modId.toLowerCase(Locale.ROOT).trim() : null;
   }

   public static Set<String> defaultAllowedModsSnapshot() {
      return new HashSet<>(DEFAULT_ALLOWED_MODS);
   }

   private record TranslationDecision(int action, String value) {
      static final int ALLOW = 0;
      static final int BLOCK = 1;
      static final int SERVER = 2;
      static final RiptideProtectorTracker.TranslationDecision ALLOW_DECISION = new RiptideProtectorTracker.TranslationDecision(0, null);
      static final RiptideProtectorTracker.TranslationDecision BLOCK_DECISION = new RiptideProtectorTracker.TranslationDecision(1, null);

      static RiptideProtectorTracker.TranslationDecision server(String value) {
         return new RiptideProtectorTracker.TranslationDecision(2, value);
      }
   }
}
