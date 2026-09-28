package riptide.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundChangeGameModePacket;
import net.minecraft.world.level.GameType;

public final class RiptideGamemode {
   private RiptideGamemode() {
   }

   public static RiptideFakeGamemode.Result real(GameType mode) {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null || mc.player == null || mc.player.connection == null) {
         return RiptideFakeGamemode.Result.fail("Not connected.");
      } else if (mode == null) {
         return RiptideFakeGamemode.Result.fail("Unknown game mode.");
      } else {
         boolean clearedFake = RiptideFakeGamemode.snapshot().fakeActive();
         if (clearedFake) {
            RiptideFakeGamemode.reset();
         }

         mc.player.connection.send(new ServerboundChangeGameModePacket(mode));
         return RiptideFakeGamemode.Result.ok("Requested real gamemode: " + mode.getName() + (clearedFake ? " (fake cleared)" : ""));
      }
   }
}
