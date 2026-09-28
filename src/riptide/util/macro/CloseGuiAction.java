package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import riptide.util.RiptideGuiActions;
import riptide.util.RiptideInventoryHelper;

public class CloseGuiAction implements MacroAction {
   public String guiName = "";
   public String itemName = "";
   public ItemTarget itemTarget = new ItemTarget();
   public int targetSlot = -1;
   public boolean useItemFilter = false;
   public boolean sendPacket = true;
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
      if (mc.player != null && mc.gui.screen() != null) {
         boolean matchGui = true;
         if (!this.guiName.isEmpty()) {
            MacroTemplate.Resolution gui = MacroVariables.resolve(this.guiName, mc);
            if (!gui.success()) {
               return;
            }

            matchGui = mc.gui.screen().getTitle().getString().toLowerCase().contains(gui.value().toLowerCase());
         }

         boolean matchItem = true;
         ItemTarget target = this.resolvedItemTarget().resolveTemplate(mc);
         if (!this.useItemFilter || target != null) {
            if (this.useItemFilter && (target.hasSlot() || target.hasIdentity())) {
               matchItem = false;
               if (mc.player.containerMenu != null) {
                  int effectiveSlot = target.hasSlot() ? target.slot : this.targetSlot;
                  int resolvedTargetSlot = RiptideInventoryHelper.resolveConfiguredHandlerSlot(mc, effectiveSlot);
                  if (resolvedTargetSlot >= 0 && resolvedTargetSlot < mc.player.containerMenu.slots.size()) {
                     ItemStack stack = ((Slot)mc.player.containerMenu.slots.get(resolvedTargetSlot)).getItem();
                     int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(mc, resolvedTargetSlot);
                     if (!stack.isEmpty() && target.matches(stack, visibleSlot)) {
                        matchItem = true;
                     }
                  } else if (effectiveSlot == -1) {
                     for (Slot slot : mc.player.containerMenu.slots) {
                        ItemStack stack = slot.getItem();
                        int visibleSlot = RiptideInventoryHelper.toUserVisibleSlot(mc, slot.index);
                        if (!stack.isEmpty() && target.matches(stack, visibleSlot)) {
                           matchItem = true;
                           break;
                        }
                     }
                  }
               }
            }

            if (matchGui && matchItem) {
               RiptideGuiActions.closeCurrentScreen(mc, this.sendPacket, false);
            }
         }
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.CLOSE_GUI;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "CLOSE_GUI");
      tag.putString("guiName", this.guiName);
      ItemTarget target = this.resolvedItemTarget();
      if (target.hasSlot() || target.hasIdentity()) {
         tag.put("itemName", target.toTag());
      }

      tag.putBoolean("useItemFilter", this.useItemFilter);
      tag.putBoolean("sendPacket", this.sendPacket);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("guiName")) {
         this.guiName = tag.getStringOr("guiName", "");
      }

      this.itemTarget = tag.getCompound("itemName").map(ItemTarget::fromTag).orElseGet(() -> {
         int legacySlot = tag.contains("targetSlot") ? tag.getIntOr("targetSlot", -1) : -1;
         String legacyName = tag.getStringOr("itemName", "");
         if (legacySlot >= 0 && !legacyName.isBlank()) {
            return ItemTarget.fromLegacyEntry("#" + legacySlot + "|" + legacyName);
         } else {
            return legacySlot >= 0 ? ItemTarget.slotOnly(legacySlot) : ItemTarget.fromLegacyEntry(legacyName);
         }
      });
      this.itemName = this.itemTarget.toLegacyEntry();
      this.targetSlot = this.itemTarget.hasSlot() ? this.itemTarget.slot : -1;
      if (tag.contains("useItemFilter")) {
         this.useItemFilter = tag.getBooleanOr("useItemFilter", false);
      } else {
         this.useItemFilter = this.itemTarget.hasSlot() || this.itemTarget.hasIdentity();
      }

      if (tag.contains("sendPacket")) {
         this.sendPacket = tag.getBooleanOr("sendPacket", true);
      }

      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public String getDisplayName() {
      String base = !this.guiName.isEmpty() ? "Close GUI: " + this.guiName : "Close GUI";
      return this.sendPacket ? base : base + " (desync)";
   }

   @Override
   public String getIcon() {
      return "X";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   private ItemTarget resolvedItemTarget() {
      if (this.itemTarget == null || !this.itemTarget.hasSlot() && !this.itemTarget.hasIdentity()) {
         if (this.targetSlot >= 0 && !this.itemName.isBlank()) {
            this.itemTarget = ItemTarget.fromLegacyEntry("#" + this.targetSlot + "|" + this.itemName);
         } else if (this.targetSlot >= 0) {
            this.itemTarget = ItemTarget.slotOnly(this.targetSlot);
         } else {
            this.itemTarget = ItemTarget.fromLegacyEntry(this.itemName);
         }

         return this.itemTarget;
      } else {
         return this.itemTarget;
      }
   }
}
