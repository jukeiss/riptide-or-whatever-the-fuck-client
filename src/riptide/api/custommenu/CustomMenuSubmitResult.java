package riptide.api.custommenu;

import java.util.List;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.protocol.Packet;

public record CustomMenuSubmitResult(boolean success, String error, List<Packet<?>> packets, CustomMenuSnapshot replacement, ClickEvent clientAction) {
   public CustomMenuSubmitResult(boolean success, String error, List<Packet<?>> packets, CustomMenuSnapshot replacement, ClickEvent clientAction) {
      error = error == null ? "" : error;
      packets = packets == null ? List.of() : List.copyOf(packets);
      this.success = success;
      this.error = error;
      this.packets = packets;
      this.replacement = replacement;
      this.clientAction = clientAction;
   }

   public static CustomMenuSubmitResult failure(String error) {
      return new CustomMenuSubmitResult(false, error, List.of(), null, null);
   }

   public static CustomMenuSubmitResult packets(List<Packet<?>> packets) {
      return new CustomMenuSubmitResult(true, "", packets, null, null);
   }

   public static CustomMenuSubmitResult replacement(CustomMenuSnapshot replacement, ClickEvent clientAction) {
      return new CustomMenuSubmitResult(true, "", List.of(), replacement, clientAction);
   }
}
