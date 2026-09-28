package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class TickSyncAction implements MacroAction {
   public int tickOffset = 1;
   public int preGenCount = -1;

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putInt("tickOffset", this.tickOffset);
      tag.putInt("preGenCount", this.preGenCount);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.tickOffset = tag.getIntOr("tickOffset", 1);
      this.preGenCount = tag.getIntOr("preGenCount", -1);
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.TICK_SYNC;
   }

   @Override
   public String getDisplayName() {
      StringBuilder sb = new StringBuilder("Tick Sync");
      if (this.tickOffset > 0) {
         sb.append(" +").append(this.tickOffset);
      }

      if (this.preGenCount > 0) {
         sb.append(" (").append(this.preGenCount).append(" pkts)");
      } else if (this.preGenCount == -1) {
         sb.append(" (all)");
      }

      return sb.toString();
   }

   @Override
   public String getIcon() {
      return "Tick";
   }
}
