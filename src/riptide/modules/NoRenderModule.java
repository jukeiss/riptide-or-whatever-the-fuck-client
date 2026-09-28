package riptide.modules;

import riptide.api.module.BoolSetting;
import riptide.api.module.EnumSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.RegistryListSetting;

public final class NoRenderModule extends Module {
   private final BoolSetting portalOverlay = this.add(
      new BoolSetting("portal-overlay", "Portal Overlay", false).group("Overlay").description("Hide nether portal overlay")
   );
   private final BoolSetting spyglassOverlay = this.add(
      new BoolSetting("spyglass-overlay", "Spyglass Overlay", false).group("Overlay").description("Hide spyglass overlay")
   );
   private final BoolSetting nausea = this.add(new BoolSetting("nausea", "Nausea", false).group("Overlay").description("Disable nausea/portal warp"));
   private final BoolSetting pumpkinOverlay = this.add(
      new BoolSetting("pumpkin-overlay", "Pumpkin Overlay", false).group("Overlay").description("Hide pumpkin overlay")
   );
   private final BoolSetting powderedSnowOverlay = this.add(
      new BoolSetting("powdered-snow-overlay", "Powdered Snow Overlay", false).group("Overlay").description("Hide powder snow overlay")
   );
   private final BoolSetting fireOverlay = this.add(new BoolSetting("fire-overlay", "Fire Overlay", false).group("Overlay").description("Hide fire overlay"));
   private final BoolSetting liquidOverlay = this.add(
      new BoolSetting("liquid-overlay", "Liquid Overlay", false).group("Overlay").description("Hide underwater overlay")
   );
   private final BoolSetting inWallOverlay = this.add(
      new BoolSetting("in-wall-overlay", "In-Wall Overlay", false).group("Overlay").description("Hide in-block overlay")
   );
   private final BoolSetting vignette = this.add(new BoolSetting("vignette", "Vignette", false).group("Overlay").description("Disable screen vignette"));
   private final BoolSetting guiBackground = this.add(
      new BoolSetting("gui-background", "GUI Background", false).group("Overlay").description("Hide GUI background dim")
   );
   private final BoolSetting totemAnimation = this.add(
      new BoolSetting("totem-animation", "Totem Animation", false).group("Overlay").description("Hide totem animation")
   );
   private final BoolSetting eatingParticles = this.add(
      new BoolSetting("eating-particles", "Eating Particles", false).group("Overlay").description("Hide eating particles")
   );
   private final BoolSetting enchantGlint = this.add(
      new BoolSetting("enchantment-glint", "Enchantment Glint", false).group("Overlay").description("Hide enchantment glint")
   );
   private final BoolSetting hurtcam = this.add(
      new BoolSetting("hurt-cam", "Hurt Cam", false).group("Overlay").description("Disable hurt camera tilt and animation")
   );
   private final BoolSetting bossBar = this.add(new BoolSetting("boss-bar", "Boss Bar", false).group("HUD").description("Hide boss bars"));
   private final BoolSetting scoreboard = this.add(new BoolSetting("scoreboard", "Scoreboard", false).group("HUD").description("Hide scoreboard"));
   private final BoolSetting crosshair = this.add(new BoolSetting("crosshair", "Crosshair", false).group("HUD").description("Hide crosshair"));
   private final BoolSetting title = this.add(new BoolSetting("title", "Title", false).group("HUD").description("Hide on-screen titles"));
   private final BoolSetting heldItemName = this.add(new BoolSetting("held-item-name", "Held Item Name", false).group("HUD").description("Hide held item name"));
   private final BoolSetting obfuscation = this.add(new BoolSetting("obfuscation", "Obfuscation", false).group("HUD").description("Show obfuscated text"));
   private final BoolSetting potionIcons = this.add(new BoolSetting("potion-icons", "Potion Icons", false).group("HUD").description("Hide effect icons"));
   private final BoolSetting weather = this.add(new BoolSetting("weather", "Weather", false).group("World").description("Hide rain and snow"));
   private final BoolSetting worldBorder = this.add(new BoolSetting("world-border", "World Border", false).group("World").description("Hide world border"));
   private final BoolSetting blindness = this.add(new BoolSetting("blindness", "Blindness", false).group("World").description("Disable blindness fog"));
   private final BoolSetting darkness = this.add(new BoolSetting("darkness", "Darkness", false).group("World").description("Disable darkness fog"));
   private final BoolSetting fog = this.add(new BoolSetting("fog", "Fog", false).group("World").description("Disable fog"));
   private final BoolSetting enchantTableBook = this.add(
      new BoolSetting("enchantment-table-book", "Enchant Table Book", false).group("World").description("Hide enchant table book")
   );
   private final BoolSetting signText = this.add(new BoolSetting("sign-text", "Sign Text", false).group("World").description("Hide sign text"));
   private final BoolSetting blockBreakOverlay = this.add(
      new BoolSetting("block-break-overlay", "Block Break Overlay", false).group("World").description("Hide block crack overlay")
   );
   private final BoolSetting blockBreakParticles = this.add(
      new BoolSetting("block-break-particles", "Block Break Particles", false).group("World").description("Hide block break particles")
   );
   private final BoolSetting beaconBeams = this.add(new BoolSetting("beacon-beams", "Beacon Beams", false).group("World").description("Hide beacon beams"));
   private final BoolSetting fallingBlocks = this.add(
      new BoolSetting("falling-blocks", "Falling Blocks", false).group("World").description("Hide falling blocks")
   );
   private final BoolSetting mapMarkers = this.add(new BoolSetting("map-markers", "Map Markers", false).group("World").description("Hide map markers"));
   private final BoolSetting mapContents = this.add(new BoolSetting("map-contents", "Map Contents", false).group("World").description("Hide map contents"));
   private final BoolSetting banners = this.add(new BoolSetting("banners", "Banners", false).group("World").description("Hide banners"));
   private final BoolSetting fireworkExplosions = this.add(
      new BoolSetting("firework-explosions", "Firework Explosions", false).group("World").description("Hide firework explosions")
   );
   private final BoolSetting hideAllParticles = this.add(
      new BoolSetting("hide-all-particles", "Hide All Particles", false).group("World").description("Hide all particles")
   );
   private final BoolSetting textureRotations = this.add(
      new BoolSetting("texture-rotations", "Texture Rotations", false).group("World").description("Constant texture rotations")
   );
   private final RegistryListSetting blockEntities = this.add(
      RegistryListSetting.blocks("block-entities", "Block Entities").group("World").description("Hide chosen block entities")
   );
   private final BoolSetting sky = this.add(new BoolSetting("sky", "Sky", false).group("Sky").description("Hide the sky"));
   private final BoolSetting stars = this.add(new BoolSetting("stars", "Stars", false).group("Sky").description("Hide the stars"));
   private final BoolSetting sun = this.add(new BoolSetting("sun", "Sun", false).group("Sky").description("Hide the sun"));
   private final BoolSetting moon = this.add(new BoolSetting("moon", "Moon", false).group("Sky").description("Hide the moon"));
   private final BoolSetting sunriseGlow = this.add(new BoolSetting("sunrise-glow", "Sunset Glow", false).group("Sky").description("Hide sunrise/sunset glow"));
   private final BoolSetting clouds = this.add(new BoolSetting("clouds", "Clouds", false).group("Sky").description("Hide the clouds"));
   private final EnumSetting<NoRenderModule.TimeMode> timeMode = this.add(
      new EnumSetting<>("time", "Time", NoRenderModule.TimeMode.VANILLA, NoRenderModule.TimeMode.values())
         .group("Time & Weather")
         .description("Force a client time")
   );
   private final IntSetting customTime = this.add(
      new IntSetting("custom-time", "Custom Time", 6000, 0, 24000, 100)
         .group("Time & Weather")
         .description("Custom time in ticks")
         .visibleWhen(() -> this.timeMode.get() == NoRenderModule.TimeMode.CUSTOM)
   );
   private final EnumSetting<NoRenderModule.WeatherMode> weatherChanger = this.add(
      new EnumSetting<>("weather-changer", "Weather Changer", NoRenderModule.WeatherMode.VANILLA, NoRenderModule.WeatherMode.values())
         .group("Time & Weather")
         .description("Force client weather")
   );
   private final RegistryListSetting entities = this.add(
      RegistryListSetting.entityTypes("entities", "Entities").group("Entity").description("Hide chosen entities")
   );
   private final BoolSetting dropSpawnPackets = this.add(
      new BoolSetting("drop-spawn-packets", "Drop Spawn Packets", false).group("Entity").description("Drop listed spawn packets")
   );
   private final BoolSetting armor = this.add(new BoolSetting("armor", "Armor", false).group("Entity").description("Hide entity armor"));
   private final BoolSetting invisibility = this.add(
      new BoolSetting("invisibility", "Invisibility", false).group("Entity").description("Show invisible entities")
   );
   private final BoolSetting glowing = this.add(new BoolSetting("glowing", "Glowing", false).group("Entity").description("Disable glowing outline"));
   private final BoolSetting spawnerEntities = this.add(
      new BoolSetting("spawner-entities", "Spawner Entities", false).group("Entity").description("Hide spawner mobs")
   );
   private final BoolSetting deadEntities = this.add(new BoolSetting("dead-entities", "Dead Entities", false).group("Entity").description("Hide dead entities"));
   private final BoolSetting nametags = this.add(new BoolSetting("nametags", "Nametags", false).group("Entity").description("Hide entity nametags"));
   private boolean lastTextureRotations;

   public NoRenderModule() {
      super("no-render", "NoRender", ModuleCategory.RENDER, "Disables rendering of selected overlays, HUD elements, world features and entities.");
   }

   @Override
   public void onEnable() {
      this.lastTextureRotations = this.textureRotations.get();
      this.push();
      rebuildChunks();
   }

   @Override
   public void onDisable() {
      NoRenderState.disable();
      rebuildChunks();
   }

   @Override
   public void tick() {
      this.push();
      boolean tr = this.textureRotations.get();
      if (tr != this.lastTextureRotations) {
         this.lastTextureRotations = tr;
         rebuildChunks();
      }
   }

   @Override
   public boolean ticksWhenDisabled() {
      return false;
   }

   @Override
   protected void onOptionValueChanged(String settingId) {
      if (this.isEnabled()) {
         this.push();
      }
   }

   private void push() {
      NoRenderState.portalOverlay = this.portalOverlay.get();
      NoRenderState.spyglassOverlay = this.spyglassOverlay.get();
      NoRenderState.nausea = this.nausea.get();
      NoRenderState.pumpkinOverlay = this.pumpkinOverlay.get();
      NoRenderState.powderedSnowOverlay = this.powderedSnowOverlay.get();
      NoRenderState.fireOverlay = this.fireOverlay.get();
      NoRenderState.liquidOverlay = this.liquidOverlay.get();
      NoRenderState.inWallOverlay = this.inWallOverlay.get();
      NoRenderState.vignette = this.vignette.get();
      NoRenderState.guiBackground = this.guiBackground.get();
      NoRenderState.totemAnimation = this.totemAnimation.get();
      NoRenderState.eatingParticles = this.eatingParticles.get();
      NoRenderState.enchantGlint = this.enchantGlint.get();
      NoRenderState.hurtcam = this.hurtcam.get();
      NoRenderState.bossBar = this.bossBar.get();
      NoRenderState.scoreboard = this.scoreboard.get();
      NoRenderState.crosshair = this.crosshair.get();
      NoRenderState.title = this.title.get();
      NoRenderState.heldItemName = this.heldItemName.get();
      NoRenderState.obfuscation = this.obfuscation.get();
      NoRenderState.potionIcons = this.potionIcons.get();
      NoRenderState.weather = this.weather.get();
      NoRenderState.worldBorder = this.worldBorder.get();
      NoRenderState.blindness = this.blindness.get();
      NoRenderState.darkness = this.darkness.get();
      NoRenderState.fog = this.fog.get();
      NoRenderState.enchantTableBook = this.enchantTableBook.get();
      NoRenderState.signText = this.signText.get();
      NoRenderState.blockBreakOverlay = this.blockBreakOverlay.get();
      NoRenderState.blockBreakParticles = this.blockBreakParticles.get();
      NoRenderState.beaconBeams = this.beaconBeams.get();
      NoRenderState.fallingBlocks = this.fallingBlocks.get();
      NoRenderState.mapMarkers = this.mapMarkers.get();
      NoRenderState.mapContents = this.mapContents.get();
      NoRenderState.banners = this.banners.get();
      NoRenderState.fireworkExplosions = this.fireworkExplosions.get();
      NoRenderState.hideAllParticles = this.hideAllParticles.get();
      NoRenderState.textureRotations = this.textureRotations.get();
      NoRenderState.blockEntities = this.blockEntities.get();
      NoRenderState.sky = this.sky.get();
      NoRenderState.stars = this.stars.get();
      NoRenderState.sun = this.sun.get();
      NoRenderState.moon = this.moon.get();
      NoRenderState.sunriseGlow = this.sunriseGlow.get();
      NoRenderState.clouds = this.clouds.get();
      NoRenderModule.TimeMode tm = this.timeMode.get();
      NoRenderState.timeChanger = tm != NoRenderModule.TimeMode.VANILLA;
      NoRenderState.timeTicks = this.timeTicksFor(tm);
      NoRenderModule.WeatherMode wm = this.weatherChanger.get();
      NoRenderState.weatherChanger = wm != NoRenderModule.WeatherMode.VANILLA;

      NoRenderState.rainLevel = switch (wm) {
         case RAIN, THUNDER -> 1.0F;
         case SNOW -> 0.9F;
         default -> 0.0F;
      };
      NoRenderState.thunderLevel = wm == NoRenderModule.WeatherMode.THUNDER ? 1.0F : 0.0F;
      NoRenderState.entities = this.entities.get();
      NoRenderState.dropSpawnPackets = this.dropSpawnPackets.get();
      NoRenderState.armor = this.armor.get();
      NoRenderState.invisibility = this.invisibility.get();
      NoRenderState.glowing = this.glowing.get();
      NoRenderState.spawnerEntities = this.spawnerEntities.get();
      NoRenderState.deadEntities = this.deadEntities.get();
      NoRenderState.nametags = this.nametags.get();
      NoRenderState.enable();
   }

   private long timeTicksFor(NoRenderModule.TimeMode mode) {
      return switch (mode) {
         case VANILLA -> 0L;
         case DAY -> 1000L;
         case NOON -> 6000L;
         case SUNSET -> 12610L;
         case NIGHT -> 13000L;
         case MIDNIGHT -> 18000L;
         case SUNRISE -> 23041L;
         case CUSTOM -> this.customTime.get().intValue();
      };
   }

   private static void rebuildChunks() {
      try {
         ModuleRenderUtil.refreshWorldRenderer();
      } catch (Throwable var1) {
      }
   }

   public static enum TimeMode {
      VANILLA,
      DAY,
      NOON,
      SUNSET,
      NIGHT,
      MIDNIGHT,
      SUNRISE,
      CUSTOM;
   }

   public static enum WeatherMode {
      VANILLA,
      CLEAR,
      RAIN,
      SNOW,
      THUNDER;
   }
}
