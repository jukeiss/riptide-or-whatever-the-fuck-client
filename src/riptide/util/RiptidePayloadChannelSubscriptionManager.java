package riptide.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import riptide.modules.PackHideState;

public final class RiptidePayloadChannelSubscriptionManager {
   private static final boolean TRACE = Boolean.getBoolean("riptide.payload.trace");
   public static final int REGISTRATION_WARNING_VERSION = 1;
   private static final int DEFAULT_LIMIT = 96;
   private static final int HARD_LIMIT = 120;
   private static final int RECENT_CHANNEL_CAP = 64;
   private static volatile boolean dirty = true;
   private static int graceTicks;
   private static String lastKey = "";
   private static long lastSentMs;
   private static RiptidePayloadChannelSubscriptionManager.Status status = RiptidePayloadChannelSubscriptionManager.Status.empty(96);
   private static final LinkedHashSet<String> lastRegisteredChannels = new LinkedHashSet<>();
   private static final LinkedHashSet<String> recentChannels = new LinkedHashSet<>();
   private static final LinkedHashSet<String> observedChannels = new LinkedHashSet<>();

   private RiptidePayloadChannelSubscriptionManager() {
   }

   public static synchronized void requestRefresh() {
      dirty = true;
      graceTicks = 0;
   }

   public static synchronized void clear() {
      dirty = true;
      graceTicks = 0;
      lastKey = "";
      lastSentMs = 0L;
      recentChannels.clear();
      observedChannels.clear();
      lastRegisteredChannels.clear();
      status = RiptidePayloadChannelSubscriptionManager.Status.empty(96);
   }

   public static synchronized RiptidePayloadChannelSubscriptionManager.Status status() {
      if (PackHideState.isHardLocked()) {
         status = RiptidePayloadChannelSubscriptionManager.Status.locked(subscriptionLimit(), 0);
         return status;
      } else {
         if (!isRegistrationUnlocked() && !status.locked()) {
            status = RiptidePayloadChannelSubscriptionManager.Status.locked(subscriptionLimit(), status.lastUnregisteredCount());
         }

         return status;
      }
   }

   public static boolean isRegistrationUnlocked() {
      RiptideConfig config = RiptideConfig.getGlobal();
      return config != null && config.payloadRegistrationUnlocked && config.payloadRegistrationWarningAcceptedVersion >= 1;
   }

   public static synchronized void unlockRegistration() {
      if (!PackHideState.isHardLocked()) {
         RiptideConfig config = RiptideConfig.getGlobal();
         config.payloadRegistrationUnlocked = true;
         config.payloadRegistrationWarningAcceptedVersion = 1;
         config.save();
         requestRefresh();
         status = RiptidePayloadChannelSubscriptionManager.Status.empty(subscriptionLimit());
      }
   }

   public static void lockRegistrationAndUnregister(Minecraft mc) {
      if (PackHideState.isHardLocked()) {
         synchronized (RiptidePayloadChannelSubscriptionManager.class) {
            RiptideConfig config = RiptideConfig.getGlobal();
            config.payloadRegistrationUnlocked = false;
            config.save();
            dirty = false;
            graceTicks = 0;
            lastKey = "";
            lastSentMs = 0L;
            lastRegisteredChannels.clear();
            status = RiptidePayloadChannelSubscriptionManager.Status.locked(subscriptionLimit(), 0);
         }
      } else {
         RiptidePayloadChannelSubscriptionManager.UnregisterResult unregister = new RiptidePayloadChannelSubscriptionManager.UnregisterResult(0, true);
         if (isReady(mc)) {
            unregister = unregisterRemovedChannels(List.of());
         }

         synchronized (RiptidePayloadChannelSubscriptionManager.class) {
            RiptideConfig config = RiptideConfig.getGlobal();
            config.payloadRegistrationUnlocked = false;
            config.save();
            dirty = false;
            graceTicks = 0;
            lastKey = "";
            lastSentMs = System.currentTimeMillis();
            if (unregister.sent()) {
               lastRegisteredChannels.clear();
            }

            status = RiptidePayloadChannelSubscriptionManager.Status.locked(subscriptionLimit(), unregister.count());
         }
      }
   }

   public static synchronized void rememberRequestedChannel(String channel) {
      String normalized = normalize(channel);
      if (isRegisterableChannel(normalized)) {
         if (!isHiddenProbeChannel(normalized)) {
            addCappedRecent(recentChannels, normalized);
         }
      }
   }

   public static synchronized void rememberObservedChannel(String channel) {
      String normalized = normalize(channel);
      if (isRegisterableChannel(normalized)) {
         if (!isHiddenProbeChannel(normalized)) {
            addCappedRecent(observedChannels, normalized);
         }
      }
   }

   public static synchronized void rememberObservedPayload(RiptidePayloadSupport.PayloadSnapshot snapshot) {
      if (snapshot != null) {
         if ("S2C".equalsIgnoreCase(snapshot.direction())) {
            rememberObservedChannel(snapshot.channel());

            for (String channel : RiptidePayloadSupport.extractRegisterChannelList(snapshot.channel(), snapshot.rawBytes())) {
               String normalized = normalize(channel);
               if (isRegisterableChannel(normalized) && !isHiddenProbeChannel(normalized)) {
                  addCappedRecent(observedChannels, normalized);
               }
            }
         }
      }
   }

   public static synchronized List<String> learnedChannels() {
      LinkedHashSet<String> channels = new LinkedHashSet<>();
      channels.addAll(recentChannels);
      channels.addAll(observedChannels);
      return List.copyOf(channels);
   }

   public static void tick(Minecraft mc, boolean packetLoggerCapturing) {
      if (dirty) {
         if (!PackHideState.isHardLocked()) {
            if (isReady(mc)) {
               synchronized (RiptidePayloadChannelSubscriptionManager.class) {
                  if (graceTicks > 0) {
                     graceTicks--;
                     return;
                  }

                  if (!dirty) {
                     return;
                  }
               }

               if (!isRegistrationUnlocked()) {
                  synchronized (RiptidePayloadChannelSubscriptionManager.class) {
                     dirty = false;
                     lastKey = "";
                     lastSentMs = 0L;
                     status = RiptidePayloadChannelSubscriptionManager.Status.locked(subscriptionLimit(), 0);
                  }
               } else {
                  RiptidePayloadChannelSubscriptionManager.SubscriptionPlan plan = buildPlan(packetLoggerCapturing);
                  if (plan.channels().isEmpty()) {
                     RiptidePayloadChannelSubscriptionManager.UnregisterResult unregister = unregisterRemovedChannels(List.of());
                     synchronized (RiptidePayloadChannelSubscriptionManager.class) {
                        dirty = false;
                        lastKey = "";
                        lastSentMs = 0L;
                        status = new RiptidePayloadChannelSubscriptionManager.Status(
                           0,
                           plan.limit(),
                           plan.skippedCount(),
                           unregister.sent(),
                           System.currentTimeMillis(),
                           List.of(),
                           plan.skippedChannels(),
                           unregister.count(),
                           false
                        );
                     }
                  } else {
                     String key = String.join("\n", plan.channels());
                     synchronized (RiptidePayloadChannelSubscriptionManager.class) {
                        long now = System.currentTimeMillis();
                        if (!dirty && key.equals(lastKey)) {
                           return;
                        }

                        lastKey = key;
                        lastSentMs = now;
                        dirty = false;
                     }

                     byte[] body = registrationBody(plan.channels());
                     if (body.length != 0) {
                        RiptidePayloadChannelSubscriptionManager.UnregisterResult unregister = unregisterRemovedChannels(plan.channels());
                        boolean sent = RiptidePayloadSupport.sendPayloadSilent("minecraft:register", body, "play");
                        RiptidePayloadChannelSubscriptionManager.Status newStatus = new RiptidePayloadChannelSubscriptionManager.Status(
                           plan.channels().size(),
                           plan.limit(),
                           plan.skippedCount(),
                           sent,
                           System.currentTimeMillis(),
                           plan.channels(),
                           plan.skippedChannels(),
                           unregister.count(),
                           false
                        );
                        synchronized (RiptidePayloadChannelSubscriptionManager.class) {
                           status = newStatus;
                           if (sent) {
                              lastRegisteredChannels.clear();
                              lastRegisteredChannels.addAll(plan.channels());
                           }
                        }

                        if (TRACE) {
                           riptide.RiptideClientAddon.LOG
                              .info(
                                 "[Riptide Payload] registered {} / {} plugin channels, skipped {}, unregistered {}, sent={}, sample={}",
                                 new Object[]{
                                    newStatus.registeredCount(),
                                    newStatus.limit(),
                                    newStatus.skippedCount(),
                                    unregister.count(),
                                    sent,
                                    newStatus.sampleChannels()
                                 }
                              );
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static RiptidePayloadChannelSubscriptionManager.SubscriptionPlan buildPlan(boolean packetLoggerCapturing) {
      int limit = subscriptionLimit();
      RiptidePayloadChannelSubscriptionManager.ChannelCollector collector = new RiptidePayloadChannelSubscriptionManager.ChannelCollector(limit);
      RiptidePayloadChannelRegistrations registrations = new RiptidePayloadChannelRegistrations();

      for (String channel : registrations.enabledChannels()) {
         collector.add(channel);
      }

      return collector.plan();
   }

   private static RiptidePayloadChannelSubscriptionManager.UnregisterResult unregisterRemovedChannels(List<String> nextChannels) {
      LinkedHashSet<String> removed = new LinkedHashSet<>();
      synchronized (RiptidePayloadChannelSubscriptionManager.class) {
         if (lastRegisteredChannels.isEmpty()) {
            return new RiptidePayloadChannelSubscriptionManager.UnregisterResult(0, true);
         }

         Set<String> next = new LinkedHashSet<>(nextChannels == null ? List.of() : nextChannels);

         for (String channel : lastRegisteredChannels) {
            if (!next.contains(channel)) {
               removed.add(channel);
            }
         }
      }

      if (removed.isEmpty()) {
         return new RiptidePayloadChannelSubscriptionManager.UnregisterResult(0, true);
      } else {
         boolean sent = RiptidePayloadSupport.sendPayloadSilent("minecraft:unregister", registrationBody(List.copyOf(removed)), "play");
         if (sent) {
            synchronized (RiptidePayloadChannelSubscriptionManager.class) {
               lastRegisteredChannels.removeAll(removed);
            }
         }

         if (TRACE) {
            riptide.RiptideClientAddon.LOG
               .info("[Riptide Payload] unregistered {} plugin channels: sent={}, sample={}", new Object[]{removed.size(), sent, sample(removed)});
         }

         return new RiptidePayloadChannelSubscriptionManager.UnregisterResult(removed.size(), sent);
      }
   }

   public static RiptidePayloadChannelSubscriptionManager.RegistrationImpact impactForPattern(String pattern) {
      String normalized = RiptidePayloadChannelListeners.normalizePattern(pattern);
      if (normalized.isBlank()) {
         return new RiptidePayloadChannelSubscriptionManager.RegistrationImpact(false, false, 0, List.of());
      } else {
         LinkedHashSet<String> channels = new LinkedHashSet<>();
         boolean wildcard = normalized.indexOf(42) >= 0;
         if (!wildcard) {
            if (isRegisterableChannel(normalized)) {
               channels.add(normalized);
            }

            return new RiptidePayloadChannelSubscriptionManager.RegistrationImpact(false, !channels.isEmpty(), channels.size(), List.copyOf(channels));
         } else {
            for (RiptidePayloadChannelListeners.Preset preset : RiptidePayloadChannelListeners.presetCatalog()) {
               String presetPattern = RiptidePayloadChannelListeners.normalizePattern(preset.pattern());
               if (presetPattern.indexOf(42) < 0 && RiptidePayloadChannelListeners.isRegisterablePreset(preset) && matchesPattern(normalized, presetPattern)) {
                  channels.add(presetPattern);
               }
            }

            return new RiptidePayloadChannelSubscriptionManager.RegistrationImpact(true, false, channels.size(), List.copyOf(channels));
         }
      }
   }

   public static RiptidePayloadChannelSubscriptionManager.Projection projectWithPatterns(Collection<String> patterns, boolean packetLoggerCapturing) {
      RiptidePayloadChannelSubscriptionManager.SubscriptionPlan plan = buildPlan(packetLoggerCapturing);
      LinkedHashSet<String> projected = new LinkedHashSet<>(plan.channels());
      int before = projected.size();
      if (patterns != null) {
         for (String pattern : patterns) {
            RiptidePayloadChannelSubscriptionManager.RegistrationImpact impact = impactForPattern(pattern);
            projected.addAll(impact.exactChannels());
         }
      }

      int exactAdded = Math.max(0, projected.size() - before);
      int overflow = Math.max(0, projected.size() - plan.limit());
      return new RiptidePayloadChannelSubscriptionManager.Projection(projected.size(), plan.limit(), exactAdded, overflow);
   }

   private static int subscriptionLimit() {
      String raw = System.getProperty("riptide.payload.subscription.limit");
      if (raw != null && !raw.isBlank()) {
         try {
            return Math.max(1, Math.min(120, Integer.parseInt(raw.trim())));
         } catch (NumberFormatException var2) {
            return 96;
         }
      } else {
         return 96;
      }
   }

   private static boolean isReady(Minecraft mc) {
      return mc != null && mc.getConnection() != null && mc.player != null;
   }

   private static byte[] registrationBody(List<String> channels) {
      StringBuilder builder = new StringBuilder();

      for (String channel : channels) {
         if (isRegisterableChannel(channel)) {
            if (!builder.isEmpty()) {
               builder.append('\u0000');
            }

            builder.append(channel);
         }
      }

      return builder.isEmpty() ? new byte[0] : builder.toString().getBytes(StandardCharsets.US_ASCII);
   }

   private static boolean isRegisterableChannel(String channel) {
      return RiptidePayloadChannelRegistrations.isRegisterableChannel(channel);
   }

   private static boolean isHiddenProbeChannel(String channel) {
      if (TRACE) {
         return false;
      } else {
         String normalized = normalize(channel);
         return normalized.contains("riptidetest")
            || normalized.contains("payloadprobe")
            || normalized.contains("payload_probe")
            || normalized.contains("brandlike")
            || normalized.contains("mc_string")
            || normalized.contains("java_utf");
      }
   }

   private static String normalize(String channel) {
      return channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
   }

   private static void addCappedRecent(LinkedHashSet<String> set, String channel) {
      if (set.remove(channel)) {
         set.add(channel);
      } else {
         set.add(channel);

         while (set.size() > 64) {
            String first = set.iterator().next();
            set.remove(first);
         }
      }
   }

   private static boolean matchesPattern(String pattern, String text) {
      String normalizedPattern = RiptidePayloadChannelListeners.normalizePattern(pattern);
      String normalizedText = normalize(text);
      if (!normalizedPattern.isEmpty() && !normalizedText.isEmpty()) {
         if ("*".equals(normalizedPattern)) {
            return true;
         } else if (normalizedPattern.indexOf(42) < 0) {
            return normalizedText.equals(normalizedPattern);
         } else {
            int p = 0;
            int t = 0;
            int star = -1;
            int mark = 0;

            while (t < normalizedText.length()) {
               if (p < normalizedPattern.length() && normalizedPattern.charAt(p) == normalizedText.charAt(t)) {
                  p++;
                  t++;
               } else if (p < normalizedPattern.length() && normalizedPattern.charAt(p) == '*') {
                  star = p++;
                  mark = t;
               } else {
                  if (star == -1) {
                     return false;
                  }

                  p = star + 1;
                  t = ++mark;
               }
            }

            while (p < normalizedPattern.length() && normalizedPattern.charAt(p) == '*') {
               p++;
            }

            return p == normalizedPattern.length();
         }
      } else {
         return false;
      }
   }

   private static String sample(Set<String> channels) {
      if (channels != null && !channels.isEmpty()) {
         List<String> values = new ArrayList<>(channels);
         int count = Math.min(8, values.size());
         return String.join(", ", values.subList(0, count)) + (values.size() > count ? ", ..." : "");
      } else {
         return "";
      }
   }

   private static final class ChannelCollector {
      private final int limit;
      private final LinkedHashMap<String, String> channels = new LinkedHashMap<>();
      private final LinkedHashSet<String> skipped = new LinkedHashSet<>();

      private ChannelCollector(int limit) {
         this.limit = limit;
      }

      private void add(String channel) {
         String normalized = RiptidePayloadChannelSubscriptionManager.normalize(channel);
         if (RiptidePayloadChannelSubscriptionManager.isRegisterableChannel(normalized)) {
            if (!this.channels.containsKey(normalized)) {
               if (this.channels.size() >= this.limit) {
                  this.skipped.add(normalized);
               } else {
                  this.channels.put(normalized, normalized);
               }
            }
         }
      }

      private RiptidePayloadChannelSubscriptionManager.SubscriptionPlan plan() {
         return new RiptidePayloadChannelSubscriptionManager.SubscriptionPlan(
            List.copyOf(this.channels.values()), this.limit, this.skipped.size(), List.copyOf(this.skipped)
         );
      }
   }

   public record Projection(int projectedCount, int limit, int addedCount, int overflowCount) {
      public boolean exceedsLimit() {
         return this.overflowCount > 0;
      }
   }

   public record RegistrationImpact(boolean wildcard, boolean exact, int exactChannelCount, List<String> exactChannels) {
      public String compactLabel() {
         return this.exact ? "1ch" : this.exactChannelCount + "ch";
      }
   }

   public record Status(
      int registeredCount,
      int limit,
      int skippedCount,
      boolean lastSendSucceeded,
      long lastSentMs,
      List<String> channels,
      List<String> skippedChannels,
      int lastUnregisteredCount,
      boolean locked
   ) {
      private static RiptidePayloadChannelSubscriptionManager.Status empty(int limit) {
         return new RiptidePayloadChannelSubscriptionManager.Status(0, limit, 0, false, 0L, List.of(), List.of(), 0, false);
      }

      private static RiptidePayloadChannelSubscriptionManager.Status locked(int limit, int lastUnregisteredCount) {
         return new RiptidePayloadChannelSubscriptionManager.Status(
            0, limit, 0, false, System.currentTimeMillis(), List.of(), List.of(), Math.max(0, lastUnregisteredCount), true
         );
      }

      public String shortLabel() {
         if (this.locked) {
            return this.lastUnregisteredCount > 0 ? "Registration locked -" + this.lastUnregisteredCount : "Registration locked";
         } else {
            String base = "Registered " + this.registeredCount + "/" + this.limit;
            if (this.lastUnregisteredCount > 0) {
               base = base + " -" + this.lastUnregisteredCount;
            }

            return this.skippedCount > 0 ? base + " +" + this.skippedCount : base;
         }
      }

      public String sampleChannels() {
         if (this.channels != null && !this.channels.isEmpty()) {
            int count = Math.min(8, this.channels.size());
            return String.join(", ", this.channels.subList(0, count)) + (this.channels.size() > count ? ", ..." : "");
         } else {
            return "";
         }
      }
   }

   public record SubscriptionPlan(List<String> channels, int limit, int skippedCount, List<String> skippedChannels) {
   }

   private record UnregisterResult(int count, boolean sent) {
   }
}
