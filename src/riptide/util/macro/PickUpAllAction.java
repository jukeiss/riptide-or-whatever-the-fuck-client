package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import riptide.util.RiptideCursorClickHelper;

public class PickUpAllAction implements MacroAction {
   public int times = 1;

   @Override
   public void execute(Minecraft mc) {
      RiptideCursorClickHelper.click(mc, null, this.times);
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "PICK_UP_ALL");
      tag.putInt("times", Math.max(1, this.times));
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.times = Math.max(1, tag.getIntOr("times", 1));
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.PICK_UP_ALL;
   }

   @Override
   public String getDisplayName() {
      return "Pick Up All" + (this.times > 1 ? " x" + this.times : "");
   }

   @Override
   public String getIcon() {
      return "All";
   }
}
