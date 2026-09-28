package riptide.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.modules.TeamsModule;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptidePlayerScanner;

public final class FriendCommand extends Command {
   public FriendCommand() {
      super("friend", "Manage the friends/teams list. add <name>, remove <name>, clear.", "team");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> listFriends());
      root.then(
         LiteralArgumentBuilder.literal("add")
            .then(
               RequiredArgumentBuilder.argument("name", StringArgumentType.word())
                  .suggests(FriendCommand::suggestServerPlayers)
                  .executes(ctx -> TeamsModule.addFriend(StringArgumentType.getString(ctx, "name")) ? 1 : 0)
            )
      );
      root.then(
         LiteralArgumentBuilder.literal("remove")
            .then(
               RequiredArgumentBuilder.argument("name", StringArgumentType.word())
                  .suggests(FriendCommand::suggestFriends)
                  .executes(ctx -> TeamsModule.removeFriend(StringArgumentType.getString(ctx, "name")) ? 1 : 0)
            )
      );
      root.then(LiteralArgumentBuilder.literal("clear").executes(ctx -> TeamsModule.clearFriends() ? 1 : 0));
   }

   private static int listFriends() {
      List<String> friends = TeamsModule.storedFriendNames();
      if (friends.isEmpty()) {
         RiptideClientMessaging.sendPrefixed("§7Friend list is empty.");
         return 1;
      } else {
         RiptideClientMessaging.sendPrefixed("§7Friends (§f" + friends.size() + "§7): §f" + String.join("§7, §f", friends));
         return 1;
      }
   }

   private static CompletableFuture<Suggestions> suggestServerPlayers(CommandContext<RiptideCommandSource> ctx, SuggestionsBuilder builder) {
      String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

      for (RiptidePlayerScanner.ScannedPlayer player : RiptidePlayerScanner.scan(Minecraft.getInstance())) {
         String name = player.name();
         if (name != null && name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
            builder.suggest(name);
         }
      }

      return builder.buildFuture();
   }

   private static CompletableFuture<Suggestions> suggestFriends(CommandContext<RiptideCommandSource> ctx, SuggestionsBuilder builder) {
      String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

      for (String name : TeamsModule.storedFriendNames()) {
         if (name != null && name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
            builder.suggest(name);
         }
      }

      return builder.buildFuture();
   }
}
