package riptide.commands.args;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import riptide.commands.RiptideCommandSource;

public final class VisibleItemNameArgumentType implements ArgumentType<String> {
   private static AbstractContainerMenu cachedMenu;
   private static int cachedMenuRevision = Integer.MIN_VALUE;
   private static List<VisibleItemNameArgumentType.Candidate> cachedNames = List.of();

   private VisibleItemNameArgumentType() {
   }

   public static VisibleItemNameArgumentType itemName() {
      return new VisibleItemNameArgumentType();
   }

   public static String get(CommandContext<RiptideCommandSource> context, String name) {
      return (String)context.getArgument(name, String.class);
   }

   public String parse(StringReader reader) throws CommandSyntaxException {
      return reader.readString();
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
      if (remaining.startsWith("\"")) {
         remaining = remaining.substring(1);
      }

      Minecraft mc = Minecraft.getInstance();

      for (VisibleItemNameArgumentType.Candidate candidate : names(mc)) {
         if (candidate.lower().startsWith(remaining)) {
            builder.suggest(StringArgumentType.escapeIfRequired(candidate.value()));
         }
      }

      return builder.buildFuture();
   }

   private static List<VisibleItemNameArgumentType.Candidate> names(Minecraft mc) {
      AbstractContainerMenu menu = mc.player == null ? null : mc.player.containerMenu;
      int revision = menu == null ? -1 : menu.getStateId();
      if (menu == cachedMenu && revision == cachedMenuRevision) {
         return cachedNames;
      } else {
         Set<String> names = new LinkedHashSet<>();
         if (menu != null) {
            for (int slot = 0; slot < menu.slots.size(); slot++) {
               ItemStack stack = ((Slot)menu.slots.get(slot)).getItem();
               if (!stack.isEmpty()) {
                  String displayName = collapseWhitespace(stack.getHoverName().getString());
                  if (!displayName.isEmpty()) {
                     names.add(displayName);
                  }
               }
            }
         }

         List<VisibleItemNameArgumentType.Candidate> rebuilt = new ArrayList<>(names.size());

         for (String name : names) {
            rebuilt.add(new VisibleItemNameArgumentType.Candidate(name, name.toLowerCase(Locale.ROOT)));
         }

         cachedMenu = menu;
         cachedMenuRevision = revision;
         cachedNames = List.copyOf(rebuilt);
         return cachedNames;
      }
   }

   private static String collapseWhitespace(String input) {
      if (input != null && !input.isBlank()) {
         StringBuilder out = new StringBuilder(input.length());
         boolean pendingSpace = false;

         for (int index = 0; index < input.length(); index++) {
            char chr = input.charAt(index);
            if (Character.isWhitespace(chr)) {
               pendingSpace = out.length() > 0;
            } else {
               if (pendingSpace) {
                  out.append(' ');
               }

               out.append(chr);
               pendingSpace = false;
            }
         }

         return out.toString();
      } else {
         return "";
      }
   }

   private record Candidate(String value, String lower) {
   }
}
