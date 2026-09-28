package riptide.util.macro;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import riptide.modules.PackHideState;
import riptide.util.RiptideInventoryHelper;
import riptide.util.RiptideSharedState;

public class ContainerClickSequenceAction implements MacroAction {
   public ContainerClickSequenceAction.SlotSource slotSource = ContainerClickSequenceAction.SlotSource.SINGLE;
   public ContainerClickSequenceAction.ContainerSource containerSource = ContainerClickSequenceAction.ContainerSource.CURRENT;
   public int slot = 0;
   public int startSlot = 0;
   public int endSlot = 0;
   public ArrayList<String> slots = new ArrayList<>();
   public int manualContainerId = 0;
   public int savedContainerId = -1;
   public String button = "0 (Left/Primary)";
   public String containerInput = "PICKUP";
   public int repeatCount = 1;
   public int delayTicks = 0;

   @Override
   public void execute(Minecraft mc) {
      if (!PackHideState.isHardLocked()) {
         if (mc != null && mc.player != null && mc.gameMode != null && mc.player.containerMenu != null) {
            List<Integer> clickSlots = this.resolvedSlots();

            for (int r = 0; r < Math.max(1, this.repeatCount); r++) {
               for (int visibleSlot : clickSlots) {
                  this.executeClick(mc, visibleSlot);
               }
            }
         }
      }
   }

   boolean executeClick(Minecraft mc, int visibleSlot) {
      if (!PackHideState.isHardLocked() && mc != null && mc.player != null && mc.gameMode != null && mc.player.containerMenu != null) {
         int handlerSlot = this.containerSource != ContainerClickSequenceAction.ContainerSource.CURRENT
               && this.containerSource != ContainerClickSequenceAction.ContainerSource.PLAYER_INVENTORY
            ? visibleSlot
            : RiptideInventoryHelper.toHandlerSlot(mc, visibleSlot);
         if (handlerSlot < 0) {
            return false;
         } else {
            int buttonNum = 0;

            try {
               buttonNum = Integer.parseInt(this.button.split(" ")[0]);
            } catch (Exception var6) {
            }

            ContainerInput input = MacroStringList.enumValue(ContainerInput.class, this.containerInput, ContainerInput.PICKUP);
            mc.gameMode.handleContainerInput(this.resolvedContainerId(mc), handlerSlot, buttonNum, input, mc.player);
            return true;
         }
      } else {
         return false;
      }
   }

   private int resolvedContainerId(Minecraft mc) {
      return switch (this.containerSource) {
         case CURRENT -> mc.player.containerMenu.containerId;
         case SAVED_GUI -> {
            AbstractContainerMenu saved = RiptideSharedState.get().getStoredAbstractContainerMenu();
            yield saved == null ? this.savedContainerId : saved.containerId;
         }
         case PLAYER_INVENTORY -> 0;
         case MANUAL -> this.manualContainerId;
      };
   }

   public List<Integer> resolvedSlots() {
      ArrayList<Integer> out = new ArrayList<>();
      switch (this.slotSource) {
         case SINGLE:
            out.add(this.slot);
            break;
         case RANGE:
            int a = Math.min(this.startSlot, this.endSlot);
            int b = Math.max(this.startSlot, this.endSlot);

            for (int i = a; i <= b; i++) {
               out.add(i);
            }
            break;
         case LIST:
            for (String raw : this.slots) {
               try {
                  out.add(Integer.parseInt(raw.trim()));
               } catch (Exception var6) {
               }
            }
            break;
         case CAPTURED_SEQUENCE:
            for (String raw : this.slots) {
               try {
                  out.add(Integer.parseInt(raw.trim()));
               } catch (Exception var5) {
               }
            }
      }

      return out;
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.CONTAINER_CLICK_SEQUENCE;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "CONTAINER_CLICK_SEQUENCE");
      tag.putString("slotSource", this.slotSource.name());
      tag.putString("containerSource", this.containerSource.name());
      tag.putInt("slot", this.slot);
      tag.putInt("startSlot", this.startSlot);
      tag.putInt("endSlot", this.endSlot);
      tag.put("slots", MacroStringList.toTag(this.slots));
      tag.putInt("manualContainerId", this.manualContainerId);
      tag.putInt("savedContainerId", this.savedContainerId);
      tag.putString("button", this.button);
      tag.putString("containerInput", this.containerInput);
      tag.putInt("repeatCount", this.repeatCount);
      tag.putInt("delayTicks", this.delayTicks);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.slotSource = MacroStringList.enumValue(
         ContainerClickSequenceAction.SlotSource.class, tag.getStringOr("slotSource", "SINGLE"), ContainerClickSequenceAction.SlotSource.SINGLE
      );
      this.containerSource = MacroStringList.enumValue(
         ContainerClickSequenceAction.ContainerSource.class,
         tag.getStringOr("containerSource", "CURRENT"),
         ContainerClickSequenceAction.ContainerSource.CURRENT
      );
      this.slot = tag.getIntOr("slot", 0);
      this.startSlot = tag.getIntOr("startSlot", 0);
      this.endSlot = tag.getIntOr("endSlot", 0);
      this.slots = MacroStringList.fromTag(tag.getList("slots").orElse(new ListTag()));
      this.manualContainerId = tag.getIntOr("manualContainerId", 0);
      this.savedContainerId = tag.getIntOr("savedContainerId", -1);
      String btnStr = tag.getStringOr("button", "");
      if (!btnStr.isEmpty() && !btnStr.matches("\\d+")) {
         this.button = btnStr;
      } else {
         int b = tag.getIntOr("button", btnStr.isEmpty() ? 0 : Integer.parseInt(btnStr));
         if (b == 0) {
            this.button = "0 (Left/Primary)";
         } else if (b == 1) {
            this.button = "1 (Right/Secondary)";
         } else if (b == 2) {
            this.button = "2 (Middle)";
         } else {
            this.button = String.valueOf(b);
         }
      }

      this.containerInput = tag.getStringOr("containerInput", "PICKUP");
      this.repeatCount = tag.getIntOr("repeatCount", 1);
      this.delayTicks = tag.getIntOr("delayTicks", 0);
   }

   @Override
   public String getDisplayName() {
      return "Click slots " + this.slotSource + " " + this.containerInput;
   }

   @Override
   public String getIcon() {
      return "C";
   }

   public static enum ContainerSource {
      CURRENT,
      SAVED_GUI,
      PLAYER_INVENTORY,
      MANUAL;
   }

   public static enum SlotSource {
      SINGLE,
      RANGE,
      LIST,
      CAPTURED_SEQUENCE;
   }
}
