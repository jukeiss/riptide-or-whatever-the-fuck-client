package riptide.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import riptide.gui.RiptideThemeApplyOverlay;
import riptide.modules.RiptideModule;

public final class RiptideProfileManager {
   private static RiptideProfileManager INSTANCE;
   public static final String DEFAULT_ID = "default";
   private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
   private final File dir = new File(riptide.RiptideClientAddon.FOLDER, "profiles");
   private final List<RiptideProfile> profiles = new ArrayList<>();
   private String activeId = "";
   private volatile boolean dirty = false;
   private volatile long revision = 0L;
   private boolean applying = false;
   private static final long MIRROR_DEBOUNCE_MS = 750L;
   private volatile boolean mirrorPending = false;
   private long mirrorDueAtMs = 0L;
   private RiptideConfig pendingMirrorSnapshot;
   private volatile boolean loadPending = false;

   private RiptideProfileManager() {
      this.loadAll();
      this.ensureDefault();
      this.dedupeNames();
      this.adoptActive();
      RiptideConfig.afterSave = this::onLiveConfigSaved;
      RiptideConfig.afterPersistenceSnapshot = this::onLiveConfigSnapshot;
   }

   public static synchronized RiptideProfileManager get() {
      if (INSTANCE == null) {
         INSTANCE = new RiptideProfileManager();
      }

      return INSTANCE;
   }

   static void flushPendingMirrorIfInitialized() {
      RiptideProfileManager current = INSTANCE;
      if (current != null) {
         current.flushMirrorNow();
      }
   }

   public synchronized List<RiptideProfile> list() {
      List<RiptideProfile> out = new ArrayList<>(this.profiles);
      out.sort((a, b) -> {
         boolean da = "default".equals(a.id);
         boolean db = "default".equals(b.id);
         if (da != db) {
            return da ? -1 : 1;
         } else {
            return Long.compare(a.createdAt, b.createdAt);
         }
      });
      return out;
   }

   public synchronized int count() {
      return this.profiles.size();
   }

   public static boolean isDefault(RiptideProfile p) {
      return p != null && "default".equals(p.id);
   }

   public synchronized String activeId() {
      return this.activeId;
   }

   public synchronized boolean isDirty() {
      return this.dirty;
   }

   public long revision() {
      return this.revision;
   }

   public synchronized RiptideProfile active() {
      return this.byId(this.activeId);
   }

   public synchronized RiptideProfile byId(String id) {
      if (id == null) {
         return null;
      } else {
         for (RiptideProfile p : this.profiles) {
            if (p != null && id.equals(p.id)) {
               return p;
            }
         }

         return null;
      }
   }

   private boolean existsId(String id) {
      return this.byId(id) != null;
   }

   public synchronized boolean nameExists(String name, String excludeId) {
      if (name == null) {
         return false;
      } else {
         String want = name.strip();
         if (want.isEmpty()) {
            return false;
         } else {
            for (RiptideProfile p : this.profiles) {
               if (p != null && p.displayName != null && (excludeId == null || !excludeId.equals(p.id)) && p.displayName.strip().equalsIgnoreCase(want)) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   public synchronized RiptideProfile create(String name) {
      return this.create(name, true, false, true);
   }

   public synchronized RiptideProfile create(String name, boolean autoSave, boolean ownMacroLibrary, boolean ownThemeColor) {
      if (this.nameExists(name, null)) {
         return null;
      } else {
         RiptideProfile p = new RiptideProfile(this.slug(name), name != null && !name.isBlank() ? name.strip() : "Profile");
         p.autoSave = autoSave;
         p.ownMacroLibrary = ownMacroLibrary;
         p.ownThemeColor = ownThemeColor;
         p.createdAt = p.updatedAt = System.currentTimeMillis();
         p.snapshot = this.snapshotLiveConfig();
         if (ownMacroLibrary) {
            RiptideMacroManager.writeEmptyLibrary(this.companionFile(p));
         }

         this.profiles.add(p);
         this.writeProfile(p, p.snapshot);
         this.revision++;
         return p;
      }
   }

   public synchronized RiptideProfile duplicate(RiptideProfile src, String name) {
      if (src == null) {
         return this.create(name);
      } else if (this.nameExists(name, null)) {
         return null;
      } else {
         RiptideProfile p = new RiptideProfile(this.slug(name), name != null && !name.isBlank() ? name.strip() : src.displayName + " copy");
         p.autoSave = src.autoSave;
         p.ownMacroLibrary = src.ownMacroLibrary;
         p.ownThemeColor = src.ownThemeColor;
         p.serverPatterns = new ArrayList<>(src.serverPatterns);
         p.createdAt = p.updatedAt = System.currentTimeMillis();
         p.snapshot = src.snapshot.deepCopy();
         this.profiles.add(p);
         this.writeProfile(p);
         if (src.ownMacroLibrary) {
            try {
               File from = this.companionFile(src);
               if (from.exists()) {
                  Files.copy(from.toPath(), this.companionFile(p).toPath(), StandardCopyOption.REPLACE_EXISTING);
               }
            } catch (Exception var5) {
               riptide.RiptideClientAddon.LOG.warn("Could not copy macro library while duplicating profile", var5);
            }
         }

         this.revision++;
         return p;
      }
   }

   public synchronized boolean rename(RiptideProfile p, String name) {
      if (p == null || name == null || name.isBlank()) {
         return false;
      } else if (this.nameExists(name, p.id)) {
         return false;
      } else {
         p.displayName = name.strip();
         p.updatedAt = System.currentTimeMillis();
         this.writeProfile(p);
         this.revision++;
         return true;
      }
   }

   public synchronized boolean delete(RiptideProfile p) {
      if (p != null && !"default".equals(p.id) && this.profiles.size() > 1) {
         boolean wasActive = p.id.equals(this.activeId);
         this.profiles.remove(p);
         this.deleteProfileFiles(p);
         if (wasActive) {
            RiptideProfile fallback = this.byId("default");
            if (fallback == null) {
               fallback = this.profiles.get(0);
            }

            this.load(fallback);
         }

         this.deleteFile(this.companionFile(p));
         this.deleteFile(new File(this.dir, p.id + ".macros.nbt.bak"));
         this.revision++;
         return true;
      } else {
         return false;
      }
   }

   public synchronized void setAutoSave(RiptideProfile p, boolean autoSave) {
      if (p != null && p.autoSave != autoSave) {
         p.autoSave = autoSave;
         p.updatedAt = System.currentTimeMillis();
         if (autoSave && p.id.equals(this.activeId)) {
            p.snapshot = this.snapshotLiveConfig();
            this.dirty = false;
         }

         this.writeProfile(p, autoSave && p.id.equals(this.activeId) ? p.snapshot : null);
         this.revision++;
      }
   }

   public synchronized void setOwnMacroLibrary(RiptideProfile p, boolean own) {
      if (p != null && p.ownMacroLibrary != own) {
         p.ownMacroLibrary = own;
         p.updatedAt = System.currentTimeMillis();
         if (own) {
            this.seedCompanionIfAbsent(p);
         }

         this.writeProfile(p);
         if (p.id.equals(this.activeId)) {
            if (own) {
               RiptideMacroManager.get().switchBackingFile(this.companionFile(p), false, true);
            } else {
               RiptideMacroManager.get().resetToSharedLibrary();
            }
         }

         this.revision++;
      }
   }

   public synchronized void setOwnThemeColor(RiptideProfile p, boolean own) {
      if (p != null && p.ownThemeColor != own) {
         p.ownThemeColor = own;
         p.updatedAt = System.currentTimeMillis();
         this.writeProfile(p);
         this.revision++;
      }
   }

   public synchronized void setServerPatterns(RiptideProfile p, List<String> patterns) {
      if (p != null) {
         List<String> clean = new ArrayList<>();
         if (patterns != null) {
            for (String s : patterns) {
               if (s != null) {
                  String n = s.strip().toLowerCase(Locale.ROOT);
                  if (!n.isEmpty() && !clean.contains(n)) {
                     clean.add(n);
                  }
               }
            }
         }

         p.serverPatterns = clean;
         p.updatedAt = System.currentTimeMillis();
         this.writeProfile(p);
         this.revision++;
      }
   }

   public synchronized void save(RiptideProfile p) {
      if (p != null) {
         p.snapshot = this.snapshotLiveConfig();
         p.updatedAt = System.currentTimeMillis();
         this.writeProfile(p, p.snapshot);
         if (p.id.equals(this.activeId)) {
            this.dirty = false;
         }

         this.revision++;
      }
   }

   public synchronized void load(RiptideProfile p) {
      if (p != null) {
         RiptideConfig.enqueuePendingSaveNow();
         this.flushMirrorNow();
         this.applying = true;

         try {
            RiptideConfig next = p.snapshot.deepCopy();
            next.activeProfileId = p.id;
            if (!p.ownThemeColor) {
               RiptideConfig liveNow = RiptideConfig.getGlobal();
               if (liveNow != null) {
                  next.themeColors = liveNow.deepCopy().themeColors;
               }
            }

            RiptideModule.get().applyConfig(next);
            this.switchMacroLibraryFor(p);
            this.activeId = p.id;
            this.dirty = false;
            this.revision++;
         } finally {
            this.applying = false;
         }

         RiptideClientMessaging.sendPrefixed("§aLoaded profile: " + p.displayName);
      }
   }

   public synchronized void beginLoad(RiptideProfile p) {
      if (p != null && !this.loadPending) {
         if (!p.id.equals(this.activeId)) {
            this.loadPending = true;
            Runnable applyJob = RiptideThemeApplyOverlay.beginJob("Switching Profile", "Applying profile…");
            RiptideThemeApplyOverlay.runAfterShown(() -> this.runSwitch(p, applyJob));
         }
      }
   }

   private synchronized void runSwitch(RiptideProfile p, Runnable applyJob) {
      Runnable macroJob = RiptideThemeApplyOverlay.beginJob("Switching Profile", "Loading macros…");
      boolean macroKicked = false;

      try {
         RiptideConfig.enqueuePendingSaveNow();
         this.flushMirrorNow();
         this.applying = true;
         RiptideConfig next = p.snapshot.deepCopy();
         next.activeProfileId = p.id;
         if (!p.ownThemeColor) {
            RiptideConfig live = RiptideConfig.getGlobal();
            if (live != null) {
               next.themeColors = live.deepCopy().themeColors;
            }
         }

         RiptideModule.get().applyConfig(next);
         this.activeId = p.id;
         this.dirty = false;
         this.revision++;
         RiptideClientMessaging.sendPrefixed("§aLoaded profile: " + p.displayName);
         RiptideBackgroundTasks.runTracked("profile-macros", () -> {
            try {
               this.switchMacroLibraryFor(p);
            } catch (Throwable var7) {
               riptide.RiptideClientAddon.LOG.warn("Profile macro switch failed", var7);
            } finally {
               macroJob.run();
            }
         });
         macroKicked = true;
      } catch (Throwable var11) {
         riptide.RiptideClientAddon.LOG.warn("Profile switch failed", var11);
      } finally {
         this.applying = false;
         this.loadPending = false;
         applyJob.run();
         if (!macroKicked) {
            macroJob.run();
         }
      }
   }

   public synchronized void applyForServerConnect(ServerAddress addr, ServerData data) {
      String address = data != null && data.ip != null && !data.ip.isBlank() ? data.ip : (addr != null ? addr.getHost() + ":" + addr.getPort() : "");
      RiptideProfile match = this.matchForServer(address);
      if (match != null && !match.id.equals(this.activeId)) {
         RiptideProfile current = this.active();
         if (current != null && !current.autoSave && this.dirty) {
            this.save(current);
         }

         this.load(match);
      }
   }

   public synchronized RiptideProfile matchForServer(String address) {
      if (address != null && !address.isBlank()) {
         String addr = address.strip().toLowerCase(Locale.ROOT);
         String host = addr;
         int port = 25565;
         int colon = addr.lastIndexOf(58);
         if (colon > 0 && colon < addr.length() - 1) {
            try {
               port = Integer.parseInt(addr.substring(colon + 1));
               host = addr.substring(0, colon);
            } catch (NumberFormatException var15) {
            }
         }

         if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
         }

         RiptideProfile best = null;
         int bestLen = -1;
         long bestCreated = Long.MAX_VALUE;

         for (RiptideProfile p : this.profiles) {
            if (p != null && p.serverPatterns != null) {
               for (String pattern : p.serverPatterns) {
                  if (matchesPattern(host, port, pattern)) {
                     int len = pattern.length();
                     if (len > bestLen || len == bestLen && p.createdAt < bestCreated) {
                        best = p;
                        bestLen = len;
                        bestCreated = p.createdAt;
                     }
                  }
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   private static boolean matchesPattern(String host, int port, String pattern) {
      if (pattern == null) {
         return false;
      } else {
         String pat = pattern.strip().toLowerCase(Locale.ROOT);
         if (pat.isEmpty()) {
            return false;
         } else {
            if (pat.endsWith(".")) {
               pat = pat.substring(0, pat.length() - 1);
            }

            if (pat.equals("*")) {
               return true;
            } else if (pat.startsWith("*.")) {
               String suffix = pat.substring(2);
               return host.equals(suffix) || host.endsWith("." + suffix);
            } else {
               int colon = pat.lastIndexOf(58);
               if (colon > 0 && colon < pat.length() - 1) {
                  try {
                     int patPort = Integer.parseInt(pat.substring(colon + 1));
                     return host.equals(pat.substring(0, colon)) && port == patPort;
                  } catch (NumberFormatException var6) {
                  }
               }

               return host.equals(pat);
            }
         }
      }
   }

   private synchronized void onLiveConfigSaved() {
      if (!this.applying) {
         RiptideProfile a = this.active();
         if (a != null) {
            if (a.autoSave) {
               this.pendingMirrorSnapshot = null;
               this.mirrorDueAtMs = System.currentTimeMillis() + 750L;
               this.mirrorPending = true;
            } else {
               this.dirty = true;
               this.revision++;
            }
         }
      }
   }

   private synchronized void onLiveConfigSnapshot(RiptideConfig snapshot) {
      if (!this.applying && snapshot != null && this.mirrorPending) {
         RiptideProfile a = this.active();
         if (a != null && a.autoSave) {
            this.pendingMirrorSnapshot = RiptideConfig.profileSnapshotFromDetached(snapshot);
         }
      }
   }

   public void flushMirrorIfDue() {
      if (this.mirrorPending) {
         synchronized (this) {
            if (this.mirrorPending && System.currentTimeMillis() >= this.mirrorDueAtMs) {
               this.doMirror();
            }
         }
      }
   }

   public synchronized void flushMirrorNow() {
      if (this.mirrorPending && this.pendingMirrorSnapshot == null) {
         RiptideConfigWriter.capturePendingNow();
      }

      if (this.mirrorPending) {
         this.doMirror();
      }
   }

   private void doMirror() {
      this.mirrorPending = false;
      RiptideProfile a = this.active();
      if (a != null && a.autoSave) {
         RiptideConfig snap = this.pendingMirrorSnapshot;
         this.pendingMirrorSnapshot = null;
         if (snap == null) {
            snap = this.snapshotLiveConfig();
         }

         a.snapshot = snap;
         a.updatedAt = System.currentTimeMillis();
         this.writeProfile(a, snap);
         this.dirty = false;
      }
   }

   private RiptideConfig snapshotLiveConfig() {
      return RiptideConfig.getGlobal().snapshotForProfile();
   }

   private File companionFile(RiptideProfile p) {
      return new File(this.dir, p.id + ".macros.nbt");
   }

   private void seedCompanionIfAbsent(RiptideProfile p) {
      File comp = this.companionFile(p);
      File bak = new File(this.dir, p.id + ".macros.nbt.bak");
      if (!comp.exists() && !bak.exists()) {
         try {
            this.dir.mkdirs();
            File shared = RiptideMacroManager.sharedLibraryFile();
            if (shared.exists()) {
               Files.copy(shared.toPath(), comp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
         } catch (Exception var5) {
            riptide.RiptideClientAddon.LOG.warn("Could not seed profile macro library", var5);
         }
      }
   }

   private void switchMacroLibraryFor(RiptideProfile p) {
      if (p.ownMacroLibrary) {
         this.seedCompanionIfAbsent(p);
         RiptideMacroManager.get().switchBackingFile(this.companionFile(p), false, false);
      } else {
         RiptideMacroManager.get().resetToSharedLibrary();
      }
   }

   private File profileFile(RiptideProfile p) {
      return new File(this.dir, p.id + ".json");
   }

   private String profileWriteKey(String id) {
      return "profile:" + new File(this.dir, id + ".json").getAbsolutePath();
   }

   private void loadAll() {
      this.profiles.clear();
      if (this.dir.exists()) {
         File[] files = this.dir.listFiles((d, n) -> n.endsWith(".json") && !n.endsWith(".tmp"));
         if (files != null) {
            for (File f : files) {
               RiptideProfile p = this.readProfile(f);
               if (p == null) {
                  this.setAsideCorrupt(f);
                  File bak = new File(this.dir, f.getName() + ".bak");
                  if (bak.exists()) {
                     p = this.readProfile(bak);
                  }
               }

               if (p != null && !p.id.isBlank() && this.byId(p.id) == null) {
                  this.profiles.add(p);
               }
            }
         }
      }
   }

   private RiptideProfile readProfile(File f) {
      try {
         RiptideProfile var4;
         try (FileReader r = new FileReader(f)) {
            RiptideProfile p = (RiptideProfile)this.gson.fromJson(r, RiptideProfile.class);
            if (p == null) {
               return null;
            }

            p.normalize();
            var4 = p;
         }

         return var4;
      } catch (Throwable var7) {
         riptide.RiptideClientAddon.LOG.error("Failed to read profile {}", f.getName(), var7);
         return null;
      }
   }

   private void setAsideCorrupt(File f) {
      try {
         File aside = new File(this.dir, f.getName() + ".corrupt-" + System.currentTimeMillis());
         Files.move(f.toPath(), aside.toPath(), StandardCopyOption.REPLACE_EXISTING);
         riptide.RiptideClientAddon.LOG.error("Profile {} was corrupt; moved it to {}", f.getName(), aside.getName());
      } catch (Throwable var3) {
         riptide.RiptideClientAddon.LOG.error("Failed to set aside corrupt profile {}", f.getName(), var3);
      }
   }

   private void writeProfile(RiptideProfile p) {
      this.writeProfile(p, null);
   }

   private void writeProfile(RiptideProfile p, RiptideConfig detachedSnapshot) {
      if (p != null) {
         RiptideProfile snapshot = copyProfile(p, detachedSnapshot);
         SaveCoordinator.enqueueLatest(this.profileWriteKey(snapshot.id), () -> this.writeProfileSnapshot(snapshot));
      }
   }

   private void writeProfileSnapshot(RiptideProfile p) {
      this.dir.mkdirs();
      File target = this.profileFile(p);
      File tmp = new File(this.dir, p.id + ".json.tmp");
      File bak = new File(this.dir, p.id + ".json.bak");

      try (FileWriter w = new FileWriter(tmp)) {
         this.gson.toJson(p, w);
      } catch (Throwable var13) {
         riptide.RiptideClientAddon.LOG.error("Failed to write profile {}", p.id, var13);
         tmp.delete();
         return;
      }

      try {
         if (target.exists()) {
            Files.copy(target.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (Throwable var11) {
         riptide.RiptideClientAddon.LOG.warn("Failed to back up profile {} before save", p.id, var11);
      }

      try {
         Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (Throwable var10) {
         try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
         } catch (Throwable var9) {
            riptide.RiptideClientAddon.LOG.error("Failed to swap in profile {}", p.id, var9);
         }
      }
   }

   private static RiptideProfile copyProfile(RiptideProfile source, RiptideConfig detachedSnapshot) {
      RiptideProfile copy = new RiptideProfile();
      copy.id = source.id == null ? "" : source.id;
      copy.displayName = source.displayName == null ? "" : source.displayName;
      copy.autoSave = source.autoSave;
      copy.ownMacroLibrary = source.ownMacroLibrary;
      copy.ownThemeColor = source.ownThemeColor;
      copy.serverPatterns = source.serverPatterns == null ? new ArrayList<>() : new ArrayList<>(source.serverPatterns);
      copy.createdAt = source.createdAt;
      copy.updatedAt = source.updatedAt;
      copy.schemaVersion = source.schemaVersion;
      copy.snapshot = detachedSnapshot != null ? detachedSnapshot : (source.snapshot == null ? new RiptideConfig() : source.snapshot.deepCopy());
      return copy;
   }

   private void deleteProfileFiles(RiptideProfile profile) {
      if (profile != null) {
         String id = profile.id;
         SaveCoordinator.enqueueLatest(this.profileWriteKey(id), () -> {
            this.deleteFile(new File(this.dir, id + ".json"));
            this.deleteFile(new File(this.dir, id + ".json.bak"));
            this.deleteFile(new File(this.dir, id + ".json.tmp"));
         });
      }
   }

   private void deleteFile(File f) {
      try {
         if (f != null && f.exists()) {
            Files.deleteIfExists(f.toPath());
         }
      } catch (Throwable var3) {
      }
   }

   private void ensureDefault() {
      if (this.profiles.isEmpty()) {
         RiptideProfile def = new RiptideProfile("default", "Default");
         def.autoSave = true;
         def.createdAt = def.updatedAt = System.currentTimeMillis();
         def.snapshot = this.snapshotLiveConfig();
         this.profiles.add(def);
         this.activeId = def.id;
         this.writeProfile(def);
      } else if (this.byId("default") == null) {
         RiptideConfig stock = new RiptideConfig();
         stock.applyRuntimeDefaults();
         RiptideProfile def = new RiptideProfile("default", "Default");
         def.autoSave = true;
         def.createdAt = def.updatedAt = System.currentTimeMillis();
         def.snapshot = stock.snapshotForProfile();
         this.profiles.add(def);
         this.writeProfile(def);
      }
   }

   private void dedupeNames() {
      Set<String> taken = new HashSet<>();
      RiptideProfile def = this.byId("default");
      if (def != null && def.displayName != null) {
         taken.add(def.displayName.strip().toLowerCase(Locale.ROOT));
      }

      for (RiptideProfile p : this.profiles) {
         if (p != null && p != def) {
            String base = p.displayName == null ? "" : p.displayName.strip();
            if (base.isEmpty()) {
               base = "Profile";
            }

            if (!taken.add(base.toLowerCase(Locale.ROOT))) {
               int n = 2;

               String candidate;
               do {
                  candidate = base + " (" + n++ + ")";
               } while (!taken.add(candidate.toLowerCase(Locale.ROOT)));

               p.displayName = candidate;
               p.updatedAt = System.currentTimeMillis();
               this.writeProfile(p);
            }
         }
      }
   }

   private void adoptActive() {
      String want = RiptideConfig.getGlobal().activeProfileId;
      if (want != null && !want.isBlank() && this.existsId(want)) {
         this.activeId = want;
      } else if (this.activeId == null || this.activeId.isBlank() || !this.existsId(this.activeId)) {
         this.activeId = this.profiles.isEmpty() ? "" : this.profiles.get(0).id;
         RiptideConfig.getGlobal().activeProfileId = this.activeId;
      }

      RiptideProfile a = this.active();
      if (a != null && !a.autoSave) {
         try {
            String live = this.gson.toJson(this.snapshotLiveConfig());
            String saved = this.gson.toJson(a.snapshot);
            if (!live.equals(saved)) {
               this.dirty = true;
            }
         } catch (Throwable var5) {
         }
      }
   }

   private String slug(String name) {
      String base = RiptideProfile.sanitizeId(name);
      String candidate = base;
      int n = 2;

      while (this.existsId(candidate)) {
         candidate = base + "-" + n++;
      }

      return candidate;
   }
}
