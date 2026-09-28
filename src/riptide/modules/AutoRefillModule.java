package riptide.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideHandArbiter;
import riptide.util.RiptideInventoryHelper;

public final class AutoRefillModule extends Module {
   private static final int PLAYER_SLOTS = 36;
   private static final int HOTBAR_SLOTS = 9;
   private static final int OFFHAND_SLOT = 40;
   private static final int COOLDOWN_TICKS = 6;
   private int cooldown;

   public AutoRefillModule() {
      super("auto-refill", "AutoRefill", ModuleCategory.PLAYER, "Refills the stack in your hand from your inventory before it runs out.");
      this.add(
         new IntSetting("threshold", "Refill At", 8, 1, 32, 1).description("Top up once the held stack drops to this many or fewer.").group("General").build()
      );
      this.add(
         new BoolSetting("blocks", "Blocks", true)
            .description("Refill placeable blocks. This is the one that matters while bridging.")
            .group("What Counts")
            .build()
      );
      this.add(
         new BoolSetting("throwables", "Pearls And Crystals", true)
            .description("Refill ender pearls, end crystals and experience bottles.")
            .group("What Counts")
            .build()
      );
      this.add(new BoolSetting("food", "Food", true).description("Refill whatever you are eating.").group("What Counts").build());
      this.add(new BoolSetting("offhand-too", "Off Hand", true).description("Refill the off hand as well as the main one.").group("What Counts").build());
   }

   @Override
   public void onEnable() {
      this.cooldown = 0;
   }

   @Override
   public void onGameLeft() {
      this.cooldown = 0;
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 == null || var1.player == null || var1.gameMode == null || var1.level == null) {
         this.cooldown = 0;
      } else if (this.cooldown > 0) {
         this.cooldown--;
      } else if (!(var1.gui.screen() instanceof AbstractContainerScreen)) {
         if (var1.player.containerMenu.getCarried().isEmpty()) {
            int var2 = var1.player.getInventory().getSelectedSlot();
            if (!this.refill(var1, var2)) {
               if (this.bool("offhand-too")) {
                  this.refillOffhand(var1);
               }
            }
         }
      }
   }

   private boolean refill(Minecraft var1, int var2) {
      if (var2 >= 0 && var2 < 9) {
         ItemStack var3 = var1.player.getInventory().getItem(var2);
         if (!this.needsTopUp(var3)) {
            return false;
         } else {
            int var4 = this.findSource(var1, var3, var2);
            if (var4 < 0) {
               return false;
            } else if (!RiptideHandArbiter.reserveSlot(this.id(), var4)) {
               return false;
            } else {
               boolean var5;
               try {
                  if (RiptideInventoryHelper.swapInventorySlots(var1, var4, var2)) {
                     this.cooldown = 6;
                     return true;
                  }

                  var5 = false;
               } finally {
                  RiptideHandArbiter.releaseSlot(this.id(), var4);
               }

               return var5;
            }
         }
      } else {
         return false;
      }
   }

   private void refillOffhand(Minecraft var1) {
      ItemStack var2 = var1.player.getOffhandItem();
      if (this.needsTopUp(var2)) {
         int var3 = this.findSource(var1, var2, 40);
         if (var3 >= 0) {
            if (RiptideHandArbiter.reserveSlot(this.id(), var3)) {
               try {
                  if (RiptideInventoryHelper.swapInventorySlots(var1, var3, 40)) {
                     this.cooldown = 6;
                  }
               } finally {
                  RiptideHandArbiter.releaseSlot(this.id(), var3);
               }
            }
         }
      }
   }

   private boolean needsTopUp(ItemStack var1) {
      if (var1 != null && !var1.isEmpty()) {
         if (var1.getMaxStackSize() <= 1) {
            return false;
         } else {
            return var1.getCount() > this.integer("threshold") ? false : this.wanted(var1);
         }
      } else {
         return false;
      }
   }

   private boolean wanted(ItemStack var1) {
      if (this.bool("food") && var1.get(DataComponents.FOOD) != null) {
         return true;
      } else {
         return !this.bool("throwables") || !var1.is(Items.ENDER_PEARL) && !var1.is(Items.END_CRYSTAL) && !var1.is(Items.EXPERIENCE_BOTTLE)
            ? this.bool("blocks") && var1.getItem() instanceof BlockItem
            : true;
      }
   }

   private int findSource(Minecraft var1, ItemStack var2, int var3) {
      int var4 = -1;
      int var5 = var2.getCount();

      for (int var6 = 0; var6 < 36; var6++) {
         if (var6 != var3 && !RiptideHandArbiter.slotReserved(var6, this.id())) {
            ItemStack var7 = var1.player.getInventory().getItem(var6);
            if (var7 != null && !var7.isEmpty() && ItemStack.isSameItemSameComponents(var7, var2) && var7.getCount() > var5) {
               var5 = var7.getCount();
               var4 = var6;
            }
         }
      }

      return var4;
   }
}
