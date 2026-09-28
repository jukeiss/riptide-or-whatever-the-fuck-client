package riptide.util.macro;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import riptide.util.RiptideRegistryLabels;

public class LookAtBlockAction implements MacroAction {
   public int blockX = 0;
   public int blockY = 0;
   public int blockZ = 0;
   public LookAtBlockAction.TargetMode targetMode = LookAtBlockAction.TargetMode.SPECIFIC;
   public List<String> blockIds = new ArrayList<>();
   public List<String> entityIds = new ArrayList<>();
   public double searchRadius = 16.0;
   public boolean smooth;
   public int smoothness = 6;
   public boolean waitForCompletion = true;
   private boolean enabled = true;

   public LookAtBlockAction() {
   }

   public LookAtBlockAction(int x, int y, int z) {
      this.blockX = x;
      this.blockY = y;
      this.blockZ = z;
      this.targetMode = LookAtBlockAction.TargetMode.SPECIFIC;
   }

   public static String toSpecificEntityEntry(Entity entity) {
      if (entity == null) {
         return "";
      } else {
         String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
         String displayName = entity.getDisplayName().getString().replaceAll("§.", "").trim();
         return "~" + entity.getStringUUID() + "~" + typeId + "~" + displayName;
      }
   }

   public double getRotationStep() {
      return RotateAction.smoothnessToRotationStep(this.smoothness);
   }

   public LookAtBlockAction.RotationTarget resolveRotationTarget(Minecraft mc) {
      if (mc != null && mc.player != null && mc.level != null) {
         return switch (this.targetMode) {
            case SPECIFIC -> rotationTo(mc.player.getEyePosition(), new Vec3(this.blockX + 0.5, this.blockY + 0.5, this.blockZ + 0.5));
            case BLOCK -> this.resolveNearestBlockTarget(mc);
            case ENTITY -> this.resolveNearestEntityTarget(mc);
         };
      } else {
         return null;
      }
   }

   @Override
   public void execute(Minecraft mc) {
      if (mc != null && mc.player != null) {
         LookAtBlockAction.RotationTarget target = this.resolveRotationTarget(mc);
         if (target != null && !this.smooth) {
            mc.player.setYRot(target.yaw());
            mc.player.setXRot(target.pitch());
         }
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.getType().name());
      tag.putInt("blockX", this.blockX);
      tag.putInt("blockY", this.blockY);
      tag.putInt("blockZ", this.blockZ);
      tag.putString("targetMode", this.targetMode.name());
      ListTag blocks = new ListTag();

      for (String id : this.blockIds) {
         blocks.add(StringTag.valueOf(id));
      }

      tag.put("blockIds", blocks);
      ListTag entities = new ListTag();

      for (String id : this.entityIds) {
         entities.add(StringTag.valueOf(id));
      }

      tag.put("entityIds", entities);
      tag.putDouble("searchRadius", this.searchRadius);
      tag.putBoolean("smooth", this.smooth);
      tag.putInt("smoothness", RotateAction.clampSmoothness(this.smoothness));
      tag.putBoolean("waitForCompletion", this.waitForCompletion);
      tag.putBoolean("enabled", this.enabled);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      if (tag.contains("blockX")) {
         this.blockX = tag.getIntOr("blockX", 0);
      }

      if (tag.contains("blockY")) {
         this.blockY = tag.getIntOr("blockY", 0);
      }

      if (tag.contains("blockZ")) {
         this.blockZ = tag.getIntOr("blockZ", 0);
      }

      if (tag.contains("targetMode")) {
         try {
            this.targetMode = LookAtBlockAction.TargetMode.valueOf(tag.getStringOr("targetMode", LookAtBlockAction.TargetMode.SPECIFIC.name()));
         } catch (Exception var6) {
            this.targetMode = LookAtBlockAction.TargetMode.SPECIFIC;
         }
      } else {
         this.targetMode = LookAtBlockAction.TargetMode.SPECIFIC;
      }

      this.blockIds.clear();
      if (tag.contains("blockIds")) {
         for (Tag el : tag.getList("blockIds").orElse(new ListTag())) {
            String value = el.asString().orElse("");
            if (!value.isEmpty()) {
               this.blockIds.add(value);
            }
         }
      }

      this.entityIds.clear();
      if (tag.contains("entityIds")) {
         for (Tag elx : tag.getList("entityIds").orElse(new ListTag())) {
            String value = elx.asString().orElse("");
            if (!value.isEmpty()) {
               this.entityIds.add(value);
            }
         }
      }

      this.searchRadius = Math.max(1.0, tag.getDoubleOr("searchRadius", 16.0));
      this.smooth = tag.getBooleanOr("smooth", false);
      this.smoothness = RotateAction.clampSmoothness(tag.getIntOr("smoothness", 6));
      this.waitForCompletion = tag.getBooleanOr("waitForCompletion", true);
      if (tag.contains("enabled")) {
         this.enabled = tag.getBooleanOr("enabled", true);
      }
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.LOOK_AT_BLOCK;
   }

   @Override
   public String getDisplayName() {
      String targetLabel = switch (this.targetMode) {
         case SPECIFIC -> "(" + this.blockX + ", " + this.blockY + ", " + this.blockZ + ")";
         case BLOCK -> this.formatBlockTargets();
         case ENTITY -> this.formatEntityTargets();
      };
      String suffix = this.smooth ? " (Smooth)" : "";
      if (!this.waitForCompletion) {
         suffix = suffix + " [NoWait]";
      }

      return "Look At " + targetLabel + suffix;
   }

   @Override
   public String getIcon() {
      return "LAB";
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean e) {
      this.enabled = e;
   }

   private LookAtBlockAction.RotationTarget resolveNearestBlockTarget(Minecraft mc) {
      if (mc.player != null && mc.level != null && !this.blockIds.isEmpty()) {
         Vec3 eyePos = mc.player.getEyePosition();
         BlockPos center = mc.player.blockPosition();
         int radius = Math.max(1, Mth.ceil(this.searchRadius));
         double bestSq = this.searchRadius * this.searchRadius;
         BlockPos bestPos = null;
         Set<String> wanted = new HashSet<>(this.blockIds);
         MutableBlockPos mutable = new MutableBlockPos();

         for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
               for (int dz = -radius; dz <= radius; dz++) {
                  mutable.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                  Vec3 targetPos = Vec3.atCenterOf(mutable);
                  double distSq = eyePos.distanceToSqr(targetPos);
                  if (!(distSq > bestSq)) {
                     Block block = mc.level.getBlockState(mutable).getBlock();
                     String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                     if (wanted.contains(id)) {
                        bestSq = distSq;
                        bestPos = mutable.immutable();
                     }
                  }
               }
            }
         }

         return bestPos == null ? null : rotationTo(eyePos, Vec3.atCenterOf(bestPos));
      } else {
         return null;
      }
   }

   private LookAtBlockAction.RotationTarget resolveNearestEntityTarget(Minecraft mc) {
      if (mc.player != null && mc.level != null && !this.entityIds.isEmpty()) {
         Set<String> typeIds = new HashSet<>();
         Set<String> specificUuids = new HashSet<>();

         for (String entry : this.entityIds) {
            if (entry != null && !entry.isBlank()) {
               if (entry.startsWith("~")) {
                  String[] parts = entry.split("~", 4);
                  if (parts.length >= 2 && !parts[1].isBlank()) {
                     specificUuids.add(parts[1]);
                  }
               } else {
                  typeIds.add(entry);
               }
            }
         }

         double maxSq = this.searchRadius * this.searchRadius;
         double bestSq = maxSq;
         Entity bestEntity = null;

         for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity != null && entity != mc.player && entity.isAlive()) {
               double distSq = mc.player.distanceToSqr(entity);
               if (!(distSq > bestSq) && this.matchesEntity(entity, typeIds, specificUuids)) {
                  bestSq = distSq;
                  bestEntity = entity;
               }
            }
         }

         if (bestEntity == null) {
            return null;
         } else {
            Vec3 targetPos = bestEntity.getBoundingBox().getCenter();
            return rotationTo(mc.player.getEyePosition(), targetPos);
         }
      } else {
         return null;
      }
   }

   private boolean matchesEntity(Entity entity, Set<String> typeIds, Set<String> specificUuids) {
      if (specificUuids.contains(entity.getStringUUID())) {
         return true;
      } else {
         String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
         return typeIds.contains(typeId);
      }
   }

   private String formatBlockTargets() {
      if (this.blockIds.isEmpty()) {
         return "Block (none)";
      } else {
         String first = RiptideRegistryLabels.block(this.blockIds.get(0));
         return this.blockIds.size() == 1 ? "Nearest " + first : "Nearest " + first + " (+" + (this.blockIds.size() - 1) + ")";
      }
   }

   private String formatEntityTargets() {
      if (this.entityIds.isEmpty()) {
         return "Entity (none)";
      } else {
         String firstEntry = this.entityIds.get(0);
         String first;
         if (firstEntry.startsWith("~")) {
            String[] parts = firstEntry.split("~", 4);
            String rawName = parts.length >= 4 ? parts[3] : "";
            String type = parts.length >= 3 ? RiptideRegistryLabels.entity(parts[2]) : "?";
            first = rawName != null && !rawName.isBlank() ? rawName + " (" + type + ")" : type;
         } else {
            first = RiptideRegistryLabels.entity(firstEntry);
         }

         return this.entityIds.size() == 1 ? "Nearest " + first : "Nearest " + first + " (+" + (this.entityIds.size() - 1) + ")";
      }
   }

   private static LookAtBlockAction.RotationTarget rotationTo(Vec3 from, Vec3 to) {
      Vec3 diff = to.subtract(from);
      double dist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
      float yaw = (float)Math.toDegrees(Math.atan2(-diff.x, diff.z));
      float pitch = (float)(-Math.toDegrees(Math.atan2(diff.y, dist)));
      return new LookAtBlockAction.RotationTarget(yaw, pitch);
   }

   public record RotationTarget(float yaw, float pitch) {
   }

   public static enum TargetMode {
      SPECIFIC,
      BLOCK,
      ENTITY;
   }
}
