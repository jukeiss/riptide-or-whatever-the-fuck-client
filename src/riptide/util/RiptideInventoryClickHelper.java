package riptide.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import riptide.modules.PackHideState;

public final class RiptideInventoryClickHelper {
   private RiptideInventoryClickHelper() {
   }

   public static boolean click(Minecraft mc, int handlerSlotId, int button, ContainerInput input) {
      if (PackHideState.isHardLocked()) {
         return false;
      } else if (mc == null) {
         return false;
      } else if (mc.isSameThread()) {
         return clickNow(mc, handlerSlotId, button, input);
      } else {
         CountDownLatch latch = new CountDownLatch(1);
         AtomicBoolean clicked = new AtomicBoolean(false);
         mc.execute(() -> {
            try {
               clicked.set(clickNow(mc, handlerSlotId, button, input));
            } finally {
               latch.countDown();
            }
         });

         try {
            if (!latch.await(2L, TimeUnit.SECONDS)) {
               return false;
            }
         } catch (InterruptedException var7) {
            Thread.currentThread().interrupt();
            return false;
         }

         return clicked.get();
      }
   }

   private static boolean clickNow(Minecraft mc, int handlerSlotId, int button, ContainerInput input) {
      if (PackHideState.isHardLocked()) {
         return false;
      } else if (mc != null && mc.player != null && mc.gameMode != null && mc.getConnection() != null && input != null) {
         AbstractContainerMenu handler = mc.player.containerMenu;
         if (handler != null && handlerSlotId >= 0 && handlerSlotId < handler.slots.size()) {
            ItemStack beforeCarried = handler.getCarried().copy();
            ItemStack beforeSlot = ((Slot)handler.slots.get(handlerSlotId)).getItem().copy();

            try {
               mc.gameMode.handleContainerInput(handler.containerId, handlerSlotId, button, input, mc.player);
               RiptideCursorClickHelper.recordAfterContainerClick(mc, handler, handlerSlotId, button, input, beforeCarried, beforeSlot);
               return true;
            } catch (RuntimeException var8) {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }
}
