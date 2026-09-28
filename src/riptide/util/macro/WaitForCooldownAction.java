package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class WaitForCooldownAction implements MacroAction {
   public String itemName = "";
   public ItemTarget itemTarget = new ItemTarget();
   public boolean checkMainInteractionHand = true;
   public boolean listenDuringPreviousAction = false;

   public WaitForCooldownAction() {
   }

   public WaitForCooldownAction(String itemName, boolean checkMainHand) {
      this.itemName = itemName;
      this.itemTarget = ItemTarget.fromLegacyEntry(itemName);
      this.checkMainInteractionHand = checkMainHand;
   }

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      ItemTarget target = this.resolvedItemTarget();
      if (target.hasSlot() || target.hasIdentity()) {
         tag.put("itemName", target.toTag());
      }

      tag.putBoolean("checkMainHand", this.checkMainInteractionHand);
      MacroWaitOptions.write(tag, this);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.itemTarget = tag.getCompound("itemName").map(ItemTarget::fromTag).orElseGet(() -> ItemTarget.fromLegacyEntry(tag.getStringOr("itemName", "")));
      this.itemName = this.itemTarget.toLegacyEntry();
      if (tag.contains("checkMainHand")) {
         this.checkMainInteractionHand = tag.getBooleanOr("checkMainHand", true);
      }

      MacroWaitOptions.read(tag, this);
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.WAIT_COOLDOWN;
   }

   @Override
   public String getDisplayName() {
      ItemTarget target = this.resolvedItemTarget();
      String base = !target.hasSlot() && !target.hasIdentity() ? (this.checkMainInteractionHand ? "Main Hand" : "Off Hand") : target.summaryText();
      if (base.length() > 10) {
         base = base.substring(0, 8) + "..";
      }

      return "Wait Cooldown (" + base + ")";
   }

   @Override
   public String getIcon() {
      return "CD";
   }

   private ItemTarget resolvedItemTarget() {
      if (this.itemTarget == null || !this.itemTarget.hasSlot() && !this.itemTarget.hasIdentity()) {
         this.itemTarget = ItemTarget.fromLegacyEntry(this.itemName);
         return this.itemTarget;
      } else {
         return this.itemTarget;
      }
   }
}
