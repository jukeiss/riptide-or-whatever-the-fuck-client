package riptide.util.macro;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class CaptureListSelector {
   private CaptureListSelector() {
   }

   public static String filterError(CaptureListSelector.Filter mode, String text) {
      if (mode == CaptureListSelector.Filter.REGEX && text != null && !text.isBlank()) {
         try {
            Pattern.compile(text);
            return "";
         } catch (PatternSyntaxException var3) {
            return "Invalid filter regex";
         }
      } else {
         return "";
      }
   }

   public static List<String> filter(List<String> candidates, CaptureListSelector.Filter mode, String text) {
      if (candidates != null && !candidates.isEmpty()) {
         CaptureListSelector.Filter effective = mode == null ? CaptureListSelector.Filter.NONE : mode;
         if (effective != CaptureListSelector.Filter.NONE && text != null && !text.isBlank()) {
            if (effective == CaptureListSelector.Filter.REGEX) {
               Pattern pattern;
               try {
                  pattern = Pattern.compile(text);
               } catch (PatternSyntaxException var10) {
                  return List.of();
               }

               List<String> matched = new ArrayList<>();

               for (String candidate : candidates) {
                  if (candidate != null && pattern.matcher(candidate).find()) {
                     matched.add(candidate);
                  }
               }

               return matched;
            } else {
               String needle = text.trim().toLowerCase(Locale.ROOT);
               List<String> matched = new ArrayList<>();

               for (String candidatex : candidates) {
                  if (candidatex != null) {
                     String lower = candidatex.toLowerCase(Locale.ROOT);

                     boolean hit = switch (effective) {
                        case PREFIX -> lower.startsWith(needle);
                        case SUFFIX -> lower.endsWith(needle);
                        case CONTAINS -> lower.contains(needle);
                        case NOT_CONTAINS -> !lower.contains(needle);
                        default -> true;
                     };
                     if (hit) {
                        matched.add(candidatex);
                     }
                  }
               }

               return matched;
            }
         } else {
            return candidates;
         }
      } else {
         return List.of();
      }
   }

   public static List<String> exclude(List<String> pool, String excludeText) {
      if (pool != null && !pool.isEmpty() && excludeText != null && !excludeText.isBlank()) {
         List<String> tokens = new ArrayList<>();

         for (String token : excludeText.split(",")) {
            String trimmed = token.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
               tokens.add(trimmed);
            }
         }

         if (tokens.isEmpty()) {
            return pool;
         } else {
            List<String> kept = new ArrayList<>(pool.size());

            for (String entry : pool) {
               if (entry != null) {
                  String lower = entry.toLowerCase(Locale.ROOT);
                  boolean excluded = false;

                  for (String tokenx : tokens) {
                     if (lower.contains(tokenx)) {
                        excluded = true;
                        break;
                     }
                  }

                  if (!excluded) {
                     kept.add(entry);
                  }
               }
            }

            return kept;
         }
      } else {
         return pool == null ? List.of() : pool;
      }
   }

   public static String stripMatch(String value, CaptureListSelector.Filter mode, String text) {
      if (value != null && text != null) {
         String match = text.trim();
         if (match.isEmpty()) {
            return value;
         } else if (mode == CaptureListSelector.Filter.PREFIX && value.regionMatches(true, 0, match, 0, match.length())) {
            return value.substring(match.length());
         } else {
            return mode == CaptureListSelector.Filter.SUFFIX
                  && value.length() >= match.length()
                  && value.regionMatches(true, value.length() - match.length(), match, 0, match.length())
               ? value.substring(0, value.length() - match.length())
               : value;
         }
      } else {
         return value;
      }
   }

   public static Optional<CaptureListSelector.Pick> pick(List<String> pool, CaptureListSelector.Selection selection, CaptureListSelector.State state) {
      return pick(pool, selection, 1, state);
   }

   public static Optional<CaptureListSelector.Pick> pick(
      List<String> pool, CaptureListSelector.Selection selection, int position, CaptureListSelector.State state
   ) {
      if (pool != null && !pool.isEmpty()) {
         CaptureListSelector.Selection effective = selection == null ? CaptureListSelector.Selection.RANDOM : selection;

         int index = switch (effective) {
            case RANDOM -> state == null ? 0 : ThreadLocalRandom.current().nextInt(pool.size());
            case SEQUENTIAL -> {
               if (state == null) {
                  yield 0;
               } else {
                  int next = Math.floorMod(state.cursor, pool.size());
                  state.cursor = next + 1;
                  yield next;
               }
            }
            case RANDOM_NO_REPEAT -> {
               if (state == null) {
                  yield 0;
               } else {
                  List<Integer> unused = new ArrayList<>();

                  for (int i = 0; i < pool.size(); i++) {
                     if (!state.used.contains(lowered(pool.get(i)))) {
                        unused.add(i);
                     }
                  }

                  if (unused.isEmpty()) {
                     state.used.clear();

                     for (int i = 0; i < pool.size(); i++) {
                        unused.add(i);
                     }
                  }

                  int picked = unused.get(ThreadLocalRandom.current().nextInt(unused.size()));
                  state.used.add(lowered(pool.get(picked)));
                  yield picked;
               }
            }
            case FIRST -> 0;
            case LAST -> pool.size() - 1;
            case POSITION -> Math.max(1, Math.min(position, pool.size())) - 1;
         };
         return Optional.of(new CaptureListSelector.Pick(pool.get(index), index, pool));
      } else {
         return Optional.empty();
      }
   }

   private static String lowered(String value) {
      return value == null ? "" : value.toLowerCase(Locale.ROOT);
   }

   public static enum Filter {
      NONE,
      PREFIX,
      SUFFIX,
      CONTAINS,
      NOT_CONTAINS,
      REGEX;
   }

   public record Pick(String value, int index, List<String> pool) {
   }

   public static enum Selection {
      RANDOM,
      SEQUENTIAL,
      RANDOM_NO_REPEAT,
      FIRST,
      LAST,
      POSITION;
   }

   public static final class State {
      int cursor;
      final Set<String> used = new HashSet<>();
   }
}
