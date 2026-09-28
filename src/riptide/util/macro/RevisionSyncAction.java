package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class RevisionSyncAction implements MacroAction {
   public int revisionOffset = 1;
   public int preGenCount = -1;

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putInt("revisionOffset", this.revisionOffset);
      tag.putInt("preGenCount", this.preGenCount);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.revisionOffset = tag.getIntOr("revisionOffset", 1);
      this.preGenCount = tag.getIntOr("preGenCount", -1);
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.REVISION_SYNC;
   }

   @Override
   public String getDisplayName() {
      StringBuilder sb = new StringBuilder("Rev Sync");
      sb.append(" +").append(this.revisionOffset);
      if (this.preGenCount > 0) {
         sb.append(" (").append(this.preGenCount).append(" pkts)");
      } else if (this.preGenCount == -1) {
         sb.append(" (all)");
      }

      return sb.toString();
   }

   @Override
   public String getIcon() {
      return "Rev";
   }
}
