package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import riptide.util.RiptideGuiActions;

public class InventoryAction implements MacroAction, WaitsForGui {
   public InventoryAction.InvMode mode = InventoryAction.InvMode.OPEN;
   public boolean waitForGuiBefore = false;
   public boolean waitForGuiAfter = true;
   public String guiName = "";
   public boolean sendPacket = true;

   @Override
   public void execute(Minecraft mc) {
      if (mc.player != null) {
         if (this.mode == InventoryAction.InvMode.OPEN) {
            mc.execute(() -> mc.gui.setScreen(new InventoryScreen(mc.player)));
         } else {
            mc.execute(() -> RiptideGuiActions.closeCurrentScreen(mc, this.sendPacket, false));
         }
      }
   }

   @Override
   public boolean isWaitForGuiBefore() {
      return this.mode == InventoryAction.InvMode.OPEN && this.waitForGuiBefore;
   }

   @Override
   public void setWaitForGuiBefore(boolean v) {
      this.waitForGuiBefore = v;
   }

   @Override
   public boolean isWaitForGuiAfter() {
      return this.mode == InventoryAction.InvMode.OPEN && this.waitForGuiAfter;
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
   public void setWaitGuiName(String name) {
      this.guiName = "";
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.INVENTORY;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "INVENTORY");
      tag.putString("mode", this.mode.name());
      tag.putBoolean("waitForGuiBefore", this.waitForGuiBefore);
      tag.putBoolean("waitForGuiAfter", this.waitForGuiAfter);
      tag.putString("guiName", this.guiName);
      tag.putBoolean("sendPacket", this.sendPacket);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("mode")) {
         try {
            this.mode = InventoryAction.InvMode.valueOf(tag.getStringOr("mode", "OPEN"));
         } catch (IllegalArgumentException var3) {
            this.mode = InventoryAction.InvMode.OPEN;
         }
      }

      this.waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
      this.waitForGuiAfter = WaitsForGui.loadAfter(tag, true);
      if (tag.contains("guiName")) {
         this.guiName = tag.getStringOr("guiName", "");
      }

      if (tag.contains("sendPacket")) {
         this.sendPacket = tag.getBooleanOr("sendPacket", true);
      }
   }

   @Override
   public String getDisplayName() {
      if (this.mode == InventoryAction.InvMode.OPEN) {
         return "Inv Open" + WaitsForGui.timingLabel(this);
      } else {
         return this.sendPacket ? "Inv Close" : "Inv Close (no pkt)";
      }
   }

   @Override
   public String getIcon() {
      return "I";
   }

   public static enum InvMode {
      OPEN,
      CLOSE;
   }
}
