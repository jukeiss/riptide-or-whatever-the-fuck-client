package riptide.util;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class RiptideRegistryLabels {
   private static final Map<String, String> ITEM_CACHE = new ConcurrentHashMap<>();
   private static final Map<String, String> BLOCK_CACHE = new ConcurrentHashMap<>();
   private static final Map<String, String> ENTITY_CACHE = new ConcurrentHashMap<>();
   private static final Map<String, String> IDENTIFIER_CACHE = new ConcurrentHashMap<>();

   private RiptideRegistryLabels() {
   }

   public static String item(String rawId) {
      String key = normalizedKey(rawId);
      return ITEM_CACHE.computeIfAbsent(key, id -> registryLabel(id, BuiltInRegistries.ITEM, item -> item.getName(item.getDefaultInstance()).getString()));
   }

   public static String block(String rawId) {
      String key = normalizedKey(rawId);
      return BLOCK_CACHE.computeIfAbsent(key, id -> registryLabel(id, BuiltInRegistries.BLOCK, block -> block.getName().getString()));
   }

   public static String entity(String rawId) {
      String key = normalizedKey(rawId);
      return ENTITY_CACHE.computeIfAbsent(key, id -> registryLabel(id, BuiltInRegistries.ENTITY_TYPE, entity -> entity.getDescription().getString()));
   }

   public static String sound(String rawId) {
      return identifier(rawId);
   }

   public static String identifier(String rawId) {
      String key = normalizedKey(rawId);
      return IDENTIFIER_CACHE.computeIfAbsent(key, RiptideRegistryLabels::humanizeIdentifier);
   }

   public static String stripNamespace(String rawId) {
      String key = normalizedKey(rawId);
      if (key.isEmpty()) {
         return "";
      } else {
         Identifier parsed = Identifier.tryParse(key);
         return parsed == null ? key : parsed.getPath();
      }
   }

   public static boolean looksLikeRawIdentifierLabel(String text, String rawId) {
      String trimmed = text == null ? "" : text.trim();
      String key = normalizedKey(rawId);
      return trimmed.isEmpty()
         ? true
         : trimmed.equalsIgnoreCase(key) || trimmed.equalsIgnoreCase(stripNamespace(key)) || trimmed.equalsIgnoreCase(identifier(key));
   }

   private static <T> String registryLabel(String rawId, Registry<T> registry, Function<T, String> nameProvider) {
      if (rawId.isEmpty()) {
         return "";
      } else {
         Identifier parsed = Identifier.tryParse(rawId);
         String fallback = humanizeIdentifier(rawId);

         try {
            if (parsed != null && registry.containsKey(parsed)) {
               T value = (T)registry.getValue(parsed);
               if (value == null) {
                  return fallback;
               } else {
                  String resolved = nameProvider.apply(value);
                  return resolved != null && !resolved.isBlank() ? resolved : fallback;
               }
            } else {
               return fallback;
            }
         } catch (Throwable var7) {
            return fallback;
         }
      }
   }

   private static String humanizeIdentifier(String rawId) {
      if (rawId.isEmpty()) {
         return "";
      } else {
         Identifier parsed = Identifier.tryParse(rawId);
         if (parsed == null) {
            return humanizeWords(rawId);
         } else {
            String pathLabel = humanizeWords(parsed.getPath());
            if ("minecraft".equals(parsed.getNamespace())) {
               return pathLabel;
            } else {
               String namespaceLabel = humanizeWords(parsed.getNamespace());
               return namespaceLabel.isEmpty() ? pathLabel : namespaceLabel + ": " + pathLabel;
            }
         }
      }
   }

   private static String humanizeWords(String raw) {
      if (raw != null && !raw.isBlank()) {
         String cleaned = raw.trim().replaceAll("[:_/.-]+", " ").replaceAll("\\s+", " ");
         if (cleaned.isEmpty()) {
            return raw.trim();
         } else {
            StringBuilder out = new StringBuilder(cleaned.length());

            for (String part : cleaned.split(" ")) {
               if (!part.isEmpty()) {
                  if (out.length() > 0) {
                     out.append(' ');
                  }

                  out.append(humanizeToken(part));
               }
            }

            return out.toString();
         }
      } else {
         return "";
      }
   }

   private static String humanizeToken(String token) {
      if (token.isEmpty()) {
         return token;
      } else {
         return token.length() <= 3 && token.equals(token.toUpperCase(Locale.ROOT))
            ? token
            : Character.toUpperCase(token.charAt(0)) + token.substring(1).toLowerCase(Locale.ROOT);
      }
   }

   private static String normalizedKey(String rawId) {
      return rawId == null ? "" : rawId.trim();
   }
}
