package riptide.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;

public final class RiptideFabricRegisterMimicry {
   private static final boolean DEBUG = Boolean.getBoolean("riptide.protector.debug");
   private static final long MIN_PLAY_TICKS_BEFORE_SYNTHESIZE = 5L;
   private static final String SCREEN_HANDLER_OPEN_SCREEN_NAMESPACE = "fabric-screen-handler-api-v1";
   private static final String SCREEN_HANDLER_OPEN_SCREEN_PATH = "open_screen";
   private static final String VOICECHAT_MOD_ID = "voicechat";
   private static final String[] VOICECHAT_CLIENT_CHANNELS = new String[]{
      "secret", "state", "states", "remove_state", "add_group", "remove_group", "joined_group", "add_category", "remove_category"
   };
   private static volatile boolean outboundRegisterSeen;
   private static volatile boolean synthesized;
   private static volatile boolean fallbackLogClaimed;
   private static volatile long playStartTick = -1L;
   private static long tickCounter;

   private RiptideFabricRegisterMimicry() {
   }

   public static void noteOutboundRegister() {
      outboundRegisterSeen = true;
   }

   static boolean claimFallbackLog() {
      if (fallbackLogClaimed) {
         return false;
      } else {
         fallbackLogClaimed = true;
         return true;
      }
   }

   public static void onClientTick(Minecraft client) {
      tickCounter++;
      ClientPacketListener listener = client == null ? null : client.getConnection();
      if (client == null || client.player == null || listener == null) {
         resetConnectionState();
      } else if (!synthesized && !outboundRegisterSeen) {
         if (RiptideProtector.isActive() && !RiptideProtector.isVanillaMode()) {
            if (playStartTick < 0L) {
               playStartTick = tickCounter;
            }

            if (tickCounter - playStartTick >= 5L) {
               List<Identifier> channels = expectedAnnouncementChannels();
               synthesized = true;
               if (!channels.isEmpty()) {
                  try {
                     listener.send(new ServerboundCustomPayloadPacket(new RegistrationPayload(RegistrationPayload.REGISTER, channels)));
                     if (DEBUG) {
                        riptide.RiptideClientAddon.LOG.debug("[RiptideProtector] Synthesized late register with {} channel(s)", channels.size());
                     }
                  } catch (Throwable var4) {
                     riptide.RiptideClientAddon.LOG.warn("[RiptideProtector] Late register send failed: {}", var4.getMessage());
                  }
               }
            }
         }
      }
   }

   private static void resetConnectionState() {
      outboundRegisterSeen = false;
      synthesized = false;
      fallbackLogClaimed = false;
      playStartTick = -1L;
   }

   static List<Identifier> expectedAnnouncementChannels() {
      Collection<Identifier> receivable;
      try {
         receivable = ClientPlayNetworking.getGlobalReceivers();
      } catch (Throwable var2) {
         if (DEBUG) {
            riptide.RiptideClientAddon.LOG.debug("[RiptideProtector] getGlobalReceivers unavailable, using stock fallback: {}", var2.getMessage());
         }

         receivable = fallbackReceivableChannels(isVoicechatLoaded());
      }

      if (receivable == null) {
         receivable = List.of();
      }

      return RiptideProtectorChannelFilter.keepWhitelisted(receivable, RiptideProtectorTracker::isWhitelistedChannel);
   }

   static List<Identifier> fallbackReceivableChannels(boolean voicechatLoaded) {
      Set<Identifier> channels = new LinkedHashSet<>();
      channels.add(Identifier.fromNamespaceAndPath("fabric-screen-handler-api-v1", "open_screen"));
      if (voicechatLoaded) {
         for (String path : VOICECHAT_CLIENT_CHANNELS) {
            channels.add(Identifier.fromNamespaceAndPath("voicechat", path));
         }
      }

      return new ArrayList<>(channels);
   }

   private static boolean isVoicechatLoaded() {
      try {
         return FabricLoader.getInstance().isModLoaded("voicechat");
      } catch (Throwable var1) {
         return false;
      }
   }

   static boolean shouldSynthesize(boolean moddedModeActive, boolean registerSeen, boolean alreadySynthesized, long ticksSincePlayStart) {
      return moddedModeActive && !registerSeen && !alreadySynthesized && ticksSincePlayStart >= 5L;
   }

   static long minPlayTicksBeforeSynthesize() {
      return 5L;
   }
}
