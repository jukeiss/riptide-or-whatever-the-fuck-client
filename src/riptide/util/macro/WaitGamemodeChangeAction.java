package riptide.util.macro;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;

public class WaitGamemodeChangeAction implements MacroAction, MacroCaptureOutput {
   public WaitGamemodeChangeAction.Match match = WaitGamemodeChangeAction.Match.ANY_CHANGE;
   public WaitGamemodeChangeAction.TargetMode gameMode = WaitGamemodeChangeAction.TargetMode.SURVIVAL;
   public boolean detectFake = false;
   public boolean listenDuringPreviousAction = false;
   public int timeoutMs = 0;
   public String saveAs = "";
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("match", this.match.name());
      tag.putString("gameMode", this.gameMode.name());
      tag.putBoolean("detectFake", this.detectFake);
      tag.putInt("timeoutMs", Math.max(0, this.timeoutMs));
      tag.putBoolean("enabled", this.enabled);
      MacroWaitOptions.write(tag, this);
      tag.putString("saveAs", this.saveAs);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.match = MacroStringList.enumValue(
         WaitGamemodeChangeAction.Match.class,
         tag.getStringOr("match", WaitGamemodeChangeAction.Match.ANY_CHANGE.name()),
         WaitGamemodeChangeAction.Match.ANY_CHANGE
      );
      this.gameMode = MacroStringList.enumValue(
         WaitGamemodeChangeAction.TargetMode.class,
         tag.getStringOr("gameMode", WaitGamemodeChangeAction.TargetMode.SURVIVAL.name()),
         WaitGamemodeChangeAction.TargetMode.SURVIVAL
      );
      this.detectFake = tag.getBooleanOr("detectFake", false);
      this.timeoutMs = Math.max(0, tag.getIntOr("timeoutMs", 0));
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }

      MacroWaitOptions.read(tag, this);
      this.saveAs = tag.getStringOr("saveAs", "");
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.WAIT_GAMEMODE_CHANGE;
   }

   @Override
   public String getDisplayName() {
      String suffix = this.match == WaitGamemodeChangeAction.Match.TO_MODE ? " -> " + this.displayTarget() : " Change";
      return "Wait Gamemode" + suffix + (this.detectFake ? " (fake ok)" : "");
   }

   @Override
   public String getIcon() {
      return "GM?";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   public boolean accepts(GameType mode) {
      return mode == null ? false : this.match == WaitGamemodeChangeAction.Match.ANY_CHANGE || mode == this.targetGameType();
   }

   public GameType targetGameType() {
      return switch (this.gameMode) {
         case SURVIVAL -> GameType.SURVIVAL;
         case CREATIVE -> GameType.CREATIVE;
         case ADVENTURE -> GameType.ADVENTURE;
         case SPECTATOR -> GameType.SPECTATOR;
      };
   }

   private String displayTarget() {
      String lower = this.gameMode.name().toLowerCase(Locale.ROOT);
      return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
   }

   @Override
   public String getSaveAs() {
      return this.saveAs;
   }

   @Override
   public void setSaveAs(String name) {
      this.saveAs = name == null ? "" : name;
   }

   public static enum Match {
      ANY_CHANGE,
      TO_MODE;
   }

   public static enum TargetMode {
      SURVIVAL,
      CREATIVE,
      ADVENTURE,
      SPECTATOR;
   }
}
