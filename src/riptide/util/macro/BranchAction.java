package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class BranchAction implements MacroAction {
   public BranchAction.ConditionKind conditionKind = BranchAction.ConditionKind.ALWAYS;
   public String value = "";
   public int thenSteps = 1;
   public int elseSteps = 0;

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.BRANCH;
   }

   public boolean matches(Minecraft mc) {
      if (mc == null) {
         return false;
      } else {
         MacroTemplate.Resolution resolution = MacroVariables.resolve(this.value, mc);
         if (!resolution.success()) {
            return false;
         } else {
            String resolved = resolution.value();

            return switch (this.conditionKind) {
               case ALWAYS -> true;
               case GUI_TYPE -> MacroGuiMatcher.matches(mc.gui.screen(), resolved);
               case INVENTORY_ITEM -> WaitInventoryPredicateAction.hasInventoryItem(mc, ItemTarget.fromLegacyEntry(resolved));
               case ENTITY_TARGET -> mc.crosshairPickEntity != null;
               case HELD_ITEM -> mc.player != null
                  && ItemTarget.fromLegacyEntry(resolved).score(mc.player.getMainHandItem(), mc.player.getInventory().getSelectedSlot()) >= 0;
            };
         }
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "BRANCH");
      tag.putString("conditionKind", this.conditionKind.name());
      tag.putString("value", this.value);
      tag.putInt("thenSteps", this.thenSteps);
      tag.putInt("elseSteps", this.elseSteps);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.conditionKind = MacroStringList.enumValue(
         BranchAction.ConditionKind.class, tag.getStringOr("conditionKind", "ALWAYS"), BranchAction.ConditionKind.ALWAYS
      );
      this.value = tag.getStringOr("value", "");
      this.thenSteps = tag.getIntOr("thenSteps", 1);
      this.elseSteps = tag.getIntOr("elseSteps", 0);
   }

   @Override
   public String getDisplayName() {
      return "If " + this.conditionKind + " then " + this.thenSteps + " else " + this.elseSteps;
   }

   @Override
   public String getIcon() {
      return "?";
   }

   public static enum ConditionKind {
      ALWAYS,
      GUI_TYPE,
      INVENTORY_ITEM,
      ENTITY_TARGET,
      HELD_ITEM;
   }
}
