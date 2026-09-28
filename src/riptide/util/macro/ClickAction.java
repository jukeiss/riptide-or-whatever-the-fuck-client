package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult.Type;
import riptide.mixin.accessor.RiptideMinecraftAccessor;

public class ClickAction implements MacroAction, WaitsForGui, PacketOrdered {
   public ClickAction.ContainerInput type;
   public int clickCount = 1;
   public boolean waitForGuiBefore = false;
   public boolean waitForGuiAfter = false;
   public String guiName = "";
   public volatile PacketOrder packetOrder = PacketOrder.INSTANT;

   public ClickAction() {
   }

   public ClickAction(ClickAction.ContainerInput type) {
      this.type = type;
      this.clickCount = 1;
   }

   @Override
   public void execute(Minecraft mc) {
      if (mc.player != null && mc.gameMode != null) {
         if (this.type == ClickAction.ContainerInput.LEFT) {
            ((RiptideMinecraftAccessor)mc).riptide$startAttack();
         } else if (mc.hitResult == null || mc.hitResult.getType() == Type.MISS) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
         } else if (mc.hitResult.getType() == Type.ENTITY) {
            mc.gameMode.interact(mc.player, ((EntityHitResult)mc.hitResult).getEntity(), (EntityHitResult)mc.hitResult, InteractionHand.MAIN_HAND);
         } else if (mc.hitResult.getType() == Type.BLOCK) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, (BlockHitResult)mc.hitResult);
         }
      }
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
   public MacroActionType getType() {
      return MacroActionType.CLICK;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putString("clickType", (this.type == null ? ClickAction.ContainerInput.RIGHT : this.type).name());
      tag.putInt("clickCount", this.clickCount);
      tag.putBoolean("waitForGuiBefore", this.waitForGuiBefore);
      tag.putBoolean("waitForGuiAfter", this.waitForGuiAfter);
      tag.putString("guiName", this.guiName);
      PacketOrdered.save(tag, this.packetOrder);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("clickType")) {
         try {
            this.type = ClickAction.ContainerInput.valueOf(tag.getStringOr("clickType", "RIGHT"));
         } catch (IllegalArgumentException var3) {
            this.type = ClickAction.ContainerInput.RIGHT;
         }
      }

      if (this.type == null) {
         this.type = ClickAction.ContainerInput.RIGHT;
      }

      if (tag.contains("clickCount")) {
         this.clickCount = tag.getIntOr("clickCount", 1);
      }

      this.waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
      this.waitForGuiAfter = WaitsForGui.loadAfter(tag, false);
      if (tag.contains("guiName")) {
         this.guiName = tag.getStringOr("guiName", "");
      }

      this.packetOrder = PacketOrdered.load(tag);
   }

   @Override
   public String getDisplayName() {
      String base = this.type == ClickAction.ContainerInput.LEFT ? "Left Click" : "Right Click";
      if (this.clickCount > 1) {
         base = base + " x" + this.clickCount;
      }

      return base + WaitsForGui.timingLabel(this);
   }

   @Override
   public String getIcon() {
      return this.type == ClickAction.ContainerInput.LEFT ? "L" : "R";
   }

   public static enum ContainerInput {
      LEFT,
      RIGHT;
   }
}
