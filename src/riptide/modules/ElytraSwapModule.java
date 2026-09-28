package riptide.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import riptide.api.module.BoolSetting;
import riptide.api.module.KeybindSetting;
import riptide.util.RiptideBindUtil;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideHandArbiter;
import riptide.util.RiptideInventoryHelper;

public final class ElytraSwapModule extends Module {
   private static final int SEARCH_SLOTS = 36;
   private static final int CHEST_INV_SLOT = 38;
   private boolean keyWasDown;
   private int cooldown;

   public ElytraSwapModule() {
      super("elytra-swap", "ElytraSwap", ModuleCategory.MOVEMENT, "Swaps between your chestplate and elytra with one key.");
      this.add(new KeybindSetting("bind", "Swap Key", 71).description("Press to swap whichever you are wearing for the other.").group("General").build());
      this.add(new BoolSetting("chat", "Confirm In Chat", true).description("Say which one you just put on.").group("General").build());
      this.add(
         new BoolSetting("only-in-world", "Only In World", true)
            .description("Ignore the key while a screen is open, so it cannot fire while you type.")
            .group("General")
            .build()
      );
   }

   @Override
   public void onEnable() {
      this.keyWasDown = false;
      this.cooldown = 0;
   }

   @Override
   public void onGameLeft() {
      this.keyWasDown = false;
      this.cooldown = 0;
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 != null && var1.player != null && var1.level != null) {
         if (this.cooldown > 0) {
            this.cooldown--;
         }

         int var2 = this.integer("bind");
         boolean var3 = var2 != -1 && (!this.bool("only-in-world") || var1.gui.screen() == null) && RiptideBindUtil.isBindPressed(var1, var2);
         boolean var4 = var3 && !this.keyWasDown;
         this.keyWasDown = var3;
         if (var4 && this.cooldown <= 0) {
            this.swap(var1);
         }
      } else {
         this.keyWasDown = false;
      }
   }

   private void swap(Minecraft var1) {
      ItemStack var2 = var1.player.getItemBySlot(EquipmentSlot.CHEST);
      boolean var3 = var2 != null && var2.is(Items.ELYTRA);
      int var4 = findReplacement(var1, var3);
      if (var4 < 0) {
         if (this.bool("chat")) {
            RiptideClientMessaging.sendPrefixed(var3 ? "§cNo chestplate in your inventory." : "§cNo elytra in your inventory.");
         }

         this.cooldown = 10;
      } else if (RiptideHandArbiter.reserveSlot(this.id(), 38)) {
         try {
            if (RiptideInventoryHelper.swapInventorySlots(var1, var4, 38)) {
               this.cooldown = 10;
               if (this.bool("chat")) {
                  RiptideClientMessaging.sendPrefixed(var3 ? "§7Wearing your §fchestplate§7." : "§7Wearing your §felytra§7.");
               }

               return;
            }
         } finally {
            RiptideHandArbiter.releaseSlot(this.id(), 38);
         }
      }
   }

   private static int findReplacement(Minecraft var0, boolean var1) {
      int var2 = -1;
      int var3 = -1;

      for (int var4 = 0; var4 < 36; var4++) {
         ItemStack var5 = var0.player.getInventory().getItem(var4);
         if (var5 != null && !var5.isEmpty()) {
            if (var1) {
               int var6 = chestplateScore(var5);
               if (var6 > var3) {
                  var3 = var6;
                  var2 = var4;
               }
            } else if (var5.is(Items.ELYTRA)) {
               int var7 = var5.getMaxDamage() - var5.getDamageValue();
               if (var7 > 1 && var7 > var3) {
                  var3 = var7;
                  var2 = var4;
               }
            }
         }
      }

      return var2;
   }

   static int chestplateScore(ItemStack var0) {
      if (var0 == null || var0.isEmpty()) {
         return -1;
      } else if (var0.is(Items.NETHERITE_CHESTPLATE)) {
         return 5;
      } else if (var0.is(Items.DIAMOND_CHESTPLATE)) {
         return 4;
      } else if (var0.is(Items.IRON_CHESTPLATE)) {
         return 3;
      } else if (var0.is(Items.CHAINMAIL_CHESTPLATE)) {
         return 2;
      } else if (var0.is(Items.GOLDEN_CHESTPLATE)) {
         return 1;
      } else {
         return var0.is(Items.LEATHER_CHESTPLATE) ? 0 : -1;
      }
   }

   @Override
   public String info() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 != null && var1.player != null) {
         ItemStack var2 = var1.player.getItemBySlot(EquipmentSlot.CHEST);
         return var2 != null && var2.is(Items.ELYTRA) ? "Elytra" : "Chestplate";
      } else {
         return "";
      }
   }
}
