package riptide.util.macro;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.Vec3;
import riptide.modules.PackHideState;
import riptide.util.RiptideSharedState;

public class PacketBurstAction implements MacroAction {
   public PacketBurstAction.BurstMode mode = PacketBurstAction.BurstMode.CONTAINER_CLICK;
   public int count = 1;
   public int delayTicks = 0;
   public int slot = 0;
   public int button = 0;
   public String containerInput = "PICKUP";
   public int entityId = -1;
   public String hand = "MAIN_HAND";
   public String clientCommand = "REQUEST_STATS";
   public String playerCommand = "OPEN_INVENTORY";
   public int bundleIndex = 0;
   public int carriedSlot = 0;
   public int containerId = 0;
   public boolean flushBefore = false;

   @Override
   public void execute(Minecraft mc) {
      if (!PackHideState.isHardLocked()) {
         if (mc != null && mc.getConnection() != null) {
            if (this.flushBefore) {
               RiptideSharedState.get().flushDelayedPackets(mc.getConnection());
            }

            int times = this.effectiveCount();

            for (int i = 0; i < times; i++) {
               if (PackHideState.isHardLocked()) {
                  return;
               }

               this.sendOne(mc);
            }
         }
      }
   }

   int effectiveCount() {
      return Math.max(1, Math.min(10000, this.count));
   }

   boolean sendOne(Minecraft mc) {
      if (!PackHideState.isHardLocked() && mc != null && mc.getConnection() != null) {
         Packet<?> packet = this.createPacket(mc);
         if (packet == null) {
            return false;
         } else {
            mc.getConnection().send(packet);
            return true;
         }
      } else {
         return false;
      }
   }

   private Packet<?> createPacket(Minecraft mc) {
      return (Packet<?>)(switch (this.mode) {
         case CONTAINER_CLICK -> new ServerboundContainerClickPacket(
            mc.player != null && mc.player.containerMenu != null ? mc.player.containerMenu.containerId : 0,
            mc.player != null && mc.player.containerMenu != null ? mc.player.containerMenu.getStateId() : 0,
            (short)this.slot,
            (byte)this.button,
            this.input(),
            new Int2ObjectOpenHashMap(),
            HashedStack.EMPTY
         );
         case ENTITY_INTERACT -> {
            int id = this.entityId;
            if (id < 0 && mc.crosshairPickEntity != null) {
               id = mc.crosshairPickEntity.getId();
            }

            yield id < 0 ? null : new ServerboundInteractPacket(id, this.interactionHand(), Vec3.ZERO, false);
         }
         case CLIENT_COMMAND -> mc.player == null
            ? null
            : new ServerboundPlayerCommandPacket(mc.player, MacroStringList.enumValue(Action.class, this.playerCommand, Action.OPEN_INVENTORY));
         case BUNDLE_SELECT -> new ServerboundSelectBundleItemPacket(
            this.slot >= 0 ? this.slot : 36 + (mc.player == null ? 0 : mc.player.getInventory().getSelectedSlot()), this.bundleIndex
         );
         case USE_ITEM -> new ServerboundUseItemPacket(
            this.interactionHand(), 0, mc.player == null ? 0.0F : mc.player.getYRot(), mc.player == null ? 0.0F : mc.player.getXRot()
         );
         case RELEASE_ITEM -> new ServerboundPlayerActionPacket(
            net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN
         );
         case SET_CARRIED_ITEM -> new ServerboundSetCarriedItemPacket(Math.max(0, Math.min(8, this.carriedSlot)));
         case CLIENT_INFORMATION -> new ServerboundClientInformationPacket(ClientInformation.createDefault());
         case CLOSE_CONTAINER -> new ServerboundContainerClosePacket(this.containerId);
      });
   }

   private ContainerInput input() {
      return MacroStringList.enumValue(ContainerInput.class, this.containerInput, ContainerInput.PICKUP);
   }

   private InteractionHand interactionHand() {
      return "OFF_HAND".equalsIgnoreCase(this.hand) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.PACKET_BURST;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "PACKET_BURST");
      tag.putString("mode", this.mode.name());
      tag.putInt("count", this.count);
      tag.putInt("delayTicks", this.delayTicks);
      tag.putInt("slot", this.slot);
      tag.putInt("button", this.button);
      tag.putString("containerInput", this.containerInput);
      tag.putInt("entityId", this.entityId);
      tag.putString("hand", this.hand);
      tag.putString("clientCommand", this.clientCommand);
      tag.putString("playerCommand", this.playerCommand);
      tag.putInt("bundleIndex", this.bundleIndex);
      tag.putInt("carriedSlot", this.carriedSlot);
      tag.putInt("containerId", this.containerId);
      tag.putBoolean("flushBefore", this.flushBefore);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.mode = MacroStringList.enumValue(
         PacketBurstAction.BurstMode.class, tag.getStringOr("mode", "CONTAINER_CLICK"), PacketBurstAction.BurstMode.CONTAINER_CLICK
      );
      this.count = tag.getIntOr("count", 1);
      this.delayTicks = tag.getIntOr("delayTicks", 0);
      this.slot = tag.getIntOr("slot", 0);
      this.button = tag.getIntOr("button", 0);
      this.containerInput = tag.getStringOr("containerInput", "PICKUP");
      this.entityId = tag.getIntOr("entityId", -1);
      this.hand = tag.getStringOr("hand", "MAIN_HAND");
      this.clientCommand = tag.getStringOr("clientCommand", "REQUEST_STATS");
      this.playerCommand = tag.getStringOr("playerCommand", "OPEN_INVENTORY");
      this.bundleIndex = tag.getIntOr("bundleIndex", 0);
      this.carriedSlot = tag.getIntOr("carriedSlot", 0);
      this.containerId = tag.getIntOr("containerId", 0);
      this.flushBefore = tag.getBooleanOr("flushBefore", false);
   }

   @Override
   public String getDisplayName() {
      return "Packet burst " + this.mode + " x" + this.count;
   }

   @Override
   public String getIcon() {
      return "B";
   }

   public static enum BurstMode {
      CONTAINER_CLICK,
      ENTITY_INTERACT,
      CLIENT_COMMAND,
      BUNDLE_SELECT,
      USE_ITEM,
      RELEASE_ITEM,
      SET_CARRIED_ITEM,
      CLIENT_INFORMATION,
      CLOSE_CONTAINER;
   }
}
