package riptide.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RiptideChatLine {
   private static final Pattern NAME = Pattern.compile("(?<![A-Za-z0-9_])([A-Za-z0-9_]{3,16})(?![A-Za-z0-9_])");
   private static final Pattern BRACKETED = Pattern.compile("\\[[^\\]]*\\]|\\([^)]*\\)|<[^>]*>");

   private RiptideChatLine() {
   }

   public static String sender(String var0) {
      if (var0 != null && !var0.isEmpty()) {
         Matcher var1 = NAME.matcher(stripTags(var0));
         return var1.find() ? var1.group(1) : null;
      } else {
         return null;
      }
   }

   public static String senderBefore(String var0, String var1) {
      if (var0 == null || var0.isEmpty()) {
         return null;
      } else if (var1 != null && !var1.isBlank()) {
         String var2 = stripTags(var0);
         int var3 = var2.toLowerCase(Locale.ROOT).indexOf(var1.trim().toLowerCase(Locale.ROOT));
         if (var3 <= 0) {
            return sender(var0);
         } else {
            Matcher var4 = NAME.matcher(var2.substring(0, var3));
            String var5 = null;

            while (var4.find()) {
               var5 = var4.group(1);
            }

            return var5 != null ? var5 : sender(var0);
         }
      } else {
         return sender(var0);
      }
   }

   public static String stripTags(String var0) {
      return var0 == null ? "" : BRACKETED.matcher(var0).replaceAll(" ");
   }

   public static boolean contains(String var0, String var1) {
      if (var0 == null || var0.isEmpty()) {
         return false;
      } else {
         return var1 != null && !var1.isBlank() ? var0.toLowerCase(Locale.ROOT).contains(var1.trim().toLowerCase(Locale.ROOT)) : false;
      }
   }

   public static boolean sameName(String var0, String var1) {
      return var0 != null && var1 != null ? var0.trim().equalsIgnoreCase(var1.trim()) : false;
   }
}
