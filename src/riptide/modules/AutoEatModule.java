package riptide.modules;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideInventoryHelper;

public final class AutoEatModule extends Module {
   private int originalSlot = -1;
   private boolean eating;

   public AutoEatModule() {
      super("auto-eat", "Auto Eat", ModuleCategory.PLAYER, "Eats when your hunger gets low.");
      this.add(new IntSetting("hunger", "Eat At", 16, 1, 19, 1).description("Eat once your hunger bar drops to this (20 is full).").build());
      this.add(new IntSetting("health", "Or Health Below", 0, 0, 20, 1).description("Also eat below this health. 0 turns this off.").build());
      this.add(new BoolSetting("switch-back", "Switch Back", true).description("Return to your previous hotbar slot when finished.").build());
      this.add(new BoolSetting("skip-bad", "Skip Bad Food", true).description("Ignore rotten flesh, spider eyes, pufferfish and chorus fruit.").build());
   }

   @Override
   public String info() {
      return this.eating ? "eating" : "idle";
   }

   @Override
   public void onEnable() {
      this.stopEating();
   }

   @Override
   public void onDisable() {
      this.stopEating();
   }

   @Override
   public void onGameLeft() {
      this.stopEating();
   }

   @Override
   public void tick() {
      if (MC.player == null || MC.gameMode == null || MC.gui.screen() != null || MC.player.hasInfiniteMaterials()) {
         this.stopEating();
      } else if (this.eating) {
         if (isFood(MC.player.getMainHandItem()) && this.hungry()) {
            if (!MC.player.isUsingItem()) {
               MC.options.keyUse.setDown(true);
            }
         } else {
            this.stopEating();
         }
      } else if (this.hungry() && !MC.player.isUsingItem()) {
         int var1 = this.bestFoodSlot();
         if (var1 >= 0) {
            this.originalSlot = MC.player.getInventory().getSelectedSlot();
            RiptideInventoryHelper.selectHotbarSlot(MC, var1);
            this.eating = true;
            MC.options.keyUse.setDown(true);
         }
      }
   }

   private boolean hungry() {
      if (MC.player.getFoodData().getFoodLevel() <= this.integer("hunger")) {
         return true;
      } else {
         int var1 = this.integer("health");
         return var1 > 0 && MC.player.getHealth() <= var1;
      }
   }

   private int bestFoodSlot() {
      int var1 = -1;
      int var2 = -1;

      for (int var3 = 0; var3 < 9; var3++) {
         ItemStack var4 = MC.player.getInventory().getItem(var3);
         if (isFood(var4) && (!this.bool("skip-bad") || !isBad(var4))) {
            FoodProperties var5 = (FoodProperties)var4.get(DataComponents.FOOD);
            int var6 = var5 == null ? 0 : var5.nutrition();
            if (var6 > var2) {
               var2 = var6;
               var1 = var3;
            }
         }
      }

      return var1;
   }

   private void stopEating() {
      if (this.eating) {
         MC.options.keyUse.setDown(false);
         if (this.bool("switch-back") && this.originalSlot >= 0 && MC.player != null) {
            RiptideInventoryHelper.selectHotbarSlot(MC, this.originalSlot);
         }
      }

      this.eating = false;
      this.originalSlot = -1;
   }

   private static boolean isFood(ItemStack var0) {
      return !var0.isEmpty() && var0.has(DataComponents.FOOD);
   }

   private static boolean isBad(ItemStack var0) {
      return var0.is(Items.ROTTEN_FLESH)
         || var0.is(Items.SPIDER_EYE)
         || var0.is(Items.PUFFERFISH)
         || var0.is(Items.CHORUS_FRUIT)
         || var0.is(Items.POISONOUS_POTATO);
   }
}
