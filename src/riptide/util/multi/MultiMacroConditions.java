package riptide.util.multi;

import java.util.Locale;
import java.util.Map;
import riptide.util.macro.MacroCondition;
import riptide.util.macro.WaitDurabilityAction;

final class MultiMacroConditions {
   private MultiMacroConditions() {
   }

   static boolean evaluate(MacroCondition c, MultiMacroHost h, Map<String, String> vars) {
      if (c == null) {
         return true;
      } else {
         boolean result = c.nodeType == MacroCondition.NodeType.GROUP ? group(c, h, vars) : leaf(c, h, vars);
         return c.negate != result;
      }
   }

   private static boolean group(MacroCondition c, MultiMacroHost h, Map<String, String> vars) {
      if (c.children.isEmpty()) {
         return true;
      } else if (c.combine == MacroCondition.Combine.ANY) {
         for (MacroCondition child : c.children) {
            if (evaluate(child, h, vars)) {
               return true;
            }
         }

         return false;
      } else {
         for (MacroCondition childx : c.children) {
            if (!evaluate(childx, h, vars)) {
               return false;
            }
         }

         return true;
      }
   }

   private static boolean leaf(MacroCondition c, MultiMacroHost h, Map<String, String> vars) {
      String item = c.item == null ? "" : c.item;

      try {
         return switch (c.kind) {
            case ALWAYS -> true;
            case HELD_ITEM -> matchesName(h.heldItemName(), item, c.op);
            case INVENTORY_ITEM -> h.countItem(item) > 0;
            case ITEM_COUNT -> cmp(h.countItem(item), c.amount, c.cmp);
            case SLOT_EMPTY -> !h.slotFilled(c.slot);
            case SLOT_FILLED -> h.slotFilled(c.slot);
            case INVENTORY_FULL -> h.freeSlots() <= 0;
            case INVENTORY_EMPTY -> h.countItem("") <= 0;
            case CURSOR_EMPTY -> h.cursorEmpty();
            case CURSOR_FILLED -> !h.cursorEmpty();
            case CURSOR_MATCHES -> matchesName(h.cursorName(), item, c.op);
            case SELECTED_SLOT -> cmp(h.selectedHotbar(), c.slot, c.cmp);
            case FREE_SLOTS -> cmp(h.freeSlots(), c.amount, c.cmp);
            case GUI_OPEN -> !item.isBlank() && !item.equalsIgnoreCase("ANY") ? matchesName(h.openScreenTitle(), item, c.op) : h.containerOpen();
            case HEALTH -> cmp(h.health(), c.amount, c.cmp);
            case CONNECTION -> h.macroReady();
            case VARIABLE -> variable(c, vars);
            case ENTITY_NEARBY, ENTITY_TARGET, LOOKING_AT_ENTITY, LOOKING_AT_CONTAINER_ENTITY -> h.fullMode() && h.nearestEntity(item) >= 0;
            case DURABILITY -> durabilityLeaf(c, h);
            case HAS_BUNDLE -> h.countItem("bundle") > 0;
            case HAS_WRITABLE_BOOK -> h.countItem("writable_book") > 0;
            case LOOKING_AT_BLOCK, MOUNTED_ENTITY, BUNDLE_V2_READY -> false;
         };
      } catch (RuntimeException var5) {
         return false;
      }
   }

   private static boolean durabilityLeaf(MacroCondition c, MultiMacroHost h) {
      WaitDurabilityAction w = new WaitDurabilityAction();
      w.targetMode = c.durMode;
      w.itemName = c.item == null ? "" : c.item;
      w.slot = c.slot;
      w.measurement = c.durMeasure;
      w.comparison = durCmp(c.cmp);
      w.value = c.amount;
      return MultiMacroRun.durabilityMet(w, h);
   }

   private static WaitDurabilityAction.Comparison durCmp(MacroCondition.Cmp cmp) {
      return switch (cmp == null ? MacroCondition.Cmp.AT_LEAST : cmp) {
         case BELOW -> WaitDurabilityAction.Comparison.BELOW;
         case AT_MOST -> WaitDurabilityAction.Comparison.AT_MOST;
         case EXACT -> WaitDurabilityAction.Comparison.EXACT;
         case AT_LEAST -> WaitDurabilityAction.Comparison.AT_LEAST;
         case ABOVE -> WaitDurabilityAction.Comparison.ABOVE;
      };
   }

   static boolean cmp(double value, double amount, MacroCondition.Cmp cmp) {
      return switch (cmp == null ? MacroCondition.Cmp.AT_LEAST : cmp) {
         case BELOW -> value < amount;
         case AT_MOST -> value <= amount;
         case EXACT -> value == amount;
         case AT_LEAST -> value >= amount;
         case ABOVE -> value > amount;
      };
   }

   private static boolean matchesName(String actual, String expected, MacroCondition.Op op) {
      String a = normalizeItem(actual);
      String e = normalizeItem(stripId(expected));

      return switch (op == null ? MacroCondition.Op.EQ : op) {
         case NEQ -> {
            boolean var14 = !a.equals(e);
            yield var14;
         }
         case CONTAINS -> {
            boolean var13 = a.contains(e);
            yield var13;
         }
         case STARTS_WITH -> {
            boolean var12 = a.startsWith(e);
            yield var12;
         }
         case ENDS_WITH -> {
            boolean var11 = a.endsWith(e);
            yield var11;
         }
         case IS_EMPTY -> {
            boolean var10 = a.isEmpty();
            yield var10;
         }
         case REGEX -> {
            boolean var9;
            try {
               var9 = a.matches(expected == null ? "" : expected);
            } catch (RuntimeException var7) {
               var9 = false;
               yield var9;
            }

            yield var9;
         }
         default -> {
            boolean var5 = a.equals(e) || !e.isEmpty() && a.contains(e);
            yield var5;
         }
      };
   }

   private static boolean variable(MacroCondition c, Map<String, String> vars) {
      String left = vars == null ? "" : vars.getOrDefault(stripBraces(c.item), "");
      String right = c.varB == null ? "" : c.varB;

      return switch (c.op == null ? MacroCondition.Op.EQ : c.op) {
         case NEQ -> {
            boolean var15 = !left.equals(right);
            yield var15;
         }
         case CONTAINS -> {
            boolean var14 = left.contains(right);
            yield var14;
         }
         case STARTS_WITH -> {
            boolean var13 = left.startsWith(right);
            yield var13;
         }
         case ENDS_WITH -> {
            boolean var12 = left.endsWith(right);
            yield var12;
         }
         case IS_EMPTY -> {
            boolean var11 = left.isEmpty();
            yield var11;
         }
         case REGEX -> {
            boolean var10;
            try {
               var10 = left.matches(right);
            } catch (RuntimeException var6) {
               var10 = false;
               yield var10;
            }

            yield var10;
         }
         case IS_TRUE -> {
            boolean var8 = left.equalsIgnoreCase("true") || left.equals("1");
            yield var8;
         }
         case LT, LE, GT, GE -> {
            boolean var7 = numberCompare(left, right, c.op);
            yield var7;
         }
         case EQ -> {
            boolean var4 = left.equals(right);
            yield var4;
         }
      };
   }

   private static boolean numberCompare(String left, String right, MacroCondition.Op op) {
      try {
         double l = Double.parseDouble(left.trim());
         double r = Double.parseDouble(right.trim());

         return switch (op) {
            case LT -> l < r;
            case LE -> l <= r;
            case GT -> l > r;
            case GE -> l >= r;
            default -> false;
         };
      } catch (NumberFormatException var7) {
         return false;
      }
   }

   private static String stripId(String s) {
      if (s == null) {
         return "";
      } else {
         int colon = s.indexOf(58);
         return colon >= 0 ? s.substring(colon + 1) : s;
      }
   }

   private static String normalizeItem(String s) {
      return s == null ? "" : s.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
   }

   private static String stripBraces(String s) {
      if (s == null) {
         return "";
      } else {
         String t = s.trim();
         return t.startsWith("{") && t.endsWith("}") && t.length() >= 2 ? t.substring(1, t.length() - 1) : t;
      }
   }
}
