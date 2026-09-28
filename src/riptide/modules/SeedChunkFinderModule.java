package riptide.modules;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.api.module.StringSetting;
import riptide.util.RiptideClientMessaging;
import riptide.util.RiptideWorldGeometry;

public final class SeedChunkFinderModule extends Module {
   private static volatile boolean renderHookInstalled;
   private static SeedChunkFinderModule cachedInstance;
   private boolean outline = true;
   private boolean fill = true;
   private boolean full;
   private int radius = 8;
   private int color = -13058568;
   private long seed;
   private boolean seedValid;
   private String seedRaw = "";

   public SeedChunkFinderModule() {
      super("seed-chunk-finder", "Seed Chunk Finder", ModuleCategory.RENDER, "Highlights slime chunks for a known seed.");
      this.add(new StringSetting("seed", "World Seed", "").description("Your server's seed. Numbers, or any text (hashed like vanilla).").build());
      this.add(new ChoiceSetting("mode", "Mode", "Both", "Both", "Outline", "Fill").description("Chunk outline, translucent walls, or both.").build());
      this.add(new ChoiceSetting("height", "Height", "Player", "Player", "Full").description("A band around you, or the full world column.").build());
      this.add(new IntSetting("radius", "Radius", 8, 1, 32, 1).description("Chunk radius searched around you.").build());
      this.add(
         new BoolSetting("only-ground", "Skip Below Y0", true).description("Slimes only spawn under Y40 in slime chunks; this just trims the drawing.").build()
      );
      this.add(new ColorSetting("color", "Color", -13058568).group("Colors").description("Highlight color.").build());
   }

   public static void initialize() {
      installRenderHook();
   }

   @Override
   public String info() {
      return this.seedValid ? Long.toString(this.seed) : "no seed";
   }

   @Override
   public void onEnable() {
      installRenderHook();
      this.refreshSettings();
      if (!this.seedValid) {
         RiptideClientMessaging.sendPrefixed("§eSet the World Seed in this module's settings first.");
      }
   }

   @Override
   protected void onOptionValueChanged(String var1) {
      if ("seed".equals(var1)) {
         this.refreshSettings();
         RiptideClientMessaging.sendPrefixed(this.seedValid ? "§aSeed set to §f" + this.seed : "§cCouldn't read that seed.");
      }
   }

   private static SeedChunkFinderModule instance() {
      SeedChunkFinderModule var0 = cachedInstance;
      if (var0 == null && ModuleRegistry.get("seed-chunk-finder") instanceof SeedChunkFinderModule var1) {
         var0 = var1;
         cachedInstance = var1;
      }

      return var0;
   }

   private void refreshSettings() {
      String var1 = this.choice("mode");
      this.outline = !"Fill".equals(var1);
      this.fill = !"Outline".equals(var1);
      this.full = "Full".equals(this.choice("height"));
      this.radius = this.integer("radius");
      this.color = ModuleRenderUtil.color(this, "color", -13058568);
      String var2 = this.text("seed");
      if (!var2.equals(this.seedRaw)) {
         this.seedRaw = var2;
         this.seedValid = parseSeed(var2) != null;
         this.seed = this.seedValid ? parseSeed(var2) : 0L;
      }
   }

   private static Long parseSeed(String var0) {
      if (var0 == null) {
         return null;
      } else {
         String var1 = var0.trim();
         if (var1.isEmpty()) {
            return null;
         } else {
            try {
               return Long.parseLong(var1);
            } catch (NumberFormatException var3) {
               return (long)var1.hashCode();
            }
         }
      }
   }

   public static boolean isSlimeChunk(long var0, int var2, int var3) {
      Random var4 = new Random(var0 + var2 * var2 * 4987142 + var2 * 5947611 + (long)var3 * var3 * 4392871L + var3 * 389711 ^ 987234911L);
      return var4.nextInt(10) == 0;
   }

   private static synchronized void installRenderHook() {
      if (!renderHookInstalled) {
         renderHookInstalled = true;
         LevelRenderEvents.COLLECT_SUBMITS
            .register(
               (CollectSubmits)var0 -> {
                  try {
                     SeedChunkFinderModule var1 = instance();
                     if (var1 == null || !var1.isEnabled() || PackHideState.isActive()) {
                        return;
                     }

                     if (MC == null || MC.level == null || MC.player == null || MC.gui.hud.isHidden()) {
                        return;
                     }

                     if (ModuleRenderUtil.shouldSuppressEspForUi()) {
                        return;
                     }

                     var1.refreshSettings();
                     if (!var1.seedValid || !var1.fill && !var1.outline) {
                        return;
                     }

                     Vec3 var2 = var0.levelState().cameraRenderState.pos;
                     if (var1.fill) {
                        var0.submitNodeCollector()
                           .submitCustomGeometry(
                              var0.poseStack(), RiptideRenderTypes.storageEspFillSeeThrough(), (var2x, var3x) -> var1.emit(var2x, var3x, var2, true)
                           );
                     }

                     if (var1.outline) {
                        var0.submitNodeCollector()
                           .submitCustomGeometry(
                              var0.poseStack(), RiptideRenderTypes.storageEspLinesSeeThrough(), (var2x, var3x) -> var1.emit(var2x, var3x, var2, false)
                           );
                     }
                  } catch (Throwable var3) {
                  }
               }
            );
      }
   }

   private void emit(Pose var1, VertexConsumer var2, Vec3 var3, boolean var4) {
      int var5 = MC.player.blockPosition().getX() >> 4;
      int var6 = MC.player.blockPosition().getZ() >> 4;
      double var7;
      double var9;
      if (this.full) {
         var7 = this.bool("only-ground") ? Math.max(MC.level.getMinY(), 0) : MC.level.getMinY();
         var9 = MC.level.getMaxY() + 1;
      } else {
         double var11 = MC.player.getY();
         var7 = var11 - 8.0;
         var9 = var11 + 8.0;
      }

      for (int var27 = var5 - this.radius; var27 <= var5 + this.radius; var27++) {
         for (int var12 = var6 - this.radius; var12 <= var6 + this.radius; var12++) {
            if (isSlimeChunk(this.seed, var27, var12)) {
               ChunkPos var13 = new ChunkPos(var27, var12);
               double var14 = var13.getMinBlockX() - var3.x;
               double var16 = var13.getMaxBlockX() + 1 - var3.x;
               double var18 = var13.getMinBlockZ() - var3.z;
               double var20 = var13.getMaxBlockZ() + 1 - var3.z;
               double var22 = var7 - var3.y;
               double var24 = var9 - var3.y;
               if (var4) {
                  int var26 = withAlpha(this.color, 0.16);
                  if (var26 != 0) {
                     walls(var1, var2, var14, var16, var18, var20, var22, var24, var26);
                  }
               } else {
                  int var28 = withAlpha(this.color, 1.0);
                  if (var28 != 0) {
                     edges(var1, var2, var14, var16, var18, var20, var22, var24, var28);
                  }
               }
            }
         }
      }
   }

   private static void walls(Pose var0, VertexConsumer var1, double var2, double var4, double var6, double var8, double var10, double var12, int var14) {
      quad(var0, var1, var2, var10, var6, var2, var12, var6, var4, var12, var6, var4, var10, var6, var14);
      quad(var0, var1, var4, var10, var8, var4, var12, var8, var2, var12, var8, var2, var10, var8, var14);
      quad(var0, var1, var2, var10, var8, var2, var12, var8, var2, var12, var6, var2, var10, var6, var14);
      quad(var0, var1, var4, var10, var6, var4, var12, var6, var4, var12, var8, var4, var10, var8, var14);
   }

   private static void edges(Pose var0, VertexConsumer var1, double var2, double var4, double var6, double var8, double var10, double var12, int var14) {
      for (double var18 : new double[]{var10, var12}) {
         RiptideWorldGeometry.line(var0, var1, var2, var18, var6, var4, var18, var6, var14, 1.5F);
         RiptideWorldGeometry.line(var0, var1, var4, var18, var6, var4, var18, var8, var14, 1.5F);
         RiptideWorldGeometry.line(var0, var1, var4, var18, var8, var2, var18, var8, var14, 1.5F);
         RiptideWorldGeometry.line(var0, var1, var2, var18, var8, var2, var18, var6, var14, 1.5F);
      }

      RiptideWorldGeometry.line(var0, var1, var2, var10, var6, var2, var12, var6, var14, 1.5F);
      RiptideWorldGeometry.line(var0, var1, var4, var10, var6, var4, var12, var6, var14, 1.5F);
      RiptideWorldGeometry.line(var0, var1, var4, var10, var8, var4, var12, var8, var14, 1.5F);
      RiptideWorldGeometry.line(var0, var1, var2, var10, var8, var2, var12, var8, var14, 1.5F);
   }

   private static void quad(
      Pose var0,
      VertexConsumer var1,
      double var2,
      double var4,
      double var6,
      double var8,
      double var10,
      double var12,
      double var14,
      double var16,
      double var18,
      double var20,
      double var22,
      double var24,
      int var26
   ) {
      var1.addVertex(var0, (float)var2, (float)var4, (float)var6).setColor(var26);
      var1.addVertex(var0, (float)var8, (float)var10, (float)var12).setColor(var26);
      var1.addVertex(var0, (float)var14, (float)var16, (float)var18).setColor(var26);
      var1.addVertex(var0, (float)var20, (float)var22, (float)var24).setColor(var26);
   }

   private static int withAlpha(int var0, double var1) {
      int var3 = (int)Math.round((var0 >>> 24 & 0xFF) * var1);
      return var3 <= 0 ? 0 : Math.min(255, var3) << 24 | var0 & 16777215;
   }
}
