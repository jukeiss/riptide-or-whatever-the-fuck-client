package riptide.util.worldgen.mc26_2;

import java.util.Objects;
import java.util.function.Supplier;

public final class RiptideOreSimGenerationScope {
   private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

   private RiptideOreSimGenerationScope() {
   }

   public static boolean isActive() {
      Integer depth = DEPTH.get();
      return depth != null && depth > 0;
   }

   public static <T> T call(Supplier<T> operation) {
      Objects.requireNonNull(operation, "operation");
      Integer previous = DEPTH.get();
      DEPTH.set(previous == null ? 1 : previous + 1);

      Object var2;
      try {
         var2 = operation.get();
      } finally {
         if (previous == null) {
            DEPTH.remove();
         } else {
            DEPTH.set(previous);
         }
      }

      return (T)var2;
   }
}
