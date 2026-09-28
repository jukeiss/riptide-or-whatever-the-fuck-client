package riptide.util.mm.relay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import riptide.util.mm.MmPrefs;
import riptide.util.mm.ServerClock;
import riptide.util.mm.crypto.MmCrypto;

public final class MqttRelay implements Relay {
   private static final long ROTATE_LEAD_MS = 120000L;
   private static final long ROTATE_JITTER_MS = 20000L;
   private static final int MAX_QUEUED_FRAMES = 256;
   private static final long QUEUE_TTL_MS = 90000L;
   private final String serverUri;
   private final RelayStatus status;
   private final Map<String, Consumer<byte[]>> subs = new ConcurrentHashMap<>();
   private final Supplier<String> tokenSupplier;
   private final Supplier<String> usernameSupplier;
   private final ScheduledExecutorService reconnects;
   private final AtomicInteger backoff = new AtomicInteger();
   private final String stableClientId;
   private final ArrayDeque<MqttRelay.Queued> queue = new ArrayDeque<>();
   private volatile MqttAsyncClient client;
   private volatile ScheduledFuture<?> rotation;
   private volatile boolean closed;

   private MqttRelay(String name, String serverUri, Supplier<String> tokenSupplier, Supplier<String> usernameSupplier) {
      this.status = new RelayStatus(name);
      this.serverUri = serverUri;
      this.tokenSupplier = tokenSupplier;
      this.usernameSupplier = usernameSupplier;
      this.stableClientId = "mm" + MmCrypto.hex(MmCrypto.randomBytes(16));
      this.reconnects = Executors.newSingleThreadScheduledExecutor(r -> {
         Thread t = new Thread(r, "mm-mqtt-reconnect");
         t.setDaemon(true);
         return t;
      });
      this.reconnects.execute(this::connect);
   }

   public static MqttRelay authed(String name, String serverUri, Supplier<String> tokenSupplier, Supplier<String> usernameSupplier) {
      return new MqttRelay(name, serverUri, tokenSupplier, usernameSupplier);
   }

   @Override
   public String name() {
      return this.status.name;
   }

   @Override
   public RelayStatus status() {
      return this.status;
   }

   private boolean logDiag() {
      return MmPrefs.get().debugLog();
   }

   private synchronized void connect() {
      if (!this.closed) {
         try {
            final String jwt = this.tokenSupplier.get();
            if (jwt == null || jwt.isBlank()) {
               this.scheduleReconnect();
               return;
            }

            String user = this.usernameSupplier == null ? null : this.usernameSupplier.get();
            MqttAsyncClient old = this.client;
            if (old != null) {
               quietClose(old);
            }

            final MqttAsyncClient c = new MqttAsyncClient(this.serverUri, this.stableClientId, new MemoryPersistence());
            c.setCallback(
               new MqttCallbackExtended() {
                  {
                     Objects.requireNonNull(MqttRelay.this);
                  }

                  public void connectComplete(boolean reconnect, String uri) {
                     if (MqttRelay.this.closed) {
                        MqttRelay.quietClose(c);
                     } else if (c == MqttRelay.this.client) {
                        MqttRelay.this.status.connected = true;
                        MqttRelay.this.status.lastError = "";
                        MqttRelay.this.status.markActivity();

                        for (String topic : MqttRelay.this.subs.keySet()) {
                           MqttRelay.this.trySubscribe(topic);
                        }

                        MqttRelay.this.flushQueued(c);
                     }
                  }

                  public void connectionLost(Throwable cause) {
                     if (MqttRelay.this.closed) {
                        MqttRelay.quietClose(c);
                     } else if (c == MqttRelay.this.client) {
                        if (MqttRelay.this.logDiag()) {
                           riptide.RiptideClientAddon.LOG
                              .info("[mm-relay] {} connection lost: {}", MqttRelay.this.status.name, cause == null ? "?" : String.valueOf(cause.getMessage()));
                        }

                        MqttRelay.this.status.connected = false;
                        MqttRelay.this.status.lastError = cause == null ? "lost" : String.valueOf(cause.getMessage());
                        MqttRelay.this.scheduleReconnect();
                     }
                  }

                  public void messageArrived(String topic, MqttMessage message) {
                     Consumer<byte[]> consumer = MqttRelay.this.subs.get(topic);
                     if (consumer != null) {
                        MqttRelay.this.status.received.incrementAndGet();
                        MqttRelay.this.status.markActivity();

                        try {
                           consumer.accept(message.getPayload());
                        } catch (Throwable var5) {
                           riptide.RiptideClientAddon.LOG.debug("MQTT inbound handler threw (dropped)", var5);
                        }
                     }
                  }

                  public void deliveryComplete(IMqttDeliveryToken token) {
                  }
               }
            );
            this.client = c;
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setCleanSession(false);
            opts.setAutomaticReconnect(false);
            opts.setUserName(user != null && !user.isBlank() ? user : "jwt");
            opts.setPassword((jwt == null ? "" : jwt).toCharArray());
            opts.setConnectionTimeout(15);
            opts.setKeepAliveInterval(30);
            opts.setMaxInflight(64);
            final long runwayS = jwt == null ? -1L : (jwtExpMs(jwt) - ServerClock.nowMs()) / 1000L;
            if (this.logDiag()) {
               riptide.RiptideClientAddon.LOG
                  .info("[mm-relay] {} connecting (tokenRunway={}s, subs={})", new Object[]{this.status.name, runwayS, this.subs.size()});
            }

            c.connect(
               opts,
               null,
               new IMqttActionListener() {
                  {
                     Objects.requireNonNull(MqttRelay.this);
                  }

                  public void onSuccess(IMqttToken asyncActionToken) {
                     if (MqttRelay.this.closed) {
                        MqttRelay.quietClose(c);
                     } else if (c == MqttRelay.this.client) {
                        boolean resumed = false;

                        try {
                           resumed = asyncActionToken != null && asyncActionToken.getSessionPresent();
                        } catch (Throwable var5) {
                        }

                        if (MqttRelay.this.logDiag()) {
                           riptide.RiptideClientAddon.LOG
                              .info("[mm-relay] {} connected (sessionPresent={}, tokenRunway={}s)", new Object[]{MqttRelay.this.status.name, resumed, runwayS});
                        }

                        MqttRelay.this.backoff.set(0);
                        MqttRelay.this.status.connected = true;
                        MqttRelay.this.status.lastError = "";
                        MqttRelay.this.status.markActivity();

                        for (String topic : MqttRelay.this.subs.keySet()) {
                           MqttRelay.this.trySubscribe(topic);
                        }

                        MqttRelay.this.flushQueued(c);
                        MqttRelay.this.scheduleRotation(jwt);
                     }
                  }

                  public void onFailure(IMqttToken asyncActionToken, Throwable e) {
                     if (MqttRelay.this.closed) {
                        MqttRelay.quietClose(c);
                     } else if (c == MqttRelay.this.client) {
                        if (MqttRelay.this.logDiag()) {
                           riptide.RiptideClientAddon.LOG
                              .info("[mm-relay] {} connect FAILED: {}", MqttRelay.this.status.name, e == null ? "?" : String.valueOf(e.getMessage()));
                        }

                        MqttRelay.this.status.connected = false;
                        MqttRelay.this.status.lastError = e == null ? "connect failed" : String.valueOf(e.getMessage());
                        MqttRelay.this.scheduleReconnect();
                     }
                  }
               }
            );
         } catch (Throwable var9) {
            this.status.lastError = String.valueOf(var9.getMessage());
            this.scheduleReconnect();
         }
      }
   }

   @Override
   public void reconnect() {
      if (!this.closed) {
         this.backoff.set(0);
         this.reconnects.execute(this::connect);
      }
   }

   private void scheduleReconnect() {
      if (!this.closed) {
         int attempt = this.backoff.incrementAndGet();
         long ceil = Math.min(30000L, 1000L * (1L << Math.min(attempt, 5)));
         long delay = 250L + (long)(ThreadLocalRandom.current().nextDouble() * (ceil - 250L));

         try {
            this.reconnects.schedule(() -> {
               if (!this.closed) {
                  this.connect();
               }
            }, delay, TimeUnit.MILLISECONDS);
         } catch (Throwable var7) {
         }
      }
   }

   private void scheduleRotation(String jwt) {
      long expMs = jwtExpMs(jwt);
      if (expMs > 0L) {
         ScheduledFuture<?> prev = this.rotation;
         if (prev != null) {
            prev.cancel(false);
         }

         long jitter = (long)(ThreadLocalRandom.current().nextDouble() * 20000.0);
         long runway = expMs - ServerClock.nowMs();
         long delay = runway > 145000L ? runway - 120000L - jitter : Math.max(20000L, runway / 2L);

         try {
            this.rotation = this.reconnects.schedule(() -> {
               if (!this.closed) {
                  this.backoff.set(0);
                  this.connect();
               }
            }, delay, TimeUnit.MILLISECONDS);
            if (this.logDiag()) {
               riptide.RiptideClientAddon.LOG.info("[mm-relay] {} token rotation in {}s", this.status.name, delay / 1000L);
            }
         } catch (Throwable var12) {
         }
      }
   }

   private static long jwtExpMs(String jwt) {
      if (jwt != null && !jwt.isEmpty()) {
         try {
            int d1 = jwt.indexOf(46);
            int d2 = jwt.indexOf(46, d1 + 1);
            if (d1 > 0 && d2 > d1) {
               byte[] payload = Base64.getUrlDecoder().decode(jwt.substring(d1 + 1, d2));
               JsonObject o = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
               return o.has("exp") ? o.get("exp").getAsLong() * 1000L : 0L;
            } else {
               return 0L;
            }
         } catch (Throwable var5) {
            return 0L;
         }
      } else {
         return 0L;
      }
   }

   @Override
   public void publish(String topic, byte[] frame) {
      this.publish(topic, frame, false);
   }

   @Override
   public void publish(String topic, byte[] frame, boolean durable) {
      if (!this.closed) {
         MqttAsyncClient c = this.client;
         boolean up = c != null && c.isConnected();
         synchronized (this.queue) {
            if (!up) {
               if (durable) {
                  this.enqueueLocked(topic, frame);
               }

               return;
            }

            if (!this.queue.isEmpty()) {
               this.enqueueLocked(topic, frame);
               this.drainLocked(c);
               return;
            }
         }

         if (!this.publishNow(c, topic, frame) && durable) {
            synchronized (this.queue) {
               this.enqueueLocked(topic, frame);
            }
         }
      }
   }

   private void enqueueLocked(String topic, byte[] frame) {
      while (this.queue.size() >= 256) {
         this.queue.pollFirst();
         this.status.queueDropped.incrementAndGet();
      }

      this.queue.addLast(new MqttRelay.Queued(topic, frame, System.currentTimeMillis()));
   }

   private void drainLocked(MqttAsyncClient c) {
      long now = System.currentTimeMillis();

      MqttRelay.Queued q;
      while ((q = this.queue.pollFirst()) != null) {
         if (now - q.enqueuedMs > 90000L) {
            this.status.queueDropped.incrementAndGet();
         } else if (!this.publishNow(c, q.topic, q.frame)) {
            this.queue.addFirst(q);
            return;
         }
      }
   }

   private void flushQueued(MqttAsyncClient c) {
      synchronized (this.queue) {
         this.drainLocked(c);
      }
   }

   private boolean publishNow(MqttAsyncClient c, String topic, byte[] frame) {
      try {
         MqttMessage m = new MqttMessage(frame);
         m.setQos(0);
         m.setRetained(false);
         c.publish(topic, m);
         this.status.published.incrementAndGet();
         this.status.markActivity();
         return true;
      } catch (Throwable var5) {
         this.status.lastError = String.valueOf(var5.getMessage());
         return false;
      }
   }

   @Override
   public void subscribe(String topic, Consumer<byte[]> onFrame) {
      if (!this.closed) {
         this.subs.put(topic, onFrame);
         this.trySubscribe(topic);
      }
   }

   private void trySubscribe(final String topic) {
      MqttAsyncClient c = this.client;
      if (c != null && c.isConnected()) {
         try {
            c.subscribe(
               topic,
               0,
               null,
               new IMqttActionListener() {
                  {
                     Objects.requireNonNull(MqttRelay.this);
                  }

                  public void onSuccess(IMqttToken t) {
                     int[] q = t == null ? null : t.getGrantedQos();
                     int granted = q != null && q.length > 0 ? q[0] : -1;
                     if (MqttRelay.this.logDiag()) {
                        riptide.RiptideClientAddon.LOG
                           .info("[mm-relay] subscribe …{} -> {}", MqttRelay.tail(topic), granted >= 128 ? "DENIED" : "granted qos" + granted);
                     }
                  }

                  public void onFailure(IMqttToken t, Throwable e) {
                     if (MqttRelay.this.logDiag()) {
                        riptide.RiptideClientAddon.LOG
                           .info("[mm-relay] subscribe …{} FAILED: {}", MqttRelay.tail(topic), e == null ? "?" : String.valueOf(e.getMessage()));
                     }
                  }
               }
            );
         } catch (Throwable var4) {
            this.status.lastError = String.valueOf(var4.getMessage());
         }
      }
   }

   private static String tail(String topic) {
      return topic == null ? "?" : topic.substring(Math.max(0, topic.length() - 6));
   }

   @Override
   public void unsubscribe(String topic) {
      this.subs.remove(topic);
      MqttAsyncClient c = this.client;
      if (c != null && c.isConnected()) {
         try {
            c.unsubscribe(topic);
         } catch (Throwable var4) {
         }
      }
   }

   @Override
   public void close() {
      this.closed = true;
      ScheduledFuture<?> r = this.rotation;
      if (r != null) {
         r.cancel(false);
      }

      try {
         this.reconnects.shutdownNow();
      } catch (Throwable var3) {
      }

      MqttAsyncClient c = this.client;
      if (c != null) {
         quietClose(c);
      }

      this.status.connected = false;
   }

   private static void quietClose(MqttAsyncClient c) {
      try {
         c.disconnectForcibly(0L, 250L);
      } catch (Throwable var3) {
      }

      try {
         c.close(true);
      } catch (Throwable var2) {
      }
   }

   private static final class Queued {
      final String topic;
      final byte[] frame;
      final long enqueuedMs;

      Queued(String topic, byte[] frame, long enqueuedMs) {
         this.topic = topic;
         this.frame = frame;
         this.enqueuedMs = enqueuedMs;
      }
   }
}
