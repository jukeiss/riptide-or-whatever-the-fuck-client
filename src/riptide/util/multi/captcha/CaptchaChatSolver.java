package riptide.util.multi.captcha;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.ClickEvent.RunCommand;

public final class CaptchaChatSolver {
   private static final Pattern MATH = Pattern.compile("(\\d{1,3})\\s*([+\\-])\\s*(\\d{1,3})");
   private static final Pattern CODE_TOKEN = Pattern.compile("(?<![A-Za-z0-9])([A-Za-z0-9]{3,8})(?![A-Za-z0-9])");
   private static final Pattern CAPTCHA_HINT = Pattern.compile("captcha|verif|\\bcode\\b|type the|enter the|solve|try again|➤", 2);
   private static final String[][] COLOR_WORDS = new String[][]{
      {"pink", "light_purple"},
      {"purple", "dark_purple"},
      {"blue", "blue"},
      {"aqua", "aqua"},
      {"cyan", "aqua"},
      {"green", "green"},
      {"lime", "green"},
      {"yellow", "yellow"},
      {"gold", "gold"},
      {"orange", "gold"},
      {"red", "red"},
      {"white", "white"},
      {"gray", "gray"},
      {"grey", "gray"}
   };
   private final ArrayDeque<String> recentLines = new ArrayDeque<>();

   public CaptchaChatSolver.Answer onChat(Component component) {
      if (component == null) {
         return null;
      } else {
         String plain = component.getString();
         List<CaptchaChatSolver.Button> buttons = new ArrayList<>();
         this.collectButtons(component, buttons);
         if (!buttons.isEmpty()) {
            CaptchaChatSolver.Answer a = this.solveButtons(buttons, plain);
            if (a != null) {
               this.pushRecent(plain);
               return a;
            }
         }

         CaptchaChatSolver.Answer code = this.solveChatCode(plain);
         this.pushRecent(plain);
         return code;
      }
   }

   private void collectButtons(Component node, List<CaptchaChatSolver.Button> out) {
      Style style = node.getStyle();
      if (style != null && style.getClickEvent() instanceof RunCommand rc) {
         String cmd = rc.command();
         if (cmd != null && cmd.toLowerCase(Locale.ROOT).contains("captcha_click")) {
            out.add(new CaptchaChatSolver.Button(cmd, node.getString().trim(), style.getColor()));
         }
      }

      for (Component sibling : node.getSiblings()) {
         this.collectButtons(sibling, out);
      }
   }

   private CaptchaChatSolver.Answer solveButtons(List<CaptchaChatSolver.Button> buttons, String currentLine) {
      String context = String.join(" ", this.recentLines).toLowerCase(Locale.ROOT);
      Matcher m = MATH.matcher(context);

      while (m.find()) {
         int a = Integer.parseInt(m.group(1));
         int b = Integer.parseInt(m.group(3));
         int result = m.group(2).equals("-") ? a - b : a + b;

         for (CaptchaChatSolver.Button btn : buttons) {
            String label = btn.label().replaceAll("[^0-9-]", "");
            if (!label.isEmpty() && parseIntSafe(label) == result) {
               return command(btn.command());
            }
         }
      }

      List<CaptchaChatSolver.Button> labelHits = new ArrayList<>();

      for (CaptchaChatSolver.Button btnx : buttons) {
         String label = btnx.label().toLowerCase(Locale.ROOT).trim();
         if (label.length() >= 2 && context.contains(label)) {
            labelHits.add(btnx);
         }
      }

      if (labelHits.size() == 1) {
         return command(labelHits.get(0).command());
      } else if (labelHits.size() > 1) {
         for (CaptchaChatSolver.Button btnxx : labelHits) {
            if (this.colorWordInContext(btnxx.color(), context)) {
               return command(btnxx.command());
            }
         }

         return command(labelHits.get(0).command());
      } else {
         for (CaptchaChatSolver.Button btnxxx : buttons) {
            if (this.colorWordInContext(btnxxx.color(), context)) {
               return command(btnxxx.command());
            }
         }

         return null;
      }
   }

   private boolean colorWordInContext(TextColor color, String context) {
      if (color == null) {
         return false;
      } else {
         String name = color.serialize().toLowerCase(Locale.ROOT);

         for (String[] pair : COLOR_WORDS) {
            if (pair[1].equals(name) && context.contains(pair[0])) {
               return true;
            }
         }

         return false;
      }
   }

   private static CaptchaChatSolver.Answer command(String clickCommand) {
      String cmd = clickCommand.startsWith("/") ? clickCommand.substring(1) : clickCommand;
      return new CaptchaChatSolver.Answer(CaptchaChatSolver.Kind.COMMAND, cmd);
   }

   private CaptchaChatSolver.Answer solveChatCode(String line) {
      if (line != null && !line.isBlank()) {
         boolean hint = CAPTCHA_HINT.matcher(line).find();
         int arrow = line.indexOf(10148);
         if (!hint && arrow < 0) {
            return null;
         } else {
            String search = arrow >= 0 ? line.substring(arrow + 1) : line;
            Matcher m = CODE_TOKEN.matcher(search);
            String candidate = null;

            while (m.find()) {
               String tok = m.group(1);
               if (tok.chars().anyMatch(Character::isDigit) || tok.equals(tok.toUpperCase(Locale.ROOT))) {
                  candidate = tok;
               }
            }

            return candidate == null ? null : new CaptchaChatSolver.Answer(CaptchaChatSolver.Kind.CHAT, candidate);
         }
      } else {
         return null;
      }
   }

   private void pushRecent(String line) {
      if (line != null && !line.isBlank()) {
         this.recentLines.addLast(line);

         while (this.recentLines.size() > 6) {
            this.recentLines.removeFirst();
         }
      }
   }

   private static int parseIntSafe(String s) {
      try {
         return Integer.parseInt(s);
      } catch (NumberFormatException var2) {
         return Integer.MIN_VALUE;
      }
   }

   public record Answer(CaptchaChatSolver.Kind kind, String text) {
   }

   private record Button(String command, String label, TextColor color) {
   }

   public static enum Kind {
      CHAT,
      COMMAND;
   }
}
