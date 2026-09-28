package riptide.util.macro;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import riptide.util.RiptideDropHelper;
import riptide.util.RiptideInventoryHelper;

public class DropAction implements MacroAction, WaitsForGui, PacketOrdered {
   public DropAction.DropMode mode = DropAction.DropMode.TIMES;
   public int dropCount = 1;
   public List<String> itemNames = new ArrayList<>();
   public List<ItemTarget> itemTargets = new ArrayList<>();
   public List<Integer> itemCounts = new ArrayList<>();
   public boolean waitForGuiBefore = false;
   public boolean waitForGuiAfter = false;
   public String guiName = "";
   public boolean useHandlerSlots = true;
   public volatile PacketOrder packetOrder = PacketOrder.INSTANT;
   private boolean enabled = true;

   public int getItemCount(int index) {
      if (index >= 0 && index < this.itemCounts.size()) {
         return Math.max(0, this.itemCounts.get(index));
      } else {
         return this.mode == DropAction.DropMode.ALL ? 0 : Math.max(1, this.dropCount);
      }
   }

   public boolean hasSpecificTargets() {
      for (ItemTarget target : this.resolvedItemTargets()) {
         if (target != null && (target.hasIdentity() || target.hasSlot())) {
            return true;
         }
      }

      return false;
   }

   public List<ItemTarget> resolvedTargets() {
      return List.copyOf(this.resolvedItemTargets());
   }

   @Override
   public void execute(Minecraft mc) {
      if (mc.player != null && mc.gameMode != null) {
         List<ItemTarget> targets = this.resolvedItemTargets();
         if (!this.hasSpecificTargets()) {
            if (this.mode == DropAction.DropMode.ALL) {
               for (int i = 0; i < 36; i++) {
                  if (!mc.player.getInventory().getItem(i).isEmpty()) {
                     if (this.useHandlerSlots) {
                        int resolved = RiptideInventoryHelper.toHandlerSlot(mc, i);
                        if (resolved >= 0) {
                           this.dropFromHandlerSlot(mc, resolved, 0);
                        }
                     } else {
                        this.dropFromInvSlot(mc, i, 0);
                     }
                  }
               }
            } else {
               int selected = Math.max(0, Math.min(8, mc.player.getInventory().getSelectedSlot()));
               int count = Math.max(1, this.dropCount);
               if (this.useHandlerSlots) {
                  int resolved = RiptideInventoryHelper.toHandlerSlot(mc, selected);
                  if (resolved >= 0) {
                     this.dropFromHandlerSlot(mc, resolved, count);
                  }
               } else {
                  this.dropFromInvSlot(mc, selected, count);
               }
            }
         } else {
            if (!targets.isEmpty()) {
               for (int ei = 0; ei < targets.size(); ei++) {
                  ItemTarget entryTarget = this.getItemTarget(ei).resolveTemplate(mc);
                  if (entryTarget != null) {
                     int count = this.getItemCount(ei);
                     String entryName = parseEntryName(entryTarget.toLegacyEntry());
                     int configuredSlot = entryTarget.hasSlot() ? entryTarget.slot : -1;
                     if (configuredSlot >= 0 && !entryName.isEmpty()) {
                        this.dropMatchingItemInSpecificSlot(mc, configuredSlot, entryTarget, count);
                     } else if (configuredSlot >= 0) {
                        if (this.useHandlerSlots) {
                           int resolvedSlot = RiptideInventoryHelper.resolveConfiguredHandlerSlot(mc, configuredSlot);
                           if (resolvedSlot >= 0) {
                              this.dropFromHandlerSlot(mc, resolvedSlot, count);
                           }
                        } else {
                           this.dropFromInvSlot(mc, configuredSlot, count);
                        }
                     } else if (!entryName.isEmpty()) {
                        this.dropMatchingItemsByTarget(mc, entryTarget, count);
                     }
                  }
               }
            }
         }
      }
   }

   private void dropMatchingItemInSpecificSlot(Minecraft mc, int configuredSlot, String itemName, int count) {
      this.dropMatchingItemInSpecificSlot(mc, configuredSlot, ItemTarget.fromLegacyEntry(itemName), count);
   }

   private void dropMatchingItemInSpecificSlot(Minecraft mc, int configuredSlot, ItemTarget target, int count) {
      if (target.hasIdentity()) {
         if (this.useHandlerSlots) {
            AbstractContainerMenu handler = mc.player.containerMenu;
            if (handler != null) {
               int resolvedSlot = RiptideInventoryHelper.resolveConfiguredHandlerSlot(mc, configuredSlot);
               if (resolvedSlot >= 0 && resolvedSlot < handler.slots.size()) {
                  Slot slot = (Slot)handler.slots.get(resolvedSlot);
                  if (slot != null && !slot.getItem().isEmpty()) {
                     int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(mc, resolvedSlot);
                     if (target.matches(slot.getItem(), visibleSlot)) {
                        int toDrop = count == 0 ? 0 : Math.min(count, slot.getItem().getCount());
                        this.dropFromHandlerSlot(mc, resolvedSlot, toDrop);
                     }
                  }
               }
            }
         } else if (configuredSlot >= 0 && configuredSlot < 36) {
            ItemStack stack = mc.player.getInventory().getItem(configuredSlot);
            if (!stack.isEmpty() && target.matches(stack, configuredSlot)) {
               int toDrop = count == 0 ? 0 : Math.min(count, stack.getCount());
               this.dropFromInvSlot(mc, configuredSlot, toDrop);
            }
         }
      }
   }

   private void dropMatchingItemsByName(Minecraft mc, String itemName, int count) {
      this.dropMatchingItemsByTarget(mc, ItemTarget.fromLegacyEntry(itemName), count);
   }

   private void dropMatchingItemsByTarget(Minecraft mc, ItemTarget target, int count) {
      if (target.hasIdentity()) {
         int remaining = count;
         if (this.useHandlerSlots) {
            AbstractContainerMenu handler = mc.player.containerMenu;
            if (handler != null) {
               for (Slot s : handler.slots) {
                  if (s != null && !s.getItem().isEmpty()) {
                     int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(mc, s.index);
                     if (target.matches(s.getItem(), visibleSlot)) {
                        if (count == 0) {
                           this.dropFromHandlerSlot(mc, s.index, 0);
                        } else {
                           if (remaining <= 0) {
                              break;
                           }

                           int toDrop = Math.min(remaining, s.getItem().getCount());
                           this.dropFromHandlerSlot(mc, s.index, toDrop);
                           remaining -= toDrop;
                        }
                     }
                  }
               }
            }
         } else {
            for (int i = 0; i < 36; i++) {
               ItemStack stack = mc.player.getInventory().getItem(i);
               if (!stack.isEmpty() && target.matches(stack, i)) {
                  if (count == 0) {
                     this.dropFromInvSlot(mc, i, 0);
                  } else {
                     if (remaining <= 0) {
                        break;
                     }

                     int toDrop = Math.min(remaining, stack.getCount());
                     this.dropFromInvSlot(mc, i, toDrop);
                     remaining -= toDrop;
                  }
               }
            }
         }
      }
   }

   private void dropFromInvSlot(Minecraft mc, int invSlot, int count) {
      RiptideDropHelper.dropFromInventorySlot(mc, invSlot, count);
   }

   private void dropFromHandlerSlot(Minecraft mc, int handlerSlotId, int count) {
      RiptideDropHelper.dropFromHandlerSlot(mc, handlerSlotId, count);
   }

   @Override
   public boolean isWaitForGuiBefore() {
      return this.waitForGuiBefore;
   }

   @Override
   public void setWaitForGuiBefore(boolean v) {
      this.waitForGuiBefore = v;
   }

   @Override
   public boolean isWaitForGuiAfter() {
      return this.waitForGuiAfter;
   }

   @Override
   public void setWaitForGuiAfter(boolean v) {
      this.waitForGuiAfter = v;
   }

   @Override
   public String getWaitGuiName() {
      return this.guiName;
   }

   @Override
   public void setWaitGuiName(String name) {
      this.guiName = name;
   }

   @Override
   public PacketOrder getPacketOrder() {
      return this.packetOrder;
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.DROP;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "DROP");
      tag.putString("mode", this.mode.name());
      tag.putInt("count", this.dropCount);
      tag.put("itemNames", ItemTarget.toTagList(this.resolvedItemTargets()));
      ListTag counts = new ListTag();

      for (int c : this.itemCounts) {
         counts.add(StringTag.valueOf(String.valueOf(c)));
      }

      tag.put("itemCounts", counts);
      tag.putBoolean("waitForGuiBefore", this.waitForGuiBefore);
      tag.putBoolean("waitForGuiAfter", this.waitForGuiAfter);
      tag.putString("guiName", this.guiName);
      tag.putBoolean("useHandlerSlots", this.useHandlerSlots);
      tag.putBoolean("enabled", this.enabled);
      PacketOrdered.save(tag, this.packetOrder);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      try {
         this.mode = DropAction.DropMode.valueOf(tag.getStringOr("mode", "TIMES"));
      } catch (IllegalArgumentException var7) {
         this.mode = DropAction.DropMode.TIMES;
      }

      this.dropCount = tag.getIntOr("count", 1);
      this.itemTargets.clear();
      this.itemNames.clear();
      if (tag.contains("itemNames")) {
         ListTag nl = tag.getList("itemNames").orElse(new ListTag());
         this.itemTargets.addAll(ItemTarget.fromElementList(nl));
      } else if (tag.contains("itemName")) {
         String old = tag.getStringOr("itemName", "");
         if (!old.isEmpty()) {
            this.itemTargets.add(ItemTarget.fromLegacyEntry(old));
         }
      }

      this.syncLegacyItemNames();
      this.itemCounts.clear();
      if (tag.contains("itemCounts")) {
         for (Tag el : tag.getList("itemCounts").orElse(new ListTag())) {
            try {
               this.itemCounts.add(Integer.parseInt(el.asString().orElse("1")));
            } catch (NumberFormatException var6) {
               this.itemCounts.add(1);
            }
         }
      }

      while (this.itemCounts.size() < this.itemNames.size()) {
         this.itemCounts.add(this.getItemCount(this.itemCounts.size()));
      }

      this.waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
      this.waitForGuiAfter = WaitsForGui.loadAfter(tag, false);
      if (tag.contains("guiName")) {
         this.guiName = tag.getStringOr("guiName", "");
      }

      this.useHandlerSlots = true;
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }

      this.packetOrder = PacketOrdered.load(tag);
   }

   @Override
   public String getDisplayName() {
      List<ItemTarget> targets = this.resolvedItemTargets();
      if (targets.isEmpty()) {
         String untargeted = this.mode == DropAction.DropMode.ALL ? "Drop Entire Inventory" : "Drop Held Item x" + Math.max(1, this.dropCount);
         return untargeted + WaitsForGui.timingLabel(this);
      } else {
         String e0 = targets.get(0).toLegacyEntry();
         int slot = parseEntrySlot(e0);
         String itemName = parseEntryName(e0);
         String first;
         if (slot >= 0 && !itemName.isEmpty()) {
            first = "\"" + itemName + "\" @ Slot " + slot;
         } else if (slot >= 0) {
            first = "Slot " + slot;
         } else {
            first = "\"" + itemName + "\"";
         }

         if (targets.size() > 1) {
            first = first + " (+" + (targets.size() - 1) + ")";
         }

         String base = this.mode == DropAction.DropMode.ALL ? "Drop All [" + first + "]" : "Drop [" + first + "]";
         return base + WaitsForGui.timingLabel(this);
      }
   }

   @Override
   public String getIcon() {
      return "D";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
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

   private List<ItemTarget> resolvedItemTargets() {
      if (!this.itemTargets.isEmpty()) {
         return this.itemTargets;
      } else {
         for (String itemName : this.itemNames) {
            ItemTarget target = ItemTarget.fromLegacyEntry(itemName);
            if (target.hasSlot() || target.hasIdentity()) {
               this.itemTargets.add(target);
            }
         }

         return this.itemTargets;
      }
   }

   private ItemTarget getItemTarget(int index) {
      List<ItemTarget> targets = this.resolvedItemTargets();
      return index >= 0 && index < targets.size() ? targets.get(index) : new ItemTarget();
   }

   private void syncLegacyItemNames() {
      this.itemNames.clear();

      for (ItemTarget target : this.itemTargets) {
         if (target != null) {
            String entry = target.toLegacyEntry();
            if (!entry.isBlank()) {
               this.itemNames.add(entry);
            }
         }
      }
   }

   public static enum DropMode {
      ALL,
      TIMES;
   }
}
