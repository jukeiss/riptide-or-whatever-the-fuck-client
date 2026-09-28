package riptide.util.macro;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public class WaitForLanStepAction implements MacroAction {
   public boolean filterByUser = false;
   public int defaultStep = 1;
   public List<WaitForLanStepAction.LanStepEntry> entries = new ArrayList<>();
   public boolean listenDuringPreviousAction = false;
   private boolean enabled = true;

   @Override
   public MacroActionType getType() {
      return MacroActionType.WAIT_LAN_STEP;
   }

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public String getDisplayName() {
      if (!this.filterByUser) {
         return "Wait LAN: any @ step " + this.defaultStep;
      } else if (this.entries.isEmpty()) {
         return "Wait LAN: any @ step " + this.defaultStep;
      } else if (this.entries.size() == 1) {
         WaitForLanStepAction.LanStepEntry e = this.entries.get(0);
         return "Wait LAN: " + (e.username.isEmpty() ? "any" : e.username) + " @ " + e.step;
      } else {
         return "Wait LAN: " + this.entries.size() + " peers";
      }
   }

   @Override
   public String getIcon() {
      return "LAN";
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
      tag.putBoolean("filterByUser", this.filterByUser);
      tag.putInt("defaultStep", this.defaultStep);
      tag.putBoolean("enabled", this.enabled);
      ListTag list = new ListTag();

      for (WaitForLanStepAction.LanStepEntry entry : this.entries) {
         list.add(entry.toTag());
      }

      tag.put("entries", list);
      MacroWaitOptions.write(tag, this);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.filterByUser = tag.getBooleanOr("filterByUser", false);
      this.defaultStep = tag.getIntOr("defaultStep", 1);
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }

      MacroWaitOptions.read(tag, this);
      this.entries.clear();
      if (tag.contains("entries")) {
         for (Tag el : tag.getList("entries").orElse(new ListTag())) {
            if (el instanceof CompoundTag c) {
               this.entries.add(WaitForLanStepAction.LanStepEntry.fromTag(c));
            }
         }
      }
   }

   public static class LanStepEntry {
      public String username = "";
      public int step = 1;

      public LanStepEntry() {
      }

      public LanStepEntry(String username, int step) {
         this.username = username != null ? username : "";
         this.step = Math.max(1, step);
      }

      public CompoundTag toTag() {
         CompoundTag tag = new CompoundTag();
         tag.putString("username", this.username);
         tag.putInt("step", this.step);
         return tag;
      }

      public static WaitForLanStepAction.LanStepEntry fromTag(CompoundTag tag) {
         WaitForLanStepAction.LanStepEntry e = new WaitForLanStepAction.LanStepEntry();
         e.username = tag.getStringOr("username", "");
         e.step = tag.getIntOr("step", 1);
         return e;
      }

      public String getDisplayName() {
         return (this.username.isEmpty() ? "Any" : this.username) + " @ step " + this.step;
      }
   }
}
