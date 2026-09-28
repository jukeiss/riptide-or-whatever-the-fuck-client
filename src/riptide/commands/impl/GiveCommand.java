package riptide.commands.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import riptide.commands.Command;
import riptide.commands.CommandSuggest;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.util.RiptideClientMessaging;
import riptide.util.multi.MultiPilot;

public class GiveCommand extends Command {
   public GiveCommand() {
      super("give", "Give item to self (creative only).");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         RiptideClientMessaging.sendPrefixed("§eUsage: " + RiptideCommands.effectivePrefix() + "give <item> [count]");
         return 1;
      });
      root.then(
         ((RequiredArgumentBuilder)RequiredArgumentBuilder.argument("item", StringArgumentType.word())
               .suggests(CommandSuggest::itemIds)
               .executes(ctx -> give(StringArgumentType.getString(ctx, "item"), 1)))
            .then(
               RequiredArgumentBuilder.argument("count", IntegerArgumentType.integer(1, 64))
                  .suggests(CommandSuggest::counts)
                  .executes(ctx -> give(StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count")))
            )
      );
   }

   private static int give(String itemId, int count) {
      Minecraft mc = Minecraft.getInstance();
      boolean pov = MultiPilot.isActive();
      if (!pov && mc.player == null) {
         RiptideClientMessaging.sendPrefixed("§cNot in a world.");
         return 1;
      } else if (!pov && !mc.player.getAbilities().instabuild) {
         RiptideClientMessaging.sendPrefixed("§cCreative mode required.");
         return 1;
      } else {
         Identifier id = itemId.contains(":") ? Identifier.tryParse(itemId) : Identifier.tryParse("minecraft:" + itemId);
         if (id == null) {
            RiptideClientMessaging.sendPrefixed("§cInvalid item id: §f" + itemId);
            return 1;
         } else {
            Item item = (Item)BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item == null) {
               RiptideClientMessaging.sendPrefixed("§cUnknown item: §f" + id);
               return 1;
            } else {
               ItemStack stack = new ItemStack(item, count);
               String povResult = MultiPilot.commandGiveCreative(stack);
               if (povResult != null) {
                  RiptideClientMessaging.sendPrefixed(("Sent".equals(povResult) ? "§a" : "§c") + "POV give: §f" + povResult);
                  return 1;
               } else {
                  int slot = mc.player.getInventory().getSelectedSlot();
                  int containerSlot = 36 + slot;
                  mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket((short)containerSlot, stack));
                  mc.player.getInventory().setItem(slot, stack);
                  RiptideClientMessaging.sendPrefixed("§aGave §f" + id + " x" + count);
                  return 1;
               }
            }
         }
      }
   }
}
