package riptide.util.macro;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Queue;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.server.RunningOnDifferentThreadException;

public final class PingSpoofController {
   private static final Minecraft MC = Minecraft.getInstance();
   private static volatile boolean moduleActive;
   private static volatile int moduleDelayMs;
   private static volatile boolean moduleRealIncoming;
   private static volatile boolean moduleRealOutgoing;
   private static volatile boolean anyOverrides;
   private static final long LEGACY_OWNER = 0L;
   private static final ConcurrentHashMap<Long, PingSpoofController.MacroOverride> MACRO_OVERRIDES = new ConcurrentHashMap<>();
   private static final Queue<PingSpoofController.Held> INCOMING = new ConcurrentLinkedQueue<>();
   private static final Queue<PingSpoofController.Held> OUTGOING = new ConcurrentLinkedQueue<>();
   private static final Set<Packet<?>> PASS_THROUGH = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
   private static final AtomicInteger PASS_THROUGH_COUNT = new AtomicInteger();
   private static volatile boolean flushIncomingNow;
   private static final long MAX_HOLD_MS = 8000L;
   private static final int FORCE_FLUSH_CEILING = 8192;
   private static final int MAX_DELIVER_PER_FLUSH = 1024;
   private static final int MAX_PENDING_REPLIES = 256;
   private static final AtomicInteger PENDING_REPLIES = new AtomicInteger();
   private static volatile int connectionEpoch;
   private static volatile ScheduledExecutorService scheduler;
   private static volatile long lastErrorLogMs;
   private static final long ERROR_LOG_INTERVAL_MS = 5000L;

   private static ScheduledExecutorService scheduler() {
      ScheduledExecutorService local = scheduler;
      if (local == null) {
         synchronized (PingSpoofController.class) {
            local = scheduler;
            if (local == null) {
               local = Executors.newSingleThreadScheduledExecutor(r -> {
                  Thread t = new Thread(r, "Riptide-PingSpoof-Scheduler");
                  t.setDaemon(true);
                  return t;
               });
               scheduler = local;
            }
         }
      }

      return local;
   }

   private PingSpoofController() {
   }

   public static void apply(int delayMs, boolean realIncoming, boolean realOutgoing, long durationNanos) {
      apply(0L, delayMs, realIncoming, realOutgoing, durationNanos);
   }

   public static void apply(long ownerRunId, int delayMs, boolean realIncoming, boolean realOutgoing, long durationNanos) {
      if (durationNanos <= 0L) {
         clearMacro(ownerRunId);
      } else {
         MACRO_OVERRIDES.put(
            ownerRunId, new PingSpoofController.MacroOverride(Math.max(0, delayMs), realIncoming, realOutgoing, System.nanoTime() + durationNanos, false)
         );
         anyOverrides = true;
      }
   }

   public static void applyUntilCleared(int delayMs, boolean realIncoming, boolean realOutgoing) {
      applyUntilCleared(0L, delayMs, realIncoming, realOutgoing);
   }

   public static void applyUntilCleared(long ownerRunId, int delayMs, boolean realIncoming, boolean realOutgoing) {
      MACRO_OVERRIDES.put(ownerRunId, new PingSpoofController.MacroOverride(Math.max(0, delayMs), realIncoming, realOutgoing, 0L, true));
      anyOverrides = true;
   }

   public static void clearMacro() {
      clearMacro(0L);
   }

   public static void clearMacro(long ownerRunId) {
      MACRO_OVERRIDES.remove(ownerRunId);
      refreshOverrideInterest();
   }

   public static void clearAllMacros() {
      MACRO_OVERRIDES.clear();
      refreshOverrideInterest();
   }

   public static void setModuleOverride(int delayMs, boolean realIncoming, boolean realOutgoing) {
      moduleDelayMs = Math.max(0, delayMs);
      moduleRealIncoming = realIncoming;
      moduleRealOutgoing = realOutgoing;
      moduleActive = true;
      anyOverrides = true;
   }

   public static void clearModuleOverride() {
      moduleActive = false;
      refreshOverrideInterest();
   }

   public static int delayMs() {
      if (!anyOverrides) {
         return -1;
      } else {
         pruneExpiredMacroOverrides();
         if (MACRO_OVERRIDES.isEmpty()) {
            return moduleActive ? moduleDelayMs : -1;
         } else {
            int effective = 0;

            for (PingSpoofController.MacroOverride override : MACRO_OVERRIDES.values()) {
               effective = Math.max(effective, override.delayMs);
            }

            return effective;
         }
      }
   }

   public static boolean delayIncoming() {
      if (!anyOverrides) {
         return false;
      } else {
         pruneExpiredMacroOverrides();
         if (!MACRO_OVERRIDES.isEmpty()) {
            for (PingSpoofController.MacroOverride override : MACRO_OVERRIDES.values()) {
               if (override.realIncoming) {
                  return true;
               }
            }

            return false;
         } else {
            return moduleActive && moduleRealIncoming;
         }
      }
   }

   public static boolean delayOutgoing() {
      if (!anyOverrides) {
         return false;
      } else {
         pruneExpiredMacroOverrides();
         if (!MACRO_OVERRIDES.isEmpty()) {
            for (PingSpoofController.MacroOverride override : MACRO_OVERRIDES.values()) {
               if (override.realOutgoing) {
                  return true;
               }
            }

            return false;
         } else {
            return moduleActive && moduleRealOutgoing;
         }
      }
   }

   public static boolean isActive() {
      return delayMs() >= 0;
   }

   private static void pruneExpiredMacroOverrides() {
      if (MACRO_OVERRIDES.isEmpty()) {
         refreshOverrideInterest();
      } else {
         long now = System.nanoTime();

         for (Entry<Long, PingSpoofController.MacroOverride> entry : MACRO_OVERRIDES.entrySet()) {
            if (!entry.getValue().active(now)) {
               MACRO_OVERRIDES.remove(entry.getKey(), entry.getValue());
            }
         }

         refreshOverrideInterest();
      }
   }

   private static void refreshOverrideInterest() {
      anyOverrides = moduleActive || !MACRO_OVERRIDES.isEmpty();
   }

   public static void clearQueue() {
      INCOMING.clear();
      OUTGOING.clear();
      PASS_THROUGH.clear();
      PASS_THROUGH_COUNT.set(0);
      flushIncomingNow = false;
      connectionEpoch++;
      PENDING_REPLIES.set(0);
   }

   public static boolean interceptInbound(Packet<?> packet) {
      if (!anyOverrides) {
         return false;
      } else if (MC.player == null) {
         return false;
      } else {
         int delay = delayMs();
         if (delay <= 0) {
            return false;
         } else if (!delayIncoming()) {
            return false;
         } else if (isIncomingNeverDelay(packet)) {
            return false;
         } else if (isIncomingFlushTrigger(packet)) {
            INCOMING.add(new PingSpoofController.Held(packet, System.currentTimeMillis()));
            flushIncomingNow = true;
            return true;
         } else {
            INCOMING.add(new PingSpoofController.Held(packet, System.currentTimeMillis()));
            return true;
         }
      }
   }

   public static boolean interceptOutbound(Packet<?> packet) {
      if (consumePassThrough(packet)) {
         return false;
      } else if (!anyOverrides) {
         return false;
      } else if (MC.player == null) {
         return false;
      } else {
         int delay = delayMs();
         if (delay > 0 && packet instanceof ServerboundKeepAlivePacket) {
            scheduleResend(packet, delay);
            return true;
         } else if (delay <= 0 || !delayOutgoing()) {
            return false;
         } else if (isOutgoingNeverDelay(packet)) {
            return false;
         } else {
            OUTGOING.add(new PingSpoofController.Held(packet, System.currentTimeMillis()));
            return true;
         }
      }
   }

   public static void flushDue() {
      if (INCOMING.isEmpty() && OUTGOING.isEmpty()) {
         flushIncomingNow = false;
      } else if (MC.getConnection() == null) {
         if (!INCOMING.isEmpty() || !OUTGOING.isEmpty() || PASS_THROUGH_COUNT.get() > 0) {
            clearQueue();
         }
      } else {
         long now = System.currentTimeMillis();
         if (!INCOMING.isEmpty()) {
            boolean flushAll = flushIncomingNow || INCOMING.size() >= 8192;
            flushIncomingNow = false;
            int delay = delayMs();
            long threshold = !flushAll && delay >= 0 && delayIncoming() ? delay : 0L;
            int budget = flushAll ? Integer.MAX_VALUE : 1024;

            PingSpoofController.Held head;
            while (budget-- > 0 && (head = INCOMING.peek()) != null && (flushAll || now - head.timestampMs() >= threshold || now - head.timestampMs() >= 8000L)) {
               INCOMING.poll();
               deliverIncoming(head.packet());
            }
         }

         if (!OUTGOING.isEmpty()) {
            boolean flushAll = OUTGOING.size() >= 8192;
            int delay = delayMs();
            long threshold = !flushAll && delay >= 0 && delayOutgoing() ? delay : 0L;
            int budget = flushAll ? Integer.MAX_VALUE : 1024;

            PingSpoofController.Held head;
            while (budget-- > 0 && (head = OUTGOING.peek()) != null && (flushAll || now - head.timestampMs() >= threshold || now - head.timestampMs() >= 8000L)) {
               OUTGOING.poll();
               deliverOutgoing(head.packet());
            }
         }
      }
   }

   private static void scheduleResend(Packet<?> packet, int delayMs) {
      long wait = PENDING_REPLIES.get() >= 256 ? 0L : Math.max(0L, Math.min((long)delayMs, 8000L));
      int epoch = connectionEpoch;
      if (wait <= 0L) {
         sendResend(packet, epoch);
      } else {
         PENDING_REPLIES.incrementAndGet();

         try {
            scheduler().schedule(() -> {
               PENDING_REPLIES.decrementAndGet();
               sendResend(packet, epoch);
            }, wait, TimeUnit.MILLISECONDS);
         } catch (Throwable var6) {
            PENDING_REPLIES.decrementAndGet();
            sendResend(packet, epoch);
         }
      }
   }

   private static void sendResend(Packet<?> packet, int epoch) {
      if (epoch == connectionEpoch) {
         ClientPacketListener connection = MC.getConnection();
         if (connection != null) {
            addPassThrough(packet);

            try {
               connection.send(packet);
            } catch (Throwable var4) {
               consumePassThrough(packet);
               logRedispatchError("keepalive", packet, var4);
            }
         }
      }
   }

   private static void deliverIncoming(Packet<?> packet) {
      ClientPacketListener connection = MC.getConnection();
      if (connection != null) {
         try {
            packet.handle(connection);
         } catch (RunningOnDifferentThreadException var3) {
         } catch (Throwable var4) {
            logRedispatchError("incoming", packet, var4);
         }
      }
   }

   private static void deliverOutgoing(Packet<?> packet) {
      ClientPacketListener connection = MC.getConnection();
      if (connection != null) {
         addPassThrough(packet);

         try {
            connection.send(packet);
         } catch (Throwable var3) {
            consumePassThrough(packet);
            logRedispatchError("outgoing", packet, var3);
         }
      }
   }

   private static void addPassThrough(Packet<?> packet) {
      if (packet != null && PASS_THROUGH.add(packet)) {
         PASS_THROUGH_COUNT.incrementAndGet();
      }
   }

   private static boolean consumePassThrough(Packet<?> packet) {
      if (PASS_THROUGH_COUNT.get() == 0 || packet == null) {
         return false;
      } else if (!PASS_THROUGH.remove(packet)) {
         return false;
      } else {
         PASS_THROUGH_COUNT.updateAndGet(count -> Math.max(0, count - 1));
         return true;
      }
   }

   private static void logRedispatchError(String direction, Packet<?> packet, Throwable t) {
      if (!(t instanceof RunningOnDifferentThreadException) && !(t.getCause() instanceof RunningOnDifferentThreadException)) {
         long now = System.currentTimeMillis();
         if (now - lastErrorLogMs >= 5000L) {
            lastErrorLogMs = now;
            riptide.RiptideClientAddon.LOG
               .warn("[Riptide] PingSpoof re-dispatch ({}) failed for {}", new Object[]{direction, packet.getClass().getSimpleName(), t});
         }
      }
   }

   private static boolean isIncomingNeverDelay(Packet<?> packet) {
      return packet instanceof ClientboundSystemChatPacket
         || packet instanceof ClientboundDisguisedChatPacket
         || packet instanceof ClientboundKeepAlivePacket
         || packet instanceof ClientboundPingPacket
         || packet instanceof ClientboundStartConfigurationPacket;
   }

   private static boolean isIncomingFlushTrigger(Packet<?> packet) {
      return packet instanceof ClientboundPlayerPositionPacket
         || packet instanceof ClientboundRespawnPacket
         || packet instanceof ClientboundLoginPacket
         || packet instanceof ClientboundDisconnectPacket
         || packet instanceof ClientboundSetHealthPacket health && health.getHealth() <= 0.0F
         || packet instanceof ClientboundLevelChunkWithLightPacket
         || packet instanceof ClientboundLightUpdatePacket
         || packet instanceof ClientboundForgetLevelChunkPacket
         || packet instanceof ClientboundChunkBatchStartPacket
         || packet instanceof ClientboundChunkBatchFinishedPacket
         || packet instanceof ClientboundSetChunkCacheCenterPacket
         || packet instanceof ClientboundSetChunkCacheRadiusPacket
         || packet instanceof ClientboundSectionBlocksUpdatePacket
         || packet instanceof ClientboundCustomPayloadPacket;
   }

   private static boolean isOutgoingNeverDelay(Packet<?> packet) {
      return packet instanceof ServerboundKeepAlivePacket
         || packet instanceof ServerboundPongPacket
         || packet instanceof ServerboundChatPacket
         || packet instanceof ServerboundChatCommandPacket
         || packet instanceof ServerboundResourcePackPacket
         || packet instanceof ServerboundChunkBatchReceivedPacket;
   }

   private record Held(Packet<?> packet, long timestampMs) {
   }

   private record MacroOverride(int delayMs, boolean realIncoming, boolean realOutgoing, long expiryNanos, boolean indefinite) {
      boolean active(long now) {
         return this.indefinite || now - this.expiryNanos < 0L;
      }
   }
}
