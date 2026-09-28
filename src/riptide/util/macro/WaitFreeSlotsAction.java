package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class WaitFreeSlotsAction implements MacroAction {
   public WaitFreeSlotsAction.CountMode countMode = WaitFreeSlotsAction.CountMode.FREE_SLOTS;
   public WaitFreeSlotsAction.Comparison comparison = WaitFreeSlotsAction.Comparison.AT_MOST;
   public int slots = 0;
   public int timeoutMs = 0;
   public boolean listenDuringPreviousAction = false;
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.WAIT_FREE_SLOTS;
   }

   public boolean matches(Minecraft mc) {
      if (mc != null && mc.player != null) {
         int free = 0;
         int filled = 0;
         int size = Math.min(36, mc.player.getInventory().getContainerSize());

         for (int i = 0; i < size; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
               free++;
            } else {
               filled++;
            }
         }

         int actual = this.countMode == WaitFreeSlotsAction.CountMode.FILLED_SLOTS ? filled : free;
         return this.compare(actual, Math.max(0, Math.min(36, this.slots)));
      } else {
         return false;
      }
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
      tag.putString("countMode", this.countMode.name());
      tag.putString("comparison", this.comparison.name());
      tag.putInt("slots", Math.max(0, Math.min(36, this.slots)));
      tag.putInt("timeoutMs", Math.max(0, this.timeoutMs));
      tag.putBoolean("enabled", this.enabled);
      MacroWaitOptions.write(tag, this);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.countMode = MacroStringList.enumValue(
         WaitFreeSlotsAction.CountMode.class,
         tag.getStringOr("countMode", WaitFreeSlotsAction.CountMode.FREE_SLOTS.name()),
         WaitFreeSlotsAction.CountMode.FREE_SLOTS
      );
      this.comparison = MacroStringList.enumValue(
         WaitFreeSlotsAction.Comparison.class,
         tag.getStringOr("comparison", WaitFreeSlotsAction.Comparison.AT_MOST.name()),
         WaitFreeSlotsAction.Comparison.AT_MOST
      );
      this.slots = Math.max(0, Math.min(36, tag.getIntOr("slots", 0)));
      this.timeoutMs = Math.max(0, tag.getIntOr("timeoutMs", 0));
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }

      MacroWaitOptions.read(tag, this);
   }

   @Override
   public String getDisplayName() {
      return "Wait "
         + (this.countMode == WaitFreeSlotsAction.CountMode.FILLED_SLOTS ? "Filled" : "Free")
         + " Slots "
         + this.comparisonLabel()
         + " "
         + Math.max(0, Math.min(36, this.slots));
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
      return "FS";
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

   public static enum CountMode {
      FREE_SLOTS,
      FILLED_SLOTS;
   }
}
