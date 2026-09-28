package riptide.util.macro;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;

public class WaitGuiTypeAction implements MacroAction, MacroCaptureOutput {
   public WaitGuiTypeAction.WaitMode waitMode = WaitGuiTypeAction.WaitMode.OPEN;
   public String guiType = "ANY";
   public String title = "";
   public MacroCapturePattern.Mode matchMode = MacroCapturePattern.Mode.MATCH;
   public String saveAs = "";
   public int timeoutMs = 0;
   public boolean listenDuringPreviousAction = false;

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.WAIT_GUI_TYPE;
   }

   public boolean matches(Minecraft mc) {
      return this.capture(mc).isPresent();
   }

   public Optional<Map<String, MacroValue>> capture(Minecraft mc) {
      if (mc != null && MacroGuiMatcher.matches(mc.gui.screen(), this.guiType, "")) {
         Screen screen = mc.gui.screen();
         String actual = screen.getTitle() == null ? "" : screen.getTitle().getString();
         Map<String, MacroValue> values = new LinkedHashMap<>();
         if (this.matchMode == MacroCapturePattern.Mode.MATCH) {
            MacroTemplate.Resolution resolved = MacroVariables.resolve(this.title, mc);
            if (!resolved.success()) {
               return Optional.empty();
            }

            if (!resolved.value().isBlank() && !MacroGuiMatcher.matches(screen, this.guiType, resolved.value())) {
               return Optional.empty();
            }
         } else {
            Optional<MacroCapturePattern.Result> result = MacroCapturePattern.match(this.matchMode, this.title, actual);
            if (result.isEmpty()) {
               return Optional.empty();
            }

            values.putAll(result.get().values());
         }

         if (this.saveAs != null && !this.saveAs.isBlank()) {
            values.put(
               this.saveAs,
               MacroValue.structured(
                  MacroValue.Kind.GUI, actual, Map.of("title", MacroValue.text(actual), "type", MacroValue.text(MacroGuiMatcher.semanticName(screen)))
               )
            );
         }

         return Optional.of(values);
      } else {
         return Optional.empty();
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "WAIT_GUI_TYPE");
      tag.putString("waitMode", this.waitMode.name());
      tag.putString("guiType", this.guiType);
      tag.putString("title", this.title);
      tag.putString("matchMode", (this.matchMode == null ? MacroCapturePattern.Mode.MATCH : this.matchMode).name());
      tag.putString("saveAs", this.saveAs == null ? "" : this.saveAs);
      tag.putInt("timeoutMs", this.timeoutMs);
      MacroWaitOptions.write(tag, this);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.waitMode = MacroStringList.enumValue(WaitGuiTypeAction.WaitMode.class, tag.getStringOr("waitMode", "OPEN"), WaitGuiTypeAction.WaitMode.OPEN);
      this.guiType = tag.getStringOr("guiType", "ANY");
      this.title = tag.getStringOr("title", "");
      this.matchMode = MacroStringList.enumValue(MacroCapturePattern.Mode.class, tag.getStringOr("matchMode", "MATCH"), MacroCapturePattern.Mode.MATCH);
      this.saveAs = tag.getStringOr("saveAs", "");
      this.timeoutMs = tag.getIntOr("timeoutMs", 0);
      MacroWaitOptions.read(tag, this);
   }

   @Override
   public String getDisplayName() {
      return "Wait GUI " + this.guiType;
   }

   @Override
   public String getIcon() {
      return "W";
   }

   @Override
   public String getSaveAs() {
      return this.saveAs;
   }

   @Override
   public void setSaveAs(String value) {
      this.saveAs = value == null ? "" : value;
   }

   public static enum WaitMode {
      OPEN,
      CLOSE;
   }
}
