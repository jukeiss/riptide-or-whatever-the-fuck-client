package riptide.util.macro;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import riptide.util.RiptideInventoryHelper;

public class SelectSlotAction implements MacroAction, PacketOrdered {
   public int slot = 0;
   public String itemName = "";
   public ItemTarget itemTarget = new ItemTarget();
   public SelectSlotAction.Strategy strategy = SelectSlotAction.Strategy.FIRST_MATCH;
   public String outputVariable = "";
   public volatile PacketOrder packetOrder = PacketOrder.INSTANT;

   @Override
   public void execute(Minecraft mc) {
      if (mc.player != null) {
         List<ItemTarget> targets = new ArrayList<>();

         for (ItemTarget candidate : this.resolvedTargets()) {
            ItemTarget resolved = candidate.resolveTemplate(mc);
            if (resolved != null) {
               targets.add(resolved);
            }
         }

         if (this.strategy == SelectSlotAction.Strategy.EMPTY_SLOT) {
            int empty = this.findEmptyHotbar(mc);
            if (empty >= 0) {
               RiptideInventoryHelper.selectHotbarSlot(mc, empty);
               MacroVariables.set(this.outputVariable, empty);
               return;
            }
         }

         if (!targets.isEmpty()) {
            if (this.strategy != SelectSlotAction.Strategy.FIRST_MATCH) {
               int best = this.findStrategicHotbarSlot(mc, targets);
               if (best >= 0) {
                  RiptideInventoryHelper.selectHotbarSlot(mc, best);
                  MacroVariables.set(this.outputVariable, best);
                  return;
               }
            }

            for (ItemTarget target : targets) {
               int selectedSlot = RiptideInventoryHelper.selectHotbarItem(mc, target, this.slot);
               if (selectedSlot >= 0) {
                  MacroVariables.set(this.outputVariable, selectedSlot);
                  return;
               }
            }
         }

         int actualSlot = Math.max(0, Math.min(8, this.slot));
         RiptideInventoryHelper.selectHotbarSlot(mc, actualSlot);
         MacroVariables.set(this.outputVariable, actualSlot);
      }
   }

   @Override
   public PacketOrder getPacketOrder() {
      return this.packetOrder;
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.SELECT_SLOT;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "SELECT_SLOT");
      tag.putInt("slot", this.slot);
      tag.putString("strategy", this.strategy.name());
      tag.putString("outputVariable", this.outputVariable);
      ItemTarget target = this.resolvedItemTarget();
      if (target.hasSlot() || target.hasIdentity()) {
         tag.put("itemName", target.toTag());
      }

      PacketOrdered.save(tag, this.packetOrder);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.slot = tag.getIntOr("slot", 0);
      this.strategy = MacroStringList.enumValue(
         SelectSlotAction.Strategy.class, tag.getStringOr("strategy", "FIRST_MATCH"), SelectSlotAction.Strategy.FIRST_MATCH
      );
      this.outputVariable = tag.getStringOr("outputVariable", "");
      this.itemTarget = tag.getCompound("itemName").map(ItemTarget::fromTag).orElseGet(() -> ItemTarget.fromLegacyEntry(tag.getStringOr("itemName", "")));
      this.itemName = this.itemTarget.toLegacyEntry();
      this.packetOrder = PacketOrdered.load(tag);
   }

   @Override
   public String getDisplayName() {
      ItemTarget target = this.resolvedItemTarget();
      return !target.hasSlot() && !target.hasIdentity() ? "Select Slot " + (this.slot + 1) : "Select " + this.strategy + " \"" + target.summaryText() + "\"";
   }

   @Override
   public String getIcon() {
      return "S";
   }

   private ItemTarget resolvedItemTarget() {
      if (this.itemTarget == null || !this.itemTarget.hasSlot() && !this.itemTarget.hasIdentity()) {
         this.itemTarget = ItemTarget.fromLegacyEntry(this.shortcut(this.itemName));
         return this.itemTarget;
      } else {
         return this.itemTarget;
      }
   }

   private String shortcut(String raw) {
      String key = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);

      return switch (key) {
         case "bundle" -> "minecraft:bundle";
         case "writable book", "book" -> "minecraft:writable_book";
         case "pickaxe" -> "#minecraft:pickaxes";
         case "trident or bow" -> "minecraft:trident|minecraft:bow";
         case "chest boat/minecart", "chest boat" -> "minecraft:chest_minecart";
         default -> raw;
      };
   }

   private int findEmptyHotbar(Minecraft mc) {
      for (int i = 0; i < 9; i++) {
         if (mc.player.getInventory().getItem(i).isEmpty()) {
            return i;
         }
      }

      return -1;
   }

   private List<ItemTarget> resolvedTargets() {
      String raw = this.shortcut(this.itemName);
      if (raw != null && !raw.isBlank()) {
         if (!raw.contains("|")) {
            ItemTarget target = this.resolvedItemTarget();
            return !target.hasSlot() && !target.hasIdentity() ? List.of() : List.of(target);
         } else {
            ArrayList<ItemTarget> out = new ArrayList<>();

            for (String part : raw.split("\\|")) {
               ItemTarget target = ItemTarget.fromLegacyEntry(part.trim());
               if (target.hasSlot() || target.hasIdentity()) {
                  out.add(target);
               }
            }

            return out;
         }
      } else {
         ItemTarget target = this.resolvedItemTarget();
         return !target.hasSlot() && !target.hasIdentity() ? List.of() : List.of(target);
      }
   }

   private int findStrategicHotbarSlot(Minecraft mc, List<ItemTarget> targets) {
      int bestSlot = -1;
      int bestMetric = this.strategy == SelectSlotAction.Strategy.WORST_DURABILITY ? Integer.MAX_VALUE : Integer.MIN_VALUE;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = mc.player.getInventory().getItem(i);
         boolean matches = false;

         for (ItemTarget target : targets) {
            if (target.score(stack, i) >= 0) {
               matches = true;
               break;
            }
         }

         if (matches) {
            int metric = switch (this.strategy) {
               case BEST_DURABILITY -> stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
               case WORST_DURABILITY -> stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
               case LARGEST_STACK -> stack.getCount();
               default -> 0;
            };
            if (this.strategy == SelectSlotAction.Strategy.WORST_DURABILITY && metric < bestMetric
               || this.strategy != SelectSlotAction.Strategy.WORST_DURABILITY && metric > bestMetric) {
               bestMetric = metric;
               bestSlot = i;
            }
         }
      }

      return bestSlot;
   }

   public static enum Strategy {
      FIRST_MATCH,
      BEST_DURABILITY,
      WORST_DURABILITY,
      LARGEST_STACK,
      EMPTY_SLOT;
   }
}
