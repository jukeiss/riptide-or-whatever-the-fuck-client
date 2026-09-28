package riptide.util.mm;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MmSafety {
   private static final long TIMESTAMP_WINDOW_MS = 120000L;
   private static final long MSGID_TTL_MS = 300000L;
   private static final int BUCKET_CAPACITY = 24;
   private static final double REFILL_PER_SEC = 6.0;
   private static final int MAX_MSGIDS = 8192;
   private static final int MAX_SENDERS = 4096;
   private static final long SENDER_IDLE_MS = 600000L;
   private static final int GLOBAL_CAPACITY = 256;
   private static final double GLOBAL_REFILL_PER_SEC = 128.0;
   private final Map<String, Long> seenMsgIds = new ConcurrentHashMap<>();
   private final Map<String, MmSafety.TokenBucket> buckets = new ConcurrentHashMap<>();
   private final MmSafety.TokenBucket globalGate = new MmSafety.TokenBucket(System.currentTimeMillis(), 256.0, 128.0);
   public final AtomicLong droppedReplay = new AtomicLong();
   public final AtomicLong droppedStale = new AtomicLong();
   private volatile long lastStaleLogMs;
   public final AtomicLong droppedFlood = new AtomicLong();
   public final AtomicLong droppedOversize = new AtomicLong();
   public final AtomicLong droppedAuth = new AtomicLong();

   public static int maxSizeFor(MmMessageType type) {
      if (type == null) {
         return 0;
      } else {
         return switch (type) {
            case CHAT -> 2048;
            case COMMAND_OFFER -> 4096;
            case MACRO_OFFER, PACKET_OFFER -> 32768;
            case BLOB_OFFER -> 61440;
            case PRESENCE -> 2048;
            case LOCATION -> 256;
            case LEAVE -> 64;
            case KICK -> 64;
            case RECEIPT -> 64;
         };
      }
   }

   public boolean withinSizeLimit(MmMessageType type, int payloadLen) {
      boolean ok = payloadLen <= maxSizeFor(type);
      if (!ok) {
         this.droppedOversize.incrementAndGet();
      }

      return ok;
   }

   public boolean admitPreDecrypt() {
      if (!this.globalGate.tryConsume(System.currentTimeMillis())) {
         this.droppedFlood.incrementAndGet();
         return false;
      } else {
         return true;
      }
   }

   public boolean accept(MmEnvelope env) {
      long now = System.currentTimeMillis();
      this.purge(now);
      long serverNow = ServerClock.nowMs();
      if (Math.abs(serverNow - env.timestamp) > 120000L) {
         this.droppedStale.incrementAndGet();
         if (now - this.lastStaleLogMs > 10000L) {
            this.lastStaleLogMs = now;
            riptide.RiptideClientAddon.LOG
               .info(
                  "[mm-safety] dropped stale frame: sender timestamp {}s {} server time (limit ±120s)",
                  Math.abs(env.timestamp - serverNow) / 1000L,
                  env.timestamp > serverNow ? "ahead of" : "behind"
               );
         }

         return false;
      } else {
         String fp = env.senderFpHex();
         if (!this.withinSizeLimit(env.type(), env.payload.length)) {
            return false;
         } else {
            String msgKey = fp + ":" + bytesHex(env.msgId);
            if (this.seenMsgIds.putIfAbsent(msgKey, now) != null) {
               this.droppedReplay.incrementAndGet();
               return false;
            } else {
               MmSafety.TokenBucket bucket = this.buckets.computeIfAbsent(fp, k -> new MmSafety.TokenBucket(now, 24.0, 6.0));
               if (!bucket.tryConsume(now)) {
                  this.droppedFlood.incrementAndGet();
                  return false;
               } else {
                  return true;
               }
            }
         }
      }
   }

   public void forgetPeer(String fpHex) {
      this.buckets.remove(fpHex);
      this.seenMsgIds.keySet().removeIf(k -> k.startsWith(fpHex + ":"));
   }

   private void purge(long now) {
      this.seenMsgIds.values().removeIf(t -> now - t > 300000L);
      if (this.seenMsgIds.size() > 8192) {
         evictOldestByValue(this.seenMsgIds, 8192);
      }

      if (this.buckets.size() > 4096) {
         this.buckets.entrySet().removeIf(e -> now - e.getValue().lastActivityMs() > 600000L);
         if (this.buckets.size() > 4096) {
            this.evictOldestBuckets(4096);
         }
      }
   }

   private static void evictOldestByValue(Map<String, Long> map, int cap) {
      int over = map.size() - cap;
      if (over > 0) {
         List<String> victims = map.entrySet().stream().sorted(Entry.comparingByValue()).limit(over).map(Entry::getKey).toList();
         victims.forEach(map::remove);
      }
   }

   private void evictOldestBuckets(int cap) {
      int over = this.buckets.size() - cap;
      if (over > 0) {
         List<String> victims = this.buckets
            .entrySet()
            .stream()
            .sorted(Comparator.comparingLong(e -> e.getValue().lastActivityMs()))
            .limit(over)
            .map(Entry::getKey)
            .toList();
         victims.forEach(this.buckets::remove);
      }
   }

   private static String bytesHex(byte[] b) {
      StringBuilder sb = new StringBuilder(b.length * 2);

      for (byte x : b) {
         sb.append(Character.forDigit(x >> 4 & 15, 16)).append(Character.forDigit(x & 15, 16));
      }

      return sb.toString();
   }

   private static final class TokenBucket {
      private final double capacity;
      private final double refillPerSec;
      private double tokens;
      private long lastRefillMs;

      TokenBucket(long now, double capacity, double refillPerSec) {
         this.capacity = capacity;
         this.refillPerSec = refillPerSec;
         this.tokens = capacity;
         this.lastRefillMs = now;
      }

      long lastActivityMs() {
         return this.lastRefillMs;
      }

      synchronized boolean tryConsume(long now) {
         double elapsed = (now - this.lastRefillMs) / 1000.0;
         if (elapsed > 0.0) {
            this.tokens = Math.min(this.capacity, this.tokens + elapsed * this.refillPerSec);
            this.lastRefillMs = now;
         }

         if (this.tokens >= 1.0) {
            this.tokens--;
            return true;
         } else {
            return false;
         }
      }
   }
}
