package riptide.util;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import riptide.util.macro.MacroExecutor;

public final class RiptidePlayerScanner {
   private RiptidePlayerScanner() {
   }

   public static List<RiptidePlayerScanner.ScannedPlayer> scan(Minecraft mc) {
      Map<String, RiptidePlayerScanner.ScannedPlayer> byName = new LinkedHashMap<>();
      if (mc == null) {
         return new ArrayList<>();
      } else {
         String self = mc.player != null && mc.player.getGameProfile() != null ? mc.player.getGameProfile().name() : null;
         Scoreboard scoreboard = mc.level != null ? mc.level.getScoreboard() : null;
         ClientPacketListener connection = mc.getConnection();
         if (connection != null) {
            for (PlayerInfo info : connection.getOnlinePlayers()) {
               if (info != null) {
                  RiptidePlayerScanner.RankedName parsed = parseDisplayName(info.getTabListDisplayName());
                  if (parsed != null && valid(parsed.name(), self)) {
                     put(byName, parsed.name(), parsed.rank(), parsed.rankComponent());
                  }
               }
            }

            for (PlayerInfo infox : connection.getOnlinePlayers()) {
               if (infox != null && isRealPlayer(infox.getProfile())) {
                  String name = infox.getProfile().name();
                  if (valid(name, self)) {
                     put(byName, name, resolvePrefix(scoreboard, name, infox), null);
                  }
               }
            }
         }

         if (mc.level != null) {
            for (AbstractClientPlayer player : mc.level.players()) {
               if (player != null && isRealPlayer(player.getGameProfile())) {
                  String name = player.getGameProfile().name();
                  if (valid(name, self)) {
                     PlayerInfo infoxx = connection == null ? null : connection.getPlayerInfoIgnoreCase(name);
                     put(byName, name, resolvePrefix(scoreboard, name, infoxx), null);
                  }
               }
            }
         }

         enrichRanksFromChat(byName);
         List<RiptidePlayerScanner.ScannedPlayer> result = new ArrayList<>(byName.values());
         result.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
         return result;
      }
   }

   private static void enrichRanksFromChat(Map<String, RiptidePlayerScanner.ScannedPlayer> byName) {
      if (!byName.isEmpty() && !byName.values().stream().allMatch(RiptidePlayerScanner.ScannedPlayer::hasPrefix)) {
         List<String> keys = new ArrayList<>(byName.keySet());
         keys.sort((a, b) -> Integer.compare(b.length(), a.length()));

         try {
            for (MacroExecutor.RecentChatMessage chat : MacroExecutor.getRecentChatMessages()) {
               if (chat != null && chat.displayText() != null && !chat.displayText().isBlank()) {
                  String display = chat.displayText();
                  int sep = display.indexOf(187);
                  String header = sep > 0 ? display.substring(0, sep) : display;
                  String lowerHeader = header.toLowerCase(Locale.ROOT);

                  for (String key : keys) {
                     RiptidePlayerScanner.ScannedPlayer player = byName.get(key);
                     if (player != null && !player.hasPrefix()) {
                        int nameIdx = lowerHeader.indexOf(key);
                        if (nameIdx > 0) {
                           String rank = nonAsciiGlyphs(header.substring(0, nameIdx));
                           if (!rank.isBlank()) {
                              byName.put(key, new RiptidePlayerScanner.ScannedPlayer(player.name(), rank, null));
                              break;
                           }
                        }
                     }
                  }
               }
            }
         } catch (Throwable var13) {
         }
      }
   }

   private static boolean valid(String name, String self) {
      return name != null && !name.isBlank() && (self == null || !name.equalsIgnoreCase(self));
   }

   private static boolean isRealPlayer(GameProfile profile) {
      return profile != null && isRealName(profile.name());
   }

   public static boolean isRealName(String name) {
      return isUsername(name) && !isLayoutPlaceholder(name);
   }

   public static boolean isUsername(String name) {
      if (name == null) {
         return false;
      } else {
         int len = name.length();
         if (len >= 2 && len <= 32) {
            boolean hasAlnum = false;

            for (int i = 0; i < len; i++) {
               char c = name.charAt(i);
               boolean alnum = c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9';
               if (alnum) {
                  hasAlnum = true;
               } else if (c != '_' && c != '.' && c != ' ') {
                  return false;
               }
            }

            return hasAlnum;
         } else {
            return false;
         }
      }
   }

   private static boolean isLayoutPlaceholder(String name) {
      String lower = name.toLowerCase(Locale.ROOT).trim();
      return lower.matches("(slot|npc|line|spacer|empty|blank|grid|hud|tab|frame|fill)_?-?\\d+") || lower.matches("[#.\\-_ ]*\\d{1,4}[#.\\-_ ]*");
   }

   private static void put(Map<String, RiptidePlayerScanner.ScannedPlayer> byName, String name, String prefix, Component prefixComponent) {
      String key = name.toLowerCase(Locale.ROOT);
      RiptidePlayerScanner.ScannedPlayer existing = byName.get(key);
      if (existing == null) {
         byName.put(key, new RiptidePlayerScanner.ScannedPlayer(name, prefix, prefixComponent));
      } else if (!existing.hasPrefix() && prefix != null && !prefix.isBlank()) {
         byName.put(key, new RiptidePlayerScanner.ScannedPlayer(existing.name(), prefix, prefixComponent));
      }
   }

   private static RiptidePlayerScanner.RankedName parseDisplayName(Component display) {
      if (display == null) {
         return null;
      } else {
         String text = strip(display);
         if (text.isBlank()) {
            return null;
         } else {
            String rank = nonAsciiGlyphs(text);
            if (rank.isBlank()) {
               return null;
            } else {
               String name = nameAfterGlyphs(text);
               return !name.isBlank() && isUsername(name) && !isLayoutPlaceholder(name)
                  ? new RiptidePlayerScanner.RankedName(name, rank, extractStyledRank(display))
                  : null;
            }
         }
      }
   }

   private static MutableComponent extractStyledRank(Component display) {
      if (display == null) {
         return null;
      } else {
         MutableComponent out = Component.empty();
         boolean[] any = new boolean[]{false};
         boolean[] stopped = new boolean[]{false};
         display.visit((style, content) -> {
            StringBuilder seg = new StringBuilder();

            for (int i = 0; i < content.length(); i++) {
               char c = content.charAt(i);
               if (c > 127 && !Character.isWhitespace(c)) {
                  seg.append(c);
               } else if (seg.length() != 0 || any[0] || !Character.isWhitespace(c)) {
                  stopped[0] = true;
                  break;
               }
            }

            if (seg.length() > 0) {
               out.append(Component.literal(seg.toString()).setStyle(style));
               any[0] = true;
            }

            return stopped[0] ? Optional.of(Boolean.TRUE) : Optional.empty();
         }, Style.EMPTY);
         return any[0] ? out : null;
      }
   }

   private static String nameAfterGlyphs(String text) {
      int last = -1;

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c > 127 && !Character.isWhitespace(c)) {
            last = i;
         }
      }

      String tail = last < 0 ? text : text.substring(last + 1);
      return stripLevel(tail).trim();
   }

   private static String resolvePrefix(Scoreboard scoreboard, String name, PlayerInfo info) {
      try {
         Component display = info != null ? info.getTabListDisplayName() : null;
         String fromDisplay = rankFrom(strip(display), name);
         if (!fromDisplay.isBlank()) {
            return fromDisplay;
         } else {
            PlayerTeam team = info != null ? info.getTeam() : (scoreboard != null ? scoreboard.getPlayersTeam(name) : null);
            if (team != null) {
               String fromPrefix = rankFrom(strip(team.getPlayerPrefix()), name);
               if (!fromPrefix.isBlank()) {
                  return fromPrefix;
               }

               String fromSuffix = rankFrom(strip(team.getPlayerSuffix()), name);
               if (!fromSuffix.isBlank()) {
                  return fromSuffix;
               }

               String fromTeam = rankFrom(strip(PlayerTeam.formatNameForTeam(team, Component.literal(name))), name);
               if (!fromTeam.isBlank()) {
                  return fromTeam;
               }
            }

            return "";
         }
      } catch (Throwable var9) {
         return "";
      }
   }

   private static String rankFrom(String text, String name) {
      if (text != null && !text.isBlank()) {
         String glyphs = nonAsciiGlyphs(text);
         if (!glyphs.isBlank()) {
            return glyphs;
         } else {
            String ascii = stripLevel(text);
            int idx = indexOfName(ascii, name);
            if (idx > 0) {
               String prefix = ascii.substring(0, idx).trim();
               if (!prefix.isBlank()) {
                  return prefix;
               }
            }

            return idx < 0 && ascii.length() <= 24 ? ascii : "";
         }
      } else {
         return "";
      }
   }

   private static int indexOfName(String text, String name) {
      if (text != null && name != null && !name.isBlank()) {
         String lower = text.toLowerCase(Locale.ROOT);
         int idx = lower.indexOf(name.toLowerCase(Locale.ROOT));
         if (idx < 0 && name.startsWith(".") && name.length() > 1) {
            idx = lower.indexOf(name.substring(1).toLowerCase(Locale.ROOT));
         }

         return idx;
      } else {
         return -1;
      }
   }

   private static String nonAsciiGlyphs(String text) {
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c > 127 && !Character.isWhitespace(c)) {
            sb.append(c);
         }
      }

      return sb.toString().trim();
   }

   private static String stripLevel(String text) {
      return text == null ? "" : text.replaceFirst("^\\s*\\[\\s*\\d+\\s*\\]\\s*", "").trim();
   }

   private static String strip(Component component) {
      return component == null ? "" : stripFormatting(component.getString());
   }

   public static String stripFormatting(String text) {
      if (text == null) {
         return "";
      } else {
         StringBuilder out = new StringBuilder(text.length());

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 167 && i + 1 < text.length()) {
               char code = Character.toLowerCase(text.charAt(i + 1));
               if ("0123456789abcdefklmnorx".indexOf(code) >= 0) {
                  i++;
                  continue;
               }
            }

            out.append(c);
         }

         return out.toString().trim();
      }
   }

   private record RankedName(String name, String rank, Component rankComponent) {
   }

   public record ScannedPlayer(String name, String prefix, Component prefixComponent) {
      public boolean hasPrefix() {
         return this.prefix != null && !this.prefix.isBlank();
      }
   }
}
