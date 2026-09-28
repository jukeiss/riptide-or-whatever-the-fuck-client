package riptide.util.multi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import riptide.util.macro.ItemTarget;
import riptide.util.macro.XCarryAction;

final class MultiXCarryPlanner {
   private static final int[] STORAGE = new int[]{1, 2, 3, 4, 0, 5, 6, 7, 8, 45};
   private static final Set<Integer> STORAGE_SET = Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 45);
   private static final int MAX_CLICKS = 1024;

   private MultiXCarryPlanner() {
   }

   static List<MultiXCarryPlanner.Click> plan(XCarryAction action, List<ItemStack> inventorySlots, ItemStack initialCursor) {
      if (action != null && inventorySlots != null && inventorySlots.size() >= 46) {
         List<ItemStack> slots = copyStacks(inventorySlots);
         ItemStack[] cursor = new ItemStack[]{copy(initialCursor)};
         List<MultiXCarryPlanner.Click> clicks = new ArrayList<>();
         if (!cursor[0].isEmpty()) {
            planTrimCursor(action, slots, cursor, clicks);
            return List.copyOf(clicks);
         } else {
            List<ItemTarget> targets = targets(action);
            switch (action.mode == null ? XCarryAction.Mode.PUT_IN : action.mode) {
               case PUT_IN:
                  planPutIn(action, targets, slots, cursor, clicks);
                  break;
               case TAKE_OUT:
                  planTakeOut(targets, slots, clicks);
                  break;
               case DROP:
                  planDrop(targets, slots, clicks);
            }

            return List.copyOf(clicks);
         }
      } else {
         return List.of();
      }
   }

   private static void planTrimCursor(XCarryAction action, List<ItemStack> slots, ItemStack[] cursor, List<MultiXCarryPlanner.Click> clicks) {
      int safety = Math.max(0, cursor[0].getCount() - 1);

      while (!cursor[0].isEmpty() && cursor[0].getCount() > 1 && safety-- > 0 && clicks.size() < 1024) {
         int destination = findCursorReturnSlot(slots, cursor[0]);
         if (destination < 0) {
            break;
         }

         addPickup(
            action, slots, cursor, destination, 1, clicks, cursor[0].getCount() > 2 ? MultiXCarryPlanner.Pace.BETWEEN_CLICKS : MultiXCarryPlanner.Pace.COMPLETE
         );
      }
   }

   private static int findCursorReturnSlot(List<ItemStack> slots, ItemStack cursor) {
      for (int slot = 9; slot <= 44; slot++) {
         ItemStack stack = slots.get(slot);
         if (stack.isEmpty()) {
            return slot;
         }

         if (ItemStack.isSameItemSameComponents(stack, cursor) && stack.getCount() < stack.getMaxStackSize()) {
            return slot;
         }
      }

      return -1;
   }

   static List<Integer> collectContainerSlots(XCarryAction action, List<ItemStack> menuSlots, int containerSlotCount) {
      if (action != null && action.mode == XCarryAction.Mode.PUT_IN && menuSlots != null && containerSlotCount > 0) {
         List<ItemTarget> targets = targets(action);
         List<Integer> result = new ArrayList<>();
         Set<Integer> used = new HashSet<>();

         for (ItemTarget target : targets) {
            if (target != null && !target.hasSlot() && target.hasIdentity()) {
               for (int slot = 0; slot < containerSlotCount && slot < menuSlots.size(); slot++) {
                  if (!used.contains(slot) && identityMatches(target, menuSlots.get(slot))) {
                     result.add(slot);
                     used.add(slot);
                     break;
                  }
               }
            }
         }

         return List.copyOf(result);
      } else {
         return List.of();
      }
   }

   private static void planPutIn(
      XCarryAction action, List<ItemTarget> targets, List<ItemStack> slots, ItemStack[] cursor, List<MultiXCarryPlanner.Click> clicks
   ) {
      if (!targets.isEmpty()) {
         int limit = Math.min(11, targets.size());
         Set<Integer> claimed = new HashSet<>();
         int autoIndex = 0;
         boolean explicitDestination = false;

         for (int i = 0; i < limit && cursor[0].isEmpty(); i++) {
            if (action.destinationFor(i) == -1) {
               int source = findSource(slots, targets.get(i));
               if (source >= 0) {
                  moveOneToCursor(action, slots, cursor, source, clicks);
               }
            }
         }

         List<MultiXCarryPlanner.Row> rows = new ArrayList<>();

         for (int ix = 0; ix < limit; ix++) {
            ItemTarget target = targets.get(ix);
            int configured = action.destinationFor(ix);
            if (configured != -1) {
               int destination;
               if (configured == Integer.MIN_VALUE) {
                  destination = -1;

                  while (autoIndex < STORAGE.length) {
                     int candidate = STORAGE[autoIndex++];
                     if (!claimed.contains(candidate)) {
                        ItemStack current = slots.get(candidate);
                        if (current.isEmpty() || identityMatches(target, current)) {
                           destination = candidate;
                           break;
                        }
                     }
                  }

                  if (destination < 0) {
                     continue;
                  }
               } else {
                  explicitDestination = true;
                  destination = configured;
                  if (!STORAGE_SET.contains(configured) || claimed.contains(configured)) {
                     continue;
                  }
               }

               ItemStack destinationStack = slots.get(destination);
               if (destinationStack.isEmpty() || identityMatches(target, destinationStack)) {
                  ItemStack sample = destinationStack.isEmpty() ? firstSourceStack(slots, target) : destinationStack;
                  if (!sample.isEmpty()) {
                     int capacity = Math.max(1, sample.getMaxStackSize());
                     if (destinationStack.getCount() < capacity) {
                        claimed.add(destination);
                        rows.add(
                           new MultiXCarryPlanner.Row(
                              target, destination, action.amountModeFor(ix), action.amountFor(ix), destinationStack.getCount(), capacity
                           )
                        );
                     }
                  }
               }
            }
         }

         allocateRows(rows, slots);

         for (MultiXCarryPlanner.Row row : rows) {
            int needed = row.allocated;

            while (needed > 0 && clicks.size() < 1024) {
               int source = findSource(slots, row.target);
               if (source < 0) {
                  break;
               }

               int moved = Math.min(needed, slots.get(source).getCount());
               moveCount(action, slots, cursor, source, row.destination, moved, clicks);
               needed -= moved;
            }
         }

         if (cursor[0].isEmpty() && !explicitDestination && limit > STORAGE.length) {
            int source = findSource(slots, targets.get(STORAGE.length));
            if (source >= 0) {
               moveOneToCursor(action, slots, cursor, source, clicks);
            }
         }
      }
   }

   private static void allocateRows(List<MultiXCarryPlanner.Row> rows, List<ItemStack> slots) {
      Map<String, List<MultiXCarryPlanner.Row>> groups = new LinkedHashMap<>();

      for (MultiXCarryPlanner.Row row : rows) {
         groups.computeIfAbsent(targetKey(row.target), ignored -> new ArrayList<>()).add(row);
      }

      for (List<MultiXCarryPlanner.Row> group : groups.values()) {
         if (!group.isEmpty()) {
            int available = 0;

            for (int slot = 9; slot <= 44; slot++) {
               ItemStack stack = slots.get(slot);
               if (identityMatches(group.getFirst().target, stack)) {
                  available += stack.getCount();
               }
            }

            for (MultiXCarryPlanner.Row row : group) {
               available += row.current;
            }

            List<MultiXCarryPlanner.Row> full = new ArrayList<>();

            for (MultiXCarryPlanner.Row row : group) {
               if (row.amountMode == XCarryAction.AmountMode.CUSTOM) {
                  int desired = Math.min(row.capacity, Math.min(row.customAmount, available));
                  row.allocated = Math.max(0, desired - row.current);
                  available = Math.max(0, available - Math.max(row.current, desired));
               } else {
                  full.add(row);
               }
            }

            Map<MultiXCarryPlanner.Row, Integer> desired = new HashMap<>();

            for (MultiXCarryPlanner.Row rowx : full) {
               desired.put(rowx, 0);
            }

            while (available > 0 && !full.isEmpty()) {
               int active = 0;

               for (MultiXCarryPlanner.Row rowx : full) {
                  if (desired.get(rowx) < rowx.capacity) {
                     active++;
                  }
               }

               if (active == 0) {
                  break;
               }

               int share = Math.max(1, available / active);
               boolean progressed = false;

               for (MultiXCarryPlanner.Row rowxx : full) {
                  int room = rowxx.capacity - desired.get(rowxx);
                  if (room > 0 && available > 0) {
                     int take = Math.min(room, Math.min(share, available));
                     desired.put(rowxx, desired.get(rowxx) + take);
                     available -= take;
                     progressed |= take > 0;
                  }
               }

               if (!progressed) {
                  break;
               }
            }

            for (MultiXCarryPlanner.Row rowxxx : full) {
               rowxxx.allocated = Math.max(0, desired.getOrDefault(rowxxx, 0) - rowxxx.current);
            }
         }
      }
   }

   private static void moveCount(
      XCarryAction action, List<ItemStack> slots, ItemStack[] cursor, int source, int destination, int amount, List<MultiXCarryPlanner.Click> clicks
   ) {
      if (amount > 0 && cursor[0].isEmpty()) {
         addPickup(action, slots, cursor, source, 0, clicks, MultiXCarryPlanner.Pace.AFTER_PICKUP);

         while (!cursor[0].isEmpty() && cursor[0].getCount() > amount && clicks.size() < 1024) {
            addPickup(action, slots, cursor, source, 1, clicks, MultiXCarryPlanner.Pace.BETWEEN_CLICKS);
         }

         if (!cursor[0].isEmpty()) {
            addPickup(
               action,
               slots,
               cursor,
               destination,
               0,
               clicks,
               cursor[0].getCount() > amount ? MultiXCarryPlanner.Pace.BEFORE_RETURN : MultiXCarryPlanner.Pace.COMPLETE
            );
         }

         if (!cursor[0].isEmpty()) {
            addPickup(action, slots, cursor, source, 0, clicks, MultiXCarryPlanner.Pace.COMPLETE);
         }
      }
   }

   private static void moveOneToCursor(XCarryAction action, List<ItemStack> slots, ItemStack[] cursor, int source, List<MultiXCarryPlanner.Click> clicks) {
      int sourceCount = slots.get(source).getCount();
      addPickup(action, slots, cursor, source, 0, clicks, sourceCount > 1 ? MultiXCarryPlanner.Pace.AFTER_PICKUP : MultiXCarryPlanner.Pace.COMPLETE);

      while (!cursor[0].isEmpty() && cursor[0].getCount() > 1 && clicks.size() < 1024) {
         addPickup(
            action, slots, cursor, source, 1, clicks, cursor[0].getCount() > 2 ? MultiXCarryPlanner.Pace.BETWEEN_CLICKS : MultiXCarryPlanner.Pace.COMPLETE
         );
      }
   }

   private static void addPickup(
      XCarryAction action, List<ItemStack> slots, ItemStack[] cursor, int slot, int button, List<MultiXCarryPlanner.Click> clicks, MultiXCarryPlanner.Pace pace
   ) {
      ItemStack[] result = predictPickup(cursor[0], slots.get(slot), button == 0);
      cursor[0] = result[0];
      slots.set(slot, result[1]);
      clicks.add(new MultiXCarryPlanner.Click(slot, button, ContainerInput.PICKUP, delay(action, pace)));
   }

   private static void planTakeOut(List<ItemTarget> targets, List<ItemStack> slots, List<MultiXCarryPlanner.Click> clicks) {
      for (int slot : STORAGE) {
         ItemStack stack = slots.get(slot);
         if (!stack.isEmpty() && matchesStored(targets, stack, slot)) {
            clicks.add(new MultiXCarryPlanner.Click(slot, 0, ContainerInput.QUICK_MOVE, 50L));
         }
      }
   }

   private static void planDrop(List<ItemTarget> targets, List<ItemStack> slots, List<MultiXCarryPlanner.Click> clicks) {
      for (int slot : STORAGE) {
         ItemStack stack = slots.get(slot);
         if (!stack.isEmpty() && matchesStored(targets, stack, slot)) {
            clicks.add(new MultiXCarryPlanner.Click(slot, 1, ContainerInput.THROW, 50L));
         }
      }
   }

   private static boolean matchesStored(List<ItemTarget> targets, ItemStack stack, int handlerSlot) {
      if (targets.isEmpty()) {
         return true;
      } else {
         for (ItemTarget target : targets) {
            if (target != null
               && (!target.hasSlot() || target.slot == handlerSlot || target.slot == 100 && handlerSlot == 0)
               && (!target.hasIdentity() || identityMatches(target, stack))) {
               return true;
            }
         }

         return false;
      }
   }

   private static int findSource(List<ItemStack> slots, ItemTarget target) {
      if (target != null && target.hasSlot()) {
         int handler = visibleToInventoryHandler(target.slot);
         return handler >= 9 && handler <= 44 && sourceMatches(target, slots.get(handler), handler) ? handler : -1;
      } else {
         for (int slot = 9; slot <= 44; slot++) {
            if (sourceMatches(target, slots.get(slot), slot)) {
               return slot;
            }
         }

         return -1;
      }
   }

   private static ItemStack firstSourceStack(List<ItemStack> slots, ItemTarget target) {
      int source = findSource(slots, target);
      return source < 0 ? ItemStack.EMPTY : slots.get(source);
   }

   private static boolean sourceMatches(ItemTarget target, ItemStack stack, int handlerSlot) {
      if (stack == null || stack.isEmpty()) {
         return false;
      } else if (target == null) {
         return true;
      } else {
         int visible = inventoryHandlerToVisible(handlerSlot);
         return target.score(stack, visible) >= 0;
      }
   }

   private static boolean identityMatches(ItemTarget target, ItemStack stack) {
      if (stack != null && !stack.isEmpty() && target != null) {
         ItemTarget identity = target.copy();
         identity.slot = -1;
         return !identity.hasIdentity() || identity.matches(stack, -1);
      } else {
         return false;
      }
   }

   private static List<ItemTarget> targets(XCarryAction action) {
      List<ItemTarget> result = new ArrayList<>();
      if (!action.entryTargets.isEmpty()) {
         for (ItemTarget target : action.entryTargets) {
            if (target != null) {
               result.add(target.copy());
            }
         }
      } else {
         for (String entry : action.entries) {
            ItemTarget targetx = ItemTarget.fromLegacyEntry(entry);
            if (targetx.hasSlot() || targetx.hasIdentity()) {
               result.add(targetx);
            }
         }
      }

      return result.size() > 11 ? List.copyOf(result.subList(0, 11)) : List.copyOf(result);
   }

   private static String targetKey(ItemTarget target) {
      if (target == null) {
         return "";
      } else {
         ItemTarget identity = target.copy();
         identity.slot = -1;
         return identity.toLegacyEntry().toLowerCase(Locale.ROOT);
      }
   }

   private static long delay(XCarryAction action, MultiXCarryPlanner.Pace pace) {
      XCarryAction.TransferMode mode = action.transferMode == null ? XCarryAction.TransferMode.FAST : action.transferMode;
      if (mode == XCarryAction.TransferMode.FAST) {
         return 0L;
      } else if (mode == XCarryAction.TransferMode.CLICK) {
         return 50L;
      } else {
         long configured = XCarryAction.clampSafeClickDelayTicks(action.safeClickDelayTicks) * 50L;
         if (configured <= 0L || pace == MultiXCarryPlanner.Pace.COMPLETE) {
            return 0L;
         } else if (pace == MultiXCarryPlanner.Pace.AFTER_PICKUP && !action.safeClickDelayAfterPickup) {
            return 0L;
         } else {
            return pace == MultiXCarryPlanner.Pace.BEFORE_RETURN && !action.safeClickDelayBeforeReturn ? 0L : configured;
         }
      }
   }

   private static int visibleToInventoryHandler(int visible) {
      if (visible >= 0 && visible <= 8) {
         return 36 + visible;
      } else if (visible >= 9 && visible <= 35) {
         return visible;
      } else if (visible >= 36 && visible <= 39) {
         return 44 - visible;
      } else {
         return visible == 40 ? 45 : -1;
      }
   }

   private static int inventoryHandlerToVisible(int handler) {
      if (handler >= 36 && handler <= 44) {
         return handler - 36;
      } else if (handler >= 9 && handler <= 35) {
         return handler;
      } else if (handler >= 5 && handler <= 8) {
         return 44 - handler;
      } else {
         return handler == 45 ? 40 : handler;
      }
   }

   private static ItemStack[] predictPickup(ItemStack cursor, ItemStack slot, boolean primary) {
      ItemStack cur = copy(cursor);
      ItemStack target = copy(slot);
      if (target.isEmpty()) {
         if (!cur.isEmpty()) {
            int place = primary ? cur.getCount() : 1;
            target = cur.copyWithCount(place);
            cur.shrink(place);
         }
      } else if (cur.isEmpty()) {
         int take = primary ? target.getCount() : (target.getCount() + 1) / 2;
         cur = target.copyWithCount(take);
         target.shrink(take);
      } else if (ItemStack.isSameItemSameComponents(target, cur)) {
         int place = Math.min(primary ? cur.getCount() : 1, Math.max(0, target.getMaxStackSize() - target.getCount()));
         target.grow(place);
         cur.shrink(place);
      } else {
         ItemStack swap = target;
         target = cur;
         cur = swap;
      }

      return new ItemStack[]{empty(cur), empty(target)};
   }

   private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
      List<ItemStack> copy = new ArrayList<>(stacks.size());

      for (ItemStack stack : stacks) {
         copy.add(copy(stack));
      }

      return copy;
   }

   private static ItemStack copy(ItemStack stack) {
      return stack != null && !stack.isEmpty() ? stack.copy() : ItemStack.EMPTY;
   }

   private static ItemStack empty(ItemStack stack) {
      return stack != null && !stack.isEmpty() ? stack : ItemStack.EMPTY;
   }

   record Click(int slot, int button, ContainerInput input, long delayAfterMs) {
   }

   private static enum Pace {
      AFTER_PICKUP,
      BETWEEN_CLICKS,
      BEFORE_RETURN,
      COMPLETE;
   }

   private static final class Row {
      final ItemTarget target;
      final int destination;
      final XCarryAction.AmountMode amountMode;
      final int customAmount;
      final int current;
      final int capacity;
      int allocated;

      Row(ItemTarget target, int destination, XCarryAction.AmountMode amountMode, int customAmount, int current, int capacity) {
         this.target = target;
         this.destination = destination;
         this.amountMode = amountMode;
         this.customAmount = customAmount;
         this.current = current;
         this.capacity = capacity;
      }
   }
}
