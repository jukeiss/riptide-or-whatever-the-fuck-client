package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket.Pos;

public class ServerTickTracker {
   private static final long TICK_DURATION_NANOS = 50000000L;
   private static final Minecraft MC = Minecraft.getInstance();
   private static final long MIN_WARMUP_MS = 2000L;
   private static final int MIN_SAMPLES = 40;
   private static final int WORLD_TIME_SAMPLE_COUNT = 60;
   private static final long[] worldTimeSamples = new long[60];
   private static int worldTimeSampleIndex = 0;
   private static int worldTimeSamplesFilled = 0;
   private static volatile long trackingStartTime = 0L;
   private static volatile long lastWorldTimePacket = 0L;
   private static volatile long lastServerGameTime = Long.MIN_VALUE;
   private static volatile double estimatedTps = 20.0;
   private static volatile long averagedTickPhase = 0L;
   private static volatile long baseTickTime = 0L;
   private static volatile long lastEntityPacketTime = 0L;
   private static volatile int entityPacketBurstCount = 0;
   private static final long BURST_THRESHOLD_NANOS = 5000000L;

   public static void onWorldTimePacket(ClientboundSetTimePacket packet) {
      long now = System.nanoTime();
      if (trackingStartTime == 0L) {
         trackingStartTime = now;
      }

      long previousPacketTime = lastWorldTimePacket;
      long previousGameTime = lastServerGameTime;
      lastWorldTimePacket = now;
      lastServerGameTime = packet.gameTime();
      if (previousPacketTime > 0L && previousGameTime != Long.MIN_VALUE) {
         long gameTicks = packet.gameTime() - previousGameTime;
         long elapsedNanos = now - previousPacketTime;
         if (gameTicks > 0L && gameTicks <= 200L && elapsedNanos > 0L) {
            double sample = Math.max(0.0, Math.min(20.0, gameTicks * 1.0E9 / elapsedNanos));
            estimatedTps = estimatedTps <= 0.0 ? sample : estimatedTps * 0.75 + sample * 0.25;
         }
      }

      int pingMs = getPingMs();
      long halfPingNanos = pingMs * 1000000L / 2L;
      long serverSendTime = now - halfPingNanos;
      worldTimeSamples[worldTimeSampleIndex] = serverSendTime;
      worldTimeSampleIndex = (worldTimeSampleIndex + 1) % 60;
      if (worldTimeSamplesFilled < 60) {
         worldTimeSamplesFilled++;
      }

      updateAveragedPhase();
   }

   public static void onEntityPacket() {
      long now = System.nanoTime();
      if (now - lastEntityPacketTime < 5000000L) {
         entityPacketBurstCount++;
      } else {
         entityPacketBurstCount = 1;
      }

      lastEntityPacketTime = now;
   }

   public static void onS2CPacket(Packet<?> packet) {
      if (packet instanceof ClientboundSetTimePacket worldTimePacket) {
         onWorldTimePacket(worldTimePacket);
      } else if (packet instanceof Pos || packet instanceof ClientboundSetEntityMotionPacket || packet instanceof ClientboundPlayerPositionPacket) {
         onEntityPacket();
      }
   }

   private static void updateAveragedPhase() {
      if (worldTimeSamplesFilled >= 3) {
         long sumPhase = 0L;
         int validSamples = Math.min(worldTimeSamplesFilled, 60);

         for (int i = 0; i < validSamples; i++) {
            long phase = worldTimeSamples[i] % 50000000L;
            sumPhase += phase;
         }

         averagedTickPhase = sumPhase / validSamples;
         long mostRecent = worldTimeSamples[(worldTimeSampleIndex - 1 + 60) % 60];
         long tickNumber = mostRecent / 50000000L;
         baseTickTime = tickNumber * 50000000L + averagedTickPhase;
         long now = System.nanoTime();
         if (baseTickTime > now) {
            baseTickTime -= 50000000L;
         }
      }
   }

   public static int getPingMs() {
      if (MC.getConnection() != null && MC.player != null) {
         PlayerInfo entry = MC.getConnection().getPlayerInfo(MC.player.getUUID());
         return entry == null ? 0 : entry.getLatency();
      } else {
         return 0;
      }
   }

   public static long getNextServerTickNanos() {
      if (baseTickTime == 0L) {
         return System.nanoTime() + 50000000L;
      } else {
         long now = System.nanoTime();
         long elapsed = now - baseTickTime;
         long ticksElapsed = elapsed / 50000000L;
         return baseTickTime + (ticksElapsed + 1L) * 50000000L;
      }
   }

   public static long getOptimalSendTime(int bufferMs, boolean ignorePing) {
      long nextTick = getNextServerTickNanos();
      int pingMs = ignorePing ? 0 : getPingMs();
      long halfPingNanos = pingMs * 1000000L / 2L;
      long bufferNanos = bufferMs * 1000000L;
      long optimalTime = nextTick - bufferNanos - halfPingNanos;
      long now = System.nanoTime();

      while (optimalTime <= now) {
         optimalTime += 50000000L;
      }

      return optimalTime;
   }

   public static boolean isReady() {
      if (trackingStartTime != 0L && worldTimeSamplesFilled >= 40) {
         long elapsedMs = (System.nanoTime() - trackingStartTime) / 1000000L;
         return elapsedMs >= 2000L && baseTickTime > 0L;
      } else {
         return false;
      }
   }

   public static float getWarmupProgress() {
      if (trackingStartTime == 0L) {
         return 0.0F;
      } else {
         long elapsedMs = (System.nanoTime() - trackingStartTime) / 1000000L;
         float timeProgress = Math.min(1.0F, (float)elapsedMs / 2000.0F);
         float sampleProgress = Math.min(1.0F, worldTimeSamplesFilled / 40.0F);
         return Math.min(timeProgress, sampleProgress);
      }
   }

   public static float getConfidence() {
      if (!isReady()) {
         return 0.0F;
      } else {
         float sampleConfidence = Math.min(1.0F, worldTimeSamplesFilled / 60.0F);
         long timeSinceLast = System.nanoTime() - lastWorldTimePacket;
         float freshnessConfidence = timeSinceLast < 100000000L ? 1.0F : Math.max(0.0F, 1.0F - (float)timeSinceLast / 5.0E8F);
         return sampleConfidence * freshnessConfidence;
      }
   }

   public static long getMsUntilOptimal(int bufferMs, boolean ignorePing) {
      long optimalTime = getOptimalSendTime(bufferMs, ignorePing);
      return Math.max(0L, (optimalTime - System.nanoTime()) / 1000000L);
   }

   public static long getMsUntilNextTick() {
      long nextTick = getNextServerTickNanos();
      return Math.max(0L, (nextTick - System.nanoTime()) / 1000000L);
   }

   public static long getMsSinceLastTick() {
      return lastWorldTimePacket == 0L ? 0L : Math.max(0L, (System.nanoTime() - lastWorldTimePacket) / 1000000L);
   }

   public static double getEstimatedTps() {
      return Math.max(0.0, Math.min(20.0, estimatedTps));
   }

   public static int getSampleCount() {
      return worldTimeSamplesFilled;
   }

   public static long getTrackingTimeMs() {
      return trackingStartTime == 0L ? 0L : (System.nanoTime() - trackingStartTime) / 1000000L;
   }

   public static long getAveragedPhaseMs() {
      return averagedTickPhase / 1000000L;
   }

   public static void reset() {
      worldTimeSamplesFilled = 0;
      worldTimeSampleIndex = 0;
      trackingStartTime = 0L;
      lastWorldTimePacket = 0L;
      lastServerGameTime = Long.MIN_VALUE;
      estimatedTps = 20.0;
      averagedTickPhase = 0L;
      baseTickTime = 0L;
      lastEntityPacketTime = 0L;
      entityPacketBurstCount = 0;
   }

   public static String getDebugInfo() {
      return String.format(
         "Ping: %dms | Samples: %d/%d | Phase: %dms | Confidence: %.0f%%",
         getPingMs(),
         worldTimeSamplesFilled,
         40,
         getAveragedPhaseMs(),
         getConfidence() * 100.0F
      );
   }
}
