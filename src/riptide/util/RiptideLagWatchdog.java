package riptide.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import riptide.modules.AntiVanishModule;
import riptide.modules.ModuleRegistry;
import riptide.modules.PackHideState;
import riptide.modules.RiptideModule;
import riptide.util.macro.FpsLimitController;
import riptide.util.macro.MacroConditionRegistry;
import riptide.util.macro.MacroExecutor;
import riptide.util.mm.MatchmakingManager;

public final class RiptideLagWatchdog {
   private static final int SAMPLE_EVERY_TICKS = 20;
   private static final int FORCED_WINDOW_TICKS = 200;
   private static final boolean TEST_MODE = Boolean.getBoolean("riptide.lagtest");
   private static final long LOW_HOLD_MS = TEST_MODE ? 3000L : 15000L;
   private static final long REPORT_COOLDOWN_MS = TEST_MODE ? 30000L : 300000L;
   private static final int WARMUP_SAMPLES = TEST_MODE ? 5 : 30;
   private static int tickCounter;
   private static double baselineFps = -1.0;
   private static int warmupSamples;
   private static long lowSinceMs;
   private static long lastReportMs;
   private static boolean censusPending;
   private static Map<String, long[]> countersAtTrigger;
   private static int fpsAtTrigger;
   private static long lastErrorLogMs;

   private RiptideLagWatchdog() {
   }

   public static void onClientTick(Minecraft client) {
      try {
         RiptidePerf.tickForcedWindow();
         if (censusPending && !RiptidePerf.isForcedWindowActive()) {
            censusPending = false;
            emitCensus(client);
         }

         if (++tickCounter % 20 != 0) {
            return;
         }

         sample(client);
      } catch (Throwable var4) {
         long now = System.currentTimeMillis();
         if (now - lastErrorLogMs >= 5000L) {
            lastErrorLogMs = now;
            riptide.RiptideClientAddon.LOG.warn("[Riptide] lag watchdog failed; isolated to protect the tick", var4);
         }
      }
   }

   public static void reset() {
      baselineFps = -1.0;
      warmupSamples = 0;
      lowSinceMs = 0L;
      censusPending = false;
      countersAtTrigger = null;
   }

   private static void sample(Minecraft client) {
      if (client == null || client.level == null) {
         reset();
      } else if (client.getWindow() == null || !client.getWindow().isFocused()) {
         lowSinceMs = 0L;
      } else if (FpsLimitController.isActive()) {
         lowSinceMs = 0L;
      } else {
         int fps = client.getFps();
         if (fps > 0) {
            if (baselineFps < 0.0) {
               baselineFps = fps;
            }

            double alpha = fps > baselineFps ? 0.2 : 0.02;
            baselineFps = baselineFps + alpha * (fps - baselineFps);
            if (warmupSamples < WARMUP_SAMPLES) {
               warmupSamples++;
               lowSinceMs = 0L;
            } else {
               double threshold = Math.max(15.0, baselineFps * 0.5);
               long now = System.currentTimeMillis();
               if (fps >= threshold) {
                  lowSinceMs = 0L;
               } else if (lowSinceMs == 0L) {
                  lowSinceMs = now;
               } else if (now - lowSinceMs >= LOW_HOLD_MS) {
                  if (!censusPending && now - lastReportMs >= REPORT_COOLDOWN_MS) {
                     lastReportMs = now;
                     fpsAtTrigger = fps;
                     if (!RiptidePerf.enabled()) {
                        riptide.RiptideClientAddon.LOG
                           .warn("[RiptideLagReport] sustained low fps detected (fps={} baseline={})", fps, String.format(Locale.ROOT, "%.1f", baselineFps));
                     } else {
                        countersAtTrigger = RiptidePerf.snapshotCounters();
                        RiptidePerf.beginForcedWindow(200);
                        censusPending = true;
                        riptide.RiptideClientAddon.LOG
                           .warn(
                              "[RiptideLagReport] sustained low fps detected (fps={} baseline={}); sampling frame time for 10s...",
                              fps,
                              String.format(Locale.ROOT, "%.1f", baselineFps)
                           );
                     }
                  }
               }
            }
         }
      }
   }

   private static void emitCensus(Minecraft client) {
      StringBuilder sb = new StringBuilder(1024);
      sb.append("===== lag census — paste this whole block when reporting =====");
      long heapUsedMb = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() >> 20;
      long heapMaxMb = Runtime.getRuntime().maxMemory() >> 20;
      long lowForMs = lowSinceMs == 0L ? 0L : System.currentTimeMillis() - lowSinceMs;
      line(
         sb,
         "fps="
            + (client == null ? -1 : client.getFps())
            + " atTrigger="
            + fpsAtTrigger
            + " baseline="
            + String.format(Locale.ROOT, "%.1f", baselineFps)
            + " lowForMs="
            + lowForMs
            + " heapMB="
            + heapUsedMb
            + "/"
            + heapMaxMb
      );
      appendSafe(
         sb,
         "state",
         () -> {
            boolean focused = client != null && client.getWindow() != null && client.getWindow().isFocused();
            Screen screen = client == null ? null : client.gui.screen();
            RiptideSharedState shared = RiptideSharedState.get();
            boolean capture = shared != null
               && (
                  shared.isCaptureMode()
                     || shared.hasCaptureCancelCallback()
                     || shared.hasAttackCaptureCallback()
                     || shared.hasBlockCaptureCallback()
                     || shared.hasEntityCaptureCallback()
                     || shared.isGBreakCapturing()
               );
            return "focused="
               + focused
               + " screen="
               + (screen == null ? "none" : screen.getClass().getSimpleName())
               + " capture="
               + capture
               + " payloadStudy="
               + RiptidePayloadStudySession.isActive()
               + " packHide="
               + PackHideState.isActive();
         }
      );
      appendSafe(sb, "overlays", () -> RiptideOverlayManager.get().censusSummary());
      appendSafe(sb, "packetLogger", () -> {
         RiptideModule module = RiptideModule.get();
         RiptidePacketLoggerOverlay logger = module == null ? null : module.getPacketLoggerOverlayIfExists();
         return logger == null ? "not created" : logger.censusSummary();
      });
      appendSafe(sb, "macro", () -> MacroExecutor.oneShotCensus() + " pendingConditions=" + MacroConditionRegistry.pendingConditionCount());
      appendSafe(sb, "antiVanish", AntiVanishModule::censusSummary);
      appendSafe(
         sb,
         "hud",
         () -> {
            String mmSegment = " mm=lite";
            if (!RiptideLiteVariant.enabled()) {
               mmSegment = " mm=" + MatchmakingManager.get().censusSummary();
            }

            return "enabledElements="
               + RiptideHudManager.enabledElementCount()
               + " activeModules="
               + ModuleRegistry.activeModules().size()
               + " toasts="
               + RiptideNotifications.pendingCount()
               + mmSegment;
         }
      );
      appendSafe(
         sb,
         "frame sections (10s window, window-total desc, avg/max* ms, n)",
         () -> {
            StringBuilder sections = new StringBuilder();
            Map<String, long[]> before = countersAtTrigger;
            List<long[]> rows = new ArrayList<>();
            List<String> names = new ArrayList<>();

            for (Entry<String, long[]> entry : RiptidePerf.snapshotCounters().entrySet()) {
               long[] nowC = entry.getValue();
               long[] beforeC = before == null ? null : before.get(entry.getKey());
               long dSamples = nowC[0] - (beforeC == null ? 0L : beforeC[0]);
               long dTotal = nowC[1] - (beforeC == null ? 0L : beforeC[1]);
               if (dSamples > 0L) {
                  names.add(entry.getKey());
                  rows.add(new long[]{dTotal, dSamples, nowC[2], names.size() - 1});
               }
            }

            rows.sort((a, b) -> Long.compare(b[0], a[0]));
            int shown = Math.min(12, rows.size());

            for (int i = 0; i < shown; i++) {
               long[] row = rows.get(i);
               sections.append("\n[RiptideLagReport]   ")
                  .append(names.get((int)row[3]))
                  .append(" avg=")
                  .append(String.format(Locale.ROOT, "%.3f", (double)row[0] / row[1] / 1000000.0))
                  .append(" max*=")
                  .append(String.format(Locale.ROOT, "%.3f", row[2] / 1000000.0))
                  .append(" n=")
                  .append(row[1]);
            }

            return sections.length() == 0 ? "(no samples)" : sections.toString();
         }
      );
      line(sb, "===== end lag census =====");
      countersAtTrigger = null;
      riptide.RiptideClientAddon.LOG.warn("[RiptideLagReport] {}", sb);
   }

   private static void line(StringBuilder sb, String text) {
      sb.append("\n[RiptideLagReport] ").append(text);
   }

   private static void appendSafe(StringBuilder sb, String label, Supplier<String> value) {
      String text;
      try {
         text = value.get();
      } catch (Throwable var5) {
         text = "n/a (" + var5.getClass().getSimpleName() + ")";
      }

      line(sb, label + ": " + text);
   }
}
