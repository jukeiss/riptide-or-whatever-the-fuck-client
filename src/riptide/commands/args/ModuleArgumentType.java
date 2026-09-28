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
import riptide.modules.ModuleRegistry;

public class ModuleArgumentType implements ArgumentType<String> {
   private static volatile int cachedRevision = Integer.MIN_VALUE;
   private static volatile List<ModuleArgumentType.Candidate> cachedCandidates = List.of();

   public static ModuleArgumentType moduleName() {
      return new ModuleArgumentType();
   }

   public static String get(CommandContext<RiptideCommandSource> ctx, String name) {
      return (String)ctx.getArgument(name, String.class);
   }

   public String parse(StringReader reader) throws CommandSyntaxException {
      return reader.readUnquotedString();
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

      for (ModuleArgumentType.Candidate candidate : candidates()) {
         if (candidate.lower().startsWith(remaining)) {
            builder.suggest(candidate.value());
         }
      }

      if ("all".startsWith(remaining)) {
         builder.suggest("all");
      }

      if ("hud".startsWith(remaining)) {
         builder.suggest("hud");
      }

      return builder.buildFuture();
   }

   private static List<ModuleArgumentType.Candidate> candidates() {
      int revision = ModuleRegistry.revision();
      List<ModuleArgumentType.Candidate> hit = cachedCandidates;
      if (cachedRevision == revision) {
         return hit;
      } else {
         List<ModuleArgumentType.Candidate> rebuilt = new ArrayList<>();

         for (String name : ModuleRegistry.names()) {
            if (name != null) {
               String commandName = name.replace(' ', '-');
               rebuilt.add(new ModuleArgumentType.Candidate(commandName, commandName.toLowerCase(Locale.ROOT)));
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
