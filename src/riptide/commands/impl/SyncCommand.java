package riptide.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.commands.args.MacroArgumentType;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideLANSync;
import riptide.util.RiptideMacro;
import riptide.util.RiptideMacroManager;

public final class SyncCommand extends Command {
   public SyncCommand() {
      super("sync", "Synchronize a chat message, server command, or macro across the LAN session.", "lan-sync", "lansync");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(context -> usage());
      root.then(
         ((LiteralArgumentBuilder)LiteralArgumentBuilder.literal("send").executes(context -> {
               RiptideClientMessaging.sendPrefixed("§eUsage: §f" + RiptideCommands.effectivePrefix() + "sync send <message or /command>");
               return 1;
            }))
            .then(
               RequiredArgumentBuilder.argument("message", StringArgumentType.greedyString())
                  .executes(context -> send(StringArgumentType.getString(context, "message")))
            )
      );
      root.then(((LiteralArgumentBuilder)LiteralArgumentBuilder.literal("macro").executes(context -> {
         RiptideClientMessaging.sendPrefixed("§eUsage: §f" + RiptideCommands.effectivePrefix() + "sync macro <name>");
         return 1;
      })).then(RequiredArgumentBuilder.argument("name", MacroArgumentType.macroName()).executes(context -> macro(MacroArgumentType.get(context, "name")))));
   }

   private static int usage() {
      String prefix = RiptideCommands.effectivePrefix();
      RiptideClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "sync send <message or /command>");
      RiptideClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "sync macro <name>");
      return 1;
   }

   private static int send(String message) {
      RiptideLANSync sync = RiptideLANSync.getInstance();
      if (!sync.isInSession()) {
         RiptideClientMessaging.sendPrefixed("§cJoin a LAN Sync session first.");
         return 1;
      } else {
         sync.sendChatMessage(message);
         return 1;
      }
   }

   private static int macro(String name) {
      RiptideLANSync sync = RiptideLANSync.getInstance();
      if (!sync.isInSession()) {
         RiptideClientMessaging.sendPrefixed("§cJoin a LAN Sync session first.");
         return 1;
      } else {
         RiptideMacro macro = RiptideMacroManager.get().get(name);
         if (macro == null) {
            RiptideClientMessaging.sendPrefixed("§cMacro not found: §f" + name);
            return 1;
         } else {
            sync.executeMacroSynchronized(macro.name);
            return 1;
         }
      }
   }
}
