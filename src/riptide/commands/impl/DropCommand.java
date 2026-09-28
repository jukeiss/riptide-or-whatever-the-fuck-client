package riptide.commands.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import riptide.commands.Command;
import riptide.commands.CommandSuggest;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideDropHelper;
import riptide.util.RiptideInventoryHelper;

public class DropCommand extends Command {
   public DropCommand() {
      super("drop", "Drop hand, full inventory, or an amount of a held/specific item.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         showUsage();
         return 1;
      });
      root.then(LiteralArgumentBuilder.literal("hand").executes(ctx -> dropHand()));
      root.then(LiteralArgumentBuilder.literal("fullinventory").executes(ctx -> dropFullInventory()));
      root.then(
         ((RequiredArgumentBuilder)RequiredArgumentBuilder.argument("amount", IntegerArgumentType.integer(1))
               .suggests(CommandSuggest::counts)
               .executes(ctx -> dropHeldAmount(IntegerArgumentType.getInteger(ctx, "amount"))))
            .then(
               RequiredArgumentBuilder.argument("item", StringArgumentType.word())
                  .suggests(CommandSuggest::itemIds)
                  .executes(ctx -> dropItemAmount(IntegerArgumentType.getInteger(ctx, "amount"), StringArgumentType.getString(ctx, "item")))
            )
      );
   }

   private static void showUsage() {
      String prefix = RiptideCommands.effectivePrefix();
      RiptideClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "drop hand");
      RiptideClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "drop fullinventory");
      RiptideClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "drop <amount> [item_id]");
   }

   private static int dropHand() {
      Minecraft mc = Minecraft.getInstance();
      if (!ready(mc)) {
         return 1;
      } else {
         ItemStack held = mc.player.getMainHandItem();
         if (held.isEmpty()) {
            RiptideClientMessaging.sendPrefixed("§cYou are not holding an item.");
            return 1;
         } else {
            int amount = held.getCount();
            Identifier id = BuiltInRegistries.ITEM.getKey(held.getItem());
            mc.player.drop(true);
            RiptideClientMessaging.sendPrefixed("§aDropped §f" + amount + "x " + id + "§a.");
            return 1;
         }
      }
   }

   private static int dropFullInventory() {
      Minecraft mc = Minecraft.getInstance();
      if (!ready(mc)) {
         return 1;
      } else {
         int droppedItems = 0;
         int droppedStacks = 0;

         for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack stack = mc.player.getInventory().getItem(inventorySlot);
            if (!stack.isEmpty()) {
               int handlerSlot = RiptideInventoryHelper.toHandlerSlot(mc, inventorySlot);
               if (handlerSlot >= 0) {
                  int stackCount = stack.getCount();
                  if (RiptideDropHelper.dropFromHandlerSlot(mc, handlerSlot, 0) > 0) {
                     droppedItems += stackCount;
                     droppedStacks++;
                  }
               }
            }
         }

         if (droppedStacks == 0) {
            RiptideClientMessaging.sendPrefixed("§eYour inventory is empty.");
         } else {
            RiptideClientMessaging.sendPrefixed("§aDropped §f" + droppedItems + " items §7(" + droppedStacks + " stacks§7)§a.");
         }

         return 1;
      }
   }

   private static int dropHeldAmount(int requested) {
      Minecraft mc = Minecraft.getInstance();
      if (!ready(mc)) {
         return 1;
      } else {
         int selectedSlot = mc.player.getInventory().getSelectedSlot();
         ItemStack held = mc.player.getInventory().getItem(selectedSlot);
         if (held.isEmpty()) {
            RiptideClientMessaging.sendPrefixed("§cYou are not holding an item.");
            return 1;
         } else {
            Identifier id = BuiltInRegistries.ITEM.getKey(held.getItem());
            return dropResolvedItemAmount(mc, requested, id, held.getItem());
         }
      }
   }

   private static int dropItemAmount(int requested, String rawItemId) {
      Minecraft mc = Minecraft.getInstance();
      if (!ready(mc)) {
         return 1;
      } else {
         Identifier id = parseItemId(rawItemId);
         Item item = id == null ? null : (Item)BuiltInRegistries.ITEM.getOptional(id).orElse(null);
         if (item == null) {
            RiptideClientMessaging.sendPrefixed("§cUnknown item id: §f" + rawItemId);
            return 1;
         } else {
            return dropResolvedItemAmount(mc, requested, id, item);
         }
      }
   }

   private static int dropResolvedItemAmount(Minecraft mc, int requested, Identifier id, Item item) {
      List<Integer> matchingSlots = matchingInventorySlots(mc, item);
      int available = 0;

      for (int slot : matchingSlots) {
         available += mc.player.getInventory().getItem(slot).getCount();
      }

      if (available == 0) {
         RiptideClientMessaging.sendPrefixed("§cYou do not have §f" + id + "§c in your inventory.");
         return 1;
      } else {
         int remaining = Math.min(requested, available);
         int dropped = 0;

         for (int inventorySlot : matchingSlots) {
            if (remaining <= 0) {
               break;
            }

            ItemStack stack = mc.player.getInventory().getItem(inventorySlot);
            if (!stack.isEmpty() && stack.getItem() == item) {
               int amount = Math.min(remaining, stack.getCount());
               int handlerSlot = RiptideInventoryHelper.toHandlerSlot(mc, inventorySlot);
               if (handlerSlot >= 0 && RiptideDropHelper.dropFromHandlerSlot(mc, handlerSlot, amount) != 0) {
                  dropped += amount;
                  remaining -= amount;
               }
            }
         }

         if (dropped == 0) {
            RiptideClientMessaging.sendPrefixed("§cCould not drop §f" + id + "§c from the current screen.");
         } else {
            reportDrop(id, dropped, requested);
         }

         return 1;
      }
   }

   private static List<Integer> matchingInventorySlots(Minecraft mc, Item item) {
      List<Integer> slots = new ArrayList<>();
      int selected = mc.player.getInventory().getSelectedSlot();
      addIfMatching(mc, item, selected, slots);

      for (int slot = 0; slot < 9; slot++) {
         if (slot != selected) {
            addIfMatching(mc, item, slot, slots);
         }
      }

      for (int slotx = 9; slotx < 36; slotx++) {
         addIfMatching(mc, item, slotx, slots);
      }

      return slots;
   }

   private static void addIfMatching(Minecraft mc, Item item, int slot, List<Integer> slots) {
      ItemStack stack = mc.player.getInventory().getItem(slot);
      if (!stack.isEmpty() && stack.getItem() == item) {
         slots.add(slot);
      }
   }

   private static Identifier parseItemId(String raw) {
      if (raw != null && !raw.isBlank()) {
         String value = raw.trim().toLowerCase(Locale.ROOT);
         return Identifier.tryParse(value.indexOf(58) >= 0 ? value : "minecraft:" + value);
      } else {
         return null;
      }
   }

   private static void reportDrop(Identifier id, int dropped, int requested) {
      if (dropped < requested) {
         RiptideClientMessaging.sendPrefixed("§eDropped §f" + dropped + "/" + requested + "x " + id + "§e (all available).");
      } else {
         RiptideClientMessaging.sendPrefixed("§aDropped §f" + dropped + "x " + id + "§a.");
      }
   }

   private static boolean ready(Minecraft mc) {
      if (mc.player != null && mc.gameMode != null) {
         return true;
      } else {
         RiptideClientMessaging.sendPrefixed("§cNot in a world.");
         return false;
      }
   }
}
