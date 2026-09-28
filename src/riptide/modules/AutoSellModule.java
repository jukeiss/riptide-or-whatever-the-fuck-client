package riptide.modules;

import net.minecraft.world.item.ItemStack;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.KeybindSetting;
import riptide.api.module.StringSetting;
import riptide.util.RiptideBindUtil;
import riptide.util.RiptideClientMessaging;

public final class AutoSellModule extends Module {
   private int cooldownTicks;
   private int timerTicks;
   private int followUpTicks = -1;
   private boolean keyWasDown;

   public AutoSellModule() {
      super("auto-sell", "Auto Sell", ModuleCategory.MISC, "Runs your server's sell command automatically.");
      this.add(new StringSetting("command", "Sell Command", "/sell all").description("Exactly what your server uses, e.g. /sell all").build());
      this.add(new ChoiceSetting("trigger", "Trigger", "Hotkey", "Hotkey", "Inventory Full", "Timer").description("When to send the command.").build());
      this.add(
         new KeybindSetting("key", "Sell Key", -1).description("Sells once when pressed.").visibleWhen(() -> "Hotkey".equals(this.choice("trigger"))).build()
      );
      this.add(
         new IntSetting("free-slots", "Free Slots Left", 2, 0, 20, 1)
            .description("Sell once this few inventory slots remain empty.")
            .visibleWhen(() -> "Inventory Full".equals(this.choice("trigger")))
            .build()
      );
      this.add(
         new IntSetting("interval", "Interval", 60, 5, 600, 5)
            .description("Seconds between sells.")
            .visibleWhen(() -> "Timer".equals(this.choice("trigger")))
            .build()
      );
      this.add(new IntSetting("cooldown", "Cooldown", 5, 1, 120, 1).description("Minimum seconds between two sells, whatever the trigger.").build());
      this.add(
         new StringSetting("confirm-command", "Confirm Command", "")
            .description("Optional second command for servers that ask to confirm.")
            .group("Confirm")
            .build()
      );
      this.add(
         new IntSetting("confirm-delay", "Confirm Delay", 20, 1, 100, 1)
            .description("Ticks to wait before the confirm command.")
            .group("Confirm")
            .visibleWhen(() -> !this.text("confirm-command").isBlank())
            .build()
      );
      this.add(new BoolSetting("notify", "Notify", true).description("Print a line in chat on each sell.").build());
   }

   @Override
   public String info() {
      return this.choice("trigger");
   }

   @Override
   public void onEnable() {
      this.cooldownTicks = 0;
      this.timerTicks = 0;
      this.followUpTicks = -1;
      this.keyWasDown = false;
   }

   @Override
   public void onGameLeft() {
      this.onEnable();
   }

   @Override
   public void tick() {
      if (MC.player != null && MC.getConnection() != null) {
         if (this.followUpTicks > 0 && --this.followUpTicks == 0) {
            String var1 = this.text("confirm-command");
            if (!var1.isBlank()) {
               this.sendCommand(var1);
            }

            this.followUpTicks = -1;
         }

         if (this.cooldownTicks > 0) {
            this.cooldownTicks--;
         }

         String var3 = this.choice("trigger");
         switch (var3) {
            case "Hotkey":
               this.tickHotkey();
               break;
            case "Inventory Full":
               this.tickInventoryFull();
               break;
            case "Timer":
               this.tickTimer();
         }
      }
   }

   private void tickHotkey() {
      int var1 = this.bindCode();
      boolean var2 = var1 != -1 && RiptideBindUtil.isBindPressed(MC, var1);
      if (var2 && !this.keyWasDown && MC.gui.screen() == null) {
         this.sell();
      }

      this.keyWasDown = var2;
   }

   private void tickInventoryFull() {
      if (this.freeSlots() <= this.integer("free-slots")) {
         this.sell();
      }
   }

   private void tickTimer() {
      if (++this.timerTicks >= this.integer("interval") * 20) {
         this.timerTicks = 0;
         this.sell();
      }
   }

   private int freeSlots() {
      int var1 = 0;

      for (int var2 = 0; var2 < 36; var2++) {
         ItemStack var3 = MC.player.getInventory().getItem(var2);
         if (var3.isEmpty()) {
            var1++;
         }
      }

      return var1;
   }

   private void sell() {
      if (this.cooldownTicks <= 0) {
         String var1 = this.text("command");
         if (var1.isBlank()) {
            RiptideClientMessaging.sendPrefixed("§cSet a sell command first.");
            this.cooldownTicks = 20 * this.integer("cooldown");
         } else {
            this.sendCommand(var1);
            this.cooldownTicks = 20 * this.integer("cooldown");
            this.timerTicks = 0;
            if (!this.text("confirm-command").isBlank()) {
               this.followUpTicks = this.integer("confirm-delay");
            }

            if (this.bool("notify")) {
               RiptideClientMessaging.sendPrefixed("§aSold §7(" + var1 + ")");
            }
         }
      }
   }

   private int bindCode() {
      try {
         return Integer.parseInt(this.value("key"));
      } catch (NumberFormatException var2) {
         return -1;
      }
   }
}
