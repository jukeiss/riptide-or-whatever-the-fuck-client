package riptide.mixin.compat;

import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import com.replaymod.replaystudio.protocol.Packet;
import com.replaymod.replaystudio.protocol.Packet.Reader;
import com.replaymod.replaystudio.protocol.packets.PacketTeam.Action;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(
   targets = {"com.replaymod.replaystudio.protocol.packets.PacketTeam"}
)
public abstract class RiptideReplayStudioTeamMixin {
   @Overwrite
   private static void skipTeamInfo(Packet packet, Reader in) throws IOException {
      in.readText();
      if (!packet.atLeast(ProtocolVersion.v1_13)) {
         in.readString();
         in.readString();
         in.readByte();
      }

      if (packet.atLeast(ProtocolVersion.v1_13)) {
         in.readText();
         in.readText();
      }

      if (packet.atLeast(ProtocolVersion.v1_8)) {
         if (packet.atLeast(ProtocolVersion.v1_21_5)) {
            in.readVarInt();
            in.readVarInt();
         } else {
            in.readString();
            if (packet.atLeast(ProtocolVersion.v1_9)) {
               in.readString();
            }
         }

         if (packet.atLeast(ProtocolVersion.v1_13)) {
            if (packet.olderThan(ProtocolVersion.v26_2)) {
               in.readVarInt();
            } else if (in.readBoolean()) {
               in.readVarInt();
            }

            in.readByte();
         } else {
            in.readByte();
         }
      } else {
         in.readString();
         in.readByte();
      }
   }

   @Overwrite
   public static List<String> getPlayers(Packet packet) throws IOException {
      try {
         Reader in = packet.reader();

         List var9;
         label68: {
            Object var10;
            try {
               in.readString();
               Action action = Action.values()[in.readByte()];
               if (action != Action.CREATE && action != Action.ADD_PLAYER && action != Action.REMOVE_PLAYER) {
                  var9 = Collections.emptyList();
                  break label68;
               }

               if (action == Action.CREATE) {
                  skipTeamInfo(packet, in);
               }

               int count = packet.atLeast(ProtocolVersion.v1_8) ? in.readVarInt() : in.readShort();
               List<String> result = new ArrayList<>(count);

               for (int i = 0; i < count; i++) {
                  result.add(in.readString());
               }

               var10 = result;
            } catch (Throwable var7) {
               if (in != null) {
                  try {
                     in.close();
                  } catch (Throwable var6) {
                     var7.addSuppressed(var6);
                  }
               }

               throw var7;
            }

            if (in != null) {
               in.close();
            }

            return (List<String>)var10;
         }

         if (in != null) {
            in.close();
         }

         return var9;
      } catch (RuntimeException | IOException var8) {
         return Collections.emptyList();
      }
   }
}
