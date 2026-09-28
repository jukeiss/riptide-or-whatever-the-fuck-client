package riptide.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import riptide.util.mm.crypto.AtRestSeal;

public final class RiptideDupeRadar {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   private static final String PROVIDER_ID = "dupedb";
   private static final String PROVIDER_LABEL = "DupeDB";
   private static final String BASE_URL = "https://dupedb.net";
   private static final String CLIENT_ID = "catr-dupedb-radar";
   private static final long FINDINGS_CACHE_MS = TimeUnit.MINUTES.toMillis(30L);
   private static final int FINDINGS_CACHE_VERSION = 8;
   private static final int SERVER_FINDINGS_CACHE_VERSION = 8;
   private static final String EXPLOIT_SEARCH_STATUSES = "working,verified,patched,unverified";
   private static final int CONNECT_TIMEOUT_MS = 8000;
   private static final int READ_TIMEOUT_MS = 12000;
   private static final int MAX_FINDINGS_PER_PLUGIN = 50;
   private static final AtomicInteger WORKER_ID = new AtomicInteger();
   private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory() {
      @Override
      public Thread newThread(Runnable runnable) {
         Thread thread = new Thread(runnable, "RIPTIDE-DupeRadar-" + RiptideDupeRadar.WORKER_ID.incrementAndGet());
         thread.setDaemon(true);
         return thread;
      }
   });
   private static final AtomicInteger GENERATION = new AtomicInteger();
   private static final Object CACHE_LOCK = new Object();
   private static final Path CACHE_FILE = riptide.RiptideClientAddon.FOLDER.toPath().resolve("providers").resolve("dupedb-cache.json");
   private static RiptideDupeRadar.ProviderCache cache = loadCache();
   private static volatile RiptideDupeRadar.RadarState state = initialState();

   private RiptideDupeRadar() {
   }

   public static RiptideDupeRadar.RadarState state() {
      ensureUserFromCache();
      return state;
   }

   public static String providerLabel() {
      return "DupeDB";
   }

   public static String sourceUrl() {
      return "https://dupedb.net";
   }

   public static void login() {
      RiptideDupeRadar.RadarState current = state;
      if (!current.authenticating()) {
         if (!current.authenticated()) {
            int generation = GENERATION.incrementAndGet();
            state = state.withBusy(true, false, "Opening DupeDB login...", null);
            CompletableFuture.runAsync(() -> runLogin(generation), EXECUTOR);
         }
      }
   }

   public static void logout() {
      GENERATION.incrementAndGet();
      synchronized (CACHE_LOCK) {
         cache.accessToken = null;
         cache.refreshToken = null;
         cache.username = null;
         cache.userUpdatedAt = 0L;
         saveCacheLocked();
      }

      state = state.withAuth(false, null).withBusy(false, false, "Logged out.", null).withMatches(List.of(), 0, false);
   }

   public static void refreshUser() {
      int generation = GENERATION.incrementAndGet();
      state = state.withBusy(true, false, "Refreshing DupeDB user...", null);
      CompletableFuture.runAsync(() -> {
         try {
            String token = ensureAccessToken();
            if (token == null || token.isBlank()) {
               publish(generation, state.withAuth(false, null).withBusy(false, false, "Login required.", "No DupeDB token saved."));
               return;
            }

            String username = fetchUserAuthorized();
            synchronized (CACHE_LOCK) {
               cache.username = username;
               cache.userUpdatedAt = System.currentTimeMillis();
               saveCacheLocked();
            }

            publish(generation, state.withAuth(true, username).withBusy(false, false, "User refreshed.", null));
         } catch (Exception var6) {
            publish(generation, state.withBusy(false, false, "User refresh failed.", cleanError(var6)));
         }
      }, EXECUTOR);
   }

   public static void checkServer(List<RiptideDupeRadar.RadarPluginSnapshot> plugins, boolean forceRefresh) {
      checkServer(plugins, RiptideDupeRadar.RadarServerSnapshot.EMPTY, forceRefresh);
   }

   public static void checkServer(List<RiptideDupeRadar.RadarPluginSnapshot> plugins, RiptideDupeRadar.RadarServerSnapshot server, boolean forceRefresh) {
      List<RiptideDupeRadar.RadarPluginSnapshot> snapshots = plugins == null ? List.of() : List.copyOf(plugins);
      RiptideDupeRadar.RadarServerSnapshot serverSnapshot = server == null ? RiptideDupeRadar.RadarServerSnapshot.EMPTY : server;
      int generation = GENERATION.incrementAndGet();
      state = state.withBusy(false, true, forceRefresh ? "Refreshing radar data..." : "Checking server...", null).withPluginCount(snapshots.size());
      CompletableFuture.runAsync(() -> runCheck(generation, snapshots, serverSnapshot, forceRefresh), EXECUTOR);
   }

   public static void clearServerResults() {
      GENERATION.incrementAndGet();
      state = state.withMatches(List.of(), 0, false).withBusy(false, false, "No server checked.", null);
   }

   public static void cancel() {
      GENERATION.incrementAndGet();
      state = state.withBusy(false, false, "Canceled.", null);
   }

   public static String copyReport() {
      RiptideDupeRadar.RadarState current = state();
      StringBuilder sb = new StringBuilder();
      sb.append("Radar - ").append("DupeDB").append('\n');
      sb.append("Status: ").append(current.status()).append('\n');
      if (current.error() != null && !current.error().isBlank()) {
         sb.append("Error: ").append(current.error()).append('\n');
      }

      sb.append("Plugins checked: ").append(current.detectedPluginCount()).append('\n');
      sb.append("Matches: ").append(current.matches().size()).append("\n\n");
      if (current.matches().isEmpty()) {
         sb.append("No matches.\n");
         return sb.toString();
      } else {
         for (RiptideDupeRadar.RadarMatch match : current.matches()) {
            sb.append("- ")
               .append(match.displayLabel())
               .append(" [")
               .append(match.matchConfidence())
               .append(" ")
               .append(match.matchSource().label())
               .append("]")
               .append(" findings=")
               .append(match.findings().size())
               .append(" source=")
               .append(match.sourceUrl())
               .append('\n');
         }

         return sb.toString();
      }
   }

   public static void open(String url) {
      if (url != null && !url.isBlank()) {
         try {
            RiptideLinks.open(safeFindingUrl(url, ""));
         } catch (Exception var2) {
         }
      }
   }

   private static RiptideDupeRadar.RadarState initialState() {
      RiptideDupeRadar.ProviderCache loaded = cache;
      boolean authed = loaded != null && loaded.accessToken != null && !loaded.accessToken.isBlank();
      String username = loaded == null ? null : loaded.username;
      return new RiptideDupeRadar.RadarState(
         "dupedb", "DupeDB", authed, false, false, username, authed ? "Ready." : "Login required.", null, List.of(), 0, 0L, false
      );
   }

   private static void ensureUserFromCache() {
      RiptideDupeRadar.ProviderCache loaded = cache;
      if (loaded != null) {
         boolean authed = loaded.accessToken != null && !loaded.accessToken.isBlank();
         RiptideDupeRadar.RadarState current = state;
         if (current.authenticated() != authed || !Objects.equals(current.username(), loaded.username)) {
            state = current.withAuth(authed, loaded.username);
         }
      }
   }

   private static void runLogin(int generation) {
      String verifier = randomUrlToken(48);
      String challenge = codeChallenge(verifier);
      String stateToken = randomUrlToken(24);

      try (ServerSocket server = new ServerSocket()) {
         server.setReuseAddress(true);
         server.bind(new InetSocketAddress("127.0.0.1", 0));
         server.setSoTimeout(120000);
         int port = server.getLocalPort();
         String redirect = "http://127.0.0.1:" + port + "/callback";
         String loginUrl = "https://dupedb.net/api/oauth/authorize?response_type=code&client_id="
            + url("catr-dupedb-radar")
            + "&redirect_uri="
            + url(redirect)
            + "&code_challenge="
            + url(challenge)
            + "&code_challenge_method=S256&state="
            + url(stateToken);
         RiptideLinks.open(loginUrl);
         publish(generation, state.withBusy(true, false, "Waiting for browser login...", null));

         try (Socket socket = server.accept()) {
            socket.setSoTimeout(5000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String request = reader.readLine();
            String code = null;
            String returnedState = null;
            if (request != null) {
               int start = request.indexOf(32);
               int end = request.indexOf(32, start + 1);
               if (start >= 0 && end > start) {
                  URI uri = URI.create("http://127.0.0.1" + request.substring(start + 1, end));
                  Map<String, String> params = parseQuery(uri.getRawQuery());
                  code = params.get("code");
                  returnedState = params.get("state");
               }
            }

            String responseBody = "<html><body style=\"font-family:sans-serif;background:#111;color:#eee\"><h2>Riptide Radar login received.</h2>You can close this tab.</body></html>";
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            OutputStream out = socket.getOutputStream();
            out.write(
               ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + responseBytes.length + "\r\n\r\n")
                  .getBytes(StandardCharsets.UTF_8)
            );
            out.write(responseBytes);
            out.flush();
            if (code == null || code.isBlank()) {
               throw new IOException("No authorization code returned.");
            }

            if (!stateToken.equals(returnedState)) {
               throw new IOException("OAuth state did not match.");
            }

            RiptideDupeRadar.TokenResponse token = exchangeCode(code, verifier, redirect);
            String username = fetchUser(token.accessToken);
            synchronized (CACHE_LOCK) {
               cache.accessToken = token.accessToken;
               cache.refreshToken = token.refreshToken;
               cache.username = username;
               cache.userUpdatedAt = System.currentTimeMillis();
               saveCacheLocked();
            }

            publish(generation, state.withAuth(true, username).withBusy(false, false, "Logged in.", null));
         }
      } catch (Exception var25) {
         publish(generation, state.withBusy(false, false, "Login failed.", cleanError(var25)));
      }
   }

   private static void runCheck(
      int generation, List<RiptideDupeRadar.RadarPluginSnapshot> plugins, RiptideDupeRadar.RadarServerSnapshot server, boolean forceRefresh
   ) {
      try {
         String token = ensureAccessToken();
         if (token == null || token.isBlank()) {
            publish(generation, state.withAuth(false, null).withBusy(false, false, "Login required.", "Log in to DupeDB before checking."));
            return;
         }

         Map<String, RiptideDupeRadar.RadarFindingHit> dedupedHits = new LinkedHashMap<>();
         List<RiptideDupeRadar.RadarPluginSnapshot> orderedPlugins = new ArrayList<>(plugins);
         orderedPlugins.removeIf(snapshot -> snapshot == null || !snapshot.isPluginIdentity());
         orderedPlugins.sort(Comparator.comparing(RiptideDupeRadar.RadarPluginSnapshot::displayName, String.CASE_INSENSITIVE_ORDER));

         for (RiptideDupeRadar.RadarPluginSnapshot snapshot : orderedPlugins) {
            try {
               List<RiptideDupeRadar.RadarFinding> findings = getPluginFindings(snapshot.displayName(), forceRefresh);
               if (!findings.isEmpty()) {
                  boolean exact = findingsNamePlugin(findings, snapshot.displayName());
                  String provider = firstAffectedPlugin(findings, snapshot.displayName());
                  RiptideDupeRadar.RadarMatch match = new RiptideDupeRadar.RadarMatch(
                     snapshot.displayName(),
                     provider,
                     exact ? "Exact" : "Fuzzy",
                     exact ? RiptideDupeRadar.MatchSource.PLUGIN : RiptideDupeRadar.MatchSource.PLUGIN_FUZZY,
                     exact ? 1.0 : 0.9,
                     snapshot.displayName(),
                     "https://dupedb.net/plugins/" + urlPath(provider),
                     "DupeDB",
                     findings,
                     newestTimestamp(findings)
                  );
                  addDedupedHits(dedupedHits, match);
               }
            } catch (Exception var13) {
            }
         }

         for (RiptideDupeRadar.RadarServerIdentity identity : server.identities()) {
            if (identity != null && !identity.value().isBlank()) {
               List<RiptideDupeRadar.RadarFinding> findings = getServerFindings(identity, forceRefresh);
               if (!findings.isEmpty()) {
                  RiptideDupeRadar.MatchSource source = identity.ip() ? RiptideDupeRadar.MatchSource.SERVER_IP : RiptideDupeRadar.MatchSource.SERVER_DOMAIN;
                  RiptideDupeRadar.RadarMatch match = new RiptideDupeRadar.RadarMatch(
                     identity.value(),
                     identity.value(),
                     "Server",
                     source,
                     1.0,
                     identity.value(),
                     "https://dupedb.net",
                     "DupeDB",
                     findings,
                     newestTimestamp(findings)
                  );
                  addDedupedHits(dedupedHits, match);
               }
            }
         }

         List<RiptideDupeRadar.RadarMatch> matches = collapseHits(dedupedHits);
         matches.sort(
            Comparator.comparingLong(RiptideDupeRadar.RadarMatch::sortTimestampMs)
               .reversed()
               .thenComparingInt(matchx -> matchSourceRank(matchx.matchSource()))
               .thenComparing(RiptideDupeRadar.RadarMatch::detectedPlugin, String.CASE_INSENSITIVE_ORDER)
         );
         String checkedLabel = plugins.isEmpty() ? "Checked server." : "Checked " + plugins.size() + " plugin" + (plugins.size() == 1 ? "." : "s.");
         publish(generation, state.withAuth(true, cache.username).withBusy(false, false, checkedLabel, null).withMatches(matches, plugins.size(), false));
      } catch (Exception var14) {
         publish(generation, state.withBusy(false, false, "Radar check failed.", cleanError(var14)).markStale());
      }
   }

   private static boolean findingsNamePlugin(List<RiptideDupeRadar.RadarFinding> findings, String plugin) {
      String want = RiptidePluginNameMatcher.normalizeVersionless(plugin);
      if (want.isBlank()) {
         return false;
      } else {
         for (RiptideDupeRadar.RadarFinding finding : findings) {
            if (finding != null && want.equals(RiptidePluginNameMatcher.normalizeVersionless(finding.affectedPlugin()))) {
               return true;
            }
         }

         return false;
      }
   }

   private static String firstAffectedPlugin(List<RiptideDupeRadar.RadarFinding> findings, String fallback) {
      for (RiptideDupeRadar.RadarFinding finding : findings) {
         if (finding != null && finding.affectedPlugin() != null && !finding.affectedPlugin().isBlank()) {
            return finding.affectedPlugin();
         }
      }

      return fallback;
   }

   private static void addDedupedHits(Map<String, RiptideDupeRadar.RadarFindingHit> hits, RiptideDupeRadar.RadarMatch match) {
      if (hits != null && match != null && match.findings() != null) {
         for (RiptideDupeRadar.RadarFinding finding : match.findings()) {
            if (finding != null) {
               String key = findingDedupeKey(finding);
               if (!key.isBlank()) {
                  RiptideDupeRadar.RadarFindingHit next = new RiptideDupeRadar.RadarFindingHit(match, finding);
                  RiptideDupeRadar.RadarFindingHit current = hits.get(key);
                  if (current == null || isStrongerHit(next, current)) {
                     hits.put(key, next);
                  }
               }
            }
         }
      }
   }

   private static List<RiptideDupeRadar.RadarMatch> collapseHits(Map<String, RiptideDupeRadar.RadarFindingHit> hits) {
      if (hits != null && !hits.isEmpty()) {
         Map<String, RiptideDupeRadar.RadarMatchBuilder> builders = new LinkedHashMap<>();

         for (RiptideDupeRadar.RadarFindingHit hit : hits.values()) {
            RiptideDupeRadar.RadarMatch match = hit.match;
            String key = match.matchSource().name() + "|" + normalize(match.detectedPlugin()) + "|" + normalize(match.providerPlugin());
            RiptideDupeRadar.RadarMatchBuilder builder = builders.computeIfAbsent(key, unused -> new RiptideDupeRadar.RadarMatchBuilder(match));
            builder.findings.add(hit.finding);
         }

         List<RiptideDupeRadar.RadarMatch> out = new ArrayList<>();

         for (RiptideDupeRadar.RadarMatchBuilder builder : builders.values()) {
            builder.findings
               .sort(
                  Comparator.comparingLong(RiptideDupeRadar.RadarFinding::sortTimestampMs)
                     .reversed()
                     .thenComparing(RiptideDupeRadar.RadarFinding::title, String.CASE_INSENSITIVE_ORDER)
               );
            out.add(builder.toMatch());
         }

         return out;
      } else {
         return List.of();
      }
   }

   private static boolean isStrongerHit(RiptideDupeRadar.RadarFindingHit next, RiptideDupeRadar.RadarFindingHit current) {
      int bySource = Integer.compare(matchSourceRank(next.match.matchSource()), matchSourceRank(current.match.matchSource()));
      if (bySource != 0) {
         return bySource < 0;
      } else {
         int byScore = Double.compare(next.match.matchScore(), current.match.matchScore());
         return byScore != 0 ? byScore > 0 : next.finding.sortTimestampMs() > current.finding.sortTimestampMs();
      }
   }

   private static String findingDedupeKey(RiptideDupeRadar.RadarFinding finding) {
      if (finding == null) {
         return "";
      } else if (finding.id() != null && !finding.id().isBlank()) {
         return "id:" + normalize(finding.id());
      } else {
         return finding.sourceUrl() != null && !finding.sourceUrl().isBlank()
            ? "url:" + finding.sourceUrl().trim().toLowerCase(Locale.ROOT)
            : "title:" + normalize(finding.title() + ":" + finding.status());
      }
   }

   private static long newestTimestamp(List<RiptideDupeRadar.RadarFinding> findings) {
      long best = 0L;
      if (findings != null) {
         for (RiptideDupeRadar.RadarFinding finding : findings) {
            if (finding != null) {
               best = Math.max(best, finding.sortTimestampMs());
            }
         }
      }

      return best == 0L ? System.currentTimeMillis() : best;
   }

   private static int matchSourceRank(RiptideDupeRadar.MatchSource source) {
      return switch (source == null ? RiptideDupeRadar.MatchSource.PLUGIN_FUZZY : source) {
         case PLUGIN -> 0;
         case PLUGIN_FUZZY -> 1;
         case SERVER_DOMAIN -> 2;
         case SERVER_IP -> 3;
      };
   }

   private static String pluginSearchEndpoint(String plugin) {
      return "https://dupedb.net/api/exploits/search?scope=plugin&q="
         + url(plugin)
         + "&status=working,verified,patched,unverified&sort=date_submitted&order=desc&page=1&limit=50";
   }

   private static String serverSearchEndpoint(String host) {
      return "https://dupedb.net/api/exploits/search?scope=serverip&q="
         + url(host)
         + "&status=working,verified,patched,unverified&sort=date_submitted&order=desc&page=1&limit=50";
   }

   private static List<RiptideDupeRadar.RadarFinding> getPluginFindings(String plugin, boolean forceRefresh) throws IOException {
      long now = System.currentTimeMillis();
      String key = normalize(plugin);
      synchronized (CACHE_LOCK) {
         RiptideDupeRadar.CachedFindings cached = cache.findings.get(key);
         if (!forceRefresh && cached != null && now - cached.updatedAt <= FINDINGS_CACHE_MS && cached.items != null) {
            return List.copyOf(cached.items);
         }
      }

      String raw = httpGetAuthorized(pluginSearchEndpoint(plugin));
      List<RiptideDupeRadar.RadarFinding> findings = parseCards(JsonParser.parseString(raw), false, plugin);
      synchronized (CACHE_LOCK) {
         RiptideDupeRadar.CachedFindings cached = new RiptideDupeRadar.CachedFindings();
         cached.updatedAt = now;
         cached.items = new ArrayList<>(findings);
         cache.findings.put(key, cached);
         saveCacheLocked();
         return findings;
      }
   }

   private static List<RiptideDupeRadar.RadarFinding> getServerFindings(RiptideDupeRadar.RadarServerIdentity identity, boolean forceRefresh) {
      long now = System.currentTimeMillis();
      String key = (identity.ip() ? "ip:" : "domain:") + identity.value().toLowerCase(Locale.ROOT);
      synchronized (CACHE_LOCK) {
         RiptideDupeRadar.CachedFindings cached = cache.serverFindings.get(key);
         if (!forceRefresh && cached != null && now - cached.updatedAt <= FINDINGS_CACHE_MS && cached.items != null) {
            return List.copyOf(cached.items);
         }
      }

      List<RiptideDupeRadar.RadarFinding> findings;
      try {
         String raw = httpGetAuthorized(serverSearchEndpoint(identity.value()));
         findings = parseCards(JsonParser.parseString(raw), true, identity.value());
      } catch (Exception var10) {
         findings = List.of();
      }

      synchronized (CACHE_LOCK) {
         RiptideDupeRadar.CachedFindings cached = new RiptideDupeRadar.CachedFindings();
         cached.updatedAt = now;
         cached.items = new ArrayList<>(findings);
         cache.serverFindings.put(key, cached);
         saveCacheLocked();
         return findings;
      }
   }

   private static void mergeFindings(Map<String, RiptideDupeRadar.RadarFinding> unique, List<RiptideDupeRadar.RadarFinding> findings) {
      if (unique != null && findings != null && !findings.isEmpty()) {
         for (RiptideDupeRadar.RadarFinding finding : findings) {
            if (finding != null) {
               String key = findingDedupeKey(finding);
               if (key.isBlank()) {
                  key = normalize(finding.title() + ":" + finding.sourceUrl());
               }

               RiptideDupeRadar.RadarFinding existing = unique.get(key);
               if (existing == null || finding.sortTimestampMs() > existing.sortTimestampMs()) {
                  unique.put(key, finding);
               }
            }
         }
      }
   }

   private static List<RiptideDupeRadar.RadarFinding> parseCards(JsonElement element, boolean serverScope, String queried) {
      JsonArray cards = resolveCardArray(element);
      Map<String, RiptideDupeRadar.RadarFinding> unique = new LinkedHashMap<>();
      long fallbackTimestamp = System.currentTimeMillis();

      for (JsonElement el : cards) {
         if (el != null && el.isJsonObject()) {
            JsonObject object = el.getAsJsonObject();
            String id = firstString(object, "id");
            String title = firstString(object, "name");
            if (title != null && !title.isBlank()) {
               String status = resolvePatchStatus(object);
               String finalId = id != null && !id.isBlank() ? id.trim() : normalize(title + ":" + status);
               String affectedPlugin = serverScope ? queried : cardPluginName(object, queried);
               long timestamp = parseTimestampMs(object, fallbackTimestamp);
               String sourceUrl = safeFindingUrl(null, id != null && !id.isBlank() ? id : title);
               unique.putIfAbsent(
                  finalId,
                  new RiptideDupeRadar.RadarFinding(
                     finalId, title.trim(), status, cardBadge(object), affectedPlugin, sourceUrl, "DupeDB", timestamp, cardServerIps(object)
                  )
               );
            }
         }
      }

      List<RiptideDupeRadar.RadarFinding> out = new ArrayList<>(unique.values());
      out.sort(
         Comparator.comparingLong(RiptideDupeRadar.RadarFinding::sortTimestampMs)
            .reversed()
            .thenComparing(RiptideDupeRadar.RadarFinding::title, String.CASE_INSENSITIVE_ORDER)
      );
      return out;
   }

   private static JsonArray resolveCardArray(JsonElement element) {
      if (element == null || element.isJsonNull()) {
         return new JsonArray();
      } else if (element.isJsonArray()) {
         return element.getAsJsonArray();
      } else {
         if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();

            for (String key : List.of("exploits", "results", "data", "items")) {
               JsonElement value = object.get(key);
               if (value != null && value.isJsonArray()) {
                  return value.getAsJsonArray();
               }
            }
         }

         return new JsonArray();
      }
   }

   private static String cardPluginName(JsonObject object, String fallback) {
      JsonElement plugins = object.get("plugins");
      if (plugins != null && plugins.isJsonArray()) {
         for (JsonElement el : plugins.getAsJsonArray()) {
            if (el != null && !el.isJsonNull()) {
               if (el.isJsonObject()) {
                  String name = firstString(el.getAsJsonObject(), "name", "plugin", "pluginName");
                  if (name != null && !name.isBlank()) {
                     return name.trim();
                  }
               } else if (el.isJsonPrimitive()) {
                  String name = el.getAsString();
                  if (name != null && !name.isBlank()) {
                     return name.trim();
                  }
               }
            }
         }
      }

      String legacy = firstString(object, "plugin_name");
      return legacy != null && !legacy.isBlank() ? legacy.trim() : fallback;
   }

   private static List<String> cardServerIps(JsonObject object) {
      JsonElement ips = object.get("server_ips");
      if (ips != null && ips.isJsonArray()) {
         List<String> out = new ArrayList<>();

         for (JsonElement el : ips.getAsJsonArray()) {
            if (el != null && el.isJsonPrimitive()) {
               String value = el.getAsString();
               if (value != null && !value.isBlank()) {
                  out.add(value.trim());
               }
            }
         }

         return out;
      } else {
         return List.of();
      }
   }

   private static String cardBadge(JsonObject object) {
      List<String> parts = new ArrayList<>();
      String type = firstString(object, "type");
      if (type != null && !type.isBlank()) {
         parts.add(type.trim().toLowerCase(Locale.ROOT));
      }

      long upvotes = readLong(object, "upvotes");
      if (upvotes > 0L) {
         parts.add(upvotes + "▲");
      }

      String version = newestVersion(object.get("minecraft_versions"));
      if (!version.isBlank()) {
         parts.add(version);
      }

      return String.join(" · ", parts);
   }

   private static long readLong(JsonObject object, String key) {
      JsonElement value = object.get(key);
      if (value != null && !value.isJsonNull() && value.isJsonPrimitive()) {
         try {
            return value.getAsLong();
         } catch (Exception var4) {
            return 0L;
         }
      } else {
         return 0L;
      }
   }

   private static String newestVersion(JsonElement versions) {
      if (versions != null && versions.isJsonArray()) {
         String best = "";

         for (JsonElement el : versions.getAsJsonArray()) {
            if (el != null && el.isJsonPrimitive()) {
               String value = el.getAsString();
               if (value != null && !value.isBlank()) {
                  value = value.trim();
                  if (best.isBlank() || compareVersions(value, best) > 0) {
                     best = value;
                  }
               }
            }
         }

         return best;
      } else {
         return "";
      }
   }

   private static int compareVersions(String a, String b) {
      String[] pa = a.split("\\.");
      String[] pb = b.split("\\.");
      int n = Math.max(pa.length, pb.length);

      for (int i = 0; i < n; i++) {
         int va = i < pa.length ? parseIntSafe(pa[i]) : 0;
         int vb = i < pb.length ? parseIntSafe(pb[i]) : 0;
         if (va != vb) {
            return Integer.compare(va, vb);
         }
      }

      return 0;
   }

   private static int parseIntSafe(String value) {
      try {
         return Integer.parseInt(value.replaceAll("[^0-9].*$", ""));
      } catch (Exception var2) {
         return 0;
      }
   }

   private static String safeFindingUrl(String rawUrl, String fallback) {
      String clean = rawUrl == null ? "" : rawUrl.trim();
      String fallbackText = fallback == null ? "" : fallback.trim();
      if (clean.isBlank()) {
         return looksLikeExploitId(fallbackText) ? "https://dupedb.net/exploit/" + urlPath(fallbackText) : "https://dupedb.net/?q=" + url(fallbackText);
      } else {
         String lower = clean.toLowerCase(Locale.ROOT);
         if (lower.startsWith("http://") || lower.startsWith("https://")) {
            try {
               URI uri = URI.create(clean);
               String host = uri.getHost();
               if (host == null || !"dupedb.net".equalsIgnoreCase(host) && !host.toLowerCase(Locale.ROOT).endsWith(".dupedb.net")) {
                  return looksLikeExploitId(fallbackText)
                     ? "https://dupedb.net/exploit/" + urlPath(fallbackText)
                     : "https://dupedb.net/?q=" + url(fallbackText);
               } else {
                  String path = uri.getPath() == null ? "" : uri.getPath();
                  if (!path.startsWith("/exploit/") && !path.startsWith("/exploits/")) {
                     return "https://dupedb.net/?q=" + url(fallbackText.isBlank() ? clean : fallbackText);
                  } else {
                     String id = path.substring(path.lastIndexOf(47) + 1);
                     return "https://dupedb.net/exploit/" + urlPath(id);
                  }
               }
            } catch (Exception var9) {
               return looksLikeExploitId(fallbackText) ? "https://dupedb.net/exploit/" + urlPath(fallbackText) : "https://dupedb.net/?q=" + url(fallbackText);
            }
         } else if (lower.startsWith("/exploit/")
            || lower.startsWith("/exploits/")
            || lower.startsWith("https://dupedb.net/exploit/")
            || lower.startsWith("https://dupedb.net/exploits/")) {
            String id = clean.substring(clean.lastIndexOf(47) + 1);
            return "https://dupedb.net/exploit/" + urlPath(id);
         } else if (clean.startsWith("/")) {
            return "https://dupedb.net" + clean;
         } else if (looksLikeExploitId(clean)) {
            return "https://dupedb.net/exploit/" + urlPath(clean);
         } else {
            return looksLikeExploitId(fallbackText) ? "https://dupedb.net/exploit/" + urlPath(fallbackText) : "https://dupedb.net/?q=" + url(fallbackText);
         }
      }
   }

   private static boolean looksLikeExploitId(String value) {
      return value != null && value.matches("[A-Za-z0-9_-]{8,64}") && !value.contains(" ");
   }

   private static RiptideDupeRadar.TokenResponse exchangeCode(String code, String verifier, String redirectUri) throws IOException {
      String body = "grant_type=authorization_code&client_id="
         + url("catr-dupedb-radar")
         + "&code="
         + url(code)
         + "&code_verifier="
         + url(verifier)
         + "&redirect_uri="
         + url(redirectUri);
      String raw = httpPost("https://dupedb.net/api/oauth/token", body);
      JsonObject object = JsonParser.parseString(raw).getAsJsonObject();
      String accessToken = firstString(object, "access_token", "accessToken", "token");
      if (accessToken != null && !accessToken.isBlank()) {
         return new RiptideDupeRadar.TokenResponse(accessToken, firstString(object, "refresh_token", "refreshToken"));
      } else {
         throw new IOException("No access token returned.");
      }
   }

   private static RiptideDupeRadar.TokenResponse exchangeRefreshToken(String refreshToken) throws IOException {
      if (refreshToken != null && !refreshToken.isBlank()) {
         String body = "grant_type=refresh_token&client_id=" + url("catr-dupedb-radar") + "&refresh_token=" + url(refreshToken);
         String raw = httpPost("https://dupedb.net/api/oauth/token", body);
         JsonObject object = JsonParser.parseString(raw).getAsJsonObject();
         String accessToken = firstString(object, "access_token", "accessToken", "token");
         if (accessToken != null && !accessToken.isBlank()) {
            return new RiptideDupeRadar.TokenResponse(accessToken, firstString(object, "refresh_token", "refreshToken"));
         } else {
            throw new IOException("No refreshed access token returned.");
         }
      } else {
         throw new IOException("No refresh token saved.");
      }
   }

   private static String fetchUser(String token) throws IOException {
      String raw = httpGet("https://dupedb.net/api/oauth/userinfo", token);
      return parseUser(raw);
   }

   private static String fetchUserAuthorized() throws IOException {
      String raw = httpGetAuthorized("https://dupedb.net/api/oauth/userinfo");
      return parseUser(raw);
   }

   private static String parseUser(String raw) throws IOException {
      JsonElement element = JsonParser.parseString(raw);
      if (element.isJsonObject()) {
         String user = firstString(element.getAsJsonObject(), "username", "name", "display_name", "email");
         if (user != null && !user.isBlank()) {
            return user.trim();
         }
      }

      return "DupeDB user";
   }

   private static String httpGetAuthorized(String url) throws IOException {
      String token = ensureAccessToken();
      if (token != null && !token.isBlank()) {
         try {
            return httpGet(url, token);
         } catch (RiptideDupeRadar.HttpStatusException var4) {
            if (!isAuthFailure(var4.code())) {
               throw var4;
            } else if (!refreshAccessToken()) {
               throw var4;
            } else {
               String refreshed = currentToken();
               if (refreshed != null && !refreshed.isBlank() && !Objects.equals(refreshed, token)) {
                  return httpGet(url, refreshed);
               } else {
                  throw var4;
               }
            }
         }
      } else {
         throw new IOException("No DupeDB token saved.");
      }
   }

   private static String httpGet(String url, String token) throws IOException {
      HttpURLConnection connection = (HttpURLConnection)URI.create(url).toURL().openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(8000);
      connection.setReadTimeout(12000);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("User-Agent", "RIPTIDE-Client-Radar");
      if (token != null && !token.isBlank()) {
         connection.setRequestProperty("Authorization", "Bearer " + token);
      }

      return readResponse(connection);
   }

   private static String httpPost(String url, String body) throws IOException {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      HttpURLConnection connection = (HttpURLConnection)URI.create(url).toURL().openConnection();
      connection.setRequestMethod("POST");
      connection.setConnectTimeout(8000);
      connection.setReadTimeout(12000);
      connection.setDoOutput(true);
      connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("User-Agent", "RIPTIDE-Client-Radar");
      connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));

      try (OutputStream out = connection.getOutputStream()) {
         out.write(bytes);
      }

      return readResponse(connection);
   }

   private static String readResponse(HttpURLConnection connection) throws IOException {
      int code = connection.getResponseCode();
      InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
      if (stream == null) {
         throw new IOException("HTTP " + code);
      } else {
         String var7;
         try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[2048];

            int read;
            while ((read = reader.read(buffer)) >= 0) {
               sb.append(buffer, 0, read);
            }

            if (code < 200 || code >= 300) {
               throw new RiptideDupeRadar.HttpStatusException(code, sb.toString());
            }

            var7 = sb.toString();
         }

         return var7;
      }
   }

   private static String sealToken(String value) {
      if (value != null && !value.isBlank()) {
         byte[] sealed = AtRestSeal.seal(value.getBytes(StandardCharsets.UTF_8));
         return sealed == null ? "" : Base64.getEncoder().encodeToString(sealed);
      } else {
         return "";
      }
   }

   private static String unsealToken(String encoded) {
      if (encoded != null && !encoded.isBlank()) {
         try {
            byte[] plain = AtRestSeal.unseal(Base64.getDecoder().decode(encoded));
            return plain == null ? null : new String(plain, StandardCharsets.UTF_8);
         } catch (Throwable var2) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static RiptideDupeRadar.ProviderCache loadCache() {
      try {
         if (Files.exists(CACHE_FILE)) {
            String text = Files.readString(CACHE_FILE, StandardCharsets.UTF_8);
            RiptideDupeRadar.ProviderCache loaded = (RiptideDupeRadar.ProviderCache)GSON.fromJson(text, RiptideDupeRadar.ProviderCache.class);
            if (loaded != null) {
               loaded.ensure();
               loaded.accessToken = unsealToken(loaded.encAccessToken);
               loaded.refreshToken = unsealToken(loaded.encRefreshToken);
               if (loaded.accessToken == null || loaded.refreshToken == null) {
                  try {
                     JsonObject raw = JsonParser.parseString(text).getAsJsonObject();
                     if (loaded.accessToken == null) {
                        loaded.accessToken = firstString(raw, "accessToken", "access_token");
                     }

                     if (loaded.refreshToken == null) {
                        loaded.refreshToken = firstString(raw, "refreshToken", "refresh_token");
                     }
                  } catch (Exception var5) {
                  }
               }

               return loaded;
            }
         }
      } catch (Exception var6) {
      }

      try {
         Path oldTokenFile = riptide.RiptideClientAddon.FOLDER.toPath().getParent().resolve("dupedb-token.json");
         if (Files.exists(oldTokenFile)) {
            JsonObject object = JsonParser.parseString(Files.readString(oldTokenFile, StandardCharsets.UTF_8)).getAsJsonObject();
            String token = firstString(object, "access_token", "accessToken", "token");
            if (token != null && !token.isBlank()) {
               RiptideDupeRadar.ProviderCache migrated = new RiptideDupeRadar.ProviderCache();
               migrated.ensure();
               migrated.accessToken = token;
               migrated.refreshToken = firstString(object, "refresh_token", "refreshToken");
               return migrated;
            }
         }
      } catch (Exception var4) {
      }

      RiptideDupeRadar.ProviderCache fresh = new RiptideDupeRadar.ProviderCache();
      fresh.ensure();
      return fresh;
   }

   private static void saveCacheLocked() {
      try {
         cache.encAccessToken = sealToken(cache.accessToken);
         cache.encRefreshToken = sealToken(cache.refreshToken);
         Files.createDirectories(CACHE_FILE.getParent());

         try (Writer writer = Files.newBufferedWriter(CACHE_FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(cache, writer);
         }
      } catch (Exception var5) {
      }
   }

   private static String currentToken() {
      synchronized (CACHE_LOCK) {
         return cache == null ? null : cache.accessToken;
      }
   }

   private static String ensureAccessToken() {
      synchronized (CACHE_LOCK) {
         if (cache != null && cache.accessToken != null && !cache.accessToken.isBlank()) {
            return cache.accessToken;
         }
      }

      return refreshAccessToken() ? currentToken() : null;
   }

   private static boolean refreshAccessToken() {
      String refreshToken;
      synchronized (CACHE_LOCK) {
         refreshToken = cache == null ? null : cache.refreshToken;
      }

      if (refreshToken != null && !refreshToken.isBlank()) {
         try {
            RiptideDupeRadar.TokenResponse token = exchangeRefreshToken(refreshToken);
            synchronized (CACHE_LOCK) {
               cache.accessToken = token.accessToken;
               if (token.refreshToken != null && !token.refreshToken.isBlank()) {
                  cache.refreshToken = token.refreshToken;
               }

               saveCacheLocked();
            }

            RiptideDupeRadar.RadarState current = state;
            state = current.withAuth(true, cache.username);
            return true;
         } catch (Exception var6) {
            if (var6 instanceof RiptideDupeRadar.HttpStatusException http && isAuthFailure(http.code())) {
               clearSavedAuth("DupeDB session expired.");
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private static void clearSavedAuth(String status) {
      synchronized (CACHE_LOCK) {
         if (cache != null) {
            cache.accessToken = null;
            cache.refreshToken = null;
            cache.username = null;
            cache.userUpdatedAt = 0L;
            saveCacheLocked();
         }
      }

      state = state.withAuth(false, null).withBusy(false, false, status == null ? "Login required." : status, null).withMatches(List.of(), 0, false);
   }

   private static boolean isAuthFailure(int code) {
      return code == 401 || code == 403;
   }

   private static void publish(int generation, RiptideDupeRadar.RadarState next) {
      if (generation == GENERATION.get()) {
         state = next;
      }
   }

   private static String normalize(String value) {
      if (value == null) {
         return "";
      } else {
         String lower = value.trim().toLowerCase(Locale.ROOT);
         StringBuilder sb = new StringBuilder(lower.length());

         for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
               sb.append(c);
            }
         }

         return sb.toString();
      }
   }

   private static String normalizeStatus(String value) {
      if (value != null && !value.isBlank()) {
         String lower = value.toLowerCase(Locale.ROOT);
         if (lower.contains("reject")) {
            return "Rejected";
         } else if (lower.contains("pending")) {
            return "Pending";
         } else if (lower.contains("unverif")) {
            return "Unverified";
         } else if (lower.contains("unpatch") || lower.contains("not_patched") || lower.contains("not patched")) {
            return "Unpatched";
         } else if (lower.contains("work")) {
            return "Working";
         } else if (lower.contains("patch")) {
            return "Patched";
         } else {
            return lower.contains("verif") ? "Verified" : RiptideDupeRadar.UiTextShortener.titleCase(value.trim());
         }
      } else {
         return "Unknown";
      }
   }

   private static String resolvePatchStatus(JsonObject object) {
      String raw = normalizeStatus(firstString(object, "status"));
      if (!raw.equals("Unverified") && !raw.equals("Pending") && !raw.equals("Rejected")) {
         long patchedAt = markTimestamp(object, "marked_patched_at");
         long workingAt = markTimestamp(object, "marked_working_at");
         if (patchedAt > 0L && patchedAt >= workingAt) {
            return "Patched";
         } else {
            return workingAt > 0L ? "Working" : raw;
         }
      } else {
         return raw;
      }
   }

   private static long markTimestamp(JsonObject object, String key) {
      return object != null && object.has(key) ? parseTimestampElement(object.get(key)) : 0L;
   }

   public static int statusRank(String status) {
      String lower = status == null ? "" : status.toLowerCase(Locale.ROOT);
      if (lower.contains("work")) {
         return 0;
      } else if (lower.contains("verif")) {
         return 1;
      } else {
         return lower.contains("patch") ? 2 : 3;
      }
   }

   public static int highestStatusRank(List<RiptideDupeRadar.RadarFinding> findings) {
      int best = 3;
      if (findings != null) {
         for (RiptideDupeRadar.RadarFinding finding : findings) {
            best = Math.min(best, statusRank(finding.status()));
         }
      }

      return best;
   }

   public static String highestStatusLabel(List<RiptideDupeRadar.RadarFinding> findings) {
      int rank = highestStatusRank(findings);

      return switch (rank) {
         case 0 -> "Working";
         case 1 -> "Verified";
         case 2 -> "Patched";
         default -> "Unknown";
      };
   }

   public static RiptideDupeRadar.StatusCounts statusCounts(List<RiptideDupeRadar.RadarFinding> findings) {
      int working = 0;
      int patched = 0;
      int verified = 0;
      if (findings != null) {
         for (RiptideDupeRadar.RadarFinding finding : findings) {
            String lower = finding.status() == null ? "" : finding.status().toLowerCase(Locale.ROOT);
            if (lower.contains("work")) {
               working++;
            } else if (lower.contains("patch")) {
               patched++;
            } else if (lower.contains("verif")) {
               verified++;
            }
         }
      }

      return new RiptideDupeRadar.StatusCounts(working, patched, verified);
   }

   private static String firstString(JsonObject object, String... keys) {
      if (object != null && keys != null) {
         for (String key : keys) {
            if (key != null && object.has(key)) {
               JsonElement value = object.get(key);
               if (value != null && value.isJsonPrimitive()) {
                  try {
                     String text = value.getAsString();
                     if (text != null && !text.isBlank()) {
                        return text;
                     }
                  } catch (Exception var8) {
                  }
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static long parseTimestampMs(JsonObject object, long fallbackMs) {
      if (object == null) {
         return fallbackMs;
      } else {
         for (String key : List.of(
            "updated_at",
            "updatedAt",
            "published_at",
            "publishedAt",
            "submitted_at",
            "submittedAt",
            "date_submitted",
            "dateSubmitted",
            "created_at",
            "createdAt",
            "last_activity_at",
            "lastActivityAt",
            "marked_working_at",
            "markedWorkingAt",
            "marked_patched_at",
            "markedPatchedAt",
            "verified_at",
            "verifiedAt",
            "working_at",
            "patched_at",
            "sighting_created_at",
            "sightingCreatedAt",
            "sighting_updated_at",
            "sightingUpdatedAt",
            "sighting_date",
            "sightingDate"
         )) {
            if (object.has(key)) {
               JsonElement value = object.get(key);
               long parsed = parseTimestampElement(value);
               if (parsed > 0L) {
                  return parsed;
               }
            }
         }

         return fallbackMs;
      }
   }

   private static long parseTimestampElement(JsonElement value) {
      if (value != null && !value.isJsonNull() && value.isJsonPrimitive()) {
         try {
            if (value.getAsJsonPrimitive().isNumber()) {
               long raw = value.getAsLong();
               return raw < 10000000000L ? raw * 1000L : raw;
            }
         } catch (Exception var5) {
         }

         try {
            String text = value.getAsString();
            return text != null && !text.isBlank() && !"null".equalsIgnoreCase(text) ? Instant.parse(text.trim()).toEpochMilli() : 0L;
         } catch (Exception var4) {
            try {
               String textx = value.getAsString();
               return textx != null && !textx.isBlank() ? OffsetDateTime.parse(textx.trim()).toInstant().toEpochMilli() : 0L;
            } catch (Exception var3) {
               return 0L;
            }
         }
      } else {
         return 0L;
      }
   }

   private static String cleanError(Exception ex) {
      if (ex == null) {
         return "Unknown error.";
      } else {
         String msg = ex.getMessage();
         if (msg == null || msg.isBlank()) {
            msg = ex.getClass().getSimpleName();
         }

         return RiptideDupeRadar.UiTextShortener.shorten(msg, 220);
      }
   }

   private static String randomUrlToken(int bytes) {
      byte[] data = new byte[Math.max(16, bytes)];
      new SecureRandom().nextBytes(data);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
   }

   private static String codeChallenge(String verifier) {
      try {
         MessageDigest digest = MessageDigest.getInstance("SHA-256");
         return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
      } catch (Exception var2) {
         return verifier;
      }
   }

   private static Map<String, String> parseQuery(String raw) {
      if (raw != null && !raw.isBlank()) {
         Map<String, String> out = new LinkedHashMap<>();

         for (String part : raw.split("&")) {
            int eq = part.indexOf(61);
            String key = eq >= 0 ? part.substring(0, eq) : part;
            String value = eq >= 0 ? part.substring(eq + 1) : "";
            out.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
         }

         return out;
      } else {
         return Map.of();
      }
   }

   private static String url(String value) {
      return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
   }

   private static String urlPath(String value) {
      return url(value).replace("+", "%20");
   }

   private static final class CachedFindings {
      long updatedAt;
      List<RiptideDupeRadar.RadarFinding> items = new ArrayList<>();
   }

   private static final class HttpStatusException extends IOException {
      private final int code;

      private HttpStatusException(int code, String body) {
         super("HTTP " + code + ": " + RiptideDupeRadar.UiTextShortener.shorten(body == null ? "" : body, 180));
         this.code = code;
      }

      private int code() {
         return this.code;
      }
   }

   public static enum MatchSource {
      PLUGIN("Plugin"),
      PLUGIN_FUZZY("Fuzzy"),
      SERVER_DOMAIN("Server"),
      SERVER_IP("IP");

      private final String label;

      private MatchSource(String label) {
         this.label = label;
      }

      public String label() {
         return this.label;
      }
   }

   private static final class ProviderCache {
      transient String accessToken;
      transient String refreshToken;
      String encAccessToken;
      String encRefreshToken;
      String username;
      long userUpdatedAt;
      Map<String, RiptideDupeRadar.CachedFindings> findings = new LinkedHashMap<>();
      Map<String, RiptideDupeRadar.CachedFindings> serverFindings = new LinkedHashMap<>();
      int findingsVersion = 8;
      int serverFindingsVersion = 8;

      void ensure() {
         if (this.findings == null) {
            this.findings = new LinkedHashMap<>();
         }

         if (this.serverFindings == null) {
            this.serverFindings = new LinkedHashMap<>();
         }

         if (this.findingsVersion != 8) {
            this.findings = new LinkedHashMap<>();
            this.findingsVersion = 8;
         }

         if (this.serverFindingsVersion != 8) {
            this.serverFindings = new LinkedHashMap<>();
            this.serverFindingsVersion = 8;
         }
      }
   }

   public record RadarFinding(
      String id,
      String title,
      String status,
      String summary,
      String affectedPlugin,
      String sourceUrl,
      String provider,
      long sortTimestampMs,
      List<String> affectedServers
   ) {
      public RadarFinding(
         String id,
         String title,
         String status,
         String summary,
         String affectedPlugin,
         String sourceUrl,
         String provider,
         long sortTimestampMs,
         List<String> affectedServers
      ) {
         affectedServers = affectedServers == null ? List.of() : List.copyOf(affectedServers);
         this.id = id;
         this.title = title;
         this.status = status;
         this.summary = summary;
         this.affectedPlugin = affectedPlugin;
         this.sourceUrl = sourceUrl;
         this.provider = provider;
         this.sortTimestampMs = sortTimestampMs;
         this.affectedServers = affectedServers;
      }
   }

   private static final class RadarFindingHit {
      final RiptideDupeRadar.RadarMatch match;
      final RiptideDupeRadar.RadarFinding finding;

      RadarFindingHit(RiptideDupeRadar.RadarMatch match, RiptideDupeRadar.RadarFinding finding) {
         this.match = match;
         this.finding = finding;
      }
   }

   public record RadarMatch(
      String detectedPlugin,
      String providerPlugin,
      String matchConfidence,
      RiptideDupeRadar.MatchSource matchSource,
      double matchScore,
      String matchedInput,
      String sourceUrl,
      String provider,
      List<RiptideDupeRadar.RadarFinding> findings,
      long sortTimestampMs
   ) {
      public RadarMatch(
         String detectedPlugin,
         String providerPlugin,
         String matchConfidence,
         RiptideDupeRadar.MatchSource matchSource,
         double matchScore,
         String matchedInput,
         String sourceUrl,
         String provider,
         List<RiptideDupeRadar.RadarFinding> findings,
         long sortTimestampMs
      ) {
         detectedPlugin = detectedPlugin == null ? "" : detectedPlugin;
         providerPlugin = providerPlugin == null ? "" : providerPlugin;
         matchConfidence = matchConfidence == null ? "Unknown" : matchConfidence;
         matchSource = matchSource == null ? RiptideDupeRadar.MatchSource.PLUGIN_FUZZY : matchSource;
         matchedInput = matchedInput == null ? "" : matchedInput;
         findings = findings == null ? List.of() : List.copyOf(findings);
         this.detectedPlugin = detectedPlugin;
         this.providerPlugin = providerPlugin;
         this.matchConfidence = matchConfidence;
         this.matchSource = matchSource;
         this.matchScore = matchScore;
         this.matchedInput = matchedInput;
         this.sourceUrl = sourceUrl;
         this.provider = provider;
         this.findings = findings;
         this.sortTimestampMs = sortTimestampMs;
      }

      public String displayLabel() {
         if (this.matchSource != RiptideDupeRadar.MatchSource.SERVER_DOMAIN && this.matchSource != RiptideDupeRadar.MatchSource.SERVER_IP) {
            return this.providerPlugin != null && !this.providerPlugin.isBlank() && !this.providerPlugin.equalsIgnoreCase(this.detectedPlugin)
               ? this.detectedPlugin + " -> " + this.providerPlugin
               : this.detectedPlugin;
         } else {
            return this.matchedInput != null && !this.matchedInput.isBlank() ? this.matchedInput : this.detectedPlugin;
         }
      }
   }

   private static final class RadarMatchBuilder {
      final RiptideDupeRadar.RadarMatch prototype;
      final List<RiptideDupeRadar.RadarFinding> findings = new ArrayList<>();

      RadarMatchBuilder(RiptideDupeRadar.RadarMatch prototype) {
         this.prototype = prototype;
      }

      RiptideDupeRadar.RadarMatch toMatch() {
         long newest = RiptideDupeRadar.newestTimestamp(this.findings);
         return new RiptideDupeRadar.RadarMatch(
            this.prototype.detectedPlugin(),
            this.prototype.providerPlugin(),
            this.prototype.matchConfidence(),
            this.prototype.matchSource(),
            this.prototype.matchScore(),
            this.prototype.matchedInput(),
            this.prototype.sourceUrl(),
            this.prototype.provider(),
            this.findings,
            newest
         );
      }
   }

   public record RadarPluginSnapshot(
      String displayName, String canonicalKey, String confidence, int commandCount, List<String> channels, List<String> guis, boolean feature
   ) {
      public RadarPluginSnapshot(
         String displayName, String canonicalKey, String confidence, int commandCount, List<String> channels, List<String> guis, boolean feature
      ) {
         displayName = displayName == null ? "" : displayName.trim();
         canonicalKey = canonicalKey == null ? RiptideDupeRadar.normalize(displayName) : canonicalKey.trim();
         confidence = confidence == null ? "Unknown" : confidence.trim();
         channels = channels == null ? List.of() : List.copyOf(channels);
         guis = guis == null ? List.of() : List.copyOf(guis);
         this.displayName = displayName;
         this.canonicalKey = canonicalKey;
         this.confidence = confidence;
         this.commandCount = commandCount;
         this.channels = channels;
         this.guis = guis;
         this.feature = feature;
      }

      public boolean isPluginIdentity() {
         if (!this.feature && !this.displayName.isBlank()) {
            String lowerConfidence = this.confidence.toLowerCase(Locale.ROOT);
            return lowerConfidence.contains("exact") || lowerConfidence.contains("strong");
         } else {
            return false;
         }
      }
   }

   public record RadarServerIdentity(String value, boolean ip) {
      public RadarServerIdentity(String value, boolean ip) {
         value = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
         this.value = value;
         this.ip = ip;
      }
   }

   public record RadarServerSnapshot(List<RiptideDupeRadar.RadarServerIdentity> identities) {
      public static final RiptideDupeRadar.RadarServerSnapshot EMPTY = new RiptideDupeRadar.RadarServerSnapshot(List.of());

      public RadarServerSnapshot(List<RiptideDupeRadar.RadarServerIdentity> identities) {
         if (identities == null) {
            identities = List.of();
         } else {
            Map<String, RiptideDupeRadar.RadarServerIdentity> unique = new LinkedHashMap<>();

            for (RiptideDupeRadar.RadarServerIdentity identity : identities) {
               if (identity != null && !identity.value().isBlank()) {
                  unique.putIfAbsent((identity.ip() ? "ip:" : "domain:") + identity.value(), identity);
               }
            }

            identities = List.copyOf(unique.values());
         }

         this.identities = identities;
      }
   }

   public record RadarState(
      String providerId,
      String providerLabel,
      boolean authenticated,
      boolean authenticating,
      boolean checking,
      String username,
      String status,
      String error,
      List<RiptideDupeRadar.RadarMatch> matches,
      int detectedPluginCount,
      long updatedAtMs,
      boolean stale
   ) {
      public RadarState(
         String providerId,
         String providerLabel,
         boolean authenticated,
         boolean authenticating,
         boolean checking,
         String username,
         String status,
         String error,
         List<RiptideDupeRadar.RadarMatch> matches,
         int detectedPluginCount,
         long updatedAtMs,
         boolean stale
      ) {
         matches = matches == null ? List.of() : List.copyOf(matches);
         this.providerId = providerId;
         this.providerLabel = providerLabel;
         this.authenticated = authenticated;
         this.authenticating = authenticating;
         this.checking = checking;
         this.username = username;
         this.status = status;
         this.error = error;
         this.matches = matches;
         this.detectedPluginCount = detectedPluginCount;
         this.updatedAtMs = updatedAtMs;
         this.stale = stale;
      }

      public RiptideDupeRadar.RadarState withAuth(boolean authenticated, String username) {
         return new RiptideDupeRadar.RadarState(
            this.providerId,
            this.providerLabel,
            authenticated,
            this.authenticating,
            this.checking,
            username,
            this.status,
            this.error,
            this.matches,
            this.detectedPluginCount,
            this.updatedAtMs,
            this.stale
         );
      }

      public RiptideDupeRadar.RadarState withBusy(boolean authenticating, boolean checking, String status, String error) {
         return new RiptideDupeRadar.RadarState(
            this.providerId,
            this.providerLabel,
            this.authenticated,
            authenticating,
            checking,
            this.username,
            status == null ? this.status : status,
            error,
            this.matches,
            this.detectedPluginCount,
            this.updatedAtMs,
            this.stale
         );
      }

      public RiptideDupeRadar.RadarState withMatches(List<RiptideDupeRadar.RadarMatch> matches, int detectedPluginCount, boolean stale) {
         return new RiptideDupeRadar.RadarState(
            this.providerId,
            this.providerLabel,
            this.authenticated,
            this.authenticating,
            this.checking,
            this.username,
            this.status,
            this.error,
            matches,
            detectedPluginCount,
            System.currentTimeMillis(),
            stale
         );
      }

      public RiptideDupeRadar.RadarState withPluginCount(int count) {
         return new RiptideDupeRadar.RadarState(
            this.providerId,
            this.providerLabel,
            this.authenticated,
            this.authenticating,
            this.checking,
            this.username,
            this.status,
            this.error,
            this.matches,
            count,
            this.updatedAtMs,
            this.stale
         );
      }

      public RiptideDupeRadar.RadarState markStale() {
         return new RiptideDupeRadar.RadarState(
            this.providerId,
            this.providerLabel,
            this.authenticated,
            this.authenticating,
            this.checking,
            this.username,
            this.status,
            this.error,
            this.matches,
            this.detectedPluginCount,
            this.updatedAtMs,
            true
         );
      }
   }

   public record StatusCounts(int working, int patched, int verified) {
   }

   private record TokenResponse(String accessToken, String refreshToken) {
   }

   private static final class UiTextShortener {
      static String shorten(String value, int max) {
         if (value == null) {
            return "";
         } else {
            String clean = value.trim();
            return clean.length() <= max ? clean : clean.substring(0, Math.max(0, max - 3)).trim() + "...";
         }
      }

      static String titleCase(String value) {
         if (value != null && !value.isBlank()) {
            String lower = value.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
            StringBuilder sb = new StringBuilder(lower.length());
            boolean upperNext = true;

            for (int i = 0; i < lower.length(); i++) {
               char c = lower.charAt(i);
               if (Character.isWhitespace(c)) {
                  upperNext = true;
                  sb.append(c);
               } else if (upperNext) {
                  sb.append(Character.toUpperCase(c));
                  upperNext = false;
               } else {
                  sb.append(c);
               }
            }

            return sb.toString();
         } else {
            return "";
         }
      }
   }
}
