package riptide.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class RiptideFarmActionWatchdog<T> {
   private final Map<Long, RiptideFarmActionWatchdog.Attempt<T>> attempts = new LinkedHashMap<>();

   public boolean allow(long cell, T state, int tick, int limit) {
      RiptideFarmActionWatchdog.Attempt<T> old = this.attempts.get(cell);
      int elapsed = old == null ? 0 : tick - old.lastTick();
      int count = old != null && Objects.equals(old.state(), state) && elapsed >= 0 && elapsed <= 20 ? old.ticks() + (elapsed == 0 ? 0 : 1) : 1;
      if (count > Math.max(1, limit)) {
         this.attempts.remove(cell);
         return false;
      } else {
         this.attempts.put(cell, new RiptideFarmActionWatchdog.Attempt<>(state, tick, count));
         if (this.attempts.size() > 256) {
            this.attempts.remove(this.attempts.keySet().iterator().next());
         }

         return true;
      }
   }

   public void clear() {
      this.attempts.clear();
   }

   private record Attempt<T>(T state, int lastTick, int ticks) {
   }
}
