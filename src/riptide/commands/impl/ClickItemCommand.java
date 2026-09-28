package riptide.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.commands.args.VisibleItemNameArgumentType;
import riptide.util.RiptideClientMessaging;
import riptide.util.macro.ItemTarget;

public final class ClickItemCommand extends Command {
   public ClickItemCommand() {
      super("click-item", "Click an item by its visible in-game name.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(context -> {
         RiptideClientMessaging.sendPrefixed("§eUsage: §f" + RiptideCommands.effectivePrefix() + "click-item \"<item name>\" [click] [times]");
         return 1;
      });
      RequiredArgumentBuilder<RiptideCommandSource, String> item = (RequiredArgumentBuilder<RiptideCommandSource, String>)RequiredArgumentBuilder.argument(
            "item-name", VisibleItemNameArgumentType.itemName()
         )
         .executes(context -> click(context, ItemClickCommandSupport.ClickSpec.click("Left", ContainerInput.PICKUP, 0), 1));
      ItemClickCommandSupport.attachModes(item, ClickItemCommand::click);
      root.then(item);
   }

   private static int click(CommandContext<RiptideCommandSource> context, ItemClickCommandSupport.ClickSpec spec, int times) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.player.containerMenu != null) {
         String requestedName = VisibleItemNameArgumentType.get(context, "item-name");
         int handlerSlot = findByVisibleName(mc.player.containerMenu, requestedName);
         if (handlerSlot < 0) {
            RiptideClientMessaging.sendPrefixed("§cNo visible item named §f" + requestedName + "§c exists in the current GUI.");
            return 1;
         } else {
            return ItemClickCommandSupport.clickHandlerSlot(handlerSlot, spec, times, "\"" + requestedName + "\"");
         }
      } else {
         RiptideClientMessaging.sendPrefixed("§cNo active inventory or GUI.");
         return 1;
      }
   }

   private static int findByVisibleName(AbstractContainerMenu menu, String requestedName) {
      String normalizedTarget = ItemTarget.normalize(requestedName);
      if (normalizedTarget.isEmpty()) {
         return -1;
      } else {
         for (int handlerSlot = 0; handlerSlot < menu.slots.size(); handlerSlot++) {
            ItemStack stack = ((Slot)menu.slots.get(handlerSlot)).getItem();
            if (!stack.isEmpty() && normalizedTarget.equals(ItemTarget.normalize(stack.getHoverName().getString()))) {
               return handlerSlot;
            }
         }

         return -1;
      }
   }
}
