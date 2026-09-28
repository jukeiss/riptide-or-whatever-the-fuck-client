package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class FlowAction implements MacroAction {
   public FlowAction.Target target = FlowAction.Target.LABEL;
   public int amount = 1;
   public String labelName = "";
   public FlowAction.MissingPolicy onMissingLabel = FlowAction.MissingPolicy.CONTINUE;
   public boolean conditional = false;
   public MacroCondition condition = MacroCondition.defaultRoot();
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.FLOW;
   }

   public boolean shouldTake(Minecraft mc) {
      return !this.conditional || this.condition == null || this.condition.evaluate(mc);
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "FLOW");
      tag.putString("target", this.target.name());
      tag.putInt("amount", this.amount);
      tag.putString("labelName", this.labelName == null ? "" : this.labelName);
      tag.putString("onMissingLabel", this.onMissingLabel.name());
      tag.putBoolean("conditional", this.conditional);
      MacroCondition c = this.condition == null ? MacroCondition.defaultRoot() : this.condition;
      tag.put("condition", c.toTag());
      c.writeFlat(tag, "cond_");
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.target = MacroStringList.enumValue(FlowAction.Target.class, tag.getStringOr("target", "LABEL"), FlowAction.Target.LABEL);
      this.amount = Math.max(0, tag.getIntOr("amount", 1));
      this.labelName = tag.getStringOr("labelName", "");
      this.onMissingLabel = MacroStringList.enumValue(
         FlowAction.MissingPolicy.class, tag.getStringOr("onMissingLabel", "CONTINUE"), FlowAction.MissingPolicy.CONTINUE
      );
      this.conditional = tag.getBooleanOr("conditional", false);
      this.condition = MacroCondition.readForEditor(tag, "condition", "cond_");
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public String getDisplayName() {
      String where = switch (this.target) {
         case FORWARD -> "skip " + this.amount;
         case BACK -> "back " + this.amount;
         case STEP -> "step " + this.amount;
         case LABEL -> "→ " + (this.labelName != null && !this.labelName.isBlank() ? this.labelName : "?");
         case TOP -> "→ top";
         case END -> "→ end";
         case STOP -> "stop";
      };
      return "Goto " + where + (this.conditional ? " if " + (this.condition == null ? "" : this.condition.summary()) : "");
   }

   @Override
   public String getIcon() {
      return "GO";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   public static enum MissingPolicy {
      CONTINUE,
      STOP;
   }

   public static enum Target {
      FORWARD,
      BACK,
      STEP,
      LABEL,
      TOP,
      END,
      STOP;
   }
}
