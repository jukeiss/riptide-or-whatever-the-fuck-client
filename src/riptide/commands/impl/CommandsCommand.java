package riptide.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.util.RiptideClientMessaging;

public class CommandsCommand extends Command {
   public CommandsCommand() {
      super("commands", "List all available commands.", "cmds");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         String prefix = RiptideCommands.effectivePrefix();
         RiptideClientMessaging.sendPrefixed("§e" + RiptideCommands.all().size() + " commands (prefix §f" + prefix + "§e):");
         StringBuilder line = new StringBuilder();

         for (Command c : RiptideCommands.all()) {
            if (line.length() > 0) {
               line.append("§7, ");
            }

            line.append("§f").append(c.name());
            if (line.length() > 200) {
               RiptideClientMessaging.sendPrefixed(line.toString());
               line.setLength(0);
            }
         }

         if (line.length() > 0) {
            RiptideClientMessaging.sendPrefixed(line.toString());
         }

         RiptideClientMessaging.sendPrefixed("§7Type §f" + prefix + "help <command>§7 for details.");
         return 1;
      });
   }
}
