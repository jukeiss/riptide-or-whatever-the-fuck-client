package riptide.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

public final class RiptideCommandSource {
   public static final RiptideCommandSource INSTANCE = new RiptideCommandSource();

   private RiptideCommandSource() {
   }

   public Minecraft mc() {
      return Minecraft.getInstance();
   }

   public LocalPlayer player() {
      return Minecraft.getInstance().player;
   }

   public ClientPacketListener connection() {
      return Minecraft.getInstance().getConnection();
   }

   public boolean hasPlayer() {
      return Minecraft.getInstance().player != null;
   }

   public boolean hasConnection() {
      return Minecraft.getInstance().getConnection() != null;
   }
}
