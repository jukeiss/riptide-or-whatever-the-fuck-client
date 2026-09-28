package riptide.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.util.RiptideClientMessaging;
import riptide.util.mm.MatchmakingManager;

public class IrcCommand extends Command {
   public IrcCommand() {
      super("irc", "Send a message to the Matchmaking lobby chat.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         RiptideClientMessaging.sendPrefixed("§eUsage: " + RiptideCommands.effectivePrefix() + "irc <message>");
         return 1;
      });
      root.then(RequiredArgumentBuilder.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
         String msg = StringArgumentType.getString(ctx, "message");
         MatchmakingManager mm = MatchmakingManager.get();
         if (!mm.inLobby()) {
            RiptideClientMessaging.sendPrefixed("§cNot in a lobby. Open Matchmaking and join or create one first.");
            return 1;
         } else {
            mm.sendChat(msg);
            return 1;
         }
      }));
   }
}
