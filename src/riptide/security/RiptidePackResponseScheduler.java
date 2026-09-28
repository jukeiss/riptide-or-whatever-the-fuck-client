package riptide.security;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action;
import riptide.util.RiptideConfig;

public final class RiptidePackResponseScheduler {
   private static final long ACCEPT_BASE_MS = 250L;
   private static final long ACCEPT_SPREAD_MS = 650L;
   private static final long DOWNLOAD_BASE_MS = 1200L;
   private static final long DOWNLOAD_SPREAD_MS = 3800L;
   private static final long APPLY_BASE_MS = 1500L;
   private static final long APPLY_SPREAD_MS = 2500L;
   private static final long FAIL_BASE_MS = 3000L;
   private static final long FAIL_SPREAD_MS = 6000L;
   private static final long DECLINE_SPREAD_MS = 6000L;
   private static final List<RiptidePackResponseScheduler.Pending> PENDING = new ArrayList<>();

   private RiptidePackResponseScheduler() {
   }

   private static long jitter(long baseMs, long spreadMs) {
      return baseMs + ThreadLocalRandom.current().nextLong(spreadMs + 1L);
   }

   public static long declineDelayMs() {
      long configured = Math.max(0L, (long)RiptideConfig.getGlobal().packResponseDelayMs);
      return configured == 0L ? 0L : jitter(configured, 6000L);
   }

   public static long acceptDelayMs() {
      return jitter(250L, 650L);
   }

   public static long downloadedDelayMs(long afterAcceptMs) {
      return afterAcceptMs + jitter(1200L, 3800L);
   }

   public static long appliedDelayMs(long afterDownloadedMs) {
      return afterDownloadedMs + jitter(1500L, 2500L);
   }

   public static long failedDelayMs(long afterAcceptMs) {
      return afterAcceptMs + jitter(3000L, 6000L);
   }

   public static void schedule(UUID packId, Action action, long delayMs, Consumer<ServerboundResourcePackPacket> sender) {
      schedule(packId, action, delayMs, sender, null);
   }

   public static void schedule(UUID packId, Action action, long delayMs, Consumer<ServerboundResourcePackPacket> sender, Runnable onSent) {
      if (packId != null && action != null && sender != null) {
         RiptidePackResponseScheduler.Pending pending = new RiptidePackResponseScheduler.Pending(
            packId, action, System.currentTimeMillis() + Math.max(0L, delayMs), sender, onSent
         );
         synchronized (PENDING) {
            PENDING.add(pending);
         }
      }
   }

   public static void tick() {
      List<RiptidePackResponseScheduler.Pending> due = null;
      synchronized (PENDING) {
         if (PENDING.isEmpty()) {
            return;
         }

         long now = System.currentTimeMillis();
         Iterator<RiptidePackResponseScheduler.Pending> it = PENDING.iterator();

         while (it.hasNext()) {
            RiptidePackResponseScheduler.Pending pending = it.next();
            if (pending.dueAtMs() <= now) {
               it.remove();
               if (due == null) {
                  due = new ArrayList<>(3);
               }

               due.add(pending);
            }
         }
      }

      if (due != null) {
         for (RiptidePackResponseScheduler.Pending pending : due) {
            try {
               pending.sender().accept(new ServerboundResourcePackPacket(pending.packId(), pending.action()));
               if (pending.onSent() != null) {
                  pending.onSent().run();
               }
            } catch (Throwable var7) {
            }
         }
      }
   }

   public static void cancel(UUID packId) {
      synchronized (PENDING) {
         if (packId == null) {
            PENDING.clear();
         } else {
            PENDING.removeIf(pending -> packId.equals(pending.packId()));
         }
      }
   }

   public static void clearAll() {
      cancel(null);
   }

   public static boolean hasPending() {
      synchronized (PENDING) {
         return !PENDING.isEmpty();
      }
   }

   private record Pending(UUID packId, Action action, long dueAtMs, Consumer<ServerboundResourcePackPacket> sender, Runnable onSent) {
   }
}
