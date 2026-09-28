package riptide.commands.args;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import riptide.commands.RiptideCommandSource;
import riptide.util.multi.MultiProfile;
import riptide.util.multi.MultiProfileManager;

public class MultiProfileArgumentType implements ArgumentType<String> {
   private static volatile long cachedRevision = Long.MIN_VALUE;
   private static volatile List<MultiProfileArgumentType.Candidate> cachedCandidates = List.of();

   public static MultiProfileArgumentType profileName() {
      return new MultiProfileArgumentType();
   }

   public static String get(CommandContext<RiptideCommandSource> ctx, String name) {
      return (String)ctx.getArgument(name, String.class);
   }

   public String parse(StringReader reader) throws CommandSyntaxException {
      return reader.canRead() && reader.peek() == '"' ? reader.readQuotedString() : reader.readUnquotedString();
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

      for (MultiProfileArgumentType.Candidate candidate : candidates()) {
         if (candidate.lower().startsWith(remaining)) {
            builder.suggest(candidate.value());
         }
      }

      return builder.buildFuture();
   }

   private static List<MultiProfileArgumentType.Candidate> candidates() {
      MultiProfileManager manager = MultiProfileManager.get();
      long revision = manager.revision();
      List<MultiProfileArgumentType.Candidate> hit = cachedCandidates;
      if (cachedRevision == revision) {
         return hit;
      } else {
         List<MultiProfileArgumentType.Candidate> rebuilt = new ArrayList<>();

         for (MultiProfile profile : manager) {
            if (profile != null && profile.name != null) {
               String name = profile.name;
               String value = name.contains(" ") ? "\"" + name + "\"" : name;
               rebuilt.add(new MultiProfileArgumentType.Candidate(value, name.toLowerCase(Locale.ROOT)));
            }
         }

         hit = List.copyOf(rebuilt);
         cachedCandidates = hit;
         cachedRevision = revision;
         return hit;
      }
   }

   private record Candidate(String value, String lower) {
   }
}
