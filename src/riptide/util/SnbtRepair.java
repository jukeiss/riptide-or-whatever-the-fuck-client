package riptide.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class SnbtRepair {
   private SnbtRepair() {
   }

   public static String repair(String raw) {
      if (raw != null && !raw.isBlank()) {
         try {
            return repairTokens(tokenize(raw));
         } catch (Throwable var2) {
            return raw.trim();
         }
      } else {
         return "{}";
      }
   }

   private static String repairTokens(List<SnbtRepair.Tok> in) {
      int deficit = 0;

      for (SnbtRepair.Tok t : in) {
         deficit += t.kind == SnbtRepair.Kind.OPEN ? 1 : (t.kind == SnbtRepair.Kind.CLOSE ? -1 : 0);
      }

      List<SnbtRepair.Tok> out = new ArrayList<>(in.size() + 8);
      ArrayDeque<SnbtRepair.Tok> open = new ArrayDeque<>();
      SnbtRepair.Tok prev = null;

      for (SnbtRepair.Tok t : in) {
         if (deficit > 0 && t.kind != SnbtRepair.Kind.CLOSE && prev != null && t.line > prev.line) {
            while (deficit > 0 && !open.isEmpty() && open.peek().line < t.line && t.lineIndent <= open.peek().lineIndent) {
               insertCloser(out, open.pop());
               deficit--;
            }

            prev = last(out);
         }

         switch (t.kind) {
            case OPEN:
               maybeComma(out, prev, t);
               open.push(t);
               out.add(t);
               break;
            case CLOSE:
               if (open.isEmpty()) {
                  continue;
               }

               dropTrailingComma(out);
               SnbtRepair.Tok opener = open.pop();
               out.add(new SnbtRepair.Tok(SnbtRepair.Kind.CLOSE, opener.text.equals("{") ? "}" : "]", t.line, t.lineIndent));
               break;
            case COMMA:
               SnbtRepair.Tok lastTok = last(out);
               if (lastTok == null
                  || lastTok.kind == SnbtRepair.Kind.OPEN
                  || lastTok.kind == SnbtRepair.Kind.COMMA
                  || lastTok.kind == SnbtRepair.Kind.COLON
                  || lastTok.kind == SnbtRepair.Kind.SEMI) {
                  continue;
               }

               out.add(t);
               break;
            default:
               maybeComma(out, prev, t);
               out.add(t);
         }

         prev = last(out);
      }

      dropTrailingComma(out);

      while (!open.isEmpty()) {
         dropTrailingComma(out);
         insertCloser(out, open.pop());
      }

      StringBuilder sb = new StringBuilder();

      for (SnbtRepair.Tok t : out) {
         sb.append(t.text);
      }

      return sb.isEmpty() ? "{}" : sb.toString();
   }

   private static void maybeComma(List<SnbtRepair.Tok> out, SnbtRepair.Tok prev, SnbtRepair.Tok t) {
      if (prev != null) {
         boolean prevEnds = prev.kind == SnbtRepair.Kind.ATOM || prev.kind == SnbtRepair.Kind.STRING || prev.kind == SnbtRepair.Kind.CLOSE;
         boolean starts = t.kind == SnbtRepair.Kind.ATOM || t.kind == SnbtRepair.Kind.STRING || t.kind == SnbtRepair.Kind.OPEN;
         if (prevEnds && starts) {
            out.add(new SnbtRepair.Tok(SnbtRepair.Kind.COMMA, ",", t.line, t.lineIndent));
         }
      }
   }

   private static void insertCloser(List<SnbtRepair.Tok> out, SnbtRepair.Tok opener) {
      SnbtRepair.Tok trailing = null;
      if (!out.isEmpty() && out.get(out.size() - 1).kind == SnbtRepair.Kind.COMMA) {
         trailing = out.remove(out.size() - 1);
      }

      out.add(new SnbtRepair.Tok(SnbtRepair.Kind.CLOSE, opener.text.equals("{") ? "}" : "]", opener.line, opener.lineIndent));
      if (trailing != null) {
         out.add(trailing);
      }
   }

   private static void dropTrailingComma(List<SnbtRepair.Tok> out) {
      if (!out.isEmpty() && out.get(out.size() - 1).kind == SnbtRepair.Kind.COMMA) {
         out.remove(out.size() - 1);
      }
   }

   private static SnbtRepair.Tok last(List<SnbtRepair.Tok> out) {
      return out.isEmpty() ? null : out.get(out.size() - 1);
   }

   private static List<SnbtRepair.Tok> tokenize(String s) {
      List<SnbtRepair.Tok> out = new ArrayList<>();
      int line = 0;
      int lineIndent = 0;
      boolean measuring = true;

      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         if (c == '\n') {
            line++;
            lineIndent = 0;
            measuring = true;
         } else {
            if (measuring) {
               if (c == ' ') {
                  lineIndent++;
                  continue;
               }

               if (c == '\t') {
                  lineIndent += 4;
                  continue;
               }

               measuring = false;
            }

            if (!Character.isWhitespace(c)) {
               if (c != '"' && c != '\'') {
                  int j;
                  switch (c) {
                     case ',':
                        out.add(new SnbtRepair.Tok(SnbtRepair.Kind.COMMA, ",", line, lineIndent));
                        continue;
                     case ':':
                        out.add(new SnbtRepair.Tok(SnbtRepair.Kind.COLON, ":", line, lineIndent));
                        continue;
                     case ';':
                        out.add(new SnbtRepair.Tok(SnbtRepair.Kind.SEMI, ";", line, lineIndent));
                        continue;
                     case '[':
                     case '{':
                        out.add(new SnbtRepair.Tok(SnbtRepair.Kind.OPEN, String.valueOf(c), line, lineIndent));
                        continue;
                     case ']':
                     case '}':
                        out.add(new SnbtRepair.Tok(SnbtRepair.Kind.CLOSE, String.valueOf(c), line, lineIndent));
                        continue;
                     default:
                        j = i;
                  }

                  while (j < s.length() && !Character.isWhitespace(s.charAt(j)) && "{}[],:;\"'".indexOf(s.charAt(j)) < 0) {
                     j++;
                  }

                  out.add(new SnbtRepair.Tok(SnbtRepair.Kind.ATOM, s.substring(i, j), line, lineIndent));
                  i = j - 1;
               } else {
                  int j = i + 1;

                  for (boolean esc = false; j < s.length(); j++) {
                     char d = s.charAt(j);
                     if (esc) {
                        esc = false;
                     } else if (d == '\\') {
                        esc = true;
                     } else if (d == c) {
                        break;
                     }

                     if (d == '\n') {
                        line++;
                     }
                  }

                  String body = j < s.length() ? s.substring(i, j + 1) : s.substring(i) + c;
                  out.add(new SnbtRepair.Tok(SnbtRepair.Kind.STRING, body, line, lineIndent));
                  i = Math.min(j, s.length() - 1);
               }
            }
         }
      }

      return out;
   }

   private static enum Kind {
      OPEN,
      CLOSE,
      COMMA,
      COLON,
      SEMI,
      ATOM,
      STRING;
   }

   private record Tok(SnbtRepair.Kind kind, String text, int line, int lineIndent) {
   }
}
