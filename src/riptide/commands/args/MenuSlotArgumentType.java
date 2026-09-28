package riptide.commands.args;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import riptide.commands.RiptideCommandSource;
import riptide.util.RiptideInventoryHelper;

public final class MenuSlotArgumentType implements ArgumentType<Integer> {
   private static final SimpleCommandExceptionType INVALID_SLOT = new SimpleCommandExceptionType(
      Component.literal("Unknown slot. Use gui1, hotbar1, inventory1, helmet, chestplate, leggings, boots, offhand, or a visible numeric slot id.")
   );
   private static AbstractContainerMenu cachedMenu;
   private static int cachedSlotCount = -1;
   private static List<String> cachedValues = List.of();

   private MenuSlotArgumentType() {
   }

   public static MenuSlotArgumentType slot() {
      return new MenuSlotArgumentType();
   }

   public static int get(CommandContext<RiptideCommandSource> context, String name) {
      return (Integer)context.getArgument(name, Integer.class);
   }

   public Integer parse(StringReader reader) throws CommandSyntaxException {
      String token = reader.readUnquotedString().trim().toLowerCase(Locale.ROOT);
      if (token.isEmpty()) {
         throw INVALID_SLOT.create();
      } else {
         Integer named = parseNamed(token);
         if (named != null) {
            return named;
         } else {
            try {
               int numeric = Integer.parseInt(token);
               if (numeric >= 0) {
                  return numeric;
               }
            } catch (NumberFormatException var5) {
            }

            throw INVALID_SLOT.create();
         }
      }
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
      Minecraft mc = Minecraft.getInstance();

      for (String value : values(mc)) {
         if (value.toLowerCase(Locale.ROOT).startsWith(remaining)) {
            builder.suggest(value);
         }
      }

      return builder.buildFuture();
   }

   private static List<String> values(Minecraft mc) {
      AbstractContainerMenu menu = mc.player == null ? null : mc.player.containerMenu;
      int slotCount = menu == null ? 0 : menu.slots.size();
      if (menu == cachedMenu && slotCount == cachedSlotCount) {
         return cachedValues;
      } else {
         Set<String> values = new LinkedHashSet<>();
         if (menu != null) {
            for (int handlerSlot = 0; handlerSlot < menu.slots.size(); handlerSlot++) {
               int visible = RiptideInventoryHelper.toUserVisibleSlot(mc, handlerSlot);
               values.add(displayToken(visible));
               values.add(String.valueOf(visible));
            }
         } else {
            for (int slot = 1; slot <= 9; slot++) {
               values.add("hotbar" + slot);
            }
         }

         cachedMenu = menu;
         cachedSlotCount = slotCount;
         cachedValues = List.copyOf(values);
         return cachedValues;
      }
   }

   public static String displayToken(int visibleSlot) {
      if (visibleSlot >= 100) {
         return "gui" + (visibleSlot - 100 + 1);
      } else if (visibleSlot >= 0 && visibleSlot <= 8) {
         return "hotbar" + (visibleSlot + 1);
      } else if (visibleSlot >= 9 && visibleSlot <= 35) {
         return "inventory" + (visibleSlot - 8);
      } else {
         return switch (visibleSlot) {
            case 36 -> "boots";
            case 37 -> "leggings";
            case 38 -> "chestplate";
            case 39 -> "helmet";
            case 40 -> "offhand";
            default -> String.valueOf(visibleSlot);
         };
      }
   }

   private static Integer parseNamed(String token) {
      return switch (token) {
         case "boots" -> 36;
         case "leggings" -> 37;
         case "chestplate" -> 38;
         case "helmet" -> 39;
         case "offhand" -> 40;
         default -> {
            Integer gui = parseIndexed(token, "gui", 100, Integer.MAX_VALUE);
            if (gui != null) {
               yield gui;
            } else {
               Integer hotbar = parseIndexed(token, "hotbar", 0, 9);
               yield hotbar != null ? hotbar : parseIndexed(token, "inventory", 9, 27);
            }
         }
      };
   }

   private static Integer parseIndexed(String token, String prefix, int offset, int maxIndex) {
      if (!token.startsWith(prefix)) {
         return null;
      } else {
         String suffix = token.substring(prefix.length());
         if (suffix.startsWith(":")) {
            suffix = suffix.substring(1);
         }

         try {
            int oneBased = Integer.parseInt(suffix);
            return oneBased >= 1 && oneBased <= maxIndex ? offset + oneBased - 1 : null;
         } catch (NumberFormatException var6) {
            return null;
         }
      }
   }
}
