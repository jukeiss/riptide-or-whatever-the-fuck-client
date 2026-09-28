package riptide.modules;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.DoubleSetting;
import riptide.api.module.IntSetting;

public final class TrajectoriesModule extends Module {
   public TrajectoriesModule() {
      super("trajectories", "Trajectories", ModuleCategory.RENDER, "Shows where throws land.");
      this.add(new IntSetting("traj-ticks", "Max Ticks", 120, 20, 1000, 10).description("Simulation length.").build());
      this.add(new BoolSetting("traj-multishot", "Multishot", true).description("Show crossbow spread.").build());
      this.add(new BoolSetting("traj-always-bow", "Always Show Bow", false).description("Show before drawing.").build());
      this.add(new BoolSetting("traj-hit-marker", "Hit Marker", true).description("Box at impact.").build());
      this.add(new DoubleSetting("traj-line-width", "Line Width", 1.0, 1.0, 8.0, 0.5).description("Trajectory line thickness.").build());
      this.add(new ColorSetting("traj-color", "Color", -50373).description("Trajectory line color.").build());
      this.add(new ColorSetting("traj-hit-color", "Hit Color", -50373).description("Block impact color.").build());
      this.add(new ColorSetting("traj-entity-color", "Entity Color", -13248397).description("Entity hit color.").build());
   }

   @Override
   public void onDisable() {
      ModuleWorldRenderer.setTrajectoryPaths(List.of());
   }

   public static void collect(float partialTick) {
      if (ModuleRegistry.get("trajectories") instanceof TrajectoriesModule trajectories && trajectories.isEnabled()) {
         ModuleWorldRenderer.setTrajectoryPaths(trajectories.buildPaths(partialTick));
      } else {
         ModuleWorldRenderer.setTrajectoryPaths(List.of());
      }
   }

   private List<TrajectoriesModule.Path> buildPaths(float partialTick) {
      if (MC != null && MC.player != null && MC.level != null) {
         Player player = MC.player;
         int maxTicks = this.integer("traj-ticks");
         boolean multiShot = this.bool("traj-multishot");
         boolean alwaysBow = this.bool("traj-always-bow");
         boolean hitMarker = this.bool("traj-hit-marker");
         int color = ModuleRenderUtil.color(this, "traj-color", -50373);
         int blockColor = ModuleRenderUtil.color(this, "traj-hit-color", -50373);
         int entityColor = ModuleRenderUtil.color(this, "traj-entity-color", -13248397);
         float lineWidth = (float)this.decimal("traj-line-width");
         float yaw = player.getYRot();
         float pitch = player.getXRot();
         double yawRadians = Math.toRadians(yaw);
         Vec3 offset = interpolated(player, partialTick).subtract(player.position()).add(-Math.cos(yawRadians) * 0.16, 0.0, -Math.sin(yawRadians) * 0.16);
         Vec3 inherited = TrajectorySim.inheritedVelocity(player);
         List<TrajectoriesModule.Path> paths = new ArrayList<>(2);

         for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);

            for (TrajectorySim.Shot shot : TrajectorySim.shotsFor(player, stack, alwaysBow, multiShot)) {
               TrajectorySim.Result result = TrajectorySim.simulate(MC.level, player, shot.info(), yaw + shot.yawOffsetDegrees(), pitch, maxTicks, inherited);
               if (result.points().size() >= 2) {
                  List<Vec3> points = new ArrayList<>(result.points().size());

                  for (Vec3 point : result.points()) {
                     points.add(point.add(offset));
                  }

                  List<TrajectoriesModule.Marker> markers = new ArrayList<>(1);
                  if (hitMarker && result.hit() != null) {
                     if (result.hit() instanceof EntityHitResult entityHit) {
                        markers.add(new TrajectoriesModule.Marker(interpolatedBox(entityHit.getEntity(), partialTick), entityColor));
                     } else {
                        markers.add(new TrajectoriesModule.Marker(landingBox(result.hit().getLocation().add(offset), shot.info().hitboxRadius()), blockColor));
                     }

                     if (shot.info().isSplashPotion()) {
                        splashTargets(result.hit().getLocation(), partialTick, entityColor, markers);
                     }
                  }

                  paths.add(new TrajectoriesModule.Path(points, color, lineWidth, markers));
               }
            }

            if (!paths.isEmpty()) {
               break;
            }
         }

         return paths;
      } else {
         return List.of();
      }
   }

   private static AABB interpolatedBox(Entity entity, float partialTick) {
      Vec3 at = new Vec3(
         Mth.lerp(partialTick, entity.xo, entity.getX()), Mth.lerp(partialTick, entity.yo, entity.getY()), Mth.lerp(partialTick, entity.zo, entity.getZ())
      );
      return entity.getDimensions(entity.getPose()).makeBoundingBox(at);
   }

   private static void splashTargets(Vec3 landing, float partialTick, int color, List<TrajectoriesModule.Marker> out) {
      if (MC != null && MC.level != null && landing != null) {
         AABB cloud = new AABB(landing, landing).inflate(4.0, 2.0, 4.0);

         for (LivingEntity target : MC.level
            .getEntitiesOfClass(LivingEntity.class, cloud, candidate -> candidate.distanceToSqr(landing) <= 16.0 && candidate.isAffectedByPotions())) {
            if (target != MC.player) {
               out.add(new TrajectoriesModule.Marker(interpolatedBox(target, partialTick), color));
            }
         }
      }
   }

   private static Vec3 interpolated(Player player, float partialTick) {
      return player.tickCount == 0
         ? player.position()
         : new Vec3(
            Math.fma((double)partialTick, player.getX() - player.xOld, player.xOld),
            Math.fma((double)partialTick, player.getY() - player.yOld, player.yOld),
            Math.fma((double)partialTick, player.getZ() - player.zOld, player.zOld)
         );
   }

   private static AABB landingBox(Vec3 at, double hitboxRadius) {
      double size = Math.max(0.2, hitboxRadius * 0.8);
      return new AABB(at.x - size, at.y - size, at.z - size, at.x + size, at.y + size, at.z + size);
   }

   public record Marker(AABB box, int color) {
   }

   public record Path(List<Vec3> points, int color, float lineWidth, List<TrajectoriesModule.Marker> markers) {
   }
}
