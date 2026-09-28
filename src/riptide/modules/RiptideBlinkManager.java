package riptide.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

public final class RiptideBlinkManager {
   private static final Minecraft MC = Minecraft.getInstance();
   private static volatile boolean blinkIncoming;
   private static volatile boolean blinkOutgoing;
   private static volatile boolean autoResetEnabled;
   private static volatile int autoResetTicks = 100;
   private static volatile boolean holdMovement = true;
   private static volatile boolean holdActions = true;
   private static volatile boolean hasServerPos;
   private static volatile boolean showPosition = true;
   private static volatile double serverX;
   private static volatile double serverY;
   private static volatile double serverZ;
   private static volatile float serverYaw;
   private static volatile float serverPitch;
   private static volatile float serverHeadYaw;
   private static volatile float serverBodyYaw;
   private static RiptideBlinkFakePlayer clone;
   private static boolean cloneDirty;
   private static final Queue<Packet<?>> INCOMING = new ConcurrentLinkedQueue<>();
   private static final Queue<Packet<?>> OUTGOING = new ConcurrentLinkedQueue<>();
   private static final Set<Packet<?>> PASS_THROUGH = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
   private static final AtomicInteger PASS_THROUGH_COUNT = new AtomicInteger();
   private static volatile boolean flushIncomingNow;
   private static int heldTicks;
   private static final List<RiptideBlinkManager.HoldPolicy> POLICIES = new CopyOnWriteArrayList<>();
   private static volatile boolean pendingFlushIncoming;
   private static volatile boolean pendingFlushOutgoing;
   private static volatile long lastErrorLogMs;
   private static final long ERROR_LOG_INTERVAL_MS = 5000L;

   private RiptideBlinkManager() {
   }

   public static void addPolicy(RiptideBlinkManager.HoldPolicy policy) {
      if (policy != null && !POLICIES.contains(policy)) {
         POLICIES.add(policy);
      }
   }

   public static void removePolicy(RiptideBlinkManager.HoldPolicy policy) {
      if (policy != null) {
         POLICIES.remove(policy);
      }
   }

   public static void requestFlush(boolean incoming, boolean outgoing) {
      if (incoming) {
         pendingFlushIncoming = true;
      }

      if (outgoing) {
         pendingFlushOutgoing = true;
      }
   }

   public static List<Vec3> heldOutgoingPositions() {
      List<Vec3> positions = new ArrayList<>();

      for (Packet<?> packet : OUTGOING) {
         if (packet instanceof ServerboundMovePlayerPacket move && move.hasPosition()) {
            positions.add(new Vec3(move.getX(0.0), move.getY(0.0), move.getZ(0.0)));
         }
      }

      return positions;
   }

   private static RiptideBlinkManager.Hold classify(Packet<?> packet, boolean incoming) {
      RiptideBlinkManager.Hold merged = RiptideBlinkManager.Hold.FLUSH;

      for (RiptideBlinkManager.HoldPolicy policy : POLICIES) {
         RiptideBlinkManager.Hold hold;
         try {
            hold = policy.classify(packet, incoming);
         } catch (Throwable var7) {
            continue;
         }

         if (hold != null && hold.priority > merged.priority) {
            merged = hold;
         }
      }

      return merged;
   }

   public static void setDirections(boolean incoming, boolean outgoing) {
      blinkIncoming = incoming;
      blinkOutgoing = outgoing;
   }

   public static void setAutoReset(boolean enabled, int ticks) {
      autoResetEnabled = enabled;
      autoResetTicks = Math.max(1, ticks);
   }

   public static void setScope(boolean movement, boolean actions) {
      holdMovement = movement;
      holdActions = actions;
   }

   public static void setShowPosition(boolean show) {
      showPosition = show;
   }

   public static boolean isActive() {
      return blinkIncoming || blinkOutgoing;
   }

   public static boolean holdsActionsWithoutMovement() {
      return holdsActionsWithoutMovement(blinkOutgoing, holdMovement, holdActions);
   }

   static boolean holdsActionsWithoutMovement(boolean outgoing, boolean movement, boolean actions) {
      return outgoing && actions && !movement;
   }

   public static int held() {
      return INCOMING.size() + OUTGOING.size();
   }

   public static int ticksUntilReset() {
      return autoResetEnabled && held() > 0 ? Math.max(0, autoResetTicks - heldTicks) : -1;
   }

   public static void captureServerPos() {
      LocalPlayer player = MC.player;
      if (player == null) {
         hasServerPos = false;
      } else {
         serverX = player.getX();
         serverY = player.getY();
         serverZ = player.getZ();
         serverYaw = player.getYRot();
         serverPitch = player.getXRot();
         serverHeadYaw = player.yHeadRot;
         serverBodyYaw = player.yBodyRot;
         hasServerPos = true;
         cloneDirty = true;
      }
   }

   private static void updateClone() {
      boolean want = blinkOutgoing && holdMovement && showPosition && hasServerPos && MC.player != null && MC.level != null;
      if (!want) {
         despawnClone();
      } else if (clone != null && !clone.isRemoved() && clone.level() == MC.level) {
         if (cloneDirty) {
            positionClone(clone);
            cloneDirty = false;
         }
      } else {
         spawnClone();
      }
   }

   private static void spawnClone() {
      despawnClone();
      LocalPlayer player = MC.player;
      ClientLevel level = MC.level;
      if (player != null && level != null) {
         try {
            RiptideBlinkFakePlayer fake = new RiptideBlinkFakePlayer(level, player);
            positionClone(fake);
            level.addEntity(fake);
            clone = fake;
            cloneDirty = false;
         } catch (Throwable var3) {
            clone = null;
            riptide.RiptideClientAddon.LOG.warn("[Riptide] Blink clone spawn failed", var3);
         }
      }
   }

   private static void positionClone(RiptideBlinkFakePlayer fake) {
      fake.snapTo(serverX, serverY, serverZ, serverYaw, serverPitch);
      fake.freezeHeadRotation(serverHeadYaw, serverBodyYaw);
   }

   private static void despawnClone() {
      if (clone != null) {
         try {
            clone.discard();
         } catch (Throwable var1) {
         }

         clone = null;
      }
   }

   public static void disableAndFlush() {
      blinkIncoming = false;
      blinkOutgoing = false;
      autoResetEnabled = false;
      flushAll();
      heldTicks = 0;
      hasServerPos = false;
   }

   public static void clear() {
      INCOMING.clear();
      OUTGOING.clear();
      PASS_THROUGH.clear();
      PASS_THROUGH_COUNT.set(0);
      flushIncomingNow = false;
      pendingFlushIncoming = false;
      pendingFlushOutgoing = false;
      heldTicks = 0;
      hasServerPos = false;
   }

   public static boolean interceptInbound(Packet<?> packet) {
      if (!POLICIES.isEmpty() && classify(packet, true) == RiptideBlinkManager.Hold.QUEUE) {
         if (isIncomingNeverHold(packet) || isIncomingConnectionCritical(packet)) {
            return false;
         } else if (isIncomingFlushTrigger(packet)) {
            pendingFlushIncoming = true;
            return false;
         } else {
            INCOMING.add(packet);
            return true;
         }
      } else if (!blinkIncoming || MC.player == null) {
         return false;
      } else if (isIncomingNeverHold(packet) || isIncomingConnectionCritical(packet)) {
         return false;
      } else if (isIncomingFlushTrigger(packet)) {
         INCOMING.add(packet);
         flushIncomingNow = true;
         return true;
      } else {
         INCOMING.add(packet);
         return true;
      }
   }

   public static boolean interceptOutbound(Packet<?> packet) {
      if (consumePassThrough(packet)) {
         return false;
      } else if (!POLICIES.isEmpty() && classify(packet, false) == RiptideBlinkManager.Hold.QUEUE) {
         if (!isOutgoingNeverHold(packet) && !isOutgoingConnectionCritical(packet)) {
            OUTGOING.add(packet);
            return true;
         } else {
            return false;
         }
      } else if (!blinkOutgoing || MC.player == null) {
         return false;
      } else if (isOutgoingNeverHold(packet) || isOutgoingConnectionCritical(packet)) {
         return false;
      } else if (!isInOutboundScope(packet)) {
         return false;
      } else {
         OUTGOING.add(packet);
         return true;
      }
   }

   private static boolean isInOutboundScope(Packet<?> packet) {
      return holdMovement && packet instanceof ServerboundMovePlayerPacket ? true : holdActions && isActionPacket(packet);
   }

   private static boolean isActionPacket(Packet<?> packet) {
      return packet instanceof ServerboundPlayerActionPacket
         || packet instanceof ServerboundUseItemOnPacket
         || packet instanceof ServerboundUseItemPacket
         || packet instanceof ServerboundInteractPacket
         || packet instanceof ServerboundSwingPacket;
   }

   public static void onPacketProcessFrame() {
      if (!POLICIES.isEmpty() || pendingFlushIncoming || pendingFlushOutgoing) {
         if (MC.getConnection() != null) {
            if (!POLICIES.isEmpty()) {
               if (!blinkIncoming && classify(null, true) == RiptideBlinkManager.Hold.FLUSH) {
                  pendingFlushIncoming = true;
               }

               if (!blinkOutgoing && classify(null, false) == RiptideBlinkManager.Hold.FLUSH) {
                  pendingFlushOutgoing = true;
               }
            }

            if (pendingFlushIncoming) {
               pendingFlushIncoming = false;
               flushIncoming();
            }

            if (pendingFlushOutgoing) {
               pendingFlushOutgoing = false;
               if (!blinkOutgoing) {
                  flushOutgoing();
               }
            }
         }
      }
   }

   public static void tick() {
      if (!blinkIncoming && !blinkOutgoing && INCOMING.isEmpty() && OUTGOING.isEmpty() && PASS_THROUGH_COUNT.get() == 0 && !flushIncomingNow && clone == null) {
         heldTicks = 0;
      } else if (MC.getConnection() != null) {
         if (flushIncomingNow) {
            flushIncomingNow = false;
            flushIncoming();
         }

         if (held() > 0) {
            heldTicks++;
            if (autoResetEnabled && heldTicks >= autoResetTicks) {
               flushAll();
               heldTicks = 0;
               captureServerPos();
            }
         } else {
            heldTicks = 0;
         }

         updateClone();
      } else {
         if (held() > 0 || PASS_THROUGH_COUNT.get() > 0) {
            clear();
         }

         despawnClone();
      }
   }

   public static void flushAll() {
      flushIncoming();
      flushOutgoing();
   }

   public static void flushIncoming() {
      ClientPacketListener connection = MC.getConnection();

      Packet<?> packet;
      while ((packet = INCOMING.poll()) != null) {
         if (connection != null) {
            deliverIncoming(connection, packet);
         }
      }
   }

   public static void flushOutgoing() {
      ClientPacketListener connection = MC.getConnection();

      Packet<?> packet;
      while ((packet = OUTGOING.poll()) != null) {
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

   private static void deliverIncoming(ClientPacketListener connection, Packet<?> packet) {
      try {
         packet.handle(connection);
      } catch (RunningOnDifferentThreadException var3) {
      } catch (Throwable var4) {
         logRedispatchError("incoming", packet, var4);
      }
   }

   private static void logRedispatchError(String direction, Packet<?> packet, Throwable t) {
      if (!(t instanceof RunningOnDifferentThreadException) && !(t.getCause() instanceof RunningOnDifferentThreadException)) {
         long now = System.currentTimeMillis();
         if (now - lastErrorLogMs >= 5000L) {
            lastErrorLogMs = now;
            riptide.RiptideClientAddon.LOG
               .warn("[Riptide] Blink re-dispatch ({}) failed for {}", new Object[]{direction, packet.getClass().getSimpleName(), t});
         }
      }
   }

   private static boolean isIncomingNeverHold(Packet<?> packet) {
      return packet instanceof ClientboundSystemChatPacket
         || packet instanceof ClientboundDisguisedChatPacket
         || packet instanceof ClientboundSoundPacket sound && sound.getSound().value() == SoundEvents.PLAYER_HURT;
   }

   private static boolean isIncomingConnectionCritical(Packet<?> packet) {
      return packet instanceof ClientboundKeepAlivePacket || packet instanceof ClientboundPingPacket;
   }

   private static boolean isIncomingFlushTrigger(Packet<?> packet) {
      return packet instanceof ClientboundPlayerPositionPacket
         || packet instanceof ClientboundRespawnPacket
         || packet instanceof ClientboundLoginPacket
         || packet instanceof ClientboundDisconnectPacket
         || packet instanceof ClientboundSetHealthPacket health && health.getHealth() <= 0.0F;
   }

   private static boolean isOutgoingNeverHold(Packet<?> packet) {
      return packet instanceof ServerboundChatPacket || packet instanceof ServerboundChatCommandPacket;
   }

   private static boolean isOutgoingConnectionCritical(Packet<?> packet) {
      return packet instanceof ServerboundKeepAlivePacket || packet instanceof ServerboundPongPacket || packet instanceof ServerboundResourcePackPacket;
   }

   public static enum Hold {
      FLUSH(0),
      PASS(1),
      QUEUE(2);

      private final int priority;

      private Hold(int priority) {
         this.priority = priority;
      }
   }

   public interface HoldPolicy {
      RiptideBlinkManager.Hold classify(Packet<?> var1, boolean var2);
   }
}
