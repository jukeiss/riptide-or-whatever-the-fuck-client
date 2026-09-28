package riptide.util.mm.relay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import riptide.util.mm.crypto.MmCrypto;

public final class RelayManager {
   private static final byte MAGIC0 = -95;
   private static final byte MAGIC1 = 109;
   private static final byte FRAME_VERSION = 1;
   private static final int HEADER = 23;
   private static final int MAX_CHUNK_PAYLOAD = 2400;
   public static final int MAX_ENVELOPE = 65536;
   private static final int MAX_CHUNKS = 29;
   private static final int MAX_PENDING_GROUPS = 256;
   private static final long GROUP_TTL_MS = 60000L;
   private static final long DELIVERED_TTL_MS = 180000L;
   private final List<Relay> relays = new ArrayList<>();
   private final Map<String, Consumer<byte[]>> topicConsumers = new ConcurrentHashMap<>();
   private final Map<String, RelayManager.PendingGroup> pending = new ConcurrentHashMap<>();
   private final Map<String, Long> deliveredGroups = new ConcurrentHashMap<>();
   private volatile long lastWarnMs;

   public RelayManager(List<Relay> relays) {
      this.relays.addAll(relays);
   }

   public List<RelayStatus> statuses() {
      List<RelayStatus> out = new ArrayList<>();

      for (Relay r : this.relays) {
         out.add(r.status());
      }

      return out;
   }

   public void subscribe(String topic, Consumer<byte[]> onEnvelope) {
      this.topicConsumers.put(topic, onEnvelope);

      for (Relay r : this.relays) {
         r.subscribe(topic, frame -> this.onFrame(topic, frame));
      }
   }

   public void unsubscribe(String topic) {
      this.topicConsumers.remove(topic);

      for (Relay r : this.relays) {
         r.unsubscribe(topic);
      }

      this.pending.values().removeIf(g -> g.topic.equals(topic));
   }

   public void subscribeRaw(String topic, Consumer<byte[]> onMessage) {
      for (Relay r : this.relays) {
         r.subscribe(topic, payload -> {
            try {
               onMessage.accept(payload);
            } catch (Throwable var4x) {
               this.warnThrottled("Matchmaking: sys-event handler threw on a message", var4x);
            }
         });
      }
   }

   public void unsubscribeRaw(String topic) {
      for (Relay r : this.relays) {
         r.unsubscribe(topic);
      }
   }

   public boolean publish(String topic, byte[] envelope) {
      return this.publish(topic, envelope, false);
   }

   public boolean publish(String topic, byte[] envelope, boolean durable) {
      if (envelope.length > 65536) {
         return false;
      } else {
         byte[] groupId = MmCrypto.randomBytes(16);
         int total = Math.max(1, (envelope.length + 2400 - 1) / 2400);

         for (int index = 0; index < total; index++) {
            int off = index * 2400;
            int len = Math.min(2400, envelope.length - off);
            byte[] frame = new byte[23 + len];
            frame[0] = -95;
            frame[1] = 109;
            frame[2] = 1;
            System.arraycopy(groupId, 0, frame, 3, 16);
            putU16(frame, 19, total);
            putU16(frame, 21, index);
            System.arraycopy(envelope, off, frame, 23, len);

            for (Relay r : this.relays) {
               r.publish(topic, frame, durable);
            }
         }

         return true;
      }
   }

   public void reconnectAll() {
      for (Relay r : this.relays) {
         try {
            r.reconnect();
         } catch (Throwable var4) {
         }
      }
   }

   public void closeAll() {
      for (Relay r : this.relays) {
         try {
            r.close();
         } catch (Throwable var4) {
         }
      }

      this.topicConsumers.clear();
      this.pending.clear();
      this.deliveredGroups.clear();
   }

   private void onFrame(String topic, byte[] frame) {
      try {
         if (frame.length < 23) {
            return;
         }

         if (frame[0] != -95 || frame[1] != 109 || frame[2] != 1) {
            return;
         }

         int total = getU16(frame, 19);
         int index = getU16(frame, 21);
         if (total < 1 || total > 29 || index < 0 || index >= total) {
            return;
         }

         String gid = MmCrypto.hex(Arrays.copyOfRange(frame, 3, 19));
         long now = System.currentTimeMillis();
         this.purge(now);
         if (this.deliveredGroups.containsKey(gid)) {
            return;
         }

         byte[] payload = Arrays.copyOfRange(frame, 23, frame.length);
         if (total == 1) {
            this.deliveredGroups.put(gid, now);
            this.deliver(topic, payload);
            return;
         }

         if (this.pending.size() >= 256 && !this.pending.containsKey(gid)) {
            return;
         }

         RelayManager.PendingGroup group = this.pending.computeIfAbsent(gid, k -> new RelayManager.PendingGroup(topic, total, now));
         byte[] complete = group.add(index, payload);
         if (complete != null) {
            this.pending.remove(gid);
            this.deliveredGroups.put(gid, now);
            this.deliver(topic, complete);
         }
      } catch (Throwable var11) {
         this.warnThrottled("Matchmaking: dropped a bad inbound frame", var11);
      }
   }

   private void deliver(String topic, byte[] envelope) {
      Consumer<byte[]> consumer = this.topicConsumers.get(topic);
      if (consumer != null) {
         try {
            consumer.accept(envelope);
         } catch (Throwable var5) {
            this.warnThrottled("Matchmaking: inbound handler threw on a frame", var5);
         }
      }
   }

   private void warnThrottled(String msg, Throwable t) {
      long now = System.currentTimeMillis();
      if (now - this.lastWarnMs >= 1000L) {
         this.lastWarnMs = now;
         riptide.RiptideClientAddon.LOG.warn(msg, t);
      }
   }

   private void purge(long now) {
      this.pending.values().removeIf(g -> now - g.createdMs > 60000L);
      this.deliveredGroups.values().removeIf(t -> now - t > 180000L);
   }

   private static void putU16(byte[] b, int off, int v) {
      b[off] = (byte)(v >>> 8 & 0xFF);
      b[off + 1] = (byte)(v & 0xFF);
   }

   private static int getU16(byte[] b, int off) {
      return (b[off] & 0xFF) << 8 | b[off + 1] & 0xFF;
   }

   private static final class PendingGroup {
      final String topic;
      final int total;
      final long createdMs;
      final byte[][] chunks;
      int received;

      PendingGroup(String topic, int total, long createdMs) {
         this.topic = topic;
         this.total = total;
         this.createdMs = createdMs;
         this.chunks = new byte[total][];
      }

      synchronized byte[] add(int index, byte[] payload) {
         if (this.chunks[index] != null) {
            return null;
         } else {
            this.chunks[index] = payload;
            if (++this.received < this.total) {
               return null;
            } else {
               int size = 0;

               for (byte[] c : this.chunks) {
                  size += c.length;
               }

               byte[] out = new byte[size];
               int off = 0;

               for (byte[] c : this.chunks) {
                  System.arraycopy(c, 0, out, off, c.length);
                  off += c.length;
               }

               return out;
            }
         }
      }
   }
}
