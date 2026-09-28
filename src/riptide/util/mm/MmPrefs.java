package riptide.util.mm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MmPrefs {
   private static final File FILE = new File(riptide.RiptideClientAddon.FOLDER, "mm_prefs.properties");
   private static final File TMP = new File(riptide.RiptideClientAddon.FOLDER, "mm_prefs.properties.tmp");
   private static final int MAX_SET = 4096;
   private static final long SAVE_DEBOUNCE_MS = 400L;
   private static volatile MmPrefs instance;
   private static final ScheduledExecutorService WRITER = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "mm-prefs-writer");
      t.setDaemon(true);
      return t;
   });
   private final AtomicBoolean dirty = new AtomicBoolean(false);
   private final AtomicBoolean scheduled = new AtomicBoolean(false);
   private volatile boolean shareServer;
   private volatile boolean shareLocation;
   private volatile boolean autoJoinPublic;
   private volatile boolean killSwitch;
   private volatile boolean chatMirror = true;
   private volatile boolean debugLog;
   private volatile int defaultMaxPlayers = 8;
   private volatile boolean defaultPublic = true;
   private volatile String discordSession = "";
   private volatile String discordName = "";
   private volatile String discordIdToken = "";
   private volatile String defaultLobbyName = "";
   private volatile String defaultServer = "";
   private volatile String defaultPlugins = "";
   private volatile int myDupeStatus = -1;
   private final Set<String> blocked = Collections.synchronizedSet(new LinkedHashSet<>());
   private final Set<String> trusted = Collections.synchronizedSet(new LinkedHashSet<>());
   private final Map<String, String> blockedDiscord = Collections.synchronizedMap(new LinkedHashMap<>());
   private final Map<String, String> bannedDiscord = Collections.synchronizedMap(new LinkedHashMap<>());
   private final Map<String, boolean[]> lobbyShare = Collections.synchronizedMap(new LinkedHashMap<>());

   private MmPrefs() {
   }

   public static MmPrefs get() {
      MmPrefs local = instance;
      if (local == null) {
         synchronized (MmPrefs.class) {
            if (instance == null) {
               instance = load();
            }

            local = instance;
         }
      }

      return local;
   }

   public boolean shareServer() {
      return this.shareServer;
   }

   public void setShareServer(boolean v) {
      this.shareServer = v;
      this.save();
   }

   public boolean shareLocation() {
      return this.shareLocation;
   }

   public void setShareLocation(boolean v) {
      this.shareLocation = v;
      this.save();
   }

   public boolean autoJoinPublic() {
      return this.autoJoinPublic;
   }

   public void setAutoJoinPublic(boolean v) {
      this.autoJoinPublic = v;
      this.save();
   }

   public boolean lobbyShareServer(String lobbyId) {
      return this.lobbyShare(lobbyId, 0);
   }

   public boolean lobbyShareLocation(String lobbyId) {
      return this.lobbyShare(lobbyId, 1);
   }

   public void setLobbyShareServer(String lobbyId, boolean v) {
      this.setLobbyShare(lobbyId, 0, v);
   }

   public void setLobbyShareLocation(String lobbyId, boolean v) {
      this.setLobbyShare(lobbyId, 1, v);
   }

   private boolean lobbyShare(String lobbyId, int idx) {
      boolean[] cur = lobbyId == null ? null : this.lobbyShare.get(lobbyId);
      return cur != null && cur[idx];
   }

   private void setLobbyShare(String lobbyId, int idx, boolean v) {
      if (lobbyId != null && !lobbyId.isBlank() && lobbyId.length() <= 128) {
         synchronized (this.lobbyShare) {
            boolean[] cur = this.lobbyShare.get(lobbyId);
            if (cur == null) {
               if (!v) {
                  return;
               }

               if (this.lobbyShare.size() >= 4096) {
                  return;
               }

               cur = new boolean[2];
               this.lobbyShare.put(lobbyId, cur);
            }

            cur[idx] = v;
            if (!cur[0] && !cur[1]) {
               this.lobbyShare.remove(lobbyId);
            }
         }

         this.save();
      }
   }

   public boolean killSwitch() {
      return this.killSwitch;
   }

   public void setKillSwitch(boolean v) {
      this.killSwitch = v;
      this.save();
   }

   public boolean chatMirror() {
      return this.chatMirror;
   }

   public void setChatMirror(boolean v) {
      this.chatMirror = v;
      this.save();
   }

   public boolean debugLog() {
      return this.debugLog;
   }

   public void setDebugLog(boolean v) {
      this.debugLog = v;
      this.save();
   }

   public int defaultMaxPlayers() {
      return this.defaultMaxPlayers;
   }

   public void setDefaultMaxPlayers(int v) {
      this.defaultMaxPlayers = v <= 0 ? 0 : Math.max(2, Math.min(500, v));
      this.save();
   }

   public boolean defaultPublic() {
      return this.defaultPublic;
   }

   public void setDefaultPublic(boolean v) {
      this.defaultPublic = v;
      this.save();
   }

   public String defaultLobbyName() {
      return this.defaultLobbyName;
   }

   public void setDefaultLobbyName(String v) {
      this.defaultLobbyName = v == null ? "" : v;
      this.save();
   }

   public String defaultServer() {
      return this.defaultServer;
   }

   public void setDefaultServer(String v) {
      this.defaultServer = v == null ? "" : v;
      this.save();
   }

   public String defaultPlugins() {
      return this.defaultPlugins;
   }

   public void setDefaultPlugins(String v) {
      this.defaultPlugins = v == null ? "" : v;
      this.save();
   }

   public int myDupeStatus() {
      return this.myDupeStatus;
   }

   public void setMyDupeStatus(int v) {
      this.myDupeStatus = v >= 0 && v <= 1 ? v : -1;
      this.save();
   }

   public String discordSession() {
      return this.discordSession;
   }

   public String discordName() {
      return this.discordName;
   }

   public String discordIdToken() {
      return this.discordIdToken;
   }

   public void setDiscord(String sealedSession, String name, String idToken) {
      this.discordSession = sealedSession == null ? "" : sealedSession;
      this.discordName = name == null ? "" : name;
      this.discordIdToken = idToken == null ? "" : idToken;
      this.save();
   }

   public void setDiscordIdToken(String idToken) {
      this.discordIdToken = idToken == null ? "" : idToken;
      this.save();
   }

   public void clearDiscord() {
      this.discordSession = "";
      this.discordName = "";
      this.discordIdToken = "";
      this.save();
   }

   public boolean isBlocked(String fpHex) {
      return this.blocked.contains(fpHex);
   }

   public void block(String fpHex) {
      if (validFp(fpHex)) {
         addCapped(this.blocked, fpHex);
         this.trusted.remove(fpHex);
         this.save();
      }
   }

   public void unblock(String fpHex) {
      if (this.blocked.remove(fpHex)) {
         this.save();
      }
   }

   public boolean isTrusted(String fpHex) {
      return this.trusted.contains(fpHex);
   }

   public void trust(String fpHex) {
      if (validFp(fpHex)) {
         addCapped(this.trusted, fpHex);
         this.blocked.remove(fpHex);
         this.save();
      }
   }

   public void untrust(String fpHex) {
      if (this.trusted.remove(fpHex)) {
         this.save();
      }
   }

   public Set<String> blockedSnapshot() {
      synchronized (this.blocked) {
         return new LinkedHashSet<>(this.blocked);
      }
   }

   public boolean isBlockedDiscord(String id) {
      return id != null && this.blockedDiscord.containsKey(id);
   }

   public void blockDiscord(String id, String name) {
      if (validId(id)) {
         putCapped(this.blockedDiscord, id, name);
         this.save();
      }
   }

   public void unblockDiscord(String id) {
      if (this.blockedDiscord.remove(id) != null) {
         this.save();
      }
   }

   public boolean isBannedDiscord(String id) {
      return id != null && this.bannedDiscord.containsKey(id);
   }

   public void banDiscord(String id, String name) {
      if (validId(id)) {
         putCapped(this.bannedDiscord, id, name);
         this.save();
      }
   }

   public void unbanDiscord(String id) {
      if (this.bannedDiscord.remove(id) != null) {
         this.save();
      }
   }

   public Map<String, String> blockedDiscordSnapshot() {
      synchronized (this.blockedDiscord) {
         return new LinkedHashMap<>(this.blockedDiscord);
      }
   }

   public Map<String, String> bannedDiscordSnapshot() {
      synchronized (this.bannedDiscord) {
         return new LinkedHashMap<>(this.bannedDiscord);
      }
   }

   public void refreshDiscordName(String id, String name) {
      if (validId(id) && name != null && !name.isBlank()) {
         String clean = sanitizeName(name);
         if (!clean.isEmpty()) {
            boolean changed = false;
            synchronized (this.blockedDiscord) {
               if (this.blockedDiscord.containsKey(id) && !clean.equals(this.blockedDiscord.get(id))) {
                  this.blockedDiscord.put(id, clean);
                  changed = true;
               }
            }

            synchronized (this.bannedDiscord) {
               if (this.bannedDiscord.containsKey(id) && !clean.equals(this.bannedDiscord.get(id))) {
                  this.bannedDiscord.put(id, clean);
                  changed = true;
               }
            }

            if (changed) {
               this.save();
            }
         }
      }
   }

   private static MmPrefs load() {
      MmPrefs p = new MmPrefs();
      if (FILE.exists()) {
         try (FileInputStream in = new FileInputStream(FILE)) {
            Properties props = new Properties();
            props.load(in);
            p.shareServer = bool(props, "shareServer");
            p.shareLocation = bool(props, "shareLocation");
            p.autoJoinPublic = bool(props, "autoJoinPublic");
            p.killSwitch = bool(props, "killSwitch");
            p.chatMirror = bool(props, "chatMirror", true);
            p.debugLog = bool(props, "debugLog");
            p.defaultPublic = bool(props, "defaultPublic", true);

            try {
               p.defaultMaxPlayers = Integer.parseInt(props.getProperty("defaultMaxPlayers", "8"));
            } catch (NumberFormatException var6) {
            }

            p.defaultMaxPlayers = p.defaultMaxPlayers <= 0 ? 0 : Math.max(2, Math.min(500, p.defaultMaxPlayers));
            p.defaultLobbyName = props.getProperty("defaultLobbyName", "");
            p.defaultServer = props.getProperty("defaultServer", "");
            p.defaultPlugins = props.getProperty("defaultPlugins", "");
            p.discordSession = props.getProperty("discordSession", "");
            p.discordName = props.getProperty("discordName", "");
            p.discordIdToken = props.getProperty("discordIdToken", "");

            try {
               p.myDupeStatus = Integer.parseInt(props.getProperty("myDupeStatus", "-1"));
            } catch (NumberFormatException var5) {
            }

            addAll(p.blocked, props.getProperty("blocked", ""));
            addAll(p.trusted, props.getProperty("trusted", ""));
            addIdMap(p.blockedDiscord, props.getProperty("blockedDiscord", ""));
            addIdMap(p.bannedDiscord, props.getProperty("bannedDiscord", ""));
            readLobbyShare(p.lobbyShare, props);
         } catch (Throwable var8) {
            riptide.RiptideClientAddon.LOG.warn("Failed to read Matchmaking prefs", var8);
         }
      }

      Runtime.getRuntime().addShutdownHook(new Thread(p::flush, "mm-prefs-flush"));
      return p;
   }

   private void save() {
      this.dirty.set(true);
      if (this.scheduled.compareAndSet(false, true)) {
         try {
            WRITER.schedule(() -> {
               this.scheduled.set(false);
               this.flush();
            }, 400L, TimeUnit.MILLISECONDS);
         } catch (Throwable var2) {
            this.scheduled.set(false);
            this.flush();
         }
      }
   }

   private void flush() {
      if (this.dirty.compareAndSet(true, false)) {
         this.writeNow();
      }
   }

   private synchronized void writeNow() {
      try {
         File dir = FILE.getParentFile();
         if (dir != null) {
            dir.mkdirs();
         }

         Properties props = new Properties();
         props.setProperty("shareServer", Boolean.toString(this.shareServer));
         props.setProperty("shareLocation", Boolean.toString(this.shareLocation));
         props.setProperty("autoJoinPublic", Boolean.toString(this.autoJoinPublic));
         props.setProperty("killSwitch", Boolean.toString(this.killSwitch));
         props.setProperty("chatMirror", Boolean.toString(this.chatMirror));
         props.setProperty("debugLog", Boolean.toString(this.debugLog));
         props.setProperty("defaultPublic", Boolean.toString(this.defaultPublic));
         props.setProperty("defaultMaxPlayers", Integer.toString(this.defaultMaxPlayers));
         props.setProperty("defaultLobbyName", this.defaultLobbyName);
         props.setProperty("defaultServer", this.defaultServer);
         props.setProperty("defaultPlugins", this.defaultPlugins);
         props.setProperty("discordSession", this.discordSession);
         props.setProperty("discordName", this.discordName);
         props.setProperty("discordIdToken", this.discordIdToken);
         props.setProperty("myDupeStatus", Integer.toString(this.myDupeStatus));
         props.setProperty("blocked", String.join(",", this.blockedSnapshot()));
         synchronized (this.trusted) {
            props.setProperty("trusted", String.join(",", this.trusted));
         }

         props.setProperty("blockedDiscord", idMapCsv(this.blockedDiscord));
         props.setProperty("bannedDiscord", idMapCsv(this.bannedDiscord));
         writeLobbyShare(this.lobbyShare, props);

         try (FileOutputStream out = new FileOutputStream(TMP)) {
            props.store(out, "Riptide Matchmaking preferences (passphrases are never stored)");
         }

         try {
            Files.move(TMP.toPath(), FILE.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException var7) {
            Files.move(TMP.toPath(), FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (Throwable var10) {
         riptide.RiptideClientAddon.LOG.warn("Failed to save Matchmaking prefs", var10);
      }
   }

   private static boolean bool(Properties p, String key) {
      return bool(p, key, false);
   }

   private static boolean bool(Properties p, String key, boolean def) {
      return Boolean.parseBoolean(p.getProperty(key, Boolean.toString(def)));
   }

   private static void addAll(Set<String> set, String csv) {
      if (csv != null && !csv.isBlank()) {
         for (String s : csv.split(",")) {
            if (validFp(s)) {
               addCapped(set, s.trim());
            }
         }
      }
   }

   private static void readLobbyShare(Map<String, boolean[]> out, Properties props) {
      for (String key : props.stringPropertyNames()) {
         if (key.startsWith("lobby.")) {
            String rest = key.substring("lobby.".length());
            int idx = rest.endsWith(".shareServer") ? 0 : (rest.endsWith(".shareLocation") ? 1 : -1);
            if (idx >= 0 && Boolean.parseBoolean(props.getProperty(key))) {
               String id = rest.substring(0, rest.length() - (idx == 0 ? ".shareServer" : ".shareLocation").length());
               if (!id.isBlank() && id.length() <= 128) {
                  synchronized (out) {
                     if (out.size() < 4096 || out.containsKey(id)) {
                        boolean[] cur = out.get(id);
                        if (cur == null) {
                           cur = new boolean[2];
                           out.put(id, cur);
                        }

                        cur[idx] = true;
                     }
                  }
               }
            }
         }
      }
   }

   private static void writeLobbyShare(Map<String, boolean[]> map, Properties props) {
      synchronized (map) {
         for (Entry<String, boolean[]> e : map.entrySet()) {
            if (e.getValue()[0]) {
               props.setProperty("lobby." + e.getKey() + ".shareServer", "true");
            }

            if (e.getValue()[1]) {
               props.setProperty("lobby." + e.getKey() + ".shareLocation", "true");
            }
         }
      }
   }

   private static void addIdMap(Map<String, String> map, String csv) {
      if (csv != null && !csv.isBlank()) {
         for (String entry : csv.split(",")) {
            int eq = entry.indexOf(61);
            String id = (eq >= 0 ? entry.substring(0, eq) : entry).trim();
            String name = eq >= 0 ? entry.substring(eq + 1).trim() : "";
            if (validId(id)) {
               putCapped(map, id, name);
            }
         }
      }
   }

   private static String idMapCsv(Map<String, String> map) {
      StringBuilder sb = new StringBuilder();
      synchronized (map) {
         for (Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 0) {
               sb.append(',');
            }

            sb.append(e.getKey()).append('=').append(sanitizeName(e.getValue()));
         }
      }

      return sb.toString();
   }

   private static String sanitizeName(String n) {
      return n == null ? "" : n.replace(",", "").replace("=", "").trim();
   }

   private static void putCapped(Map<String, String> map, String id, String name) {
      synchronized (map) {
         map.put(id, sanitizeName(name));

         while (map.size() > 4096) {
            Iterator<String> it = map.keySet().iterator();
            if (!it.hasNext()) {
               break;
            }

            it.next();
            it.remove();
         }
      }
   }

   private static void addCapped(Set<String> set, String value) {
      synchronized (set) {
         set.add(value);

         while (set.size() > 4096) {
            Iterator<String> it = set.iterator();
            if (!it.hasNext()) {
               break;
            }

            it.next();
            it.remove();
         }
      }
   }

   private static boolean validFp(String s) {
      if (s == null) {
         return false;
      } else {
         String t = s.trim();
         if (!t.isEmpty() && t.length() <= 64) {
            for (int i = 0; i < t.length(); i++) {
               char c = t.charAt(i);
               boolean hex = c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
               if (!hex) {
                  return false;
               }
            }

            return true;
         } else {
            return false;
         }
      }
   }

   private static boolean validId(String s) {
      if (s == null) {
         return false;
      } else {
         String t = s.trim();
         if (!t.isEmpty() && t.length() <= 32) {
            for (int i = 0; i < t.length(); i++) {
               if (t.charAt(i) < '0' || t.charAt(i) > '9') {
                  return false;
               }
            }

            return true;
         } else {
            return false;
         }
      }
   }
}
