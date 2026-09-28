package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import riptide.modules.PackHideState;

public class SendCommandPacketAction implements MacroAction {
   public String command = "";
   public boolean stripLeadingSlash = true;

   @Override
   public void execute(Minecraft mc) {
      if (!PackHideState.isHardLocked()) {
         if (mc != null && mc.getConnection() != null) {
            MacroTemplate.Resolution resolution = MacroVariables.resolve(this.command, mc);
            if (resolution.success()) {
               String normalized = resolution.value().trim();
               if (this.stripLeadingSlash && normalized.startsWith("/")) {
                  normalized = normalized.substring(1);
               }

               if (!normalized.isBlank()) {
                  mc.getConnection().sendCommand(normalized);
               }
            }
         }
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.SEND_COMMAND_PACKET;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "SEND_COMMAND_PACKET");
      tag.putString("command", this.command);
      tag.putBoolean("stripLeadingSlash", this.stripLeadingSlash);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.command = tag.getStringOr("command", "");
      this.stripLeadingSlash = tag.getBooleanOr("stripLeadingSlash", true);
   }

   @Override
   public String getDisplayName() {
      return "Command packet " + this.command;
   }

   @Override
   public String getIcon() {
      return "/";
   }
}
