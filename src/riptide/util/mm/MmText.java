package riptide.util.mm;

public final class MmText {
   private MmText() {
   }

   public static String clean(String s, int maxLen) {
      if (s == null) {
         return "";
      } else {
         StringBuilder sb = new StringBuilder(Math.min(s.length(), maxLen));

         for (int i = 0; i < s.length() && sb.length() < maxLen; i++) {
            char c = s.charAt(i);
            if (c != 167 && c >= ' ' && c != 127) {
               sb.append(c);
            }
         }

         return sb.toString();
      }
   }

   public static String clean(String s) {
      return clean(s, 256);
   }
}
