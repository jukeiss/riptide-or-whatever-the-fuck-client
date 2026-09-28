package riptide.commands.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.commands.args.MacroArgumentType;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroManager;
import riptide.util.macro.MacroExecutor;
import riptide.util.multi.MultiManager;
import riptide.util.multi.MultiTakeoverState;

public class MacroCommand extends Command {
   private static final ConcurrentHashMap<String, AtomicBoolean> RUNNING_LOOPS = new ConcurrentHashMap<>();

   public MacroCommand() {
      super("macro", "Run a macro, optionally N times with a delay between starts.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         String prefix = RiptideCommands.effectivePrefix();
         RiptideClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "macro <name> [times] [delayTicks]");
         RiptideClientMessaging.sendPrefixed("§7Also: §f" + prefix + "macro stop [name] §7or §f" + prefix + "macro clear");
         return 1;
      });
      root.then(
         ((LiteralArgumentBuilder)LiteralArgumentBuilder.literal("stop")
               .executes(
                  ctx -> {
                     String pov = MultiTakeoverState.activeAccountId();
                     if (pov != null) {
                        stopPovLoops(pov);
                        MultiManager mgr = MultiManager.getIfInitialized();
                        MultiManager.BroadcastResult result = mgr == null
                           ? new MultiManager.BroadcastResult(0, 1, 0, List.of("Multi unavailable"))
                           : mgr.stopMacroOnScope(Set.of(pov));
                        RiptideClientMessaging.sendPrefixed("§ePOV macro stop: §f" + result.summary());
                        return 1;
                     } else {
                        stopAllLoops();
                        RiptideMacroManager.get().stopMacro();
                        RiptideClientMessaging.sendPrefixed("§eStopped all macros.");
                        return 1;
                     }
                  }
               ))
            .then(
               RequiredArgumentBuilder.argument("name", MacroArgumentType.macroName())
                  .executes(
                     ctx -> {
                        String name = MacroArgumentType.get(ctx, "name");
                        String pov = MultiTakeoverState.activeAccountId();
                        if (pov != null) {
                           AtomicBoolean flag = RUNNING_LOOPS.remove(povLoopKey(pov, name));
                           if (flag != null) {
                              flag.set(false);
                           }

                           MultiManager mgr = MultiManager.getIfInitialized();
                           MultiManager.BroadcastResult result = mgr == null
                              ? new MultiManager.BroadcastResult(0, 1, 0, List.of("Multi unavailable"))
                              : mgr.stopMacroOnScope(Set.of(pov));
                           RiptideClientMessaging.sendPrefixed("§eStopped POV macro §f" + name + "§e: " + result.summary());
                           return 1;
                        } else {
                           AtomicBoolean flag = RUNNING_LOOPS.remove(name.toLowerCase());
                           if (flag != null) {
                              flag.set(false);
                           }

                           MacroExecutor.stopMacro(name);
                           RiptideClientMessaging.sendPrefixed("§eStopped: §f" + name);
                           return 1;
                        }
                     }
                  )
            )
      );
      root.then(LiteralArgumentBuilder.literal("clear").executes(ctx -> {
         String pov = MultiTakeoverState.activeAccountId();
         if (pov != null) {
            stopPovLoops(pov);
            RiptideClientMessaging.sendPrefixed("§eCleared POV macro loops for §f" + pov + "§e.");
         } else {
            stopAllLoops();
            RiptideClientMessaging.sendPrefixed("§eCleared macro loops.");
         }

         return 1;
      }));
      root.then(
         ((RequiredArgumentBuilder)RequiredArgumentBuilder.argument("name", MacroArgumentType.macroName())
               .executes(ctx -> startLoop(MacroArgumentType.get(ctx, "name"), 1, 0)))
            .then(
               ((RequiredArgumentBuilder)RequiredArgumentBuilder.argument("times", IntegerArgumentType.integer(1, 100000))
                     .executes(ctx -> startLoop(MacroArgumentType.get(ctx, "name"), IntegerArgumentType.getInteger(ctx, "times"), 0)))
                  .then(
                     RequiredArgumentBuilder.argument("delayTicks", IntegerArgumentType.integer(0, 72000))
                        .executes(
                           ctx -> startLoop(
                              MacroArgumentType.get(ctx, "name"),
                              IntegerArgumentType.getInteger(ctx, "times"),
                              IntegerArgumentType.getInteger(ctx, "delayTicks")
                           )
                        )
                  )
            )
      );
   }

   private static int startLoop(String name, int times, int delayTicks) {
      RiptideMacro macro = RiptideMacroManager.get().get(name);
      if (macro == null) {
         RiptideClientMessaging.sendPrefixed("§cMacro not found: §f" + name);
         return 1;
      } else {
         String pov = MultiTakeoverState.activeAccountId();
         if (pov != null) {
            return startPovLoop(name, macro, times, delayTicks, pov);
         } else if (times <= 1 && delayTicks <= 0) {
            RiptideMacroManager.get().executeMacro(name);
            return 1;
         } else {
            AtomicBoolean flag = new AtomicBoolean(true);
            RUNNING_LOOPS.put(name.toLowerCase(), flag);
            long sleepMs = Math.max(0L, delayTicks * 50L);
            int totalTimes = Math.max(1, times);
            Thread t = new Thread(() -> {
               try {
                  for (int i = 0; i < totalTimes && flag.get(); i++) {
                     RiptideMacroManager.get().executeMacro(name);
                     long start = System.nanoTime();
                     long waitTimeoutNanos = 86400000000000L;

                     while (MacroExecutor.isMacroRunning(name) && flag.get() && System.nanoTime() - start < waitTimeoutNanos) {
                        Thread.sleep(50L);
                     }

                     if (!flag.get()) {
                        break;
                     }

                     if (i + 1 < totalTimes && sleepMs > 0L) {
                        Thread.sleep(sleepMs);
                     }
                  }
               } catch (InterruptedException var13) {
                  Thread.currentThread().interrupt();
               } finally {
                  RUNNING_LOOPS.remove(name.toLowerCase(), flag);
               }
            }, "MacroLoop-" + name);
            t.setDaemon(true);
            t.start();
            RiptideClientMessaging.sendPrefixed("§aQueued " + totalTimes + "x §f" + name + "§a (delay " + delayTicks + "t)");
            return 1;
         }
      }
   }

   private static int startPovLoop(String name, RiptideMacro macro, int times, int delayTicks, String accountId) {
      MultiManager mgr = MultiManager.getIfInitialized();
      if (mgr == null) {
         RiptideClientMessaging.sendPrefixed("§cMulti is unavailable.");
         return 1;
      } else {
         Set<String> scope = Set.of(accountId);
         if (times <= 1 && delayTicks <= 0) {
            MultiManager.BroadcastResult result = mgr.runMacroDirect(macro, scope);
            RiptideClientMessaging.sendPrefixed((result.sent() > 0 ? "§a" : "§c") + "POV macro §f" + name + "§a: " + result.summary());
            return 1;
         } else {
            String key = povLoopKey(accountId, name);
            AtomicBoolean flag = new AtomicBoolean(true);
            AtomicBoolean previous = RUNNING_LOOPS.put(key, flag);
            if (previous != null) {
               previous.set(false);
            }

            long sleepMs = Math.max(0L, delayTicks * 50L);
            int totalTimes = Math.max(1, times);
            Thread thread = new Thread(() -> {
               try {
                  for (int i = 0; i < totalTimes && flag.get(); i++) {
                     mgr.runMacroDirect(macro, scope);
                     long start = System.nanoTime();
                     long timeout = 86400000000000L;

                     while (mgr.isMacroPlayingOnScope(name, scope) && flag.get() && System.nanoTime() - start < timeout) {
                        Thread.sleep(50L);
                     }

                     if (!flag.get()) {
                        break;
                     }

                     if (i + 1 < totalTimes && sleepMs > 0L) {
                        Thread.sleep(sleepMs);
                     }
                  }
               } catch (InterruptedException var17) {
                  Thread.currentThread().interrupt();
               } finally {
                  RUNNING_LOOPS.remove(key, flag);
               }
            }, "PovMacroLoop-" + name);
            thread.setDaemon(true);
            thread.start();
            RiptideClientMessaging.sendPrefixed("§aQueued POV §f" + totalTimes + "x " + name + "§a for §f" + accountId + "§a.");
            return 1;
         }
      }
   }

   private static String povLoopKey(String accountId, String name) {
      return "pov:" + accountId.toLowerCase(Locale.ROOT) + ":" + name.toLowerCase(Locale.ROOT);
   }

   private static void stopPovLoops(String accountId) {
      String prefix = "pov:" + accountId.toLowerCase(Locale.ROOT) + ":";
      RUNNING_LOOPS.forEach((key, flag) -> {
         if (key.startsWith(prefix) && RUNNING_LOOPS.remove(key, flag)) {
            flag.set(false);
         }
      });
   }

   private static void stopAllLoops() {
      for (AtomicBoolean flag : RUNNING_LOOPS.values()) {
         flag.set(false);
      }

      RUNNING_LOOPS.clear();
   }
}
