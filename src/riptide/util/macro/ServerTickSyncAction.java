package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class ServerTickSyncAction implements MacroAction {
   public int bufferMs = 5;
   public int maxWaitMs = 3000;
   public boolean ignorePing = false;
   public int preGenCount = -1;

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.SERVER_TICK_SYNC;
   }

   @Override
   public String getIcon() {
      return "Srv";
   }

   @Override
   public String getDisplayName() {
      StringBuilder sb = new StringBuilder("ServerSync");
      sb.append(" buf:").append(this.bufferMs).append("ms");
      if (this.ignorePing) {
         sb.append(" (no ping)");
      }

      if (this.preGenCount > 0) {
         sb.append(" (").append(this.preGenCount).append(" pkts)");
      } else if (this.preGenCount == -1) {
         sb.append(" (all)");
      }

      return sb.toString();
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putInt("bufferMs", this.bufferMs);
      tag.putInt("maxWaitMs", this.maxWaitMs);
      tag.putBoolean("ignorePing", this.ignorePing);
      tag.putInt("preGenCount", this.preGenCount);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.bufferMs = tag.getIntOr("bufferMs", 5);
      this.maxWaitMs = tag.getIntOr("maxWaitMs", 3000);
      this.ignorePing = tag.getBooleanOr("ignorePing", false);
      this.preGenCount = tag.getIntOr("preGenCount", -1);
   }
}
