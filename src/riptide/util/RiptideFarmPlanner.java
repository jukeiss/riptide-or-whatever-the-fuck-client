package riptide.util;

import java.util.List;

public final class RiptideFarmPlanner {
   private RiptideFarmPlanner() {
   }

   public static <T> T choose(List<RiptideFarmPlanner.Option<T>> options, float yaw, float pitch, int selected, T previous) {
      boolean urgent = options.stream().anyMatch(RiptideFarmPlanner.Option::urgent);
      boolean planting = options.stream().anyMatch(o -> isPlanting(o.kind()));
      T best = null;
      double bestCost = Double.POSITIVE_INFINITY;

      for (RiptideFarmPlanner.Option<T> first : options) {
         if (!urgent || first.urgent()) {
            double cost = transition(yaw, pitch, selected, first) + priorityCost(first.kind(), planting);
            int nextSlot = first.slot() < 0 ? selected : first.slot();
            double next = Double.POSITIVE_INFINITY;

            for (RiptideFarmPlanner.Option<T> second : options) {
               if (first != second && !first.target().equals(second.target())) {
                  next = Math.min(next, transition(first.yaw(), first.pitch(), nextSlot, second));
               }
            }

            if (Double.isFinite(next)) {
               cost += 0.65 * next;
            }

            if (first.target().equals(previous)) {
               cost -= 0.6;
            }

            if (cost < bestCost) {
               bestCost = cost;
               best = first.target();
            }
         }
      }

      return best;
   }

   private static boolean isPlanting(RiptideFarmPlanner.Kind kind) {
      return kind == RiptideFarmPlanner.Kind.REPLANT || kind == RiptideFarmPlanner.Kind.REPLANT_RECENT;
   }

   private static double priorityCost(RiptideFarmPlanner.Kind kind, boolean planting) {
      return kind == RiptideFarmPlanner.Kind.TILL ? 8.0 : (kind == RiptideFarmPlanner.Kind.BONEMEAL && planting ? 6.0 : 0.0);
   }

   private static double transition(float yaw, float pitch, int selected, RiptideFarmPlanner.Option<?> next) {
      double yawDelta = Math.abs(Math.IEEEremainder(next.yaw() - yaw, 360.0));
      double turn = Math.hypot(yawDelta, next.pitch() - pitch) / 20.0;
      double change = next.slot() >= 0 && next.slot() != selected ? 2.0 : 0.0;
      double use = next.kind() == RiptideFarmPlanner.Kind.HARVEST ? 1.0 : 4.0;
      return turn + change + use;
   }

   public static enum Kind {
      HARVEST,
      REPLANT_RECENT,
      BONEMEAL,
      REPLANT,
      TILL;
   }

   public record Option<T>(T target, RiptideFarmPlanner.Kind kind, float yaw, float pitch, int slot, boolean urgent) {
   }
}
