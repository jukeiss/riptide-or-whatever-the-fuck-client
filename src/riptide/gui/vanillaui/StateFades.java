package riptide.gui.vanillaui;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class StateFades {
   private static final long DURATION_NANOS = 200000000L;
   private static final long STALE_NANOS = 30000000000L;
   private static final Map<String, StateFades.State> STATES = new HashMap<>();
   private static long lastPrune;

   private StateFades() {
   }

   public static float get(String key, boolean on) {
      long now = System.nanoTime();
      StateFades.State state = STATES.get(key);
      if (state == null) {
         state = new StateFades.State(on ? 1.0F : 0.0F, on, now);
         STATES.put(key, state);
         return state.value;
      } else {
         if (state.target != on) {
            state.start = state.value(now);
            state.target = on;
            state.since = now;
         }

         state.lastSeen = now;
         if (now - lastPrune > 30000000000L) {
            lastPrune = now;
            Iterator<StateFades.State> it = STATES.values().iterator();

            while (it.hasNext()) {
               if (now - it.next().lastSeen > 30000000000L) {
                  it.remove();
               }
            }
         }

         return state.value(now);
      }
   }

   public static String key(UiBounds bounds) {
      return bounds.x() + ":" + bounds.y() + ":" + bounds.width() + ":" + bounds.height();
   }

   private static final class State {
      float start;
      float value;
      boolean target;
      long since;
      long lastSeen;

      State(float value, boolean target, long now) {
         this.value = value;
         this.start = value;
         this.target = target;
         this.since = now;
         this.lastSeen = now;
      }

      float value(long now) {
         float elapsed = Math.min(1.0F, (float)(now - this.since) / 2.0E8F);
         float eased = elapsed * elapsed * (3.0F - 2.0F * elapsed);
         this.value = this.start + ((this.target ? 1.0F : 0.0F) - this.start) * eased;
         return this.value;
      }
   }
}
