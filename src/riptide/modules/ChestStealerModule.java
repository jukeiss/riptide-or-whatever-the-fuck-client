package riptide.modules;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.StringListSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideSettingCache;

public final class ChestStealerModule extends Module {
   private static final int PLAYER_SLOTS = 36;
   private static final int MIN_DELAY_TICKS = 1;
   private final RiptideSettingCache<Set<Item>> wanted = new RiptideSettingCache<>(ChestStealerModule::parseItems);
   private int cooldown;
   private int taken;
   private boolean finishedReported;

   public ChestStealerModule() {
      super("chest-stealer", "ChestStealer", ModuleCategory.PLAYER, "Empties an open container into your inventory.");
      this.add(
         new IntSetting("delay", "Delay", 2, 1, 20, 1)
            .unit("ticks")
            .description("Ticks between each item. Lower is faster and more likely to be rejected.")
            .group("General")
            .build()
      );
      this.add(
         new BoolSetting("all-items", "Take Everything", true)
            .description("Take every item. Turn off to take only what is listed below.")
            .group("General")
            .build()
      );
      this.add(
         new StringListSetting("items", "Item List", "minecraft:diamond|minecraft:netherite_ingot")
            .description("Items to take when Take Everything is off.")
            .visibleWhen(() -> !this.bool("all-items"))
            .group("General")
            .build()
      );
      this.add(
         new BoolSetting("leave-junk", "Leave Junk", false)
            .description("Skip cobblestone, dirt, gravel and the other things not worth the trip.")
            .visibleWhen(() -> this.bool("all-items"))
            .group("General")
            .build()
      );
      this.add(
         new BoolSetting("close-when-done", "Close When Empty", true)
            .description("Close the container once there is nothing left worth taking.")
            .group("General")
            .build()
      );
      this.add(new BoolSetting("chat", "Report", true).description("Say how much was taken.").group("General").build());
   }

   @Override
   public void onEnable() {
      this.reset();
   }

   @Override
   public void onDisable() {
      this.reset();
   }

   @Override
   public void onGameLeft() {
      this.reset();
   }

   private void reset() {
      this.cooldown = 0;
      this.taken = 0;
      this.finishedReported = false;
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 != null && var1.player != null && var1.gameMode != null) {
         if (var1.gui.screen() instanceof AbstractContainerScreen var2) {
            AbstractContainerMenu var6 = var2.getMenu();
            if (var6 != null && var1.player.containerMenu.getCarried().isEmpty()) {
               if (this.cooldown > 0) {
                  this.cooldown--;
               } else {
                  Slot var4 = this.nextSlot(var1, var6);
                  if (var4 == null) {
                     this.finish(var1);
                  } else {
                     int var5 = var4.getItem().getCount();
                     var1.gameMode.handleContainerInput(var6.containerId, var4.index, 0, ContainerInput.QUICK_MOVE, var1.player);
                     this.taken += var5;
                     this.cooldown = Math.max(1, this.integer("delay"));
                  }
               }
            }
         } else {
            if (this.taken > 0 || this.finishedReported) {
               this.reset();
            }
         }
      } else {
         this.reset();
      }
   }

   private Slot nextSlot(Minecraft var1, AbstractContainerMenu var2) {
      Set var3 = this.bool("all-items") ? Set.of() : this.wanted.get(this.list("items"));
      boolean var4 = this.bool("all-items");
      boolean var5 = var4 && this.bool("leave-junk");

      for (Slot var7 : var2.slots) {
         if (var7 != null && var7.container != var1.player.getInventory()) {
            ItemStack var8 = var7.getItem();
            if (var8 != null && !var8.isEmpty() && (var4 ? !var5 || !isJunk(var8) : var3.contains(var8.getItem())) && hasRoomFor(var1, var8)) {
               return var7;
            }
         }
      }

      return null;
   }

   private static boolean hasRoomFor(Minecraft var0, ItemStack var1) {
      for (int var2 = 0; var2 < 36; var2++) {
         ItemStack var3 = var0.player.getInventory().getItem(var2);
         if (var3 == null || var3.isEmpty()) {
            return true;
         }

         if (ItemStack.isSameItemSameComponents(var3, var1) && var3.getCount() < var3.getMaxStackSize()) {
            return true;
         }
      }

      return false;
   }

   private void finish(Minecraft var1) {
      if (!this.finishedReported) {
         this.finishedReported = true;
         if (this.bool("chat") && this.taken > 0) {
            RiptideClientMessaging.sendPrefixed("§aTook §f" + this.taken + " §aitem" + (this.taken == 1 ? "" : "s") + ".");
         }

         if (this.bool("close-when-done")) {
            var1.player.closeContainer();
         }
      }
   }

   private static boolean isJunk(ItemStack var0) {
      return var0.is(Items.COBBLESTONE)
         || var0.is(Items.DIRT)
         || var0.is(Items.GRAVEL)
         || var0.is(Items.SAND)
         || var0.is(Items.NETHERRACK)
         || var0.is(Items.COBBLED_DEEPSLATE)
         || var0.is(Items.ROTTEN_FLESH)
         || var0.is(Items.POISONOUS_POTATO);
   }

   static Set<Item> parseItems(List<String> var0) {
      HashSet var1 = new HashSet();

      for (String var3 : var0) {
         if (var3 != null && !var3.isBlank()) {
            String var4 = var3.trim().toLowerCase(Locale.ROOT);
            if (var4.indexOf(58) < 0) {
               var4 = "minecraft:" + var4;
            }

            Identifier var5 = Identifier.tryParse(var4);
            if (var5 != null) {
               BuiltInRegistries.ITEM.getOptional(var5).ifPresent(var1::add);
            }
         }
      }

      return Set.copyOf(var1);
   }

   @Override
   public String info() {
      return this.taken > 0 ? this.taken + " taken" : "";
   }
}
