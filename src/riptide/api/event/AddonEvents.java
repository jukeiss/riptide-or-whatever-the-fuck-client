package riptide.api.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import riptide.addons.AddonManager;
import riptide.modules.PackHideState;

public final class AddonEvents {
   private static final List<AddonEvents.OwnedConsumer<Minecraft>> TICK = new CopyOnWriteArrayList<>();
   private static final List<AddonEvents.OwnedPredicate<Packet<?>>> PACKET_SEND = new CopyOnWriteArrayList<>();
   private static final List<AddonEvents.OwnedConsumer<Packet<?>>> PACKET_RECEIVE = new CopyOnWriteArrayList<>();
   private static final List<AddonEvents.OwnedRunnable> GAME_JOIN = new CopyOnWriteArrayList<>();
   private static final List<AddonEvents.OwnedRunnable> GAME_LEFT = new CopyOnWriteArrayList<>();
   private static final long ERROR_LOG_INTERVAL_MS = 5000L;
   private static final Map<String, Long> LAST_ERROR_LOG_MS = new ConcurrentHashMap<>();

   private AddonEvents() {
   }

   public static void onTick(Consumer<Minecraft> listener) {
      String owner = ownerOrReject("tick");
      if (owner != null && listener != null) {
         TICK.add(new AddonEvents.OwnedConsumer<>(owner, listener));
         AddonManager.recordAcceptedRegistration("event", "tick");
      }
   }

   public static void onPacketSend(Predicate<Packet<?>> listener) {
      String owner = ownerOrReject("packetSend");
      if (owner != null && listener != null) {
         PACKET_SEND.add(new AddonEvents.OwnedPredicate<>(owner, listener));
         AddonManager.recordAcceptedRegistration("event", "packetSend");
      }
   }

   public static void onPacketReceive(Consumer<Packet<?>> listener) {
      String owner = ownerOrReject("packetReceive");
      if (owner != null && listener != null) {
         PACKET_RECEIVE.add(new AddonEvents.OwnedConsumer<>(owner, listener));
         AddonManager.recordAcceptedRegistration("event", "packetReceive");
      }
   }

   public static void onGameJoin(Runnable listener) {
      String owner = ownerOrReject("gameJoin");
      if (owner != null && listener != null) {
         GAME_JOIN.add(new AddonEvents.OwnedRunnable(owner, listener));
         AddonManager.recordAcceptedRegistration("event", "gameJoin");
      }
   }

   public static void onGameLeft(Runnable listener) {
      String owner = ownerOrReject("gameLeft");
      if (owner != null && listener != null) {
         GAME_LEFT.add(new AddonEvents.OwnedRunnable(owner, listener));
         AddonManager.recordAcceptedRegistration("event", "gameLeft");
      }
   }

   private static String ownerOrReject(String event) {
      String owner = AddonManager.currentAddonId();
      if (owner != null && !owner.isBlank()) {
         return owner;
      } else {
         riptide.RiptideClientAddon.LOG.warn("[Addons] Rejecting {} listener outside an addon lifecycle", event);
         AddonManager.recordRejectedRegistration(
            owner, "event", event, "registration outside an addon lifecycle - register from onInitialize() or onRegisterCategories()"
         );
         return null;
      }
   }

   public static void unregisterAddon(String addonId) {
      if (addonId != null && !addonId.isBlank()) {
         TICK.removeIf(listener -> addonId.equals(listener.owner()));
         PACKET_SEND.removeIf(listener -> addonId.equals(listener.owner()));
         PACKET_RECEIVE.removeIf(listener -> addonId.equals(listener.owner()));
         GAME_JOIN.removeIf(listener -> addonId.equals(listener.owner()));
         GAME_LEFT.removeIf(listener -> addonId.equals(listener.owner()));
         LAST_ERROR_LOG_MS.keySet().removeIf(key -> key.startsWith(addonId + ":"));
      }
   }

   public static void fireTick(Minecraft mc) {
      if (!PackHideState.isHardLocked()) {
         if (!TICK.isEmpty()) {
            for (AddonEvents.OwnedConsumer<Minecraft> l : TICK) {
               try {
                  l.listener().accept(mc);
               } catch (Throwable var4) {
                  logThrottled(l.owner(), "tick", var4);
               }
            }
         }
      }
   }

   public static boolean hasTickListeners() {
      return PackHideState.isHardLocked() ? false : !TICK.isEmpty();
   }

   public static boolean hasPacketListeners() {
      return PackHideState.isHardLocked() ? false : !PACKET_SEND.isEmpty() || !PACKET_RECEIVE.isEmpty();
   }

   public static boolean firePacketSend(Packet<?> packet) {
      if (PackHideState.isHardLocked()) {
         return false;
      } else if (PACKET_SEND.isEmpty()) {
         return false;
      } else {
         boolean cancel = false;

         for (AddonEvents.OwnedPredicate<Packet<?>> l : PACKET_SEND) {
            try {
               cancel |= l.listener().test(packet);
            } catch (Throwable var5) {
               logThrottled(l.owner(), "packetSend", var5);
            }
         }

         return cancel;
      }
   }

   public static void firePacketReceive(Packet<?> packet) {
      if (!PackHideState.isHardLocked()) {
         if (!PACKET_RECEIVE.isEmpty()) {
            for (AddonEvents.OwnedConsumer<Packet<?>> l : PACKET_RECEIVE) {
               try {
                  l.listener().accept(packet);
               } catch (Throwable var4) {
                  logThrottled(l.owner(), "packetReceive", var4);
               }
            }
         }
      }
   }

   public static void fireGameJoin() {
      if (!PackHideState.isHardLocked()) {
         if (!GAME_JOIN.isEmpty()) {
            for (AddonEvents.OwnedRunnable l : GAME_JOIN) {
               try {
                  l.listener().run();
               } catch (Throwable var3) {
                  logThrottled(l.owner(), "gameJoin", var3);
               }
            }
         }
      }
   }

   public static void fireGameLeft() {
      if (!PackHideState.isHardLocked()) {
         if (!GAME_LEFT.isEmpty()) {
            for (AddonEvents.OwnedRunnable l : GAME_LEFT) {
               try {
                  l.listener().run();
               } catch (Throwable var3) {
                  logThrottled(l.owner(), "gameLeft", var3);
               }
            }
         }
      }
   }

   private static void logThrottled(String owner, String event, Throwable t) {
      long now = System.currentTimeMillis();
      String key = owner + ":" + event + ":" + t.getClass().getName();
      Long last = LAST_ERROR_LOG_MS.get(key);
      if (last == null || now - last >= 5000L) {
         LAST_ERROR_LOG_MS.put(key, now);
         AddonManager.recordRuntimeError(owner, event + " listener threw " + t.getClass().getSimpleName());
         riptide.RiptideClientAddon.LOG.warn("[Addons] Addon '{}' {} event listener threw", new Object[]{owner, event, t});
      }
   }

   private record OwnedConsumer<T>(String owner, Consumer<T> listener) {
   }

   private record OwnedPredicate<T>(String owner, Predicate<T> listener) {
   }

   private record OwnedRunnable(String owner, Runnable listener) {
   }
}
