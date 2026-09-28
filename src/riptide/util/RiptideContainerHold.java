package riptide.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import riptide.modules.PackHideState;

public final class RiptideContainerHold {
   private static final long IDLE_TTL_MS = 60000L;
   private static final Map<Integer, AtomicInteger> HOLDS = new ConcurrentHashMap<>();
   private static final Map<Integer, ServerboundContainerClosePacket> PENDING_CLOSES = new ConcurrentHashMap<>();
   private static final Map<Integer, Long> LAST_TOUCHED_MS = new ConcurrentHashMap<>();
   private static volatile Consumer<RiptidePacketClick.Target> pendingCapture = null;

   private RiptideContainerHold() {
   }

   public static void hold(int containerId) {
      if (containerId >= 0) {
         HOLDS.computeIfAbsent(containerId, id -> new AtomicInteger(0)).incrementAndGet();
         LAST_TOUCHED_MS.put(containerId, System.currentTimeMillis());
      }
   }

   public static boolean isHeld(int containerId) {
      AtomicInteger counter = HOLDS.get(containerId);
      return counter != null && counter.get() > 0;
   }

   public static void capturePendingClose(int containerId, ServerboundContainerClosePacket pkt) {
      if (pkt != null) {
         PENDING_CLOSES.put(containerId, pkt);
         LAST_TOUCHED_MS.put(containerId, System.currentTimeMillis());
      }
   }

   public static void release(int containerId, ClientPacketListener conn) {
      AtomicInteger counter = HOLDS.get(containerId);
      if (counter == null) {
         flushPendingClose(containerId, conn);
      } else {
         if (counter.decrementAndGet() <= 0) {
            HOLDS.remove(containerId);
            flushPendingClose(containerId, conn);
            LAST_TOUCHED_MS.remove(containerId);
         } else {
            LAST_TOUCHED_MS.put(containerId, System.currentTimeMillis());
         }
      }
   }

   private static void flushPendingClose(int containerId, ClientPacketListener conn) {
      ServerboundContainerClosePacket pending = PENDING_CLOSES.remove(containerId);
      if (!PackHideState.isHardLocked()) {
         if (pending != null && conn != null) {
            try {
               conn.send(pending);
            } catch (Throwable var4) {
            }
         }
      }
   }

   public static void clearAll() {
      HOLDS.clear();
      PENDING_CLOSES.clear();
      LAST_TOUCHED_MS.clear();
   }

   public static void releaseAllAndFlush(ClientPacketListener conn) {
      HOLDS.clear();
      LAST_TOUCHED_MS.clear();
      if (conn != null && !PackHideState.isHardLocked()) {
         for (Entry<Integer, ServerboundContainerClosePacket> entry : PENDING_CLOSES.entrySet()) {
            try {
               conn.send((Packet)entry.getValue());
            } catch (Throwable var4) {
            }
         }

         PENDING_CLOSES.clear();
      } else {
         PENDING_CLOSES.clear();
      }
   }

   public static void onContainerOpened(int newContainerId) {
      if (HOLDS.remove(newContainerId) != null) {
         PENDING_CLOSES.remove(newContainerId);
         LAST_TOUCHED_MS.remove(newContainerId);
      }
   }

   public static void tickExpiry() {
      if (!LAST_TOUCHED_MS.isEmpty()) {
         long now = System.currentTimeMillis();
         List<Integer> expired = new ArrayList<>();

         for (Entry<Integer, Long> e : LAST_TOUCHED_MS.entrySet()) {
            if (now - e.getValue() >= 60000L) {
               expired.add(e.getKey());
            }
         }

         if (!expired.isEmpty()) {
            ClientPacketListener conn = null;

            try {
               Minecraft mc = Minecraft.getInstance();
               conn = mc == null ? null : mc.getConnection();
            } catch (Throwable var6) {
            }

            for (Integer id : expired) {
               HOLDS.remove(id);
               LAST_TOUCHED_MS.remove(id);
               flushPendingClose(id, conn);
            }
         }
      }
   }

   public static boolean hasExpiryWork() {
      return !LAST_TOUCHED_MS.isEmpty();
   }

   public static void setPendingCapture(Consumer<RiptidePacketClick.Target> consumer) {
      pendingCapture = consumer;
   }

   public static boolean hasPendingCapture() {
      return pendingCapture != null;
   }

   public static boolean deliverCapture(RiptidePacketClick.Target target) {
      Consumer<RiptidePacketClick.Target> consumer = pendingCapture;
      if (consumer != null && target != null) {
         pendingCapture = null;

         try {
            consumer.accept(target);
         } catch (Throwable var3) {
         }

         return true;
      } else {
         return false;
      }
   }

   public static void clearPendingCapture() {
      pendingCapture = null;
   }
}
