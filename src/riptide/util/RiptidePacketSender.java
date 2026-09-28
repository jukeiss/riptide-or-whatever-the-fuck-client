package riptide.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import riptide.modules.PackHideState;
import riptide.security.RiptideProtector;

public class RiptidePacketSender {
   public static void send(Packet<?> packet) {
      if (!PackHideState.isHardLocked()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.getConnection() != null) {
            RiptideProtector.markUserBypass(packet);
            mc.getConnection().send(packet);
         }
      }
   }

   public static void sendPacketDirect(Packet<?> packet) {
      if (!PackHideState.isHardLocked()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.getConnection() != null) {
            RiptideProtector.markUserBypass(packet);
            mc.getConnection().send(packet);
         }
      }
   }
}
