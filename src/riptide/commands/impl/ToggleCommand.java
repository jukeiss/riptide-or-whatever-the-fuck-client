package riptide.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import riptide.commands.Command;
import riptide.commands.CommandSuggest;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.commands.args.ModuleArgumentType;
import riptide.modules.ModuleRegistry;
import riptide.modules.PackHideState;
import riptide.util.RiptideClientMessaging;
import riptide.util.macro.ToggleModuleAction;

public class ToggleCommand extends Command {
   public ToggleCommand() {
      super("toggle", "Toggle a module on/off.", "t");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         RiptideClientMessaging.sendPrefixed("§eUsage: §f" + RiptideCommands.effectivePrefix() + "toggle <module> [on|off|toggle]");
         return 1;
      });
      root.then(
         ((RequiredArgumentBuilder)RequiredArgumentBuilder.argument("module", ModuleArgumentType.moduleName())
               .executes(ctx -> doToggle(ModuleArgumentType.get(ctx, "module"), ToggleModuleAction.ToggleMode.TOGGLE)))
            .then(
               RequiredArgumentBuilder.argument("state", StringArgumentType.word())
                  .suggests(CommandSuggest::state)
                  .executes(
                     ctx -> {
                        String state = StringArgumentType.getString(ctx, "state").toLowerCase();
                        if (!state.equals("on")
                           && !state.equals("enable")
                           && !state.equals("true")
                           && !state.equals("1")
                           && !state.equals("off")
                           && !state.equals("disable")
                           && !state.equals("false")
                           && !state.equals("0")
                           && !state.equals("toggle")) {
                           RiptideClientMessaging.sendPrefixed("§cUnknown toggle state: §f" + state);
                           RiptideClientMessaging.sendPrefixed("§7Use §fon§7, §foff§7, or §ftoggle§7.");
                           return 1;
                        } else {
                           ToggleModuleAction.ToggleMode mode = switch (state) {
                              case "on", "enable", "true", "1" -> ToggleModuleAction.ToggleMode.ENABLE;
                              case "off", "disable", "false", "0" -> ToggleModuleAction.ToggleMode.DISABLE;
                              default -> ToggleModuleAction.ToggleMode.TOGGLE;
                           };
                           return doToggle(ModuleArgumentType.get(ctx, "module"), mode);
                        }
                     }
                  )
            )
      );
   }

   private static int doToggle(String moduleName, ToggleModuleAction.ToggleMode mode) {
      boolean ok = ModuleRegistry.toggle(moduleName, mode);
      if (ok && PackHideState.isHideModuleName(moduleName)) {
         return 1;
      } else {
         if (ok) {
            RiptideClientMessaging.sendPrefixed("§a" + mode.name().toLowerCase() + ": §f" + moduleName);
         } else {
            RiptideClientMessaging.sendPrefixed("§cModule not found: §f" + moduleName);
         }

         return 1;
      }
   }
}
