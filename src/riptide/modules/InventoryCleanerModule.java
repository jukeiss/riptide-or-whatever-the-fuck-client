package riptide.modules;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.StringListSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideDropHelper;
import riptide.util.RiptideHandArbiter;
import riptide.util.RiptideSettingCache;

public final class InventoryCleanerModule extends Module {
   private static final int PLAYER_SLOTS = 36;
   private static final String DEFAULT_JUNK = String.join(
      "|",
      "minecraft:cobblestone",
      "minecraft:cobbled_deepslate",
      "minecraft:dirt",
      "minecraft:gravel",
      "minecraft:andesite",
      "minecraft:diorite",
      "minecraft:granite",
      "minecraft:tuff",
      "minecraft:rotten_flesh",
      "minecraft:poisonous_potato",
      "minecraft:netherrack"
   );
   private static final int COOLDOWN_TICKS = 4;
   private final RiptideSettingCache<Set<Item>> junk = new RiptideSettingCache<>(InventoryCleanerModule::parseItems);
   private int cooldown;
   private int dropped;

   public InventoryCleanerModule() {
      super("inventory-cleaner", "InventoryCleaner", ModuleCategory.PLAYER, "Drops junk when your inventory is nearly full.");
      this.add(
         new IntSetting("free-slots", "Clean Below", 4, 0, 30, 1)
            .description("Start dropping once you have fewer than this many empty slots.")
            .group("General")
            .build()
      );
      this.add(new StringListSetting("junk", "Junk", DEFAULT_JUNK).description("Items to throw away. Nothing else is ever dropped.").group("General").build());
      this.add(
         new BoolSetting("keep-hotbar", "Leave The Hotbar Alone", true)
            .description("Never drop from the nine slots you can hold. What is there is there because you put it there.")
            .group("General")
            .build()
      );
      this.add(new BoolSetting("chat", "Say What Went", true).description("Print a line when something is dropped.").group("General").build());
   }

   @Override
   public void onEnable() {
      this.cooldown = 0;
      this.dropped = 0;
   }

   @Override
   public void onGameLeft() {
      this.onEnable();
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
            if (freeSlots(var1) < this.integer("free-slots")) {
               int var2 = this.nextJunkSlot(var1);
               if (var2 >= 0) {
                  ItemStack var3 = var1.player.getInventory().getItem(var2);
                  String var4 = var3.getHoverName().getString();
                  int var5 = var3.getCount();
                  if (RiptideHandArbiter.reserveSlot(this.id(), var2)) {
                     try {
                        if (RiptideDropHelper.dropFromInventorySlot(var1, var2, var5) <= 0) {
                           return;
                        }
                     } finally {
                        RiptideHandArbiter.releaseSlot(this.id(), var2);
                     }

                     this.cooldown = 4;
                     this.dropped += var5;
                     if (this.bool("chat")) {
                        RiptideClientMessaging.sendPrefixed("§7Dropped §f" + var5 + "x " + var4 + "§7.");
                     }
                  }
               }
            }
         }
      }
   }

   static int freeSlotsIn(List<ItemStack> var0) {
      int var1 = 0;

      for (ItemStack var3 : var0) {
         if (var3 == null || var3.isEmpty()) {
            var1++;
         }
      }

      return var1;
   }

   private static int freeSlots(Minecraft var0) {
      int var1 = 0;

      for (int var2 = 0; var2 < 36; var2++) {
         ItemStack var3 = var0.player.getInventory().getItem(var2);
         if (var3 == null || var3.isEmpty()) {
            var1++;
         }
      }

      return var1;
   }

   private int nextJunkSlot(Minecraft var1) {
      Set var2 = this.junk.get(this.list("junk"));
      if (var2.isEmpty()) {
         return -1;
      } else {
         int var3 = this.bool("keep-hotbar") ? 9 : 0;

         for (int var4 = 35; var4 >= var3; var4--) {
            if (!RiptideHandArbiter.slotReserved(var4, this.id())) {
               ItemStack var5 = var1.player.getInventory().getItem(var4);
               if (var5 != null && !var5.isEmpty() && var2.contains(var5.getItem())) {
                  return var4;
               }
            }
         }

         return -1;
      }
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
      return this.dropped > 0 ? this.dropped + " dropped" : "";
   }
}
