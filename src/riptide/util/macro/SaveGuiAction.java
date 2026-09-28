package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import riptide.util.RiptideGuiActions;

public class SaveGuiAction implements MacroAction {
   private boolean enabled = true;
   public boolean closeAfter = false;
   public boolean sendPacket = true;

   @Override
   public void execute(Minecraft mc) {
      if (RiptideGuiActions.saveCurrentGui(mc, true)) {
         if (this.closeAfter) {
            RiptideGuiActions.closeCurrentScreen(mc, this.sendPacket, false);
         }
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.SAVE_GUI;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "SAVE_GUI");
      tag.putBoolean("enabled", this.enabled);
      tag.putBoolean("closeAfter", this.closeAfter);
      tag.putBoolean("sendPacket", this.sendPacket);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }

      if (tag.contains("closeAfter")) {
         this.closeAfter = tag.getBooleanOr("closeAfter", false);
      }

      if (tag.contains("sendPacket")) {
         this.sendPacket = tag.getBooleanOr("sendPacket", true);
      }
   }

   @Override
   public String getDisplayName() {
      if (!this.closeAfter) {
         return "Save GUI";
      } else {
         return this.sendPacket ? "Save GUI (close)" : "Save GUI (close, desync)";
      }
   }

   @Override
   public String getIcon() {
      return "S";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }
}
