package riptide.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Map;
import java.util.Map.Entry;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.commands.args.KeyArgumentType;
import riptide.modules.RiptideModule;
import riptide.util.RiptideClientMessaging;

public class BindsCommand extends Command {
   public BindsCommand() {
      super("binds", "List command keybinds (set with `bind`).");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         Map<Integer, String> binds = RiptideModule.get().getCommandBinds();
         if (binds.isEmpty()) {
            RiptideClientMessaging.sendPrefixed("§eNo command keybinds set.");
            return 1;
         } else {
            RiptideClientMessaging.sendPrefixed("§e" + binds.size() + " command keybinds:");

            for (Entry<Integer, String> e : binds.entrySet()) {
               RiptideClientMessaging.sendPrefixed("§b" + KeyArgumentType.keyName(e.getKey()) + "§7 → §f" + e.getValue());
            }

            return 1;
         }
      });
   }
}
