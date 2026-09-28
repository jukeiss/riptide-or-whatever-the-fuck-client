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
import riptide.util.macro.ItemTarget;

public final class RiptideCursorClickHelper {
   private static int originContainerId = -1;
   private static int originSlotId = -1;
   private static ItemStack originStack = ItemStack.EMPTY;

   private RiptideCursorClickHelper() {
   }

   public static boolean click(Minecraft mc) {
      return click(mc, null, 1);
   }

   public static boolean click(Minecraft mc, ItemTarget carriedGuard) {
      return click(mc, carriedGuard, 1);
   }

   public static boolean click(Minecraft mc, ItemTarget carriedGuard, int times) {
      if (PackHideState.isHardLocked()) {
         return false;
      } else if (mc == null) {
         return false;
      } else {
         int clickCount = Math.max(1, times);
         if (mc.isSameThread()) {
            return clickNow(mc, carriedGuard, clickCount);
         } else {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean clicked = new AtomicBoolean(false);
            mc.execute(() -> {
               try {
                  clicked.set(clickNow(mc, carriedGuard, clickCount));
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
   }

   private static boolean clickNow(Minecraft mc, ItemTarget carriedGuard, int times) {
      if (PackHideState.isHardLocked()) {
         return false;
      } else if (mc != null && mc.player != null && mc.gameMode != null && mc.getConnection() != null) {
         AbstractContainerMenu handler = mc.player.containerMenu;
         if (handler == null) {
            return false;
         } else {
            boolean clickedAny = false;

            for (int i = 0; i < times; i++) {
               ItemStack carried = handler.getCarried();
               if (carried == null || carried.isEmpty() || !matchesCarriedGuard(carriedGuard, carried)) {
                  break;
               }

               int triggerSlot = findOriginTriggerSlot(mc, handler, carried);
               if (triggerSlot < 0) {
                  break;
               }

               try {
                  mc.gameMode.handleContainerInput(handler.containerId, triggerSlot, 0, ContainerInput.PICKUP_ALL, mc.player);
                  refreshOriginStack(handler);
                  clickedAny = true;
               } catch (RuntimeException var9) {
                  break;
               }
            }

            return clickedAny;
         }
      } else {
         return false;
      }
   }

   public static void recordAfterContainerClick(
      Minecraft mc, AbstractContainerMenu handler, int slotId, int button, ContainerInput input, ItemStack beforeCarried, ItemStack beforeSlot
   ) {
      if (mc != null && mc.player != null && handler != null && input != null) {
         ItemStack afterCarried = handler.getCarried();
         if (afterCarried != null && !afterCarried.isEmpty()) {
            if (input == ContainerInput.PICKUP
               && slotId >= 0
               && slotId < handler.slots.size()
               && (button == 0 || button == 1)
               && (beforeCarried == null || beforeCarried.isEmpty())
               && beforeSlot != null
               && !beforeSlot.isEmpty()
               && ItemStack.isSameItemSameComponents(beforeSlot, afterCarried)) {
               originContainerId = handler.containerId;
               originSlotId = slotId;
               originStack = afterCarried.copy();
            } else {
               if (originContainerId == handler.containerId && originSlotId >= 0 && ItemStack.isSameItemSameComponents(originStack, afterCarried)) {
                  originStack = afterCarried.copy();
               } else {
                  clearOrigin();
               }
            }
         } else {
            clearOrigin();
         }
      }
   }

   private static int findOriginTriggerSlot(Minecraft mc, AbstractContainerMenu handler, ItemStack carried) {
      if (originContainerId != handler.containerId || originSlotId < 0 || originSlotId >= handler.slots.size()) {
         return -1;
      } else if (!originStack.isEmpty() && ItemStack.isSameItemSameComponents(originStack, carried)) {
         Slot slot = (Slot)handler.slots.get(originSlotId);
         return isSafePickupAllTrigger(mc, slot, carried) ? originSlotId : -1;
      } else {
         return -1;
      }
   }

   private static boolean isSafePickupAllTrigger(Minecraft mc, Slot slot, ItemStack carried) {
      if (mc == null || mc.player == null || slot == null || !slot.isActive()) {
         return false;
      } else {
         return !slot.hasItem() ? true : !slot.mayPickup(mc.player) && !ItemStack.isSameItemSameComponents(slot.getItem(), carried);
      }
   }

   private static void refreshOriginStack(AbstractContainerMenu handler) {
      if (handler != null && originContainerId == handler.containerId) {
         ItemStack carried = handler.getCarried();
         if (carried != null && !carried.isEmpty()) {
            originStack = carried.copy();
         } else {
            clearOrigin();
         }
      }
   }

   private static void clearOrigin() {
      originContainerId = -1;
      originSlotId = -1;
      originStack = ItemStack.EMPTY;
   }

   private static boolean matchesCarriedGuard(ItemTarget target, ItemStack carried) {
      if (target != null && target.hasIdentity()) {
         ItemTarget identityOnly = target.copy();
         identityOnly.slot = -1;
         return identityOnly.matches(carried, -1);
      } else {
         return true;
      }
   }
}
