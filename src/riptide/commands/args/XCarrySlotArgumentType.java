package riptide.commands.args;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.chat.Component;
import riptide.commands.RiptideCommandSource;

public class XCarrySlotArgumentType implements ArgumentType<Integer> {
   public static final int CURSOR = Integer.MIN_VALUE;
   private static final Map<String, Integer> TOKENS = new LinkedHashMap<>();
   private static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(
      Component.literal("Unknown XCarry slot — use craft1..craft5 / helmet / chestplate / leggings / boots / offhand / cursor.")
   );

   public static XCarrySlotArgumentType slot() {
      return new XCarrySlotArgumentType();
   }

   public static int get(CommandContext<RiptideCommandSource> ctx, String name) {
      return (Integer)ctx.getArgument(name, Integer.class);
   }

   public static String displayName(int slotId) {
      for (Entry<String, Integer> e : TOKENS.entrySet()) {
         if (e.getValue() == slotId) {
            return e.getKey();
         }
      }

      return "slot " + slotId;
   }

   public Integer parse(StringReader reader) throws CommandSyntaxException {
      String raw = reader.readUnquotedString().toLowerCase(Locale.ROOT);
      Integer id = TOKENS.get(raw);
      if (id == null) {
         throw UNKNOWN.create();
      } else {
         return id;
      }
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

      for (String name : TOKENS.keySet()) {
         if (name.startsWith(remaining)) {
            builder.suggest(name);
         }
      }

      return builder.buildFuture();
   }

   static {
      TOKENS.put("craft1", 1);
      TOKENS.put("craft2", 2);
      TOKENS.put("craft3", 3);
      TOKENS.put("craft4", 4);
      TOKENS.put("craft5", 0);
      TOKENS.put("crafting5", 0);
      TOKENS.put("result", 0);
      TOKENS.put("helmet", 5);
      TOKENS.put("chestplate", 6);
      TOKENS.put("leggings", 7);
      TOKENS.put("boots", 8);
      TOKENS.put("offhand", 45);
      TOKENS.put("cursor", Integer.MIN_VALUE);
   }
}
