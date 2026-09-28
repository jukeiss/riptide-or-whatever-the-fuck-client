package riptide.util.macro;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

public class BreakAction implements MacroAction, WaitsForGui, PacketOrdered, RaycastAim {
   public BlockPos blockPos = BlockPos.ZERO;
   public Direction direction = Direction.UP;
   public int delayTicks = 0;
   public int times = 1;
   public boolean autoTool = true;
   public boolean considerInventory = false;
   public boolean manualDirection = false;
   public boolean interact = false;
   public boolean runNextSteps = false;
   public InteractTiming interactTiming = InteractTiming.WITH;
   public int interactCustomMs = 0;
   public boolean sneak = false;
   public String sneakMode = "Packet";
   public boolean waitForGuiBefore = false;
   public boolean waitForGuiAfter = false;
   public String guiName = "";
   public volatile PacketOrder packetOrder = PacketOrder.INSTANT;
   private transient List<MacroAction> nextActions = null;
   private transient boolean nextActionsRan = false;
   private boolean enabled = true;
   public boolean raycast = false;

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public boolean isRaycast() {
      return this.raycast;
   }

   @Override
   public void setRaycast(boolean value) {
      this.raycast = value;
   }

   @Override
   public RaycastAim.Target raycastTarget(Minecraft mc) {
      return RaycastAim.Target.ofBlock(this.blockPos);
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putInt("x", this.blockPos.getX());
      tag.putInt("y", this.blockPos.getY());
      tag.putInt("z", this.blockPos.getZ());
      tag.putString("direction", this.direction.name());
      tag.putInt("delayTicks", this.delayTicks);
      tag.putInt("times", this.times);
      tag.putBoolean("autoTool", this.autoTool);
      tag.putBoolean("considerInventory", this.considerInventory);
      tag.putBoolean("manualDirection", this.manualDirection);
      tag.putBoolean("interact", this.interact);
      tag.putBoolean("runNextSteps", this.runNextSteps);
      tag.putString("interactTiming", this.interactTiming.name());
      tag.putInt("interactCustomMs", this.interactCustomMs);
      tag.putBoolean("sneak", this.sneak);
      tag.putBoolean("raycast", this.raycast);
      tag.putString("sneakMode", this.sneakMode);
      tag.putBoolean("waitForGuiBefore", this.waitForGuiBefore);
      tag.putBoolean("waitForGuiAfter", this.waitForGuiAfter);
      tag.putString("guiName", this.guiName);
      tag.putBoolean("enabled", this.enabled);
      PacketOrdered.save(tag, this.packetOrder);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("x") && tag.contains("y") && tag.contains("z")) {
         this.blockPos = new BlockPos(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0));
      }

      if (tag.contains("direction")) {
         try {
            this.direction = Direction.valueOf(tag.getStringOr("direction", "UP"));
         } catch (IllegalArgumentException var3) {
            this.direction = Direction.UP;
         }
      }

      this.delayTicks = Math.max(0, tag.getIntOr("delayTicks", 0));
      this.times = Math.max(0, tag.getIntOr("times", 1));
      this.autoTool = tag.getBooleanOr("autoTool", true);
      this.considerInventory = tag.getBooleanOr("considerInventory", false);
      this.manualDirection = tag.getBooleanOr("manualDirection", false);
      this.interact = tag.getBooleanOr("interact", false);
      this.runNextSteps = tag.getBooleanOr("runNextSteps", false);
      this.interactTiming = InteractTiming.parse(tag.getStringOr("interactTiming", "WITH"), InteractTiming.WITH);
      this.interactCustomMs = Math.max(-5000, Math.min(5000, tag.getIntOr("interactCustomMs", 0)));
      this.sneak = tag.getBooleanOr("sneak", false);
      this.raycast = tag.getBooleanOr("raycast", false);
      this.sneakMode = tag.getStringOr("sneakMode", "Packet");
      this.waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
      this.waitForGuiAfter = WaitsForGui.loadAfter(tag, false);
      this.guiName = tag.getStringOr("guiName", "");
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }

      this.packetOrder = PacketOrdered.load(tag);
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.BREAK;
   }

   public void setNextActions(List<MacroAction> nextActions) {
      this.nextActions = nextActions;
      this.nextActionsRan = false;
   }

   public List<MacroAction> getNextActions() {
      return this.nextActions;
   }

   public void markNextActionsRan() {
      this.nextActionsRan = true;
   }

   public boolean didRunNextActions() {
      return this.nextActionsRan;
   }

   @Override
   public String getDisplayName() {
      return "Break @ "
         + this.blockPos.getX()
         + ","
         + this.blockPos.getY()
         + ","
         + this.blockPos.getZ()
         + (this.times == 0 ? " ∞" : " x" + this.times)
         + (this.interact ? (this.runNextSteps ? " +Next" : " +GUI") : "")
         + WaitsForGui.timingLabel(this);
   }

   @Override
   public boolean isWaitForGuiBefore() {
      return this.waitForGuiBefore;
   }

   @Override
   public void setWaitForGuiBefore(boolean v) {
      this.waitForGuiBefore = v;
   }

   @Override
   public boolean isWaitForGuiAfter() {
      return this.waitForGuiAfter;
   }

   @Override
   public void setWaitForGuiAfter(boolean v) {
      this.waitForGuiAfter = v;
   }

   @Override
   public String getWaitGuiName() {
      return this.guiName;
   }

   @Override
   public void setWaitGuiName(String name) {
      this.guiName = name;
   }

   @Override
   public PacketOrder getPacketOrder() {
      return this.packetOrder;
   }

   @Override
   public String getIcon() {
      return "BRK";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }
}
