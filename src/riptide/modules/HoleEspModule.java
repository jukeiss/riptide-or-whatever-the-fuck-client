package riptide.modules;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.DoubleSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideHoleScanner;
import riptide.util.RiptidePerf;
import riptide.util.RiptideWorldGeometry;

public final class HoleEspModule extends Module implements RiptideHoleScanner.Subscriber {
   private static final String MODE_BOX = "Box";
   private static final String MODE_GLOWING_PLANE = "GlowingPlane";
   private static final double FACE_ALPHA = 0.19607843137254902;
   private static final double OUTLINE_ALPHA = 0.39215686274509803;
   private static final float LINE_WIDTH = 1.5F;
   private static final double INFLATE = 0.002;
   private static final double LIFT = 0.002;
   private static volatile boolean renderHookInstalled;
   private static HoleEspModule cachedInstance;
   private int cachedRevision = -1;
   private boolean glowingPlane = true;
   private boolean fill = true;
   private boolean outline = true;
   private int horizontal = 32;
   private int vertical = 8;
   private double distanceFade = 0.3;
   private double glowHeight = 0.7;
   private int maxHoles = 512;
   private int colorBedrock = -15089316;
   private int colorOneByOne = -575461;
   private int colorOneByTwo = -13255988;
   private int colorTwoByTwo = -536805;
   private boolean subscribed;

   public HoleEspModule() {
      super("hole-esp", "HoleESP", ModuleCategory.RENDER, "Highlights safe holes.");
      this.add(new ChoiceSetting("mode", "Mode", "GlowingPlane", "Box", "GlowingPlane").description("Full box or glowing floor.").build());
      this.add(new IntSetting("horizontal-distance", "Horizontal Distance", 32, 4, 128, 1).description("Scan radius on X/Z.").build());
      this.add(new IntSetting("vertical-distance", "Vertical Distance", 8, 4, 64, 1).description("Scan radius on Y.").build());
      this.add(new DoubleSetting("distance-fade", "Distance Fade", 0.3, 0.0, 1.0, 0.05).description("Fraction of radius that fades.").build());
      this.add(new BoolSetting("fill", "Fill", true).description("Draw the translucent face.").build());
      this.add(new BoolSetting("outline", "Outline", true).description("Draw the edges.").build());
      this.add(
         new DoubleSetting("glow-height", "Glow Height", 0.7, 0.0, 1.0, 0.05)
            .description("Height of the fading walls.")
            .visibleWhen(() -> !"Box".equals(this.choice("mode")))
            .build()
      );
      this.add(new IntSetting("max-holes", "Max Holes", 512, 16, 4096, 16).description("Most holes drawn at once.").build());
      this.add(new ColorSetting("color-1x1-bedrock", "1x1 Bedrock", -15089316).group("Colors").description("A 1x1 walled in bedrock.").build());
      this.add(new ColorSetting("color-1x1", "1x1", -575461).group("Colors").description("A one-block hole.").build());
      this.add(new ColorSetting("color-1x2", "1x2", -13255988).group("Colors").description("A two-block hole.").build());
      this.add(new ColorSetting("color-2x2", "2x2", -536805).group("Colors").description("A four-block hole.").build());
   }

   public static void initialize() {
      installRenderHook();
   }

   @Override
   public void onEnable() {
      this.cachedRevision = -1;
      installRenderHook();
      this.ensureSubscribed();
   }

   @Override
   public void onDisable() {
      this.cachedRevision = -1;
      this.release();
   }

   @Override
   public void onGameLeft() {
      this.cachedRevision = -1;
      this.release();
   }

   @Override
   public void tick() {
      this.ensureSubscribed();
      this.refreshSettings();
      RiptideHoleScanner.tick();
   }

   @Override
   public String info() {
      return this.choice("mode");
   }

   @Override
   public int horizontalDistance() {
      this.refreshSettings();
      return this.horizontal;
   }

   @Override
   public int verticalDistance() {
      this.refreshSettings();
      return this.vertical;
   }

   private void ensureSubscribed() {
      if (!this.subscribed) {
         RiptideHoleScanner.subscribe(this);
         this.subscribed = true;
      }
   }

   private void release() {
      if (this.subscribed) {
         this.subscribed = false;
         RiptideHoleScanner.unsubscribe(this);
      }
   }

   private void refreshSettings() {
      int revision = ModuleRegistry.revision();
      if (revision != this.cachedRevision) {
         this.cachedRevision = revision;
         this.glowingPlane = !"Box".equals(this.choice("mode"));
         this.fill = this.bool("fill");
         this.outline = this.bool("outline");
         this.horizontal = this.integer("horizontal-distance");
         this.vertical = this.integer("vertical-distance");
         this.distanceFade = this.decimal("distance-fade");
         this.glowHeight = this.decimal("glow-height");
         this.maxHoles = this.integer("max-holes");
         this.colorBedrock = ModuleRenderUtil.color(this, "color-1x1-bedrock", -15089316);
         this.colorOneByOne = ModuleRenderUtil.color(this, "color-1x1", -575461);
         this.colorOneByTwo = ModuleRenderUtil.color(this, "color-1x2", -13255988);
         this.colorTwoByTwo = ModuleRenderUtil.color(this, "color-2x2", -536805);
      }
   }

   private static HoleEspModule instance() {
      HoleEspModule cached = cachedInstance;
      if (cached != null) {
         return cached;
      } else {
         return ModuleRegistry.get("hole-esp") instanceof HoleEspModule hole ? (cachedInstance = hole) : null;
      }
   }

   private static synchronized void installRenderHook() {
      if (!renderHookInstalled) {
         renderHookInstalled = true;
         LevelRenderEvents.COLLECT_SUBMITS
            .register(
               (CollectSubmits)context -> {
                  try {
                     HoleEspModule module = instance();
                     if (module == null || !module.isEnabled()) {
                        return;
                     }

                     if (PackHideState.isActive()) {
                        return;
                     }

                     if (MC == null || MC.level == null || MC.player == null || MC.gui.hud.isHidden()) {
                        return;
                     }

                     if (ModuleRenderUtil.shouldSuppressEspForUi()) {
                        return;
                     }

                     module.refreshSettings();
                     if (!module.fill && !module.outline) {
                        return;
                     }

                     List<RiptideHoleScanner.Hole> holes = RiptideHoleScanner.holes();
                     if (holes.isEmpty()) {
                        return;
                     }

                     Vec3 camera = context.levelState().cameraRenderState.pos;
                     Vec3 player = MC.player.position();
                     if (module.fill) {
                        context.submitNodeCollector()
                           .submitCustomGeometry(
                              context.poseStack(),
                              RiptideRenderTypes.storageEspFillSeeThrough(),
                              (pose, buffer) -> module.emitFill(pose, buffer, holes, camera, player)
                           );
                     }

                     if (module.outline) {
                        context.submitNodeCollector()
                           .submitCustomGeometry(
                              context.poseStack(),
                              RiptideRenderTypes.storageEspLinesSeeThrough(),
                              (pose, buffer) -> module.emitOutline(pose, buffer, holes, camera, player)
                           );
                     }
                  } catch (Throwable var5) {
                  }
               }
            );
      }
   }

   private void emitFill(Pose pose, VertexConsumer buffer, List<RiptideHoleScanner.Hole> holes, Vec3 camera, Vec3 player) {
      long perf = RiptidePerf.beginSampled();
      int drawn = 0;

      for (RiptideHoleScanner.Hole hole : holes) {
         if (drawn >= this.maxHoles) {
            break;
         }

         double fade = this.fade(hole.pos(), player);
         if (!(fade <= 0.0)) {
            drawn++;
            int color = withAlpha(this.colorFor(hole.type()), 0.19607843137254902 * fade);
            if (color != 0) {
               AABB box = hole.box().move(-camera.x, -camera.y, -camera.z);
               if (this.glowingPlane) {
                  double floorY = box.minY + 0.002;
                  sheet(pose, buffer, box.minX, box.maxX, box.minZ, box.maxZ, floorY, color);
                  if (this.glowHeight > 0.0) {
                     this.glow(pose, buffer, box, floorY, color);
                  }
               } else {
                  fillBox(pose, buffer, box.inflate(0.002), color);
               }
            }
         }
      }

      RiptidePerf.end("frame.holeEspFill", perf);
   }

   private void emitOutline(Pose pose, VertexConsumer buffer, List<RiptideHoleScanner.Hole> holes, Vec3 camera, Vec3 player) {
      long perf = RiptidePerf.beginSampled();
      int drawn = 0;

      for (RiptideHoleScanner.Hole hole : holes) {
         if (drawn >= this.maxHoles) {
            break;
         }

         double fade = this.fade(hole.pos(), player);
         if (!(fade <= 0.0)) {
            drawn++;
            int color = withAlpha(this.colorFor(hole.type()), 0.39215686274509803 * fade);
            if (color != 0) {
               AABB box = hole.box().move(-camera.x, -camera.y, -camera.z);
               if (this.glowingPlane) {
                  footprint(pose, buffer, box, box.minY + 0.002, color);
               } else {
                  edges(pose, buffer, box.inflate(0.002), color);
               }
            }
         }
      }

      RiptidePerf.end("frame.holeEspOutline", perf);
   }

   private int colorFor(RiptideHoleScanner.HoleType type) {
      return switch (type) {
         case ONE_BY_ONE_BEDROCK -> this.colorBedrock;
         case ONE_BY_ONE -> this.colorOneByOne;
         case ONE_BY_TWO -> this.colorOneByTwo;
         case TWO_BY_TWO -> this.colorTwoByTwo;
      };
   }

   private double fade(BlockPos pos, Vec3 player) {
      double dx = player.x - pos.getX();
      double dy = player.y - pos.getY();
      double dz = player.z - pos.getZ();
      if (Math.abs(dy) > this.vertical || Math.abs(dx) > this.horizontal || Math.abs(dz) > this.horizontal) {
         return 0.0;
      } else if (this.distanceFade <= 0.0) {
         return 1.0;
      } else {
         double verticalFraction = dy / this.vertical;
         double horizontalFraction = Math.sqrt(dx * dx + dz * dz) / this.horizontal;
         double fade = (1.0 - Math.max(verticalFraction, horizontalFraction)) / this.distanceFade;
         return fade <= 0.0 ? 0.0 : Math.min(1.0, fade);
      }
   }

   private static int withAlpha(int argb, double multiplier) {
      int alpha = (int)Math.round((argb >>> 24 & 0xFF) * multiplier);
      return alpha <= 0 ? 0 : Math.min(255, alpha) << 24 | argb & 16777215;
   }

   private static void sheet(Pose pose, VertexConsumer buffer, double minX, double maxX, double minZ, double maxZ, double y, int color) {
      quad(pose, buffer, minX, y, minZ, maxX, y, minZ, maxX, y, maxZ, minX, y, maxZ, color);
      quad(pose, buffer, minX, y, maxZ, maxX, y, maxZ, maxX, y, minZ, minX, y, minZ, color);
   }

   private void glow(Pose pose, VertexConsumer buffer, AABB box, double baseY, int color) {
      int top = color & 16777215;
      double topY = baseY + this.glowHeight;
      gradientSide(pose, buffer, box.minX, box.minZ, box.maxX, box.minZ, baseY, topY, color, top);
      gradientSide(pose, buffer, box.maxX, box.minZ, box.maxX, box.maxZ, baseY, topY, color, top);
      gradientSide(pose, buffer, box.maxX, box.maxZ, box.minX, box.maxZ, baseY, topY, color, top);
      gradientSide(pose, buffer, box.minX, box.maxZ, box.minX, box.minZ, baseY, topY, color, top);
   }

   private static void gradientSide(
      Pose pose, VertexConsumer buffer, double x1, double z1, double x2, double z2, double bottomY, double topY, int bottom, int top
   ) {
      quad(pose, buffer, x1, bottomY, z1, bottom, x2, bottomY, z2, bottom, x2, topY, z2, top, x1, topY, z1, top);
      quad(pose, buffer, x1, topY, z1, top, x2, topY, z2, top, x2, bottomY, z2, bottom, x1, bottomY, z1, bottom);
   }

   private static void footprint(Pose pose, VertexConsumer buffer, AABB box, double y, int color) {
      RiptideWorldGeometry.line(pose, buffer, box.minX, y, box.minZ, box.maxX, y, box.minZ, color, 1.5F);
      RiptideWorldGeometry.line(pose, buffer, box.maxX, y, box.minZ, box.maxX, y, box.maxZ, color, 1.5F);
      RiptideWorldGeometry.line(pose, buffer, box.maxX, y, box.maxZ, box.minX, y, box.maxZ, color, 1.5F);
      RiptideWorldGeometry.line(pose, buffer, box.minX, y, box.maxZ, box.minX, y, box.minZ, color, 1.5F);
   }

   private static void edges(Pose pose, VertexConsumer buffer, AABB box, int color) {
      double x1 = box.minX;
      double y1 = box.minY;
      double z1 = box.minZ;
      double x2 = box.maxX;
      double y2 = box.maxY;
      double z2 = box.maxZ;
      RiptideWorldGeometry.line(pose, buffer, x1, y1, z1, x2, y1, z1, color, 1.5F);
      RiptideWorldGeometry.line(pose, buffer, x2, y1, z1, x2, y1, z2, color, 1.5F);
      RiptideWorldGeometry.line(pose, buffer, x2, y1, z2, x1, y1, z2, color, 1.5F);
      RiptideWorldGeometry.line(pose, buffer, x1, y1, z2, x1, y1, z1, color, 1.5F);
      RiptideWorldGeometry.line(pose, buffer, x1, y2, z1, x2, y2, z1, color, 1.5F);
      RiptideWorldGeometry.line(pose, buffer, x2, y2, z1, x2, y2, z2, color, 1.5F);
      RiptideWorldGeometry.line(pose, buffer, x2, y2, z2, x1, y2, z2, color, 1.5F);
      RiptideWorldGeometry.line(pose, buffer, x1, y2, z2, x1, y2, z1, color, 1.5F);
      RiptideWorldGeometry.line(pose, buffer, x1, y1, z1, x1, y2, z1, color, 1.5F);
      RiptideWorldGeometry.line(pose, buffer, x2, y1, z1, x2, y2, z1, color, 1.5F);
      RiptideWorldGeometry.line(pose, buffer, x2, y1, z2, x2, y2, z2, color, 1.5F);
      RiptideWorldGeometry.line(pose, buffer, x1, y1, z2, x1, y2, z2, color, 1.5F);
   }

   private static void fillBox(Pose pose, VertexConsumer buffer, AABB box, int color) {
      quad(pose, buffer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, color);
      quad(pose, buffer, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, box.minX, box.maxY, box.minZ, color);
      quad(pose, buffer, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color);
      quad(pose, buffer, box.maxX, box.minY, box.minZ, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, color);
      quad(pose, buffer, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, color);
      quad(pose, buffer, box.maxX, box.minY, box.maxZ, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color);
   }

   private static void quad(
      Pose pose,
      VertexConsumer buffer,
      double x1,
      double y1,
      double z1,
      double x2,
      double y2,
      double z2,
      double x3,
      double y3,
      double z3,
      double x4,
      double y4,
      double z4,
      int color
   ) {
      quad(pose, buffer, x1, y1, z1, color, x2, y2, z2, color, x3, y3, z3, color, x4, y4, z4, color);
   }

   private static void quad(
      Pose pose,
      VertexConsumer buffer,
      double x1,
      double y1,
      double z1,
      int c1,
      double x2,
      double y2,
      double z2,
      int c2,
      double x3,
      double y3,
      double z3,
      int c3,
      double x4,
      double y4,
      double z4,
      int c4
   ) {
      buffer.addVertex(pose, (float)x1, (float)y1, (float)z1).setColor(c1);
      buffer.addVertex(pose, (float)x2, (float)y2, (float)z2).setColor(c2);
      buffer.addVertex(pose, (float)x3, (float)y3, (float)z3).setColor(c3);
      buffer.addVertex(pose, (float)x4, (float)y4, (float)z4).setColor(c4);
   }
}
