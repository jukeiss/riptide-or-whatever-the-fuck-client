package riptide.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Locale;
import net.minecraft.world.level.GameType;
import riptide.commands.Command;
import riptide.commands.CommandSuggest;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideFakeGamemode;
import riptide.util.RiptideGamemode;

public class GamemodeCommand extends Command {
   public GamemodeCommand() {
      super("gamemode", "Set your real game mode (no chat command sent).");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         RiptideClientMessaging.sendPrefixed("§eUsage: " + RiptideCommands.effectivePrefix() + "gamemode <survival|creative|adventure|spectator>");
         RiptideClientMessaging.sendPrefixed("§7Client-side only: " + RiptideCommands.effectivePrefix() + "fakegm");
         return 1;
      });
      root.then(RequiredArgumentBuilder.argument("mode", StringArgumentType.word()).suggests(CommandSuggest::realGamemodes).executes(ctx -> {
         String mode = StringArgumentType.getString(ctx, "mode").toLowerCase(Locale.ROOT);
         GameType resolved = RiptideFakeGamemode.parseMode(mode);
         if (resolved != null) {
            RiptideFakeGamemode.Result result = RiptideGamemode.real(resolved);
            RiptideClientMessaging.sendPrefixed((result.success() ? "§a" : "§c") + result.message());
            return 1;
         } else {
            RiptideClientMessaging.sendPrefixed("§cUnknown mode: §f" + mode);
            if ("reset".equals(mode) || "r".equals(mode)) {
               RiptideClientMessaging.sendPrefixed("§7Reset only applies to the fake mode: §f" + RiptideCommands.effectivePrefix() + "fakegm reset");
            }

            return 1;
         }
      }));
   }
}
