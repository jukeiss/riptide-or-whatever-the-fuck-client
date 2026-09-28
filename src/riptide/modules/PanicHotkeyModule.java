package riptide.modules;

import java.util.ArrayDeque;
import java.util.Queue;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.KeybindSetting;
import riptide.api.module.StringSetting;
import riptide.util.RiptideBindUtil;
import riptide.util.RiptideClientMessaging;

public final class PanicHotkeyModule extends Module {
   private final Queue<String> pending = new ArrayDeque<>();
   private boolean keyWasDown;
   private int cooldown;
   private long lastPress;

   public PanicHotkeyModule() {
      super("panic-hotkey", "Panic Hotkey", ModuleCategory.MISC, "Runs a saved command sequence on one keypress.");
      this.add(new KeybindSetting("key", "Panic Key", -1).description("The key that fires the sequence.").build());
      this.add(
         new ChoiceSetting("mode", "Mode", "Order", "Order", "Sell", "Both")
            .description("Order hands items to a player, Sell lists them, Both runs order then sell.")
            .build()
      );
      this.add(new StringSetting("target", "Send To", "").description("Player name used by the order command.").build());
      this.add(
         new StringSetting("order-command", "Order Command", "order {target}")
            .description("Command run in Order mode. {target} is replaced with the name above.")
            .build()
      );
      this.add(new StringSetting("sell-command", "Sell Command", "ah sell").description("Command run in Sell mode.").build());
      this.add(new StringSetting("extra", "Extra Commands", "").description("Optional extra commands, separated by ; — run after the others.").build());
      this.add(new IntSetting("delay", "Command Delay", 10, 2, 100, 1).description("Ticks between commands, so the server doesn't throttle you.").build());
      this.add(
         new BoolSetting("double-press", "Double Press", true).description("Require two presses within a second, so you can't fire it by accident.").build()
      );
      this.add(new BoolSetting("notify", "Notify", true).description("Print what it's running in chat.").build());
   }

   @Override
   public String info() {
      return this.pending.isEmpty() ? this.choice("mode") : this.pending.size() + " queued";
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
      this.pending.clear();
      this.keyWasDown = false;
      this.cooldown = 0;
      this.lastPress = 0L;
   }

   @Override
   public void tick() {
      if (MC.player == null || MC.level == null) {
         this.reset();
      } else if (!this.pending.isEmpty()) {
         if (this.cooldown > 0) {
            this.cooldown--;
         } else {
            this.sendCommand(this.pending.poll());
            this.cooldown = this.integer("delay");
         }
      } else {
         int var1 = this.integer("key");
         if (var1 != -1) {
            boolean var2 = RiptideBindUtil.isBindPressed(MC, var1) && MC.gui.screen() == null;
            boolean var3 = var2 && !this.keyWasDown;
            this.keyWasDown = var2;
            if (var3) {
               this.trigger();
            }
         }
      }
   }

   private void trigger() {
      long var1 = System.currentTimeMillis();
      if (this.bool("double-press") && var1 - this.lastPress > 1000L) {
         this.lastPress = var1;
         if (this.bool("notify")) {
            RiptideClientMessaging.sendPrefixed("§ePress again to run the panic sequence.");
         }
      } else {
         this.lastPress = 0L;
         this.queueCommands();
      }
   }

   private void queueCommands() {
      String var1 = this.choice("mode");
      String var2 = this.text("target").trim();
      if (!"Sell".equals(var1)) {
         String var3 = this.text("order-command").trim();
         if (!var3.isEmpty()) {
            if (var3.contains("{target}") && var2.isEmpty()) {
               RiptideClientMessaging.sendPrefixed("§cSet a name in Send To first.");
            } else {
               this.pending.add(var3.replace("{target}", var2));
            }
         }
      }

      if (!"Order".equals(var1)) {
         String var8 = this.text("sell-command").trim();
         if (!var8.isEmpty()) {
            this.pending.add(var8);
         }
      }

      for (String var6 : this.text("extra").split(";")) {
         String var7 = var6.trim();
         if (!var7.isEmpty()) {
            this.pending.add(var7);
         }
      }

      if (this.pending.isEmpty()) {
         RiptideClientMessaging.sendPrefixed("§cNothing to run — check the command settings.");
      } else if (this.bool("notify")) {
         RiptideClientMessaging.sendPrefixed("§ePanic: running §f" + this.pending.size() + "§e command(s).");
      }

      this.cooldown = 0;
   }
}
