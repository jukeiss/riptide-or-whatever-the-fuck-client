package riptide.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import riptide.modules.Module;
import riptide.modules.ModuleRegistry;

public final class RiptideHandArbiter {
   public static final int PRIORITY_DEFAULT = 0;
   public static final int PRIORITY_SURVIVAL = 100;
   public static final int OFFHAND_SLOT = 40;
   private static final Set<String> SURVIVAL_MODULES = Set.of("auto-totem");
   private static final Object LOCK = new Object();
   private static final Map<Integer, String> SLOT_OWNERS = new HashMap<>();
   private static String offhandClaim;
   private static String groupOwner;
   private static int groupTick;
   private static boolean groupOpen;
   private static String handHolder;
   private static String handReleasedBy;
   private static int handReleasedTick;

   private RiptideHandArbiter() {
   }

   public static int priorityOf(String moduleId) {
      String id = normalize(moduleId);
      return id != null && SURVIVAL_MODULES.contains(id) ? 100 : 0;
   }

   public static boolean claimOffhand(String moduleId) {
      String id = normalize(moduleId);
      if (id == null) {
         return false;
      } else {
         synchronized (LOCK) {
            String owner = liveOffhandOwner();
            if (owner != null && !owner.equals(id) && priorityOf(id) <= priorityOf(owner)) {
               return false;
            } else {
               offhandClaim = id;
               return true;
            }
         }
      }
   }

   public static String offhandOwner() {
      synchronized (LOCK) {
         return liveOffhandOwner();
      }
   }

   public static boolean offhandClaimedByOther(String moduleId) {
      String id = normalize(moduleId);
      synchronized (LOCK) {
         String owner = liveOffhandOwner();
         return owner != null && !owner.equals(id);
      }
   }

   public static void releaseOffhand(String moduleId) {
      String id = normalize(moduleId);
      if (id != null) {
         synchronized (LOCK) {
            if (id.equals(offhandClaim)) {
               offhandClaim = null;
            }
         }
      }
   }

   public static boolean reserveSlot(String moduleId, int inventorySlot) {
      String id = normalize(moduleId);
      if (id != null && isSlot(inventorySlot)) {
         synchronized (LOCK) {
            String owner = liveSlotOwner(inventorySlot);
            if (owner != null && !owner.equals(id) && priorityOf(id) <= priorityOf(owner)) {
               return false;
            } else {
               SLOT_OWNERS.values().removeIf(id::equals);
               SLOT_OWNERS.put(inventorySlot, id);
               return true;
            }
         }
      } else {
         return false;
      }
   }

   public static void releaseSlot(String moduleId, int inventorySlot) {
      String id = normalize(moduleId);
      if (id != null && isSlot(inventorySlot)) {
         synchronized (LOCK) {
            if (id.equals(SLOT_OWNERS.get(inventorySlot))) {
               SLOT_OWNERS.remove(inventorySlot);
            }
         }
      }
   }

   public static void releaseSlots(String moduleId) {
      String id = normalize(moduleId);
      if (id != null) {
         synchronized (LOCK) {
            SLOT_OWNERS.values().removeIf(id::equals);
         }
      }
   }

   public static boolean slotReserved(int inventorySlot, String exceptModuleId) {
      if (!isSlot(inventorySlot)) {
         return false;
      } else {
         String id = normalize(exceptModuleId);
         synchronized (LOCK) {
            String owner = liveSlotOwner(inventorySlot);
            return owner != null && !owner.equals(id);
         }
      }
   }

   public static String slotOwner(int inventorySlot) {
      if (!isSlot(inventorySlot)) {
         return null;
      } else {
         synchronized (LOCK) {
            return liveSlotOwner(inventorySlot);
         }
      }
   }

   public static boolean canBeginHandPacketGroup(String moduleId) {
      String id = normalize(moduleId);
      if (id == null) {
         return false;
      } else {
         synchronized (LOCK) {
            return groupAvailable(id, RiptideSharedState.get().getClientTickCounter());
         }
      }
   }

   public static boolean beginHandPacketGroup(String moduleId) {
      String id = normalize(moduleId);
      if (id == null) {
         return false;
      } else {
         synchronized (LOCK) {
            int tick = RiptideSharedState.get().getClientTickCounter();
            if (!groupAvailable(id, tick)) {
               return false;
            } else {
               groupOwner = id;
               groupTick = tick;
               groupOpen = true;
               return true;
            }
         }
      }
   }

   public static void endHandPacketGroup(String moduleId) {
      String id = normalize(moduleId);
      if (id != null) {
         synchronized (LOCK) {
            if (id.equals(groupOwner)) {
               groupOpen = false;
            }
         }
      }
   }

   public static String handPacketOwner() {
      synchronized (LOCK) {
         if (groupOpen && groupOwner != null) {
            return groupTick == RiptideSharedState.get().getClientTickCounter() ? groupOwner : null;
         } else {
            return null;
         }
      }
   }

   public static boolean holdHand(String moduleId) {
      String id = normalize(moduleId);
      if (id == null) {
         return false;
      } else {
         synchronized (LOCK) {
            String owner = liveHandHolder();
            if (owner != null && !owner.equals(id) && priorityOf(id) <= priorityOf(owner)) {
               return false;
            } else {
               handHolder = id;
               return true;
            }
         }
      }
   }

   public static void releaseHand(String moduleId) {
      String id = normalize(moduleId);
      if (id != null) {
         synchronized (LOCK) {
            if (id.equals(handHolder)) {
               handHolder = null;
               handReleasedBy = id;
               handReleasedTick = RiptideSharedState.get().getClientTickCounter();
            }
         }
      }
   }

   public static boolean handHandedOver(String moduleId) {
      String id = normalize(moduleId);
      synchronized (LOCK) {
         if (handReleasedBy == null) {
            return false;
         } else {
            int tick = RiptideSharedState.get().getClientTickCounter();
            if (tick != handReleasedTick && tick != handReleasedTick + 1) {
               handReleasedBy = null;
               return false;
            } else {
               return !handReleasedBy.equals(id);
            }
         }
      }
   }

   public static void releaseAll(String moduleId) {
      String id = normalize(moduleId);
      if (id != null) {
         synchronized (LOCK) {
            if (id.equals(offhandClaim)) {
               offhandClaim = null;
            }

            SLOT_OWNERS.values().removeIf(id::equals);
            if (id.equals(groupOwner)) {
               groupOwner = null;
               groupOpen = false;
            }

            if (id.equals(handHolder)) {
               handHolder = null;
               handReleasedBy = id;
               handReleasedTick = RiptideSharedState.get().getClientTickCounter();
            }
         }
      }
   }

   private static boolean groupAvailable(String id, int tick) {
      if (groupOwner == null || groupTick != tick) {
         return true;
      } else if (id.equals(groupOwner)) {
         return true;
      } else {
         return groupOpen ? false : priorityOf(id) > priorityOf(groupOwner);
      }
   }

   private static String liveHandHolder() {
      if (handHolder != null && !isLive(handHolder)) {
         handHolder = null;
      }

      return handHolder;
   }

   private static String liveOffhandOwner() {
      if (offhandClaim != null && !isLive(offhandClaim)) {
         offhandClaim = null;
      }

      return offhandClaim;
   }

   private static String liveSlotOwner(int inventorySlot) {
      String owner = SLOT_OWNERS.get(inventorySlot);
      if (owner == null) {
         return null;
      } else if (!isLive(owner)) {
         SLOT_OWNERS.remove(inventorySlot);
         return null;
      } else {
         return owner;
      }
   }

   private static boolean isLive(String moduleId) {
      Module module = ModuleRegistry.get(moduleId);
      return module != null && module.isEnabled();
   }

   private static boolean isSlot(int inventorySlot) {
      return inventorySlot >= 0 && inventorySlot < 41;
   }

   private static String normalize(String moduleId) {
      if (moduleId == null) {
         return null;
      } else {
         String trimmed = moduleId.trim();
         return trimmed.isEmpty() ? null : trimmed;
      }
   }
}
