package riptide.util;

import java.util.Locale;
import riptide.util.macro.MacroAction;
import riptide.util.macro.MacroConditionUtil;
import riptide.util.macro.ToggleModuleAction;
import riptide.util.macro.WaitDurabilityAction;
import riptide.util.macro.WaitFreeSlotsAction;

public final class AutoFishStopMacroFactory {
   public static final String FREE_SLOTS_NAME = "AutoFish - Free Slots Stop";
   public static final String DURABILITY_NAME = "AutoFish - Durability Stop";
   public static final String CUSTOM_NAME = "AutoFish - Custom Stop";

   private AutoFishStopMacroFactory() {
   }

   public static RiptideMacro ensurePreset(AutoFishStopMacroFactory.Preset preset) {
      return switch (preset == null ? AutoFishStopMacroFactory.Preset.CUSTOM : preset) {
         case FREE_SLOTS -> ensureMacro("AutoFish - Free Slots Stop", freeSlotsCondition());
         case DURABILITY -> ensureMacro("AutoFish - Durability Stop", durabilityCondition());
         case CUSTOM -> ensureMacro("AutoFish - Custom Stop", freeSlotsCondition());
      };
   }

   public static boolean isValidStopMacro(RiptideMacro macro) {
      return MacroConditionUtil.startsWithWaitCondition(macro);
   }

   public static boolean isGeneratedStopMacroName(String name) {
      return name == null
         ? false
         : generatedNameMatches(name, "AutoFish - Free Slots Stop")
            || generatedNameMatches(name, "AutoFish - Durability Stop")
            || generatedNameMatches(name, "AutoFish - Custom Stop");
   }

   public static boolean isGeneratedStopMacro(RiptideMacro macro) {
      return macro != null && isGeneratedStopMacroName(macro.name);
   }

   public static AutoFishStopMacroFactory.Preset presetForGeneratedName(String name) {
      if (name == null) {
         return null;
      } else if (generatedNameMatches(name, "AutoFish - Free Slots Stop")) {
         return AutoFishStopMacroFactory.Preset.FREE_SLOTS;
      } else if (generatedNameMatches(name, "AutoFish - Durability Stop")) {
         return AutoFishStopMacroFactory.Preset.DURABILITY;
      } else {
         return generatedNameMatches(name, "AutoFish - Custom Stop") ? AutoFishStopMacroFactory.Preset.CUSTOM : null;
      }
   }

   public static boolean isAutoFishToggleAction(MacroAction action) {
      return action instanceof ToggleModuleAction toggle && togglesAutoFish(toggle);
   }

   public static boolean disablesAutoFish(MacroAction action) {
      return action instanceof ToggleModuleAction toggle && disablesAutoFish(toggle);
   }

   private static RiptideMacro ensureMacro(String preferredName, MacroAction firstCondition) {
      RiptideMacroManager manager = RiptideMacroManager.get();
      RiptideMacro existing = manager.get(preferredName);
      if (existing != null) {
         boolean changed = false;
         if (!isValidStopMacro(existing)) {
            existing.actions.add(0, firstCondition);
            changed = true;
         }

         changed |= removeImmediateGeneratedAutoFishToggle(existing);
         if (changed) {
            manager.save();
         }

         return existing;
      } else {
         RiptideMacro macro = new RiptideMacro(manager.createUniqueName(preferredName));
         macro.description = "AutoFish stop macro";
         macro.actions.add(firstCondition);
         manager.add(macro);
         return macro;
      }
   }

   private static boolean togglesAutoFish(ToggleModuleAction action) {
      if (action == null) {
         return false;
      } else if (action.entries != null && !action.entries.isEmpty()) {
         for (ToggleModuleAction.ModuleEntry entry : action.entries) {
            if (entry != null && "AutoFish".equalsIgnoreCase(entry.moduleName)) {
               return true;
            }
         }

         return false;
      } else {
         return "AutoFish".equalsIgnoreCase(action.moduleName);
      }
   }

   private static boolean disablesAutoFish(ToggleModuleAction action) {
      if (action == null) {
         return false;
      } else if (action.entries != null && !action.entries.isEmpty()) {
         for (ToggleModuleAction.ModuleEntry entry : action.entries) {
            if (entry != null && "AutoFish".equalsIgnoreCase(entry.moduleName) && entry.toggleMode == ToggleModuleAction.ToggleMode.DISABLE) {
               return true;
            }
         }

         return false;
      } else {
         return "AutoFish".equalsIgnoreCase(action.moduleName) && action.toggleMode == ToggleModuleAction.ToggleMode.DISABLE;
      }
   }

   private static boolean removeImmediateGeneratedAutoFishToggle(RiptideMacro macro) {
      if (macro != null && macro.actions != null) {
         for (int i = 0; i < macro.actions.size(); i++) {
            MacroAction action = macro.actions.get(i);
            if (action != null && action.isEnabled() && MacroConditionUtil.isWaitConditionAction(action)) {
               int next = i + 1;
               if (next < macro.actions.size() && isAutoFishToggleAction(macro.actions.get(next))) {
                  macro.actions.remove(next);
                  return true;
               }

               return false;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean generatedNameMatches(String name, String base) {
      if (name != null && base != null) {
         String trimmed = name.trim();
         return trimmed.equalsIgnoreCase(base) || trimmed.toLowerCase(Locale.ROOT).startsWith(base.toLowerCase(Locale.ROOT) + " (");
      } else {
         return false;
      }
   }

   private static WaitFreeSlotsAction freeSlotsCondition() {
      WaitFreeSlotsAction action = new WaitFreeSlotsAction();
      action.countMode = WaitFreeSlotsAction.CountMode.FREE_SLOTS;
      action.comparison = WaitFreeSlotsAction.Comparison.AT_MOST;
      action.slots = 0;
      action.timeoutMs = 0;
      return action;
   }

   private static WaitDurabilityAction durabilityCondition() {
      WaitDurabilityAction action = new WaitDurabilityAction();
      action.targetMode = WaitDurabilityAction.TargetMode.ITEM;
      action.itemName = "minecraft:fishing_rod";
      action.measurement = WaitDurabilityAction.Measurement.REMAINING;
      action.comparison = WaitDurabilityAction.Comparison.AT_MOST;
      action.value = 2;
      action.useNext = true;
      action.timeoutMs = 0;
      return action;
   }

   public static enum Preset {
      FREE_SLOTS,
      DURABILITY,
      CUSTOM;
   }
}
