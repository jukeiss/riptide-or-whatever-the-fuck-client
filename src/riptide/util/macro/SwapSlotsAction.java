package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import riptide.util.RiptideInventoryHelper;

public class SwapSlotsAction implements MacroAction {
   public int fromSlot = -1;
   public int toSlot = -1;
   public String fromItemName = "";
   public String toItemName = "";
   public ItemTarget fromItemTarget = new ItemTarget();
   public ItemTarget toItemTarget = new ItemTarget();
   public boolean fromUseItemName = false;
   public boolean toUseItemName = false;
   public boolean useHandlerSlots = true;
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
      if (mc.player != null) {
         int resolvedFrom = this.fromSlot;
         int resolvedTo = this.toSlot;
         if (this.fromUseItemName) {
            ItemTarget target = this.resolvedFromItemTarget().resolveTemplate(mc);
            if (target == null) {
               return;
            }

            if (!target.hasSlot() && !target.hasIdentity()) {
               return;
            }

            resolvedFrom = RiptideInventoryHelper.findUserVisibleSlot(mc, target);
            if (resolvedFrom == -1) {
               return;
            }
         }

         if (this.toUseItemName) {
            ItemTarget targetx = this.resolvedToItemTarget().resolveTemplate(mc);
            if (targetx == null) {
               return;
            }

            if (!targetx.hasSlot() && !targetx.hasIdentity()) {
               return;
            }

            resolvedTo = RiptideInventoryHelper.findUserVisibleSlot(mc, targetx);
            if (resolvedTo == -1) {
               return;
            }
         }

         int fromHandlerSlot = RiptideInventoryHelper.resolveConfiguredHandlerSlot(mc, resolvedFrom);
         int toHandlerSlot = RiptideInventoryHelper.resolveConfiguredHandlerSlot(mc, resolvedTo);
         if (fromHandlerSlot >= 0 && toHandlerSlot >= 0) {
            RiptideInventoryHelper.swapHandlerSlots(mc, fromHandlerSlot, toHandlerSlot);
         }
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.SWAP_SLOTS;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "SWAP_SLOTS");
      tag.putInt("fromSlot", this.fromSlot);
      tag.putInt("toSlot", this.toSlot);
      ItemTarget fromTarget = this.resolvedFromItemTarget();
      ItemTarget toTarget = this.resolvedToItemTarget();
      if (fromTarget.hasSlot() || fromTarget.hasIdentity()) {
         tag.put("fromItemName", fromTarget.toTag());
      }

      if (toTarget.hasSlot() || toTarget.hasIdentity()) {
         tag.put("toItemName", toTarget.toTag());
      }

      tag.putBoolean("fromUseItemName", this.fromUseItemName);
      tag.putBoolean("toUseItemName", this.toUseItemName);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.fromSlot = tag.getIntOr("fromSlot", -1);
      this.toSlot = tag.getIntOr("toSlot", -1);
      this.fromItemTarget = tag.getCompound("fromItemName")
         .map(ItemTarget::fromTag)
         .orElseGet(() -> ItemTarget.fromLegacyEntry(tag.getStringOr("fromItemName", "")));
      this.toItemTarget = tag.getCompound("toItemName").map(ItemTarget::fromTag).orElseGet(() -> ItemTarget.fromLegacyEntry(tag.getStringOr("toItemName", "")));
      this.fromItemName = this.fromItemTarget.toLegacyEntry();
      this.toItemName = this.toItemTarget.toLegacyEntry();
      this.fromUseItemName = tag.contains("fromUseItemName") ? tag.getBooleanOr("fromUseItemName", false) : !this.fromItemName.isEmpty();
      this.toUseItemName = tag.contains("toUseItemName") ? tag.getBooleanOr("toUseItemName", false) : !this.toItemName.isEmpty();
      this.useHandlerSlots = true;
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public String getDisplayName() {
      String from = this.fromUseItemName ? "\"" + this.resolvedFromItemTarget().summaryText() + "\"" : String.valueOf(this.fromSlot);
      String to = this.toUseItemName ? "\"" + this.resolvedToItemTarget().summaryText() + "\"" : String.valueOf(this.toSlot);
      return "Swap " + from + " <-> " + to;
   }

   @Override
   public String getIcon() {
      return "SWP";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   private ItemTarget resolvedFromItemTarget() {
      if (this.fromItemTarget == null || !this.fromItemTarget.hasSlot() && !this.fromItemTarget.hasIdentity()) {
         this.fromItemTarget = ItemTarget.fromLegacyEntry(this.fromItemName);
         return this.fromItemTarget;
      } else {
         return this.fromItemTarget;
      }
   }

   private ItemTarget resolvedToItemTarget() {
      if (this.toItemTarget == null || !this.toItemTarget.hasSlot() && !this.toItemTarget.hasIdentity()) {
         this.toItemTarget = ItemTarget.fromLegacyEntry(this.toItemName);
         return this.toItemTarget;
      } else {
         return this.toItemTarget;
      }
   }
}
