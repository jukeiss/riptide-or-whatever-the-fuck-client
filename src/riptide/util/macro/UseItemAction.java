package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.InteractionHand;
import riptide.util.RiptideInventoryHelper;

public class UseItemAction implements MacroAction, WaitsForGui, PacketOrdered {
   public String itemName = "";
   public ItemTarget itemTarget = new ItemTarget();
   public int slot = -1;
   public UseItemAction.UseMode useMode = UseItemAction.UseMode.AUTOMATIC;
   public boolean waitForFinish = true;
   public int holdTicks = 20;
   public int useCount = 1;
   public boolean sneak = false;
   public boolean waitForGui = false;
   public String guiName = "";
   public volatile PacketOrder packetOrder = PacketOrder.INSTANT;
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
      if (mc.player != null && mc.getConnection() != null) {
         ItemTarget target = this.resolvedItemTarget();
         target = target.resolveTemplate(mc);
         if (target != null) {
            if (target.hasSlot() || target.hasIdentity()) {
               RiptideInventoryHelper.selectHotbarItem(mc, target, mc.player.getInventory().getSelectedSlot());
            }

            mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, mc.player.getYRot(), mc.player.getXRot()));
         }
      }
   }

   public void sendRelease(Minecraft mc) {
      if (mc.player != null && mc.getConnection() != null) {
         mc.getConnection().send(new ServerboundPlayerActionPacket(Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.USE_ITEM;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "USE_ITEM");
      ItemTarget target = this.resolvedItemTarget();
      if (target.hasSlot() || target.hasIdentity()) {
         tag.put("itemName", target.toTag());
      }

      if (this.slot >= 0) {
         tag.putInt("slot", this.slot);
      }

      tag.putString("useMode", this.useMode.name());
      tag.putBoolean("waitForFinish", this.waitForFinish);
      tag.putInt("holdTicks", this.holdTicks);
      tag.putInt("useCount", this.useCount);
      tag.putBoolean("sneak", this.sneak);
      tag.putBoolean("waitForGui", this.waitForGui);
      tag.putString("guiName", this.guiName == null ? "" : this.guiName);
      tag.putBoolean("enabled", this.enabled);
      PacketOrdered.save(tag, this.packetOrder);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.itemTarget = tag.getCompound("itemName").map(ItemTarget::fromTag).orElseGet(() -> ItemTarget.fromLegacyEntry(tag.getStringOr("itemName", "")));
      this.slot = tag.contains("slot") ? Math.max(-1, tag.getIntOr("slot", -1)) : this.itemTarget.slot;
      this.itemTarget.slot = -1;
      this.itemName = this.itemTarget.toLegacyEntry();
      if (tag.contains("useMode")) {
         String modeName = tag.getStringOr("useMode", "AUTOMATIC");
         if ("INSTANT".equals(modeName)) {
            modeName = "AUTOMATIC";
         }

         if ("HOLD".equals(modeName)) {
            modeName = "CUSTOM_HOLD";
         }

         try {
            this.useMode = UseItemAction.UseMode.valueOf(modeName);
         } catch (IllegalArgumentException var4) {
            this.useMode = UseItemAction.UseMode.AUTOMATIC;
         }
      }

      this.waitForFinish = tag.getBooleanOr("waitForFinish", true);
      if (tag.contains("holdTicks")) {
         this.holdTicks = tag.getIntOr("holdTicks", 20);
      }

      if (tag.contains("useCount")) {
         this.useCount = Math.max(1, tag.getIntOr("useCount", 1));
      }

      this.sneak = tag.getBooleanOr("sneak", false);
      this.waitForGui = tag.getBooleanOr("waitForGui", false);
      this.guiName = tag.getStringOr("guiName", "");
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }

      this.packetOrder = PacketOrdered.load(tag);
   }

   @Override
   public boolean isWaitForGuiBefore() {
      return false;
   }

   @Override
   public void setWaitForGuiBefore(boolean v) {
   }

   @Override
   public boolean isWaitForGuiAfter() {
      return this.waitForGui;
   }

   @Override
   public void setWaitForGuiAfter(boolean v) {
      this.waitForGui = v;
   }

   @Override
   public String getWaitGuiName() {
      return this.guiName == null ? "" : this.guiName;
   }

   @Override
   public void setWaitGuiName(String name) {
      this.guiName = name == null ? "" : name;
   }

   @Override
   public PacketOrder getPacketOrder() {
      return this.packetOrder;
   }

   @Override
   public String getDisplayName() {
      ItemTarget target = this.resolvedItemTarget();
      String item = !target.hasSlot() && !target.hasIdentity() ? "current" : target.summaryText();
      String count = this.useCount > 1 ? " x" + this.useCount : "";
      String wait = this.waitForFinish ? ", wait" : "";
      String sneakLabel = this.sneak ? ", sneak" : "";
      String base = this.useMode == UseItemAction.UseMode.CUSTOM_HOLD
         ? "Use Item (" + item + ", hold " + this.holdTicks + "t" + wait + sneakLabel + ")"
         : "Use Item (" + item + count + wait + sneakLabel + ")";
      return base + WaitsForGui.timingLabel(this);
   }

   @Override
   public String getIcon() {
      return "U";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   private ItemTarget resolvedItemTarget() {
      ItemTarget target = this.itemTarget != null ? this.itemTarget.copy() : ItemTarget.fromLegacyEntry(this.itemName);
      if (!target.hasSlot() && !target.hasIdentity()) {
         target = ItemTarget.fromLegacyEntry(this.itemName);
      }

      target.slot = this.slot;
      return target;
   }

   public static enum UseMode {
      AUTOMATIC,
      CUSTOM_HOLD;
   }
}
