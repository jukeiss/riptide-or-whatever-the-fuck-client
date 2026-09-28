package riptide.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import riptide.modules.KillAuraModule;
import riptide.modules.Module;
import riptide.modules.ModuleRegistry;
import riptide.modules.PackHideState;

public final class RiptideKillAuraRenderer {
   private static final long HIT_MARKER_DURATION_MS = 500L;
   private static final int ACCENT_RGB = 16726843;
   private static final int HIT_MARKER_FILL = 1509899067;
   private static volatile AABB frozenBox;
   private static volatile long hitAtMs;
   private static Module cachedKillAura;

   private RiptideKillAuraRenderer() {
   }

   public static void initialize() {
      LevelRenderEvents.COLLECT_SUBMITS
         .register(
            (CollectSubmits)context -> {
               if (!PackHideState.isActive()) {
                  Module module = killAuraModule();
                  if (module != null && module.isEnabled() && Boolean.parseBoolean(module.value("hit-marker"))) {
                     Minecraft mc = Minecraft.getInstance();
                     if (mc != null && mc.level != null && mc.player != null && !mc.gui.hud.isHidden()) {
                        Vec3 camera = context.levelState().cameraRenderState.pos;
                        PoseStack poseStack = context.poseStack();
                        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
                        LivingEntity target = killAuraTarget(module);
                        long now = System.currentTimeMillis();
                        float wave = 0.5F + 0.5F * (float)Math.sin(now * 0.001 * Math.PI * 2.0 * 1.2);
                        AABB targetBox = null;
                        if (target != null && target.isAlive()) {
                           Vec3 interpolated = target.getPosition(partialTick);
                           targetBox = target.getBoundingBox()
                              .inflate(0.06)
                              .move(interpolated.subtract(target.position()))
                              .move(-camera.x, -camera.y, -camera.z);
                        }

                        if (targetBox != null) {
                           int targetFillColor = 16 + (int)(16.0F * wave) << 24 | 16726843;
                           AABB fillBox = targetBox;
                           context.submitNodeCollector()
                              .submitCustomGeometry(
                                 poseStack, RiptideRenderTypes.storageEspFillSeeThrough(), (pose, buffer) -> fillBox(pose, buffer, fillBox, targetFillColor)
                              );
                           int bracketColor = 208 + (int)(47.0F * wave) << 24 | 16726843;
                           AABB bracketBox = targetBox;
                           context.submitNodeCollector()
                              .submitCustomGeometry(
                                 poseStack,
                                 RiptideRenderTypes.storageEspLinesSeeThrough(),
                                 (pose, buffer) -> strokeCornerBrackets(pose, buffer, bracketBox, bracketColor)
                              );
                        }

                        AABB marker = frozenBox;
                        if (marker != null && now - hitAtMs < 500L) {
                           context.submitNodeCollector()
                              .submitCustomGeometry(
                                 poseStack,
                                 RiptideRenderTypes.storageEspFillSeeThrough(),
                                 (pose, buffer) -> fillBox(pose, buffer, marker.move(-camera.x, -camera.y, -camera.z), 1509899067)
                              );
                        }
                     }
                  }
               }
            }
         );
   }

   public static void show(AABB box) {
      if (box != null) {
         frozenBox = box;
         hitAtMs = System.currentTimeMillis();
      }
   }

   public static void clear() {
      frozenBox = null;
   }

   private static Module killAuraModule() {
      Module module = cachedKillAura;
      if (module == null) {
         module = cachedKillAura = ModuleRegistry.get("kill-aura");
      }

      return module;
   }

   private static LivingEntity killAuraTarget(Module module) {
      return module instanceof KillAuraModule aura ? aura.currentTarget() : null;
   }

   private static void strokeCornerBrackets(Pose pose, VertexConsumer buffer, AABB box, int color) {
      double bracket = Math.min(0.35, Math.min(box.getXsize(), Math.min(box.getYsize(), box.getZsize())) * 0.3);
      if (!(bracket <= 0.0)) {
         for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? box.minX : box.maxX;
            double y = (corner & 2) == 0 ? box.minY : box.maxY;
            double z = (corner & 4) == 0 ? box.minZ : box.maxZ;
            double dx = (corner & 1) == 0 ? bracket : -bracket;
            double dy = (corner & 2) == 0 ? bracket : -bracket;
            double dz = (corner & 4) == 0 ? bracket : -bracket;
            RiptideWorldGeometry.line(pose, buffer, x, y, z, x + dx, y, z, color, 2.0F);
            RiptideWorldGeometry.line(pose, buffer, x, y, z, x, y + dy, z, color, 2.0F);
            RiptideWorldGeometry.line(pose, buffer, x, y, z, x, y, z + dz, color, 2.0F);
         }
      }
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
      buffer.addVertex(pose, (float)x1, (float)y1, (float)z1).setColor(color);
      buffer.addVertex(pose, (float)x2, (float)y2, (float)z2).setColor(color);
      buffer.addVertex(pose, (float)x3, (float)y3, (float)z3).setColor(color);
      buffer.addVertex(pose, (float)x4, (float)y4, (float)z4).setColor(color);
   }
}
