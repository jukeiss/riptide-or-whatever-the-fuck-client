package riptide.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideClientMessaging;

public final class ItemSaverModule extends Module {
   private static final long WARN_GAP_MS = 20000L;
   private long lastWarnMs;
   private long lastBlockedMessageMs;

   public ItemSaverModule() {
      super("item-saver", "ItemSaver", ModuleCategory.PLAYER, "Stops using a tool before it breaks, and warns when armour gets low.");
      this.add(
         new IntSetting("threshold", "Stop At", 8, 1, 50, 1)
            .unit("%")
            .description("Refuse to use the held item below this much durability left.")
            .group("General")
            .build()
      );
      this.add(
         new BoolSetting("block-attack", "Block Attacking", true)
            .description("Also stop attacks, not just block breaking and item use.")
            .group("General")
            .build()
      );
      this.add(
         new BoolSetting("warn-armor", "Warn On Armour", true)
            .description("Say something when a worn piece drops below the threshold.")
            .group("Armour")
            .build()
      );
      this.add(new BoolSetting("chat", "Explain When Blocked", true).description("Say why, the first time it stops you.").group("General").build());
   }

   @Override
   public boolean shouldCancelUse(HitResult var1, InteractionHand var2) {
      return this.blocked(held(var2));
   }

   @Override
   public boolean shouldCancelAttack(HitResult var1) {
      return !this.bool("block-attack") ? false : this.blocked(held(InteractionHand.MAIN_HAND));
   }

   @Override
   public boolean shouldCancelStartBreakingBlock(BlockPos var1, Direction var2) {
      return this.blocked(held(InteractionHand.MAIN_HAND));
   }

   @Override
   public void tick() {
      if (this.bool("warn-armor")) {
         Minecraft var1 = Minecraft.getInstance();
         if (var1 != null && var1.player != null) {
            if (System.currentTimeMillis() - this.lastWarnMs >= 20000L) {
               for (EquipmentSlot var5 : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                  ItemStack var6 = var1.player.getItemBySlot(var5);
                  if (this.isLow(var6)) {
                     this.lastWarnMs = System.currentTimeMillis();
                     RiptideClientMessaging.sendPrefixed("§eYour " + var6.getHoverName().getString() + " is down to " + percentLeft(var6) + "%.");
                     return;
                  }
               }
            }
         }
      }
   }

   private static ItemStack held(InteractionHand var0) {
      Minecraft var1 = Minecraft.getInstance();
      return var1 != null && var1.player != null ? var1.player.getItemInHand(var0 == null ? InteractionHand.MAIN_HAND : var0) : ItemStack.EMPTY;
   }

   private boolean blocked(ItemStack var1) {
      if (!this.isLow(var1)) {
         return false;
      } else {
         if (this.bool("chat") && System.currentTimeMillis() - this.lastBlockedMessageMs > 20000L) {
            this.lastBlockedMessageMs = System.currentTimeMillis();
            RiptideClientMessaging.sendPrefixed("§cHeld back: §f" + var1.getHoverName().getString() + "§c is at " + percentLeft(var1) + "%.");
         }

         return true;
      }
   }

   private boolean isLow(ItemStack var1) {
      return var1 != null && !var1.isEmpty() && var1.isDamageableItem() ? percentLeft(var1) <= this.integer("threshold") : false;
   }

   static int percentLeft(ItemStack var0) {
      return percentLeft(var0.getMaxDamage(), var0.getDamageValue());
   }

   static int percentLeft(int var0, int var1) {
      if (var0 <= 0) {
         return 100;
      } else {
         int var2 = var0 - var1;
         return Math.max(0, Math.min(100, var2 * 100 / var0));
      }
   }
}
