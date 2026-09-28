package riptide.util.login;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuthMeChatLogin {
   private static final Pattern REGISTER = Pattern.compile("(?i)/reg(?:ister)?\\b");
   private static final Pattern LOGIN = Pattern.compile("(?i)/log(?:in)?\\b");
   private static final Pattern PLACEHOLDER = Pattern.compile("<[^>]{1,32}>");

   private AuthMeChatLogin() {
   }

   public static AuthMeChatLogin.Detection detect(String rawLine) {
      if (rawLine == null) {
         return AuthMeChatLogin.Detection.NONE;
      } else {
         String line = rawLine.trim();
         if (line.isEmpty()) {
            return AuthMeChatLogin.Detection.NONE;
         } else {
            Matcher reg = REGISTER.matcher(line);
            if (reg.find()) {
               String command = line.substring(reg.start(), reg.end()).toLowerCase(Locale.ROOT);
               return new AuthMeChatLogin.Detection(AuthMeChatLogin.Kind.REGISTER, command, registerPasswordArgs(line, reg.end()));
            } else {
               Matcher log = LOGIN.matcher(line);
               return log.find()
                  ? new AuthMeChatLogin.Detection(AuthMeChatLogin.Kind.LOGIN, line.substring(log.start(), log.end()).toLowerCase(Locale.ROOT), 1)
                  : AuthMeChatLogin.Detection.NONE;
            }
         }
      }
   }

   public static String loginCommandFor(String registerCommand, String seenLoginCommand) {
      if (seenLoginCommand != null && !seenLoginCommand.isBlank()) {
         return seenLoginCommand;
      } else {
         return "/reg".equalsIgnoreCase(registerCommand == null ? "" : registerCommand.trim()) ? "/log" : "/login";
      }
   }

   private static int registerPasswordArgs(String line, int afterCommand) {
      String tail = line.substring(afterCommand);
      Matcher ph = PLACEHOLDER.matcher(tail);
      int count = 0;
      boolean email = false;

      while (ph.find()) {
         count++;
         String token = ph.group().toLowerCase(Locale.ROOT);
         if (token.contains("mail") || token.contains("correo") || token.contains("courriel")) {
            email = true;
         }
      }

      if (email) {
         return 1;
      } else {
         return count == 1 ? 1 : 2;
      }
   }

   public record Detection(AuthMeChatLogin.Kind kind, String command, int passwordArgs) {
      public static final AuthMeChatLogin.Detection NONE = new AuthMeChatLogin.Detection(AuthMeChatLogin.Kind.NONE, "", 0);

      public String commandLine(String password) {
         String line = this.command + " " + password;
         return this.passwordArgs >= 2 ? line + " " + password : line;
      }
   }

   public static enum Kind {
      NONE,
      REGISTER,
      LOGIN;
   }
}
