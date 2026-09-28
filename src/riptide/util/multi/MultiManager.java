package riptide.util.multi;

import com.mojang.authlib.GameProfile;
import com.mojang.util.UndashedUuid;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import riptide.commands.RiptideCommands;
import riptide.gui.screen.RiptideMultiPasswordPromptScreen;
import riptide.util.RiptideAccount;
import riptide.util.RiptideAccountManager;
import riptide.util.RiptideAccountSessionSwitcher;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroManager;
import riptide.util.RiptideNotifications;
import riptide.util.RiptidePacketRegistry;
import riptide.util.RiptideProxy;
import riptide.util.RiptideProxyManager;
import riptide.util.RiptideProxyType;
import riptide.util.RiptideRuntimeActivity;
import riptide.util.macro.WaitForMacroStepAction;

public final class MultiManager implements MultiSession.Sink {
   private static volatile MultiManager.UiLifecycleListener uiLifecycleListener;
   private static volatile MultiManager instance;
   private static final int CHAT_LIMIT = 100;
   private static final long CHAT_MERGE_MS = 2000L;
   private static final long SYS_MERGE_MS = 20000L;
   private static final int PROXY_SAMPLE_COUNT = 3;
   private static final int PROXY_CANDIDATE_CAP = 100;
   private static final long PROXY_SAMPLE_MAX_MS = 30000L;
   private static final long SNAPSHOT_INTERVAL_MS = 100L;
   private static final long SNAPSHOT_DEMAND_NANOS = TimeUnit.SECONDS.toNanos(1L);
   private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "Riptide-Multi-Scheduler");
      thread.setDaemon(true);
      return thread;
   });
   private final ExecutorService workers = Executors.newFixedThreadPool(Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors())), runnable -> {
      Thread thread = new Thread(runnable, "Riptide-Multi-Worker");
      thread.setDaemon(true);
      return thread;
   });
   private final AtomicLong generations = new AtomicLong();
   private final Map<String, MultiSession> sessions = new LinkedHashMap<>();
   private volatile List<MultiSession> sessionList = List.of();
   private volatile List<MultiSession.Snapshot> snapshotList = List.of();
   private volatile long snapshotDemandUntilNanos;
   private volatile Map<String, MultiSession> sessionsById = Map.of();
   private volatile long sessionRevision;
   private volatile long uiRevision;
   private final Map<String, MultiProfile.SessionSpec> runtimeSpecs = new HashMap<>();
   private final Map<String, RiptideProxy> runtimeProxies = new HashMap<>();
   private final Set<String> controllableIds = ConcurrentHashMap.newKeySet();
   private static final int MAX_CONTROLLABLE = 1;
   private final Map<String, MultiManager.MacroResumeIntent> macroIntents = new HashMap<>();
   private volatile Map<String, String> assignedMacroNames = Map.of();
   private final ArrayDeque<MultiManager.Pending> pending = new ArrayDeque<>();
   private final Set<MultiSession> connecting = new HashSet<>();
   private final Map<UUID, String> resolvedIdentities = new HashMap<>();
   private final Map<String, String> lastFailedProxyIds = new HashMap<>();
   private final Map<String, MultiSession.Status> lastPostedStatus = new HashMap<>();
   private final Map<String, Long> retryTokens = new HashMap<>();
   private final Set<String> retryingAccounts = new HashSet<>();
   private final Map<String, ArrayDeque<MultiManager.ChatMsg>> accountChat = new HashMap<>();
   private final ArrayDeque<MultiManager.UnifiedMsg> unifiedChat = new ArrayDeque<>();
   private final Map<String, MultiManager.UnifiedMsg> recentByText = new HashMap<>();
   private long chatSeq;
   private volatile long chatRevision;
   private final ArrayDeque<String> commandHistory = new ArrayDeque<>();
   private String suggestSourceId = "";
   private int suggestId = -1;
   private String suggestText = "";
   private volatile long generation;
   private long retrySerial;
   private boolean passwordPromptShown;
   private MultiProfile activeProfile;
   private UUID renderedProfileIdAtStart;
   private long nextStartAt;
   private long lastSnapshotPublishAt;
   private boolean active;
   private MultiViaCompat.Target viaTarget = new MultiViaCompat.Target(false, null, "Native");
   private ScheduledFuture<?> tickTask;
   private String rememberedServerAddress = "";
   private MultiViaCompat.Target rememberedServerTarget;
   private boolean deferRepublish;
   private static final int HISTORY_LIMIT = 50;
   private static final long ISSUE_SUMMARY_MS = 15000L;
   private final Map<String, long[]> connectionIssues = new HashMap<>();

   public static void setUiLifecycleListener(MultiManager.UiLifecycleListener listener) {
      uiLifecycleListener = listener;
   }

   private static void fireBatchEnded() {
      MultiManager.UiLifecycleListener listener = uiLifecycleListener;
      if (listener != null) {
         try {
            listener.batchEnded();
         } catch (Throwable var2) {
         }
      }
   }

   private static void fireSessionDropped(String accountId) {
      MultiManager.UiLifecycleListener listener = uiLifecycleListener;
      if (listener != null) {
         try {
            listener.sessionDropped(accountId);
         } catch (Throwable var3) {
         }
      }
   }

   private static void fireMenuClosed(String accountId) {
      MultiManager.UiLifecycleListener listener = uiLifecycleListener;
      if (listener != null) {
         try {
            listener.menuClosed(accountId);
         } catch (Throwable var3) {
         }
      }
   }

   private static String groupKey(String source, String text) {
      return (source == null ? "" : source) + "\u0000" + text;
   }

   private static String systemKey(String text) {
      return "\u0000sys\u0000" + text;
   }

   private MultiManager() {
   }

   public static MultiManager get() {
      MultiManager current = instance;
      if (current != null) {
         return current;
      } else {
         synchronized (MultiManager.class) {
            if (instance == null) {
               instance = new MultiManager();
            }

            return instance;
         }
      }
   }

   public static MultiManager getIfInitialized() {
      return instance;
   }

   public synchronized MultiManager.StartResult start(MultiProfile source) {
      if (source == null) {
         return MultiManager.StartResult.error("Profile is missing");
      } else if (this.active) {
         return MultiManager.StartResult.error("Disconnect the active Multi batch first");
      } else {
         MultiProfile profile = new MultiProfile(source);
         profile.normalize();
         UUID renderedProfileId = currentRenderedProfileId();
         boolean hadSessions = !profile.sessions.isEmpty();
         profile.sessions.removeIf(spec -> isCurrentRenderedAccount(spec.accountId()));
         if (hadSessions && profile.sessions.isEmpty()) {
            return MultiManager.StartResult.error("You're playing on this account. Pick another.");
         } else {
            MultiManager.StartResult validation = this.validate(profile, renderedProfileId);
            if (!validation.ok()) {
               return validation;
            } else {
               ServerAddress server = ServerAddress.parseString(profile.serverAddress);
               MultiViaCompat.Target selectedVia = MultiViaCompat.captureSelectedTarget();
               if (MultiViaCompat.isAutoDetect(selectedVia)
                  && profile.serverAddress.equalsIgnoreCase(this.rememberedServerAddress)
                  && this.rememberedServerTarget != null
                  && this.rememberedServerTarget.version() != null) {
                  selectedVia = this.rememberedServerTarget;
               }

               String viaError = MultiViaCompat.validateSelectedTarget(selectedVia);
               if (!viaError.isBlank()) {
                  return MultiManager.StartResult.error(viaError);
               } else {
                  this.generation = this.generations.incrementAndGet();
                  this.activeProfile = profile;
                  this.renderedProfileIdAtStart = renderedProfileId;
                  this.viaTarget = selectedVia;
                  this.active = true;
                  RiptideRuntimeActivity.publish(512L, true);
                  this.ensureTicking();
                  this.nextStartAt = 0L;
                  this.lastSnapshotPublishAt = 0L;
                  this.sessions.clear();
                  this.republishSessions();
                  this.runtimeSpecs.clear();
                  this.runtimeProxies.clear();
                  this.macroIntents.clear();
                  this.rebuildAssignedMacroNames();
                  this.pending.clear();
                  this.connecting.clear();
                  this.resolvedIdentities.clear();
                  this.lastFailedProxyIds.clear();
                  this.lastPostedStatus.clear();
                  this.retryTokens.clear();
                  this.retryingAccounts.clear();
                  this.passwordPromptShown = false;
                  this.clearChat();
                  long startedGeneration = this.generation;
                  boolean auto = profile.proxyMode == MultiProfile.ProxyMode.Auto;
                  this.appendSystem(auto ? "Verifying proxies for " + profile.serverAddress : "Resolving " + profile.serverAddress);
                  CompletableFuture.runAsync(
                     () -> {
                        try {
                           Map<String, RiptideProxy> assignment = switch (profile.proxyMode) {
                              case Auto -> this.verifyAndAssign(startedGeneration, profile, server.getHost(), server.getPort());
                              case Manual -> assignManualProxies(profile, RiptideProxyManager.get().all());
                              case Off -> Map.of();
                           };
                           if (!this.isCurrent(startedGeneration)) {
                              return;
                           }

                           InetSocketAddress resolved = ServerNameResolver.DEFAULT
                              .resolveAddress(server)
                              .<InetSocketAddress>map(ResolvedServerAddress::asInetSocketAddress)
                              .orElse(null);
                           Minecraft.getInstance().execute(() -> this.buildAndStartSessions(startedGeneration, profile, assignment, resolved, server));
                        } catch (Throwable var7x) {
                           String message = "Start failed: "
                              + singleLine(var7x.getMessage() == null ? var7x.getClass().getSimpleName() : var7x.getMessage(), 120);
                           Minecraft.getInstance().execute(() -> this.buildFailedSessions(startedGeneration, profile, message));
                        }
                     },
                     this.workers
                  );
                  return MultiManager.StartResult.success();
               }
            }
         }
      }
   }

   private synchronized void buildAndStartSessions(
      long gen, MultiProfile profile, Map<String, RiptideProxy> assignment, InetSocketAddress resolved, ServerAddress server
   ) {
      if (this.active && this.generation == gen) {
         boolean dnsOk = resolved != null;
         this.appendSystem(dnsOk ? "Starting " + profile.name + " on " + profile.serverAddress : "Unknown server address");

         for (MultiProfile.SessionSpec spec : profile.sessions) {
            RiptideProxy proxy = perModeProxy(profile, spec, assignment);
            boolean proxyRequired = profile.proxyMode != MultiProfile.ProxyMode.Off && !spec.direct();
            boolean missingProxy = proxyRequired && proxy == null;
            RiptideProxy snapshot = copyProxy(proxy);
            String proxyName = snapshot != null ? snapshot.displayName() : (proxyRequired ? "No proxy" : "Proxy Off");
            MultiSession session = new MultiSession(
               this.generation,
               spec,
               snapshot,
               proxyName,
               profile.packetPolicy,
               profile.loginMode,
               profile.name,
               profile.openFormValues(spec.accountId()),
               this,
               this.workers,
               this.viaTarget
            );
            session.setAutoAccept(profile.autoAccept);
            this.armCapture(session);
            this.sessions.put(spec.accountId(), session);
            this.runtimeSpecs.put(spec.accountId(), runtimeSpecFor(profile, spec, proxy));
            this.runtimeProxies.put(spec.accountId(), snapshot);
            if (!dnsOk) {
               session.failExternal("Unknown server address");
            } else if (missingProxy) {
               session.failExternal("No working proxy");
            } else {
               this.pending.addLast(new MultiManager.Pending(session, resolved, server.getHost(), server.getPort()));
            }

            this.armJoinMacro(profile, spec, session);
         }

         this.republishSessions();
         if (dnsOk) {
            this.pump();
         }

         if (profile.loginMode == MultiProfile.LoginMode.Custom) {
            LinkedHashSet<String> names = new LinkedHashSet<>();

            for (MultiProfile.SessionSpec spec : profile.sessions) {
               String name = spec.macroName().isBlank() ? profile.allMacroName : spec.macroName();
               if (name != null && !name.isBlank()) {
                  names.add(name);
               }
            }

            for (String name : names) {
               RiptideMacro macro = RiptideMacroManager.get().get(name);
               if (macro != null) {
                  this.warnMacroCompatibility(macro);
               }
            }
         }
      }
   }

   private void republishSessions() {
      if (!this.deferRepublish) {
         this.sessionList = List.copyOf(this.sessions.values());
         this.sessionsById = Map.copyOf(this.sessions);
         this.publishSnapshots(true);
         this.sessionRevision++;
      }
   }

   private synchronized void buildFailedSessions(long gen, MultiProfile profile, String reason) {
      if (this.active && this.generation == gen && this.sessions.isEmpty()) {
         this.appendSystem(reason);

         for (MultiProfile.SessionSpec spec : profile.sessions) {
            MultiSession session = new MultiSession(
               gen,
               spec,
               null,
               "Proxy Off",
               profile.packetPolicy,
               profile.loginMode,
               profile.name,
               profile.openFormValues(spec.accountId()),
               this,
               this.workers,
               this.viaTarget
            );
            this.sessions.put(spec.accountId(), session);
            this.runtimeSpecs.put(spec.accountId(), spec);
            this.runtimeProxies.put(spec.accountId(), null);
            this.armJoinMacro(profile, spec, session);
            session.failExternal(reason);
         }

         this.republishSessions();
      }
   }

   private static RiptideProxy perModeProxy(MultiProfile profile, MultiProfile.SessionSpec spec, Map<String, RiptideProxy> assignment) {
      return switch (profile.proxyMode) {
         case Auto -> (RiptideProxy)assignment.get(spec.accountId());
         case Manual -> spec.direct() ? null : (RiptideProxy)assignment.get(spec.accountId());
         case Off -> null;
      };
   }

   private static MultiProfile.SessionSpec runtimeSpecFor(MultiProfile profile, MultiProfile.SessionSpec spec, RiptideProxy proxy) {
      return switch (profile.proxyMode) {
         case Auto -> new MultiProfile.SessionSpec(spec.accountId(), proxy == null ? "" : proxy.stableId());
         case Manual -> spec;
         case Off -> new MultiProfile.SessionSpec(spec.accountId(), "");
      };
   }

   private MultiManager.StartResult validate(MultiProfile profile, UUID renderedProfileId) {
      if (profile.serverAddress.isBlank()) {
         return MultiManager.StartResult.error("Server address is required");
      } else if (profile.sessions.isEmpty()) {
         return MultiManager.StartResult.error("Select at least one account");
      } else if (profile.sessions.size() > 500) {
         return MultiManager.StartResult.error("Maximum 500 sessions");
      } else {
         boolean manual = profile.proxyMode == MultiProfile.ProxyMode.Manual;
         Set<String> accountIds = new HashSet<>();
         Set<String> identities = new HashSet<>();
         int manualBestRows = 0;

         for (MultiProfile.SessionSpec spec : profile.sessions) {
            if (!accountIds.add(spec.accountId())) {
               return MultiManager.StartResult.error("Duplicate account row");
            }

            String identity;
            if ("default".equals(spec.accountId())) {
               UUID defaultProfileId = RiptideAccountSessionSwitcher.getOriginalUser().getProfileId();
               if (renderedProfileId != null && renderedProfileId.equals(defaultProfileId)) {
                  return MultiManager.StartResult.error("You're playing on this account.");
               }

               identity = defaultProfileId.toString();
            } else {
               RiptideAccount account = RiptideAccountManager.get().findById(spec.accountId());
               if (account == null) {
                  return MultiManager.StartResult.error("Missing account: " + spec.accountId());
               }

               UUID knownProfileId = parseKnownProfileId(account.uuid);
               if (renderedProfileId != null && renderedProfileId.equals(knownProfileId)) {
                  return MultiManager.StartResult.error("You're playing on this account.");
               }

               identity = account.uuid != null && !account.uuid.isBlank()
                  ? account.uuid.toLowerCase(Locale.ROOT)
                  : account.type.name() + ":" + account.displayName().toLowerCase(Locale.ROOT);
            }

            if (!identities.add(identity)) {
               return MultiManager.StartResult.error("The same Minecraft identity is selected twice");
            }

            if (manual) {
               if (spec.bestProxy()) {
                  manualBestRows++;
               } else if (!spec.direct()) {
                  RiptideProxy proxy = RiptideProxyManager.get().findById(spec.proxyId());
                  if (proxy == null || !proxy.isValid()) {
                     return MultiManager.StartResult.error("Missing proxy for " + accountLabel(spec.accountId()));
                  }
               }
            }
         }

         List<RiptideProxy> usableProxies = distinctUsableProxies(RiptideProxyManager.get().all());
         if (profile.proxyMode == MultiProfile.ProxyMode.Auto && usableProxies.isEmpty()) {
            return MultiManager.StartResult.error("Add proxies or set Proxy: Off");
         } else {
            return manual && manualBestRows > 0 && usableProxies.isEmpty()
               ? MultiManager.StartResult.error("No proxy is available for Manual Best Proxy rows")
               : MultiManager.StartResult.success();
         }
      }
   }

   private static UUID currentRenderedProfileId() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft == null) {
         return null;
      } else {
         ClientPacketListener connection = minecraft.getConnection();
         if (connection == null) {
            return null;
         } else {
            GameProfile profile = connection.getLocalGameProfile();
            return profile == null ? null : profile.id();
         }
      }
   }

   public static String renderedServerName() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null && minecraft.getConnection() != null) {
         GameProfile profile = minecraft.getConnection().getLocalGameProfile();
         return profile == null ? "" : profile.name();
      } else {
         return "";
      }
   }

   public boolean isRenderedOnActiveServer() {
      if (this.active && this.activeProfile != null) {
         Minecraft minecraft = Minecraft.getInstance();
         return minecraft != null && minecraft.getConnection() != null && minecraft.getCurrentServer() != null
            ? sameServer(this.activeProfile.serverAddress, minecraft.getCurrentServer().ip)
            : false;
      } else {
         return false;
      }
   }

   static boolean sameServer(String a, String b) {
      if (a != null && b != null) {
         try {
            ServerAddress sa = ServerAddress.parseString(a.trim());
            ServerAddress sb = ServerAddress.parseString(b.trim());
            return sa.getHost().equalsIgnoreCase(sb.getHost()) && sa.getPort() == sb.getPort();
         } catch (RuntimeException var4) {
            return a.trim().equalsIgnoreCase(b.trim());
         }
      } else {
         return false;
      }
   }

   static UUID parseKnownProfileId(String value) {
      if (value != null && !value.isBlank()) {
         try {
            return UndashedUuid.fromStringLenient(value.trim());
         } catch (RuntimeException var4) {
            try {
               return UUID.fromString(value.trim());
            } catch (RuntimeException var3) {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private static UUID accountProfileId(String accountId) {
      if (accountId == null) {
         return null;
      } else if ("default".equals(accountId)) {
         User user = RiptideAccountSessionSwitcher.getOriginalUser();
         return user == null ? null : user.getProfileId();
      } else {
         RiptideAccount account = RiptideAccountManager.get().findById(accountId);
         if (account == null) {
            return null;
         } else {
            UUID known = parseKnownProfileId(account.uuid);
            if (known != null) {
               return known;
            } else {
               String name = account.username != null && !account.username.isBlank() ? account.username : account.displayName();
               return name != null && !name.isBlank() ? UUIDUtil.createOfflinePlayerUUID(name) : null;
            }
         }
      }
   }

   public static boolean isCurrentRenderedAccount(String accountId) {
      if (accountId == null) {
         return false;
      } else {
         Minecraft minecraft = Minecraft.getInstance();
         ClientPacketListener connection = minecraft == null ? null : minecraft.getConnection();
         if (connection == null) {
            return false;
         } else {
            GameProfile rendered = connection.getLocalGameProfile();
            if (rendered == null) {
               return false;
            } else {
               UUID target = accountProfileId(accountId);
               if (target != null && target.equals(rendered.id())) {
                  return true;
               } else {
                  String renderedName = rendered.name();
                  String accountName = accountUsername(accountId);
                  return renderedName != null
                     && !renderedName.isBlank()
                     && accountName != null
                     && !accountName.isBlank()
                     && renderedName.equalsIgnoreCase(accountName);
               }
            }
         }
      }
   }

   private static String accountUsername(String accountId) {
      if ("default".equals(accountId)) {
         User user = RiptideAccountSessionSwitcher.getOriginalUser();
         return user == null ? null : user.getName();
      } else {
         RiptideAccount account = RiptideAccountManager.get().findById(accountId);
         if (account == null) {
            return null;
         } else {
            return account.username != null && !account.username.isBlank() ? account.username : account.displayName();
         }
      }
   }

   static Map<String, RiptideProxy> assignManualProxies(MultiProfile profile, List<RiptideProxy> available) {
      if (profile == null) {
         return Map.of();
      } else {
         List<RiptideProxy> usable = distinctUsableProxies(available);
         List<RiptideProxy> allValid = available == null ? List.of() : available.stream().filter(proxy -> proxy != null && proxy.isValid()).toList();
         Map<String, RiptideProxy> assignment = new LinkedHashMap<>();
         Set<String> explicitlyUsed = new HashSet<>();

         for (MultiProfile.SessionSpec spec : profile.sessions) {
            if (!spec.direct() && !spec.bestProxy()) {
               RiptideProxy selected = findProxyById(allValid, spec.proxyId());
               if (selected != null) {
                  assignment.put(spec.accountId(), selected);
                  explicitlyUsed.add(proxyLeaseKey(selected));
               }
            }
         }

         List<RiptideProxy> bestOrder = new ArrayList<>(usable.size());

         for (RiptideProxy candidate : usable) {
            if (!explicitlyUsed.contains(proxyLeaseKey(candidate))) {
               bestOrder.add(candidate);
            }
         }

         for (RiptideProxy candidatex : usable) {
            if (explicitlyUsed.contains(proxyLeaseKey(candidatex))) {
               bestOrder.add(candidatex);
            }
         }

         int bestIndex = 0;

         for (MultiProfile.SessionSpec specx : profile.sessions) {
            if (specx.bestProxy()) {
               RiptideProxy selected = bestOrder.isEmpty() ? null : bestOrder.get(bestIndex++ % bestOrder.size());
               if (selected != null) {
                  assignment.put(specx.accountId(), selected);
               }
            }
         }

         return assignment;
      }
   }

   private static RiptideProxy findProxyById(List<RiptideProxy> proxies, String id) {
      if (proxies != null && id != null && !id.isBlank()) {
         for (RiptideProxy proxy : proxies) {
            if (id.equals(proxy.stableId())) {
               return proxy;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private synchronized void pump() {
      if (this.active && this.activeProfile != null) {
         int limit = this.activeProfile.concurrency();

         while (this.connecting.size() < limit && !this.pending.isEmpty()) {
            MultiManager.Pending next = this.pending.removeFirst();
            this.connecting.add(next.session());
            long now = System.currentTimeMillis();
            long at = Math.max(now, this.nextStartAt);
            this.nextStartAt = at + this.activeProfile.delayMs();
            this.scheduler.schedule(() -> {
               synchronized (this) {
                  if (!this.active || next.session().generation() != this.generation || this.sessions.get(next.session().accountId()) != next.session()) {
                     this.connecting.remove(next.session());
                     this.pump();
                     return;
                  }
               }

               next.session().start(next.address(), next.host(), next.port());
            }, Math.max(0L, at - now), TimeUnit.MILLISECONDS);
         }
      }
   }

   public synchronized MultiManager.RetryResult retry(String accountId) {
      if (this.active && this.activeProfile != null && accountId != null) {
         MultiSession old = this.sessions.get(accountId);
         if (old != null && old.connected()) {
            return MultiManager.RetryResult.error("Connected");
         } else if (old != null && !isRetryable(old.statusValue())) {
            return MultiManager.RetryResult.error("Still connecting");
         } else if (!this.retryingAccounts.add(accountId)) {
            return MultiManager.RetryResult.error("Already retrying");
         } else {
            long retryToken = ++this.retrySerial;
            this.retryTokens.put(accountId, retryToken);
            MultiProfile.SessionSpec spec = this.runtimeSpecs.get(accountId);
            if (spec == null) {
               this.retryingAccounts.remove(accountId);
               return MultiManager.RetryResult.error("No session");
            } else {
               ServerAddress server = ServerAddress.parseString(this.activeProfile.serverAddress);
               if (this.activeProfile.proxyMode == MultiProfile.ProxyMode.Auto) {
                  List<RiptideProxy> candidates = this.orderedAutoRetryCandidates(accountId);
                  if (candidates.isEmpty()) {
                     this.retryingAccounts.remove(accountId);
                     this.appendSystem(accountLabel(accountId) + ": No proxy");
                     return MultiManager.RetryResult.error("No proxy");
                  } else {
                     long retryGeneration = this.generation;
                     int target = this.activeProfile.autoMaxPingMs;
                     CompletableFuture.runAsync(() -> {
                        try {
                           this.retryAutoAsync(retryGeneration, retryToken, accountId, candidates, server, target);
                        } catch (Throwable var13x) {
                           synchronized (this) {
                              if (!this.active || this.generation != retryGeneration || this.retryTokens.getOrDefault(accountId, -1L) != retryToken) {
                                 return;
                              }

                              this.retryingAccounts.remove(accountId);
                              this.appendSystem(accountLabel(accountId) + ": Retry failed");
                           }
                        }
                     }, this.workers);
                     this.appendSystem(accountLabel(accountId) + ": Verifying proxy");
                     return MultiManager.RetryResult.ok("Verifying proxy");
                  }
               } else {
                  RiptideProxy previous = this.runtimeProxies.get(accountId);
                  String currentProxyId = previous == null ? spec.proxyId() : previous.stableId();
                  boolean proxyEnabled = this.activeProfile.proxyMode == MultiProfile.ProxyMode.Manual && !spec.direct();
                  if (!proxyEnabled) {
                     currentProxyId = "";
                  }

                  String lastFailedProxyId = this.lastFailedProxyIds.getOrDefault(accountId, currentProxyId == null ? "" : currentProxyId);
                  RiptideProxy selectedProxy = null;
                  if (proxyEnabled) {
                     if (spec.bestProxy()) {
                        selectedProxy = this.selectPreferredRetryProxy(accountId, currentProxyId, lastFailedProxyId);
                     } else {
                        RiptideProxy configured = RiptideProxyManager.get().findById(spec.proxyId());
                        selectedProxy = configured != null ? configured : previous;
                     }
                  }

                  if (selectedProxy == null && proxyEnabled) {
                     this.retryingAccounts.remove(accountId);
                     this.appendSystem(accountLabel(accountId) + ": No proxy");
                     return MultiManager.RetryResult.error("No proxy");
                  } else {
                     MultiProfile.SessionSpec retrySpec = this.activeProfile.proxyMode == MultiProfile.ProxyMode.Manual
                        ? spec
                        : new MultiProfile.SessionSpec(accountId, "");
                     RiptideProxy proxy = copyProxy(selectedProxy);
                     this.runtimeSpecs.put(accountId, retrySpec);
                     this.runtimeProxies.put(accountId, proxy);
                     MultiSession replacement = new MultiSession(
                        this.generation,
                        retrySpec,
                        proxy,
                        proxy == null ? "Proxy Off" : proxy.displayName(),
                        this.activeProfile.packetPolicy,
                        this.activeProfile.loginMode,
                        this.activeProfile.name,
                        this.activeProfile.openFormValues(accountId),
                        this,
                        this.workers,
                        this.viaTarget
                     );
                     replacement.setAutoAccept(this.activeProfile.autoAccept);
                     this.armCapture(replacement);
                     this.pending.removeIf(next -> next.session() == old);
                     this.connecting.remove(old);
                     this.sessions.put(accountId, replacement);
                     if (old != null) {
                        old.disconnect("Retrying");
                     }

                     this.rearmMacroIfRunning(accountId, replacement);
                     this.republishSessions();
                     long retryGeneration = this.generation;
                     CompletableFuture.<InetSocketAddress>supplyAsync(
                           () -> ServerNameResolver.DEFAULT
                              .resolveAddress(server)
                              .<InetSocketAddress>map(ResolvedServerAddress::asInetSocketAddress)
                              .orElse(null),
                           this.workers
                        )
                        .whenComplete(
                           (resolved, error) -> {
                              synchronized (this) {
                                 if (this.active
                                    && this.generation == retryGeneration
                                    && this.sessions.get(accountId) == replacement
                                    && this.retryTokens.getOrDefault(accountId, -1L) == retryToken) {
                                    this.retryingAccounts.remove(accountId);
                                    if (error == null && resolved != null) {
                                       this.pending.addLast(new MultiManager.Pending(replacement, resolved, server.getHost(), server.getPort()));
                                       this.pump();
                                    } else {
                                       replacement.failExternal("Unknown server address");
                                    }
                                 }
                              }
                           }
                        );
                     String label = proxy == null ? "Proxy Off" : proxy.displayName();
                     String message = "Retry: " + singleLine(label, 32);
                     this.appendSystem(accountLabel(accountId) + ": " + message);
                     return MultiManager.RetryResult.ok(message);
                  }
               }
            }
         }
      } else {
         return MultiManager.RetryResult.error("No batch");
      }
   }

   private synchronized List<RiptideProxy> orderedAutoRetryCandidates(String accountId) {
      String lastFailed = this.lastFailedProxyIds.getOrDefault(accountId, "");
      Set<String> inUseByOthers = new HashSet<>();

      for (Entry<String, RiptideProxy> entry : this.runtimeProxies.entrySet()) {
         if (!entry.getKey().equals(accountId) && entry.getValue() != null) {
            inUseByOthers.add(proxyLeaseKey(entry.getValue()));
         }
      }

      List<RiptideProxy> all = distinctUsableProxies(RiptideProxyManager.get().all());
      List<RiptideProxy> primary = new ArrayList<>();
      List<RiptideProxy> fallback = new ArrayList<>();

      for (RiptideProxy proxy : all) {
         if (!proxy.stableId().equals(lastFailed) && !inUseByOthers.contains(proxyLeaseKey(proxy))) {
            primary.add(proxy);
         } else {
            fallback.add(proxy);
         }
      }

      primary.addAll(fallback);
      return primary;
   }

   private synchronized RiptideProxy selectPreferredRetryProxy(String accountId, String currentProxyId, String lastFailedProxyId) {
      Set<String> inUseByOthers = new HashSet<>();

      for (Entry<String, RiptideProxy> entry : this.runtimeProxies.entrySet()) {
         if (!entry.getKey().equals(accountId) && entry.getValue() != null) {
            inUseByOthers.add(proxyLeaseKey(entry.getValue()));
         }
      }

      List<RiptideProxy> preferred = new ArrayList<>();
      List<RiptideProxy> shared = new ArrayList<>();

      for (RiptideProxy proxy : distinctUsableProxies(RiptideProxyManager.get().all())) {
         if (inUseByOthers.contains(proxyLeaseKey(proxy))) {
            shared.add(proxy);
         } else {
            preferred.add(proxy);
         }
      }

      preferred.addAll(shared);
      return selectRetryProxy(preferred, currentProxyId, lastFailedProxyId, false);
   }

   private void retryAutoAsync(long gen, long retryToken, String accountId, List<RiptideProxy> candidates, ServerAddress server, int target) {
      int timeout = probeTimeoutMs(target);
      MultiManager.Sample best = null;
      int tries = 0;

      for (RiptideProxy candidate : candidates) {
         if (!this.isCurrentRetry(gen, retryToken, accountId)) {
            return;
         }

         if (tries++ >= 6) {
            break;
         }

         MultiManager.Sample sample = this.sampleOne(gen, candidate, server.getHost(), server.getPort(), timeout);
         if (sample == null) {
            return;
         }

         if (sample.ok()) {
            if (sample.pingMs() <= target) {
               best = sample;
               break;
            }

            if (best == null || sample.pingMs() < best.pingMs()) {
               best = sample;
            }
         }
      }

      if (this.isCurrentRetry(gen, retryToken, accountId)) {
         InetSocketAddress resolved = ServerNameResolver.DEFAULT
            .resolveAddress(server)
            .<InetSocketAddress>map(ResolvedServerAddress::asInetSocketAddress)
            .orElse(null);
         RiptideProxy chosenFinal = best == null ? null : best.proxy();
         Minecraft.getInstance().execute(() -> this.applyAutoRetry(gen, retryToken, accountId, chosenFinal, resolved, server));
      }
   }

   private synchronized void applyAutoRetry(long gen, long retryToken, String accountId, RiptideProxy chosen, InetSocketAddress resolved, ServerAddress server) {
      if (this.active && this.generation == gen && this.activeProfile != null && this.retryTokens.getOrDefault(accountId, -1L) == retryToken) {
         this.retryingAccounts.remove(accountId);
         MultiSession old = this.sessions.get(accountId);
         if (old == null || !old.connected()) {
            if (chosen == null) {
               this.appendSystem(accountLabel(accountId) + ": No working proxy");
               if (old != null) {
                  old.failExternal("No working proxy");
               }
            } else if (resolved == null) {
               this.appendSystem("Unknown server address");
               if (old != null) {
                  old.failExternal("Unknown server address");
               }
            } else {
               this.pending.removeIf(next -> next.session() == old);
               this.connecting.remove(old);
               RiptideProxy proxy = copyProxy(chosen);
               MultiProfile.SessionSpec retrySpec = new MultiProfile.SessionSpec(accountId, chosen.stableId());
               this.runtimeSpecs.put(accountId, retrySpec);
               this.runtimeProxies.put(accountId, proxy);
               MultiSession replacement = new MultiSession(
                  this.generation,
                  retrySpec,
                  proxy,
                  proxy.displayName(),
                  this.activeProfile.packetPolicy,
                  this.activeProfile.loginMode,
                  this.activeProfile.name,
                  this.activeProfile.openFormValues(accountId),
                  this,
                  this.workers,
                  this.viaTarget
               );
               replacement.setAutoAccept(this.activeProfile.autoAccept);
               this.armCapture(replacement);
               this.sessions.put(accountId, replacement);
               if (old != null) {
                  old.disconnect("Retrying");
               }

               this.rearmMacroIfRunning(accountId, replacement);
               this.republishSessions();
               this.pending.addLast(new MultiManager.Pending(replacement, resolved, server.getHost(), server.getPort()));
               this.pump();
               this.appendSystem(accountLabel(accountId) + ": Retry " + singleLine(proxy.displayName(), 32));
            }
         }
      }
   }

   public synchronized MultiManager.RetryResult retryAllDisconnected() {
      if (this.active && this.activeProfile != null) {
         int attempted = 0;
         this.deferRepublish = true;

         try {
            for (String accountId : new ArrayList<>(this.sessions.keySet())) {
               MultiSession session = this.sessions.get(accountId);
               if (session != null && isRetryable(session.statusValue()) && this.retry(accountId).ok()) {
                  attempted++;
               }
            }
         } finally {
            this.deferRepublish = false;
         }

         this.republishSessions();
         return attempted == 0 ? MultiManager.RetryResult.error("Nothing to retry") : MultiManager.RetryResult.ok("Retrying " + attempted);
      } else {
         return MultiManager.RetryResult.error("No batch");
      }
   }

   private Map<String, RiptideProxy> verifyAndAssign(long gen, MultiProfile profile, String host, int port) {
      List<RiptideProxy> candidates = new ArrayList<>();

      for (RiptideProxy proxy : RiptideProxyManager.get().all()) {
         if (proxy != null && proxy.isValid() && proxy.status != RiptideProxy.Status.DEAD) {
            candidates.add(proxy);
         }
      }

      reserveMainProxyEndpoint(candidates);
      candidates.sort(
         Comparator.comparingInt(MultiManager::retryRank)
            .thenComparingLong(proxyx -> proxyx.status == RiptideProxy.Status.ALIVE && proxyx.latency > 0L ? proxyx.latency : Long.MAX_VALUE)
      );
      Map<String, RiptideProxy> assignment = new LinkedHashMap<>();
      if (candidates.isEmpty()) {
         this.post(gen, "No proxies to verify");
         return assignment;
      } else {
         int needed = (int)profile.sessions.stream().filter(spec -> !spec.direct()).count();
         int target = profile.autoMaxPingMs;
         List<RiptideProxy> poolList = new ArrayList<>(candidates.subList(0, Math.min(candidates.size(), 100)));
         int timeout = probeTimeoutMs(target);
         int wantUnderTarget = Math.min(poolList.size(), Math.max(needed + 3, 8));
         ExecutorCompletionService<MultiManager.Sample> service = new ExecutorCompletionService<>(this.workers);
         List<Future<MultiManager.Sample>> probeTasks = new ArrayList<>(poolList.size());

         for (RiptideProxy candidate : poolList) {
            probeTasks.add(service.submit(() -> this.sampleOne(gen, candidate, host, port, timeout)));
         }

         this.post(gen, "Testing " + poolList.size() + (poolList.size() == 1 ? " proxy" : " proxies") + " (target " + target + "ms)");
         long deadline = System.currentTimeMillis() + 30000L;
         List<MultiManager.Sample> working = new ArrayList<>();
         int underTarget = 0;

         for (int i = 0; i < poolList.size() && this.isCurrent(gen); i++) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0L) {
               break;
            }

            Future<MultiManager.Sample> done;
            try {
               done = service.poll(remaining, TimeUnit.MILLISECONDS);
            } catch (InterruptedException var26) {
               Thread.currentThread().interrupt();
               break;
            }

            if (done == null) {
               break;
            }

            MultiManager.Sample sample;
            try {
               sample = done.get();
            } catch (Exception var27) {
               continue;
            }

            if (sample != null && sample.ok()) {
               working.add(sample);
               if (sample.pingMs() <= target) {
                  if (++underTarget >= wantUnderTarget) {
                     break;
                  }
               }
            }
         }

         if (working.isEmpty()) {
            cancelProbeTasks(probeTasks);
            this.post(gen, "No working proxy found");
            return assignment;
         } else {
            working.sort(Comparator.comparingLong(MultiManager.Sample::pingMs));
            List<MultiManager.Sample> distinctWorking = new ArrayList<>(working.size());
            Set<String> workingLeases = new HashSet<>();

            for (MultiManager.Sample samplex : working) {
               if (workingLeases.add(proxyLeaseKey(samplex.proxy()))) {
                  distinctWorking.add(samplex);
               }
            }

            int distinct = distinctWorking.size();
            long best = distinctWorking.getFirst().pingMs();
            long underTargetCount = distinctWorking.stream().filter(samplexx -> samplexx.pingMs() <= target).count();
            this.post(gen, "Verified " + distinct + (distinct == 1 ? " proxy" : " proxies") + " (best " + best + "ms)");
            if (underTargetCount < distinct) {
               this.post(gen, "Only " + underTargetCount + " under " + target + "ms; slower proxies stay unique");
            }

            if (distinct < needed) {
               this.post(
                  gen,
                  "Only " + distinct + " distinct working " + (distinct == 1 ? "proxy" : "proxies") + " for " + needed + " accounts; sharing as a last resort"
               );
            }

            assignment.putAll(distributeProxies(profile.sessions, distinctWorking.stream().map(MultiManager.Sample::proxy).toList()));
            cancelProbeTasks(probeTasks);
            return assignment;
         }
      }
   }

   private static void cancelProbeTasks(List<? extends Future<?>> tasks) {
      if (tasks != null) {
         for (Future<?> task : tasks) {
            if (task != null && !task.isDone()) {
               task.cancel(true);
            }
         }
      }
   }

   private MultiManager.Sample sampleOne(long gen, RiptideProxy proxy, String host, int port, int timeout) {
      long[] pings = new long[3];
      RiptideProxyType workingType = proxy == null ? null : proxy.type;

      for (int i = 0; i < 3; i++) {
         if (!this.isCurrent(gen)) {
            return null;
         }

         MultiProxyVerifier.Result result = MultiProxyVerifier.verify(proxy, host, port, timeout);
         if (!result.ok()) {
            return new MultiManager.Sample(proxy, false, 0L);
         }

         if (result.workingType() != null) {
            workingType = result.workingType();
         }

         pings[i] = result.latencyMs();
      }

      Arrays.sort(pings);
      RiptideProxy verified = copyProxy(proxy);
      if (workingType != null) {
         verified.type = workingType;
      }

      return new MultiManager.Sample(verified, true, pings[1]);
   }

   private static int probeTimeoutMs(int targetPingMs) {
      return Math.max(700, Math.min(1500, targetPingMs * 3));
   }

   private synchronized boolean isCurrent(long gen) {
      return this.active && this.generation == gen;
   }

   private synchronized boolean isCurrentRetry(long gen, long retryToken, String accountId) {
      return this.active && this.generation == gen && this.retryTokens.getOrDefault(accountId, -1L) == retryToken;
   }

   private void post(long gen, String message) {
      synchronized (this) {
         if (this.active && this.generation == gen) {
            this.appendSystem(message);
         }
      }
   }

   public synchronized void disconnectSession(String accountId) {
      MultiSession session = this.sessions.get(accountId);
      if (session != null) {
         this.retryTokens.put(accountId, ++this.retrySerial);
         this.retryingAccounts.remove(accountId);
         this.macroIntents.remove(accountId);
         this.pending.removeIf(next -> next.session() == session);
         this.connecting.remove(session);
         session.disconnect("Disconnected by user");
         this.pump();
      }
   }

   public static boolean isRetryable(MultiSession.Status status) {
      return status == MultiSession.Status.FAILED || status == MultiSession.Status.DISCONNECTED;
   }

   public synchronized void disconnectAll(String reason) {
      if (this.active || !this.sessions.isEmpty()) {
         this.generation = this.generations.incrementAndGet();
         this.active = false;
         RiptideRuntimeActivity.publish(512L, false);
         if (this.tickTask != null) {
            this.tickTask.cancel(false);
            this.tickTask = null;
         }

         this.pending.clear();
         this.connecting.clear();
         this.resolvedIdentities.clear();
         this.lastFailedProxyIds.clear();
         this.lastPostedStatus.clear();

         for (MultiSession session : this.sessions.values()) {
            session.disconnect(reason == null ? "Multi stopped" : reason);
         }

         this.sessions.clear();
         this.republishSessions();
         this.runtimeSpecs.clear();
         this.runtimeProxies.clear();
         this.macroIntents.clear();
         this.assignedMacroNames = Map.of();
         this.retryTokens.clear();
         this.retryingAccounts.clear();
         this.controllableIds.clear();
         this.activeProfile = null;
         this.renderedProfileIdAtStart = null;
         this.clearChat();
         fireBatchEnded();
      }
   }

   private void clearChat() {
      this.accountChat.clear();
      this.unifiedChat.clear();
      this.recentByText.clear();
      this.chatSeq = 0L;
      this.chatRevision++;
      this.commandHistory.clear();
      this.suggestSourceId = "";
      this.suggestId = -1;
      this.suggestText = "";
   }

   public synchronized void pushHistory(String line) {
      String value = line == null ? "" : line.trim();
      if (!value.isEmpty()) {
         this.commandHistory.remove(value);
         this.commandHistory.addLast(value);

         while (this.commandHistory.size() > 50) {
            this.commandHistory.removeFirst();
         }

         RiptideClientMessaging.rememberRecentChat(value);
      }
   }

   public synchronized List<String> commandHistory() {
      return List.copyOf(this.commandHistory);
   }

   public synchronized String lastHistoryEntry() {
      return this.commandHistory.peekLast();
   }

   public synchronized void clearHistory() {
      this.commandHistory.clear();
   }

   public synchronized void requestSuggestions(String command, Set<String> scope) {
      if (command != null) {
         MultiSession source = this.representativeReady(scope);
         if (source == null) {
            this.suggestSourceId = "";
            this.suggestId = -1;
            this.suggestText = "";
         } else {
            int id = source.requestSuggestions(command);
            if (id < 0) {
               this.suggestSourceId = "";
               this.suggestId = -1;
               this.suggestText = "";
            } else {
               this.suggestSourceId = source.accountId();
               this.suggestId = id;
               this.suggestText = command;
            }
         }
      }
   }

   public synchronized MultiManager.SuggestionResult suggestions(String command) {
      if (command != null && command.equals(this.suggestText)) {
         MultiSession source = this.sessions.get(this.suggestSourceId);
         if (source == null) {
            return null;
         } else {
            MultiSession.Suggest suggest = source.suggestion(this.suggestId);
            return suggest == null ? null : new MultiManager.SuggestionResult(suggest.start(), suggest.length(), suggest.entries());
         }
      } else {
         return null;
      }
   }

   private MultiSession representativeReady(Set<String> scope) {
      if (scope != null && !scope.isEmpty()) {
         for (String id : scope) {
            MultiSession session = this.sessions.get(id);
            if (session != null && session.ready()) {
               return session;
            }
         }
      }

      for (MultiSession session : this.sessions.values()) {
         if (session.ready()) {
            return session;
         }
      }

      return null;
   }

   public void shutdown() {
      this.disconnectAll("Game closed");
      this.workers.shutdownNow();
      this.scheduler.shutdownNow();
   }

   public synchronized boolean isActive() {
      return this.active;
   }

   public synchronized void rememberSelectedServer(ServerData serverData) {
      if (serverData == null) {
         this.rememberedServerAddress = "";
         this.rememberedServerTarget = null;
      } else {
         this.rememberedServerAddress = serverData.ip == null ? "" : serverData.ip.trim();
         this.rememberedServerTarget = MultiViaCompat.captureServerTarget(serverData);
      }
   }

   public int connectedCount() {
      int count = 0;

      for (MultiSession session : this.sessionList) {
         if (session.connected()) {
            count++;
         }
      }

      return count;
   }

   public int readyCount() {
      int count = 0;

      for (MultiSession session : this.sessionList) {
         if (session.ready()) {
            count++;
         }
      }

      return count;
   }

   public int sessionCount() {
      return this.sessionList.size();
   }

   public String readyFraction() {
      return this.readyCount() + "/" + this.sessionCount();
   }

   public synchronized MultiProfile activeProfile() {
      return this.activeProfile == null ? null : new MultiProfile(this.activeProfile);
   }

   public synchronized void updatePolicy(MultiPacketPolicy policy) {
      if (this.active && this.activeProfile != null && policy != null) {
         this.activeProfile.packetPolicy = new MultiPacketPolicy(policy);

         for (MultiSession session : this.sessions.values()) {
            session.applyPolicy(policy);
         }

         MultiProfileManager.get().put(this.activeProfile);
         this.uiRevision++;
      }
   }

   public synchronized void updateAutoAccept(MultiAutoAccept config) {
      if (this.active && this.activeProfile != null && config != null) {
         this.activeProfile.autoAccept = new MultiAutoAccept(config);

         for (MultiSession session : this.sessions.values()) {
            session.setAutoAccept(this.activeProfile.autoAccept);
         }

         MultiProfileManager.get().put(this.activeProfile);
         this.uiRevision++;
      }
   }

   public synchronized String tpaToMe(String accountId) {
      return this.startTeleport(accountId, this.autoAcceptConfig().tpaToMeCommand, "tpa");
   }

   public synchronized String tpaToBot(String accountId) {
      return this.startTeleport(accountId, this.autoAcceptConfig().tpaToBotCommand, "tpa");
   }

   public synchronized String tradeWith(String accountId) {
      return this.startTeleport(accountId, this.autoAcceptConfig().tradeCommand, "trade");
   }

   private MultiAutoAccept autoAcceptConfig() {
      return this.activeProfile == null ? new MultiAutoAccept() : this.activeProfile.autoAccept;
   }

   private String startTeleport(String accountId, String template, String kind) {
      if (!this.isRenderedOnActiveServer()) {
         return "Join the bots' server first";
      } else {
         MultiSession session = this.sessions.get(accountId);
         if (session == null) {
            return "No session";
         } else if (session.statusValue() != MultiSession.Status.READY) {
            return "Bot is not in the world yet";
         } else {
            String me = renderedServerName();
            if (me.isBlank()) {
               return "Can't read your name";
            } else {
               String command = MultiAutoAccept.expand(template, session.username(), me);
               if (command.isBlank()) {
                  return "No command set";
               } else {
                  Minecraft mc = Minecraft.getInstance();
                  if (mc != null && mc.getConnection() != null) {
                     if (command.startsWith("/")) {
                        mc.getConnection().sendCommand(command.substring(1));
                     } else {
                        mc.getConnection().sendChat(command);
                     }

                     session.armAutoAccept(kind);
                     return "Sent";
                  } else {
                     return "Not connected";
                  }
               }
            }
         }
      }
   }

   public synchronized void updateQuickAction(int index, MultiQuickAction action) {
      if (this.active && this.activeProfile != null) {
         this.activeProfile.setQuickAction(index, action);
         MultiProfileManager.get().put(this.activeProfile);
         this.uiRevision++;
      }
   }

   public synchronized void clearQuickAction(int index) {
      this.updateQuickAction(index, new MultiQuickAction());
   }

   public synchronized void resetQuickActions() {
      if (this.active && this.activeProfile != null) {
         this.activeProfile.resetQuickActions();
         MultiProfileManager.get().put(this.activeProfile);
         this.uiRevision++;
      }
   }

   public synchronized void assignAllMacro(String macroName) {
      if (this.activeProfile != null) {
         String name = macroName == null ? "" : macroName.trim();
         if (!name.equals(this.activeProfile.allMacroName)) {
            this.activeProfile.allMacroName = name;
            MultiProfileManager.get().put(this.activeProfile);
            this.rebuildAssignedMacroNames();
            this.uiRevision++;
         }
      }
   }

   public synchronized void assignMacro(String accountId, String macroName) {
      if (accountId != null) {
         this.assignMacroOnScope(Set.of(accountId), macroName);
      }
   }

   public synchronized int assignMacroOnScope(Set<String> accountIds, String macroName) {
      if (this.activeProfile != null && accountIds != null && !accountIds.isEmpty()) {
         String name = macroName == null ? "" : macroName.trim();
         List<MultiProfile.SessionSpec> specs = this.activeProfile.sessions;
         int changed = 0;

         for (int i = 0; i < specs.size(); i++) {
            MultiProfile.SessionSpec spec = specs.get(i);
            if (accountIds.contains(spec.accountId()) && !spec.macroName().equals(name)) {
               specs.set(i, spec.withMacro(name));
               changed++;
            }
         }

         if (changed > 0) {
            MultiProfileManager.get().put(this.activeProfile);
            this.rebuildAssignedMacroNames();
            this.uiRevision++;
         }

         return changed;
      } else {
         return 0;
      }
   }

   public synchronized String allMacroName() {
      return this.activeProfile == null ? "" : this.activeProfile.allMacroName;
   }

   public synchronized boolean hasAnyAssignedMacro() {
      if (this.activeProfile == null) {
         return false;
      } else if (!this.activeProfile.allMacroName.isBlank()) {
         return true;
      } else {
         for (MultiProfile.SessionSpec spec : this.activeProfile.sessions) {
            if (!spec.macroName().isBlank()) {
               return true;
            }
         }

         return false;
      }
   }

   public synchronized boolean hasAssignedMacroOnInteractiveScope(Set<String> requestedScope) {
      Set<String> scope = this.interactiveMacroScope(requestedScope);
      if (scope == null) {
         return true;
      } else if (scope.isEmpty()) {
         return this.hasAnyAssignedMacro();
      } else {
         for (String accountId : scope) {
            if (!this.assignedMacroName(accountId).isBlank()) {
               return true;
            }
         }

         return false;
      }
   }

   public synchronized String effectiveMacroName(String accountId) {
      if (this.activeProfile == null) {
         return "";
      } else {
         String published = this.assignedMacroNames.get(accountId);
         return published == null ? this.activeProfile.allMacroName : published;
      }
   }

   public String assignedMacroName(String accountId) {
      return accountId == null ? "" : this.assignedMacroNames.getOrDefault(accountId, "");
   }

   private void rebuildAssignedMacroNames() {
      MultiProfile profile = this.activeProfile;
      if (profile != null && !profile.sessions.isEmpty()) {
         Map<String, String> next = new HashMap<>();

         for (MultiProfile.SessionSpec spec : profile.sessions) {
            next.put(spec.accountId(), spec.macroName().isBlank() ? profile.allMacroName : spec.macroName());
         }

         this.assignedMacroNames = Map.copyOf(next);
      } else {
         this.assignedMacroNames = Map.of();
      }
   }

   public synchronized List<String> macroCompatibility(String macroName) {
      if (macroName != null && !macroName.isBlank()) {
         RiptideMacro macro = RiptideMacroManager.get().get(macroName);
         return macro == null ? List.of("Macro not found") : List.copyOf(MultiMacroSupport.analyze(macro));
      } else {
         return List.of();
      }
   }

   public synchronized MultiManager.BroadcastResult runMacroOnScope(Set<String> scope) {
      return this.runMacroOnScope(scope, false);
   }

   public synchronized MultiManager.BroadcastResult runMacroOnScope(Set<String> scope, boolean idleOnly) {
      Map<String, RiptideMacro> snapshots = new HashMap<>();
      Set<String> analyzed = new HashSet<>();
      MultiManager.MacroStagger stagger = new MultiManager.MacroStagger();
      MultiManager.BroadcastResult result = this.broadcastSessionAction("Run macro", scope, session -> {
         if (idleOnly && session.isMacroRunning()) {
            return "Macro already running; not restarted";
         } else {
            String name = this.effectiveMacroName(session.accountId());
            if (name != null && !name.isBlank()) {
               RiptideMacro copy = snapshots.computeIfAbsent(name, n -> {
                  RiptideMacro found = RiptideMacroManager.get().get(n);
                  return found == null ? null : found.deepCopy();
               });
               if (copy == null) {
                  return "Macro not found: " + name;
               } else {
                  if (analyzed.add(name)) {
                     this.warnMacroCompatibility(copy);
                  }

                  this.macroIntents.put(session.accountId(), MultiManager.MacroResumeIntent.assigned());
                  MultiSession.Status status = session.statusValue();
                  if (status != MultiSession.Status.FAILED && status != MultiSession.Status.DISCONNECTED) {
                     session.startAssignedMacro(copy, stagger.next());
                     return "Sent";
                  } else {
                     return "Session is not connected; queued for retry";
                  }
               }
            } else {
               return "No macro assigned";
            }
         }
      });
      stagger.report();
      return result;
   }

   private void warnMacroCompatibility(RiptideMacro macro) {
      List<String> warnings = MultiMacroSupport.analyze(macro);
      if (!warnings.isEmpty()) {
         String name = singleLine(macro.name, 40);
         this.appendSystem("Warning: macro \"" + name + "\" - " + warnings.size() + " item(s) may not run as intended:");

         for (String line : warnings) {
            this.appendSystem(line);
         }
      }
   }

   public synchronized MultiManager.BroadcastResult runMacroDirect(RiptideMacro macro) {
      return this.runMacroDirect(macro, null);
   }

   public synchronized MultiManager.BroadcastResult runMacroDirect(RiptideMacro macro, Set<String> scope) {
      if (macro != null && !macro.actions.isEmpty()) {
         RiptideMacro copy = macro.deepCopy();
         if (copy.name == null || copy.name.isBlank()) {
            copy.name = "Editor macro";
         }

         this.warnMacroCompatibility(copy);
         MultiManager.MacroResumeIntent intent = MultiManager.MacroResumeIntent.direct(copy);
         MultiManager.MacroStagger stagger = new MultiManager.MacroStagger();
         MultiManager.BroadcastResult result = this.broadcastSessionAction("Run for Multi", scope, session -> {
            this.macroIntents.put(session.accountId(), intent);
            MultiSession.Status status = session.statusValue();
            if (status != MultiSession.Status.FAILED && status != MultiSession.Status.DISCONNECTED) {
               session.startAssignedMacro(copy, stagger.next());
               return "Sent";
            } else {
               return "Session is not connected; queued for retry";
            }
         });
         stagger.report();
         return result;
      } else {
         return new MultiManager.BroadcastResult(0, 0, 1, List.of("Macro has no actions"));
      }
   }

   public synchronized boolean hasQueuedMacroIntent(String accountId, String macroName) {
      if (accountId != null && macroName != null && !macroName.isBlank()) {
         MultiManager.MacroResumeIntent intent = this.macroIntents.get(accountId);
         if (intent != null && intent.kind() == MultiManager.MacroIntentKind.DIRECT) {
            RiptideMacro direct = intent.directMacro();
            if (direct != null && macroName.equals(direct.name)) {
               MultiSession session = this.sessions.get(accountId);
               return session == null || !session.connected();
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public synchronized MultiManager.BroadcastResult stopMacroOnScope(Set<String> scope) {
      return this.broadcastSessionAction("Stop macro", scope, session -> {
         this.macroIntents.remove(session.accountId());
         session.stopMacro();
         return "Sent";
      });
   }

   public synchronized MultiManager.BroadcastResult runMacroOnInteractiveScope(Set<String> requestedScope) {
      Set<String> scope = this.interactiveMacroScope(requestedScope);
      return scope == null ? stalePovResult() : this.runMacroOnScope(scope);
   }

   public synchronized MultiManager.BroadcastResult runMacroDirectInteractive(RiptideMacro macro, Set<String> requestedScope) {
      Set<String> scope = this.interactiveMacroScope(requestedScope);
      return scope == null ? stalePovResult() : this.runMacroDirect(macro, scope);
   }

   public synchronized MultiManager.BroadcastResult stopMacroOnInteractiveScope(Set<String> requestedScope) {
      Set<String> scope = this.interactiveMacroScope(requestedScope);
      return scope == null ? stalePovResult() : this.stopMacroOnScope(scope);
   }

   public synchronized boolean isMacroPlayingOnInteractiveScope(String macroName, Set<String> requestedScope) {
      if (macroName != null && !macroName.isBlank()) {
         Set<String> scope = this.interactiveMacroScope(requestedScope);
         return scope == null ? false : this.isMacroPlayingOnScope(macroName, scope);
      } else {
         return false;
      }
   }

   public synchronized boolean isMacroPlayingOnScope(String macroName, Set<String> scope) {
      if (macroName != null && !macroName.isBlank()) {
         boolean scoped = scope != null && !scope.isEmpty();

         for (Entry<String, MultiSession> entry : this.sessions.entrySet()) {
            if (!scoped || scope.contains(entry.getKey())) {
               MultiSession session = entry.getValue();
               if (session.connected() && session.isMacroRunning()) {
                  MultiManager.MacroResumeIntent intent = this.macroIntents.get(entry.getKey());
                  String intended = intent == null
                     ? ""
                     : (
                        intent.kind() == MultiManager.MacroIntentKind.DIRECT
                           ? (intent.directMacro() == null ? "" : intent.directMacro().name)
                           : this.effectiveMacroName(entry.getKey())
                     );
                  if (macroName.equalsIgnoreCase(intended) || macroName.equalsIgnoreCase(session.currentMacroName())) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private Set<String> interactiveMacroScope(Set<String> requestedScope) {
      String pov = MultiTakeoverState.activeAccountId();
      return resolveInteractiveMacroScope(pov, pov == null || this.sessions.containsKey(pov), requestedScope);
   }

   static Set<String> resolveInteractiveMacroScope(String povAccountId, boolean povSessionExists, Set<String> requestedScope) {
      if (povAccountId == null) {
         return requestedScope;
      } else {
         return povSessionExists ? Set.of(povAccountId) : null;
      }
   }

   private static MultiManager.BroadcastResult stalePovResult() {
      return new MultiManager.BroadcastResult(0, 0, 1, List.of("The POV bot is no longer available"));
   }

   private void rearmMacroIfRunning(String accountId, MultiSession session) {
      MultiManager.MacroResumeIntent intent = this.macroIntents.get(accountId);
      if (intent != null) {
         if (intent.kind() == MultiManager.MacroIntentKind.DIRECT) {
            RiptideMacro direct = intent.directMacro();
            if (direct != null && direct.actions != null && !direct.actions.isEmpty()) {
               session.startAssignedMacro(direct);
            }
         } else {
            String name = this.effectiveMacroName(accountId);
            if (name != null && !name.isBlank()) {
               RiptideMacro macro = RiptideMacroManager.get().get(name);
               if (macro != null) {
                  if (this.activeProfile != null && this.activeProfile.loginMode == MultiProfile.LoginMode.Custom) {
                     session.startLoginMacro(macro.deepCopy());
                  } else {
                     session.startAssignedMacro(macro.deepCopy());
                  }
               }
            }
         }
      }
   }

   private void armJoinMacro(MultiProfile profile, MultiProfile.SessionSpec spec, MultiSession session) {
      if (profile != null && spec != null && session != null) {
         if (profile.loginMode == MultiProfile.LoginMode.Custom) {
            String name = spec.macroName().isBlank() ? profile.allMacroName : spec.macroName();
            if (name != null && !name.isBlank()) {
               RiptideMacro macro = RiptideMacroManager.get().get(name);
               if (macro != null) {
                  this.macroIntents.put(spec.accountId(), MultiManager.MacroResumeIntent.assigned());
                  session.startLoginMacro(macro.deepCopy());
               }
            }
         }
      }
   }

   public List<MultiSession.Snapshot> snapshots() {
      this.snapshotDemandUntilNanos = System.nanoTime() + SNAPSHOT_DEMAND_NANOS;
      return this.snapshotList;
   }

   public List<MultiManager.BotHandle> liveBots() {
      List<MultiSession> live = this.sessionList;
      List<MultiManager.BotHandle> out = new ArrayList<>(live.size());

      for (MultiSession session : live) {
         if (session != null) {
            String name = session.username();
            out.add(new MultiManager.BotHandle(session.accountId(), name != null && !name.isBlank() ? name : session.accountId()));
         }
      }

      return List.copyOf(out);
   }

   public Set<String> botUsernamesLower() {
      List<MultiSession> live = this.sessionList;
      if (live.isEmpty()) {
         return Set.of();
      } else {
         Set<String> names = new HashSet<>(live.size() * 2);

         for (MultiSession session : live) {
            if (session != null) {
               String name = session.username();
               if (name != null && !name.isBlank()) {
                  names.add(name.toLowerCase(Locale.ROOT));
               }
            }
         }

         return names;
      }
   }

   public static MultiManager.BotHandle resolveBot(Collection<MultiManager.BotHandle> bots, String botName) {
      if (bots != null && botName != null) {
         String want = botName.trim();
         if (want.isEmpty()) {
            return null;
         } else {
            for (MultiManager.BotHandle bot : bots) {
               if (bot != null && bot.username() != null && bot.username().equalsIgnoreCase(want)) {
                  return bot;
               }
            }

            for (MultiManager.BotHandle botx : bots) {
               if (botx != null && botx.accountId() != null && botx.accountId().equalsIgnoreCase(want)) {
                  return botx;
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }

   public Set<String> scopeForBot(String botName) {
      if (botName != null && !botName.isBlank()) {
         MultiManager.BotHandle bot = resolveBot(this.liveBots(), botName);
         return bot == null ? null : Set.of(bot.accountId());
      } else {
         return Set.of();
      }
   }

   private void publishSnapshots(boolean force) {
      if (force || System.nanoTime() - this.snapshotDemandUntilNanos < 0L) {
         List<MultiSession> live = this.sessionList;
         if (live.isEmpty()) {
            this.snapshotList = List.of();
         } else {
            List<MultiSession.Snapshot> previous = this.snapshotList;
            ArrayList<MultiSession.Snapshot> changed = null;

            for (int i = 0; i < live.size(); i++) {
               MultiSession.Snapshot next = live.get(i).snapshot();
               if (changed != null || i >= previous.size() || previous.get(i) != next) {
                  if (changed == null) {
                     changed = new ArrayList<>(live.size());

                     for (int j = 0; j < i; j++) {
                        changed.add(previous.get(j));
                     }
                  }

                  changed.add(next);
               }
            }

            if (changed != null || previous.size() != live.size()) {
               if (changed == null) {
                  changed = new ArrayList<>(previous.subList(0, live.size()));
               }

               this.snapshotList = List.copyOf(changed);
            }
         }
      }
   }

   public long sessionRevision() {
      return this.sessionRevision;
   }

   public long uiRevision() {
      return this.uiRevision;
   }

   public long chatRevision() {
      return this.chatRevision;
   }

   public synchronized List<MultiManager.ChatLine> chatView(Set<String> scope) {
      if (scope != null && !scope.isEmpty()) {
         if (scope.size() == 1) {
            String id = scope.iterator().next();
            ArrayDeque<MultiManager.ChatMsg> acc = this.accountChat.get(id);
            if (acc == null) {
               return List.of();
            } else {
               List<MultiManager.ChatLine> out = new ArrayList<>(acc.size());

               for (MultiManager.ChatMsg m : acc) {
                  out.add(new MultiManager.ChatLine(m.seq, m.time, m.component, Map.of(id, m.component), false, 1, m.source()));
               }

               return out;
            }
         } else {
            record Owned(String id, MultiManager.ChatMsg msg) {
            }

            List<Owned> gathered = new ArrayList<>();

            for (String id : scope) {
               ArrayDeque<MultiManager.ChatMsg> acc = this.accountChat.get(id);
               if (acc != null) {
                  for (MultiManager.ChatMsg m : acc) {
                     gathered.add(new Owned(id, m));
                  }
               }
            }

            gathered.sort(Comparator.comparingLong(o -> o.msg().seq()));
            Map<String, MultiManager.UnifiedMsg> recent = new HashMap<>();
            List<MultiManager.UnifiedMsg> ordered = new ArrayList<>();

            for (Owned o : gathered) {
               String text = o.msg().text();
               String src = o.msg().source();
               String key = groupKey(src, text);
               MultiManager.UnifiedMsg prev = recent.get(key);
               if (prev != null && !text.isBlank() && withinChatMergeWindow(prev.time, o.msg().time())) {
                  prev.count++;
                  prev.perAccount.putIfAbsent(o.id(), o.msg().component());
               } else {
                  MultiManager.UnifiedMsg m = new MultiManager.UnifiedMsg(o.msg().seq(), o.msg().time(), text, src, false, o.msg().component());
                  m.perAccount.put(o.id(), o.msg().component());
                  ordered.add(m);
                  if (!text.isBlank()) {
                     recent.put(key, m);
                  }
               }
            }

            int startIndex = Math.max(0, ordered.size() - 100);
            List<MultiManager.ChatLine> out = new ArrayList<>(ordered.size() - startIndex);

            for (int i = startIndex; i < ordered.size(); i++) {
               MultiManager.UnifiedMsg m = ordered.get(i);
               out.add(
                  new MultiManager.ChatLine(
                     m.seq, m.time, m.representative, Collections.unmodifiableMap(new LinkedHashMap<>(m.perAccount)), false, m.count, m.source
                  )
               );
            }

            return out;
         }
      } else {
         List<MultiManager.ChatLine> out = new ArrayList<>(this.unifiedChat.size());

         for (MultiManager.UnifiedMsg m : this.unifiedChat) {
            out.add(
               new MultiManager.ChatLine(
                  m.seq, m.time, m.representative, Collections.unmodifiableMap(new LinkedHashMap<>(m.perAccount)), m.system, m.count, m.source
               )
            );
         }

         return out;
      }
   }

   public synchronized String sendCommandTo(String accountId, String command) {
      MultiSession session = this.sessions.get(accountId);
      if (session == null) {
         return "No session";
      } else {
         try {
            return session.sendConsoleLine(command);
         } catch (RuntimeException var5) {
            return var5.getMessage() == null ? var5.getClass().getSimpleName() : var5.getMessage();
         }
      }
   }

   public synchronized MultiManager.BroadcastResult broadcastConsole(String line) {
      return this.broadcastConsole(line, null);
   }

   public synchronized MultiManager.BroadcastResult broadcastConsole(String line, Set<String> targets) {
      if (line != null && RiptideCommands.isRiptideCommandMessage(line.trim())) {
         return this.runClientCommand(line, targets);
      } else if (this.sessions.isEmpty()) {
         return this.sessionsStartingResult("Send");
      } else {
         boolean scoped = targets != null && !targets.isEmpty();
         int sent = 0;
         int skipped = 0;
         int failed = 0;
         List<String> details = new ArrayList<>();

         for (Entry<String, MultiSession> entry : this.sessions.entrySet()) {
            if (!scoped || targets.contains(entry.getKey())) {
               MultiSession session = entry.getValue();

               String result;
               try {
                  result = session.sendConsoleLine(line);
               } catch (RuntimeException var13) {
                  result = var13.getMessage() == null ? var13.getClass().getSimpleName() : var13.getMessage();
               }

               if ("Sent".equals(result) || result.startsWith("Queued ")) {
                  sent++;
               } else if (!result.contains("not ready") && !result.contains("Blocked") && !result.contains("key") && !result.contains("Rate limited")) {
                  failed++;
                  details.add(session.snapshot().accountName() + ": " + result);
               } else {
                  skipped++;
                  details.add(session.snapshot().accountName() + ": " + result);
               }
            }
         }

         MultiManager.BroadcastResult resultx = new MultiManager.BroadcastResult(sent, skipped, failed, List.copyOf(details));
         if (failed > 0) {
            this.appendSystem(resultx.summary());
            this.appendResultDetails(details);
         }

         return resultx;
      }
   }

   public synchronized MultiManager.BroadcastResult runClientCommand(String line, Set<String> targets) {
      String body = RiptideCommands.commandBody(line).trim();
      if (body.isEmpty()) {
         this.appendSystem("Empty command");
         return new MultiManager.BroadcastResult(0, 0, 0, List.of());
      } else if (this.sessions.isEmpty()) {
         return this.sessionsStartingResult("Command");
      } else {
         int space = body.indexOf(32);
         String name = (space < 0 ? body : body.substring(0, space)).toLowerCase(Locale.ROOT);
         String args = space < 0 ? "" : body.substring(space + 1).trim();
         String deny = MultiClientCommands.batchDenyReason(name, args);
         if (deny != null) {
            this.appendSystem(name + ": " + deny);
            return new MultiManager.BroadcastResult(0, 0, 0, List.of());
         } else {
            boolean scoped = targets != null && !targets.isEmpty();
            int sent = 0;
            int skipped = 0;
            int failed = 0;
            List<String> details = new ArrayList<>();

            for (Entry<String, MultiSession> entry : this.sessions.entrySet()) {
               if (!scoped || targets.contains(entry.getKey())) {
                  MultiSession session = entry.getValue();

                  String result;
                  try {
                     result = session.runClientAction(name, args);
                  } catch (RuntimeException var18) {
                     result = var18.getMessage() == null ? var18.getClass().getSimpleName() : var18.getMessage();
                  }

                  if ("Sent".equals(result)) {
                     sent++;
                  } else if (!result.contains("not ready") && !result.contains("Blocked") && !result.contains("Rate limited")) {
                     failed++;
                     details.add(session.snapshot().accountName() + ": " + result);
                  } else {
                     skipped++;
                     details.add(session.snapshot().accountName() + ": " + result);
                  }
               }
            }

            MultiManager.BroadcastResult resultx = new MultiManager.BroadcastResult(sent, skipped, failed, List.copyOf(details));
            if (failed > 0) {
               this.appendSystem(name + " -> " + resultx.summary());
               this.appendResultDetails(details);
            }

            return resultx;
         }
      }
   }

   public synchronized MultiManager.BroadcastResult broadcastMovementNow() {
      return this.broadcastMovementNow(null);
   }

   public synchronized MultiManager.BroadcastResult broadcastMovementNow(Set<String> targets) {
      return this.broadcastSessionAction("Move", targets, MultiSession::sendImmediateMoveLook);
   }

   public synchronized MultiManager.BroadcastResult broadcastQuickAction(MultiQuickAction action) {
      return this.broadcastQuickAction(action, null);
   }

   public synchronized MultiManager.BroadcastResult broadcastQuickAction(MultiQuickAction action, Set<String> targets) {
      if (action != null && !action.empty()) {
         MultiQuickAction sendAction = new MultiQuickAction(action);
         List<MultiManager.PreparedStep> prepared = new ArrayList<>();

         for (MultiQuickAction.Step step : sendAction.steps) {
            Class<? extends Packet<?>> packetClass = resolvePacket(step.packetClass());
            if (packetClass == null) {
               MultiManager.BroadcastResult result = new MultiManager.BroadcastResult(0, 0, this.sessions.size(), List.of("Missing packet"));
               this.appendSystem("Missing packet");
               return result;
            }

            prepared.add(new MultiManager.PreparedStep(packetClass, step.arguments()));
         }

         return this.broadcastSessionAction(sendAction.label(0), targets, session -> {
            for (MultiManager.PreparedStep stepx : prepared) {
               String resultx = session.sendManual(stepx.packetClass(), stepx.arguments());
               if (!"Sent".equals(resultx)) {
                  return resultx;
               }
            }

            return "Sent";
         });
      } else {
         int skipped = targets != null && !targets.isEmpty() ? (int)targets.stream().filter(this.sessions::containsKey).count() : this.sessions.size();
         MultiManager.BroadcastResult result = new MultiManager.BroadcastResult(0, skipped, 0, List.of("Empty slot"));
         this.appendSystem("Empty slot");
         return result;
      }
   }

   public synchronized MultiManager.BroadcastResult broadcastManual(Class<? extends Packet<?>> packetClass, String arguments) {
      return this.broadcastSessionAction("Packet", session -> session.sendManual(packetClass, arguments));
   }

   private MultiManager.BroadcastResult broadcastSessionAction(String label, Function<MultiSession, String> sender) {
      return this.broadcastSessionAction(label, null, sender);
   }

   private MultiManager.BroadcastResult broadcastSessionAction(String label, Set<String> targets, Function<MultiSession, String> sender) {
      if (this.sessions.isEmpty()) {
         return this.sessionsStartingResult(label);
      } else {
         boolean scoped = targets != null && !targets.isEmpty();
         int sent = 0;
         int skipped = 0;
         int failed = 0;
         List<String> details = new ArrayList<>();

         for (Entry<String, MultiSession> entry : this.sessions.entrySet()) {
            if (!scoped || targets.contains(entry.getKey())) {
               MultiSession session = entry.getValue();

               String result;
               try {
                  result = sender.apply(session);
               } catch (RuntimeException var14) {
                  result = var14.getMessage() == null ? var14.getClass().getSimpleName() : var14.getMessage();
               }

               if ("Sent".equals(result)) {
                  sent++;
               } else if (isSkippedSendResult(result)) {
                  skipped++;
                  details.add(session.snapshot().accountName() + ": " + result);
               } else {
                  failed++;
                  details.add(session.snapshot().accountName() + ": " + result);
               }
            }
         }

         MultiManager.BroadcastResult resultx = new MultiManager.BroadcastResult(sent, skipped, failed, List.copyOf(details));
         if (failed > 0) {
            this.appendSystem(singleLine(label, 24) + ": " + resultx.summary());
            this.appendResultDetails(details);
         }

         return resultx;
      }
   }

   private MultiManager.BroadcastResult sessionsStartingResult(String label) {
      int waiting = this.activeProfile == null ? 0 : this.activeProfile.sessions.size();
      return new MultiManager.BroadcastResult(0, waiting, 0, List.of("Sessions are still starting"));
   }

   private void appendResultDetails(List<String> details) {
      if (details != null && !details.isEmpty()) {
         int shown = Math.min(5, details.size());

         for (int i = 0; i < shown; i++) {
            this.appendSystem(details.get(i));
         }

         if (details.size() > shown) {
            this.appendSystem(details.size() - shown + " more sessions omitted");
         }
      }
   }

   public synchronized MultiManager.BroadcastResult useOnScope(Set<String> scope) {
      return this.broadcastSessionAction("Use", scope, MultiSession::useItem);
   }

   public synchronized MultiManager.BroadcastResult closeOnScope(Set<String> scope) {
      return this.broadcastSessionAction("Close", scope, MultiSession::closeContainer);
   }

   public synchronized MultiManager.BroadcastResult closeSilentOnScope(Set<String> scope) {
      return this.broadcastSessionAction("Close (silent)", scope, MultiSession::closeSilent);
   }

   public MultiSession.MenuView menuView(String accountId) {
      MultiSession session = this.sessionsById.get(accountId);
      return session == null ? null : session.menuView();
   }

   public MultiSession.MenuView inventoryView(String accountId) {
      MultiSession session = this.sessionsById.get(accountId);
      return session == null ? null : session.inventoryView();
   }

   public long menuRevision(String accountId) {
      MultiSession session = this.sessionsById.get(accountId);
      return session == null ? -1L : session.menuRevision();
   }

   public String clickBotSlot(String accountId, int handler, MultiClientCommands.ClickSpec spec) {
      MultiSession session = this.sessionsById.get(accountId);
      if (session == null || spec == null) {
         return "No session";
      } else {
         return povMacroBlocksInput(accountId, session) ? "Macro controls POV bot" : session.clickSlot(handler, spec.button(), spec.input());
      }
   }

   private static boolean povMacroBlocksInput(String accountId, MultiSession session) {
      return session != null && MultiTakeoverState.isActive(accountId) && session.macroOwnsPilot();
   }

   public int hotbarIndexForHandler(String accountId, int handler) {
      MultiSession session = this.sessionsById.get(accountId);
      return session == null ? -1 : session.hotbarIndexOfHandler(handler);
   }

   public int visibleSlotForHandler(String accountId, int handler) {
      MultiSession session = this.sessionsById.get(accountId);
      return session == null ? -1 : session.handlerToVisibleSlot(handler);
   }

   public synchronized boolean setControllable(String accountId, boolean on) {
      if (accountId == null) {
         return false;
      } else {
         if (on) {
            if (!this.controllableIds.contains(accountId) && this.controllableIds.size() >= 1) {
               return false;
            }

            this.controllableIds.add(accountId);
         } else {
            this.controllableIds.remove(accountId);
         }

         MultiSession session = this.sessions.get(accountId);
         if (session != null) {
            session.setCaptureWorld(on);
         }

         return true;
      }
   }

   public boolean isControllable(String accountId) {
      return accountId != null && this.controllableIds.contains(accountId);
   }

   MultiSession session(String accountId) {
      return accountId == null ? null : this.sessionsById.get(accountId);
   }

   public UUID botServerUuid(String accountId) {
      MultiSession session = this.session(accountId);
      return session == null ? null : session.serverUuid();
   }

   public static GameProfile botProfileByServerUuid(UUID uuid) {
      MultiManager mgr = getIfInitialized();
      if (mgr != null && uuid != null && mgr.isActive()) {
         for (MultiSession session : mgr.sessionsById.values()) {
            if (uuid.equals(session.serverUuid())) {
               return session.takeoverProfile();
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public int controllableCount() {
      return this.controllableIds.size();
   }

   public int maxControllable() {
      return 1;
   }

   private void armCapture(MultiSession session) {
      if (session != null && this.controllableIds.contains(session.accountId())) {
         session.setCaptureWorld(true);
      }
   }

   public String botServerAddress(String accountId) {
      MultiSession session = this.sessionsById.get(accountId);
      if (session != null) {
         String addr = session.serverAddress();
         if (addr != null && !addr.isBlank()) {
            return addr;
         }
      }

      MultiProfile profile = this.activeProfile();
      return profile != null && profile.serverAddress != null && !profile.serverAddress.isBlank() ? profile.serverAddress : null;
   }

   public int selectedHotbarHandler(String accountId) {
      MultiSession session = this.sessionsById.get(accountId);
      return session == null ? -1 : session.selectedHotbarHandler();
   }

   public String selectBotHotbar(String accountId, int index) {
      MultiSession session = this.sessionsById.get(accountId);
      if (session == null) {
         return "No session";
      } else {
         return povMacroBlocksInput(accountId, session)
            ? "Macro controls POV bot"
            : session.runClientAction("change-slot", String.valueOf(Math.max(1, Math.min(9, index + 1))));
      }
   }

   public String useBotHotbar(String accountId, int index) {
      MultiSession session = this.sessionsById.get(accountId);
      if (session == null) {
         return "No session";
      } else if (povMacroBlocksInput(accountId, session)) {
         return "Macro controls POV bot";
      } else {
         String selected = session.runClientAction("change-slot", String.valueOf(Math.max(1, Math.min(9, index + 1))));
         return !"Sent".equals(selected) ? selected : session.runClientAction("use", "");
      }
   }

   public MultiManager.BroadcastResult clickBotSlots(List<String> accountIds, int handler, MultiClientCommands.ClickSpec spec) {
      int sent = 0;
      int skipped = 0;
      int failed = 0;
      if (accountIds != null && spec != null) {
         for (String accountId : accountIds) {
            MultiSession session = this.sessionsById.get(accountId);
            if (session != null && !povMacroBlocksInput(accountId, session) && (handler < 0 || handler < session.clickHandlerLimit())) {
               if ("Sent".equals(session.clickSlot(handler, spec.button(), spec.input()))) {
                  sent++;
               } else {
                  failed++;
               }
            } else {
               skipped++;
            }
         }
      }

      return new MultiManager.BroadcastResult(sent, skipped, failed, List.of());
   }

   public String buttonClickBot(String accountId, int buttonId) {
      MultiSession session = this.sessionsById.get(accountId);
      if (session == null) {
         return "No session";
      } else {
         return povMacroBlocksInput(accountId, session) ? "Macro controls POV bot" : session.buttonClick(buttonId);
      }
   }

   public MultiManager.BroadcastResult buttonClickBots(List<String> accountIds, int buttonId, String typeId) {
      return this.fanoutMenuAction(accountIds, typeId, session -> session.buttonClick(buttonId));
   }

   public String selectTradeBot(String accountId, int index) {
      MultiSession session = this.sessionsById.get(accountId);
      if (session == null) {
         return "No session";
      } else {
         return povMacroBlocksInput(accountId, session) ? "Macro controls POV bot" : session.selectTrade(index);
      }
   }

   public MultiManager.BroadcastResult selectTradeBots(List<String> accountIds, int index, String typeId) {
      return this.fanoutMenuAction(accountIds, typeId, session -> session.selectTrade(index));
   }

   public String setBeaconBot(String accountId, int primaryId, int secondaryId) {
      MultiSession session = this.sessionsById.get(accountId);
      if (session == null) {
         return "No session";
      } else {
         return povMacroBlocksInput(accountId, session) ? "Macro controls POV bot" : session.setBeacon(primaryId, secondaryId);
      }
   }

   public MultiManager.BroadcastResult setBeaconBots(List<String> accountIds, int primaryId, int secondaryId, String typeId) {
      return this.fanoutMenuAction(accountIds, typeId, session -> session.setBeacon(primaryId, secondaryId));
   }

   public String placeRecipeBot(String accountId, RecipeDisplayId id, boolean all) {
      MultiSession session = this.sessionsById.get(accountId);
      if (session == null) {
         return "No session";
      } else {
         return povMacroBlocksInput(accountId, session) ? "Macro controls POV bot" : session.placeRecipe(id, all);
      }
   }

   public MultiManager.BroadcastResult placeRecipeBots(List<String> accountIds, RecipeDisplayId id, boolean all, String typeId) {
      return this.fanoutMenuAction(accountIds, typeId, session -> session.placeRecipe(id, all));
   }

   public String renameBotItem(String accountId, String name) {
      MultiSession session = this.sessionsById.get(accountId);
      if (session == null) {
         return "No session";
      } else {
         return povMacroBlocksInput(accountId, session) ? "Macro controls POV bot" : session.renameItem(name);
      }
   }

   public MultiManager.BroadcastResult renameBotItems(List<String> accountIds, String name, String typeId) {
      return this.fanoutMenuAction(accountIds, typeId, session -> session.renameItem(name));
   }

   private MultiManager.BroadcastResult fanoutMenuAction(List<String> accountIds, String typeId, Function<MultiSession, String> action) {
      int sent = 0;
      int skipped = 0;
      int failed = 0;
      if (accountIds != null) {
         for (String accountId : accountIds) {
            MultiSession session = this.sessionsById.get(accountId);
            if (session != null && !povMacroBlocksInput(accountId, session) && (typeId == null || typeId.isEmpty() || typeId.equals(session.menuTypeId()))) {
               if ("Sent".equals(action.apply(session))) {
                  sent++;
               } else {
                  failed++;
               }
            } else {
               skipped++;
            }
         }
      }

      return new MultiManager.BroadcastResult(sent, skipped, failed, List.of());
   }

   public MultiManager.BroadcastResult selectBotHotbars(List<String> accountIds, int index) {
      return this.fanoutHotbar(accountIds, index, false);
   }

   public MultiManager.BroadcastResult useBotHotbars(List<String> accountIds, int index) {
      return this.fanoutHotbar(accountIds, index, true);
   }

   private MultiManager.BroadcastResult fanoutHotbar(List<String> accountIds, int index, boolean use) {
      int sent = 0;
      int skipped = 0;
      int failed = 0;
      if (accountIds != null) {
         for (String accountId : accountIds) {
            if (this.sessionsById.get(accountId) == null) {
               skipped++;
            } else {
               String result = use ? this.useBotHotbar(accountId, index) : this.selectBotHotbar(accountId, index);
               if ("Sent".equals(result)) {
                  sent++;
               } else {
                  failed++;
               }
            }
         }
      }

      return new MultiManager.BroadcastResult(sent, skipped, failed, List.of());
   }

   private static boolean isSkippedSendResult(String result) {
      return result == null
         ? false
         : result.contains("not ready")
            || result.contains("Blocked")
            || result.contains("headless-safe")
            || result.contains("Rate limited")
            || result.contains("already running")
            || result.contains("Macro controls POV bot")
            || result.contains("Position is not ready")
            || result.contains("Signing key");
   }

   @Override
   public synchronized String identityRejection(MultiSession session, UUID profileId) {
      return session != null && profileId != null && session.generation() == this.generation && this.sessions.get(session.accountId()) == session
         ? identityRejection(this.renderedProfileIdAtStart, this.resolvedIdentities, session.accountId(), profileId)
         : "Stale Multi session";
   }

   static String identityRejection(UUID renderedProfileId, Map<UUID, String> resolved, String accountId, UUID profileId) {
      if (profileId == null || accountId == null || resolved == null) {
         return "Stale Multi session";
      } else if (renderedProfileId != null && renderedProfileId.equals(profileId)) {
         return "Account is already used by the rendered client";
      } else {
         String existing = resolved.putIfAbsent(profileId, accountId);
         return existing != null && !existing.equals(accountId) ? "Duplicate Minecraft identity in this batch" : "";
      }
   }

   public synchronized void replaceMacroReference(String oldName, String newName) {
      if (this.activeProfile != null && oldName != null && !oldName.isBlank()) {
         String replacement = newName == null ? "" : newName.trim();
         List<String> affected = new ArrayList<>();

         for (String accountId : this.sessions.keySet()) {
            if (oldName.equals(this.effectiveMacroName(accountId))) {
               affected.add(accountId);
            }
         }

         if (this.activeProfile.replaceMacroReference(oldName, replacement)) {
            MultiProfileManager.get().put(this.activeProfile);
            this.rebuildAssignedMacroNames();
            this.uiRevision++;
         }

         if (replacement.isBlank()) {
            for (String accountIdx : affected) {
               this.macroIntents.remove(accountIdx);
               MultiSession session = this.sessions.get(accountIdx);
               if (session != null) {
                  session.stopMacro();
               }
            }
         }
      }
   }

   @Override
   public synchronized void stateChanged(MultiSession session) {
      if (session != null && session.generation() == this.generation && this.sessions.get(session.accountId()) == session) {
         MultiSession.Status current = session.statusValue();
         String accountId = session.accountId();
         MultiSession.Status previous = this.lastPostedStatus.put(accountId, current);
         if (previous != current && (current == MultiSession.Status.FAILED || current == MultiSession.Status.DISCONNECTED)) {
            RiptideProxy proxy = this.runtimeProxies.get(accountId);
            this.lastFailedProxyIds.put(accountId, proxy == null ? "" : proxy.stableId());
            String reason = singleLine(session.detailText(), 160);
            if (!reason.isBlank()) {
               this.reportConnectionIssue(accountLabel(accountId), reason);
            }

            fireSessionDropped(accountId);
         }

         if ((current == MultiSession.Status.READY || current == MultiSession.Status.FAILED || current == MultiSession.Status.DISCONNECTED)
            && this.connecting.remove(session)) {
            this.pump();
         }

         if (current == MultiSession.Status.READY || current == MultiSession.Status.FAILED || current == MultiSession.Status.DISCONNECTED) {
            this.retryingAccounts.remove(accountId);
         }
      }
   }

   @Override
   public void note(MultiSession session, String text) {
      if (session != null && text != null && !text.isBlank() && session.generation() == this.generation) {
         this.recordNote(session, text);
      }
   }

   @Override
   public void menuClosed(MultiSession session) {
      if (session != null && session.generation() == this.generation) {
         synchronized (this) {
            if (this.sessions.get(session.accountId()) != session) {
               return;
            }
         }

         fireMenuClosed(session.accountId());
      }
   }

   private synchronized void recordNote(MultiSession session, String text) {
      if (session.generation() == this.generation && this.sessions.get(session.accountId()) == session) {
         this.appendSystem(text);
      }
   }

   @Override
   public void chat(MultiSession session, Component component) {
      if (session != null && component != null && session.generation() == this.generation) {
         String text = singleLine(component.getString(), 512);
         Component visible = sanitizeComponent(component, 512);
         String source = singleLine(session.currentMacroName(), 32);
         if (this.recordChat(session, text, visible, source)) {
            MultiPovChat.onBotChat(session, visible);
         }
      }
   }

   private synchronized boolean recordChat(MultiSession session, String text, Component visible, String source) {
      if (session.generation() == this.generation && this.sessions.get(session.accountId()) == session) {
         String accountId = session.accountId();
         long now = System.currentTimeMillis();
         long seq = ++this.chatSeq;
         ArrayDeque<MultiManager.ChatMsg> account = this.accountChat.computeIfAbsent(accountId, keyx -> new ArrayDeque<>());
         account.addLast(new MultiManager.ChatMsg(seq, now, visible, text, source));

         while (account.size() > 100) {
            account.removeFirst();
         }

         if (!text.isBlank()) {
            this.pruneRecent(now);
            String key = groupKey(source, text);
            MultiManager.UnifiedMsg existing = this.recentByText.get(key);
            if (existing != null && !existing.system && withinChatMergeWindow(existing.time, now)) {
               existing.count++;
               existing.perAccount.putIfAbsent(accountId, visible);
            } else {
               MultiManager.UnifiedMsg msg = new MultiManager.UnifiedMsg(seq, now, text, source, false, visible);
               msg.perAccount.put(accountId, visible);
               this.pushUnified(msg);
               this.recentByText.put(key, msg);
            }
         }

         this.chatRevision++;
         return true;
      } else {
         return false;
      }
   }

   synchronized List<MultiPovChat.HistoryLine> povChatHistory(String accountId) {
      ArrayDeque<MultiManager.ChatMsg> messages = this.accountChat.get(accountId);
      if (messages != null && !messages.isEmpty()) {
         List<MultiPovChat.HistoryLine> result = new ArrayList<>(messages.size());

         for (MultiManager.ChatMsg message : messages) {
            result.add(new MultiPovChat.HistoryLine(message.time(), message.component()));
         }

         return List.copyOf(result);
      } else {
         return List.of();
      }
   }

   @Override
   public void customMenuNeedsPassword(MultiSession session, String title) {
      boolean firstAlert;
      synchronized (this) {
         if (session == null || session.generation() != this.generation || this.sessions.get(session.accountId()) != session) {
            return;
         }

         firstAlert = !this.passwordPromptShown;
         this.passwordPromptShown = true;
         if (firstAlert) {
            this.appendSystem(
               "Warning: "
                  + accountLabel(session.accountId())
                  + " got a login screen (\""
                  + singleLine(title, 48)
                  + "\") but no password is stored for this profile. Set one in the popup."
            );
         }
      }

      if (firstAlert) {
         Minecraft.getInstance().execute(() -> {
            RiptideNotifications.error("A bot hit a login screen. Set a password.");
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof RiptideMultiPasswordPromptScreen)) {
               mc.gui.setScreen(new RiptideMultiPasswordPromptScreen(mc.gui.screen()));
            }
         });
      }
   }

   public synchronized int applyPasswordToAllAccounts(String password) {
      if (this.activeProfile != null && password != null && !password.isBlank()) {
         int updated = 0;

         for (MultiProfile.SessionSpec spec : this.activeProfile.sessions) {
            if (this.activeProfile.setFormValue(spec.accountId(), "password", password)) {
               updated++;
            }
         }

         this.persistAndPushFormValues();
         return updated;
      } else {
         return 0;
      }
   }

   public synchronized int applyGeneratedPasswords() {
      if (this.activeProfile == null) {
         return 0;
      } else {
         int updated = 0;

         for (MultiProfile.SessionSpec spec : this.activeProfile.sessions) {
            if (this.activeProfile.setFormValue(spec.accountId(), "password", generatePassword())) {
               updated++;
            }
         }

         this.persistAndPushFormValues();
         return updated;
      }
   }

   public static String generatePassword() {
      SecureRandom random = new SecureRandom();
      String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
      int length = 9 + random.nextInt(8);
      StringBuilder out = new StringBuilder(length);

      for (int i = 0; i < length; i++) {
         out.append(alphabet.charAt(random.nextInt(alphabet.length())));
      }

      return out.toString();
   }

   private void persistAndPushFormValues() {
      if (this.activeProfile != null) {
         MultiProfileManager.get().put(this.activeProfile);
         this.uiRevision++;

         for (Entry<String, MultiSession> entry : this.sessions.entrySet()) {
            entry.getValue().updateFormValues(this.activeProfile.openFormValues(entry.getKey()));
         }
      }
   }

   public synchronized void updateActiveFormValues(MultiProfile source) {
      if (this.activeProfile != null && source != null) {
         for (MultiProfile.SessionSpec spec : this.activeProfile.sessions) {
            String password = source.openFormValues(spec.accountId()).get("password");
            if (password != null) {
               this.activeProfile.setFormValue(spec.accountId(), "password", password);
            }
         }

         this.persistAndPushFormValues();
      }
   }

   @Override
   public boolean macroStepMet(MultiSession requester, WaitForMacroStepAction action) {
      List<MultiSession.MacroProgress> published = new ArrayList<>();

      for (MultiSession session : this.sessionList) {
         if (session != requester) {
            published.add(session.snapshot().macroProgress());
         }
      }

      return publishedMacroStepMet(action, published);
   }

   @Override
   public synchronized void macroChained(MultiSession session, RiptideMacro macro) {
      if (session != null && macro != null && session.generation() == this.generation && this.sessions.get(session.accountId()) == session) {
         this.macroIntents.put(session.accountId(), MultiManager.MacroResumeIntent.direct(macro));
      }
   }

   @Override
   public synchronized void macroDisconnected(MultiSession session) {
      if (session != null && session.generation() == this.generation && this.sessions.get(session.accountId()) == session) {
         this.macroIntents.remove(session.accountId());
      }
   }

   static boolean publishedMacroStepMet(WaitForMacroStepAction action, Iterable<MultiSession.MacroProgress> published) {
      if (action == null) {
         return true;
      } else {
         String target = action.macroName == null ? "" : action.macroName.trim();
         if (target.isEmpty()) {
            return true;
         } else {
            int step = Math.max(1, action.step);
            boolean found = false;

            for (MultiSession.MacroProgress progress : published) {
               if (progress != null && target.equalsIgnoreCase(progress.macroName())) {
                  found = true;

                  boolean satisfied = switch (action.mode == null ? WaitForMacroStepAction.WaitMode.COMPLETED_STEP : action.mode) {
                     case STARTED_STEP -> progress.step() >= step || !progress.running() && progress.totalSteps() >= step;
                     case COMPLETED_STEP -> progress.step() >= step || !progress.running() && progress.totalSteps() >= step;
                     case FINISHED -> !progress.running();
                  };
                  if (satisfied) {
                     return true;
                  }
               }
            }

            return !found && action.mode == WaitForMacroStepAction.WaitMode.FINISHED;
         }
      }
   }

   private void pushUnified(MultiManager.UnifiedMsg msg) {
      this.unifiedChat.addLast(msg);

      while (this.unifiedChat.size() > 100) {
         MultiManager.UnifiedMsg removed = this.unifiedChat.removeFirst();
         this.recentByText.remove(removed.system ? systemKey(removed.text) : groupKey(removed.source, removed.text), removed);
      }
   }

   private void pruneRecent(long now) {
      if (this.recentByText.size() >= 256) {
         this.recentByText.values().removeIf(m -> m.system ? now - m.lastAt > 20000L : !withinChatMergeWindow(m.time, now));
      }
   }

   static boolean withinChatMergeWindow(long firstCopyAt, long currentCopyAt) {
      long elapsed = currentCopyAt - firstCopyAt;
      return elapsed >= 0L && elapsed <= 2000L;
   }

   private void tick() {
      long now = System.currentTimeMillis();
      List<MultiSession> snapshot;
      long tickGeneration;
      synchronized (this) {
         if (!this.active) {
            return;
         }

         snapshot = this.sessionList;
         tickGeneration = this.generation;
      }

      for (MultiSession session : snapshot) {
         tickOne(session, now);
      }

      synchronized (this) {
         if (this.active && this.generation == tickGeneration) {
            this.drainMacroFinishes(snapshot);
            if (now - this.lastSnapshotPublishAt >= 100L) {
               this.publishSnapshots(false);
               this.lastSnapshotPublishAt = now;
            }
         }
      }
   }

   private void drainMacroFinishes(List<MultiSession> snapshot) {
      LinkedHashMap<String, Integer> groups = null;

      for (MultiSession session : snapshot) {
         String note = session.pollMacroFinish();
         if (note != null) {
            MultiManager.MacroFinish finish = parseMacroFinish(note);
            String macroName = finish.macroName();
            String reason = finish.reason();
            if (!"chained".equals(reason)) {
               this.macroIntents.remove(session.accountId());
            }

            String key = macroFinishVerb(reason) + "\u0000" + macroName;
            if (groups == null) {
               groups = new LinkedHashMap<>();
            }

            groups.merge(key, 1, Integer::sum);
         }
      }

      if (groups != null) {
         for (Entry<String, Integer> e : groups.entrySet()) {
            int sep = e.getKey().indexOf(0);
            String verb = e.getKey().substring(0, sep);
            String macroNamex = e.getKey().substring(sep + 1);
            int count = e.getValue();
            this.appendSystem("Macro \"" + macroNamex + "\" " + verb + " on " + count + " bot" + (count == 1 ? "" : "s") + ".");
         }
      }
   }

   static MultiManager.MacroFinish parseMacroFinish(String note) {
      String value = note == null ? "" : note;
      int separator = value.indexOf(0);
      return separator >= 0
         ? new MultiManager.MacroFinish(value.substring(0, separator), value.substring(separator + 1))
         : new MultiManager.MacroFinish(value, "done");
   }

   private static String macroFinishVerb(String reason) {
      return switch (reason) {
         case "chained" -> "handed off";
         case "error" -> "stopped on an error";
         case "stopped" -> "finished (stop action)";
         default -> "finished";
      };
   }

   private static void tickOne(MultiSession session, long now) {
      try {
         session.tick(now);
      } catch (RuntimeException var7) {
         String message = var7.getMessage() == null ? var7.getClass().getSimpleName() : var7.getMessage();

         try {
            session.failExternal("Session tick failed: " + singleLine(message, 120));
         } catch (RuntimeException var6) {
         }
      }
   }

   private synchronized void ensureTicking() {
      if (this.tickTask == null || this.tickTask.isCancelled() || this.tickTask.isDone()) {
         this.tickTask = this.scheduler.scheduleWithFixedDelay(this::tick, 50L, 50L, TimeUnit.MILLISECONDS);
      }
   }

   private void reportConnectionIssue(String account, String reason) {
      String key = singleLine(reason == null ? "" : reason.trim(), 160);
      if (!key.isBlank()) {
         long now = System.currentTimeMillis();
         long[] agg = this.connectionIssues.get(key);
         if (agg == null) {
            this.connectionIssues.put(key, new long[]{1L, 0L});
            this.postConnectionIssue(account + ": " + key);
         } else {
            agg[0]++;
            if (agg[0] == 4L || agg[0] > 4L && now - agg[1] >= 15000L) {
               this.postConnectionIssue(key + " (x" + agg[0] + " accounts)");
               agg[1] = now;
            }
         }
      }
   }

   private void postConnectionIssue(String line) {
      this.appendSystem(line);
      RiptideClientMessaging.sendPrefixed("Multi: " + line);
   }

   private synchronized void appendSystem(String text) {
      String safe = singleLine(text, 512);
      if (!safe.isBlank()) {
         long now = System.currentTimeMillis();
         this.pruneRecent(now);
         String key = systemKey(safe);
         MultiManager.UnifiedMsg existing = this.recentByText.get(key);
         if (existing != null && existing.system && now - existing.lastAt < 20000L) {
            existing.count++;
            existing.lastAt = now;
            this.chatRevision++;
         } else {
            Component render = RiptideClientMessaging.themedTag("Multi").append(RiptideClientMessaging.themedBody(safe));
            MultiManager.UnifiedMsg msg = new MultiManager.UnifiedMsg(++this.chatSeq, now, safe, "", true, render);
            this.pushUnified(msg);
            this.recentByText.put(key, msg);
            this.chatRevision++;
         }
      }
   }

   public static String singleLine(String text, int maxChars) {
      if (text != null && !text.isBlank()) {
         StringBuilder out = new StringBuilder(Math.min(text.length(), Math.max(16, maxChars)));
         boolean spaced = false;

         for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            boolean space = Character.isISOControl(ch) || Character.isWhitespace(ch);
            if (space) {
               if (!spaced && out.length() > 0) {
                  out.append(' ');
                  spaced = true;
               }
            } else {
               out.append(ch);
               spaced = false;
               if (maxChars > 3 && out.length() >= maxChars) {
                  break;
               }
            }
         }

         String safe = out.toString().trim();
         if (maxChars > 3 && safe.length() > maxChars - 3) {
            safe = safe.substring(0, maxChars - 3).trim() + "...";
         }

         return safe;
      } else {
         return "";
      }
   }

   static Component sanitizeComponent(Component component, int maxChars) {
      if (component != null && maxChars > 0) {
         MutableComponent safe = Component.empty();
         int[] length = new int[]{0};
         boolean[] spaced = new boolean[]{true};
         component.visit((style, part) -> {
            if (part != null && !part.isEmpty() && length[0] < maxChars) {
               StringBuilder run = new StringBuilder(Math.min(part.length(), maxChars - length[0]));

               for (int i = 0; i < part.length() && length[0] < maxChars; i++) {
                  char ch = part.charAt(i);
                  if (!Character.isISOControl(ch) && !Character.isWhitespace(ch)) {
                     run.append(ch);
                     length[0]++;
                     spaced[0] = false;
                  } else if (!spaced[0] && length[0] > 0) {
                     run.append(' ');
                     length[0]++;
                     spaced[0] = true;
                  }
               }

               if (!run.isEmpty()) {
                  safe.append(Component.literal(run.toString()).withStyle(style == null ? Style.EMPTY : style));
               }

               return Optional.empty();
            } else {
               return Optional.empty();
            }
         }, Style.EMPTY);
         return safe;
      } else {
         return Component.empty();
      }
   }

   private static String accountLabel(String accountId) {
      if ("default".equals(accountId)) {
         return RiptideAccountSessionSwitcher.getOriginalUser().getName();
      } else {
         RiptideAccount account = RiptideAccountManager.get().findById(accountId);
         return account == null ? accountId : account.displayName();
      }
   }

   private static RiptideProxy copyProxy(RiptideProxy source) {
      if (source == null) {
         return null;
      } else {
         RiptideProxy copy = new RiptideProxy();
         copy.id = source.stableId();
         copy.name = source.name;
         copy.type = source.type;
         copy.address = source.address;
         copy.port = source.port;
         copy.username = source.username;
         copy.password = source.password;
         return copy;
      }
   }

   static String proxyLeaseKey(RiptideProxy proxy) {
      return proxy != null && proxy.isValid() ? (proxy.address == null ? "" : proxy.address.trim().toLowerCase(Locale.ROOT)) + ":" + proxy.port : "";
   }

   static List<RiptideProxy> distinctUsableProxies(List<RiptideProxy> proxies) {
      if (proxies != null && !proxies.isEmpty()) {
         List<RiptideProxy> ordered = new ArrayList<>();

         for (RiptideProxy proxy : proxies) {
            if (isRetryCandidate(proxy)) {
               ordered.add(proxy);
            }
         }

         ordered.sort(
            Comparator.comparingInt(MultiManager::retryRank)
               .thenComparingLong(proxyx -> proxyx.status == RiptideProxy.Status.ALIVE && proxyx.latency > 0L ? proxyx.latency : Long.MAX_VALUE)
               .thenComparing(proxyx -> proxyx.displayName().toLowerCase(Locale.ROOT))
         );
         reserveMainProxyEndpoint(ordered);
         Map<String, RiptideProxy> distinct = new LinkedHashMap<>();

         for (RiptideProxy proxyx : ordered) {
            distinct.putIfAbsent(proxyLeaseKey(proxyx), proxyx);
         }

         return List.copyOf(distinct.values());
      } else {
         return List.of();
      }
   }

   private static void reserveMainProxyEndpoint(List<RiptideProxy> candidates) {
      if (candidates != null && candidates.size() >= 2) {
         if (Minecraft.getInstance() != null) {
            RiptideProxy mainProxy = RiptideProxyManager.get().getEnabled();
            if (mainProxy != null) {
               String reserved = proxyLeaseKey(mainProxy);
               if (!reserved.isBlank()) {
                  long distinctEndpoints = candidates.stream().map(MultiManager::proxyLeaseKey).distinct().count();
                  if (distinctEndpoints > 1L) {
                     candidates.removeIf(proxy -> reserved.equals(proxyLeaseKey(proxy)));
                  }
               }
            }
         }
      }
   }

   static Map<String, RiptideProxy> distributeProxies(List<MultiProfile.SessionSpec> sessions, List<RiptideProxy> candidates) {
      if (sessions != null && !sessions.isEmpty()) {
         if (candidates != null && !candidates.isEmpty()) {
            List<RiptideProxy> distinct = new ArrayList<>();
            Set<String> leases = new HashSet<>();

            for (RiptideProxy candidate : candidates) {
               String key = proxyLeaseKey(candidate);
               if (!key.isBlank() && leases.add(key)) {
                  distinct.add(candidate);
               }
            }

            if (distinct.isEmpty()) {
               return Map.of();
            } else {
               Map<String, RiptideProxy> assignment = new LinkedHashMap<>();
               int index = 0;

               for (MultiProfile.SessionSpec spec : sessions) {
                  if (!spec.direct()) {
                     assignment.put(spec.accountId(), distinct.get(index++ % distinct.size()));
                  }
               }

               return assignment;
            }
         } else {
            return Map.of();
         }
      } else {
         return Map.of();
      }
   }

   static RiptideProxy selectRetryProxy(List<RiptideProxy> proxies, String currentProxyId, String lastFailedProxyId, boolean currentDirect) {
      if (currentDirect) {
         return null;
      } else if (proxies != null && !proxies.isEmpty()) {
         String current = currentProxyId == null ? "" : currentProxyId;
         String failed = lastFailedProxyId == null ? "" : lastFailedProxyId;
         RiptideProxy candidate = bestRetryProxy(proxies, current, failed);
         if (candidate != null) {
            return candidate;
         } else {
            candidate = bestRetryProxy(proxies, "", failed);
            return candidate != null ? candidate : bestRetryProxy(proxies, "", "");
         }
      } else {
         return null;
      }
   }

   private static RiptideProxy bestRetryProxy(List<RiptideProxy> proxies, String avoidCurrent, String avoidFailed) {
      if (proxies != null && !proxies.isEmpty()) {
         String current = avoidCurrent == null ? "" : avoidCurrent;
         String failed = avoidFailed == null ? "" : avoidFailed;
         return proxies.stream()
            .filter(MultiManager::isRetryCandidate)
            .filter(proxy -> {
               String id = proxy.stableId();
               return (current.isBlank() || !current.equals(id)) && (failed.isBlank() || !failed.equals(id));
            })
            .min(
               Comparator.comparingInt(MultiManager::retryRank)
                  .thenComparingLong(proxy -> proxy.status == RiptideProxy.Status.ALIVE && proxy.latency > 0L ? proxy.latency : Long.MAX_VALUE)
                  .thenComparing(proxy -> proxy.displayName().toLowerCase(Locale.ROOT))
            )
            .orElse(null);
      } else {
         return null;
      }
   }

   private static boolean isRetryCandidate(RiptideProxy proxy) {
      return proxy != null && proxy.isValid() && proxy.status != RiptideProxy.Status.DEAD;
   }

   private static int retryRank(RiptideProxy proxy) {
      if (proxy != null && proxy.status != null) {
         return switch (proxy.status) {
            case ALIVE -> 0;
            case UNCHECKED -> 1;
            case CHECKING -> 2;
            case DEAD -> 3;
         };
      } else {
         return 3;
      }
   }

   public static Class<? extends Packet<?>> resolvePacket(String name) {
      return RiptidePacketRegistry.getPacket(name);
   }

   public record BotHandle(String accountId, String username) {
   }

   public record BroadcastResult(int sent, int skipped, int failed, List<String> details) {
      public String summary() {
         return "Sent " + this.sent + ", skipped " + this.skipped + ", failed " + this.failed;
      }
   }

   public record ChatLine(long seq, long time, Component render, Map<String, Component> targets, boolean system, int count, String source) {
   }

   private record ChatMsg(long seq, long time, Component component, String text, String source) {
   }

   record MacroFinish(String macroName, String reason) {
   }

   private static enum MacroIntentKind {
      ASSIGNED,
      DIRECT;
   }

   private record MacroResumeIntent(MultiManager.MacroIntentKind kind, RiptideMacro directMacro) {
      static MultiManager.MacroResumeIntent assigned() {
         return new MultiManager.MacroResumeIntent(MultiManager.MacroIntentKind.ASSIGNED, null);
      }

      static MultiManager.MacroResumeIntent direct(RiptideMacro macro) {
         return new MultiManager.MacroResumeIntent(MultiManager.MacroIntentKind.DIRECT, macro);
      }
   }

   private final class MacroStagger {
      private final long launchedAt;
      private final int gapMs;
      private int started;

      private MacroStagger() {
         Objects.requireNonNull(MultiManager.this);
         super();
         this.launchedAt = System.currentTimeMillis();
         this.gapMs = MultiMacroDelay.currentMs();
      }

      long next() {
         return MultiMacroDelay.startAt(this.launchedAt, this.started++, this.gapMs);
      }

      void report() {
         if (this.gapMs > 0 && this.started > 1) {
            MultiManager.this.appendSystem("Starting " + this.started + " bots " + MultiMacroDelay.valueText(this.gapMs) + " apart.");
         }
      }
   }

   private record Pending(MultiSession session, InetSocketAddress address, String host, int port) {
   }

   private record PreparedStep(Class<? extends Packet<?>> packetClass, String arguments) {
   }

   public record RetryResult(boolean ok, String message) {
      static MultiManager.RetryResult ok(String message) {
         return new MultiManager.RetryResult(true, message);
      }

      static MultiManager.RetryResult error(String message) {
         return new MultiManager.RetryResult(false, message);
      }
   }

   private record Sample(RiptideProxy proxy, boolean ok, long pingMs) {
   }

   public record StartResult(boolean ok, String message) {
      static MultiManager.StartResult success() {
         return new MultiManager.StartResult(true, "");
      }

      static MultiManager.StartResult error(String message) {
         return new MultiManager.StartResult(false, message);
      }
   }

   public record SuggestionResult(int start, int length, List<String> entries) {
   }

   public interface UiLifecycleListener {
      void batchEnded();

      void sessionDropped(String var1);

      default void menuClosed(String accountId) {
      }
   }

   private static final class UnifiedMsg {
      final long seq;
      final long time;
      long lastAt;
      int count = 1;
      final String text;
      final String source;
      final boolean system;
      final Component representative;
      final Map<String, Component> perAccount = new LinkedHashMap<>();

      UnifiedMsg(long seq, long time, String text, String source, boolean system, Component representative) {
         this.seq = seq;
         this.time = time;
         this.lastAt = time;
         this.text = text;
         this.source = source == null ? "" : source;
         this.system = system;
         this.representative = representative;
      }
   }
}
