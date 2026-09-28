package riptide.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.util.RiptideClientMessaging;

public class ClearCommand extends Command {
   public ClearCommand() {
      super("clear", "Clear every message from the chat.", "cls");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         Minecraft mc = Minecraft.getInstance();
         if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            try {
               mc.gui.hud.getChat().clearMessages(false);
               mc.gui.hud.getChat().resetChatScroll();
            } catch (Throwable var3) {
               RiptideClientMessaging.sendPrefixed("§cClear failed: " + var3.getMessage());
            }

            return 1;
         } else {
            RiptideClientMessaging.sendPrefixed("§cChat unavailable.");
            return 1;
         }
      });
   }
}
