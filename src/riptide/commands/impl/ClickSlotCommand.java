package riptide.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.commands.args.MenuSlotArgumentType;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideInventoryHelper;

public final class ClickSlotCommand extends Command {
   public ClickSlotCommand() {
      super("click-slot", "Click a GUI/player slot with Item Click actions.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(context -> {
         RiptideClientMessaging.sendPrefixed("§eUsage: §f" + RiptideCommands.effectivePrefix() + "click-slot <slot> [click] [times]");
         return 1;
      });
      RequiredArgumentBuilder<RiptideCommandSource, Integer> slot = (RequiredArgumentBuilder<RiptideCommandSource, Integer>)RequiredArgumentBuilder.argument(
            "slot", MenuSlotArgumentType.slot()
         )
         .executes(context -> click(context, ItemClickCommandSupport.ClickSpec.click("Left", ContainerInput.PICKUP, 0), 1));
      ItemClickCommandSupport.attachModes(slot, ClickSlotCommand::click);
      root.then(slot);
   }

   private static int click(CommandContext<RiptideCommandSource> context, ItemClickCommandSupport.ClickSpec spec, int times) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.player.containerMenu != null) {
         int visibleSlot = MenuSlotArgumentType.get(context, "slot");
         int handlerSlot = RiptideInventoryHelper.toHandlerSlot(mc, visibleSlot);
         return ItemClickCommandSupport.clickHandlerSlot(handlerSlot, spec, times, MenuSlotArgumentType.displayToken(visibleSlot));
      } else {
         RiptideClientMessaging.sendPrefixed("§cNo active inventory or GUI.");
         return 1;
      }
   }
}
