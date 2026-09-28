package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import riptide.util.RiptideNotifications;
import riptide.util.RiptideSharedState;

public class RestoreGuiAction implements MacroAction, WaitsForGui {
   public boolean waitForGuiBefore = false;
   public boolean waitForGuiAfter = true;
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
      RiptideSharedState shared = RiptideSharedState.get();
      if (shared.getStoredScreen() != null && shared.getStoredAbstractContainerMenu() != null) {
         mc.gui.setScreen(shared.getStoredScreen());
         if (mc.player != null) {
            mc.player.containerMenu = shared.getStoredAbstractContainerMenu();
         }

         RiptideNotifications.show("GUI restored.", -13248397);
      } else {
         RiptideNotifications.error("No stored GUI.");
      }
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
      return "";
   }

   @Override
   public void setWaitGuiName(String n) {
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.RESTORE_GUI;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "RESTORE_GUI");
      tag.putBoolean("waitForGuiBefore", this.waitForGuiBefore);
      tag.putBoolean("waitForGuiAfter", this.waitForGuiAfter);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
      this.waitForGuiAfter = WaitsForGui.loadAfter(tag, true);
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public String getDisplayName() {
      return "Restore GUI" + WaitsForGui.timingLabel(this);
   }

   @Override
   public String getIcon() {
      return "R";
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
