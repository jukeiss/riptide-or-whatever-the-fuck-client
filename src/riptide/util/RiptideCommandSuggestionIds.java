package riptide.util;

import java.util.concurrent.atomic.AtomicInteger;

public final class RiptideCommandSuggestionIds {
   public static final int MACRO_FIRST_ID = 2000000000;
   private static final AtomicInteger NEXT_MACRO_ID = new AtomicInteger(2000000000);

   public static int nextMacroId() {
      return NEXT_MACRO_ID.getAndUpdate(id -> id >= 2147483646 ? 2000000000 : id + 1);
   }

   public static boolean isMacroId(int id) {
      return id >= 2000000000;
   }

   private RiptideCommandSuggestionIds() {
   }
}
