package riptide.util.macro;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import riptide.util.RiptideRegistryLabels;

public class WaitForEntityAction implements MacroAction, MacroCaptureOutput {
   public List<String> entityIds = new ArrayList<>();
   public boolean mustBeLookingAt = false;
   public WaitForEntityAction.CheckMode checkMode = WaitForEntityAction.CheckMode.RADIUS;
   public boolean centerOnPlayer = true;
   public double radius = 6.0;
   public boolean containerEntitiesOnly = false;
   public int timeoutMs = 0;
   public double x = 0.0;
   public double y = 0.0;
   public double z = 0.0;
   public boolean listenDuringPreviousAction = false;
   public String saveAs = "";
   private boolean enabled = true;

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      ListTag list = new ListTag();

      for (String id : this.entityIds) {
         list.add(StringTag.valueOf(id));
      }

      tag.put("entityIds", list);
      tag.putBoolean("mustBeLookingAt", this.mustBeLookingAt);
      tag.putBoolean("centerOnPlayer", this.centerOnPlayer);
      tag.putDouble("radius", this.radius);
      tag.putDouble("x", this.x);
      tag.putDouble("y", this.y);
      tag.putDouble("z", this.z);
      tag.putBoolean("containerEntitiesOnly", this.containerEntitiesOnly);
      tag.putInt("timeoutMs", this.timeoutMs);
      tag.putBoolean("enabled", this.enabled);
      tag.putString("checkMode", this.checkMode.name());
      MacroWaitOptions.write(tag, this);
      tag.putString("saveAs", this.saveAs);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.entityIds.clear();
      if (tag.contains("entityIds")) {
         for (Tag el : tag.getList("entityIds").orElse(new ListTag())) {
            String s = el.asString().orElse("");
            if (!s.isEmpty()) {
               this.entityIds.add(s);
            }
         }
      } else if (tag.contains("entityName")) {
         String oldName = tag.getStringOr("entityName", "");
         if (!oldName.isEmpty()) {
            String tryId = "minecraft:" + oldName.toLowerCase().replace(" ", "_");

            try {
               Identifier id = Identifier.parse(tryId);
               if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                  this.entityIds.add(tryId);
               }
            } catch (Exception var7) {
            }
         }
      }

      this.mustBeLookingAt = tag.getBooleanOr("mustBeLookingAt", false);
      if (tag.contains("checkMode")) {
         try {
            this.checkMode = WaitForEntityAction.CheckMode.valueOf(tag.getStringOr("checkMode", "RADIUS"));
         } catch (Exception var6) {
            this.checkMode = WaitForEntityAction.CheckMode.RADIUS;
         }
      } else {
         this.checkMode = this.mustBeLookingAt ? WaitForEntityAction.CheckMode.LOOKING_AT : WaitForEntityAction.CheckMode.RADIUS;
      }

      this.centerOnPlayer = tag.getBooleanOr("centerOnPlayer", true);
      this.radius = tag.getDoubleOr("radius", 6.0);
      this.containerEntitiesOnly = tag.getBooleanOr("containerEntitiesOnly", false);
      this.timeoutMs = tag.getIntOr("timeoutMs", 0);
      this.x = tag.getDoubleOr("x", 0.0);
      this.y = tag.getDoubleOr("y", 0.0);
      this.z = tag.getDoubleOr("z", 0.0);
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }

      MacroWaitOptions.read(tag, this);
      this.saveAs = tag.getStringOr("saveAs", "");
   }

   public void fromLegacyTargetTag(CompoundTag tag) {
      this.entityIds.clear();
      String entityId = tag.getStringOr("entityId", "");
      if (!entityId.isBlank()) {
         this.entityIds.add(entityId);
      }

      String condition = tag.getStringOr("condition", "LOOKING_AT");

      this.checkMode = switch (condition) {
         case "WITHIN_REACH" -> WaitForEntityAction.CheckMode.WITHIN_REACH;
         case "MOUNTED_IN" -> WaitForEntityAction.CheckMode.MOUNTED_IN;
         case "NEARBY" -> WaitForEntityAction.CheckMode.NEARBY;
         default -> WaitForEntityAction.CheckMode.LOOKING_AT;
      };
      this.radius = tag.getDoubleOr("range", 5.0);
      this.centerOnPlayer = true;
      this.containerEntitiesOnly = tag.getBooleanOr("containerEntitiesOnly", false);
      this.timeoutMs = tag.getIntOr("timeoutMs", 0);
      MacroWaitOptions.read(tag, this);
   }

   public boolean matchesEntityTarget(Minecraft mc) {
      if (mc != null && mc.player != null && mc.level != null) {
         return switch (this.checkMode) {
            case RADIUS, NEARBY -> {
               Vec3 center = this.centerOnPlayer ? new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ()) : new Vec3(this.x, this.y, this.z);
               double radiusSq = this.radius * this.radius;
               boolean found = false;

               for (Entity entity : mc.level.entitiesForRendering()) {
                  if (entity != mc.player && !(entity.distanceToSqr(center) > radiusSq) && this.entityMatches(entity)) {
                     found = true;
                     break;
                  }
               }

               yield found;
            }
            case LOOKING_AT -> this.entityMatches(mc.crosshairPickEntity);
            case WITHIN_REACH -> {
               double reachSq = mc.player.blockInteractionRange() * mc.player.blockInteractionRange();
               boolean found = false;

               for (Entity entity : mc.level.entitiesForRendering()) {
                  if (entity != mc.player && !(entity.distanceToSqr(mc.player) > reachSq) && this.entityMatches(entity)) {
                     found = true;
                     break;
                  }
               }

               yield found;
            }
            case MOUNTED_IN -> this.entityMatches(mc.player.getVehicle());
         };
      } else {
         return false;
      }
   }

   public boolean entityMatches(Entity entity) {
      if (entity == null) {
         return false;
      } else {
         String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
         if (this.containerEntitiesOnly && !type.contains("boat") && !type.contains("minecart") && !type.contains("llama") && !type.contains("chest")) {
            return false;
         } else if (this.entityIds.isEmpty()) {
            return true;
         } else {
            for (String entry : this.entityIds) {
               if (entry != null && !entry.isBlank()) {
                  if (!entry.startsWith("~")) {
                     if (type.equalsIgnoreCase(entry) || entity.getStringUUID().equalsIgnoreCase(entry) || entity.getName().getString().equalsIgnoreCase(entry)
                        )
                      {
                        return true;
                     }
                  } else {
                     String[] parts = entry.split("~", 4);
                     if (parts.length >= 2 && entity.getStringUUID().equalsIgnoreCase(parts[1])) {
                        return true;
                     }

                     if (parts.length >= 3 && type.equalsIgnoreCase(parts[2])) {
                        return true;
                     }
                  }
               }
            }

            return false;
         }
      }
   }

   public Entity findMatchingEntity(Minecraft mc) {
      if (mc != null && mc.player != null && mc.level != null) {
         if (this.checkMode == WaitForEntityAction.CheckMode.LOOKING_AT) {
            return this.entityMatches(mc.crosshairPickEntity) ? mc.crosshairPickEntity : null;
         } else if (this.checkMode == WaitForEntityAction.CheckMode.MOUNTED_IN) {
            return this.entityMatches(mc.player.getVehicle()) ? mc.player.getVehicle() : null;
         } else {
            Vec3 center = this.centerOnPlayer ? mc.player.position() : new Vec3(this.x, this.y, this.z);
            double maxDistance = this.checkMode == WaitForEntityAction.CheckMode.WITHIN_REACH ? mc.player.blockInteractionRange() : this.radius;
            double maxDistanceSq = maxDistance * maxDistance;
            Entity nearest = null;
            double nearestDistance = Double.MAX_VALUE;

            for (Entity entity : mc.level.entitiesForRendering()) {
               if (entity != mc.player && this.entityMatches(entity)) {
                  double distance = entity.distanceToSqr(center);
                  if (distance <= maxDistanceSq && distance < nearestDistance) {
                     nearest = entity;
                     nearestDistance = distance;
                  }
               }
            }

            return nearest;
         }
      } else {
         return null;
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.WAIT_ENTITY;
   }

   @Override
   public String getDisplayName() {
      String first;
      if (this.entityIds.isEmpty()) {
         first = "any";
      } else {
         String e0 = this.entityIds.get(0);
         if (e0.startsWith("~")) {
            String[] p = e0.split("~", 4);
            String display = p.length >= 4 ? p[3] : "";
            String type = p.length >= 3 ? RiptideRegistryLabels.entity(p[2]) : "?";
            first = "SPEC " + (display != null && !display.isBlank() ? display : type);
         } else {
            first = RiptideRegistryLabels.entity(e0);
         }

         if (this.entityIds.size() > 1) {
            first = first + " (+" + (this.entityIds.size() - 1) + ")";
         }
      }
      String modeStr = switch (this.checkMode) {
         case RADIUS -> "r=" + (int)this.radius;
         case LOOKING_AT -> "look";
         case WITHIN_REACH -> "reach";
         case MOUNTED_IN -> "mounted";
         case NEARBY -> "nearby " + (int)this.radius;
      };
      return "Wait Entity: " + first + " (" + modeStr + ")";
   }

   @Override
   public String getIcon() {
      return "ENT";
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
   public String getSaveAs() {
      return this.saveAs;
   }

   @Override
   public void setSaveAs(String name) {
      this.saveAs = name == null ? "" : name;
   }

   public static enum CheckMode {
      RADIUS,
      LOOKING_AT,
      WITHIN_REACH,
      MOUNTED_IN,
      NEARBY;
   }
}
