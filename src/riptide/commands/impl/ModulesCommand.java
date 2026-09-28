package riptide.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.modules.ModuleRegistry;
import riptide.util.RiptideClientMessaging;

public class ModulesCommand extends Command {
   public ModulesCommand() {
      super("modules", "List installed modules.", "features");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         List<String> names = ModuleRegistry.names();
         RiptideClientMessaging.sendPrefixed("§e" + names.size() + " modules:");
         StringBuilder line = new StringBuilder();

         for (String n : names) {
            if (line.length() > 0) {
               line.append("§7, ");
            }

            line.append("§f").append(n);
            if (line.length() > 200) {
               RiptideClientMessaging.sendPrefixed(line.toString());
               line.setLength(0);
            }
         }

         if (line.length() > 0) {
            RiptideClientMessaging.sendPrefixed(line.toString());
         }

         return 1;
      });
   }
}
