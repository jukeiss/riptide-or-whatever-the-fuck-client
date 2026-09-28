package riptide.util.macro;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class MacroCondition {
   public MacroCondition.NodeType nodeType = MacroCondition.NodeType.GROUP;
   public boolean negate = false;
   public MacroCondition.Combine combine = MacroCondition.Combine.ALL;
   public List<MacroCondition> children = new ArrayList<>();
   public MacroCondition.Kind kind = MacroCondition.Kind.ALWAYS;
   public String item = "";
   public String varB = "";
   public MacroCondition.Op op = MacroCondition.Op.EQ;
   public MacroCondition.Cmp cmp = MacroCondition.Cmp.AT_LEAST;
   public int amount = 0;
   public int slot = 0;
   public double range = 5.0;
   public WaitDurabilityAction.TargetMode durMode = WaitDurabilityAction.TargetMode.HELD;
   public WaitDurabilityAction.Measurement durMeasure = WaitDurabilityAction.Measurement.PERCENT_REMAINING;
   public WaitFreeSlotsAction.CountMode freeMode = WaitFreeSlotsAction.CountMode.FREE_SLOTS;

   public static MacroCondition group(MacroCondition.Combine c) {
      MacroCondition m = new MacroCondition();
      m.nodeType = MacroCondition.NodeType.GROUP;
      m.combine = c;
      return m;
   }

   public static MacroCondition leaf(MacroCondition.Kind k) {
      MacroCondition m = new MacroCondition();
      m.nodeType = MacroCondition.NodeType.LEAF;
      m.kind = k;
      return m;
   }

   public static MacroCondition defaultRoot() {
      MacroCondition root = group(MacroCondition.Combine.ALL);
      root.children.add(leaf(MacroCondition.Kind.ALWAYS));
      return root;
   }

   public MacroCondition copy() {
      return fromTag(this.toTag());
   }

   public MacroCondition simpleLeafOrNull() {
      if (this.nodeType == MacroCondition.NodeType.LEAF) {
         return this;
      } else {
         return !this.negate
               && this.nodeType == MacroCondition.NodeType.GROUP
               && this.children.size() == 1
               && this.children.get(0).nodeType == MacroCondition.NodeType.LEAF
            ? this.children.get(0)
            : null;
      }
   }

   public void writeFlat(CompoundTag t, String p) {
      MacroCondition leaf = this.simpleLeafOrNull();
      if (leaf != null) {
         t.putString(p + "kind", leaf.kind.name());
         t.putBoolean(p + "negate", leaf.negate);
         t.putString(p + "item", leaf.item == null ? "" : leaf.item);
         t.putString(p + "op", leaf.op.name());
         t.putString(p + "value", leaf.varB == null ? "" : leaf.varB);
         t.putString(p + "cmp", leaf.cmp.name());
         t.putInt(p + "amount", leaf.amount);
         t.putInt(p + "slot", leaf.slot);
      }
   }

   public static MacroCondition readForEditor(CompoundTag tag, String nestedKey, String flatPrefix) {
      MacroCondition nested = tag.get(nestedKey) instanceof CompoundTag c ? fromTag(c) : null;
      boolean nestedSimple = nested == null || nested.simpleLeafOrNull() != null;
      if (nestedSimple && tag.contains(flatPrefix + "kind")) {
         return readFlatLeaf(tag, flatPrefix);
      } else {
         return nested != null ? nested : defaultRoot();
      }
   }

   public static MacroCondition readFlatLeaf(CompoundTag t, String p) {
      MacroCondition leaf = leaf(MacroCondition.Kind.ALWAYS);
      leaf.kind = MacroStringList.enumValue(MacroCondition.Kind.class, t.getStringOr(p + "kind", "ALWAYS"), MacroCondition.Kind.ALWAYS);
      leaf.negate = t.getBooleanOr(p + "negate", false);
      leaf.item = t.getStringOr(p + "item", "");
      leaf.op = MacroStringList.enumValue(MacroCondition.Op.class, t.getStringOr(p + "op", "EQ"), MacroCondition.Op.EQ);
      leaf.varB = t.getStringOr(p + "value", "");
      leaf.cmp = MacroStringList.enumValue(MacroCondition.Cmp.class, t.getStringOr(p + "cmp", "AT_LEAST"), MacroCondition.Cmp.AT_LEAST);
      leaf.amount = t.getIntOr(p + "amount", 0);
      leaf.slot = t.getIntOr(p + "slot", 0);
      MacroCondition root = group(MacroCondition.Combine.ALL);
      root.children.add(leaf);
      return root;
   }

   public boolean isAlways() {
      if (this.negate) {
         return false;
      } else if (this.nodeType == MacroCondition.NodeType.LEAF) {
         return this.kind == MacroCondition.Kind.ALWAYS;
      } else if (this.children.isEmpty()) {
         return true;
      } else {
         for (MacroCondition c : this.children) {
            if (!c.isAlways()) {
               return false;
            }
         }

         return true;
      }
   }

   public boolean evaluate(Minecraft mc) {
      boolean result = this.nodeType == MacroCondition.NodeType.GROUP ? this.evaluateGroup(mc) : this.evaluateLeaf(mc);
      return this.negate != result;
   }

   private boolean evaluateGroup(Minecraft mc) {
      if (this.children.isEmpty()) {
         return true;
      } else if (this.combine == MacroCondition.Combine.ANY) {
         for (MacroCondition c : this.children) {
            if (c.evaluate(mc)) {
               return true;
            }
         }

         return false;
      } else {
         for (MacroCondition cx : this.children) {
            if (!cx.evaluate(mc)) {
               return false;
            }
         }

         return true;
      }
   }

   private boolean evaluateLeaf(Minecraft mc) {
      if (mc == null) {
         return false;
      } else {
         try {
            return switch (this.kind) {
               case ALWAYS -> true;
               case HELD_ITEM -> this.assertCheck(mc, AssertAction.CheckType.HELD_ITEM);
               case INVENTORY_ITEM -> this.assertCheck(mc, AssertAction.CheckType.INVENTORY_ITEM);
               case ITEM_COUNT -> this.compareNum(this.invCount(mc), this.amount);
               case SLOT_EMPTY -> this.invPredicate(mc, WaitInventoryPredicateAction.InventoryCondition.SLOT_EMPTY);
               case SLOT_FILLED -> this.invPredicate(mc, WaitInventoryPredicateAction.InventoryCondition.SLOT_FILLED);
               case INVENTORY_FULL -> this.invPredicate(mc, WaitInventoryPredicateAction.InventoryCondition.INVENTORY_FULL);
               case INVENTORY_EMPTY -> this.invPredicate(mc, WaitInventoryPredicateAction.InventoryCondition.INVENTORY_EMPTY);
               case CURSOR_EMPTY -> this.invPredicate(mc, WaitInventoryPredicateAction.InventoryCondition.CURSOR_EMPTY);
               case CURSOR_FILLED -> this.invPredicate(mc, WaitInventoryPredicateAction.InventoryCondition.CURSOR_FILLED);
               case CURSOR_MATCHES -> this.invPredicate(mc, WaitInventoryPredicateAction.InventoryCondition.CURSOR_MATCHES);
               case SELECTED_SLOT -> this.invPredicate(mc, WaitInventoryPredicateAction.InventoryCondition.SELECTED_SLOT);
               case FREE_SLOTS -> this.freeSlots(mc);
               case DURABILITY -> this.durability(mc);
               case GUI_OPEN -> this.assertCheck(mc, AssertAction.CheckType.GUI_TYPE);
               case LOOKING_AT_ENTITY -> this.assertCheck(mc, AssertAction.CheckType.LOOKING_AT_ENTITY);
               case LOOKING_AT_CONTAINER_ENTITY -> this.assertCheck(mc, AssertAction.CheckType.LOOKING_AT_CONTAINER_ENTITY);
               case MOUNTED_ENTITY -> this.assertCheck(mc, AssertAction.CheckType.MOUNTED_ENTITY);
               case ENTITY_NEARBY -> this.entityNearby(mc);
               case LOOKING_AT_BLOCK -> this.assertCheck(mc, AssertAction.CheckType.LOOKING_AT_BLOCK);
               case ENTITY_TARGET -> mc.crosshairPickEntity != null;
               case CONNECTION -> this.assertCheck(mc, AssertAction.CheckType.CONNECTION);
               case HAS_BUNDLE -> this.assertCheck(mc, AssertAction.CheckType.HAS_BUNDLE);
               case BUNDLE_V2_READY -> this.assertCheck(mc, AssertAction.CheckType.BUNDLE_V2_READY);
               case HAS_WRITABLE_BOOK -> this.assertCheck(mc, AssertAction.CheckType.HAS_WRITABLE_BOOK);
               case HEALTH -> mc.player != null && this.compareNum(mc.player.getHealth(), this.amount);
               case VARIABLE -> this.variable(mc);
            };
         } catch (Throwable var3) {
            return false;
         }
      }
   }

   private boolean assertCheck(Minecraft mc, AssertAction.CheckType type) {
      AssertAction a = new AssertAction();
      a.check = type;
      a.itemName = this.item == null ? "" : this.item;
      a.entityId = this.item == null ? "" : this.item;
      a.guiType = this.item != null && !this.item.isBlank() ? this.item : "ANY";
      return a.passes(mc);
   }

   private boolean invPredicate(Minecraft mc, WaitInventoryPredicateAction.InventoryCondition cond) {
      WaitInventoryPredicateAction w = new WaitInventoryPredicateAction();
      w.condition = cond;
      w.itemName = this.item == null ? "" : this.item;
      w.count = this.amount;
      w.slot = this.slot;
      return w.matches(mc);
   }

   private int invCount(Minecraft mc) {
      MacroTemplate.Resolution r = MacroVariables.resolve(this.item == null ? "" : this.item, mc);
      return !r.success() ? 0 : WaitInventoryPredicateAction.countInventory(mc, ItemTarget.fromLegacyEntry(r.value()));
   }

   private boolean freeSlots(Minecraft mc) {
      WaitFreeSlotsAction w = new WaitFreeSlotsAction();
      w.countMode = this.freeMode;
      w.comparison = this.freeCmp();
      w.slots = this.amount;
      return w.matches(mc);
   }

   private boolean durability(Minecraft mc) {
      WaitDurabilityAction w = new WaitDurabilityAction();
      w.targetMode = this.durMode;
      w.itemName = this.item == null ? "" : this.item;
      w.slot = this.slot;
      w.measurement = this.durMeasure;
      w.comparison = this.durCmp();
      w.value = this.amount;
      return w.matches(mc);
   }

   private boolean entityNearby(Minecraft mc) {
      WaitEntityTargetAction w = new WaitEntityTargetAction();
      w.condition = WaitEntityTargetAction.EntityCondition.NEARBY;
      w.entityId = this.item == null ? "" : this.item;
      w.range = this.range;
      return w.matches(mc);
   }

   private boolean variable(Minecraft mc) {
      String a = resolveText(this.item, mc);
      if (this.op == MacroCondition.Op.IS_EMPTY) {
         return a.isBlank();
      } else if (this.op == MacroCondition.Op.IS_TRUE) {
         return parseTrue(a);
      } else {
         String b = resolveText(this.varB, mc);
         Double da = tryNum(a);
         Double db = tryNum(b);
         if (da != null && db != null) {
            double x = da;
            double y = db;

            return switch (this.op) {
               case EQ -> x == y;
               case NEQ -> x != y;
               case LT -> x < y;
               case LE -> x <= y;
               case GT -> x > y;
               case GE -> x >= y;
               case CONTAINS -> a.contains(b);
               case STARTS_WITH -> a.startsWith(b);
               case ENDS_WITH -> a.endsWith(b);
               case REGEX -> a.matches(b);
               default -> false;
            };
         } else {
            return switch (this.op) {
               case EQ -> a.equalsIgnoreCase(b);
               case NEQ -> !a.equalsIgnoreCase(b);
               case LT -> a.compareTo(b) < 0;
               case LE -> a.compareTo(b) <= 0;
               case GT -> a.compareTo(b) > 0;
               case GE -> a.compareTo(b) >= 0;
               case CONTAINS -> a.toLowerCase().contains(b.toLowerCase());
               case STARTS_WITH -> a.toLowerCase().startsWith(b.toLowerCase());
               case ENDS_WITH -> a.toLowerCase().endsWith(b.toLowerCase());
               case REGEX -> a.matches(b);
               default -> false;
            };
         }
      }
   }

   private boolean compareNum(double actual, double target) {
      return switch (this.cmp) {
         case BELOW -> actual < target;
         case AT_MOST -> actual <= target;
         case EXACT -> actual == target;
         case AT_LEAST -> actual >= target;
         case ABOVE -> actual > target;
      };
   }

   private WaitDurabilityAction.Comparison durCmp() {
      return switch (this.cmp) {
         case BELOW -> WaitDurabilityAction.Comparison.BELOW;
         case AT_MOST -> WaitDurabilityAction.Comparison.AT_MOST;
         case EXACT -> WaitDurabilityAction.Comparison.EXACT;
         case AT_LEAST -> WaitDurabilityAction.Comparison.AT_LEAST;
         case ABOVE -> WaitDurabilityAction.Comparison.ABOVE;
      };
   }

   private WaitFreeSlotsAction.Comparison freeCmp() {
      return switch (this.cmp) {
         case BELOW -> WaitFreeSlotsAction.Comparison.BELOW;
         case AT_MOST -> WaitFreeSlotsAction.Comparison.AT_MOST;
         case EXACT -> WaitFreeSlotsAction.Comparison.EXACT;
         case AT_LEAST -> WaitFreeSlotsAction.Comparison.AT_LEAST;
         case ABOVE -> WaitFreeSlotsAction.Comparison.ABOVE;
      };
   }

   private static String resolveText(String template, Minecraft mc) {
      MacroTemplate.Resolution r = MacroVariables.resolve(template == null ? "" : template, mc);
      return r.success() ? r.value() : "";
   }

   private static Double tryNum(String s) {
      if (s != null && !s.isBlank()) {
         try {
            return Double.parseDouble(s.trim());
         } catch (NumberFormatException var2) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static boolean parseTrue(String s) {
      if (s == null) {
         return false;
      } else {
         String t = s.trim().toLowerCase(Locale.ROOT);
         return t.equals("true") || t.equals("1") || t.equals("yes") || t.equals("on");
      }
   }

   public String summary() {
      String s = this.nodeType == MacroCondition.NodeType.GROUP ? this.groupSummary() : this.leafSummary();
      return this.negate ? "NOT(" + s + ")" : s;
   }

   private String groupSummary() {
      if (this.children.isEmpty()) {
         return "always";
      } else {
         StringBuilder sb = new StringBuilder();
         String join = this.combine == MacroCondition.Combine.ANY ? " OR " : " AND ";

         for (int i = 0; i < this.children.size(); i++) {
            if (i > 0) {
               sb.append(join);
            }

            sb.append(this.children.get(i).summary());
         }

         return this.children.size() > 1 ? "(" + sb + ")" : sb.toString();
      }
   }

   private String leafSummary() {
      return switch (this.kind) {
         case ALWAYS -> "always";
         case HELD_ITEM -> "held=" + this.shortItem();
         case INVENTORY_ITEM -> "has " + this.shortItem();
         case ITEM_COUNT -> this.shortItem() + " " + this.cmpSym() + " " + this.amount;
         default -> this.kind.name().toLowerCase(Locale.ROOT);
         case FREE_SLOTS -> (this.freeMode == WaitFreeSlotsAction.CountMode.FILLED_SLOTS ? "filled" : "free") + " " + this.cmpSym() + " " + this.amount;
         case DURABILITY -> "dura " + this.cmpSym() + " " + this.amount;
         case GUI_OPEN -> "gui=" + (this.item.isBlank() ? "any" : this.item);
         case ENTITY_NEARBY -> "near " + (this.item.isBlank() ? "entity" : this.item);
         case HEALTH -> "hp " + this.cmpSym() + " " + this.amount;
         case VARIABLE -> this.compactVar();
      };
   }

   private String shortItem() {
      String s = this.item == null ? "" : this.item;
      int colon = s.indexOf(58);
      return colon >= 0 && colon + 1 < s.length() ? s.substring(colon + 1) : s;
   }

   private String compactVar() {
      String left = this.item == null ? "" : this.item;

      return switch (this.op) {
         case IS_EMPTY -> left + " empty";
         case IS_TRUE -> left + " true";
         default -> left + " " + this.opSym() + " " + this.varB;
      };
   }

   private String cmpSym() {
      return switch (this.cmp) {
         case BELOW -> "<";
         case AT_MOST -> "<=";
         case EXACT -> "=";
         case AT_LEAST -> ">=";
         case ABOVE -> ">";
      };
   }

   private String opSym() {
      return switch (this.op) {
         case EQ -> "=";
         case NEQ -> "!=";
         case LT -> "<";
         case LE -> "<=";
         case GT -> ">";
         case GE -> ">=";
         case CONTAINS -> "has";
         case STARTS_WITH -> "starts";
         case ENDS_WITH -> "ends";
         case REGEX -> "regex";
         case IS_EMPTY -> "empty";
         case IS_TRUE -> "true";
      };
   }

   public CompoundTag toTag() {
      CompoundTag t = new CompoundTag();
      t.putString("node", this.nodeType.name());
      t.putBoolean("negate", this.negate);
      if (this.nodeType == MacroCondition.NodeType.GROUP) {
         t.putString("combine", this.combine.name());
         ListTag list = new ListTag();

         for (MacroCondition c : this.children) {
            if (c != null) {
               list.add(c.toTag());
            }
         }

         t.put("children", list);
      } else {
         t.putString("kind", this.kind.name());
         t.putString("item", this.item == null ? "" : this.item);
         t.putString("varB", this.varB == null ? "" : this.varB);
         t.putString("op", this.op.name());
         t.putString("cmp", this.cmp.name());
         t.putInt("amount", this.amount);
         t.putInt("slot", this.slot);
         t.putDouble("range", this.range);
         t.putString("durMode", this.durMode.name());
         t.putString("durMeasure", this.durMeasure.name());
         t.putString("freeMode", this.freeMode.name());
      }

      return t;
   }

   public static MacroCondition fromTag(CompoundTag t) {
      MacroCondition m = new MacroCondition();
      if (t == null) {
         return defaultRoot();
      } else {
         m.nodeType = MacroStringList.enumValue(MacroCondition.NodeType.class, t.getStringOr("node", "GROUP"), MacroCondition.NodeType.GROUP);
         m.negate = t.getBooleanOr("negate", false);
         if (m.nodeType == MacroCondition.NodeType.GROUP) {
            m.combine = MacroStringList.enumValue(MacroCondition.Combine.class, t.getStringOr("combine", "ALL"), MacroCondition.Combine.ALL);
            m.children = new ArrayList<>();
            Tag var3 = t.get("children");
            if (var3 instanceof ListTag) {
               for (Tag el : (ListTag)var3) {
                  if (el instanceof CompoundTag ct) {
                     m.children.add(fromTag(ct));
                  }
               }
            }
         } else {
            m.kind = MacroStringList.enumValue(MacroCondition.Kind.class, t.getStringOr("kind", "ALWAYS"), MacroCondition.Kind.ALWAYS);
            m.item = t.getStringOr("item", "");
            m.varB = t.getStringOr("varB", "");
            m.op = MacroStringList.enumValue(MacroCondition.Op.class, t.getStringOr("op", "EQ"), MacroCondition.Op.EQ);
            m.cmp = MacroStringList.enumValue(MacroCondition.Cmp.class, t.getStringOr("cmp", "AT_LEAST"), MacroCondition.Cmp.AT_LEAST);
            m.amount = t.getIntOr("amount", 0);
            m.slot = t.getIntOr("slot", 0);
            m.range = t.getDoubleOr("range", 5.0);
            m.durMode = MacroStringList.enumValue(WaitDurabilityAction.TargetMode.class, t.getStringOr("durMode", "HELD"), WaitDurabilityAction.TargetMode.HELD);
            m.durMeasure = MacroStringList.enumValue(
               WaitDurabilityAction.Measurement.class, t.getStringOr("durMeasure", "PERCENT_REMAINING"), WaitDurabilityAction.Measurement.PERCENT_REMAINING
            );
            m.freeMode = MacroStringList.enumValue(
               WaitFreeSlotsAction.CountMode.class, t.getStringOr("freeMode", "FREE_SLOTS"), WaitFreeSlotsAction.CountMode.FREE_SLOTS
            );
         }

         return m;
      }
   }

   public static enum Cmp {
      BELOW,
      AT_MOST,
      EXACT,
      AT_LEAST,
      ABOVE;
   }

   public static enum Combine {
      ALL,
      ANY;
   }

   public static enum Kind {
      ALWAYS,
      HELD_ITEM,
      INVENTORY_ITEM,
      ITEM_COUNT,
      SLOT_EMPTY,
      SLOT_FILLED,
      INVENTORY_FULL,
      INVENTORY_EMPTY,
      CURSOR_EMPTY,
      CURSOR_FILLED,
      CURSOR_MATCHES,
      SELECTED_SLOT,
      FREE_SLOTS,
      DURABILITY,
      GUI_OPEN,
      LOOKING_AT_ENTITY,
      LOOKING_AT_CONTAINER_ENTITY,
      MOUNTED_ENTITY,
      ENTITY_NEARBY,
      LOOKING_AT_BLOCK,
      ENTITY_TARGET,
      CONNECTION,
      HAS_BUNDLE,
      BUNDLE_V2_READY,
      HAS_WRITABLE_BOOK,
      HEALTH,
      VARIABLE;
   }

   public static enum NodeType {
      GROUP,
      LEAF;
   }

   public static enum Op {
      EQ,
      NEQ,
      LT,
      LE,
      GT,
      GE,
      CONTAINS,
      STARTS_WITH,
      ENDS_WITH,
      REGEX,
      IS_EMPTY,
      IS_TRUE;
   }
}
