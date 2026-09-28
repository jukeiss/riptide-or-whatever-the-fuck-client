package riptide.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import riptide.commands.Command;
import riptide.commands.RiptideCommandSource;
import riptide.modules.PackFreecamState;
import riptide.util.RiptideClientMessaging;
import riptide.util.multi.MultiPilot;

public final class CopyPosCommand extends Command {
   public CopyPosCommand() {
      super("copypos", "Copies current coordinates.");
   }

   @Override
   public void build(LiteralArgumentBuilder<RiptideCommandSource> root) {
      root.executes(ctx -> {
         Minecraft mc = Minecraft.getInstance();
         if (PackFreecamState.isActive()) {
            String coordinates = format(PackFreecamState.footPosition(1.0F));
            if (mc != null && mc.keyboardHandler != null) {
               mc.keyboardHandler.setClipboard(coordinates);
            }

            RiptideClientMessaging.sendPrefixed("§aPosition copied (freecam): §f" + coordinates);
            return 1;
         } else {
            Player controlled = MultiPilot.commandPlayer();
            if (mc != null && controlled != null && mc.keyboardHandler != null) {
               Entity positionOwner = (Entity)(!MultiPilot.isActive() && controlled.getVehicle() != null ? controlled.getVehicle() : controlled);
               String coordinates = format(positionOwner.position());
               mc.keyboardHandler.setClipboard(coordinates);
               RiptideClientMessaging.sendPrefixed("§aPosition copied: §f" + coordinates);
               return 1;
            } else {
               RiptideClientMessaging.sendPrefixed("§cNo controlled position available.");
               return 1;
            }
         }
      });
   }

   static String format(Vec3 position) {
      return position == null ? "0 0 0" : floorCoordinate(position.x) + " " + safeFeetY(position.y) + " " + floorCoordinate(position.z);
   }

   private static int floorCoordinate(double value) {
      return Double.isFinite(value) ? (int)Math.floor(value) : 0;
   }

   private static int safeFeetY(double value) {
      return Double.isFinite(value) ? (int)Math.ceil(value - 1.0E-7) : 0;
   }
}
