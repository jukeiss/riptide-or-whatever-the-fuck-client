package riptide.gui.multi;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.modules.ModuleRegistry;
import riptide.util.RiptideMacroManager;

public final class MultiChatCompletion {
   private static MultiChatCompletion.CacheEntry cached;

   private MultiChatCompletion() {
   }

   public static boolean isClientCommand(String value) {
      return RiptideCommands.isRiptideCommandMessage(value);
   }

   public static MultiChatCompletion.Result clientSuggestions(String value) {
      return clientSuggestions(value, value == null ? 0 : value.length());
   }

   public static MultiChatCompletion.Result clientSuggestions(String value, int requestedCursor) {
      try {
         int prefixLen = RiptideCommands.effectivePrefix().length();
         if (value != null && value.length() >= prefixLen) {
            int cursor = Math.max(prefixLen, Math.min(requestedCursor, value.length()));
            int commandRevision = RiptideCommands.revision();
            int moduleRevision = ModuleRegistry.revision();
            long macroRevision = RiptideMacroManager.get().getRevision();
            Minecraft minecraft = Minecraft.getInstance();
            AbstractContainerMenu menu = minecraft.player == null ? null : minecraft.player.containerMenu;
            int menuRevision = menu == null ? -1 : menu.getStateId();
            MultiChatCompletion.CacheEntry hit = cached;
            if (hit == null || !hit.matches(value, cursor, commandRevision, moduleRevision, macroRevision, menu, menuRevision)) {
               StringReader reader = new StringReader(value);
               reader.setCursor(prefixLen);
               ParseResults<RiptideCommandSource> parse = RiptideCommands.dispatcher().parse(reader, RiptideCommandSource.INSTANCE);
               CompletableFuture<Suggestions> future = RiptideCommands.dispatcher().getCompletionSuggestions(parse, cursor);
               Suggestions built = future.getNow(null);
               MultiChatCompletion.Result result = built == null ? null : toResult(built);
               cached = new MultiChatCompletion.CacheEntry(value, cursor, commandRevision, moduleRevision, macroRevision, menu, menuRevision, future, result);
               return result;
            } else if (hit.result() != null) {
               return hit.result();
            } else {
               Suggestions completed = hit.future().getNow(null);
               if (completed == null) {
                  return null;
               } else {
                  MultiChatCompletion.Result result = toResult(completed);
                  cached = new MultiChatCompletion.CacheEntry(
                     value, cursor, commandRevision, moduleRevision, macroRevision, menu, menuRevision, hit.future(), result
                  );
                  return result;
               }
            }
         } else {
            return new MultiChatCompletion.Result(0, 0, List.of());
         }
      } catch (RuntimeException var17) {
         return new MultiChatCompletion.Result(0, 0, List.of());
      }
   }

   private static MultiChatCompletion.Result toResult(Suggestions built) {
      List<String> entries = new ArrayList<>(built.getList().size());

      for (Suggestion suggestion : built.getList()) {
         entries.add(suggestion.getText());
      }

      return new MultiChatCompletion.Result(built.getRange().getStart(), built.getRange().getLength(), List.copyOf(entries));
   }

   private record CacheEntry(
      String value,
      int cursor,
      int commandRevision,
      int moduleRevision,
      long macroRevision,
      AbstractContainerMenu menu,
      int menuRevision,
      CompletableFuture<Suggestions> future,
      MultiChatCompletion.Result result
   ) {
      private boolean matches(
         String nextValue,
         int nextCursor,
         int nextCommandRevision,
         int nextModuleRevision,
         long nextMacroRevision,
         AbstractContainerMenu nextMenu,
         int nextMenuRevision
      ) {
         return this.value.equals(nextValue)
            && this.cursor == nextCursor
            && this.commandRevision == nextCommandRevision
            && this.moduleRevision == nextModuleRevision
            && this.macroRevision == nextMacroRevision
            && this.menu == nextMenu
            && this.menuRevision == nextMenuRevision;
      }
   }

   public record Result(int start, int length, List<String> entries) {
   }
}
