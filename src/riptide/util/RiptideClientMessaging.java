package riptide.util;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.CommandHistory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import riptide.commands.RiptideCommands;
import riptide.mixin.accessor.RiptideChatComponentAccessor;
import riptide.mixin.accessor.RiptideCommandHistoryAccessor;
import riptide.modules.PackHideState;

public final class RiptideClientMessaging {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final long DUPLICATE_WINDOW_MS = 600L;
   private static final String PREFIX_TEXT = "[RIPTIDE] ";
   private static String lastMessageKey = "";
   private static long lastMessageAtMs = 0L;
   private static String lastReplaceGroup = "";
   private static long lastReplaceAtMs = 0L;
   private static final long REPLACE_WINDOW_MS = 4500L;
   private static final Set<String> SENT_MESSAGE_KEYS = new LinkedHashSet<>();
   private static final Object HISTORY_LOCK = new Object();
   private static final int HISTORY_QUEUE_CAP = 64;
   private static final ExecutorService HISTORY_WRITER = Executors.newSingleThreadExecutor(var0 -> {
      Thread var1 = new Thread(var0, "Riptide-CommandHistory-Writer");
      var1.setDaemon(true);
      return var1;
   });
   private static final ArrayDeque<String> pendingHistoryLines = new ArrayDeque<>();
   private static boolean historyDrainQueued;

   private RiptideClientMessaging() {
   }

   public static void send(String var0) {
      send(buildBodyText(var0));
   }

   public static void sendPrefixed(String var0) {
      MutableComponent var1 = Component.empty().append(themedTag("RIPTIDE")).append(buildBodyText(var0));
      send(var1);
   }

   public static MutableComponent themedTag(String var0) {
      return Component.empty()
         .append(styled("[", RiptideColors.textMuted()))
         .append(styled(var0 == null ? "" : var0, RiptideColors.accent(), true))
         .append(styled("] ", RiptideColors.textMuted()));
   }

   public static MutableComponent themedBody(String var0) {
      return buildBodyText(var0 == null ? "" : var0);
   }

   public static void send(Component var0) {
      if (MC != null && var0 != null && !PackHideState.shouldSuppressClientOutput()) {
         String var1 = normalizeDuplicateKey(var0.getString());
         long var2 = System.currentTimeMillis();
         synchronized (RiptideClientMessaging.class) {
            if (!var1.isEmpty() && var1.equals(lastMessageKey) && var2 - lastMessageAtMs <= 600L) {
               return;
            }

            lastMessageKey = var1;
            lastMessageAtMs = var2;
            if (!var1.isEmpty()) {
               SENT_MESSAGE_KEYS.add(var1);
            }
         }

         String var7 = replacementGroupKey(var0.getString());
         MC.execute(() -> {
            if (MC.player != null) {
               if (!var7.isEmpty()) {
                  removeReplaceableMessage(var7);
               }

               MC.gui.hud.getChat().addClientSystemMessage(var0);
            } else if (MC.gui.chatListener() != null) {
               MC.gui.chatListener().handleSystemMessage(var0, false);
            }
         });
      }
   }

   public static void clearClientMessages() {
      if (MC != null) {
         MC.execute(() -> {
            if (MC.gui != null && MC.gui.hud.getChat() != null) {
               try {
                  RiptideChatComponentAccessor var0 = (RiptideChatComponentAccessor)MC.gui.hud.getChat();
                  List var1 = var0.riptide$getAllMessages();
                  boolean var2 = false;
                  ListIterator var3 = var1.listIterator();

                  while (var3.hasNext()) {
                     GuiMessage var4 = (GuiMessage)var3.next();
                     if (isClientGeneratedMessage(var4.content().getString())) {
                        var3.remove();
                        var2 = true;
                     }
                  }

                  if (var2) {
                     var0.riptide$refreshTrimmedMessages();
                  }
               } catch (Throwable var5) {
               }
            }
         });
      }
   }

   public static void rememberRecentChat(String var0) {
      if (MC != null && var0 != null && !var0.isBlank()) {
         try {
            if (MC.gui == null || MC.gui.hud.getChat() == null) {
               return;
            }

            ChatComponent var1 = MC.gui.hud.getChat();
            var1.addRecentChat(var0);
            if (RiptideCommands.isRiptideCommandMessage(var0)) {
               CommandHistory var2 = ((RiptideChatComponentAccessor)var1).riptide$getCommandHistory();
               if (var2 != null) {
                  persistCommandHistoryAsync(var2, var0);
               }
            }
         } catch (Throwable var3) {
         }
      }
   }

   private static void persistCommandHistoryAsync(CommandHistory var0, String var1) {
      synchronized (HISTORY_LOCK) {
         if (pendingHistoryLines.size() >= 64) {
            pendingHistoryLines.pollFirst();
         }

         pendingHistoryLines.addLast(var1);
         if (historyDrainQueued) {
            return;
         }

         historyDrainQueued = true;
      }

      HISTORY_WRITER.execute(() -> {
         try {
            while (true) {
               String var1x;
               synchronized (HISTORY_LOCK) {
                  var1x = pendingHistoryLines.pollFirst();
                  if (var1x == null) {
                     historyDrainQueued = false;
                     return;
                  }
               }

               try {
                  var0.addCommand(var1x);
               } catch (Throwable var6) {
                  riptide.RiptideClientAddon.LOG.warn("[Chat] Failed to persist command history", var6);
               }
            }
         } catch (Throwable var8) {
            synchronized (HISTORY_LOCK) {
               historyDrainQueued = false;
            }

            riptide.RiptideClientAddon.LOG.warn("[Chat] Command history writer failed", var8);
         }
      });
   }

   public static void clearChatInputHistory() {
      if (MC != null) {
         MC.execute(() -> {
            try {
               if (MC.gui == null || MC.gui.hud.getChat() == null) {
                  return;
               }

               ChatComponent var0 = MC.gui.hud.getChat();
               var0.getRecentChat().clear();
               CommandHistory var1 = ((RiptideChatComponentAccessor)var0).riptide$getCommandHistory();
               if (var1 == null) {
                  return;
               }

               RiptideCommandHistoryAccessor var2 = (RiptideCommandHistoryAccessor)var1;
               var2.riptide$getLastCommands().clear();
               var2.riptide$save();
            } catch (Throwable var3) {
            }
         });
      }
   }

   private static boolean isClientGeneratedMessage(String var0) {
      String var1 = stripLegacyCodes(normalizeLegacyFormatting(var0)).replaceAll("\\s+", " ").trim();
      String var2 = var1.toLowerCase(Locale.ROOT);
      if (!var2.startsWith("[riptide]") && !var2.startsWith("[lan]")) {
         String var3 = normalizeDuplicateKey(var0);
         synchronized (RiptideClientMessaging.class) {
            return !var3.isEmpty() && SENT_MESSAGE_KEYS.contains(var3);
         }
      } else {
         return true;
      }
   }

   private static MutableComponent buildBodyText(String var0) {
      String var1 = normalizeLegacyFormatting(var0);
      MutableComponent var2 = tryBuildLanChatText(var1);
      if (var2 != null) {
         return var2;
      } else {
         int var3 = inferDefaultColor(stripLegacyCodes(var1));
         return var1.indexOf(167) >= 0 ? parseLegacyStyled(var1, var3) : colorizeStructuredText(var1, var3);
      }
   }

   private static MutableComponent tryBuildLanChatText(String var0) {
      if (var0 != null && var0.startsWith("[LAN] <")) {
         int var1 = var0.indexOf("> ");
         if (var1 <= 7) {
            return null;
         } else {
            String var2 = var0.substring(7, var1);
            String var3 = var0.substring(var1 + 2);
            return Component.empty()
               .append(styled("[", RiptideColors.textMuted()))
               .append(styled("LAN", RiptideColors.packetCyan(), true))
               .append(styled("] ", RiptideColors.textMuted()))
               .append(styled("<", RiptideColors.textMuted()))
               .append(styled(var2, RiptideColors.packetPink(), true))
               .append(styled("> ", RiptideColors.textMuted()))
               .append(colorizeTokenStream(var3, RiptideColors.textPrimary()));
         }
      } else {
         return null;
      }
   }

   private static MutableComponent colorizeStructuredText(String var0, int var1) {
      if (var0 != null && !var0.isEmpty()) {
         int var2 = var0.indexOf(58);
         if (var2 > 0 && var2 < 24 && isLikelyLabel(var0.substring(0, var2))) {
            String var3 = var0.substring(0, var2);
            String var4 = var0.substring(var2 + 1);
            MutableComponent var5 = Component.empty().append(styled(var3, RiptideColors.packetGray(), true)).append(styled(":", RiptideColors.textMuted()));
            if (!var4.isEmpty()) {
               var5.append(styled(leadingWhitespace(var4), RiptideColors.textMuted()));
               var5.append(colorizeTokenStream(var4.stripLeading(), inferDefaultColor(var4.trim())));
            }

            return var5;
         } else {
            return colorizeTokenStream(var0, var1);
         }
      } else {
         return Component.empty();
      }
   }

   private static MutableComponent colorizeTokenStream(String var0, int var1) {
      MutableComponent var2 = Component.empty();
      if (var0 != null && !var0.isEmpty()) {
         StringBuilder var3 = new StringBuilder();

         for (int var4 = 0; var4 < var0.length(); var4++) {
            char var5 = var0.charAt(var4);
            if (!Character.isLetterOrDigit(var5) && var5 != '_' && var5 != '-' && var5 != '#' && var5 != '.' && var5 != '/') {
               if (var3.length() > 0) {
                  String var6 = var3.toString();
                  var2.append(styled(var6, colorForToken(var6, var1), isEmphasizedToken(var6)));
                  var3.setLength(0);
               }

               var2.append(styled(String.valueOf(var5), punctuationColor(var5, var1)));
            } else {
               var3.append(var5);
            }
         }

         if (var3.length() > 0) {
            String var7 = var3.toString();
            var2.append(styled(var7, colorForToken(var7, var1), isEmphasizedToken(var7)));
         }

         return var2;
      } else {
         return var2;
      }
   }

   private static boolean isLikelyLabel(String var0) {
      if (var0 != null && !var0.isBlank()) {
         for (int var1 = 0; var1 < var0.length(); var1++) {
            char var2 = var0.charAt(var1);
            if (!Character.isLetterOrDigit(var2)
               && !Character.isWhitespace(var2)
               && var2 != '['
               && var2 != ']'
               && var2 != '+'
               && var2 != '-'
               && var2 != '/'
               && var2 != '#') {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static String leadingWhitespace(String var0) {
      if (var0 != null && !var0.isEmpty()) {
         int var1 = 0;

         while (var1 < var0.length() && Character.isWhitespace(var0.charAt(var1))) {
            var1++;
         }

         return var0.substring(0, var1);
      } else {
         return "";
      }
   }

   private static int punctuationColor(char var0, int var1) {
      return switch (var0) {
         case '"' -> RiptideColors.packetLightYellow();
         case '(', ')', ',', ':', '<', '>', '[', ']', '|' -> RiptideColors.textMuted();
         default -> var1;
      };
   }

   private static boolean isEmphasizedToken(String var0) {
      String var1 = normalizeToken(var0);
      return var1.equals("enabled")
         || var1.equals("disabled")
         || var1.equals("started")
         || var1.equals("stopped")
         || var1.equals("failed")
         || var1.equals("error")
         || var1.equals("queue")
         || var1.equals("packet")
         || var1.equals("packets")
         || var1.equals("gui")
         || var1.equals("macro")
         || var1.equals("macros")
         || var1.equals("sync")
         || var1.equals("lan");
   }

   private static int colorForToken(String var0, int var1) {
      String var2 = normalizeToken(var0);
      if (var2.isEmpty()) {
         return var1;
      } else if (isNumericToken(var2)) {
         return RiptideColors.packetLightYellow();
      } else if (var2.equals("enabled")
         || var2.equals("started")
         || var2.equals("joined")
         || var2.equals("restored")
         || var2.equals("saved")
         || var2.equals("loaded")
         || var2.equals("copied")
         || var2.equals("imported")
         || var2.equals("received")
         || var2.equals("executed")
         || var2.equals("finished")
         || var2.equals("reconnected")
         || var2.equals("host")
         || var2.equals("sent")
         || var2.equals("on")
         || var2.equals("true")) {
         return RiptideColors.successText();
      } else if (var2.equals("disabled")
         || var2.equals("stopped")
         || var2.equals("empty")
         || var2.equals("already")
         || var2.equals("requested")
         || var2.equals("left")
         || var2.equals("attempting")
         || var2.equals("timeout")
         || var2.equals("timed")
         || var2.equals("out")
         || var2.equals("off")
         || var2.equals("false")) {
         return RiptideColors.packetYellow();
      } else if (var2.equals("failed")
         || var2.equals("error")
         || var2.equals("errors")
         || var2.equals("denied")
         || var2.equals("blocked")
         || var2.equals("crashed")
         || var2.equals("cannot")
         || var2.equals("missing")
         || var2.equals("unavailable")
         || var2.equals("invalid")
         || var2.equals("not")
         || var2.equals("null")) {
         return RiptideColors.dangerText();
      } else if (var2.equals("queue")
         || var2.equals("packet")
         || var2.equals("packets")
         || var2.equals("sync")
         || var2.equals("lan")
         || var2.equals("session")
         || var2.equals("tcp")) {
         return RiptideColors.packetCyan();
      } else if (!var2.equals("gui") && !var2.equals("screen") && !var2.equals("inventory") && !var2.equals("slot") && !var2.equals("slots")) {
         return !var2.equals("macro") && !var2.equals("macros") && !var2.equals("preset") && !var2.equals("presets") ? var1 : RiptideColors.packetPink();
      } else {
         return RiptideColors.packetOrange();
      }
   }

   private static boolean isNumericToken(String var0) {
      int var1 = var0.startsWith("-") ? 1 : 0;
      if (var1 >= var0.length()) {
         return false;
      } else {
         for (int var2 = var1; var2 < var0.length(); var2++) {
            char var3 = var0.charAt(var2);
            if (!Character.isDigit(var3) && var3 != '.' && var3 != '/' && var3 != '#') {
               return false;
            }
         }

         return true;
      }
   }

   private static String normalizeToken(String var0) {
      if (var0 != null && !var0.isEmpty()) {
         int var1 = 0;
         int var2 = var0.length();

         while (var1 < var2 && !Character.isLetterOrDigit(var0.charAt(var1))) {
            var1++;
         }

         while (var2 > var1 && !Character.isLetterOrDigit(var0.charAt(var2 - 1))) {
            var2--;
         }

         return var1 >= var2 ? "" : var0.substring(var1, var2).toLowerCase();
      } else {
         return "";
      }
   }

   private static MutableComponent parseLegacyStyled(String var0, int var1) {
      MutableComponent var2 = Component.empty();
      if (var0 != null && !var0.isEmpty()) {
         StringBuilder var3 = new StringBuilder();
         int var4 = var1;
         boolean var5 = false;

         for (int var6 = 0; var6 < var0.length(); var6++) {
            char var7 = var0.charAt(var6);
            if (var7 == 167 && var6 + 1 < var0.length()) {
               if (var3.length() > 0) {
                  var2.append(styled(var3.toString(), var4, var5));
                  var3.setLength(0);
               }

               char var8 = Character.toLowerCase(var0.charAt(++var6));
               Integer var9 = mapLegacyColor(var8);
               if (var9 != null) {
                  var4 = var9;
                  if (isColorCode(var8) || var8 == 'r') {
                     var5 = false;
                  }
               } else if (var8 == 'l') {
                  var5 = true;
               } else if (var8 == 'r') {
                  var4 = var1;
                  var5 = false;
               }
            } else {
               var3.append(var7);
            }
         }

         if (var3.length() > 0) {
            var2.append(styled(var3.toString(), var4, var5));
         }

         return var2;
      } else {
         return var2;
      }
   }

   private static MutableComponent styled(String var0, int var1) {
      return styled(var0, var1, false);
   }

   private static MutableComponent styled(String var0, int var1, boolean var2) {
      Style var3 = Style.EMPTY.withColor(var1);
      if (var2) {
         var3 = var3.withBold(true);
      }

      return Component.literal(var0).setStyle(var3);
   }

   private static boolean isColorCode(char var0) {
      return var0 >= '0' && var0 <= '9' || var0 >= 'a' && var0 <= 'f';
   }

   private static Integer mapLegacyColor(char var0) {
      return switch (var0) {
         case '0' -> -10597554;
         case '1' -> -9727272;
         case '2' -> -10897036;
         case '3' -> RiptideColors.packetCyan();
         case '4' -> RiptideColors.dangerText();
         case '5' -> -1602869;
         case '6' -> RiptideColors.packetOrange();
         case '7' -> RiptideColors.packetGray();
         case '8' -> RiptideColors.textMuted();
         case '9' -> RiptideColors.packetBlue();
         default -> null;
         case 'a' -> RiptideColors.successText();
         case 'b' -> RiptideColors.packetCyan();
         case 'c' -> RiptideColors.dangerText();
         case 'd' -> RiptideColors.packetPink();
         case 'e' -> RiptideColors.packetYellow();
         case 'f' -> RiptideColors.textSecondary();
         case 'r' -> null;
      };
   }

   private static String normalizeLegacyFormatting(String var0) {
      return var0 == null ? "" : var0.replace("Â§", "§");
   }

   private static String stripLegacyCodes(String var0) {
      if (var0 != null && !var0.isEmpty()) {
         StringBuilder var1 = new StringBuilder(var0.length());

         for (int var2 = 0; var2 < var0.length(); var2++) {
            char var3 = var0.charAt(var2);
            if (var3 == 167 && var2 + 1 < var0.length()) {
               var2++;
            } else {
               var1.append(var3);
            }
         }

         return var1.toString().trim();
      } else {
         return "";
      }
   }

   private static int inferDefaultColor(String var0) {
      String var1 = var0 == null ? "" : var0.trim().toLowerCase();
      if (var1.isEmpty()) {
         return RiptideColors.textSecondary();
      } else if (var1.contains("failed")
         || var1.contains("error")
         || var1.startsWith("no ")
         || var1.startsWith("not ")
         || var1.contains(" not ")
         || var1.contains("cannot")
         || var1.contains("denied")
         || var1.contains("blocked")
         || var1.contains("crashed")) {
         return RiptideColors.dangerText();
      } else if (var1.contains("enabled")
         || var1.contains("started")
         || var1.contains("joined")
         || var1.contains("restored")
         || var1.contains("saved")
         || var1.contains("loaded")
         || var1.contains("copied")
         || var1.contains("imported")
         || var1.contains("received")
         || var1.contains("executed")
         || var1.contains("finished")
         || var1.startsWith("sent ")
         || var1.startsWith("signed ")
         || var1.startsWith("deleted ")
         || var1.startsWith("broadcasted ")
         || var1.startsWith("queue sent")) {
         return RiptideColors.successText();
      } else if (!var1.contains("disabled")
         && !var1.contains("stopped")
         && !var1.contains("empty")
         && !var1.contains("already")
         && !var1.contains("requested")
         && !var1.contains("left")
         && !var1.contains("attempting")
         && !var1.contains("timed out")) {
         return var1.startsWith("[lan] <") ? RiptideColors.textPrimary() : RiptideColors.textSecondary();
      } else {
         return RiptideColors.packetYellow();
      }
   }

   private static String normalizeDuplicateKey(String var0) {
      return var0 == null ? "" : stripLegacyCodes(normalizeLegacyFormatting(var0)).replace("[RIPTIDE] ", "").replaceAll("\\s+", " ").trim().toLowerCase();
   }

   private static String replacementGroupKey(String var0) {
      String var1 = stripRiptidePrefix(stripLegacyCodes(normalizeLegacyFormatting(var0)).replaceAll("\\s+", " ").trim());
      if (var1.isBlank()) {
         return "";
      } else {
         String var2 = var1.toLowerCase(Locale.ROOT);
         if (var2.startsWith("[lan]")) {
            return "";
         } else if (isErrorLike(var2)) {
            return "";
         } else {
            int var3 = var1.indexOf(58);
            if (var3 > 0 && var3 <= 38) {
               String var4 = var1.substring(0, var3).trim();
               if (isReplaceableLabel(var4)) {
                  return "label:" + var4.toLowerCase(Locale.ROOT);
               }
            }

            if (var2.startsWith("macro stopped")) {
               return "macro:stopped";
            } else if (var2.startsWith("macro finished")) {
               return "macro:finished";
            } else if (var2.startsWith("tick sync:")) {
               return "macro:tick-sync";
            } else if (var2.startsWith("rev sync:")) {
               return "macro:rev-sync";
            } else {
               return var2.startsWith("serversync:") ? "macro:server-sync" : "";
            }
         }
      }
   }

   private static void removeReplaceableMessage(String var0) {
      long var1 = System.currentTimeMillis();
      synchronized (RiptideClientMessaging.class) {
         if (!var0.equals(lastReplaceGroup) || var1 - lastReplaceAtMs > 4500L) {
            lastReplaceGroup = var0;
            lastReplaceAtMs = var1;
            return;
         }

         lastReplaceAtMs = var1;
      }

      try {
         RiptideChatComponentAccessor var9 = (RiptideChatComponentAccessor)MC.gui.hud.getChat();
         List var4 = var9.riptide$getAllMessages();
         ListIterator var5 = var4.listIterator();

         while (var5.hasNext()) {
            GuiMessage var6 = (GuiMessage)var5.next();
            if (var0.equals(replacementGroupKey(var6.content().getString()))) {
               var5.remove();
               var9.riptide$refreshTrimmedMessages();
               return;
            }
         }
      } catch (Throwable var7) {
      }
   }

   private static String stripRiptidePrefix(String var0) {
      if (var0 == null) {
         return "";
      } else {
         String var1 = var0.trim();
         if (var1.startsWith("[RIPTIDE] ")) {
            return var1.substring("[RIPTIDE] ".length()).trim();
         } else {
            return var1.startsWith("[RIPTIDE]") ? var1.substring("[RIPTIDE]".length()).trim() : var1;
         }
      }
   }

   private static boolean isReplaceableLabel(String var0) {
      if (var0 != null && !var0.isBlank()) {
         String var1 = var0.trim().toLowerCase(Locale.ROOT);
         if (!var1.contains("failed") && !var1.contains("error") && !var1.contains("invalid") && !var1.contains("missing")) {
            return !var1.equals("saved packet canceller preset") && !var1.equals("loaded packet canceller preset") ? isLikelyLabel(var0) : false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean isErrorLike(String var0) {
      return var0.startsWith("failed")
         || var0.startsWith("error")
         || var0.startsWith("invalid")
         || var0.startsWith("missing")
         || var0.startsWith("cannot")
         || var0.contains(" crashed:");
   }
}
