package riptide.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.util.RiptideClientMessaging;

public class SayCommand extends Command {
   public SayCommand() {
      super("say", "Send a chat message.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         RiptideClientMessaging.sendPrefixed("§eUsage: " + RiptideCommands.effectivePrefix() + "say <message>");
         return 1;
      });
      root.then(RequiredArgumentBuilder.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
         String msg = StringArgumentType.getString(ctx, "message");
         ClientPacketListener conn = Minecraft.getInstance().getConnection();
         if (conn == null) {
            RiptideClientMessaging.sendPrefixed("§cNot connected.");
            return 1;
         } else {
            if (msg.startsWith("/")) {
               conn.sendCommand(msg.substring(1));
            } else {
               RiptideCommands.sendPlainChat(conn, msg);
            }

            return 1;
         }
      }));
   }
}
