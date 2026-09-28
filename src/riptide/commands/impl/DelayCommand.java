package riptide.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import riptide.commands.Command;
import riptide.commands.CommandSuggest;
import riptide.commands.RiptideCommandSource;
import riptide.modules.RiptideModule;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideSharedState;

public class DelayCommand extends Command {
   public DelayCommand() {
      super("delay", "Toggle the GUI packet delay queue.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> apply(null));
      root.then(
         RequiredArgumentBuilder.argument("state", StringArgumentType.word())
            .suggests(CommandSuggest::state)
            .executes(ctx -> apply(StringArgumentType.getString(ctx, "state")))
      );
   }

   private static int apply(String state) {
      boolean current = RiptideSharedState.get().shouldDelayGuiPackets();
      if (state != null) {
         String lower = state.toLowerCase();
         if (!lower.equals("on")
            && !lower.equals("enable")
            && !lower.equals("true")
            && !lower.equals("1")
            && !lower.equals("off")
            && !lower.equals("disable")
            && !lower.equals("false")
            && !lower.equals("0")
            && !lower.equals("toggle")) {
            RiptideClientMessaging.sendPrefixed("Unknown delay state: " + state);
            RiptideClientMessaging.sendPrefixed("Use on, off, or toggle.");
            return 1;
         }
      }

      boolean var10000;
      if (state == null) {
         var10000 = !current;
      } else {
         String module = state.toLowerCase();
         switch (module) {
            case "on":
            case "enable":
            case "true":
            case "1":
               var10000 = true;
               break;
            case "off":
            case "disable":
            case "false":
            case "0":
               var10000 = false;
               break;
            case "toggle":
               var10000 = !current;
               break;
            default:
               var10000 = !current;
         }
      }

      boolean target = var10000;
      RiptideModule module = RiptideModule.get();
      int flushed = module.applyDelayGuiPacketsUiBehavior(target);
      module.notifyDelayPacketsUiResult(target, flushed);
      return 1;
   }
}
