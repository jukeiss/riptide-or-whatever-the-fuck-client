package riptide.util.macro;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import riptide.util.RiptideGuiActions;
import riptide.util.RiptideInventoryClickHelper;
import riptide.util.RiptideInventoryHelper;

public class StoreItemAction implements MacroAction {
   public StoreItemAction.Mode mode = StoreItemAction.Mode.STORE;
   public List<String> targetItems = new ArrayList<>();
   public List<ItemTarget> itemTargets = new ArrayList<>();
   public boolean persistent = false;
   public boolean closeAfter = false;
   public boolean allItems = false;
   public boolean closeSendPkt = true;
   public int delayTicks = 0;
   private boolean enabled = true;

   public void doTransfer(Minecraft mc) {
      if (mc.player != null && mc.gameMode != null) {
         AbstractContainerMenu h = mc.player.containerMenu;
         if (h != mc.player.inventoryMenu) {
            for (int slotId : this.collectTransferSlots(mc)) {
               RiptideInventoryClickHelper.click(mc, slotId, 0, ContainerInput.QUICK_MOVE);
            }

            if (this.closeAfter && !this.persistent) {
               RiptideGuiActions.closeCurrentScreen(mc, this.closeSendPkt, false);
            }
         }
      }
   }

   @Override
   public void execute(Minecraft mc) {
      this.doTransfer(mc);
   }

   public boolean matchesAny(ItemStack stack) {
      return this.matchesAny(stack, -1);
   }

   public boolean matchesAny(ItemStack stack, int visibleSlot) {
      if (stack.isEmpty()) {
         return false;
      } else if (this.allItems) {
         return true;
      } else {
         List<ItemTarget> targets = this.runtimeTargets(Minecraft.getInstance());
         if (targets.isEmpty()) {
            return false;
         } else {
            for (ItemTarget target : targets) {
               if (this.matchesTargetEntry(stack, visibleSlot, target)) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   public List<Integer> collectTransferSlots(Minecraft mc) {
      List<Integer> slotsToMove = new ArrayList<>();
      if (mc != null && mc.player != null && mc.gameMode != null) {
         AbstractContainerMenu h = mc.player.containerMenu;
         if (h != null && h != mc.player.inventoryMenu) {
            for (int i = 0; i < h.slots.size(); i++) {
               Slot slot = (Slot)h.slots.get(i);
               if (slot != null) {
                  boolean playerInventorySlot = RiptideInventoryHelper.isInventorySlot(mc, slot);
                  if ((this.mode != StoreItemAction.Mode.LOOT || !playerInventorySlot) && (this.mode != StoreItemAction.Mode.STORE || playerInventorySlot)) {
                     ItemStack stack = slot.getItem();
                     if (!stack.isEmpty()) {
                        int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(mc, i);
                        if (this.matchesAny(stack, visibleSlot)) {
                           slotsToMove.add(i);
                        }
                     }
                  }
               }
            }

            return slotsToMove;
         } else {
            return slotsToMove;
         }
      } else {
         return slotsToMove;
      }
   }

   private boolean matchesTargetEntry(ItemStack stack, int visibleSlot, ItemTarget target) {
      return target != null && (target.hasSlot() || target.hasIdentity()) ? target.matches(stack, visibleSlot) : false;
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.STORE_ITEM;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "STORE_ITEM");
      tag.putString("mode", this.mode.name());
      tag.putBoolean("persistent", this.persistent);
      tag.putBoolean("closeAfter", this.closeAfter);
      tag.putBoolean("allItems", this.allItems);
      tag.putBoolean("closeSendPkt", this.closeSendPkt);
      tag.putInt("delayTicks", this.delayTicks);
      tag.put("targetItems", ItemTarget.toTagList(this.resolvedTargets()));
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("mode")) {
         try {
            this.mode = StoreItemAction.Mode.valueOf(tag.getStringOr("mode", "LOOT"));
         } catch (IllegalArgumentException var3) {
            this.mode = StoreItemAction.Mode.LOOT;
         }
      }

      if (tag.contains("persistent")) {
         this.persistent = tag.getBooleanOr("persistent", false);
      }

      if (tag.contains("closeAfter")) {
         this.closeAfter = tag.getBooleanOr("closeAfter", false);
      }

      if (tag.contains("allItems")) {
         this.allItems = tag.getBooleanOr("allItems", false);
      }

      if (tag.contains("closeSendPkt")) {
         this.closeSendPkt = tag.getBooleanOr("closeSendPkt", true);
      }

      if (tag.contains("delayTicks")) {
         this.delayTicks = clampDelayTicks(tag.getIntOr("delayTicks", 0));
      }

      this.itemTargets.clear();
      this.targetItems.clear();
      if (tag.contains("targetItems")) {
         ListTag list = tag.getList("targetItems").orElse(new ListTag());
         this.itemTargets.addAll(ItemTarget.fromElementList(list));
      }

      this.syncLegacyTargetItems();
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public String getDisplayName() {
      String modeStr = this.mode == StoreItemAction.Mode.LOOT ? "Loot" : "Store";
      if (this.allItems) {
         return modeStr + " All" + (this.persistent ? " [Inf]" : "");
      } else {
         List<ItemTarget> targets = this.resolvedTargets();
         if (targets.isEmpty()) {
            return modeStr + " (nothing)";
         } else {
            String what = targets.size() == 1 ? formatTargetEntry(targets.get(0).toLegacyEntry()) : targets.size() + " items";
            return modeStr + " " + what + (this.persistent ? " [Inf]" : "");
         }
      }
   }

   public static String normalizeTargetEntry(String raw) {
      if (raw == null) {
         return null;
      } else {
         String trimmed = raw.trim();
         if (trimmed.isEmpty()) {
            return null;
         } else {
            if (!trimmed.startsWith("#") && trimmed.matches("\\d+")) {
               trimmed = "#" + trimmed;
            }

            if (!trimmed.startsWith("#")) {
               return trimmed;
            } else {
               int separator = trimmed.indexOf(124);
               String slotRaw = separator >= 0 ? trimmed.substring(1, separator).trim() : trimmed.substring(1).trim();
               if (slotRaw.isEmpty()) {
                  return null;
               } else {
                  try {
                     int slot = Integer.parseInt(slotRaw);
                     if (slot < 0) {
                        return null;
                     } else {
                        String itemPart = separator >= 0 && separator + 1 < trimmed.length() ? trimmed.substring(separator + 1).trim() : "";
                        return itemPart.isEmpty() ? "#" + slot : "#" + slot + "|" + itemPart;
                     }
                  } catch (NumberFormatException var6) {
                     return trimmed;
                  }
               }
            }
         }
      }
   }

   public static String buildCapturedTargetEntry(String itemName, String registryId, int visibleSlot) {
      String itemPart = registryId != null && !registryId.isBlank() ? registryId.trim() : (itemName != null ? itemName.trim() : "");
      if (!itemPart.isEmpty()) {
         return itemPart;
      } else {
         return visibleSlot >= 0 ? "#" + visibleSlot : null;
      }
   }

   public static String formatTargetEntry(String entry) {
      if (entry != null && !entry.isBlank()) {
         ItemTarget target = ItemTarget.fromLegacyEntry(entry);
         String label = target.displayLabel();
         if (target.hasSlot() && label.isBlank()) {
            return "#" + target.slot;
         } else {
            return target.hasSlot() ? target.slot + ": " + label : label;
         }
      } else {
         return "";
      }
   }

   private static int parseTargetSlot(String entry) {
      if (entry != null && entry.startsWith("#")) {
         int separator = entry.indexOf(124);
         String slotRaw = separator >= 0 ? entry.substring(1, separator).trim() : entry.substring(1).trim();
         if (slotRaw.isEmpty()) {
            return -1;
         } else {
            try {
               int slot = Integer.parseInt(slotRaw);
               return slot >= 0 ? slot : -1;
            } catch (NumberFormatException var4) {
               return -1;
            }
         }
      } else {
         return -1;
      }
   }

   private static String parseTargetName(String entry) {
      if (entry != null && !entry.isBlank()) {
         int separator = entry.indexOf(124);
         return separator >= 0 && separator + 1 < entry.length() ? entry.substring(separator + 1).trim() : "";
      } else {
         return "";
      }
   }

   @Override
   public String getIcon() {
      return this.mode == StoreItemAction.Mode.LOOT ? "LOT" : "STR";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   private List<ItemTarget> resolvedTargets() {
      if (!this.itemTargets.isEmpty()) {
         return this.itemTargets;
      } else {
         for (String target : this.targetItems) {
            ItemTarget parsed = ItemTarget.fromLegacyEntry(target);
            if (parsed.hasSlot() || parsed.hasIdentity()) {
               this.itemTargets.add(parsed);
            }
         }

         return this.itemTargets;
      }
   }

   private List<ItemTarget> runtimeTargets(Minecraft mc) {
      List<ItemTarget> runtime = new ArrayList<>();

      for (ItemTarget target : this.resolvedTargets()) {
         if (target != null) {
            ItemTarget resolved = target.resolveTemplate(mc);
            if (resolved != null && (resolved.hasSlot() || resolved.hasIdentity())) {
               runtime.add(resolved);
            }
         }
      }

      return runtime;
   }

   private void syncLegacyTargetItems() {
      this.targetItems.clear();

      for (ItemTarget target : this.itemTargets) {
         if (target != null) {
            String entry = target.toLegacyEntry();
            if (!entry.isBlank()) {
               this.targetItems.add(entry);
            }
         }
      }
   }

   public static int clampDelayTicks(int value) {
      return Math.max(0, Math.min(200, value));
   }

   public static enum Mode {
      LOOT,
      STORE;
   }
}
