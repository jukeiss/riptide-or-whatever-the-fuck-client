package riptide.util.macro;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import riptide.util.RiptideContainerTarget;
import riptide.util.RiptideRegistryLabels;
import riptide.util.RiptideSharedState;

public class OpenContainerAction implements MacroAction, WaitsForGui, RaycastAim {
   public OpenContainerAction.TargetMode targetMode = OpenContainerAction.TargetMode.BLOCK;
   public BlockPos blockPos = BlockPos.ZERO;
   public String entityTarget = "";
   public List<String> entityTargets = new ArrayList<>();
   public boolean waitForTarget = true;
   public boolean waitForGuiBefore = false;
   public boolean waitForGuiAfter = true;
   public String guiName = "";
   public boolean raycast = false;
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
      if (this.targetMode == OpenContainerAction.TargetMode.BLOCK) {
         return RaycastAim.Target.ofBlock(this.blockPos);
      } else {
         RiptideContainerTarget target = this.targetMode == OpenContainerAction.TargetMode.LAST_TARGET
            ? RiptideSharedState.get().getLastContainerTarget()
            : this.firstEntityTarget();
         if (target == null) {
            return null;
         } else {
            return target.isBlock() ? RaycastAim.Target.ofBlock(target.blockPos()) : RaycastAim.Target.ofEntity(target.aimEntity(mc));
         }
      }
   }

   private RiptideContainerTarget firstEntityTarget() {
      for (String ref : this.effectiveEntityTargets()) {
         if (ref != null && !ref.isBlank()) {
            RiptideContainerTarget target = RiptideContainerTarget.forEntityRef(ref);
            if (target != null) {
               return target;
            }
         }
      }

      return null;
   }

   @Override
   public void execute(Minecraft mc) {
      this.tryExecute(mc);
   }

   public boolean tryExecute(Minecraft mc) {
      if (mc != null && mc.player != null) {
         if (this.targetMode == OpenContainerAction.TargetMode.ENTITY) {
            List<String> refs = this.effectiveEntityTargets();
            boolean sentAny = false;

            for (String ref : refs) {
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
            RiptideContainerTarget target = this.targetMode == OpenContainerAction.TargetMode.BLOCK
               ? RiptideContainerTarget.forBlock(this.blockPos)
               : RiptideSharedState.get().getLastContainerTarget();
            return target != null && (!this.waitForTarget || target.canInteract(mc)) && target.interact(mc);
         }
      } else {
         return false;
      }
   }

   public boolean canExecuteNow(Minecraft mc) {
      if (mc == null || mc.player == null) {
         return false;
      } else if (this.targetMode == OpenContainerAction.TargetMode.ENTITY) {
         List<String> refs = this.effectiveEntityTargets();
         boolean hasTarget = false;

         for (String ref : refs) {
            if (ref != null && !ref.isBlank()) {
               RiptideContainerTarget target = RiptideContainerTarget.forEntityRef(ref);
               if (target == null || !target.canInteract(mc)) {
                  return false;
               }

               hasTarget = true;
            }
         }

         return hasTarget;
      } else {
         RiptideContainerTarget target = this.targetMode == OpenContainerAction.TargetMode.BLOCK
            ? RiptideContainerTarget.forBlock(this.blockPos)
            : RiptideSharedState.get().getLastContainerTarget();
         return target != null && target.canInteract(mc);
      }
   }

   public void captureCurrentLookTarget(Minecraft mc) {
      if (mc != null) {
         if (mc.hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() != null) {
            this.targetMode = OpenContainerAction.TargetMode.ENTITY;
            this.entityTarget = RiptideContainerTarget.toSpecificEntityRef(entityHit.getEntity());
            if (!this.entityTarget.isBlank() && !this.entityTargets.contains(this.entityTarget)) {
               this.entityTargets.add(this.entityTarget);
            }
         } else if (mc.hitResult instanceof BlockHitResult blockHit) {
            this.targetMode = OpenContainerAction.TargetMode.BLOCK;
            this.blockPos = blockHit.getBlockPos();
            this.entityTarget = "";
            this.entityTargets.clear();
         }
      }
   }

   public String entityTargetLabel() {
      return entityTargetLabel(this.entityTarget);
   }

   private static String entityTargetLabel(String targetRef) {
      if (targetRef == null || targetRef.isBlank()) {
         return "(none)";
      } else if (targetRef.startsWith("~")) {
         String[] parts = targetRef.split("~", 4);
         String name = parts.length >= 4 ? parts[3] : "?";
         String type = parts.length >= 3 ? RiptideRegistryLabels.entity(parts[2]) : "?";
         return name.isBlank() ? type : name + " (" + type + ")";
      } else {
         return RiptideRegistryLabels.entity(targetRef);
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
   public MacroActionType getType() {
      return MacroActionType.OPEN_CONTAINER;
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
      return "OC";
   }

   @Override
   public String getDisplayName() {
      String targetLabel = switch (this.targetMode) {
         case BLOCK -> this.blockPos.getX() + "," + this.blockPos.getY() + "," + this.blockPos.getZ();
         case ENTITY -> {
            List<String> refs = this.effectiveEntityTargets();
            yield refs.isEmpty() ? "(none)" : (refs.size() == 1 ? entityTargetLabel(refs.get(0)) : refs.size() + " entities");
         }
         case LAST_TARGET -> "Last Target";
      };
      String base = "Open Container " + targetLabel;
      return base + WaitsForGui.timingLabel(this);
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "OPEN_CONTAINER");
      tag.putString("targetMode", this.targetMode.name());
      tag.putInt("x", this.blockPos.getX());
      tag.putInt("y", this.blockPos.getY());
      tag.putInt("z", this.blockPos.getZ());
      List<String> refs = this.effectiveEntityTargets();
      tag.putString("entityTarget", refs.isEmpty() ? "" : refs.get(0));
      ListTag entityTargets = new ListTag();

      for (String ref : refs) {
         if (ref != null && !ref.isBlank()) {
            entityTargets.add(StringTag.valueOf(ref));
         }
      }

      tag.put("entityTargets", entityTargets);
      tag.putBoolean("waitForTarget", this.waitForTarget);
      tag.putBoolean("waitForGuiBefore", this.waitForGuiBefore);
      tag.putBoolean("waitForGuiAfter", this.waitForGuiAfter);
      tag.putString("guiName", this.guiName);
      tag.putBoolean("raycast", this.raycast);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      int x = tag.getIntOr("x", 0);
      int y = tag.getIntOr("y", 0);
      int z = tag.getIntOr("z", 0);
      this.blockPos = new BlockPos(x, y, z);

      try {
         this.targetMode = OpenContainerAction.TargetMode.valueOf(tag.getStringOr("targetMode", "BLOCK"));
      } catch (IllegalArgumentException var9) {
         this.targetMode = OpenContainerAction.TargetMode.BLOCK;
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

      this.entityTarget = tag.getStringOr("entityTarget", "");
      if (this.entityTargets.isEmpty() && this.entityTarget != null && !this.entityTarget.isBlank()) {
         this.entityTargets.add(this.entityTarget);
      } else if (!this.entityTargets.isEmpty()) {
         this.entityTarget = this.entityTargets.get(0);
      }

      this.waitForTarget = tag.getBooleanOr("waitForTarget", true);
      this.waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
      this.waitForGuiAfter = WaitsForGui.loadAfter(tag, true);
      this.guiName = tag.getStringOr("guiName", "");
      this.raycast = tag.getBooleanOr("raycast", false);
      this.enabled = tag.getBooleanOr("enabled", true);
   }

   private List<String> effectiveEntityTargets() {
      if (!this.entityTargets.isEmpty()) {
         return this.entityTargets;
      } else {
         return this.entityTarget != null && !this.entityTarget.isBlank() ? List.of(this.entityTarget) : List.of();
      }
   }

   public static enum TargetMode {
      BLOCK,
      ENTITY,
      LAST_TARGET;
   }
}
