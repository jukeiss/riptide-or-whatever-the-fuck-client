package riptide.util.macro;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import riptide.modules.PackHideState;
import riptide.util.RiptideClientMessaging;

public final class RollbackAction implements MacroAction {
   private static final int TASK_TIMEOUT_SECONDS = 60;
   private boolean enabled = true;
   public RollbackAction.Scope scope = RollbackAction.Scope.ALL_CONTAINER;
   public ArrayList<String> slots = new ArrayList<>();
   public int startSlot = 0;
   public int endSlot = 26;

   @Override
   public void execute(Minecraft mc) {
      if (mc != null && !mc.isSameThread()) {
         try {
            this.executeRollback(mc);
         } catch (InterruptedException var3) {
            RiptideClientMessaging.sendPrefixed("§eRollback canceled safely.");
         } catch (Throwable var4) {
            RiptideClientMessaging.sendPrefixed("§cRollback failed: " + conciseMessage(var4));
         }
      } else {
         RiptideClientMessaging.sendPrefixed("§cRollback must run from the macro executor.");
      }
   }

   public void executeRollback(Minecraft mc) throws Exception {
      if (mc == null || mc.isSameThread()) {
         throw new IllegalStateException("rollback cannot execute on the render thread");
      } else if (!PackHideState.isHardLocked()) {
         IntegratedServer server = mc.getSingleplayerServer();
         if (server == null) {
            RiptideClientMessaging.sendPrefixed("§cRollback requires a singleplayer world or the LAN host.");
         } else {
            RollbackAction.TransferPlan plan = onClient(mc, () -> this.createPlan(mc));
            if (plan != null) {
               requireRunning();
               onServer(server, () -> duplicateAuthoritatively(server, plan));
            }
         }
      }
   }

   private RollbackAction.TransferPlan createPlan(Minecraft mc) {
      if (mc.player != null && mc.gameMode != null && mc.player.containerMenu != null) {
         AbstractContainerMenu menu = mc.player.containerMenu;
         if (menu != mc.player.inventoryMenu && menu.containerId != 0 && menu.getCarried().isEmpty()) {
            List<Integer> requested = this.requestedSlots(menu.slots.size());
            List<RollbackAction.SlotSnapshot> selected = new ArrayList<>();
            IdentityHashMap<Container, Set<Integer>> capturedContainerSlots = new IdentityHashMap<>();

            for (int slotId : requested) {
               if (slotId >= 0 && slotId < menu.slots.size()) {
                  Slot slot = (Slot)menu.slots.get(slotId);
                  if (slot.container != mc.player.getInventory() && slot.hasItem() && slot.mayPickup(mc.player) && slot.mayPlace(slot.getItem())) {
                     Set<Integer> indexes = capturedContainerSlots.computeIfAbsent(slot.container, ignored -> new LinkedHashSet<>());
                     if (indexes.add(slot.getContainerSlot())) {
                        selected.add(new RollbackAction.SlotSnapshot(slotId, slot.getItem().copy()));
                     }
                  }
               }
            }

            if (selected.isEmpty()) {
               RiptideClientMessaging.sendPrefixed("§cRollback found no transferable container items.");
               return null;
            } else {
               return new RollbackAction.TransferPlan(menu.containerId, selected, mc.player.getUUID());
            }
         } else {
            RiptideClientMessaging.sendPrefixed("§cOpen a container and keep the cursor empty first.");
            return null;
         }
      } else {
         RiptideClientMessaging.sendPrefixed("§cRollback requires an open container.");
         return null;
      }
   }

   private List<Integer> requestedSlots(int menuSize) {
      LinkedHashSet<Integer> result = new LinkedHashSet<>();
      switch (this.scope == null ? RollbackAction.Scope.ALL_CONTAINER : this.scope) {
         case ALL_CONTAINER:
            for (int i = 0; i < menuSize; i++) {
               result.add(i);
            }
            break;
         case CAPTURED_SLOTS:
            for (String raw : this.slots) {
               try {
                  result.add(Integer.parseInt(raw.trim()));
               } catch (RuntimeException var6) {
               }
            }
            break;
         case SLOT_RANGE:
            int first = Math.max(0, Math.min(this.startSlot, this.endSlot));
            int last = Math.min(menuSize - 1, Math.max(this.startSlot, this.endSlot));

            for (int i = first; i <= last; i++) {
               result.add(i);
            }
      }

      return List.copyOf(result);
   }

   private static boolean duplicateAuthoritatively(IntegratedServer server, RollbackAction.TransferPlan plan) {
      ServerPlayer player = server.getPlayerList().getPlayer(plan.playerId);
      if (player != null && player.containerMenu != null && player.containerMenu.containerId == plan.containerId && player.containerMenu.getCarried().isEmpty()
         )
       {
         AbstractContainerMenu menu = player.containerMenu;

         for (RollbackAction.SlotSnapshot expected : plan.slots) {
            if (expected.slotId >= menu.slots.size()) {
               throw new IllegalStateException("container layout changed");
            }

            Slot source = (Slot)menu.slots.get(expected.slotId);
            if (source.container == player.getInventory() || !ItemStack.matches(source.getItem(), expected.stack)) {
               throw new IllegalStateException("container contents changed before duplication");
            }
         }

         Inventory inventory = player.getInventory();
         List<ItemStack> simulated = new ArrayList<>(36);

         for (int slot = 0; slot < 36; slot++) {
            simulated.add(inventory.getItem(slot).copy());
         }

         for (RollbackAction.SlotSnapshot source : plan.slots) {
            if (!insertStack(simulated, source.stack.copy(), inventory.getMaxStackSize())) {
               throw new IllegalStateException("not enough inventory space for all selected items");
            }
         }

         for (int slot = 0; slot < simulated.size(); slot++) {
            ItemStack target = simulated.get(slot);
            if (!ItemStack.matches(inventory.getItem(slot), target)) {
               target.setPopTime(5);
               inventory.setItem(slot, target);
               player.connection.send(inventory.createInventoryUpdatePacket(slot));
            }
         }

         inventory.setChanged();
         menu.broadcastFullState();
         if (inventoryMatches(inventory, simulated) && sourceMatches(menu, plan)) {
            return true;
         } else {
            throw new IllegalStateException("authoritative duplicate verification failed");
         }
      } else {
         throw new IllegalStateException("the container is no longer open");
      }
   }

   private static boolean sourceMatches(AbstractContainerMenu menu, RollbackAction.TransferPlan plan) {
      for (RollbackAction.SlotSnapshot expected : plan.slots) {
         if (expected.slotId >= menu.slots.size() || !ItemStack.matches(((Slot)menu.slots.get(expected.slotId)).getItem(), expected.stack)) {
            return false;
         }
      }

      return true;
   }

   private static boolean inventoryMatches(Inventory inventory, List<ItemStack> expected) {
      if (expected.size() != 36) {
         return false;
      } else {
         for (int slot = 0; slot < expected.size(); slot++) {
            if (!ItemStack.matches(inventory.getItem(slot), expected.get(slot))) {
               return false;
            }
         }

         return true;
      }
   }

   private static boolean insertStack(List<ItemStack> inventory, ItemStack incoming, int inventoryMaxStackSize) {
      if (incoming.isEmpty()) {
         return true;
      } else {
         for (ItemStack target : inventory) {
            if (incoming.isEmpty()) {
               return true;
            }

            if (!target.isEmpty() && ItemStack.isSameItemSameComponents(target, incoming)) {
               int capacity = Math.min(inventoryMaxStackSize, target.getMaxStackSize()) - target.getCount();
               if (capacity > 0) {
                  int moved = Math.min(capacity, incoming.getCount());
                  target.grow(moved);
                  incoming.shrink(moved);
               }
            }
         }

         for (int slot = 0; slot < inventory.size() && !incoming.isEmpty(); slot++) {
            if (inventory.get(slot).isEmpty()) {
               int moved = Math.min(incoming.getCount(), Math.min(inventoryMaxStackSize, incoming.getMaxStackSize()));
               inventory.set(slot, incoming.copyWithCount(moved));
               incoming.shrink(moved);
            }
         }

         return incoming.isEmpty();
      }
   }

   private static void requireRunning() throws InterruptedException {
      if (PackHideState.isHardLocked() || !MacroExecutor.isCurrentActionRunActive() || Thread.currentThread().isInterrupted()) {
         throw new InterruptedException("rollback canceled");
      }
   }

   private static <T> T onClient(Minecraft mc, Supplier<T> task) throws Exception {
      if (mc.isSameThread()) {
         return task.get();
      } else {
         CompletableFuture<T> future = new CompletableFuture<>();
         mc.execute(() -> {
            try {
               future.complete(task.get());
            } catch (Throwable var3) {
               future.completeExceptionally(var3);
            }
         });
         return future.get(60L, TimeUnit.SECONDS);
      }
   }

   private static <T> T onServer(IntegratedServer server, Supplier<T> task) throws Exception {
      CompletableFuture<T> future = new CompletableFuture<>();
      server.execute(() -> {
         try {
            future.complete(task.get());
         } catch (Throwable var3) {
            future.completeExceptionally(var3);
         }
      });
      return future.get(60L, TimeUnit.SECONDS);
   }

   private static String conciseMessage(Throwable throwable) {
      Throwable current = throwable;

      while (current.getCause() != null) {
         current = current.getCause();
      }

      String message = current.getMessage();
      return message != null && !message.isBlank() ? message : current.getClass().getSimpleName();
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putBoolean("enabled", this.enabled);
      tag.putString("scope", this.scope.name());
      tag.put("slots", MacroStringList.toTag(this.slots));
      tag.putInt("startSlot", this.startSlot);
      tag.putInt("endSlot", this.endSlot);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.enabled = tag.getBooleanOr("enabled", true);
      this.scope = MacroStringList.enumValue(RollbackAction.Scope.class, tag.getStringOr("scope", "ALL_CONTAINER"), RollbackAction.Scope.ALL_CONTAINER);
      this.slots = MacroStringList.fromTag(tag.getList("slots").orElse(new ListTag()));
      this.startSlot = tag.getIntOr("startSlot", 0);
      this.endSlot = tag.getIntOr("endSlot", 26);
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.ROLLBACK;
   }

   @Override
   public String getDisplayName() {
      return "Rollback " + this.scope.name().replace('_', ' ');
   }

   @Override
   public String getIcon() {
      return "RB";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public static enum Scope {
      ALL_CONTAINER,
      CAPTURED_SLOTS,
      SLOT_RANGE;
   }

   private record SlotSnapshot(int slotId, ItemStack stack) {
   }

   private record TransferPlan(int containerId, List<RollbackAction.SlotSnapshot> slots, UUID playerId) {
   }
}
