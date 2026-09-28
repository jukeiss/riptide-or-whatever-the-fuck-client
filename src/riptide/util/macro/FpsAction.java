package riptide.util.macro;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class FpsAction implements MacroAction {
   public int maxFps = 60;
   public String endMode = FpsAction.EndMode.TIMEOUT.name();
   public int stepCount = 1;
   public boolean useTicks = false;
   public int durationMs = 1000;
   public int durationTicks = 20;
   public boolean continueNextActions = false;
   private boolean enabled = true;

   public FpsAction() {
   }

   public FpsAction(int maxFps, int durationMs) {
      this.maxFps = Math.max(0, maxFps);
      this.durationMs = Math.max(0, durationMs);
   }

   public FpsAction.EndMode endMode() {
      return FpsAction.EndMode.parse(this.endMode);
   }

   public int normalizedStepCount() {
      return Math.max(1, this.stepCount);
   }

   public long durationMillis() {
      return this.useTicks ? Math.max(0, this.durationTicks) * 50L : Math.max(0, this.durationMs);
   }

   @Override
   public void execute(Minecraft mc) {
      long owner = MacroExecutor.currentRunId();
      FpsLimitController.apply(owner < 0L ? 0L : owner, Math.max(0, this.maxFps), this.durationMillis() * 1000000L);
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putInt("maxFps", this.maxFps);
      tag.putString("endMode", this.endMode().name());
      tag.putInt("stepCount", Math.max(1, this.stepCount));
      tag.putBoolean("useTicks", this.useTicks);
      tag.putInt("durationMs", this.durationMs);
      tag.putInt("durationTicks", this.durationTicks);
      tag.putBoolean("continueNextActions", this.continueNextActions);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("maxFps")) {
         this.maxFps = Math.max(0, tag.getIntOr("maxFps", 60));
      }

      if (tag.contains("endMode")) {
         this.endMode = FpsAction.EndMode.parse(tag.getStringOr("endMode", FpsAction.EndMode.TIMEOUT.name())).name();
      }

      if (tag.contains("stepCount")) {
         this.stepCount = Math.max(1, tag.getIntOr("stepCount", 1));
      }

      if (tag.contains("useTicks")) {
         this.useTicks = tag.getBooleanOr("useTicks", false);
      }

      if (tag.contains("durationMs")) {
         this.durationMs = Math.max(0, tag.getIntOr("durationMs", 1000));
      }

      if (tag.contains("durationTicks")) {
         this.durationTicks = Math.max(0, tag.getIntOr("durationTicks", 20));
      }

      if (tag.contains("continueNextActions")) {
         this.continueNextActions = tag.getBooleanOr("continueNextActions", false);
      }

      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.FPS;
   }

   @Override
   public String getDisplayName() {
      String cap = this.maxFps <= 0 ? "Freeze" : this.maxFps + " fps";

      String window = switch (this.endMode()) {
         case TIMEOUT -> this.useTicks ? Math.max(0, this.durationTicks) + " ticks" : Math.max(0, this.durationMs) + " ms";
         case STEPS -> this.normalizedStepCount() + (this.normalizedStepCount() == 1 ? " step" : " steps");
         case END -> "until end";
      };
      String suffix = this.endMode() == FpsAction.EndMode.TIMEOUT && this.continueNextActions ? " [cont]" : "";
      return "FPS: " + cap + " (" + window + ")" + suffix;
   }

   @Override
   public String getIcon() {
      return "FPS";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   public static enum EndMode {
      TIMEOUT,
      STEPS,
      END;

      public static FpsAction.EndMode parse(String raw) {
         if (raw == null) {
            return TIMEOUT;
         } else {
            try {
               return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException var2) {
               return TIMEOUT;
            }
         }
      }
   }
}
