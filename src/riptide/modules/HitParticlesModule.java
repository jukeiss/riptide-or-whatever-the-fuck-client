package riptide.modules;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.IntSetting;

public final class HitParticlesModule extends Module {
   public HitParticlesModule() {
      super("hit-particles", "Hit Particles", ModuleCategory.RENDER, "Bursts extra particles on whatever you hit. Only you see them.");
      this.add(
         new ChoiceSetting("type", "Particle", "Crit", "Crit", "Magic Crit", "Flame", "Soul Flame", "Heart", "Spark", "End Rod", "Totem", "Damage")
            .description("Which particle to spawn.")
            .build()
      );
      this.add(new IntSetting("amount", "Amount", 12, 1, 64, 1).description("Particles per hit.").build());
      this.add(new IntSetting("spread", "Speed", 40, 0, 200, 5).description("How fast they fly outwards, in hundredths of a block per tick.").build());
      this.add(new BoolSetting("players-only", "Players Only", false).description("Only burst when you hit a player.").build());
   }

   @Override
   public String info() {
      return this.choice("type");
   }

   // Observes the vanilla attack path; never cancels it.
   @Override
   public boolean shouldCancelAttack(HitResult hitResult) {
      try {
         if (MC.level != null && hitResult instanceof EntityHitResult entityHit && !PackHideState.isActive()) {
            Entity target = entityHit.getEntity();
            if (target instanceof LivingEntity living && living.isAlive() && target != MC.player) {
               if (!this.bool("players-only") || target instanceof Player) {
                  this.burst(target);
               }
            }
         }
      } catch (Throwable var5) {
      }

      return false;
   }

   private void burst(Entity target) {
      ParticleOptions particle = this.particle();
      int amount = this.integer("amount");
      double speed = this.integer("spread") / 100.0;
      ThreadLocalRandom random = ThreadLocalRandom.current();
      AABB box = target.getBoundingBox();
      double width = box.getXsize();
      double height = box.getYsize();

      for (int i = 0; i < amount; i++) {
         double x = target.getX() + (random.nextDouble() - 0.5) * width;
         double y = box.minY + random.nextDouble() * height;
         double z = target.getZ() + (random.nextDouble() - 0.5) * width;
         double dx = (random.nextDouble() - 0.5) * 2.0 * speed;
         double dy = random.nextDouble() * speed;
         double dz = (random.nextDouble() - 0.5) * 2.0 * speed;
         MC.level.addParticle(particle, x, y, z, dx, dy, dz);
      }
   }

   private ParticleOptions particle() {
      return switch (this.choice("type")) {
         case "Magic Crit" -> ParticleTypes.ENCHANTED_HIT;
         case "Flame" -> ParticleTypes.FLAME;
         case "Soul Flame" -> ParticleTypes.SOUL_FIRE_FLAME;
         case "Heart" -> ParticleTypes.HEART;
         case "Spark" -> ParticleTypes.ELECTRIC_SPARK;
         case "End Rod" -> ParticleTypes.END_ROD;
         case "Totem" -> ParticleTypes.TOTEM_OF_UNDYING;
         case "Damage" -> ParticleTypes.DAMAGE_INDICATOR;
         default -> ParticleTypes.CRIT;
      };
   }
}
