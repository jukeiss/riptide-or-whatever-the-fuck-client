package riptide.security;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;

public final class RiptideProtectorVanillaKeys {
   private static final boolean DEBUG = Boolean.getBoolean("riptide.protector.debug");
   private static final Object LOCK = new Object();
   private static volatile Set<String> KEYS;

   private RiptideProtectorVanillaKeys() {
   }

   public static boolean contains(String key) {
      return key != null && !key.isEmpty() ? ensureLoaded().contains(key) : false;
   }

   public static boolean isLoaded() {
      return KEYS != null;
   }

   private static Set<String> ensureLoaded() {
      Set<String> current = KEYS;
      if (current != null) {
         return current;
      } else {
         synchronized (LOCK) {
            if (KEYS == null) {
               KEYS = load();
            }

            return KEYS;
         }
      }
   }

   private static Set<String> load() {
      try {
         Set var10;
         try (InputStream in = Minecraft.class.getResourceAsStream("/assets/minecraft/lang/en_us.json")) {
            if (in == null) {
               riptide.RiptideClientAddon.LOG.warn("[RiptideProtector] vanilla en_us.json not on classpath; falling back to blocklist only.");
               return Collections.emptySet();
            }

            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Set<String> out = new HashSet<>(root.size() * 2);

            for (Entry<String, JsonElement> entry : root.entrySet()) {
               JsonElement value = entry.getValue();
               if (value != null && value.isJsonPrimitive()) {
                  out.add(entry.getKey());
               }
            }

            if (DEBUG) {
               riptide.RiptideClientAddon.LOG.debug("[RiptideProtector] Loaded {} vanilla translation keys.", out.size());
            }

            var10 = Collections.unmodifiableSet(out);
         }

         return var10;
      } catch (Exception var8) {
         riptide.RiptideClientAddon.LOG.warn("[RiptideProtector] Failed to load vanilla en_us.json: {}", var8.getMessage());
         return Collections.emptySet();
      }
   }

   public static void primeAsync() {
      Thread t = new Thread(RiptideProtectorVanillaKeys::ensureLoaded, "RiptideProtector-Vanilla-Keys");
      t.setDaemon(true);
      t.start();
   }
}
