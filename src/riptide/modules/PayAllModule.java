package riptide.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import riptide.api.module.BoolSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.StringListSetting;
import riptide.api.module.StringSetting;
import riptide.util.RiptideBackgroundTasks;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptidePlayerProbe;
import riptide.util.macro.PayAction;

public final class PayAllModule extends Module {
   private volatile int runGeneration;

   public PayAllModule() {
      super("pay-all", "PayAll", ModuleCategory.MISC, "Pay every listed player.");
      this.add(new StringListSetting("players", "Players", "").playerRankPicker().build());
      this.add(new BoolSetting("probe", "Probe Hidden", true).description("Include hidden players.").build());
      this.add(new StringSetting("amount", "Amount", "1000").description("Supports K, M, B.").build());
      this.add(new BoolSetting("divide", "Split Total", false).description("Split across players.").build());
      this.add(new IntSetting("delay", "Delay (ms)", 1000, 0, 10000, 100).description("Gap between payments.").build());
      this.add(new StringSetting("command", "Command", "/pay <player> <amount>").description("<player> and <amount>.").build());
      this.add(new StringSetting("confirm-macro", "Confirm Macro", "").macroPicker().description("Run after every pay.").build());
   }

   @Override
   public void onEnable() {
      int generation = ++this.runGeneration;
      RiptideBackgroundTasks.runTracked("pay-all", () -> this.runPayRound(generation));
   }

   @Override
   public void onDisable() {
      this.runGeneration++;
   }

   @Override
   public void onGameLeft() {
      if (this.isEnabled()) {
         this.setEnabled(false);
      }
   }

   private void runPayRound(int generation) {
      try {
         List<String> targets = new ArrayList<>();

         for (String player : this.list("players")) {
            if (player != null) {
               String trimmed = player.trim();
               if (!trimmed.isEmpty() && !targets.contains(trimmed)) {
                  targets.add(trimmed);
               }
            }
         }

         if (MC != null && MC.getConnection() != null) {
            boolean everyone = targets.isEmpty();
            if (everyone) {
               if (this.bool("probe")) {
                  this.chat("§7Pay All: scanning players...");
               }

               targets.addAll(RiptidePlayerProbe.everyone(MC, this.bool("probe"), () -> this.cancelled(generation)));
               if (targets.isEmpty()) {
                  this.chat("§cPay All: no players listed or visible.");
                  return;
               }
            }

            long amount = Math.max(0L, PayAction.parseAmount(this.text("amount")));
            long[] divided = this.bool("divide") ? PayAction.distribute(amount, targets.size()) : null;
            int sent = 0;

            for (int i = 0; i < targets.size(); i++) {
               if (this.cancelled(generation)) {
                  return;
               }

               String playerx = targets.get(i);
               long perAmount = divided != null ? divided[i] : amount;
               if (perAmount > 0L) {
                  if (sent > 0 && this.integer("delay") > 0) {
                     Thread.sleep(this.integer("delay"));
                  }

                  if (this.cancelled(generation)) {
                     return;
                  }

                  String command = this.commandFor(playerx, perAmount);
                  if (!command.isEmpty()) {
                     sent++;
                     int total = targets.size();
                     String label = PayAction.formatAmount(perAmount);
                     CountDownLatch sendLatch = new CountDownLatch(1);
                     MC.execute(() -> {
                        try {
                           this.sendPayment(player, command, label, sent, total);
                        } finally {
                           sendLatch.countDown();
                        }
                     });
                     sendLatch.await(2000L, TimeUnit.MILLISECONDS);
                     if (this.cancelled(generation)) {
                        return;
                     }

                     String confirm = this.text("confirm-macro");
                     if (confirm != null && !confirm.isBlank()) {
                        PayAction.runConfirmMacro(confirm, () -> this.cancelled(generation));
                     }
                  }
               }
            }

            int finalSent = sent;
            MC.execute(() -> this.chat("§aPay All done: §f" + finalSent + " payment" + (finalSent == 1 ? "" : "s") + (everyone ? " §7(everyone)" : "") + "."));
         }
      } catch (InterruptedException var22) {
         Thread.currentThread().interrupt();
      } finally {
         this.finish(generation);
      }
   }

   private void sendPayment(String player, String command, String label, int index, int total) {
      try {
         if (PackHideState.isHardLocked() || MC == null || MC.getConnection() == null) {
            return;
         }

         if (command.startsWith("/") && command.length() > 1) {
            MC.getConnection().sendCommand(command.substring(1));
         } else {
            MC.getConnection().sendChat(command);
         }

         this.chat("§aPaid §f" + player + " §e" + label + " §7(" + index + "/" + total + ")");
      } catch (Exception var7) {
         this.chat("§cPay failed: " + var7.getMessage());
      }
   }

   private String commandFor(String player, long amount) {
      String template = this.text("command");
      if (template == null || template.isBlank()) {
         template = "/pay <player> <amount>";
      }

      String value = String.valueOf(amount);
      return template.replace("<player>", player).replace("{player}", player).replace("<amount>", value).replace("{amount}", value).trim();
   }

   private boolean cancelled(int generation) {
      return generation != this.runGeneration || !this.isEnabled();
   }

   private void finish(int generation) {
      if (MC == null) {
         if (generation == this.runGeneration) {
            this.setEnabled(false);
         }
      } else {
         MC.execute(() -> {
            if (generation == this.runGeneration && this.isEnabled()) {
               this.setEnabled(false);
            }
         });
      }
   }

   private void chat(String message) {
      RiptideClientMessaging.sendPrefixed(message);
   }
}
