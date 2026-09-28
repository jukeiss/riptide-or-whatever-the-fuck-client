package riptide.util;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import riptide.modules.PackHideState;

public final class RiptidePayloadFilterNotifier {
   private static final long THROTTLE_MS = 2000L;
   private static final long STALE_MS = 30000L;
   private static final int ACCENT = -14249;
   private static final Map<String, RiptidePayloadFilterNotifier.State> STATES = new LinkedHashMap<>();
   private static volatile boolean hasStates;

   private RiptidePayloadFilterNotifier() {
   }

   public static synchronized void onMatch(String channel, String direction, RiptidePayloadChannelListeners.Match match) {
      if ("S2C".equalsIgnoreCase(direction)) {
         if (!PackHideState.isActive()) {
            String normalized = normalize(channel);
            if (!normalized.isBlank()) {
               long now = System.currentTimeMillis();
               RiptidePayloadFilterNotifier.State state = STATES.computeIfAbsent(normalized, unused -> new RiptidePayloadFilterNotifier.State());
               hasStates = true;
               state.channel = normalized;
               state.summaryLabel = summaryLabel(normalized, match);
               state.lastSeenMs = now;
               if (now >= state.nextToastMs) {
                  if (state.pendingHits > 0) {
                     state.pendingHits++;
                     showSummary(state);
                  } else {
                     RiptideNotifications.show("Payload: " + normalized, -14249);
                  }

                  state.pendingHits = 0;
                  state.nextToastMs = now + 2000L;
               } else {
                  state.pendingHits++;
               }
            }
         }
      }
   }

   public static void tick() {
      if (hasStates) {
         synchronized (RiptidePayloadFilterNotifier.class) {
            if (STATES.isEmpty()) {
               hasStates = false;
            } else if (PackHideState.isHardLocked()) {
               STATES.clear();
               hasStates = false;
            } else {
               long now = System.currentTimeMillis();
               Iterator<Entry<String, RiptidePayloadFilterNotifier.State>> it = STATES.entrySet().iterator();

               while (it.hasNext()) {
                  RiptidePayloadFilterNotifier.State state = it.next().getValue();
                  if (state.pendingHits > 0 && now >= state.nextToastMs) {
                     showSummary(state);
                     state.pendingHits = 0;
                     state.nextToastMs = now + 2000L;
                  }

                  if (state.pendingHits == 0 && now - state.lastSeenMs > 30000L) {
                     it.remove();
                  }
               }

               hasStates = !STATES.isEmpty();
            }
         }
      }
   }

   public static synchronized void clear() {
      STATES.clear();
      hasStates = false;
   }

   private static void showSummary(RiptidePayloadFilterNotifier.State state) {
      String label = state.summaryLabel != null && !state.summaryLabel.isBlank() ? state.summaryLabel : state.channel;
      RiptideNotifications.show("Payload: " + label + " +" + state.pendingHits, -14249);
   }

   private static String summaryLabel(String channel, RiptidePayloadChannelListeners.Match match) {
      if (match != null && match.pattern() != null && !match.pattern().isBlank()) {
         String pattern = match.pattern().trim().toLowerCase(Locale.ROOT);
         return pattern.contains("*") ? pattern : channel;
      } else {
         return channel;
      }
   }

   private static String normalize(String channel) {
      return channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
   }

   private static final class State {
      String channel = "";
      String summaryLabel = "";
      long nextToastMs;
      long lastSeenMs;
      int pendingHits;
   }
}
