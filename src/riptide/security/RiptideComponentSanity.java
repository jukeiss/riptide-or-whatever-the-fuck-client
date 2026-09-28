package riptide.security;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.chat.numbers.NumberFormat;

public final class RiptideComponentSanity {
   private static final int MAX_DEPTH = 64;
   private static final int MAX_TOTAL_CHARS = 65536;
   private static final int MAX_STRING_CHARS = 16384;

   private RiptideComponentSanity() {
   }

   public static boolean isSafe(Component component) {
      if (component == null) {
         return true;
      } else {
         try {
            int[] charBudget = new int[1];
            if (!walk(component, 0, charBudget)) {
               return false;
            } else {
               component.getString();
               return true;
            }
         } catch (Throwable var2) {
            return false;
         }
      }
   }

   public static boolean isSafe(NumberFormat format) {
      if (format == null) {
         return true;
      } else {
         try {
            return format instanceof FixedFormat fixed ? isSafe(fixed.value()) : true;
         } catch (Throwable var2) {
            return false;
         }
      }
   }

   private static boolean walk(Component component, int depth, int[] charBudget) {
      if (depth > 64) {
         return false;
      } else {
         ComponentContents contents = component.getContents();
         if (contents instanceof PlainTextContents plain) {
            if (!budgetString(plain.text(), charBudget)) {
               return false;
            }
         } else if (contents instanceof TranslatableContents translatable) {
            if (!budgetString(translatable.getKey(), charBudget)) {
               return false;
            }

            if (translatable.getFallback() != null && !budgetString(translatable.getFallback(), charBudget)) {
               return false;
            }

            for (Object arg : translatable.getArgs()) {
               if (arg instanceof Component argComponent) {
                  if (!walk(argComponent, depth + 1, charBudget)) {
                     return false;
                  }
               } else if (arg != null && !budgetString(String.valueOf(arg), charBudget)) {
                  return false;
               }
            }
         }

         for (Component sibling : component.getSiblings()) {
            if (!walk(sibling, depth + 1, charBudget)) {
               return false;
            }
         }

         return true;
      }
   }

   private static boolean budgetString(String value, int[] charBudget) {
      if (value == null) {
         return true;
      } else if (value.length() > 16384) {
         return false;
      } else {
         charBudget[0] += value.length();
         return charBudget[0] <= 65536;
      }
   }
}
