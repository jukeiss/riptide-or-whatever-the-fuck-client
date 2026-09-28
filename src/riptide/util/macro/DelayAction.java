package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class DelayAction implements MacroAction {
   public int delayMs = 1000;
   public boolean useTicks = false;
   public int delayTicks = 20;

   public DelayAction() {
   }

   public DelayAction(int delayMs) {
      this.delayMs = delayMs;
   }

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putInt("delayMs", this.delayMs);
      tag.putBoolean("useTicks", this.useTicks);
      tag.putInt("delayTicks", this.delayTicks);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("delayMs")) {
         this.delayMs = tag.getIntOr("delayMs", 0);
      } else if (tag.contains("delayTicks") && !tag.contains("useTicks")) {
         this.delayMs = tag.getIntOr("delayTicks", 0) * 50;
      }

      if (tag.contains("useTicks")) {
         this.useTicks = tag.getBooleanOr("useTicks", false);
      }

      if (tag.contains("delayTicks")) {
         this.delayTicks = tag.getIntOr("delayTicks", 20);
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.DELAY;
   }

   @Override
   public String getDisplayName() {
      return this.useTicks ? "Delay: " + this.delayTicks + " ticks" : "Delay: " + this.delayMs + " ms";
   }

   @Override
   public String getIcon() {
      return "Wait";
   }
}
