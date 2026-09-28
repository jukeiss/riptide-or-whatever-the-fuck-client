package riptide.util.macro;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public class WaitForSlotChangeAction implements MacroAction, MacroCaptureOutput {
   public List<WaitForSlotChangeAction.WaitEntry> entries = new ArrayList<>();
   public boolean listenDuringPreviousAction = false;
   public String saveAs = "";
   private boolean enabled = true;

   public WaitForSlotChangeAction() {
   }

   public WaitForSlotChangeAction(int slotNumber) {
      WaitForSlotChangeAction.WaitEntry entry = new WaitForSlotChangeAction.WaitEntry();
      entry.target = slotNumber >= 0 ? "#" + slotNumber : "";
      entry.itemTarget = ItemTarget.fromLegacyEntry(entry.target);
      this.entries.add(entry);
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      ListTag list = new ListTag();

      for (WaitForSlotChangeAction.WaitEntry entry : this.entries) {
         CompoundTag entryTag = new CompoundTag();
         ItemTarget target = entry.resolvedTarget();
         if (target.hasSlot() || target.hasIdentity()) {
            entryTag.put("target", target.toTag());
         }

         entryTag.putString("mode", entry.waitMode.name());
         entryTag.putInt("count", entry.targetCount);
         list.add(entryTag);
      }

      tag.put("entries", list);
      tag.putBoolean("enabled", this.enabled);
      MacroWaitOptions.write(tag, this);
      tag.putString("saveAs", this.saveAs);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.entries.clear();
      if (tag.contains("entries")) {
         for (Tag element : tag.getList("entries").orElse(new ListTag())) {
            if (element instanceof CompoundTag entryTag) {
               WaitForSlotChangeAction.WaitEntry entry = new WaitForSlotChangeAction.WaitEntry();
               entry.itemTarget = entryTag.getCompound("target")
                  .map(ItemTarget::fromTag)
                  .orElseGet(() -> ItemTarget.fromLegacyEntry(entryTag.getStringOr("target", "")));
               entry.target = entry.itemTarget.toLegacyEntry();
               entry.targetCount = entryTag.getIntOr("count", 1);

               try {
                  entry.waitMode = WaitForSlotChangeAction.WaitMode.valueOf(entryTag.getStringOr("mode", "NOT_EMPTY"));
               } catch (IllegalArgumentException var9) {
                  entry.waitMode = WaitForSlotChangeAction.WaitMode.NOT_EMPTY;
               }

               this.entries.add(entry);
            }
         }
      } else {
         WaitForSlotChangeAction.WaitMode globalMode = WaitForSlotChangeAction.WaitMode.NOT_EMPTY;
         int globalCount = 1;
         if (tag.contains("waitMode")) {
            try {
               String mode = tag.getStringOr("waitMode", "NOT_EMPTY");
               if ("HAS_ITEM".equals(mode)) {
                  mode = "NOT_EMPTY";
               }

               globalMode = WaitForSlotChangeAction.WaitMode.valueOf(mode);
            } catch (IllegalArgumentException var8) {
            }
         }

         if (tag.contains("targetCount")) {
            globalCount = tag.getIntOr("targetCount", 1);
         }

         if (tag.contains("targetEntries")) {
            for (Tag elementx : tag.getList("targetEntries").orElse(new ListTag())) {
               String value = elementx.asString().orElse("").strip();
               if (!value.isBlank()) {
                  this.entries.add(new WaitForSlotChangeAction.WaitEntry(value, globalMode, globalCount));
               }
            }
         } else {
            boolean slotSpecific = true;
            if (tag.contains("scope")) {
               String scope = tag.getStringOr("scope", "HANDLER_SLOT");
               slotSpecific = "SLOT".equals(scope) || "HANDLER_SLOT".equals(scope);
            }

            int slotNumber = tag.contains("slotNumber") && slotSpecific ? tag.getIntOr("slotNumber", -1) : -1;
            String itemName = tag.getStringOr("itemName", "");
            String target;
            if (slotNumber >= 0 && !itemName.isEmpty()) {
               target = "#" + slotNumber + "|" + itemName;
            } else if (slotNumber >= 0) {
               target = "#" + slotNumber;
            } else {
               target = itemName;
            }

            this.entries.add(new WaitForSlotChangeAction.WaitEntry(target, globalMode, globalCount));
         }
      }

      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }

      MacroWaitOptions.read(tag, this);
      this.saveAs = tag.getStringOr("saveAs", "");
   }

   public void fromTagLegacyItem(CompoundTag tag) {
      this.entries.clear();
      String itemName = tag.getStringOr("itemName", "");
      boolean useSlot = tag.contains("useSlot") && tag.getBooleanOr("useSlot", false);
      int targetSlot = tag.contains("targetSlot") ? tag.getIntOr("targetSlot", -1) : -1;
      int slotNumber = useSlot && targetSlot >= 0 ? targetSlot : -1;
      String target;
      if (slotNumber >= 0 && !itemName.isEmpty()) {
         target = "#" + slotNumber + "|" + itemName;
      } else if (slotNumber >= 0) {
         target = "#" + slotNumber;
      } else {
         target = itemName;
      }

      this.entries.add(new WaitForSlotChangeAction.WaitEntry(target, WaitForSlotChangeAction.WaitMode.NOT_EMPTY, 1));
      this.enabled = true;
   }

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.WAIT_SLOT_CHANGE;
   }

   @Override
   public String getDisplayName() {
      if (this.entries.isEmpty()) {
         return "Wait Slot (empty)";
      } else if (this.entries.size() == 1) {
         WaitForSlotChangeAction.WaitEntry entry = this.entries.get(0);
         String display = StoreItemAction.formatTargetEntry(entry.target.isEmpty() ? "" : entry.target);
         if (display.isEmpty()) {
            display = "any slot";
         }

         return "Wait " + display + " " + entry.modeLabel();
      } else {
         return "Wait all(" + this.entries.size() + ")";
      }
   }

   @Override
   public String getIcon() {
      return "WSL";
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
   public String getSaveAs() {
      return this.saveAs;
   }

   @Override
   public void setSaveAs(String name) {
      this.saveAs = name == null ? "" : name;
   }

   public static class WaitEntry {
      public String target = "";
      public ItemTarget itemTarget = new ItemTarget();
      public WaitForSlotChangeAction.WaitMode waitMode = WaitForSlotChangeAction.WaitMode.NOT_EMPTY;
      public int targetCount = 1;

      public WaitEntry() {
      }

      public WaitEntry(String target, WaitForSlotChangeAction.WaitMode waitMode, int targetCount) {
         this.target = target;
         this.itemTarget = ItemTarget.fromLegacyEntry(target);
         this.waitMode = waitMode;
         this.targetCount = targetCount;
      }

      public WaitForSlotChangeAction.WaitEntry copy() {
         WaitForSlotChangeAction.WaitEntry copy = new WaitForSlotChangeAction.WaitEntry(this.target, this.waitMode, this.targetCount);
         copy.itemTarget = this.itemTarget == null ? new ItemTarget() : this.itemTarget.copy();
         return copy;
      }

      public String modeLabel() {
         return switch (this.waitMode) {
            case NOT_EMPTY -> "has";
            case IS_EMPTY -> "empty";
            case COUNT_AT_LEAST -> ">=" + this.targetCount;
            case COUNT_BELOW -> "<" + this.targetCount;
            case ANY_CHANGE -> "~";
         };
      }

      public void cycleMode() {
         WaitForSlotChangeAction.WaitMode[] modes = WaitForSlotChangeAction.WaitMode.values();
         this.waitMode = modes[(this.waitMode.ordinal() + 1) % modes.length];
      }

      public void cycleModeBackwards() {
         WaitForSlotChangeAction.WaitMode[] modes = WaitForSlotChangeAction.WaitMode.values();
         this.waitMode = modes[(this.waitMode.ordinal() - 1 + modes.length) % modes.length];
      }

      public ItemTarget resolvedTarget() {
         if (this.itemTarget == null || !this.itemTarget.hasSlot() && !this.itemTarget.hasIdentity()) {
            this.itemTarget = ItemTarget.fromLegacyEntry(this.target);
            return this.itemTarget;
         } else {
            return this.itemTarget;
         }
      }
   }

   public static enum WaitMode {
      NOT_EMPTY,
      IS_EMPTY,
      COUNT_AT_LEAST,
      COUNT_BELOW,
      ANY_CHANGE;
   }
}
