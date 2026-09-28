package riptide.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import riptide.util.multi.MultiProxyVerifier;

public final class RiptideProxyManager extends PersistentNbtManager<RiptideProxy> implements Iterable<RiptideProxy> {
   private static final RiptideProxyManager INSTANCE = new RiptideProxyManager();
   private final AtomicBoolean refreshing = new AtomicBoolean(false);
   private final AtomicBoolean refreshCancelRequested = new AtomicBoolean(false);
   private final AtomicInteger refreshChecked = new AtomicInteger();
   private final AtomicInteger refreshTotal = new AtomicInteger();
   private final AtomicBoolean importing = new AtomicBoolean(false);
   private final AtomicBoolean importCancelRequested = new AtomicBoolean(false);
   private final AtomicInteger importLinesRead = new AtomicInteger();
   private final AtomicInteger importCandidates = new AtomicInteger();
   private final AtomicInteger importAdded = new AtomicInteger();
   private final AtomicBoolean saveWorkerRunning = new AtomicBoolean(false);
   private final AtomicBoolean geoLookupRunning = new AtomicBoolean(false);
   private final AtomicBoolean geoLookupRequestedAgain = new AtomicBoolean(false);
   private int timeoutMs = 3000;
   private int threads = 64;
   private int retries = 0;
   private boolean sortByLatency = true;
   private boolean pruneDead = true;
   private int pruneLatency = 2000;
   private int pruneToCount = 0;
   private volatile long refreshGeneration;
   private volatile long refreshRevision;
   private volatile long refreshCancelNoticeUntilMs;
   private volatile long importGeneration;
   private volatile long importRevision;
   private volatile long importCancelNoticeUntilMs;
   private volatile long geoGeneration;
   private volatile long canceledImportGeneration = Long.MIN_VALUE;
   private volatile long listRevision;
   private volatile long lastRefreshRevisionMs;
   private volatile long lastImportRevisionMs;
   private volatile boolean saveRequested;
   private volatile ExecutorService refreshExecutor;
   private volatile Thread importThread;
   private volatile List<RiptideProxy> activeRefreshSnapshot = List.of();
   private static final Pattern PROXY_PATTERN = Pattern.compile(
      "^(?:([\\w\\s]+)=)?((?:0*(?:\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])(?:\\.(?!:)|)){4}):(?!0)(\\d{1,4}|[1-5]\\d{4}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5])(?i:@(socks[45]))?$",
      8
   );
   private static final Pattern PROXY_PATTERN_WEBSHARE = Pattern.compile(
      "^((?:0*(?:\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])(?:\\.(?!:)|)){4}):(?!0)(\\d{1,4}|[1-5]\\d{4}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5]):([^:]+)(?::(.+))?$",
      8
   );
   private static final Pattern PROXY_PATTERN_URI = Pattern.compile(
      "^(?:(?<type>socks|socks4|socks5)://)?(?:(?<user>[\\w~-]+)(:(?<pass>[\\w~-]+))?@)?(?<addr>(?:0*(?:\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])(?:\\.(?!:)|)){4}):(?!0)(?<port>\\d{1,4}|[1-5]\\d{4}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5])$",
      8
   );
   private static final int GEO_BATCH_SIZE = 100;

   private RiptideProxyManager() {
   }

   public static RiptideProxyManager get() {
      INSTANCE.ensureLoaded();
      RiptideMeteorImport.ensureImported();
      INSTANCE.ensureStableIds();
      return INSTANCE;
   }

   private synchronized void ensureStableIds() {
      boolean changed = false;

      for (RiptideProxy proxy : this.items) {
         if (proxy != null) {
            proxy.stableId();
            changed |= proxy.generatedStableId;
            proxy.generatedStableId = false;
         }
      }

      if (changed) {
         this.save();
      }
   }

   public synchronized RiptideProxy findById(String id) {
      if (id != null && !id.isBlank()) {
         for (RiptideProxy proxy : this.items) {
            if (id.equals(proxy.stableId())) {
               return proxy;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   @Override
   protected File saveFile() {
      return new File(Minecraft.getInstance().gameDirectory, "riptide-proxies.nbt");
   }

   @Override
   protected String listKey() {
      return "proxies";
   }

   protected RiptideProxy fromTag(CompoundTag tag) {
      return new RiptideProxy().fromTag(tag);
   }

   protected CompoundTag toTag(RiptideProxy item) {
      return item.toTag();
   }

   @Override
   protected String describe() {
      return "Riptide proxies";
   }

   @Override
   protected void readExtra(CompoundTag tag) {
      this.timeoutMs = tag.getIntOr("timeoutMs", this.timeoutMs);
      this.threads = tag.getIntOr("threads", this.threads);
      this.retries = tag.getIntOr("retries", this.retries);
      this.sortByLatency = tag.getBooleanOr("sortByLatency", this.sortByLatency);
      this.pruneDead = tag.getBooleanOr("pruneDead", this.pruneDead);
      this.pruneLatency = tag.getIntOr("pruneLatency", this.pruneLatency);
      this.pruneToCount = tag.getIntOr("pruneToCount", this.pruneToCount);
   }

   @Override
   protected void writeExtra(CompoundTag tag) {
      tag.putInt("timeoutMs", this.timeoutMs);
      tag.putInt("threads", this.threads);
      tag.putInt("retries", this.retries);
      tag.putBoolean("sortByLatency", this.sortByLatency);
      tag.putBoolean("pruneDead", this.pruneDead);
      tag.putInt("pruneLatency", this.pruneLatency);
      tag.putInt("pruneToCount", this.pruneToCount);
   }

   private void scheduleSave() {
      this.saveRequested = true;
      if (this.saveWorkerRunning.compareAndSet(false, true)) {
         Thread thread = new Thread(() -> {
            try {
               while (true) {
                  this.saveRequested = false;
                  this.save();
                  if (this.saveRequested) {
                     continue;
                  }
               }
            } finally {
               this.saveWorkerRunning.set(false);
               if (this.saveRequested) {
                  this.scheduleSave();
               }
            }
         }, "Riptide-Proxy-Save");
         thread.setDaemon(true);
         thread.start();
      }
   }

   public synchronized int enabledCount() {
      int count = 0;

      for (RiptideProxy proxy : this.items) {
         if (proxy.enabled && proxy.isValid()) {
            count++;
         }
      }

      return count;
   }

   public long listRevision() {
      return this.listRevision;
   }

   public synchronized boolean add(RiptideProxy proxy) {
      if (proxy != null && proxy.isValid() && !this.items.contains(proxy)) {
         if (this.items.isEmpty()) {
            proxy.enabled = true;
         }

         this.items.add(proxy);
         this.bumpListRevision();
         this.scheduleSave();
         this.requestGeoLookup(false);
         return true;
      } else {
         return false;
      }
   }

   public synchronized void remove(RiptideProxy proxy) {
      if (this.items.remove(proxy)) {
         this.bumpListRevision();
         this.scheduleSave();
      }
   }

   public synchronized int clearAll() {
      if (!this.refreshing.get() && !this.importing.get() && !this.items.isEmpty()) {
         int removed = this.items.size();
         this.items.clear();
         this.bumpListRevision();
         this.scheduleSave();
         return removed;
      } else {
         return 0;
      }
   }

   public synchronized boolean update(RiptideProxy existing, RiptideProxy updated) {
      if (existing != null && updated != null && updated.isValid()) {
         int index = -1;

         for (int i = 0; i < this.items.size(); i++) {
            RiptideProxy proxy = this.items.get(i);
            if (proxy == existing) {
               index = i;
               break;
            }
         }

         if (index < 0) {
            return false;
         } else {
            for (int ix = 0; ix < this.items.size(); ix++) {
               if (ix != index && this.items.get(ix).equals(updated)) {
                  return false;
               }
            }

            boolean identityChanged = existing.type != updated.type || existing.port != updated.port || !Objects.equals(existing.address, updated.address);
            existing.name = updated.name;
            existing.type = updated.type;
            existing.address = updated.address;
            existing.port = updated.port;
            existing.username = updated.username;
            existing.password = updated.password;
            if (identityChanged) {
               existing.status = RiptideProxy.Status.UNCHECKED;
               existing.latency = 0L;
               existing.clearGeo();
            }

            this.bumpListRevision();
            this.scheduleSave();
            if (identityChanged) {
               this.requestGeoLookup(false);
            }

            return true;
         }
      } else {
         return false;
      }
   }

   public synchronized void setEnabled(RiptideProxy proxy, boolean enabled) {
      for (RiptideProxy current : this.items) {
         current.enabled = false;
      }

      if (proxy != null) {
         proxy.enabled = enabled;
      }

      this.bumpListRevision();
      this.scheduleSave();
   }

   public synchronized RiptideProxy getEnabled() {
      for (RiptideProxy proxy : this.items) {
         if (proxy.enabled && proxy.isValid()) {
            return proxy;
         }
      }

      return null;
   }

   public boolean isRefreshing() {
      return this.refreshing.get();
   }

   public RiptideProxyManager.RefreshStatus refreshStatus() {
      boolean running = this.refreshing.get();
      boolean canceling = !running && System.currentTimeMillis() < this.refreshCancelNoticeUntilMs;
      return new RiptideProxyManager.RefreshStatus(
         running, canceling, this.refreshChecked.get(), this.refreshTotal.get(), this.refreshGeneration, this.refreshRevision
      );
   }

   public RiptideProxyManager.ImportStatus importStatus() {
      boolean running = this.importing.get();
      boolean canceling = !running && System.currentTimeMillis() < this.importCancelNoticeUntilMs;
      boolean canceled = !running && this.importGeneration == this.canceledImportGeneration;
      return new RiptideProxyManager.ImportStatus(
         running,
         canceling,
         this.importLinesRead.get(),
         this.importCandidates.get(),
         this.importAdded.get(),
         this.importGeneration,
         this.importRevision,
         canceled
      );
   }

   public int getTimeoutMs() {
      return this.timeoutMs;
   }

   public void setTimeoutMs(int v) {
      this.timeoutMs = Math.max(1, v);
      this.scheduleSave();
   }

   public int getThreads() {
      return this.threads;
   }

   public void setThreads(int v) {
      this.threads = Math.max(1, v);
      this.scheduleSave();
   }

   public int getRetries() {
      return this.retries;
   }

   public void setRetries(int v) {
      this.retries = Math.max(0, v);
      this.scheduleSave();
   }

   public boolean isSortByLatency() {
      return this.sortByLatency;
   }

   public synchronized void setSortByLatency(boolean v) {
      this.sortByLatency = v;
      if (v) {
         this.sortByLatencyInternal();
      }

      this.bumpListRevision();
      this.scheduleSave();
   }

   public boolean isPruneDead() {
      return this.pruneDead;
   }

   public void setPruneDead(boolean v) {
      this.pruneDead = v;
      this.scheduleSave();
   }

   public int getPruneLatency() {
      return this.pruneLatency;
   }

   public void setPruneLatency(int v) {
      this.pruneLatency = Math.max(0, v);
      this.scheduleSave();
   }

   public int getPruneToCount() {
      return this.pruneToCount;
   }

   public void setPruneToCount(int v) {
      this.pruneToCount = Math.max(0, v);
      this.scheduleSave();
   }

   public synchronized boolean sortByLatencyNow() {
      if (!this.refreshing.get() && !this.importing.get() && this.items.size() >= 2) {
         this.sortByLatencyInternal();
         this.bumpListRevision();
         this.scheduleSave();
         return true;
      } else {
         return false;
      }
   }

   public void checkProxies(boolean all) {
      this.startRefresh(all);
   }

   public boolean requestGeoLookup(boolean force) {
      this.ensureLoaded();
      if (!this.geoLookupRunning.compareAndSet(false, true)) {
         this.geoLookupRequestedAgain.set(true);
         return false;
      } else {
         List<RiptideProxyManager.GeoTarget> targets;
         long generation;
         synchronized (this) {
            targets = this.collectGeoTargets(force);
            if (targets.isEmpty()) {
               this.geoLookupRunning.set(false);
               return false;
            }

            generation = this.geoGeneration + 1L;
            this.geoGeneration = generation;
            long now = System.currentTimeMillis();

            for (RiptideProxyManager.GeoTarget target : targets) {
               target.proxy().markGeoLookupPending(now);
            }

            this.bumpListRevision();
         }

         Thread thread = new Thread(() -> this.runGeoLookup(generation, targets), "Riptide-Proxy-Geo");
         thread.setDaemon(true);
         thread.start();
         return true;
      }
   }

   public boolean startRefresh(boolean all) {
      return this.startRefreshInternal(all, "", 0);
   }

   public boolean startRefreshToServer(boolean all, String host, int port) {
      String destination = host == null ? "" : host.trim();
      return !destination.isBlank() && port > 0 && port <= 65535 ? this.startRefreshInternal(all, destination, port) : false;
   }

   private boolean startRefreshInternal(boolean all, String destinationHost, int destinationPort) {
      List<RiptideProxyManager.RefreshTarget> targets = new ArrayList<>();
      List<RiptideProxy> active = new ArrayList<>();
      ExecutorService executor;
      long generation;
      int timeout;
      int retryCount;
      int workerCount;
      synchronized (this) {
         if (this.refreshing.get() || this.importing.get() || this.items.isEmpty()) {
            return false;
         }

         for (RiptideProxy proxy : this.items) {
            if (proxy != null && proxy.isValid() && (all || proxy.status == RiptideProxy.Status.UNCHECKED)) {
               targets.add(new RiptideProxyManager.RefreshTarget(proxy, proxyKey(proxy)));
               active.add(proxy);
            }
         }

         if (targets.isEmpty()) {
            return false;
         }

         generation = this.refreshGeneration + 1L;
         this.refreshGeneration = generation;
         this.refreshCancelRequested.set(false);
         this.refreshCancelNoticeUntilMs = 0L;
         this.refreshChecked.set(0);
         this.refreshTotal.set(targets.size());
         this.activeRefreshSnapshot = List.copyOf(active);

         for (RiptideProxy proxyx : active) {
            proxyx.status = RiptideProxy.Status.CHECKING;
            proxyx.latency = 0L;
         }

         timeout = this.effectiveRefreshTimeout(targets.size());
         retryCount = this.effectiveRefreshRetries(targets.size());
         workerCount = this.effectiveRefreshThreads(targets.size());
         executor = Executors.newFixedThreadPool(workerCount);
         this.refreshExecutor = executor;
         this.refreshing.set(true);
         this.bumpRefreshRevision(true);
      }

      Thread thread = new Thread(
         () -> this.runRefreshJob(generation, executor, targets, timeout, retryCount, workerCount, destinationHost, destinationPort), "Riptide-Proxy-Refresh"
      );
      thread.setDaemon(true);
      thread.start();
      return true;
   }

   public boolean cancelRefresh() {
      ExecutorService executor;
      synchronized (this) {
         if (!this.refreshing.get()) {
            return false;
         }

         this.refreshCancelRequested.set(true);
         this.refreshing.set(false);
         this.refreshCancelNoticeUntilMs = System.currentTimeMillis() + 800L;
         executor = this.refreshExecutor;
         this.refreshExecutor = null;
         List<RiptideProxy> active = this.activeRefreshSnapshot;
         this.activeRefreshSnapshot = List.of();

         for (RiptideProxy proxy : active) {
            if (proxy.status == RiptideProxy.Status.CHECKING) {
               proxy.status = RiptideProxy.Status.UNCHECKED;
               proxy.latency = 0L;
            }
         }

         if (this.sortByLatency) {
            this.sortByLatencyInternal();
            this.bumpListRevision();
            this.scheduleSave();
         }

         this.bumpRefreshRevision(true);
      }

      if (executor != null) {
         executor.shutdownNow();
      }

      return true;
   }

   public boolean startImport(File file) {
      return file != null && file.isFile() ? this.launchImport(() -> new BufferedReader(new FileReader(file))) : false;
   }

   public boolean startImportFromText(String text) {
      return text != null && !text.isBlank() ? this.launchImport(() -> new BufferedReader(new StringReader(text))) : false;
   }

   private boolean launchImport(RiptideProxyManager.ImportSource source) {
      long generation;
      synchronized (this) {
         if (this.importing.get() || this.refreshing.get()) {
            return false;
         }

         generation = this.importGeneration + 1L;
         this.importGeneration = generation;
         this.canceledImportGeneration = Long.MIN_VALUE;
         this.importCancelRequested.set(false);
         this.importCancelNoticeUntilMs = 0L;
         this.importLinesRead.set(0);
         this.importCandidates.set(0);
         this.importAdded.set(0);
         this.importing.set(true);
         this.bumpImportRevision(true);
      }

      Thread thread = new Thread(() -> this.runImportJob(generation, source), "Riptide-Proxy-Import");
      thread.setDaemon(true);
      this.importThread = thread;
      thread.start();
      return true;
   }

   public boolean cancelImport() {
      Thread thread;
      synchronized (this) {
         if (!this.importing.get()) {
            return false;
         }

         this.importCancelRequested.set(true);
         this.importing.set(false);
         this.canceledImportGeneration = this.importGeneration;
         this.importCancelNoticeUntilMs = System.currentTimeMillis() + 800L;
         thread = this.importThread;
         this.importThread = null;
         this.bumpImportRevision(true);
      }

      if (thread != null) {
         thread.interrupt();
      }

      return true;
   }

   private void runImportJob(long generation, RiptideProxyManager.ImportSource source) {
      List<RiptideProxy> parsed = new ArrayList<>();
      Set<String> knownKeys;
      synchronized (this) {
         knownKeys = new HashSet<>(this.items.size() + 1024);

         for (RiptideProxy proxy : this.items) {
            knownKeys.add(proxyKey(proxy));
         }
      }

      try {
         String line;
         try (BufferedReader reader = source.open()) {
            for (; this.isImportCurrent(generation) && (line = reader.readLine()) != null; this.bumpImportRevision(false)) {
               this.importLinesRead.incrementAndGet();
               RiptideProxy proxy = parseProxyLine(line.trim());
               if (proxy != null && proxy.isValid() && knownKeys.add(proxyKey(proxy))) {
                  parsed.add(proxy);
                  this.importCandidates.set(parsed.size());
               }
            }
         } catch (Exception var18) {
            if (this.isImportCurrent(generation)) {
               riptide.RiptideClientAddon.LOG.error("Failed to import proxies", var18);
            }
         }
      } finally {
         this.finishImport(generation, parsed);
      }
   }

   private synchronized void finishImport(long generation, List<RiptideProxy> parsed) {
      if (this.importGeneration == generation && this.importing.get()) {
         int added = 0;
         if (!this.importCancelRequested.get() && parsed != null && !parsed.isEmpty()) {
            Set<String> knownKeys = new HashSet<>(this.items.size() + parsed.size() + 16);

            for (RiptideProxy proxy : this.items) {
               knownKeys.add(proxyKey(proxy));
            }

            for (RiptideProxy proxy : parsed) {
               if (knownKeys.add(proxyKey(proxy))) {
                  proxy.enabled = false;
                  this.items.add(proxy);
                  added++;
               }
            }
         }

         this.importAdded.set(added);
         this.importing.set(false);
         this.importCancelRequested.set(false);
         this.importThread = null;
         if (added > 0) {
            this.bumpListRevision();
            this.scheduleSave();
            this.requestGeoLookup(false);
         }

         this.bumpImportRevision(true);
      }
   }

   private boolean isImportCurrent(long generation) {
      return this.importing.get() && this.importGeneration == generation && !this.importCancelRequested.get();
   }

   private void bumpImportRevision(boolean force) {
      long now = System.currentTimeMillis();
      if (force || now - this.lastImportRevisionMs >= 150L) {
         this.lastImportRevisionMs = now;
         this.importRevision++;
      }
   }

   private void runRefreshJob(
      long generation,
      ExecutorService executor,
      List<RiptideProxyManager.RefreshTarget> targets,
      int timeout,
      int retryCount,
      int workerCount,
      String destinationHost,
      int destinationPort
   ) {
      boolean completed = false;

      try {
         AtomicInteger nextIndex = new AtomicInteger();

         for (int i = 0; i < workerCount && this.isRefreshCurrent(generation); i++) {
            executor.execute(() -> {
               while (this.isRefreshCurrent(generation)) {
                  int index = nextIndex.getAndIncrement();
                  if (index < targets.size()) {
                     this.refreshOne(generation, targets.get(index), timeout, retryCount, destinationHost, destinationPort);
                     continue;
                  }
                  break;
               }
            });
         }

         executor.shutdown();

         while (this.isRefreshCurrent(generation)) {
            if (executor.awaitTermination(100L, TimeUnit.MILLISECONDS)) {
               completed = true;
               return;
            }
         }

         executor.shutdownNow();
      } catch (InterruptedException var18) {
         Thread.currentThread().interrupt();
         executor.shutdownNow();
      } catch (RejectedExecutionException var19) {
         if (this.isRefreshCurrent(generation)) {
            riptide.RiptideClientAddon.LOG.error("Proxy refresh rejected a check task", var19);
         }

         executor.shutdownNow();
      } catch (RuntimeException var20) {
         riptide.RiptideClientAddon.LOG.error("Proxy refresh failed", var20);
         executor.shutdownNow();
      } finally {
         this.finishRefresh(generation, completed && this.isRefreshCurrent(generation));
      }
   }

   private void refreshOne(long generation, RiptideProxyManager.RefreshTarget target, int timeout, int retryCount, String destinationHost, int destinationPort) {
      if (this.isRefreshCurrent(generation)) {
         RiptideProxyManager.RefreshProbe probe = probeRefreshTarget(target.proxy(), timeout, destinationHost, destinationPort);

         for (int attempts = 0; probe.result().status() != RiptideProxy.Status.ALIVE && attempts < retryCount && this.isRefreshCurrent(generation); attempts++) {
            probe = probeRefreshTarget(target.proxy(), timeout, destinationHost, destinationPort);
         }

         this.applyRefreshResult(generation, target, probe);
      }
   }

   private static RiptideProxyManager.RefreshProbe probeRefreshTarget(RiptideProxy proxy, int timeout, String destinationHost, int destinationPort) {
      if (destinationHost != null && !destinationHost.isBlank() && destinationPort > 0) {
         MultiProxyVerifier.Result verified = MultiProxyVerifier.verify(proxy, destinationHost, destinationPort, timeout);
         RiptideProxy.CheckResult result = verified.ok()
            ? new RiptideProxy.CheckResult(RiptideProxy.Status.ALIVE, verified.latencyMs(), 1)
            : new RiptideProxy.CheckResult(RiptideProxy.Status.DEAD, 0L, 2);
         return new RiptideProxyManager.RefreshProbe(result, verified.workingType());
      } else {
         return new RiptideProxyManager.RefreshProbe(proxy.probeStatus(timeout), null);
      }
   }

   public synchronized boolean applySingleCheck(RiptideProxy proxy, RiptideProxy.CheckResult result, RiptideProxyType workingType) {
      if (proxy != null && result != null && !this.refreshing.get() && this.items.contains(proxy)) {
         if (result.status() == RiptideProxy.Status.ALIVE && workingType != null) {
            proxy.type = workingType;
         }

         proxy.applyCheckResult(result);
         this.sortByLatencyInternal();
         this.bumpListRevision();
         this.scheduleSave();
         return true;
      } else {
         return false;
      }
   }

   private synchronized void applyRefreshResult(long generation, RiptideProxyManager.RefreshTarget target, RiptideProxyManager.RefreshProbe probe) {
      if (this.refreshGeneration == generation && this.refreshing.get() && !this.refreshCancelRequested.get()) {
         RiptideProxy proxy = target.proxy();
         if (this.items.contains(proxy) && target.key().equals(proxyKey(proxy))) {
            if (probe.result().status() == RiptideProxy.Status.ALIVE && probe.workingType() != null) {
               proxy.type = probe.workingType();
            }

            proxy.applyCheckResult(probe.result());
         }

         this.refreshChecked.incrementAndGet();
         this.bumpRefreshRevision(false);
      }
   }

   private synchronized void finishRefresh(long generation, boolean completed) {
      if (this.refreshGeneration == generation && this.refreshing.get()) {
         this.refreshing.set(false);
         this.refreshCancelRequested.set(false);
         this.refreshExecutor = null;
         this.activeRefreshSnapshot = List.of();
         if (completed && this.sortByLatency) {
            this.sortByLatencyInternal();
         }

         if (completed && this.sortByLatency) {
            this.bumpListRevision();
            this.scheduleSave();
         }

         if (completed) {
            this.requestGeoLookup(false);
         }

         this.bumpRefreshRevision(true);
      }
   }

   private boolean isRefreshCurrent(long generation) {
      return this.refreshing.get() && this.refreshGeneration == generation && !this.refreshCancelRequested.get();
   }

   private synchronized List<RiptideProxyManager.GeoTarget> collectGeoTargets(boolean force) {
      long now = System.currentTimeMillis();
      List<RiptideProxyManager.GeoTarget> targets = new ArrayList<>();

      for (RiptideProxy proxy : this.items) {
         if (proxy != null && proxy.needsGeoLookup(now, force)) {
            targets.add(new RiptideProxyManager.GeoTarget(proxy, proxyKey(proxy)));
         }
      }

      return targets;
   }

   private void runGeoLookup(long generation, List<RiptideProxyManager.GeoTarget> targets) {
      if (!RiptideLiteVariant.enabled()) {
         try {
            Map<String, List<RiptideProxyManager.GeoTarget>> byIp = new LinkedHashMap<>();
            List<RiptideProxyManager.GeoApplication> immediate = new ArrayList<>();

            for (RiptideProxyManager.GeoTarget target : targets) {
               if (!this.isGeoCurrent(generation)) {
                  return;
               }

               RiptideProxyGeoLookup.ResolveResult resolved = RiptideProxyGeoLookup.resolveAddress(target.proxy().address);
               if (resolved.immediateResult() != null) {
                  immediate.add(new RiptideProxyManager.GeoApplication(target, resolved.immediateResult()));
                  if (immediate.size() >= 100) {
                     this.applyGeoResults(generation, immediate);
                     immediate.clear();
                  }
               } else if (!resolved.ip().isBlank()) {
                  byIp.computeIfAbsent(resolved.ip(), ignored -> new ArrayList<>()).add(target);
               }
            }

            if (!immediate.isEmpty()) {
               this.applyGeoResults(generation, immediate);
            }

            List<String> batch = new ArrayList<>(100);

            for (String ip : byIp.keySet()) {
               if (!this.isGeoCurrent(generation)) {
                  return;
               }

               batch.add(ip);
               if (batch.size() >= 100) {
                  this.lookupGeoBatch(generation, batch, byIp);
                  batch.clear();
                  sleepBetweenGeoBatches();
               }
            }

            if (!batch.isEmpty() && this.isGeoCurrent(generation)) {
               this.lookupGeoBatch(generation, batch, byIp);
            }
         } finally {
            this.finishGeoLookup(generation);
         }
      }
   }

   private void lookupGeoBatch(long generation, List<String> batch, Map<String, List<RiptideProxyManager.GeoTarget>> byIp) {
      Map<String, RiptideProxyGeoLookup.GeoResult> results = RiptideProxyGeoLookup.lookupBatch(List.copyOf(batch));
      List<RiptideProxyManager.GeoApplication> applications = new ArrayList<>();

      for (String ip : batch) {
         RiptideProxyGeoLookup.GeoResult result = results.getOrDefault(ip, RiptideProxyGeoLookup.GeoResult.failed(ip, System.currentTimeMillis()));
         List<RiptideProxyManager.GeoTarget> targets = byIp.get(ip);
         if (targets != null) {
            for (RiptideProxyManager.GeoTarget target : targets) {
               applications.add(new RiptideProxyManager.GeoApplication(target, result));
            }
         }
      }

      this.applyGeoResults(generation, applications);
   }

   private synchronized void applyGeoResults(long generation, List<RiptideProxyManager.GeoApplication> applications) {
      if (this.geoGeneration == generation && applications != null && !applications.isEmpty()) {
         int changed = 0;

         for (RiptideProxyManager.GeoApplication application : applications) {
            RiptideProxyManager.GeoTarget target = application.target();
            RiptideProxy proxy = target.proxy();
            if (this.items.contains(proxy) && target.key().equals(proxyKey(proxy))) {
               proxy.applyGeoResult(application.result());
               changed++;
            }
         }

         if (changed > 0) {
            this.bumpListRevision();
            this.scheduleSave();
         }
      }
   }

   private void finishGeoLookup(long generation) {
      boolean runAgain = false;
      synchronized (this) {
         if (this.geoGeneration == generation) {
            runAgain = this.geoLookupRequestedAgain.getAndSet(false);
            this.geoLookupRunning.set(false);
         }
      }

      if (runAgain) {
         this.requestGeoLookup(false);
      }
   }

   private boolean isGeoCurrent(long generation) {
      return this.geoLookupRunning.get() && this.geoGeneration == generation;
   }

   private static void sleepBetweenGeoBatches() {
      try {
         Thread.sleep(120L);
      } catch (InterruptedException var1) {
         Thread.currentThread().interrupt();
      }
   }

   private void bumpRefreshRevision(boolean force) {
      long now = System.currentTimeMillis();
      if (force || now - this.lastRefreshRevisionMs >= 150L || this.refreshChecked.get() >= this.refreshTotal.get()) {
         this.lastRefreshRevisionMs = now;
         this.refreshRevision++;
      }
   }

   private void bumpListRevision() {
      this.listRevision++;
      this.refreshRevision++;
   }

   private int effectiveRefreshThreads(int targetCount) {
      int configured = Math.max(1, this.threads);
      int floor = targetCount >= 2000 ? 256 : (targetCount >= 500 ? 128 : (targetCount >= 100 ? 64 : (targetCount >= 32 ? 32 : configured)));
      return Math.max(1, Math.min(targetCount, Math.max(configured, floor)));
   }

   private int effectiveRefreshTimeout(int targetCount) {
      int configured = Math.max(1, this.timeoutMs);
      if (targetCount >= 500) {
         return Math.min(configured, 2500);
      } else {
         return targetCount >= 100 ? Math.min(configured, 3000) : configured;
      }
   }

   private int effectiveRefreshRetries(int targetCount) {
      return targetCount >= 100 ? 0 : Math.max(0, this.retries);
   }

   private static String proxyKey(RiptideProxy proxy) {
      return proxy == null ? "" : (proxy.type == null ? "" : proxy.type.name()) + "\u0000" + proxy.address + "\u0000" + proxy.port;
   }

   public synchronized void clean() {
      if (!this.refreshing.get() && !this.importing.get()) {
         int before = this.items.size();
         this.items.removeIf(proxy -> this.pruneDead && proxy.status == RiptideProxy.Status.DEAD);
         this.items.removeIf(proxy -> this.pruneLatency > 0 && proxy.status == RiptideProxy.Status.ALIVE && proxy.latency >= this.pruneLatency);
         List<RiptideProxy> sorted = new ArrayList<>(this.items);
         sorted.sort(RiptideProxyManager::compareByLatency);
         if (this.pruneToCount > 0 && sorted.size() > this.pruneToCount) {
            sorted.subList(this.pruneToCount, sorted.size()).clear();
            this.items.removeIf(proxy -> !sorted.contains(proxy));
         }

         if (this.sortByLatency) {
            this.items.clear();
            this.items.addAll(sorted);
         }

         if (before != this.items.size() || this.sortByLatency) {
            this.bumpListRevision();
         }

         this.scheduleSave();
      }
   }

   public int importFromFile(File file) {
      List<RiptideProxy> parsed = new ArrayList<>();
      int added = 0;
      Set<String> knownKeys;
      synchronized (this) {
         knownKeys = new HashSet<>(this.items.size() + 1024);

         for (RiptideProxy proxy : this.items) {
            knownKeys.add(proxyKey(proxy));
         }
      }

      String line;
      try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
         while ((line = reader.readLine()) != null) {
            RiptideProxy proxy = parseProxyLine(line.trim());
            if (proxy != null && proxy.isValid() && knownKeys.add(proxyKey(proxy))) {
               parsed.add(proxy);
            }
         }
      } catch (Exception var13) {
         riptide.RiptideClientAddon.LOG.error("Failed to import proxies", var13);
      }

      if (parsed.isEmpty()) {
         return 0;
      } else {
         synchronized (this) {
            Set<String> currentKeys = new HashSet<>(this.items.size() + parsed.size() + 16);

            for (RiptideProxy proxy : this.items) {
               currentKeys.add(proxyKey(proxy));
            }

            for (RiptideProxy proxy : parsed) {
               if (currentKeys.add(proxyKey(proxy))) {
                  if (this.items.isEmpty() && added == 0) {
                     proxy.enabled = true;
                  }

                  this.items.add(proxy);
                  added++;
               }
            }

            if (added > 0) {
               this.bumpListRevision();
               this.scheduleSave();
               this.requestGeoLookup(false);
            }

            return added;
         }
      }
   }

   private void sortByLatencyInternal() {
      this.items.sort(RiptideProxyManager::compareByLatency);
   }

   private static int compareByLatency(RiptideProxy a, RiptideProxy b) {
      boolean aliveA = a != null && a.status == RiptideProxy.Status.ALIVE;
      boolean aliveB = b != null && b.status == RiptideProxy.Status.ALIVE;
      if (aliveA != aliveB) {
         return aliveA ? -1 : 1;
      } else {
         return aliveA ? Long.compare(a.latency, b.latency) : 0;
      }
   }

   private static RiptideProxy parseProxyLine(String line) {
      if (!line.isBlank() && !line.startsWith("#")) {
         Matcher m = PROXY_PATTERN.matcher(line);
         if (m.find()) {
            return buildProxy(m.group(1), normalizeAddress(m.group(2)), Integer.parseInt(m.group(3)), m.group(4), RiptideProxyType.Socks4);
         } else {
            m = PROXY_PATTERN_WEBSHARE.matcher(line);
            if (m.find()) {
               RiptideProxy proxy = buildProxy(null, normalizeAddress(m.group(1)), Integer.parseInt(m.group(2)), null, RiptideProxyType.Socks5);
               if (m.group(3) != null) {
                  proxy.username = m.group(3);
               }

               if (m.group(4) != null) {
                  proxy.password = m.group(4);
               }

               return proxy;
            } else {
               m = PROXY_PATTERN_URI.matcher(line);
               if (!m.find()) {
                  return null;
               } else {
                  String typeName = m.group("type");
                  RiptideProxyType defaultType = m.group("pass") == null && !"socks".equals(typeName) ? RiptideProxyType.Socks4 : RiptideProxyType.Socks5;
                  RiptideProxy proxyx = buildProxy(null, normalizeAddress(m.group("addr")), Integer.parseInt(m.group("port")), typeName, defaultType);
                  if (m.group("user") != null) {
                     proxyx.username = m.group("user");
                  }

                  if (m.group("pass") != null) {
                     proxyx.password = m.group("pass");
                  }

                  return proxyx;
               }
            }
         }
      } else {
         return null;
      }
   }

   private static String normalizeAddress(String address) {
      return address == null ? "" : address.replaceAll("\\b0+\\B", "");
   }

   private static RiptideProxy buildProxy(String name, String address, int port, String typeName, RiptideProxyType defaultType) {
      RiptideProxy proxy = new RiptideProxy();
      proxy.name = name == null ? "" : name.trim();
      proxy.address = address;
      proxy.port = port;
      proxy.type = defaultType == null ? RiptideProxyType.Socks5 : defaultType;
      if (typeName != null) {
         String lower = typeName.toLowerCase();
         if (lower.contains("4")) {
            proxy.type = RiptideProxyType.Socks4;
         } else if (lower.contains("5")) {
            proxy.type = RiptideProxyType.Socks5;
         }
      }

      return proxy;
   }

   @Override
   public Iterator<RiptideProxy> iterator() {
      return this.all().iterator();
   }

   private record GeoApplication(RiptideProxyManager.GeoTarget target, RiptideProxyGeoLookup.GeoResult result) {
   }

   private record GeoTarget(RiptideProxy proxy, String key) {
   }

   private interface ImportSource {
      BufferedReader open() throws Exception;
   }

   public record ImportStatus(boolean running, boolean canceling, int linesRead, int candidates, int added, long generation, long revision, boolean canceled) {
   }

   private record RefreshProbe(RiptideProxy.CheckResult result, RiptideProxyType workingType) {
   }

   public record RefreshStatus(boolean running, boolean canceling, int checked, int total, long generation, long revision) {
   }

   private record RefreshTarget(RiptideProxy proxy, String key) {
   }
}
