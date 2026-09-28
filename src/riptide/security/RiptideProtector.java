package riptide.security;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.Packet;
import riptide.util.RiptideConfig;

public final class RiptideProtector {
   private static final String OPSEC_MOD_ID = "opsec";
   private static final String EXPLOIT_PREVENTER_MOD_ID = "exploitpreventer";
   private static final boolean OPSEC_PRESENT;
   private static final boolean EXPLOIT_PREVENTER_PRESENT;
   private static final int ACTIVE = 1;
   private static final int FILTER_CHANNELS = 4;
   private static final int PROTECT_TRANSLATIONS = 8;
   private static final int DISABLE_TELEMETRY = 16;
   private static final int BLOCK_LOCAL_URLS = 32;
   private static final int ISOLATE_PACK_CACHE = 64;
   private static final int STRIP_SERVER_PACKS = 128;
   private static final int SKIP_CHAT_SIGNING = 256;
   private static final int VANILLA_MODE = 512;
   private static final RiptideProtector.RuntimeState UNINITIALIZED_STATE = new RiptideProtector.RuntimeState(false, null, 0, 0);
   private static volatile RiptideProtector.RuntimeState runtimeState = UNINITIALIZED_STATE;
   private static final int MAX_BYPASS_ENTRIES = 1024;
   private static final Map<Object, Boolean> USER_BYPASS_PACKETS;

   private RiptideProtector() {
   }

   public static boolean isExternalProtectorPresent() {
      return isFullExternalProtectorPresent();
   }

   public static boolean isFullExternalProtectorPresent() {
      return OPSEC_PRESENT;
   }

   public static boolean isOverlapExternalProtectorPresent() {
      return OPSEC_PRESENT || EXPLOIT_PREVENTER_PRESENT;
   }

   public static boolean isExploitPreventerPresent() {
      return EXPLOIT_PREVENTER_PRESENT && !OPSEC_PRESENT;
   }

   public static boolean isActive() {
      return has(1);
   }

   public static boolean isVanillaMode() {
      return has(512);
   }

   public static boolean shouldFilterChannels() {
      return has(4);
   }

   public static boolean shouldProtectTranslationKeys() {
      return has(8);
   }

   public static boolean shouldTagPacketComponents() {
      return has(8);
   }

   public static boolean shouldDisableTelemetry() {
      return has(16);
   }

   public static boolean shouldBlockLocalUrls() {
      return has(32);
   }

   public static boolean shouldIsolatePackCache() {
      return has(64);
   }

   public static boolean shouldStripServerPacks() {
      return has(128);
   }

   public static boolean shouldSkipChatSigning() {
      return has(256);
   }

   public static void refreshRuntimeState() {
      publishRuntimeState(RiptideConfig.getGlobal());
   }

   public static void publishRuntimeState(RiptideConfig config) {
      if (config == null) {
         runtimeState = UNINITIALIZED_STATE;
      } else {
         int sourceFlags = sourceFlags(config);
         RiptideProtector.RuntimeState current = runtimeState;
         if (!current.initialized || current.config != config || current.sourceFlags != sourceFlags) {
            runtimeState = new RiptideProtector.RuntimeState(
               true, config, sourceFlags, effectiveFlags(sourceFlags, OPSEC_PRESENT, isOverlapExternalProtectorPresent())
            );
         }
      }
   }

   private static boolean has(int flag) {
      return (state().effectiveFlags & flag) != 0;
   }

   private static RiptideProtector.RuntimeState state() {
      RiptideProtector.RuntimeState state = runtimeState;
      if (state.initialized) {
         return state;
      } else {
         publishRuntimeState(RiptideConfig.getGlobal());
         return runtimeState;
      }
   }

   static int sourceFlags(RiptideConfig config) {
      int flags = 0;
      if (config.protectorEnabled) {
         flags |= 1;
      }

      if (config.protectorFilterChannels) {
         flags |= 4;
      }

      if (config.protectorTranslationProtection) {
         flags |= 8;
      }

      if (config.protectorDisableTelemetry) {
         flags |= 16;
      }

      if (config.protectorBlockLocalUrls) {
         flags |= 32;
      }

      if (config.protectorIsolatePackCache) {
         flags |= 64;
      }

      if (config.protectorStripServerPacks) {
         flags |= 128;
      }

      if (config.protectorChatSigningOff) {
         flags |= 256;
      }

      if (config.spoofClientVanilla) {
         flags |= 512;
      }

      return flags;
   }

   static int effectiveFlags(int sourceFlags, boolean fullExternalProtector, boolean overlapExternalProtector) {
      if (!fullExternalProtector && (sourceFlags & 1) != 0) {
         int flags = sourceFlags;
         if (overlapExternalProtector) {
            flags = sourceFlags & -105;
         }

         return flags;
      } else {
         return 0;
      }
   }

   static Object runtimeStateIdentityForTests() {
      return runtimeState;
   }

   public static void markUserBypass(Packet<?> packet) {
      if (packet != null) {
         synchronized (USER_BYPASS_PACKETS) {
            if (USER_BYPASS_PACKETS.size() >= 1024) {
               Iterator<Entry<Object, Boolean>> it = USER_BYPASS_PACKETS.entrySet().iterator();
               if (it.hasNext()) {
                  it.next();
                  it.remove();
               }
            }

            USER_BYPASS_PACKETS.put(packet, Boolean.TRUE);
         }
      }
   }

   public static boolean isUserBypass(Packet<?> packet) {
      return packet == null ? false : USER_BYPASS_PACKETS.containsKey(packet);
   }

   public static void consumeUserBypass(Packet<?> packet) {
      if (packet != null) {
         USER_BYPASS_PACKETS.remove(packet);
      }
   }

   static {
      boolean opsec = false;
      boolean exploitPreventer = false;

      try {
         FabricLoader loader = FabricLoader.getInstance();
         opsec = loader.isModLoaded("opsec");
         exploitPreventer = loader.isModLoaded("exploitpreventer");
      } catch (Throwable var3) {
      }

      OPSEC_PRESENT = opsec;
      EXPLOIT_PREVENTER_PRESENT = exploitPreventer;
      USER_BYPASS_PACKETS = Collections.synchronizedMap(new IdentityHashMap<>());
   }

   private record RuntimeState(boolean initialized, RiptideConfig config, int sourceFlags, int effectiveFlags) {
   }
}
