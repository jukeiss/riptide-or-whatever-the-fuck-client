package riptide.util;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import riptide.api.hud.HudElementProvider;
import riptide.api.hud.HudElements;
import riptide.gui.screen.RiptideHudEditorScreen;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.mixin.accessor.RiptideMultiPlayerGameModeAccessor;
import riptide.modules.AntiVanishModule;
import riptide.modules.Module;
import riptide.modules.ModuleRegistry;
import riptide.modules.PackFreecamState;
import riptide.modules.PackHideState;
import riptide.util.macro.ServerTickTracker;

public final class RiptideHudManager {
   public static final String ACTIVE_MODULES = "active_modules";
   public static final String TPS = "tps";
   public static final String COORDINATES = "coordinates";
   public static final String NETHER_COORDS = "nether_coords";
   public static final String FPS = "fps";
   public static final String PING = "ping";
   public static final String SPEED = "speed";
   public static final String GAME_MODE = "game_mode";
   public static final String DURABILITY = "durability";
   public static final String LOOKING_AT = "looking_at";
   public static final String BREAKING_PROGRESS = "breaking_progress";
   public static final String SERVER = "server";
   public static final String WEATHER = "weather";
   public static final String BIOME = "biome";
   public static final String WORLD_TIME = "world_time";
   public static final String REAL_TIME = "real_time";
   public static final String ROTATION = "rotation";
   public static final String WATERMARK = "watermark";
   public static final String ARMOR = "armor";
   public static final String INVENTORY = "inventory";
   public static final String ITEM_COUNTER = "item_counter";
   public static final String POTION_TIMERS = "potion_timers";
   public static final String COMPASS = "compass";
   public static final String ANTI_VANISH = "anti_vanish";
   public static final String CPS = "cps";
   public static final String KEYSTROKES = "keystrokes";
   public static final String MEMORY = "memory";
   public static final String SERVER_IP = "server_ip";
   public static final String SERVER_BRAND = "server_brand";
   public static final String FPS_GRAPH = "fps_graph";
   public static final String SPOTIFY = "spotify";
   private static final String KEY_PADDING = "padding";
   private static final String KEY_VERTICAL_PADDING = "vertical-padding";
   private static final String KEY_OUTLINE = "outline";
   private static final String KEY_OUTLINE_COLOR = "outline-color";
   private static final String KEY_BACKGROUND = "background";
   private static final String KEY_COMPASS_WIDTH = "compass-width";
   private static final String KEY_SPOTIFY_SCROLL_SPEED = "spotify-scroll-speed";
   private static final String KEY_SPOTIFY_MENU_STRIP = "spotify-menu-strip";
   private static final String KEY_SPOTIFY_SOURCE = "spotify-source";
   private static final String KEY_SPOTIFY_WIDTH = "spotify-width";
   private static final String KEY_SPOTIFY_COLOR_MODE = "spotify-color-mode";
   private static final String KEY_SPOTIFY_ARTIST_COLOR = "spotify-artist-color";
   private static final String KEY_SPOTIFY_TITLE_COLOR = "spotify-title-color";
   private static final String KEY_SPOTIFY_TIME_COLOR = "spotify-time-color";
   private static final String KEY_SPOTIFY_PROGRESS_COLOR = "spotify-progress-color";
   private static final String KEY_SPOTIFY_PART_ART = "spotify-part-art";
   private static final String KEY_SPOTIFY_PART_ARTIST = "spotify-part-artist";
   private static final String KEY_SPOTIFY_PART_TIME = "spotify-part-time";
   private static final String KEY_SPOTIFY_TIME_POSITION = "spotify-time-position";
   private static final String KEY_SPOTIFY_PART_PROGRESS = "spotify-part-progress";
   private static final String KEY_SPOTIFY_RAINBOW_SPEED = "rainbow-speed";
   private static final String KEY_SPOTIFY_RAINBOW_SPREAD = "rainbow-spread";
   private static final String KEY_SPOTIFY_RAINBOW_SATURATION = "rainbow-saturation";
   private static final String KEY_SPOTIFY_RAINBOW_BRIGHTNESS = "rainbow-brightness";
   private static final String KEY_SPOTIFY_RAINBOW_DIRECTION = "spotify-rainbow-direction";
   private static final String KEY_SPOTIFY_RAINBOW_ARTIST = "spotify-rainbow-artist";
   private static final String KEY_SPOTIFY_RAINBOW_TITLE = "spotify-rainbow-title";
   private static final String KEY_SPOTIFY_RAINBOW_TIME = "spotify-rainbow-time";
   private static final String KEY_SPOTIFY_RAINBOW_PROGRESS = "spotify-rainbow-progress";
   private static final String KEY_STAIR_SNAP = "stair-snap";
   private static final String KEY_LOGO_WIDTH = "logo-width";
   private static final String KEY_LOGO_RIGHT_PADDING = "logo-right-padding";
   private static final String KEY_KS_ACTIVE_COLOR = "keystroke-active-color";
   private static final String KEY_KS_IDLE_COLOR = "keystroke-idle-color";
   private static final String KEY_KS_TEXT_COLOR = "keystroke-text-color";
   private static final String KEY_KS_SHOW_SPACE = "keystroke-show-space";
   private static final String KEY_KS_SHOW_MOUSE = "keystroke-show-mouse";
   private static final String KEY_KS_SIZE = "keystroke-size";
   private static final String DEFAULT_PADDING = "1";
   private static final String DEFAULT_VERTICAL_PADDING = "0";
   private static final String DEFAULT_OUTLINE = "false";
   private static final String DEFAULT_OUTLINE_COLOR = "FF750000";
   private static final String DEFAULT_BACKGROUND = "true";
   private static final String DEFAULT_COMPASS_WIDTH = "112";
   private static final String DEFAULT_SPOTIFY_MENU_STRIP = "true";
   private static final String DEFAULT_SPOTIFY_SCROLL_SPEED = "25";
   private static final String DEFAULT_SPOTIFY_SOURCE = "Spotify";
   private static final String DEFAULT_SPOTIFY_WIDTH = "175";
   private static final String DEFAULT_SPOTIFY_COLOR_MODE = "Theme";
   private static final String DEFAULT_SPOTIFY_ARTIST_COLOR = "FFB79E9E";
   private static final String DEFAULT_SPOTIFY_TITLE_COLOR = "FFF3ECE7";
   private static final String DEFAULT_SPOTIFY_TIME_COLOR = "FFB79E9E";
   private static final String DEFAULT_SPOTIFY_PROGRESS_COLOR = "FFFF3B3B";
   private static final String DEFAULT_SPOTIFY_RAINBOW_SPEED = "1.0";
   private static final String DEFAULT_SPOTIFY_RAINBOW_SPREAD = "0.035";
   private static final String DEFAULT_SPOTIFY_RAINBOW_SATURATION = "0.35";
   private static final String DEFAULT_SPOTIFY_RAINBOW_BRIGHTNESS = "1.0";
   private static final String DEFAULT_SPOTIFY_RAINBOW_DIRECTION = "Forward";
   private static final String DEFAULT_SPOTIFY_RAINBOW_ARTIST = "true";
   private static final String DEFAULT_SPOTIFY_RAINBOW_TITLE = "true";
   private static final String DEFAULT_SPOTIFY_RAINBOW_TIME = "false";
   private static final String DEFAULT_SPOTIFY_RAINBOW_PROGRESS = "true";
   private static final String DEFAULT_STAIR_SNAP = "2";
   private static final Minecraft MC = Minecraft.getInstance();
   private static long lastHudErrorLogMs;
   private static final CompactTheme THEME = new CompactTheme();
   private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
   private static final Identifier HUD_LOGO = Identifier.fromNamespaceAndPath("riptide", "textures/gui/hud/riptide_hud.png");
   private static final int HUD_LOGO_TEXTURE_WIDTH = 552;
   private static final int HUD_LOGO_TEXTURE_HEIGHT = 52;
   private static final int HUD_LOGO_DISPLAY_WIDTH = 552;
   private static final int HUD_LOGO_DISPLAY_HEIGHT = 52;
   private static final int HUD_OUTLINE_MERGE_TOLERANCE = 1;
   private static final int HUD_SAFE_ZONE_X = 1;
   private static final int HUD_SAFE_ZONE_Y = 2;
   private static final int HUD_OUTLINE_CHROME_PADDING = 2;
   private static final List<String> ORDER = List.of(
      "active_modules",
      "tps",
      "coordinates",
      "nether_coords",
      "fps",
      "ping",
      "speed",
      "game_mode",
      "durability",
      "looking_at",
      "breaking_progress",
      "server",
      "server_ip",
      "server_brand",
      "weather",
      "biome",
      "world_time",
      "real_time",
      "rotation",
      "watermark",
      "armor",
      "inventory",
      "item_counter",
      "potion_timers",
      "compass",
      "anti_vanish",
      "cps",
      "keystrokes",
      "memory",
      "fps_graph",
      "spotify"
   );
   private static double lastX;
   private static double lastZ;
   private static long lastSpeedGameTime = Long.MIN_VALUE;
   private static boolean lastSpeedFreecam;
   private static double blocksPerSecond;
   private static double cachedRainbowSpeed = 1.0;
   private static int cachedRainbowSpeedRev = Integer.MIN_VALUE;
   private static final Map<String, RiptideHudManager.CachedHudElement> HUD_CACHE = new HashMap<>();
   private static final Map<String, Map<String, String>> DEFAULT_SETTINGS_CACHE = new HashMap<>();
   private static long hudRenderFrame;
   private static int metricsStableWidth;
   private static long metricsWidthHoldUntil;
   private static List<RiptideHudManager.ElementBounds> HUD_OCCLUDERS = List.of();
   private static boolean defaultsEnsured;
   private static List<String> allIdsCache = ORDER;
   private static int allIdsCacheRevision = -1;
   private static int lastHudElementsRevision = -1;
   private static final Map<String, RiptideConfig.HudElementState> STATE_CACHE = new HashMap<>();
   private static RiptideConfig stateCacheConfig;
   private static int hudSettingsRevision;
   private static boolean fastPassActive;
   private static boolean frameEnvChanged;
   private static long frameNowMs;
   private static int lastEnvScreenW;
   private static int lastEnvScreenH;
   private static int lastEnvPlayerId;
   private static int lastEnvFontId;
   private static int lastEnvSettingsRev;
   private static int lastEnvModuleRev;
   private static int lastEnvHudElementsRev;
   private static int lastEnvConfigId;
   private static int lastEnvThemeRev;
   private static final String[] COMPASS_LABELS = new String[]{"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
   private static final int[] COMPASS_LABEL_WIDTHS = new int[COMPASS_LABELS.length];
   private static Font compassWidthsFont;
   private static int compassWidthsReloadGen = Integer.MIN_VALUE;
   private static long lastSpotifyTickErrorLogMs;
   private static boolean spotifyGateRejectedLogged;
   private static boolean spotifyRenderLogged;
   private static final int SPOTIFY_ART_SIZE = 32;
   private static final int SPOTIFY_ART_GAP = 7;
   private static final int SPOTIFY_ART_RADIUS = 5;
   private static final int SPOTIFY_PROGRESS_H = 3;
   private static RiptideHudManager.SpotifyTextCache spotifyTextCache;
   private static RiptideMarquee.CompositionKey spotifyTextCacheKey = new RiptideMarquee.CompositionKey(Long.MIN_VALUE, "", null, -1);
   private static final RiptideHudManager.MusicCardGeom SPOTIFY_GEOM = new RiptideHudManager.MusicCardGeom();
   private static boolean spotifyLastArtLoaded;
   private static double spotifyPositionAnchorSec = Double.NaN;
   private static long spotifyPositionAnchorAtMs;
   private static long spotifyTimeCacheSec = Long.MIN_VALUE;
   private static double spotifyTimeCacheDuration = Double.NaN;
   private static String spotifyTimeCacheText = "";
   private static int spotifyTimeCacheWidth;
   private static final Identifier SPOTIFY_ART_ID = Identifier.fromNamespaceAndPath("riptide", "spotify_art");
   private static RiptideHudManager.SpotifyArtTexture spotifyArtTexture;
   private static String spotifyArtPath = "";
   private static long spotifyArtStamp = Long.MIN_VALUE;
   private static long spotifyArtFailAtMs;
   private static int spotifyArtWidth;
   private static int spotifyArtHeight;
   private static long spotifyArtFileMtime = -1L;
   private static long spotifyArtFileSize = -1L;
   private static final long SPOTIFY_ART_RETRY_MS = 500L;
   private static volatile RiptideHudManager.ArtDecodeRequest spotifyArtDecodeRequest;
   private static final Object ART_DECODE_LOCK = new Object();
   private static RiptideHudManager.ArtDecodeResult spotifyArtDecodeResult;
   private static Thread spotifyArtDecodeThread;
   private static long lastArtErrorLogMs;
   private static boolean spotifyMoveDragging;
   private static int spotifyMoveGrabX;
   private static int spotifyMoveGrabY;
   private static Screen spotifyDragScreen;
   private static final float[] KS_FILL = new float[7];
   private static long ksAnimNanos;
   private static final int FPS_BUF_SIZE = 200;
   private static final int[] fpsSamples = new int[200];
   private static int fpsSampleIndex;
   private static int fpsSampleCount;
   private static double tpsRollingMin = 20.0;
   private static long tpsMinSetTime;
   private static int lastPingValue;
   private static volatile boolean dayTimeResolved;
   private static Method dayTimeGetter;
   private static Method dayTimeLevelData;
   private static int spotifySyncedSource = -1;
   private static final Map<String, Integer> ORDER_INDEX = buildOrderIndex();
   private static List<RiptideHudManager.HudRectEntry> frameRects;
   private static final RiptideHudManager.CachedHudElement[] FRAME_RECT_CACHE_ENTRIES = new RiptideHudManager.CachedHudElement[ORDER.size()];
   private static List<RiptideHudManager.HudRectEntry> cachedFrameRects = List.of();
   private static long cachedFrameRectOccluders = Long.MIN_VALUE;

   private RiptideHudManager() {
   }

   private static List<String> allIds() {
      if (HudElements.isEmpty()) {
         return ORDER;
      } else {
         int var0 = HudElements.revision();
         if (var0 != allIdsCacheRevision) {
            ArrayList var1 = new ArrayList<>(ORDER);
            var1.addAll(HudElements.ids());
            allIdsCache = var1;
            allIdsCacheRevision = var0;
         }

         return allIdsCache;
      }
   }

   public static int enabledElementCount() {
      ensureDefaults();
      int var0 = 0;

      for (String var2 : allIds()) {
         if (state(var2).enabled) {
            var0++;
         }
      }

      return var0;
   }

   public static void ensureDefaults() {
      RiptideConfig var0 = RiptideConfig.getGlobal();
      int var1 = HudElements.revision();
      if (!defaultsEnsured || !var0.hudLayoutMigrated || var1 != lastHudElementsRevision) {
         STATE_CACHE.clear();
         if (!var0.hudLayoutMigrated) {
            migrateOldHud(var0);
         }

         for (String var3 : allIds()) {
            state(var3);
         }

         normalizeDefaultHudStack(var0);
         defaultsEnsured = true;
         lastHudElementsRevision = var1;
      }
   }

   public static List<String> elementIds() {
      ensureDefaults();
      return allIds();
   }

   public static String label(String var0) {
      return switch (var0) {
         case "active_modules" -> "Active Modules";
         case "tps" -> "TPS";
         case "coordinates" -> "Coordinates";
         case "nether_coords" -> "Nether Coords";
         case "fps" -> "FPS";
         case "ping" -> "Ping";
         case "speed" -> "Speed";
         case "game_mode" -> "Game Mode";
         case "durability" -> "Durability";
         case "looking_at" -> "Looking At";
         case "breaking_progress" -> "Breaking Progress";
         case "server" -> "Server";
         case "weather" -> "Weather";
         case "biome" -> "Biome";
         case "world_time" -> "World Time";
         case "real_time" -> "Real Time";
         case "rotation" -> "Rotation";
         case "watermark" -> "Logo";
         case "armor" -> "Armor";
         case "inventory" -> "Inventory";
         case "item_counter" -> "Item Counter";
         case "potion_timers" -> "Potion Timers";
         case "compass" -> "Compass";
         case "anti_vanish" -> "Anti Vanish";
         case "cps" -> "CPS";
         case "keystrokes" -> "Keystrokes";
         case "memory" -> "Memory";
         case "server_ip" -> "Server IP";
         case "server_brand" -> "Server Brand";
         case "fps_graph" -> "FPS Graph";
         case "spotify" -> "Spotify";
         default -> {
            HudElementProvider var3 = HudElements.get(var0);
            yield var3 != null ? var3.label() : var0;
         }
      };
   }

   public static String description(String var0) {
      return switch (var0) {
         case "active_modules" -> "Enabled Riptide modules with optional info and keybinds.";
         case "tps" -> "Estimated server TPS from Riptide's tick tracker.";
         case "coordinates" -> "Current player coordinates in this dimension.";
         case "nether_coords" -> "Overworld/Nether converted coordinates using the 8:1 ratio.";
         case "fps" -> "Current client FPS.";
         case "ping" -> "Current player latency.";
         case "speed" -> "Horizontal player speed in blocks per second.";
         case "game_mode" -> "Current game mode.";
         case "durability" -> "Main hand durability.";
         case "looking_at" -> "Block or entity currently under the crosshair.";
         case "breaking_progress" -> "Current vanilla block-breaking progress.";
         case "server" -> "Current server or world.";
         case "weather" -> "Current world weather.";
         case "biome" -> "Current biome.";
         case "world_time" -> "Current world day time.";
         case "real_time" -> "Local system time.";
         case "rotation" -> "Camera direction, yaw, and pitch.";
         case "watermark" -> "Movable Riptide Client logo image.";
         case "armor" -> "Equipped armor with vanilla item overlays.";
         case "inventory" -> "Main inventory grid.";
         case "item_counter" -> "Counts a picked or held item across the inventory.";
         case "potion_timers" -> "Active status effects and durations.";
         case "compass" -> "Compact facing compass.";
         case "anti_vanish" -> "Vanish detections.";
         case "cps" -> "Left and right mouse clicks per second (counts Fast Use too).";
         case "keystrokes" -> "Movement keys and mouse buttons with fill animation.";
         case "memory" -> "JVM heap memory usage and max.";
         case "server_ip" -> "Connected server IP and port.";
         case "server_brand" -> "Server software brand and version.";
         case "fps_graph" -> "Recent FPS history as a graph.";
         case "spotify" -> "Spotify now playing (Windows, Linux, macOS).";
         default -> {
            HudElementProvider var3 = HudElements.get(var0);
            yield var3 != null ? var3.description() : "";
         }
      };
   }

   public static RiptideConfig.HudElementState state(String var0) {
      RiptideConfig var1 = RiptideConfig.getGlobal();
      if (var1 != stateCacheConfig) {
         STATE_CACHE.clear();
         stateCacheConfig = var1;
      }

      RiptideConfig.HudElementState var2 = STATE_CACHE.get(var0);
      if (var2 != null) {
         return var2;
      } else {
         ensureStateMap(var1);
         RiptideConfig.HudElementState var3 = var1.hudElements.computeIfAbsent(var0, var0x -> defaultState(var0x));
         if (var3.settings == null) {
            var3.settings = new LinkedHashMap<>();
         }

         for (Entry var5 : defaultSettings(var0).entrySet()) {
            var3.settings.putIfAbsent((String)var5.getKey(), (String)var5.getValue());
         }

         STATE_CACHE.put(var0, var3);
         return var3;
      }
   }

   public static void save() {
      RiptideConfig.getGlobal().save();
   }

   public static void tickHeartbeat() {
      try {
         if (MC.player == null || MC.level == null) {
            return;
         }

         if (!state("spotify").enabled) {
            return;
         }

         if (!RiptideLiteVariant.enabled()) {
            RiptideSpotify.setWanted();
         }
      } catch (Throwable var3) {
         long var1 = System.currentTimeMillis();
         if (var1 - lastSpotifyTickErrorLogMs > 5000L) {
            lastSpotifyTickErrorLogMs = var1;
            riptide.RiptideClientAddon.LOG.warn("[Riptide] Spotify tick heartbeat failed; skipped", var3);
         }
      }
   }

   public static boolean shouldRenderInGame(Screen var0, Module var1) {
      if (PackHideState.isActive()) {
         return false;
      } else if (var1 == null || !var1.isEnabled() || MC.player == null || MC.gui.hud.isHidden()) {
         return false;
      } else if (var0 == null) {
         return true;
      } else if (var0 instanceof RiptideHudEditorScreen) {
         return false;
      } else if (var0 instanceof ChatScreen) {
         return bool(var1, "show-in-chat", false);
      } else if (var0.isPauseScreen()) {
         return bool(var1, "show-in-pause", false);
      } else {
         return bool(var1, "hide-in-guis", true) ? false : !(var0 instanceof AbstractContainerScreen) || !bool(var1, "hide-in-guis", true);
      }
   }

   public static void tick() {
      if (MC.player == null) {
         lastSpeedGameTime = Long.MIN_VALUE;
         blocksPerSecond = 0.0;
      } else if (MC.level != null) {
         long var0 = MC.level.getGameTime();
         if (var0 != lastSpeedGameTime) {
            boolean var2 = freecamView();
            if (var2 != lastSpeedFreecam) {
               lastSpeedFreecam = var2;
               lastSpeedGameTime = Long.MIN_VALUE;
               blocksPerSecond = 0.0;
            }

            Vec3 var3 = viewFootPos();
            double var4 = var3.x;
            double var6 = var3.z;
            if (lastSpeedGameTime != Long.MIN_VALUE) {
               long var8 = Math.max(1L, var0 - lastSpeedGameTime);
               double var10 = var4 - lastX;
               double var12 = var6 - lastZ;
               double var14 = Math.sqrt(var10 * var10 + var12 * var12);
               double var16 = var14 * (20.0 / var8);
               if (var16 > 80.0 && !var2) {
                  var16 = horizontalVelocityBps();
               }

               if (var16 < 0.01) {
                  var16 = 0.0;
               }

               blocksPerSecond = blocksPerSecond * 0.7 + var16 * 0.3;
               if (blocksPerSecond < 0.03) {
                  blocksPerSecond = 0.0;
               }
            }

            lastX = var4;
            lastZ = var6;
            lastSpeedGameTime = var0;
         }
      }
   }

   private static double horizontalVelocityBps() {
      if (MC.player == null) {
         return 0.0;
      } else {
         double var0 = MC.player.getDeltaMovement().x;
         double var2 = MC.player.getDeltaMovement().z;
         return Math.sqrt(var0 * var0 + var2 * var2) * 20.0;
      }
   }

   public static void render(GuiGraphicsExtractor var0, Font var1, boolean var2, String var3, int var4, int var5) {
      render(var0, var1, var2, var3, var4, var5, List.of());
   }

   private static void logHudError(String var0, Throwable var1) {
      long var2 = System.currentTimeMillis();
      if (var2 - lastHudErrorLogMs >= 5000L) {
         lastHudErrorLogMs = var2;
         riptide.RiptideClientAddon.LOG.warn("[Riptide] HUD '{}' render failed; skipped to protect the UI", var0, var1);
      }
   }

   public static void render(GuiGraphicsExtractor var0, Font var1, boolean var2, String var3, int var4, int var5, List<RiptideHudManager.ElementBounds> var6) {
      long var7 = RiptidePerf.beginSampled();
      ensureDefaults();
      beginFramePass(var1, var2);
      HUD_OCCLUDERS = !var2 && var6 != null && !var6.isEmpty() ? var6 : List.of();

      try {
         hudRenderFrame++;
         tick();
         frameRects = collectFrameRects(var1);

         for (String var10 : allIds()) {
            RiptideConfig.HudElementState var11 = state(var10);
            if (var11.enabled && (var2 || !"anti_vanish".equals(var10) || AntiVanishModule.shouldShowHud()) && !combinedMetricsRowOwns(var10)) {
               RiptideHudManager.CachedHudElement var12 = cached(var10, var1);
               RiptideHudManager.ElementBounds var13 = var12.layout().bounds();
               if (var13.width > 0 && var13.height > 0) {
                  int var14 = 0;
                  if (!var2 && "anti_vanish".equals(var10)) {
                     var14 = computeDodge(var13);
                  } else if (!"active_modules".equals(var10) && occluded(var13)) {
                     continue;
                  }

                  boolean var15 = hover(var4, var5, var13);

                  try {
                     renderElement(var0, var1, var10, var11, var12, var2, var3 != null && var3.equals(var10), var15, var14);
                  } catch (Throwable var20) {
                     logHudError("element:" + var10, var20);
                  }
               }
            }
         }
      } finally {
         HUD_OCCLUDERS = List.of();
         frameRects = null;
         endFramePass();
         RiptidePerf.end("hud.render", var7);
      }
   }

   public static RiptideHudManager.HudLayout layout(String var0, Font var1) {
      return cached(var0, var1).layout();
   }

   public static void renderSingle(GuiGraphicsExtractor var0, Font var1, String var2) {
      if (var0 != null && var1 != null && var2 != null && !PackHideState.isActive()) {
         ensureDefaults();
         RiptideConfig.HudElementState var3 = state(var2);
         if (var3.enabled && (!"anti_vanish".equals(var2) || AntiVanishModule.shouldShowHud())) {
            beginFramePass(var1, false);

            try {
               frameRects = collectFrameRects(var1);
               RiptideHudManager.CachedHudElement var4 = cached(var2, var1);
               RiptideHudManager.ElementBounds var5 = var4.layout().bounds();
               if (var5.width() <= 0 || var5.height() <= 0) {
                  return;
               }

               renderElement(var0, var1, var2, var3, var4, false, false, false, 0);
            } finally {
               frameRects = null;
               endFramePass();
            }

            return;
         }
      }
   }

   private static RiptideHudManager.HudLayout computeLayout(
      String var0, Font var1, RiptideConfig.HudElementState var2, List<RiptideHudManager.HudLine> var3, List<Integer> var4
   ) {
      int var5 = padding(var0);
      int var6;
      int var7;
      if ("watermark".equals(var0)) {
         int var8 = logoWidth(var0);
         var6 = var8 + var5 * 2 + logoRightPadding(var0);
         var7 = logoHeight(var8) + var5 * 2;
      } else if ("armor".equals(var0)) {
         var6 = var5 * 2 + 72;
         var7 = var5 * 2 + 18;
      } else if ("inventory".equals(var0)) {
         var6 = var5 * 2 + 162;
         var7 = var5 * 2 + 54;
      } else if ("compass".equals(var0)) {
         var6 = var5 * 2 + compassWidth(var0);
         var7 = var5 * 2 + 18;
      } else if ("spotify".equals(var0) && !RiptideLiteVariant.enabled()) {
         RiptideSpotify.Snapshot var18 = RiptideSpotify.snapshot();
         boolean var25 = spotifyHasTrack(var18);
         boolean var33 = spotifyPart(var0, "spotify-part-art")
            && (!var25 || spotifyArtTexture(var18.artworkPath(), var18.updatedAtMs(), var18.artist() + "|" + var18.title()) != null);
         var6 = var5 * 2 + spotifyWidth(var0);
         var7 = var5 * 2 + Math.max(var33 ? 32 : 0, spotifyPart(var0, "spotify-part-artist") ? 20 : THEME.fontHeight(UiTone.BODY));
         if (spotifyPart(var0, "spotify-part-progress")) {
            var7 += 5;
         }
      } else if ("keystrokes".equals(var0)) {
         int var13 = keystrokeUnit(var0);
         int var9 = keystrokeKeyHeight(var13);
         byte var10 = 2;
         var6 = var5 * 2 + 3 * var13 + 2 * var10;
         int var11 = 2 * var9 + var10;
         if (keystrokesShowSpace(var0)) {
            var11 += var10 + var9;
         }

         if (keystrokesShowMouse(var0)) {
            var11 += var10 + var9;
         }

         var7 = var5 * 2 + var11;
      } else if (HudElements.isAddon(var0)) {
         HudElementProvider var14 = HudElements.get(var0);
         int var20 = 16;
         int var27 = 10;

         try {
            var20 = Math.max(1, var14.width());
            var27 = Math.max(1, var14.height());
         } catch (Throwable var12) {
            riptide.RiptideClientAddon.LOG.warn("[Hud] Addon element '{}' sizing failed", var0, var12);
         }

         var6 = var5 * 2 + var20;
         var7 = var5 * 2 + var27;
      } else if ("active_modules".equals(var0)) {
         int var15 = 0;

         for (Integer var28 : var4) {
            var15 = Math.max(var15, var28);
         }

         int var22 = activeModuleRowHeight(var0);
         int var29 = lineGap(var0);
         var6 = Math.max(32, var15 + var5 * 2);
         var7 = var3.isEmpty() ? 0 : var3.size() * var22 + Math.max(0, var3.size() - 1) * var29;
      } else if ("fps".equals(var0)) {
         int var16 = 0;

         for (Integer var30 : var4) {
            var16 = Math.max(var16, var30);
         }

         var6 = stableMetricsWidth(var16) + var5 * 2;
         var7 = THEME.fontHeight(UiTone.BODY) + verticalPadding(var0) * 2;
      } else {
         int var17 = lineHeight(var0);
         int var24 = 0;

         for (Integer var34 : var4) {
            var24 = Math.max(var24, var34);
         }

         var6 = Math.max(32, var24 + var5 * 2);
         int var32 = verticalPadding(var0);
         var7 = Math.max(THEME.fontHeight(UiTone.BODY) + var32 * 2, var3.size() * var17 - lineGap(var0) + var32 * 2);
      }

      int var19 = safeContentX(var0, anchorX(var2.anchor, var2.x, var6), var6);
      int var26 = safeContentY(var0, anchorY(var2.anchor, var2.y, var7), var7);
      return new RiptideHudManager.HudLayout(var0, var19, var26, var6, var7, 1.0);
   }

   public static RiptideHudManager.ElementBounds bounds(String var0, Font var1) {
      return layout(var0, var1).bounds();
   }

   private static RiptideHudManager.ElementBounds visualBounds(String var0, Font var1) {
      RiptideHudManager.CachedHudElement var2 = cached(var0, var1);
      if (!"active_modules".equals(var0)) {
         RiptideHudManager.ElementBounds var10 = var2.layout().bounds();
         RiptideHudManager.VisualRect var11 = visualChromeRect(
            new RiptideHudManager.VisualRect(var0, var10.x(), var10.y(), var10.width(), var10.height()), var2.style()
         );
         return new RiptideHudManager.ElementBounds(var0, var11.x(), var11.y(), var11.width(), var11.height());
      } else {
         List var3 = activeModuleVisualRects(var0, var2);
         if (var3.isEmpty()) {
            return var2.layout().bounds();
         } else {
            int var4 = Integer.MAX_VALUE;
            int var5 = Integer.MAX_VALUE;
            int var6 = Integer.MIN_VALUE;
            int var7 = Integer.MIN_VALUE;

            for (RiptideHudManager.VisualRect var9 : var3) {
               var4 = Math.min(var4, var9.x());
               var5 = Math.min(var5, var9.y());
               var6 = Math.max(var6, var9.right());
               var7 = Math.max(var7, var9.bottom());
            }

            return new RiptideHudManager.ElementBounds(var0, var4, var5, Math.max(0, var6 - var4), Math.max(0, var7 - var5));
         }
      }
   }

   public static String hit(Font var0, int var1, int var2) {
      ensureDefaults();
      List var3 = allIds();

      for (int var4 = var3.size() - 1; var4 >= 0; var4--) {
         String var5 = (String)var3.get(var4);
         if (state(var5).enabled) {
            RiptideHudManager.ElementBounds var6 = visualBounds(var5, var0);
            if (hover(var1, var2, var6)) {
               return var5;
            }
         }
      }

      return null;
   }

   public static void move(String var0, int var1, int var2, int var3, int var4) {
      if (var3 > 0 && var4 > 0) {
         RiptideConfig.HudElementState var5 = state(var0);
         RiptideHudManager.HudLayout var6 = layout(var0, MC.font);
         int var7 = var6.scaledWidth();
         int var8 = var6.scaledHeight();
         int var9 = clamp(var1, safeZoneXFor(var0), maxSafeX(var0, var3, var7));
         int var10 = clamp(var2, safeZoneYFor(var0), maxSafeY(var0, var4, var8));
         boolean var11 = var9 > var3 / 2 || var9 + var7 >= var3 - 2 || var9 + var7 / 2 >= var3 / 2;
         boolean var12 = var10 > var4 / 2 || var10 + var8 >= var4 - 2 || var10 + var8 / 2 >= var4 / 2;
         var5.anchor = (var12 ? "BOTTOM_" : "TOP_") + (var11 ? "RIGHT" : "LEFT");
         var5.x = var11 ? var9 + var7 - var3 : var9;
         var5.y = var12 ? var10 + var8 - var4 : var10;
         HUD_CACHE.remove(var0);
         hudSettingsRevision++;
         save();
      }
   }

   public static void setEnabled(String var0, boolean var1) {
      state(var0).enabled = var1;
      hudSettingsRevision++;
      save();
   }

   public static void toggle(String var0) {
      RiptideConfig.HudElementState var1 = state(var0);
      var1.enabled = !var1.enabled;
      hudSettingsRevision++;
      save();
   }

   public static String setting(String var0, String var1) {
      return state(var0).settings.getOrDefault(var1, defaultSettings(var0).getOrDefault(var1, ""));
   }

   public static String defaultSetting(String var0, String var1) {
      return defaultSettings(var0).getOrDefault(var1, "");
   }

   public static void setSetting(String var0, String var1, String var2) {
      state(var0).settings.put(var1, var2 == null ? "" : var2);
      hudSettingsRevision++;
      save();
   }

   public static boolean boolSetting(String var0, String var1) {
      return Boolean.parseBoolean(setting(var0, var1));
   }

   public static int intSetting(String var0, String var1, int var2) {
      try {
         return Integer.parseInt(setting(var0, var1));
      } catch (Exception var4) {
         return var2;
      }
   }

   public static double doubleSetting(String var0, String var1, double var2) {
      try {
         return Double.parseDouble(setting(var0, var1));
      } catch (Exception var5) {
         return var2;
      }
   }

   public static void resetElement(String var0) {
      RiptideConfig var1 = RiptideConfig.getGlobal();
      var1.hudElements.put(var0, defaultState(var0));
      STATE_CACHE.remove(var0);
      hudSettingsRevision++;
      save();
   }

   public static void resetAllElements() {
      RiptideConfig var0 = RiptideConfig.getGlobal();
      ensureStateMap(var0);
      var0.hudElements.clear();

      for (String var2 : allIds()) {
         var0.hudElements.put(var2, defaultState(var2));
      }

      defaultsEnsured = false;
      HUD_CACHE.clear();
      STATE_CACHE.clear();
      hudSettingsRevision++;
      save();
   }

   public static List<String> lines(String var0) {
      ArrayList var1 = new ArrayList();

      for (RiptideHudManager.HudLine var3 : cached(var0, MC.font).lines()) {
         var1.add(var3.plainText());
      }

      if (var1.isEmpty()) {
         var1.add(label(var0));
      }

      return var1;
   }

   public static int settingsRevision() {
      return hudSettingsRevision;
   }

   private static void beginFramePass(Font var0, boolean var1) {
      frameNowMs = System.currentTimeMillis();
      int var2 = anchorScreenWidth();
      int var3 = anchorScreenHeight();
      int var4 = MC.player == null ? 0 : MC.player.getId();
      int var5 = System.identityHashCode(var0);
      int var6 = ModuleRegistry.revision();
      int var7 = HudElements.revision();
      int var8 = System.identityHashCode(RiptideConfig.getGlobal());
      int var9 = RiptideTheme.generation();
      frameEnvChanged = var2 != lastEnvScreenW
         || var3 != lastEnvScreenH
         || var4 != lastEnvPlayerId
         || var5 != lastEnvFontId
         || hudSettingsRevision != lastEnvSettingsRev
         || var6 != lastEnvModuleRev
         || var7 != lastEnvHudElementsRev
         || var8 != lastEnvConfigId
         || var9 != lastEnvThemeRev;
      lastEnvScreenW = var2;
      lastEnvScreenH = var3;
      lastEnvPlayerId = var4;
      lastEnvFontId = var5;
      lastEnvSettingsRev = hudSettingsRevision;
      lastEnvModuleRev = var6;
      lastEnvHudElementsRev = var7;
      lastEnvConfigId = var8;
      lastEnvThemeRev = var9;
      fastPassActive = !var1;
   }

   private static void endFramePass() {
      fastPassActive = false;
   }

   private static RiptideHudManager.CachedHudElement cached(String var0, Font var1) {
      RiptideHudManager.CachedHudElement var2 = HUD_CACHE.get(var0);
      if (fastPassActive && !frameEnvChanged && var2 != null && frameNowMs < var2.nextSignatureCheckAtMs) {
         return var2;
      } else {
         RiptideConfig.HudElementState var3 = state(var0);
         long var4 = fastPassActive ? frameNowMs : System.currentTimeMillis();
         RiptideHudManager.HudCacheKey var6 = cacheSignature(var0, var3, var1, var4);
         long var7 = cacheIntervalMillis(var0);
         long var9 = (var4 / var7 + 1L) * var7;
         RiptideHudManager.CachedHudElement var11 = HUD_CACHE.get(var0);
         if (var11 != null && var11.signature().equals(var6)) {
            var11.nextSignatureCheckAtMs = var9;
            return var11;
         } else {
            List var12 = !"armor".equals(var0)
                  && !"inventory".equals(var0)
                  && !"compass".equals(var0)
                  && !"watermark".equals(var0)
                  && !"keystrokes".equals(var0)
                  && !"spotify".equals(var0)
                  && !HudElements.isAddon(var0)
               ? buildLines(var0)
               : List.of();
            ArrayList var13 = new ArrayList(var12.size());

            for (RiptideHudManager.HudLine var15 : var12) {
               var13.add(stableLineWidth(var0, var1, var15));
            }

            RiptideHudManager.HudLayout var16 = computeLayout(var0, var1, var3, var12, var13);
            RiptideHudManager.CachedHudElement var17 = new RiptideHudManager.CachedHudElement(var6, var12, var13, var16, computeStyle(var0));
            var17.nextSignatureCheckAtMs = var9;
            HUD_CACHE.put(var0, var17);
            return var17;
         }
      }
   }

   private static int stableLineWidth(String var0, Font var1, RiptideHudManager.HudLine var2) {
      int var3 = lineWidth(var1, var2);
      if ("breaking_progress".equals(var0)) {
         var3 = Math.max(var3, lineWidth(var1, row(var0, "Breaking", "100%")));
      }

      return var3;
   }

   private static RiptideHudManager.HudCacheKey cacheSignature(String var0, RiptideConfig.HudElementState var1, Font var2, long var3) {
      long var5 = var3 / cacheIntervalMillis(var0);
      int var7 = anchorScreenWidth();
      int var8 = anchorScreenHeight();
      int var9 = MC.player == null ? 0 : MC.player.getId();
      int var10 = "active_modules".equals(var0) ? ModuleRegistry.activeRevision() : 0;
      int var11 = "active_modules".equals(var0) ? ModuleRegistry.revision() : 0;
      return new RiptideHudManager.HudCacheKey(
         var1.enabled,
         var1.anchor,
         var1.x,
         var1.y,
         var1.settings.hashCode(),
         var5,
         var7,
         var8,
         var9,
         var10,
         var11,
         System.identityHashCode(var2),
         RiptideTheme.generation()
      );
   }

   private static long cacheIntervalMillis(String var0) {
      return switch (var0) {
         case "active_modules", "coordinates", "nether_coords", "speed", "looking_at", "breaking_progress", "rotation", "compass", "anti_vanish", "cps", "keystrokes", "spotify" -> 50L;
         case "fps", "tps", "ping", "real_time", "world_time", "potion_timers", "item_counter" -> 250L;
         default -> 500L;
      };
   }

   private static boolean freecamView() {
      return PackFreecamState.isActive();
   }

   private static Vec3 viewFootPos() {
      if (freecamView()) {
         return PackFreecamState.footPosition(1.0F);
      } else {
         return MC.player == null ? Vec3.ZERO : MC.player.position();
      }
   }

   private static BlockPos viewBlockPos() {
      if (freecamView()) {
         return BlockPos.containing(PackFreecamState.footPosition(1.0F));
      } else {
         return MC.player == null ? BlockPos.ZERO : MC.player.blockPosition();
      }
   }

   private static float viewYaw() {
      if (freecamView()) {
         return PackFreecamState.getYaw(1.0F);
      } else {
         return MC.player == null ? 180.0F : MC.player.getYRot();
      }
   }

   private static float viewPitch() {
      if (freecamView()) {
         return PackFreecamState.getPitch(1.0F);
      } else {
         return MC.player == null ? 0.0F : MC.player.getXRot();
      }
   }

   private static Vec3 pickOrigin() {
      if (freecamView() && PackFreecamState.interactEnabled()) {
         return PackFreecamState.footPosition(1.0F);
      } else {
         return MC.player == null ? Vec3.ZERO : MC.player.position();
      }
   }

   private static List<RiptideHudManager.HudLine> buildLines(String var0) {
      ArrayList var1 = new ArrayList();
      if (MC.player == null) {
         var1.add(row(var0, label(var0), "Preview"));
         return var1;
      } else {
         switch (var0) {
            case "active_modules":
               activeModuleLines(var1);
               break;
            case "tps":
               var1.add(tpsLine());
               break;
            case "coordinates":
               Vec3 var4 = viewFootPos();
               double[] var5 = RiptideFakeCoords.apply(var4.x, var4.y, var4.z);
               var1.add(row(var0, "Pos", blockPositionText(var5[0], var5[1], var5[2])));
               break;
            case "nether_coords":
               var1.add(oppositeCoordsLine());
               break;
            case "fps":
               var1.add(metricsLine());
               break;
            case "ping":
               var1.add(pingLine());
               break;
            case "speed":
               var1.add(row(var0, "Speed", String.format(Locale.ROOT, "%.2f b/s", blocksPerSecond)));
               break;
            case "game_mode":
               var1.add(row(var0, "Game", gameMode()));
               break;
            case "durability":
               var1.add(durabilityLine());
               break;
            case "looking_at":
               var1.add(lookingAtLine());
               break;
            case "breaking_progress":
               var1.add(breakingLine());
               break;
            case "server":
               var1.add(serverLine());
               break;
            case "weather":
               var1.add(weatherLine());
               break;
            case "biome":
               var1.add(biomeLine());
               break;
            case "world_time":
               var1.add(worldTimeLine());
               break;
            case "real_time":
               var1.add(realTimeLine());
               break;
            case "rotation":
               var1.add(row(var0, "Rot", String.format(Locale.ROOT, "%.1f yaw, %.1f pitch", Mth.wrapDegrees(viewYaw()), viewPitch())));
            case "watermark":
               break;
            case "item_counter":
               var1.add(itemCounterLine());
               break;
            case "potion_timers":
               potionLines(var1);
               break;
            case "compass":
               var1.add(row(var0, "Compass", directionName()));
               break;
            case "anti_vanish":
               antiVanishLines(var1);
               break;
            case "cps":
               cpsLines(var1);
               break;
            case "memory":
               var1.add(memoryLine());
               break;
            case "server_ip":
               var1.add(serverIpLine());
               break;
            case "server_brand":
               var1.add(row(var0, "Brand", serverBrand()));
               break;
            case "fps_graph":
               var1.add(fpsGraphLine());
               break;
            default:
               var1.add(row(var0, label(var0), ""));
         }

         if ("active_modules".equals(var0) && var1.isEmpty()) {
            return var1;
         } else {
            if (var1.isEmpty()) {
               var1.add(row(var0, label(var0), ""));
            }

            return var1;
         }
      }
   }

   private static void activeModuleLines(List<RiptideHudManager.HudLine> var0) {
      boolean var1 = boolSetting("active_modules", "module-info");
      boolean var2 = boolSetting("active_modules", "show-keybind");
      String var3 = "|" + setting("active_modules", "hidden-modules").toLowerCase(Locale.ROOT) + "|";
      ArrayList var4 = new ArrayList<>(ModuleRegistry.activeModules());
      var4.removeIf(var0x -> !var0x.showInArrayList());
      var4.removeIf(var1x -> var3.contains("|" + var1x.id().toLowerCase(Locale.ROOT) + "|"));
      String var5 = setting("active_modules", "sort");
      if ("Name".equals(var5)) {
         var4.sort(Comparator.comparing(Module::name, String.CASE_INSENSITIVE_ORDER));
      } else if ("Category".equals(var5)) {
         var4.sort(Comparator.<Module, String>comparing(var0x -> var0x.category().label()).thenComparing(Module::name));
      } else {
         IdentityHashMap var6 = new IdentityHashMap(var4.size() * 2);

         for (Module var8 : var4) {
            var6.put(var8, modulePlainWidth(var8, var1, var2));
         }

         var4.sort((var1x, var2x) -> {
            int var3x = Integer.compare((Integer)var6.get(var2x), (Integer)var6.get(var1x));
            return var3x != 0 ? var3x : var1x.name().compareToIgnoreCase(var2x.name());
         });
      }

      String var15 = setting("active_modules", "color-mode");
      int var16 = var4.size();
      RiptideHudManager.RainbowParams var17 = buildRainbowParams(var15, var16);
      int var9 = color("module-info-color", -4743522);

      for (int var10 = 0; var10 < var4.size(); var10++) {
         Module var11 = (Module)var4.get(var10);
         int var12 = activeModuleColor(var11, var10, var17);
         RiptideHudManager.HudLine var13 = new RiptideHudManager.HudLine();
         var13.add(var11.name(), var12);
         String var14 = var11.info();
         if (var1 && var14 != null && !var14.isBlank()) {
            var13.add(" " + var14, var9);
         }

         if (var2 && var11.keybind() != -1) {
            var13.add(" [" + RiptideBindUtil.getBindName(var11.keybind()) + "]", var9);
         }

         var0.add(var13);
      }
   }

   private static void antiVanishLines(List<RiptideHudManager.HudLine> var0) {
      for (AntiVanishModule.HudEntry var2 : AntiVanishModule.hudEntries()) {
         var0.add(row("anti_vanish", AntiVanishModule.hudTag(var2), AntiVanishModule.hudValue(var2)));
      }

      if (var0.isEmpty()) {
         var0.add(row("anti_vanish", "Vanish", "Clear"));
      }
   }

   private static RiptideHudManager.RainbowParams buildRainbowParams(String var0, int var1) {
      return new RiptideHudManager.RainbowParams(
         var0,
         rainbowPhase(cachedRainbowSpeed()),
         (float)doubleSetting("active_modules", "rainbow-spread", 0.035),
         Math.min(0.35F, (float)doubleSetting("active_modules", "rainbow-saturation", 0.35)),
         (float)doubleSetting("active_modules", "rainbow-brightness", 1.0),
         color("active_modules", "flat-color", -50373),
         color("active_modules", "gradient-start-color", -50373),
         color("active_modules", "gradient-end-color", -10538),
         color("active_modules", "value-color", -791321),
         Math.max(1.0, var1 - 1.0)
      );
   }

   private static double cachedRainbowSpeed() {
      if (cachedRainbowSpeedRev != hudSettingsRevision) {
         cachedRainbowSpeed = doubleSetting("active_modules", "rainbow-speed", 1.0)
            * ("Reverse".equals(setting("active_modules", "rainbow-direction")) ? -1.0 : 1.0);
         cachedRainbowSpeedRev = hudSettingsRevision;
      }

      return cachedRainbowSpeed;
   }

   private static float rainbowPhase(double var0) {
      if (var0 <= 0.0) {
         return 0.0F;
      } else {
         double var2 = 1000.0 / (0.0525 * var0);
         long var4 = Math.max(1L, (long)var2);
         return (float)((double)(System.currentTimeMillis() % var4) / var4);
      }
   }

   private static int activeModuleColor(Module var0, int var1, RiptideHudManager.RainbowParams var2) {
      if ("Flat".equals(var2.mode())) {
         return var2.flatColor();
      } else if ("Random".equals(var2.mode())) {
         return 0xFF000000 | var0.id().hashCode() & 16777215;
      } else if ("Gradient".equals(var2.mode())) {
         double var9 = var2.spread() * 3.0;
         double var5 = (var2.basePhase() + var1 * var9 + var1 / var2.gradientRows()) % 1.0;
         double var7 = var5 < 0.5 ? var5 * 2.0 : (1.0 - var5) * 2.0;
         return lerpColor(var2.gradientStart(), var2.gradientEnd(), var7);
      } else {
         float var3 = (var2.basePhase() + var1 * var2.spread()) % 1.0F;
         int var4 = Color.HSBtoRGB(var3, var2.saturation(), var2.brightness());
         return softenColor(-402653184 | var4 & 16777215, var2.valueColor(), 0.55);
      }
   }

   private static String moduleLinePlain(Module var0, boolean var1, boolean var2) {
      StringBuilder var3 = new StringBuilder(var0.name());
      String var4 = var0.info();
      if (var1 && var4 != null && !var4.isBlank()) {
         var3.append(' ').append(var4);
      }

      if (var2 && var0.keybind() != -1) {
         var3.append(" [").append(RiptideBindUtil.getBindName(var0.keybind())).append(']');
      }

      return var3.toString();
   }

   private static int modulePlainWidth(Module var0, boolean var1, boolean var2) {
      return UiText.width(MC.font, moduleLinePlain(var0, var1, var2), THEME.fontFor(UiTone.BODY), color("active_modules", "text-color", -791321));
   }

   private static RiptideHudManager.HudLine oppositeCoordsLine() {
      if (MC.level != null && MC.player != null) {
         Identifier var0 = MC.level.dimension().identifier();
         String var1 = var0.getPath();
         Vec3 var2 = viewFootPos();
         double[] var3 = RiptideFakeCoords.apply(var2.x, var2.y, var2.z);
         double var4 = var3[0];
         double var6 = var3[1];
         double var8 = var3[2];
         if ("overworld".equals(var1)) {
            return row("nether_coords", "Nether", blockPositionText(var4 / 8.0, var6, var8 / 8.0));
         } else {
            return "the_nether".equals(var1)
               ? row("nether_coords", "Overworld", blockPositionText(var4 * 8.0, var6, var8 * 8.0))
               : row("nether_coords", "Opposite", "N/A");
         }
      } else {
         return row("nether_coords", "Opposite", "N/A");
      }
   }

   private static void renderElement(
      GuiGraphicsExtractor var0,
      Font var1,
      String var2,
      RiptideConfig.HudElementState var3,
      RiptideHudManager.CachedHudElement var4,
      boolean var5,
      boolean var6,
      boolean var7,
      int var8
   ) {
      float var9 = 1.0F;
      RiptideHudManager.HudLayout var10 = var4.layout();
      int var11 = var10.x();
      int var12 = var10.y() + var8;
      int var13 = var10.unscaledWidth();
      int var14 = var10.unscaledHeight();
      if ("active_modules".equals(var2)) {
         renderActiveModuleRows(var0, var1, var2, var4, var5, var6, var7, var9);
      } else {
         if ("spotify".equals(var2) && !var5 && !RiptideLiteVariant.enabled()) {
            RiptideSpotify.setWanted();
            RiptideSpotify.Snapshot var15 = RiptideSpotify.snapshot();
            if (!spotifyHasTrack(var15)) {
               return;
            }
         }

         RiptideHudManager.HudStyle var27 = var4.style();
         boolean var16 = ("armor".equals(var2) || "inventory".equals(var2)) && flatSlots(var2);
         boolean var17 = var27.background() && !var16;
         RiptideHudManager.VisualRect var18 = new RiptideHudManager.VisualRect(var2, var11, var12, var13, var14);
         RiptideHudManager.VisualRect var19 = visualChromeRect(var18, var27);
         boolean var20 = var27.outline() && !var16 && !var5 && !var6 && !var7;
         boolean var21 = var17 && !"compass".equals(var2) || var20;
         List var22 = var21 ? mergeBlockers(var1, var2, var19) : null;
         if (var17) {
            if ("compass".equals(var2)) {
               drawCompassBackground(var0, var19.x(), var19.y(), var19.width(), var19.height(), var9, var27.backgroundColor());
            } else {
               drawMergedBackground(var0, var1, var2, var19, alphaColor(var27.backgroundColor(), var9), var22);
            }
         } else if (var5) {
            UiText.fill(var0, var19.x(), var19.y(), var19.right(), var19.bottom(), editorWash(var6));
         }

         if (var27.outline() && !var16 || var5 || var6 || var7) {
            int var23 = var6 ? color(var2, "accent-color", -50373) : (var5 ? color(var2, "outline-color", -1426113733) : var27.outlineColor());
            int var24 = alphaColor(var23, var9);
            if (!var5 && !var6 && !var7) {
               outlineMergedRect(var0, var19, var22, var24, var27.outlineWidth());
            } else {
               outline(var0, var19.x(), var19.y(), var19.width(), var19.height(), var24, var27.outlineWidth());
            }
         }

         int var28 = var27.padding();
         if ("watermark".equals(var2)) {
            renderLogo(var0, var11 + var28, var12 + var28, logoWidth(var2), logoHeight(logoWidth(var2)), var9);
         } else if ("armor".equals(var2)) {
            renderArmor(var0, var11 + var28, var12 + var28, var9, var27);
         } else if ("inventory".equals(var2)) {
            renderInventory(var0, var11 + var28, var12 + var28, var9, var27);
         } else if ("compass".equals(var2)) {
            renderCompass(var0, var1, var2, var11 + var28, var12 + var28, var13 - var28 * 2, var14 - var28 * 2, var9, var27);
         } else if ("spotify".equals(var2)) {
            renderSpotify(var0, var1, var2, var11 + var28, var12 + var28, var13 - var28 * 2, var14, var9, var27, var5);
         } else if ("keystrokes".equals(var2)) {
            renderKeystrokes(var0, var1, var2, var11 + var28, var12 + var28, var13 - var28 * 2, var14 - var28 * 2, var9);
         } else if (HudElements.isAddon(var2)) {
            HudElementProvider var29 = HudElements.get(var2);

            try {
               var29.render(var0, var1, var11 + var28, var12 + var28, var9);
            } catch (Exception var26) {
               riptide.RiptideClientAddon.LOG.warn("[Hud] Addon element '{}' render failed", var2, var26);
            }
         } else {
            renderTextLines(var0, var1, var2, var3, var4, var11, var12, var13, var9);
         }
      }
   }

   private static void renderActiveModuleRows(
      GuiGraphicsExtractor var0, Font var1, String var2, RiptideHudManager.CachedHudElement var3, boolean var4, boolean var5, boolean var6, float var7
   ) {
      List var8 = var3.lines();
      List var9 = activeModuleContentRects(var2, var3);
      RiptideHudManager.HudStyle var10 = var3.style();
      int var11 = var10.padding();
      boolean var12 = var10.background();
      boolean var13 = var10.outline() || var4 || var5 || var6;
      boolean var14 = var10.shadow();
      int var15 = alphaColor(var10.backgroundColor(), var7);
      int var16 = editorWash(var5);
      int var17 = var5 ? color(var2, "accent-color", -50373) : (var4 ? color(var2, "outline-color", -1426113733) : var10.outlineColor());
      int var18 = alphaColor(var17, var7);
      int var19 = var10.outlineWidth();
      int var20 = var10.verticalPadding();

      for (int var21 = 0; var21 < var8.size() && var21 < var9.size(); var21++) {
         RiptideHudManager.VisualRect var22 = (RiptideHudManager.VisualRect)var9.get(var21);
         RiptideHudManager.VisualRect var23 = visualChromeRect(var22, var10);
         if (!occluded(var23)) {
            List var24 = !var12 && !var13 ? null : mergeBlockers(var1, var2, var23);
            if (var12) {
               drawMergedBackground(var0, var1, var2, var23, var15, var24);
            } else if (var4) {
               UiText.fill(var0, var23.x(), var23.y(), var23.right(), var23.bottom(), var16);
            }

            if (var13) {
               outlineMergedRect(var0, var23, var24, var18, var19);
            }

            int var25 = var21 < var3.widths().size() ? var3.widths().get(var21) : -1;
            renderLineSegments(
               var0,
               var1,
               (RiptideHudManager.HudLine)var8.get(var21),
               var22.x() + var11,
               var22.y() + var20,
               Math.max(1, var22.width() - var11 * 2),
               var7,
               var14,
               var25
            );
         }
      }
   }

   private static void renderLogo(GuiGraphicsExtractor var0, int var1, int var2, int var3, int var4, float var5) {
      if (!RiptideSvgHudLogo.render(var0, var1, var2, var3, var4, var5)) {
         var0.blit(
            RenderPipelines.GUI_TEXTURED,
            RiptideThemeTextures.recolored(HUD_LOGO, RiptideTheme.Channel.ACCENT),
            var1,
            var2,
            0.0F,
            0.0F,
            var3,
            var4,
            552,
            52,
            552,
            52,
            ARGB.white(var5)
         );
      }
   }

   private static void renderTextLines(
      GuiGraphicsExtractor var0,
      Font var1,
      String var2,
      RiptideConfig.HudElementState var3,
      RiptideHudManager.CachedHudElement var4,
      int var5,
      int var6,
      int var7,
      float var8
   ) {
      List var9 = var4.lines();
      RiptideHudManager.HudStyle var10 = var4.style();
      int var11 = var10.padding();
      int var12 = var6 + var10.verticalPadding();
      int var13 = var10.lineHeight();
      String var14 = var10.alignment();
      boolean var15 = var10.shadow();
      int var16 = Math.max(1, var7 - var11 * 2);

      for (int var17 = 0; var17 < var9.size(); var17++) {
         RiptideHudManager.HudLine var18 = (RiptideHudManager.HudLine)var9.get(var17);
         int var19 = var17 < var4.widths().size() ? var4.widths().get(var17) : lineWidth(var1, var18);
         int var20 = var5 + var11;
         if ("Center".equals(var14)) {
            var20 = var5 + Math.max(var11, (var7 - var19) / 2);
         } else if ("Right".equals(var14)) {
            var20 = var5 + Math.max(var11, var7 - var11 - var19);
         }

         renderLineSegments(var0, var1, var18, var20, var12, var16, var8, var15, var19);
         var12 += var13;
      }
   }

   private static void renderLineSegments(
      GuiGraphicsExtractor var0, Font var1, RiptideHudManager.HudLine var2, int var3, int var4, int var5, float var6, boolean var7, int var8
   ) {
      int var9 = var3;
      int var10 = 0;
      boolean var11 = var8 >= 0 && var8 <= var5;
      int[] var12 = var11 ? var2.segmentWidths : null;
      boolean var13 = var12 != null && var12.length == var2.segments.size();

      for (int var14 = 0; var14 < var2.segments.size(); var14++) {
         RiptideHudManager.HudSegment var15 = var2.segments.get(var14);
         int var16 = Math.max(1, var5 - var10);
         String var17 = var11 ? var15.text : UiText.trimToWidth(var1, var15.text, var16, THEME.fontFor(UiTone.BODY), var15.color);
         UiText.draw(var0, var1, var17, THEME.fontFor(UiTone.BODY), alphaColor(var15.color, var6), var9, var4, var7);
         int var18 = var13 ? var12[var14] : UiText.width(var1, var17, THEME.fontFor(UiTone.BODY), var15.color);
         var9 += var18;
         var10 += var18;
         if (var10 >= var5) {
            break;
         }
      }
   }

   private static void renderArmor(GuiGraphicsExtractor var0, int var1, int var2, float var3, RiptideHudManager.HudStyle var4) {
      if (MC.player != null) {
         EquipmentSlot[] var5 = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
         boolean var6 = flatSlots("armor");
         if (var6) {
            drawFlatSlotGrid(var0, var1, var2, var5.length, 1, var3, var4);
         }

         for (int var7 = 0; var7 < var5.length; var7++) {
            int var8 = var1 + var7 * 18;
            if (!var6) {
               drawHudSlot(var0, var8, var2);
            }

            ItemStack var9 = MC.player.getItemBySlot(var5[var7]);
            renderHudItem(var0, var9, var8 + 1, var2 + 1);
         }
      }
   }

   private static void renderInventory(GuiGraphicsExtractor var0, int var1, int var2, float var3, RiptideHudManager.HudStyle var4) {
      if (MC.player != null) {
         boolean var5 = flatSlots("inventory");
         if (var5) {
            drawFlatSlotGrid(var0, var1, var2, 9, 3, var3, var4);
         }

         for (int var6 = 0; var6 < 3; var6++) {
            for (int var7 = 0; var7 < 9; var7++) {
               int var8 = var1 + var7 * 18;
               int var9 = var2 + var6 * 18;
               if (!var5) {
                  drawHudSlot(var0, var8, var9);
               }

               ItemStack var10 = MC.player.getInventory().getItem(9 + var6 * 9 + var7);
               renderHudItem(var0, var10, var8 + 1, var9 + 1);
            }
         }
      }
   }

   private static void renderHudItem(GuiGraphicsExtractor var0, ItemStack var1, int var2, int var3) {
      if (!var1.isEmpty() && MC.font != null) {
         var0.item(var1, var2, var3);
         var0.itemDecorations(MC.font, var1, var2, var3);
      }
   }

   private static boolean flatSlots(String var0) {
      return "Flat".equals(setting(var0, "slot-style"));
   }

   private static void drawFlatSlotGrid(GuiGraphicsExtractor var0, int var1, int var2, int var3, int var4, float var5, RiptideHudManager.HudStyle var6) {
      int var7 = var3 * 18;
      int var8 = var4 * 18;
      if (var6.background()) {
         UiText.fill(var0, var1, var2, var1 + var7, var2 + var8, alphaColor(var6.backgroundColor(), var5));
      }

      if (var6.outline()) {
         int var9 = alphaColor(var6.outlineColor(), var5);

         for (int var10 = 0; var10 <= var3; var10++) {
            int var11 = var10 == var3 ? var1 + var7 - 1 : var1 + var10 * 18;
            UiText.fill(var0, var11, var2, var11 + 1, var2 + var8, var9);
         }

         for (int var12 = 0; var12 <= var4; var12++) {
            int var13 = var12 == var4 ? var2 + var8 - 1 : var2 + var12 * 18;
            UiText.fill(var0, var1, var13, var1 + var7, var13 + 1, var9);
         }
      }
   }

   private static void drawHudSlot(GuiGraphicsExtractor var0, int var1, int var2) {
      int var3 = RiptideTheme.recolor(538579477, RiptideTheme.Channel.BACKDROP);
      int var4 = RiptideTheme.recolor(1435577909, RiptideTheme.Channel.OUTLINE);
      int var5 = RiptideTheme.recolor(1427835662, RiptideTheme.Channel.BACKDROP);
      UiText.fill(var0, var1, var2, var1 + 18, var2 + 18, var3);
      UiText.fill(var0, var1, var2, var1 + 18, var2 + 1, var4);
      UiText.fill(var0, var1, var2 + 17, var1 + 18, var2 + 18, var5);
      UiText.fill(var0, var1, var2, var1 + 1, var2 + 18, var4);
      UiText.fill(var0, var1 + 17, var2, var1 + 18, var2 + 18, var5);
   }

   private static void renderCompass(
      GuiGraphicsExtractor var0, Font var1, String var2, int var3, int var4, int var5, int var6, float var7, RiptideHudManager.HudStyle var8
   ) {
      int var9 = Math.max(16, var6);
      int var10 = var3 + var5 / 2;
      UiText.fill(var0, var10, var4 + 2, var10 + 1, var4 + var9 - 2, alphaColor(var8.accentColor(), var7 * 0.88F));
      String[] var11 = COMPASS_LABELS;
      int var12 = UiText.reloadGeneration();
      if (compassWidthsFont != var1 || compassWidthsReloadGen != var12) {
         for (int var13 = 0; var13 < var11.length; var13++) {
            COMPASS_LABEL_WIDTHS[var13] = UiText.width(var1, var11[var13], THEME.fontFor(UiTone.BODY), -1);
         }

         compassWidthsFont = var1;
         compassWidthsReloadGen = var12;
      }

      float var39 = viewYaw();
      double var14 = (var39 % 360.0 + 360.0) % 360.0;
      int var16 = var4 + Math.max(2, (var9 - THEME.fontHeight(UiTone.BODY)) / 2);
      double var17 = 118.0;
      boolean var19 = var8.background();
      boolean var20 = var8.shadow();

      for (int var21 = 0; var21 < var11.length; var21++) {
         double var22 = var21 * 45.0;
         double var24 = wrappedDegrees(var22 - var14);
         double var26 = Math.abs(var24);
         if (!(var26 > var17)) {
            double var28 = 1.0 - var26 / var17;
            var28 = var28 * var28 * (3.0 - 2.0 * var28);
            int var30 = var10 + (int)Math.round(var24 / var17 * (var5 / 2.0 - 8.0));
            double var31 = Math.max(0.0, 1.0 - var26 / 24.0);
            int var33 = lerpColor(var8.labelColor(), var8.accentColor(), var31);
            int var34 = alphaColor(var33, var7 * (0.18F + (float)var28 * 0.82F));
            int var35 = COMPASS_LABEL_WIDTHS[var21];
            int var36 = alphaColor(RiptideTheme.recolor(942148882, RiptideTheme.Channel.BACKDROP), var7 * (float)var31);
            if (var31 > 0.0) {
               int var37 = var19 ? var4 : var4 + 2;
               int var38 = var19 ? var4 + var9 : var4 + var9 - 2;
               UiText.fill(var0, var30 - var35 / 2 - 3, var37, var30 + var35 / 2 + 3, var38, var36);
            }

            UiText.draw(
               var0,
               var1,
               var11[var21],
               THEME.fontFor(UiTone.BODY),
               alphaColor(-872415232, var7 * (0.24F + (float)var28 * 0.36F)),
               var30 - var35 / 2 + 1,
               var16 + 1,
               false
            );
            UiText.draw(var0, var1, var11[var21], THEME.fontFor(UiTone.BODY), var34, var30 - var35 / 2, var16, var20);
         }
      }
   }

   private static boolean spotifyHasTrack(RiptideSpotify.Snapshot var0) {
      return var0 != null
         && (var0.status() == RiptideSpotify.Status.PLAYING || var0.status() == RiptideSpotify.Status.PAUSED)
         && var0.title() != null
         && !var0.title().isBlank();
   }

   private static boolean spotifyPart(String var0, String var1) {
      return boolSetting(var0, var1);
   }

   private static RiptideHudManager.SpotifyTextCache spotifyText(Font var0, RiptideSpotify.Snapshot var1) {
      long var2 = var1 == null ? Long.MIN_VALUE : var1.updatedAtMs();
      int var4 = UiText.reloadGeneration();
      if (spotifyTextCache != null && spotifyTextCacheKey.matches(var2, "", var0, var4)) {
         return spotifyTextCache;
      } else {
         RiptideHudManager.SpotifyTextCache var5 = new RiptideHudManager.SpotifyTextCache();
         var5.title = var1 != null && var1.title() != null ? var1.title().trim() : "";
         var5.artist = var1 != null && var1.artist() != null ? var1.artist().trim() : "";
         var5.titleWidth = UiText.width(var0, var5.title, THEME.fontFor(UiTone.BODY), 0);
         var5.artistWidth = UiText.width(var0, var5.artist, THEME.fontFor(UiTone.BODY), 0);
         spotifyTextCache = var5;
         spotifyTextCacheKey = new RiptideMarquee.CompositionKey(var2, "", var0, var4);
         return var5;
      }
   }

   private static RiptideHudManager.MusicCardGeom spotifyCardGeom(String var0, int var1, int var2, boolean var3, boolean var4) {
      RiptideHudManager.MusicCardGeom var5 = SPOTIFY_GEOM;
      boolean var6 = spotifyPart(var0, "spotify-part-artist");
      boolean var7 = spotifyPart(var0, "spotify-part-time");
      boolean var8 = spotifyPart(var0, "spotify-part-progress");
      int var9 = THEME.fontHeight(UiTone.BODY);
      var5.blockH = Math.max(var4 ? 32 : 0, var6 ? 20 : var9);
      var5.textX = var4 ? 39 : 0;
      boolean var10 = !"Bottom".equals(setting(var0, "spotify-time-position")) && var6;
      int var11 = var7 && var3 ? var2 + 4 : 0;
      var5.artistClip = Math.max(8, var1 - var5.textX - (var10 ? var11 : 0));
      var5.titleClip = Math.max(8, var1 - var5.textX - (var10 ? 0 : var11));
      if (var6) {
         var5.artistY = (var5.blockH - 20) / 2;
         var5.titleY = var5.artistY + 11;
      } else {
         var5.artistY = 0;
         var5.titleY = (var5.blockH - var9) / 2;
      }

      var5.timeY = var10 ? var5.artistY : var5.titleY;
      var5.timeX = var1 - var2;
      int var12 = var5.blockH;
      if (var8) {
         var5.progressY = var12 + 2;
         var5.progressW = var1;
         var12 += 5;
      }

      var5.contentH = var12;
      return var5;
   }

   private static double spotifyPositionSec(RiptideSpotify.Snapshot var0) {
      double var1 = var0.positionSec();
      long var3 = System.currentTimeMillis();
      if (Double.isNaN(spotifyPositionAnchorSec) || var1 != spotifyPositionAnchorSec) {
         spotifyPositionAnchorSec = var1;
         spotifyPositionAnchorAtMs = var3;
      }

      return RiptideMarquee.interpolatePosition(
         spotifyPositionAnchorSec, spotifyPositionAnchorAtMs, var0.status() == RiptideSpotify.Status.PLAYING, var0.durationSec(), var3
      );
   }

   private static double spotifyProgress(RiptideSpotify.Snapshot var0) {
      double var1 = var0.durationSec();
      return var1 <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, spotifyPositionSec(var0) / var1));
   }

   public static RiptideHudManager.SpotifyArt spotifyArt(RiptideSpotify.Snapshot var0) {
      if (var0 != null && var0.artworkPath() != null && !var0.artworkPath().isEmpty()) {
         Identifier var1 = spotifyArtTexture(var0.artworkPath(), var0.updatedAtMs(), var0.artist() + "|" + var0.title());
         return var1 == null ? null : new RiptideHudManager.SpotifyArt(var1, spotifyArtWidth, spotifyArtHeight);
      } else {
         return null;
      }
   }

   public static double spotifyProgressFor(RiptideSpotify.Snapshot var0) {
      return var0 == null ? 0.0 : spotifyProgress(var0);
   }

   private static String spotifyTimeTextCached(RiptideSpotify.Snapshot var0, Font var1, Identifier var2) {
      double var3 = spotifyPositionSec(var0);
      long var5 = (long)var3;
      double var7 = var0.durationSec();
      if (var5 != spotifyTimeCacheSec || var7 != spotifyTimeCacheDuration) {
         spotifyTimeCacheSec = var5;
         spotifyTimeCacheDuration = var7;
         spotifyTimeCacheText = spotifyFormatTime(var3) + " / " + spotifyFormatTime(Math.max(0.0, var7));
         spotifyTimeCacheWidth = UiText.width(var1, spotifyTimeCacheText, var2, 0);
      }

      return spotifyTimeCacheText;
   }

   private static String spotifyFormatTime(double var0) {
      int var2 = Math.max(0, (int)var0);
      int var3 = var2 % 60;
      return var2 / 60 + ":" + (var3 < 10 ? "0" + var3 : Integer.toString(var3));
   }

   private static void requestArtDecode(String var0, long var1, long var3, String var5) {
      RiptideHudManager.ArtDecodeRequest var6 = spotifyArtDecodeRequest;
      if (var6 == null || !var6.path().equals(var0) || var6.mtime() != var1 || var6.size() != var3) {
         spotifyArtDecodeRequest = new RiptideHudManager.ArtDecodeRequest(var0, var1, var3, var5);
         if (spotifyArtDecodeThread == null) {
            spotifyArtDecodeThread = new Thread(RiptideHudManager::artDecodeLoop, "riptide-spotify-art-decode");
            spotifyArtDecodeThread.setDaemon(true);
            spotifyArtDecodeThread.start();
         }
      }
   }

   private static RiptideHudManager.ArtDecodeResult pollArtDecode(String var0, long var1, long var3) {
      synchronized (ART_DECODE_LOCK) {
         RiptideHudManager.ArtDecodeResult var6 = spotifyArtDecodeResult;
         if (var6 == null) {
            return null;
         } else if (var6.request().path().equals(var0) && var6.request().mtime() == var1 && var6.request().size() == var3) {
            spotifyArtDecodeResult = null;
            return var6;
         } else {
            return null;
         }
      }
   }

   private static void artDecodeLoop() {
      while (true) {
         RiptideHudManager.ArtDecodeRequest var0 = spotifyArtDecodeRequest;
         if (var0 == null) {
            try {
               Thread.sleep(50L);
            } catch (InterruptedException var8) {
               Thread.currentThread().interrupt();
               return;
            }
         } else {
            spotifyArtDecodeRequest = null;
            NativeImage[] var1 = null;

            try {
               NativeImage var2 = RiptideImageCodec.decode(Files.readAllBytes(Path.of(var0.path())));
               if (var2 != null) {
                  spotifyRoundCorners(var2, 5);
                  var1 = RiptideImageCodec.mipChain(var2);
               }
            } catch (Throwable var9) {
               var1 = null;
            }

            synchronized (ART_DECODE_LOCK) {
               if (spotifyArtDecodeResult != null && spotifyArtDecodeResult.chain() != null) {
                  for (NativeImage var6 : spotifyArtDecodeResult.chain()) {
                     var6.close();
                  }
               }

               spotifyArtDecodeResult = new RiptideHudManager.ArtDecodeResult(var0, var1);
            }
         }
      }
   }

   private static Identifier spotifyArtTexture(String var0, long var1, String var3) {
      if (var0 == null || var0.isEmpty()) {
         return null;
      } else if (var0.equals(spotifyArtPath) && var1 == spotifyArtStamp && spotifyArtTexture != null) {
         return SPOTIFY_ART_ID;
      } else {
         long var4 = System.currentTimeMillis();
         if (var4 - spotifyArtFailAtMs < 500L) {
            return null;
         } else {
            Path var6 = Path.of(var0);

            BasicFileAttributes var7;
            try {
               var7 = Files.readAttributes(var6, BasicFileAttributes.class);
            } catch (IOException var15) {
               spotifyLogArt("art file not there yet (download in flight?): " + var0);
               spotifyArtFailAtMs = var4;
               return null;
            }

            if (spotifyArtTexture != null && var7.lastModifiedTime().toMillis() == spotifyArtFileMtime && var7.size() == spotifyArtFileSize) {
               spotifyArtPath = var0;
               spotifyArtStamp = var1;
               return SPOTIFY_ART_ID;
            } else {
               RiptideHudManager.ArtDecodeResult var8 = pollArtDecode(var0, var7.lastModifiedTime().toMillis(), var7.size());
               if (var8 == null) {
                  requestArtDecode(var0, var7.lastModifiedTime().toMillis(), var7.size(), var3);
                  return null;
               } else if (var8.chain() == null) {
                  spotifyLogArt("art decode failed for " + var3 + " (" + var0 + ")");
                  spotifyArtFailAtMs = var4;
                  spotifyArtPath = "";
                  return null;
               } else {
                  NativeImage[] var9 = var8.chain();

                  try {
                     int var10 = var9[0].getWidth();
                     int var17 = var9[0].getHeight();
                     if (spotifyArtTexture != null && var10 == spotifyArtWidth && var17 == spotifyArtHeight) {
                        spotifyArtTexture.update(var9);
                     } else {
                        spotifyArtTexture = new RiptideHudManager.SpotifyArtTexture(var9);
                     }

                     var9 = null;
                     MC.getTextureManager().register(SPOTIFY_ART_ID, spotifyArtTexture);
                     spotifyArtPath = var0;
                     spotifyArtStamp = var1;
                     spotifyArtWidth = var10;
                     spotifyArtHeight = var17;
                     spotifyArtFileMtime = var7.lastModifiedTime().toMillis();
                     spotifyArtFileSize = var7.size();
                     return SPOTIFY_ART_ID;
                  } catch (Exception var16) {
                     if (var9 != null) {
                        for (NativeImage var14 : var9) {
                           var14.close();
                        }
                     }

                     spotifyLogArt("art upload failed on " + var0 + ": " + var16);
                     spotifyArtFailAtMs = var4;
                     spotifyArtPath = "";
                     return null;
                  }
               }
            }
         }
      }
   }

   private static void spotifyLogArt(String var0) {
   }

   private static void spotifyRoundCorners(NativeImage var0, int var1) {
      int var2 = var0.getWidth();
      int var3 = var0.getHeight();
      int var4 = Math.max(1, Math.min(var1, Math.min(var2, var3) / 2));

      for (int var5 = 0; var5 < var4; var5++) {
         for (int var6 = 0; var6 < var4; var6++) {
            double var7 = Math.sqrt((var6 + 0.5 - var4) * (var6 + 0.5 - var4) + (var5 + 0.5 - var4) * (var5 + 0.5 - var4));
            if (!(var7 <= var4 - 1.0)) {
               float var9 = (float)Math.max(0.0, Math.min(1.0, var4 - var7));
               spotifyMaskCorner(var0, var6, var5, var2, var3, var9);
            }
         }
      }
   }

   private static void spotifyMaskCorner(NativeImage var0, int var1, int var2, int var3, int var4, float var5) {
      int[][] var6 = new int[][]{{var1, var2}, {var3 - 1 - var1, var2}, {var1, var4 - 1 - var2}, {var3 - 1 - var1, var4 - 1 - var2}};

      for (int[] var10 : var6) {
         int var11 = var0.getPixel(var10[0], var10[1]);
         int var12 = var11 >>> 24 & 0xFF;
         var0.setPixel(var10[0], var10[1], var11 & 16777215 | (int)(var12 * var5) << 24);
      }
   }

   private static void renderSpotify(
      GuiGraphicsExtractor var0, Font var1, String var2, int var3, int var4, int var5, int var6, float var7, RiptideHudManager.HudStyle var8, boolean var9
   ) {
      if (!RiptideLiteVariant.enabled()) {
         syncSpotifySource(var2);
         RiptideSpotify.setWanted();
         RiptideSpotify.Snapshot var10 = RiptideSpotify.snapshot();
         boolean var11 = spotifyHasTrack(var10);
         if (var11 || var9) {
            Identifier var12 = THEME.fontFor(UiTone.BODY);
            boolean var13 = spotifyPart(var2, "spotify-part-art");
            boolean var14 = spotifyPart(var2, "spotify-part-artist");
            boolean var15 = spotifyPart(var2, "spotify-part-time");
            boolean var16 = spotifyPart(var2, "spotify-part-progress");
            Identifier var17 = var11 && var13 ? spotifyArtTexture(var10.artworkPath(), var10.updatedAtMs(), var10.artist() + "|" + var10.title()) : null;
            boolean var18 = var17 != null;
            if (var18 != spotifyLastArtLoaded) {
               spotifyLastArtLoaded = var18;
               HUD_CACHE.remove("spotify");
            }

            int var19 = padding(var2) * 2 + 32 + (var16 ? 5 : 0);
            boolean var20 = var13 && (!var11 || var18) && var6 >= var19;
            if (!spotifyRenderLogged && !var9 && var11) {
            }

            String var21 = var11 && var15 ? spotifyTimeTextCached(var10, var1, var12) : "";
            int var22 = var21.isEmpty() ? 0 : spotifyTimeCacheWidth;
            RiptideHudManager.MusicCardGeom var23 = spotifyCardGeom(var2, var5, var22, var11, var20);
            String var24 = spotifyColorMode(var2);
            int var25;
            int var26;
            int var27;
            int var28;
            if ("Theme".equals(var24)) {
               var25 = themedColor(var2, "spotify-artist-color", -4743522);
               var26 = themedColor(var2, "spotify-title-color", -791321);
               var27 = themedColor(var2, "spotify-progress-color", -50373);
               var28 = themedColor(var2, "spotify-time-color", -4743522);
            } else if ("Rainbow".equals(var24)) {
               float var29 = rainbowPhase(
                  doubleSetting(var2, "rainbow-speed", 1.0) * ("Reverse".equals(setting(var2, "spotify-rainbow-direction")) ? -1.0 : 1.0)
               );
               float var30 = (float)doubleSetting(var2, "rainbow-spread", 0.035);
               float var31 = Math.min(0.35F, (float)doubleSetting(var2, "rainbow-saturation", 0.35));
               float var32 = Math.min(1.0F, (float)doubleSetting(var2, "rainbow-brightness", 1.0));
               int var33 = color(var2, "value-color", -791321);
               var25 = spotifyPartColor(var2, "spotify-rainbow-artist", "true", var29, 0.0F, var31, var32, var33, "spotify-artist-color", -4743522);
               var26 = spotifyPartColor(var2, "spotify-rainbow-title", "true", var29, var30, var31, var32, var33, "spotify-title-color", -791321);
               var27 = spotifyPartColor(var2, "spotify-rainbow-progress", "true", var29, var30 * 2.0F, var31, var32, var33, "spotify-progress-color", -50373);
               var28 = spotifyPartColor(var2, "spotify-rainbow-time", "false", var29, var30 * 3.0F, var31, var32, var33, "spotify-time-color", -4743522);
            } else {
               var25 = customColor(var2, "spotify-artist-color", -4743522);
               var26 = customColor(var2, "spotify-title-color", -791321);
               var27 = customColor(var2, "spotify-progress-color", -50373);
               var28 = customColor(var2, "spotify-time-color", -4743522);
            }

            var28 = alphaColor(var28, var7);
            if (var20) {
               int var38 = var4 + (var23.blockH - 32) / 2;
               if (var17 != null) {
                  var0.blit(
                     RenderPipelines.GUI_TEXTURED,
                     var17,
                     var3,
                     var38,
                     0.0F,
                     0.0F,
                     32,
                     32,
                     spotifyArtWidth,
                     spotifyArtHeight,
                     spotifyArtWidth,
                     spotifyArtHeight,
                     ARGB.white(var7)
                  );
               } else {
                  int var40 = alphaColor(color(var2, "label-color", -4743522), var7);
                  UiText.fill(var0, var3, var38, var3 + 32, var38 + 32, alphaColor(var40, 0.22F));
               }
            }

            RiptideHudManager.SpotifyTextCache var39 = spotifyText(var1, var10);
            long var41 = System.currentTimeMillis();
            long var42 = var11 ? var10.updatedAtMs() + 1200L : 0L;
            int var34 = spotifyScrollSpeed(var2);
            if (var14) {
               RiptideMarquee.drawMarquee(
                  var0,
                  var1,
                  var11 ? var39.artist : "",
                  var12,
                  alphaColor(var25, var7),
                  var3 + var23.textX,
                  var4 + var23.artistY,
                  var23.artistClip,
                  var8.shadow(),
                  var41,
                  var42,
                  var34,
                  var11 ? var39.artistWidth : 0
               );
            }

            String var35 = var11 ? var39.title : "Spotify";
            RiptideMarquee.drawMarquee(
               var0,
               var1,
               var35,
               var12,
               alphaColor(var26, var7),
               var3 + var23.textX,
               var4 + var23.titleY,
               var23.titleClip,
               var8.shadow(),
               var41,
               var42,
               var34,
               var11 ? var39.titleWidth : UiText.width(var1, var35, var12, 0)
            );
            if (!var21.isEmpty()) {
               UiText.draw(var0, var1, var21, var12, var28, var3 + var23.timeX, var4 + var23.timeY, var8.shadow());
            }

            if (var16) {
               UiText.fill(var0, var3, var4 + var23.progressY, var3 + var23.progressW, var4 + var23.progressY + 3, alphaColor(var27, var7 * 0.22F));
               int var36 = var11 ? Math.round(var23.progressW * (float)spotifyProgress(var10)) : 0;
               if (var36 > 0) {
                  UiText.fill(var0, var3, var4 + var23.progressY, var3 + var36, var4 + var23.progressY + 3, alphaColor(var27, var7 * 0.9F));
               }
            }
         }
      }
   }

   private static boolean spotifyInteractive(Screen var0) {
      if (var0 instanceof RiptideHudEditorScreen) {
         return false;
      } else if (MC.player == null) {
         return false;
      } else if (state("spotify").enabled && (RiptideLiteVariant.enabled() || spotifyHasTrack(RiptideSpotify.snapshot()))) {
         Module var1 = ModuleRegistry.get("hud");
         return var1 != null && shouldRenderInGame(var0, var1);
      } else {
         return false;
      }
   }

   public static boolean musicDisplayMouseClicked(int var0, int var1, Screen var2) {
      if (!spotifyInteractive(var2)) {
         return false;
      } else {
         RiptideHudManager.HudLayout var3 = layout("spotify", MC.font);
         int var4 = padding("spotify");
         int var5 = var3.x() + var4;
         int var6 = var3.y() + var4;
         int var7 = var3.unscaledWidth() - var4 * 2;
         int var8 = var3.unscaledHeight() - var4 * 2;
         if (var0 >= var5 && var0 < var5 + var7 && var1 >= var6 && var1 < var6 + var8) {
            spotifyMoveDragging = true;
            spotifyDragScreen = var2;
            spotifyMoveGrabX = var0 - var3.x();
            spotifyMoveGrabY = var1 - var3.y();
            return true;
         } else {
            return false;
         }
      }
   }

   public static boolean musicDisplayMouseDragged(int var0, int var1, Screen var2) {
      if (var2 != spotifyDragScreen) {
         spotifyMoveDragging = false;
         spotifyDragScreen = null;
         return false;
      } else if (spotifyMoveDragging) {
         move("spotify", var0 - spotifyMoveGrabX, var1 - spotifyMoveGrabY, anchorScreenWidth(), anchorScreenHeight());
         return true;
      } else {
         return false;
      }
   }

   public static boolean musicDisplayMouseReleased(Screen var0) {
      if (var0 != spotifyDragScreen) {
         return false;
      } else if (!spotifyMoveDragging) {
         return false;
      } else {
         spotifyMoveDragging = false;
         spotifyDragScreen = null;
         return true;
      }
   }

   private static int spotifyRainbowColor(float var0, float var1, float var2, float var3, int var4) {
      float var5 = (var0 + var1) % 1.0F;
      int var6 = Color.HSBtoRGB(var5, var2, var3);
      return softenColor(-402653184 | var6 & 16777215, var4, 0.55);
   }

   private static int keystrokeUnit(String var0) {
      return clamp(intSetting(var0, "keystroke-size", 18), 12, 40);
   }

   private static int keystrokeKeyHeight(int var0) {
      return Math.max(10, Math.round(var0 * 0.86F));
   }

   private static boolean keystrokesShowSpace(String var0) {
      return boolSetting(var0, "keystroke-show-space");
   }

   private static boolean keystrokesShowMouse(String var0) {
      return boolSetting(var0, "keystroke-show-mouse");
   }

   private static void renderKeystrokes(GuiGraphicsExtractor var0, Font var1, String var2, int var3, int var4, int var5, int var6, float var7) {
      int var8 = keystrokeUnit(var2);
      byte var9 = 2;
      int var10 = color(var2, "keystroke-active-color", -50373);
      int var11 = color(var2, "keystroke-idle-color", -1274015724);
      int var12 = color(var2, "keystroke-text-color", -1);
      boolean var13 = keystrokesShowMouse(var2);
      long var14 = System.nanoTime();
      float var16 = ksAnimNanos == 0L ? 0.0F : (float)Math.min(0.1, (var14 - ksAnimNanos) / 1.0E9);
      ksAnimNanos = var14;
      float var17 = 1.0F - (float)Math.exp(-var16 * 16.0);
      boolean[] var18 = keystrokePressed();

      for (int var19 = 0; var19 < KS_FILL.length; var19++) {
         float var20 = var18[var19] ? 1.0F : 0.0F;
         KS_FILL[var19] = KS_FILL[var19] + (var20 - KS_FILL[var19]) * var17;
         if (Math.abs(var20 - KS_FILL[var19]) < 0.003F) {
            KS_FILL[var19] = var20;
         }
      }

      int var24 = keystrokeKeyHeight(var8);
      int var25 = 3 * var8 + 2 * var9;
      drawKey(var0, var1, var3 + (var25 - var8) / 2, var4, var8, var24, "W", KS_FILL[0], var11, var10, var12, var7);
      int var21 = var4 + var24 + var9;
      drawKey(var0, var1, var3, var21, var8, var24, "A", KS_FILL[1], var11, var10, var12, var7);
      drawKey(var0, var1, var3 + var8 + var9, var21, var8, var24, "S", KS_FILL[2], var11, var10, var12, var7);
      drawKey(var0, var1, var3 + 2 * (var8 + var9), var21, var8, var24, "D", KS_FILL[3], var11, var10, var12, var7);
      int var22 = var21 + var24 + var9;
      if (keystrokesShowSpace(var2)) {
         drawSpaceKey(var0, var1, var3, var22, var25, var24, KS_FILL[6], var11, var10, var12, var7);
         var22 += var24 + var9;
      }

      if (var13) {
         int var23 = (var25 - var9) / 2;
         drawKey(var0, var1, var3, var22, var23, var24, Integer.toString(RiptideCpsTracker.leftCps()), KS_FILL[4], var11, var10, var12, var7);
         drawKey(
            var0,
            var1,
            var3 + var23 + var9,
            var22,
            var25 - var23 - var9,
            var24,
            Integer.toString(RiptideCpsTracker.rightCps()),
            KS_FILL[5],
            var11,
            var10,
            var12,
            var7
         );
      }
   }

   private static boolean[] keystrokePressed() {
      Options var0 = MC == null ? null : MC.options;
      return new boolean[]{
         keyHeld(var0 == null ? null : var0.keyUp),
         keyHeld(var0 == null ? null : var0.keyLeft),
         keyHeld(var0 == null ? null : var0.keyDown),
         keyHeld(var0 == null ? null : var0.keyRight),
         keyHeld(var0 == null ? null : var0.keyAttack) || RiptideCpsTracker.leftActiveRecently(120L),
         keyHeld(var0 == null ? null : var0.keyUse) || RiptideCpsTracker.rightActiveRecently(120L),
         keyHeld(var0 == null ? null : var0.keyJump)
      };
   }

   private static boolean keyHeld(KeyMapping var0) {
      return var0 != null && RiptideKeyMappingBridge.of(var0).riptide$isActuallyDown();
   }

   private static void drawSpaceKey(
      GuiGraphicsExtractor var0, Font var1, int var2, int var3, int var4, int var5, float var6, int var7, int var8, int var9, float var10
   ) {
      drawKey(var0, var1, var2, var3, var4, var5, "", var6, var7, var8, var9, var10);
      int var11 = Math.max(6, Math.round(var4 * 0.45F));
      int var12 = Math.max(2, var5 / 7);
      int var13 = var2 + (var4 - var11) / 2;
      int var14 = var3 + (var5 - var12) / 2;
      UiText.fill(var0, var13, var14, var13 + var11, var14 + var12, alphaColor(var9, var10));
   }

   private static void drawKey(
      GuiGraphicsExtractor var0, Font var1, int var2, int var3, int var4, int var5, String var6, float var7, int var8, int var9, int var10, float var11
   ) {
      if (var4 > 0 && var5 > 0) {
         UiText.fill(var0, var2, var3, var2 + var4, var3 + var5, alphaColor(var8, var11));
         if (var7 > 0.003F) {
            float var12 = var7 * var7 * (3.0F - 2.0F * var7);
            int var13 = alphaColor(var9, var11 * (0.6F + 0.4F * var12));
            if (var12 >= 0.999F) {
               UiText.fill(var0, var2, var3, var2 + var4, var3 + var5, var13);
            } else {
               double var14 = var2 + var4 / 2.0;
               double var16 = var3 + var5 / 2.0;
               double var18 = Math.sqrt(var4 / 2.0 * (var4 / 2.0) + var5 / 2.0 * (var5 / 2.0));
               double var20 = var12 * var18;
               int var22 = Math.max(var3, (int)Math.floor(var16 - var20));
               int var23 = Math.min(var3 + var5, (int)Math.ceil(var16 + var20));

               for (int var24 = var22; var24 < var23; var24++) {
                  double var25 = var24 + 0.5 - var16;
                  double var27 = var20 * var20 - var25 * var25;
                  if (!(var27 <= 0.0)) {
                     double var29 = Math.sqrt(var27);
                     int var31 = Math.max(var2, (int)Math.round(var14 - var29));
                     int var32 = Math.min(var2 + var4, (int)Math.round(var14 + var29));
                     if (var32 > var31) {
                        UiText.fill(var0, var31, var24, var32, var24 + 1, var13);
                     }
                  }
               }
            }
         }

         outline(var0, var2, var3, var4, var5, alphaColor(1711276032, var11), 1);
         Identifier var33 = THEME.fontFor(UiTone.BODY);
         int var34 = UiText.width(var1, var6, var33, var10);
         int var35 = THEME.fontHeight(UiTone.BODY);
         int var15 = var2 + (var4 - var34) / 2;
         int var36 = var3 + (var5 - var35) / 2 + 1;
         UiText.draw(var0, var1, var6, var33, alphaColor(-872415232, var11 * 0.55F), var15 + 1, var36 + 1, false);
         UiText.draw(var0, var1, var6, var33, alphaColor(var10, var11), var15, var36, false);
      }
   }

   private static void drawCompassBackground(GuiGraphicsExtractor var0, int var1, int var2, int var3, int var4, float var5, int var6) {
      if (var3 > 0 && var4 > 0 && !(var5 <= 0.001F)) {
         int var7 = (int)((var6 >>> 24 & 0xFF) * Math.max(0.0F, Math.min(1.0F, var5)));
         int var8 = var6 & 16777215;
         int var9 = Math.max(1, var3 / 2);

         for (int var10 = 0; var10 < var3; var10++) {
            double var11 = Math.abs(var10 + 0.5 - var3 / 2.0) / var9;
            double var13 = 1.0 - Math.min(1.0, var11);
            var13 = var13 * var13 * (3.0 - 2.0 * var13);
            int var15 = (int)Math.round(var7 * var13);
            if (var15 > 0) {
               UiText.fill(var0, var1 + var10, var2, var1 + var10 + 1, var2 + var4, var15 << 24 | var8);
            }
         }
      }
   }

   private static double wrappedDegrees(double var0) {
      double var2 = var0 % 360.0;
      if (var2 >= 180.0) {
         var2 -= 360.0;
      }

      if (var2 < -180.0) {
         var2 += 360.0;
      }

      return var2;
   }

   private static RiptideHudManager.HudLine row(String var0, String var1, String var2) {
      RiptideHudManager.HudLine var3 = new RiptideHudManager.HudLine();
      var3.add(var1 + ": ", color(var0, "label-color", -4743522));
      var3.add(var2 != null && !var2.isBlank() ? var2 : "N/A", color(var0, "value-color", -791321));
      return var3;
   }

   private static RiptideHudManager.HudLine metricsLine() {
      RiptideHudManager.HudLine var0 = new RiptideHudManager.HudLine();
      int var1 = color("fps", "label-color", -4743522);
      int var2 = color("fps", "value-color", -791321);
      int var3 = alphaColor(var1, 0.72F);
      var0.add("FPS: ", var1);
      var0.add(Integer.toString(MC.getFps()), var2);
      var0.add(" ", var3);
      var0.add("TPS: ", var1);
      var0.add(String.format(Locale.ROOT, "%.1f", ServerTickTracker.getEstimatedTps()), var2);
      var0.add(" ", var3);
      var0.add("Ping: ", var1);
      var0.add(ServerTickTracker.getPingMs() + " ms", var2);
      return var0;
   }

   private static String gameMode() {
      if (MC.gameMode == null) {
         return "N/A";
      } else {
         try {
            String var0 = MC.gameMode.getPlayerMode().getName();
            return var0 != null && !var0.isBlank() ? RiptideRegistryLabels.identifier(var0) : "N/A";
         } catch (Throwable var1) {
            return MC.gameMode.toString();
         }
      }
   }

   private static String durability() {
      ItemStack var0 = MC.player == null ? ItemStack.EMPTY : MC.player.getMainHandItem();
      if (var0.isEmpty()) {
         return "Empty";
      } else if (!var0.isDamageableItem()) {
         return "No durability";
      } else {
         int var1 = Math.max(0, var0.getMaxDamage() - var0.getDamageValue());
         int var2 = Math.max(1, var0.getMaxDamage());
         return String.format(Locale.ROOT, "%d / %d (%d%%)", var1, var2, Math.round(var1 * 100.0F / var2));
      }
   }

   private static RiptideHudManager.HudLine memoryLine() {
      Runtime var0 = Runtime.getRuntime();
      long var1 = var0.totalMemory() - var0.freeMemory() >> 20;
      long var3 = var0.maxMemory() >> 20;
      boolean var5 = boolSetting("memory", "show-bar");
      boolean var6 = boolSetting("memory", "show-percent");
      String var7 = setting("memory", "memory-format");
      long var8 = var3 > 0L ? var1 * 100L / var3 : 0L;
      RiptideHudManager.HudLine var10 = new RiptideHudManager.HudLine();
      var10.add("Memory: ", color("memory", "label-color", -4743522));
      boolean var11 = !"used/max".equals(var7);
      if (var11) {
         var10.add(var8 + "%", color("memory", "value-color", -791321));
      } else {
         var10.add(var1 + " / " + var3 + " MB", color("memory", "value-color", -791321));
      }

      if (var6 && !var11) {
         var10.add(" (" + var8 + "%)", color("memory", "value-color", -791321));
      }

      if (var5) {
         int var12 = var8 > 85L ? -50373 : (var8 > 60L ? -10752 : -12588930);
         int var13 = Math.min(10, Math.max(1, (int)(var8 * 10L / 100L)));
         var10.add(" [", alphaColor(color("memory", "label-color", -4743522), 0.5F));
         StringBuilder var14 = new StringBuilder();

         for (int var15 = 0; var15 < var13; var15++) {
            var14.append('|');
         }

         for (int var16 = var13; var16 < 10; var16++) {
            var14.append('.');
         }

         var10.add(var14.toString(), var12);
         var10.add("]", alphaColor(color("memory", "label-color", -4743522), 0.5F));
      }

      return var10;
   }

   private static RiptideHudManager.HudLine serverIpLine() {
      RiptideHudManager.HudLine var0 = new RiptideHudManager.HudLine();
      var0.add("IP: ", color("server_ip", "label-color", -4743522));
      if (MC.getCurrentServer() != null && MC.getCurrentServer().ip != null && !MC.getCurrentServer().ip.isBlank()) {
         String var1 = MC.getCurrentServer().ip;
         if (boolSetting("server_ip", "show-port")
            && MC.getConnection() != null
            && MC.getConnection().getConnection().getRemoteAddress() instanceof InetSocketAddress var2) {
            var1 = var1.contains(":") ? var1 : var1 + ":" + var2.getPort();
         }

         var0.add(var1, color("server_ip", "value-color", -791321));
      } else {
         var0.add(MC.hasSingleplayerServer() ? "Singleplayer" : "N/A", color("server_ip", "value-color", -791321));
      }

      return var0;
   }

   private static String serverBrand() {
      if (MC.getConnection() == null) {
         return "N/A";
      } else {
         try {
            String var0 = MC.getConnection().serverBrand();
            return var0 != null && !var0.isBlank() ? var0 : "Unknown";
         } catch (Throwable var1) {
            return "Unknown";
         }
      }
   }

   private static RiptideHudManager.HudLine fpsGraphLine() {
      int var0 = MC.getFps();
      fpsSamples[fpsSampleIndex] = var0;
      fpsSampleIndex = (fpsSampleIndex + 1) % 200;
      if (fpsSampleCount < 200) {
         fpsSampleCount++;
      }

      int var1 = intSetting("fps_graph", "graph-samples", 100);
      var1 = Math.max(10, Math.min(200, var1));
      int var2 = Math.min(var1, fpsSampleCount);
      if (var2 <= 0) {
         return row("fps_graph", "FPS", Integer.toString(var0));
      } else {
         int var3 = var0;
         int var4 = var0;
         int var5 = fpsSampleIndex;

         for (int var6 = 0; var6 < var2; var6++) {
            var5 = (var5 - 1 + 200) % 200;
            int var7 = fpsSamples[var5];
            if (var7 < var3) {
               var3 = var7;
            }

            if (var7 > var4) {
               var4 = var7;
            }
         }

         int var20 = Math.max(1, var4 - var3);
         RiptideHudManager.HudLine var21 = new RiptideHudManager.HudLine();
         if (boolSetting("fps_graph", "show-current-fps")) {
            var21.add(Integer.toString(var0), color("fps_graph", "value-color", -791321));
         }

         String[] var8 = new String[]{"▁", "▂", "▃", "▄", "▅", "▆", "▇", "█"};
         var5 = fpsSampleIndex;
         int var9 = Math.max(1, var2 / 20);
         int var10 = 0;
         var21.add(" ", alphaColor(color("fps_graph", "label-color", -4743522), 0.3F));

         for (int var11 = 0; var11 < var2 && var10 < 20; var11 += var9) {
            var5 = (var5 - 1 + 200) % 200;
            int var12 = fpsSamples[var5];
            int var13 = Math.min(7, (var12 - var3) * 8 / var20);
            float var14 = (float)(var12 - var3) / var20;
            int var15 = Math.round(255.0F * (1.0F - var14));
            int var16 = Math.round(255.0F * var14);
            int var17 = 0xFF000000 | Math.min(255, var15) << 16 | Math.min(255, var16) << 8;
            var21.add(var8[var13], var17);
            var10++;
         }

         return var21;
      }
   }

   private static void cpsLines(List<RiptideHudManager.HudLine> var0) {
      boolean var1 = boolSetting("cps", "show-total");
      int var2 = RiptideCpsTracker.leftCps();
      int var3 = RiptideCpsTracker.rightCps();
      var0.add(row("cps", "Left", Integer.toString(var2)));
      var0.add(row("cps", "Right", Integer.toString(var3)));
      if (var1) {
         var0.add(row("cps", "Total", Integer.toString(var2 + var3)));
      }
   }

   private static String lookingAt() {
      HitResult var0 = MC.hitResult;
      if (var0 == null || var0.getType() == Type.MISS) {
         return "Nothing";
      } else if (var0 instanceof EntityHitResult var1) {
         return var1.getEntity().getName().getString();
      } else if (var0 instanceof BlockHitResult var2 && MC.level != null) {
         BlockPos var3 = var2.getBlockPos();
         Identifier var4 = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(var3).getBlock());
         return RiptideRegistryLabels.block(var4.toString()) + " " + blockPositionText(var3.getX(), var3.getY(), var3.getZ());
      } else {
         return RiptideRegistryLabels.identifier(var0.getType().name());
      }
   }

   private static String breakingProgress() {
      if (MC.hitResult instanceof BlockHitResult && MC.gameMode != null) {
         try {
            RiptideMultiPlayerGameModeAccessor var0 = (RiptideMultiPlayerGameModeAccessor)MC.gameMode;
            return !var0.riptide$isDestroying() ? "0%" : Math.round(clamp((double)var0.riptide$getDestroyProgress(), 0.0, 1.0) * 100.0) + "%";
         } catch (Throwable var1) {
            return "0%";
         }
      } else {
         return "0%";
      }
   }

   private static RiptideHudManager.HudLine tpsLine() {
      double var0 = ServerTickTracker.getEstimatedTps();
      if (var0 < tpsRollingMin || System.currentTimeMillis() - tpsMinSetTime > 5000L) {
         tpsRollingMin = var0;
         tpsMinSetTime = System.currentTimeMillis();
      }

      String var2 = String.format(Locale.ROOT, boolSetting("tps", "tps-precise") ? "%.2f" : "%.1f", var0);
      int var3 = color("tps", "value-color", -791321);
      if (boolSetting("tps", "tps-color-threshold")) {
         var3 = var0 >= 19.0 ? -12588930 : (var0 >= 15.0 ? -10752 : -50373);
      }

      RiptideHudManager.HudLine var4 = new RiptideHudManager.HudLine();
      var4.add("TPS: ", color("tps", "label-color", -4743522));
      var4.add(var2, var3);
      if (boolSetting("tps", "tps-show-jitter")) {
         var4.add(" (" + String.format(Locale.ROOT, "%.1f", tpsRollingMin) + ")", alphaColor(var3, 0.65F));
      }

      return var4;
   }

   private static RiptideHudManager.HudLine pingLine() {
      int var0 = ServerTickTracker.getPingMs();
      int var1 = color("ping", "value-color", -791321);
      if (boolSetting("ping", "ping-color-threshold")) {
         var1 = var0 <= 50 ? -12588930 : (var0 <= 150 ? -10752 : -50373);
      }

      RiptideHudManager.HudLine var2 = new RiptideHudManager.HudLine();
      var2.add("Ping: ", color("ping", "label-color", -4743522));
      var2.add(var0 + " ms", var1);
      if (boolSetting("ping", "ping-show-jitter")) {
         int var3 = lastPingValue;
         lastPingValue = var0;
         int var4 = var3 > 0 ? Math.abs(var0 - var3) : 0;
         if (var4 > 0) {
            var2.add(" ±" + var4, alphaColor(var1, 0.65F));
         }
      }

      return var2;
   }

   private static RiptideHudManager.HudLine durabilityLine() {
      ItemStack var0 = MC.player == null ? ItemStack.EMPTY : MC.player.getMainHandItem();
      RiptideHudManager.HudLine var1 = new RiptideHudManager.HudLine();
      if (boolSetting("durability", "show-item-name") && !var0.isEmpty()) {
         var1.add(var0.getHoverName().getString() + ": ", color("durability", "label-color", -4743522));
      } else {
         var1.add("Durability: ", color("durability", "label-color", -4743522));
      }

      if (var0.isEmpty()) {
         var1.add("Empty", color("durability", "value-color", -791321));
      } else if (!var0.isDamageableItem()) {
         var1.add("No durability", color("durability", "value-color", -791321));
      } else {
         int var2 = Math.max(0, var0.getMaxDamage() - var0.getDamageValue());
         int var3 = Math.max(1, var0.getMaxDamage());
         int var4 = Math.round(var2 * 100.0F / var3);
         int var5 = color("durability", "value-color", -791321);
         if (boolSetting("durability", "low-durability-warn")) {
            var5 = var4 > 50 ? -12588930 : (var4 > 20 ? -10752 : -50373);
         }

         var1.add(String.format(Locale.ROOT, "%d / %d (%d%%)", var2, var3, var4), var5);
      }

      return var1;
   }

   private static RiptideHudManager.HudLine lookingAtLine() {
      RiptideHudManager.HudLine var0 = new RiptideHudManager.HudLine();
      HitResult var1 = MC.hitResult;
      var0.add("Looking: ", color("looking_at", "label-color", -4743522));
      if (var1 != null && var1.getType() != Type.MISS) {
         String var2;
         if (var1 instanceof EntityHitResult var3) {
            var2 = var3.getEntity().getName().getString();
         } else if (var1 instanceof BlockHitResult var4 && MC.level != null) {
            BlockState var5 = MC.level.getBlockState(var4.getBlockPos());
            Identifier var6 = BuiltInRegistries.BLOCK.getKey(var5.getBlock());
            if (boolSetting("looking_at", "show-block-id")) {
               var2 = var6.toString();
            } else {
               var2 = RiptideRegistryLabels.block(var6.toString());
            }

            if (boolSetting("looking_at", "show-distance")) {
               double var7 = pickOrigin().distanceTo(var4.getLocation());
               var2 = var2 + " " + String.format(Locale.ROOT, "%.1fm", var7);
            }
         } else {
            var2 = RiptideRegistryLabels.identifier(var1.getType().name());
         }

         var0.add(var2, color("looking_at", "value-color", -791321));
         return var0;
      } else {
         var0.add("Nothing", color("looking_at", "value-color", -791321));
         return var0;
      }
   }

   private static RiptideHudManager.HudLine breakingLine() {
      RiptideHudManager.HudLine var0 = new RiptideHudManager.HudLine();
      if (MC.hitResult instanceof BlockHitResult && MC.gameMode != null && MC.level != null) {
         double var1 = 0.0;

         try {
            RiptideMultiPlayerGameModeAccessor var3 = (RiptideMultiPlayerGameModeAccessor)MC.gameMode;
            if (var3.riptide$isDestroying()) {
               var1 = var3.riptide$getDestroyProgress();
            }

            var1 = Math.max(0.0, Math.min(1.0, var1));
         } catch (Throwable var6) {
         }

         int var7 = (int)Math.round(var1 * 100.0);
         if (boolSetting("breaking_progress", "show-block-name")) {
            BlockPos var4 = ((BlockHitResult)MC.hitResult).getBlockPos();
            String var5 = RiptideRegistryLabels.block(BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(var4).getBlock()).toString());
            var0.add(var5 + ": ", color("breaking_progress", "label-color", -4743522));
         } else {
            var0.add("Breaking: ", color("breaking_progress", "label-color", -4743522));
         }

         var0.add(var7 + "%", var7 >= 80 ? -12588930 : (var7 >= 40 ? -10752 : color("breaking_progress", "value-color", -791321)));
         return var0;
      } else {
         var0.add("Breaking: 0%", color("breaking_progress", "label-color", -4743522));
         return var0;
      }
   }

   private static RiptideHudManager.HudLine serverLine() {
      RiptideHudManager.HudLine var0 = new RiptideHudManager.HudLine();
      var0.add("Server: ", color("server", "label-color", -4743522));
      if (MC.getCurrentServer() != null && MC.getCurrentServer().ip != null && !MC.getCurrentServer().ip.isBlank()) {
         var0.add(MC.getCurrentServer().ip, color("server", "value-color", -791321));
      } else if (MC.hasSingleplayerServer()) {
         var0.add("Singleplayer", color("server", "value-color", -791321));
      } else {
         var0.add("N/A", color("server", "value-color", -791321));
      }

      return var0;
   }

   private static RiptideHudManager.HudLine weatherLine() {
      String var0 = weather();
      RiptideHudManager.HudLine var1 = new RiptideHudManager.HudLine();
      var1.add("Weather: ", color("weather", "label-color", -4743522));

      int var2 = switch (var0) {
         case "Thunder" -> -50373;
         case "Rain" -> -10036737;
         default -> color("weather", "value-color", -791321);
      };
      var1.add(var0, var2);
      if (boolSetting("weather", "show-temperature") && MC.level != null) {
         Holder var3 = MC.level.getBiome(viewBlockPos());
         float var5 = ((Biome)var3.value()).getBaseTemperature();
         var1.add(" " + String.format(Locale.ROOT, "%.1f°C", var5), alphaColor(var2, 0.65F));
      }

      return var1;
   }

   private static RiptideHudManager.HudLine biomeLine() {
      String var0 = biome();
      RiptideHudManager.HudLine var1 = new RiptideHudManager.HudLine();
      var1.add("Biome: ", color("biome", "label-color", -4743522));
      var1.add(var0, color("biome", "value-color", -791321));
      return var1;
   }

   private static RiptideHudManager.HudLine worldTimeLine() {
      String var0 = setting("world_time", "world-time-format");
      RiptideHudManager.HudLine var1 = new RiptideHudManager.HudLine();
      var1.add("Time: ", color("world_time", "label-color", -4743522));
      if ("ticks".equals(var0) && MC.level != null) {
         var1.add(Long.toString(currentDayTime() % 24000L), color("world_time", "value-color", -791321));
      } else {
         var1.add(worldTime(), color("world_time", "value-color", -791321));
      }

      if (boolSetting("world_time", "show-day") && MC.level != null) {
         long var2 = currentDayTime() / 24000L;
         var1.add(" Day " + var2, alphaColor(color("world_time", "value-color", -791321), 0.65F));
      }

      return var1;
   }

   private static RiptideHudManager.HudLine realTimeLine() {
      String var0 = setting("real_time", "real-time-format");
      DateTimeFormatter var1 = "24h".equals(var0) ? DateTimeFormatter.ofPattern("HH:mm") : DateTimeFormatter.ofPattern("h:mm a", Locale.US);
      RiptideHudManager.HudLine var2 = new RiptideHudManager.HudLine();
      var2.add("Time: ", color("real_time", "label-color", -4743522));
      var2.add(LocalTime.now().format(var1), color("real_time", "value-color", -791321));
      if (boolSetting("real_time", "show-date")) {
         var2.add(" " + LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd")), alphaColor(color("real_time", "value-color", -791321), 0.65F));
      }

      return var2;
   }

   private static String serverName() {
      if (MC.getCurrentServer() != null && MC.getCurrentServer().ip != null && !MC.getCurrentServer().ip.isBlank()) {
         return MC.getCurrentServer().ip;
      } else {
         return MC.hasSingleplayerServer() ? "Singleplayer" : "N/A";
      }
   }

   private static String weather() {
      if (MC.level == null) {
         return "N/A";
      } else if (MC.level.isThundering()) {
         return "Thunder";
      } else {
         return MC.level.isRaining() ? "Rain" : "Clear";
      }
   }

   private static String biome() {
      if (MC.level != null && MC.player != null) {
         try {
            return MC.level.getBiome(viewBlockPos()).unwrapKey().map(var0 -> RiptideRegistryLabels.identifier(var0.identifier().toString())).orElse("Unknown");
         } catch (Throwable var1) {
            return "Unknown";
         }
      } else {
         return "N/A";
      }
   }

   private static String worldTime() {
      if (MC.level == null) {
         return "N/A";
      } else {
         long var0 = currentDayTime() % 24000L;
         long var2 = (var0 / 1000L + 6L) % 24L;
         long var4 = var0 % 1000L * 60L / 1000L;
         return String.format(Locale.ROOT, "%02d:%02d", var2, var4);
      }
   }

   private static long currentDayTime() {
      if (MC.level == null) {
         return 0L;
      } else {
         if (!dayTimeResolved) {
            resolveDayTimeAccessor();
         }

         Method var0 = dayTimeGetter;
         if (var0 != null) {
            try {
               Object var1 = dayTimeLevelData != null ? dayTimeLevelData.invoke(MC.level) : MC.level;
               if ((var1 == null ? null : var0.invoke(var1)) instanceof Number var2) {
                  return var2.longValue();
               }
            } catch (Throwable var4) {
            }
         }

         return MC.level.getGameTime();
      }
   }

   private static synchronized void resolveDayTimeAccessor() {
      if (!dayTimeResolved) {
         try {
            Method var0 = findGetter(MC.level.getClass(), "getDayTime", "dayTime");
            if (var0 != null) {
               dayTimeGetter = var0;
            } else {
               Method var1 = MC.level.getClass().getMethod("getLevelData");
               Object var2 = var1.invoke(MC.level);
               Method var3 = var2 == null ? null : findGetter(var2.getClass(), "getDayTime", "dayTime");
               if (var3 != null) {
                  dayTimeLevelData = var1;
                  dayTimeGetter = var3;
               }
            }
         } catch (Throwable var4) {
         }

         dayTimeResolved = true;
      }
   }

   private static Method findGetter(Class<?> var0, String... var1) {
      for (String var5 : var1) {
         try {
            return var0.getMethod(var5);
         } catch (Throwable var7) {
         }
      }

      return null;
   }

   private static String directionName() {
      if (MC.player == null) {
         return "N";
      } else {
         return switch (Direction.fromYRot(viewYaw())) {
            case NORTH -> "North";
            case SOUTH -> "South";
            case EAST -> "East";
            case WEST -> "West";
            case UP -> "Up";
            case DOWN -> "Down";
            default -> throw new MatchException(null, null);
         };
      }
   }

   private static RiptideHudManager.HudLine itemCounterLine() {
      Item var0 = itemCounterTarget();
      RiptideHudManager.HudLine var1 = new RiptideHudManager.HudLine();
      if (boolSetting("item_counter", "item-show-name")) {
         var1.add(itemCounterLabel(var0) + ": ", color("item_counter", "label-color", -4743522));
      }

      var1.add(Integer.toString(countItemInInventory(var0)), color("item_counter", "value-color", -791321));
      return var1;
   }

   private static Item itemCounterTarget() {
      if (MC.player == null) {
         return null;
      } else if (boolSetting("item_counter", "item-count-held")) {
         ItemStack var3 = MC.player.getMainHandItem();
         return var3.isEmpty() ? null : var3.getItem();
      } else {
         String var0 = setting("item_counter", "item-id");
         if (var0 != null && !var0.isBlank()) {
            try {
               Identifier var1 = Identifier.parse(var0.contains(":") ? var0 : "minecraft:" + var0);
               return (Item)BuiltInRegistries.ITEM.getOptional(var1).orElse(null);
            } catch (Exception var2) {
               return null;
            }
         } else {
            return null;
         }
      }
   }

   private static String itemCounterLabel(Item var0) {
      if (var0 == null) {
         return boolSetting("item_counter", "item-count-held") ? "Hand" : "Items";
      } else {
         return RiptideRegistryLabels.item(BuiltInRegistries.ITEM.getKey(var0).toString());
      }
   }

   private static int countItemInInventory(Item var0) {
      if (MC.player != null && var0 != null) {
         int var1 = 0;

         for (int var2 = 0; var2 < MC.player.getInventory().getContainerSize(); var2++) {
            ItemStack var3 = MC.player.getInventory().getItem(var2);
            if (!var3.isEmpty() && var3.getItem() == var0) {
               var1 += var3.getCount();
            }
         }

         return var1;
      } else {
         return 0;
      }
   }

   private static String blockPositionText(double var0, double var2, double var4) {
      return String.format(Locale.ROOT, "%d, %d, %d", (int)Math.floor(var0), (int)Math.floor(var2), (int)Math.floor(var4));
   }

   private static void potionLines(List<RiptideHudManager.HudLine> var0) {
      if (MC.player != null && !MC.player.getActiveEffects().isEmpty()) {
         for (MobEffectInstance var2 : MC.player.getActiveEffects()) {
            String var3 = Component.translatable(var2.getDescriptionId()).getString();
            int var4 = var2.getAmplifier() + 1;
            if (var4 > 1) {
               var3 = var3 + " " + roman(var4);
            }

            int var5 = Math.max(0, var2.getDuration() / 20);
            var0.add(row("potion_timers", var3, String.format(Locale.ROOT, "%d:%02d", var5 / 60, var5 % 60)));
         }
      } else {
         var0.add(row("potion_timers", "Effects", "None"));
      }
   }

   private static String roman(int var0) {
      return switch (Math.max(1, Math.min(10, var0))) {
         case 1 -> "I";
         case 2 -> "II";
         case 3 -> "III";
         case 4 -> "IV";
         case 5 -> "V";
         case 6 -> "VI";
         case 7 -> "VII";
         case 8 -> "VIII";
         case 9 -> "IX";
         default -> "X";
      };
   }

   private static int lineWidth(Font var0, RiptideHudManager.HudLine var1) {
      int[] var2 = new int[var1.segments.size()];
      int var3 = 0;

      for (int var4 = 0; var4 < var1.segments.size(); var4++) {
         RiptideHudManager.HudSegment var5 = var1.segments.get(var4);
         int var6 = UiText.width(var0, var5.text, THEME.fontFor(UiTone.BODY), var5.color);
         var2[var4] = var6;
         var3 += var6;
      }

      var1.segmentWidths = var2;
      return var3;
   }

   private static int lineHeight(String var0) {
      return THEME.fontHeight(UiTone.BODY) + lineGap(var0);
   }

   private static int activeModuleRowHeight(String var0) {
      return THEME.fontHeight(UiTone.BODY) + verticalPadding(var0) * 2;
   }

   private static int activeModuleStairSnap(String var0) {
      return clamp(intSetting(var0, "stair-snap", 2), 0, 24);
   }

   private static int lineGap(String var0) {
      return clamp(intSetting(var0, "line-gap", 0), 0, 10);
   }

   private static void migrateOldHud(RiptideConfig var0) {
      ensureStateMap(var0);
      RiptideConfig.ModuleState var1 = var0.modules == null ? null : var0.modules.get("hud");
      Map var2 = var1 != null && var1.settings != null ? var1.settings : Map.of();
      int var3 = parseInt((String)var2.get("x"), 8);
      int var4 = parseInt((String)var2.get("y"), 8);
      boolean var5 = parseBool((String)var2.get("modules"), true);
      boolean var6 = parseBool((String)var2.get("metrics"), true);
      boolean var7 = parseBool((String)var2.get("coords"), true);
      putMigrated(var0, "active_modules", var5, var3, var4);
      putMigrated(var0, "tps", var6, var3, var4 + 24);
      putMigrated(var0, "coordinates", var7, var3, var4 + 44);
      putMigrated(var0, "nether_coords", var7, var3, var4 + 64);
      var0.hudLayoutMigrated = true;
      var0.save();
   }

   private static void putMigrated(RiptideConfig var0, String var1, boolean var2, int var3, int var4) {
      RiptideConfig.HudElementState var5 = var0.hudElements.computeIfAbsent(var1, var1x -> defaultState(var1));
      var5.enabled = var2;
      var5.x = var3;
      var5.y = var4;
      if (var5.settings == null) {
         var5.settings = new LinkedHashMap<>();
      }

      for (Entry var7 : defaultSettings(var1).entrySet()) {
         var5.settings.putIfAbsent((String)var7.getKey(), (String)var7.getValue());
      }
   }

   private static void normalizeDefaultHudStack(RiptideConfig var0) {
      if (var0 != null && var0.hudElements != null) {
         int var1 = defaultHudRowStep();
         int var2 = THEME.fontHeight(UiTone.BODY) + 2;
         int var3 = logoHeight(180) + 1;
         int var4 = logoHeight(180) + 6;
         int var5 = defaultLogoElementHeight();
         boolean var6 = false;
         RiptideConfig.HudElementState var7 = var0.hudElements.get("fps");
         RiptideConfig.HudElementState var8 = var0.hudElements.get("tps");
         RiptideConfig.HudElementState var9 = var0.hudElements.get("ping");
         RiptideConfig.HudElementState var10 = var0.hudElements.get("speed");
         RiptideConfig.HudElementState var11 = var0.hudElements.get("compass");
         RiptideConfig.HudElementState var12 = var0.hudElements.get("coordinates");
         RiptideConfig.HudElementState var13 = var0.hudElements.get("nether_coords");
         RiptideConfig.HudElementState var14 = var0.hudElements.get("rotation");
         RiptideConfig.HudElementState var15 = var0.hudElements.get("anti_vanish");
         RiptideConfig.HudElementState var16 = var0.hudElements.get("watermark");
         if (!var0.hudLayoutNormalizedV2) {
            normalizeLegacyHudPositions(var7, var8, var9, var10, var11, var12, var13, var14, var15, var1, var2, var3, var4, var5);
            var0.hudLayoutNormalizedV2 = true;
            var6 = true;
         }

         if (var11 != null && var11.settings != null && !var11.settings.containsKey("compass-style-migrated")) {
            if ("3".equals(var11.settings.get("padding"))) {
               var11.settings.put("padding", "1");
               var6 = true;
            }

            if ("86".equals(var11.settings.get("compass-width"))) {
               var11.settings.put("compass-width", "112");
               var6 = true;
            }

            if ("true".equalsIgnoreCase(var11.settings.get("outline"))) {
               var11.settings.put("outline", "false");
               var6 = true;
            }

            var11.settings.put("compass-style-migrated", "true");
            var6 = true;
         }

         if (var16 != null && var16.settings != null && !var16.settings.containsKey("logo-style-migrated")) {
            if ("false".equalsIgnoreCase(var16.settings.get("background"))) {
               var16.settings.put("background", "true");
               var6 = true;
            }

            if ("1".equals(var16.settings.get("padding"))) {
               var16.settings.put("padding", "1");
               var6 = true;
            }

            var16.settings.put("logo-style-migrated", "true");
            var6 = true;
         }

         for (RiptideConfig.HudElementState var18 : var0.hudElements.values()) {
            if (var18 != null && var18.settings != null) {
               if (!var18.settings.containsKey("vertical-padding")) {
                  var18.settings.put("vertical-padding", "0");
                  var6 = true;
               }

               if (!var18.settings.containsKey("legacy-style-migrated")) {
                  String var19 = var18.settings.get("outline-color");
                  if ("FF8F1F24".equalsIgnoreCase(var19) || "FFD3424D".equalsIgnoreCase(var19)) {
                     var18.settings.put("outline-color", "FF750000");
                     var6 = true;
                  }

                  if ("5".equals(var18.settings.get("stair-snap"))) {
                     var18.settings.put("stair-snap", "2");
                     var6 = true;
                  }

                  var18.settings.put("legacy-style-migrated", "true");
                  var6 = true;
               }
            }
         }

         if (var6) {
            var0.save();
         }
      }
   }

   private static void normalizeLegacyHudPositions(
      RiptideConfig.HudElementState var0,
      RiptideConfig.HudElementState var1,
      RiptideConfig.HudElementState var2,
      RiptideConfig.HudElementState var3,
      RiptideConfig.HudElementState var4,
      RiptideConfig.HudElementState var5,
      RiptideConfig.HudElementState var6,
      RiptideConfig.HudElementState var7,
      RiptideConfig.HudElementState var8,
      int var9,
      int var10,
      int var11,
      int var12,
      int var13
   ) {
      if (isTopLeftAt(var0, 0, var11) || isTopLeftAt(var0, 0, var12)) {
         var0.y = var13;
      }

      if (isTopLeftAt(var1, 0, var11 + var9) || isTopLeftAt(var1, 0, var12 + var9)) {
         var1.enabled = false;
         var1.y = var13;
      }

      if (isTopLeftAt(var2, 0, var11 + var9 * 2) || isTopLeftAt(var2, 0, var12 + var9 * 2)) {
         var2.enabled = false;
         var2.y = var13;
      }

      if (isTopLeftAt(var3, 0, var11 + var9 * 3) || isTopLeftAt(var3, 0, var12 + var9)) {
         var3.y = var13 + var9;
      }

      if (isTopLeftAt(var4, 0, var11 + var9 * 5) || isTopLeftAt(var4, 0, var12 + var9 * 2)) {
         var4.y = var13 + var9 * 2;
      }

      if (isDefaultAntiVanishPlacement(var8)) {
         snapHudState(var8, "TOP_LEFT", 0, var13 + var9 * 2);
      }

      byte var14 = -1;
      byte var15 = -2;
      if (isLegacyRightCornerStack(var5, var6, var7) || isBottomRightStackAt(var5, var6, var7, 0, var10) || isBottomRightStackAt(var5, var6, var7, 0, var9)) {
         snapHudState(var5, "BOTTOM_RIGHT", var14, var15 - var9 * 2);
         snapHudState(var6, "BOTTOM_RIGHT", var14, var15 - var9);
         snapHudState(var7, "BOTTOM_RIGHT", var14, var15);
      }

      if (isLegacyCompass(var4, var11, var12, var13, var9)) {
         snapHudState(var4, "TOP_CENTER", 0, 2);
      }
   }

   private static boolean isTopLeftAt(RiptideConfig.HudElementState var0, int var1, int var2) {
      return var0 != null && "TOP_LEFT".equals(var0.anchor) && var0.x == var1 && var0.y == var2;
   }

   private static boolean isDefaultAntiVanishPlacement(RiptideConfig.HudElementState var0) {
      return isAnchoredAt(var0, "TOP_RIGHT", 0, 72) || isAnchoredAt(var0, "TOP_LEFT", 0, 72);
   }

   private static boolean isLegacyRightCornerStack(RiptideConfig.HudElementState var0, RiptideConfig.HudElementState var1, RiptideConfig.HudElementState var2) {
      int var3 = 0;
      if (isLegacyRightCornerState(var0)) {
         var3++;
      }

      if (isLegacyRightCornerState(var1)) {
         var3++;
      }

      if (isLegacyRightCornerState(var2)) {
         var3++;
      }

      return var3 >= 2;
   }

   private static boolean isLegacyRightCornerState(RiptideConfig.HudElementState var0) {
      if (var0 == null) {
         return false;
      } else {
         String var1 = var0.anchor == null ? "" : var0.anchor.toUpperCase(Locale.ROOT);
         return !var1.contains("RIGHT") || var0.x <= 0 && var0.y <= 0
            ? "TOP_LEFT".equals(var1) && var0.x >= 0 && var0.x <= 16 && var0.y >= 0 && var0.y <= 140
            : true;
      }
   }

   private static boolean isBottomRightStackAt(
      RiptideConfig.HudElementState var0, RiptideConfig.HudElementState var1, RiptideConfig.HudElementState var2, int var3, int var4
   ) {
      return isAnchoredAt(var0, "BOTTOM_RIGHT", var3, -var4 * 2)
         && isAnchoredAt(var1, "BOTTOM_RIGHT", var3, -var4)
         && isAnchoredAt(var2, "BOTTOM_RIGHT", var3, 0);
   }

   private static boolean isAnchoredAt(RiptideConfig.HudElementState var0, String var1, int var2, int var3) {
      return var0 != null && var1.equals(var0.anchor) && var0.x == var2 && var0.y == var3;
   }

   private static boolean isLegacyCompass(RiptideConfig.HudElementState var0, int var1, int var2, int var3, int var4) {
      if (var0 == null) {
         return false;
      } else {
         String var5 = var0.anchor == null ? "" : var0.anchor.toUpperCase(Locale.ROOT);
         if ("TOP_CENTER".equals(var5) && var0.x == 0 && var0.y == 2) {
            return false;
         } else {
            return var0.x != 0 && Math.abs(var0.x) > 16
               ? false
               : "TOP_LEFT".equals(var5) || "TOP_CENTER".equals(var5) && (var0.y == var3 + var4 * 2 || var0.y == var1 + var4 * 5 || var0.y == var2 + var4 * 2);
         }
      }
   }

   private static boolean snapHudState(RiptideConfig.HudElementState var0, String var1, int var2, int var3) {
      if (var0 == null) {
         return false;
      } else {
         boolean var4 = !var1.equals(var0.anchor) || var0.x != var2 || var0.y != var3;
         if (!var4) {
            return false;
         } else {
            var0.anchor = var1;
            var0.x = var2;
            var0.y = var3;
            return true;
         }
      }
   }

   private static RiptideConfig.HudElementState defaultState(String var0) {
      RiptideConfig.HudElementState var1 = new RiptideConfig.HudElementState();
      var1.enabled = defaultEnabled(var0);
      var1.scale = 1.0;
      var1.anchor = defaultAnchor(var0);
      int[] var2 = defaultPosition(var0);
      var1.x = var2[0];
      var1.y = var2[1];
      var1.settings = new LinkedHashMap<>(defaultSettings(var0));
      return var1;
   }

   private static String defaultAnchor(String var0) {
      HudElementProvider var1 = HudElements.get(var0);
      if (var1 != null) {
         return var1.defaultAnchor();
      } else {
         return switch (var0) {
            case "active_modules" -> "TOP_RIGHT";
            case "coordinates", "nether_coords", "rotation" -> "BOTTOM_RIGHT";
            case "compass" -> "TOP_CENTER";
            case "spotify" -> "BOTTOM_LEFT";
            case "keystrokes" -> "MIDDLE_LEFT";
            default -> "TOP_LEFT";
         };
      }
   }

   private static int[] defaultPosition(String var0) {
      HudElementProvider var1 = HudElements.get(var0);
      if (var1 != null) {
         return new int[]{var1.defaultX(), var1.defaultY()};
      } else {
         int var2 = defaultHudRowStep();
         int var3 = defaultLogoElementHeight();

         return switch (var0) {
            case "watermark" -> new int[]{0, 0};
            case "fps" -> new int[]{0, var3};
            case "tps", "ping" -> new int[]{0, var3};
            case "speed" -> new int[]{0, var3 + var2};
            case "active_modules" -> new int[]{0, 0};
            case "anti_vanish" -> new int[]{0, var3 + var2 * 2};
            case "rotation" -> new int[]{-1, -2};
            case "nether_coords" -> new int[]{-1, -2 - var2};
            case "coordinates" -> new int[]{-1, -2 - var2 * 2};
            case "armor" -> new int[]{0, var3 + var2 * 2};
            case "compass" -> new int[]{0, 2};
            case "spotify" -> new int[]{1, -2};
            case "potion_timers" -> new int[]{0, var3 + var2 * 4};
            case "keystrokes" -> new int[]{4, 0};
            default -> new int[]{0, 0};
         };
      }
   }

   private static boolean defaultEnabled(String var0) {
      HudElementProvider var1 = HudElements.get(var0);
      return var1 != null
         ? var1.defaultEnabled()
         : "active_modules".equals(var0)
            || "watermark".equals(var0)
            || "fps".equals(var0)
            || "speed".equals(var0)
            || "coordinates".equals(var0)
            || "nether_coords".equals(var0)
            || "rotation".equals(var0)
            || "compass".equals(var0)
            || "anti_vanish".equals(var0)
            || "keystrokes".equals(var0);
   }

   private static Map<String, String> defaultSettings(String var0) {
      Map var1 = DEFAULT_SETTINGS_CACHE.get(var0);
      if (var1 != null) {
         return var1;
      } else {
         LinkedHashMap var2 = new LinkedHashMap();
         var2.put("shadow", "true");
         var2.put("use-custom-colors", "false");
         var2.put("background", "true");
         var2.put("background-color", "32191919");
         var2.put("text-color", "FFF3ECE7");
         var2.put("label-color", "FFB79E9E");
         var2.put("value-color", "FFF3ECE7");
         var2.put("accent-color", "FFFF3B3B");
         var2.put("alignment", "Left");
         var2.put("outline", "false");
         var2.put("outline-color", "FF750000");
         var2.put("outline-width", "1");
         var2.put("padding", "1");
         var2.put("vertical-padding", "0");
         var2.put("line-gap", "0");
         if ("watermark".equals(var0)) {
            var2.put("shadow", "false");
            var2.put("background", "true");
            var2.put("outline", "false");
            var2.put("padding", "1");
            var2.put("logo-right-padding", "3");
            var2.put("logo-width", "180");
         }

         if ("compass".equals(var0)) {
            var2.put("padding", "1");
            var2.put("compass-width", "112");
            var2.put("outline", "false");
         }

         if ("spotify".equals(var0)) {
            var2.put("spotify-menu-strip", "true");
            var2.put("spotify-scroll-speed", "25");
            var2.put("spotify-source", "Spotify");
            var2.put("spotify-width", "175");
            var2.put("spotify-color-mode", "Theme");
            var2.put("spotify-artist-color", "FFB79E9E");
            var2.put("spotify-title-color", "FFF3ECE7");
            var2.put("spotify-time-color", "FFB79E9E");
            var2.put("spotify-progress-color", "FFFF3B3B");
            var2.put("spotify-part-art", "true");
            var2.put("spotify-part-artist", "true");
            var2.put("spotify-part-time", "true");
            var2.put("spotify-time-position", "Top");
            var2.put("spotify-part-progress", "true");
            var2.put("rainbow-speed", "1.0");
            var2.put("rainbow-spread", "0.035");
            var2.put("rainbow-saturation", "0.35");
            var2.put("rainbow-brightness", "1.0");
            var2.put("spotify-rainbow-direction", "Forward");
            var2.put("spotify-rainbow-artist", "true");
            var2.put("spotify-rainbow-title", "true");
            var2.put("spotify-rainbow-time", "false");
            var2.put("spotify-rainbow-progress", "true");
         }

         if ("armor".equals(var0) || "inventory".equals(var0)) {
            var2.put("outline", "true");
            var2.put("slot-style", "Flat");
         }

         if ("tps".equals(var0)) {
            var2.put("tps-precise", "false");
            var2.put("tps-color-threshold", "true");
            var2.put("tps-show-jitter", "false");
         }

         if ("ping".equals(var0)) {
            var2.put("ping-color-threshold", "true");
            var2.put("ping-show-jitter", "true");
         }

         if ("cps".equals(var0)) {
            var2.put("show-total", "false");
         }

         if ("durability".equals(var0)) {
            var2.put("show-item-name", "false");
            var2.put("low-durability-warn", "true");
         }

         if ("looking_at".equals(var0)) {
            var2.put("show-distance", "false");
            var2.put("show-block-id", "false");
         }

         if ("breaking_progress".equals(var0)) {
            var2.put("show-block-name", "false");
         }

         if ("weather".equals(var0)) {
            var2.put("show-temperature", "false");
         }

         if ("world_time".equals(var0)) {
            var2.put("world-time-format", "24h");
            var2.put("show-day", "false");
         }

         if ("real_time".equals(var0)) {
            var2.put("real-time-format", "12h");
            var2.put("show-date", "false");
         }

         if ("keystrokes".equals(var0)) {
            var2.put("background", "false");
            var2.put("padding", "0");
            var2.put("keystroke-active-color", "FFFF3B3B");
            var2.put("keystroke-idle-color", "B4101014");
            var2.put("keystroke-text-color", "FFFFFFFF");
            var2.put("keystroke-show-space", "true");
            var2.put("keystroke-show-mouse", "true");
            var2.put("keystroke-size", "18");
         }

         if ("active_modules".equals(var0) || "coordinates".equals(var0) || "nether_coords".equals(var0) || "rotation".equals(var0)) {
            var2.put("alignment", "Right");
         }

         if ("active_modules".equals(var0)) {
            var2.put("use-custom-colors", "true");
            var2.put("module-info", "true");
            var2.put("show-keybind", "false");
            var2.put("sort", "Width");
            var2.put("hidden-modules", "hud");
            var2.put("color-mode", "Rainbow");
            var2.put("flat-color", "FFFF3B3B");
            var2.put("gradient-start-color", "FFFF3B3B");
            var2.put("gradient-end-color", "FFFFD6D6");
            var2.put("module-info-color", "FFB79E9E");
            var2.put("rainbow-speed", "1.0");
            var2.put("rainbow-spread", "0.035");
            var2.put("rainbow-saturation", "0.35");
            var2.put("rainbow-brightness", "1.0");
            var2.put("stair-snap", "2");
         }

         if ("item_counter".equals(var0)) {
            var2.put("item-id", "minecraft:totem_of_undying");
            var2.put("item-show-name", "true");
            var2.put("item-count-held", "false");
         }

         if ("memory".equals(var0)) {
            var2.put("show-bar", "true");
            var2.put("show-percent", "true");
            var2.put("memory-format", "used/max");
         }

         if ("server_ip".equals(var0)) {
            var2.put("show-port", "true");
         }

         if ("fps_graph".equals(var0)) {
            var2.put("graph-samples", "100");
            var2.put("show-current-fps", "true");
         }

         Map var3 = Collections.unmodifiableMap(new LinkedHashMap(var2));
         DEFAULT_SETTINGS_CACHE.put(var0, var3);
         return var3;
      }
   }

   private static int padding(String var0) {
      return clamp(intSetting(var0, "padding", 0), 0, 16);
   }

   private static int verticalPadding(String var0) {
      return clamp(intSetting(var0, "vertical-padding", 0), 0, 16);
   }

   private static int defaultHudRowStep() {
      return THEME.fontHeight(UiTone.BODY);
   }

   private static int logoWidth(String var0) {
      return clamp(intSetting(var0, "logo-width", 180), 48, 420);
   }

   private static int logoHeight(int var0) {
      return Math.max(1, Math.round(var0 * 0.0942029F));
   }

   private static int defaultLogoElementHeight() {
      return logoHeight(180) + 2;
   }

   private static int logoRightPadding(String var0) {
      return clamp(intSetting(var0, "logo-right-padding", 3), 0, 16);
   }

   private static int compassWidth(String var0) {
      return clamp(intSetting(var0, "compass-width", 112), 72, 200);
   }

   public static boolean spotifyMenuStrip(String var0) {
      return boolSetting(var0, "spotify-menu-strip");
   }

   public static int spotifyScrollSpeed(String var0) {
      return clamp(intSetting(var0, "spotify-scroll-speed", 25), 10, 60);
   }

   private static int spotifyWidth(String var0) {
      return clamp(intSetting(var0, "spotify-width", 175), 140, 260);
   }

   private static void syncSpotifySource(String var0) {
      int var1 = "Any Media".equals(setting(var0, "spotify-source")) ? 1 : 0;
      if (var1 != spotifySyncedSource) {
         spotifySyncedSource = var1;
         if (!RiptideLiteVariant.enabled()) {
            RiptideSpotify.setSourceAnywhere(var1 == 1);
         }
      }
   }

   static int anchorScreenWidth() {
      if (MC.getWindow() == null) {
         return 854;
      } else {
         int var0 = RiptideUiScale.getVirtualScreenWidth();
         return var0 > 0 ? var0 : 854;
      }
   }

   static int anchorScreenHeight() {
      if (MC.getWindow() == null) {
         return 480;
      } else {
         int var0 = RiptideUiScale.getVirtualScreenHeight();
         return var0 > 0 ? var0 : 480;
      }
   }

   private static int anchorX(String var0, int var1, int var2) {
      String var3 = var0 == null ? "" : var0.toUpperCase(Locale.ROOT);
      if (var3.contains("RIGHT")) {
         return anchorScreenWidth() + var1 - var2;
      } else {
         return var3.contains("CENTER") ? (anchorScreenWidth() - var2) / 2 + var1 : var1;
      }
   }

   private static int anchorY(String var0, int var1, int var2) {
      String var3 = var0 == null ? "" : var0.toUpperCase(Locale.ROOT);
      if (var3.contains("BOTTOM")) {
         return anchorScreenHeight() + var1 - var2;
      } else {
         return var3.contains("MIDDLE") ? (anchorScreenHeight() - var2) / 2 + var1 : var1;
      }
   }

   private static int safeContentX(String var0, int var1, int var2) {
      return clamp(var1, safeZoneXFor(var0), maxSafeX(var0, anchorScreenWidth(), var2));
   }

   private static int safeContentY(String var0, int var1, int var2) {
      return clamp(var1, safeZoneYFor(var0), maxSafeY(var0, anchorScreenHeight(), var2));
   }

   private static int safeZoneXFor(String var0) {
      return "watermark".equals(var0) ? 0 : 1;
   }

   private static int safeZoneYFor(String var0) {
      return !"watermark".equals(var0) && !"active_modules".equals(var0) ? 2 : 0;
   }

   private static int maxSafeX(String var0, int var1, int var2) {
      int var3 = safeZoneXFor(var0);
      return Math.max(var3, var1 - Math.max(0, var2) - var3);
   }

   private static int maxSafeY(String var0, int var1, int var2) {
      int var3 = safeZoneYFor(var0);
      return Math.max(var3, var1 - Math.max(0, var2) - var3);
   }

   private static int color(String var0, int var1) {
      return color("active_modules", var0, var1);
   }

   private static int color(String var0, String var1, int var2) {
      if (!boolSetting(var0, "use-custom-colors")) {
         int var3 = parseColor(defaultSetting(var0, var1), var2);
         return RiptideTheme.recolor(var3, channelForColorKey(var1));
      } else {
         return parseColor(setting(var0, var1), var2);
      }
   }

   private static String spotifyColorMode(String var0) {
      String var1 = setting(var0, "spotify-color-mode");
      return "Flat".equals(var1) ? "Custom" : var1;
   }

   private static int themedColor(String var0, String var1, int var2) {
      return RiptideTheme.recolor(parseColor(defaultSetting(var0, var1), var2), channelForColorKey(var1));
   }

   private static int customColor(String var0, String var1, int var2) {
      return parseColor(setting(var0, var1), var2);
   }

   private static boolean boolSettingDefault(String var0, String var1, boolean var2) {
      String var3 = setting(var0, var1);
      return var3.isEmpty() ? var2 : Boolean.parseBoolean(var3);
   }

   private static int spotifyPartColor(String var0, String var1, String var2, float var3, float var4, float var5, float var6, int var7, String var8, int var9) {
      return boolSettingDefault(var0, var1, Boolean.parseBoolean(var2)) ? spotifyRainbowColor(var3, var4, var5, var6, var7) : customColor(var0, var8, var9);
   }

   private static RiptideTheme.Channel channelForColorKey(String var0) {
      if (var0 == null) {
         return RiptideTheme.Channel.ACCENT;
      } else {
         return switch (var0) {
            case "outline-color" -> RiptideTheme.Channel.OUTLINE;
            case "label-color", "value-color", "text-color", "module-info-color", "keystroke-text-color", "spotify-artist-color", "spotify-title-color", "spotify-time-color" -> RiptideTheme.Channel.TEXT;
            case "background-color", "keystroke-idle-color" -> RiptideTheme.Channel.BACKDROP;
            default -> RiptideTheme.Channel.ACCENT;
         };
      }
   }

   private static int editorWash(boolean var0) {
      return RiptideTheme.recolor(var0 ? 942280982 : 571675672, RiptideTheme.Channel.BACKDROP);
   }

   private static int alphaColor(int var0, float var1) {
      if (var1 >= 0.999F) {
         return var0;
      } else {
         int var2 = (int)((var0 >>> 24 & 0xFF) * Math.max(0.0F, Math.min(1.0F, var1)));
         return var0 & 16777215 | var2 << 24;
      }
   }

   private static int lerpColor(int var0, int var1, double var2) {
      double var4 = Math.max(0.0, Math.min(1.0, var2));
      int var6 = var0 >>> 24 & 0xFF;
      int var7 = var0 >>> 16 & 0xFF;
      int var8 = var0 >>> 8 & 0xFF;
      int var9 = var0 & 0xFF;
      int var10 = (int)Math.round(var6 + ((var1 >>> 24 & 0xFF) - var6) * var4);
      int var11 = (int)Math.round(var7 + ((var1 >>> 16 & 0xFF) - var7) * var4);
      int var12 = (int)Math.round(var8 + ((var1 >>> 8 & 0xFF) - var8) * var4);
      int var13 = (int)Math.round(var9 + ((var1 & 0xFF) - var9) * var4);
      return var10 << 24 | var11 << 16 | var12 << 8 | var13;
   }

   private static int softenColor(int var0, int var1, double var2) {
      return lerpColor(var0, var1, var2);
   }

   private static void ensureStateMap(RiptideConfig var0) {
      if (var0.hudElements == null) {
         var0.hudElements = new LinkedHashMap<>();
      }
   }

   private static boolean bool(Module var0, String var1, boolean var2) {
      RiptideHudManager.ModuleOptionAccess var3 = new RiptideHudManager.ModuleOptionAccess(var0, var1);
      return !var3.exists ? var2 : Boolean.parseBoolean(var3.value);
   }

   private static boolean parseBool(String var0, boolean var1) {
      return var0 == null ? var1 : Boolean.parseBoolean(var0);
   }

   private static int parseInt(String var0, int var1) {
      try {
         return Integer.parseInt(var0);
      } catch (Exception var3) {
         return var1;
      }
   }

   public static int parseColor(String var0, int var1) {
      if (var0 != null && !var0.isBlank()) {
         String var2 = var0.startsWith("#") ? var0.substring(1) : var0;

         try {
            long var3 = Long.parseLong(var2, 16);
            if (var2.length() == 6) {
               var3 |= 4278190080L;
            }

            return (int)var3;
         } catch (Exception var5) {
            return var1;
         }
      } else {
         return var1;
      }
   }

   private static int clamp(int var0, int var1, int var2) {
      return Math.max(var1, Math.min(var2, var0));
   }

   private static double clamp(double var0, double var2, double var4) {
      return Math.max(var2, Math.min(var4, var0));
   }

   private static boolean hover(int var0, int var1, RiptideHudManager.ElementBounds var2) {
      return hover(var0, var1, var2.x, var2.y, var2.width, var2.height);
   }

   private static boolean hover(int var0, int var1, int var2, int var3, int var4, int var5) {
      return var0 >= var2 && var0 < var2 + var4 && var1 >= var3 && var1 < var3 + var5;
   }

   private static boolean isRightAnchor(String var0) {
      return var0 != null && var0.toUpperCase(Locale.ROOT).contains("RIGHT");
   }

   private static boolean combinedMetricsRowOwns(String var0) {
      return ("tps".equals(var0) || "ping".equals(var0)) && state("fps").enabled;
   }

   private static int stableMetricsWidth(int var0) {
      long var1 = System.currentTimeMillis();
      int var3 = Math.max(1, var0);
      if (var3 >= metricsStableWidth) {
         metricsStableWidth = var3;
         metricsWidthHoldUntil = var1 + 350L;
         return metricsStableWidth;
      } else if (var1 < metricsWidthHoldUntil) {
         return metricsStableWidth;
      } else {
         metricsStableWidth = Math.max(var3, metricsStableWidth - 6);
         return metricsStableWidth;
      }
   }

   private static void drawMergedBackground(
      GuiGraphicsExtractor var0, Font var1, String var2, RiptideHudManager.VisualRect var3, int var4, List<RiptideHudManager.VisualRect> var5
   ) {
      List var6 = backgroundOccluders(var1, var2, var3);
      drawRectWithoutOverlaps(var0, var3, var6, var4);
      drawBackgroundBridges(var0, var3, var5 != null ? var5 : mergeBlockers(var1, var2, var3), var4);
   }

   private static RiptideHudManager.VisualRect bleedToScreenEdge(RiptideHudManager.VisualRect var0) {
      if (var0 == null) {
         return null;
      } else {
         int var1 = anchorScreenWidth();
         int var2 = anchorScreenHeight();
         int var3 = safeZoneXFor(var0.id());
         int var4 = safeZoneYFor(var0.id());
         int var5 = var0.x();
         int var6 = var0.y();
         int var7 = var0.right();
         int var8 = var0.bottom();
         if (var5 == var3) {
            var5 = 0;
         }

         if (var6 == var4) {
            var6 = 0;
         }

         if (var7 == var1 - var3) {
            var7 = var1;
         }

         if (var8 == var2 - var4) {
            var8 = var2;
         }

         var5 = clamp(var5, 0, var1);
         var6 = clamp(var6, 0, var2);
         var7 = clamp(var7, var5, var1);
         var8 = clamp(var8, var6, var2);
         return new RiptideHudManager.VisualRect(var0.id(), var5, var6, Math.max(0, var7 - var5), Math.max(0, var8 - var6));
      }
   }

   private static RiptideHudManager.VisualRect visualChromeRect(RiptideHudManager.VisualRect var0, RiptideHudManager.HudStyle var1) {
      if (var0 == null) {
         return null;
      } else {
         RiptideHudManager.VisualRect var2 = var0;
         if (var1.outline()) {
            byte var3 = 2;
            var2 = new RiptideHudManager.VisualRect(var0.id(), var0.x() - var3, var0.y() - var3, var0.width() + var3 * 2, var0.height() + var3 * 2);
         }

         return bleedToScreenEdge(var2);
      }
   }

   private static Map<String, Integer> buildOrderIndex() {
      HashMap var0 = new HashMap();

      for (int var1 = 0; var1 < ORDER.size(); var1++) {
         var0.put(ORDER.get(var1), var1);
      }

      return var0;
   }

   private static List<RiptideHudManager.HudRectEntry> collectFrameRects(Font var0) {
      boolean var1 = false;

      for (int var2 = 0; var2 < ORDER.size(); var2++) {
         String var3 = ORDER.get(var2);
         RiptideHudManager.CachedHudElement var4 = !combinedMetricsRowOwns(var3) && state(var3).enabled ? cached(var3, var0) : null;
         if (FRAME_RECT_CACHE_ENTRIES[var2] != var4) {
            FRAME_RECT_CACHE_ENTRIES[var2] = var4;
            var1 = true;
         }
      }

      long var11 = hudOccluderSignature();
      if (!var1 && var11 == cachedFrameRectOccluders) {
         return cachedFrameRects;
      } else {
         ArrayList var12 = new ArrayList();

         for (int var5 = 0; var5 < ORDER.size(); var5++) {
            String var6 = ORDER.get(var5);
            RiptideHudManager.CachedHudElement var7 = FRAME_RECT_CACHE_ENTRIES[var5];
            if (var7 != null) {
               RiptideHudManager.HudStyle var8 = var7.style();
               if (var8.background() || var8.outline()) {
                  for (RiptideHudManager.VisualRect var10 : visualRects(var6, var0)) {
                     var12.add(new RiptideHudManager.HudRectEntry(var6, var5, var10, var8.background()));
                  }
               }
            }
         }

         cachedFrameRectOccluders = var11;
         cachedFrameRects = List.copyOf(var12);
         return cachedFrameRects;
      }
   }

   private static long hudOccluderSignature() {
      long var0 = HUD_OCCLUDERS.size();

      for (RiptideHudManager.ElementBounds var3 : HUD_OCCLUDERS) {
         if (var3 != null) {
            var0 = var0 * 31L + var3.x();
            var0 = var0 * 31L + var3.y();
            var0 = var0 * 31L + var3.width();
            var0 = var0 * 31L + var3.height();
         }
      }

      return var0;
   }

   private static List<RiptideHudManager.VisualRect> backgroundOccluders(Font var0, String var1, RiptideHudManager.VisualRect var2) {
      ArrayList var3 = new ArrayList();
      List var4 = frameRects;
      if (var4 != null) {
         int var11 = ORDER_INDEX.getOrDefault(var1, -1);

         for (RiptideHudManager.HudRectEntry var13 : var4) {
            if (var13.background() && var13.orderIndex() <= var11) {
               RiptideHudManager.VisualRect var14 = var13.rect();
               if (!var14.sameBounds(var2) && (var13.orderIndex() != var11 || drawsBefore(var14, var2)) && var14.intersects(var2)) {
                  var3.add(var14);
               }
            }
         }

         return var3;
      } else {
         int var5 = ORDER.indexOf(var1);

         for (String var7 : ORDER) {
            if (!combinedMetricsRowOwns(var7) && state(var7).enabled && boolSetting(var7, "background")) {
               int var8 = ORDER.indexOf(var7);
               if (var8 <= var5) {
                  for (RiptideHudManager.VisualRect var10 : visualRects(var7, var0)) {
                     if (!var10.sameBounds(var2) && (var8 != var5 || drawsBefore(var10, var2)) && var10.intersects(var2)) {
                        var3.add(var10);
                     }
                  }
               }
            }
         }

         return var3;
      }
   }

   private static boolean drawsBefore(RiptideHudManager.VisualRect var0, RiptideHudManager.VisualRect var1) {
      if (var0.y() != var1.y()) {
         return var0.y() < var1.y();
      } else if (var0.x() != var1.x()) {
         return var0.x() < var1.x();
      } else {
         return var0.width() != var1.width() ? var0.width() < var1.width() : var0.height() < var1.height();
      }
   }

   private static void drawRectWithoutOverlaps(GuiGraphicsExtractor var0, RiptideHudManager.VisualRect var1, List<RiptideHudManager.VisualRect> var2, int var3) {
      if (var2.isEmpty()) {
         UiText.fill(var0, var1.x(), var1.y(), var1.right(), var1.bottom(), var3);
      } else {
         ArrayList var4 = new ArrayList();
         var4.add(var1.y());
         var4.add(var1.bottom());

         for (RiptideHudManager.VisualRect var6 : var2) {
            int var7 = clamp(var6.y(), var1.y(), var1.bottom());
            int var8 = clamp(var6.bottom(), var1.y(), var1.bottom());
            if (var7 < var8) {
               var4.add(var7);
               var4.add(var8);
            }
         }

         var4.sort(Integer::compareTo);

         for (int var13 = 0; var13 < var4.size() - 1; var13++) {
            int var14 = (Integer)var4.get(var13);
            int var15 = (Integer)var4.get(var13 + 1);
            if (var14 < var15) {
               ArrayList var16 = new ArrayList();

               for (RiptideHudManager.VisualRect var10 : var2) {
                  if (var10.y() < var15 && var10.bottom() > var14) {
                     int var11 = clamp(var10.x(), var1.x(), var1.right());
                     int var12 = clamp(var10.right(), var1.x(), var1.right());
                     if (var11 < var12) {
                        var16.add(new int[]{var11, var12});
                     }
                  }
               }

               drawLineSegments(var0, var1.x(), var1.right(), var16, 0, (var4x, var5) -> UiText.fill(var0, var4x, var14, var5, var15, var3));
            }
         }
      }
   }

   private static void drawBackgroundBridges(GuiGraphicsExtractor var0, RiptideHudManager.VisualRect var1, List<RiptideHudManager.VisualRect> var2, int var3) {
      for (RiptideHudManager.VisualRect var5 : var2) {
         if (var1.right() <= var5.x() && var5.x() - var1.right() <= 1) {
            int var8 = Math.max(var1.y(), var5.y());
            int var11 = Math.min(var1.bottom(), var5.bottom());
            if (var8 < var11 && var1.right() < var5.x()) {
               UiText.fill(var0, var1.right(), var8, var5.x(), var11, var3);
            }
         } else if (var5.right() <= var1.x() && var1.x() - var5.right() <= 1) {
            int var6 = Math.max(var1.y(), var5.y());
            int var7 = Math.min(var1.bottom(), var5.bottom());
            if (var6 < var7 && var5.right() < var1.x()) {
               UiText.fill(var0, var5.right(), var6, var1.x(), var7, var3);
            }
         }

         if (var1.bottom() <= var5.y() && var5.y() - var1.bottom() <= 1) {
            int var10 = Math.max(var1.x(), var5.x());
            int var13 = Math.min(var1.right(), var5.right());
            if (var10 < var13 && var1.bottom() < var5.y()) {
               UiText.fill(var0, var10, var1.bottom(), var13, var5.y(), var3);
            }
         } else if (var5.bottom() <= var1.y() && var1.y() - var5.bottom() <= 1) {
            int var9 = Math.max(var1.x(), var5.x());
            int var12 = Math.min(var1.right(), var5.right());
            if (var9 < var12 && var5.bottom() < var1.y()) {
               UiText.fill(var0, var9, var5.bottom(), var12, var1.y(), var3);
            }
         }
      }
   }

   private static void outlineMerged(GuiGraphicsExtractor var0, Font var1, String var2, int var3, int var4, int var5, int var6, int var7, int var8) {
      RiptideHudManager.VisualRect var9 = new RiptideHudManager.VisualRect(var2, var3, var4, var5, var6);
      outlineMergedRect(var0, var9, mergeBlockers(var1, var2, var9), var7, var8);
   }

   private static void outlineMergedRect(
      GuiGraphicsExtractor var0, RiptideHudManager.VisualRect var1, List<RiptideHudManager.VisualRect> var2, int var3, int var4
   ) {
      int var5 = Math.max(1, var4);
      int var6 = anchorScreenWidth();
      int var7 = anchorScreenHeight();
      int var8 = clamp(var1.x(), 0, var6);
      int var9 = clamp(var1.right(), 0, var6);
      int var10 = clamp(var1.y(), 0, var7);
      int var11 = clamp(var1.bottom(), 0, var7);
      if (var8 < var9 && var10 < var11) {
         if (var10 > 0) {
            drawMergedHorizontalEdge(var0, var2, var8, var9, var10, var10, var5, var3, true);
         }

         if (var11 < var7) {
            drawMergedHorizontalEdge(var0, var2, var8, var9, Math.max(var10, var11 - var5), var11, var5, var3, false);
         }

         if (var8 > 0) {
            drawMergedVerticalEdge(var0, var2, var10, var11, var8, var8, var5, var3, true);
         }

         if (var9 < var6) {
            drawMergedVerticalEdge(var0, var2, var10, var11, Math.max(var8, var9 - var5), var9, var5, var3, false);
         }
      }
   }

   private static void drawMergedHorizontalEdge(
      GuiGraphicsExtractor var0, List<RiptideHudManager.VisualRect> var1, int var2, int var3, int var4, int var5, int var6, int var7, boolean var8
   ) {
      ArrayList var9 = new ArrayList();

      for (RiptideHudManager.VisualRect var11 : var1) {
         boolean var12 = var8 ? var11.y() < var5 && var11.bottom() >= var5 - 1 : var11.y() <= var5 + 1 && var11.bottom() > var5;
         if (var12) {
            int var13 = Math.max(var2, var11.x());
            int var14 = Math.min(var3, var11.right());
            if (var13 < var14) {
               var9.add(new int[]{var13, var14});
            }
         }
      }

      drawLineSegments(var0, var2, var3, var9, var6, (var4x, var5x) -> UiText.fill(var0, var4x, var4, var5x, var4 + var6, var7));
   }

   private static void drawMergedVerticalEdge(
      GuiGraphicsExtractor var0, List<RiptideHudManager.VisualRect> var1, int var2, int var3, int var4, int var5, int var6, int var7, boolean var8
   ) {
      ArrayList var9 = new ArrayList();

      for (RiptideHudManager.VisualRect var11 : var1) {
         boolean var12 = var8 ? var11.x() < var5 && var11.right() >= var5 - 1 : var11.x() <= var5 + 1 && var11.right() > var5;
         if (var12) {
            int var13 = Math.max(var2, var11.y());
            int var14 = Math.min(var3, var11.bottom());
            if (var13 < var14) {
               var9.add(new int[]{var13, var14});
            }
         }
      }

      drawLineSegments(var0, var2, var3, var9, var6, (var4x, var5x) -> UiText.fill(var0, var4, var4x, var4 + var6, var5x, var7));
   }

   private static List<RiptideHudManager.VisualRect> mergeBlockers(Font var0, String var1, RiptideHudManager.VisualRect var2) {
      ArrayList var3 = new ArrayList();
      List var4 = frameRects;
      if (var4 != null) {
         for (RiptideHudManager.HudRectEntry var10 : var4) {
            if (!var10.id().equals(var1) || "active_modules".equals(var10.id())) {
               RiptideHudManager.VisualRect var11 = var10.rect();
               if (var2 == null || !var11.sameBounds(var2)) {
                  var3.add(var11);
               }
            }
         }

         return var3;
      } else {
         for (String var6 : ORDER) {
            if (!combinedMetricsRowOwns(var6) && state(var6).enabled && mergesWithHudOutline(var6) && (!var6.equals(var1) || "active_modules".equals(var6))) {
               for (RiptideHudManager.VisualRect var8 : visualRects(var6, var0)) {
                  if (var2 == null || !var8.sameBounds(var2)) {
                     var3.add(var8);
                  }
               }
            }
         }

         return var3;
      }
   }

   private static List<RiptideHudManager.VisualRect> visualRects(String var0, Font var1) {
      RiptideHudManager.CachedHudElement var2 = cached(var0, var1);
      if ("active_modules".equals(var0)) {
         List var7 = activeModuleVisualRects(var0, var2);
         if (!HUD_OCCLUDERS.isEmpty() && !var7.isEmpty()) {
            ArrayList var4 = new ArrayList(var7.size());

            for (RiptideHudManager.VisualRect var6 : var7) {
               if (!occluded(var6)) {
                  var4.add(var6);
               }
            }

            return var4;
         } else {
            return var7;
         }
      } else {
         RiptideHudManager.ElementBounds var3 = var2.layout().bounds();
         return occluded(var3)
            ? List.of()
            : List.of(visualChromeRect(new RiptideHudManager.VisualRect(var0, var3.x(), var3.y(), var3.width(), var3.height()), var2.style()));
      }
   }

   private static List<RiptideHudManager.VisualRect> activeModuleVisualRects(String var0, RiptideHudManager.CachedHudElement var1) {
      List var2 = activeModuleContentRects(var0, var1);
      if (var2.isEmpty()) {
         return var2;
      } else {
         ArrayList var3 = new ArrayList(var2.size());
         RiptideHudManager.HudStyle var4 = var1.style();

         for (RiptideHudManager.VisualRect var6 : var2) {
            var3.add(visualChromeRect(var6, var4));
         }

         return var3;
      }
   }

   private static List<RiptideHudManager.VisualRect> activeModuleContentRects(String var0, RiptideHudManager.CachedHudElement var1) {
      if (var1.contentRects != null) {
         return var1.contentRects;
      } else {
         ArrayList var2 = new ArrayList();
         RiptideHudManager.HudLayout var3 = var1.layout();
         RiptideHudManager.HudStyle var4 = var1.style();
         int var5 = var4.activeRowHeight();
         int var6 = var4.lineGap();
         int var7 = var4.stairSnap();
         int var8 = var4.padding();
         int var9 = -1;

         for (int var10 = 0; var10 < var1.lines().size(); var10++) {
            int var11 = var10 < var1.widths().size() ? var1.widths().get(var10) : lineWidth(MC.font, var1.lines().get(var10));
            int var12 = Math.max(1, var11 + var8 * 2);
            if (var9 >= 0 && Math.abs(var9 - var12) <= var7) {
               var12 = var9;
            } else {
               var9 = var12;
            }

            int var13 = alignedRowX(var4.alignment(), var3.x(), var3.unscaledWidth(), var12);
            int var14 = var3.y() + var10 * (var5 + var6);
            var2.add(new RiptideHudManager.VisualRect(var0, var13, var14, var12, var5));
         }

         var1.contentRects = List.copyOf(var2);
         return var1.contentRects;
      }
   }

   private static int alignedRowX(String var0, int var1, int var2, int var3) {
      if ("Center".equals(var0)) {
         return var1 + Math.max(0, (var2 - var3) / 2);
      } else {
         return "Right".equals(var0) ? var1 + Math.max(0, var2 - var3) : var1;
      }
   }

   private static boolean mergesWithHudOutline(String var0) {
      RiptideHudManager.HudStyle var1 = cached(var0, MC.font).style();
      return var1.background() || var1.outline();
   }

   private static boolean occluded(RiptideHudManager.ElementBounds var0) {
      if (var0 != null && !HUD_OCCLUDERS.isEmpty()) {
         for (RiptideHudManager.ElementBounds var2 : HUD_OCCLUDERS) {
            if (var0.intersects(var2)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean occluded(RiptideHudManager.VisualRect var0) {
      if (var0 != null && !HUD_OCCLUDERS.isEmpty()) {
         for (RiptideHudManager.ElementBounds var2 : HUD_OCCLUDERS) {
            if (var0.x() < var2.right() && var0.right() > var2.x() && var0.y() < var2.bottom() && var0.bottom() > var2.y()) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static int computeDodge(RiptideHudManager.ElementBounds var0) {
      if (var0 != null && !HUD_OCCLUDERS.isEmpty() && occluded(var0)) {
         int var1 = anchorScreenHeight();
         int var2 = dodgeInDir(var0, 1, var1);
         int var3 = dodgeInDir(var0, -1, var1);
         boolean var4 = var2 != Integer.MIN_VALUE;
         boolean var5 = var3 != Integer.MIN_VALUE;
         if (var4 && var5) {
            return Math.abs(var2) <= Math.abs(var3) ? var2 : var3;
         } else if (var4) {
            return var2;
         } else {
            return var5 ? var3 : 0;
         }
      } else {
         return 0;
      }
   }

   private static int dodgeInDir(RiptideHudManager.ElementBounds var0, int var1, int var2) {
      int var3 = 0;

      for (int var4 = 0; var4 < 32; var4++) {
         RiptideHudManager.ElementBounds var5 = new RiptideHudManager.ElementBounds(var0.id(), var0.x(), var0.y() + var3, var0.width(), var0.height());
         RiptideHudManager.ElementBounds var6 = null;

         for (RiptideHudManager.ElementBounds var8 : HUD_OCCLUDERS) {
            if (var5.intersects(var8)) {
               var6 = var8;
               break;
            }
         }

         if (var6 == null) {
            int var10 = var0.y() + var3;
            return var10 >= 0 && var10 + var0.height() <= var2 ? var3 : Integer.MIN_VALUE;
         }

         int var9 = var1 > 0 ? var6.bottom() - var0.y() + 1 : var6.y() - var0.height() - var0.y() - 1;
         if (var1 > 0 ? var9 <= var3 : var9 >= var3) {
            var9 = var3 + var1;
         }

         var3 = var9;
         int var11 = var0.y() + var9;
         if (var11 < 0 || var11 + var0.height() > var2) {
            return Integer.MIN_VALUE;
         }
      }

      return Integer.MIN_VALUE;
   }

   private static void drawLineSegments(GuiGraphicsExtractor var0, int var1, int var2, List<int[]> var3, int var4, RiptideHudManager.SegmentDrawer var5) {
      if (var1 < var2) {
         if (var3.isEmpty()) {
            var5.draw(var1, var2);
         } else {
            var3.sort(Comparator.comparingInt(var0x -> var0x[0]));
            int var6 = var1;

            for (int[] var8 : var3) {
               int var9 = clamp(var8[0], var1, var2);
               int var10 = clamp(var8[1], var1, var2);
               if (var6 < var9) {
                  var5.draw(var6, Math.min(var2, var9 + var4));
               }

               var6 = Math.max(var6, var10);
               if (var6 >= var2) {
                  return;
               }
            }

            if (var6 < var2) {
               var5.draw(Math.max(var1, var6 - var4), var2);
            }
         }
      }
   }

   private static void outline(GuiGraphicsExtractor var0, int var1, int var2, int var3, int var4, int var5, int var6) {
      int var7 = Math.max(1, var6);
      int var8 = anchorScreenWidth();
      int var9 = anchorScreenHeight();
      int var10 = clamp(var1, 0, var8);
      int var11 = clamp(var2, 0, var9);
      int var12 = clamp(var1 + var3, var10, var8);
      int var13 = clamp(var2 + var4, var11, var9);
      if (var10 < var12 && var11 < var13) {
         int var14 = Math.min(var7, Math.max(1, Math.min(var12 - var10, var13 - var11)));
         UiText.fill(var0, var10, var11, var12, Math.min(var13, var11 + var14), var5);
         if (var13 - var11 > var14) {
            UiText.fill(var0, var10, Math.max(var11, var13 - var14), var12, var13, var5);
         }

         UiText.fill(var0, var10, var11, Math.min(var12, var10 + var14), var13, var5);
         if (var12 - var10 > var14) {
            UiText.fill(var0, Math.max(var10, var12 - var14), var11, var12, var13, var5);
         }
      }
   }

   private static RiptideHudManager.HudStyle computeStyle(String var0) {
      return new RiptideHudManager.HudStyle(
         boolSetting(var0, "background"),
         boolSetting(var0, "outline"),
         boolSetting(var0, "shadow"),
         Math.max(1, intSetting(var0, "outline-width", 1)),
         padding(var0),
         verticalPadding(var0),
         lineGap(var0),
         lineHeight(var0),
         activeModuleRowHeight(var0),
         activeModuleStairSnap(var0),
         setting(var0, "alignment"),
         color(var0, "background-color", 840505625),
         color(var0, "outline-color", "active_modules".equals(var0) ? -7397596 : -9109504),
         color(var0, "accent-color", -50373),
         color(var0, "label-color", -4743522)
      );
   }

   private record ArtDecodeRequest(String path, long mtime, long size, String label) {
   }

   private record ArtDecodeResult(RiptideHudManager.ArtDecodeRequest request, NativeImage[] chain) {
   }

   private static final class CachedHudElement {
      private final RiptideHudManager.HudCacheKey signature;
      private final List<RiptideHudManager.HudLine> lines;
      private final List<Integer> widths;
      private final RiptideHudManager.HudLayout layout;
      private final RiptideHudManager.HudStyle style;
      private long nextSignatureCheckAtMs;
      private List<RiptideHudManager.VisualRect> contentRects;

      private CachedHudElement(
         RiptideHudManager.HudCacheKey var1,
         List<RiptideHudManager.HudLine> var2,
         List<Integer> var3,
         RiptideHudManager.HudLayout var4,
         RiptideHudManager.HudStyle var5
      ) {
         this.signature = var1;
         this.lines = var2;
         this.widths = var3;
         this.layout = var4;
         this.style = var5;
      }

      RiptideHudManager.HudCacheKey signature() {
         return this.signature;
      }

      List<RiptideHudManager.HudLine> lines() {
         return this.lines;
      }

      List<Integer> widths() {
         return this.widths;
      }

      RiptideHudManager.HudLayout layout() {
         return this.layout;
      }

      RiptideHudManager.HudStyle style() {
         return this.style;
      }
   }

   public record ElementBounds(String id, int x, int y, int width, int height) {
      public int right() {
         return this.x + this.width;
      }

      public int bottom() {
         return this.y + this.height;
      }

      public boolean intersects(RiptideHudManager.ElementBounds var1) {
         return var1 != null && this.x < var1.right() && this.right() > var1.x() && this.y < var1.bottom() && this.bottom() > var1.y();
      }
   }

   private record HudCacheKey(
      boolean enabled,
      String anchor,
      int x,
      int y,
      int settingsHash,
      long timeBucket,
      int screenWidth,
      int screenHeight,
      int playerId,
      int activeRevision,
      int moduleRevision,
      int fontIdentity,
      int themeRevision
   ) {
   }

   public record HudLayout(String id, int x, int y, int unscaledWidth, int unscaledHeight, double scale) {
      public int scaledWidth() {
         return (int)Math.ceil(this.unscaledWidth * this.scale);
      }

      public int scaledHeight() {
         return (int)Math.ceil(this.unscaledHeight * this.scale);
      }

      public RiptideHudManager.ElementBounds bounds() {
         return new RiptideHudManager.ElementBounds(this.id, this.x, this.y, this.scaledWidth(), this.scaledHeight());
      }
   }

   private static final class HudLine {
      private final List<RiptideHudManager.HudSegment> segments = new ArrayList<>();
      private int[] segmentWidths;

      void add(String var1, int var2) {
         if (var1 != null && !var1.isEmpty()) {
            this.segments.add(new RiptideHudManager.HudSegment(var1, var2));
         }
      }

      String plainText() {
         StringBuilder var1 = new StringBuilder();

         for (RiptideHudManager.HudSegment var3 : this.segments) {
            var1.append(var3.text);
         }

         return var1.toString();
      }
   }

   private record HudRectEntry(String id, int orderIndex, RiptideHudManager.VisualRect rect, boolean background) {
   }

   private record HudSegment(String text, int color) {
   }

   private record HudStyle(
      boolean background,
      boolean outline,
      boolean shadow,
      int outlineWidth,
      int padding,
      int verticalPadding,
      int lineGap,
      int lineHeight,
      int activeRowHeight,
      int stairSnap,
      String alignment,
      int backgroundColor,
      int outlineColor,
      int accentColor,
      int labelColor
   ) {
   }

   private record ModuleOptionAccess(boolean exists, String value) {
      ModuleOptionAccess(Module var1, String var2) {
         this(var1 != null && var1.setting(var2) != null, var1 != null && var1.setting(var2) != null ? var1.value(var2) : "");
      }
   }

   private static final class MusicCardGeom {
      int blockH;
      int textX;
      int artistClip;
      int titleClip;
      int artistY;
      int titleY;
      int timeY;
      int timeX;
      int progressY;
      int progressW;
      int contentH;
   }

   private record RainbowParams(
      String mode,
      float basePhase,
      float spread,
      float saturation,
      float brightness,
      int flatColor,
      int gradientStart,
      int gradientEnd,
      int valueColor,
      double gradientRows
   ) {
   }

   @FunctionalInterface
   private interface SegmentDrawer {
      void draw(int var1, int var2);
   }

   public record SpotifyArt(Identifier id, int width, int height) {
   }

   private static final class SpotifyArtTexture extends AbstractTexture {
      private NativeImage[] levels;

      private SpotifyArtTexture(NativeImage[] var1) {
         this.levels = var1;
         NativeImage var2 = var1[0];
         this.texture = RenderSystem.getDevice().createTexture("spotify_art", 5, GpuFormat.RGBA8_UNORM, var2.getWidth(), var2.getHeight(), 1, var1.length);
         this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, true);
         this.textureView = RenderSystem.getDevice().createTextureView(this.texture);
         this.uploadChain();
      }

      private void uploadChain() {
         for (int var1 = 0; var1 < this.levels.length; var1++) {
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, this.levels[var1], var1, 0, 0, 0);
         }
      }

      private void update(NativeImage[] var1) {
         NativeImage[] var2 = this.levels;
         this.levels = var1;
         this.uploadChain();

         for (NativeImage var6 : var2) {
            var6.close();
         }
      }

      public void close() {
         for (NativeImage var4 : this.levels) {
            var4.close();
         }

         super.close();
      }
   }

   private static final class SpotifyTextCache {
      String title = "";
      String artist = "";
      int titleWidth;
      int artistWidth;
   }

   private record VisualRect(String id, int x, int y, int width, int height) {
      int right() {
         return this.x + this.width;
      }

      int bottom() {
         return this.y + this.height;
      }

      boolean sameBounds(RiptideHudManager.VisualRect var1) {
         return var1 != null && this.x == var1.x && this.y == var1.y && this.width == var1.width && this.height == var1.height;
      }

      boolean intersects(RiptideHudManager.VisualRect var1) {
         return var1 != null && this.x < var1.right() && this.right() > var1.x() && this.y < var1.bottom() && this.bottom() > var1.y();
      }
   }
}
