package riptide.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideHandArbiter;
import riptide.util.RiptideInventoryHelper;

public final class AutoFireworkModule extends Module {
   private static final int HOTBAR_SLOTS = 9;
   private static final long MIN_GAP_MS = 400L;
   private long lastUsedMs;
   private int previousSlot = -1;
   private boolean warnedEmpty;

   public AutoFireworkModule() {
      super("auto-firework", "AutoFirework", ModuleCategory.MOVEMENT, "Fires elytra rockets for you when your speed drops off.");
      this.add(
         new IntSetting("min-speed", "Boost Below", 25, 5, 100, 1)
            .description("Speed, in tenths of a block per tick, under which it fires.")
            .group("General")
            .build()
      );
      this.add(new IntSetting("delay", "Minimum Delay", 900, 400, 5000, 50).unit("ms").description("Shortest gap between rockets.").group("General").build());
      this.add(
         new BoolSetting("forward-only", "Only While Holding Forward", true)
            .description("Do nothing while you are gliding down on purpose.")
            .group("General")
            .build()
      );
      this.add(
         new BoolSetting("warn-empty", "Warn When Out", true).description("Say so once when there are no rockets left in the hotbar.").group("General").build()
      );
   }

   @Override
   public void onDisable() {
      this.restore();
      this.warnedEmpty = false;
   }

   @Override
   public void onGameLeft() {
      this.previousSlot = -1;
      this.warnedEmpty = false;
      RiptideHandArbiter.releaseAll(this.id());
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 == null || var1.player == null || var1.gameMode == null || var1.level == null || var1.options == null) {
         this.previousSlot = -1;
         RiptideHandArbiter.releaseAll(this.id());
      } else if (this.previousSlot >= 0) {
         this.restore();
      } else if (!var1.player.isFallFlying()) {
         this.warnedEmpty = false;
      } else if (!this.bool("forward-only") || var1.options.keyUp.isDown()) {
         long var2 = System.currentTimeMillis();
         if (var2 - this.lastUsedMs >= Math.max(400L, (long)this.integer("delay"))) {
            if (speedTenths(var1) < this.integer("min-speed")) {
               int var4 = this.rocketSlot(var1);
               if (var4 < 0) {
                  if (this.bool("warn-empty") && !this.warnedEmpty) {
                     this.warnedEmpty = true;
                     RiptideClientMessaging.sendPrefixed("§cAutoFirework: no rockets in your hotbar.");
                  }
               } else {
                  this.warnedEmpty = false;
                  if (this.takeHand(var1, var4)) {
                     var1.gameMode.useItem(var1.player, InteractionHand.MAIN_HAND);
                     this.lastUsedMs = var2;
                  }
               }
            }
         }
      }
   }

   private static int speedTenths(Minecraft var0) {
      Vec3 var1 = var0.player.getDeltaMovement();
      double var2 = Math.sqrt(var1.x * var1.x + var1.z * var1.z);
      return (int)Math.round(var2 * 10.0);
   }

   private int rocketSlot(Minecraft var1) {
      for (int var2 = 0; var2 < 9; var2++) {
         if (!RiptideHandArbiter.slotReserved(var2, this.id())) {
            ItemStack var3 = var1.player.getInventory().getItem(var2);
            if (var3 != null && var3.is(Items.FIREWORK_ROCKET)) {
               return var2;
            }
         }
      }

      return -1;
   }

   private void restore() {
      int var1 = this.previousSlot;
      this.previousSlot = -1;

      try {
         if (var1 < 0) {
            return;
         }

         Minecraft var2 = Minecraft.getInstance();
         if (var2 != null && var2.player != null) {
            RiptideInventoryHelper.restoreHotbarSlot(var2, var1);
            return;
         }
      } finally {
         RiptideHandArbiter.releaseAll(this.id());
      }
   }

   private boolean takeHand(Minecraft var1, int var2) {
      if (!RiptideHandArbiter.holdHand(this.id())) {
         return false;
      } else if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
         RiptideHandArbiter.releaseHand(this.id());
         return false;
      } else {
         try {
            this.previousSlot = var1.player.getInventory().getSelectedSlot();
            RiptideHandArbiter.reserveSlot(this.id(), var2);
            RiptideInventoryHelper.selectHotbarSlot(var1, var2);
         } finally {
            RiptideHandArbiter.endHandPacketGroup(this.id());
         }

         return true;
      }
   }
}
