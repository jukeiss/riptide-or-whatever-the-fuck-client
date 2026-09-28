package riptide.util.macro;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class MoveAction implements MacroAction {
   public MoveAction.Direction direction = MoveAction.Direction.FORWARD;
   public int durationTicks = 20;
   public boolean nonBlocking = false;
   private boolean enabled = true;

   public MoveAction() {
   }

   public MoveAction(MoveAction.Direction direction, int durationTicks) {
      this.direction = direction;
      this.durationTicks = durationTicks;
   }

   public KeyMapping getKey(Minecraft mc) {
      if (mc.options == null) {
         return null;
      } else {
         return switch (this.direction) {
            case FORWARD -> mc.options.keyUp;
            case BACKWARD -> mc.options.keyDown;
            case LEFT -> mc.options.keyLeft;
            case RIGHT -> mc.options.keyRight;
         };
      }
   }

   @Override
   public void execute(Minecraft mc) {
      KeyMapping key = this.getKey(mc);
      if (key != null) {
         key.setDown(true);
      }
   }

   public void release(Minecraft mc) {
      KeyMapping key = this.getKey(mc);
      if (key != null) {
         key.setDown(false);
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("direction", this.direction.name());
      tag.putInt("durationTicks", this.durationTicks);
      tag.putBoolean("nonBlocking", this.nonBlocking);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("direction")) {
         try {
            this.direction = MoveAction.Direction.valueOf(tag.getStringOr("direction", "FORWARD"));
         } catch (Exception var3) {
            this.direction = MoveAction.Direction.FORWARD;
         }
      }

      if (tag.contains("durationTicks")) {
         this.durationTicks = tag.getIntOr("durationTicks", 20);
      }

      if (tag.contains("nonBlocking")) {
         this.nonBlocking = tag.getBooleanOr("nonBlocking", false);
      }

      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.MOVE;
   }

   @Override
   public String getDisplayName() {
      return "Move " + this.direction.displayName() + " (" + this.durationTicks + "t)" + (this.nonBlocking ? " [BG]" : "");
   }

   @Override
   public String getIcon() {
      return "MOV";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   public static enum Direction {
      FORWARD,
      BACKWARD,
      LEFT,
      RIGHT;

      public String displayName() {
         return this.name().charAt(0) + this.name().substring(1).toLowerCase();
      }

      public MoveAction.Direction next() {
         MoveAction.Direction[] vals = values();
         return vals[(this.ordinal() + 1) % vals.length];
      }
   }
}
