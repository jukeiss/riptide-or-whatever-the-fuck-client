package riptide.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public final class RiptidePerf {
   private static final int REPORT_EVERY = 240;
   private static final long DEFAULT_SPIKE_NANOS = 4000000L;
   private static final boolean SYSTEM_ENABLED = Boolean.getBoolean("riptide.perf");
   private static volatile Map<String, RiptidePerf.Counter> counters;
   private static volatile boolean configEnabled;
   private static volatile int joinTicksRemaining;
   private static volatile int forcedTicksRemaining;

   private RiptidePerf() {
   }

   public static long begin() {
      return enabled() ? System.nanoTime() : 0L;
   }

   public static long beginSampled() {
      return forcedTicksRemaining <= 0 && !enabled() ? 0L : System.nanoTime();
   }

   public static void beginForcedWindow(int ticks) {
      forcedTicksRemaining = Math.max(forcedTicksRemaining, ticks);
   }

   public static void tickForcedWindow() {
      if (forcedTicksRemaining > 0) {
         forcedTicksRemaining--;
      }
   }

   public static boolean isForcedWindowActive() {
      return forcedTicksRemaining > 0;
   }

   public static Map<String, long[]> snapshotCounters() {
      Map<String, RiptidePerf.Counter> source = counters;
      if (source != null && !source.isEmpty()) {
         Map<String, long[]> out = new HashMap<>(source.size());

         for (Entry<String, RiptidePerf.Counter> entry : source.entrySet()) {
            RiptidePerf.Counter c = entry.getValue();
            out.put(entry.getKey(), new long[]{c.samples, c.totalNanos, c.maxNanos});
         }

         return out;
      } else {
         return Map.of();
      }
   }

   public static long beginJoin() {
      return joinTicksRemaining > 0 && enabled() ? System.nanoTime() : 0L;
   }

   public static void end(String name, long startNanos) {
      if (startNanos != 0L && name != null && !name.isBlank()) {
         long elapsed = System.nanoTime() - startNanos;
         record(name, elapsed);
      }
   }

   public static void endJoinSpike(String name, long startNanos) {
      endJoinSpike(name, startNanos, 4000000L);
   }

   public static void endJoinSpike(String name, long startNanos, long thresholdNanos) {
      if (startNanos != 0L && name != null && !name.isBlank()) {
         long elapsed = System.nanoTime() - startNanos;
         record(name, elapsed);
         if (joinTicksRemaining > 0 && elapsed >= thresholdNanos) {
            riptide.RiptideClientAddon.LOG.warn("[RiptidePerf] join spike {}={}ms", name, String.format(Locale.ROOT, "%.3f", elapsed / 1000000.0));
         }
      }
   }

   public static void endSpike(String name, long startNanos, long thresholdNanos) {
      if (startNanos != 0L && name != null && !name.isBlank()) {
         long elapsed = System.nanoTime() - startNanos;
         record(name, elapsed);
         if (elapsed >= thresholdNanos) {
            riptide.RiptideClientAddon.LOG.warn("[RiptidePerf] spike {}={}ms", name, String.format(Locale.ROOT, "%.3f", elapsed / 1000000.0));
         }
      }
   }

   private static void record(String name, long elapsed) {
      RiptidePerf.Counter counter = countersForWrite().computeIfAbsent(name, ignored -> new RiptidePerf.Counter());
      long samples = ++counter.samples;
      counter.totalNanos += elapsed;
      counter.maxNanos = Math.max(counter.maxNanos, elapsed);
      if (samples % 240L == 0L) {
         double avgMs = (double)counter.totalNanos / samples / 1000000.0;
         double maxMs = counter.maxNanos / 1000000.0;
         riptide.RiptideClientAddon.LOG
            .info(
               "[RiptidePerf] {} avg={}ms max={}ms samples={}",
               new Object[]{name, String.format(Locale.ROOT, "%.3f", avgMs), String.format(Locale.ROOT, "%.3f", maxMs), samples}
            );
      }
   }

   public static void beginJoinWindow() {
      if (enabled()) {
         joinTicksRemaining = 100;
         riptide.RiptideClientAddon.LOG.info("[RiptidePerf] join profiling window started.");
      }
   }

   public static void tickJoinWindow() {
      if (joinTicksRemaining > 0) {
         joinTicksRemaining--;
      }
   }

   public static boolean isJoinWindowActive() {
      return joinTicksRemaining > 0 && enabled();
   }

   public static boolean enabled() {
      return SYSTEM_ENABLED || configEnabled;
   }

   static void publishConfigState(RiptideConfig config) {
      configEnabled = config != null && config.performanceDebug;
   }

   private static Map<String, RiptidePerf.Counter> countersForWrite() {
      Map<String, RiptidePerf.Counter> current = counters;
      if (current != null) {
         return current;
      } else {
         synchronized (RiptidePerf.class) {
            current = counters;
            if (current == null) {
               current = new ConcurrentHashMap<>();
               counters = current;
            }

            return current;
         }
      }
   }

   private static final class Counter {
      private long samples;
      private long totalNanos;
      private long maxNanos;
   }
}
