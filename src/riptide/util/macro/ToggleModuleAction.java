package riptide.util.macro;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import riptide.modules.PackHideState;
import riptide.util.RiptideCompatManager;

public class ToggleModuleAction implements MacroAction {
   public String moduleName = "";
   public ToggleModuleAction.ToggleMode toggleMode = ToggleModuleAction.ToggleMode.TOGGLE;
   public List<ToggleModuleAction.ModuleEntry> entries = new ArrayList<>();
   private boolean enabled = true;

   public ToggleModuleAction() {
   }

   public ToggleModuleAction(String moduleName) {
      this.moduleName = moduleName == null ? "" : moduleName.trim();
      if (!this.moduleName.isBlank()) {
         this.entries.add(new ToggleModuleAction.ModuleEntry(this.moduleName, this.toggleMode));
      }
   }

   @Override
   public void execute(Minecraft mc) {
      if (!this.entries.isEmpty()) {
         for (ToggleModuleAction.ModuleEntry entry : this.entries) {
            if (entry != null && entry.moduleName != null && !entry.moduleName.isBlank()) {
               RiptideCompatManager.toggleMeteorModule(entry.moduleName, entry.toggleMode);
            }
         }
      } else {
         RiptideCompatManager.toggleMeteorModule(this.moduleName, this.toggleMode);
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("moduleName", this.moduleName);
      tag.putString("toggleMode", this.toggleMode.name());
      ListTag list = new ListTag();

      for (ToggleModuleAction.ModuleEntry entry : this.entries) {
         if (entry != null && entry.moduleName != null && !entry.moduleName.isBlank()) {
            list.add(entry.toTag());
         }
      }

      tag.put("entries", list);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("moduleName")) {
         this.moduleName = tag.getStringOr("moduleName", "");
      }

      if (tag.contains("toggleMode")) {
         try {
            this.toggleMode = ToggleModuleAction.ToggleMode.valueOf(tag.getStringOr("toggleMode", "TOGGLE"));
         } catch (IllegalArgumentException var7) {
            this.toggleMode = ToggleModuleAction.ToggleMode.TOGGLE;
         }
      }

      this.entries.clear();
      if (tag.contains("entries")) {
         for (Tag element : tag.getList("entries").orElse(new ListTag())) {
            if (element instanceof CompoundTag compound) {
               ToggleModuleAction.ModuleEntry entry = ToggleModuleAction.ModuleEntry.fromTag(compound);
               if (entry.moduleName != null && !entry.moduleName.isBlank()) {
                  this.entries.add(entry);
               }
            }
         }
      }

      if (this.entries.isEmpty() && this.moduleName != null && !this.moduleName.isBlank()) {
         this.entries.add(new ToggleModuleAction.ModuleEntry(this.moduleName, this.toggleMode));
      }

      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.TOGGLE_MODULE;
   }

   @Override
   public String getDisplayName() {
      if (!this.entries.isEmpty()) {
         return this.entries.size() == 1
            ? formatMode(this.entries.get(0).toggleMode) + " Module (" + this.entries.get(0).moduleName + ")"
            : "Module Batch (" + this.entries.size() + ")";
      } else {
         String modeStr = switch (this.toggleMode) {
            case ENABLE -> "Enable";
            case DISABLE -> "Disable";
            default -> "Toggle";
         };
         return modeStr + " Module (" + (this.moduleName.isEmpty() ? "None" : this.moduleName) + ")";
      }
   }

   @Override
   public String getIcon() {
      return "M";
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
   public void sanitizeForSharing() {
      this.entries.removeIf(e -> e != null && PackHideState.isHideModuleName(e.moduleName));
      if (PackHideState.isHideModuleName(this.moduleName)) {
         this.moduleName = "";
         this.toggleMode = ToggleModuleAction.ToggleMode.TOGGLE;
      }
   }

   private static String formatMode(ToggleModuleAction.ToggleMode mode) {
      return switch (mode) {
         case ENABLE -> "Enable";
         case DISABLE -> "Disable";
         default -> "Toggle";
      };
   }

   public static class ModuleEntry {
      public String moduleName = "";
      public ToggleModuleAction.ToggleMode toggleMode = ToggleModuleAction.ToggleMode.TOGGLE;

      public ModuleEntry() {
      }

      public ModuleEntry(String moduleName, ToggleModuleAction.ToggleMode toggleMode) {
         this.moduleName = moduleName == null ? "" : moduleName.trim();
         this.toggleMode = toggleMode == null ? ToggleModuleAction.ToggleMode.TOGGLE : toggleMode;
      }

      public CompoundTag toTag() {
         CompoundTag tag = new CompoundTag();
         tag.putString("moduleName", this.moduleName);
         tag.putString("toggleMode", this.toggleMode.name());
         return tag;
      }

      public static ToggleModuleAction.ModuleEntry fromTag(CompoundTag tag) {
         ToggleModuleAction.ModuleEntry entry = new ToggleModuleAction.ModuleEntry();
         if (tag.contains("moduleName")) {
            entry.moduleName = tag.getStringOr("moduleName", "");
         }

         if (tag.contains("toggleMode")) {
            try {
               entry.toggleMode = ToggleModuleAction.ToggleMode.valueOf(tag.getStringOr("toggleMode", "TOGGLE"));
            } catch (IllegalArgumentException var3) {
               entry.toggleMode = ToggleModuleAction.ToggleMode.TOGGLE;
            }
         }

         return entry;
      }
   }

   public static enum ToggleMode {
      TOGGLE,
      ENABLE,
      DISABLE;
   }
}
