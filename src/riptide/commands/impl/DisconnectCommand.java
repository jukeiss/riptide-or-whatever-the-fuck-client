package riptide.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.util.RiptideClientMessaging;

public class DisconnectCommand extends Command {
   public DisconnectCommand() {
      super("disconnect", "Disconnect from the current server.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         Minecraft mc = Minecraft.getInstance();
         if (mc.getConnection() == null) {
            RiptideClientMessaging.sendPrefixed("§cNot connected.");
            return 1;
         } else {
            try {
               mc.getConnection().getConnection().disconnect(Component.literal("Disconnected via .disconnect"));
            } catch (Throwable var3) {
               RiptideClientMessaging.sendPrefixed("§cDisconnect failed: " + var3.getMessage());
            }

            return 1;
         }
      });
   }
}
