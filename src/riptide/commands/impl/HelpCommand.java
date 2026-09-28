package riptide.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Locale;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.util.RiptideClientMessaging;

public class HelpCommand extends Command {
   public HelpCommand() {
      super("help", "Show usage info for a command (or list all).");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         listAll();
         return 1;
      });
      root.then(RequiredArgumentBuilder.argument("command", StringArgumentType.word()).suggests((ctx, builder) -> {
         String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

         for (Command command : RiptideCommands.all()) {
            if (command.name().toLowerCase(Locale.ROOT).startsWith(remaining)) {
               builder.suggest(command.name());
            }

            for (String alias : command.aliases()) {
               if (alias != null && alias.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                  builder.suggest(alias);
               }
            }
         }

         return builder.buildFuture();
      }).executes(ctx -> {
         String name = StringArgumentType.getString(ctx, "command");
         Command cmd = RiptideCommands.find(name);
         if (cmd == null) {
            RiptideClientMessaging.sendPrefixed("§cNo such command: §f" + name);
            return 1;
         } else {
            String prefix = RiptideCommands.effectivePrefix();
            RiptideClientMessaging.sendPrefixed("§b" + prefix + cmd.name() + " §7" + cmd.description());
            if (cmd.aliases().length > 0) {
               RiptideClientMessaging.sendPrefixed("§7aliases: §f" + String.join(", ", cmd.aliases()));
            }

            return 1;
         }
      }));
   }

   private static void listAll() {
      String prefix = RiptideCommands.effectivePrefix();

      for (Command c : RiptideCommands.all()) {
         RiptideClientMessaging.sendPrefixed("§b" + prefix + c.name() + "§7 - " + c.description());
      }
   }
}
