package riptide.gui.vanillaui;

import java.util.ArrayList;
import java.util.List;

public final class TextWrapLayout {
   private TextWrapLayout() {
   }

   public static List<TextWrapLayout.Line> layout(String text, int maxWidth, TextWrapLayout.RangeWidth width) {
      String source = text == null ? "" : text;
      int available = Math.max(1, maxWidth);
      List<TextWrapLayout.Line> lines = new ArrayList<>();
      if (source.isEmpty()) {
         lines.add(new TextWrapLayout.Line(0, 0, 0));
         return lines;
      } else {
         int paragraphStart = 0;

         while (paragraphStart < source.length()) {
            int newline = source.indexOf(10, paragraphStart);
            int paragraphEnd = newline >= 0 ? newline : source.length();
            if (paragraphStart == paragraphEnd) {
               lines.add(new TextWrapLayout.Line(paragraphStart, newline + 1, paragraphStart));
            } else {
               int cursor = paragraphStart;

               while (cursor < paragraphEnd) {
                  int end = nextLineEnd(source, cursor, paragraphEnd, available, width);
                  int renderEnd = trimRenderEnd(source, cursor, end);
                  lines.add(new TextWrapLayout.Line(cursor, end, renderEnd));
                  cursor = end;
               }

               if (newline >= 0) {
                  TextWrapLayout.Line last = lines.remove(lines.size() - 1);
                  lines.add(new TextWrapLayout.Line(last.start(), newline + 1, last.renderEnd()));
               }
            }

            paragraphStart = newline >= 0 ? newline + 1 : source.length();
         }

         if (source.endsWith("\n")) {
            lines.add(new TextWrapLayout.Line(source.length(), source.length(), source.length()));
         }

         return lines;
      }
   }

   public static int nextLineEnd(String source, int start, int limit, int maxWidth, TextWrapLayout.RangeWidth width) {
      if (source != null && !source.isEmpty()) {
         int safeStart = floorCodePointBoundary(source, Math.max(0, Math.min(start, source.length())));
         int safeLimit = floorCodePointBoundary(source, Math.max(safeStart, Math.min(limit, source.length())));
         if (safeStart >= safeLimit) {
            return safeLimit;
         } else {
            int available = Math.max(1, maxWidth);
            int low = 1;
            int high = source.codePointCount(safeStart, safeLimit);
            int fittingCodePoints = 0;

            while (low <= high) {
               int middle = low + (high - low >>> 1);
               int middleEnd = source.offsetByCodePoints(safeStart, middle);
               if (width.width(safeStart, middleEnd) <= available) {
                  fittingCodePoints = middle;
                  low = middle + 1;
               } else {
                  high = middle - 1;
               }
            }

            int fittingEnd = source.offsetByCodePoints(safeStart, fittingCodePoints);
            if (fittingEnd >= safeLimit) {
               return safeLimit;
            } else if (fittingEnd == safeStart) {
               return source.offsetByCodePoints(safeStart, 1);
            } else {
               int end = fittingEnd;

               while (end > safeStart) {
                  int index = source.offsetByCodePoints(end, -1);
                  if (isPreferredBreak(source.codePointAt(index))) {
                     return end;
                  }

                  end = index;
               }

               return fittingEnd;
            }
         }
      } else {
         return 0;
      }
   }

   private static int trimRenderEnd(String source, int start, int end) {
      int renderEnd;
      for (renderEnd = end; renderEnd > start; renderEnd--) {
         char ch = source.charAt(renderEnd - 1);
         if (ch != ' ' && ch != '\t' && ch != '\r' && ch != '\n') {
            break;
         }
      }

      return renderEnd;
   }

   private static int floorCodePointBoundary(String source, int index) {
      return index > 0 && index < source.length() && Character.isHighSurrogate(source.charAt(index - 1)) && Character.isLowSurrogate(source.charAt(index))
         ? index - 1
         : index;
   }

   private static boolean isPreferredBreak(int ch) {
      return Character.isWhitespace(ch) || ch == 44 || ch == 59 || ch == 58 || ch == 47 || ch == 41 || ch == 93 || ch == 125 || ch == 62;
   }

   public record Line(int start, int end, int renderEnd) {
   }

   @FunctionalInterface
   public interface RangeWidth {
      int width(int var1, int var2);
   }
}
