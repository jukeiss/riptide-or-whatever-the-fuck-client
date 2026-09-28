package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import riptide.modules.PackHideState;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideContainerHold;
import riptide.util.RiptidePacketClick;
import riptide.util.RiptideSharedState;

public class PacketClickAction implements MacroAction {
   public RiptidePacketClick.Target target;
   public String mode = RiptidePacketClick.Mode.LEFT_CLICK.name();
   public int times = 1;
   public boolean queue = false;
   private transient boolean holdsContainer = false;

   public PacketClickAction() {
   }

   public PacketClickAction(RiptidePacketClick.Target target, int times, boolean queue) {
      this.target = target;
      this.times = Math.max(1, times);
      this.queue = queue;
      this.acquireHold();
   }

   public void setTarget(RiptidePacketClick.Target newTarget) {
      this.releaseHoldNoFlush();
      this.target = newTarget == null ? null : newTarget.withMode(this.effectiveMode());
      this.acquireHold();
   }

   private RiptidePacketClick.Mode effectiveMode() {
      if (this.target != null && (this.mode == null || this.mode.isBlank())) {
         return this.target.mode() == null ? RiptidePacketClick.Mode.LEFT_CLICK : this.target.mode();
      } else {
         return RiptidePacketClick.Mode.fromName(this.mode);
      }
   }

   private void acquireHold() {
      if (this.target != null && !this.holdsContainer) {
         RiptideContainerHold.hold(this.target.containerId());
         this.holdsContainer = true;
      }
   }

   private void releaseHold(ClientPacketListener conn) {
      if (this.holdsContainer && this.target != null) {
         RiptideContainerHold.release(this.target.containerId(), conn);
         this.holdsContainer = false;
      } else {
         this.holdsContainer = false;
      }
   }

   private void releaseHoldNoFlush() {
      if (this.holdsContainer && this.target != null) {
         RiptideContainerHold.release(this.target.containerId(), null);
         this.holdsContainer = false;
      } else {
         this.holdsContainer = false;
      }
   }

   @Override
   public void execute(Minecraft mc) {
      if (!PackHideState.isHardLocked()) {
         if (mc.getConnection() == null) {
            RiptideClientMessaging.sendPrefixed("§cNo network connection!");
         } else if (this.target == null) {
            RiptideClientMessaging.sendPrefixed("§cPacket Click has no captured target.");
         } else {
            int count = Math.max(1, this.times);
            RiptidePacketClick.Target effectiveTarget = this.target.withMode(this.effectiveMode());

            for (int i = 0; i < count; i++) {
               ServerboundContainerClickPacket packet = effectiveTarget.buildPacket();
               if (this.queue) {
                  RiptideSharedState.get().enqueueExactPacket(packet);
               } else {
                  RiptideSharedState.get().sendPacketBypassDelay(mc.getConnection(), packet);
               }
            }

            this.releaseHold(mc.getConnection());
         }
      }
   }

   public void cancelHold() {
      this.releaseHoldNoFlush();
   }

   public void releasePendingClose(ClientPacketListener conn) {
      this.releaseHold(conn);
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("mode", this.effectiveMode().name());
      tag.putInt("times", Math.max(1, this.times));
      tag.putBoolean("queue", this.queue);
      if (this.target != null) {
         tag.put("target", this.target.withMode(this.effectiveMode()).toTag());
      }

      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.times = Math.max(1, tag.getIntOr("times", 1));
      this.queue = tag.getBooleanOr("queue", false);
      this.mode = tag.getStringOr("mode", "");
      this.target = tag.getCompound("target").map(RiptidePacketClick.Target::fromTag).orElse(null);
      if (this.mode == null || this.mode.isBlank()) {
         this.mode = this.target != null && this.target.mode() != null ? this.target.mode().name() : RiptidePacketClick.Mode.LEFT_CLICK.name();
      }

      if (this.target != null) {
         this.target = this.target.withMode(this.effectiveMode());
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.PACKET_CLICK;
   }

   @Override
   public String getDisplayName() {
      if (this.target == null) {
         return "Packet Click (empty)";
      } else {
         String suffix = this.times > 1 ? " x" + this.times : "";
         return "Packet Click " + this.target.withMode(this.effectiveMode()).summary() + suffix;
      }
   }

   @Override
   public String getIcon() {
      return "Pkt";
   }
}
