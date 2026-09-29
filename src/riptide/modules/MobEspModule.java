package riptide.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.ArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.CollectSubmits;
import net.minecraft.client.renderer.rendertype.RiptideRenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import riptide.api.module.BoolSetting;
import riptide.api.module.ChoiceSetting;
import riptide.api.module.ColorSetting;
import riptide.api.module.IntSetting;
import riptide.util.RiptideWorldGeometry;

public final class MobEspModule extends Module {
   private static volatile boolean hook;
   private static MobEspModule cached;

   public MobEspModule() {
      super("mob-esp", "Mob ESP", ModuleCategory.RENDER, "Boxes and tracers mobs, with a name-and-health label.");
      // Riptide restores "enabled" from config without calling onEnable, so a hook
      // installed only in onEnable never registers after a relaunch. Install it here.
      installHook();
      this.add(
         new ChoiceSetting("mode", "Show", "All", "All", "Hostile", "Passive")
            .description("Which mobs to draw. Players are never included (use PlayerESP+ for those).")
            .build()
      );
      this.add(new BoolSetting("box", "Box", true).description("Draw a box around each mob.").build());
      this.add(new BoolSetting("tracer", "Tracers", false).description("Draw a line from you to each mob.").build());
      this.add(new BoolSetting("name", "Names", true).description("Float the mob's name and health above it.").build());
      this.add(new IntSetting("range", "Range", 64, 8, 256, 4).description("How far away mobs still show, in blocks.").build());
      this.add(new ColorSetting("c-hostile", "Hostile Color", -2080722).group("Colors").description("Box/tracer color for hostile mobs.").build());
      this.add(new ColorSetting("c-passive", "Passive Color", -11024307).group("Colors").description("Box/tracer color for passive mobs.").build());
   }

   public static void initialize() {
      installHook();
   }

   @Override
   public void onEnable() {
      installHook();
   }

   @Override
   public String info() {
      return this.choice("mode").toLowerCase();
   }

   private static MobEspModule instance() {
      MobEspModule var0 = cached;
      if (var0 == null && ModuleRegistry.get("mob-esp") instanceof MobEspModule var1) {
         var0 = var1;
         cached = var1;
      }

      return var0;
   }

   private static boolean hostile(LivingEntity var0) {
      return var0 instanceof Enemy;
   }

   private boolean wanted(LivingEntity var1) {
      String var2 = this.choice("mode");
      if ("Hostile".equals(var2)) {
         return hostile(var1);
      } else {
         return "Passive".equals(var2) ? !hostile(var1) : true;
      }
   }

   private static synchronized void installHook() {
      if (!hook) {
         hook = true;
         LevelRenderEvents.COLLECT_SUBMITS
            .register(
               (CollectSubmits)var0 -> {
                  try {
                     MobEspModule var1 = instance();
                     if (var1 == null || !var1.isEnabled() || PackHideState.isActive()) {
                        return;
                     }

                     if (MC == null || MC.level == null || MC.player == null || MC.gui.hud.isHidden() || ModuleRenderUtil.shouldSuppressEspForUi()) {
                        return;
                     }

                     CameraRenderState var2 = var0.levelState().cameraRenderState;
                     Vec3 var3 = var2.pos;
                     double var4 = var1.integer("range");
                     double var6 = var4 * var4;
                     boolean var8 = var1.bool("box");
                     boolean var9 = var1.bool("tracer");
                     boolean var10 = var1.bool("name");
                     Vec3 var11 = MC.player.getEyePosition();
                     ArrayList<LivingEntity> var12 = new ArrayList<>();

                     for (Entity var14 : MC.level.entitiesForRendering()) {
                        if (var14 instanceof LivingEntity var15
                           && !(var14 instanceof Player)
                           && var14 != MC.player
                           && var15.isAlive()
                           && var14.position().distanceToSqr(var3) <= var6
                           && var1.wanted(var15)) {
                           var12.add(var15);
                        }
                     }

                     if (var12.isEmpty()) {
                        return;
                     }

                     if (var8 || var9) {
                        var0.submitNodeCollector()
                           .submitCustomGeometry(
                              var0.poseStack(),
                              RiptideRenderTypes.storageEspLinesSeeThrough(),
                              (var6x, var7) -> {
                                 for (LivingEntity var9x : var12) {
                                    int var10x = (
                                          hostile(var9x)
                                             ? ModuleRenderUtil.color(var1, "c-hostile", -2080722)
                                             : ModuleRenderUtil.color(var1, "c-passive", -11024307)
                                       )
                                       | 0xFF000000;
                                    AABB var11x = var9x.getBoundingBox();
                                    if (var8) {
                                       drawBox(var6x, var7, var11x, var3, var10x);
                                    }

                                    if (var9) {
                                       Vec3 var12x = var9x.position();
                                       RiptideWorldGeometry.line(
                                          var6x,
                                          var7,
                                          var11.x - var3.x,
                                          var11.y - var3.y,
                                          var11.z - var3.z,
                                          var12x.x - var3.x,
                                          (var11x.minY + var11x.maxY) / 2.0 - var3.y,
                                          var12x.z - var3.z,
                                          var10x,
                                          1.5F
                                       );
                                    }
                                 }
                              }
                           );
                     }

                     if (var10) {
                        PoseStack var20 = var0.poseStack();

                        for (LivingEntity var22 : var12) {
                           AABB var16 = var22.getBoundingBox();
                           int var17 = (int)Math.ceil(var22.getHealth());
                           MutableComponent var18 = Component.literal(var22.getName().getString() + " " + var17 + "♥");
                           var20.pushPose();
                           var20.translate(var22.getX() - var3.x, var16.maxY + 0.35 - var3.y, var22.getZ() - var3.z);
                           var0.submitNodeCollector().submitNameTag(var20, Vec3.ZERO, 1073741824, var18, false, 15728880, var2);
                           var20.popPose();
                        }
                     }
                  } catch (Throwable var19) {
                  }
               }
            );
      }
   }

   private static void drawBox(Pose var0, VertexConsumer var1, AABB var2, Vec3 var3, int var4) {
      double var5 = var2.minX - var3.x;
      double var7 = var2.minY - var3.y;
      double var9 = var2.minZ - var3.z;
      double var11 = var2.maxX - var3.x;
      double var13 = var2.maxY - var3.y;
      double var15 = var2.maxZ - var3.z;
      edge(var0, var1, var5, var7, var9, var11, var7, var9, var4);
      edge(var0, var1, var11, var7, var9, var11, var7, var15, var4);
      edge(var0, var1, var11, var7, var15, var5, var7, var15, var4);
      edge(var0, var1, var5, var7, var15, var5, var7, var9, var4);
      edge(var0, var1, var5, var13, var9, var11, var13, var9, var4);
      edge(var0, var1, var11, var13, var9, var11, var13, var15, var4);
      edge(var0, var1, var11, var13, var15, var5, var13, var15, var4);
      edge(var0, var1, var5, var13, var15, var5, var13, var9, var4);
      edge(var0, var1, var5, var7, var9, var5, var13, var9, var4);
      edge(var0, var1, var11, var7, var9, var11, var13, var9, var4);
      edge(var0, var1, var11, var7, var15, var11, var13, var15, var4);
      edge(var0, var1, var5, var7, var15, var5, var13, var15, var4);
   }

   private static void edge(Pose var0, VertexConsumer var1, double var2, double var4, double var6, double var8, double var10, double var12, int var14) {
      RiptideWorldGeometry.line(var0, var1, var2, var4, var6, var8, var10, var12, var14, 1.5F);
   }
}
