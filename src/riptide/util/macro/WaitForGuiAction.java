package riptide.util.macro;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import riptide.api.custommenu.CustomMenuSnapshot;
import riptide.util.custommenu.CustomMenuTracker;

public class WaitForGuiAction implements MacroAction {
   public WaitForGuiAction.WaitMode waitMode = WaitForGuiAction.WaitMode.OPEN;
   public String guiType = "ANY";
   public String guiTitle = "";
   public MacroCapturePattern.Mode matchMode = MacroCapturePattern.Mode.MATCH;
   public String saveAs = "";
   public int timeoutMs = 0;
   public boolean listenDuringPreviousAction = false;
   private boolean enabled = true;

   @Override
   public MacroActionType getType() {
      return MacroActionType.WAIT_GUI;
   }

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public String getDisplayName() {
      String prefix = this.waitMode == WaitForGuiAction.WaitMode.CLOSE ? "Wait GUI Close: " : "Wait GUI Open: ";
      String type = this.guiType != null && !this.guiType.isBlank() && !"ANY".equals(this.guiType) ? this.guiType : "Any";
      return prefix + type + (this.guiTitle.isEmpty() ? "" : " \"" + this.guiTitle + "\"");
   }

   @Override
   public String getIcon() {
      return "GUI";
   }

   public boolean matchesText(String search, String target) {
      if (search.isEmpty() || target.isEmpty()) {
         return false;
      } else if (search.equals(target)) {
         return true;
      } else if (target.toLowerCase().contains(search.toLowerCase())) {
         return true;
      } else {
         String[] searchWords = search.toLowerCase().split("\\s+");
         String[] targetWords = target.toLowerCase().split("\\s+");
         boolean allFound = true;

         for (String word : searchWords) {
            boolean found = false;

            for (String tWord : targetWords) {
               if (tWord.contains(word) || word.contains(tWord)) {
                  found = true;
                  break;
               }
            }

            if (!found) {
               allFound = false;
               break;
            }
         }

         return allFound;
      }
   }

   public boolean checkGui(Minecraft mc) {
      return mc != null && this.captureGui(mc.gui.screen()).isPresent();
   }

   public Optional<Map<String, MacroValue>> captureGui(Screen screen) {
      if (screen == null) {
         return this.captureCustomMenu();
      } else if (!MacroGuiMatcher.matches(screen, this.guiType, "")) {
         return Optional.empty();
      } else {
         String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
         if (this.matchMode == MacroCapturePattern.Mode.MATCH) {
            MacroTemplate.Resolution resolved = MacroVariables.resolve(this.guiTitle, Minecraft.getInstance());
            if (!resolved.success()) {
               return Optional.empty();
            } else {
               return !resolved.value().isBlank() && !MacroGuiMatcher.matches(screen, this.guiType, resolved.value())
                  ? Optional.empty()
                  : Optional.of(this.guiValues(screen, title, Map.of()));
            }
         } else {
            Optional<MacroCapturePattern.Result> matched = MacroCapturePattern.match(this.matchMode, this.guiTitle, title);
            return matched.isEmpty() ? Optional.empty() : Optional.of(this.guiValues(screen, title, matched.get().values()));
         }
      }
   }

   private Optional<Map<String, MacroValue>> captureCustomMenu() {
      CustomMenuSnapshot snapshot = CustomMenuTracker.current();
      boolean anyType = this.guiType == null || this.guiType.isBlank() || "ANY".equalsIgnoreCase(this.guiType);
      if (snapshot != null && (anyType || MacroGuiMatcher.isCustomMenuType(this.guiType))) {
         String title = snapshot.title();
         Map<String, MacroValue> captures = Map.of();
         if (this.matchMode == MacroCapturePattern.Mode.MATCH) {
            MacroTemplate.Resolution resolved = MacroVariables.resolve(this.guiTitle, Minecraft.getInstance());
            if (!resolved.success() || !resolved.value().isBlank() && !title.toLowerCase(Locale.ROOT).contains(resolved.value().toLowerCase(Locale.ROOT))) {
               return Optional.empty();
            }
         } else {
            Optional<MacroCapturePattern.Result> matched = MacroCapturePattern.match(this.matchMode, this.guiTitle, title);
            if (matched.isEmpty()) {
               return Optional.empty();
            }

            captures = matched.get().values();
         }

         Map<String, MacroValue> values = new LinkedHashMap<>(captures);
         if (this.saveAs != null && !this.saveAs.isBlank()) {
            values.put(
               this.saveAs, MacroValue.structured(MacroValue.Kind.GUI, title, Map.of("title", MacroValue.text(title), "type", MacroValue.text("CUSTOM_MENU")))
            );
         }

         return Optional.of(values);
      } else {
         return Optional.empty();
      }
   }

   private Map<String, MacroValue> guiValues(Screen screen, String title, Map<String, MacroValue> captures) {
      Map<String, MacroValue> values = new LinkedHashMap<>(captures);
      if (this.saveAs != null && !this.saveAs.isBlank()) {
         Map<String, MacroValue> properties = new LinkedHashMap<>();
         properties.put("title", MacroValue.text(title));
         properties.put("type", MacroValue.text(MacroGuiMatcher.semanticName(screen)));
         values.put(this.saveAs, MacroValue.structured(MacroValue.Kind.GUI, title, properties));
      }

      return values;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("waitMode", this.waitMode.name());
      tag.putString("guiType", this.guiType == null ? "ANY" : this.guiType);
      tag.putString("guiTitle", this.guiTitle);
      tag.putString("matchMode", (this.matchMode == null ? MacroCapturePattern.Mode.MATCH : this.matchMode).name());
      tag.putString("saveAs", this.saveAs == null ? "" : this.saveAs);
      tag.putInt("timeoutMs", this.timeoutMs);
      tag.putBoolean("enabled", this.enabled);
      MacroWaitOptions.write(tag, this);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("waitMode")) {
         try {
            this.waitMode = WaitForGuiAction.WaitMode.valueOf(tag.getStringOr("waitMode", "OPEN"));
         } catch (IllegalArgumentException var3) {
            this.waitMode = WaitForGuiAction.WaitMode.OPEN;
         }
      }

      if (tag.contains("guiTitle")) {
         this.guiTitle = tag.getStringOr("guiTitle", "");
      } else if (tag.contains("title")) {
         this.guiTitle = tag.getStringOr("title", "");
      }

      this.guiType = tag.getStringOr("guiType", "ANY");
      this.matchMode = MacroStringList.enumValue(MacroCapturePattern.Mode.class, tag.getStringOr("matchMode", "MATCH"), MacroCapturePattern.Mode.MATCH);
      this.saveAs = tag.getStringOr("saveAs", "");
      this.timeoutMs = tag.getIntOr("timeoutMs", 0);
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }

      MacroWaitOptions.read(tag, this);
   }

   public void fromGuiTypeTag(CompoundTag tag) {
      this.fromTag(tag);
      this.guiType = tag.getStringOr("guiType", "ANY");
      this.guiTitle = tag.getStringOr("title", tag.getStringOr("guiTitle", ""));
      this.timeoutMs = tag.getIntOr("timeoutMs", 0);
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   public static enum WaitMode {
      OPEN,
      CLOSE;
   }
}
