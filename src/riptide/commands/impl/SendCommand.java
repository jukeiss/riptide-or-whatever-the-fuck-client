package riptide.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.commands.RiptideCommands;
import riptide.commands.args.PacketClassArgumentType;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptidePacketArgumentBuilder;
import riptide.util.RiptidePacketRegistry;
import riptide.util.RiptideSharedState;

public class SendCommand extends Command {
   public SendCommand() {
      super("send", "Send a raw C2S packet by class name with optional field=value arguments.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         RiptideClientMessaging.sendPrefixed("§eUsage: §f" + RiptideCommands.effectivePrefix() + "send <packetName> [field=value ...]");
         RiptideClientMessaging.sendPrefixed("§7Tip: press Tab after the space to search C2S packet classes.");
         RiptideClientMessaging.sendPrefixed("§7Example: §f" + RiptideCommands.effectivePrefix() + "send ServerboundSetCarriedItemPacket slot=0");
         return 1;
      });
      root.then(
         ((RequiredArgumentBuilder)RequiredArgumentBuilder.argument("packet", PacketClassArgumentType.packetClass())
               .executes(ctx -> this.send(PacketClassArgumentType.get(ctx, "packet"), "")))
            .then(RequiredArgumentBuilder.argument("args", StringArgumentType.greedyString()).suggests((ctx, builder) -> {
               Class<? extends Packet<?>> cls = RiptidePacketRegistry.getPacket(PacketClassArgumentType.get(ctx, "packet"));
               return RiptidePacketArgumentBuilder.suggest(cls, builder);
            }).executes(ctx -> this.send(PacketClassArgumentType.get(ctx, "packet"), StringArgumentType.getString(ctx, "args"))))
      );
   }

   private int send(String name, String args) {
      Class<? extends Packet<?>> cls = RiptidePacketRegistry.getPacket(name);
      if (cls == null) {
         RiptideClientMessaging.sendPrefixed("§cUnknown packet: §f" + name);
         return 1;
      } else if (!RiptidePacketRegistry.getC2SPackets().contains(cls)) {
         RiptideClientMessaging.sendPrefixed("§cRefusing to send non-C2S packet: §f" + name);
         return 1;
      } else {
         RiptidePacketArgumentBuilder.PreparedArgs prepared;
         try {
            prepared = RiptidePacketArgumentBuilder.prepare(args);
         } catch (IllegalArgumentException var7) {
            RiptideClientMessaging.sendPrefixed("§cBad arguments: " + var7.getMessage());
            return 1;
         }

         RiptidePacketArgumentBuilder.Result result = RiptidePacketArgumentBuilder.build(cls, prepared.args());
         if (result.help()) {
            sendLines("§e", result.message());
            return 1;
         } else if (!result.ok()) {
            sendLines("§c", "Failed to build §f" + name + "§c: " + result.message());
            return 1;
         } else if (prepared.dryRun()) {
            RiptideClientMessaging.sendPrefixed("§eBuilt §f" + name + "§7 (" + result.source() + ") §e- dry run, not sent.");
            return 1;
         } else {
            ClientPacketListener conn = Minecraft.getInstance().getConnection();
            if (conn == null) {
               RiptideClientMessaging.sendPrefixed("§cNo network connection.");
               return 1;
            } else {
               RiptideSharedState.get().sendPacketBypassDelay(conn, result.packet());
               RiptideClientMessaging.sendPrefixed("§aSent §f" + name + "§7 (" + result.source() + ")");
               return 1;
            }
         }
      }
   }

   private static void sendLines(String color, String message) {
      if (message != null && !message.isBlank()) {
         for (String line : message.split("\\R")) {
            if (!line.isBlank()) {
               RiptideClientMessaging.sendPrefixed(color + line);
            }
         }
      }
   }
}
