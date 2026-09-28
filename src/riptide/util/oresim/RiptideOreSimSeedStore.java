package riptide.util.oresim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RiptideOreSimSeedStore {
   private static final int FORMAT_VERSION = 1;
   private static final int MAX_SCOPES = 2048;
   private static final long SAVE_DEBOUNCE_MS = 400L;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Type LEGACY_MAP_TYPE = (new TypeToken<Map<String, String>>() {}).getType();
   private static final ScheduledExecutorService WRITER = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread thread = new Thread(r, "ore-sim-seeds-writer");
      thread.setDaemon(true);
      return thread;
   });
   private static volatile RiptideOreSimSeedStore instance;
   private final File file;
   private final File temporaryFile;
   private final Map<String, String> seedsByScope = new LinkedHashMap<>();
   private final AtomicBoolean dirty = new AtomicBoolean(false);
   private final AtomicBoolean scheduled = new AtomicBoolean(false);
   private boolean legacyMigrationComplete;

   private RiptideOreSimSeedStore(File file) {
      this.file = file;
      this.temporaryFile = new File(file.getParentFile(), file.getName() + ".tmp");
   }

   public static RiptideOreSimSeedStore get() {
      RiptideOreSimSeedStore local = instance;
      if (local == null) {
         synchronized (RiptideOreSimSeedStore.class) {
            if (instance == null) {
               instance = load(new File(riptide.RiptideClientAddon.FOLDER, "ore-sim-seeds.json"));
               Runtime.getRuntime().addShutdownHook(new Thread(instance::flush, "ore-sim-seeds-flush"));
            }

            local = instance;
         }
      }

      return local;
   }

   public String value(String scope, String legacyGlobalValue) {
      String key = knownScope(scope);
      if (key == null) {
         return "";
      } else {
         boolean changed = false;
         String result;
         synchronized (this.seedsByScope) {
            if (!this.legacyMigrationComplete) {
               this.legacyMigrationComplete = true;
               if (!this.seedsByScope.containsKey(key) && legacyGlobalValue != null && !legacyGlobalValue.isBlank()) {
                  this.seedsByScope.put(key, legacyGlobalValue);
               }

               changed = true;
            }

            result = this.seedsByScope.getOrDefault(key, "");
         }

         if (changed) {
            this.touch();
         }

         return result;
      }
   }

   public void put(String scope, String rawValue) {
      String key = knownScope(scope);
      if (key != null) {
         String value = rawValue == null ? "" : rawValue;
         boolean changed;
         synchronized (this.seedsByScope) {
            changed = !this.legacyMigrationComplete;
            this.legacyMigrationComplete = true;
            if (value.isBlank()) {
               changed |= this.seedsByScope.remove(key) != null;
            } else if (this.seedsByScope.containsKey(key) || this.seedsByScope.size() < 2048) {
               changed |= !Objects.equals(this.seedsByScope.put(key, value), value);
            }
         }

         if (changed) {
            this.touch();
         }
      }
   }

   private static String knownScope(String scope) {
      if (scope != null && !scope.isBlank()) {
         String normalized = scope.trim();
         return "unknown".equals(normalized) ? null : normalized;
      } else {
         return null;
      }
   }

   private void touch() {
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
         File directory = this.file.getParentFile();
         if (directory != null) {
            Files.createDirectories(directory.toPath());
         }

         RiptideOreSimSeedStore.Persisted snapshot = new RiptideOreSimSeedStore.Persisted();
         snapshot.version = 1;
         synchronized (this.seedsByScope) {
            snapshot.legacyMigrationComplete = this.legacyMigrationComplete;
            snapshot.seeds = new LinkedHashMap<>(this.seedsByScope);
         }

         try (Writer writer = Files.newBufferedWriter(this.temporaryFile.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(snapshot, writer);
         }

         try {
            Files.move(this.temporaryFile.toPath(), this.file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException var7) {
            Files.move(this.temporaryFile.toPath(), this.file.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (Throwable var10) {
         this.dirty.set(true);
         riptide.RiptideClientAddon.LOG.warn("Failed to save scoped OreSim seeds", var10);
      }
   }

   private static RiptideOreSimSeedStore load(File file) {
      RiptideOreSimSeedStore store = new RiptideOreSimSeedStore(file);
      if (!file.exists()) {
         return store;
      } else {
         try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root instanceof JsonObject object && object.has("seeds")) {
               RiptideOreSimSeedStore.Persisted parsed = (RiptideOreSimSeedStore.Persisted)GSON.fromJson(object, RiptideOreSimSeedStore.Persisted.class);
               if (parsed != null) {
                  store.legacyMigrationComplete = parsed.legacyMigrationComplete;
                  store.copyValidEntries(parsed.seeds);
               }
            } else {
               Map<String, String> legacyMap = (Map<String, String>)GSON.fromJson(root, LEGACY_MAP_TYPE);
               store.copyValidEntries(legacyMap);
               store.legacyMigrationComplete = true;
            }
         } catch (Throwable var8) {
            riptide.RiptideClientAddon.LOG.warn("Failed to read scoped OreSim seeds", var8);
         }

         return store;
      }
   }

   private void copyValidEntries(Map<String, String> entries) {
      if (entries != null) {
         for (Entry<String, String> entry : entries.entrySet()) {
            String key = knownScope(entry.getKey());
            String value = entry.getValue();
            if (key != null && value != null && !value.isBlank()) {
               this.seedsByScope.put(key, value);
               if (this.seedsByScope.size() >= 2048) {
                  break;
               }
            }
         }
      }
   }

   static RiptideOreSimSeedStore loadForTest(File file) {
      return load(file);
   }

   void flushForTest() {
      this.flush();
   }

   private static final class Persisted {
      int version = 1;
      boolean legacyMigrationComplete;
      Map<String, String> seeds = new LinkedHashMap<>();
   }
}
