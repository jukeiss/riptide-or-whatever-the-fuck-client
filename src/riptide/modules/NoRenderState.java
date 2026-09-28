package riptide.modules;

import java.util.List;
import java.util.Locale;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

public final class NoRenderState {
   private static volatile boolean on;
   static volatile boolean portalOverlay;
   static volatile boolean spyglassOverlay;
   static volatile boolean nausea;
   static volatile boolean pumpkinOverlay;
   static volatile boolean powderedSnowOverlay;
   static volatile boolean fireOverlay;
   static volatile boolean liquidOverlay;
   static volatile boolean inWallOverlay;
   static volatile boolean vignette;
   static volatile boolean guiBackground;
   static volatile boolean totemAnimation;
   static volatile boolean eatingParticles;
   static volatile boolean enchantGlint;
   static volatile boolean hurtcam;
   static volatile boolean bossBar;
   static volatile boolean scoreboard;
   static volatile boolean crosshair;
   static volatile boolean title;
   static volatile boolean heldItemName;
   static volatile boolean obfuscation;
   static volatile boolean potionIcons;
   static volatile boolean weather;
   static volatile boolean worldBorder;
   static volatile boolean blindness;
   static volatile boolean darkness;
   static volatile boolean fog;
   static volatile boolean enchantTableBook;
   static volatile boolean signText;
   static volatile boolean blockBreakOverlay;
   static volatile boolean blockBreakParticles;
   static volatile boolean beaconBeams;
   static volatile boolean fallingBlocks;
   static volatile boolean mapMarkers;
   static volatile boolean mapContents;
   static volatile boolean banners;
   static volatile boolean fireworkExplosions;
   static volatile boolean hideAllParticles;
   static volatile boolean textureRotations;
   static volatile boolean sky;
   static volatile boolean stars;
   static volatile boolean sun;
   static volatile boolean moon;
   static volatile boolean sunriseGlow;
   static volatile boolean clouds;
   static volatile boolean timeChanger;
   static volatile long timeTicks;
   static volatile boolean weatherChanger;
   static volatile float rainLevel;
   static volatile float thunderLevel;
   static volatile boolean armor;
   static volatile boolean invisibility;
   static volatile boolean glowing;
   static volatile boolean spawnerEntities;
   static volatile boolean deadEntities;
   static volatile boolean nametags;
   static volatile boolean dropSpawnPackets;
   static volatile List<String> entities = List.of();
   static volatile List<String> blockEntities = List.of();

   private NoRenderState() {
   }

   static void disable() {
      on = false;
   }

   static void enable() {
      on = true;
   }

   public static boolean noPortalOverlay() {
      return on && portalOverlay;
   }

   public static boolean noSpyglassOverlay() {
      return on && spyglassOverlay;
   }

   public static boolean noNausea() {
      return on && nausea;
   }

   public static boolean noPumpkinOverlay() {
      return on && pumpkinOverlay;
   }

   public static boolean noPowderedSnowOverlay() {
      return on && powderedSnowOverlay;
   }

   public static boolean noFireOverlay() {
      return on && fireOverlay;
   }

   public static boolean noLiquidOverlay() {
      return on && liquidOverlay;
   }

   public static boolean noInWallOverlay() {
      return on && inWallOverlay;
   }

   public static boolean noVignette() {
      return on && vignette;
   }

   public static boolean noGuiBackground() {
      return on && guiBackground;
   }

   public static boolean noTotemAnimation() {
      return on && totemAnimation;
   }

   public static boolean noEatingParticles() {
      return on && eatingParticles;
   }

   public static boolean noEnchantGlint() {
      return on && enchantGlint;
   }

   public static boolean noHurtcam() {
      return on && hurtcam;
   }

   public static boolean noBossBar() {
      return on && bossBar;
   }

   public static boolean noScoreboard() {
      return on && scoreboard;
   }

   public static boolean noCrosshair() {
      return on && crosshair;
   }

   public static boolean noTitle() {
      return on && title;
   }

   public static boolean noHeldItemName() {
      return on && heldItemName;
   }

   public static boolean noObfuscation() {
      return on && obfuscation;
   }

   public static boolean noPotionIcons() {
      return on && potionIcons;
   }

   public static boolean noWeather() {
      return on && weather;
   }

   public static boolean noWorldBorder() {
      return on && worldBorder;
   }

   public static boolean noBlindness() {
      return on && blindness;
   }

   public static boolean noDarkness() {
      return on && darkness;
   }

   public static boolean noFog() {
      return on && fog;
   }

   public static boolean noEnchantTableBook() {
      return on && enchantTableBook;
   }

   public static boolean noSignText() {
      return on && signText;
   }

   public static boolean noBlockBreakOverlay() {
      return on && blockBreakOverlay;
   }

   public static boolean noBlockBreakParticles() {
      return on && blockBreakParticles;
   }

   public static boolean noBeaconBeams() {
      return on && beaconBeams;
   }

   public static boolean noFallingBlocks() {
      return on && fallingBlocks;
   }

   public static boolean noMapMarkers() {
      return on && mapMarkers;
   }

   public static boolean noMapContents() {
      return on && mapContents;
   }

   public static boolean noBanners() {
      return on && banners;
   }

   public static boolean noTextureRotations() {
      return on && textureRotations;
   }

   public static boolean noSky() {
      return on && sky;
   }

   public static boolean noStars() {
      return on && stars;
   }

   public static boolean noSun() {
      return on && sun;
   }

   public static boolean noMoon() {
      return on && moon;
   }

   public static boolean noSunriseGlow() {
      return on && sunriseGlow;
   }

   public static boolean noClouds() {
      return on && clouds;
   }

   public static boolean timeChanged() {
      return on && timeChanger;
   }

   public static long timeTicks() {
      return timeTicks;
   }

   public static boolean weatherChanged() {
      return on && weatherChanger;
   }

   public static float rainLevel() {
      return rainLevel;
   }

   public static float thunderLevel() {
      return thunderLevel;
   }

   public static boolean noArmor() {
      return on && armor;
   }

   public static boolean noInvisibility() {
      return on && invisibility;
   }

   public static boolean noGlowing() {
      return on && glowing;
   }

   public static boolean noSpawnerEntities() {
      return on && spawnerEntities;
   }

   public static boolean noDeadEntities() {
      return on && deadEntities;
   }

   public static boolean noNametags() {
      return on && nametags;
   }

   public static boolean noParticle(ParticleType<?> type) {
      if (!on) {
         return false;
      } else if (hideAllParticles) {
         return true;
      } else {
         if (fireworkExplosions && type != null) {
            Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(type);
            if (id != null && id.getPath().equals("firework")) {
               return true;
            }
         }

         return false;
      }
   }

   public static boolean noEntity(Entity entity) {
      return entity != null && noEntityType(entity.getType());
   }

   public static boolean noEntityType(EntityType<?> type) {
      if (on && type != null) {
         List<String> list = entities;
         if (list.isEmpty()) {
            return false;
         } else {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            return id != null && containsId(list, id);
         }
      } else {
         return false;
      }
   }

   public static boolean dropSpawnPacket(EntityType<?> type) {
      return on && dropSpawnPackets && noEntityType(type);
   }

   public static boolean noBlockEntity(Block block) {
      if (on && block != null) {
         List<String> list = blockEntities;
         if (list.isEmpty()) {
            return false;
         } else {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            return id != null && containsId(list, id);
         }
      } else {
         return false;
      }
   }

   private static boolean containsId(List<String> list, Identifier id) {
      String full = id.toString();
      String path = id.getPath();

      for (String entry : list) {
         if (entry != null) {
            String e = entry.trim().toLowerCase(Locale.ROOT);
            if (e.equals(full) || e.equals(path)) {
               return true;
            }
         }
      }

      return false;
   }
}
