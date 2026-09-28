package riptide.modules;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideHandArbiter;
import riptide.util.RiptideInventoryClickHelper;

public final class InventoryTotemModule extends Module {
   private static final int OFFHAND_MENU_SLOT = 45;
   private static final int OFFHAND_BUTTON = 40;
   private InventoryTotemModule.Phase phase = InventoryTotemModule.Phase.IDLE;
   private boolean openedByUs;
   private int carriedFrom = -1;
   private int wait;

   public InventoryTotemModule() {
      super("inventory-totem", "Inventory Totem", ModuleCategory.COMBAT, "Restocks totems with real inventory clicks.");
      this.add(
         new ChoiceSetting("mode", "Mode", "Open Inventory", "Open Inventory", "When Open")
            .description("Open the inventory itself, or only act while you have it open.")
            .build()
      );
      this.add(
         new ChoiceSetting("click", "Click Style", "Swap Key", "Swap Key", "Pick & Place")
            .description("Offhand swap key (1 click) or pick up and place (2 clicks).")
            .build()
      );
      this.add(new IntSetting("step-delay", "Step Delay", 2, 0, 20, 1).description("Ticks between each step of the macro.").build());
      this.add(new BoolSetting("refill-hotbar", "Refill Hotbar", false).description("Also keep a totem in a hotbar slot.").build());
      this.add(new IntSetting("hotbar-slot", "Hotbar Slot", 9, 1, 9, 1).description("Hotbar slot to keep stocked.").build());
      this.add(new BoolSetting("close", "Close Inventory", true).description("Close the inventory afterwards if the macro opened it.").build());
   }

   @Override
   public void onEnable() {
      this.reset();
   }

   @Override
   public void onDisable() {
      this.closeIfOurs();
      this.reset();
   }

   @Override
   public void onGameLeft() {
      this.reset();
   }

   @Override
   public void tick() {
      if (MC.player == null || MC.gameMode == null || !MC.player.isAlive() || MC.player.hasInfiniteMaterials()) {
         this.reset();
      } else if (this.wait > 0) {
         this.wait--;
      } else {
         switch (this.phase) {
            case IDLE:
               this.tickIdle();
               break;
            case OPENED:
               this.tickOpened();
               break;
            case CARRYING:
               this.tickCarrying();
               break;
            case CLOSING:
               this.tickClosing();
         }
      }
   }

   private void tickIdle() {
      if (this.hasWork() && !AutoTotemModule.operationActive() && !RiptideHandArbiter.offhandClaimedByOther(this.id())) {
         if (MC.gui.screen() instanceof InventoryScreen) {
            this.openedByUs = false;
            this.next(InventoryTotemModule.Phase.OPENED);
         } else if (MC.gui.screen() == null && "Open Inventory".equals(this.choice("mode"))) {
            MC.gui.setScreen(new InventoryScreen(MC.player));
            this.openedByUs = true;
            this.next(InventoryTotemModule.Phase.OPENED);
         }
      }
   }

   private void tickOpened() {
      if (!this.inventoryOpen()) {
         this.reset();
      } else {
         if (!isTotem(MC.player.getOffhandItem())) {
            int var1 = this.findTotem(true);
            if (var1 >= 0) {
               if ("Pick & Place".equals(this.choice("click"))) {
                  RiptideInventoryClickHelper.click(MC, var1, 0, ContainerInput.PICKUP);
                  this.carriedFrom = var1;
                  this.next(InventoryTotemModule.Phase.CARRYING);
               } else {
                  RiptideInventoryClickHelper.click(MC, var1, 40, ContainerInput.SWAP);
                  this.next(InventoryTotemModule.Phase.OPENED);
               }

               return;
            }
         }

         if (this.hotbarNeedsTotem()) {
            int var2 = this.findTotem(false);
            if (var2 >= 0) {
               RiptideInventoryClickHelper.click(MC, var2, this.hotbarIndex(), ContainerInput.SWAP);
               this.next(InventoryTotemModule.Phase.OPENED);
               return;
            }
         }

         this.next(InventoryTotemModule.Phase.CLOSING);
      }
   }

   private void tickCarrying() {
      if (!this.inventoryOpen()) {
         this.reset();
      } else {
         if (isTotem(MC.player.containerMenu.getCarried())) {
            RiptideInventoryClickHelper.click(MC, 45, 0, ContainerInput.PICKUP);
         }

         if (!MC.player.containerMenu.getCarried().isEmpty() && this.carriedFrom >= 0) {
            RiptideInventoryClickHelper.click(MC, this.carriedFrom, 0, ContainerInput.PICKUP);
         }

         this.carriedFrom = -1;
         this.next(InventoryTotemModule.Phase.OPENED);
      }
   }

   private void tickClosing() {
      if (this.openedByUs && this.bool("close") && this.inventoryOpen() && MC.player.containerMenu.getCarried().isEmpty()) {
         MC.player.closeContainer();
      }

      this.phase = InventoryTotemModule.Phase.IDLE;
      this.openedByUs = false;
      this.wait = this.hasWork() ? 40 : Math.max(this.integer("step-delay"), 5);
   }

   private boolean hasWork() {
      boolean var1 = !isTotem(MC.player.getOffhandItem()) && this.findTotem(true) >= 0;
      boolean var2 = this.hotbarNeedsTotem() && this.findTotem(false) >= 0;
      return var1 || var2;
   }

   private boolean hotbarNeedsTotem() {
      return this.bool("refill-hotbar") && !isTotem(MC.player.getInventory().getItem(this.hotbarIndex()));
   }

   private int hotbarIndex() {
      return Math.max(0, Math.min(8, this.integer("hotbar-slot") - 1));
   }

   private int findTotem(boolean var1) {
      InventoryMenu var2 = MC.player.inventoryMenu;

      for (int var3 = 9; var3 < 36; var3++) {
         if (isTotem(var2.getSlot(var3).getItem())) {
            return var3;
         }
      }

      if (var1) {
         int var5 = this.bool("refill-hotbar") ? 36 + this.hotbarIndex() : -1;

         for (int var4 = 36; var4 < 45; var4++) {
            if (var4 != var5 && isTotem(var2.getSlot(var4).getItem())) {
               return var4;
            }
         }

         if (var5 >= 0 && isTotem(var2.getSlot(var5).getItem())) {
            return var5;
         }
      }

      return -1;
   }

   private boolean inventoryOpen() {
      return MC.gui.screen() instanceof InventoryScreen && MC.player.containerMenu == MC.player.inventoryMenu;
   }

   private void next(InventoryTotemModule.Phase var1) {
      this.phase = var1;
      this.wait = this.integer("step-delay");
   }

   private void closeIfOurs() {
      if (this.openedByUs && MC.player != null && this.inventoryOpen() && MC.player.containerMenu.getCarried().isEmpty()) {
         MC.player.closeContainer();
      }
   }

   private void reset() {
      this.phase = InventoryTotemModule.Phase.IDLE;
      this.openedByUs = false;
      this.carriedFrom = -1;
      this.wait = 0;
   }

   private static boolean isTotem(ItemStack var0) {
      return var0.is(Items.TOTEM_OF_UNDYING);
   }

   private static enum Phase {
      IDLE,
      OPENED,
      CARRYING,
      CLOSING;
   }
}
