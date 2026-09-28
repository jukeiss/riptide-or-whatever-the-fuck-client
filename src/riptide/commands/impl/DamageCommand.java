package riptide.commands.impl;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import riptide.commands.Command;
import riptide.commands.CommandSuggest;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.util.RiptideClientMessaging;

public class DamageCommand extends Command {
   public DamageCommand() {
      super("damage", "Take damage to self (vanilla server allows via /damage if op).");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         RiptideClientMessaging.sendPrefixed("§eUsage: " + RiptideCommands.effectivePrefix() + "damage <amount>");
         return 1;
      });
      root.then(RequiredArgumentBuilder.argument("amount", DoubleArgumentType.doubleArg(0.1, 1024.0)).suggests(CommandSuggest::damage).executes(ctx -> {
         double amt = DoubleArgumentType.getDouble(ctx, "amount");
         ClientPacketListener conn = Minecraft.getInstance().getConnection();
         if (conn == null) {
            RiptideClientMessaging.sendPrefixed("§cNot connected.");
            return 1;
         } else {
            conn.sendCommand("damage @s " + amt);
            return 1;
         }
      }));
   }
}
