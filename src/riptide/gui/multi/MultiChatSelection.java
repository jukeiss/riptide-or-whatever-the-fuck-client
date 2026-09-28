package riptide.gui.multi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

public final class MultiChatSelection {
   private final List<MultiChatSelection.Row> rows = new ArrayList<>();
   private boolean active;
   private boolean dragging;
   private long anchorSeq;
   private long focusSeq;
   private int anchorLine;
   private int focusLine;
   private int anchorChar;
   private int focusChar;

   public void setRows(List<MultiChatSelection.Row> current) {
      this.rows.clear();
      if (current != null) {
         this.rows.addAll(current);
      }
   }

   public void begin(long seq, int lineIndex, int charIndex) {
      this.anchorSeq = this.focusSeq = seq;
      this.anchorLine = this.focusLine = lineIndex;
      this.anchorChar = this.focusChar = charIndex;
      this.dragging = true;
      this.active = false;
   }

   public void extend(long seq, int lineIndex, int charIndex) {
      if (this.dragging) {
         this.focusSeq = seq;
         this.focusLine = lineIndex;
         this.focusChar = charIndex;
         if (seq != this.anchorSeq || lineIndex != this.anchorLine || charIndex != this.anchorChar) {
            this.active = true;
         }
      }
   }

   public void finishDrag() {
      this.dragging = false;
   }

   public boolean dragging() {
      return this.dragging;
   }

   public boolean hasSelection() {
      return this.active;
   }

   public void clear() {
      this.active = false;
      this.dragging = false;
   }

   private int ordinal(long seq, int lineIndex) {
      for (int i = 0; i < this.rows.size(); i++) {
         MultiChatSelection.Row row = this.rows.get(i);
         if (row.seq() == seq && row.lineIndex() == lineIndex) {
            return i;
         }
      }

      return -1;
   }

   public int[] rangeFor(long seq, int lineIndex, int rowLength) {
      if (!this.active) {
         return null;
      } else {
         long[] bounds = this.orderedBounds();
         if (bounds == null) {
            return null;
         } else {
            int loOrd = (int)bounds[0];
            int loChar = (int)bounds[1];
            int hiOrd = (int)bounds[2];
            int hiChar = (int)bounds[3];
            int rOrd = this.ordinal(seq, lineIndex);
            if (rOrd >= 0 && rOrd >= loOrd && rOrd <= hiOrd) {
               int start = rOrd == loOrd ? loChar : 0;
               int end = rOrd == hiOrd ? hiChar : rowLength;
               start = Math.max(0, Math.min(start, rowLength));
               end = Math.max(start, Math.min(end, rowLength));
               return new int[]{start, end};
            } else {
               return null;
            }
         }
      }
   }

   public String selectedText() {
      if (!this.active) {
         return "";
      } else {
         long[] bounds = this.orderedBounds();
         if (bounds == null) {
            return "";
         } else {
            int loOrd = (int)bounds[0];
            int loChar = (int)bounds[1];
            int hiOrd = (int)bounds[2];
            int hiChar = (int)bounds[3];
            StringBuilder sb = new StringBuilder();

            for (int o = loOrd; o <= hiOrd; o++) {
               String t = this.rows.get(o).text();
               int start = o == loOrd ? Math.min(loChar, t.length()) : 0;
               int end = o == hiOrd ? Math.min(hiChar, t.length()) : t.length();
               if (o > loOrd) {
                  sb.append('\n');
               }

               sb.append(t, Math.max(0, Math.min(start, end)), Math.max(start, end));
            }

            return sb.toString();
         }
      }
   }

   private long[] orderedBounds() {
      int aOrd = this.ordinal(this.anchorSeq, this.anchorLine);
      int fOrd = this.ordinal(this.focusSeq, this.focusLine);
      if (aOrd >= 0 && fOrd >= 0) {
         return aOrd >= fOrd && (aOrd != fOrd || this.anchorChar > this.focusChar)
            ? new long[]{fOrd, this.focusChar, aOrd, this.anchorChar}
            : new long[]{aOrd, this.anchorChar, fOrd, this.focusChar};
      } else {
         return null;
      }
   }

   public static int widthOfStyled(Font font, FormattedText line, int chars) {
      return font != null && line != null && chars > 0 ? font.width(prefixStyled(line, chars)) : 0;
   }

   public static int charIndexAtStyled(Font font, FormattedText line, int relativeX) {
      if (font != null && line != null && relativeX > 0) {
         int[] index = new int[]{0};
         float[] acc = new float[]{0.0F};
         int[] result = new int[]{-1};
         line.visit((style, content) -> {
            for (int i = 0; i < content.length(); i++) {
               float cw = font.width(FormattedText.of(content.substring(i, i + 1), style));
               if (relativeX < acc[0] + cw / 2.0F) {
                  result[0] = index[0];
                  return Optional.of(Boolean.TRUE);
               }

               acc[0] += cw;
               index[0]++;
            }

            return Optional.empty();
         }, Style.EMPTY);
         return result[0] >= 0 ? result[0] : index[0];
      } else {
         return 0;
      }
   }

   private static FormattedText prefixStyled(FormattedText line, int chars) {
      List<FormattedText> parts = new ArrayList<>();
      int[] remaining = new int[]{chars};
      line.visit((style, content) -> {
         int take = Math.min(remaining[0], content.length());
         if (take > 0) {
            parts.add(FormattedText.of(content.substring(0, take), style));
            remaining[0] -= take;
         }

         return remaining[0] <= 0 ? Optional.of(Boolean.TRUE) : Optional.empty();
      }, Style.EMPTY);
      return FormattedText.composite(parts);
   }

   public static String plain(FormattedText text) {
      if (text == null) {
         return "";
      } else {
         StringBuilder sb = new StringBuilder();
         text.visit(part -> {
            sb.append(part);
            return Optional.empty();
         });
         return sb.toString();
      }
   }

   public static String plainStyled(FormattedText text) {
      if (text == null) {
         return "";
      } else {
         StringBuilder sb = new StringBuilder();
         text.visit((style, part) -> {
            sb.append(part);
            return Optional.empty();
         }, Style.EMPTY);
         return sb.toString();
      }
   }

   public record Row(long seq, int lineIndex, String text) {
   }
}
