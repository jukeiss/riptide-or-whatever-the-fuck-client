package riptide.gui.multi;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.util.RiptideClientMessaging;
import riptide.util.multi.MultiManager;

public final class MultiChatPresentation {
   private MultiChatPresentation() {
   }

   public static List<MultiChatPresentation.VisualRow> wrap(
      Font font, List<MultiManager.ChatLine> lines, int width, int totalAccounts, Function<String, String> accountLabel, int mutedColor
   ) {
      List<MultiChatPresentation.VisualRow> out = new ArrayList<>();
      if (font != null && lines != null) {
         int available = Math.max(1, width);

         for (MultiManager.ChatLine line : lines) {
            Component full = prefixed(line, totalAccounts, accountLabel, mutedColor);
            List<FormattedCharSequence> render = font.split(full, available);
            List<FormattedText> hit = font.getSplitter().splitLines(full, available, Style.EMPTY);

            for (int index = 0; index < render.size(); index++) {
               FormattedText hitLine = index < hit.size() ? hit.get(index) : FormattedText.EMPTY;
               out.add(new MultiChatPresentation.VisualRow(line, index, render.get(index), hitLine));
            }
         }

         return out;
      } else {
         return out;
      }
   }

   public static Component prefixed(MultiManager.ChatLine line, int totalAccounts, Function<String, String> accountLabel, int mutedColor) {
      Component message = (Component)(line != null && line.render() != null ? line.render() : Component.empty());
      return prefixed(line, message, totalAccounts, accountLabel, mutedColor);
   }

   public static Component recipientPrefix(MultiManager.ChatLine line, int totalAccounts, Function<String, String> accountLabel, int mutedColor) {
      if (line != null && !line.system()) {
         Map<String, Component> targets = line.targets();
         int count = targets == null ? 0 : targets.size();
         if (count == 1 && totalAccounts > 1) {
            String id = targets.keySet().iterator().next();
            String label = accountLabel == null ? null : accountLabel.apply(id);
            String name = label != null && !label.isBlank() ? MultiManager.singleLine(label, 16) : id;
            return Component.literal("[" + name + "] ").withColor(mutedColor);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static void underlineClickableLine(GuiGraphicsExtractor graphics, Font font, FormattedText line, int x, int y) {
      if (graphics != null && font != null && line != null) {
         int[] offset = new int[]{0};
         line.visit((style, text) -> {
            int width = font.width(FormattedText.of(text, style));
            if (style.getClickEvent() != null && width > 0) {
               UiRenderer.rect(graphics, UiBounds.of(x + offset[0], y + 9, width, 1), -8738817);
            }

            offset[0] += width;
            return Optional.empty();
         }, Style.EMPTY);
      }
   }

   public static void openLinkSafely(URI uri) {
      if (uri != null && uri.getScheme() != null) {
         String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
         if (scheme.equals("http") || scheme.equals("https")) {
            Util.getPlatform().openUri(uri);
         }
      }
   }

   public static ClickEvent resolveClick(Font font, FormattedText line, int relativeX) {
      if (font != null && line != null && relativeX >= 0) {
         ClickEvent[] found = new ClickEvent[]{null};
         int[] offset = new int[]{0};
         line.visit((style, text) -> {
            int width = font.width(FormattedText.of(text, style));
            if (relativeX < offset[0] + width) {
               found[0] = style.getClickEvent();
               return Optional.of(Boolean.TRUE);
            } else {
               offset[0] += width;
               return Optional.empty();
            }
         }, Style.EMPTY);
         return found[0];
      } else {
         return null;
      }
   }

   public static int runCommands(
      Font font,
      MultiManager.ChatLine line,
      int lineIndex,
      int relativeX,
      int wrapWidth,
      int totalAccounts,
      Function<String, String> accountLabel,
      int mutedColor,
      RunCommand clicked
   ) {
      if (font != null && line != null && line.targets() != null) {
         int ran = 0;

         for (Entry<String, Component> target : line.targets().entrySet()) {
            Component full = prefixed(line, target.getValue(), totalAccounts, accountLabel, mutedColor);
            List<FormattedText> wrapped = font.getSplitter().splitLines(full, Math.max(1, wrapWidth), Style.EMPTY);
            String command = (lineIndex < wrapped.size() ? resolveClick(font, wrapped.get(lineIndex), relativeX) : null) instanceof RunCommand own
               ? own.command()
               : (clicked == null ? null : clicked.command());
            if (command != null && !command.isBlank()) {
               MultiManager.get().sendCommandTo(target.getKey(), command);
               ran++;
            }
         }

         return ran;
      } else {
         return 0;
      }
   }

   private static Component prefixed(MultiManager.ChatLine line, Component message, int totalAccounts, Function<String, String> accountLabel, int mutedColor) {
      MutableComponent out = Component.empty();
      if (line != null && line.source() != null && !line.source().isEmpty()) {
         out.append(RiptideClientMessaging.themedTag(MultiManager.singleLine(line.source(), 24)));
      }

      Component recipients = recipientPrefix(line, totalAccounts, accountLabel, mutedColor);
      if (recipients != null) {
         out.append(recipients);
      }

      out.append((Component)(message == null ? Component.empty() : MultiChatLinks.linkify(message)));
      if (line != null && line.count() > 1) {
         out.append(Component.literal(" (x" + line.count() + ")").withColor(mutedColor));
      }

      return out;
   }

   public record VisualRow(MultiManager.ChatLine line, int lineIndex, FormattedCharSequence render, FormattedText hit) {
   }
}
