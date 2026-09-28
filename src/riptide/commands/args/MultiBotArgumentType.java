package riptide.commands.args;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import riptide.commands.RiptideCommandSource;
import riptide.util.multi.MultiManager;

public class MultiBotArgumentType implements ArgumentType<String> {
   public static MultiBotArgumentType botName() {
      return new MultiBotArgumentType();
   }

   public static String get(CommandContext<RiptideCommandSource> ctx, String name) {
      return (String)ctx.getArgument(name, String.class);
   }

   public String parse(StringReader reader) throws CommandSyntaxException {
      return reader.canRead() && reader.peek() == '"' ? reader.readQuotedString() : reader.readUnquotedString();
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

      for (MultiManager.BotHandle bot : MultiManager.get().liveBots()) {
         String name = bot.username();
         if (name != null && !name.isBlank() && name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
            builder.suggest(name.contains(" ") ? "\"" + name + "\"" : name);
         }
      }

      return builder.buildFuture();
   }
}
