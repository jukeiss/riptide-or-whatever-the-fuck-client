package riptide.modules;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import riptide.util.RiptideChamsHit;
import riptide.util.RiptideChamsHolder;
import riptide.util.RiptideOverlayManager;
import riptide.util.SodiumTerrainPassGuard;

public final class ModuleRenderUtil {
   private static final Minecraft MC = Minecraft.getInstance();
   private static volatile ModuleRenderUtil.XraySnapshot xraySnapshot = ModuleRenderUtil.XraySnapshot.inactive(-1, false);
   private static volatile ModuleRenderUtil.FullbrightSnapshot fullbrightSnapshot = ModuleRenderUtil.FullbrightSnapshot.inactive(-1, false);
   private static volatile ModuleRenderUtil.WorldDarkenSnapshot worldDarkenSnapshot = ModuleRenderUtil.WorldDarkenSnapshot.inactive(-1, false);
   private static volatile ModuleRenderUtil.EntityRenderSnapshot espSnapshot = ModuleRenderUtil.EntityRenderSnapshot.inactive("esp", -1, false);
   private static volatile ModuleRenderUtil.ItemEspSnapshot itemEspSnapshot = ModuleRenderUtil.ItemEspSnapshot.inactive(-1, false);
   private static volatile ModuleRenderUtil.EntityRenderSnapshot tracerSnapshot = ModuleRenderUtil.EntityRenderSnapshot.inactive("tracers", -1, false);
   private static volatile ModuleRenderUtil.ChamsSnapshot chamsSnapshot = ModuleRenderUtil.ChamsSnapshot.inactive(-1, false);
   private static volatile boolean xrayRenderWork;
   private static volatile boolean worldDarkenWork;
   private static volatile boolean fullbrightGammaWork;
   private static volatile boolean fullbrightLuminanceWork;
   private static volatile boolean brightLightmapWork;
   private static volatile boolean worldTracerWork;
   private static volatile boolean outlineWork;
   private static volatile boolean esp2dWork;
   private static final AtomicBoolean WORLD_REFRESH_QUEUED = new AtomicBoolean();
   private static final ClassValue<ModuleRenderUtil.SodiumQuadAccess> SODIUM_QUAD_ACCESS = new ClassValue<ModuleRenderUtil.SodiumQuadAccess>() {
      protected ModuleRenderUtil.SodiumQuadAccess computeValue(Class<?> type) {
         try {
            return new ModuleRenderUtil.SodiumQuadAccess(
               type.getMethod("baseColor", int.class),
               type.getMethod("setColor", int.class, int.class),
               type.getMethod("setRenderType", ChunkSectionLayer.class)
            );
         } catch (ReflectiveOperationException var3) {
            return ModuleRenderUtil.SodiumQuadAccess.UNAVAILABLE;
         }
      }
   };
   private static final ClassValue<Optional<Method>> SODIUM_REGION_RESOURCES = new ClassValue<Optional<Method>>() {
      protected Optional<Method> computeValue(Class<?> type) {
         try {
            return Optional.of(type.getMethod("getResources"));
         } catch (ReflectiveOperationException var3) {
            return Optional.empty();
         }
      }
   };
   private static final Object SODIUM_SORT_TYPE_UNAVAILABLE = new Object();
   private static volatile Object sodiumNoneSortType;
   private static final Direction[] DIRECTIONS = Direction.values();
   private static final ThreadLocal<ModuleRenderUtil.ExposureMemo> EXPOSURE_MEMO = ThreadLocal.withInitial(ModuleRenderUtil.ExposureMemo::new);

   private ModuleRenderUtil() {
   }

   public static boolean xrayActive() {
      return xrayRenderWork;
   }

   public static int effectiveRenderChunkRadius() {
      return MC != null && MC.options != null ? Math.max(1, MC.options.getEffectiveRenderDistance()) : 8;
   }

   public static boolean hasXrayRenderWork() {
      return xrayRenderWork;
   }

   public static boolean hasWorldDarkenWork() {
      return worldDarkenWork;
   }

   public static int worldDarkenTint(BlockState state, BlockPos pos) {
      ModuleRenderUtil.WorldDarkenSnapshot snapshot = worldDarkenSnapshot();
      if (snapshot.active() && state != null && !state.isAir()) {
         boolean matched = matchesWorldDarkenList(state, snapshot);
         boolean darken = snapshot.whitelist() ? matched : !matched;
         return darken ? snapshot.tint() : -1;
      } else {
         return -1;
      }
   }

   public static boolean shouldBypassOcclusionCulling() {
      return xrayRenderWork || PackFreecamState.isActive();
   }

   public static boolean shouldUseFullbrightGamma() {
      return fullbrightGammaWork;
   }

   public static boolean shouldApplyFullbrightLuminance() {
      return fullbrightLuminanceWork;
   }

   public static boolean hasFullbrightLuminanceWork() {
      return fullbrightLuminanceWork;
   }

   public static boolean shouldUseBrightLightmap() {
      return brightLightmapWork;
   }

   public static boolean hasBrightLightmapWork() {
      return brightLightmapWork;
   }

   public static int fullbrightLuminance(LightLayer lightLayer) {
      ModuleRenderUtil.FullbrightSnapshot snapshot = fullbrightSnapshot();
      if (snapshot.luminance() && lightLayer != null) {
         return lightLayer == LightLayer.SKY ? snapshot.skyLightValue() : (lightLayer == LightLayer.BLOCK ? snapshot.blockLightValue() : 0);
      } else {
         return 0;
      }
   }

   public static boolean shouldRenderXrayBlock(BlockAndTintGetter level, BlockPos pos, BlockState state) {
      return !isXrayBlocked(xraySnapshot(), level, state, pos);
   }

   public static int xrayAlpha(BlockAndTintGetter level, BlockPos pos, BlockState state) {
      ModuleRenderUtil.XraySnapshot snapshot = xraySnapshot();
      if (!snapshot.active() || state == null || state.isAir()) {
         return -1;
      } else if (!isXrayBlocked(snapshot, level, state, pos)) {
         return -1;
      } else {
         return snapshot.irisShaderPackInUse() ? 0 : snapshot.opacity();
      }
   }

   public static int xrayAlpha(BlockState state, BlockPos pos) {
      return xrayAlpha(null, pos, state);
   }

   public static boolean modifyXrayFace(BlockAndTintGetter level, BlockState state, Direction direction, BlockPos originalPos, boolean original) {
      ModuleRenderUtil.XraySnapshot snapshot = xraySnapshot();
      if (!snapshot.active()) {
         return original;
      } else if (!original && !isXrayBlocked(snapshot, level, state, originalPos)) {
         BlockPos adjPos = originalPos == null ? null : originalPos.relative(direction);
         BlockState adjState = level != null && adjPos != null ? level.getBlockState(adjPos) : null;
         return !hidesXrayFace(snapshot, level, adjState, adjPos, direction);
      } else {
         return original;
      }
   }

   private static boolean hidesXrayFace(ModuleRenderUtil.XraySnapshot snapshot, BlockGetter level, BlockState adjState, BlockPos adjPos, Direction direction) {
      return adjState != null
         && adjState.isSolidRender()
         && adjState.getFaceOcclusionShape(direction.getOpposite()) == Shapes.block()
         && !isXrayBlocked(snapshot, level, adjState, adjPos);
   }

   public static boolean shouldForceXrayFace(BlockState state, BlockState neighborState, Direction direction) {
      ModuleRenderUtil.XraySnapshot snapshot = xraySnapshot();
      return snapshot.active() && !isXrayBlocked(snapshot, null, state, null) ? !hidesXrayFace(snapshot, null, neighborState, null, direction) : false;
   }

   public static boolean isXrayBlocked(BlockState state, BlockPos pos) {
      return isXrayBlocked(xraySnapshot(), state, pos);
   }

   public static int xrayFluidAlpha(BlockAndTintGetter level, BlockPos pos, FluidState fluidState) {
      return xrayFluidAlpha(fluidState, pos);
   }

   public static int xrayFluidAlpha(FluidState fluidState, BlockPos pos) {
      ModuleRenderUtil.XraySnapshot snapshot = xraySnapshot();
      if (snapshot.active() && fluidState != null && !fluidState.isEmpty()) {
         boolean water = fluidState.is(FluidTags.WATER);
         boolean lava = fluidState.is(FluidTags.LAVA);
         String fluidBlock = snapshot.fluidOpacityMode();

         boolean apply = switch (fluidBlock) {
            case "None" -> false;
            case "Water" -> water;
            case "Lava" -> lava;
            default -> water || lava;
         };
         if (!apply) {
            return -1;
         } else {
            BlockState fluidBlock = fluidState.createLegacyBlock();
            if (!isXrayBlocked(snapshot, fluidBlock, pos)) {
               return -1;
            } else {
               return snapshot.irisShaderPackInUse() ? 0 : snapshot.opacity();
            }
         }
      } else {
         return -1;
      }
   }

   public static boolean shouldForceXrayFluidSides() {
      return xrayActive();
   }

   public static boolean xrayUsesShaderCullMode() {
      ModuleRenderUtil.XraySnapshot snapshot = xraySnapshot();
      return snapshot.active() && snapshot.irisShaderPackInUse();
   }

   public static boolean shouldKeepXrayFluidSide(BlockState neighborState) {
      return isXrayBlocked(xraySnapshot(), neighborState, null);
   }

   public static int sodiumFullLight() {
      return 4095;
   }

   public static int sodiumBlockLight(int current, BlockState state, BlockPos pos) {
      ModuleRenderUtil.XraySnapshot snapshot = xraySnapshot();
      if (snapshot.active() && !isXrayBlocked(snapshot, state, pos)) {
         return sodiumFullLight();
      } else {
         ModuleRenderUtil.FullbrightSnapshot fullbright = fullbrightSnapshot();
         return fullbright.luminance() && "BLOCK".equals(fullbright.lightType()) ? Math.max(current, fullbright.minimumLightLevel()) : current;
      }
   }

   public static boolean sodiumRegionHasNoResources(Object region) {
      if (region == null) {
         return true;
      } else {
         Method getResources = SODIUM_REGION_RESOURCES.get(region.getClass()).orElse(null);
         if (getResources == null) {
            return false;
         } else {
            try {
               return getResources.invoke(region) == null;
            } catch (Throwable var3) {
               return false;
            }
         }
      }
   }

   public static void applySodiumQuadAlpha(Object quad, int alpha) {
      if (quad != null && alpha >= 0) {
         SODIUM_QUAD_ACCESS.get(quad.getClass()).applyAlpha(quad, alpha);
      }
   }

   public static void applySodiumQuadTint(Object quad, int tint) {
      if (quad != null) {
         SODIUM_QUAD_ACCESS.get(quad.getClass()).applyTint(quad, tint);
      }
   }

   public static void applySodiumQuadRenderLayer(Object quad, ChunkSectionLayer layer) {
      if (quad != null && layer != null) {
         SODIUM_QUAD_ACCESS.get(quad.getClass()).applyRenderLayer(quad, layer);
      }
   }

   public static Object sodiumTranslucentMaterial(Object fallback) {
      try {
         Class<?> defaultMaterials = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials");
         Field translucent = defaultMaterials.getField("TRANSLUCENT");
         Object value = translucent.get(null);
         return value == null ? fallback : value;
      } catch (Throwable var4) {
         return fallback;
      }
   }

   public static Object sodiumNoneSortType(Object fallback) {
      Object resolved = sodiumNoneSortType;
      if (resolved == null) {
         resolved = resolveSodiumNoneSortType();
         sodiumNoneSortType = resolved;
      }

      return resolved == SODIUM_SORT_TYPE_UNAVAILABLE ? fallback : resolved;
   }

   private static Object resolveSodiumNoneSortType() {
      try {
         Class<?> sortType = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortType");
         Object[] constants = sortType.getEnumConstants();
         if (constants != null) {
            for (Object constant : constants) {
               if (constant instanceof Enum<?> value && "NONE".equals(value.name())) {
                  return constant;
               }
            }
         }
      } catch (Throwable var7) {
      }

      return SODIUM_SORT_TYPE_UNAVAILABLE;
   }

   public static void refreshWorldRenderer() {
      if (MC != null && MC.level != null) {
         WORLD_REFRESH_QUEUED.set(true);
      }
   }

   public static void flushWorldRendererRefresh() {
      if (WORLD_REFRESH_QUEUED.getAndSet(false)) {
         if (MC != null && MC.level != null && MC.levelExtractor != null) {
            SodiumTerrainPassGuard.armForTransition();
            MC.levelExtractor.allChanged();
         }
      }
   }

   public static int applyFullbrightLuminance(BlockAndLightGetter level, BlockPos pos, int packedBrightness) {
      ModuleRenderUtil.FullbrightSnapshot snapshot = fullbrightSnapshot();
      if (!snapshot.luminance()) {
         return packedBrightness;
      } else {
         int sky = snapshot.skyLightValue();
         int block = snapshot.blockLightValue();
         int originalSky = level != null && pos != null ? level.getBrightness(LightLayer.SKY, pos) : 0;
         int originalBlock = level != null && pos != null ? level.getBrightness(LightLayer.BLOCK, pos) : 0;
         return LightCoordsUtil.pack(Math.max(block, originalBlock), Math.max(sky, originalSky));
      }
   }

   public static boolean shouldTrace(Entity entity) {
      return shouldRenderEntity(tracerSnapshot(), entity);
   }

   public static boolean hasWorldTracerWork() {
      return worldTracerWork;
   }

   public static boolean shouldEsp(Entity entity) {
      if (entity instanceof ItemEntity) {
         return false;
      } else {
         ModuleRenderUtil.EntityRenderSnapshot snapshot = espSnapshot();
         if (!snapshot.enabled()) {
            return false;
         } else {
            return shouldSuppressEspForUi() ? false : shouldRenderEntity(snapshot, entity) && espFadeAlpha(snapshot, entity) > 0.0;
         }
      }
   }

   public static int tracerColor(Entity entity) {
      return entityColor(tracerSnapshot(), entity, -855638017);
   }

   public static int espColor(Entity entity) {
      ModuleRenderUtil.EntityRenderSnapshot snapshot = espSnapshot();
      int color = entityColor(snapshot, entity, -855638017);
      return withAlphaMultiplier(color, espFadeAlpha(snapshot, entity));
   }

   public static int espOutlineColor(Entity entity) {
      return espColor(entity) | 0xFF000000;
   }

   public static boolean hasChamsWork() {
      return chamsSnapshot().enabled();
   }

   public static void applyChams(Entity entity, EntityRenderState state) {
      if (state instanceof RiptideChamsHolder holder) {
         ModuleRenderUtil.ChamsSnapshot snapshot = chamsSnapshot();
         if (!snapshot.enabled() || entity instanceof ItemEntity || !shouldRenderChams(snapshot, entity)) {
            holder.riptide$setChams(false, 0, 0);
         } else if (snapshot.hitEnabled() && RiptideChamsHit.isFlashing(entity)) {
            int hit = snapshot.hitColor() | 0xFF000000;
            holder.riptide$setChams(true, hit, hit);
         } else if (snapshot.textureMode()) {
            holder.riptide$setChams(true, 16777215, 16777215);
         } else {
            int alpha = Math.round(255.0F * Math.max(0.0F, Math.min(100.0F, (float)snapshot.opacity())) / 100.0F);
            holder.riptide$setChams(true, alpha << 24 | snapshot.visibleColor() & 16777215, alpha << 24 | snapshot.occludedColor() & 16777215);
         }
      }
   }

   private static boolean shouldRenderChams(ModuleRenderUtil.ChamsSnapshot snapshot, Entity entity) {
      if (snapshot == null || !snapshot.enabled()) {
         return false;
      } else if (MC == null || MC.player == null || entity == null) {
         return false;
      } else if (entity == MC.player) {
         return false;
      } else if (RiptideAntiBot.suppress(entity)) {
         return false;
      } else if (entity == MC.getCameraEntity() && MC.options.getCameraType().isFirstPerson()) {
         return false;
      } else {
         Vec3 camera = MC.gameRenderer.mainCamera().position();
         if (!entity.shouldRender(camera.x, camera.y, camera.z)) {
            return false;
         } else {
            double maxDistance = snapshot.maxDistance();
            if (maxDistance > 0.0 && entity.distanceToSqr(MC.player) > maxDistance * maxDistance) {
               return false;
            } else {
               return snapshot.entityIds().isEmpty() && snapshot.entityPaths().isEmpty()
                  ? true
                  : snapshot.matchCache().computeIfAbsent(entity.getType(), type -> {
                     Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                     return snapshot.entityIds().contains(id) || snapshot.entityPaths().contains(id.getPath());
                  });
            }
         }
      }
   }

   public static boolean shouldItemEsp(Entity entity) {
      if (!(entity instanceof ItemEntity itemEntity)) {
         return false;
      } else {
         ModuleRenderUtil.ItemEspSnapshot snapshot = itemEspSnapshot();
         if (!snapshot.enabled()) {
            return false;
         } else {
            return shouldSuppressEspForUi() ? false : shouldRenderItem(snapshot, itemEntity) && itemEspFadeAlpha(snapshot, itemEntity) > 0.0;
         }
      }
   }

   public static int itemEspColor(Entity entity) {
      ModuleRenderUtil.ItemEspSnapshot snapshot = itemEspSnapshot();
      if (entity instanceof ItemEntity itemEntity) {
         int base = snapshot.dynamicColor() ? dynamicItemColor(snapshot, itemEntity.getItem(), snapshot.color()) : snapshot.color();
         return withAlphaMultiplier(base, itemEspFadeAlpha(snapshot, itemEntity));
      } else {
         return snapshot.color();
      }
   }

   private static int dynamicItemColor(ModuleRenderUtil.ItemEspSnapshot snapshot, ItemStack stack, int fallbackArgb) {
      int alpha = fallbackArgb >>> 24 & 0xFF;
      int rgb = stack != null && !stack.isEmpty() ? snapshot.rgbCache().computeIfAbsent(stack.getItem(), ignored -> computeItemRgb(stack)) : 16766826;
      return alpha << 24 | rgb & 16777215;
   }

   private static int computeItemRgb(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
         String path = id == null ? "" : id.getPath();
         if (path.contains("netherite")) {
            return 12038057;
         } else if (path.contains("diamond")) {
            return 4907990;
         } else if (path.contains("emerald")) {
            return 3137626;
         } else if (path.contains("lapis")) {
            return 2774496;
         } else if (path.contains("redstone")) {
            return 16724016;
         } else if (path.contains("amethyst")) {
            return 11832562;
         } else if (path.contains("copper")) {
            return 14715482;
         } else if (path.contains("gold") || path.contains("golden") || path.contains("raw_gold")) {
            return 16573003;
         } else if (path.contains("iron")) {
            return 14342874;
         } else if (path.contains("coal")) {
            return 2894892;
         } else if (path.contains("quartz")) {
            return 15919840;
         } else if (path.contains("melon")) {
            return 6801483;
         } else if (path.contains("pumpkin")) {
            return 14712864;
         } else if (path.contains("ender")) {
            return 1226650;
         } else if (path.contains("blaze")) {
            return 16758062;
         } else if (path.contains("slime")) {
            return 8377434;
         } else if (path.contains("bone")) {
            return 15328464;
         } else if (!path.contains("netherrack") && !path.contains("nether_brick")) {
            if (stack.getItem() instanceof BlockItem blockItem) {
               try {
                  int col = blockItem.getBlock().defaultMapColor().col;
                  if (col != 0) {
                     return col & 16777215;
                  }
               } catch (Throwable var5) {
               }
            }

            int hash = id == null ? path.hashCode() : id.toString().hashCode();
            float hue = (hash & 2147483647) % 360 / 360.0F;
            return Color.HSBtoRGB(hue, 0.65F, 0.95F) & 16777215;
         } else {
            return 8010555;
         }
      } else {
         return 16766826;
      }
   }

   public static int itemEspOutlineColor(Entity entity) {
      return itemEspColor(entity) | 0xFF000000;
   }

   public static int itemOutlineColorOrZero(Entity entity) {
      if (entity instanceof ItemEntity itemEntity) {
         ModuleRenderUtil.ItemEspSnapshot snapshot = itemEspSnapshot();
         if (!snapshot.enabled() || !"Shader".equals(snapshot.mode()) || shouldSuppressEspForUi()) {
            return 0;
         } else if (!shouldRenderItem(snapshot, itemEntity)) {
            return 0;
         } else {
            double fade = itemEspFadeAlpha(snapshot, itemEntity);
            if (fade <= 0.0) {
               return 0;
            } else {
               int base = snapshot.dynamicColor() ? dynamicItemColor(snapshot, itemEntity.getItem(), snapshot.color()) : snapshot.color();
               return withAlphaMultiplier(base, fade) | 0xFF000000;
            }
         }
      } else {
         return 0;
      }
   }

   public static int entityOutlineColorOrZero(Entity entity) {
      if (entity instanceof ItemEntity) {
         return 0;
      } else {
         ModuleRenderUtil.EntityRenderSnapshot snapshot = espSnapshot();
         if (!snapshot.enabled() || !"Shader".equals(snapshot.mode()) || shouldSuppressEspForUi()) {
            return 0;
         } else if (!shouldRenderEntity(snapshot, entity)) {
            return 0;
         } else {
            double fade = espFadeAlpha(snapshot, entity);
            return fade <= 0.0 ? 0 : withAlphaMultiplier(entityColor(snapshot, entity, -855638017), fade) | 0xFF000000;
         }
      }
   }

   public static boolean shouldUseItemOutline() {
      ModuleRenderUtil.ItemEspSnapshot snapshot = itemEspSnapshot();
      return snapshot.enabled() && "Shader".equals(snapshot.mode()) ? !shouldSuppressEspForUi() : false;
   }

   public static boolean shouldUseEntityOutline() {
      ModuleRenderUtil.EntityRenderSnapshot snapshot = espSnapshot();
      return snapshot.enabled() && "Shader".equals(snapshot.mode()) ? !shouldSuppressEspForUi() : false;
   }

   public static boolean hasAnyOutlineWork() {
      return outlineWork;
   }

   public static boolean has2dEspWork() {
      return esp2dWork;
   }

   public static void refreshFastFlags() {
      int revision = ModuleRegistry.revision();
      boolean hidden = PackHideState.isActive();
      synchronized (ModuleRenderUtil.class) {
         xraySnapshot = buildXraySnapshot(revision, hidden);
         worldDarkenSnapshot = buildWorldDarkenSnapshot(revision, hidden);
         fullbrightSnapshot = buildFullbrightSnapshot(revision, hidden);
         espSnapshot = buildEntityRenderSnapshot("esp", revision, hidden, false);
         itemEspSnapshot = buildItemEspSnapshot(revision, hidden);
         tracerSnapshot = buildEntityRenderSnapshot("tracers", revision, hidden, true);
         chamsSnapshot = buildChamsSnapshot(revision, hidden);
         xrayRenderWork = xraySnapshot.active();
         worldDarkenWork = worldDarkenSnapshot.active();
         SodiumTerrainPassGuard.setXrayActive(xrayRenderWork);
         fullbrightGammaWork = fullbrightSnapshot.gamma();
         fullbrightLuminanceWork = fullbrightSnapshot.luminance();
         brightLightmapWork = fullbrightGammaWork || xrayRenderWork;
         worldTracerWork = tracerSnapshot.enabled();
         outlineWork = itemEspSnapshot.enabled() && "Shader".equals(itemEspSnapshot.mode()) || espSnapshot.enabled() && "Shader".equals(espSnapshot.mode());
         esp2dWork = espSnapshot.enabled() && "2D".equals(espSnapshot.mode());
      }
   }

   public static boolean shouldSuppressEspForUi() {
      if (PackHideState.isActive()) {
         return true;
      } else if (MC == null) {
         return false;
      } else if (MC.gui.screen() != null && !(MC.gui.screen() instanceof ChatScreen) && !(MC.gui.screen() instanceof InBedChatScreen)) {
         return true;
      } else {
         RiptideOverlayManager overlays = RiptideOverlayManager.get();
         return overlays.hasRegisteredOverlays() && overlays.hasVisibleOverlay();
      }
   }

   private static boolean shouldRenderEntity(ModuleRenderUtil.EntityRenderSnapshot snapshot, Entity entity) {
      if (snapshot == null || !snapshot.enabled()) {
         return false;
      } else if (MC == null || MC.player == null || entity == null) {
         return false;
      } else if (entity == MC.player) {
         return false;
      } else if (RiptideAntiBot.suppress(entity)) {
         return false;
      } else if (TeamsModule.isFriendOrTeam(entity) && !snapshot.friendsEnabled()) {
         return false;
      } else if (entity == MC.getCameraEntity() && MC.options.getCameraType().isFirstPerson()) {
         return false;
      } else {
         Vec3 camera = MC.gameRenderer.mainCamera().position();
         if (!entity.shouldRender(camera.x, camera.y, camera.z)) {
            return false;
         } else {
            double maxDistance = snapshot.maxDistance();
            if (maxDistance > 0.0 && entity.distanceToSqr(MC.player) > maxDistance * maxDistance) {
               return false;
            } else {
               return snapshot.entityIds().isEmpty() && snapshot.entityPaths().isEmpty()
                  ? true
                  : snapshot.matchCache().computeIfAbsent(entity.getType(), type -> {
                     Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                     return snapshot.entityIds().contains(id) || snapshot.entityPaths().contains(id.getPath());
                  });
            }
         }
      }
   }

   private static boolean shouldRenderItem(ModuleRenderUtil.ItemEspSnapshot snapshot, ItemEntity entity) {
      if (snapshot == null || !snapshot.enabled()) {
         return false;
      } else if (MC == null || MC.player == null || entity == null) {
         return false;
      } else if (entity.getItem() != null && !entity.getItem().isEmpty()) {
         Vec3 camera = MC.gameRenderer.mainCamera().position();
         if (!entity.shouldRender(camera.x, camera.y, camera.z)) {
            return false;
         } else {
            double maxDistance = snapshot.maxDistance();
            if (maxDistance > 0.0 && entity.distanceToSqr(MC.player) > maxDistance * maxDistance) {
               return false;
            } else {
               return !snapshot.someOnly() ? true : snapshot.matchCache().computeIfAbsent(entity.getItem().getItem(), item -> {
                  Identifier id = BuiltInRegistries.ITEM.getKey(item);
                  return snapshot.itemIds().contains(id) || snapshot.itemPaths().contains(id.getPath());
               });
            }
         }
      } else {
         return false;
      }
   }

   private static ModuleRenderUtil.EntityRenderSnapshot espSnapshot() {
      ModuleRenderUtil.EntityRenderSnapshot snapshot = espSnapshot;
      int revision = ModuleRegistry.revision();
      boolean hidden = PackHideState.isActive();
      if (snapshot.revision() == revision && snapshot.hidden() == hidden) {
         return snapshot;
      } else {
         synchronized (ModuleRenderUtil.class) {
            snapshot = espSnapshot;
            if (snapshot.revision() == revision && snapshot.hidden() == hidden) {
               return snapshot;
            } else {
               snapshot = buildEntityRenderSnapshot("esp", revision, hidden, false);
               espSnapshot = snapshot;
               return snapshot;
            }
         }
      }
   }

   private static ModuleRenderUtil.EntityRenderSnapshot tracerSnapshot() {
      ModuleRenderUtil.EntityRenderSnapshot snapshot = tracerSnapshot;
      int revision = ModuleRegistry.revision();
      boolean hidden = PackHideState.isActive();
      if (snapshot.revision() == revision && snapshot.hidden() == hidden) {
         return snapshot;
      } else {
         synchronized (ModuleRenderUtil.class) {
            snapshot = tracerSnapshot;
            if (snapshot.revision() == revision && snapshot.hidden() == hidden) {
               return snapshot;
            } else {
               snapshot = buildEntityRenderSnapshot("tracers", revision, hidden, true);
               tracerSnapshot = snapshot;
               return snapshot;
            }
         }
      }
   }

   private static ModuleRenderUtil.ChamsSnapshot chamsSnapshot() {
      ModuleRenderUtil.ChamsSnapshot snapshot = chamsSnapshot;
      int revision = ModuleRegistry.revision();
      boolean hidden = PackHideState.isActive();
      if (snapshot.revision() == revision && snapshot.hidden() == hidden) {
         return snapshot;
      } else {
         synchronized (ModuleRenderUtil.class) {
            snapshot = chamsSnapshot;
            if (snapshot.revision() == revision && snapshot.hidden() == hidden) {
               return snapshot;
            } else {
               snapshot = buildChamsSnapshot(revision, hidden);
               chamsSnapshot = snapshot;
               return snapshot;
            }
         }
      }
   }

   private static ModuleRenderUtil.ChamsSnapshot buildChamsSnapshot(int revision, boolean hidden) {
      if (hidden) {
         return ModuleRenderUtil.ChamsSnapshot.inactive(revision, true);
      } else {
         Module module = ModuleRegistry.get("chams");
         if (module != null && module.isEnabled()) {
            Set<Identifier> ids = new HashSet<>();
            Set<String> paths = new HashSet<>();

            for (String entry : module.list("entities")) {
               if (entry != null) {
                  String normalized = entry.trim().toLowerCase(Locale.ROOT);
                  if (!normalized.isEmpty()) {
                     Identifier identifier = normalized.contains(":") ? Identifier.tryParse(normalized) : null;
                     if (identifier != null) {
                        ids.add(identifier);
                     } else {
                        paths.add(normalized.contains(":") ? normalized.substring(normalized.indexOf(58) + 1) : normalized);
                     }
                  }
               }
            }

            return new ModuleRenderUtil.ChamsSnapshot(
               revision,
               false,
               true,
               "Texture".equals(module.value("style")),
               parseDouble(module.value("max-distance"), 128.0),
               Set.copyOf(ids),
               Set.copyOf(paths),
               color(module, "visible-color", -13238437),
               color(module, "occluded-color", -5230337),
               (int)parseDouble(module.value("opacity"), 100.0),
               Boolean.parseBoolean(module.value("hit-color")),
               color(module, "hit-color-value", -50373),
               Boolean.parseBoolean(module.value("draw-armor")),
               new ConcurrentHashMap<>()
            );
         } else {
            return ModuleRenderUtil.ChamsSnapshot.inactive(revision, false);
         }
      }
   }

   public static boolean chamsDrawArmor() {
      return chamsSnapshot().drawArmor();
   }

   private static ModuleRenderUtil.ItemEspSnapshot itemEspSnapshot() {
      ModuleRenderUtil.ItemEspSnapshot snapshot = itemEspSnapshot;
      int revision = ModuleRegistry.revision();
      boolean hidden = PackHideState.isActive();
      if (snapshot.revision() == revision && snapshot.hidden() == hidden) {
         return snapshot;
      } else {
         synchronized (ModuleRenderUtil.class) {
            snapshot = itemEspSnapshot;
            if (snapshot.revision() == revision && snapshot.hidden() == hidden) {
               return snapshot;
            } else {
               snapshot = buildItemEspSnapshot(revision, hidden);
               itemEspSnapshot = snapshot;
               return snapshot;
            }
         }
      }
   }

   private static ModuleRenderUtil.ItemEspSnapshot buildItemEspSnapshot(int revision, boolean hidden) {
      if (hidden) {
         return ModuleRenderUtil.ItemEspSnapshot.inactive(revision, true);
      } else {
         Module module = ModuleRegistry.get("item-esp");
         if (module != null && module.isEnabled()) {
            Set<Identifier> ids = new HashSet<>();
            Set<String> paths = new HashSet<>();

            for (String entry : module.list("items")) {
               if (entry != null) {
                  String normalized = entry.trim().toLowerCase(Locale.ROOT);
                  if (!normalized.isEmpty()) {
                     Identifier identifier = normalized.contains(":") ? Identifier.tryParse(normalized) : null;
                     if (identifier != null) {
                        ids.add(identifier);
                     } else {
                        paths.add(normalized.contains(":") ? normalized.substring(normalized.indexOf(58) + 1) : normalized);
                     }
                  }
               }
            }

            return new ModuleRenderUtil.ItemEspSnapshot(
               revision,
               false,
               true,
               module.value("mode"),
               "Some".equals(module.value("items-mode")),
               parseDouble(module.value("max-distance"), 64.0),
               parseDouble(module.value("fade-distance"), 3.0),
               Set.copyOf(ids),
               Set.copyOf(paths),
               color(module, "color", -855648406),
               !"Static".equals(module.value("color-mode")),
               new ConcurrentHashMap<>(),
               new ConcurrentHashMap<>()
            );
         } else {
            return ModuleRenderUtil.ItemEspSnapshot.inactive(revision, false);
         }
      }
   }

   private static ModuleRenderUtil.EntityRenderSnapshot buildEntityRenderSnapshot(String moduleId, int revision, boolean hidden, boolean useMaxDistance) {
      if (hidden) {
         return ModuleRenderUtil.EntityRenderSnapshot.inactive(moduleId, revision, true);
      } else {
         Module module = ModuleRegistry.get(moduleId);
         if (module != null && module.isEnabled()) {
            Set<Identifier> ids = new HashSet<>();
            Set<String> paths = new HashSet<>();

            for (String entry : module.list("entities")) {
               if (entry != null) {
                  String normalized = entry.trim().toLowerCase(Locale.ROOT);
                  if (!normalized.isEmpty()) {
                     Identifier identifier = normalized.contains(":") ? Identifier.tryParse(normalized) : null;
                     if (identifier != null) {
                        ids.add(identifier);
                     } else {
                        paths.add(normalized.contains(":") ? normalized.substring(normalized.indexOf(58) + 1) : normalized);
                     }
                  }
               }
            }

            return new ModuleRenderUtil.EntityRenderSnapshot(
               moduleId,
               revision,
               false,
               true,
               module.value("mode"),
               useMaxDistance ? parseDouble(module.value("max-distance"), 256.0) : 0.0,
               parseDouble(module.value("fade-distance"), 3.0),
               Set.copyOf(ids),
               Set.copyOf(paths),
               color(module, "players-color", -855638017),
               color(module, "monsters-color", -855684534),
               color(module, "animals-color", -864747633),
               color(module, "water-animals-color", -865674753),
               color(module, "ambient-color", -860386049),
               color(module, "misc-color", -858993460),
               Boolean.parseBoolean(module.value("distance-color")),
               parseDouble(module.value("color-distance"), 40.0),
               TeamsModule.visualTargetsFriends(moduleId),
               TeamsModule.friendsColor(),
               new ConcurrentHashMap<>()
            );
         } else {
            return ModuleRenderUtil.EntityRenderSnapshot.inactive(moduleId, revision, false);
         }
      }
   }

   private static boolean isXrayBlocked(ModuleRenderUtil.XraySnapshot snapshot, BlockState state, BlockPos pos) {
      return isXrayBlocked(snapshot, null, state, pos);
   }

   private static boolean isXrayBlocked(ModuleRenderUtil.XraySnapshot snapshot, BlockGetter level, BlockState state, BlockPos pos) {
      return snapshot.active() && state != null && !state.isAir()
         ? !matchesBlockList(state, snapshot) || snapshot.exposedOnly() && pos != null && !isExposed(snapshot, level, pos)
         : false;
   }

   private static boolean matchesBlockList(BlockState state, ModuleRenderUtil.XraySnapshot snapshot) {
      if (state == null || state.isAir()) {
         return false;
      } else if (snapshot.blockIds().isEmpty() && snapshot.blockPaths().isEmpty()) {
         return false;
      } else {
         Block block = state.getBlock();
         Boolean cached = snapshot.matchCache().get(block);
         if (cached != null) {
            return cached;
         } else {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            boolean match = snapshot.blockIds().contains(id) || snapshot.blockPaths().contains(id.getPath());
            snapshot.matchCache().put(block, match);
            return match;
         }
      }
   }

   private static boolean matchesWorldDarkenList(BlockState state, ModuleRenderUtil.WorldDarkenSnapshot snapshot) {
      if (state != null && !state.isAir()) {
         Block block = state.getBlock();
         Boolean cached = snapshot.matchCache().get(block);
         if (cached != null) {
            return cached;
         } else {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            boolean match = snapshot.blockIds().contains(id) || snapshot.blockPaths().contains(id.getPath());
            snapshot.matchCache().put(block, match);
            return match;
         }
      } else {
         return false;
      }
   }

   private static boolean matchesBlockList(BlockState state, List<String> entries) {
      if (state == null || state.isAir()) {
         return false;
      } else if (entries != null && !entries.isEmpty()) {
         String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

         for (String entry : entries) {
            String normalized = entry.toLowerCase(Locale.ROOT);
            if (id.equals(normalized) || id.endsWith(":" + normalized)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean isExposed(ModuleRenderUtil.XraySnapshot snapshot, BlockGetter level, BlockPos pos) {
      if (pos == null) {
         return true;
      } else {
         BlockGetter source = (BlockGetter)(level != null ? level : (MC == null ? null : MC.level));
         if (source == null) {
            return true;
         } else {
            ModuleRenderUtil.ExposureMemo memo = EXPOSURE_MEMO.get();
            if (memo.revision != snapshot.revision()) {
               Arrays.fill(memo.filled, false);
               memo.revision = snapshot.revision();
            }

            long key = pos.asLong();
            int slot = (int)(key * -7046029254386353131L >>> 60) & 15;
            if (memo.filled[slot] && memo.keys[slot] == key) {
               return memo.exposed[slot];
            } else {
               boolean exposed = computeExposed(source, pos, memo.cursor);
               memo.keys[slot] = key;
               memo.exposed[slot] = exposed;
               memo.filled[slot] = true;
               return exposed;
            }
         }
      }
   }

   private static boolean computeExposed(BlockGetter level, BlockPos pos, MutableBlockPos cursor) {
      for (Direction direction : DIRECTIONS) {
         cursor.setWithOffset(pos, direction);
         BlockState neighbor = level.getBlockState(cursor);
         if (neighbor == null || neighbor.isAir() || !neighbor.isSolidRender()) {
            return true;
         }

         FluidState fluid = neighbor.getFluidState();
         if (fluid != null && !fluid.isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private static int entityColor(ModuleRenderUtil.EntityRenderSnapshot snapshot, Entity entity, int fallback) {
      if (snapshot == null || entity == null) {
         return fallback;
      } else if (snapshot.friendsEnabled() && TeamsModule.isFriendOrTeam(entity)) {
         return snapshot.friendsColor();
      } else if (snapshot.distanceColor()) {
         return distanceColor(entity, snapshot.colorDistance());
      } else if (entity.getType() == EntityTypes.PLAYER) {
         return snapshot.playersColor();
      } else {
         MobCategory category = entity.getType().getCategory();

         return switch (category) {
            case MONSTER -> snapshot.monstersColor();
            case CREATURE -> snapshot.animalsColor();
            case WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> snapshot.waterAnimalsColor();
            case AMBIENT -> snapshot.ambientColor();
            default -> snapshot.miscColor();
         };
      }
   }

   private static int distanceColor(Entity entity, double span) {
      double distance = 0.0;
      if (MC != null && MC.gameRenderer != null && entity != null) {
         Vec3 camera = MC.gameRenderer.mainCamera().position();
         double dx = entity.getX() - camera.x;
         double dy = entity.getY() + entity.getBbHeight() * 0.5 - camera.y;
         double dz = entity.getZ() - camera.z;
         distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
      }

      return distanceHueColor(distance, span);
   }

   public static int distanceHueColor(double distance, double span) {
      if (span <= 0.0) {
         span = 40.0;
      }

      float t = (float)Math.max(0.0, Math.min(1.0, distance / span));
      float hue = t * 0.33333334F;
      return 0xFF000000 | hsbToRgb(hue, 1.0F, 1.0F) & 16777215;
   }

   private static int hsbToRgb(float h, float s, float b) {
      h = (h % 1.0F + 1.0F) % 1.0F;
      int i = (int)(h * 6.0F);
      float f = h * 6.0F - i;
      float p = b * (1.0F - s);
      float q = b * (1.0F - s * f);
      float t = b * (1.0F - s * (1.0F - f));
      float r;
      float g;
      float bl;
      switch (i % 6) {
         case 0:
            r = b;
            g = t;
            bl = p;
            break;
         case 1:
            r = q;
            g = b;
            bl = p;
            break;
         case 2:
            r = p;
            g = b;
            bl = t;
            break;
         case 3:
            r = p;
            g = q;
            bl = b;
            break;
         case 4:
            r = t;
            g = p;
            bl = b;
            break;
         default:
            r = b;
            g = p;
            bl = q;
      }

      int ri = Math.round(r * 255.0F);
      int gi = Math.round(g * 255.0F);
      int bi = Math.round(bl * 255.0F);
      return ri << 16 | gi << 8 | bi;
   }

   private static double espFadeAlpha(ModuleRenderUtil.EntityRenderSnapshot snapshot, Entity entity) {
      if (MC != null && MC.gameRenderer != null && entity != null) {
         double fadeDistance = snapshot == null ? 3.0 : snapshot.fadeDistance();
         if (fadeDistance <= 0.0) {
            return 1.0;
         } else {
            Vec3 camera = MC.gameRenderer.mainCamera().position();
            double dx = entity.getX() - camera.x;
            double dy = entity.getY() + entity.getBbHeight() * 0.5 - camera.y;
            double dz = entity.getZ() - camera.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double alpha = Math.min(1.0, distance / fadeDistance);
            return alpha <= 0.075 ? 0.0 : alpha;
         }
      } else {
         return 1.0;
      }
   }

   private static double itemEspFadeAlpha(ModuleRenderUtil.ItemEspSnapshot snapshot, ItemEntity entity) {
      if (MC != null && MC.gameRenderer != null && entity != null) {
         double fadeDistance = snapshot == null ? 3.0 : snapshot.fadeDistance();
         if (fadeDistance <= 0.0) {
            return 1.0;
         } else {
            Vec3 camera = MC.gameRenderer.mainCamera().position();
            double dx = entity.getX() - camera.x;
            double dy = entity.getY() + entity.getBbHeight() * 0.5 - camera.y;
            double dz = entity.getZ() - camera.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double alpha = Math.min(1.0, distance / fadeDistance);
            return alpha <= 0.075 ? 0.0 : alpha;
         }
      } else {
         return 1.0;
      }
   }

   private static int withAlphaMultiplier(int color, double multiplier) {
      int alpha = Math.max(0, Math.min(255, (int)((color >>> 24 & 0xFF) * multiplier)));
      return alpha << 24 | color & 16777215;
   }

   public static int color(Module module, String option, int fallback) {
      try {
         String value = module.value(option).replace("#", "");
         if (value.length() == 6) {
            value = "CC" + value;
         }

         return (int)Long.parseLong(value, 16);
      } catch (Throwable var4) {
         return fallback;
      }
   }

   private static double parseDouble(String value, double fallback) {
      try {
         return Double.parseDouble(value);
      } catch (Exception var4) {
         return fallback;
      }
   }

   private static int parseInt(String value, int fallback) {
      try {
         return Integer.parseInt(value);
      } catch (Exception var3) {
         return fallback;
      }
   }

   private static boolean isIrisShaderPackInUse() {
      try {
         Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
         Object instance = irisApiClass.getMethod("getInstance").invoke(null);
         return irisApiClass.getMethod("isShaderPackInUse").invoke(instance) instanceof Boolean bool && bool;
      } catch (Throwable var4) {
         return false;
      }
   }

   private static ModuleRenderUtil.XraySnapshot xraySnapshot() {
      int revision = ModuleRegistry.revision();
      boolean hidden = PackHideState.isActive();
      ModuleRenderUtil.XraySnapshot snapshot = xraySnapshot;
      if (snapshot.revision() == revision && snapshot.hidden() == hidden) {
         return snapshot;
      } else {
         synchronized (ModuleRenderUtil.class) {
            snapshot = xraySnapshot;
            if (snapshot.revision() == revision && snapshot.hidden() == hidden) {
               return snapshot;
            } else {
               snapshot = buildXraySnapshot(revision, hidden);
               xraySnapshot = snapshot;
               return snapshot;
            }
         }
      }
   }

   private static ModuleRenderUtil.WorldDarkenSnapshot worldDarkenSnapshot() {
      int revision = ModuleRegistry.revision();
      boolean hidden = PackHideState.isActive();
      ModuleRenderUtil.WorldDarkenSnapshot snapshot = worldDarkenSnapshot;
      if (snapshot.revision() == revision && snapshot.hidden() == hidden) {
         return snapshot;
      } else {
         synchronized (ModuleRenderUtil.class) {
            snapshot = worldDarkenSnapshot;
            if (snapshot.revision() == revision && snapshot.hidden() == hidden) {
               return snapshot;
            } else {
               snapshot = buildWorldDarkenSnapshot(revision, hidden);
               worldDarkenSnapshot = snapshot;
               return snapshot;
            }
         }
      }
   }

   private static ModuleRenderUtil.FullbrightSnapshot fullbrightSnapshot() {
      int revision = ModuleRegistry.revision();
      boolean hidden = PackHideState.isActive();
      ModuleRenderUtil.FullbrightSnapshot snapshot = fullbrightSnapshot;
      if (snapshot.revision() == revision && snapshot.hidden() == hidden) {
         return snapshot;
      } else {
         synchronized (ModuleRenderUtil.class) {
            snapshot = fullbrightSnapshot;
            if (snapshot.revision() == revision && snapshot.hidden() == hidden) {
               return snapshot;
            } else {
               snapshot = buildFullbrightSnapshot(revision, hidden);
               fullbrightSnapshot = snapshot;
               return snapshot;
            }
         }
      }
   }

   private static ModuleRenderUtil.FullbrightSnapshot buildFullbrightSnapshot(int revision, boolean hidden) {
      if (hidden) {
         return ModuleRenderUtil.FullbrightSnapshot.inactive(revision, true);
      } else {
         Module module = ModuleRegistry.get("fullbright");
         if (module != null && module.isEnabled()) {
            String mode = module.value("mode");
            boolean gamma = "Gamma".equals(mode);
            boolean luminance = "Luminance".equals(mode);
            String lightType = module.value("light-type");
            int minLight = Math.max(0, Math.min(15, parseInt(module.value("minimum-light-level"), 8)));
            return new ModuleRenderUtil.FullbrightSnapshot(
               revision, false, gamma, luminance, lightType, minLight, "SKY".equals(lightType) ? minLight : 0, "BLOCK".equals(lightType) ? minLight : 0
            );
         } else {
            return ModuleRenderUtil.FullbrightSnapshot.inactive(revision, false);
         }
      }
   }

   private static ModuleRenderUtil.WorldDarkenSnapshot buildWorldDarkenSnapshot(int revision, boolean hidden) {
      if (hidden) {
         return ModuleRenderUtil.WorldDarkenSnapshot.inactive(revision, true);
      } else {
         Module module = ModuleRegistry.get("world");
         if (module != null && module.isEnabled() && Boolean.parseBoolean(module.value("darken-blocks"))) {
            int darkness = Math.max(0, Math.min(100, parseInt(module.value("darkness"), 50)));
            if (darkness <= 0) {
               return ModuleRenderUtil.WorldDarkenSnapshot.inactive(revision, false);
            } else {
               double t = darkness / 100.0;
               int picked = color(module, "tint-color", -16777216);
               int cr = Math.max(0, Math.min(255, (int)Math.round(255.0 + ((picked >> 16 & 0xFF) - 255) * t)));
               int cg = Math.max(0, Math.min(255, (int)Math.round(255.0 + ((picked >> 8 & 0xFF) - 255) * t)));
               int cb = Math.max(0, Math.min(255, (int)Math.round(255.0 + ((picked & 0xFF) - 255) * t)));
               int tint = 0xFF000000 | cr << 16 | cg << 8 | cb;
               boolean whitelist = "Whitelist".equalsIgnoreCase(module.value("mode"));
               Set<Identifier> ids = new HashSet<>();
               Set<String> paths = new HashSet<>();

               for (String entry : module.list("blocks")) {
                  if (entry != null) {
                     String normalized = entry.trim().toLowerCase(Locale.ROOT);
                     if (!normalized.isEmpty()) {
                        Identifier identifier = normalized.contains(":") ? Identifier.tryParse(normalized) : null;
                        if (identifier != null) {
                           ids.add(identifier);
                        } else {
                           paths.add(normalized.contains(":") ? normalized.substring(normalized.indexOf(58) + 1) : normalized);
                        }
                     }
                  }
               }

               return new ModuleRenderUtil.WorldDarkenSnapshot(
                  revision, false, true, tint, whitelist, Set.copyOf(ids), Set.copyOf(paths), new ConcurrentHashMap<>()
               );
            }
         } else {
            return ModuleRenderUtil.WorldDarkenSnapshot.inactive(revision, false);
         }
      }
   }

   private static ModuleRenderUtil.XraySnapshot buildXraySnapshot(int revision, boolean hidden) {
      if (hidden) {
         return ModuleRenderUtil.XraySnapshot.inactive(revision, true);
      } else {
         Module module = ModuleRegistry.get("xray");
         if (module == null || !module.isEnabled()) {
            return ModuleRenderUtil.XraySnapshot.inactive(revision, false);
         } else if (!ModuleOreSim.tintActive(module)) {
            return ModuleRenderUtil.XraySnapshot.inactive(revision, false);
         } else {
            Set<Identifier> ids = new HashSet<>();
            Set<String> paths = new HashSet<>();
            boolean oreSim = ModuleOreSim.oreSimMode(module);
            if (!oreSim) {
               for (String entry : module.list("whitelist")) {
                  if (entry != null) {
                     String normalized = entry.trim().toLowerCase(Locale.ROOT);
                     if (!normalized.isEmpty()) {
                        Identifier identifier = normalized.contains(":") ? Identifier.tryParse(normalized) : null;
                        if (identifier != null) {
                           ids.add(identifier);
                        } else {
                           paths.add(normalized.contains(":") ? normalized.substring(normalized.indexOf(58) + 1) : normalized);
                        }
                     }
                  }
               }
            }

            return new ModuleRenderUtil.XraySnapshot(
               revision,
               false,
               true,
               Math.max(0, Math.min(255, parseInt(module.value("opacity"), 25))),
               !oreSim && Boolean.parseBoolean(module.value("exposed-only")),
               isIrisShaderPackInUse(),
               module.value("fluid-opacity"),
               Set.copyOf(ids),
               Set.copyOf(paths),
               new ConcurrentHashMap<>()
            );
         }
      }
   }

   private record ChamsSnapshot(
      int revision,
      boolean hidden,
      boolean enabled,
      boolean textureMode,
      double maxDistance,
      Set<Identifier> entityIds,
      Set<String> entityPaths,
      int visibleColor,
      int occludedColor,
      int opacity,
      boolean hitEnabled,
      int hitColor,
      boolean drawArmor,
      ConcurrentHashMap<EntityType<?>, Boolean> matchCache
   ) {
      static ModuleRenderUtil.ChamsSnapshot inactive(int revision, boolean hidden) {
         return new ModuleRenderUtil.ChamsSnapshot(
            revision, hidden, false, false, 0.0, Set.of(), Set.of(), -13238437, -5230337, 100, true, -50373, true, new ConcurrentHashMap<>()
         );
      }
   }

   private record EntityRenderSnapshot(
      String moduleId,
      int revision,
      boolean hidden,
      boolean enabled,
      String mode,
      double maxDistance,
      double fadeDistance,
      Set<Identifier> entityIds,
      Set<String> entityPaths,
      int playersColor,
      int monstersColor,
      int animalsColor,
      int waterAnimalsColor,
      int ambientColor,
      int miscColor,
      boolean distanceColor,
      double colorDistance,
      boolean friendsEnabled,
      int friendsColor,
      ConcurrentHashMap<EntityType<?>, Boolean> matchCache
   ) {
      static ModuleRenderUtil.EntityRenderSnapshot inactive(String moduleId, int revision, boolean hidden) {
         return new ModuleRenderUtil.EntityRenderSnapshot(
            moduleId,
            revision,
            hidden,
            false,
            "",
            0.0,
            0.0,
            Set.of(),
            Set.of(),
            -855638017,
            -855684534,
            -864747633,
            -865674753,
            -860386049,
            -858993460,
            false,
            40.0,
            true,
            -866779137,
            new ConcurrentHashMap<>()
         );
      }
   }

   private static final class ExposureMemo {
      static final int MASK = 15;
      final long[] keys = new long[16];
      final boolean[] exposed = new boolean[16];
      final boolean[] filled = new boolean[16];
      final MutableBlockPos cursor = new MutableBlockPos();
      int revision = Integer.MIN_VALUE;
   }

   private record FullbrightSnapshot(
      int revision, boolean hidden, boolean gamma, boolean luminance, String lightType, int minimumLightLevel, int skyLightValue, int blockLightValue
   ) {
      static ModuleRenderUtil.FullbrightSnapshot inactive(int revision, boolean hidden) {
         return new ModuleRenderUtil.FullbrightSnapshot(revision, hidden, false, false, "", 0, 0, 0);
      }
   }

   private record ItemEspSnapshot(
      int revision,
      boolean hidden,
      boolean enabled,
      String mode,
      boolean someOnly,
      double maxDistance,
      double fadeDistance,
      Set<Identifier> itemIds,
      Set<String> itemPaths,
      int color,
      boolean dynamicColor,
      ConcurrentHashMap<Item, Integer> rgbCache,
      ConcurrentHashMap<Item, Boolean> matchCache
   ) {
      static ModuleRenderUtil.ItemEspSnapshot inactive(int revision, boolean hidden) {
         return new ModuleRenderUtil.ItemEspSnapshot(
            revision, hidden, false, "", false, 0.0, 0.0, Set.of(), Set.of(), -855648406, true, new ConcurrentHashMap<>(), new ConcurrentHashMap<>()
         );
      }
   }

   private record SodiumQuadAccess(Method baseColor, Method setColor, Method setRenderType) {
      private static final ModuleRenderUtil.SodiumQuadAccess UNAVAILABLE = new ModuleRenderUtil.SodiumQuadAccess(null, null, null);

      private void applyAlpha(Object quad, int alpha) {
         if (this.baseColor != null && this.setColor != null) {
            try {
               for (int i = 0; i < 4; i++) {
                  int color = (Integer)this.baseColor.invoke(quad, i);
                  this.setColor.invoke(quad, i, (alpha & 0xFF) << 24 | color & 16777215);
               }
            } catch (ReflectiveOperationException var5) {
            }
         }
      }

      private void applyTint(Object quad, int tint) {
         if (this.baseColor != null && this.setColor != null) {
            try {
               for (int i = 0; i < 4; i++) {
                  int color = (Integer)this.baseColor.invoke(quad, i);
                  this.setColor.invoke(quad, i, ARGB.multiply(color, tint));
               }
            } catch (ReflectiveOperationException var5) {
            }
         }
      }

      private void applyRenderLayer(Object quad, ChunkSectionLayer layer) {
         if (this.setRenderType != null) {
            try {
               this.setRenderType.invoke(quad, layer);
            } catch (ReflectiveOperationException var4) {
            }
         }
      }
   }

   private record WorldDarkenSnapshot(
      int revision,
      boolean hidden,
      boolean active,
      int tint,
      boolean whitelist,
      Set<Identifier> blockIds,
      Set<String> blockPaths,
      ConcurrentHashMap<Block, Boolean> matchCache
   ) {
      static ModuleRenderUtil.WorldDarkenSnapshot inactive(int revision, boolean hidden) {
         return new ModuleRenderUtil.WorldDarkenSnapshot(revision, hidden, false, -1, false, Set.of(), Set.of(), new ConcurrentHashMap<>());
      }
   }

   private record XraySnapshot(
      int revision,
      boolean hidden,
      boolean active,
      int opacity,
      boolean exposedOnly,
      boolean irisShaderPackInUse,
      String fluidOpacityMode,
      Set<Identifier> blockIds,
      Set<String> blockPaths,
      ConcurrentHashMap<Block, Boolean> matchCache
   ) {
      static ModuleRenderUtil.XraySnapshot inactive(int revision, boolean hidden) {
         return new ModuleRenderUtil.XraySnapshot(revision, hidden, false, -1, false, false, "Both", Set.of(), Set.of(), new ConcurrentHashMap<>());
      }
   }
}
