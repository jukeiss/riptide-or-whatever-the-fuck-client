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
import net.minecraft.network.protocol.Packet;
import riptide.commands.RiptideCommandSource;
import riptide.util.RiptidePacketRegistry;

public class PacketClassArgumentType implements ArgumentType<String> {
   private static volatile List<PacketClassArgumentType.Candidate> cachedCandidates;

   public static PacketClassArgumentType packetClass() {
      return new PacketClassArgumentType();
   }

   public static String get(CommandContext<RiptideCommandSource> ctx, String name) {
      return (String)ctx.getArgument(name, String.class);
   }

   public String parse(StringReader reader) throws CommandSyntaxException {
      return reader.readUnquotedString();
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

      for (PacketClassArgumentType.Candidate candidate : candidates()) {
         if (candidate.lower().contains(remaining)) {
            builder.suggest(candidate.value());
         }
      }

      return builder.buildFuture();
   }

   private static List<PacketClassArgumentType.Candidate> candidates() {
      List<PacketClassArgumentType.Candidate> hit = cachedCandidates;
      if (hit != null) {
         return hit;
      } else {
         List<PacketClassArgumentType.Candidate> rebuilt = new ArrayList<>();

         for (Class<? extends Packet<?>> cls : RiptidePacketRegistry.getC2SPackets()) {
            String name = RiptidePacketRegistry.getName(cls);
            if (name != null) {
               rebuilt.add(new PacketClassArgumentType.Candidate(name, name.toLowerCase(Locale.ROOT)));
            }
         }

         hit = List.copyOf(rebuilt);
         cachedCandidates = hit;
         return hit;
      }
   }

   private record Candidate(String value, String lower) {
   }
}
