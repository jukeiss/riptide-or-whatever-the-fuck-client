package riptide.util.multi;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import riptide.util.RiptidePacketRegistry;

public final class MultiManualPackets {
   private static final Set<Class<?>> SAFE = Set.of(
      ServerboundChatCommandPacket.class,
      ServerboundClientCommandPacket.class,
      ServerboundCustomPayloadPacket.class,
      Pos.class,
      PosRot.class,
      Rot.class,
      StatusOnly.class,
      ServerboundPlayerInputPacket.class,
      ServerboundSetCarriedItemPacket.class,
      ServerboundSwingPacket.class
   );

   private MultiManualPackets() {
   }

   public static boolean isSafe(Class<? extends Packet<?>> packetClass) {
      return packetClass != null && SAFE.contains(packetClass);
   }

   public static Set<Class<? extends Packet<?>>> unsafeC2S() {
      Set<Class<? extends Packet<?>>> out = new LinkedHashSet<>();

      for (Class<? extends Packet<?>> packetClass : RiptidePacketRegistry.getC2SPackets()) {
         if (!isSafe(packetClass)) {
            out.add(packetClass);
         }
      }

      return out;
   }
}
