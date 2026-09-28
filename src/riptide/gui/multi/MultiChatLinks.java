package riptide.gui.multi;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent.OpenUrl;

public final class MultiChatLinks {
   private static final int LINK_COLOR = -10835457;
   private static final String TRAILING = ".,!?;:)]}'\"";
   private static final Pattern URL = Pattern.compile(
      "(?i)(https?://[^\\s]+|www\\.[^\\s]+|[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.(?:com|net|org|gg|io|tv|dev|me|co|uk|edu|gov|info|xyz|app|link|shop|store|site|online|be)(?:/[^\\s]*)?)"
   );

   private MultiChatLinks() {
   }

   public static Component linkify(Component in) {
      if (in == null) {
         return Component.empty();
      } else {
         MutableComponent out = Component.empty();
         in.visit((style, text) -> {
            appendLinkified(out, text, style);
            return Optional.empty();
         }, Style.EMPTY);
         return out;
      }
   }

   private static void appendLinkified(MutableComponent out, String text, Style style) {
      if (text != null && !text.isEmpty()) {
         if (style.getClickEvent() != null) {
            out.append(Component.literal(text).setStyle(style));
         } else {
            Matcher matcher = URL.matcher(text);

            int last;
            for (last = 0; matcher.find(); last = matcher.end()) {
               if (matcher.start() > last) {
                  out.append(Component.literal(text.substring(last, matcher.start())).setStyle(style));
               }

               String raw = matcher.group();
               int endTrim = raw.length();

               while (endTrim > 0 && ".,!?;:)]}'\"".indexOf(raw.charAt(endTrim - 1)) >= 0) {
                  endTrim--;
               }

               String shown = raw.substring(0, endTrim);
               String trailing = raw.substring(endTrim);
               URI uri = toUri(shown);
               if (uri != null) {
                  out.append(Component.literal(shown).setStyle(style.withColor(-10835457).withUnderlined(true).withClickEvent(new OpenUrl(uri))));
               } else {
                  out.append(Component.literal(shown).setStyle(style));
               }

               if (!trailing.isEmpty()) {
                  out.append(Component.literal(trailing).setStyle(style));
               }
            }

            if (last < text.length()) {
               out.append(Component.literal(text.substring(last)).setStyle(style));
            }
         }
      }
   }

   private static URI toUri(String token) {
      String s = !token.regionMatches(true, 0, "http://", 0, 7) && !token.regionMatches(true, 0, "https://", 0, 8) ? "https://" + token : token;

      try {
         URI uri = URI.create(s);
         return uri.getHost() == null && uri.getScheme() == null ? null : uri;
      } catch (RuntimeException var3) {
         return null;
      }
   }
}
