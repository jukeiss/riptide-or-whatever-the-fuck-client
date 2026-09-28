package riptide.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ContainerInput;

public final class RiptidePacketClick {
   private RiptidePacketClick() {
   }

   public static enum Mode {
      LEFT_CLICK("Left Click", "Left", 0, ContainerInput.PICKUP),
      RIGHT_CLICK("Right Click", "Right", 1, ContainerInput.PICKUP),
      QUICK_MOVE("Quick Move", "QMove", 0, ContainerInput.QUICK_MOVE);

      public final String displayName;
      public final String shortName;
      public final int button;
      public final ContainerInput input;

      private Mode(String displayName, String shortName, int button, ContainerInput input) {
         this.displayName = displayName;
         this.shortName = shortName;
         this.button = button;
         this.input = input;
      }

      public static RiptidePacketClick.Mode byIndex(int index) {
         RiptidePacketClick.Mode[] values = values();
         return index >= 0 && index < values.length ? values[index] : LEFT_CLICK;
      }

      public static RiptidePacketClick.Mode fromName(String name) {
         if (name != null) {
            for (RiptidePacketClick.Mode mode : values()) {
               if (mode.name().equalsIgnoreCase(name) || mode.displayName.equalsIgnoreCase(name) || mode.shortName.equalsIgnoreCase(name)) {
                  return mode;
               }
            }
         }

         return LEFT_CLICK;
      }
   }

   public record Target(
      int containerId,
      int stateId,
      int handlerSlot,
      int visibleSlot,
      String screenTitle,
      String menuClass,
      String itemSummary,
      RiptidePacketClick.Mode mode,
      long capturedAtMs
   ) {
      public ServerboundContainerClickPacket buildPacket() {
         RiptidePacketClick.Mode effectiveMode = this.mode == null ? RiptidePacketClick.Mode.LEFT_CLICK : this.mode;
         return new ServerboundContainerClickPacket(
            this.containerId,
            this.stateId,
            (short)this.handlerSlot,
            (byte)effectiveMode.button,
            effectiveMode.input,
            new Int2ObjectArrayMap(),
            HashedStack.EMPTY
         );
      }

      public RiptidePacketClick.Target withMode(RiptidePacketClick.Mode newMode) {
         return new RiptidePacketClick.Target(
            this.containerId,
            this.stateId,
            this.handlerSlot,
            this.visibleSlot,
            this.screenTitle,
            this.menuClass,
            this.itemSummary,
            newMode == null ? RiptidePacketClick.Mode.LEFT_CLICK : newMode,
            this.capturedAtMs
         );
      }

      public String summary() {
         String modeName = (this.mode == null ? RiptidePacketClick.Mode.LEFT_CLICK : this.mode).displayName;
         String item = this.itemSummary != null && !this.itemSummary.isBlank() ? this.itemSummary : "empty";
         return modeName + " slot " + this.visibleSlot + " [" + item + "] menu " + this.containerId + ":" + this.stateId;
      }

      public CompoundTag toTag() {
         CompoundTag tag = new CompoundTag();
         tag.putInt("containerId", this.containerId);
         tag.putInt("stateId", this.stateId);
         tag.putInt("handlerSlot", this.handlerSlot);
         tag.putInt("visibleSlot", this.visibleSlot);
         tag.putString("screenTitle", this.screenTitle == null ? "" : this.screenTitle);
         tag.putString("menuClass", this.menuClass == null ? "" : this.menuClass);
         tag.putString("itemSummary", this.itemSummary == null ? "" : this.itemSummary);
         tag.putString("mode", (this.mode == null ? RiptidePacketClick.Mode.LEFT_CLICK : this.mode).name());
         tag.putLong("capturedAtMs", this.capturedAtMs);
         return tag;
      }

      public static RiptidePacketClick.Target fromTag(CompoundTag tag) {
         if (tag == null) {
            return null;
         } else {
            int containerId = tag.getIntOr("containerId", -1);
            int stateId = tag.getIntOr("stateId", 0);
            int handlerSlot = tag.getIntOr("handlerSlot", -1);
            int visibleSlot = tag.getIntOr("visibleSlot", handlerSlot);
            return containerId >= 0 && handlerSlot >= 0
               ? new RiptidePacketClick.Target(
                  containerId,
                  stateId,
                  handlerSlot,
                  visibleSlot,
                  tag.getStringOr("screenTitle", ""),
                  tag.getStringOr("menuClass", ""),
                  tag.getStringOr("itemSummary", ""),
                  RiptidePacketClick.Mode.fromName(tag.getStringOr("mode", RiptidePacketClick.Mode.LEFT_CLICK.name())),
                  tag.getLongOr("capturedAtMs", 0L)
               )
               : null;
         }
      }
   }
}
