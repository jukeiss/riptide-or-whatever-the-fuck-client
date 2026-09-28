package riptide.util.macro;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import riptide.modules.PackHideState;

public class BundleDupeV2Action implements MacroAction {
   public int delayAfterPickingUpMs = 30;
   public int delayAfterPuttingBackMs = 20;
   public int bundlePacketCount = 20;
   public int dropDelayMs = 50;
   public int hotbarSlot = 0;
   public int bundleIndex = -1;
   public int maxCycles = 0;
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
      if (!PackHideState.isHardLocked()) {
         if (this.isReady(mc)) {
            int cycles = 0;

            while (MacroExecutor.isCurrentActionRunActive() && !Thread.currentThread().isInterrupted()) {
               if (PackHideState.isHardLocked()) {
                  return;
               }

               if (!this.hasConnection(mc)) {
                  return;
               }

               int handlerSlot = this.handlerSlot();
               this.sendClick(mc, handlerSlot, 1);
               this.sendBundleSelectBurst(mc, handlerSlot);
               if (!this.sleep(this.delayAfterPickingUpMs)) {
                  return;
               }

               this.sendClick(mc, handlerSlot, 0);
               if (!this.sleep(this.delayAfterPuttingBackMs)) {
                  return;
               }

               ItemStack firstStack = this.slotStack(mc);
               if (!firstStack.isEmpty() && !firstStack.is(Items.BUNDLE)) {
                  this.sendClick(mc, handlerSlot, 1);
                  if (!this.sleep(this.dropDelayMs)) {
                     return;
                  }

                  this.sendClick(mc, -999, 0);
                  if (!this.sleep(this.dropDelayMs)) {
                     return;
                  }

                  if (this.maxCycles > 0 && ++cycles >= this.maxCycles) {
                     return;
                  }
               } else if (this.maxCycles > 0 && ++cycles >= this.maxCycles) {
                  return;
               }
            }
         }
      }
   }

   private boolean isReady(Minecraft mc) {
      if (this.hasConnection(mc) && mc.player.containerMenu instanceof InventoryMenu) {
         ItemStack stack = this.slotStack(mc);
         if (!stack.isEmpty() && stack.is(Items.BUNDLE)) {
            BundleContents contents = (BundleContents)stack.get(DataComponents.BUNDLE_CONTENTS);
            return contents != null && contents.size() == 1;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean hasConnection(Minecraft mc) {
      return mc != null && mc.player != null && mc.getConnection() != null;
   }

   private ItemStack slotStack(Minecraft mc) {
      if (mc != null && mc.player != null) {
         int slot = Math.max(0, Math.min(8, this.hotbarSlot));
         return mc.player.getInventory().getItem(slot);
      } else {
         return ItemStack.EMPTY;
      }
   }

   private int handlerSlot() {
      return 36 + Math.max(0, Math.min(8, this.hotbarSlot));
   }

   private void sendBundleSelectBurst(Minecraft mc, int handlerSlot) {
      int count = Math.max(1, Math.min(10000, this.bundlePacketCount));

      for (int i = 0; i < count && MacroExecutor.isCurrentActionRunActive(); i++) {
         mc.getConnection().send(new ServerboundSelectBundleItemPacket(handlerSlot, this.bundleIndex));
      }
   }

   private void sendClick(Minecraft mc, int slot, int button) {
      if (this.hasConnection(mc) && mc.player.containerMenu != null) {
         mc.getConnection()
            .send(
               new ServerboundContainerClickPacket(
                  mc.player.containerMenu.containerId,
                  mc.player.containerMenu.getStateId(),
                  (short)slot,
                  (byte)button,
                  ContainerInput.PICKUP,
                  new Int2ObjectOpenHashMap(),
                  HashedStack.EMPTY
               )
            );
      }
   }

   private boolean sleep(int ms) {
      int remaining = Math.max(0, ms);
      long deadline = System.currentTimeMillis() + remaining;

      while (MacroExecutor.isCurrentActionRunActive() && !Thread.currentThread().isInterrupted()) {
         long left = deadline - System.currentTimeMillis();
         if (left <= 0L) {
            return true;
         }

         try {
            Thread.sleep(Math.min(10L, left));
         } catch (InterruptedException var8) {
            Thread.currentThread().interrupt();
            return false;
         }
      }

      return false;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putInt("delayAfterPickingUpMs", Math.max(0, this.delayAfterPickingUpMs));
      tag.putInt("delayAfterPuttingBackMs", Math.max(0, this.delayAfterPuttingBackMs));
      tag.putInt("bundlePacketCount", Math.max(1, this.bundlePacketCount));
      tag.putInt("dropDelayMs", Math.max(0, this.dropDelayMs));
      tag.putInt("hotbarSlot", Math.max(0, Math.min(8, this.hotbarSlot)));
      tag.putInt("bundleIndex", this.bundleIndex);
      tag.putInt("maxCycles", Math.max(0, this.maxCycles));
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.delayAfterPickingUpMs = Math.max(0, tag.getIntOr("delayAfterPickingUpMs", 30));
      this.delayAfterPuttingBackMs = Math.max(0, tag.getIntOr("delayAfterPuttingBackMs", 20));
      this.bundlePacketCount = Math.max(1, tag.getIntOr("bundlePacketCount", 20));
      this.dropDelayMs = Math.max(0, tag.getIntOr("dropDelayMs", 50));
      this.hotbarSlot = Math.max(0, Math.min(8, tag.getIntOr("hotbarSlot", 0)));
      this.bundleIndex = tag.getIntOr("bundleIndex", -1);
      this.maxCycles = Math.max(0, tag.getIntOr("maxCycles", 0));
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.BUNDLE_DUPE_V2;
   }

   @Override
   public String getDisplayName() {
      return "Bundle Dupe V2" + (this.maxCycles > 0 ? " x" + this.maxCycles : " loop");
   }

   @Override
   public String getIcon() {
      return "BD2";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }
}
