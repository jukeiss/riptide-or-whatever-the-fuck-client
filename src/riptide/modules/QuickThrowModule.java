package riptide.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.KeybindSetting;
import riptide.util.RiptideBindUtil;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideHandArbiter;
import riptide.util.RiptideInventoryHelper;

public final class QuickThrowModule extends Module {
   private static final String PEARL = "Ender Pearl";
   private static final String GAPPLE = "Golden Apple";
   private static final String CRYSTAL = "End Crystal";
   private static final int HOTBAR_SLOTS = 9;
   private static final int RETURN_DELAY_TICKS = 2;
   private boolean keyWasDown;
   private int previousSlot = -1;
   private int returnIn;

   public QuickThrowModule() {
      super("quick-throw", "QuickThrow", ModuleCategory.COMBAT, "Throws a pearl from any hotbar slot and switches straight back.");
      this.add(
         new ChoiceSetting("item", "Throw", "Ender Pearl", "Ender Pearl", "Golden Apple", "End Crystal")
            .description("What the key throws or uses.")
            .group("General")
            .build()
      );
      this.add(new KeybindSetting("bind", "Throw Key", 82).description("Press to use it from wherever it is in your hotbar.").group("General").build());
      this.add(
         new BoolSetting("only-in-world", "Only In World", true)
            .description("Ignore the key while a screen is open, so it cannot fire while you type.")
            .group("General")
            .build()
      );
      this.add(new BoolSetting("warn-empty", "Say When Out", true).description("Say so when there is none left in your hotbar.").group("General").build());
   }

   @Override
   public void onEnable() {
      this.keyWasDown = false;
   }

   @Override
   public void onDisable() {
      this.restore();
   }

   @Override
   public void onGameLeft() {
      this.keyWasDown = false;
      this.previousSlot = -1;
      this.returnIn = 0;
      RiptideHandArbiter.releaseAll(this.id());
   }

   @Override
   public void tick() {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 == null || var1.player == null || var1.gameMode == null || var1.level == null) {
         this.keyWasDown = false;
         this.previousSlot = -1;
         this.returnIn = 0;
         RiptideHandArbiter.releaseAll(this.id());
      } else if (this.previousSlot >= 0) {
         if (--this.returnIn <= 0) {
            this.restore();
         }
      } else {
         int var2 = this.integer("bind");
         boolean var3 = var2 != -1 && (!this.bool("only-in-world") || var1.gui.screen() == null) && RiptideBindUtil.isBindPressed(var1, var2);
         boolean var4 = var3 && !this.keyWasDown;
         this.keyWasDown = var3;
         if (var4) {
            this.throwIt(var1);
         }
      }
   }

   private void throwIt(Minecraft var1) {
      int var2 = this.findSlot(var1);
      if (var2 < 0) {
         if (this.bool("warn-empty")) {
            RiptideClientMessaging.sendPrefixed("§cNo " + this.choice("item").toLowerCase() + " in your hotbar.");
         }
      } else if (RiptideHandArbiter.holdHand(this.id())) {
         if (!RiptideHandArbiter.beginHandPacketGroup(this.id())) {
            RiptideHandArbiter.releaseHand(this.id());
         } else {
            try {
               this.previousSlot = var1.player.getInventory().getSelectedSlot();
               RiptideHandArbiter.reserveSlot(this.id(), var2);
               RiptideInventoryHelper.selectHotbarSlot(var1, var2);
            } finally {
               RiptideHandArbiter.endHandPacketGroup(this.id());
            }

            var1.gameMode.useItem(var1.player, InteractionHand.MAIN_HAND);
            if ("Ender Pearl".equals(this.choice("item"))) {
               StatTrackerModule.notePearl();
            }

            this.returnIn = 2;
         }
      }
   }

   private int findSlot(Minecraft var1) {
      for (int var2 = 0; var2 < 9; var2++) {
         if (!RiptideHandArbiter.slotReserved(var2, this.id())) {
            ItemStack var3 = var1.player.getInventory().getItem(var2);
            if (var3 != null && !var3.isEmpty() && this.matches(var3)) {
               return var2;
            }
         }
      }

      return -1;
   }

   private boolean matches(ItemStack var1) {
      String var2 = this.choice("item");

      return switch (var2) {
         case "Golden Apple" -> var1.is(Items.GOLDEN_APPLE) || var1.is(Items.ENCHANTED_GOLDEN_APPLE);
         case "End Crystal" -> var1.is(Items.END_CRYSTAL);
         default -> var1.is(Items.ENDER_PEARL);
      };
   }

   private void restore() {
      int var1 = this.previousSlot;
      this.previousSlot = -1;
      this.returnIn = 0;

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

   @Override
   public String info() {
      return this.choice("item");
   }
}
