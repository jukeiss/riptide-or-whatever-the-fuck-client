package riptide.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

public class WaitEntityTargetAction implements MacroAction {
   public WaitEntityTargetAction.EntityCondition condition = WaitEntityTargetAction.EntityCondition.LOOKING_AT;
   public String entityId = "";
   public double range = 5.0;
   public boolean containerEntitiesOnly = false;
   public int timeoutMs = 0;
   public boolean listenDuringPreviousAction = false;

   @Override
   public void execute(Minecraft mc) {
   }

   @Override
   public MacroActionType getType() {
      return MacroActionType.WAIT_ENTITY_TARGET;
   }

   public boolean matches(Minecraft mc) {
      if (mc != null && mc.player != null && mc.level != null) {
         MacroTemplate.Resolution entityResolution = MacroVariables.resolve(this.entityId, mc);
         if (!entityResolution.success()) {
            return false;
         } else {
            String expectedEntity = entityResolution.value();

            return switch (this.condition) {
               case LOOKING_AT -> this.matchesEntity(mc.crosshairPickEntity, expectedEntity);
               case WITHIN_REACH, NEARBY -> {
                  boolean found = false;

                  for (Entity entity : mc.level.entitiesForRendering()) {
                     if (entity != mc.player && mc.player.distanceTo(entity) <= this.range && this.matchesEntity(entity, expectedEntity)) {
                        found = true;
                        break;
                     }
                  }

                  yield found;
               }
               case MOUNTED_IN -> this.matchesEntity(mc.player.getVehicle(), expectedEntity);
            };
         }
      } else {
         return false;
      }
   }

   private boolean matchesEntity(Entity entity, String expectedEntity) {
      if (entity == null) {
         return false;
      } else {
         String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
         if (this.containerEntitiesOnly && !type.contains("boat") && !type.contains("minecart") && !type.contains("llama") && !type.contains("chest")) {
            return false;
         } else {
            return expectedEntity != null && !expectedEntity.isBlank()
               ? type.equalsIgnoreCase(expectedEntity)
                  || entity.getStringUUID().equalsIgnoreCase(expectedEntity)
                  || entity.getName().getString().equalsIgnoreCase(expectedEntity)
               : true;
         }
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "WAIT_ENTITY_TARGET");
      tag.putString("condition", this.condition.name());
      tag.putString("entityId", this.entityId);
      tag.putDouble("range", this.range);
      tag.putBoolean("containerEntitiesOnly", this.containerEntitiesOnly);
      tag.putInt("timeoutMs", this.timeoutMs);
      MacroWaitOptions.write(tag, this);
      return tag;
   }

   @Override
   public void fromTag(CompoundTag tag) {
      this.condition = MacroStringList.enumValue(
         WaitEntityTargetAction.EntityCondition.class, tag.getStringOr("condition", "LOOKING_AT"), WaitEntityTargetAction.EntityCondition.LOOKING_AT
      );
      this.entityId = tag.getStringOr("entityId", "");
      this.range = tag.getDoubleOr("range", 5.0);
      this.containerEntitiesOnly = tag.getBooleanOr("containerEntitiesOnly", false);
      this.timeoutMs = tag.getIntOr("timeoutMs", 0);
      MacroWaitOptions.read(tag, this);
   }

   @Override
   public String getDisplayName() {
      return "Wait entity " + this.condition;
   }

   @Override
   public String getIcon() {
      return "E";
   }

   public static enum EntityCondition {
      LOOKING_AT,
      WITHIN_REACH,
      MOUNTED_IN,
      NEARBY;
   }
}
