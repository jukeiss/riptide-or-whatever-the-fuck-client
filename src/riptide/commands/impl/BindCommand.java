package riptide.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.commands.args.KeyArgumentType;
import riptide.modules.RiptideModule;
import riptide.util.RiptideClientMessaging;

public class BindCommand extends Command {
   public BindCommand() {
      super("bind", "Bind a key to a command. Usage: bind <key> <command…> | bind clear <key>");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         String prefix = RiptideCommands.effectivePrefix();
         RiptideClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "bind <key> <command>");
         RiptideClientMessaging.sendPrefixed("§7Example: §f" + prefix + "bind G " + prefix + "macro myMacro");
         return 1;
      });
      root.then(LiteralArgumentBuilder.literal("clear").then(RequiredArgumentBuilder.argument("key", KeyArgumentType.key()).executes(ctx -> {
         int key = KeyArgumentType.get(ctx, "key");
         RiptideModule.get().clearCommandBind(key);
         RiptideClientMessaging.sendPrefixed("§eCleared bind for §f" + KeyArgumentType.keyName(key));
         return 1;
      })));
      root.then(
         RequiredArgumentBuilder.argument("key", KeyArgumentType.key())
            .then(RequiredArgumentBuilder.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
               int key = KeyArgumentType.get(ctx, "key");
               String cmd = StringArgumentType.getString(ctx, "command");
               RiptideModule.get().setCommandBind(key, cmd);
               RiptideClientMessaging.sendPrefixed("§aBound §f" + KeyArgumentType.keyName(key) + "§a → §f" + cmd);
               return 1;
            }))
      );
   }
}
