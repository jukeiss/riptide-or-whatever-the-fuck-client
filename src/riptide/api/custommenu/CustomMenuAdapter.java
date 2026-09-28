package riptide.api.custommenu;

import net.minecraft.network.protocol.Packet;

public interface CustomMenuAdapter {
   String id();

   default boolean acceptsInbound(Packet<?> packet) {
      return true;
   }

   CustomMenuEvent inspectInbound(Packet<?> var1, String var2);

   CustomMenuSubmitResult submit(CustomMenuSnapshot var1, CustomMenuSubmission var2);
}
