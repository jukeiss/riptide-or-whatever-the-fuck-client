package riptide.util.macro;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import riptide.util.RiptideRegistryLabels;

public class MineAction implements MacroAction, PacketOrdered {
   public List<String> targetBlocks = new ArrayList<>();
   public boolean stopInventoryFull = false;
   public boolean stopSlotsUsed = false;
   public int slotsUsedThreshold = 18;
   public boolean stopMinedCount = false;
   public int minedCountTarget = 64;
   public boolean stopAfterTime = false;
   public int timeoutSeconds = 60;
   public volatile PacketOrder packetOrder = PacketOrder.INSTANT;
   private boolean enabled = true;

   @Override
   public MacroActionType getType() {
      return MacroActionType.MINE;
   }

   @Override
   public PacketOrder getPacketOrder() {
      return this.packetOrder;
   }

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public String getDisplayName() {
      if (this.targetBlocks.isEmpty()) {
         return "Mine: (none)";
      } else {
         String first = RiptideRegistryLabels.block(this.targetBlocks.get(0));
         return this.targetBlocks.size() == 1 ? "Mine: " + first : "Mine: " + first + " (+" + (this.targetBlocks.size() - 1) + ")";
      }
   }

   @Override
   public String getIcon() {
      return "MN";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      ListTag list = new ListTag();

      for (String id : this.targetBlocks) {
         list.add(StringTag.valueOf(id));
      }

      tag.put("targetBlocks", list);
      tag.putBoolean("stopInventoryFull", this.stopInventoryFull);
      tag.putBoolean("stopSlotsUsed", this.stopSlotsUsed);
      tag.putInt("slotsUsedThreshold", this.slotsUsedThreshold);
      tag.putBoolean("stopMinedCount", this.stopMinedCount);
      tag.putInt("minedCountTarget", this.minedCountTarget);
      tag.putBoolean("stopAfterTime", this.stopAfterTime);
      tag.putInt("timeoutSeconds", this.timeoutSeconds);
      tag.putBoolean("enabled", this.enabled);
      PacketOrdered.save(tag, this.packetOrder);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.targetBlocks.clear();
      if (tag.contains("targetBlocks")) {
         for (Tag el : tag.getList("targetBlocks").orElse(new ListTag())) {
            String s = el.asString().orElse("");
            if (!s.isEmpty()) {
               this.targetBlocks.add(s);
            }
         }
      }

      this.stopInventoryFull = tag.getBooleanOr("stopInventoryFull", false);
      this.stopSlotsUsed = tag.getBooleanOr("stopSlotsUsed", false);
      this.slotsUsedThreshold = tag.getIntOr("slotsUsedThreshold", 18);
      this.stopMinedCount = tag.getBooleanOr("stopMinedCount", false);
      this.minedCountTarget = tag.getIntOr("minedCountTarget", 64);
      this.stopAfterTime = tag.getBooleanOr("stopAfterTime", false);
      this.timeoutSeconds = tag.getIntOr("timeoutSeconds", 60);
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }

      this.packetOrder = PacketOrdered.load(tag);
   }
}
