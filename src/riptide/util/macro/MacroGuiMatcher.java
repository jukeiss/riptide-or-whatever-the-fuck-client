package riptide.util.macro;

import java.util.Locale;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.HangingSignEditScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;

public final class MacroGuiMatcher {
   private MacroGuiMatcher() {
   }

   public static boolean matches(Screen screen, String query) {
      if (screen != null && !isOwnScreen(screen)) {
         String trimmed = query == null ? "" : query.trim();
         if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase("any")) {
            MacroGuiMatcher.GuiType type = parseType(trimmed);
            if (type != null && matchesType(screen, type)) {
               return true;
            } else {
               String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
               String semantic = semanticName(screen);
               String lower = trimmed.toLowerCase(Locale.ROOT);
               return title.toLowerCase(Locale.ROOT).contains(lower) || semantic.toLowerCase(Locale.ROOT).contains(lower);
            }
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   public static boolean matches(Screen screen, String guiType, String titleFilter) {
      MacroGuiMatcher.GuiType type = parseType(guiType);
      if (type == null) {
         type = MacroGuiMatcher.GuiType.ANY;
      }

      return !matchesType(screen, type) ? false : titleFilter == null || titleFilter.isBlank() || matches(screen, titleFilter);
   }

   public static boolean matchesType(Screen screen, MacroGuiMatcher.GuiType type) {
      if (screen != null && !isOwnScreen(screen)) {
         return switch (type == null ? MacroGuiMatcher.GuiType.ANY : type) {
            case ANY -> true;
            case CONTAINER -> screen instanceof AbstractContainerScreen
               && !(screen instanceof InventoryScreen)
               && !(screen instanceof CreativeModeInventoryScreen);
            case INVENTORY -> screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen;
            case SIGN -> screen instanceof AbstractSignEditScreen;
            case HANGING_SIGN -> screen instanceof HangingSignEditScreen;
            case BOOK -> screen instanceof BookEditScreen || screen instanceof BookSignScreen || screen instanceof BookViewScreen;
            case BOOK_EDIT -> screen instanceof BookEditScreen;
            case BOOK_SIGN -> screen instanceof BookSignScreen;
            case BOOK_VIEW -> screen instanceof BookViewScreen;
            case CHAT -> screen instanceof ChatScreen;
            case CUSTOM_MENU -> screen instanceof DialogScreen;
         };
      } else {
         return false;
      }
   }

   public static String semanticName(Screen screen) {
      if (screen == null) {
         return "";
      } else if (screen instanceof DialogScreen) {
         return "CustomScreen";
      } else if (screen instanceof HangingSignEditScreen) {
         return "Hanging Sign";
      } else if (screen instanceof SignEditScreen || screen instanceof AbstractSignEditScreen) {
         return "Sign";
      } else if (screen instanceof BookSignScreen) {
         return "Book Sign";
      } else if (screen instanceof BookEditScreen) {
         return "Book Edit";
      } else if (screen instanceof BookViewScreen) {
         return "Book View";
      } else if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
         return "Inventory";
      } else if (screen instanceof AbstractContainerScreen) {
         return "Container";
      } else {
         return screen instanceof ChatScreen ? "Chat" : screen.getClass().getSimpleName();
      }
   }

   public static boolean isOwnScreen(Screen screen) {
      return screen != null && screen.getClass().getName().startsWith("riptide.");
   }

   private static MacroGuiMatcher.GuiType parseType(String raw) {
      if (raw != null && !raw.isBlank()) {
         String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');

         return switch (key) {
            case "ANY" -> MacroGuiMatcher.GuiType.ANY;
            case "CONTAINER", "GUI", "CHEST", "SHULKER" -> MacroGuiMatcher.GuiType.CONTAINER;
            case "INVENTORY", "PLAYER_INVENTORY", "PLAYER", "INV" -> MacroGuiMatcher.GuiType.INVENTORY;
            case "SIGN", "SIGN_GUI" -> MacroGuiMatcher.GuiType.SIGN;
            case "HANGING_SIGN", "HANGINGSIGN" -> MacroGuiMatcher.GuiType.HANGING_SIGN;
            case "BOOK" -> MacroGuiMatcher.GuiType.BOOK;
            case "BOOK_EDIT", "EDIT_BOOK", "WRITABLE_BOOK" -> MacroGuiMatcher.GuiType.BOOK_EDIT;
            case "BOOK_SIGN", "SIGN_BOOK" -> MacroGuiMatcher.GuiType.BOOK_SIGN;
            case "BOOK_VIEW", "READ_BOOK" -> MacroGuiMatcher.GuiType.BOOK_VIEW;
            case "CHAT" -> MacroGuiMatcher.GuiType.CHAT;
            case "CUSTOM_MENU", "CUSTOM", "CUSTOM_SCREEN", "CUSTOMSCREEN", "DIALOG" -> MacroGuiMatcher.GuiType.CUSTOM_MENU;
            default -> null;
         };
      } else {
         return null;
      }
   }

   public static boolean isCustomMenuType(String raw) {
      return parseType(raw) == MacroGuiMatcher.GuiType.CUSTOM_MENU;
   }

   public static enum GuiType {
      ANY,
      CONTAINER,
      INVENTORY,
      SIGN,
      HANGING_SIGN,
      BOOK,
      BOOK_EDIT,
      BOOK_SIGN,
      BOOK_VIEW,
      CHAT,
      CUSTOM_MENU;
   }
}
