package riptide.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import riptide.modules.PackHideState;

public final class RiptideDropHelper {
   private RiptideDropHelper() {
   }

   public static int dropFromHandlerSlot(Minecraft mc, int handlerSlotId, int count) {
      if (PackHideState.isHardLocked()) {
         return 0;
      } else if (mc != null && mc.player != null && mc.gameMode != null) {
         AbstractContainerMenu handler = mc.player.containerMenu;
         if (handler != null && handlerSlotId >= 0 && handlerSlotId < handler.slots.size()) {
            ItemStack stack = ((Slot)handler.slots.get(handlerSlotId)).getItem();
            if (stack.isEmpty()) {
               return 0;
            } else {
               int syncId = handler.containerId;
               if (count > 0 && count < stack.getCount()) {
                  int sent = 0;
                  int drops = Math.min(count, stack.getCount());

                  for (int i = 0; i < drops; i++) {
                     mc.gameMode.handleContainerInput(syncId, handlerSlotId, 0, ContainerInput.THROW, mc.player);
                     sent++;
                  }

                  return sent;
               } else {
                  mc.gameMode.handleContainerInput(syncId, handlerSlotId, 1, ContainerInput.THROW, mc.player);
                  return 1;
               }
            }
         } else {
            return 0;
         }
      } else {
         return 0;
      }
   }

   public static int dropFromInventorySlot(Minecraft mc, int inventorySlotId, int count) {
      if (PackHideState.isHardLocked()) {
         return 0;
      } else if (mc == null || mc.player == null) {
         return 0;
      } else if (inventorySlotId >= 0 && inventorySlotId < 36) {
         int handlerSlotId = RiptideInventoryHelper.toHandlerSlot(mc, inventorySlotId);
         return dropFromHandlerSlot(mc, handlerSlotId, count);
      } else {
         return 0;
      }
   }
}
