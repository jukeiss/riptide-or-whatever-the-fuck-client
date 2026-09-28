package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class WaitDurabilityAction implements MacroAction {
   public WaitDurabilityAction.TargetMode targetMode = WaitDurabilityAction.TargetMode.HELD;
   public String itemName = "";
   public int slot = 0;
   public WaitDurabilityAction.Measurement measurement = WaitDurabilityAction.Measurement.REMAINING;
   public WaitDurabilityAction.Comparison comparison = WaitDurabilityAction.Comparison.AT_MOST;
   public int value = 2;
   public int timeoutMs = 0;
   public boolean listenDuringPreviousAction = false;
   public boolean useNext = false;
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.WAIT_DURABILITY;
   }

   public boolean matches(Minecraft mc) {
      if (mc != null && mc.player != null) {
         return switch (this.targetMode) {
            case HELD -> this.matchesStack(mc.player.getMainHandItem()) || this.matchesStack(mc.player.getOffhandItem());
            case ITEM -> this.matchesItemTarget(mc);
            case SLOT -> this.matchesStack(this.stackForSlot(mc, this.slot));
         };
      } else {
         return false;
      }
   }

   private boolean matchesItemTarget(Minecraft mc) {
      ItemTarget target = ItemTarget.fromLegacyEntry(this.itemName).resolveTemplate(mc);
      if (target == null) {
         return false;
      } else if (!target.hasIdentity() && !target.hasSlot()) {
         return this.matchesStack(mc.player.getMainHandItem()) || this.matchesStack(mc.player.getOffhandItem());
      } else {
         ItemTarget itemOnly = target.copy();
         if (itemOnly.hasIdentity()) {
            itemOnly.slot = -1;
         }

         int size = Math.min(36, mc.player.getInventory().getContainerSize());

         for (int i = 0; i < size; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (itemOnly.score(stack, i) >= 0 && this.matchesStack(stack)) {
               return true;
            }
         }

         return itemOnly.score(mc.player.getOffhandItem(), 40) >= 0 && this.matchesStack(mc.player.getOffhandItem());
      }
   }

   private ItemStack stackForSlot(Minecraft mc, int visibleSlot) {
      if (mc == null || mc.player == null) {
         return ItemStack.EMPTY;
      } else if (visibleSlot == 40) {
         return mc.player.getOffhandItem();
      } else {
         int size = mc.player.getInventory().getContainerSize();
         int index = Math.max(0, Math.min(Math.max(0, size - 1), visibleSlot));
         return mc.player.getInventory().getItem(index);
      }
   }

   public boolean matchesStack(ItemStack stack) {
      if (stack != null && !stack.isEmpty() && stack.isDamageableItem()) {
         int metric = this.durabilityMetric(stack);
         int target = this.clampTargetValue(stack);
         return this.compare(metric, target);
      } else {
         return false;
      }
   }

   private int durabilityMetric(ItemStack stack) {
      int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());

      return switch (this.measurement) {
         case REMAINING -> remaining;
         case DAMAGE_USED -> Math.max(0, stack.getDamageValue());
         case PERCENT_REMAINING -> stack.getMaxDamage() <= 0 ? 100 : Math.round(remaining * 100.0F / stack.getMaxDamage());
      };
   }

   private int clampTargetValue(ItemStack stack) {
      int max = this.measurement == WaitDurabilityAction.Measurement.PERCENT_REMAINING ? 100 : Math.max(0, stack == null ? 2048 : stack.getMaxDamage());
      return Math.max(0, Math.min(max, this.value));
   }

   private boolean compare(int actual, int target) {
      return switch (this.comparison) {
         case BELOW -> actual < target;
         case AT_MOST -> actual <= target;
         case EXACT -> actual == target;
         case AT_LEAST -> actual >= target;
         case ABOVE -> actual > target;
      };
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("targetMode", this.targetMode.name());
      tag.putString("itemName", this.itemName == null ? "" : this.itemName);
      tag.putInt("slot", Math.max(0, Math.min(40, this.slot)));
      tag.putString("measurement", this.measurement.name());
      tag.putString("comparison", this.comparison.name());
      tag.putInt("value", Math.max(0, this.value));
      tag.putInt("timeoutMs", Math.max(0, this.timeoutMs));
      tag.putBoolean("useNext", this.useNext);
      tag.putBoolean("enabled", this.enabled);
      MacroWaitOptions.write(tag, this);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.targetMode = MacroStringList.enumValue(
         WaitDurabilityAction.TargetMode.class,
         tag.getStringOr("targetMode", WaitDurabilityAction.TargetMode.HELD.name()),
         WaitDurabilityAction.TargetMode.HELD
      );
      this.itemName = tag.getStringOr("itemName", "");
      this.slot = Math.max(0, Math.min(40, tag.getIntOr("slot", 0)));
      this.measurement = MacroStringList.enumValue(
         WaitDurabilityAction.Measurement.class,
         tag.getStringOr("measurement", WaitDurabilityAction.Measurement.REMAINING.name()),
         WaitDurabilityAction.Measurement.REMAINING
      );
      this.comparison = MacroStringList.enumValue(
         WaitDurabilityAction.Comparison.class,
         tag.getStringOr("comparison", WaitDurabilityAction.Comparison.AT_MOST.name()),
         WaitDurabilityAction.Comparison.AT_MOST
      );
      this.value = Math.max(0, tag.getIntOr("value", 2));
      this.timeoutMs = Math.max(0, tag.getIntOr("timeoutMs", 0));
      this.useNext = tag.getBooleanOr("useNext", false);
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }

      MacroWaitOptions.read(tag, this);
   }

   @Override
   public String getDisplayName() {
      return "Wait Durability "
         + this.targetLabel()
         + " "
         + this.metricLabel()
         + " "
         + this.comparisonLabel()
         + " "
         + Math.max(0, this.value)
         + (this.useNext ? " + next" : "");
   }

   private String targetLabel() {
      return switch (this.targetMode) {
         case HELD -> "Held";
         case ITEM -> this.itemName != null && !this.itemName.isBlank() ? ItemTarget.fromLegacyEntry(this.itemName).summaryText() : "Held";
         case SLOT -> "#" + Math.max(0, Math.min(40, this.slot));
      };
   }

   private String metricLabel() {
      return switch (this.measurement) {
         case REMAINING -> "remaining";
         case DAMAGE_USED -> "damage";
         case PERCENT_REMAINING -> "%";
      };
   }

   private String comparisonLabel() {
      return switch (this.comparison) {
         case BELOW -> "<";
         case AT_MOST -> "<=";
         case EXACT -> "=";
         case AT_LEAST -> ">=";
         case ABOVE -> ">";
      };
   }

   @Override
   public String getIcon() {
      return "DUR";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   public static enum Comparison {
      BELOW,
      AT_MOST,
      EXACT,
      AT_LEAST,
      ABOVE;
   }

   public static enum Measurement {
      REMAINING,
      DAMAGE_USED,
      PERCENT_REMAINING;
   }

   public static enum TargetMode {
      HELD,
      ITEM,
      SLOT;
   }
}
