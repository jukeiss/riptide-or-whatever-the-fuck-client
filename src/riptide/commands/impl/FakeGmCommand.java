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

public class FakeGmCommand extends Command {
   public FakeGmCommand() {
      super("fakegm", "Set a fake client-side game mode.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         RiptideClientMessaging.sendPrefixed("§eUsage: " + RiptideCommands.effectivePrefix() + "fakegm <survival|creative|adventure|spectator|reset>");
         RiptideClientMessaging.sendPrefixed("§7Real change: " + RiptideCommands.effectivePrefix() + "gamemode");
         return 1;
      });
      root.then(RequiredArgumentBuilder.argument("mode", StringArgumentType.word()).suggests(CommandSuggest::gamemodes).executes(ctx -> {
         String mode = StringArgumentType.getString(ctx, "mode").toLowerCase(Locale.ROOT);
         RiptideFakeGamemode.Result result;
         if (!"reset".equals(mode) && !"r".equals(mode)) {
            GameType resolved = RiptideFakeGamemode.parseMode(mode);
            if (resolved == null) {
               RiptideClientMessaging.sendPrefixed("§cUnknown mode: §f" + mode);
               return 1;
            }

            result = RiptideFakeGamemode.apply(resolved);
         } else {
            result = RiptideFakeGamemode.reset();
         }

         RiptideClientMessaging.sendPrefixed((result.success() ? "§a" : "§c") + result.message());
         return 1;
      }));
   }
}
