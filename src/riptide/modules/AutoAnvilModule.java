package riptide.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideClientMessaging;

public final class AutoAnvilModule extends Module {
   private static final int LEFT_INPUT = 0;
   private static final int RIGHT_INPUT = 1;
   private static final int OUTPUT = 2;
   private static final int COOLDOWN_TICKS = 4;
   private int cooldown;
   private int taken;
   private boolean warnedCost;

   public AutoAnvilModule() {
      super("auto-anvil", "AutoAnvil", ModuleCategory.PLAYER, "Takes the result out of an anvil whenever one is ready and you can afford it.");
      this.add(
         new IntSetting("max-cost", "Most To Spend", 30, 1, 39, 1)
            .unit("levels")
            .description("Never take a result that costs more than this.")
            .group("General")
            .build()
      );
      this.add(
         new BoolSetting("keep-levels", "Keep A Reserve", true)
            .description("Stop once taking the next one would leave you below the reserve.")
            .group("General")
            .build()
      );
      this.add(
         new IntSetting("reserve", "Reserve", 0, 0, 60, 1)
            .unit("levels")
            .description("Levels to leave untouched.")
            .visibleWhen(() -> this.bool("keep-levels"))
            .group("General")
            .build()
      );
      this.add(new BoolSetting("chat", "Report", true).description("Say how many were taken when the anvil is closed.").group("General").build());
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
      this.warnedCost = false;
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 != null && var1.player != null && var1.gameMode != null) {
         if (var1.gui.screen() instanceof AnvilScreen var2 && var2.getMenu() instanceof AnvilMenu var3) {
            if (this.cooldown > 0) {
               this.cooldown--;
            } else if (var1.player.containerMenu.getCarried().isEmpty()) {
               ItemStack var7 = var3.getSlot(2).getItem();
               if (var7 != null && !var7.isEmpty()) {
                  int var5 = var3.getCost();
                  if (this.canAfford(var1, var5)) {
                     var1.gameMode.handleContainerInput(var3.containerId, 2, 0, ContainerInput.QUICK_MOVE, var1.player);
                     this.cooldown = 4;
                     this.taken++;
                  }
               }
            }
         } else {
            if (this.taken > 0) {
               if (this.bool("chat")) {
                  RiptideClientMessaging.sendPrefixed("§aCombined §f" + this.taken + " §atime" + (this.taken == 1 ? "" : "s") + ".");
               }

               this.reset();
            }

            this.warnedCost = false;
         }
      } else {
         this.reset();
      }
   }

   private boolean canAfford(Minecraft var1, int var2) {
      if (var1.player.getAbilities().instabuild) {
         return true;
      } else if (var2 <= 0) {
         return true;
      } else if (var2 > this.integer("max-cost")) {
         if (!this.warnedCost && this.bool("chat")) {
            this.warnedCost = true;
            RiptideClientMessaging.sendPrefixed("§eStopped: that combine costs §f" + var2 + " §elevels.");
         }

         return false;
      } else {
         int var3 = var1.player.experienceLevel;
         int var4 = this.bool("keep-levels") ? this.integer("reserve") : 0;
         if (var3 - var2 >= var4) {
            return true;
         } else {
            if (!this.warnedCost && this.bool("chat")) {
               this.warnedCost = true;
               RiptideClientMessaging.sendPrefixed("§eStopped: not enough levels to spare.");
            }

            return false;
         }
      }
   }

   static boolean allows(int var0, int var1, int var2, int var3) {
      if (var0 <= 0) {
         return true;
      } else {
         return var0 > var2 ? false : var1 - var0 >= var3;
      }
   }

   @Override
   public String info() {
      return this.taken > 0 ? Integer.toString(this.taken) : "";
   }

   static int[] slots() {
      return new int[]{0, 1, 2};
   }
}
