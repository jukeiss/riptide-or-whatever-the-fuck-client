package riptide.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import riptide.api.module.BoolSetting;
import riptide.mixin.accessor.RiptideMovePlayerPacketAccessor;
import riptide.mixin.accessor.RiptideMultiPlayerGameModeAccessor;

public final class AntiHungerModule extends Module {
   private static final Minecraft MC = Minecraft.getInstance();

   public AntiHungerModule() {
      super("anti-hunger", "AntiHunger", ModuleCategory.MISC, "Prevents hunger from decreasing. Will flag anticheats.");
      this.add(new BoolSetting("no-sprint", "NoSprint", true).description("Hide sprint from server."));
      this.add(new BoolSetting("sprint-while-swimming", "While Swimming", false).description("Keep sprinting while swimming."));
      this.add(new BoolSetting("keep-floating", "KeepFloating", true).description("Spoof onGround as false."));
   }

   boolean noSprintActive() {
      return this.bool("no-sprint") && (!MC.player.isSwimming() || this.bool("sprint-while-swimming"));
   }

   public static boolean noSprintRequested() {
      return ModuleRegistry.get("anti-hunger") instanceof AntiHungerModule antiHunger
            && antiHunger.isEnabled()
            && MC.player != null
            && antiHunger.noSprintActive()
         ? true
         : ModuleRegistry.get("flight") instanceof BuiltinModules.FlightModule flightModule && flightModule.isEnabled() && flightModule.antiHungerToggle();
   }

   @Override
   public boolean onPacketSend(Packet<?> packet) {
      if (MC.player != null && MC.getConnection() != null) {
         if (packet instanceof ServerboundMovePlayerPacket move) {
            if (!this.bool("keep-floating") || MC.player.isPassenger() || isDestroyingBlock()) {
               return false;
            }

            if (MC.player.isInWater() || MC.player.isSwimming() || MC.player.isUnderWater()) {
               return false;
            }

            if (move.isOnGround() && MC.player.fallDistance <= 0.0) {
               ((RiptideMovePlayerPacketAccessor)move).riptide$setOnGround(false);
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean isDestroyingBlock() {
      return MC.gameMode instanceof RiptideMultiPlayerGameModeAccessor accessor && accessor.riptide$isDestroying();
   }
}
