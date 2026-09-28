package riptide.util;

public final class RiptidePlacementTick {
   private static String owner;
   private static int ownedTick = Integer.MIN_VALUE;

   private RiptidePlacementTick() {
   }

   public static synchronized boolean claim(String moduleId) {
      int tick = RiptideSharedState.get().getClientTickCounter();
      if (tick != ownedTick) {
         owner = moduleId;
         ownedTick = tick;
         return true;
      } else {
         return owner != null && owner.equals(moduleId);
      }
   }

   public static synchronized String owner() {
      return RiptideSharedState.get().getClientTickCounter() == ownedTick ? owner : null;
   }
}
