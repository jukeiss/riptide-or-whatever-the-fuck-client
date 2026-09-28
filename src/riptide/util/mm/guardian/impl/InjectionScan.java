package riptide.util.mm.guardian.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.security.CodeSource;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import riptide.util.RiptideDiscordLogin;
import riptide.util.mm.MatchmakingManager;
import riptide.util.mm.crypto.MmCrypto;
import riptide.util.mm.guardian.Guardian;

final class InjectionScan {
   private static final Class<?>[] CORE = new Class[]{RiptideDiscordLogin.class, MatchmakingManager.class, MmCrypto.class, Guardian.class};

   private InjectionScan() {
   }

   static JsonObject evidence() {
      JsonObject ev = new JsonObject();

      try {
         JsonArray agents = new JsonArray();

         for (String a : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            String low = a.toLowerCase(Locale.ROOT);
            if (low.startsWith("-javaagent") || low.startsWith("-agentpath") || low.startsWith("-agentlib") || low.startsWith("-xbootclasspath")) {
               agents.add(a.length() > 120 ? a.substring(0, 120) : a);
            }
         }

         ev.add("agents", agents);
      } catch (Throwable var10) {
      }

      try {
         String self = codeSource(CORE[0]);
         boolean mismatch = false;

         for (Class<?> c : CORE) {
            String cs = codeSource(c);
            if (self != null && cs != null && !self.equals(cs)) {
               mismatch = true;
               break;
            }
         }

         ev.addProperty("csMismatch", mismatch);
      } catch (Throwable var9) {
      }

      try {
         ev.addProperty("mods", FabricLoader.getInstance().getAllMods().size());
      } catch (Throwable var8) {
      }

      return ev;
   }

   private static String codeSource(Class<?> c) {
      try {
         CodeSource src = c.getProtectionDomain().getCodeSource();
         return src != null && src.getLocation() != null ? src.getLocation().toString() : null;
      } catch (Throwable var2) {
         return null;
      }
   }
}
