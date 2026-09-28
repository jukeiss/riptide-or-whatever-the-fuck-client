package riptide.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import riptide.commands.Command;
import riptide.commands.CommandSuggest;
import riptide.commands.RiptideCommandSource;
import riptide.modules.RiptideModule;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideCompatManager;
import riptide.util.RiptideNotifications;

public class PrefixCommand extends Command {
   public PrefixCommand() {
      super("prefix", "Change the Riptide command prefix.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         RiptideClientMessaging.sendPrefixed("Current prefix: " + RiptideCompatManager.effectiveCommandPrefix());
         return 1;
      });
      root.then(LiteralArgumentBuilder.literal("reset").executes(ctx -> {
         String prefix = RiptideCompatManager.environmentDefaultCommandPrefix();
         RiptideModule.get().setCommandPrefix(prefix);
         RiptideClientMessaging.sendPrefixed("Prefix reset to " + prefix);
         return 1;
      }));
      root.then(RequiredArgumentBuilder.argument("new", StringArgumentType.word()).suggests(CommandSuggest::prefixes).executes(ctx -> {
         String requested = StringArgumentType.getString(ctx, "new");
         if (!RiptideCompatManager.COMMAND_PREFIX_CHOICES.contains(requested)) {
            RiptideClientMessaging.sendPrefixed("Prefix must be one of: . % - _ * # @ & =");
            return 1;
         } else {
            String selected = RiptideCompatManager.normalizeStoredCommandPrefix(requested);
            RiptideModule.get().setCommandPrefix(selected);
            if (!selected.equals(requested)) {
               RiptideNotifications.warning("Meteor uses '.'. Prefix kept as '%'.");
            }

            RiptideClientMessaging.sendPrefixed("Prefix set to " + selected);
            return 1;
         }
      }));
   }
}
