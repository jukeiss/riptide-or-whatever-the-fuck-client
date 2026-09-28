package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class WaitInventoryPredicateAction implements MacroAction, MacroCaptureOutput {
   public WaitInventoryPredicateAction.InventoryCondition condition = WaitInventoryPredicateAction.InventoryCondition.ITEM_EXISTS;
   public String itemName = "";
   public int count = 1;
   public int slot = 0;
   public int timeoutMs = 0;
   public boolean listenDuringPreviousAction = false;
   public String saveAs = "";
   private transient boolean baselineCaptured = false;
   private transient int baselineCount = 0;
   private transient boolean baselineSlotEmpty = false;

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.WAIT_INVENTORY_PREDICATE;
   }

   public boolean matches(Minecraft mc) {
      if (mc != null && mc.player != null) {
         MacroTemplate.Resolution resolution = MacroVariables.resolve(this.itemName, mc);
         if (!resolution.success()) {
            return false;
         } else {
            ItemTarget target = ItemTarget.fromLegacyEntry(resolution.value());
            this.captureBaselineIfNeeded(mc, target);

            return switch (this.condition) {
               case ITEM_EXISTS -> hasInventoryItem(mc, target);
               case COUNT_AT_LEAST -> countInventory(mc, target) >= Math.max(1, this.count);
               case COUNT_CHANGED -> countInventory(mc, target) != this.baselineCount;
               case COUNT_INCREASED -> countInventory(mc, target) > this.baselineCount;
               case COUNT_DECREASED -> countInventory(mc, target) < this.baselineCount;
               case SLOT_EMPTY -> safeStack(mc, this.slot).isEmpty();
               case SLOT_FILLED -> !safeStack(mc, this.slot).isEmpty();
               case SLOT_CHANGED -> safeStack(mc, this.slot).isEmpty() != this.baselineSlotEmpty;
               case INVENTORY_FULL -> isInventoryFull(mc);
               case INVENTORY_EMPTY -> isInventoryEmpty(mc);
               case CURSOR_MATCHES -> target.score(mc.player.containerMenu.getCarried(), -1) >= 0;
               case CURSOR_EMPTY -> mc.player.containerMenu.getCarried().isEmpty();
               case CURSOR_FILLED -> !mc.player.containerMenu.getCarried().isEmpty();
               case SELECTED_SLOT -> mc.player.getInventory().getSelectedSlot() == Math.max(0, Math.min(8, this.slot));
            };
         }
      } else {
         return false;
      }
   }

   public void resetBaseline() {
      this.baselineCaptured = false;
   }

   private void captureBaselineIfNeeded(Minecraft mc, ItemTarget target) {
      if (!this.baselineCaptured) {
         this.baselineCount = countInventory(mc, target);
         this.baselineSlotEmpty = safeStack(mc, this.slot).isEmpty();
         this.baselineCaptured = true;
      }
   }

   static boolean hasInventoryItem(Minecraft mc, ItemTarget target) {
      return countInventory(mc, target) > 0;
   }

   static int countInventory(Minecraft mc, ItemTarget target) {
      if (mc != null && mc.player != null && target != null && target.hasIdentity()) {
         int total = 0;

         for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (target.score(stack, i) >= 0) {
               total += stack.getCount();
            }
         }

         return total;
      } else {
         return 0;
      }
   }

   private static ItemStack safeStack(Minecraft mc, int slot) {
      if (mc != null && mc.player != null) {
         int idx = Math.max(0, Math.min(mc.player.getInventory().getContainerSize() - 1, slot));
         return mc.player.getInventory().getItem(idx);
      } else {
         return ItemStack.EMPTY;
      }
   }

   private static boolean isInventoryFull(Minecraft mc) {
      for (int i = 0; i < 36; i++) {
         if (mc.player.getInventory().getItem(i).isEmpty()) {
            return false;
         }
      }

      return true;
   }

   private static boolean isInventoryEmpty(Minecraft mc) {
      for (int i = 0; i < 36; i++) {
         if (!mc.player.getInventory().getItem(i).isEmpty()) {
            return false;
         }
      }

      return true;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "WAIT_INVENTORY_PREDICATE");
      tag.putString("condition", this.condition.name());
      tag.putString("itemName", this.itemName);
      tag.putInt("count", this.count);
      tag.putInt("slot", this.slot);
      tag.putInt("timeoutMs", this.timeoutMs);
      MacroWaitOptions.write(tag, this);
      tag.putString("saveAs", this.saveAs);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.condition = MacroStringList.enumValue(
         WaitInventoryPredicateAction.InventoryCondition.class,
         tag.getStringOr("condition", "ITEM_EXISTS"),
         WaitInventoryPredicateAction.InventoryCondition.ITEM_EXISTS
      );
      this.itemName = tag.getStringOr("itemName", "");
      this.count = tag.getIntOr("count", 1);
      this.slot = tag.getIntOr("slot", 0);
      this.timeoutMs = tag.getIntOr("timeoutMs", 0);
      MacroWaitOptions.read(tag, this);
      this.saveAs = tag.getStringOr("saveAs", "");
   }

   @Override
   public String getDisplayName() {
      return "Wait inventory " + this.condition;
   }

   @Override
   public String getIcon() {
      return "I";
   }

   @Override
   public String getSaveAs() {
      return this.saveAs;
   }

   @Override
   public void setSaveAs(String name) {
      this.saveAs = name == null ? "" : name;
   }

   public static enum InventoryCondition {
      ITEM_EXISTS,
      COUNT_AT_LEAST,
      COUNT_CHANGED,
      COUNT_INCREASED,
      COUNT_DECREASED,
      SLOT_EMPTY,
      SLOT_FILLED,
      SLOT_CHANGED,
      INVENTORY_FULL,
      INVENTORY_EMPTY,
      CURSOR_MATCHES,
      CURSOR_EMPTY,
      CURSOR_FILLED,
      SELECTED_SLOT;
   }
}
