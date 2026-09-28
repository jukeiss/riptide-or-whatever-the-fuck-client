package riptide.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import riptide.modules.ModuleRegistry;
import riptide.modules.ScaffoldModule;

public final class RiptideSilentAim {
   private RiptideSilentAim() {
   }

   public static boolean scaffoldOwnsRotation() {
      return ScaffoldModule.reservesTellyInput() || ScaffoldModule.hasActiveSilentMovementRotation();
   }

   public static RiptideRotationUtil.Rotation packetRotation() {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.player != null) {
         RiptideRotationUtil.Rotation rotation = RiptideKillAuraRotation.getCurrentRotation();
         if (rotation == null) {
            return null;
         } else {
            return ModuleRegistry.get(RiptideKillAuraRotation.currentOwner()) instanceof RiptideSilentAim.Owner owner && owner.silentCorrectionApplies()
               ? rotation
               : null;
         }
      } else {
         return null;
      }
   }

   public static Input modifyMovementInput(ClientInput source, Input input) {
      Minecraft mc = Minecraft.getInstance();
      if (input != null && mc != null && mc.player != null && mc.player.input == source) {
         RiptideRotationUtil.Rotation rotation = packetRotation();
         return rotation == null ? input : ScaffoldModule.transformSilentMovementInput(input, mc.player.getYRot(), rotation.yaw());
      } else {
         return input;
      }
   }

   public static float correctedMovementYaw(Entity entity, float vanillaYaw) {
      RiptideRotationUtil.Rotation rotation = activeMovementRotation(entity);
      return rotation == null ? vanillaYaw : rotation.yaw();
   }

   public static float outgoingMovementYaw(LocalPlayer player, float vanillaYaw) {
      return correctedMovementYaw(player, vanillaYaw);
   }

   public static float outgoingMovementPitch(LocalPlayer player, float vanillaPitch) {
      RiptideRotationUtil.Rotation rotation = activeMovementRotation(player);
      return rotation == null ? vanillaPitch : rotation.pitch();
   }

   public static Vec3 silentViewVector(LocalPlayer player, Vec3 vanillaVector) {
      RiptideRotationUtil.Rotation rotation = activeMovementRotation(player);
      return rotation == null ? vanillaVector : Vec3.directionFromRotation(rotation.pitch(), rotation.yaw());
   }

   public static Vec3 correctedJumpImpulse(LivingEntity entity, Vec3 vanillaImpulse) {
      RiptideRotationUtil.Rotation rotation = activeMovementRotation(entity);
      if (rotation == null) {
         return vanillaImpulse;
      } else {
         float yaw = rotation.yaw() * (float) (Math.PI / 180.0);
         return new Vec3(-Mth.sin(yaw) * 0.2F, vanillaImpulse.y, Mth.cos(yaw) * 0.2F);
      }
   }

   public static float correctedFallFlyingPitch(LivingEntity entity, float vanillaPitch) {
      RiptideRotationUtil.Rotation rotation = activeMovementRotation(entity);
      return rotation == null ? vanillaPitch : rotation.pitch();
   }

   public static Vec3 correctedFallFlyingLook(LivingEntity entity, Vec3 vanillaLook) {
      RiptideRotationUtil.Rotation rotation = activeMovementRotation(entity);
      return rotation == null ? vanillaLook : Vec3.directionFromRotation(rotation.pitch(), rotation.yaw());
   }

   private static RiptideRotationUtil.Rotation activeMovementRotation(Entity entity) {
      Minecraft mc = Minecraft.getInstance();
      return entity != null && mc != null && entity == mc.player ? packetRotation() : null;
   }

   public static RiptideRotationUtil.Rotation activeOutgoingRotation(LocalPlayer player) {
      Minecraft mc = Minecraft.getInstance();
      if (player != null && mc != null && player == mc.player) {
         RiptideRotationUtil.Rotation scaffold = ScaffoldModule.activeOutgoingRotation();
         return scaffold != null ? scaffold : packetRotation();
      } else {
         return null;
      }
   }

   public static RiptideRotationUtil.Rotation activeUseItemRotation(LocalPlayer player) {
      return activeOutgoingRotation(player);
   }

   public interface Owner {
      boolean silentCorrectionApplies();
   }
}
