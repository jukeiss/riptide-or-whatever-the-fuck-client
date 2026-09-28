package riptide.util.macro;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.EntityHitResult;
import riptide.util.RiptideContainerTarget;
import riptide.util.RiptideRegistryLabels;
import riptide.util.RiptideSharedState;

public class InteractEntityAction implements MacroAction, WaitsForGui, PacketOrdered, RaycastAim {
   public InteractEntityAction.TargetMode targetMode = InteractEntityAction.TargetMode.ENTITY;
   public List<String> entityTargets = new ArrayList<>();
   public boolean waitForTarget = true;
   public boolean waitForGuiBefore = false;
   public boolean waitForGuiAfter = false;
   public String guiName = "";
   public boolean raycast = false;
   public volatile PacketOrder packetOrder = PacketOrder.INSTANT;
   private boolean enabled = true;

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
      RiptideContainerTarget target = this.resolveAimTarget();
      if (target == null) {
         return null;
      } else {
         return target.isBlock() ? RaycastAim.Target.ofBlock(target.blockPos()) : RaycastAim.Target.ofEntity(target.aimEntity(mc));
      }
   }

   private RiptideContainerTarget resolveAimTarget() {
      if (this.targetMode == InteractEntityAction.TargetMode.LAST_TARGET) {
         return RiptideSharedState.get().getLastContainerTarget();
      } else {
         for (String ref : this.entityTargets) {
            if (ref != null && !ref.isBlank()) {
               RiptideContainerTarget target = RiptideContainerTarget.forEntityRef(ref);
               if (target != null) {
                  return target;
               }
            }
         }

         return null;
      }
   }

   @Override
   public void execute(Minecraft mc) {
      this.tryExecute(mc);
   }

   public boolean tryExecute(Minecraft mc) {
      if (mc != null && mc.player != null) {
         if (this.targetMode != InteractEntityAction.TargetMode.LAST_TARGET) {
            boolean sentAny = false;

            for (String ref : this.entityTargets) {
               if (ref != null && !ref.isBlank()) {
                  RiptideContainerTarget target = RiptideContainerTarget.forEntityRef(ref);
                  if (target != null) {
                     if (this.waitForTarget && !target.canInteract(mc)) {
                        return false;
                     }

                     sentAny |= target.interact(mc);
                  }
               }
            }

            return sentAny;
         } else {
            RiptideContainerTarget target = RiptideSharedState.get().getLastContainerTarget();
            return target != null && (!this.waitForTarget || target.canInteract(mc)) && target.interact(mc);
         }
      } else {
         return false;
      }
   }

   public boolean canExecuteNow(Minecraft mc) {
      if (mc == null || mc.player == null) {
         return false;
      } else if (this.targetMode == InteractEntityAction.TargetMode.LAST_TARGET) {
         RiptideContainerTarget target = RiptideSharedState.get().getLastContainerTarget();
         return target != null && target.canInteract(mc);
      } else {
         boolean hasTarget = false;

         for (String ref : this.entityTargets) {
            if (ref != null && !ref.isBlank()) {
               RiptideContainerTarget target = RiptideContainerTarget.forEntityRef(ref);
               if (target == null || !target.canInteract(mc)) {
                  return false;
               }

               hasTarget = true;
            }
         }

         return hasTarget;
      }
   }

   public void captureCurrentLookTarget(Minecraft mc) {
      if (mc != null) {
         if (mc.hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() != null) {
            this.targetMode = InteractEntityAction.TargetMode.ENTITY;
            String ref = RiptideContainerTarget.toSpecificEntityRef(entityHit.getEntity());
            if (!ref.isBlank() && !this.entityTargets.contains(ref)) {
               this.entityTargets.add(ref);
            }
         }
      }
   }

   private String entityTargetLabel(String ref) {
      if (ref == null || ref.isBlank()) {
         return "(none)";
      } else if (ref.startsWith("~")) {
         String[] parts = ref.split("~", 4);
         String name = parts.length >= 4 ? parts[3] : "?";
         String type = parts.length >= 3 ? RiptideRegistryLabels.entity(parts[2]) : "?";
         return name.isBlank() ? type : name + " (" + type + ")";
      } else {
         return RiptideRegistryLabels.entity(ref);
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
      return MacroActionType.INTERACT_ENTITY;
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
      return "IE";
   }

   @Override
   public String getDisplayName() {
      String targetLabel;
      if (this.targetMode == InteractEntityAction.TargetMode.LAST_TARGET) {
         targetLabel = "Last Target";
      } else if (this.entityTargets.isEmpty()) {
         targetLabel = "(none)";
      } else if (this.entityTargets.size() == 1) {
         targetLabel = this.entityTargetLabel(this.entityTargets.get(0));
      } else {
         targetLabel = this.entityTargets.size() + " entities";
      }

      return "Interact Entity " + targetLabel + WaitsForGui.timingLabel(this);
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "INTERACT_ENTITY");
      tag.putString("targetMode", this.targetMode.name());
      ListTag list = new ListTag();

      for (String ref : this.entityTargets) {
         if (ref != null && !ref.isBlank()) {
            list.add(StringTag.valueOf(ref));
         }
      }

      tag.put("entityTargets", list);
      tag.putString("entityTarget", this.entityTargets.isEmpty() ? "" : this.entityTargets.get(0));
      tag.putBoolean("waitForTarget", this.waitForTarget);
      tag.putBoolean("waitForGuiBefore", this.waitForGuiBefore);
      tag.putBoolean("waitForGuiAfter", this.waitForGuiAfter);
      tag.putString("guiName", this.guiName);
      tag.putBoolean("raycast", this.raycast);
      tag.putBoolean("enabled", this.enabled);
      PacketOrdered.save(tag, this.packetOrder);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      String mode = tag.getStringOr("targetMode", "ENTITY");
      if ("BLOCK".equals(mode)) {
         mode = "ENTITY";
      }

      try {
         this.targetMode = InteractEntityAction.TargetMode.valueOf(mode);
      } catch (IllegalArgumentException var7) {
         this.targetMode = InteractEntityAction.TargetMode.ENTITY;
      }

      this.entityTargets.clear();
      if (tag.contains("entityTargets")) {
         for (Tag element : tag.getList("entityTargets").orElse(new ListTag())) {
            String value = element.asString().orElse("");
            if (value != null && !value.isBlank() && !this.entityTargets.contains(value)) {
               this.entityTargets.add(value);
            }
         }
      }

      if (this.entityTargets.isEmpty()) {
         String single = tag.getStringOr("entityTarget", "");
         if (single != null && !single.isBlank()) {
            this.entityTargets.add(single);
         }
      }

      this.waitForTarget = tag.getBooleanOr("waitForTarget", true);
      this.waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
      this.waitForGuiAfter = WaitsForGui.loadAfter(tag, false);
      this.guiName = tag.getStringOr("guiName", "");
      this.raycast = tag.getBooleanOr("raycast", false);
      this.enabled = tag.getBooleanOr("enabled", true);
      this.packetOrder = PacketOrdered.load(tag);
   }

   public static enum TargetMode {
      ENTITY,
      LAST_TARGET;
   }
}
