package riptide.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RiptidePayloadStudySession {
   private static final int MAX_FINGERPRINTS = 512;
   private static final int MAX_SUMMARY_CHANNELS = 6;
   private static volatile boolean active;
   private static String label = "";
   private static long startedAtMs;
   private static final List<String> patterns = new ArrayList<>();
   private static final LinkedHashSet<String> armedChannels = new LinkedHashSet<>();
   private static final LinkedHashMap<String, RiptidePayloadStudySession.ChannelStats> channels = new LinkedHashMap<>();
   private static final LinkedHashSet<String> registeredHints = new LinkedHashSet<>();
   private static final LinkedHashSet<String> fingerprints = new LinkedHashSet<>();
   private static int eventCount;
   private static int matchedCount;
   private static int registerListCount;

   private RiptidePayloadStudySession() {
   }

   public static synchronized void start(String studyLabel, Collection<String> studyPatterns, Collection<String> registeredNow) {
      active = true;
      label = studyLabel != null && !studyLabel.isBlank() ? studyLabel.trim() : "Payloads";
      startedAtMs = System.currentTimeMillis();
      patterns.clear();
      armedChannels.clear();
      channels.clear();
      registeredHints.clear();
      fingerprints.clear();
      eventCount = 0;
      matchedCount = 0;
      registerListCount = 0;
      if (studyPatterns != null) {
         for (String pattern : studyPatterns) {
            String normalized = RiptidePayloadChannelListeners.normalizePattern(pattern);
            if (!normalized.isBlank() && !patterns.contains(normalized)) {
               patterns.add(normalized);
            }
         }
      }

      if (registeredNow != null) {
         for (String channel : registeredNow) {
            String normalized = RiptidePayloadChannelRegistrations.normalizeChannel(channel);
            if (RiptidePayloadChannelRegistrations.isRegisterableChannel(normalized)) {
               armedChannels.add(normalized);
            }
         }
      }

      RiptideNetworkCaptureState.refreshCurrent();
   }

   public static boolean isActive() {
      return active;
   }

   public static synchronized String bannerTitle() {
      return active ? "Payload Study: " + label : "";
   }

   public static synchronized String bannerLine1() {
      return !active ? "" : eventCount + " payloads | " + channels.size() + " channels | ESC to finish";
   }

   public static synchronized String bannerLine2() {
      if (!active) {
         return "";
      } else if (channels.isEmpty()) {
         return "Interact with the plugin GUI/commands now.";
      } else {
         List<RiptidePayloadStudySession.ChannelStats> sorted = new ArrayList<>(channels.values());
         sorted.sort((a, b) -> Integer.compare(b.count, a.count));
         StringBuilder sb = new StringBuilder("Top: ");
         int shown = 0;

         for (RiptidePayloadStudySession.ChannelStats stats : sorted) {
            if (shown >= 3) {
               break;
            }

            if (shown > 0) {
               sb.append(", ");
            }

            sb.append(stats.channel).append(" x").append(stats.count);
            shown++;
         }

         if (sorted.size() > shown) {
            sb.append(" +").append(sorted.size() - shown);
         }

         return sb.toString();
      }
   }

   public static synchronized void stop() {
      active = false;
      label = "";
      startedAtMs = 0L;
      patterns.clear();
      armedChannels.clear();
      channels.clear();
      registeredHints.clear();
      fingerprints.clear();
      eventCount = 0;
      matchedCount = 0;
      registerListCount = 0;
      RiptideNetworkCaptureState.refreshCurrent();
   }

   public static synchronized boolean recordPayload(RiptidePayloadSupport.PayloadSnapshot snapshot) {
      if (active && snapshot != null) {
         String channel = RiptidePayloadChannelRegistrations.normalizeChannel(snapshot.channel());
         if (channel.isBlank()) {
            return false;
         } else {
            String fingerprint = fingerprint(snapshot);
            if (!fingerprint.isBlank()) {
               if (!fingerprints.add(fingerprint)) {
                  return false;
               }

               while (fingerprints.size() > 512) {
                  String first = fingerprints.iterator().next();
                  fingerprints.remove(first);
               }
            }

            RiptidePayloadChannelSubscriptionManager.rememberObservedPayload(snapshot);
            eventCount++;
            RiptidePayloadStudySession.ChannelStats stats = channels.computeIfAbsent(channel, RiptidePayloadStudySession.ChannelStats::new);
            stats.count++;
            stats.totalBytes = stats.totalBytes + Math.max(0, snapshot.sizeBytes());
            String direction = snapshot.direction() == null ? "" : snapshot.direction().trim().toUpperCase(Locale.ROOT);
            if ("C2S".equals(direction)) {
               stats.c2s++;
            } else if ("S2C".equals(direction)) {
               stats.s2c++;
            }

            boolean matched = patterns.isEmpty();

            for (String pattern : patterns) {
               if (RiptidePayloadChannelListeners.patternMatchesChannel(pattern, channel)) {
                  stats.matchedPatterns.add(pattern);
                  matched = true;
               }
            }

            if (matched) {
               matchedCount++;
            }

            List<String> registerChannels = RiptidePayloadSupport.extractRegisterChannelList(channel, snapshot.rawBytes());
            if (!registerChannels.isEmpty()) {
               registerListCount++;

               for (String hinted : registerChannels) {
                  String normalized = RiptidePayloadChannelRegistrations.normalizeChannel(hinted);
                  if (RiptidePayloadChannelRegistrations.isRegisterableChannel(normalized)) {
                     registeredHints.add(normalized);
                  }
               }
            }

            return true;
         }
      } else {
         return false;
      }
   }

   public static synchronized boolean finishFromEscape() {
      if (!active) {
         return false;
      } else {
         RiptidePayloadStudySession.Summary summary = finish();
         if (summary.eventCount() == 0) {
            RiptideNotifications.warning("Payload study finished: no payloads captured.");
            return true;
         } else {
            RiptideNotifications.copied("Payload study: " + summary.eventCount() + " payloads, " + summary.channelCount() + " channels.");
            if (!summary.topChannels().isBlank()) {
               RiptideNotifications.show(summary.topChannels(), -7346000);
            }

            if (summary.learnedCount() > 0) {
               RiptideNotifications.warning("Learned " + summary.learnedCount() + " exact channels. Open Channels -> Capture.");
            }

            return true;
         }
      }
   }

   public static synchronized RiptidePayloadStudySession.Summary finish() {
      long durationMs = active ? Math.max(0L, System.currentTimeMillis() - startedAtMs) : 0L;
      active = false;
      List<RiptidePayloadStudySession.ChannelStats> sorted = new ArrayList<>(channels.values());
      sorted.sort((a, b) -> {
         int count = Integer.compare(b.count, a.count);
         return count != 0 ? count : a.channel.compareToIgnoreCase(b.channel);
      });
      StringBuilder top = new StringBuilder();
      int shown = 0;

      for (RiptidePayloadStudySession.ChannelStats stats : sorted) {
         if (shown >= 6) {
            break;
         }

         if (!top.isEmpty()) {
            top.append(", ");
         }

         top.append(stats.channel).append(" x").append(stats.count);
         shown++;
      }

      if (sorted.size() > shown) {
         top.append(" +").append(sorted.size() - shown);
      }

      int learned = learnedExactChannelsLocked().size();
      RiptidePayloadStudySession.Summary summary = new RiptidePayloadStudySession.Summary(
         label, durationMs, eventCount, channels.size(), matchedCount, registerListCount, learned, top.toString()
      );
      RiptideNetworkCaptureState.refreshCurrent();
      return summary;
   }

   public static synchronized List<String> learnedExactChannels() {
      return List.copyOf(learnedExactChannelsLocked());
   }

   private static LinkedHashSet<String> learnedExactChannelsLocked() {
      LinkedHashSet<String> learned = new LinkedHashSet<>();
      learned.addAll(channels.keySet());
      learned.addAll(registeredHints);
      learned.removeAll(armedChannels);
      return learned;
   }

   private static String fingerprint(RiptidePayloadSupport.PayloadSnapshot snapshot) {
      byte[] bytes = snapshot.rawBytes();
      return (snapshot.direction() == null ? "" : snapshot.direction())
         + "|"
         + (snapshot.protocolPhase() == null ? "" : snapshot.protocolPhase())
         + "|"
         + snapshot.packetId()
         + "|"
         + (snapshot.channel() == null ? "" : snapshot.channel())
         + "|"
         + snapshot.sizeBytes()
         + "|"
         + Arrays.hashCode(bytes);
   }

   private static final class ChannelStats {
      private final String channel;
      private final Set<String> matchedPatterns = new LinkedHashSet<>();
      private int count;
      private int c2s;
      private int s2c;
      private long totalBytes;

      private ChannelStats(String channel) {
         this.channel = channel;
      }
   }

   public record Summary(
      String label, long durationMs, int eventCount, int channelCount, int matchedCount, int registerListCount, int learnedCount, String topChannels
   ) {
   }
}
