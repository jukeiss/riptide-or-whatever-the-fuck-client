package riptide.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.network.protocol.Packet;

public class RiptidePacketNamer {
   private static final String UNKNOWN_PACKET = "Unknown Packet";
   private static final ConcurrentMap<Class<?>, String> CLASS_NAME_CACHE = new ConcurrentHashMap<>();
   private static final ConcurrentMap<String, String> DIRECTIONAL_CLASS_NAME_CACHE = new ConcurrentHashMap<>();
   private static final ConcurrentMap<Class<?>, String> S2C_NAME_CACHE = new ConcurrentHashMap<>();
   private static final ConcurrentMap<Class<?>, String> C2S_NAME_CACHE = new ConcurrentHashMap<>();
   private static final ConcurrentMap<String, String> NORMALIZED_CACHE = new ConcurrentHashMap<>();
   private static final Map<String, String> MANUAL_NAME_ALIASES = createManualNameAliases();

   private static String stripSuffix(String name) {
      return name != null && name.endsWith("Packet") ? name.substring(0, name.length() - 6) : name;
   }

   public static String getFriendlyName(Packet<?> packet) {
      Class<? extends Packet<?>> packetClass = packet.getClass();
      String cached = CLASS_NAME_CACHE.get(packetClass);
      if (cached != null) {
         return cached;
      } else {
         String name = resolveRegistryName(packetClass);
         if (name != null) {
            return cache(packetClass, stripSuffix(name));
         } else {
            String typeName = resolveNameFromPacketType(packet);
            if (typeName != null) {
               return cache(packetClass, stripSuffix(typeName));
            } else {
               String inspected = inspectObfuscatedPacket(packet, packetClass.getSimpleName());
               if (inspected != null) {
                  return cache(packetClass, inspected);
               } else {
                  String readable = deriveReadableClassName(packetClass);
                  return cache(packetClass, readable != null ? readable : "Unknown Packet");
               }
            }
         }
      }
   }

   public static String getFriendlyName(Packet<?> packet, String direction) {
      Class<? extends Packet<?>> packetClass = packet.getClass();
      ConcurrentMap<Class<?>, String> classCache = directionalClassCache(direction);
      if (classCache != null) {
         String fast = classCache.get(packetClass);
         if (fast != null) {
            return fast;
         }
      }

      String cacheKey = directionalKey(packetClass, direction);
      String cached = DIRECTIONAL_CLASS_NAME_CACHE.get(cacheKey);
      if (cached != null) {
         return cacheDirectional(classCache, packetClass, cached);
      } else {
         String name = resolveRegistryName(packetClass, direction);
         if (name != null) {
            return cacheDirectional(classCache, packetClass, cache(cacheKey, stripSuffix(name)));
         } else {
            String typeName = resolveNameFromPacketType(packet, direction);
            if (typeName != null) {
               return cacheDirectional(classCache, packetClass, cache(cacheKey, stripSuffix(typeName)));
            } else {
               String inspected = inspectObfuscatedPacket(packet, packetClass.getSimpleName());
               if (inspected != null) {
                  return cacheDirectional(classCache, packetClass, cache(cacheKey, inspected));
               } else {
                  String readable = deriveReadableClassName(packetClass);
                  return cacheDirectional(classCache, packetClass, cache(cacheKey, readable != null ? readable : "Unknown Packet"));
               }
            }
         }
      }
   }

   private static ConcurrentMap<Class<?>, String> directionalClassCache(String direction) {
      if ("S2C".equalsIgnoreCase(direction)) {
         return S2C_NAME_CACHE;
      } else {
         return "C2S".equalsIgnoreCase(direction) ? C2S_NAME_CACHE : null;
      }
   }

   private static String cacheDirectional(ConcurrentMap<Class<?>, String> classCache, Class<?> packetClass, String value) {
      if (classCache != null && value != null) {
         classCache.putIfAbsent(packetClass, value);
      }

      return value;
   }

   public static String getFriendlyName(Class<? extends Packet<?>> packetClass) {
      String cached = CLASS_NAME_CACHE.get(packetClass);
      if (cached != null) {
         return cached;
      } else {
         String name = resolveRegistryName(packetClass);
         if (name != null) {
            return cache(packetClass, stripSuffix(name));
         } else {
            String readable = deriveReadableClassName(packetClass);
            return cache(packetClass, readable != null ? readable : "Unknown Packet");
         }
      }
   }

   private static String cache(Class<?> packetClass, String value) {
      String safe = value == null ? "Unknown Packet" : value;
      if (packetClass != null) {
         CLASS_NAME_CACHE.putIfAbsent(packetClass, safe);
      }

      return safe;
   }

   private static String cache(String key, String value) {
      String safe = value == null ? "Unknown Packet" : value;
      if (key != null) {
         DIRECTIONAL_CLASS_NAME_CACHE.putIfAbsent(key, safe);
      }

      return safe;
   }

   private static String directionalKey(Class<?> packetClass, String direction) {
      String dir = direction == null ? "" : direction.toUpperCase(Locale.ROOT);
      return dir + "|" + (packetClass == null ? "" : packetClass.getName());
   }

   public static String getFriendlyName(String name) {
      if (name != null && !name.isEmpty()) {
         Class<? extends Packet<?>> packetClass = RiptidePacketRegistry.getPacket(name);
         if (packetClass != null) {
            String resolved = resolveRegistryName(packetClass);
            return resolved != null ? stripSuffix(resolved) : "Unknown Packet";
         } else {
            String resolved = resolveNameFromText(name);
            if (resolved != null) {
               return stripSuffix(resolved);
            } else {
               String readable = prettifyRawName(name);
               return readable != null ? readable : "Unknown Packet";
            }
         }
      } else {
         return "";
      }
   }

   private static String resolveRegistryName(Class<? extends Packet<?>> packetClass) {
      if (packetClass == null) {
         return null;
      } else {
         String direct = RiptidePacketRegistry.getName(packetClass);
         if (direct != null) {
            return direct;
         } else {
            for (Class<? extends Packet<?>> registeredClass : RiptidePacketRegistry.PACKETS) {
               if (registeredClass == packetClass || registeredClass.isAssignableFrom(packetClass)) {
                  String name = RiptidePacketRegistry.getName(registeredClass);
                  if (name != null) {
                     return name;
                  }
               }
            }

            String byText = resolveNameFromText(packetClass.getName());
            return byText != null ? byText : resolveNameFromText(packetClass.getCanonicalName());
         }
      }
   }

   private static String resolveRegistryName(Class<? extends Packet<?>> packetClass, String direction) {
      if (packetClass == null) {
         return null;
      } else {
         String direct = resolveDirectionalRegistryName(packetClass, direction);
         if (direct != null) {
            return direct;
         } else {
            String byText = resolveNameFromText(packetClass.getName(), direction);
            return byText != null ? byText : resolveNameFromText(packetClass.getCanonicalName(), direction);
         }
      }
   }

   private static String resolveNameFromPacketType(Packet<?> packet) {
      if (packet == null) {
         return null;
      } else {
         try {
            Object packetType = packet.getClass().getMethod("getPacketType").invoke(packet);
            return packetType == null ? null : resolveNameFromText(packetType.toString());
         } catch (Throwable var2) {
            return null;
         }
      }
   }

   private static String resolveNameFromPacketType(Packet<?> packet, String direction) {
      if (packet == null) {
         return null;
      } else {
         try {
            Object packetType = packet.getClass().getMethod("getPacketType").invoke(packet);
            return packetType == null ? null : resolveNameFromText(packetType.toString(), direction);
         } catch (Throwable var3) {
            return null;
         }
      }
   }

   private static String resolveNameFromText(String text) {
      if (text != null && !text.isEmpty()) {
         String normalizedText = normalizePacketKey(text);
         if (normalizedText.isEmpty()) {
            return null;
         } else {
            String manual = MANUAL_NAME_ALIASES.get(normalizedText);
            if (manual != null) {
               return manual;
            } else {
               for (Class<? extends Packet<?>> registeredClass : RiptidePacketRegistry.PACKETS) {
                  String registeredName = RiptidePacketRegistry.getName(registeredClass);
                  if (registeredName != null && matchesRegisteredName(normalizedText, registeredName, registeredClass)) {
                     return registeredName;
                  }
               }

               return null;
            }
         }
      } else {
         return null;
      }
   }

   private static String resolveNameFromText(String text, String direction) {
      if (text != null && !text.isEmpty()) {
         String normalizedText = normalizePacketKey(text);
         if (normalizedText.isEmpty()) {
            return null;
         } else {
            String manual = MANUAL_NAME_ALIASES.get(normalizedText);
            if (manual != null) {
               return manual;
            } else {
               for (Class<? extends Packet<?>> registeredClass : getDirectionalRegistry(direction)) {
                  String registeredName = RiptidePacketRegistry.getName(registeredClass);
                  if (registeredName != null && matchesRegisteredName(normalizedText, registeredName, registeredClass)) {
                     return registeredName;
                  }
               }

               return null;
            }
         }
      } else {
         return null;
      }
   }

   private static String resolveDirectionalRegistryName(Class<? extends Packet<?>> packetClass, String direction) {
      for (Class<? extends Packet<?>> registeredClass : getDirectionalRegistry(direction)) {
         if (registeredClass == packetClass) {
            return RiptidePacketRegistry.getName(registeredClass);
         }
      }

      return null;
   }

   private static Iterable<Class<? extends Packet<?>>> getDirectionalRegistry(String direction) {
      if ("C2S".equalsIgnoreCase(direction)) {
         return RiptidePacketRegistry.getC2SPackets();
      } else {
         return "S2C".equalsIgnoreCase(direction) ? RiptidePacketRegistry.getS2CPackets() : RiptidePacketRegistry.PACKETS;
      }
   }

   private static boolean matchesRegisteredName(String normalizedText, String registeredName, Class<? extends Packet<?>> registeredClass) {
      String normalizedRegistered = normalizePacketKey(registeredName);
      if (normalizedRegistered.equals(normalizedText)) {
         return true;
      } else {
         String strippedRegistered = normalizePacketKey(stripSuffix(registeredName));
         if (strippedRegistered.equals(normalizedText)) {
            return true;
         } else {
            String className = normalizePacketKey(registeredClass.getName());
            if (!className.equals(normalizedText) && !normalizedText.endsWith(className)) {
               String simpleName = normalizePacketKey(registeredClass.getSimpleName());
               if (!simpleName.equals(normalizedText) && !normalizedText.endsWith(simpleName)) {
                  String canonicalName = normalizePacketKey(registeredClass.getCanonicalName());
                  return canonicalName.isEmpty() || !canonicalName.equals(normalizedText) && !normalizedText.endsWith(canonicalName)
                     ? normalizedText.endsWith(normalizedRegistered) || normalizedText.endsWith(strippedRegistered)
                     : true;
               } else {
                  return true;
               }
            } else {
               return true;
            }
         }
      }
   }

   private static Map<String, String> createManualNameAliases() {
      Map<String, String> aliases = new HashMap<>();
      registerManualAlias(
         aliases,
         "BundleS2CPacket",
         "BundleS2C",
         "class_8042",
         "net.minecraft.class_8042",
         "net.minecraft.network.protocol.game.BundleS2CPacket",
         "net.minecraft.network.protocol.game.ClientboundBundlePacket"
      );
      return Collections.unmodifiableMap(aliases);
   }

   private static void registerManualAlias(Map<String, String> aliases, String canonicalName, String... variants) {
      aliases.put(normalizePacketKey(canonicalName), canonicalName);
      aliases.put(normalizePacketKey(stripSuffix(canonicalName)), canonicalName);

      for (String variant : variants) {
         if (variant != null && !variant.isEmpty()) {
            aliases.put(normalizePacketKey(variant), canonicalName);
         }
      }
   }

   private static String deriveReadableClassName(Class<?> packetClass) {
      if (packetClass == null) {
         return null;
      } else {
         String canonical = prettifyRawName(packetClass.getCanonicalName());
         return canonical != null ? canonical : prettifyRawName(packetClass.getName());
      }
   }

   private static String prettifyRawName(String rawName) {
      if (rawName != null && !rawName.isEmpty()) {
         int lastDot = rawName.lastIndexOf(46);
         String shortName = lastDot >= 0 ? rawName.substring(lastDot + 1) : rawName;
         shortName = shortName.replace('$', '.');
         return isObfuscatedName(shortName) ? null : stripSuffix(shortName);
      } else {
         return null;
      }
   }

   private static boolean isObfuscatedName(String name) {
      String normalized = normalizePacketKey(name);
      return normalized.startsWith("class") && normalized.length() > 5;
   }

   public static String normalizePacketKey(String value) {
      if (value != null && !value.isEmpty()) {
         if (NORMALIZED_CACHE.size() > 4096) {
            NORMALIZED_CACHE.clear();
         }

         return NORMALIZED_CACHE.computeIfAbsent(value, RiptidePacketNamer::normalizePacketKeyUncached);
      } else {
         return "";
      }
   }

   private static String normalizePacketKeyUncached(String value) {
      String lower = value.toLowerCase(Locale.ROOT);
      StringBuilder out = new StringBuilder(lower.length());

      for (int i = 0; i < lower.length(); i++) {
         char c = lower.charAt(i);
         if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
            out.append(c);
         }
      }

      return out.toString();
   }

   private static String inspectObfuscatedPacket(Packet<?> packet, String className) {
      try {
         String packetStr = packet.toString();
         if (!packetStr.contains("slot=") && !packetStr.contains("Slot")) {
            if (packetStr.contains("hand=") || packetStr.contains("Hand")) {
               return "Swing Hand";
            } else if (packetStr.contains("button=") || packetStr.contains("Button")) {
               return "Click Button";
            } else if (!packetStr.contains("action=") && !packetStr.contains("Action")) {
               return null;
            } else if (packetStr.contains("DROP_ITEM") || packetStr.contains("drop")) {
               return "Drop Item";
            } else if (packetStr.contains("DROP_ALL") || packetStr.contains("drop_all")) {
               return "Drop Stack";
            } else {
               return !packetStr.contains("SWAP") && !packetStr.contains("swap") ? "Player Action" : "Swap Hands";
            }
         } else {
            return !packetStr.contains("button=") && !packetStr.contains("Button") ? "Slot Action" : "Click Slot";
         }
      } catch (Exception var3) {
         return null;
      }
   }
}
