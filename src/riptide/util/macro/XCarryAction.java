package riptide.util.macro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import riptide.util.RiptideContainerTarget;
import riptide.util.RiptideSharedState;

public class XCarryAction implements MacroAction {
   public static final int CRAFT_RESULT_SLOT = 0;
   public static final int GRID_ENTRY_LIMIT = 4;
   public static final int CRAFT_RESULT_ENTRY_LIMIT = 1;
   public static final int ARMOR_ENTRY_LIMIT = 4;
   public static final int OFFHAND_ENTRY_LIMIT = 1;
   public static final int CURSOR_ENTRY_LIMIT = 1;
   public static final int STORAGE_ENTRY_LIMIT = 10;
   public static final int MAX_ENTRIES = 11;
   private static final int MAX_PUT_IN_ATTEMPTS = 11;
   public final List<String> entries = new ArrayList<>();
   public final List<ItemTarget> entryTargets = new ArrayList<>();
   public XCarryAction.Mode mode = XCarryAction.Mode.PUT_IN;
   public XCarryAction.TransferMode transferMode = XCarryAction.TransferMode.FAST;
   public int safeClickDelayTicks = 1;
   public boolean safeClickDelayAfterPickup = true;
   public boolean safeClickDelayBeforeReturn = true;
   public boolean carryCursor = true;
   public boolean useCrafting = true;
   public boolean useArmor = true;
   public boolean useOffhand = true;
   public static final int DEST_AUTO = Integer.MIN_VALUE;
   public static final int DEST_CURSOR = -1;
   private static final int[] DESTINATION_VALUES = new int[]{Integer.MIN_VALUE, -1, 1, 2, 3, 4, 0, 5, 6, 7, 8, 45};
   public final List<Integer> entryDestinations = new ArrayList<>();
   public final List<XCarryAction.AmountMode> entryAmountModes = new ArrayList<>();
   public final List<Integer> entryAmounts = new ArrayList<>();
   private boolean enabled = true;

   public static int[] destinationValues() {
      return (int[])DESTINATION_VALUES.clone();
   }

   public static List<String> destinationLabels() {
      ArrayList<String> labels = new ArrayList<>(DESTINATION_VALUES.length);

      for (int value : DESTINATION_VALUES) {
         labels.add(destinationLabel(value));
      }

      return labels;
   }

   public static int destinationIndex(int destination) {
      for (int i = 0; i < DESTINATION_VALUES.length; i++) {
         if (DESTINATION_VALUES[i] == destination) {
            return i;
         }
      }

      return 0;
   }

   public static String destinationLabel(int destination) {
      if (destination == Integer.MIN_VALUE) {
         return "Auto";
      } else if (destination == -1) {
         return "Cursor";
      } else {
         return switch (destination) {
            case 0 -> "Craft 5";
            case 1 -> "Craft 1";
            case 2 -> "Craft 2";
            case 3 -> "Craft 3";
            case 4 -> "Craft 4";
            case 5 -> "Helmet";
            case 6 -> "Chestplate";
            case 7 -> "Leggings";
            case 8 -> "Boots";
            case 45 -> "Offhand";
            default -> "Auto";
         };
      }
   }

   private void forceAllDestinationsEnabled() {
      this.carryCursor = true;
      this.useCrafting = true;
      this.useArmor = true;
      this.useOffhand = true;
   }

   public int destinationFor(int entryIndex) {
      if (entryIndex >= 0 && entryIndex < this.entryDestinations.size()) {
         Integer v = this.entryDestinations.get(entryIndex);
         return v == null ? Integer.MIN_VALUE : v;
      } else {
         return Integer.MIN_VALUE;
      }
   }

   public void setDestinationFor(int entryIndex, int destSlotId) {
      while (this.entryDestinations.size() <= entryIndex) {
         this.entryDestinations.add(Integer.MIN_VALUE);
      }

      this.entryDestinations.set(entryIndex, destSlotId);
   }

   public void resizeDestinations(int targetSize) {
      while (this.entryDestinations.size() < targetSize) {
         this.entryDestinations.add(Integer.MIN_VALUE);
      }

      while (this.entryDestinations.size() > targetSize) {
         this.entryDestinations.remove(this.entryDestinations.size() - 1);
      }
   }

   public XCarryAction.AmountMode amountModeFor(int entryIndex) {
      if (entryIndex >= 0 && entryIndex < this.entryAmountModes.size()) {
         XCarryAction.AmountMode mode = this.entryAmountModes.get(entryIndex);
         return mode == null ? XCarryAction.AmountMode.FULL_STK : mode;
      } else {
         return XCarryAction.AmountMode.FULL_STK;
      }
   }

   public void setAmountModeFor(int entryIndex, XCarryAction.AmountMode mode) {
      this.resizeAmountSettings(entryIndex + 1);
      this.entryAmountModes.set(entryIndex, mode == null ? XCarryAction.AmountMode.FULL_STK : mode);
   }

   public int amountFor(int entryIndex) {
      if (entryIndex >= 0 && entryIndex < this.entryAmounts.size()) {
         Integer amount = this.entryAmounts.get(entryIndex);
         return Math.max(1, amount == null ? 1 : amount);
      } else {
         return 1;
      }
   }

   public void setAmountFor(int entryIndex, int amount) {
      this.resizeAmountSettings(entryIndex + 1);
      this.entryAmounts.set(entryIndex, Math.max(1, amount));
   }

   public void resizeAmountSettings(int targetSize) {
      while (this.entryAmountModes.size() < targetSize) {
         this.entryAmountModes.add(XCarryAction.AmountMode.FULL_STK);
      }

      while (this.entryAmountModes.size() > targetSize) {
         this.entryAmountModes.remove(this.entryAmountModes.size() - 1);
      }

      while (this.entryAmounts.size() < targetSize) {
         this.entryAmounts.add(1);
      }

      while (this.entryAmounts.size() > targetSize) {
         this.entryAmounts.remove(this.entryAmounts.size() - 1);
      }
   }

   public static int clampSafeClickDelayTicks(int ticks) {
      return Math.max(0, Math.min(10, ticks));
   }

   public Set<Integer> activeStorageSlotIds() {
      LinkedHashSet<Integer> s = new LinkedHashSet<>();
      s.add(1);
      s.add(2);
      s.add(3);
      s.add(4);
      s.add(0);
      s.add(5);
      s.add(6);
      s.add(7);
      s.add(8);
      s.add(45);
      return s;
   }

   private List<Integer> putInAutoSlotIds() {
      return new ArrayList<>(this.activeStorageSlotIds());
   }

   private Set<Integer> actionSlotIds() {
      LinkedHashSet<Integer> s = new LinkedHashSet<>();
      s.addAll(this.activeStorageSlotIds());
      return s;
   }

   public static boolean isCraftResultSlot(int slotId) {
      return slotId == 0;
   }

   public void preparePutInStorageGuard() {
      this.markXCarryForcedForPutIn(this.carryCursor);
   }

   @Override
   public void execute(Minecraft mc) {
      if (mc != null && mc.player != null && mc.gameMode != null) {
         this.forceAllDestinationsEnabled();
         RiptideSharedState shared = RiptideSharedState.get();
         boolean prevBypass = shared.isXCarryArmorBypass();
         shared.setXCarryArmorBypass(true);

         try {
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
               this.executeWithContainerOpen(mc);
               return;
            }

            InventoryMenu handler = mc.player.inventoryMenu;
            if (!handler.getCarried().isEmpty()) {
               trimCursorToOne(handler, mc);
               this.updateXCarryState(handler, true);
               return;
            }

            switch (this.mode) {
               case PUT_IN:
                  this.executePutIn(handler, mc);
                  break;
               case TAKE_OUT:
                  this.executeTakeOut(handler, mc);
                  break;
               case DROP:
                  this.executeDrop(handler, mc);
            }
         } finally {
            shared.setXCarryArmorBypass(prevBypass);
         }
      }
   }

   private void executeWithContainerOpen(Minecraft mc) {
      if (mc.getConnection() != null) {
         this.forceAllDestinationsEnabled();
         RiptideContainerTarget containerTarget = RiptideSharedState.get().getLastContainerTarget();
         if (containerTarget != null) {
            InventoryMenu playerHandler = mc.player.inventoryMenu;
            if (!playerHandler.getCarried().isEmpty()) {
               trimCursorToOne(playerHandler, mc);
               this.updateXCarryState(playerHandler, true);
            } else {
               AbstractContainerMenu containerHandler = mc.player.containerMenu;
               if (this.mode == XCarryAction.Mode.PUT_IN) {
                  for (int slotId : this.collectContainerTransferSlots(containerHandler, playerHandler)) {
                     mc.gameMode.handleContainerInput(containerHandler.containerId, slotId, 0, ContainerInput.QUICK_MOVE, mc.player);
                  }
               }

               mc.getConnection().send(new ServerboundContainerClosePacket(containerHandler.containerId));
               mc.player.containerMenu = playerHandler;
               switch (this.mode) {
                  case PUT_IN:
                     this.executePutIn(playerHandler, mc);
                     break;
                  case TAKE_OUT:
                     this.executeTakeOut(playerHandler, mc);
                     break;
                  case DROP:
                     this.executeDrop(playerHandler, mc);
               }

               mc.player.containerMenu = containerHandler;
               containerTarget.interact(mc);
            }
         }
      }
   }

   private void executePutIn(InventoryMenu handler, Minecraft mc) {
      this.forceAllDestinationsEnabled();
      List<ItemTarget> targets = this.runtimeEntryTargets();
      if (targets.isEmpty()) {
         if (hasStoredItems(handler, true)) {
            this.updateXCarryState(handler, true);
         }
      } else {
         this.markXCarryForcedForPutIn(true);
         boolean moved = false;
         boolean cursorClaimed = false;

         for (int i = 0; i < targets.size(); i++) {
            if (this.destinationFor(i) == -1 && !cursorClaimed && handler.getCarried().isEmpty() && this.moveOneItemToCursor(handler, mc, i, i + 1)) {
               moved = true;
               cursorClaimed = true;
            }
         }

         int safety = 0;

         while (safety++ < 512) {
            XCarryAction.PutInMove move = this.findNextPutInMove(handler);
            if (move == null || !movePlannedCount(handler, mc, move.sourceSlotId, move.targetSlotId, move.count)) {
               break;
            }

            moved = true;
         }

         int gridCapacity = this.putInAutoSlotIds().size();
         if (!cursorClaimed && handler.getCarried().isEmpty() && targets.size() > gridCapacity) {
            boolean anyExplicit = false;

            for (int ix = 0; ix < targets.size(); ix++) {
               int dest = this.destinationFor(ix);
               if (dest != Integer.MIN_VALUE && dest != -1) {
                  anyExplicit = true;
                  break;
               }
            }

            if (!anyExplicit && this.moveOneItemToCursor(handler, mc, gridCapacity, gridCapacity + 1)) {
               moved = true;
            }
         }

         this.updateXCarryState(handler, true);
      }
   }

   private void executeTakeOut(InventoryMenu handler, Minecraft mc) {
      this.forceAllDestinationsEnabled();

      for (Integer slotId : this.actionSlotIds()) {
         if (slotId != null && slotId >= 0 && slotId < handler.slots.size()) {
            Slot slot = (Slot)handler.slots.get(slotId);
            if (!slot.getItem().isEmpty() && (this.entries.isEmpty() || this.matchesCraftingSlot(slot, slotId))) {
               mc.gameMode.handleContainerInput(handler.containerId, slotId, 0, ContainerInput.QUICK_MOVE, mc.player);
            }
         }
      }

      this.updateXCarryState(handler, true);
   }

   private void executeDrop(InventoryMenu handler, Minecraft mc) {
      this.forceAllDestinationsEnabled();

      for (Integer slotId : this.actionSlotIds()) {
         if (slotId != null && slotId >= 0 && slotId < handler.slots.size()) {
            Slot slot = (Slot)handler.slots.get(slotId);
            if (!slot.getItem().isEmpty() && (this.entries.isEmpty() || this.matchesCraftingSlot(slot, slotId))) {
               mc.gameMode.handleContainerInput(handler.containerId, slotId, 1, ContainerInput.THROW, mc.player);
            }
         }
      }

      this.updateXCarryState(handler, true);
   }

   private boolean matchesCraftingSlot(Slot slot, int slotId) {
      for (ItemTarget entryTarget : this.runtimeEntryTargets()) {
         if (entryTarget != null) {
            if (entryTarget.hasSlot()) {
               if (matchesConfiguredStorageSlot(entryTarget.slot, slotId)
                  && (!entryTarget.hasIdentity() || matchesDestinationStack(slot.getItem(), entryTarget))) {
                  return true;
               }
            } else if (entryTarget.hasIdentity() && matchesTarget(slot.getItem(), entryTarget, slotId)) {
               return true;
            }
         }
      }

      return false;
   }

   public XCarryAction.PutInMove findNextPutInMove(InventoryMenu handler) {
      List<XCarryAction.PutInMove> moves = this.planPutInMoves(handler);
      return moves.isEmpty() ? null : moves.get(0);
   }

   public List<XCarryAction.PutInMove> planPutInMoves(InventoryMenu handler) {
      ArrayList<XCarryAction.PutInMove> moves = new ArrayList<>();
      if (handler == null) {
         return moves;
      } else {
         this.forceAllDestinationsEnabled();
         Set<Integer> storageSlotSet = this.activeStorageSlotIds();
         List<Integer> storageSlots = this.putInAutoSlotIds();
         List<ItemTarget> targets = this.runtimeEntryTargets();
         int limit = Math.min(this.maxConfiguredEntries(), targets.size());
         this.resizeDestinations(limit);
         this.resizeAmountSettings(limit);
         int nextTargetIdx = 0;
         Set<Integer> claimedDestinations = new HashSet<>();
         List<XCarryAction.PutInRow> rows = new ArrayList<>();

         for (int i = 0; i < limit; i++) {
            int dest = this.destinationFor(i);
            if (dest != -1) {
               int targetSlotId = -1;
               if (dest != Integer.MIN_VALUE) {
                  targetSlotId = dest;
                  if (dest < 0 || dest >= handler.slots.size()) {
                     continue;
                  }
               } else {
                  while (nextTargetIdx < storageSlots.size()) {
                     int candidate = storageSlots.get(nextTargetIdx);
                     if (candidate < 0 || candidate >= handler.slots.size()) {
                        nextTargetIdx++;
                     } else if (claimedDestinations.contains(candidate)) {
                        nextTargetIdx++;
                     } else {
                        ItemStack stack = ((Slot)handler.slots.get(candidate)).getItem();
                        if (stack.isEmpty() || matchesDestinationStack(stack, targets.get(i))) {
                           break;
                        }

                        nextTargetIdx++;
                     }
                  }

                  if (nextTargetIdx >= storageSlots.size()) {
                     continue;
                  }

                  targetSlotId = storageSlots.get(nextTargetIdx++);
               }

               Slot destSlot = (Slot)handler.slots.get(targetSlotId);
               ItemStack destStack = destSlot.getItem();
               ItemTarget target = targets.get(i);
               if (destStack.isEmpty() || matchesDestinationStack(destStack, target)) {
                  int maxCount = slotCapacity(destSlot, destStack.isEmpty() ? this.firstMatchingSourceStack(handler, target, storageSlotSet) : destStack);
                  int currentCount = destStack.isEmpty() ? 0 : destStack.getCount();
                  if (maxCount > currentCount) {
                     claimedDestinations.add(targetSlotId);
                     rows.add(new XCarryAction.PutInRow(i, target, targetSlotId, this.amountModeFor(i), this.amountFor(i), currentCount, maxCount));
                  }
               }
            }
         }

         if (rows.isEmpty()) {
            return moves;
         } else {
            allocatePutInRows(handler, rows, storageSlotSet);
            appendOptimizedPutInMoves(handler, rows, storageSlotSet, moves);
            return moves;
         }
      }
   }

   private static void appendOptimizedPutInMoves(
      InventoryMenu handler, List<XCarryAction.PutInRow> rows, Set<Integer> storageSlotSet, List<XCarryAction.PutInMove> moves
   ) {
      if (handler != null && rows != null && !rows.isEmpty() && moves != null) {
         Map<Integer, Integer> sourceUsed = new HashMap<>();
         LinkedHashMap<String, List<XCarryAction.PutInRow>> byTarget = new LinkedHashMap<>();

         for (XCarryAction.PutInRow row : rows) {
            if (row != null && row.allocated > 0 && row.target != null) {
               byTarget.computeIfAbsent(targetKey(row.target), ignored -> new ArrayList<>()).add(row);
            }
         }

         for (List<XCarryAction.PutInRow> group : byTarget.values()) {
            if (group != null && !group.isEmpty()) {
               int totalDemand = 0;

               for (XCarryAction.PutInRow rowx : group) {
                  totalDemand += Math.max(0, rowx.allocated);
               }

               if (totalDemand > 0) {
                  int sourceSlot = findBestPutInSourceSlot(handler, group.get(0).target, storageSlotSet, sourceUsed, totalDemand);
                  if (sourceSlot >= 0) {
                     appendPutInMovesFromSingleSource(handler, group, sourceSlot, sourceUsed, moves);
                  } else {
                     for (XCarryAction.PutInRow rowx : group) {
                        int remaining = rowx.allocated;
                        if (remaining > 0) {
                           for (Slot slot : handler.slots) {
                              if (remaining <= 0) {
                                 break;
                              }

                              if (canUsePutInSourceSlot(handler, slot, rowx.target, storageSlotSet, sourceUsed) && slot.index != rowx.targetSlotId) {
                                 int used = sourceUsed.getOrDefault(slot.index, 0);
                                 int available = slot.getItem().getCount() - used;
                                 if (available > 0) {
                                    int take = Math.min(remaining, available);
                                    moves.add(new XCarryAction.PutInMove(slot.index, rowx.targetSlotId, take));
                                    sourceUsed.put(slot.index, used + take);
                                    remaining -= take;
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static int findBestPutInSourceSlot(
      InventoryMenu handler, ItemTarget target, Set<Integer> storageSlotSet, Map<Integer, Integer> sourceUsed, int totalDemand
   ) {
      if (handler != null && target != null && totalDemand > 0) {
         int exact = -1;
         int smallestEnough = -1;
         int smallestEnoughCount = Integer.MAX_VALUE;

         for (Slot slot : handler.slots) {
            if (canUsePutInSourceSlot(handler, slot, target, storageSlotSet, sourceUsed)) {
               int available = slot.getItem().getCount() - sourceUsed.getOrDefault(slot.index, 0);
               if (available > 0) {
                  if (available == totalDemand) {
                     exact = slot.index;
                     break;
                  }

                  if (available > totalDemand && available < smallestEnoughCount) {
                     smallestEnough = slot.index;
                     smallestEnoughCount = available;
                  }
               }
            }
         }

         return exact >= 0 ? exact : smallestEnough;
      } else {
         return -1;
      }
   }

   private static boolean canUsePutInSourceSlot(
      InventoryMenu handler, Slot slot, ItemTarget target, Set<Integer> storageSlotSet, Map<Integer, Integer> sourceUsed
   ) {
      if (handler == null || slot == null || target == null || storageSlotSet == null) {
         return false;
      } else if (storageSlotSet.contains(slot.index) || !isEligibleSourceSlot(slot)) {
         return false;
      } else if (!matchesSourceSlot(handler, slot, target)) {
         return false;
      } else {
         int available = slot.getItem().getCount() - (sourceUsed == null ? 0 : sourceUsed.getOrDefault(slot.index, 0));
         return available > 0;
      }
   }

   private static void appendPutInMovesFromSingleSource(
      InventoryMenu handler, List<XCarryAction.PutInRow> rows, int sourceSlotId, Map<Integer, Integer> sourceUsed, List<XCarryAction.PutInMove> moves
   ) {
      if (handler != null && rows != null && sourceUsed != null && moves != null) {
         if (sourceSlotId >= 0 && sourceSlotId < handler.slots.size()) {
            Slot sourceSlot = (Slot)handler.slots.get(sourceSlotId);
            if (sourceSlot != null && !sourceSlot.getItem().isEmpty()) {
               int used = sourceUsed.getOrDefault(sourceSlotId, 0);
               int available = sourceSlot.getItem().getCount() - used;

               for (XCarryAction.PutInRow row : rows) {
                  if (row != null && row.allocated > 0 && available > 0 && row.targetSlotId != sourceSlotId) {
                     int take = Math.min(row.allocated, available);
                     if (take > 0) {
                        moves.add(new XCarryAction.PutInMove(sourceSlotId, row.targetSlotId, take));
                        available -= take;
                        used += take;
                     }
                  }
               }

               sourceUsed.put(sourceSlotId, used);
            }
         }
      }
   }

   public boolean requiresCursorStorage() {
      this.forceAllDestinationsEnabled();
      List<ItemTarget> targets = this.runtimeEntryTargets();
      int limit = Math.min(this.maxConfiguredEntries(), targets.size());
      boolean anyExplicit = false;
      int autoCount = 0;

      for (int i = 0; i < limit; i++) {
         int dest = this.destinationFor(i);
         if (dest == -1) {
            return true;
         }

         if (dest == Integer.MIN_VALUE) {
            autoCount++;
         } else {
            anyExplicit = true;
         }
      }

      return !anyExplicit && autoCount > this.putInAutoSlotIds().size();
   }

   public List<Integer> collectContainerTransferSlots(AbstractContainerMenu containerHandler) {
      return this.collectContainerTransferSlots(containerHandler, null);
   }

   public List<Integer> collectContainerTransferSlots(AbstractContainerMenu containerHandler, InventoryMenu playerHandler) {
      List<Integer> slotIds = new ArrayList<>();
      if (containerHandler == null) {
         return slotIds;
      } else {
         List<ItemTarget> targets = this.runtimeEntryTargets();
         int limit = Math.min(this.maxConfiguredEntries(), targets.size());
         Map<String, Integer> neededByKey = new LinkedHashMap<>();
         if (playerHandler != null) {
            List<XCarryAction.PutInRow> rows = this.previewPutInRows(playerHandler);
            allocatePutInRows(playerHandler, rows, this.activeStorageSlotIds());

            for (XCarryAction.PutInRow row : rows) {
               if (row.allocated > 0 && row.target != null && row.target.hasIdentity()) {
                  String key = targetKey(row.target);
                  neededByKey.put(key, neededByKey.getOrDefault(key, 0) + row.allocated);
               }
            }
         }

         Set<Integer> usedContainerSlots = new HashSet<>();

         for (int i = 0; i < limit; i++) {
            ItemTarget itemTarget = targets.get(i);
            if (itemTarget != null && !itemTarget.hasSlot() && itemTarget.hasIdentity()) {
               String key = targetKey(itemTarget);
               if (playerHandler == null || neededByKey.isEmpty() || neededByKey.getOrDefault(key, 0) > 0) {
                  for (Slot slot : containerHandler.slots) {
                     if (!usedContainerSlots.contains(slot.index)
                        && !slot.getItem().isEmpty()
                        && !(slot.container instanceof Inventory)
                        && matchesTarget(slot.getItem(), itemTarget, -1)) {
                        slotIds.add(slot.index);
                        usedContainerSlots.add(slot.index);
                        if (playerHandler != null) {
                           neededByKey.put(key, neededByKey.getOrDefault(key, 0) - slot.getItem().getCount());
                        }
                        break;
                     }
                  }
               }
            }
         }

         return slotIds;
      }
   }

   @Override
   public CompoundTag toTag() {
      this.forceAllDestinationsEnabled();
      CompoundTag tag = new CompoundTag();
      tag.putString("type", MacroActionType.XCARRY.name());
      tag.putString("mode", this.mode.name());
      tag.putString("transferMode", this.transferMode.name());
      tag.putInt("safeClickDelayTicks", clampSafeClickDelayTicks(this.safeClickDelayTicks));
      tag.putBoolean("safeClickDelayAfterPickup", this.safeClickDelayAfterPickup);
      tag.putBoolean("safeClickDelayBeforeReturn", this.safeClickDelayBeforeReturn);
      tag.putBoolean("carryCursor", true);
      tag.putBoolean("useCrafting", true);
      tag.putBoolean("useArmor", true);
      tag.putBoolean("useOffhand", true);
      List<ItemTarget> targets = this.resolvedEntryTargets();
      int savedCount = Math.min(this.maxConfiguredEntries(), targets.size());
      tag.put("entries", ItemTarget.toTagList(targets.subList(0, savedCount)));
      this.resizeDestinations(savedCount);
      this.resizeAmountSettings(savedCount);
      ListTag destinations = new ListTag();
      ListTag amountModes = new ListTag();
      ListTag amounts = new ListTag();

      for (int i = 0; i < savedCount; i++) {
         destinations.add(StringTag.valueOf(String.valueOf(this.destinationFor(i))));
         amountModes.add(StringTag.valueOf(this.amountModeFor(i).name()));
         amounts.add(StringTag.valueOf(String.valueOf(this.amountFor(i))));
      }

      tag.put("entryDestinations", destinations);
      tag.put("entryAmountModes", amountModes);
      tag.put("entryAmounts", amounts);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.entries.clear();
      this.entryTargets.clear();
      this.entryDestinations.clear();
      this.entryAmountModes.clear();
      this.entryAmounts.clear();
      String modeStr = tag.getStringOr("mode", "PUT_IN");

      try {
         this.mode = XCarryAction.Mode.valueOf(modeStr);
      } catch (IllegalArgumentException var13) {
         this.mode = XCarryAction.Mode.PUT_IN;
      }

      String transferModeStr = tag.getStringOr("transferMode", "FAST");

      try {
         this.transferMode = XCarryAction.TransferMode.valueOf(transferModeStr);
      } catch (IllegalArgumentException var12) {
         this.transferMode = XCarryAction.TransferMode.FAST;
      }

      this.safeClickDelayTicks = clampSafeClickDelayTicks(tag.getIntOr("safeClickDelayTicks", 1));
      this.safeClickDelayAfterPickup = tag.getBooleanOr("safeClickDelayAfterPickup", true);
      this.safeClickDelayBeforeReturn = tag.getBooleanOr("safeClickDelayBeforeReturn", true);
      this.forceAllDestinationsEnabled();
      if (tag.contains("entries")) {
         ListTag list = tag.getList("entries").orElse(new ListTag());

         for (ItemTarget target : ItemTarget.fromElementList(list)) {
            if (target != null && (target.hasSlot() || target.hasIdentity())) {
               this.entryTargets.add(target);
               if (this.entryTargets.size() >= this.maxConfiguredEntries()) {
                  break;
               }
            }
         }
      }

      if (tag.contains("entryDestinations")) {
         for (Tag element : tag.getList("entryDestinations").orElse(new ListTag())) {
            int dest = Integer.MIN_VALUE;

            try {
               dest = Integer.parseInt(element.asString().orElse(String.valueOf(Integer.MIN_VALUE)));
            } catch (NumberFormatException var11) {
            }

            this.entryDestinations.add(dest);
         }
      }

      if (tag.contains("entryAmountModes")) {
         for (Tag element : tag.getList("entryAmountModes").orElse(new ListTag())) {
            XCarryAction.AmountMode amountMode = XCarryAction.AmountMode.FULL_STK;

            try {
               amountMode = XCarryAction.AmountMode.valueOf(element.asString().orElse(XCarryAction.AmountMode.FULL_STK.name()));
            } catch (IllegalArgumentException var10) {
            }

            this.entryAmountModes.add(amountMode);
         }
      }

      if (tag.contains("entryAmounts")) {
         for (Tag element : tag.getList("entryAmounts").orElse(new ListTag())) {
            int amount = 1;

            try {
               amount = Integer.parseInt(element.asString().orElse("1"));
            } catch (NumberFormatException var9) {
            }

            this.entryAmounts.add(Math.max(1, amount));
         }
      }

      this.resizeDestinations(this.entryTargets.size());
      this.resizeAmountSettings(this.entryTargets.size());
      this.syncLegacyEntries();
      this.enabled = tag.getBooleanOr("enabled", true);
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.XCARRY;
   }

   @Override
   public String getDisplayName() {
      String prefix = switch (this.mode) {
         case PUT_IN -> {
            switch (this.transferMode) {
               case FAST:
                  yield "XCarry";
               case CLICK:
                  yield "XCarry Click";
               case SAFE_CLICK:
                  yield "XCarry Safe";
               default:
                  throw new MatchException(null, null);
            }
         }
         case TAKE_OUT -> "XCarry Out";
         case DROP -> "XCarry Drop";
      };
      this.forceAllDestinationsEnabled();
      if (this.entries.isEmpty()) {
         return prefix;
      } else {
         String first = formatEntry(this.entries.get(0));
         if (this.entries.size() > 1) {
            first = first + " (+" + (this.entries.size() - 1) + ")";
         }

         return prefix + " [" + first + "]";
      }
   }

   @Override
   public String getIcon() {
      return "XC";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public static String normalizeEntry(String raw) {
      if (raw == null) {
         return null;
      } else {
         String trimmed = raw.trim();
         if (trimmed.isEmpty()) {
            return null;
         } else if (!trimmed.startsWith("#")) {
            return trimmed;
         } else {
            int separator = trimmed.indexOf(124);
            String slotPart = separator >= 0 ? trimmed.substring(1, separator) : trimmed.substring(1);

            try {
               int slot = Integer.parseInt(slotPart.trim());
               if (slot < 0) {
                  return null;
               } else {
                  String name = separator >= 0 && separator + 1 < trimmed.length() ? trimmed.substring(separator + 1).trim() : "";
                  return !name.isEmpty() ? "#" + slot + "|" + name : "#" + slot;
               }
            } catch (NumberFormatException var6) {
               return null;
            }
         }
      }
   }

   public static String formatEntry(String entry) {
      String normalized = normalizeEntry(entry);
      if (normalized == null) {
         return "";
      } else {
         int slot = parseEntrySlot(normalized);
         String name = parseEntryName(normalized);
         if (slot >= 0 && !name.isEmpty()) {
            return name + " @ Slot " + slot;
         } else {
            return slot >= 0 ? "Slot " + slot : normalized;
         }
      }
   }

   private static int findSourceSlot(InventoryMenu handler, ItemTarget target) {
      return findSourceSlot(handler, target, Set.of());
   }

   private static int findSourceSlot(InventoryMenu handler, ItemTarget target, Set<Integer> excludedSlotIds) {
      if (target == null) {
         return -1;
      } else {
         Set<Integer> excluded = excludedSlotIds == null ? Set.of() : excludedSlotIds;
         if (target.hasSlot()) {
            int exactSlot = resolvePlayerVisibleSlot(handler, target.slot);
            if (exactSlot < 0) {
               return -1;
            } else if (excluded.contains(exactSlot)) {
               return -1;
            } else if (!target.hasIdentity()) {
               return exactSlot;
            } else {
               Slot slot = (Slot)handler.slots.get(exactSlot);
               return matchesTarget(slot.getItem(), target, target.slot) ? exactSlot : -1;
            }
         } else {
            for (Slot slot : handler.slots) {
               if (!excluded.contains(slot.index) && isEligibleSourceSlot(slot)) {
                  int visibleSlot = resolveVisibleSlotForPlayerSlot(handler, slot.index);
                  if (matchesTarget(slot.getItem(), target, visibleSlot)) {
                     return slot.index;
                  }
               }
            }

            return -1;
         }
      }
   }

   public static boolean movePlannedCount(InventoryMenu handler, Minecraft mc, int fromSlotId, int toSlotId, int count) {
      if (count <= 0) {
         return false;
      } else if (mc == null || mc.player == null || mc.gameMode == null) {
         return false;
      } else if (fromSlotId < 0 || fromSlotId >= handler.slots.size()) {
         return false;
      } else if (toSlotId < 0 || toSlotId >= handler.slots.size()) {
         return false;
      } else if (fromSlotId == toSlotId) {
         return true;
      } else if (!handler.getCarried().isEmpty()) {
         return false;
      } else {
         Slot sourceSlot = (Slot)handler.slots.get(fromSlotId);
         Slot targetSlot = (Slot)handler.slots.get(toSlotId);
         ItemStack sourceBefore = sourceSlot.getItem();
         if (sourceBefore.isEmpty()) {
            return false;
         } else {
            ItemStack expectedSource = sourceBefore.copy();
            ItemStack targetBefore = targetSlot.getItem();
            if (!targetBefore.isEmpty() && !ItemStack.isSameItemSameComponents(sourceBefore, targetBefore)) {
               return false;
            } else {
               int targetCapacity = slotCapacity(targetSlot, targetBefore.isEmpty() ? sourceBefore : targetBefore);
               int targetCountBefore = targetBefore.isEmpty() ? 0 : targetBefore.getCount();
               int amount = Math.min(count, Math.min(expectedSource.getCount(), Math.max(0, targetCapacity - targetCountBefore)));
               if (amount <= 0) {
                  return false;
               } else if (amount == expectedSource.getCount() && targetBefore.isEmpty()) {
                  return moveStack(handler, mc, fromSlotId, toSlotId);
               } else {
                  mc.gameMode.handleContainerInput(handler.containerId, fromSlotId, 0, ContainerInput.PICKUP, mc.player);
                  if (handler.getCarried().isEmpty()) {
                     return false;
                  } else {
                     int trimSafety = Math.max(0, handler.getCarried().getCount() - amount);

                     while (!handler.getCarried().isEmpty() && handler.getCarried().getCount() > amount && trimSafety-- > 0) {
                        mc.gameMode.handleContainerInput(handler.containerId, fromSlotId, 1, ContainerInput.PICKUP, mc.player);
                     }

                     if (!handler.getCarried().isEmpty() && handler.getCarried().getCount() == amount) {
                        mc.gameMode.handleContainerInput(handler.containerId, toSlotId, 0, ContainerInput.PICKUP, mc.player);
                        if (!handler.getCarried().isEmpty()) {
                           returnCarriedStack(handler, mc, fromSlotId);
                        }

                        ItemStack targetAfter = targetSlot.getItem();
                        return !targetAfter.isEmpty()
                           && ItemStack.isSameItemSameComponents(targetAfter, expectedSource)
                           && targetAfter.getCount() >= targetCountBefore + amount
                           && handler.getCarried().isEmpty();
                     } else {
                        returnCarriedStack(handler, mc, fromSlotId);
                        return false;
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean returnCarriedStack(InventoryMenu handler, Minecraft mc, int preferredSlotId) {
      if (handler == null || mc == null || mc.player == null || mc.gameMode == null) {
         return false;
      } else if (handler.getCarried().isEmpty()) {
         return true;
      } else {
         int returnSlot = preferredSlotId;
         if (preferredSlotId < 0 || preferredSlotId >= handler.slots.size() || !canReturnToSlot((Slot)handler.slots.get(preferredSlotId), handler.getCarried())
            )
          {
            returnSlot = findCursorReturnSlot(handler, handler.getCarried());
         }

         if (returnSlot >= 0 && returnSlot < handler.slots.size()) {
            mc.gameMode.handleContainerInput(handler.containerId, returnSlot, 0, ContainerInput.PICKUP, mc.player);
            return handler.getCarried().isEmpty();
         } else {
            return false;
         }
      }
   }

   private static boolean moveStack(InventoryMenu handler, Minecraft mc, int fromSlotId, int toSlotId) {
      if (mc == null || mc.player == null || mc.gameMode == null) {
         return false;
      } else if (fromSlotId < 0 || fromSlotId >= handler.slots.size()) {
         return false;
      } else if (toSlotId < 0 || toSlotId >= handler.slots.size()) {
         return false;
      } else if (fromSlotId == toSlotId) {
         return true;
      } else if (!handler.getCarried().isEmpty()) {
         return false;
      } else if (((Slot)handler.slots.get(fromSlotId)).getItem().isEmpty()) {
         return false;
      } else if (!((Slot)handler.slots.get(toSlotId)).getItem().isEmpty()) {
         return false;
      } else {
         mc.gameMode.handleContainerInput(handler.containerId, fromSlotId, 0, ContainerInput.PICKUP, mc.player);
         if (handler.getCarried().isEmpty()) {
            return false;
         } else {
            mc.gameMode.handleContainerInput(handler.containerId, toSlotId, 0, ContainerInput.PICKUP, mc.player);
            if (!handler.getCarried().isEmpty()) {
               mc.gameMode.handleContainerInput(handler.containerId, fromSlotId, 0, ContainerInput.PICKUP, mc.player);
               return false;
            } else {
               return ((Slot)handler.slots.get(fromSlotId)).getItem().isEmpty() && !((Slot)handler.slots.get(toSlotId)).getItem().isEmpty();
            }
         }
      }
   }

   private boolean moveOneItemToCursor(InventoryMenu handler, Minecraft mc, int startTargetIndex, int maxTargets) {
      if (handler != null && mc != null && mc.player != null && mc.gameMode != null) {
         if (!handler.getCarried().isEmpty()) {
            return trimCursorToOne(handler, mc);
         } else {
            List<ItemTarget> targets = this.runtimeEntryTargets();
            Set<Integer> storageSlotSet = this.activeStorageSlotIds();
            int limit = Math.min(maxTargets, targets.size());

            for (int i = Math.max(0, startTargetIndex); i < limit; i++) {
               int sourceSlotId = findSourceSlot(handler, targets.get(i), storageSlotSet);
               if (sourceSlotId >= 0 && sourceSlotId < handler.slots.size()) {
                  Slot sourceSlot = (Slot)handler.slots.get(sourceSlotId);
                  if (sourceSlot != null && !sourceSlot.getItem().isEmpty()) {
                     ItemStack expected = sourceSlot.getItem().copy();
                     mc.gameMode.handleContainerInput(handler.containerId, sourceSlotId, 0, ContainerInput.PICKUP, mc.player);
                     if (handler.getCarried().isEmpty()) {
                        return false;
                     }

                     int safety = Math.max(0, handler.getCarried().getCount() - 1);

                     while (handler.getCarried().getCount() > 1 && safety-- > 0) {
                        mc.gameMode.handleContainerInput(handler.containerId, sourceSlotId, 1, ContainerInput.PICKUP, mc.player);
                     }

                     ItemStack carried = handler.getCarried();
                     return !carried.isEmpty() && carried.getCount() == 1 && ItemStack.isSameItemSameComponents(carried, expected);
                  }
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean trimCursorToOne(InventoryMenu handler, Minecraft mc) {
      if (handler == null || mc == null || mc.player == null || mc.gameMode == null) {
         return false;
      } else if (handler.getCarried().isEmpty()) {
         return false;
      } else if (handler.getCarried().getCount() <= 1) {
         return true;
      } else {
         int safety = Math.max(0, handler.getCarried().getCount() - 1);

         while (!handler.getCarried().isEmpty() && handler.getCarried().getCount() > 1 && safety-- > 0) {
            int returnSlotId = findCursorReturnSlot(handler, handler.getCarried());
            if (returnSlotId < 0) {
               break;
            }

            mc.gameMode.handleContainerInput(handler.containerId, returnSlotId, 1, ContainerInput.PICKUP, mc.player);
         }

         return !handler.getCarried().isEmpty() && handler.getCarried().getCount() == 1;
      }
   }

   private static int findCursorReturnSlot(InventoryMenu handler, ItemStack carried) {
      if (handler != null && carried != null && !carried.isEmpty()) {
         for (Slot slot : handler.slots) {
            if (slot != null && slot.index >= 5 && (slot.index < 5 || slot.index > 8) && slot.index != 45 && slot.container instanceof Inventory) {
               ItemStack stack = slot.getItem();
               if (stack.isEmpty()) {
                  return slot.index;
               }

               if (ItemStack.isSameItemSameComponents(stack, carried) && stack.getCount() < Math.min(stack.getMaxStackSize(), slot.getMaxStackSize(stack))) {
                  return slot.index;
               }
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   private static boolean canReturnToSlot(Slot slot, ItemStack carried) {
      if (slot != null && carried != null && !carried.isEmpty()) {
         ItemStack stack = slot.getItem();
         return stack.isEmpty() ? true : ItemStack.isSameItemSameComponents(stack, carried) && stack.getCount() < slotCapacity(slot, stack);
      } else {
         return false;
      }
   }

   private static int slotCapacity(Slot slot, ItemStack stack) {
      return slot != null && stack != null && !stack.isEmpty() ? Math.max(0, Math.min(stack.getMaxStackSize(), slot.getMaxStackSize(stack))) : 0;
   }

   private ItemStack firstMatchingSourceStack(InventoryMenu handler, ItemTarget target, Set<Integer> excludedSlotIds) {
      if (handler != null && target != null) {
         for (Slot slot : handler.slots) {
            if (slot != null && !excludedSlotIds.contains(slot.index) && isEligibleSourceSlot(slot) && matchesSourceSlot(handler, slot, target)) {
               return slot.getItem();
            }
         }

         return ItemStack.EMPTY;
      } else {
         return ItemStack.EMPTY;
      }
   }

   private List<XCarryAction.PutInRow> previewPutInRows(InventoryMenu handler) {
      List<XCarryAction.PutInRow> rows = new ArrayList<>();
      if (handler == null) {
         return rows;
      } else {
         Set<Integer> storageSlotSet = this.activeStorageSlotIds();
         List<Integer> storageSlots = this.putInAutoSlotIds();
         List<ItemTarget> targets = this.runtimeEntryTargets();
         int limit = Math.min(this.maxConfiguredEntries(), targets.size());
         this.resizeDestinations(limit);
         this.resizeAmountSettings(limit);
         int nextTargetIdx = 0;
         Set<Integer> claimedDestinations = new HashSet<>();

         for (int i = 0; i < limit; i++) {
            int dest = this.destinationFor(i);
            if (dest != -1) {
               int targetSlotId;
               if (dest == Integer.MIN_VALUE) {
                  targetSlotId = -1;

                  while (nextTargetIdx < storageSlots.size()) {
                     int candidate = storageSlots.get(nextTargetIdx++);
                     if (candidate >= 0 && candidate < handler.slots.size() && !claimedDestinations.contains(candidate)) {
                        targetSlotId = candidate;
                        break;
                     }
                  }

                  if (targetSlotId < 0) {
                     continue;
                  }
               } else {
                  targetSlotId = dest;
                  if (dest < 0 || dest >= handler.slots.size()) {
                     continue;
                  }
               }

               Slot destSlot = (Slot)handler.slots.get(targetSlotId);
               ItemStack destStack = destSlot.getItem();
               ItemTarget target = targets.get(i);
               if (destStack.isEmpty() || matchesDestinationStack(destStack, target)) {
                  ItemStack sample = destStack.isEmpty() ? this.firstMatchingSourceStack(handler, target, storageSlotSet) : destStack;
                  int maxCount = slotCapacity(destSlot, sample);
                  int currentCount = destStack.isEmpty() ? 0 : destStack.getCount();
                  if (maxCount > currentCount) {
                     claimedDestinations.add(targetSlotId);
                     rows.add(new XCarryAction.PutInRow(i, target, targetSlotId, this.amountModeFor(i), this.amountFor(i), currentCount, maxCount));
                  }
               }
            }
         }

         return rows;
      }
   }

   private static void allocatePutInRows(InventoryMenu handler, List<XCarryAction.PutInRow> rows, Set<Integer> excludedSlotIds) {
      if (handler != null && rows != null && !rows.isEmpty()) {
         Map<String, Integer> available = new LinkedHashMap<>();

         for (Slot slot : handler.slots) {
            if (slot != null && !excludedSlotIds.contains(slot.index) && isEligibleSourceSlot(slot)) {
               ItemStack stack = slot.getItem();
               if (!stack.isEmpty()) {
                  for (XCarryAction.PutInRow row : rows) {
                     if (row.target != null && matchesSourceSlot(handler, slot, row.target)) {
                        String key = targetKey(row.target);
                        available.put(key, available.getOrDefault(key, 0) + stack.getCount());
                        break;
                     }
                  }
               }
            }
         }

         Map<String, List<XCarryAction.PutInRow>> byKey = new LinkedHashMap<>();

         for (XCarryAction.PutInRow rowx : rows) {
            if (rowx.target != null) {
               byKey.computeIfAbsent(targetKey(rowx.target), ignored -> new ArrayList<>()).add(rowx);
            }
         }

         for (Entry<String, List<XCarryAction.PutInRow>> entry : byKey.entrySet()) {
            int remaining = available.getOrDefault(entry.getKey(), 0);

            for (XCarryAction.PutInRow rowxx : entry.getValue()) {
               remaining += rowxx.currentCount;
            }

            List<XCarryAction.PutInRow> fullRows = new ArrayList<>();

            for (XCarryAction.PutInRow rowxx : entry.getValue()) {
               rowxx.allocated = 0;
               rowxx.desiredFinalCount = rowxx.currentCount;
               if (rowxx.amountMode == XCarryAction.AmountMode.CUSTOM) {
                  int desired = Math.min(Math.min(rowxx.customCount, rowxx.maxCount), Math.max(0, remaining));
                  int reserved = Math.max(rowxx.currentCount, desired);
                  rowxx.desiredFinalCount = desired;
                  rowxx.allocated = Math.max(0, desired - rowxx.currentCount);
                  remaining = Math.max(0, remaining - reserved);
               } else {
                  fullRows.add(rowxx);
               }
            }

            if (!fullRows.isEmpty()) {
               int totalForFull = remaining;
               Map<XCarryAction.PutInRow, Integer> desired = new LinkedHashMap<>();

               for (XCarryAction.PutInRow rowxxx : fullRows) {
                  desired.put(rowxxx, 0);
               }

               while (totalForFull > 0) {
                  int active = 0;

                  for (XCarryAction.PutInRow rowxxx : fullRows) {
                     if (desired.get(rowxxx) < rowxxx.maxCount) {
                        active++;
                     }
                  }

                  if (active <= 0) {
                     break;
                  }

                  int share = Math.max(1, totalForFull / active);
                  boolean progressed = false;

                  for (XCarryAction.PutInRow rowxxxx : fullRows) {
                     if (totalForFull <= 0) {
                        break;
                     }

                     int currentDesired = desired.get(rowxxxx);
                     int room = rowxxxx.maxCount - currentDesired;
                     if (room > 0) {
                        int take = Math.min(room, Math.min(share, totalForFull));
                        desired.put(rowxxxx, currentDesired + take);
                        totalForFull -= take;
                        progressed = true;
                     }
                  }

                  if (!progressed) {
                     break;
                  }
               }

               for (XCarryAction.PutInRow rowxxxx : fullRows) {
                  int finalCount = desired.getOrDefault(rowxxxx, 0);
                  rowxxxx.desiredFinalCount = finalCount;
                  rowxxxx.allocated = Math.max(0, finalCount - rowxxxx.currentCount);
               }
            }
         }
      }
   }

   public static boolean hasCraftingGridItems(InventoryMenu handler) {
      if (handler == null) {
         return false;
      } else {
         for (int slotId = 1; slotId <= 4 && slotId < handler.slots.size(); slotId++) {
            if (!((Slot)handler.slots.get(slotId)).getItem().isEmpty()) {
               return true;
            }
         }

         return false;
      }
   }

   public static boolean hasCursorItem(InventoryMenu handler) {
      return handler != null && !handler.getCarried().isEmpty();
   }

   public static boolean hasStoredItems(InventoryMenu handler, boolean includeCursor) {
      return hasCraftingGridItems(handler) || includeCursor && hasCursorItem(handler);
   }

   public static boolean hasStoredItems(InventoryMenu handler, boolean includeCursor, Set<Integer> slotIds) {
      if (handler == null) {
         return false;
      } else {
         if (slotIds != null) {
            for (Integer id : slotIds) {
               if (id != null && id >= 0 && id < handler.slots.size() && !((Slot)handler.slots.get(id)).getItem().isEmpty()) {
                  return true;
               }
            }
         }

         return includeCursor && hasCursorItem(handler);
      }
   }

   private void updateXCarryState(InventoryMenu handler, boolean includeCursor) {
      boolean active = hasStoredItems(handler, includeCursor, this.activeStorageSlotIds());
      RiptideSharedState shared = RiptideSharedState.get();
      if (active) {
         shared.mergeXCarryForcedTargets(this.activeStorageSlotIds(), includeCursor);
         shared.setXCarryForced(true);
         shared.setXCarryActive(true);
      } else {
         if (shared.isXCarryForced()) {
            boolean stillHeld = hasStoredItems(handler, shared.isXCarryForcedCarryCursor(), shared.getXCarryForcedSlotMask());
            if (stillHeld) {
               shared.setXCarryActive(true);
               return;
            }

            shared.setXCarryForced(false);
         }

         shared.setXCarryActive(false);
      }
   }

   private void markXCarryForcedForPutIn(boolean includeCursor) {
      RiptideSharedState shared = RiptideSharedState.get();
      shared.mergeXCarryForcedTargets(this.activeStorageSlotIds(), includeCursor);
      shared.setXCarryForced(true);
      shared.setXCarryActive(true);
   }

   public int maxConfiguredEntries() {
      return 11;
   }

   public static int maxEntriesFor(boolean carryCursor) {
      return 10 + (carryCursor ? 1 : 0);
   }

   static void sendOpenTarget(Minecraft mc, RiptideContainerTarget target) {
      if (target != null) {
         target.interact(mc);
      }
   }

   private static boolean isEligibleSourceSlot(Slot slot) {
      return slot != null && slot.index >= 5 && !slot.getItem().isEmpty();
   }

   private static int resolvePlayerVisibleSlot(AbstractContainerMenu handler, int configuredSlot) {
      for (Slot slot : handler.slots) {
         if (slot != null
            && slot.index >= 0
            && slot.index < handler.slots.size()
            && slot.index > 4
            && (slot.index < 5 || slot.index > 8)
            && slot.index != 45
            && slot.container != null
            && slot.container instanceof Inventory) {
            int visibleSlot = slot.getContainerSlot();
            if (visibleSlot == configuredSlot) {
               return slot.index;
            }
         }
      }

      return -1;
   }

   private static boolean matchesTarget(ItemStack stack, ItemTarget target, int visibleSlot) {
      if (stack == null || stack.isEmpty()) {
         return false;
      } else {
         return target != null && target.hasIdentity() ? target.matches(stack, visibleSlot) : true;
      }
   }

   private static boolean matchesSourceSlot(InventoryMenu handler, Slot slot, ItemTarget target) {
      if (handler != null && slot != null && target != null && !slot.getItem().isEmpty()) {
         int visibleSlot = resolveVisibleSlotForPlayerSlot(handler, slot.index);
         return target.hasSlot() && target.slot != visibleSlot ? false : matchesTarget(slot.getItem(), target, visibleSlot);
      } else {
         return false;
      }
   }

   private static boolean matchesConfiguredStorageSlot(int configuredSlot, int handlerSlotId) {
      return configuredSlot == handlerSlotId ? true : configuredSlot == 100 && handlerSlotId == 0;
   }

   private static boolean matchesDestinationStack(ItemStack stack, ItemTarget target) {
      if (stack == null || stack.isEmpty()) {
         return false;
      } else if (target != null && target.hasIdentity()) {
         ItemTarget identityOnly = target.copy();
         identityOnly.slot = -1;
         return identityOnly.matches(stack, -1);
      } else {
         return true;
      }
   }

   private static String targetKey(ItemTarget target) {
      if (target == null) {
         return "";
      } else {
         String legacy = normalizeEntry(target.toLegacyEntry());
         if (legacy != null && !legacy.isBlank()) {
            return legacy.toLowerCase(Locale.ROOT);
         } else if (target.hasSlot()) {
            return "#slot:" + target.slot;
         } else {
            return target.editorText() == null ? "" : target.editorText().toLowerCase(Locale.ROOT);
         }
      }
   }

   private static int parseEntrySlot(String entry) {
      if (entry != null && entry.startsWith("#")) {
         int separator = entry.indexOf(124);
         String raw = separator >= 0 ? entry.substring(1, separator) : entry.substring(1);

         try {
            return Integer.parseInt(raw);
         } catch (NumberFormatException var4) {
            return -1;
         }
      } else {
         return -1;
      }
   }

   private static String parseEntryName(String entry) {
      if (entry == null || entry.isBlank()) {
         return "";
      } else if (!entry.startsWith("#")) {
         return entry.trim();
      } else {
         int separator = entry.indexOf(124);
         return separator >= 0 && separator + 1 < entry.length() ? entry.substring(separator + 1).trim() : "";
      }
   }

   private static int resolveVisibleSlotForPlayerSlot(InventoryMenu handler, int handlerSlotId) {
      if (handlerSlotId >= 0 && handlerSlotId < handler.slots.size()) {
         Slot slot = (Slot)handler.slots.get(handlerSlotId);
         return slot != null && slot.container instanceof Inventory ? slot.getContainerSlot() : -1;
      } else {
         return -1;
      }
   }

   private List<ItemTarget> resolvedEntryTargets() {
      if (!this.entryTargets.isEmpty()) {
         return this.entryTargets;
      } else {
         for (String entry : this.entries) {
            String normalized = normalizeEntry(entry);
            if (normalized != null) {
               ItemTarget target = ItemTarget.fromLegacyEntry(normalized);
               if (target.hasSlot() || target.hasIdentity()) {
                  this.entryTargets.add(target);
               }

               if (this.entryTargets.size() >= this.maxConfiguredEntries()) {
                  break;
               }
            }
         }

         return this.entryTargets;
      }
   }

   private List<ItemTarget> runtimeEntryTargets() {
      List<ItemTarget> runtime = new ArrayList<>();
      Minecraft mc = Minecraft.getInstance();

      for (ItemTarget target : this.resolvedEntryTargets()) {
         if (target != null) {
            ItemTarget resolved = target.resolveTemplate(mc);
            if (resolved != null && (resolved.hasSlot() || resolved.hasIdentity())) {
               runtime.add(resolved);
            }
         }
      }

      return runtime;
   }

   private void syncLegacyEntries() {
      this.entries.clear();

      for (ItemTarget target : this.entryTargets) {
         if (target != null) {
            String entry = normalizeEntry(target.toLegacyEntry());
            if (entry != null) {
               this.entries.add(entry);
               if (this.entries.size() >= this.maxConfiguredEntries()) {
                  break;
               }
            }
         }
      }
   }

   public static enum AmountMode {
      FULL_STK,
      CUSTOM;
   }

   public static enum Mode {
      PUT_IN,
      TAKE_OUT,
      DROP;
   }

   public static final class PutInMove {
      final int sourceSlotId;
      final int targetSlotId;
      final int count;

      PutInMove(int sourceSlotId, int targetSlotId, int count) {
         this.sourceSlotId = sourceSlotId;
         this.targetSlotId = targetSlotId;
         this.count = count;
      }

      public int sourceSlotId() {
         return this.sourceSlotId;
      }

      public int targetSlotId() {
         return this.targetSlotId;
      }

      public int count() {
         return this.count;
      }
   }

   private static final class PutInRow {
      final int index;
      final ItemTarget target;
      final int targetSlotId;
      final XCarryAction.AmountMode amountMode;
      final int customCount;
      final int currentCount;
      final int maxCount;
      int allocated;
      int desiredFinalCount;

      PutInRow(int index, ItemTarget target, int targetSlotId, XCarryAction.AmountMode amountMode, int customCount, int currentCount, int maxCount) {
         this.index = index;
         this.target = target;
         this.targetSlotId = targetSlotId;
         this.amountMode = amountMode;
         this.customCount = customCount;
         this.currentCount = currentCount;
         this.maxCount = maxCount;
      }
   }

   public static enum TransferMode {
      FAST,
      CLICK,
      SAFE_CLICK;
   }
}
