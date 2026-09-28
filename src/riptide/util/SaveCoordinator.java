package riptide.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;

final class SaveCoordinator {
   static final long CONFIG_DEBOUNCE_MS = 300L;
   private static final Object LOCK = new Object();
   private static final Map<String, Runnable> LATEST = new HashMap<>();
   private static final Map<String, Boolean> DRAINING = new HashMap<>();
   private static final Map<String, ScheduledFuture<?>> DEBOUNCED = new HashMap<>();
   private static final Map<String, Long> DEBOUNCE_VERSIONS = new HashMap<>();
   private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread thread = new Thread(r, "riptide-persistence");
      thread.setDaemon(true);
      return thread;
   });
   private static RiptideConfig pendingConfig;
   private static long pendingConfigVersion;
   private static final String CONFIG_DEBOUNCE_KEY = "config-capture";

   private SaveCoordinator() {
   }

   static void requestConfigSave(RiptideConfig config) {
      if (config != null) {
         synchronized (LOCK) {
            pendingConfig = config;
            long version = ++pendingConfigVersion;
            scheduleDebouncedLocked("config-capture", 300L, () -> dispatchConfigCapture(version));
         }
      }
   }

   private static void dispatchConfigCapture(long version) {
      Runnable capture = () -> captureConfigIfCurrent(version);

      try {
         Minecraft client = Minecraft.getInstance();
         if (client != null) {
            client.execute(capture);
            return;
         }
      } catch (Throwable var4) {
         riptide.RiptideClientAddon.LOG.debug("Could not dispatch config capture to client thread", var4);
      }

      capture.run();
   }

   private static void captureConfigIfCurrent(long version) {
      RiptideConfig config;
      synchronized (LOCK) {
         if (version != pendingConfigVersion || pendingConfig == null) {
            return;
         }

         config = pendingConfig;
         pendingConfig = null;
      }

      RiptideConfigWriter.captureAndEnqueue(config);
   }

   static void capturePendingConfigNow() {
      RiptideConfig config;
      synchronized (LOCK) {
         config = pendingConfig;
         if (config == null) {
            return;
         }

         pendingConfig = null;
         pendingConfigVersion++;
         cancelDebouncedLocked("config-capture");
      }

      RiptideConfigWriter.captureAndEnqueue(config);
   }

   static void scheduleDebounced(String key, long delayMs, Runnable work) {
      if (key != null && work != null) {
         synchronized (LOCK) {
            scheduleDebouncedLocked(key, delayMs, work);
         }
      }
   }

   private static void scheduleDebouncedLocked(String key, long delayMs, Runnable work) {
      ScheduledFuture<?> old = DEBOUNCED.remove(key);
      if (old != null) {
         old.cancel(false);
      }

      long version = DEBOUNCE_VERSIONS.getOrDefault(key, 0L) + 1L;
      DEBOUNCE_VERSIONS.put(key, version);
      ScheduledFuture<?> next = WORKER.schedule(() -> {
         synchronized (LOCK) {
            if (DEBOUNCE_VERSIONS.getOrDefault(key, 0L) != version) {
               return;
            }

            DEBOUNCED.remove(key);
         }

         work.run();
      }, Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
      DEBOUNCED.put(key, next);
   }

   private static void cancelDebouncedLocked(String key) {
      ScheduledFuture<?> pending = DEBOUNCED.remove(key);
      if (pending != null) {
         pending.cancel(false);
      }

      DEBOUNCE_VERSIONS.put(key, DEBOUNCE_VERSIONS.getOrDefault(key, 0L) + 1L);
   }

   static void enqueueLatest(String key, Runnable work) {
      if (key != null && work != null) {
         synchronized (LOCK) {
            LATEST.put(key, work);
            if (DRAINING.putIfAbsent(key, Boolean.TRUE) == null) {
               WORKER.execute(() -> drain(key));
            }
         }
      }
   }

   private static void drain(String key) {
      while (true) {
         Runnable work;
         synchronized (LOCK) {
            work = LATEST.remove(key);
            if (work == null) {
               DRAINING.remove(key);
               return;
            }
         }

         try {
            work.run();
         } catch (Throwable var4) {
            riptide.RiptideClientAddon.LOG.error("Persistence task failed for {}", key, var4);
         }
      }
   }

   static void flushBlocking(long timeoutMs) {
      capturePendingConfigNow();
      CountDownLatch latch = new CountDownLatch(1);
      WORKER.execute(latch::countDown);

      try {
         latch.await(Math.max(0L, timeoutMs), TimeUnit.MILLISECONDS);
      } catch (InterruptedException var4) {
         Thread.currentThread().interrupt();
      } catch (Throwable var5) {
      }
   }
}
