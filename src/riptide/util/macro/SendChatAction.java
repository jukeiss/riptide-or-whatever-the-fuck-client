package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import riptide.modules.PackHideState;

public class SendChatAction implements MacroAction, WaitsForGui {
   public String message = "";
   public boolean waitForGuiBefore = false;
   public boolean waitForGuiAfter = true;
   public String guiName = "";
   private boolean enabled = true;

   public SendChatAction() {
   }

   public SendChatAction(String message) {
      this.message = message;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", MacroActionType.SEND_CHAT.name());
      tag.putString("message", this.message);
      tag.putBoolean("waitForGuiBefore", this.waitForGuiBefore);
      tag.putBoolean("waitForGuiAfter", this.waitForGuiAfter);
      tag.putString("guiName", this.guiName);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("message")) {
         this.message = tag.getStringOr("message", "");
      }

      this.waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
      this.waitForGuiAfter = WaitsForGui.loadAfter(tag, true);
      if (tag.contains("guiName")) {
         this.guiName = tag.getStringOr("guiName", "");
      }

      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public void execute(Minecraft mc) {
      if (!PackHideState.isHardLocked()) {
         if (this.message != null && !this.message.isEmpty()) {
            if (mc.getConnection() != null) {
               MacroTemplate.Resolution resolution = MacroVariables.resolve(this.message, mc);
               if (MacroVariables.resolved("Send chat", resolution)) {
                  String resolved = resolution.value();
                  if (resolved.startsWith("/") && resolved.length() > 1) {
                     mc.getConnection().sendCommand(resolved.substring(1));
                  } else {
                     mc.getConnection().sendChat(resolved);
                  }
               }
            }
         }
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.SEND_CHAT;
   }

   @Override
   public boolean isWaitForGuiBefore() {
      return this.waitForGuiBefore;
   }

   @Override
   public void setWaitForGuiBefore(boolean v) {
      this.waitForGuiBefore = v;
   }

   @Override
   public boolean isWaitForGuiAfter() {
      return this.waitForGuiAfter;
   }

   @Override
   public void setWaitForGuiAfter(boolean v) {
      this.waitForGuiAfter = v;
   }

   @Override
   public String getWaitGuiName() {
      return this.guiName;
   }

   @Override
   public void setWaitGuiName(String name) {
      this.guiName = name != null ? name : "";
   }

   @Override
   public String getDisplayName() {
      String preview = this.message.length() > 20 ? this.message.substring(0, 20) + "..." : this.message;
      return "Chat: " + preview + WaitsForGui.timingLabel(this);
   }

   @Override
   public String getIcon() {
      return "CH";
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
