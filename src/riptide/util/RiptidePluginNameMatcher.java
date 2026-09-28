package riptide.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class RiptidePluginNameMatcher {
   private RiptidePluginNameMatcher() {
   }

   static String normalizeVersionless(String value) {
      if (value == null) {
         return "";
      } else {
         String text = value.toLowerCase(Locale.ROOT)
            .replaceAll("(?i)(?:^|[\\s_\\-\\[\\(])v?\\d+(?:\\.\\d+)+(?:[-+][a-z0-9_.-]+)?(?=$|[\\s_\\-\\]\\)])", " ")
            .replaceAll("(?i)\\b(?:free|premium|pro|plus|paid|plugin|addon|spigot|bukkit|paper)\\b", " ");
         StringBuilder normalized = new StringBuilder(text.length());

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
               normalized.append(c);
            }
         }

         return normalized.toString();
      }
   }

   static double similarity(String detected, String provider) {
      String a = normalizeVersionless(detected);
      String b = normalizeVersionless(provider);
      if (a.isBlank() || b.isBlank()) {
         return 0.0;
      } else if (a.equals(b)) {
         return 1.0;
      } else if (a.length() >= 6 && b.contains(a) && (double)a.length() / Math.max(1, b.length()) >= 0.7) {
         return 0.93;
      } else if (b.length() >= 6 && a.contains(b) && (double)b.length() / Math.max(1, a.length()) >= 0.7) {
         return 0.92;
      } else {
         int distance = levenshtein(a, b, 8);
         return distance > 8 ? 0.0 : 1.0 - (double)distance / Math.max(a.length(), b.length());
      }
   }

   static String bestMatchingReference(String detected, Collection<String> providerReferences, double minimumScore) {
      if (providerReferences != null && !providerReferences.isEmpty()) {
         String best = null;
         double bestScore = minimumScore;

         for (String reference : providerReferences) {
            double score = similarity(detected, reference);
            if (!(score < bestScore) && (best == null || score > bestScore)) {
               best = reference;
               bestScore = score;
            }
         }

         return best;
      } else {
         return null;
      }
   }

   static List<String> extractProviderReferences(JsonObject object) {
      if (object == null) {
         return List.of();
      } else {
         Set<String> references = new LinkedHashSet<>();
         addProviderReference(
            references,
            firstString(object, "pluginName", "plugin_name", "affectedPlugin", "affected_plugin"),
            firstString(object, "pluginVersion", "plugin_version", "affectedPluginVersion", "affected_plugin_version")
         );
         JsonElement plugin = object.get("plugin");
         if (plugin != null && plugin.isJsonPrimitive()) {
            try {
               addProviderReference(references, plugin.getAsString(), null);
            } catch (Exception var4) {
            }
         }

         JsonElement plugins = firstPresent(object, "plugins", "affectedPlugins", "affected_plugins");
         collectProviderReferences(plugins, references);
         return List.copyOf(references);
      }
   }

   private static void collectProviderReferences(JsonElement element, Set<String> out) {
      if (element != null && !element.isJsonNull() && out != null) {
         if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
               collectProviderReferences(child, out);
            }
         } else if (element.isJsonPrimitive()) {
            try {
               addProviderReference(out, element.getAsString(), null);
            } catch (Exception var4) {
            }
         } else if (element.isJsonObject()) {
            JsonObject reference = element.getAsJsonObject();
            addProviderReference(
               out, firstString(reference, "name", "pluginName", "plugin_name", "plugin"), firstString(reference, "version", "pluginVersion", "plugin_version")
            );
         }
      }
   }

   private static void addProviderReference(Set<String> out, String name, String version) {
      if (out != null && name != null && !name.isBlank()) {
         String cleanName = name.trim();
         if (version != null && !version.isBlank()) {
            out.add(cleanName + " " + version.trim());
         }

         out.add(cleanName);
      }
   }

   private static JsonElement firstPresent(JsonObject object, String... keys) {
      if (object != null && keys != null) {
         for (String key : keys) {
            if (key != null && object.has(key)) {
               return object.get(key);
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static String firstString(JsonObject object, String... keys) {
      if (object != null && keys != null) {
         for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive()) {
               try {
                  String text = value.getAsString();
                  if (text != null && !text.isBlank()) {
                     return text.trim();
                  }
               } catch (Exception var8) {
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static int levenshtein(String a, String b, int max) {
      if (a != null && b != null) {
         if (Math.abs(a.length() - b.length()) > max) {
            return max + 1;
         } else {
            int[] prev = new int[b.length() + 1];
            int[] curr = new int[b.length() + 1];
            int j = 0;

            while (j <= b.length()) {
               prev[j] = j++;
            }

            for (int i = 1; i <= a.length(); i++) {
               curr[0] = i;
               int rowBest = curr[0];

               for (int jx = 1; jx <= b.length(); jx++) {
                  int cost = a.charAt(i - 1) == b.charAt(jx - 1) ? 0 : 1;
                  curr[jx] = Math.min(Math.min(curr[jx - 1] + 1, prev[jx] + 1), prev[jx - 1] + cost);
                  rowBest = Math.min(rowBest, curr[jx]);
               }

               if (rowBest > max) {
                  return max + 1;
               }

               int[] tmp = prev;
               prev = curr;
               curr = tmp;
            }

            return prev[b.length()];
         }
      } else {
         return max + 1;
      }
   }
}
