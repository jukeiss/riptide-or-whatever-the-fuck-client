package riptide.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;

final class ServerPluginScanCache {
   static final int MAX_ENTRIES = 128;
   static final long MAX_BYTES = 8388608L;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Logger LOG = LogUtils.getLogger();
   private static volatile ServerPluginScanCache instance;
   private final File file;
   private final String persistenceKey;
   private final Object persistenceLock = new Object();
   private final LinkedHashMap<String, RiptideConfig.PluginScanCacheEntry> entries = new LinkedHashMap<>();
   private final LinkedHashMap<String, Long> serializedSizes = new LinkedHashMap<>();
   private volatile Map<String, RiptideConfig.PluginScanCacheEntry> publishedView = Map.of();
   private volatile Map<String, RiptideConfig.PluginScanCacheEntry> pendingLegacyFallback = Map.of();
   private long stateRevision;
   private long persistedRevision = -1L;
   private long pendingLegacyRevision = Long.MAX_VALUE;

   private ServerPluginScanCache(File file) {
      this.file = file;
      this.persistenceKey = "plugin-scans:" + file.getAbsolutePath();
      this.load();
   }

   static ServerPluginScanCache get() {
      ServerPluginScanCache current = instance;
      if (current != null) {
         return current;
      } else {
         synchronized (ServerPluginScanCache.class) {
            if (instance == null) {
               instance = new ServerPluginScanCache(new File(riptide.RiptideClientAddon.FOLDER, "server-plugin-scans.json"));
            }

            return instance;
         }
      }
   }

   static ServerPluginScanCache forFile(File file) {
      return new ServerPluginScanCache(file);
   }

   Map<String, RiptideConfig.PluginScanCacheEntry> sharedView() {
      return this.publishedView;
   }

   Map<String, RiptideConfig.PluginScanCacheEntry> pendingLegacyFallback() {
      return this.pendingLegacyFallback;
   }

   synchronized RiptideConfig.PluginScanCacheEntry get(String key) {
      return copyEntry(this.entries.get(key));
   }

   synchronized RiptideConfig.PluginScanCacheEntry newestForAddress(String normalizedAddress) {
      if (normalizedAddress != null && !normalizedAddress.isBlank()) {
         RiptideConfig.PluginScanCacheEntry newest = null;

         for (Entry<String, RiptideConfig.PluginScanCacheEntry> item : this.entries.entrySet()) {
            String key = item.getKey();
            if ((key.equals(normalizedAddress) || key.startsWith(normalizedAddress + "|"))
               && (newest == null || item.getValue().scannedAtMs > newest.scannedAtMs)) {
               newest = item.getValue();
            }
         }

         return copyEntry(newest);
      } else {
         return null;
      }
   }

   synchronized int size() {
      return this.entries.size();
   }

   synchronized long revision() {
      return this.stateRevision;
   }

   synchronized Map<String, RiptideConfig.PluginScanCacheEntry> snapshot() {
      return copyEntries(this.entries);
   }

   void put(String key, RiptideConfig.PluginScanCacheEntry entry) {
      if (key != null && !key.isBlank() && entry != null) {
         RiptideConfig.PluginScanCacheEntry copied = copyEntry(entry);
         long serializedSize = serializedBytes(key, copied);
         synchronized (this) {
            this.entries.put(key, copied);
            this.serializedSizes.put(key, serializedSize);
            this.trimToBounds();
            this.stateRevision++;
            this.publishAndEnqueueWrite();
         }
      }
   }

   synchronized void remove(String key) {
      if (key != null && this.entries.remove(key) != null) {
         this.serializedSizes.remove(key);
         this.stateRevision++;
         this.publishAndEnqueueWrite();
      }
   }

   synchronized void removeAddress(String normalizedAddress) {
      if (normalizedAddress != null) {
         boolean changed = this.entries.keySet().removeIf(key -> key.equals(normalizedAddress) || key.startsWith(normalizedAddress + "|"));
         if (changed) {
            this.serializedSizes.keySet().retainAll(this.entries.keySet());
            this.stateRevision++;
            this.publishAndEnqueueWrite();
         }
      }
   }

   boolean mergeLegacy(Map<String, RiptideConfig.PluginScanCacheEntry> legacy) {
      if (legacy != null && !legacy.isEmpty()) {
         LinkedHashMap<String, RiptideConfig.PluginScanCacheEntry> candidates = new LinkedHashMap<>();
         LinkedHashMap<String, Long> candidateSizes = new LinkedHashMap<>();

         for (Entry<String, RiptideConfig.PluginScanCacheEntry> item : legacy.entrySet()) {
            if (item.getKey() != null && !item.getKey().isBlank() && item.getValue() != null) {
               RiptideConfig.PluginScanCacheEntry copied = copyEntry(item.getValue());
               candidates.put(item.getKey(), copied);
               candidateSizes.put(item.getKey(), serializedBytes(item.getKey(), copied));
            }
         }

         if (candidates.isEmpty()) {
            return false;
         } else {
            Map<String, RiptideConfig.PluginScanCacheEntry> snapshot;
            long revision;
            synchronized (this) {
               this.pendingLegacyFallback = Collections.unmodifiableMap(copyEntries(candidates));
               boolean changed = false;

               for (Entry<String, RiptideConfig.PluginScanCacheEntry> itemx : candidates.entrySet()) {
                  RiptideConfig.PluginScanCacheEntry old = this.entries.get(itemx.getKey());
                  if (old == null || itemx.getValue().scannedAtMs > old.scannedAtMs) {
                     this.entries.put(itemx.getKey(), itemx.getValue());
                     this.serializedSizes.put(itemx.getKey(), candidateSizes.get(itemx.getKey()));
                     changed = true;
                  }
               }

               this.trimToBounds();
               if (changed) {
                  this.stateRevision++;
               }

               this.pendingLegacyRevision = this.stateRevision;
               snapshot = this.publishSnapshot();
               revision = this.stateRevision;
            }

            return this.writeSnapshot(snapshot, revision);
         }
      } else {
         return false;
      }
   }

   private void publishAndEnqueueWrite() {
      Map<String, RiptideConfig.PluginScanCacheEntry> snapshot = this.publishSnapshot();
      long revision = this.stateRevision;
      SaveCoordinator.enqueueLatest(this.persistenceKey, () -> this.writeSnapshot(snapshot, revision));
   }

   private Map<String, RiptideConfig.PluginScanCacheEntry> publishSnapshot() {
      Map<String, RiptideConfig.PluginScanCacheEntry> snapshot = copyEntries(this.entries);
      this.publishedView = Collections.unmodifiableMap(snapshot);
      return snapshot;
   }

   private void trimToBounds() {
      if (!this.entries.isEmpty()) {
         List<Entry<String, RiptideConfig.PluginScanCacheEntry>> newest = new ArrayList<>(this.entries.entrySet());
         newest.sort(
            Comparator.<Entry<String, RiptideConfig.PluginScanCacheEntry>>comparingLong(entry -> entry.getValue() == null ? 0L : entry.getValue().scannedAtMs)
               .reversed()
         );
         LinkedHashMap<String, RiptideConfig.PluginScanCacheEntry> retained = new LinkedHashMap<>();
         long bytes = 32L;

         for (Entry<String, RiptideConfig.PluginScanCacheEntry> item : newest) {
            if (retained.size() >= 128) {
               break;
            }

            Long knownSize = this.serializedSizes.get(item.getKey());
            long entryBytes = knownSize == null ? 8388608L : knownSize;
            if (!retained.isEmpty() && bytes + entryBytes > 8388608L) {
               break;
            }

            retained.put(item.getKey(), item.getValue());
            bytes += entryBytes;
         }

         this.entries.clear();
         this.entries.putAll(retained);
         this.serializedSizes.keySet().retainAll(retained.keySet());
      }
   }

   private static long serializedBytes(String key, RiptideConfig.PluginScanCacheEntry entry) {
      return GSON.toJson(Map.of(key, entry)).getBytes(StandardCharsets.UTF_8).length;
   }

   private void load() {
      ServerPluginScanCache.CacheFile cache = this.read(this.file);
      if (cache == null) {
         File backup = this.backupFile();
         cache = this.read(backup);
      }

      if (cache != null && cache.entries != null) {
         this.entries.putAll(copyEntries(cache.entries));

         for (Entry<String, RiptideConfig.PluginScanCacheEntry> item : this.entries.entrySet()) {
            this.serializedSizes.put(item.getKey(), serializedBytes(item.getKey(), item.getValue()));
         }

         this.trimToBounds();
         if (!this.entries.isEmpty()) {
            this.stateRevision++;
         }

         this.publishSnapshot();
      }
   }

   private ServerPluginScanCache.CacheFile read(File source) {
      if (source != null && source.exists()) {
         try {
            ServerPluginScanCache.CacheFile var4;
            try (FileReader reader = new FileReader(source)) {
               ServerPluginScanCache.CacheFile loaded = (ServerPluginScanCache.CacheFile)GSON.fromJson(reader, ServerPluginScanCache.CacheFile.class);
               var4 = loaded == null ? new ServerPluginScanCache.CacheFile() : loaded;
            }

            return var4;
         } catch (Throwable var7) {
            LOG.warn("Failed to read server plugin cache {}", source.getName(), var7);
            return null;
         }
      } else {
         return null;
      }
   }

   private boolean writeSnapshot(Map<String, RiptideConfig.PluginScanCacheEntry> snapshot, long revision) {
      synchronized (this) {
         if (revision < this.persistedRevision) {
            return true;
         }
      }

      ServerPluginScanCache.CacheFile value = new ServerPluginScanCache.CacheFile();
      value.entries = snapshot;

      String json;
      try {
         json = GSON.toJson(value);
      } catch (Throwable var17) {
         LOG.error("Failed to serialize server plugin cache", var17);
         return false;
      }

      synchronized (this.persistenceLock) {
         synchronized (this) {
            if (revision < this.persistedRevision) {
               return true;
            }
         }

         File parent = this.file.getAbsoluteFile().getParentFile();
         if (parent != null && (parent.isDirectory() || parent.mkdirs())) {
            File tmp = new File(parent, this.file.getName() + ".tmp");

            try (FileWriter writer = new FileWriter(tmp)) {
               writer.write(json);
            } catch (Throwable var16) {
               LOG.error("Failed to write server plugin cache", var16);
               tmp.delete();
               return false;
            }

            try {
               if (this.file.exists()) {
                  Files.copy(this.file.toPath(), this.backupFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
               }
            } catch (Throwable var14) {
               LOG.warn("Failed to back up server plugin cache", var14);
            }

            boolean persisted = atomicReplace(tmp, this.file, "server plugin cache") && this.file.isFile();
            if (persisted) {
               this.markPersisted(revision);
            }

            return persisted;
         } else {
            LOG.error("Failed to create server plugin cache directory {}", parent);
            return false;
         }
      }
   }

   private synchronized void markPersisted(long revision) {
      this.persistedRevision = Math.max(this.persistedRevision, revision);
      if (!this.pendingLegacyFallback.isEmpty() && revision >= this.pendingLegacyRevision) {
         this.pendingLegacyFallback = Map.of();
         this.pendingLegacyRevision = Long.MAX_VALUE;
      }
   }

   private File backupFile() {
      return new File(this.file.getParentFile(), this.file.getName() + ".bak");
   }

   private static boolean atomicReplace(File tmp, File target, String description) {
      try {
         Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         return true;
      } catch (Throwable var6) {
         try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
         } catch (Throwable var5) {
            LOG.error("Failed to swap in {}", description, var5);
            tmp.delete();
            return false;
         }
      }
   }

   private static LinkedHashMap<String, RiptideConfig.PluginScanCacheEntry> copyEntries(Map<String, RiptideConfig.PluginScanCacheEntry> source) {
      LinkedHashMap<String, RiptideConfig.PluginScanCacheEntry> copy = new LinkedHashMap<>();
      if (source == null) {
         return copy;
      } else {
         for (Entry<String, RiptideConfig.PluginScanCacheEntry> item : source.entrySet()) {
            if (item.getKey() != null && item.getValue() != null) {
               copy.put(item.getKey(), copyEntry(item.getValue()));
            }
         }

         return copy;
      }
   }

   private static RiptideConfig.PluginScanCacheEntry copyEntry(RiptideConfig.PluginScanCacheEntry source) {
      if (source == null) {
         return null;
      } else {
         RiptideConfig.PluginScanCacheEntry copy = new RiptideConfig.PluginScanCacheEntry();
         copy.contextSignature = source.contextSignature == null ? "" : source.contextSignature;
         copy.serverName = source.serverName == null ? "" : source.serverName;
         copy.serverAddress = source.serverAddress == null ? "" : source.serverAddress;
         copy.plugins = source.plugins == null ? new ArrayList<>() : new ArrayList<>(source.plugins);
         copy.commands = copyListMap(source.commands);
         copy.evidence = source.evidence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.evidence);
         copy.channels = copyListMap(source.channels);
         copy.guis = copyListMap(source.guis);
         copy.confidence = source.confidence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.confidence);
         copy.copyEvidence = source.copyEvidence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.copyEvidence);
         copy.copyCommands = copyListMap(source.copyCommands);
         copy.scanStatus = source.scanStatus == null ? "COMPLETE" : source.scanStatus;
         copy.totalProbes = source.totalProbes;
         copy.answeredProbes = source.answeredProbes;
         copy.retriedProbes = source.retriedProbes;
         copy.failedProbes = source.failedProbes;
         copy.scannedAtMs = source.scannedAtMs;
         return copy;
      }
   }

   private static LinkedHashMap<String, List<String>> copyListMap(Map<String, List<String>> source) {
      LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
      if (source == null) {
         return copy;
      } else {
         for (Entry<String, List<String>> item : source.entrySet()) {
            copy.put(item.getKey(), item.getValue() == null ? new ArrayList<>() : new ArrayList<>(item.getValue()));
         }

         return copy;
      }
   }

   private static final class CacheFile {
      int version = 1;
      Map<String, RiptideConfig.PluginScanCacheEntry> entries = new LinkedHashMap<>();
   }
}
