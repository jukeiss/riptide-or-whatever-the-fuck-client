package riptide.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;

public class PluginsCommand extends Command {
   public PluginsCommand() {
      super("plugins", "Open the plugin scanner (alias of `server plugins`).");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         ServerCommand.openPlugins();
         return 1;
      });
   }
}
