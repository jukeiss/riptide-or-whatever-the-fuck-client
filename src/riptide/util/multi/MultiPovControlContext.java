package riptide.util.multi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import riptide.util.RiptideInventoryHelper;

final class MultiPovControlContext {
   private final Minecraft minecraft;
   private final Player player;
   private final MultiSession botSession;
   private final MultiPovControlContext.PacketDestination packetDestination;

   private MultiPovControlContext(Minecraft minecraft, Player player, MultiSession botSession, MultiPovControlContext.PacketDestination packetDestination) {
      this.minecraft = minecraft;
      this.player = player;
      this.botSession = botSession;
      this.packetDestination = packetDestination;
   }

   static MultiPovControlContext resolve(MultiSession session, int botEntityId) {
      Minecraft mc = Minecraft.getInstance();
      return session != null && mc.level != null && mc.level.getEntity(botEntityId) instanceof RemotePlayer bot && MultiPilot.isManualControlEntity(bot)
         ? new MultiPovControlContext(mc, bot, session, MultiPovControlContext.PacketDestination.BOT)
         : new MultiPovControlContext(mc, mc.player, null, MultiPovControlContext.PacketDestination.MAIN);
   }

   boolean controls(Entity entity) {
      return this.packetDestination == MultiPovControlContext.PacketDestination.BOT && this.player != null && this.player == entity;
   }

   Player player() {
      return this.player;
   }

   MultiSession botSession() {
      return this.botSession;
   }

   MultiPovControlContext.PacketDestination packetDestination() {
      return this.packetDestination;
   }

   int gameModeId() {
      if (this.botSession != null) {
         return this.botSession.takeoverGameModeId();
      } else {
         return this.minecraft.gameMode == null ? -1 : this.minecraft.gameMode.getPlayerMode().getId();
      }
   }

   Inventory inventory() {
      return this.player == null ? null : this.player.getInventory();
   }

   int selectedHotbar() {
      return this.botSession == null ? (this.player == null ? -1 : this.player.getInventory().getSelectedSlot()) : this.botSession.selectedHotbar();
   }

   boolean send(Packet<?> packet) {
      if (packet == null) {
         return false;
      } else if (this.botSession != null) {
         return this.botSession.pilotSend(packet);
      } else if (this.minecraft.getConnection() == null) {
         return false;
      } else {
         this.minecraft.getConnection().send(packet);
         return true;
      }
   }

   boolean selectHotbar(int slot) {
      int clamped = Math.max(0, Math.min(8, slot));
      if (this.botSession != null) {
         return this.botSession.pilotSelectHotbar(clamped);
      } else if (this.player != null && this.minecraft.getConnection() != null) {
         RiptideInventoryHelper.selectHotbarSlot(this.minecraft, clamped);
         return true;
      } else {
         return false;
      }
   }

   boolean swapInventoryToHotbar(int inventorySlot, int hotbarSlot) {
      return this.botSession != null
         ? this.botSession.pilotSwapInventoryToHotbar(inventorySlot, hotbarSlot)
         : this.player != null && RiptideInventoryHelper.swapInventorySlots(this.minecraft, inventorySlot, Math.max(0, Math.min(8, hotbarSlot)));
   }

   static enum PacketDestination {
      MAIN,
      BOT;
   }
}
