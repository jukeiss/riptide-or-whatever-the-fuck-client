package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import riptide.util.RiptideContainerTarget;
import riptide.util.RiptideInventoryHelper;
import riptide.util.RiptideMacroSneak;

public class PlaceAction implements MacroAction, WaitsForGui, PacketOrdered, RaycastAim {
   public String itemName = "";
   public ItemTarget itemTarget = new ItemTarget();
   public BlockPos blockPos = BlockPos.ZERO;
   public Direction direction = Direction.UP;
   public boolean manualDirection = false;
   public boolean waitForGuiBefore = false;
   public boolean waitForGuiAfter = false;
   public boolean waitForItem = true;
   public boolean silentSwitch = false;
   public boolean sneak = false;
   public String sneakMode = "Packet";
   public boolean interact = false;
   public InteractTiming interactTiming = InteractTiming.AFTER;
   public int interactCustomMs = 0;
   public String guiName = "";
   public volatile PacketOrder packetOrder = PacketOrder.INSTANT;
   private boolean enabled = true;
   public boolean raycast = false;

   @Override
   public void execute(Minecraft mc) {
      if (mc.player != null && mc.gameMode != null && mc.level != null) {
         if (this.blockPos != null && !mc.level.isOutsideBuildHeight(this.blockPos)) {
            if (RiptideContainerTarget.isWithinBlockReach(mc, this.blockPos)) {
               BlockHitResult hit = resolvePlaceHit(mc, this.blockPos, this.manualDirection, this.direction);
               if (hit != null) {
                  int originalSlot = mc.player.getInventory().getSelectedSlot();
                  ItemTarget target = this.resolvedItemTarget().resolveTemplate(mc);
                  if (target != null) {
                     selectItemForPlace(mc, target);
                     boolean sneaking = this.sneak;
                     if (sneaking) {
                        RiptideMacroSneak.hold(mc, this.sneakMode);
                     }

                     try {
                        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                     } finally {
                        if (sneaking) {
                           RiptideMacroSneak.release(mc, this.sneakMode);
                        }
                     }

                     if (this.silentSwitch && mc.player.getInventory().getSelectedSlot() != originalSlot) {
                        RiptideInventoryHelper.selectHotbarSlot(mc, originalSlot);
                     }
                  }
               }
            }
         }
      }
   }

   static BlockHitResult resolvePlaceHit(Minecraft mc, BlockPos targetPos, boolean manualDirection, Direction manualFace) {
      if (mc != null && mc.level != null && targetPos != null) {
         if (mc.level.isOutsideBuildHeight(targetPos)) {
            return null;
         } else {
            Direction[] order;
            if (manualDirection) {
               Direction face = manualFace == null ? Direction.UP : manualFace;
               order = new Direction[]{face};
            } else {
               order = Direction.values();
            }

            for (Direction face : order) {
               BlockPos supportPos = targetPos.relative(face.getOpposite());
               if (!mc.level.isOutsideBuildHeight(supportPos)
                  && !mc.level.getBlockState(supportPos).isAir()
                  && RiptideContainerTarget.isWithinBlockReach(mc, supportPos)
                  && mc.level.getBlockState(targetPos).isAir()) {
                  Vec3 hitPos = Vec3.atCenterOf(supportPos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
                  return new BlockHitResult(hitPos, face, supportPos, false);
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }

   private BlockHitResult resolvePlaceHit(Minecraft mc) {
      return resolvePlaceHit(mc, this.blockPos, this.manualDirection, this.direction);
   }

   private static void selectItemForPlace(Minecraft mc, ItemTarget target) {
      if (target != null && (target.hasSlot() || target.hasIdentity())) {
         if (target.hasIdentity()) {
            RiptideInventoryHelper.selectHotbarItem(mc, target, mc.player.getInventory().getSelectedSlot());
         } else {
            int visibleSlot = target.slot;
            if (visibleSlot >= 0 && visibleSlot <= 8) {
               RiptideInventoryHelper.selectHotbarSlot(mc, visibleSlot);
            } else {
               int handlerSlot = RiptideInventoryHelper.resolveConfiguredHandlerSlot(mc, visibleSlot);
               if (handlerSlot >= 0) {
                  int hotbarSlot = mc.player.getInventory().getSelectedSlot();
                  int hotbarHandlerSlot = RiptideInventoryHelper.resolveConfiguredHandlerSlot(mc, hotbarSlot);
                  if (hotbarHandlerSlot >= 0) {
                     RiptideInventoryHelper.swapHandlerSlots(mc, handlerSlot, hotbarHandlerSlot);
                  }
               }
            }
         }
      }
   }

   public void captureCurrentLookTarget(Minecraft mc) {
      if (mc != null) {
         if (mc.hitResult instanceof BlockHitResult blockHit) {
            this.blockPos = blockHit.getBlockPos();
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
      return MacroActionType.PLACE;
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   @Override
   public String getIcon() {
      return "P";
   }

   @Override
   public String getDisplayName() {
      ItemTarget target = this.resolvedItemTarget();
      String item = !target.hasSlot() && !target.hasIdentity() ? "current" : target.summaryText();
      String pos = this.blockPos.getX() + "," + this.blockPos.getY() + "," + this.blockPos.getZ();
      String face = this.manualDirection ? this.direction.name().toLowerCase() : "auto";
      return "Place " + item + " at " + pos + " " + face + WaitsForGui.timingLabel(this);
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
      tag.putString("type", "PLACE");
      ItemTarget target = this.resolvedItemTarget();
      if (target.hasSlot() || target.hasIdentity()) {
         tag.put("itemName", target.toTag());
      }

      tag.putInt("blockX", this.blockPos.getX());
      tag.putInt("blockY", this.blockPos.getY());
      tag.putInt("blockZ", this.blockPos.getZ());
      tag.putBoolean("manualDirection", this.manualDirection);
      if (this.manualDirection) {
         tag.putString("direction", this.direction.name());
      }

      tag.putBoolean("waitForGuiBefore", this.waitForGuiBefore);
      tag.putBoolean("waitForGuiAfter", this.waitForGuiAfter);
      tag.putBoolean("waitForItem", this.waitForItem);
      tag.putBoolean("silentSwitch", this.silentSwitch);
      tag.putBoolean("sneak", this.sneak);
      tag.putBoolean("raycast", this.raycast);
      tag.putString("sneakMode", this.sneakMode);
      tag.putBoolean("interact", this.interact);
      tag.putString("interactTiming", this.interactTiming.name());
      tag.putInt("interactCustomMs", this.interactCustomMs);
      tag.putString("guiName", this.guiName);
      tag.putBoolean("enabled", this.enabled);
      PacketOrdered.save(tag, this.packetOrder);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.itemTarget = tag.getCompound("itemName").map(ItemTarget::fromTag).orElseGet(() -> {
         String legacyName = tag.getStringOr("itemName", "");
         if (!legacyName.isBlank()) {
            return ItemTarget.fromLegacyEntry(legacyName);
         } else {
            int legacySlot = tag.getIntOr("slot", -1);
            return legacySlot >= 0 ? ItemTarget.slotOnly(legacySlot) : new ItemTarget();
         }
      });
      this.itemName = this.itemTarget.toLegacyEntry();
      int x = tag.getIntOr("blockX", 0);
      int y = tag.getIntOr("blockY", 0);
      int z = tag.getIntOr("blockZ", 0);
      this.blockPos = new BlockPos(x, y, z);
      this.manualDirection = tag.getBooleanOr("manualDirection", false);
      this.direction = this.manualDirection ? parseDirection(tag) : Direction.UP;
      this.waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
      this.waitForGuiAfter = WaitsForGui.loadAfter(tag, false);
      this.waitForItem = tag.getBooleanOr("waitForItem", true);
      this.silentSwitch = tag.getBooleanOr("silentSwitch", false);
      this.sneak = tag.getBooleanOr("sneak", false);
      this.raycast = tag.getBooleanOr("raycast", false);
      this.sneakMode = tag.getStringOr("sneakMode", "Packet");
      this.interact = tag.getBooleanOr("interact", false);
      this.interactTiming = InteractTiming.parse(tag.getStringOr("interactTiming", "AFTER"), InteractTiming.AFTER);
      this.interactCustomMs = Math.max(-5000, Math.min(5000, tag.getIntOr("interactCustomMs", 0)));
      this.guiName = tag.getStringOr("guiName", "");
      this.enabled = tag.getBooleanOr("enabled", true);
      this.packetOrder = PacketOrdered.load(tag);
   }

   private static Direction parseDirection(CompoundTag tag) {
      if (!tag.contains("direction")) {
         return Direction.UP;
      } else {
         String named = tag.getStringOr("direction", "");
         if (!named.isBlank()) {
            try {
               return Direction.valueOf(named.trim().toUpperCase());
            } catch (IllegalArgumentException var4) {
            }
         }

         int dirOrd = tag.getIntOr("direction", Direction.UP.ordinal());
         Direction[] values = Direction.values();
         return values[Math.max(0, Math.min(values.length - 1, dirOrd))];
      }
   }

   public ItemTarget resolvedItemTarget() {
      if (this.itemTarget == null || !this.itemTarget.hasSlot() && !this.itemTarget.hasIdentity()) {
         this.itemTarget = ItemTarget.fromLegacyEntry(this.itemName);
         return this.itemTarget;
      } else {
         return this.itemTarget;
      }
   }
}
