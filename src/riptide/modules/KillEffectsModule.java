package riptide.modules;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.IntSetting;

/**
 * Bursts particles where a nearby player or mob dies. Client-side decoration only:
 * the particles are spawned in your own level, so nobody else sees them.
 */
public final class KillEffectsModule extends Module {
   private final Set<Integer> played = new HashSet<>();

   public KillEffectsModule() {
      super("kill-effects", "Kill Effects", ModuleCategory.RENDER, "Bursts particles where someone dies near you. Only you see them.");
      this.add(
         new ChoiceSetting("type", "Particle", "Soul", "Soul", "Flame", "Soul Flame", "Heart", "Spark", "End Rod", "Totem", "Cloud", "Explosion", "Happy")
            .description("Which particle to burst on a death.")
            .build()
      );
      this.add(new IntSetting("amount", "Amount", 40, 4, 200, 4).description("Particles per death.").build());
      this.add(new IntSetting("spread", "Speed", 30, 0, 200, 5).description("How fast they fly outwards, in hundredths of a block per tick.").build());
      this.add(new IntSetting("range", "Range", 48, 8, 128, 8).unit("blocks").description("Ignore deaths further away than this.").build());
      this.add(new BoolSetting("players", "Players", true).description("Burst when a player dies.").group("Who").build());
      this.add(new BoolSetting("mobs", "Mobs", false).description("Burst when a mob dies.").group("Who").build());
      this.add(new BoolSetting("self", "Yourself", false).description("Burst when you die.").group("Who").build());
   }

   @Override
   public void onEnable() {
      this.played.clear();
   }

   @Override
   public void onDisable() {
      this.played.clear();
   }

   @Override
   public void onGameJoin() {
      this.played.clear();
   }

   @Override
   public void onGameLeft() {
      this.played.clear();
   }

   @Override
   public String info() {
      return this.choice("type");
   }

   @Override
   public void tick() {
      if (MC.level == null || MC.player == null || PackHideState.isActive()) {
         this.played.clear();
      } else {
         boolean players = this.bool("players");
         boolean mobs = this.bool("mobs");
         boolean self = this.bool("self");
         double range = this.integer("range");
         double rangeSq = range * range;
         Set<Integer> alive = new HashSet<>();

         for (Entity entity : MC.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living) {
               boolean isPlayer = living instanceof Player;
               boolean isSelf = living == MC.player;
               if ((!isSelf || self) && (isPlayer ? players : mobs)) {
                  if (!(living.distanceToSqr(MC.player) > rangeSq)) {
                     int id = living.getId();
                     if (living.isAlive() && living.getHealth() > 0.0F) {
                        alive.add(id);
                     } else if (this.played.add(id)) {
                        this.burst(living);
                     }
                  }
               }
            }
         }

         // Drop ids that are alive again (respawned or re-sent), so a later death fires once more.
         this.played.removeAll(alive);
      }
   }

   private void burst(LivingEntity target) {
      ParticleOptions particle = this.particle();
      int amount = this.integer("amount");
      double speed = this.integer("spread") / 100.0;
      ThreadLocalRandom random = ThreadLocalRandom.current();
      AABB box = target.getBoundingBox();
      double width = Math.max(0.5, box.getXsize());
      double height = Math.max(0.5, box.getYsize());

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
         case "Flame" -> ParticleTypes.FLAME;
         case "Soul Flame" -> ParticleTypes.SOUL_FIRE_FLAME;
         case "Heart" -> ParticleTypes.HEART;
         case "Spark" -> ParticleTypes.ELECTRIC_SPARK;
         case "End Rod" -> ParticleTypes.END_ROD;
         case "Totem" -> ParticleTypes.TOTEM_OF_UNDYING;
         case "Cloud" -> ParticleTypes.CLOUD;
         case "Explosion" -> ParticleTypes.EXPLOSION;
         case "Happy" -> ParticleTypes.HAPPY_VILLAGER;
         default -> ParticleTypes.SOUL;
      };
   }
}
