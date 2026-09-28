package riptide.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.util.RiptideClientMessaging;

public class DismountCommand extends Command {
   public DismountCommand() {
      super("dismount", "Force-dismount from any vehicle.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null || mc.getConnection() == null) {
            RiptideClientMessaging.sendPrefixed("§cNot in a world.");
            return 1;
         } else if (mc.player.getVehicle() == null) {
            RiptideClientMessaging.sendPrefixed("§eNot riding anything.");
            return 1;
         } else {
            try {
               mc.player.removeVehicle();
               RiptideClientMessaging.sendPrefixed("§aDismounted.");
            } catch (Throwable var3) {
               RiptideClientMessaging.sendPrefixed("§cDismount failed: " + var3.getMessage());
            }

            return 1;
         }
      });
   }
}
