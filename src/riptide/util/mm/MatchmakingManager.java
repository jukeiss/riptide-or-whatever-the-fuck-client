package riptide.util.mm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.invoke.StringConcatFactory;
import java.security.PublicKey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import riptide.commands.RiptideCommands;
import riptide.modules.PackHideState;
import riptide.util.RiptideDiscordLogin;
import riptide.util.RiptideHttp;
import riptide.util.RiptideNotifications;
import riptide.util.mm.crypto.MmCrypto;
import riptide.util.mm.crypto.MmIdentity;
import riptide.util.mm.crypto.MmSession;
import riptide.util.mm.crypto.RoomKey;
import riptide.util.mm.msg.MmMessages;
import riptide.util.mm.relay.MqttRelay;
import riptide.util.mm.relay.Relay;
import riptide.util.mm.relay.RelayManager;
import riptide.util.mm.relay.RelayStatus;

public final class MatchmakingManager {
   private static final MatchmakingManager INSTANCE = new MatchmakingManager();
   private static final int MAX_CHAT_LINES = 300;
   private static final long UI_CACHE_MS = 250L;
   private static final long PRESENCE_INTERVAL_MS = 8000L;
   private static final long PEER_TIMEOUT_MS = 60000L;
   private static final long JOIN_RESPONSE_MS = 10000L;
   private static final int MAX_PEERS = 2000;
   private static final int MAX_DIRECTORY = 2000;
   private final MmIdentity identity = MmIdentity.get();
   private final MmPrefs prefs = MmPrefs.get();
   private final MmSafety safety = new MmSafety();
   private final String selfFpHex = this.identity.fingerprint().replace("-", "").toLowerCase(Locale.ROOT);
   private final Map<String, MmPeer> peers = new ConcurrentHashMap<>();
   private final Map<String, LobbyListing> directory = new ConcurrentHashMap<>();
   private static final String DIRECTORY_URL = "                                ";
   private static final long DIR_POLL_MS = 6000L;
   private static final long DIR_ANNOUNCE_MS = 10000L;
   private ScheduledExecutorService directoryHttp;
   private volatile long lastHttpAnnounceMs;
   private final Deque<MmChatLine> chat = new ArrayDeque<>();
   private volatile List<MmPeer> cachedMembers = List.of();
   private volatile long cachedMembersAtMs;
   private volatile List<LobbyListing> cachedDirectory = List.of();
   private volatile long cachedDirectoryAtMs;
   private volatile int chatVersion;
   private volatile RelayManager relays;
   private volatile Lobby lobby;
   private volatile MmSession session;
   private volatile boolean directoryOpen;
   private volatile long lastImmediatePollMs;
   private volatile long lastPresenceMs;
   private volatile long lastPeerReplyMs;
   private volatile String lastPresenceNick = "";
   private volatile long joinedAtMs;
   private volatile boolean joinResponsePending;
   private volatile boolean aclReconnectPending;
   private volatile MatchmakingManager.JoinPhase joinPhase = MatchmakingManager.JoinPhase.NONE;
   private volatile String joinError = "";
   private volatile boolean autoJoinAttempted;
   private volatile boolean dirRefreshing;
   private volatile long lastDirPullMs;
   private volatile boolean connectivitySeen;
   private volatile boolean lastConnectivity;
   private volatile long engagedSinceMs;
   private volatile boolean warnedNoConnect;
   private volatile long connLostSinceMs;
   private volatile boolean warnedOffline;
   private volatile long lastDiagLogMs;
   private static final long CONN_TOAST_DEBOUNCE_MS = 10000L;
   private volatile long gateOfflineSinceMs;
   private volatile boolean gateEverOnline;
   private static final long CONNECT_GRACE_MS = 12000L;
   private static final long CONNECT_FAST_FAIL_MS = 2000L;
   private static final long IDLE_RELAY_CLOSE_MS = 15000L;
   private ScheduledExecutorService scheduler;
   private ScheduledFuture<?> idleRelayClose;
   private long idleRelayGeneration;
   private volatile byte[] currentKey;
   private volatile int keyEpoch;
   private volatile List<MatchmakingManager.KeyEpoch> oldKeys = List.of();
   private static final int KEY_HISTORY = 2;
   private volatile long lastServerRefreshMs;
   private volatile long rosterVersion;
   private volatile boolean rosterSeeded;
   private volatile long selfEchoOk;
   private volatile long selfEchoBad;
   private static final long RECEIPT_TTL_MS = 60000L;
   private static final int RECEIPT_TRACK_MAX = 64;
   private final Map<String, MatchmakingManager.SentContent> awaitingReceipts = new ConcurrentHashMap<>();
   private final Set<String> droppedFps = ConcurrentHashMap.newKeySet();
   private final Set<String> kickedFromLobbies = ConcurrentHashMap.newKeySet();
   private final Set<String> officialLobbyIds = ConcurrentHashMap.newKeySet();
   private final Map<String, String> fpToDid = new ConcurrentHashMap<>();
   private final Set<String> mutedFps = ConcurrentHashMap.newKeySet();
   private volatile int hostTerm;
   private volatile String hostFp = "";
   private volatile List<MatchmakingManager.RosterEntry> serverRoster = List.of();
   private volatile long lastAnnounceOkMs;
   private static final String BROKER_URL = "                         ";
   private final AtomicBoolean shutdownLeaveSent = new AtomicBoolean();
   private static final long MIN_USER_SEND_MS = 1000L;
   private volatile long lastUserSendMs;

   public static MatchmakingManager get() {
      return INSTANCE;
   }

   private static RiptideHttp.JsonResult directoryPost(String endpoint, JsonObject body) {
      return directoryPost(endpoint, body, 15000);
   }

   private static RiptideHttp.JsonResult directoryPost(String endpoint, JsonObject body, int timeoutMs) {
      String suffix = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
      String json = body == null ? "{}" : body.toString();
      return RiptideHttp.postJsonResult(
         StringConcatFactory.makeConcatWithConstants<"makeConcatWithConstants","                                 ">(suffix),
         json,
         timeoutMs,
         RiptideDiscordLogin.proofHeaders("POST", "/lobbies" + suffix, json)
      );
   }

   private static JsonObject directoryGet(String jwt) {
      return RiptideHttp.getJson("                                ", jwt, RiptideDiscordLogin.proofHeaders("GET", "/lobbies", ""));
   }

   private MatchmakingManager() {
   }

   public MmPrefs prefs() {
      return this.prefs;
   }

   public MmSafety safety() {
      return this.safety;
   }

   public Lobby currentLobby() {
      return this.lobby;
   }

   public boolean inLobby() {
      return this.lobby != null;
   }

   public String currentShareCode() {
      Lobby lb = this.lobby;
      return lb == null ? "" : lb.shareCode;
   }

   public String selfFingerprint() {
      return this.identity.fingerprint();
   }

   public String selfFpHex() {
      return this.selfFpHex;
   }

   public boolean isHost() {
      return this.isActingHost();
   }

   public boolean canSend() {
      Lobby lb = this.lobby;
      return lb != null && (!lb.announcement || lb.canSend);
   }

   public boolean canManageSpeakers() {
      Lobby lb = this.lobby;
      return lb != null && lb.announcement && RiptideDiscordLogin.isAdmin() && lb.ownerDid.equals(RiptideDiscordLogin.currentDid());
   }

   public String actingHostFp() {
      Lobby lb = this.lobby;
      return lb == null ? "" : this.hostFp;
   }

   private boolean isActingHost() {
      return this.lobby != null && !this.hostFp.isEmpty() && this.hostFp.equals(this.selfFpHex);
   }

   public boolean currentLobbyOfficial() {
      Lobby lb = this.lobby;
      return lb != null && this.officialLobbyIds.contains(lb.lobbyId);
   }

   public boolean effectiveShareServer() {
      Lobby lb = this.lobby;
      return lb != null && this.officialLobbyIds.contains(lb.lobbyId) ? this.prefs.lobbyShareServer(lb.lobbyId) : this.prefs.shareServer();
   }

   public boolean effectiveShareLocation() {
      Lobby lb = this.lobby;
      return lb != null && this.officialLobbyIds.contains(lb.lobbyId) ? this.prefs.lobbyShareLocation(lb.lobbyId) : this.prefs.shareLocation();
   }

   public boolean serverLinkHealthy() {
      return this.lobby == null || System.currentTimeMillis() - this.lastAnnounceOkMs < 25000L;
   }

   private boolean eligibleHost(String fp) {
      return fp != null && !this.droppedFps.contains(fp);
   }

   public static String currentServerIp() {
      try {
         ServerData sd = Minecraft.getInstance().getCurrentServer();
         return sd != null && sd.ip != null && !sd.ip.isBlank() ? sd.ip.trim() : null;
      } catch (Throwable var1) {
         return null;
      }
   }

   public static boolean sameServerAs(MmPeer peer) {
      if (peer != null && peer.serverShared && peer.serverIp != null && !peer.serverIp.isBlank()) {
         String mine = currentServerIp();
         return mine != null && mine.equalsIgnoreCase(peer.serverIp.trim());
      } else {
         return false;
      }
   }

   public static boolean alreadyOn(String ip) {
      if (ip != null && !ip.isBlank()) {
         String mine = currentServerIp();
         return mine != null && mine.equalsIgnoreCase(ip.trim());
      } else {
         return false;
      }
   }

   public List<RelayStatus> relayStatuses() {
      RelayManager rm = this.relays;
      return rm == null ? List.of() : rm.statuses();
   }

   public String censusSummary() {
      RelayManager rm = this.relays;
      if (rm == null) {
         return "idle";
      } else {
         int connected = 0;
         List<RelayStatus> statuses = rm.statuses();

         for (RelayStatus status : statuses) {
            if (status.connected) {
               connected++;
            }
         }

         return "lobby=" + this.inLobby() + " relays=" + connected + "/" + statuses.size();
      }
   }

   public MatchmakingManager.ConnState connState() {
      if (this.relays == null) {
         return MatchmakingManager.ConnState.CONNECTING;
      } else {
         long now = System.currentTimeMillis();
         if (this.relayUp()) {
            this.gateEverOnline = true;
            this.gateOfflineSinceMs = 0L;
            return MatchmakingManager.ConnState.ONLINE;
         } else {
            if (this.gateOfflineSinceMs == 0L) {
               this.gateOfflineSinceMs = now;
            }

            long offlineFor = now - this.gateOfflineSinceMs;
            if (!this.gateEverOnline && offlineFor >= 2000L && !this.connError().isEmpty()) {
               return MatchmakingManager.ConnState.OFFLINE;
            } else {
               return offlineFor < 12000L ? MatchmakingManager.ConnState.CONNECTING : MatchmakingManager.ConnState.OFFLINE;
            }
         }
      }
   }

   public boolean matchmakingReady() {
      MatchmakingManager.ConnState s = this.connState();
      return s == MatchmakingManager.ConnState.ONLINE || this.gateEverOnline && s == MatchmakingManager.ConnState.CONNECTING;
   }

   public String connError() {
      RelayManager rm = this.relays;
      if (rm == null) {
         return "";
      } else {
         for (RelayStatus s : rm.statuses()) {
            if (s.lastError != null && !s.lastError.isEmpty()) {
               return s.lastError;
            }
         }

         return "";
      }
   }

   public void reconnectNow() {
      this.gateOfflineSinceMs = 0L;
      this.ensureRelays().reconnectAll();
   }

   public void closeRelays() {
      RelayManager rm = this.relays;
      if (rm != null) {
         rm.closeAll();
      }
   }

   public MatchmakingManager.JoinPhase joinPhase() {
      return this.joinPhase;
   }

   public String joinError() {
      return this.joinError;
   }

   public boolean directoryRefreshing() {
      return this.dirRefreshing;
   }

   public long lastDirectoryPullMs() {
      return this.lastDirPullMs;
   }

   public synchronized void cancelJoin() {
      this.joinPhase = MatchmakingManager.JoinPhase.NONE;
      this.joinError = "";
      if (this.lobby != null) {
         this.leave();
      } else {
         this.closeIdleRelays();
      }
   }

   public MmPeer peer(String fpHex) {
      if (fpHex == null) {
         return null;
      } else {
         MmPeer live = this.peers.get(fpHex);
         if (live != null) {
            return live;
         } else {
            for (MatchmakingManager.RosterEntry e : this.serverRoster) {
               if (fpHex.equalsIgnoreCase(e.fp())) {
                  byte[] fp = hexToBytes(e.fp());
                  if (fp == null) {
                     return null;
                  }

                  MmPeer rosterPeer = new MmPeer(fp);
                  rosterPeer.discordId = e.did();
                  rosterPeer.discordUser = MmText.clean(e.name(), 32);
                  rosterPeer.admin = e.admin();
                  rosterPeer.speaker = e.speaker();
                  rosterPeer.role = e.role();
                  return rosterPeer;
               }
            }

            return null;
         }
      }
   }

   public List<MmPeer> members() {
      long now = System.currentTimeMillis();
      List<MmPeer> cache = this.cachedMembers;
      if (cache != null && now - this.cachedMembersAtMs < 250L) {
         return cache;
      } else {
         List<MatchmakingManager.RosterEntry> roster = this.serverRoster;
         List<MmPeer> out;
         if (this.lobby != null && !roster.isEmpty()) {
            out = new ArrayList<>(roster.size());

            for (MatchmakingManager.RosterEntry e : roster) {
               String fp = e.fp() == null ? "" : e.fp().toLowerCase(Locale.ROOT);
               if (!fp.equals(this.selfFpHex)) {
                  MmPeer live = fp.isEmpty() ? null : this.peers.get(fp);
                  if (live != null) {
                     String discordName = MmText.clean(e.name(), 32);
                     if (!discordName.isBlank()) {
                        live.discordUser = discordName;
                     }

                     if (e.did() != null && !e.did().isBlank()) {
                        live.discordId = e.did();
                     }

                     live.admin = e.admin();
                     live.speaker = e.speaker();
                     live.role = e.role();
                     out.add(live);
                  } else {
                     byte[] fpb = hexToBytes(fp);
                     MmPeer ghost = new MmPeer(fpb != null ? fpb : new byte[8]);
                     ghost.discordUser = MmText.clean(e.name(), 32);
                     ghost.discordId = e.did() == null ? "" : e.did();
                     ghost.admin = e.admin();
                     ghost.speaker = e.speaker();
                     ghost.role = e.role();
                     out.add(ghost);
                  }
               }
            }
         } else {
            out = new ArrayList<>(this.peers.values());
            out.removeIf(p -> now - p.lastSeenMs > 60000L);
         }

         out.sort(Comparator.comparing(MmPeer::displayName, String.CASE_INSENSITIVE_ORDER));
         this.cachedMembers = out;
         this.cachedMembersAtMs = now;
         return out;
      }
   }

   public int memberCount() {
      List<MatchmakingManager.RosterEntry> roster = this.serverRoster;
      if (this.lobby != null && !roster.isEmpty()) {
         return roster.size();
      } else {
         long now = System.currentTimeMillis();
         int live = 0;

         for (MmPeer p : this.peers.values()) {
            if (now - p.lastSeenMs <= 60000L) {
               live++;
            }
         }

         return live + (this.lobby != null ? 1 : 0);
      }
   }

   public long rosterUiVersion() {
      Lobby lb = this.lobby;
      return this.rosterVersion * 1001L + (lb == null ? 0 : lb.speakerVersion);
   }

   public int chatVersion() {
      return this.chatVersion;
   }

   public List<LobbyListing> directoryListings() {
      long now = System.currentTimeMillis();
      List<LobbyListing> cache = this.cachedDirectory;
      if (cache != null && now - this.cachedDirectoryAtMs < 250L) {
         return cache;
      } else {
         List<LobbyListing> out = new ArrayList<>(this.directory.values());
         out.removeIf(l -> !l.isFresh(now) || this.kickedFromLobbies.contains(l.lobbyId));
         out.sort(LobbyListing.DIRECTORY_ORDER);
         this.cachedDirectory = out;
         this.cachedDirectoryAtMs = now;
         return out;
      }
   }

   public synchronized List<MmChatLine> chatSnapshot() {
      return new ArrayList<>(this.chat);
   }

   private synchronized RelayManager ensureRelays() {
      if (this.relays == null) {
         List<Relay> set = new ArrayList<>();
         addRelay(set, () -> MqttRelay.authed("mm", "                         ", () -> RiptideDiscordLogin.freshJwt(180000L), RiptideDiscordLogin::currentDid));
         this.relays = new RelayManager(set);
      }

      this.ensureScheduler();
      this.ensureDirectoryWorker();
      return this.relays;
   }

   private static void addRelay(List<Relay> set, Supplier<Relay> factory) {
      try {
         set.add(factory.get());
      } catch (Throwable var3) {
         riptide.RiptideClientAddon.LOG.warn("mm relay init failed", var3);
      }
   }

   private synchronized void ensureScheduler() {
      if (this.scheduler == null) {
         this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mm-engine");
            t.setDaemon(true);
            return t;
         });
         this.scheduler.scheduleAtFixedRate(this::tick, 2L, 2L, TimeUnit.SECONDS);
      }
   }

   private synchronized void ensureDirectoryWorker() {
      if (this.directoryHttp == null) {
         this.directoryHttp = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mm-directory");
            t.setDaemon(true);
            return t;
         });
         this.directoryHttp.scheduleWithFixedDelay(this::directoryTick, 1L, 6000L, TimeUnit.MILLISECONDS);
      }
   }

   private void directoryTick() {
      try {
         Lobby lb = this.lobby;
         if (lb != null) {
            long now = System.currentTimeMillis();
            if (now - this.lastHttpAnnounceMs >= 10000L) {
               this.lastHttpAnnounceMs = this.httpHeartbeat(lb) ? now : 0L;
            }
         }

         if (this.directoryOpen || this.autoJoinWanted()) {
            this.httpPullDirectory();
         }
      } catch (Throwable var4) {
         riptide.RiptideClientAddon.LOG.debug("mm directory tick error", var4);
      }
   }

   public synchronized void openDirectory() {
      if (RiptideDiscordLogin.hasSession()) {
         if (!this.directoryOpen) {
            this.directoryOpen = true;
            this.cancelIdleRelayClose();
            this.ensureRelays();
            long now = System.currentTimeMillis();
            if (now - this.lastImmediatePollMs > 3000L) {
               this.lastImmediatePollMs = now;
               this.submitPoll();
            }
         }
      }
   }

   public synchronized void closeDirectory() {
      if (this.directoryOpen) {
         this.directoryOpen = false;
         this.scheduleIdleRelayClose();
      }
   }

   private boolean autoJoinWanted() {
      return this.prefs.autoJoinPublic() && !this.autoJoinAttempted && this.lobby == null && !this.prefs.killSwitch() && RiptideDiscordLogin.hasSession();
   }

   private void maybeAutoJoinPublic() {
      if (this.autoJoinWanted()) {
         if (this.joinPhase != MatchmakingManager.JoinPhase.RESERVING && this.joinPhase != MatchmakingManager.JoinPhase.CONNECTING) {
            LobbyListing official = null;

            for (LobbyListing l : this.directory.values()) {
               if (l.official) {
                  official = l;
                  break;
               }
            }

            if (official != null) {
               this.autoJoinAttempted = true;
               this.joinPublic(official.lobbyId);
            }
         }
      }
   }

   public void resetAutoJoinAttempt() {
      this.autoJoinAttempted = false;
   }

   public void autoJoinNudge() {
      if (this.autoJoinWanted()) {
         this.ensureScheduler();
         this.ensureDirectoryWorker();
      }
   }

   private void scheduleIdleRelayClose() {
      if (this.lobby == null
         && this.joinPhase != MatchmakingManager.JoinPhase.RESERVING
         && this.joinPhase != MatchmakingManager.JoinPhase.CONNECTING
         && this.relays != null) {
         this.cancelIdleRelayClose();
         RelayManager expected = this.relays;
         long generation = this.idleRelayGeneration;
         this.ensureScheduler();
         this.idleRelayClose = this.scheduler.schedule(() -> this.closeIdleRelays(expected, generation), 15000L, TimeUnit.MILLISECONDS);
      }
   }

   private void cancelIdleRelayClose() {
      this.idleRelayGeneration++;
      ScheduledFuture<?> pending = this.idleRelayClose;
      this.idleRelayClose = null;
      if (pending != null) {
         pending.cancel(false);
      }
   }

   private void closeIdleRelays() {
      RelayManager expected = this.relays;
      this.cancelIdleRelayClose();
      this.closeIdleRelays(expected, this.idleRelayGeneration);
   }

   private synchronized void closeIdleRelays(RelayManager expected, long generation) {
      if (generation == this.idleRelayGeneration) {
         this.idleRelayClose = null;
         if (this.lobby == null
            && !this.directoryOpen
            && this.joinPhase != MatchmakingManager.JoinPhase.RESERVING
            && this.joinPhase != MatchmakingManager.JoinPhase.CONNECTING) {
            RelayManager rm = this.relays;
            if (rm != null && rm == expected) {
               this.relays = null;
               this.connectivitySeen = false;
               this.engagedSinceMs = 0L;
               this.warnedNoConnect = false;
               this.connLostSinceMs = 0L;
               this.warnedOffline = false;
               this.closeRelaysAsync(rm);
            }
         }
      }
   }

   private void closeRelaysAsync(RelayManager rm) {
      Runnable close = rm::closeAll;
      ScheduledExecutorService ex = this.scheduler;
      if (ex != null) {
         try {
            ex.execute(close);
            return;
         } catch (Throwable var5) {
         }
      }

      Thread t = new Thread(close, "mm-relay-close");
      t.setDaemon(true);
      t.start();
   }

   private void submitPoll() {
      ScheduledExecutorService ex = this.directoryHttp;
      if (ex != null) {
         try {
            ex.execute(this::httpPullDirectory);
         } catch (Throwable var3) {
         }
      }
   }

   private void submitHeartbeat() {
      this.submitDirectory(() -> {
         Lobby lb = this.lobby;
         if (lb != null) {
            this.lastHttpAnnounceMs = this.httpHeartbeat(lb) ? System.currentTimeMillis() : 0L;
         }
      });
   }

   private boolean httpHeartbeat(Lobby lb) {
      String jwt = RiptideDiscordLogin.currentJwt();
      if (jwt != null && !jwt.isBlank()) {
         JsonObject body = new JsonObject();
         body.addProperty("jwt", jwt);
         body.addProperty("lobbyId", lb.lobbyId);
         body.addProperty("rosterVersion", this.rosterVersion);
         if (this.isActingHost() && lb.isPublic) {
            body.addProperty("name", lb.name);
            body.addProperty("server", lb.server);
            body.addProperty("plugins", lb.plugins);
            body.addProperty("hostDupe", this.prefs.myDupeStatus());
            body.addProperty("serverShared", this.prefs.shareServer());
         }

         RiptideHttp.JsonResult r = directoryPost("/announce", body);
         if (r.status() == 404) {
            this.reestablishMembership(lb);
            return false;
         } else if (r.ok() && r.body() != null) {
            this.applyServerState(r.body());
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void reestablishMembership(Lobby lb) {
      String jwt = RiptideDiscordLogin.currentJwt();
      if (jwt != null && !jwt.isBlank()) {
         JsonObject body = new JsonObject();
         body.addProperty("jwt", jwt);
         if (lb.isPublic) {
            body.addProperty("lobbyId", lb.lobbyId);
         } else {
            body.addProperty("joinToken", lb.shareCode);
         }

         body.addProperty("fp", this.selfFpHex);
         RiptideHttp.JsonResult r = directoryPost("/join", body);
         if (r.status() != -1) {
            if (r.ok() && r.body() != null) {
               synchronized (this) {
                  if (this.lobby == lb) {
                     JsonObject b = r.body();
                     if (b.has("jwt") && b.has("exp")) {
                        RiptideDiscordLogin.adoptJwt(b.get("jwt").getAsString(), b.get("exp").getAsLong());
                     }

                     this.applyServerState(b);
                     RelayManager rm = this.relays;
                     if (rm != null) {
                        rm.reconnectAll();
                     }
                  }
               }
            } else {
               String err = r.error();
               synchronized (this) {
                  if (this.lobby == lb) {
                     RiptideNotifications.show(switch (err) {
                        case "lobby_banned" -> "You were removed from that lobby.";
                        case "full" -> "Couldn't rejoin. Lobby is full.";
                        default -> "That lobby is no longer available.";
                     }, -42149);
                     this.leave();
                  }
               }
            }
         }
      }
   }

   private synchronized void applyServerState(JsonObject b) {
      if (this.lobby != null && b != null) {
         this.lastAnnounceOkMs = System.currentTimeMillis();
         if (b.has("jwt") && b.has("exp")) {
            RiptideDiscordLogin.adoptJwt(b.get("jwt").getAsString(), b.get("exp").getAsLong());
         }

         if (b.has("role")) {
            RiptideDiscordLogin.adoptServerRole(optString(b, "role"));
         }

         String contentTopic = optString(b, "contentTopic");
         if (contentTopic.isBlank()) {
            contentTopic = optString(b, "topic");
         }

         this.adoptTopics(contentTopic, optString(b, "controlTopic"), optString(b, "sysTopic"));
         Lobby active = this.lobby;
         if (active != null) {
            if (b.has("speakerVersion")) {
               active.speakerVersion = b.get("speakerVersion").getAsInt();
            }

            if (b.has("canSend")) {
               boolean nextCanSend = b.get("canSend").getAsBoolean();
               boolean permissionChanged = active.canSend != nextCanSend;
               active.canSend = nextCanSend;
               if (permissionChanged && this.relays != null) {
                  this.relays.reconnectAll();
               }
            }

            if (this.aclReconnectPending && b.has("jwt") && this.relays != null) {
               this.aclReconnectPending = false;
               this.relays.reconnectAll();
            }

            this.adoptHost(optString(b, "hostFp"), b.has("term") ? b.get("term").getAsInt() : null);
            int ke = b.has("keyEpoch") ? b.get("keyEpoch").getAsInt() : -1;
            if (ke >= 0 && (ke != this.keyEpoch || this.currentKey == null)) {
               byte[] k = resolveLobbyKey(b);
               if (k != null && k.length == 32) {
                  if (this.currentKey != null) {
                     List<MatchmakingManager.KeyEpoch> old = new ArrayList<>(2);
                     old.add(new MatchmakingManager.KeyEpoch(this.keyEpoch, this.currentKey));

                     for (MatchmakingManager.KeyEpoch o : this.oldKeys) {
                        if (old.size() >= 2) {
                           break;
                        }

                        old.add(o);
                     }

                     this.oldKeys = List.copyOf(old);
                  }

                  this.currentKey = k;
                  this.keyEpoch = ke;
               }
            }

            long rv = b.has("rosterVersion") ? b.get("rosterVersion").getAsLong() : -1L;
            if (b.has("roster") && b.get("roster").isJsonArray()) {
               List<MatchmakingManager.RosterEntry> fresh = new ArrayList<>();

               for (JsonElement el : b.getAsJsonArray("roster")) {
                  if (el.isJsonObject()) {
                     JsonObject o = el.getAsJsonObject();
                     fresh.add(
                        new MatchmakingManager.RosterEntry(
                           optString(o, "did"),
                           optString(o, "fp").toLowerCase(Locale.ROOT),
                           optString(o, "name"),
                           o.has("admin") && o.get("admin").getAsBoolean(),
                           o.has("speaker") && o.get("speaker").getAsBoolean(),
                           optString(o, "role")
                        )
                     );
                  }
               }

               if (rv < 0L) {
                  this.serverRoster = fresh;
               } else if (rv >= this.rosterVersion) {
                  if (this.rosterSeeded && rv > this.rosterVersion) {
                     this.renderRosterDiff(this.serverRoster, fresh);
                  }

                  this.serverRoster = fresh;
                  this.rosterVersion = rv;
                  this.rosterSeeded = true;
                  this.cachedMembersAtMs = 0L;
               }

               for (MatchmakingManager.RosterEntry e : fresh) {
                  if (!e.did().isBlank() && e.fp().matches("[0-9a-f]{16}")) {
                     this.fpToDid.put(e.fp(), e.did());
                  }
               }
            } else if (rv > this.rosterVersion) {
               this.maybeRefreshServerState();
            }
         }
      }
   }

   private void adoptTopics(String contentTopic, String controlTopic, String sysTopic) {
      Lobby lb = this.lobby;
      RelayManager rm = this.relays;
      if (lb != null && rm != null && !contentTopic.isBlank() && !controlTopic.isBlank() && !sysTopic.isBlank()) {
         RoomKey old = lb.key;
         if (!contentTopic.equals(old.contentTopic()) || !controlTopic.equals(old.controlTopic()) || !sysTopic.equals(old.sysTopic())) {
            try {
               RoomKey updated = RoomKey.fromServer(lb.lobbyId, contentTopic, controlTopic, sysTopic, lb.isPublic);
               if (!contentTopic.equals(old.contentTopic())) {
                  rm.subscribe(contentTopic, this::onContentFrame);
               }

               if (!controlTopic.equals(old.controlTopic())) {
                  rm.subscribe(controlTopic, this::onControlFrame);
               }

               if (!sysTopic.equals(old.sysTopic())) {
                  rm.subscribeRaw(sysTopic, this::onSysEvent);
               }

               lb.adoptKey(updated);
               if (!contentTopic.equals(old.contentTopic())) {
                  rm.unsubscribe(old.contentTopic());
               }

               if (!controlTopic.equals(old.controlTopic())) {
                  rm.unsubscribe(old.controlTopic());
               }

               if (!sysTopic.equals(old.sysTopic())) {
                  rm.unsubscribeRaw(old.sysTopic());
               }

               if (!this.aclReconnectPending) {
                  rm.reconnectAll();
               }
            } catch (IllegalArgumentException var8) {
               riptide.RiptideClientAddon.LOG.warn("ignored invalid matchmaking topic transition", var8);
            }
         }
      }
   }

   private void adoptHost(String hostFpHex, Integer term) {
      String hf = hostFpHex == null ? "" : hostFpHex.toLowerCase(Locale.ROOT);
      if (hf.matches("[0-9a-f]{16}") && !hf.equals(this.hostFp)) {
         boolean wasHost = this.isActingHost();
         this.hostFp = hf;
         if (term != null) {
            this.hostTerm = term;
         }

         if (!wasHost && this.isActingHost()) {
            this.addSystem("You are the host now");
         } else if (!this.isActingHost()) {
            MmPeer p = this.peers.get(hf);
            if (p != null) {
               this.addSystem(this.displayNameFor(hf) + " is the host now");
            }
         }
      } else if (term != null) {
         this.hostTerm = term;
      }
   }

   private void renderRosterDiff(List<MatchmakingManager.RosterEntry> before, List<MatchmakingManager.RosterEntry> after) {
      Set<String> old = new HashSet<>();

      for (MatchmakingManager.RosterEntry e : before) {
         old.add(e.did());
      }

      Set<String> now = new HashSet<>();

      for (MatchmakingManager.RosterEntry e : after) {
         now.add(e.did());
      }

      for (MatchmakingManager.RosterEntry e : after) {
         if (!old.contains(e.did()) && !e.fp().equals(this.selfFpHex)) {
            this.addSystem(this.rosterName(e) + " joined");
         }
      }

      for (MatchmakingManager.RosterEntry ex : before) {
         if (!now.contains(ex.did()) && !ex.fp().equals(this.selfFpHex)) {
            this.addSystem(this.rosterName(ex) + " left");
         }
      }
   }

   private String rosterName(MatchmakingManager.RosterEntry e) {
      String n = MmText.clean(e.name(), 32);
      if (!n.isBlank()) {
         return n;
      } else {
         MmPeer live = e.fp().isEmpty() ? null : this.peers.get(e.fp());
         return live != null ? live.displayName() : "Discord member";
      }
   }

   private void httpPullDirectory() {
      if (this.directoryOpen || this.autoJoinWanted()) {
         String jwt = RiptideDiscordLogin.currentJwt();
         if (jwt != null && !jwt.isBlank()) {
            this.dirRefreshing = true;

            try {
               JsonObject resp = directoryGet(jwt);
               if (resp != null && resp.has("lobbies")) {
                  long now = System.currentTimeMillis();
                  Map<String, LobbyListing> fresh = new HashMap<>();
                  Set<String> seenIds = new HashSet<>();
                  Set<String> flaggedOfficial = new HashSet<>();

                  for (JsonElement el : resp.getAsJsonArray("lobbies")) {
                     if (el.isJsonObject()) {
                        JsonObject o = el.getAsJsonObject();
                        String lid = o.has("lobbyId") ? o.get("lobbyId").getAsString() : "";
                        if (lid != null && !lid.isBlank()) {
                           boolean official = o.has("official") && o.get("official").getAsBoolean();
                           seenIds.add(lid);
                           if (official) {
                              flaggedOfficial.add(lid);
                           }

                           if (!lid.equals(this.currentLobbyId())) {
                              String hostDid = o.has("hostDid") ? o.get("hostDid").getAsString() : "";
                              if (!this.prefs.isBlockedDiscord(hostDid)) {
                                 byte[] fpb = hexToBytes(parseHostFp(lid));
                                 LobbyListing l = new LobbyListing(lid, fpb != null ? fpb : new byte[8]);
                                 l.name = MmText.clean(optString(o, "name"), 48);
                                 l.members = optInt(o, "members");
                                 l.maxMembers = optInt(o, "maxMembers");
                                 l.serverShared = o.has("serverShared") && o.get("serverShared").getAsBoolean();
                                 l.server = MmText.clean(optString(o, "server"), 48);
                                 l.plugins = MmText.clean(optString(o, "plugins"), 64);
                                 l.hostDupe = o.has("hostDupe") ? o.get("hostDupe").getAsInt() : -1;
                                 l.announcement = o.has("announcement") && o.get("announcement").getAsBoolean();
                                 l.official = official;
                                 l.hostDid = hostDid;
                                 l.lastSeenMs = now;
                                 fresh.put(lid, l);
                              }
                           }
                        }
                     }
                  }

                  this.kickedFromLobbies.removeIf(fresh::containsKey);
                  synchronized (this) {
                     this.directory.keySet().retainAll(fresh.keySet());
                     this.directory.putAll(fresh);
                     this.officialLobbyIds.removeAll(seenIds);
                     this.officialLobbyIds.addAll(flaggedOfficial);
                  }

                  this.lastDirPullMs = now;
                  return;
               }
            } catch (Throwable var23) {
               riptide.RiptideClientAddon.LOG.debug("mm directory parse error", var23);
               return;
            } finally {
               this.dirRefreshing = false;
            }
         }
      }
   }

   private static String optString(JsonObject o, String k) {
      try {
         return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
      } catch (Throwable var3) {
         return "";
      }
   }

   private static int optInt(JsonObject o, String k) {
      try {
         return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : 0;
      } catch (Throwable var3) {
         return 0;
      }
   }

   private static byte[] resolveLobbyKey(JsonObject b) {
      try {
         String kb64 = optString(b, "key");
         if (kb64.isEmpty()) {
            kb64 = optString(b, "baseKey");
         }

         if (kb64.isEmpty()) {
            return null;
         } else {
            byte[] k = Base64.getDecoder().decode(kb64);
            return k.length == 32 ? k : null;
         }
      } catch (Throwable var3) {
         return null;
      }
   }

   private String currentLobbyId() {
      Lobby lb = this.lobby;
      return lb == null ? null : lb.lobbyId;
   }

   private synchronized void tick() {
      try {
         long now = System.currentTimeMillis();
         this.peers.values().removeIf(p -> now - p.lastSeenMs > 60000L);
         this.directory.values().removeIf(l -> !l.isFresh(now));
         if (this.peers.size() > 2000) {
            evictOldest(this.peers, p -> p.lastSeenMs, 2000);
         }

         if (this.directory.size() > 2000) {
            evictOldest(this.directory, l -> l.lastSeenMs, 2000);
         }

         this.awaitingReceipts.values().removeIf(sc -> now - sc.sentMs > 60000L);
         if (this.awaitingReceipts.size() > 64) {
            evictOldest(this.awaitingReceipts, sc -> sc.sentMs, 64);
         }

         if (this.joinPhase == MatchmakingManager.JoinPhase.CONNECTING && this.lobby != null && this.relayUp()) {
            this.joinPhase = MatchmakingManager.JoinPhase.NONE;
            this.sendPresence();
            this.lastPresenceMs = now;
         }

         if (this.lobby != null && !RiptideDiscordLogin.hasSession()) {
            this.addSystem("Signed out. Left the lobby.");
            this.leave();
         }

         this.maybeAutoJoinPublic();
         Lobby lb = this.lobby;
         if (lb != null) {
            long presenceInterval = !lb.announcement && lb.maxPlayers <= 100 ? 8000L : 20000L;
            if (now - this.lastPresenceMs >= presenceInterval || !this.selfTpaName().equals(this.lastPresenceNick)) {
               this.lastPresenceMs = now;
               this.sendPresence();
            }

            if (this.currentKey == null) {
               this.maybeRefreshServerState();
            }

            if (this.joinResponsePending && now - this.joinedAtMs > 10000L) {
               this.joinResponsePending = false;
               if (this.peers.isEmpty() && !this.isActingHost()) {
                  RiptideNotifications.show("You're alone here. Share the code to invite people.", -14249);
               }
            }

            if (this.prefs.debugLog() && now - this.lastDiagLogMs >= 16000L) {
               this.lastDiagLogMs = now;
               StringBuilder sb = new StringBuilder("[mm-diag]");
               RelayManager rmDiag = this.relays;
               if (rmDiag != null) {
                  for (RelayStatus s : rmDiag.statuses()) {
                     sb.append(' ')
                        .append(s.name)
                        .append(s.connected ? "+" : "-")
                        .append(" up=")
                        .append(s.published.get())
                        .append(" down=")
                        .append(s.received.get())
                        .append(" qDrop=")
                        .append(s.queueDropped.get())
                        .append(s.lastError.isEmpty() ? "" : " err=" + s.lastError);
                  }
               }

               sb.append(" | peers=")
                  .append(this.peers.size())
                  .append(" keyEpoch=")
                  .append(this.keyEpoch)
                  .append(" key=")
                  .append(this.currentKey != null)
                  .append(" host=")
                  .append(this.isActingHost())
                  .append(" srvRoster=")
                  .append(this.serverRoster.size())
                  .append(" srvLink=")
                  .append(this.serverLinkHealthy())
                  .append(" echo=")
                  .append(this.selfEchoOk)
                  .append('/')
                  .append(this.selfEchoBad)
                  .append(" | drop stale=")
                  .append(this.safety.droppedStale.get())
                  .append(" replay=")
                  .append(this.safety.droppedReplay.get())
                  .append(" flood=")
                  .append(this.safety.droppedFlood.get())
                  .append(" auth=")
                  .append(this.safety.droppedAuth.get())
                  .append(" oversize=")
                  .append(this.safety.droppedOversize.get());
               riptide.RiptideClientAddon.LOG.info(sb.toString());
            }
         }

         this.updateConnectivity();
      } catch (Throwable var10) {
         riptide.RiptideClientAddon.LOG.debug("mm tick error", var10);
      }
   }

   private void updateConnectivity() {
      RelayManager rm = this.relays;
      if (rm != null && this.lobby != null) {
         long now = System.currentTimeMillis();
         if (this.engagedSinceMs == 0L) {
            this.engagedSinceMs = now;
         }

         boolean anyConnected = false;

         for (RelayStatus s : rm.statuses()) {
            if (s.connected) {
               anyConnected = true;
               break;
            }
         }

         if (!this.connectivitySeen) {
            if (anyConnected) {
               this.connectivitySeen = true;
               this.lastConnectivity = true;
               this.warnedNoConnect = false;
            } else if (!this.warnedNoConnect && now - this.engagedSinceMs > 9000L) {
               this.warnedNoConnect = true;
               RiptideNotifications.show("Can't reach matchmaking. Check your connection.", -42149);
            }
         } else {
            if (anyConnected != this.lastConnectivity) {
               this.lastConnectivity = anyConnected;
               if (anyConnected) {
                  if (this.lobby != null) {
                     this.sendPresence();
                     this.lastPresenceMs = now;
                  }

                  if (this.warnedOffline) {
                     RiptideNotifications.show("Matchmaking: reconnected to relays.", -13248397);
                  }

                  this.connLostSinceMs = 0L;
                  this.warnedOffline = false;
               } else {
                  this.connLostSinceMs = now;
               }
            }

            if (!anyConnected && !this.warnedOffline && this.connLostSinceMs != 0L && now - this.connLostSinceMs > 10000L) {
               this.warnedOffline = true;
               RiptideNotifications.show("Matchmaking offline. Messages won't send.", -42149);
            }
         }
      } else {
         this.connectivitySeen = false;
         this.engagedSinceMs = 0L;
         this.warnedNoConnect = false;
         this.connLostSinceMs = 0L;
         this.warnedOffline = false;
      }
   }

   private static <V> void evictOldest(Map<String, V> map, ToLongFunction<V> ts, int cap) {
      int over = map.size() - cap;
      if (over > 0) {
         map.entrySet()
            .stream()
            .sorted(Comparator.comparingLong(e -> ts.applyAsLong(e.getValue())))
            .limit(over)
            .map(Entry::getKey)
            .toList()
            .forEach(map::remove);
      }
   }

   public synchronized void createLobby(LobbySettings s) {
      if (this.beginJoin()) {
         this.ensureRelays();
         this.submitDirectory(() -> this.doServerCreate(s));
      }
   }

   public synchronized void joinPublic(String lobbyId) {
      String id = lobbyId == null ? "" : lobbyId.trim();
      if (!id.isEmpty()) {
         if (this.beginJoin()) {
            this.ensureRelays();
            this.submitDirectory(() -> this.doServerJoin(id, null));
         }
      }
   }

   public synchronized void joinPrivate(char[] passkey) {
      String code = passkey == null ? "" : new String(passkey).trim();
      if (!code.isEmpty()) {
         if (this.beginJoin()) {
            this.ensureRelays();
            this.submitDirectory(() -> this.doServerJoin(null, code));
         }
      }
   }

   private boolean beginJoin() {
      if (!this.ensureAuthed()) {
         return false;
      } else if (this.joinPhase != MatchmakingManager.JoinPhase.RESERVING && this.joinPhase != MatchmakingManager.JoinPhase.CONNECTING) {
         this.joinPhase = MatchmakingManager.JoinPhase.RESERVING;
         this.joinError = "";
         return true;
      } else {
         return false;
      }
   }

   private void submitDirectory(Runnable task) {
      ScheduledExecutorService ex = this.directoryHttp;
      if (ex != null) {
         try {
            ex.execute(task);
         } catch (Throwable var4) {
         }
      }
   }

   private synchronized void failJoin(String error) {
      this.joinError = error != null && !error.isEmpty() ? error : "failed";
      this.joinPhase = MatchmakingManager.JoinPhase.FAILED;
      this.closeIdleRelays();
   }

   private void doServerCreate(LobbySettings s) {
      String jwt = RiptideDiscordLogin.currentJwt();
      if (jwt != null && !jwt.isBlank()) {
         JsonObject body = new JsonObject();
         body.addProperty("jwt", jwt);
         body.addProperty("name", s.name);
         body.addProperty("isPublic", s.isPublic);
         body.addProperty("cap", s.maxPlayers);
         body.addProperty("server", s.isPublic ? s.server : "");
         body.addProperty("plugins", s.isPublic ? s.plugins : "");
         body.addProperty("hostDupe", this.prefs.myDupeStatus());
         body.addProperty("serverShared", this.prefs.shareServer());
         body.addProperty("fp", this.selfFpHex);
         body.addProperty("announcement", s.announcement);
         RiptideHttp.JsonResult r = directoryPost("/create", body);
         if (r.status() == -1) {
            this.failJoin("network");
         } else if (r.ok() && r.body() != null) {
            this.applyServerLobby(r.body(), true, null);
         } else {
            this.failJoin(r.error());
         }
      } else {
         this.failJoin("network");
      }
   }

   private void doServerJoin(String publicId, String privateToken) {
      String jwt = RiptideDiscordLogin.currentJwt();
      if (jwt != null && !jwt.isBlank()) {
         JsonObject body = new JsonObject();
         body.addProperty("jwt", jwt);
         if (publicId != null) {
            body.addProperty("lobbyId", publicId);
         }

         if (privateToken != null) {
            body.addProperty("joinToken", privateToken);
         }

         body.addProperty("fp", this.selfFpHex);
         RiptideHttp.JsonResult r = directoryPost("/join", body);
         if (r.status() == -1) {
            this.failJoin("network");
         } else if (r.ok() && r.body() != null) {
            this.applyServerLobby(r.body(), false, privateToken);
         } else {
            this.failJoin(r.error());
         }
      } else {
         this.failJoin("network");
      }
   }

   private synchronized void applyServerLobby(JsonObject b, boolean isHost, String joinTokenUsed) {
      try {
         if (b.has("jwt") && b.has("exp")) {
            RiptideDiscordLogin.adoptJwt(b.get("jwt").getAsString(), b.get("exp").getAsLong());
         }

         String lobbyId = b.get("lobbyId").getAsString();
         boolean isPublic = b.has("isPublic") && b.get("isPublic").getAsBoolean();
         int cap = b.has("cap") ? b.get("cap").getAsInt() : 500;
         int term = b.has("term") ? b.get("term").getAsInt() : 0;
         String hostFp = isHost ? this.selfFpHex : optString(b, "hostFp");
         String privateToken = optString(b, "joinToken");
         if (privateToken.isBlank() && joinTokenUsed != null) {
            privateToken = joinTokenUsed.trim();
         }

         String shareCode = isPublic ? lobbyId : privateToken;
         String contentTopic = optString(b, "contentTopic");
         if (contentTopic.isBlank()) {
            contentTopic = optString(b, "topic");
         }

         RoomKey key = RoomKey.fromServer(lobbyId, contentTopic, optString(b, "controlTopic"), optString(b, "sysTopic"), isPublic);
         boolean announcement = b.has("announcement") && b.get("announcement").getAsBoolean();
         String ownerDid = optString(b, "ownerDid");
         int speakerVersion = optInt(b, "speakerVersion");
         boolean canSend = !announcement || b.has("canSend") && b.get("canSend").getAsBoolean();
         this.joinRoom(
            key,
            optString(b, "name"),
            isPublic,
            cap,
            isHost,
            optString(b, "server"),
            optString(b, "plugins"),
            hostFp,
            shareCode,
            term,
            announcement,
            ownerDid,
            speakerVersion,
            canSend
         );
         this.applyServerState(b);
         this.sendPresence();
         if (isHost) {
            this.addSystem(
               (isPublic ? "Created public lobby \"" : "Created private lobby \"")
                  + optString(b, "name")
                  + "\"  ("
                  + (isPublic ? "code: " : "invite: ")
                  + shareCode
                  + ")"
            );
         } else {
            this.addSystem("Joined lobby");
         }

         this.joinPhase = MatchmakingManager.JoinPhase.CONNECTING;
         RelayManager rm = this.relays;
         if (rm != null) {
            rm.reconnectAll();
         }
      } catch (Throwable var18) {
         riptide.RiptideClientAddon.LOG.warn("apply server lobby failed", var18);
         this.failJoin(isHost ? "create_failed" : "join_failed");
      }
   }

   private synchronized void joinRoom(
      RoomKey key,
      String name,
      boolean isPublic,
      int maxPlayers,
      boolean isHost,
      String server,
      String plugins,
      String hostFpHex,
      String shareCode,
      int hostTermInit,
      boolean announcement,
      String ownerDid,
      int speakerVersion,
      boolean canSend
   ) {
      if (this.ensureAuthed()) {
         this.leave();
         RelayManager rm = this.ensureRelays();
         this.lobby = new Lobby(key, name, isPublic, maxPlayers, server, plugins, shareCode, announcement, ownerDid, speakerVersion, canSend);
         this.autoJoinAttempted = false;
         this.session = new MmSession();
         this.peers.clear();
         this.droppedFps.clear();
         this.fpToDid.clear();
         this.mutedFps.clear();
         this.awaitingReceipts.clear();
         this.selfEchoOk = 0L;
         this.selfEchoBad = 0L;
         this.hostTerm = hostTermInit;
         this.hostFp = hostFpHex;
         this.rosterVersion = 0L;
         this.rosterSeeded = false;
         this.lastPresenceMs = 0L;
         this.lastHttpAnnounceMs = 0L;
         this.subscribeLobbyTopics(rm, key);
         this.joinedAtMs = System.currentTimeMillis();
         this.lastAnnounceOkMs = this.joinedAtMs;
         this.joinResponsePending = !isHost;
      }
   }

   private void subscribeLobbyTopics(RelayManager rm, RoomKey key) {
      rm.subscribe(key.contentTopic(), this::onContentFrame);
      rm.subscribe(key.controlTopic(), this::onControlFrame);
      rm.subscribeRaw(key.sysTopic(), this::onSysEvent);
   }

   public synchronized void leave() {
      this.clearLobby(true, "Left lobby");
   }

   private void clearLobby(boolean notifyServer, String message) {
      Lobby lb = this.lobby;
      if (lb != null) {
         if (notifyServer) {
            this.submitLeave(lb.lobbyId);
         }

         if (this.relays != null) {
            this.relays.unsubscribe(lb.topic());
            this.relays.unsubscribe(lb.controlTopic());
            this.relays.unsubscribeRaw(lb.sysTopic());
         }

         if (message != null && !message.isBlank()) {
            this.addSystem(message);
         }

         this.lobby = null;
         this.session = null;
         this.peers.clear();
         this.droppedFps.clear();
         this.fpToDid.clear();
         this.mutedFps.clear();
         this.awaitingReceipts.clear();
         this.selfEchoOk = 0L;
         this.selfEchoBad = 0L;
         this.hostTerm = 0;
         this.hostFp = "";
         this.currentKey = null;
         this.oldKeys = List.of();
         this.keyEpoch = 0;
         this.rosterVersion = 0L;
         this.rosterSeeded = false;
         this.serverRoster = List.of();
         this.cachedMembers = List.of();
         this.cachedMembersAtMs = 0L;
         this.lastAnnounceOkMs = 0L;
         this.joinResponsePending = false;
         this.aclReconnectPending = false;
         this.directory.clear();
         this.closeIdleRelays();
      }
   }

   public synchronized void refreshDirectory() {
      this.directory.clear();
      this.directoryOpen = true;
      this.cancelIdleRelayClose();
      this.ensureRelays();
      this.submitPoll();
   }

   public void shutdownLeave() {
      if (this.shutdownLeaveSent.compareAndSet(false, true)) {
         Lobby lb = this.lobby;
         String jwt = RiptideDiscordLogin.cachedJwt();
         if (lb != null && jwt != null && !jwt.isBlank()) {
            try {
               JsonObject body = new JsonObject();
               body.addProperty("jwt", jwt);
               body.addProperty("lobbyId", lb.lobbyId);
               directoryPost("/leave", body, 2000);
            } catch (Throwable var6) {
            }
         }

         RelayManager rm = this.relays;
         if (rm != null) {
            try {
               rm.closeAll();
            } catch (Throwable var5) {
            }
         }
      }
   }

   private void submitLeave(String lobbyId) {
      String jwt = RiptideDiscordLogin.cachedJwt();
      this.submitDirectory(() -> {
         if (jwt != null && !jwt.isBlank() && lobbyId != null) {
            JsonObject body = new JsonObject();
            body.addProperty("jwt", jwt);
            body.addProperty("lobbyId", lobbyId);
            RiptideHttp.JsonResult r = directoryPost("/leave", body);
            if (r.ok() && r.body() != null && r.body().has("jwt") && r.body().has("exp")) {
               RiptideDiscordLogin.adoptJwt(r.body().get("jwt").getAsString(), r.body().get("exp").getAsLong());
            }
         }
      });
   }

   private boolean outboundBlocked() {
      if (this.prefs.killSwitch()) {
         RiptideNotifications.show("Kill switch on. Sharing off.", -14249);
         return true;
      } else {
         return PackHideState.isHardLocked();
      }
   }

   private boolean broadcast(MmMessageType type, byte[] payload) {
      Lobby lb = this.lobby;
      MmSession se = this.session;
      if (lb == null || se == null) {
         return false;
      } else if (type.isUserContent() && !this.canSend()) {
         return false;
      } else if (!this.safety.withinSizeLimit(type, payload.length)) {
         return false;
      } else {
         try {
            byte[] key = this.currentKey;
            if (key == null) {
               return false;
            } else {
               byte[] frame = MmEnvelope.seal(this.identity, se, lb.key, key, type, payload);
               String topic = type.usesControlTopic() ? lb.controlTopic() : lb.topic();
               boolean published = this.relays.publish(topic, frame, type.isDurable());
               if (!published) {
                  riptide.RiptideClientAddon.LOG.warn("mm broadcast dropped: {} frame too large for relays ({} bytes)", type, frame.length);
               } else if (this.receiptsEnabled() && expectsReceipt(type)) {
                  String id = MmEnvelope.peekMsgIdHex(frame);
                  if (id != null) {
                     this.awaitingReceipts.put(id, new MatchmakingManager.SentContent(type.name(), System.currentTimeMillis()));
                  }
               }

               return published;
            }
         } catch (Throwable var10) {
            riptide.RiptideClientAddon.LOG.debug("mm broadcast failed", var10);
            return false;
         }
      }
   }

   private boolean rateOk() {
      long now = System.currentTimeMillis();
      if (now - this.lastUserSendMs < 1000L) {
         RiptideNotifications.show("Slow down. 1 message per second.", -14249);
         return false;
      } else {
         this.lastUserSendMs = now;
         return true;
      }
   }

   private boolean ensureAuthed() {
      if (RiptideDiscordLogin.hasSession()) {
         return true;
      } else {
         RiptideNotifications.show("Sign in with Discord to use matchmaking.", -14249);
         return false;
      }
   }

   public synchronized void sendChat(String text) {
      if (text != null && !text.isBlank() && this.lobby != null) {
         if (this.prefs.killSwitch()) {
            RiptideNotifications.show("Kill switch on. Sending off.", -14249);
         } else if (this.rateOk()) {
            String trimmed = text.length() > 1024 ? text.substring(0, 1024) : text;
            if (this.sendContent(MmMessageType.CHAT, new MmMessages.Chat(trimmed).encode())) {
               this.addChat(new MmChatLine(this.selfFpHex, this.selfDisplayName(), trimmed, true, false));
            }
         }
      }
   }

   private boolean sendContent(MmMessageType type, byte[] payload) {
      if (!this.canSend()) {
         RiptideNotifications.show("Only admins and approved speakers can send here.", -14249);
         return false;
      } else if (this.broadcast(type, payload)) {
         return true;
      } else {
         if (this.currentKey == null) {
            RiptideNotifications.show("Joining lobby. Try again.", -14249);
         } else if (!this.relayUp()) {
            RiptideNotifications.show("Not connected. Try again.", -14249);
         } else {
            RiptideNotifications.show("Couldn't send. Try again.", -2075846);
         }

         return false;
      }
   }

   private boolean relayUp() {
      RelayManager rm = this.relays;
      if (rm == null) {
         return false;
      } else {
         for (RelayStatus s : rm.statuses()) {
            if (s.connected) {
               return true;
            }
         }

         return false;
      }
   }

   public synchronized void offerMacro(MmMessages.MacroOffer m) {
      if (m != null && this.lobby != null && !this.outboundBlocked()) {
         if (this.rateOk()) {
            if (this.sendContent(MmMessageType.MACRO_OFFER, m.encode())) {
               this.addChat(MmChatLine.macroCard(this.selfFpHex, this.selfDisplayName(), true, m));
            }
         }
      }
   }

   public synchronized void offerPacket(MmMessages.PacketOffer p) {
      if (p != null && this.lobby != null && !this.outboundBlocked()) {
         if (this.rateOk()) {
            if (this.sendContent(MmMessageType.PACKET_OFFER, p.encode())) {
               this.addChat(MmChatLine.packetCard(this.selfFpHex, this.selfDisplayName(), true, p));
            }
         }
      }
   }

   public synchronized boolean offerBlob(MmMessages.BlobOffer b) {
      if (b == null || this.lobby == null || this.outboundBlocked()) {
         return false;
      } else if (!this.rateOk()) {
         return false;
      } else {
         byte[] payload = b.encode();
         if (!this.safety.withinSizeLimit(MmMessageType.BLOB_OFFER, payload.length)) {
            RiptideNotifications.show("Share too large to send.", -2075846);
            return false;
         } else if (!this.sendContent(MmMessageType.BLOB_OFFER, payload)) {
            return false;
         } else {
            this.addChat(MmChatLine.blobCard(this.selfFpHex, this.selfDisplayName(), true, b));
            return true;
         }
      }
   }

   public synchronized void offerCommand(String typedLine) {
      if (typedLine != null && !typedLine.isBlank() && this.lobby != null && !this.outboundBlocked()) {
         if (this.rateOk()) {
            MmMessages.CommandOffer offer = classifyCommand(typedLine.trim());
            if (this.sendContent(MmMessageType.COMMAND_OFFER, offer.encode())) {
               this.addChat(MmChatLine.commandCard(this.selfFpHex, this.selfDisplayName(), true, offer));
            }
         }
      }
   }

   public static MmMessages.CommandOffer classifyCommand(String line) {
      if (RiptideCommands.isRiptideCommandMessage(line)) {
         return new MmMessages.CommandOffer((byte)1, RiptideCommands.commandBody(line));
      } else {
         return line.startsWith("/") ? new MmMessages.CommandOffer((byte)0, line.substring(1).trim()) : new MmMessages.CommandOffer((byte)2, line);
      }
   }

   public static String renderCommandOffer(MmMessages.CommandOffer offer) {
      if (offer == null) {
         return "";
      } else {
         return switch (offer.kind) {
            case 0 -> "/" + offer.body;
            case 1 -> RiptideCommands.effectivePrefix() + offer.body;
            default -> offer.body;
         };
      }
   }

   public void runCommandOffer(MmMessages.CommandOffer offer) {
      if (offer != null && !this.prefs.killSwitch()) {
         ClientPacketListener conn = Minecraft.getInstance().getConnection();
         switch (offer.kind) {
            case 0:
               if (conn != null) {
                  conn.sendCommand(offer.body);
               }
               break;
            case 1:
               if (isBlockedSharedRiptideCommand(offer.body)) {
                  RiptideNotifications.show("Blocked: shared commands can't change your prefix or disable protections.", -42149);
                  return;
               }

               RiptideCommands.dispatch(offer.body);
               break;
            default:
               if (conn != null) {
                  conn.sendChat(offer.body);
               }
         }
      }
   }

   private static boolean isBlockedSharedRiptideCommand(String body) {
      if (body == null) {
         return false;
      } else {
         String norm = body.trim();

         while (norm.startsWith("/")) {
            norm = norm.substring(1).trim();
         }

         if (norm.isEmpty()) {
            return false;
         } else {
            String[] parts = norm.split("\\s+");
            String head = parts[0].toLowerCase(Locale.ROOT);
            if (head.equals("prefix")) {
               return true;
            } else {
               if (head.equals("toggle") || head.equals("module")) {
                  for (int i = 1; i < parts.length; i++) {
                     if (PackHideState.isHideModuleName(parts[i])) {
                        return true;
                     }
                  }
               }

               return false;
            }
         }
      }
   }

   public void tpaPeer(String name) {
      String n = name == null ? "" : name.trim();
      if (!n.matches("[A-Za-z0-9_]{1,16}")) {
         RiptideNotifications.show("Can't TPA: that player's name isn't a valid Minecraft username.", -14249);
      } else {
         ClientPacketListener conn = Minecraft.getInstance().getConnection();
         if (conn != null) {
            conn.sendCommand("tpa " + n);
         }
      }
   }

   public void tradePeer(String name) {
      String n = name == null ? "" : name.trim();
      if (!n.matches("[A-Za-z0-9_]{1,16}")) {
         RiptideNotifications.show("Can't trade: that player's name isn't a valid Minecraft username.", -14249);
      } else {
         ClientPacketListener conn = Minecraft.getInstance().getConnection();
         if (conn != null) {
            conn.sendCommand("trade " + n);
         }
      }
   }

   private void sendPresence() {
      MmSession se = this.session;
      if (se != null) {
         String nick = this.selfTpaName();
         this.lastPresenceNick = nick;
         MmMessages.Presence p = new MmMessages.Presence(nick);
         boolean ks = this.prefs.killSwitch();
         boolean mayShare = this.canSend();
         boolean shareServer = this.effectiveShareServer();
         boolean shareLocation = this.effectiveShareLocation();
         if (!ks && mayShare && shareServer) {
            try {
               ServerData sd = Minecraft.getInstance().getCurrentServer();
               if (sd != null && sd.ip != null && !sd.ip.isBlank()) {
                  p.shareServer = true;
                  p.serverIp = sd.ip;
                  p.serverName = sd.name == null ? sd.ip : sd.name;
               }
            } catch (Throwable var9) {
            }
         }

         if (!ks && mayShare && shareLocation) {
            p.shareLocation = true;
         }

         p.dupeStatus = mayShare ? this.prefs.myDupeStatus() : -1;
         p.identityToken = RiptideDiscordLogin.currentIdToken();
         this.broadcast(MmMessageType.PRESENCE, p.encode());
         if (!ks && mayShare && shareLocation) {
            this.sendLocation();
         }
      }
   }

   private void sendLocation() {
      try {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null) {
            return;
         }

         String dim = mc.player.level().dimension().identifier().toString();
         MmMessages.Location loc = new MmMessages.Location(dim, mc.player.getX(), mc.player.getY(), mc.player.getZ());
         this.broadcast(MmMessageType.LOCATION, loc.encode());
      } catch (Throwable var4) {
      }
   }

   private void onContentFrame(byte[] frame) {
      this.onLobbyFrame(frame, false);
   }

   private void onControlFrame(byte[] frame) {
      this.onLobbyFrame(frame, true);
   }

   private synchronized void onLobbyFrame(byte[] frame, boolean controlTopic) {
      try {
         Lobby lb = this.lobby;
         if (lb == null) {
            return;
         }

         String fp = MmEnvelope.peekSenderFpHex(frame);
         if (fp == null) {
            return;
         }

         MmMessageType peeked = MmMessageType.byId(MmEnvelope.peekTypeId(frame));
         if (peeked == null || peeked.usesControlTopic() != controlTopic) {
            return;
         }

         if (fp.equals(this.selfFpHex)) {
            this.verifySelfEcho(frame);
            return;
         }

         if (this.droppedFps.contains(fp)) {
            return;
         }

         if (!this.safety.admitPreDecrypt()) {
            return;
         }

         boolean content = peeked != null && !peeked.isControl();
         if (content && (this.mutedFps.contains(fp) || this.prefs.isBlocked(fp))) {
            return;
         }

         byte[] k1 = this.currentKey;
         if (k1 == null) {
            this.maybeRefreshServerState();
            return;
         }

         MmEnvelope env = MmEnvelope.open(frame, k1);
         if (env == null) {
            for (MatchmakingManager.KeyEpoch o : this.oldKeys) {
               env = MmEnvelope.open(frame, o.key());
               if (env != null) {
                  break;
               }
            }
         }

         if (env == null) {
            this.safety.droppedAuth.incrementAndGet();
            this.maybeRefreshServerState();
            return;
         }

         if (!this.safety.accept(env)) {
            return;
         }

         MmMessageType type = env.type();
         if (type == null) {
            return;
         }

         if (type.usesControlTopic() != controlTopic) {
            return;
         }

         if (type.isUserContent() && !this.isAuthorizedContentSender(fp)) {
            return;
         }

         if (!type.isControl() && this.prefs.killSwitch()) {
            return;
         }

         switch (type) {
            case PRESENCE:
               this.handlePresence(env);
               break;
            case CHAT:
               this.handleChat(env);
               break;
            case MACRO_OFFER:
               this.handleMacroOffer(env);
               break;
            case COMMAND_OFFER:
               this.handleCommandOffer(env);
               break;
            case PACKET_OFFER:
               this.handlePacketOffer(env);
               break;
            case BLOB_OFFER:
               this.handleBlob(env);
               break;
            case LOCATION:
               this.handleLocation(env);
               break;
            case LEAVE:
               this.handleLeave(env);
               break;
            case KICK:
               this.handleKick(env);
               break;
            case RECEIPT:
               this.handleReceipt(env);
         }
      } catch (Throwable var11) {
         riptide.RiptideClientAddon.LOG.debug("mm inbound handler error", var11);
      }
   }

   private boolean isAuthorizedContentSender(String fp) {
      Lobby lb = this.lobby;
      if (lb != null && lb.announcement) {
         for (MatchmakingManager.RosterEntry e : this.serverRoster) {
            if (e.speaker() && fp.equalsIgnoreCase(e.fp())) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private synchronized void onSysEvent(byte[] payload) {
      try {
         Lobby lb = this.lobby;
         if (lb == null) {
            return;
         }

         MmSysEvent ev = MmSysEvent.parse(payload);
         if (ev == null) {
            return;
         }

         PublicKey serverKey = MmDiscordProof.serverPublicKey();
         if (serverKey == null) {
            this.maybeRefreshServerState();
            return;
         }

         if (!ev.verify(serverKey, lb.sysTopic())) {
            return;
         }

         long last = this.rosterVersion;
         if (ev.rv <= last) {
            return;
         }

         if (ev.rv > last + 1L) {
            this.maybeRefreshServerState();
            return;
         }

         byte[] key = this.keyForEpoch(ev.ke);
         MmSysEvent.Body body = key == null ? null : ev.open(key, lb.sysTopic());
         if (body == null) {
            this.maybeRefreshServerState();
            return;
         }

         this.rosterVersion = ev.rv;
         this.applySysEvent(body);
         if (ev.cke > this.keyEpoch) {
            this.maybeRefreshServerState();
         }
      } catch (Throwable var9) {
         riptide.RiptideClientAddon.LOG.debug("mm sys event error", var9);
      }
   }

   private void applySysEvent(MmSysEvent.Body b) {
      String fp = b.fp() == null ? "" : b.fp().toLowerCase(Locale.ROOT);
      boolean self = fp.equals(this.selfFpHex);
      String var4 = b.t();
      switch (var4) {
         case "join":
            if (self) {
               return;
            }

            if (!fp.isBlank() && b.did() != null && !b.did().isBlank()) {
               this.fpToDid.put(fp, b.did());
            }

            List<MatchmakingManager.RosterEntry> fresh = new ArrayList<>(this.serverRoster);
            if (fresh.removeIf(ex -> ex.did().equals(b.did()))) {
               fresh.add(new MatchmakingManager.RosterEntry(b.did(), fp, b.name(), b.admin(), b.speaker(), b.role()));
               this.serverRoster = fresh;
            } else {
               MatchmakingManager.RosterEntry e = new MatchmakingManager.RosterEntry(b.did(), fp, b.name(), b.admin(), b.speaker(), b.role());
               fresh.add(e);
               this.serverRoster = fresh;
               this.addSystem(this.rosterName(e) + " joined");
            }

            this.cachedMembersAtMs = 0L;
            break;
         case "leave":
         case "kick":
            boolean kick = b.t().equals("kick");
            if (self) {
               if (kick) {
                  this.onSelfKicked();
               }

               return;
            }

            MatchmakingManager.RosterEntry gone = null;
            List<MatchmakingManager.RosterEntry> fresh = new ArrayList<>(this.serverRoster);
            Iterator<MatchmakingManager.RosterEntry> it = fresh.iterator();

            while (it.hasNext()) {
               MatchmakingManager.RosterEntry e = it.next();
               if (e.did().equals(b.did())) {
                  gone = e;
                  it.remove();
               }
            }

            this.serverRoster = fresh;
            this.cachedMembersAtMs = 0L;
            String name = gone != null
               ? this.rosterName(gone)
               : this.rosterName(new MatchmakingManager.RosterEntry(b.did(), fp, b.name(), b.admin(), b.speaker(), b.role()));
            if (!fp.isEmpty()) {
               if (kick) {
                  this.droppedFps.add(fp);
               }

               this.peers.remove(fp);
               this.fpToDid.remove(fp);
               this.safety.forgetPeer(fp);
            }

            this.addSystem(kick ? name + " was removed by the host" : name + " left");
            break;
         case "host":
            this.adoptHost(b.hostFp(), b.term());
            break;
         case "speaker":
            Lobby lb = this.lobby;
            if (lb == null || !lb.announcement || b.speakerVersion() < lb.speakerVersion) {
               return;
            }

            lb.speakerVersion = b.speakerVersion();
            List<MatchmakingManager.RosterEntry> fresh = new ArrayList<>(this.serverRoster.size());
            MatchmakingManager.RosterEntry changed = null;

            for (MatchmakingManager.RosterEntry ex : this.serverRoster) {
               if (ex.did().equals(b.did())) {
                  changed = new MatchmakingManager.RosterEntry(ex.did(), ex.fp(), ex.name(), ex.admin(), ex.admin() || b.allowed(), ex.role());
                  fresh.add(changed);
               } else {
                  fresh.add(ex);
               }
            }

            this.serverRoster = fresh;
            if (changed != null && !changed.fp().isBlank()) {
               MmPeer peer = this.peers.get(changed.fp());
               if (peer != null) {
                  peer.speaker = changed.speaker();
               }
            }

            boolean selfDid = b.did().equals(RiptideDiscordLogin.currentDid());
            if (selfDid && !b.allowed()) {
               lb.canSend = false;
            }

            String oldContent = lb.topic();
            String oldControl = lb.controlTopic();
            boolean announcedRotation = !oldContent.equals(b.contentTopic()) || !oldControl.equals(b.controlTopic());
            if (selfDid || announcedRotation) {
               this.aclReconnectPending = true;
            }

            this.adoptTopics(b.contentTopic(), b.controlTopic(), lb.sysTopic());
            boolean rotated = !oldContent.equals(lb.topic()) || !oldControl.equals(lb.controlTopic());
            if (selfDid || rotated) {
               this.maybeRefreshServerState();
            }

            this.cachedMembersAtMs = 0L;
            if (changed != null) {
               this.addSystem(this.rosterName(changed) + (changed.speaker() ? " can send now" : " can no longer send"));
            }
            break;
         case "role":
            List<MatchmakingManager.RosterEntry> fresh = new ArrayList<>(this.serverRoster.size());
            MatchmakingManager.RosterEntry changed = null;

            for (MatchmakingManager.RosterEntry e : this.serverRoster) {
               if (e.did().equals(b.did())) {
                  changed = new MatchmakingManager.RosterEntry(e.did(), e.fp(), e.name(), e.admin(), e.speaker(), b.role());
                  fresh.add(changed);
               } else {
                  fresh.add(e);
               }
            }

            this.serverRoster = fresh;
            if (changed != null && !changed.fp().isBlank()) {
               MmPeer peer = this.peers.get(changed.fp());
               if (peer != null) {
                  peer.role = changed.role();
               }
            }

            if (b.did() != null && b.did().equals(RiptideDiscordLogin.currentDid())) {
               RiptideDiscordLogin.adoptServerRole(b.role());
            }

            this.cachedMembersAtMs = 0L;
            break;
         case "close":
            this.clearLobby(false, "Lobby closed: the host left.");
      }
   }

   private void onSelfKicked() {
      Lobby lb = this.lobby;
      if (lb != null) {
         if (lb.lobbyId != null && !lb.lobbyId.isBlank()) {
            this.kickedFromLobbies.add(lb.lobbyId);
         }

         this.directory.remove(lb.lobbyId);
         RiptideNotifications.show("You're banned from this lobby.", -42149);
         this.addSystem("You were removed from the lobby by the host.");
         this.leave();
      }
   }

   private byte[] keyForEpoch(int epoch) {
      if (this.currentKey != null && epoch == this.keyEpoch) {
         return this.currentKey;
      } else {
         for (MatchmakingManager.KeyEpoch o : this.oldKeys) {
            if (o.epoch() == epoch) {
               return o.key();
            }
         }

         return null;
      }
   }

   private MmPeer peerFor(MmEnvelope env) {
      return this.peers.computeIfAbsent(env.senderFpHex(), k -> new MmPeer(env.senderFp));
   }

   private void verifySelfEcho(byte[] frame) {
      byte[] k1 = this.currentKey;
      if (k1 != null) {
         MmEnvelope env = MmEnvelope.open(frame, k1);
         if (env == null) {
            for (MatchmakingManager.KeyEpoch o : this.oldKeys) {
               env = MmEnvelope.open(frame, o.key());
               if (env != null) {
                  break;
               }
            }
         }

         if (env != null) {
            if (this.selfEchoOk++ == 0L && this.prefs.debugLog()) {
               riptide.RiptideClientAddon.LOG
                  .info("[mm-loopback] round-trip verified: publish -> broker -> subscribe -> decrypt OK (every member on this key receives our frames)");
            }
         } else {
            this.selfEchoBad++;
         }
      }
   }

   private void handleReceipt(MmEnvelope env) {
      MmMessages.Receipt r = MmMessages.Msg.decodeInto(new MmMessages.Receipt(), env.payload);
      if (r != null) {
         MatchmakingManager.SentContent sc = this.awaitingReceipts.get(MmCrypto.hex(r.msgId));
         if (sc != null) {
            String fp = env.senderFpHex();
            if (sc.confirmedFps.add(fp)) {
               if (this.prefs.debugLog()) {
                  MmPeer peer = this.peers.get(fp);
                  riptide.RiptideClientAddon.LOG
                     .info(
                        "[mm-receipt] {} delivered to {} ({} member(s) confirmed, {}ms after send)",
                        new Object[]{
                           sc.type,
                           peer != null ? this.displayNameFor(peer.fpHex) : "Discord member",
                           sc.confirmedFps.size(),
                           System.currentTimeMillis() - sc.sentMs
                        }
                     );
               }
            }
         }
      }
   }

   private void sendReceipt(MmEnvelope env) {
      if (this.receiptsEnabled()) {
         try {
            this.broadcast(MmMessageType.RECEIPT, new MmMessages.Receipt(env.msgId).encode());
         } catch (Throwable var3) {
         }
      }
   }

   private boolean receiptsEnabled() {
      Lobby lb = this.lobby;
      return lb != null && !lb.announcement && this.memberCount() <= 100;
   }

   private static boolean expectsReceipt(MmMessageType type) {
      return switch (type) {
         case CHAT, MACRO_OFFER, COMMAND_OFFER, PACKET_OFFER, BLOB_OFFER -> true;
         default -> false;
      };
   }

   private void handlePresence(MmEnvelope env) {
      MmMessages.Presence p = MmMessages.Msg.decodeInto(new MmMessages.Presence(), env.payload);
      if (p != null) {
         String fp = env.senderFpHex();
         MmDiscordProof.Identity id = MmDiscordProof.verify(p.identityToken, env.senderSpki);
         if (id != null) {
            this.fpToDid.put(fp, id.id());
         }

         String did = id != null ? id.id() : this.fpToDid.get(fp);
         if (did != null && this.isActingHost() && this.prefs.isBannedDiscord(did)) {
            byte[] fpb = hexToBytes(fp);
            if (fpb != null) {
               this.broadcast(MmMessageType.KICK, new MmMessages.Kick(fpb).encode());
            }

            this.droppedFps.add(fp);
            this.peers.remove(fp);
            if (this.lobby != null) {
               this.submitBan(this.lobby.lobbyId, did, fp);
            }
         } else {
            boolean isNew = !this.peers.containsKey(fp);
            MmPeer peer = this.peerFor(env);
            peer.discordId = did == null ? "" : did;

            for (MatchmakingManager.RosterEntry e : this.serverRoster) {
               if (fp.equalsIgnoreCase(e.fp())) {
                  peer.admin = e.admin();
                  peer.speaker = e.speaker();
                  peer.role = e.role();
                  if (peer.discordId.isBlank()) {
                     peer.discordId = e.did();
                  }
                  break;
               }
            }

            if (id != null) {
               peer.discordUser = MmText.clean(id.username(), 32);
               if (!peer.discordUser.isBlank()) {
                  this.prefs.refreshDiscordName(did, peer.discordUser);
               }
            }

            boolean blockedNow = did != null && this.prefs.isBlockedDiscord(did) || this.prefs.isBlocked(fp);
            peer.muted = blockedNow;
            if (blockedNow) {
               this.mutedFps.add(fp);
            } else {
               this.mutedFps.remove(fp);
            }

            peer.nickname = MmText.clean(p.nickname, 32);
            peer.lastSeenMs = System.currentTimeMillis();
            boolean senderMayShare = this.isAuthorizedContentSender(fp);
            peer.serverShared = senderMayShare && p.shareServer;
            peer.serverName = senderMayShare ? MmText.clean(p.serverName, 48) : "";
            peer.serverIp = senderMayShare ? MmText.clean(p.serverIp, 64) : "";
            peer.dupeStatus = senderMayShare ? p.dupeStatus : -1;
            if (isNew) {
               long nowMs = System.currentTimeMillis();
               Lobby lb = this.lobby;
               boolean largeRoom = lb != null && (lb.announcement || lb.maxPlayers > 100);
               if (!largeRoom && nowMs - this.lastPeerReplyMs > 1500L) {
                  this.lastPeerReplyMs = nowMs;
                  this.sendPresence();
                  this.lastPresenceMs = nowMs;
               }
            }
         }
      }
   }

   private void handleChat(MmEnvelope env) {
      MmMessages.Chat c = MmMessages.Msg.decodeInto(new MmMessages.Chat(), env.payload);
      if (c != null) {
         MmPeer peer = this.peerFor(env);
         peer.lastSeenMs = System.currentTimeMillis();
         this.addChat(new MmChatLine(peer.fpHex, this.displayNameFor(peer.fpHex), MmText.clean(c.text, 512), false, false));
         this.sendReceipt(env);
      }
   }

   private void handleMacroOffer(MmEnvelope env) {
      MmMessages.MacroOffer m = MmMessages.Msg.decodeInto(new MmMessages.MacroOffer(), env.payload);
      if (m != null) {
         m.macroName = MmText.clean(m.macroName, 48);
         m.singleActionLabel = MmText.clean(m.singleActionLabel, 64);
         MmPeer peer = this.peerFor(env);
         peer.lastSeenMs = System.currentTimeMillis();
         this.addChat(MmChatLine.macroCard(peer.fpHex, this.displayNameFor(peer.fpHex), false, m));
         this.sendReceipt(env);
      }
   }

   private void handleCommandOffer(MmEnvelope env) {
      MmMessages.CommandOffer c = MmMessages.Msg.decodeInto(new MmMessages.CommandOffer(), env.payload);
      if (c != null) {
         MmPeer peer = this.peerFor(env);
         peer.lastSeenMs = System.currentTimeMillis();
         this.addChat(MmChatLine.commandCard(peer.fpHex, this.displayNameFor(peer.fpHex), false, c));
         this.sendReceipt(env);
      }
   }

   private void handlePacketOffer(MmEnvelope env) {
      MmMessages.PacketOffer p = MmMessages.Msg.decodeInto(new MmMessages.PacketOffer(), env.payload);
      if (p != null) {
         p.friendlyName = MmText.clean(p.friendlyName, 48);
         MmPeer peer = this.peerFor(env);
         peer.lastSeenMs = System.currentTimeMillis();
         this.addChat(MmChatLine.packetCard(peer.fpHex, this.displayNameFor(peer.fpHex), false, p));
         this.sendReceipt(env);
      }
   }

   private void handleBlob(MmEnvelope env) {
      MmMessages.BlobOffer b = MmMessages.Msg.decodeInto(new MmMessages.BlobOffer(), env.payload);
      if (b != null) {
         b.friendlyName = MmText.clean(b.friendlyName, 64);
         MmPeer peer = this.peerFor(env);
         peer.lastSeenMs = System.currentTimeMillis();
         this.addChat(MmChatLine.blobCard(peer.fpHex, this.displayNameFor(peer.fpHex), false, b));
         this.sendReceipt(env);
      }
   }

   private void handleLocation(MmEnvelope env) {
      MmMessages.Location loc = MmMessages.Msg.decodeInto(new MmMessages.Location(), env.payload);
      if (loc != null) {
         MmPeer peer = this.peerFor(env);
         peer.hasLocation = true;
         peer.dimension = MmText.clean(loc.dimension, 48);
         peer.x = loc.x;
         peer.y = loc.y;
         peer.z = loc.z;
         peer.lastSeenMs = System.currentTimeMillis();
      }
   }

   private void handleLeave(MmEnvelope env) {
      this.peers.remove(env.senderFpHex());
      this.safety.forgetPeer(env.senderFpHex());
   }

   public synchronized void setSpeaker(String targetFpHex, boolean allowed) {
      if (this.canManageSpeakers() && this.lobby != null && targetFpHex != null) {
         MatchmakingManager.RosterEntry target = null;

         for (MatchmakingManager.RosterEntry e : this.serverRoster) {
            if (targetFpHex.equalsIgnoreCase(e.fp())) {
               target = e;
               break;
            }
         }

         if (target != null && !target.did().isBlank() && !target.admin()) {
            String lobbyId = this.lobby.lobbyId;
            String targetDid = target.did();
            int expectedVersion = this.lobby.speakerVersion;
            this.submitDirectory(() -> {
               String jwt = RiptideDiscordLogin.currentJwt();
               if (jwt != null && !jwt.isBlank()) {
                  JsonObject body = new JsonObject();
                  body.addProperty("jwt", jwt);
                  body.addProperty("lobbyId", lobbyId);
                  body.addProperty("targetDid", targetDid);
                  body.addProperty("allowed", allowed);
                  body.addProperty("speakerVersion", expectedVersion);
                  RiptideHttp.JsonResult r = directoryPost("/speaker", body);
                  if (r.ok() && r.body() != null) {
                     this.applyServerState(r.body());
                     this.submitHeartbeat();
                  } else if (r.status() == 409) {
                     this.submitHeartbeat();
                     RiptideNotifications.show("Speaker permissions changed; try again.", -14249);
                  } else {
                     RiptideNotifications.show("Could not change speaker permission.", -42149);
                  }
               }
            });
         }
      }
   }

   public synchronized void banPeer(String fpHex) {
      if (this.isActingHost() && fpHex != null && !fpHex.equals(this.selfFpHex)) {
         String did = this.fpToDid.get(fpHex);
         MmPeer target = this.peers.get(fpHex);
         if (did != null) {
            this.prefs.banDiscord(did, target != null ? target.discordUser : "");
         }

         String who = this.displayNameFor(fpHex);
         this.droppedFps.add(fpHex);
         this.peers.remove(fpHex);
         this.safety.forgetPeer(fpHex);
         byte[] fp = hexToBytes(fpHex);
         if (fp != null) {
            this.broadcast(MmMessageType.KICK, new MmMessages.Kick(fp).encode());
         }

         if (this.lobby != null) {
            this.submitBan(this.lobby.lobbyId, did, fpHex);
            this.submitHeartbeat();
         }

         this.addSystem("Banned " + who + " from the lobby");
      }
   }

   private void submitBan(String lobbyId, String targetDid, String targetFp) {
      this.submitDirectory(() -> {
         String jwt = RiptideDiscordLogin.currentJwt();
         if (jwt != null && !jwt.isBlank()) {
            JsonObject body = new JsonObject();
            body.addProperty("jwt", jwt);
            body.addProperty("lobbyId", lobbyId);
            if (targetDid != null) {
               body.addProperty("targetDid", targetDid);
            }

            if (targetFp != null) {
               body.addProperty("targetFp", targetFp);
            }

            directoryPost("/ban", body);
         }
      });
   }

   public synchronized void makeHost(String targetFpHex) {
      if (this.isActingHost()) {
         String t = targetFpHex == null ? "" : targetFpHex.trim().toLowerCase(Locale.ROOT);
         if (!t.isEmpty() && !t.equals(this.selfFpHex) && t.matches("[0-9a-f]{16}")) {
            MmPeer p = this.peers.get(t);
            if (p != null && this.eligibleHost(t)) {
               String targetDid = this.fpToDid.get(t);
               if (targetDid != null && this.lobby != null) {
                  this.submitTransfer(this.lobby.lobbyId, targetDid);
                  this.addSystem("Made " + this.displayNameFor(p.fpHex) + " the host");
               } else {
                  this.addSystem("Can't make host. Not verified yet.");
               }
            } else {
               this.addSystem("Can't make host. Not ready.");
            }
         }
      }
   }

   private void submitTransfer(String lobbyId, String targetDid) {
      this.submitDirectory(() -> {
         String jwt = RiptideDiscordLogin.currentJwt();
         if (jwt != null && !jwt.isBlank()) {
            JsonObject body = new JsonObject();
            body.addProperty("jwt", jwt);
            body.addProperty("lobbyId", lobbyId);
            body.addProperty("targetDid", targetDid);
            RiptideHttp.JsonResult r = directoryPost("/transfer", body);
            if (r.ok() && r.body() != null) {
               this.applyServerState(r.body());
            }
         }
      });
   }

   private void handleKick(MmEnvelope env) {
      Lobby lb = this.lobby;
      if (lb != null) {
         if (env.senderFpHex().equals(this.actingHostFp())) {
            MmMessages.Kick k = MmMessages.Msg.decodeInto(new MmMessages.Kick(), env.payload);
            if (k != null) {
               String fp = MmCrypto.hex(k.bannedFp);
               if (fp.equals(this.selfFpHex)) {
                  this.onSelfKicked();
               } else {
                  this.droppedFps.add(fp);
                  this.peers.remove(fp);
                  this.safety.forgetPeer(fp);
               }
            }
         }
      }
   }

   private void maybeRefreshServerState() {
      long now = System.currentTimeMillis();
      if (now - this.lastServerRefreshMs >= 3000L) {
         this.lastServerRefreshMs = now;
         this.submitHeartbeat();
      }
   }

   private static String parseHostFp(String code) {
      return code != null && code.length() > 17 && code.charAt(16) == ':' && code.substring(0, 16).matches("[0-9a-fA-F]{16}")
         ? code.substring(0, 16).toLowerCase(Locale.ROOT)
         : "";
   }

   private static byte[] hexToBytes(String hex) {
      if (hex != null && hex.length() == 16) {
         try {
            byte[] out = new byte[8];

            for (int i = 0; i < 8; i++) {
               out[i] = (byte)Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }

            return out;
         } catch (Exception var3) {
            return null;
         }
      } else {
         return null;
      }
   }

   private synchronized void addChat(MmChatLine line) {
      this.chat.addLast(line);

      while (this.chat.size() > 300) {
         this.chat.removeFirst();
      }

      this.chatVersion++;
      if (this.prefs.chatMirror()) {
         MmChatComponents.emit(line);
      }
   }

   public synchronized void clearChat() {
      this.chat.clear();
      this.chatVersion++;
   }

   public synchronized boolean hasChat() {
      return !this.chat.isEmpty();
   }

   private void addSystem(String text) {
      this.addChat(MmChatLine.system(text));
   }

   public synchronized void blockPeer(String fpHex) {
      MmPeer peer = this.peers.get(fpHex);
      String did = this.fpToDid.get(fpHex);
      if (did != null) {
         this.prefs.blockDiscord(did, peer != null ? peer.discordUser : "");
         this.directory.values().removeIf(l -> did.equals(l.hostDid));
      } else {
         this.prefs.block(fpHex);
      }

      this.mutedFps.add(fpHex);
      if (peer != null) {
         peer.muted = true;
      }
   }

   public synchronized void unblockDiscord(String did) {
      if (did != null) {
         this.prefs.unblockDiscord(did);
         this.mutedFps.removeIf(fp -> did.equals(this.fpToDid.get(fp)));

         for (MmPeer p : this.peers.values()) {
            if (did.equals(p.discordId)) {
               p.muted = false;
            }
         }
      }
   }

   public synchronized void unblockPeer(String fpHex) {
      String did = this.fpToDid.get(fpHex);
      if (did != null) {
         this.prefs.unblockDiscord(did);
      } else {
         this.prefs.unblock(fpHex);
      }

      this.mutedFps.remove(fpHex);
      MmPeer p = this.peers.get(fpHex);
      if (p != null) {
         p.muted = false;
      }
   }

   public synchronized void unbanDiscord(String did) {
      if (did != null) {
         this.prefs.unbanDiscord(did);

         for (Entry<String, String> e : this.fpToDid.entrySet()) {
            if (did.equals(e.getValue())) {
               this.droppedFps.remove(e.getKey());
            }
         }

         Lobby lb = this.lobby;
         if (lb != null && this.isActingHost()) {
            this.submitUnban(lb.lobbyId, did);
         }
      }
   }

   private void submitUnban(String lobbyId, String targetDid) {
      this.submitDirectory(() -> {
         String jwt = RiptideDiscordLogin.currentJwt();
         if (jwt != null && !jwt.isBlank()) {
            JsonObject body = new JsonObject();
            body.addProperty("jwt", jwt);
            body.addProperty("lobbyId", lobbyId);
            body.addProperty("targetDid", targetDid);
            directoryPost("/unban", body);
         }
      });
   }

   public String selfTpaName() {
      try {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            String inWorld = mc.player.getGameProfile().name();
            if (inWorld != null && !inWorld.isBlank()) {
               return inWorld;
            }
         }

         String mcName = mc.getUser().getName();
         if (mcName != null && !mcName.isBlank()) {
            return mcName;
         }
      } catch (Throwable var3) {
      }

      return "";
   }

   public String selfDisplayName() {
      String proofName = MmText.clean(MmDiscordProof.ownName(), 32);
      if (!proofName.isBlank()) {
         return proofName;
      } else {
         String loginName = MmText.clean(RiptideDiscordLogin.displayName(), 32);
         return loginName.isBlank() ? "Discord member" : loginName;
      }
   }

   public String displayNameFor(String fpHex) {
      if (fpHex != null && fpHex.equalsIgnoreCase(this.selfFpHex)) {
         return this.selfDisplayName();
      } else {
         MmPeer peer = this.peer(fpHex);
         if (peer != null && peer.discordUser != null && !peer.discordUser.isBlank()) {
            return peer.displayName();
         } else {
            if (fpHex != null) {
               for (MatchmakingManager.RosterEntry e : this.serverRoster) {
                  if (fpHex.equalsIgnoreCase(e.fp())) {
                     String name = MmText.clean(e.name(), 32);
                     if (!name.isBlank()) {
                        return name;
                     }
                  }
               }
            }

            return "Discord member";
         }
      }
   }

   public static String roleFor(String fpHex, boolean self) {
      if (self) {
         return RiptideDiscordLogin.role();
      } else if (fpHex != null && !fpHex.isEmpty()) {
         MatchmakingManager mm = get();
         MmPeer peer = mm == null ? null : mm.peer(fpHex);
         String role = peer == null ? null : peer.role;
         return role != null && !role.isBlank() ? role : "user";
      } else {
         return "user";
      }
   }

   public static boolean isGradientName(String fpHex, boolean self) {
      return MmRoleColors.isGradient(roleFor(fpHex, self));
   }

   public static int nameColor(String fpHex, boolean self) {
      return MmRoleColors.roleColor(roleFor(fpHex, self));
   }

   public static int gradientNameColor(String fpHex, boolean self, int index, int length) {
      return MmRoleColors.gradientNameColor(roleFor(fpHex, self), index, length);
   }

   public static enum ConnState {
      CONNECTING,
      ONLINE,
      OFFLINE;
   }

   public static enum JoinPhase {
      NONE,
      RESERVING,
      CONNECTING,
      FAILED;
   }

   private record KeyEpoch(int epoch, byte[] key) {
   }

   public record RosterEntry(String did, String fp, String name, boolean admin, boolean speaker, String role) {
   }

   private static final class SentContent {
      final String type;
      final long sentMs;
      final Set<String> confirmedFps = ConcurrentHashMap.newKeySet();

      SentContent(String type, long sentMs) {
         this.type = type;
         this.sentMs = sentMs;
      }
   }
}
