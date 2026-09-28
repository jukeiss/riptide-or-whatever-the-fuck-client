package riptide.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.PrimitiveIterator.OfInt;

public final class RiptideBookPayloadBuilder {
   public static final int MAX_PAGES = 100;
   public static final int DEFAULT_PAGES = 100;
   public static final int DEFAULT_CHARS_PER_PAGE = 1024;
   public static final String RANDOM_ASCII = "Ascii";
   public static final String RANDOM_UTF8 = "Utf8";
   public static final String RANDOM_PAPERMC = "PaperMC";

   private RiptideBookPayloadBuilder() {
   }

   public static List<String> randomPages(int requestedPages, int requestedCharsPerPage, String randomType, Random random) {
      String type = normalizeRandomType(randomType);
      if ("PaperMC".equals(type)) {
         return paperMcPages(random);
      } else {
         int pages = Math.max(1, Math.min(100, requestedPages));
         int charsPerPage = Math.max(1, Math.min(1024, requestedCharsPerPage));
         Random rng = random == null ? new Random() : random;
         OfInt chars = "Ascii".equals(type)
            ? rng.ints(33, 128).filter(RiptideBookPayloadBuilder::validBookCodepoint).iterator()
            : rng.ints(33, 55296).filter(RiptideBookPayloadBuilder::validBookCodepoint).iterator();
         List<String> out = new ArrayList<>(pages);

         for (int page = 0; page < pages; page++) {
            StringBuilder builder = new StringBuilder(charsPerPage);

            for (int i = 0; i < charsPerPage && chars.hasNext(); i++) {
               builder.appendCodePoint(chars.nextInt());
            }

            out.add(builder.toString());
         }

         return out;
      }
   }

   public static String normalizeRandomType(String value) {
      if (value == null) {
         return "Utf8";
      } else {
         String var1 = value.trim().toLowerCase(Locale.ROOT);

         return switch (var1) {
            case "ascii" -> "Ascii";
            case "papermc", "paper" -> "PaperMC";
            default -> "Utf8";
         };
      }
   }

   private static List<String> paperMcPages(Random random) {
      Random rng = random == null ? new Random() : random;
      List<String> pages = new ArrayList<>(100);
      StringBuilder page = new StringBuilder(1024);
      OfInt oneByte = rng.ints(33, 128).filter(RiptideBookPayloadBuilder::validBookCodepoint).iterator();
      OfInt twoBytes = rng.ints(128, 2048).filter(RiptideBookPayloadBuilder::validBookCodepoint).iterator();
      OfInt threeBytes = rng.ints(2048, 55296).filter(RiptideBookPayloadBuilder::validBookCodepoint).iterator();

      for (int pageIndex = 0; pageIndex < 100; pageIndex++) {
         if (pageIndex < 50) {
            page.appendCodePoint(threeBytes.nextInt());

            for (int i = 1; i < 1024; i++) {
               page.appendCodePoint(oneByte.nextInt());
            }
         } else if (pageIndex != 50) {
            for (int i = 0; i < 1024; i++) {
               page.appendCodePoint(threeBytes.nextInt());
            }
         } else {
            for (int i = 0; i < 110; i++) {
               page.appendCodePoint(threeBytes.nextInt());
            }

            page.appendCodePoint(twoBytes.nextInt());

            for (int i = 0; i < 913; i++) {
               page.appendCodePoint(oneByte.nextInt());
            }
         }

         pages.add(page.toString());
         page.setLength(0);
      }

      return pages;
   }

   public static int estimateUtf8Bytes(List<String> pages) {
      if (pages != null && !pages.isEmpty()) {
         int bytes = 0;

         for (String page : pages) {
            if (page != null && !page.isEmpty()) {
               for (int i = 0; i < page.length(); i++) {
                  char c = page.charAt(i);
                  if (c < 128) {
                     bytes++;
                  } else if (c < 2048) {
                     bytes += 2;
                  } else {
                     bytes += 3;
                  }
               }
            }
         }

         return bytes;
      } else {
         return 0;
      }
   }

   private static boolean validBookCodepoint(int value) {
      return !Character.isWhitespace(value) && value != 13 && value != 10;
   }
}
