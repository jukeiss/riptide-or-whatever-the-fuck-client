package riptide.commands.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import riptide.commands.Command;
import riptide.commands.CommandSuggest;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideInventoryHelper;

public final class ChangeSlotCommand extends Command {
   public ChangeSlotCommand() {
      super("change-slot", "Select hotbar slot 1-9.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(context -> {
         RiptideClientMessaging.sendPrefixed("§eUsage: §f" + RiptideCommands.effectivePrefix() + "change-slot <1-9>");
         return 1;
      });
      root.then(
         RequiredArgumentBuilder.argument("hotbar-slot", IntegerArgumentType.integer(1, 9))
            .suggests((context, builder) -> CommandSuggest.literals(builder, "1", "2", "3", "4", "5", "6", "7", "8", "9"))
            .executes(context -> changeSlot(IntegerArgumentType.getInteger(context, "hotbar-slot")))
      );
   }

   private static int changeSlot(int oneBasedSlot) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.getConnection() != null) {
         RiptideInventoryHelper.selectHotbarSlot(mc, oneBasedSlot - 1);
         RiptideClientMessaging.sendPrefixed("§aSelected hotbar slot §f" + oneBasedSlot + "§a.");
         return 1;
      } else {
         RiptideClientMessaging.sendPrefixed("§cNot in a world.");
         return 1;
      }
   }
}
